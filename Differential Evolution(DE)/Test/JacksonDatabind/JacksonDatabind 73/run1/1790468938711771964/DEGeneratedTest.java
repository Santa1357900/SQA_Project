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
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addAll(com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder):void",
            new int[]{817,498,-765,-308,-139,-1,501,-609,535,601,-249,853,-802,599,598,-920,22,817,118,677,178,727,48,-847,573,771,-412,343,-46,454,775,298,-419,538,186,374,348,216,-203,-465,219,552,-760,-135,-841,-336,-603,201,-644,-873,-676,390,467,914,785,-761,853,-192,-601,437,344,-316,283,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addCtor(com.fasterxml.jackson.databind.introspect.AnnotatedParameter,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{659,465,1000,-210,859,819,-363,-143,399,677,-874,-545,-1000,-96,1000,-223,870,686,-1000,1000,436,1000,1000,-867,-880,1000,452,956,1000,83,402,579,212,624,-1000,1000,-1000,-144,860,-924,-6,-1000,799,-153,339,1000,132,319,-856,-202,-23,1000,-308,-475,-20,-1000,-1000,-28,775,447,-1000,300,-895,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addCtor(com.fasterxml.jackson.databind.introspect.AnnotatedParameter,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{341,243,247,978,-385,-518,-294,-23,209,718,-538,690,-477,539,416,583,-673,666,-986,881,-1000,1000,1000,766,-1000,-234,-166,1000,939,-406,-740,641,876,-550,-64,1000,-281,-568,928,-446,292,44,789,-111,-305,1000,124,-449,-628,196,458,-242,-23,-591,-312,-522,-976,-719,-121,138,-982,-18,-586,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addCtor(com.fasterxml.jackson.databind.introspect.AnnotatedParameter,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-811,282,-675,-836,116,-124,500,16,997,-641,-723,646,-813,640,-701,-752,390,-747,594,570,-666,-577,772,-463,460,-471,-540,-261,-435,549,-226,923,-18,-188,-495,885,651,-313,-226,-960,209,-248,-51,-672,-900,-635,786,-911,629,-365,949,98,-137,-712,-673,-3,338,89,916,-915,645,167,-599,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addCtor(com.fasterxml.jackson.databind.introspect.AnnotatedParameter,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-303,255,131,204,-862,235,-351,-813,895,763,101,-1000,-770,-1000,-996,-39,-948,-773,633,50,-135,815,-305,657,-576,746,-309,-1000,-445,-634,1000,697,-1000,-460,-48,-1000,193,1000,662,-73,-91,105,542,-44,461,871,111,-768,-243,-519,963,399,-428,541,1000,-343,-393,-852,-620,-347,416,-94,-271,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addField(com.fasterxml.jackson.databind.introspect.AnnotatedField,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{91,-458,-289,-1000,1000,-761,-988,-1000,-141,-702,-1000,-497,-4,338,101,1000,319,1000,-1000,408,341,670,-3,-443,-1000,-1000,-340,-970,-779,164,1000,542,1000,831,1000,1000,-453,1000,969,138,-428,-367,-134,-1000,1000,52,848,-65,1000,506,-1000,1000,-313,290,1000,1000,610,677,-484,549,-611,194,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addField(com.fasterxml.jackson.databind.introspect.AnnotatedField,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{735,-141,-588,-570,5,-523,-308,-617,878,411,-781,-224,413,268,-24,-272,515,724,-66,-31,-45,-1000,-678,-207,824,-76,-122,-453,-254,-464,297,192,990,9,-1000,548,-182,-400,-482,-256,66,406,-3,-356,-488,700,595,157,295,327,601,11,4,-608,-79,166,322,88,433,176,938,-260,953,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addField(com.fasterxml.jackson.databind.introspect.AnnotatedField,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{875,280,295,-1000,-335,-19,466,-524,429,40,493,-37,857,-875,365,-486,-120,799,905,988,74,-1000,-1000,377,954,-76,709,703,-1000,-478,56,530,-466,-683,509,-629,-1000,13,-536,74,445,1000,726,149,-1000,519,885,210,-166,871,1000,11,360,-1000,57,-963,-7,984,-168,-589,-359,-8,1000,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addField(com.fasterxml.jackson.databind.introspect.AnnotatedField,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{322,-577,1000,1000,212,-405,1000,-106,136,564,1000,390,207,287,65,1000,-51,-1000,-512,-561,-1000,1000,-184,-596,981,257,-585,933,690,468,329,318,-482,-647,370,849,-125,-841,-738,-338,-166,-1000,-1000,806,-1000,-1000,-168,937,-847,-592,613,-1000,-164,652,-241,1000,1000,1000,-798,348,912,141,-1000,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addGetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{967,199,-1000,-230,297,376,33,-471,-691,-1000,430,539,-379,-829,-740,-190,537,434,-861,868,1000,1000,325,-18,-557,-405,955,-775,-898,-325,654,-241,350,569,383,-504,-1000,1000,-222,995,-1000,1000,-955,-1000,-569,-938,815,291,876,239,195,-655,-632,-957,-1000,1000,790,-869,21,-275,-296,-227,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addGetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-483,1000,409,592,-1000,-34,-1000,6,1000,771,-1000,-1000,-1000,922,288,487,90,-851,-190,-457,-1000,89,1000,-36,241,-910,-944,-292,1000,-213,-627,438,-443,-656,-324,-746,1000,345,1000,-433,-583,783,1000,408,289,-748,-141,-820,893,1000,-1000,2,-703,940,1000,-1000,1000,-204,1000,-603,-191,-403,-369,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addGetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-470,-745,882,-500,-749,806,74,-577,-541,296,-886,-532,42,447,672,672,-607,830,213,-327,-740,901,645,93,588,-94,-620,-182,-538,497,519,982,722,-282,109,287,-657,350,717,-342,249,-477,935,-834,-134,-45,-172,-314,-561,240,-125,-227,890,176,436,-92,839,-707,-554,-572,-714,-532,-328,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addGetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{585,-995,-259,327,-1000,-845,1000,529,-1000,-619,985,1000,-77,-39,898,-37,943,406,-647,295,1000,763,33,-818,-171,-351,-151,-141,-552,658,-409,-1000,136,701,-952,-731,-660,158,3,579,-655,873,-1000,-264,50,-873,744,-525,-702,-1000,1000,251,356,343,-1000,1000,298,-650,-1000,-514,-1000,-889,-1000,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addSetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{347,-260,477,614,440,-200,95,460,585,-355,-966,-180,658,-562,-1000,-144,1000,499,1000,-113,810,1000,-320,1000,-618,-34,557,499,603,-175,-128,462,840,1000,483,139,982,-818,534,-304,-1000,555,-398,1000,-1000,361,779,335,518,-35,-413,807,-762,558,593,529,144,-766,42,106,-23,-128,-20,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addSetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-612,-1000,-471,910,-13,-974,-1000,-1000,667,251,-317,-602,-1000,-971,1000,-1000,1000,-377,-1000,402,373,-271,535,-821,-908,-133,-695,870,380,255,-607,-726,812,-637,-1000,1000,779,-24,-543,665,1000,-139,-1000,-1000,1000,143,-1000,-1000,493,1000,1000,-1000,1000,462,753,-547,-1000,-167,-1000,1000,80,646,1000,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addSetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-652,-921,-961,1000,-616,-998,214,818,-97,156,-488,21,260,-453,421,-620,1000,-355,-445,535,213,167,-49,-428,-1000,43,1000,860,-1000,97,-835,1000,1000,189,180,-1000,434,-87,-299,-335,-1000,865,-675,285,-1000,1000,-434,-38,94,-137,382,-60,-306,55,46,533,-961,-1000,-422,-1000,-747,401,174,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "addSetter(com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.PropertyName,boolean,boolean,boolean):void",
            new int[]{-1000,705,-90,169,468,884,999,401,834,-597,-571,-426,1000,142,-951,-192,1000,967,1000,-1000,1000,1000,145,-96,-516,-449,689,-365,1000,68,-480,8,945,1000,1000,-919,-42,-1000,-37,667,-685,993,-172,1000,-1000,-205,943,293,931,-1000,-1000,1000,-920,892,1000,699,193,46,695,-129,68,-1000,-854,-252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "anyIgnorals():boolean",
            new int[]{116,592,1000,297,-964,-574,-1000,895,824,801,-27,-664,1000,-765,-995,-1000,-428,824,-1000,-891,1000,721,1000,465,158,769,1000,1000,360,168,-971,-940,-30,369,-793,-88,-709,-335,1000,1000,944,157,1000,-61,819,-971,-404,775,-663,-1000,129,1000,796,725,-160,-436,16,700,556,1000,-469,-1000,-408,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "anyIgnorals():boolean",
            new int[]{1000,373,788,-502,-66,461,-707,248,400,-1000,-677,194,-739,1000,-715,-981,-552,-398,-400,575,-492,779,452,1000,-306,51,1000,142,68,294,150,664,154,815,-927,941,1000,-1000,-972,741,558,-881,26,4,608,-310,-1000,-426,-711,-775,-1000,869,775,601,-342,1000,-1000,338,-39,-1000,92,171,522,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "anyVisible():boolean",
            new int[]{1000,655,549,-648,568,409,364,484,-951,-395,-718,-1000,242,-95,-231,907,-54,-467,-394,-849,399,832,435,-691,-990,399,-728,911,-449,-365,364,364,1000,-99,-696,-610,41,-448,-363,583,105,440,-345,1000,683,-329,406,516,744,644,1000,-11,483,287,-275,480,506,-81,-376,1000,209,32,935,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "anyVisible():boolean",
            new int[]{1000,1000,-749,673,-634,501,558,78,304,111,159,-558,213,49,-236,-958,619,162,1000,828,-580,-1000,-267,-1000,-193,1000,1000,828,-751,743,201,-717,-1000,-123,133,726,-419,-1000,-273,-708,940,-994,-51,840,-580,1000,-748,-358,-781,-8,1000,297,224,-1000,607,-1000,-71,-222,-245,-1000,1000,1000,605,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTc4", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "compareTo(com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder):int",
            new int[]{865,-1000,-27,905,-82,450,-1000,-1000,809,-506,-1000,-586,142,-895,-281,173,599,-894,-840,-170,-719,-605,698,-297,497,-187,-362,1000,40,51,722,-789,294,255,157,-849,197,52,638,-269,-1000,-904,-108,347,359,-213,277,591,169,-1000,-1000,158,260,-113,100,199,-485,215,-300,244,327,-337,64,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "compareTo(com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder):int",
            new int[]{-1000,744,45,-1000,-641,772,52,1000,532,551,-472,284,631,808,-539,369,427,-225,-726,809,-924,-28,-593,202,-103,1000,351,796,-166,412,-398,-810,1000,-1000,-315,908,1000,75,483,1000,513,791,-1000,175,1000,461,-1,175,1000,361,-75,-1000,-395,-626,1000,-557,-377,469,-439,469,839,-578,64,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "couldDeserialize():boolean",
            new int[]{-663,-765,746,1000,390,-810,101,685,414,692,656,-740,-3,731,80,187,-183,-130,-1000,111,503,-687,-618,-348,631,79,-697,984,26,839,-51,-11,-534,60,-397,-233,-438,1000,948,-1000,-787,-577,-816,-1000,-228,304,325,-614,-992,712,1000,402,-43,35,-622,1000,-230,619,580,-53,-999,1000,-847,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "couldDeserialize():boolean",
            new int[]{182,-1000,-323,1000,-1,-584,-708,322,-441,197,340,910,-496,495,112,765,-707,-754,-844,-1000,642,-241,-399,-556,189,793,-782,1000,-850,867,-964,615,-1000,346,-75,-336,-1000,-106,-746,-764,-879,-983,-1000,-922,213,137,759,761,18,-761,-16,1000,-1000,-1000,556,1000,181,81,258,-1000,-187,923,587,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "couldSerialize():boolean",
            new int[]{53,820,995,1000,205,-819,489,-322,1000,-342,930,1000,-396,244,1000,-896,-221,30,-358,1000,-196,719,-847,-610,508,963,-516,848,769,464,-1000,-384,-290,464,-271,412,-385,1000,-475,382,515,1000,1000,588,-829,-537,-63,-742,159,-1000,306,934,787,61,-366,-497,-309,-81,-159,275,-952,-1000,-723,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "couldSerialize():boolean",
            new int[]{-475,-726,-941,343,-214,529,822,915,-31,813,108,213,586,703,1000,-314,100,-15,514,-511,1000,-1000,-806,56,-253,760,-952,-341,296,438,725,1000,-1000,466,-1000,745,783,-720,1000,-592,-1000,-587,-66,858,653,275,-922,999,651,-467,-469,475,-1000,-13,-160,-195,374,-519,-531,786,764,588,-1000,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:java.util.HashMap$Values", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "explode(java.util.Collection):java.util.Collection",
            new int[]{613,1000,1000,199,1000,-1000,-178,554,697,-1000,-205,509,-695,-960,67,1000,-1000,-1000,1000,1000,263,565,1000,1000,-495,-1000,675,-150,-54,370,-242,-568,1000,1000,1000,-430,59,500,-1000,-294,-519,2,-1000,802,4,-15,267,891,76,1000,-693,-1000,384,1000,-1000,391,1000,-272,432,15,53,1000,-587,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:java.util.HashMap$Values", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "explode(java.util.Collection):java.util.Collection",
            new int[]{-381,384,-308,145,-1000,822,-445,-592,-783,561,-1000,-484,953,-1000,-35,-794,1000,582,-1000,168,-874,-1000,-458,728,-357,-126,-435,660,-293,862,-440,393,-793,-692,-1000,-797,-1000,235,-769,224,0,905,219,-227,334,806,-601,-115,644,-607,351,-808,873,-1000,842,621,232,940,340,-17,-225,-229,-522,-926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findAccess():com.fasterxml.jackson.annotation.JsonProperty$Access",
            new int[]{816,522,-266,875,-696,831,-65,445,84,719,-33,-662,-114,592,-941,-328,-991,-687,-609,-874,189,-368,-374,818,416,398,567,114,-768,-591,607,500,113,-273,-387,675,812,-179,-194,567,-253,-10,-694,-886,902,173,93,-660,-942,-509,-702,504,649,-374,-986,916,-945,513,-925,389,718,-307,-367,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findAccess():com.fasterxml.jackson.annotation.JsonProperty$Access",
            new int[]{1000,1000,-514,663,-1000,630,-20,354,-725,-438,-473,-968,-252,-631,622,-111,-223,-162,-132,-185,369,-860,-1000,-659,1000,273,-168,-1000,37,-227,1000,179,-1000,-767,-502,-195,1000,-567,97,-2,-462,1000,-235,127,-353,-333,-670,-1000,-624,-987,-770,-610,1000,240,-214,-321,85,147,-576,88,-198,-883,1000,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptySet", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findExplicitNames():java.util.Set",
            new int[]{989,-951,201,1000,314,-347,-678,999,-896,162,671,-1000,1000,309,219,-717,-198,-999,377,-77,-978,840,306,-447,559,822,763,1000,351,783,-1000,19,1000,118,-826,-103,736,-585,-309,1000,133,-34,1000,-17,-238,579,1000,-52,-785,308,-762,657,-456,-1000,1000,-908,-747,442,-348,752,-1000,19,1000,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptySet", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findExplicitNames():java.util.Set",
            new int[]{-336,-546,302,-581,128,-96,273,625,-1000,250,924,269,-449,-60,-391,1000,205,519,652,-817,873,-498,-909,-34,152,721,467,1000,1000,1000,1000,1000,828,-1000,-633,82,-1000,-446,-301,-589,586,361,150,1000,-1000,-886,-747,-393,-412,-682,275,-247,-319,357,-377,-886,-298,-1000,-965,715,282,-1000,86,805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.annotation.JsonInclude$Value", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findInclusion():com.fasterxml.jackson.annotation.JsonInclude$Value",
            new int[]{-42,452,-117,833,-915,667,899,-1000,-722,-278,-318,294,573,864,-402,718,-297,150,3,454,-1000,-59,366,592,-303,-1000,-875,-638,-365,-194,-821,947,467,824,960,181,-537,313,386,-485,-338,-463,-491,-1000,-74,507,-1000,174,-576,-512,415,-32,533,-655,-765,2,179,1000,-346,-1000,1000,440,51,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.annotation.JsonInclude$Value", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findInclusion():com.fasterxml.jackson.annotation.JsonInclude$Value",
            new int[]{-783,-752,277,677,28,-49,976,-457,-1000,256,908,-1000,826,-29,-251,-469,249,334,421,-1000,-758,-221,-1000,45,-376,480,1000,-187,-920,904,-218,1000,641,62,-762,-542,-53,-167,83,1000,-837,682,105,244,-216,-222,812,54,794,-68,124,1000,218,419,-1000,-395,-143,955,-146,-279,45,-816,-490,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findObjectIdInfo():com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{1000,-1000,-177,-479,1000,-216,895,283,-358,-196,-1000,1000,732,-601,319,771,-694,337,532,-799,-843,586,-1000,-42,149,505,326,959,474,317,-467,724,-1000,692,-109,-1000,-1000,-692,828,-226,1000,1000,-1000,1000,-462,-652,-1000,-816,-893,414,1000,142,1000,-1000,-1000,-525,-871,122,-1000,-176,432,1000,210,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findObjectIdInfo():com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{-944,-774,-287,-87,-611,-769,-1000,1000,-1000,-355,355,295,1000,-65,-1000,884,-818,-1000,159,-891,1000,-608,-1000,-1000,486,579,-754,-272,-360,-1000,1000,-1000,-373,1000,-174,228,-665,-119,1000,-241,-1000,-931,-229,-1000,38,218,172,-84,206,857,-501,637,-1000,-415,693,-1000,-1000,560,65,-379,-49,-512,1000,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findReferenceType():com.fasterxml.jackson.databind.AnnotationIntrospector$ReferenceProperty",
            new int[]{380,588,287,909,-110,-1000,1000,-260,17,1000,884,-1000,-651,124,1000,136,-1000,-533,698,-1000,-254,-1000,287,-1000,536,125,-854,-1000,582,-817,-1000,937,427,-615,-1000,168,-1000,-521,419,170,-166,257,840,-1000,1000,355,-313,645,-14,-1000,-477,-682,519,-284,-1000,289,-839,1000,-103,225,1000,-465,292,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findReferenceType():com.fasterxml.jackson.databind.AnnotationIntrospector$ReferenceProperty",
            new int[]{-343,-898,-301,612,-561,777,-92,-159,257,-926,682,558,905,970,176,-441,685,-668,-24,-24,-835,753,123,957,470,888,-864,903,-157,-821,-539,-652,-595,-796,873,87,-428,59,-211,-546,421,146,-800,812,-539,-446,0,-747,711,797,-174,-743,766,112,231,656,641,737,-993,604,-456,-381,-240,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findViews():java.lang.Class[]",
            new int[]{-191,787,-295,941,962,173,-231,259,-1000,-436,754,367,788,-590,602,284,-170,1000,-383,-192,-272,120,258,538,394,-464,582,-60,1000,830,259,406,-209,-565,-199,-644,910,22,-567,754,-474,-212,-165,-697,-408,452,570,-507,622,615,-34,563,66,-790,725,-358,764,1000,-1000,-666,-1000,1000,408,-819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "findViews():java.lang.Class[]",
            new int[]{1000,1000,167,-516,-1000,-1000,956,675,1000,819,-1000,1000,-926,1000,-245,-299,1000,-1000,-691,-803,599,243,607,155,-716,531,-1000,-921,443,-1000,972,1000,725,-854,1000,1000,-245,-1000,575,-1000,-976,-517,-422,905,-1000,957,-328,605,31,649,646,1000,1000,-1000,884,1000,537,-1000,1000,781,1000,-1000,72,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getAccessor():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{10,-461,-286,-617,-844,-689,-1000,-882,115,611,180,11,-1000,-369,610,-486,154,611,-666,472,-332,883,-46,712,-1000,1000,-353,703,42,399,320,626,1000,735,-681,50,659,221,193,605,1000,-971,633,222,-431,-319,21,214,-643,-370,-254,427,-652,-733,-493,-558,803,789,-673,-724,-706,-820,-875,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getAccessor():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{1000,-201,63,-512,933,-689,186,108,-21,-137,-785,114,-1000,-1000,1000,-209,1000,1000,-19,-735,317,-776,-1000,607,-14,932,-1000,-4,-407,-63,1000,1000,108,1000,-394,-493,530,650,-813,49,-1000,-430,705,115,1000,-975,-539,1000,756,-370,-1000,-513,-1000,-45,954,-889,56,757,-1000,-571,-253,612,-566,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getConstructorParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{815,-881,257,1000,-125,-801,-372,-681,688,-1000,-764,-129,-681,-843,1000,213,-331,-497,-376,-700,-1000,-117,371,919,-23,-192,-538,-1000,143,-619,214,-631,827,754,-433,-175,-775,722,-175,376,535,-427,-481,-310,-495,-1000,-1000,-587,-596,1000,-329,539,176,-575,102,1000,1000,-737,-735,46,455,-66,285,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getConstructorParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{1000,1000,173,-499,-1000,-617,1000,189,449,1000,-352,-146,876,1000,-686,-1000,-473,-111,686,39,-225,536,1000,-29,-1000,1000,-1000,-678,-696,-111,1000,1000,1000,-713,-856,97,565,1000,-1000,-999,-1000,-474,11,-1000,-1000,-446,797,344,-29,-107,1000,1000,1000,-1000,712,1000,246,-143,516,77,1000,-943,-395,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.ClassUtil$EmptyIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getConstructorParameters():java.util.Iterator",
            new int[]{-1000,1000,-568,-977,-1000,-18,698,262,348,-652,853,766,-1000,1000,584,606,587,-58,-912,-908,-760,-5,1000,93,633,-271,1000,610,-595,726,-223,-68,-1000,611,1000,-711,-1000,-897,130,959,-1000,733,-165,1000,-309,-869,1000,-542,-59,58,343,-645,222,418,26,1000,1000,-1000,-490,-786,1000,186,337,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.util.ClassUtil$EmptyIterator", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getConstructorParameters():java.util.Iterator",
            new int[]{-473,172,-857,500,848,249,-152,289,-782,918,-1000,998,-126,-21,-912,-1000,655,873,45,923,586,-196,-357,954,-536,-533,-482,-137,127,-1000,-324,181,645,-65,-298,-613,500,988,-604,-65,1000,53,-1000,-414,-583,-1000,569,410,247,-182,-226,1000,1000,-191,370,337,-78,177,-1000,545,-1000,881,297,-502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getField():com.fasterxml.jackson.databind.introspect.AnnotatedField",
            new int[]{295,-346,545,1000,1000,1000,-1000,777,-1000,12,-1000,1000,-1000,639,579,-133,447,30,-297,-802,-532,-105,-1000,523,-1000,1000,393,912,959,385,1000,1000,-559,-848,-411,-251,358,1000,1000,1000,-222,-193,-379,-1000,-469,-897,-309,1000,34,702,-1000,762,-822,-310,-1000,-1000,630,-744,-151,-219,1000,258,33,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getField():com.fasterxml.jackson.databind.introspect.AnnotatedField",
            new int[]{850,324,395,273,738,252,-936,116,-924,28,-704,-622,-194,-196,-177,-482,440,-423,-718,-99,-455,195,-452,471,-418,731,133,916,548,291,726,940,-574,-714,-658,-842,99,599,937,667,92,-826,-134,-624,269,-894,-913,850,-563,495,-366,568,-285,31,-454,-740,471,-430,207,-661,655,-193,-205,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getFullName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{720,560,67,-440,-790,-1000,-722,-377,654,-421,679,1000,862,119,-440,1000,429,-312,-412,165,488,1000,-421,-232,-4,-14,-87,-1000,-1000,509,1000,-1000,45,127,1000,-802,-279,591,-157,537,208,1000,772,-25,1000,993,-215,-729,734,405,655,-256,-793,193,681,-1000,84,-1000,1000,-404,-419,-657,1000,-284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getFullName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-442,141,-597,-440,1000,-1000,125,1000,918,1000,-1000,807,-481,10,-1000,-1000,-1000,1000,1000,15,-1000,1000,6,1000,-431,-14,-87,690,-1000,102,287,-1000,739,-303,-1000,-802,438,-225,712,-679,-1000,247,1000,-1000,1000,-132,753,-216,-178,-1000,488,-948,169,-1000,-959,769,137,-1000,361,550,1000,912,1000,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getGetter():com.fasterxml.jackson.databind.introspect.AnnotatedMethod",
            new int[]{-1000,973,-1000,-632,-1000,1000,447,211,334,-1000,-1000,921,989,1000,-159,-785,1000,657,-1000,834,937,-237,1000,-853,737,667,-514,275,1000,-739,-365,711,-846,-704,-797,192,1000,809,1000,758,1000,1000,219,1000,929,-565,-1000,595,-414,980,741,766,-534,1000,313,-736,498,44,365,72,304,1000,53,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getGetter():com.fasterxml.jackson.databind.introspect.AnnotatedMethod",
            new int[]{-1000,1000,-368,-725,550,945,970,-249,293,-814,-975,345,268,-281,-984,-976,-381,522,-465,854,655,-237,821,-1000,346,746,270,743,393,-56,300,174,76,-995,-1000,-18,455,-226,510,819,1000,940,9,1000,11,-489,-1000,609,149,459,225,1000,-1000,398,-229,-358,-24,151,1000,-260,-361,493,-206,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:MHgzNDY=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getInternalName():java.lang.String",
            new int[]{739,-603,704,-364,-585,838,-168,-811,-845,-212,-267,460,-688,201,218,734,625,625,279,-382,-762,144,-987,-894,-326,-118,418,860,920,671,-424,-546,107,31,-389,-328,233,288,-709,418,-126,346,198,-770,-115,386,-53,-20,739,-372,129,-66,314,132,349,-979,-287,633,-974,-101,9,-637,-533,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:KzQyMmUtMjQ4", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getInternalName():java.lang.String",
            new int[]{-25,367,842,625,-714,422,-930,-248,-108,-882,236,-386,-153,-53,-955,298,944,-234,-455,989,-209,-643,-234,-337,15,977,-273,866,-363,-370,719,970,-985,399,662,688,-287,-204,-169,28,-857,808,-758,-648,-110,-918,623,-226,272,-494,30,729,-144,370,890,218,413,209,549,-36,-879,469,-761,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyMetadata", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getMetadata():com.fasterxml.jackson.databind.PropertyMetadata",
            new int[]{-169,745,197,1000,1000,-1000,-455,-819,-921,647,-139,731,420,-518,-276,44,-999,-824,-391,-1000,607,-15,-461,27,-429,174,-679,-260,636,-927,-137,-677,787,16,-250,313,802,948,-36,554,827,-615,-684,-494,-914,391,939,-526,-79,189,-4,-403,712,-63,-1000,-546,-1000,-1000,939,-1000,-710,500,1000,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyMetadata", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getMetadata():com.fasterxml.jackson.databind.PropertyMetadata",
            new int[]{958,433,-1000,-393,-1000,382,-564,-361,-1000,274,513,-17,-281,772,1000,647,406,-1000,-286,-400,-772,-595,1000,-755,576,556,-1000,-852,-54,-88,694,435,908,-315,878,-1000,-889,791,1000,-1000,904,-49,-145,-697,395,-1000,-336,-1000,-1000,-1000,715,-715,490,1000,-1000,26,-1000,-864,473,770,-149,509,-360,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getMutator():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{241,1000,-206,627,879,1000,-1000,556,-1000,603,-1000,-1000,339,1000,-869,-51,-62,244,657,-1000,596,698,-1000,-445,607,-1000,-265,439,-780,-1000,-484,467,-95,83,1000,97,1000,1000,859,-586,1000,740,231,571,-507,-320,413,855,-536,-526,43,792,1000,408,-374,-1000,604,-375,280,799,-797,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getMutator():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{615,206,-337,1000,241,1000,-108,-179,919,-578,-1000,-747,598,-475,-525,-614,-296,-373,574,898,599,872,-72,492,-1000,-1000,-485,-821,1000,-1000,689,968,636,-1000,-272,368,492,33,-1000,887,-946,-1000,-782,619,1000,1000,932,-800,-1000,318,721,-680,251,347,-1000,993,-634,-627,-1000,1000,95,940,248,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.String:LTI3Mw==", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getName():java.lang.String",
            new int[]{96,716,139,413,-751,-310,-32,-476,624,830,-1000,924,979,-273,573,-911,383,-660,-954,-713,259,152,508,287,557,-1000,-871,-188,-642,1000,-1000,-713,1000,-50,1000,-102,-1000,-1000,457,1000,-339,-460,-166,336,256,35,309,-696,935,482,-1000,606,391,-840,1000,-1000,-750,254,-704,-21,727,1000,-454,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.String:TDk5NytWNlk5a3Z1akFoSldUa1VM", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getName():java.lang.String",
            new int[]{821,819,628,971,-465,-645,-806,-504,544,-204,-133,-135,843,-369,148,-508,-488,-264,812,-609,516,-461,940,-310,-439,-939,-51,340,-379,815,-353,505,640,242,743,-529,-516,-966,348,471,214,-535,-464,308,426,-288,-773,316,574,-18,368,-160,-59,-714,674,181,-749,657,-986,730,-228,729,-22,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getNonConstructorMutator():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{-304,988,-801,-641,201,-611,-48,-153,-651,478,1000,-110,526,32,-646,-183,-180,-652,-943,692,650,303,-1000,55,605,262,916,489,611,1000,-864,-316,2,-695,383,-101,-812,-586,1000,396,-1000,-31,999,-998,-93,-1000,-501,-517,-120,-445,-1000,833,-660,1000,938,216,1000,-746,137,-845,1000,-1000,-515,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getNonConstructorMutator():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{107,-399,-1000,-1000,-1000,713,1000,77,-411,397,-692,-290,799,-1000,1000,-222,-1000,630,754,1000,-1000,567,-429,319,-1000,-545,899,1000,667,540,-1000,458,-875,426,757,-257,-350,1000,1000,-42,648,1000,288,111,854,-755,-961,244,1000,-543,903,1000,-1000,-77,-1000,-361,-235,702,1000,1000,-1000,479,-1000,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getPrimaryMember():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{107,289,-635,377,-522,-364,927,278,1000,1000,-30,160,-556,-865,667,-978,-480,286,-708,-407,-640,824,168,1000,-987,703,-936,443,1000,1000,-435,897,693,1000,-1000,469,-1000,778,-26,869,966,-693,-208,-1000,-2,662,-1000,-1000,-978,-1000,-54,-689,957,825,-1000,-850,820,633,175,653,769,43,-696,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getPrimaryMember():com.fasterxml.jackson.databind.introspect.AnnotatedMember",
            new int[]{283,-945,-810,875,827,42,-698,24,-1000,155,352,-1000,-222,1000,-1000,845,227,-1000,1000,-170,426,-1000,-907,-204,177,-643,-883,-726,-833,-1000,582,507,-1000,-1000,-1000,51,1000,-121,1000,-1000,-272,929,750,616,-1000,1000,419,-1000,-1000,-485,524,675,-557,401,-163,-59,-350,1000,-561,107,-105,188,-129,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getSetter():com.fasterxml.jackson.databind.introspect.AnnotatedMethod",
            new int[]{601,1000,168,-365,1000,462,1000,-192,1000,-637,1000,-108,-3,104,-141,452,-219,-718,-1000,-110,-37,1000,-185,465,927,1000,14,367,1000,-159,854,1000,1000,1000,-586,-312,173,68,-1000,-127,-413,604,65,-820,-1000,555,-376,-280,251,-91,-327,63,-903,1000,1000,951,-188,-702,-329,-452,-1000,-1000,1000,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getSetter():com.fasterxml.jackson.databind.introspect.AnnotatedMethod",
            new int[]{-502,1000,-81,-567,1000,1000,957,-36,73,-869,-597,-788,1000,857,-1000,-52,1000,-747,-936,917,18,783,532,-164,1000,-351,1000,109,42,1000,-776,227,65,-138,-482,-1000,180,946,540,-656,1000,1000,-251,1000,-367,455,-225,238,1000,-1000,835,504,537,361,994,302,-1000,1000,-737,19,-541,121,-859,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getWrapperName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{32,-577,73,-223,-412,536,-223,335,-316,-778,-293,93,-26,-756,160,173,-478,237,-356,52,-1000,-565,236,-340,659,-511,181,730,132,1000,-119,-177,-745,642,-257,-678,170,776,64,252,-1000,175,791,40,-360,-51,834,-796,-546,1000,-686,151,281,-242,434,476,492,-485,802,105,198,-713,290,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "getWrapperName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{915,-1000,-293,488,-157,-175,-549,-900,721,-435,133,1000,-924,236,347,-681,731,-725,719,1000,-215,-1000,125,-856,702,191,135,1000,329,532,-290,-83,805,420,5,5,-403,-83,659,994,-861,-180,-582,278,-91,-69,11,-23,944,118,778,756,360,428,-127,739,-1000,-140,503,-281,66,-545,428,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasConstructorParameter():boolean",
            new int[]{1000,-1000,-161,-601,920,146,124,846,936,-761,-1000,1000,-196,343,644,-1000,-1000,699,475,737,1000,209,628,1000,956,-854,583,-6,-606,-933,-1000,-788,97,319,255,714,1000,-516,1000,-1000,-818,90,439,114,803,-1000,-1000,-1000,-876,-630,-776,33,-1000,728,35,389,-235,320,-865,920,-819,531,1000,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasConstructorParameter():boolean",
            new int[]{560,-23,-676,-664,-1000,456,614,1000,-36,-1000,-364,-82,-555,129,388,-85,-679,-1000,1000,-423,517,-1000,-543,663,908,-44,-270,384,50,201,-173,-996,501,-1000,531,246,354,227,-712,504,1000,1000,-410,-431,649,1000,-520,-477,-111,-944,-667,-134,340,234,-414,269,-653,576,-1000,493,-271,1000,-1000,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasField():boolean",
            new int[]{-836,-264,-349,16,866,1000,-16,439,553,1000,1000,-444,-1000,-1000,429,688,478,-729,89,785,-1000,-602,175,381,876,357,275,-538,-1000,-570,1000,-993,121,242,435,-493,855,-226,-1000,-80,165,245,820,-15,-550,1000,-1000,334,980,-976,-426,90,290,221,-1000,1000,77,-1000,154,612,404,979,206,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasField():boolean",
            new int[]{-29,-678,-585,971,-517,833,-75,-237,-452,1000,452,-345,-722,-1000,-62,1000,-231,-691,328,1000,-1000,-959,1000,-200,1000,501,1000,633,-134,-654,1000,-442,257,-17,285,150,94,-402,-1000,-576,344,1000,1000,845,877,757,-409,-758,-10,-647,13,735,346,1000,219,330,-241,-698,-983,369,1000,-47,1000,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasGetter():boolean",
            new int[]{152,915,-816,-543,63,-1000,1000,1000,-1000,724,226,-1000,944,543,467,847,-308,470,-668,348,-884,879,230,-321,-1000,1000,1000,33,944,492,-827,593,140,11,-1000,-305,1000,1000,-252,-996,-474,632,-324,677,-697,1000,1000,-178,456,-122,372,-1000,325,-1000,328,-1000,-1000,-803,-426,-1000,1000,566,260,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasGetter():boolean",
            new int[]{152,109,-831,656,1000,-1000,1000,8,-736,127,-325,-1000,1000,881,22,496,-308,618,-668,1000,-1000,879,-529,-147,-465,1000,1000,1000,-644,-997,-808,1000,-119,-126,-1000,545,-105,1000,-1000,-1000,572,-1000,-1000,455,-1000,-560,415,287,306,705,-458,-1000,1000,-734,432,-1000,-1000,-249,-426,-1000,1000,-685,260,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasName(com.fasterxml.jackson.databind.PropertyName):boolean",
            new int[]{-1000,-1000,283,-591,-771,-718,1000,-271,382,801,-317,-1000,1000,-819,-361,420,80,-215,-151,-246,342,-672,-291,141,72,-515,-1000,812,-1000,544,208,-855,703,-1000,-790,942,-822,-887,219,1000,39,-1000,334,543,1000,-1000,-196,422,436,339,-377,-114,32,-653,802,-467,110,-45,1000,480,-1000,-61,-581,868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasName(com.fasterxml.jackson.databind.PropertyName):boolean",
            new int[]{-238,761,-565,-869,752,-505,-172,-65,711,773,45,-57,10,-828,-1000,-779,-408,-273,-484,-711,-581,-236,-1000,1000,-216,826,-833,-308,-749,87,1000,-331,95,291,454,169,-496,-1000,-879,-63,709,-196,1000,421,-400,-494,1000,393,-393,-129,598,227,374,525,-929,315,-215,130,419,-361,-319,145,675,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasSetter():boolean",
            new int[]{838,340,773,570,-632,916,-563,100,-191,-698,-831,842,684,276,-222,-748,-916,391,-784,-347,390,936,221,-719,663,987,-356,466,615,-184,-997,-780,-780,448,3,-605,764,-88,-509,-477,-613,400,660,-516,53,-601,-842,214,250,-316,231,-195,729,14,9,-430,111,336,618,-102,-727,-640,918,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "hasSetter():boolean",
            new int[]{671,68,-1000,29,-951,1000,639,-881,-1000,551,-754,184,-496,220,-762,279,521,-322,-1000,-1000,-672,-709,-390,317,-483,1000,973,475,404,-275,-690,-1000,275,64,-799,715,971,722,654,898,424,7,-831,592,-1000,-681,-11,-716,-314,-1000,-335,1000,773,300,-420,489,1000,1000,376,1000,507,-1000,-626,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "isExplicitlyIncluded():boolean",
            new int[]{897,1000,285,-740,-794,1000,260,-1000,123,200,-595,-113,-1000,419,337,-847,448,1000,-77,-262,113,374,-1000,-940,-1000,407,192,617,-328,602,640,-841,896,884,870,-817,-418,616,-59,-165,-504,-211,-1000,-410,-908,-397,-155,-907,773,260,1000,-509,181,792,454,1000,382,-1000,824,-15,-789,529,-13,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "isExplicitlyIncluded():boolean",
            new int[]{-481,-908,253,-1,853,-797,-792,354,-33,-480,979,888,795,663,-641,740,-270,-157,-194,173,664,-143,-208,698,890,-347,-52,244,454,545,-823,654,-929,-413,-606,501,720,72,-382,-37,640,716,768,355,605,-348,-289,779,77,-933,-136,343,-878,-962,-62,370,-185,918,338,-852,268,-515,122,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "isExplicitlyNamed():boolean",
            new int[]{-330,-119,242,-230,1000,247,745,-236,-546,-560,-746,-284,-195,192,97,-808,-706,458,-339,-571,-372,-710,1000,-137,51,-94,192,149,944,-845,697,275,-664,889,274,-113,-906,-221,1000,232,-477,-579,-1000,422,245,-556,415,220,239,201,469,428,-1000,-210,-741,724,-196,-375,-500,759,1000,876,-888,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "isExplicitlyNamed():boolean",
            new int[]{516,402,105,-1000,-169,-792,766,32,107,448,1000,-242,1000,-761,-414,-657,999,999,-425,878,349,1000,-484,-456,353,-890,1000,282,1000,-485,-170,-1000,-640,-471,979,-759,-735,-1000,361,-281,-1000,259,-704,-166,-779,-876,15,535,-345,-442,-601,-55,180,-1000,-1000,562,368,245,1000,264,-99,93,573,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "isTypeId():boolean",
            new int[]{536,541,313,-559,-1000,89,-970,-499,-269,-1000,-318,419,819,679,14,-753,-596,-692,1000,548,-883,454,185,306,578,14,144,514,1000,-685,-516,608,-72,424,367,-409,472,-500,-1000,-180,-421,-1000,-1000,-445,-545,-1000,-609,-145,760,-76,-27,516,-248,1000,565,-1000,412,-504,-495,376,-1000,-103,387,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "isTypeId():boolean",
            new int[]{-332,29,-686,801,823,-1000,930,733,-1000,192,-395,332,-187,1000,39,-647,-226,185,-105,326,1000,-790,-150,-585,-1000,38,-1000,483,-889,1000,-109,1000,-60,-695,880,-797,-491,594,-94,-440,712,845,30,-1000,-516,-331,440,-461,-654,-148,-371,1000,-988,1000,-921,1000,-644,-1000,1000,-167,-283,-60,-222,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "mergeAnnotations(boolean):void",
            new int[]{56,559,-68,348,-317,1000,591,-436,625,23,595,776,-840,1000,-41,229,1000,1000,539,-89,-509,143,-180,-127,-927,-477,-597,79,151,72,-430,-747,1000,46,-1000,-1000,-801,-651,172,-199,1000,-1000,-515,1000,28,-285,1000,1000,-484,-24,1000,-422,338,-1000,-477,-27,1000,-1000,941,-1000,1000,1000,-1000,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "mergeAnnotations(boolean):void",
            new int[]{-786,226,-851,1000,721,-397,-739,-719,-788,-635,674,-660,-872,-501,-236,1000,-190,-961,388,-499,-538,334,-416,-386,-1000,643,-382,926,-1000,317,-1000,-296,290,-41,-243,114,-767,-1000,180,70,-860,140,-359,-502,808,924,855,755,296,-133,378,-7,-453,152,731,-560,587,429,220,225,950,153,-987,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "removeConstructors():void",
            new int[]{509,548,215,720,22,-836,596,-258,808,737,489,-499,589,-494,-744,355,-124,-755,274,-349,-600,-520,70,-363,7,978,329,-694,957,898,801,572,977,-610,-805,557,598,-818,-989,307,341,-342,-87,269,-908,-675,-280,31,702,-816,407,437,246,496,586,34,653,-298,-135,987,-496,850,212,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "removeIgnored():void",
            new int[]{-513,66,-1000,-253,1000,-536,303,982,-468,677,-47,62,1000,-476,293,845,708,1000,168,-449,-410,795,466,-1000,84,-1000,-796,-68,-557,819,-240,553,475,-543,-587,660,-345,277,923,116,-1000,-96,-878,448,-582,-353,46,147,476,121,443,1000,-459,1000,68,1000,382,85,-875,7,552,437,32,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "removeIgnored():void",
            new int[]{1000,-403,651,-992,391,-845,187,-106,-277,1000,1000,-1000,695,386,-1000,17,-46,-684,-1000,1000,-1000,804,276,765,1000,-329,-781,205,216,-821,-1000,-176,806,90,-1000,936,225,148,818,-235,-109,-1000,561,1000,-888,1000,-1000,1000,-760,1000,1000,1000,-304,-399,200,-12,-161,876,-136,404,542,-628,-154,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.String:W1Byb3BlcnR5ICd7fSAtMTAzICc7IGN0b3JzOiBudWxsLCBmaWVsZChzKTogbnVsbCwgZ2V0dGVyKHMpOiBudWxsLCBzZXR0ZXIocyk6IG51bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "toString():java.lang.String",
            new int[]{103,1000,-7,-357,-1000,-220,-696,374,-89,570,-103,-259,-319,-181,243,-316,522,532,-157,-525,376,312,126,-515,223,-713,-461,503,540,98,949,-680,-1000,273,29,-677,-379,81,370,368,-919,-269,62,-953,-81,-1000,256,-347,541,-1000,669,-358,108,-750,-178,332,-154,-1000,-849,-319,-599,449,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.String:W1Byb3BlcnR5ICcweDgwJzsgY3RvcnM6IG51bGwsIGZpZWxkKHMpOiBudWxsLCBnZXR0ZXIocyk6IG51bGwsIHNldHRlcihzKTogbnVsbF0=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "toString():java.lang.String",
            new int[]{-563,1000,43,424,-1000,982,-1000,-839,-1000,1000,1000,-667,-1000,-179,219,-834,-228,597,-947,203,-1000,960,-734,-1000,-1000,-1000,-193,-457,841,-125,114,-716,-1000,853,-931,-93,-1000,289,-1000,1000,-971,-526,-1000,-600,761,-588,-538,804,389,376,1000,1000,1000,-1000,-154,1000,445,319,-1000,-55,-531,982,-246,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "trimByVisibility():void",
            new int[]{-402,-1000,-101,-1000,471,1000,444,647,1000,392,1000,-1000,1000,236,-612,-509,113,841,369,709,-406,243,-264,-1000,625,-1000,801,-661,80,-577,711,-772,-722,-232,-231,-456,723,1000,487,-1000,-87,1000,194,-957,1000,369,-1000,1000,424,-934,-750,-684,1000,1000,-90,402,-1000,764,460,997,710,421,-784,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "trimByVisibility():void",
            new int[]{108,-112,40,647,-64,-1000,-1000,-921,-103,-880,-1000,1000,-745,602,1000,353,979,-251,-318,464,207,1000,-520,-251,-1000,618,918,-197,-1000,-1000,-355,324,-309,-873,1000,745,987,-1000,-86,372,505,-230,-632,-153,125,-255,292,-51,-352,-106,277,-263,557,-40,1000,103,1000,-1000,-86,-1000,-388,-1000,269,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "withName(com.fasterxml.jackson.databind.PropertyName):com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder",
            new int[]{-404,-399,18,871,688,91,-1000,1000,-1000,311,-803,-616,-801,-309,-1000,152,274,-433,353,302,-362,745,-40,-411,867,315,156,190,-230,-369,1000,1000,-125,434,531,472,-977,-262,1000,-1000,-300,-1000,1000,-151,996,1000,-136,-528,1000,-1000,-383,1000,981,-524,918,1000,540,1000,503,54,-1000,1000,826,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "withName(com.fasterxml.jackson.databind.PropertyName):com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder",
            new int[]{-1000,73,-995,-82,1000,-978,-897,832,-501,62,-199,243,-1000,-1000,501,-116,1000,137,263,-128,-842,370,13,-493,662,-556,-372,1000,-773,-965,219,400,345,103,-900,1000,-1000,343,130,-780,-224,-494,487,-535,840,-603,103,-129,-980,-503,-284,640,231,-1000,552,577,-1000,-317,152,597,-148,627,-921,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "withSimpleName(java.lang.String):com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder",
            new int[]{-225,-598,545,762,-506,771,-8,-383,-662,153,-791,-312,-800,79,312,-559,550,-60,-325,-652,520,-621,-489,181,558,-185,328,100,-2,19,-454,-74,-92,375,-839,554,-750,-663,-527,43,-482,445,648,615,-153,-746,-853,-469,-181,517,-96,601,141,-938,-447,951,-242,949,959,593,-8,666,855,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder", "withSimpleName(java.lang.String):com.fasterxml.jackson.databind.introspect.POJOPropertyBuilder",
            new int[]{-728,-374,259,-402,-1000,1000,-201,-1000,-1000,-176,-606,-1000,-1000,-946,-529,694,140,-1000,616,-1000,1000,-477,-1000,-1000,1000,-1000,-829,-199,-117,505,88,834,-831,1000,-1000,291,791,1000,-778,-112,-1000,-570,642,-1000,-63,-1000,-1000,310,-1000,-1000,130,586,-1000,-1000,1000,1000,877,982,-1000,1000,-344,58,-1000,1000}));
    }
}
