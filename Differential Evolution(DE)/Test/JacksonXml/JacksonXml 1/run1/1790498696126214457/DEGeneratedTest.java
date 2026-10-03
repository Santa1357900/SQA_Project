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
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "addVirtualWrapping(java.util.Set):void",
            new int[]{-767,-972,675,-466,-367,732,-1000,-972,-449,127,-154,-783,-1000,940,-126,417,313,447,933,-869,-671,-331,-718,588,-1000,-276,688,1000,230,-904,-421,-210,-1000,-276,-269,1000,533,-422,927,-65,-1000,-1000,281,-613,27,-600,588,-47,-820,-215,826,-1000,496,-285,934,77,-213,-841,-809,1000,378,-1000,833,726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "addVirtualWrapping(java.util.Set):void",
            new int[]{-123,203,115,513,841,15,-283,-164,-937,1000,1000,-884,-1000,966,450,1000,-81,-446,642,-567,-200,1000,426,712,-1000,-493,-378,333,887,486,-779,-60,-811,-249,556,826,295,246,45,-1000,-360,-640,-514,-1000,431,66,1000,-854,-124,-451,1000,-1000,-554,117,-122,-246,-1000,-301,-1000,-672,761,-673,209,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "addVirtualWrapping(java.util.Set):void",
            new int[]{1000,160,919,-132,-358,-1000,-18,-465,1000,1000,433,-784,-773,1000,-276,781,-314,267,30,1000,878,-401,826,-1000,-1000,-350,767,946,1000,1000,213,747,-659,1000,1000,801,1000,-840,296,-463,-1000,-682,-899,791,762,767,1000,-1000,-710,1000,-1000,-387,1000,1000,260,-951,193,-289,-1000,-827,-697,-533,98,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "close():void",
            new int[]{158,-57,-325,349,-54,332,925,803,711,-669,1000,-317,-1000,275,-930,366,-1000,-1000,529,-164,-1000,1000,1000,-892,-1000,1000,-1000,1000,1000,1000,-847,-1000,1000,-710,1000,-86,826,1000,-1000,-975,-1000,1000,926,-1000,-193,-281,-107,609,1000,57,-276,-881,-1000,1000,36,319,-548,535,-462,980,-810,308,697,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "close():void",
            new int[]{-603,-1000,-933,929,-194,684,1000,-487,892,-1000,-1000,-206,-1000,324,-1000,-874,-613,-684,1000,-1000,356,862,-1000,-1000,1000,-2,-233,-118,1000,1000,1000,-559,912,61,339,-1000,-423,1000,-1000,1000,34,1000,-292,1000,1000,612,43,824,196,-297,-1000,-263,-1000,957,1000,742,220,1000,1000,199,-1000,330,1000,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "close():void",
            new int[]{-363,1000,3,249,363,406,-309,-423,1000,-162,397,84,-612,-389,-444,-349,-1000,326,-270,-1000,106,-720,-131,265,-591,746,-495,599,563,-19,1000,-324,578,-423,-175,1000,107,1000,-492,163,-253,435,-636,-386,-1000,379,261,895,1000,-668,479,43,961,1000,-363,304,-1000,499,-1000,-1000,-613,-138,872,755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "configure(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature,boolean):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-1000,321,-501,-72,90,726,973,-307,-638,-314,-286,-478,-200,-1000,-546,-324,179,-369,-313,89,103,-862,651,50,-706,273,-810,-1000,-25,-710,90,-532,714,-329,-616,123,67,-1000,221,907,-639,1000,586,450,974,-507,1000,802,-1000,1000,-436,256,859,-11,4,-985,-1000,77,-230,1000,867,-943,-434,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "configure(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature,boolean):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{668,116,535,-988,997,324,1000,-112,-1000,-126,1000,-54,845,85,681,634,-1000,260,29,-181,490,-567,888,926,64,-16,-538,-649,1000,-604,-633,334,143,1000,-210,155,-840,-793,486,-1000,713,-1000,197,-1000,305,-600,-598,1000,-518,16,-226,212,-308,805,-845,239,-408,-239,-814,-10,681,-1000,-1000,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "configure(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature,boolean):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-1000,451,659,203,-377,-72,1000,203,-173,925,-1000,-1000,160,-665,-1000,-342,1000,225,-1000,252,-99,-286,-1000,-1000,366,223,-28,-1000,-1000,-197,1000,-73,745,-255,512,728,-253,356,-321,119,310,1000,595,839,182,-839,377,-963,-400,-263,-5,-636,-684,747,1000,-94,764,-298,1000,16,120,-860,-263,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "disable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-110,-63,-469,-42,-1000,-915,-217,-740,-760,-372,-1000,486,1000,-48,-631,802,-1000,187,1000,796,-1000,-785,-576,-715,-83,-1000,1000,-321,-37,-1000,1000,-634,-583,-534,-193,562,-780,789,479,-1000,1000,-530,1000,-1000,-515,-122,-1000,190,-1000,1000,-867,1000,-596,1000,700,1000,902,1000,-380,-122,-1000,-1000,1000,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "disable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{325,152,183,469,-479,-283,-452,460,-232,107,476,439,1000,-612,754,-166,506,-169,-103,1000,-1000,-250,-1000,-200,-431,-400,400,354,190,1000,356,-43,-220,213,165,679,-320,-201,1000,-400,1000,-819,1000,-819,1000,-465,-400,-1000,-400,400,37,1000,1000,-488,-717,400,932,-465,-1000,43,-400,-1000,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "disable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{1000,904,-277,719,534,829,-1000,1000,1000,-1000,175,-1000,-307,-766,-947,-192,991,1000,169,-1000,1000,-429,-629,-608,-1000,-460,258,-351,-946,-740,814,1000,-1000,-1000,216,-1000,-266,-339,-1000,-268,19,1000,-953,1000,-1000,451,1000,427,-958,-1000,1000,-179,347,310,-937,934,-1000,-1000,1000,706,1000,1000,-794,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "enable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-1000,-66,707,-912,-1000,708,402,-750,-482,-865,-181,1000,248,1000,1000,981,-588,-133,-1000,362,1000,-166,-358,1000,-1000,-369,-1000,-513,-794,-360,-1000,-1000,-1000,-369,-875,-1000,143,-638,171,-18,-1000,-1000,1000,-125,-377,-919,791,-227,-422,185,817,-1000,-501,-816,-666,160,-477,245,1000,1000,1000,-171,113,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "enable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-1000,-1000,323,-246,-721,918,-849,1000,-289,-345,-1000,1000,882,-1000,-467,-1000,-1000,-484,-448,1000,169,-1000,-1000,82,-13,-313,-954,-887,-35,-657,-512,202,662,-1000,-1000,1000,-1000,719,-111,1000,-1000,348,400,-736,-370,420,160,498,-387,-1000,1000,200,-72,119,-911,711,-386,-490,427,-323,-620,-1000,831,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "enable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{1000,781,411,254,-888,-311,-428,-721,381,1000,505,-889,-39,1000,-318,898,812,-94,1000,-1000,1000,-436,514,-1000,-1000,-929,695,463,505,690,-150,-409,125,-139,681,178,458,-20,904,254,-1000,-208,372,-883,-359,-1000,-40,90,367,407,-112,581,-952,208,-211,-449,-734,241,-604,-768,-1000,-249,1000,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBigIntegerValue():java.math.BigInteger",
            new int[]{-194,525,347,163,775,324,912,-976,383,-1000,-1000,195,67,525,175,-245,-1000,-830,-553,1000,923,49,-343,469,1000,1000,877,-858,-154,1000,-625,-106,-382,1000,609,564,85,-322,901,-589,840,37,-914,1000,355,7,1000,-1000,30,-923,265,604,1000,1000,690,566,-40,6,-1000,986,539,242,-1000,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBigIntegerValue():java.math.BigInteger",
            new int[]{-87,68,55,-698,-763,686,798,720,-536,852,913,-558,-681,159,303,-277,-462,894,-544,-50,-628,351,625,67,-643,494,-714,-288,474,63,774,779,-335,-626,-371,-647,-462,-203,839,659,88,158,923,-974,237,-825,573,729,-882,436,-551,996,-874,407,-348,-492,-995,473,-639,-774,-521,-865,204,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBigIntegerValue():java.math.BigInteger",
            new int[]{777,871,319,-632,-59,18,-697,975,411,487,185,-16,-1000,266,701,-971,492,1000,322,90,218,768,-427,1000,-892,-457,-1000,-1000,-133,-404,1000,-286,428,-17,-866,501,393,-397,484,-497,-342,-265,-280,-419,-762,-1000,393,757,463,-211,-865,1000,-180,-136,1000,-734,-219,-137,236,132,351,-401,156,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBinaryValue(com.fasterxml.jackson.core.Base64Variant):byte[]",
            new int[]{-1000,153,139,693,-498,584,67,577,832,-832,-304,-256,1000,-386,780,958,888,-1000,-992,-852,-1000,-177,-320,-897,-1000,-291,-1000,704,447,-1000,974,-617,734,-223,-688,190,999,71,-1000,985,-586,-190,-1000,-267,1000,-160,-697,-95,1000,-829,-351,-499,14,1000,355,695,-1000,-455,91,1000,-1000,587,44,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonParseException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBinaryValue(com.fasterxml.jackson.core.Base64Variant):byte[]",
            new int[]{136,-1000,671,889,491,36,486,-1000,-888,123,804,-17,1000,-209,-1000,561,293,-737,-1000,958,701,-900,-1000,1000,-1000,-1000,-62,-438,-919,1000,686,-35,610,-1000,-825,184,-359,1000,1000,1000,178,1000,-498,481,-445,-990,-659,-806,176,262,-822,317,1000,952,340,-1000,-126,223,1000,-445,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBinaryValue(com.fasterxml.jackson.core.Base64Variant):byte[]",
            new int[]{-1000,1000,987,188,-462,324,859,645,-417,-680,853,-1000,1000,-591,-721,1000,-833,-583,1000,463,205,-438,-715,1000,-1000,196,-936,-822,-773,884,654,-982,-250,-594,349,382,664,-125,102,1000,834,751,241,864,-127,768,-784,1000,237,-521,-33,-702,-455,-311,563,-481,-2,-1000,-520,-928,949,958,-1000,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{-373,-636,-789,-241,795,-602,0,-1000,145,1000,1000,246,869,-1000,181,-1000,-498,-104,756,1000,-1000,526,386,114,-552,436,618,430,792,885,226,-1000,467,-1000,-272,-518,878,-489,1000,675,-562,-458,359,263,96,1000,629,450,-1000,909,-1000,-550,1000,-1000,712,-1000,233,619,1000,-1000,-602,-1000,-827,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{-395,-1000,851,93,-449,461,175,1000,-1000,-100,-180,749,-71,945,1000,1000,48,-1000,-705,-1000,-1000,-116,-123,1000,-411,824,1000,-78,971,-180,939,952,-587,148,-1000,52,1000,-657,514,-1000,127,-62,114,1000,-780,-897,-471,660,6,-138,563,-48,-485,109,-472,1000,-145,39,-1000,516,226,1000,752,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{376,1000,639,698,-150,309,-84,287,-625,414,-611,251,-760,203,-984,1000,186,-1000,-1000,-885,-688,-107,-612,-1000,-1000,1000,78,-1000,674,142,1000,135,-753,-196,-1000,-589,-94,-1000,1000,-159,1000,756,-1000,296,178,876,-1000,-141,409,-835,-99,-524,-994,-371,499,515,-979,618,-773,669,1000,826,402,-465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{719,438,-209,-553,-1000,-1000,167,-582,-26,1000,-770,-901,606,-106,-345,966,1000,-195,-916,556,38,-75,358,31,-945,-200,607,-958,-1000,323,373,531,646,-1000,218,-966,103,-347,-787,-217,908,-317,-1000,-826,-570,365,895,-592,180,-860,-514,585,-506,1000,380,317,1000,549,-656,-24,745,397,-316,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-783,-187,191,594,366,-52,700,-824,-1000,-297,237,-1000,287,993,-475,-218,229,-409,622,378,-1000,367,-375,-1000,-341,-1000,374,982,-214,-373,195,-1000,-806,706,76,-142,745,-1000,-1000,-1000,1000,-85,1000,565,-614,-784,225,-1000,-210,-793,1000,-162,1000,869,-34,-135,239,289,653,1000,630,-246,1000,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-1000,-32,791,-987,846,369,-320,690,61,-1000,1000,-935,143,-398,955,-147,33,-1000,365,-1000,-1000,349,-1000,1000,794,-388,838,1000,-100,-815,900,-161,-476,1000,-1000,1000,246,78,-455,-1000,-167,272,1000,146,-972,-1000,1000,546,254,48,1000,242,529,-574,-422,-247,-508,760,913,1000,1000,146,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{-411,-861,-85,-562,-520,-155,1000,1000,-690,607,-1000,156,-839,-307,1000,65,-605,-255,-482,-32,-881,1000,-59,-162,634,1000,692,779,-891,-1000,-689,768,-51,1000,137,943,75,1000,-400,1000,-465,-71,-343,1000,-238,-1000,11,525,-1000,-194,-431,659,304,-598,452,423,-1000,709,950,1000,1000,234,-89,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{595,-700,-817,74,-325,-902,-370,-1000,601,-601,558,1000,520,-1000,-1000,-1000,819,-533,583,-494,844,401,-322,-443,120,-998,1000,1000,947,68,848,-1000,130,-720,739,-436,-1000,-166,1000,-1000,-393,1000,-1000,1000,1000,1000,758,445,-1000,602,327,-781,-547,-676,607,244,957,1000,-155,-901,-960,393,-550,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{-357,-295,-783,799,-1000,-691,289,-778,1000,578,-881,-246,-1000,220,771,-169,344,340,-1000,-908,-1000,-1000,1000,115,358,1000,1000,1000,44,1000,-1000,641,1000,644,-203,104,674,-804,1000,595,33,308,-412,-400,862,-596,-1000,959,-850,-192,-472,232,271,433,1000,152,-550,416,-256,151,322,-1000,-1000,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{-1000,-434,-229,-96,-383,-461,-732,557,-1000,-1000,558,-393,-137,1000,1000,156,-1000,21,-651,94,-707,-1000,875,-953,-469,991,-400,-208,313,347,1000,1000,-60,-397,-1000,-40,-716,1000,-11,1000,-509,-1000,1000,-1000,-1000,-1000,-637,-830,-345,-1000,-1000,-1000,-732,134,-217,1000,76,-867,1000,1000,755,-811,-695,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDecimalValue():java.math.BigDecimal",
            new int[]{400,120,-713,89,-400,-164,-29,-400,168,150,1000,-849,-431,-508,-230,1000,986,-163,102,352,-887,-1000,1000,-150,-400,-28,-387,-1000,-631,-637,189,200,1000,-222,-389,477,822,-139,-792,-548,-850,421,-450,-274,-656,-336,-262,-619,-1000,1000,-368,-610,266,1000,-205,-791,209,-411,400,-1000,-931,-400,-408,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDecimalValue():java.math.BigDecimal",
            new int[]{261,-856,483,967,-301,412,1000,-1000,896,235,499,-1000,-1000,-15,558,853,-305,-623,-620,-457,-24,437,713,823,-40,527,664,38,179,-498,-819,-1000,1000,-1000,-843,-312,-1000,-939,-689,-621,208,816,793,1000,-116,446,-738,971,19,-1000,-660,1000,216,18,1000,-769,1000,-669,997,-265,-251,-1000,-1000,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDecimalValue():java.math.BigDecimal",
            new int[]{127,1000,35,939,-571,1000,789,67,-647,-1000,-1000,-58,768,1000,76,-1000,529,-1000,-829,-562,929,1000,-1000,-155,1000,-254,-1000,1000,-662,1000,-896,71,930,-1000,942,-1000,1000,733,583,-1000,-616,1000,1000,374,-934,-1000,-16,71,174,-1000,-1000,-522,-414,283,-494,96,-1000,1000,-1000,1000,-1000,1000,197,563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDoubleValue():double",
            new int[]{-429,-948,-617,113,336,-1000,677,567,472,28,-1000,212,-700,-894,2,976,-430,-1000,-53,-341,-1000,-845,-9,860,36,-719,621,313,839,197,686,79,-607,-789,-745,382,273,-14,-822,895,-20,1000,-759,-582,950,-340,1000,-52,900,-199,1000,123,-784,63,-833,-972,951,943,14,667,-700,-538,1000,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDoubleValue():double",
            new int[]{232,-316,-617,107,54,-760,727,-574,1000,-1000,-698,-402,-700,-1000,388,1000,-313,-1000,1000,-1000,-178,-709,598,663,791,-1000,976,-1000,1000,-903,861,-594,1000,150,-741,685,273,-493,709,749,-49,1000,148,1000,950,-1000,835,-1000,1000,284,923,622,1000,856,-709,-743,-888,943,1000,713,-692,-556,1000,9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDoubleValue():double",
            new int[]{576,256,763,228,-504,332,-425,757,-1000,-902,242,-41,-762,-445,-477,-964,922,209,786,-758,426,1000,510,628,1000,64,362,-736,684,511,236,-643,-376,-198,845,-13,790,-545,1000,68,-304,494,893,564,-794,-976,-229,-1000,-955,1000,-813,461,611,1000,313,551,-838,-187,71,-1000,-830,787,612,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getEmbeddedObject():java.lang.Object",
            new int[]{648,15,-93,-183,206,-29,-1000,906,804,-170,207,-749,-640,-486,-81,608,-876,1000,-665,-1000,157,1000,-122,792,-626,1000,-257,-302,-1000,-608,1000,783,-238,50,1000,894,347,-920,1000,175,730,134,-911,1000,1000,1000,127,1000,613,348,1000,-1000,-1000,264,-665,1000,716,1000,1000,-1000,-121,25,192,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getEmbeddedObject():java.lang.Object",
            new int[]{-211,-934,-205,-502,-52,-330,-546,-1000,599,302,-82,-840,184,489,-173,-350,-226,-771,473,-703,484,-80,1000,1000,309,-1000,258,-485,-998,-345,-879,89,-164,1000,-411,-1000,1000,-410,-207,-227,-818,-367,-114,856,-343,-861,-248,642,1000,1000,721,422,-429,1000,-628,1000,708,744,-3,116,-179,1000,18,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getEmbeddedObject():java.lang.Object",
            new int[]{1000,1000,-745,419,180,-179,-939,235,-1000,154,151,1000,-872,-904,-66,-1000,8,-135,-1000,224,-1000,1000,-105,-108,-1000,-107,-1000,730,-112,729,975,1000,-983,1000,1000,291,-644,860,868,214,1000,684,-935,584,739,150,740,972,-908,-1000,1000,-1000,-400,-449,-626,241,-686,-233,-778,-1000,-1000,-991,587,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFloatValue():float",
            new int[]{-1000,384,439,-417,972,117,-488,-1000,-19,61,1000,-457,1000,-1000,1000,-1000,-256,-1000,-86,289,764,-43,1000,1000,657,-58,-1000,-236,-1000,-1000,-435,-167,1000,618,658,-632,1000,-282,113,436,166,591,759,-49,-1000,-11,-1000,-499,-329,-1000,1000,-425,-567,767,-1000,-326,857,114,1000,-690,1000,629,136,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFloatValue():float",
            new int[]{123,185,-385,-441,277,708,715,576,-1000,-154,-15,-584,608,791,208,-731,-764,-679,-123,234,459,-112,-365,301,-209,427,-1000,668,-288,-763,-918,-1000,601,-98,945,-293,347,-1000,-1000,-274,-21,-1000,975,-1000,654,-404,-130,-713,479,906,-243,383,-358,588,142,-1000,-198,240,272,-697,970,-177,176,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFloatValue():float",
            new int[]{45,475,643,798,210,-491,-1000,-80,216,-261,-149,1000,-20,-135,819,1000,739,-282,-533,-668,914,421,338,-9,-785,-1000,-766,-440,-793,123,620,1000,150,-444,-1000,765,-954,-1000,1000,-1000,703,256,509,-354,-1000,517,3,328,264,-379,494,-171,812,-1000,-1000,-313,-286,260,-573,-54,-284,981,-805,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFormatFeatures():int",
            new int[]{803,684,-341,-7,570,-803,-219,1000,797,1000,-1000,498,778,-893,-1000,1000,973,1000,22,-893,1000,1000,109,-404,-912,-288,-1000,-301,-23,-1000,-1000,300,320,143,625,-678,-299,216,-576,568,-102,-1000,-1000,85,969,1000,-489,1000,-747,-849,484,926,1000,-205,1000,768,-1000,970,466,1000,864,-476,406,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFormatFeatures():int",
            new int[]{-949,-634,-313,303,-510,-422,425,587,1000,-1000,501,-217,-995,1000,-336,-21,-1000,-424,90,-505,-395,1000,-1000,113,-1000,400,-1000,-405,-1000,-405,-289,392,-1000,590,-1000,742,204,-580,56,-7,-116,-1000,-1000,-940,708,-299,-1000,309,-946,396,-804,147,1000,-667,1000,613,744,1000,453,1000,-144,-648,35,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFormatFeatures():int",
            new int[]{-1000,1000,167,549,794,-218,1000,756,15,199,897,282,-646,1000,1000,-730,-794,1000,1000,1000,896,994,-1000,333,705,1000,-1000,535,-862,-700,-1000,-399,505,997,-899,99,1000,-16,-1000,1000,274,853,977,1000,1000,1000,-487,1000,-270,-248,1000,-315,-288,514,103,1000,-1000,573,409,1000,1000,-536,1000,805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getIntValue():int",
            new int[]{369,-21,751,-567,-868,195,679,-12,189,399,-21,327,-1000,-447,-516,447,-929,-652,-414,-269,-627,734,-190,1000,-23,556,-264,541,719,-730,713,182,923,32,-1000,-79,527,249,676,1000,595,-650,893,159,762,762,-1000,-145,1000,645,1000,1000,50,954,-693,-792,23,-262,153,-825,12,1000,799,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getIntValue():int",
            new int[]{-453,938,-949,169,-163,-643,430,237,270,400,223,-22,-613,717,881,119,589,87,-977,-6,-500,1000,649,163,1000,162,803,287,1000,819,322,-290,-642,31,-264,-671,9,339,-528,1000,1000,108,181,-909,-120,-277,-352,1000,-5,-1000,-86,530,1000,662,1000,-287,-972,-399,-99,-550,-210,933,-579,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getIntValue():int",
            new int[]{664,-206,327,-611,954,773,1000,424,37,-443,-785,737,1000,-40,-1000,-98,1000,-403,710,28,1000,156,-367,749,564,-201,-178,-766,-1000,1000,-154,754,712,-468,-872,-907,559,-650,1000,-148,-113,109,910,940,456,956,-176,147,-1000,292,956,-324,342,-380,-470,546,-966,-618,672,-1000,-789,776,1000,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getLongValue():long",
            new int[]{358,-363,683,-771,-773,-540,-158,803,285,1000,-796,525,1000,-121,1000,171,703,883,-478,870,-532,756,-886,-1000,-1000,-416,298,797,675,992,943,-65,1000,852,-1000,-794,893,1000,47,-1000,97,-1000,178,-211,-1000,938,186,1000,1000,126,-1000,1000,-472,-1000,1000,505,-675,475,-523,73,744,-654,1000,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getLongValue():long",
            new int[]{908,947,683,788,-60,973,-317,522,-294,431,901,-716,-929,1000,1000,303,-65,1000,-35,-345,981,-614,589,1000,765,-652,-153,917,-222,326,-653,-634,1000,-985,-712,603,-143,6,929,774,-127,-80,-1000,486,-1000,25,-564,-835,650,-262,1000,-48,528,-290,-1000,622,1000,1000,-1000,1000,-669,-762,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getLongValue():long",
            new int[]{-22,-86,303,-81,-589,-494,-688,-344,-72,-642,89,297,-357,1000,-789,1000,756,-1000,-433,1000,967,-968,-941,-381,-1000,947,-243,-597,-432,-384,-388,1000,529,-517,-12,617,-231,-398,593,-58,-1000,-760,976,-201,1000,428,711,60,-619,466,-186,1000,-728,-61,483,-416,-1000,-616,717,-1000,1000,-59,971,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberType():com.fasterxml.jackson.core.JsonParser$NumberType",
            new int[]{-436,165,-417,99,341,-629,488,583,628,378,-112,541,-172,537,1000,-1000,590,172,-55,-572,-1000,51,-426,1000,-115,-1000,-123,-201,1000,26,827,1000,-93,-911,-412,888,-572,741,-228,-277,909,718,-1000,-645,-487,-1000,865,-167,-264,706,-163,347,235,-62,-22,-293,-1000,-1000,-1000,379,-592,-1000,-138,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberType():com.fasterxml.jackson.core.JsonParser$NumberType",
            new int[]{-426,-1000,-357,594,1000,865,384,-89,-972,961,709,131,-360,-328,161,587,3,-1000,903,-287,404,814,-339,-1000,608,942,-789,-22,779,-461,693,288,820,700,-479,344,624,1000,-1000,-578,720,503,227,366,-255,-1000,-866,500,460,1000,924,31,642,-384,56,-545,1000,-310,1000,501,-910,56,-278,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberType():com.fasterxml.jackson.core.JsonParser$NumberType",
            new int[]{543,-428,-437,-156,441,-537,-205,-767,1000,84,-953,420,-944,667,626,-1000,-21,-518,491,-832,-517,-816,175,1000,763,-328,-506,-201,235,985,453,1000,-20,611,-484,282,-378,20,-762,481,876,742,-1000,-429,-399,412,1000,-24,-287,955,-1000,516,631,-99,358,516,-447,-1000,-980,-1000,168,-635,-810,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberValue():java.lang.Number",
            new int[]{1000,360,-29,-31,600,-400,685,921,-1000,235,-178,1000,1000,836,85,270,908,1000,-1000,-609,908,-1000,568,1000,208,-991,1000,375,1000,-789,271,1000,1000,322,-1000,-491,519,-427,729,463,278,1000,512,1000,193,-215,975,-264,1000,56,-1000,-1000,-515,265,105,-90,777,728,-492,-767,647,357,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberValue():java.lang.Number",
            new int[]{80,-373,-753,403,-180,-463,-900,-717,-59,485,485,-94,-246,-479,229,-994,908,239,-47,889,25,-886,-429,573,408,437,135,-472,-326,-838,271,-468,578,-383,349,-517,212,606,-980,463,278,196,-235,-189,193,206,717,-465,-593,-722,-839,-36,-484,265,844,280,485,672,-492,-767,640,-140,-100,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberValue():java.lang.Number",
            new int[]{468,-440,391,-211,-144,-1000,-1000,607,615,290,21,-479,375,-357,-116,-730,-441,522,-1000,658,477,375,-1000,572,366,-367,201,-994,-211,-245,451,-630,-1000,-1000,1000,776,23,-1000,525,-1000,-1000,990,-536,656,563,-1000,1000,1000,-968,-117,-1000,-1000,408,285,58,-201,148,181,-773,-803,-377,426,20,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{1000,-642,-541,-891,590,449,430,53,1000,-820,1000,-906,-78,-1000,61,-1000,-1000,-989,867,218,756,1000,1000,1000,559,-1000,-14,-294,-1000,-838,-698,1000,-128,-374,-847,576,1000,717,828,279,-965,272,-82,-584,-137,59,1000,1000,-1000,-999,1000,301,1000,-1000,-28,810,-229,264,336,-602,-47,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{1000,-538,-985,-561,696,561,-904,1000,-433,400,328,-423,163,-137,-33,479,-499,210,385,289,263,-548,1000,435,880,-376,-1000,-916,-1000,-1000,184,-2,-354,-245,1000,-1000,-22,44,948,-139,310,372,547,263,597,-982,-650,1000,-938,262,268,70,-269,441,418,585,208,534,-1000,-887,-324,343,795,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{-345,1000,131,-931,41,-839,270,-348,1000,-497,310,-900,-220,-445,297,893,823,506,-281,245,295,63,535,769,-787,-707,1000,-294,156,-667,1000,-1000,-986,176,-897,1000,-415,44,364,70,-1000,-865,254,784,-1000,837,694,-571,1000,3,971,654,549,-1000,623,-990,353,636,413,179,-1000,-228,965,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.ctc.wstx.sr.ValidatingStreamReader", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getStaxReader():javax.xml.stream.XMLStreamReader",
            new int[]{-150,615,-965,-796,-487,55,-439,-756,18,221,1000,320,1000,689,700,-779,388,502,551,-877,-756,150,1000,135,-406,-165,-580,234,-19,-698,212,492,-524,1000,981,-1000,-482,830,-235,-1000,312,31,-1000,-629,1000,-172,982,-1000,371,378,709,1000,-1000,-1000,-1000,-1000,566,-282,-600,49,219,235,909,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:com.ctc.wstx.sr.ValidatingStreamReader", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getStaxReader():javax.xml.stream.XMLStreamReader",
            new int[]{1000,-1000,863,-516,-158,-1000,1000,1000,319,-330,-1000,71,-777,-783,-212,1000,-700,-1000,-268,945,-669,-1000,-1000,550,59,-908,376,-1000,211,1000,-110,-1000,321,-1000,-1000,1000,537,-1000,-819,1000,694,708,-515,1000,-1000,-47,-388,1000,-837,826,-1000,285,1000,1000,714,1000,343,168,43,120,195,-681,-1000,224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:com.ctc.wstx.sr.ValidatingStreamReader", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getStaxReader():javax.xml.stream.XMLStreamReader",
            new int[]{1000,1000,-885,119,158,-459,541,-331,-614,-147,-1000,342,339,195,-51,1000,-352,370,-797,-932,-993,104,303,-470,-544,-956,1000,580,209,767,360,-991,-66,-1000,669,-363,-452,-1000,-948,1000,-953,-734,833,748,448,-919,-999,-118,-332,519,-347,-475,-772,1000,1000,-1000,-497,-549,-546,62,471,-557,-381,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-30,-684,779,69,707,572,749,-136,-126,-1000,-400,815,-72,974,-490,-10,-197,1000,-63,-284,612,334,-637,191,64,160,-188,212,555,-400,-372,210,-104,-835,643,-23,981,-1000,105,250,-525,824,-13,-331,-622,-523,70,421,12,-260,636,229,-423,-286,96,-400,159,185,400,-688,-62,337,23,-251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-426,749,219,-627,-1000,-90,-149,720,10,592,1000,-1000,427,-621,-269,-283,-111,-1000,-552,-1000,-701,-963,731,744,-204,-184,284,542,-269,1000,-382,1000,330,1000,-995,-1000,1000,1000,202,-1000,-1000,-450,-401,-363,72,792,-1000,-888,102,-413,476,-200,1000,-278,720,261,524,342,-1000,348,610,1000,608,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-936,1000,650,204,-168,1000,235,545,560,937,1000,-527,-1000,-1000,388,-1000,1000,-1000,-119,-1000,-520,-1000,1000,-662,1000,-87,-1000,312,-1000,1000,-1000,389,-1000,-1000,-782,-106,-1000,919,-1000,-829,-1000,-718,123,572,210,742,1000,-1000,726,978,660,673,1000,621,1000,1000,1000,1000,-1000,-456,-1000,-1000,1000,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-999,-335,947,569,988,777,1000,-498,-174,-1000,505,1000,-422,335,773,-482,1000,919,466,-740,-1000,-686,-341,-504,542,1000,-443,-445,395,306,-1000,-447,-601,-1000,400,815,1000,-962,1000,1000,-850,791,644,-596,457,-1000,756,1000,97,338,1000,1000,198,1000,228,956,-1000,1000,-455,-781,3,1000,633,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:fQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{157,-280,150,398,1000,-51,344,33,-630,-414,-995,1000,-31,-545,731,-331,-331,882,281,-404,277,848,342,-597,98,-459,-555,54,-173,-287,486,-888,-231,-952,202,1000,1000,-661,245,800,-728,-379,178,286,-548,3,788,-777,355,182,-37,-54,-640,246,-405,-908,779,-360,502,-688,-625,104,-766,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{-456,57,551,444,-468,887,1000,1000,1000,-931,1000,-1000,-358,361,-1000,1000,-87,1000,1000,311,471,532,-1000,102,-1000,-1000,422,-632,-1000,-1000,822,-1000,493,-477,-1000,-601,-478,-988,644,-971,-1000,333,1000,8,1000,-1000,-1000,266,-207,-1000,210,1000,-366,-637,623,-231,-824,815,-1000,-793,-134,-831,1000,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{966,938,-233,309,-470,870,1000,-887,-636,1000,107,-1000,-230,-93,-569,28,1000,-74,-1000,-380,-128,-145,110,992,-670,-173,-105,1000,572,-224,1000,767,-803,511,-375,-1000,1000,1000,122,89,852,-1000,-337,-246,-504,1000,223,-45,-1000,108,664,-856,929,504,-1000,-1000,-970,983,-777,-1000,200,-509,694,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("ARRAY:[C:5:24:java.lang.Character:dg==:24:java.lang.Character:YQ==:24:java.lang.Character:bA==:24:java.lang.Character:dQ==:24:java.lang.Character:ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{-267,-530,166,-536,331,993,1000,413,413,284,284,-1000,92,-228,-383,-234,240,-439,-306,724,-1000,-436,-962,806,557,-820,119,-512,205,-1000,-215,272,-435,-155,224,-533,-634,-637,-716,-547,161,-645,-101,-1000,1000,86,932,-444,-1000,743,-1000,-483,569,102,677,-786,-714,549,166,-1000,39,127,-988,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:NA==:24:java.lang.Character:Mg==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{995,-455,-309,134,-702,1000,-1000,116,-1000,919,-1000,1000,904,-398,1000,168,-240,348,-1000,515,-896,1000,1000,925,1000,1000,652,662,1000,904,244,196,-104,-301,1000,-1000,-1000,783,-1000,-120,1000,75,-1000,-521,-495,1000,0,157,33,-743,401,-753,1000,676,-1000,780,260,1000,1000,-490,-554,-976,-1000,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:fQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{-275,5,46,-901,174,378,151,-45,-388,1000,-416,-52,253,489,-605,1000,-20,-287,-873,802,-119,-313,-100,-284,629,229,-483,157,20,-191,-755,1000,-32,-561,1000,698,1000,65,323,-331,1000,-96,-694,-223,165,887,-1000,-213,-335,1000,-575,-1000,-65,75,6,-613,-673,-335,-1000,-118,-109,-1000,-1000,-571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{571,465,795,-976,798,866,316,619,125,-480,952,221,-917,-513,-281,539,-812,383,846,-784,-330,-306,-738,-580,-239,383,32,645,711,-453,84,728,-514,-106,500,-493,701,226,-579,344,770,758,-835,-190,472,125,571,243,-827,-785,740,596,-184,602,-594,11,417,-730,-59,-96,-660,566,-343,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{-169,431,307,-706,152,1000,625,780,-360,-944,74,-427,-1000,-1000,1000,-709,87,1000,1000,989,-1000,1000,831,640,236,909,-966,751,798,-274,-1000,-811,-735,-885,-950,567,-1000,1000,283,-1000,-1000,-701,-443,151,1000,162,918,-154,-452,1000,-632,476,1000,-1000,571,1000,-1000,-1000,866,-199,-1000,825,-1000,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{210,874,234,564,581,-253,188,-360,504,328,-430,-306,584,471,-1000,123,-399,-25,-1000,-191,339,-628,-1000,-88,794,-1000,-16,1000,1000,-617,1000,1000,1000,554,1000,785,1000,-404,-124,964,1000,1000,-1000,362,-436,-61,-121,-146,1000,-387,214,-1000,-1000,1000,20,-1000,670,396,-435,813,1000,-672,508,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{-195,-1000,766,-226,158,1000,595,-769,814,555,-736,-453,1000,-553,-280,-247,-130,-345,-173,1000,-842,705,-328,1000,742,-1000,-606,916,1000,443,-449,530,701,545,-426,1000,151,220,-329,-25,-215,-914,467,595,240,-222,-342,-948,704,1000,-1000,778,439,1000,368,-630,952,-269,-1000,-966,228,-438,-149,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{816,277,159,64,37,-1000,-142,-242,576,501,-574,119,389,1,-928,-914,-38,96,-543,-225,-70,-421,-644,514,356,-712,-478,-19,361,-115,666,651,1000,364,94,487,-58,-193,344,194,345,969,-597,621,-514,-801,-234,-427,774,404,421,-153,374,534,-9,-266,-216,-1000,-298,441,182,-447,81,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextOffset():int",
            new int[]{231,-219,423,26,384,-295,-19,512,-1000,-161,878,-592,-248,438,194,-1000,-657,-851,471,93,1000,638,1000,-794,-845,1000,805,1000,390,923,385,-1000,-199,79,-570,-14,-979,184,18,-1000,-927,348,-871,349,-486,-336,-387,-1000,341,197,124,-283,467,751,-1000,898,-1000,86,347,-444,1000,552,1000,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextOffset():int",
            new int[]{-106,-277,-493,-521,-139,1000,156,-51,-460,-1000,3,-1000,-1000,-625,752,-1000,-1000,461,121,-944,-24,-522,1000,-44,1000,1000,515,1000,-159,1000,965,-1000,-1000,996,-267,-988,733,450,-877,-1000,-723,-1000,-1000,-413,768,341,-651,-1000,-1000,-1000,-495,-1000,305,227,1000,-914,-1000,443,1000,747,-700,-1000,1000,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextOffset():int",
            new int[]{873,1000,519,49,-59,512,-782,1000,675,-309,-554,-138,-179,-95,-106,-319,540,1000,-228,-45,-1000,-337,-959,652,943,-943,-1000,-1000,-236,516,-562,377,-164,-278,1,179,1000,303,-71,688,214,-747,-176,352,43,909,394,1000,-117,-258,733,80,-119,-545,832,-927,1000,-531,325,-480,-367,240,226,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTokenLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{564,474,11,-341,110,-852,-59,-618,694,-742,-70,581,477,-644,-564,319,521,1000,-408,-618,-881,-274,1000,345,891,16,1000,-291,-893,-243,-1000,-1000,1000,-568,-759,-179,1000,-802,-1000,-1000,1000,-768,1000,-239,-155,-195,221,545,281,848,908,302,-660,-592,-767,936,1000,-1000,-1000,-149,353,-30,-1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTokenLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-784,-130,355,522,-504,261,1000,5,-282,-37,1000,38,-297,-485,569,-21,592,587,837,0,-1000,1000,197,546,774,536,-696,640,-115,292,-397,1000,1000,401,-213,839,-752,679,177,766,1000,257,1000,209,-567,2,1000,290,-891,-908,702,6,777,391,877,-1000,-333,-1000,436,623,959,-1000,567,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTokenLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{861,1000,323,-386,54,-349,532,-65,563,-342,-453,132,-22,695,-738,-12,-245,661,674,-149,615,-316,11,364,1000,-247,841,-574,-840,334,-1000,-1000,1000,-186,-1000,-117,426,-636,920,-1000,775,121,314,286,407,226,-183,30,42,340,-202,252,1000,13,-605,920,706,-926,-602,108,-712,-238,-461,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{918,798,-125,-278,-394,-104,-1000,-246,1000,311,-559,1000,53,-1000,-503,1000,-254,-391,825,1000,-835,323,-871,985,654,1000,375,-1000,-992,1000,94,1000,-1000,68,162,-258,27,977,-79,23,922,731,-231,-948,1000,-60,-971,-898,161,232,-865,-1000,-998,538,-744,-475,616,582,99,-916,1000,335,-976,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-1000,194,255,519,569,527,724,780,-330,-1000,888,-223,534,255,-742,224,-475,-331,1000,-8,1000,-418,1000,-785,-800,-1000,1000,-638,1000,-1000,489,-585,561,851,517,960,-121,124,237,-456,-699,416,442,841,-337,-740,-80,454,-308,670,152,489,541,737,609,594,-821,77,1000,-23,1,579,149,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-1000,457,890,-671,-1000,1000,580,-991,-431,-39,-286,900,-302,1000,555,1000,799,7,865,-387,-904,-884,678,146,-276,399,-556,-194,80,1000,-1000,-852,565,1000,-575,677,-1000,-258,941,-105,-774,-171,1000,766,-474,101,1000,-1000,-821,-247,204,306,410,-117,1000,-73,153,766,229,309,-265,-639,367,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{187,-997,670,-182,30,-231,-133,114,535,604,32,372,-19,239,234,-885,-962,-1000,652,835,-1000,-519,925,-661,-1000,825,-112,-583,1000,606,924,-569,-724,151,-244,260,292,536,-113,137,-376,-251,498,-37,-889,-465,98,654,422,-1000,905,481,1000,-607,616,1000,666,-455,979,1000,-705,680,851,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-689,1000,-717,-678,-911,498,-983,153,927,31,-487,583,-623,456,37,1000,-83,215,878,225,666,-735,-1000,-107,531,267,918,547,-600,461,-161,-654,-162,350,-1000,1000,244,244,1000,36,-191,551,108,468,697,427,-713,-1000,-393,748,-1000,-650,-863,1000,-983,-241,61,216,177,-1000,302,-1000,-369,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-559,131,521,-281,-1000,-1000,-238,-768,1000,1000,905,1000,-476,-146,917,-989,-1000,-1000,1000,1000,832,-288,824,660,107,-754,-72,-83,-351,-535,-451,452,-1000,-528,-723,218,-493,-406,548,-1000,766,1000,-189,-561,1000,-620,-559,70,1000,294,-409,-771,-62,-613,499,953,338,968,462,15,527,854,973,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{689,738,-17,-196,-18,-206,-70,570,350,833,-27,551,-786,-4,157,-893,-1000,-928,562,-4,-1000,319,63,829,180,101,-394,192,84,117,102,354,-375,-1000,135,1000,521,350,505,1000,1000,1000,-1000,41,-321,-50,-107,-1000,165,-384,188,-83,-190,-323,577,-304,301,283,402,-32,59,-284,769,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{-43,-292,71,-562,751,185,775,929,623,-1000,-167,414,-838,-1000,-587,-1000,472,-174,376,-387,-577,-255,145,867,870,-972,260,152,54,-104,-263,-793,-619,805,-1000,456,722,-35,748,1000,1000,319,1000,142,-336,965,-239,157,-259,198,-304,-391,618,270,-603,928,498,1000,-791,-713,509,-400,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{273,31,-162,244,560,-1000,939,394,708,-103,-350,309,-151,-781,1000,-1000,420,400,1000,255,-110,539,-376,1000,787,-178,-453,678,183,355,420,263,-871,72,291,629,350,-788,1000,31,43,1000,400,-1000,-590,-415,97,-213,-864,145,264,-778,111,-194,379,262,-111,394,578,-822,-305,38,903,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYWFhYQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{1000,677,-6,594,-403,-196,189,1000,904,708,-1000,1000,823,76,17,-1000,-203,-1000,956,-289,-1000,1000,-1000,1000,-362,1000,-1000,1000,-1000,-670,-484,1000,-935,270,-400,777,638,612,830,-976,1000,1000,-1000,678,-586,-1000,1000,-919,-974,-1000,758,-1000,1000,206,494,393,-1000,449,1000,-7,1000,-446,1000,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{330,781,415,519,-421,660,-904,173,549,611,-230,342,-126,269,-808,-976,201,-613,759,135,-636,465,-691,176,-833,447,-289,-162,-672,-699,-958,919,11,-159,7,-415,602,493,337,-437,904,873,-657,-872,-219,-954,574,-79,-615,-919,540,-442,627,178,-145,-244,-726,4,851,-713,176,-856,662,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{1000,694,349,174,185,-510,380,841,354,698,0,1000,-585,-1000,-691,-294,-574,-1000,173,127,-1000,1000,196,1000,-442,-11,-861,758,-299,434,-494,979,348,-1000,-592,1000,402,-112,1000,1000,1000,1000,-1000,-939,-807,-737,-376,-1000,-802,-1000,-24,-1000,240,-1000,1000,-208,-209,458,726,-1000,671,254,751,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "hasTextCharacters():boolean",
            new int[]{179,-75,431,293,-34,363,-185,-630,-481,-528,401,-894,811,-435,-800,280,665,881,858,-1000,794,-653,-763,178,-688,-132,167,-799,490,964,-746,-503,69,771,-967,1,254,-638,-467,675,-300,167,-643,162,-783,-139,751,-569,-245,-490,-178,848,-749,-18,57,-535,-616,-317,-388,546,-53,536,425,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "hasTextCharacters():boolean",
            new int[]{944,-1000,663,9,-942,-919,-359,-1000,569,136,851,-894,-317,870,384,76,42,-375,-408,-753,-389,652,186,190,-235,-84,706,-990,-443,-95,-1000,217,-1000,660,675,1,860,-849,-7,-197,-553,1000,104,-41,213,311,870,-548,-279,228,1000,-1000,278,9,417,1000,-616,530,408,406,426,-893,244,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "hasTextCharacters():boolean",
            new int[]{-473,670,375,-401,462,-928,-840,933,413,-51,763,564,950,-377,1000,962,-294,-1000,727,-14,-467,-564,-674,-1000,1000,-1000,-1000,-261,-814,20,-1000,-675,-1000,568,189,-474,456,-429,790,-1000,-695,1,9,-473,1000,-848,-335,-393,-792,345,1000,-1000,599,-343,1000,353,-194,993,-769,-545,1000,-1000,371,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isClosed():boolean",
            new int[]{-84,855,599,514,-5,300,231,-267,1000,680,-1000,-678,-268,232,-60,-786,312,38,389,-1000,7,43,-69,-749,-1000,-482,736,94,-741,-681,729,448,432,702,449,461,-481,690,-59,-713,471,967,-162,-747,-111,275,-284,-1000,-592,-774,1000,-84,-1000,38,-508,-918,958,387,-974,-229,297,-325,-1000,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isClosed():boolean",
            new int[]{1000,662,175,119,66,-46,546,-189,15,-1000,965,-1000,-1000,249,-896,1000,-943,-304,314,1000,-916,111,224,-1000,887,617,1000,1000,271,599,178,1000,-333,-578,-337,697,732,-66,545,-428,-420,572,-693,-203,-448,-588,-97,17,1000,242,305,210,1000,1000,1000,739,-1000,-876,198,422,702,-423,-560,-432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isClosed():boolean",
            new int[]{-761,-629,-237,-841,231,-467,-358,-978,943,-383,803,875,727,-865,-233,-923,621,-328,3,-637,707,-45,875,557,360,-522,-936,-761,-539,687,-445,-669,175,816,22,162,-598,-953,-697,737,-342,893,-478,660,795,-545,393,-581,702,473,-767,-373,-48,-956,-669,184,-808,243,402,311,-982,-63,137,950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isEnabled(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):boolean",
            new int[]{-459,204,-633,179,570,437,337,-640,170,-135,296,-515,349,-373,-843,574,341,94,667,309,650,168,-802,-500,-603,-665,650,929,-206,620,697,-980,-10,875,450,739,268,982,782,-70,-348,520,-18,-92,326,-842,-775,507,-30,662,400,70,-907,745,-747,-157,776,-787,-489,143,-179,-139,694,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isEnabled(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):boolean",
            new int[]{95,407,-749,629,229,510,282,115,-360,65,-1000,-995,-249,422,155,-43,510,-415,209,559,-90,-2,-574,-1000,-69,-413,-493,29,762,-95,740,1000,-41,-282,524,59,765,53,555,888,193,737,781,-104,529,-1000,625,902,-669,608,135,1,-1000,-270,310,117,-446,-770,-593,748,974,-1000,-406,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isEnabled(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):boolean",
            new int[]{447,1000,-385,-222,427,71,-106,1000,-1000,56,-660,109,-1000,114,1000,-138,-1000,-394,777,996,810,-117,95,1000,-223,727,-423,-1000,-1000,795,1000,578,1000,1000,242,529,747,848,-398,-461,-166,-564,-1000,-405,1000,1000,-204,397,131,-756,273,-1000,1000,697,1000,315,751,132,-1000,460,-337,196,1000,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{305,-627,103,614,1000,469,174,-1000,-207,1000,-178,784,-1000,-1000,462,715,212,744,-232,2,-130,366,34,-882,-663,123,497,-390,131,1000,1000,486,122,-1000,838,832,1000,250,-178,304,-618,-112,-22,-362,46,588,-555,494,-136,855,597,1000,-1000,1000,1000,-2,1000,-494,-1000,-863,666,-873,-824,-833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{935,17,371,999,780,843,215,-934,-244,929,-421,168,-1000,-782,175,782,532,923,-759,-283,56,-180,944,-141,-829,283,-100,-87,851,600,258,192,-44,-1000,1000,-881,-400,398,335,841,162,968,-1000,-400,485,1000,-633,-674,56,333,63,955,18,536,1000,697,523,129,-403,-718,494,21,-275,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{-17,-535,-307,78,200,-363,329,-296,-697,811,202,180,-1000,713,726,647,321,328,605,40,-133,-248,52,-35,627,195,-459,-594,-538,671,1000,548,282,-83,122,-298,-369,293,323,788,510,515,-359,293,324,-546,84,299,-223,950,308,999,-941,715,-515,137,-449,916,-975,716,300,-485,-582,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{-121,-752,-137,24,520,-743,191,-552,391,923,-46,-276,-198,699,354,-42,509,-82,-152,665,133,-276,5,972,721,371,406,-299,-1000,671,724,-567,-340,16,782,442,-903,-165,250,348,-457,-413,-386,1000,-232,-1000,-352,910,-1000,920,508,999,-784,1000,502,-120,-211,-291,-1000,1000,877,-485,-737,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{499,609,-541,323,918,-1000,-512,236,-82,-194,-11,-486,973,-847,653,1000,1000,-983,1000,-462,139,-819,-928,-194,-495,-332,-547,65,993,-809,237,-682,847,-495,374,794,-326,-1000,-415,387,384,1000,462,-365,-356,101,-893,-512,-635,236,-241,909,931,-1000,-1000,376,709,-158,1000,-639,-940,-128,643,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{721,503,519,-771,947,-980,363,1000,787,343,-1000,-1000,68,171,-847,1000,418,1000,1000,1000,-1000,-1000,-892,-1000,1000,78,-1000,1000,433,-1000,-302,-989,1000,1000,1000,1000,1000,-1000,-1000,98,794,388,-1000,1000,-1000,-995,-414,-1000,-214,1000,-1000,1000,1000,-1000,1000,1000,-1000,1000,1000,-326,876,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-877,-841,973,158,770,-584,-70,-342,1000,-703,568,70,-914,-80,1000,12,-84,1000,-381,-1000,-906,717,690,521,-873,355,288,963,-903,285,-417,1000,-412,744,-405,804,733,-203,206,-604,887,1000,896,124,-924,-726,854,354,305,1000,-1000,-735,105,-119,-586,-792,176,75,-235,79,-565,1000,415,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-635,181,-701,-346,319,141,-443,-599,-163,-1000,497,914,519,-518,1000,1000,426,-335,438,-1000,606,-141,-1000,639,-938,190,10,-255,854,-1000,976,-653,131,-1000,898,61,-1000,-399,-199,-80,-492,-400,524,-1000,228,1000,-851,274,-346,-473,-264,433,-189,-250,-1000,-45,1000,-1000,351,670,-1000,607,971,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-614,-839,796,-951,-191,125,574,872,519,221,-960,-602,-7,584,-863,940,32,128,-193,273,-866,-389,-661,436,78,102,-51,510,797,-213,653,-126,55,-259,107,504,659,-706,-597,332,978,914,-921,339,403,-843,561,108,36,155,95,917,395,-619,872,60,-823,-47,474,-904,269,-361,44,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{739,879,250,198,619,-264,-221,1000,-373,-111,141,-1000,494,-1000,633,-78,915,-372,1000,1000,-741,-1000,-897,-687,546,-311,-1000,164,331,-1000,33,-756,1000,-307,171,52,-17,-1000,-280,735,1000,462,-764,1000,-469,-318,-1,-914,-555,863,-421,586,446,-303,1000,1000,107,770,896,-977,855,-1000,-579,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-528,-749,-894,849,-143,410,388,-1000,-282,709,310,34,563,-475,-283,1000,99,-1000,-379,-1000,796,1000,1000,1000,-1000,-225,1000,-1000,1000,1000,-1000,112,-1000,-1000,14,999,340,1000,1000,-612,-387,994,1000,-1000,757,-91,-65,1000,679,-1000,-312,-1000,12,867,-1000,-1000,1000,-729,-942,-968,-1000,1000,1000,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{480,-182,161,-62,947,893,-331,-269,881,1000,-1000,-1000,1000,-863,-516,816,1000,-1000,-459,-53,-539,140,-568,-203,392,-291,269,-1000,266,-242,1000,-11,725,795,1000,-1000,437,-602,-41,269,-1000,659,659,-915,1000,-814,-196,62,-157,22,1000,1000,115,268,1000,-1000,690,863,-388,-1000,1000,-539,-468,-589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonToken:FIELD_NAME", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{537,666,255,604,80,-628,-1000,1000,-763,1000,-1000,-424,489,468,-548,1000,1000,959,837,-1000,-977,-1000,244,874,-33,1000,1000,620,-568,160,-237,-433,352,-1000,550,333,-345,-143,-15,398,510,824,-311,332,212,-719,405,-1000,-1000,613,-1000,1000,96,198,-550,-1000,-407,-659,-434,1000,224,-1000,1000,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{723,-1000,-229,-772,-407,72,1000,-883,-1000,-363,1000,1000,-365,-398,876,-979,301,-783,-1000,806,-1000,411,-128,1000,-739,-713,3,-1000,-800,-527,-433,-383,-565,721,-104,830,258,411,-434,-873,-784,-713,570,-1000,221,1000,-728,1000,-1000,844,651,649,338,-1000,598,1000,-1000,182,302,-1000,919,427,351,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonToken:FIELD_NAME", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{1000,892,919,-331,-374,-85,-449,-95,151,-107,-159,-1000,729,1000,383,-120,1000,58,-57,-442,-275,794,-146,1000,1000,569,279,-260,372,-221,320,273,840,-315,931,76,-219,-1000,-521,-55,128,-1000,283,-212,268,844,404,-607,-264,570,-116,121,319,-758,528,1000,-998,195,1000,1000,-1000,-706,556,-884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{-1000,585,-37,329,-244,-1000,-48,-619,-159,79,-98,-828,495,480,-520,0,-187,-212,-91,-16,-923,-84,496,572,553,487,430,1000,-600,473,996,-27,-146,68,-53,-393,-102,-264,350,100,-663,-412,135,617,-87,-699,-750,996,-737,305,-192,385,-714,903,-614,-284,456,347,-434,597,418,735,53,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{-465,-502,-381,592,-791,-180,-1000,-1000,-423,-1000,-1000,1000,1000,-634,-423,-900,-1000,1000,-1000,-770,292,-606,1000,-194,99,-1000,1000,719,1000,-1000,-334,284,1000,-635,143,-528,-492,-639,-1000,-371,-1000,-273,274,773,138,1000,1000,313,1000,928,-161,-6,1000,1000,453,-959,-1000,122,-188,1000,-1000,-473,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{-899,-527,-289,394,578,-988,54,415,-138,-901,-456,-1000,351,1000,-249,-167,-347,-143,16,-343,-557,708,1000,435,703,1000,-1000,191,-58,1000,321,222,-737,711,335,307,527,517,529,868,-810,-105,98,1000,176,-988,-893,1000,-216,400,1000,-357,-427,367,-457,172,203,17,-1000,597,626,24,-266,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{-549,-445,185,-546,-630,1000,-607,-907,370,725,346,-359,532,-559,-363,48,703,273,1000,540,-140,-1000,151,234,-1000,230,266,445,-1000,500,-144,698,-1000,-283,891,-220,-523,-385,754,263,426,-286,-48,-667,347,-1000,904,-572,-713,442,-29,-99,74,-915,-698,-967,1000,1000,41,-1000,274,153,549,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonParser",
            new int[]{580,366,803,474,-18,-17,-567,-414,-359,962,888,-197,-675,160,350,-69,796,-439,440,872,-1000,95,-1000,-251,1000,607,-1000,-319,-919,878,670,781,57,-347,-178,305,-672,918,-723,1000,-1000,287,169,-48,-481,662,-614,-32,893,245,-1000,-839,692,236,-738,863,-577,531,150,776,-684,-337,425,941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonParser",
            new int[]{-472,-589,-281,-806,-576,591,799,947,-799,163,476,-65,331,-256,-290,-339,-55,341,-980,-733,-680,976,-210,-198,484,137,-504,210,-549,245,-87,374,763,867,896,-236,-583,-278,143,183,-983,-361,-477,-86,-979,611,989,184,-625,812,-89,-771,-564,-955,692,552,142,-545,995,-81,-328,-458,583,565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonParser",
            new int[]{-870,1000,607,-477,-762,430,-643,-221,-935,-125,-755,801,1000,904,-1000,985,-462,297,-51,-1000,741,939,207,38,-1000,1000,-24,1000,-668,57,380,-678,645,-253,1000,-602,1000,-719,344,1000,-529,-1000,-1000,-1000,36,1000,623,-547,-122,1000,476,-2,1000,-1000,325,-1000,14,941,-289,1000,829,-547,391,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "requiresCustomCodec():boolean",
            new int[]{822,-600,-509,659,275,970,18,991,-575,-232,63,-426,461,215,-1000,568,302,11,-598,-121,-1000,-638,899,456,962,-47,494,-1000,-91,-843,-919,-1000,-427,-302,295,800,-65,-1000,469,673,1000,802,-923,-837,-80,1000,654,-1000,-546,-428,599,-365,1000,258,197,1000,265,-1000,-823,18,-1000,-1000,719,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "requiresCustomCodec():boolean",
            new int[]{838,-130,195,637,-233,768,150,101,-712,-842,597,-259,715,-936,21,834,710,-139,-805,739,804,178,60,332,818,362,-362,-705,-979,-662,634,-946,956,-285,550,-445,505,114,802,421,996,-128,587,-499,950,499,-936,329,377,581,-206,614,-512,168,-692,502,104,-890,-803,65,-871,-686,159,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "requiresCustomCodec():boolean",
            new int[]{-545,-431,-225,-276,354,-580,715,947,330,-1000,1000,552,650,1000,-1000,894,298,-521,698,-797,-808,-725,-99,16,739,323,935,-525,1000,214,-1000,-688,632,784,-168,299,-1000,-1000,144,1000,583,913,11,-196,-1000,1000,1000,-408,-691,-1000,611,380,1000,-494,1000,-378,1000,-462,-1000,306,159,-190,274,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setCodec(com.fasterxml.jackson.core.ObjectCodec):void",
            new int[]{-167,-405,-437,59,-70,65,-388,688,-138,274,-119,198,-640,-8,-619,-455,141,-879,472,805,-454,-55,-571,788,915,348,562,234,-895,306,-45,980,-444,-95,-995,397,-196,157,555,9,180,149,-181,1000,-57,303,-48,860,181,-380,1000,52,755,-144,-412,291,571,1000,-1000,52,190,389,0,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setCodec(com.fasterxml.jackson.core.ObjectCodec):void",
            new int[]{241,-841,-461,-31,-156,-24,375,-72,-1000,-1000,1000,1000,-547,-652,-361,1000,440,-77,-381,-914,-443,-50,218,-934,784,825,-251,583,-160,0,971,1000,815,778,1000,398,-67,805,-81,1000,-313,1000,-1000,680,-432,-148,417,-574,-285,810,52,-560,233,-599,-960,-1000,1000,-907,-1000,-988,1000,-475,-755,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setCodec(com.fasterxml.jackson.core.ObjectCodec):void",
            new int[]{838,1000,703,884,-1000,-77,229,-1000,197,1000,-174,1000,-133,1000,-1000,-872,255,-1000,1000,1000,-638,209,-1000,1000,1000,119,1000,821,-750,1000,-853,1000,1000,-20,-1000,-1000,-241,-1000,1000,399,-1000,-344,-569,194,636,-18,-253,1000,1000,-604,1000,869,1000,-1000,-1000,635,1000,-1000,1000,-1000,813,205,602,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setXMLTextElementName(java.lang.String):void",
            new int[]{-599,-39,-269,909,480,-106,1000,-1000,-763,-1000,1000,-35,325,1000,-1000,-1000,-706,1000,-1000,-1000,251,-94,-1000,938,198,1000,-1000,-1000,144,1000,644,-501,321,-770,-1000,-1000,1000,1000,-363,-288,-642,643,-532,436,-1000,-997,-275,1000,-1000,635,1000,223,-610,-406,526,1000,704,-598,1000,1000,249,-852,690,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setXMLTextElementName(java.lang.String):void",
            new int[]{263,-415,-457,604,-940,556,-549,312,677,368,-438,-10,145,353,811,1000,467,-493,1000,653,327,823,850,1000,68,-353,807,822,-418,485,346,-1000,97,1000,1000,995,-1000,18,75,-1000,625,-1000,290,-909,1000,-502,-990,17,-440,640,762,1000,-822,1000,-21,-872,-154,542,-299,1000,1000,398,-450,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setXMLTextElementName(java.lang.String):void",
            new int[]{1000,-869,295,-211,-1000,526,322,-570,1000,11,195,-368,10,-39,-261,667,-628,496,226,104,102,-252,1000,531,1000,410,-401,980,-88,-603,537,-98,-435,815,-845,294,-563,236,1000,-928,-495,257,1000,-1000,1000,-98,-256,1000,320,749,-266,991,470,-17,460,-658,-1000,-1000,-238,-236,1000,-443,-1000,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "version():com.fasterxml.jackson.core.Version",
            new int[]{-538,-846,611,-417,-917,-1000,387,234,-566,-7,-231,108,-774,1000,-95,1000,-206,29,38,927,-1000,-1000,-872,516,698,1000,538,-1000,1000,1000,533,720,129,-875,-1000,479,927,619,31,-118,424,1000,1000,-958,-26,828,-1000,198,-163,999,-1000,1000,-1000,-25,-755,-1000,-898,-647,-329,-552,-1000,104,-509,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "version():com.fasterxml.jackson.core.Version",
            new int[]{-637,-1000,27,-471,-1000,-1000,306,293,-680,-294,-69,281,-996,601,257,-766,809,-302,236,-213,-400,-600,-1000,156,-223,792,283,-895,149,-80,-137,1000,525,-870,-359,1000,11,133,756,-531,147,927,-1000,-928,369,237,-568,-678,-673,-223,-1000,452,329,-318,-302,-245,-329,111,365,-687,-811,170,-796,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "version():com.fasterxml.jackson.core.Version",
            new int[]{-391,-590,155,394,437,-203,414,-1000,-528,216,-658,114,-469,-395,-372,-851,1000,12,-742,-68,344,-1000,-1000,646,-331,458,-830,146,-271,149,674,1000,1000,312,-661,487,-511,1000,141,-177,1000,-26,400,95,-1000,1000,-1000,1000,-463,956,-886,-434,-405,-1000,-174,598,838,-960,707,-160,779,-103,97,428}));
    }
}
