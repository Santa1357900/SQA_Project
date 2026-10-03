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
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-382,607,-372,774,-1000,1000,282,1000,-964,-461,123,-388,248,866,-64,-1000,235,1000,-1000,-1000,453,-58,318,-569,76,-501,-856,-202,-944,513,-1000,514,-1000,2,-727,-271,-1000,1000,-1000,1000,-1000,-1000,-69,48,-753,-1000,230,1000,807,-1000,563,759,620,-1000,-577,-270,932,1000,799,-743,513,645,644,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-935,-1000,743,519,627,1000,151,-1000,-362,-170,-453,1000,370,1000,723,1000,-1000,980,1000,368,-1000,1000,-540,-795,-1000,-535,-528,-57,-861,-558,1000,663,130,-793,-230,1000,929,-492,439,-486,1000,-282,-1000,544,-1000,-1000,1000,188,709,115,-531,409,-736,1000,600,769,-1000,-889,-25,74,704,1000,-1000,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-1000,-342,-1000,38,-803,-545,-236,484,-371,-806,-988,1000,884,887,776,-1000,-641,580,-151,240,-292,154,20,-385,-824,300,-588,239,-855,343,-40,400,-542,-3,-141,361,-155,478,-1000,161,409,-73,-1000,518,-443,-1000,414,-47,-348,-933,210,433,-925,951,609,1000,-263,-251,-831,-1000,-556,-387,-93,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{280,269,-1000,-102,-87,-757,826,1000,453,613,-1000,552,894,537,834,1000,-1000,1000,-390,-216,50,162,-50,-360,-580,-164,-507,168,-54,-471,463,958,1000,-1000,72,1000,910,-73,373,672,212,1000,-1000,-54,420,-926,-240,262,851,-1000,-225,-666,-724,231,-443,1000,-1000,364,428,-873,-253,363,-872,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-435,341,29,439,-1000,363,38,818,-740,-716,640,-388,620,2,-362,-1000,1000,347,-1000,-1000,261,-515,54,-315,21,-51,453,-324,-890,1000,-1000,148,-1000,847,582,-1000,-652,87,-1000,842,-709,-662,607,384,384,-79,-499,841,-169,-284,778,1000,381,-1000,38,-749,835,915,336,-532,158,178,644,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-202,632,199,-176,295,93,587,-846,-1000,-461,186,-579,-128,-1000,-448,1000,248,-446,66,373,-322,-908,-905,-1000,506,370,-147,897,919,286,-798,134,-944,-1000,-275,-361,-458,-186,17,-187,-1000,317,448,-403,343,-100,702,623,-844,893,-1000,889,279,653,25,682,18,169,255,-252,-116,-290,595,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{646,-599,-1000,929,160,-58,-1000,647,-668,580,-75,540,775,1000,98,-666,-261,888,-77,827,1000,611,-277,-218,-361,342,-231,-493,-840,-122,814,-512,-711,922,702,-1000,876,870,-923,592,223,250,-1000,-760,-751,-1000,378,149,-664,-672,139,22,-101,-987,0,595,-51,1000,704,-376,612,-400,-862,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{1000,-204,10,64,207,107,-560,-1000,-891,-895,-23,287,-1000,-960,681,657,-252,-530,-881,294,1000,-843,-479,-833,342,442,402,-921,-399,201,837,578,-1000,-192,-248,-1000,-124,916,-751,-989,196,-1000,778,-621,-933,-999,-1000,-927,-122,321,-355,976,85,641,-231,-44,-448,-666,887,485,-162,198,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{769,-581,-1000,-881,204,401,-378,-1000,-271,-296,-81,639,-294,207,196,435,-1000,929,-1000,-1000,964,-685,-210,-1000,-102,931,205,-415,-952,631,125,895,357,796,-1000,-1000,-145,986,-451,-739,-202,64,-735,-180,259,833,462,-875,135,1000,1000,1000,1000,430,-298,349,1000,-1000,323,-1000,-531,-204,1000,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{196,-247,400,79,344,127,-522,-204,-347,-412,-496,-646,-522,-655,-87,55,-663,-158,-254,-351,370,228,-380,-192,409,-289,1000,284,-1000,488,593,72,-107,-213,1000,-654,310,633,-628,-736,-74,229,82,734,340,-391,-300,-701,89,342,-1000,767,358,416,-574,37,419,-499,865,1000,-565,80,329,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{17,-1000,-1000,-461,904,-1000,316,-89,192,75,-309,338,-495,137,-1000,124,323,-511,293,505,-357,-812,483,1000,1000,472,1000,604,719,-1000,-1000,727,1000,1000,-1000,-31,432,-885,1000,-1000,538,-861,-1000,-927,294,582,-1000,-1000,962,-584,-722,393,-625,1000,-1000,-312,-544,-122,1000,-64,303,381,1000,-586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{108,-125,-605,-156,413,-346,-1000,521,703,-1000,913,-679,-1000,47,-870,-424,-989,557,-243,-1000,-706,-1000,-263,-57,-56,-754,-768,74,922,-993,-1000,122,553,634,-751,-747,-916,-1000,949,-85,285,-374,-1000,1000,-1000,859,376,207,1000,-16,860,-891,-574,841,-941,749,-1000,-934,310,-1000,996,-1000,1000,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{855,-372,256,-326,868,-793,-699,-836,-517,-1000,214,594,-1000,-129,-1000,-349,-1000,191,374,-1000,-790,159,171,-64,298,532,-8,48,1000,-1000,359,1000,117,1000,121,-827,-83,725,501,-859,718,-353,-1000,1000,-605,-268,61,-1000,1000,-436,220,-127,122,818,-729,-95,-236,-484,-95,-1000,709,-469,-43,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{490,-129,-1000,-331,-1000,-620,316,-1000,474,-674,-65,-490,13,-329,188,-156,-545,-319,-379,-614,-1000,980,0,-1000,396,246,563,-136,935,-1000,690,480,-1000,444,877,603,229,141,-588,907,45,175,-475,-326,-916,-1000,-92,-630,1000,830,39,790,1000,40,826,-1000,930,113,-1000,-644,-140,-965,1000,-769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{353,-506,62,-696,315,-1000,-857,-381,-561,-1000,292,-932,-1000,-106,7,-311,-1000,5,587,-848,242,-83,90,426,412,247,-299,77,855,-833,-1000,648,1000,1000,-982,-814,-456,101,886,-921,49,-325,-1000,654,-871,507,-205,39,924,-381,-153,52,-122,1000,-771,-173,1000,-851,416,-1000,1000,1000,828,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{236,86,691,-376,-106,1000,-1000,37,123,1000,-357,459,-82,-138,-1000,-108,-636,636,1000,-398,-399,1000,279,-1000,-760,1000,-1000,-283,15,626,-23,315,982,-566,-1000,-331,-975,166,1000,781,631,-72,-690,682,-540,615,213,1000,41,546,98,147,300,-928,-86,1000,-432,-1000,-791,342,1000,-683,100,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-476,246,-321,-411,-676,-300,-1000,1000,-900,316,-635,975,441,1000,1000,522,-212,544,952,-1000,527,257,71,-116,373,1000,1000,746,61,861,-861,-1000,-346,-83,-783,-573,-20,-1000,1000,-1000,-994,520,-1000,-642,1000,722,-759,422,-1000,-178,-2,-741,-1000,-165,625,-8,1000,326,-343,-367,-344,-765,-264,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-395,709,-418,499,-597,-310,-1000,797,-588,-17,-157,113,-486,761,-319,1000,-627,665,-294,-936,750,619,83,238,-307,878,480,432,545,-259,-1000,-1000,-16,381,-919,-109,-1000,-1000,1000,-1000,-430,314,-917,-607,503,1000,-831,-193,-900,-218,-585,-819,-1000,461,223,-285,-142,-184,21,90,411,-653,-441,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-32,-142,-153,-236,-490,98,-1000,1000,-1000,-66,-461,-104,442,1000,118,284,304,440,568,12,-28,298,-218,640,-541,910,319,603,-676,1000,-654,-402,-223,-22,284,-179,301,-1000,803,-956,-619,-659,-1000,-126,843,416,-769,50,-510,-220,-142,-1000,-1000,-70,-763,426,-139,369,531,323,-421,-1000,-22,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-1000,389,1000,-221,-745,-832,139,1000,1000,44,-1000,1000,870,1000,-660,1000,-593,862,-71,72,844,769,1000,-1000,1000,1000,1000,1000,490,-485,-1000,-707,-1000,-450,-898,-757,-172,-1000,862,-896,-1000,883,-1000,-1000,-1000,714,-949,809,-1000,1000,154,-946,-1000,-722,1000,-806,1000,948,1000,-1000,-1000,-769,-886,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDelegatingCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-476,-165,-153,-236,-532,-568,-789,1000,-356,582,-776,299,566,1000,47,521,-45,752,568,-648,527,298,100,24,836,910,737,551,-184,564,-758,-582,-223,112,-842,45,-20,-1000,803,-1000,-563,147,-1000,-663,-938,591,-759,9,-921,29,-328,-741,-1000,-159,289,-8,1000,159,531,-139,-285,603,-277,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-532,125,1000,234,795,1000,163,588,918,812,1000,653,874,356,-1000,953,1000,-296,1000,49,-201,-1000,1000,1000,1000,1000,-524,495,77,-1000,-8,-1000,1000,401,-359,-677,-1000,-104,1000,-1000,-67,1000,-718,-846,-797,-531,20,1000,621,20,66,1000,1000,-20,-1000,1000,1000,1000,-756,1000,415,-1000,-1000,-833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-1000,185,-1000,469,-938,918,-264,38,-1000,483,934,1000,777,-183,-701,-1000,563,323,-920,1000,-1000,-1000,196,-1000,730,-1000,410,326,-284,-46,990,-406,-983,184,779,72,-18,-59,1000,-672,-193,777,-678,-96,-932,-665,24,-1000,679,250,1000,-1000,-415,1000,830,-1000,-558,-657,-336,490,-300,93,153,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-1000,-981,-1000,174,-90,926,-100,629,-1000,-124,-192,-263,607,-834,-894,-1000,408,-176,-1000,563,-482,-550,-1000,-1000,389,245,57,-1000,-662,180,362,311,-43,-29,1000,768,1000,-13,372,-142,-682,663,-537,575,-162,1000,466,-1000,-344,41,-167,-743,1000,865,1000,1000,-29,-1000,-896,-146,-1000,289,870,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{204,679,-198,439,-486,-842,-553,-745,466,-392,192,378,1000,-1000,1000,67,455,-164,49,525,-457,-197,-977,-783,115,-869,-832,-72,1000,-1000,1000,123,1000,-310,-769,1000,-88,-656,82,-619,899,-490,-486,-306,-159,1000,-265,486,-989,22,-427,314,927,-80,-90,643,-1000,1000,-810,943,427,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{55,-676,-1000,14,205,-187,291,651,174,843,274,143,-241,400,400,-930,-242,-90,-892,-248,621,14,-806,400,376,297,386,-1000,305,493,-496,39,-151,-400,341,-285,465,-30,-49,343,400,23,-344,151,749,400,179,-640,195,-384,-165,746,-400,871,-1000,400,-855,350,-400,388,-561,-372,-1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-331,-419,1000,834,192,-504,-825,-532,-1000,-644,-71,-580,-170,-522,-11,238,-110,-114,818,19,-351,143,707,-245,-295,1000,720,20,-140,-130,-447,1000,657,-1000,945,1000,-653,-1000,-332,127,251,-1000,-832,-871,1000,-582,-848,252,-1000,-50,381,253,484,801,408,299,586,982,-605,1000,-407,695,806,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-1000,-1000,707,834,140,-443,38,680,137,512,-14,-944,589,404,-85,89,-330,-911,-59,-155,-224,-443,-496,585,-269,-358,-783,423,-227,714,319,220,707,-263,-473,90,-168,400,915,1000,236,-399,-953,-46,-853,41,871,-322,1000,-1000,-408,-1000,-874,-1000,-110,-939,-84,-350,404,-1000,26,57,237,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-699,1000,731,644,-519,671,-349,-40,1000,140,1000,-188,1000,-50,-1000,-334,878,302,269,-1000,1000,-590,-1000,-39,1000,-238,-555,-372,371,835,1000,339,510,-213,440,-357,91,271,-661,-710,-1000,-297,1000,1000,-498,614,-808,719,-1000,1000,116,-1000,-1000,68,63,1000,-1000,305,283,-603,1000,-382,448,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-887,262,401,834,140,592,359,-819,506,758,103,682,729,106,-926,689,-289,-801,-333,154,-224,137,-252,93,-269,585,-343,-690,-328,-438,319,-65,791,-602,351,677,994,-845,441,636,817,302,679,-969,-207,-551,-989,254,-776,-448,551,-702,-874,155,75,-225,-993,948,-299,-470,96,-776,517,-874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{1000,-478,-830,-641,-186,-175,433,434,-943,-428,95,-586,-48,64,208,-784,-215,-27,-362,578,412,97,841,922,-110,-1000,757,716,383,930,-425,-424,-205,322,9,80,4,1000,276,147,-744,181,-487,347,-19,155,502,-704,984,-267,-760,392,1000,-367,138,-265,1000,-557,848,-36,-728,23,-564,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIncompeteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{0,-530,-48,904,139,84,-180,-340,-460,-789,-897,803,953,920,56,595,388,488,94,-894,862,424,-538,-388,-649,-975,120,487,-821,-786,803,652,-695,-525,289,803,-646,-614,987,-686,586,-554,169,-16,471,697,370,576,54,-24,-185,520,868,-463,52,-960,-184,-728,-566,-491,105,-231,-444,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIncompeteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{-802,380,-889,-426,349,-1000,689,-381,-552,-443,-210,23,223,464,-904,-1000,-96,-1000,965,1000,900,557,-67,-879,-375,267,-594,888,1000,202,631,322,-56,303,1000,-920,229,1000,338,857,-721,-378,-507,-93,-297,-1000,-741,-163,-180,-724,-294,-293,-984,-72,1000,1000,-600,-510,696,284,103,1000,-137,935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIncompeteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{-1000,781,-1000,539,-892,400,235,1000,308,138,1000,1000,-1000,-1000,-303,-897,641,318,-669,-54,-417,-1000,1000,-25,-949,-1000,1000,-1000,1000,1000,-1000,-741,127,182,354,-698,867,583,-1000,604,-532,-1000,-941,-575,-863,-205,963,-1000,-1000,531,1000,1000,-1000,-378,880,381,1000,472,-872,-965,-1000,-182,70,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIncompeteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{917,912,-93,-406,12,666,-1000,1000,119,138,433,-823,-1000,400,-779,-1000,172,318,-138,-559,340,733,1000,98,-458,-9,658,133,143,1000,278,-1000,-774,-702,1000,-699,453,526,457,-197,-1000,-1000,-82,193,-345,624,-51,-918,-1000,663,-926,953,-797,-239,1000,-350,-472,-400,1000,-965,-568,-819,70,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIncompeteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{411,218,453,44,518,1000,-517,580,-113,-309,15,250,-917,-1000,-656,995,990,-103,-336,-1000,-433,272,-167,-428,-1000,475,883,-1000,70,-674,-1000,464,-692,-15,-580,-716,-180,1000,-893,-1000,-414,-177,-606,4,-769,-73,88,-1000,-1000,-368,234,-831,-118,-614,1000,-1000,-189,983,1000,-778,-400,807,-368,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{737,-1000,192,-46,-42,90,-148,480,72,95,-144,558,496,-260,-1000,501,852,-612,257,199,-41,1000,-376,-202,-1000,1000,1000,1000,-451,553,120,1000,981,-31,-1000,-1000,1000,1000,-227,-1000,-282,-444,-531,-378,-110,982,766,-817,-287,-338,7,1000,-58,-408,1000,579,-155,202,183,87,-547,269,-1000,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-61,-738,-68,-281,332,49,-235,-742,915,-626,-41,-824,557,-315,-706,-402,-333,-199,-672,177,1000,-449,97,-331,489,350,-525,24,833,-105,1000,72,489,-521,-193,223,-165,228,-496,-680,-323,205,-69,-949,942,-156,-337,179,422,639,136,-106,-711,-301,-50,171,788,702,-42,628,-229,-667,-202,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{1000,-931,-528,-46,-70,-1000,79,84,72,-939,-527,1000,772,18,-1000,1000,-998,-1000,83,784,-1000,1000,-968,38,-1000,634,1000,1000,-933,1000,774,1000,981,1000,-1000,-484,1000,1000,181,305,1000,780,-1000,310,-718,1000,1000,-692,-146,-338,-1000,5,1000,-24,1000,1000,-342,178,0,-605,-547,1000,-1000,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-216,837,-1000,-336,321,-1000,941,-997,8,-1000,457,-203,1000,212,311,-1000,-1000,-1000,-812,737,-601,-90,-648,260,304,-1000,-273,-448,371,372,784,848,320,752,-8,1000,-658,346,205,495,816,1000,-906,-173,-224,188,-221,-911,1000,518,1000,-1000,699,-151,122,931,756,522,-1000,-73,-78,427,1,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-562,671,-745,-358,918,-942,595,-380,50,-447,-58,-540,-82,-346,-840,-551,-704,-1000,-475,445,-594,-677,-588,258,-80,-27,253,423,-257,-839,522,1000,558,701,465,-20,-930,211,33,-168,893,720,-1000,-610,630,1000,-260,-865,655,-993,-824,-1000,890,-809,245,207,257,-704,-993,-462,-345,377,852,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-136,-844,-1000,34,-101,945,946,-834,-1000,560,-676,252,-1000,-1000,516,-1000,1000,-370,-648,210,-385,768,177,-998,-423,198,1000,-907,759,1000,222,-1000,-1000,113,-470,1000,109,-1000,-971,137,1000,-329,-748,-574,-533,-579,1000,1000,488,215,-1000,-400,1000,-880,-1000,-505,-407,-454,535,269,-609,1000,1000,-704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{529,590,613,-181,713,-410,1000,-1000,-861,248,-1000,156,-756,131,1000,458,-991,-246,-159,863,-1000,496,-525,102,384,908,385,340,-92,-310,-70,558,107,699,-326,540,1000,-117,135,530,1000,364,1000,1000,-921,-804,-729,232,-28,-394,232,-240,-1000,738,351,-784,-1000,720,115,879,-649,441,1000,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{1000,398,-205,-586,-518,1000,964,861,-993,-17,-914,-74,-158,612,465,-782,-477,-1000,-1000,-613,215,719,-100,-204,-1000,290,-464,164,-215,254,59,129,1000,-407,-390,1000,149,-666,-542,832,969,-308,664,260,-1000,496,-125,586,470,-621,-1000,-467,769,-696,456,-1000,159,-543,702,-85,-646,479,19,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-408,316,-638,-81,597,1000,192,581,-1000,-905,-649,-1000,1000,1000,-575,893,402,-1000,-1000,-1000,1000,1000,-1000,-363,397,-1000,212,1000,1000,30,743,655,578,-1000,514,1000,-819,-39,289,-800,-218,-1000,1000,-614,591,1000,-1000,642,329,-1000,-462,-249,-627,583,1000,-1000,1000,345,1000,-1000,-1000,814,49,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{337,1000,607,519,-1000,752,-1000,945,38,-316,-19,-1000,719,-70,-810,778,210,662,-474,-893,974,-178,-1000,-541,-748,-401,108,637,-1000,-226,101,207,40,228,-1000,773,-815,-13,618,-177,1000,-231,279,17,350,599,-534,528,-369,1000,-717,1000,214,225,167,724,-337,12,710,82,1000,447,-140,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-435,326,-706,924,-37,-554,-93,-554,241,-453,-850,-183,-449,214,313,657,51,371,645,981,876,720,-201,-113,-364,-724,56,24,-423,-292,684,496,-19,711,458,878,-158,705,187,644,419,-224,867,-565,881,-42,-986,-670,913,-275,658,572,918,856,-476,-967,225,-271,976,-882,582,524,-535,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{282,-587,-372,204,-845,954,-36,-821,1000,1000,1000,-402,-865,-490,568,1000,308,-522,-293,39,296,1000,1000,-901,-1000,956,96,-392,1000,1000,-238,-1000,-179,-969,-1000,-462,1000,1000,1000,-231,22,-807,231,-178,361,489,1000,1000,-1000,-918,-1000,-1000,-1000,-1000,458,-1000,760,-50,1000,139,1000,-777,912,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-67,-839,-769,563,-537,-246,-617,733,143,73,-180,660,-909,685,-210,978,-330,-866,-658,122,-266,642,533,-957,-235,259,-47,-902,148,-971,-484,-863,-768,235,-843,798,-673,870,-158,656,316,78,131,-13,201,527,429,-323,543,408,2,-955,528,-918,877,-770,150,266,-195,171,896,-915,600,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-365,1000,317,-41,626,613,921,299,-1000,111,240,228,1000,-574,-755,-913,887,1000,325,38,-434,-550,-90,580,519,-113,-244,754,-399,-634,-925,1000,-144,-408,419,-1000,-226,-564,-686,-467,-527,892,869,269,-1000,-220,400,-890,278,641,636,592,-99,1000,536,1000,-657,-1000,1000,587,-821,-797,-17,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{1000,-984,83,694,-689,556,-277,88,456,302,1000,-1000,401,-101,-1000,1000,-572,-781,-333,-60,-92,232,-308,1000,-560,1000,-1000,-4,-601,-1000,-726,1000,-156,518,204,-1000,516,1000,1000,46,-70,-1000,-208,36,-243,754,1000,636,-379,-1000,26,-1000,-126,206,1000,-133,581,335,-669,1000,1000,292,1000,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{1000,-1000,-761,714,284,626,76,-906,398,302,-1000,-1000,19,-665,27,-136,247,-585,-515,532,-1000,860,-282,-1000,0,-330,352,-854,1000,583,-239,-560,32,1000,240,-739,-206,10,-1000,-553,870,-1000,16,403,-939,-907,-843,138,-1000,-443,501,-766,-204,-794,-51,-162,184,76,-957,305,898,852,237,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{92,974,1000,-147,-26,-114,-1000,109,1000,1000,7,-733,1000,1000,-551,-439,-1000,-1000,755,-861,1000,1000,1000,397,1000,1000,-1000,50,-466,-111,208,210,-1000,684,321,479,-684,27,160,-1000,625,378,-1000,1000,-802,1000,-1000,1000,-1000,180,-692,206,-811,-1000,-1000,-1000,-25,-229,1000,809,-819,1000,-767,423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{770,639,183,714,244,185,-691,331,965,255,437,732,-479,-777,27,209,272,377,-7,41,802,-848,643,195,51,303,900,103,935,583,107,55,-512,800,-325,954,673,-322,-468,-345,870,891,-115,-57,739,-197,-843,-717,-304,20,132,945,526,-348,313,-162,-171,-618,-711,433,898,852,-484,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{326,-141,-1000,314,272,-314,-1000,-957,1000,-830,7,-1000,322,-257,1000,-348,547,-1000,-400,-153,-1000,731,-1000,-1000,-256,100,-1000,-270,400,-257,-687,-1000,-445,379,129,-1000,-387,-798,-229,-95,1000,-441,885,1000,-1000,-368,-1000,-99,469,316,1000,-1000,-888,-1000,45,-306,-837,967,69,1000,1000,1000,-711,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{54,395,1000,-331,-27,951,-1000,-286,289,586,-661,400,142,-177,280,-452,267,-810,-1000,-174,216,524,394,-16,354,273,-1000,-654,522,61,464,-53,86,495,312,-38,1000,-935,-1000,-626,335,-786,-196,389,194,1000,-805,-135,82,-440,-131,174,-342,-369,-1000,-1000,419,-280,262,1000,-281,205,1000,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-626,1000,1000,292,390,-94,-1000,-1000,1000,1000,374,1000,1000,1000,-1000,-565,-1000,-952,1000,-1000,1000,1000,1000,1000,1000,1000,-1000,1000,-1000,-1000,734,1000,-1000,471,-569,1000,-985,-1000,1000,-1000,-487,1000,-1000,1000,1000,1000,-1000,-1000,680,1000,-1000,974,-545,-1000,-1000,-1000,1000,-86,1000,575,-1000,1000,1000,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{763,-1000,1000,-196,-631,788,572,-893,326,1000,-1000,-1000,1000,793,-866,-1000,1000,881,215,567,1000,1000,-82,664,-511,-190,93,-287,1000,-1000,872,-863,-773,-750,-463,-1000,-464,-863,1000,-1000,107,-434,-1000,1000,-950,287,-420,-151,-1000,1000,-1000,789,-349,1000,1000,-338,-898,426,-1000,-1000,-858,163,807,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-1000,-1000,1000,-666,994,-531,572,-595,-96,1000,1000,-1000,1000,1000,-1000,264,-1000,867,106,567,1000,1000,1000,857,-149,-409,-819,-1000,-126,-963,659,-236,-1000,353,-1000,-751,-934,-456,1000,1000,-758,-916,235,1000,286,287,253,-191,-993,70,-757,297,772,1000,338,-1000,-386,1000,394,-1000,155,209,-508,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{332,671,-235,69,-315,-568,-758,-806,955,-743,367,419,177,-109,-64,43,-891,110,-1000,-1000,631,-76,435,-714,79,-1000,-1000,-1000,1000,768,659,-396,-394,-397,332,-566,581,43,815,-575,397,53,-1000,259,853,274,-13,-284,865,50,516,148,-82,-233,1000,-482,62,-472,215,150,417,-707,-597,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{585,1000,-108,954,-561,-979,251,-868,-780,119,-1000,1000,-1000,-1000,1000,-1000,908,-946,-766,-1000,1000,14,-856,-1000,-1000,-722,-381,1000,-882,390,92,487,1000,-723,-207,159,1000,128,-1000,-1000,1000,728,-549,-590,-291,1000,446,-1000,1000,50,-498,1000,-1000,-575,927,260,1000,-1000,-647,-159,-1000,130,923,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{1000,102,-14,354,-970,97,-663,-207,260,-546,-1000,211,-842,-440,1000,364,1000,-1000,-1000,-1000,451,1000,-1000,74,-514,-247,-42,802,16,-510,-741,-1000,459,236,677,-152,1000,1000,-62,-1000,-373,513,-712,419,-509,72,-321,240,607,797,-1000,468,-1000,-447,991,560,934,-1000,-447,605,-754,300,1000,-210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{1000,402,472,639,-229,1000,-1000,963,878,-1000,537,180,361,-560,1000,776,-937,-1000,1000,-435,-223,1000,267,271,-29,1000,746,136,-866,-1000,30,1000,-406,272,-110,-1000,-233,328,-821,-1000,901,-996,426,-157,466,-474,266,-361,-236,391,-995,-168,869,-236,-200,200,499,399,-1000,257,177,1000,60,419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-568,101,486,574,152,484,-874,-709,-372,945,-408,-848,-379,341,333,-74,405,-272,635,-420,304,-931,593,-719,322,-576,-824,773,153,93,-561,187,369,-579,-419,998,823,567,-17,-199,-548,-632,-378,-138,557,640,347,976,-625,-267,354,74,292,-25,930,-765,-587,-249,-984,268,354,-593,490,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-514,-683,-864,504,607,16,-1000,856,202,805,-884,1000,-317,-497,-572,516,-113,50,-117,-709,-579,-3,823,-120,-1000,399,-439,161,463,-1000,1000,-322,-285,1000,659,103,94,501,-865,1000,792,-437,-609,-18,-1000,-1000,881,-337,471,-555,-1000,359,664,573,597,-305,148,-973,-100,66,-1000,18,1000,-906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{-1000,926,651,389,-290,-632,311,-135,891,1000,-1000,-831,-903,1000,-541,-784,262,562,-294,-1000,-271,-424,570,-288,-263,977,-781,548,1000,343,295,888,-980,134,-140,1000,-923,-178,243,1000,-1000,1000,-619,-1000,-1000,-959,177,865,-15,-513,-368,705,702,1000,1000,1000,-942,-1000,-143,432,-1000,-1000,1000,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addPropertyCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.CreatorProperty[]):void",
            new int[]{1000,650,128,754,-33,966,-676,362,1000,-775,537,635,140,-768,694,1000,-551,-1000,1000,-465,-206,1000,194,179,-364,1000,620,296,-1000,-1000,258,1000,-374,772,-309,-1000,-896,236,-989,-1000,174,-1000,766,-17,1000,-849,-760,-1000,-608,886,-1000,-529,1000,-236,-450,848,625,582,-1000,179,-499,116,60,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-1000,-1000,305,104,886,1000,999,-13,-405,-786,956,113,281,-709,194,-185,1000,1000,-180,10,-1000,-1000,323,957,-859,704,161,1000,-246,637,843,-630,-84,46,-454,1000,194,-164,1000,-833,-909,-1000,828,-721,-448,872,1000,382,1000,759,-312,-896,1000,-333,-1000,-367,1000,-1000,350,-1000,906,-852,-338,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-659,-542,-13,129,1000,1000,839,-333,348,463,1000,622,-97,-778,-956,847,1000,-1000,98,-702,-1000,-851,1000,394,-920,-406,-777,595,119,-757,1000,-1000,-82,-717,-688,978,39,982,-1000,-665,-344,-1000,1000,-283,492,-872,1000,-7,1000,1000,-498,-1000,554,-1000,-178,-1000,1000,635,1000,-1000,234,548,-414,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-611,-277,984,-286,-464,811,-844,-327,378,-278,21,216,877,-760,131,-576,-1000,818,142,-568,18,-661,382,-330,457,-138,250,-1000,634,1,305,-48,712,935,-879,677,1000,-229,447,1000,-647,-771,-1000,858,-1000,500,-187,-208,322,-614,-372,1000,222,1000,396,-422,-582,-545,-138,347,-160,186,700,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{1000,1000,526,-841,708,907,-119,-285,111,-283,1000,392,510,-1000,182,-563,-913,704,-88,245,-1000,-713,597,511,-687,839,-198,-104,432,-143,587,-1000,-473,1000,-713,842,1000,254,-221,48,-634,-874,-16,31,-263,831,1000,-320,715,514,-327,-1,1000,362,-868,-932,539,-352,443,1000,-332,-449,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-1000,-43,184,939,-1000,1000,592,-484,-827,-1000,1000,1000,-66,-1000,44,-839,-224,1000,-1000,-1000,-929,-389,1000,-933,1000,316,-173,734,-852,-407,-39,-1000,204,38,-258,971,372,387,-936,-97,882,-1000,676,-82,517,-80,-109,462,869,545,-237,-178,426,-699,-1000,-1000,771,97,1000,-165,-113,395,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-445,-106,749,-652,-826,730,-465,-131,-6,-575,-394,505,597,-657,74,176,587,915,-287,-723,454,-571,582,-774,887,-93,238,-778,105,-174,-12,360,782,-467,-664,185,969,-513,238,980,47,-857,-934,682,-803,252,-696,162,130,-392,-108,783,-112,884,883,-326,-576,89,-397,924,-657,385,874,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-641,-398,-1000,549,-1000,-550,-269,-1000,684,1000,-260,-816,-1000,366,-614,794,913,1000,-1000,-1000,-1000,678,1000,1000,364,-1000,-553,-962,767,298,-1000,580,-764,255,-869,536,1000,199,791,-1000,365,-1000,-234,-536,-862,1000,-1000,-1000,-452,-663,1000,-1000,343,610,-679,-464,-819,339,-1000,-1000,651,-554,-382,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{318,1000,460,129,-570,-37,-478,841,-377,-15,-54,1000,-256,-378,710,427,-408,-363,1000,219,224,651,-416,-1000,1000,522,-682,386,1000,934,-11,922,-1000,478,1000,300,327,291,323,37,1000,148,660,-736,-608,-558,-833,160,-825,662,869,1000,546,-618,-885,-1000,-673,1000,120,457,-1000,223,-76,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{-752,-563,359,-807,237,975,212,493,281,844,392,-577,176,-950,-94,-411,679,-698,213,548,372,-943,-225,161,-213,636,85,919,-889,-394,-663,-385,-251,-101,-364,-922,-969,996,461,-456,448,905,915,-316,912,-373,277,-688,-220,-646,302,-637,-382,-278,-196,-153,19,-892,146,-808,-800,182,-556,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{467,-252,841,34,-939,806,156,996,-904,1000,823,175,-548,-987,534,-1000,-402,-228,-363,-799,-366,185,56,-376,-695,-168,30,-39,-1000,118,468,-781,-1000,631,1000,-704,32,1000,-493,308,-434,230,561,-197,-1000,-707,-518,-1000,961,673,1000,-1000,522,244,1000,-758,-516,730,100,1000,-1000,-468,-626,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "addStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,boolean):void",
            new int[]{955,332,-913,-56,395,1000,-322,-997,1000,-966,-1000,634,729,1000,1000,163,-112,-732,1000,1000,-359,-509,266,-382,-544,1000,274,1000,792,-370,217,986,748,76,83,-893,-269,-188,700,-741,-190,-16,456,-238,863,1000,-522,457,-972,815,99,868,1000,-1000,-1000,1000,-467,-599,644,-1000,1000,1000,1000,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "constructValueInstantiator(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.ValueInstantiator",
            new int[]{678,-819,-1000,-821,252,134,619,1000,523,271,-179,-636,369,414,355,-273,570,-1000,-255,-1000,-406,-722,897,-250,-374,297,-1000,1000,-545,-187,-1000,275,1000,28,-260,-317,188,-34,-898,-765,1000,618,1000,1000,-1000,93,-1000,-736,-1000,-813,-238,518,924,-903,91,580,441,572,817,-728,134,-341,-566,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "constructValueInstantiator(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.ValueInstantiator",
            new int[]{-560,67,-335,-131,-540,38,1000,-261,-1000,537,-631,19,136,1000,-646,769,164,-25,1000,965,-394,538,729,1000,1000,-336,937,354,736,723,-125,-11,-559,862,-752,-570,-967,108,-280,-131,1000,227,-1000,773,-125,-297,1000,-1000,1000,-1000,289,-1000,-399,-1000,225,1000,889,1000,903,-835,-605,645,-230,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "constructValueInstantiator(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.ValueInstantiator",
            new int[]{-541,463,-94,-446,759,400,525,522,-1000,-393,-385,1000,639,1000,11,-441,498,-85,428,501,-759,-1000,941,478,-562,394,779,690,-738,-1000,-330,1000,-20,286,-482,-598,1000,-355,-1000,697,-698,1000,400,1000,-748,410,473,-495,1000,-887,-101,338,946,359,-92,363,-555,700,773,-400,665,499,-528,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "constructValueInstantiator(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.ValueInstantiator",
            new int[]{-395,-288,-852,-806,812,135,130,1000,18,-119,-779,-269,726,220,386,-706,406,-695,442,-890,67,-281,944,-975,114,216,-582,847,19,333,-277,552,998,-213,-778,-503,-43,-420,-1000,-818,467,1000,515,921,-854,202,-748,-775,-836,-311,-400,-269,-10,-329,-251,34,1000,515,929,-552,-294,-290,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "constructValueInstantiator(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.ValueInstantiator",
            new int[]{400,669,-646,956,-721,1000,172,-668,-441,-484,638,49,486,-528,1000,-1000,-666,959,1000,-331,-1000,497,568,-431,527,414,331,1000,-665,67,1000,1000,51,-1000,579,-1000,-244,49,1000,-488,836,60,-1000,130,-333,693,842,-669,342,1000,385,-1000,-735,-70,1000,837,494,-20,486,1000,-217,805,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "constructValueInstantiator(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.ValueInstantiator",
            new int[]{678,-1000,-700,-431,-862,-196,1000,300,-210,1000,499,-636,369,-144,-412,613,-425,-784,-577,121,-406,250,1000,849,-374,-1000,362,331,855,141,-187,-248,50,-803,-1000,-4,-296,199,-55,162,1000,256,1000,1000,-875,-121,-1000,-831,127,-1000,-324,963,1000,-1000,-295,333,430,583,504,-1000,-651,-484,-282,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "hasDefaultCreator():boolean",
            new int[]{301,786,-240,54,261,-402,-426,301,-58,-362,-608,-696,-647,668,196,935,775,295,-883,995,308,-423,-869,-716,73,-401,-515,-907,830,-676,351,-170,-127,641,865,972,-79,429,294,-639,-556,-348,-589,958,-649,48,-677,615,-683,-207,-22,-827,-878,689,376,232,364,872,-911,-668,-357,822,-848,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "hasDefaultCreator():boolean",
            new int[]{892,-801,243,-66,-564,486,-803,165,48,-573,-524,722,-326,788,419,945,285,343,884,34,256,-700,-392,-51,-515,-265,457,62,599,420,-437,-661,-355,20,-879,299,571,540,-885,836,480,106,-368,-917,-838,696,573,928,724,-724,539,-429,-720,-59,487,-488,598,129,326,-214,-616,-257,64,895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "hasDefaultCreator():boolean",
            new int[]{-1000,851,-823,429,887,619,640,-446,-120,-375,186,-528,-778,742,-499,173,-743,-992,580,-1000,315,-783,918,-933,588,-109,42,-81,686,252,-494,727,149,747,-874,-656,768,446,-650,-525,-102,567,-800,17,-164,543,805,673,-129,102,896,981,-175,-67,737,-493,808,271,-167,-668,733,-528,911,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "hasDefaultCreator():boolean",
            new int[]{-1000,1000,-1000,733,-512,-53,1000,718,228,830,569,-220,-1000,-700,-646,-1000,192,-601,142,-434,229,1000,38,-694,1000,-231,-1000,134,-1000,488,1000,1000,1000,106,-718,-1000,-424,138,173,415,868,-82,-389,-337,268,-907,-1000,-1000,443,51,186,337,2,-516,-419,1000,-1000,642,-1000,-401,642,-875,-579,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "hasDefaultCreator():boolean",
            new int[]{-1000,540,-672,54,-65,444,29,-436,402,-362,498,-529,-1000,-148,-206,-832,-1000,-327,1000,-247,-241,-401,1000,-400,268,432,-515,-84,424,1000,-1000,1000,-12,-671,191,-894,847,352,-1000,-346,714,761,-1000,-153,167,703,666,581,-439,-207,-22,339,688,-650,376,-349,69,-119,-193,450,80,167,400,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "hasDefaultCreator():boolean",
            new int[]{-1000,-677,-1000,589,-232,72,-266,-511,380,991,1000,861,118,-753,48,-1000,-1000,872,1000,-1000,740,748,1000,387,183,536,-610,-222,-618,659,-725,957,-797,-530,-171,-753,-373,-980,-59,384,-695,-518,926,-24,1000,-325,38,-124,338,-307,1000,720,-379,-645,-1000,230,-766,-1000,-240,538,-157,-200,232,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "setDefaultCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{565,-1000,394,-951,859,-92,-756,-250,261,-353,1000,-214,370,-69,-447,260,-627,-392,991,4,263,1000,54,541,-1000,737,-581,165,-397,542,519,308,-683,-471,1000,781,1000,-899,1000,997,1000,1000,-841,-998,-1000,-1000,-37,-1000,-394,-1000,-838,1000,-238,-471,775,1000,289,-997,590,798,-707,712,-1000,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "setDefaultCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{1000,-284,1000,-246,979,-1000,361,686,1000,-1000,1000,-284,1000,-1000,-460,771,128,905,771,-1000,317,1000,725,-653,-1000,-426,-74,-532,-955,-1000,361,-906,-291,-148,1000,677,387,-564,1000,1000,404,1000,661,-923,-198,1000,1000,-1000,23,42,-80,284,-1000,423,1000,189,874,205,1000,-407,-1000,999,-254,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "setDefaultCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{207,-1000,191,4,711,804,-454,439,326,592,567,332,231,-1000,805,82,-358,754,1000,-517,442,817,66,-139,-1000,129,-777,-940,-378,305,-295,817,446,-366,530,1000,776,-207,928,1000,1000,531,-957,-770,198,683,31,-1000,-227,-534,-422,1000,-1000,338,959,289,1000,290,842,-1000,-363,333,710,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "setDefaultCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-481,-1000,461,-386,-699,605,1000,-322,950,34,1000,1000,-1000,876,908,270,643,-2,927,-385,199,869,-1000,66,457,452,-683,-745,131,-391,1000,579,211,-1000,-38,-63,-232,-594,866,1000,1000,331,-877,-930,-781,-240,-525,-973,11,453,987,222,-316,436,68,1000,969,-293,-238,342,-631,46,140,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "setDefaultCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{17,-834,543,-626,859,1000,-1000,-400,-1000,629,546,-1000,259,-312,-535,-262,-867,74,1000,-74,400,1000,-162,156,-1000,114,-1000,-503,-794,542,-114,1000,-407,-407,610,393,1000,-192,1000,1000,1000,1000,-711,-563,687,-217,-494,-1000,-345,-351,-730,940,-121,-135,1000,410,980,85,445,238,-98,1000,-457,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "com.fasterxml.jackson.databind.deser.impl.CreatorCollector", "setDefaultCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-479,1000,566,-8,22,-1000,855,1000,571,-673,-250,93,-245,-1000,940,-582,846,916,325,-1000,914,-487,-261,-1000,-559,305,-1000,1000,-954,-1000,-958,259,415,-481,-342,102,1000,559,428,627,223,28,-167,619,1000,780,-344,-357,113,-819,393,-756,-1000,1000,567,-898,-1000,1000,779,-977,563,517,66,1000}));
    }
}
