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
            new int[]{-864,1000,384,-922,-822,-1000,-839,-861,183,161,398,1000,-1000,-269,-331,-217,406,500,-382,70,8,694,-1000,943,-1000,355,357,209,427,-131,-278,1000,824,-806,-100,-184,843,-796,-737,1000,845,315,-699,918,557,1000,230,492,765,177,834,52,-406,-653,-319,-466,-602,192,387,-453,1000,344,-638,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "append(com.fasterxml.jackson.databind.util.TokenBuffer):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{-546,953,-1000,-246,-520,-89,-227,-845,415,861,322,-244,-654,-1000,-278,-305,146,-1000,-930,1000,276,535,-504,1000,-1000,34,-76,-336,808,1000,-1000,1000,546,-263,-23,884,352,-1000,-1000,-434,1000,1000,-1000,607,1000,473,1000,-1000,-1000,186,551,73,-1000,-380,244,1000,-389,-103,-559,570,-34,735,-800,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser():com.fasterxml.jackson.core.JsonParser",
            new int[]{-687,-152,-356,-1000,-126,-957,1000,330,-325,-1000,140,-737,-434,0,172,-64,1000,-170,-714,240,142,-374,-231,1000,-599,-880,-600,0,-225,956,-1000,777,994,587,1000,0,507,-3,-78,-560,-89,310,-36,290,0,0,-393,-482,-1000,-660,-271,-878,-792,17,-627,-712,1000,-245,69,-35,623,-360,-879,658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.JsonParser):com.fasterxml.jackson.core.JsonParser",
            new int[]{-170,76,-251,773,656,201,905,855,274,-776,-835,-271,858,-43,312,434,772,394,-184,-512,518,573,-694,-219,-54,-881,-768,-470,-510,355,259,706,834,606,939,-304,161,-318,198,738,-71,331,248,-862,995,965,707,198,-191,914,-116,-647,-588,-958,467,-910,-6,-293,197,882,907,695,-544,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer$Parser", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "asParser(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonParser",
            new int[]{-832,13,193,-335,-1000,-649,738,-120,168,926,-1000,1000,875,40,-1000,1000,753,-1000,1000,128,255,28,1000,679,30,-989,-266,232,95,-1000,1000,293,747,655,-1000,-703,536,-487,1000,196,544,467,-1000,422,-249,268,-416,641,-847,1000,51,-475,895,-1000,-967,495,-867,947,-1000,437,-804,40,-399,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteBinaryNatively():boolean",
            new int[]{1000,1000,-546,373,88,-548,-1000,728,-298,304,-1000,1000,1000,288,638,-747,949,104,-1000,-310,-442,-120,1000,370,60,514,-977,-303,-1000,-291,1000,353,140,-144,378,-1000,-1000,-966,323,-1000,873,1000,269,-543,717,-626,645,123,-128,-923,-448,-318,335,-28,1000,-1000,826,109,-1000,-96,-343,45,229,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteObjectId():boolean",
            new int[]{592,-56,749,-110,-339,917,694,174,-66,-527,-47,977,642,-899,-582,-28,269,85,-180,-664,365,-735,-28,-731,331,-351,-945,878,-973,314,-131,-844,638,-377,639,-401,402,360,-862,909,503,62,626,-822,-251,-394,453,-13,465,-165,-979,-113,471,-59,-753,-69,956,195,683,-913,621,-371,4,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "canWriteTypeId():boolean",
            new int[]{168,-197,-41,-909,-188,894,590,855,-327,-19,-97,-696,-878,-686,-27,-891,612,889,-725,-356,-820,441,-576,612,547,-874,137,222,581,-518,-916,-314,51,72,370,-256,29,-199,-253,706,-888,326,335,-945,-300,-823,-361,-955,961,-694,933,925,863,563,-270,440,622,-673,963,-946,306,-45,753,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "close():void",
            new int[]{-208,37,-541,-1000,205,-877,223,765,-206,-829,-665,-252,745,-1000,860,536,-305,-1000,-255,-699,-1000,549,-220,810,123,-1000,1000,-1000,337,379,927,475,-1000,338,-517,332,75,300,1000,-510,54,932,-435,1000,-1000,450,158,585,-646,440,15,-623,-432,-1000,-390,1000,-473,-14,245,-874,-450,-787,1000,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{-450,259,-388,-34,86,477,-1000,-1000,152,543,1000,-1000,-748,-1000,-488,1000,-393,501,-216,16,-656,-1000,-735,1000,1000,99,-274,495,263,740,-1000,573,-740,-803,-468,-448,893,-45,1000,1000,-930,-357,-515,1000,85,1000,-96,1000,420,-677,108,-274,-508,-1000,568,-436,-796,-240,-843,1000,-1000,-60,46,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentEvent(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{921,-187,918,-486,-894,8,708,1000,-622,-144,340,262,-338,-869,-519,1000,516,-661,559,228,851,-1000,52,-60,-1000,597,-1000,-135,92,-236,-863,-787,734,615,-850,208,-957,-984,691,557,541,1000,-1000,-630,733,79,1000,-1000,421,761,393,-1000,1000,1000,-238,-1000,-233,414,100,-1000,-695,-986,-805,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "copyCurrentStructure(com.fasterxml.jackson.core.JsonParser):void",
            new int[]{-38,-533,424,555,367,581,-250,960,882,-378,-608,717,445,-868,701,554,-611,-632,-926,-628,-559,-999,-959,770,-787,931,-817,293,12,-327,15,-838,-484,133,-853,-606,-922,-916,-142,-494,263,125,-464,-749,-416,537,-648,208,-643,42,17,501,-935,-777,496,346,-257,-913,-861,750,-548,-880,-127,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{-1000,-251,-208,-992,-632,-922,-1000,-248,1000,551,1000,-841,-1000,766,797,-78,56,-667,-1000,645,-338,1000,220,-274,-1000,1000,-836,771,1000,-197,767,1000,153,1000,1000,-581,-913,-1000,-358,-207,-165,176,1000,-104,1000,-348,-484,-3,-793,-1000,-63,549,489,-1000,-798,1000,726,91,789,-239,-128,-741,69,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "disable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-930,-899,224,1000,744,1000,-1000,-90,-586,564,245,530,1000,-661,257,1000,-141,-726,155,-1000,558,-1000,-260,685,-1000,-560,382,1000,355,99,1000,1000,-1000,1000,867,-38,272,-1000,803,1000,1000,1000,-1000,-1000,1000,1000,434,-941,-1000,636,-498,1000,1000,1000,-1000,-386,712,944,-894,-1000,783,-122,-738,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "enable(com.fasterxml.jackson.core.JsonGenerator$Feature):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{410,-686,149,-706,-89,-1000,-362,-1000,-1000,1000,-1000,883,-1000,-1000,-189,-429,1000,198,967,949,1000,1000,153,-272,-517,-400,157,-1000,-1000,-371,-157,642,884,-988,-1000,-1000,-349,756,-467,68,354,-579,-1000,752,-826,829,1000,601,-45,-1,914,962,-1000,-488,243,1000,-427,-1000,-30,835,188,-926,-68,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "firstToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{-1000,1000,-341,-559,-606,1000,85,-572,1000,1000,300,-768,912,394,1000,-219,-65,1000,-619,173,-400,434,-140,761,-607,222,1000,981,484,-1000,1000,712,228,955,-456,144,-550,61,1000,-37,1000,-1000,641,66,-225,1000,-1000,-1000,238,-1000,-318,1000,-547,-221,165,-1000,-77,620,-1000,246,-647,-436,398,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "flush():void",
            new int[]{-753,19,489,128,33,221,-279,479,-436,-57,660,163,-546,382,477,-952,859,290,120,890,383,-183,558,-532,-542,-669,355,48,144,36,-819,-512,568,258,-739,670,244,-484,-56,-294,-635,163,-377,-677,843,-711,-1,-755,-680,-172,370,-24,-295,-633,59,390,854,21,-680,-204,-39,935,-797,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{-774,88,17,-776,-747,9,-220,68,482,-354,945,69,340,-64,174,731,-42,504,-933,80,653,273,117,754,226,732,-459,-801,510,-879,776,-733,-574,68,-285,418,6,-726,364,469,-666,-291,-547,-635,157,-471,809,-195,-949,-631,773,723,-565,-951,810,594,-503,-175,594,-659,129,275,335,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getFeatureMask():int",
            new int[]{541,229,-616,-505,569,-709,231,-447,-387,53,-420,-875,462,905,129,-720,-717,770,-453,95,491,-129,-436,640,168,-872,-881,576,275,289,-585,-66,90,-136,142,16,391,283,-694,-903,-297,-721,114,-391,645,-561,-799,849,-141,139,-900,886,-698,-420,322,-691,-201,-433,651,739,-358,-185,-635,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.json.JsonWriteContext", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "getOutputContext():com.fasterxml.jackson.core.json.JsonWriteContext",
            new int[]{882,-551,839,-215,654,1000,-245,-1000,-209,-931,-154,1000,1000,-1000,243,146,1000,568,-620,29,916,-847,-806,-387,994,-1000,1000,346,1000,1000,27,62,-1000,-294,-426,651,1000,1000,220,-473,239,-386,-1000,-1000,-811,-1000,-799,-871,128,1000,-1000,12,-383,494,1000,-430,-533,388,127,625,-1000,135,-777,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isClosed():boolean",
            new int[]{972,-101,-291,312,890,23,123,-447,-969,136,30,-698,-806,-125,387,898,-96,778,576,422,10,-367,978,410,-136,-607,679,-604,956,-782,608,655,-431,-637,-950,924,-778,-248,768,-682,451,-636,757,200,831,823,-964,-676,-954,956,-157,214,288,-491,-942,-539,299,692,-210,-631,-51,-219,-341,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{-812,-944,-968,-1000,821,-1000,-585,-1000,-1000,-690,539,1000,59,-1000,-960,27,1000,-1000,-465,339,-52,387,-195,255,-93,-781,-67,879,-1000,558,-1000,-78,228,1000,849,-89,-768,291,-754,248,550,540,-624,-835,490,1000,-157,-1000,375,-245,1000,-367,-27,71,345,592,250,335,-195,440,-1000,201,99,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "isEnabled(com.fasterxml.jackson.core.JsonGenerator$Feature):boolean",
            new int[]{-388,-938,679,-1000,1000,-502,-1000,-1000,-621,-1000,-125,801,-114,-1000,-885,-490,1000,-590,-1000,1000,485,-7,-1000,588,-393,-1000,-405,1000,-1000,969,-440,-702,877,96,-1000,-597,-329,875,-1000,461,295,1000,-1000,-1000,896,1000,605,-798,1000,420,1000,257,385,543,361,180,-126,412,-695,1000,-1000,657,-377,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "serialize(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{290,431,118,-347,-292,-175,188,456,-940,817,-132,-927,-706,333,985,-747,793,505,-3,-847,814,-564,200,-743,929,139,517,698,33,-361,553,-309,484,-900,-616,-276,-973,-758,440,531,302,-726,736,-86,939,105,-297,-977,500,-294,928,-145,-122,129,-795,-622,135,-68,-512,3,-767,529,-427,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "serialize(com.fasterxml.jackson.core.JsonGenerator):void",
            new int[]{-437,-884,-457,-1000,-446,-999,1000,1000,-1000,-443,872,-1000,-1000,762,1000,447,49,1000,-1000,-128,1000,-1000,788,-657,-103,-1000,-949,-855,1000,-1000,1000,-359,-831,-718,-1000,-1000,-106,-864,-438,446,440,1000,779,181,-1000,328,1000,-415,556,216,268,-1000,743,-373,-1000,-135,309,-834,533,-1000,1000,-1000,-126,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "setCodec(com.fasterxml.jackson.core.ObjectCodec):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{116,286,84,-815,-516,-178,-38,-1000,-163,345,651,118,52,0,-891,-357,521,586,0,219,1000,1000,706,-158,576,-533,1000,636,0,-321,-110,721,-155,-305,1000,4,607,1000,1000,-474,730,-615,257,1000,-1000,521,-701,-579,393,59,-756,0,-333,-323,-130,1000,901,0,475,-163,0,0,907,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "setFeatureMask(int):com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-300,-335,318,484,495,-674,-996,66,-2,-151,471,-290,505,-729,-212,897,113,-8,251,645,-851,-571,986,-165,-556,-257,500,726,-651,-534,192,-463,-242,-713,-170,-717,628,-80,-110,641,-534,40,-719,597,-890,609,-502,-174,-278,529,-492,359,-591,-429,-211,908,-908,-232,854,454,378,64,-676,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:W1Rva2VuQnVmZmVyOiBd", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "toString():java.lang.String",
            new int[]{13,-122,-647,400,-565,36,444,-928,133,538,-915,1000,629,-381,-390,-486,-442,-1000,216,-556,893,504,-314,251,468,666,632,-540,-1000,417,-410,697,697,-744,-295,113,-696,1000,1000,994,-514,-527,599,-95,1000,-590,-258,55,-552,-481,-955,-571,-1000,-667,1000,-712,-138,851,-292,-79,-110,-1000,-604,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:W1Rva2VuQnVmZmVyOiBd", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "toString():java.lang.String",
            new int[]{415,923,496,-261,485,1000,-435,26,-1000,-217,308,741,-18,925,455,-606,-72,-905,5,1000,216,-738,1000,666,-277,116,1000,172,893,-422,-1000,564,399,688,-121,-734,668,394,-1000,828,564,562,753,9,422,69,375,-116,138,881,445,783,724,1000,795,607,546,-330,572,-640,215,-428,853,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.TokenBuffer", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "useDefaultPrettyPrinter():com.fasterxml.jackson.core.JsonGenerator",
            new int[]{-321,1000,-356,1000,-1000,-563,-124,11,60,802,1000,1000,1000,-361,-634,-34,968,-269,147,-531,-1000,-1000,1000,1000,673,1000,642,473,1000,1000,723,1000,-1000,39,-125,867,-821,706,1000,404,836,248,-503,269,-223,-27,-591,-1000,1000,-769,1000,782,1000,-480,-492,-212,-239,-395,1000,1000,1000,-255,-1000,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "version():com.fasterxml.jackson.core.Version",
            new int[]{-456,-104,477,-180,1000,1,-1000,-444,-750,1000,299,795,608,-682,-620,-1000,-545,-115,-373,-218,627,-600,-329,-244,858,1000,630,906,183,-1000,1000,240,1000,386,793,-1000,-276,520,-352,-648,501,323,1000,-985,-672,1000,-1000,1000,-550,663,1000,-124,353,235,-739,-118,590,323,761,-1000,1000,32,221,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,byte[],int,int):void",
            new int[]{249,1000,-371,191,31,-757,289,-543,-132,-1000,143,-664,164,983,695,-806,315,377,-637,376,-479,968,-155,483,40,1000,240,95,-63,-506,-527,-371,308,-86,-99,13,-91,-780,1000,1000,-491,1000,416,511,833,-776,1000,400,328,-501,971,1000,-167,-555,-579,437,-437,-756,-36,315,-155,-976,303,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBinary(com.fasterxml.jackson.core.Base64Variant,java.io.InputStream,int):int",
            new int[]{-90,976,937,807,307,-50,799,-653,-371,772,673,-107,-13,-524,150,-165,391,-846,523,-125,-139,-858,725,-570,-153,271,-194,537,64,-572,-422,-653,-456,-354,340,51,62,945,449,873,-58,245,897,-369,713,-211,-964,-710,-933,747,-878,-94,139,-84,-554,424,-731,777,479,-95,87,-447,-337,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBoolean(boolean):void",
            new int[]{-1000,1000,-867,1000,933,1000,1000,-1000,1000,1000,-119,754,483,-924,701,794,-997,237,-295,-431,-616,-845,-683,273,-1000,60,-476,-582,-1000,-93,-249,245,1000,-618,228,1000,-1000,237,609,-1000,60,797,1000,-1000,-723,1000,965,83,496,-9,909,321,440,1000,36,635,-397,819,739,296,-149,-259,207,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeBoolean(boolean):void",
            new int[]{1000,1000,334,-1000,-1000,-196,-908,519,1000,-307,72,-665,-831,1000,-1000,361,912,-70,1000,43,975,1000,1000,996,1000,1000,1000,1000,-1000,1000,40,-371,262,-874,-405,-1000,1000,-546,-891,1000,1000,1000,-737,851,17,-1000,960,-574,1000,1000,-1000,1000,-1000,274,-845,893,-1000,-305,-780,-101,-1000,116,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndArray():void",
            new int[]{1000,781,-966,-488,-1000,1000,606,1000,-1000,-95,951,263,-1000,-1000,633,482,898,-1000,-796,-79,-1000,1000,-1000,1000,48,1000,1000,-1000,-172,1000,-536,1000,-985,1000,1000,-1000,1000,-972,193,-296,1000,1000,-893,1000,-533,1000,1000,-1000,-1000,-1000,530,-1000,682,1000,-1000,-14,-147,721,-556,-529,181,-1000,834,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeEndObject():void",
            new int[]{613,-41,-301,-852,-496,-1000,-773,211,-668,-547,462,-722,99,464,-25,777,783,1000,26,738,-469,710,164,-82,-105,-19,-552,-429,1000,482,-104,641,-533,-87,-540,18,780,-37,922,-31,471,1000,574,-1000,-71,193,7,649,507,-116,92,-292,517,-139,-1000,137,-783,8,-646,837,4,-788,-1000,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{298,-614,784,-426,992,38,195,1000,-747,-1000,941,-841,1000,-33,594,87,-1000,1000,605,-588,889,1000,-446,1000,-319,-186,721,-1000,-1000,-856,-154,1000,1000,-1000,961,469,1000,-67,258,60,-1000,-1000,1000,472,1000,1000,-570,-359,412,-176,-376,-1000,-722,1000,-1000,506,-1000,-463,-1000,-1000,1000,784,677,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeFieldName(java.lang.String):void",
            new int[]{-152,1000,-111,1000,534,-104,-1000,-431,-337,-120,-1000,-1000,-1000,-574,470,-202,-222,1000,2,1000,1000,-173,-144,146,-471,-422,180,1000,1000,-14,-1000,1000,-1000,931,-953,589,-95,-177,1000,-456,-954,-973,1000,653,-1000,-688,-698,-471,1000,-10,-470,-1000,-1000,-132,-998,-355,139,1000,1000,-382,-532,-1000,825,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNull():void",
            new int[]{346,916,-462,-1000,513,-818,240,-1000,149,811,215,-1000,118,431,-573,12,-132,-119,461,425,293,-315,309,637,951,1000,-378,1000,-152,918,92,648,-952,570,162,150,-889,-914,584,341,283,-797,148,-451,-575,-312,265,269,204,-383,998,-499,1000,1000,-535,1000,1000,776,-438,-464,-53,-518,653,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(double):void",
            new int[]{286,346,459,-320,76,-276,349,-857,547,131,-538,485,-26,-669,359,-734,547,-282,-237,33,627,265,97,-677,503,817,198,997,-32,152,-431,891,-23,311,-590,12,931,-372,-132,-384,712,-378,404,-644,521,431,-978,-616,-259,-135,183,851,618,265,698,490,-656,-613,-390,180,331,288,144,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(float):void",
            new int[]{-789,1000,78,-33,-168,672,159,-1000,-259,-264,198,1000,363,-767,227,1,236,299,-883,1000,614,-286,-1000,897,-891,181,290,366,-214,-710,41,98,724,240,-652,785,770,-773,-228,411,348,126,1000,460,-387,297,415,1000,303,80,390,734,-201,-711,23,-880,-920,207,-460,1000,-643,-66,147,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(int):void",
            new int[]{207,-59,-931,610,-698,-484,-618,-837,-623,934,-555,459,992,-509,-963,733,872,-185,838,734,-959,-537,-526,879,-917,910,-856,-67,444,-473,261,-903,463,536,-935,-375,498,-453,400,-320,934,-728,-996,956,136,-740,525,-182,461,107,267,-807,362,477,-863,242,-726,506,684,964,-487,651,249,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.lang.String):void",
            new int[]{421,1000,-831,527,1000,989,1000,-13,1000,-115,-1000,-406,-257,261,-578,-577,1000,-987,1000,1000,862,-770,633,583,1000,420,-747,318,394,182,-959,439,223,-90,-323,1000,336,1000,-1000,-550,-669,677,344,-192,348,-1000,-95,-930,306,-862,-621,-243,1000,-433,-694,-1000,6,-430,-1000,1000,-1000,322,715,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigDecimal):void",
            new int[]{-234,-383,639,-558,282,1000,687,831,-967,1000,1000,1000,-851,-63,-720,956,-1000,-1000,-452,1000,31,332,52,-326,-159,711,1000,-723,-1000,527,81,633,934,210,-1000,-815,784,1000,28,-476,-758,1000,910,267,-402,453,-417,-44,-1000,838,375,-604,-891,-856,762,984,317,-1000,-1000,-1000,1000,-1000,360,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigDecimal):void",
            new int[]{1000,526,603,-658,513,931,645,-1000,484,-1000,1000,-985,-263,-226,-1000,-382,606,1000,1000,-812,-53,642,-197,1000,-477,-341,-7,672,-435,105,-1000,684,663,-364,-1000,-1000,-1000,-404,49,1000,-1000,899,-417,-994,-996,-535,45,-514,710,1000,-1000,-348,76,-279,1000,-389,269,447,-615,310,-747,29,15,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigInteger):void",
            new int[]{702,-59,108,-327,54,-585,67,-1000,583,-232,421,315,-73,865,-261,927,566,-1000,-288,141,-181,868,200,284,-645,-63,-153,-388,113,-1000,194,267,-2,906,-59,-301,-710,-313,78,-320,743,589,-1000,-39,-580,-349,-272,-811,-225,4,66,-60,503,-77,102,-1000,-294,-98,108,835,126,-399,182,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(java.math.BigInteger):void",
            new int[]{745,1000,-716,1000,656,-1000,-75,542,-672,1000,-1000,-340,641,-781,-689,-1000,-1000,1000,1000,4,1000,-1000,-844,1000,94,-1000,1000,572,-967,-542,-137,-1000,-513,478,-858,1000,1000,993,-990,1000,-716,390,-616,1000,1000,-858,-942,550,1000,512,-696,-984,648,589,-11,1000,677,-913,141,968,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(long):void",
            new int[]{-103,-620,259,-1000,-320,-24,140,809,-724,1000,564,657,-689,344,-1000,-161,380,375,149,-1000,-1000,647,87,700,-521,-1000,-946,1000,-722,-85,3,-832,113,107,-581,-1000,-250,-657,-593,-1000,-438,-1,-1000,202,1000,1000,-765,-1000,929,886,-545,-124,518,-1000,1000,269,-931,-1000,-991,-604,-52,-401,1000,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeNumber(short):void",
            new int[]{1000,1000,633,-600,221,-1000,-1000,-121,1000,1000,-1000,1000,1000,-1000,-1000,1000,105,1000,1000,-203,-1000,-1000,-1000,-328,861,-704,-682,-895,-1000,554,-361,1000,-1000,-340,-1000,1000,644,644,15,-518,-676,-1000,672,-400,1000,1000,-552,1000,61,-1000,-509,1000,1000,278,-276,52,-411,459,-396,329,1000,-939,-226,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObject(java.lang.Object):void",
            new int[]{-620,1000,-872,-1000,-978,1000,348,1000,-1000,1000,1000,1000,-591,971,-1000,-626,1000,-1000,1000,-893,855,1000,1000,-1000,-1000,749,1000,33,-911,-1000,-1000,259,-590,1000,-1000,941,-217,-887,959,1000,232,-1000,-650,1000,1000,-1000,1000,-588,1000,616,-367,-840,457,-107,-1000,240,191,1000,-1000,-736,-1000,-885,419,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObject(java.lang.Object):void",
            new int[]{471,907,-691,-320,204,-663,-1000,-202,-291,-10,-869,-273,-747,629,169,448,-1000,704,-186,1000,13,542,-563,-118,-603,578,746,181,576,-923,287,832,-701,-11,-32,994,-1000,-395,-1000,500,703,1000,-537,-764,372,377,46,-415,1000,-1000,6,681,524,-413,1000,-19,140,477,-1000,454,153,769,306,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeObjectId(java.lang.Object):void",
            new int[]{252,700,499,-208,-558,114,304,357,558,-743,274,-560,308,-942,-517,576,-129,809,850,-937,930,367,459,-563,-800,-210,203,-198,231,-571,-176,-508,-481,221,251,132,-80,906,810,-886,222,301,365,736,-664,336,734,-181,-968,-413,-172,-513,407,-725,-121,958,-677,301,854,716,-34,785,679,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char):void",
            new int[]{-531,226,38,-797,-278,170,22,-1000,-1000,414,-1000,-1000,-623,-594,301,-336,-15,-274,2,-193,1000,-512,-404,997,1000,-823,-1000,-402,1000,330,68,660,125,178,-839,676,640,-969,-781,1000,-907,-1000,431,391,-723,-728,-367,1000,309,883,-754,1000,1000,114,-444,495,-1000,-673,475,-337,810,134,1000,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(char[],int,int):void",
            new int[]{394,-209,408,-446,-255,278,-1000,381,-1000,872,1000,-363,262,-85,-1000,237,82,339,-484,65,-1000,-178,-969,-137,1000,441,603,1000,-1000,-922,123,1000,-264,619,-970,30,1000,1000,410,-134,415,1000,-459,764,936,-1000,-1000,784,984,-114,-592,522,692,232,-280,115,-1000,-585,-1000,-359,-1000,-387,-558,570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{521,49,579,-964,-485,-481,206,577,-696,267,-541,-195,-1000,335,-54,-942,-564,-211,468,408,591,709,382,456,-335,-596,-442,130,-707,-123,542,-377,-537,-660,1000,-1000,769,533,730,560,1000,832,519,860,-1000,-695,191,-530,-314,185,-961,-474,108,117,193,810,128,-1000,611,920,-727,235,82,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String):void",
            new int[]{-1000,-956,273,587,-880,-1000,1000,-1000,211,-1000,1000,-737,-5,-1000,-177,1000,-113,588,-442,1000,717,-794,198,1000,616,-1000,-1000,257,-438,-1000,-1000,34,758,30,1000,-228,-1000,1000,-53,-250,1000,-890,-1000,-1000,1000,1000,-1000,-173,-1000,-1000,-192,-35,-1000,-559,1000,939,1000,-1000,833,261,512,-954,920,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRaw(java.lang.String,int,int):void",
            new int[]{269,-179,-796,880,-790,-245,-516,-1000,1000,1000,419,-284,676,1000,307,-824,-1000,-1000,1000,-208,1000,-375,-734,-1000,775,1000,-680,469,124,199,-438,-973,1000,1000,42,689,1000,-428,-830,-53,-725,1000,-1000,7,337,566,697,-650,953,-688,-315,-1000,981,-1000,-704,475,804,-1000,-1000,467,297,-848,-774,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawUTF8String(byte[],int,int):void",
            new int[]{992,862,-811,-737,-341,-943,991,489,694,-890,397,-694,136,-340,257,846,444,-325,560,-838,-389,-282,247,842,18,-908,330,-590,-135,-398,497,-673,-314,-580,-702,645,-809,844,71,227,-524,-375,808,879,506,979,321,-341,-290,408,-715,-786,480,-12,974,-947,-537,953,850,-647,598,-934,578,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(char[],int,int):void",
            new int[]{927,799,-191,-517,-210,-544,-897,-598,993,367,-815,100,-906,991,-723,326,915,264,-817,6,315,673,-46,242,970,354,927,-65,-250,-298,220,933,431,-890,533,-358,440,950,-275,429,67,-334,47,824,171,931,-859,504,779,235,-47,-626,-826,981,-436,661,95,175,615,876,467,167,-540,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String):void",
            new int[]{-47,-437,549,135,1000,-949,1000,-420,-1000,-811,241,596,1000,1000,127,-408,-985,-757,-374,-731,555,-125,515,-50,-119,-403,-537,-222,1000,-1000,565,1000,996,-436,1000,468,-392,-389,-948,-14,-568,-445,1000,515,154,105,-1000,1000,114,-1000,-1,-1000,643,131,-359,303,746,543,328,-20,286,-221,-135,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeRawValue(java.lang.String,int,int):void",
            new int[]{155,-92,-982,399,-922,591,115,393,509,53,-964,-551,143,8,456,327,506,843,-181,-494,378,-479,287,-970,865,-477,408,-622,977,-82,-443,-425,162,312,493,389,-261,368,-510,-726,-609,898,393,-78,990,76,-295,790,-129,-739,-667,-244,-592,100,-14,185,226,123,-632,-875,721,-121,-679,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartArray():void",
            new int[]{-1000,1000,-516,-1000,167,-943,525,-38,-722,249,-1000,-446,-458,-1000,607,607,578,128,-152,-391,-873,31,-752,1000,1000,-1000,-650,1000,225,642,-656,1000,-433,-40,910,-227,-242,1000,745,-951,768,1000,840,-36,580,1000,-84,-1000,-1000,250,78,1000,-420,-920,-1000,-1000,-245,764,-1000,519,832,-290,833,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeStartObject():void",
            new int[]{-1000,721,-707,-1000,1000,450,1000,-227,-730,11,-1000,1000,-584,-41,-533,170,-955,-324,-1000,-1000,-1000,369,-847,-1000,146,-730,841,1000,-1000,520,1000,521,-1000,-781,856,861,348,365,697,-859,6,108,1000,-1000,-1000,-235,-269,-79,-159,1000,-168,-1000,-1000,930,-811,966,-1000,647,-1000,-620,-1000,673,476,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(char[],int,int):void",
            new int[]{670,145,663,-695,-138,679,-196,400,-737,-165,654,-798,758,276,907,-944,39,-789,854,283,1000,-23,1000,-1000,109,744,576,-662,409,-595,-1000,-770,373,69,81,388,1000,412,15,-1000,-263,958,843,1000,872,-356,183,-463,-18,515,997,1000,-1000,1000,-26,-840,-869,127,-1000,-514,-1000,-374,789,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(com.fasterxml.jackson.core.SerializableString):void",
            new int[]{-246,1000,672,-12,1000,-331,169,75,-469,-733,856,1000,656,296,-794,-38,246,442,-659,1000,373,-704,-1000,960,463,-149,-1000,1000,680,-960,-771,789,-364,-462,-171,-774,176,159,821,526,-73,1000,477,225,-39,473,-96,334,35,-241,-115,1000,915,-772,1000,-338,349,-280,492,303,651,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(java.lang.String):void",
            new int[]{721,733,228,344,-621,-1000,-485,-1000,640,553,134,1000,-606,150,393,391,-62,504,-1000,-610,-804,-431,753,-387,-572,-118,-126,-684,1000,425,581,-162,1000,928,-635,-684,783,316,-1000,-383,1000,1000,-128,539,-620,541,1000,732,-596,-1000,1000,286,-558,680,-818,-1000,-1000,-737,-11,-884,515,404,-220,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeString(java.lang.String):void",
            new int[]{-976,-209,-22,-592,930,473,926,644,759,337,805,-808,186,-364,-145,499,-21,-152,-708,-611,35,-792,-171,313,639,44,987,756,191,265,-253,-165,575,-363,-127,448,-633,314,982,-779,162,530,-645,-79,444,281,362,-991,616,418,-609,594,97,-390,191,131,697,-655,683,-55,-109,-691,441,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTree(com.fasterxml.jackson.core.TreeNode):void",
            new int[]{1000,1000,672,-1000,-369,1000,-1000,-3,-473,-1000,-80,-924,29,315,-1000,-153,1000,-1000,-376,-55,-652,-583,-587,-104,383,117,-1000,209,-540,961,1000,1000,-451,-342,836,-1000,-184,1000,-1000,-977,-474,-1000,-617,367,-1000,-555,-17,-248,1000,-463,-323,72,429,463,646,258,1000,121,-729,160,91,979,423,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeTypeId(java.lang.Object):void",
            new int[]{-435,-29,-406,254,841,153,-529,657,454,849,-400,-308,729,-55,117,-91,-671,831,-959,-391,912,-553,987,-903,937,505,883,243,-21,920,-350,-409,-107,336,-385,179,265,-961,-732,-361,730,527,-70,171,-61,-475,843,-472,-783,333,273,-695,307,-923,333,-519,-42,610,362,443,-413,636,1,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "com.fasterxml.jackson.databind.util.TokenBuffer", "com.fasterxml.jackson.databind.util.TokenBuffer", "writeUTF8String(byte[],int,int):void",
            new int[]{-491,286,-218,36,870,-2,-742,296,422,41,-300,-185,424,-684,-600,-948,-642,-577,773,385,-527,-158,592,-96,420,190,389,-251,-769,117,921,-693,-304,-501,-401,63,-217,922,504,-344,173,-843,-810,-208,208,303,880,66,-409,587,822,-561,-983,-555,-370,-817,614,-598,197,-616,-492,561,464,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.TokenBufferDeserializer", "com.fasterxml.jackson.databind.deser.std.TokenBufferDeserializer", "deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):com.fasterxml.jackson.databind.util.TokenBuffer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
