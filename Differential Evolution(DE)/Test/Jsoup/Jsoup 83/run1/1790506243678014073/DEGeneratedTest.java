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
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "advance():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "advance():void",
            new int[]{-582,-902,107,-759,646,-141,-494,676,575,184,-247,101,-971,-720,-670,-62,-104,509,-576,-588,185,-313,-71,176,-893,338,942,881,800,-685,304,-138,424,-382,589,554,-518,-804,865,-741,429,-928,-455,-562,901,-802,689,835,227,140,-762,-561,-726,879,816,-480,-865,-173,-997,-191,694,-522,-764,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "advance():void",
            new int[]{948,-256,788,-584,-163,-245,-726,-247,-477,357,689,615,840,586,-983,-853,814,69,885,-44,254,138,-63,-437,-395,974,-908,-13,972,432,-530,889,266,-364,-347,-851,-429,383,851,-268,82,-67,540,277,143,-146,-813,-33,-651,-737,-218,923,-25,-989,79,-371,-488,144,686,686,581,-672,422,-395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:IDQzNCA=", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "consumeTo(char):java.lang.String",
            new int[]{-54,-467,-38,434,-48,-92,112,-46,-309,-364,793,-154,-594,177,225,203,716,-185,-942,-670,842,37,39,855,-763,-692,752,-448,164,-459,158,-668,-636,169,-6,374,-560,733,-865,-706,807,-859,-1000,371,-835,-635,767,-596,306,-768,722,521,789,165,422,425,508,301,-489,-17,506,179,-994,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVyLHZhbHVlCixJ", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "consumeTo(char):java.lang.String",
            new int[]{577,495,-911,-169,-425,-213,-568,805,-530,508,-103,1000,-83,-35,-685,459,1000,115,-109,-1000,-332,-675,-1000,140,-21,-580,859,449,-23,-626,1000,-467,-313,307,-446,-108,753,198,-66,-713,-1000,352,521,16,377,-183,620,-664,1000,1000,-66,-901,-721,-18,1000,49,-766,1000,788,196,-40,-809,30,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "consumeTo(char):java.lang.String",
            new int[]{-191,-1000,-1000,-1000,-63,1000,-1000,-538,-132,-1000,-1000,1000,250,-468,350,125,1000,-1000,-1000,-426,-274,1000,-522,1000,1000,1000,1000,-408,1000,-217,-1000,-601,-1000,1000,-1000,676,-40,-32,414,-1000,-1000,-1000,343,-1000,-1000,1000,1000,-1000,967,-1000,-1000,283,-1000,-1000,1000,961,-1000,-1000,-180,-1000,-383,-950,-1000,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVyLA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "consumeToAny(char[]):java.lang.String",
            new int[]{56,-504,-175,806,-727,869,113,598,730,-906,-202,693,-823,683,132,894,-652,-366,872,-243,-531,427,-359,-144,928,557,294,-146,126,-681,502,814,92,836,894,-699,578,650,748,-114,42,574,433,867,-837,-8,592,-761,-631,-739,413,264,-792,510,225,-666,372,709,-859,-744,-360,-356,489,563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "consumeToAny(char[]):java.lang.String",
            new int[]{35,1000,1000,-1000,-1000,-564,-1000,735,427,214,102,-590,-834,-764,-755,-363,694,547,-355,213,300,-867,-1000,1000,-658,-361,816,-1000,1000,487,-185,664,-1000,223,469,180,-290,-128,-253,-347,-729,785,-1000,-380,1000,-643,-337,170,405,420,-202,1000,-1000,741,-81,517,147,-53,-629,200,-153,-409,-775,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "consumeToAny(char[]):java.lang.String",
            new int[]{-588,-712,25,1000,793,-103,-262,694,-898,-412,-173,-421,-357,-981,1000,-64,1000,-312,-913,661,-319,-464,1000,616,639,-451,-423,-73,-739,331,612,266,567,-257,-58,633,228,-363,-597,268,-6,-561,-709,-266,-1000,359,-535,-41,-108,-984,249,-479,-984,-541,382,-52,-504,729,-627,345,175,1000,699,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Character:aA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "current():char",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Character:77+/", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "current():char",
            new int[]{-747,955,353,143,-68,93,-126,-101,-961,287,-42,-327,415,268,814,-8,274,691,-383,13,-401,252,912,-626,909,-619,-815,121,-905,-365,892,300,82,-595,-695,613,-335,99,-862,-668,12,-434,-863,585,-926,-787,-455,-954,393,-5,-318,140,-328,253,751,872,-133,-748,414,614,528,176,706,-671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Character:aA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "current():char",
            new int[]{536,-61,900,-658,300,-176,371,326,-976,441,29,202,-800,-461,-343,-172,-237,-875,-588,-166,-829,-581,-723,486,55,-62,338,-895,-952,723,-956,-458,-616,-818,-317,-51,529,-408,-81,938,180,905,-554,99,-229,178,540,805,834,-315,-367,-737,470,-442,887,-175,-453,695,195,747,-758,546,-960,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "isEmpty():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "isEmpty():boolean",
            new int[]{288,-23,-770,-1000,838,1000,457,1000,-670,-1000,1000,287,361,1000,146,-756,45,615,432,-772,131,108,2,120,895,1000,-1000,-1000,1000,434,836,-913,278,1000,142,-938,-1000,1000,-225,1000,-899,140,1000,-1000,-493,-502,12,-816,174,1000,867,-1000,475,1000,-486,509,484,-1000,-250,-1000,688,250,449,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "isEmpty():boolean",
            new int[]{533,-1000,-507,253,842,715,995,0,-864,-760,-821,452,-393,1000,-602,1000,-1000,0,1000,-116,1000,747,240,-1000,106,-552,-779,0,121,-884,500,144,-216,1000,236,-872,-1000,0,562,1000,-389,48,0,-304,-705,731,-781,351,466,302,-371,-202,458,322,-121,571,673,275,-656,1000,-973,-625,-649,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "pos():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "pos():int",
            new int[]{558,322,-243,-554,-426,984,283,437,-788,167,-348,877,-780,-462,736,600,-776,851,656,381,697,-115,880,528,850,697,-257,496,-705,564,303,765,837,198,922,564,593,751,-325,-857,801,956,755,249,135,329,-577,-776,102,-216,-282,-295,-781,-761,142,309,37,829,839,858,808,206,-577,738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "pos():int",
            new int[]{653,-673,-936,-151,-567,-356,133,170,-839,-729,-295,196,-128,-608,-347,-945,-417,-287,-220,347,258,-130,-447,653,251,196,131,-822,254,-99,832,496,304,934,586,629,236,740,679,880,-874,932,137,-255,726,-491,-504,-984,-331,-363,340,-258,-692,284,-963,-880,462,989,-641,557,-955,944,335,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVyLHZhbHVlCm51bGwsMAphbHBoYSxiZXRhCg==", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "toString():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:MTkzZS00NzA=", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "toString():java.lang.String",
            new int[]{-614,634,-954,193,892,-470,379,113,361,930,364,-469,662,166,-892,46,616,841,9,676,242,-329,588,-498,-472,-762,-969,-125,-316,339,-605,311,-314,984,363,-192,-545,-508,-92,-950,-17,669,-665,719,366,545,755,957,-165,799,271,933,-401,660,872,414,602,-190,381,245,-847,604,650,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:aGVhZGVyLHZhbHVlCi01NTUuOTYzLC0xCmFscGhhLGJldGEK", DEReplay.run(
            "org.jsoup.parser.CharacterReader", "org.jsoup.parser.CharacterReader", "toString():java.lang.String",
            new int[]{-760,-25,-443,-555,-999,-37,-417,542,924,-669,-413,-113,410,775,57,-309,754,-338,-206,675,570,186,-578,-762,708,-895,-406,-497,-131,305,-391,-593,-948,-270,-897,-395,414,-288,-26,-338,163,167,-233,-194,-275,-303,-617,312,536,249,-861,496,-418,647,-8,-752,-945,-951,-979,-941,356,202,166,-440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attribute", DEReplay.run(
            "org.jsoup.nodes.Attribute", "", "createFromEncoded(java.lang.String,java.lang.String):org.jsoup.nodes.Attribute",
            new int[]{-677,-763,331,-673,796,-283,727,997,901,411,97,-308,-396,-42,-199,-525,-127,950,347,-939,630,-707,628,-30,221,209,51,750,-876,130,-978,168,292,384,543,853,950,-462,-232,714,940,533,-705,651,-243,339,-307,-411,-657,-247,-700,-376,359,-816,699,-754,198,787,-585,996,-691,146,-616,-335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.DataNode", DEReplay.run(
            "org.jsoup.nodes.DataNode", "", "createFromEncoded(java.lang.String,java.lang.String):org.jsoup.nodes.DataNode",
            new int[]{891,-828,886,-691,552,-567,-618,601,868,9,117,-753,-362,-709,-260,-163,781,-51,-459,-591,-417,104,-321,-43,378,27,293,548,886,844,772,-370,963,527,132,-647,-119,584,972,-775,-584,289,806,287,-3,27,-313,-889,-29,-604,13,-574,-368,-926,-868,-84,86,145,-44,-162,244,559,-812,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:KzczMC40MjY=", DEReplay.run(
            "org.jsoup.nodes.Entities", "", "unescape(java.lang.String):java.lang.String",
            new int[]{-843,730,-178,-574,-121,353,-6,118,298,310,338,897,-479,-26,579,418,952,903,902,834,-613,-776,144,915,292,150,539,-106,-399,813,-312,-816,-614,537,366,302,225,-365,417,622,-744,880,36,-110,-500,-98,499,-155,-688,-59,597,731,555,632,885,3,481,-229,-795,131,-833,-605,-99,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.TextNode", DEReplay.run(
            "org.jsoup.nodes.TextNode", "", "createFromEncoded(java.lang.String):org.jsoup.nodes.TextNode",
            new int[]{91,267,929,-910,-313,-961,-382,589,-229,234,-737,718,-919,-625,806,-568,132,389,987,-605,-413,-791,386,-837,-899,858,-475,-18,-420,933,4,513,416,-99,591,-113,-930,-160,-541,438,-400,352,-620,-820,-794,-740,535,-393,-336,385,-133,807,-338,-783,393,985,464,-384,740,-496,-929,824,320,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.TextNode", DEReplay.run(
            "org.jsoup.nodes.TextNode", "", "createFromEncoded(java.lang.String,java.lang.String):org.jsoup.nodes.TextNode",
            new int[]{-985,672,586,608,-916,41,-247,-339,938,-112,62,-887,-621,452,614,-512,-488,-723,247,37,-105,221,-980,-941,752,-4,-613,-222,-43,157,-191,391,-22,886,-927,-380,-10,402,623,-128,990,375,864,920,-141,-668,-431,-68,-906,722,284,625,158,41,-77,-668,293,213,-349,851,-990,291,-437,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDEwMw==", DEReplay.run(
            "org.jsoup.parser.Parser", "", "unescapeEntities(java.lang.String,boolean):java.lang.String",
            new int[]{903,-259,447,-760,-518,857,607,-991,230,-214,669,94,-743,241,137,246,-182,-990,-216,327,-151,866,234,-534,-888,430,-360,-361,268,-994,629,633,-96,512,-220,171,652,629,401,570,-151,996,-864,712,-87,-114,-997,351,592,173,-98,804,-480,595,303,-52,-806,-674,877,83,-652,-300,174,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.jsoup.parser.Parser", "", "unescapeEntities(java.lang.String,boolean):java.lang.String",
            new int[]{-872,570,-764,379,-429,-526,263,-211,276,-182,906,-797,-997,982,-407,-822,-297,-535,-745,187,-856,-560,-130,-988,-158,695,-175,-377,-96,-498,-964,-450,-903,-909,901,-446,549,-655,599,-20,830,111,73,-358,-147,-921,-934,248,36,-440,-962,-511,-544,-279,443,708,363,802,-65,765,341,-300,350,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.parser.TokeniserState", "", "valueOf(java.lang.String):org.jsoup.parser.TokeniserState",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("ARRAY:[Lorg.jsoup.parser.TokeniserState;:67:43:ENUM:org.jsoup.parser.TokeniserState$1:Data:63:ENUM:org.jsoup.parser.TokeniserState$2:CharacterReferenceInData:45:ENUM:org.jsoup.parser.TokeniserState$3:Rcdata:65:ENUM:org.jsoup.parser.TokeniserState$4:CharacterReferenceInRcdata:46:ENUM:org.jsoup.parser.TokeniserState$5:Rawtext:49:ENUM:org.jsoup.parser.TokeniserState$6:ScriptData:48:ENUM:org.jsoup.parser.TokeniserState$7:PLAINTEXT:46:ENUM:org.jsoup.parser.TokeniserState$8:TagOpen:49:ENUM:org.jsoup.parser.TokeniserState$9:EndTagOpen:47:ENUM:org.jsoup.parser.TokeniserState$10:TagName:58:ENUM:org.jsoup.parser.TokeniserState$11:RcdataLessthanSign:56:ENUM:org.jsoup.parser.TokeniserState$12:RCDATAEndTagOpen:56:ENUM:org.jsoup.parser.TokeniserState$13:RCDATAEndTagName:59:ENUM:org.jsoup.parser.TokeniserState$14:RawtextLessthanSign:57:ENUM:org.jsoup.parser.TokeniserState$15:RawtextEndTagOpen:57:ENUM:org.jsoup.parser.TokeniserState$16:RawtextEndTagName:62:ENUM:org.jsoup.parser.TokeniserState$17:ScriptDataLessthanSign:60:ENUM:org.jsoup.parser.TokeniserState$18:ScriptDataEndTagOpen:60:ENUM:org.jsoup.parser.TokeniserState$19:ScriptDataEndTagName:61:ENUM:org.jsoup.parser.TokeniserState$20:ScriptDataEscapeStart:65:ENUM:org.jsoup.parser.TokeniserState$21:ScriptDataEscapeStartDash:57:ENUM:org.jsoup.parser.TokeniserState$22:ScriptDataEscaped:61:ENUM:org.jsoup.parser.TokeniserState$23:ScriptDataEscapedDash:65:ENUM:org.jsoup.parser.TokeniserState$24:ScriptDataEscapedDashDash:69:ENUM:org.jsoup.parser.TokeniserState$25:ScriptDataEscapedLessthanSign:67:ENUM:org.jsoup.parser.TokeniserState$26:ScriptDataEscapedEndTagOpen:67:ENUM:org.jsoup.parser.TokeniserState$27:ScriptDataEscapedEndTagName:67:ENUM:org.jsoup.parser.TokeniserState$28:ScriptDataDoubleEscapeStart:63:ENUM:org.jsoup.parser.TokeniserState$29:ScriptDataDoubleEscaped:67:ENUM:org.jsoup.parser.TokeniserState$30:ScriptDataDoubleEscapedDash:71:ENUM:org.jsoup.parser.TokeniserState$31:ScriptDataDoubleEscapedDashDash:75:ENUM:org.jsoup.parser.TokeniserState$32:ScriptDataDoubleEscapedLessthanSign:65:ENUM:org.jsoup.parser.TokeniserState$33:ScriptDataDoubleEscapeEnd:59:ENUM:org.jsoup.parser.TokeniserState$34:BeforeAttributeName:53:ENUM:org.jsoup.parser.TokeniserState$35:AttributeName:58:ENUM:org.jsoup.parser.TokeniserState$36:AfterAttributeName:60:ENUM:org.jsoup.parser.TokeniserState$37:BeforeAttributeValue:67:ENUM:org.jsoup.parser.TokeniserState$38:AttributeValue_doubleQuoted:67:ENUM:org.jsoup.parser.TokeniserState$39:AttributeValue_singleQuoted:63:ENUM:org.jsoup.parser.TokeniserState$40:AttributeValue_unquoted:66:ENUM:org.jsoup.parser.TokeniserState$41:AfterAttributeValue_quoted:59:ENUM:org.jsoup.parser.TokeniserState$42:SelfClosingStartTag:52:ENUM:org.jsoup.parser.TokeniserState$43:BogusComment:61:ENUM:org.jsoup.parser.TokeniserState$44:MarkupDeclarationOpen:52:ENUM:org.jsoup.parser.TokeniserState$45:CommentStart:56:ENUM:org.jsoup.parser.TokeniserState$46:CommentStartDash:47:ENUM:org.jsoup.parser.TokeniserState$47:Comment:54:ENUM:org.jsoup.parser.TokeniserState$48:CommentEndDash:50:ENUM:org.jsoup.parser.TokeniserState$49:CommentEnd:54:ENUM:org.jsoup.parser.TokeniserState$50:CommentEndBang:47:ENUM:org.jsoup.parser.TokeniserState$51:Doctype:57:ENUM:org.jsoup.parser.TokeniserState$52:BeforeDoctypeName:51:ENUM:org.jsoup.parser.TokeniserState$53:DoctypeName:56:ENUM:org.jsoup.parser.TokeniserState$54:AfterDoctypeName:65:ENUM:org.jsoup.parser.TokeniserState$55:AfterDoctypePublicKeyword:69:ENUM:org.jsoup.parser.TokeniserState$56:BeforeDoctypePublicIdentifier:76:ENUM:org.jsoup.parser.TokeniserState$57:DoctypePublicIdentifier_doubleQuoted:76:ENUM:org.jsoup.parser.TokeniserState$58:DoctypePublicIdentifier_singleQuoted:68:ENUM:org.jsoup.parser.TokeniserState$59:AfterDoctypePublicIdentifier:80:ENUM:org.jsoup.parser.TokeniserState$60:BetweenDoctypePublicAndSystemIdentifiers:65:ENUM:org.jsoup.parser.TokeniserState$61:AfterDoctypeSystemKeyword:69:ENUM:org.jsoup.parser.TokeniserState$62:BeforeDoctypeSystemIdentifier:76:ENUM:org.jsoup.parser.TokeniserState$63:DoctypeSystemIdentifier_doubleQuoted:76:ENUM:org.jsoup.parser.TokeniserState$64:DoctypeSystemIdentifier_singleQuoted", DEReplay.run(
            "org.jsoup.parser.TokeniserState", "", "values():org.jsoup.parser.TokeniserState[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{357,987,-597,-47,-6,660,-830,-886,-384,-273,-210,91,881,470,-625,607,946,276,560,-924,-474,-899,-519,-456,-685,136,341,-947,-267,-950,745,-99,-570,-556,-308,913,-743,-300,481,-200,547,-718,-888,964,-580,721,806,-295,604,-982,-859,660,-227,-989,40,-464,-245,-664,299,729,-839,-890,-269,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String,org.jsoup.parser.Parser):org.jsoup.nodes.Document",
            new int[]{536,-10,274,636,637,-152,655,541,-537,185,-754,-632,-586,966,446,785,-129,-686,-626,828,-136,-820,824,38,102,33,-434,349,-728,-933,-133,412,228,-370,-465,162,195,-862,-100,-109,-566,521,157,790,958,454,272,-970,-184,-947,-741,-223,249,-499,-948,-748,571,161,430,-87,-974,129,483,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-251,-651,278,896,-853,-201,991,-530,-595,130,547,-318,-541,-97,-421,289,574,588,791,-635,21,810,607,-751,76,306,522,-893,-860,-234,-105,436,981,-491,986,789,-478,558,-606,773,395,-918,739,910,576,-587,-559,387,-214,-126,-268,859,97,-469,667,-470,-836,608,-162,533,-119,840,730,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{331,730,27,-835,-775,620,424,-316,-720,-744,934,950,1,698,84,692,360,-530,-256,434,399,-332,-954,-74,651,785,-71,-788,672,-633,636,-22,559,777,-608,-491,-951,135,442,916,487,-234,651,75,777,426,937,-797,-716,947,971,891,-431,9,-249,-987,850,265,962,-15,93,-809,339,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-293,29,454,-841,-785,-18,501,220,-805,-393,-924,-905,-391,-468,-175,-569,-480,-943,799,-631,565,560,526,-693,-689,414,252,409,319,597,777,133,719,-699,131,303,380,-259,-424,308,-678,99,-145,937,-49,88,-526,989,-117,966,-143,-606,-339,-7,716,-196,508,-688,665,847,403,-976,-814,-896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String,org.jsoup.parser.Parser):org.jsoup.nodes.Document",
            new int[]{-244,-529,790,728,-61,-816,-621,-966,-795,321,699,-47,-556,-248,-636,-936,499,-278,-338,-619,398,-468,402,446,-57,246,-900,-816,903,-349,719,-263,395,-756,-405,436,111,903,440,-615,-26,68,-595,-323,-462,757,-945,-812,993,-457,-194,99,964,756,892,-175,492,-425,250,-135,-454,-547,833,234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-546,287,-721,-224,347,694,215,652,789,1000,348,-1000,-141,263,-573,-431,1000,563,-728,-1000,355,1000,-965,218,-97,339,-19,-1000,-679,-1000,1000,-601,-520,798,-102,938,-510,665,365,1000,-120,-900,626,23,-1000,1000,-311,-589,373,-1000,-1000,-1000,824,-919,642,-417,-115,217,-639,-668,-871,-880,446,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{850,284,260,642,-167,-48,625,692,-21,327,197,-966,-631,139,473,-842,540,64,103,-967,882,900,-72,-834,-892,-959,-617,710,-970,904,-462,-61,-974,528,578,-413,7,834,364,541,418,-800,-130,-864,-86,-58,735,-58,-354,51,-21,-685,855,115,981,-760,398,-264,243,5,179,-62,177,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-1000,59,-449,1000,-609,182,74,384,-265,990,-449,351,-1000,-608,-93,-1000,1000,322,-571,-713,922,1000,-1000,1000,36,328,556,-1000,37,-723,1000,658,-1000,4,531,-321,-1000,1000,-640,1000,246,-1000,342,-32,-931,1000,-1000,757,1000,-419,-1000,-1000,-453,162,1000,-1000,-208,772,373,-1000,-1000,-1000,209,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{387,371,-851,617,615,213,456,908,-442,-434,691,-123,-461,121,1000,-472,-632,446,-99,175,-797,935,877,-609,-74,941,425,1000,-1000,407,-788,120,935,486,-288,-727,912,-1000,-188,414,-467,1000,-941,12,0,128,27,-217,-776,-748,1000,476,757,-321,287,414,584,-164,598,664,-384,-633,274,-371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-116,-197,-419,-88,37,299,-916,-614,86,-1000,-402,-207,58,-1000,-73,-146,-1000,-623,-1000,646,396,370,-396,-80,-238,812,-1000,315,312,1000,7,-900,-617,-2,519,-1000,1000,651,572,1000,-473,768,-454,-460,984,477,-690,798,-479,-430,-1000,-48,828,-115,611,-609,-306,1000,20,-911,-703,788,312,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-828,-134,733,516,187,-228,579,877,578,1000,1000,-976,96,-398,188,-896,588,348,-491,-947,993,158,-483,1000,735,-5,663,-1000,-684,-1000,1000,788,-597,1000,-56,-447,16,1000,832,-210,1000,-1000,948,104,-1000,1000,-352,514,1000,-193,-1000,-799,-363,626,161,-187,-523,958,-747,-561,-743,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-329,-124,531,749,-58,-520,912,975,748,-909,-941,527,469,101,887,162,343,430,419,550,-468,428,-497,474,171,963,-151,624,-904,281,74,94,-602,884,-975,-192,156,-325,-963,471,747,-79,974,381,992,210,913,435,49,-501,153,41,-536,606,-133,244,-940,-946,-838,-611,454,29,570,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragmentRelaxed(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-169,783,-106,344,-666,690,-628,781,-958,908,-251,996,61,80,10,-54,712,-993,326,580,-376,-311,-193,-966,-739,331,-826,-586,-804,571,341,-2,-590,25,274,153,93,-131,91,-435,41,-367,386,482,549,-370,-6,-408,-68,-468,18,860,-730,-529,-156,232,793,-240,-932,-571,434,819,807,-762}));
    }
}
