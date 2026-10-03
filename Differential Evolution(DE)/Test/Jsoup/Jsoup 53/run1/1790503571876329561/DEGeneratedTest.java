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
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "addFirst(java.lang.Character):void",
            new int[]{-689,-628,-218,932,981,-634,-867,124,-345,130,413,935,584,904,570,-951,664,-481,20,-755,295,-425,65,737,-101,-294,622,-336,-245,-870,919,395,-957,918,752,454,-938,27,-392,424,491,-965,-485,-75,-761,733,814,-58,-55,-759,243,176,521,176,-340,-158,-745,-475,-418,-600,-65,368,242,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "addFirst(java.lang.String):void",
            new int[]{-654,547,-534,830,886,589,-458,645,72,-916,276,599,-434,57,-977,908,-476,-526,-651,56,70,-409,275,900,-912,-470,-472,791,-130,683,445,489,65,259,789,357,336,-432,-463,-311,138,-490,426,-133,-888,98,979,489,-264,-139,588,-383,85,473,-720,867,795,67,-213,-838,-308,-642,-319,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "advance():void",
            new int[]{818,-532,-111,29,-894,661,406,-804,787,-777,912,-219,-503,-521,-930,898,835,-622,-593,991,102,-903,821,-21,538,592,-411,748,-478,442,971,452,24,749,-315,-919,-431,863,730,283,906,278,-722,-485,-324,148,-283,-741,444,-406,-700,266,-342,-808,-687,-426,324,-136,194,372,479,779,625,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "advance():void",
            new int[]{993,1000,158,356,1000,-1000,-1000,-1000,1000,-1000,135,-1000,-614,-359,462,1000,-48,1000,-618,805,1000,-45,262,835,532,337,-1000,-1000,-1000,1000,-827,1000,-485,299,-598,-653,1000,-153,-474,1000,-1000,-645,1000,-1000,1000,-1000,-469,1000,1000,797,-157,419,-367,1000,473,1000,1000,1000,109,-396,-45,-606,1000,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:GBgweDgweDg=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompBalanced(char,char):java.lang.String",
            new int[]{-1000,-848,1000,711,-1000,1000,-381,205,1000,-1000,1000,-1000,1000,-1000,-1000,-1000,278,-1000,-1000,-65,341,-1000,-382,1000,1000,-1000,528,-1000,1000,-676,-1000,-1000,1000,-1000,495,1000,446,413,-1000,-475,-1000,-304,290,1000,728,372,1000,-970,1000,-1000,-501,1000,745,-1000,-23,19,61,1000,-390,-1000,1000,21,1000,-673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompBalanced(char,char):java.lang.String",
            new int[]{-342,1000,-361,-1000,623,257,1000,1000,-1000,-1000,-1000,1000,-616,-946,1000,-1000,1000,-1000,508,-182,-711,938,1000,257,1000,75,-233,1000,-550,-655,77,-650,-906,1000,-896,486,792,-527,-158,385,1000,-60,1000,-659,392,1000,159,1000,-1000,-817,-441,865,910,-11,24,1000,-949,1000,-143,1000,286,-809,-1000,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:ZmZhbHNl", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompTo(java.lang.String):java.lang.String",
            new int[]{-276,393,-646,-429,693,408,-717,-533,-796,-651,-474,743,-79,-279,730,860,486,-910,-334,-719,-995,-960,49,-310,815,236,-312,-65,457,876,-825,-782,155,-733,-475,701,-290,-456,-198,128,726,699,565,-662,309,940,-791,-402,32,186,728,-396,792,-999,261,640,-558,662,-126,955,595,180,-496,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:WSA=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompTo(java.lang.String):java.lang.String",
            new int[]{590,-290,-142,-778,-748,361,-530,89,542,503,808,-668,215,-591,483,-18,424,-676,776,486,412,-344,-664,-770,820,-419,-577,323,-965,578,854,-98,-680,819,-939,-171,125,693,246,-890,-56,804,445,626,361,721,-467,-177,497,478,-322,923,-998,-638,-429,883,-88,-111,852,-145,-741,-973,-381,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:Sy0xNzFlLTI0OSAtNDAzRA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{377,708,41,403,997,351,54,313,-990,27,-974,-489,-954,-171,409,-249,637,737,-655,307,-66,-437,659,-277,-841,647,485,-293,-981,-191,921,148,339,226,-319,-839,-297,203,-213,182,45,-879,2,-814,818,233,947,-681,-408,-542,594,-553,-210,-193,202,-573,-371,-313,-338,276,-759,-59,-321,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:Bw==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{1000,-21,-1000,-1000,501,-225,-378,861,-229,524,37,-247,-870,-377,-501,-400,-646,663,1000,-929,922,-538,-1000,-893,1000,1000,293,-1000,212,313,-1000,897,813,-979,1000,-184,1000,-583,1000,-247,-514,-1000,1000,-862,-552,1000,-14,-534,-469,-1000,-1000,-478,-427,907,444,1000,186,1000,763,-1000,-1000,-392,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Character:bQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consume():char",
            new int[]{-500,72,245,-419,475,98,-469,-550,-275,-279,97,-48,440,353,-791,684,-86,-867,531,-594,743,-726,584,569,-20,934,239,-720,-248,119,-310,-658,848,-475,797,-2,65,-568,335,896,734,736,-218,435,-194,71,-438,-849,51,582,336,-934,573,-640,892,65,626,-180,-539,-762,961,361,870,891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consume(java.lang.String):void",
            new int[]{400,932,1000,-204,-519,108,-627,-1000,791,473,789,1000,-1000,-1000,-446,267,-50,379,-1000,1000,-170,1000,-276,-118,502,513,277,55,-1000,334,-1000,1000,132,-485,-353,-1000,-99,1000,-690,-840,-253,1000,1000,344,307,474,1000,-709,-311,290,-369,76,725,-251,-316,-664,-534,686,414,1000,-149,633,585,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consume(java.lang.String):void",
            new int[]{-200,732,-101,-718,25,132,273,-404,480,-759,-595,-812,-56,-244,447,664,-929,-908,-956,-652,484,408,-342,-782,-964,-325,-18,308,-834,-436,724,-292,506,-248,-415,620,218,627,-206,835,876,275,-738,-517,154,-809,57,916,-960,-823,-267,-785,878,530,-176,-73,128,819,740,-604,-591,-703,240,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:Ni0weDgwMDAw", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeAttributeKey():java.lang.String",
            new int[]{-118,-123,1000,-523,-675,-954,193,575,-1000,643,-166,-420,-809,8,1000,-639,-116,-1000,54,-205,1000,-179,-68,-1000,414,799,478,667,-489,962,-362,-78,1000,121,-389,-511,873,-977,356,1000,-662,-823,481,-265,58,-480,-792,602,174,-1000,-393,771,-429,-246,-1000,-29,854,105,211,1000,-927,-446,676,351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.String:aGpf", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeAttributeKey():java.lang.String",
            new int[]{-345,1000,-66,962,978,-398,-123,-1000,-484,-590,1000,-79,1000,-1000,1000,1000,-109,-185,691,-457,1000,1000,871,-604,444,1000,376,-607,-305,-130,114,245,-1000,-1000,1000,321,988,-252,-161,1000,-1000,-1000,420,101,-1000,193,-374,226,1000,-791,731,-1000,-1000,-1000,-1000,-53,947,1000,1000,363,-1000,1000,-242,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:X2hB", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeCssIdentifier():java.lang.String",
            new int[]{-942,540,-315,-242,-376,-648,714,52,-418,398,272,983,-595,694,-363,160,869,-887,-788,926,-451,801,-224,-82,-809,-334,-592,735,670,428,-733,-190,-752,-900,-213,-420,167,-34,-392,-43,199,149,536,-288,-257,-352,-799,903,272,-603,-281,-247,-485,330,-385,484,-392,677,-433,547,-360,827,94,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:N01ERXNTZWNfbk42", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeCssIdentifier():java.lang.String",
            new int[]{-1000,-639,-113,450,-141,-715,-458,-528,596,-940,582,83,-1000,94,-803,1000,947,-1000,-563,1000,439,48,-516,494,-379,894,-229,404,-179,-379,-920,-1000,-653,495,-705,-515,326,-666,-714,706,777,643,21,-1000,54,-254,-680,-788,335,-398,1000,-1000,-1000,-625,-497,-666,246,80,-389,-196,-1000,-27,385,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:Z1BNX2s=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeElementSelector():java.lang.String",
            new int[]{177,510,761,703,620,128,947,986,165,269,-450,-456,16,655,-339,-304,261,-1000,-406,427,-879,597,848,940,-406,-231,77,759,-1000,-712,219,-9,694,577,321,466,499,1000,567,-284,493,14,270,-65,-636,-284,-1000,537,1000,-448,539,518,-552,-540,149,-790,-1000,-292,-453,725,-814,146,704,-627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:Xy0tNDY3ZTcwNA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeElementSelector():java.lang.String",
            new int[]{-755,612,86,-467,-581,704,-764,-1000,-33,-833,325,905,242,86,-1000,1000,920,-918,-477,-156,-479,-1000,90,-835,-364,-386,1000,-1000,-208,1000,250,545,-862,399,916,-292,825,442,-93,400,391,-925,-401,-1000,1000,1000,1000,-807,-980,1000,-190,-353,-1000,966,580,401,1000,-570,445,882,1000,831,-942,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4MWJhLS02MTJ0", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTagName():java.lang.String",
            new int[]{-299,11,-669,-46,-820,604,-802,-833,-80,-12,-615,-12,-612,247,-733,119,442,-939,-473,-825,-243,246,-529,-876,970,-109,897,-956,-449,-532,862,-827,-976,-385,-549,-956,-419,22,219,573,340,-191,369,-642,-428,-884,918,180,-967,223,127,-303,-739,-529,268,688,102,-526,471,-164,-903,704,799,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:LWgweDNhOTI2ZS0yODE=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTagName():java.lang.String",
            new int[]{1000,343,566,-926,-1000,-281,-616,659,-553,-58,-180,640,1000,497,796,-1000,342,-1000,918,-595,-77,89,1000,1000,1000,-378,1000,1000,1000,-1000,573,-197,-1000,-1000,-831,-346,748,1000,-526,1000,835,239,-1000,-135,825,1000,-779,313,-1000,-1000,-117,1000,502,509,370,1000,-361,158,948,50,609,1000,-1000,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:JT5mYWxzZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTo(java.lang.String):java.lang.String",
            new int[]{202,-506,-197,-361,-154,496,157,-248,-322,-968,-987,-364,716,-766,828,953,948,114,-269,532,-845,636,-920,347,935,270,33,-104,-362,-550,134,-762,-635,-31,299,-317,-23,-250,-723,-268,-885,-339,624,223,-318,95,582,846,896,-122,-173,204,-905,-267,4,-590,789,-985,-770,857,-905,-280,-752,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTo(java.lang.String):java.lang.String",
            new int[]{-944,209,215,-139,-66,-702,347,-216,739,-163,433,150,888,428,-854,-415,-898,-425,897,14,119,-570,-753,-704,432,-678,305,611,821,338,-277,-815,962,824,-750,718,-885,89,526,-574,30,-160,746,-982,565,-112,-834,-896,-732,551,-966,560,-840,819,441,183,327,836,-148,-388,553,-446,-355,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:TytXCXlaV3M3V1VrMHV5N3hiaDRycBMtMjY5ZC02ODE=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToAny(java.lang.String[]):java.lang.String",
            new int[]{789,970,-114,271,875,766,-744,-719,-745,-643,309,-281,-815,678,-53,-354,491,365,-360,562,-594,288,114,-945,243,-681,299,-665,-551,-269,289,-622,-256,-365,983,-627,-740,-632,-928,760,-151,-155,-925,-747,61,-439,241,-64,-297,127,-690,781,314,34,362,743,-557,656,288,-44,167,-572,758,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:MjU3LS02NTNMIy0yMjZ0cnVl", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToAny(java.lang.String[]):java.lang.String",
            new int[]{-124,-592,923,483,386,-255,-746,671,-268,-226,549,-746,675,-735,-455,653,811,-361,-23,771,257,248,-868,-760,655,-446,27,124,-201,382,729,-471,-653,-621,-786,578,268,356,192,-228,-49,-713,-650,-104,231,-635,568,-695,981,-203,879,-319,-629,-270,122,176,411,-884,-418,-788,-11,-824,250,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAtLTB4MTUxY251bGwrMHgyMDk=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-163,491,-137,521,-646,349,-639,112,188,184,-358,-797,-43,743,337,315,-937,-552,-701,589,-776,184,-496,214,2,-626,-816,-323,97,-558,797,20,441,169,184,525,-63,49,-360,-470,67,-769,-496,459,-484,-632,374,568,493,-663,65,394,465,-414,26,-571,662,-299,-900,186,-109,-348,231,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:MBQt", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{1000,816,-1000,682,-649,461,-633,-506,-236,-388,432,-1000,-886,333,1000,1000,-937,-571,-1000,1000,-1000,1000,-1000,-794,1000,-147,-816,-1000,847,-679,168,20,1000,626,761,1000,-616,411,-1000,-558,-1000,-1000,-351,1000,237,-1000,681,603,1000,-658,65,692,-336,-1000,-472,-220,1000,-1000,-1000,839,-238,266,169,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:akZyX3FHSy9wcm51bGwtLTB4M2M3", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{388,601,23,967,879,24,-191,225,760,-755,-549,128,-593,283,713,543,651,650,159,893,27,988,310,184,-593,848,664,27,-530,-406,287,-739,-315,-107,537,-23,306,-268,-246,-919,-731,-467,-281,871,793,-475,114,531,11,-704,272,-49,-986,145,-914,-215,322,-232,125,-663,-30,-382,-304,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWhitespace():boolean",
            new int[]{-744,928,199,636,664,-577,-577,-580,842,412,-935,-936,68,396,-352,-880,-532,-475,177,267,-844,-677,231,-554,-804,-556,-694,600,-904,-803,-334,645,450,-86,-170,-40,829,-350,381,372,-963,208,-188,-593,456,32,-231,-632,361,-906,232,-272,795,-380,-73,-727,898,-805,-416,432,-589,605,-476,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWhitespace():boolean",
            new int[]{-44,-497,-622,-471,-496,355,1000,380,981,-384,-697,421,1000,-742,-79,1000,-115,182,-104,-457,608,883,247,-66,614,124,839,-383,222,537,719,1000,-88,-301,-477,1000,-706,848,1000,44,-429,-457,541,-833,487,651,981,408,661,-437,539,-698,683,-824,222,-1000,-21,-947,997,1000,265,60,-842,450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2VvOTg5ZTEzMw==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWord():java.lang.String",
            new int[]{517,182,-554,989,36,133,-653,-614,-273,321,123,-914,-952,921,-965,422,424,331,-724,-814,-293,965,644,-317,647,-206,773,422,200,-301,299,-556,-80,451,600,-905,-792,-482,-940,441,789,780,126,-555,-599,-288,898,544,-592,630,-554,323,-192,-811,614,601,-68,-521,-332,454,46,-210,840,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:VGdjZw==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWord():java.lang.String",
            new int[]{267,177,-851,-839,-408,-851,195,-223,272,-270,-164,-354,145,-646,852,-700,964,-914,931,-719,694,-326,244,487,-138,-964,322,947,-15,-812,367,-524,-452,435,-658,517,535,956,-513,-197,83,868,-713,-545,-525,-736,-678,-389,407,818,926,-254,201,-904,-882,483,-502,893,-90,-132,-159,-362,-273,846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "isEmpty():boolean",
            new int[]{-546,-102,855,739,525,-906,-705,-811,-484,-862,990,693,-743,340,-954,-875,762,613,-877,155,136,-242,-488,-465,-510,327,155,-282,103,482,323,328,171,-272,262,-772,916,-217,-119,870,-423,-81,-239,-595,-137,-894,202,-342,-196,21,801,-541,-359,566,156,77,360,926,671,-477,814,385,-832,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "isEmpty():boolean",
            new int[]{-489,581,417,-1000,468,1000,835,-486,426,638,763,58,1000,694,-985,251,1000,619,676,-789,491,-215,400,40,-439,-570,-58,86,1000,729,1000,745,229,-1000,281,887,-1000,763,773,75,-155,417,382,-179,-481,-1000,-658,1000,-851,-1000,1000,-1000,-295,151,-79,343,935,736,1000,-384,1000,728,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchChomp(java.lang.String):boolean",
            new int[]{655,26,-552,92,299,618,564,819,102,-10,667,-178,-363,653,-794,-330,359,248,-936,184,382,29,-872,-885,424,989,98,149,-438,347,321,-69,643,-827,782,-756,-735,-816,619,-861,-929,-602,513,892,-215,-765,-862,947,878,-76,-512,707,-938,618,-653,-40,-100,-516,-577,13,745,-760,-251,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchChomp(java.lang.String):boolean",
            new int[]{-581,162,-808,-685,982,210,266,260,-386,-690,-76,584,-15,765,-297,-301,779,253,-574,-903,-83,133,990,964,-645,-954,-847,-748,14,-283,465,-451,-407,872,933,530,-928,-543,145,461,-425,-981,652,-735,-93,974,169,-11,579,-198,837,-826,411,692,202,-433,913,-895,861,932,-441,-162,-250,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matches(java.lang.String):boolean",
            new int[]{10,-803,98,-297,71,697,-164,635,905,293,-705,487,278,465,313,-973,-605,-807,217,-163,-683,-411,139,-576,-195,-345,693,748,442,-384,-33,577,-941,257,-823,-557,559,-987,-490,736,80,-839,-289,-542,106,-596,-210,-490,-393,405,-162,-951,589,-676,412,386,-467,-636,210,203,120,428,-196,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(char[]):boolean",
            new int[]{515,-382,753,777,981,314,-568,340,-868,204,-173,40,-570,-760,-419,354,193,487,813,972,-577,-542,480,226,978,953,102,723,192,87,-304,601,379,647,19,-516,308,-787,-220,-342,-310,-395,335,216,-973,-914,-419,-946,-298,-465,-757,418,157,-695,-447,451,161,845,469,-334,-265,-655,958,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(char[]):boolean",
            new int[]{139,311,734,88,842,800,-1000,322,-1000,462,1000,446,317,-727,-651,-568,-205,-246,-371,-204,1000,-508,-1000,-1000,40,750,1000,18,-1000,-631,1000,124,494,-523,846,393,-1000,1000,1000,1000,596,198,716,468,-402,240,-371,1000,715,-1000,1000,1000,-555,-1000,1000,-285,-434,528,-1000,161,340,183,-110,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(char[]):boolean",
            new int[]{267,972,1000,405,849,628,72,-876,-587,257,858,-80,718,561,-4,909,-352,908,306,-105,-296,155,-1000,-681,365,-321,530,296,232,283,-289,765,-725,-623,-1000,-14,-346,931,696,1000,476,1000,1000,144,-1000,-1000,-381,-313,-132,-95,398,18,-743,279,1000,-329,477,186,523,562,672,1000,-729,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(java.lang.String[]):boolean",
            new int[]{-684,489,-148,216,-879,-929,141,748,-907,673,442,-149,233,452,569,131,379,-85,527,861,-797,911,-859,-471,-82,-203,435,-233,-617,-354,519,834,-281,399,-978,-25,274,-306,-248,-950,770,829,-809,583,830,712,660,75,-291,32,568,828,-951,-95,-975,806,716,233,250,146,-617,697,756,771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(java.lang.String[]):boolean",
            new int[]{-374,544,-893,-21,-309,-287,500,-951,-924,438,-558,-91,-470,110,-796,583,802,-263,114,901,995,-165,204,-527,876,-645,-539,260,524,-296,106,-914,134,-54,663,626,473,124,157,415,535,-693,747,258,262,-549,317,-969,854,-552,-869,336,171,-147,499,-279,-30,-336,611,-760,-665,306,-842,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesCS(java.lang.String):boolean",
            new int[]{-771,-338,40,-990,866,101,-634,712,976,877,-994,102,797,-598,980,776,501,-708,-220,-354,681,447,282,22,903,-986,448,-46,495,-945,-680,-518,-655,-133,-465,-207,-243,79,-171,788,145,-134,801,-968,-269,-60,607,-320,294,331,181,-418,517,-601,69,504,462,-8,-933,-859,369,-773,423,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesStartTag():boolean",
            new int[]{-628,-686,-622,-7,352,-907,-831,-298,-815,-81,153,-691,562,827,582,26,-480,-418,-837,-74,319,-109,-421,767,-572,-535,-189,-924,-247,-893,-804,18,551,-910,-836,-315,774,-261,-279,171,989,387,930,-3,487,613,864,315,-306,48,800,666,-421,-75,810,-985,-582,-962,293,-712,-55,780,514,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesStartTag():boolean",
            new int[]{599,-633,-431,-130,25,-214,188,1000,-1000,-1000,-797,-19,-1000,954,1000,-1000,1000,-568,-846,1000,126,-991,345,-500,-742,-540,862,-288,-496,584,-79,439,1000,750,561,-503,680,-787,821,770,-228,1000,570,789,-1000,779,-522,-1000,-153,-1000,799,-1000,-995,-654,-637,1000,-315,-931,359,533,-470,-182,-88,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWhitespace():boolean",
            new int[]{675,-813,-414,-735,562,-164,-814,-905,-439,-987,-183,-742,672,686,758,-776,-695,716,413,-383,-909,-251,-576,772,594,-137,-776,209,858,420,-77,549,-273,970,817,-525,180,-767,551,166,530,226,-49,324,300,381,913,368,781,178,-627,682,-103,243,-667,-110,813,-898,224,-947,937,-93,-552,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWhitespace():boolean",
            new int[]{-953,-638,593,599,-863,-369,513,-463,-1000,286,578,-35,208,-1000,381,1000,1000,-1000,-495,532,-407,-1000,1000,-183,134,-626,-150,416,-136,589,-1000,879,-1000,1000,-1000,565,1000,630,120,-579,-1000,-141,233,-342,-455,726,-7,-1000,-1000,53,158,-779,257,643,-492,88,-953,-220,103,529,-161,-786,679,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWhitespace():boolean",
            new int[]{410,94,635,890,-270,318,-729,60,652,842,108,922,623,66,-250,900,-869,875,463,-515,-555,-713,-209,848,581,-881,308,-4,192,885,173,780,-925,-480,528,90,-734,-294,-509,-570,344,-392,543,722,495,-358,-566,-312,95,514,368,-574,184,252,-779,913,18,-822,796,87,-891,860,720,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWord():boolean",
            new int[]{-108,-331,546,872,914,303,643,-87,-359,374,-785,436,-49,-379,-391,-728,-443,-459,-81,-467,472,988,392,793,-982,29,-534,-382,-85,155,-862,827,775,28,316,-723,-46,298,572,-223,83,709,283,796,-119,-282,534,67,-147,-864,-653,851,-641,-52,-181,705,-827,364,569,-112,-830,-561,-471,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWord():boolean",
            new int[]{301,182,225,-469,463,-1000,-41,-342,-258,-64,44,1000,-612,-358,1000,-1000,564,-1000,-1,-182,-343,9,1000,-1000,375,560,-603,-538,551,-262,1000,932,-1000,-242,27,519,-152,-41,-290,-906,-355,427,-502,-94,-285,-903,-897,-121,736,569,1000,112,-115,357,416,487,75,-825,-911,-760,-102,-1000,542,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWord():boolean",
            new int[]{226,434,302,913,163,372,299,-861,402,82,679,-832,-758,-170,717,-788,684,185,301,-596,-622,-38,-728,726,-86,-148,882,830,169,-196,831,595,-700,312,231,-318,512,-901,-348,165,-824,-593,-64,369,90,57,-113,482,-270,786,-888,-819,165,961,315,582,-979,-284,-827,-885,294,792,-470,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Character:Og==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "peek():char",
            new int[]{737,-404,-342,-345,63,-202,868,532,934,-381,922,58,153,-926,-241,121,805,-912,316,-593,949,-881,-16,988,511,68,429,663,-245,970,-370,-112,525,205,-212,683,-222,-770,143,-557,-158,-745,672,272,-701,642,-860,117,440,-643,40,-222,-189,294,-396,691,-449,-47,222,-741,935,590,388,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Character:AA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "peek():char",
            new int[]{201,128,353,1000,-254,-1000,-1000,797,939,1000,172,385,260,171,-849,110,356,1000,-456,292,832,-1000,-1000,-631,-406,-1000,-1000,-953,198,554,-361,514,-20,-427,43,-506,945,-181,85,596,-102,800,68,522,444,-241,-305,518,-463,385,-280,-606,394,-58,-837,-133,828,-392,398,-128,-182,99,1000,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:MG51bGwrMHg4MDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "remainder():java.lang.String",
            new int[]{497,704,-104,211,-906,315,-853,-131,192,976,-20,54,-80,948,12,192,345,896,701,989,704,-902,-114,-989,298,252,730,-126,245,-749,342,-137,-619,-758,589,-966,515,384,709,484,988,286,194,337,-8,860,-875,-773,507,-525,-342,585,-170,715,652,-133,-307,802,-403,6,719,-235,-61,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:RnprWDQzUVJjCUZvdi9xZwlQbhkg", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "toString():java.lang.String",
            new int[]{-872,450,-558,742,855,-288,-860,25,-293,-899,959,377,-981,-598,-462,446,-12,-493,-920,-303,337,-59,353,-101,-331,-679,-572,-329,-55,-570,-20,-332,867,557,-324,467,410,202,-156,260,183,-624,390,-976,-113,-713,778,25,731,-543,754,230,350,0,-531,463,-512,169,675,16,-459,107,722,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:SmZVYVRQZXlwSmJFei5HQwpICl8geg==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "unescape(java.lang.String):java.lang.String",
            new int[]{-434,-400,-1000,373,-97,-553,-228,-629,-229,-162,156,673,-117,400,224,-31,35,845,255,180,-995,185,-72,775,-715,-533,-3,-355,-498,-113,58,-528,600,1000,-1000,-468,-54,-281,153,-416,199,319,-25,192,-975,-453,-923,182,-1000,-790,699,704,1000,-714,-652,413,-309,277,90,-837,855,135,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-177,368,-442,-671,-526,30,-12,76,-1000,-132,-206,82,379,627,-817,-462,633,398,-173,864,206,-269,287,-1000,-940,391,406,192,187,-487,-466,-2,452,734,787,926,781,16,969,822,556,-157,-322,1000,872,-88,-540,-556,1000,-804,308,1000,1000,-247,-1000,-971,399,-364,-70,863,32,451,-390,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{127,883,76,72,891,-680,554,262,-102,65,519,-752,153,-77,747,-265,985,566,114,331,826,-406,925,-146,-542,-172,630,554,672,444,-860,-881,-981,861,237,-457,536,-160,-722,112,-820,-302,-181,130,55,-837,623,320,-524,24,49,278,408,431,785,841,-126,-519,561,780,572,-459,-438,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-1000,419,-590,521,278,-510,-777,-812,-405,1000,1000,-790,-1000,-748,23,562,1000,334,-629,611,-683,-269,1000,126,107,-1000,-429,786,-440,1000,1000,-1000,1000,302,419,-1000,-40,1000,1000,432,447,-551,1000,-486,867,172,1000,418,-443,146,-32,-784,821,-555,1000,-1000,1000,-1000,1000,402,-1000,1000,-497,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-806,726,1000,-482,511,-1000,668,-696,332,1000,1000,683,306,491,-415,1000,385,417,669,713,-914,-788,690,-8,-323,61,-400,-50,1000,970,822,-552,1000,-1000,-461,-490,-840,61,-887,-653,145,664,-459,-790,-23,821,-609,-966,823,-755,-169,803,-293,-1000,-1000,615,-655,779,588,955,-721,293,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{611,310,198,904,914,-676,313,978,298,407,-478,-1000,-771,1000,614,733,-721,-841,293,718,-604,1000,973,440,1000,-1000,-702,-1000,710,-23,-648,-1000,-1000,-1000,517,79,-129,1000,184,717,278,564,-1000,1000,1000,-409,-90,21,83,-396,821,339,61,-1000,202,1000,468,-735,158,1000,194,-432,998,697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-526,-121,-536,602,-13,113,-966,943,745,699,-259,-81,-727,324,36,-63,888,851,444,930,-12,-544,877,254,291,-1000,249,374,73,839,-561,-915,-381,126,295,939,152,-353,1000,-758,233,682,-912,1000,427,708,-678,-1000,-149,-551,-166,214,157,-267,-1000,198,-36,368,-1000,-1000,-5,-375,-189,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-1000,-555,378,661,74,-1000,279,1000,513,-290,-1000,-652,1000,-1000,-1000,-235,198,162,1000,-310,189,716,-540,1000,9,1000,-241,864,1000,-253,504,-1000,-958,1000,999,-1000,1000,950,1000,-1000,1000,675,1000,773,708,-1000,1000,1000,-576,-65,1000,1000,-1000,-204,474,597,-525,72,-508,398,304,-458,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{699,1000,-1000,991,-1000,-251,-178,1000,1000,-709,405,-210,541,1000,-582,679,-632,1000,-220,1000,651,148,-1000,-1000,-394,1000,-134,1000,100,-1000,-1000,1000,248,1000,57,-849,1000,196,-1000,-1000,613,-1000,1000,457,-1000,1000,1000,1000,-689,-761,-1000,-654,935,-280,941,376,-1000,-406,-1000,1000,-1000,1000,-821,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-115,-502,-296,44,607,221,960,-498,-275,546,87,702,-576,658,-276,-345,-129,537,-717,486,277,-1000,-114,-1000,381,183,54,-541,-1000,-246,-234,17,677,882,-635,185,103,-766,140,417,21,-613,-186,613,921,-246,-36,129,573,-34,1000,910,-367,812,951,-428,1000,57,-201,-1000,122,445,360,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{138,1000,-234,210,1000,1000,1000,-959,-727,-1000,571,224,588,-363,-23,135,413,-691,493,-8,-189,-941,-1000,593,565,-676,395,63,-502,-617,850,431,-1000,447,333,1000,-543,-486,-440,752,522,386,567,658,-876,-655,-17,239,271,60,1000,932,1000,1000,617,-1000,-1000,-841,1000,-525,-844,-1000,-674,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-545,376,403,16,-378,799,-857,105,-367,-1000,-532,356,-200,64,650,209,-319,-681,501,157,-1000,522,-233,1000,661,1000,311,671,-36,199,-433,610,297,-769,947,-472,1000,-518,-1000,281,-19,-705,1000,-537,-600,159,1000,-239,609,526,-20,-938,-313,-267,374,-211,744,-469,117,-376,371,384,1000,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{138,762,250,349,-394,-355,213,-1000,950,-625,-496,190,223,-406,1000,265,348,498,77,-832,356,1000,-734,-704,476,-1000,536,-1000,-40,-329,-522,-907,-2,-1000,-470,19,893,-657,-403,-1000,-144,-323,882,-917,-1000,406,-877,565,-940,-1000,-262,977,332,237,167,714,762,-96,-835,-279,1000,-922,-67,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{31,848,-345,-380,-70,704,-1000,-330,-148,807,390,135,-137,-198,897,-302,-94,395,441,-835,477,1000,-1000,430,123,-654,248,-343,-309,360,-647,-67,-692,-653,479,-1000,-110,-437,-94,178,-428,780,-654,-124,575,79,525,-301,-558,-917,-542,765,-265,341,724,-321,378,46,130,466,-38,134,-579,-784}));
    }
}
