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
        org.junit.Assert.assertEquals("java.lang.String:LTk4Mg==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-198,-982,-511,-265,-352,789,146,519,-838,-319,438,600,406,-152,884,-759,-266,-885,-175,343,503,-358,-527,-450,-809,923,-345,626,-562,-302,-768,-43,-336,-229,962,427,997,-348,585,-39,-501,-450,818,-443,-73,-741,43,768,-411,-806,-426,-693,259,38,545,-343,-433,224,-281,-583,794,862,804,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{498,-199,-41,-939,841,-509,-642,-1000,-93,-230,-380,36,216,-684,-1000,190,-99,-1000,-1000,396,144,-1000,1000,-921,525,-848,-598,-31,447,56,-404,-339,524,-21,259,89,-619,-19,-896,776,489,83,927,-776,-432,364,-271,-1000,-916,-213,-1000,-979,83,521,-880,-1000,654,1000,-1000,-420,-898,384,-36,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:NTIx", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-966,-521,-52,-45,739,97,158,747,-427,232,434,-92,408,351,204,279,-725,-179,-804,-538,-157,182,-37,-492,220,-189,-769,-139,48,734,-659,-463,-642,291,624,-226,767,-562,669,-291,-732,-451,-864,-939,-944,795,-977,-776,638,-633,-894,-305,-182,154,-359,373,-956,-187,377,-143,-903,611,399,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-574,-7,375,103,902,-288,488,548,-101,1000,149,210,946,-490,739,778,-307,-1000,-1000,-676,-342,387,-207,-104,534,116,-96,32,-279,745,-54,-1000,-433,-51,123,756,5,-868,-530,373,-1000,-672,-1000,-938,-851,1000,59,-83,419,450,-670,141,-476,-254,416,-352,-411,-617,1000,-125,-1000,-818,224,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:VDZGSTZCMEM=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-145,-34,-326,-241,140,197,-65,-527,328,1000,250,-284,748,967,-725,-851,108,335,542,-284,-1000,-108,187,275,-160,325,1000,-899,-1000,-921,1000,1000,691,699,-1000,-242,586,-2,-494,103,-236,-649,-623,-805,-424,-821,1000,-756,-324,122,-841,6,-762,672,629,-923,-718,-310,-893,-660,-1000,79,171,428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{82,816,479,-171,-3,773,-269,272,514,-90,-991,-6,185,-744,707,63,-814,97,-867,123,-81,-703,-784,95,-834,-136,-607,-645,-282,-520,-389,637,-19,922,-669,-355,-223,-962,299,-381,-754,-728,941,966,-399,582,-736,-527,-681,-922,-680,561,-720,-341,482,-371,-635,-470,-960,-435,-941,-406,-12,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{-550,-83,865,969,-966,27,-710,555,-494,30,-473,816,345,23,-337,-369,-722,562,97,712,-598,348,18,749,-602,-637,-616,-986,14,405,-374,117,318,-124,405,656,-210,-571,-704,413,-790,-390,57,416,538,963,-741,-279,-507,-722,498,76,366,-402,147,772,778,417,651,928,534,-917,760,-784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{994,687,505,171,442,-159,-249,397,-1000,97,-233,410,-17,447,-57,1000,-303,1000,611,-784,-457,357,-418,1000,-430,1000,-998,615,-1000,-392,-804,-762,-44,-578,1000,-118,-1000,1000,927,1000,-1000,-228,420,-505,666,102,1000,-39,357,-887,718,-146,716,-541,583,723,53,49,256,361,1000,-1000,200,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-274,926,781,137,850,69,-92,136,329,1000,-926,-470,1000,-123,1000,-602,-819,1000,-1000,-1000,-525,540,-376,-986,1000,-909,-293,901,-1000,-382,-1000,449,-114,348,233,968,250,-700,355,518,-41,620,-19,239,-931,329,-1000,-406,-206,-1000,944,-243,-1000,521,889,-1000,1000,1000,179,1000,-894,-416,585,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-574,-59,1000,-775,729,-177,-517,654,-882,626,274,731,1000,367,-175,667,-911,21,-1000,-1000,-146,1000,-1000,-463,-383,-610,284,439,-882,-813,-98,-589,1000,-829,1000,328,-267,894,-873,-137,-1000,287,-618,-212,1000,-978,-424,155,-322,-159,622,714,-251,137,-554,-331,171,-1000,-961,1000,-1000,515,-170,-260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{682,-487,-678,124,-466,-945,-433,371,-18,904,59,-324,1000,-647,568,983,-557,921,61,-123,983,413,-487,-331,-551,450,-978,1000,-1000,1000,-403,562,-881,-829,104,729,943,1000,-1000,-300,-302,119,-897,648,1000,-1000,-899,-513,-1000,1000,1000,-699,402,-258,80,789,-685,733,43,1000,-118,512,-67,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-158,884,195,-1000,604,737,-623,-43,175,-458,423,48,-1000,1000,-298,936,-93,-329,-1000,-1000,32,-403,-533,826,-324,700,883,-1000,62,-1000,-191,1000,259,1000,-831,80,1000,-882,110,-99,-1000,168,-326,322,-1000,-4,-296,-748,-340,-296,-472,211,1000,529,-1000,-721,187,-349,-649,730,-1000,-151,-789,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-75,969,-121,-963,-157,-813,684,-234,952,-381,-85,-49,-438,158,137,872,-680,638,-903,-648,-90,-326,-741,653,-459,923,-199,-136,-968,646,754,-903,-497,872,-716,616,646,221,282,-803,337,-510,-660,482,972,-520,-32,-171,-693,223,481,303,811,-241,-637,812,605,930,-890,111,880,256,-423,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{98,-183,-1000,977,295,-1000,685,-206,-389,-368,-768,-1000,195,-1000,-1000,830,-380,155,-400,57,-1000,-501,907,-488,-770,275,1000,132,-289,155,-73,-801,113,339,-271,-1000,994,-938,320,-1000,-1000,149,757,186,-674,183,-760,300,952,-744,-230,-814,1000,532,-1000,715,636,-598,-857,772,-534,-578,135,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-982,888,-441,-967,725,57,482,-326,-846,-650,998,419,-17,362,-881,-488,-613,-801,140,136,927,-238,-471,138,-443,-722,637,612,-318,-574,-739,996,-860,822,733,-318,-262,-565,-413,-167,-453,581,-896,84,559,-469,83,-869,-526,-483,-989,-201,-571,-104,-489,153,289,-384,-620,411,746,-248,865,-973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-561,537,483,-734,-406,-360,402,-155,-482,-171,31,259,-382,95,290,338,-961,694,-560,966,-264,130,606,-844,-325,990,207,48,23,653,829,-301,-794,375,638,881,411,51,897,-189,720,-18,-673,701,-573,-539,142,-657,308,609,-608,559,-408,-990,970,-729,236,-43,61,-795,-277,814,-860,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-294,336,9,-367,-1000,-1000,28,750,-270,-254,97,-336,606,236,410,-759,606,-1000,-1000,424,-274,341,1000,1000,877,-924,-1000,-380,538,1000,-1000,628,433,875,-1000,947,925,29,-762,1000,-321,350,2,-271,-318,-346,111,223,187,58,144,-985,732,976,228,-456,-630,-1000,738,392,-1000,-258,1000,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-496,-435,1000,-859,1000,-162,-875,658,664,-104,1000,44,-1000,1000,984,-1000,356,-534,-1000,1000,-665,-433,-1000,238,506,1000,764,-834,741,-1000,9,-742,-490,-977,-204,-1000,16,-662,-541,-8,870,-1000,-1000,-288,-88,1000,1000,1000,472,245,800,1000,-133,-784,1000,905,-178,936,-1000,-1000,1000,124,-1000,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{865,-600,1000,512,-964,-1000,-538,-589,243,667,-833,1000,-85,762,-812,537,946,-642,773,-314,1000,1000,1000,-827,-414,1000,-897,-650,722,1000,50,-436,-687,905,992,-426,-639,-1000,950,382,1000,-244,-216,-389,-1000,-234,1000,655,768,-197,-815,444,-317,737,-488,627,-921,-1000,-1000,-694,365,1000,1000,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-600,-600,-1000,763,1000,-58,1000,-110,893,656,1000,246,97,-1000,1000,-912,-803,314,-642,1000,509,-221,126,-926,-141,-1000,1000,391,-1000,-1000,-1000,-223,-1000,648,988,414,11,-774,702,1000,-1000,-199,1000,-252,428,557,865,636,-850,510,-1000,1000,848,181,1000,-341,-566,-273,1000,-199,-767,-343,204,-573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{-125,-1000,-150,-446,-8,-254,60,-933,-841,94,-473,-277,-119,237,-651,-549,-744,569,-491,-1000,-22,185,-316,815,-529,869,481,-556,-31,-1000,694,302,-960,174,285,441,-388,55,39,-1000,-99,1000,-175,-181,-948,-11,133,-520,-979,-677,1000,32,-262,1000,540,131,128,1000,-1000,446,710,-236,-98,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Node", "org.jsoup.nodes.Comment,org.jsoup.nodes.DataNode,org.jsoup.nodes.Document,org.jsoup.nodes.DocumentType", "wrap(java.lang.String):org.jsoup.nodes.Node",
            new int[]{943,999,-728,103,15,224,-195,-258,640,554,100,854,-387,-427,-581,253,-205,422,-76,842,-271,283,642,971,830,-48,-466,278,-794,-972,-331,-770,90,-763,736,-899,863,-32,379,-805,-320,-750,-840,836,834,61,385,793,-840,129,-575,676,25,-805,750,-906,735,-923,407,365,-430,835,-629,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-646,-922,-781,381,615,-325,-67,-424,-509,-35,84,141,399,-458,629,-954,688,-382,-794,465,926,469,686,-65,358,287,992,-131,-39,38,850,-976,-350,-754,-197,-344,-108,-25,-950,258,-786,807,434,-983,-70,-941,267,-578,768,117,584,818,-974,-619,871,-871,402,-989,384,450,-326,476,725,761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{802,-758,232,-930,888,-551,150,740,-838,-724,-148,675,729,-732,-809,236,661,-749,-563,-439,-186,220,191,941,12,591,-397,71,235,78,772,-506,-465,817,842,-359,141,-298,-475,-954,-596,394,582,580,116,-33,-860,800,-941,-268,-406,911,-385,420,-625,245,-260,630,837,645,-302,183,-837,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{490,-642,-366,1000,-282,-457,426,-416,-995,-441,-656,-1000,183,1000,1000,1000,18,-1000,-546,-86,-320,1000,461,948,330,676,1000,449,311,-246,-1000,175,931,238,1000,202,215,597,-194,-4,-948,-1000,-61,224,-89,21,-257,-1000,-881,-765,786,1000,-356,570,-1000,-45,598,135,71,-820,358,1000,886,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{98,-316,-201,-42,42,-833,-817,101,895,695,233,-332,164,-545,-142,769,599,-769,-655,194,109,635,627,365,-174,-106,-514,-940,-12,-160,246,981,-372,-294,189,-309,-466,831,-391,470,382,-575,-803,-733,-465,-792,276,49,569,-118,-697,593,-696,184,-1,-966,825,558,504,338,-216,377,-991,-723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{898,-967,300,-161,-836,85,206,873,-798,-918,575,414,-401,-663,603,-886,-509,363,-641,372,710,-432,-364,-730,184,-432,-131,-377,801,-196,231,252,-689,145,704,-917,-725,631,600,-308,-459,-420,794,-486,279,-70,-253,104,-767,833,288,-468,496,320,109,-688,878,-866,886,-912,711,-276,-914,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{186,-1000,1000,514,1000,1000,-1000,-313,-524,1000,439,-1000,-931,1000,918,1000,512,-984,-1000,433,1000,1000,753,-285,493,-1000,-1000,547,-600,-753,-1000,383,1000,-64,-1000,1000,1000,923,557,543,107,206,-948,465,-1000,-1000,1000,392,-564,1000,-1000,328,-1000,-1000,-857,919,-948,-1000,378,-74,726,-874,-1000,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{-94,60,447,-582,374,-709,-861,-889,413,77,-767,-51,-988,-630,247,-133,-828,585,-751,571,-756,412,-790,60,840,583,-464,-631,-6,220,-978,-937,-693,-743,-294,-736,780,-389,198,-387,-274,265,929,423,-288,-751,-524,-461,832,-402,-393,922,777,-178,-966,807,146,166,-10,-549,238,-39,-322,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{66,108,836,-993,-382,226,-1000,-725,662,84,-801,718,-893,-1000,638,-665,-683,649,-463,111,-1000,653,-222,-473,1000,46,-52,-220,-486,-853,-757,-315,-949,-5,-1000,-485,1000,-101,97,-733,297,305,624,377,-187,-846,151,880,1000,-42,-1000,1000,1000,-957,-1000,251,934,398,-574,175,-384,538,-31,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{408,866,738,978,-342,-631,970,-915,-934,893,709,-537,283,-880,-619,-138,-628,719,-947,483,414,651,-10,808,-356,-957,412,-36,-659,107,-936,-389,812,607,288,-323,447,-474,9,631,58,-279,-704,-515,498,617,-46,190,-109,279,9,688,722,-450,-145,540,-497,406,365,725,765,-887,310,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{-902,879,-324,-298,-334,-382,308,-325,-760,-301,113,-415,-392,1000,118,-338,393,1000,-439,462,-411,416,66,1000,332,-1000,557,371,720,54,-1000,-77,1000,98,665,324,-323,-346,1000,-22,463,887,343,-1000,-1000,479,-233,-1000,556,337,309,568,-79,-237,-292,-1000,-1000,-344,1000,-311,419,-216,-253,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{-186,749,-547,612,633,33,1000,-197,-1000,-953,-1000,-123,-400,631,-532,1000,1000,711,-826,-262,160,-262,-13,3,732,-955,-109,-685,413,1,995,599,-470,718,1000,-691,475,-442,1000,-726,-990,-1000,1000,306,-74,1000,-1000,-454,-430,-1000,-399,157,85,-711,1000,976,247,-977,875,400,-400,198,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "append(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,59,1000,879,1000,880,-95,316,-1000,-1000,736,-1000,180,-326,185,-512,-943,-148,-798,-1000,-918,-1000,-598,391,1000,-462,1000,180,-39,35,493,1000,-348,1000,-332,-369,1000,-416,465,-579,69,-97,1000,169,-743,716,678,-422,-1000,-722,640,-1000,-548,248,-148,744,-263,262,610,751,835,109,-125,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "html(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-266,-345,717,-982,-197,670,-268,-788,519,771,84,-1000,-1000,-997,-202,-705,506,-592,384,-86,-621,-598,11,453,-25,-693,413,308,-470,-140,-258,150,111,20,-1000,-453,-707,-326,-629,487,-401,-31,-223,-624,-877,289,383,-236,87,-25,-151,-1000,-485,-374,-10,-843,20,276,141,1000,-50,-517,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prepend(java.lang.String):org.jsoup.select.Elements",
            new int[]{70,709,-506,198,391,193,-49,-1000,-1000,-274,97,194,-157,716,1000,696,-698,-1000,-907,687,1000,674,-371,1000,-665,-630,-526,123,-119,-245,-395,834,-26,-1000,1000,-105,-934,-1000,-1000,-1000,173,1000,-66,1000,1000,1000,370,128,131,-1000,849,158,1000,729,1000,-682,1000,512,1000,-1000,-1000,-1000,-818,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "prepend(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-891,1000,432,734,-1000,321,-104,-311,-164,945,589,-272,-184,212,558,650,1000,-540,887,707,-1000,-97,1000,-49,1000,558,-445,-454,664,-603,-1000,-601,124,-693,-669,323,1000,432,-320,290,561,-1000,-174,642,-442,1000,-1000,-554,769,428,-349,-381,105,464,-606,-1000,830,927,228,-1000,-798,234,-220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.select.Elements", "org.jsoup.select.Elements", "wrap(java.lang.String):org.jsoup.select.Elements",
            new int[]{293,-751,631,-30,1000,1000,231,-672,92,-707,-588,101,-1000,-148,936,458,-972,-1000,-1000,-931,-282,-452,-360,794,896,-1000,-1000,-785,-1000,-222,431,730,681,498,-670,-514,99,-117,649,1000,-1000,-431,-128,-224,-356,1000,64,-508,-298,-645,582,736,345,259,-855,-855,-1000,-412,318,-326,187,58,-97,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.parser.HtmlTreeBuilderState", "", "valueOf(java.lang.String):org.jsoup.parser.HtmlTreeBuilderState",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.jsoup.parser.HtmlTreeBuilderState;:23:52:ENUM:org.jsoup.parser.HtmlTreeBuilderState$1:Initial:55:ENUM:org.jsoup.parser.HtmlTreeBuilderState$2:BeforeHtml:55:ENUM:org.jsoup.parser.HtmlTreeBuilderState$3:BeforeHead:51:ENUM:org.jsoup.parser.HtmlTreeBuilderState$4:InHead:59:ENUM:org.jsoup.parser.HtmlTreeBuilderState$5:InHeadNoscript:54:ENUM:org.jsoup.parser.HtmlTreeBuilderState$6:AfterHead:51:ENUM:org.jsoup.parser.HtmlTreeBuilderState$7:InBody:49:ENUM:org.jsoup.parser.HtmlTreeBuilderState$8:Text:52:ENUM:org.jsoup.parser.HtmlTreeBuilderState$9:InTable:57:ENUM:org.jsoup.parser.HtmlTreeBuilderState$10:InTableText:55:ENUM:org.jsoup.parser.HtmlTreeBuilderState$11:InCaption:59:ENUM:org.jsoup.parser.HtmlTreeBuilderState$12:InColumnGroup:57:ENUM:org.jsoup.parser.HtmlTreeBuilderState$13:InTableBody:51:ENUM:org.jsoup.parser.HtmlTreeBuilderState$14:InRow:52:ENUM:org.jsoup.parser.HtmlTreeBuilderState$15:InCell:54:ENUM:org.jsoup.parser.HtmlTreeBuilderState$16:InSelect:61:ENUM:org.jsoup.parser.HtmlTreeBuilderState$17:InSelectInTable:55:ENUM:org.jsoup.parser.HtmlTreeBuilderState$18:AfterBody:56:ENUM:org.jsoup.parser.HtmlTreeBuilderState$19:InFrameset:59:ENUM:org.jsoup.parser.HtmlTreeBuilderState$20:AfterFrameset:60:ENUM:org.jsoup.parser.HtmlTreeBuilderState$21:AfterAfterBody:64:ENUM:org.jsoup.parser.HtmlTreeBuilderState$22:AfterAfterFrameset:60:ENUM:org.jsoup.parser.HtmlTreeBuilderState$23:ForeignContent", DEReplay.run(
            "org.jsoup.parser.HtmlTreeBuilderState", "", "values():org.jsoup.parser.HtmlTreeBuilderState[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
