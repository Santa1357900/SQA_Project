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
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "absUrl(java.lang.String):java.lang.String",
            new int[]{-973,-28,325,190,-1000,-311,-217,-1000,484,-369,-1000,437,47,-936,1000,-233,-353,557,1000,745,45,-1,537,-1000,-27,697,-152,-730,419,352,-1000,694,347,-1000,-201,-446,-254,1000,-266,181,-27,263,-960,173,262,455,-235,369,130,-306,-728,492,-732,434,-211,-963,-1000,566,-1000,658,-755,579,285,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-322,-887,747,-1000,56,-131,59,437,972,655,-630,-241,1000,-1000,-138,336,-1000,-182,350,-273,14,-535,1000,-683,316,1000,-998,-1000,-169,-627,596,-207,-474,-488,-836,-56,891,174,433,-1000,1000,-437,641,-862,511,309,-228,-1000,-597,737,-783,655,400,916,-809,-1000,241,1000,-731,-416,1000,342,687,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-569,438,302,217,-357,628,-535,545,51,435,-1000,616,255,-71,693,-111,442,-425,170,-844,-291,-865,99,-477,519,249,-1000,-380,-33,-634,296,278,-341,1000,112,289,214,622,-773,320,-412,-478,-161,888,-964,-116,-516,103,-491,947,-611,932,-699,1000,757,-42,-455,-28,-922,-441,366,-570,580,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-586,-1000,-734,21,1000,994,-132,829,1000,1000,1000,-973,-492,663,804,-457,-1000,-74,-1000,393,799,-1000,-1000,-1000,1000,1000,-571,-146,1000,-76,819,231,881,-944,988,-1000,-647,56,-740,-119,-1000,755,802,1000,1000,-263,-21,-1000,1000,467,-1000,-550,120,1000,893,-1000,1000,-1000,-1000,1000,-458,1000,150,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-13,486,-461,-190,977,865,-610,612,627,-803,-593,859,560,-92,615,-352,917,-804,2,-972,927,679,876,143,20,133,-956,-741,-28,490,201,-191,536,905,418,963,489,419,609,753,-879,-517,-494,317,-217,-520,-216,-557,-671,-764,725,254,-966,484,75,-361,-139,-733,-381,-203,-134,-660,-624,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String):java.lang.String",
            new int[]{-641,129,1000,257,-486,984,-385,250,-136,-289,540,1000,-9,-679,25,-925,1000,-605,383,434,672,159,-759,-135,1000,617,-257,63,154,-603,-928,-127,-311,451,1000,-1000,22,238,-759,473,-107,1000,-58,-1000,-931,-419,-342,-579,1000,473,-344,740,-667,1000,201,-781,-895,277,329,798,1000,588,1000,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{406,-540,-1000,1000,65,1000,34,166,209,-257,421,-280,358,1000,46,768,1000,424,-932,223,-919,-438,1000,1000,-1000,-900,-37,341,-951,714,-12,500,-399,301,764,-980,504,281,-128,942,-1000,-1000,-268,-11,694,453,948,1000,861,505,-487,-733,614,-297,-1000,1000,-54,560,-410,1000,472,-179,79,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Comment", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{-408,-555,-20,-826,-930,569,-168,-196,659,-567,-571,-497,-626,-337,599,-55,856,-191,278,-263,911,-970,885,-110,-624,600,-201,232,214,460,-820,-332,330,421,-911,915,-983,-695,473,466,180,295,-60,922,514,-916,-541,-452,669,-981,-872,-66,-888,390,-31,973,-142,-778,352,-966,-463,524,95,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attributes():org.jsoup.nodes.Attributes",
            new int[]{642,541,-519,-24,906,667,-36,622,748,-927,1000,-463,-121,1000,576,-180,421,21,-807,-1000,716,219,434,658,861,-1000,-1000,-951,-232,-833,-853,-1000,127,657,470,1000,444,635,951,361,-1000,-463,387,757,830,-903,318,-370,967,660,-915,-777,-951,-375,41,629,-286,-673,-186,1000,-205,185,1000,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attributes():org.jsoup.nodes.Attributes",
            new int[]{523,389,411,-144,470,-138,-123,830,-85,-652,-780,-947,733,-743,-434,-638,292,-22,365,555,689,-161,-211,-74,-489,755,-877,992,-329,-934,-565,-795,452,-651,-136,406,-19,-727,559,-33,-916,26,-495,691,-291,-779,-742,793,-98,-221,962,-411,-271,809,916,-128,457,-812,-552,376,805,-783,873,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDA=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "baseUri():java.lang.String",
            new int[]{-778,-817,1000,-724,1000,736,-11,-1000,1000,-46,-1000,-1000,-768,1000,-941,-233,-209,334,1000,100,1000,834,1000,683,851,1000,-620,-279,479,-726,1000,-380,-121,1000,790,-136,-176,-1000,390,-1000,260,-1000,718,395,12,919,1000,139,601,-423,713,-1000,631,736,74,-843,983,353,-1000,1000,771,-466,-638,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:LTQ3Mw==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "baseUri():java.lang.String",
            new int[]{-625,655,235,647,755,-473,335,504,-950,65,-847,466,280,356,895,418,99,-840,801,-898,-825,-311,-469,169,-882,-723,-420,-459,-935,83,-886,367,-784,-7,-744,625,-886,819,-281,-85,-717,543,910,395,925,-84,149,-66,-164,988,-514,-440,-562,326,753,62,-131,608,-53,210,-536,-976,-185,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-690,175,-594,-197,342,-351,179,-1000,98,-1000,145,-829,-382,302,-300,-76,-1000,104,574,400,1000,-350,-1000,-428,-1000,1000,-681,-1000,1000,-1000,719,714,485,-622,-936,-214,518,180,-1000,-156,-668,1000,-580,-175,-225,-1000,-662,1000,-103,-264,785,771,-1000,-16,-19,-615,-565,215,-933,-1000,-113,368,0,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-721,896,-357,-474,297,212,-321,-552,634,505,210,-931,956,792,196,3,-275,349,372,701,598,-886,759,-845,454,-118,747,-978,520,-739,114,556,-571,-693,-968,-824,438,239,775,555,-426,731,649,-303,961,-146,474,623,246,-4,-173,-971,695,779,-746,535,-757,348,627,13,-365,262,-65,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-870,-344,-81,1000,86,677,-706,867,333,57,967,760,-596,964,-470,283,-178,-1000,-815,1000,91,248,-345,1000,-294,263,1000,774,703,-641,229,-1000,493,-1000,579,762,1000,-1000,34,1000,500,-872,-656,627,566,62,-2,-94,900,-418,1,437,-100,-1000,-182,-544,446,-89,-1000,163,-147,920,-86,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{547,8,652,606,146,-117,1000,957,481,1000,-991,569,652,1000,1000,-1000,-216,-467,-1000,-591,786,564,-1000,-1000,-1000,-114,895,-926,-1000,593,-146,-240,-266,-1000,435,-1000,1000,1000,-926,109,154,497,420,34,211,277,1000,595,404,-322,1000,103,1000,-1000,684,633,1000,-1000,-948,764,1000,-300,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNode(int):org.jsoup.nodes.Node",
            new int[]{-738,-1000,-1000,554,-1000,1000,-251,-835,1000,-325,-634,448,-288,678,-1000,530,-1000,339,-782,86,59,-961,-1000,-410,838,-789,-1000,189,701,1000,-1000,-196,815,-1000,-22,-1000,611,-712,-1000,-308,1000,-1000,1000,540,1000,164,-1000,1000,1000,-1000,-1000,168,-90,438,-151,-169,1000,780,598,569,-186,125,575,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNode(int):org.jsoup.nodes.Node",
            new int[]{-449,314,-621,-453,-1000,346,-174,-871,-692,-162,1000,1000,1000,-455,1000,-211,-772,73,-1000,1000,1000,1000,-359,1000,-376,-1000,-1000,-1000,-1000,1000,930,686,388,40,1000,-880,25,-1000,775,-1000,-481,319,-505,163,-246,1000,139,778,-903,1000,-776,604,1000,459,1000,1000,-690,1000,1000,-1000,-1000,-404,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodes():java.util.List",
            new int[]{446,113,870,-891,298,191,-446,-877,-309,-670,387,75,275,866,581,-593,227,786,117,-615,-641,-796,-249,-997,-470,-798,-948,-806,9,305,819,-263,16,403,355,658,915,747,-902,678,729,-176,-145,313,-444,-653,-475,-392,247,-592,590,164,264,-107,887,102,30,-316,235,932,286,-116,294,903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodes():java.util.List",
            new int[]{-977,-312,-23,-843,-355,786,732,595,926,817,788,-46,985,405,-427,-613,707,-977,-893,-91,-663,-662,-152,-397,-44,-154,768,-945,-528,790,464,-411,-701,-135,-731,-651,187,92,312,989,-167,353,252,794,823,-432,42,436,-775,259,464,-767,741,329,425,797,-321,-693,45,-776,228,997,-597,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "clone():org.jsoup.nodes.Node",
            new int[]{-934,-683,-270,520,-329,-576,604,-121,-646,448,-96,896,59,775,542,390,426,498,-674,-689,19,-411,637,-904,565,-133,259,511,776,95,-563,-558,-711,483,-602,682,-787,-530,852,-212,-167,-199,-659,815,145,543,-354,-120,-956,-978,978,-639,253,-609,-849,828,342,985,532,744,-405,-486,-786,-573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "clone():org.jsoup.nodes.Node",
            new int[]{-581,-542,298,196,-98,-782,128,842,-1000,320,394,795,1000,1000,536,-326,94,734,437,-541,-410,-599,230,-650,-10,441,-154,1000,-302,-1000,278,140,-208,153,38,1000,-237,-265,1000,-81,-275,-352,-753,-76,1000,-562,192,600,-1000,-652,680,52,-267,-709,-557,1000,-811,980,69,374,-637,-680,101,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{131,-798,-165,755,566,-277,575,-564,-354,112,-451,-348,384,195,-995,826,365,-539,-286,112,856,221,-44,61,901,-990,435,443,-52,852,-49,-792,-795,417,-183,676,201,-460,861,-675,836,107,-867,816,571,870,-610,286,-633,427,890,-668,-543,273,46,-388,-864,-383,-891,545,-658,-506,-812,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{-342,-850,-1000,78,1000,-557,753,939,-344,-938,-457,-437,867,-509,-354,-514,323,-943,102,-29,650,1000,-1000,1000,-913,-1000,-591,1000,200,140,400,1000,-699,-468,-618,153,-493,-1000,-1000,-1000,787,-1000,27,467,65,1000,246,343,-583,-1000,419,-1000,1000,489,-306,-5,134,-541,-165,390,-166,254,340,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "hasAttr(java.lang.String):boolean",
            new int[]{-122,508,586,7,-508,39,-1000,1000,843,-107,-530,-329,-483,-372,697,162,651,-386,476,-1000,1000,87,-1000,373,-141,-733,-973,-199,-699,72,-42,410,-298,-90,467,234,826,23,1000,-1000,1000,708,-637,-103,804,134,-1000,116,-1000,-188,453,-21,607,-271,73,65,-580,-277,-975,-1000,678,-484,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "hasAttr(java.lang.String):boolean",
            new int[]{287,-184,607,115,-704,-676,-477,287,-241,544,990,786,27,-232,792,797,550,421,849,-123,523,242,-727,-692,117,218,860,411,-458,-899,951,-954,-693,914,-630,-17,907,770,-348,665,-141,517,155,973,-474,279,-202,62,495,765,141,-940,399,401,-646,-500,-969,774,159,-306,-655,-316,-690,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{538,481,1000,178,-1000,-395,-607,-868,1000,-915,-630,421,43,308,54,-301,884,52,-1000,813,-445,-247,-289,1000,1000,-438,767,808,194,-1000,1000,-1000,1000,-588,1000,280,327,-1000,-718,-337,-1000,-188,207,541,-431,-1000,447,-78,-288,-550,-158,347,719,1000,437,11,680,67,-1000,497,-1000,-8,899,5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{279,676,72,441,1000,728,59,-862,138,-336,1000,158,-512,525,1000,-1000,1000,442,-981,-1000,-190,-851,0,1000,1000,325,-710,-505,-343,480,1000,-558,1000,714,-213,-317,1000,-730,1000,-807,-176,1000,-155,-322,1000,-336,-984,-1000,640,1000,-86,29,-1000,-286,825,833,-1000,-1000,-669,953,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:I2RvY3VtZW50", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nodeName():java.lang.String",
            new int[]{62,1000,409,964,406,-44,539,514,1000,502,-903,1000,365,916,919,160,-70,572,-268,341,-446,-928,-1000,189,-166,592,243,258,157,-6,-892,-397,-260,-175,962,259,648,955,856,387,-1000,-596,-305,-1000,-88,903,451,-1000,1000,-825,111,-1000,-616,-128,1000,240,-441,-471,398,588,691,-138,-1000,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:I2RvY3R5cGU=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nodeName():java.lang.String",
            new int[]{99,892,-795,-412,-63,980,-331,892,-350,270,-676,-396,720,-137,470,-438,-834,-864,-481,607,-204,-775,-643,962,727,812,907,380,-143,-785,832,-645,335,-717,578,295,-315,885,-497,-982,-746,854,-298,-805,887,-777,344,423,341,253,793,502,-154,347,631,202,-569,-738,790,278,167,220,-546,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:PCFET0NUWVBFIGh0bWwgUFVCTElDICI5NDkiICAtMTU4ICI+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{-265,-701,-233,656,-88,-157,949,740,90,158,821,332,68,-737,845,934,-578,209,-1000,758,-646,422,0,1000,-141,178,50,-755,-233,340,440,-174,829,-103,830,48,932,222,-21,502,-858,-775,886,-392,-348,-325,-1000,-640,1000,-67,-875,748,353,-80,209,912,415,154,-662,-159,-1000,-509,967,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:CjwhLS0weDgwMDAwMDAwMDAwMDAwMDAwMDAwLS0+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{936,-676,-232,245,-156,379,879,977,82,-798,-1000,733,924,641,-1000,1000,369,-637,130,-1000,-514,1000,26,-231,-167,680,295,-366,251,-631,-16,915,804,-1000,677,-50,-5,-291,822,793,-1000,433,-920,557,1000,-1000,-1000,153,1000,-869,-1000,-535,-84,380,1000,960,730,-1000,353,-427,-324,-861,-63,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{470,584,-1000,-330,-48,-1000,198,312,-351,-170,-1000,448,1000,-283,293,-469,-536,685,1000,-762,-1000,400,61,384,-648,-374,672,684,307,-255,-306,1000,685,143,-771,-192,-56,182,216,262,877,400,273,-56,1000,-685,40,-646,908,494,-306,280,326,361,-434,-122,-715,249,50,-539,1000,91,-506,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{-314,1000,-413,-307,-930,498,893,-757,-1000,-140,-592,317,825,400,-297,-139,-841,-533,265,-1000,-398,-724,-424,1000,-599,-719,-672,396,-59,-809,1000,-532,535,-907,-1000,-525,505,-1000,-23,-232,1000,226,981,631,-1000,-428,524,-1000,1000,-169,-664,522,1000,-583,-122,-335,701,693,-1000,224,981,970,1000,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{-373,-356,-242,1000,-103,-497,-1000,-102,60,-802,66,-118,10,884,215,-195,269,-833,-1000,669,-794,-1000,35,-177,-695,1000,482,-168,844,-680,420,-910,-468,173,-845,349,-377,544,-580,-959,1000,-273,109,956,-44,-448,439,-689,-136,190,-648,-551,-542,91,387,620,-125,936,-590,759,-281,216,-523,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parent():org.jsoup.nodes.Node",
            new int[]{447,-72,293,613,346,-314,-578,-692,-891,-482,-886,-828,544,744,647,482,733,-253,-129,-958,300,-412,775,-478,976,-348,869,-427,989,6,-811,-864,-434,937,-852,-966,421,235,683,-48,-543,-845,-802,-820,-850,749,650,159,-959,-764,782,753,637,468,9,464,-379,-855,-269,318,724,443,960,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parent():org.jsoup.nodes.Node",
            new int[]{-882,922,-1000,764,598,-713,-426,-745,220,-244,-223,447,299,-961,725,1000,1000,48,486,-458,1000,782,252,139,1000,1000,604,-1000,701,876,160,329,644,123,-1000,-163,240,1000,600,701,-342,630,-1000,915,-446,-532,-1000,-377,400,-119,563,772,317,-590,-586,1000,0,671,-821,405,42,-1000,635,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "previousSibling():org.jsoup.nodes.Node",
            new int[]{-542,483,-15,4,-34,514,-941,894,-383,992,391,-553,-396,-564,913,-793,607,445,-267,208,967,-413,782,753,-972,-45,967,-852,-799,-122,131,807,-550,-15,-77,788,-35,-161,397,-268,546,710,-404,426,-81,-298,690,771,-390,-606,-661,811,271,469,865,642,-554,342,538,532,614,752,841,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "previousSibling():org.jsoup.nodes.Node",
            new int[]{239,815,1000,-823,-270,-1000,321,-683,1000,-523,1000,-1000,-649,1000,-1000,-627,1000,318,1000,-162,-1000,-757,1000,-1000,-979,357,532,-1000,1000,1000,240,-952,-175,1000,-303,-1000,52,-604,1000,-355,-613,941,149,1000,1000,666,1000,-401,-1000,934,-1000,185,1000,-19,-508,-1000,1000,-475,917,-250,117,-375,1000,859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "remove():void",
            new int[]{2,-619,271,673,413,-65,-664,539,-1000,235,65,1000,72,64,1000,861,733,914,996,-700,-269,1000,1000,-366,1000,272,712,759,747,-83,-1000,476,41,-1000,-90,-326,-504,-1000,401,-900,144,65,5,-604,430,-1000,-566,1000,-1000,503,-1000,194,-110,-862,-181,826,1000,-1000,368,-829,-90,94,620,549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "remove():void",
            new int[]{403,-7,865,573,-560,529,1000,1000,-126,657,37,890,-216,666,734,659,853,1000,-887,1000,-713,-356,-363,564,198,1000,-656,37,-307,-404,1000,604,-80,-711,275,1000,-1000,292,-302,-1000,-427,733,-1000,-222,-1000,1000,-451,133,758,789,-406,-109,168,827,-1000,75,-592,-944,1000,423,-95,278,-513,-493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{362,1000,46,-915,537,-312,-35,316,868,83,-192,1000,-861,-1000,-736,-1000,293,118,-678,748,1000,-353,-1000,67,-1000,209,1000,193,1000,-205,-940,898,600,-193,1000,-63,-1000,-746,-1000,797,-1000,-556,750,825,1000,-1000,-1000,-576,148,680,-491,693,-29,-802,-1000,656,-69,519,-184,389,-187,302,815,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{699,979,-58,-915,-141,224,-760,-579,843,-555,-397,-149,539,542,-736,-247,636,-92,-458,255,932,-982,-707,383,-732,-502,834,-859,474,-451,393,390,723,-183,-291,-624,-740,96,-518,797,591,-476,31,410,910,385,-244,-633,793,-965,-246,693,10,-975,-683,621,-338,-943,-77,-148,-180,42,815,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "replaceWith(org.jsoup.nodes.Node):void",
            new int[]{202,-637,322,1000,-138,64,62,-1000,-131,-482,1000,705,-127,-1000,-672,1000,-235,-784,1000,665,289,427,491,295,-440,1000,-388,-86,299,-73,-655,-869,23,-315,1000,734,1000,371,706,-91,1000,1000,951,1000,-481,1000,-690,1000,-1000,-1000,-765,984,-789,253,629,297,469,-589,934,911,-795,190,659,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "replaceWith(org.jsoup.nodes.Node):void",
            new int[]{-405,329,1000,-1000,-228,77,472,649,279,1000,-1000,-45,1000,1000,701,-786,14,1000,159,-846,-141,884,-887,241,145,-1000,1000,546,1000,-1000,702,472,-1000,-442,-180,-268,-939,-1000,474,-166,42,955,-210,-895,555,275,-1000,-1000,-428,257,-474,586,-732,416,-1000,613,555,1000,-908,-176,1000,490,-324,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "setBaseUri(java.lang.String):void",
            new int[]{818,-572,-121,78,-923,-156,447,817,773,-763,-602,-343,922,981,102,972,140,-900,973,-721,-530,451,837,-747,431,48,890,107,-569,-834,756,-774,696,256,1,397,-936,-304,-574,-42,-835,921,404,-228,796,565,-404,-949,-394,930,492,-258,-368,-703,401,451,-350,374,-275,-327,843,546,-310,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "setBaseUri(java.lang.String):void",
            new int[]{-397,117,73,-394,766,-398,84,-126,582,201,-41,236,469,-364,493,230,-364,349,-600,-496,903,448,164,-279,-138,624,235,205,557,528,-326,-444,971,-531,217,931,-249,319,645,-345,-229,-6,-661,407,456,-994,949,-648,-935,229,-795,280,-427,-756,92,-940,198,-588,829,-466,-810,160,398,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingIndex():int",
            new int[]{-998,497,-1000,-338,-5,-176,233,538,-1000,-579,-706,200,-676,-1000,-169,-363,134,772,-791,-44,652,-802,-830,1000,-346,887,-538,-390,1000,445,-1000,-1000,-46,-157,-1000,-705,-111,1000,1000,270,60,652,-62,396,158,114,-728,-872,-663,-1000,1000,-198,539,958,189,226,1000,918,-61,-551,-502,-185,-881,-509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingIndex():int",
            new int[]{-813,480,159,-304,870,-264,51,-299,-814,-173,-425,-286,994,-852,97,815,-796,909,381,500,-984,40,-115,-564,789,10,-197,60,226,759,-670,-687,-512,519,-566,-561,22,638,548,348,-545,-335,14,303,720,983,-135,891,-275,-986,-416,-878,367,663,-157,309,438,-939,-77,-67,-924,-86,-512,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingNodes():java.util.List",
            new int[]{-405,-735,202,347,892,367,927,-242,-93,-904,882,-339,-780,866,-384,-871,-451,850,-365,973,68,799,-114,596,518,988,-139,-90,178,137,238,-823,-850,38,-957,532,-270,-42,295,-895,-350,437,761,-844,922,-981,33,-544,-346,-338,-520,255,-242,-911,781,-394,790,-798,738,-416,-746,809,-140,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingNodes():java.util.List",
            new int[]{-274,400,-235,182,264,154,-331,-281,413,-381,87,-829,-216,-526,-272,466,687,-315,-99,520,-222,-857,-346,-363,800,-26,485,33,-465,-837,122,709,-457,-898,974,744,542,-37,-221,287,313,356,699,-992,-56,658,647,-423,895,-398,-999,850,887,-761,-3,-103,-64,219,-884,-276,-10,505,522,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:PCFET0NUWVBFIGh0bWwgUFVCTElDICIzNzkiIDQwMmYiPg==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{-797,813,383,265,262,906,-88,165,-577,748,608,-976,995,379,971,601,-402,-796,-492,843,2,-258,-19,-887,227,-428,-144,-40,-869,947,360,-781,73,-124,-896,168,-450,424,934,-74,161,-504,-704,-693,730,426,-746,939,962,310,47,639,-375,225,43,-287,-80,-769,-167,-830,-559,342,274,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:CjwhLS1CX1NKZExWQWhcaHEuLS0+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{628,-601,430,322,-337,-537,-602,-432,-159,45,-626,473,696,107,17,-74,869,239,-78,-882,-201,-838,183,-955,145,-295,617,161,-91,164,283,-42,545,326,-521,-852,-305,373,79,612,594,-591,742,-707,727,379,-684,-177,-198,-184,585,-179,-228,1,-611,522,-727,-82,-881,690,-390,-345,-412,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{-938,571,301,-923,-671,50,-692,-347,945,-682,828,-396,508,-526,-557,670,457,-338,648,120,73,299,-850,-203,-943,-420,-460,347,135,-606,-66,145,-572,-838,-251,306,710,17,-578,-335,66,-709,598,618,986,309,61,345,447,-880,-471,53,170,6,320,-367,-103,-680,841,398,-713,572,-101,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{391,-1000,-248,479,-457,-546,1000,523,341,-703,-591,605,683,-42,-720,-708,-1000,-161,564,542,-479,-1000,-956,-253,-814,675,-576,-821,451,305,-732,461,-582,381,736,1000,-665,975,1000,-108,253,1000,-964,-996,666,-62,-682,933,988,257,413,-145,-412,-467,433,1000,159,1000,-163,-328,-719,510,-746,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{694,19,166,839,472,-547,473,532,-115,271,-80,663,-1000,414,-524,-1000,687,-613,-1000,-822,-501,1000,-108,766,39,909,-557,1000,894,1000,-833,217,-344,-1000,-294,1000,391,728,432,-891,-1000,855,242,-786,-946,86,279,-873,1000,1000,-1000,855,1000,90,-1000,410,-1000,761,-721,-1000,-979,-126,64,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:LTYyMmUzOTM=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{310,-622,233,393,695,86,-921,778,626,164,-235,-138,41,-689,-243,455,878,228,783,-500,963,124,828,-474,130,-523,-974,124,-958,-120,-174,568,964,427,-176,-301,-542,-977,-517,543,433,-755,572,790,-752,945,644,203,723,-352,82,318,-233,-23,-640,385,-879,-159,30,390,198,927,626,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:VGR4OENRIGFfN3M=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-803,-749,230,262,-87,510,530,292,-601,-871,-573,-700,-6,-206,-895,-1,818,-843,437,823,-953,278,-717,190,-80,383,677,-893,-208,-294,157,581,-533,-804,737,-197,-968,419,-785,-799,454,116,926,-915,344,581,-629,-140,406,-751,654,-476,-604,726,153,570,-333,-151,432,-746,-256,-681,-932,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "clone():org.jsoup.nodes.Document",
            new int[]{-340,946,607,-446,663,-220,-75,422,164,232,-942,-667,-63,121,589,283,-821,-226,-55,-122,-682,371,-48,-729,-144,609,-191,268,-749,-481,93,-81,-979,273,797,286,328,146,586,817,118,-632,487,-244,614,602,25,382,-632,952,482,-498,-568,29,370,-411,272,425,504,240,-26,-854,96,-792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{640,1000,520,267,481,-58,-996,-1000,926,341,-937,-10,-958,1000,-725,-1000,421,814,-273,-152,973,444,-610,404,1000,12,55,466,304,-869,428,-182,-592,342,-248,716,65,-456,188,-771,1000,1000,1000,1000,1000,-518,-24,1000,-219,-276,-342,-124,122,-102,-118,477,312,511,728,-921,1000,-267,-849,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{-834,-754,182,-1000,-705,-399,679,-3,766,486,1000,-377,-1000,-400,165,-159,-1000,922,915,-1000,399,825,-1000,-369,458,-1000,157,215,1000,-314,-719,928,-770,181,1000,-546,714,810,479,19,713,311,640,-856,-65,406,-313,-14,862,24,1000,1000,-240,1000,-767,1000,739,940,-1000,-417,768,-741,-509,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.TextNode", DEReplay.run(
            "org.jsoup.nodes.TextNode", "org.jsoup.nodes.TextNode", "splitText(int):org.jsoup.nodes.TextNode",
            new int[]{-343,-411,856,-700,193,935,253,-519,321,689,186,972,-32,-683,-90,194,824,-276,-531,843,-407,-980,334,14,-956,-283,-280,414,895,525,-789,650,478,385,944,-62,935,-874,10,-728,228,255,-32,81,-350,-237,-605,-300,-269,-455,-729,621,413,-917,-371,-603,-784,343,-578,-660,-251,505,-184,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{681,-747,41,2,717,-70,-175,656,-762,20,601,241,360,625,-625,-98,469,-385,645,583,786,282,-163,825,481,244,-403,198,696,441,349,-30,-336,417,-986,-145,733,716,-901,259,556,198,-584,-728,-6,-649,-325,896,-702,260,862,-573,72,-202,896,761,-809,810,-615,506,358,606,-512,-808}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{780,-277,796,113,-372,-447,-537,457,-518,-478,-982,860,201,637,-838,-985,-789,-79,-922,251,-749,648,-217,431,948,-370,-772,-441,933,834,953,214,741,742,-445,-744,568,-100,-47,-608,-875,825,484,-870,258,-892,263,-588,821,-74,891,786,348,404,-402,366,-525,-653,-765,-978,783,-332,106,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{-657,-705,-189,-769,-22,829,-802,389,185,-799,-33,-135,-636,870,19,-638,-343,448,-776,-973,643,-246,228,846,114,375,-381,479,-860,-396,131,866,90,-351,326,-494,-174,-327,35,469,917,-618,-302,-59,-636,-350,-257,357,103,-22,136,-950,-698,465,301,729,-621,-604,-208,-910,556,570,266,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-27,-510,-35,922,-391,-325,-222,-938,-807,693,573,834,-487,763,-757,877,-236,958,976,-531,-449,788,-595,-915,641,879,-772,523,-29,-317,469,-184,-36,-564,700,-852,322,893,-823,133,-815,759,-944,-27,773,225,664,273,-557,-516,120,543,484,-273,-469,-166,-96,729,-560,334,957,870,-759,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,331,-836,-920,-371,-537,-775,458,-462,624,-887,376,291,956,282,491,607,926,872,255,-865,79,981,-789,199,230,130,-494,93,-492,-664,934,32,-592,-275,695,798,563,402,-205,-668,-734,424,-540,-956,-22,-860,326,-567,997,-52,242,799,-703,510,-485,255,108,-110,-837,35,-751,-254,315}));
    }
}
