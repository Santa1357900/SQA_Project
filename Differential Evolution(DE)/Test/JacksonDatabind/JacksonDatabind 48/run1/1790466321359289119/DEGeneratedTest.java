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
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "findTypeDeserializer(com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeDeserializer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getAnnotationIntrospector():com.fasterxml.jackson.databind.AnnotationIntrospector",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.annotation.JsonFormat$Value", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getDefaultPropertyFormat(java.lang.Class):com.fasterxml.jackson.annotation.JsonFormat$Value",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.annotation.JsonInclude$Value", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getDefaultPropertyInclusion():com.fasterxml.jackson.annotation.JsonInclude$Value",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.annotation.JsonInclude$Value", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getDefaultPropertyInclusion(java.lang.Class):com.fasterxml.jackson.annotation.JsonInclude$Value",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getDefaultVisibilityChecker():com.fasterxml.jackson.databind.introspect.VisibilityChecker",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTUyMTQ4ODA=", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getDeserializationFeatures():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.JsonNodeFactory", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getNodeFactory():com.fasterxml.jackson.databind.node.JsonNodeFactory",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "getProblemHandlers():com.fasterxml.jackson.databind.util.LinkedNode",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "hasDeserializationFeatures(int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "hasDeserializationFeatures(int):boolean",
            new int[]{-971,774,-947,634,738,753,-83,159,739,911,495,209,726,-685,-261,-276,-329,898,825,-950,909,538,-859,830,-729,-730,983,-269,952,-939,-20,-79,857,593,-586,561,880,618,-746,951,-266,646,-986,443,783,916,-668,-269,637,964,556,-278,197,385,-433,-168,-688,890,547,-741,221,-818,-497,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "hasSomeOfFeatures(int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "hasSomeOfFeatures(int):boolean",
            new int[]{-908,318,884,203,-715,443,872,284,-185,-90,45,-896,390,-296,-885,-84,320,521,-633,178,976,423,-804,-600,-791,-647,287,-175,312,509,636,579,-177,980,-412,781,-716,-153,186,480,-92,-551,254,288,65,471,-184,-899,-558,123,785,383,821,752,-896,-834,-495,-35,-19,130,595,571,-713,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "initialize(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "introspect(com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.BeanDescription",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "introspectClassAnnotations(com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.BeanDescription",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "introspectDirectClassAnnotations(com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.BeanDescription",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "introspectForBuilder(com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.BeanDescription",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "introspectForCreation(com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.BeanDescription",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "isEnabled(com.fasterxml.jackson.core.JsonParser$Feature,com.fasterxml.jackson.core.JsonFactory):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "isEnabled(com.fasterxml.jackson.databind.DeserializationFeature):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "isEnabled(com.fasterxml.jackson.databind.DeserializationFeature):boolean",
            new int[]{-238,347,-2,-704,-945,155,-240,83,933,981,-890,-300,521,109,-626,789,68,-38,-511,-222,-370,698,359,162,-648,-403,-370,-254,340,104,624,533,812,-252,-565,84,-548,654,965,413,732,855,4,811,-683,-818,-606,307,633,-798,-234,-60,152,311,391,473,-961,916,255,-246,419,-571,665,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "useRootWrapping():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.core.FormatFeature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.core.JsonParser$Feature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-337,-576,859,225,-965,776,146,657,-928,196,-453,-554,-79,682,-428,235,-700,919,-384,-291,-325,476,151,-690,-938,-91,319,391,-759,23,-326,-553,-282,-13,-169,148,-348,-725,-966,-186,-857,-543,234,-272,-250,767,-819,-824,-38,-949,-747,505,406,-753,253,617,55,-193,-751,-666,418,681,697,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{56,1,891,-231,-992,-877,148,-25,-207,-94,-470,-66,961,-426,650,-31,642,-502,-365,-187,539,-926,-39,-554,-194,908,156,-35,900,-835,925,944,496,327,656,-228,871,811,972,322,757,899,-980,957,-367,-756,526,-626,-380,888,-694,64,755,125,867,-728,240,-158,15,268,479,-980,999,448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{95,732,406,1000,249,-395,-1000,-1000,104,848,540,1000,109,1000,590,795,-733,-822,938,1000,857,-325,-436,-184,561,-359,597,-329,882,413,417,145,759,604,-1000,92,70,-224,-418,-931,1000,-482,-1000,-1000,-966,585,-880,-224,474,-585,-41,280,243,-155,-733,9,356,1000,922,65,926,320,265,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-406,-230,-86,-181,200,37,940,50,282,-715,-11,288,238,801,612,766,957,245,-302,530,-412,426,548,-183,949,455,-143,-245,172,775,353,223,-851,59,421,55,107,709,477,473,200,792,-934,621,-608,-342,-277,-633,121,180,459,187,-791,592,-565,-154,344,-763,828,858,-938,191,220,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-43,175,-360,-139,-414,-183,547,276,428,244,795,-81,-578,622,147,-190,498,-909,423,-335,102,280,842,-532,348,-990,578,349,769,773,-894,188,-452,-927,-465,-261,919,-537,-496,736,955,193,495,456,-619,-174,-587,-496,217,833,164,-787,-904,-735,-385,-14,-113,-115,-189,136,-73,-179,597,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-354,-7,242,79,853,-907,-240,462,-190,-49,971,341,-177,-905,960,799,-757,505,-195,-860,-264,209,-446,-625,222,-805,620,-16,412,202,352,80,556,-277,-508,387,-273,-135,-758,-699,-216,-428,759,684,7,-817,27,-948,339,272,265,-692,-873,308,-711,294,326,765,590,99,-763,46,278,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{140,208,523,-919,-445,154,466,278,84,214,499,631,767,-704,-182,545,-518,358,-424,947,-596,-735,952,477,-337,-881,240,293,-477,-724,-388,-195,-895,-955,182,889,387,357,0,999,22,-415,-309,-987,397,130,609,-411,-769,378,556,377,-942,221,400,-510,-64,-555,-739,345,-136,975,311,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.cfg.ContextAttributes):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.introspect.ClassIntrospector):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.introspect.VisibilityChecker):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.jsontype.SubtypeResolver):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.node.JsonNodeFactory):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(com.fasterxml.jackson.databind.type.TypeFactory):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(java.text.DateFormat):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(java.util.Locale):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "with(java.util.TimeZone):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withAppendedAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withFeatures(com.fasterxml.jackson.core.FormatFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withFeatures(com.fasterxml.jackson.core.FormatFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{239,-387,27,-188,920,-377,469,583,-565,144,-616,321,-177,542,-289,54,-961,945,-492,-858,-942,378,-287,814,-823,511,649,572,-865,297,-843,80,219,-324,100,-291,-732,-800,187,259,-335,973,630,526,-205,580,-55,413,-794,-140,-618,996,-211,294,918,-355,-389,537,-457,-361,773,-765,-376,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withFeatures(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{489,-144,342,-566,-742,749,343,33,553,996,176,558,220,-446,872,976,-653,108,-171,495,528,-658,134,-70,-242,483,-497,-710,-429,-478,901,-804,829,-23,-257,626,-945,-84,-710,-296,595,40,387,774,29,71,-43,-176,-738,433,874,-483,481,-723,-294,479,-195,-741,650,-653,-162,170,-502,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withFeatures(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withFeatures(com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-166,-431,227,959,849,-937,255,511,94,398,356,716,178,753,771,-865,778,-575,355,812,-75,929,297,458,128,-58,984,990,-843,334,283,484,-536,-960,483,141,517,639,-792,38,656,159,705,-929,-553,-154,98,794,-7,421,427,-352,-848,698,599,462,226,-674,-278,31,-714,925,-258,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withFeatures(com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{1000,-1000,-1000,1000,-631,350,-1000,-1000,-84,-1000,1000,-281,1000,-1000,644,-936,382,1000,-1000,563,234,-977,-436,-388,-771,1000,-631,1000,91,219,0,-1000,982,-1000,-684,-331,-1000,-652,179,-486,1000,-170,-698,1000,-576,-944,14,1,0,-156,-1000,820,-165,601,-75,284,64,-954,209,1000,-651,27,-555,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withInsertedAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withNoProblemHandlers():com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withRootName(com.fasterxml.jackson.databind.PropertyName):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withView(java.lang.Class):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withVisibility(com.fasterxml.jackson.annotation.PropertyAccessor,com.fasterxml.jackson.annotation.JsonAutoDetect$Visibility):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.core.FormatFeature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.core.JsonParser$Feature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-308,-24,-784,-216,577,-67,369,-701,2,-860,444,736,-720,-183,-323,-626,-970,161,877,-307,884,-665,-318,-69,-821,447,371,-921,-34,-993,437,45,995,-430,-156,596,-60,246,-108,337,-956,-372,-1,-533,657,-717,307,-654,-364,-582,10,14,-509,-409,-406,-291,-682,409,-291,168,73,29,367,-971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-788,525,-196,-518,-111,752,-317,674,-504,469,-212,661,-367,736,604,-565,635,-561,858,216,-422,-4,-860,-903,-586,397,707,881,-878,-133,515,442,-631,398,905,854,793,799,790,-950,750,12,671,822,-888,-13,351,-373,544,610,-142,-181,-413,-154,721,706,-188,-389,512,-866,-661,-342,346,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{275,-665,242,-759,0,1000,151,0,0,-212,-953,523,-794,-1000,-744,680,0,908,1000,-941,-161,-259,0,236,-477,-628,0,-1000,-279,167,0,66,-585,1000,-110,196,-510,-53,0,-950,318,0,0,0,0,-404,-705,-386,-637,-953,85,16,-1000,-84,243,310,-369,303,-8,-25,-48,743,539,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{488,-239,-996,-519,-20,965,-308,99,-548,-577,-306,-637,237,668,-493,577,-209,816,-750,-506,-17,469,-892,-70,-377,19,411,-447,951,-478,-853,-657,559,262,-16,974,-441,-861,-304,165,-329,-914,341,889,-674,899,464,-709,927,13,-822,-893,922,82,-991,464,519,-499,-868,-978,524,-552,761,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "without(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-263,-787,-186,-450,672,272,964,-678,942,-951,546,-92,-451,676,-987,-217,-588,-949,17,451,-812,728,-941,-71,989,294,-360,807,-651,338,857,831,618,9,-332,-360,-795,-784,899,113,21,588,474,637,883,469,317,-467,650,-598,912,847,435,-148,-401,-200,350,228,-657,250,938,-332,-63,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withoutFeatures(com.fasterxml.jackson.core.FormatFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withoutFeatures(com.fasterxml.jackson.core.FormatFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{605,372,-422,557,-269,-973,-605,-110,814,-708,409,-532,869,-228,13,162,897,317,799,470,-478,614,602,-956,552,329,-998,469,558,655,303,182,-767,-427,-537,-100,-34,167,945,11,881,929,973,5,-639,-838,-677,194,28,-717,-578,-253,-937,-854,-691,-637,785,-686,745,-207,-503,648,-312,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withoutFeatures(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-277,-283,359,112,-925,357,52,215,854,572,-165,362,788,591,92,-640,735,810,-590,-978,-558,-124,-276,588,530,-163,472,-310,-342,413,-918,-677,399,224,-371,-821,280,-683,-717,403,621,693,-697,-674,359,725,-249,68,875,-701,873,30,-19,334,-905,238,698,-80,26,271,238,737,468,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withoutFeatures(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withoutFeatures(com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{-472,397,-259,-648,-180,-227,-1000,621,188,-173,957,90,-568,-526,-802,-739,-661,934,-349,-226,-276,-850,545,-880,381,-221,-324,607,-694,-509,-301,609,808,115,-303,512,405,-481,858,-334,-244,-911,-296,-891,345,154,82,-106,-941,-230,824,408,405,-101,-141,263,693,-773,-548,40,-811,-408,-834,450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.DeserializationConfig", DEReplay.run(
            "com.fasterxml.jackson.databind.DeserializationConfig", "com.fasterxml.jackson.databind.DeserializationConfig", "withoutFeatures(com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.DeserializationConfig",
            new int[]{444,690,162,-892,-882,-928,370,-824,813,-797,-15,-913,817,-823,728,-760,26,-121,376,-733,728,-179,-724,987,501,699,-88,-848,-561,990,-394,145,-847,-297,932,114,297,798,-990,-58,717,708,-496,940,760,910,-166,151,-232,552,874,-222,306,776,-723,-606,319,554,-588,308,58,-377,876,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-305,-906,129,552,-660,-1000,1000,-128,-78,-539,509,1000,135,1000,-172,-1000,951,28,-184,-1000,274,-1000,-110,450,1000,1000,184,636,-438,1000,-1000,1000,669,1000,-376,-762,1000,758,1000,-1000,-1000,-107,1000,1000,1000,396,-106,1000,-347,672,108,488,-1000,-1000,-106,50,-829,109,-317,-460,6,634,-167,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-723,-228,34,-146,-125,-736,918,-558,449,-617,1000,5,-314,1000,325,-1000,257,1000,-944,-67,-301,-1000,-683,-593,1000,704,1000,1000,503,297,-827,1000,872,1000,-852,-1000,863,266,1000,-1000,-735,257,1000,1000,1000,951,-668,1000,-780,325,-190,677,-1000,-838,876,1000,-726,805,-415,-982,1000,850,-371,416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-410,-731,-563,389,1000,-747,1000,-92,-462,-14,298,1000,627,1000,-1000,-370,-790,645,1000,-874,-89,-793,1000,325,-1,-1000,-1000,444,1000,-162,29,-96,-558,-127,1000,-582,1000,554,-197,1000,-1000,1000,-897,-332,1000,807,-1000,-444,-842,831,-510,1000,1000,-862,-1000,-1000,1000,-581,327,-999,-420,218,-1000,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-152,-703,279,1000,5,907,495,967,-467,1000,563,478,489,-1000,671,-1000,1000,891,994,-1000,55,345,-673,-243,1000,637,41,1000,-922,376,-1000,-1000,-1000,-1000,-1000,827,0,-238,1000,328,11,-957,-915,-245,1000,-1000,-164,124,213,256,1000,-1000,-267,-1000,1000,1000,-1000,-1000,-1000,976,-561,-1000,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{892,-1000,-2,244,759,-773,1000,-930,246,-617,1000,1000,-314,266,-515,-374,1000,1000,134,180,-301,-100,-659,-230,1000,-147,429,658,-37,-768,-827,-27,877,-188,-563,-1000,863,854,1000,-1000,-735,282,981,617,137,1000,90,1000,387,1000,805,1000,-288,-838,-1000,335,212,-509,-946,-782,709,256,-1000,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{261,375,-142,650,-719,-688,1000,-1000,449,-575,1000,890,444,583,-161,-562,586,1000,-549,-243,-822,-370,-1000,-1000,1000,-99,1000,1000,901,-940,-903,414,1000,169,-852,-1000,1000,320,1000,-1000,-340,-198,1000,903,396,1000,-609,1000,-364,1000,531,970,-502,-1000,-20,1000,-30,185,-839,-1000,1000,705,-13,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{382,516,-6,-1,1000,796,1000,-479,887,1000,1000,99,-19,16,235,1000,-720,1000,1000,597,11,607,487,409,-82,-1000,-126,-324,1000,-1000,558,-879,-615,-1000,108,-922,-342,512,-592,1000,1000,881,-1000,-1000,-937,1000,-959,-823,393,1000,175,1000,1000,1000,580,357,1000,-451,-649,-257,1000,-268,-1000,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-16,109,-337,-80,-770,-988,173,612,-754,-726,-648,-1000,292,475,-1000,-1000,-493,-944,864,-301,-1000,159,-106,496,-166,1000,1000,345,948,-182,633,-563,1000,-472,-515,-366,322,826,-759,-621,-163,1000,1000,1000,-66,156,227,-545,-1000,-1000,-529,-1000,-219,1000,1000,-205,1000,-202,-988,1000,-166,708,132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{244,144,454,1000,631,360,1000,-1000,642,611,1000,430,373,489,157,-59,453,1000,-184,-254,578,-622,-453,-233,904,121,364,161,962,-1000,-309,500,836,421,-447,-1000,880,1000,363,73,400,987,660,-398,-113,532,-1000,1000,-124,1000,633,1000,400,15,583,959,118,-211,-728,-470,1000,778,-1000,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-439,658,-104,572,713,186,1000,172,-1000,741,144,397,-84,924,-1000,381,-831,380,1000,-992,344,-307,642,5,-1000,-761,-1000,-259,1000,-1000,-339,487,222,-92,810,-211,423,-136,-1000,1000,1000,1000,-1000,-5,-487,1000,-1000,-948,-922,791,-301,1000,819,1000,-571,-100,242,-615,1000,402,-975,680,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{935,742,1000,-841,382,552,1000,-167,-30,-1000,1000,125,911,-1000,230,578,935,1000,503,-1000,1000,1000,928,260,467,-31,784,525,1000,744,-116,-1000,-1000,-1000,-100,957,333,536,776,-569,-414,378,855,856,1000,403,165,-778,1000,1000,1000,104,563,-255,-178,-960,922,-422,975,-1000,-532,-932,244,381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{359,248,1000,929,288,671,-1000,1000,-791,1000,-1000,1000,787,-1000,-611,1000,384,579,893,1000,1000,-1000,1000,-349,152,-31,-646,568,1000,73,322,705,-1000,1000,230,1000,-72,1000,1000,756,1000,-1000,1000,545,0,-304,23,1000,-1000,1000,120,503,-1000,-946,444,-1000,-1000,-422,961,123,-1000,-932,-333,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-713,757,1000,309,-57,486,408,-1000,-114,-244,978,-125,518,-94,1000,1000,384,579,287,-452,1000,1000,101,-186,-699,-752,-416,-92,797,830,739,-492,-905,796,1000,1000,-350,912,-35,-671,395,1000,1000,399,877,84,1000,-1000,279,-599,464,847,-8,-114,-132,-1000,420,685,316,-987,264,-660,-369,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-81,1000,937,258,403,-97,680,-789,555,219,1000,364,1000,1000,704,59,1000,833,818,-1000,-218,441,-776,-1000,690,315,-563,-243,235,892,-997,-506,180,158,309,237,1000,822,217,-246,-753,992,740,1000,896,-75,702,-395,292,-91,290,920,999,-919,11,-154,172,-757,1000,-870,-428,268,1000,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-146,603,304,-262,-322,-1000,787,385,1000,-666,1000,186,-551,271,900,205,-161,196,634,-539,-1000,512,448,-54,-343,-502,-690,311,60,-98,423,13,588,1000,441,-616,892,810,-276,844,-340,-898,-506,76,716,601,718,-1000,-308,1000,492,-609,90,-963,-392,-1000,135,361,621,-59,1000,112,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-371,-1000,-711,-512,431,-330,781,104,425,-10,282,-96,242,-260,324,344,420,-33,791,-1000,-539,896,553,738,1000,191,-405,-342,161,-145,-579,-924,180,233,-119,-483,-534,820,379,-583,-745,510,3,-268,219,304,-364,-468,92,488,999,981,1000,639,-671,-364,-851,998,278,-365,-11,816,-124,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{423,979,1000,259,105,1000,1000,-40,-877,-243,624,1000,759,-998,-503,1000,891,1000,755,-1000,1000,648,1000,76,197,944,1000,552,177,1000,983,-810,-1000,-491,848,1000,484,1000,1000,-872,893,-160,1000,890,1000,1000,1000,-122,1000,-1000,564,355,-329,-1000,-363,-1000,535,-277,1000,-1000,-52,696,358,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{828,919,92,444,-564,926,-390,1000,-364,921,-986,-275,-786,-63,860,1000,-548,-164,-30,-670,344,1000,521,575,-250,-586,470,764,1000,-323,-651,-1000,-324,1000,925,-570,532,1000,870,-987,-19,966,818,-846,699,-665,857,-1000,-251,-83,908,930,-738,457,1000,-1000,-453,89,1000,235,-952,1000,872,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{901,536,809,959,462,-146,1000,-851,133,587,475,935,-1000,811,-371,113,628,473,-785,-731,-1000,-282,407,477,-247,-675,508,-862,377,-871,-1000,723,1000,965,-1000,1000,398,574,1000,935,-751,-997,-733,-1000,-258,-220,-1000,-114,-579,67,-234,-40,-61,69,144,317,437,-842,-609,751,1000,-906,74,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,-514,-231,-392,779,790,1000,-630,-56,-670,-826,-949,436,-1000,856,852,1000,-105,1000,142,-1000,-66,-1000,325,511,926,-61,1000,-35,-868,867,685,-1000,-851,-33,-1000,-853,870,1000,-936,-517,303,169,540,-393,-1000,284,-1000,318,564,-1000,-1000,-978,836,975,379,846,-36,-1000,1000,-764,-1000,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-634,-415,-393,-146,341,548,-1000,278,113,617,-838,-133,-788,738,-567,1000,7,1000,-69,618,-882,-253,-260,-192,114,-1000,724,-808,1000,-392,-392,1000,1000,125,-458,173,-349,883,449,-1000,-63,-82,-610,-874,-363,771,-836,370,-1000,-200,-555,-609,-627,65,526,966,660,493,-709,1000,359,-123,54,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-859,-337,-1000,-426,-564,690,-626,1000,-153,453,-1000,-574,-984,-420,257,409,-454,860,-30,1000,404,-181,-637,-139,30,722,1000,377,1000,-86,-89,319,872,-950,101,-135,-112,69,870,-81,-472,-224,814,1000,699,-701,-92,38,-1000,720,908,-453,-738,-884,-315,-702,917,1000,1000,-515,-255,122,-1000,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{828,841,-185,664,808,854,-817,1000,-677,1000,-972,-275,-581,-329,1000,1000,-823,429,691,-354,787,1000,1000,204,-725,-434,-38,893,-1000,-153,-316,-1000,-597,1000,1000,-872,99,1000,-1000,-1000,268,1000,1000,-698,-229,-426,857,-1000,371,351,-1000,1000,-201,879,1000,-1000,-1000,-498,876,-387,-1000,1000,892,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,416,-247,660,-53,-836,-558,312,-510,-705,-177,-424,-116,188,-340,654,-269,-164,-85,-993,477,-228,1000,-146,1000,801,191,548,229,-745,1000,956,339,-1000,949,1000,192,1000,-1000,-5,-1000,370,787,608,-760,385,339,-1000,909,-243,342,686,691,-1000,-1000,1000,-317,448,1000,984,287,-1000,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,305,205,-27,1000,1000,-750,-619,1000,507,224,-310,1000,1000,514,-1000,-626,1000,-780,-831,1000,-1000,134,-1000,1000,-1000,1000,658,558,-1000,-531,1000,-1000,1000,-1000,1000,1000,1000,1000,-1000,-1000,-1000,1000,1000,911,-1000,820,-1000,575,-1000,1000,-881,-1000,626,713,565,-1000,1000,839,-1000,718,1000,6,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,234,-296,-106,-197,-170,761,332,-827,-1000,26,-144,-1000,-1000,-993,478,-394,-737,-264,-43,-427,269,680,919,919,393,-1000,-1000,-785,620,544,-234,1000,-1000,-25,256,-860,-1000,-829,1000,930,1000,-665,399,165,956,79,808,-517,599,-973,-409,-387,-1000,-1000,-22,1000,-954,-103,-331,-713,235,138,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-396,714,487,-1000,289,-407,372,26,192,454,-1000,814,506,428,-526,-294,530,-383,-284,-51,-1000,1000,114,-854,1000,-400,-400,-37,-796,-420,-21,400,-388,-550,799,-170,-350,-1000,305,-1000,1000,-284,4,626,-1000,430,-1000,363,-241,-827,-451,-1000,-400,184,816,111,-552,690,-1000,-168,281,-1000,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{611,-674,196,154,1000,236,-716,1000,486,-852,128,-1000,-306,-1000,-1000,-41,-344,471,-776,-1000,-5,2,1000,-434,740,-1000,-1000,-262,197,576,-515,-831,632,-438,24,1000,-1000,-903,887,681,-1000,-531,-942,1000,1000,-111,696,-834,1000,430,93,-672,-1000,-791,-388,555,-899,-911,154,-891,-1000,-377,-1000,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-228,689,-50,-491,456,385,-88,-204,-1000,-740,36,-535,-543,1000,-250,-620,-536,234,-402,624,-855,220,515,12,-969,-477,-1000,-288,810,103,-884,-1000,-523,-510,865,169,89,1000,507,-439,612,-80,994,1000,1000,-400,79,481,-974,-876,-663,504,-1000,678,765,-298,1000,878,518,1000,910,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{76,335,298,384,797,533,-716,59,665,-440,-30,-1000,-346,-58,-1000,-41,-344,-130,-776,-845,-567,-533,1000,0,381,-455,-247,14,197,68,240,159,626,-332,443,1000,-825,183,327,503,-36,325,-137,1000,-600,-277,696,483,711,-333,-141,-933,-1000,-889,-311,433,-311,442,505,-364,-964,411,-1000,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{284,1000,394,-307,-665,180,259,-470,355,408,-851,1000,-397,950,1000,599,-919,-297,-671,589,-60,-42,-904,425,377,1000,1000,273,676,-753,83,1000,-474,180,302,-876,353,859,-390,-409,1000,864,-175,733,423,842,-471,1000,-794,-104,-436,-636,1000,702,-629,826,991,872,888,844,-144,425,1000,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-276,604,1000,289,193,-740,933,184,521,116,-1000,1000,206,-436,-955,9,272,-848,-343,1000,-928,1000,-263,-362,-1000,-67,-133,75,-703,-719,-29,319,76,-1000,1000,-180,226,-1000,198,-1000,1000,-107,78,400,-765,379,-1000,454,-849,-1000,-538,-1000,-53,919,892,-691,-557,752,-1000,-109,511,-1000,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,633,394,1000,-1000,533,138,59,34,-440,-30,-401,593,1000,-1000,-41,-567,76,-549,561,259,-1000,-204,267,-834,1000,291,-290,244,-1000,-154,159,89,-371,-954,-325,892,468,-1000,289,107,1000,243,-572,-487,-359,344,-521,-811,-754,-1000,-731,-329,-889,339,428,481,442,964,30,317,956,-203,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-335,1000,801,-577,-839,713,-310,-1000,818,986,-1000,1000,512,1000,1000,-111,-1000,501,-1000,1000,13,-803,-565,-509,367,945,1000,1000,1000,-748,-435,1000,-1000,1000,-735,-1000,1000,1000,1000,-1000,1000,-46,1000,807,471,904,-153,1000,-1000,-456,198,-1000,1000,1000,559,930,147,1000,1000,1000,887,95,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTyping(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,com.fasterxml.jackson.annotation.JsonTypeInfo$As):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,572,429,719,-1000,-332,861,-363,448,-174,-1000,1000,-1000,19,226,1000,-1000,-400,-369,799,-497,550,-1000,677,508,1000,111,-1000,-368,-51,201,634,941,-400,375,-1000,4,268,-400,471,1000,1000,-222,159,-339,1000,-1000,1000,-1000,613,-1000,-925,1000,199,-917,569,778,64,828,879,-907,-488,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{745,159,259,-440,-977,-730,-52,-32,511,-52,360,1000,-221,-357,-282,-493,263,-87,747,1000,760,462,-216,585,-400,-676,-400,215,1000,688,408,-4,-1000,-122,306,-954,-667,-254,-179,-643,69,58,-425,1000,-226,1000,-221,-553,1000,215,-166,-132,116,-152,23,390,621,-1000,-431,-83,-923,394,-1000,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-51,646,-583,114,-133,-6,-560,-84,134,914,-910,131,-232,58,789,187,-1000,967,-1000,-903,377,753,282,744,-54,32,760,-614,-1000,-109,-935,560,-276,17,306,-756,859,217,-261,449,-413,-269,-750,189,-18,542,42,-243,502,-728,218,387,-948,-40,-622,-1000,-163,-644,-573,825,-859,601,692,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{839,391,317,504,416,-357,104,-22,1000,859,988,89,-177,-746,-1000,-683,806,159,-453,-696,852,383,-1000,1000,-1000,-191,-294,-76,424,581,628,-552,362,-870,-64,-365,-1000,-517,353,-766,-636,705,-313,-612,1000,374,622,361,-466,-76,-171,469,-972,390,67,1000,905,-321,463,233,-649,134,960,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-401,-872,-683,314,957,-189,-439,351,377,191,636,8,939,-287,-867,748,-4,-373,-825,7,-972,319,515,786,-145,180,761,504,866,-514,106,711,960,-261,-590,889,266,-343,-734,-46,-595,-413,738,331,-976,486,-88,-494,676,-166,60,790,697,822,-730,734,699,-988,-258,-918,-476,280,-433,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{33,-914,-772,9,-899,-467,-756,136,-224,-709,-975,480,45,784,185,-458,-502,-466,174,-605,-1000,-313,-780,-625,1000,420,-462,535,662,-20,317,1000,70,1000,883,-50,528,-555,-232,-248,203,-1000,49,161,-176,409,-978,-749,-1000,908,392,204,768,628,-62,772,104,-984,865,-159,-798,-386,93,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{741,66,-582,-909,753,-502,52,1000,83,1000,-1000,1000,-600,-1000,-1000,779,1000,-433,174,1000,230,631,1000,635,1000,-676,597,-1000,478,475,1000,-912,426,89,692,-745,152,-97,1000,-108,1000,1000,-1000,1000,74,-186,699,-867,1000,-1000,1000,-1000,-919,-341,9,-886,851,-842,-1000,-17,-499,708,-37,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{511,-182,-796,674,704,-25,1000,1000,258,894,-801,1000,-792,55,-43,-222,1000,698,-678,1000,-737,165,-1000,26,-168,373,-957,-172,1000,1000,1000,-699,49,1000,1000,406,-1000,-1000,-1000,165,270,57,699,-428,323,-668,-446,-245,1000,-251,-969,-1000,-631,1000,49,703,1000,-1000,685,651,314,68,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "enableDefaultTypingAsProperty(com.fasterxml.jackson.databind.ObjectMapper$DefaultTyping,java.lang.String):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-240,252,-361,102,-59,-486,164,-319,104,-418,-366,-616,-913,1000,1000,-966,-909,-788,-112,-978,-671,1000,-1000,127,-799,254,-1000,1000,625,-184,-221,806,-456,706,1000,-273,-554,-424,-911,-108,-955,-1000,703,-389,-1000,544,-1000,-1000,-562,1000,-610,-789,535,439,-415,55,-186,194,83,-656,-285,-1000,-844,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-132,-898,-1000,-116,118,-89,-392,1000,-1000,1000,740,-1000,-1000,1000,-66,-371,-673,1000,-1000,213,-1000,35,375,1000,1000,-1000,-893,1000,1000,-729,667,-933,751,-551,308,-1000,-1000,-299,451,212,1000,-912,477,-695,1000,1000,-491,930,-261,1000,-1000,1000,977,-1000,52,620,611,1000,180,1000,1000,-357,-1000,-964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-630,-185,550,934,-476,-366,323,-880,194,48,-1,-930,131,120,867,-918,-682,464,-811,219,-1,495,-989,-647,55,-777,454,-227,-715,-682,784,-320,-395,-582,-791,-487,-526,209,-209,51,988,-318,117,476,947,132,462,-803,973,1,-962,107,290,-590,-359,-798,97,-584,502,958,688,-601,-511,755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{211,-110,-613,-501,164,-31,-870,678,-389,596,-84,1000,-392,-1000,-555,652,-556,-1000,291,122,-479,-1000,-155,-389,-498,1000,-517,842,-353,-224,-1000,-1000,-1000,413,613,265,-32,-172,-242,1000,-1000,-1000,-67,621,-216,395,-1000,-502,-1000,-112,1000,643,-1000,-150,636,1000,27,489,-461,101,-973,1000,1000,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{1000,413,-745,628,232,331,-28,464,-1000,-721,-452,260,883,656,-513,-103,-394,919,269,-1000,-657,214,274,-266,-708,267,96,-1000,-829,788,1000,-725,147,220,-98,171,-1000,-1000,82,-186,255,-147,3,663,-1000,-543,1000,279,-99,396,532,461,1000,-110,1000,-1000,-229,366,388,-229,-446,-959,1000,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-100,-1000,1000,-17,796,-1000,-889,1000,-1000,1000,279,-382,-293,-505,1000,1000,1000,145,-1000,-330,650,57,-1000,1000,1000,-1000,-1000,1000,773,-1000,86,-459,-949,-807,1000,-1000,-1000,-404,-1000,-882,479,-767,1000,-439,1000,1000,-700,1000,1000,317,800,1000,-1000,-1000,972,1000,1000,1000,-758,-276,1000,1000,424,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-48,-68,-1000,-261,-248,867,-302,916,-707,266,404,351,-1000,1000,-1000,-428,154,1000,32,552,-1000,37,1000,753,403,244,-907,459,930,270,819,-1000,39,557,161,41,-926,-97,1000,877,32,-1000,-305,599,519,647,-114,1000,-1000,1000,87,852,564,-342,-1000,479,-157,709,299,195,-131,-636,58,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-790,576,-336,-806,-818,-321,564,-341,-274,-338,-657,951,-1000,1000,-1000,-873,323,1000,767,-30,-1000,1000,-92,1000,-196,844,-1000,-432,-365,878,994,-1000,-179,1000,-728,641,-818,760,1000,-563,-810,-967,-1000,1000,407,429,1000,1000,-981,358,972,415,-819,464,1000,1000,-737,-478,-57,-1000,-760,-760,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectReader", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{-61,127,-259,79,-168,323,414,569,639,-646,-809,-316,896,-626,16,332,-553,-427,-246,640,959,124,-1000,-1000,-646,559,928,-912,-547,-56,-674,-903,-1000,-974,838,441,-133,-43,-502,1000,-671,-1000,-491,-358,-355,-1000,-640,-136,-1000,-387,381,-727,-490,225,217,-1000,161,-603,1000,215,-1000,341,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "reader(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectReader",
            new int[]{88,280,-339,-882,-883,-347,-592,285,-47,-154,-71,1000,-712,1000,-1000,-428,213,992,216,676,-1000,871,1000,125,-101,732,96,-1000,39,347,819,-1000,-362,879,53,1000,-758,414,1000,1000,-372,-992,-578,1000,493,187,546,1000,-1000,548,1000,371,-1000,-338,-1000,-376,-305,287,70,-640,-500,-636,58,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-425,451,-74,154,593,803,-511,-1000,1000,-347,114,-590,400,-551,-580,21,-400,-300,-400,-82,431,428,149,-285,-300,-215,-342,310,-737,1000,-436,393,-284,758,1000,560,-228,-716,32,-90,718,-308,-377,-294,1000,-280,437,-643,-956,980,533,-400,-658,543,1000,-96,193,129,872,326,379,488,105,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-94,19,-194,979,-859,1000,-290,-397,897,235,22,-215,-527,1000,-396,-676,-27,535,471,305,683,-283,1000,-376,-131,258,-51,-945,432,104,-227,361,-275,-921,-360,866,125,-309,-55,-627,199,290,-421,89,257,-61,242,-655,-491,171,-1000,358,200,-124,1000,792,-343,-12,-783,-137,-176,-512,135,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-415,463,-101,-336,-971,-400,-386,384,-1000,-109,-155,-184,-1000,515,-1000,728,-486,24,-209,-389,-210,72,371,440,705,-263,400,430,191,52,-573,870,455,115,-765,-5,587,-400,-1000,128,297,-1000,-277,-1,654,270,724,-246,761,-400,-451,-726,-552,-202,415,378,-727,331,-400,580,-476,-147,-140,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-300,-630,-446,-972,-446,-638,-953,740,-872,-883,220,722,-617,932,-822,-178,-508,186,-681,-493,374,697,-187,-414,751,-37,816,-726,311,149,-100,223,624,82,-970,-445,610,-523,-631,-36,-440,-835,140,503,980,187,-119,-214,854,267,-534,-936,317,406,504,-160,-814,-878,-64,-470,-686,-799,-63,-945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-264,1000,101,979,439,-257,-357,21,897,-532,907,276,347,-501,273,-1000,-1000,431,-82,510,431,100,-616,-779,330,-312,-70,-454,203,-50,249,607,-292,404,729,-1000,-391,-1000,329,603,199,251,-186,-994,-176,-229,-65,-271,-1000,568,402,-43,-838,-414,1000,-369,230,-180,872,-261,1000,1000,-1000,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-182,537,-456,707,868,1000,-540,-977,-266,-495,255,-364,260,-731,-956,-300,1000,-463,411,-621,902,1000,88,188,-17,-4,-1000,88,-702,459,472,706,233,895,801,-330,8,1000,20,275,-969,-170,-156,-563,-176,-519,138,-1000,1000,1000,306,252,-927,1000,-927,478,4,-484,1000,758,940,348,629,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,129,-926,857,710,810,-760,-650,-1000,139,1000,-66,1000,-843,-956,1000,-487,-213,1000,-709,762,-17,-878,188,-17,-1000,60,486,-1000,-670,1000,-864,-38,328,1000,-981,-450,1000,634,-540,-1000,-1000,188,-563,-176,-1000,138,462,456,1000,673,-765,-983,1000,-465,-310,4,96,321,267,1000,251,629,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-403,-186,-456,-391,20,1000,-1000,-641,-524,188,151,-315,-557,102,-956,-221,143,-412,327,-523,-554,312,1000,25,-158,-232,-255,1000,-648,621,-381,-731,-407,795,-212,246,489,1000,-453,-159,413,-149,-642,331,83,-267,138,-832,5,1000,1000,160,-28,589,-1000,721,283,499,391,849,-627,-359,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospector(com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-568,1000,-52,-483,71,146,629,-218,48,-4,-45,-780,-570,-602,-1000,537,56,169,635,1000,965,-656,333,378,-17,-56,882,348,82,-299,-145,706,-137,-36,341,-1000,8,66,-849,150,-596,-669,-439,-966,195,-519,664,-102,1000,-487,-301,202,-1000,-387,-10,774,-446,681,-255,1000,1000,337,-256,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{358,400,-278,-317,642,798,-843,1000,184,-504,312,1000,-359,-1000,-65,-485,-909,-1000,-450,110,-393,1000,-505,-291,9,324,-805,-689,400,-223,-307,-1000,423,132,259,1000,-1000,-754,-216,-231,-1000,-562,422,-1000,-71,364,-504,341,-831,-843,322,418,-826,1000,538,1000,284,-1000,684,-439,-601,-663,977,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-387,-1000,-56,594,-1000,-173,744,699,-119,-1000,-427,1000,396,-680,-335,1000,-998,1000,1000,-354,-1000,1000,876,-877,974,-364,-724,-64,776,-1000,-1000,-1000,-194,1000,-1000,711,-434,494,-1000,1000,1000,-338,-288,1000,-1000,1000,1000,1000,256,-1000,-483,767,-1000,-816,500,-917,890,-939,33,-1000,453,336,1000,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-334,654,-636,538,174,-128,-488,-1000,939,-999,-585,400,214,263,-1000,-1000,-1000,517,-338,-974,-333,356,913,-400,382,-271,-1000,-114,243,904,370,-293,-532,270,203,-51,412,-603,331,215,400,-593,1000,400,-740,150,-8,-79,479,-303,-638,661,83,-877,50,-126,1000,473,323,-98,358,-1000,1000,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{693,-738,389,215,909,1000,475,-678,1000,1000,597,-517,-351,-113,-218,616,816,0,330,-92,-71,-16,-60,-748,418,-285,62,1000,539,283,-1000,-306,-941,-146,1000,-86,563,-263,-263,242,762,-1000,-176,933,-651,-255,-546,54,-750,-134,-422,230,406,353,-441,-420,312,37,129,258,-342,480,-288,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-246,525,438,-667,-951,481,-597,-905,151,637,953,-310,-155,-227,-605,381,895,-619,-288,115,-531,426,-697,313,-150,346,529,870,165,-223,-932,390,768,-942,896,421,-899,-92,-169,-84,-597,-177,23,512,473,-245,-519,-676,-158,-620,-174,-91,152,900,-201,-171,-638,-271,-245,777,432,636,-659,658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-286,19,88,-71,749,1000,293,1000,-230,-157,215,1000,-398,-844,-437,-138,-936,-1000,306,-239,-1000,798,-1000,-115,-1000,1000,-840,-596,-20,-710,-1000,-496,-924,-333,-1000,400,-1000,-636,-239,688,-211,-694,596,-1000,37,-172,-55,530,-711,-1000,-912,14,-1000,1000,77,650,-23,-987,42,-7,-1000,-108,210,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-669,-300,-586,924,145,-566,1000,-542,1000,-1000,-1000,-875,105,-402,-38,-441,-1000,1000,1000,26,-129,-499,1000,661,-1000,-1000,-14,-229,-1000,-1000,923,336,-1000,993,665,-37,533,397,621,1000,46,-1000,1000,-829,-876,1000,313,875,1000,-577,-1000,1000,-1000,-1000,-1000,-706,1000,-774,1000,287,636,-751,942,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,-870,414,-240,93,1000,-744,113,1000,158,-1000,681,415,-102,1000,-65,1000,1000,-1000,1000,1000,1000,-1000,-152,-649,512,1000,-127,991,-312,-97,-1000,1000,-982,-1000,886,-5,-1000,1000,1000,545,508,1000,-25,-1000,-1000,746,-1000,125,-1000,1000,1000,-568,-1000,-1000,1000,1000,701,-248,-1000,1000,196,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setAnnotationIntrospectors(com.fasterxml.jackson.databind.AnnotationIntrospector,com.fasterxml.jackson.databind.AnnotationIntrospector):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-5,-569,-910,104,-71,701,1000,709,807,-251,-118,-677,-724,-936,809,633,-1000,-64,802,382,-955,672,289,-921,-33,-446,-1000,1000,-130,-434,-1000,1000,-1000,1000,118,347,-1000,-268,-766,1000,-238,-675,315,-725,-1000,594,-674,1000,4,-1000,-1000,394,-1000,633,-444,-5,1000,-1000,442,20,-288,-158,504,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,-1000,-67,1000,-285,-607,838,1000,-345,1000,1000,-57,1000,-210,93,-597,946,-1000,-1000,-450,-102,955,-985,76,1000,483,-1000,-1000,-1000,-390,721,-1000,250,-542,-263,-1000,-961,918,-1000,1000,-846,-168,-564,-652,393,724,-1000,-941,977,-1000,-53,258,-342,-761,157,-481,-1000,983,1000,1000,94,1000,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-76,349,-160,-16,827,-257,-239,538,707,236,297,968,12,-106,161,-425,113,1000,-2,-586,160,929,717,-832,-637,-525,504,-731,-285,-48,569,-226,48,321,-566,373,-113,94,1000,-260,-134,-805,-720,-670,207,373,237,-190,-316,346,-596,178,-122,922,106,282,-687,-506,1000,141,846,253,-61,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-1000,1000,-226,-620,699,964,1000,944,-449,-1000,400,-474,305,-125,-270,-1000,6,995,487,156,-947,855,1000,-249,-195,1000,1000,1000,97,708,1000,1000,-1000,-96,625,93,1000,12,-937,-1000,1000,-483,-783,1000,-189,1000,1000,362,-1000,-1000,495,-1000,-1000,-12,-392,-1000,79,-1000,-1000,-1000,-1000,925,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{962,-685,-170,-56,-787,913,457,1000,-662,-281,-607,451,-192,798,-497,214,-1000,711,-7,753,571,-293,0,1000,-411,457,1000,1000,1000,-864,-33,754,339,-1000,1000,-447,-169,960,-1000,-1000,-310,1000,498,-890,251,-1000,-524,-24,-395,-1000,710,585,-1000,-953,-188,-927,-667,546,410,-349,-1000,-1000,893,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{386,-233,-141,-211,492,928,-302,986,533,-815,301,1000,221,567,-311,-701,1000,755,-708,-358,-17,-138,553,-60,-1000,93,1000,255,996,-1000,686,590,293,-1000,-96,-733,-28,1000,-211,-482,-642,-106,-706,1000,827,99,342,-214,-231,-58,-120,223,-1000,-495,-420,-784,-802,677,834,329,266,-123,747,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{185,-401,476,539,661,834,-1000,1000,-1000,-493,-501,-1000,-1000,1000,-559,-793,150,-528,629,-1000,1000,-1000,432,733,1000,-1000,-1000,1000,-1000,697,420,-168,-319,-3,-442,195,143,-834,-1000,-1000,992,123,-1000,826,-60,590,1000,-473,883,363,-608,1000,292,-1000,-1000,308,-1000,1000,-343,1000,-616,-326,-569,-143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{117,-751,-377,649,664,-722,-55,-709,-16,276,-520,-191,-482,-998,-274,-118,590,424,711,-106,4,938,-688,-692,122,60,912,-852,935,176,218,-945,-726,912,25,80,-759,-470,103,-204,150,-605,6,671,-112,985,-429,-313,407,189,-844,-851,290,711,239,-661,803,-716,-77,-807,655,80,85,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{675,-875,476,359,1000,-335,-640,777,855,-68,629,-1000,-1000,799,-866,-757,-153,473,-1000,-1000,-863,210,522,-1000,-272,518,151,-332,-1000,-428,1000,163,-559,768,-1000,461,-87,-426,-1000,-588,496,-1000,-1000,-861,-359,1000,1000,-21,-206,1000,-294,-379,609,639,-246,70,-435,-1000,23,1000,1000,1000,-241,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setBase64Variant(com.fasterxml.jackson.core.Base64Variant):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-103,-1000,192,-552,-424,536,908,695,417,250,-190,1000,381,-167,-550,291,-693,946,-561,641,-590,1000,724,400,-1000,864,1000,-1000,400,-1000,-337,876,924,-992,202,-195,-65,1000,949,-488,1000,-846,292,-1000,1000,-475,-983,1000,-768,-1000,601,-53,-1000,-326,547,-45,-481,120,438,-803,-400,-803,1000,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-358,-466,162,-161,-26,-260,265,1000,1000,-819,-814,0,798,-968,-595,-51,-718,-191,660,1000,-373,-283,-428,277,-386,994,-608,257,153,-240,-432,-192,-403,-266,-23,786,205,-678,-1000,1000,-962,0,1000,-215,0,1000,0,-569,-116,-1000,494,-1000,609,861,976,550,0,-907,-1000,417,-1000,151,-871,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-423,-433,47,289,883,-806,-133,541,407,-420,657,264,922,28,-156,-151,-722,-339,-911,-217,592,-261,352,-562,-703,0,-415,81,510,-268,208,539,99,-305,-521,-879,-791,-104,-145,-350,422,-857,-367,68,400,400,276,195,40,1000,-137,45,-133,523,13,1000,-404,514,-485,-73,806,138,272,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-646,-835,790,719,833,-865,-200,-50,-423,957,815,338,-68,929,-791,625,-719,-389,37,-582,-181,-200,469,-753,263,-247,260,544,-284,-82,647,-416,640,79,-193,-555,-859,-430,341,13,671,0,-901,146,-410,-192,129,282,72,881,-484,947,-687,-555,448,-95,138,515,-29,7,737,-725,-406,-775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-44,-266,-601,-227,-280,-208,42,423,575,-594,-1000,-67,1000,-1000,1000,6,893,-861,378,108,-610,416,-1000,1000,-875,280,-1000,-1000,-695,34,-361,-857,-773,255,-622,382,1000,480,62,-309,-1000,632,1000,-481,546,877,648,-407,-1000,-1000,711,550,1000,1000,594,261,-1000,948,118,1000,-216,1000,245,777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{862,-1000,1000,513,858,445,-52,1000,1000,25,-121,-1000,1000,1000,118,-622,-363,-1000,-1000,-1000,1000,387,1000,622,798,-612,837,1000,857,1000,626,1000,-121,-1000,-628,-1000,-544,-1000,1000,-1000,-197,1000,-1000,720,-1000,-1000,137,-1000,1000,1000,-889,10,-1000,1000,-1000,-346,-1000,423,-1000,-291,1000,1000,235,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{498,954,-751,-859,846,-101,-245,52,-743,864,425,703,253,-829,-254,760,-974,475,-276,774,628,481,385,-644,984,-190,-992,-902,848,-586,-92,977,-454,-399,-704,916,-349,492,94,199,559,-502,-581,-477,348,296,942,602,-701,-307,-733,-543,934,-253,98,-221,-406,581,59,-908,-830,427,-289,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{141,184,1000,-186,384,-125,-896,580,725,766,544,-289,874,944,955,-1000,-171,-466,-682,-158,349,1000,115,102,-814,-305,56,59,352,814,1000,-646,349,-585,-562,-780,-155,-858,1000,-1000,295,386,-1000,-331,-540,-601,883,-1000,368,400,-720,379,-400,1000,-1000,47,-1000,634,-656,-749,-33,1000,964,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-129,462,393,565,1000,-872,1000,427,-533,1000,1000,-245,646,969,-948,1000,-1000,-224,-902,309,1000,-1000,1000,-149,1000,-1000,52,-1000,1000,-802,1000,436,371,-1000,-1000,1000,-1000,210,887,1000,1000,-121,-1000,-92,-315,183,1000,1000,274,885,-1000,236,-596,1000,-523,-626,-144,415,-771,-1000,-592,-376,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDateFormat(java.text.DateFormat):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-95,176,1000,684,409,23,-995,526,1000,397,-745,-992,1000,-407,696,54,768,-1000,-379,-829,-152,797,112,1000,-573,-1000,-6,-401,-186,1000,1000,-29,270,-1000,-1000,726,1000,-702,1000,-558,-241,458,-823,1000,-900,-1000,-154,-715,-186,780,-512,1000,-848,1000,-727,7,-1000,-287,-1000,145,459,1000,489,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-734,79,48,428,1000,-994,-1000,-176,-751,275,380,-809,1000,1000,-1000,-1000,-247,426,73,1000,-964,-1000,-577,-543,-1000,287,-1000,217,66,-81,268,-1000,964,833,-826,618,1000,-563,1000,634,1000,-1000,-1000,128,-125,834,18,444,-549,974,-1000,1000,1000,-1000,499,173,-1000,-852,-619,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-28,43,-1000,-71,744,-763,883,-1000,-1000,1000,18,-956,446,100,724,825,-587,-1000,41,-408,-229,100,-966,-1000,-175,-624,178,271,-795,322,-176,322,99,-23,313,669,1000,-360,151,483,1000,205,1000,-851,-1000,171,1000,758,626,-79,-157,1000,-1000,-449,1000,-267,-70,-906,-100,-402,-82,899,766,389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{33,1000,475,424,-429,478,466,-859,-4,506,885,-1000,-402,1000,-1000,-477,-881,-525,392,950,12,-153,-819,-678,-97,-463,161,916,-7,-108,-325,273,-55,-135,264,614,430,735,-1000,-328,-97,535,178,840,-926,-498,831,565,293,-571,190,1000,-257,198,621,1000,-1000,491,-333,-594,-848,-545,-834,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-405,-476,-890,-514,-1000,571,-471,-1000,1000,-741,-956,517,805,-1000,872,-308,-1000,1000,-1000,-1000,-863,-564,-1000,443,-10,705,736,62,-743,-697,181,-594,-885,283,379,525,525,692,1000,1000,-749,1000,454,-756,473,1000,1000,-774,-265,71,1000,-1000,-547,1000,1000,-1000,-241,884,191,82,636,-264,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-668,-1000,213,-189,-1000,425,-1000,-351,1000,-5,376,55,358,-51,861,410,-296,945,-282,-454,-246,-167,-481,-727,-461,715,1000,-1000,-340,-105,32,-1000,-233,821,1000,480,299,-751,1000,1000,984,1000,-322,-752,301,1000,1000,-765,124,655,1000,-1000,-969,1000,439,-184,-1000,763,-31,-411,-316,951,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-431,497,-1000,549,924,-812,1000,-1000,-1000,1000,-868,743,813,177,1000,1000,-753,-1000,-100,-328,334,756,-757,-1000,-158,-733,146,-335,-1000,-64,103,88,-1,-774,821,1000,1000,401,-750,-9,1000,1000,1000,-1000,-1000,170,1000,792,447,774,-173,83,-1000,-736,893,-993,-137,-1000,-718,-1000,-774,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,428,99,208,535,862,-500,450,-1000,-345,1000,-149,554,903,166,-177,-683,432,-247,1000,204,463,-739,332,-1000,-880,-433,-240,-1000,156,66,701,317,-1000,822,50,450,-593,-1000,-1000,-654,-264,-743,75,-335,-1000,-808,-287,643,-24,-123,-790,-1000,-870,-793,-942,1000,7,-1000,32,-717,1000,-272,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-595,43,498,353,1000,-1000,22,161,478,211,962,-816,446,100,-485,-190,112,-313,292,918,-205,-49,-463,-189,-470,-750,506,-680,-390,346,930,1000,710,-23,571,504,414,-1000,151,-304,252,-210,73,-33,-17,-306,1000,256,431,505,-87,-635,65,288,-1000,-413,221,-793,693,-402,-104,599,-448,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setDefaultTyping(com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{759,618,-131,-164,-393,-1000,155,-961,-956,400,-1000,-403,923,-269,-462,677,-713,-438,765,-1000,375,-785,-1000,-883,830,-104,896,85,561,-181,-967,1000,124,-716,-389,-515,638,-652,358,1000,-1000,-906,1000,1000,-894,570,483,809,116,-934,-655,-1000,-1000,1000,1000,557,-1000,157,986,-820,876,-1000,-666,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-137,445,507,518,1000,-161,195,300,1000,1000,1000,-754,84,-462,-1000,220,50,-495,1000,-1000,654,185,780,1000,690,-228,-430,-139,-1000,-42,478,-497,-309,1000,70,463,1000,-1000,-1000,226,1000,439,1000,-46,1000,-1000,185,155,1000,208,-1000,1000,-42,844,-1000,123,372,349,1000,-1000,-647,-676,68,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{1000,-369,929,215,841,1000,330,798,-484,43,-957,-770,1000,-30,-955,689,-323,-312,-148,1000,552,-572,-835,-435,-21,-100,-618,896,1000,1000,12,836,-1000,-1000,104,-1000,-304,425,176,1000,623,731,-1000,-771,-435,-779,-997,-128,-261,987,204,-336,-1000,231,-464,-307,-1000,48,-1000,609,1000,-641,9,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-95,-883,536,-391,816,-595,-103,-395,492,-1000,-271,-876,-1000,-952,-1000,-561,301,-457,1000,-766,-342,-400,-569,1000,153,-77,242,1000,1000,124,1000,-355,486,983,-459,382,859,-778,343,-924,-38,731,333,-823,-899,-555,1000,225,761,744,-591,756,-1000,-283,1000,-307,-1000,73,647,1000,-1000,-1000,-380,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{941,-11,-90,-676,416,772,-706,466,-589,-911,-170,-322,-582,-344,-328,-699,-985,-472,423,801,862,-765,-981,-12,-73,503,-173,48,-127,-456,-537,436,155,-872,-280,371,-936,384,915,984,-329,604,-959,4,442,176,-860,-740,133,254,474,278,-467,720,286,764,569,-384,199,857,361,294,620,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-1000,-312,628,445,203,-414,1000,-1000,359,35,489,361,-204,-276,633,1000,-717,-774,1000,-379,344,835,504,912,812,-709,806,716,-616,-380,-467,244,-445,703,1000,434,1000,-241,-741,-690,761,-1000,869,576,1000,-792,1000,1000,-178,393,-755,737,915,-104,846,790,-838,562,1000,246,-1000,-405,374,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-485,1000,84,-876,831,1000,-637,521,20,397,538,-340,-436,-801,-50,449,-685,-234,106,1000,429,-1000,-200,1000,1000,632,-724,-840,-782,-647,-922,-1000,-98,148,492,1000,-400,-947,-60,153,113,1000,-380,-367,1000,-823,-645,-316,1000,-502,-20,364,736,300,113,361,1000,895,1000,-1000,-780,207,136,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{290,-447,-791,-363,-512,211,-1000,391,-1000,130,874,215,-63,366,-1000,967,426,-861,-1000,387,-91,-538,-1000,-645,497,1000,-1000,-1000,-839,-1000,-1000,-944,-540,-1000,-688,534,-763,1000,-1000,647,-1000,-1000,-959,-1000,-1000,-287,-1000,-950,812,910,1000,-1000,1000,187,1000,504,1000,442,-546,-609,203,939,749,845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-194,433,263,-376,67,1000,-1000,1000,850,-557,380,579,1000,-147,1000,679,-1000,464,404,722,1000,-1000,1000,605,114,1000,-611,-1000,-1000,-1000,-889,-1000,-554,-1000,-1000,563,-1000,750,1000,1000,545,243,-460,-880,1000,-864,-1000,-1000,1000,-1000,1000,-1000,1000,1000,-1000,357,832,1000,-104,224,1000,1000,1000,912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setHandlerInstantiator(com.fasterxml.jackson.databind.cfg.HandlerInstantiator):java.lang.Object",
            new int[]{-463,-1000,-1000,616,735,-109,-448,-690,-1000,-1000,1000,-355,-1000,-943,-240,-298,-1000,-1000,675,-3,884,-260,-1000,605,593,868,-1000,49,393,-1000,1000,-289,654,1000,-703,1000,1000,-807,-423,856,1,-227,-131,-939,-988,92,998,692,-972,859,-443,535,1000,1000,873,1000,1000,1000,1000,-1000,632,71,248,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-888,15,-251,-496,-37,1000,87,1000,-1000,-375,1000,-734,1000,-115,86,-1000,1000,-241,-898,-377,-565,-852,-83,1000,-201,714,1000,579,1000,1000,-1000,367,1000,-981,-448,1000,1000,945,-1000,-1000,166,-1000,312,-1000,-1000,-37,1000,152,-333,-1000,-1000,-1000,494,582,-559,-1000,-1000,520,100,-1000,-1000,-90,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-858,-109,-274,-496,-747,202,-122,-146,-353,1000,1000,389,725,17,895,130,1000,-241,-898,1000,492,-189,-90,-256,370,73,-69,1000,-1000,-266,1000,-1000,1000,269,-448,255,56,785,171,412,403,-301,963,-846,145,119,310,266,-311,-1000,174,-1000,-604,601,-559,410,-18,426,-885,-1000,1000,135,293,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-885,561,39,168,-237,-486,1000,-80,-628,1000,-75,801,-454,-754,761,-1000,993,41,204,-123,168,-1000,-586,-292,1000,-655,513,-343,-107,109,357,130,-774,-1000,-247,962,108,1000,-79,-845,675,-374,578,-85,181,-1000,942,12,1000,-392,316,658,-1000,739,-452,702,810,229,-66,689,-583,-1000,788,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{232,544,-366,-886,-227,756,-1000,-187,-908,1000,-276,-271,-554,-461,-182,-820,554,680,684,-358,-243,-933,-1000,-1000,1000,310,339,-565,-224,1000,148,-376,-613,1000,-254,1000,672,-25,-877,-1000,298,871,925,-482,-1000,-288,380,30,1000,-932,61,-202,-66,70,331,851,-220,243,269,-363,174,-1000,195,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-646,-202,129,-327,282,645,585,-525,-995,-408,508,-737,969,915,63,161,947,-158,-477,-563,-705,64,353,549,-983,-243,277,718,621,674,-606,-262,655,-456,-937,-531,756,321,-982,782,-930,-322,977,-411,976,520,874,59,-816,-707,746,-910,508,383,-676,-315,-161,994,-595,27,-781,556,190,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-905,-605,-431,-714,490,-243,1000,-1000,-176,893,-496,1000,-1000,799,-1000,1000,-1000,1000,507,-565,-933,100,1000,318,1000,1000,851,278,1000,-583,-169,1000,-889,-1000,1000,1000,1000,-1000,641,782,-1000,678,-1000,-1000,-288,1000,-216,614,-289,-673,-1000,84,486,-84,-997,-618,554,-513,-160,-463,-633,873,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-159,-200,242,-622,279,465,854,-556,679,180,943,-667,-531,498,-680,-340,941,860,908,-651,327,486,896,758,915,-372,-928,-404,-913,598,249,-766,28,-304,-593,725,-178,455,-139,-824,664,468,-379,-12,993,446,917,933,-494,-827,828,6,-796,-498,916,598,898,678,819,630,927,906,884,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setLocale(java.util.Locale):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{135,-152,388,373,620,-469,270,-553,1000,45,1000,466,-20,1000,-502,150,23,88,608,244,-128,-561,381,-399,79,-223,-90,-65,269,-826,-147,-898,366,174,148,-30,-864,54,658,538,-344,53,-134,-196,-71,282,588,1000,607,-235,-97,-16,-306,441,549,138,618,-771,834,174,172,-440,-40,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-150,-594,-536,1000,118,-944,-655,-511,-1000,-521,574,509,1000,517,-1000,824,-689,694,732,564,412,236,1000,-244,-209,-133,-868,720,-1000,-795,297,-536,1000,24,407,-481,354,125,-731,-286,-458,-694,-1000,1000,-752,587,1000,13,802,468,235,-493,222,-707,-150,517,-703,1000,311,537,847,483,-1000,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{572,833,663,384,1000,168,-419,-390,239,-510,590,-835,-397,59,791,634,-44,-1000,1000,-1000,-852,1000,-117,180,530,-615,828,-784,1000,-94,936,-79,366,-547,-961,-356,768,-301,-1000,319,-1000,-1000,166,-1000,1000,989,-309,-1000,-260,435,366,-447,897,-421,-1000,577,-294,-364,-742,309,-781,-1000,897,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-582,-1000,-686,159,181,-601,1000,800,654,-673,-862,-619,-1000,-29,-775,636,248,-259,123,-969,412,865,-8,312,-571,-868,547,-61,1000,-839,1000,-953,55,-1000,821,-793,-368,-1,-1000,-114,-254,687,184,-194,1000,-59,1000,786,1000,1000,689,-278,1000,-443,-159,-618,-1000,121,-213,-487,-249,-90,307,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-895,-337,450,3,568,-93,1000,-400,657,-538,950,-244,400,1000,432,296,445,1000,1000,-95,-762,20,-239,-7,401,153,243,-1000,365,20,-53,131,400,-667,-830,-686,684,-519,-940,-708,-734,-989,1000,400,278,798,-20,-951,498,-305,625,-470,-415,-1000,-295,-838,118,-422,180,166,-1000,-291,341,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{878,21,-842,96,-794,803,63,-883,365,-385,944,290,-521,-52,654,223,183,337,181,-283,276,164,-615,604,-171,643,376,87,-566,791,90,-777,-430,-989,-839,-443,307,-703,759,-214,-27,989,-298,855,-191,534,854,864,-874,763,607,804,36,-200,48,275,436,-46,-675,-931,587,653,-329,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{825,833,148,734,412,-476,1000,1000,-187,-677,312,-417,-106,287,-19,678,-208,-1000,236,-1000,-737,784,431,63,166,-376,738,-681,833,-662,1000,-614,315,-1000,-526,-356,23,-1000,-689,1000,-990,-188,-592,-1000,1000,572,1000,737,994,1000,366,-1000,832,210,-686,468,-444,174,-481,627,-699,-522,897,444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-654,919,-573,-611,0,-57,-89,172,-277,-1000,-143,-1000,34,1000,-1000,513,-1000,830,-977,-1000,-416,-90,-333,-62,-1000,-1000,1000,-641,900,-1000,64,-776,-806,-6,831,796,-457,-1000,-762,579,-660,538,-1000,204,-177,-1000,1000,-520,1000,390,803,451,297,1000,343,890,-849,1000,702,968,-466,430,-52,971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{548,-1000,-1000,38,536,-1000,-53,-102,-88,-1000,1000,-1000,-1000,-850,-740,-282,-339,422,654,-855,-1000,-303,1000,-1000,-433,-1000,592,-1000,-1000,305,253,-636,442,-213,542,1000,1000,-7,-693,-44,-1000,1000,-834,-155,-492,379,305,-241,21,-1000,47,-1000,-1000,1000,1000,59,1000,649,744,514,-84,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-286,-1000,-343,452,963,-815,-283,397,264,-693,1000,-581,-1000,-882,643,-711,205,1000,1000,-201,-1000,-319,311,-844,-415,-537,597,-1000,-1000,-89,59,-239,-554,279,522,160,1000,1000,1000,334,-1000,124,-241,-512,-194,174,-726,-563,-170,-725,-1000,-1000,-1000,732,297,-565,1000,412,753,856,-829,578,-954,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategy):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-235,-806,-1000,809,937,-1000,-213,-490,-527,-750,993,747,-1000,-365,-1000,-731,-30,-46,1000,533,-1000,20,1000,-858,-1,1000,-1000,-132,-897,-299,-905,641,1000,1000,1000,1000,1000,664,-1000,899,-1000,-1000,400,1000,-807,1000,-212,988,-1000,-876,546,-1000,-1000,-1000,704,-34,840,-594,-99,-380,46,266,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{512,-1000,127,634,-540,1000,-998,339,1000,1000,-1000,-610,-736,1000,1000,-725,-868,-1000,637,898,1000,-1000,1000,-1000,-1000,-1000,-141,1000,1000,-1000,1000,-1000,-1000,1000,55,-406,-813,582,-963,1000,1000,-1000,1000,-50,106,-1000,-1000,-120,-171,-1000,8,-784,1000,193,1000,904,-372,422,-1000,-1000,-1000,-61,-1000,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-840,491,-1000,-256,394,-320,183,924,98,-632,383,467,-579,42,40,748,222,197,-778,933,821,1000,110,379,269,1000,-291,1000,-830,326,-853,-906,-17,-679,158,-60,-95,-190,821,-1000,-911,1000,-311,-90,863,543,1000,1000,-866,1000,-714,1000,120,-373,-77,-548,-620,-442,1000,969,1000,-81,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,54,-551,170,-824,40,328,1000,-754,-1000,-324,-536,-713,-608,885,593,1000,1000,-735,-132,-1000,1000,281,5,1000,1000,400,-994,-1000,-803,-853,1000,416,-1000,1000,-877,-624,-1000,-484,-441,126,1000,445,-1000,1000,2,1000,1000,-996,1000,367,558,1000,111,-1000,-1000,-902,-601,1000,1000,1000,71,-1000,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-272,-631,-598,-406,-425,744,-1000,957,287,1000,-307,-202,-627,332,-65,651,-1000,-611,84,622,540,-761,1000,-720,-1000,-873,-449,14,1000,612,-73,197,385,928,-460,-382,-437,1000,-1000,1000,322,-381,1000,-1000,-535,-1000,-626,688,-1000,-632,339,-799,58,-503,131,-392,98,12,-1000,-1000,673,-180,-13,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-519,419,-1000,3,-103,-322,646,881,-1000,-1000,-331,57,-244,-160,444,1000,216,826,-775,438,-633,1000,-843,426,1000,1000,-387,-714,-1000,577,-1000,578,1000,-1000,429,1000,859,-817,-813,-1000,-837,1000,433,-49,1000,143,1000,1000,-422,993,-508,-335,832,-568,-934,1000,73,-854,1000,1000,1000,408,-1000,-282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,1000,758,-130,470,-631,-8,613,309,-1000,-843,-491,853,-413,-104,241,-405,1000,121,949,-1000,1000,108,-475,-1000,-588,1000,937,979,993,-1000,-17,1000,530,994,313,-1000,-813,1000,392,-1000,1000,151,-904,-1000,-146,10,974,620,-42,1000,-582,-265,-600,1000,37,82,-630,-1000,-1000,531,1000,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{165,-701,-346,-767,-219,-177,381,622,545,9,-530,105,491,-1,1000,-361,989,-361,-464,180,-31,88,1000,-62,-704,582,-7,679,264,471,57,-438,25,-541,637,-511,-451,-16,-680,-952,203,562,-464,-100,-85,-558,660,1000,-868,1000,-617,-634,-96,398,391,-326,-797,280,-402,321,-113,-101,268,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "setTimeZone(java.util.TimeZone):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,516,-721,302,-439,433,-40,29,660,45,-306,526,988,593,-642,-33,-323,679,444,-54,-432,1000,855,-958,-105,-19,-141,323,1000,350,-1000,923,-411,281,-306,-183,-643,-916,-440,-844,-202,-914,1000,90,-1000,-340,27,-441,-450,-824,669,-257,776,382,1000,-1000,-431,646,76,1000,-169,441,-1000}));
    }
}
