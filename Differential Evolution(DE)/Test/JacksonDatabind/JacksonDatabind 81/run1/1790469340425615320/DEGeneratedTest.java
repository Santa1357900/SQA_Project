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
            new int[]{-407,-102,479,-868,-436,659,441,-459,-27,753,617,716,-825,345,-349,752,44,796,30,-689,961,-834,442,261,920,-679,776,-13,923,337,-69,-194,-544,732,-262,-393,-974,923,55,-556,-507,289,321,595,239,-780,-228,-625,-886,616,-140,-146,-503,-545,441,-440,-133,-774,-199,-768,787,327,-305,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findAutoDetectVisibility(com.fasterxml.jackson.databind.introspect.AnnotatedClass,com.fasterxml.jackson.databind.introspect.VisibilityChecker):com.fasterxml.jackson.databind.introspect.VisibilityChecker",
            new int[]{291,281,-616,-999,-858,829,40,-209,-107,38,916,-98,-854,532,736,-386,794,-711,-24,288,-629,301,390,-879,472,887,-990,-553,-329,-800,-719,405,5,-208,-756,964,385,-392,437,-459,-920,278,-464,-265,-194,155,-318,-942,903,-816,-122,997,568,-584,917,-827,-814,-285,854,-15,-580,-924,221,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findClassDescription(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.String",
            new int[]{-913,-653,-649,-478,95,710,-240,-73,914,650,-302,105,598,-210,434,-698,27,-539,221,-759,959,110,248,457,526,539,376,-318,245,-394,143,-823,-241,426,437,-665,619,102,117,-97,644,-491,-130,714,655,-207,636,-53,720,875,55,654,425,480,-475,492,-843,10,762,-532,-117,-115,-20,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findContentDeserializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-947,-50,691,-846,667,-616,-174,892,-839,191,-498,361,73,166,912,-608,-564,-872,-354,-503,-152,-657,-798,32,501,374,470,-18,516,-520,668,999,-236,401,397,-315,-567,881,-957,267,70,586,550,807,-454,222,-218,863,-532,-391,116,127,-90,-793,-284,-7,-968,185,996,-788,151,492,-791,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findContentSerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{690,264,-526,-698,-348,-852,-352,279,98,-84,-161,-666,-358,476,-395,-254,77,626,-308,-883,196,96,911,275,133,-209,-401,414,-670,-878,-769,-624,292,157,337,-441,-81,-921,39,535,-593,-620,247,-870,437,-844,-171,473,-295,895,932,-717,-278,208,-986,-423,-757,129,139,858,-310,-213,-388,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findCreatorAnnotation(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonCreator$Mode",
            new int[]{-297,-342,172,708,466,-429,142,545,300,-734,-462,-305,899,955,-52,318,316,-899,72,844,527,-507,-777,319,442,-815,305,-39,5,132,745,-947,-848,60,-815,-508,541,351,-750,-872,-764,526,-819,331,-206,304,-277,-811,-407,-844,315,144,646,-250,-708,967,640,-224,-378,-467,-518,249,-499,-985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findCreatorBinding(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonCreator$Mode",
            new int[]{-739,627,816,50,858,249,-914,600,892,208,943,-934,575,768,-952,79,-442,36,-389,743,839,-131,-668,520,216,-439,-250,-867,-106,-460,851,286,924,-772,-734,-338,-490,960,124,-969,-96,-523,-227,553,-395,-161,913,395,883,-151,463,632,276,48,-540,-928,612,-802,-347,119,406,-224,-898,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDefaultEnumValue(java.lang.Class):java.lang.Enum",
            new int[]{976,-435,-86,-980,-609,-699,577,-860,323,587,-857,893,-801,-321,240,-878,-514,327,-678,356,137,12,291,319,239,74,969,546,877,551,-163,-771,629,863,320,400,885,-396,18,-666,210,672,-230,680,-634,706,-792,-779,256,382,-472,-594,-907,-503,640,845,360,988,535,783,-233,5,-246,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationContentConverter(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Object",
            new int[]{-623,-685,-11,462,-194,-992,798,-211,-977,843,-639,90,-467,117,-680,643,-900,-513,-253,-262,910,-98,44,-180,-824,796,187,-677,768,932,710,701,-285,743,620,371,-755,556,-248,-405,-630,627,-994,-296,-976,606,-625,462,202,-296,686,872,-348,652,-400,-721,-718,323,-440,221,-356,-174,-910,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationContentType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{-587,701,-87,-732,-528,324,547,-620,-368,768,-195,-739,4,-605,284,-828,-490,-6,-748,808,-618,145,825,896,-668,128,-436,-727,-758,-304,84,-290,-589,688,106,313,395,-438,63,313,-670,183,856,-724,-775,-86,616,-686,-814,993,-524,775,782,450,227,-956,831,-568,503,596,-215,668,644,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationConverter(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-669,-245,-56,517,252,518,948,598,936,-224,468,-971,-848,977,-155,756,951,992,22,-170,-862,625,369,1,-319,841,-580,291,652,-670,-848,959,-59,-112,-481,-131,-857,938,-249,966,-186,-487,-846,-715,554,-973,-981,735,-699,-776,655,-275,618,861,526,-315,800,-126,6,448,140,46,360,561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationKeyType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{379,243,-722,-249,-986,-248,-126,-253,-723,72,-94,190,13,-717,-632,578,-549,-315,-102,-817,-716,782,-204,395,238,485,-632,943,-619,664,-728,827,926,-126,-417,299,-648,857,903,773,293,-757,972,-855,820,766,986,-306,528,-720,-910,-530,105,859,372,-147,983,351,-912,858,87,-93,-185,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializationType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{-829,-476,136,297,77,269,834,803,906,-928,446,-97,658,-383,257,-400,-785,-343,926,802,-702,414,939,73,-263,934,621,-949,374,-689,-1,-923,638,-678,355,-804,-284,170,-52,879,85,734,14,-402,154,29,-63,-356,-208,418,234,514,560,-483,910,598,643,722,614,891,675,800,-955,66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findDeserializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-199,835,712,254,-375,126,255,-612,909,-978,43,-451,-87,-245,37,980,66,-56,-511,991,-135,907,-302,-840,-846,-894,409,43,41,119,409,226,100,-191,72,831,952,-888,-173,365,859,-789,-173,198,-477,796,464,-96,934,543,776,180,-192,18,319,-866,954,778,-421,-637,681,615,648,423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findEnumValue(java.lang.Enum):java.lang.String",
            new int[]{-497,158,477,-991,22,-639,448,-551,656,74,-100,-815,274,-612,389,801,-291,-992,-488,487,682,147,-938,-58,-558,117,295,737,-85,705,-133,645,-207,455,44,-891,914,780,-873,-512,523,-525,675,-970,590,647,-929,-388,177,-665,273,-798,-904,160,140,-267,-481,250,666,-381,85,-645,643,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:3:49:java.lang.String:dWNfU1I4T19NQWpKdFYzQWRabjQtdw==:37:java.lang.String:YWFhYWFhYWFhYWFhYWFh:49:java.lang.String:R09XZ2FCQS9Od3ZTSm4vMFNNVG5lCmI=", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findEnumValues(java.lang.Class,java.lang.Enum[],java.lang.String[]):java.lang.String[]",
            new int[]{307,-864,466,-805,-894,-275,351,-801,-226,-492,347,172,651,-574,835,621,860,-660,-290,-165,107,942,613,-468,-795,-636,-887,-981,-933,-545,-422,-8,-607,-468,-794,248,690,-513,-817,-831,398,-384,263,-368,-126,-771,-815,-816,-217,191,-678,-253,-940,-97,-332,-75,994,906,900,-939,-758,298,-427,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findFilterId(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-36,41,-497,-876,-575,881,-149,946,-104,747,513,-932,-298,359,523,-383,130,594,-395,619,544,-262,-550,120,-822,-281,546,907,283,-81,593,164,-372,-665,274,-749,949,-100,170,479,-90,-411,930,195,-660,-459,-517,824,100,931,-965,580,-215,523,-161,507,662,963,-631,222,725,715,377,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findFormat(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonFormat$Value",
            new int[]{-240,934,599,-377,507,-814,-84,-475,709,212,-23,-107,-247,-868,29,21,-861,-615,751,-569,-121,-751,-214,-554,-857,37,-345,867,372,432,477,-943,-724,493,-7,-140,547,556,488,-519,417,452,386,706,743,-763,-472,631,732,-980,-556,-507,-942,327,-718,81,-256,945,500,747,-120,-779,886,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findImplicitPropertyName(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.String",
            new int[]{-669,-815,-237,194,495,-414,-202,889,833,-800,-542,-360,470,-322,-37,254,-614,-64,534,980,981,155,-478,68,-869,25,-83,-660,-319,-483,255,-425,-12,-326,-171,-732,-250,-351,-907,558,-875,534,116,-988,-68,412,79,-677,586,491,905,977,-653,-813,346,459,-552,-418,98,-322,-284,-173,873,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findInjectableValue(com.fasterxml.jackson.databind.introspect.AnnotatedMember):com.fasterxml.jackson.annotation.JacksonInject$Value",
            new int[]{401,-646,263,64,186,-741,-12,171,16,868,619,860,684,930,981,453,-816,529,-499,-182,-522,-841,-949,605,-992,-863,24,242,166,-941,-374,-220,-794,-853,398,-211,420,-35,-900,221,788,-779,256,386,-233,-861,916,-985,395,-761,459,-246,-514,279,-355,372,308,566,544,482,861,-110,-554,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findInjectableValueId(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Object",
            new int[]{293,747,-563,-923,330,-741,337,-546,183,266,354,-37,-430,778,606,345,60,-640,343,-529,908,458,581,-998,-121,-865,-827,-140,-908,885,-143,-968,-373,206,-831,-4,-18,568,890,842,341,-449,135,13,-959,23,180,534,-364,-541,609,-420,521,766,-496,-740,206,-695,-966,645,13,194,-972,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findKeyDeserializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{609,287,838,-520,28,-214,795,-420,-584,-150,-711,-172,889,-180,190,-969,-798,975,30,46,566,815,180,240,663,-311,826,-411,-396,660,-847,818,-834,417,500,933,-733,522,663,-7,356,290,40,-963,155,-784,-176,715,-975,24,-916,-665,124,-897,-760,-165,468,-572,-817,-546,-968,-149,70,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findKeySerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{-745,536,119,-784,62,-382,-883,-598,-766,-945,243,421,62,-195,437,900,-684,-47,311,264,-564,-7,488,840,917,-110,968,-263,-806,874,311,932,835,-968,-782,592,-621,-660,891,-173,-203,13,468,784,179,-241,-223,281,45,880,-341,10,436,-993,216,-223,-190,-291,-540,176,-300,-780,-956,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findMergeInfo(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Boolean",
            new int[]{-8,141,-584,-711,644,-889,-917,311,515,-83,414,-600,-60,805,-872,52,530,-552,-126,588,-373,43,-58,-124,-956,-113,352,601,478,987,691,664,784,-736,-189,-593,-540,-340,-444,-676,171,88,-932,-327,-106,584,874,-21,-630,222,993,795,930,-571,-442,289,500,-850,120,243,-396,-968,687,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNameForDeserialization(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.PropertyName",
            new int[]{277,-807,-461,434,863,-487,-611,-396,-22,-735,693,-603,163,-833,749,-580,-53,-460,573,-849,165,-683,-348,126,756,-810,-18,-716,528,55,147,798,-828,131,-176,-114,-267,799,-271,-115,681,532,-213,-835,-241,90,-882,-680,833,606,-760,560,164,-873,881,169,-333,-279,730,-477,45,361,-477,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNameForSerialization(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.PropertyName",
            new int[]{-687,524,772,-422,-721,-655,-966,351,301,-750,-741,-854,705,-123,-854,-831,-484,200,255,872,-183,718,-648,-984,-627,-382,-759,-912,432,-384,372,415,-617,-928,-412,-316,847,-622,-466,-406,-316,657,-546,848,372,-647,-661,-166,301,422,777,974,-407,-462,-817,-53,-739,956,-397,-209,-248,508,-255,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNamingStrategy(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Object",
            new int[]{378,919,-672,-386,754,-562,554,-62,-243,-500,22,757,297,322,283,-542,487,-116,-426,777,839,-302,-606,-542,-235,224,-200,-946,-863,-266,-76,-302,563,932,88,-645,248,9,-17,594,-161,-25,796,244,491,98,-47,398,-473,37,-841,226,-454,911,-176,786,609,-85,609,-412,-804,354,-148,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findNullSerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{933,-753,138,439,-625,462,626,857,64,-651,102,807,-16,173,432,776,-433,-662,933,243,194,428,-5,-233,104,18,-363,132,838,974,-996,832,-785,234,-883,54,0,146,-259,-928,-55,303,162,-498,678,526,877,212,23,-848,690,-141,786,107,-392,-999,781,-623,23,506,-96,438,-847,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findObjectIdInfo(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{727,676,143,-678,-909,533,-688,579,765,-863,-792,837,-562,932,-532,856,-961,-351,-994,-590,635,-731,-36,175,519,750,-931,-523,308,-105,-380,-470,-992,156,-729,265,112,-866,-386,823,23,-922,-370,-639,-659,-238,69,952,-102,812,-37,43,-175,-270,-370,180,352,-313,786,-447,-383,-525,-348,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findObjectReferenceInfo(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.introspect.ObjectIdInfo):com.fasterxml.jackson.databind.introspect.ObjectIdInfo",
            new int[]{-128,-630,-217,-622,489,-239,879,786,-636,520,276,749,-296,754,83,45,-868,-881,-797,1,-773,509,-774,484,213,-78,-499,211,247,356,931,5,733,480,-151,-528,-791,-786,198,-974,-540,317,151,198,752,632,296,-291,312,726,568,946,-455,300,208,124,836,-986,-782,-277,281,97,999,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPOJOBuilder(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Class",
            new int[]{-146,23,884,-528,-307,559,364,-919,-405,-329,-505,-306,737,934,-30,861,711,204,362,402,-79,977,473,128,0,-101,-754,480,460,-930,526,783,158,-812,998,-906,384,-708,-481,258,338,908,665,713,-352,691,-130,-165,167,371,747,153,745,-283,-656,655,-41,-459,971,579,811,-655,983,-440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPOJOBuilderConfig(com.fasterxml.jackson.databind.introspect.AnnotatedClass):com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder$Value",
            new int[]{-223,385,-584,330,558,-542,168,-560,-800,88,573,889,154,165,434,-567,702,-470,785,743,-529,889,338,-190,-777,815,-340,146,402,837,-879,413,491,437,-627,-684,-428,898,-418,132,642,-638,-983,-937,235,225,-323,780,667,31,-372,-31,-152,403,459,-532,9,-764,-155,-327,914,-633,-413,300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyAccess(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonProperty$Access",
            new int[]{633,-582,279,70,-979,737,613,349,712,98,956,-551,224,-876,-199,-78,410,-690,589,-659,579,891,896,-597,-395,639,-248,453,420,-377,-159,501,-551,-908,-367,-718,367,918,283,-580,-104,282,-480,315,236,719,-314,57,260,988,-407,340,-437,-230,-89,-879,104,-271,-538,-367,-682,-920,946,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyAliases(com.fasterxml.jackson.databind.introspect.Annotated):java.util.List",
            new int[]{715,-516,-329,395,605,-348,307,510,421,753,740,215,784,-227,23,513,183,-665,-727,605,148,354,292,637,-14,-625,-137,-436,105,6,-91,-442,-461,-186,-938,351,-95,469,-393,390,777,-3,-741,750,165,-436,-227,380,671,-590,8,-559,771,-771,-268,-751,-403,-389,187,660,666,984,252,-407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyContentTypeResolver(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedMember,com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder",
            new int[]{-279,750,-539,155,-841,5,-623,-633,-77,-460,-888,735,363,-314,468,-599,909,-154,643,138,400,893,872,-736,22,930,544,-863,467,612,641,-960,983,189,-174,897,-646,186,258,-339,-472,-795,320,469,622,-284,-157,-338,-698,-78,107,659,-798,755,-50,313,-58,-79,628,874,562,-476,-256,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyDefaultValue(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.String",
            new int[]{588,-714,514,-440,-247,455,414,-992,212,-508,-184,1,-697,979,-114,-492,-624,601,70,28,-41,-886,-650,666,84,698,423,-528,942,859,195,-13,-33,187,-937,539,-868,822,346,725,838,237,-81,-275,87,100,599,-357,-181,388,190,-134,-740,698,192,-372,924,-293,148,-614,518,166,352,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyDescription(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.String",
            new int[]{811,-771,-377,-72,-713,712,-129,75,219,-733,-857,36,495,-638,352,123,999,438,-157,287,337,374,135,-922,-714,-579,890,787,313,418,173,479,691,782,970,-710,-241,-178,-450,282,715,-755,766,385,917,-616,674,520,171,621,947,-132,-301,794,474,-581,-413,913,-713,453,-638,-549,458,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyIgnorals(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonIgnoreProperties$Value",
            new int[]{-297,-25,157,-836,727,785,-311,696,-192,389,-410,-650,542,-835,-324,500,-892,891,-128,-360,-131,-92,-613,-470,-19,742,-775,40,352,131,-29,334,-451,-998,-566,349,-188,-560,-294,595,204,898,453,245,-921,-989,862,-798,605,-762,543,362,-191,260,-73,-329,-198,582,-791,-296,-789,81,-285,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyInclusion(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonInclude$Value",
            new int[]{-308,-57,-793,116,779,-284,686,849,95,-906,405,86,-876,122,-304,562,-744,-83,-209,-722,446,-258,-294,895,952,659,-162,-70,-655,-389,987,716,48,-736,-18,-713,-875,274,976,-664,-217,-207,-392,-854,-904,958,-330,36,825,860,745,-735,-76,-908,309,-445,259,-667,-756,-535,430,621,-619,-346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyIndex(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Integer",
            new int[]{599,-943,369,391,792,945,721,673,478,317,469,112,-791,-791,-991,627,-538,298,80,-450,-975,990,569,694,131,-269,848,968,-323,-271,773,-193,-884,660,-698,881,439,498,-23,865,15,-851,-529,412,-972,-740,-152,-114,-271,-782,-769,907,788,-818,143,305,676,779,-16,-311,-835,-995,-216,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findPropertyTypeResolver(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedMember,com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder",
            new int[]{-605,325,-878,153,946,603,-876,414,-257,-42,-245,-468,-375,345,-184,-382,864,-585,-976,-863,-456,-256,-323,598,683,-634,956,-153,246,283,-970,-887,442,898,-954,-846,-220,-79,811,305,239,-825,531,78,612,965,304,51,-424,173,-757,-763,377,107,-920,853,-839,261,898,477,900,-378,229,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findReferenceType(com.fasterxml.jackson.databind.introspect.AnnotatedMember):com.fasterxml.jackson.databind.AnnotationIntrospector$ReferenceProperty",
            new int[]{-8,-882,612,-84,447,-69,742,864,-192,-721,701,623,-611,759,130,837,695,-870,730,133,583,593,-45,409,-814,658,-695,-159,352,-699,-722,411,-151,-633,-393,-627,872,-306,-79,53,-529,-652,175,-339,474,-502,302,354,-823,998,411,728,-388,496,544,101,591,67,539,-531,-148,757,-161,903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findRootName(com.fasterxml.jackson.databind.introspect.AnnotatedClass):com.fasterxml.jackson.databind.PropertyName",
            new int[]{874,606,-832,336,-67,765,450,-181,-169,439,-948,-779,-53,-489,808,-895,-910,131,-113,557,-544,-978,337,-813,-417,611,-654,-361,250,154,733,-965,112,-596,670,51,468,988,-94,-121,-976,-356,-118,189,779,742,54,-616,201,-918,428,-47,795,-108,906,509,-185,-863,-645,244,-583,-213,-513,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationContentConverter(com.fasterxml.jackson.databind.introspect.AnnotatedMember):java.lang.Object",
            new int[]{600,-819,-224,-650,875,-886,136,797,491,-978,402,-850,-457,-482,8,-726,-296,2,-955,-912,-512,-748,712,-826,690,428,318,563,-359,-793,-216,-565,-772,653,-460,-776,-248,-486,-458,-325,654,180,-302,756,913,224,-552,-957,-536,-386,429,-856,387,35,-761,-922,808,-510,-866,110,483,371,177,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationContentType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{612,-238,517,-226,767,-460,-776,-470,905,234,778,861,-541,573,-292,-789,91,-664,774,354,-789,-942,183,59,939,775,242,-959,-727,-554,-713,457,543,724,604,44,119,818,421,808,-253,348,78,-854,-78,266,949,-765,-734,-769,855,-415,-17,-890,-697,-236,-819,699,149,471,86,-790,57,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationConverter(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{362,-807,-916,414,664,818,-118,547,-606,-450,306,638,-966,-967,548,-616,736,875,-188,-190,-943,15,-94,570,-113,-805,212,-750,-876,700,238,916,154,25,131,-401,536,-1000,501,124,393,-970,694,788,990,583,-733,-280,-275,821,-182,-529,-674,-616,-430,-647,446,688,-864,-640,31,-121,-250,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationKeyType(com.fasterxml.jackson.databind.introspect.Annotated,com.fasterxml.jackson.databind.JavaType):java.lang.Class",
            new int[]{467,-237,548,-787,136,471,-147,827,-889,773,-614,-600,-782,-585,-287,-526,96,-959,-839,-195,-896,852,10,-809,946,270,664,988,70,67,-33,-922,-162,-133,-555,-586,-781,-296,-332,-38,346,409,153,-144,649,995,267,-140,-524,941,-548,902,-882,-738,751,-686,2,636,97,718,-593,-517,-886,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationPropertyOrder(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.String[]",
            new int[]{-614,621,-89,-930,-840,913,883,306,-36,-182,-994,867,19,-863,377,-671,512,-120,-412,117,144,-355,-96,946,-785,929,445,248,717,-41,369,898,952,741,-647,-758,315,-241,-898,-378,34,949,37,398,916,436,860,-195,-557,-128,235,881,901,165,-482,202,695,-117,-876,-856,-567,688,923,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationSortAlphabetically(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Boolean",
            new int[]{-209,-409,-783,660,-342,355,582,941,-27,-37,-314,-116,72,-447,856,-683,-251,119,-497,537,-937,-322,576,718,-772,408,771,-715,-826,-219,-286,885,-928,73,-714,430,-59,471,379,-892,-712,-533,516,202,689,205,-69,132,178,31,40,-824,364,-387,496,40,337,695,-542,628,-461,788,146,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationType(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Class",
            new int[]{-193,-370,426,-447,-884,-753,-318,-683,-729,-407,-188,14,-500,170,-747,-166,-52,918,-962,-462,295,-602,-375,969,866,723,-197,68,-780,-258,231,178,-639,230,617,229,-48,640,69,-260,228,362,708,464,-363,362,-680,-997,-311,-409,471,-279,-31,-395,-672,906,804,466,-769,-732,722,-806,-45,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializationTyping(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.databind.annotation.JsonSerialize$Typing",
            new int[]{-691,-953,934,-825,855,-734,-789,189,819,535,857,-127,372,-398,716,770,836,-209,-459,-112,182,107,708,-566,-985,952,-387,-429,-399,801,148,-98,306,653,-6,657,215,-303,391,-32,609,239,508,-8,-502,668,-446,154,471,-18,-594,337,-255,254,638,431,-511,282,-397,-638,-747,645,-533,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSerializer(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Object",
            new int[]{947,-641,779,829,613,400,131,-108,-878,-679,-140,-86,-600,-421,-10,-850,-102,170,496,-707,447,-406,-103,-410,-487,913,435,-39,-577,-762,-270,-572,-728,413,156,-68,-801,441,288,-186,529,-807,-253,-324,-137,-616,-501,120,-371,409,257,-48,820,682,18,839,-302,-598,-42,104,-907,-717,-81,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSetterInfo(com.fasterxml.jackson.databind.introspect.Annotated):com.fasterxml.jackson.annotation.JsonSetter$Value",
            new int[]{785,-366,-883,40,826,-214,-325,-953,604,-865,-36,813,-777,959,-492,882,-684,-607,246,241,342,592,-16,-245,-593,-848,882,-378,35,376,-720,388,-714,-360,-304,503,-914,822,830,851,721,499,-985,-707,73,-521,713,-207,-645,100,371,-538,855,710,-757,510,-473,-272,619,949,133,798,-680,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findSubtypes(com.fasterxml.jackson.databind.introspect.Annotated):java.util.List",
            new int[]{609,627,-876,-390,-198,343,-220,-249,240,-556,451,121,-584,201,-567,948,-105,274,-316,-529,503,363,-434,-754,-660,-777,643,-972,-505,456,-38,-311,-27,-250,-868,377,964,934,-521,369,390,564,-433,972,-599,-391,461,175,405,-729,-762,-234,90,-48,479,143,-65,-52,718,153,-291,-390,165,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findTypeName(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.String",
            new int[]{138,805,672,-537,-663,300,228,114,655,-890,762,-378,-538,-402,290,-733,954,201,673,111,695,29,356,996,-376,534,-158,-428,547,-913,327,-205,-67,163,973,-251,705,290,-518,633,642,887,-210,-758,970,731,20,434,803,865,-885,663,-815,-502,2,932,-345,651,558,-727,521,172,-788,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findTypeResolver(com.fasterxml.jackson.databind.cfg.MapperConfig,com.fasterxml.jackson.databind.introspect.AnnotatedClass,com.fasterxml.jackson.databind.JavaType):com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder",
            new int[]{835,-447,-514,770,-694,710,-935,232,127,-783,802,-88,-695,-748,-64,927,172,-180,384,396,-86,281,249,-649,-250,-318,-721,-406,486,-735,409,987,411,721,-194,63,275,966,-363,406,428,-467,-856,817,136,-964,572,903,511,581,984,-532,590,476,-930,823,621,160,-332,-20,-162,-130,-73,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findUnwrappingNameTransformer(com.fasterxml.jackson.databind.introspect.AnnotatedMember):com.fasterxml.jackson.databind.util.NameTransformer",
            new int[]{514,873,-531,-304,180,752,476,-963,-305,218,913,-532,920,894,-591,-275,-767,-302,-694,43,594,676,289,-803,-992,-348,-103,290,-160,557,-889,694,-420,-379,894,-358,-675,-978,-233,162,-478,-974,270,251,-702,-820,543,-478,-886,694,131,-107,401,-211,615,308,330,-497,-312,2,-43,-797,-103,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findValueInstantiator(com.fasterxml.jackson.databind.introspect.AnnotatedClass):java.lang.Object",
            new int[]{-114,-15,66,-983,508,295,368,-927,372,-68,-977,388,-507,501,-246,34,-588,-637,-199,423,-241,-929,-978,373,313,770,-837,758,497,-284,652,514,-805,493,690,-191,100,662,-889,-965,-567,629,-474,949,-222,311,589,761,51,-176,-127,-717,-851,-137,827,-691,-269,-294,-469,-629,710,850,576,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "findViews(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Class[]",
            new int[]{-489,-137,427,393,-990,-695,-964,15,982,-924,614,-366,868,85,-576,-820,-363,-41,-380,-828,597,429,-863,612,-310,538,770,325,-960,-40,233,-497,774,32,-104,433,-784,-364,652,833,434,-992,879,-927,821,750,843,644,891,-154,-980,681,807,-682,-158,-893,290,-685,100,-20,-512,897,-92,-951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasAnyGetter(com.fasterxml.jackson.databind.introspect.Annotated):java.lang.Boolean",
            new int[]{225,-332,118,-107,682,-374,875,31,559,-893,-810,25,-839,699,84,-978,-122,-641,118,-661,573,-458,-631,-615,810,970,-383,-699,508,-674,-402,-229,-552,380,-148,650,-41,713,283,-225,56,-470,-624,-312,-687,-122,737,838,859,-1,533,285,-6,520,320,926,-839,715,-290,-165,248,-830,420,-577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector", "hasAnyGetterAnnotation(com.fasterxml.jackson.databind.introspect.AnnotatedMethod):boolean",
            new int[]{-143,-153,606,486,-878,-847,534,-463,276,636,280,-736,607,360,-241,-310,822,-401,-131,-172,466,394,-391,986,-88,-909,301,633,-177,-405,500,993,-549,-393,-50,-398,582,165,-326,638,521,-570,112,-10,-895,-137,149,-235,-601,-330,793,-791,-516,737,292,857,-461,115,578,-843,266,592,35,-253}));
    }
}
