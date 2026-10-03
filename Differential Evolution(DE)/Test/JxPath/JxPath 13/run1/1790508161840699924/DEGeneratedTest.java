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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "clone():java.lang.Object",
            new int[]{-799,559,-506,156,-19,-775,691,832,863,-729,219,-931,489,-356,-149,971,-63,104,-173,-611,335,943,-264,40,802,688,-81,651,-293,690,847,-282,-239,151,927,954,520,-48,809,71,477,452,406,117,-851,-665,-412,297,-707,519,757,923,696,172,-617,-63,-117,967,983,329,502,830,-5,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "getNamespaceContextPointer():org.apache.commons.jxpath.Pointer",
            new int[]{393,-71,-86,-141,-388,-444,-794,-686,-291,118,-460,-109,892,592,-825,437,-344,567,-104,956,-844,567,155,-206,-623,-144,107,-228,611,97,-11,-868,-677,-428,534,493,-971,-415,508,-5,474,143,-134,-716,-909,32,-483,-923,-504,-986,224,639,602,-973,-202,744,829,975,-599,392,835,4,504,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{516,989,467,-978,-412,-681,50,601,-754,-449,800,317,218,214,434,514,659,786,-208,-163,-591,-270,-493,-760,-132,685,-719,-723,-24,-565,-566,264,-956,287,729,298,205,-225,715,-175,117,241,825,-505,-786,-222,268,346,269,247,431,54,353,-339,908,-105,-672,-935,-423,400,990,-733,178,-788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "getPrefix(java.lang.String):java.lang.String",
            new int[]{973,697,169,-584,206,610,-32,-995,-817,-116,987,969,-578,-238,-240,-221,355,880,969,-626,737,-420,-652,-247,-222,503,751,-148,-208,738,502,-139,-54,350,-516,-542,90,-337,-653,775,571,-670,-338,353,443,936,-86,821,562,-380,-214,582,-347,62,377,409,657,-941,692,-496,-605,-136,46,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "isSealed():boolean",
            new int[]{701,-212,-409,851,36,147,449,102,-984,-165,964,-505,-658,-771,466,793,635,-328,445,-168,228,128,662,-225,934,151,-914,-783,-713,850,972,818,489,-309,-422,-96,-636,912,438,-879,976,-905,925,580,269,-996,901,-770,-945,184,660,181,-993,27,-921,582,920,-16,-81,-679,-562,483,-433,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "registerNamespace(java.lang.String,java.lang.String):void",
            new int[]{329,-635,-468,-313,-436,949,-153,947,658,-977,881,-774,282,887,-1000,-248,-515,-500,126,-399,944,321,-25,274,-466,-398,-922,765,524,364,-86,706,429,232,-200,-120,-111,-671,12,222,-192,512,-978,-737,-572,361,-287,-79,20,-442,957,495,429,-461,-962,819,852,651,85,-426,895,-568,844,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "seal():void",
            new int[]{290,-59,688,-898,-426,613,542,825,752,-920,202,-866,-987,-938,-301,-850,-158,443,-498,53,320,892,969,27,234,343,-671,-426,-336,970,-565,28,-797,725,998,-174,-991,980,104,847,-546,378,-587,962,-674,-404,676,844,-761,-588,-112,-985,192,-821,-207,-840,80,643,245,-226,341,872,459,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.NamespaceResolver", "org.apache.commons.jxpath.ri.NamespaceResolver", "setNamespaceContextPointer(org.apache.commons.jxpath.ri.model.NodePointer):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:aWQoJy0tMjE3Jyk=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{-441,-385,-314,-296,-700,771,636,-744,-480,411,-751,-380,-217,135,-841,648,660,506,-732,735,-806,715,-487,-233,567,455,185,590,-237,-868,-95,86,-7,460,-690,122,181,471,-859,-66,-110,23,463,827,759,-997,-483,-550,702,581,-93,-607,935,141,651,331,231,759,15,-439,-299,-475,-32,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{706,-677,61,-977,66,139,259,-944,655,126,334,439,-813,-754,-446,-898,-674,724,-612,530,984,264,-605,409,668,-346,291,-17,719,420,329,769,255,138,-852,457,196,28,799,-869,823,-566,780,230,969,977,626,104,-805,-697,-190,-559,-950,580,570,-620,895,-777,618,694,-354,673,-346,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{667,-504,288,-977,-209,-545,-1000,-749,-204,1000,-307,-292,8,-198,261,551,-701,-144,-143,-293,-974,111,451,-978,119,506,-521,-320,-34,773,1000,368,325,1000,241,-82,595,133,-780,-144,95,-429,816,570,1000,-1000,-528,-1000,-590,-1000,-530,-923,516,1000,1000,-266,-674,-500,-305,326,-154,-98,834,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{78,254,715,-902,-533,937,302,293,229,-532,-178,91,741,909,-676,-632,339,590,559,-719,-355,-294,80,-394,-622,-729,635,784,-305,-150,898,69,-445,-426,296,-225,199,-403,564,-204,400,292,521,-732,-142,-718,-457,-465,801,-310,204,442,708,-127,-357,358,879,356,367,-677,532,-332,389,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{849,-861,738,813,274,-478,-891,308,-40,-208,-51,783,-998,-635,-949,672,-412,-790,-481,176,166,-324,-390,-585,-699,842,134,727,853,-118,993,-405,523,-979,-804,251,421,278,-455,192,-121,-614,47,548,-566,-972,-923,523,624,838,343,-18,-944,504,767,677,40,740,-448,-462,-955,893,-102,-960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{963,715,816,581,-186,-139,-476,-508,-655,-327,-642,-444,-522,777,114,-48,-541,-700,305,193,-359,586,361,406,35,-545,17,-794,111,-932,816,709,731,591,405,855,719,-561,-850,354,-870,-928,-508,-713,-7,97,394,682,-994,706,-714,-759,-508,378,-47,780,-248,216,989,-71,-85,275,-372,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{701,-397,-1000,-269,-65,393,-527,-865,-460,799,-747,193,-310,365,-341,540,-439,96,941,-143,-78,-44,1000,725,1000,-290,276,-31,-76,162,-1000,453,913,-940,277,516,-1000,-558,-189,-604,27,193,-613,1000,166,159,302,-39,-202,-364,-303,412,653,-255,207,-1000,-246,-1000,-477,-1000,320,928,651,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{674,438,893,-292,-239,-710,24,791,-679,-648,421,802,-604,-251,-897,337,7,874,-285,232,361,656,798,-758,82,90,333,-928,242,804,427,327,-163,-200,480,147,944,-854,454,870,-729,-656,-25,950,55,389,-81,503,351,138,848,384,-381,-536,-777,-522,-764,-71,-349,23,53,205,-381,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{128,247,-885,-738,-778,-440,-650,-121,-313,1000,-1000,-327,405,-3,202,-550,120,-311,930,3,-811,2,242,1000,777,177,-58,1000,933,519,-1000,733,1000,-197,504,929,-1000,763,-1000,342,163,-287,-670,566,1000,167,242,406,219,-29,-316,377,695,-434,500,-1000,-194,-743,958,-650,529,-12,87,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{136,92,871,246,140,920,-236,505,471,553,243,673,438,-776,-903,-130,183,-97,-263,86,588,-426,665,317,-616,-360,-848,-328,-81,743,-258,346,548,860,359,802,-55,-673,775,245,758,-389,992,-480,991,136,681,950,-772,-807,-747,307,422,-891,-641,81,-665,206,655,563,910,813,-481,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{52,-293,610,-80,-129,203,-941,164,908,-777,207,565,323,22,563,249,778,910,-843,34,280,300,-537,-275,-461,-469,646,-940,-619,-656,809,771,245,-601,77,-321,-885,74,-654,445,375,-73,-701,695,30,669,-90,-413,-518,267,-937,562,-364,-110,154,-961,480,-189,556,-902,-501,-917,-271,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-69,-615,39,617,373,421,-242,-110,356,-1000,143,694,512,580,854,822,1000,1000,-973,642,799,768,-730,-693,-1000,-481,-11,-1000,-837,-12,1000,1000,-307,-740,-590,-636,-474,92,-262,-44,475,193,-1000,998,502,1000,297,-285,-383,-134,-732,305,-305,-539,475,-541,604,620,1000,-660,-1000,-802,-438,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{425,827,583,-415,714,-906,-10,-859,969,911,-36,-810,796,719,-222,-121,566,703,743,121,-427,-508,-896,301,604,132,-921,638,439,-424,-544,-507,375,368,-402,-657,-389,226,180,-729,-895,862,537,-593,305,-511,120,951,98,-776,216,-814,233,-148,-163,161,518,278,476,651,168,399,293,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-255,-854,-684,-321,406,-927,-410,-466,317,809,471,-1000,-629,-497,915,527,-55,-670,-337,-195,865,-154,-291,101,-311,-693,-736,-840,-808,-895,49,-152,650,-62,212,-746,-838,-627,636,-11,-936,54,-52,85,283,-35,-836,49,-596,876,-437,-581,146,383,-857,446,988,814,-226,-563,752,652,-931,696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{71,-345,-351,-275,-661,-588,-76,100,942,133,-282,-761,536,-660,967,-102,-275,107,437,102,-775,178,862,195,-599,619,-515,979,992,-683,216,-215,504,102,-861,-159,435,306,-492,337,279,167,-26,236,71,643,374,61,151,-74,-919,639,746,-788,668,101,-979,-828,434,313,-691,-91,-829,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{710,-1000,72,-1000,-1000,-687,499,110,-281,692,937,-407,1000,1000,1000,-23,-384,-219,-1000,618,1000,-775,968,402,-27,822,1000,677,1000,106,-1000,-1000,1000,1000,-400,-213,306,1000,387,-148,-569,1000,-1000,1000,1000,148,61,-907,1000,-220,772,928,629,-663,1000,883,258,831,-763,449,124,-1000,-1000,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{132,603,-374,23,-200,552,14,537,620,755,-485,-86,-401,-651,-43,751,822,-741,700,86,-38,822,972,-992,-907,-675,-520,-221,208,277,196,775,-332,-742,-30,779,-24,-59,-332,-973,-284,-495,-95,228,-726,-924,-879,311,-932,44,-237,-939,379,-833,-401,-9,626,211,-138,-194,906,-456,-184,-346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-248,-914,404,-785,-133,-192,330,-55,689,-466,-942,-431,-3,-158,-264,799,-799,273,241,388,550,-768,653,73,-300,756,296,-421,270,960,969,496,-173,942,743,5,-87,732,-271,-664,171,-528,-389,-353,419,423,422,-463,592,-382,-983,-161,891,54,24,484,-826,-886,-340,-701,-339,-368,-269,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-725,-400,201,-456,-7,-243,-928,238,-1000,-407,154,-1000,1000,-539,455,-506,-719,46,-377,-326,21,327,-1000,-235,384,555,346,371,328,-539,-25,873,-158,150,616,-493,-236,-917,-1000,143,997,1000,-1000,1000,32,400,129,177,905,-1000,-915,-572,-636,611,1000,-161,440,-890,923,472,904,148,599,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{601,537,639,-484,825,901,897,550,-892,-776,-742,-485,-602,511,195,-649,109,-401,535,417,-200,-549,845,807,402,-399,-844,575,-405,798,-289,-601,-641,-245,-350,-966,-734,175,-154,-733,648,-783,476,-963,-578,-54,-685,891,-943,443,-753,989,935,746,-968,524,-806,42,-38,-410,-982,176,742,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{47,655,-184,431,575,92,-959,-10,-169,-600,-596,-293,-195,494,-524,-721,-101,968,-881,578,-983,25,-857,-592,783,-601,330,246,-115,-711,-57,435,-954,-886,90,118,-398,-776,-436,-441,264,652,-790,464,290,-593,225,391,986,-106,-663,-584,-786,595,315,-148,454,354,-78,995,806,-720,33,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{374,-373,-760,-599,-299,779,368,-310,-47,296,754,-696,693,-206,494,676,797,777,-998,-161,158,-366,-755,-507,851,87,-875,-89,-285,-129,271,-548,-689,-587,528,-351,-258,198,-34,-895,-86,-375,-625,-224,-530,-799,904,591,-135,156,744,826,813,748,-420,-834,707,867,-105,-33,490,-129,181,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-223,573,269,56,-587,-172,600,508,40,-701,334,409,-4,286,-600,546,298,125,763,-163,-148,-414,333,343,-207,-941,-265,378,-246,945,562,-397,890,-439,-270,166,951,895,100,195,-138,551,695,341,-144,-615,493,389,497,456,-949,-519,-352,190,21,-59,839,943,2,-661,-640,-504,-900,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-353,121,943,516,713,194,-413,-24,484,-577,110,844,918,883,-386,787,-892,880,-296,-57,-717,-615,-138,-161,750,402,797,698,-68,726,-764,-373,-346,547,-391,-687,338,-710,-343,161,-123,-850,107,142,-382,898,-731,-31,-909,-310,-372,739,-75,869,-645,634,-287,-976,-996,892,-481,-41,638,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{77,467,634,-284,-612,-36,-20,-877,-661,-43,-807,-494,62,368,223,495,-68,256,-695,-547,723,-72,371,969,640,817,-461,-798,-773,-484,206,-422,954,-14,806,972,-918,90,913,-288,320,-663,32,468,-266,-11,-816,424,392,-437,156,1,-706,-885,-839,-162,303,724,608,-258,-27,-925,595,872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{40,-41,577,-463,567,-373,-654,-80,-497,982,461,90,-412,-622,-711,981,-84,-308,-110,586,-628,-377,-68,-287,-946,272,35,721,-68,-624,798,211,-268,-825,281,272,887,28,-644,88,-722,644,-27,432,-557,43,175,-75,-45,-768,-556,-214,750,-11,858,-747,323,102,448,860,912,959,-870,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{-452,117,-396,175,695,-812,-756,791,249,-506,437,407,-840,-318,904,-741,821,-917,862,-559,784,999,595,-247,848,653,680,-122,659,346,-492,510,737,-65,-721,689,-334,931,-714,-197,-930,-788,-36,-551,984,639,907,-322,-801,-284,874,-987,-714,-162,-343,64,-885,623,606,597,902,-595,585,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{169,665,913,-119,-204,345,637,-145,244,-845,82,-554,-355,439,785,-758,408,770,544,28,-139,809,-347,-809,211,-559,-83,-662,-731,-281,263,-387,-102,469,-924,590,744,-653,728,-425,-589,-415,230,616,-3,-282,-629,616,-366,-844,314,-442,-471,645,178,-964,886,-630,845,-946,-958,-266,-44,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{454,-669,-72,-522,645,377,320,-940,555,946,-963,-769,941,489,-960,-793,696,-320,-490,-294,805,938,975,-223,-123,-951,-447,148,644,-857,-376,-190,362,-401,-493,-79,-360,382,-907,86,-166,774,-184,539,891,501,-177,-178,-764,857,744,-792,-805,-440,-906,274,-710,838,996,798,696,486,-339,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{-829,1000,-163,-267,52,830,219,-408,-1000,-605,-133,-271,302,-821,736,-92,195,-163,1000,-619,-1000,-313,-868,1000,-244,-791,1000,1000,912,-1000,-1000,-368,1000,968,119,106,-143,239,-514,-252,-592,901,13,558,-596,-418,775,-1000,-497,-29,643,1000,836,285,-691,338,-507,625,-903,21,-559,-298,-1000,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{471,-967,-329,960,-327,758,640,427,417,-244,50,296,200,121,988,-46,-249,24,-766,808,182,-402,-270,-987,982,762,228,-938,65,294,-142,282,160,624,-304,-993,-92,-486,-234,362,-768,5,419,-588,758,-129,-496,-240,397,-832,347,593,514,-589,-798,636,585,422,519,-482,680,950,173,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{918,762,-159,128,-220,496,-378,896,732,532,-26,869,747,-385,185,-329,122,-476,-537,-978,665,-959,627,535,-22,513,130,-447,-903,-373,681,871,780,-855,-925,-125,870,-969,207,834,-701,651,735,867,574,-442,335,-550,-143,-274,-887,-746,120,68,344,543,283,-470,437,424,-263,176,784,-174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-825,247,987,-764,-700,-589,-162,954,-370,150,-617,902,-638,545,453,874,-943,615,-947,513,-224,781,230,-711,-795,-962,-832,268,299,-996,583,-443,-951,88,786,-5,37,-221,187,839,-336,246,-526,-985,-852,-706,205,-174,705,-916,147,-70,652,955,-683,860,654,-783,412,-593,755,808,-945,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-394,359,647,746,845,-992,-996,-537,-409,-938,-116,966,311,99,-267,559,-136,-38,-747,914,-520,600,-682,460,542,-807,-415,-175,35,-402,650,-779,-291,272,383,798,900,292,-294,286,745,888,459,-31,-897,-17,251,28,838,-390,227,-430,-619,837,-458,316,427,-183,977,808,-491,-68,-356,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-583,40,-80,372,170,208,297,-469,94,265,-961,-528,-192,-838,873,119,237,181,511,984,898,-124,-163,-715,217,-831,-209,333,-421,633,108,671,-130,182,813,458,299,-208,-984,995,-23,726,932,-55,-643,-247,324,822,989,315,554,602,-748,244,322,-62,745,-276,832,414,818,-530,579,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-609,-204,-731,273,616,166,480,-514,240,124,-427,-588,-436,89,-878,-921,-206,366,629,939,-718,992,692,842,652,-493,-76,-591,-871,617,-88,-341,-458,845,-702,825,-603,743,668,628,-816,992,-655,1,662,837,-589,-785,-766,-326,-660,682,-256,-665,-558,-674,11,-364,32,351,-734,-897,674,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-184,305,-972,-471,533,-679,787,-181,317,-349,117,9,975,-921,-846,639,554,127,988,399,-514,-855,977,-451,609,433,-907,-252,351,761,510,428,-206,940,-518,-135,-935,256,-297,-155,580,499,820,479,14,-880,-484,87,568,-426,700,347,90,-674,-274,892,-87,186,267,448,902,-365,519,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-311,-399,369,868,805,-609,-920,-83,323,-877,-346,898,190,-912,807,718,697,-125,39,307,-505,764,-17,145,703,905,292,738,-987,-762,-72,827,-876,474,493,73,-405,-471,-540,-210,245,-182,545,853,-372,-487,-675,373,-255,494,-784,-86,-797,-929,370,109,-703,-228,-237,-655,128,-516,740,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-1000,-233,-834,-743,-525,-999,-2,-592,-334,1000,874,-208,1000,-327,-654,716,744,-231,382,-181,628,767,1,-448,58,-698,630,-876,724,-723,-712,1000,539,227,-514,873,-65,-188,-74,-313,-783,835,582,544,-168,343,673,939,829,-327,134,1000,-360,-593,-933,-847,-528,588,-318,976,-623,-301,1000,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{39,-394,-166,-569,882,-58,-418,445,648,-118,269,886,11,501,983,-947,164,-692,578,844,330,-565,-653,98,836,-737,351,-727,507,-910,-429,788,633,179,-880,863,827,292,-688,246,738,610,-426,-200,-275,-110,885,-373,424,614,-278,128,-636,923,-666,190,756,-782,571,-427,-55,-569,-519,-530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{618,357,-593,-467,938,-644,-738,-776,222,-16,193,412,159,57,-917,-639,332,-706,431,134,-28,-523,7,-101,480,-60,702,188,-76,232,-266,-807,-760,-499,400,632,-63,-78,148,299,704,-700,-85,-16,-456,-99,-600,175,779,492,-801,-367,606,609,-394,-221,748,145,-715,659,-800,-519,956,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-979,880,-592,200,-284,46,-784,-542,-458,240,-357,921,-884,297,654,-75,733,-533,-593,-50,468,893,247,602,-400,-135,-144,-324,-119,507,-965,-847,-300,258,615,-489,229,-720,-400,244,293,-258,779,-876,-126,-165,796,391,926,-673,-70,813,481,-525,-310,333,910,204,-272,910,-361,-409,-431,-939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{610,200,-594,-644,668,-566,-655,-575,-7,943,-278,-524,-772,973,-910,-190,-284,-943,899,754,544,-343,-871,371,141,-123,923,93,-20,-717,178,750,962,-549,-408,837,-515,639,648,-585,327,680,-363,-465,-951,-924,969,704,-137,-865,756,609,-397,952,-88,-991,814,73,879,537,805,722,-504,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{400,538,745,-271,239,209,-14,-991,684,7,-1000,498,-177,-448,17,-44,317,393,-108,-207,-97,-756,640,761,-124,-499,952,-331,88,-706,-941,-551,622,0,11,153,-1000,1000,538,-694,-379,-984,-383,-140,545,-1000,-1000,401,-137,-514,0,657,-305,1000,-330,812,0,578,-716,-479,0,-546,-355,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(org.w3c.dom.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{199,-766,641,-343,178,502,-867,-961,-297,84,646,810,54,-318,-420,695,65,-367,711,-644,60,-48,-123,-477,573,457,823,173,187,949,492,-377,946,-468,850,467,-257,643,-86,-367,-146,143,102,537,353,884,210,-700,520,591,411,-698,-417,598,-532,-47,-898,-997,83,519,154,-848,-285,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{369,456,-991,707,-406,-808,-389,999,-872,-191,-38,-520,9,58,740,-322,363,-821,-613,995,469,645,407,-351,-113,-154,833,-88,714,705,-554,-971,239,-648,-908,-705,-832,-542,-917,81,361,600,-956,-668,-382,115,819,21,398,312,-903,377,578,601,249,-713,-632,152,549,249,309,-986,-566,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{695,259,751,-613,814,-917,-479,-869,319,-770,34,589,-777,896,-825,901,-921,-434,605,18,980,-827,-426,-570,-41,354,97,657,528,-594,-332,505,-783,-315,-482,171,925,-301,-19,649,647,864,-995,-450,735,-974,-988,472,210,-767,-598,-666,847,943,826,741,786,909,-986,-671,204,-713,442,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{493,-994,-314,449,443,-879,-258,224,950,342,-191,267,29,-622,485,558,815,699,-161,-148,-693,-870,110,-990,-208,65,723,-843,-887,-516,-291,566,-312,-581,-536,-961,739,-625,500,-210,-482,265,350,343,-496,916,89,-196,512,-108,112,26,457,-515,-298,625,103,34,-267,607,558,-506,382,427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{1000,730,16,-97,-416,-504,229,-1000,712,737,198,-1000,1000,130,813,577,-201,-228,-352,458,-428,-357,-1000,-1000,1000,-138,-347,450,385,491,-344,287,-1000,-242,-1000,-1000,-436,409,-634,17,293,1000,-368,1000,356,1000,405,1000,-38,-788,-261,-559,150,1000,39,-186,723,288,596,1000,1000,-19,-1000,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{1000,771,298,959,651,-812,602,843,-383,-1000,838,1000,-315,-726,894,-1000,-130,284,-167,-517,-806,-84,938,706,-365,894,172,54,-980,-1000,1000,773,1000,1000,302,-1000,37,878,-865,285,302,327,1000,-34,-106,-912,381,-1000,-447,927,-564,751,735,-766,0,-360,-1000,-1000,31,-1000,-366,163,-806,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{440,368,-950,-18,653,927,907,422,-589,-85,-804,368,111,786,703,784,-980,371,209,636,-247,-392,-731,612,-310,-948,-204,601,-102,-420,364,-8,758,-914,882,-370,880,-378,885,377,-855,470,66,-421,739,903,849,-489,-97,28,-131,838,782,41,-35,-471,-751,-551,-991,-752,855,393,284,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{790,801,847,548,110,-38,924,-844,181,81,880,-309,-717,115,-510,704,72,279,-94,-928,-794,-569,-259,-123,902,626,194,62,230,236,-157,-629,-471,-613,-516,900,-214,-841,970,658,-1,976,-596,927,-360,931,206,-68,131,-590,615,368,963,-705,990,-348,527,-711,917,-583,-952,-541,570,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{-909,-56,-956,940,42,989,438,-961,682,927,-636,-423,534,562,236,-192,-18,-489,-771,524,-639,501,97,-751,188,-642,-817,-200,-535,-509,906,-358,639,18,761,270,178,405,449,-92,964,-571,-882,234,765,642,-61,-450,-142,76,189,-156,588,900,-114,510,-905,-23,634,-829,-262,792,782,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{455,-406,-523,1000,-667,231,-968,-625,291,-237,1000,118,83,-175,-762,107,-1000,563,-247,424,301,-273,227,-605,356,413,-75,-1000,-387,1000,-1000,1000,922,319,-153,-741,-657,-761,-1000,-67,579,51,-467,158,-939,1000,-416,-519,-507,241,-23,-845,-1000,987,-1000,1,685,-48,785,-264,-1000,13,889,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{506,553,635,-585,546,-907,122,639,937,576,631,-770,-346,527,664,-850,928,833,-19,26,120,-853,-886,-139,-343,66,33,52,-680,195,-41,951,204,144,799,-200,-970,-695,-541,-689,212,-227,-649,355,-966,213,856,310,614,636,164,-575,-38,-928,-12,-747,356,945,-52,-318,506,-398,-904,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{-663,237,586,-207,-406,85,512,137,912,848,-21,458,527,262,-471,-778,75,318,51,279,-296,895,82,1000,-418,-305,5,-720,381,-575,707,-634,-559,54,184,475,-557,-500,335,-997,-367,181,85,-81,-15,-28,-115,-821,-287,0,-138,455,1000,-561,679,-513,-737,-82,-110,-1000,185,-493,-50,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-994,302,-717,462,-738,-105,-524,-640,-366,-25,312,454,-114,820,962,-792,-641,-794,23,228,964,-180,-905,-910,214,-953,-468,889,-857,811,513,136,-52,-920,580,-146,697,-359,344,976,-428,769,-781,503,751,574,-311,243,271,-76,901,-575,-24,143,-821,-1,28,-86,422,-989,-664,249,304,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{849,502,-429,-235,-447,-319,481,578,-110,559,792,643,269,752,-214,-843,-636,357,-971,-715,-979,436,-283,-539,701,340,19,-26,-521,-743,-231,156,630,271,-174,716,-654,-779,-995,-998,215,-302,619,348,-223,-397,-678,0,665,-86,-759,-384,-357,480,174,-164,198,-971,4,-19,-783,-121,437,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{818,297,-336,417,355,938,-397,434,292,322,-977,951,736,-52,-980,-329,-825,288,94,206,243,670,352,-277,-822,-169,285,708,36,802,965,974,-276,-301,-463,206,-593,986,434,-360,-10,-175,870,-224,-25,-768,863,-557,274,-53,-722,-48,-705,142,214,643,992,410,417,652,458,822,346,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{302,-946,-333,297,721,-416,107,-296,-770,88,673,-881,553,170,-480,-571,-825,827,-233,-883,-84,873,167,-8,-563,-631,-815,-656,-448,48,-841,-898,-122,-480,214,-172,-893,330,578,-741,962,807,-943,-503,791,134,561,-77,-362,-997,767,439,649,-167,-711,-246,-294,-363,37,64,298,-688,873,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{207,-149,-494,-728,139,-64,-150,121,91,-989,-654,449,412,265,-556,253,-975,-659,-597,623,-401,-830,419,205,-365,-849,-948,-776,-66,-972,748,-504,570,569,184,-890,-759,481,-790,-663,-335,-155,-776,698,555,822,968,-881,-382,364,920,-372,27,-758,-244,138,845,812,657,-481,6,-349,-360,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{336,453,-176,704,1000,-444,-362,511,-1000,-54,340,-870,582,555,-87,549,560,405,-1000,696,319,1000,1000,-191,-358,-555,-257,912,-1000,-37,-952,-1000,-736,-1000,1000,-962,-1000,302,6,-42,-611,-150,-269,50,1000,-608,-235,-355,-1000,-1000,-217,720,582,63,264,620,60,135,1000,-531,79,-1000,1000,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-200,923,528,54,179,706,-421,-886,-493,720,-808,-142,918,-455,-24,-84,173,611,-314,884,-313,232,-323,319,-623,485,626,-645,-168,479,-326,495,-873,244,-683,-546,-280,5,909,-875,297,-102,486,708,-815,-640,-243,821,999,-684,578,40,559,107,-987,-290,-191,-373,-861,103,442,-445,-805,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-662,399,728,-409,-834,784,-687,-47,-492,-986,561,-600,-873,142,-511,-122,290,-455,586,551,868,-243,-709,-997,802,778,-579,-798,421,136,146,960,885,653,-696,25,-816,-626,-587,-935,-496,478,525,-186,-442,68,274,-572,526,-267,-137,-303,592,513,-407,181,790,-539,549,-423,-174,-591,506,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-122,715,-188,879,-24,-208,-959,807,-494,-961,527,218,-370,431,568,-33,949,460,27,-909,-367,-591,349,-134,-588,965,-406,-811,-700,-881,986,203,765,-57,691,-572,-858,394,65,841,545,569,455,-690,-742,12,-234,-408,-343,598,-496,-292,-933,279,742,907,-329,-440,-416,683,529,-51,-944,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{285,-283,-616,249,-548,-847,368,-680,-805,43,-40,-930,-821,-136,219,-298,23,-832,317,790,862,431,376,-472,214,319,-557,-667,617,179,971,94,-974,-93,444,723,566,-762,174,-403,839,6,-306,-988,-289,921,683,-212,-717,-970,880,-164,549,-829,95,-340,-834,463,-953,529,699,-855,549,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{743,730,408,169,811,-236,-429,-322,-847,158,-396,-935,519,205,-575,546,150,951,-733,-829,373,-445,208,-449,484,-721,-612,936,-34,967,813,981,614,-778,975,-73,676,-584,850,-146,-177,-890,-712,168,-595,-484,-729,550,264,-782,79,225,182,-466,-989,-413,937,612,-23,985,899,-347,-976,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{798,687,296,-252,-548,-51,-1000,-680,1000,-887,-846,-1000,516,1000,329,-1000,-15,638,317,12,-519,99,-575,1000,45,-1000,-111,-812,-507,-1000,971,-855,1000,1000,228,-1000,-1000,1000,-12,628,-391,-481,1000,-869,-583,921,-523,-212,-717,-970,-368,1000,400,530,412,-340,1000,-1000,848,-1000,-319,1000,549,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{-144,884,-109,-406,-820,-192,-333,-952,-923,-740,-553,171,-403,-894,759,-342,793,433,-971,-172,742,860,217,-77,19,613,879,342,-878,-828,-976,412,-756,-787,639,-896,467,-212,-890,246,983,181,-29,-831,250,-241,-668,515,137,-753,-820,955,-938,-655,326,-36,876,-628,185,461,821,-297,-182,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{-216,-780,33,-732,-950,23,-643,961,73,-894,782,-543,991,-659,-676,394,467,-667,-575,-87,926,-138,301,-510,-263,-57,809,-712,-764,-1000,59,-231,-592,-481,586,-850,-309,765,208,-614,922,695,890,-204,742,-1,-100,-796,-88,-612,335,506,965,683,705,-42,323,53,669,-845,660,-169,259,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{391,451,-804,376,-51,-888,-722,-424,53,390,794,-790,-501,471,241,-370,-988,127,145,736,320,-864,60,825,740,642,-332,894,474,-547,-983,189,-305,-810,459,-399,188,407,-776,-364,-171,-676,714,-907,-481,663,593,-639,-717,214,528,-721,-742,-770,52,877,-19,-467,-577,-993,-974,-726,-405,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{158,-310,-391,790,998,153,648,-267,-199,-732,-962,-872,-513,542,-515,-981,526,-71,787,266,313,795,-224,12,-337,514,-312,-234,-860,-509,836,-189,-669,-118,-785,-309,-314,-616,419,-892,87,-942,57,754,-102,-633,-201,285,974,-669,76,956,-226,58,540,-746,-411,-262,-600,-683,711,672,972,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-673,186,198,590,-137,-202,-188,310,689,530,112,138,603,405,885,-394,620,236,-103,-505,-894,-54,290,144,-536,-780,794,421,781,-268,-110,-966,294,-274,-271,922,-12,742,427,-986,217,963,152,-148,835,-199,-681,247,999,121,184,-531,550,-199,101,-728,660,-787,-154,498,-676,-352,-502,-47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{710,-695,676,486,607,852,-389,-834,751,-335,762,750,-438,-436,536,-41,199,-230,992,856,-556,-927,703,-676,382,-248,700,-457,120,-921,995,253,-759,780,846,605,173,847,-302,18,-816,-250,-417,-405,-656,283,-369,402,-538,-971,-679,161,742,-274,-907,787,695,730,-943,586,718,174,635,912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{576,560,494,327,-694,439,-732,970,-903,-376,92,-966,-692,507,483,-327,123,-912,-561,429,78,-932,-833,331,982,-962,34,-491,-137,418,163,-983,463,-670,-106,271,45,-26,-538,993,762,214,-350,-93,722,70,-38,-457,-24,599,875,-539,-820,262,113,965,-583,278,-407,958,756,-298,-370,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{533,46,909,976,848,385,-811,-472,-297,-525,956,408,303,-173,-143,-344,358,113,853,449,-590,-179,-812,-498,728,-337,-901,-261,-413,-923,873,-382,665,-284,-323,-797,474,147,-321,555,292,184,424,-608,900,-782,-817,36,553,-265,-970,669,106,780,-226,652,-19,580,-418,225,742,740,-920,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{1000,390,-651,-597,232,914,1000,1000,0,-770,-27,-534,-466,651,0,59,1000,395,1000,207,553,-337,0,78,-415,19,1000,-1000,0,-693,607,144,-475,1000,226,-855,1000,760,-911,-179,334,-1000,454,-506,190,-828,385,-123,-1000,1000,-613,-357,1000,748,-1000,101,520,-1000,-484,482,551,-241,-167,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.w3c.dom.Node,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
