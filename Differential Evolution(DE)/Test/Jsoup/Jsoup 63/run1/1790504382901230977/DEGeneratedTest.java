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
        org.junit.Assert.assertEquals("java.lang.String:LTYyMw==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-502,623,269,-35,708,46,-665,-71,-209,-131,926,-155,259,-257,-771,826,920,-4,126,-604,95,853,730,510,505,-120,-560,739,-961,146,-560,120,-996,747,-918,-255,589,-263,917,969,274,-728,-743,-729,-290,-184,136,-713,-825,652,-767,-720,159,-409,977,-579,-169,-212,-646,646,-796,-118,199,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-542,-217,617,-811,-564,263,304,53,-509,474,-471,-401,713,-359,-925,-147,467,-616,404,-153,-691,697,-440,-422,-747,582,476,-782,-994,-813,-14,483,-871,-526,-633,-410,234,-164,-476,62,331,-151,-888,800,-784,378,800,300,385,-347,648,-339,404,585,-830,-858,-148,-381,-10,-607,-951,-320,-760,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:NzM4", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{410,738,-576,-762,-931,-20,539,-841,1000,-1000,-249,-868,-1000,442,899,-1000,924,-329,-272,1000,-638,1000,535,645,-1000,-403,937,-1000,1000,265,-857,874,-232,343,-561,1000,-1000,-809,862,208,508,1000,-1000,427,-715,-499,322,36,-366,-921,-165,728,-14,1000,1000,-731,-47,-664,-173,-625,1000,1000,120,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-958,622,-1000,1000,-1000,-935,-272,816,542,182,589,-83,635,-95,797,-1000,-260,-577,834,875,204,660,-900,488,1000,-1000,332,-809,1000,867,-28,-1000,-1000,601,546,487,671,438,-320,882,-1000,713,-1000,1000,196,586,61,218,-731,-381,-141,-151,-375,330,957,290,-344,-421,9,23,1000,184,81,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:Nzk5", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-358,-799,796,-813,-723,681,635,-95,-536,159,-783,573,417,90,-260,-31,-338,-594,669,-440,581,-82,-767,693,283,390,765,-944,-605,-326,618,-17,800,-597,349,-936,327,-24,-575,556,-5,811,-250,-905,-482,-805,-972,355,472,-572,163,-557,-220,-559,-324,89,992,-443,490,953,-809,-336,-671,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-414,283,-235,253,-390,-811,144,848,740,491,267,-638,999,888,369,314,529,687,-546,-879,233,-537,923,-847,-136,716,888,-782,822,-191,-969,428,316,-402,-126,-657,588,379,861,-734,-579,-935,718,-381,326,787,-752,440,436,562,-71,-802,-551,-920,-551,392,-276,-782,-316,667,42,424,-483,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{-65,853,-176,-434,-857,-987,-375,-665,-232,17,-80,-749,28,-142,295,-1000,-51,-117,845,-112,-407,-485,-343,455,169,995,397,77,-454,754,-604,-157,577,-254,142,833,897,-590,707,-487,-377,-588,-62,1000,-1000,-119,1000,-1000,-648,-351,358,432,-483,848,218,314,652,-1000,-123,-1000,1000,1000,99,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{786,-1000,-840,1000,88,1000,1000,-748,449,-1000,614,825,602,-929,-12,-538,-1000,-487,590,778,400,-1000,289,105,-1000,294,-1000,-871,602,1000,-491,980,-763,477,1000,962,-536,-1000,550,-404,158,1000,886,1000,-37,-735,566,359,424,-1000,-158,97,-1000,-878,-772,-400,82,736,-425,-482,1000,168,-69,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{858,983,582,-39,985,-364,774,-461,411,156,-974,-907,-545,-726,-367,-170,228,859,474,322,-619,-821,-455,-602,-932,333,-735,129,46,-485,494,-307,349,596,-78,-890,-659,606,-13,374,107,-843,-219,329,-858,-436,590,813,-939,489,601,-966,-326,326,112,920,-821,-836,-864,-708,467,-752,-327,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{354,-514,43,913,464,-743,129,92,-845,-5,-16,-976,-440,-674,-265,-949,595,-31,145,351,2,-18,201,-448,295,-300,225,-191,-830,219,-856,309,-464,-913,265,-609,651,992,-610,983,-993,643,430,-94,-390,-173,846,-254,-934,627,136,-554,-484,206,-605,-447,843,-784,522,153,-159,-118,44,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{378,-382,-399,177,61,-167,-759,843,-263,-70,-631,-113,917,-88,698,-508,-921,486,181,518,-306,-505,-392,-408,669,-86,-408,37,-849,-704,755,828,250,-377,-405,112,-502,711,-203,307,-370,-426,552,307,-388,404,-195,897,-245,-771,337,131,-770,-284,45,120,-669,66,868,-541,-595,-487,-555,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{578,545,743,322,-935,-381,-305,-442,179,916,-839,-419,-8,453,-580,445,-14,-273,840,495,-964,-979,-625,59,574,446,101,-322,892,-119,-887,831,-805,-945,844,469,-754,211,52,171,205,10,-412,209,636,637,415,427,549,452,-719,-552,-181,-81,-36,-542,-647,-952,72,-432,-16,159,982,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-180,-534,534,-319,-665,-486,-1000,-134,608,-124,-1000,1000,481,-497,-589,303,172,744,-970,205,-998,302,-1000,588,52,-955,1000,-623,760,758,1000,-895,-1000,423,232,678,586,-759,1000,-738,-339,349,1000,-1000,1000,-1000,122,-6,-540,995,1000,-116,-177,568,818,174,322,-1000,1000,1000,1000,-1000,529,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{29,87,46,52,-42,-322,240,164,650,89,799,486,764,96,-485,491,-185,447,-37,870,207,407,584,95,-728,551,-1000,-470,299,80,549,657,522,-746,181,-667,-572,-597,-129,-195,27,-74,-290,305,563,122,-436,-486,169,-322,-117,-778,265,45,285,79,-279,542,-271,354,719,-198,-488,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,-399,-472,-1000,457,-119,440,-558,-331,1000,-196,-1000,-181,-820,1,-524,68,-271,1000,196,-973,-274,923,-418,153,-1000,389,169,-357,-108,52,473,405,-530,53,1000,-631,-619,432,191,303,-769,376,947,664,909,688,482,-958,1000,321,844,938,163,-900,-599,-1000,-1000,-477,319,713,152,-842,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-425,633,-903,59,-700,-874,-143,725,634,365,711,337,-283,-323,910,-165,-678,-424,194,-469,282,-121,-472,-216,316,-257,-997,980,677,-767,905,-27,435,445,-974,442,-606,913,-890,247,-326,-953,19,-216,778,704,680,-389,-363,-361,138,-864,-468,522,-817,-937,96,-469,-85,441,633,66,324,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-177,192,660,442,-258,489,986,-116,-820,695,199,1000,-463,-300,-856,-547,1000,-363,-357,-559,-93,386,1000,54,-360,157,330,-96,179,892,-255,-193,-272,340,-348,-624,-310,546,-1000,216,-983,352,-770,-149,-227,366,-260,1000,576,741,-1000,803,-594,351,-926,-481,-729,297,396,965,386,-737,210,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{94,-510,165,630,982,-881,795,-214,787,-475,198,998,138,949,192,-254,-487,-519,223,388,-961,-174,346,-32,176,623,-34,-861,-794,-360,703,-631,651,780,776,176,-976,-165,324,264,-381,-324,203,118,-524,-285,967,863,74,-702,-7,-973,-143,23,973,-809,-753,-121,-594,486,-532,590,100,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{21,-171,25,1000,-639,-189,1000,-286,1000,1000,-386,-733,989,1000,973,572,390,282,7,-1000,365,217,1000,1000,1000,1000,317,211,-301,515,635,-175,-24,170,227,-521,-723,672,-1000,-219,-360,-92,196,-152,-798,-463,-3,-507,657,518,-153,1000,-641,-166,324,-456,-932,144,558,78,-205,-631,505,-95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{565,643,233,594,-11,-486,-291,-179,819,-1000,-71,650,-594,1000,-433,463,470,170,-472,-1000,601,229,1000,-207,-682,104,-959,-1000,1000,-1000,-498,-1000,1000,-918,-108,-68,-6,-578,93,-74,-1000,-371,319,190,-261,-211,85,-800,-34,-261,1000,-216,-529,-599,93,81,-62,-46,592,590,-273,-506,-261,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-1000,168,178,878,541,-314,-325,1000,-348,-407,922,499,-612,-1000,-757,-950,-368,92,-1000,380,364,241,284,226,790,253,974,-79,-810,-818,518,-1000,109,26,-1000,-1000,21,158,825,-137,-576,-1000,1000,-379,-550,671,739,-263,-78,-1000,-1000,952,-69,1000,-342,1000,659,746,-597,1000,1000,1000,766,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{698,382,201,764,896,-91,703,-381,408,-267,361,-611,-987,66,705,-677,-190,-338,-406,258,-761,-68,426,-257,868,198,-479,-183,960,-498,31,-98,-476,-852,692,-410,-722,460,-281,905,745,113,276,-931,595,847,46,740,226,354,-347,-749,599,-279,971,-553,-619,917,647,397,-458,503,601,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{258,801,746,-312,1000,-91,-1000,-169,139,142,21,-187,1000,880,216,397,-308,-1000,0,-987,446,-188,892,-293,-1000,124,145,-73,1000,0,750,-1000,1000,296,1000,311,0,318,-1000,-1000,418,750,734,-153,37,1000,435,-64,-14,91,589,456,-1000,-327,-884,-1000,-1000,-1000,-1000,-113,955,639,1000,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{634,1000,-886,-301,-584,-734,-64,402,288,-1000,171,-28,-32,-924,1000,1000,1000,1000,397,1000,198,-703,-1000,-379,-431,960,-285,140,1000,1000,764,469,-126,653,-1000,531,71,-1000,-296,-1000,831,-514,-452,-1000,-1000,-951,-922,714,765,888,-1000,125,860,1000,1000,-707,482,-1000,822,1000,-1000,948,610,-934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{-414,497,370,-234,-436,304,621,238,328,216,799,1000,671,930,1000,-721,-144,1000,170,-518,-140,980,376,-787,-158,676,803,-159,290,657,1000,-9,381,-841,775,-128,-1000,-366,575,211,406,265,-869,-504,-861,280,-324,922,-1000,971,749,809,-1000,-958,-1000,253,969,-493,217,-386,-162,200,942,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{-382,-1000,1000,-112,1000,568,404,-1000,731,371,410,-670,651,328,-333,-210,-1000,702,-798,810,727,70,1000,690,-167,-1000,421,-327,-1000,145,-549,-730,712,799,-119,-1000,-548,-34,230,1000,1000,1000,-1000,-144,919,473,-156,140,1000,-161,124,-1000,173,277,882,1000,93,258,-706,-262,614,105,43,949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{314,-521,-199,-233,1000,1000,-69,1000,280,-889,648,1000,-928,1000,-1000,-1000,534,707,671,657,407,-114,-136,-326,-464,559,362,-201,-219,-334,284,1000,-294,-328,-990,-54,542,-826,1000,625,-58,1000,-1000,713,1000,832,1000,-121,389,-42,-137,154,172,59,919,-694,113,94,1000,803,243,-653,-332,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{530,297,277,-594,922,1000,66,-236,712,136,-410,228,-911,-245,-271,-1000,715,-209,1000,1000,955,1000,-324,1000,-193,-195,567,925,904,738,-983,721,-434,298,724,-487,1000,1000,970,152,1000,768,-945,470,815,1000,524,688,1000,303,806,-1000,-978,-1000,209,237,306,-550,624,177,100,187,1000,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{-718,-1000,-1000,-1000,1000,-264,105,-40,889,1000,-162,1000,-104,569,629,-1000,-1000,-447,1000,1000,-959,1000,-587,1000,-1000,-112,1000,719,-268,-393,-1000,1000,1000,-499,-617,-372,783,1000,1000,718,-357,-560,-819,1000,753,947,-410,-676,722,337,-536,-1000,-1000,-1000,-1000,853,-871,-118,658,77,-1000,890,551,-591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{534,-324,264,769,-950,-20,-850,-789,540,-210,360,239,560,154,739,-250,405,-646,-28,644,-322,266,-114,-17,-323,413,-340,522,67,-715,-222,-562,201,-671,45,875,-859,-713,412,77,944,-955,-557,-733,88,13,312,-783,79,199,-58,-515,181,608,-450,663,-977,-629,259,-315,-642,84,-340,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{-322,668,-1000,-543,-286,-1000,-615,1000,-746,-456,-815,651,-640,-1000,-636,-425,214,12,-292,486,-1000,896,-479,590,-1000,-949,-593,396,296,502,-99,941,580,1000,73,144,1000,-103,-1000,-842,-124,66,590,-1000,1000,107,696,831,869,568,88,-22,793,1000,-1000,-192,914,799,488,984,121,-838,246,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{-501,-731,-352,-354,373,573,324,-723,944,90,400,-400,-278,274,-645,-400,-502,-1000,-409,-410,5,-1000,1000,-132,-400,142,46,-513,-400,-383,-863,1000,612,-834,757,-644,511,426,-581,396,-1000,91,-1000,-201,-215,-447,-651,265,-510,-158,1000,-751,-274,-269,-1000,-521,-234,-379,-400,400,-310,83,-400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{-7,-731,-1000,654,-663,779,1000,279,152,90,-759,193,-506,-964,759,-545,1000,1000,-1000,1000,-217,854,1000,-831,340,1000,242,-1000,959,-1000,1000,130,351,1000,1000,250,1000,-246,-581,1000,730,1000,726,230,558,296,-570,734,-523,1000,-430,1000,-1000,-247,584,-14,114,1000,-1000,1000,-1000,-348,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html(java.lang.String):org.jsoup.select.Elements",
            new int[]{-182,-856,202,-825,542,-1000,238,21,-846,-691,-1000,1000,345,440,426,963,-645,505,1000,911,614,165,825,227,-92,-492,350,883,-400,-193,800,420,-353,410,1000,884,876,-20,577,-1000,-24,-1000,1000,1000,522,-888,-924,-1000,312,289,-273,113,400,453,621,1000,-1000,-337,-1000,-714,-542,569,181,-919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html(java.lang.String):org.jsoup.select.Elements",
            new int[]{-249,-226,-163,6,-1000,1000,1000,-690,-219,1000,1000,-589,-120,-330,1000,-143,-584,1000,-946,-1000,-1000,327,-564,657,216,-1000,-306,1000,266,-392,-750,416,-111,-286,-1000,-1000,400,843,238,69,400,20,1000,-685,440,-7,-69,-734,-774,-79,71,1000,-299,-1000,-64,-444,-222,-893,-614,-1000,625,-955,-190,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prepend(java.lang.String):org.jsoup.select.Elements",
            new int[]{21,524,-956,-223,-612,-935,-278,-949,-240,575,-263,-939,-229,-808,390,-1000,-1000,-302,-962,262,-942,788,775,-615,-331,-815,1000,-694,1000,162,-222,-160,250,-47,172,887,309,102,1000,37,88,-993,355,596,-693,190,258,-598,249,256,609,53,252,-870,-350,823,265,-18,416,-328,149,-127,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prepend(java.lang.String):org.jsoup.select.Elements",
            new int[]{752,449,-268,-33,-1000,-1000,-643,-382,-422,1000,-1000,-1000,-630,-1000,-126,-323,-1000,-813,954,-16,-738,1000,628,-1000,-1000,-209,319,-1000,344,184,-914,-1000,173,1000,990,906,-480,-1000,751,341,-161,-1000,1000,1000,-1000,-955,-242,-233,520,-201,1000,211,-32,-932,-1000,-1000,594,-792,447,1000,-1000,1000,670,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{-251,744,101,741,-599,-453,-73,735,-728,-780,-887,-823,658,638,925,208,-562,-666,-605,-533,-796,-813,346,-170,-355,-689,-300,-300,-203,648,-903,47,-684,-535,-48,-82,-59,-263,615,665,-12,-800,-513,757,888,785,-328,-286,520,78,-8,-564,-569,462,-907,15,890,712,-288,944,180,899,-785,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{217,744,-279,-819,-346,1000,963,-667,1000,1000,1000,367,-1000,-403,-666,166,617,-469,1000,-430,302,686,-696,733,82,1000,-893,277,452,-825,-583,-1000,-204,-605,-844,-169,-37,-605,-669,-46,1000,431,640,147,-255,-951,985,1000,-698,43,-83,-539,1000,-832,285,21,-1000,-1000,-145,-1000,-566,-416,-66,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:LTYyMw==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-502,623,269,-35,708,46,-665,-71,-209,-131,926,-155,259,-257,-771,826,920,-4,126,-604,95,853,730,510,505,-120,-560,739,-961,146,-560,120,-996,747,-918,-255,589,-263,917,969,274,-728,-743,-729,-290,-184,136,-713,-825,652,-767,-720,159,-409,977,-579,-169,-212,-646,646,-796,-118,199,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-542,-217,617,-811,-564,263,304,53,-509,474,-471,-401,713,-359,-925,-147,467,-616,404,-153,-691,697,-440,-422,-747,582,476,-782,-994,-813,-14,483,-871,-526,-633,-410,234,-164,-476,62,331,-151,-888,800,-784,378,800,300,385,-347,648,-339,404,585,-830,-858,-148,-381,-10,-607,-951,-320,-760,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:NzM4", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{410,738,-576,-762,-931,-20,539,-841,1000,-1000,-249,-868,-1000,442,899,-1000,924,-329,-272,1000,-638,1000,535,645,-1000,-403,937,-1000,1000,265,-857,874,-232,343,-561,1000,-1000,-809,862,208,508,1000,-1000,427,-715,-499,322,36,-366,-921,-165,728,-14,1000,1000,-731,-47,-664,-173,-625,1000,1000,120,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-958,622,-1000,1000,-1000,-935,-272,816,542,182,589,-83,635,-95,797,-1000,-260,-577,834,875,204,660,-900,488,1000,-1000,332,-809,1000,867,-28,-1000,-1000,601,546,487,671,438,-320,882,-1000,713,-1000,1000,196,586,61,218,-731,-381,-141,-151,-375,330,957,290,-344,-421,9,23,1000,184,81,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:Nzk5", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-358,-799,796,-813,-723,681,635,-95,-536,159,-783,573,417,90,-260,-31,-338,-594,669,-440,581,-82,-767,693,283,390,765,-944,-605,-326,618,-17,800,-597,349,-936,327,-24,-575,556,-5,811,-250,-905,-482,-805,-972,355,472,-572,163,-557,-220,-559,-324,89,992,-443,490,953,-809,-336,-671,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-414,283,-235,253,-390,-811,144,848,740,491,267,-638,999,888,369,314,529,687,-546,-879,233,-537,923,-847,-136,716,888,-782,822,-191,-969,428,316,-402,-126,-657,588,379,861,-734,-579,-935,718,-381,326,787,-752,440,436,562,-71,-802,-551,-920,-551,392,-276,-782,-316,667,42,424,-483,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{-65,853,-176,-434,-857,-987,-375,-665,-232,17,-80,-749,28,-142,295,-1000,-51,-117,845,-112,-407,-485,-343,455,169,995,397,77,-454,754,-604,-157,577,-254,142,833,897,-590,707,-487,-377,-588,-62,1000,-1000,-119,1000,-1000,-648,-351,358,432,-483,848,218,314,652,-1000,-123,-1000,1000,1000,99,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{786,-1000,-840,1000,88,1000,1000,-748,449,-1000,614,825,602,-929,-12,-538,-1000,-487,590,778,400,-1000,289,105,-1000,294,-1000,-871,602,1000,-491,980,-763,477,1000,962,-536,-1000,550,-404,158,1000,886,1000,-37,-735,566,359,424,-1000,-158,97,-1000,-878,-772,-400,82,736,-425,-482,1000,168,-69,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String):org.jsoup.nodes.Document",
            new int[]{330,1000,-725,307,1000,349,100,176,584,-192,1000,-1000,42,-751,-50,1000,46,488,-1000,-983,-436,435,-23,-244,-1000,1000,-571,-1000,-1000,-568,56,1000,-527,588,293,-538,-444,1000,-1000,-169,-545,428,-992,1000,615,1000,-529,-572,-355,527,-37,-76,1000,-412,246,113,1000,199,-1000,-60,-446,-411,-649,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-318,-777,-525,73,208,570,243,-243,-917,765,-967,518,110,292,175,818,111,841,-177,-96,-261,-315,-534,-754,15,354,-571,-341,273,676,9,485,-950,904,877,204,116,-452,209,-804,-661,807,-346,-984,327,-978,-319,524,-45,471,98,-529,740,976,640,733,-201,-689,382,-709,-360,-791,423,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-38,-778,-216,-773,722,906,-691,-106,73,-707,-36,-524,87,-221,-246,-219,-756,-769,-141,-354,35,-448,862,113,-54,813,276,3,669,756,216,-233,526,357,784,141,891,103,21,-104,684,644,-884,-261,-121,573,-10,-685,-557,-778,-686,-836,-912,-432,581,-520,-205,-391,-64,558,-782,-87,124,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{722,405,138,523,-902,956,-62,-260,-200,-189,-48,61,-83,-488,-17,346,54,-425,-405,-114,767,931,141,1000,-705,863,-895,-258,-67,-968,678,866,599,-931,143,867,875,159,-39,-343,-850,-932,280,-770,344,-865,13,530,-871,919,579,-326,595,-652,958,-362,842,537,-205,-399,114,-256,-64,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{858,983,582,-39,985,-364,774,-461,411,156,-974,-907,-545,-726,-367,-170,228,859,474,322,-619,-821,-455,-602,-932,333,-735,129,46,-485,494,-307,349,596,-78,-890,-659,606,-13,374,107,-843,-219,329,-858,-436,590,813,-939,489,601,-966,-326,326,112,920,-821,-836,-864,-708,467,-752,-327,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{354,-514,43,913,464,-743,129,92,-845,-5,-16,-976,-440,-674,-265,-949,595,-31,145,351,2,-18,201,-448,295,-300,225,-191,-830,219,-856,309,-464,-913,265,-609,651,992,-610,983,-993,643,430,-94,-390,-173,846,-254,-934,627,136,-554,-484,206,-605,-447,843,-784,522,153,-159,-118,44,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{378,-382,-399,177,61,-167,-759,843,-263,-70,-631,-113,917,-88,698,-508,-921,486,181,518,-306,-505,-392,-408,669,-86,-408,37,-849,-704,755,828,250,-377,-405,112,-502,711,-203,307,-370,-426,552,307,-388,404,-195,897,-245,-771,337,131,-770,-284,45,120,-669,66,868,-541,-595,-487,-555,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{578,545,743,322,-935,-381,-305,-442,179,916,-839,-419,-8,453,-580,445,-14,-273,840,495,-964,-979,-625,59,574,446,101,-322,892,-119,-887,831,-805,-945,844,469,-754,211,52,171,205,10,-412,209,636,637,415,427,549,452,-719,-552,-181,-81,-36,-542,-647,-952,72,-432,-16,159,982,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Attribute", "", "createFromEncoded(java.lang.String,java.lang.String):org.jsoup.nodes.Attribute",
            new int[]{-288,-933,-152,-503,211,-898,-981,261,-894,-520,-262,998,968,71,-281,-175,498,927,-817,497,837,-257,413,28,532,-118,-492,544,196,-497,202,339,44,817,-50,-26,461,178,624,396,-426,827,73,-489,365,447,620,-564,-688,-637,78,728,766,249,305,290,290,472,-521,388,80,-156,-703,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DataNode", DEReplay.run(
            "org.jsoup.nodes.DataNode", "", "createFromEncoded(java.lang.String,java.lang.String):org.jsoup.nodes.DataNode",
            new int[]{-476,-961,-740,-148,-122,608,-902,-8,-813,-571,912,-124,671,610,46,378,-408,-714,205,803,-450,814,-842,-849,-249,623,429,-772,-512,-415,267,-662,461,424,302,986,-94,898,480,-706,-7,289,97,-388,-554,-943,-942,-789,-56,-559,825,881,469,-742,993,737,-484,166,854,-371,-199,-705,-94,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-180,-534,534,-319,-665,-486,-1000,-134,608,-124,-1000,1000,481,-497,-589,303,172,744,-970,205,-998,302,-1000,588,52,-955,1000,-623,760,758,1000,-895,-1000,423,232,678,586,-759,1000,-738,-339,349,1000,-1000,1000,-1000,122,-6,-540,995,1000,-116,-177,568,818,174,322,-1000,1000,1000,1000,-1000,529,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{29,87,46,52,-42,-322,240,164,650,89,799,486,764,96,-485,491,-185,447,-37,870,207,407,584,95,-728,551,-1000,-470,299,80,549,657,522,-746,181,-667,-572,-597,-129,-195,27,-74,-290,305,563,122,-436,-486,169,-322,-117,-778,265,45,285,79,-279,542,-271,354,719,-198,-488,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,-399,-472,-1000,457,-119,440,-558,-331,1000,-196,-1000,-181,-820,1,-524,68,-271,1000,196,-973,-274,923,-418,153,-1000,389,169,-357,-108,52,473,405,-530,53,1000,-631,-619,432,191,303,-769,376,947,664,909,688,482,-958,1000,321,844,938,163,-900,-599,-1000,-1000,-477,319,713,152,-842,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-425,633,-903,59,-700,-874,-143,725,634,365,711,337,-283,-323,910,-165,-678,-424,194,-469,282,-121,-472,-216,316,-257,-997,980,677,-767,905,-27,435,445,-974,442,-606,913,-890,247,-326,-953,19,-216,778,704,680,-389,-363,-361,138,-864,-468,522,-817,-937,96,-469,-85,441,633,66,324,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-177,192,660,442,-258,489,986,-116,-820,695,199,1000,-463,-300,-856,-547,1000,-363,-357,-559,-93,386,1000,54,-360,157,330,-96,179,892,-255,-193,-272,340,-348,-624,-310,546,-1000,216,-983,352,-770,-149,-227,366,-260,1000,576,741,-1000,803,-594,351,-926,-481,-729,297,396,965,386,-737,210,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{94,-510,165,630,982,-881,795,-214,787,-475,198,998,138,949,192,-254,-487,-519,223,388,-961,-174,346,-32,176,623,-34,-861,-794,-360,703,-631,651,780,776,176,-976,-165,324,264,-381,-324,203,118,-524,-285,967,863,74,-702,-7,-973,-143,23,973,-809,-753,-121,-594,486,-532,590,100,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{21,-171,25,1000,-639,-189,1000,-286,1000,1000,-386,-733,989,1000,973,572,390,282,7,-1000,365,217,1000,1000,1000,1000,317,211,-301,515,635,-175,-24,170,227,-521,-723,672,-1000,-219,-360,-92,196,-152,-798,-463,-3,-507,657,518,-153,1000,-641,-166,324,-456,-932,144,558,78,-205,-631,505,-95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{565,643,233,594,-11,-486,-291,-179,819,-1000,-71,650,-594,1000,-433,463,470,170,-472,-1000,601,229,1000,-207,-682,104,-959,-1000,1000,-1000,-498,-1000,1000,-918,-108,-68,-6,-578,93,-74,-1000,-371,319,190,-261,-211,85,-800,-34,-261,1000,-216,-529,-599,93,81,-62,-46,592,590,-273,-506,-261,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-1000,168,178,878,541,-314,-325,1000,-348,-407,922,499,-612,-1000,-757,-950,-368,92,-1000,380,364,241,284,226,790,253,974,-79,-810,-818,518,-1000,109,26,-1000,-1000,21,158,825,-137,-576,-1000,1000,-379,-550,671,739,-263,-78,-1000,-1000,952,-69,1000,-342,1000,659,746,-597,1000,1000,1000,766,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.TextNode", DEReplay.run(
            "org.jsoup.nodes.TextNode", "", "createFromEncoded(java.lang.String,java.lang.String):org.jsoup.nodes.TextNode",
            new int[]{457,-798,-292,733,172,900,301,-288,318,849,90,389,665,-279,-237,-951,529,-166,644,-200,781,-687,-570,-202,821,541,-949,74,221,30,-552,786,717,-298,-116,-329,338,959,-753,-153,-408,892,738,149,-169,539,326,-249,191,151,385,934,-164,139,-931,-440,610,540,109,71,-318,676,-994,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{250,209,-301,700,206,720,969,743,957,101,-963,600,-624,-280,209,163,-497,291,-271,489,-263,337,775,157,-869,389,285,-391,-831,-877,-922,-107,-335,292,-607,549,960,-253,925,435,-525,95,723,-738,-139,643,871,279,471,104,833,139,-310,-681,485,297,793,675,-560,-526,395,286,79,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-382,-665,-160,1000,-956,-544,907,-1000,-1000,-1000,27,583,716,-1000,23,-98,-242,1000,1000,-918,-582,-1000,-164,-995,-417,-73,-767,190,643,-1000,659,-418,1000,835,-593,-732,-679,-53,-344,-259,-472,-519,-1000,-1000,-827,42,319,-234,-473,-1000,478,-616,-760,1000,1000,-1000,-279,-344,1000,-902,-260,177,57,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{698,382,201,764,896,-91,703,-381,408,-267,361,-611,-987,66,705,-677,-190,-338,-406,258,-761,-68,426,-257,868,198,-479,-183,960,-498,31,-98,-476,-852,692,-410,-722,460,-281,905,745,113,276,-931,595,847,46,740,226,354,-347,-749,599,-279,971,-553,-619,917,647,397,-458,503,601,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{258,801,746,-312,1000,-91,-1000,-169,139,142,21,-187,1000,880,216,397,-308,-1000,0,-987,446,-188,892,-293,-1000,124,145,-73,1000,0,750,-1000,1000,296,1000,311,0,318,-1000,-1000,418,750,734,-153,37,1000,435,-64,-14,91,589,456,-1000,-327,-884,-1000,-1000,-1000,-1000,-113,955,639,1000,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragmentRelaxed(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-917,718,-852,-219,392,381,84,425,710,167,-866,-917,-18,-545,165,-839,928,-291,235,-820,-885,-352,566,-644,522,457,217,-165,292,-749,-717,-97,-567,193,423,-92,-350,752,833,-212,185,-602,-599,136,589,724,-290,-453,-339,461,796,-375,-534,123,131,477,289,-847,-832,782,-343,-663,967,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragmentRelaxed(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{466,1000,710,-1000,392,-805,119,400,1000,1000,735,-364,743,953,13,-4,767,410,1000,996,1,-173,-426,86,784,-655,-413,-1000,292,597,1000,840,-100,-489,-276,908,-829,-1000,-838,-675,-508,181,-16,534,-115,-351,-363,-848,-44,607,-955,-426,-589,147,27,96,-550,-260,-1000,-593,612,-861,516,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{634,1000,-886,-301,-584,-734,-64,402,288,-1000,171,-28,-32,-924,1000,1000,1000,1000,397,1000,198,-703,-1000,-379,-431,960,-285,140,1000,1000,764,469,-126,653,-1000,531,71,-1000,-296,-1000,831,-514,-452,-1000,-1000,-951,-922,714,765,888,-1000,125,860,1000,1000,-707,482,-1000,822,1000,-1000,948,610,-934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{-414,497,370,-234,-436,304,621,238,328,216,799,1000,671,930,1000,-721,-144,1000,170,-518,-140,980,376,-787,-158,676,803,-159,290,657,1000,-9,381,-841,775,-128,-1000,-366,575,211,406,265,-869,-504,-861,280,-324,922,-1000,971,749,809,-1000,-958,-1000,253,969,-493,217,-386,-162,200,942,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{-382,-1000,1000,-112,1000,568,404,-1000,731,371,410,-670,651,328,-333,-210,-1000,702,-798,810,727,70,1000,690,-167,-1000,421,-327,-1000,145,-549,-730,712,799,-119,-1000,-548,-34,230,1000,1000,1000,-1000,-144,919,473,-156,140,1000,-161,124,-1000,173,277,882,1000,93,258,-706,-262,614,105,43,949}));
    }
}
