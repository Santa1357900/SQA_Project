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
            new int[]{613,-216,-315,25,1000,801,374,-432,-571,-1000,588,488,39,-653,-463,418,-119,-1000,242,-303,-947,1000,-168,708,-251,-61,1000,1000,438,380,-211,-813,1000,-1000,733,-328,-173,-643,-981,235,162,199,1000,-649,-1000,-1000,-975,554,-1000,-916,213,938,446,-56,-1000,1000,860,-520,-653,404,-502,746,835,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,-63,-1000,-1000,1000,1000,549,814,798,-1000,-105,71,1000,676,-138,-545,-501,1000,-454,-850,876,-482,-601,-644,-154,683,-1000,220,1000,-1000,-438,-796,327,-552,1000,701,1000,-911,1000,-941,1000,356,-412,-12,1000,989,783,-1000,-601,566,-125,546,1000,1000,230,149,259,-705,-450,114,-989,692,-1000,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{141,-921,-131,-1000,1000,-179,395,-284,331,1000,-89,805,-198,238,530,-1000,-7,647,137,98,1000,-495,-16,957,-374,-691,513,-291,119,-952,-936,-69,-580,-147,1000,-524,1000,-6,1000,-183,-551,-116,1000,951,-566,67,-178,465,-115,616,-882,1000,1000,-367,-298,-139,749,-258,-749,-175,823,46,-450,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,687,-820,381,-805,-764,799,647,270,797,-424,1000,309,-1000,-328,587,-938,-452,599,-360,-117,1000,-506,-487,893,-339,-494,543,-295,582,77,-1000,480,-1000,897,-521,-1000,-414,-135,-1000,-444,1000,-1000,-574,-489,447,-982,896,-41,72,240,640,408,-1000,-690,885,-42,-742,1000,169,1000,265,-469,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-800,-222,-827,11,497,1000,38,63,60,-427,-66,774,1000,198,-901,-89,210,-241,-918,30,877,-1000,-1000,450,1000,-292,112,-793,-582,128,-249,-134,923,452,-941,622,-751,699,11,669,-943,0,471,967,-1000,-148,-467,457,724,364,-292,-163,-526,-631,43,-139,221,-153,1000,965,1000,-514,840,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-680,603,1000,-606,522,-114,74,-322,177,39,552,223,444,714,-583,736,634,1000,-16,672,-158,-1000,-165,-407,-890,20,889,1000,455,400,1000,-147,860,-139,-411,820,-455,-204,-565,-826,500,-1000,1000,800,-62,-809,-675,-400,-888,-193,874,-562,-1000,3,341,1000,-411,155,657,947,268,394,898,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{551,177,1000,-479,1000,-683,643,-777,820,-926,-637,400,125,-1000,556,1000,-954,-689,330,206,-105,1000,-1000,925,-1000,-239,697,337,-1000,1000,-1000,1000,1000,416,-815,-1000,1000,-1000,-15,-252,402,1000,1000,-858,-81,1000,-408,878,-317,560,-336,241,1000,898,1000,527,-1000,-882,1000,-246,-321,-567,42,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-200,-252,707,-1000,1000,219,-316,-1000,1000,482,633,1000,-121,-275,-143,-480,-428,-412,1000,418,-497,1000,-382,1000,-451,-687,578,1000,-613,317,-1000,733,1000,-1000,-1000,1000,-173,-426,-330,-726,390,228,680,259,-883,136,1000,1000,1000,384,-1000,-838,196,1000,1000,-475,-460,162,874,-1000,-536,1000,104,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendTo(org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{1000,15,-827,-697,-602,-1000,-26,-706,767,72,1000,-1000,1000,-687,-635,51,254,216,328,-292,-178,-327,-541,554,-458,-999,-200,-1000,-421,487,159,670,1000,1000,-461,-285,1000,-1000,1000,1000,1000,-691,620,-1000,1000,186,1000,349,992,605,-571,-757,763,-1000,-376,460,-311,908,890,377,1000,-1000,633,-945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{332,-93,-695,-1000,566,-161,809,-122,-1000,-97,223,715,610,-1000,396,75,-870,421,-1000,-39,-400,-486,1000,-397,-134,-156,-1000,-317,-520,-1000,1000,-34,-283,1000,-1000,-961,-253,-233,-1000,69,-10,348,490,-702,10,384,1000,-1000,-810,-1000,-159,688,627,322,-1000,-1000,-1000,462,-769,-1000,1000,-59,744,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{1000,315,-1000,-780,1000,1000,-606,865,821,-12,80,1000,1000,-50,-146,8,962,1000,37,450,-762,-729,450,356,-78,606,-674,-255,-761,173,785,1000,-1000,-171,-890,-1000,220,885,-481,676,-1000,-561,606,-747,180,118,648,-505,851,-1000,-1000,686,827,1000,-456,217,34,306,-996,-215,619,702,1000,-472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{190,705,501,-123,814,-1000,-351,652,1000,838,617,-376,398,417,65,289,-226,-179,467,123,-1000,1000,-343,-628,-977,-510,359,-198,-1000,779,1000,-899,-883,-422,385,557,691,-150,786,612,-1000,-527,-129,-1000,12,-1000,-1000,534,589,-121,581,342,30,633,-739,824,683,-1000,316,-123,-817,843,-76,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attributes():org.jsoup.nodes.Attributes",
            new int[]{-573,675,316,-63,1000,-584,-472,159,204,-1000,186,-1000,648,-1000,498,-398,-282,1000,616,-288,997,647,-1000,534,744,-55,-482,-714,-150,-968,146,353,-789,819,-367,-1000,434,-1000,-470,1000,-125,-320,1000,360,-869,-506,641,1000,-787,-1000,-969,880,1000,-64,529,881,1000,-545,-1000,-4,727,-1000,-82,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:LS05MC4w", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "baseUri():java.lang.String",
            new int[]{1000,342,303,947,-144,-267,-272,-860,444,48,-445,-493,340,-281,473,909,-155,-90,-97,1000,-147,225,-933,-1000,1000,-1000,-591,930,316,243,1000,-1000,802,-1000,1000,339,-1000,147,-175,1000,-660,-657,21,-939,-327,-879,906,-931,-490,-152,-484,-211,-755,1000,1000,-1000,1000,1000,-623,-386,-295,-520,-842,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-547,6,-994,-1000,85,-349,743,-996,1000,572,40,380,1000,134,935,902,-469,503,167,-242,-678,-689,428,1000,-896,-354,70,-115,564,-247,-1000,1000,50,164,-330,-1000,231,149,792,-122,-1000,-932,-213,-262,-314,653,969,753,913,-1000,-1000,323,-105,-318,160,-1000,-649,1000,259,-430,1000,-1000,191,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{1000,6,14,931,904,749,-1000,-115,-140,1000,44,1000,-216,-669,-1000,-1000,414,158,323,-41,0,922,-960,628,695,-761,-478,599,181,909,919,622,-1000,227,-653,383,-687,690,-615,736,880,400,433,-26,0,892,199,-387,-567,989,229,-439,95,96,-172,636,-974,-1000,317,-211,-299,-225,912,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-974,666,499,386,1000,284,225,1000,-282,1000,44,-189,-602,-669,1000,-1000,154,683,-107,-94,0,-1000,325,1000,1000,1000,347,1000,785,159,1000,0,1000,1000,841,-726,83,1000,337,1000,-1000,-448,-607,604,0,321,819,-959,0,140,-202,-483,1000,1000,184,-1000,-178,562,1000,0,261,-766,-1000,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "child(int):org.jsoup.nodes.Element",
            new int[]{-942,279,-794,-758,301,807,684,369,154,-266,-336,-940,-270,-371,-584,902,-6,-519,1000,-352,129,-1000,949,266,307,-1000,-10,-352,827,1000,792,-137,1000,-180,1000,370,-1000,-628,1000,872,-871,645,1000,978,-586,-661,-107,-1000,-416,-466,474,-1000,-157,1000,1000,273,-489,-1000,20,-1000,-1000,590,229,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "childNodeSize():int",
            new int[]{-1000,201,-675,61,400,-262,-443,-365,-1000,-456,-632,-1000,95,1000,-1000,1000,1000,-859,-284,789,883,1000,1000,-197,1000,-692,1000,-1000,1000,1000,694,-159,1000,1000,-1000,150,-977,475,906,286,398,817,561,-1000,-423,-61,-786,625,1000,-967,-278,-145,1000,-814,-325,-643,-1000,-33,-835,1000,1000,-849,108,911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "children():org.jsoup.select.Elements",
            new int[]{-1000,693,888,91,176,427,-422,458,-692,-465,415,-644,-156,-964,-1000,-65,-239,-300,1000,572,-1000,-1000,-888,700,-1000,-259,-873,1000,292,-969,-7,966,549,316,-506,-993,-1000,-490,-134,794,1000,80,-1000,-889,649,-679,909,-362,482,492,1000,1000,825,373,552,79,367,-479,879,298,-623,365,-444,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwIDB4OA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "className():java.lang.String",
            new int[]{-229,-771,1000,5,877,181,-102,-577,-958,-1000,1000,-1000,1000,313,-1000,-197,840,1000,1000,1000,1000,-459,-381,1000,-1000,-520,-619,-676,-26,1000,-804,-1000,185,-1000,-471,502,1000,998,1000,-1000,1000,1000,-112,387,-5,-1000,593,-1000,609,-623,1000,-492,1000,-1000,-39,223,34,-1000,1000,1000,47,-1000,-816,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:LS00NDc=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "className():java.lang.String",
            new int[]{-1000,-948,695,-1000,1000,9,1000,212,-447,927,453,-1000,940,-1000,821,-656,1000,-1000,-379,1000,-406,-691,-1000,333,61,-268,-782,-261,-804,-1000,-112,724,1000,-53,1000,1000,410,1000,1000,77,881,159,-1000,-503,-1000,817,172,6,-1000,-1000,97,709,-599,444,-1000,-814,77,-471,-286,788,-908,59,952,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashSet", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames():java.util.Set",
            new int[]{-205,477,613,-330,827,517,-386,503,1000,-420,873,878,-70,-477,-546,855,9,910,264,571,848,-1000,-230,1000,-353,-729,-2,-113,-993,9,68,476,-1000,278,164,-259,670,543,113,773,-575,-9,-224,194,-299,-144,-710,282,-134,649,739,840,339,70,-602,574,959,-270,369,-260,-456,301,468,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{-204,459,-690,79,-355,-628,609,-95,400,1000,-885,195,31,-559,-777,722,-327,1000,568,-1000,190,-465,1000,1000,860,428,1000,982,308,-832,-134,679,-548,-436,-340,-400,504,-401,400,1000,647,-458,-6,-173,-457,-563,-887,951,-401,475,912,-704,443,-413,239,-169,366,358,826,480,-276,140,-63,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "clone():org.jsoup.nodes.Element",
            new int[]{-49,423,-1000,-589,522,-31,-891,-417,-325,1000,1000,-767,565,-899,456,-473,1000,-318,-387,-475,-422,-251,-111,561,207,488,-491,601,-1000,-971,-579,-827,-693,-588,-1000,-753,-1000,-224,-149,-893,-893,-562,171,620,1000,-651,677,171,-247,1000,-1000,887,240,-389,421,1000,561,-269,-166,1000,1000,-377,529,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "clone():org.jsoup.nodes.Element",
            new int[]{-395,405,-471,-104,1000,-1000,-977,64,-957,-1000,1000,-1000,-1000,-1000,1000,-1000,78,1000,-1000,-968,-262,-1000,1000,-736,363,-995,612,1000,-272,-426,478,-552,-383,1000,798,-1000,-96,288,1000,1000,91,-4,374,1000,-1000,1000,-4,1000,-446,-159,-368,-1000,-697,32,-472,-1000,1000,-1000,-1000,1000,1000,1000,1000,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:NS4rMHg4MDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{-180,36,-83,1000,1000,-874,-279,-422,494,-1000,1000,269,-1000,-477,-1000,-730,225,1000,1000,-1000,-315,394,915,1000,-1000,1000,1000,1000,686,-27,504,-463,317,1000,112,-1000,348,-255,487,1000,-310,1000,-338,1000,-1000,1000,363,-809,468,30,819,-330,-852,-1000,137,-1000,146,1000,-1000,1000,753,11,-327,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDA=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{1000,807,820,-1000,-491,794,-1000,814,-342,104,-1000,-64,237,1000,1000,238,25,-47,809,1000,468,-1000,-249,-266,697,333,115,-557,960,-1000,-108,-861,206,-1000,-227,17,1000,1000,-481,-557,-1000,-303,1000,1000,1000,-1000,438,440,599,157,317,81,-167,1000,671,1000,-1000,-1000,1000,1000,-1000,314,32,54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "data():java.lang.String",
            new int[]{1000,105,-57,-820,-1000,-196,-912,-136,-1000,851,-1000,199,604,-1000,1000,291,-761,-1000,466,-882,-344,-1000,485,400,322,-192,1000,-848,98,749,-720,-630,743,989,-1000,-785,-1000,-117,617,93,-831,728,379,1000,244,1000,397,-874,-536,1000,1000,149,438,344,183,516,1000,922,-806,-1000,337,876,132,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataNodes():java.util.List",
            new int[]{-563,588,511,-685,961,876,-1000,368,654,810,1000,-1000,505,-659,-999,-1000,1000,-507,-1000,530,11,-1000,419,-990,1000,-197,874,1000,-929,898,-253,-1000,572,1000,-305,168,809,-859,-110,1000,-197,112,-385,-1000,-859,-1000,1000,-1000,-1000,-19,-322,-322,-1000,-741,1000,-336,838,261,-460,-401,-392,1000,-1000,135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$Dataset", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataset():java.util.Map",
            new int[]{863,576,-231,1000,1000,406,469,46,634,802,-723,-1000,650,477,1000,-896,-1000,113,406,-1000,-1000,-251,1000,-183,-426,769,-1000,-437,1000,1000,1000,1000,-239,7,-915,-433,-348,-59,-76,128,-236,-1000,365,1000,151,937,151,982,314,316,571,-118,-1000,498,1000,300,-180,-634,851,-26,-438,-775,-245,-683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "elementSiblingIndex():int",
            new int[]{1000,-84,-1000,-1000,373,-319,994,568,-343,-1000,-1000,-443,-65,-1000,1000,100,1000,369,-1000,1000,-581,-570,-21,851,-573,464,571,998,-1000,-680,912,592,-719,-103,-1000,1000,-259,1000,1000,1000,553,1000,439,-1000,1000,-274,1000,-1000,1000,352,708,-501,-1000,1000,-315,1000,1000,1000,546,935,453,-462,-62,-678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "empty():org.jsoup.nodes.Element",
            new int[]{1000,501,91,-320,1000,518,-831,243,-1000,-792,990,577,1000,-1000,589,-582,-519,-906,-955,-382,593,861,393,805,521,469,-810,-137,-487,-524,-595,-1000,-326,484,1000,1000,-5,-1000,-683,558,-1000,1000,884,386,-308,-1000,471,781,-402,-212,699,679,-440,1000,-1000,-1000,-486,88,-348,-521,1000,-1000,78,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "firstElementSibling():org.jsoup.nodes.Element",
            new int[]{870,348,-396,1000,824,3,58,1000,-136,629,-786,-437,136,443,165,-161,1000,728,-605,1000,558,-36,636,-35,-767,-880,760,-1000,-1000,344,-1000,1000,-1000,-179,94,-166,507,1000,-478,369,-66,-178,-396,-153,-735,538,-1000,934,-174,498,487,-1000,542,177,-530,207,-400,1000,-1000,-1000,967,419,-124,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "firstElementSibling():org.jsoup.nodes.Element",
            new int[]{730,-327,1000,-742,-1000,-1000,-676,620,-825,270,-922,693,838,-957,161,-682,-1000,231,633,-1000,684,-431,-370,1000,-215,-314,-512,-1000,907,929,1000,-164,255,960,727,531,154,60,-1000,-1000,751,1000,423,-1000,1000,-26,530,-247,-905,-1000,-768,-144,-206,284,1000,1000,1000,-66,-592,199,-856,1000,-351,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getAllElements():org.jsoup.select.Elements",
            new int[]{-839,-105,-373,1000,-1000,113,954,299,858,-843,-410,210,849,-1000,-503,-422,214,-389,-1000,906,-967,188,-538,-592,-689,-1000,-134,-41,424,-173,1000,-875,-715,-925,-879,759,212,95,-1000,-954,256,-845,246,-347,-1000,-1000,384,-759,-956,43,-812,209,1000,44,20,771,703,-534,-806,43,659,1000,-796,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementById(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,870,-882,1000,1000,596,-1000,-1000,263,98,1000,1000,-1000,1000,-1000,49,-264,1000,-1000,-948,1000,-70,-1000,-1000,941,927,141,-83,603,902,517,1000,828,1000,-1000,-700,531,-766,-713,734,696,789,-962,1000,33,-1000,-1000,-830,-1000,-1000,-423,-1000,-700,809,758,-1000,-1000,1000,1000,-846,-1000,1000,-354,-745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementById(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,210,-353,149,-314,-539,985,1000,551,1000,631,-663,-1000,-374,-277,-870,1000,-712,270,-24,-248,238,997,-861,-470,1000,267,-307,1000,884,20,-1000,1000,834,901,1000,-609,648,134,1000,541,-29,1000,1000,966,1000,1000,453,-1000,1000,311,-542,710,-235,-977,-173,1000,-715,330,709,1000,564,440,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttribute(java.lang.String):org.jsoup.select.Elements",
            new int[]{451,906,1000,-713,100,587,-202,308,-233,-1000,1000,-740,-171,-6,-319,-1000,419,-334,-1000,-317,-901,-518,-585,-248,437,-598,-377,-424,-152,-692,480,-697,1000,-488,-1000,-1000,-1000,246,344,-547,174,-610,228,-718,-206,339,1000,828,-769,217,823,1000,-499,-245,792,469,1000,1000,89,-203,-264,-807,-640,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeStarting(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,480,-1000,-269,-392,1000,788,139,628,376,384,-297,-683,-526,-290,-823,-1000,-589,-873,-109,314,-514,238,1000,-1000,-1000,-893,1000,-289,1000,284,1000,1000,173,-690,533,735,-889,-1000,-216,134,-1000,-546,443,284,476,1000,-428,259,161,16,603,629,-107,-622,-897,293,-185,543,-1000,239,125,107,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValue(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{941,-96,31,221,-689,-884,-1000,639,-87,94,1000,46,-1000,654,-452,592,-162,619,445,-130,726,-1000,104,958,-850,-1000,282,400,453,415,-22,-449,157,-150,-4,-391,90,-316,152,871,-526,-59,569,-36,956,-840,-246,-272,892,472,430,72,-471,-913,-820,-1000,661,-786,-867,163,224,870,291,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-32,-405,1000,-846,80,1000,-347,728,825,-21,20,1000,-1000,-1000,399,1000,1000,914,850,-1000,-1000,-864,1000,-1000,1000,1000,-427,-1000,229,-1000,-1000,-1000,-400,-1000,400,75,745,360,1000,1000,-1000,-1000,-1000,-1000,-1000,-1000,-9,1000,1000,-537,1000,-1000,1000,304,-19,1000,-702,-929,393,-1000,969,1000,903,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{876,990,-685,1000,141,-928,-97,-71,-207,-1000,1000,-1000,243,1000,-921,-1000,-394,-603,-1000,1000,1000,486,-1000,109,-1000,-7,1000,1000,-1000,1000,1000,-1000,768,107,-1000,334,-1000,-283,646,-924,1000,989,1000,20,1000,1000,844,-1000,-1000,-140,-1000,1000,-1000,822,-570,-11,1000,-452,-197,-644,-289,-166,-349,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueEnding(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,465,1000,209,-933,754,-831,924,227,1000,-138,343,964,-445,401,585,-88,539,539,1000,-1000,-1000,-499,161,699,1000,1000,-119,16,-877,1000,-421,-1000,1000,-296,-1000,160,127,1000,120,-379,675,579,1000,103,770,-465,-1000,56,-580,-139,-376,887,1000,-45,-347,-116,87,-598,-702,-699,-330,610,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-646,-792,-1000,-825,1000,445,-556,-909,-456,-930,-53,-1000,1000,-542,97,609,822,-1000,884,690,715,612,-1000,549,-1000,-40,-1000,-1000,894,-29,-1000,-1000,450,-171,1000,-1000,-1000,-105,-884,-1000,636,195,-1000,1000,-1000,-1000,1000,1000,105,-1000,1000,-733,908,-934,1000,-1000,-487,-1000,1000,-434,344,1000,-595,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{494,-810,-908,99,228,-151,173,-394,-1000,1000,261,-465,902,-970,-521,911,-766,163,941,-1,649,94,-269,-112,-1000,14,-344,-1000,669,-127,-603,-938,517,327,306,-70,-1000,68,-732,-1000,-200,551,-1000,153,-954,-510,-924,-787,1000,-1000,558,-35,-574,-620,-41,-1000,-504,-416,547,-810,1000,762,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{196,-531,841,-819,-297,-78,773,-227,533,527,182,502,-606,-127,169,1000,-290,-966,1000,923,795,639,-237,581,-677,833,1000,-19,888,-73,-1000,694,-317,-321,-915,-1000,-37,8,-398,497,-643,231,154,-1000,-499,-839,-7,-435,-1000,602,536,697,-1000,696,1000,-373,338,1000,539,-668,-683,581,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueNot(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{905,-279,1000,-676,129,-628,413,-302,606,220,796,1000,-6,428,1000,1000,722,-290,1000,237,-1000,367,-253,391,875,387,-664,313,220,-1000,-1000,809,660,-786,388,1000,647,-108,-1000,1000,-1000,1000,-953,494,837,1000,314,757,-289,1000,-794,-1000,900,73,33,-250,-1000,-38,1000,-1000,-956,928,1000,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueStarting(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{84,-231,424,1000,1000,458,814,34,-1000,1000,990,-1000,-22,-1000,-24,-1000,574,-520,275,426,-828,580,433,950,1000,8,882,79,844,405,943,940,-702,1000,-473,-1000,296,1000,-709,-469,-105,648,846,-694,-263,-1000,116,958,-204,1000,154,759,-282,1000,351,-932,-1000,-215,386,297,-458,296,-913,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{677,180,1000,-500,1000,1000,459,953,301,-1000,551,319,1000,1000,-975,-819,1000,-457,-46,-48,-1000,-1000,-678,-356,-535,903,-1000,1000,1000,1000,401,570,-1000,950,-822,616,-1000,-252,-397,1000,549,-1000,-1000,154,-27,584,1000,1000,1000,-554,598,682,-1000,-1000,749,-194,900,457,-1000,1000,804,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-232,60,1000,-233,307,409,-109,140,-726,278,-471,-327,298,749,-625,149,491,-790,-868,-243,-253,127,-553,-666,-164,-66,-473,416,695,343,-440,-509,-21,228,219,246,611,-426,-62,-144,-170,-100,-860,355,-424,-703,-530,738,247,-548,584,-123,157,-1000,558,-100,426,-57,-617,472,1000,560,231,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-496,54,-345,-500,807,-312,-782,953,301,409,72,-960,578,-496,-28,-819,-413,-66,-329,-48,-650,86,110,158,-535,-713,-431,-554,913,175,822,-127,-296,-236,-527,4,632,-926,-397,-426,-944,615,-403,-480,-974,-373,-375,-717,-710,-954,598,682,788,-593,659,-632,47,-432,-766,888,619,23,531,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{343,801,1000,1000,-82,-978,967,-1000,-661,-999,747,-28,-802,173,-1000,340,-1000,-105,214,-728,990,706,45,-142,197,190,379,653,-1000,684,-1000,-28,1000,24,681,-230,81,1000,1000,1000,-207,-1000,-136,833,812,-828,1000,425,542,-95,496,189,-172,534,-34,577,1000,-127,1000,-998,163,-9,817,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-612,-6,1000,1000,-571,643,-367,89,481,1000,12,-1000,-1000,537,881,1000,708,-1000,-14,1000,659,481,1000,-516,-941,553,-84,-75,-450,-154,1000,1000,-1000,-126,-373,425,303,-22,-322,735,-299,1000,-820,-1000,-217,-708,-342,-261,137,974,-591,1000,-384,692,-445,542,-959,752,190,1000,-1000,616,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-90,1000,-148,-541,907,523,-1000,-1000,-1000,173,943,1000,1000,-1000,1000,1000,475,479,1000,-1000,-143,-821,-573,-66,1000,-1000,1000,836,1000,-1000,1000,-1000,683,-1000,249,-597,1000,1000,853,-27,-744,-1000,974,871,-418,1000,1000,1000,-183,189,-1000,-1000,-1000,110,799,448,389,-258,352,-532,1000,-678,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-578,339,677,-1000,411,-312,-816,1000,93,219,-114,-1000,477,-277,-484,-1000,-413,-562,-1000,-205,-1000,-1,-302,-1000,-421,-697,-153,-449,1000,159,365,-1000,-296,218,172,127,1000,-1000,-1000,-415,-1000,1000,-849,49,-1000,-889,181,9,-666,-1000,485,971,1000,-794,461,-298,-226,-160,-1000,1000,1000,23,665,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexEquals(int):org.jsoup.select.Elements",
            new int[]{-208,-609,-1000,1000,93,-726,-691,88,-1000,1000,-1000,-315,44,984,1000,-307,1000,-1000,-450,847,430,-1000,-1000,-454,342,181,-339,-758,155,663,619,927,580,-1000,450,-1000,-1000,273,-572,-1000,-114,1000,-453,934,-527,-418,-635,-79,1000,-226,160,25,356,638,904,-807,484,-170,-272,-1000,-281,1000,-490,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexGreaterThan(int):org.jsoup.select.Elements",
            new int[]{802,90,239,723,-201,-770,139,-128,-279,-359,1000,-263,428,-1000,393,-1000,211,-750,651,-184,-550,-367,-467,-579,784,-494,154,734,-240,295,-65,-1000,20,809,1000,1000,311,91,-112,-654,309,-105,533,812,1000,-221,519,-339,709,430,-192,682,844,-629,800,292,-1000,-343,-323,528,701,-1000,436,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexGreaterThan(int):org.jsoup.select.Elements",
            new int[]{-947,-918,648,4,968,279,-631,-580,533,-869,-248,831,-631,-924,142,-419,123,490,-447,-796,-110,76,251,-879,-414,341,115,-88,818,-918,117,847,-508,-806,451,610,677,-467,-331,-672,14,137,494,344,-619,-350,-100,-468,432,-560,-477,377,-860,-366,-510,513,-455,-973,365,-21,-465,-332,47,-934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexLessThan(int):org.jsoup.select.Elements",
            new int[]{-354,-723,693,-820,429,-1000,-536,-117,309,-951,-145,-124,9,110,-268,-485,965,555,-964,-228,-220,259,1000,-180,1000,798,42,-289,-26,-312,-811,-1000,1000,442,-237,-344,1000,979,279,481,-528,-395,520,-716,-688,541,-5,1000,-552,248,-1000,-33,-283,-296,640,-370,209,110,509,174,-286,316,-156,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByTag(java.lang.String):org.jsoup.select.Elements",
            new int[]{-287,-732,459,1000,-155,-394,-717,653,367,828,973,-165,977,-238,1000,517,396,-407,-281,1000,851,1000,-510,687,-1000,250,-460,408,954,1000,1000,673,524,979,-107,207,-910,1000,1000,453,-3,-213,889,-41,-1000,-922,-1000,72,-998,1000,407,1000,407,-1000,-205,-1000,-85,-974,-1000,777,1000,748,-1000,133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,132,924,1000,-1000,258,-666,438,214,396,1000,-731,-1000,567,684,-477,-586,-39,1000,1000,-457,251,-932,-11,158,847,135,-827,974,1000,485,-1000,-119,-825,-83,96,-882,1000,529,-398,-40,1000,-1000,829,-1000,-1000,-502,771,-762,-178,-396,-423,-864,-983,-445,-126,-540,1000,-241,-218,264,-20,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,555,157,-979,918,72,745,702,163,1000,-530,207,136,-37,-157,-898,89,-877,-245,940,-687,1000,-1000,833,844,1000,-719,-271,-641,-727,-168,-942,-375,1000,1000,-958,777,443,794,112,1000,-1000,444,-903,1000,-543,650,855,-469,-1000,20,-46,-231,320,26,-1000,-600,280,985,-264,609,-1000,455,594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-345,-439,-686,-1000,477,-756,369,-1000,1000,-1000,-834,-919,153,834,1000,-421,-55,-325,-1000,-20,278,70,717,-1000,1000,-637,1000,409,422,686,-214,-451,-656,1000,242,-650,-696,-307,-959,-16,-1000,-106,261,-243,-1000,107,558,-335,-422,398,-351,1000,-551,24,1000,102,-1000,1000,401,92,1000,-383,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{746,-627,-524,145,1000,-126,940,-1000,127,625,-1000,-1000,-1000,1000,-838,-388,268,-395,1000,-1000,-1000,232,61,-549,944,-402,1000,146,703,517,1000,355,-48,-967,-124,-1000,1000,1000,817,198,246,745,-960,-591,355,704,1000,55,646,-91,-1000,-346,991,601,658,-1000,1000,-753,-966,711,-345,-66,1000,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{774,960,383,724,-687,95,-1000,570,580,-110,1000,-180,-1000,-1000,-358,-264,-521,400,-1000,-518,147,-748,686,1000,128,85,503,658,-460,-608,138,-335,1000,1000,-545,137,-471,85,91,581,-163,163,-740,9,678,-422,-64,389,-577,338,-510,808,1000,-1000,-76,-468,-160,1000,220,-115,75,274,219,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-693,614,-493,762,-853,254,998,-397,-689,1000,-86,111,1000,214,-426,-243,-173,-1000,322,103,1000,-1000,-1000,752,874,-925,-894,-216,891,-1000,883,-1000,1000,-268,-453,-187,-1000,422,24,650,-1000,390,-347,76,1000,-1000,-1000,957,1000,553,-1000,-787,-1000,-408,139,-1000,1000,-1000,667,1000,610,1000,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-75,-111,-1000,21,1000,-945,-838,774,75,-604,-694,-469,663,-1000,245,595,974,1000,632,-166,1000,-1000,970,1000,-257,-1000,-1000,-44,-986,45,-73,-827,768,614,645,565,554,-327,930,-435,25,1000,167,-518,1000,-902,130,-1000,-748,-1000,-963,95,1000,902,-1000,314,-574,-570,1000,91,-1000,-15,-804,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{615,-339,263,-103,-20,709,-381,562,288,1000,-177,-298,415,-805,443,1000,-315,514,178,51,671,-580,434,-975,-663,1000,604,514,-63,-431,-266,-328,-877,391,159,985,-477,-669,-472,1000,593,-749,-246,-791,-836,285,826,-789,-1000,362,820,215,-797,-205,280,-97,-595,184,-477,467,-20,306,483,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{1000,-339,20,-307,-872,789,545,1000,-1000,315,995,-917,-1000,-401,-604,-129,1000,1000,-891,868,1000,-831,908,-24,51,-403,185,-933,813,1000,-994,-419,-385,-213,815,-1000,853,366,-787,206,-708,-1000,-442,-1000,-351,1000,170,482,112,-150,1000,118,791,788,-897,-106,532,992,-1000,-213,-423,6,-1000,-604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{1000,-219,899,1000,-557,-97,768,1000,-103,-970,-751,-95,-1000,-651,-1000,-1000,1000,-1000,-1000,1000,1000,-1000,1000,-1000,-1000,-1000,1000,-305,1000,-749,-1000,1000,-277,398,942,-742,411,444,1000,1000,1000,-1000,1000,-1000,-1000,528,1000,-1000,1000,1000,421,1000,-1000,1000,-1000,-1000,1000,1000,-1000,-1000,104,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{1000,-339,861,1000,-111,294,613,1000,-1000,315,-582,-20,-1000,-105,-361,-1000,1000,-868,-891,562,1000,429,524,-664,-300,-403,1000,-374,1000,-578,-231,604,-507,-22,34,-598,-29,-715,909,723,1000,-69,1000,-1000,-813,1000,672,10,1000,233,138,1000,-1000,332,-507,-1000,418,1000,-729,-1000,168,-1,-1000,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{649,750,291,-346,-197,818,-164,380,680,-112,-691,971,-280,-364,-263,942,1000,1000,70,-1000,466,61,330,242,-60,-1000,14,422,-968,-423,-115,377,450,-614,-218,-507,-1000,-1000,-902,444,-764,931,877,-213,-1000,-27,2,805,-482,-1000,504,-633,-685,1000,-558,1000,823,885,-128,-809,835,-178,-311,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{1000,159,-294,-552,-96,549,709,1000,-1000,185,1000,-537,1000,-1000,-793,861,228,1000,1000,1000,-141,1000,1000,-104,432,-1000,-1000,7,287,859,-1000,-1000,1000,-187,1000,140,1000,35,-1000,-1000,-1000,-1000,-381,-648,832,127,-1000,343,-962,1000,858,-1000,1000,-1000,-1000,1000,445,-224,-1000,1000,-1000,-380,450,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{167,423,291,1000,1000,189,1000,1000,-281,-685,-1000,-336,-1000,-105,-361,1000,1000,-1000,-1000,1000,1000,473,524,-1000,-300,139,1000,-715,-405,-1000,-377,922,-18,913,34,-81,1000,28,1000,1000,1000,-76,1000,-1000,-1000,1000,772,-630,1000,1000,74,-633,-1000,84,-618,-1000,475,-101,-729,-1000,-182,-89,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{189,183,-456,136,-111,-46,-231,57,103,315,575,516,318,-481,-361,559,79,812,45,-698,-441,429,210,1000,-618,-422,-999,54,-1000,484,304,27,1000,899,34,-598,-437,375,-783,316,-820,-29,-108,242,-433,-593,16,-425,-19,-311,1000,-528,134,332,1000,780,375,-309,573,-66,156,826,400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasText():boolean",
            new int[]{99,960,-22,1000,-345,908,-277,1000,1000,253,388,-541,231,1000,-465,1000,-754,828,280,-1000,-564,610,-794,-270,-278,1000,1000,536,-60,488,1000,-66,1000,-1000,-208,-612,571,305,-936,-406,-832,529,-647,-570,966,-782,285,-977,593,-314,1000,161,679,1000,1000,51,-303,1000,-852,185,-1000,-1000,465,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html():java.lang.String",
            new int[]{849,-99,263,-225,332,-687,-931,561,-943,125,-337,723,400,-529,-705,-893,604,88,-33,213,-420,59,526,-426,-940,-338,243,79,541,-506,-842,-225,267,604,-201,49,876,-82,817,410,-718,895,-486,525,-134,60,-681,-344,-910,818,-181,193,321,786,-866,-427,-209,-210,-846,-418,413,588,-591,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.Appendable):java.lang.Appendable",
            new int[]{-1000,273,-217,-15,382,439,73,863,-815,-350,101,373,334,-762,1000,-479,-356,827,-1000,400,-520,-119,1000,155,-400,353,-933,1000,400,175,1000,-1000,1000,343,881,-1000,702,-400,-458,451,-1000,288,-1000,-660,1000,29,937,-1000,-222,-573,934,951,-1000,1000,1000,-372,-1000,-271,745,1000,-1000,-1000,90,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{351,-828,1000,750,667,636,-422,-832,-77,16,125,44,-1000,-823,-1000,-1000,-1000,-467,626,1000,533,-509,866,445,936,-710,941,1000,-1000,-779,-245,-799,-618,-395,-192,-650,-64,-104,569,-1000,1000,-569,927,-66,34,-1000,-978,561,-1000,756,381,1000,1000,430,1000,1000,391,797,79,-189,-53,-14,-1000,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "id():java.lang.String",
            new int[]{-179,195,-426,876,669,-23,944,463,159,-272,164,-520,-1000,-711,-600,464,-832,892,750,968,-1000,552,147,86,454,756,440,70,-290,971,340,1000,-1000,737,844,-919,-771,-111,58,167,-43,237,-151,418,-71,-1000,-52,343,393,201,334,-624,659,328,430,411,-214,660,-399,83,668,302,-248,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-1000,510,-1000,323,-339,1000,-321,1000,-699,-862,1000,673,1000,-1000,776,-951,-1000,-279,-152,261,1000,581,1000,1000,-931,-1000,-1000,388,-336,257,77,1000,-1000,-157,975,-1000,786,-1000,-1000,1000,-1000,350,57,-1000,735,280,659,463,-320,241,-159,557,598,-697,396,-983,-1000,-1000,-838,923,205,1000,-733,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-936,117,1000,-203,351,-56,198,434,-92,571,857,278,1000,-477,-468,-776,-1000,521,-456,720,-985,622,863,813,-1000,-869,-1000,-584,-650,-250,-425,-992,-56,-802,-74,-1000,-745,-830,-996,56,-787,159,-105,-900,-816,371,-380,-606,-910,-961,50,1000,900,321,1000,-947,-461,-1000,-825,1000,236,878,-565,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{702,-573,-1000,1000,147,-967,-21,924,-150,52,1000,-592,1000,265,-612,469,-1000,74,-160,1000,511,739,664,418,-741,-531,406,388,-756,-273,-1000,-1000,-860,-1000,-845,-1000,132,520,-185,-1000,295,786,115,-236,-1000,-842,-1000,-39,1000,-929,-533,1000,-524,-51,-1000,522,-645,1000,-871,376,1000,105,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{792,96,583,-1000,1000,-7,1000,627,-656,-680,694,-781,-1000,-573,1000,727,264,-1000,806,-869,252,-527,-258,1000,51,737,1000,-10,644,685,1000,716,-208,-1000,173,679,-55,755,-1000,-497,515,-784,1000,1000,-1000,86,-677,-1000,-742,288,-400,812,433,681,-351,-93,179,1000,-1000,82,-147,1000,789,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{1000,-270,105,1000,1000,319,753,467,-83,705,1000,161,-1000,-426,604,-1000,-203,-1000,1000,-1000,372,-399,598,868,-552,-207,1000,-512,1000,-429,747,594,-848,-272,204,1000,637,451,-463,-1000,573,-57,1000,1000,926,-230,25,-1000,522,219,1000,-349,-809,-982,193,380,156,-386,-1000,449,1000,1000,128,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{413,-246,-284,688,436,-328,650,741,-501,274,-418,-566,329,-600,-141,727,607,505,155,620,-605,-291,-832,821,-710,737,403,400,322,-615,601,497,259,-284,466,372,40,575,-268,-847,285,-784,329,453,-468,840,-557,723,710,646,592,680,-343,681,-515,429,3,-839,409,-984,655,-757,160,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-33,699,478,-1000,-332,-71,134,-351,-575,45,222,363,158,1000,1000,-1000,1000,519,868,-1000,155,-1000,-567,-161,-1000,-1000,-743,-226,1000,741,-585,-1000,-320,512,-144,-1000,-1000,500,-1000,-66,1000,827,1000,654,788,678,-923,1000,773,-1000,-129,1000,1000,271,-118,851,-1000,-1000,268,35,-1000,838,102,389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{1000,477,24,-201,-808,-1000,404,-1000,-218,-430,1000,1000,-506,1000,718,-1000,523,828,844,-198,346,-644,-459,621,-1000,-406,-16,24,1000,-534,-625,-318,-29,-427,269,-1000,-280,285,-213,983,334,1000,1000,904,134,-1000,-895,781,-815,-1000,-1000,-511,455,316,400,-16,-707,-1000,271,1000,-632,351,-964,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(org.jsoup.select.Evaluator):boolean",
            new int[]{-413,-612,-795,-734,95,-400,-831,418,-200,1000,-368,-20,-450,134,400,-171,1000,-41,463,-536,-400,74,609,248,-1000,-400,-989,-308,-45,565,-129,-297,326,37,327,400,-136,-400,-582,-184,-46,155,1000,215,-705,-400,-400,-316,-121,790,307,168,899,1000,-348,-40,-20,914,-526,-504,27,1000,400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "isBlock():boolean",
            new int[]{-195,-78,-889,711,419,989,-1,1000,-662,662,652,133,-910,-196,-565,59,970,660,627,640,431,114,835,-636,726,-732,303,-43,-830,758,240,-224,-1000,-533,591,1000,246,-1000,660,873,-465,-222,-490,1000,-519,-460,772,-1000,940,1000,-710,452,-1000,880,-910,-1000,-169,-988,71,16,296,-312,1000,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "lastElementSibling():org.jsoup.nodes.Element",
            new int[]{-1000,564,-1000,481,-14,619,218,-47,-1000,-1000,-256,-184,129,398,747,-566,-1000,-400,1000,-408,400,-804,1000,-86,-74,630,-179,-736,580,213,824,-596,1000,561,784,-126,486,249,-1000,761,193,547,127,99,-783,227,-1000,1000,446,431,757,-1000,-725,88,886,-692,-659,-823,953,-427,-933,-152,-1000,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nextElementSibling():org.jsoup.nodes.Element",
            new int[]{-1000,867,1000,-536,1000,-894,994,568,1000,-1000,94,104,289,267,1000,-1000,-506,-61,979,1000,-855,1000,916,232,37,939,517,876,606,-1000,361,-388,-400,1000,589,655,173,-1000,45,-555,-458,-1000,-168,639,-42,265,249,-593,-878,986,0,-1000,-679,-653,220,1000,709,-497,-1000,1000,506,754,-652,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nodeName():java.lang.String",
            new int[]{-405,-669,728,385,-488,-347,-342,-702,181,-747,73,-60,-110,-862,54,-693,-326,-472,1000,-588,382,-160,103,-660,309,891,-665,136,463,-889,1000,506,-981,-1000,1000,-349,890,71,154,-1000,-1000,-347,1000,41,-361,-1000,238,-382,-47,480,-950,-400,-619,566,66,-397,-796,-278,138,10,-1000,-720,-667,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "ownText():java.lang.String",
            new int[]{416,828,-325,25,-263,-213,464,-302,-137,83,-1000,-56,638,-1000,-760,-45,550,-180,-272,769,-751,-1000,750,-589,-16,144,-327,-1000,-206,446,183,-1000,580,1000,-717,-1000,347,-1000,935,407,-1000,420,-904,1000,210,1000,738,535,30,-1000,-1000,-367,1000,-129,288,-1000,293,-35,-1000,-1000,-767,-6,-1000,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parent():org.jsoup.nodes.Element",
            new int[]{-123,819,521,-85,-331,-1000,529,99,698,69,-540,153,340,8,417,-140,319,191,-245,-1000,-308,159,-86,-165,-441,530,-346,-97,-118,-654,298,-397,-432,44,-895,333,-100,-60,222,-272,511,-129,-760,251,602,-839,1000,649,-55,1000,381,-573,966,-387,83,-45,581,-477,-286,-737,-924,261,-498,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parents():org.jsoup.select.Elements",
            new int[]{1000,-834,1000,-978,535,-1000,789,-272,-511,-367,-2,-485,-1000,-729,-247,-857,-191,314,662,-928,-1000,-1000,1000,-1000,-339,1000,-5,1000,582,-1000,70,-253,-940,-12,1000,-214,-168,-393,-1000,-389,689,-1000,1000,1000,-1000,-723,1000,-200,58,973,1000,-1000,437,-802,1000,1000,1000,-310,1000,400,944,31,1000,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-280,321,-758,441,82,-936,124,-856,336,-843,735,53,401,165,-575,-645,216,-537,-246,-642,-653,80,-23,-859,828,-657,191,-334,367,85,-187,538,728,738,-830,840,-903,-960,376,495,559,253,372,-509,-602,831,-947,-78,-85,268,-276,339,-351,-483,-218,-863,486,-195,494,-157,979,918,8,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{370,-27,-1000,-665,-152,-44,849,-171,-53,-849,-699,354,3,-510,1000,309,-197,-483,-28,-1000,-578,31,-286,-393,123,902,-561,-471,-158,741,428,-309,-443,31,667,1000,-468,-279,-245,-76,357,-639,162,-155,1000,201,560,317,-816,1000,-296,1000,-418,-223,-896,-1000,-137,-1000,-364,351,-102,675,921,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{414,642,-513,243,-508,305,72,-480,-236,-1000,1000,483,400,447,-246,550,1000,658,-1000,-296,-165,-1000,-401,70,17,497,1000,171,-542,1000,534,-401,1000,-208,85,151,288,-1000,-1000,-1000,-124,-739,1000,400,596,-670,1000,-245,1000,-60,-1000,-90,-267,-244,1000,502,284,1000,1000,239,-868,-1000,-344,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{224,-708,471,-271,-107,-976,103,-111,83,667,379,751,1000,754,147,1000,-656,2,-395,186,-1000,-388,129,887,123,909,-426,899,-448,-111,-156,1000,543,726,-65,526,-927,-735,771,-909,-1000,-262,730,423,166,-939,-747,-315,1000,-3,-1000,-144,684,-621,798,595,63,599,511,0,546,-155,191,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "previousElementSibling():org.jsoup.nodes.Element",
            new int[]{1000,186,-1000,1000,39,-1000,348,-392,-1000,1000,-1000,1000,209,-1000,-244,17,-1000,-1000,254,-1000,585,-432,-1000,-633,318,-474,-292,-1000,825,-1000,-119,1000,1000,1000,-1000,1000,1000,-698,283,-544,-435,-1000,1000,665,-1000,779,-564,-1000,-479,-739,-374,-935,-1000,1000,-1000,-1000,988,476,-1000,-583,1000,-1000,-1000,975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "removeClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{694,-153,587,829,105,-243,284,313,594,1000,324,979,1000,309,-306,-406,-433,135,-1000,248,190,-1000,-486,-850,1,-752,192,641,1000,771,-202,542,824,-1000,-347,1000,-57,-863,1000,-830,185,-468,1000,-68,-564,234,-264,-878,-1000,-527,505,-699,-826,-448,1000,563,-555,153,-1000,396,-138,-772,177,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{601,-984,548,-144,-970,653,-923,-958,-910,648,30,193,532,228,-842,823,749,-1000,-8,-795,400,-585,-756,909,990,1000,330,-208,-1000,-488,76,-61,-597,351,1000,266,-421,762,669,-373,-445,1000,1000,800,-427,1000,-840,-1000,334,47,11,-899,-1000,554,-729,-136,-164,239,835,703,936,558,744,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{-173,-513,285,-889,1000,313,-511,-457,160,-389,490,139,190,380,-356,-675,56,-668,-781,-242,-155,-926,-1000,-503,-666,-1000,895,1000,74,-262,908,656,564,872,663,-782,437,376,-123,161,161,-400,-953,-350,350,426,1000,-417,1000,-250,1000,-171,1000,1000,-807,679,-943,999,992,258,832,210,-376,-867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,-42,-422,917,-56,-881,-997,1000,-443,589,-693,614,-14,1000,-392,-819,1000,-302,1000,1000,-1000,-256,514,-478,-1000,109,-840,1000,-955,-449,1000,-1000,1000,1000,973,990,571,468,282,990,-92,1000,-1000,521,-717,75,-1000,659,-1000,-55,58,-499,-983,702,1000,-561,-255,775,-1000,728,745,286,-383,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "shallowClone():org.jsoup.nodes.Element",
            new int[]{-116,-300,-1000,-583,-1000,15,-616,-494,598,-86,-1000,-563,-265,-59,606,884,665,14,-1000,1000,-824,665,-159,-509,-653,1000,76,-1000,396,121,1000,-511,-877,95,1000,-449,94,-174,286,214,3,1000,1000,203,-1000,-1000,-855,-27,-1000,-585,588,669,290,168,1000,-1000,-770,-899,-257,108,-653,-699,779,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "siblingElements():org.jsoup.select.Elements",
            new int[]{-933,39,-586,1000,0,-715,59,932,-47,102,274,-171,1000,1000,571,782,268,1000,-174,-1000,486,-1000,-616,958,1000,-116,972,-278,-573,-1000,1000,693,1000,1000,1000,0,-917,-789,52,-358,613,-1000,-481,1000,-618,-523,-1000,-452,985,-347,-530,1000,-15,81,-479,352,-604,-367,-842,-589,876,405,1000,953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.parser.Tag", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tag():org.jsoup.parser.Tag",
            new int[]{-242,-909,460,198,1000,419,289,363,98,-794,-1000,475,529,-1000,383,-831,113,1000,988,-507,408,-469,149,-524,83,-258,906,932,1000,183,342,-1000,596,-340,1000,305,-1000,-1000,-1000,-1000,-586,1000,-737,1000,1000,727,628,-679,-45,-881,799,-553,1000,-1000,1000,-317,-427,575,290,1000,-610,237,1000,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDAwMA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName():java.lang.String",
            new int[]{-963,720,1000,1000,275,-395,-66,878,-1000,-492,-1000,365,341,-946,-943,-726,1000,-1000,-1000,-967,-1000,-931,927,518,-915,-1000,-328,123,-1000,1000,1000,1000,-140,1000,952,1000,-1000,1000,-1000,-1000,-996,-566,1000,-452,1000,-1000,-1000,732,-1000,1000,-832,1000,504,1000,-763,-1000,-220,261,1000,-297,-1000,-1000,936,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,-471,564,1000,-1000,578,-47,646,-608,-1000,197,1000,1000,784,-1000,9,-86,-1000,-1000,-865,-649,1000,-291,-107,-835,-632,-274,684,1000,1000,-35,-810,-908,-940,754,418,-282,240,-801,-674,-610,-876,792,1000,-1000,947,1000,800,1000,-197,-699,-529,1000,1000,-1000,979,41,-1000,-742,-1000,-725,-963,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text():java.lang.String",
            new int[]{-1000,120,-972,346,-147,573,308,915,1000,-246,1000,1000,-62,-1000,1000,-636,475,1000,-731,-662,-531,-680,993,46,-478,834,1000,1000,-870,1000,-1000,1000,898,323,-1000,-277,73,-284,-1000,-314,-1000,-644,-965,-253,-395,-339,1000,884,206,1000,367,-321,293,-484,-674,-1000,702,1000,788,583,-949,1000,-173,63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-364,360,1000,810,85,55,479,123,196,-316,1000,-586,-88,-152,233,-123,140,747,792,1000,625,31,799,-997,1000,980,-814,-1000,-519,-1000,1000,798,-963,-1000,375,151,-755,1000,-499,-1000,1000,449,18,-1000,698,-1000,-207,-329,-167,-775,-719,-472,-554,497,900,-71,566,-319,-1000,1000,227,215,-459,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "textNodes():java.util.List",
            new int[]{-132,408,-466,-4,-1000,-573,373,488,-888,-427,815,-1000,1000,713,785,483,1000,-749,-100,-1000,1000,-389,42,-426,-1000,-238,-138,-1000,-663,-1000,1000,-1000,-238,649,-771,-1000,433,-1000,1000,565,-406,1000,478,1000,-624,212,-240,-163,1000,1000,-193,-2,374,-969,1000,-446,-637,1000,400,-577,-1000,-438,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "textNodes():java.util.List",
            new int[]{843,-396,46,-1000,-1000,-241,-438,658,-1000,-1000,1000,-614,699,250,96,374,-536,-700,-625,-699,1000,-1000,-832,-1000,112,-724,573,-1000,-1000,-1000,-963,-749,735,1000,574,-672,-264,-1000,1000,282,-37,959,52,830,-71,545,984,640,1000,1000,-793,945,-1000,-541,72,344,-170,106,1000,-721,-1000,246,1000,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.String:PC0weDJmZSBjbGFzcz0iMHg4MCI+PC8tMHgyZmU+", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toString():java.lang.String",
            new int[]{-1000,-576,-137,-766,105,-426,958,-1000,66,-1000,-130,639,-406,663,40,-1000,748,1000,-259,1000,-1000,1000,998,-1000,-319,523,-1000,867,-153,1000,281,562,460,-238,-423,-1000,809,-1000,-1000,1000,-511,1000,-337,1000,-1000,-404,1000,262,-1000,-1000,1000,1000,-1000,-1000,252,-1000,1000,-1000,619,-367,535,-413,-1000,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toggleClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{701,-846,1000,-172,-11,715,764,-481,864,-624,-365,-612,650,-310,-824,164,-107,-439,1000,208,220,491,-440,-225,402,-275,96,601,553,162,-1000,-1000,746,343,214,292,118,-501,419,360,-593,-940,398,889,-807,68,302,-559,-1000,944,642,-451,1000,363,-976,-650,391,-655,403,958,-453,-487,-1000,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val():java.lang.String",
            new int[]{-1000,3,729,745,961,47,-596,1000,-991,1000,1000,634,889,-318,-956,303,-500,-144,-695,556,163,14,339,1000,584,361,-339,-82,162,-644,827,-725,-474,-915,-381,-261,-346,-1000,-876,-718,378,-49,-741,-478,-51,637,1000,243,160,-510,-382,-1000,381,-266,1000,-1000,213,563,-443,-422,-299,825,-376,-397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val(java.lang.String):org.jsoup.nodes.Element",
            new int[]{712,366,-467,-809,-469,-362,-225,-280,344,-400,1000,-480,290,-337,394,-285,296,-302,30,378,178,-293,450,-87,309,400,-675,510,483,70,1000,-18,-129,304,611,731,-416,564,834,-505,83,-310,582,191,-1000,81,-457,-556,1000,184,529,-284,-122,204,239,-1000,-730,362,-1000,-893,927,1000,-161,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{168,567,570,395,-390,-807,-582,-188,549,816,105,-177,-18,402,-624,-759,-697,-784,173,805,499,-597,942,-530,417,326,-335,-327,-881,-499,29,709,757,-765,23,312,-867,271,347,339,-818,-375,-456,-947,-6,-387,477,-94,625,613,917,980,-984,-508,-627,255,571,833,15,-261,612,-895,-417,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{456,741,394,1000,569,-291,-146,-66,607,345,178,-78,-18,1000,-33,-231,-379,-1000,1000,1000,-181,167,-298,313,538,846,-586,-198,560,-827,575,1000,1000,-801,716,-1,-821,1000,456,55,-1000,127,384,-1000,-519,-270,846,365,1000,1000,575,1000,-424,170,-627,245,121,757,420,8,-236,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{642,512,271,355,-760,954,747,-254,743,560,-315,975,350,-525,649,-330,620,387,-198,795,514,-624,-123,767,599,500,472,643,-482,-721,-69,-351,-58,-591,57,705,-374,-880,198,-204,870,438,-106,-950,-244,249,-823,-801,833,587,880,751,-405,822,-146,630,48,537,-130,308,-52,821,159,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.String:KzQxNA==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-12,-414,-222,-978,-847,205,-289,671,888,253,145,-729,-856,-13,566,-239,231,-79,-81,-872,-888,-866,-50,533,512,236,-735,752,-195,206,953,886,699,-247,-504,-211,884,-205,600,629,-965,206,582,-416,794,-941,-14,-215,-273,261,-795,-674,-401,-301,91,-616,806,791,730,-657,296,-311,891,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFh", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{892,-583,-526,-385,876,118,-959,-674,294,72,-206,-837,-276,994,-707,53,-632,-810,203,-449,637,603,771,-376,-443,-660,-785,-734,902,-7,-443,-312,190,-996,-149,213,-268,678,924,-261,763,-963,777,-344,361,-352,611,-580,-677,-180,-921,-334,-513,-916,-207,-318,54,781,-578,361,412,-274,-275,312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{110,-461,-842,-755,434,-970,294,133,348,-483,70,-374,983,470,-319,-644,555,-157,912,-595,501,-451,585,775,-699,207,768,850,-885,512,345,-944,-67,-984,-462,-934,777,384,-756,-233,-295,250,-387,-24,-160,682,-766,-599,-539,-28,-528,719,322,-39,41,797,-227,-543,-260,-940,-970,-596,425,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-114,-632,340,842,400,431,741,43,-648,408,263,515,-662,-898,542,744,-156,-157,790,189,-30,-872,881,-823,-773,120,-556,234,-640,893,403,798,915,-167,-12,-113,-900,805,-184,-504,397,-603,339,-793,306,854,680,-117,-350,129,-310,-890,-58,932,902,-438,-566,-426,990,-576,721,992,-657,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{986,932,-467,599,420,-626,-485,-389,511,-256,-564,-220,714,-356,245,958,629,-357,-930,801,179,-913,630,98,171,-607,-959,-602,351,544,-952,169,966,801,-39,-307,-11,-324,191,-421,-319,123,-843,-634,966,-399,576,991,219,-841,630,-890,479,-978,401,202,-134,295,53,205,28,-657,759,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{111,362,482,89,-473,-117,693,-31,-837,-1000,-102,-1000,1000,567,253,212,1000,-519,-476,-52,70,-76,-1000,-486,784,327,281,-649,-656,-125,660,336,70,321,-460,1000,132,-372,-593,-9,-49,-708,906,1000,1000,-887,186,366,394,997,343,1000,-304,1000,649,720,792,296,135,-678,-190,610,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{-742,-875,-616,-488,-231,-713,9,44,-513,-657,527,-933,-281,22,-467,446,616,118,-99,-810,655,-778,-499,716,443,930,-130,-662,-94,794,631,-553,-799,-362,-434,353,-838,-5,-335,-927,972,-551,-738,494,857,-589,-627,-707,111,-507,-246,735,-499,39,-134,66,194,618,935,583,765,313,594,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "", "createShell(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-714,-668,389,593,261,-874,370,-256,886,16,871,-254,831,978,90,-360,-513,-35,0,81,754,168,583,610,-188,972,706,-541,-670,989,85,931,-346,-975,-658,-479,-4,-796,-27,951,-670,552,-783,81,827,-656,247,237,-41,251,-84,-57,141,-117,362,-739,742,-253,-843,972,616,796,-553,-292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{-1000,495,1000,671,213,439,649,311,1000,-701,-1000,-1000,1000,-1000,-413,-137,1000,338,-129,-1000,-299,-1000,1000,-893,-1000,-238,-1000,1000,-403,-790,441,-973,1000,-1000,-1000,-806,1000,-995,-6,-131,-343,-1000,252,-507,21,-1000,-1000,-1000,953,-1000,-1000,828,-1000,-837,-389,998,231,916,-175,1000,-1000,534,1000,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{-1000,970,77,977,39,-169,-1000,-611,299,456,-326,1000,848,1000,-1000,346,-46,-597,-410,-924,-333,704,-505,-414,366,-695,-868,408,-487,-152,-256,800,838,-488,182,-1000,-1000,244,773,70,-175,-184,-475,-730,-446,-365,626,-288,-641,-127,-353,670,1000,25,-380,-280,82,-1000,-789,753,-405,-45,235,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "outerHtml():java.lang.String",
            new int[]{-763,-788,-603,753,1000,-491,-41,812,965,202,754,1000,-158,-729,1000,1000,207,-380,-953,-1000,-603,788,116,98,-587,865,-1000,841,-1000,-974,-295,1000,-513,345,-1000,-1000,-962,1000,1000,-1000,899,1000,-558,-1000,-1000,-682,404,-960,810,-580,-40,-22,514,757,1000,-1000,-406,-98,-196,764,796,459,1000,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-823,-1000,246,1000,1000,561,-333,-270,991,306,-1000,516,698,-365,-667,655,874,-819,-47,-634,1000,-988,345,1000,-834,-10,1000,-1000,343,-1000,-809,79,-934,-442,786,267,-233,-97,-207,-105,-781,-641,-843,-286,795,-617,-510,-1000,-61,12,329,444,-1000,-639,183,-859,1000,242,-1000,777,-881,490,-383,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-882,194,514,-240,-179,234,-344,-263,700,746,158,-31,242,-13,624,619,-760,-349,-509,211,857,243,221,-123,-506,663,931,436,107,-448,-986,417,-162,105,553,-400,599,-700,-830,-836,624,788,-704,114,862,52,816,288,614,871,-475,976,-210,-410,914,920,818,291,-596,323,-666,-484,-627,365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "title(java.lang.String):void",
            new int[]{630,536,877,309,954,-461,402,759,-265,1000,510,473,-600,-418,-34,746,716,-978,927,-236,224,-221,-943,948,176,-1000,399,921,300,-694,-1000,-899,1000,-1000,618,-828,1000,200,-239,1000,-222,785,-167,-1000,-44,452,-1000,-618,-1000,1000,-90,-784,773,-87,-1000,-842,-945,-660,-110,1000,195,548,-315,-360}));
    }
}
