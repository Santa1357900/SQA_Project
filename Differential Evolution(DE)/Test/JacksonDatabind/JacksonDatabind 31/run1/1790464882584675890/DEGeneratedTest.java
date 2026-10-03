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
            new int[]{-1000,-19,-693,-999,330,1000,-1000,213,206,532,937,-747,1000,100,955,-507,-152,489,-706,-1000,-1000,-1000,-630,830,-189,-1000,556,-1000,-1000,-956,643,-647,-719,-826,-717,205,426,51,18,-1000,-1000,-331,514,1000,957,1000,1000,-1000,-764,-1000,-981,-1000,140,706,-741,759,-1000,-1000,-1000,97,-1000,433,-1000,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "append(com.fasterxml.jackson.databind.util.TokenBuffer):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{-659,-89,50,523,1000,659,-632,-164,-303,-222,-320,-796,833,-163,-394,-699,1000,-583,-1000,-499,-752,-578,949,1000,-242,273,1000,-1000,-577,812,-361,1000,187,-341,-1000,106,-347,-143,-117,-933,262,-1000,-606,309,773,185,182,698,-252,-15,-50,175,21,5,-298,-190,-1000,166,-1000,138,-189,6,18,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser():com.fasterxml.jackson.core.JsonParser",
            new int[]{-376,189,349,-550,-624,959,-1000,-813,1000,-657,-744,1000,1000,-507,-1000,358,-703,-528,1000,841,957,-342,605,-887,-651,-1000,1000,-812,917,1000,-198,-51,690,1000,719,-161,-1000,652,-201,-1000,1000,999,-1000,-1000,554,1000,-330,1000,718,366,346,-1000,-437,208,-796,-1000,-917,-707,-1000,694,-1000,-500,-693,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser():com.fasterxml.jackson.core.JsonParser",
            new int[]{1000,297,-153,569,-509,-1000,949,939,-541,-68,1000,956,129,-1000,-1000,-1000,-141,-1000,-434,-679,-1000,1000,385,265,1000,1000,-1000,1000,-727,-471,133,-45,-1000,1000,914,-1000,424,-712,-1000,-372,-1000,-1000,81,1000,-1000,-1000,17,-1000,-1000,-191,464,1000,1000,-1000,836,-442,948,-441,1000,-101,915,1000,1000,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.JsonParser",
            new int[]{-696,-835,-86,-163,651,112,322,862,-926,-455,637,-565,-753,-555,495,511,-399,126,507,762,-774,499,168,241,368,-970,661,756,-267,-899,-305,-942,172,-542,-847,453,-240,879,44,452,-258,315,999,677,384,-655,408,410,386,988,160,884,-845,-323,932,-318,25,-512,962,-502,250,-365,337,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.JsonParser",
            new int[]{147,-551,179,-345,-431,803,173,1000,-1000,1000,205,-404,1000,-1000,-987,480,371,920,-1000,-887,-991,837,-4,-855,-476,670,808,28,1000,-1000,243,-863,375,-283,588,20,-920,260,40,-959,-1000,-296,-1000,296,-647,1000,311,762,1000,386,-198,-239,1000,201,-259,-241,-591,-141,-1000,693,228,406,817,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonParser",
            new int[]{487,689,-32,668,1000,1000,-383,818,-209,-371,418,1000,-372,-849,-378,51,537,933,1000,204,936,-453,-313,-183,951,731,39,714,-757,-309,-1000,-217,-267,-1000,-328,-288,-1000,-163,-928,-1000,478,-460,588,358,784,973,-589,70,632,-620,-1000,1000,-620,1000,296,-1000,969,518,-1000,431,-854,175,1000,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonParser",
            new int[]{-117,-415,-756,641,277,451,535,524,-8,1000,-902,247,660,-656,1000,-605,206,1000,-1000,1000,-66,1000,-69,266,1000,-102,-831,-518,267,-379,-949,-1000,417,-271,-1000,481,1000,-767,-13,-158,985,-669,476,156,-743,224,-720,1000,775,252,-706,50,-1000,415,-81,-1000,1000,-219,-281,51,-155,1000,317,909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteBinaryNatively():boolean",
            new int[]{-227,-131,324,1000,291,-324,-316,482,-588,733,-319,350,-400,-171,42,-499,400,-210,-71,-223,-619,-325,-324,-90,-1000,-171,-400,536,-182,-278,340,-569,170,499,1000,14,110,-135,539,454,-593,559,-735,971,686,-299,-265,-36,400,685,220,-506,624,439,379,1000,-215,-98,-381,-647,400,-460,844,412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteBinaryNatively():boolean",
            new int[]{-471,769,-676,657,-828,852,-1000,478,531,602,-40,-1000,875,717,52,681,-562,738,1000,-712,557,617,235,94,-4,-16,908,1000,-244,859,35,478,513,-888,-751,-506,-993,1000,403,-1000,1000,178,-979,463,-132,684,123,-529,241,-479,-796,-719,-979,561,-664,-559,398,231,208,720,-145,-222,-271,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteObjectId():boolean",
            new int[]{1000,-259,798,970,-473,1000,-1000,-311,-1000,1000,-507,-154,703,-1000,-106,-1000,1000,873,-1000,566,-1000,-302,-723,1000,-732,-9,844,-1000,-96,1000,-1000,-668,1000,-1000,-1000,-943,1000,48,1000,254,1000,-945,576,-910,-234,1000,168,-1000,1000,-571,1000,1000,-607,-27,475,1000,431,1000,-79,-761,-728,-216,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteObjectId():boolean",
            new int[]{-1000,561,-41,-380,1000,-1000,936,-189,1000,-1000,906,670,-1000,215,1000,-77,-629,-1000,690,-645,902,512,806,-1000,43,-1000,-1000,278,953,-967,1000,1000,663,780,454,1000,649,1000,-183,-1000,-1000,177,-1000,606,712,-239,-417,513,-1000,-922,50,658,-851,520,782,112,497,-465,244,1000,69,-451,885,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteTypeId():boolean",
            new int[]{-255,369,-941,345,490,1000,-352,-309,-510,335,-1000,1000,162,-1000,-1000,889,915,242,277,-524,270,-1000,1000,159,-559,82,-964,-868,-471,1000,-614,465,-1000,881,945,-346,1000,613,23,177,97,-357,-1000,1000,285,1000,-552,-1000,-888,-29,400,826,-786,423,991,-1000,-1000,1000,-48,529,439,-394,278,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "close():void",
            new int[]{-489,-507,14,314,337,293,569,329,-50,685,-779,132,-353,1000,-154,-577,0,-504,810,-90,-651,-353,41,513,74,-261,402,674,53,1000,164,-108,1000,1000,-617,1000,0,0,-266,727,-830,-532,622,0,-746,-543,278,486,346,785,-1000,-165,436,84,-892,149,209,352,249,60,692,-16,-748,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "close():void",
            new int[]{83,633,148,-1000,771,-492,1000,-367,-1000,-418,1000,575,1000,-387,1000,608,-258,-1000,-1000,-722,1000,-633,399,-269,270,604,1000,-585,-375,645,-109,-1000,-1000,-777,61,173,-645,1000,240,1000,477,-180,-1000,-819,1000,89,-211,140,-416,-1000,-868,-875,-326,-1000,-468,-410,103,438,235,-1000,-276,708,-755,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{47,397,-556,-783,793,-208,770,-882,-532,626,-559,554,-133,619,47,-258,-884,148,622,-678,-923,-278,956,493,766,-809,-577,226,-752,674,558,540,-644,695,417,586,-229,-586,869,-272,565,-44,-571,-815,-871,874,-57,894,-461,-486,-477,-770,918,-282,494,-40,-917,-354,780,-612,895,802,186,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{326,-77,-658,648,657,-317,-1000,-655,-1000,-1000,-858,594,-1000,647,731,-384,-966,1000,139,-984,-1000,542,274,112,-515,906,-823,-1000,1000,1000,594,1000,687,-580,1000,983,-1000,-175,590,1000,-580,460,-579,-915,-1000,-941,392,682,371,-329,625,1000,1000,-623,220,580,250,-887,-1000,1000,-593,-1000,672,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentStructure(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{429,805,378,242,-1000,76,257,787,156,1000,301,941,-150,-148,-388,501,-284,672,217,598,263,549,793,192,-711,-1000,-314,-1000,-796,-132,213,-150,1000,348,-1000,-1000,349,-122,-862,-1000,-317,-170,-1000,1000,-621,-1000,140,371,-486,364,462,96,-67,1000,-1000,-1000,-186,-870,16,1000,-1000,737,42,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{290,655,939,969,397,307,727,877,489,-638,558,271,471,-46,574,-770,853,-571,86,399,232,1,-916,-603,-855,313,290,185,623,-965,-581,732,-693,579,846,-392,-926,75,-975,-565,-208,879,685,530,-966,-980,-917,-461,369,-720,-591,-642,194,841,-183,241,-24,854,-521,-386,240,-344,-396,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{446,-207,-458,345,-421,389,417,-810,-610,923,-910,872,225,-569,-116,-727,466,166,-6,344,-78,-704,90,-949,260,667,-508,527,-985,-541,754,-516,-804,-518,56,-829,667,981,929,503,57,-144,-471,-598,-729,449,-73,-185,361,230,-301,-603,935,37,-528,455,-843,-112,-977,353,855,-191,-961,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{1000,837,394,-206,-133,-163,817,43,798,-45,321,-334,420,-285,899,84,-247,-552,-441,-9,298,-970,-397,445,-283,1000,-680,-622,79,-1000,738,-104,-769,-481,-178,59,-579,-725,24,546,-613,-640,451,-984,-26,-198,734,-1000,-376,-106,-643,-1000,-466,135,-749,-653,-1000,-819,78,465,-865,1000,-104,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "enable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{548,517,-51,315,374,-23,-982,-503,-588,-247,593,929,921,-160,-828,-873,-617,-718,569,445,-90,270,-461,-933,-236,-958,706,-659,34,-445,183,491,-738,680,-733,20,-510,-55,-414,982,-968,778,-827,-314,-879,-417,-934,379,-490,41,252,-6,-906,554,-961,-881,-342,887,-741,-958,-7,-239,116,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "firstToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{820,-263,-446,-157,737,356,-574,993,-367,-310,542,-560,-473,701,225,-846,36,-612,1000,919,-523,495,-377,510,655,-306,-14,-459,-755,-605,1000,10,434,949,301,-227,-488,-954,276,957,-200,764,14,328,411,935,363,-837,-559,868,-487,1000,893,-813,-544,-650,461,219,-697,1000,-620,-791,-402,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "flush():void",
            new int[]{-916,117,389,-428,428,-868,338,1000,664,1000,923,-130,626,-1000,615,1000,123,776,1000,-1000,-339,1000,31,-552,1000,-779,672,-26,417,-471,357,989,415,-123,1000,-143,-535,333,-882,-333,123,1000,1000,94,-1000,358,16,564,-1000,515,-1000,418,-1000,-1000,1000,342,1000,-811,-1000,-1000,-823,-330,796,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "flush():void",
            new int[]{1000,-315,-336,-623,560,51,280,-1000,1,-1000,-1000,1000,751,943,-1000,-1000,347,-413,-1000,1000,774,-1000,1000,805,445,-779,-16,-61,-1000,-844,-1000,1000,1000,-561,203,-727,-267,-765,-879,556,850,-537,-1000,180,990,-339,246,1000,1000,1000,380,343,787,1000,-1000,786,-991,-279,731,-479,1000,598,-1000,749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "forceUseOfBigDecimal(boolean):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{799,597,413,168,-87,-646,-590,1000,-148,-755,-1000,305,1000,256,37,404,-393,48,380,308,-85,-78,-296,755,714,1000,-1000,-920,651,-404,-927,-999,-748,965,-841,-265,51,-218,534,-264,61,160,62,35,793,839,513,149,-261,178,-615,1000,-455,90,-428,-656,-542,65,850,-635,-414,901,-297,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "forceUseOfBigDecimal(boolean):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{-896,-163,-406,394,237,-341,-81,1000,1000,-330,-264,-111,-955,-767,170,305,-824,399,201,682,-789,241,619,-220,-1000,-1000,141,-1000,697,1000,516,-171,242,372,-197,-423,-416,-425,753,-679,-529,154,-826,284,-380,-353,-260,438,338,-998,-224,-688,139,-522,-841,426,-147,231,832,400,-562,-91,-820,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{593,137,799,-738,-447,-30,793,-685,835,-681,-1000,-286,240,-777,-473,34,-942,-479,104,98,1000,-533,-1000,100,-110,563,-411,226,-776,-627,1,621,-323,907,862,-1000,-1000,-60,-603,23,1000,1000,456,204,557,-642,-348,1000,-896,508,-159,-1000,-189,-1000,-1,-1000,470,31,656,26,-148,-270,-766,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzE=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getFeatureMask():int",
            new int[]{-819,-147,-556,837,929,746,264,-422,399,1000,678,-531,839,558,1000,-446,-834,1000,102,249,1000,248,1000,-171,-477,340,273,-1000,-1000,-557,-413,133,1000,-776,1000,238,-349,1000,636,476,-861,-342,-375,-877,360,-354,337,605,-728,47,-860,-769,219,186,-1000,50,-1000,-301,-1000,-838,-297,93,-1000,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getFeatureMask():int",
            new int[]{-243,125,824,-77,-1000,784,11,-566,-1000,-1000,961,144,464,1000,-1000,1000,1000,-324,-738,928,-1000,-278,-735,-841,-447,1000,240,633,1000,190,-1000,1000,-895,1000,-1000,685,810,902,543,901,-660,-246,-908,977,-683,1000,1000,-57,-1000,-17,-574,1000,462,240,1000,-107,1000,657,482,280,-105,687,304,539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.JsonWriteContext", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getOutputContext():com.fasterxml.jackson.core.json.JsonWriteContext",
            new int[]{185,-603,-912,-989,-1000,720,761,874,-34,-285,-792,209,553,-273,354,-30,-1000,256,225,1000,26,1000,416,737,-16,1000,682,200,1000,-493,653,-259,-135,-1000,-196,-684,-1000,685,127,408,348,154,-441,-560,163,-326,767,84,-44,242,707,303,-47,858,-1000,239,-493,1000,73,251,-474,562,-830,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isClosed():boolean",
            new int[]{1000,-491,-46,106,-1000,493,759,-333,493,-360,649,255,-775,313,-1000,554,-38,-439,860,-33,-800,172,886,752,-649,405,-475,-1000,-355,1000,190,858,390,-1000,-5,-253,-1000,773,0,731,1000,1000,-587,-1000,-932,-384,-1000,1000,-717,-553,1000,158,-785,-179,47,1000,1000,148,-861,14,-123,-458,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{355,437,-751,-437,1000,304,-1000,-1000,-470,-844,-1000,-1000,-151,-661,-1000,-341,-763,114,1000,1000,-1000,-924,114,1000,-493,1000,-975,1000,470,-725,-719,-336,960,-350,-1000,1000,-478,-1000,-278,-1000,1000,1000,-944,314,-1000,1000,-485,1000,1000,-164,210,1000,770,164,604,-1000,-240,1000,-407,-1000,1000,-179,-369,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{886,949,-917,-846,432,-225,-335,-277,196,970,-1000,-21,26,25,-207,-424,-1000,756,917,1000,-538,21,-801,336,-235,1000,-1000,-88,1000,-16,1000,-587,-213,1000,1000,1000,-865,-1000,430,-266,469,179,-183,1000,-334,806,1000,-437,926,-1000,1000,-499,1000,-531,-251,-122,-664,662,427,275,1000,1000,-1000,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "serialize(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{867,595,604,-951,819,-795,367,-946,-568,-14,-623,1000,-622,1000,857,-72,570,-117,-970,825,1000,-911,622,401,862,609,-1000,1000,989,6,504,-1000,-969,274,191,-1000,790,-1000,1000,-1000,580,1000,-617,856,797,-1000,-1000,-360,-484,835,286,-439,-419,-1000,-750,-498,-474,-121,-955,-749,896,1000,789,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "serialize(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-1000,-475,-231,-543,1000,-758,274,403,-77,1000,1000,-814,154,-2,936,-350,493,121,-467,466,1000,-44,139,-1000,1000,133,-645,326,158,140,442,741,-1000,-486,-1000,-627,1000,235,244,-160,417,1000,997,1000,1000,-693,1000,1000,911,1000,-549,-529,-43,979,215,-806,-719,-93,-179,209,1000,89,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "setCodec(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{111,-279,568,545,-270,672,587,-471,955,-1000,154,-354,79,248,1000,1000,-559,80,-444,397,-751,48,1000,-495,-78,-5,-98,-723,-745,165,-1000,1000,-660,776,642,1000,46,-644,120,400,683,-1000,34,225,48,397,-1000,-139,-507,422,1000,419,503,-867,1000,-1000,331,56,760,268,-165,1000,-158,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "setFeatureMask(int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{522,925,-92,-592,-223,-902,547,-39,963,-268,938,152,929,871,-968,-563,825,-945,23,-979,309,-879,872,-384,-737,-427,-83,-498,-853,45,370,538,11,-195,-332,786,61,270,-179,-102,708,51,-807,-451,-953,177,-543,-174,-166,-156,891,-261,209,-727,602,-238,635,95,-204,693,845,-965,-183,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:W1Rva2VuQnVmZmVyOiBd", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "toString():java.lang.String",
            new int[]{-845,197,-831,946,-594,-8,-629,358,191,496,-227,-378,-761,-291,668,937,-160,-226,534,348,-82,-967,-940,977,158,-511,-407,582,-415,353,-630,277,342,693,304,-173,-687,-475,-996,-408,721,135,228,-125,850,903,-979,-786,-353,-566,-563,-16,114,439,-93,557,-887,455,-921,246,510,246,-715,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:W1Rva2VuQnVmZmVyOiBd", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "toString():java.lang.String",
            new int[]{-678,-329,764,284,-470,-258,825,100,-1000,-131,1000,159,-9,-322,896,1000,-352,-661,384,-1000,517,-396,-283,-53,32,1000,1000,333,-1000,746,-1000,231,455,-302,1000,-461,46,-240,1000,-1000,-1000,654,1000,-458,897,476,443,436,97,884,-133,-130,-186,-100,1000,544,959,605,853,-1000,375,-546,205,-476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "useDefaultPrettyPrinter():com.fasterxml.jackson.core.JsonGenerator",
            new int[]{950,-691,544,-30,400,1000,201,379,288,-1000,1000,-1000,-374,-258,1000,591,1000,-312,561,990,-503,-1000,-1000,-568,-1000,652,453,-492,246,-563,-978,246,-734,-889,391,240,1000,976,-394,1000,462,-1000,-913,-1000,-1000,959,1000,-535,1000,370,134,-830,-25,-1000,637,-678,-1000,-73,-1000,-381,619,-27,-1000,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "version():com.fasterxml.jackson.core.Version",
            new int[]{-466,857,-697,523,-502,644,-823,-550,-754,-443,-641,1000,-228,-302,584,114,-829,-1000,-58,-43,-1000,-901,-655,-953,-1000,-967,-1000,-644,-725,524,-690,134,602,493,-541,948,-1000,-1000,805,-733,1000,-95,168,1000,1000,522,-988,-576,-687,-381,79,1000,-339,76,1000,190,-1000,-561,1000,1000,953,-312,1000,523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{553,-135,617,-502,1000,-63,914,149,342,-16,663,1000,1000,-132,-371,1000,-82,-718,521,1000,-363,223,-554,-461,56,-912,316,-48,-1000,224,-370,28,-94,-190,-240,-1000,-839,-1000,472,316,697,-195,593,-112,-805,1000,186,-152,-209,1000,-253,686,176,-805,775,-843,1000,-1000,861,461,-161,-1000,-25,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{1000,253,-656,513,-45,-109,-148,517,334,781,-145,769,-403,-960,-929,400,756,-232,872,-468,-582,-542,840,715,83,-583,-37,-892,170,1000,-1000,213,-705,1000,-80,-729,1000,-787,508,-1000,474,-964,1000,-135,-333,1000,969,493,-1000,393,89,364,-242,-906,-1000,-1000,699,248,-1000,700,-25,-872,-189,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{-189,633,-312,-814,-379,-1000,-679,-766,-785,-423,-594,839,209,-1000,365,750,1000,-1000,-883,-809,-230,1000,-148,360,1000,1000,645,1000,-1000,182,-25,131,-1000,-683,853,-537,758,-1000,1000,-1000,-25,-637,204,565,-6,1000,450,391,525,-1000,975,441,1000,-459,-341,-493,-1000,667,1000,1000,-1000,-557,-388,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{190,-643,-191,824,1,427,-81,861,1000,-1000,646,24,1000,1000,1000,1000,-1000,639,1000,45,1000,-389,325,1000,-619,-424,-1000,-651,284,-964,930,1000,1000,-1000,-1000,1000,-1000,360,-108,-1000,766,1000,-1000,1000,1000,-1000,-966,-920,1000,-340,-1000,417,92,172,1000,-292,1000,-1000,-320,-1000,-1000,294,1000,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBoolean(boolean):void",
            new int[]{414,757,-197,153,322,656,1000,299,740,4,604,-139,-984,440,-834,-360,-128,1000,-972,238,448,1000,879,1000,-834,1000,-637,-119,545,-865,1000,740,-327,-1000,-1000,-381,164,-114,494,1000,791,-456,1000,912,6,613,-974,-790,306,647,358,97,902,-1000,719,775,-174,-477,830,-389,1000,1000,-483,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBoolean(boolean):void",
            new int[]{-1000,-159,-552,1000,89,482,440,603,-559,-1000,-287,-1000,-317,1000,-834,-241,10,-640,-1000,238,1000,1000,1000,1000,-136,67,801,1000,545,-865,1000,1000,96,-943,-1000,-488,-1000,-959,248,863,791,-1000,986,-136,-321,543,-737,-790,297,1000,-446,-702,902,-839,1000,956,495,-93,1000,789,1000,153,-622,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndArray():void",
            new int[]{-403,589,-718,306,-1000,116,-719,829,577,102,1000,178,-434,-1000,-444,-1000,-509,-204,-949,-1000,464,1000,316,-1000,-933,246,-271,1000,291,-70,-832,-256,809,716,-25,949,-115,1000,917,1000,683,282,-586,-888,-748,-1000,-781,-145,-62,-264,-121,600,-610,-859,-1000,-709,525,-700,-1000,160,346,-766,-729,-509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndArray():void",
            new int[]{-1000,17,-936,-1000,-37,-1000,207,-291,577,-345,1000,-1000,-208,524,-1000,-286,1000,1000,-949,-78,1000,-22,830,-859,-362,-210,1000,-849,743,-1000,1000,-984,148,-1000,598,-249,-735,-1000,407,-799,-701,1000,-200,1000,-351,-806,478,117,-424,-872,187,-818,92,-33,-1000,906,104,-1000,1000,897,368,-139,-247,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndObject():void",
            new int[]{1000,269,69,-950,749,-692,1000,-269,971,-757,-519,-1000,-1000,1000,1000,-1000,1000,1000,-923,-955,-944,310,-267,880,1000,137,510,-604,-161,-106,136,-474,344,600,363,747,279,676,-1000,-89,89,-307,1000,556,1000,-235,631,-994,-1000,661,1000,172,582,963,-1000,-1000,710,221,1000,72,1000,-722,1000,-695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{723,729,-552,57,-510,-124,-195,-899,250,372,419,-279,660,-736,1000,759,-18,-963,830,-86,-1000,1000,219,-117,1000,-985,1000,813,761,-431,511,-378,332,776,719,467,358,665,4,-871,-193,123,942,108,348,1000,617,-1000,-1000,586,253,765,-667,69,847,699,-345,1000,-1000,-468,1000,495,-993,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-91,673,394,537,-317,-316,259,-612,-124,262,-875,1000,-171,285,-400,264,829,-826,73,-235,-36,-1000,-400,-813,-977,-442,-739,861,-70,490,-832,101,1000,249,-335,-1000,92,700,195,114,160,-435,284,-800,385,-1000,242,-203,562,-799,495,835,206,1000,-773,403,320,-1000,-364,-677,611,-1000,-192,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(java.lang.String):void",
            new int[]{-929,-83,218,395,-1000,-308,-70,197,382,601,4,1000,-838,-66,1000,609,921,24,595,-622,-284,969,225,777,60,-356,-318,1000,494,-1000,1000,253,566,-447,-89,-12,-176,-1000,-442,582,184,1000,1000,-250,-1000,-565,761,-593,630,903,-437,-933,-1000,1000,814,-1000,-235,491,-1000,1000,1000,-750,614,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNull():void",
            new int[]{76,421,-242,209,187,318,133,898,-705,100,-394,-959,789,-435,-532,363,-343,981,650,339,69,-219,480,-379,-11,843,-555,-657,414,142,-755,725,-875,363,-241,-553,-322,808,396,-714,-231,823,775,-275,-919,-116,641,378,-746,473,-466,-599,-281,-89,808,-659,772,445,323,302,-469,-959,-631,748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(double):void",
            new int[]{223,497,324,754,-1000,-616,-351,411,-693,-388,-1000,-648,1000,758,1000,-161,780,-497,-767,-757,-645,-211,683,-497,-947,-606,-719,-286,60,52,351,-318,1000,-162,415,227,1000,-977,-504,-1000,31,-1000,1000,620,392,666,725,-1000,-1000,333,-649,589,-755,307,136,91,-306,-211,100,-1000,326,58,-1000,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(float):void",
            new int[]{679,-59,-832,787,-1000,-1000,762,-1000,-630,-967,17,-1000,-59,-1000,1000,-578,1000,-323,-1000,-1000,130,-140,1000,60,-1000,-643,-762,-1000,-124,-45,1000,-1000,-681,-960,835,1000,574,895,604,523,554,-105,1000,1000,78,-1000,400,-1000,-83,-620,98,233,101,-952,1000,43,1000,1000,-1000,732,-650,-682,1000,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(int):void",
            new int[]{84,-567,-721,602,-86,270,-117,-243,-618,-255,993,-1000,1000,-178,213,105,1000,956,449,624,-1000,684,353,111,-448,797,-1000,158,1000,841,547,201,-721,1000,-468,-626,180,-50,1000,1000,22,-57,170,-1000,-508,-475,1000,1000,333,-1000,-265,-1000,297,-789,-908,-94,-1000,-605,1000,880,-1000,-507,-638,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.lang.String):void",
            new int[]{1000,-227,-208,-862,-865,-95,-399,-608,-166,-295,-1000,-437,-479,289,38,463,-302,232,1000,-65,-364,-1000,-472,458,263,732,-518,-988,-214,-705,731,261,353,160,-135,817,272,-71,175,295,-1000,589,22,597,414,70,-579,-213,-517,-246,732,76,-659,933,74,-87,655,-799,-1000,639,1000,752,-1000,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.lang.String):void",
            new int[]{815,-771,679,-119,-605,-975,-82,-871,1000,330,-807,-23,-976,143,-1000,-1000,-1000,264,123,-1000,-462,1000,-145,1000,1000,-218,-94,447,-667,-150,318,-1000,-308,55,618,1000,-254,-80,43,-223,-774,1000,894,-857,1000,747,735,-931,37,-1000,1000,-98,-828,-217,74,-1000,713,-1000,95,655,1000,-724,114,800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigDecimal):void",
            new int[]{442,-133,-1000,444,-383,-590,528,171,-900,912,-387,-644,1000,656,-540,-402,878,-6,-1000,-35,-405,265,-70,674,-391,848,-200,-1000,200,-681,1000,1000,-435,-407,-491,-976,685,968,442,978,1000,-1000,847,-1000,792,-1000,-298,403,-769,327,-621,1000,890,266,668,-1000,-75,912,175,-747,774,-664,-133,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigDecimal):void",
            new int[]{725,953,408,370,-390,-457,-733,-17,-123,-438,-111,-285,828,465,-697,708,-120,144,125,188,589,-233,476,-365,518,-254,-587,-914,-742,599,634,-560,-508,-468,448,652,393,974,37,973,824,-977,869,-947,-180,-232,-166,623,-325,-190,-892,736,554,-76,-940,-907,-955,-188,633,-149,276,-869,517,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigInteger):void",
            new int[]{-1000,71,905,-282,642,987,827,395,-666,889,-307,282,1000,-487,1000,-558,-329,358,217,-741,-554,-431,-758,550,-1000,807,-1000,-1000,18,-1000,-1000,-988,679,-783,1000,-1000,412,1000,-804,-632,-267,-1000,-793,-1000,-1000,-304,-1000,-603,1000,907,-830,-1000,-24,728,603,239,-7,241,-1000,896,-146,1000,-996,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigInteger):void",
            new int[]{511,801,-647,746,-448,-835,-615,-298,1000,-799,-381,847,-976,565,-1000,-667,-377,67,-82,-1000,1000,1000,-203,251,802,811,389,-38,117,545,-918,766,-1000,-1000,-1000,476,-400,955,216,1000,1000,89,1000,1000,-409,1000,1000,1000,-1000,865,26,-464,1000,1000,-1000,771,-776,236,388,-905,-1000,1000,1000,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(long):void",
            new int[]{-246,149,648,-277,-1000,-247,-748,1000,-1000,786,-1000,-747,-1000,-1000,873,-1000,-1000,1000,1000,1000,485,438,-166,1000,-1000,-469,725,1000,-1000,1000,-847,-726,-1000,-559,1000,153,-1000,1000,947,-707,1000,-1000,22,-1000,-902,-780,-1000,914,245,1000,1000,556,1000,-1000,1000,1000,-1000,1000,-1000,1000,-1000,-1000,-1000,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(long):void",
            new int[]{-1000,-807,-722,9,-1000,-61,-1000,1000,-1000,-465,-1000,-1,-1000,-766,911,-294,-85,1000,1000,1000,-101,-371,608,53,-1000,-628,-606,-259,82,1000,974,-927,1000,-1000,-600,600,-1000,1000,-109,-801,1000,-1000,486,329,-1000,-1000,-1000,889,1000,1000,-595,-156,1000,-1000,1000,-1000,-1000,1000,-1000,981,-1000,-1000,-1000,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(short):void",
            new int[]{1000,449,849,-294,432,-749,-204,-745,-351,-1000,260,1000,-1000,-236,573,-1000,149,1000,-1000,-471,-400,368,430,-374,-400,-570,-389,342,-417,1000,1000,1000,720,474,-989,323,-369,1000,1000,-1000,-479,289,495,-634,400,-1000,-55,644,140,420,-1000,542,879,-778,120,-1000,-308,-97,-943,248,-128,997,-842,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObject(java.lang.Object):void",
            new int[]{696,517,493,-157,-200,-1000,144,994,1000,1000,1000,489,1000,-797,-451,75,209,-1000,1000,301,-999,-576,-1000,1000,-51,-82,1000,-536,-1000,73,-38,-1000,-1000,-559,324,579,-480,-1000,438,-1000,1000,572,-1000,1000,-805,-243,958,-548,1000,593,1000,1000,-810,-1000,452,1000,1000,1000,-1000,447,-1000,-181,-128,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObject(java.lang.Object):void",
            new int[]{-165,541,223,65,451,-1000,-372,414,318,621,1000,70,-292,70,-47,225,133,-1000,548,1000,-604,45,-288,1000,-440,288,1000,-536,-333,-610,531,1000,-754,-620,467,579,-1000,-452,-661,-101,1000,838,-1000,736,-805,595,15,-368,828,-860,578,1000,-528,84,363,1000,444,508,299,1000,-364,101,98,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObjectId(java.lang.Object):void",
            new int[]{-442,921,-321,-734,-396,-344,-166,10,-456,-671,-129,562,1000,1000,-786,438,-119,-263,1000,955,615,-1000,-1000,-1000,384,-430,694,103,444,-2,-1000,-62,-100,-590,1000,284,1000,189,391,369,776,-752,-876,480,204,-370,362,-399,-599,230,396,-286,1000,1000,160,504,-272,-1000,-400,712,385,-517,65,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObjectId(java.lang.Object):void",
            new int[]{303,-51,-18,955,-637,518,57,49,-507,1,673,-1000,-490,631,124,79,1000,579,-482,-345,-104,-124,217,175,87,276,-779,-253,403,-730,248,705,219,625,-1000,158,530,201,-905,-1000,104,468,-460,-672,4,-767,1000,1000,697,371,-64,-507,-121,406,-288,-340,89,91,-11,-385,-1000,-631,635,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char):void",
            new int[]{-72,649,874,-461,28,-51,293,-339,554,-311,79,-203,-79,774,411,-21,929,124,-937,47,-907,-71,-958,312,-999,188,618,44,-28,-900,-141,-842,140,770,-714,-764,-203,924,655,194,348,831,-998,283,539,-516,-901,-101,759,237,236,-294,-828,552,857,957,176,296,994,579,281,-888,-323,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char):void",
            new int[]{1000,961,759,1000,-1000,251,293,-217,-271,-311,-1000,-99,549,-433,130,-53,570,-647,-373,-149,-907,-71,-17,-566,-133,721,-942,743,-12,-884,-618,-171,1000,67,-640,-992,-734,1000,547,35,-626,-555,-28,833,910,505,57,-101,461,966,486,483,-1000,839,-790,541,327,1000,889,959,414,-306,-186,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char[],int,int):void",
            new int[]{225,-43,169,1000,691,1000,1000,-294,510,1000,-752,-449,1000,-419,875,810,703,-30,-1000,-880,254,-969,-1000,-583,181,-363,702,-185,144,-412,-1000,931,-563,260,699,1000,840,608,-782,-159,1000,1000,662,90,735,833,605,1000,-396,-1000,-621,220,245,142,-665,1000,526,48,-340,-708,-1000,114,748,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char[],int,int):void",
            new int[]{228,-583,548,-687,451,1000,1000,484,-1000,1000,-1000,-1000,1000,-601,1000,311,6,-259,-215,-1000,1000,-685,429,-1000,1000,995,418,-1000,-738,-227,-40,1000,-1000,63,832,1000,1000,373,266,361,1000,-408,1000,936,465,1000,1000,-989,-1000,-598,-1000,662,-249,-1000,-1000,95,-1000,-1000,-966,-1000,-1000,1000,471,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-109,-27,149,812,-1000,44,185,338,-330,-1000,-1000,351,60,597,-254,-195,606,416,-392,-1000,448,-834,-854,72,-324,-102,-17,-101,-102,810,433,521,-1000,-1000,441,210,-356,647,416,-1000,-959,-311,-321,-616,-1000,50,160,292,1000,-248,-526,898,1000,-637,444,-581,15,-254,-131,61,-1000,513,-132,565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-1000,677,-416,1000,-1000,-749,262,1000,-287,-1000,-520,613,496,945,-1000,1000,291,-1000,-291,-1000,-857,-886,136,258,-171,836,-52,1000,498,-366,694,-222,-368,-625,660,720,-842,97,999,1000,-1000,183,134,155,-661,847,-1000,1000,1000,902,1000,-654,1000,-84,998,690,1000,-433,-219,96,-372,1000,-554,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String):void",
            new int[]{1000,213,279,-381,721,-132,-677,624,1000,705,-670,616,1000,-786,505,-1000,-718,407,-586,1000,1000,-740,-526,-1000,-425,256,-1000,460,914,363,1000,396,382,974,1000,-712,777,1000,1000,-536,589,680,94,-179,1000,-551,557,-448,1000,481,693,-1000,-753,-1000,44,448,-419,-1000,1000,553,216,-284,198,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String,int,int):void",
            new int[]{742,177,14,315,1000,285,-406,-57,-652,997,701,18,-328,1000,385,-723,893,79,1000,1000,-1000,-62,-314,-435,730,578,-253,-497,-655,729,1,1000,-158,-856,1000,1000,-328,-1000,-376,-1000,-893,1000,-452,-207,-678,-303,-1000,-467,-405,-265,-539,-758,-43,508,1000,250,-134,-33,-147,-592,-516,-752,-300,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String,int,int):void",
            new int[]{-1000,-139,159,-127,515,14,418,244,-293,-491,-594,-1000,-598,588,222,-595,179,-755,1000,-937,591,-658,163,-388,-549,1000,-670,356,55,-716,-1000,-835,-117,537,547,298,345,832,549,644,752,-1000,-950,-871,493,714,193,381,949,298,539,-1000,-438,-309,35,268,625,814,-185,799,94,580,1000,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawUTF8String(byte[],int,int):void",
            new int[]{-1000,-581,187,718,171,-1000,-762,-390,285,153,-438,1000,437,-1000,-1000,-659,-590,-772,1000,163,-1000,549,434,-1000,1000,186,623,-420,947,-435,-1000,-1000,1000,-214,-301,-575,1000,-374,-1000,-1000,-868,-100,-698,404,-916,949,-627,1000,-666,519,-916,-1000,-728,175,791,1000,-653,-656,-173,1000,862,-47,1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawUTF8String(byte[],int,int):void",
            new int[]{-380,-771,-156,-251,140,-652,198,-519,811,460,-80,1000,-573,-1000,-1000,-431,-99,-1000,411,607,-1000,184,421,-881,477,94,-702,-718,661,965,-323,193,634,-189,465,-773,6,82,-960,-1000,-1000,-379,-259,594,-687,996,488,700,-532,283,-521,-1000,-728,352,323,765,211,-650,-399,1000,1000,525,824,-307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(char[],int,int):void",
            new int[]{1000,-187,366,-310,-360,-661,-175,-900,-189,-429,437,275,-701,-1000,-186,-547,-84,134,-392,182,-1000,953,-91,-532,-691,1000,78,320,-346,388,-1000,125,-72,203,421,13,316,-1000,-490,756,460,-25,-628,-150,-744,274,520,183,-678,1000,67,-925,-26,623,260,718,-1000,1000,-1000,-120,-903,-351,-355,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(char[],int,int):void",
            new int[]{1000,865,34,405,-450,217,-893,970,-110,941,-1000,54,-1000,-500,-30,1000,-1000,-309,257,-387,-582,-1000,-55,-489,-687,-824,630,-665,556,723,204,-1000,-1000,383,0,524,1000,1000,1000,725,1000,1000,-228,1000,-926,1000,793,-84,1000,-761,697,-431,1000,285,-1000,677,-360,-247,-410,871,-76,-1000,-1000,-215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String):void",
            new int[]{106,173,234,-283,954,449,580,952,739,1000,723,-1000,1000,562,745,-1000,-31,891,448,281,1000,-858,1000,727,-1000,32,1000,468,-776,-1000,23,1000,964,306,-304,540,-654,225,-1000,1000,52,1000,1000,-290,809,-117,-216,520,-274,1000,-328,-181,1000,366,150,1000,274,-1000,-1000,-65,480,-21,-559,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String):void",
            new int[]{607,193,18,173,-973,-1000,977,201,345,-327,-265,-117,-198,-155,-470,-1000,-137,-1000,-1000,-1000,-1000,-185,49,1000,1000,-569,-448,39,-222,1000,-1000,-579,1000,303,-126,1000,536,-631,1000,-400,-572,-936,-1000,-359,548,592,160,-1000,-208,-819,263,-1000,-537,989,-98,-1000,1000,-1000,1000,531,-741,-263,-955,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String,int,int):void",
            new int[]{240,-699,-211,907,750,977,-99,839,-998,-1000,787,252,-1000,30,289,-70,996,547,144,-168,-331,-1000,576,-583,-316,-81,-1000,896,1000,-915,1000,-72,-564,-530,-593,89,-399,546,228,1000,-316,-227,1000,152,-779,1000,74,-573,-609,55,-492,1000,454,664,-796,192,195,777,-244,142,-82,-278,997,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String,int,int):void",
            new int[]{-395,313,604,-581,-303,-354,713,211,106,154,-1000,-98,-935,579,-234,396,-1000,1000,-486,-807,160,-521,-635,-1000,-841,-100,1000,362,1000,1000,1000,-1000,488,1000,-763,219,-35,608,1000,989,-438,-1000,-461,567,-150,-309,1000,-333,790,-297,-482,-465,1000,1000,-100,-403,1000,-129,536,-1000,650,-556,712,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String,int,int):void",
            new int[]{-270,-383,-1000,78,739,490,-1000,378,1000,768,-354,-964,1000,360,865,-89,963,-1000,-1000,1000,-758,208,-1000,414,-484,18,50,994,60,390,641,302,680,1000,1000,728,-295,-1000,255,-1000,1000,1000,-1000,-1000,1000,805,-761,298,220,-1000,1000,-1000,-90,1000,1000,-721,-875,-316,-934,1000,-311,545,473,-467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartArray():void",
            new int[]{244,-675,403,-550,107,619,-90,-757,-118,-520,314,-507,-291,577,881,931,434,874,180,-858,443,-937,208,-23,-276,936,799,-43,-508,-73,292,-639,-34,-958,-167,65,-90,678,-115,937,-592,505,518,596,22,451,-119,688,-61,131,-101,503,28,-423,671,-320,-206,-979,-690,55,194,-346,-947,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartArray():void",
            new int[]{932,417,34,-853,445,1000,-914,-596,60,-1000,105,-748,-798,928,929,-617,811,598,1000,-848,98,-595,512,50,-61,1000,-420,-34,-1000,77,-335,-1000,160,-1000,-109,376,440,-221,-97,637,-1000,1000,678,1000,-195,1000,-669,820,1000,1000,52,507,-320,-488,-1000,-817,-957,-570,365,-154,-199,115,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartObject():void",
            new int[]{143,-99,884,-104,-517,721,870,-374,543,1000,-321,777,160,330,162,-568,24,-456,-1000,-1000,-661,1000,-43,-782,-447,879,662,-1000,544,-909,-152,-1000,448,1000,-790,827,-123,1000,-421,-1000,-873,-823,473,-403,693,1000,-90,171,-817,-253,-378,-897,340,-1000,-929,675,58,-1000,-853,734,547,1000,990,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartObject():void",
            new int[]{225,425,-367,131,28,899,-847,-251,-566,-949,118,-970,665,-838,-539,-337,-669,273,420,-47,-781,446,-58,-40,-176,802,-412,537,528,230,791,261,-726,61,697,267,926,151,532,-122,-896,-104,479,-431,-765,-550,-632,286,-223,117,147,-562,-839,292,-944,-13,-161,598,-296,-54,19,956,844,-620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(char[],int,int):void",
            new int[]{1000,383,-284,48,-339,466,-44,-279,-253,-189,255,1000,735,65,1000,695,-803,756,-448,-83,181,207,-192,-663,1000,535,-237,-350,414,215,-1000,-403,-598,-92,-668,589,-20,-877,827,-726,103,-721,-12,-188,293,-237,-824,1000,237,-489,365,798,-310,-480,585,1000,-168,54,3,-741,147,43,-53,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(char[],int,int):void",
            new int[]{431,625,-631,-185,-153,-127,718,347,101,580,-667,472,-296,-719,234,-18,-629,140,310,-634,250,1000,200,170,-1000,-137,-152,803,-503,-1000,357,-220,-87,649,377,-697,-682,-738,-60,101,403,141,-423,-76,-5,-22,-809,871,694,-103,256,1000,-69,103,-400,152,315,-1000,863,239,-169,58,-899,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-78,-311,-153,169,-910,-887,-544,824,370,-859,-799,517,402,-236,-630,-967,627,282,129,1000,936,-548,1000,871,-204,-668,378,270,344,-312,-842,92,91,950,234,141,1000,581,293,320,-552,81,-87,501,-274,986,-1000,-35,-598,-56,175,-272,114,-516,-331,628,-1000,-348,-318,-1000,-451,276,19,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-579,-927,452,531,-1000,1000,-694,-221,1000,-1000,453,293,-1000,457,-864,352,630,-51,-636,-1000,-302,-302,-681,318,-830,-755,-398,267,179,354,-818,683,-827,883,773,-722,591,-318,-329,1000,120,-369,-672,152,974,68,-1000,1000,-75,188,415,-1000,-847,-532,-1000,213,-86,749,-1000,-311,-440,-239,-608,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(java.lang.String):void",
            new int[]{789,-967,504,-830,-331,-789,-935,1000,824,910,180,1000,1000,-603,-1000,-1000,1000,427,-386,1000,757,136,520,-1000,627,-1000,-302,-502,890,1000,-1000,-840,-328,-682,-330,1000,-1000,-1000,-957,288,-698,-1000,-558,1000,-77,-190,-1000,317,538,233,603,-1000,785,-432,-536,-50,-1000,-960,1000,74,-771,-775,-232,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(java.lang.String):void",
            new int[]{1000,-595,-718,108,4,1000,483,1000,-849,-144,-1000,6,770,807,-1000,-1000,-678,-468,-1000,170,1000,902,-1000,-1000,1000,-161,-1000,901,658,494,-1000,-793,-1000,1000,-454,641,-1000,-992,-283,686,-1000,-592,-919,737,-573,-1000,199,-1000,1000,37,556,-256,504,23,282,984,-1000,645,168,-1000,-657,1000,-1000,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTree(com.fasterxml.jackson.core.TreeNode):void",
            new int[]{-202,889,-136,304,-455,-430,868,1000,243,-754,26,-764,-1000,-71,-1000,563,-1000,354,475,320,631,311,1000,-74,-228,946,-1000,220,-172,-563,-370,-570,-1000,942,559,1000,806,1000,-1000,511,-866,51,1000,-480,-732,-1000,-1000,-1000,-534,809,1000,697,-495,1000,-541,630,-141,-1000,-1000,-217,-766,-833,-614,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTypeId(java.lang.Object):void",
            new int[]{1000,-131,762,969,298,-151,-654,1000,-866,528,1000,-1000,20,-672,-450,-604,307,-1000,-798,956,1000,437,-670,-551,364,166,788,-875,-1000,-845,249,-577,61,-368,-718,971,282,1000,503,785,-287,1000,-996,-1000,10,-465,653,1000,470,439,400,-1000,858,-701,99,710,-572,15,-532,-1000,1000,-1000,1000,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTypeId(java.lang.Object):void",
            new int[]{-742,97,929,833,932,951,867,420,658,471,-53,373,660,862,-124,337,358,282,956,41,460,420,726,-157,-141,583,926,541,-152,-986,-421,893,-44,-118,415,909,-744,516,106,30,402,-176,779,-419,834,-426,-817,516,608,-779,500,394,-885,324,311,-202,-374,212,482,-77,991,570,-125,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeUTF8String(byte[],int,int):void",
            new int[]{455,-683,-141,-538,894,-774,380,231,-687,-938,-503,-340,-978,585,986,-250,102,888,557,-667,520,-983,-523,60,747,772,-564,661,88,295,-406,297,-361,-184,-779,492,-582,11,511,-840,-438,839,245,-482,-320,427,-218,-691,491,-388,575,-307,-332,58,833,401,-544,554,286,306,-916,-964,96,434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeUTF8String(byte[],int,int):void",
            new int[]{-7,-299,408,823,827,1000,-953,961,340,387,-1000,199,906,-985,-70,-774,6,41,-1000,-181,281,-231,-378,1000,-1000,-496,-1000,1000,1000,998,-343,-552,-289,881,-222,393,-1000,-1000,662,-241,302,968,339,1000,-209,-1000,-861,337,743,146,1000,-517,806,414,-110,-1000,-1000,-980,976,292,-276,-786,-594,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.TokenBufferDeserializer", "com.fasterxml.jackson.databind.deser.std.TokenBufferDeserializer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
