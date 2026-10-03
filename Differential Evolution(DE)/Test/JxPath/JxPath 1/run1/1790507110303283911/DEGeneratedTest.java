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
        org.junit.Assert.assertEquals("java.lang.String:aWQoJzU1ZTEwJyk=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{862,-919,595,52,545,-835,742,249,391,-980,886,-55,368,10,-340,-933,922,178,162,-150,82,980,628,-431,204,-148,-442,-431,-173,116,-247,571,-669,980,681,-620,197,-27,265,651,536,89,-200,-864,237,82,979,993,-364,919,240,-860,-120,198,585,-603,-667,-465,835,-790,299,-663,864,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{-86,142,-132,-929,97,-758,478,-413,-295,108,-554,153,436,726,-374,-637,-914,301,156,-930,855,134,-377,514,312,600,-290,642,-237,-779,564,601,-989,-852,-915,601,568,-88,31,794,278,-747,-618,658,993,-1,-225,-529,461,-469,967,-89,-100,-816,276,-463,882,-905,735,1000,-917,480,297,-852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{733,-715,28,147,-204,-784,582,660,-31,121,-35,-36,-558,83,-740,577,933,32,-380,14,-695,-371,-503,396,-833,580,-741,-144,444,-267,-616,425,-934,-725,-677,-996,-871,895,158,-330,114,877,-833,624,134,-287,577,-166,-467,-912,-79,-364,619,852,-334,394,852,928,-911,685,370,50,-295,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{974,-512,59,-739,-122,-884,-754,855,-144,61,-793,-452,-439,-523,338,815,994,379,-195,-74,-245,-560,902,731,-100,-963,-49,-115,961,-15,-500,197,-155,317,-520,823,-504,-938,-8,-420,-477,5,417,-229,-729,-858,-318,621,-748,693,-339,662,284,370,-51,-775,-797,922,-463,573,-399,-113,-740,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-150,-967,-881,-844,841,-150,-609,161,-682,660,-187,-624,959,-490,-638,863,-144,943,-577,178,701,-481,-29,766,-204,-127,-840,-478,-491,-296,185,-220,-928,-832,-656,93,943,-159,358,851,349,592,121,72,78,537,373,470,-880,546,-2,314,310,728,93,516,796,-260,309,-104,-153,855,504,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-283,715,-158,944,-56,-627,-607,38,-557,217,-429,730,-649,-838,-375,324,980,-402,-34,-510,-410,560,880,-35,228,-517,355,682,698,-141,336,-474,997,-760,790,22,879,643,25,-58,-413,680,-56,91,289,488,88,322,212,892,683,237,110,-859,205,-64,-711,-967,-544,-289,-829,-743,-403,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-616,440,88,471,-832,-295,-996,-700,405,-127,-105,210,-131,938,553,-804,-429,328,-904,-576,-845,-461,-847,912,543,758,628,-859,497,-697,164,338,252,179,-618,239,290,-685,-740,-923,491,100,-413,265,-617,-265,-647,-211,-957,-390,-890,-344,292,-577,-94,-52,279,91,-790,-23,-716,-387,392,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{856,829,210,347,-971,683,572,-71,-47,759,404,493,45,-463,-98,880,492,935,-402,846,853,-204,119,114,705,348,139,-21,131,187,837,854,-547,760,-878,280,-571,-415,171,-179,-654,-462,411,8,-73,392,986,14,786,-489,937,-866,249,114,892,259,854,824,-158,-434,515,-625,-211,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-34,305,157,355,549,-523,231,-962,-259,-724,627,-647,-968,-994,-701,849,-679,-798,-273,-261,895,655,843,-439,600,657,-933,834,348,113,114,274,34,-469,-838,-216,-627,-655,499,-848,-762,473,-205,145,-366,596,-205,-492,654,240,-570,294,417,415,-953,893,347,309,844,-862,925,-673,-600,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{275,820,687,47,-606,-101,603,-827,-491,-449,-616,899,-602,-664,-165,-967,-759,-345,700,396,-377,195,-131,58,-940,16,-225,360,-52,-62,920,-166,802,-690,-586,776,-934,-412,-101,911,-134,712,316,-606,-372,766,-428,180,-347,-955,-607,711,-507,-688,-656,832,-803,-224,-618,-170,-606,-888,-883,625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-446,-28,104,376,-599,0,274,-159,-265,-64,0,589,-1000,-1000,-1000,1000,-1000,-255,-659,1000,601,-1000,-1000,596,0,1000,-767,1000,-134,0,744,-179,-148,1000,-969,-1000,0,1000,903,-127,-47,-1000,0,-308,-537,162,-128,526,401,-546,1,519,-67,155,-1000,229,1000,0,209,-114,513,-384,-879,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-1000,1000,288,400,-986,-201,-1000,263,1000,-93,1000,155,653,580,-799,-825,-50,1000,709,1000,291,348,690,74,-570,-1000,-368,664,-219,412,-612,282,-702,504,386,-948,145,55,684,-196,1000,-248,-1000,286,771,-649,-335,129,-942,-63,-852,136,-230,457,-339,-57,-937,-173,437,-1000,989,813,51,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{679,371,-264,-147,-381,576,-434,-35,-748,169,-819,-305,755,60,990,284,784,-518,-3,223,872,110,-94,-4,-687,724,759,204,582,403,-993,-625,-987,-636,658,123,-356,695,-634,794,-56,-485,152,257,-513,357,-179,64,528,946,-921,854,920,970,857,583,-160,-85,415,929,384,612,-581,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-276,-224,303,391,379,65,-63,-224,1000,12,600,181,-767,763,-944,-415,374,1000,-359,-744,-264,-1000,1000,680,-8,-1000,-1000,-611,-489,-1000,-392,730,1000,-156,891,-221,61,-717,-785,116,757,-42,462,-1000,-484,-400,-696,-1000,-430,-1000,1000,-1000,-395,-673,37,271,1000,-264,440,-767,270,391,1000,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-99,398,-129,-795,557,403,-557,-449,-544,-443,-438,-362,-13,-509,568,-841,560,595,867,-496,584,728,849,-361,331,-32,-413,-453,780,916,-363,158,-622,892,164,-252,87,205,-984,-41,-957,-399,411,-219,669,-8,436,-896,-785,602,-293,795,-668,-202,-792,-380,507,25,-401,-194,-14,512,77,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-505,793,-331,120,-401,-70,340,-738,-828,-260,-58,963,421,-282,158,-979,114,-431,-204,-816,-574,-569,469,175,884,-500,2,-249,-277,557,-382,186,-169,685,827,-354,-164,324,-615,63,907,-240,686,323,319,92,-490,-157,-21,807,-796,-45,722,174,-978,904,-545,866,-372,703,961,445,-410,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{-148,650,449,-169,-90,-155,422,-417,-888,-566,540,752,942,-894,-302,65,-853,320,730,206,137,849,-536,-214,-92,-975,788,968,25,-187,83,-958,828,238,-833,998,419,332,458,-736,-761,-185,-562,136,62,-33,-288,173,104,829,960,-156,-403,-242,-101,41,-324,625,11,-248,-314,-494,-764,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{166,559,102,-675,633,-996,-189,677,-955,574,709,-578,378,633,260,976,-943,-713,136,-961,197,-780,385,-763,-435,741,753,-961,-41,-398,-814,121,321,-512,-683,-861,-692,780,283,983,-734,180,-375,950,-391,98,-780,-988,452,311,-804,367,2,-652,-400,-193,707,-712,240,-915,-559,-777,-448,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{-373,-13,423,928,-246,58,27,823,187,66,-850,-693,-293,320,-556,598,-137,-724,-533,-799,467,731,-941,-360,-978,31,-348,971,723,-254,-75,1,915,-888,186,-39,-921,-316,-351,-633,334,110,385,-787,-776,-289,148,-681,-897,-358,-513,622,858,-196,610,-451,865,-603,321,-811,101,-985,-995,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{942,-296,-717,-200,-467,755,682,631,-826,-334,-697,192,-377,-802,435,-773,-385,-536,-932,-837,-723,378,-116,702,-891,-89,13,917,59,18,901,-737,554,-2,-404,-354,-526,-855,577,-252,994,-936,832,191,13,742,-681,771,-584,624,-832,-970,829,-738,-974,-297,732,-103,797,226,-237,302,694,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{758,272,-281,-751,185,-738,330,44,-835,69,913,-314,646,811,666,-735,676,268,-896,-445,-682,-287,659,-272,260,453,-965,7,999,-535,392,-781,-162,-321,-41,-318,-905,857,-2,-263,-77,-211,152,366,892,558,200,806,126,-987,-43,844,-813,260,-333,568,119,-8,587,-257,-875,-244,-310,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-136,-629,-283,-13,-870,-121,384,-605,-304,-296,726,-331,-132,-227,404,-904,81,803,207,-70,-806,-66,-644,-275,773,-358,-341,-467,-129,968,252,-505,-818,-790,118,-76,576,-313,948,-200,39,-119,8,745,819,375,-364,-987,-840,312,764,805,-529,-149,406,-684,944,19,-536,-711,-259,-269,-619,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-738,-412,113,982,-416,-621,43,1000,-1000,0,-585,843,534,-304,-722,-1000,-1000,332,1000,-354,-1000,1000,704,-254,1000,-1000,-1000,545,1000,0,0,-193,-183,-598,218,-454,0,761,497,735,-1000,-74,629,706,-357,-92,-1000,-921,1000,-18,-1000,1000,-763,-261,-15,-508,536,429,1000,279,-801,504,-279,-995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{363,-305,734,-857,559,-945,149,-488,438,-793,492,178,-500,-215,337,112,421,418,-995,-643,252,-587,-363,258,69,802,695,-111,-803,330,231,-948,-990,153,342,850,-926,511,86,-327,916,448,-124,-267,56,750,822,711,-864,747,870,-982,-100,316,-313,231,-358,-476,188,-199,276,692,385,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-766,125,937,780,-251,229,-884,-851,-352,978,134,-414,-633,-287,-137,-228,806,-621,133,248,-48,-508,513,692,-492,205,308,-191,-873,837,-657,-641,175,576,468,-577,-136,376,670,664,65,-465,-834,31,-855,-103,-74,-865,766,-814,702,-662,-256,595,819,513,-216,400,-250,-640,-798,-848,-208,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{771,-761,-488,-956,476,991,547,622,-984,681,-851,-417,-649,879,-819,-151,-966,64,-740,-539,958,-307,930,-867,-53,-277,361,-359,-510,-586,364,-481,-730,-404,879,147,-952,884,-964,948,-72,-200,379,-887,69,-784,936,52,493,-911,-590,577,-181,85,-23,755,448,330,927,268,-979,298,699,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-127,113,-655,-31,259,-11,-213,-492,390,-15,237,167,-67,-507,-395,539,-397,-389,411,-331,450,774,769,-211,478,489,-892,50,823,-338,4,-796,735,198,111,177,-828,837,221,-480,933,507,-834,-166,957,885,-514,-545,-343,272,-423,894,979,-527,374,-158,-746,873,530,-69,835,619,533,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-384,598,-378,-99,291,-887,937,530,-690,994,-441,783,-800,7,683,247,715,-762,-414,-969,67,-305,412,-411,930,173,483,-577,-81,81,510,-652,257,131,630,-27,-102,-914,292,111,-480,44,654,921,454,-820,-597,796,925,-716,-331,209,730,-34,-950,-951,-97,-12,-299,-662,-156,804,-127,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-806,599,864,396,-706,288,-49,690,-650,885,-280,601,-702,-308,-229,971,937,559,-354,-246,478,-237,-492,-54,-759,-403,-319,907,-698,-498,930,757,-636,742,-221,-133,-984,-410,923,-413,653,-195,-151,292,-178,-245,-876,620,516,996,-471,-694,35,-710,181,433,425,155,99,107,244,826,-698,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-337,-531,568,-935,-560,730,685,422,-207,-375,-345,879,-90,807,329,-544,-444,-365,-161,-133,42,944,964,-790,463,-413,144,295,353,-8,406,701,430,58,-127,-75,-48,-211,278,160,345,770,982,154,232,-742,335,-601,772,-970,-83,-578,287,401,182,385,-412,814,355,365,87,312,-989,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{66,907,-1000,406,3,1000,-1000,-1000,6,-83,515,-593,-724,358,597,1000,222,-1000,1000,1000,-1000,1000,-1000,951,-1000,261,341,1000,-626,-356,-1000,-1000,610,-452,552,-1000,426,1000,768,-1000,-981,-324,342,-1000,-56,-771,581,-1000,1000,11,-547,49,-1000,-1000,-907,576,1000,-674,-772,-411,-443,-482,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(org.w3c.dom.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{-113,905,-341,508,305,-183,352,884,-325,-850,-480,-152,878,-126,411,795,-722,-718,-231,398,-772,778,764,-687,324,-85,383,-567,-631,786,-233,532,-937,-26,-691,-220,558,-46,-279,839,183,119,-250,-967,871,-608,702,-575,132,-238,-441,-445,786,-955,77,51,53,-355,991,355,-857,174,190,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{-280,-695,184,905,-317,-34,-126,-587,-154,-343,-167,-815,-697,-656,254,-613,649,790,217,-860,923,-672,-345,-16,-284,-590,-846,864,-699,916,932,341,683,-712,-282,-612,-439,-720,-415,-637,-605,357,303,-491,991,-424,253,722,-690,-826,544,-831,592,-967,623,884,281,672,-654,446,748,-502,-725,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{155,203,556,-783,620,-965,495,-145,525,-132,-390,452,-955,-17,939,-940,-692,-883,-178,438,-20,373,-282,-337,-895,-607,-947,913,-292,850,-543,923,847,-252,106,-571,790,781,981,86,760,41,-153,793,301,-267,-525,152,763,252,-780,851,-418,960,468,-811,-883,-696,624,656,-366,-453,-642,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{13,-920,771,818,-777,724,-462,-135,954,979,236,-630,12,-846,417,325,768,-957,-339,12,-792,-351,428,-497,197,279,-204,-404,-220,-137,-209,488,527,822,291,-668,897,-471,673,949,-620,472,293,476,799,-407,-366,453,-446,-31,-432,982,818,-25,-472,-35,-249,404,603,-836,642,610,568,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{332,-838,559,-582,243,-7,756,17,-932,-374,752,396,206,-647,-587,-942,198,721,-935,-475,773,-174,12,500,-7,-166,79,188,-519,-551,748,-712,536,-31,725,954,-827,588,-349,793,-924,708,-946,-172,-160,-871,609,294,-96,-759,-659,113,-858,549,-559,933,-807,245,-908,749,309,-96,-140,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{406,661,45,939,-71,510,-438,-986,655,-553,75,991,-592,566,707,-106,153,-148,-722,472,669,640,-309,-931,-984,-840,-878,683,220,518,502,-91,341,416,20,-795,-583,-67,287,-302,634,777,-868,637,537,546,52,-505,73,-246,801,-265,843,641,-259,-721,-948,109,-775,-149,-657,-464,-499,-476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{556,-892,-720,-114,-793,868,336,-418,360,995,158,978,296,427,-113,-164,-116,-376,896,-306,19,-490,-444,-804,-629,-280,-91,550,372,-909,389,958,951,355,-193,-484,452,-188,-886,-397,438,-618,-867,-984,633,915,398,-746,-896,857,-476,-751,-2,-526,-891,-429,-383,703,-918,684,439,406,613,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{336,-152,510,300,816,507,854,54,-873,498,-916,-822,-923,-377,294,-216,-703,458,-846,-517,393,-278,-272,184,-744,711,-605,167,-809,510,-213,14,122,679,702,-321,80,933,255,-575,-595,-6,205,-330,671,201,-644,-123,191,767,195,531,-842,606,530,1000,-30,-427,775,-410,68,616,766,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{18,599,101,933,618,773,709,-968,414,347,-693,-147,-600,-304,16,539,-733,-573,-615,330,342,-850,445,-982,601,307,137,201,873,-605,752,932,-890,-785,-482,-439,-730,-525,714,-850,790,-2,-151,707,905,96,-678,660,107,224,-288,483,967,967,411,-787,200,-417,-363,-312,-614,-104,-949,795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-917,-878,170,728,183,-582,568,361,-669,343,-562,-849,-22,-745,547,725,-788,-283,-207,-749,881,-608,656,-964,-238,644,-340,392,-519,-947,-345,745,762,-595,-828,-778,185,-615,-563,-519,-898,133,697,-559,-396,114,448,-254,-491,357,-128,-721,-764,977,-651,-145,108,-848,-773,-636,158,-780,257,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{41,578,-600,-801,960,989,-62,327,600,-28,-132,-20,209,352,-739,-589,428,-18,417,-723,510,104,827,-688,566,194,888,351,-195,-929,-881,878,396,62,-315,-22,479,-136,-669,-992,-878,329,-490,-378,559,-100,457,-118,925,643,-811,-898,-127,-85,-932,-205,-31,585,406,-364,558,431,-177,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{220,409,-418,555,-712,-502,-627,874,-845,229,416,60,-269,917,-827,-64,-878,-59,-907,-23,-619,905,-363,-714,621,-9,321,-569,-922,-344,652,182,339,-211,-189,845,-145,312,-685,-632,-877,-191,-463,-300,-519,-757,888,971,309,51,794,-18,628,8,149,-779,583,112,-162,182,87,-539,-849,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-183,-871,-529,-140,329,-353,224,224,759,-142,-240,108,893,660,-68,-801,-834,532,99,-917,995,-661,654,682,260,688,-998,912,-398,282,-146,-395,881,-215,201,-207,940,280,879,92,-383,589,-750,-911,873,-588,-819,420,-182,561,-723,-485,487,-628,68,868,-759,-174,-234,142,864,-157,104,407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{589,784,742,378,-423,247,814,658,-295,951,-740,-351,-107,682,-206,90,673,-922,-436,-40,211,375,892,-127,-417,-864,812,-962,-273,465,-430,-647,-772,-769,733,253,840,438,86,-406,-524,-986,125,3,-685,35,-364,-89,591,-334,-455,-227,-307,956,-920,871,-759,-974,99,548,-27,-828,386,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-897,-595,-205,-579,325,-372,872,-636,-518,-909,-400,893,125,667,484,-795,-104,-645,-623,506,-709,-938,-416,930,664,-600,760,-411,-770,-243,535,426,-480,153,384,-21,662,-464,88,426,179,60,-649,-654,540,635,608,-478,-370,557,-125,-188,-480,409,615,-382,-588,563,-988,808,509,-384,-191,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-587,487,-779,32,644,225,-276,363,-499,-281,-182,223,-878,14,-230,-108,-697,-7,-719,-292,599,-688,989,-489,-910,-726,539,-773,-239,566,342,-47,139,-924,-282,-670,-255,120,972,-740,-758,-685,-856,105,-305,-928,657,-632,-685,-794,905,566,231,547,944,-469,-629,-443,860,657,-616,-19,156,790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{-963,-541,-47,750,136,474,-511,-697,673,-902,570,-528,535,803,-476,49,-634,831,-533,723,-263,453,-989,-563,-626,-826,177,705,-88,-323,-968,-786,274,-520,468,730,406,47,486,647,-108,392,-625,-670,-276,113,-750,833,380,207,-443,-460,-699,-966,363,-212,-175,-629,-501,680,-319,601,-13,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{-603,499,-185,460,627,-590,-934,-65,25,-440,904,803,39,-63,856,447,608,95,-252,-408,539,25,660,991,-759,-639,330,-102,-818,610,-587,551,14,249,-132,544,-992,298,-272,-694,-843,-708,-496,-284,126,-539,580,-690,77,339,61,-509,314,55,777,-297,-307,110,-603,614,-207,-429,-63,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-653,-322,-512,-401,-619,638,-580,-16,187,-719,-745,-906,-81,775,410,-43,31,-131,-938,743,688,-683,-371,473,-268,996,-25,983,949,-676,888,534,-924,960,540,685,219,-763,-528,-782,-791,293,972,261,-259,445,-390,81,-855,984,-813,-547,-920,-710,385,-1000,556,-64,-24,449,277,241,-359,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-395,-233,-459,557,180,686,-841,852,858,-337,-691,564,-171,-971,-812,435,-167,899,478,879,-158,205,406,-59,891,790,318,-713,637,-517,67,-60,878,-547,790,-343,-467,603,-599,-904,-600,-723,-521,184,380,144,-854,-488,-208,-253,991,-61,923,-64,135,-852,991,137,125,372,359,282,507,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{705,-397,-754,-575,470,-627,-458,659,527,-956,-985,707,615,-579,493,-936,-557,-765,-921,665,-737,224,245,-203,-864,588,-390,-762,111,671,905,896,872,-43,-201,491,-70,233,-492,606,-123,162,-595,-159,876,-652,196,730,142,-479,675,-424,-88,437,-466,-560,154,730,-727,-868,99,-905,-158,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-445,-137,64,-695,28,435,851,-382,354,-205,-201,-927,-986,-260,-309,562,223,213,-758,59,-908,540,-756,946,924,158,-484,-479,505,-390,338,-628,-371,799,205,-437,-457,260,966,367,-458,582,-282,686,-503,-993,202,-783,754,693,837,943,546,-821,51,-432,-165,183,90,-831,-688,-991,120,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.w3c.dom.Node,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:aWQoJzB4ODAwMDAwJyk=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-1000,296,168,328,-924,499,884,-1000,327,629,575,-1000,103,-684,185,-272,-906,278,-1000,213,-1000,322,218,-5,-485,-515,-1000,790,-757,-1000,-689,737,-853,105,313,844,-100,433,1000,-913,487,329,602,119,-656,239,-421,-1000,64,-1000,817,244,156,-1000,615,1000,664,-903,-670,-516,197,-322,652,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-954,-623,-312,229,-280,378,427,-709,300,-451,575,-489,103,-455,442,-484,-977,-722,-977,524,-931,235,-286,329,-268,-764,-686,725,-694,-694,-689,151,67,200,541,769,101,-410,472,-874,487,879,525,119,-496,220,-838,-948,-371,-595,764,244,156,-987,727,870,462,-582,-333,-516,185,120,138,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-1000,360,246,0,1000,879,1000,436,832,-1000,-401,-634,-228,113,949,-728,-984,-1000,-304,-295,-406,1000,-1000,-238,24,-1000,-1000,88,-786,1000,-182,-1000,-1000,1000,890,1000,-196,-204,177,-248,-1000,1000,420,466,-761,-146,-1000,-964,-874,-716,1000,432,-350,-1000,809,1000,-268,-1000,259,-745,-204,418,-496,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{438,479,-93,-298,-770,-874,-101,505,299,339,654,-270,-353,-987,356,-979,-949,136,657,782,552,-550,-653,-434,-601,484,433,-244,-441,938,-662,990,114,373,352,-854,-83,-506,660,-577,-273,897,268,-895,-287,119,-25,893,-304,53,190,-624,-216,-652,-186,-846,160,287,688,-597,709,-175,-833,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,420,165,-504,-130,1000,455,-1000,-861,1000,-291,480,346,1000,-114,-80,-1000,1000,311,401,-800,-1000,1000,-143,1000,470,-1000,-648,1000,-1000,-210,-1000,1000,610,453,476,-574,-1000,-855,961,342,-1000,-64,1000,-111,-999,1000,-370,-683,1000,-1000,0,1000,-1000,-407,1000,-1000,333,1000,530,405,-947,567,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,1000,468,-763,114,1000,-311,-1000,-483,1000,654,1000,1000,1000,533,94,-279,1000,1000,-955,-1000,319,803,1000,1000,1000,-805,440,97,-1000,-28,472,538,1000,-651,104,1000,-1000,517,96,658,328,-137,-497,-63,-205,1000,-1000,-1000,417,-1000,-1000,1000,-665,-1000,-288,-460,740,587,455,493,-732,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-317,353,-213,-130,513,-1000,873,-484,32,601,-849,-1000,-560,-400,-1000,49,-109,-31,989,-340,1000,-939,1000,964,-330,-1000,-799,117,-313,-66,951,-1000,-136,400,-962,-569,-339,91,-137,1000,207,308,342,167,782,482,-564,-498,-791,-447,-98,-571,-1000,71,371,-75,-945,390,-485,-795,-293,808,353,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-90,-998,123,-531,161,646,-271,1000,-188,-400,-912,61,-288,1000,1000,-1000,-365,132,-1000,-1000,-971,-496,-677,-76,-273,627,-409,446,561,843,-979,101,-988,-237,1000,178,-728,14,243,-1000,-444,-340,-59,-651,-103,-1000,217,-40,-1000,491,1000,845,921,629,-1000,-795,-620,-916,236,313,1000,-540,1000,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-199,-270,147,-769,880,-1000,361,603,194,152,348,61,-424,277,90,908,-401,-847,-47,257,269,-251,582,-29,406,-609,-83,107,59,265,204,106,-836,-6,-23,291,-1000,50,712,-277,-92,494,127,-819,119,-266,-489,-272,601,571,396,115,-457,243,-503,-327,845,511,715,481,593,325,-888,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{870,-493,870,-1000,1000,-626,657,-405,37,451,-1000,366,-263,1000,-314,-508,2,-141,1000,419,0,-511,0,-288,69,0,-733,1000,-1000,528,-135,-319,-880,-1000,-267,707,535,381,214,-1000,1000,470,-684,0,-1000,-148,1000,0,-464,0,-746,-1000,0,1000,-285,-387,457,-545,736,0,-912,33,-698,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-460,-908,933,319,785,634,-576,641,-857,668,-197,539,-721,-307,-7,568,-361,867,874,-371,843,290,-444,880,-69,13,-507,877,-70,-820,720,265,611,908,-325,-429,754,-684,-694,474,761,426,455,-683,-956,610,876,-462,232,692,677,-780,-18,863,441,-976,-84,-835,854,378,-942,449,257,6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{814,-21,-16,-451,167,-1000,110,-1000,-354,520,-1000,-1000,-1000,204,-73,-1000,-109,267,-362,-1000,-632,-161,-1000,-430,-262,588,-158,-133,-617,257,1000,-803,-1000,-240,1000,-770,154,1000,-1000,35,1000,1000,-1000,-753,-764,-480,1000,407,138,-1000,1000,946,325,-222,-1000,-65,1000,489,67,-1000,1000,506,-967,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-887,-1000,-366,396,-885,-451,1000,131,-13,530,942,-751,-237,-1000,-4,280,-1000,-381,566,34,-7,117,940,-65,-555,1000,-544,-88,498,-878,280,-1000,293,376,817,408,-131,-31,-82,-973,1000,-370,-385,483,976,922,772,-1000,957,297,-535,1000,-1000,1000,576,-1000,476,-25,-401,660,-1000,-1000,172,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-1000,1000,624,562,1000,-451,-1000,720,1000,-149,-1000,-877,-1000,682,-695,-1000,-882,-368,-1000,-1000,125,-376,-125,1000,-1000,71,795,1000,-36,1000,-149,164,391,-1000,-1000,-1000,-1000,-1000,-1000,-82,-1000,201,768,400,-836,-1000,239,-269,1000,1000,1000,-39,-1000,166,1000,1000,330,1000,1000,-1000,-912,735,239,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{452,414,108,-382,-901,400,-1000,413,750,-1000,608,-974,-859,-398,-515,-1000,364,752,-221,-1000,-1000,-521,1000,1000,-1000,-279,-524,901,337,983,941,-1000,-1000,-1000,-1000,-817,-1000,-365,-1000,788,459,-1000,-1000,1000,1000,-874,313,-11,1000,-888,-628,-831,-804,1000,-544,482,-1000,1000,181,233,-1000,-1000,-207,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-641,-1000,-654,-1000,-132,487,683,292,744,797,-66,-116,-746,64,-497,-892,784,594,-354,304,-342,605,310,-203,-66,784,443,314,-757,64,-1000,-1000,-536,637,544,331,-248,592,940,-745,9,6,1000,-177,218,-772,91,851,1000,804,129,193,-1000,873,865,785,-1000,-827,450,414,-936,-487,-1000,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{213,781,-681,-701,-51,813,169,1000,716,1000,-112,-942,-523,1000,-66,-396,1000,-912,500,-374,512,1000,1000,1000,-66,1000,-389,1000,46,839,-1000,-1000,-852,-926,-311,-36,1000,1000,45,888,1000,-317,1000,-1000,138,446,-832,-989,1000,1000,-223,1000,-1000,763,-880,1000,-1000,1000,216,-981,-1000,-1000,-533,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-90,783,-39,-1000,676,139,-560,-222,621,-1000,548,634,-864,-797,-291,242,1000,869,-160,-43,1000,-1000,848,694,-1000,1000,268,-33,199,1000,-690,-337,-1000,-765,1000,-382,846,-669,-781,-975,-450,-220,-285,724,811,1000,138,618,525,1000,88,-412,-790,711,59,-820,-359,1000,-754,-797,-400,-1000,186,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,-850,345,272,-47,534,1000,80,816,-283,68,-612,-640,1000,1000,-787,438,-806,-228,-948,-271,1000,-861,547,-131,460,-1000,-20,-368,-696,1000,-530,1000,966,-400,1000,-1000,179,1000,373,-1000,-241,1000,1000,429,719,-672,-684,-1000,-1000,-1000,940,404,850,-440,-868,1000,390,491,414,1000,1000,461,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{621,9,753,-766,-536,443,255,884,-126,118,200,573,97,-145,-228,39,-465,-28,-523,-768,452,956,-766,315,654,-658,35,536,21,-169,-143,-133,835,973,-292,15,-319,-623,710,439,-620,328,-91,330,876,944,-817,-817,-703,-446,35,544,-479,-144,-683,-232,484,587,-961,-729,197,947,-761,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-298,1000,939,-836,-1000,-1000,-236,756,1000,1000,1000,1000,-1000,-1000,160,1000,-236,-326,1000,-970,-1000,461,780,521,1000,-601,550,699,-618,437,-1000,1000,1000,230,927,480,703,1000,-1000,-1000,-1000,1000,-28,-415,1000,1000,-28,335,-347,-138,-533,285,743,1000,1000,1000,-669,-1000,-1000,1000,486,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-493,941,24,-590,-617,742,750,-180,-699,-409,234,-409,49,842,983,-581,-429,-240,274,910,762,-834,531,-687,234,-543,427,-547,880,565,-466,175,-643,-640,-513,682,164,706,156,21,923,-67,109,673,669,829,769,737,472,454,-885,471,-417,1,489,146,403,-214,-824,-940,71,-8,-267,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-1000,-270,-642,250,141,322,605,-446,463,-1000,825,-301,613,699,-271,-692,-26,-1000,933,1000,763,-1000,398,-1000,325,-71,64,-836,511,1000,-203,8,-401,-926,-784,1000,-491,457,-1000,119,529,-502,-382,215,1000,1000,386,1000,377,-138,-311,457,94,-90,393,202,-806,659,-72,-1000,138,-1000,-337,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-300,418,-927,-432,159,-91,-853,-680,195,-231,962,543,850,1000,-779,127,-268,-1000,-150,861,1,-11,-1000,-69,658,-332,652,-451,82,951,-528,747,-197,-963,-102,580,-258,1000,-490,-649,-1000,827,20,-646,632,627,220,268,753,316,-111,-1000,1000,-137,400,-631,734,759,569,601,-575,-782,147,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{869,356,-645,-109,527,323,1000,-616,166,-774,516,513,-286,-825,1000,-409,-915,-516,-1000,814,-395,-312,189,1000,1000,656,1000,-238,-416,1000,554,668,801,-256,1000,-457,184,808,729,-617,-255,1000,-648,-1000,1000,-130,1000,-315,-1000,-654,-779,-599,765,292,384,-1000,-768,1000,874,-220,-852,174,-1000,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{570,-698,-60,-676,-583,-447,186,71,-373,-806,-746,400,820,969,872,374,557,-877,298,823,544,-1000,-1000,364,623,-510,-1000,-1000,-803,-1000,210,491,-1000,-1000,589,-416,-1000,1000,-700,-531,722,-1000,-965,-743,-483,-1000,1000,-84,94,-1000,-921,-719,1000,251,-800,-1000,-998,536,411,194,1000,-892,631,710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{-328,819,-176,-572,-718,816,-124,-195,862,451,-526,188,-110,-961,758,104,385,462,-562,-652,963,229,754,630,-444,477,-58,-92,-599,143,548,-185,301,399,-592,-283,-851,-620,87,172,228,-827,-853,-217,894,160,-255,818,-84,847,736,317,-42,-479,720,-98,487,-680,-711,-11,704,569,92,702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-681,32,-696,-646,-855,769,-772,-435,-651,-685,797,684,659,-518,691,-500,-583,862,-635,48,709,328,25,-80,-819,851,-987,76,-181,-931,812,-219,-515,692,930,771,61,645,490,322,-320,643,-446,-520,-296,798,-121,406,-108,-179,138,-485,460,391,-565,887,-320,335,398,-702,-964,431,-941,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{326,90,-177,751,489,971,1,-405,-213,873,692,-74,-61,120,87,-89,990,-238,961,-405,-191,-264,980,-507,511,-788,427,924,885,-430,852,-752,390,-874,-830,636,349,-452,-661,-418,-392,671,-99,-60,-893,-635,386,980,564,-969,882,-335,-216,-665,886,-834,4,859,-7,-515,-924,509,-67,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{37,40,-774,3,109,1000,-779,-833,-19,983,1000,212,-97,-353,92,-941,117,543,1000,770,253,-502,1000,-1000,-612,-238,-267,1000,672,-1000,1000,-1000,-435,91,891,770,-234,114,-565,-460,-855,515,283,-188,-1000,-474,91,959,67,-1000,-966,-712,519,-723,81,18,-501,1000,-168,-1000,-1000,1000,-310,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-902,-1000,-612,797,-603,703,354,-284,-75,-650,437,946,476,992,-1000,288,-269,-454,197,252,-811,170,65,-1000,127,-384,-1000,-977,310,-170,240,-614,-820,-259,-214,-1000,122,124,-865,-81,-268,-367,142,-1000,-503,310,-188,1000,-505,65,-830,-139,336,436,198,-656,-641,-880,1000,-78,816,-52,177,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{467,1000,-786,-361,1000,760,409,-391,150,818,109,473,1000,-1000,-808,-1000,-647,-958,45,-589,622,-66,-375,1000,1000,484,1000,1000,281,868,-452,952,109,-831,153,763,-190,-340,271,-508,-444,9,211,593,-76,-137,-1000,-405,-227,266,104,470,-1000,-232,209,29,1000,463,-123,-1000,654,608,554,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-179,480,-582,-58,-1000,703,1000,434,-949,-1000,502,-322,1000,1000,-1000,-69,389,-1000,-793,-693,-626,985,-621,-152,400,654,-239,-684,-871,511,-396,-1000,-474,-259,-122,7,-329,732,-399,1000,-1000,-114,-485,-1000,-670,1000,-188,393,-592,-57,-1000,567,-338,820,1000,-1000,-625,-1000,1000,-78,816,-254,-22,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLocalName(java.lang.Object):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{557,-868,129,-1000,-120,-941,1000,213,-382,-344,603,226,1000,-340,-804,1,476,561,-216,-550,-497,-871,-735,-367,672,-212,-1000,-156,-29,656,662,1000,1000,21,-906,1000,791,9,-182,696,-597,467,-188,-472,720,403,-615,827,-1000,1000,-28,-1000,312,204,340,-1000,345,-525,-280,-469,928,-458,1000,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-283,1000,-402,-473,784,436,-763,-70,566,-261,-548,226,295,799,-333,185,-320,-8,474,35,603,166,271,1000,688,-384,-346,975,198,1000,1000,744,-123,-101,721,34,652,1000,215,-856,697,933,-1000,1000,-536,624,1000,1000,496,1000,1000,-400,923,406,173,-717,-282,-856,-896,-425,1000,-934,-857,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{115,-15,-18,-805,1000,1000,-1000,-70,893,651,222,777,295,-248,-291,-310,426,-8,98,467,1000,-654,1000,1000,656,538,347,-1000,1000,73,705,-224,-114,-1000,404,-427,-477,1000,-329,-534,304,374,-740,1000,-476,1000,585,258,1000,934,1000,-955,239,-429,780,-950,-1000,-1000,-931,505,1000,235,-857,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-1000,443,-903,-135,312,-1000,-541,-1000,-1000,-1000,439,-92,-131,-924,-804,179,594,72,790,600,360,263,125,-724,1000,1000,-351,466,-466,556,1000,-637,-691,-71,107,1000,-1000,-209,-286,12,-1000,-177,66,1000,645,-140,318,1000,-949,-1000,-1000,-832,-326,359,-12,96,508,943,-1000,725,997,-158,1000,857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-1000,1000,-444,-941,-821,-1000,174,-1000,-1000,-1000,304,853,-73,-1000,-1000,728,351,428,-1000,-701,-656,1000,715,-1000,1000,-139,-1000,958,-705,1000,1000,-437,-859,438,-639,350,-476,16,-1000,53,-1000,127,812,1000,-163,-758,-446,1000,325,-1000,-1000,-1000,-461,260,-950,-154,1000,126,-1000,-548,1000,409,1000,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-459,531,139,-563,6,-282,532,121,121,-587,778,-445,609,-414,420,-684,-247,46,-700,551,783,-872,426,-950,-363,-37,-493,-855,-799,815,15,-527,-774,-689,-774,-278,-165,-237,-17,665,769,259,645,37,342,-304,83,72,-542,521,-555,921,962,585,-73,-429,-871,-773,-208,364,130,455,474,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-991,-1000,186,685,1000,-1000,825,-1000,-472,-287,700,-816,-37,503,-984,-1000,963,-1000,-41,948,122,170,380,-1000,295,543,1000,104,122,295,-121,-921,197,1000,-1000,-77,451,-150,750,524,291,-425,313,-226,-152,234,565,-1000,946,-256,-154,-1000,659,252,-433,-892,904,-908,1000,-690,-971,-297,-515,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-303,711,471,-799,29,-70,-476,635,355,-896,-892,-849,277,977,261,624,-671,-687,563,184,-397,-986,169,-319,-847,642,129,-511,582,-730,-564,43,452,856,107,-314,243,946,-610,-81,461,377,676,904,215,886,85,-745,-951,61,-152,517,196,-112,585,536,494,316,363,935,209,709,-946,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-570,-797,141,73,627,-457,710,-1000,-201,847,-130,186,1000,1000,-488,-1000,1000,-273,-1000,-536,-9,-125,1000,635,1000,827,216,-339,-800,-198,1000,-284,213,165,-299,1000,-370,-1000,1000,-454,177,780,-1000,-808,-125,-1000,117,166,1000,413,560,-1000,-409,594,-623,78,-583,-634,76,43,-1000,586,-615,-978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getPrefix(java.lang.Object):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{437,-1000,-216,191,-179,-476,-151,312,-207,-609,-372,-379,-418,-182,380,-679,-706,1000,-331,675,-1000,774,1000,588,357,147,364,204,-621,362,251,764,-89,931,-166,-536,-990,-795,-811,956,-699,-163,1000,838,-1000,1000,-230,-583,1000,1000,1000,-505,-695,242,1000,229,-331,-553,13,903,-108,1000,152,-522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{540,555,-762,-1000,-839,-342,-717,704,-713,868,487,-1000,-94,-585,1000,-468,174,-1000,548,-425,-1000,420,-1000,-878,7,-1000,-406,60,-7,-265,481,56,489,-1000,-1000,101,-461,102,472,-706,-58,-698,475,-605,1000,-288,589,742,-596,-326,-1000,-518,-54,-672,1000,1000,-353,-202,-612,-707,-27,-415,-101,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{809,1000,-630,624,481,455,-1000,783,1000,1000,-1000,-291,-228,-964,-924,-679,1000,-300,45,-981,-188,-453,960,-945,-1000,-169,1000,779,1000,-122,-1000,-447,1000,-1000,-913,-627,1000,-1000,1000,-1000,1000,-314,-886,-1000,-881,-1000,1000,789,175,1000,86,-266,56,295,-1000,-781,-1000,317,127,1000,-126,-1000,596,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{-634,602,-684,-335,1000,-988,1000,-1000,-1000,-530,-272,-1000,-507,259,-856,1000,-723,-16,888,1000,-795,-431,-251,-459,-1000,926,733,-305,-61,-1000,934,1000,-591,-1000,101,-1000,-910,916,-591,673,-92,942,-232,-479,-1000,1000,1000,-204,-21,17,1000,-636,-947,985,-994,351,-279,-1000,602,-699,88,438,349,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{42,211,-873,353,567,-534,755,900,129,721,-920,708,497,-11,-600,-624,89,686,-354,94,438,-831,976,-310,-69,-769,-451,71,813,266,528,-829,-428,-315,683,799,-275,-637,968,345,-252,368,-603,-749,-554,46,-814,-701,-592,646,-326,365,356,-888,-152,67,175,-604,421,920,208,-284,368,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{153,729,-684,-1000,379,779,4,-604,600,-1000,518,642,6,399,-1000,149,704,-231,-1000,35,16,-1000,-1000,436,-87,-624,718,708,-625,644,934,500,23,256,1000,-1000,644,-422,677,-549,560,-397,-1000,236,-660,-385,-763,1000,177,18,-332,449,-725,91,-742,0,-279,547,-953,-588,-377,284,66,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-1000,437,1000,122,-957,510,296,-1000,-556,845,894,-148,179,1000,1000,1000,-452,-1000,-966,-275,-1000,460,444,-1000,531,-703,-988,-823,-1000,-322,359,1000,608,-697,-1000,377,65,-600,-743,-1000,-1000,-737,737,1000,528,-150,-730,-61,-1000,68,81,-1000,-535,1000,-262,-213,-394,668,80,-654,-656,-1000,365,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-537,-552,828,-200,-1000,784,-380,-629,767,571,620,-148,179,-776,89,802,347,-1000,-906,-1000,-1000,425,801,267,-98,-880,803,-224,-540,405,737,-598,52,-164,-498,375,-795,-722,194,-138,-685,384,679,1000,558,542,391,274,226,-288,-1000,-48,-1000,-688,-343,-318,-210,514,-775,-1000,-165,-287,-490,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{174,-848,862,-30,-848,225,817,535,957,367,993,-93,-685,142,-133,559,199,-430,-450,938,-942,810,212,627,602,327,806,-664,-945,-779,876,487,-769,379,-663,992,-948,-647,38,-957,200,477,615,-817,-524,629,736,787,-865,262,142,147,-8,852,82,-179,386,-271,-279,-351,-750,-548,-139,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{-765,-112,987,-69,-12,143,550,-397,541,-531,224,-405,-875,-936,182,579,-641,286,-149,66,-510,-166,318,-524,970,-524,-918,79,839,-540,565,-724,-435,265,-180,232,-744,-524,-147,-976,518,-799,-834,136,-411,676,-763,-603,868,486,-76,-734,-566,-817,74,755,-208,-21,973,-799,652,662,-307,-376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{-760,10,507,929,121,425,-458,-329,1000,-226,405,-1000,-1000,-622,-918,8,-653,822,-28,-598,-1000,-553,960,-813,739,-1000,-767,615,1000,156,-428,-317,-1000,427,-354,218,-622,-1000,377,-953,570,652,-1000,140,410,-225,6,88,723,442,-1000,-269,-805,1000,-128,711,654,-207,344,-399,-313,-157,-720,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{99,432,351,252,612,36,225,993,145,-226,873,-584,-124,-1000,-173,1000,804,-1000,-1000,175,657,-983,-574,480,1000,-1000,616,-245,25,784,-428,101,308,-245,1000,376,557,-1000,341,-383,662,-545,-1000,-13,-158,55,-797,88,59,-492,1000,635,-397,-1000,-311,713,-445,686,-947,-259,971,-157,489,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-693,-1000,129,361,-805,-513,-651,-622,809,755,913,-947,-787,-141,190,-1000,524,550,887,906,-1000,-369,1000,779,-346,-951,972,-426,15,-1000,-573,-359,260,1000,634,891,-534,-177,1000,41,-146,889,289,517,380,-496,377,-895,-923,617,712,674,-184,1000,1000,-595,-559,152,-1000,323,-1000,-500,-975,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{1000,-920,-300,-352,1000,-451,-444,-1000,-1000,1000,927,-1000,-851,-767,261,-1000,343,92,40,837,-631,-1000,821,1000,-25,-107,1000,-1000,-214,-630,190,-841,798,1000,477,1000,-614,-911,849,263,-240,-1000,1000,1000,182,115,1000,-1000,-901,470,172,780,-278,118,1000,944,1000,473,-1000,339,-536,-111,-87,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{42,-255,90,402,969,-603,721,-862,619,755,-36,-845,-853,936,265,-875,-180,-313,400,929,-745,-771,-405,541,243,-54,1000,-426,-400,239,321,591,391,582,-694,244,-846,-1000,400,801,-7,-400,289,1000,1000,48,-25,-149,-400,272,-781,-134,-184,760,20,-800,400,-1000,-316,701,-1000,-307,-463,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-335,-1000,-624,-111,1000,381,-149,80,63,449,-173,92,-578,512,-67,491,1000,1000,433,-217,1000,599,517,475,-599,999,-1000,-1000,-1000,-397,-1000,659,771,-718,749,305,1000,-1000,171,865,20,-767,-235,561,522,-85,624,-37,-60,-1000,-463,1000,546,-745,326,162,621,-619,-694,636,-372,1000,1000,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-73,-366,399,-1000,575,-696,865,-1000,-652,-822,-746,-1000,-488,-1000,834,-171,-1000,569,587,-934,-934,-934,-1000,298,546,175,-17,-1000,-421,-293,420,212,769,-673,-393,-288,-473,-566,-153,441,314,-520,781,670,24,631,478,-689,129,-525,-684,-1000,242,-1000,347,-140,629,20,89,-1000,-98,-719,-981,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{444,-689,-438,469,-938,-400,1000,1000,-400,-416,505,427,-355,-1000,896,-701,-1000,363,872,-981,516,-97,900,276,-427,-1000,-675,400,-1000,1000,-812,-1000,-57,434,-1000,-400,343,183,435,596,664,2,-260,-158,-526,400,-130,-736,730,-621,-767,281,1000,-1000,663,818,697,-785,402,1000,-32,-857,86,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{483,62,-768,-652,975,903,703,-244,511,675,144,-757,-591,-813,-35,-471,614,382,-316,806,221,-199,645,-24,-593,-731,422,889,-808,665,153,-232,908,-704,389,841,-976,784,771,683,-925,-407,-226,-751,-163,390,-864,688,-404,-725,-842,107,-605,569,17,-759,-516,356,458,621,828,234,827,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{-1000,202,-678,389,1000,-490,424,928,605,-294,-221,545,-174,607,926,-170,125,1000,-119,1000,25,-230,606,-697,1000,429,-290,-1000,-665,155,-395,-775,-1000,210,341,588,310,-234,-183,-202,-30,674,-327,317,611,-308,320,-318,874,-234,108,1000,1000,495,565,320,242,216,-167,-986,166,132,-231,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{-584,-975,-801,389,-456,345,1000,1000,-803,-294,416,-827,-174,-432,151,149,-757,-947,-119,-212,-629,1000,160,-675,669,429,434,-400,352,-10,701,-1000,274,210,822,156,-857,-450,-514,-202,-30,704,1000,317,1000,445,-203,-676,493,-853,683,77,1000,495,107,527,87,191,276,381,607,132,388,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-266,-112,-909,598,364,-626,-252,222,-33,141,794,177,-428,-342,-969,-857,-107,-941,642,-201,-756,20,-884,-92,-223,965,416,500,186,287,-961,-254,-68,555,-436,740,-471,-813,803,-897,-559,-522,-301,112,757,-519,-223,858,-49,806,130,869,-224,-890,-343,539,594,765,-378,773,-668,-7,-556,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-328,-65,-885,-112,-684,443,-796,-297,205,648,849,424,-1000,1000,-809,-1000,892,-608,496,-435,-507,1000,1000,-184,1000,-50,-1000,15,-716,-926,-433,-6,912,-392,-898,64,-1000,19,966,637,-1000,-1000,1000,-401,-446,-1000,178,-878,1000,27,548,-1000,1000,428,-1000,194,109,269,-1000,896,-525,-912,895,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-925,-381,-450,-1000,-108,-876,-470,-1000,-255,16,6,-898,-287,431,196,-707,252,-1000,-838,406,277,494,-687,1000,-697,-1000,232,206,1000,-366,1000,975,1000,149,830,-595,96,-1000,812,-1000,1000,780,-869,913,-465,178,-538,700,-820,-544,-480,348,-838,353,799,-1000,1000,-1000,-670,-1000,1000,1000,-923,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-774,860,-876,814,-960,940,531,-791,854,323,-277,462,-688,-372,-686,-747,25,-582,293,-199,756,-475,-629,312,-473,106,207,-45,-754,94,-371,-642,432,-564,-913,-232,378,-444,516,455,738,-115,570,743,-86,948,-268,129,-163,498,-790,-657,814,520,713,933,932,-495,233,-42,188,367,739,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{842,871,777,-338,989,-38,-824,-590,-36,-987,-311,-507,-288,806,167,-132,-533,-273,207,-699,-678,-212,-223,-475,-663,186,-621,-358,628,635,-788,-137,-160,462,903,796,220,514,-644,-12,-909,-715,-9,-823,950,364,243,-187,-349,-704,-433,800,163,243,-602,-872,246,471,-498,-912,-936,-392,27,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{297,462,-360,-670,379,630,-1000,-1,-387,-1000,-525,-823,-1000,641,-203,-187,-905,493,8,-1000,619,318,-1000,-690,-35,-528,12,176,-34,876,-754,19,-856,-337,633,-1000,148,140,-241,980,-1000,597,438,-336,-929,-647,-260,-894,1000,-1000,-1000,634,820,588,1000,-890,-896,490,162,1000,-15,1000,-567,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.model.NodePointer,java.lang.Object,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
