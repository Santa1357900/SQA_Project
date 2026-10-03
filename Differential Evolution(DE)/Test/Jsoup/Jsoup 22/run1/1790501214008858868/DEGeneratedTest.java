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
        org.junit.Assert.assertEquals("java.lang.String:LTM4Mg==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{371,-382,-973,855,-489,-740,34,381,78,453,-576,749,138,469,-932,76,-226,442,-890,385,714,-168,863,-224,996,-688,77,-516,85,-209,475,223,-503,518,-216,366,-458,706,-328,-85,109,997,978,957,517,-809,393,-605,466,-835,388,67,818,659,568,71,-447,703,-118,-887,616,857,-857,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:LTMzMS4zMTQ=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{517,331,449,314,489,-969,237,272,-565,954,238,-489,-481,18,16,826,-32,892,-414,-612,50,621,639,47,-588,-837,-356,352,-345,-885,234,-672,-606,-70,571,-725,-188,-223,-966,867,683,887,482,610,225,-325,-405,422,-228,-764,-638,-537,224,923,-366,688,-503,-390,867,584,806,-11,-352,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{-714,992,1000,580,1000,768,-197,813,660,-880,-815,-1000,-1000,1000,-754,1000,919,369,-867,548,-165,-1000,358,-1000,-324,-472,341,-62,204,989,-920,-587,-745,-200,-720,1000,103,8,282,-307,89,-438,-601,-400,-47,-553,-589,-581,467,-7,1000,-408,-1000,-611,322,298,410,77,-606,1000,-74,519,1000,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{927,423,-1000,-1000,1000,301,-851,-793,741,-268,-694,-149,-1000,1000,617,106,1000,-971,-306,-1000,69,171,610,-1000,-126,-1000,-268,-1000,532,1000,85,-669,-1000,1000,-428,879,-1000,-1000,-1000,-1000,1000,532,1000,300,379,-659,-1000,-752,-847,17,198,-215,-272,1000,-454,-759,-532,-442,-491,-1000,1000,-231,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "title():java.lang.String",
            new int[]{-150,462,757,698,-24,754,-686,-1000,-514,-129,857,936,501,478,-1000,965,-1000,347,667,1000,-26,1000,921,422,1000,117,348,-351,839,146,-129,-601,-817,-781,6,1000,1000,-3,397,598,1000,325,68,317,359,333,457,-732,-20,-1000,-545,-1000,1000,892,130,-68,-1000,-814,-220,-317,188,500,-144,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{129,-305,1000,2,-90,-201,56,-501,647,-1000,44,-165,853,-230,-549,-972,-1000,-61,401,-62,-143,-1000,913,-88,1000,692,-567,-1000,-1000,689,-759,1000,1000,1000,631,784,539,652,1000,-803,-659,-700,1000,604,476,832,-415,1000,-1000,-1000,964,-863,-1000,-687,677,-532,38,-550,650,305,155,-652,530,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-111,876,309,-773,-90,-909,171,-472,5,-290,236,-386,853,321,-784,-335,-68,-568,590,96,-657,-805,272,-1000,842,282,83,-741,-406,-393,-640,616,711,903,631,697,-145,652,744,-122,-659,-210,113,226,75,755,-222,280,-623,-48,918,-627,-696,-471,87,-605,-572,-550,8,421,181,-704,60,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-538,-231,-359,-26,-571,182,1000,-330,-415,1000,74,-1000,-955,-982,-709,1000,509,-694,-1000,-390,-780,106,-21,-620,-920,-831,-1000,-275,774,-124,-354,-228,-160,-1000,54,-314,-231,-1000,-612,577,616,364,-567,-1000,-528,-26,-795,-1000,1000,302,-1000,827,710,-355,661,1000,572,340,220,-148,-1000,-552,-1000,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{205,-1000,-291,521,-1000,-371,855,-42,1000,-424,-1000,1000,297,258,688,-510,-92,457,539,-143,147,-550,154,686,422,-334,-697,-1000,-239,182,904,-400,170,646,738,-755,-216,157,223,-488,-876,422,100,-302,297,-1000,1000,1000,164,371,706,-809,290,348,691,-573,694,-671,-25,832,-1000,-1000,1000,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-92,899,1000,-1000,175,-1000,929,-38,-129,301,44,-400,971,471,-400,-722,217,-61,322,-557,-386,162,267,-1000,1000,390,-481,-802,-211,-812,-400,400,400,404,970,-189,-269,-270,275,-551,-675,57,-97,-804,100,-188,1000,-25,372,944,516,-360,368,884,365,-1000,-235,-891,-789,724,457,-1000,-110,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{252,371,711,199,-965,-424,373,-38,549,-466,-1000,1000,-230,1000,792,-407,729,230,982,-210,-107,-181,-606,-955,226,580,-481,-802,-372,-1000,833,-612,400,404,1000,-977,71,-955,-974,55,-842,276,-870,7,-77,-188,821,1000,-725,454,1000,-228,1000,834,-262,-461,595,-755,-639,1000,-181,-1000,1000,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{364,-252,-26,1000,-1000,1000,1000,44,1000,-378,-1000,1000,-220,1000,1000,229,1000,-208,168,-15,-412,888,461,-889,-1000,505,213,-1000,-327,-1000,1000,-1000,-1000,-287,1000,-1000,-609,-887,-1000,-188,-1000,1000,-1000,-403,-1000,-1000,1000,709,931,454,275,-243,1000,409,-934,499,1000,-1000,-1000,1000,-1000,-935,686,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{-244,-918,1000,609,-24,458,-468,1000,1000,304,-729,-193,850,-225,-533,-564,1000,1000,-646,856,327,1000,-1000,0,417,227,281,-883,569,372,-911,1000,347,-568,169,675,689,-1000,1000,991,-194,-915,-803,-931,-1000,-1000,-377,84,-1000,1000,-1000,-352,-324,1000,118,1000,442,520,940,336,1000,-617,1000,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{-500,-296,-566,-405,-811,-159,-573,-680,138,739,398,260,898,1000,1000,-112,-153,-813,47,571,-1,392,395,819,817,-445,-1000,-787,1000,791,-550,14,1000,827,352,-494,15,-356,-478,-739,-147,-399,283,-308,552,-780,1000,-338,-1000,1000,-683,-216,1000,-193,1000,936,-220,599,55,580,1000,-1000,138,440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{847,67,-1000,-502,1000,460,-1000,-1000,-1000,46,-893,-9,-589,-1000,672,1000,296,633,101,504,357,-58,847,-1000,-196,976,927,1000,-608,-1000,1000,-1000,-827,823,-53,1000,272,1000,-1000,396,1000,1000,1000,369,1000,1000,34,390,1000,-320,1000,-955,-12,551,-284,-1000,-735,-937,1000,-1000,-768,-620,839,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{-873,-1000,353,929,34,-325,-641,-45,615,-678,801,-61,594,-956,872,238,885,-125,-1000,-104,-788,1000,-332,20,135,-21,-1000,-607,380,538,-1000,20,1000,308,77,-507,-290,-47,66,-1000,22,-1000,-980,-1000,379,-1000,1000,1000,-541,1000,87,-312,842,364,-559,94,-989,697,-275,1000,1000,621,-125,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{981,765,-336,538,88,500,-1000,453,-509,415,-852,-15,270,-1000,-577,869,340,-58,408,539,-280,1000,-147,-844,221,886,1000,172,-128,-280,351,331,-1000,361,-24,1000,942,-367,348,1000,762,490,1000,400,-203,528,-400,565,-231,357,-645,-1000,-928,1000,284,136,7,443,1000,-1000,632,163,975,-224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{55,3,-1000,-1000,524,-437,-787,-1000,473,-379,836,933,198,-276,1000,522,-458,-1000,-8,-472,-336,902,831,-589,-981,950,-412,-276,419,30,-1000,-1000,676,1000,-16,412,94,492,937,-284,512,171,-141,-681,1000,235,1000,1000,476,1000,472,-433,689,-47,-247,-30,-802,-179,802,-293,994,1000,-416,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "parents():org.jsoup.select.Elements",
            new int[]{774,-238,70,279,761,354,85,229,-1000,-46,-559,-824,-736,-1000,-811,420,704,378,407,607,-527,-69,740,-1000,-572,1000,1000,1000,-678,517,622,-1000,-1000,766,-422,100,-70,1000,655,633,1000,1000,1000,172,1000,-511,547,-23,1000,-429,1000,313,-571,237,-394,-1000,-1000,-1000,-211,-1000,-936,-578,-413,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{477,-1000,484,368,-1000,-311,553,929,219,-494,1000,813,694,881,1000,-291,1000,-133,977,36,-294,-304,-1000,-952,-151,-617,1000,662,-492,-281,-330,1000,844,-289,70,-1000,-437,-1000,-1000,647,-488,1000,505,556,-836,-1000,-281,1000,-1000,928,368,1000,77,-1000,-1000,-263,379,185,-110,-1000,-1000,-561,-1000,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{1000,487,757,348,-413,-120,1000,-821,-537,-608,675,663,-1000,157,975,627,215,194,963,-953,-340,-353,201,-1000,1000,-356,1000,-175,510,-273,-580,462,51,-1000,-1000,221,-814,-592,1000,217,-294,1000,612,980,1000,81,1000,-45,-118,1000,1000,657,1000,-1000,319,500,381,-1000,-1000,703,-705,561,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{1000,577,1000,-6,-70,-225,228,-91,-1000,824,598,-208,-1000,733,496,1000,-206,1000,-17,-358,-1000,-1000,847,-820,1000,-258,-1000,1000,13,-285,-413,-832,1000,-1000,-778,1000,1000,688,866,-20,-309,1000,1000,-736,1000,578,1000,-601,-5,305,-154,130,479,-1000,1000,896,-675,-575,-857,1000,157,717,-608,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{1000,-1000,149,565,-181,-696,635,118,303,-1000,689,370,-186,-248,388,-162,251,-204,-193,-1000,-520,-338,-1000,-1000,-226,-1000,992,-126,6,-441,-274,745,-535,-1000,-476,-899,-115,-1000,421,973,-1000,589,353,1000,358,-960,607,801,-397,515,1000,1000,97,-275,-146,1000,918,-630,432,-1000,-930,558,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{1000,577,1000,-6,-70,-225,228,-91,194,824,598,-208,-1000,733,496,1000,-206,1000,-17,-358,518,-1000,847,-820,1000,-258,-1000,1000,13,-285,-413,-110,1000,-1000,-778,1000,-849,688,866,-20,-309,1000,1000,-736,1000,578,1000,-601,-5,305,1000,130,479,-1000,1000,485,-675,-13,-857,1000,157,717,-608,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{197,-433,269,-3,-630,-386,299,-62,512,380,823,121,-873,-1000,-1000,-910,-669,470,-1000,-1000,-723,-241,-1000,618,-1000,-765,995,-33,340,-745,1000,506,-917,-1000,-1000,1000,991,-1000,-13,1000,-1000,14,763,394,421,240,1000,902,163,-557,1000,610,462,-195,-137,1000,1000,-1000,891,-1000,112,1000,157,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{325,-800,59,878,-891,-951,-529,1000,685,-599,823,-477,1000,824,1000,7,915,-80,1000,-425,202,-326,-880,-421,165,-415,-585,275,-709,-235,-1000,-313,478,862,1000,-1000,-377,270,424,136,-578,-171,-209,102,-828,-1000,-1000,-327,-752,-23,339,1000,-613,427,-281,352,-636,511,176,-1000,-341,-1000,-419,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{477,-1000,314,616,-1000,-237,1000,418,-293,-1000,1000,-353,694,-1000,1000,-552,1000,-697,1000,-380,-895,149,-1000,-1000,-151,-686,1000,-85,-144,-273,-447,1000,180,-289,-85,259,-413,-1000,-907,813,-478,1000,234,1000,-836,-1000,-281,1000,-1000,1000,368,-910,442,-1000,-1000,-253,1000,-506,328,-1000,-1000,-670,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "text():java.lang.String",
            new int[]{1000,-535,1000,974,503,-225,285,188,762,-400,380,-83,-769,140,-1000,1000,70,1000,378,-798,822,-1000,847,-1000,-664,-766,-1000,239,496,-157,-1000,-68,-176,-748,-380,-329,-940,247,1000,981,-691,991,214,-378,120,-242,725,-1000,335,531,1000,1000,460,107,766,879,-430,204,-562,938,-796,726,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{742,923,843,634,617,708,-77,59,973,60,824,-1000,-800,-554,-288,420,485,-836,-13,-404,48,376,402,1000,-220,-718,553,344,-283,-1000,-110,-1000,-1000,-4,174,244,-58,501,-850,-1000,-319,612,1000,845,1000,-656,394,93,-181,-485,650,-490,-446,-1000,1000,-440,992,1000,579,851,-752,-1000,-1000,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{-453,960,449,340,350,-465,490,894,503,-666,-770,-606,-1000,-977,-284,298,-638,-193,-345,-253,545,527,346,6,-539,-14,-78,1000,-46,334,-700,-1000,1000,658,-783,46,-95,817,-1000,1000,959,280,838,-617,-288,-1000,-1000,394,746,181,1000,53,885,-1000,1000,-1000,-268,686,927,1000,-53,1000,-651,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{371,905,449,982,24,-330,1000,894,491,166,151,1000,-954,-977,-284,900,-1000,-1000,-185,-253,1000,-352,-590,272,-527,-50,893,994,-346,-381,-1000,-1000,848,658,-62,46,-1000,-1000,-1000,832,959,-169,-252,1000,-664,-657,766,1000,55,1000,1000,292,1000,-1000,930,-1000,-268,582,-385,1000,261,-67,-1000,-946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{1000,-1000,414,403,-181,773,938,-1000,-747,656,287,1000,883,8,1000,-1000,1000,-169,-688,-1000,-268,116,-1000,277,1000,-1000,552,-1000,-1000,66,-313,1000,-705,-569,249,176,-782,-1000,-161,-1000,-1000,647,-1000,1000,123,421,1000,-1000,-1000,948,364,-1000,-293,1000,643,267,-359,-436,-1000,-166,-1000,273,807,-638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{971,-1000,769,644,222,-252,458,249,-297,-510,1000,1000,486,-444,801,-577,1000,-327,-672,-1000,472,260,-365,1000,1000,-696,-202,155,-1000,1000,-435,-381,-1000,-788,-176,-140,-259,-201,-1000,-400,-1000,1000,-580,752,-559,-413,1000,-677,-637,375,543,-1000,958,862,643,-1000,-773,115,255,1000,-798,-1000,190,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{-539,1000,-691,-788,-1000,889,644,-1000,445,-439,-1000,-174,-1000,-572,-10,-432,-1000,-211,-430,-281,-13,-150,-399,-83,-1000,-293,1000,-786,460,562,38,-368,181,1000,405,-72,-1000,-30,1000,365,254,-1000,-1000,321,156,1000,-952,1000,-196,1000,-351,1000,-818,-236,407,1000,1000,-46,-331,-793,1000,879,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{359,-310,1000,704,-359,789,1000,-713,121,-57,-569,1000,-392,-780,1000,-1000,-225,-501,-141,-1000,-512,1000,-1000,-545,-1000,-269,704,-446,-1000,1000,263,-159,-936,189,-1000,-1000,-1000,-1000,-553,189,31,504,-1000,826,-333,744,811,-367,-968,475,-26,-95,1000,1000,-90,-7,-251,-561,712,493,-858,1000,724,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "val():java.lang.String",
            new int[]{764,1000,719,188,561,-972,151,1000,-1000,469,718,-552,489,-199,-1000,303,250,-859,-280,-184,328,784,998,1000,155,-465,-1000,-228,77,-1000,-555,-522,-1000,-792,345,68,354,-5,-1000,-4,372,1000,341,-56,826,-1000,22,167,602,99,424,-342,233,733,788,-1000,-698,934,215,1000,-614,-827,-374,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$ContainsOwnText", "org.jsoup.select.Evaluator$ContainsOwnText", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{587,236,520,-491,89,852,832,-812,677,541,602,-527,-467,-957,590,-273,-802,-33,-339,-215,549,-741,-671,518,-40,674,-958,-120,-690,-673,14,-164,472,848,-999,918,662,462,-160,-145,-397,-620,-204,-290,-150,-685,-192,-738,-195,-385,578,995,979,-435,98,983,865,722,716,-783,429,818,-895,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$ContainsText", "org.jsoup.select.Evaluator$ContainsText", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{346,-780,743,-62,-517,969,172,606,666,830,372,894,441,540,211,445,-911,-859,-124,-786,101,713,-546,818,24,-644,-698,919,-834,-518,-750,439,883,-898,361,647,-277,-752,-86,-698,579,-62,-429,-67,179,147,252,62,742,139,-826,729,-575,-773,692,-974,317,502,-879,-492,158,439,563,-663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$IndexEquals", "org.jsoup.select.Evaluator$IndexEquals", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{-718,-964,654,145,736,-994,617,-538,-20,345,58,147,-620,-389,337,-50,694,329,798,155,-869,799,-731,-819,-206,708,314,-771,-119,830,-451,548,-131,-298,-630,859,-434,93,225,865,397,337,220,397,-412,-837,305,-302,998,-515,514,672,-702,870,839,-554,871,-192,183,554,414,-209,536,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$IndexGreaterThan", "org.jsoup.select.Evaluator$IndexGreaterThan", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{-518,27,-938,556,-776,585,-480,-693,240,-701,-838,-625,560,-255,-729,823,506,144,41,967,946,-230,-236,-17,-527,107,575,290,584,224,110,13,-994,-427,821,981,-490,640,-864,560,-213,686,-816,-78,-747,237,383,-715,20,-150,814,-874,684,259,-962,-618,-920,9,-246,287,685,-638,-457,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$IndexLessThan", "org.jsoup.select.Evaluator$IndexLessThan", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{431,-601,-240,263,-282,37,582,183,-696,108,-774,-282,-111,930,-160,666,-446,-641,-854,-617,-852,689,561,-168,-932,-450,860,-163,197,482,-407,-646,321,968,662,-414,-251,-910,-476,-974,47,-297,341,-804,-194,-969,-939,723,-806,-65,878,-321,659,759,-165,641,635,-387,426,815,308,-777,-49,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$Matches", "org.jsoup.select.Evaluator$Matches", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{-39,-493,612,-805,-497,213,-907,335,-356,-748,985,-681,-971,-389,721,87,-271,-184,153,697,-725,-897,915,-154,512,-595,435,-792,-684,831,-38,-747,574,175,-29,-6,940,-909,77,-868,833,82,-781,450,-83,201,-748,997,-151,452,-634,586,-523,-711,-260,-561,892,-686,732,-568,-305,-698,541,-671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Evaluator$MatchesOwn", "org.jsoup.select.Evaluator$MatchesOwn", "matches(org.jsoup.nodes.Element,org.jsoup.nodes.Element):boolean",
            new int[]{432,-910,970,479,579,884,-652,-188,104,-713,358,-488,-409,-251,-118,373,-73,567,-289,-172,502,209,-781,823,692,-899,-247,985,531,-368,-160,-618,189,97,-162,207,-370,942,181,379,-344,810,0,-766,-559,-401,552,-789,-389,68,651,777,-59,307,-922,710,821,870,-197,270,-206,272,-196,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "absUrl(java.lang.String):java.lang.String",
            new int[]{174,456,-1000,616,-300,646,-702,-320,801,-486,-993,-86,-530,1000,-71,610,-1000,-435,-1000,-1000,-729,-1000,-1000,-1000,-1000,101,558,-1000,-1000,-1000,188,439,-1000,-244,30,568,-16,93,1000,1000,94,-401,-604,-566,-1000,72,-182,-442,1000,677,-454,1000,-1000,-975,-1000,931,-1000,-1000,-1000,-987,240,81,-300,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "absUrl(java.lang.String):java.lang.String",
            new int[]{-37,-308,-555,1000,1000,544,-820,-784,1000,204,-167,1000,-514,211,29,107,-1000,788,-46,-1000,40,-1000,-445,858,617,-483,823,600,-764,-855,484,-896,-1000,673,-15,-467,443,866,657,327,1000,294,-1000,1000,-277,-697,360,980,468,225,-1000,1000,-668,-282,458,1000,-1000,-1000,-391,-872,908,-295,434,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-298,1000,1000,68,-257,778,-197,-716,-552,390,1000,-1000,-1000,799,-115,-123,-108,606,-1000,452,-376,-1000,-392,-16,504,-646,622,-200,-1000,720,-994,1000,1000,89,-22,537,47,-444,-249,229,494,-983,468,-728,-198,-254,977,-1000,-1000,262,173,190,-467,-434,-1000,661,162,615,-1000,-1000,-586,50,-522,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-157,-764,1000,577,-1000,408,1000,1000,-19,-7,1000,-299,-945,0,391,770,87,-26,-854,1000,673,-1000,-693,511,-1000,-528,632,1000,-1000,1000,-220,-400,1000,1000,-597,-310,-1000,1000,-1000,1000,42,226,-1000,1000,1000,-1000,1000,224,309,745,-759,1000,737,-400,-298,1000,1000,-45,-668,726,299,-324,751,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-578,-260,-162,1000,-961,363,-727,113,-891,374,1000,-11,-1000,648,-949,-272,-271,644,-1000,-736,-76,-713,-1000,-1000,1000,1000,-589,640,-137,-292,200,-315,284,222,717,-1000,1000,991,274,-1000,-83,-154,666,1000,-802,-494,-1000,350,353,-1000,221,-272,449,1000,-523,-952,815,1000,-506,-406,-526,-343,-473,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-21,-446,-584,-507,-775,-306,181,-557,624,300,290,-225,-588,-931,-82,185,518,889,471,144,-842,-561,-318,-70,261,304,162,834,-288,-929,970,-780,-121,-730,223,768,-68,-469,850,-934,40,860,-774,469,-300,-437,-408,-887,182,-997,-52,-144,-664,130,-613,999,-131,515,749,668,-396,471,-123,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String):java.lang.String",
            new int[]{-898,925,-1000,31,-115,-636,-142,133,227,507,-1000,-1000,1000,-276,1000,-470,-771,-267,893,384,-190,-492,964,-107,1000,-510,-452,404,-899,-775,1000,320,70,80,63,522,168,82,1000,-526,-432,1000,1000,18,1000,143,-473,-870,637,-1000,-98,-860,1000,1000,-1000,-990,-875,-1000,-132,785,-39,554,-191,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String):java.lang.String",
            new int[]{591,-20,-519,-849,-56,-649,-465,-66,-213,253,-345,651,462,-230,317,992,84,-920,272,-591,946,-561,-288,-336,982,-838,-809,-631,-787,44,393,-213,795,299,562,-918,587,880,-270,278,-1,797,782,210,289,940,788,932,439,834,89,239,-706,723,-865,-771,-278,442,251,148,-781,659,148,-813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{446,145,-710,16,806,-981,-452,632,-646,672,446,914,381,660,950,414,657,-126,779,-401,-242,266,-996,809,854,-182,544,366,549,247,652,167,602,-428,452,-297,604,677,635,-433,-982,730,-453,-812,393,489,65,789,-988,-651,888,397,-264,-701,186,-924,-579,876,906,-479,894,694,-566,649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{798,241,-166,653,274,-801,-580,663,519,190,315,-703,495,154,551,-6,-409,667,295,486,-884,942,272,986,574,-696,155,-785,265,-888,905,81,-37,-364,98,382,-316,-479,-740,-989,901,78,-605,863,947,-83,-978,-123,-209,-815,313,-542,916,-976,-811,906,581,-218,607,430,416,-27,705,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Comment", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{968,-21,-428,364,398,740,-548,437,-893,-493,873,591,-982,-594,-677,-635,894,-261,271,-852,515,255,-95,-289,-191,-310,703,-489,-338,526,583,21,411,-547,355,-69,-759,-884,-772,-824,-951,-616,277,-544,749,-404,909,140,423,-971,-385,278,254,-823,-385,823,-249,336,334,88,-569,449,-913,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attributes():org.jsoup.nodes.Attributes",
            new int[]{-730,-938,-162,455,585,1000,-331,549,1000,892,-700,-1000,888,422,703,-1000,-887,984,481,1000,-1000,-675,1000,563,-543,481,500,-1000,-770,-966,1000,805,181,1000,-1000,-440,660,158,-953,-487,-259,-685,656,-599,-736,-130,-77,-492,-160,1000,-965,980,-739,-1000,1000,-362,970,-60,-322,-186,265,-1000,1000,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attributes():org.jsoup.nodes.Attributes",
            new int[]{263,-269,1000,318,-233,-574,-165,-728,106,-413,1000,1000,-827,-1000,-788,-248,1000,-1000,-98,-374,45,891,-84,176,69,-964,-473,1000,-223,248,-1000,-489,-520,741,1000,830,-99,-894,666,658,1000,-363,915,-1000,465,-10,1000,145,-188,581,-1000,-1000,796,1000,-305,1000,-1000,1000,-306,-274,-682,563,-147,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "baseUri():java.lang.String",
            new int[]{434,1000,119,-1000,1000,29,-1000,306,-486,77,-857,-1000,-842,1000,-497,189,-462,133,-154,-1000,297,1000,-393,-607,545,1000,526,223,323,-180,598,-1000,1000,-532,-473,-889,-769,-1000,-728,-989,513,-681,843,147,595,838,-711,171,1000,1000,-133,146,815,769,-869,-1000,748,963,-1000,414,19,-528,-27,767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "baseUri():java.lang.String",
            new int[]{-905,-390,997,-534,182,-929,1000,54,-37,1000,-210,289,-802,-728,1000,787,1000,-996,-580,12,1000,-419,-242,-368,-833,-1000,-762,19,-664,260,-774,222,-548,-838,904,667,9,-120,-271,943,937,-370,1000,-682,865,-886,151,-382,-674,382,1000,-673,-913,1000,-516,899,-885,-506,130,588,346,1000,-51,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{246,-747,-1000,1000,634,-236,889,863,-467,1000,522,-419,-599,-1000,836,205,-346,-284,1000,-387,-470,489,-416,625,-1000,1000,-403,1000,-1000,111,637,348,377,-21,-548,767,848,-96,391,-781,830,968,-730,-174,-72,-449,1000,1000,164,518,259,1000,929,97,1000,-416,-1000,921,1000,789,1000,952,457,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{731,92,406,642,33,851,-797,-370,297,175,261,71,909,-665,639,663,-87,379,684,-103,352,-3,728,-428,-309,939,-786,34,-424,140,-94,-26,676,-649,926,-888,445,894,-89,656,234,-40,-214,18,402,-234,319,952,-184,855,-43,21,-569,825,253,-916,551,-149,-387,-634,767,534,78,-399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{494,-462,791,559,-205,649,21,-116,162,-854,-481,1000,-202,160,-597,-67,68,1000,987,-282,340,345,196,182,147,-489,-40,421,-548,-764,1000,-63,77,-1000,822,-65,334,522,106,254,-486,691,-710,-153,1000,874,18,-287,622,-1000,-146,215,790,-925,793,145,1000,390,-1000,701,-1000,-448,-744,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-145,1000,-298,-663,31,1000,-1000,37,132,-1000,546,346,-1000,156,554,-522,203,322,790,1000,-1000,1000,-1000,-1000,-1000,782,-340,-806,341,299,1000,-322,-1000,842,-556,309,1000,-263,-1000,163,332,-146,195,1000,-76,-343,1000,-734,-1000,325,1000,-123,-773,-634,-380,-510,233,-197,245,-430,-527,-559,1000,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNode(int):org.jsoup.nodes.Node",
            new int[]{-670,799,-811,510,-88,1000,904,1000,1000,-1000,-783,1000,648,13,1000,319,-902,-1000,1000,719,-474,63,-1000,-799,-581,-931,809,482,-1000,453,1000,-772,1000,-291,-1000,-1000,232,1000,-967,586,-313,148,1000,-1000,9,268,1000,-1000,-818,-830,-1000,28,-232,975,288,177,272,835,400,-454,-316,-1000,-1000,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNode(int):org.jsoup.nodes.Node",
            new int[]{479,-256,-151,-313,593,394,899,-361,852,-338,1000,-927,-660,-355,685,-1000,311,-93,-430,340,-86,-368,-201,428,-671,178,1000,1000,-1000,352,-679,1000,378,-785,-1000,895,-352,-254,121,201,-70,125,-1000,8,-145,235,-841,262,379,454,-237,-387,-121,712,-699,152,-1000,412,-593,175,-478,291,697,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodes():java.util.List",
            new int[]{-342,1000,1000,1000,-849,-447,119,-212,130,879,-995,192,-692,54,-452,-104,274,-1000,817,-661,439,-1000,1000,-1000,580,-212,1000,-1000,-1000,-1000,1000,-431,352,-787,335,393,273,584,-86,660,974,1000,-336,728,-34,111,-338,-1000,1000,-493,214,450,906,24,-1000,-660,618,-1000,711,-394,-594,188,982,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodes():java.util.List",
            new int[]{-517,3,482,562,125,-765,-206,-152,-419,893,-494,-583,-749,-152,734,-104,432,-256,-227,739,-588,105,378,-181,-179,666,852,-192,-420,-855,923,15,27,-311,791,755,-156,-505,-86,62,245,768,100,-467,583,70,320,-506,976,-615,-729,-736,863,777,172,299,-488,-627,538,266,396,-388,-250,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "clone():org.jsoup.nodes.Node",
            new int[]{266,-713,-253,-563,615,-792,-167,-479,-885,-864,-576,-143,249,581,-5,603,-669,-506,-973,965,869,-218,490,437,-547,890,-208,310,405,-184,-562,-598,-814,545,-820,-355,408,-993,-944,288,-844,-567,-603,293,-685,225,589,764,-393,322,-787,-849,86,450,26,406,55,-386,-832,-726,491,-280,975,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "clone():org.jsoup.nodes.Node",
            new int[]{-253,-1000,899,-453,1000,-923,-551,174,-57,-253,576,-19,970,-1000,-815,-676,-58,-446,-40,1000,-244,778,-262,-1000,514,899,1000,1000,-238,792,680,611,367,-1000,316,156,-277,-311,-1000,254,-346,216,391,-1000,-303,-591,970,1000,104,513,863,-478,435,-243,1000,-202,469,-396,-1000,490,639,571,1000,98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{482,1000,270,278,708,1000,124,375,29,-859,-1000,-247,-474,338,649,-762,-440,271,-610,1000,-571,-1000,246,-1000,-1000,-478,-1000,1000,558,-30,469,-289,661,-55,142,-839,1000,1000,666,-497,255,-489,304,133,-351,-294,-246,142,-1000,755,1000,-414,1000,-402,-396,-495,-295,-144,-55,-1000,-1000,544,193,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{695,91,-285,320,1000,782,-107,935,-967,-681,507,-926,-530,206,-661,830,683,-935,598,854,-744,303,-148,-267,-333,570,592,195,-482,330,240,-424,-731,927,666,-28,-186,-30,279,836,-555,-486,-348,-638,-259,-394,280,-47,-238,49,548,-682,-456,-86,310,272,-647,-704,-465,-782,408,-938,-575,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "hasAttr(java.lang.String):boolean",
            new int[]{-590,1000,-1000,931,868,220,614,-424,-362,170,443,-788,-1000,-573,-459,-1000,74,-233,-476,-564,-404,-368,-1000,973,-661,-135,1000,45,1000,-240,1000,-456,-1000,49,-24,759,-251,1000,-1000,-388,379,-191,-1000,-766,-995,1000,129,525,-547,-1000,1000,1000,-562,-141,-251,1000,690,923,651,-59,-429,603,-605,-969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "hasAttr(java.lang.String):boolean",
            new int[]{551,1000,-145,490,548,298,620,960,120,-1000,1000,-746,412,1000,343,611,-1000,782,-941,-1000,-474,958,1000,1000,816,-1000,-124,-37,982,29,-390,-1000,-146,1000,-939,-720,-405,1000,11,-12,-183,391,-536,-866,558,808,-58,-1000,-1000,-1000,-442,-663,202,-562,891,-823,1000,-104,-1000,667,884,1000,595,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{-50,767,-1000,73,-29,1000,204,184,1000,896,779,-1000,-1000,273,-46,-781,83,442,1000,-928,-968,-334,794,225,278,584,908,-1000,-595,864,-733,-533,77,786,-930,-517,110,-399,1000,1000,-371,-905,-2,491,1000,-725,41,506,-18,53,608,1000,-884,220,1000,255,208,98,830,717,-268,-422,614,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{-161,937,868,-444,1000,-954,-517,-271,-836,962,-1000,-1000,101,551,245,-142,793,386,1000,-486,924,-1000,-486,1000,708,286,399,-16,4,418,-990,-406,-662,1000,-1000,-74,-1000,1000,-1000,904,-1000,1000,368,962,613,52,1000,164,12,-1000,611,870,1000,608,-1000,-25,-1000,-1000,-252,1000,605,-312,1000,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:I2RvY3VtZW50", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nodeName():java.lang.String",
            new int[]{350,-1000,-1000,-1000,340,1000,-991,1000,-484,-157,-866,-1000,-14,584,-783,-138,-285,-917,-843,721,520,1000,476,880,1000,-425,-326,-1000,-343,188,-277,204,-214,21,-1000,-406,205,1000,852,281,-235,665,-590,428,111,1000,1000,-988,800,308,-1000,597,342,1000,807,257,1000,353,-598,1000,408,200,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:I2RvY3R5cGU=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nodeName():java.lang.String",
            new int[]{347,-484,-670,-869,302,498,778,1000,-980,580,-1000,-1000,749,-117,-830,-1000,43,790,20,-760,-851,-214,270,1000,-268,245,-395,1000,1000,-289,918,-805,254,-189,98,978,370,-389,-62,27,1000,-921,111,-498,-1000,578,188,696,-462,-1000,-405,1000,-830,827,735,-332,534,1000,-1000,495,-235,720,501,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{838,-685,-1000,-913,-1000,-1000,-551,-822,1000,683,619,1000,-516,-252,-1000,-1000,-901,-334,-1000,808,400,1000,423,1000,1000,-1000,1000,-1000,682,757,-1000,-1000,-1000,772,-984,-1000,900,512,656,-1000,-157,-895,950,637,-661,1000,325,1000,-1000,-1000,-944,-887,-596,-640,311,1000,1000,1000,-1000,1000,-332,-717,263,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:PCFET0NUWVBFICA5NDcgIFBVQkxJQyAiLS0weDE4ZCIgIi04OC43NjMiPg==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{-645,804,666,947,-784,487,397,-381,-603,88,149,763,251,116,302,99,398,-508,571,749,807,189,277,51,903,-314,32,-228,487,-925,569,400,657,-7,238,136,-284,668,890,7,57,344,289,131,-569,87,-3,587,635,547,751,-465,-287,-352,761,-666,0,-625,52,191,20,808,28,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:CjwhLS0weDgwLS0+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{1000,-762,-1000,-143,1000,-259,-1000,459,647,-635,-46,27,822,290,-108,62,264,904,-214,-621,-110,-874,-973,-587,-316,-348,586,-583,-127,197,-77,158,-246,392,546,-475,893,-843,-1000,473,502,-966,291,-421,951,605,665,-206,-1000,-1000,289,202,80,-669,-1000,41,742,-104,-255,-525,414,-703,-134,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{870,110,-137,296,-697,104,352,-1000,-1000,686,-444,-1000,310,-1000,482,27,164,237,1000,-63,-182,385,321,-698,884,-535,-640,752,-1000,-576,927,64,-641,-1000,549,-822,157,1000,-174,675,54,-1000,1000,278,114,1000,289,1000,-450,80,-1000,360,-1000,-1000,-1000,-579,131,1000,-871,239,157,-508,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{-514,1000,259,60,1000,348,-16,-445,-1000,-295,-38,-503,238,-339,-1000,148,-396,-220,413,402,-168,729,-1000,559,377,1000,-447,-680,-195,347,454,861,-764,1000,553,851,837,-660,217,764,-96,1000,-278,341,358,562,-520,-1000,-77,-788,350,-130,-416,59,814,231,-585,968,195,460,-633,-612,-250,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{-457,-224,-402,838,1000,-21,823,294,201,1000,-404,1000,-819,-339,-833,148,-396,171,413,-542,-1000,-73,-272,-637,-505,-543,-447,467,151,415,-771,-50,-1000,-170,488,71,1000,-660,-1000,116,712,1000,764,-1000,-588,562,-515,166,-916,74,315,-753,-406,-1000,134,-454,704,1000,136,1000,-147,-739,994,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parent():org.jsoup.nodes.Node",
            new int[]{-202,75,-1000,-1000,287,676,458,798,579,-1000,9,-1000,-22,933,-1000,-516,-1000,769,1000,1000,731,990,456,539,1000,1000,811,-796,-1000,-79,-90,-717,-1000,72,315,1000,-1000,-1000,-1000,-1000,-1000,-974,-214,-29,-380,-1000,1000,750,1000,-513,1000,400,1000,214,-619,1000,1000,520,-936,1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parent():org.jsoup.nodes.Node",
            new int[]{-945,-461,-477,918,-265,791,877,-261,-78,499,-691,-407,-408,-410,275,-831,218,-652,-919,105,813,-17,635,98,617,399,-784,273,-65,171,969,356,-995,907,-651,752,-92,-431,-949,431,537,-902,-885,-293,424,-275,809,-568,-179,-280,691,935,-145,-960,-197,74,811,-721,328,234,-375,691,-740,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "previousSibling():org.jsoup.nodes.Node",
            new int[]{106,-377,312,192,1000,-1000,-616,270,262,316,409,-704,-391,153,-207,-574,48,203,809,442,622,-759,-219,-867,125,788,-194,169,652,-1000,-780,-224,-782,-784,1000,5,1000,1000,-1000,-822,-43,47,-998,-663,-699,-1000,-332,1000,1000,558,-1000,1000,-388,436,157,259,-809,1000,528,787,-244,-114,1000,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "previousSibling():org.jsoup.nodes.Node",
            new int[]{-285,-310,-1000,-466,868,508,151,1000,-430,1000,1000,-19,734,-1000,728,-269,377,578,637,-354,206,26,163,-124,-74,792,1000,646,147,-970,-1000,-1000,1000,204,167,-1000,1000,570,804,-232,-1000,-868,-243,602,740,761,-751,486,-1000,-226,-318,1000,137,1000,-158,-68,-703,-995,-1000,-411,596,545,387,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "remove():void",
            new int[]{-374,-784,113,307,407,-341,-617,239,925,409,1000,-733,365,-1000,482,970,719,-618,-20,300,1000,568,-119,-215,-31,-204,278,-32,-576,-195,-249,127,375,970,175,19,-1000,-10,-1000,-889,120,-26,1000,-240,-1000,801,-1000,125,995,828,-5,17,-32,1000,-511,-170,48,416,-200,-8,-20,501,20,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "remove():void",
            new int[]{231,131,-107,-642,-492,303,-238,717,-878,2,322,-945,348,750,-410,-30,449,-700,-242,430,-160,-806,-537,-361,320,466,664,-293,309,-885,227,-879,-653,348,874,-89,-553,-460,812,-102,-648,355,65,407,-805,522,286,-525,-358,431,-339,684,-808,-163,-577,529,-16,-220,-575,458,193,96,-737,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{594,-44,-1000,-226,-32,526,-391,-601,130,185,-1000,56,425,-1000,866,-60,170,-102,-1000,1000,615,-534,-1000,-1000,-908,-784,-1000,-239,-441,-426,-742,-789,1000,140,-1000,470,200,96,309,315,-1000,325,1000,1000,-1000,-520,605,-655,283,-1000,-263,-1000,-575,1000,1000,363,-429,-495,263,836,-922,-365,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{407,394,-707,-393,-534,-641,848,-742,881,701,-950,54,-448,-553,-164,329,-947,188,273,918,-562,-241,-844,590,844,-676,787,211,-796,-95,221,-859,277,792,270,-33,675,658,-12,423,-375,734,-516,76,759,-98,496,213,-583,-797,363,249,-350,-73,795,927,400,-811,132,-395,186,-329,685,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "replaceWith(org.jsoup.nodes.Node):void",
            new int[]{-874,-299,521,1000,744,-422,-151,1000,261,-1000,400,293,138,-873,-347,-950,-416,207,1000,-1000,1000,-917,-1000,1000,-264,-734,776,795,-710,-1000,1000,-1000,-1000,1000,-521,1000,-1000,-290,-948,1000,-783,38,-157,-830,-47,-1000,-1000,-1000,18,1000,-1000,1000,1000,1000,-272,-606,-1000,-1000,551,822,670,1000,-954,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "replaceWith(org.jsoup.nodes.Node):void",
            new int[]{147,-751,168,717,680,-86,247,128,-185,-852,673,-94,-216,-411,58,-221,690,-161,-85,-855,95,605,-1,-495,-942,-686,403,-332,390,14,-861,-66,-741,267,-475,884,-279,-738,-558,491,-788,-556,264,-186,610,-438,-888,724,70,266,-22,823,515,78,-812,-995,-598,-58,749,-57,-811,377,-856,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "setBaseUri(java.lang.String):void",
            new int[]{134,-1000,-474,-1000,1000,1000,-956,-1000,666,-231,0,-1000,1000,678,-433,-763,-228,476,1000,1000,-724,823,604,1000,641,-1000,-282,476,-1000,1000,-1000,1000,-1000,-1000,-1000,853,1000,-921,1000,-859,-656,1000,-1000,-1000,1000,-1000,-90,295,-1000,-458,1000,-1000,785,1000,-583,-1000,1000,-91,1000,774,-1000,-744,143,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "setBaseUri(java.lang.String):void",
            new int[]{-993,1000,-888,461,-1000,-227,-1000,1000,302,-225,255,757,29,-11,-807,104,-1000,-735,-330,162,509,946,259,-1000,188,672,-639,454,511,-547,20,-526,1000,491,-513,-1000,-1000,-45,-675,60,1000,514,-86,-3,-610,606,-551,-628,333,684,-448,684,-494,-735,632,359,-413,-235,-1000,-724,1000,438,-878,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingIndex():int",
            new int[]{-890,-471,-845,-1000,645,563,-1000,-278,379,89,484,-202,-121,1000,571,543,-951,-1000,85,366,957,348,749,125,345,162,-62,-321,-326,570,-42,-616,-374,-188,-794,1000,-870,-534,1000,-1000,-499,592,1000,287,-85,-102,-201,998,863,708,-475,-637,-284,-607,864,-949,171,-1000,57,349,-613,-426,-99,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingIndex():int",
            new int[]{-945,-979,-306,-481,768,-662,105,-867,401,732,772,950,312,42,944,742,-750,-787,889,973,604,-10,676,147,522,332,-594,-931,122,958,1,96,-603,49,-481,-172,-660,-235,485,-877,-664,-79,714,791,914,-27,-252,877,599,244,-863,58,-229,-730,-444,-653,209,54,-6,-964,304,48,-851,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptyList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingNodes():java.util.List",
            new int[]{506,1000,-946,-449,311,-491,-563,-429,658,350,-47,-910,164,-430,-677,258,228,-739,1000,262,64,-510,295,375,-658,-400,-785,602,522,736,386,661,355,-100,-132,-186,258,-294,-400,220,-147,779,508,340,302,-92,-564,694,-355,-255,734,596,-61,194,-332,130,-715,-777,473,-107,128,646,-416,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptyList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingNodes():java.util.List",
            new int[]{-545,583,309,855,101,126,382,-883,-131,505,-832,-186,481,-242,-677,950,-864,-985,917,755,339,-75,813,664,237,112,-567,973,-767,852,-483,-697,-930,-578,-347,-764,-990,-698,582,-675,260,818,589,-972,386,663,-353,77,73,-752,412,-706,-675,-155,-350,-89,946,-912,221,-891,-26,551,-644,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{-130,-1000,-1000,929,-1000,-678,-856,-422,-609,1000,1000,-1000,-1000,271,-1000,-507,967,-1000,-72,-584,-1000,-1000,849,-1000,714,-718,-1000,-674,437,-941,-1000,-1000,-1000,-1000,-1000,1000,-277,-1000,-1000,744,1000,511,1000,-362,-1000,-532,-757,159,1000,418,-1000,-699,1000,1000,160,-1000,-1000,1000,-686,417,-1000,-873,-1000,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.String:PCFET0NUWVBFIDEwMDAuODEyIFBVQkxJQyAiKzB4OCIgIi0tMHg4MDAwMDAwMDAwMDAiPg==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{139,-931,-107,1000,-1000,-188,-472,117,-474,1000,-776,-1000,-961,-849,-204,-11,1000,-483,109,140,-1000,-1000,118,-1000,500,-693,-1000,-918,-302,-946,-876,-642,-1000,-1000,-593,1000,233,-325,-824,-12,1000,-131,190,-498,-1000,109,-849,783,1000,420,-785,-795,642,1000,966,-784,-30,674,-705,387,-688,387,-1000,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:CjwhLS0rMHg4LS0+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{260,-601,-264,1000,554,-1000,-868,-1000,70,370,544,54,-1000,-836,864,138,604,-23,509,-291,-818,-1000,1000,-39,-483,-75,580,-997,586,-1000,441,-215,-1000,-969,-508,1000,821,-1000,-539,683,1000,1000,736,-604,-132,-802,-143,-1000,1000,527,45,-1000,1000,1000,1000,-1000,-1000,1000,-431,650,-696,-176,-719,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "traverse(org.jsoup.select.NodeVisitor):org.jsoup.nodes.Node",
            new int[]{-810,-17,149,766,914,481,-211,-627,-583,559,-848,-197,-723,902,905,979,-673,84,-780,-959,509,560,733,-384,-290,-483,-53,-993,-960,187,-941,620,-927,324,-456,-369,689,-750,188,84,-686,-870,-257,435,-396,-60,-103,-151,-97,974,-204,528,200,684,-947,761,676,-876,-165,-540,483,-630,409,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "traverse(org.jsoup.select.NodeVisitor):org.jsoup.nodes.Node",
            new int[]{-113,-594,-830,-877,1000,-447,1000,-211,-260,600,685,1000,-349,-1000,-1000,-368,-386,-22,-522,1000,-907,-414,-793,-291,313,-489,-1000,-1000,28,-511,736,1000,-393,-1000,823,847,-324,-270,1000,-186,312,-1000,-396,-4,-45,1000,471,911,-1000,1000,334,1000,1000,-422,-173,-1000,239,764,1000,-1000,-1000,-467,912,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "unwrap():org.jsoup.nodes.Node",
            new int[]{-910,-122,-716,395,160,-381,632,1000,-338,-603,-536,-737,1000,-407,-119,717,929,-795,-605,-605,-67,1000,783,-868,-300,443,-1000,-16,810,-172,-606,-397,-265,-157,1000,68,511,382,555,1000,-438,-1000,521,932,687,-404,-421,268,-364,-163,1000,-28,122,571,-674,-546,-1000,1000,594,-568,963,149,1000,-374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "unwrap():org.jsoup.nodes.Node",
            new int[]{987,562,-391,-786,-544,-128,219,835,509,985,-30,-266,-705,74,-202,878,913,-279,889,-696,-873,979,-319,-142,214,-67,-279,-308,-829,-383,-817,-815,-593,-389,-487,-683,868,-605,21,-764,-947,-348,-482,716,813,87,-892,536,-12,-930,103,-579,976,-595,-286,-885,-879,-111,621,-595,-542,-34,839,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{694,308,-1000,549,-581,1000,504,-860,-164,-155,265,-191,-51,471,400,39,-417,-750,822,-946,-96,-379,-105,400,400,-115,202,347,-304,-110,312,-620,-189,-1000,-1000,-1000,-93,-541,324,159,545,163,850,204,-736,-400,288,147,-1000,-79,-77,-312,66,664,1000,1000,400,-248,400,-723,400,902,349,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{987,-384,-1000,-7,-145,-663,822,9,-744,-367,248,-1000,-1000,542,-47,1000,-694,-766,1000,552,73,-81,-1000,-1000,1000,-969,1000,-1000,-1000,1000,-1000,-650,-317,-1000,1000,282,648,-1000,88,862,-1000,683,-526,661,-448,1000,495,806,26,-1000,-1000,-545,98,-730,-861,11,-1000,1000,-751,-421,-222,881,1000,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.String:LTM4Mg==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{371,-382,-973,855,-489,-740,34,381,78,453,-576,749,138,469,-932,76,-226,442,-890,385,714,-168,863,-224,996,-688,77,-516,85,-209,475,223,-503,518,-216,366,-458,706,-328,-85,109,997,978,957,517,-809,393,-605,466,-835,388,67,818,659,568,71,-447,703,-118,-887,616,857,-857,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.String:LTMzMS4zMTQ=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{517,331,449,314,489,-969,237,272,-565,954,238,-489,-481,18,16,826,-32,892,-414,-612,50,621,639,47,-588,-837,-356,352,-345,-885,234,-672,-606,-70,571,-725,-188,-223,-966,867,683,887,482,610,225,-325,-405,422,-228,-764,-638,-537,224,923,-366,688,-503,-390,867,584,806,-11,-352,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "clone():org.jsoup.nodes.Document",
            new int[]{-9,393,-581,-976,-1000,554,74,384,-36,1000,-999,1000,-700,-1000,-1000,1000,-14,-212,-1000,-284,1000,308,-703,1000,199,864,298,-566,1000,-1000,-1000,871,-747,-95,1000,-1000,959,358,91,834,1000,-836,-348,-1000,1000,-813,-375,1000,-581,734,-377,-74,381,1000,-1000,730,-1000,-1000,-696,13,430,-294,-309,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{-667,-154,-776,-604,1000,358,49,-1000,1000,1000,998,597,423,1000,-583,-119,-218,-751,1000,707,1000,-46,-638,1000,148,-695,998,198,-232,-1000,615,1000,1000,-319,-171,59,-266,650,225,-1000,-1000,-1000,1000,-458,-678,-851,845,-647,-1000,-569,62,-16,-499,265,974,1000,-353,-246,911,148,-1000,1000,461,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{-714,992,1000,580,1000,768,-197,813,660,-880,-815,-1000,-1000,1000,-754,1000,919,369,-867,548,-165,-1000,358,-1000,-324,-472,341,-62,204,989,-920,-587,-745,-200,-720,1000,103,8,282,-307,89,-438,-601,-400,-47,-553,-589,-581,467,-7,1000,-408,-1000,-611,322,298,410,77,-606,1000,-74,519,1000,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{927,423,-1000,-1000,1000,301,-851,-793,741,-268,-694,-149,-1000,1000,617,106,1000,-971,-306,-1000,69,171,610,-1000,-126,-1000,-268,-1000,532,1000,85,-669,-1000,1000,-428,879,-1000,-1000,-1000,-1000,1000,532,1000,300,379,-659,-1000,-752,-847,17,198,-215,-272,1000,-454,-759,-532,-442,-491,-1000,1000,-231,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.TextNode", DEReplay.run(
            "org.jsoup.nodes.TextNode", "org.jsoup.nodes.TextNode", "splitText(int):org.jsoup.nodes.TextNode",
            new int[]{221,300,245,-632,-343,912,625,-795,-553,-306,-630,-563,45,966,767,813,-756,869,-803,877,-76,-861,23,450,690,886,869,-81,-520,911,-898,121,860,-582,436,698,-227,276,-738,-106,281,-631,-524,-57,-988,-927,-579,-562,-103,-649,-148,350,-870,606,-233,761,-769,16,-570,765,595,-183,906,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{545,-613,-204,664,-280,-432,1000,594,-1000,61,761,-254,346,-325,-1000,-587,417,1000,-136,-1000,-461,1000,-137,-418,-884,-300,-938,-455,-1000,762,-734,-1000,-479,-777,44,-1000,-20,250,574,-1000,-1000,-726,487,-848,-27,-607,457,33,724,-940,352,395,158,1000,1000,900,-14,-176,-381,764,235,-1000,474,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,24,-226,683,-533,-519,157,435,-551,-1000,215,242,340,1000,-235,-884,821,536,1000,1000,950,459,967,-617,559,-1000,1000,-247,-69,347,-1000,-448,-736,-481,178,771,-361,-685,50,-105,588,380,624,509,567,-1000,274,-369,-893,721,508,-486,-227,-626,-1000,-581,-375,249,391,-39,1000,849,546,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,126,1000,229,-622,1000,1000,491,152,-853,594,-743,786,1000,-788,-948,-1000,-58,1000,-1000,-315,44,-212,1000,1000,287,668,490,89,-1000,-1000,-652,-774,163,168,985,400,325,820,-285,421,-1000,77,1000,246,1000,-445,822,-160,1000,-1000,-1000,1000,-1000,-1000,1000,1000,-319,1000,-624,-1000,-392,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{30,-1000,124,1000,4,152,-1000,-444,1000,309,-191,1000,674,266,-1000,-152,675,977,1000,-263,884,-967,280,-529,-367,759,668,-1000,-664,-304,-1000,-1000,-410,-24,438,1000,400,1000,837,-1000,421,247,1000,-359,119,-1000,-801,335,758,1000,-506,-1000,459,-1000,1000,-315,258,759,-609,-1000,-1000,1000,558,805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-505,-721,500,-102,182,-143,-792,1000,-435,-1000,1000,-1000,469,914,675,-357,-792,-215,22,1000,-213,40,-525,770,1000,-1000,-535,922,1000,-499,329,265,-178,-990,175,-624,-70,-1000,-840,632,122,-428,-1000,1000,1000,1000,386,970,-1000,-420,-454,1000,272,149,376,863,192,-326,159,141,504,-496,-733,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{149,-41,1000,906,-556,35,489,-850,1000,-892,-451,-599,-567,570,1000,666,-1000,-355,108,-792,417,-856,-1000,1000,932,250,46,1000,-1000,-5,276,556,1000,-518,-575,166,-1000,55,-907,1000,937,-932,-925,486,406,-634,-539,-487,-192,518,-697,761,394,35,525,-47,-756,236,635,-1000,554,158,608,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-672,-172,139,866,62,-475,-574,-695,1000,-51,-700,245,-617,-408,831,920,483,338,-274,152,729,-795,-1000,-468,-107,71,142,315,-182,421,1000,151,1000,-884,-136,-303,-1000,293,-1000,529,872,744,-175,-609,-80,-939,-222,-1000,482,312,309,1000,445,668,1000,-700,-1000,830,-333,-579,872,352,-1000,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{585,1000,331,-334,83,523,890,954,1000,1000,-482,729,167,-659,-618,50,517,-343,-830,-982,277,-652,-306,-177,-524,-339,-209,77,-627,185,-907,-1000,-354,620,1000,-538,1000,821,-1,-417,238,-169,451,-171,-772,1000,897,-533,76,150,-681,233,1000,62,-910,151,-372,859,-1000,930,-1000,-31,1000,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-321,-731,-340,-361,396,-683,831,180,-578,-199,-173,385,-46,-422,1000,492,-1000,10,139,948,-1000,-826,530,214,331,-981,861,-729,5,259,-262,425,-931,8,-328,603,-1,515,246,446,-254,46,52,-558,518,-30,931,304,-42,454,-1000,-962,382,-268,-138,-550,-671,116,1000,-860,261,0,310,860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{0,-1000,199,-755,288,-555,-1000,0,-143,453,1000,-229,551,233,0,1000,0,-903,-1000,0,-1000,-153,530,-244,-421,-1000,199,-729,0,-385,-639,-884,-140,-342,488,769,231,458,1000,-673,-254,-1000,93,229,589,-30,472,-316,55,122,-1000,56,512,-738,-1000,0,734,720,1000,119,717,0,286,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-400,919,420,1000,-420,-400,-137,-56,173,1000,-1000,141,-227,-300,197,269,-332,-618,-862,47,-343,343,411,5,-140,439,748,1000,-499,-1000,632,418,-295,773,49,956,-855,400,992,122,30,-34,-22,92,-587,93,399,-485,574,-143,981,-64,-389,-347,622,973,155,276,-231,202,894,500,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,1000,339,927,1000,1000,1000,584,126,-547,160,669,-1000,1000,-1000,-1000,900,523,-898,788,1000,-978,-480,599,432,1000,813,467,-1000,-764,-269,1000,-770,328,1000,-390,525,-71,-1000,-248,1000,1000,-614,1000,1000,-1000,1000,75,1000,-211,-483,-605,470,-1000,-109,-1000,-1000,-99,-1000,-1000,496,85,70,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-593,767,-438,769,-1000,20,567,185,-98,1000,-1000,264,-326,-1000,9,-275,67,-1000,349,76,-518,-445,-469,-566,-1000,607,-189,963,-671,-368,581,-435,-1000,-555,9,972,-899,405,994,-440,-164,-465,-823,416,-364,-88,1000,-834,71,30,987,-28,-512,57,736,1000,-192,-107,409,-159,925,650,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-94,-115,1000,424,314,1,314,1000,-435,-581,825,-362,-463,-623,-1000,-829,458,-1000,-855,-1000,743,-662,-1000,-1000,1000,1000,1000,167,-424,71,1000,706,-299,-758,-277,-1000,255,295,-575,93,-8,185,-1000,-1000,-370,-343,-25,1000,-1000,1000,-1000,83,-408,-686,1000,692,236,-1000,-375,-14,-1000,1000,1000,940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-378,919,891,1000,878,-400,-696,401,174,1000,-1000,141,-227,-252,303,547,-332,-451,-862,557,-40,-175,967,5,-140,298,748,938,-438,-1000,794,418,-651,773,-648,-66,-1000,1000,1000,-424,255,168,-22,-804,-587,89,355,-805,1000,-375,981,-813,-212,-347,-348,-225,746,276,-565,866,1000,571,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-128,-709,-167,679,602,-283,1000,347,-212,-295,-590,552,-838,-1000,301,-394,686,412,798,456,-194,-877,-983,30,906,-829,1000,-628,-96,-850,339,-884,485,-342,-324,-210,231,1000,-490,489,-542,-252,-336,229,176,-30,160,1000,-193,948,624,-791,-150,115,1000,351,-919,720,-402,-945,-34,-360,194,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{352,1000,89,-283,-713,580,-474,802,237,880,-849,-1000,-1000,-214,704,405,-681,1000,-519,-1000,1000,542,-788,-101,1000,75,-45,949,1000,1000,1000,375,-1000,1000,276,-392,-668,91,-1000,-1000,855,1000,-772,1000,690,533,-1000,-456,271,773,-1000,747,-310,-59,732,138,-431,-702,-698,-317,-1000,-1000,876,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{-352,-653,638,209,28,1000,-771,1000,56,1000,-1000,-623,-747,-81,24,823,1000,-1000,-1000,-324,-318,-485,-183,400,-57,-594,-647,-1000,1000,-37,-415,272,488,-227,975,-435,327,-680,-894,-1000,496,-1000,-460,-400,766,-224,-453,-523,691,-1000,-1000,991,476,253,1000,771,-490,-287,-370,-1000,-1000,-300,180,933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{-1000,-1000,664,-321,712,1000,-1000,1000,-328,1000,-1000,-145,-303,760,-190,471,685,-276,-1000,-157,577,-451,-48,320,-212,-527,-686,-436,1000,244,-71,1000,864,-1000,-329,0,453,-1000,-223,-472,318,-153,-353,557,651,-208,171,-100,414,-776,-421,1000,282,-356,1000,1000,-42,-381,-350,-789,-919,-44,-46,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{707,378,-809,-1000,669,-630,-619,-785,-375,-803,-546,933,530,-673,-370,897,545,-699,464,-1000,-842,-1000,-564,-993,-646,-528,-1000,401,66,-883,-1000,805,394,1000,728,-51,765,998,-20,-498,-57,-1000,695,-738,-71,-137,-643,45,909,-1000,-302,640,-157,-735,-7,895,-252,-1000,-75,42,-629,1000,-174,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{-1000,-1000,79,-820,165,-531,117,-620,-814,-1000,-205,1000,1000,1000,-677,-396,-290,-699,-361,1000,-318,-1000,-245,735,-735,-471,61,-1000,10,-708,-343,671,915,-1000,1000,1000,506,1000,1000,1000,-507,-1000,378,-7,-8,67,644,535,424,-1000,448,464,324,-89,-617,1000,848,-1000,1000,1000,630,1000,-566,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{931,391,175,-450,-1000,-1000,-640,-210,8,-451,10,-57,993,-220,700,1000,-443,377,327,-1000,-318,10,-1000,112,-242,636,-608,-243,212,1000,231,420,-722,491,1000,-339,318,101,-282,455,-370,-554,-733,401,-438,-472,-389,-31,685,-467,-377,102,-1000,196,-1000,-203,-261,-1000,-250,-881,-528,-281,580,200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{-1000,-1000,-36,-893,800,815,-640,363,-881,47,-1000,378,57,1000,-852,-796,192,330,-364,-1000,-318,-785,772,91,-138,-866,4,-445,1000,-856,231,-16,1000,-441,468,582,-103,477,1000,181,-397,-1000,253,14,501,813,351,178,806,-1000,-377,5,973,-5,472,-203,877,-792,448,969,-427,278,-502,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "clone():org.jsoup.select.Elements",
            new int[]{-1000,-653,473,409,761,1000,-647,960,-1000,-1000,-1000,-527,-265,259,-49,513,426,554,122,-167,-523,105,-213,-350,336,-433,-1000,-266,1000,-497,-337,331,895,173,276,-881,605,-338,444,-416,-807,767,-750,452,21,395,-181,-204,862,-1000,-921,277,320,75,-606,1000,697,-613,-545,434,-530,-96,-715,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{129,-305,1000,2,-90,-201,56,-501,647,-1000,44,-165,853,-230,-549,-972,-1000,-61,401,-62,-143,-1000,913,-88,1000,692,-567,-1000,-1000,689,-759,1000,1000,1000,631,784,539,652,1000,-803,-659,-700,1000,604,476,832,-415,1000,-1000,-1000,964,-863,-1000,-687,677,-532,38,-550,650,305,155,-652,530,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-111,876,309,-773,-90,-909,171,-472,5,-290,236,-386,853,321,-784,-335,-68,-568,590,96,-657,-805,272,-1000,842,282,83,-741,-406,-393,-640,616,711,903,631,697,-145,652,744,-122,-659,-210,113,226,75,755,-222,280,-623,-48,918,-627,-696,-471,87,-605,-572,-550,8,421,181,-704,60,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-538,-231,-359,-26,-571,182,1000,-330,-415,1000,74,-1000,-955,-982,-709,1000,509,-694,-1000,-390,-780,106,-21,-620,-920,-831,-1000,-275,774,-124,-354,-228,-160,-1000,54,-314,-231,-1000,-612,577,616,364,-567,-1000,-528,-26,-795,-1000,1000,302,-1000,827,710,-355,661,1000,572,340,220,-148,-1000,-552,-1000,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{205,-1000,-291,521,-1000,-371,855,-42,1000,-424,-1000,1000,297,258,688,-510,-92,457,539,-143,147,-550,154,686,422,-334,-697,-1000,-239,182,904,-400,170,646,738,-755,-216,157,223,-488,-876,422,100,-302,297,-1000,1000,1000,164,371,706,-809,290,348,691,-573,694,-671,-25,832,-1000,-1000,1000,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-92,899,1000,-1000,175,-1000,929,-38,-129,301,44,-400,971,471,-400,-722,217,-61,322,-557,-386,162,267,-1000,1000,390,-481,-802,-211,-812,-400,400,400,404,970,-189,-269,-270,275,-551,-675,57,-97,-804,100,-188,1000,-25,372,944,516,-360,368,884,365,-1000,-235,-891,-789,724,457,-1000,-110,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{252,371,711,199,-965,-424,373,-38,549,-466,-1000,1000,-230,1000,792,-407,729,230,982,-210,-107,-181,-606,-955,226,580,-481,-802,-372,-1000,833,-612,400,404,1000,-977,71,-955,-974,55,-842,276,-870,7,-77,-188,821,1000,-725,454,1000,-228,1000,834,-262,-461,595,-755,-639,1000,-181,-1000,1000,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{364,-252,-26,1000,-1000,1000,1000,44,1000,-378,-1000,1000,-220,1000,1000,229,1000,-208,168,-15,-412,888,461,-889,-1000,505,213,-1000,-327,-1000,1000,-1000,-1000,-287,1000,-1000,-609,-887,-1000,-188,-1000,1000,-1000,-403,-1000,-1000,1000,709,931,454,275,-243,1000,409,-934,499,1000,-1000,-1000,1000,-1000,-935,686,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{-872,-117,95,-138,300,975,-174,-315,507,-1000,-297,-256,863,-265,94,368,-606,-713,103,843,85,-1000,-891,521,-1000,643,733,-726,1000,220,-1000,1000,1000,-722,-611,-1000,355,-487,806,-7,1000,858,53,435,609,823,-662,448,-154,408,-1000,235,741,-726,499,282,293,-26,385,110,-227,-1000,-546,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{108,-1000,-916,910,996,1000,813,385,942,158,-505,1000,-314,-404,225,417,-978,-382,192,-263,-253,-1000,-423,6,152,-485,-251,-784,1000,36,543,1000,770,-1000,-376,452,1000,-789,103,-792,638,979,-506,791,475,-580,6,-331,-330,-38,-1000,-1000,482,-815,794,550,-287,-345,877,93,-107,-841,-455,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{209,785,20,913,659,524,-823,205,362,-519,-383,531,-188,789,652,34,-43,-840,779,929,-168,141,-681,-843,-527,-64,425,844,-834,-475,-839,-736,759,188,-54,-564,-313,-438,-500,753,-313,343,-363,-870,-278,-566,-289,-992,563,-110,365,284,679,-626,-681,-289,-392,989,387,681,-172,-652,645,-620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{274,652,-461,-872,-593,-745,-1000,884,329,203,-200,-982,-22,3,-270,-1000,-444,-66,28,-53,1000,-247,-1000,1000,211,265,-591,-560,-561,902,1000,-50,381,828,-467,-217,-1000,368,687,-1000,-1000,-1000,1000,480,-1000,400,-1000,44,-513,-205,823,631,333,-400,-366,-958,-1000,-257,-419,-1000,-1000,-215,-724,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{-851,-721,-285,-138,993,-112,996,-964,-766,140,-297,-256,863,-265,304,-551,-965,-699,361,661,-788,-258,700,248,183,513,-452,286,763,-936,-458,125,581,-12,144,-236,-635,-490,806,916,-612,858,434,198,326,823,219,765,705,-704,-351,422,-720,-9,-296,-262,-733,-332,609,-983,145,-804,311,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{383,-470,940,794,-347,-745,336,782,247,33,78,-982,-107,341,535,-623,-811,-360,300,-360,425,396,439,-682,-100,-1000,-382,-59,721,380,1000,780,345,-1000,-385,600,1000,-310,-1000,151,1000,336,-1000,-616,743,-962,449,-644,-341,-205,678,631,1000,-241,-366,800,864,163,655,-1000,644,1000,-724,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,438,940,528,-1000,1000,-1000,1000,534,1000,900,1000,-1000,-1000,-84,-1000,-170,-554,503,-1000,425,-648,98,1000,1000,-1000,-1000,-59,-1000,1000,-1000,266,-62,1000,-262,1000,-1000,171,1000,-1000,-1000,-1000,1000,-108,-1000,-1000,-363,-1000,-1000,-218,1000,631,1000,-575,-1000,-1000,-308,463,-1000,-1000,-1000,1000,-1000,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(int,org.jsoup.nodes.Element):void",
            new int[]{-69,1000,-436,-773,-1000,-998,248,836,1000,-706,-725,1000,704,-179,415,-1000,861,639,66,-1000,432,1000,-695,1000,-144,250,1000,382,-852,1000,-505,-1000,-639,1000,114,638,215,-400,1000,-968,-921,-820,-992,-1000,1000,436,-543,-259,1000,571,-541,-456,45,527,-397,953,508,244,-379,-161,961,-37,-1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(int,org.jsoup.nodes.Element):void",
            new int[]{1000,-557,-165,242,782,-775,-554,-493,-445,1000,178,22,423,-736,999,-1000,-10,-1000,536,368,-45,671,336,-1000,732,-364,-137,-485,311,-1000,725,262,123,445,-1000,-176,975,1000,472,382,-62,840,1000,-295,-523,666,610,-148,1000,575,-304,-773,557,-183,-789,538,-42,853,566,1000,-507,-724,-866,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(int,org.jsoup.nodes.Element):void",
            new int[]{184,45,1000,328,-508,373,180,765,1000,-280,178,360,-453,1000,-1000,454,-514,-742,1000,355,-632,-502,-465,244,311,-451,750,849,-772,-439,502,-826,-885,421,-346,-142,1000,1000,-157,-649,-1000,1,-1000,-1000,-78,1000,329,786,1000,111,-103,-704,642,841,-404,-190,-254,853,376,-1000,-840,-724,-786,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(int,org.jsoup.nodes.Element):void",
            new int[]{-871,584,494,-93,-349,461,528,-551,1000,1000,-38,1000,-742,1000,-719,994,-224,1000,-1000,-250,-56,-649,-653,881,-225,1000,709,463,-56,73,539,-942,-758,-508,-918,-103,1000,974,-269,-450,-1000,466,-1000,-213,1000,1000,226,-188,-966,631,-308,-212,-972,-895,-262,-1000,-517,-871,-571,11,-813,-215,-1000,416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(int,org.jsoup.nodes.Element):void",
            new int[]{-416,1000,-456,332,-1000,66,40,1000,776,633,-1000,93,1000,-871,-841,169,-51,1000,1000,-765,711,1000,-375,423,-113,1000,848,472,-1000,1000,-821,-255,312,1000,1000,553,-179,61,513,-965,-368,-986,-992,-520,1000,-395,-1000,-602,513,-55,-1000,954,-614,1000,-46,1000,486,-799,-1000,-1000,1000,394,-875,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(int,org.jsoup.nodes.Element):void",
            new int[]{678,-1000,204,332,801,1000,-1000,-22,32,-1000,1000,-835,-846,898,-1000,906,-1000,-988,-454,1000,-459,-796,-288,-677,-113,-869,283,-956,-686,-1000,-727,1000,-174,-911,1000,553,-785,6,513,84,1000,-209,393,-1000,-135,-788,-1000,-742,-373,-340,1000,1000,-835,-1000,374,1000,40,221,1000,-1000,-778,394,1000,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{-680,-1000,-956,1000,696,523,-1000,-558,-590,-65,-500,-1000,518,-1000,1000,-839,670,939,1000,1000,1000,1000,-149,-400,-178,605,-1000,400,1000,-89,69,-1000,-344,986,643,-1000,1000,-579,999,881,82,1000,-699,-742,1000,-247,-563,206,1000,-1000,207,1000,967,1000,-319,-201,-64,259,-291,-812,-1000,-778,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{-1000,387,165,335,-228,761,-501,82,255,1000,-1000,170,1000,-499,1000,561,-965,-987,389,1000,1000,823,138,1000,-419,80,-1000,-1000,1000,-250,340,-1000,-822,827,-129,-662,279,160,-1000,-318,1000,434,649,-742,-243,-205,-1000,369,488,-371,694,422,526,-344,-360,1000,-194,-556,595,52,-1000,143,1000,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{-1000,401,297,282,124,882,-467,-145,672,1000,-510,-96,487,-636,1000,122,-848,-1000,545,968,1000,298,918,426,-82,324,-1000,-528,1000,-1000,-335,-617,-1000,-34,-979,-711,376,464,-1000,-449,1000,423,1000,-571,852,-106,-977,445,328,-768,212,527,751,315,-1000,687,-190,-79,275,832,-164,-703,801,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{1000,-1000,-211,580,4,248,-857,591,-1000,-1000,-1000,-1000,-991,-203,-692,-919,-68,1000,1000,1000,88,1000,574,-1000,-159,790,772,-1000,-611,-645,943,398,1000,-607,545,-1000,-275,-1000,848,889,381,599,-865,-642,466,403,-1000,-671,810,-1000,-1000,1000,81,1000,-824,-746,677,-1000,-1000,-610,-1000,190,-351,-5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{10,-1000,94,1000,267,-240,-442,-327,-104,107,-822,-392,728,-1000,1000,1000,719,312,322,-9,759,-913,267,1000,641,868,-98,892,297,-40,-796,-1000,-663,628,229,-51,-57,400,201,391,1000,616,-447,-722,510,-75,776,756,-400,1000,-893,699,242,1000,364,1000,-513,-372,1000,-952,-737,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{-944,1000,964,-1000,-382,292,613,-1000,1000,1000,-1000,440,-132,891,211,1000,-1000,-1000,-34,-120,-917,-398,-503,1000,-205,-1000,-1000,-1000,237,-1000,-167,130,692,611,-324,69,95,1000,-1000,-649,381,-148,-865,-194,-1000,813,-1000,724,275,-338,1000,-295,607,-614,54,695,-1000,-1000,1000,1000,-694,-161,-236,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{-1000,-377,-470,-770,-209,981,1000,-996,1000,1000,852,51,-1000,207,166,166,-679,-1000,801,-316,-656,445,794,-1000,-205,-478,1000,-819,-582,-994,1000,-489,-85,915,436,-437,95,786,-1000,1000,-1000,-300,1000,-264,602,902,-1000,504,275,428,429,638,464,-614,-639,-606,-1000,146,-153,-218,-964,-161,-626,-345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "add(org.jsoup.nodes.Element):boolean",
            new int[]{-1000,-1000,69,561,588,-678,-592,86,-1000,-1000,1000,-407,-1000,-173,-545,-1000,-90,1000,869,1000,1000,875,1000,-1000,1000,1000,1000,1000,-1000,110,-38,-722,477,1000,1000,-1000,643,-1000,1000,362,-585,501,-1000,-711,683,467,1000,11,850,-1000,-1000,1000,-444,1000,-579,-805,1000,1000,-1000,-1000,-1000,93,-146,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(int,java.util.Collection):boolean",
            new int[]{-1000,555,-1000,-1000,-1000,-944,451,235,887,6,835,1000,1000,-652,270,-158,-1000,-383,1000,-734,700,-1000,-336,-118,-1000,-668,-100,-198,1000,1000,153,186,1000,-431,-987,1000,-1000,-1000,-1000,335,-994,670,-1000,1000,-1000,-1000,1000,776,-874,973,242,-160,-753,1000,-1000,-1000,694,-166,1000,396,912,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(int,java.util.Collection):boolean",
            new int[]{-23,216,117,-647,-796,-495,-236,836,-406,-436,470,-457,-329,-286,-295,-514,-508,-450,-121,-178,-17,-241,289,570,-598,764,707,-40,-139,224,-141,-533,20,-1000,938,-23,264,-543,-1000,333,-961,459,192,1000,1000,680,-163,677,255,-138,1000,663,447,-505,-872,304,-110,17,942,71,837,-909,-574,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(int,java.util.Collection):boolean",
            new int[]{-1000,849,-455,969,-257,1000,1000,-583,-220,1000,-468,-1000,-313,-699,-1000,1000,1000,610,-1000,-330,432,-896,925,797,-1000,1000,6,-1000,-1000,821,353,371,635,103,-305,893,-700,-1000,1000,-742,215,686,236,923,1000,650,13,1000,-1000,-192,397,-1000,1000,1000,1000,92,215,886,-45,402,-1000,801,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(int,java.util.Collection):boolean",
            new int[]{-1000,-233,571,366,-468,838,993,126,-146,571,-515,144,-398,-756,-1000,1000,1000,401,-1000,721,594,-789,485,817,-1000,384,104,-377,-1000,744,186,229,379,-883,-565,912,-216,-1000,1000,-1000,260,570,-39,593,921,727,291,704,-694,-670,579,-770,1000,1000,694,-739,-724,515,-74,-302,-1000,862,830,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(int,java.util.Collection):boolean",
            new int[]{914,1000,709,1000,256,1000,-598,-158,-732,1000,-97,-1000,-817,-379,-650,-364,1000,27,-1000,-72,1000,1000,-1000,782,156,1000,191,-771,-1000,-777,-794,988,520,-273,1000,-957,676,1000,308,-616,309,-813,659,-329,1000,862,31,-578,700,-603,716,417,1000,-1000,1000,1000,-1000,168,-861,-550,108,-962,135,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{1000,-900,109,588,67,-421,1000,612,894,1000,-428,-346,273,1000,-92,417,261,-238,1000,1000,563,-1000,-1000,1000,362,-1000,1000,1000,652,556,689,357,782,1000,-1000,85,1000,-482,-146,-802,-1000,-967,85,-1000,906,670,640,-843,-1000,217,-1000,-887,-913,-996,358,-444,607,-1000,-85,460,-938,1000,-631,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{-1000,-361,338,-1000,1000,830,-918,-1000,564,649,-862,762,-72,-916,-1000,1000,1000,1000,-346,-290,428,-85,-1000,821,-1000,-1000,-1000,898,1000,1000,650,1000,1000,-575,1000,-1000,-1000,1000,1000,963,1000,1000,-585,-1000,-667,1000,141,-990,-1000,291,1000,1000,1000,1000,523,-1000,-1000,-1000,428,-1000,-1000,1000,-693,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{-749,964,408,-840,-655,-900,-888,702,-408,656,-685,-661,-805,344,516,532,466,466,918,504,281,-272,566,478,844,368,-167,-609,-713,562,-727,-462,-549,-933,-384,776,-630,-188,728,-161,425,-195,-267,99,914,91,-824,-358,-121,753,91,-29,416,982,166,-225,-575,95,-211,284,771,-964,705,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{716,-705,-118,827,-1000,-532,793,1000,-283,46,47,-371,438,376,483,-1000,-596,-903,1000,176,323,519,1000,1000,823,-227,574,447,-546,-152,603,-1000,-348,-57,-1000,615,1000,-284,-412,-1000,-1000,-1000,-320,581,1000,1000,79,106,1000,-335,-1000,-768,-923,-1000,-736,801,102,1000,-1000,114,556,-288,550,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{1000,1000,-16,267,1000,-1000,578,-79,44,1000,455,-1000,-258,62,-92,769,55,1000,-63,892,1000,-1000,-803,1000,362,-1000,-708,-667,403,556,689,1000,1000,871,498,-924,-767,-179,-115,-131,-517,-358,-989,-100,225,-1000,-1000,-707,-1000,363,-1000,-358,-240,-1000,1000,-888,-317,-1000,1000,899,-938,610,859,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{-1000,1000,409,47,1000,237,-1000,-432,-1000,-568,720,-428,164,-1000,-1000,131,-416,1000,-1000,-827,668,172,989,158,418,718,-1000,-1000,-131,35,-882,1000,582,1000,1000,-1000,-1000,287,-322,437,1000,248,-1000,1000,-41,-1000,-1000,122,-151,-1000,489,9,163,-1000,1000,-310,-1000,523,1000,-129,-816,-777,1000,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{1000,909,1000,-67,98,425,-354,998,-371,-633,-428,-215,142,-281,736,-1000,-1000,-903,1000,-438,472,-457,856,-239,516,-1000,1000,576,652,-750,551,-1000,-709,-920,-854,314,1000,-635,-753,-324,634,-207,-294,245,415,1000,444,488,1000,-969,-762,-1000,-913,-1000,147,643,607,584,-922,-111,397,-1000,691,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addAll(java.util.Collection):boolean",
            new int[]{-1000,807,939,-477,991,133,-400,-205,-947,-138,-203,-58,162,-837,-1000,386,-35,1000,207,-424,284,250,-783,1000,-72,-913,-708,-1000,-153,593,-934,987,752,-511,600,238,-767,459,213,-512,400,94,-855,-401,834,-1000,-1000,-448,-840,-15,400,321,339,397,603,-610,-1000,-622,134,-1000,-853,454,177,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-386,1000,-361,1000,-150,220,80,932,896,-391,-63,-477,1000,239,168,1000,46,-288,-798,-687,-71,140,-1000,65,388,263,154,-1000,777,-1000,1000,527,953,-1000,199,97,-774,-1000,-177,-932,504,-535,-1000,723,830,548,967,26,130,-169,-42,-574,-732,-547,-1000,229,911,1000,750,-1000,-625,1000,-103,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-14,-969,-448,186,-1000,10,556,1000,1000,-504,340,-1000,-845,778,565,10,732,-1000,-1000,-121,-535,254,1000,1000,132,-485,681,1000,183,25,428,1000,-1000,1000,-1000,532,-363,-807,-426,-514,67,-535,-59,-19,-744,-387,1000,-955,-100,251,-1000,-397,394,-945,269,-75,-1000,-900,-1000,829,-606,-70,-524,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-515,273,-1000,848,-563,1000,-637,227,1000,1000,-1000,-233,-891,914,651,807,1000,386,-1000,-245,-268,660,1000,323,-527,97,1000,-1000,1000,-820,226,1000,-736,-582,-1000,-52,-415,-1000,-480,-1000,1000,-1000,-1000,1000,848,1000,1000,-1000,646,653,-1000,690,-791,486,718,-571,-183,-720,365,-210,476,599,-848,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{597,368,164,276,29,-125,392,-423,240,-788,411,-600,307,116,1000,840,-550,772,-132,-70,999,306,-109,-328,-172,649,758,-1000,-1000,158,609,-156,1000,-959,904,906,558,-805,-208,77,-603,152,-222,-38,1000,-160,298,-266,689,-76,890,-384,-994,-538,216,-746,399,-407,1000,-233,370,1000,-1000,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-284,466,-620,469,429,128,-661,524,750,365,-643,131,543,554,-242,689,-203,432,-325,-101,-147,-646,-727,1000,1000,108,380,-982,708,-1000,-255,534,551,-605,-208,336,-1000,-583,363,421,205,-331,-427,763,-268,407,32,319,412,-660,-479,-370,-131,-92,-882,651,754,516,-124,-642,195,755,-813,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{519,-772,-352,-432,29,342,259,7,1000,129,606,-1000,-588,378,1000,492,243,143,-160,-702,610,526,-1000,208,-571,-781,758,-1000,-512,468,428,-156,393,-1000,904,540,738,-1000,-988,196,394,-204,233,-275,869,78,543,-400,862,-76,642,-384,-1000,-687,619,-1000,74,-1000,642,394,370,1000,-801,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "addClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{121,175,-428,-545,-46,170,-214,-261,541,-893,268,-88,-427,597,770,465,-347,1000,-24,-418,1000,97,1000,-860,21,546,1000,-1000,-244,622,588,-59,1000,-971,720,127,-68,-863,203,1000,-340,20,-339,0,615,505,91,-411,1000,-348,861,-687,-932,-785,368,318,239,-477,937,155,703,1000,-1000,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{545,-613,-204,664,-280,-432,1000,594,-1000,61,761,-254,346,-325,-1000,-587,417,1000,-136,-1000,-461,1000,-137,-418,-884,-300,-938,-455,-1000,762,-734,-1000,-479,-777,44,-1000,-20,250,574,-1000,-1000,-726,487,-848,-27,-607,457,33,724,-940,352,395,158,1000,1000,900,-14,-176,-381,764,235,-1000,474,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,24,-226,683,-533,-519,157,435,-551,-1000,215,242,340,1000,-235,-884,821,536,1000,1000,950,459,967,-617,559,-1000,1000,-247,-69,347,-1000,-448,-736,-481,178,771,-361,-685,50,-105,588,380,624,509,567,-1000,274,-369,-893,721,508,-486,-227,-626,-1000,-581,-375,249,391,-39,1000,849,546,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,126,1000,229,-622,1000,1000,491,152,-853,594,-743,786,1000,-788,-948,-1000,-58,1000,-1000,-315,44,-212,1000,1000,287,668,490,89,-1000,-1000,-652,-774,163,168,985,400,325,820,-285,421,-1000,77,1000,246,1000,-445,822,-160,1000,-1000,-1000,1000,-1000,-1000,1000,1000,-319,1000,-624,-1000,-392,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{30,-1000,124,1000,4,152,-1000,-444,1000,309,-191,1000,674,266,-1000,-152,675,977,1000,-263,884,-967,280,-529,-367,759,668,-1000,-664,-304,-1000,-1000,-410,-24,438,1000,400,1000,837,-1000,421,247,1000,-359,119,-1000,-801,335,758,1000,-506,-1000,459,-1000,1000,-315,258,759,-609,-1000,-1000,1000,558,805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-505,-721,500,-102,182,-143,-792,1000,-435,-1000,1000,-1000,469,914,675,-357,-792,-215,22,1000,-213,40,-525,770,1000,-1000,-535,922,1000,-499,329,265,-178,-990,175,-624,-70,-1000,-840,632,122,-428,-1000,1000,1000,1000,386,970,-1000,-420,-454,1000,272,149,376,863,192,-326,159,141,504,-496,-733,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{149,-41,1000,906,-556,35,489,-850,1000,-892,-451,-599,-567,570,1000,666,-1000,-355,108,-792,417,-856,-1000,1000,932,250,46,1000,-1000,-5,276,556,1000,-518,-575,166,-1000,55,-907,1000,937,-932,-925,486,406,-634,-539,-487,-192,518,-697,761,394,35,525,-47,-756,236,635,-1000,554,158,608,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-672,-172,139,866,62,-475,-574,-695,1000,-51,-700,245,-617,-408,831,920,483,338,-274,152,729,-795,-1000,-468,-107,71,142,315,-182,421,1000,151,1000,-884,-136,-303,-1000,293,-1000,529,872,744,-175,-609,-80,-939,-222,-1000,482,312,309,1000,445,668,1000,-700,-1000,830,-333,-579,872,352,-1000,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{585,1000,331,-334,83,523,890,954,1000,1000,-482,729,167,-659,-618,50,517,-343,-830,-982,277,-652,-306,-177,-524,-339,-209,77,-627,185,-907,-1000,-354,620,1000,-538,1000,821,-1,-417,238,-169,451,-171,-772,1000,897,-533,76,150,-681,233,1000,62,-910,151,-372,859,-1000,930,-1000,-31,1000,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-1000,439,179,408,1000,761,-530,1000,-489,735,-1000,-52,259,-136,-816,480,23,755,797,-889,146,648,528,-417,455,597,1000,-672,1000,655,-68,-42,1000,-830,816,259,-394,1000,-117,-186,117,-748,-398,201,-1000,537,-64,145,127,-1000,534,-732,156,-507,1000,234,267,-481,-578,793,384,752,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{97,267,-94,1000,149,443,200,-914,224,-251,569,-1000,-995,279,-179,-835,134,91,796,1000,612,1000,695,725,-1000,-755,798,-258,-623,-909,-938,525,-55,207,-1000,-247,-128,-47,397,-544,577,-1000,-164,-947,-717,274,537,-967,900,-256,1000,-476,-97,1000,-986,150,866,803,-654,-442,1000,240,10,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{-310,357,-131,1000,814,443,-165,-583,-163,38,-188,1000,-373,631,178,116,864,1000,1000,950,1000,1000,884,1000,1000,-793,389,26,-595,-1000,-272,525,429,164,-882,244,492,504,-147,-109,1000,-1000,-155,-1000,-1000,-7,825,-802,690,58,968,-584,-331,230,-1000,-490,-248,351,-855,400,1000,-158,-651,-919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{910,-716,239,88,-234,610,864,124,1000,-742,1000,-900,-1000,-328,-1000,-308,750,-98,956,-720,-295,-680,521,225,880,34,336,895,-557,1000,468,-865,-954,835,722,1000,3,722,862,120,-378,423,228,-1000,897,-1000,444,-1000,-39,734,1000,1000,-526,1000,-208,988,766,-5,-644,-1000,752,522,-167,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-20,-822,1000,177,1000,926,-492,907,-703,753,-1000,231,952,-563,-1000,1000,1000,-492,400,-473,1000,684,1000,829,699,861,713,311,475,-1000,525,-543,1000,1000,1000,476,18,-119,516,1000,-336,-398,-36,-499,-979,1000,-491,900,616,911,-258,-594,457,-439,1000,1000,-1000,455,-1000,975,-1000,104,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{-390,883,1000,1000,1000,102,16,38,-815,-242,-234,-984,569,588,-82,25,-1000,603,303,1000,486,748,-1000,-1000,152,463,-375,-1000,92,1000,1000,696,83,-961,-375,-997,-462,-363,705,396,-182,726,-642,-272,-57,-131,95,-768,553,-138,-482,-102,703,352,-261,1000,-66,673,1000,-926,913,500,-872,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{1000,1000,304,986,358,-1000,-953,-87,156,-1000,-974,-1000,172,-117,495,-1000,-880,1000,10,986,330,151,-217,-247,-994,-359,-17,-530,1000,1000,92,-204,15,-1000,628,-527,-299,163,330,816,-1000,346,-1000,-89,-1000,880,-159,-142,1000,1000,-124,112,-255,337,-555,5,-1000,-65,-10,-1000,1000,139,-548,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{-1000,-807,-212,-821,-23,347,499,425,-937,-672,668,-188,-1000,-97,-763,140,778,800,107,-486,-957,-262,-883,-579,-695,-1000,1000,-228,-491,558,1000,1000,737,-458,1000,1000,1000,337,-665,1000,382,200,501,-1000,1000,863,846,898,35,1000,624,363,1000,270,-733,-86,764,-521,-1000,83,-1000,496,-735,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{290,418,1000,404,202,-508,266,-274,-999,-223,-464,-1000,602,1000,-378,317,-1000,-260,-338,1000,1000,-43,-426,-1000,-194,-268,-307,-327,-329,1000,645,-124,259,-352,37,-1000,-485,257,1000,384,-427,1000,-597,374,-340,-442,102,-539,680,204,-813,-118,189,-120,-466,1000,-6,268,48,-1000,726,410,215,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{-976,1000,74,-293,180,1000,-116,109,156,832,767,1000,-469,1000,-328,103,-376,30,1000,-794,-626,1000,-1000,1000,1000,791,370,-692,538,-231,92,995,-1000,-537,518,490,442,-287,0,773,-79,457,-1000,-89,1000,880,667,1000,-309,92,-389,-115,287,1000,-1000,307,-1000,378,165,1000,-629,139,-1000,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{27,143,530,-467,482,779,-537,1000,-830,242,621,-881,699,415,481,178,-545,-109,181,-225,412,1000,-812,-1000,-361,624,-574,-350,-158,421,-81,-77,-149,-886,-563,-1000,33,-105,232,98,-331,403,-1000,-316,-574,-414,399,602,-110,-1000,-676,797,1000,344,-635,371,674,-158,772,-1000,285,499,-286,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{379,1000,674,-616,-1000,-257,-47,-1000,-394,186,-1000,-169,-497,-323,-467,-862,-989,-1000,-113,882,786,142,215,331,-1000,-1000,1000,-152,966,1000,311,-514,162,340,1000,-941,-573,458,1000,1000,-1000,-716,-848,497,-438,998,294,1000,712,1000,-158,-1000,-265,-590,-1000,-110,-617,-685,-110,-403,1000,361,863,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String):java.lang.String",
            new int[]{-990,500,134,-83,-23,-80,-88,-552,-217,26,-109,-31,-946,-375,-227,69,-441,1000,234,147,454,187,-137,537,-361,-70,-21,191,-251,508,1000,-41,-517,740,550,595,420,712,654,1000,-1000,-1000,-71,-107,1000,-2,99,-285,993,193,368,-485,64,-223,474,-86,-442,538,-790,99,179,329,-1000,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,1000,-706,-192,-693,1000,1000,1000,468,-451,-1000,-788,-757,1000,-743,713,613,323,201,564,-545,-495,-678,-710,-3,-621,1000,-107,687,-276,400,521,525,-38,-276,-421,222,-275,7,791,-255,-387,553,453,-157,-84,864,-495,-31,536,-1000,-21,833,-260,-209,-433,630,-889,268,-204,468,374,-205,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,467,-1000,-22,-747,-1000,516,-57,-913,858,457,-270,-266,-358,-745,468,21,-1000,932,20,-1000,-78,303,711,1000,960,224,-1000,1000,-1000,-1000,567,1000,-1000,1000,-1000,-366,-28,1000,906,-63,-1000,1000,1000,859,549,-861,-461,1000,1000,1000,1000,981,-1000,571,-924,-782,1000,787,-56,811,-155,905,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-651,-1000,-746,-424,-863,-346,1000,-868,622,1000,-869,-1000,706,-932,490,-280,-397,536,1000,-974,-239,-947,-294,-426,-329,-552,-714,1000,-390,472,242,1000,-294,197,391,83,77,-889,-564,-434,-80,228,256,191,-805,790,-1000,-1000,-158,378,98,775,-1000,32,-659,265,-1000,431,-275,-470,1000,255,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{496,765,-1000,114,34,-1000,-515,42,-769,1000,1000,850,-1000,-358,130,707,-655,-1000,650,658,-1000,-108,758,-846,210,133,41,-1000,1000,-104,-1000,567,408,-1000,1000,-750,-1000,-874,1000,-247,175,-604,13,1000,1000,598,1000,-296,-376,287,-394,1000,1000,-324,-194,-1000,-657,1000,-198,-85,452,771,668,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,222,-1000,114,-60,-809,390,881,-560,693,391,-118,-783,1000,30,677,-987,503,777,420,-1000,7,245,-1000,210,-199,224,-1000,797,336,-1000,-335,569,-999,829,-50,-844,-615,1000,1000,208,-978,13,1000,1000,-217,879,-482,-376,174,780,929,893,98,-288,-808,-363,1000,-156,-60,214,1000,-61,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{803,-1000,-721,756,629,1000,-738,829,-896,-963,-157,184,-1000,1000,548,-209,328,16,-582,-837,-452,-856,502,-1000,-1000,-507,-19,-962,157,829,-804,646,-1000,-562,-625,996,-1000,409,-1000,1000,1000,144,-235,-525,452,-559,392,275,-777,69,-1000,541,739,-136,-279,-715,-747,619,-1000,-82,1000,551,517,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-389,4,912,-138,-809,-1000,859,-844,126,1000,1000,305,-703,-579,1000,-105,357,538,400,-61,546,813,539,736,529,1000,-562,-326,71,-915,-604,417,-693,876,15,-1000,2,-400,1000,732,-967,685,6,1000,-646,785,322,-226,179,-1000,680,317,-255,602,-225,-536,606,183,-402,358,83,-753,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "attr(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{656,1000,-627,-959,-493,-1000,547,127,-636,1000,770,713,-735,-289,-413,867,-388,-622,685,788,950,1000,1000,513,957,1000,224,-731,-386,77,-1000,-452,711,-32,732,-240,-258,-415,596,-255,-1000,-1000,904,1000,375,-361,253,1000,-434,691,-207,337,611,-660,-456,27,2,413,1000,879,331,110,-433,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-400,919,420,1000,-420,-400,-137,-56,173,1000,-1000,141,-227,-300,197,269,-332,-618,-862,47,-343,343,411,5,-140,439,748,1000,-499,-1000,632,418,-295,773,49,956,-855,400,992,122,30,-34,-22,92,-587,93,399,-485,574,-143,981,-64,-389,-347,622,973,155,276,-231,202,894,500,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-888,-981,-216,264,608,751,476,-173,-522,-240,-676,979,-362,-707,-993,881,-675,-962,627,67,-492,-380,864,846,902,171,99,-845,-459,997,-678,-384,-545,341,174,-190,27,288,634,121,-668,-22,517,-110,927,468,241,-929,368,805,-906,-883,-911,-812,-358,-635,-863,454,836,-113,801,-470,-109,422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,1000,759,74,-22,1000,1000,883,-807,-533,-106,279,-898,366,-1000,-710,882,1000,-855,-197,361,-486,-603,-93,1000,629,1000,-370,-847,-238,-77,639,-265,55,812,-505,294,251,-1000,-661,1000,386,-1000,313,646,143,1000,1000,451,730,633,83,78,-898,925,539,-40,-930,-822,-980,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-635,-327,106,-887,-252,584,296,984,373,-606,243,-901,348,-252,344,-62,700,57,-901,520,339,-65,-286,-197,-264,453,566,-346,82,-762,-375,666,-775,-539,457,-623,-232,787,617,773,-606,489,-135,-665,676,95,-51,-621,-570,-849,-531,911,-988,-963,737,837,873,66,485,990,-866,572,763,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{261,1000,-82,76,855,-24,-150,-869,-52,388,1000,-665,516,992,-1000,-351,676,-113,-452,-1000,189,-428,1000,196,662,998,341,125,597,-560,-1000,-44,292,-447,1000,1000,-106,207,56,-673,1000,440,-219,756,409,-349,744,-1000,331,923,-353,1000,776,-688,-1000,-1000,952,1000,416,-1000,426,682,500,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{451,-505,1000,-541,753,-383,-1000,666,1000,178,969,-493,1000,1000,-1000,216,-217,-454,-1000,-1000,252,148,137,-1000,-1000,543,-381,222,-1000,-742,-1000,-1000,308,271,1000,1000,1000,423,-977,-1000,-577,-891,35,1000,-1000,-784,-532,-372,1000,-1000,-133,1000,1000,-265,-1000,916,695,-774,729,878,-1000,100,-960,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{130,19,71,-184,989,-420,140,-800,431,-564,-573,-960,889,-40,205,-849,-26,71,408,-230,521,942,568,879,-77,397,-680,110,762,-711,-215,748,283,-850,764,653,365,-561,-267,211,275,835,-621,851,-272,185,-359,-897,-426,975,-976,679,-267,462,363,674,965,984,-883,-413,485,397,817,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-128,-709,-167,679,602,-283,1000,347,-212,-295,-590,552,-838,-1000,301,-394,686,412,798,456,-194,-877,-983,30,906,-829,1000,-628,-96,-850,339,-884,485,-342,-324,-210,231,1000,-490,489,-542,-252,-336,229,176,-30,160,1000,-193,948,624,-791,-150,115,1000,351,-919,720,-402,-945,-34,-360,194,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{649,-400,-356,-83,798,-786,-752,-875,274,871,-137,-867,141,631,-97,41,-275,-351,-618,400,89,-386,723,122,343,-400,-12,-331,1000,-974,-675,-995,418,-641,124,1000,-854,207,98,-673,-395,160,317,355,381,-412,93,-579,-1000,462,-1000,153,823,-609,-1000,-1000,1000,155,276,44,927,421,372,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{443,930,810,241,310,22,312,248,-134,198,537,-826,-634,533,-1000,-205,890,1000,-758,-628,501,-683,1000,303,1000,-434,1000,467,1000,-805,-907,1000,623,1000,904,439,704,-467,-907,434,1000,717,-816,-120,495,-711,882,-493,-1000,1000,-105,694,228,-1000,-182,1000,1000,-411,-412,-807,-820,1000,1000,915}));
    }
}
