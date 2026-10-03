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
            new int[]{664,-759,444,-820,-675,-164,-859,-139,-597,175,-898,459,-965,-831,-233,-641,-904,330,-682,-523,213,867,540,98,202,281,-417,-456,777,-251,531,313,-853,-451,978,-963,412,-224,648,-557,577,-305,514,-649,-601,-780,-178,637,-674,189,995,136,-676,-581,-561,-563,530,-200,979,700,346,124,-159,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "asList():java.util.List",
            new int[]{-663,-245,239,99,-146,839,-479,161,237,-5,-734,-435,192,-519,849,-543,996,912,142,-778,-617,-353,644,950,-724,-612,665,749,469,866,470,-269,929,75,-930,704,-20,733,266,264,-687,386,-491,314,-478,-114,-863,-140,-380,-132,-346,-663,-105,-736,841,-295,371,-89,698,-921,114,-124,494,751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "clone():org.jsoup.nodes.Attributes",
            new int[]{844,-289,-76,745,905,690,237,171,-647,-871,884,-257,924,-177,-397,-25,-766,415,566,-822,-95,-249,921,278,-135,280,394,-900,-605,-469,-34,699,-857,-793,-950,858,35,469,-891,-109,-244,541,-828,-431,-661,977,-656,-866,-730,-383,63,-862,468,550,-407,157,-458,-751,55,-700,-225,-539,-850,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$Dataset", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "dataset():java.util.Map",
            new int[]{155,-923,659,-78,409,441,712,-515,-150,-572,430,365,-26,445,472,93,863,-814,852,617,-76,299,297,312,-667,-242,512,-152,565,-901,-836,-915,390,458,-15,-265,413,-803,-423,-894,-648,760,185,-49,71,395,28,159,-357,368,-418,-175,623,-609,-337,117,-851,-884,-919,262,483,760,216,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "equals(java.lang.Object):boolean",
            new int[]{-54,280,-571,645,-825,505,809,-59,-546,-767,827,-94,481,-452,-992,-649,400,745,-101,-627,-775,-81,261,61,-548,-562,451,-712,21,61,810,458,341,22,59,509,-646,-801,-314,-768,821,686,81,15,-107,-513,796,474,-163,-109,716,-849,-670,-870,156,-590,142,-666,-56,8,204,522,388,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "equals(java.lang.Object):boolean",
            new int[]{-193,659,-571,-915,1000,1000,-94,1000,772,-1000,-1000,-94,481,1000,-281,-888,769,514,-631,-627,20,-81,-234,-347,-453,-50,-559,-59,73,824,-1000,-497,341,-644,640,455,-33,537,-314,-807,666,-14,-754,87,-91,-110,535,396,-801,-1000,-679,-454,848,1000,-848,-680,-18,-285,232,-579,-257,643,210,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "get(java.lang.String):java.lang.String",
            new int[]{-773,-480,-311,-440,-342,-728,-992,-60,378,912,-884,530,99,-11,301,-150,296,94,-562,160,-206,-897,-618,427,580,764,-752,-625,-300,-405,949,-678,-858,181,676,169,-313,643,-587,118,-65,321,19,-501,-98,-502,-53,397,-85,402,220,-274,-126,-366,963,818,-613,698,-538,669,-246,-650,513,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "getIgnoreCase(java.lang.String):java.lang.String",
            new int[]{-352,-679,-841,-569,373,928,-629,-639,372,-461,931,757,826,-531,-307,-346,-375,712,-882,-626,-500,-718,-234,23,262,615,416,726,428,897,-873,358,-172,836,-359,190,-809,619,725,-850,-855,-423,-739,-808,-397,-583,217,653,-922,-180,-21,-591,112,-372,-55,-836,-285,-82,772,-765,458,829,239,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "hasKey(java.lang.String):boolean",
            new int[]{907,-523,344,565,567,1000,-686,834,1000,775,264,477,-392,328,-1000,-69,531,819,47,137,426,-757,-881,-657,1000,697,-763,-550,-734,794,-780,82,-255,-1000,-457,743,1000,1000,238,-805,15,-470,-1000,-709,770,-317,83,-20,-1000,-580,475,-196,527,-497,680,23,-286,1000,369,202,-949,87,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "hasKeyIgnoreCase(java.lang.String):boolean",
            new int[]{-529,-223,-996,-703,486,311,75,-797,18,-1000,739,1000,645,459,1000,206,851,284,177,-396,-669,377,-541,-924,320,705,39,-772,1000,194,-1000,997,-160,1000,-1000,-694,29,-959,-660,729,98,-216,-1000,135,-489,-1000,-314,-35,200,66,-276,-210,-409,-114,-359,-1000,697,793,-525,-823,902,-577,-898,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:IEEyXysvVV9GX0E2NllHX3RqNm89Ii0weDIxIg==", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "html():java.lang.String",
            new int[]{13,-600,-291,-1000,14,-297,767,469,888,2,-1000,204,-4,-725,-1000,-385,-1000,-603,1000,1000,-437,-668,-1000,597,-620,1000,663,-169,-33,245,-533,-964,1000,-860,-673,659,-1000,321,309,1000,-45,889,-104,1000,-947,287,-321,242,-23,-30,915,-173,424,-229,393,810,247,-552,574,1000,-501,228,927,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes$1", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "iterator():java.util.Iterator",
            new int[]{-258,605,-317,-110,-200,-401,663,451,-802,-422,936,386,-870,629,-353,-627,551,-579,679,-250,-877,387,578,-435,66,-519,-296,628,-781,597,-73,161,-825,-52,93,430,-904,459,454,959,-513,523,-756,827,-950,-642,685,-72,-420,651,-145,43,790,-847,21,-876,-46,-517,358,-728,-726,497,255,557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "normalize():void",
            new int[]{520,-116,-772,63,-205,271,947,-264,-611,854,396,789,965,-5,365,-118,993,424,919,502,586,-593,-210,-565,-709,-602,-960,743,-182,624,701,594,845,705,960,942,-899,501,124,-38,-310,-336,75,413,-685,56,214,-365,459,142,998,772,-130,748,-899,-9,-247,157,-62,949,967,416,-157,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(java.lang.String,boolean):org.jsoup.nodes.Attributes",
            new int[]{261,1000,899,1000,278,-134,1000,1000,-646,1000,-935,-791,-803,-45,497,-1000,132,-1000,630,-1000,152,1000,-554,-1000,-547,-1000,400,286,927,319,667,-153,-117,-441,227,436,-568,-1000,47,-2,-642,830,25,-1000,-689,547,-432,-1000,542,866,19,389,-511,-699,-558,1000,872,-296,-626,343,-1000,-265,975,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(java.lang.String,boolean):org.jsoup.nodes.Attributes",
            new int[]{931,520,778,785,-152,339,590,946,-932,622,-499,-303,-610,784,621,-561,-611,-21,352,-893,49,27,270,-905,-124,-330,-602,783,870,-58,735,558,523,37,503,834,-166,-805,145,774,217,880,-178,-835,-857,768,-147,-791,-625,-52,-625,-117,-284,-881,-669,931,360,543,-151,601,-868,63,30,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(java.lang.String,java.lang.String):org.jsoup.nodes.Attributes",
            new int[]{-74,113,869,-865,594,752,-941,541,-246,59,-841,-409,-316,-447,695,387,-243,494,465,305,-488,-714,-474,475,10,1000,222,357,245,-438,-251,-314,368,-253,-976,131,-490,194,-43,698,-877,399,622,-885,935,-136,271,214,-178,399,648,-303,236,962,657,-460,158,344,68,-197,993,534,648,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(org.jsoup.nodes.Attribute):org.jsoup.nodes.Attributes",
            new int[]{-954,449,-301,904,-672,27,686,-509,538,-221,674,-790,457,-709,226,-14,-801,752,-872,23,-406,-916,623,-505,73,-352,-843,-151,-541,-665,-985,-491,557,-66,17,345,-20,-728,-739,-537,-725,-148,786,-458,-939,407,13,291,-344,-830,787,-627,441,475,-9,-377,103,793,-224,302,777,269,713,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Attributes", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "put(org.jsoup.nodes.Attribute):org.jsoup.nodes.Attributes",
            new int[]{-1000,1000,-292,339,-649,-1000,1000,-1000,887,-494,1000,-999,700,-1000,-302,272,-1000,472,747,-420,192,-1000,60,-915,972,-1000,-1000,-584,-1000,-1000,-611,-580,292,81,-666,1000,-637,530,-97,-601,-256,1000,216,-120,-1000,387,-1000,1000,275,-967,208,-316,1000,-436,-737,362,782,1000,-600,970,987,-963,-64,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "remove(java.lang.String):void",
            new int[]{207,381,709,-911,-939,125,89,-364,-242,995,-502,878,-724,-757,-337,-577,617,-789,-580,917,977,-166,712,-849,-9,-963,-892,281,-629,517,-971,573,-818,27,-594,521,-469,985,448,-329,-527,27,-133,-976,-412,-102,-988,-490,869,38,109,-295,308,98,-910,-325,878,717,329,663,503,896,6,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "removeIgnoreCase(java.lang.String):void",
            new int[]{-163,-983,883,-207,434,49,-311,-441,-190,-447,82,-423,700,-763,-657,-503,-840,-395,501,963,-452,-504,813,-387,-382,-493,-756,-527,78,798,308,560,327,778,945,624,-153,76,-254,53,699,827,-260,729,947,803,-778,-729,-525,-807,-346,582,-388,-31,-468,815,272,-345,305,5,899,58,915,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "size():int",
            new int[]{-867,-938,568,257,-494,-506,211,685,-956,997,-128,-360,-696,751,-371,840,434,269,716,-95,337,-622,-223,-45,508,97,722,338,527,-303,-295,741,-544,478,638,-896,441,401,-585,-754,740,-401,522,951,-947,-978,106,-672,-422,984,699,-251,-488,-784,809,-636,-139,192,-652,281,384,-64,508,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:IC0tMHgxZTM9Iis3MDRlOTAxIiAtMHg4PSItLTB4OCI=", DEReplay.run(
            "org.jsoup.nodes.Attributes", "org.jsoup.nodes.Attributes", "toString():java.lang.String",
            new int[]{-40,-298,524,-409,948,458,334,63,-924,-350,55,-260,-729,483,-889,310,-704,602,901,1000,-408,-141,81,-1000,-88,54,-613,-1000,-420,179,1000,630,475,-978,844,-81,892,-1000,561,1000,190,1000,-238,416,369,1000,426,-559,898,515,897,1000,-179,-372,-1000,332,-269,51,1000,-156,383,-823,-175,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String,org.jsoup.parser.Parser):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String,org.jsoup.parser.Parser):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "getValue():java.lang.String",
            new int[]{735,461,327,-928,169,44,492,1000,-267,609,137,409,258,-1000,287,-483,-480,-279,-572,-635,529,180,-203,-161,-779,244,291,484,149,-573,58,28,932,424,428,564,746,511,-311,158,607,650,307,-13,-116,336,-522,-179,-299,272,-938,648,118,-501,887,1000,1000,645,479,544,610,696,-387,-788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4MWJlPSJMRDki", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "html():java.lang.String",
            new int[]{485,-103,317,-253,35,-927,-390,-962,455,207,-921,923,-155,14,-958,151,887,-953,178,879,135,816,443,-533,-874,182,-279,-695,-815,375,-562,-511,598,-883,535,-49,834,-694,-372,-734,-103,435,-4,-233,-446,-467,-906,-189,-809,768,-289,-873,345,-932,-409,-38,592,-877,366,481,-619,944,991,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:LS00MTFlLTc0Nz0iMHg4Ig==", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "html():java.lang.String",
            new int[]{546,-1000,486,-411,-829,-747,-48,-9,975,656,607,-1000,714,696,-1000,629,880,934,342,-130,-18,153,-1000,-49,986,546,916,-1000,-602,-810,-175,-158,367,-1000,238,242,-1000,-441,-662,1000,894,-807,926,-878,-231,802,-488,221,-768,711,-829,147,-574,154,1000,449,225,-1000,273,134,679,-234,-1000,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID|getKey=java.lang.String:LTgx", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "setKey(java.lang.String):void",
            new int[]{951,293,492,-288,-68,388,585,215,491,-775,-326,951,-601,702,-727,974,779,-797,-222,574,-396,922,-81,357,605,224,-549,571,-469,471,-837,-725,768,-9,251,309,760,-95,425,226,349,-634,746,-640,-795,-536,-915,36,-153,-516,-612,528,-597,535,452,867,214,-354,-199,-181,-318,364,-570,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID|getKey=java.lang.String:MHg4", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "setKey(java.lang.String):void",
            new int[]{281,-1000,-968,183,524,827,80,-1000,428,379,-29,1000,744,-914,-494,-1000,-993,-1000,-169,-67,-1000,-738,1000,1000,-1000,4,-1000,717,-419,-741,-1000,-1000,759,107,-1000,-447,764,1000,1000,-868,-1000,1000,-730,773,386,411,171,-1000,-377,922,-1000,412,1000,-1000,812,1000,1000,-1000,1000,276,427,351,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "setValue(java.lang.String):java.lang.String",
            new int[]{766,-575,105,-740,-427,-711,-201,-355,351,-531,-390,648,-360,-604,-671,-666,213,962,-629,-214,621,22,458,824,322,553,18,820,-578,-773,-131,-51,-731,72,-521,-118,886,837,-639,-336,526,694,33,326,-463,992,-647,-373,-857,933,-963,258,-234,210,-329,-172,-946,969,658,978,938,-273,-830,-874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "setValue(java.lang.String):java.lang.String",
            new int[]{826,92,455,99,1000,479,877,724,-663,1000,-869,-672,565,-1000,179,127,1000,222,1000,834,22,-461,996,1000,111,1000,-677,-19,544,100,-1000,218,-400,-92,210,1000,911,-476,527,677,377,-169,1000,1000,-1000,158,871,-592,-125,1000,-381,-30,541,-620,307,1000,-420,-59,-925,488,547,-446,1000,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:NjE0PSI4MjFMIg==", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "toString():java.lang.String",
            new int[]{234,967,-919,16,417,725,257,682,819,942,667,-647,-87,821,24,-715,-910,-365,614,-976,-662,651,974,-575,-568,353,-671,233,-65,-575,-318,-902,37,504,491,-865,202,107,-683,-816,945,329,503,-234,654,-63,989,-586,-688,273,-307,518,217,-957,554,-872,351,510,282,-738,-69,-145,-861,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:NjAwLjA9Ik82NlVfTl9RX18udWUgMDYycF81Tl94Ig==", DEReplay.run(
            "org.jsoup.nodes.Attribute", "org.jsoup.nodes.Attribute", "toString():java.lang.String",
            new int[]{36,952,437,600,-1000,1000,0,-573,0,-236,688,160,-695,-601,866,0,640,416,-419,690,1000,527,149,414,-547,-411,948,121,432,1000,-725,-1000,-803,-1000,336,-1000,-1000,277,-254,-696,208,0,1000,215,-188,136,-421,901,-1000,246,-574,-651,670,131,-455,-1000,-474,-218,-558,570,367,-1000,-287,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{164,-305,510,-690,-1000,-877,366,956,-273,890,-866,-279,777,542,105,1000,716,157,796,1000,928,-176,986,382,-776,-53,-740,-1000,361,1000,323,-392,1000,165,486,-926,395,980,475,510,-149,-504,-832,-74,-35,787,-1000,433,-672,156,196,-1000,-56,83,-1000,954,908,942,400,327,17,-236,1000,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-706,166,-343,-497,27,-125,520,-371,-574,854,389,-433,232,-793,-227,546,-548,-713,-370,-186,-662,751,430,-990,-788,681,472,-3,-766,387,-185,-290,805,-455,-798,205,-749,-868,700,-402,660,-747,417,-145,307,406,-370,525,980,997,938,-624,-314,-27,622,-310,717,-658,-686,-559,-129,-963,288,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{726,-433,-114,-229,642,523,573,-430,-497,-969,-153,829,471,-90,134,-497,-357,686,-557,346,587,429,-510,772,-904,822,915,584,-310,-985,-603,21,-993,872,-525,-712,159,-19,245,170,361,485,716,637,-287,214,622,463,-120,847,703,-226,-418,-64,-880,-809,-676,-515,-489,-244,594,-523,-559,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-514,1000,607,-44,-765,643,-178,374,725,540,388,-786,828,491,769,-574,993,-209,-557,1000,-118,-323,-743,1000,-207,-1000,-920,666,-511,790,1000,-935,-480,540,385,-163,-1000,-151,1000,381,759,770,-858,1000,-781,413,913,1000,-357,-116,148,1000,-521,-114,-641,167,1000,-7,1000,617,323,-472,496,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-188,865,732,-759,-445,412,569,-318,675,-545,-318,206,-663,607,472,-210,373,-44,-232,402,-968,478,788,-258,652,672,179,0,-891,-921,794,-283,-461,-635,840,-538,732,450,426,550,-115,-170,-868,-701,897,-344,-762,-579,-670,408,476,89,268,83,-198,908,-136,-330,-445,-403,-875,433,-414,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-281,219,-274,-1000,-1000,59,351,-475,-974,914,516,-742,958,563,1000,818,1000,338,521,1000,44,105,-448,1000,-22,-308,-355,-956,453,857,717,626,877,1000,-43,874,-1000,-90,664,1000,-256,197,-1000,459,-1000,1000,939,-106,-158,-434,-196,400,-320,-400,-1000,682,183,641,907,850,1000,-860,1000,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{181,5,637,428,-1000,-523,-646,-176,328,1000,-732,140,-63,-176,345,-39,-154,-1000,1000,169,279,-454,-1000,-427,689,575,-215,-1000,609,989,-768,984,1000,-273,209,27,1000,-488,-342,283,802,68,554,1000,34,852,702,537,1000,-752,-1000,-150,-241,-182,509,-885,-203,556,161,1000,-1000,-236,565,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{286,-415,1000,-1000,-1000,-124,194,114,952,-565,-269,230,455,-1000,304,-313,745,-153,-774,179,1000,57,503,488,-906,-159,-678,37,361,-223,711,-274,-265,615,67,-888,-555,-1000,-1000,-930,297,-415,1000,-143,844,-192,-871,-281,911,1000,1000,633,1000,397,235,804,-1000,-522,808,69,-86,532,-107,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{-283,255,678,-218,-897,942,-112,-875,590,595,-85,-76,585,-856,-1000,829,-426,-478,327,-726,-95,-537,-791,-279,850,1000,76,70,202,-73,-282,46,-438,107,-1000,-102,478,227,-225,-383,-277,-750,1000,48,640,-65,-175,761,807,-465,-425,1000,148,931,434,-917,-277,-409,-765,-1000,-848,141,188,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "charset(java.nio.charset.Charset):void",
            new int[]{-330,1000,929,168,-936,644,-529,684,721,388,-412,574,-646,-1000,1000,826,1000,-1000,-893,1000,-1000,675,59,-904,-599,-558,731,-1000,252,1000,-618,1000,958,-287,-889,586,1000,-1000,-1000,1000,716,1000,719,-507,1000,-122,1000,1000,331,14,1000,-1000,445,1000,757,-272,1000,1000,540,468,-539,-1000,-877,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{207,-1000,-1000,1000,-253,1000,759,1000,-1000,952,539,-509,1000,497,-284,1000,-1000,-172,363,-89,528,327,688,1000,-893,-366,49,-461,1000,1000,1000,902,-1000,-705,-630,117,-1000,-801,-963,-641,123,-942,990,1000,311,1000,-1000,-552,265,-1000,-953,-366,883,-398,1000,65,-2,-1000,1000,-1000,683,7,687,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{-439,1000,-463,-547,1000,372,434,-641,140,746,1000,-515,755,-235,864,1000,364,1000,-974,-1000,-493,907,746,-446,-665,-763,-54,-723,-1000,-609,895,-598,1000,-400,-287,1000,557,-830,-689,101,1000,133,-801,-9,1000,891,311,-492,-651,359,-164,567,-445,620,1000,19,-354,-951,-834,289,-1000,489,-278,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.nodes.Document", "org.jsoup.nodes.Document", "normalise():org.jsoup.nodes.Document",
            new int[]{-177,1000,-225,-877,-28,363,-769,1000,321,419,985,105,790,677,-296,1000,-179,984,354,-377,954,1000,822,615,-779,-11,-714,-595,-618,131,1000,-1000,-399,-546,448,383,548,-1000,-780,207,1000,-219,56,110,35,306,725,-647,-377,-358,-25,-132,-505,668,1000,595,325,-942,253,400,601,178,748,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.DocumentType", "org.jsoup.nodes.DocumentType", "setPubSysKey(java.lang.String):void",
            new int[]{-823,-281,-427,-162,517,-311,-120,271,131,643,186,260,328,-949,478,801,-227,537,-527,96,-251,-23,178,-301,968,771,-639,385,-390,-832,-298,-287,979,851,428,-901,-19,-131,-709,640,-518,422,-91,883,532,33,-310,273,427,196,833,-890,-381,-306,206,-492,627,517,-671,57,-773,511,-878,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.jsoup.nodes.DocumentType", "org.jsoup.nodes.DocumentType", "setPubSysKey(java.lang.String):void",
            new int[]{278,916,-134,-529,-1000,944,-1000,223,1000,764,502,831,-744,-808,329,791,-162,336,647,-73,903,-81,16,-953,-1000,-376,-1000,-911,-315,-1000,-914,-1000,-785,962,-196,-1000,400,204,-296,-1000,1000,-1000,83,534,1000,-262,-262,-1000,-116,422,-246,-1000,-1000,167,-113,800,-1000,131,716,-1000,257,-227,875,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "addClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-480,0,389,-7,1000,16,-167,725,162,103,-453,-613,1000,-1000,-53,-191,203,253,908,-236,408,477,-724,20,20,-875,-60,670,375,-1000,-566,-177,-123,-819,272,-564,865,-460,-839,-244,693,-765,-39,-525,-729,239,-111,678,688,1000,-1000,969,679,842,-197,1000,239,-426,1000,44,820,115,967,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Element", DEReplay.run(
            "org.jsoup.nodes.Element", "org.jsoup.nodes.Element", "addClass(java.lang.String):org.jsoup.nodes.Element",
            new int[]{-718,-306,-230,-695,311,228,463,-42,254,-66,391,1000,-730,510,378,481,147,-362,-932,1000,855,1000,731,77,-211,-992,913,993,-555,170,-807,-454,1000,377,270,-970,231,1000,-196,67,113,1000,970,1000,215,-522,-1000,-811,60,-1000,15,207,668,304,-607,-936,-1000,-400,952,1000,-66,395,541,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.jsoup.parser.HtmlTreeBuilder", "org.jsoup.parser.HtmlTreeBuilder", "toString():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-69,917,-549,430,-49,726,-814,70,151,575,-792,-269,563,807,887,601,-303,-952,255,-831,95,-980,872,-224,121,-63,-927,785,-615,-484,-964,-272,589,-294,409,962,313,-941,-144,-630,-718,-629,-185,-501,975,656,995,-878,113,-152,-277,-319,-490,706,-925,-670,-950,-692,-37,-654,129,-724,792,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-398,-176,505,-942,981,286,328,934,358,-820,496,398,777,-918,185,462,-93,-723,599,943,585,850,-56,-51,935,63,-587,-417,236,-925,870,-266,-867,136,-726,-981,-528,-783,470,976,80,-277,-71,375,149,402,252,-783,-479,-137,803,384,-844,-798,-269,-812,-689,179,781,-868,-420,-908,837,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:RENUL2NYQjlpd2RBemNrREo1Zg==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-97,-432,459,-531,-387,819,552,-4,-130,414,-815,80,728,458,-200,320,-533,935,-193,820,897,-208,-198,-292,-912,471,-288,-763,-959,690,744,-477,347,264,-502,-803,970,783,-194,-383,-31,107,479,651,-837,-219,-538,-481,-450,-266,643,-318,635,-93,-987,354,-281,830,743,859,-319,581,-702,-252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,java.lang.String,org.jsoup.safety.Whitelist,org.jsoup.nodes.Document$OutputSettings):java.lang.String",
            new int[]{-190,51,740,381,851,-9,-975,475,583,-77,-737,-952,-51,673,-324,115,87,-981,-689,537,-607,751,420,-535,-778,-710,-125,-984,749,952,990,-29,77,923,-329,589,-236,40,959,941,606,-369,-296,-420,-288,-208,376,-215,-271,-264,-175,-822,-623,-836,-820,-948,-595,936,-994,427,498,-235,-628,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:YQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{-436,-875,-404,-149,-884,188,-435,-291,789,60,-161,823,818,-890,-35,-442,-856,-829,-533,-296,-426,614,-341,-243,-46,-349,77,188,-647,-302,463,-620,13,-110,61,-533,-794,86,334,-151,7,-865,-809,836,486,-474,279,556,-139,-911,-27,-966,-282,918,-667,182,597,340,471,-659,950,385,532,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.Jsoup", "", "clean(java.lang.String,org.jsoup.safety.Whitelist):java.lang.String",
            new int[]{994,-469,274,-547,-566,-483,1000,-718,-63,-931,983,-616,-370,-850,-197,530,-585,179,-533,990,258,614,99,-421,851,311,80,-740,-1000,1000,-746,317,-402,79,-785,1000,1000,-12,-131,385,60,859,-886,596,-635,-83,828,-850,-849,1000,-201,522,-395,-186,-1000,79,699,290,1000,-623,339,1000,44,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{110,-911,750,76,915,-549,-889,-874,-665,-708,-981,211,132,449,563,716,162,112,-330,588,172,-749,-323,751,30,388,-304,-747,-982,364,694,272,-603,337,-997,-731,-960,-521,-84,283,-406,568,944,671,419,991,-666,419,-728,-455,-448,-440,650,-113,-471,232,-437,-558,-269,-597,524,190,339,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.Jsoup", "", "isValid(java.lang.String,org.jsoup.safety.Whitelist):boolean",
            new int[]{594,8,-341,-940,542,657,-787,957,-254,-85,334,-738,115,95,-636,388,883,-788,-884,-799,403,990,-875,-824,78,-369,-418,-609,-567,-632,-762,-142,-99,-574,380,860,503,-787,903,657,-739,336,428,-757,-47,-691,450,-342,578,-257,-428,758,-219,-794,33,359,430,-20,936,-851,-528,480,842,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{419,302,-239,522,-440,751,70,967,52,-757,-528,44,995,793,730,-571,652,386,918,-576,79,889,940,-414,-898,-795,134,256,927,215,-629,61,497,-196,-746,808,-208,-268,-415,371,756,475,64,134,-787,-739,365,358,-760,-512,184,427,822,477,627,-637,276,-921,-597,-786,956,-976,-314,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-302,399,-1000,-432,-269,-853,-693,-75,-1000,-183,-210,430,634,1000,-607,506,-236,-121,24,450,699,1000,-121,-39,-485,-404,23,1000,306,216,1000,87,539,328,-305,604,126,498,1000,934,-1000,750,1000,47,479,386,-212,181,-1000,726,63,-602,1000,-116,1000,607,-114,1000,142,627,-352,-1000,-269,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{798,974,158,-910,-258,-877,852,-80,288,345,3,-234,-214,873,-757,876,-948,-942,-723,-56,444,-725,619,393,469,956,310,-950,-405,-836,821,184,-882,989,-951,-459,-305,11,-411,-267,468,-914,-650,168,-433,-193,-68,-471,250,-893,202,773,-103,-291,25,-420,-310,-628,-817,-757,883,214,-801,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-302,512,181,710,195,597,-590,93,-237,757,48,-392,-128,-570,-628,-99,-690,-533,355,-225,482,-241,-885,-723,-665,-369,-593,-474,-984,-642,807,205,549,7,912,-720,594,-197,202,244,618,210,-957,7,-256,-997,-289,-615,946,-242,296,84,-750,-621,-123,-936,-787,-434,-539,-810,-968,437,812,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{682,-350,-259,408,-325,-171,404,511,823,123,-236,882,919,655,-301,-216,-83,236,-92,-220,-923,758,-253,-877,658,-225,711,173,981,458,-873,-888,765,-960,-892,871,-25,636,556,-413,-852,900,129,138,-898,-650,465,339,289,404,666,241,-356,182,-904,642,727,925,-150,-599,175,554,925,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragment(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{242,326,-818,146,-910,1000,-400,763,-227,-336,-1000,-428,-44,792,917,344,-1000,-311,-1000,-104,640,1000,1000,337,251,1000,-774,-633,1000,-291,-4,-168,1000,-1000,567,-225,468,577,-573,-618,-531,799,319,-67,332,349,-471,149,-695,1000,-651,792,-302,-930,-1000,1000,412,-1000,752,1000,465,422,936,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{678,850,92,-654,-816,2,673,903,79,666,460,949,395,879,563,922,658,-703,-426,681,-313,462,959,576,-162,800,34,-627,-79,586,-611,533,-611,833,-569,734,-906,-328,769,806,875,147,-399,239,566,559,-56,307,640,-496,-406,-488,-404,561,35,516,-501,238,155,49,594,-785,-379,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{2,-1000,594,267,886,-73,-1000,1000,-417,39,-1000,1000,-256,-721,653,-1000,-1000,1000,430,-1000,195,-1000,-1000,-1000,-200,-725,824,318,594,1000,1000,-106,1000,-1000,-284,-647,1000,-129,1000,-617,-915,1000,-1000,-532,1000,357,1000,-273,-1000,903,-1000,-412,-434,-307,207,-1000,1000,-765,912,-944,159,343,398,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String):java.util.List",
            new int[]{-814,843,66,-353,-723,572,-40,-828,-626,1000,-229,-182,-1000,-655,-603,304,1000,-754,-989,401,-499,-1000,-1000,1000,267,-861,-292,-936,598,-849,-1000,621,202,-1000,-1000,-1000,-293,855,-1000,-337,271,-829,376,775,-784,486,205,595,165,-1000,-58,159,1000,-276,1000,-1000,-341,-1000,-432,685,-923,581,1000,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{154,-40,542,-189,-434,13,234,-1000,-760,-1000,-563,236,-363,-231,590,20,-317,-290,230,24,299,-508,-85,513,338,-44,340,1000,929,-1000,-589,-91,293,1000,1000,-178,-51,628,860,-1000,-1000,324,-539,153,-377,-350,-400,243,1000,-740,191,-652,222,324,874,-1000,678,1000,-1000,-659,-15,-78,336,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{731,-58,-748,-657,387,858,-347,105,-742,-68,-139,190,967,-741,-682,832,-654,-602,910,-641,937,-568,323,858,175,-10,916,336,-442,401,-893,-769,-96,-840,188,646,-558,297,-720,421,828,876,693,239,483,901,-652,404,619,156,97,-416,-763,-838,820,895,991,-641,966,-7,340,850,873,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseFragment(java.lang.String,org.jsoup.nodes.Element,java.lang.String,org.jsoup.parser.ParseErrorList):java.util.List",
            new int[]{194,-548,81,703,-804,701,121,-492,667,617,-335,712,-578,-182,-984,-630,957,-453,-352,-778,587,855,127,418,706,-871,-446,-468,334,736,854,843,911,-452,195,-678,891,-462,582,-450,-893,-730,-774,856,-873,-854,-297,-602,-181,535,-927,580,-757,-432,-809,280,-368,-715,209,635,684,81,950,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{-738,575,92,-545,407,-996,276,-510,956,992,722,608,-754,-960,-350,-274,-594,306,551,763,578,-647,416,737,-108,-516,-272,497,-325,-257,-248,-216,-635,-207,-415,532,844,783,-452,435,530,-962,-950,633,430,971,-189,-858,-405,-51,-494,-915,943,-988,264,176,225,-660,-750,973,106,-879,584,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.safety.Cleaner", "org.jsoup.safety.Cleaner", "isValidBodyHtml(java.lang.String):boolean",
            new int[]{-618,827,-682,340,578,344,395,546,631,965,469,213,314,920,-31,751,-812,-33,-552,679,939,956,520,-938,-150,361,-415,-105,700,-798,731,422,385,863,-964,643,-222,239,-293,-684,978,-640,-26,-354,-22,196,-546,-423,-19,-308,844,-919,166,-756,203,173,481,818,855,528,-759,703,981,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.jsoup.parser.ParseSettings", "org.jsoup.parser.ParseSettings", "normalizeAttribute(java.lang.String):java.lang.String",
            new int[]{918,-809,562,-65,150,561,-576,-666,-706,-685,320,185,139,641,-43,417,-28,-335,533,-947,600,-644,-888,257,-844,241,57,409,12,-776,-280,86,-863,386,462,603,609,-795,376,458,-407,857,-399,428,-894,-604,15,-646,-869,-461,246,-307,249,465,467,842,-149,-365,-784,887,-789,-59,-367,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:LS03NzAuMTEy", DEReplay.run(
            "org.jsoup.parser.ParseSettings", "org.jsoup.parser.ParseSettings", "normalizeAttribute(java.lang.String):java.lang.String",
            new int[]{967,-799,-439,-90,294,917,770,115,112,-823,-364,945,-675,-635,971,40,323,-310,-97,768,91,-127,84,-567,-799,743,-234,777,393,-639,-719,-992,469,-22,-585,-579,-450,-420,-615,-706,424,113,-284,205,-352,-471,-851,-833,-641,752,-76,-945,338,-624,255,-329,757,-563,631,-476,-740,-22,-307,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDJl", DEReplay.run(
            "org.jsoup.parser.ParseSettings", "org.jsoup.parser.ParseSettings", "normalizeTag(java.lang.String):java.lang.String",
            new int[]{838,265,-677,-566,159,791,-46,-969,411,734,346,431,482,813,175,648,789,-143,-2,452,629,900,353,607,-226,934,494,-735,332,-580,728,106,-82,-619,-71,-657,-141,-227,-62,-655,303,695,-703,-721,-119,-44,50,936,865,558,-830,670,-333,-300,603,285,939,-925,873,587,-452,-563,737,-678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.jsoup.parser.ParseSettings", "org.jsoup.parser.ParseSettings", "normalizeTag(java.lang.String):java.lang.String",
            new int[]{258,-341,-534,283,99,-136,985,456,-944,-421,351,-745,989,-660,780,155,514,-338,-163,297,927,903,271,18,-790,-829,463,-311,669,-867,-504,-279,-216,360,422,-866,-476,-347,502,737,521,-279,884,591,298,493,467,197,-979,-586,-433,-362,132,-903,398,772,-314,912,-666,-519,-831,-775,486,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.jsoup.parser.ParseSettings", "org.jsoup.parser.ParseSettings", "preserveTagCase():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.io.InputStream,java.lang.String,java.lang.String,org.jsoup.parser.Parser):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-872,-632,-64,390,-529,-889,-791,800,-804,-272,575,41,-549,-864,812,-170,-78,-847,-231,248,179,501,597,-162,118,-55,334,324,-743,-563,724,-903,287,-269,-200,-522,387,502,901,-388,577,-295,-388,-141,645,635,353,-488,-982,-45,-708,795,-605,-203,650,-795,877,12,-515,490,-87,576,-399,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String):org.jsoup.nodes.Document",
            new int[]{-638,-296,-1000,951,-1000,-1000,-364,1000,-1000,797,28,389,-754,-100,591,-300,-106,-1000,567,60,-3,1000,278,259,550,663,-481,649,-565,-764,841,-943,119,-1000,-755,-370,-361,-164,-207,978,212,254,408,-1000,247,198,729,-292,261,-900,-241,874,-486,-220,170,-929,19,632,-1000,66,-141,1000,-471,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{263,-725,-716,362,-957,822,-574,-50,948,-741,-239,506,97,730,-975,-71,-581,77,-303,350,-195,-303,-363,-713,-636,-420,-490,312,896,816,448,934,-882,104,-449,-326,613,563,-908,108,-995,493,584,-961,-575,645,-798,895,-9,32,-480,960,-270,336,-26,85,124,-420,-972,-802,-697,871,623,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.Jsoup", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-638,448,914,856,422,-483,-457,698,-881,-904,863,90,-656,-753,-653,974,-537,-276,431,-77,940,-21,-149,258,807,506,-400,-598,-751,-62,44,-710,934,-436,958,-52,198,670,454,143,-296,501,83,371,-215,-486,738,437,-710,79,-560,320,509,818,-467,-864,473,-638,407,604,784,-534,-559,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.jsoup.helper.DataUtil", "", "load(java.io.InputStream,java.lang.String,java.lang.String,org.jsoup.parser.Parser):org.jsoup.nodes.Document",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{164,-305,510,-690,-1000,-877,366,956,-273,890,-866,-279,777,542,105,1000,716,157,796,1000,928,-176,986,382,-776,-53,-740,-1000,361,1000,323,-392,1000,165,486,-926,395,980,475,510,-149,-504,-832,-74,-35,787,-1000,433,-672,156,196,-1000,-56,83,-1000,954,908,942,400,327,17,-236,1000,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-706,166,-343,-497,27,-125,520,-371,-574,854,389,-433,232,-793,-227,546,-548,-713,-370,-186,-662,751,430,-990,-788,681,472,-3,-766,387,-185,-290,805,-455,-798,205,-749,-868,700,-402,660,-747,417,-145,307,406,-370,525,980,997,938,-624,-314,-27,622,-310,717,-658,-686,-559,-129,-963,288,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{726,-433,-114,-229,642,523,573,-430,-497,-969,-153,829,471,-90,134,-497,-357,686,-557,346,587,429,-510,772,-904,822,915,584,-310,-985,-603,21,-993,872,-525,-712,159,-19,245,170,361,485,716,637,-287,214,622,463,-120,847,703,-226,-418,-64,-880,-809,-676,-515,-489,-244,594,-523,-559,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-514,1000,607,-44,-765,643,-178,374,725,540,388,-786,828,491,769,-574,993,-209,-557,1000,-118,-323,-743,1000,-207,-1000,-920,666,-511,790,1000,-935,-480,540,385,-163,-1000,-151,1000,381,759,770,-858,1000,-781,413,913,1000,-357,-116,148,1000,-521,-114,-641,167,1000,-7,1000,617,323,-472,496,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-188,865,732,-759,-445,412,569,-318,675,-545,-318,206,-663,607,472,-210,373,-44,-232,402,-968,478,788,-258,652,672,179,0,-891,-921,794,-283,-461,-635,840,-538,732,450,426,550,-115,-170,-868,-701,897,-344,-762,-579,-670,408,476,89,268,83,-198,908,-136,-330,-445,-403,-875,433,-414,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.XmlDeclaration", DEReplay.run(
            "org.jsoup.nodes.Comment", "org.jsoup.nodes.Comment", "asXmlDeclaration():org.jsoup.nodes.XmlDeclaration",
            new int[]{-281,219,-274,-1000,-1000,59,351,-475,-974,914,516,-742,958,563,1000,818,1000,338,521,1000,44,105,-448,1000,-22,-308,-355,-956,453,857,717,626,877,1000,-43,874,-1000,-90,664,1000,-256,197,-1000,459,-1000,1000,939,-106,-158,-434,-196,400,-320,-400,-1000,682,183,641,907,850,1000,-860,1000,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{181,105,-522,-343,-46,-629,621,-307,-276,-659,162,-233,667,-318,-42,959,-63,-42,-41,-16,-30,-351,-285,-156,803,-582,778,-584,-42,721,-112,357,-702,760,-656,-472,978,-8,348,246,-891,-57,-762,102,92,-108,-465,848,703,163,509,884,949,-797,-433,-734,503,-565,-809,-164,-940,908,-618,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parse(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-974,813,1000,-1000,-744,150,-1000,968,261,-1000,211,663,1000,708,112,-808,-421,845,-135,284,-66,-510,-828,-162,-894,-746,492,-691,1000,-845,1000,-432,1000,-975,-174,-1000,-297,-50,453,-447,-192,-160,-1000,-400,-896,1000,-1000,294,1000,-207,-1000,-253,1000,767,-946,-349,-475,952,-899,739,-862,1000,-570,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragmentRelaxed(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-298,-829,198,-919,154,905,-924,-858,989,173,356,-532,-72,-434,665,244,-357,-956,511,581,-897,-606,446,-92,852,-801,-823,-537,660,490,897,-862,377,226,231,-214,-875,-979,-902,-700,10,-878,520,253,-561,557,-55,-309,-203,-443,-982,-181,-213,910,-116,-300,20,-270,102,896,603,263,-978,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.jsoup.nodes.Document", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseBodyFragmentRelaxed(java.lang.String,java.lang.String):org.jsoup.nodes.Document",
            new int[]{-206,635,-871,-1000,-1000,1000,989,301,-352,-298,513,69,126,426,463,-1000,544,833,899,593,11,888,-51,-1000,-494,-365,1000,-1000,-504,-1000,-55,-120,947,652,-1000,-591,35,583,53,-170,844,-106,1000,253,-115,791,553,288,199,-1000,306,1000,-157,-923,658,1000,141,869,190,-702,-238,-797,-176,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:java.util.Collections$UnmodifiableRandomAccessList", DEReplay.run(
            "org.jsoup.parser.Parser", "", "parseXmlFragment(java.lang.String,java.lang.String):java.util.List",
            new int[]{-279,841,-375,-651,542,876,-201,471,906,-483,-470,-830,981,-313,142,168,804,794,-640,-462,94,-962,663,-135,262,896,158,-522,-697,187,-525,-597,408,-200,566,969,273,-552,-211,716,-655,582,120,-359,-473,-995,-45,723,-774,674,600,116,-931,643,233,-545,74,-800,-79,-685,-369,864,-932,995}));
    }
}
