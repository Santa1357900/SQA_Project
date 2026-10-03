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
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "addClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-45,981,632,-1000,-353,-1000,-636,851,321,298,-163,-1000,475,-81,31,516,-269,964,1000,254,-1000,650,933,-1000,845,840,1000,1000,1000,-654,-368,-274,1000,176,-79,403,-797,-37,-236,-971,-175,123,-836,-109,776,-1000,-184,-279,-336,-1000,-184,-33,-1000,-1000,-755,385,606,461,246,-1000,-789,379,829,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,816,38,-1000,-287,-610,482,-22,-136,-1000,824,1000,-325,727,-800,-740,1000,-792,-295,1000,-490,638,-941,1000,-345,1000,-652,449,39,1000,862,-415,-76,692,-67,-406,-1000,463,648,975,-497,672,-1000,1000,279,1000,-433,-209,-824,401,1000,-14,414,838,-582,-489,1000,-755,-460,804,-1000,848,-1000,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-379,918,-354,-177,-1000,492,-57,-1000,-490,-697,224,820,1000,171,910,-1000,567,-1000,1000,665,-516,578,-73,-11,-1000,-1000,149,495,-591,670,-214,813,31,603,1000,232,-592,406,-623,812,360,375,-893,807,-1000,-220,1000,-141,-317,104,-371,374,1000,863,-122,973,84,-196,1000,943,111,1000,-530,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-636,462,-532,-779,-19,-207,769,426,1000,879,-698,-255,1000,-248,263,-920,345,-232,-821,3,-138,393,282,-996,86,706,752,-156,-532,-621,878,53,-983,162,1000,802,1000,347,525,914,-402,1000,-296,244,-353,-369,-1000,516,-736,310,802,435,1000,-1000,-214,-484,187,283,694,541,-623,-91,633,422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-1000,-33,-54,-165,-281,-447,210,-404,-985,-517,-408,-153,-1000,-649,-37,483,173,950,1000,935,-1000,-746,977,607,488,-288,-172,-431,49,814,517,306,115,-848,813,-692,-182,753,566,716,952,-692,-905,574,-770,252,1000,-49,-1000,-457,956,962,590,286,431,556,-608,-153,-875,-835,-197,-195,-454,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{223,-735,471,-24,264,353,-50,334,397,428,274,579,594,-1000,-498,237,705,-110,-80,188,-174,-50,364,368,-18,692,671,137,-133,-575,607,-475,-313,283,38,-112,-62,-576,543,222,496,827,142,-817,215,356,-213,260,0,306,-700,548,176,-251,797,85,-23,151,-115,462,379,96,-208,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-298,-633,1000,-1000,606,-976,418,-400,-570,-944,690,659,-597,1,806,-692,-39,-489,-341,1000,-67,306,1000,656,-578,938,-1000,-577,-433,-220,52,-936,-864,-1000,240,12,-400,1000,-1000,-1000,-521,193,-606,673,-455,-1000,775,-781,469,135,-270,-173,173,726,-98,-1000,1000,854,953,-1000,754,939,918,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{966,147,-299,-847,-791,-363,-106,833,582,-853,567,29,270,-874,962,-380,200,-736,-215,-850,855,36,116,-8,-559,797,764,-259,-36,-90,650,658,-488,340,-891,-294,-655,149,-160,-447,-710,-555,-365,744,-644,-571,996,-806,-497,-565,286,120,-936,94,136,-411,-575,-888,519,-19,-713,504,-904,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{523,324,-556,-882,724,-892,398,-333,79,428,515,394,-904,757,-507,-602,420,253,406,-818,932,-35,834,371,-519,329,65,876,877,307,391,-377,-533,588,-775,-628,786,-511,999,610,-380,397,-380,7,-902,914,-341,511,404,207,-651,-198,105,512,-987,-474,187,-789,575,-499,434,-551,-556,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(java.lang.String):org.jsoup.nodes.Element",
            new int[]{114,81,-1000,777,1000,985,-233,248,968,-246,-1000,176,1000,355,-1000,1000,-80,-739,-138,-398,78,-210,1000,-766,-1000,330,112,-336,-752,0,1000,526,773,1000,-83,-654,-272,-1000,-571,320,554,288,1000,703,-973,587,-228,-4,1000,457,-471,-658,-40,-450,-1000,980,-318,1000,-1000,80,128,-539,-96,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{194,-798,-152,-22,-172,-411,184,-471,434,966,-438,628,-190,112,43,750,-742,124,877,406,762,19,912,175,137,863,616,315,-640,-720,-311,-11,737,62,-296,-732,-496,-497,194,-700,3,116,-812,271,-331,-623,-699,-849,721,300,206,-426,-317,-198,-467,684,-391,54,-921,290,-716,311,-419,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "child(int):org.jsoup.nodes.Element",
            new int[]{-18,-417,1000,896,496,-276,-738,583,-1000,267,-1000,22,-890,426,-597,-91,1000,-485,-126,39,-767,8,672,-1000,31,520,-555,865,798,-231,467,132,-302,461,116,1000,-386,-735,207,16,1000,-68,-983,492,235,530,-979,385,92,827,-270,743,-91,266,473,-413,806,-493,277,-578,547,362,-364,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "children():org.jsoup.select.Elements",
            new int[]{-613,129,-76,217,1000,854,-952,-1000,1000,-693,-1000,829,632,-426,475,318,-84,1000,-1000,-1000,-451,-837,680,1000,-1000,-315,799,-726,-403,-692,-172,602,-743,1000,758,184,-718,122,-7,-443,764,964,509,1000,1000,-1000,-962,-749,426,-357,-1000,-1000,-1000,-1000,442,66,-1000,-203,1000,327,561,-78,1000,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "className():java.lang.String",
            new int[]{1000,105,968,-1000,-1000,1000,94,425,-878,-449,25,1000,561,-1000,-260,948,-871,993,166,147,-389,-309,1000,-186,373,-466,-25,-1000,20,-571,-183,-1000,-1000,433,384,-126,-972,-1000,417,-514,124,860,-451,1000,1000,-896,-667,-1000,631,402,-427,76,306,248,154,1000,-907,4,1000,-27,-198,147,-30,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashSet", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames():java.util.Set",
            new int[]{130,420,-973,-260,284,-37,-605,-885,-441,224,472,-897,415,756,-655,-298,-670,420,507,877,806,268,192,-806,128,622,-334,302,163,428,-687,996,-655,666,-977,-644,-21,-117,-872,-254,-14,773,-79,-649,-682,117,674,370,391,389,975,983,108,-157,205,751,-703,-980,-996,296,-275,-763,-275,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{-260,-45,436,742,-1000,113,622,-1000,-135,-954,-576,483,847,1000,1000,1000,-1000,-456,1000,-267,391,1000,-161,-1000,843,-850,1000,-1000,1000,996,-1000,1000,957,1000,371,842,64,1000,-898,488,62,-1000,1000,-265,725,-1000,444,1000,668,458,-957,-141,-1000,328,469,1000,-369,219,-547,716,604,-829,-87,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "clone():org.jsoup.nodes.Element",
            new int[]{-1000,-198,548,-1000,-385,389,98,1000,-725,-297,-168,505,314,1000,-1000,1000,-272,-1000,-189,1000,108,1000,1000,-246,1000,-375,374,-1000,201,-115,-869,771,1000,-853,-484,398,424,-891,-1000,1000,-310,586,-504,-590,-691,1000,861,-652,1000,499,-120,717,993,100,-674,29,60,54,1000,-534,159,706,-805,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4MWU0LnBNLS5DSjdMV0Jp", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{-166,-420,-425,484,686,584,940,863,416,-546,386,-330,971,-363,211,-672,684,-916,757,626,108,-266,413,-356,132,983,-564,-232,-412,-610,-158,-801,383,-470,269,91,-790,245,471,760,-135,356,899,-682,-706,310,653,-521,-364,-776,77,-680,-269,301,182,-664,839,-293,191,-754,617,631,578,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:ODQwLjA=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{-599,66,805,-840,-1000,1000,-702,-1000,-496,61,-783,-996,1000,-891,354,-101,-1000,850,-1000,759,-1000,335,749,90,-1000,107,242,-573,252,470,-585,-22,170,-1000,582,242,-531,-521,-1000,619,122,41,1000,-1000,1000,1000,1000,1000,518,-228,-188,-3,465,-1000,551,-811,-103,-16,547,1000,-452,83,692,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "data():java.lang.String",
            new int[]{899,-393,1000,-1000,-1000,-191,-36,-6,71,1000,-807,4,188,-1000,149,-802,-72,213,-1000,-1000,-1000,165,829,-230,-1000,-1000,-789,-1000,-236,29,-747,315,-476,-1000,-1000,301,31,1000,-1000,494,-1000,-41,-998,-1000,1000,-1000,444,-44,280,755,653,976,460,1000,872,-1000,-1000,844,-786,-844,-810,1000,362,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataNodes():java.util.List",
            new int[]{-353,-60,821,268,-1000,273,984,475,349,629,203,205,-871,523,-633,103,329,-223,61,506,1000,-442,-151,-592,-480,-422,317,889,225,118,23,-761,1000,453,150,-542,-783,305,616,405,-23,93,756,-66,497,180,1000,-560,-22,-296,444,-53,425,372,-1000,1000,-593,790,136,-895,-557,-21,1000,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$Dataset", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataset():java.util.Map",
            new int[]{-71,-756,200,726,368,955,-678,468,204,996,409,-680,995,-101,-974,-693,955,-33,-872,-401,-943,993,-911,897,31,-32,-287,-495,-502,207,319,173,-187,915,-102,-831,174,-163,-190,450,-981,-634,510,291,819,227,-586,-708,-999,691,140,-728,-87,-811,-884,259,-506,109,-196,-504,662,611,-362,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "elementSiblingIndex():java.lang.Integer",
            new int[]{574,-438,-396,367,-368,78,283,198,-110,308,926,942,925,-890,401,913,76,653,-289,-124,-691,935,-841,384,412,-694,-528,-458,805,-145,671,-141,-712,729,-170,485,-817,62,229,-651,-525,-488,-96,504,811,166,-943,-672,513,985,-566,-852,-888,877,363,302,346,9,-111,710,-959,12,-807,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "empty():org.jsoup.nodes.Element",
            new int[]{602,-369,-166,-846,104,-823,1000,485,-1000,-1000,1000,-37,113,1000,-604,139,-899,-786,-45,-1000,-807,-231,537,-584,-692,1000,546,-694,1000,1000,858,-647,-872,361,-653,457,-16,-1000,-961,674,999,-453,-1000,-488,-245,436,-354,-1000,1000,-27,-793,644,1000,141,1000,-807,-225,771,696,186,-643,-1000,-424,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "firstElementSibling():org.jsoup.nodes.Element",
            new int[]{1000,438,-454,637,-757,-496,116,1000,-65,-170,411,105,126,-1000,1000,162,526,659,807,924,592,243,-391,-112,-868,-428,1000,-469,1000,-1000,704,516,-276,116,305,-1000,488,-814,-527,-901,525,-746,-1000,-328,1000,1000,198,-368,215,725,-275,-279,-24,289,765,-673,-572,505,433,-15,347,96,147,132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getAllElements():org.jsoup.select.Elements",
            new int[]{-797,927,-618,45,106,-169,757,-717,-378,89,-780,279,965,5,-380,-950,124,580,-690,-531,-585,571,947,339,722,-84,365,-642,-488,-129,-340,430,263,-962,-683,8,287,547,731,-312,110,-881,-227,79,536,-545,-774,-828,-682,-513,818,845,-30,-660,370,-49,-343,-396,149,-493,333,453,869,913}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementById(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-350,-198,1000,-150,-619,202,-426,1000,-515,1000,-1000,194,702,570,381,-1000,-1000,1000,1000,-568,-549,914,-1000,-1000,660,780,592,-571,218,936,-815,723,1000,-1000,980,1000,404,174,72,-1000,1000,928,-569,-97,-663,-205,164,612,-1000,340,-1000,-725,-173,-800,262,169,258,-1000,1000,-286,1000,-133,-1000,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttribute(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-663,-1000,78,1000,-385,-536,-715,-182,469,226,608,1000,-204,216,-784,-598,229,-60,-61,-769,-119,853,-816,946,-984,-213,-51,-642,1000,-29,-1000,477,-655,323,851,-1000,-21,-60,543,-679,221,1000,32,-322,235,156,-341,-283,327,54,265,583,-1000,-305,169,374,-902,73,-274,-1000,-392,369,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeStarting(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,858,1000,1000,1000,-257,-242,1000,-1000,-1000,-1000,1000,-247,1000,789,514,1000,-1000,-1000,-578,-1000,-1000,-1000,-1000,223,-1000,-1000,1000,-107,-959,1000,1000,360,-1000,347,1000,-720,1000,1000,1000,1000,-317,1000,1000,1000,635,-41,1000,140,-1000,-841,1000,-818,46,-1000,-1000,853,-1000,-1000,1000,132,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValue(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-40,-294,-584,842,-50,388,204,53,-505,910,-639,-823,-506,-122,-284,-433,-727,-553,-974,-927,450,-102,912,689,901,-894,-432,250,-679,-69,-885,-239,344,-479,570,823,-573,-220,340,-203,-219,-132,-139,845,528,-535,546,787,-68,-215,-441,849,96,-195,114,774,-180,63,938,564,441,-523,-617,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-646,-168,-1000,-1000,-408,688,704,1000,68,1000,474,-859,267,1000,-24,-614,101,-577,-183,126,-1000,104,1000,473,-808,-1000,19,-1000,-507,272,662,-255,-337,-293,-757,-1000,-340,-1000,-39,-378,-893,367,-656,-661,366,-685,-783,1000,706,1000,127,-180,152,-437,-429,-332,777,229,-1000,334,638,-254,-340,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueEnding(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{259,-879,-9,774,276,-881,1000,-719,871,-406,569,-1000,-737,-724,-145,938,-39,1000,-374,33,731,228,-711,-44,95,224,169,832,-577,-1000,342,273,-1000,-192,454,50,-557,-307,996,-117,398,-262,-448,-874,301,-571,-618,-456,-688,628,-955,484,-1000,592,173,-1000,-695,712,84,-562,1000,1000,242,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-464,318,-1000,-439,-895,1000,-372,-1000,-878,400,276,463,-813,-696,738,-1000,-701,864,-421,-988,748,1000,97,-792,-264,-1000,-1000,266,21,1000,-140,-149,1000,259,126,-221,598,-208,-102,-1000,371,311,814,-363,1000,1000,241,116,-134,603,1000,-449,1000,1000,400,1000,-471,1000,494,-1000,-1000,679,-156,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{127,249,-1000,-147,-1000,1000,-191,-297,-657,1000,56,-404,-744,-988,958,-885,1000,684,194,-976,-1000,-148,-362,-1000,-715,23,135,-1000,-342,250,582,-329,379,-1000,423,-1000,-537,-1000,910,-1000,-571,349,837,-674,1000,1000,-584,-171,-100,-827,-33,-234,1000,-1000,-289,556,584,970,494,90,-866,1000,1000,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{724,27,836,-143,-980,-542,-522,-217,173,-846,-785,-56,113,-426,649,-720,468,667,327,-664,-262,668,-79,354,495,432,-808,-484,-361,-294,420,-459,-612,-676,-420,-359,-192,-94,762,761,-577,-677,-954,965,729,-84,-604,859,267,267,991,-986,339,-831,-141,-512,-595,-614,-324,-212,747,-246,-837,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueNot(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-400,48,-1000,-192,-1000,-1000,539,-1000,756,-525,-1000,894,-1000,-1000,-1000,599,-1000,-1000,-545,17,-1000,-792,1000,-280,616,1000,-1000,418,263,422,-598,-1000,-854,408,-1000,-513,-1000,804,-330,448,1000,526,-433,-758,6,759,1000,978,-9,-92,-1000,1000,-1000,924,635,665,584,-511,-1000,-1000,-1000,586,496,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueStarting(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-927,-103,-985,1000,-30,-321,-1000,1000,189,466,566,162,-356,1000,-464,577,263,-948,1000,59,541,92,87,408,252,968,694,-3,1000,-393,-69,-307,664,-709,148,-47,-185,-748,671,177,-630,180,-638,484,-522,144,90,-241,-196,-577,-428,-1000,-167,-1000,-1000,-471,350,1000,159,-1000,-671,378,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-878,-918,89,903,1000,-2,-616,-888,271,1000,898,-1000,1000,-262,122,-810,-167,-410,1000,-813,-1000,-1000,237,-618,-1000,-97,-1000,1000,540,659,1000,-1000,1000,1000,1000,1000,1000,-1000,1000,335,109,1000,-1000,720,-384,-283,-602,-1000,-1000,1000,-1000,-614,-1000,1000,-900,-1000,-1000,927,-401,-638,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-449,-357,230,-378,1000,-422,-356,-485,956,-239,370,587,1000,-741,385,385,-256,18,528,28,145,-709,-388,25,-58,-817,-1000,303,-860,-519,579,-1000,816,-64,-131,1000,729,574,-1000,-488,-291,1000,-698,211,-100,-10,-743,-1000,-400,-669,-1000,205,-1000,56,-482,-64,-968,-445,-422,-803,705,-178,-885,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-402,-804,-905,664,1000,-101,221,-753,576,267,866,-324,1000,-262,1000,-608,-565,-357,969,-95,-1000,-328,608,694,-479,-184,-1000,669,-188,30,821,-1000,238,286,1000,1000,1000,-774,298,-37,-1000,702,-1000,223,-186,150,-725,-749,-804,782,-1000,-119,-1000,1000,194,-400,-1000,152,-53,-547,1000,-697,-1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-783,504,-580,272,553,-20,248,-357,1000,-1000,128,-217,600,-235,122,-635,-619,471,709,236,-79,121,294,1000,453,-779,-701,354,-125,-406,16,-1000,-1000,265,794,1000,1000,626,-402,335,109,-698,-591,-275,23,-283,-1000,284,-1000,-379,-1000,377,-1000,53,-613,1000,-310,45,-902,-7,725,-131,-919,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{427,642,761,-161,-998,-854,323,-16,-487,-262,486,-825,-25,-5,-480,-36,-664,-867,-295,879,-758,-163,221,-169,-580,446,539,293,689,-134,-980,-509,-941,-520,292,-659,-776,14,243,-737,-161,827,936,-852,432,412,-366,727,524,44,257,405,-398,-555,37,196,-48,92,960,-158,-958,392,613,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,426,-1000,-217,-1000,-1000,513,24,-762,-17,-28,-144,-212,14,-602,334,16,-817,-379,1000,-1000,627,-142,-649,159,525,-20,215,695,295,-1000,-462,-558,510,318,92,-90,-1000,213,-71,-21,1000,1000,-1000,1000,1000,22,1000,1000,-76,429,-166,-978,-1000,246,-256,1000,65,1000,293,-1000,1000,867,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexEquals(int):org.jsoup.select.Elements",
            new int[]{-1000,927,-1000,-592,-516,-1000,688,1000,-1000,-1000,-101,-148,-612,-34,-417,271,-1000,-1000,-259,-418,1000,411,772,1000,-1000,666,950,-1000,-238,-897,1000,627,-122,-793,1000,-48,-70,-1000,-272,-136,-753,1000,825,314,230,90,-1000,-457,-443,-1000,1000,-421,-1000,199,87,380,234,323,313,866,1000,-281,1000,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexGreaterThan(int):org.jsoup.select.Elements",
            new int[]{-175,-945,-23,-571,1000,-16,-226,613,-397,-722,-890,-289,457,-473,-118,523,380,-1000,250,372,-878,-753,-545,119,506,996,-901,447,135,88,-456,125,-600,1000,47,988,-392,1000,96,-453,1000,-476,-1000,-904,-729,1000,-398,334,-84,792,968,-825,-222,-645,-710,584,-416,683,327,275,-595,943,752,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexLessThan(int):org.jsoup.select.Elements",
            new int[]{-1000,-183,516,-706,-1000,-772,480,1000,-305,-23,-330,833,-135,-709,-757,-1000,900,-209,189,-273,-170,1000,-355,-1000,-1000,-869,-1000,573,1000,1000,-1000,-1000,746,-526,-1000,-82,-531,1000,-275,1000,-1000,68,-978,-172,-749,1000,707,1000,599,1000,1000,823,1000,532,-475,669,-1000,-1000,-82,592,277,-152,-979,649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByTag(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,825,580,-108,-1000,367,253,63,87,205,-455,411,595,692,-1000,-812,-923,98,603,-926,1000,1000,-832,159,1000,1000,-814,-824,1000,-1000,439,-1000,-1000,1000,1000,-1000,-674,-103,-209,-577,-633,-89,-6,-1000,111,-260,-8,798,-8,248,334,-1000,-270,1000,928,144,425,-1000,-300,-2,-348,-1000,801,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-236,-444,535,596,-56,-441,1000,856,-26,-718,153,-1000,-524,99,-725,-437,661,85,63,-1000,-826,1000,-418,-273,-718,-1000,24,822,-684,816,-391,1000,-1000,-43,-513,-1000,1000,346,-755,1000,218,-146,-293,312,-478,192,258,131,708,-816,121,788,758,252,-85,-1000,-436,-1000,-1000,-1000,618,-454,420,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-822,-374,826,862,-256,125,780,473,187,334,-27,-746,-447,430,885,-54,-592,-1000,-23,73,-548,891,508,-589,1000,854,377,412,593,-986,-970,-481,73,968,1000,-202,-728,-1000,333,501,-714,-382,-42,344,-1000,769,129,-630,-646,-1000,956,723,-11,-978,278,134,260,19,8,-1000,-772,-77,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-777,-1000,391,-1000,-233,-547,-792,188,-1000,928,589,1000,-85,-1000,852,-464,-817,-483,518,1000,-1000,584,-1000,1000,-1000,1000,394,734,-1000,403,438,205,-62,-238,1000,188,-608,13,1000,873,-1000,698,-70,38,-15,-1000,-851,-1000,-446,-132,-489,782,-973,500,656,89,-1000,-150,-65,865,1000,1000,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-683,405,-409,-303,429,669,182,418,188,-165,543,652,-420,748,-113,757,941,638,-970,726,308,-485,-19,-850,-500,14,-785,-900,534,760,-222,220,470,770,-102,-3,817,855,-176,986,213,-419,242,551,255,-85,823,-392,91,389,191,805,-359,-658,915,575,186,574,-969,-400,454,-338,997,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{-439,-567,268,-176,-222,-312,424,-152,357,743,832,-63,1000,-13,90,-767,122,1000,722,252,320,109,1000,-719,-756,-71,-191,-839,1000,759,-444,-469,-197,193,-30,204,468,1000,-759,249,-782,-552,787,-1000,675,75,-499,97,-815,-586,979,-512,-1000,49,-629,758,380,-260,534,744,-1000,430,-101,-573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{111,-441,712,1000,-1000,-1000,-761,-902,-100,886,235,-1000,-93,324,266,423,-935,443,-987,1000,-505,100,650,-1000,147,-1000,125,-580,-1000,-848,-1000,685,769,-1000,475,296,-50,-1000,-553,216,-983,80,-771,-165,1000,-1000,406,476,107,-199,-1000,367,-955,1000,-561,-637,-61,-1000,1000,-268,-1000,1000,1000,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-51,1000,-494,-1000,844,-581,-62,323,-316,-1000,269,-372,-584,-864,-997,1000,-84,892,-1000,-758,412,-709,-383,-264,1000,-314,1000,-702,39,1000,-145,-76,717,1000,-578,573,-77,-234,1000,1000,-628,1000,569,-822,896,-1000,511,-1000,292,-131,246,-1000,428,-201,468,-421,1000,-754,-756,1000,965,771,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{859,-528,-243,-1000,643,621,74,326,-85,-1000,-762,-1000,114,428,-1000,155,-98,-1000,1000,1000,1000,1000,937,609,259,-1000,316,763,783,-1000,-307,1000,831,-1000,988,510,-737,1000,78,234,1000,251,-189,-1000,-684,118,1000,-203,-1000,-383,1000,1000,1000,-750,967,-1000,601,1000,331,-1000,435,1000,1000,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{359,405,-809,881,1000,34,808,337,1000,-776,-484,1000,-374,426,-393,-495,-359,-859,1000,1000,443,647,303,470,553,1000,-766,1000,-116,-638,1000,735,1000,382,400,-1000,-371,776,1000,48,1000,-487,703,846,673,-74,-206,-58,282,174,48,-332,1000,1000,-1000,387,-1000,-198,826,1000,-128,1000,358,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{471,48,-1000,-1000,751,1000,504,-295,1000,-898,-234,1000,-1000,-724,-924,-1000,-738,-500,1000,1000,-1000,1000,-283,1000,-467,943,-428,1000,166,-1000,-779,1000,-394,863,-297,-1000,-382,54,-1000,971,1000,136,1000,940,1000,-620,-858,1000,1000,-239,37,1000,1000,-852,-1000,244,-191,899,295,1000,100,-871,901,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-1000,498,436,1000,900,-417,935,681,1000,-532,-1000,1000,33,390,-569,-1000,-358,-1000,77,470,-655,-368,1000,-246,-185,637,-965,1000,-193,722,64,1000,1000,569,-915,-1000,-619,759,386,494,-1000,-1000,542,865,899,427,-987,-1000,-1000,-1000,-162,-562,448,-56,-915,1000,-612,1000,1000,1000,864,979,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{557,-9,-1000,-1000,1000,1000,752,1000,1000,-1000,-1000,1000,-1000,-450,-939,-1000,-1000,-749,1000,1000,-1000,875,869,1000,-407,528,-1000,1000,-1000,-1000,-1000,1000,971,1000,-1000,-1000,-1000,-1,-388,691,894,-1000,478,1000,1000,-29,-1000,1000,988,-174,-1000,1000,-1000,-852,-1000,1000,88,1000,430,944,-450,1000,907,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-919,288,1000,816,250,-744,579,637,-57,559,-387,-82,1000,272,-173,-201,1000,-824,-799,-421,1000,-375,812,-1000,920,454,-786,88,-356,920,820,-204,305,-68,-1000,-1000,123,540,533,-1,-783,338,-232,-221,319,618,-461,-1000,-556,-837,-613,-619,-164,155,35,193,247,245,727,-489,1000,1000,222,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{16,924,-984,-335,-306,-470,-922,-177,510,-15,1,-624,-710,948,623,630,-351,647,833,570,-452,-948,-855,203,84,-274,632,130,320,-130,-435,-239,531,-440,-996,670,-885,-583,-114,530,583,20,749,778,-455,262,127,710,442,-441,846,-952,823,295,-987,-784,-507,258,285,491,-494,-83,197,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasText():boolean",
            new int[]{625,-321,233,499,663,569,273,388,-222,308,874,329,272,3,-461,610,-619,725,-272,-903,-490,355,878,199,922,1000,-743,-519,136,75,277,662,842,-835,1,1000,-401,-839,142,-371,265,-474,-1000,-1000,-614,-181,-580,-623,455,-39,-716,221,732,908,-813,262,-1000,-968,904,-32,821,179,433,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html():java.lang.String",
            new int[]{-127,21,202,811,460,39,-30,891,-146,837,-551,-908,-319,-294,870,71,388,-285,90,527,-551,427,224,949,692,505,500,-960,915,-584,415,842,-629,783,-884,-420,457,-941,67,-984,1,148,-444,-291,890,-648,327,-977,501,608,763,897,352,-654,-613,-507,-288,-482,224,-355,-448,-672,535,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.Appendable):java.lang.Appendable",
            new int[]{131,894,1000,284,6,-692,427,500,362,687,917,-677,-241,97,705,-522,-461,1000,757,-814,550,-36,437,-449,-95,-1000,295,-1000,-1000,782,1000,270,-836,-854,1000,-911,1000,1000,496,15,-1000,198,-1000,285,39,940,-305,534,654,-150,1000,-585,-1000,-1000,378,1000,-413,-339,264,-979,-867,-997,-829,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{293,-210,137,283,-597,-171,914,-490,-512,823,628,258,-626,810,-61,89,424,-748,281,-288,-962,-286,193,-230,-129,-351,76,901,874,-668,-55,-59,-517,530,-251,-318,-478,568,-126,-235,742,-861,546,-66,-700,-605,-963,165,-71,-341,806,975,-917,-550,-985,-395,905,-608,206,-302,532,-916,368,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "id():java.lang.String",
            new int[]{-837,-36,85,756,536,-341,259,650,383,807,937,740,-827,459,56,438,702,726,946,-126,524,1,566,811,-251,724,129,-252,-585,446,-522,541,759,549,681,727,-518,-325,152,697,-666,626,-157,-112,227,314,985,-754,483,206,260,339,-207,-399,306,727,985,115,18,438,819,736,-769,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-214,102,967,227,266,37,-262,577,-570,-1000,-1000,-1000,-584,-53,216,874,679,1000,92,-1000,-148,-1000,-748,-478,-1000,-64,-1000,1000,4,-936,-1000,-408,1000,-119,-1000,457,-362,-314,746,1000,-643,-123,-365,-9,-6,-601,-686,-540,-431,548,1000,841,1000,-698,94,-428,587,-298,-706,740,564,697,186,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-638,-60,-633,413,-363,274,138,1000,499,-728,-765,1000,154,-722,-461,-1000,1000,296,-1000,-821,537,-358,-546,-802,-365,1000,-285,711,67,-473,-921,197,-937,-175,-973,672,-623,-1000,-585,950,-1000,-692,-310,-376,499,-491,472,-434,1000,-1000,1000,-295,-868,-508,1000,-773,831,-755,-1000,-544,-623,832,-1000,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-1000,666,-1000,502,-797,-307,-787,1000,1000,-451,-1000,1000,-273,-58,971,-1000,1000,-527,236,-1000,-643,-1000,859,-1000,1000,1000,-1,701,1000,-1000,945,797,-1000,-1000,-1000,-692,-144,212,-462,1000,-1000,1000,-1000,-1000,339,-737,-872,-1000,1000,1000,1000,-222,-1000,-1000,1000,-1000,1000,-1000,-1000,-550,-836,1000,-1000,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-1000,189,-1000,-1000,-1000,1000,387,-1000,-680,93,139,1000,23,268,-1000,783,-1000,1000,1000,1000,-1000,832,-1000,1000,-281,1000,-1000,-1000,468,1000,656,-1000,1000,-1000,-1000,23,-132,1000,-900,-953,1000,-692,262,1000,1000,1000,-136,1000,-302,-1000,-364,-925,719,-729,792,-1000,1000,-375,-138,1000,1000,-232,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(org.jsoup.select.Evaluator):boolean",
            new int[]{-776,840,637,-679,590,312,-676,338,-18,-1,522,-105,196,335,-104,-437,38,-212,-626,-747,518,58,-667,697,-228,787,-545,760,-954,140,414,384,227,69,929,-207,416,817,809,-239,797,256,600,190,-331,52,169,-966,953,304,168,668,-346,-689,-334,-753,863,-567,480,-746,696,-380,-301,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "isBlock():boolean",
            new int[]{-80,-621,-796,-400,-1000,-206,-1000,451,-458,-1000,-137,757,255,-222,781,457,-1000,22,-853,1000,-1000,604,-1000,-594,-500,-808,-1000,386,-532,-494,-651,-400,396,-312,711,-442,-249,-31,1000,167,439,38,-1000,447,-766,1000,-326,250,198,-325,-1000,1000,160,-824,134,147,-855,-293,-1000,-33,150,581,4,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "lastElementSibling():org.jsoup.nodes.Element",
            new int[]{-1000,291,-1000,153,-793,-24,-466,715,-349,-459,1000,1000,-107,-1000,721,-963,-667,1000,-1000,264,124,-827,1000,549,381,885,1000,846,-921,569,-1000,371,-668,198,-541,735,-1000,453,817,-258,-699,-922,-1000,1000,762,1000,146,1000,1000,-1000,780,-637,240,1000,1000,450,1000,199,-1000,558,57,43,-441,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nextElementSibling():org.jsoup.nodes.Element",
            new int[]{-587,453,-871,-1000,1000,505,779,-457,-379,230,-233,-181,509,752,-6,-468,61,-1000,124,661,591,439,-59,-459,-830,-329,551,883,-1000,169,-1000,196,-713,274,508,-131,-938,-290,526,-461,-935,-755,-734,1000,-808,-161,-143,-669,949,13,-288,134,841,-507,-1000,-499,859,383,325,365,-724,-337,-1000,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nodeName():java.lang.String",
            new int[]{-640,-330,200,372,953,-269,849,-750,100,-877,-198,412,-104,115,-742,192,-660,237,-202,-588,-158,-584,-165,908,376,6,688,866,903,422,541,775,181,-376,720,-740,408,512,698,764,-710,-516,50,-863,66,829,144,542,-242,427,-192,-226,-459,-689,-577,-922,449,49,931,875,885,-398,-893,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "ownText():java.lang.String",
            new int[]{206,960,700,-26,906,-786,-672,-724,-527,303,-593,699,543,-419,-533,356,-931,943,-237,314,300,-174,-32,792,477,247,992,299,752,980,163,-680,619,-573,679,731,-792,-73,-784,-519,-779,328,-69,-673,988,478,659,-634,-858,949,85,-44,-239,-187,884,264,-530,-439,-609,93,-39,-637,-759,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parent():org.jsoup.nodes.Element",
            new int[]{-350,945,-9,611,533,-686,-766,778,-594,605,-992,-345,764,-989,-458,896,-513,-57,-615,-728,914,-169,-838,-841,-266,841,453,-903,-807,-416,-467,-889,-854,867,-856,106,-105,-747,-707,68,380,800,97,6,329,739,165,-345,272,11,649,757,905,642,187,463,289,707,-751,823,-451,320,76,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parents():org.jsoup.select.Elements",
            new int[]{839,213,-934,-75,-1000,-367,590,811,157,-1000,-2,1000,1000,-453,1000,733,-407,-1000,227,1000,332,258,-46,-114,-1000,-824,1000,1000,-419,-483,-198,-554,-397,599,1000,-587,112,-608,515,654,1000,56,-981,-1000,-514,-193,-32,156,-216,801,47,-1000,680,-776,-914,-434,-1000,-1000,576,-497,-904,1000,-315,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{314,-588,-406,123,216,-571,188,877,-103,395,560,1000,-363,231,-941,227,642,226,-320,-286,1000,117,418,-149,985,1000,-1000,164,529,484,-731,-66,131,-1000,1000,-1000,-895,-164,668,175,246,277,665,415,-185,-888,-856,1000,832,-1000,-703,328,1000,468,-498,91,1000,-989,28,74,-352,745,256,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{37,663,-803,-938,-229,503,56,478,566,964,852,-134,307,-6,-224,-687,-796,718,-554,110,270,-169,-478,-996,481,818,159,810,357,-411,869,821,-591,531,-520,575,130,-590,757,-172,33,105,-474,-166,-385,700,-909,416,63,-875,191,-243,21,-745,770,-734,-206,-906,449,496,19,-435,-280,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{639,717,-330,1000,-422,148,-357,523,-465,865,409,-914,943,505,339,-1000,-675,-863,1000,-230,1000,-843,-5,1000,-196,-509,-1000,-248,359,-619,-678,-1000,-2,-235,1000,83,-433,-795,645,-1000,-881,-68,810,-129,-5,182,1000,601,771,-75,-1000,50,877,1000,1000,1000,-187,1000,215,-620,-352,240,171,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-795,-693,1000,-544,-195,1000,-416,-1000,-420,-947,-670,-463,-535,855,-1000,354,-842,-675,-1000,26,-934,-1000,-484,-689,938,1000,-9,1000,-1000,-98,-1000,-489,-1000,-391,-507,-203,-348,624,938,-417,1000,-632,429,564,1000,-872,887,-53,509,1000,59,-1000,-491,164,-876,275,258,-1000,-586,-527,1000,-653,132,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "previousElementSibling():org.jsoup.nodes.Element",
            new int[]{839,-471,453,986,-316,-179,-213,-472,273,-482,946,150,-141,-520,676,708,-632,-613,-262,-56,-53,-276,267,237,-953,962,-111,507,971,340,180,-47,-836,421,807,730,-759,-855,770,-109,685,517,586,-43,-485,234,-53,-651,-204,-408,432,559,831,-182,163,-564,-921,-386,977,-767,-553,-857,620,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "removeClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{88,51,-66,4,538,-789,134,-178,-963,-914,-636,-248,450,603,75,522,343,-293,310,-697,639,-423,-139,-235,-813,751,98,-165,454,857,-755,47,88,-920,-607,-901,78,972,-388,-996,-128,767,-872,27,-52,970,-264,-477,62,671,-618,721,686,762,-867,-459,987,35,-351,-424,102,-990,724,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-289,510,1000,539,-630,-1000,503,-192,-78,1000,1000,1000,-579,-240,-344,-435,182,51,-46,660,37,-39,-1000,-209,703,1000,550,1000,789,21,-248,-744,900,-195,994,1000,-336,-879,855,503,315,233,485,-393,35,-690,665,-1000,-199,-860,525,-1000,-502,311,311,643,337,324,-231,437,227,-53,76,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "siblingElements():org.jsoup.select.Elements",
            new int[]{90,-54,220,-725,-729,793,204,558,850,734,-334,338,187,-810,-222,-812,-994,-865,-225,398,-262,785,180,870,461,806,-337,-861,618,570,-820,787,-785,-248,23,-968,264,284,58,804,837,547,538,230,-679,539,639,142,-799,-251,120,-72,-996,457,376,887,-78,265,542,59,-913,825,294,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.parser.Tag", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tag():org.jsoup.parser.Tag",
            new int[]{600,597,807,-365,-1000,-556,918,-1000,497,-6,91,130,476,-110,-638,-1000,38,-911,-23,-494,-329,-239,-293,445,778,-866,-994,1000,-1,418,-401,1000,-931,113,1000,202,-1000,6,643,-275,599,210,1000,980,97,170,141,-1000,-321,-9,1000,-1000,1000,191,693,-482,-695,-527,-258,591,-631,-1000,-284,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4M2U4", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName():java.lang.String",
            new int[]{-987,-585,391,-1000,-618,-921,154,445,-640,234,-306,-612,-699,-48,349,1000,516,1000,926,-1000,725,1000,-195,1000,40,932,353,-472,-1000,277,0,-124,-610,-698,-187,-218,-256,131,-681,796,108,-212,1000,233,701,331,-1000,544,546,433,591,421,-467,-1000,-95,-1000,1000,0,-109,-1000,1000,0,-572,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,-411,-1000,776,109,-246,157,98,145,-1000,-1000,270,-882,-354,-436,272,-637,503,1000,1000,195,-959,47,1000,-217,-331,189,-1000,-67,-296,-6,-796,201,1000,923,1000,1000,1000,-1000,15,-1000,-132,-589,-905,91,1000,1000,688,-79,582,-718,28,77,1000,-1000,-906,718,1000,833,1000,914,1000,-1000,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text():java.lang.String",
            new int[]{362,-627,188,-1000,-1000,-715,964,-105,-1000,-1000,1000,-1000,699,-1000,-751,1000,471,-239,383,1000,-1000,-1000,-820,929,1000,1000,837,1000,-619,-1000,51,-1000,125,-1000,-1000,-1000,-340,-1000,-1000,1000,604,-1000,-12,-1000,-1000,-1000,1000,-1000,1000,-86,-141,1000,1000,-1000,-1000,928,-124,1000,-708,-1000,-1000,1000,-437,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{61,540,-1000,653,-92,220,-136,-1000,-770,-1000,396,-976,-299,1000,-1000,-1000,-254,-1000,-11,264,76,1,415,-418,-1000,-1000,-254,-695,-1000,1000,-707,-1000,444,692,-953,-1000,-808,-351,88,-1000,83,-145,-533,-1000,-121,50,833,356,294,-837,1000,708,-1000,-890,6,-577,901,1000,607,792,519,137,815,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "textNodes():java.util.List",
            new int[]{139,-159,1000,1000,227,-485,894,-356,-31,-817,435,258,446,-423,-626,-888,853,885,-82,-610,988,1000,-780,304,-667,-1000,-215,351,-224,-1000,932,-1000,-918,541,178,-451,-528,505,89,-1000,400,738,-1000,835,74,13,322,403,520,792,366,617,1000,330,-717,813,1000,810,-1000,286,532,512,136,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:PC01NDYgY2xhc3M9Ii0tNjhGIj48Ly01NDY+", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toString():java.lang.String",
            new int[]{-603,-426,243,-546,154,-32,-37,337,579,477,23,496,-678,319,-850,41,-68,679,-473,-375,900,988,509,-67,840,95,-184,-335,135,-629,161,-691,-594,623,67,833,233,120,-480,-126,720,985,-26,-974,-336,-244,265,359,398,-997,-990,451,681,557,172,-98,398,-881,-324,-459,316,-200,-825,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toggleClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-402,174,-568,-644,870,415,808,-660,-173,-352,-753,713,475,-493,904,-579,-168,958,154,892,-12,217,-330,-990,302,-354,-405,-322,-824,-254,865,207,-760,370,609,-429,-394,-612,942,-557,296,-844,453,-173,986,-649,212,115,716,999,-783,416,-600,874,-134,-853,-391,-205,259,-975,400,863,904,845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toggleClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,90,1000,1000,414,947,329,416,420,-557,1000,27,1000,1000,-852,-1000,548,-1000,-1000,-1000,1000,1000,-1000,-1000,-1000,1000,-178,-304,-1000,-215,459,1000,1000,-1000,-1000,514,467,-565,647,1000,-234,-954,2,-380,769,950,716,706,-1000,-1000,-910,-1000,1000,-224,-342,-826,-1000,-630,-843,-1000,-481,177,-958,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val():java.lang.String",
            new int[]{-1000,-843,-697,-615,-1000,-756,-447,830,651,963,-27,400,367,109,421,-1000,1000,667,-93,645,472,-287,-1000,603,1000,-755,-1000,230,-440,-49,1000,-1000,-30,203,-514,733,-274,-519,-1000,-758,-686,696,1000,470,804,374,82,-443,-1000,985,1000,-271,-706,39,1000,308,1000,-720,36,31,-573,-168,1000,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val(java.lang.String):org.jsoup.nodes.Element",
            new int[]{312,954,-818,-770,-15,-469,-419,696,-1000,456,783,-76,-536,-972,-799,-71,-268,-1000,-374,327,402,95,-895,21,903,405,-731,-562,-518,232,468,-1000,-41,563,98,482,957,-132,273,1000,-977,768,-547,-164,-71,44,48,791,-1000,-18,435,-557,-900,517,506,1000,456,-724,-361,987,908,1000,-501,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-764,354,679,620,724,-66,941,578,-876,616,-848,-544,925,-634,184,694,564,-466,-229,-234,788,-299,44,-148,774,796,-301,-12,790,-951,-692,290,-436,-525,763,-640,639,-1000,184,-168,-964,103,-267,-843,-727,879,-237,190,457,-168,441,-64,-485,-648,-394,858,-84,846,967,-402,246,806,-38,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.String:Wg==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{141,-107,930,351,-791,116,-707,864,-357,-846,-719,-768,-601,409,414,844,-404,772,-750,274,345,-69,-528,-691,121,677,-800,-630,404,410,999,438,-511,-279,889,-770,-601,-43,343,-722,-361,26,-257,834,-827,-665,-976,-947,-547,730,-610,812,581,573,847,-342,-230,676,69,919,143,735,-368,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.String:LTcwOQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{84,-709,741,-473,-258,41,919,216,500,-535,302,943,-631,-617,-822,810,-555,-845,-503,-724,-146,627,105,308,962,817,199,302,-370,-873,697,272,66,-614,-897,-285,-673,-186,-879,774,911,136,-176,851,-978,489,462,-363,86,-479,163,711,866,-869,699,297,159,672,-718,550,-562,116,886,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:Qw==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{893,792,380,126,-672,925,21,61,844,-317,728,403,-190,-800,-754,-310,-903,176,-310,-91,542,637,71,-970,70,345,214,-31,362,-163,-434,-634,844,137,-181,-587,-810,355,240,919,568,-417,-879,-239,385,750,867,-68,-553,-543,906,504,-889,175,-192,999,280,-333,-205,-113,-104,-995,-832,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{-767,890,-660,167,-304,38,97,691,-278,-679,-505,579,-737,463,3,114,934,559,-746,825,31,406,22,42,-402,792,628,-978,803,-826,335,826,678,980,-287,-205,823,-189,158,-554,85,-720,-165,-38,-609,-122,-883,16,-56,-680,-695,648,824,160,-147,-883,620,92,-355,247,244,336,954,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-978,-719,191,-299,-694,711,-692,610,-414,690,-257,-961,570,-768,-119,752,-653,292,-487,799,120,-802,629,-100,-349,486,-451,-347,-213,808,275,203,-331,-180,871,154,331,555,-614,38,255,-307,-577,-585,153,-644,-254,-155,762,-450,-459,-623,758,268,885,937,-978,367,799,-913,451,819,833,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{139,-204,784,183,24,740,360,984,-976,-620,578,741,424,422,174,-184,852,78,-111,-8,357,-758,-536,-152,-561,45,-112,-319,-143,437,-792,335,-262,671,604,400,-984,-923,541,-318,560,369,832,-408,-522,-133,-765,-844,27,-525,770,708,341,40,409,28,-274,-189,-999,-331,-309,97,307,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{31,-205,-291,-618,969,63,-662,-205,720,757,-278,-716,-823,677,-131,-719,948,263,-10,228,322,149,-660,-910,600,647,-948,697,734,-355,-623,-603,962,274,332,446,100,351,-276,-572,561,-906,717,607,744,891,713,-429,467,-984,144,-209,-661,73,-7,777,599,732,324,-586,-182,-201,927,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "after(java.lang.String):org.jsoup.nodes.Node",
            new int[]{822,832,-435,1000,-1000,880,425,646,-527,366,691,-883,31,-1000,264,-565,224,672,1000,-206,1000,633,-60,-700,-501,-603,813,544,-971,22,172,-887,304,1000,-927,-163,93,-530,923,-135,188,1000,-540,-327,1000,830,-534,-938,-694,97,207,-22,-250,488,-404,-43,482,-528,908,892,832,-1000,-570,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "before(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-458,-7,590,-95,-453,702,563,-441,524,-35,-631,-147,948,-921,617,-262,624,-215,-214,906,283,634,-17,-700,805,-761,779,109,-45,663,670,-359,386,659,890,491,-434,471,-544,-99,306,-521,-355,-658,-417,-937,384,-11,-517,-191,-527,-10,-753,377,-24,-800,-331,192,-340,-590,344,-498,929,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{26,629,-467,-1000,426,-67,-233,484,-557,-420,-416,-109,-1000,552,-862,852,546,-364,-644,-1000,-249,709,-148,617,678,-1000,121,-13,-1000,946,1000,-365,348,-509,-1000,963,-230,865,60,1000,-826,-94,-336,-756,-894,1000,362,-1000,233,-900,1000,-921,321,-1000,1000,1000,-838,971,-523,-27,-1000,1000,807,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-515,506,629,-32,574,-848,-442,-705,-822,-545,851,198,610,157,-492,-626,871,-427,287,-263,118,-636,-193,763,-653,560,-25,541,979,906,11,354,628,112,-4,417,495,223,51,-672,549,-15,-1,-285,-62,839,684,593,-72,-52,1,-976,-945,539,118,-192,-827,-224,-613,146,-406,-672,-998,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{863,-492,353,367,827,659,463,-536,468,-622,-626,803,745,-749,666,-974,354,-440,-473,-123,429,960,-172,-35,-674,-331,-631,-396,-127,-126,-127,746,579,-927,-799,29,-447,584,-740,534,-917,-455,134,958,228,-601,-945,-395,111,-938,24,858,83,787,128,-411,446,-964,-111,367,986,-379,-659,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{-58,-934,-665,-145,587,334,906,206,729,332,-33,-291,213,-313,650,316,-960,-474,317,-639,-781,608,403,-504,-868,282,-893,651,-985,-23,218,504,280,-94,275,948,-363,162,-759,611,-814,533,-49,-378,58,960,656,470,-507,854,-784,115,-225,191,677,-752,-104,729,303,-944,-552,-468,-517,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{-757,840,-363,-449,93,-179,950,-569,707,-444,-163,274,292,-27,866,852,29,-446,-923,866,374,-35,712,745,-569,-516,570,-11,-981,18,-758,331,629,-626,-118,931,-876,-43,-444,-641,-279,-935,749,116,-431,575,89,145,-921,957,32,-89,37,278,871,-571,-511,-862,-809,900,418,-661,694,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{-775,723,-14,242,72,-270,869,-543,377,700,889,688,-561,316,123,832,-394,674,995,846,598,577,595,962,303,-527,211,614,332,737,-480,-232,-827,872,218,289,467,-304,320,-540,584,-387,-172,880,497,-360,-969,524,-271,58,284,16,-909,245,826,845,-153,-468,-102,638,-26,807,240,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{939,802,502,194,769,863,-803,-948,230,673,993,267,723,756,717,508,-428,997,558,24,781,-579,-537,-943,-626,-222,-486,260,25,-589,795,-736,-134,-75,-151,-724,-126,79,736,120,-978,-970,-103,-511,503,533,562,342,-220,-929,-709,-629,-55,967,364,-777,936,-457,-782,-190,-480,-920,-111,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{-60,444,950,270,59,-947,471,-1000,312,1000,-1000,-1000,113,211,851,573,-1000,-1000,-223,-557,1000,729,222,779,-400,-416,-575,433,518,466,84,-809,211,-357,-1000,860,-480,1000,-1000,1000,-322,-1000,-313,1000,-977,62,-1000,-891,-89,993,919,1000,-133,-260,433,25,-901,85,-1000,1000,-356,1000,76,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "after(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,134,-1000,387,-1000,-714,22,1000,218,-1000,335,-1000,-955,208,-1000,-134,-120,-626,1000,1000,671,-713,1000,677,-510,-984,-610,149,-1000,196,-269,-906,-160,548,-458,-1000,96,838,-1000,718,-612,-535,-160,759,391,344,-66,1000,-39,183,568,322,-1000,291,-1000,-310,1000,-1000,-1000,1000,1000,837,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{783,-191,410,-351,1000,-539,417,305,-135,137,124,643,685,-63,-652,-178,-442,-771,-110,-643,-155,-161,-13,231,-450,-285,-66,-1000,-910,-908,133,780,16,-576,824,-1000,64,357,655,-913,-242,1000,538,257,50,846,204,-812,1000,-898,889,-296,1000,-1000,496,-263,-881,1000,-1000,110,150,-233,-698,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{-188,-106,-388,447,-762,757,-1000,560,-15,-807,-1000,-12,-1000,-513,1000,-199,686,1000,-84,-110,91,-636,860,-1000,-353,245,-873,-956,991,-1000,1000,-941,28,660,219,-552,1000,1000,-1000,599,1000,-512,-260,162,620,-1000,-480,310,-668,-59,-969,561,-755,1000,221,168,697,-457,1000,-1000,999,1000,-145,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "before(java.lang.String):org.jsoup.select.Elements",
            new int[]{-379,-956,261,-240,-321,730,-719,243,247,973,723,333,-237,337,-115,-416,833,543,-809,630,-98,-982,-580,100,281,-953,-756,-7,33,-531,108,-483,-970,-618,5,19,259,-449,-666,-155,-582,872,677,-298,-260,-429,109,404,39,642,398,-214,-969,-555,366,-23,129,346,-426,257,131,829,-515,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html():java.lang.String",
            new int[]{-284,154,502,-211,935,1000,-95,258,333,-574,-648,-933,424,-1000,328,-343,-988,-393,-535,-1000,308,46,-313,90,94,1000,-23,-269,-582,-308,-184,929,637,1000,-689,-176,-1000,1000,-391,211,356,701,-618,-749,-49,-641,-232,515,-685,346,-798,486,683,98,-1000,-553,-995,-1000,9,568,-211,-355,-757,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html(java.lang.String):org.jsoup.select.Elements",
            new int[]{-522,-956,-113,-249,1000,-285,823,842,226,1000,413,1000,715,1000,-851,-659,146,873,-261,1000,955,-793,-283,-130,-925,-400,239,-610,244,-44,-439,869,-690,648,1000,-880,277,-737,764,1000,-1000,334,-405,-318,-887,-775,637,-380,1000,300,437,642,-863,814,1000,-462,1000,-356,33,-1000,340,-1000,461,497}));
    }
}
