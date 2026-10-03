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
            new int[]{-1000,32,-29,-171,-169,818,867,1000,30,-221,1000,166,-501,673,-1000,-334,1000,-301,142,171,-298,-1000,-153,706,677,-1000,-209,444,-1000,-449,-94,-937,-82,1000,-45,429,-1000,-745,-1000,1000,369,-986,-31,486,1000,1000,1000,-1000,575,428,-174,-301,754,1000,-289,1000,127,1000,1000,-1000,1000,-37,1000,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-499,-888,-6,-784,1000,-130,-649,-551,-1000,-178,-1000,347,-62,-717,758,718,706,-294,-1000,-1000,-1000,-1000,-541,-299,-277,1000,-677,1000,99,-689,-512,471,-139,-924,-1000,-205,613,-317,-39,-938,1000,-922,1000,573,-1000,1000,-754,-1000,597,-633,-777,-501,738,-451,-607,-1000,-944,-1000,75,809,521,-1000,368,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-103,-380,807,-92,207,120,-97,-119,711,-633,-345,549,92,-550,-753,295,1000,93,-439,-886,-801,-490,-896,86,-1000,-40,-723,962,369,-248,-687,967,-375,610,-639,64,44,35,-652,-124,523,-387,112,345,-179,810,1000,-489,899,-401,15,-348,-85,319,58,-566,-653,55,529,-423,1000,-771,-142,-944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-29,28,-329,764,165,35,201,-539,727,-588,-952,95,64,-1000,647,-146,-663,419,-30,-1000,-1000,-1000,-585,-255,-471,949,-1000,1000,956,756,-1000,-28,-183,30,-542,-262,383,125,-563,-1000,667,-125,333,-49,-679,1000,1000,177,856,-969,137,-343,-352,238,335,-1000,-1000,337,305,879,568,-628,-453,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-349,-463,-144,-406,65,-46,160,-970,-737,-1000,458,855,-313,-1000,-642,431,78,-669,1000,57,1000,1000,677,-868,-555,751,-484,-966,-1000,1000,-133,-815,389,875,1000,-1000,-446,-420,700,684,566,1000,-1000,-357,273,-1000,1000,333,118,-555,1000,824,-1000,-72,138,-983,912,426,-287,-905,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-605,368,-998,204,1000,44,310,555,-855,-317,285,1000,351,-113,-63,-388,72,-154,543,-243,-1000,-891,-48,-271,-243,282,12,-287,-1000,-515,-131,-710,-298,-265,-576,774,-376,-952,-693,-473,-570,-1000,776,602,541,843,1000,-554,1000,-122,112,-368,-157,1000,-379,445,103,771,603,55,292,-488,530,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{658,-531,233,-105,1000,610,-301,99,-397,-114,1000,126,1000,-914,-1000,-436,712,-717,8,-780,-260,645,604,635,-1000,-266,86,176,-1000,727,-1000,428,-797,247,-1000,1000,-338,-7,-600,-362,-1000,-906,-824,1000,1000,-1000,1000,37,134,-639,267,-605,-364,908,63,1000,-954,267,43,-581,558,-1000,-804,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-290,-577,-1000,-201,1000,806,417,241,-426,-312,379,1000,1000,816,-364,234,-302,-655,568,-56,-823,-598,232,-809,-194,-768,119,711,-813,-1000,-466,1000,-436,1000,-309,927,1000,-897,1000,1000,-1000,-291,-925,821,875,-93,1000,388,91,-821,1000,-295,938,1000,-948,-60,805,740,294,-860,351,116,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-1000,32,-505,-216,-245,792,562,879,-1000,-614,1000,-528,-1000,462,-382,43,1000,-1000,-347,563,-221,949,1000,294,653,-1000,-270,207,-554,19,644,-1000,1000,905,1000,-133,-1000,-745,-745,1000,1000,-125,220,403,1000,-1000,543,-989,537,182,-407,-5,754,-39,-1000,1000,1000,134,249,-763,1000,262,1000,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-1000,1000,-391,79,-838,110,1000,642,154,155,1000,-1000,1000,-976,-120,843,-1000,1000,-1000,-1000,-188,-897,1000,-661,-507,-1000,-1000,1000,-1000,1000,-407,-878,1000,-1000,550,942,1000,124,-1000,-49,-1000,449,-1000,1000,-1000,586,659,-1000,-1000,-684,-127,-1000,1000,-727,1000,-219,1000,-315,1000,-1000,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{158,-732,569,-542,-1000,-654,-1000,775,-725,175,-601,-471,-280,1000,376,661,1000,-666,39,153,117,926,-263,434,-792,10,915,-1000,301,-311,932,-531,-117,1000,-989,-792,-403,-682,869,25,329,-9,-494,-764,1000,-337,-725,-686,-1000,-554,-177,-922,-1000,-1000,-867,922,-856,-797,-1000,329,-821,362,-987,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{1000,-103,-749,304,-8,498,-989,-359,-787,-328,-161,-1000,551,241,538,1000,-1000,-324,-797,-1000,567,-242,1000,-761,-153,1000,-1000,464,616,-427,-186,-1000,483,-423,-712,327,983,-1000,788,-227,1000,50,-795,-494,-1000,1000,-781,970,-231,-128,-2,-312,842,1000,-1000,461,-1000,134,-967,-473,-956,561,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-1000,-454,1000,73,-686,455,637,883,-459,-65,1000,471,16,21,-264,-497,207,1000,-857,-1000,-123,-245,1000,-1000,-640,773,92,1000,-622,1000,-597,581,64,-3,-330,170,760,204,-1000,719,-1000,-739,-138,-182,-1000,-236,-127,-1000,-1000,-224,-32,-965,283,445,1000,-950,854,-655,264,-1000,-593,1000,-768,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-479,476,-965,-56,320,493,-196,-850,-801,-262,233,-182,-638,-442,784,171,777,292,-764,21,-1000,-1000,451,43,-518,-293,538,-196,-253,42,-402,145,480,-1000,888,-25,-1000,-686,-387,624,22,-1000,-209,912,-175,-790,-446,279,616,599,350,56,389,-703,-196,-100,-210,-246,-580,42,1000,863,821,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{199,-310,-930,944,748,646,-861,-754,-809,13,882,-817,-661,-103,318,343,-644,982,-731,-482,-456,-605,904,-546,-991,275,-923,360,968,-641,56,-109,650,324,-387,201,696,-619,554,-215,754,-342,-295,-696,598,293,-838,354,-522,835,159,246,659,530,-703,229,-856,407,89,-651,433,-339,-242,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{-988,1000,565,-181,-552,1000,1000,390,-720,115,1000,-727,798,-408,280,659,-57,536,-991,-224,313,-406,1000,-284,-406,216,-745,967,-1000,721,-898,702,-1000,-213,-285,-3,577,-328,-1000,510,-1000,-411,-326,-268,-1000,1000,982,-1000,-1000,185,155,-215,-121,430,1000,247,1000,-619,-567,-868,20,576,-1000,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{471,1000,-1000,-301,244,74,902,-890,-23,125,263,459,-165,-490,-69,-11,-780,-9,-917,413,-765,-181,858,-242,432,1000,-1000,945,-657,-245,274,-739,765,-460,521,-551,-1000,780,-125,471,-822,1000,-128,947,157,444,226,1000,-539,-909,-398,746,830,1000,0,-961,331,76,659,689,-702,-144,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "acceptJsonFormatVisitor(java.lang.Class,com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper):void",
            new int[]{837,1000,-1000,-806,-919,98,-90,558,-291,596,-1000,-1000,1000,-163,589,1000,-733,-267,-996,1000,20,-897,102,-249,-507,-1000,-753,-211,-1000,-67,1000,-769,1000,-734,589,-383,1000,124,-1000,365,-1000,-284,-1000,1000,1000,797,313,-604,637,21,-127,-530,1000,560,-1000,-219,986,189,621,-5,669,851,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{206,-86,-297,64,-448,-88,1000,286,-127,-136,384,1000,-554,510,131,239,59,-214,-275,-255,-1000,209,901,956,211,312,-937,718,48,232,754,631,-230,-1000,536,714,528,-445,130,-253,897,-197,-688,-363,-762,473,379,-375,-368,-377,-734,1000,309,210,1000,93,-884,253,1000,572,-499,928,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{144,210,-621,-112,123,-410,-307,128,-1000,-742,-535,542,-48,1000,399,400,402,220,-881,191,-755,1000,-374,-71,-263,-496,270,39,-836,29,1000,-217,-250,-645,454,-359,-189,1000,1000,-269,1000,-1000,-374,10,338,141,-1000,607,-1000,740,918,1000,747,625,1000,-120,-748,-583,-1000,-59,-1000,168,1000,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-836,1000,695,-11,684,197,258,430,1000,910,-134,136,-547,1000,-823,400,169,-450,-51,-364,-178,824,1000,1000,896,-733,-268,1000,318,458,918,1000,-385,1000,-55,537,354,-1000,218,-843,-1000,774,-1000,663,-1000,668,517,-1000,1000,776,-410,-1000,803,-1000,-1000,859,1000,106,-783,801,845,-310,-1000,-981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{905,1000,1000,-881,-1000,363,584,500,648,-826,-225,1000,-811,-149,712,-482,168,-363,479,-350,-1000,23,308,1000,-1000,-719,-580,717,550,453,-110,1000,-1000,-1000,425,1000,1000,-312,-263,-480,866,-374,-329,-1000,-799,540,-179,62,556,71,590,-251,435,617,201,975,-401,235,400,100,465,212,505,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{49,-1000,-558,-352,641,350,936,1000,1000,-385,87,240,-72,-1000,426,-597,-9,675,-1000,1000,-240,-532,-522,418,-317,-412,-383,830,787,310,-1000,877,-969,1000,580,890,961,-1000,-1000,-523,-1000,513,-359,-83,-953,679,410,25,1000,-776,102,-1000,-320,750,-626,94,-1000,701,-1000,-539,1000,-803,-507,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-335,890,-45,429,-480,-90,1000,883,-104,129,394,1000,-810,837,-450,774,509,-994,-1000,-1000,-1000,1000,1000,1000,1000,49,-1000,1000,-486,174,1000,1000,-917,-614,399,1000,-112,-854,682,-1000,619,-355,-1000,987,-1000,890,331,-1000,-368,48,-1000,1000,1000,-426,-1000,260,-514,-372,194,1000,-13,1000,-252,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-341,754,-1000,528,-167,-496,-59,-568,368,-420,-723,1000,-1000,1000,101,-465,-300,-549,-195,950,-1000,1000,1000,956,1000,-931,361,511,-204,344,91,858,464,-1000,-355,612,-72,419,539,-550,21,169,-961,-88,-656,-176,-229,29,-430,1000,1000,499,234,-659,106,854,166,537,-63,180,-755,706,797,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addHandler(com.fasterxml.jackson.databind.deser.DeserializationProblemHandler):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{509,24,32,-768,811,337,-6,-86,877,220,-148,1000,-519,-906,653,-67,-835,1000,450,538,1000,-152,-329,1000,384,-838,592,618,605,304,-198,523,518,643,-463,1000,45,-890,-1000,213,-1000,1000,-928,563,-907,1000,962,97,1000,320,229,-1000,-478,369,-626,401,630,1000,-43,416,918,-536,-19,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{2,1000,-438,334,978,667,1000,928,-822,1000,-359,-1000,-680,-284,762,569,-72,314,-626,537,-696,-1000,1000,-279,587,-783,360,795,1000,-609,-1000,322,-577,-414,519,1000,620,727,-297,-59,-634,1000,261,1000,154,154,-698,207,-834,-949,426,586,-540,131,532,19,-560,-265,-555,519,-604,128,-343,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-837,359,1000,-936,-562,1000,-300,306,797,-958,1000,-1000,-566,749,-467,1000,1000,-747,-616,772,-275,863,-1000,1000,289,-1000,780,-797,-1000,1000,-1000,386,833,-469,-1000,-1000,1000,-1000,1000,1000,74,100,129,-1000,-814,143,-1000,-820,-833,-1000,1000,1000,-362,-1000,1000,-362,659,1000,282,1000,714,-563,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-239,803,639,686,9,323,146,1000,659,-187,-169,-1000,-801,1000,397,40,960,-372,543,1000,15,1000,-986,845,-114,73,-480,-201,-866,1000,-398,233,-257,274,-339,-1000,-34,30,687,357,794,222,-466,-719,-444,513,162,-1000,-431,-1000,807,680,-533,-450,197,333,-207,722,156,-335,348,-1000,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-371,680,-860,-16,64,-200,506,842,-899,921,-180,-488,-171,1000,469,-595,526,988,-1000,647,684,768,670,470,968,-1000,-41,-751,20,-388,344,55,-395,-256,-587,566,20,953,-851,427,-712,703,632,-409,378,-669,542,-213,-605,-593,373,726,-207,260,20,35,-344,403,-367,836,-434,698,-540,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,919,885,34,400,-713,393,15,824,936,-483,1000,-433,-251,106,-525,-507,966,-693,-1000,385,-360,579,-922,404,117,-191,1000,-243,0,-686,-1000,-562,-963,-384,-1000,-745,-336,-803,1000,693,972,-566,-351,-1000,-140,-243,500,-1000,808,-1000,1000,58,-665,-352,1000,-1000,881,900,52,574,1000,-968,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-1000,-633,484,615,-1000,-369,998,-1000,1000,-1000,868,-434,-176,999,-16,-891,-748,-731,-424,902,1000,1000,-320,758,-826,5,-767,993,-852,-599,-1000,-1000,-328,-807,-1000,-1000,1000,-1000,1000,1000,885,189,-897,430,-1000,132,1000,-1000,1000,17,1000,495,-153,-369,953,-681,1000,1000,782,465,827,-1000,-808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-735,554,64,-1,1000,675,-1000,1000,153,-1000,133,1000,590,451,604,1000,-1000,254,403,768,117,1000,961,-678,196,-540,318,-492,607,1000,-428,-473,647,746,363,1000,144,-526,-1000,640,606,63,434,-598,25,134,-464,-1000,1000,415,-640,589,-37,381,-466,397,1000,289,-1000,0,-1000,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-770,451,-182,-607,918,-496,200,-443,-1000,-744,-765,-1000,1000,263,-549,605,-1000,304,-724,-177,807,549,-238,891,106,213,1000,-801,-52,574,-934,849,-1000,-1000,12,1000,267,1000,889,511,-1000,12,210,1000,987,-1000,713,1000,194,1000,1000,459,-498,-610,495,791,160,400,-176,5,-1000,1000,-558,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,1000,34,782,1000,-516,-1000,-25,-1000,1000,-426,-420,-145,-1000,168,1000,-823,299,1000,240,-1000,21,-414,1000,361,429,267,0,492,-710,929,214,-467,807,1000,1000,-1000,1000,-1000,-1000,-548,1000,364,589,1000,-1000,-924,-744,-117,-350,512,-95,-172,1000,-1000,1000,403,-1000,-207,-1000,-22,573,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixIn(java.lang.Class,java.lang.Class):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{190,1000,-1000,34,1000,259,904,1000,-1000,1000,-1000,-1000,-378,-552,1000,494,199,1000,-1000,916,-403,-552,1000,-592,592,-1000,320,321,1000,-812,1000,1000,-1000,-584,691,823,20,1000,-192,1000,-1000,171,617,201,-408,-1000,-911,803,-1000,-917,1000,1000,-279,131,937,271,-1000,340,-380,1000,-873,-102,-1000,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{655,1000,-886,564,-138,1000,-59,-485,1000,1000,1000,-939,363,-1000,-884,-659,58,-1000,-665,-489,-1000,800,339,1000,1000,672,59,1000,838,806,-95,-906,1000,540,1000,57,-327,-900,52,1000,1000,-754,1000,185,-1000,474,187,-565,-1000,865,-746,-1000,-1000,1000,634,-211,455,829,620,300,-1000,1000,926,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{915,270,149,25,55,1000,-469,497,478,1000,216,103,538,-204,-102,107,686,-129,945,1000,-472,-158,318,357,812,-737,221,447,-16,895,-685,-7,393,1000,400,689,-199,-453,-83,222,475,453,-155,-298,326,97,-1000,-622,-200,176,-383,-400,-353,400,-651,-55,-57,125,-138,-1000,-434,-438,606,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{814,-318,479,611,358,699,734,1000,772,1000,34,-189,684,-507,-826,171,-284,846,940,1000,-248,-839,-156,469,341,-482,221,81,-424,731,-712,448,309,1000,1000,782,411,-1000,-549,-400,-69,83,876,158,1000,168,-786,-455,153,1000,-440,-598,-1000,1000,816,304,474,-475,886,-991,22,-59,369,-26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{1000,334,-864,-277,-305,599,-1000,1000,-382,1000,1000,-555,496,285,-1000,324,-39,-1000,-787,1000,207,788,-74,1000,1000,-717,240,565,1000,1000,-1000,-464,1000,1000,559,1000,1000,-104,-123,990,1000,91,1000,1000,1000,-601,-1000,525,-1000,-325,-1000,-1000,1000,583,-146,-73,-1000,304,306,492,-1000,853,421,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{-188,913,675,14,-360,-430,-851,-1000,-1000,-801,-1000,885,582,-1000,-535,-923,875,129,1000,398,-141,612,695,61,-1000,-116,1000,133,1000,-627,426,-84,-673,-592,-770,-1000,814,-221,156,-27,882,-290,-204,-384,-326,1000,1000,222,351,400,-48,1000,596,-1000,-853,821,-1000,400,138,-766,1000,1000,-476,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{988,-457,749,-681,-361,1000,-828,650,764,780,-1000,-24,753,-499,-19,-425,715,-961,-537,1000,-289,726,129,-2,798,3,707,98,-321,381,-686,-432,-624,1000,244,549,451,50,352,400,618,-92,-824,-1000,1000,496,13,-245,446,27,-498,-400,728,346,-903,1000,112,-269,342,757,426,21,-382,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{467,47,675,-876,-758,-571,-580,-1000,-78,434,-874,466,42,-406,-458,-1000,318,1000,1000,527,430,-225,-91,752,-1000,132,587,-299,977,-688,-78,461,-794,13,630,-327,611,-270,1000,-398,105,-323,-134,318,-1000,1000,112,512,-848,-1000,822,1000,400,-1000,-669,-234,-77,-1000,-1000,-957,1000,380,703,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{-1000,466,-71,138,193,443,1000,-1000,-819,-641,-186,-388,-495,-262,-151,-450,905,-320,-123,-210,-87,1000,-78,922,320,-329,792,848,304,-793,1000,-309,-839,-697,-553,-877,535,137,118,-537,1000,214,77,-726,-378,466,756,482,336,-76,-410,1000,-221,-782,368,-903,-1000,380,324,520,1000,912,-1000,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{428,-126,-551,-986,1000,-769,176,1000,285,1000,1000,-559,636,-810,-1000,-207,792,-723,-102,992,-12,991,-191,676,554,-1000,-182,556,-579,930,-1000,-1000,52,518,-20,852,467,-111,531,1000,825,405,979,320,444,355,-305,-907,-857,13,-1000,-602,-1000,885,-257,1000,1000,-375,-30,-358,-471,426,-55,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{467,540,-281,-568,-378,599,-510,1000,-1000,1000,-1000,-181,708,-1000,-710,-1000,809,1000,1000,701,317,743,-74,794,-1000,-51,240,-226,1000,-680,41,18,-778,-502,-149,-811,1000,-892,833,335,1000,-556,1000,591,-1000,1000,667,647,-977,-1000,837,620,-289,-847,543,-73,-881,-1000,-1000,-1000,1000,1000,421,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "addMixInAnnotations(java.lang.Class,java.lang.Class):void",
            new int[]{-330,1000,-1000,559,1000,864,198,138,-1000,-756,1000,-537,124,1000,-1000,-260,-567,-663,-995,-1000,200,166,73,1000,1000,-1000,-598,502,896,1000,-542,-845,1000,928,-1000,136,-1000,-678,-1000,1000,371,631,1000,-233,1000,258,279,-637,200,1000,-597,-1000,-1000,953,1000,-786,-1000,1000,74,-325,-1000,1000,-99,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,-638,-1000,284,141,112,874,-499,1000,991,-545,-802,-910,-314,12,-226,-475,713,-872,107,200,-940,169,269,809,-300,-1000,-414,-1000,481,-365,509,-1000,-18,286,1000,841,-203,-1000,1000,-497,41,-217,793,1000,-323,-432,1000,965,-305,-1000,11,145,-4,1000,678,325,657,-1000,977,-23,1000,-799,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{904,948,18,-370,-956,-891,-978,823,948,832,-196,-214,12,248,-940,-256,-916,-717,686,912,-271,383,357,-554,500,-592,972,279,345,-663,-533,-246,329,-179,-608,375,-19,597,524,13,-171,310,815,-535,888,393,-999,-603,-733,283,318,384,-619,-470,940,7,780,62,995,799,-938,292,-690,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-493,1000,183,-441,406,275,-1000,-317,-1000,-671,763,563,-1000,-1000,-307,-754,-147,-1000,1000,1000,-1000,-666,-921,-889,686,-1000,-1000,231,-785,1000,1000,-1000,-1000,-996,-436,983,1000,1000,-654,-406,1000,-679,-569,79,1000,-493,9,-793,684,303,-1000,-970,-1000,110,51,1000,-992,-900,87,1000,-1000,-490,-233,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,-794,494,-526,655,-73,-903,565,1000,522,-942,-812,-427,-476,1000,1000,-276,1000,-1000,538,1000,660,-258,1000,866,370,-548,-1000,1000,1000,-649,1000,332,-175,-222,-678,511,-1000,-767,1000,-987,987,1000,484,515,1000,-4,1000,1000,-89,1000,-348,769,-769,1000,1000,-200,1000,-957,-1000,855,179,454,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-291,1000,997,-112,971,-413,-1000,-372,-1000,-1000,1000,287,-1000,-611,-1000,-1000,-939,-1000,1000,1000,-1000,223,-1000,-1000,584,-865,-1000,1000,1000,-71,1000,-1000,-550,-1000,-222,1000,1000,1000,-360,-1000,1000,-1000,1000,531,1000,-1000,-556,-1000,1000,1000,-1000,-1000,-1000,-482,-1000,378,-1000,-1000,1000,1000,-1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,-400,-1000,684,141,946,1000,691,907,1000,-494,341,-1000,-910,1000,23,1000,78,-1000,788,1000,-44,1000,1000,1000,-644,-201,-1000,-706,614,880,1000,-1000,-1000,-1000,1000,1000,1000,-1000,1000,-396,430,882,372,52,860,1000,771,-545,287,372,-616,1000,58,1000,1000,-570,374,-1000,-1000,633,428,-1000,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,-218,-1000,-141,-140,814,874,-326,1000,1000,-738,-74,-974,-251,12,-303,132,324,-872,572,240,-271,226,529,809,-517,-677,-989,119,590,332,776,-1000,-348,-680,222,1000,281,-1000,1000,-74,639,209,353,1000,681,-150,921,162,-81,223,-529,419,218,1000,1000,132,337,-1000,200,522,818,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,-400,-45,29,863,-1000,-350,1000,-266,-331,1000,-496,-1000,-1000,1000,372,-465,413,-965,-328,936,-1000,1000,50,1000,467,-1000,915,-1000,900,-636,-103,-1000,38,-175,-59,882,1000,214,253,-1000,-851,1000,-466,-632,-1000,659,1000,95,1000,363,-201,-38,873,-418,-203,-1000,1000,300,388,-1000,321,679,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{-1000,-638,-1000,218,-1000,74,874,-716,935,1000,-945,-216,-910,-314,12,-290,-50,591,-872,159,200,-1000,1000,269,809,-622,-774,-1000,-794,300,696,696,-1000,-18,-292,1000,841,-203,-1000,1000,-307,739,-300,353,1000,-301,-432,1000,437,-779,280,40,145,587,1000,903,626,241,-1000,434,-210,43,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType):boolean",
            new int[]{0,1000,494,-526,234,414,-1000,-1000,1000,-1000,-60,468,400,167,-795,-1000,-803,-1000,1000,1000,824,485,524,-1000,638,-561,-159,-616,-653,378,-232,-1000,-611,-703,-934,-116,-400,-400,-1000,-1000,1000,73,-736,1000,1000,-1000,-1000,-1000,706,925,-560,-480,-1000,-1000,-173,-442,-449,-1000,1000,-1000,356,-1000,-1000,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-1000,-1000,1000,369,1000,413,575,-669,-1000,899,-808,-44,1000,-364,166,33,-1000,-925,102,1000,99,973,1000,1000,-1000,135,-1000,765,699,-848,784,1000,1000,233,-638,-666,-99,-703,157,373,1000,879,798,66,-817,-251,30,1000,756,-148,971,-1000,245,-1000,128,186,1000,1000,1000,277,-257,1000,-6,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-1000,-1000,1000,-321,445,140,445,-838,-841,596,243,1000,76,47,91,357,-421,-718,-448,-294,17,363,-376,-37,662,686,795,-220,250,339,729,999,28,-495,-758,-472,-302,99,326,-622,-637,-255,696,169,904,-802,-604,48,758,847,410,-457,589,52,546,196,-839,251,414,140,-197,1000,-871,493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-600,702,-426,-828,283,-887,623,-108,-398,330,-496,718,894,-825,-250,941,659,1000,1000,473,-990,216,-1000,1000,-768,-797,61,-1000,1000,-1000,1000,-1000,589,1000,826,1000,722,-124,-691,1000,1000,239,497,498,-510,1000,288,290,-1000,635,-712,-234,1000,81,-1000,-134,201,278,812,-373,1000,299,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-837,-875,762,804,-1000,986,-93,303,-1000,905,-215,344,-125,-900,-863,88,1000,-580,-185,43,292,-738,578,-640,-1000,-111,-1000,-705,-1000,1000,-1000,427,-49,321,-165,1000,331,-1000,-632,374,-578,369,-78,676,469,-19,696,-1000,-474,-733,781,1000,1000,1000,-346,1000,-696,-942,-988,483,5,-465,-1000,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{77,-32,-541,-591,-91,-233,1000,-1000,-123,-1000,764,-85,714,-12,931,609,-403,722,-720,712,-1000,292,-1000,641,217,1000,52,-1000,149,-991,1000,-204,243,-1000,170,-579,560,-113,-169,-1000,64,856,-356,-426,-394,523,-187,558,730,1000,-400,146,854,400,-128,-400,-76,-498,722,63,1000,535,413,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{297,-1000,60,-941,145,-1000,524,-436,400,-591,695,956,117,-1000,-52,806,138,606,416,-620,-551,427,-581,497,133,1000,-283,-607,351,-771,447,846,84,-478,413,400,-34,665,56,-137,-1000,1000,632,187,284,-867,-629,188,262,1000,-400,137,956,25,-263,-820,227,227,-593,563,1000,-282,-760,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-990,-998,637,389,-57,777,-1000,-533,-400,120,-616,522,-554,-400,-652,56,-174,-1000,346,318,759,-1000,83,-400,348,-54,688,621,-993,1000,-800,1000,-115,1000,140,399,-199,260,11,-282,-39,-1000,937,1000,377,-977,-403,-237,-1000,1000,56,-152,340,-834,367,-170,71,813,-1000,159,-600,-832,-1000,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{1000,1000,-682,369,-577,-761,115,-669,1000,-1000,409,1000,-1000,-350,-430,1000,1000,1000,187,1000,-443,255,-831,-486,739,931,-1000,-884,-668,405,-890,-1000,1000,-278,1000,1000,-99,598,157,-693,1000,-590,-1000,208,356,369,-1000,-399,-249,522,-1000,750,520,1000,-583,-1000,1000,158,-1000,-362,1000,-262,-420,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canDeserialize(com.fasterxml.jackson.databind.JavaType,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-539,1000,1000,709,-390,112,-440,-650,-252,99,-161,988,-1000,-39,-552,514,261,-1000,-7,430,46,-1000,-7,-1000,-1000,-312,-1000,130,-852,322,-1000,298,1000,505,340,1000,-944,731,619,-247,84,-190,-314,1000,724,-891,-704,-493,-1000,85,-331,-47,-395,-259,-53,-400,1000,839,-1000,-842,361,-465,-776,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-1000,1000,-1000,534,1000,17,61,566,-42,-731,-1000,-746,-399,-778,985,-1000,-478,1000,-1000,528,-984,281,279,1000,-1000,-1000,1000,-307,137,-282,272,404,-133,-412,-1000,1000,-1000,1000,-718,-217,-1000,-1000,-508,1000,606,1000,-584,485,-1000,325,343,-486,1000,1000,549,-1000,1000,1000,714,-1000,545,1000,832,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-1000,606,-426,-515,983,-1000,-548,591,643,-601,259,-1000,-1000,1000,1000,-558,195,1000,-384,-247,284,-697,-1000,1000,-508,-301,553,357,-833,1000,-188,135,683,-1000,-16,624,-378,1000,-1000,1000,-705,-349,-340,1000,730,1000,611,541,310,448,-494,-1000,1000,10,1000,174,177,1000,1000,-1000,-182,-283,-969,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{789,-899,-395,-666,950,1000,511,1000,-1000,1000,-83,-91,-66,766,700,-803,-453,-583,1000,777,1000,1000,169,-1000,1000,367,169,450,983,-135,-567,1000,-46,9,1000,-131,350,270,645,-334,-911,-513,252,-400,-690,24,-210,-1000,1000,734,-326,1000,649,108,-590,996,533,-1000,-1000,1,92,63,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-689,428,-503,-732,-34,425,1000,-1000,-345,1000,721,-288,-287,-146,-365,-1000,-337,172,736,728,906,-89,-327,-1000,-67,-1000,276,700,1000,-558,-342,408,347,252,279,-329,-53,127,287,-467,-135,-423,179,-1000,-961,-340,-140,-1000,1000,-429,-803,575,-251,1000,734,80,833,-1000,-785,42,-290,-682,-346,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-20,710,-594,409,1000,757,-48,531,-106,20,-1000,-230,-95,-778,383,-1000,487,177,-743,313,-483,801,105,329,-1000,-1000,592,-582,195,-282,529,700,-187,194,-827,1000,-746,709,-215,-169,-1000,-1000,-767,373,272,479,-862,238,-250,711,-3,97,298,1000,-203,-1000,1000,20,160,-562,470,-124,774,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-711,1000,-622,-222,809,-443,-226,-108,-505,-421,848,-327,242,169,638,-771,-426,1000,-1000,385,-733,196,-124,976,-893,-1000,-241,-115,-47,-20,357,-95,522,-105,-1000,815,-1000,1000,-547,164,-1000,-969,-57,1000,553,1000,-255,473,-236,362,96,-452,643,1000,1000,-1000,1000,928,315,-1000,767,195,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{670,138,-152,-925,-34,-204,1000,-1000,-1000,1000,456,561,31,-169,45,-858,-258,-359,968,1000,966,921,429,-1000,-20,-1000,266,700,1000,-100,106,13,393,332,279,-188,-515,100,821,-616,-135,8,-403,-1000,-566,-897,199,373,1000,-347,-615,1000,-1000,1000,2,80,815,-584,-1000,336,201,530,-1000,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{83,1000,-505,809,-133,1000,504,-570,-345,841,837,-473,46,-1000,-21,79,-266,742,-246,22,-60,-219,-327,-714,-687,-1000,134,780,131,-558,-182,408,-229,197,88,-906,-526,-140,463,72,164,47,-50,223,-834,628,-209,-1000,4,-836,-200,-363,605,1000,734,920,833,260,552,-40,179,-682,116,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{410,1000,67,-276,220,288,817,-1000,-1000,1000,1000,1000,720,-1000,341,336,-352,-13,-136,1000,-99,366,243,-836,-892,-1000,-810,274,1000,-1000,-295,1000,-274,787,-412,210,-954,353,-417,-847,-871,-761,-1000,-114,-506,-63,-269,-602,53,-990,-349,1000,-1000,1000,146,25,1000,-1000,-911,33,1000,114,-1000,749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{-92,-325,-409,-397,674,-789,272,1000,532,-76,-1000,185,117,-1000,-575,-1000,-62,-1000,-505,628,627,818,887,-402,-1000,-1000,29,-983,622,-315,985,802,589,543,-1000,1000,-478,751,229,-172,731,559,162,-617,157,-618,-742,-6,-375,528,264,1000,-1000,575,-923,-1000,1000,-738,-856,-231,362,-345,1000,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class):boolean",
            new int[]{154,880,-170,-76,859,-1000,-853,324,241,-71,-11,-864,-645,-59,1000,-718,821,1000,-412,35,-1000,158,-367,1000,-1000,-593,470,-221,-870,849,571,551,-127,-96,-579,1000,-794,767,-625,782,-1000,-1000,-1000,1000,763,1000,-210,1000,-239,942,199,-1000,1000,483,906,-91,533,1000,1000,-1000,816,1000,-1000,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{298,1000,-857,-251,1000,-342,-479,-553,400,-1000,-68,-362,-1000,-1000,361,-58,-643,-979,-1000,-340,-746,-839,20,894,1000,-649,-227,1000,1000,-411,21,-547,-265,1000,1000,-804,-474,266,343,-363,-498,-737,1000,259,1000,1000,343,-1000,440,1000,197,-542,1000,55,237,676,-1000,-877,1000,685,775,-641,-499,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{1000,354,-11,354,-555,-1000,-21,470,-1000,-1000,511,1000,934,-518,-1000,-305,-66,-1000,980,-169,1000,-660,-1000,-1000,904,-76,384,358,-1000,1000,-133,1000,1000,-1000,-822,-1000,1000,118,1000,-230,-656,-1000,-668,-1000,1000,-317,-847,1000,72,-1000,700,1000,1000,-159,930,115,1000,1000,115,-1000,1000,-238,12,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{298,1000,-1000,-391,331,71,-967,-709,733,-609,411,-1000,-1000,-1000,361,-43,-374,-214,-952,-465,-1000,-1000,598,1000,1000,-788,1000,1000,1000,-824,555,400,1000,1000,1000,-338,-932,883,55,-808,-344,-192,1000,674,817,1000,210,-1000,108,1000,-465,-566,1000,997,-369,1000,-1000,-877,1000,1000,284,-922,-412,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-298,1000,-1000,-696,710,98,-118,-141,81,-1000,1000,-418,-915,-1000,235,-250,368,-1000,-1000,422,-155,-1000,48,209,853,-866,299,843,541,-26,399,-208,654,1000,1000,-1000,-164,1000,1000,-862,-458,-685,1000,-155,843,1000,-299,-1000,-323,1000,-511,-566,1000,873,505,1000,723,-124,1000,-90,307,-1000,-651,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-966,274,855,928,-202,-984,-681,-502,-1000,213,-567,560,-99,-653,379,553,2,-1000,249,-124,-1000,-550,-136,-1000,338,731,456,902,432,270,-119,1000,-1000,-746,-138,501,-1000,-991,-600,543,758,-985,337,-807,-208,623,-41,-373,734,-321,918,389,256,693,60,890,911,679,729,-113,857,-790,764,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{13,352,-710,-661,-496,915,610,-370,-962,-1000,321,-398,-178,-1000,-412,473,46,841,1000,-1000,1000,170,-630,782,-8,-1000,148,163,1000,641,207,-1000,1000,67,1000,-36,1000,183,-150,-318,-1000,696,-320,1000,960,22,710,-43,-41,-1000,845,269,416,-18,599,-1000,-960,-755,-372,754,754,316,-1000,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{210,-212,482,274,-1000,-293,-645,-8,-1000,1000,-206,578,534,441,-686,838,789,-982,-530,615,631,-692,-13,-519,-609,1000,-978,-847,-468,-96,455,1000,-460,-54,-630,-934,-1000,127,-676,483,847,306,-7,-763,-1000,-543,-598,-297,-353,-199,-362,380,397,226,127,-450,723,1000,-677,136,102,-909,626,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{-1000,-752,509,-417,748,-394,74,-416,845,20,-1000,-314,-1000,-423,424,475,-1000,479,-326,-1000,-227,1000,86,-509,625,-1000,31,1000,1000,-1000,-1000,322,-1000,-49,-201,451,-121,-1000,-791,-999,-1000,116,621,1000,300,-154,947,-340,1000,766,1000,-183,-933,-20,17,-364,429,-1000,111,1000,-300,928,-735,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "canSerialize(java.lang.Class,java.util.concurrent.atomic.AtomicReference):boolean",
            new int[]{1000,-502,-1000,804,272,-1000,903,999,800,1000,126,-1000,-1000,130,-1000,1000,-1000,-850,181,-876,280,740,-164,-771,1000,-1000,1000,376,1000,-55,-1000,400,1000,1000,1000,-1000,1000,-529,1000,-1000,-1000,1000,1000,1000,1000,1000,236,-70,923,1000,1000,712,-488,1000,1000,-1000,-1000,-799,64,887,290,-367,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-14,1000,190,69,-196,-1000,-138,335,108,-106,1000,-24,1000,-1000,-203,-1000,-259,1000,-14,-492,285,624,-622,459,-1000,-938,-1000,349,1000,1000,1000,-435,-119,200,-430,-122,124,7,-1000,471,672,923,1000,188,500,53,-85,176,1000,1000,157,-1000,529,-1000,-1000,994,1000,-1000,688,317,-99,1000,-233,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-527,396,174,1000,-354,-352,-543,-541,610,-497,-1000,911,-527,304,-367,-32,413,836,189,-508,407,-229,-255,703,-505,190,-216,479,-418,-39,918,267,60,-142,-524,-746,-22,564,-1000,1000,233,447,889,-1000,-922,345,-147,47,913,1000,-422,-403,-325,662,-842,537,887,-782,-842,-14,1000,770,-66,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,560,-11,-65,219,321,-438,920,46,-666,355,-239,609,247,3,-875,-485,595,-383,-973,-1000,-184,-754,-388,-352,-410,1000,465,-492,-749,662,-593,-697,-1000,-519,-368,176,26,664,1000,-1000,62,201,246,-591,-355,1000,-1000,-948,233,1000,351,779,-726,-1000,961,1000,891,-6,-1000,238,-427,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-88,1000,599,223,332,-1000,-18,-59,350,-113,1,-140,524,41,-840,-924,500,1000,1000,-355,967,750,-1000,337,-1000,-799,-272,611,-266,946,49,179,-12,-1000,258,639,334,-199,-1000,1000,-52,-523,53,-127,212,54,-579,-547,451,-701,713,-1000,819,-1000,-268,-443,1000,-769,1000,239,-668,1000,200,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-824,-581,311,-797,-287,811,370,650,1000,873,658,-324,746,-850,-1000,433,-397,-1000,-1000,140,951,-1000,-265,-170,-1000,1000,1000,-203,357,531,270,429,-1000,-872,-1000,-865,50,-395,1000,-57,-915,251,1000,-1000,-705,1000,-528,-178,1000,-812,867,-597,-1000,-131,824,-149,414,1000,-1000,935,-1000,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-820,1000,664,219,475,-360,-492,117,-8,617,-66,-280,369,-1000,-1000,-143,-687,851,1000,-51,-1000,-401,590,-267,-447,-415,-1000,814,1000,7,651,330,-196,412,-1000,-935,-445,233,56,-396,1000,-538,-331,164,333,1000,-772,1000,701,-415,926,-1000,817,-34,-336,624,1000,-729,830,241,17,751,-886,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-944,-1000,494,1000,-818,-739,574,11,657,521,-924,1000,-1000,-218,-725,-1000,1000,-24,-274,356,-1000,719,-223,-1000,-785,-234,496,1000,1000,243,1000,270,1000,-1000,951,150,-574,975,-21,1000,-428,-509,-319,929,16,-1000,-1000,904,400,691,367,1000,-316,1,-417,167,1000,944,490,-980,1000,-459,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{481,-89,-501,773,324,-954,-1000,101,-420,110,-194,-1000,1000,-1000,385,-534,730,1000,-1000,153,358,-685,-651,741,-1000,-862,-3,314,260,99,676,696,697,903,-867,704,-707,-400,-1000,-403,989,-384,-897,-415,257,808,-977,-178,1000,1000,662,-366,1000,-1000,583,475,1000,87,887,-216,-289,1000,303,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-681,1000,1000,-401,544,-1000,622,571,347,325,1000,502,524,228,-414,-1000,-711,773,305,-445,105,457,-434,-123,-693,-225,-359,1000,508,849,-82,272,-602,-910,-247,-71,-457,635,-1000,686,-57,-319,-1000,-169,1000,-743,-496,-81,8,-930,480,-1000,563,-815,-1000,-269,809,-1000,715,984,-827,867,-394,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "clearProblemHandlers():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-50,-70,67,252,575,589,-953,-262,360,206,-1000,88,-22,-97,280,516,-553,-249,235,-528,-372,-1000,728,-236,-133,-176,91,314,1000,-802,338,920,-722,1000,-1000,366,-81,-836,1000,-516,1000,-1000,-927,-224,27,-244,-22,888,-164,251,154,668,80,782,556,-170,-255,652,1000,-549,-246,-400,-837,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{771,-809,547,-271,451,867,-1000,-905,59,93,1000,1000,-306,-238,-477,690,109,-704,364,189,-496,400,992,-229,489,1000,-965,952,-137,642,-1000,228,498,397,283,101,1000,-1000,-218,-1000,-220,482,-271,665,296,-792,685,-462,352,323,-109,-868,998,374,-419,-761,213,-550,1000,-1000,-1000,639,220,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{-861,120,-821,-704,245,55,218,-333,-903,922,-598,-483,-373,-493,-262,809,-156,-1000,560,-1000,1000,-237,-878,-202,236,310,-374,506,-271,-309,-929,434,469,88,-982,-37,300,-439,411,-59,-464,-543,1000,65,-237,184,554,121,-682,-649,-480,69,331,935,-1000,603,-535,-373,-132,-635,-136,-237,400,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{779,-1000,1000,379,-615,1000,-690,-1000,853,-1000,135,1000,-1000,1000,-447,-387,-652,998,-1000,1000,-1000,1000,1000,513,637,1000,984,919,-126,763,123,-903,-1000,-643,-587,-599,1000,-849,576,-1000,1000,1000,25,269,34,-472,1000,-1000,-1000,1000,-574,-1000,754,562,1000,-605,702,-1000,479,307,-570,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{-1000,1000,-572,-81,-979,97,1000,945,-252,-149,-674,-1000,-473,609,1000,-183,120,-54,681,-885,520,-39,-1000,710,157,-729,-98,436,-128,457,-39,882,678,-228,-1000,481,-101,-1000,-1000,298,496,-1000,1000,-383,641,-647,729,-1000,958,-1000,-1000,296,-1000,-1000,-1000,1000,-265,-223,-552,966,565,-209,-2,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{-499,1000,432,19,72,313,-1000,-280,59,-53,747,-833,1000,1000,63,-722,574,263,-369,189,-1000,1000,992,-1000,489,1000,435,-270,1000,705,-375,-242,-1000,1000,456,101,1000,521,1000,-1000,-1000,1000,-406,1000,235,-269,-786,-448,-1000,1000,-950,-1000,1000,909,-113,-718,-874,-803,503,-364,229,1000,220,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{-419,394,106,-457,673,926,-137,-1000,552,242,-396,235,464,1000,923,-46,-1000,550,-562,-1000,-93,965,453,-635,-472,1000,435,960,509,-241,-203,1000,-357,-17,-1000,807,1000,-1000,134,-1000,-1000,0,870,848,157,-1000,-475,-1000,-556,1000,-1000,-316,1000,-183,-1000,153,-230,-426,-3,400,-1000,1000,421,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{1000,-1000,157,-491,613,539,-975,61,-72,680,1000,1000,-1000,-1000,-1000,1000,-1000,-1000,1000,1000,213,-838,534,932,1000,-531,-1000,1000,-1000,910,-1000,-1000,1000,-460,827,-1000,-214,-926,-1000,322,1000,-44,-865,295,200,-15,-375,598,1000,-1000,1000,-787,577,465,-95,-1000,1000,-648,1000,-1000,-1000,-1000,822,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{-60,-845,839,-731,319,-78,-941,-750,-673,740,-268,-208,-539,-149,-1000,-707,-260,-699,-528,849,775,581,-225,-358,360,662,-117,-566,1000,-779,1000,-326,279,530,71,-646,711,-326,236,489,-551,-526,-1000,68,-247,-18,497,574,-182,-363,-331,77,1000,233,-263,-815,1000,-269,1000,-1000,265,31,-737,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{-532,221,54,649,-866,-733,627,-1000,-179,-541,-1000,-394,828,1000,903,107,-618,-204,710,566,-257,-38,-382,-721,6,689,289,-914,276,-176,605,-440,-357,-540,-1000,647,1000,-1000,-1000,1000,-55,-924,-297,-1000,320,-583,522,-448,315,-1000,-1000,80,-1000,-1000,744,860,-440,-16,-577,-221,1000,-160,-709,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{864,863,-536,-271,622,428,-1000,434,-269,1000,1000,-154,-61,338,-477,-850,-955,18,640,256,-122,400,619,-1000,-965,508,-159,-146,880,791,-180,-369,1000,1000,846,904,1000,-32,482,-637,-757,167,-1000,828,78,604,259,130,-251,-158,407,-1000,187,709,302,-792,221,-377,769,-239,-44,-378,-385,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.cfg.MutableConfigOverride", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configOverride(java.lang.Class):com.fasterxml.jackson.databind.cfg.MutableConfigOverride",
            new int[]{539,1000,-1000,-987,78,170,-630,379,-81,873,-287,307,477,1000,1000,-183,-376,1000,298,-82,348,349,-302,-42,218,1000,167,204,-155,-389,-758,464,-1000,-674,-102,-288,143,1000,505,891,426,-931,1000,428,698,664,-221,-397,-944,-805,-453,-26,1000,-304,-109,900,-389,-981,-304,1000,-614,351,-145,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-32,-584,662,169,-638,-368,-107,795,-1000,-126,-418,1000,-47,-106,746,72,-930,700,438,-612,382,-173,77,175,277,-285,1000,432,122,-1000,-549,374,-757,720,1000,262,-734,-284,1000,-522,385,351,761,209,-738,646,205,-68,-237,-1000,-26,722,-68,-213,-1000,625,616,624,655,870,672,576,-72,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{349,-915,-796,-72,989,-202,-225,736,-784,250,303,653,-392,472,174,-74,7,468,-972,-38,215,597,-696,416,-232,200,-259,-729,-889,-41,-929,513,-908,16,-425,-562,427,869,-381,748,-460,-332,885,-814,19,715,-411,654,-182,-358,-344,-464,-703,89,-417,-787,201,-504,-513,-308,759,-949,715,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-424,-955,541,979,-1000,399,1000,630,706,-755,-1000,-955,633,297,-150,594,-1000,-906,1000,-803,-702,-660,-706,-1000,419,857,295,32,442,-705,115,-701,206,-777,18,-247,-929,-886,-509,-1000,1000,-1000,471,-146,978,-682,-483,48,-575,-979,1000,887,-315,-249,-345,-589,890,-1000,-325,177,-268,-1000,-281,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-54,-955,144,489,-772,487,1000,679,1000,-185,-684,371,1000,551,343,1000,-917,-906,694,-481,568,-366,-1000,-1000,1000,-160,1000,-79,1000,-1000,156,23,206,-1000,130,-59,-867,-886,-254,-1000,-46,-1000,-407,-298,1000,-843,-1000,-178,403,-929,1000,888,-1000,-266,-1000,-589,890,141,-325,103,-152,-323,-64,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{147,-400,680,-426,-863,-141,-1000,-1000,-1000,-731,-288,1000,-367,-1000,407,46,-994,1000,-664,-913,1000,-503,1000,67,-70,-228,-1000,-28,-161,-283,-940,480,1000,1000,1000,56,-352,-1000,671,-1000,-1000,1000,964,809,-1000,-297,-323,1000,-1000,-856,417,-701,-595,-123,-505,769,-1000,1000,-629,1000,802,1000,-545,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,91,-396,-168,-80,-795,-701,2,-576,347,1000,-40,-1000,-141,78,-974,1000,-369,-571,400,-606,1000,-116,-553,290,-1000,726,278,5,-952,570,1000,770,340,-322,-67,-1000,479,-1000,-1000,1000,1000,1000,-75,-124,-221,864,-158,-890,407,-776,-1000,384,-452,-567,-890,1000,-1000,634,175,1000,-364,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-163,757,235,-491,806,-425,-107,-221,-1000,486,645,341,-356,-225,-75,-877,-622,746,-196,-604,-1000,-343,26,614,985,-123,1000,-516,-336,-273,235,815,247,-16,-83,509,-734,-810,814,867,385,740,429,529,-732,1000,-46,195,648,-823,384,-661,-68,219,-141,625,144,1000,-460,250,443,118,64,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,969,824,-1000,1000,905,-139,333,-350,-179,360,1000,-1000,1000,716,-723,1000,-622,-1000,1000,-1000,536,-1000,1000,105,1000,459,783,898,121,-765,545,-722,-632,367,-898,-220,-190,515,-838,243,-127,646,342,-345,1000,301,224,-1000,1000,-1000,801,-333,-1000,509,-963,-470,-73,-102,-109,501,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{750,182,-813,137,946,-1000,-768,186,-524,-1000,87,206,-1000,110,-531,-10,-916,-222,1000,205,-149,518,282,776,-756,743,-1000,502,-444,-425,-1000,-327,1000,865,980,-851,182,-553,509,-127,-318,114,816,251,-241,-1000,-1000,-405,-912,-347,-282,-1000,-840,946,865,450,-1000,-743,-235,734,-57,1000,57,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonGenerator$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-89,-768,-422,479,-404,-1000,-1000,1000,54,-1000,-675,-69,-1000,471,-753,74,-944,-174,921,-352,61,-99,401,717,-1000,533,-1000,99,-75,-848,-1000,-771,719,1000,1000,-632,-18,-1000,632,-1000,-666,-202,1000,-1000,-229,-742,-846,1000,-1000,-809,1000,-350,-843,671,1000,167,-332,-740,78,1000,75,467,404,-1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-839,-593,-311,242,544,-319,-778,491,-1000,-410,-1000,-625,454,-771,-4,1000,439,-1000,-94,-107,453,223,556,-1000,138,-300,872,789,94,-55,1000,664,-293,1000,-1000,-637,177,-881,-276,-1000,-696,34,-1000,334,542,-1000,687,1000,-928,579,498,-482,-77,526,327,-514,-804,-18,-466,-369,963,-871,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-10,-726,164,-438,111,360,-196,-90,747,8,-1000,-369,202,875,-488,-1000,945,569,-423,167,912,-65,-152,183,973,-46,274,587,507,-666,-341,826,806,706,1000,-698,-370,748,63,-196,-1000,249,370,-446,-58,-109,-1000,-1000,1000,-1000,-150,905,-355,-295,-205,66,-985,949,-1000,103,128,1000,-712,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{213,415,400,124,309,-246,-832,1000,-460,440,-729,-167,356,872,-940,-1000,698,-696,-814,329,226,1000,18,1000,683,-495,-179,-402,51,-286,1000,-545,-441,168,-499,-79,198,1000,69,-501,408,682,1000,-1000,-301,-584,-240,-63,-668,-1000,-187,802,-1000,1000,-1000,218,-400,1000,-961,185,-1000,1000,-607,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-347,101,290,194,-23,-47,-218,-181,-665,1000,-1000,430,1000,1000,-370,-959,498,-473,-205,290,797,112,-700,207,-282,-604,162,3,-588,-302,-19,-975,-174,-225,313,145,-163,941,825,-750,449,1000,1000,-475,-583,-530,-541,421,-1000,-1000,-82,334,-1000,1000,-313,1000,-587,1000,-1000,-1000,929,614,-717,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,1000,109,601,-561,724,1000,-1000,580,-646,-751,1000,1000,-700,-1000,964,-1000,-980,775,-193,190,-892,642,-798,-1000,531,-918,-655,-785,135,448,848,768,-1000,1000,307,-59,1000,-679,-479,1000,1000,-1000,-1000,-1000,1000,-835,-740,-641,-264,-277,-1000,1000,-95,-569,1000,535,-1000,848,-47,-489,-1000,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-547,578,318,-516,506,-647,-804,-269,765,-950,745,177,-1000,-1000,-455,-30,-180,-126,264,166,987,327,1000,819,1000,1000,-11,-682,791,-1000,-146,-697,-1000,92,300,-81,-173,-1000,-275,921,1000,-999,-505,-455,1000,-68,343,-514,-122,1000,-1000,-1000,175,-270,-978,-412,-1000,777,304,485,15,564,549,803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{939,-842,220,-531,-605,299,828,-508,847,972,-875,166,389,697,706,-712,-161,657,-963,-790,-495,-783,851,422,782,-72,707,349,663,-494,26,801,60,-353,606,104,871,-203,614,322,174,25,363,140,-402,1,-219,60,2,-249,-552,305,-134,336,278,-473,504,-101,-486,-193,136,610,552,749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-554,-848,-259,234,379,-275,994,-687,-199,828,-644,-147,171,960,8,-269,-55,-153,296,719,226,-436,-970,-698,-379,-224,-58,665,-570,977,-696,-359,992,386,1000,-79,-552,916,694,-235,-433,1000,687,-250,-6,203,-727,1000,1000,-1000,490,332,262,-169,227,1000,648,121,-1000,-941,171,400,-386,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,1000,-891,-1000,0,879,-122,-1000,972,1000,1000,1000,-866,479,642,-382,-1000,-1000,-379,-1000,-351,-1000,413,-1000,-1000,943,-1000,-205,812,-1000,446,-44,-630,-1000,1000,1000,30,1000,-1000,12,1000,616,1000,-1000,-1000,1000,-804,-1000,791,506,-1000,-418,1000,1000,492,1000,1000,942,638,405,-1000,26,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.core.JsonParser$Feature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-280,-400,-675,134,567,1000,185,893,-1000,-991,154,-513,-179,79,568,-1000,799,-828,-237,-1000,-1000,1000,-174,-1000,943,-491,-1000,-1000,106,396,312,541,705,-1000,400,-333,103,-499,-400,-529,-109,-933,-961,-1000,-385,400,76,894,400,408,1000,1000,209,246,274,1000,-58,-911,326,1000,1000,1000,-1000,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{551,1000,-1000,519,-510,-285,1000,-48,318,1000,703,752,1000,1000,894,-1000,-1000,-555,-1000,273,1000,1000,120,1000,-367,-298,-1000,1000,562,372,127,-1000,1000,68,1000,1000,99,-1000,866,869,762,-237,321,1000,283,-332,1000,407,280,1000,-1000,-540,305,-913,1000,-754,817,349,-1000,-1000,-1000,1000,666,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-294,39,-61,-359,0,72,-7,-972,-505,649,280,655,217,91,-1000,-473,226,-278,820,749,-851,-582,559,-279,-104,-1000,-80,905,-1000,958,539,1000,665,1000,-1000,454,1000,-236,-1000,680,-920,744,-222,-279,-624,-863,415,323,-78,-743,-765,-239,565,812,355,-79,-525,-477,862,-431,-241,4,-910,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{67,-1000,-181,-291,307,-228,-76,-683,128,-197,-400,147,1000,1000,27,-947,195,715,-1000,1000,562,500,190,347,118,477,-147,1000,835,-1000,356,-982,1000,207,1000,-109,-752,367,872,-480,659,-172,-308,193,298,58,329,-237,441,1000,-91,-73,413,350,1000,793,382,566,-939,-300,60,-228,1000,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-187,-1000,-1000,-841,-587,-1000,58,1000,128,570,456,1000,1000,-1000,-144,-240,-436,-658,-1000,1000,1000,-713,843,979,17,-97,-1000,1000,1000,-1000,-105,-982,892,296,1000,-109,1000,-610,1000,1000,1000,-18,1000,998,827,-1000,46,-237,661,-182,-178,-1000,-221,800,244,-1000,425,-1000,886,218,-834,-427,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{798,-1000,119,859,-289,163,1000,-677,330,1000,-910,-1000,-959,1000,1000,958,970,524,1000,510,944,-104,-570,-590,31,-359,65,89,-671,1000,242,304,175,805,-1000,580,-344,1000,-1000,-1000,-1000,-180,-670,266,436,1000,729,-104,691,75,-1000,1000,1000,-718,1000,-181,-1000,648,-973,-1000,1000,269,-1000,538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{130,-808,-314,29,-586,-136,87,-473,-53,268,389,-450,299,582,-62,-518,-314,693,-301,778,-289,682,262,-681,-197,779,102,930,683,-328,-532,-394,1000,735,-144,330,-479,987,478,-835,237,370,-475,-379,-529,141,-74,634,313,970,-148,181,230,135,608,859,-771,630,-432,-339,-67,-445,480,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{63,-1000,-414,494,108,-848,-550,836,-55,13,725,263,-527,-1000,-705,-335,-638,452,-376,1000,-523,-486,780,-711,-199,243,-355,471,1000,-120,-733,41,-111,514,-293,-1000,-627,503,1000,-927,1000,366,147,-637,-407,874,-241,332,-332,239,772,-277,225,368,26,190,792,-689,625,1000,-736,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-10,-185,-667,-456,811,-739,-918,336,330,-719,-238,-355,-596,-199,535,548,876,-193,140,-510,107,341,-789,-799,-650,122,699,-131,861,-318,228,-463,-404,300,-362,759,-306,210,644,-619,-474,559,-405,-986,51,-564,649,-801,445,-101,-686,587,-463,-590,-713,564,-559,-702,800,-372,984,5,237,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{727,-556,-1000,-456,217,-888,285,-861,322,888,412,64,-868,-808,489,-757,460,-1000,570,928,-485,-1000,-423,-486,830,-1000,-338,1000,296,-114,-178,798,-790,660,400,-1000,5,-40,400,100,693,367,1000,1000,544,-172,437,8,-350,-1000,470,-228,-257,-433,759,-1000,-447,-898,179,598,-391,-128,572,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.DeserializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{431,-52,-1000,519,-322,-285,842,934,446,730,542,315,400,415,557,-1000,-978,163,-794,-39,1000,929,-406,385,-957,390,-1000,639,471,-400,6,-1000,1000,-108,1000,133,470,-1000,866,869,542,193,-609,1000,283,200,1000,-579,280,-486,-684,-379,-103,-461,1000,-1000,788,165,-1000,-598,-1000,1000,763,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-400,-615,264,265,-90,-438,-111,-1000,1000,1000,851,-978,650,-550,-946,510,-532,1000,-1000,488,-539,-1000,-316,-310,-628,1000,1000,435,331,-1000,-600,-772,185,-354,1000,-336,-1000,1000,552,-151,701,351,797,490,585,-663,574,-664,-648,-130,863,-764,-1000,-1000,-987,-151,493,895,90,121,-1000,-671,13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{86,-78,-251,329,1000,-1000,353,408,143,1000,1000,767,-792,308,-852,-951,185,-170,848,-1000,-249,587,-317,-1000,132,-493,1000,1000,347,-790,-664,756,516,437,154,742,-1000,-392,-16,-120,-399,258,990,-332,881,291,-832,484,-874,149,-645,548,-527,-1000,-293,71,-309,-135,1000,-384,-224,-1000,-619,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-158,130,-1000,-551,369,-512,-205,986,537,937,-675,804,-550,321,-1000,-165,100,777,-445,739,-595,1000,701,-886,859,-341,46,349,758,-1000,61,380,1000,760,1000,-937,-382,-219,-1000,-397,246,-300,1000,-1000,856,7,-772,-430,-473,574,-627,585,-432,-929,1000,-274,823,677,410,1000,459,-1000,-933,761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{752,-81,-91,232,133,-286,881,-616,44,467,774,420,1000,33,-549,417,1000,-8,-93,-273,-1000,-193,384,-1000,-985,842,234,-200,21,421,-441,1000,-532,401,593,287,284,364,-550,-402,188,593,-524,-780,-258,-865,1000,-482,473,-982,1000,278,332,68,906,-528,-290,-324,-273,518,-1000,-307,309,-723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,86,-1000,-186,301,347,221,379,647,209,214,-445,-1000,33,-427,283,926,450,1000,534,-408,792,-1000,1000,1000,175,712,830,-53,1000,662,-1000,962,885,-1000,1000,-1000,829,-7,696,1000,-320,874,62,200,-33,841,273,307,378,39,466,-835,-1000,-922,478,101,-490,-438,543,-64,-1000,-1000,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-4,-167,-463,224,-728,795,-570,768,-775,509,-795,312,-339,358,-105,696,530,-929,4,935,-561,-78,-110,391,556,291,-792,-463,334,378,284,-144,-998,610,-675,-119,-167,11,976,951,824,648,-137,-143,-390,173,-468,231,195,-594,417,261,-621,956,370,-874,-333,-80,-346,399,-151,883,-699,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-413,-49,637,419,-413,-158,237,-1000,-4,200,-230,-276,-495,49,-759,-270,519,1000,-524,529,532,809,171,982,-302,-339,-407,-837,-499,1000,-816,-1000,-166,878,474,535,-1000,-493,-198,111,-904,-688,558,1000,500,179,-580,-102,325,-260,-242,909,610,-677,285,-629,1000,356,143,244,788,-46,593,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-710,-547,224,-1000,795,-1000,768,-775,1000,1000,1000,-339,1000,67,-1000,883,-1000,1000,935,-561,-1000,-110,139,-414,-1000,968,1000,860,650,-1000,-1000,-998,694,-675,-119,224,-1000,1000,1000,809,690,-1000,1000,-742,1000,-349,231,-1000,-882,-168,1000,-979,-1000,-1000,-744,-848,645,375,400,-214,-1000,-1000,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.MapperFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-1000,-32,265,-273,-372,1000,647,-1000,-899,-421,-1000,-140,-277,1000,1000,-532,1000,1000,-669,613,-669,382,1000,348,910,172,172,-400,-1000,119,1000,237,-1000,-400,-1000,1000,-714,928,1000,-1000,423,-1000,62,142,1000,-331,474,750,75,863,-814,-1000,1000,-987,-1000,173,-925,1000,-120,-609,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-104,430,821,-542,296,-187,1000,-1000,-830,-218,-1000,231,-852,-150,-165,420,580,-792,329,1000,-1000,-1000,360,1000,-175,-259,-340,-197,-753,475,-415,-178,171,-471,350,-508,882,1000,-1000,65,-80,54,-1000,1000,-185,-1000,-1000,-1000,1000,-415,652,-831,1000,357,-332,-1000,-80,-53,467,102,254,-435,-479,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{211,381,509,67,131,323,331,-715,43,401,-395,-469,140,415,-630,-480,267,-364,-718,151,-552,-667,481,605,-129,826,-272,-885,-180,-683,-260,-430,-588,-303,-732,-423,-567,16,-807,633,-979,-620,-316,657,530,-346,261,-699,-508,556,-464,-840,283,-549,343,-701,-172,512,980,-645,489,741,372,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,1000,929,294,996,-198,345,133,406,-1000,487,1000,-464,-879,-1000,-64,466,1000,1000,-1000,672,-372,55,155,-1000,-1000,-48,597,-890,-509,1000,871,406,-1000,306,705,-1000,202,108,1000,-1000,900,656,501,-734,1000,-1000,-1000,-845,-850,536,-1000,302,51,1000,-608,927,-85,-760,-522,1000,-164,-628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{222,1000,1000,-281,-223,64,-19,1000,-400,702,-1000,-623,-1000,351,-499,429,-501,227,581,791,-1000,548,-1000,819,-831,-511,-1000,-1000,-341,-616,-871,1000,301,-246,-814,-988,268,-454,-1000,881,421,-572,348,1000,162,-859,-620,-788,120,-397,-655,320,-1000,-1000,1000,521,-949,534,1000,-1000,-930,-321,-195,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-736,-946,-156,-580,-382,-509,345,1000,702,97,-1000,805,413,925,346,-499,292,-972,-588,928,997,1000,10,-376,170,1000,-943,-568,413,-1000,-97,705,206,1000,-497,577,-278,660,1000,-698,-353,-309,-417,549,941,-518,277,259,622,691,-1000,603,-503,178,229,-475,752,-369,977,264,-610,-44,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{470,-638,1000,119,713,276,-128,191,607,175,-752,341,845,-852,-303,-956,259,113,-406,810,386,30,155,369,-543,-123,1000,1000,699,1000,54,-652,-609,442,547,644,-1000,4,-82,234,208,294,174,-471,-33,-106,-760,-1000,-890,753,-748,-49,-1000,859,705,-166,366,365,140,244,686,-94,290,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-305,810,524,-522,-131,-443,-208,559,1000,680,-64,83,367,-1000,67,-803,-292,874,-1000,-16,23,1000,1000,381,760,-922,670,191,1000,764,-268,-1000,733,1000,-1000,-7,-568,-1000,572,836,515,1000,717,-277,-737,218,-359,-982,-592,-492,-1000,480,-1000,588,1000,1000,-443,-31,-1000,550,-193,137,874,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-638,641,369,131,1000,-509,191,727,471,-1000,62,-5,79,-730,-783,401,-441,163,575,-3,8,-92,41,-977,-123,-682,729,210,315,-320,748,-528,68,-853,120,-791,54,-743,519,468,-1000,308,45,1000,-170,249,-1000,-897,26,-748,517,-1000,-182,705,334,87,609,848,-697,182,-86,290,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-550,49,-698,-617,-361,-193,-186,400,570,758,-1000,411,166,846,589,-980,852,556,1000,355,-650,278,-566,1000,-368,-530,640,-755,-1000,533,-957,-78,1000,549,880,480,-50,1000,-665,1000,607,107,400,500,-657,230,391,-1000,-400,51,-654,-1000,-400,-1000,125,-100,-1000,447,-197,-134,-650,-435,921,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "configure(com.fasterxml.jackson.databind.SerializationFeature,boolean):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{723,-239,679,394,-506,795,-509,191,608,630,-418,-536,55,171,-855,-1000,489,-587,633,-455,-3,8,356,41,-1000,244,-1000,167,37,-117,-244,1000,52,-60,-1000,-225,-82,54,-179,806,251,-1000,308,-867,1000,-170,439,-75,-897,333,-1000,399,-1000,-988,237,334,92,832,1000,-1000,-371,-481,809,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.MapType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-1000,1000,-712,-781,976,-1000,-444,1000,837,-1000,717,900,-1000,-1000,-1000,-528,151,854,-1000,-801,-1000,250,-672,-22,1000,-218,-969,1000,-1000,-477,-778,183,-1000,594,828,-183,116,357,1000,-194,-277,716,1000,-541,135,-202,-147,348,1000,-1000,37,-1000,1000,-387,-687,-69,459,1000,-909,-1000,-199,-1000,-1000,847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-797,798,-826,-991,-237,-948,-624,984,788,345,-117,-701,-529,134,-652,-481,773,253,-186,-389,-782,-191,-549,-248,477,772,658,-376,467,998,-237,156,-994,321,-296,-266,589,-902,-431,-173,825,-883,708,-389,-788,141,212,-257,-478,-483,-984,-285,-153,-723,-391,-613,-450,919,-609,-786,-188,688,108,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.SimpleType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{186,1000,-348,-921,4,-350,-1000,1000,92,-978,1000,-609,-242,-1000,-677,-474,-62,1000,-1000,-106,-485,1000,-54,-1000,1000,-688,-19,-755,-1000,-504,833,394,536,-270,1000,-457,1000,911,1000,-782,-1000,1000,1000,-598,1000,141,321,704,940,-1000,-635,-1000,960,-106,-710,1000,370,71,-683,-1000,338,-1000,-161,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.ArrayType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{1000,520,-1000,604,-986,1000,1000,-191,-1000,-130,-463,1000,1000,860,677,-419,-1000,491,361,1000,-20,-915,1000,491,-368,476,53,-1000,484,-625,584,844,-312,-1000,305,1000,-665,1000,225,912,-1000,498,-1000,1000,1000,-1000,-1000,1000,1000,1000,993,59,-766,1000,-17,-1000,420,-1000,-106,961,140,-427,936,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.ArrayType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{793,-689,-638,243,-96,359,338,-508,-931,830,444,-530,1000,914,1000,287,-1000,890,-428,927,-638,710,1000,-1000,544,-1000,542,-1000,335,-1000,1000,686,48,-1000,936,1000,-1000,424,-214,674,-662,-649,-312,1000,872,-1000,1000,1000,75,1000,-1000,1000,-939,-400,-892,735,-68,-932,1000,456,-1000,-302,-356,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.MapType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{638,-1000,-1000,498,-947,-41,983,761,-1000,51,-1000,-609,1000,8,562,-533,-1000,571,-355,1000,-231,-1000,1000,651,338,887,-601,-1000,361,-974,1000,1000,701,-1000,547,909,-1000,869,1000,1000,-1000,-1000,-1000,1000,1000,-1000,750,975,1000,1000,-423,1000,-1000,1000,162,-1000,-1000,-1000,420,1000,-1000,-1000,1000,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.MapType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-456,199,-588,-991,653,102,731,1000,-193,-532,-180,1000,-538,-1000,-4,-12,-159,-256,-1000,-546,-782,-829,-906,980,781,855,658,313,-1000,-976,-559,381,242,321,132,167,878,784,1000,-378,678,-866,411,1000,-37,141,-300,-382,715,-483,-359,-864,139,-1000,207,-838,-1000,-528,-1000,872,-1000,-786,-1000,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.CollectionType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{873,28,-816,-923,-338,937,363,683,-849,-430,621,513,534,-587,806,-916,-483,-112,630,685,-110,364,419,33,-866,894,136,705,-674,-811,-104,-395,888,-729,413,-246,-684,-60,-702,-365,-86,776,-86,968,791,-301,-341,326,-125,-670,-67,-668,-434,-667,813,-712,784,723,-478,-950,627,-257,-877,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.ArrayType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-610,1000,366,119,-329,1000,-1000,155,155,-346,115,2,95,75,182,-135,-803,909,-901,260,-1000,250,138,-1000,727,-1000,535,-1000,-339,194,713,41,-718,-919,1000,-563,-698,-211,-4,10,-1000,-201,-983,-438,135,-64,1000,548,132,1000,-1000,-1000,74,1000,-1000,666,27,19,1000,-633,528,-1000,557,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.type.CollectionType", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "constructType(java.lang.reflect.Type):com.fasterxml.jackson.databind.JavaType",
            new int[]{-570,77,-723,-307,-406,1000,-251,389,-480,-325,585,378,1000,-57,1000,-510,-552,451,-372,1000,-1000,1000,1000,-860,919,-691,-1000,-586,5,-814,209,1000,424,-1000,913,788,-691,582,1000,30,-1000,586,517,877,1000,-226,-239,690,994,-317,-541,-1000,-1000,1000,-711,337,-1000,-1000,-53,-415,-93,-891,1000,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{77,-638,741,-526,482,-219,446,-553,548,-1000,-878,-895,-296,114,-1000,-432,-354,-195,1000,-148,-799,761,-644,33,-400,-979,902,567,356,-781,-431,39,221,874,-154,31,-750,607,614,-272,-1000,-307,-676,862,446,-152,-1000,-370,798,-984,514,956,-876,-183,-322,57,-184,-887,-727,-37,-358,-64,466,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-727,-699,774,-347,590,717,-393,429,434,1000,-761,742,-1000,1000,891,-685,-648,603,955,-58,1000,448,-42,104,856,830,1000,152,-176,-1000,-892,493,1000,1000,674,-867,-21,821,582,530,-1000,-1000,-1000,1000,-282,596,-1000,-1000,-490,-1000,-210,1000,-1000,-156,-322,564,1000,-1000,-100,722,714,-1000,1000,-218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-893,-1000,1000,954,1000,-47,-603,702,699,109,116,-327,-1000,382,-1000,-1000,-951,-234,439,710,-468,708,59,32,-544,421,1000,712,-469,-1000,1000,353,-1000,1000,1000,-868,-1000,1000,196,-741,-1000,-608,-1000,1000,155,-127,-1000,-764,309,-1000,-394,1000,-1000,-643,-425,225,502,-1000,-393,471,-579,-448,459,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-1000,-302,63,954,991,-753,-571,479,-638,1000,835,1000,-866,382,-1000,-49,414,643,439,710,951,-167,1000,1000,1000,703,178,-525,-469,-1000,1000,486,-1000,1000,1000,-1000,-1000,-1000,28,901,-182,-63,10,1000,254,1000,-721,526,-427,-343,-477,1000,-635,-416,-1000,858,901,-668,1000,-355,-1000,-440,246,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-50,-1000,804,39,1000,-158,-664,466,-227,979,-1000,1000,-1000,-599,-517,-1000,1000,790,812,1000,908,-71,571,587,1000,1000,781,578,-600,716,585,1000,-14,1000,988,-144,-507,-1000,894,464,-770,100,-339,1000,1000,1000,-972,254,52,-743,189,1000,-1000,-828,-747,-293,-1000,-1000,-5,-1000,-461,-1000,-94,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-68,-1000,862,189,932,1000,-549,995,422,-218,-196,-969,-597,-108,366,1000,-522,38,415,314,690,477,863,-1000,-50,613,1000,476,-147,729,-1000,-383,1000,710,346,15,1000,1000,173,-1000,-839,9,-580,853,6,-103,-590,-1000,0,648,-707,-675,1000,919,1000,-366,1000,-314,-452,-647,147,-603,-245,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-1000,1000,-1000,-206,1000,-257,-850,574,-1000,582,513,47,1000,-784,1000,1000,1000,-693,-1000,-1000,855,-1000,1000,-814,-665,-1000,-1000,-434,-371,923,1000,-1000,1000,-89,-920,-211,750,1000,-28,1000,1000,-1000,1000,-754,-681,-1000,1000,-1000,-1000,607,8,-766,1000,-689,445,1000,964,1000,1000,77,817,1000,516,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-976,-766,217,-21,371,670,226,1000,382,31,-832,-1000,589,-286,963,-235,-168,-420,109,-1000,527,336,-564,-700,-116,234,407,1000,-24,1000,-185,-1000,1000,718,196,727,-224,1000,651,-323,81,-932,-482,571,-250,-943,16,-1000,-849,564,11,-1000,175,-74,1000,-595,77,573,397,-406,1000,114,766,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{470,1000,129,-61,207,486,-303,-191,489,100,678,89,-304,-1000,-525,-865,893,337,-496,1000,575,1000,1000,-937,-35,859,-1000,740,-1000,-853,32,-437,-704,1000,765,-1000,1000,1000,-1000,1000,-1000,-528,-1000,1000,-1000,1000,-767,-718,539,1000,-672,821,-1000,490,-59,972,263,-78,279,1000,-769,-893,-306,945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.core.type.TypeReference):java.lang.Object",
            new int[]{-68,-1000,1000,-337,852,902,-331,596,1000,560,-196,-86,-709,27,-97,-1000,-1000,185,1000,1000,754,1000,-779,-757,266,1000,1000,781,321,-169,-1000,188,614,922,1000,-170,691,471,173,-297,-1000,203,-1000,853,278,-100,-1000,-542,664,177,-609,-53,-1000,991,241,-1000,571,-1000,-1000,-647,-518,-1000,-243,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-1000,412,-997,-141,658,920,463,203,-1000,-665,-773,81,-1000,408,613,-91,-1000,425,478,-414,-691,1000,-627,1000,-631,155,1000,-772,943,1000,114,-1000,561,-541,-520,165,-1000,512,1000,-901,-1000,698,284,-612,-418,689,300,868,1000,-618,1000,-1000,516,-368,1000,1000,1000,-1000,1000,1000,-607,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{765,168,-616,-250,-948,554,355,-548,886,-1000,-1000,-1000,-919,-768,-298,64,-969,956,-45,-192,1000,-920,-411,300,-278,113,-1000,821,525,-1000,1000,3,701,704,1000,394,-1000,-1000,-1000,1000,-857,1000,-128,-45,971,-1000,917,-1000,649,295,-1000,53,-1000,-663,-919,198,-338,1000,-11,-1000,-1000,-1000,-1000,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{800,1000,-25,-411,738,786,-21,253,-348,-199,-706,139,-1000,880,482,561,-1000,-139,339,895,-158,1000,-104,1000,-417,-866,1000,-1000,1000,1000,900,-1000,907,-1000,-1000,535,-911,1000,1000,-1000,-717,1000,454,148,15,1000,404,1000,805,-1000,-871,-1000,1000,89,1000,738,1000,-1000,-861,-286,-252,-1000,1000,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{1000,-1000,679,-716,605,1000,-9,-818,1000,465,1000,-1000,-328,-300,-839,-112,-1000,-1000,-931,-940,684,-388,1000,1000,-1000,-333,-80,687,-899,-1000,1000,115,316,600,-52,-632,-590,-1000,-1000,914,-1000,1000,-1000,64,1000,-259,-373,-130,476,217,1000,-1000,-658,1000,-919,-578,1000,1000,-1000,1000,-1000,-444,843,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{1000,995,-1000,219,-884,888,455,-680,149,-661,-939,1000,-143,-400,-851,1000,145,338,-712,-1000,778,-1000,-1000,-7,-289,647,-1000,393,-268,-1000,1000,558,546,1000,1000,126,-198,-263,-1000,1000,-343,1000,675,-59,312,-1000,89,530,403,806,-1000,110,-1000,-1000,-1000,-24,-45,854,-1000,-1000,-1000,-290,-1000,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{954,-1000,-932,688,-158,1000,-614,1000,376,471,577,-242,362,-1000,41,889,400,-1000,-368,400,-1000,-1000,-118,728,-361,-536,-1000,384,-657,-60,852,-98,-720,1000,652,837,-1000,261,-303,210,-539,1000,171,-725,146,370,-1000,-1000,484,1000,-110,-1000,-439,1000,-193,-610,1000,182,-1000,1000,-1000,-96,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{1000,292,-461,789,1000,-508,-1000,-1000,719,127,1000,-523,-178,902,-1000,485,518,359,-1000,668,-488,-1000,-939,493,477,40,855,1000,430,-733,-1000,500,910,-116,-864,1000,764,-701,-353,-815,293,1000,-1000,-126,-53,-1000,166,-430,-1000,-1000,-55,-1000,1000,1000,1000,-1000,894,-159,-1000,601,240,506,1000,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-533,-271,-15,913,-177,208,122,375,217,429,678,173,-73,1000,-820,-1000,-724,-474,-470,492,193,-1000,195,894,-261,-933,646,-842,-425,54,487,-204,446,-4,-218,-737,-740,-52,595,-733,-963,5,301,-400,412,219,-297,405,23,-1000,1000,-362,645,1000,1000,-507,639,-31,-72,742,-101,-391,969,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{1000,686,-873,-551,-417,-838,-676,-1000,-1000,-186,-572,-795,745,-502,-1000,371,317,256,-1000,-141,809,-354,-1000,311,-351,-367,-1000,1000,-885,-390,1000,1000,1000,1000,1000,394,-90,1000,-1000,61,-399,1000,357,-116,1000,-1000,607,-961,21,1000,-619,-561,215,-372,-516,-892,-447,1000,-659,-1000,-852,-1000,-1000,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,com.fasterxml.jackson.databind.JavaType):java.lang.Object",
            new int[]{-152,-1000,-171,-882,79,-333,-596,374,-480,401,-16,-1000,-415,-863,400,-1000,-1000,189,128,-1000,1000,260,1000,132,-1000,554,1000,1000,-404,-1000,97,620,-849,156,-1000,-1000,243,-1000,-619,663,426,-331,156,30,1000,-172,862,-591,-272,1000,495,863,-1000,500,-685,-562,-748,1000,400,-75,923,6,357,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mg==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{682,607,-188,-627,309,-231,-691,259,-194,117,-481,-1000,-867,842,-219,399,-25,-993,-296,1000,-512,-209,-1000,1000,-690,377,916,100,-453,-160,-676,773,-452,-979,977,-758,-1000,640,858,-1000,-318,966,9,258,213,-1000,1000,500,-113,-598,-727,-1000,-1000,-1000,-411,-861,-1000,1000,1000,1000,160,-302,-411,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-907,-954,-286,819,1000,72,-921,778,98,-1000,-204,-537,-1000,402,-812,-1000,1000,-310,-193,1000,531,-1000,1000,675,-110,173,-260,-352,-621,1000,-1000,-560,675,49,1000,-842,612,1000,414,-209,1000,1000,-215,1000,-716,1000,875,-576,-719,-1000,-1000,-142,1000,1000,-189,-785,178,1000,1000,-89,-855,-487,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-88,-1000,305,944,67,-1000,39,1000,-907,-1000,-112,-237,-1000,121,-263,-863,-546,273,542,853,582,-825,1000,-156,-1000,586,-873,-554,-1000,881,-1000,227,-192,624,1000,-943,825,773,693,-43,423,-218,-578,1000,-651,-805,553,-617,-240,-1000,-184,259,164,94,88,1000,1000,-1000,151,-592,-348,139,-680,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-87,1000,-378,604,-339,572,-97,-837,761,1000,-400,-612,-1000,-961,1000,167,243,-478,-730,-881,-495,-1000,-1000,702,26,-890,1000,1000,1000,-1000,-179,1000,1000,-903,1000,81,-585,504,-77,-1000,-1000,435,389,-1000,389,-20,848,225,-1000,203,-418,599,682,-162,593,-778,-650,1000,1000,1000,1000,278,1000,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-79,-496,-374,254,290,170,-1000,1000,-145,113,-345,-466,-1000,853,-874,33,507,-971,-89,535,105,-344,225,-646,-960,1000,-1000,-1000,-1000,1000,176,215,-435,-302,1000,-774,245,194,785,148,856,828,-513,1000,350,1000,815,-968,1000,-488,-514,-261,-1000,654,-238,143,400,970,356,-645,-929,493,-343,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-28,1000,63,-276,-847,170,-587,158,-1000,1000,-1000,-3,-575,-142,147,1000,-1000,434,-515,-1000,-696,-380,510,-135,-337,853,-889,-1000,196,496,1000,899,-743,-804,766,1000,367,-875,-1000,1000,36,-1000,-861,-61,1000,1000,1000,-596,212,1000,455,-1000,506,727,1000,817,1000,418,-904,-361,-630,971,1000,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-1000,1000,782,764,-1000,1000,419,-1000,37,1000,-1000,-951,-44,-990,738,256,-906,-1000,-1000,-1000,614,-803,-1000,270,1000,-1000,1000,299,1000,379,1000,1000,527,-1000,-1000,-1000,156,-1000,-1000,-406,-1000,716,1000,-1000,862,-615,1000,1000,-1000,702,-667,557,130,-1000,162,817,-1000,-236,691,70,1000,623,541,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{657,-1000,954,-1,-84,-120,181,464,-424,605,87,115,-1000,-875,-550,-1000,836,1000,272,847,1000,161,426,792,-454,841,190,1000,-1000,942,-1000,-771,-718,280,-773,-439,-355,1000,1000,-814,-1000,193,304,635,184,-694,17,1000,-220,-286,-348,597,227,-962,-1000,87,-806,-1000,-602,-941,-542,-950,-1000,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-519,406,1000,502,241,-384,940,-1000,-651,1000,638,803,1000,-865,-112,-1000,485,1000,222,-757,1000,-1000,-894,318,-130,-948,753,-175,262,-196,567,-1000,-845,777,-1000,86,242,705,-359,118,-1000,-527,404,-252,337,588,513,1000,1000,408,-566,-424,554,-920,493,472,51,165,-1000,-1000,42,-1000,62,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "convertValue(java.lang.Object,java.lang.Class):java.lang.Object",
            new int[]{-1000,1000,444,169,-347,222,181,-1000,-114,1000,-221,-872,106,-962,693,-120,-1000,-1000,-1000,-969,960,-337,-890,1000,1000,-1000,1000,618,1000,-138,1000,349,751,-729,-482,-415,147,-841,-641,-406,-705,885,1000,-533,415,-615,1000,1000,-1000,329,-667,1000,130,-1000,1000,817,-1000,533,1000,526,1000,-44,343,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{41,705,-381,1000,118,889,-344,698,-1000,-715,398,-1000,575,435,277,-881,364,-934,154,-36,973,551,-498,239,1000,-1000,-199,954,584,-506,1000,469,-197,-256,-597,350,35,366,-1000,158,565,-192,-1000,-896,-166,-1000,401,-407,-545,563,-121,726,132,-3,74,-291,973,1000,1000,-1000,-511,-1000,-223,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{761,578,207,-801,464,624,-536,-460,482,584,1000,283,193,-95,-191,278,-1000,435,427,-580,-730,98,-731,-70,198,369,662,261,-35,1000,987,-408,360,858,220,497,1000,80,752,492,-483,339,1000,217,-6,-255,656,627,-948,1000,146,705,250,6,257,365,748,503,849,1000,160,-16,1000,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-406,360,-566,779,-1000,399,-96,1000,-926,-792,-1000,-1000,979,-357,501,590,1000,-304,-853,200,1000,409,486,-116,921,-808,-1000,-30,16,-1000,-893,358,-921,-64,430,-1000,-1000,537,-260,385,-384,-514,-1000,1000,-80,-549,-396,-711,-824,726,-315,382,623,1000,516,-926,599,257,-40,-905,-729,-586,-44,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{479,747,249,-853,861,327,-49,23,33,-508,1000,606,-434,-1000,-530,-926,311,990,520,33,-274,519,-1000,-485,438,643,935,701,-231,469,737,-980,378,527,-113,-1000,37,-859,630,389,259,18,32,-1000,1000,400,156,90,-1000,113,803,168,-300,-115,-227,-715,-246,1000,1000,1000,493,1000,-243,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{160,-746,378,-321,971,-471,995,-88,732,-715,-274,-576,-3,-475,749,-207,396,-934,348,-36,973,-17,296,-489,315,25,-460,725,-919,-475,-756,728,-928,992,-712,350,35,366,-98,994,30,-265,299,372,-641,202,858,641,-165,563,717,226,461,460,478,-615,-519,-256,110,-522,-808,956,-850,773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{936,14,124,-866,-1000,602,-451,637,509,1000,176,-819,274,-8,-1000,-1000,-256,-819,1000,-1000,1000,1000,1000,465,1000,-338,1000,100,-35,969,-397,-1000,-1000,1000,1000,615,-497,615,86,534,-386,-1000,-331,1000,-149,-1000,-209,1000,-1000,955,633,1000,1000,1000,191,695,863,464,472,1000,-1000,-1000,-8,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-9,-15,-106,-1000,-361,425,-144,-763,1000,-171,-364,-269,-959,-858,-10,1000,-256,18,-301,-936,377,188,92,-186,71,-229,-195,305,-87,-70,169,29,-94,415,361,478,-497,7,1000,-46,-31,-211,-331,1000,-1000,-1000,-125,1000,-749,-386,-767,565,511,438,405,57,488,556,411,989,-244,-475,-91,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-462,-312,319,-861,256,146,455,-1000,832,-955,-448,551,-1000,-1000,1000,-465,-1000,882,-1000,280,-982,-1000,-1000,372,-1000,43,-1000,535,-690,-1000,106,604,433,-1000,-818,-1000,858,-1000,1000,-349,-1000,342,1000,-341,-1000,1000,634,1000,747,-1000,-1000,-1000,-1000,-1000,-111,400,-325,613,666,1000,790,370,977,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "copy():com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{761,578,207,-801,18,624,-620,-460,179,584,1000,283,193,-95,-191,119,1000,-699,427,-580,-730,1000,-107,1000,198,369,662,615,-35,1000,675,-408,360,858,220,497,1000,80,86,492,-483,339,-1000,217,-6,-255,656,627,-948,1000,1000,705,668,6,-99,365,748,503,849,1000,160,-16,1000,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-762,-635,-370,194,39,314,604,-66,302,476,-723,-611,-15,351,241,-272,270,533,389,779,-507,888,153,-567,799,801,451,-201,-898,-601,647,-835,992,-362,-683,-592,593,891,386,-79,-937,-780,267,-630,-577,956,-926,-408,-300,-661,645,-150,1,48,180,-713,-949,-778,607,-78,986,-775,-536,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{1000,333,109,-577,-590,-728,-1000,472,-167,-1000,351,1000,-85,-1000,1000,-375,-1000,-280,186,372,1000,-78,-1000,562,-477,104,-1000,79,811,1000,-11,1000,866,-784,136,300,-302,-76,-154,-254,671,-307,-913,-1000,104,932,1000,358,-1000,1000,286,-1000,-1000,-72,880,790,652,1000,-811,919,-295,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{1000,127,-691,694,-252,541,-502,-817,1000,-1000,-620,-593,-562,-422,379,-44,-443,-981,-304,1000,-342,-518,-154,-567,-1000,-479,-593,-1000,-1000,982,-797,-400,-879,-53,940,177,964,1000,-328,-794,-1000,210,-1000,-1000,1000,1000,581,141,1000,-387,91,-677,-1000,295,116,-400,1000,-290,-548,1000,-863,1000,-664,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-1000,-1000,400,-186,284,432,857,875,-911,1000,-811,-648,-353,649,-93,-290,188,780,-126,1000,-796,624,1000,1000,863,-335,479,721,1000,-622,-308,17,1000,306,154,-954,-275,-952,1000,1000,-891,326,369,-695,283,-409,342,-836,-173,846,344,724,-277,1000,415,1000,-138,118,266,928,316,-1000,338,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-1000,-1000,-77,-586,375,-168,910,974,-1000,1000,-623,-474,28,-331,-1000,197,445,752,-330,989,-594,739,1000,1000,1000,691,-376,1000,1000,-529,114,-270,1000,447,-357,-1000,-42,-1000,1000,-204,-1000,379,868,-1000,-898,-984,-449,-133,-366,1000,630,807,-351,595,-331,1000,-1000,734,685,402,764,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-1000,1000,-260,-221,365,351,17,949,-57,1000,343,-628,-353,1000,-93,489,188,1000,-223,-178,-166,691,667,-128,832,-335,479,850,1000,-622,332,-58,882,-503,-432,-954,807,-796,705,375,923,326,733,-388,-282,-855,-1000,-836,146,211,1000,1000,1000,1000,279,1000,-472,-1000,-1000,-754,-524,-1000,839,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-653,-808,943,-761,703,1000,1000,425,-809,1000,-245,1000,-650,529,-772,-556,709,629,-106,1000,-1000,-73,1000,1000,431,1000,727,770,513,-247,-1000,1000,734,452,152,-767,-275,-1000,1000,769,-692,-1000,-386,-404,407,-763,676,-591,371,1000,187,837,25,1000,-435,1000,32,1000,-827,928,1000,-1000,807,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{354,-554,-253,-372,-358,-344,288,1000,-950,1000,-12,424,132,-652,649,410,-256,890,1,-523,36,1000,-237,770,1000,755,-102,178,1000,87,710,1000,1000,-299,-1000,-528,-151,-1000,242,274,-329,-794,1000,-845,-738,-178,-280,-32,-463,1000,-625,80,-451,7,785,1000,-895,842,-14,620,960,61,42,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-1000,-701,343,-106,620,-51,-45,912,-967,1000,-749,731,-1000,1000,-212,136,321,923,-29,1000,168,737,782,-5,904,-1000,463,1000,832,-1000,288,-1000,1000,-448,-74,-1000,711,-493,1000,720,83,-216,559,-805,-285,-320,-565,-966,-707,199,1000,1000,521,1000,139,-20,-305,-771,-199,-185,1000,-1000,372,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ArrayNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createArrayNode():com.fasterxml.jackson.databind.node.ArrayNode",
            new int[]{-350,-908,1000,-522,-603,-735,213,-1000,-555,1000,192,880,1000,-1000,-285,-375,156,777,851,430,-917,1000,-733,827,492,1000,54,-601,1000,885,-596,916,866,950,867,917,-578,-11,708,-254,671,-807,-681,-1000,-751,1000,1000,262,-1000,1000,-1000,-1000,231,1000,-378,165,-591,1000,-1000,872,445,471,101,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{39,-373,-285,374,588,17,504,-781,-751,198,78,-828,259,946,-275,973,-836,429,-134,-724,365,-73,707,820,204,-558,124,-492,139,-736,-79,-990,-664,-174,-309,388,-231,118,-458,-382,-201,301,-886,-137,127,-767,508,-103,993,122,-971,-839,-456,-307,-36,-411,-582,-76,2,585,747,656,383,280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-347,-231,193,-587,926,53,-1000,572,-150,284,264,-273,613,187,-356,741,-491,260,-138,-384,-149,503,109,265,-733,-705,166,-520,-321,-608,381,-1000,32,340,112,835,-565,-591,-1000,-410,-156,176,-345,-757,338,-180,743,-69,837,-411,432,-791,532,-245,-232,-188,-253,239,-379,-126,-264,181,-233,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-292,-1000,1000,944,-890,83,-805,530,533,-117,-442,1000,1000,-542,-1000,542,-1000,839,544,596,-849,1000,-523,22,-1000,-1000,-133,-1000,351,290,956,1000,668,-444,611,699,-626,-560,-367,270,435,-495,-1000,60,1000,-295,-490,-1000,-400,-1000,1000,-981,-64,451,-115,-394,-799,43,-1000,307,219,1000,-914,-397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{721,-713,918,529,792,-477,-348,-476,-19,-461,-401,289,-1000,-178,-728,-851,262,-977,544,1000,898,-335,-118,-1000,1000,-1000,-871,1000,400,635,525,-233,-1000,-1000,991,-323,-315,-293,-555,3,-196,-76,721,1000,416,747,-212,-1000,1000,-399,-1000,208,-218,-117,118,-154,-331,-303,-704,-643,734,-710,52,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{432,-1000,973,79,201,59,-371,-132,-796,217,-653,58,1000,-180,-1000,1000,-1000,295,-17,-1000,-481,-120,270,1000,-564,-558,437,-1000,742,235,356,-1000,1000,153,-1000,185,692,-1000,-484,-756,279,-97,-1000,-576,819,923,-125,-425,110,-258,-551,-1000,893,1000,-36,-685,-442,613,-208,676,-295,13,-310,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{1000,52,219,897,-336,-256,417,-348,-174,-924,1000,41,599,-546,-548,1000,-375,1000,917,839,-310,319,-489,357,-267,-999,91,-519,-526,-222,754,-19,-1000,-1000,-443,-1000,-71,-259,19,466,745,-394,-350,-86,946,-322,1000,-357,847,-865,-44,-803,-94,541,1000,-497,-447,313,-1000,881,-37,-84,-1000,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-961,295,893,-476,405,465,-700,-44,367,1000,-744,557,282,-1000,713,681,840,-818,-1000,1000,-454,-166,160,-1000,197,798,11,479,-92,-507,-369,1000,536,1000,1000,1000,1000,-1000,788,-1000,-937,427,1000,-1000,-711,-42,386,669,-585,130,1000,-673,1000,349,-1000,-30,1000,-482,476,-1000,-930,-690,155,549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{-77,-298,473,-707,193,267,719,303,849,-108,1000,1000,1000,1000,-1000,1000,171,-222,1000,-1000,-1000,1000,-491,1000,-1000,-1000,1000,-1000,99,-1000,864,-1000,1000,-1000,182,-1000,-756,1000,-1000,-37,256,-1000,-1000,1000,1000,-635,-652,-1000,1000,-98,250,-1000,-22,1000,94,589,-1000,1000,-1000,457,1000,1000,-1000,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{646,-1000,1000,189,-808,-916,383,-207,219,-623,482,249,1000,-404,-1000,1000,-1000,833,-348,-508,-1000,1000,-1000,969,-1000,-1000,1000,-1000,-538,-1000,1000,-485,665,-1000,535,-513,-1000,630,-427,-155,721,-887,-1000,395,1000,-581,-125,-1000,827,-1000,-191,-1000,236,-275,946,-950,-1000,1000,-1000,1000,823,1000,-502,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.node.ObjectNode", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "createObjectNode():com.fasterxml.jackson.databind.node.ObjectNode",
            new int[]{39,-355,1000,854,17,580,-1000,219,-751,361,-1000,1000,400,81,-365,228,-710,-598,-858,839,-45,276,446,-574,-400,586,128,-400,139,-289,35,214,307,530,1000,1000,-231,-845,-1000,-1000,-1000,184,-426,-94,1000,-767,1000,-852,-982,-405,69,-587,523,-457,-1000,101,-573,1000,291,-843,-186,-473,1000,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-749,298,941,99,-438,-45,-122,258,-292,-495,-646,114,507,-527,274,-317,86,-710,337,-173,-196,1000,-155,550,1000,356,-30,1000,227,662,-915,-891,-572,334,-837,104,-1000,157,-307,851,-389,123,79,-398,-149,208,478,-63,855,70,-755,914,-363,-701,-467,111,399,770,-652,-1000,1000,108,741,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-123,-789,-256,115,67,173,-580,397,-714,234,-982,136,857,-837,-593,-1000,-428,439,910,541,789,-421,552,-66,531,130,-102,9,-209,509,207,-643,-1000,967,-767,217,-481,-888,875,726,377,-1000,-579,-1000,829,352,697,-55,153,530,-424,-179,-739,-706,129,-416,1000,-504,-72,-1000,675,-270,-5,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-938,1000,-252,-141,-854,-468,374,-787,-987,-1000,984,-43,716,733,440,1000,164,-1000,-320,-216,-660,845,1000,-428,-334,940,1000,-1000,-1000,-477,-1000,1000,1000,-464,-838,900,-1000,-1000,-1000,-417,-506,405,674,-762,-896,-14,-523,-1000,971,-837,711,620,-1000,-934,-1000,1000,247,-262,-1000,-641,1000,-1000,645,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{689,1000,941,-71,106,-1000,-122,654,-1000,948,809,375,1000,-732,274,497,375,-1000,-30,-577,-218,1000,842,-152,1000,356,656,-114,-763,-67,-1000,-33,1000,-114,-849,257,-1000,311,-1000,87,-1000,1000,1000,149,174,784,-103,-1000,1000,-439,-215,856,-859,-1000,566,911,909,-717,-1000,453,922,-620,364,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{864,1000,961,-621,-1000,-574,1000,-958,-285,879,-413,910,918,411,283,1000,131,-1000,-708,240,-1000,537,1000,269,-760,20,545,794,-968,287,314,-381,831,-1000,-1000,751,-94,-629,-1000,-1000,308,1000,1000,1000,-1000,-1000,999,-797,340,-1000,968,1000,1000,20,1000,1000,-1000,-511,-1000,1000,1000,-1000,1000,943}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-205,-1000,-35,-107,-903,50,-66,436,-72,75,43,-1000,296,-600,107,-943,-409,-24,-1000,1000,-863,535,-789,846,-702,265,-41,1000,345,993,-1000,107,-1000,664,209,-312,716,-513,1000,-187,119,-1000,-871,-101,961,-1000,-30,-242,-126,99,-360,-34,164,818,-1000,-1000,609,252,698,-913,-387,737,122,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{864,1000,961,-621,682,-655,505,1000,-285,861,455,1000,1000,-1000,-133,304,134,-862,-1000,-1000,-949,537,1000,212,91,1000,545,156,-256,305,-167,-1000,841,-356,-1000,-97,-1000,-321,-1000,-406,-20,738,988,1000,-175,-673,543,-1000,719,295,560,1000,-1000,-1000,1000,1000,-1000,-298,-1000,724,-131,-1000,-158,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{420,1000,335,99,427,-104,685,258,388,1000,-646,1000,507,-814,-703,1000,86,-1000,-1000,-469,-902,421,1000,-233,-1000,855,1000,106,-396,37,932,-1000,895,334,-979,15,-112,-477,-817,-1000,723,1000,1000,-398,-145,-1000,1000,-1000,175,-312,1000,1000,-363,16,1000,1000,-1000,-1000,-769,1000,432,-1000,417,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,-977,-1000,-516,1000,441,895,-493,-110,-1000,-1000,-268,1000,-221,-1000,-1000,68,1000,-996,-901,1000,-167,-1000,-1000,913,-1000,822,49,764,1000,277,-951,-1000,175,-1000,-435,-881,-186,955,836,-639,-219,-1000,-821,-40,-254,-550,385,568,1000,894,987,-1000,377,795,-880,-132,1000,-506,826,542,-222,1000,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{930,1000,435,109,-438,-45,533,-787,-1000,-260,656,-900,607,-527,968,-317,-749,-740,-1000,-173,763,846,408,786,-198,-170,173,-121,-188,631,-838,660,-751,1000,-721,493,-1000,-1000,-903,851,-1000,-12,-1000,-774,-314,589,-324,-896,1000,-82,-833,851,-400,-1000,-467,-215,1000,-159,-1000,-143,-105,145,-555,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{952,196,1000,-317,-727,-236,1000,-11,-685,795,-323,1000,1000,-53,-421,-132,-539,-584,-1000,1000,-701,1000,470,185,626,1000,396,249,-58,591,28,-490,-699,-245,-1000,129,-1000,-1000,31,-687,941,175,994,-865,47,-763,-1000,-748,451,-201,299,1000,-487,-851,1000,1000,206,-1000,-1000,-226,1000,-332,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-641,-459,4,-215,-355,-581,-750,-513,-245,400,-337,-1000,263,193,638,-1000,138,252,-317,-187,-108,-299,367,1,-1000,-1000,1000,321,465,717,-285,121,23,982,1000,685,-1000,-247,1000,-1000,578,370,-444,1,-629,-46,1000,500,-617,-800,-1000,-884,1000,926,1000,-1000,-629,-95,1000,145,1000,-16,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-444,123,-841,542,-396,-839,17,-1000,-879,-688,301,-38,765,-229,-33,638,290,1000,482,481,-378,-1000,1000,-214,-474,170,-793,436,-612,237,-243,-431,657,1000,256,-681,1,-1000,-280,807,239,331,-597,-792,345,-1000,1000,869,580,-905,-799,-1000,-608,699,-585,-882,-978,212,-708,631,-793,-578,104,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-547,-35,666,608,-314,-869,-173,-719,104,-402,132,-318,239,143,242,289,-445,732,-407,64,-104,-77,236,656,-360,-466,-722,425,-759,-46,270,-390,506,796,435,-599,890,-314,517,952,-595,891,-780,-589,289,-776,653,966,507,-783,-926,-424,-636,865,287,579,-978,-521,-502,689,-432,83,-807,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-796,325,742,-71,508,809,970,-120,-409,-200,481,89,-203,-973,73,-26,223,174,-51,-281,847,-459,-194,-410,787,868,297,-908,-432,-583,-615,-43,-80,-324,-305,423,-408,-831,826,-490,947,-223,-250,-916,696,-318,963,373,7,-393,-203,296,-536,371,-515,-508,-129,941,959,245,707,-849,124,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{518,601,666,-507,364,-127,-852,730,749,1000,-686,-318,201,26,649,289,997,953,-407,286,-1000,-345,360,-97,1000,-651,-45,-93,363,196,-115,245,-223,-243,-112,-599,1000,-356,-111,-2,-595,214,-806,-908,483,890,79,-772,-545,562,257,-1000,741,236,6,342,446,1000,71,-982,-791,-504,701,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{685,807,-812,744,78,1000,298,-470,410,24,-1000,-1000,-148,-551,-186,-1000,1000,168,168,-131,295,971,-1000,1000,374,1000,-657,220,-1000,631,209,-778,-1000,577,-231,619,-1000,171,637,740,-614,452,-1000,-1000,1000,672,-894,-868,-112,-237,-1000,-1000,-894,-879,-472,1000,262,-49,1000,-175,76,-400,-819,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-88,-1000,358,-111,-63,-951,-916,-1000,-15,-537,1000,752,-1000,595,-233,1000,-1000,-162,-1000,-169,-268,-400,149,-429,-525,-1000,-403,685,171,-525,1000,378,278,614,1000,250,1000,438,176,40,1000,1000,461,-407,-1000,619,747,1000,756,-754,896,350,177,-797,1000,-905,-1000,-1000,-527,1000,-739,1000,-1000,-128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-170,-34,-806,94,757,449,484,-750,681,421,-13,-648,-683,595,-384,101,-802,-923,-648,-578,969,1000,-604,1000,340,769,-295,-47,-666,-1000,134,-711,-325,918,802,-14,230,440,-662,199,819,313,486,-1000,319,53,-269,1000,475,-539,12,289,-587,106,642,1000,-21,-1000,557,267,315,543,-1000,-260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-954,-536,-234,-331,566,-565,434,-231,82,-386,597,-308,-526,73,-73,1000,-714,-642,-165,141,535,-375,455,-305,-1000,562,-845,590,417,500,802,-842,-527,581,141,628,19,370,-310,-25,641,558,-497,830,-359,1000,56,1000,185,-379,-53,-556,138,-860,612,-192,-418,-1000,-21,646,-160,598,-416,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.core.JsonParser$Feature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-363,-209,-1000,364,523,615,-826,196,1000,1000,-260,-84,-1000,240,1000,-812,663,275,159,-816,921,1000,-1000,864,-7,562,-259,-258,1000,1000,742,-341,-1000,379,221,1000,-700,1000,-1000,365,1000,-187,120,-1000,617,1000,-332,536,-202,-165,331,769,-197,-950,-118,0,1000,-304,653,-906,1000,-666,-542,471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,1000,35,-166,1000,513,-218,-956,1000,573,706,1000,-741,-1000,-1000,1000,1000,-913,-95,1000,680,-399,-767,-280,-935,1000,1000,685,949,1000,-331,601,-1000,1000,780,-1000,-1000,1000,514,1000,1000,-231,-996,-1000,1000,-1000,661,-491,803,976,-1000,-1000,-1000,84,-886,-1000,1000,-460,1000,528,1000,-96,1000,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{78,324,244,-209,-784,-872,585,-75,-985,827,-230,850,-121,492,-1000,-197,-188,318,394,-219,-1,536,964,321,-943,31,-81,-1000,1000,-1000,455,38,1000,-100,1000,930,-632,-245,62,894,-390,-400,35,-471,-74,-1000,1000,840,-369,-392,-481,-488,-1000,113,-1000,639,1000,1000,80,-508,-1000,-248,-654,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{422,1000,-93,414,-161,788,-1000,538,1000,282,599,-704,-1000,-803,-273,-83,884,-178,394,-49,1000,-1000,-589,-765,-1000,-813,-1000,1000,801,1000,58,-869,-606,-54,172,811,655,499,-289,1000,879,542,128,-1000,-923,494,309,-618,-227,-275,817,-891,130,-419,-298,-694,-39,-825,-1000,99,1000,421,-52,-540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-25,579,558,391,-633,96,878,779,-321,240,-431,548,-778,1000,611,-219,469,-116,450,-154,-197,79,-23,-601,1000,786,-575,283,390,-631,-430,240,-1000,480,-1000,-1000,133,100,69,195,-87,-574,-668,1000,-481,-488,792,512,644,-617,398,-1000,1000,-447,-166,255,732,280,773,-125,-864,560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-724,1000,-688,458,-785,-1000,-618,260,-229,-242,-1000,-207,-1000,-197,-1000,-140,236,339,1000,-915,959,-1000,-161,77,-973,-1000,-442,-315,777,-134,-138,99,818,-308,780,-286,932,173,478,-373,700,-323,1000,-1000,-914,-1000,909,-310,-1000,-1000,952,125,-828,1000,-1000,1000,1000,63,-1000,-790,-481,276,-83,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{298,1000,-809,-321,-283,31,-853,-43,-15,-214,453,574,-1000,-548,-1000,831,-400,-1000,-383,96,1000,-471,143,-226,-1000,-237,-1000,54,1000,-15,-733,833,400,239,1000,-841,400,-93,273,516,651,-59,-15,-876,1000,-1000,-315,-196,-462,950,-669,-994,-1000,585,-930,600,1000,1000,582,35,-400,-422,-849,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{194,169,-77,-166,-400,-339,-489,1000,230,1000,274,468,-595,96,-923,-674,281,152,477,-415,932,-262,-644,-547,-244,-601,-1000,-718,459,-400,-616,-628,400,-698,-393,1000,-1000,-1000,-1000,-905,-943,-721,-391,-363,-676,690,-496,1000,-168,168,749,433,578,-354,1000,-81,93,1000,-400,-176,173,423,-1000,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-501,1000,323,588,-3,-66,-1000,-141,350,-514,-504,99,-907,-1000,-686,728,298,-1000,653,-145,865,-1000,-17,-244,-1000,-952,174,865,1000,1000,-13,-326,-582,167,780,-1000,56,1000,1000,499,1000,513,1000,-429,295,-1000,1000,-989,-1000,-455,-143,-709,-1000,794,-1000,-312,1000,-890,-1000,-445,219,126,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{472,1000,1000,-856,891,161,-384,-1000,1000,916,115,1000,-1000,-980,-868,319,1000,904,835,1000,889,-846,-1000,30,-1000,885,803,409,1000,688,-276,-894,-1000,839,1000,1000,-678,845,-324,-831,-36,1000,476,69,722,-145,1000,-297,-1000,-960,-316,29,-1000,41,-1000,337,1000,-516,-218,704,1000,542,1000,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{337,69,719,-89,-162,-76,-333,-304,165,1000,220,120,-906,-980,-1000,31,834,760,829,-66,889,-315,695,30,-615,-328,803,-1000,1000,-1000,15,-169,1000,204,1000,1000,-678,-523,-1000,540,-444,1000,-884,-1000,-1000,44,-469,1000,604,356,159,-739,-395,1000,15,92,1000,1000,-1000,704,1000,-126,-1000,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{1000,977,-185,-716,736,92,264,-1000,385,514,1000,1000,-493,-754,-1000,1000,229,-1000,-516,1000,907,-399,-101,1000,-915,1000,1000,184,979,465,-774,1000,-193,1000,1000,-1000,-368,-181,534,1000,1000,-465,-1000,-673,1000,-1000,-255,-1000,-1000,1000,-1000,-1000,-1000,1000,-1000,-112,1000,901,1000,1000,474,-446,-371,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-303,632,-761,774,-212,1000,331,390,-910,-897,925,-748,-968,495,1000,1000,-801,91,-364,-95,550,-931,-1000,615,-1000,755,561,707,-617,575,366,-716,370,1000,981,-443,-444,-1000,-1000,398,-81,509,-240,-1000,-1000,-663,-77,1000,-724,-149,570,269,-600,115,-53,575,-903,1000,748,-209,115,-1000,-1000,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-183,454,52,139,897,924,331,390,109,-340,925,-1000,-533,-207,-206,719,647,-237,611,-95,-274,-692,1000,1000,-190,-119,-650,-1000,937,-471,36,1000,-1000,-1000,466,-208,-1000,17,1000,339,1000,-452,499,-294,1000,213,-479,1000,-733,895,332,369,-256,-1000,311,976,-957,-656,27,96,-180,253,512,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-592,303,484,-835,-1000,387,616,-540,-884,253,-341,856,485,-808,1000,738,-650,-572,1000,-1000,962,-888,-394,634,795,-1000,900,-188,740,-554,-1000,1000,-932,-113,276,-1000,-1000,-104,1000,340,-280,-1000,-412,-579,22,538,-621,1000,-1000,865,258,-698,1000,-1000,-260,81,368,46,27,858,919,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-453,4,113,574,-65,226,1000,-312,-641,-545,1000,-1000,-1000,928,-673,761,544,-549,-408,245,1000,-943,-701,909,-360,-1000,347,-1000,1000,-933,-1000,1000,362,-651,542,-832,-1000,-1000,1000,1000,1000,523,446,-815,579,-564,-287,1000,-925,774,1000,-150,-974,-426,1000,-99,-1000,-142,-407,-381,-600,728,295,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{325,1000,-982,-116,-503,717,405,-415,-105,-1000,925,-312,-113,1000,-1000,1000,196,992,302,-1000,990,-595,1000,-120,-247,-625,750,-1000,1000,-532,-1000,1000,-1000,-1000,1000,-1000,-1000,-651,119,1000,707,118,-453,-319,215,-701,-158,1000,-1000,1000,1000,-1000,430,-1000,335,1000,-775,52,1000,770,967,353,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-230,1000,-1000,384,470,507,-602,-1000,279,-104,-885,408,65,-364,291,-1000,-926,16,570,-605,-923,-763,1000,-1000,-685,583,-924,224,-320,1000,-552,566,464,-338,191,131,-2,1000,-420,-660,821,-163,1000,-573,1000,546,-490,1000,252,1000,735,633,514,67,-1000,966,639,913,1000,474,773,-655,-121,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,-613,477,364,1000,541,99,149,1000,1000,-1000,17,150,-1000,937,331,-161,-1000,124,1000,-1000,-815,-267,1000,-590,758,-1000,921,-551,-246,784,-1000,867,723,-905,659,426,566,-382,-876,599,-337,927,-899,547,-398,-518,-1000,913,-1000,-105,1000,20,150,-76,-1000,-814,-212,-1000,-482,-1000,-540,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-683,1000,751,-111,89,782,1000,-157,-1000,-525,310,-1000,-583,-83,576,798,-114,-540,965,-283,786,-625,-401,1000,50,-1000,1000,-435,1000,-291,442,1000,48,-1000,574,-736,-856,-1000,1000,1000,998,-1000,-25,-380,434,55,-234,1000,-1000,902,533,-395,1000,-1000,-51,-373,-466,-736,2,128,-319,1000,722,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-1000,1000,-1000,304,-928,230,135,-967,-1000,-1000,-882,1000,1000,1000,-1000,811,-970,828,-1000,-1000,928,-891,671,-600,-957,-67,1000,131,408,234,-1000,1000,636,770,704,-1000,265,-388,383,478,-16,1000,-83,-1000,-860,-695,-187,1000,-1000,895,1000,-1000,829,-238,-225,886,-304,1000,1000,933,1000,-1000,877,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-504,784,-1000,784,-486,880,678,-698,-947,-897,-618,-748,-108,1000,-187,1000,-266,-56,-1000,-653,797,-1000,-669,481,-1000,230,742,263,49,-186,269,-9,377,747,752,-749,-714,-1000,-321,646,306,1000,125,-1000,-1000,-1000,-63,1000,-837,352,1000,-428,9,-252,629,1000,-191,1000,1000,540,860,-1000,-198,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.DeserializationFeature,com.fasterxml.jackson.databind.DeserializationFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-561,-210,-276,451,-177,811,-192,84,400,400,555,-194,-294,-400,69,331,403,-666,-29,400,-400,-742,136,423,-503,22,-400,257,-111,-225,252,-400,50,-626,528,233,-354,264,181,-93,1000,271,288,-899,109,-229,560,-400,357,-1000,-105,-23,-306,-71,727,-237,280,386,-400,-146,-400,605,-400,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-366,-857,-278,-441,-621,172,518,806,-265,-217,440,-574,-308,156,109,842,-144,-593,-963,-67,-10,-647,591,-338,0,833,-131,-903,296,979,-630,-86,389,834,561,-23,572,-719,689,706,576,376,-454,-404,-789,-157,264,469,166,790,-317,-171,-605,464,-658,23,787,-716,-255,845,-790,303,171,657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{800,-150,-971,966,-1000,-210,1000,-743,71,1000,-1000,-367,-952,-136,407,515,-603,-382,-1000,527,688,337,-732,-675,-547,1000,209,176,1000,1000,551,-144,-342,1000,1000,763,631,678,1000,-1000,-1000,0,-509,-586,-853,-433,478,185,1000,419,-1000,-71,591,-53,265,-1000,1000,-361,859,955,547,-140,-1000,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{482,25,-863,339,-91,-115,924,-772,858,28,-362,300,57,374,-69,269,-151,-871,-916,188,84,49,-349,-624,-257,665,438,617,218,548,150,-79,69,-232,284,687,72,197,98,-827,-575,476,808,-125,-920,-849,580,-399,358,-300,316,-162,228,47,760,326,926,-519,44,706,975,-142,-601,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-977,-1000,-8,-666,1000,103,728,559,-298,-1000,-1000,-1000,1000,-1000,250,687,509,-475,439,-567,52,-1000,346,-929,1000,-163,-960,-576,-1000,-1000,156,1000,-1000,447,-795,-1000,-433,-583,110,-265,-74,819,806,-70,1000,-841,-8,-993,-865,627,-998,-1000,-735,-507,-797,-272,-1000,1000,-785,1000,-1000,386,1000,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{561,-854,-277,179,570,-158,339,-421,310,-20,-830,-107,-1000,812,-183,640,-348,88,-184,263,229,-245,-201,650,-468,506,383,-830,17,198,-608,657,717,-47,-344,-210,296,348,99,-625,352,300,219,284,-166,-334,473,-403,272,-1000,-728,-970,400,34,13,-588,16,-89,24,300,271,-827,980,748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{933,-656,-979,-152,-293,-43,1000,580,207,-1000,129,-1000,-1000,238,-621,1000,-109,-1000,-924,602,-468,-742,111,79,119,691,-79,-1000,468,1000,-761,71,-148,1000,975,65,229,-922,1000,-485,407,943,-226,-744,-831,-1000,-340,-959,59,67,1000,-742,-332,142,276,508,1000,-341,-504,1000,-275,-107,-147,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-977,-1000,-8,-521,-595,655,642,1000,37,-1000,503,-1000,-639,-1000,-25,568,439,-1000,1000,507,-692,-1000,242,-1000,505,322,-378,910,-1000,-680,302,1000,-1000,-989,372,-1000,-186,240,-51,-485,-74,1000,1000,-1000,400,-1000,1000,-1000,-227,627,447,844,-1000,1000,-13,1000,-452,556,-1000,1000,-284,386,-1000,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{213,-338,-591,264,1000,-394,-38,224,-411,120,1000,1000,-400,1000,-9,368,40,420,268,1000,-1000,519,121,1000,-993,-951,302,-794,400,-793,-189,283,1000,-178,83,-491,-347,616,493,431,466,552,1,1000,223,204,81,690,-739,385,198,-1000,111,1000,-642,-981,47,-136,290,-500,375,-916,272,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.MapperFeature[]):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{-13,68,-437,644,788,-749,-639,-945,555,320,-505,673,369,1000,-2,443,222,-200,-457,-214,554,700,-388,341,-723,765,949,48,-94,-684,-43,-424,675,-666,-874,961,235,-622,-317,-1000,701,909,757,584,-783,-43,-134,21,-153,-1000,-6,-520,603,-93,770,-106,601,-734,412,48,933,-1000,161,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{489,992,827,259,227,117,726,-887,683,-320,-72,179,629,-665,-211,974,651,166,181,-790,997,-344,-678,-297,221,-935,690,-115,185,759,598,563,-812,-57,758,-816,730,180,-567,661,753,-863,627,-438,-959,36,-839,-644,951,-543,465,-670,-394,-660,-552,-237,259,290,897,-991,-321,641,514,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.ObjectMapper", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "disable(com.fasterxml.jackson.databind.SerializationFeature):com.fasterxml.jackson.databind.ObjectMapper",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
