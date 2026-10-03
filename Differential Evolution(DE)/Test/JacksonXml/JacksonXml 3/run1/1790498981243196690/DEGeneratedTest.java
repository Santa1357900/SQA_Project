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
            new int[]{-968,18,-229,-457,-1000,-420,1000,-919,219,-332,1000,-443,-582,330,958,699,-1000,-24,-204,177,-531,-219,-1000,448,129,213,-200,1000,-1000,616,-1000,-625,321,842,-62,-1000,-1000,909,268,235,355,-1000,1000,-765,-1,1000,-1000,-1000,752,1000,-330,-259,937,-351,15,1000,-738,-1000,927,213,-237,-1,384,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "addVirtualWrapping(java.util.Set):void",
            new int[]{-437,314,-53,-891,-376,-139,256,-925,-29,-1000,1000,-443,-732,-244,1000,1000,-1000,-359,667,197,-693,-622,-529,775,-739,196,602,749,-1000,616,-1000,-878,168,1000,124,-1000,-1000,424,830,-406,-269,-1000,1000,-1000,-42,801,-1000,-1000,1000,1000,7,494,1000,-1000,771,-288,-738,-1000,1000,319,-205,16,-363,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "addVirtualWrapping(java.util.Set):void",
            new int[]{1000,-758,731,-931,937,55,-295,-592,-109,757,-947,552,-722,277,-963,1000,-411,-665,248,32,-498,575,411,777,-629,739,366,312,78,-633,381,-32,-624,-958,1000,1000,-1000,-239,310,458,-187,235,233,92,-203,793,-704,-525,625,-953,470,-66,-228,-1000,-435,480,-825,-328,83,-1000,-316,1000,-534,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "close():void",
            new int[]{-934,657,395,-621,691,252,-217,152,-812,55,788,-794,-595,77,532,162,826,-547,630,796,974,-228,-644,-360,-387,-465,-163,715,-652,-740,-978,381,-136,-887,-264,708,-992,-21,-998,59,203,872,-6,-359,256,257,472,646,532,415,-642,337,-319,360,-745,910,-380,-54,90,297,323,-207,-130,-775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "close():void",
            new int[]{-116,674,623,329,312,115,-859,37,-44,214,186,1000,-627,664,307,414,-928,-425,391,-571,749,-818,-181,-86,-1000,1000,-84,-201,396,-332,-632,197,-171,-1000,1000,-976,-726,-315,-962,14,-181,487,1000,1000,1000,480,-1000,652,1000,1000,-1000,-504,991,552,-1000,469,-193,826,-1000,799,-574,1000,-437,470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "close():void",
            new int[]{1000,226,131,-807,-1000,-863,-1000,-1000,873,-666,1000,-585,-585,-1000,-766,-385,-281,-736,681,1000,-237,364,1000,485,126,894,41,1000,-1000,-594,88,-226,-371,286,-498,1000,-1000,-773,188,1000,-439,-892,-67,-1000,-1000,-787,586,104,-541,-1000,266,-64,-779,83,569,-1000,-784,166,1000,-1000,-1000,-1000,138,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "configure(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature,boolean):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-1000,447,-537,294,480,953,698,1000,-366,-31,-319,1000,-626,-955,-1000,20,-181,-659,-532,1000,-268,214,1000,1000,1000,298,1000,1000,-1000,-946,-596,1000,-1000,-86,318,54,773,-392,1000,-294,-519,-620,-606,-460,-1000,-226,-711,-345,1000,912,334,159,-529,351,-348,-628,-102,1000,574,1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "configure(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature,boolean):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{-1000,722,-833,298,607,1000,816,288,-636,488,-430,993,-182,-324,-823,287,507,-325,-300,749,-23,-400,1000,1000,1000,391,516,882,-1000,-133,462,-1000,-871,-203,-300,-614,-27,116,1000,-1000,590,-488,-1000,560,-1000,-174,-1000,-498,-400,-158,-969,400,-800,565,-1000,221,-662,-378,-434,105,-118,-128,1000,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "configure(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature,boolean):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{578,13,895,473,254,-219,-348,152,208,756,47,135,-1000,35,-350,639,297,648,-51,328,-456,753,-640,-285,-774,952,219,953,-38,245,275,185,-782,-328,-57,-540,-133,526,-1000,1000,-929,-300,-1000,221,124,247,576,-442,1000,-145,280,-359,-96,-597,1000,985,416,-106,102,-136,731,-640,-697,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "disable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{541,-720,479,-212,272,827,-330,702,619,657,429,-1000,-933,253,586,473,-1000,-438,20,-271,998,1000,61,-333,-289,-1000,-349,62,-296,1000,-898,1000,-170,-434,248,364,1000,-732,864,-1000,-5,249,-214,-1000,690,-435,-46,1000,-1000,-1000,375,1000,1000,409,-378,-293,-120,-12,-474,255,-1000,1000,703,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "disable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{898,941,-213,128,-792,551,-148,700,557,346,-44,-708,652,-658,-191,85,677,238,330,-655,-154,356,-322,-801,293,-863,-349,-703,-905,56,-837,-656,55,674,-420,897,687,35,821,773,-187,-448,-635,961,141,153,-450,-968,246,539,-106,673,-496,-882,542,235,-331,-566,425,-81,69,-299,-787,-393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "disable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{509,754,-505,-212,-1000,255,120,318,1000,812,564,371,-99,-120,-1000,-125,-1000,1000,496,-1000,-1000,178,38,-154,-289,-150,-349,654,-383,-917,-772,-1000,852,1000,-1000,-278,1000,-499,868,1000,-962,629,8,-1000,231,-731,-1000,-770,621,677,-137,449,-1000,-1000,-378,-737,217,-1000,1000,1000,-894,-1000,-403,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "enable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{978,-303,15,-981,80,936,600,115,581,234,575,409,496,265,352,636,403,-484,-509,741,-84,271,-677,-77,788,-762,-755,66,-657,-300,905,-722,681,-985,-155,16,424,342,-34,-964,661,499,618,318,-852,-939,-923,988,871,951,703,284,612,100,-825,653,288,-969,-158,153,-461,664,481,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "enable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{467,-1000,-145,289,-995,1000,56,915,42,-539,165,-1000,-324,-185,-1000,-765,-1000,1000,-294,1000,-188,-1000,-592,19,648,-301,126,-849,255,-1000,-503,1000,-645,-952,-904,-1000,-351,289,1000,-42,-112,1000,-978,-730,1000,-298,1000,-892,1000,-307,-1000,-1000,-243,-460,-1000,-141,1000,1000,-539,-288,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "enable(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser",
            new int[]{881,-500,-581,364,-671,-702,-233,967,-433,308,-19,-342,-851,-752,99,-1000,-437,602,-701,523,-675,-32,-495,1000,931,-460,695,-188,40,377,-503,332,727,-1000,-999,-29,268,93,-1000,205,942,497,-602,-650,-1000,-868,-46,-30,794,91,-510,-1000,-236,-385,918,48,302,789,-13,-276,133,251,1000,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBigIntegerValue():java.math.BigInteger",
            new int[]{418,78,-857,-256,-902,-610,-21,-857,114,715,813,-453,721,147,-985,-12,-756,510,329,-254,780,-602,-24,-393,258,3,861,-82,38,105,128,-743,-647,520,612,779,-215,-552,766,912,739,-324,-451,955,-640,-602,-541,369,756,507,-605,-948,507,697,-646,742,436,-605,-881,839,423,652,-978,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBigIntegerValue():java.math.BigInteger",
            new int[]{943,170,151,-631,-235,525,-341,825,-170,-620,-219,-779,-333,-756,936,691,-1000,-464,-755,-987,-685,747,680,437,784,-427,839,310,297,489,189,357,-544,-894,-164,754,306,-175,12,719,298,801,297,-753,527,-967,-666,-164,640,-481,276,-446,-24,853,549,-337,-529,-637,630,146,662,971,499,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBigIntegerValue():java.math.BigInteger",
            new int[]{-858,-971,-897,849,-815,829,858,-764,-247,539,5,-101,533,-559,17,-423,223,102,468,47,-614,728,222,6,-488,-883,423,-194,940,-199,-471,561,-596,898,872,-588,883,-671,-679,-652,-843,583,-510,854,22,-8,-361,-118,452,621,931,168,191,287,-984,-238,-788,988,63,-241,-726,67,297,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBinaryValue(com.fasterxml.jackson.core.Base64Variant):byte[]",
            new int[]{-1000,-408,-725,239,198,1000,-91,185,-1000,-619,-885,-1000,-1000,-1000,-1000,389,-67,1000,1000,1000,-309,-1000,-1000,-1000,-1000,265,1000,-1000,741,155,1000,1000,-1000,1000,1000,1000,-814,-1000,-598,1000,430,-183,-875,1000,1000,133,-414,212,1000,-1000,-1000,-1000,263,1000,-169,1000,521,-1000,329,-929,678,836,834,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:com.fasterxml.jackson.core.JsonParseException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBinaryValue(com.fasterxml.jackson.core.Base64Variant):byte[]",
            new int[]{1000,-139,435,-336,-305,49,428,89,687,273,-195,-157,700,-33,49,16,1000,-569,1000,-573,-426,55,841,-1000,320,1000,-648,-55,5,39,-313,-400,520,-1000,-993,-659,-177,1000,427,-1000,-586,599,-1000,581,-500,-84,418,-498,-822,716,-292,655,-77,-865,186,723,737,-185,72,-335,129,-547,-468,162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getBinaryValue(com.fasterxml.jackson.core.Base64Variant):byte[]",
            new int[]{321,211,451,-276,-1,801,-357,495,744,862,1000,-1000,176,-795,-1000,-577,-188,528,729,-921,-407,-312,-251,-1000,-997,-272,567,-44,145,47,93,168,-421,189,264,-349,-248,380,257,-174,-1000,-127,122,-821,-1000,543,-1000,-304,-5,335,-1000,764,313,-338,-589,76,424,-844,-1000,-467,-535,80,-895,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{46,-219,103,184,1000,569,222,410,-146,1000,-252,1000,104,-472,-937,-153,45,-20,-565,-377,-352,565,400,31,-64,-20,789,-952,191,382,860,764,-1000,-770,309,863,-1000,99,1000,-476,376,-340,-803,-932,-343,400,-532,730,1000,475,-66,551,953,624,873,-717,211,-104,413,-660,-1000,-1000,672,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{68,-28,943,-387,37,126,111,-1000,1000,-1000,609,-1000,-1000,591,343,735,884,-400,-397,-1000,1000,-871,232,-353,-362,1000,628,787,98,-267,-1000,123,363,700,984,79,1000,-349,22,-636,1000,-1000,516,1000,-475,-408,786,614,-930,-746,65,-174,-403,-1000,467,45,658,-710,1000,-161,134,1000,-716,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCodec():com.fasterxml.jackson.core.ObjectCodec",
            new int[]{569,208,139,-841,1000,575,75,-337,785,-819,558,-353,352,0,104,-396,572,18,27,69,597,-450,790,-843,-646,-3,215,38,-366,-146,111,653,-886,765,650,-480,-197,-610,-314,853,-67,490,-668,-376,-120,667,-824,593,-744,-76,-961,941,-1000,8,276,-766,1000,-143,649,-19,-1000,-352,-120,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-594,-711,-301,-486,-1000,-784,-10,-108,353,-1000,920,28,1000,-61,-980,-1000,-150,348,-1000,-1000,-356,-1000,799,455,372,121,-693,-221,-1000,610,-378,-650,-1000,326,-257,-930,1000,-421,631,-505,391,-33,-1000,-1000,-298,370,156,950,-345,79,953,601,-1000,471,495,277,138,1000,595,-1000,-900,230,-13,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-802,-796,-905,659,253,-508,363,847,71,844,-520,561,786,-264,-438,-161,765,-276,439,-347,-604,-807,-304,-572,-532,525,83,-644,746,-678,-468,520,-624,-83,-180,352,681,-702,376,721,-409,-225,-836,-886,-562,-733,681,-235,213,908,111,-654,-522,819,473,893,-908,362,192,13,664,983,-64,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-749,760,-133,899,931,-598,924,733,546,817,-116,-382,10,-270,337,-983,982,2,-69,-625,296,643,-912,889,-352,11,-145,318,-866,-444,642,-926,230,-954,-689,-678,-246,127,620,-362,4,275,397,-141,-202,715,-42,241,456,-257,294,732,-98,-996,309,267,-994,782,-975,-668,-945,-916,57,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{1000,-975,-597,-462,-943,1000,28,-104,1000,-432,-464,648,481,-106,754,-400,-38,-355,1000,-15,1000,1000,-1000,-266,295,400,339,-46,-741,400,1000,-400,-1000,611,-445,1000,-346,-379,440,897,-661,1000,799,1000,-400,9,835,-275,680,1000,-1000,342,580,-599,637,1000,539,-1000,26,-469,400,-975,1000,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{1000,-913,703,569,1000,599,-1000,1000,1000,-1000,-1000,1000,-1000,-282,-228,-431,-1000,-1000,-1000,-324,1000,938,-1000,-3,-1000,-847,-35,1000,-407,-453,1000,547,1000,-779,551,-400,1000,-462,522,-670,719,-1000,-581,954,1000,1000,65,639,-176,884,1000,-782,-794,1000,-1000,-670,1000,1000,-719,-921,-1000,83,60,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{-420,-968,781,613,551,1000,325,-1000,582,18,44,-839,933,-1000,745,-1000,-1000,1000,46,-302,614,1000,-507,-1000,1000,1000,1000,1000,921,1000,400,-1000,-1000,688,-72,1000,1000,-913,1000,-42,-847,-901,715,-337,453,1000,1000,-101,1000,-83,1000,-1000,-1000,-1000,521,144,1000,-245,-1000,737,-1000,-86,-202,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getCurrentName():java.lang.String",
            new int[]{318,-989,-93,353,649,288,-1000,620,1000,-1000,-285,-325,-408,-81,-92,1000,-477,-245,-1000,683,338,965,-1000,629,-940,-847,-623,550,672,-974,652,1000,396,931,551,-1000,1000,-1000,715,-1000,-57,-1000,-1000,-706,1000,1000,795,-521,-1000,-649,737,-1000,1000,-257,-1000,-670,1000,810,543,-520,-658,1000,619,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDecimalValue():java.math.BigDecimal",
            new int[]{506,-822,-301,-748,938,-517,56,-714,-134,913,-816,580,728,-793,925,412,-366,-437,-560,954,641,-352,-971,-111,-812,745,-836,-762,-881,-460,883,982,-559,80,194,-902,-279,-969,688,280,-128,-491,-855,-207,940,-492,628,647,504,189,-731,-361,-730,-802,-246,617,567,518,398,-167,-457,-604,-418,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDecimalValue():java.math.BigDecimal",
            new int[]{763,-1000,-609,769,-19,-912,132,-143,-808,-73,-1000,-1000,705,-767,811,783,16,1000,60,352,517,-495,-762,597,-1000,545,-402,210,-283,67,728,576,69,-28,132,-1000,-747,-1000,747,35,-1000,-1000,-1000,866,1000,-1000,256,1000,751,354,-1000,-49,-970,455,602,496,166,59,505,1000,-412,-946,-142,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDecimalValue():java.math.BigDecimal",
            new int[]{-88,145,-181,-382,397,-1000,-1000,177,972,-574,-455,938,-1000,164,-1000,210,271,160,-1000,-174,827,-1000,940,-1000,979,516,430,910,1000,785,-1000,-652,-864,620,-294,-891,22,-890,-489,-1000,-355,-3,-789,426,1000,-438,590,273,-647,283,-646,-1000,137,-999,-901,-232,-129,-39,448,898,-1000,477,-468,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDoubleValue():double",
            new int[]{-48,-816,283,118,-981,-368,820,-853,-1000,1000,-704,-1000,-810,-868,438,-1000,905,24,-107,-263,-593,-197,1000,-1000,38,-403,577,1000,-1000,-22,389,-566,1000,-196,-1000,186,409,-1000,-1000,-1000,-1000,-715,-1000,-734,-40,-535,-705,1000,-583,-595,966,1000,17,436,-574,700,1000,217,-122,221,987,275,434,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDoubleValue():double",
            new int[]{-622,-610,143,-781,1000,-1000,1000,595,-254,67,1000,305,-694,-244,998,1000,-400,-332,1000,1000,-1000,-59,1000,13,-654,230,-1000,-50,1000,-727,110,-1000,1000,951,573,189,1000,879,-444,612,-1000,1000,206,1000,-759,1000,610,-111,409,-588,-519,-44,1000,1000,702,1000,-162,72,678,-84,-448,221,902,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getDoubleValue():double",
            new int[]{-582,1000,-193,-382,-1000,139,978,30,-286,1000,183,286,-239,487,-1000,885,-1000,-917,1000,400,-82,356,-147,-151,-561,998,-835,-643,1000,1000,-220,90,1000,1000,-148,-230,1000,180,-596,1000,403,1000,150,1000,-1000,1000,1000,572,-1000,1000,1000,-46,-620,752,177,617,-47,-700,330,407,348,699,460,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getEmbeddedObject():java.lang.Object",
            new int[]{-918,225,-417,-246,-1000,-1000,-361,-356,-810,-573,-305,-28,-917,297,-39,-463,296,1000,583,-375,-883,448,392,-1000,-82,-353,1000,1000,845,-346,-135,477,-1000,-1000,98,-51,-532,-314,852,140,436,312,-682,9,249,-609,-646,811,-842,-869,-671,840,-1000,-111,358,-785,737,1000,1000,-99,-361,-542,55,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getEmbeddedObject():java.lang.Object",
            new int[]{-369,470,-237,-482,252,1000,-361,-1000,-843,406,1000,871,380,-1000,-927,-271,-476,1000,-372,-141,-883,1000,1000,77,58,-357,-237,-552,-29,-1000,-416,252,406,-721,-1000,-698,-210,1000,358,-990,436,-514,-236,-65,249,-318,-646,518,-842,920,596,-1000,-1000,-676,-633,1000,772,1000,1000,-99,203,-341,954,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getEmbeddedObject():java.lang.Object",
            new int[]{655,1000,-529,438,-330,-1000,-425,-1000,-1000,37,-957,944,-1000,-508,-413,-331,-363,1000,764,900,-1000,1000,1000,-1000,-118,-857,1000,1000,1000,69,-854,530,-1000,-1000,-648,541,-1000,413,1000,14,934,-835,1000,294,315,-609,-1000,1000,-1000,-365,-1000,1000,-1000,59,700,-569,737,1000,42,1000,-1000,-1000,-130,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFloatValue():float",
            new int[]{650,483,155,-577,468,903,-531,1000,1000,-913,-1000,978,-459,18,907,1000,-1000,-837,570,-337,-682,625,1000,95,-1000,-869,1000,-1000,-343,-155,1000,-61,1000,-263,-782,-1000,-331,-1000,499,-512,991,-825,-255,309,-559,-9,-1000,714,633,999,1000,-759,521,690,963,31,-1000,391,1000,1000,1000,-1000,307,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFloatValue():float",
            new int[]{316,-631,-217,64,89,102,-633,101,200,96,-190,-114,781,-52,-766,137,325,270,-627,-433,445,286,1000,47,-334,-146,913,114,-145,-572,1000,844,1000,620,-1000,-894,-888,-1000,1000,-244,333,-445,-476,288,-520,1000,-351,-1000,376,-126,-151,351,-372,868,430,107,376,575,912,982,531,294,1000,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFloatValue():float",
            new int[]{-401,1000,-77,558,-150,-712,1000,-1000,-127,-293,-1000,73,-482,-1000,1000,-559,-729,-246,-191,1000,616,825,-1000,1000,-664,335,-1000,-296,-609,-511,-1000,-565,-1000,-1000,946,1000,-239,745,-880,-610,1000,1000,-234,-1000,-31,-805,-32,1000,292,530,852,-840,581,-52,-440,-582,-341,243,-344,722,81,-836,-1000,124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFormatFeatures():int",
            new int[]{898,-318,-645,-111,-385,-1000,-140,237,1000,-244,222,750,231,737,1000,896,1000,464,-1000,-1000,-1000,1000,-161,-1000,1000,489,-933,-604,599,293,1000,-378,-361,551,-1000,537,1000,473,18,-1000,-469,1000,12,866,-177,905,-202,-1000,1000,-308,18,-1000,-1000,1000,202,-1000,1000,-458,-715,301,971,-31,-140,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFormatFeatures():int",
            new int[]{-279,245,255,-376,-544,-475,-272,-36,748,-375,652,64,-1000,294,487,404,-367,-175,1000,754,-254,-350,884,388,-340,611,160,-820,-1000,5,-237,-174,123,413,-40,437,1000,-1000,130,-863,1000,-756,-180,496,-378,747,56,173,-262,137,-492,466,-500,-234,-1000,-122,-48,33,-977,934,383,1000,-103,-210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getFormatFeatures():int",
            new int[]{205,4,-821,-131,1000,294,1000,-695,623,852,579,608,-470,1000,-641,666,661,-347,1000,118,1000,342,-1000,294,-540,276,1000,-264,-1000,-334,-173,-321,973,795,306,24,1000,-394,461,1000,1000,392,427,998,1000,829,-27,-1000,56,1000,-319,-408,-447,246,119,-736,-1000,-690,709,-237,-694,1000,-218,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getIntValue():int",
            new int[]{-261,255,-701,-181,1000,-94,113,-230,360,-541,709,-76,-911,-1000,469,-1000,320,-769,-70,-1000,537,-958,486,-481,-110,381,-1000,86,593,227,-1000,150,914,378,-1000,1000,1000,369,511,942,-6,1000,880,-57,-6,1000,-1000,-699,-272,-285,-682,477,-409,730,-825,-271,73,400,1000,1000,-414,-742,-512,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getIntValue():int",
            new int[]{-375,-1000,-149,309,-447,446,1000,-379,-1000,-1000,674,739,-1000,-320,-423,552,1000,-515,261,264,468,1000,1000,-364,-416,-726,155,188,-213,-18,-825,-1000,159,1000,-5,-805,545,-789,212,1000,161,-19,551,116,-3,1000,1000,-562,858,-819,79,954,623,145,570,-175,-377,1000,-165,1000,-1000,-317,-543,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getIntValue():int",
            new int[]{-545,-461,-669,-932,-839,533,-702,-342,906,-702,-956,9,185,-1000,-1000,-1000,514,-1000,-196,1000,-268,1000,407,839,14,596,-1000,14,912,-775,-140,523,366,-730,360,628,-998,-950,879,-334,630,-99,644,1000,-970,444,-991,202,288,285,17,-593,130,-466,-711,-551,-488,-1000,-1000,-1000,-253,-801,-111,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getLongValue():long",
            new int[]{-32,966,-225,-376,408,152,622,541,-883,1000,-635,982,1000,-92,-664,236,676,-1000,-347,-420,507,508,-766,-1000,993,-20,696,718,-158,-903,-1000,-822,15,799,-762,42,436,-111,480,356,105,-577,-803,526,-226,409,257,898,778,-226,-254,134,31,753,544,-46,706,-919,1000,1000,97,64,-864,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getLongValue():long",
            new int[]{222,-1000,-549,-326,-487,499,234,-246,134,-261,414,-1000,421,887,862,527,-674,1000,-627,254,334,472,886,16,-763,294,-463,-1000,1000,78,765,1000,-244,-156,103,-332,230,112,954,102,65,1000,666,-1000,95,204,595,-342,971,-1000,1000,477,-489,-189,791,-87,-907,457,97,-891,-1000,675,-754,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getLongValue():long",
            new int[]{377,-32,135,749,-792,75,-149,-574,1000,538,-391,-971,-836,646,852,520,-748,676,-553,-276,245,438,980,-26,1000,-194,338,-554,447,542,221,-336,578,196,1000,-103,-214,683,169,22,541,219,437,-630,261,-41,371,-483,593,-174,-632,124,-508,-396,751,-402,242,374,-508,-751,-444,380,-316,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberType():com.fasterxml.jackson.core.JsonParser$NumberType",
            new int[]{1000,-225,731,989,218,423,582,751,-270,-454,-1000,-640,715,949,-1000,-1000,871,-343,425,49,790,388,-892,-1000,763,1000,357,983,109,981,-105,1000,-1000,216,-318,-833,-198,-378,974,318,-1000,-73,954,-766,-1000,438,-652,-873,-1000,-1000,1000,-375,519,79,-1000,-388,-524,-614,824,-597,-654,385,804,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberType():com.fasterxml.jackson.core.JsonParser$NumberType",
            new int[]{671,-274,-197,989,1000,165,323,401,317,552,-880,-105,1000,714,-429,-953,484,1000,475,-213,809,1000,-1000,-1000,754,1000,794,804,-1000,594,682,767,-417,-281,228,-629,-192,229,852,995,-396,-251,-307,1000,446,796,-1000,-306,-595,-525,146,-1000,108,-652,-925,304,47,248,106,-121,-736,-336,877,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberType():com.fasterxml.jackson.core.JsonParser$NumberType",
            new int[]{1000,967,531,299,929,1000,-177,757,1000,818,-15,-864,304,1000,-172,-958,523,-647,-36,888,-191,1000,-578,128,781,-727,1000,-77,743,1000,-1000,1000,-1000,907,-782,741,-479,-587,495,434,-441,-4,1000,-824,799,492,-840,-487,-410,-941,146,-986,887,1000,559,900,-131,-175,-837,-404,128,844,1000,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberValue():java.lang.Number",
            new int[]{810,195,107,-67,-451,-884,890,1000,-255,-780,-856,-117,976,440,314,-1000,235,346,-438,572,491,-988,39,165,-657,618,-213,566,-176,-544,-400,-1000,-227,24,-457,-1000,-15,803,1000,-505,1000,20,-343,1000,378,1000,426,-220,3,-250,83,96,-537,-278,-178,-556,-502,442,105,24,1000,-256,882,629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberValue():java.lang.Number",
            new int[]{-1000,-124,539,603,198,-1000,-1000,-254,-143,-275,-259,724,-672,352,230,-645,308,-1000,-36,437,2,1000,533,-1000,663,-564,-124,-449,-527,-124,1000,-567,-884,-470,-252,588,-493,-648,-724,1000,-405,-192,1000,1000,-945,428,120,353,-1000,884,-455,906,1000,-1000,1000,-434,-640,-1000,785,402,-127,1000,-1000,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getNumberValue():java.lang.Number",
            new int[]{1000,-788,-397,-131,-553,-1000,1000,1000,-473,-469,1000,-1000,1000,1000,788,218,276,978,697,1000,1000,-1000,-1000,-387,196,1000,-582,1000,83,24,-1000,1000,-185,696,768,212,-894,1000,1000,-1000,1000,506,-346,1000,1000,-61,-156,52,122,-522,347,546,-1000,-153,-1000,762,175,-2,-207,487,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{-1000,-252,51,279,1000,949,534,146,-323,1000,-183,575,1000,1000,1000,-513,268,1000,-82,1000,-889,593,0,1000,1000,-1000,1000,1000,1000,74,-326,-852,-1000,262,-605,220,-1000,388,1000,521,1000,1000,-1000,-945,-718,548,-529,1000,1000,509,-1000,66,1000,-330,1000,384,1000,20,-85,954,639,-667,1000,452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{698,203,350,-541,-208,-1000,-1000,923,-23,-935,-486,-650,-442,-1000,-460,666,-1000,-400,355,-1000,996,-221,-1000,-540,-1000,610,-1000,-389,-400,592,456,1000,1000,-7,-173,-163,1000,-1000,-64,556,20,-956,673,992,858,1000,-676,-897,-1000,1000,528,-991,-1000,1000,-1000,333,-1000,-314,-457,-785,652,384,490,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{-1000,850,427,974,581,-221,1000,725,-1000,4,-579,829,348,1000,1000,748,-494,408,-1000,-116,-588,-909,808,1000,1000,628,1000,1000,1000,-330,-853,709,1000,282,476,1000,-134,183,162,-469,-417,1000,-881,-397,-68,267,-676,1000,669,1000,-589,-848,357,-18,-389,1000,400,-20,-893,1000,1000,-970,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getParsingContext():com.fasterxml.jackson.dataformat.xml.deser.XmlReadContext",
            new int[]{-1000,8,-185,-321,-204,671,-621,1000,-131,-867,-1000,-729,119,-759,-785,-441,433,341,522,-74,1000,880,-736,-147,-771,262,1000,-1000,358,1000,279,1000,1000,373,498,-712,1000,-1000,640,357,-398,-666,531,162,-620,817,1000,651,-237,-842,411,-1000,-170,578,-1000,241,1000,-274,-850,-514,-93,589,537,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:com.ctc.wstx.sr.ValidatingStreamReader", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getStaxReader():javax.xml.stream.XMLStreamReader",
            new int[]{1000,123,-133,444,-571,-550,-593,-744,80,-729,-480,25,-48,316,22,422,-1000,-1000,11,-304,-244,-225,-597,-515,-261,-376,-894,-750,-237,694,-188,71,-408,-550,124,-305,-388,650,15,218,-287,912,464,1000,-611,396,553,1000,-25,1000,1000,-696,-202,428,742,728,13,-403,-131,57,586,-589,996,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:com.ctc.wstx.sr.ValidatingStreamReader", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getStaxReader():javax.xml.stream.XMLStreamReader",
            new int[]{-1000,-1000,-785,-552,391,894,975,273,1000,210,569,189,735,99,-512,74,633,-135,362,-220,476,1000,53,270,1000,-379,989,422,478,-381,-1000,-368,-312,51,1000,-42,210,156,-566,-701,764,1000,-1000,-1000,-367,-161,-128,410,179,-943,-660,344,-35,-652,56,-1000,545,-377,-133,1000,-1000,44,409,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:com.ctc.wstx.sr.ValidatingStreamReader", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getStaxReader():javax.xml.stream.XMLStreamReader",
            new int[]{-635,847,707,-166,-559,-1000,876,599,-920,-648,-1000,746,1000,-1000,367,322,-1000,696,-1000,-996,-1000,396,-1000,636,1000,307,989,2,-184,666,-887,1000,1000,-661,-1000,1000,-562,802,1000,1000,-1000,-1000,1000,1000,-828,-124,705,-1000,1000,876,-704,320,832,-400,-1000,1000,-1000,585,-144,-1000,1000,-458,-639,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{542,444,-357,-281,144,1000,94,-1000,614,575,475,-827,1000,1000,450,183,616,-1000,-1000,-947,-718,221,1000,-62,-1000,628,568,109,489,-1000,-597,-1000,-772,782,-156,-1000,-630,-1000,492,-109,-1000,-400,132,46,-222,424,1000,539,-467,949,149,-41,-538,9,810,708,-476,-444,1000,-163,1000,313,1000,842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-198,-37,-257,-101,-642,1000,515,923,922,-1000,11,-1000,-1000,180,600,1000,78,-353,-696,-614,-867,468,791,78,1000,-633,-172,-593,-486,194,-1000,679,677,685,709,1000,-10,163,73,-574,-275,225,-1000,299,418,144,220,-1000,778,547,342,-1000,-199,277,1000,461,605,-975,443,1000,-246,642,1000,-178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-88,-734,-670,-472,-222,162,-206,-557,713,-455,-539,-692,181,212,549,-814,164,633,522,-225,670,-383,288,575,-888,-225,440,230,-58,412,902,101,805,-175,-697,744,-35,-596,893,-301,785,-547,-113,-268,637,-858,755,-461,-226,669,141,-225,423,344,-125,258,-363,-941,846,-803,-715,-584,368,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-234,1000,667,489,-1000,1000,-1000,-1000,-995,810,271,-906,514,1000,1000,38,1000,-1000,-896,-656,-507,416,1000,649,812,-808,1000,-293,626,-249,185,-23,659,1000,813,-671,-881,27,178,69,-1000,103,1000,-1000,921,85,1000,784,1000,891,227,-138,-763,-655,946,-418,-842,-1000,-736,-874,566,787,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.String:fQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getText():java.lang.String",
            new int[]{-854,-1000,50,789,-192,-1000,-1000,588,790,-515,-431,400,-1000,-1000,-400,-11,17,-598,1000,1000,-138,400,-471,257,787,-1000,-682,385,259,-362,-356,1000,-933,-225,-986,-170,862,789,-1000,784,-898,234,-170,-223,-1000,889,-796,237,-1000,-378,-568,-132,572,-692,-400,230,1000,1000,-1000,202,-1000,169,-5,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{-1000,-228,-293,103,997,90,33,85,872,1000,-807,-1000,573,1000,-553,-534,-107,1000,-615,-851,-561,678,69,1000,-1000,1000,-552,-346,188,-6,1000,622,-1000,-741,764,395,1000,245,849,665,436,133,239,-1000,632,1000,761,-685,-321,-1000,-1000,137,261,-1000,-1000,781,53,637,-226,697,-332,-186,1000,306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{400,-760,-737,-871,-250,-1000,-1000,281,481,791,1000,-2,-525,64,1000,-397,-805,1000,-846,195,-1000,-100,68,604,-1000,-576,-630,105,-1000,1000,693,1000,-1000,-287,796,988,126,820,498,646,364,-1000,-506,510,944,448,1000,335,679,-1000,1000,1000,-1000,-1000,-831,1000,161,432,1000,1000,656,1000,410,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("ARRAY:[C:5:24:java.lang.Character:dg==:24:java.lang.Character:YQ==:24:java.lang.Character:bA==:24:java.lang.Character:dQ==:24:java.lang.Character:ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{-1000,1000,454,-502,786,-372,-1000,-21,704,74,-1000,-474,-455,1000,-813,713,1000,-1000,1000,-1000,409,1000,-796,1000,1000,-966,980,1000,908,-735,293,-866,382,-1000,228,-983,491,193,1000,1000,1000,599,-164,153,-513,634,-333,-737,-1000,26,-159,-609,41,1000,-566,-747,-86,606,-1000,-910,329,-791,223,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:NA==:24:java.lang.Character:Mg==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{-771,1000,507,-76,402,-362,-1000,-526,1000,-610,-1000,-1000,-455,1000,-1000,332,1000,-1000,1000,-1000,1000,1000,-1000,1000,428,-1000,1000,1000,1000,-1000,-77,-1000,647,-1000,-1000,-983,52,-811,1000,1000,1000,1000,-809,53,-1000,1000,371,-1000,-1000,560,-1000,-609,782,239,-166,-1000,-493,404,-1000,377,107,90,-30,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:fQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextCharacters():char[]",
            new int[]{867,-196,454,-852,-1000,-212,1000,-250,672,882,1000,-37,6,-163,437,417,-1000,660,377,-681,-517,-147,-406,-560,-1000,1000,-1000,-1000,-719,1000,1000,1000,-1000,-1000,-625,274,810,-235,-1000,-570,179,-1000,-793,-254,1000,-352,-385,858,1000,-1000,188,888,-711,-744,-1000,1000,-120,726,1000,297,56,-770,-201,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{-1000,93,-53,-562,-292,1000,472,237,-982,-249,-36,-758,-419,-711,1000,-559,1000,-371,-254,400,-373,604,285,-493,671,1000,-895,1000,-486,-261,758,20,-491,-1000,1000,-127,-1000,-372,-445,-1000,-1000,1000,1000,-57,-174,91,-322,-1000,-325,-436,-784,1000,-1000,549,-817,-33,-1000,-1000,7,-719,544,927,-671,-9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{1000,-187,403,-797,-487,-173,-72,27,205,-813,317,480,-275,-575,-168,1000,7,654,-542,306,1000,143,-560,-805,-370,751,-1000,1000,782,-331,1000,571,136,118,-761,-417,-514,789,493,109,919,-1000,-529,328,-64,-434,1000,-1000,-555,723,-279,-468,-196,-1000,-743,796,-1000,230,-212,170,-636,-82,597,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{-1000,-341,-270,33,-1000,1000,-1000,-138,-1000,-365,30,-893,-1000,720,-147,632,-754,-659,-1000,-1000,-1000,-475,-1000,-78,549,-499,-1000,1000,563,-121,1000,-1000,-991,-1000,-508,-5,148,1000,-1000,-1000,-580,1000,563,861,1000,-462,-836,1000,-1000,-1000,-187,-628,-1000,1000,-1000,427,-1000,-1000,1000,-1000,-468,-817,-1000,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{736,-1000,938,-596,570,938,1000,-983,143,-289,894,-1000,-765,-1000,-786,1000,-497,387,-1000,-1000,-1000,-557,-1000,-1000,1000,-310,94,-209,-131,-997,807,1000,1000,-960,-90,-1000,-501,-1000,-1000,-516,-69,440,477,861,-129,-1000,-629,-23,385,669,1000,201,-1000,-1000,-1000,-1000,-284,811,858,56,1000,528,-1000,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextLength():int",
            new int[]{-716,316,-145,-381,667,-972,189,-482,1000,-193,-157,-616,-1000,826,-1000,539,-621,990,228,218,325,-113,-953,-1000,1000,-1000,-156,-1000,338,218,-1000,1000,903,1000,-297,-855,1000,-1000,-1000,42,-696,-587,-890,1000,-441,-896,-1000,-32,-1000,404,1000,230,1000,762,96,-1000,1000,1000,-496,1000,214,-210,-575,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextOffset():int",
            new int[]{-814,855,139,-747,-769,-461,-61,577,984,402,-429,478,632,-329,340,-89,-79,514,223,442,-353,-388,562,434,-73,288,553,-587,742,966,-645,-344,-178,-234,-765,-706,992,-939,261,563,805,-75,-239,228,453,-387,17,648,-205,-591,-516,-508,616,-500,173,-330,-797,-111,816,695,-247,-210,-99,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextOffset():int",
            new int[]{526,104,579,243,-570,-1000,301,-1000,1000,-16,-1000,507,-625,1000,-137,-1000,-120,-1000,-168,284,-539,5,-512,-1000,1000,-994,-1000,-1000,-459,584,953,553,-177,616,-214,258,-1000,-1000,142,404,-1000,-1000,100,976,-1000,1000,242,-408,1000,28,1000,1000,566,-14,-399,-181,1000,-251,-1000,-55,-1000,-613,-887,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTextOffset():int",
            new int[]{-1000,-680,-777,-622,-535,472,-421,1000,189,936,-247,814,763,-54,37,-560,-453,1000,-1000,1000,993,566,1000,1000,989,516,1000,189,1000,-343,-865,-237,-1000,-362,-663,67,354,-167,252,997,-526,533,1000,-1000,-388,-412,420,1000,-936,302,75,-299,-786,-1000,514,-572,-625,463,745,725,-488,-1000,353,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTokenLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{445,-294,-309,628,115,410,344,884,-462,-629,532,230,-244,703,-155,-897,860,131,875,77,-52,331,42,-719,618,967,-811,-475,-907,411,-158,-387,-612,-69,26,-959,-910,-711,310,-653,219,-834,-652,747,462,600,-372,-630,579,700,-674,401,230,-682,256,-250,600,-931,703,-521,278,-912,-352,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTokenLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-590,-322,-181,-626,-1000,-791,-328,-744,-65,1000,-985,234,75,-654,733,1000,-175,110,-445,11,-228,-321,-1000,-593,-437,-1000,734,-603,549,-1000,-16,887,150,312,-371,784,701,845,194,1000,533,371,948,-1000,782,-277,826,416,1000,-1000,351,-1000,204,884,660,-518,176,114,-1000,708,334,771,-199,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.JsonLocation", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getTokenLocation():com.fasterxml.jackson.core.JsonLocation",
            new int[]{-631,-662,-997,-541,161,338,-704,-516,969,15,-860,313,-282,-309,12,-1000,1000,-377,618,25,508,1000,-469,205,812,1000,-354,-1000,-953,240,-911,-1000,-612,-1000,414,-534,-517,-142,-37,-137,303,566,-137,795,599,867,1000,-288,1000,-550,-1000,-393,1000,546,596,-725,1000,-498,60,-35,585,-1000,892,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-1000,-603,-433,-316,956,-31,411,-341,81,1000,689,576,-221,-266,1000,-356,-98,670,-644,-1000,-1000,606,931,-154,-1000,-859,173,952,259,745,-1000,-770,-144,-1000,-1000,316,-948,-318,1000,-905,-136,696,-922,6,-1000,52,-1000,10,-372,830,544,90,1000,-99,398,-207,367,-1000,418,-27,-136,88,949,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-122,-1000,727,817,-78,1000,299,-106,226,26,934,-98,422,191,-152,-663,-295,775,221,-80,-588,-428,439,-306,-1000,-250,-847,-50,-982,270,-1000,82,15,-1000,-342,375,-476,517,-199,36,10,-410,752,-106,-74,910,-1000,131,-965,158,322,-1000,1000,360,592,-165,-697,-691,217,616,940,88,802,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-566,-887,-418,14,-541,131,233,-438,281,491,844,826,32,-450,-23,301,-128,846,744,-763,-411,-401,-324,-682,-967,-573,-924,-500,-239,-558,-668,-30,450,-876,-917,964,-125,197,690,541,523,95,14,298,323,567,-841,-505,-659,-559,542,97,813,-328,416,-185,-573,-621,-195,-585,376,-195,698,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-661,-1000,-34,-986,678,-985,-376,-652,1000,-64,-125,1000,289,-283,313,833,-1000,724,595,-351,-1000,1000,530,375,-1000,1000,751,650,882,-606,-855,-266,653,-218,-1000,-259,-496,1000,-409,-1000,1000,-451,-935,-546,-256,-254,100,1000,-383,-147,1000,1000,502,-1000,-1000,842,1000,-696,523,-1000,-145,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{1000,1000,783,139,528,988,819,-274,-562,49,246,-956,444,895,377,-593,-733,-105,-1000,888,-1000,-321,250,719,-231,-1000,-324,1000,-1000,1000,-207,-259,-1000,-654,1000,-749,-398,-722,53,355,248,142,462,-83,-620,1000,-206,175,-583,816,-253,-927,612,570,500,-304,72,-645,1000,843,396,-382,-1000,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString():java.lang.String",
            new int[]{-1000,-73,-75,-771,-737,-1000,81,-870,-299,1000,-115,487,-878,-105,623,-108,-740,-176,-802,273,-680,1000,-51,180,-472,-219,-885,1000,1000,1000,-1000,-1000,-1000,-909,75,297,-1000,-685,1000,865,388,1000,-1000,497,-1000,683,-1000,109,-122,-141,248,-178,690,-714,602,228,1000,-1000,1000,-1,-880,-129,242,857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{365,372,-217,-271,13,-456,484,-97,564,-73,-538,472,-250,-600,-806,-909,-757,368,20,-48,-387,-41,-491,187,-650,-988,-236,-236,-543,-452,-70,64,-476,174,584,-220,-63,190,435,604,-883,-467,-788,-20,-851,295,43,-534,433,690,-924,397,-129,136,10,608,-407,-584,-594,231,-476,79,364,-234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{1000,785,195,-871,108,-62,586,25,-187,853,-1000,894,-205,-560,-1000,-473,802,823,1000,510,-315,208,-1000,-380,1000,-210,125,-1000,-289,877,766,-1000,-39,1000,-1000,-393,113,639,190,1000,-1000,652,1000,362,-269,-966,1000,-294,-911,263,-652,904,-537,86,-111,1000,413,-1000,615,572,-67,-1000,-734,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.String:dmFsdWU=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{-597,1000,-362,-631,377,-681,-78,930,448,-964,984,-90,-1000,199,500,102,-242,168,-472,493,-748,-151,68,1000,635,212,1000,-470,162,590,868,282,-1000,352,1000,-26,348,143,417,400,-798,1000,248,146,390,-523,-16,1000,-115,-956,-1000,469,213,-172,-520,1000,-461,-575,-1000,-182,-1000,364,265,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:NTMxZS0zNTE=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{-846,359,366,-466,403,91,559,96,618,1000,349,-370,838,531,156,-351,-902,-795,-386,57,-416,114,-688,550,312,-392,-700,506,659,-61,330,474,302,-806,142,-744,277,607,155,-99,559,1000,-730,-619,1000,319,142,-730,-742,-1000,1000,-350,592,144,-682,-28,1000,100,979,-59,315,-508,-594,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{274,-23,847,-466,686,-898,-419,96,713,-416,1000,-266,-1000,665,484,1000,-656,26,979,452,-416,114,771,1000,1000,581,1000,-831,80,-61,330,-389,-1000,883,-62,-870,400,-1000,864,204,559,-160,-730,-203,-42,-647,-529,1000,-315,167,-1000,1000,-1000,144,395,1000,-916,-951,-1000,-458,148,1000,-594,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "getValueAsString(java.lang.String):java.lang.String",
            new int[]{-1000,-261,-527,-651,949,198,92,-711,789,1000,299,422,1000,-610,779,-829,-716,-838,300,285,-430,-479,-1000,1000,491,-870,-706,1000,95,-553,-1000,610,651,-1000,-291,617,-58,459,-831,-866,1000,579,-1000,-108,1000,326,791,98,-820,-892,676,-1000,743,574,-716,-173,327,-618,985,-19,935,86,92,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "hasTextCharacters():boolean",
            new int[]{53,-450,479,-76,566,654,-912,-88,992,-360,-269,852,246,700,-349,-375,-385,-757,-456,94,489,-327,564,-238,301,204,335,799,283,931,-593,482,-150,-431,-562,-800,-606,336,635,451,-106,-209,-148,350,-1000,440,599,-176,578,-846,893,898,82,325,263,-484,-357,630,65,738,84,1000,-159,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "hasTextCharacters():boolean",
            new int[]{-1000,665,583,754,873,786,-1000,-1000,1000,-1000,155,520,1000,697,414,-102,-1000,-999,-20,17,-499,-54,343,-106,226,541,-31,1000,21,640,-1000,723,-1000,-33,-725,-193,-407,-411,1000,451,-999,445,-344,757,-1000,-609,263,-80,1000,-16,471,1000,23,-455,1000,-1000,423,942,29,1000,258,152,39,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "hasTextCharacters():boolean",
            new int[]{74,-482,571,-707,125,604,-301,-101,212,198,63,94,-247,857,-670,-465,-7,-585,-141,-212,447,-46,449,124,-392,-86,1000,849,191,-16,381,447,-185,-263,-301,-838,-236,-332,687,924,223,715,15,240,-1000,767,63,854,-103,-732,-77,753,100,452,-414,500,376,1000,357,179,714,-676,803,829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isClosed():boolean",
            new int[]{-779,-624,19,693,990,1000,-799,1000,485,-975,706,698,-903,-498,1000,-1000,493,607,-1000,106,-304,1000,-931,-598,-1000,49,48,1000,633,659,-362,917,804,511,44,222,403,-513,219,-842,-278,782,1000,-372,1000,584,-1000,672,-380,-1000,746,1000,-851,150,402,598,1000,-354,35,689,-815,663,-242,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isClosed():boolean",
            new int[]{-109,749,615,583,889,255,-817,220,270,-623,-80,1000,141,-1000,-148,141,15,2,-618,141,358,1000,323,-659,54,234,-414,598,449,-496,-247,-657,449,558,966,-767,754,489,-800,215,-801,699,934,-394,524,771,405,-660,815,390,-294,455,-355,688,-334,125,-87,-679,389,-60,-405,1000,-552,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isClosed():boolean",
            new int[]{1000,1000,295,834,341,1000,-1000,-308,786,558,-236,1000,-1000,-373,-406,-766,144,1000,631,1000,1000,713,-304,173,787,-361,567,-246,1000,26,587,-689,-212,-602,-586,212,979,-677,-422,-429,-271,-446,-55,-282,538,769,-1000,-1000,-71,743,62,929,-1000,460,1000,597,360,-1000,-353,1000,38,445,1000,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isEnabled(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):boolean",
            new int[]{581,165,-369,-656,-310,-178,185,-218,-1000,254,-650,156,-130,800,-559,255,853,-98,-1000,250,278,-516,711,961,255,-161,720,634,-511,-181,534,-215,-1000,-725,-45,453,-245,-950,-869,24,-677,-1000,215,-1000,-757,-1000,74,-389,-553,-132,-786,-184,-798,951,-636,-1000,-1000,-796,-1000,-391,-397,303,526,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isEnabled(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):boolean",
            new int[]{-225,-1000,275,884,211,976,-87,-492,-216,599,156,-775,33,698,324,-58,158,788,473,-614,-412,-620,-655,131,598,-462,-991,483,-544,-193,-103,1000,1000,61,-1000,299,-682,1000,-1000,-49,893,281,164,492,251,1000,-624,210,1000,-276,1000,-20,78,-80,-32,957,431,514,453,1000,-1000,-295,-703,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isEnabled(com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser$Feature):boolean",
            new int[]{975,1000,627,154,-390,-553,1000,-417,336,681,-197,-721,-130,783,677,303,-649,104,44,-868,393,-252,-636,-339,335,-186,13,221,-334,1000,-325,-739,-175,52,170,-671,-668,-889,229,-873,-362,400,-466,-991,-1000,-816,197,-391,-1000,335,1000,-1000,-810,50,-916,-192,228,-923,-540,25,-234,594,499,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{-253,117,-281,89,-858,100,-1000,-531,29,194,97,-404,166,-183,200,-200,1000,149,-443,-529,296,-428,480,-190,492,-257,-21,114,-769,-313,396,-78,-255,-294,164,236,118,-669,367,484,-82,236,-996,95,141,-300,-265,-173,-1000,-405,-303,98,234,83,-1000,-479,215,545,-660,-28,328,-822,-211,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{998,-1000,-629,-202,547,455,629,-1000,-787,-1000,426,184,1000,-72,-812,-1000,1000,1000,-1000,1000,888,-746,58,1000,25,169,1000,1000,-582,-1000,1000,-1000,45,212,-248,-362,-1000,-1000,-320,1000,-1000,168,424,1000,1000,-7,817,-733,1000,-1000,237,25,1000,1000,-333,-651,895,1000,-1000,-781,1000,1000,1000,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{1000,-1000,49,-481,942,73,540,-489,-1000,-401,41,-173,298,-487,-459,373,959,893,-354,-1000,-607,-632,-45,-725,-86,-264,-585,1000,-773,-1000,1000,-1000,-433,-840,-619,918,-156,-1000,-75,-25,-1000,-559,-674,1000,151,-49,-31,296,407,-1000,-73,476,1000,923,551,-1000,1000,821,-517,-930,1000,-697,1000,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "isExpectedStartArrayToken():boolean",
            new int[]{-900,322,-745,34,600,-777,925,-519,-783,-148,407,-494,-269,-903,390,-361,378,45,164,-738,-469,-823,-71,787,335,980,50,-659,283,-345,-334,141,-984,-820,-479,689,741,-162,123,-783,-440,-487,424,-608,743,-452,103,170,529,-239,218,947,-642,-657,509,-370,647,-216,959,-884,-828,724,352,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{1000,873,-125,524,-1000,-1000,1000,908,-682,-1000,-649,1000,-1000,5,497,468,1000,-482,-383,1000,-1000,-1000,-721,13,-611,-1000,-820,-1000,577,-502,-1000,1000,1000,1000,-1000,1000,-1000,7,1000,-1000,1000,700,-1000,-664,1000,-293,-1000,53,1000,-268,1000,-466,937,951,1000,-1000,-1000,401,-1000,443,1000,130,-486,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-332,-886,-293,-318,234,247,116,-539,679,921,524,-211,-112,1000,-1000,-238,-1000,433,1000,-536,-56,0,730,-1000,-333,853,-927,777,-680,-179,-350,-780,-680,-957,970,-156,-217,-509,-126,834,-258,-56,-416,212,-356,-414,595,-383,129,1000,-1000,99,-369,144,-1000,-902,1000,-144,788,-1000,-1000,172,410,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{1000,-76,853,779,-1000,-613,-201,689,51,-326,1000,237,1000,-361,-990,993,934,81,109,-138,-1000,-631,-1000,1000,-573,-306,-276,275,-536,352,-1000,181,376,1000,-743,-611,-734,-1000,-872,933,475,561,-1000,238,-606,-64,-756,-132,66,-111,-391,454,30,-446,131,564,-790,1000,-1000,794,690,-434,-585,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{487,1000,835,259,-1000,683,929,-289,205,1000,-1000,613,1000,-1000,-564,-1000,1000,563,1000,802,-972,-445,831,-72,-1000,1000,-1000,552,89,-464,320,-374,42,-537,-687,806,622,-698,956,-472,-1000,558,-753,590,-213,-86,689,1000,-1000,1000,618,537,456,-9,-738,-12,544,347,963,-1000,391,-254,-981,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{1000,-954,600,349,343,-58,170,-188,-954,-897,-697,1000,-714,-343,93,-1000,-400,-865,488,-214,400,222,679,-32,-801,-139,145,400,885,-895,-34,237,-395,966,400,248,-1000,651,721,400,1,726,400,-1000,395,301,395,-128,-184,-1000,-336,355,-213,-791,154,-29,-130,2,-319,1000,1000,55,-429,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-389,-489,-162,-916,173,-73,-1000,-1000,110,558,-402,417,-673,137,-48,-1000,-733,637,1000,-1000,324,-627,1000,-1000,628,1000,-488,1000,298,-601,1000,-566,-354,-430,221,-1000,-889,936,77,129,-1000,44,611,476,-1000,702,-512,565,-1000,-229,1000,1000,-1000,-1000,-1000,1000,1000,663,703,-1000,-1000,-788,470,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.String:NDI=", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{782,-800,-2,-6,479,675,844,-964,-621,358,-1000,587,941,-1000,98,-802,-151,-33,776,-426,-382,-239,17,-181,-778,872,590,-68,-605,-69,-529,384,-385,651,945,1000,564,606,884,-12,-607,931,99,-1000,1000,-240,210,-813,-134,-406,-230,-446,910,-1000,368,366,-776,201,170,-1000,-131,819,-510,-680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextTextValue():java.lang.String",
            new int[]{-12,493,-567,-891,12,-162,261,565,795,554,572,-721,-555,-421,-45,59,538,375,-291,429,794,-425,-973,120,690,-786,-743,737,574,-46,-179,-731,899,885,-293,759,-702,-883,831,-284,-606,-308,39,571,-510,-896,-990,887,-434,141,-68,894,374,-344,-561,-586,864,410,20,-724,-370,-813,866,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonToken:FIELD_NAME", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{782,522,119,554,757,-1000,-660,126,-341,-1000,1000,-580,-603,-703,464,894,584,-504,-1000,-84,-73,794,-61,371,323,-546,-82,577,-728,408,-138,567,-766,215,-916,579,772,-1000,699,196,-587,-202,-532,-1,-38,-233,-1000,302,1000,-42,-911,-373,-1000,392,-81,441,-246,-661,110,839,-37,184,-280,-784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{-89,-1000,-666,-556,-511,671,332,-686,442,-1000,1000,-219,-1000,-848,544,-984,-432,645,1000,-1000,-1000,-911,701,-845,507,1000,240,134,-1000,1000,44,1000,-117,400,-940,-617,972,40,1000,1000,-1000,-1000,-1000,247,-109,1000,-1000,1000,-1000,1000,-812,21,-1000,-686,-869,613,1000,-154,-715,11,-82,70,676,381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("ENUM:com.fasterxml.jackson.core.JsonToken:FIELD_NAME", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "nextToken():com.fasterxml.jackson.core.JsonToken",
            new int[]{956,1000,311,-218,-78,89,-54,-550,-1000,-493,301,-567,92,34,-1000,673,40,1000,-182,214,825,-444,44,-740,-780,-264,-929,315,26,1000,783,-1000,512,-457,-937,-533,753,1000,383,-1000,904,-1000,-718,1000,-1000,44,93,614,81,-945,-916,-611,1000,-571,-542,-307,-245,-884,856,-315,-1000,-1000,198,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{322,-330,-913,-861,-678,701,965,131,-1000,1000,-590,-296,-960,-1000,-966,-110,-683,926,-746,-1000,766,-14,-1000,168,-236,-866,-412,83,-236,243,406,979,276,781,538,-69,-1000,-1000,-406,88,-916,20,-552,-493,38,-1000,-430,-549,605,-966,1000,1000,913,-38,651,-1000,458,-712,140,706,1000,680,-273,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{268,-7,683,23,-299,-1000,-383,-277,-1,976,-313,1000,-245,336,-211,-1000,-397,260,787,-1000,-388,-98,287,-716,-1000,-88,889,450,29,-743,975,640,996,-640,475,687,1000,-270,-771,-877,115,-130,119,240,-671,970,359,1000,-1000,-501,257,166,-603,899,408,355,926,-738,805,-885,-40,1000,-993,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{856,-347,287,904,288,1000,805,-419,392,-110,769,166,370,992,123,-1000,-691,1000,132,-1000,341,308,461,-366,-1000,1000,1000,776,140,-167,310,309,1000,-474,-181,702,1000,-598,191,980,-83,-1000,-922,91,-816,541,820,164,519,432,-773,-1000,-436,-36,1000,-796,1000,-532,1000,-79,443,1000,-181,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideCurrentName(java.lang.String):void",
            new int[]{375,-82,-951,203,894,842,-540,1000,-253,-1000,-204,-452,-371,-1000,-133,1000,1000,-1000,-1000,316,375,-64,-282,-699,1000,-1000,-1000,-1000,50,-16,-1000,954,-1000,1000,-1000,-1000,-1000,1000,870,-184,688,702,778,-229,1000,-462,-1000,-1000,-1000,-830,-339,-116,165,-1000,-1000,829,-535,1000,-1000,337,1000,-1000,-356,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonParser",
            new int[]{-612,-729,-833,-897,-166,301,-337,318,-1000,-667,-1000,-150,-95,-606,1000,334,-605,-874,-186,1000,-366,133,1000,-853,71,-193,-223,416,-1000,260,-206,406,197,-1000,1000,309,246,1000,1000,360,-470,676,1000,169,-191,180,-756,1000,-154,1000,-295,-843,1000,1000,-521,-364,-366,-902,16,264,458,-1000,546,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonParser",
            new int[]{1000,491,467,-251,-1000,-43,783,629,-758,1000,-275,-47,378,1000,149,1000,-747,-1000,1000,-603,-745,-1000,1000,1000,254,-1000,-1000,-855,-1000,-520,723,-36,-14,-660,968,-435,1000,-283,-294,-86,-1000,-390,984,-799,-275,-433,-758,915,1000,-9,485,176,-1000,-1000,233,474,-774,244,-930,464,-1000,-119,304,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "overrideFormatFeatures(int,int):com.fasterxml.jackson.core.JsonParser",
            new int[]{156,322,-373,-121,324,832,-25,-834,-563,33,638,-475,-430,410,618,-354,-973,474,664,331,-138,388,989,128,466,-607,-653,664,659,-17,863,727,736,186,416,181,-812,-539,87,-341,471,-207,208,-360,110,-673,884,993,-326,-442,-486,370,714,533,805,-75,623,-234,-972,-246,566,-338,-121,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "requiresCustomCodec():boolean",
            new int[]{-1000,-888,535,-642,-670,-1000,64,-462,1000,113,-707,-918,-43,-1000,710,-1000,504,381,-1000,1000,-1000,328,39,1000,743,256,954,-364,13,-421,-316,-690,0,399,729,1000,-1000,295,-312,-130,-1000,-421,789,-220,488,918,-630,-1000,-221,-607,342,-1000,-1000,262,-543,-975,-270,118,1000,292,-880,943,-386,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "requiresCustomCodec():boolean",
            new int[]{-653,-1000,167,-366,727,-727,396,-464,-352,-943,-1000,-621,600,-47,809,-1000,344,439,-1000,367,-1000,203,-66,1000,1000,-208,1000,-717,1000,681,1000,-1000,-782,415,20,1000,-1000,1000,-1000,-240,-434,-724,1000,560,1000,1000,-734,-1000,1000,-1000,1000,-1000,-343,1000,-1000,-933,-244,286,287,-776,-915,216,-925,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "requiresCustomCodec():boolean",
            new int[]{-625,-764,383,-642,569,-356,70,614,634,-408,-1000,-1000,340,-934,729,-1000,-175,-13,-915,1000,-916,427,-936,1000,743,-556,875,-814,-126,-371,12,-397,-273,828,307,1000,-216,-1000,-188,-26,-237,-511,1000,426,1000,849,-896,-840,806,-507,880,-1000,-1000,262,-875,-535,-184,526,697,65,-880,-190,-890,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setCodec(com.fasterxml.jackson.core.ObjectCodec):void",
            new int[]{-985,-786,-597,443,606,-415,350,175,-681,828,498,659,405,628,-241,-96,96,-62,-560,-6,83,-968,363,-324,-47,-308,-859,150,728,956,377,452,-831,653,-210,-398,984,260,-410,-904,-330,552,760,-772,372,-3,-653,273,823,833,-667,579,231,-577,-768,280,-228,-416,-876,-493,-725,354,-419,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setCodec(com.fasterxml.jackson.core.ObjectCodec):void",
            new int[]{-1000,-1000,139,-452,-259,-881,70,-102,661,-834,-1000,1000,74,-262,368,813,1000,336,-902,-1000,264,-1000,307,-532,-159,178,-1000,138,1000,-1000,-683,93,1000,1000,100,-33,224,-398,-1000,139,-855,-270,175,-445,311,604,165,-360,-805,461,64,-676,-525,917,-1000,512,-1000,-27,473,707,423,1000,235,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setCodec(com.fasterxml.jackson.core.ObjectCodec):void",
            new int[]{791,-335,-469,534,-862,-415,-57,-1000,650,-926,-900,-1000,-247,-1,594,-178,-750,301,820,271,-936,-487,1000,340,-764,377,503,23,-545,-800,-1000,-962,226,-1000,1000,114,-305,475,211,926,-1000,-869,950,488,294,187,-658,-821,780,231,828,-174,-15,-443,174,-1000,-464,21,-166,-647,597,-234,1000,-697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setXMLTextElementName(java.lang.String):void",
            new int[]{-716,-315,559,-443,-47,-780,138,857,470,-700,-270,911,-21,-278,855,-846,943,854,-473,-526,304,298,163,-572,-947,-817,147,-152,-965,945,590,-235,489,509,294,685,629,-626,494,830,355,261,-447,179,-875,-297,-539,888,933,236,-274,975,-619,-290,-956,32,901,-367,-998,194,331,-567,970,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setXMLTextElementName(java.lang.String):void",
            new int[]{-114,536,-853,-296,-649,-233,713,339,-47,92,-94,216,-822,416,-626,-152,-695,940,-278,591,596,614,88,-42,-667,34,-690,-691,-10,1000,460,-546,-451,-196,581,334,-788,-839,760,-270,-346,1000,612,-187,150,604,-390,-1000,745,722,-84,-118,318,-602,773,-810,150,1000,236,-284,258,-521,-888,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "setXMLTextElementName(java.lang.String):void",
            new int[]{348,-338,163,643,-264,350,-996,335,-1000,-1000,-1000,933,-588,1000,-295,-484,-1000,-155,886,221,520,-435,162,1000,913,-111,243,158,-956,1000,-197,-565,694,-447,107,-537,-386,774,756,89,-378,9,966,-768,1000,476,1000,-1000,719,1000,-882,-363,-21,-1000,-1000,-1000,91,-847,26,-858,-1000,69,-490,-102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "version():com.fasterxml.jackson.core.Version",
            new int[]{47,-267,-281,224,-1000,-186,482,174,739,750,11,194,721,1000,-334,424,447,204,331,-44,665,1000,-847,249,-438,1000,845,496,1000,343,-107,92,-155,-568,-1000,77,480,486,783,1000,767,1000,-294,1000,1000,1000,786,1000,-188,42,932,1000,-700,712,-766,-561,333,-772,1000,-103,-243,-1000,-231,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "version():com.fasterxml.jackson.core.Version",
            new int[]{-1000,791,747,-788,-205,1000,-86,-241,-268,-965,-286,1000,-262,-937,3,984,149,185,1000,-809,942,-460,762,-1000,-599,-889,-646,339,-1000,654,766,1000,-766,1000,-871,-1000,222,124,-468,1000,-548,-736,1000,805,-927,-1000,-251,-799,986,-1000,-1000,-364,1000,-76,1000,1000,-704,-1000,86,1000,-779,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:com.fasterxml.jackson.core.Version", DEReplay.run(
            "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser", "version():com.fasterxml.jackson.core.Version",
            new int[]{-497,352,-281,-51,-476,-487,470,647,636,797,-166,-467,-1000,816,-529,6,55,-160,288,-252,225,1000,-270,685,602,-167,-235,-1000,-518,-491,-302,-913,-988,403,-3,325,-232,-572,783,-268,-118,-276,495,-221,4,-431,183,-268,-1000,-825,442,1000,718,-155,1000,-665,613,-1000,313,1000,-1000,-1000,990,-1000}));
    }
}
