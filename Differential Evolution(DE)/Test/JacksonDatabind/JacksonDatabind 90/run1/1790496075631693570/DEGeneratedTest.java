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
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromBoolean():boolean",
            new int[]{367,-652,895,-387,-588,666,511,-294,-415,157,-582,266,-382,-327,-796,472,744,-551,-294,329,-775,-153,-543,-998,129,98,634,209,-29,-19,-350,-455,88,566,420,781,683,812,113,-309,-633,-352,680,739,-280,439,-139,117,899,202,495,23,661,-173,961,-186,-734,-322,360,-237,726,-575,-351,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromBoolean():boolean",
            new int[]{569,-279,361,-376,-96,449,-224,92,-68,-364,-575,-12,-741,864,706,790,361,-976,-994,883,-602,602,250,-557,-816,-660,-952,59,576,-945,-253,-633,814,710,585,-851,406,389,896,858,-966,748,-172,-696,-334,708,431,-605,559,703,-919,236,-834,-382,-751,625,85,700,352,-926,-844,-783,-915,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromBoolean():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromDouble():boolean",
            new int[]{99,-228,-447,-182,696,120,-647,-857,-239,661,919,-654,-375,-591,329,-192,-594,-482,-357,80,-254,-71,451,353,-668,456,312,95,-996,-215,-118,206,-565,-27,302,-877,260,518,-973,-341,343,846,-292,-953,402,139,-55,-341,619,-173,-221,166,-931,806,-345,527,-240,426,-770,-855,-16,-326,-90,737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromDouble():boolean",
            new int[]{725,293,-901,275,-881,-208,-801,838,-737,-885,-747,-965,628,926,567,-444,969,-572,-221,-292,854,35,-612,243,786,435,-395,611,-568,-337,965,-795,-397,983,-991,198,573,-161,873,941,-900,-111,-908,-400,-935,250,123,-335,-963,-537,148,302,279,420,569,-880,706,-514,268,874,-864,441,-817,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromDouble():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromInt():boolean",
            new int[]{187,-548,-117,326,206,88,-519,887,204,49,817,-3,852,-68,171,875,-811,-42,-670,230,453,84,745,-88,-78,-992,-175,-942,-607,774,121,-667,-499,-149,-419,112,-131,538,-203,658,272,338,-766,244,-563,-454,-206,947,-115,705,-269,-575,-705,-161,975,-653,-700,380,-831,-626,-691,568,396,564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromInt():boolean",
            new int[]{-621,-257,33,-41,-510,-789,-522,401,-502,-934,-861,679,85,926,203,-56,-820,-712,-38,472,836,411,310,-628,-540,254,388,-770,-393,944,-846,-464,-979,449,-899,49,492,71,-934,421,199,-812,158,-694,731,278,176,980,-312,-342,595,52,-92,619,165,-783,737,-335,498,-771,-530,-581,-377,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromInt():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromLong():boolean",
            new int[]{-739,526,945,793,-82,-586,677,981,-507,331,-470,-120,-409,-949,371,755,-758,940,934,-604,-356,-750,-907,-43,632,-401,565,671,-619,198,482,359,242,275,-77,155,15,-308,213,862,-533,774,-462,-997,-558,-965,297,-283,509,644,-548,455,800,-754,137,-368,-607,-944,785,-690,413,-968,98,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromLong():boolean",
            new int[]{517,-37,-346,606,-578,-877,-673,641,339,797,515,-418,623,855,472,782,293,-387,585,-192,-929,-627,-350,-430,-691,263,-194,-16,488,31,-274,-789,-722,395,-729,-972,614,-946,-459,860,-304,509,-4,-333,404,503,191,-33,191,53,-489,63,-766,-200,304,766,320,-256,-232,-199,-235,-734,81,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromLong():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromObjectWith():boolean",
            new int[]{233,898,-153,-858,383,720,-757,953,518,-822,479,-317,404,887,-798,909,-512,-198,-149,-170,642,-83,-378,355,387,-326,200,-55,-686,402,-738,-175,-889,496,947,-616,846,495,289,-205,975,-249,960,-801,920,943,546,154,-991,350,-899,-528,59,-517,697,-943,-43,-535,352,-96,219,-642,-202,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromObjectWith():boolean",
            new int[]{691,-457,-644,796,-679,-793,-615,749,225,-986,-311,58,-828,99,632,-706,-971,708,-319,370,724,-47,-332,379,-159,476,108,-865,-897,112,603,326,659,-969,-422,361,-624,-670,350,243,-870,513,-72,87,475,921,-201,-750,7,-210,452,-491,-756,-667,431,149,-105,999,280,422,-952,909,-77,423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromObjectWith():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromString():boolean",
            new int[]{-641,-378,217,-671,77,367,-541,963,-840,-134,-69,656,961,-396,-803,112,-372,639,583,-138,-469,-345,-228,841,-563,851,-940,258,111,316,-167,132,-245,-918,-283,-210,-779,-193,548,812,-690,197,816,449,-387,-308,-433,-635,347,678,-121,-274,-578,-444,960,94,569,367,708,385,799,342,-887,-788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromString():boolean",
            new int[]{143,725,-578,-566,-985,-608,334,-52,-976,-876,650,330,-779,-308,-198,-183,-631,-323,-96,-215,-686,-934,-12,-810,-668,-907,182,-369,-7,580,745,520,-218,557,-265,739,-739,715,-178,-645,-68,387,-769,533,103,-819,145,577,-963,112,29,219,485,-825,-732,706,-734,-370,301,40,6,984,969,-206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromString():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingArrayDelegate():boolean",
            new int[]{-105,-522,-507,-288,-68,-743,248,843,-231,-422,637,-16,-88,567,-868,-512,403,421,224,129,402,114,797,-488,21,772,-573,155,985,-563,-609,968,795,-759,-140,987,765,434,-52,378,-445,-862,-936,980,643,-764,398,941,544,-627,230,-637,541,-307,270,-689,166,-349,-771,-996,-221,977,-560,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingArrayDelegate():boolean",
            new int[]{-263,591,54,27,402,513,442,609,-256,807,203,-95,231,887,-140,552,979,-159,-5,-535,395,-318,-695,-626,-173,-420,-404,892,-173,368,573,-628,778,889,954,5,-672,204,641,709,-706,86,-528,-338,562,783,-574,578,-153,-428,-950,223,-875,317,-630,-937,-924,894,-450,-544,-106,167,185,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingArrayDelegate():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDefault():boolean",
            new int[]{-995,954,-169,-149,36,-874,950,-996,-665,422,-782,-632,102,-236,-864,820,-287,-309,501,242,582,717,-134,24,317,-843,966,925,859,765,-232,844,846,240,-178,-428,-507,408,210,-901,-110,725,-478,-302,845,648,-974,-330,-886,-646,475,-642,189,-666,846,-138,-287,-567,-298,-275,986,-908,854,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDefault():boolean",
            new int[]{-931,-293,522,-535,539,-665,-708,-517,68,-740,831,-233,710,-633,530,662,11,-303,43,-560,887,87,-978,111,576,377,-733,-69,923,307,971,-512,764,936,-212,667,209,177,-979,-612,-709,-127,-574,-398,-394,740,-204,-284,-655,447,834,336,-593,-879,606,-966,-814,-342,322,-292,-954,-651,144,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDefault():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDelegate():boolean",
            new int[]{587,-122,-573,-375,532,561,-650,707,993,374,-558,652,239,-148,-588,821,699,-701,-587,-736,751,-173,-365,-338,-466,-930,-9,-889,928,-766,-772,583,767,669,83,22,452,-672,426,700,205,-882,-282,655,403,443,-409,824,-346,18,829,-146,-421,428,673,436,629,859,-49,-480,687,880,-720,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDelegate():boolean",
            new int[]{689,-705,-647,321,-951,-370,118,-330,-708,-925,-394,64,949,417,-938,-14,542,823,-907,-946,-753,-791,-391,587,711,-621,471,-762,411,-899,-20,558,930,-128,597,491,-418,241,-383,450,-85,-762,918,-178,813,-260,345,561,130,376,-161,-138,119,995,-52,816,-750,626,-928,-583,-544,376,419,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDelegate():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canInstantiate():boolean",
            new int[]{163,-912,588,618,757,-718,217,-584,-764,47,-19,-876,-629,299,-984,-742,360,-693,834,-613,759,-232,204,257,-107,-246,-605,-586,-296,402,245,412,-570,-313,-952,411,-453,-953,-722,725,20,-772,834,714,-962,305,-351,-681,161,497,284,466,63,-827,413,620,-91,-814,19,810,831,588,-485,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canInstantiate():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canInstantiate():boolean",
            new int[]{-405,117,222,-656,-196,180,-391,859,417,-152,412,716,-900,717,-978,986,336,425,-989,755,616,535,-126,208,-871,-753,-516,-731,-954,-460,-112,586,5,721,625,353,418,-119,-214,203,821,-712,672,-721,637,367,-904,-567,466,585,-883,509,277,292,-675,640,720,389,-342,594,217,523,176,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromBoolean(com.fasterxml.jackson.databind.DeserializationContext,boolean):java.lang.Object",
            new int[]{141,-972,-523,16,-790,507,-688,557,-696,-638,-673,-812,606,188,295,-252,-249,-50,441,-863,605,-455,-325,-38,619,-410,516,-783,670,-307,-246,-865,202,545,284,-731,891,326,556,732,-903,-902,-437,550,-124,-841,447,112,-482,-368,-598,819,-793,-35,822,195,-306,-504,391,987,-343,704,-820,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromBoolean(com.fasterxml.jackson.databind.DeserializationContext,boolean):java.lang.Object",
            new int[]{817,-783,498,867,-503,-178,-61,-953,313,464,451,-704,287,7,-26,-975,-466,-538,461,-833,-999,369,-293,790,-712,-882,-265,-818,933,-307,-256,295,-559,390,49,295,751,-779,398,-115,866,-92,-427,-930,904,-366,-683,-942,-18,-932,238,133,-973,557,604,-534,832,-166,700,-875,-447,112,-232,-221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromBoolean(com.fasterxml.jackson.databind.DeserializationContext,boolean):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromDouble(com.fasterxml.jackson.databind.DeserializationContext,double):java.lang.Object",
            new int[]{459,-406,-686,-122,-6,200,634,149,-789,-924,276,891,541,-236,345,-705,-201,-887,-341,399,124,-455,853,-150,904,-830,-153,884,-923,-926,153,-120,587,511,537,180,-28,466,-563,466,68,36,-842,-80,-363,469,-612,-354,354,304,830,383,61,42,-118,402,413,51,196,673,158,996,673,875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromDouble(com.fasterxml.jackson.databind.DeserializationContext,double):java.lang.Object",
            new int[]{-877,-533,-537,-82,428,-699,-884,101,522,80,604,662,-87,558,-666,776,-909,-764,-71,169,-160,-624,-137,5,673,-775,-234,36,-845,-871,277,752,273,673,-674,581,190,656,620,815,763,757,-270,866,-19,72,693,-295,-829,370,-682,-129,-791,856,-525,346,-721,524,-407,-260,763,-240,296,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromDouble(com.fasterxml.jackson.databind.DeserializationContext,double):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromInt(com.fasterxml.jackson.databind.DeserializationContext,int):java.lang.Object",
            new int[]{249,-860,-780,940,-711,109,-47,-798,-243,415,-323,-579,172,-773,-474,948,-340,-317,-134,-788,280,-296,-866,-590,-749,350,-616,952,-12,848,222,375,-642,614,440,-969,-166,-809,130,882,-653,868,-720,947,541,-58,-25,584,873,-940,155,-326,-475,921,-367,-43,478,681,-453,357,-769,899,768,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromInt(com.fasterxml.jackson.databind.DeserializationContext,int):java.lang.Object",
            new int[]{637,-387,264,-894,226,-67,-651,-717,-371,183,899,-619,-860,976,-191,-769,-593,-173,-759,-514,-699,-751,265,978,-959,955,615,-690,338,-159,580,459,801,-283,710,823,147,261,415,-906,836,-423,860,-347,-307,908,-435,-291,-198,-466,54,-630,-521,717,-429,-997,-484,-337,-609,-231,-966,-114,-416,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromInt(com.fasterxml.jackson.databind.DeserializationContext,int):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromLong(com.fasterxml.jackson.databind.DeserializationContext,long):java.lang.Object",
            new int[]{839,-176,-790,-790,-506,949,465,71,-818,-834,532,-686,-538,-729,-664,-590,-433,-157,611,-267,-648,-679,-30,-675,-110,-455,-53,-634,468,34,633,-379,-140,290,635,-530,-620,540,811,498,-535,-494,451,101,314,-969,419,75,440,-338,115,715,917,700,-905,-360,-420,596,-343,-802,438,-890,-234,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromLong(com.fasterxml.jackson.databind.DeserializationContext,long):java.lang.Object",
            new int[]{-817,-731,-623,367,748,-845,957,-521,786,-882,10,688,684,-148,-168,-715,763,849,221,-322,286,652,340,-180,896,6,230,916,543,-46,908,493,85,-984,-897,-751,382,653,378,469,-49,422,-367,-711,6,-526,817,-439,-933,-710,11,696,-772,-4,42,-526,648,936,240,517,-149,-265,37,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromLong(com.fasterxml.jackson.databind.DeserializationContext,long):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,com.fasterxml.jackson.databind.deser.SettableBeanProperty[],com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer):java.lang.Object",
            new int[]{-11,706,687,855,-594,-697,467,357,920,825,-373,-794,-150,-338,-994,270,972,-538,269,-793,-86,715,-376,327,401,505,159,846,728,-223,922,-188,-927,-793,-223,653,-739,771,684,181,406,438,567,156,-503,92,194,66,-547,-280,-374,-354,409,196,-314,839,-911,577,633,-597,-13,-280,-220,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,com.fasterxml.jackson.databind.deser.SettableBeanProperty[],com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer):java.lang.Object",
            new int[]{153,383,-652,118,-108,-622,231,-700,621,858,976,-898,738,-244,-274,-413,33,277,-92,718,-317,-982,872,-772,816,-603,-703,676,383,546,-195,-228,-774,-436,133,744,-720,42,-198,-609,649,95,-209,-271,936,-176,-78,720,655,-948,849,390,-191,-526,401,-117,-202,964,-221,440,-310,-364,-336,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,com.fasterxml.jackson.databind.deser.SettableBeanProperty[],com.fasterxml.jackson.databind.deser.impl.PropertyValueBuffer):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object[]):java.lang.Object",
            new int[]{-925,196,607,84,-147,-928,877,369,-29,470,578,-749,-175,-845,-274,845,-637,224,-3,31,-474,846,571,444,972,-967,989,283,48,-311,-300,237,-635,-682,-521,313,445,-805,-765,-777,946,561,-148,-900,703,428,-349,-187,-513,995,829,909,741,-679,-192,-721,-175,904,-219,-907,319,-861,990,-788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object[]):java.lang.Object",
            new int[]{403,485,101,-205,-182,850,524,-847,-654,855,-593,73,60,-711,440,796,-4,521,-834,-401,-793,-642,-192,160,829,619,-304,-337,-410,-94,936,-341,-418,139,466,-812,242,-84,-991,-467,-494,440,498,105,-145,-151,571,-561,608,-845,-569,48,204,651,972,959,-525,-335,462,-820,907,-151,-763,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object[]):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromString(com.fasterxml.jackson.databind.DeserializationContext,java.lang.String):java.lang.Object",
            new int[]{633,128,-282,-508,-836,-561,488,-281,737,388,-798,750,541,-710,-157,-70,-117,106,-689,-530,-86,-867,-433,674,-964,-222,-528,-46,-63,755,528,-699,210,295,927,-405,-642,-379,354,-832,691,850,-239,371,-945,-278,7,-461,656,138,696,728,-666,-659,-217,844,554,-259,-379,-590,111,-77,-12,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromString(com.fasterxml.jackson.databind.DeserializationContext,java.lang.String):java.lang.Object",
            new int[]{-739,599,-1000,-898,969,-289,-17,105,-1000,-1000,166,457,-83,-957,-460,-1000,-142,463,-535,-379,-1000,-1000,-89,-1000,-799,-1000,271,174,1000,807,-684,483,1000,-1000,-1000,-570,610,423,357,807,1000,985,-76,-668,1000,392,400,149,353,-394,933,-516,-483,244,1000,1000,-619,-447,1000,1000,580,390,1000,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromString(com.fasterxml.jackson.databind.DeserializationContext,java.lang.String):java.lang.Object",
            new int[]{-896,-868,-666,-729,53,690,-3,-314,-758,202,-528,-404,-697,179,-75,-441,-107,-273,115,-3,880,366,-353,694,-100,683,-977,-611,353,-464,385,-952,970,-162,-997,113,-99,559,591,-969,994,-486,364,-267,-653,-244,347,368,-423,-46,-50,-457,-719,-13,-979,643,149,-809,720,-667,-138,160,-607,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingArrayDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{-123,436,55,592,185,-983,820,40,939,503,686,-131,-528,346,586,-285,973,-636,825,-832,-816,516,-827,419,-442,-517,-121,59,714,-485,192,703,-541,-99,468,-774,617,-685,896,612,-585,299,469,694,-709,-819,-52,-631,-343,841,244,288,337,-91,913,-529,213,-786,261,-200,950,619,928,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingArrayDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{569,707,790,905,124,235,474,42,-240,-253,-121,-695,23,285,722,-912,468,-247,292,134,313,160,-623,811,-820,702,624,115,-198,456,476,838,997,285,272,477,174,536,84,660,147,-969,459,-849,-822,951,324,-193,-220,301,22,-751,-947,-169,708,501,-881,503,-870,-683,538,916,189,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingArrayDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDefault(com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            new int[]{21,-608,-722,661,381,470,980,593,456,-208,308,-759,-390,599,141,-576,436,920,42,-813,-434,-6,607,210,676,-605,475,-806,37,-803,-311,-531,773,-932,-581,-659,-655,-267,990,61,745,-106,623,-253,-626,-579,309,40,641,-640,-134,-150,-46,-827,365,-848,950,-522,828,-540,-420,552,405,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDefault(com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            new int[]{-707,605,41,432,-534,469,-903,209,-685,-165,45,-425,-132,-920,-199,897,54,504,450,-409,634,-83,771,17,-586,929,-790,820,-985,720,-57,396,291,-397,-32,433,-124,274,116,-131,-742,267,305,-998,-694,858,-573,-2,213,-580,827,-420,475,58,-493,293,-316,-744,909,419,689,-789,-603,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDefault(com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{599,-844,-415,-816,-701,120,0,-169,-612,-724,677,-152,617,953,408,-480,407,-833,-285,-600,488,549,-340,-269,846,413,-128,882,557,-23,304,-157,390,-636,665,745,163,800,751,681,737,-299,-62,-328,284,-263,-971,-464,989,-137,308,620,-296,335,173,-121,249,-750,-938,643,5,807,-516,-461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{659,107,-754,-607,339,962,-625,779,-729,-567,-912,587,-796,-453,-528,972,869,222,-104,858,-328,449,933,682,69,199,255,501,689,304,-43,-816,537,139,245,407,-262,759,658,-577,183,-618,81,-814,168,547,691,-701,660,134,658,454,-811,506,-41,593,503,499,967,825,-867,712,613,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-715,122,-557,-627,-572,186,138,-177,510,-191,101,-438,679,35,-239,620,112,-527,-736,133,219,279,642,-292,-800,832,768,-394,-849,770,49,-653,154,-222,662,990,749,-472,-160,-760,60,531,147,-211,430,298,-2,-115,910,172,-209,923,-827,-534,-89,121,534,-863,606,752,-409,-876,279,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{445,183,-606,1000,551,-770,-22,-930,-1000,283,-1000,655,-376,397,-244,-1000,-1000,-9,192,-212,-1000,777,174,1000,-643,-114,-450,-1000,446,146,-301,-1000,-1000,-940,684,-99,1000,1000,-112,-562,224,-491,-585,-1000,318,-1000,534,974,14,291,-305,-723,-973,5,818,860,726,407,-13,-636,50,-33,-255,737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{-677,848,-556,-838,-351,-208,-50,277,-490,-865,850,-939,-459,792,654,-915,445,240,-146,-224,152,317,312,-437,-515,176,-593,-479,163,-27,751,-200,-768,-236,-546,-773,647,-154,-562,338,-424,-710,-944,-193,238,-217,-550,-275,-377,-939,-521,549,-753,184,-214,-386,-723,-292,662,112,-206,-594,-293,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{-975,793,-681,-637,-8,-547,-307,390,-163,-825,909,553,388,-900,-731,816,-697,-927,788,-469,789,829,880,-366,-24,735,-359,270,867,497,110,488,-928,-844,100,-255,101,-538,661,801,-75,196,77,-666,690,-578,-634,-938,-989,-215,-251,-466,-874,-52,240,-159,310,70,599,-531,-566,-176,-750,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDefaultCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{431,626,-554,-349,-887,285,-579,113,566,-950,594,-872,966,117,278,-566,981,-113,-520,327,748,-342,904,-23,370,691,309,550,81,871,-498,-657,-565,-754,-412,547,917,-37,-636,-46,229,-200,-309,970,-824,-618,536,601,-455,-565,-995,772,-333,-18,124,-737,-544,249,-608,-159,637,253,-785,754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDefaultCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{591,-505,181,653,810,42,879,-790,271,354,429,244,259,669,246,-16,83,503,-324,276,971,-139,110,-37,15,144,-93,-503,-558,412,70,-134,740,-351,-28,-617,178,-11,-351,547,154,-503,164,241,-577,-13,393,-147,773,-338,-800,-425,993,-139,418,-932,-920,112,328,998,549,-160,-624,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDefaultCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-845,784,188,752,321,237,-899,-483,-424,-618,997,391,109,639,-64,-143,-234,866,329,-331,-158,335,-86,-429,703,155,-931,458,737,836,-858,185,110,960,-991,-790,-607,-847,132,-690,-762,898,103,-280,974,115,837,-781,-749,-102,371,-75,660,-703,584,854,655,-351,-952,657,-966,551,-302,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-679,-191,277,-222,-231,-255,-798,-805,-665,-157,756,182,4,-832,254,185,-703,648,219,-250,429,-598,32,-342,-844,452,-163,-309,-838,216,-715,-650,-81,-616,-787,902,453,23,474,133,-369,-882,778,319,220,128,-380,97,274,-400,-828,140,-832,-607,-650,875,-978,651,166,-213,-837,-681,-30,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{-509,160,-212,-945,200,777,884,-839,-873,-814,-924,-375,327,696,640,585,-830,364,-851,-635,516,-48,346,509,482,-814,122,-600,-598,-543,785,225,173,-104,-536,-770,-514,908,-956,771,-365,627,323,485,-432,-434,210,-949,-364,-270,773,-508,-902,866,-350,726,844,207,-51,179,66,-976,-872,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{749,811,-273,-76,304,-226,-322,-998,723,58,-925,-216,-602,-612,23,241,-683,-949,400,-753,-168,-860,331,-575,941,502,-116,68,-194,410,-261,-803,-246,-278,300,-913,400,-560,210,-913,637,11,573,-602,286,-552,299,-353,921,414,949,519,55,-231,217,-466,-782,779,-254,-910,-238,171,-614,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getFromObjectArguments(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.SettableBeanProperty[]",
            new int[]{239,-894,755,50,86,-722,603,495,-959,433,-53,815,-852,-869,-171,628,-471,105,-967,902,293,105,712,-848,-69,575,242,-224,-302,-995,352,-288,-222,-574,-364,351,-541,-226,-228,843,977,-390,974,-231,208,26,521,-473,981,491,-427,715,-595,853,889,300,329,740,552,-572,-189,-782,392,351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getFromObjectArguments(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.SettableBeanProperty[]",
            new int[]{-205,-871,180,-174,588,-798,-758,-658,-720,-736,-642,731,922,-995,332,-343,-532,900,601,-472,728,-185,-285,216,162,-581,364,286,-85,-908,860,866,754,-285,-939,-109,15,-98,-68,-219,78,56,-850,-609,-545,-45,475,443,102,491,954,-384,251,-348,-528,-59,-252,873,619,-752,633,478,-579,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getFromObjectArguments(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.SettableBeanProperty[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getIncompleteParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{929,886,-143,640,167,106,-948,-492,863,432,565,10,681,-657,87,-863,778,-384,-717,-922,81,-932,573,-219,-464,-99,-640,-928,548,-608,-817,22,-554,699,313,-153,723,-622,-379,185,62,-177,-776,816,-296,311,-644,43,-403,-428,-803,833,-77,-103,-13,581,122,-340,-363,-140,321,-144,-729,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getIncompleteParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{181,299,-399,-146,714,-836,597,99,122,-818,364,-441,-14,-780,-145,350,195,299,119,505,-538,-44,-879,-877,51,595,-371,-498,-52,-130,-815,680,346,-877,-814,-497,230,-223,456,-193,-902,-833,-659,584,-588,772,-704,-891,234,-946,248,-57,703,189,-434,414,471,867,405,-245,61,13,-616,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getIncompleteParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueClass():java.lang.Class",
            new int[]{-915,474,417,-163,-244,-382,-315,-659,-140,194,-220,404,-4,736,717,607,776,959,910,-457,-986,-460,267,665,-129,-132,793,-671,-408,378,-225,-124,717,984,-185,-341,-758,865,484,801,-908,-606,151,-807,-469,-649,-311,709,-424,-267,169,982,414,459,407,-808,-116,-978,-715,261,627,-189,135,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueClass():java.lang.Class",
            new int[]{-181,559,244,-157,-819,-797,102,-13,-410,430,-448,-260,573,466,28,-769,368,-222,775,-569,-44,-296,-940,-184,524,247,357,60,-752,-847,356,601,564,343,865,-953,685,466,-861,-771,26,-105,981,-572,-604,859,99,-182,-629,526,-402,457,60,886,278,338,666,13,412,927,682,957,-902,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueClass():java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:VU5LTk9XTiBUWVBF", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueTypeDesc():java.lang.String",
            new int[]{-231,788,95,-402,868,-614,-813,603,-879,-113,994,312,-986,-268,790,453,355,619,-974,-718,-468,433,548,491,820,220,-656,-583,156,645,969,-341,-542,-1000,77,-254,-997,837,613,-601,627,-466,-875,71,-390,722,-635,979,-37,370,941,-376,556,-432,58,940,13,-445,645,-393,652,-616,-956,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:amF2YS5sYW5nLlN0cmluZw==", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueTypeDesc():java.lang.String",
            new int[]{-153,-243,-673,61,-831,49,819,1,-602,-722,895,-48,-912,-89,298,252,-260,-250,-699,-91,-158,-101,848,628,-960,-203,-471,-494,532,-320,672,-102,404,450,158,-473,-914,913,657,-251,348,619,-112,107,-650,-875,-725,-249,-879,-609,848,175,569,259,652,-243,599,455,64,109,262,426,389,913}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:Y29tLmZhc3RlcnhtbC5qYWNrc29uLmNvcmUuSnNvbkxvY2F0aW9u", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueTypeDesc():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getWithArgsCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{147,-602,-922,294,-930,-521,-561,-339,-622,-133,-785,241,887,-454,822,488,391,-958,799,-811,721,477,647,-904,-809,251,678,259,-824,-522,-667,696,-367,603,-919,172,98,48,-774,-4,957,169,-249,31,561,-662,652,-655,172,524,129,-636,641,430,496,-246,-757,890,651,-216,-868,48,615,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getWithArgsCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-417,-487,149,598,-683,812,-843,605,41,-861,385,-954,-948,738,-383,-25,239,-501,332,387,389,928,-212,-180,-415,-114,-682,967,657,-229,-953,-65,831,-918,958,-27,375,-232,387,599,294,150,873,-651,-25,-220,592,-373,2,144,525,-480,-641,-235,995,862,-296,994,221,-692,471,498,135,741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.ValueInstantiator", "com.fasterxml.jackson.databind.deser.std.JsonLocationInstantiator,com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getWithArgsCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromBoolean():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromBoolean():boolean",
            new int[]{-947,781,-350,221,768,626,294,732,-699,-22,485,279,702,-24,-728,-277,-359,-457,148,-80,-295,-49,249,-954,-623,-22,599,459,355,659,199,682,362,154,-72,543,-75,-930,207,-772,-253,784,-853,-910,215,-301,-452,396,-613,-786,-536,-105,334,212,365,-921,-873,336,882,-686,928,-33,-965,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromDouble():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromDouble():boolean",
            new int[]{-737,-189,333,-914,-750,918,-546,778,-69,691,-334,591,-978,-201,-111,438,-622,652,949,512,943,766,-272,163,928,-38,-199,875,-387,801,-810,219,-270,246,763,-182,949,760,-383,-920,-269,797,822,-269,473,-57,-149,38,740,849,-858,22,-816,547,-623,-94,-292,-269,-760,-392,-594,150,731,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromInt():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromInt():boolean",
            new int[]{-41,-749,-160,-886,-740,740,794,463,-935,-122,472,934,-900,746,-554,-309,-457,877,-342,980,-194,-953,535,180,-379,903,143,-847,944,524,-724,-43,-308,-659,650,356,925,-253,766,515,-270,-772,391,-241,745,-166,-880,-668,63,-552,32,-250,49,-562,38,-514,531,22,228,-480,752,-71,224,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromLong():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromLong():boolean",
            new int[]{-93,-445,-435,775,-557,71,-650,626,-242,147,170,971,-542,-267,-947,-443,-341,-183,-73,-644,-343,-509,-64,13,542,-78,-590,-598,371,231,-698,574,-340,-794,827,-647,-824,346,24,-565,-744,-184,-993,935,-772,-451,-212,-948,889,944,-263,-263,-547,424,-55,-890,918,416,-892,690,334,266,-134,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromObjectWith():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromObjectWith():boolean",
            new int[]{523,-447,413,-931,-866,-758,442,112,547,460,510,-880,996,409,669,226,11,913,482,810,-459,-568,531,859,-991,868,-575,-189,-338,-212,-132,281,-987,-563,76,492,770,518,-50,444,-270,-949,807,930,340,883,-834,-680,314,359,-853,-271,577,560,328,-340,88,84,623,485,616,840,623,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromString():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateFromString():boolean",
            new int[]{-534,187,822,410,-371,944,236,-659,864,519,-297,390,456,644,-865,141,-382,-86,-67,136,538,528,303,368,586,163,-824,301,25,41,185,-434,-681,-402,197,361,924,-983,-63,-48,311,-681,-565,678,-115,-248,171,378,-525,-281,-884,247,85,-332,-830,-182,-269,25,-119,-58,-639,-486,-180,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingArrayDelegate():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingArrayDelegate():boolean",
            new int[]{532,-975,-21,-760,-896,1,509,995,-665,-760,76,-370,-340,-710,825,815,-207,367,-432,996,666,969,-260,424,83,172,-881,-640,347,214,-744,-243,899,-18,-515,-776,638,-576,-855,823,977,-428,217,-657,-68,862,972,111,-411,-258,-581,83,138,164,-660,-975,-674,528,-454,884,670,-159,597,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDefault():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDefault():boolean",
            new int[]{867,-743,-155,256,-277,303,495,217,676,-911,-800,587,674,-13,421,430,-507,-628,-272,-396,776,377,178,909,-797,-138,-771,-978,818,555,627,613,481,950,-822,-555,927,-737,330,765,-34,-485,-893,828,0,-372,76,18,-799,-664,-224,-304,465,-361,-721,-357,567,-427,-548,767,-197,133,839,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDelegate():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "canCreateUsingDelegate():boolean",
            new int[]{-920,-675,545,-818,153,-246,714,-655,889,57,-778,351,-575,572,-386,-725,-495,100,373,-358,-316,466,-709,463,-664,73,-796,-122,-192,200,152,-11,607,-321,-61,-969,-751,-705,271,-635,-420,737,-808,941,760,283,340,-129,-116,513,-246,538,-755,302,-642,-749,620,91,-921,-81,-346,36,614,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromArraySettings(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.deser.SettableBeanProperty[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromArraySettings(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.deser.SettableBeanProperty[]):void",
            new int[]{150,651,-293,627,-23,749,-830,255,804,98,-923,-544,399,993,-691,271,23,443,-704,-687,872,296,-932,-290,911,196,-529,653,-906,783,908,699,-736,714,-323,-154,665,-843,-994,-969,-84,-860,218,215,544,868,119,-516,732,-515,-343,-283,-191,-910,175,271,-824,-181,-827,877,-112,-251,-606,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromBooleanCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{726,-369,-6,913,-566,499,387,-717,-934,-489,975,306,571,-775,454,802,-27,-798,986,-909,351,-296,106,830,-362,319,946,197,-469,17,816,-530,-723,343,-141,727,-592,31,897,331,589,-810,878,162,-338,392,-599,-883,703,484,657,341,-563,757,-142,-183,727,222,-287,507,-994,-825,823,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromDoubleCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{966,435,-211,-435,-304,-532,-930,234,-21,525,746,534,-730,-461,830,906,513,-74,978,156,-102,-889,-665,-177,-983,-551,70,-353,-356,-295,785,536,-765,842,12,486,-71,816,-179,227,-723,-659,-807,-965,-773,-913,282,-906,-972,710,-92,-916,-426,-751,759,248,-513,930,580,-899,763,237,-577,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromIntCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-66,65,678,940,-485,111,355,-956,-955,752,496,760,-860,730,649,-138,853,708,999,888,246,-935,-667,844,428,621,317,-8,-689,579,-44,-981,-837,688,323,-783,-858,-885,182,510,421,-147,-51,-916,-714,-371,596,232,612,207,-433,571,514,-241,540,-747,636,537,-32,-28,-441,-532,-261,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromLongCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{-673,863,171,-640,538,-681,-594,599,-574,307,60,784,556,536,343,785,-948,-916,-965,100,-226,996,-820,862,250,613,-215,-59,531,781,-65,-891,60,-724,793,788,-641,268,-161,829,900,451,395,-22,845,598,19,196,-446,-291,-366,855,-310,697,-583,969,582,-428,-576,-899,73,500,671,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromObjectSettings(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.deser.SettableBeanProperty[],com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.SettableBeanProperty[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromObjectSettings(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.deser.SettableBeanProperty[],com.fasterxml.jackson.databind.introspect.AnnotatedWithParams,com.fasterxml.jackson.databind.deser.SettableBeanProperty[]):void",
            new int[]{-396,999,644,-836,-995,-148,896,-500,12,458,-429,243,728,-282,3,-373,-528,-750,478,-781,230,365,-6,907,228,213,829,149,-858,936,-892,-939,244,-297,-512,-610,-626,895,840,161,84,386,396,-82,460,-165,185,833,-749,244,-189,-278,-279,645,-388,976,-669,-312,73,805,-146,-812,997,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureFromStringCreator(com.fasterxml.jackson.databind.introspect.AnnotatedWithParams):void",
            new int[]{695,-673,-263,835,291,201,-489,938,-715,566,718,896,660,746,397,-642,201,484,953,-378,594,758,387,-800,-206,-542,595,-943,-486,-340,-766,-302,-101,198,12,-321,-482,565,-516,851,-359,-220,-577,-370,441,-206,680,970,228,253,-950,-664,-855,991,148,939,-186,-510,521,-205,-627,744,323,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureIncompleteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "configureIncompleteParameter(com.fasterxml.jackson.databind.introspect.AnnotatedParameter):void",
            new int[]{-802,685,339,-659,930,71,830,467,-291,735,-490,923,269,-379,-48,-530,-204,-571,-271,851,-594,-534,350,-118,942,947,600,91,-548,654,66,354,-802,-191,-326,886,261,308,-968,-926,-84,30,71,202,-192,862,-987,-153,691,-739,86,-79,948,-263,515,698,-498,444,-640,960,-204,-762,504,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromBoolean(com.fasterxml.jackson.databind.DeserializationContext,boolean):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromBoolean(com.fasterxml.jackson.databind.DeserializationContext,boolean):java.lang.Object",
            new int[]{335,25,-941,878,543,-461,-156,-68,-1,557,-831,-248,407,-411,-694,461,465,389,-814,38,-829,-814,481,-914,-886,734,-688,509,4,-780,-746,172,700,-147,-56,110,-330,775,710,523,985,837,399,423,-290,723,840,-441,844,952,240,33,-103,325,201,899,546,570,39,923,-102,-888,602,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromDouble(com.fasterxml.jackson.databind.DeserializationContext,double):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromDouble(com.fasterxml.jackson.databind.DeserializationContext,double):java.lang.Object",
            new int[]{950,499,-684,961,-3,-940,508,-318,410,-777,-933,685,-519,255,-8,907,-744,-108,811,66,-642,-685,341,548,-13,-430,-885,733,155,499,-905,452,763,-931,961,-522,-531,-902,-197,479,484,805,337,691,-620,367,928,-258,813,717,-376,483,-338,-833,-798,-501,-521,-829,-259,32,405,474,691,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromInt(com.fasterxml.jackson.databind.DeserializationContext,int):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromInt(com.fasterxml.jackson.databind.DeserializationContext,int):java.lang.Object",
            new int[]{547,987,-17,183,-603,827,-473,67,871,-776,-305,696,755,367,118,-780,-241,130,802,-552,-812,-822,-414,-760,-636,124,880,863,106,-267,313,-261,-24,380,-909,-773,460,751,111,912,777,128,-830,-748,552,-498,969,358,-716,228,-962,-797,-788,977,-809,-387,-51,326,-326,-987,-902,-507,-218,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromLong(com.fasterxml.jackson.databind.DeserializationContext,long):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromLong(com.fasterxml.jackson.databind.DeserializationContext,long):java.lang.Object",
            new int[]{829,507,724,311,682,490,-997,601,-580,533,335,731,-633,712,458,-197,443,114,-679,-904,828,-526,791,-415,-879,-851,-716,963,-865,33,-211,-109,819,-428,-290,605,758,-303,-607,-601,-813,186,-769,0,-468,-255,869,-589,-887,-81,149,-609,-166,-157,192,-567,-567,784,272,812,-814,474,-17,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object[]):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromObjectWith(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object[]):java.lang.Object",
            new int[]{-959,-647,-941,918,850,-309,428,-974,351,548,345,363,486,-667,875,783,-223,-805,688,199,-612,933,181,-946,-121,495,-816,453,-315,-336,328,421,-334,-773,208,482,459,93,697,4,-157,637,-28,545,-179,-95,-866,836,-284,184,-363,968,218,948,-796,-148,26,-782,-640,-924,-517,936,268,941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromString(com.fasterxml.jackson.databind.DeserializationContext,java.lang.String):java.lang.Object",
            new int[]{-420,734,302,856,629,-404,555,-595,-288,-967,-497,-750,760,12,588,275,-815,814,-370,-593,-851,225,767,-198,766,-285,-355,88,609,-767,-303,482,882,210,-404,-907,-461,-22,199,842,-252,-107,-355,-925,144,-839,-331,177,-458,-378,630,269,975,-443,-713,418,262,942,-76,704,-364,551,82,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createFromString(com.fasterxml.jackson.databind.DeserializationContext,java.lang.String):java.lang.Object",
            new int[]{-895,655,-802,-303,-10,63,205,-1000,793,1000,-341,-201,-380,131,-101,-1000,254,892,-887,1000,1000,-575,-409,112,-50,1000,157,-965,-756,-994,231,-697,-302,48,107,-269,944,-348,-412,-954,290,-599,283,-1000,-121,1000,-1000,1000,-1000,-859,-607,-268,467,-244,-1000,-657,943,-669,-1000,-110,-425,-1000,-734,493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingArrayDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingArrayDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{256,-939,-502,566,983,560,-576,-502,-163,944,-363,885,-28,210,-889,845,895,-783,-411,41,-324,-939,-802,247,-23,908,-388,638,-795,400,-267,355,498,270,-391,950,-244,-128,-936,-109,472,936,330,484,716,-941,886,270,-670,217,-352,-748,94,846,675,-848,-559,972,-351,-699,-831,278,-495,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDefault(com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDefault(com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            new int[]{-952,-795,-655,470,505,388,-846,-152,-777,-344,144,-93,-15,419,133,616,-896,470,-975,193,-472,161,-754,-250,882,-410,-153,-789,497,606,825,291,-266,-227,164,532,-23,954,817,-128,551,-863,378,40,545,288,564,-825,-17,-415,791,-481,-840,191,89,751,47,889,414,-380,660,-630,9,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "createUsingDelegate(com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):java.lang.Object",
            new int[]{782,-225,743,-769,456,-27,532,-333,-434,-406,675,-697,-977,909,10,500,-470,-976,-705,482,-369,316,453,-801,-182,123,214,443,603,497,-514,487,-601,499,-774,-79,-427,-799,-381,867,-933,-657,-24,-705,-355,-593,-79,776,986,-221,151,-326,613,610,-672,524,953,-976,543,144,699,-633,147,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-357,-225,293,-719,122,991,-991,-19,769,-268,984,-612,-283,-79,-152,-510,205,-335,914,300,-465,-53,578,737,391,-296,474,-735,764,-86,-988,-275,51,-424,608,577,-527,418,969,-500,947,-975,-893,-275,-265,708,-574,-182,787,868,185,673,553,891,-99,451,-791,661,869,-108,757,679,854,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getArrayDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{981,-629,357,137,944,-617,-569,450,851,-593,874,955,605,-595,-484,-337,-909,664,-18,-470,-966,-72,-57,132,-230,24,80,69,528,-264,-454,-827,-711,517,-361,119,97,346,552,386,836,-458,660,-287,-109,-330,586,-874,-122,820,-523,-551,-323,454,355,-218,359,825,-792,-136,305,-506,555,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDefaultCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDefaultCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{209,-99,-808,-87,-155,899,721,-70,718,-43,-46,273,710,894,205,813,205,897,912,-603,743,-366,-126,383,837,873,-131,-742,-112,-67,-795,421,-275,-208,-643,-997,469,-670,55,-461,-872,820,927,212,-380,559,-879,-558,-239,284,204,-335,174,-79,-925,-733,28,108,643,-115,-928,-60,560,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-61,537,753,-454,246,936,-120,-765,872,-31,26,797,-516,629,251,535,-140,-169,-175,145,695,386,-54,-146,648,664,500,-724,-725,157,888,-842,-10,499,207,-406,-214,-597,-516,195,-903,-536,-978,-756,-129,771,-527,-113,704,-77,-256,65,720,146,478,-918,751,-37,538,-571,-645,-850,-368,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getDelegateType(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.JavaType",
            new int[]{154,-377,762,822,-583,439,-928,74,478,314,775,936,-118,-4,-675,-19,620,-75,297,-835,-292,307,-885,221,-813,451,-644,371,-61,410,-943,699,-877,27,-233,709,-272,-679,-288,641,799,-194,353,882,-806,859,876,-490,409,487,-288,401,945,-17,41,-386,-7,868,-578,846,-218,246,199,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getFromObjectArguments(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.SettableBeanProperty[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getFromObjectArguments(com.fasterxml.jackson.databind.DeserializationConfig):com.fasterxml.jackson.databind.deser.SettableBeanProperty[]",
            new int[]{-419,-777,-527,471,804,-368,-956,358,-747,88,50,299,166,32,-356,884,775,-510,-266,253,347,-271,885,-812,-2,-81,-592,128,538,634,447,954,218,-782,745,-887,905,-255,-246,934,973,-308,746,825,-24,251,674,-534,47,-307,675,-929,-132,-836,306,-677,-920,-103,-116,-313,-4,-95,606,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getIncompleteParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getIncompleteParameter():com.fasterxml.jackson.databind.introspect.AnnotatedParameter",
            new int[]{-227,959,631,790,796,853,366,210,975,-663,665,-999,-710,776,-267,-943,634,-942,-905,756,-865,-171,-427,198,-54,604,438,269,337,738,-22,860,504,926,147,481,394,475,-176,192,-910,-81,302,-701,-198,732,-947,562,346,860,-409,825,166,590,269,897,388,-115,-750,298,-804,-287,562,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueClass():java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueClass():java.lang.Class",
            new int[]{788,463,966,373,470,-126,-680,883,192,-80,690,-737,-662,964,-50,-178,320,280,-191,-27,-967,828,-472,-465,-740,-432,666,936,-464,-566,426,467,-11,652,239,-514,292,-449,-164,-680,8,705,-740,489,-663,858,-966,-156,-456,865,-21,224,219,638,-908,314,891,-4,308,-468,-469,-808,983,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.String:VU5LTk9XTiBUWVBF", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueTypeDesc():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.String:amF2YS5sYW5nLlN0cmluZw==", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getValueTypeDesc():java.lang.String",
            new int[]{642,-999,-971,-289,-523,-541,423,262,-797,-203,411,938,333,671,927,766,-255,-561,662,611,617,-294,-627,963,-384,409,492,-257,676,-37,-249,461,-652,-902,210,414,942,-817,369,106,-562,-902,-193,568,513,966,209,830,933,-520,565,-525,8,914,-175,-146,885,461,366,371,6,35,-753,-981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getWithArgsCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "com.fasterxml.jackson.databind.deser.std.StdValueInstantiator", "getWithArgsCreator():com.fasterxml.jackson.databind.introspect.AnnotatedWithParams",
            new int[]{-882,-445,559,-646,-593,230,-533,-536,-462,-579,189,107,478,-422,-800,-149,-351,912,-729,754,947,-97,-992,-94,-107,699,318,924,880,-812,-601,227,646,163,48,824,-187,-380,842,291,645,-983,38,-372,-24,91,-53,-206,-276,-298,-65,816,-679,95,250,621,965,953,-592,969,-324,-326,10,219}));
    }
}
