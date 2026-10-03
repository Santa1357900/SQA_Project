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
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "addAll(org.jsoup.nodes.Attributes):void",
            new int[]{771,328,774,-141,-696,136,-631,462,-797,-623,-49,-573,722,-740,898,27,868,557,4,-679,-227,884,-494,-199,567,-556,677,974,-916,-80,400,-943,367,-653,-1,701,604,-393,574,946,-137,432,-903,429,798,740,-377,314,-652,532,79,-647,-613,597,-666,30,268,-182,-492,-160,238,31,792,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "asList():java.util.List",
            new int[]{535,463,688,331,541,-237,854,-457,154,-348,183,779,-740,213,199,832,-208,603,465,-95,512,190,-373,-35,-200,-228,939,-153,-127,-405,150,873,-78,687,223,259,-753,-173,513,211,709,384,-248,268,-474,-791,476,-777,785,-230,-633,700,895,-192,-589,-625,-329,-49,-297,718,713,-114,-686,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "clone():org.jsoup.nodes.Attributes",
            new int[]{965,472,79,65,491,-988,74,-285,725,505,834,-929,-209,-669,608,817,-283,-69,151,-521,-19,-97,964,98,-515,-237,-788,88,-533,132,481,-300,-5,-606,-477,-101,-971,-139,-549,-342,-195,365,459,-307,-713,-153,-916,474,-225,0,-743,985,-575,-682,-46,-849,-625,-13,-867,-880,-535,990,-184,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$Dataset", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "dataset():java.util.Map",
            new int[]{915,-502,883,-149,-687,188,273,-487,-626,-643,920,964,-383,639,867,594,524,141,392,-392,-908,741,-728,-556,450,-495,981,-397,-318,896,-995,52,240,-284,-638,-465,433,-664,-880,-401,344,636,-715,-14,224,-683,-240,122,-64,-238,240,-90,-679,453,-94,718,-477,-880,558,583,-779,-759,423,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "equals(java.lang.Object):boolean",
            new int[]{617,-621,899,209,-373,-111,-830,172,-779,-994,-818,-381,-370,511,376,-475,885,-600,-300,-199,-502,-180,359,846,838,-464,857,3,-63,-617,822,-779,358,-86,-41,360,113,346,-42,643,-374,824,130,-955,-747,-14,170,612,965,-95,-487,68,467,-449,488,454,254,118,-304,987,850,786,862,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "equals(java.lang.Object):boolean",
            new int[]{990,-817,-471,-458,-318,55,856,857,979,-592,-716,-967,179,656,499,-651,-57,-450,39,-167,-756,120,-582,542,-842,-975,600,-521,51,495,-947,-778,-522,-162,-500,420,-423,370,-92,449,685,518,286,-276,440,853,-612,458,720,861,586,930,466,349,-169,788,591,587,-100,-686,36,-497,-79,-879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "get(java.lang.String):java.lang.String",
            new int[]{264,-432,723,712,-376,-299,-442,-714,249,-31,-850,-761,398,152,175,-5,661,478,91,-686,-990,753,917,520,-230,-645,-932,-91,714,-153,-88,727,780,205,-925,-344,-135,81,-539,-600,-411,889,-513,677,-32,26,41,680,-474,75,-652,894,-455,440,104,-926,857,557,-35,107,-123,545,843,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "getIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-672,-421,-581,787,796,-831,-521,-497,656,-588,-243,208,607,415,318,440,775,-999,4,100,835,472,89,701,764,164,803,-335,839,476,-221,-675,-740,896,-852,-759,761,-755,911,-670,938,-202,458,-125,649,268,-311,981,838,716,20,277,369,-612,-220,-267,71,-815,-37,372,-894,39,-860,-679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "hasKey(java.lang.String):boolean",
            new int[]{873,673,303,-110,-994,-694,-441,450,461,-918,203,-102,-6,59,-253,-112,-235,-942,-825,231,306,-273,-241,-718,-115,143,639,29,368,391,965,-222,123,69,-864,334,-25,726,409,-827,773,866,993,271,764,-656,-361,-30,-391,731,685,541,-363,-458,-465,-40,-277,-910,638,806,-966,-825,-843,177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "hasKeyIgnoreCase(java.lang.String):boolean",
            new int[]{151,705,-961,-182,-383,170,-161,413,14,53,-358,826,307,-132,-863,5,-644,-311,-458,-934,-170,-620,506,-665,573,261,744,364,-876,-631,-818,100,475,-135,468,701,363,823,-468,107,-607,-275,-450,-597,106,-746,151,-728,750,679,-1,-639,961,-942,-34,293,-192,782,61,-800,-263,-448,972,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "html():java.lang.String",
            new int[]{310,918,913,-656,183,-408,-249,-802,902,379,-242,-978,-732,516,-990,626,934,-401,-136,-603,-951,-219,385,-962,-66,901,799,783,-568,503,582,-805,941,984,207,-792,13,-523,-534,-282,907,508,-158,-242,-509,933,-592,22,504,-763,-589,-641,-672,858,-142,755,-843,-104,-826,243,-273,-708,338,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$1", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "iterator():java.util.Iterator",
            new int[]{54,-481,-282,940,-974,-727,-304,-856,90,-784,-601,-201,699,194,472,-575,252,-204,-733,-544,-987,765,543,522,-78,739,-2,204,-212,143,-192,179,347,-48,-149,274,-912,-137,34,-605,401,-63,262,478,-385,-812,-48,688,-217,768,755,930,-296,-554,119,407,-402,844,-284,427,86,-416,-637,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "normalize():void",
            new int[]{-647,-16,-697,-162,-790,440,-231,739,-115,-903,892,-955,892,-383,-317,-911,965,-552,962,-987,-844,892,226,-825,-769,840,-909,-545,9,-16,-934,960,971,-710,255,983,-46,-286,206,-300,15,737,573,-940,-335,142,-463,615,592,-356,-31,-416,-282,481,-764,-605,-35,-114,236,130,483,-550,741,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(java.lang.String,boolean):org.jsoup.nodes.Attributes",
            new int[]{-257,-1000,-171,-381,-204,-1000,-106,-723,1000,137,1000,-647,911,623,1000,497,-1000,-386,1000,543,-1000,152,-971,-68,6,-346,1000,605,1000,-1000,-350,-1000,196,-91,172,-1000,-168,321,-1000,-961,-571,1000,-591,-844,-216,237,135,-1000,56,-1000,800,-1000,1000,1000,-259,-835,714,-699,342,-1000,1000,-71,-35,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(java.lang.String,boolean):org.jsoup.nodes.Attributes",
            new int[]{-429,329,-97,-252,-299,-1000,-464,27,523,-1000,597,-1000,-282,-559,404,809,-115,-675,1000,303,-749,49,-87,927,-350,-1000,-772,82,-644,-740,-634,-1000,31,351,1000,-1000,-238,236,80,-1000,425,1000,-801,-1000,-1000,-775,-966,-984,958,857,1000,-449,491,266,640,-405,917,-1000,-949,-851,94,-697,24,-945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(java.lang.String,java.lang.String):org.jsoup.nodes.Attributes",
            new int[]{813,457,678,570,-443,778,-281,-152,497,944,-104,855,90,707,-791,-632,350,-875,-360,594,-670,-230,-948,919,139,-620,-514,487,-951,648,-528,338,294,-626,-933,555,742,723,933,738,-207,962,965,995,567,-297,-254,592,-264,-558,-610,434,-800,-42,-752,638,-804,-988,352,-444,-558,-297,511,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(org.jsoup.nodes.Attribute):org.jsoup.nodes.Attributes",
            new int[]{916,547,73,212,-24,-415,-495,107,-449,-296,-331,702,-643,-912,334,-133,125,-676,-781,-189,-655,-753,-68,-482,35,-83,778,-57,310,272,293,317,653,-778,762,-340,81,-207,560,-13,-343,280,-322,551,170,319,488,-12,248,311,-542,-779,-375,644,-657,610,-139,841,-833,-41,-602,-78,-768,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "remove(java.lang.String):void",
            new int[]{-849,693,-846,343,-798,956,928,-980,720,611,-251,300,106,671,-788,710,-896,779,-118,705,-686,947,-704,145,-845,117,-564,-809,-815,483,506,-745,469,-14,-312,-456,153,203,-699,-68,832,989,248,437,-608,625,-8,-590,-942,-989,-576,700,-899,-522,-66,505,316,147,749,-579,769,490,-810,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "removeIgnoreCase(java.lang.String):void",
            new int[]{944,-398,909,345,-260,263,-17,-36,666,-198,773,-692,141,919,913,-833,-271,977,-576,599,12,863,-857,-729,-37,-570,485,374,144,491,-655,564,514,-580,-298,-47,-794,6,-685,333,-780,-563,813,-427,-325,4,405,342,993,459,483,242,-841,-176,460,-234,-880,-118,312,519,-748,-395,-467,651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "size():int",
            new int[]{-1000,1000,198,848,231,915,-667,627,651,901,451,855,1000,238,455,-848,1000,-779,949,-375,92,-27,-140,682,399,392,-631,1000,47,3,1000,-13,291,-250,172,-278,-1000,-103,278,237,372,-869,1000,-386,-1000,265,770,1000,-123,244,910,-610,765,-747,494,314,809,443,-1000,531,232,-595,701,910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "toString():java.lang.String",
            new int[]{380,187,113,79,546,-555,-913,0,69,89,-826,-720,285,110,-346,-1000,272,-1000,-1000,964,0,115,0,531,492,-1000,-267,-760,-584,1000,-583,475,-120,656,-524,-798,766,604,1000,381,662,-580,156,-1000,971,-1000,-787,-142,-27,-805,281,966,1000,-1000,-403,-46,1000,-670,-584,-239,1000,878,0,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:LTg5N2UyMjk9Im51bGwi", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "html():java.lang.String",
            new int[]{-98,291,-214,864,460,-298,331,-522,-367,513,-736,-464,369,773,413,-368,-166,-17,10,-752,-218,-897,645,229,-135,43,-36,-909,517,415,-903,-961,-987,-1,663,-584,-301,857,517,926,987,-694,894,-570,-283,-916,-468,-629,-279,947,-515,-47,60,653,-120,-909,-747,867,638,-165,-928,823,327,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("VOID|getKey=java.lang.String:MHgxNDc=", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "setKey(java.lang.String):void",
            new int[]{999,-231,-750,-259,899,-132,-956,-145,18,602,976,891,884,-614,611,471,327,612,833,877,-382,-35,955,-74,105,-675,395,686,502,954,424,-266,471,-972,-209,15,-268,489,-585,309,574,-363,-295,808,350,-65,-382,-618,-705,103,-928,-276,-147,-299,-50,763,574,164,-388,491,614,504,580,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "setValue(java.lang.String):java.lang.String",
            new int[]{8,-529,360,825,939,-211,-785,-260,59,740,-433,-311,587,-249,402,-179,-770,724,-56,432,201,454,-915,-420,-168,-928,958,-486,-517,935,501,968,-839,-751,635,746,-309,16,372,-360,-381,426,558,-302,92,-329,-732,149,904,377,904,-144,-396,303,946,-913,-875,795,15,587,-278,585,389,-511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:LTk3NT0ibmoi", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "toString():java.lang.String",
            new int[]{-408,121,-822,975,225,-497,-990,-576,-24,-130,835,973,-622,-942,-559,647,637,799,306,447,-690,-762,224,-244,-670,-149,-449,-30,-113,-61,-514,-573,589,503,244,-798,875,19,-954,-250,657,-596,408,993,-141,274,-597,10,640,-486,-728,-810,-78,113,42,-569,-930,-751,673,-219,526,-322,-366,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{366,-984,-234,-656,-969,-451,234,117,434,-917,1000,58,-753,-117,703,-743,243,284,551,724,503,-489,965,268,561,1000,191,231,-637,-400,122,381,1000,-1000,-194,457,-176,1000,1000,-1000,-1000,926,-899,617,1000,-1000,410,33,1000,-663,313,1000,936,-845,410,-432,935,-1000,-1000,-70,-215,436,-1000,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{-1000,-765,-324,-890,202,-738,893,80,1000,602,593,158,-833,162,-57,-60,-7,206,24,1000,-931,-784,644,70,1000,-346,1000,267,-55,575,612,921,0,-76,843,735,462,-546,-560,1000,-533,-217,227,253,-356,-400,-362,-389,181,-166,-402,538,655,-201,-455,-5,267,-400,-306,-310,-779,-56,-534,912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{513,1000,7,-136,-299,-106,-848,1000,-1000,-42,277,-4,-556,-1000,-203,-365,199,-1000,1000,-155,384,-1000,570,-1000,-1000,1000,-912,32,1000,1000,-26,-801,41,188,-255,505,-35,-1000,842,-1000,961,-18,504,-1000,366,477,-618,554,-623,284,786,553,-6,-885,-239,-1000,862,-1000,-580,-564,1000,41,337,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{-163,-297,561,102,989,579,-421,31,929,562,-398,-742,-166,948,806,-925,-612,-528,-303,-680,-97,674,-882,28,723,96,-843,-513,-60,-341,92,-503,278,-781,-251,-974,124,857,-824,591,82,-263,363,-307,224,535,-119,414,-787,-628,810,-112,-110,-61,56,786,566,-902,-722,156,-740,-405,-966,-955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.DocumentType", "org.jsoup.nodes.DocumentType", "setPubSysKey(java.lang.String):void",
            new int[]{757,912,449,-470,510,649,-63,-937,871,435,644,862,108,509,761,923,629,282,-900,859,620,-151,765,-305,898,-588,-999,-706,-119,782,718,209,-334,-644,247,156,613,-16,145,467,528,33,-489,949,-410,-420,-812,326,-523,174,-502,139,814,-118,-325,387,-968,-551,-933,-531,981,308,335,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.DocumentType", "org.jsoup.nodes.DocumentType", "setPubSysKey(java.lang.String):void",
            new int[]{-1000,1000,500,447,-345,48,518,-924,286,-989,1000,151,1000,682,-1000,-740,341,-1000,-811,-318,332,1000,-207,847,1000,1000,1000,481,523,-400,-911,1000,409,221,-1000,995,369,150,-293,-1000,204,-1000,1000,934,-725,122,1000,-650,-1000,-514,-210,249,-728,968,1000,-131,-1000,6,487,173,-23,-1000,-59,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "addClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-651,-972,467,-274,-1000,-461,-48,29,1000,-957,10,250,122,-804,-665,383,1000,-1000,-1000,-1000,776,143,1000,481,-352,649,1000,-70,1000,-936,-390,-1000,-1000,363,140,829,-1000,-607,-828,1000,724,833,858,-931,410,-784,301,489,-1000,1000,-1000,1000,-1000,-84,-749,391,1000,646,965,893,956,574,-412,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "addClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-332,816,40,-1000,485,-576,123,835,587,-461,-991,8,-698,-976,-687,337,322,-853,-542,-205,-500,-267,367,-276,1000,-1000,236,-1000,240,-32,-1000,-237,290,-1000,1000,-864,482,926,-1000,-785,-252,252,642,458,-215,1000,-180,104,-876,71,10,-601,691,627,-981,483,-887,-236,1000,1000,488,-1000,1000,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{154,792,867,397,-825,954,201,1000,-984,65,67,178,-593,119,24,473,-357,310,175,299,-656,88,-435,190,167,118,719,245,111,242,114,-400,24,1000,-324,-448,-28,-175,122,-361,315,-10,-556,-619,-146,504,-132,-30,587,-1000,-282,-78,-486,22,-448,336,-771,188,90,-573,400,609,65,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{-1000,78,-684,-1000,-1000,-16,238,945,-788,1000,-711,-364,-290,340,-1000,1000,-273,356,1000,493,1000,-1000,-1000,1000,1000,960,690,1000,277,-1000,618,1000,732,-1000,-367,1000,-79,123,-464,-1000,-748,435,736,-1000,-46,-660,-1000,-301,-235,-435,-801,-1000,-1000,1000,737,209,-785,941,167,-1000,-146,103,-848,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{576,-942,264,1000,-319,-1000,789,-160,-301,704,1000,-1000,-740,1000,1000,1000,527,-442,-109,787,-1000,368,404,52,150,-1000,-325,1000,280,-9,-329,-1000,-568,-1000,222,-1000,389,299,407,1000,143,-742,-1000,-106,1000,-773,-433,422,-378,870,-669,-1000,-1000,-423,-817,-723,-390,858,-191,-706,20,-25,117,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{-449,789,-81,145,-1000,-458,1000,555,329,-1000,-106,263,-1000,-166,955,-1000,239,-1000,1000,1000,-1000,-95,-528,-262,1000,717,1000,-1000,-1000,-375,353,-614,-550,483,443,-360,-181,515,-1000,-457,-459,-989,720,-331,-1000,-1000,638,546,-198,1000,-1000,-1000,1000,586,-486,389,-101,856,1000,-179,719,-1000,410,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{940,381,419,1000,-1000,-951,402,1000,688,-1000,612,323,207,181,-568,-1000,-1000,-646,542,226,-432,-346,-528,-964,-583,-244,1000,769,79,-375,517,1000,-234,-172,586,195,-181,1000,-1000,-384,1000,-495,962,789,-1000,575,-1000,-441,792,-243,-1000,1000,-1000,-28,93,332,140,842,1000,-265,719,965,-560,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{191,24,-35,260,-506,237,-532,461,-984,-32,-617,-359,-273,188,-1000,-23,518,-382,674,-1000,106,1000,1000,452,523,-227,-813,-230,863,618,-671,795,-173,699,843,1000,403,1000,1000,-222,-1000,487,-802,-25,-128,92,-1000,926,48,1000,-1000,-908,585,-904,-226,924,-297,1000,-509,-543,157,691,327,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{840,141,-855,753,15,-563,-26,-311,-591,310,664,1000,813,526,640,1000,-1000,-849,-1000,-181,400,-627,-1000,320,727,236,1000,-222,-829,-934,1000,-214,760,1000,-1000,916,207,-1000,-169,-726,1000,-926,-1000,1000,983,-1000,1000,-379,997,1000,-456,162,1000,124,52,603,-529,191,1000,6,903,-172,-18,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{305,276,-855,-56,199,167,-823,0,-591,822,267,38,1000,-32,624,225,-171,11,-497,646,400,-581,-824,576,-452,527,1000,-808,737,-697,414,-214,164,363,-86,-576,118,-1000,-630,139,915,-823,-762,215,80,-376,214,-394,833,1000,-1000,-17,721,716,52,1000,197,-444,476,6,1000,-38,-411,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDg=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{-580,-294,1000,855,-217,-1000,114,-365,178,-224,-251,-387,20,-20,36,-283,-52,-510,439,382,-359,145,-639,-887,-95,1000,-1000,-113,-247,677,829,-559,648,-1000,-610,1000,910,-337,-20,740,739,-457,-886,-904,1000,-145,-715,-696,-590,780,-395,308,-573,-417,763,369,325,849,1000,526,-881,-367,341,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDNlOC42NjU=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{-923,-159,-761,1000,-441,244,118,-92,682,-627,-1000,-540,-665,1000,988,-234,-689,-712,1000,117,-245,-118,-467,-1000,-400,425,-567,-162,632,940,421,-1000,497,1000,712,1000,1000,250,1000,-33,456,-312,-603,-674,1000,-84,-803,-1000,-794,1000,-578,-197,-681,336,710,235,1000,855,1000,-277,-207,-873,831,621}));
    }
}
