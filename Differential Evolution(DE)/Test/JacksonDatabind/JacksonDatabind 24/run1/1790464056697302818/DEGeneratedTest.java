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
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-282,1000,1000,199,837,-1000,-202,-502,-1000,926,-1000,-866,1000,701,1000,-971,-650,-741,-1000,-500,-1000,834,-170,605,-1000,-158,-322,1000,-378,1000,1000,825,-166,-1000,186,1000,1000,-1000,-575,525,1000,1000,389,-11,41,1000,286,333,-1000,1000,330,-1000,-1000,232,-1000,-235,-120,807,-638,962,-1000,292,345,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-271,-98,96,-511,1000,274,295,-872,280,-225,1000,-530,665,-716,-185,-401,-671,89,479,-724,1000,698,1000,-1000,-1000,605,-1000,-259,-18,-138,-297,582,-1000,-619,-1000,636,-52,184,220,-343,730,-283,988,-1000,658,-417,-725,23,435,-104,403,23,1000,420,178,433,-213,412,794,-143,835,-2,-864,751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{365,1000,975,169,-557,-1000,-1000,154,-177,556,-28,-117,-970,-737,418,94,560,-1000,-716,-677,-520,919,-590,-342,877,-191,-375,-740,461,127,900,510,-1000,1000,-316,612,1000,-783,-85,-718,464,276,167,611,-207,-1000,208,796,501,779,-249,-203,46,-959,-890,1000,-1000,427,797,-1000,-26,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-404,472,-899,-67,285,-709,-462,-1000,700,191,1000,-426,-735,-1000,-1000,235,-126,-966,925,-1000,1000,-933,462,-1000,-301,870,-799,-1000,-1000,-225,704,687,-1000,400,-1000,1000,167,-765,660,-365,1000,-1000,600,-1000,715,-1000,-968,-1000,-787,352,-685,1000,770,-127,-119,1000,133,447,1000,1000,1000,885,-1000,-382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{818,742,350,-366,-544,-817,-807,-832,360,-16,-71,503,-521,576,396,530,568,287,-203,236,-864,674,-272,724,387,-507,-281,-733,790,-496,656,4,24,921,-891,332,874,166,196,-125,-590,-148,-69,50,238,-586,175,636,-21,651,298,-461,229,-408,-557,988,-764,-420,-123,-258,-755,202,-635,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-404,963,-36,295,858,-180,-40,66,-325,-406,-1000,-426,1000,1000,301,36,-731,-966,-203,325,-1000,-933,-68,-180,-654,-30,-694,375,-275,585,704,424,541,-1000,136,1000,1000,-838,-1000,425,1000,1000,139,592,820,1000,-321,-1000,-1000,1000,709,-502,-509,1000,81,-548,133,-611,-416,1000,392,523,355,-382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-163,952,342,88,453,448,-932,-1000,-1000,23,-1000,-1000,1000,-1000,1000,-1000,-1000,199,-1000,-1000,-773,1000,644,-724,-1000,-455,-968,1000,411,1000,-508,1000,-276,-1000,1000,1000,56,-1000,-1000,107,1000,1000,1000,1000,-185,-345,-117,385,-1000,-189,1000,-1000,423,868,-660,-625,-260,1000,555,521,773,441,-540,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-724,1000,-737,878,310,655,-1000,297,-1000,1000,402,1000,-702,-1000,-1000,-465,616,1000,1000,-1000,1000,1000,717,-1000,761,-51,1000,-33,-400,-773,1000,553,-286,-348,-133,-252,-620,-64,759,-414,386,987,-802,-1000,-833,1000,-1000,-1000,1000,511,-1000,-1000,-1000,-143,-735,1000,-373,-1000,1000,-725,1000,253,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-438,1000,456,-901,663,-375,-1000,-1000,69,-852,907,-78,439,-1000,348,80,-1000,542,-1000,1000,-445,1000,488,-152,-1000,-1000,717,677,-713,993,718,383,-654,-284,754,1000,519,-889,498,-304,407,-331,1000,-1000,-1000,677,530,-1000,-538,1000,-835,-778,574,-700,-1000,-441,1000,1000,615,-660,-208,-400,582,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{32,609,4,-183,-197,1000,-301,-428,926,-579,378,-361,-739,-598,-453,-973,176,-474,735,-63,389,-580,-96,358,375,19,829,118,-1000,213,1000,310,542,546,-1000,59,490,842,780,-726,594,-756,-519,186,-1000,-1000,-625,1000,45,-495,1000,-932,274,572,1000,707,49,1000,-67,289,886,328,74,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,700,-16,-1000,-1000,-232,-1000,1000,-1000,1000,-1000,-1000,-1000,514,197,1000,1000,1000,986,844,-1000,-1000,-333,-965,-1000,926,-539,-599,376,1000,497,-1000,1000,1000,637,1000,609,1000,-980,680,-1000,333,-1000,-904,1000,916,362,396,1000,216,-744,-77,-361,-358,1000,589,972,-98,1000,1000,126,877,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-416,478,375,-586,663,-56,265,190,-24,-1000,1000,71,400,-792,348,694,-354,1000,534,1000,884,81,1000,369,-1000,-1000,871,1000,204,-805,683,278,-148,-337,754,1000,1000,-1000,13,45,743,151,1000,-1000,-1000,-414,1000,-1000,-1000,1000,-835,-820,-1000,-1000,-1000,-441,1000,154,436,-660,-208,1000,621,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,369,439,-737,559,675,694,-643,-22,-1000,1000,-539,235,-659,-131,-623,935,218,899,1000,-766,1000,1000,543,-1000,-3,877,1000,1000,-811,-135,161,247,-946,-348,-822,-37,-1000,-284,677,-923,473,756,-802,-986,-1000,210,-1000,-1000,1000,-546,-1000,-1000,-1000,-657,-735,547,-373,-150,630,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-361,1000,-1000,-941,-85,1000,668,979,-1000,1000,1000,368,-962,40,-400,1000,662,829,-1000,857,501,400,1000,562,-259,-1000,149,-97,935,-1000,946,-299,-854,-664,59,-704,540,-1000,-1000,608,725,954,680,340,1000,-309,-606,1000,-1000,-865,-199,-689,-401,956,1000,-1000,-699,-1000,-374,-1000,1000,481,-328,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,451,-107,-731,-437,444,158,745,189,-1000,-831,-662,1000,366,-1000,-1000,-92,-193,866,-967,1000,1000,1000,-1000,-441,978,1000,242,-454,-343,699,-1000,1000,-1000,764,-512,-368,-1000,-1000,766,-756,-1000,-458,-404,1000,-778,-1000,1000,693,345,-730,705,1000,600,-284,803,197,-997,0,-1000,-962,-408,911,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-480,1000,1000,-731,56,-1000,630,305,-1000,-903,-831,130,1000,248,-81,-635,1000,572,866,-804,747,928,-308,-1000,1000,1000,-646,-1000,-454,-293,699,-1000,607,-1000,807,-82,5,102,-875,-146,-1000,-1000,207,-772,708,-778,-1000,267,1000,456,616,290,933,151,102,-19,1000,-113,0,-156,1000,-910,911,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{741,-620,-894,369,-446,1000,-298,723,1000,332,-692,671,75,286,-704,-48,-1000,-489,-837,-186,-46,335,774,229,-376,-476,1000,752,-267,-387,-1000,322,-212,20,320,-460,-926,-934,-323,732,-85,-783,-226,272,-9,294,206,3,-400,119,-995,869,-353,338,-498,1000,-343,-851,1000,-571,-858,146,-1000,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{702,-573,849,495,743,-1000,1000,-508,-625,-361,-969,-989,645,-116,979,-326,196,1000,357,-1000,-528,210,-371,253,1000,-466,493,411,1000,953,-536,183,600,-1000,186,414,1000,-39,-780,-1000,-171,-1000,-692,-827,-103,-9,-296,-396,298,1000,81,757,644,519,728,-87,-83,849,-553,-509,-850,221,713,406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,1000,-866,-124,-1000,676,-1000,253,225,-587,442,-1000,-139,1000,667,294,606,-766,-1000,-965,-1000,-581,587,-582,-653,610,84,348,662,-770,405,-1000,-1000,-50,1000,-63,1000,-1000,-1000,-328,-872,-702,-508,-759,847,686,382,408,387,560,-99,-1000,-1000,874,221,-440,175,1000,-1000,716,1000,81,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{185,-43,283,934,-985,129,575,625,-395,465,1000,-375,1000,-577,-179,-524,626,500,821,-1000,-1000,-68,864,-1000,1000,725,-752,212,-370,-396,-614,-281,587,-934,131,577,-1000,230,-552,1000,-223,-914,-1000,-1000,-264,-593,-1000,-94,978,762,-920,627,495,-156,-773,-321,-149,-183,-235,-1000,-112,-640,917,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-296,165,294,-220,-797,-511,-200,118,209,497,-400,792,507,1000,179,765,942,-263,-305,-344,175,-229,-624,-815,1000,320,-224,-521,-790,-921,1000,-762,-793,25,509,-570,-894,7,-845,173,-608,-1000,773,-296,140,622,-932,-579,532,-138,616,311,529,-706,-613,-133,928,-567,765,-907,560,-315,148,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-161,136,1000,189,-1000,170,92,-22,548,1000,21,-887,-1000,449,867,1000,1000,-12,-1000,-1000,-595,-1000,-1000,238,-120,-384,38,566,671,94,589,318,-1000,-753,63,830,-1000,1000,-1000,-98,-732,-261,932,259,-1000,1000,1000,-587,93,-11,1000,-584,-615,-1000,617,723,1000,-128,1000,-498,1000,488,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{242,-887,1000,429,-1000,1000,-125,935,-522,-1000,88,-1000,-1000,226,95,-103,296,-1000,-1000,-1000,-555,-1000,-57,1000,-545,-1000,212,278,-1000,-248,240,-653,-512,616,198,243,-839,695,1000,-594,-788,883,-1000,-816,-63,674,323,-71,-1000,-519,-215,237,1000,-315,987,803,54,1000,1000,1000,-481,529,467,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,1000,374,-1000,1000,-290,634,809,1000,156,-1000,-1000,1000,-67,-1000,-28,-539,-784,-1000,-183,-1000,621,1000,1000,-1000,-1000,-39,-1000,-133,-1000,-1000,160,-1000,-782,749,-682,445,390,-1000,-416,190,-1000,-1000,156,1000,2,-893,-1000,-441,7,651,1000,723,1000,-568,-803,641,1000,1000,504,174,599,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{927,-310,848,-416,-1000,778,-277,285,410,-1000,-68,-1000,437,655,-213,-1000,-359,-552,-181,755,826,-972,-531,286,-144,-1000,190,-1000,-1000,71,766,-913,-133,-1000,-283,162,-1000,507,1000,1000,39,-319,-1000,-268,610,80,791,-52,-696,-123,-41,624,1000,212,949,698,-1000,205,1000,1000,-486,-121,344,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{811,325,687,438,-1000,105,-694,-274,-522,-1000,324,-1000,1000,226,148,-277,799,-78,292,-1000,-735,-1000,-57,1000,556,-1000,-892,-446,-1000,-248,-295,-1000,560,-1000,-769,1000,-9,452,641,-32,-598,919,-1000,-1000,-63,674,304,775,-1000,-680,483,-145,1000,428,343,1000,-143,-960,1000,1000,-481,44,712,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{55,-1000,130,679,223,638,-506,810,23,-58,-238,-257,614,-142,1000,210,588,-347,-948,-1000,-309,-1000,-48,400,-575,-1000,988,-25,189,67,112,19,-285,984,655,56,-126,721,1000,-606,-279,1000,-303,452,145,461,526,483,66,-63,1000,-185,1000,-256,852,926,399,1000,4,271,-628,923,467,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{735,1000,-614,-521,-308,-1000,-707,-386,874,511,285,796,988,1000,-609,-974,-634,925,76,1000,1000,1000,-1000,-1000,847,148,-846,-1000,158,302,-844,748,-56,-654,692,-161,-26,1000,66,-299,1000,-721,861,-481,1000,-956,1000,668,334,1000,-720,427,-250,930,927,-1000,-774,-668,257,-284,987,639,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-240,-1000,1000,374,-1000,1000,-39,284,115,-1000,-122,-1000,-1000,-858,900,-261,1000,-1000,617,-1000,-183,-1000,-672,1000,-1000,-1000,516,-1000,-1000,195,1000,-1000,-97,-1000,-977,749,-1000,-203,1000,510,-81,1000,-1000,-74,480,1000,432,-323,-1000,-1000,887,651,-135,-978,1000,-568,-1000,641,1000,248,-1000,-586,1000,-244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,-367,-671,719,628,-90,649,-93,-273,-99,1000,446,-1000,59,1000,1000,-231,303,-504,-692,400,-1000,193,-1000,1000,-98,267,424,262,939,752,-171,930,602,1000,776,271,-627,1000,604,1000,501,375,1000,114,378,1000,-1000,905,1000,-730,-1000,-1000,804,-381,-70,-1000,-1000,-465,-1000,716,1000,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{25,308,551,524,-832,-836,63,595,626,978,381,-858,24,484,400,-34,332,-994,198,-99,-276,1000,22,-102,-1000,1000,-365,92,527,621,-34,205,496,-675,490,179,288,356,-493,-622,-151,1000,-261,-922,-867,-1000,110,-468,-452,-172,-81,27,-400,-29,1000,-305,-1000,1000,-489,248,879,597,1000,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,324,-156,50,-291,-1000,543,-904,777,463,-1000,158,423,-527,1000,-1000,-364,255,-169,349,695,424,-223,405,-1000,768,-1000,-1000,8,3,241,1000,1000,-317,1000,-1000,854,-572,641,-242,-1000,798,100,-70,138,413,55,-136,-154,-348,-451,1000,-469,-520,772,173,-8,547,1000,-697,-844,-393,308,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-283,1000,524,-784,43,1000,785,-285,-410,-743,-757,-1000,107,400,-961,985,-779,703,-183,-1000,1000,22,-502,-1000,1000,-90,483,-60,621,1000,-819,756,-1000,921,648,-559,551,-211,-1000,430,1000,-130,-922,230,-1000,-871,424,-1000,179,-1000,245,1000,412,1000,-42,-1000,1000,-1000,584,845,597,329,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-284,-1000,-1000,-331,345,-737,347,1000,-162,-896,146,894,242,178,-380,-346,-886,169,30,-82,-1000,-853,1000,-9,1000,-641,1000,-502,475,298,1000,285,222,403,-889,950,-693,-666,-152,1000,1000,-1000,805,-826,-60,-417,-1000,-1000,-755,429,-224,283,-755,-337,340,528,529,936,200,-1000,-79,-137,-688,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,180,829,356,-1000,-971,-1000,1000,86,-820,106,1000,-1000,1000,-399,-1000,1000,-921,237,1000,-1000,-271,1000,1000,-192,-97,-1000,811,1000,-254,1000,-930,691,433,-1000,-46,-720,13,488,-881,-935,-922,-922,-110,1000,-299,1000,1000,-1000,30,1000,-1000,-403,692,446,674,-1000,1000,-1000,-1000,-693,-993,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{345,-83,574,69,-51,992,-352,278,777,463,-375,158,198,-263,362,-647,375,639,-235,-102,580,12,64,821,-549,685,-97,-433,8,614,11,-242,-685,-1000,569,-801,-717,-38,641,-1000,-95,276,-670,-236,-53,1000,-770,1000,163,-1000,-799,78,-580,1000,679,-23,-327,84,-224,62,-354,-363,-1000,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-1000,-392,-406,994,-1000,544,-412,-65,188,-813,1000,46,32,-46,-41,843,447,-593,-1000,1000,-1000,-1000,59,14,-65,-599,929,-290,1000,-296,1000,467,-843,-863,-1000,-1000,1000,1000,-658,76,276,1000,-223,-959,-1000,-490,-1000,1000,-896,492,-857,389,-735,-682,-1000,-1000,-603,-461,-112,309,-125,1000,913,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{391,-1000,452,884,555,-50,-562,-683,728,791,376,-255,-323,-64,-751,-358,1000,869,935,-1000,-635,1000,1000,232,791,-990,363,1000,-418,832,-987,237,-508,-671,1000,596,-781,-37,749,-294,-1000,-527,-1000,644,1000,-2,-1000,-914,1000,-403,-47,443,-574,-839,84,411,-629,1000,526,-1000,266,-544,-470,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-1000,-1000,-476,-121,-677,-1000,485,88,-986,-1000,926,665,802,190,1000,-555,-1000,-1000,978,1000,210,-1000,-1000,582,-909,1000,700,-1000,-123,-1000,142,621,1000,-199,1000,1000,-175,610,-1000,1000,1000,-445,1000,-400,-1000,-213,1000,103,-1000,-908,367,-466,881,-773,-770,63,483,-1000,-1000,603,-431,1000,-559,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{255,-1000,1000,768,-862,326,-664,-351,452,1000,90,-1000,-496,10,-864,-640,888,728,-557,-167,-175,684,-867,-564,854,-1000,1000,-205,812,1000,82,-201,-1000,-1000,1000,-533,1000,-1000,1000,-353,429,-175,-668,49,472,170,-822,-109,868,456,1000,-3,116,-301,-916,-260,-87,1000,-453,-1000,484,147,-1000,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-141,821,-566,514,-172,1000,-1000,-882,-1000,-105,647,30,-639,637,1000,612,-1000,-952,-603,540,-874,-31,-494,-682,482,930,648,-775,271,-328,661,355,-400,-1000,-1000,-448,743,909,-776,212,333,58,11,-49,-1000,-1000,-400,1000,1000,272,478,136,-1000,-308,-1000,-636,-479,-653,-110,785,1000,1000,355,566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-863,-111,924,-931,723,-1000,1000,88,414,-39,593,-108,1000,374,1000,-855,-518,-659,978,631,-597,-1000,-1000,760,-254,930,1000,-1000,-123,-681,-860,842,1000,452,-949,1000,-584,708,27,596,51,-1000,1000,1000,124,-213,1000,-724,-938,-1000,1000,-429,570,-181,226,635,-85,-1000,-1000,-709,484,982,-215,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{386,-1000,992,-121,-64,8,485,540,1000,-1000,351,-275,-42,1000,-828,-169,1000,1000,1000,-1000,-877,322,646,-959,-562,-665,609,851,-940,1000,-1000,568,1000,-199,1000,458,-1000,-448,647,-469,-1000,-1000,-167,1000,-750,-1000,819,-1000,-169,-1000,367,-177,-1000,-773,1000,1000,-1000,514,1000,-724,736,-290,-559,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-453,-705,614,-86,948,-411,-435,-515,-960,-690,760,-86,831,110,461,-642,-526,841,219,783,-498,-826,258,250,760,583,600,102,-27,110,656,-214,310,899,392,782,-894,902,-144,-318,-13,-583,831,861,32,121,713,-625,-673,-861,310,-287,-699,-641,194,880,-451,-349,491,907,523,-6,549,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-363,-1000,-207,-781,-782,-1000,-448,715,-442,58,141,-861,-1000,-339,829,213,-1000,-795,-1000,413,1000,1000,-1000,-952,734,665,-16,-1000,202,298,1000,-141,638,-672,-735,505,-440,-1000,-1000,1000,548,-395,-456,-761,1000,419,1000,-230,1000,-612,0,196,-992,566,654,-354,-11,908,-64,-879,345,-983,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,933,843,395,624,734,-35,70,224,-542,344,743,267,-364,417,1000,1000,1000,-199,-1000,-707,1000,677,-393,-1000,1000,1000,217,-298,-1000,-153,235,507,1000,-156,1000,489,-127,-867,-1000,-460,220,-448,-672,367,-384,736,-1000,624,-1000,237,881,444,400,-992,-1000,461,-1000,7,854,665,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{522,-911,-445,-1,1000,476,-94,-1000,470,-1000,-749,-310,-460,850,-950,-477,-312,-285,505,-234,-1000,45,1000,-1000,875,-1000,-1000,756,1000,-26,-1000,-10,1000,-341,-419,-8,-86,1000,-1000,-235,-661,382,-1000,255,-1000,-145,-227,1000,-474,20,1000,-1000,557,-516,-349,-453,-342,-10,644,977,488,24,1000,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-363,-1000,-207,-536,-782,-287,-181,-1000,682,-1000,501,1000,-472,-286,-1000,1000,-261,-1000,126,-629,-1000,1000,1000,-1000,-311,-676,-431,244,202,-974,586,106,428,-792,-150,505,-1000,1000,-1000,1000,-111,-1000,-660,724,-1000,-554,277,1000,-567,-724,1000,-1000,-710,50,-1000,-491,-11,381,945,373,-679,-983,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-83,-335,596,-371,-1000,-545,-505,1000,-904,-386,-647,-580,-1000,-253,1000,42,-1000,-157,-1000,374,-36,932,-1000,-1000,400,283,-58,-960,-86,5,569,170,755,-123,226,-259,478,-1000,96,-314,-38,-525,-785,-761,1000,1000,1000,-70,448,536,-596,90,-1000,353,1000,-384,-32,985,243,-879,405,-983,280,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-600,578,940,459,-752,-350,-70,1000,-345,1000,-904,366,-1000,-217,1000,541,-991,116,-1000,945,-708,337,-959,-549,-742,94,761,-943,78,-509,385,-1000,-92,-162,638,-486,1000,-845,656,-877,164,-581,-804,-1000,1000,742,985,-107,212,387,-851,431,-799,696,445,-778,-98,1000,-612,-1000,534,-1000,828,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-914,-569,-866,614,-782,-1000,1000,-1000,400,-514,-797,497,208,385,-193,-1000,1000,895,1000,-1000,-1000,8,1000,27,334,-1000,-341,1000,852,-251,-1000,-17,867,497,1000,133,1000,264,-1000,-264,-628,-788,-168,-79,-1000,523,-359,1000,-1000,396,-10,-442,1000,-149,24,-897,-1000,620,-343,977,-904,-983,1000,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,53,-611,514,-382,-1000,-222,1000,-725,-1000,-489,420,1000,1000,-536,1000,-1000,396,224,1000,-1000,516,681,-1000,567,-72,1000,191,130,1000,-863,517,-1000,991,232,-153,-683,66,1000,-109,1000,-637,374,891,-918,778,-553,-928,-1000,-488,540,-778,1000,-866,-950,-590,-1000,-1000,-413,-1000,-994,1000,-1000,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-521,-644,-461,845,-85,-151,-1000,-511,-202,286,-904,230,65,867,455,705,-1000,-797,-612,671,988,447,119,655,-1000,249,502,-720,-229,229,984,-306,392,-873,121,534,-797,-398,-103,-592,-431,253,408,-110,333,-299,-416,-289,72,763,1000,65,-637,547,-881,188,183,67,-220,-134,-614,-223,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-357,-532,-136,504,-382,755,-889,-1000,-587,-77,610,-915,-1000,1000,-11,311,-690,-1000,-600,-1000,1000,1000,-357,-792,149,261,256,452,-768,1000,1000,1000,-633,-802,-685,296,1000,-377,-607,-398,-947,-686,24,1000,-247,-649,817,309,450,-677,1000,1000,-562,4,-1000,-1000,136,409,109,-1000,-987,-1000,1000,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-122,1000,95,-186,-1000,1000,206,1000,-195,621,1000,1000,1000,-385,-112,-752,-542,1000,-500,1000,-426,-1000,1000,1000,-29,29,-2,787,-265,-46,132,877,458,1000,987,-1000,-1000,963,-294,1000,-853,-1000,1000,-1000,3,1000,-924,-936,-324,-1000,-1000,56,1000,1000,14,-1000,1000,108,-527,-80,1000,1000,2,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-100,-471,514,333,141,-1000,-917,629,-1000,311,-1000,174,910,234,391,1000,-47,188,948,1000,-1000,85,-903,628,-182,733,1000,-1000,1000,1000,403,-66,-583,-859,689,214,133,-358,530,-590,1000,1000,-1000,329,842,-94,-1000,1000,-1000,-1000,-868,-579,592,-1000,302,432,-687,-400,-348,530,171,1000,-1000,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-93,988,-56,644,-382,1000,-1000,-1000,-1000,68,1000,327,-1000,854,523,-337,-277,-109,-748,-113,-464,-343,77,-529,-534,-440,931,635,-192,876,1000,1000,-985,80,640,-77,75,787,-1000,-732,-1000,-1000,463,847,-882,1000,-1000,936,-219,-1000,758,845,296,-1000,-108,-576,-942,-770,-246,-655,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{670,-1000,-635,-191,1000,-592,-807,-754,1000,-628,-824,996,856,959,550,-457,-752,-1000,-606,-512,-1000,100,333,558,-541,-66,1000,-336,361,-11,439,872,-1000,1000,341,-636,-1000,178,333,504,271,-686,869,-1000,839,-49,881,60,256,-869,-248,753,931,-748,-1000,880,-808,-468,-584,244,1000,-869,155,634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,774,-756,715,193,957,-1000,1000,-1000,-797,969,1000,-1000,65,124,192,-1000,-319,-266,1000,1000,622,1000,-1000,-1000,403,751,-429,875,-551,15,-1000,1000,621,492,-502,242,1000,1000,-126,1000,-1000,936,426,-72,1000,-508,-674,377,434,90,-1000,54,379,-259,848,-1000,94,776,204,-285,-157,421,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,743,1000,84,-862,-722,-811,-1000,738,733,-198,-944,838,-1000,-747,207,438,209,216,1000,423,-52,-646,-957,1000,-504,-71,889,-17,-2,258,1000,-1000,-881,-866,-77,-828,653,650,463,483,400,934,-1000,-130,895,-459,-648,1000,882,122,-393,475,1000,632,772,198,965,427,-1000,-321,1000,648,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,212,-1000,-981,1000,-1000,1000,-1000,1000,-52,969,-203,1000,-454,-786,-313,254,-262,1000,-616,-1000,-1000,-326,400,-85,1000,616,449,-841,1000,1000,-644,-578,704,281,-216,26,-1000,790,98,238,1000,-368,-238,-1000,-797,965,779,505,1000,1000,322,804,305,-56,-1000,1000,854,-553,814,-597,159,76,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-169,1000,927,-392,-82,1000,75,-1000,-339,455,1000,-1000,-1000,-1000,-786,1000,744,44,124,332,369,-710,-355,-1000,1000,-138,616,1000,-464,-540,-845,-310,400,843,281,-125,320,-1000,548,255,333,449,450,1000,-1000,1000,-960,779,467,308,731,-1000,-533,305,154,253,1000,854,-298,487,-815,159,557,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-936,1000,1000,668,338,938,-1000,1000,-974,-188,-574,1000,-1000,-932,606,1000,-601,-437,-8,1000,1000,200,566,-1000,-70,-1000,261,370,666,615,450,-1000,1000,302,219,-364,-42,910,921,-98,917,-623,909,216,1000,1000,-1000,-294,468,831,187,-1000,-325,1000,391,782,-654,-571,1000,-595,-192,1000,172,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{158,30,-361,-1000,509,-115,-536,-307,358,-497,477,562,299,171,1000,191,998,-1000,1000,975,-1000,393,343,346,458,-1000,-196,657,608,348,953,-343,343,1000,809,312,-1000,454,367,-34,490,-162,757,-510,888,-132,-224,684,-189,985,116,-103,659,1000,39,916,746,-628,-423,612,365,-168,943,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-941,-1000,-287,1000,-844,-724,-1000,816,-885,-1000,1000,1000,1000,1000,-748,-1000,-1000,-496,-937,-780,1000,1000,1000,-478,-523,709,-325,1000,-599,450,820,-870,1000,659,-516,-1000,505,-70,216,204,-780,1000,611,771,-871,-1000,364,172,-878,-993,425,1000,-1000,-1000,829,-1000,-1000,-643,139,1000,-959,-167,483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,40,-1000,209,393,-1000,-1000,807,-244,147,1000,870,426,1000,298,-291,-1000,-762,-1000,400,846,-233,-1000,-388,-813,606,-1000,-410,262,1000,-1000,572,-104,1000,126,644,-355,106,-440,417,1000,-931,1000,300,909,-1000,-96,-134,-669,-185,103,1000,1000,942,1000,1000,201,-1000,1000,-447,-293,1000,-600,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-890,164,-181,55,386,-614,-510,458,21,573,118,416,-838,-1000,24,-962,-1000,-625,953,971,372,-510,-944,1000,-1000,555,-760,1000,-1000,-511,1000,1000,1000,-945,-549,-1000,130,1000,1000,-705,1000,805,211,114,-209,-1000,-795,1000,714,330,1000,1000,-285,1000,1000,-1000,428,-165,-757,-867,243,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-852,-606,299,-1000,-778,-1000,-1000,823,1000,1000,422,1000,-1000,1000,1000,-664,-720,-33,-1000,-1000,1000,508,-1000,-504,625,1000,-419,-1000,-346,1000,-1000,1000,-1000,1000,-319,1000,-1000,-465,-1000,1000,504,-1000,5,-287,1000,-1000,116,-1000,-965,1000,-741,394,-636,1000,1000,-858,-1000,-1000,1000,-867,-1000,1000,-1000,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-687,-240,93,455,-16,-1000,-700,652,214,400,50,955,108,190,129,33,-622,-94,-1000,-479,289,-1000,-796,578,466,1000,-794,151,-238,34,-806,58,663,-293,884,-23,58,587,-400,400,728,-221,400,-423,611,-707,996,-467,-332,566,296,200,665,705,-565,1000,-630,-605,447,612,-697,738,-568,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,130,-1000,-156,-32,-1000,439,1000,-991,-450,1000,56,-752,1000,-640,-291,-1000,-609,-597,-989,-945,823,-1000,-1000,-1000,1000,-1000,-1000,-1000,827,-1000,1000,-134,576,1000,1000,-596,-58,-176,1000,1000,-1000,1000,1000,-309,-1000,883,-169,-685,622,114,1000,1000,1000,1000,1000,393,978,1000,541,-690,1000,-1000,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{803,-89,983,-262,-1000,-1000,705,601,580,1000,-205,1000,-336,-899,282,29,-103,-15,-770,-394,541,-871,-119,1000,1000,921,226,1000,829,903,-627,-1000,-991,235,679,755,-1000,-599,-1000,-122,-477,-229,231,53,942,-449,-429,481,-777,146,-1000,-490,1000,-207,-273,594,-1000,-1000,1000,-1000,-532,733,-422,-59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{133,1000,219,213,-896,-27,-394,128,1000,767,-617,1000,1000,-39,170,-1000,278,-1000,-867,-963,1000,263,119,392,282,901,-771,204,64,1000,-1000,189,604,1000,46,-269,-1000,-473,-1000,981,-58,-834,1000,453,1000,633,-659,-951,444,1000,1000,-157,1000,1000,1000,859,244,-1000,9,-1000,-484,239,-1000,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{429,659,913,-667,927,389,665,-172,-1000,-129,-385,393,-401,-83,1000,-609,-1000,339,896,140,-455,196,276,-356,688,303,-1000,-21,-532,1000,-156,-185,-994,329,11,617,1000,770,-908,1000,1000,979,-77,11,459,213,-242,361,556,-824,-312,-1000,-1000,970,82,1000,-170,990,-141,446,205,443,-820,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{819,-508,13,-46,540,775,-375,256,1000,-129,-98,553,896,-241,792,-377,-654,-346,-1000,218,78,-256,-580,-136,437,-335,381,256,293,-1000,819,-484,-915,-925,-362,-265,-204,-628,-1000,-731,-1000,750,356,470,1000,-603,284,690,-576,523,197,484,-322,-913,577,-739,1000,-457,820,-139,401,-502,441,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-926,-34,-72,-484,813,598,-358,960,-921,-125,1000,671,-999,1000,283,-1000,1000,-866,653,-317,157,380,-1000,536,496,1000,504,276,-831,913,-272,-1000,-750,1000,-674,-385,1000,-1000,209,-331,-295,888,206,614,-828,404,771,472,549,-351,-114,-385,-851,-1000,-210,687,143,8,60,1000,-528,-615,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-26,-868,-22,254,-1000,-1000,-535,-458,-412,-84,-358,67,179,335,591,125,-159,529,966,419,622,775,329,-453,-1000,973,1000,203,-320,-158,325,-576,441,802,807,-832,-1000,-319,-10,-250,-806,-1000,-1000,-1000,-745,-579,889,538,177,688,-20,-1000,946,121,-261,116,-961,-1000,-5,-897,-403,596,64,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{939,-926,345,-346,-782,813,610,454,483,552,181,-181,-780,691,630,372,-263,-952,-866,-407,-257,518,-971,-145,536,858,-112,937,-792,268,-511,-283,924,874,446,-774,54,-724,-224,342,-321,917,229,-16,-153,-828,-225,-96,-171,793,399,-114,588,631,990,504,687,382,8,-543,-38,525,-615,-77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{383,-467,878,734,-484,-1000,-1000,-1000,620,-921,-1000,1000,395,686,1000,-332,-1000,1000,-866,1000,1000,540,1000,-1000,-1000,1000,1000,570,644,1000,-168,91,73,-750,1000,-236,-963,1000,-704,-811,-291,-1000,-1000,206,765,-565,1000,1000,-550,368,-459,-1000,-338,913,-1000,1000,-1000,-1000,-28,-1000,1000,-528,-615,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{400,400,504,363,1000,538,598,-95,-814,172,-934,556,1000,226,304,-742,-567,209,772,32,-1000,283,-96,-725,536,-90,-446,272,89,1000,109,814,111,-397,30,-160,1000,579,-541,-429,-331,1000,888,-35,614,385,-149,252,-250,-24,608,-347,-385,-851,813,239,483,190,-299,-488,484,-528,-891,-679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-576,85,111,-46,-121,-172,-580,-178,-215,548,393,-373,335,-117,362,-101,78,748,-412,241,263,-1000,112,329,240,-400,693,-69,129,-255,-62,-291,1000,-662,-670,-637,-733,-474,368,-160,983,155,-1000,120,631,618,487,-1000,-1000,-4,-357,-1000,590,310,274,-51,-402,-342,436,-119,198,849,-667,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-1000,488,1000,404,206,77,1000,-955,-1000,139,-399,-1000,1000,-254,-572,-985,43,1000,507,228,324,1000,1000,-510,-212,1000,147,1000,-1000,-300,608,-341,-1000,-451,-1000,-1000,-1000,660,-799,-1000,726,1000,-1000,366,752,191,1000,-1000,-1000,-1000,-85,621,214,511,1000,-568,-382,1000,-691,-1000,-909,783,-666,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-1000,-1000,-208,-101,424,-1000,-580,657,424,294,7,186,288,564,-1000,485,-1000,868,495,-173,942,912,1000,-1000,1000,304,-665,1000,-477,580,-905,442,1000,-372,-1000,-1000,914,-580,368,-357,1000,432,-1000,120,1000,-580,487,-1000,-1000,-522,319,1000,-580,-982,-240,-671,-1000,655,421,-600,-1000,353,-1000,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{224,780,-657,1000,368,105,662,-877,-180,381,482,684,-146,289,190,863,1000,518,-369,-881,-308,-1000,-1000,292,-38,802,-514,-886,850,115,0,456,382,562,519,185,-405,-353,-448,153,440,361,1000,65,-153,773,969,-713,-1000,227,62,-602,-176,854,-1000,-82,-440,223,923,266,1000,-439,540,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-1000,-460,1000,839,-1000,-479,400,85,-1000,1000,1000,1000,26,-1000,-1000,1000,-651,996,162,1000,568,-684,-88,-612,1000,1000,1000,1000,-548,-1000,1000,-416,-707,131,-582,-1000,-1000,545,-805,-513,1000,548,-1000,258,1000,-1000,431,-1000,-400,-1000,939,1000,-372,-415,1000,-59,-1000,-31,1000,-1000,-1000,-78,-1000,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{432,-1000,-657,-176,368,188,662,-721,-180,-301,765,-716,-76,513,590,874,1000,1000,-540,-881,-741,-1000,10,425,509,802,-209,-886,683,257,94,-1000,-204,562,519,185,-454,-353,-381,153,556,206,1000,65,787,773,774,-260,-1000,227,-1000,-393,-176,1000,-953,170,-440,-130,982,12,1000,-439,540,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{224,-52,-699,829,-836,-386,62,-407,299,-84,22,1000,-139,160,-503,863,-1000,1000,639,-810,-234,258,886,-357,514,1000,-718,780,73,-398,749,944,516,-714,-682,-171,573,220,-1000,-310,-343,869,400,400,639,-400,1000,-963,273,227,-77,1000,255,-417,-148,-807,-850,423,-107,49,-555,294,238,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-178,-823,-93,133,175,206,488,304,295,-565,-56,-246,465,853,409,214,48,-358,994,621,466,427,467,768,258,-498,748,-651,-98,-647,-328,-24,1000,759,604,56,-260,-157,64,809,1000,-232,447,171,-631,-246,689,-566,-426,79,508,-273,-125,-1000,125,69,734,-227,-423,153,384,-1000,-429,924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,814,-91,-1000,-711,-571,305,-1000,-183,1000,-112,715,-634,1000,786,-1000,-92,-808,-1000,587,1000,879,-586,-1000,1000,-589,-212,-1000,1000,1000,29,-1000,-481,1000,799,217,-693,886,-1000,-1000,-649,-89,396,443,1000,1000,217,-958,508,1000,645,-677,1000,-347,689,-1000,-681,-1000,838,963,-53,-1000,625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{920,-712,4,-917,964,1000,-513,-1000,-168,1000,958,413,-847,-836,878,33,1000,-651,504,443,-639,-145,-312,890,-1000,1000,1000,-922,1000,401,-1000,-1000,1000,1000,-1000,721,788,883,849,1000,1000,97,1000,-1000,65,-1000,-829,1000,-771,802,-1000,1000,-1000,-843,485,1000,1000,-632,1000,829,-1000,-183,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,1000,-126,-977,-1000,594,400,-1000,-234,1000,-47,1000,-330,664,1000,-470,-551,-17,-17,1000,356,1000,-263,-1000,511,-1000,742,-780,449,1000,-146,-1000,-34,772,244,-187,-138,1000,-182,-1000,-733,-160,1000,-1000,1000,1000,-1000,1000,58,1000,875,104,1000,-276,-1000,-221,-851,-1000,399,980,-992,-1000,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{425,898,1000,-196,814,-147,782,1000,96,-1000,1000,800,-178,881,555,-288,520,-1000,1000,644,1000,-111,555,1000,-995,-1000,48,-124,126,-59,-148,-592,-380,1000,623,-617,-149,197,53,1000,567,-945,-248,-180,-741,701,368,-219,-426,-470,843,1000,96,380,-592,579,58,-724,-463,986,-226,-884,-712,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{982,684,464,-177,-61,599,675,152,-717,-195,841,-21,1000,-196,911,1000,-204,-1000,-559,134,545,-553,561,-215,-318,285,-400,680,-246,359,322,-451,-520,98,275,-99,169,955,1000,574,-1000,283,-533,502,-708,324,-558,-664,655,255,478,913,237,400,-422,-623,424,-918,-505,494,584,-821,-884,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-307,470,519,89,-102,-159,220,779,-717,-195,574,-144,372,-400,558,1000,-266,452,-437,-235,-467,-553,160,464,-1000,112,-589,302,-604,183,-710,309,-717,116,310,289,-721,352,-75,-349,-197,-1000,1000,423,-455,135,-749,-400,141,1000,397,-1000,563,758,-51,-391,1000,-351,-1000,-311,400,-1000,-755,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-839,161,567,-291,-232,424,185,-271,962,46,-634,-611,888,741,-976,259,769,962,748,701,-444,-564,282,-739,936,-926,-436,676,-503,-366,300,-891,623,539,-36,673,-701,-350,895,-915,439,-526,727,385,-451,-754,-224,-959,-396,-310,202,485,-143,818,768,156,971,111,-140,441,345,646,47,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,842,455,219,1000,-587,839,-765,-108,545,-1000,-1000,-568,1000,1000,-1000,1000,-889,-980,514,-137,-585,-1000,-652,1000,149,-381,-1000,-952,-1000,-725,1000,-1000,-372,768,622,880,1000,-814,379,-1000,717,583,-416,-1000,1000,-70,53,-132,-711,450,-795,-1000,1000,1000,225,-1000,-1000,488,1000,186,-755,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{85,91,577,-481,-903,492,-797,-1000,567,-1000,552,-365,751,798,-864,-308,166,527,902,19,-1000,497,-146,-693,1000,-1000,-165,639,-1000,185,8,-1000,933,-158,1000,38,-1000,-1000,354,-736,-165,688,-594,28,989,-699,-272,-1000,1000,-45,483,984,276,222,987,248,844,1000,930,-1000,581,185,-1000,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{440,51,74,86,32,-394,-60,346,102,338,220,-345,-386,204,-609,-755,14,458,-925,229,-469,645,656,-51,179,1000,1000,-500,-880,-142,929,-1000,-288,440,1000,-845,141,1000,-876,1000,-180,-1000,275,-435,-1000,-672,-460,842,-247,63,-865,-908,-414,-547,233,-288,105,-512,915,97,-477,-376,-158,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,842,-248,79,895,299,839,-765,362,1000,-760,-389,-125,185,1000,-1000,1000,-357,-564,533,566,-1000,-113,-795,-434,475,256,-560,-880,-1000,-111,522,-709,314,441,-215,465,1000,-240,-261,-671,304,-226,173,-539,-261,-377,-153,-1000,-325,379,-795,-1000,1000,-240,694,-1000,-1000,216,780,320,-323,907,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-483,-542,-625,1000,368,1000,-858,-207,818,-1000,659,1000,-1000,1000,-942,626,-1000,-1000,-1000,139,1000,495,372,-1000,-216,-925,-1000,-195,-382,-471,1000,-1000,-1000,407,-111,1000,1000,-1000,1000,978,370,571,-1000,-1000,1000,1000,1000,-1000,-1000,-1000,-1000,-1000,-238,-1000,468,-1000,-656,240,1000,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{514,1000,-114,654,-1000,607,-142,994,1000,-1000,-748,547,-656,163,828,-455,1000,-1000,-135,-380,928,493,-784,-56,-721,-580,-929,953,-1000,-1000,1000,497,638,1000,1000,133,-670,-1000,-633,1,773,-456,1000,-795,505,844,-580,1000,524,-498,-512,286,-859,-678,709,-342,-1000,-1000,-627,-550,256,595,-580,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-668,-892,293,794,-205,-872,7,-50,-639,80,192,133,-713,-870,-599,308,-906,312,901,286,284,498,-183,691,-301,885,630,859,267,868,-715,568,292,819,-307,-390,682,-694,-191,-436,551,590,-311,912,-531,-163,883,-405,-677,456,-587,505,209,167,-843,-991,-922,0,953,-401,969,-723,971,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-371,-1000,-497,328,133,-585,-1000,-742,346,-513,-256,-345,-870,-599,-613,-1000,312,1000,-607,-414,-539,-190,691,-65,-153,630,859,1000,457,458,743,745,504,-599,830,-491,-321,-246,221,-510,590,-1000,8,-647,-163,-108,-405,-66,201,833,-529,361,88,41,858,240,0,1000,-197,969,382,-896,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-694,-528,-107,-1000,-1000,896,-1000,995,367,-1000,290,114,-1000,1000,193,-1000,-834,756,-1000,1000,803,1000,-1000,1000,-1000,-1000,266,-476,183,-800,502,453,556,-690,308,777,-1000,-1000,-1000,-710,-272,-619,454,-367,220,179,-1000,770,389,-532,153,669,1000,-1000,294,-590,-278,-945,-1000,-483,768,826,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{545,1000,400,734,513,-296,128,-721,-123,-878,-1000,-179,936,-99,-343,129,441,-939,505,-638,-391,-651,12,-750,-415,-242,-1000,377,-375,470,155,688,730,780,703,94,-45,1000,-179,654,214,-688,-372,368,280,-154,306,829,-10,-573,925,-892,-907,548,432,112,1000,717,-303,-857,1000,832,-307,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-299,-796,-760,1000,-142,337,1000,-1000,-736,-112,-950,163,1000,-672,1000,-1000,-135,-136,443,180,-1000,-204,-1000,-1000,-987,243,-1000,-1000,1000,572,1000,223,683,655,-921,-1000,-944,90,218,-1000,1000,-883,288,361,-1000,1000,298,-1000,209,-989,1,-1000,600,687,326,-842,-1000,54,256,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-668,-1000,293,924,61,-872,-1000,-62,-992,921,983,260,-503,-1000,-1000,582,-990,501,1000,-202,494,538,283,1000,-73,1000,1000,1000,267,1000,-1000,512,-629,1000,-165,-768,1000,-206,73,-759,844,922,-731,1000,-689,122,1000,-673,-483,713,-587,768,-1000,587,-822,-1000,-748,0,1000,-483,1000,-552,1000,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTypeFactory(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-824,531,439,536,-1000,493,205,-562,400,-813,-756,314,1000,-79,-1000,-656,-324,-1000,185,-739,-1000,1000,325,203,110,231,907,-1000,-61,1000,711,536,-243,857,721,-376,-1000,-1000,-529,378,-1000,169,-70,533,-1000,-1000,-626,20,-579,-336,-734,260,49,473,-827,1000,-1000,39,-117,1000,216,991,-899,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTypeFactory(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-24,766,4,294,-386,433,-235,2,-211,-622,-1000,400,-451,-539,-53,-616,-1000,-463,116,-779,336,1000,-426,825,892,1000,609,-637,226,267,689,-299,-1000,614,-86,-13,895,-360,-125,-112,-368,578,341,675,-844,-582,224,-558,-288,-1000,-15,153,-1000,940,-1000,1000,-1000,557,955,-121,621,255,401,706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTypeFactory(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,504,-686,-481,508,1000,-1000,-269,1000,-1000,-1000,563,753,1000,-264,-469,-1000,-922,581,-1000,-1000,1000,565,-1000,-537,-98,919,-435,1000,1000,-1000,1000,-1000,92,1000,591,-1000,-1000,1000,768,-1000,-1000,-1000,673,761,-1000,181,-400,-360,-1000,-1000,-725,669,1000,-1000,1000,-1000,-1000,-816,1000,1000,1000,-1000,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTypeFactory(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-792,396,-942,-135,-355,-1000,-1000,-41,1000,-687,-919,481,1000,1000,-751,43,-1000,-1000,560,-1000,-1000,1000,-588,-1000,-336,1000,-66,189,-1000,1000,-740,1000,-679,-55,864,-179,-782,-1000,859,-380,-1000,499,-252,460,-1000,-1000,958,-959,-426,-975,-1000,-795,1000,723,-1000,291,-1000,544,1000,370,876,-850,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTypeFactory(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-224,161,-1000,-81,-536,-715,-1000,-974,744,483,343,306,1000,512,-674,-425,400,-922,488,400,270,487,-1000,400,-555,845,-56,82,-1000,231,-109,1000,-54,-123,134,-321,400,332,41,470,400,46,-956,608,-728,400,1000,409,213,-41,386,3,472,-110,-274,158,400,1000,-462,-450,887,384,400,-149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTypeFactory(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{461,597,-897,483,784,-456,583,-1000,-1000,423,380,-466,-406,-379,1000,-1000,1000,328,507,193,213,118,202,-1000,-309,-1000,-88,-531,763,1000,1000,-814,-164,-495,-437,-917,851,379,-572,563,400,1000,867,-598,-339,400,-751,1000,599,374,460,854,-865,807,263,-245,400,-286,-400,758,197,-433,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor,com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,431,565,-341,1000,-775,-696,1000,407,-452,-1000,-1000,-219,-1000,444,1000,-64,-118,-409,71,333,-979,-1000,-287,-42,-469,570,-535,866,24,-597,1000,-909,147,309,-66,-587,1000,-121,-638,-281,1000,-1000,566,-952,150,603,-967,656,-81,-185,535,209,-894,-144,201,435,-396,-788,571,729,-1000,-246,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor,com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-50,-207,314,-1000,-898,1000,-785,-271,-119,-1000,1000,-23,295,-717,-501,-1000,321,-485,120,838,-364,179,-26,164,354,-1000,-261,810,1000,-206,-831,98,225,-1000,-1000,-1000,-791,-97,1000,-1000,-650,287,181,361,648,-1000,587,-1000,-1000,-356,-1000,229,1000,-1000,1000,-829,654,201,668,1000,-757,1000,157,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor,com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-892,602,124,-211,813,-407,-466,334,214,-256,-437,-835,-477,-1000,1000,-202,-111,-366,139,348,1000,-553,-995,317,354,-1000,278,-607,1000,147,76,702,-259,-132,439,-812,-114,-97,400,-181,317,770,-792,225,-1000,62,685,-1000,329,-40,-889,575,559,-674,-75,181,654,201,-446,700,926,1000,-310,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor,com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-956,585,129,-838,-285,-304,-1000,-411,131,-666,351,425,-759,-985,-610,-753,757,346,-73,913,-254,-379,-412,555,-523,-78,-223,-599,1000,265,-451,607,-809,-1000,-165,-1000,-342,49,35,-630,-874,524,-276,208,-1000,-468,-3,-1000,-569,-531,-801,65,20,-408,2,164,1000,-748,-338,1000,426,1000,841,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setVisibility(com.fasterxml.jackson.annotation.PropertyAccessor,com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-851,1000,-476,437,446,-154,-1000,591,856,-525,-917,-1000,-14,-1000,494,-279,67,-468,-130,-118,26,-1000,-1000,351,1000,335,-28,-1000,20,-957,-572,1000,-330,924,1000,-66,-982,927,-50,631,-855,1000,-1000,1000,-1000,-336,-689,-36,577,-8,165,1000,164,-772,-674,229,683,-476,-827,-400,1000,-53,891,44}));
    }
}
