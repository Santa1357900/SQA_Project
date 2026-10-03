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
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsElement(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-898,434,-7,-871,-9,-1000,1000,589,254,-978,-1000,-57,637,413,972,876,-720,39,447,-628,-123,-487,881,-767,-1000,824,-1000,288,-47,454,388,1000,-997,-296,-15,-800,694,458,583,99,-29,-774,327,119,715,805,1000,-468,526,465,565,-650,-191,11,-1000,312,-1000,-143,-713,1000,164,175,-203,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsElement(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-631,418,-147,255,1000,-990,-140,-157,429,-59,-88,45,421,619,845,1000,91,260,-1000,666,-957,510,-222,-339,-490,-345,-444,-316,633,649,579,683,-684,326,-898,762,1000,134,-193,-288,-71,-1000,-491,-512,-61,157,1000,149,-124,1000,1000,-61,994,1000,-685,545,439,998,-200,1000,422,-581,474,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("JSON:e30=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{230,-453,-47,-631,543,535,-619,-710,-821,1000,-462,514,-204,-699,900,1000,-1000,-160,1000,1000,887,-328,-524,-667,120,-1000,717,-1000,168,164,-1000,1000,-286,424,-867,606,110,418,-1000,-507,-1000,-380,-1000,106,1000,1000,-1000,668,-1000,220,1000,123,-818,-371,344,-618,-258,-563,-75,1000,1000,-229,-92,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("JSON:eyJjb3VudHMiOm51bGx9", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-360,740,-44,473,445,633,-242,140,-816,-1000,-230,-320,897,352,1000,969,267,-420,-1000,-239,-468,50,904,342,-321,994,-42,-914,82,-359,967,-403,366,-60,-60,-1000,-549,548,596,-119,525,-711,397,418,-460,-1000,1000,380,-964,-1000,358,839,-88,-1000,467,533,-607,-1000,909,-532,384,56,29,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("JSON:e30=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-259,-300,-31,-846,143,-187,-560,-517,-156,1000,-37,-282,-449,-286,-15,425,-1000,159,1000,1000,121,-630,-602,-598,167,-1000,-101,-1000,689,136,-1000,1000,-1000,117,-414,1000,313,520,-1000,-144,-788,-380,-1000,-138,1000,1000,-647,252,-401,483,1000,-51,-604,976,-231,-126,-132,94,-560,990,311,152,218,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("JSON:e30=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsOmittedField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-357,887,-487,40,-984,-678,-873,747,585,-317,-201,335,206,855,889,-192,-394,423,-29,412,880,-592,-205,634,-585,-175,874,449,-126,664,250,396,-817,438,451,26,571,340,-77,-431,-393,-807,-715,206,940,-93,618,283,952,944,-256,978,923,-576,-82,-515,-564,906,-315,591,-228,683,-176,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("JSON:e30=", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsOmittedField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-915,-1000,719,1000,691,-32,595,321,837,-786,252,90,-767,961,1000,-103,-1000,-209,-352,-408,-1000,-638,181,-344,-528,446,1000,-1000,-1000,-403,1000,211,386,-887,736,-1000,-1000,941,-1000,-301,-1000,87,518,-886,-480,-1000,-146,-263,-578,35,-133,346,-1000,1000,-348,222,-55,-477,-466,-807,-458,760,384,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsPlaceholder(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{239,-348,-477,979,126,-247,970,-35,729,-200,947,36,372,-795,-659,221,-998,103,508,-979,-946,781,-565,-289,998,-882,495,-531,983,-523,971,-408,326,-952,767,-340,-747,869,256,-7,240,887,116,-28,-398,-659,170,892,-438,441,727,894,907,919,-234,200,589,-685,385,-571,-873,71,-615,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsPlaceholder(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{768,49,413,-892,-969,588,955,364,-163,-1000,-1000,-645,643,-802,963,308,193,829,-1000,-41,-337,-680,490,-389,80,-1000,-597,341,1000,-142,91,-111,-252,-1000,315,-1000,676,-976,-148,-1000,754,744,-1000,-769,-518,-967,-63,-1000,-625,458,-321,7,936,-260,83,-779,550,-264,771,-263,-1000,607,313,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignNullSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{-746,-322,-55,-566,763,512,-369,301,368,-279,-754,-217,-306,931,796,-141,440,-19,544,147,135,-608,-149,773,451,398,222,651,684,962,-732,54,-200,-168,-475,720,609,247,-416,-105,985,210,837,688,-109,-630,-734,-76,-80,-477,214,268,-465,484,788,-199,784,-339,-300,-507,320,48,-306,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignNullSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{-663,842,834,1000,-825,-211,-653,1000,594,-26,682,-120,518,956,-315,-336,1000,286,607,627,1000,484,-855,212,514,755,1000,-390,-825,234,-1000,-720,-139,-317,-1000,237,-714,24,12,-457,1000,-816,-952,-122,717,-953,-326,1000,-191,-86,1000,244,-1000,-787,290,-1000,-836,630,-1000,961,234,547,-529,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{-957,1000,-161,-176,215,395,-336,219,1000,1000,1000,-309,-486,238,-409,-552,172,-420,70,1000,-278,-255,-655,1000,163,-26,253,433,-357,555,231,1000,-1000,-848,-651,-923,-789,-1000,1000,788,-171,1000,-1000,947,593,642,10,-1000,-1000,4,100,-483,711,20,670,27,1000,-812,-1000,-841,-376,725,-441,-979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignSerializer(com.fasterxml.jackson.databind.JsonSerializer):void",
            new int[]{598,1000,-777,434,330,312,222,-723,1000,213,639,-549,-1000,417,-1000,1000,-694,1000,1000,1000,903,442,776,1000,1000,861,596,705,133,1000,-24,-797,1000,1000,90,-493,-46,1000,6,395,-867,566,-900,-313,-1000,1000,503,219,147,-1000,610,-965,1000,1000,245,-1000,-1000,98,504,1000,616,355,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignTypeSerializer(com.fasterxml.jackson.databind.jsontype.TypeSerializer):void",
            new int[]{-753,-1000,-996,1000,-85,-1000,962,147,618,649,-849,309,31,1000,708,774,781,-459,188,1000,1000,457,-330,-310,1000,235,-1000,205,-1000,544,144,-549,1000,-1000,1000,-897,1000,-612,-1000,841,426,-1000,858,-1000,-411,-1000,-159,378,1000,243,-271,-117,99,1000,1000,206,-197,-473,630,1000,-1000,-972,-856,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "assignTypeSerializer(com.fasterxml.jackson.databind.jsontype.TypeSerializer):void",
            new int[]{311,34,-166,-12,-556,-537,667,-1000,452,518,-1000,-654,-310,214,4,778,-391,47,-843,1000,-889,-725,-1000,539,-454,-454,-14,543,-791,51,698,-940,265,-465,593,1000,117,-2,120,1000,668,-733,303,-999,867,192,-1000,887,-504,383,1000,-374,49,699,520,467,227,-418,-709,-468,-1000,133,-542,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor):void",
            new int[]{461,375,-11,150,-407,-821,625,-268,-629,-819,483,-257,-283,0,-967,456,259,-572,444,-525,911,-555,-935,-786,-656,949,670,-91,488,-275,453,846,149,-583,485,-170,-154,-841,132,-212,-942,-103,826,-165,194,411,-524,136,838,-399,798,-524,-950,-344,-96,-456,869,-572,592,662,523,511,-50,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor):void",
            new int[]{-1000,-458,214,713,1000,864,-458,-931,-584,1000,-189,-346,-689,-456,903,-47,307,838,-327,-965,-945,643,447,619,1000,-15,-1000,842,1000,-102,342,-1000,928,260,-87,-75,-328,449,-727,756,1000,624,-397,968,-100,949,1000,1000,427,-229,-504,242,598,1000,-275,-133,-71,-1000,-101,985,-40,-729,-92,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-315,1000,869,-689,-978,356,84,297,-365,-1000,1000,672,1000,486,633,-390,-1000,-1000,1000,1000,158,-742,-374,398,-1000,1000,-190,-642,-1000,444,411,293,735,91,525,-797,-152,400,-1000,-151,1000,668,805,-1000,139,102,998,-798,169,-1000,-141,-628,-346,-701,612,145,-355,-879,-540,-827,1000,-1000,393,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-1000,1000,-1000,-36,191,-126,24,-548,548,686,146,-149,567,-1000,-326,-13,-167,1000,880,1000,-530,1000,406,1000,-1000,313,-95,151,-848,-193,970,438,209,329,-24,-762,226,422,-1000,753,447,1,-495,-764,1000,-360,540,-1000,577,-20,1000,517,-1000,-828,1000,1000,203,-309,-114,-1000,-922,-563,-192,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{-200,506,-753,263,28,-629,94,-515,589,-738,1000,535,1000,-399,717,337,-523,-433,-167,1000,182,-723,-839,128,363,101,481,-426,-883,-674,1000,449,171,1,1000,-504,-472,347,-19,-483,1000,95,549,241,31,-4,1000,-980,-744,-510,11,-251,-22,2,-260,-9,455,-1000,-923,400,1000,504,866,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "findFormatOverrides(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.annotation.JsonFormat$Value",
            new int[]{-51,-172,454,814,934,1000,1000,-1000,991,710,-1000,192,-705,-253,-227,-489,227,-895,1000,-978,-695,1000,-458,790,346,-739,-699,-1000,-391,-1000,-1000,485,152,-133,-1000,639,41,-613,1000,1000,-821,883,623,1000,-1000,848,252,-1000,-453,1000,971,-845,-654,613,69,-706,-1000,-496,999,-918,954,-704,-218,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "findFormatOverrides(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.annotation.JsonFormat$Value",
            new int[]{-1000,497,1000,869,-3,-1000,1000,-1000,590,1000,-540,1000,-209,1000,416,-1000,-1000,-693,713,-796,714,858,541,649,-431,-302,499,-1000,245,-383,-989,1000,46,-346,-708,995,542,-868,-1000,430,-21,160,-138,59,-457,1000,1000,-1000,-449,1000,1000,-1000,648,753,-821,311,481,-1000,-78,-142,1000,484,-195,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "get(java.lang.Object):java.lang.Object",
            new int[]{-545,-51,-391,108,-1000,1000,1000,-571,533,444,159,1000,-1000,1000,-483,-285,-529,1000,1000,1000,-411,-1000,-1000,185,-951,-1000,120,988,165,883,217,-60,-378,-1000,-1000,697,-1000,785,-1000,-637,-1000,-937,671,1000,-1000,-798,1000,709,728,714,-826,-553,691,-1000,280,133,902,-184,1000,-1000,896,-873,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "get(java.lang.Object):java.lang.Object",
            new int[]{145,279,453,-446,304,1000,261,165,212,-355,6,266,-681,602,-381,708,20,44,449,57,-210,-995,383,844,-721,-363,365,40,-648,273,-863,141,314,-819,-598,581,-852,1000,-486,192,-627,-567,230,120,-530,675,312,-475,704,171,803,234,892,-34,427,-10,796,57,538,-405,727,-1000,-933,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{945,400,-712,25,-445,995,-1000,-400,820,-400,-715,-435,279,1000,45,1000,-662,958,-1000,64,1000,-1000,-692,-1000,848,62,22,-971,1000,1000,128,-1000,351,-25,-1000,-103,-400,400,-1000,-1000,841,-844,-551,22,110,783,647,-1000,-365,366,1000,-1000,392,448,1000,-214,-309,969,890,-873,-318,-398,795,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{-299,-253,-61,-156,279,145,159,-216,-519,866,-50,-198,-226,-816,-857,860,-147,106,-2,-568,269,-948,-159,853,-815,406,756,-365,-22,-163,-742,411,-48,-418,975,202,576,-826,-5,833,325,-472,-205,-500,-531,-262,409,-306,-28,-556,882,30,-966,-775,928,859,-261,-878,-797,-20,800,-937,-376,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getContextAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{-671,-629,193,-470,386,-959,-112,-457,759,560,-654,474,-496,-243,-574,372,-764,308,479,734,-564,449,-82,-452,-282,-175,6,886,993,-475,-478,-755,-169,-798,422,-991,-662,-892,-378,-407,357,-139,564,-910,-77,804,-613,-402,-437,927,195,-229,226,518,-832,597,-556,897,85,952,-359,-47,195,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getContextAnnotation(java.lang.Class):java.lang.annotation.Annotation",
            new int[]{-563,62,-797,-791,880,737,310,31,142,606,310,-740,-351,931,875,-223,536,720,-788,166,-464,55,-400,258,-1,-205,749,34,-875,-885,-667,-991,-110,176,440,361,-891,172,-261,-726,629,972,-325,243,725,-378,-820,-612,-562,-906,-970,-747,818,-163,-731,-991,805,910,909,470,-150,804,-482,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getFullName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-377,218,429,438,296,-117,-170,684,951,-177,-812,1000,250,-67,882,-443,474,206,1000,617,-13,613,-693,-928,-1000,-342,-496,-1000,213,-1000,-169,-286,-250,1000,343,-1000,-1000,1000,1000,204,-1000,580,858,-1000,552,-420,875,1000,-180,412,-448,260,-226,601,-241,859,-2,-1000,-202,783,47,306,327,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getFullName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{619,289,-986,637,1000,1000,-1000,547,286,-814,-1000,423,1000,-262,175,-302,-952,-18,476,560,893,795,1000,70,-189,758,124,-426,137,-1000,171,236,149,819,-634,-747,-1000,-485,391,260,-233,711,1000,605,308,341,-543,767,1000,-429,-1000,589,380,1000,434,183,-708,-130,809,-952,-77,-491,-524,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getGenericPropertyType():java.lang.reflect.Type",
            new int[]{-14,801,-789,24,819,684,-443,755,572,-270,213,192,1000,1000,620,623,-1000,566,-1000,-262,343,40,-1000,823,79,-47,272,605,287,-822,396,-250,-1000,1000,1000,462,226,712,-367,-760,-121,243,101,-1000,-244,-82,647,2,877,-1000,659,191,-1000,648,974,419,-947,-724,-1000,-57,662,913,-329,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getGenericPropertyType():java.lang.reflect.Type",
            new int[]{160,-1000,339,548,1000,1000,-880,695,-1000,244,-344,-825,1000,852,-225,1000,-775,1000,1000,121,1000,912,-614,285,-1000,-850,722,901,-1000,-1000,1000,263,-15,-750,1000,940,-1000,-740,-1000,-1000,1000,832,1000,-1000,665,-1000,119,-1000,878,1000,-1000,-1000,-1000,326,947,-1000,902,1000,-239,-1000,1000,-591,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getInternalSetting(java.lang.Object):java.lang.Object",
            new int[]{57,-308,514,-228,-1000,-869,925,65,853,-1000,-1000,-978,316,40,331,-1000,1000,567,-1000,632,-192,1000,-753,-100,323,-464,-567,627,-380,1000,-439,-284,-470,224,81,1000,-156,1000,1000,-1000,416,-439,-5,1000,438,1000,388,-587,-635,1000,175,72,-1000,469,-1000,91,-8,333,-113,120,1000,-71,-360,-369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getInternalSetting(java.lang.Object):java.lang.Object",
            new int[]{-552,669,99,-721,268,-525,-321,573,760,-2,-522,984,-1000,-386,-154,-48,-9,561,-81,616,428,787,27,-521,-745,40,1000,-1000,-2,-1000,118,-69,-217,-130,852,-478,229,-1000,-769,1000,227,129,-1000,-1000,-853,-1000,-380,-804,-864,501,-1000,1000,-123,283,-185,1000,1000,-702,1000,450,701,-595,-278,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.AnnotatedField", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getMember():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{137,227,-241,-33,861,-161,-233,-727,765,-24,564,436,-233,265,-776,42,435,608,430,-828,-718,-918,832,929,-915,636,-359,340,593,885,-717,-600,-238,-756,710,-796,866,460,-7,31,248,-810,218,-988,285,692,-640,-407,209,-71,839,-615,-170,-576,723,-820,-72,-948,214,-58,-907,628,-60,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.AnnotatedField", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getMember():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{476,-455,-43,4,-206,754,744,828,195,-1000,38,-849,145,-701,-1000,-300,884,53,35,-817,-730,824,115,-415,-358,-683,-67,319,940,557,-356,690,-858,-359,438,-629,-692,-327,580,79,1000,307,467,1000,673,207,-524,-453,961,-83,1000,-231,-284,-336,-555,100,67,-948,-402,-637,120,534,498,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyMetadata", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getMetadata():com.fasterxml.jackson.databind.PropertyMetadata",
            new int[]{628,347,-199,629,-330,153,-134,-1000,97,-654,-167,596,-102,455,824,324,-368,668,-1000,-594,566,-448,-739,448,1000,-812,-648,-524,-188,-145,-677,-530,674,-7,-644,1000,-566,199,959,-81,-603,-682,-623,1000,1000,889,651,-605,403,-149,771,-229,197,375,-527,685,-689,-667,1000,217,-94,231,-36,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyMetadata", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getMetadata():com.fasterxml.jackson.databind.PropertyMetadata",
            new int[]{92,307,319,-262,-680,820,-155,-421,-329,-444,-197,-1000,-450,778,-354,202,79,-860,613,1000,-814,418,208,-473,-457,-834,1000,711,434,-434,-566,1000,208,-532,-405,557,754,-924,-98,1000,-1000,371,803,-964,-128,42,-207,310,-49,913,808,282,-1000,1000,684,153,-104,878,18,685,-639,193,879,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:Y291bnRz", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getName():java.lang.String",
            new int[]{-28,-1000,-719,-771,-204,724,997,16,-363,-534,996,-71,-377,714,-705,-99,-441,-1000,767,131,1000,-303,-1000,893,354,-458,-1000,1000,300,597,-664,141,-1000,1000,-522,625,-1000,274,-447,895,1000,103,-149,-113,-318,683,-508,-726,410,419,-1000,-648,-918,-1000,-72,44,129,530,-838,319,-1000,1000,197,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:bnVtYmVycw==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getName():java.lang.String",
            new int[]{-519,1000,564,1000,730,-600,1000,982,-314,854,659,1000,-1000,69,355,55,-915,-1000,1000,-155,-704,1000,41,-88,-247,-657,1000,1000,175,39,97,-889,178,742,518,1000,-62,-187,203,-120,912,-1000,201,942,-656,105,834,-567,-217,-683,419,930,691,-372,-1000,-1000,-59,-795,-328,-177,-172,603,860,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getPropertyType():java.lang.Class",
            new int[]{819,339,244,1000,1000,305,243,-497,1000,-225,1000,818,-203,1000,-1000,-534,144,1000,760,381,-1000,-621,-254,717,-1000,1000,-147,49,-577,1000,-299,1000,-321,-1000,-783,-304,-1000,-1000,-595,-1000,-1000,888,-513,1000,1000,175,-433,-220,-342,309,197,-635,219,743,-420,668,-1000,923,362,-952,1000,-112,1000,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getPropertyType():java.lang.Class",
            new int[]{332,-310,1000,289,-366,-480,1000,-216,331,1000,1000,415,312,-471,609,642,-1000,1000,610,-1000,400,512,593,1000,-649,-1000,-386,-2,670,366,1000,-745,-1000,132,-1000,-301,-1000,-1000,1000,-511,-1000,-1000,1000,-1000,661,990,475,1000,1000,610,916,-165,217,-995,953,821,555,741,-1000,-635,-1000,-965,-1000,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getRawSerializationType():java.lang.Class",
            new int[]{433,-186,519,1000,372,-13,238,202,302,994,814,818,-774,1000,354,975,108,-306,292,605,229,-45,684,-384,-6,653,622,943,-782,-224,282,420,-751,-777,999,-544,-237,357,563,254,174,271,622,-155,706,-663,-30,-275,991,233,-119,368,-4,-435,662,-295,898,203,548,-638,687,226,453,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getRawSerializationType():java.lang.Class",
            new int[]{63,714,519,-51,-1000,-1000,133,920,-144,1000,932,300,1000,798,1000,995,-543,802,637,605,593,-416,-1000,-384,-814,1000,174,-91,-284,-865,-852,163,-778,1000,616,754,728,4,952,-950,56,-638,95,1000,706,944,-381,85,-866,233,722,-631,-4,1000,662,1000,-1000,-1000,712,-15,1000,124,965,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializationType():com.fasterxml.jackson.databind.JavaType",
            new int[]{739,1000,-506,30,-328,-233,889,-573,-355,-940,573,-77,-255,-962,-1000,215,78,-215,-362,-355,189,658,157,-856,1000,-256,1000,1000,-362,-1000,-227,-316,101,-393,426,-1000,-248,394,-294,531,-609,844,140,247,-531,-81,-387,-57,-1000,-1000,-1000,385,85,703,229,42,-369,825,-420,62,-907,574,422,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializationType():com.fasterxml.jackson.databind.JavaType",
            new int[]{-453,1000,-551,-453,-719,357,1000,34,-393,-1000,322,-561,466,197,-1000,404,-184,-628,-178,-34,88,-1000,903,-945,1000,457,1000,1000,-539,274,-410,-540,1000,-855,330,-586,-1000,367,-929,462,-719,1000,385,548,-531,-380,-761,58,-1000,-1000,-1000,590,758,80,-831,-366,-133,-204,-1000,-10,-522,603,147,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SerializedString", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializedName():com.fasterxml.jackson.core.SerializableString",
            new int[]{-565,895,-541,-108,124,120,-632,531,1000,312,-303,-623,436,901,1000,305,-870,-68,-146,117,-973,1000,-595,-1000,-77,353,747,115,-268,617,72,-400,-424,-1000,229,1000,-1000,-310,-1000,-421,905,-120,1000,597,-429,712,11,126,908,422,-839,-268,1000,-420,-328,979,-15,-1000,-775,453,393,-1000,-1000,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.io.SerializedString", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializedName():com.fasterxml.jackson.core.SerializableString",
            new int[]{241,-916,-462,-722,-642,797,-128,-783,-599,4,978,165,-556,339,-946,82,131,-124,-531,-190,428,-408,309,414,844,177,-340,886,-626,-146,-471,349,712,858,740,-745,260,544,288,826,367,682,-321,982,863,-528,-403,350,-557,-983,861,-396,-251,571,103,-661,95,744,844,-48,-295,20,716,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializer():com.fasterxml.jackson.databind.JsonSerializer",
            new int[]{-301,337,-21,-297,183,-413,1000,301,-675,-137,-956,-571,408,-348,781,137,-55,-3,755,-834,-351,134,359,115,140,-796,218,147,-1000,797,-201,53,-385,279,-124,442,187,879,536,-523,249,-869,-22,635,-97,491,1000,-302,223,-518,-1000,221,801,505,-344,-8,-1000,-287,263,294,-1000,238,-6,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ser.std.StringSerializer", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getSerializer():com.fasterxml.jackson.databind.JsonSerializer",
            new int[]{299,-900,869,458,1000,-1000,1000,1000,-1000,1000,1000,1000,-425,-252,450,40,630,983,-558,-763,1000,134,-824,1000,-619,1000,-298,169,819,-891,-1000,1000,1000,279,1000,-439,440,-625,1000,1000,1000,138,1000,1000,-665,228,-461,-302,96,-518,-1000,9,242,1000,-363,48,290,43,-843,-306,194,357,-6,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.MapType", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getType():com.fasterxml.jackson.databind.JavaType",
            new int[]{-341,-502,164,365,1000,975,-819,-230,96,-1000,-383,-483,-207,-401,1000,-181,-274,239,-35,-786,-370,-967,-276,-361,-1000,229,1000,-1000,-705,570,-646,1000,-604,-265,403,-118,1000,308,-506,1000,-267,741,1000,1000,1000,-214,-656,-265,603,35,253,-297,-990,-879,1000,-247,-615,748,547,1000,910,128,174,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.CollectionType", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getType():com.fasterxml.jackson.databind.JavaType",
            new int[]{965,-159,389,876,179,237,1000,92,-213,262,-1000,-55,-621,-662,426,363,36,532,-51,668,208,-565,1000,556,34,1000,362,-46,-600,-1000,-108,1000,1000,138,120,-172,-78,1000,-837,1000,-1000,306,-300,342,226,185,-579,-563,-512,-1000,-427,317,906,-5,-1000,-654,986,499,1000,-66,818,-892,625,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getTypeSerializer():com.fasterxml.jackson.databind.jsontype.TypeSerializer",
            new int[]{-701,-168,499,657,947,888,61,302,-392,209,702,-944,211,-632,20,481,-651,-895,-472,793,484,401,305,-370,-754,-896,-170,-534,298,942,539,-877,525,781,500,-336,194,-934,985,771,155,-489,719,664,-811,-670,79,595,-865,557,660,-9,576,333,240,451,550,-568,429,-44,10,-241,38,896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getTypeSerializer():com.fasterxml.jackson.databind.jsontype.TypeSerializer",
            new int[]{798,-1000,431,214,-105,-1000,205,145,-900,-1000,-149,1000,-356,-93,794,315,-396,-171,-1000,404,639,792,-1000,-1000,-1000,109,296,-1000,-620,1000,122,639,-563,807,173,811,-556,-544,187,623,-370,1000,-940,1000,-170,862,-663,711,562,-452,684,981,1000,453,1000,-1000,1000,-1000,1000,829,-154,-1000,-896,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getViews():java.lang.Class[]",
            new int[]{616,-686,-129,-496,1000,-1000,721,-1000,-1000,370,1000,-503,-160,1000,117,613,196,-429,335,967,-151,-750,203,-216,-582,1000,75,-703,954,1000,-1000,-1000,730,-77,201,-1000,-1000,298,-1000,-1000,-1000,636,-1000,-553,-1000,1000,-448,1000,2,-746,-94,-1000,1000,1000,195,390,497,-1000,-384,-588,1000,-844,-60,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getViews():java.lang.Class[]",
            new int[]{701,-1000,379,171,1000,-160,1000,-270,-937,232,1000,386,-145,676,231,-75,-512,-478,906,182,205,-564,200,-497,-389,-9,1000,-1000,1000,1000,-1000,-1000,-299,510,-253,-1000,-601,457,-720,-643,-1000,-333,40,-740,-1000,566,896,858,-1000,-294,884,-1000,122,763,757,299,741,-1000,277,-927,754,-589,-72,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getWrapperName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{873,1000,-151,162,1000,-1000,1000,113,215,227,-540,238,-1000,-1000,-284,-1000,-774,298,1000,-364,20,-1000,-1000,787,-1000,-1000,-1000,-829,76,-817,-947,38,-185,-1000,292,1000,-1000,-877,504,-882,680,-517,-893,-1000,-1000,-1000,905,-134,1000,1000,931,1000,27,1000,767,684,378,-1000,-997,-1000,-294,-323,1000,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "getWrapperName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-7,1000,184,-993,-839,-301,1000,203,-89,707,-508,1000,-993,353,925,-84,562,439,953,106,531,-493,-178,-290,641,-111,15,-520,-317,-225,-427,680,-207,-1000,-542,378,-1000,485,-848,-126,794,348,252,478,319,1000,339,712,977,-401,-347,442,1000,-687,830,530,-80,487,971,124,-220,584,-998,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasNullSerializer():boolean",
            new int[]{-394,-1000,413,-271,-65,393,-423,421,-930,-231,75,144,1000,-1000,-1000,-666,681,-721,-711,-1000,319,152,-206,-53,1000,546,802,253,597,412,-870,608,720,-395,759,-328,46,-668,211,-697,1000,50,361,576,-418,-664,281,636,-436,-831,-321,1000,287,844,-1000,918,289,114,420,-846,-9,-101,548,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasNullSerializer():boolean",
            new int[]{37,1000,-672,-281,1000,-1000,1000,-1000,645,1000,-247,546,-355,1000,1000,-1000,135,32,-429,-3,785,592,94,418,-535,68,-264,811,1000,-1000,-1000,-7,241,-586,-296,-231,-1000,-511,167,1000,-300,15,1000,-1000,710,-43,-1000,162,225,359,1000,-281,-981,43,1000,652,-276,580,836,-116,961,665,-294,295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "hasNullSerializer():boolean",
            new int[]{1000,414,775,423,279,1000,-348,1000,-1000,-1000,-798,-593,181,839,1000,-436,-663,-906,-230,542,-1000,209,43,817,-1000,1000,-1000,-1000,-667,1000,430,-863,969,-382,391,-1000,-888,276,-704,29,1000,1000,-747,1000,-1000,555,668,392,45,-339,-737,-697,-681,-923,1000,217,605,-668,-1000,-201,-1000,-1000,1000,97}));
    }
}
