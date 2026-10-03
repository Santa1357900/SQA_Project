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
            new int[]{71,998,-570,-785,-336,795,0,-72,235,389,43,-542,-811,685,145,-462,-102,-864,-876,-997,865,612,-16,824,-442,-637,566,50,-553,-541,977,-561,139,-240,3,237,-120,690,-157,-175,-852,-132,806,-234,-419,-8,844,-841,75,-706,-807,-843,-492,325,823,977,-729,912,845,-149,542,-332,-375,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "addFirst(java.lang.String):void",
            new int[]{-411,-480,436,-633,-463,748,331,104,416,379,682,-513,-637,434,-426,-758,92,-43,775,525,553,925,804,282,828,-758,-198,216,118,444,336,-469,-448,270,92,-671,-185,-366,-489,-973,-947,-336,-142,-506,-600,-508,979,-261,-394,-768,-978,-56,690,-779,803,-650,-642,-19,-850,-846,-30,-383,573,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "advance():void",
            new int[]{340,-465,66,302,587,198,179,-324,-608,929,373,-228,-864,-222,3,838,445,782,-652,-28,-604,-629,260,-188,478,937,687,-967,-221,-925,-164,-521,-239,-46,-619,555,-335,-537,-621,630,127,309,-209,-357,865,-922,-544,823,488,441,153,512,-635,-894,4,-545,310,-53,-119,563,-651,780,694,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "advance():void",
            new int[]{292,697,513,351,-44,-460,290,509,-42,-827,-360,-633,-229,493,-383,242,403,-183,-313,36,498,216,-757,261,901,-334,-111,845,-175,435,-539,-20,630,-915,311,24,-75,-380,687,-367,-792,-131,-728,-306,592,-958,867,-596,290,504,17,880,482,322,-997,61,949,889,988,327,431,14,390,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompBalanced(char,char):java.lang.String",
            new int[]{145,-8,-473,1000,-139,619,-1000,-28,288,806,-1000,1000,638,1000,1000,703,130,-1000,1000,-470,737,594,520,-896,362,1000,-150,1000,-1000,-1000,-553,1000,-643,344,127,607,-1000,-1000,44,1000,1000,-741,-518,310,1000,-1000,270,1000,1000,1000,185,403,1000,-1000,1000,-504,-885,-1000,-1000,-1000,1000,315,628,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompBalanced(char,char):java.lang.String",
            new int[]{-881,-902,46,401,932,-718,1000,522,-44,1000,-344,-51,-1000,-23,-215,-366,-1000,-325,-476,418,854,-1000,-77,-323,406,174,-1000,-183,282,698,403,482,-369,1000,7,-1000,-944,-770,393,857,-182,-836,490,547,699,1000,238,-244,184,-775,1000,486,1000,777,751,96,-205,374,-868,909,638,-1000,1000,-596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompBalanced(char,char):java.lang.String",
            new int[]{-1000,397,-228,755,932,-929,82,1000,1000,1000,-1000,472,-1000,-1000,-572,-230,-775,700,-242,-291,889,643,-1000,-700,1000,-1000,-1000,-935,-1000,95,879,-372,799,-309,1000,786,1000,1000,-1000,-1000,-1000,-556,-1000,130,-993,1000,-1000,-1000,-1000,-775,851,-796,789,1000,-484,-1000,-1000,-239,-868,1000,561,-1000,-1000,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompBalanced(char,char):java.lang.String",
            new int[]{-1000,978,597,245,733,-749,-497,252,1000,-33,-63,616,-679,150,-601,-17,-1000,711,-90,366,-841,304,-836,-332,-516,690,-1000,-404,399,88,103,579,209,-875,1000,-938,-1000,-287,103,554,934,-748,-56,-473,708,215,1000,233,-947,583,404,-184,592,1000,43,1000,-129,-595,1000,1000,1000,-842,189,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompTo(java.lang.String):java.lang.String",
            new int[]{36,357,-207,-727,700,62,-880,588,67,-902,16,-543,-78,468,198,903,665,250,169,-335,-726,-894,426,-756,-410,-171,-367,-921,913,994,-586,54,-604,-652,-429,939,-920,387,512,151,-490,-286,-527,223,847,-747,-272,811,-857,158,-644,-329,4,340,434,99,279,957,488,-411,576,884,-55,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:d3RydWU/", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompTo(java.lang.String):java.lang.String",
            new int[]{-766,-253,993,-215,-966,768,-66,-961,-851,427,403,-783,255,-694,-649,-852,488,-621,-905,647,618,268,749,-63,-998,83,-338,-901,953,-886,198,922,-243,-808,-647,983,808,340,-320,477,539,-483,-199,431,-281,-291,-631,265,481,-615,771,-638,-1,448,78,-702,-871,-813,817,695,-484,-536,-50,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:aRpYGC0tMHg4", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-528,-722,-1000,-535,743,-1000,-636,-132,-1000,-500,-296,-126,-102,1000,-407,88,-359,-937,-377,1000,-860,-287,807,70,268,-507,131,-319,-651,-799,-114,263,-1000,-67,-134,-1000,101,-1000,638,1000,342,-664,-267,-760,1000,-729,-920,-610,-858,772,-156,-39,852,-396,1000,943,1000,714,1000,-727,375,264,376,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDArGA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{158,-172,-1000,-130,904,396,198,-224,536,-1000,43,969,4,-1000,-719,1000,-1000,-208,-1000,-421,1000,-254,-414,219,193,-1000,42,-178,1000,369,664,182,-259,477,-400,-1000,1000,622,99,400,-21,1000,-167,311,-361,10,1000,557,207,235,282,351,-146,400,-610,-163,-823,56,-239,-1000,380,72,186,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:NTk3IC04NDIgYiA=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "chompToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-891,505,-670,-35,205,-842,-936,866,811,-294,842,521,-99,355,597,884,-293,476,926,145,843,150,485,-693,-94,-853,-216,-458,702,693,-683,249,764,630,-412,423,91,-887,61,861,-852,-120,-713,50,98,-711,-420,139,942,-171,453,-690,798,607,-254,-917,-93,444,-101,-407,102,-381,-894,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Character:YQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consume():char",
            new int[]{978,-884,-351,-381,352,-386,616,297,594,769,-825,-936,-492,462,760,693,140,-8,-297,489,-446,897,-52,362,431,355,186,322,820,100,-985,-353,69,-812,977,-834,-770,410,71,307,12,-353,-289,-105,-233,-931,-990,3,917,696,708,-370,-266,568,143,-580,-556,-483,-782,225,275,376,504,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consume(java.lang.String):void",
            new int[]{675,756,1000,-392,489,-1000,163,512,70,624,-423,106,-1000,989,1000,-890,1000,214,-252,-297,1000,1000,555,-1000,-899,-284,-1000,-755,-442,-675,1000,487,-254,-1000,1000,1000,-486,228,-221,-499,1000,48,433,-1000,-285,-766,286,-216,-249,91,-721,-75,-1000,-501,335,194,-683,964,-1000,1000,1000,-1000,-817,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consume(java.lang.String):void",
            new int[]{919,-159,-890,111,415,-535,-219,-612,720,920,682,701,879,-674,720,785,318,-374,96,857,-706,-178,568,-385,573,-338,640,487,-36,306,-934,-112,84,-704,496,-76,314,204,303,977,294,-212,-313,911,480,-864,989,261,-676,-647,829,697,440,661,-851,-711,763,435,-460,-320,154,664,334,-884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:RC02MTYyOTc=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeAttributeKey():java.lang.String",
            new int[]{-388,407,917,-297,996,633,462,101,-476,616,373,452,452,-666,-389,405,-365,-910,110,149,-870,-724,670,-529,-575,-911,-872,-280,438,61,879,-152,428,300,422,694,781,-368,124,-134,-739,533,673,-826,788,-321,780,-593,-971,-789,-359,-553,202,-577,358,202,330,-546,95,640,929,-191,-932,-670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:XzB4OA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeAttributeKey():java.lang.String",
            new int[]{-1000,-567,1000,-1000,1000,1000,-669,-1000,479,672,-1000,522,979,1000,-15,1000,-1000,-1000,-357,710,397,-1000,1000,1000,829,-887,-819,-98,416,-884,765,705,14,1000,587,801,1000,444,1000,-571,-1000,28,-31,-1000,733,213,1000,-1000,-191,-429,755,28,1000,-1000,879,240,-202,-378,-1000,1000,957,-1000,378,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:LTk2MQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeCssIdentifier():java.lang.String",
            new int[]{62,552,475,633,474,957,-451,-993,-937,991,-822,589,463,-703,24,314,-781,792,956,860,-984,881,-931,-538,412,733,-787,213,-715,-473,194,423,-187,-365,-961,520,-852,-834,-247,-635,194,949,863,409,-149,101,679,-906,962,-360,727,114,798,-730,57,731,-618,406,254,-806,-790,813,897,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:d19V", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeCssIdentifier():java.lang.String",
            new int[]{584,-157,-884,-1000,-1000,1000,-492,488,981,1000,-673,344,-521,-740,-424,-982,-829,943,-1000,-100,-954,-309,813,1000,1000,-1000,695,-238,-792,-699,-693,1000,934,-1000,-1000,-1000,1000,251,-1000,-350,-183,-1000,-1000,1000,-713,976,458,189,8,-975,776,-919,-1000,137,1000,-77,-235,320,-1000,1000,-824,-832,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4X1hoQjZfOW1kMFEya1hMdXA2", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeElementSelector():java.lang.String",
            new int[]{616,-157,1000,475,-117,-620,-111,-1000,-1000,-274,-507,437,302,343,419,-632,-1000,414,-906,-389,1000,-1000,861,-830,-200,426,123,-992,-903,59,757,740,735,1000,-891,-1000,962,1000,-1000,-371,248,-588,4,173,1000,104,186,176,850,-949,858,1000,-648,1000,336,-1000,782,-1000,1000,-227,103,628,-237,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:NS0tMHg4", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeElementSelector():java.lang.String",
            new int[]{-894,309,-280,1000,307,-1000,526,246,309,-68,830,708,-176,-589,-1000,-1000,-201,-735,-441,141,-350,-342,-317,-766,608,407,-1000,886,-868,-311,604,-435,767,802,1000,-1000,-1000,416,23,-946,-364,-439,400,409,-1000,695,-768,1000,-1000,-144,-831,520,-708,34,-536,-954,-535,-481,477,263,-988,725,587,762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDBlNTYx", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTagName():java.lang.String",
            new int[]{-795,499,-85,868,-182,-1000,-637,41,-168,822,1000,-64,-804,163,801,166,-1000,785,561,-159,206,-875,-246,674,-1000,-265,-379,-592,-335,-188,0,530,-576,492,-230,967,-927,-985,-316,-353,-339,1000,1000,791,-314,452,-1000,917,823,-761,1000,-142,284,852,132,-391,-1000,-632,1000,-889,-1000,-238,234,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhaC0tMHg4MDAw", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTagName():java.lang.String",
            new int[]{-761,-637,1000,-243,391,-777,348,-1000,1000,-273,-388,-453,564,-502,1000,-26,-217,417,561,-370,77,-931,1000,1000,998,100,-783,575,-319,813,806,534,428,-37,804,564,320,763,17,1000,480,1000,783,145,130,-644,-472,-1000,704,631,376,-262,-172,550,-1000,-345,368,-390,380,-1000,1000,1000,279,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:bnVsbAw2OTdE", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTo(java.lang.String):java.lang.String",
            new int[]{-733,127,-279,697,-540,-891,102,316,-628,905,464,650,395,168,39,-500,81,538,864,736,-280,-917,-152,-239,513,1,194,695,-552,370,-631,-264,630,-386,-345,455,-688,648,-61,489,159,196,-151,990,386,642,-390,524,319,-28,-950,-323,649,-153,-906,-499,-767,653,753,206,574,506,158,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeTo(java.lang.String):java.lang.String",
            new int[]{812,595,-749,-561,-233,387,551,920,-537,485,14,-342,0,-385,196,-467,0,248,-38,-615,-1000,-1000,1000,0,588,0,1000,-50,155,-83,741,-268,-639,52,-97,-565,-477,153,-1000,631,395,1000,527,1000,-627,420,-1000,166,1000,50,945,-641,-798,919,1000,-1000,1000,305,-402,164,892,162,91,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:aCs=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToAny(java.lang.String[]):java.lang.String",
            new int[]{1000,1000,1000,655,993,648,32,667,1000,-773,646,-1000,-1000,1000,-352,1000,-186,237,1000,-1000,300,1000,-1000,940,-113,-475,-729,711,375,922,483,380,-1000,-792,37,42,-1000,-941,1000,1000,1000,635,698,-400,1000,-366,987,422,-147,1000,-441,830,1000,1000,-518,1000,226,-427,-1000,1000,-255,49,-302,-63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:IC0tMzM4IG51bGw1d2dQdys5NTJlMzUy", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToAny(java.lang.String[]):java.lang.String",
            new int[]{795,-457,134,-952,290,352,-206,-247,366,300,257,-471,-536,-197,51,-962,330,-75,261,608,-171,285,-157,314,338,823,100,-884,612,-530,506,531,-609,997,-759,536,473,92,52,-744,-501,73,327,-672,280,-466,-340,803,127,716,-413,926,-639,-596,-333,-790,-53,764,633,-142,9,680,-51,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:LTkxMGZqLS0xMDAwbA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{1000,-516,601,1000,-549,1000,-213,-400,-918,-835,-615,910,797,-894,57,-1000,-1000,355,787,1000,-521,-30,1000,-35,539,-989,15,-1000,1000,837,-473,-58,80,-416,584,-248,-376,2,234,-372,587,-959,12,681,1000,633,-1000,-277,139,568,560,-1000,-1000,693,-479,154,-1000,-1000,1000,1000,801,-733,-232,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:AjB4M2U4GA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-1000,-193,-1000,-649,1000,-51,843,-226,-1000,921,503,1000,996,-902,770,136,-1000,1000,1000,-1000,-1000,-441,-946,1000,778,1000,-1000,377,-1000,-361,-1000,614,-600,1000,1000,-566,431,-408,-403,329,1000,-95,1000,1000,-92,314,1000,-35,205,-82,365,-1000,1000,953,-351,-1000,-1000,-1000,1000,-736,1000,234,1000,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:Xy4weDgwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeToIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-2,73,-88,240,624,930,-583,-132,-82,-784,735,-722,236,-707,-520,586,-123,-132,181,-128,421,-101,-207,-438,-744,-989,232,-644,687,707,904,-968,7,706,58,-276,148,-308,984,-687,160,-537,-433,491,619,32,-481,734,-674,568,716,-65,-850,-759,528,-239,712,0,-266,965,-215,131,-288,777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWhitespace():boolean",
            new int[]{214,408,-1000,341,742,-966,968,177,-414,478,-1000,972,-1000,-552,-608,660,-1000,463,1000,966,880,-460,1000,-289,527,375,-420,-1000,-342,-1000,403,-102,1000,-1000,930,-463,-1000,-114,1000,75,-195,360,1000,1000,-1000,-376,439,404,-57,-1000,1000,988,-810,149,586,-332,540,-221,-222,1000,1000,-400,-183,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWhitespace():boolean",
            new int[]{-729,730,258,496,683,260,657,-30,414,255,965,-689,166,-438,552,-836,864,-204,984,-952,586,718,41,161,473,464,316,-896,827,318,-294,-307,-924,-471,-680,-175,766,9,-343,-254,361,-349,256,998,252,491,-567,-148,81,-34,-778,-945,264,96,181,-615,-591,620,-664,-312,-746,-216,206,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:VWg=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWord():java.lang.String",
            new int[]{0,258,607,-303,-459,-1000,97,1000,1000,1000,981,1000,892,273,433,218,365,-295,-537,256,-737,324,1000,-1000,955,-429,-312,129,-829,406,269,-360,211,-829,679,109,740,981,1000,395,1000,106,-1000,-240,-424,-301,643,212,-342,-816,-774,-1000,989,-1000,609,732,-446,1000,-864,-1000,469,211,1000,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFm", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "consumeWord():java.lang.String",
            new int[]{894,910,184,431,-58,723,373,-729,-747,234,-682,-298,576,102,357,-916,543,-240,864,-715,-483,-738,921,-192,124,203,-816,808,669,-469,-75,-305,-320,418,470,-260,-319,-66,-799,142,949,241,-818,310,-487,241,-947,816,-90,168,-704,-603,346,509,259,689,-191,789,360,926,-582,-732,799,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "isEmpty():boolean",
            new int[]{317,103,63,-467,-850,-868,923,166,558,-72,-883,-794,-90,174,676,-985,814,460,-592,-203,440,429,87,-562,458,766,-810,600,-230,-403,801,-13,340,-28,106,836,351,-395,510,986,-278,812,823,-394,-448,-355,174,-364,-544,675,379,304,965,-460,320,710,-848,-576,575,-610,489,-26,-999,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "isEmpty():boolean",
            new int[]{555,-210,-719,-43,678,620,529,284,-148,624,994,984,-677,-288,580,-351,155,-201,-485,-292,752,-903,-230,-614,-145,41,78,-984,814,-696,309,64,-889,-497,800,282,-965,-328,827,-211,-834,-668,-210,546,-85,-919,984,686,-261,-940,776,-524,-156,-695,-47,302,215,-922,600,-4,615,-440,-611,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchChomp(java.lang.String):boolean",
            new int[]{480,745,-1000,-1000,-1000,164,-742,-1000,1000,-1000,815,-611,-3,352,-399,655,-1000,-750,-884,1000,-32,273,-223,-856,-201,-144,-525,-1000,456,-34,488,-493,1000,-844,540,264,-798,-1000,-676,1000,460,344,-115,-707,-27,-270,441,247,-534,-639,-1000,1000,398,755,-642,862,586,858,-673,-28,-514,-301,122,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchChomp(java.lang.String):boolean",
            new int[]{-978,182,233,-200,884,59,-554,-416,957,89,-307,385,-216,631,646,-688,497,30,-64,-35,-581,327,-722,127,276,-951,237,155,447,932,-301,-884,-630,-633,899,36,-330,611,-476,292,914,348,-157,128,-52,68,-942,-436,723,-97,-810,-452,240,-943,640,-902,-233,770,306,403,-139,-106,-368,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matches(java.lang.String):boolean",
            new int[]{-453,-770,-804,411,160,785,78,-27,-53,-790,-290,-278,-895,738,565,-89,-156,282,-324,339,793,407,262,819,-426,546,60,-668,820,-487,733,852,721,473,113,665,662,-322,1000,-28,-500,447,803,516,607,174,-639,-976,-88,466,-23,-599,882,884,903,737,-367,-746,-703,464,159,-2,-981,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(char[]):boolean",
            new int[]{642,296,-873,-204,374,727,30,614,563,242,356,-131,-385,571,602,-471,-432,557,54,-43,-363,975,-829,-489,-421,722,559,859,-544,-162,-889,-1,813,-520,452,-933,-504,970,-779,-568,392,-656,910,-745,-785,-546,-997,-239,134,594,964,761,520,886,273,659,-631,-815,-854,-903,79,176,-164,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(char[]):boolean",
            new int[]{-880,-673,892,971,-1000,1000,-1000,-669,-913,-472,-703,241,-318,1000,-1000,-469,658,-220,-1000,1000,-527,707,474,-145,-764,390,-1000,-631,1000,841,378,1000,-434,1000,844,-1000,-1000,637,-949,-1000,-205,-16,-972,1000,-751,-69,-451,-238,-771,362,278,136,-805,1000,-205,-1000,-1000,-668,733,-315,1000,-373,-670,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(char[]):boolean",
            new int[]{-850,-527,-152,-643,338,-566,-136,132,-774,593,366,-104,921,-808,-1000,1000,969,524,720,97,-409,-448,1000,786,98,-227,-229,-1000,1000,-932,1000,619,-1000,57,845,-132,1000,-1000,689,332,-582,-163,-1000,1000,-799,650,-242,613,-643,432,-980,-1000,-836,-865,-866,-124,-726,-170,319,710,-318,1000,-29,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(java.lang.String[]):boolean",
            new int[]{-876,-997,-820,628,-675,-524,848,-727,-694,138,-967,-790,636,159,-874,-196,-535,-708,-172,-364,-644,934,-381,96,-341,-264,-588,-858,-643,-416,366,96,633,-800,-98,-351,657,296,-219,-117,57,-721,-194,-969,397,-214,-845,943,600,44,679,-197,-304,-44,-444,483,564,129,667,116,368,618,724,971}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesAny(java.lang.String[]):boolean",
            new int[]{-1000,-853,-1000,1000,10,-71,-411,-1000,-532,699,1000,-1000,1000,334,-121,-1000,-1000,106,-239,1000,301,250,258,-657,-796,117,-1000,-398,-1000,-319,-585,-29,363,623,-453,577,-457,742,-929,-1000,1000,-751,-933,-1000,790,125,307,980,-298,555,862,-472,334,-1000,143,-384,-740,804,666,-947,156,464,188,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesCS(java.lang.String):boolean",
            new int[]{2,548,991,964,-46,301,777,-332,-173,-173,758,-68,-284,245,-145,-284,-576,767,-478,790,-242,758,209,-336,735,436,-870,114,763,875,-848,412,863,-120,-283,564,479,-697,-808,-844,584,-584,220,-301,-618,-940,-676,790,180,471,-760,838,-244,920,-674,649,906,-400,-301,-302,758,-454,765,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesStartTag():boolean",
            new int[]{250,385,-451,-8,544,-373,70,818,-664,668,-723,10,947,239,493,698,-31,-172,306,461,355,867,827,-158,-947,695,-943,-218,-137,787,-801,-307,-313,-89,751,741,-707,33,732,964,334,95,508,-143,-569,112,317,180,870,-590,-169,-74,-318,309,880,-400,702,47,785,-704,581,-654,-315,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesStartTag():boolean",
            new int[]{-799,-944,337,633,890,591,-172,-913,351,622,-659,415,-412,820,-79,-442,861,-762,154,-394,-263,-638,-804,-231,348,883,-440,592,279,154,727,62,-60,445,630,177,290,305,582,561,-646,619,333,-284,79,-741,-424,-346,-227,650,461,-202,647,-11,450,-714,-260,420,194,-883,944,-12,-977,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWhitespace():boolean",
            new int[]{950,-645,-106,193,-269,856,966,-16,385,-994,96,-526,-359,307,102,251,-875,-182,271,14,-731,862,-53,-111,792,-966,-630,-850,69,-695,678,-956,-148,-89,273,-280,708,890,-225,815,-740,610,728,-832,872,469,-123,361,767,29,-635,272,722,320,-482,706,490,-257,-983,-710,-147,-887,209,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWhitespace():boolean",
            new int[]{-181,-514,97,-960,533,400,-134,457,-458,-918,-634,-659,847,-666,231,729,-776,864,708,80,-525,30,494,-969,425,793,225,164,-598,-931,-115,871,8,525,707,659,-69,69,-810,852,265,752,-260,533,-656,630,149,-222,456,233,-365,774,-34,740,475,-768,356,-320,-581,674,-523,-938,-7,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWhitespace():boolean",
            new int[]{920,173,-401,557,826,11,-160,342,-336,252,936,-240,-400,-968,-758,831,856,362,950,-863,201,2,808,476,-864,-190,388,-115,670,-839,-156,989,-354,-97,372,982,532,489,-346,50,290,-579,-534,19,494,-49,-701,-830,213,-898,381,722,-647,506,799,-685,-895,291,541,948,-25,318,-746,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWord():boolean",
            new int[]{-78,-518,-456,140,916,-137,778,240,-467,-503,-508,552,741,-364,-750,-938,864,158,-988,-223,-753,453,324,77,-134,-545,524,-951,927,281,-21,-163,794,-861,574,-475,-702,-619,-456,581,885,-114,-928,457,-76,329,36,614,995,281,-215,905,-4,620,901,55,-488,246,500,-229,36,708,-147,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWord():boolean",
            new int[]{-693,-175,-367,508,1000,-1000,-913,150,-357,-397,-1000,444,-134,398,-746,449,-492,-553,-156,621,-257,550,991,803,88,-926,1000,-404,872,-778,-991,-846,-264,-780,-532,-710,-1000,-413,410,-389,-123,-1000,25,803,371,978,-722,367,1000,-771,521,605,-435,1000,-741,-522,-111,732,-953,1000,-966,578,773,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "matchesWord():boolean",
            new int[]{752,-374,712,-802,-702,-63,-936,-919,-981,-327,298,-908,-6,398,94,5,-495,984,512,508,-257,269,814,803,983,-926,-92,333,-835,-331,750,773,23,-85,-705,893,-313,-440,410,-591,42,-523,525,-472,24,978,-246,527,-503,-244,703,-592,416,-474,-741,-541,544,-61,278,346,168,-2,268,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Character:Jg==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "peek():char",
            new int[]{-988,494,8,-986,-575,-62,181,-326,-90,814,230,814,-115,-983,6,43,197,361,-951,-769,898,-692,406,553,161,-73,817,293,254,-816,-886,440,471,-672,-850,561,379,808,592,-118,-153,-47,517,110,924,465,916,-911,815,674,-82,84,992,-557,-162,-769,753,651,990,459,435,-750,632,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Character:AA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "peek():char",
            new int[]{-34,1000,865,253,-445,796,-13,-351,1000,-192,-75,319,-140,-701,-964,-30,-228,-567,-627,180,808,-989,-898,830,-993,1000,-936,-477,1000,627,-735,104,-377,-644,-391,-419,1000,-766,253,-1000,-996,-433,1000,-260,780,-1000,533,-570,-560,1000,-640,1000,-895,190,-770,-856,-1000,-741,-1000,-1000,-121,365,-549,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:KzcyOEAtODE0LjE5MQ==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "remainder():java.lang.String",
            new int[]{388,579,-747,-814,581,191,857,-728,-64,-791,-956,-728,418,443,-108,835,874,-354,273,298,-153,311,-329,-304,-728,-612,522,439,-422,-475,-443,-245,-254,162,204,511,534,661,271,667,-746,-460,-146,-728,234,843,-305,-355,430,-326,70,-799,505,26,674,-771,156,-824,959,855,460,100,-82,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:VwRNNEpXbFVhdkFHTzMvOA==", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "toString():java.lang.String",
            new int[]{885,-655,351,-512,-686,314,474,785,-381,839,-405,553,223,-466,-248,823,-802,-494,-501,292,-773,814,-892,-322,-41,-67,244,-645,185,-440,442,635,-499,681,151,978,-737,662,-643,714,-456,324,-95,437,522,-548,468,139,-717,-12,-122,-647,370,-709,976,119,573,274,-488,-604,-554,947,731,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:UGJq", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "unescape(java.lang.String):java.lang.String",
            new int[]{751,-896,663,254,406,792,-358,-833,-795,-1000,-309,374,288,782,-932,-103,118,-99,643,-841,340,1000,-485,650,214,-232,-268,-579,-73,-1000,-1000,-218,-68,1000,431,1000,-134,65,-268,-1000,1000,720,27,791,-262,-858,-521,-1000,253,-671,782,-1000,-1000,1000,31,-679,776,-588,567,577,213,-855,-972,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.String:Nl9BX2Nf", DEReplay.run(
            "org.jsoup.parser.TokenQueue", "org.jsoup.parser.TokenQueue", "unescape(java.lang.String):java.lang.String",
            new int[]{-97,196,201,807,210,1000,-1000,-603,-1000,-769,-503,1000,711,-601,-523,1000,-1000,926,24,-775,239,-287,227,400,-552,-1000,132,-1000,-1000,366,-703,-469,-1000,855,1000,1000,-1000,1000,-310,-658,1000,-154,542,-260,-887,-1000,192,-214,-1000,-1000,637,-1000,-669,492,1000,295,-1000,268,432,1000,968,-1000,207,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{713,-717,-483,467,-367,360,-356,1000,243,625,-1000,-665,502,9,15,979,-241,646,37,-657,1000,-1000,774,406,186,1000,-168,-54,-723,-263,317,-1000,675,893,430,525,-1000,-181,-476,92,-616,945,193,693,-1000,226,646,-444,224,-342,363,355,-443,-1000,946,-473,-234,-721,-241,-543,-52,-165,-894,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-167,33,-1000,-1000,649,1000,230,26,-145,334,1000,-131,1000,-32,-166,728,1000,1000,-435,716,731,1000,-30,831,-425,1000,-1000,1000,281,878,-1000,-18,1000,334,1000,1000,-575,123,955,-573,448,1000,814,1000,1000,-1000,1000,-1000,267,986,49,-1000,-718,281,-539,-219,1000,278,394,-544,167,-210,1000,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{204,645,676,-328,-75,-384,-546,-612,227,87,428,-337,-440,131,-107,-78,-222,-632,-310,969,-349,410,-64,589,15,457,-125,1000,380,526,-837,2,75,735,1000,137,-1000,257,636,-1000,23,249,287,-284,672,-394,804,-1000,-724,-356,-1000,-149,-393,118,170,-833,-336,918,25,547,1000,-129,175,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{337,114,-216,-555,-98,264,-243,945,337,298,948,-781,351,482,-399,855,264,-341,207,37,-812,911,-211,982,-310,1000,-493,495,0,-91,-428,-849,886,436,550,552,-1000,-32,-100,-545,-55,982,683,733,-76,-188,646,-1000,-280,-289,-450,-106,-24,-672,-248,-644,-111,113,33,272,406,67,-661,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-1000,-129,647,-587,-431,-1000,-418,-711,-614,298,55,553,-861,-1000,-39,148,-1000,810,304,920,-218,-261,1000,-567,723,164,-210,821,-459,440,-779,438,-773,-796,851,670,-907,93,260,6,-298,599,-802,-594,-288,-468,-99,-317,61,657,-138,-1000,296,-1000,790,-770,-290,-585,-731,303,794,233,-357,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{100,462,-580,288,-780,1000,-240,-865,-1000,1000,-235,-99,-430,101,171,-130,205,-1000,-193,-678,1000,839,-317,-143,19,1000,479,410,1000,802,460,1000,312,149,-989,-955,907,571,-1000,-50,445,-2,-1000,-47,95,672,148,154,-258,-1000,1000,321,516,-198,-597,-501,-363,-591,168,-231,-781,-1000,4,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{903,657,-40,-1000,-163,1000,1000,739,1000,1000,406,-673,-591,1000,-289,-457,1000,596,-1000,-76,-1000,-327,-1000,1000,-344,1000,1000,1000,652,-105,421,-731,-210,242,246,-1000,129,1000,1000,-121,-849,-474,-208,412,1000,689,-1000,-343,1000,-474,-1000,-220,1000,846,-552,672,-1000,-342,429,1000,-869,389,-145,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{484,437,-1000,-1000,-322,822,1000,964,1000,1000,1000,-1000,-504,836,270,-969,717,703,-901,-252,-1000,-769,-319,-1000,-362,626,733,-273,412,-370,129,1000,1000,580,1000,-1000,469,1000,839,85,-488,-246,284,623,-255,845,-747,-217,-1000,-239,-743,-329,1000,1000,-537,425,-1000,623,178,1000,-844,682,64,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-363,990,-10,493,295,-631,508,305,-506,239,388,883,-1000,1000,-648,577,259,170,538,-805,-303,-786,-400,-154,822,680,294,1000,176,922,882,568,-1000,-411,-204,682,400,-454,349,360,-163,1000,-253,161,832,-319,319,-139,244,336,-314,166,-979,-759,771,115,-30,-825,1000,-547,-880,-638,-692,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,708,26,365,152,207,197,1000,995,53,786,-1000,1000,10,21,-220,-514,-1000,-934,217,702,-1000,-1000,1000,986,-332,676,553,-411,-387,-210,-673,63,720,391,666,332,-994,249,851,596,-967,-501,-189,-422,-824,560,-221,-961,1000,721,-105,686,882,683,52,-371,637,-1000,-1000,-605,461,-577,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{-563,-318,-119,-954,921,362,-6,45,-74,-334,-878,-787,-140,-952,-373,-426,-276,469,463,-144,-973,987,-665,508,90,-867,831,-177,568,-749,-873,-534,-919,920,283,300,210,-649,-66,-160,-193,-793,-702,-123,-461,693,-231,853,910,946,134,437,-363,-223,424,-586,-857,645,803,451,-774,-656,484,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{565,-141,-535,14,-1000,-1000,541,1000,-269,160,1000,1000,573,686,1000,-625,1000,-233,-1000,-205,1000,-542,75,1000,51,-375,85,818,-600,-1000,1000,-406,-267,279,1000,-824,-485,868,-886,1000,666,-337,-494,483,-536,999,64,-511,-1000,581,1000,-1000,-1000,1000,1000,-136,-1000,1000,-421,-867,577,-24,-1000,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{606,604,-954,-483,-502,707,-1000,859,29,719,101,-1000,-101,1000,546,-165,-613,398,-155,558,-214,400,-1000,1000,1000,-332,725,-419,-120,-715,655,297,-1000,920,161,-13,400,-1000,-429,562,-1000,-1000,-151,-719,-776,596,-1000,666,-1000,1000,-179,1000,-97,949,1000,139,-267,761,786,-102,-853,477,-58,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{-404,35,409,243,139,282,768,568,253,310,297,657,469,-952,-22,-63,675,1000,191,224,-973,872,-25,25,-721,-590,1000,-60,417,-905,396,826,-842,729,113,358,1000,-316,345,1000,-193,-325,-90,-741,-710,288,135,-531,468,-301,-296,76,-730,-780,608,-791,-1000,-468,244,128,-862,-193,495,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,1000,147,-1000,-495,401,253,836,-1000,-395,-795,303,622,645,-373,-1000,286,998,-25,1000,-259,-1000,39,715,-3,-1000,-836,-761,-1000,-514,-479,-286,-201,1000,-126,401,-65,-523,133,557,-1000,264,-223,-1000,154,-949,-1000,-923,165,-690,-773,-22,1000,1000,1000,-219,552,-362,1000,-566,-137,1000,344,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-323,1000,-222,239,204,-758,492,432,-511,-1000,-162,-1000,-319,-731,-695,1000,-887,267,1000,-400,-285,-709,1000,-522,167,258,1000,11,-1000,1000,133,579,906,-1000,-1000,-1000,1000,1000,-592,-928,-1000,887,-1000,173,-23,671,35,-1000,685,-570,1000,23,773,1000,1000,-944,402,933,161,339,-286,1000,384,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{618,1000,1000,634,280,-278,1000,-553,-500,-1000,169,861,-1000,-1000,1000,1000,689,-833,-365,-1000,793,748,752,1000,193,1000,-1000,-1000,1000,-1000,-1000,869,738,1000,163,-322,-1000,561,-326,617,1000,900,1000,1000,146,1000,-1000,-1000,116,1000,-468,1000,-1000,1000,-1000,-1000,251,458,211,1000,738,-1000,-156,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-693,-1000,137,-801,-13,391,-195,-1000,638,-43,-113,-401,-541,-478,1000,135,348,-1000,252,-682,-378,291,1000,1000,70,-641,-1000,-1000,-324,-821,-31,-503,1000,767,654,-1000,-494,-130,43,1000,-392,562,-211,1000,621,-73,603,-1000,-219,532,-629,-1000,-511,-1000,145,81,-728,-144,0,156,-43,253,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{-60,-777,721,309,569,-381,1000,890,-804,300,-1000,-212,1000,469,315,1000,-893,902,-1000,-1000,-235,886,330,-333,854,-24,594,825,1000,740,-1000,895,-113,-1000,-946,-1000,91,-1000,-142,-72,513,-954,-463,-564,1000,1000,-466,1000,-822,-257,581,783,1000,-69,580,-830,1000,-95,-24,-1000,239,-459,-789,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{-260,774,-171,-352,341,-176,-300,724,423,0,-268,-615,-435,-33,-975,389,-582,671,1000,-796,-367,-601,-984,533,509,18,-710,603,498,-52,-1000,1000,145,224,-538,526,628,-854,966,450,-448,419,-604,-264,-1000,528,966,670,778,-172,1000,-1000,-736,1000,1,85,1000,885,-847,-712,199,30,-97,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-196,401,156,1000,-824,347,579,-170,-1000,457,-593,-779,318,16,177,-858,1000,492,-306,-1000,-1000,-972,-228,174,-546,-913,685,478,-419,418,694,-555,135,-1000,-324,-1000,447,-642,83,-165,228,-509,125,-820,994,621,338,-1000,2,-23,-510,-976,370,731,-1000,943,1000,-600,-566,148,-265,649,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{952,-446,-951,152,17,-810,200,974,-1000,-229,-689,-276,40,1000,755,473,-773,-164,-851,-783,-105,925,845,-333,762,211,981,-1000,-138,1000,-73,928,933,-1000,-1000,-928,229,-1000,-1000,-409,288,-1000,244,-933,197,1000,-1000,-71,-1000,-758,-284,230,570,1000,-608,-114,412,-131,229,-221,-1000,-430,-531,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,1000,927,237,531,524,536,-430,-650,-495,-507,881,346,-1000,-102,995,-420,-270,-1000,-393,-1000,1000,249,1000,670,-1000,447,1000,621,-419,-708,-432,276,476,51,1000,-1000,-1000,-1000,26,93,427,1000,-584,290,486,21,-767,-457,1000,535,-54,-627,1000,-58,947,643,-4,319,787,-877,871,-281,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{-473,400,172,546,160,-13,24,-265,-460,-274,189,-1000,-718,240,-203,-1000,845,-296,-640,-1000,-843,-1000,423,-271,450,-308,-1000,-458,143,-959,-257,-1000,198,565,300,-41,450,213,290,-198,-399,-306,-872,-584,-720,400,637,652,-984,-1000,-486,-424,-382,-174,921,947,2,51,-433,434,-177,-892,-20,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{663,-525,947,-616,-436,673,362,-14,387,546,672,-721,126,180,497,-485,346,601,-211,718,-988,676,243,-255,-238,-602,-45,418,-360,-438,312,820,261,242,474,-77,736,-329,508,525,-739,128,281,-798,233,272,-446,386,992,-21,-646,-967,832,214,-316,417,823,-310,-120,508,132,-820,-441,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{64,860,226,-124,-340,-313,-739,421,-390,-581,-942,-475,-336,-139,450,167,238,615,-153,-265,184,-669,-307,-921,-326,74,332,936,-637,-754,-143,750,-24,-157,995,-48,776,83,733,-410,-986,-599,-541,908,-670,-339,-310,902,405,56,154,-770,-359,610,914,-102,-438,-524,-476,-904,-199,-735,784,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,508,947,-183,1000,673,501,-14,-1000,546,149,1000,126,-962,497,979,-1000,462,-19,-114,-484,681,243,987,-1000,-1000,210,1000,1000,607,-1000,135,-239,-905,114,-77,-1000,-543,-477,-391,488,362,1000,79,81,-462,451,-555,739,1000,50,688,-1000,286,-816,-194,741,1000,335,508,-312,393,-441,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{110,324,-201,-227,205,125,459,1000,407,123,-207,49,-219,-1000,-286,-85,332,-994,-1000,594,629,1000,-738,1000,1000,-1000,259,-360,509,-416,-1000,1000,-882,-951,489,-391,141,1000,312,213,1000,-271,361,-320,909,-245,408,-934,-254,26,-304,798,-974,-748,1000,157,1000,505,-1000,-138,895,-17,323,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-417,-465,-22,-1000,-366,-1000,86,1000,-1000,1000,333,42,465,-250,536,-231,827,-263,-554,-1000,222,1000,845,1000,583,-1000,43,-78,531,215,-1000,-208,-1000,-591,411,-532,1000,608,1000,400,1000,-367,-316,7,1000,199,1000,-1000,-1000,-633,152,1000,-517,-997,1000,-21,1000,-355,-998,-792,636,941,-2,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{490,-810,1000,-113,-622,1000,516,-599,-159,-422,419,744,-946,-1000,-1000,669,-443,-1000,829,1000,-302,687,1000,-192,-590,280,-279,-427,-1000,903,-1000,-330,-1000,1000,-1000,1000,1000,819,-565,1000,45,880,469,507,710,821,1000,-152,-1000,32,445,786,426,370,-1000,-1000,-811,-328,-852,-1000,-1000,-243,206,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-648,322,-22,1000,476,850,896,1000,573,1000,-369,-90,1000,-1000,264,-214,815,1000,-640,-672,1000,318,-605,-905,-135,57,498,837,531,-986,-195,1000,315,-420,341,1000,-949,1000,-988,351,644,-736,-260,-879,1000,-131,-326,-702,183,-270,152,378,480,-1000,739,1000,1000,1000,-1000,-207,1000,670,1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-692,380,-201,483,301,453,399,523,644,191,145,49,98,-497,173,-60,838,237,-983,-661,985,803,-826,-151,663,-981,-359,597,298,-375,400,771,-278,-944,489,367,-492,802,-69,-334,953,-59,-441,-717,121,-689,-446,-531,440,159,-618,-162,-513,-899,741,644,826,751,-882,334,986,-526,590,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{163,-723,1000,-1000,-561,1000,-1000,497,438,-9,1000,1000,-12,-530,1000,1000,-875,463,-1000,1000,221,-291,-1000,-328,1000,1000,1000,786,1000,-175,-1000,-970,1000,-655,-1000,-174,1000,-713,1000,1000,-1000,-292,548,609,-1000,-587,1000,-56,-1000,880,610,387,801,1000,1000,1000,765,107,1000,118,1000,45,-1000,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-39,525,-1000,-246,-867,206,508,497,-599,-9,-1000,-1000,-1000,439,-631,1000,111,-667,1000,39,-1000,-291,311,-234,-954,227,100,-1000,-673,-478,400,970,603,567,-131,-199,-400,1000,-1000,407,1000,-292,361,-410,1000,1000,-1000,1000,540,738,-605,387,17,-1000,1,-978,389,-143,-768,-280,-585,-1000,557,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-257,225,1000,-178,262,-931,-551,514,0,-238,1000,297,-1000,-286,725,-1000,-17,893,1000,-749,559,63,-631,305,-778,-722,370,1000,-1000,-949,-1000,-1000,-289,-926,-1000,-850,760,-951,-728,1000,-276,318,50,691,196,-27,451,-1000,-1000,327,819,391,312,858,563,663,1000,1000,-838,796,-1000,-294,595,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-600,-221,-1000,-411,-687,900,419,-495,-804,-431,-462,-519,-269,679,-966,877,-225,-1000,1000,-307,-835,-291,773,-27,-12,1000,912,-1000,443,155,-1000,520,327,-4,214,-1000,1000,869,-1000,264,1000,338,-195,-410,550,486,102,1000,82,553,427,307,538,-756,-214,-175,-112,147,-876,-279,-189,-1000,829,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-417,-820,-1000,-705,-95,388,1000,1000,-362,428,613,-493,-96,-42,-394,-1000,-151,-679,1000,1000,834,-1000,837,-1000,-1000,771,-1000,774,-178,-748,-160,-1000,779,-1000,-450,-1000,-690,783,-1000,1000,-1000,-158,-1000,-497,828,516,965,1000,-709,-638,-1000,-179,-720,-443,1000,454,-1000,-1000,1000,-395,-1000,1000,-303,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-694,-34,66,-832,383,313,-528,-830,61,-29,-1000,964,-392,-434,-97,362,-514,-1000,-1000,-28,-125,630,238,172,-626,-586,-195,580,1000,-433,1000,68,-518,93,-234,-884,-1000,886,-1000,-393,-1000,496,-529,154,-31,703,-298,636,-94,-139,1000,-323,-209,-187,-234,98,44,-662,548,-906,71,-133,-1000,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-514,-112,231,-959,961,-374,-254,-227,-810,-97,847,-340,-394,-922,326,-60,731,-646,311,65,-938,-419,520,920,-282,-584,153,672,54,-730,684,377,-56,866,299,-841,-558,762,-183,-418,132,-302,521,615,647,265,553,531,-457,64,262,-998,11,-745,187,689,742,179,294,-308,-320,641,-557,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{13,-505,-99,99,1000,-627,-237,602,-59,202,512,-887,-270,-209,284,-632,-772,-1000,-1000,904,-90,561,-70,-644,-280,-390,189,-931,-722,189,-770,614,-290,211,-223,-897,-515,-203,-566,525,1000,-294,272,367,802,-647,300,-430,-773,528,-891,886,57,508,11,562,1000,582,420,284,1000,-547,216,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-902,-158,-46,-588,0,-762,-985,117,1000,-414,482,-933,819,22,-1000,87,-332,-288,-105,885,0,0,269,910,0,1000,0,17,225,-48,817,-678,164,-1000,-318,-830,-759,-309,-585,627,31,-734,0,-846,1000,372,-647,344,-1000,260,-1000,759,0,1000,-245,287,-751,328,-337,1000,-729,0,13,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-689,562,33,-380,386,573,-1000,-276,-873,355,-1000,619,-788,-673,23,-522,361,189,1000,-1000,-230,33,46,885,-849,1000,-918,-1000,3,-895,452,1000,902,696,865,447,-753,1000,-1000,-398,-1000,223,-233,-315,-375,848,-35,1000,1000,-1000,999,-827,1000,-702,930,-1000,47,-1000,1000,1000,-79,-1000,494,-947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-705,-403,418,-709,-740,578,-657,-435,121,201,-705,892,602,-840,-557,-147,513,577,-298,-140,-983,604,-999,883,265,276,136,-472,425,-966,721,-33,867,-81,304,858,-855,588,-670,-420,259,-960,294,856,-325,214,-819,162,580,-533,76,-816,920,-649,-310,-968,941,-925,-522,922,-873,-423,-998,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-417,-820,-1000,-705,-95,388,1000,1000,-362,428,613,-493,-96,-42,-394,-1000,-151,-679,1000,1000,834,-1000,837,-1000,-1000,771,-1000,774,-178,-748,-160,-1000,779,-1000,-450,-1000,-690,783,-1000,1000,-1000,-158,-1000,-497,828,516,965,1000,-709,-638,-1000,-179,-720,-443,1000,454,-1000,-1000,1000,-395,-1000,1000,-303,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-694,-34,66,-832,383,313,-528,-830,61,-29,-1000,964,-392,-434,-97,362,-514,-1000,-1000,-28,-125,630,238,172,-626,-586,-195,580,1000,-433,1000,68,-518,93,-234,-884,-1000,886,-1000,-393,-1000,496,-529,154,-31,703,-298,636,-94,-139,1000,-323,-209,-187,-234,98,44,-662,548,-906,71,-133,-1000,-560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.CombiningEvaluator$And", DEReplay.run(
            "org.jsoup.select.QueryParser", "", "parse(java.lang.String):org.jsoup.select.Evaluator",
            new int[]{-514,-112,231,-959,961,-374,-254,-227,-810,-97,847,-340,-394,-922,326,-60,731,-646,311,65,-938,-419,520,920,-282,-584,153,672,54,-730,684,377,-56,866,299,-841,-558,762,-183,-418,132,-302,521,615,647,265,553,531,-457,64,262,-998,11,-745,187,689,742,179,294,-308,-320,641,-557,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{713,-717,-483,467,-367,360,-356,1000,243,625,-1000,-665,502,9,15,979,-241,646,37,-657,1000,-1000,774,406,186,1000,-168,-54,-723,-263,317,-1000,675,893,430,525,-1000,-181,-476,92,-616,945,193,693,-1000,226,646,-444,224,-342,363,355,-443,-1000,946,-473,-234,-721,-241,-543,-52,-165,-894,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-167,33,-1000,-1000,649,1000,230,26,-145,334,1000,-131,1000,-32,-166,728,1000,1000,-435,716,731,1000,-30,831,-425,1000,-1000,1000,281,878,-1000,-18,1000,334,1000,1000,-575,123,955,-573,448,1000,814,1000,1000,-1000,1000,-1000,267,986,49,-1000,-718,281,-539,-219,1000,278,394,-544,167,-210,1000,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{204,645,676,-328,-75,-384,-546,-612,227,87,428,-337,-440,131,-107,-78,-222,-632,-310,969,-349,410,-64,589,15,457,-125,1000,380,526,-837,2,75,735,1000,137,-1000,257,636,-1000,23,249,287,-284,672,-394,804,-1000,-724,-356,-1000,-149,-393,118,170,-833,-336,918,25,547,1000,-129,175,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{337,114,-216,-555,-98,264,-243,945,337,298,948,-781,351,482,-399,855,264,-341,207,37,-812,911,-211,982,-310,1000,-493,495,0,-91,-428,-849,886,436,550,552,-1000,-32,-100,-545,-55,982,683,733,-76,-188,646,-1000,-280,-289,-450,-106,-24,-672,-248,-644,-111,113,33,272,406,67,-661,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-1000,-129,647,-587,-431,-1000,-418,-711,-614,298,55,553,-861,-1000,-39,148,-1000,810,304,920,-218,-261,1000,-567,723,164,-210,821,-459,440,-779,438,-773,-796,851,670,-907,93,260,6,-298,599,-802,-594,-288,-468,-99,-317,61,657,-138,-1000,296,-1000,790,-770,-290,-585,-731,303,794,233,-357,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{100,462,-580,288,-780,1000,-240,-865,-1000,1000,-235,-99,-430,101,171,-130,205,-1000,-193,-678,1000,839,-317,-143,19,1000,479,410,1000,802,460,1000,312,149,-989,-955,907,571,-1000,-50,445,-2,-1000,-47,95,672,148,154,-258,-1000,1000,321,516,-198,-597,-501,-363,-591,168,-231,-781,-1000,4,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{903,657,-40,-1000,-163,1000,1000,739,1000,1000,406,-673,-591,1000,-289,-457,1000,596,-1000,-76,-1000,-327,-1000,1000,-344,1000,1000,1000,652,-105,421,-731,-210,242,246,-1000,129,1000,1000,-121,-849,-474,-208,412,1000,689,-1000,-343,1000,-474,-1000,-220,1000,846,-552,672,-1000,-342,429,1000,-869,389,-145,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{484,437,-1000,-1000,-322,822,1000,964,1000,1000,1000,-1000,-504,836,270,-969,717,703,-901,-252,-1000,-769,-319,-1000,-362,626,733,-273,412,-370,129,1000,1000,580,1000,-1000,469,1000,839,85,-488,-246,284,623,-255,845,-747,-217,-1000,-239,-743,-329,1000,1000,-537,425,-1000,623,178,1000,-844,682,64,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "is(java.lang.String):boolean",
            new int[]{-363,990,-10,493,295,-631,508,305,-506,239,388,883,-1000,1000,-648,577,259,170,538,-805,-303,-786,-400,-154,822,680,294,1000,176,922,882,568,-1000,-411,-204,682,400,-454,349,360,-163,1000,-253,161,832,-319,319,-139,244,336,-314,166,-979,-759,771,115,-30,-825,1000,-547,-880,-638,-692,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,708,26,365,152,207,197,1000,995,53,786,-1000,1000,10,21,-220,-514,-1000,-934,217,702,-1000,-1000,1000,986,-332,676,553,-411,-387,-210,-673,63,720,391,666,332,-994,249,851,596,-967,-501,-189,-422,-824,560,-221,-961,1000,721,-105,686,882,683,52,-371,637,-1000,-1000,-605,461,-577,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{-563,-318,-119,-954,921,362,-6,45,-74,-334,-878,-787,-140,-952,-373,-426,-276,469,463,-144,-973,987,-665,508,90,-867,831,-177,568,-749,-873,-534,-919,920,283,300,210,-649,-66,-160,-193,-793,-702,-123,-461,693,-231,853,910,946,134,437,-363,-223,424,-586,-857,645,803,451,-774,-656,484,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{565,-141,-535,14,-1000,-1000,541,1000,-269,160,1000,1000,573,686,1000,-625,1000,-233,-1000,-205,1000,-542,75,1000,51,-375,85,818,-600,-1000,1000,-406,-267,279,1000,-824,-485,868,-886,1000,666,-337,-494,483,-536,999,64,-511,-1000,581,1000,-1000,-1000,1000,1000,-136,-1000,1000,-421,-867,577,-24,-1000,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{606,604,-954,-483,-502,707,-1000,859,29,719,101,-1000,-101,1000,546,-165,-613,398,-155,558,-214,400,-1000,1000,1000,-332,725,-419,-120,-715,655,297,-1000,920,161,-13,400,-1000,-429,562,-1000,-1000,-151,-719,-776,596,-1000,666,-1000,1000,-179,1000,-97,949,1000,139,-267,761,786,-102,-853,477,-58,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "next(java.lang.String):org.jsoup.select.Elements",
            new int[]{-404,35,409,243,139,282,768,568,253,310,297,657,469,-952,-22,-63,675,1000,191,224,-973,872,-25,25,-721,-590,1000,-60,417,-905,396,826,-842,729,113,358,1000,-316,345,1000,-193,-325,-90,-741,-710,288,135,-531,468,-301,-296,76,-730,-780,608,-791,-1000,-468,244,128,-862,-193,495,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,1000,147,-1000,-495,401,253,836,-1000,-395,-795,303,622,645,-373,-1000,286,998,-25,1000,-259,-1000,39,715,-3,-1000,-836,-761,-1000,-514,-479,-286,-201,1000,-126,401,-65,-523,133,557,-1000,264,-223,-1000,154,-949,-1000,-923,165,-690,-773,-22,1000,1000,1000,-219,552,-362,1000,-566,-137,1000,344,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-323,1000,-222,239,204,-758,492,432,-511,-1000,-162,-1000,-319,-731,-695,1000,-887,267,1000,-400,-285,-709,1000,-522,167,258,1000,11,-1000,1000,133,579,906,-1000,-1000,-1000,1000,1000,-592,-928,-1000,887,-1000,173,-23,671,35,-1000,685,-570,1000,23,773,1000,1000,-944,402,933,161,339,-286,1000,384,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{618,1000,1000,634,280,-278,1000,-553,-500,-1000,169,861,-1000,-1000,1000,1000,689,-833,-365,-1000,793,748,752,1000,193,1000,-1000,-1000,1000,-1000,-1000,869,738,1000,163,-322,-1000,561,-326,617,1000,900,1000,1000,146,1000,-1000,-1000,116,1000,-468,1000,-1000,1000,-1000,-1000,251,458,211,1000,738,-1000,-156,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "nextAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-693,-1000,137,-801,-13,391,-195,-1000,638,-43,-113,-401,-541,-478,1000,135,348,-1000,252,-682,-378,291,1000,1000,70,-641,-1000,-1000,-324,-821,-31,-503,1000,767,654,-1000,-494,-130,43,1000,-392,562,-211,1000,621,-73,603,-1000,-219,532,-629,-1000,-511,-1000,145,81,-728,-144,0,156,-43,253,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{-60,-777,721,309,569,-381,1000,890,-804,300,-1000,-212,1000,469,315,1000,-893,902,-1000,-1000,-235,886,330,-333,854,-24,594,825,1000,740,-1000,895,-113,-1000,-946,-1000,91,-1000,-142,-72,513,-954,-463,-564,1000,1000,-466,1000,-822,-257,581,783,1000,-69,580,-830,1000,-95,-24,-1000,239,-459,-789,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{-260,774,-171,-352,341,-176,-300,724,423,0,-268,-615,-435,-33,-975,389,-582,671,1000,-796,-367,-601,-984,533,509,18,-710,603,498,-52,-1000,1000,145,224,-538,526,628,-854,966,450,-448,419,-604,-264,-1000,528,966,670,778,-172,1000,-1000,-736,1000,1,85,1000,885,-847,-712,199,30,-97,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-196,401,156,1000,-824,347,579,-170,-1000,457,-593,-779,318,16,177,-858,1000,492,-306,-1000,-1000,-972,-228,174,-546,-913,685,478,-419,418,694,-555,135,-1000,-324,-1000,447,-642,83,-165,228,-509,125,-820,994,621,338,-1000,2,-23,-510,-976,370,731,-1000,943,1000,-600,-566,148,-265,649,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "not(java.lang.String):org.jsoup.select.Elements",
            new int[]{952,-446,-951,152,17,-810,200,974,-1000,-229,-689,-276,40,1000,755,473,-773,-164,-851,-783,-105,925,845,-333,762,211,981,-1000,-138,1000,-73,928,933,-1000,-1000,-928,229,-1000,-1000,-409,288,-1000,244,-933,197,1000,-1000,-71,-1000,-758,-284,230,570,1000,-608,-114,412,-131,229,-221,-1000,-430,-531,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,1000,927,237,531,524,536,-430,-650,-495,-507,881,346,-1000,-102,995,-420,-270,-1000,-393,-1000,1000,249,1000,670,-1000,447,1000,621,-419,-708,-432,276,476,51,1000,-1000,-1000,-1000,26,93,427,1000,-584,290,486,21,-767,-457,1000,535,-54,-627,1000,-58,947,643,-4,319,787,-877,871,-281,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{-473,400,172,546,160,-13,24,-265,-460,-274,189,-1000,-718,240,-203,-1000,845,-296,-640,-1000,-843,-1000,423,-271,450,-308,-1000,-458,143,-959,-257,-1000,198,565,300,-41,450,213,290,-198,-399,-306,-872,-584,-720,400,637,652,-984,-1000,-486,-424,-382,-174,921,947,2,51,-433,434,-177,-892,-20,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{663,-525,947,-616,-436,673,362,-14,387,546,672,-721,126,180,497,-485,346,601,-211,718,-988,676,243,-255,-238,-602,-45,418,-360,-438,312,820,261,242,474,-77,736,-329,508,525,-739,128,281,-798,233,272,-446,386,992,-21,-646,-967,832,214,-316,417,823,-310,-120,508,132,-820,-441,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{64,860,226,-124,-340,-313,-739,421,-390,-581,-942,-475,-336,-139,450,167,238,615,-153,-265,184,-669,-307,-921,-326,74,332,936,-637,-754,-143,750,-24,-157,995,-48,776,83,733,-410,-986,-599,-541,908,-670,-339,-310,902,405,56,154,-770,-359,610,914,-102,-438,-524,-476,-904,-199,-735,784,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prev(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,508,947,-183,1000,673,501,-14,-1000,546,149,1000,126,-962,497,979,-1000,462,-19,-114,-484,681,243,987,-1000,-1000,210,1000,1000,607,-1000,135,-239,-905,114,-77,-1000,-543,-477,-391,488,362,1000,79,81,-462,451,-555,739,1000,50,688,-1000,286,-816,-194,741,1000,335,508,-312,393,-441,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{110,324,-201,-227,205,125,459,1000,407,123,-207,49,-219,-1000,-286,-85,332,-994,-1000,594,629,1000,-738,1000,1000,-1000,259,-360,509,-416,-1000,1000,-882,-951,489,-391,141,1000,312,213,1000,-271,361,-320,909,-245,408,-934,-254,26,-304,798,-974,-748,1000,157,1000,505,-1000,-138,895,-17,323,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-417,-465,-22,-1000,-366,-1000,86,1000,-1000,1000,333,42,465,-250,536,-231,827,-263,-554,-1000,222,1000,845,1000,583,-1000,43,-78,531,215,-1000,-208,-1000,-591,411,-532,1000,608,1000,400,1000,-367,-316,7,1000,199,1000,-1000,-1000,-633,152,1000,-517,-997,1000,-21,1000,-355,-998,-792,636,941,-2,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{490,-810,1000,-113,-622,1000,516,-599,-159,-422,419,744,-946,-1000,-1000,669,-443,-1000,829,1000,-302,687,1000,-192,-590,280,-279,-427,-1000,903,-1000,-330,-1000,1000,-1000,1000,1000,819,-565,1000,45,880,469,507,710,821,1000,-152,-1000,32,445,786,426,370,-1000,-1000,-811,-328,-852,-1000,-1000,-243,206,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-648,322,-22,1000,476,850,896,1000,573,1000,-369,-90,1000,-1000,264,-214,815,1000,-640,-672,1000,318,-605,-905,-135,57,498,837,531,-986,-195,1000,315,-420,341,1000,-949,1000,-988,351,644,-736,-260,-879,1000,-131,-326,-702,183,-270,152,378,480,-1000,739,1000,1000,1000,-1000,-207,1000,670,1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prevAll(java.lang.String):org.jsoup.select.Elements",
            new int[]{-692,380,-201,483,301,453,399,523,644,191,145,49,98,-497,173,-60,838,237,-983,-661,985,803,-826,-151,663,-981,-359,597,298,-375,400,771,-278,-944,489,367,-492,802,-69,-334,953,-59,-441,-717,121,-689,-446,-531,440,159,-618,-162,-513,-899,741,644,826,751,-882,334,986,-526,590,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{163,-723,1000,-1000,-561,1000,-1000,497,438,-9,1000,1000,-12,-530,1000,1000,-875,463,-1000,1000,221,-291,-1000,-328,1000,1000,1000,786,1000,-175,-1000,-970,1000,-655,-1000,-174,1000,-713,1000,1000,-1000,-292,548,609,-1000,-587,1000,-56,-1000,880,610,387,801,1000,1000,1000,765,107,1000,118,1000,45,-1000,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-39,525,-1000,-246,-867,206,508,497,-599,-9,-1000,-1000,-1000,439,-631,1000,111,-667,1000,39,-1000,-291,311,-234,-954,227,100,-1000,-673,-478,400,970,603,567,-131,-199,-400,1000,-1000,407,1000,-292,361,-410,1000,1000,-1000,1000,540,738,-605,387,17,-1000,1,-978,389,-143,-768,-280,-585,-1000,557,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-257,225,1000,-178,262,-931,-551,514,0,-238,1000,297,-1000,-286,725,-1000,-17,893,1000,-749,559,63,-631,305,-778,-722,370,1000,-1000,-949,-1000,-1000,-289,-926,-1000,-850,760,-951,-728,1000,-276,318,50,691,196,-27,451,-1000,-1000,327,819,391,312,858,563,663,1000,1000,-838,796,-1000,-294,595,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-600,-221,-1000,-411,-687,900,419,-495,-804,-431,-462,-519,-269,679,-966,877,-225,-1000,1000,-307,-835,-291,773,-27,-12,1000,912,-1000,443,155,-1000,520,327,-4,214,-1000,1000,869,-1000,264,1000,338,-195,-410,550,486,102,1000,82,553,427,307,538,-756,-214,-175,-112,147,-876,-279,-189,-1000,829,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{13,-505,-99,99,1000,-627,-237,602,-59,202,512,-887,-270,-209,284,-632,-772,-1000,-1000,904,-90,561,-70,-644,-280,-390,189,-931,-722,189,-770,614,-290,211,-223,-897,-515,-203,-566,525,1000,-294,272,367,802,-647,300,-430,-773,528,-891,886,57,508,11,562,1000,582,420,284,1000,-547,216,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-902,-158,-46,-588,0,-762,-985,117,1000,-414,482,-933,819,22,-1000,87,-332,-288,-105,885,0,0,269,910,0,1000,0,17,225,-48,817,-678,164,-1000,-318,-830,-759,-309,-585,627,31,-734,0,-846,1000,372,-647,344,-1000,260,-1000,759,0,1000,-245,287,-751,328,-337,1000,-729,0,13,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-689,562,33,-380,386,573,-1000,-276,-873,355,-1000,619,-788,-673,23,-522,361,189,1000,-1000,-230,33,46,885,-849,1000,-918,-1000,3,-895,452,1000,902,696,865,447,-753,1000,-1000,-398,-1000,223,-233,-315,-375,848,-35,1000,1000,-1000,999,-827,1000,-702,930,-1000,47,-1000,1000,1000,-79,-1000,494,-947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.jsoup.select.Selector", "", "select(java.lang.String,java.lang.Iterable):org.jsoup.select.Elements",
            new int[]{-705,-403,418,-709,-740,578,-657,-435,121,201,-705,892,602,-840,-557,-147,513,577,-298,-140,-983,604,-999,883,265,276,136,-472,425,-966,721,-33,867,-81,304,858,-855,588,-670,-420,259,-960,294,856,-325,214,-819,162,580,-533,76,-816,920,-649,-310,-968,941,-925,-522,922,-873,-423,-998,-396}));
    }
}
