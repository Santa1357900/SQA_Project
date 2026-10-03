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
            new int[]{492,-846,-444,1000,-1000,408,-257,-1000,-1000,216,-1000,-581,814,-97,-33,-749,-1000,117,147,503,457,698,640,403,-941,636,-621,1000,-400,111,-1000,-874,676,-54,794,77,58,1000,980,121,-1000,-1000,-484,-312,-830,-840,92,-117,-481,1000,-1000,-1000,-1000,550,469,64,1000,-826,-538,950,420,664,695,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-138,-867,-10,-1000,-650,-119,-16,445,-591,-158,-68,133,-407,-392,926,371,685,-134,118,0,-981,-295,181,1000,364,0,-1000,57,-481,-71,247,-366,763,-280,232,-582,-77,316,234,497,1,-622,479,-281,-836,8,488,8,89,118,-567,-813,-964,-166,-834,-980,47,-681,-808,-871,771,136,495,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "after(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{162,-225,1000,868,-478,267,619,78,394,1000,351,-1000,-98,1000,81,151,-228,-1000,-968,1000,-466,520,-876,-1000,206,-1000,1000,210,523,-400,946,-785,1000,-1000,1000,278,-328,-220,500,-755,880,400,-444,-477,-520,1000,-625,614,-1000,162,-887,817,-631,-580,557,-860,-80,245,-1000,1000,-292,-594,778,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{95,-507,-706,87,-388,-636,-633,1000,793,-37,-753,-778,265,-925,838,-250,144,77,-237,564,-6,629,637,375,986,-1000,-52,-882,-410,44,-1000,162,753,-182,682,1000,10,470,887,825,552,775,-103,-508,546,-418,-341,101,41,388,509,974,1000,-684,669,346,991,616,211,1000,-207,272,-131,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "append(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-284,-246,-565,-1000,-34,-1000,-131,353,700,549,-69,-1000,1000,-409,572,-1000,-1000,1000,249,-705,134,-355,769,401,1000,-846,-436,-185,353,-313,928,1000,-554,522,1000,31,-769,1000,-918,532,-237,773,0,-1000,1000,444,277,263,-1000,1000,-210,353,-103,-710,-85,-264,400,246,1000,1000,1000,-1000,-944,654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{413,-957,293,1000,-270,-32,524,-606,-123,-718,-47,-704,-937,-62,3,-1000,-685,-314,-697,-239,-1000,-466,1000,-537,104,1000,-608,-10,-527,570,-882,-232,-193,433,755,-383,468,18,-525,-1000,1000,700,28,1000,1000,-304,-1000,1000,677,317,1000,-1000,462,-1000,-710,-31,1000,400,-393,236,30,-1000,-476,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-423,-543,783,890,-537,-972,213,-694,237,398,-822,830,577,-520,606,-531,51,736,213,-960,-311,-251,-328,333,373,461,775,-25,534,208,499,198,-123,863,-883,190,-397,624,-663,-799,-4,283,-188,-831,-914,185,-699,271,-364,-506,272,517,-318,-111,802,249,477,-666,-747,272,849,209,-620,203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-517,-681,-498,-475,651,410,734,-871,-147,354,1000,80,-1000,-797,514,-371,-11,243,182,72,-615,1000,1000,-1000,18,-853,392,916,-126,898,-1000,-596,-1000,1000,191,-733,-237,-283,1000,1000,723,-230,-960,-769,1000,-1000,-1000,-428,345,542,-637,-742,-1000,601,156,1000,-1000,-397,418,-760,-83,-824,-211,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendTo(org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-369,-786,-1000,-997,-858,443,664,616,596,-552,635,-274,-1000,-94,1000,602,-889,-366,-915,1000,-1000,763,587,544,139,138,515,-200,604,677,971,-300,902,-203,468,-1000,685,-900,34,546,534,-402,-332,-1000,1000,-965,1000,-156,-1000,1000,-1000,-1000,-1000,-571,754,591,644,410,190,-666,-1000,-755,-435,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendTo(org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-382,753,-988,-1000,1000,234,-633,248,-713,37,-207,-105,-879,497,551,119,-493,196,-222,95,10,-621,-818,-300,31,382,-268,-226,1000,1000,1000,1000,-689,-272,-802,-65,808,593,-960,1000,1000,-38,575,-576,341,395,-908,-702,313,-443,838,8,-964,63,713,-366,-1,342,535,-1000,-66,141,-901,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "appendTo(org.jsoup.nodes.Element):org.jsoup.nodes.Element",
            new int[]{-1000,-861,-1000,356,169,560,391,1000,1000,-474,1000,52,162,380,-129,1000,151,1000,1000,-446,-369,1000,-660,228,1000,224,890,1000,1000,1000,1000,-851,1000,33,-596,176,437,484,-519,1000,-860,-710,388,-1000,1000,738,483,206,-1000,678,-77,1000,881,-369,564,-518,-661,-1000,1000,-858,1000,-1000,-137,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,boolean):org.jsoup.nodes.Element",
            new int[]{443,852,670,612,1000,-70,617,1000,396,1000,624,-492,1000,594,142,675,-336,-780,-659,-785,828,-297,-618,547,134,-347,-10,715,293,-344,163,503,570,596,110,-472,-424,1000,417,-345,1000,377,548,-564,-27,34,224,-39,86,227,-1000,-635,-89,25,-1000,-1000,516,248,-778,-418,199,132,-168,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{409,-57,-956,-1000,-1000,-291,-351,1000,-1000,1000,269,-402,-294,-1000,-1000,-83,1000,1000,1000,754,1000,-754,-108,1000,-1000,-196,-1000,-289,514,550,-730,-319,-314,1000,913,-115,-648,-346,-1000,-78,-300,-1000,598,-33,-384,-1000,777,-291,-700,574,496,-1000,605,25,1000,-1000,-1000,-1000,-143,1000,668,-394,506,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attr(java.lang.String,java.lang.String):org.jsoup.nodes.Element",
            new int[]{-149,-702,-994,748,-1000,1,-330,-327,-679,378,-378,-433,-931,-400,258,-257,-1000,926,20,-413,457,-1000,135,403,539,-258,114,-318,376,-1000,-730,-139,208,-444,766,-906,-309,-74,16,-305,-91,-949,-903,-967,-346,-517,20,1000,-111,-524,99,-743,188,-158,400,-44,-546,-532,-1000,660,-30,-321,1000,751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attributes():org.jsoup.nodes.Attributes",
            new int[]{464,576,812,1000,802,-228,-176,-167,849,384,-151,-397,-747,764,-19,1000,220,617,451,-200,449,-411,-181,-485,-13,-544,268,-1000,837,456,-407,1000,-773,-637,123,36,-733,1000,-713,868,-254,-369,-703,-275,1000,391,-1000,-280,-810,-776,-287,407,756,556,-607,-395,77,-1000,-1000,681,448,-1000,-80,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "attributes():org.jsoup.nodes.Attributes",
            new int[]{964,360,-788,70,897,-499,-302,-451,984,-225,-972,-213,827,576,-502,338,349,-193,1000,642,325,551,-923,354,-765,-279,769,198,-630,1000,-45,-652,582,-629,-187,-852,170,-752,54,72,-1000,-745,631,634,316,647,-828,733,-543,-458,-416,1000,871,489,-847,-1000,-245,154,-626,-969,1000,-456,-157,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "baseUri():java.lang.String",
            new int[]{-296,-894,809,-448,493,548,-792,-522,509,-20,53,-128,1000,210,255,-482,493,-315,-1000,-785,-426,-861,631,116,-521,-875,863,1000,-467,-387,602,-252,-951,305,715,-840,687,302,-319,-162,-1000,195,-314,-667,1000,825,-196,473,213,-815,1000,-1000,-867,-1000,-577,-871,-154,-316,754,328,261,-119,-850,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "baseUri():java.lang.String",
            new int[]{-733,-768,-794,90,122,-806,314,740,-1000,-246,-1000,847,-196,-126,705,956,-982,172,216,-400,-74,-225,351,801,385,1000,163,-860,250,400,-648,885,217,-603,212,139,1000,1000,88,-789,443,-99,644,400,-74,502,481,-1000,402,-207,-480,1000,-183,-840,-26,-1000,400,426,-88,-69,300,735,-120,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,-297,623,634,-1000,607,1000,-871,-1000,-1000,103,144,-633,914,-1000,-292,-165,-616,-1000,274,529,284,956,-177,-503,-309,1000,455,1000,-1000,1000,-440,184,127,-70,395,-1000,644,879,1000,764,-12,-900,-678,1000,309,-340,157,54,-757,-431,-1000,962,1000,-544,169,2,1000,-1000,927,1000,-543,590,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{841,624,-1000,-817,161,-716,-457,-107,234,-210,-1000,-50,-53,-463,163,1000,839,611,-516,-698,129,-364,789,14,344,1000,-1000,88,266,977,-711,-1000,-727,676,-274,555,-841,-504,-1000,483,1000,-52,-300,872,560,-420,982,1000,-1000,-507,-646,-537,1000,1000,-1000,-1000,58,-925,-309,-746,418,-294,-515,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "before(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{1000,-177,236,-952,-126,-99,-1,400,-125,1000,1000,-890,-676,-730,-859,400,441,807,435,-606,1000,-52,-273,-267,-1000,-1000,21,803,1000,117,-722,-24,-627,634,385,-902,-833,-334,-31,1000,-801,44,-1000,1000,1000,223,-267,-349,-748,-153,-400,-256,-400,581,-259,755,76,400,-315,24,194,-208,125,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "child(int):org.jsoup.nodes.Element",
            new int[]{-1000,-138,549,-959,-630,-290,729,289,152,-562,-1000,608,603,-289,555,1000,-1000,1000,-1000,-535,632,-289,-429,-54,61,-1000,-299,-42,806,416,-153,-519,-771,-316,-488,571,136,-891,233,-1000,-400,-455,-521,-329,802,-431,101,321,172,235,-504,286,-63,-303,-746,-292,-493,-1000,-256,-555,-55,988,-465,-575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "childNodeSize():int",
            new int[]{1000,756,-657,-122,993,196,-830,530,-1000,-597,-291,-452,1000,-66,167,-1000,-738,852,-431,303,381,-1000,53,178,1000,1000,-706,829,-190,-98,162,59,-1000,328,715,-118,-119,-1000,-566,1000,431,487,-954,-166,-212,510,409,-157,710,983,1000,-1,1000,-18,-105,-477,-254,-244,498,-563,-718,-1000,-413,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "childNodeSize():int",
            new int[]{-97,-696,492,-962,564,351,685,-757,22,687,-425,484,-21,782,-365,-983,-547,-479,307,391,981,-200,474,-551,-391,-891,558,455,-185,116,942,-272,904,927,-452,-103,750,-91,554,-904,-755,-219,479,264,323,-40,-834,-901,-986,-867,-452,108,-387,-569,-480,762,774,-338,-299,-234,-922,-385,442,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "children():org.jsoup.select.Elements",
            new int[]{183,-420,-901,-117,871,1000,824,843,153,318,433,762,554,86,1000,861,1000,1000,-520,-164,994,-281,331,-1000,-380,1000,554,30,-635,-539,1000,1000,1000,13,-9,-669,723,-815,-942,-263,-1000,-1000,445,676,792,83,-141,-900,-287,-710,-463,-599,89,253,425,1000,-331,-1000,175,-1000,-637,750,471,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "children():org.jsoup.select.Elements",
            new int[]{-257,-207,-836,31,821,51,283,-320,-457,1000,-1000,-765,8,410,476,1000,457,358,1000,429,1000,1000,338,247,-1000,-334,69,-1000,-572,1000,1000,693,598,1000,-241,-811,-1000,319,-591,965,-765,305,-988,-1000,795,-28,-400,-672,-295,-647,-241,-1000,-27,-936,-171,-572,716,-863,-1000,-951,-1000,187,710,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "className():java.lang.String",
            new int[]{-180,477,1000,-1000,83,-747,-786,-994,613,-808,-402,697,-888,-1000,-52,-1000,-540,23,424,-515,-140,1000,-343,367,-228,-717,-133,-875,1000,-436,435,-954,-227,248,-465,-616,-208,75,560,-163,-535,-218,-691,327,-32,1000,-473,-842,-98,408,1000,-656,-597,243,558,-1000,295,543,1000,1000,61,-385,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:java.util.LinkedHashSet", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames():java.util.Set",
            new int[]{828,-411,100,505,-532,-306,128,-1000,636,609,-734,482,-642,363,1000,197,-213,-249,105,263,-326,229,-437,-587,-1000,-827,87,365,755,-1000,-38,-243,517,-683,453,184,385,-16,825,-1000,745,493,173,119,-1000,-512,42,1000,664,-730,263,396,245,175,-707,529,-579,-282,-185,-150,-283,48,-208,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "classNames(java.util.Set):org.jsoup.nodes.Element",
            new int[]{-1000,693,-349,-657,1000,139,-1000,690,-1000,-153,262,627,-66,781,-587,426,-38,-616,-371,705,-1000,408,-648,1000,581,-486,-193,-1000,183,570,-883,-281,1000,1000,702,-83,-502,702,-247,-195,-354,-139,-983,443,-373,384,169,-284,-437,-114,-585,-52,71,-1000,-85,9,-995,-53,-711,17,-577,-554,562,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "clone():org.jsoup.nodes.Element",
            new int[]{-807,-681,-82,-930,-771,332,-55,840,-310,682,-475,395,1000,-646,-536,1000,-736,-287,-530,-612,-555,-837,-596,400,1000,363,-1000,-470,251,1000,373,891,-939,-232,-1000,-223,-439,404,999,-1000,1000,-569,371,1000,780,-1000,-732,821,306,-466,652,828,889,848,444,-789,-453,-608,-1000,-967,-21,-734,66,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "clone():org.jsoup.nodes.Element",
            new int[]{-1000,-267,780,-240,-1000,-224,1000,-626,-230,-631,-1000,741,-1000,1000,827,-1000,-1000,-321,-883,-655,-863,1000,-275,-1000,-1000,338,-42,-1000,-110,-1000,-1000,1000,104,-979,-1000,-723,1000,-1000,-510,-1000,574,354,1000,581,1000,1000,1000,197,1000,1000,1000,1000,1000,981,-879,-1000,-1000,-1000,440,1000,-1000,-893,1000,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:NjZ3LisweDgwMDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{422,-285,-913,211,-1000,78,1000,787,-536,333,403,-358,-277,294,-356,-7,675,1000,12,-135,1000,1000,250,436,512,-164,-404,564,-1000,287,-379,-834,-202,384,-1000,1000,-661,-591,-260,745,1000,-1000,-265,1000,957,-1000,-1000,-1000,782,507,-1000,-965,957,-550,-1000,42,-1000,-1000,781,-593,-595,-1000,-399,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:LS0xMDAwZA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "cssSelector():java.lang.String",
            new int[]{1000,-744,-967,-1000,159,-1000,-607,397,1000,-1000,150,660,-295,-402,387,-871,1000,-7,805,556,-725,-1000,-168,1000,-1000,-1000,-711,618,-1000,1000,1000,270,-215,63,400,-1000,257,168,189,1000,-37,-47,1000,-431,406,211,798,-242,174,-1000,118,-394,-915,228,-1000,1000,1000,586,1000,-1000,1000,624,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "data():java.lang.String",
            new int[]{-383,-522,676,-98,-218,724,-757,1000,-994,-1000,-1000,-446,-1000,490,-72,-405,1000,1000,148,1000,1000,-794,-664,1000,-905,189,-147,-490,-217,-1000,-373,639,-1000,330,650,-1000,-1000,1000,707,-1000,325,-96,1000,-1000,-220,10,769,-1000,-388,1000,-137,713,1000,-1000,-535,-1000,-1000,-1000,748,1000,-100,1000,-959,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataNodes():java.util.List",
            new int[]{1000,399,493,-1000,-613,-327,-399,-1000,-283,-1000,-146,438,-1000,1000,701,-1000,943,-552,1000,-296,-986,-1000,-523,-100,-583,-557,858,1000,1000,-807,-822,323,-304,401,1000,1000,-1000,-563,50,949,1000,1000,-541,1000,-768,181,-1000,1000,649,-154,-1000,-856,-500,289,884,1000,686,-281,-1000,-323,1000,456,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$Dataset", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "dataset():java.util.Map",
            new int[]{1000,-621,996,1000,76,679,-327,-242,-89,-278,403,-1000,-1000,-1000,-1000,-210,346,1000,-100,103,-635,612,1000,-462,-291,1000,-888,921,265,377,-65,805,-736,64,-292,1000,-304,649,-714,646,-999,860,1000,-777,-91,-187,250,308,-1000,-374,-1000,934,1000,-343,-255,-361,433,-43,868,461,-128,283,-910,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "elementSiblingIndex():int",
            new int[]{785,690,629,102,849,66,529,889,-898,-359,811,111,-797,739,757,132,95,-695,-529,-349,954,851,556,584,123,-507,-225,-822,-207,19,-212,-287,-420,-866,-814,-618,-409,-202,-692,754,-888,951,-374,-400,607,-207,823,815,374,-720,134,-349,920,-480,4,-441,-161,354,649,-450,-126,-434,-260,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "empty():org.jsoup.nodes.Element",
            new int[]{-99,276,606,-226,-436,473,377,-92,-112,-351,1000,133,542,-506,563,1000,-208,746,952,583,527,-264,-570,260,-738,-253,-647,-401,721,-347,-117,88,13,144,151,115,183,1000,1000,-71,350,-107,516,-200,-546,758,-52,-323,793,1000,974,-462,-261,1000,688,808,-566,-75,1000,275,-1000,-1000,-1000,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "firstElementSibling():org.jsoup.nodes.Element",
            new int[]{-727,-966,184,-1000,-837,302,484,868,286,-420,-304,-1000,-852,891,-563,185,1000,-386,-1000,-33,751,-466,-1000,-200,-349,374,-910,-911,-252,-126,-775,272,-92,-308,-876,1000,-586,-534,475,-146,445,844,175,1000,1000,-1000,-432,90,567,-687,-644,1000,-774,-561,149,-1000,-373,-75,-18,-113,-723,49,1000,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "firstElementSibling():org.jsoup.nodes.Element",
            new int[]{-1000,-543,-419,-759,-832,-274,97,298,677,-382,-752,-509,-997,435,-372,1000,562,-106,203,-108,3,-213,-914,-273,-823,222,-716,-518,122,272,-759,-550,920,-221,-1000,135,444,-164,236,-588,-104,194,-256,759,734,-762,-400,-312,-477,-1000,-271,-318,-769,-802,481,-1000,315,-266,-105,-422,1000,-613,896,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getAllElements():org.jsoup.select.Elements",
            new int[]{-696,-378,-355,-192,-191,417,116,1000,-196,866,-937,1000,-650,-125,-270,264,1000,920,1000,1000,-1000,280,-217,438,-162,1000,-953,-610,191,-1000,643,-84,891,-51,-1000,-822,-1000,431,-428,1000,545,-431,-514,905,-1000,-612,1000,548,-1000,-873,321,-156,140,-668,770,-1000,357,252,871,574,-25,287,-239,547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementById(java.lang.String):org.jsoup.nodes.Element",
            new int[]{873,762,-808,10,-496,-402,598,-262,676,345,-179,-666,1000,-596,-192,174,-1000,-939,-995,518,-1000,-621,1000,-450,1000,-78,-1000,-1000,1000,-1000,633,753,600,-861,-971,-109,909,42,1000,-1000,975,870,-457,-1000,-440,902,243,1000,-800,379,-1000,907,1000,-1000,-1000,1000,781,-1000,-1000,516,606,1000,78,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttribute(java.lang.String):org.jsoup.select.Elements",
            new int[]{619,24,-199,85,-106,-771,-566,283,-665,-181,-111,-886,806,-869,385,96,982,-963,-124,76,-450,-550,-2,411,353,247,22,780,-811,-517,557,799,-618,-198,57,765,-68,-224,799,914,-886,621,948,359,697,-842,547,530,623,-759,-171,-619,953,-88,156,-110,-847,-484,994,-557,372,703,921,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeStarting(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-636,1000,-305,1000,1000,-372,-376,-376,-304,-491,417,513,939,1000,-965,-315,-1000,167,-1000,441,-8,658,-774,903,-1000,1000,-535,630,-509,-162,-998,559,901,-909,-1000,-494,-941,707,-485,187,473,-528,1000,159,-1000,-826,502,-556,-460,-1000,386,77,-346,-1000,-295,1000,-323,-1000,-1000,-599,532,933,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValue(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,204,-1000,-59,-200,1000,-832,-37,360,-1000,272,774,-1000,-1000,809,68,-370,299,-613,-232,116,1000,-1000,630,0,1000,-62,1000,-991,-400,199,346,-25,-1000,764,-246,-302,1000,-37,-1000,-1000,1000,235,1000,1000,236,-1000,1000,-1000,-490,-445,-1000,1000,-1000,1000,-1000,-193,-1000,-448,336,-1000,-533,-572,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{920,-528,-1000,1000,-359,-909,412,818,116,441,-1000,1000,-1000,-577,-460,-872,409,-843,612,1000,1000,-243,1000,1000,-161,-454,1000,-1000,-1000,-1000,634,-911,1000,468,-648,230,-1000,280,-58,762,22,949,1000,-1000,-67,289,574,-365,191,1000,-406,-319,-374,-187,-456,772,1000,351,-581,-876,-761,-626,-1000,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueContaining(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,381,-17,525,1000,-796,1000,285,-1000,-47,139,1000,-72,-99,-289,-1000,-1000,-597,-859,-1000,-543,-44,-606,-788,70,-553,520,-1000,-655,-681,-82,461,1000,350,-748,-102,609,-1000,936,1000,-33,-285,732,-1000,-283,834,-1000,-217,-700,-1000,1000,-169,-603,-1000,-1000,210,739,-220,-527,1000,1000,452,377,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueEnding(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-683,-276,-429,-416,852,823,293,305,974,-765,-1000,-941,413,-1000,-136,-20,-941,-558,1000,-13,1000,-555,-1000,68,-600,-1000,-160,682,508,159,-1000,-458,1000,723,-577,1000,-82,1000,1000,257,862,3,-318,-292,-852,1000,848,-294,668,1000,-1000,-1000,-272,88,758,-1000,1000,515,-989,363,79,-348,-627,541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,702,-284,355,171,639,353,133,-463,-670,-1000,-1000,-1000,375,777,-992,503,-873,-250,-41,-669,-475,679,397,755,-205,-325,-572,266,-780,-252,-665,5,992,-1000,-468,-431,603,-320,-34,26,1000,-461,-466,-1000,-398,-826,-61,-1000,109,31,1000,-770,1000,976,-808,-563,785,88,551,374,-1000,-695,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-534,-171,87,-659,-1000,-143,-98,-1000,-1000,801,1000,849,-1000,-462,-1000,-255,-1000,-235,-1000,-1000,1000,169,-518,579,-1000,1000,297,-39,-1000,-370,1000,-1000,-79,559,-1000,-1000,1000,-422,25,1000,-1000,1000,-580,-1000,-1000,1000,-363,1000,-981,-1000,-347,1000,1000,717,-1000,-1000,-356,-848,-523,-58,1000,1000,1000,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{1000,42,344,644,-1000,232,-407,289,-1000,124,851,345,223,207,1000,614,1000,1000,127,266,1000,-528,435,-529,-1000,1000,924,828,-716,-103,570,1000,-1000,324,556,-157,-1000,608,138,-1000,608,-157,-427,22,-1000,-122,-1000,-986,-70,-500,-811,-99,1000,-201,-1000,-505,-1000,-897,-729,-467,608,822,-1000,406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueMatching(java.lang.String,java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{914,-15,510,289,-1000,351,-467,83,-1000,-850,916,1000,-107,392,1000,1000,1000,617,553,-89,1000,-1000,1000,-1000,-501,1000,1000,-35,-1000,1000,377,1000,-1000,-714,638,319,-1000,673,189,-1000,-581,1000,-609,900,-888,-132,-827,-1000,-1000,-1000,-859,-170,732,-84,-323,-1000,-1000,-550,-1000,-431,-373,170,-584,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueNot(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{958,-48,1000,-1000,-538,-1000,513,-987,-954,-158,900,1000,-546,-1000,-200,-734,1000,-650,1000,-390,-206,-882,-493,-150,287,158,232,-1000,-515,580,-1000,-239,-412,1000,-250,629,-966,-696,-675,835,-1000,595,568,-232,881,-713,-17,506,-984,928,-178,-1000,-123,-756,811,183,-1000,-561,-1000,560,855,-84,545,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueStarting(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-282,501,-474,354,-140,-751,-606,333,92,484,-415,697,-120,-103,908,-515,-882,112,554,102,167,469,-918,-665,990,-529,-372,-819,-382,283,-95,7,-807,379,502,-630,202,-114,-749,-521,115,-540,949,722,660,-338,637,930,-792,-324,785,132,-673,-11,247,450,491,-856,-366,451,122,-935,-91,818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByAttributeValueStarting(java.lang.String,java.lang.String):org.jsoup.select.Elements",
            new int[]{-954,900,-676,102,51,151,-804,1000,-1000,-572,-1000,-1000,798,735,-1000,-691,-719,-1000,670,976,-1000,-1000,-216,-992,-1000,1000,602,510,1000,-526,-449,513,1000,566,494,187,1000,-185,-1000,1000,-1000,-980,-996,251,1000,396,-161,171,-561,914,455,-162,-1000,166,1000,-344,-730,1000,1000,1000,41,658,-223,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,147,-281,1000,-950,-226,159,-538,1000,1000,-574,-657,-9,1000,-291,-1000,1000,992,745,1000,-1000,-793,582,-1000,-580,-1000,-960,-243,-862,1000,872,-323,-1000,-1000,845,6,-473,-1000,-1000,-1000,1000,-58,0,-277,1000,-1000,548,-201,366,-559,-705,-234,1000,-500,-1000,-581,-353,-282,1000,1000,370,-722,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,42,1000,1000,-591,1000,-203,-321,-1000,-221,540,799,-1000,778,1000,-1000,387,743,96,-749,-555,813,1000,1000,1000,363,301,635,833,1000,-231,53,696,900,-830,179,673,-735,-246,-835,1000,393,765,-1000,1000,-1000,1000,358,1000,267,-579,94,1000,238,660,-1000,-1000,1000,-608,370,-1000,1000,367,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,147,-281,1000,-950,-226,159,-538,1000,1000,-574,-657,-9,1000,-291,-1000,880,992,1000,1000,1000,-793,582,-1000,-580,-1000,-960,-243,-862,1000,872,-323,-1000,-360,845,6,-473,-1000,-1000,-244,1000,-58,0,-824,-20,-1000,548,-201,563,-559,-913,-234,1000,-500,-1000,-581,-353,-282,1000,1000,370,-722,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,150,846,1000,-167,845,-273,-516,-632,1000,620,456,-1000,778,1000,-1000,83,743,298,-626,-555,-1000,1000,-1000,1000,530,299,489,666,1000,340,-355,523,504,-1000,20,673,-234,-820,-835,1000,843,765,-1000,753,-1000,1000,358,1000,-559,-729,94,1000,518,410,-468,-530,1000,-835,1000,-1000,871,-116,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,555,939,1000,-1000,1000,-7,-159,-1000,-745,162,318,-1000,1000,768,-1000,398,743,-64,-626,124,390,-400,928,1000,-200,188,500,666,1000,-712,769,523,448,-1000,165,673,-1000,580,-895,1000,378,1000,-1000,1000,-1000,1000,399,437,216,-271,985,217,-699,1000,-1000,-1000,678,290,-47,-572,877,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-273,-1000,-213,770,-1000,408,-189,1000,1000,238,-804,1000,-1000,-237,1000,-985,-1000,1000,1000,1000,-815,1000,-1000,-1000,379,273,-274,-1000,-1000,1000,-919,-1000,-458,596,-239,-1000,739,-1000,1000,-1000,626,-1000,1000,-1000,1000,-212,-1000,454,-1000,-133,-1000,926,1000,-1000,988,595,-1000,-1000,825,1000,-1000,-677,54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,-405,-121,-869,-614,-476,1000,-766,107,831,162,-83,1000,-1000,-618,847,-1000,-1000,1000,947,1000,-244,278,928,-932,315,188,-297,-499,-910,-476,-1000,-878,1000,717,-828,-1000,300,-754,-895,-1000,874,-799,1000,-1000,1000,-970,-1000,437,-862,1000,-589,-58,623,-728,46,-986,-934,-1000,832,628,-852,106,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByClass(java.lang.String):org.jsoup.select.Elements",
            new int[]{-801,555,-436,1000,-1000,-374,228,-1000,-83,833,-1000,70,294,1000,-747,-1000,961,-943,674,976,-1000,-1000,-400,-1000,-838,-1000,188,-1000,-563,1000,102,769,-1000,1000,600,-748,-629,-1000,-404,293,19,856,543,270,-1000,-1000,1000,399,-1000,-691,1000,743,217,-699,740,-845,-1000,150,1000,1000,559,-985,968,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexEquals(int):org.jsoup.select.Elements",
            new int[]{923,3,652,418,-71,-435,134,-7,-20,-1000,-1000,1000,-1000,1000,-782,1000,-269,-491,-294,-122,-706,665,-761,-1000,-1000,-301,-865,-177,-997,-97,-737,-917,6,-1000,-840,811,111,-1000,-754,1000,461,-45,154,1000,-60,1000,-1000,779,-681,906,522,1000,357,738,-755,-206,-1000,1000,-1000,342,-1000,800,351,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexEquals(int):org.jsoup.select.Elements",
            new int[]{-315,318,780,-312,-398,1,-312,1000,-1000,333,1000,-3,399,1000,112,1000,-816,773,-444,-213,250,-807,1000,1000,533,652,139,-1000,628,716,280,-1000,597,841,-138,-19,-7,702,-1000,-1000,-1000,298,183,1000,-558,-1000,364,1000,259,-995,109,-1000,-1000,-617,565,-554,284,-1000,138,-318,-1000,1000,513,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexGreaterThan(int):org.jsoup.select.Elements",
            new int[]{-975,516,-755,827,-409,-362,815,-1000,-1000,-467,-954,-167,829,-1000,-169,-293,-525,-932,1000,483,-137,-1000,-1000,1000,-1000,29,936,-542,1000,1000,1000,837,93,457,-439,806,787,966,-1000,139,652,481,551,-922,-491,-964,1000,1000,-249,-835,-358,1000,634,267,-955,1000,589,143,1000,-623,-142,-82,-605,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByIndexLessThan(int):org.jsoup.select.Elements",
            new int[]{791,-39,1000,-308,-1000,1000,49,-992,1000,-1000,-1000,-208,-1000,1000,-1000,-1000,405,1000,1000,758,1000,1000,-806,-487,-198,748,595,1000,404,1000,1000,213,-913,690,-422,981,798,-1000,-1000,-1000,527,354,-729,-1000,1000,-741,510,1000,-1000,733,-1000,-1000,-365,1000,-155,300,1000,-945,-1000,1000,-658,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsByTag(java.lang.String):org.jsoup.select.Elements",
            new int[]{-1000,114,303,1000,339,-631,-489,426,231,1000,797,-1000,152,819,190,-677,-512,-1000,-826,-803,523,-1000,-638,1000,300,974,527,-997,-956,346,-1000,-152,-977,-508,-270,789,-801,-533,962,-1000,457,-677,1000,549,909,529,370,130,-402,-1000,-266,388,-876,345,530,-10,-726,479,686,-1000,275,873,161,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{277,372,599,1000,-372,149,453,1000,-443,-1000,-963,-117,-1000,-976,-154,1000,224,1000,-1000,1000,-1000,-825,-638,-1000,-487,508,1000,-1000,-544,-30,175,-1000,1000,126,438,548,168,-407,-1000,1000,799,635,-974,345,-192,-480,453,82,852,1000,22,735,1000,-119,-479,-1000,-196,359,-773,1000,1000,1000,-503,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-141,282,793,-347,725,1000,647,1000,817,371,-1000,308,295,1000,282,1000,-986,-243,1000,1000,-1000,-922,486,469,289,1000,-1000,-1000,973,437,-664,676,508,343,-31,-1000,831,-1000,160,-1000,-775,-974,-1000,1000,-425,-466,-1000,271,104,387,825,1000,-632,-103,-1000,967,-781,1000,296,1000,480,804,1000,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsContainingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-636,624,1000,475,325,1000,-311,250,1000,304,1000,-841,554,-744,-846,1000,98,81,-132,1000,-275,-958,295,96,-984,-479,-346,-1000,-271,-511,-320,-118,-1000,-903,529,-544,-918,414,220,-1000,-295,179,-243,-1000,-342,-1000,-863,392,737,759,-1000,-534,606,305,986,-187,-1000,1000,1000,63,273,-291,-141,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-334,-69,410,374,-833,213,-176,-309,-276,332,-823,-312,730,969,926,675,108,-348,-699,-667,1000,-1000,109,-6,-567,388,885,33,683,-1000,-664,-1000,-799,-220,-750,1000,241,506,-132,-110,-593,-1000,-96,-176,617,-125,-551,-1000,786,-1000,306,-398,-1000,-338,900,-1000,7,-1000,-979,-652,-1000,-1000,466,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.lang.String):org.jsoup.select.Elements",
            new int[]{789,-525,-532,640,-170,851,-647,1000,-714,-781,-60,884,-594,245,-530,-374,400,-650,-455,-615,-910,981,83,-286,-81,530,-851,-919,-166,1000,334,1000,174,258,125,132,-687,-832,-25,586,-82,613,3,-568,250,672,-527,686,-171,-59,-114,-133,553,819,574,1000,-2,16,770,171,1000,713,-820,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{1000,108,-252,-54,1000,-466,632,1000,537,470,1000,769,300,-541,-291,335,-37,-969,-220,-5,-255,404,-249,-28,-92,752,-7,-287,-685,374,-762,-796,843,-69,402,-1000,-86,107,819,703,161,57,-35,556,-156,920,1000,-286,1000,-454,329,1000,569,-694,368,513,696,603,841,564,688,540,339,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingOwnText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{-888,726,271,359,-1000,-474,-628,1000,-637,-26,-320,1000,-354,1000,1000,-1000,1000,1000,120,-1000,1000,-913,-9,449,45,355,64,-1000,109,1000,-34,1000,-113,-1000,-1000,-206,1000,-1000,-1000,-924,-1000,-509,1000,854,-58,-1000,-1000,-1000,-889,-1000,1000,856,-1000,-390,-1000,-1000,-1000,-1000,-1000,-13,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{-119,-210,670,593,-1000,-832,-188,994,875,522,134,-1000,214,-146,-1000,-772,1000,-1000,-681,752,-637,1000,325,-76,-662,-772,-237,-728,744,580,933,-657,459,641,624,-687,-382,-686,437,-722,-377,-595,-584,80,1000,-1000,144,-94,96,-103,830,1000,84,633,954,899,1000,-240,-1000,646,-439,-819,-1000,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.lang.String):org.jsoup.select.Elements",
            new int[]{1000,-348,1000,251,-1000,-507,-688,-1000,699,390,675,-176,-1000,-691,165,1000,-328,1000,813,69,478,1000,232,1000,13,608,871,-830,-425,-751,-766,1000,-941,-648,1000,829,-734,-966,941,-1000,447,466,-728,1000,490,1000,635,1000,-1000,1000,-1000,800,1000,1000,-574,-525,494,-1000,-691,-185,474,-659,-1000,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "getElementsMatchingText(java.util.regex.Pattern):org.jsoup.select.Elements",
            new int[]{-1000,-675,924,654,-3,674,618,393,-376,1000,-1000,-881,-721,521,-783,324,-1000,615,591,-290,820,1000,-560,-549,-1000,971,312,1000,18,1000,609,-191,197,-1000,-1000,439,-302,-917,1000,236,-236,-1000,-111,1000,-1000,1000,889,1000,-125,-1000,-627,-1000,450,-1000,-1000,120,951,623,-209,-1000,-835,-83,39,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-1000,-66,-429,-1000,-195,-432,353,122,-73,543,525,138,-1000,-59,-139,-495,131,892,721,-242,-1000,181,-1000,-1000,645,1000,-633,793,967,86,56,531,-574,696,-511,-569,804,-582,1000,-988,-988,364,422,256,87,-73,898,854,-248,649,511,84,-367,-1000,242,575,-566,-368,-444,320,-224,877,793,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{450,-504,-1000,1000,866,-896,454,-1000,109,-365,-1000,845,-33,-310,1000,-173,122,963,-1000,713,-889,339,37,-456,1000,-400,1000,-236,-942,-1000,-234,-1000,-156,-1000,-1000,-720,695,-117,-1000,689,1000,191,-485,-27,239,-1000,-1000,-226,358,126,920,229,1000,-949,-214,-1000,-26,-478,375,71,-396,90,952,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-320,987,-24,440,1000,261,-91,730,-555,-46,1000,334,142,-1000,-184,990,620,888,283,23,313,-550,120,596,-213,1000,-477,567,797,-572,1000,664,193,460,488,-223,-974,-588,-443,355,805,136,123,274,130,1000,667,62,-580,1000,-682,-679,185,205,265,46,849,-1000,324,-264,911,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-1000,-738,-849,-1000,-1000,-356,-1000,-564,232,-362,1000,998,664,-452,1000,356,-1000,725,459,667,736,-195,961,-1000,-890,549,408,-970,-631,1000,-1000,-1000,-388,450,-1000,-320,1000,18,216,1000,1000,301,1000,-381,-1000,485,339,-484,985,1000,962,259,738,-1000,276,810,958,-1000,1000,-1000,-223,1000,-460,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-977,126,1000,-965,-972,525,-514,-550,-506,-94,640,-215,373,-38,-734,147,-396,-193,1000,-1000,89,541,-514,-980,818,762,-1000,-1000,-61,1000,-1000,-739,-250,653,-14,-324,422,265,1000,43,1000,-590,425,-148,-915,1000,1000,-10,258,828,540,-289,-737,-659,100,1000,-546,-161,-817,-598,392,751,16,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-97,-342,-1000,841,-508,-394,249,-1000,-14,1000,384,-982,1000,-1000,-1000,1000,-693,0,-469,-191,336,-581,-815,985,973,1000,-964,-1000,-945,-1000,208,967,-1000,-1000,36,-181,958,-741,417,919,1000,304,463,212,-713,1000,1000,287,-1000,267,405,-479,-191,-16,1000,-1000,481,-1000,948,-1000,147,1000,-1000,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasClass(java.lang.String):boolean",
            new int[]{-461,-699,173,-608,-1000,-524,-471,-1000,544,-397,-447,457,260,284,129,-138,-801,170,79,-363,-73,1000,318,-1000,764,20,34,-1000,-1000,634,136,-1000,-1000,-355,-1000,-698,1000,925,610,723,-55,-355,382,-483,-924,-1000,-275,659,1000,110,1000,546,243,-1000,-219,1000,-541,142,-217,-564,-637,363,609,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "hasText():boolean",
            new int[]{136,-558,-711,-406,1000,1000,-281,944,346,97,-753,818,1000,447,-12,434,888,1000,1000,224,-627,1000,-233,677,-1000,735,-713,-1000,380,270,-199,185,-26,-1000,-951,-212,478,-1000,-440,-378,-133,-51,550,400,431,-154,943,148,-450,-1000,759,-579,372,-229,-494,1000,-158,551,-2,1000,-400,582,-582,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html():java.lang.String",
            new int[]{255,699,-21,-1000,-645,201,-397,-12,465,153,-583,-806,-887,749,1000,-1000,1000,317,1000,-369,-760,281,546,-1000,393,696,128,-1000,455,-1000,1000,-1000,-1000,-1000,362,259,-958,200,-21,-649,-131,1000,-743,-1000,-27,-461,1000,-293,357,862,903,44,1000,799,460,-580,1000,-930,1000,415,-443,-524,-250,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.Appendable):java.lang.Appendable",
            new int[]{-1000,-246,-604,267,636,519,-42,349,-209,-823,400,-606,-510,321,-598,-1000,1000,-754,744,361,549,784,127,1000,-519,-884,-809,-358,179,216,196,-648,-323,-515,-734,-216,-248,240,1000,-126,596,-419,278,-73,123,612,-284,-947,280,46,456,0,-400,-374,-35,-642,216,-386,-35,149,144,392,-490,-224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.Appendable):java.lang.Appendable",
            new int[]{-396,-300,-276,-761,1000,176,361,-461,-333,808,-1000,649,294,-671,-138,-877,469,922,497,-696,908,975,1000,623,-1000,-172,351,-690,144,63,959,866,343,206,596,-954,469,-741,59,-382,-361,848,707,-650,428,348,-336,210,1000,131,-404,-886,38,-1000,-1000,4,-9,953,1000,-196,-1000,1000,605,-533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-370,162,1000,34,-514,97,-411,699,-905,287,312,-285,-318,641,957,753,-633,-63,-127,148,-149,268,-101,179,-188,-501,606,-1000,871,1000,485,-1000,1000,-348,464,-357,198,1000,444,191,-385,452,439,-1000,801,68,-1000,758,378,384,388,563,80,212,312,-400,975,-147,1000,-326,-478,-166,98,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "html(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,777,-1000,-1000,-1000,1000,397,-1000,-445,454,-638,1000,1000,-745,783,-1000,-862,-1000,-524,1000,-570,451,-1000,-72,-1000,889,1000,-1000,-562,-1000,1000,-358,398,-601,-1000,773,-549,104,878,936,420,-1000,-1000,661,1000,55,308,1000,-1000,1000,1000,-1000,101,-1000,-983,335,1000,1000,-140,1000,-939,33,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "id():java.lang.String",
            new int[]{-701,-195,-269,-956,829,449,-406,563,-461,728,227,571,-1000,481,-1000,20,-159,-741,1000,-577,1000,-442,376,183,589,968,-337,-1000,1000,-778,391,987,1000,-1000,907,779,1000,1000,621,975,205,680,844,-1000,570,395,-27,-1000,470,-114,525,-1000,483,-376,-845,281,475,-430,355,-132,-657,1000,-188,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "id():java.lang.String",
            new int[]{-924,-129,-116,-960,305,926,698,610,-922,915,-20,982,999,155,-467,923,537,223,887,-789,597,-635,-739,-471,-94,-686,-675,-525,960,-197,728,474,-341,-818,869,946,911,978,-311,885,448,982,425,181,-83,-505,-815,-537,-107,750,991,-969,-127,190,491,511,-429,300,-78,-700,-171,963,-870,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-2,0,1000,-594,1000,-1000,369,-1000,-1000,348,-420,-1000,239,-492,1000,1000,-1000,1000,-813,-914,365,-150,-74,809,1000,776,308,590,-1000,-1000,513,-623,923,-4,-374,1000,-604,574,-27,227,-1000,-1000,1000,-867,-419,-389,596,-579,-1000,-303,-1000,-505,-5,425,1000,210,879,1000,1000,417,173,56,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{-451,99,589,234,1000,-330,1000,-907,1000,1000,-601,78,-541,-1000,1000,920,-1000,-1000,-288,-117,-187,252,378,225,1000,854,769,254,-1000,-749,-598,-374,943,283,-130,730,-822,-132,80,-280,-1000,-1000,830,-586,163,-549,-202,-538,-1000,197,-1000,-820,498,-112,717,-357,1000,1000,633,1000,234,-761,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,java.util.Collection):org.jsoup.nodes.Element",
            new int[]{341,-975,-1000,-933,-400,411,-426,404,-1000,392,466,-947,238,-164,-1000,-1000,1000,1000,1000,162,1000,-946,-1000,675,880,-1000,-1000,971,836,1000,1000,208,-1000,735,184,-496,1000,550,654,125,166,1000,-509,844,-2,899,118,17,-1000,584,1000,1000,-360,-886,-1000,-157,235,-1000,-1000,-176,-621,1000,73,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{916,-387,-949,-967,-77,425,339,-247,118,-325,517,-444,-294,654,935,-43,203,411,25,-288,-402,-916,-473,254,506,319,-695,729,-596,-155,-23,423,-900,121,430,265,645,383,99,513,-947,-621,-146,739,-617,628,301,796,24,-885,368,-427,336,16,141,33,202,244,107,283,-487,415,186,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{1000,-324,430,1000,968,-851,390,69,183,774,-691,-538,-160,-1000,103,-1000,525,39,374,936,-795,576,-1000,1000,628,984,-783,-674,1000,-343,323,529,-1000,-614,38,254,-377,-470,-532,-130,-1000,58,176,-86,1000,-308,1000,-237,681,128,832,-1000,68,-590,438,1000,-187,525,-998,156,703,420,-197,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "insertChildren(int,org.jsoup.nodes.Node[]):org.jsoup.nodes.Element",
            new int[]{800,-234,615,1000,1000,484,588,-1000,1000,1000,1000,-661,-1000,410,-1000,-249,1000,-1000,-866,1000,390,62,1000,-978,120,-315,368,923,981,-1000,834,-1,-1000,1000,-247,-51,-1000,-1000,-38,1000,-1000,277,371,291,1000,-741,306,-1000,-909,-176,-1000,975,-403,954,1000,-699,1000,-509,1000,1000,-29,103,-460,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{350,-732,-1000,90,-791,186,-561,-937,500,433,1000,-1000,396,-1000,-679,-1000,1000,-1000,-1000,-1000,-1000,898,-648,1000,587,134,-1000,551,-1000,491,699,-945,-25,-843,-1000,-1000,808,863,1000,-834,-141,577,-1000,1000,-951,1000,182,1000,-400,-225,884,751,-1000,1000,900,83,-1000,-1000,-316,582,285,-1000,307,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-55,-726,-470,-643,-505,403,1000,-1000,-4,-286,611,-636,244,198,-569,-876,192,89,434,-131,-777,-161,120,-220,-19,-38,-1000,686,-482,197,1000,-332,-556,-1000,-756,-689,983,1000,999,963,-685,50,123,-547,-1000,614,-200,291,1000,783,956,98,-1000,-764,1000,371,-1000,-577,629,255,-85,-301,652,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(java.lang.String):boolean",
            new int[]{-917,351,-772,281,346,-249,1000,-448,-303,-669,-267,292,-784,415,195,216,-221,-911,-124,513,-319,-396,276,-1000,-516,-145,167,719,-761,272,99,79,-1000,767,401,-165,384,-869,54,958,-433,-719,770,-1000,476,-1000,-440,-435,400,934,1000,-898,-1000,-1000,-637,209,-586,590,1000,338,-958,589,234,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "is(org.jsoup.select.Evaluator):boolean",
            new int[]{338,-867,-117,650,-65,-656,389,103,470,-175,-687,-108,714,-677,-517,-389,186,400,549,794,392,684,-768,35,704,603,-652,-337,184,563,565,-553,253,-552,-769,-260,720,-233,16,978,-577,847,-398,505,-539,16,-787,-667,-113,924,-621,635,-457,-155,1,498,-245,-914,-475,-176,-431,-508,842,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "isBlock():boolean",
            new int[]{96,126,953,237,648,-339,534,-937,-439,572,-546,578,682,-298,-97,103,287,-110,-159,894,1000,-236,-1,639,-514,-490,-1000,-839,89,767,-370,1000,91,447,-47,-827,874,-273,697,196,474,525,187,-152,653,-573,848,372,1000,-434,-176,358,851,2,859,-283,-569,599,73,-27,-19,355,-321,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "lastElementSibling():org.jsoup.nodes.Element",
            new int[]{182,-480,-228,-629,847,667,-56,103,1000,-400,-290,400,-1000,-322,525,-482,371,-1000,-251,333,-700,-1000,525,447,683,-213,-359,308,-708,-688,286,150,-655,-315,61,-1000,-283,348,69,291,1000,-79,-1000,331,-203,1000,13,-343,-799,-20,-446,-532,361,775,1000,-1000,-533,104,-1000,94,1000,-925,-710,-530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nextElementSibling():org.jsoup.nodes.Element",
            new int[]{62,-870,-1000,90,-283,-148,894,-476,-702,-1000,432,-97,84,-238,169,-383,-1000,-534,-118,141,1000,213,1000,-1000,393,191,468,264,-1000,992,860,-1000,-677,-447,853,-443,-786,-363,893,1000,-464,435,989,121,1000,-220,668,-1000,-213,-373,253,1000,-1000,-697,-907,-293,734,-1000,50,36,-420,-836,126,-944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nextElementSiblings():org.jsoup.select.Elements",
            new int[]{-1000,111,25,845,-683,-1000,-587,-521,158,1000,-1000,-111,-1000,-1000,379,1000,-1000,932,1000,-1000,317,-526,1000,1000,94,-1000,973,123,-1000,359,1000,1000,-1000,-1000,-699,-757,-521,-968,1000,-785,-42,102,330,1000,-488,-1000,1000,982,-1000,1000,211,-932,1000,-1000,-309,-241,-177,-1000,373,300,-1000,-703,-502,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nodeName():java.lang.String",
            new int[]{432,516,1000,-1000,-88,-962,-516,499,-583,806,1000,1000,-457,-1000,293,930,1000,1000,386,-804,521,631,-907,883,1000,1000,-1000,1000,265,1000,-684,747,-1000,193,-540,639,70,-1000,227,1000,-1000,22,-83,824,-681,809,1000,693,1000,131,-464,997,701,-1000,-677,553,721,1000,-1000,562,162,8,-677,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.String:YQ==", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "nodeName():java.lang.String",
            new int[]{432,564,492,-256,-1000,-799,38,-561,-18,1000,-452,953,-685,-1000,293,1000,167,1000,1000,-203,272,1000,61,1000,-529,1000,-790,290,484,735,-347,-238,-1000,788,-327,543,446,-784,-708,676,-1000,151,-516,-1000,-983,1000,-392,136,950,-272,1000,263,92,-382,-427,553,1000,1000,-1000,793,1000,-8,184,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "ownText():java.lang.String",
            new int[]{-1000,-528,948,798,798,-66,-601,527,-909,-1000,609,-726,-1000,993,413,797,1000,555,-1000,-776,-940,591,14,-1000,1000,78,-359,1000,388,-362,-214,-1000,1000,-1000,304,1000,128,-978,680,-89,-1000,-570,-740,1000,-636,1000,1000,803,-264,1000,984,-689,-604,1000,-616,-631,-1000,-1000,-1000,37,383,1000,877,628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parent():org.jsoup.nodes.Element",
            new int[]{-1000,570,843,-1000,-644,1000,689,-591,-1000,-928,-427,316,-350,817,582,-1000,472,-671,-1000,517,828,-1000,347,582,-1000,-729,377,1000,266,-1000,275,1000,57,236,772,-1000,-960,-1000,-517,-1000,-803,-591,-58,1000,464,100,860,-997,1000,-237,683,-152,658,-302,-895,1000,-1000,-442,-889,-1000,-987,111,719,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "parents():org.jsoup.select.Elements",
            new int[]{-496,-117,-865,656,-868,-354,1000,1000,-442,8,-756,-750,183,-911,913,1000,-1000,188,201,-293,-91,1000,177,-124,-450,-628,1000,-801,113,29,531,577,-787,714,-1000,-882,1000,472,1000,1000,11,-499,-751,-459,-647,-392,-912,1000,-65,-174,-912,-20,181,-869,-1000,-445,-946,-1000,-637,1000,-622,-1000,-754,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-202,-846,319,811,-294,832,457,53,51,-912,-18,492,-687,-523,750,33,-40,208,-942,-270,320,774,-537,-236,530,633,575,593,-983,394,625,286,-625,957,737,963,812,-726,732,403,384,-950,24,-599,101,556,722,-744,740,-41,-617,892,657,-905,44,-466,238,-541,132,633,764,-690,656,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prepend(java.lang.String):org.jsoup.nodes.Element",
            new int[]{1000,-57,-19,338,-712,-428,960,-166,-318,150,-34,689,931,199,290,446,551,400,20,-229,-773,1000,-1000,174,-155,761,-16,151,-392,304,-385,545,-249,749,108,-870,1000,41,433,1000,457,-302,-167,-1000,-238,324,264,548,27,209,-57,204,-629,-1000,-308,166,-865,358,399,64,997,90,-337,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependChild(org.jsoup.nodes.Node):org.jsoup.nodes.Element",
            new int[]{-323,-759,-540,-1000,1000,-1,264,364,-682,206,635,483,-1000,1000,800,-251,1000,-1000,-762,1000,-1000,-1000,1000,1000,1000,-224,-1000,-605,1000,232,187,866,950,24,1000,7,418,-327,-585,-821,74,-940,-305,-1000,544,1000,-1000,-479,1000,-1000,-222,403,-1000,1000,1000,474,311,889,778,-1000,1000,-929,-417,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{841,-252,-809,-838,523,-496,-321,59,454,-848,1000,-652,-1000,-1000,1,768,-845,-1000,-1000,599,-921,730,554,1000,988,-630,-260,1000,-188,-566,29,499,-1000,-1000,817,-378,-688,76,21,745,958,-422,363,-86,1000,-9,72,1000,-95,740,840,581,578,818,141,-1000,-1000,811,1000,-176,-752,-479,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-486,-252,-146,-390,-357,9,-853,-1000,-211,-3,1000,-1000,-13,-1000,-1000,1000,926,-796,-1000,-924,-943,160,196,-144,-1000,330,-1000,265,1000,933,1000,680,-905,1000,-339,-1000,-958,-542,1000,-567,90,528,-469,-78,-900,85,1000,-137,-1000,151,-147,-26,645,-761,-423,532,-853,400,77,-449,1000,-1000,1000,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependElement(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-587,273,172,-72,1000,135,-1000,-228,967,280,-124,-327,392,525,-636,803,264,400,-473,188,-801,-208,-401,-248,-1000,-78,600,105,-1000,-473,-624,461,-622,222,570,-354,-769,994,423,680,-41,71,944,-90,-216,15,-144,536,-255,-509,215,796,-860,1000,-154,316,-26,40,49,-588,-857,-199,24,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "prependText(java.lang.String):org.jsoup.nodes.Element",
            new int[]{30,150,-1000,-1000,1000,-65,263,-492,-680,273,883,-671,-91,986,-657,-1000,400,1000,1000,-509,-189,938,1000,-577,42,1000,428,-1000,1000,1000,774,-838,592,756,-362,-673,559,786,-1000,977,-1000,-99,-322,-1000,1000,4,-617,1000,-612,523,-1000,792,-227,121,245,441,-1000,-1000,-1000,-1000,1000,-689,-701,-876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "previousElementSibling():org.jsoup.nodes.Element",
            new int[]{-202,267,-631,-454,68,124,579,-187,-599,-578,-567,379,-84,-416,-1000,117,1000,-1000,-330,-261,1000,795,185,-762,-91,381,-1000,-777,32,-1000,929,-758,-224,-682,712,524,1000,439,344,440,-1000,-1000,-935,811,399,-94,200,262,-332,-222,-99,-810,-706,-86,367,-196,350,-1000,-838,-144,131,265,1000,295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "previousElementSiblings():org.jsoup.select.Elements",
            new int[]{663,-693,-756,-676,275,-213,729,648,746,-241,441,814,536,974,961,1000,330,-424,233,-1000,128,-360,-416,970,-638,-455,418,-1000,-856,939,-575,-397,378,78,561,-387,838,-202,166,-1000,-92,-49,-551,1000,512,1000,30,566,-991,994,-611,-227,170,-549,-385,49,345,476,-833,-1000,-937,338,880,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "previousElementSiblings():org.jsoup.select.Elements",
            new int[]{867,-783,-420,-175,313,-824,471,555,879,-316,-129,-738,624,-372,96,866,965,-147,-515,-155,696,175,8,-324,845,-383,380,-452,-883,473,221,778,351,-807,994,-511,679,-475,239,-602,-109,-109,977,820,881,-466,65,-628,989,618,-260,295,-441,185,-362,-231,-897,620,168,-826,66,107,269,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "removeClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-317,279,-355,143,1000,497,321,-543,173,33,-698,865,820,-1000,133,1000,400,-1000,1000,-874,1000,-691,-888,1000,1000,1000,-1000,243,-326,-567,-471,1000,-173,717,1000,-115,149,1000,-1000,-581,1000,100,1000,-1000,-64,-1000,-7,836,1000,-10,-1000,1000,1000,55,171,1000,1000,506,196,35,697,402,1000,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "select(java.lang.String):org.jsoup.select.Elements",
            new int[]{781,-24,-199,-389,477,1000,179,-61,-550,-651,-249,859,-143,540,-430,588,-793,310,-520,-400,619,-443,-628,875,117,781,-772,-180,-341,62,-373,545,-251,-23,794,37,1000,554,1000,1000,-333,-738,553,1000,-19,-645,81,-903,-841,-503,1000,-574,-36,-1000,509,53,704,24,1000,509,-853,-688,-961,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-1000,258,-408,-1000,-573,-526,-437,518,1000,17,-91,849,580,-393,33,951,-211,658,-1000,-1000,279,-1000,-126,1000,134,250,-808,-719,738,-1000,376,1000,-90,-391,228,906,-1000,1000,281,-749,1000,-1000,1000,-804,-696,1000,1000,-871,646,1000,415,1000,247,1000,-215,-220,-225,36,-768,-585,-582,-305,-647,684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:org.jsoup.select.Selector$SelectorParseException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "selectFirst(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-642,183,-820,-577,518,-199,64,-950,528,204,-240,911,545,235,17,1000,-1000,368,-910,-978,-553,-107,-603,337,-177,-449,-1000,300,501,-559,799,1000,208,-711,-1000,-822,-870,648,992,-342,-268,-1000,542,-167,102,1000,1000,-764,692,1000,484,903,609,1000,-616,269,142,-1000,53,-1000,-156,-1000,-882,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "shallowClone():org.jsoup.nodes.Element",
            new int[]{661,783,78,503,-307,-377,-428,189,1000,1000,131,857,154,-184,-713,109,385,-330,-1000,951,-327,489,258,-861,-1000,445,-9,1000,-182,-16,-268,-1000,459,180,500,1000,782,-308,-84,1000,158,703,884,-1000,-237,-487,714,-726,203,245,90,126,-39,498,1000,-1000,-62,344,994,72,-348,-771,1000,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "siblingElements():org.jsoup.select.Elements",
            new int[]{152,-261,1000,1000,555,-1000,-316,1000,834,763,-290,994,846,195,-358,-828,1000,865,982,407,180,-996,770,-299,197,-889,-271,870,42,509,-158,190,-715,-1000,370,253,-996,20,709,715,1000,971,638,378,-642,1000,-54,1000,-17,-319,1000,-76,-172,346,-530,590,1000,846,972,-269,-576,943,-229,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.select.Elements", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "siblingElements():org.jsoup.select.Elements",
            new int[]{-832,-357,798,-1000,-1000,651,172,1000,676,832,1000,994,846,352,1000,-1000,-807,-16,-884,-278,-868,605,-617,1000,-1000,-871,1000,790,495,-1000,784,1000,159,-456,-1000,-1000,327,-1000,892,1000,-1000,1000,1000,-1000,-642,-1000,-1000,1000,-1000,-250,1000,-1000,1000,-714,-911,-86,31,-1000,657,-206,-767,1000,816,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.parser.Tag", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tag():org.jsoup.parser.Tag",
            new int[]{-205,384,917,489,-345,-541,373,-841,-715,756,347,232,-442,6,-89,-404,675,745,-868,-187,-119,-857,-253,193,530,-663,-514,330,-193,720,-723,221,-45,751,775,-968,-748,-763,724,932,197,756,487,-11,-656,906,-246,-908,73,-577,942,84,191,837,-955,-147,743,-675,830,0,633,654,-808,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.parser.Tag", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tag():org.jsoup.parser.Tag",
            new int[]{1000,-726,-915,1000,-924,-499,-45,612,897,1000,1000,-527,963,-726,967,-370,1000,-472,-198,589,1000,-1000,-902,711,-255,-1000,-1000,639,-379,-1000,-183,162,1000,-1000,527,839,631,-1000,-907,1000,-771,1000,-127,1000,732,-1000,1000,875,1000,673,-504,406,-1000,253,571,1000,1000,1000,1000,1000,734,155,1000,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.String:X2ZQL2I=", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName():java.lang.String",
            new int[]{-209,264,815,1000,-1000,755,-1000,938,-91,280,650,153,-596,410,-620,64,108,1000,554,1000,-266,-1000,-546,303,-156,-723,-1000,522,-58,-1000,-81,1000,959,888,-237,-1000,-749,59,-422,385,-168,-428,-1000,309,-786,544,-276,-398,832,1000,-190,-1000,855,-16,1000,99,357,819,963,1000,-374,-797,-354,710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "tagName(java.lang.String):org.jsoup.nodes.Element",
            new int[]{550,-612,-377,-1000,100,-12,-982,-917,680,1000,-278,-450,922,-1000,1000,89,1000,-222,-1000,198,277,33,-294,-54,754,-234,-668,-282,-528,-650,115,-787,1000,39,-381,295,-1000,-995,931,545,1000,624,-984,-783,764,-1000,-988,648,-513,1000,1000,-463,-1000,726,187,-394,607,-1000,-322,362,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text():java.lang.String",
            new int[]{340,90,-582,1000,1000,678,-21,1000,191,1000,-1000,-277,734,-1000,592,945,-935,-471,-1000,-245,-1000,-72,-1000,-1000,1000,607,-1000,1000,-281,-1000,-516,-1000,391,1000,-1000,1000,1000,54,933,1000,-608,-1000,373,1000,72,1000,-364,1000,1000,-189,503,1000,-956,1000,-1000,1000,-1000,-1000,78,-1000,574,1000,120,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "text(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-13,168,143,652,-854,-608,-169,-61,701,364,-808,-510,-489,-357,974,866,-385,-983,-419,618,-777,943,-357,259,-916,-526,434,490,0,497,692,-993,-777,-934,-612,-635,-14,565,577,689,-459,509,558,315,720,899,-712,-143,257,-790,-762,-350,-106,-658,-326,-967,-359,142,770,504,468,500,-162,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "textNodes():java.util.List",
            new int[]{1000,-147,1000,401,396,-51,128,33,372,-40,610,1000,-517,644,-1000,1000,219,1000,-1000,170,91,549,-250,1000,-757,-536,592,1000,-676,494,-1000,1000,1000,943,1000,-788,-203,-1000,-680,1000,1000,-691,7,738,319,1000,-1000,-235,-1000,1000,990,-82,670,145,-1000,1000,1000,709,-1000,-62,-1000,636,799,699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "toggleClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-756,264,-130,-939,-994,-536,-769,-577,-1000,-168,503,1000,403,105,1000,315,-759,1000,851,18,469,861,448,-961,-163,95,659,199,285,-64,276,-70,-55,-1000,782,-825,-661,-908,-400,439,-1000,855,-605,-44,-1000,-1000,1000,1000,231,-351,-557,-489,-208,-301,282,897,-676,-1000,324,114,-39,-105,788,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val():java.lang.String",
            new int[]{-1000,-39,564,535,-1000,-42,-547,-210,-23,95,155,653,263,-759,-451,1000,1000,-396,100,568,284,-173,-1000,82,-850,1000,-859,-741,561,659,-751,-452,-342,46,-1000,1000,-32,-369,236,-599,1000,-413,-366,-554,-402,-941,1000,342,-641,500,-980,-26,533,-271,-1000,-552,-164,411,321,75,265,86,1000,-497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-95,195,-349,898,95,274,-236,-1000,-1000,-1000,-844,437,-383,845,-826,-835,-770,-118,1000,1000,-607,244,-747,757,-1000,-1000,-991,-1000,1000,-794,-1000,-1000,720,1000,1000,-1000,1000,443,-620,-639,26,357,-873,-348,-486,-1000,721,-994,644,-32,1000,-1000,-882,928,-1000,1000,810,1000,491,-1000,900,610,-149,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "val(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-260,711,-148,794,-400,676,24,-659,-104,26,-307,300,-86,-441,105,575,837,747,-758,-875,101,244,-659,-789,-697,311,-488,-9,-36,-1000,1000,-179,-426,-684,-646,-273,212,-527,400,448,729,730,1000,352,-487,-419,152,515,-1000,-875,-347,41,260,669,982,470,-140,-113,-291,-940,-284,-524,-688,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wholeText():java.lang.String",
            new int[]{-669,-255,-189,-420,340,-326,-417,-1000,70,1000,1000,1000,338,1000,210,-274,769,-483,1000,-747,531,420,-114,-614,886,263,-430,-1000,-1000,-1000,-308,397,24,-587,-832,692,-305,-364,-372,787,28,1000,281,921,-46,662,1000,-24,-288,86,903,615,-284,-753,275,-636,-400,643,-1000,-1000,-1000,-433,857,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-235,-630,1000,-565,235,-1000,-226,393,-6,-475,-537,-1000,-687,828,839,74,-1000,-161,-1000,1000,822,1000,-79,-1000,-856,954,1000,-1000,-473,-1000,-1000,-311,1000,7,-35,-994,468,-417,102,949,-1000,-1000,-681,176,198,-794,-693,-1000,-612,431,-1000,-570,-495,1000,-207,-186,-1000,364,-1000,1000,-1000,-15,-551,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "wrap(java.lang.String):org.jsoup.nodes.Element",
            new int[]{533,-243,-417,279,-348,-426,118,-898,-98,54,-444,-1000,571,-1000,-416,-46,2,-636,-646,4,-654,-818,-630,792,1000,687,431,-98,1000,-337,529,-187,-1000,1000,178,1000,959,-1000,1000,634,-169,364,1000,979,1000,892,-613,-1000,1000,340,133,530,-729,-1000,539,1000,891,567,12,203,77,-501,-887,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.String:KzEyNkY=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-695,-126,-590,475,607,-93,813,-63,366,-393,-534,-583,468,-717,-700,181,44,-521,-598,56,550,959,-201,655,53,-522,536,-672,564,-425,-178,-33,886,354,432,663,300,-232,441,-59,-339,266,418,783,-19,-166,683,990,423,437,958,-456,-224,656,796,-387,-54,933,549,-436,773,226,-686,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{50,888,-592,1000,-779,248,-886,423,-177,1000,1000,-1000,748,-1000,-596,-1000,852,-580,-125,1000,959,414,80,926,-546,23,-1000,-319,-716,-508,-1000,461,363,-371,-449,418,494,-436,440,150,105,1000,-1000,-525,-156,224,-318,-365,230,282,-963,504,94,47,-99,-907,-432,-95,303,-567,1000,-655,585,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDAwMDAwMDAwMDAwMDAwMDA=", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{936,201,467,458,326,-662,-38,-556,646,989,727,45,-727,260,-503,820,364,334,488,-21,440,521,685,659,404,343,-504,-71,384,898,-933,-90,936,514,619,-641,-385,319,538,-185,-309,761,-380,-26,-121,334,-129,-621,342,842,-416,-358,958,-673,-988,507,851,-480,-109,801,-480,-907,338,-964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-478,-322,-731,351,-185,979,808,-101,965,302,-361,-293,130,-967,-585,-427,-686,565,384,393,-305,-329,-212,541,518,-885,69,962,592,-678,839,-14,-467,784,-438,858,-596,-592,328,674,-864,514,-262,-940,237,-674,-70,251,669,926,-921,-534,507,435,-663,-534,3,876,-928,5,28,-347,566,-465}));
    }
}
