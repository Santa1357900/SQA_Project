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
            new int[]{348,102,-1000,-1000,154,1000,529,-208,-1000,-31,11,248,23,-39,-26,837,303,64,-529,115,1000,1000,1000,-697,1000,-169,714,599,276,216,-493,839,501,-37,-25,-462,-1000,-367,20,-525,56,-629,552,668,-962,-1000,-892,-90,-1000,440,1000,-274,463,397,-1000,-475,-27,868,324,-1000,80,-54,1000,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-201,-6,982,720,-860,49,618,-457,-35,459,565,958,-372,1000,-714,954,19,75,-428,188,220,548,390,-678,-771,-1000,16,-263,147,356,625,435,-521,427,-1000,311,159,-610,438,-983,-862,-251,-39,1000,306,-51,508,1000,-263,243,-648,-585,-1000,605,324,-1000,-243,556,-731,-745,-774,562,700,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-1000,-240,-1000,-9,-556,1000,419,260,600,692,-681,637,-232,-398,220,-185,599,602,-742,80,-642,516,-165,-198,1000,854,508,158,-64,618,-572,-330,-877,-1000,771,285,-1000,-1000,252,68,459,-735,-554,677,-680,-488,-383,-1000,-1000,210,1000,-485,-736,523,1000,-483,894,-351,1000,-1000,-499,879,-788,-627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{388,-414,138,391,-64,844,225,1000,193,111,1000,754,209,-1000,255,-1000,-535,-324,629,1000,8,172,-376,-842,29,859,-1000,127,3,716,73,-1000,-585,273,-33,1000,-765,590,755,-521,-605,-940,352,-923,-1000,-33,-909,679,942,91,-665,-1000,1000,1000,-971,-896,345,-966,167,-880,353,136,717,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{532,153,1000,897,-712,-202,914,159,-666,1000,859,-1000,880,918,-1000,-180,203,705,-209,270,-1000,978,-1000,237,1000,-377,1000,1000,672,455,669,-318,-530,1000,-1000,501,-1000,-79,-814,317,1000,991,767,-837,-605,124,147,926,-649,224,-830,244,-1000,-860,-131,67,-1000,98,-1000,389,-1000,-426,335,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-888,378,22,1000,-332,691,-577,1000,-653,336,751,-816,9,334,-1000,1000,428,1000,-1000,-312,-608,-1000,-1000,-783,-195,-1000,848,-1000,-387,749,1000,-340,-510,888,-304,-1000,-1000,405,10,-537,-116,-1000,-723,-1000,311,-73,-524,-879,-117,509,-1000,45,-1000,-920,84,65,-217,706,275,-158,1000,-1000,-1000,-995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{878,720,-649,722,-982,-256,-1000,1000,-791,994,-262,-351,-632,-1000,-1000,-526,-87,-363,1000,-722,312,1000,58,836,-1000,-536,-738,-249,-412,-902,-1000,968,191,203,-595,603,-665,-1000,-880,1000,-997,1000,-1000,20,510,322,-855,903,743,-1000,-1000,389,804,-1000,-355,-255,-74,1000,604,340,1000,-1000,-165,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendTo(org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-926,447,1000,37,-191,408,-341,1000,-477,520,-365,130,676,-1000,-1000,618,1000,-351,73,67,1000,-514,789,-995,216,-354,1000,-732,455,-243,705,902,400,-885,128,268,-484,811,429,102,853,684,-1000,-1000,935,-328,-352,-138,-596,-1000,-758,1000,744,456,322,727,483,-439,-574,-112,188,-421,-637,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendTo(org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{737,-408,445,-804,-195,-80,-246,62,535,-54,325,389,25,19,-198,-779,-118,-1000,-24,768,238,-449,-679,534,637,-753,-327,-618,480,-347,79,83,-1000,704,665,1000,-616,409,142,-333,132,-150,4,593,536,-817,448,1000,-254,-225,493,23,109,1000,132,250,236,-23,-317,-703,570,534,-306,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{375,-768,1000,-394,-400,942,644,-400,-497,383,-1000,-2,1000,-726,442,831,92,83,-262,-1000,347,-431,347,8,-538,-241,698,-701,809,-634,-157,-189,-695,137,-1000,-181,-390,-818,-875,866,-617,215,-473,-483,99,661,-1000,40,1000,-458,983,332,1000,232,924,828,1000,-89,-779,-480,325,182,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{-403,909,439,-1000,519,174,-905,540,406,-1000,-1000,279,-1000,342,1000,-378,283,-876,1000,-1000,72,-1000,-1000,-155,-1000,-630,-65,-381,210,-30,883,-841,-270,-123,-374,-1000,1000,1000,-152,125,248,297,-1000,-932,-399,406,724,1000,1000,394,102,-1000,-1000,631,-717,-1000,335,-1000,-993,-578,744,672,-1000,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attributes():org.jsoup.nodes.Attributes",
            new int[]{902,-57,958,903,554,730,772,-35,337,-311,-717,-761,-817,947,-329,-820,925,-363,-846,988,-989,-193,971,80,-258,705,-966,-893,-300,-174,-714,451,-857,197,915,-556,-218,-548,-743,977,594,-548,484,-428,-908,-583,-7,568,-165,861,-239,-401,860,952,909,-780,179,380,182,-780,552,260,-849,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "baseUri():java.lang.String",
            new int[]{400,-33,699,343,-285,1000,809,-854,878,419,1000,-219,830,-1000,1000,-891,875,334,1000,109,329,-246,-1000,1000,586,1000,1000,-364,-237,-319,1000,167,-1000,-181,-92,1000,-899,138,746,631,-386,1000,1000,-1000,942,521,-265,176,-279,-560,-29,-629,-407,1000,386,585,1000,929,277,788,-69,-619,-244,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-179,-660,863,1000,644,486,-1000,-161,57,-1000,-635,348,599,-1000,-173,-505,943,-16,-1000,45,-515,216,43,882,1000,446,301,-140,-661,-1000,149,-1000,-785,-532,-290,-1000,1000,416,-383,1000,-1000,-1000,591,1000,-1000,-430,687,86,-1000,-570,-230,701,-1000,-1000,-1000,1000,-635,-434,-1000,-738,-739,499,691,854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{1000,-888,-1000,-703,-1000,343,-377,389,1000,-417,1000,1000,-415,748,-160,-1000,-1000,-127,-975,-337,-1000,1000,210,-1000,727,-1000,-448,1000,1000,28,-1000,-1000,889,-187,293,-643,-380,-1000,-1000,1000,1000,-76,-355,-856,-362,-1000,1000,-825,1000,25,571,253,-111,979,794,-1000,231,-1000,-964,-1000,832,-900,-750,620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "child(int):org.jsoup.nodes.Element",
            new int[]{-1000,828,1000,1000,-1000,-1000,393,-240,-674,1000,497,1000,-657,1000,1000,1000,-1000,-156,-1000,862,-1000,631,-1000,-1000,1000,149,-1000,1000,-1000,453,1000,861,-1000,-860,-881,-972,1000,1000,1000,-23,-1000,-1000,-300,-763,122,1000,525,-1000,-1000,802,57,1000,-1000,1000,-1000,-1000,277,-922,-863,-651,557,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "childNodeSize():int",
            new int[]{1000,663,630,-400,353,105,-631,204,670,453,697,320,-2,-288,1000,-814,-585,1000,-251,1000,-205,-37,937,-513,-181,-240,883,-340,1000,-354,-1000,-441,-472,825,-1000,643,822,-576,-979,-130,-133,1000,480,-1000,-912,-318,679,-764,-971,-90,-669,-1000,1000,-298,-232,-28,-767,-742,981,-1000,819,37,-416,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "children():org.jsoup.select.Elements",
            new int[]{-465,-828,1000,1000,-399,661,624,44,415,1000,434,146,911,-685,771,911,1000,-455,-1000,-890,984,-1000,-539,616,789,-610,-1000,-1000,231,-1000,372,216,-943,443,-922,395,-1000,358,-87,-939,-880,-17,-651,-790,152,-393,455,-285,501,-855,1000,1000,81,-78,-93,174,1000,1000,-379,-365,1000,-1000,732,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:MTc0", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "className():java.lang.String",
            new int[]{-333,-483,1000,400,741,-522,-256,-1000,-534,174,-44,-81,-369,-106,1000,542,-114,-166,-67,283,86,275,646,1000,-5,598,-767,-444,819,-128,297,291,109,-272,476,115,-393,141,786,400,1000,-248,-1000,-374,-822,352,108,-274,-296,589,-167,-230,825,398,-1000,408,-253,738,-307,-7,25,411,400,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashSet", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames():java.util.Set",
            new int[]{148,42,1000,920,-1000,-323,449,302,-397,991,-362,1000,-615,1000,-269,-1000,204,-717,-1000,-983,263,1000,-711,-279,-711,218,90,-1000,1000,-1000,-518,143,318,57,-1000,-667,423,-646,1000,1000,1000,-890,-945,-610,711,-994,-272,328,932,1000,-1000,12,333,-1000,757,-964,-290,-1000,533,-652,797,523,-596,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{860,609,1000,1000,962,2,-121,503,312,-857,-592,-986,-230,-432,-184,75,-566,327,-516,404,1000,-492,332,144,-225,1000,137,834,-1000,1000,-773,-677,-1000,593,1000,-537,-944,-425,694,-436,-928,-967,-288,81,1000,124,378,1000,-152,1000,-1000,1000,1000,492,623,-1000,1000,1000,-26,454,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "clone():org.jsoup.nodes.Element",
            new int[]{273,-759,361,-196,986,254,84,-1000,127,116,367,-532,934,895,805,-1000,714,-1000,466,700,-567,-1000,928,-451,-226,562,1000,577,-321,440,873,681,503,-1000,683,344,933,1000,2,-334,-81,456,1000,296,1000,619,-177,1000,-1000,1000,447,541,-925,-549,636,96,697,-89,-224,104,-58,-71,-1000,444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMC4rMHg4MDA=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{1000,849,346,-1000,1000,-291,-631,-152,-264,1000,1000,720,-216,452,-270,-578,-52,-1000,339,515,1000,1000,-15,1000,-1000,-715,-930,181,-1000,-769,664,1000,-674,-491,-1000,-150,-421,-1000,-1000,-1000,832,572,1000,-1000,-698,740,88,1000,-148,325,-55,-950,326,129,-707,-167,246,1000,1000,934,1000,-255,-303,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:LTQyMA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{937,945,83,-420,522,958,368,76,-3,559,758,938,-916,1000,-148,158,-716,-244,745,423,737,876,-460,367,-297,-142,-725,-1000,-981,-1000,-667,67,-674,451,-913,-455,1000,-932,-275,-818,-1000,-812,722,-1000,-1000,-178,1000,498,-1000,552,-797,-1000,28,308,-452,-1000,589,603,575,1000,416,56,374,-373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "data():java.lang.String",
            new int[]{-527,663,744,-620,300,-1000,-587,393,-1000,-1000,-177,-971,-791,913,583,1000,-660,1000,-1000,345,482,-834,574,7,233,-173,-53,-866,-200,1000,-1000,-930,1000,-1000,154,-1000,-591,1000,520,-1000,1000,917,31,851,-598,389,-629,-1000,903,-1000,501,-1000,-851,987,-844,-75,395,-769,1000,701,-353,341,1000,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataNodes():java.util.List",
            new int[]{-908,-837,3,-686,220,138,-770,533,0,-337,-102,-931,1000,712,512,-491,-417,299,1000,-102,555,-96,147,305,1000,-434,-980,-517,719,-1000,191,-349,320,-763,-739,367,-934,645,1000,1000,265,-257,834,-112,1000,-800,409,867,-14,-293,264,914,201,334,390,-668,678,771,300,960,-70,-563,-817,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$Dataset", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataset():java.util.Map",
            new int[]{491,126,359,100,48,824,-166,1000,-355,666,-564,1000,-532,-306,27,-295,-462,-695,-238,235,-331,-180,1000,-357,-1000,-926,-1000,1000,1000,610,-444,-458,87,-700,1000,120,992,94,-939,-226,-1000,1000,-1000,-113,-160,-1000,727,1000,87,-205,-391,-154,4,-569,536,1000,-246,1000,948,996,-889,594,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "elementSiblingIndex():int",
            new int[]{1000,-783,1000,-362,-431,1000,129,1000,562,323,-262,-1000,225,-291,1000,-666,1000,105,1000,849,933,858,208,1000,1000,-1000,-1000,-1000,1000,64,1000,356,120,-549,-1000,1000,32,-890,-1000,-776,-663,-887,1000,1000,303,1000,371,-978,182,260,-282,-968,840,-1000,-389,1000,-281,-983,-797,769,-400,1000,-572,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "empty():org.jsoup.nodes.Element",
            new int[]{400,-441,-764,566,179,894,700,-505,-165,-319,-1000,-869,120,-181,822,499,-801,-1000,1000,401,-1000,403,429,-1000,-611,-171,-1000,426,-130,631,294,432,-1000,1000,-2,1000,143,91,931,957,-17,-23,822,-26,-26,-1000,1000,-349,221,-1000,-759,185,-242,277,-453,695,1000,-813,798,489,683,175,-973,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "firstElementSibling():org.jsoup.nodes.Element",
            new int[]{-857,723,-403,194,551,-734,744,-321,-908,-403,67,480,493,672,-310,-601,-298,934,-365,-506,529,3,968,-346,782,-762,-351,71,751,-985,277,360,682,102,-747,332,286,76,-208,-177,249,-625,685,-614,230,-721,754,143,-134,-105,-466,654,970,34,-141,-749,273,770,-212,-27,808,425,-463,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getAllElements():org.jsoup.select.Elements",
            new int[]{935,399,804,643,-815,89,-76,-1000,143,-453,430,758,-772,193,675,-623,1000,-843,-791,-744,-422,101,1000,382,1000,511,783,1000,914,-1000,92,-1000,1000,1000,941,1000,429,910,412,1000,252,954,1000,219,1000,-695,-1000,1000,1000,-838,-409,-80,-160,0,-1000,45,531,1000,358,277,660,-330,1000,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementById(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-841,-627,-70,272,748,-566,-63,1000,647,671,400,-405,1000,1000,-346,393,-482,588,-1000,413,1000,84,-220,-111,-253,1000,-898,-1000,-1000,135,-1000,-638,685,149,-175,-237,376,-486,-1000,1000,460,-44,-445,-616,117,440,-666,-1000,-376,-954,-527,1000,-1000,-1000,-72,1000,-1000,1000,-925,722,-1000,-172,913,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttribute(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-105,-1000,-1000,-45,-1000,608,-87,-334,-414,-542,145,-486,1000,773,-831,871,-367,1000,36,294,-246,1000,51,205,-409,136,-1000,1000,696,482,-1000,-1000,-940,529,578,1000,-977,-1000,-1000,-1000,-677,439,-960,396,-825,-641,415,-807,-812,-536,190,-440,141,-1000,1000,85,364,-765,-1000,1000,341,37,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeStarting(java.lang.String):org.jsoup.select.Elements",
            new int[]{-153,-183,1000,192,-1000,-1000,934,-648,393,1000,-266,-164,-397,-558,-662,119,-231,-1000,-1000,-1000,553,-1000,-1000,-1000,-1000,575,-1000,-1000,-1000,-73,-1000,207,-98,-190,217,903,-1000,724,-764,-37,-1000,-48,188,-15,93,1000,-1000,116,1000,-580,1000,-367,784,935,-998,-217,1000,650,-1000,1000,-396,-407,-683,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValue(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{469,-360,-130,570,-831,-824,241,-152,198,-565,495,1000,708,-316,543,-10,1000,299,-389,-866,995,-937,-154,1000,625,-467,196,357,1000,-564,-331,-834,343,-1000,-373,1000,-1000,-717,1000,839,-799,660,-1000,-917,729,178,231,1000,-1000,42,-1000,-1000,-851,528,-1000,-667,1000,1000,1000,-454,-1000,-881,-135,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{388,507,308,453,782,-778,333,207,708,278,443,-448,1000,313,1000,505,714,-38,1000,-921,-1000,-1000,-718,1000,-1000,810,-504,-1000,158,-1000,-573,417,-164,-615,348,1000,57,-1000,342,25,-388,93,1000,323,-1000,-1000,-810,-728,-328,1000,-1000,32,-1000,-354,-1000,-1000,585,333,645,1000,86,-264,-873,154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{733,987,-300,-591,31,-191,614,631,-714,865,442,-932,872,857,540,462,-712,-988,-296,115,-427,-725,446,-92,-10,79,-999,-897,320,475,675,247,-353,101,955,-348,-880,615,-305,630,-141,-23,667,977,-101,291,-249,15,885,786,156,925,-941,-46,163,-922,46,868,135,590,888,-732,764,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueEnding(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{733,639,211,436,-873,574,839,888,296,281,51,-725,140,-508,172,-819,868,99,-746,-315,-320,250,66,576,-85,-17,939,492,907,685,681,466,-518,927,-839,517,512,-290,-706,-662,311,-980,-841,877,758,732,775,682,-15,365,643,-771,-758,-35,602,-870,-896,482,-247,153,-408,418,-731,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{504,441,-1000,-243,-1000,404,-67,184,-692,1000,-1000,1000,-1000,1000,1000,-206,500,723,-1000,1000,-1000,-234,393,-1000,719,-868,-62,-328,633,-177,-1000,220,-676,-590,-1000,-983,549,1000,-492,890,-8,-433,1000,990,560,-264,1000,-803,-326,-12,408,-368,327,136,89,-493,-981,-114,-196,-438,-470,71,-662,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-687,798,-1000,-1000,-786,142,973,1000,-568,1000,-1000,29,-7,1000,1000,292,-214,1000,361,472,846,785,976,-1000,1000,-940,105,498,-481,82,-471,-1000,-219,-210,-1000,-1000,-233,738,25,-755,455,548,1000,722,1000,-659,1000,561,-1000,-282,1000,42,1000,1000,319,-923,-1000,-560,1000,-510,-1000,258,-1000,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{-761,714,-1000,-1000,147,-691,-42,1000,634,357,-567,-72,-1000,4,585,-592,-111,-1000,-789,-670,-304,-68,516,-569,1000,-382,-1000,1000,1000,-246,-176,-1000,1000,-19,-1000,-351,-1000,-1000,-652,-333,1000,693,-559,147,1000,-352,130,-1000,-796,-197,-582,542,1000,-372,-141,-257,-1000,650,750,810,-315,-682,-593,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueNot(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-327,-255,-56,-274,-1000,-1000,-291,1000,35,-1000,140,392,-501,-1000,-409,389,-582,-158,-764,-291,624,519,-48,-781,-381,1000,1000,-1000,569,-394,-606,735,-1000,-1000,174,-546,857,1000,1000,596,884,522,-468,-507,977,1000,478,-130,-1000,-481,830,-645,643,-1000,527,1000,1000,-1000,-627,768,6,-854,-337,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueStarting(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-376,231,328,806,894,130,-387,569,-926,-446,881,-822,54,-78,-478,-534,-975,1000,840,-287,-165,-452,-244,1000,582,1000,-474,-714,-795,272,-720,-1000,1000,-1000,-558,554,-1000,451,827,740,-818,-1000,-363,456,1000,-1000,-467,-445,193,-515,153,260,-581,-455,1000,679,1000,-961,-377,-291,-778,-345,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,930,1000,683,-636,-1000,-202,-497,829,1000,141,342,560,762,-1000,-1000,-488,-557,-1000,-1000,-553,276,-478,1000,279,363,-657,189,-445,1000,-886,951,-75,244,882,146,700,1000,-1000,-1000,940,-1000,-1000,1000,-400,280,-505,-103,-1000,-1000,-1000,-482,-1000,-153,1000,-1000,1000,-1000,-122,-1000,-518,1000,124,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,720,900,177,533,-112,-1000,-1000,973,1000,1000,1000,1000,975,967,-1000,-966,-286,341,-834,315,-557,1000,123,1000,1000,-425,-1000,115,1000,-1000,1000,-1000,-1000,1000,-735,-1000,1000,-1000,959,-910,-469,142,1000,-441,1000,671,1000,-681,716,-1000,-920,137,-168,1000,-1000,24,-1000,-1000,-134,1000,-802,-116,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{209,300,91,940,-1000,-1000,94,-340,-112,-159,-783,537,584,1000,388,-772,268,-561,-343,-929,687,1000,-249,304,-994,377,-696,-27,1000,-234,863,269,-319,560,666,407,892,71,-473,-576,675,104,220,1000,778,-918,-1000,-930,-664,-503,-1000,400,-549,-278,-250,-140,584,-980,-351,341,435,1000,-519,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-470,225,596,-401,879,-322,-127,155,991,-468,-764,1000,178,-899,-1000,959,-682,1000,-531,335,558,-1000,403,71,521,700,696,277,-618,1000,-1,496,310,-1000,-153,-1000,-937,1000,-1000,959,324,-642,-570,-59,-434,48,-957,472,-213,-208,-188,-59,-583,1000,867,-209,1000,1000,-1000,401,-290,284,549,-575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{302,417,-202,-458,1000,250,-883,-1000,699,-1000,-975,1000,-400,-81,205,-933,-468,728,772,716,1000,-1000,891,-145,939,1000,-1000,-1000,-599,1000,-1000,1000,-1000,-1000,398,-962,-1000,668,-1000,-1000,-1000,400,629,973,1000,1000,-36,-1000,1000,470,1000,-401,114,427,946,-1000,157,-334,-1000,1000,1000,-923,-49,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-465,-1000,-1000,-460,1000,414,324,901,1000,-1000,751,-1000,97,-419,1000,-748,1000,-1000,1000,-1000,-581,287,-402,-1000,554,-79,1000,-538,-435,1000,-1000,369,543,-1000,1000,1000,-670,-308,1000,1000,1000,1000,-789,-1000,-264,-1000,-1000,15,1000,-1000,-1000,340,658,-1000,1000,-541,1000,1000,-408,-1000,-648,-214,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexEquals(int):org.jsoup.select.Elements",
            new int[]{1000,-69,1000,471,-929,1000,-201,1000,413,-1000,-1000,546,-419,-205,790,-936,839,279,-63,-869,-329,-242,845,-99,832,-1000,-1000,303,552,-521,1000,124,546,268,-792,-422,468,257,29,1000,127,1000,-1000,352,-1000,1000,1000,-229,-657,700,-758,-1000,-497,-1000,198,314,-1000,885,-1000,1000,-903,-1000,-112,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexGreaterThan(int):org.jsoup.select.Elements",
            new int[]{870,462,1000,1000,731,-832,439,-57,-1000,-26,-747,268,139,119,409,-1000,-1000,578,-499,-606,540,-1000,-1000,-661,803,1000,-143,-110,992,-1000,-802,703,791,-1000,-1000,441,-909,399,356,128,269,217,803,-85,-1000,630,-1000,-383,548,-682,709,154,1000,374,454,-394,375,1000,970,386,-908,-732,-19,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexLessThan(int):org.jsoup.select.Elements",
            new int[]{-316,174,1000,-141,435,1000,323,-516,254,1000,1000,204,-1000,122,-1000,-1000,-947,-338,-105,-294,180,731,-698,611,414,279,-1000,-295,456,515,-605,-541,1000,1000,320,-1000,1000,1000,-541,706,-368,1000,-28,926,-1000,-1000,-655,-1000,365,717,46,622,1000,616,-341,-750,836,915,705,304,-482,284,-448,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByTag(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-486,69,1000,-1000,1000,889,-205,-1000,-515,890,-572,218,-411,1000,619,1000,1000,-1000,-227,-453,-1000,-491,574,1000,-1000,117,-1000,1000,343,113,968,542,-1000,184,-748,1000,-76,-48,-226,370,-117,-960,-7,-302,-1000,-1000,282,474,56,-1000,494,-150,207,810,1000,962,-1000,753,587,538,-1000,1000,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{832,-516,-1000,405,-373,122,-982,-831,1000,264,787,282,1000,-79,727,573,588,-218,-633,695,332,-200,-57,86,867,732,-265,288,1000,-671,-1000,-51,-91,-1000,683,188,125,701,-371,1000,-1000,209,-1000,1000,-910,-115,-1000,140,-277,-1000,-1000,480,410,54,-400,-858,-785,-162,986,-36,-562,937,115,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-221,-300,250,1000,-962,779,198,-1000,1000,1000,127,1000,1000,1000,908,521,-371,-130,-1000,70,277,-1000,-1000,1000,-1000,-188,791,-309,59,467,270,1000,1000,288,-1000,-856,870,-649,499,785,-770,-12,-294,314,296,331,-788,-1000,1000,997,5,-305,-1000,-1000,487,-881,-767,-1000,259,221,-282,705,-1000,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-293,669,180,712,450,309,-411,-1000,-1000,-1000,-192,-94,1000,58,1000,1000,583,-402,369,534,-934,-6,365,-173,1000,1000,1000,-116,-389,843,-669,1000,1000,-1000,-796,1000,1000,-455,-131,-805,-949,111,-531,836,1000,1000,-448,1000,1000,-1000,-1000,1000,-670,638,683,658,202,-1000,1000,-844,7,-249,-1000,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,507,1000,1000,660,479,38,-884,-1000,-1000,-932,481,-905,58,1000,737,-1000,-1000,1000,1000,-222,-1000,934,816,1000,934,628,-1000,74,1000,-1000,1000,1000,-1000,810,1000,-372,-1000,-384,-426,16,-660,333,918,419,811,-529,946,1000,-1000,-253,1000,-876,638,1000,1000,689,-1000,1000,-844,-1000,885,-1000,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{-245,-231,-790,-902,104,934,978,-920,662,379,3,75,622,784,-459,-636,100,-978,-594,-859,-151,-75,-737,407,901,855,263,-252,-588,503,79,-388,-584,199,-173,804,162,-82,351,138,-774,196,-49,464,-293,-828,-288,585,-546,-876,327,-982,512,-993,-485,-39,-263,767,-5,378,892,-524,318,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-288,213,94,296,322,178,-1000,1000,671,-14,634,284,353,1000,-843,-97,-156,-337,-222,-249,280,-4,-353,305,-214,-202,-23,427,514,-151,654,146,319,-410,-486,-56,678,110,83,-908,-311,-444,-150,-526,154,1000,570,-113,116,693,-257,-29,1000,-168,-140,300,-354,-670,328,851,-917,-998,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,840,618,-119,-351,-467,-1000,1000,72,-171,1000,-547,1000,-604,147,-1000,51,-1000,-1000,270,-613,437,-545,1000,58,5,-442,1000,-1000,61,637,1000,825,1000,-1000,-411,853,-944,-1000,-51,-152,-1000,-435,1000,481,522,-934,443,-1000,1000,655,907,-678,1000,484,1000,-512,-1000,-468,-430,479,-832,773,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{-71,-660,488,-532,-565,940,-607,698,290,-273,-1000,-1000,174,-1000,-360,-977,-1000,1,1000,-273,217,-680,843,1000,775,-276,-236,-1000,-51,-362,486,-368,-86,1000,1000,-821,678,681,485,-461,-236,598,-164,21,-276,-345,-482,-663,-259,-1000,1000,-273,-547,157,88,-319,1000,-70,265,1000,660,566,750,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{1000,-354,-582,-76,-1000,629,-540,-1000,-11,715,1000,965,-982,90,-1000,-933,136,-638,387,383,-672,-1000,-614,14,166,-1000,-1000,-868,-956,-1000,491,22,1000,1000,456,-545,639,-874,-1000,-632,-1000,864,426,1000,-47,-574,-1000,137,-981,-415,-648,599,-69,41,1000,-184,-1000,235,-450,-820,-327,42,544,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{121,-354,-582,37,-914,629,199,121,-489,715,1000,922,-346,1000,-1000,-666,990,128,1000,987,265,-413,-813,-1000,-1000,-1000,-1000,-499,-910,136,233,1,705,471,359,-1000,-901,442,1000,-726,-1000,-776,1000,1000,-1000,952,-750,-1000,1000,-860,95,1000,1000,41,-435,-858,-233,1000,1000,-1000,-938,427,279,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-573,-618,-1000,10,-1000,198,-336,-33,-243,-268,-857,-1000,1,-1000,-38,-430,-1000,246,-860,-271,-1000,-651,763,1000,166,1000,-1000,-868,-956,-768,653,-950,-504,-1000,-785,1000,353,1000,-1000,-632,1000,703,-582,-1000,1000,-1000,548,1000,-963,641,-1000,599,-1000,1000,-1000,252,-1000,-1000,-1000,1000,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-1000,-924,-876,-570,1000,397,-365,-660,-1000,-943,489,-1000,755,-1000,-716,-278,-553,748,-1000,-209,-881,-1000,443,1000,1000,1000,1000,1000,-887,395,-457,-1000,-124,-1000,-1000,1000,-352,1000,-1000,1000,1000,1000,-1000,-1000,-798,-1000,1000,772,-1000,1000,-1000,-1000,-350,1000,-635,299,718,-1000,-1000,-688,1000,-1000,935,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-249,-768,-876,925,1000,64,-496,-634,-1000,-723,588,-755,650,-1000,7,-355,-422,208,-484,363,-715,-898,213,1000,1000,1000,1000,539,-983,319,-216,-1000,546,-137,-1000,984,643,622,-1000,1000,1000,818,-977,-672,568,-1000,1000,59,-789,596,-1000,-644,-337,908,-610,-284,586,-1000,-967,405,1000,-968,935,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-317,-75,645,424,-528,-377,309,-1000,-740,459,990,1000,412,506,-1000,-491,13,-207,509,-536,512,663,-708,-525,-318,-1000,-1000,-268,-851,-83,236,241,43,166,-1000,-1000,-229,-150,-863,-202,-422,656,22,424,-101,854,-555,-513,-122,-226,-141,603,695,-393,1000,87,75,724,-1000,-1000,-785,1000,-80,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasText():boolean",
            new int[]{-467,177,-81,-578,-620,773,504,-430,-222,444,29,1000,-89,-252,-1000,-1000,525,-108,-746,285,-894,-781,-613,-757,-265,-127,-308,-384,-368,-161,-971,-762,-166,-632,500,1000,-281,649,-1000,-717,-52,515,-1000,-755,-539,-445,1000,-885,436,-150,495,894,-456,-357,997,-358,-1000,825,-604,-1000,-1000,-613,-746,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html():java.lang.String",
            new int[]{1000,336,1000,485,-903,1000,-496,920,-446,297,-595,-266,-1000,508,1000,-1000,873,598,103,1000,1000,627,-1000,-1000,-1000,749,1000,1000,-1000,1000,1000,683,-1000,1000,-1000,-1000,1000,75,1000,33,-1000,1000,-1000,-1000,1000,994,281,-1000,452,1000,-1000,386,-926,-1000,582,-1000,-335,135,1000,-420,-616,-743,-186,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.Appendable):java.lang.Appendable",
            new int[]{776,-39,696,386,-1000,811,674,-616,-851,-545,28,51,-174,-617,60,-175,-355,-763,495,635,148,59,652,-1000,918,809,108,943,-314,-354,-627,249,-741,870,208,-142,341,-347,1000,205,-472,-553,-21,447,379,307,-625,1000,389,616,499,516,-698,-580,-50,275,-127,-13,-570,879,869,651,1000,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-405,-921,-600,-336,681,363,348,518,350,1000,-40,-26,-20,185,-400,508,-382,-787,970,1000,-49,248,-631,-410,-288,262,-371,-522,-1000,-284,152,225,-72,508,-170,1000,79,-111,-157,-679,-515,337,-325,533,517,471,-217,-493,339,-657,-524,213,1000,-1000,-722,232,228,650,-504,398,1000,-776,-238,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "id():java.lang.String",
            new int[]{178,-81,1000,310,704,424,-66,124,1000,-1000,410,-1000,1000,396,69,-635,-1000,1000,1000,-1000,-863,1000,413,-996,-795,1000,594,898,-1000,306,-1000,141,-999,-1000,-1000,-509,1000,-306,1000,-224,-1000,-667,1000,315,-370,-108,103,-660,-1000,999,-872,-145,289,213,-330,-346,-843,-447,460,856,724,-639,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{1000,-618,1000,-1000,762,1000,524,43,-1000,-956,-1000,-811,1000,-250,1000,-914,-280,777,201,-1000,-945,-318,-1000,304,171,981,-819,787,1000,1000,-1000,1000,-638,277,1000,1000,-437,526,-1000,-289,1000,-1000,-1000,-1000,1000,1000,593,326,1000,191,83,-1000,635,295,448,-1000,1000,1000,1000,-380,-1000,-1000,-58,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-162,717,1000,81,263,281,794,49,-197,445,-734,509,-217,25,206,-144,444,-660,-1,-73,-1000,112,-176,-523,1000,595,269,1000,258,-400,-673,188,124,500,-119,-253,-493,188,-400,535,378,-1000,-1000,-1000,114,-332,-188,-350,-400,462,-522,-990,-379,1000,-960,-39,927,78,782,-514,-131,-958,1000,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-1000,411,1000,418,-178,-286,293,-155,268,-47,1000,393,-896,-796,-394,235,313,-375,76,324,-66,-526,277,-595,595,-75,459,1000,492,-1000,-1000,-59,702,988,-948,-749,33,516,-50,902,225,1000,-864,-436,-570,-365,303,230,-1000,1000,-678,859,-462,286,-531,1000,1000,-1000,670,-514,469,-523,1000,651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{-626,480,-695,-315,-187,-590,-687,-692,314,-1000,-387,-30,-451,1000,60,-502,1000,-860,-14,-525,-73,-434,613,469,-851,30,864,1000,295,1000,112,935,736,-1000,-841,612,-1000,467,476,-1000,-876,289,345,461,588,-324,620,-1000,1000,-46,31,-417,-611,-346,-185,1000,-730,1000,435,-980,305,-402,-1000,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{684,-78,1000,1000,867,768,848,-1000,1000,963,1000,-1000,-961,-1000,-1000,1000,-233,-454,166,988,445,1000,-1000,271,85,-223,-585,-165,-1000,-713,-147,-1000,1000,-580,-771,-1000,-1000,610,-1000,-1000,-1000,158,-1000,501,416,69,240,692,-1000,626,1000,687,813,-3,1000,-206,984,-846,-1000,1000,-664,975,866,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{-465,-18,-157,72,-744,-656,1000,-290,-648,-41,-400,385,297,-497,460,264,-221,676,283,-1000,753,-281,109,-1000,-1000,-1000,-913,-1000,-1000,-1000,322,-643,137,-1000,932,-387,1000,-1000,-1000,-145,-882,-98,49,-331,-1000,-136,-514,-579,910,676,835,-1000,239,-449,158,-1000,-81,934,752,974,-877,-190,-99,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-841,-534,364,619,428,-261,-167,1000,652,-9,1000,520,-552,-956,1000,-307,-178,76,-739,-971,668,517,-1000,498,565,1000,-824,724,-299,134,563,965,-398,606,1000,56,243,623,-466,-351,784,-1000,-194,1000,-52,52,917,500,-361,-988,1000,1000,-402,964,88,631,466,238,184,725,858,1000,-841,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{1000,-684,-53,-465,-1000,259,28,254,-226,-195,-451,540,-487,-100,-1000,173,285,-1000,456,-1000,-501,-489,1000,1000,-1000,-1000,188,953,597,846,-379,-557,1000,532,-108,67,-470,-459,1000,1000,1,1000,-209,-162,1000,477,306,-689,510,344,-1000,1000,-526,-461,344,-113,824,-287,604,-138,442,-751,327,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(org.jsoup.select.Evaluator):boolean",
            new int[]{381,807,-498,-805,313,188,1000,-1000,-1000,1000,706,-528,-385,1000,-1000,1000,-593,224,-1000,29,40,1000,1000,1000,-1000,385,-388,-24,890,-387,69,337,-891,835,-977,1000,-648,-1000,-1000,415,-1000,-1000,646,-55,-552,-1000,-147,-1000,-1000,-826,1000,-1000,599,80,-400,605,492,-227,-270,409,495,-1000,323,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "isBlock():boolean",
            new int[]{800,519,1000,-809,-1000,-736,-252,1000,-523,-520,-57,428,203,-1000,603,349,288,-471,-327,95,-689,1000,830,358,-790,-1000,-647,1000,1000,-988,-560,1000,755,-707,-762,-1000,308,396,1000,-1000,-787,-1000,-1000,-351,-1000,-1000,-1000,-98,1000,-1000,-113,-1000,591,-66,-1000,-356,-149,295,-1000,1000,-724,-593,765,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "lastElementSibling():org.jsoup.nodes.Element",
            new int[]{-416,-219,731,1000,-177,1000,-637,-152,-1000,833,-1000,-88,855,-583,-808,1000,1000,-436,-617,279,-1000,605,373,668,1000,479,-668,1000,517,1000,-488,-806,-569,611,664,733,779,-193,297,-728,1000,99,901,587,-825,-280,-164,-724,131,631,1000,529,1000,-515,-1000,-429,-20,-428,-912,1000,-652,-548,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nextElementSibling():org.jsoup.nodes.Element",
            new int[]{626,-819,-285,535,1000,-126,-66,163,-975,1000,959,-377,-514,1000,-617,-506,-1000,-928,-1000,-1000,-367,-1000,1000,-1000,432,-776,781,1000,-1000,143,-1000,-2,-647,-996,-1000,1000,-651,104,1000,-100,-100,1000,-338,352,-369,-1000,-1000,-1000,-409,340,-893,920,-1000,412,-1000,-1000,1000,-1000,-1000,854,-1000,-1000,759,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nodeName():java.lang.String",
            new int[]{166,252,-1000,1000,-1000,1000,154,-991,1000,740,98,59,-1000,-76,950,-1000,-167,766,-1000,-126,-403,1000,518,-130,-1000,838,-43,1000,1000,1000,1000,865,246,33,1000,-412,-650,-1000,-1000,1000,1000,231,858,156,-1000,-296,81,1000,-1000,-645,-1000,569,1000,379,-1000,-572,1000,993,-875,447,1000,49,-459,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "ownText():java.lang.String",
            new int[]{-431,843,-748,1000,381,469,613,-1000,552,1000,-284,1000,1000,611,242,919,29,-266,499,-1000,1000,799,-390,-1000,160,-1000,148,290,-295,-983,-193,-62,272,-585,-828,-1000,1000,1000,-1000,1000,-1000,-1000,143,-103,1000,311,23,861,-1000,712,-988,-316,239,-395,-554,42,1000,136,1000,486,-767,26,-769,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parent():org.jsoup.nodes.Element",
            new int[]{472,711,1000,-1000,-953,280,399,824,-1000,-689,419,698,1000,-818,-1000,-758,1000,-1000,-439,1000,-197,1000,-922,-109,-788,-137,1000,-900,1000,-965,208,-549,267,-494,317,-1000,1000,1000,660,553,465,60,1000,29,-733,-278,-826,1000,-1000,-183,-416,-995,-326,1000,-1000,-1000,1000,1000,-1000,-1000,724,-15,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parents():org.jsoup.select.Elements",
            new int[]{1000,252,134,-1000,-982,-127,-511,658,-1000,-500,-905,363,-316,800,-1000,69,-326,443,-1000,-164,-467,1000,200,-44,-1000,690,963,198,109,1000,664,1000,-1000,227,1000,-1000,-1000,-41,-293,417,-834,924,-180,832,1000,1000,627,-485,-284,346,430,-1000,-1000,-1000,685,1000,-1000,1000,931,-480,520,404,-630,550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{977,-645,-707,1000,158,-560,1000,-67,400,-154,1000,933,1000,-415,-264,-229,436,-376,1000,-944,-1000,-332,408,-1000,298,-114,1000,-65,-985,-126,-532,454,125,-95,-181,1000,874,-531,-941,1000,223,529,503,381,447,739,-24,616,431,653,-724,-305,-20,956,-29,-952,-552,-1000,-88,175,-686,-593,-1000,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-1000,-594,1000,1000,1000,-926,484,705,-321,182,-885,50,173,793,797,227,480,479,407,-703,-1000,778,1000,429,-1000,1000,-283,-812,1000,630,19,-88,219,732,1000,699,997,-220,1000,7,506,-384,829,772,-306,863,76,-70,4,-125,1000,22,770,-1000,-1000,-272,1000,-590,-352,832,1000,955,1000,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-440,-330,-44,763,97,-368,623,-677,-969,-572,-1000,-1000,-1000,186,185,533,1000,-644,-793,-201,-265,-631,918,2,-739,-763,1000,90,-258,386,-1000,-758,662,-1000,-44,-1000,1000,941,-1000,769,1000,1000,124,-954,-1000,-1000,1000,-329,83,267,-785,418,-628,1000,768,-125,229,-197,303,-580,849,500,1000,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{60,-234,-871,-788,-460,-395,-31,333,412,375,-976,-813,-777,726,142,-176,280,-496,-471,459,-94,-161,-617,757,-554,-614,-401,-356,324,-386,-805,-742,373,-634,-190,949,720,701,-542,-863,553,489,-444,266,-740,-834,188,-906,-375,611,-465,-879,40,178,-530,92,16,-388,406,-806,-322,-246,682,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-802,543,810,-837,1000,-146,-632,-722,1000,1000,-1000,-1000,-1000,5,266,-461,-246,567,-1000,1000,-1000,-59,-1000,-225,1000,-163,-321,1000,-965,424,1000,-962,-197,282,-1000,-906,-198,1000,-588,1000,-1000,899,-712,1000,1000,-1000,1000,577,1000,-1000,-1000,-149,1000,1000,-1000,-159,-667,-903,-939,267,1000,834,-626,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "previousElementSibling():org.jsoup.nodes.Element",
            new int[]{135,-684,-584,1000,527,-389,-691,-211,1000,1000,1000,605,1000,1000,-800,459,1000,658,1000,1000,-1000,718,1000,-351,-1000,-652,-895,-171,-274,1000,-120,-473,-218,1000,1000,1000,165,-738,926,-30,1000,1000,1000,52,-1000,824,894,1000,-856,414,-114,-1000,1000,764,1000,1000,269,418,-812,-807,-1000,-151,454,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "removeClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{946,-207,1000,888,139,-1000,-241,830,-821,-1000,889,1000,844,262,-1000,-1000,1000,816,525,-207,681,-963,-61,-1000,-1000,-336,161,-965,-697,-1000,1000,162,757,-570,1000,1000,444,-449,627,564,927,706,-528,-11,1000,-163,608,-311,-443,886,1000,-49,1000,1000,518,771,-250,-366,-591,1000,1000,-579,317,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{537,-393,-1000,308,-1000,94,-967,866,448,-698,-399,975,763,-520,179,-358,296,1000,1000,-1000,-970,846,972,933,-56,-243,578,-1000,-756,-339,-244,-750,199,1000,-1000,379,524,1000,-669,246,1000,558,1000,-914,445,634,525,469,-772,1000,-39,415,920,6,-1000,-213,916,697,1000,-422,445,-1000,1000,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{859,-531,493,-489,888,-305,-467,555,-1000,-1000,1000,-402,-219,-428,25,512,-344,141,-825,-258,-337,-270,462,620,-463,-897,565,15,-323,674,56,-220,-1000,1000,126,-1000,1000,35,-1000,601,415,426,355,412,-1000,-852,-1000,-300,-1000,239,-3,130,826,-1000,-8,-514,1000,1000,1000,-646,-536,-991,-840,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-708,-890,-601,42,1000,-256,627,-578,536,341,-899,-594,358,29,1000,-945,-674,-1000,-6,1000,-640,-919,-145,1000,-779,976,-892,-38,914,918,889,339,-364,379,203,388,111,-134,1000,-106,791,-19,472,-153,271,-1000,498,-793,-1000,54,-949,-116,1000,881,292,1000,-733,696,-421,-865,-269,-410,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,-180,40,-1000,-1000,111,529,-1000,651,1000,1000,-1000,23,-107,-1000,-1000,-95,-446,-261,734,-434,-1000,975,1000,-889,-220,-266,535,-183,-1000,-617,1000,727,-1000,-1000,-784,-1000,-1000,482,-954,-662,1000,-617,1000,616,1000,330,1000,14,1000,1000,-367,595,-1000,-931,-641,-584,-1000,247,962,183,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "siblingElements():org.jsoup.select.Elements",
            new int[]{1000,-648,789,-1000,23,1000,999,1000,734,-1000,1000,-391,-830,5,-1000,-661,1000,1000,250,-465,312,-572,723,-181,912,-312,520,-189,282,-193,-301,413,-901,-376,733,-741,691,561,179,-1000,117,683,-224,963,-236,1000,162,-1000,-904,1000,691,13,1000,-450,-775,-600,-750,-1000,-429,711,865,50,522,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.parser.Tag", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tag():org.jsoup.parser.Tag",
            new int[]{951,99,174,-84,-789,986,294,-271,-923,674,-795,-144,-89,510,-382,-432,-841,-302,108,-737,-912,675,-101,311,163,156,-950,241,-54,67,743,-309,-650,-62,154,910,-891,121,-839,449,394,-374,12,-890,651,-647,-2,265,953,993,-826,-837,399,17,201,-973,457,-218,-503,389,-484,-971,86,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.String:WW9aTG1fX2k0NDNfLl9fNQ==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName():java.lang.String",
            new int[]{594,-102,-1,-29,-1000,591,-295,876,842,-805,-49,-1000,-1000,799,4,-280,-352,-1000,-7,-1000,-1000,-279,-872,-795,650,154,-1000,-347,-918,-752,-597,719,-1000,-629,-155,607,702,826,-1000,-1000,286,691,-234,384,-1000,842,950,-80,371,-700,1000,-820,-900,-169,-1000,1000,-720,1000,-214,919,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,-111,-1000,503,-958,-1000,-861,-516,-86,1000,1000,-1000,445,-305,-979,-951,-1000,-472,-1000,354,-1000,1000,325,-1000,1000,-1000,1000,-842,1000,-234,-870,-830,844,1000,85,-636,-1000,-812,564,-1000,-825,-668,839,-75,363,-1000,-918,1000,1000,-5,-942,-1000,-519,1000,-1000,1000,1000,814,733,673,144,1000,-423,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text():java.lang.String",
            new int[]{696,69,-518,-5,27,148,-765,-1000,-465,-1000,992,-436,-467,487,-911,398,1000,-694,2,38,1000,-994,173,-257,169,-1000,144,832,327,185,358,-683,908,-1000,667,-801,-860,-400,549,-687,582,1000,-1000,-925,105,-89,932,-994,-1000,-405,-296,-93,81,-68,769,-849,-186,80,-756,337,1000,29,-799,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-961,594,331,546,-9,-590,-341,388,398,65,377,49,594,-438,-962,515,-1000,-458,261,-691,102,265,487,-227,-728,489,-443,897,774,375,947,-958,-105,-694,-388,577,329,-526,-853,-930,-281,991,-765,448,-448,-682,202,325,488,886,-88,-452,643,-69,-799,954,351,742,-408,312,489,842,-898,-544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "textNodes():java.util.List",
            new int[]{-1000,378,-73,-1000,45,194,-186,900,1000,-505,318,-519,625,59,495,1000,73,793,841,-1000,-572,23,-580,860,-322,408,-116,-25,-1000,-1000,668,-478,403,-1000,-692,1000,-567,406,-677,151,-324,427,1000,308,1000,-476,-113,-211,468,-952,343,991,-962,-806,476,115,-230,393,-1000,629,1000,71,460,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.String:PC0xNyBjbGFzcz0iKzg1NSI+PC8tMTc+", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toString():java.lang.String",
            new int[]{473,135,-925,-17,-529,-622,84,-572,-451,1000,-1000,84,855,-250,-272,450,775,-325,-383,-1000,893,-225,-458,968,420,675,1000,609,1000,-830,-318,473,160,-792,744,-725,386,-436,-205,-681,704,-1000,-535,-721,-184,905,449,659,-358,-384,1000,-581,-61,902,-362,794,874,452,39,-655,615,-450,-998,550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toggleClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,465,621,1000,-137,194,546,-1000,570,-934,169,-317,-847,912,-209,-193,1000,369,1000,289,233,-910,-502,-1000,-1000,-831,828,-865,374,-938,-1000,764,249,1000,583,-1000,241,-1000,1000,-284,-106,-1000,241,-150,-1000,-438,419,1000,-1000,-43,-1000,-256,975,1000,-467,-125,1000,-1000,-134,359,1000,-828,1000,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toggleClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{530,465,621,-767,-197,-152,621,-328,-1000,-447,169,-1000,462,-1000,306,241,-675,616,-1000,909,1000,-910,-821,13,-1000,1000,910,-954,174,-62,672,-316,56,-1000,-1000,1000,1000,1000,-1000,-280,1000,-1000,-688,91,1000,-438,260,-1000,337,-1000,1000,-1000,-1000,1000,-958,-481,-937,419,934,894,-249,600,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val():java.lang.String",
            new int[]{-512,228,371,73,-346,-256,-702,-445,-492,-1000,-131,-1000,-148,-136,-381,712,955,1000,-593,17,1000,1000,-405,-736,17,-169,648,-497,1000,180,1000,627,291,-51,-1000,-154,-831,274,1000,717,1000,972,525,48,1000,481,703,-960,-144,-458,352,-535,-1000,422,-394,-104,-113,398,-84,-815,1000,-557,-474,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val(java.lang.String):org.jsoup.nodes.Element",
            new int[]{361,-60,364,-642,-539,-609,-402,160,-523,-791,-212,580,-186,-169,889,546,28,-404,344,998,508,24,414,-169,-120,-178,137,-116,58,-9,662,329,896,475,823,-781,114,-128,-481,-721,338,-68,702,-459,-77,-223,-391,-108,-466,-902,-770,210,404,935,-38,398,626,-930,892,343,899,546,211,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{537,-966,-1000,-307,-308,598,559,149,858,889,-572,-649,-597,1000,-470,-235,-469,-665,-59,289,-1000,727,776,-1000,525,248,223,1000,1000,447,-1000,-646,1000,-436,-281,112,232,-546,160,823,-154,-1000,-179,448,-620,986,-324,-327,867,1000,616,-35,91,558,-663,-94,293,14,-481,447,969,-209,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.String:SW5maW5pdHk=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-341,-665,529,-561,-50,-870,-454,-916,787,764,-707,-219,-187,392,-212,947,53,-426,-695,-273,-967,-889,212,239,736,-369,-955,482,-860,2,57,371,-597,-682,-353,-941,146,-592,206,-64,-233,620,180,-456,-797,-54,444,-15,-845,-481,-579,-627,-559,908,844,-640,-175,746,507,834,-534,-239,-86,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-574,-449,272,723,383,-102,-834,434,-852,480,-594,-398,-462,698,-556,866,-728,673,550,378,-103,647,794,-128,-722,-28,-193,-730,979,-586,565,-866,-914,654,-298,713,-191,146,660,-121,551,738,365,-214,-985,-124,69,-230,-570,-405,467,686,-24,-997,312,-346,-893,517,330,15,600,718,462,-805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4ODAwMDAwMDAwMDAwMDA=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-280,-521,-71,573,746,-802,-690,463,41,-182,324,469,-787,-384,982,-705,-706,753,-182,-467,899,995,-547,-25,-277,921,592,347,-524,-856,616,-638,-878,50,-769,-296,-421,554,437,-637,946,473,-458,-400,741,-760,-126,-259,626,-89,396,4,-297,-345,535,-423,536,277,159,310,-215,697,904,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{-943,-87,-859,-740,0,289,100,-622,788,555,-593,894,-743,-335,-914,413,-359,268,432,134,-923,-484,-955,-895,377,85,129,-108,90,-166,242,772,-935,-142,806,91,-766,881,-865,-284,-774,889,451,680,510,734,-544,39,-726,978,349,-261,-996,746,-94,-150,809,-497,-801,-89,960,-230,-141,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{887,-682,925,954,518,-148,41,526,634,521,80,354,854,-656,-164,-837,338,-241,-345,986,113,598,615,929,-778,-619,-6,551,-947,-708,708,86,-20,-308,747,22,-127,-368,-464,938,937,-545,-375,-83,-145,-719,153,306,289,945,-891,-790,381,-213,589,-353,-865,566,952,8,270,-234,82,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{930,97,330,301,-202,-908,937,673,-377,-717,279,871,612,-555,550,-621,-978,980,-666,-45,-172,819,-4,-174,-824,296,-587,153,260,451,596,393,-584,49,812,166,678,-834,180,-23,841,647,-598,-294,-871,-60,274,-809,-5,864,958,403,281,725,-441,-276,997,856,553,-316,-697,125,793,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{106,893,-257,-551,-194,606,1000,-455,242,-1000,143,172,-401,-347,169,1000,-1000,-1000,32,-646,-1000,-1000,-385,-21,502,1000,1000,686,1000,-1000,469,-1000,519,-103,69,711,1000,820,205,735,867,294,276,-1000,-761,368,691,-103,-58,527,1000,-376,519,-1000,907,162,-55,288,26,276,-558,-623,-136,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "", "createShell(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-357,360,-791,254,-641,-465,641,-653,888,15,-415,-750,967,998,-99,434,794,630,507,-959,-813,-751,359,-345,285,-247,960,-963,820,766,965,223,-952,-650,743,-980,-481,751,190,181,-927,888,503,314,-350,-885,172,918,817,164,-994,-135,-713,-877,-330,-909,108,-202,-103,326,699,864,318,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{581,682,-472,550,-375,917,-46,710,-739,514,596,708,521,-309,729,6,591,-620,63,-245,205,39,989,749,28,-630,383,-801,-59,925,-700,26,-684,428,949,280,805,177,714,83,950,384,644,-961,23,-527,860,461,885,-572,-386,-54,377,-851,364,855,781,395,147,-943,-774,-576,797,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{1000,1000,-1000,-585,1000,453,-151,1000,1000,186,1000,-1000,-1000,376,-494,-1000,-54,145,-78,587,-1000,-598,-1000,-658,-1000,-1000,310,-1000,34,747,1000,613,-1000,1000,495,-977,890,1000,1000,795,1000,433,1000,1000,1000,1000,-372,445,-1000,-784,190,-167,-801,-1000,-551,-1000,459,-177,-1000,-874,883,306,821,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{738,-933,-180,-932,-25,318,-582,-105,854,-795,-444,-874,732,-657,-241,-339,739,-994,-751,885,372,976,-839,572,-567,-614,735,-653,-56,-98,359,801,251,849,396,-600,663,238,-576,451,653,-989,721,-453,-111,304,-401,-833,-797,39,567,-618,966,-273,182,5,-726,7,934,937,865,-825,-466,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,-555,-1000,-107,373,1000,-257,-445,1000,-880,-816,1000,-609,-1000,399,1000,-826,480,426,-384,1000,-480,-50,-180,-828,-125,-576,66,-1000,-24,1000,-83,1000,-232,-1000,711,298,-1000,-445,-945,627,1000,413,653,-1000,1000,203,865,-1000,-65,397,-103,111,-1000,-1000,-314,480,-543,329,-587,476,121,-554,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "title(java.lang.String):void",
            new int[]{-444,1000,-1000,1000,906,185,-111,367,906,1000,-665,529,1000,-193,1000,502,70,-559,1,-1000,1000,1000,1000,-681,-330,-1000,-170,1000,1000,-48,1000,875,-700,-1000,-865,210,-1000,-1000,978,879,1000,-1000,-932,1000,1000,-1000,1000,389,648,890,-664,-200,-683,248,-1000,207,145,386,-1000,90,-1000,-988,-1000,-1000}));
    }
}
