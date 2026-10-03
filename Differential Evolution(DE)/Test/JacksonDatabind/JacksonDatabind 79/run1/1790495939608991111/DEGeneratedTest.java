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
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findAndAddVirtualProperties(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedClass,java.util.List):void",
            new int[]{332,-771,212,508,-906,594,499,-505,17,-295,-213,805,-979,475,393,-54,-48,6,611,-713,556,-516,-760,23,-661,-284,-676,415,642,354,-917,-454,286,-322,797,758,-766,232,249,-665,-180,590,295,-970,362,-970,79,-423,-823,-978,483,468,399,-928,502,954,-632,697,418,502,804,84,-141,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findAutoDetectVisibility(com.fasterxml.jackson.databind.introspect.AnnotatedClass,com.fasterxml.jackson.databind.introspect.VisibilityChecker):com.fasterxml.jackson.databind.introspect.VisibilityChecker",
            new int[]{772,-329,231,-868,442,-578,739,-958,795,15,430,151,-326,-459,484,-177,-549,-174,622,679,-252,388,-945,243,-504,677,-737,-414,-490,-624,-557,823,-735,57,626,-217,-108,-647,429,-107,-470,915,-776,392,-869,-89,79,348,575,611,-728,-362,508,-344,385,-963,724,37,35,-110,-912,-490,-838,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findClassDescription(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.String",
            new int[]{-997,-818,437,-176,370,-519,389,801,-264,-140,-960,498,-146,-861,-758,204,511,918,338,-408,-978,-442,-156,-12,767,-960,717,878,98,319,958,603,335,-873,-885,-860,-635,307,431,681,372,706,-191,323,-378,415,-432,585,893,-271,54,-294,-854,559,-459,731,324,247,605,204,-188,746,-845,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findContentDeserializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-875,472,398,461,537,-817,930,747,870,-732,98,-217,-640,187,-971,719,-249,965,-394,600,-91,-935,898,86,110,546,341,609,140,-524,-606,-269,-77,147,-972,-736,-454,232,-404,653,296,706,364,29,575,-907,334,-596,-566,384,-172,510,-141,852,-234,-17,-91,-98,573,735,356,76,-805,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findContentSerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-517,-399,-397,-300,-588,896,-716,778,-44,-459,709,542,452,-393,-926,95,-918,225,494,956,772,-810,-184,408,263,963,-687,-118,-432,697,724,729,75,-424,-479,33,-233,48,715,-168,-736,321,45,517,661,-129,956,-962,549,-347,452,-947,975,223,-434,-823,-381,3,996,-925,-990,607,-732,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findCreatorBinding(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonCreator$Mode",
            new int[]{404,-229,-688,887,719,-655,-470,665,826,-902,-635,878,918,-492,567,585,862,494,-222,664,-517,-608,-940,-148,-423,149,-403,-840,709,734,-313,-692,993,260,603,-2,-520,-356,812,797,493,-238,126,-771,360,-996,866,244,-688,-105,876,679,-977,17,-682,515,-731,-147,573,-169,-543,-624,294,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationContentConverter(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Object",
            new int[]{170,935,356,480,941,-37,-950,-390,326,-869,-298,-97,799,-957,-299,-363,958,-287,-232,-369,-524,397,-990,-701,993,-979,-376,121,-434,-895,-979,205,707,799,-45,-18,-316,-750,275,355,-225,-128,-626,620,-537,336,76,364,183,-956,764,-814,162,-264,859,593,-859,-535,-561,-325,-689,653,-281,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationContentType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{-427,-407,-164,-109,-930,646,574,585,-606,-714,375,-781,-154,167,800,-61,-470,766,-154,-652,930,48,947,-573,706,653,-748,39,325,108,-399,-554,-498,833,769,536,-87,-564,-187,-753,-875,-818,639,75,-691,-595,273,-520,-94,119,740,-493,141,-899,-59,-990,51,321,-518,482,-492,766,991,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationConverter(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-496,941,-39,-400,414,-400,932,-537,-12,-5,328,213,681,-93,-242,-53,-78,-72,698,-258,-802,-600,335,-505,462,-661,946,-218,860,-764,240,339,297,-689,-864,-773,-600,552,-965,-668,86,-258,814,424,-884,-486,484,-306,778,484,-266,-578,147,-509,-560,-779,-739,-939,610,173,976,378,222,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationKeyType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{933,-671,-518,90,447,-819,402,180,-165,2,-511,758,26,837,462,983,586,129,409,-806,799,-883,-719,145,-853,-191,-783,993,-241,799,-730,-712,-787,-874,825,360,237,213,75,-555,303,497,265,-335,895,795,141,518,-670,661,-21,946,-620,-951,914,245,-316,436,907,-930,-39,239,-407,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{196,1,-607,-713,-512,260,-884,-290,532,-978,-221,-918,357,-620,-284,945,-258,-727,528,-32,352,-496,741,-42,-330,228,-872,-909,97,-822,-999,-100,437,523,-190,596,653,183,-789,-382,273,-341,-641,974,-912,9,-308,-107,-79,459,681,574,-876,324,-890,428,-766,412,-651,79,-294,871,750,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-904,361,-912,329,457,676,-420,815,571,-225,330,-839,790,-230,-677,934,-897,57,840,508,-162,291,570,-767,-879,829,-180,-711,345,-729,573,-46,701,-629,-272,-316,618,722,434,-422,880,337,-864,-445,-870,558,651,-119,484,-473,432,-430,74,915,-999,-393,465,518,486,968,110,924,-845,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findEnumValue(java.lang.Enum):java.lang.String",
            new int[]{-411,483,727,-254,308,200,50,-430,517,929,-216,938,-881,539,-807,685,258,-247,93,288,373,-143,378,-293,687,-562,710,-883,713,612,482,118,-894,815,-351,591,-930,381,706,-377,352,837,-311,606,875,156,880,-13,40,-968,647,-5,827,655,-266,104,943,-557,243,15,512,-285,-485,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:1:21:java.lang.String:IA==", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findEnumValues(java.lang.Class,java.lang.Enum[],java.lang.String[]):java.lang.String[]",
            new int[]{-905,679,464,-729,253,-988,280,-743,89,306,-467,-128,235,-894,515,833,-684,-175,-423,-518,-806,809,450,-574,298,-654,150,111,-607,737,-463,-275,-879,728,756,463,-676,99,-389,34,-266,1000,663,553,-50,767,311,-546,-214,361,486,903,-914,-680,677,357,826,-810,986,-642,-983,738,-347,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findFilterId(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-474,-303,-748,-182,-771,310,32,-275,-128,-846,-717,-500,443,-504,994,142,859,176,-940,109,716,-663,487,471,60,-478,709,-461,-684,912,-947,713,-571,166,837,-314,798,991,-358,664,625,-647,878,-212,949,-931,-66,-444,-192,417,909,725,-264,-663,-773,-87,-897,-150,-747,-104,704,-647,469,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findFormat(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonFormat$Value",
            new int[]{-492,105,-356,-384,882,522,588,585,-940,319,-955,796,995,-244,-647,-224,375,3,-559,-303,878,-41,434,13,-536,-261,768,-675,-880,504,519,414,-49,287,-773,-415,-354,522,-390,853,-997,104,258,852,-604,959,659,-172,860,-641,-297,-418,-157,511,197,817,526,308,875,-28,-856,-450,-126,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findIgnoreUnknownProperties(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Boolean",
            new int[]{27,-595,-633,592,263,709,523,320,-461,-650,-652,-370,92,834,381,-936,-301,-904,831,230,-860,-737,62,-81,516,268,991,-232,-653,453,-928,-207,681,-343,655,-362,884,420,-91,707,673,966,292,-512,-902,-635,572,-699,606,-89,769,495,154,-841,113,-328,365,293,-958,890,172,529,637,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findImplicitPropertyName(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.String",
            new int[]{596,374,-308,935,-620,-521,179,-49,22,234,13,298,-505,545,673,-253,780,-192,876,821,-192,-439,-88,-443,305,289,870,-99,-497,-819,193,526,-241,-603,601,427,752,-755,-436,254,117,-441,-750,11,-893,-483,121,492,236,-843,-756,-373,-335,943,-252,-248,602,851,-152,-219,938,-328,396,-805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findInjectableValueId(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Object",
            new int[]{120,403,208,-545,844,-876,-931,379,807,-262,570,692,-127,-286,-37,-610,-350,-705,97,-185,-423,-279,725,-288,-300,-880,-961,659,-312,-421,557,277,724,-258,-267,874,807,-496,-292,799,769,832,554,-358,-188,-430,-979,-326,-249,-813,307,-600,293,883,-263,973,-312,-814,-698,-650,-149,247,-975,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findKeyDeserializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-93,-294,587,617,466,-316,508,384,-566,115,483,166,387,-318,163,-221,-9,-870,971,-231,59,245,-705,-692,-255,179,-292,-529,-919,-258,-455,249,-971,-847,-84,-448,-648,113,-848,-143,-300,-915,545,-804,-439,-849,-716,-285,950,935,-511,-816,75,753,-593,510,588,324,958,-436,-508,-782,-914,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findKeySerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-943,500,-401,96,-588,-805,-337,710,-364,-429,-887,589,781,550,-307,619,-766,952,276,-47,356,-129,-505,-830,9,197,468,-337,-605,-979,-189,391,-738,631,-840,783,-551,-307,-225,-933,994,-227,-573,600,586,-134,-260,854,-986,822,-540,643,430,628,657,911,-232,431,836,930,-563,-172,70,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNameForDeserialization(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.PropertyName",
            new int[]{-879,-284,-184,-329,-685,499,609,-351,-696,-215,-51,196,-6,-110,-406,100,-457,393,-995,-473,-551,-923,826,483,59,-55,573,-585,-192,-678,324,-467,-349,946,-37,119,934,940,-209,806,-750,-477,-828,628,696,448,-715,494,404,-735,29,-339,208,-274,-337,-227,874,-93,838,83,463,-32,375,570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNameForSerialization(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.PropertyName",
            new int[]{893,551,-256,951,-732,-910,118,570,-477,-349,896,541,-890,564,243,932,509,609,109,-331,-23,-966,546,563,-648,-409,823,-644,173,-268,676,554,704,-853,901,624,-698,867,898,986,42,-652,-277,-826,477,-886,780,486,-985,819,344,-452,484,943,778,-728,-828,673,533,-98,870,-958,799,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNamingStrategy(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Object",
            new int[]{-654,75,543,-167,311,231,-698,-382,159,15,29,-587,-335,929,-495,-53,-688,-247,-139,404,304,-214,918,143,825,838,35,-428,898,665,195,-72,-952,-169,353,599,-830,-993,-352,358,473,644,965,-563,-780,-380,-425,701,48,11,-902,361,-869,-600,-158,-359,-830,340,-229,597,-746,478,-216,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNullSerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-711,957,413,-619,105,169,-974,-808,-661,-152,110,889,-330,-855,59,823,562,-57,-137,198,-32,-7,305,506,526,-542,-38,224,367,241,792,189,-15,599,644,-425,786,-18,307,-769,-67,756,879,307,-141,564,-827,-463,-539,-590,628,330,-619,-745,789,925,10,909,-217,-767,-98,-705,-584,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findObjectIdInfo(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{-230,404,64,789,942,-126,-315,-686,10,-654,80,586,796,-351,588,-999,354,811,58,-601,-642,645,-334,369,-720,-522,-902,237,-787,656,240,889,-897,979,300,-688,336,591,-549,-786,-818,8,467,-568,-584,958,-400,-52,-398,-530,997,310,-26,288,-685,984,-371,-932,879,-244,842,-921,730,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findObjectReferenceInfo(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.introspect.ObjectIdInfo):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{-690,-882,301,-759,145,-668,335,327,-678,-245,-988,-560,539,64,-905,46,336,-513,-676,723,705,559,260,-414,672,712,-669,-421,773,-367,728,-790,-14,993,-923,-104,153,79,-50,933,142,165,-848,301,-884,36,496,8,-876,968,267,472,-41,-291,-21,-440,-294,491,-836,365,747,-16,917,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findObjectReferenceInfo(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.introspect.ObjectIdInfo):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{165,-450,-944,-938,-980,-726,-433,-550,-324,-357,-159,496,783,391,413,179,473,672,-581,-830,231,-300,-749,-414,413,-211,-497,-178,654,-439,162,875,48,-298,293,130,-578,654,-417,437,-706,844,-990,-494,173,-280,632,-878,749,-317,-954,-494,206,-725,411,-738,-584,478,-924,-236,78,-160,890,620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findObjectReferenceInfo(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.introspect.ObjectIdInfo):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{412,-648,-327,371,578,-448,59,796,53,467,-280,934,-611,-136,108,770,-388,918,-587,93,164,-956,-160,-122,-824,-289,260,980,621,800,519,358,547,-287,939,-389,20,417,720,481,-307,869,-214,-858,-51,856,420,-398,-504,200,-484,366,-47,-533,-319,-421,501,-136,-471,362,974,-159,546,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPOJOBuilder(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Class",
            new int[]{276,-198,-776,760,130,980,-543,-933,-708,-43,569,-856,68,-555,396,41,-288,858,-915,517,612,-70,949,-238,937,-716,-760,-636,67,293,-488,-272,981,121,-396,-502,-874,-622,-820,846,245,-510,-781,963,137,578,-93,535,-611,422,-57,-438,-254,884,-523,-529,-20,103,567,162,-144,689,-638,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPOJOBuilderConfig(com.fasterxml.jackson.databind.introspect.AnnotatedClass):com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder$Value",
            new int[]{77,-909,99,-114,841,-184,379,329,163,98,-140,574,-556,837,967,-412,12,613,-198,458,-174,759,533,-919,841,-465,210,54,734,108,900,871,368,-717,-439,-48,330,-918,975,340,132,-860,-788,912,298,655,623,895,399,139,132,-881,-626,-410,742,-996,-52,856,-399,-447,829,-35,688,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertiesToIgnore(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.String[]",
            new int[]{-83,-1,539,556,-301,-657,785,-687,703,-160,867,-322,-434,-150,541,612,-801,677,816,-621,-458,-78,453,-456,106,-720,511,474,-392,650,-425,-492,851,291,821,-275,-284,-994,-559,-629,-354,832,-883,-547,-670,-435,-794,-283,-607,-397,86,333,-102,-962,727,922,192,730,-37,297,199,-855,-839,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertiesToIgnore(com.fasterxml.jackson.databind.introspect.Annotated,boolean):java.lang.String[]",
            new int[]{-835,-768,482,956,-182,398,503,-580,69,761,-15,937,315,-224,913,551,545,-303,980,354,-568,484,-474,186,-692,-132,-117,-923,-637,-336,426,260,-315,-679,820,607,-684,417,-558,-728,432,-628,767,-17,-47,-21,-377,972,-584,547,653,-186,-223,398,72,-387,-28,-721,844,-85,516,834,-195,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyAccess(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonProperty$Access",
            new int[]{-806,707,-753,781,275,698,854,-86,-89,-864,-857,-889,923,-671,797,-317,-615,241,340,-950,551,-188,171,237,-689,-837,869,-170,-865,156,520,-94,359,307,586,-865,-152,-575,-23,-206,-363,-496,427,716,-26,-627,986,-642,312,410,-465,-884,319,-793,565,764,-312,-478,-819,-526,655,-944,-760,-739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyContentTypeResolver(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedMember,com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder",
            new int[]{-381,-758,553,-904,967,-617,-636,-209,897,-882,-762,-808,-303,316,-459,372,-190,-610,-596,-66,-339,-796,690,266,-92,-327,-413,851,34,-590,-261,-919,-673,-299,549,-349,94,998,-437,-604,239,-833,-307,-812,-771,-194,449,408,-92,75,-672,395,602,709,-994,-692,-434,120,132,-178,897,-138,119,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyDefaultValue(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.String",
            new int[]{90,-218,-693,623,399,-455,-980,-378,-718,-29,-336,-63,-204,-422,318,658,-241,-475,-732,774,-51,-561,763,362,-204,167,-374,538,-821,-296,745,805,583,320,946,-649,-274,-588,575,538,997,-28,-775,332,569,748,474,741,901,-883,255,488,-541,-479,514,-958,-1,874,217,956,478,-181,-598,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyDescription(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.String",
            new int[]{-973,951,121,888,167,571,600,-164,-87,-917,257,702,-294,-509,919,-937,-237,-17,-314,606,783,74,-325,320,-134,196,-987,120,797,-920,-671,-67,-471,184,-153,571,-836,667,-200,395,-975,-28,330,-278,-467,-46,-686,-792,481,657,607,-977,-931,-649,-897,553,-427,-767,684,-468,890,340,-329,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyInclusion(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonInclude$Value",
            new int[]{440,-680,829,-677,-97,174,-456,279,-658,-640,-611,-425,-755,-902,-313,303,735,618,-799,105,-749,120,-240,465,570,-626,940,-250,-139,158,-393,325,-476,954,135,467,-985,372,418,-17,-486,603,-604,459,373,-99,-217,880,573,-461,892,-989,-893,1,-976,353,-868,-733,770,-233,51,944,-272,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyIndex(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Integer",
            new int[]{25,915,57,-382,0,375,-898,688,848,-751,452,776,479,-876,743,-984,148,-278,847,-142,851,-343,343,-624,-230,-523,138,-339,860,429,-767,365,-138,621,878,897,842,-545,187,-688,682,-484,-355,478,-134,463,889,789,359,-111,653,-632,444,-389,-200,481,-67,384,-952,727,-639,-337,-884,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyTypeResolver(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedMember,com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder",
            new int[]{-363,-976,993,466,-38,-203,-1,985,229,193,-157,847,467,-338,-887,-190,-412,826,629,797,-812,266,148,-792,-809,-281,807,541,-251,349,-820,561,787,170,-803,-657,918,-147,920,333,-955,-791,177,269,921,-582,396,-755,-846,730,-367,540,738,-174,-255,891,-971,-760,957,-170,-725,-629,-384,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findReferenceType(com.fasterxml.jackson.databind.introspect.AnnotatedMember):com.fasterxml.jackson.databind.AnnotationIntrospector$ReferenceProperty",
            new int[]{722,271,366,-155,958,-787,-271,961,-736,564,884,-11,650,159,-768,-887,984,-956,10,-437,537,-163,294,958,898,-962,-414,296,-722,602,458,-499,-755,-545,-852,-566,-791,824,723,188,-376,293,422,75,780,278,162,-721,-673,-716,822,357,-794,732,877,936,-456,416,253,-161,258,-289,837,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findRootName(com.fasterxml.jackson.databind.introspect.AnnotatedClass):com.fasterxml.jackson.databind.PropertyName",
            new int[]{-103,213,492,-104,946,517,81,-873,-960,964,-774,-467,-954,540,385,-316,-66,-668,882,762,811,941,-306,-57,105,453,-108,952,744,-350,-99,106,757,-510,484,-72,146,-411,-58,517,-893,-593,-587,-736,-277,168,-691,180,-905,158,-992,-821,555,959,669,963,-923,453,729,804,-711,-39,-950,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationContentConverter(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Object",
            new int[]{-297,-918,491,34,-720,960,51,-952,48,-516,638,396,-72,548,798,-52,-350,934,782,320,417,283,-608,903,-502,-788,311,-576,-181,-755,649,93,530,-691,780,-708,-83,690,-430,658,73,-479,580,-608,288,-801,981,721,-759,388,-495,-523,770,-279,-946,-985,260,-275,-697,-148,488,32,-972,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationContentType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{833,-495,484,-325,-113,-785,-134,31,302,533,394,210,-32,-79,-128,-941,363,361,-791,59,552,346,901,-186,825,-775,406,-703,204,347,967,699,-496,644,-364,964,-984,-922,465,933,-550,735,154,363,335,245,-211,51,-283,-324,-267,796,709,104,-783,975,194,626,-270,162,846,-234,-75,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationConverter(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-348,43,-919,563,263,-456,-343,-239,-183,317,26,47,-842,367,-935,353,-970,852,-53,-877,-564,907,-863,-380,-783,-905,580,849,474,71,848,-262,882,445,983,-521,-894,-453,-295,-394,558,807,243,-740,559,-171,-412,387,-412,-152,222,134,768,273,484,-399,974,-764,680,-731,55,716,-283,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationInclusion(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.annotation.JsonInclude$Include):com.fasterxml.jackson.annotation.JsonInclude$Include",
            new int[]{29,-285,-889,893,-351,-220,-436,366,436,989,583,101,950,-618,-171,-271,-671,690,-351,-400,882,379,-909,773,-841,-178,273,62,-691,109,-250,957,704,-280,886,426,911,532,731,-585,-609,432,371,-810,562,200,-654,571,249,-739,351,607,950,-597,130,-767,-143,-368,277,-915,-919,943,-567,766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationInclusionForContent(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.annotation.JsonInclude$Include):com.fasterxml.jackson.annotation.JsonInclude$Include",
            new int[]{744,-707,421,790,280,464,-97,744,372,-781,-123,779,-92,0,550,123,920,-894,735,447,827,748,-598,-205,78,324,-957,-894,689,669,196,450,5,-440,-204,421,352,-672,-37,-532,-765,81,264,402,188,741,-442,769,-508,862,-893,345,572,-963,802,623,92,588,-925,-162,-494,182,334,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationKeyType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{169,14,-438,552,-181,-452,820,-691,346,163,-689,215,441,-344,-654,-417,323,144,242,746,804,-982,66,705,-316,100,700,173,236,-882,-7,368,582,-245,-902,-418,-136,194,-513,761,-694,-644,588,119,-521,817,-490,157,-598,797,901,360,449,534,-383,-674,480,847,-391,-897,-921,-201,-823,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationPropertyOrder(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.String[]",
            new int[]{597,801,989,-474,-553,859,316,-371,-626,478,377,78,641,879,991,489,-675,-394,951,-482,192,-919,-906,-117,337,-666,351,-434,-504,506,26,887,-21,-609,517,-4,513,-250,904,-77,-92,450,-582,811,456,537,-352,-813,-781,319,-463,220,-22,232,-281,792,-514,800,529,889,905,-610,-823,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationSortAlphabetically(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Boolean",
            new int[]{-982,-924,823,-224,471,915,807,-600,-250,-116,972,-77,-497,-213,-685,-257,529,217,-432,-332,-280,739,486,150,721,-637,-318,-760,364,285,104,-432,652,-172,-457,-588,426,-476,392,708,682,-629,295,-277,981,433,300,19,-635,166,-356,359,780,102,-586,199,-868,-124,979,582,-233,-48,-699,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationType(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Class",
            new int[]{434,-67,568,799,-700,-50,711,-430,-550,-952,846,116,700,670,-127,582,-75,-373,138,-283,658,78,-954,-501,-899,-478,-164,230,-622,-744,289,-248,-602,-513,192,997,725,-963,951,-435,-490,-606,-67,-328,706,797,-188,489,562,-44,-500,-409,673,-949,69,83,785,-283,494,795,948,-113,8,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationTyping(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.annotation.JsonSerialize$Typing",
            new int[]{762,-156,-509,93,-655,-618,215,-690,16,345,668,-464,-246,112,-181,502,368,552,-5,838,-609,-790,-919,-192,-656,-564,808,-316,407,-63,-184,111,844,-425,-243,714,-118,219,333,-352,-842,930,892,-149,-634,828,-48,-659,244,-375,-136,-937,682,-10,-289,-487,-600,668,-493,-365,-632,-416,760,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{270,574,387,597,-396,-61,359,-529,-344,-279,-757,581,859,273,-140,-22,-587,-20,579,-198,412,-33,126,-918,856,-349,741,29,-790,298,-508,-526,482,-130,-887,680,981,-131,-984,568,884,-358,285,-448,-462,-340,-592,-354,23,646,240,-728,-74,130,292,296,475,-24,-195,221,285,-239,272,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSubtypes(com.fasterxml.jackson.databind.introspect.Annotated):java.util.List",
            new int[]{-896,-66,206,-498,-198,699,124,-448,-762,-730,944,715,-27,748,314,-167,-807,569,-673,-550,-830,-701,-900,195,954,-435,849,182,-705,400,-206,953,357,294,796,770,-148,-715,747,-622,-93,533,-571,-230,165,984,819,-73,454,994,604,-972,647,53,-915,210,284,128,-454,-418,-16,-441,715,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findTypeName(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.String",
            new int[]{549,-92,-432,421,637,242,-355,-310,-269,600,-32,-652,-375,282,857,-908,289,-257,-11,-161,931,-384,454,463,401,-897,-451,848,438,365,-364,797,316,-123,-445,-975,-683,-676,113,-522,83,-444,0,-421,-580,-658,-920,652,-613,252,507,716,631,-174,-677,139,-180,-587,-699,-781,-221,521,-325,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findTypeResolver(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedClass,com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder",
            new int[]{-480,985,-181,-793,889,-965,-429,27,618,-323,881,347,-81,-853,547,-781,809,428,774,786,-985,-187,-346,374,456,945,830,-634,131,503,906,175,299,666,-362,201,225,230,711,104,404,844,20,930,230,-804,-280,684,-681,190,86,-212,-628,171,-793,530,-650,-952,887,97,6,-161,-224,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findUnwrappingNameTransformer(com.fasterxml.jackson.databind.introspect.AnnotatedMember):com.fasterxml.jackson.databind.util.NameTransformer",
            new int[]{-575,84,-538,937,496,385,827,81,873,498,391,-307,860,791,634,538,60,-19,171,456,743,-688,-800,386,637,852,357,-958,-60,893,838,223,404,895,765,-555,-72,992,-660,-524,260,657,562,-796,120,-162,30,-764,-646,-428,-361,657,-449,-95,-107,429,-999,812,-24,86,794,720,-280,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findValueInstantiator(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Object",
            new int[]{-49,663,603,-390,145,875,154,-595,-276,-629,811,-667,-595,-140,-773,-158,477,268,-130,551,-471,161,941,736,217,-616,-653,-127,-633,-154,-325,364,-101,770,284,119,601,857,-967,-26,605,825,-90,45,575,542,-104,99,-910,614,755,-259,-555,660,-867,831,-760,-222,324,897,452,718,399,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findViews(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Class[]",
            new int[]{-326,-427,691,-179,593,-72,-931,275,-880,-315,-743,-247,-426,896,-745,-811,-908,-505,514,-155,368,-196,-172,-613,485,-986,-139,549,719,-347,-147,-398,-647,404,150,-649,-916,788,-342,704,454,-749,43,-26,876,919,581,56,-723,945,891,360,-822,-238,-478,938,114,-221,299,671,676,-876,-311,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasAnyGetterAnnotation(com.fasterxml.jackson.databind.introspect.AnnotatedMethod):boolean",
            new int[]{-532,-864,383,854,-927,-752,-794,-644,-325,-480,186,380,-137,525,541,-425,914,-131,707,890,79,268,413,-365,-177,270,-277,624,-103,272,32,-851,721,-668,-362,-194,854,344,63,-329,528,625,589,211,-804,-337,-366,410,-189,-129,524,106,208,-472,-16,-725,304,-205,238,95,-860,-330,3,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasAnySetterAnnotation(com.fasterxml.jackson.databind.introspect.AnnotatedMethod):boolean",
            new int[]{-358,-491,439,964,-565,100,734,-126,-462,-604,-761,675,-94,-574,-870,-461,-101,21,-951,413,399,102,-687,841,-915,-813,-222,587,354,-755,-78,-259,-655,62,586,-776,-205,-736,960,699,55,820,-43,784,480,-585,-155,140,623,441,208,-463,196,494,-328,307,-399,-728,-389,-251,-45,541,107,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasAsValueAnnotation(com.fasterxml.jackson.databind.introspect.AnnotatedMethod):boolean",
            new int[]{76,713,-421,-380,-551,-168,117,-922,28,270,923,-706,794,245,776,-865,-676,404,55,438,-902,-799,86,869,-441,992,-937,-318,-910,-951,946,-216,-941,-738,-990,-374,162,-550,-939,-896,352,-158,703,853,879,597,514,126,-866,7,508,770,362,412,818,-254,602,-182,-791,87,677,-326,-291,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasCreatorAnnotation(com.fasterxml.jackson.databind.introspect.Annotated):boolean",
            new int[]{-716,157,-553,203,926,-81,523,-937,-412,946,-11,921,-679,-292,569,-122,-915,241,503,-242,-638,39,-598,-900,-288,148,-318,-305,-205,-81,28,-866,-321,-545,756,806,-656,660,248,17,218,955,663,556,-686,245,33,-140,-941,-481,560,-793,-716,575,-591,-148,-6,811,649,866,508,754,-905,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasIgnoreMarker(com.fasterxml.jackson.databind.introspect.AnnotatedMember):boolean",
            new int[]{744,-289,992,372,453,-548,-180,575,-217,278,-298,-1,-110,862,-932,623,-25,-872,-156,473,-552,331,-911,440,513,415,-905,-385,236,-248,911,-954,528,-313,79,-208,-721,-581,-458,-38,-113,-255,-874,998,-181,899,-225,-85,-468,335,-174,-601,-959,662,-15,-251,770,145,-602,-550,-92,192,-784,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasRequiredMarker(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Boolean",
            new int[]{-19,689,764,950,793,205,823,267,-501,-510,-2,113,688,978,-387,314,83,913,102,800,606,-192,-36,395,-357,-546,501,127,-572,826,-937,-391,-744,22,-78,-243,783,423,-859,-915,79,705,-174,130,271,21,-962,-816,640,-309,-745,432,-140,-901,-188,842,694,135,977,725,905,-526,-508,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "isAnnotationBundle(java.lang.annotation.Annotation):boolean",
            new int[]{594,-868,-203,-637,323,895,644,-595,-330,183,416,-610,407,-954,-50,-733,-921,833,394,249,-424,-281,-397,-28,918,-290,-691,-109,-846,532,-835,843,598,-53,-762,237,820,-556,-113,161,-237,-967,-942,-136,166,881,20,510,73,943,-732,894,610,398,960,-99,-539,691,570,865,508,-695,699,728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "isIgnorableType(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Boolean",
            new int[]{657,-995,-33,-285,-489,-576,-195,294,297,477,-648,569,269,165,-725,848,-62,-917,485,317,-790,-834,-976,-652,312,495,-769,333,-431,-101,943,-754,-333,-52,764,-882,-325,441,934,-932,-168,520,-229,628,-177,111,-778,707,-848,593,-328,-245,80,275,-173,398,-455,576,234,173,542,-371,793,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "isTypeId(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Boolean",
            new int[]{-338,975,-579,-879,858,818,-986,711,-551,-218,-159,429,394,-760,146,602,860,-63,847,-353,-297,-323,960,-987,477,422,-25,312,555,937,713,260,-34,-120,-206,-357,241,-902,-998,92,-312,-258,-463,-848,-673,792,472,-139,971,610,516,-162,-129,-816,656,775,746,-93,162,102,29,917,30,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "resolveSetterConflict(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedMethod,com.fasterxml.jackson.databind.introspect.AnnotatedMethod):com.fasterxml.jackson.databind.introspect.AnnotatedMethod",
            new int[]{554,63,-412,728,-289,277,-706,149,-652,494,435,767,-479,-939,-790,637,855,575,-155,-450,-906,-574,254,-903,-329,335,480,-689,835,-967,-48,781,921,-634,951,-229,-551,-221,-958,-872,918,403,720,-461,416,-221,-12,15,-578,-377,-773,-565,589,983,-713,412,-329,-365,-494,800,-705,637,80,748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "setConstructorPropertiesImpliesCreator(boolean):com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "version():com.fasterxml.jackson.core.Version",
            new int[]{-115,-838,519,-888,-732,-95,-298,-546,-550,394,-926,-783,919,-431,354,-929,-274,-814,372,-879,-17,-990,794,-549,-353,980,-411,-43,678,-389,-816,974,978,-946,980,53,-880,284,910,881,-188,685,93,-143,294,947,235,84,-586,628,-552,-308,-1,4,385,-729,-677,-105,-784,-51,-41,-957,838,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getAlwaysAsId():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getAlwaysAsId():boolean",
            new int[]{-513,-707,-972,92,312,88,-993,-803,-213,-837,-428,6,41,747,266,401,609,-196,-756,1,-725,445,-809,-470,-109,590,9,682,591,600,803,-320,617,-733,932,78,116,64,-630,-472,659,-105,-489,-801,-966,-294,650,-659,861,-739,-107,80,-12,-840,959,954,297,-250,-506,-84,535,940,-146,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getAlwaysAsId():boolean",
            new int[]{836,950,-829,805,163,484,155,-629,999,-705,692,684,36,-271,376,-274,791,272,519,-270,-831,-34,263,300,-458,466,-735,-687,-473,-518,774,314,-525,14,-890,498,-197,340,118,515,-283,8,-33,339,629,-784,849,-799,634,948,-426,222,755,947,844,-223,-610,206,-936,-145,342,40,970,717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getGeneratorType():java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getGeneratorType():java.lang.Class",
            new int[]{-940,868,-84,-226,79,475,-701,846,25,87,-928,-207,-21,-502,-192,547,-975,123,-837,-726,-577,741,-853,228,-179,-974,123,386,512,236,-614,126,-719,-863,-323,-919,-674,811,-200,14,-71,335,-385,-959,92,476,-864,-117,-819,544,168,941,767,323,-982,954,341,603,155,-272,319,270,-620,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getGeneratorType():java.lang.Class",
            new int[]{775,-367,975,-673,-532,95,-126,-891,899,308,244,322,-44,5,-934,827,-833,-418,574,748,-837,243,841,64,430,142,-155,355,-397,415,643,961,-837,740,-199,745,-721,577,-990,-697,763,-390,970,18,636,-913,-981,-350,612,708,-248,-723,2,-481,933,-600,-43,-116,-655,-276,-215,985,947,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getPropertyName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getPropertyName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-107,-425,-97,461,480,-564,119,23,958,79,763,224,687,-71,-825,970,798,-472,610,901,145,184,560,-499,-224,-733,669,94,473,-663,-877,-90,259,-892,-625,-856,880,-574,821,3,243,881,-294,-928,-477,-545,-103,53,-752,709,-581,894,50,-614,-302,-358,950,45,280,978,-922,-645,484,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.PropertyName", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getPropertyName():com.fasterxml.jackson.databind.PropertyName",
            new int[]{-963,680,-603,-31,96,250,-709,787,-841,-657,350,-265,981,-988,-408,-709,-265,-499,820,546,-906,244,-962,92,388,-833,-867,908,-651,-839,-844,-324,-682,-509,539,-356,-120,-6,340,248,894,436,246,69,578,140,-549,372,953,98,-212,467,267,-823,-511,-542,280,-260,847,-456,237,39,697,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getResolverType():java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getResolverType():java.lang.Class",
            new int[]{-941,-929,784,885,192,782,4,-59,860,359,-486,987,-252,847,-975,-469,202,413,-131,-152,-759,340,885,-256,880,15,-971,-149,-823,5,121,-562,628,-569,941,-228,-596,-654,474,458,742,-427,-462,-147,-660,563,234,-368,-982,219,-269,-89,-937,369,714,947,-255,562,-480,355,249,234,456,-962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getResolverType():java.lang.Class",
            new int[]{547,926,96,-75,769,-385,448,778,988,-315,-755,-961,-719,134,20,-34,-402,932,-274,-121,709,-813,-856,51,-97,-898,-747,-62,946,-650,36,-697,-51,664,-3,-163,734,-98,-750,-746,-947,603,473,520,580,156,245,-125,995,100,311,-413,-343,788,203,-953,-617,661,-728,685,-316,-269,-294,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getScope():java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getScope():java.lang.Class",
            new int[]{-601,-725,-570,505,-971,-30,-22,-498,-317,-266,-815,-88,716,860,-565,-986,-289,-708,-59,-597,-358,771,-854,393,-635,446,142,196,-111,642,455,-759,293,-972,444,-695,49,-886,12,606,-933,664,-586,-547,790,-576,463,628,-667,-644,-326,-750,830,487,553,593,-95,-568,539,-987,514,-340,960,993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "getScope():java.lang.Class",
            new int[]{251,-1000,77,240,693,947,-511,426,1000,1000,690,-166,571,-525,-710,-732,-742,107,940,1000,1000,-1000,1000,423,-547,427,1000,-454,-431,-632,724,239,134,315,-103,684,-1000,-1000,499,-440,722,464,1000,-1000,305,629,-623,274,99,-1000,1000,-847,1000,-1000,-351,34,689,728,737,775,-463,439,155,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:T2JqZWN0SWRJbmZvOiBwcm9wTmFtZT0sIHNjb3BlPWphdmEubGFuZy5TdHJpbmcsIGdlbmVyYXRvclR5cGU9amF2YS5sYW5nLlN0cmluZywgYWx3YXlzQXNJZD1mYWxzZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "toString():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:T2JqZWN0SWRJbmZvOiBwcm9wTmFtZT1OYU4sIHNjb3BlPWphdmEubGFuZy5TdHJpbmcsIGdlbmVyYXRvclR5cGU9amF2YS5sYW5nLlN0cmluZywgYWx3YXlzQXNJZD1mYWxzZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "toString():java.lang.String",
            new int[]{356,52,891,678,-562,-192,-129,-56,251,-289,852,956,682,864,974,-649,-891,381,718,520,296,227,-397,-559,-223,-993,805,-387,439,990,477,230,-458,796,49,-965,-739,-841,-535,436,965,970,425,419,934,-585,-965,-458,980,-576,964,883,-483,-108,-803,-829,-955,-843,787,565,713,91,609,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.String:T2JqZWN0SWRJbmZvOiBwcm9wTmFtZT17SW5maW5pdHl9LTYxMi4zODYsIHNjb3BlPWphdmEubGFuZy5TdHJpbmcsIGdlbmVyYXRvclR5cGU9amF2YS5sYW5nLlN0cmluZywgYWx3YXlzQXNJZD1mYWxzZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "toString():java.lang.String",
            new int[]{282,836,7,661,612,117,386,-885,-99,940,89,-193,580,-757,476,543,-154,-556,21,121,325,-827,-779,-482,-653,312,904,-739,-977,-187,-284,827,831,976,-1,-761,255,-209,20,-840,-822,-218,554,-262,178,383,-772,194,-321,278,660,764,-36,390,478,-72,-518,-785,569,-893,578,90,-450,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.ObjectIdInfo", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "withAlwaysAsId(boolean):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.ObjectIdInfo", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "withAlwaysAsId(boolean):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{341,-626,445,234,-8,248,858,545,78,495,273,10,182,933,524,839,128,516,47,169,489,528,844,-866,110,-758,658,44,-88,-911,193,-605,136,140,885,-796,152,-41,-535,-551,-36,753,-631,307,635,134,-113,291,973,-207,-121,-597,66,907,-116,999,-525,220,478,141,-743,349,-151,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.introspect.ObjectIdInfo", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "com.fasterxml.jackson.databind.introspect.ObjectIdInfo", "withAlwaysAsId(boolean):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{-222,11,-467,664,-912,640,-101,295,897,642,449,-585,466,721,-858,460,765,-719,-690,367,199,346,-245,-226,-294,873,-288,-218,-442,-909,546,596,94,-797,-217,-925,566,101,-246,554,288,550,978,181,381,755,-755,432,518,136,-722,501,-688,-268,873,-211,-773,697,-575,-125,-757,44,-119,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.databind.jsonschema.JsonSchema", DEReplay.run(
            "com.fasterxml.jackson.databind.ObjectMapper", "com.fasterxml.jackson.databind.ObjectMapper", "generateJsonSchema(java.lang.Class):com.fasterxml.jackson.databind.jsonschema.JsonSchema",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.node.ObjectNode,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("JSON:W251bGxd", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsElement(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("JSON:eyJ0ZXh0IjpudWxsfQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter", "com.fasterxml.jackson.databind.ser.BeanPropertyWriter,com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "depositSchemaProperty(com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "com.fasterxml.jackson.databind.ser.impl.UnwrappingBeanPropertyWriter", "serializeAsField(java.lang.Object,com.fasterxml.jackson.core.JsonGenerator,com.fasterxml.jackson.databind.SerializerProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
