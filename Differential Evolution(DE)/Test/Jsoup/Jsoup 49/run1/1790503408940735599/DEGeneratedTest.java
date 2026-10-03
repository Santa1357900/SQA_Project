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
            new int[]{38,-551,586,-285,931,-787,325,561,-937,-649,-385,-485,53,240,879,-654,439,-935,52,482,-138,-929,513,343,-280,227,-370,927,-444,457,919,-654,-407,-47,-491,143,507,471,-190,-627,-238,956,45,670,-312,364,688,852,-179,148,740,921,980,473,-761,-202,-942,-16,-560,574,572,679,-288,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "absUrl(java.lang.String):java.lang.String",
            new int[]{719,-1000,792,605,734,-1000,1000,922,-446,-1000,113,-534,120,-236,473,-655,-268,500,418,1000,206,124,1000,-428,-1000,-188,-567,908,-673,200,1000,-1000,616,61,-316,-606,551,156,135,-1000,288,1000,646,-862,-151,995,1000,767,293,188,1000,594,-43,1000,-128,224,506,297,173,-226,124,1000,-631,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-605,-701,-476,699,56,6,-416,773,823,871,53,908,-870,-51,153,-121,922,-998,399,352,-574,342,-996,634,741,942,938,548,-207,844,-975,-240,-346,-528,774,681,-986,906,377,-408,649,-637,262,-924,189,-801,917,3,-877,-401,-690,330,-916,772,-830,812,-269,-48,628,349,237,-183,279,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-718,669,-260,214,708,-312,-357,94,-577,871,-587,-189,232,334,1000,-517,-466,-1000,-697,602,1000,-966,-1000,1000,517,280,569,1000,-207,-20,-1000,1000,-245,466,720,-390,1000,-38,888,-1000,837,-1000,-1000,-851,767,-729,1000,16,-1000,-135,-301,-135,-183,163,-308,204,436,-432,1000,-1000,-1000,-540,365,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-422,278,-1000,455,-961,-971,949,-215,-587,1000,1000,-1000,636,-1000,-984,767,-180,507,-103,-1000,910,-925,-431,-1000,241,-131,1000,-590,407,-660,629,-698,-33,-365,-99,-190,-1000,118,632,92,-435,480,-637,1000,1000,-1000,1000,544,-1000,385,1000,1000,-155,854,1000,-1000,-1,-84,-6,-29,-253,-861,289,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-653,310,962,86,140,-210,-1,956,-127,-446,-193,579,391,-36,-2,36,-507,575,-228,-410,-559,968,-52,871,-290,-653,397,-986,546,377,-880,664,193,716,605,187,-362,-965,254,-876,600,889,738,958,-837,-214,915,599,895,4,602,-358,-358,294,928,419,-765,440,274,141,-703,879,-148,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String):java.lang.String",
            new int[]{603,-347,681,-747,-551,402,-858,849,646,-938,257,735,585,-988,848,-886,-968,-227,-36,805,-740,-148,-817,185,-758,148,-651,-138,-470,-206,718,-550,-499,-683,591,130,365,122,-782,90,483,490,-996,-622,389,488,-144,227,-780,-477,899,495,-838,610,-290,-509,29,849,751,-819,77,107,710,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String):java.lang.String",
            new int[]{-242,60,-328,1000,1000,386,324,-501,-1000,210,977,-600,-1000,398,-1000,-196,-215,-975,983,1000,768,409,828,-310,1000,-1000,1000,260,-444,480,-412,-35,-752,-827,168,-179,-1000,-431,180,1000,-466,688,1000,1000,-1000,-988,-1000,617,1000,1000,-1000,-1000,1000,-480,-24,456,45,-1000,-1000,670,792,-1000,420,-665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{-181,-452,-881,-427,823,500,830,-937,-173,-105,-877,399,216,504,-463,-984,-202,714,-937,114,260,802,624,444,709,-905,34,630,-767,425,-516,470,857,654,-347,950,397,808,-636,-670,785,334,-587,-391,-779,-631,-351,-126,-836,533,-323,658,-388,-252,-328,249,376,832,696,89,-858,230,-359,-77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Node",
            new int[]{-870,408,-600,115,-626,-430,-106,-515,-339,970,-677,-186,725,-504,180,350,-390,421,199,648,-873,85,-749,-265,-562,-430,555,-418,41,-178,89,899,-140,-844,-862,-403,313,708,768,-128,-752,496,-340,778,470,35,249,-551,191,-858,390,-377,216,498,859,-954,-85,-165,945,-514,-903,187,-905,582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attributes():org.jsoup.nodes.Attributes",
            new int[]{-290,355,-1000,-1000,514,122,444,206,600,1000,1000,1000,-395,1000,-625,96,-1000,-729,-500,577,-27,418,688,-38,961,1000,-947,138,1000,-931,434,-181,1000,504,-193,-191,-1000,668,-1000,946,201,1000,-340,-413,998,-889,963,-1000,1000,-1000,-341,-242,-467,-80,-1000,-1000,-379,-931,-26,319,-1000,-792,1000,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "attributes():org.jsoup.nodes.Attributes",
            new int[]{-977,-403,-707,249,525,-570,-264,-240,-504,-1000,1000,709,1000,-167,-1000,1000,0,651,641,8,162,14,865,759,-1000,-1000,0,-1000,723,83,178,-914,-1000,1000,201,-224,-943,1000,13,1000,45,-1000,1000,-1000,-786,-63,-180,669,-608,488,-1000,-467,700,-1,0,1000,-424,-238,-581,-70,818,635,530,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMC40NjI=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "baseUri():java.lang.String",
            new int[]{198,-1000,-630,-479,-953,-621,-207,488,-817,393,1000,-935,-1000,466,672,-291,-974,32,-682,-731,1000,-1000,-538,-1000,-429,913,839,-40,-704,-766,-324,-1000,-366,-1000,-1000,-140,-1000,817,-227,905,-1000,1000,-535,-539,-435,-606,1000,1000,365,349,-743,216,-302,816,458,626,186,-467,-675,1000,-929,-1000,-839,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:NTk3LjgxMg==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "baseUri():java.lang.String",
            new int[]{503,-685,-472,-751,-1000,-459,629,1000,-1000,-1000,1000,-502,-968,1000,1000,-262,-998,136,-11,175,22,-181,165,-574,-133,-361,-321,-117,-496,-81,-156,-702,1000,-1000,-683,597,-1000,812,24,1000,-978,730,-212,-354,-825,-269,291,1000,-1000,1000,-964,460,-447,1000,-5,135,-361,-793,-635,844,-888,-787,-536,-989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{322,-473,113,495,1000,709,-28,-151,264,-212,-663,541,-500,457,-453,367,53,-332,199,145,577,-200,87,246,-681,-352,818,613,987,-416,-847,1000,-431,-910,-289,55,147,-652,455,-423,586,330,-400,98,-242,84,-66,-182,-904,880,-415,-91,280,-11,202,-55,-21,-419,-236,-193,142,-1000,-124,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-325,-7,-1000,-717,-207,72,1000,-1000,354,1000,119,1000,246,81,627,-853,427,-362,-1000,226,570,-896,-196,1000,145,684,-1000,920,-517,-260,-415,1000,709,-785,-1000,-1000,603,-296,-993,850,132,544,-847,809,-1000,-675,693,1000,633,939,-373,832,-327,-734,1000,-1000,-918,-867,311,-177,618,-787,-1000,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{334,-224,498,474,210,453,-929,-379,-1000,679,-139,52,230,-186,228,485,270,301,949,1000,-129,-1000,537,-100,-578,-742,48,-574,352,-282,-696,462,-1000,1000,863,1000,-344,884,-278,551,-24,1000,-722,-17,-639,361,-42,546,-16,-55,114,-1000,-1000,281,31,-1000,753,-161,-736,-441,360,656,342,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{-181,-232,-148,-294,621,-522,-507,138,-504,783,-809,-454,-139,-696,-762,719,977,-691,-995,22,-833,-588,113,-565,-422,917,-19,864,227,320,-413,-950,-734,-226,726,-801,623,-917,-419,-211,500,218,989,-72,-457,251,-565,104,934,-868,503,-795,-854,777,303,-729,-357,-429,185,615,423,-408,-14,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Node",
            new int[]{126,-598,-321,618,-807,-209,-1000,437,1000,156,960,283,411,-5,-30,-343,-1000,480,1000,21,909,1000,548,353,-669,81,-1000,-896,91,-1000,-947,-238,97,-807,-1000,1000,-433,1000,-330,955,71,-1000,-766,-596,950,1000,651,-375,-966,87,216,-753,417,-206,411,163,1000,828,-1000,-1000,-417,367,-1000,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNode(int):org.jsoup.nodes.Node",
            new int[]{-658,661,24,1000,1000,643,-222,906,-1000,903,712,-560,154,819,864,-393,389,-892,-787,-258,558,-1000,965,-218,611,1000,-1000,-1000,1000,-199,99,660,101,182,-322,828,-355,755,1000,-123,1000,-144,224,-845,919,-48,911,1000,-173,143,185,-377,-763,-1000,110,138,-68,-210,130,-119,346,-371,506,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNode(int):org.jsoup.nodes.Node",
            new int[]{-881,832,349,-889,-624,293,892,-819,231,-349,869,407,-81,862,151,-651,-487,-829,657,782,836,402,991,787,158,-2,-73,-449,770,-820,217,899,323,499,-718,-37,679,-504,503,-622,944,-838,-11,-59,345,-504,-969,-274,655,-463,-278,352,-13,-16,654,287,945,722,-968,873,368,-562,-901,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodeSize():int",
            new int[]{154,-389,-1000,366,592,1000,-86,486,902,1000,-1000,-134,-766,881,-599,1000,-1000,150,-203,-466,1000,441,127,-345,1000,160,-208,215,316,901,-546,546,445,139,706,800,1000,-858,44,116,745,902,-244,460,-46,878,592,159,-730,-1000,-1000,-753,1000,-623,502,-1000,232,-541,304,-640,61,-367,651,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodeSize():int",
            new int[]{-301,267,-399,282,178,615,642,-579,518,-158,775,-456,567,-346,72,587,760,-613,9,881,867,-727,574,-439,-622,825,850,-27,-764,-118,753,191,38,594,218,-127,83,564,466,43,-357,725,220,137,-268,115,-297,667,279,-351,-861,173,116,241,39,-188,449,-820,-947,-116,-366,537,-587,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodes():java.util.List",
            new int[]{-778,-443,1000,1000,-145,-427,-441,591,899,442,117,-430,-1000,1000,60,647,24,-1000,108,538,1000,-1000,1000,247,-193,-1000,-9,892,972,1000,-235,-1000,1000,816,-1000,781,-562,-1000,-771,1,-259,1000,-1000,-286,-575,294,-1000,993,940,-382,306,-461,943,-509,1000,402,181,110,367,497,198,652,-176,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodes():java.util.List",
            new int[]{483,113,719,354,779,-692,1000,842,1000,531,-99,-1000,370,-1000,154,1000,899,-1000,1000,877,-220,409,191,467,-238,-903,691,1000,150,-660,-659,-742,-474,799,-1000,-331,183,-1000,148,380,-344,609,323,-179,-153,1000,688,170,-310,-759,-507,-476,831,473,890,-500,-321,-524,480,-207,-1000,-733,-432,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodesCopy():java.util.List",
            new int[]{375,-330,780,563,99,-790,-3,-326,-660,-849,-984,-107,-22,568,-70,-187,-858,-941,-755,-747,-460,493,752,278,849,726,-519,264,928,660,695,-575,-303,399,-404,358,268,586,-418,-697,-881,-480,-276,59,-55,-877,-562,-170,192,-569,271,-428,-792,-67,-834,-98,458,747,605,-929,-689,-546,645,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "childNodesCopy():java.util.List",
            new int[]{38,1000,1000,136,-1000,378,-666,-249,-712,1000,723,1000,72,-907,239,628,901,-270,1000,293,-101,-1000,-908,-33,-339,-1000,-292,48,-1000,-635,522,306,369,-1000,1000,-822,-710,387,201,1000,917,875,1000,684,-1000,-183,-398,783,-1000,-190,-1000,96,1000,457,377,-87,-885,1000,-979,1000,-1000,-675,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "clone():org.jsoup.nodes.Node",
            new int[]{-549,-994,899,-753,100,-668,722,-195,-250,707,-662,-249,-507,-766,-553,-604,-377,-760,-218,-258,836,-711,-334,795,-451,88,-992,-281,-698,509,760,-811,-243,771,534,-512,-847,-59,-392,-568,-756,330,852,-937,-258,135,270,-851,38,-308,-246,-940,-369,128,-539,-699,-935,-179,751,123,424,123,-972,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "clone():org.jsoup.nodes.Node",
            new int[]{854,1000,-145,-1000,-1000,-450,762,168,-435,272,192,-972,-114,1000,265,451,-1000,-712,-1000,-1000,394,-36,-914,1000,-1000,1000,1000,-1000,-240,882,-203,-1000,1000,-1000,113,107,-1000,1000,-1000,-692,599,256,-516,1000,-1000,-1000,-550,-963,598,870,1000,1000,-1000,1000,1000,279,1000,-205,1000,-1000,-1000,-890,-913,766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{-133,-1000,347,-1000,-1000,1000,220,-423,902,302,-459,-59,-632,-1000,-443,-142,-193,281,179,214,807,770,50,-626,-329,-1000,1000,-827,-1000,-613,-73,-255,302,-129,-460,1000,-1000,-764,-462,822,-700,500,827,581,707,478,-1000,389,181,-805,-562,-469,646,-500,-1000,-84,-998,164,1000,-410,301,-112,757,-673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{426,400,-617,415,1000,-671,-381,821,-538,-1000,448,-452,-355,912,583,-161,-142,1000,1000,321,85,-778,-874,589,874,971,-1000,639,-253,-299,-584,930,-560,-1000,521,-341,500,-986,-574,-1000,-424,107,535,-672,-263,-1000,1000,757,758,1000,-656,1000,764,-679,1000,-432,767,-291,-126,-909,-18,-402,-1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "equals(java.lang.Object):boolean",
            new int[]{-369,756,329,-1000,-1000,703,1000,-474,-735,390,1000,-240,731,98,777,-1000,1000,567,-36,-668,-70,823,-690,89,915,-1000,216,-34,950,-936,-1000,508,464,171,-831,-85,1000,99,-11,600,296,-249,341,1000,-802,431,-260,-396,236,-514,-18,-18,-833,-1000,85,-111,-1000,928,405,112,-1000,592,979,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "hasAttr(java.lang.String):boolean",
            new int[]{-365,-433,461,-512,514,436,-933,-542,594,71,-954,743,-230,-782,-157,-949,796,-490,-440,230,520,285,-400,-985,320,-323,232,-827,-463,456,-934,280,-779,347,512,-610,664,-90,963,-23,-123,422,-337,-284,-605,-303,1000,872,662,-749,307,418,-440,-142,175,-703,637,114,-262,-263,68,408,-567,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "hasAttr(java.lang.String):boolean",
            new int[]{-106,1000,1000,1000,1000,312,437,-819,-386,-68,1000,-1000,858,-1000,1000,-1000,278,1000,-828,400,980,-396,-1000,542,1000,-394,156,1000,-346,-445,1000,-975,1000,-1000,733,1000,-912,1000,-411,838,-1000,502,356,-323,1000,1000,400,-1000,135,-1000,1000,1000,113,200,1000,-810,732,-643,-414,-1000,101,721,878,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{-234,519,619,-46,424,732,-452,322,-338,443,470,-286,-263,376,725,1000,1000,400,445,-56,990,-553,-218,-1000,1000,-249,963,1000,155,533,144,471,1000,-320,400,66,550,313,-685,1000,583,-164,-393,213,448,395,-400,-491,554,-191,162,-1000,-652,-120,-22,530,-400,-1000,-745,572,15,-531,297,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{-38,1000,-1000,-467,301,732,782,836,-407,-978,1000,211,-585,-869,815,32,-813,84,654,-769,-1000,-553,-618,672,354,927,-433,1000,-4,-704,-620,779,-978,-320,400,1000,926,-256,-125,-895,-747,-276,471,-449,-898,-626,288,908,156,-7,-201,959,-255,959,-350,531,-373,1000,180,-5,-200,-208,-352,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nextSibling():org.jsoup.nodes.Node",
            new int[]{-669,-161,-993,613,-896,-257,-130,845,659,81,356,-736,-789,-124,585,-812,-470,824,260,-811,657,34,-640,-422,-396,-169,709,755,346,508,44,900,-334,337,-42,-304,238,-332,-503,-917,-314,562,-975,-610,324,-9,512,-355,-242,-611,376,316,-376,84,-352,-701,335,976,358,-402,-89,-291,-219,-229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:I2RvY3R5cGU=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nodeName():java.lang.String",
            new int[]{-449,-99,-847,-738,-226,-724,305,688,-647,37,265,192,-840,-133,-979,172,-782,414,145,38,960,-54,-89,-232,61,-33,-951,579,-890,810,-890,-675,-278,-50,-254,-721,471,574,-926,-17,502,814,741,-941,589,-20,306,-984,-19,-901,117,403,456,-881,-11,959,669,-732,691,815,82,199,466,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:I2RvY3VtZW50", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "nodeName():java.lang.String",
            new int[]{138,-652,-385,-555,-905,-1000,514,1000,-753,738,180,157,-888,731,-901,532,-58,5,-680,-535,1000,-511,-442,394,488,-487,-824,284,-991,519,-1000,-859,105,-776,-84,-449,994,555,-1000,1000,651,547,640,-1000,339,-206,205,-470,1000,-1000,123,-469,462,-1000,-973,1000,1000,-1000,1000,14,-736,-193,1000,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:PCFET0NUWVBFIC04MTQgUFVCTElDICIweDgwMDAwMDAiPg==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{-325,-1000,35,-814,-893,-1000,6,-16,146,-372,-1000,-235,-800,1000,-893,814,296,-526,141,-462,224,1000,331,-1000,-575,1000,-1000,1000,-666,533,-401,365,-1000,288,1000,-106,-106,-526,-738,-683,-280,741,-681,-366,-1000,-716,452,910,-947,-508,1000,-119,-1000,600,306,113,519,-425,756,-148,166,-689,255,412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{-134,536,-249,-218,877,-428,-389,836,175,-6,571,-870,775,-82,-524,243,-56,-906,491,186,177,121,455,124,-22,-25,12,-631,-602,496,770,36,-942,215,150,363,964,34,-192,741,797,-362,570,781,552,247,667,-627,-572,340,705,624,-329,319,320,802,733,-277,-276,971,-474,592,472,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:CjwhLS1oNjRpbEtmK3NwZV8JSlxELS0+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "outerHtml():java.lang.String",
            new int[]{108,-37,-33,-73,835,-384,-906,1000,75,-408,21,827,-624,-222,-682,664,-57,-1000,566,258,565,39,364,-132,-412,441,-328,-380,-1000,349,455,-55,-1000,-265,105,376,547,457,-243,-965,422,260,505,1000,38,131,183,-247,-1000,773,275,629,-532,54,232,189,1000,-130,371,1000,-719,-16,585,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{938,-119,1000,944,324,130,-206,-417,-413,-777,-433,718,670,706,440,-1000,793,-398,348,-71,-959,1000,-1000,388,1000,-362,148,-210,-293,-398,738,-353,908,-838,144,885,-1000,-482,-308,1000,21,-231,157,-278,-1000,248,-887,405,-816,-346,-384,975,1000,-116,-370,-1000,1000,-756,750,-282,367,-773,690,-221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "ownerDocument():org.jsoup.nodes.Document",
            new int[]{-221,-874,641,1000,531,-345,54,-1000,-1000,-377,8,476,-891,-923,210,-706,-671,-764,-173,-213,-580,374,-101,693,-2,93,-781,1000,-1000,302,-516,557,-778,-93,-297,18,746,1000,-521,763,938,-98,657,-116,-255,-589,-451,215,1000,-261,-200,500,1000,-1000,-593,-156,443,41,-468,-141,887,-723,1000,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parent():org.jsoup.nodes.Node",
            new int[]{39,1000,700,-1000,-1000,-22,315,-664,-1000,-694,-506,741,-1000,-406,-18,549,971,512,-101,399,528,-397,-306,1000,652,1000,-1,-1000,584,1000,459,-825,-1000,266,115,-857,809,-511,61,-1000,-617,-188,1000,-1000,345,76,86,798,-983,-16,-1000,440,-1000,652,-821,1000,-142,-308,202,233,40,120,1000,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parent():org.jsoup.nodes.Node",
            new int[]{942,1000,1000,-997,247,1000,-227,368,487,875,406,105,-1000,1000,1000,303,346,-1000,-1000,-371,-1000,93,-584,194,-35,-252,-44,493,1000,32,673,-126,-1000,-1000,555,427,87,859,434,-410,-1000,116,3,265,755,-1000,-477,-337,1000,-943,-743,37,770,327,155,-491,-1000,-1000,-45,589,-70,1000,652,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parentNode():org.jsoup.nodes.Node",
            new int[]{910,172,-1000,-736,1000,1000,-422,340,287,-1000,-1000,709,302,-965,829,581,-1000,-141,-663,1000,-166,490,-364,1000,438,-445,-212,-409,-1000,-224,36,-1000,-725,-531,-647,655,549,1000,-236,1000,-1000,388,107,-593,-1000,722,-782,882,658,-1000,1000,1000,1000,-676,-756,-1000,1000,-1000,1000,-366,1000,-521,494,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "parentNode():org.jsoup.nodes.Node",
            new int[]{719,865,-403,1000,761,45,-211,-1000,-1000,-117,-277,301,-462,-683,1000,725,-607,824,169,-434,340,-660,520,-1000,1000,859,523,956,-576,-390,846,-899,-62,243,347,-454,-460,1000,-1000,-78,-559,1000,-1000,-27,-1000,1000,186,962,380,28,-80,784,-763,-932,345,-418,-183,22,584,-665,-568,-1000,-1000,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "previousSibling():org.jsoup.nodes.Node",
            new int[]{834,-951,-831,-904,-672,108,-427,-17,1000,-614,287,109,485,-1000,-1000,-395,736,222,267,1000,-200,1000,-241,676,-511,127,651,822,1000,-221,1000,558,-88,-506,-255,390,1000,-892,226,-1000,-711,1000,-268,-1000,41,-1000,-754,1000,-1000,117,1000,515,1000,-208,864,605,-277,-380,-457,622,1000,-292,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "previousSibling():org.jsoup.nodes.Node",
            new int[]{371,-102,467,-363,-825,244,242,-926,700,279,507,335,510,996,26,593,773,689,-331,-833,-78,-981,760,437,-669,762,-611,-660,40,923,240,-2,281,-113,804,-268,-122,250,661,-443,496,96,947,88,-890,597,70,700,119,-961,-706,-182,-967,-369,-112,-793,-147,-207,208,-728,902,-992,298,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "remove():void",
            new int[]{-670,737,961,-885,499,74,-738,-191,-424,219,-651,945,-136,125,437,-815,476,875,85,777,-742,290,-923,-876,854,-303,-322,-713,-195,657,574,356,-860,561,-960,617,-709,-611,933,-226,381,-356,-62,-934,922,-512,-806,81,-202,-531,857,302,39,-140,512,-29,340,586,-619,742,-477,-986,246,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "remove():void",
            new int[]{123,951,680,113,-510,776,533,-626,508,-505,-333,-396,-502,-946,-312,-861,-425,284,736,415,935,-368,814,-849,744,-971,421,-695,-775,161,22,-307,130,524,123,-808,-855,-979,766,-277,991,-417,-914,-153,983,316,275,739,-358,-972,743,536,-420,-385,578,621,323,-230,-491,283,-988,750,473,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{294,-1000,-254,195,-386,-252,-1000,84,376,70,604,-746,-486,-106,147,-1000,-875,-1000,-972,150,474,1000,-916,-1000,248,962,1000,697,-1000,-901,742,-1000,1000,692,435,-887,-550,96,-1000,-255,604,-257,-391,-1000,1000,541,707,-695,-1000,321,553,-754,1000,145,1000,200,-597,-965,-526,-735,-526,424,-550,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DocumentType", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-393,-791,-625,-232,369,181,-747,216,693,-37,147,-635,403,329,-44,-796,-211,-588,181,-526,786,-267,-127,901,346,964,506,605,484,-526,-344,745,47,120,-342,-158,-200,-386,810,153,-916,-738,-933,-11,998,-61,-303,914,-179,-274,896,397,-760,811,982,687,814,970,-549,417,-920,230,-76,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "removeAttr(java.lang.String):org.jsoup.nodes.Node",
            new int[]{2,-1000,-318,696,806,-321,14,-90,127,-3,-398,-82,-14,111,-130,-18,-400,107,1000,119,1000,-1000,45,1000,-1000,-47,1000,447,-129,-129,-769,1000,-492,-207,-17,-703,-609,190,-324,-255,-495,-369,-816,727,257,-267,411,423,-1000,867,229,624,-750,940,-181,1000,496,1000,106,-1000,434,-1000,524,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "replaceWith(org.jsoup.nodes.Node):void",
            new int[]{426,332,-942,71,116,-71,900,47,-477,120,-618,-868,986,201,184,-405,-164,464,409,-866,692,630,-879,-299,716,922,450,76,-557,-856,-368,853,767,-685,423,-911,-662,907,214,784,-785,527,868,557,927,20,807,-589,311,393,-784,-673,414,-302,335,-436,607,-913,790,25,593,598,-345,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "replaceWith(org.jsoup.nodes.Node):void",
            new int[]{-69,-117,-952,-611,158,223,440,-75,484,478,673,414,567,153,997,-363,-723,14,-829,-14,209,-4,-440,-940,-139,625,81,954,252,460,495,-973,-338,208,609,519,-119,-300,838,323,-68,694,-727,389,558,603,915,782,954,-338,51,270,-838,823,-111,425,395,-307,508,-271,-885,-797,959,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "setBaseUri(java.lang.String):void",
            new int[]{-166,-825,-155,594,487,-850,-596,-350,-1000,-221,-90,986,-907,-466,514,-55,-1000,-785,-573,1000,-107,75,-793,-83,-1000,1000,701,1000,1000,-1000,-13,521,984,11,513,-580,-258,554,1000,96,850,622,-764,1000,-508,1000,-88,-1000,-588,1000,555,-1000,-115,26,297,-661,890,-122,488,580,604,-270,-81,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "setBaseUri(java.lang.String):void",
            new int[]{999,-680,670,-258,-73,446,-451,-926,683,-698,-398,679,64,-520,792,-125,-138,25,412,813,-238,221,-579,461,-241,-975,161,-698,851,-686,-616,618,962,460,-333,729,-390,914,704,898,-391,-519,-287,-277,-674,-283,-751,800,391,524,-899,195,-384,-587,599,-156,-105,-869,694,371,-690,-595,-565,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingIndex():int",
            new int[]{210,-32,-1000,-89,-951,-237,563,137,-51,-331,265,-400,157,-668,-443,-190,483,310,-1000,968,740,-728,181,-27,-1000,662,-1000,36,-384,1000,815,-598,322,613,881,841,256,-353,-976,-359,435,-1000,398,407,184,-1000,1000,-105,1000,1000,224,108,539,-199,1000,-1000,-698,1000,1000,255,-851,-223,864,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingIndex():int",
            new int[]{783,-61,-575,506,545,611,401,-947,-983,354,-651,-405,86,-417,18,-761,-862,560,-776,591,-610,-587,67,403,-368,-994,934,176,475,-457,-16,673,-640,448,69,192,297,704,-268,411,603,-404,281,-377,-272,-432,807,-737,688,-192,428,-218,589,-937,81,107,-747,-815,722,-790,56,973,923,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptyList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingNodes():java.util.List",
            new int[]{-73,367,-379,365,-208,-6,-715,-641,-607,554,876,201,900,-507,-817,-600,-156,535,-536,-194,780,-704,-25,-18,476,757,-414,-888,-253,-404,-777,-817,-339,-85,-510,54,994,-500,3,855,-471,-557,742,-835,90,678,31,-584,-969,785,-375,47,316,-714,834,-917,306,-433,623,4,886,552,-841,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$EmptyList", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "siblingNodes():java.util.List",
            new int[]{218,458,788,183,111,-861,-716,-347,-191,111,-511,850,-715,-758,-159,-449,-197,-967,931,-831,-342,-202,-336,-639,159,-606,-58,-933,-618,-99,956,-914,-245,-875,-175,-836,763,-526,494,437,-274,389,-429,31,-98,-735,73,658,46,-414,-39,86,905,-119,-251,-441,152,-1000,500,388,-240,226,76,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.String:PCFET0NUWVBFIFBVQkxJQyAiLS04ODIiICI1T0pnIGJ5UVlvK2hjK2sKIj4=", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{55,393,-463,-643,-238,-956,-882,-201,-322,486,-274,-259,-350,831,329,158,-999,650,460,194,-366,-189,346,-693,509,-9,162,851,-343,946,-357,186,233,31,-662,-227,-636,159,-367,275,496,-563,-670,664,-172,27,62,-452,994,448,109,494,-335,507,-719,-1,938,-716,-553,-52,-488,234,-969,865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{-238,1000,695,386,1000,-226,738,-788,-525,-350,-1000,-829,-308,1000,1000,-361,-308,-279,1000,429,1000,-257,1000,-48,271,116,-231,1000,-421,1000,-1000,1000,1000,-669,-1,-686,461,-96,-411,-1000,-314,376,-1000,-125,236,306,-319,1000,-30,-862,-422,264,28,-956,-1000,981,-1000,507,-640,-1000,-1000,-1000,-243,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:CjwhLS0rNDdkLS0+", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "toString():java.lang.String",
            new int[]{964,233,-871,-47,250,-850,278,713,113,-150,-812,834,884,-984,795,688,-601,397,877,-1000,546,-572,-897,203,159,1000,-369,-926,-157,-1000,-615,-997,-442,-990,-905,580,-269,80,-420,139,-708,1000,562,1000,454,-1000,637,-986,298,896,-470,403,-108,287,541,691,811,304,-916,618,-288,60,599,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "traverse(org.jsoup.select.NodeVisitor):org.jsoup.nodes.Node",
            new int[]{283,352,-815,968,-72,214,55,-292,574,-540,-763,329,-494,873,-180,479,974,-956,-836,827,173,275,391,309,415,-727,209,657,730,960,-15,335,-778,-307,888,991,-222,724,913,114,-72,-186,-337,-312,46,603,-150,359,427,-490,667,-151,-746,776,-579,-816,984,-440,104,677,-740,943,955,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "traverse(org.jsoup.select.NodeVisitor):org.jsoup.nodes.Node",
            new int[]{786,405,-271,-813,128,-906,-537,743,-485,-517,-886,-70,-371,311,-697,-470,1000,-36,-1000,756,-1000,641,-5,-811,891,-310,-81,-375,1000,566,-804,299,-893,769,951,994,-116,833,1000,-24,849,-870,54,-130,145,-121,208,-83,51,621,929,599,-950,1000,-1000,107,159,425,-261,1000,-1000,1000,1000,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "unwrap():org.jsoup.nodes.Node",
            new int[]{-126,1000,-359,-625,-972,-988,-896,-213,-64,436,626,-693,827,1000,-234,-460,-346,-76,1000,-118,385,923,759,-36,-1000,-179,-51,931,459,-340,-3,1000,266,687,-141,511,-26,-1000,-993,1000,372,374,1000,-1000,-421,-699,864,786,-509,-791,-115,-22,-1000,998,-27,306,-1000,147,-1000,-354,-330,-904,427,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "unwrap():org.jsoup.nodes.Node",
            new int[]{63,-1000,541,-525,-408,-561,-869,-1000,23,206,655,597,-573,341,-734,1000,124,1000,-1000,224,-837,-161,110,-43,191,-895,37,611,-273,-354,1000,-524,-640,257,-278,1000,-1000,-110,767,-15,408,276,697,448,325,-736,95,-963,-517,-1000,472,-87,-751,443,883,-140,-451,-538,-611,258,-1000,613,-145,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{803,-435,99,339,-863,968,241,589,-985,-377,748,-944,804,539,-944,-602,-215,738,-515,914,-230,-23,628,-862,60,311,-56,-805,-274,694,55,-318,377,734,973,518,-661,-363,763,949,-523,439,-2,-312,-227,-346,762,29,212,656,938,705,-468,-729,-952,232,946,380,-6,560,-905,601,-266,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{150,1000,-604,352,-1000,-61,-231,1000,576,-1000,851,-1000,-98,232,-1000,256,587,88,1000,-535,27,2,773,-28,-1000,884,-976,329,-170,98,1000,75,1000,814,-355,-411,-344,129,880,684,-620,576,272,-728,-897,616,181,-581,-974,654,1000,1000,-113,128,-284,181,-562,87,-850,131,-579,366,126,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.String:KzYwMA==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-326,600,986,280,-927,604,-614,-712,-301,-183,-680,-898,-147,-91,314,-790,-883,-717,603,797,315,-34,-313,-928,-374,5,-94,-96,136,393,334,-705,61,978,268,782,-691,166,-957,612,-877,777,22,-710,-916,915,-813,905,-11,347,974,591,-243,-442,-252,-647,865,379,615,589,-373,-111,154,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:KzkxN2Y=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{137,917,770,-420,269,-364,-796,664,491,-802,-987,-244,753,502,-585,-765,702,805,-772,150,-568,877,134,-369,-954,518,-421,459,-358,857,365,-799,-608,-380,596,895,986,-615,790,863,943,754,696,-88,-992,286,641,-82,-807,-904,-942,582,-127,77,-716,-981,-184,-145,670,-248,3,-210,-434,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-676,85,-948,574,-337,-171,-879,454,-244,-79,-23,-125,-944,-1000,653,702,-695,909,872,-122,263,-273,120,241,198,651,728,270,86,205,669,-733,-648,767,635,191,228,799,34,-140,-699,-718,-2,-433,669,902,238,279,-709,-759,635,-447,-453,913,-185,41,830,-59,-332,54,-932,927,718,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{522,959,209,350,618,-428,-200,892,-741,635,-139,215,652,341,376,-126,353,193,-527,509,588,833,-11,452,-339,-503,542,661,518,-914,-268,166,983,326,556,-14,242,912,202,-829,720,-875,20,524,-321,-6,-348,-614,561,-221,41,877,411,-756,383,963,529,36,-67,815,939,-683,-957,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{696,363,159,-690,-775,686,-428,950,-195,-569,361,-131,-183,-826,-332,-908,350,-924,-567,638,364,833,-587,764,868,-723,340,-287,897,-969,695,-732,-124,470,469,-472,388,-930,708,442,-674,159,643,-144,-55,79,774,101,429,809,411,-552,228,201,-160,-800,-323,177,567,-410,-853,457,-124,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-200,880,-377,-372,161,-856,-567,-957,790,-309,630,-413,-208,930,720,-75,843,-671,456,-943,-399,-63,596,-618,-706,-437,-875,-963,-707,-439,985,480,603,-496,13,-567,-111,-556,-965,447,920,867,805,606,-897,-985,-151,-826,-806,-209,-801,359,166,-863,418,781,767,-553,414,738,307,376,90,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{-1000,-1000,-18,-997,-408,-463,-63,395,1000,-932,323,874,1000,15,-834,-217,929,377,-601,-1000,-620,325,593,-935,259,-700,601,885,-568,-553,-290,-1000,588,-1000,-1000,1000,-152,-1000,119,1000,1000,1000,-1000,-704,1000,556,-825,-1000,266,924,1000,1000,614,-404,78,927,-1000,-1000,-1000,-195,1000,304,-722,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{728,291,-216,1000,731,15,889,-59,-98,-192,26,981,-19,-525,162,-1000,258,1000,-312,-36,-337,-979,1000,-789,1000,-602,1000,615,404,-1000,133,-150,382,-270,208,953,-281,-798,-145,-74,-101,163,-222,1000,-504,309,1000,-475,31,1000,-608,-170,1000,-541,-138,-415,639,-124,577,-699,-5,1000,-129,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "clone():org.jsoup.nodes.Document",
            new int[]{99,-180,-932,-626,822,-435,434,658,-643,-391,-335,-307,-772,-858,952,725,442,158,711,-967,-743,84,-449,919,-992,-404,-709,839,-707,317,-235,765,-62,-798,984,912,-878,899,-208,339,-204,151,594,617,-913,-623,945,-255,-198,-6,843,800,-700,-280,-584,443,-967,173,360,311,-109,-639,-37,-413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "clone():org.jsoup.nodes.Document",
            new int[]{-1000,822,-109,785,-59,-406,1000,-497,772,75,1000,224,-283,-396,-1000,-928,-844,938,809,-1000,-223,482,-724,-922,928,-700,-1000,-1000,1000,682,-203,-512,264,-627,1000,840,114,833,-273,-170,206,-1000,1000,514,-146,-1000,1000,129,-1000,-1000,916,-359,-232,-587,277,-1000,-1000,-1000,728,-184,678,-369,-814,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "", "createShell(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-337,-827,107,735,-566,-285,977,-415,-417,-366,637,555,-288,-243,495,-921,-17,35,118,250,-287,925,463,923,359,-714,-822,484,815,-551,-91,393,858,-457,-516,-750,-946,-492,372,834,470,783,844,-650,-862,145,-71,918,340,-163,404,-744,577,758,856,470,277,-750,-870,839,220,804,367,620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{289,-839,-193,567,-912,851,1000,68,-336,482,614,-1000,1000,-466,949,-466,-653,1000,-1000,271,-363,-773,284,225,-1000,362,107,957,-277,1000,-1000,-728,-589,-39,433,1000,303,-1000,1000,-906,947,427,-648,-1000,911,-210,190,-910,-921,472,1000,1000,-436,1000,-575,1000,351,-1000,-1000,-1000,120,-1000,-418,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{136,-522,262,-1000,24,1000,349,42,360,694,1000,-1000,709,-486,10,-515,87,1000,-136,-307,1000,690,518,-778,-738,1000,-615,11,-285,-331,86,-808,-955,519,613,232,892,-1000,1000,-1000,-270,-570,70,-1000,210,140,-806,-211,-550,661,350,1000,729,-674,555,648,934,-1000,-1000,-1000,456,47,-1000,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{662,-330,-791,-505,-970,956,-316,374,-827,-849,383,-200,558,-333,-91,-519,-582,293,193,-196,-141,-870,-390,451,-71,-236,-407,116,-451,-525,214,-710,835,231,59,420,-156,-588,-685,415,54,-184,480,-584,-6,337,337,-972,364,-115,-95,821,-298,-350,-8,-243,206,561,634,-521,657,-44,-754,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{-813,-202,986,-200,1000,494,562,1000,1000,-726,306,352,233,-73,-684,-688,633,-842,-627,453,-678,-409,329,35,47,458,411,144,925,158,-362,-848,220,348,-170,676,-442,1000,-632,-629,-1000,685,673,-1000,-52,-344,443,406,203,-714,531,-584,-569,-729,-435,-136,184,1000,-578,396,-5,-651,778,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,1000,989,609,-1000,-32,865,84,-1000,194,-74,1000,-1000,-885,93,373,413,774,-1000,62,1000,1000,1000,-109,24,777,-553,-1000,-1000,-983,-788,412,-640,-1000,-1000,637,-742,-1000,-199,-149,42,-1000,-221,-211,-317,1000,231,-1000,-225,157,527,-1000,1000,-313,-1000,1000,1000,-1000,-509,1000,-1000,109,397,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-244,519,-911,-1000,-803,-391,-129,-965,1000,1000,1000,-852,-298,1000,995,629,819,-355,-111,1000,1000,316,12,1000,-140,-701,1000,-1000,-1000,-968,-677,-838,-716,-685,315,-1000,87,573,-3,-1000,776,809,-1000,160,-498,-952,-1000,704,-65,-1000,1000,-518,1000,727,-280,419,-704,-400,-291,1000,-1000,-688,-93,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "title(java.lang.String):void",
            new int[]{47,691,-983,264,-151,424,524,-886,66,55,392,-347,-587,268,-174,1000,431,-1000,1000,-305,75,286,-115,-928,-1000,-132,-26,1000,-295,1000,181,1000,151,-249,268,-566,115,947,-63,-470,1000,-126,-175,-38,1000,-462,-169,-268,-828,-81,1000,1000,622,-452,515,28,453,-581,-682,443,396,-858,-521,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "title(java.lang.String):void",
            new int[]{-823,-263,662,563,-176,-53,-236,-242,-61,-36,760,57,-591,175,-799,79,853,208,62,-481,310,616,-303,-1000,637,421,696,584,828,-570,11,-457,-287,-2,-1000,-850,-354,-628,12,499,1000,315,352,-351,714,-318,-143,-1000,-133,-968,49,-762,961,-1000,-477,496,-330,103,-20,374,-42,-1000,688,988}));
    }
}
