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
        org.junit.Assert.assertEquals("java.lang.String:aWQoJzgxNCcp", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{-970,221,487,317,-728,-187,185,253,-448,679,-177,-265,96,948,-353,488,482,-782,613,-917,-716,814,-644,470,-697,-77,-657,-864,-374,-284,-430,-657,686,-931,-238,-730,-711,-743,-524,548,-891,-927,-307,703,561,235,-439,-700,159,660,-203,-796,-548,-803,-92,326,-509,-241,880,443,378,-558,285,246}));
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
            new int[]{-638,649,-734,-266,719,856,-760,335,681,711,-624,-526,362,-989,771,757,-658,367,790,268,473,460,-922,569,-200,-495,-953,-292,873,624,-725,243,-280,283,841,664,-518,735,-977,290,284,-519,-935,685,320,188,-316,-26,305,-820,918,-981,-522,-319,528,407,-870,301,-326,379,-686,916,-145,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{772,-664,742,434,-662,-110,340,-16,-671,264,751,205,-802,-100,-549,203,72,195,525,-849,-485,-269,367,511,-305,-551,-463,776,744,-800,-760,410,-703,-498,-973,106,-255,-563,-264,871,-458,-332,455,-466,95,275,317,252,-809,782,-658,-682,-276,113,-812,-816,544,-537,683,-39,673,-886,330,882}));
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
            new int[]{-272,346,301,951,-495,196,701,-200,-198,47,-196,474,153,467,802,-213,882,474,640,611,566,731,-929,550,-187,-366,735,-195,-432,673,-830,-165,-612,-178,-199,-105,629,831,346,-131,-633,340,-131,959,-32,-548,-268,716,743,75,-920,-316,-138,700,182,-527,791,-449,921,496,-52,-932,-934,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{67,149,-651,578,359,-450,818,-536,-308,-209,-655,-299,-847,-207,-76,207,-270,965,-589,177,896,399,-803,347,-189,108,-507,-652,-86,-337,-756,-393,-258,-7,-703,303,558,425,696,-877,597,-427,752,-277,538,-872,875,183,-768,249,83,-966,598,851,543,-103,160,805,566,-741,708,845,49,440}));
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
            new int[]{-27,571,-919,840,986,106,149,729,964,315,344,684,34,-83,-298,-938,-730,511,-325,-423,76,104,-9,302,-533,358,-878,925,916,-622,288,134,591,193,587,99,469,-492,-968,-798,-930,-283,104,-605,-612,-299,742,884,-51,534,597,163,880,835,-109,-115,364,-780,190,516,-63,75,-946,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{592,-913,430,-671,275,126,-165,246,575,83,-68,-619,839,-27,453,967,42,151,153,532,-955,-810,476,646,-367,243,-321,632,-766,-849,-701,55,628,-780,-574,895,-791,-369,-389,683,374,-308,462,635,486,-187,961,801,522,-16,377,612,-23,-198,-711,-146,-76,627,-346,38,185,332,-858,-541}));
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
            new int[]{188,577,15,417,753,719,510,179,-718,162,-237,958,-858,772,-755,588,-328,109,-221,83,869,-259,-589,-613,837,19,926,-872,-1,436,-543,531,-492,336,255,-276,-545,-241,51,-909,-400,504,632,-985,-873,-887,290,399,639,418,-411,-323,-919,511,562,-307,532,-395,877,-232,981,50,778,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-718,380,-104,-203,-138,259,205,-376,842,-498,193,-371,247,572,-322,-992,306,77,743,-772,-825,250,223,964,-625,-514,-403,386,-3,927,579,-371,639,-663,-260,-164,743,937,397,-883,587,722,701,-81,-516,880,-646,897,961,374,727,-868,-365,-521,-741,-222,292,143,997,-386,315,-609,-132,784}));
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
            new int[]{-146,-932,-318,10,-246,-34,289,548,-949,48,-979,-230,193,917,-413,-386,-395,706,169,274,706,-847,-850,-825,-642,571,869,-690,-888,547,999,-424,514,-601,208,-676,473,-112,-362,-732,231,517,454,-926,-164,845,205,501,-835,-305,-922,-197,550,854,-757,231,-170,-43,452,967,-741,575,808,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-806,236,-18,-574,-156,-67,977,696,-1000,-153,-934,452,-829,407,576,425,-761,287,-561,-244,1000,-373,433,-586,230,47,989,-271,612,746,-147,-51,-495,916,1000,-141,703,-1000,603,-148,945,-1000,-295,186,1000,-212,268,-897,986,492,14,554,-127,-893,-347,-940,745,-599,-1000,-810,256,134,581,840}));
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
            new int[]{-415,-707,-1000,-620,431,1000,-18,-217,1000,-870,-939,-782,-1000,-970,-372,-341,-1000,-981,-472,-163,-343,6,-216,-56,-144,752,-288,-921,211,362,-644,245,-737,723,120,-1000,-677,1000,-388,109,-75,-52,947,106,1000,115,840,932,-416,125,528,-867,-646,930,-150,-923,-1000,-198,-309,-1000,228,-396,1000,436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-703,764,-978,365,43,224,326,-588,731,-765,-745,-208,-739,-281,376,-679,327,471,32,-595,-665,234,327,-133,-627,703,-200,-933,338,613,560,-842,783,201,-387,234,28,-438,639,127,654,640,-417,662,-367,976,-911,266,13,-307,-996,-967,362,668,971,-448,-47,350,506,-863,85,276,-198,421}));
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
            new int[]{-775,898,42,446,449,741,-372,912,544,809,-698,74,-745,-604,-273,45,168,-608,-655,-916,-765,-703,-807,249,777,-544,412,341,478,-637,-866,163,-665,954,367,-10,522,65,64,179,687,-527,635,-142,340,272,-167,782,666,-254,150,579,363,-684,-333,-120,-103,-288,-380,-902,-16,42,872,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-102,-475,243,157,-264,528,760,-903,95,485,201,785,275,51,-205,342,-324,843,507,517,-468,693,451,692,-566,-320,-913,-607,-916,-808,-323,-628,680,-4,-441,-494,-845,371,-223,-814,62,-284,-638,670,340,720,-874,957,-590,200,-804,-871,-905,991,-230,-163,711,909,-493,616,345,-267,862,60}));
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
            new int[]{-216,463,-632,-291,-730,328,504,683,484,-782,746,742,-828,126,-157,604,-405,123,-252,-266,431,281,-322,-746,756,-712,-851,-579,829,-178,-252,570,-577,476,541,-440,-791,-903,-44,-767,323,-235,744,148,-354,-684,193,-24,-437,324,-839,314,-988,-266,663,-609,-520,-12,-187,187,-725,398,-328,795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{201,320,47,-336,952,295,610,-296,-799,620,732,-172,124,-239,499,702,-109,-25,-447,526,-641,-35,-698,-252,-915,-734,50,-667,-358,-243,72,-802,-685,-233,-30,-917,306,-18,62,349,817,-430,990,-27,-445,-215,271,-634,-442,679,82,528,-458,481,213,555,-538,719,826,506,394,-786,442,284}));
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
            new int[]{-543,-293,-614,-711,61,-924,-183,-629,467,827,443,-146,-632,899,-978,276,2,656,816,882,170,407,360,-107,462,791,-959,78,966,920,312,-709,-203,-689,-334,269,884,317,-121,196,-355,-967,-480,363,-611,-554,210,553,601,-2,-295,-281,-368,-334,-469,-16,724,131,-421,-983,-210,-257,-129,457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{852,77,-275,-692,33,965,-87,746,-987,5,586,507,47,31,99,-401,413,-51,-534,-906,-292,-396,-123,851,538,0,-341,-546,-644,824,-911,-59,431,-51,-177,-123,808,-600,-484,434,-467,-878,-286,-728,350,-178,58,231,-336,-874,417,-920,603,-636,696,950,-277,386,-237,-583,-922,-997,164,-299}));
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
            new int[]{305,436,-592,-548,816,-468,19,535,482,117,807,-59,870,161,96,918,467,-269,941,682,-220,-941,918,61,-304,-610,-330,-628,-17,969,-718,-17,109,237,-129,47,373,-856,-332,-32,-229,-682,-708,292,-8,754,-69,-57,452,694,704,-32,-603,-600,-406,-692,741,-363,-936,473,-465,-139,-326,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{431,-91,351,-3,-430,-351,-912,570,-722,424,-217,-422,687,-739,-402,-467,752,930,767,177,-187,-502,-50,-310,-890,-540,270,407,-67,-612,-11,528,-40,-436,-429,6,564,445,17,-714,-621,947,-229,-76,-708,520,-749,218,116,800,-885,948,731,-809,738,81,-806,167,-952,855,458,319,898,-936}));
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
            new int[]{-631,-212,14,826,-780,113,-932,719,436,-759,673,-769,794,-634,-338,991,34,758,886,601,-648,-64,468,-541,-650,-343,214,-291,-200,161,339,770,-468,592,501,-410,-81,-93,-780,-711,999,-255,-793,155,-928,-319,899,123,805,132,483,789,-712,-277,150,-214,323,393,921,905,245,705,435,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-861,-427,-817,217,131,466,-647,330,-564,-720,520,-879,-730,-836,-728,49,249,290,-773,-732,826,24,-962,957,39,-208,431,-558,608,196,999,-959,455,-442,172,-902,-358,952,-639,-553,409,997,592,701,485,217,130,152,203,-392,581,-148,43,-800,727,465,-790,-133,968,880,-399,-582,-339,-755}));
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
            new int[]{693,-173,-446,806,37,-859,-355,168,846,-655,-785,-673,-228,-709,-897,964,283,992,-169,950,98,575,-628,878,541,-54,975,-969,179,528,79,-430,128,55,-935,188,-666,-687,-867,315,821,-113,-53,-959,399,561,-135,400,-344,802,542,-639,-793,935,-528,954,79,83,679,-684,916,703,-874,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-140,-946,-281,951,275,558,601,-967,211,-272,-257,-476,195,-886,-240,-612,645,528,-390,489,999,-159,-134,692,378,-58,-597,583,-523,200,-294,-712,-572,264,495,49,878,-631,219,190,-771,-539,-886,892,42,-629,979,-189,14,68,616,-21,-432,-454,947,-343,-869,340,-760,-143,453,814,-438,-906}));
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
            new int[]{830,-68,302,809,-427,747,121,-589,417,971,154,344,242,592,-331,792,449,-128,625,860,-548,353,-522,-975,-667,1000,-71,953,885,153,919,-957,460,490,649,179,390,483,279,172,555,33,91,-845,-299,-160,367,804,-518,-123,-979,-316,-119,-105,87,-795,-324,264,-409,-217,-102,649,29,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{592,-784,-4,-868,321,616,-210,-519,417,500,-70,514,580,119,-936,482,240,-512,-359,31,-52,-650,-26,-289,596,-143,307,210,38,847,880,-512,-893,932,801,-14,759,709,-263,853,-858,-618,-173,763,-238,-156,-940,280,-424,811,-588,995,873,26,-559,-342,-932,-287,-225,-589,191,495,912,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{357,-287,-260,-488,798,765,222,-906,90,-558,-683,690,414,-493,-389,-99,-906,721,9,513,-8,92,725,-100,619,-35,-990,-135,-258,540,-196,801,643,688,322,22,973,-900,746,901,-816,-282,644,72,-124,-803,519,-652,107,-320,-6,-755,-311,-422,498,56,805,235,-911,242,-107,-627,-980,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{212,377,194,-866,-933,473,845,599,28,130,707,408,-615,650,436,-64,861,786,-152,-600,117,-494,-270,-480,973,-709,334,563,-950,384,-90,579,235,152,-609,550,-334,419,-346,243,-220,-567,-357,571,-628,8,-885,481,-419,-715,724,528,-741,556,768,-385,-726,-362,30,-329,945,-602,-185,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{991,382,-752,729,323,-930,-472,333,331,807,318,665,377,492,-822,-184,198,-341,218,-395,968,-80,9,-976,34,219,-250,-647,8,61,-489,-41,-430,-414,142,737,667,-938,-284,177,604,-810,302,-893,-50,-531,-749,-673,142,787,-780,-469,225,407,701,201,215,545,-40,464,-704,-135,-28,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{961,182,-981,-130,290,77,811,-453,968,-333,512,493,458,-912,551,42,147,-476,-813,278,-304,-534,-217,122,-867,772,-380,344,924,132,361,996,841,288,-226,657,-661,894,-874,577,390,-753,776,304,216,295,-63,725,230,-678,-696,-434,623,-554,-674,-659,-487,-247,241,530,-663,221,696,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-767,-276,-737,742,913,-471,-564,-878,45,113,413,706,581,-758,-554,-414,-147,-854,-714,-207,-425,-682,-278,-673,629,-776,-679,826,-885,-233,742,-350,-267,-897,457,-9,-525,-52,575,-641,-196,-132,514,-716,-983,-811,-238,-595,371,-819,-324,-757,-373,-79,321,902,203,880,-246,724,860,-343,-202,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-687,-227,644,426,536,-524,-614,53,-821,-969,828,-542,-973,969,-79,-33,596,971,368,895,173,-174,233,65,-115,-505,498,345,-453,679,-285,-44,638,-989,401,-765,667,-453,609,-779,378,573,-766,-69,805,-93,71,533,-20,-135,-673,-263,289,849,423,701,910,104,216,473,-446,-368,508,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(org.w3c.dom.Node):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{674,-85,-757,15,-578,715,-292,-976,853,-739,914,545,-596,-841,-200,643,480,721,-571,840,99,944,-272,802,-368,-127,-358,-240,419,-636,-906,-114,43,35,-51,-306,-854,661,-944,-10,791,-804,-669,900,-404,164,876,40,-873,-748,-870,-924,-632,580,-798,539,-530,-624,681,-915,453,522,-538,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{-190,874,-460,24,-705,744,-716,773,304,-819,-73,151,807,-955,-141,-293,-349,-410,636,-353,-853,610,531,140,-237,411,-443,341,815,-298,-505,459,82,-668,-756,-922,326,270,-321,377,-128,-894,-870,-114,-409,-34,576,-998,-111,-210,701,-396,651,564,-940,772,476,-627,797,-70,992,863,567,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{-173,-475,-821,-892,73,399,146,-80,498,607,-321,383,-56,-731,412,885,-531,-961,660,-507,-117,218,-86,527,-543,984,909,190,808,-558,348,351,75,-914,-397,894,388,443,-174,577,-596,162,745,310,-819,609,-860,911,41,164,-495,-17,239,833,-285,-663,216,-453,659,-416,22,558,735,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{64,460,90,11,632,-87,602,-788,-805,-737,-432,-238,-909,-246,-480,820,122,-235,-55,-623,289,742,-659,-132,746,963,932,-509,-166,-543,488,436,-870,-752,907,500,385,-707,326,164,941,-34,398,-904,760,-250,-941,273,558,923,-353,-584,-309,-556,-484,-702,434,999,475,610,72,-767,278,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{926,-559,-940,-178,-642,625,-628,-877,-77,814,-357,-738,947,-330,790,203,-23,462,-822,730,267,-339,906,246,820,166,-19,947,758,-434,-799,58,199,898,-136,64,-490,236,-978,847,879,-925,968,-514,416,-20,849,-914,-282,-759,879,-531,734,415,-510,-180,-908,-512,938,438,587,191,-632,-366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{-517,496,-830,531,-555,681,556,-996,489,-463,-274,330,125,251,743,276,73,-713,-723,252,-652,210,774,-27,316,-199,-749,590,396,28,-54,683,484,513,-658,674,-97,-55,-296,239,681,-403,200,127,-676,-450,928,415,-112,543,-284,-312,114,-550,446,-121,676,319,-186,-406,167,-375,959,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{36,836,-662,663,-33,728,151,174,-836,-503,-760,522,-576,-601,328,-671,-704,441,217,581,-867,-129,-735,173,-425,164,600,112,884,818,-279,-879,381,120,999,446,-332,858,40,822,248,923,859,-244,-257,-309,409,-450,957,151,94,-186,717,847,620,-370,639,878,772,-44,-257,-577,524,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{-2,667,-379,304,281,902,-459,47,727,253,-206,311,548,-546,-229,-167,-617,424,216,-166,-188,388,-330,-987,701,700,-323,-567,-242,-546,839,230,533,719,-278,192,499,118,33,368,817,401,498,78,922,-372,432,-13,179,643,127,195,488,-371,855,-279,-36,-305,559,205,581,705,105,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{484,251,-294,869,928,-775,-630,209,-995,-878,-320,-619,-520,-672,-151,390,-61,7,-997,502,-999,243,246,-915,584,-464,648,988,-77,607,-911,-758,-715,548,-52,18,-322,-963,-252,473,-779,536,7,-653,-682,186,834,-990,287,463,50,10,668,-462,27,-864,-910,801,825,-490,282,5,-283,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{739,355,240,-485,662,-471,454,-506,130,686,400,-179,-881,95,-604,862,990,685,561,47,744,167,-18,34,91,461,80,143,572,442,-315,909,262,-872,-636,-237,-96,58,-337,-486,713,-128,113,577,535,846,48,690,-398,522,627,257,-357,-563,790,-634,822,464,288,78,-390,299,451,593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{-269,-961,-702,662,144,292,366,77,655,-692,-521,-675,-782,-903,142,-619,-419,278,90,480,-58,-897,-29,-151,912,419,-922,-673,695,-864,872,21,107,-471,24,396,-249,-976,-314,71,503,-26,824,-16,293,-608,540,560,-889,590,-434,-940,145,263,331,-623,8,-342,377,196,-883,-834,-752,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLeaf():boolean",
            new int[]{806,199,-812,-427,-461,-869,-898,-873,887,573,-124,-824,-242,-580,877,207,247,873,-466,-272,758,-16,-649,269,-669,-141,813,-939,220,-522,-422,435,514,865,342,262,274,-320,747,604,-512,-883,-195,136,-9,515,-550,-101,439,-986,25,453,-605,-796,785,-398,-366,-181,835,-468,-91,223,296,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{240,-652,330,819,223,-137,304,235,-503,124,152,-643,-812,153,-811,-294,255,-968,-954,19,319,854,168,8,818,-573,-265,-223,-518,645,573,-2,810,-611,521,284,-321,-961,-697,-154,549,281,-575,-525,786,-301,870,-807,-435,-119,441,-529,-446,-769,-522,-170,-908,-507,380,858,-54,110,873,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-495,187,988,64,-912,371,-226,-321,638,-886,-978,-582,498,-980,-309,377,-896,-480,-128,-767,897,158,544,273,455,-619,902,-270,647,-202,150,63,-751,-222,-528,-863,-49,-478,527,940,-14,-694,-726,396,-311,427,163,-853,-759,-388,-154,-253,559,-894,-610,604,-161,-521,-266,-765,629,319,581,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-35,-823,166,746,-622,-600,-333,239,377,-398,311,593,122,-92,-286,-592,-248,-521,654,-214,140,-463,-21,524,223,247,-772,396,-832,-604,-392,-65,-113,372,452,105,553,-202,510,-444,665,-613,-471,688,936,46,-570,-288,-161,-909,120,-953,-28,-465,464,-17,602,-152,268,-684,686,-657,266,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{666,-551,289,-427,408,-446,-1,504,408,15,665,-125,-835,560,-622,-747,809,-523,370,45,-992,37,460,-539,-629,551,-797,-832,-715,-822,-900,253,387,180,-341,75,15,-828,-628,855,-140,-895,-282,467,808,217,-773,755,458,-18,-708,-55,200,-778,970,-760,-127,-36,-705,-498,2,-628,796,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{-974,470,600,453,947,11,303,-606,206,19,123,695,-468,-529,430,-503,-778,234,825,529,-601,-632,-569,838,831,-413,418,-356,-449,10,-39,-612,-33,393,-385,-326,-278,672,892,-44,-828,-169,-244,-475,-985,-158,662,661,-647,-223,822,634,255,41,444,669,-237,-892,-173,949,192,-824,-592,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{912,-305,-757,-35,-704,-38,-435,-63,-786,612,430,-69,-617,878,-689,-758,-161,614,-231,-764,246,-487,-720,-528,95,-485,817,-330,-608,933,-248,-24,-74,-149,424,-385,-276,999,-430,125,952,490,654,139,168,563,155,-78,-420,-224,-166,-246,754,993,98,620,91,-82,753,672,295,966,579,436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{353,986,-775,-149,-369,28,158,743,-893,363,-923,-617,-755,335,340,-436,-948,74,862,-906,-439,-886,199,-611,139,-946,-619,862,383,-731,-910,-827,748,-404,880,265,-657,264,-941,310,407,-144,-425,158,-156,-954,-218,704,900,-973,-658,-290,586,-901,-349,42,755,-288,-828,30,891,-595,869,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-38,955,-562,248,-666,675,269,-15,-631,471,-635,378,368,-254,948,155,538,-805,-558,-629,427,131,-927,-889,-779,-537,-201,-53,293,514,-104,-599,-120,-170,535,-465,999,385,-291,-36,789,204,-513,-917,-638,294,210,-887,-75,-976,-467,684,-535,-415,-601,100,599,-984,39,-812,317,-563,838,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-204,581,876,1,293,674,732,296,-8,328,-387,742,-901,-924,22,-659,-409,-185,81,345,-127,-42,-91,-470,315,48,1,271,350,159,-877,288,489,150,172,235,341,-827,-514,-860,511,700,172,107,149,-224,-860,680,794,-791,762,-224,-802,967,-682,-780,813,191,-245,544,-223,-95,-373,950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{559,181,-15,579,355,-614,337,731,-653,806,824,854,-769,982,378,987,282,-657,-11,-742,806,726,-735,35,-916,140,-338,-420,-664,704,212,-811,899,425,-417,74,548,-209,274,-906,-300,65,852,-170,-924,-448,288,-486,-646,161,624,797,-694,-867,512,486,969,911,454,-979,519,-147,994,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.w3c.dom.Node,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:aWQoJ3REWlVQeGVyX20nKQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{962,311,88,-66,661,-601,61,264,-829,407,-749,463,-775,702,-540,810,-458,842,269,548,530,-128,-257,-503,-972,-486,685,469,-608,-96,689,997,-995,751,-949,-600,-350,121,-10,798,78,367,-458,-680,-416,-808,-978,-543,-562,-708,987,292,606,586,291,-849,353,647,977,114,-940,-382,-478,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{68,381,-102,552,207,-754,632,762,-1000,-384,-865,302,-717,1000,-525,1000,-166,-257,718,383,-351,-393,-215,-392,-1000,-510,724,108,-796,-509,-613,655,-1000,777,-644,-44,450,110,-774,793,154,785,-846,-1000,23,-1000,-690,-25,451,86,1000,-294,893,882,-789,-1000,-484,-96,599,-336,-680,-843,-228,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{580,757,-531,252,764,-348,795,333,-849,78,-89,-412,-750,-687,-446,368,565,-1000,225,782,-1000,-725,-136,823,-1000,-946,494,468,533,-760,-354,-344,420,134,-15,591,1000,385,1000,532,-363,-1000,-1000,-613,-122,-718,-473,-903,479,-269,-413,631,104,-958,-1000,160,-571,-1000,429,578,704,75,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-295,-1000,-747,774,-405,-1000,-1000,-700,1000,-346,-1000,783,488,-50,99,493,966,-312,-685,260,-356,-617,943,-1000,-458,1000,-1000,-1000,484,-445,-831,-1000,491,-586,765,305,239,1000,-451,574,26,-1000,1000,36,-1000,-263,1000,-149,-1000,912,-630,-1000,1000,6,1000,1000,539,286,-1000,351,1000,1000,-486,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-539,1000,-747,774,147,-193,268,-1000,642,359,291,294,488,1000,99,-266,33,394,1000,394,1000,648,-598,259,-970,564,1000,-13,-544,-929,-831,-505,-1000,-1000,898,305,-795,1000,254,-534,228,373,-1000,1000,277,397,-183,-991,-343,1000,-630,405,403,1000,428,1000,886,1000,203,-4,-673,372,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{436,-936,-732,-1000,-1000,-275,1000,-282,97,-1000,-812,1000,-618,-580,303,859,1000,-956,-1000,309,-1000,-1000,-634,-1000,-812,-108,-936,665,400,1000,279,-1000,1000,-455,-18,1000,438,-83,-300,-927,322,-588,1000,-109,106,-1000,763,811,-276,23,1000,-1000,1000,-941,-522,-400,-1000,-574,-699,1000,1000,31,400,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{719,470,300,722,-910,1000,703,435,604,15,1000,-1000,1000,835,-1000,-760,647,533,-653,-455,-681,1000,1000,35,737,-587,245,-408,-1000,-1000,-1000,-213,781,-1000,1000,-901,1000,261,-928,1000,-663,-1000,-494,1000,-726,1000,1000,-771,-469,-392,-960,186,10,-333,22,863,-512,-671,754,442,-267,1000,715,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-103,250,-636,-81,-96,-1000,390,-6,8,-209,1000,-958,-152,631,-216,-340,-596,-853,-108,1000,-1000,1000,782,-1000,804,484,426,-1000,683,-619,317,-1000,-199,776,-275,821,-546,496,464,448,-327,-89,-376,-169,-762,818,1000,-212,320,-11,-999,-190,-1000,-115,-456,1000,687,452,-1000,-209,385,1000,-41,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{719,651,-75,770,-143,-234,652,576,71,1000,-689,1000,438,44,-765,-47,1000,-813,9,-455,1000,1000,-950,-953,737,-587,724,574,1000,-1000,-1000,-213,750,-71,104,11,50,1000,-1000,-702,95,-865,-377,-596,1000,487,1000,199,-396,68,-381,186,1000,-333,-413,924,-512,141,1000,-452,-197,-308,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{553,-886,-240,-1000,153,-1000,-179,924,460,-164,911,-245,-874,607,-63,898,-78,1000,624,194,74,-1000,182,373,1000,1000,-1000,319,654,523,894,1000,240,819,257,1000,173,278,-275,-436,694,423,-886,957,-18,1000,255,49,-209,-328,-302,-1000,15,281,-13,-1000,-790,842,-157,-597,107,1000,552,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{1000,498,402,319,-671,252,607,1000,-872,-896,-1000,-753,-1000,-471,-338,-1000,-78,-1000,478,194,-709,-433,63,830,-742,-268,598,-1000,-1000,-1000,-1000,970,-989,66,-273,-165,-1000,1000,-1000,-180,694,-309,-1000,-473,-22,-548,-123,652,-1000,742,166,-1000,603,670,834,334,553,-987,844,-247,334,81,621,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{55,-596,-573,-941,160,69,289,504,-510,-710,1000,-545,-201,297,-398,981,14,1000,-719,1000,282,786,487,556,243,-241,-598,229,774,249,390,-354,-357,-593,-653,1000,-414,-405,1000,-367,-270,887,-1000,116,-8,1000,367,-120,-748,152,-36,-490,-955,-69,-612,-784,-1000,923,-93,54,193,678,-364,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-1000,-799,108,-1000,-98,-585,285,-820,-515,713,-818,1000,1000,-427,-102,97,-626,1000,63,-14,575,-767,-1000,-137,542,-1000,474,504,367,968,823,-285,374,-314,-1000,-320,-705,-17,405,160,-151,209,308,459,-163,-544,-241,-413,297,-102,-1000,-1000,192,-513,-697,1000,-16,691,548,784,1000,1000,-719,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-58,-821,648,-227,404,321,801,-400,120,42,-1000,1000,581,-957,1000,397,-415,1000,-658,-731,1000,-473,399,575,-409,-794,-410,-600,400,-9,400,-400,490,1000,-590,-150,-609,-441,-400,-1000,-225,781,447,-350,-974,-1000,1000,645,-207,751,-8,-611,586,166,-368,-1000,1000,-129,452,-1000,-145,315,-1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-156,153,-528,-841,1000,-83,884,372,-677,-29,872,265,1000,422,1000,341,-842,950,501,-921,869,1000,-326,-205,-473,-1000,796,1000,593,305,-300,243,58,879,-104,-306,-524,-275,968,-698,-876,18,-552,1000,283,-384,1000,-417,-345,-584,-256,209,-447,691,-216,1000,695,217,28,139,727,-38,183,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{237,533,-285,327,-637,737,-315,376,-802,47,-859,-966,274,513,256,786,-290,986,633,471,-285,233,721,841,-598,587,-667,-584,141,667,-660,510,-470,194,966,630,-584,488,40,776,-653,924,-998,-380,335,277,-486,-984,878,692,-893,-153,-182,828,467,-176,784,-388,682,-482,750,-981,-960,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{270,75,-732,554,581,229,1000,-969,-504,452,-234,375,-449,-140,-1000,86,-591,613,-246,-365,-1000,530,-433,842,304,315,-226,-731,659,731,72,-134,492,-979,1000,700,-855,1000,1000,-927,776,555,-668,134,-115,1000,-873,572,1000,-1000,-848,-280,292,-1000,-192,-1000,-1000,-1000,124,-618,-954,-317,1000,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,73,-291,439,-1000,-32,-16,1000,-716,1000,746,1000,289,1000,-847,1000,-1000,266,1000,1000,-1000,-500,256,535,-692,236,-1000,-1000,-528,-1000,-558,935,549,-1000,1000,-713,1000,-719,82,-811,-1000,40,-407,-1000,922,-375,-406,360,-306,993,-760,193,477,670,-752,445,-1000,-564,-559,295,294,-1000,-36,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{342,-1000,-36,168,1000,-299,-587,-1000,122,-170,1000,724,-174,1000,-1000,-240,-261,479,-1000,-390,359,206,357,590,460,-542,-819,-499,398,-495,55,-833,290,314,-20,-1000,-1000,-210,248,789,-618,-1000,1000,-541,91,511,1000,-19,-159,-1000,-1000,977,-140,-391,973,700,837,-1000,-944,-585,994,-281,439,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-38,96,-46,-910,-978,-678,-825,-180,538,672,666,-774,526,588,-640,831,559,-773,-310,-461,933,-192,693,122,376,-733,982,728,-292,-259,-604,556,-965,-829,-768,-153,500,927,-683,-944,-586,771,-156,672,-350,719,932,-472,611,-632,-877,-375,772,-859,-305,-469,849,566,579,229,-56,152,902,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{91,1000,-702,-656,696,1000,680,1000,694,-605,-1000,1000,-1000,1000,244,98,-1000,1000,21,929,-191,-1000,-1000,1000,-187,-233,-1000,1000,1000,1000,-1000,1000,1000,-85,48,-1000,489,-704,331,467,-1000,173,1000,990,-475,1000,-458,751,1000,-1000,840,-1000,1000,1000,348,-1000,-68,1000,-1000,-690,206,-318,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-714,-589,-912,-421,-257,1000,-1000,1000,67,355,-1000,648,1000,-961,-843,276,-1000,718,777,1000,729,-5,1000,761,330,-1000,197,1000,-935,-1000,143,153,-335,-228,-281,1000,-535,179,1000,-669,47,845,1000,1000,-1000,924,588,1000,1000,-1000,293,499,-1000,-1000,1000,-726,254,779,660,-925,611,-56,-654,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-704,-597,750,88,12,479,888,359,-729,549,208,-952,924,101,145,-635,330,279,321,-83,993,825,-474,-443,-413,252,870,138,-236,244,-636,987,388,-904,707,307,125,-612,637,566,-325,-982,843,-539,-618,230,145,-966,-587,323,409,-865,268,382,-499,-428,979,520,482,29,-956,-82,-821,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-580,1000,-447,224,-422,1000,642,545,1000,-626,977,-247,-280,-1000,376,-833,-31,-1000,472,-535,256,514,-681,-509,1000,1000,1000,1000,-439,875,-825,-441,259,-947,634,-110,-627,-235,1000,-316,-497,-561,-1000,1000,-1000,1000,109,-662,388,-911,1000,-1000,647,-185,-895,-1000,180,-567,-94,-1000,59,-469,-737,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{-401,-1000,-975,470,-256,-1000,-640,663,701,-737,-577,1000,1000,1000,733,1000,28,-886,171,-959,-1000,772,-1000,-400,774,1000,392,-1000,-412,297,-1000,-1000,-196,929,150,-400,665,131,235,-1000,-732,-369,-1000,1000,-1000,-820,1000,-1000,-754,74,14,860,-1000,-25,1000,-1000,-535,-714,-651,-251,-1000,1000,-51,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{1000,109,-285,-181,463,-386,120,-611,183,621,442,-551,-6,-254,-1000,457,-951,-408,726,-18,-846,-1000,763,-585,899,-1000,236,-1000,644,1000,6,117,-753,25,-675,306,823,619,261,145,-1000,-348,-289,-87,1000,381,-910,800,-131,224,-927,-649,-361,-1000,-73,255,1000,-603,153,-258,-500,202,-563,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{265,-819,-504,-44,-224,-873,-647,-145,1000,583,-284,-1000,251,1000,-1000,-1000,-221,811,21,-173,278,-123,763,-88,141,-400,-138,713,-1000,-121,973,6,50,-1000,-107,-1000,-161,1000,1000,-746,1000,-58,288,-703,1000,1000,261,-551,952,934,-1000,-158,-76,-625,400,591,-423,-1000,-157,-444,-402,1000,669,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{833,-487,-249,-306,632,492,740,629,-229,457,789,311,-856,519,676,799,750,490,-919,493,399,950,-568,746,184,-352,132,-633,782,-420,-667,-161,418,-248,627,-579,-534,-831,-454,487,-910,-101,606,-584,-936,204,780,134,-17,-324,-248,-951,422,-934,60,479,304,87,-647,952,-276,829,368,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{775,-828,-300,-947,-1000,177,740,-1000,-609,-326,912,1,-423,519,-50,799,472,430,-744,493,-1000,314,952,574,79,-469,585,771,637,1000,319,32,1000,257,-614,-1000,300,-831,1000,961,404,906,140,-606,-936,382,959,1000,-426,-324,-567,-920,1000,-79,60,124,-481,531,169,952,-86,817,-547,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{208,292,819,-733,-1000,24,1000,-651,189,205,70,638,60,-1000,273,-403,-175,1000,1000,-911,-264,269,-19,365,-1000,-1000,-829,-95,517,-1000,891,19,128,898,991,-677,432,535,1000,-1000,50,60,-467,-310,-1000,1000,-1000,1000,-686,-1000,-1000,-1000,-552,1000,-585,464,76,-646,-583,-1000,722,346,-873,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-469,485,-168,-374,396,1000,-1000,390,100,1000,114,1000,-223,-1000,-409,164,-1000,1000,699,767,457,-925,10,775,1000,226,363,-1000,212,67,1000,996,-109,934,-464,954,-811,-245,-673,-710,747,429,523,-785,-221,-1000,1000,-1000,-536,-955,-362,-1000,-1000,-96,-706,-408,-209,-290,-25,-689,746,835,-1000,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{840,-396,-168,742,627,-994,398,-74,-814,560,-973,-558,154,697,599,-460,-197,-994,-861,-560,244,949,335,816,-108,-627,-124,641,793,-661,-986,839,793,-987,920,-592,-992,136,693,540,-453,949,94,-404,-958,339,-460,766,169,466,218,715,49,143,742,630,-816,850,-714,-361,-566,-588,-267,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-1000,1000,465,38,-26,1000,310,495,-230,208,924,962,223,-1000,-1000,415,15,1000,-950,378,716,-348,793,1000,1000,1000,1000,-922,-524,406,1000,3,718,1000,-1000,1000,-370,-243,-593,-1000,723,-574,-502,600,10,-518,200,-640,-120,218,-1000,-201,-1000,-378,-111,-1000,-1000,-1000,-1000,-1000,1000,224,-630,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLocalName(java.lang.Object):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-1000,-16,-651,-1000,-535,-665,-869,254,1000,-90,1000,174,-95,-209,-1000,-1000,-382,277,-238,-347,902,-1000,-1000,-676,827,-353,-1000,-1000,-763,936,1000,-636,-591,62,-1000,524,250,-890,170,-1000,-550,-462,1000,-1000,1000,-1000,-269,810,-72,-382,-1000,802,-1000,-510,152,-254,-574,-1000,-976,-1000,945,-162,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-983,-171,-540,-615,-13,-128,-334,424,249,667,851,554,-1000,-429,-190,-326,527,860,-719,-273,601,-733,-681,-1000,-26,1000,274,-1000,-511,-49,-10,798,-1000,879,-1000,681,403,400,625,181,14,1000,325,-405,602,-491,410,-399,367,489,-593,695,-521,-610,-1000,-553,1000,-1000,-193,121,1000,1000,386,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{846,1000,138,-996,508,-894,-1000,-174,21,894,1000,-109,-604,8,899,-401,-698,-215,203,123,-416,24,381,218,-153,-539,-520,1000,1000,899,761,603,538,12,1000,296,-1000,-1000,-1000,112,-1000,797,1000,659,-317,1000,-1000,69,-351,1000,-349,-1000,100,-322,-249,-424,704,-193,152,25,956,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{-765,113,-624,1000,-1000,22,-842,243,-1000,-920,-383,-663,-1000,153,95,-1000,814,632,-256,-572,-1000,-661,-622,-568,273,892,-788,-171,-95,437,104,-194,-1000,-1000,711,-582,-278,893,-463,-477,-4,70,796,622,325,1000,-139,-1000,451,-1000,-229,-330,-87,393,847,-1000,147,370,660,-1000,-569,1000,-873,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{758,-965,-363,-757,405,-1000,674,-1000,371,-837,-215,1000,296,-343,64,717,-1000,1000,721,-239,902,-552,140,635,864,-597,-714,302,1000,220,209,1000,588,962,1000,506,-648,1000,344,539,227,762,144,-515,72,-417,1000,117,305,391,-376,-848,-567,-357,-400,333,-970,1000,513,-256,405,422,-923,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{-229,-822,434,79,-138,-428,37,945,-766,419,631,-467,586,-560,-214,562,815,-110,251,461,14,-688,-885,-850,-441,212,363,133,235,-817,-422,-123,-670,933,526,-326,134,661,-75,-557,411,253,835,-752,26,924,793,724,180,-952,55,-965,-553,-781,155,518,-832,822,-688,-252,683,-196,-770,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-108,761,480,-1000,-843,972,1000,849,-1000,847,-1000,937,1000,868,905,641,-1000,1000,803,124,544,383,-1000,220,-26,-231,897,951,-41,138,-957,-1000,-915,-546,48,-1000,1000,-751,-1000,-48,466,-1000,-399,845,-827,-703,532,-1000,1000,701,-157,323,-1000,654,-653,-162,-489,135,962,825,-172,634,1000,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-423,1000,204,-182,289,843,1000,-465,-450,1000,-666,262,420,-1000,-22,-946,-1000,-1000,-1000,-1000,1000,1000,15,-258,-966,73,897,352,1000,-343,-358,-1000,-1000,917,-86,-881,-49,994,-1000,24,1000,-873,-180,307,-892,-1000,-294,-671,-202,209,-1000,-818,41,603,1000,1000,558,391,-410,919,756,-199,681,-826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{133,705,36,-597,-1000,242,1000,-11,-352,1000,-151,1000,1000,170,1000,454,-829,1000,-1000,-1000,575,-438,-1000,1000,-280,73,-220,1000,-92,1000,-957,-1000,-92,-301,34,-881,939,218,-923,328,450,281,19,-555,-644,697,230,-1000,531,4,-711,-245,-1000,-53,-1000,-52,-570,391,-387,-35,650,315,376,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-910,-1000,-675,638,-1000,253,-693,-1000,-1000,-1000,-556,-200,-321,-256,904,-430,1000,888,1000,757,62,-348,-1000,209,-428,-542,-1000,-744,981,-23,-52,1000,291,-1000,1000,1000,-273,-539,-820,-208,-1,636,-1000,46,-593,-610,-97,-727,517,-1000,-1000,19,-1000,-112,-83,-1000,526,444,841,-179,97,-1000,-920,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-246,-375,-240,-489,-1000,-1000,-438,-568,-881,-296,546,329,196,-1000,-282,965,-695,953,1000,-900,126,429,-1000,953,629,907,-473,-733,1000,-1000,-632,487,311,-566,943,-989,818,454,-93,23,-775,694,-950,-634,-394,-1000,-302,197,186,-1000,173,207,579,209,743,-402,473,422,-366,-151,93,-961,-632,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{618,67,-899,-490,-361,-241,-120,469,-372,-202,-86,460,167,174,-431,396,89,-179,-830,-853,-190,-10,516,809,-586,-363,125,-310,-272,-623,600,442,-295,-872,-699,260,-683,-507,997,870,854,107,-428,149,-966,-267,979,651,-345,710,-350,-929,-343,515,-653,703,393,-345,-959,360,700,-104,829,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getPrefix(java.lang.Object):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{-75,-433,424,693,-134,502,-65,-812,-480,513,788,-57,-767,-536,-747,-867,374,466,-716,200,348,77,535,744,248,966,284,-107,-835,-595,-235,-488,-717,-284,15,154,-537,-377,-528,-720,251,377,-688,950,-27,-564,778,897,-96,-451,-402,738,378,128,20,-99,-874,959,919,-223,163,513,-977,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{-1000,-233,-705,-566,19,-361,1000,598,102,-1000,-700,338,24,-597,373,35,-1000,-912,-810,229,-318,-10,-350,1000,-1000,253,1000,849,-881,-460,1000,-660,-391,898,-344,1000,-884,1000,46,-1000,670,-1000,1000,638,1000,628,-969,193,-1000,90,-34,-272,-779,269,65,776,383,206,619,545,-382,340,1000,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{-143,-885,-1000,68,1000,-987,310,607,-498,-1000,-176,967,285,-289,-1000,-467,-29,-1000,-519,-876,-343,181,881,18,-163,205,11,155,323,491,-638,-1000,639,1000,-908,50,574,-355,-907,-577,370,-195,859,329,1000,-519,19,-104,-888,723,1000,-1000,468,503,116,672,-288,639,-504,-425,834,79,-239,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{526,-172,-237,-1000,-1000,243,1000,1000,-704,1000,54,-762,1000,31,340,-111,340,-307,-576,287,-1000,18,-748,-530,966,-461,-346,332,495,643,-46,-66,-607,1000,1000,-413,-730,836,-90,583,-727,-1000,-1000,-833,-1000,-1000,-1000,744,30,-1000,-773,-786,-248,606,1000,-1000,-564,645,542,-1000,1000,729,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{1000,-587,480,99,-307,168,919,887,-989,1000,-54,-612,942,296,162,154,335,-646,-53,954,-948,827,-485,-1000,-382,134,-952,355,-998,68,541,292,-696,56,135,-822,-264,852,311,-572,-1000,547,-757,950,-145,-531,-735,1000,857,463,-828,-585,-865,1000,-361,1000,-808,-76,-489,-474,572,468,-675,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{526,-501,225,-982,-870,-151,1000,356,-1000,681,54,241,1000,323,534,-103,-96,-905,-576,327,-1000,349,-786,-530,-14,-195,-346,-91,495,-1000,248,588,-1000,1000,47,-413,-730,1000,284,166,-451,-611,-1000,-413,-397,-1000,-1000,1000,-986,-1000,1,-786,-248,421,351,-1000,-831,653,-411,-1000,1000,-57,401,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-249,458,730,-776,-995,-420,-175,-357,-913,1000,493,-760,192,-460,310,150,-51,-1000,-235,492,1000,-341,-956,-851,-38,236,628,-656,-205,1000,-589,-843,-66,642,791,933,-1000,1000,529,1000,-114,1000,-920,-855,-577,949,-775,804,746,-1000,-957,-1000,-333,-1000,-189,694,-563,-499,-1000,529,285,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{645,-249,132,778,20,-39,-749,-990,60,448,934,375,-231,540,859,-40,-348,975,646,-675,655,-304,559,-286,-609,-405,497,-649,-272,-476,250,-207,223,-543,876,650,-191,322,551,-558,647,764,268,-182,-886,813,-654,922,-453,310,-116,-696,520,-191,-993,452,210,-362,42,-257,-298,813,589,422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-893,-443,52,697,-744,-365,-890,784,779,282,-19,935,398,-305,981,492,510,-100,184,176,922,378,-856,350,859,-786,200,-546,386,629,347,157,-215,-643,-353,-898,359,-51,-445,-432,636,814,605,833,13,545,-495,-399,-488,-53,802,-556,492,-963,53,-103,942,267,-308,-441,-740,-763,-381,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{-1000,-517,-546,-1000,-1000,1000,-886,-1000,496,-433,-60,778,-805,-663,219,-830,-585,63,-362,-518,-681,-1000,119,716,-590,-1000,-85,28,-853,-1000,303,1000,131,-295,-421,-537,-682,794,1000,216,-474,-746,-219,1000,-930,-1000,-388,1000,755,-802,310,-431,393,-1000,-49,-304,959,-995,-386,1000,460,1000,880,979}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{919,-90,-765,1000,985,-370,-594,-139,-1000,-104,-277,916,742,-1000,-596,-13,1000,464,640,-535,225,386,-696,1000,1000,461,-1000,1000,195,-481,-163,729,-244,453,322,1000,1000,-627,-431,-613,-545,-670,1000,1000,-960,1000,-1000,-1000,-1000,210,1000,127,973,-84,468,-427,220,719,580,-157,-488,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{-1000,-545,-990,198,-1000,989,-229,-1000,246,-799,547,1000,-846,342,348,-375,-407,885,-88,-943,-544,-881,-384,-200,82,-499,417,469,-220,-724,-123,1000,49,-829,427,-968,-675,1000,1000,411,276,-1000,2,1000,-900,-1000,-336,510,1000,-363,915,-1000,-9,-699,1000,-478,-425,-167,99,644,538,1000,89,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-985,-1000,969,-1000,1000,1000,-405,1000,-830,232,-97,-1000,1000,-668,-264,-431,1000,-1000,238,179,209,1000,860,-504,-1000,-1000,709,-471,1000,1000,865,255,771,-1000,-8,-1000,-800,361,1000,663,1000,1000,-1000,-1000,-1000,1000,1000,-7,-263,360,848,-1000,-1000,301,595,-798,-712,-1000,-254,-1000,615,-1000,-1000,-374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-516,907,333,-116,80,-934,-314,304,-606,1000,-882,387,1000,224,754,-447,679,715,15,129,-517,459,484,1000,-973,197,-363,-955,-1000,-923,1000,362,-619,-1000,-76,-402,593,71,105,272,-459,-75,-1000,313,847,-619,-1000,331,677,334,760,295,556,-856,-1000,-1000,48,123,1000,324,992,-863,61,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-788,-612,378,-112,1000,311,-210,345,-127,-829,-97,-1000,950,-433,-969,88,684,-449,-886,638,-262,1000,375,-821,-1000,-1000,1000,-424,1000,799,865,-14,943,400,-448,-1000,330,91,1000,1000,606,1000,-1000,-1000,214,768,-400,31,-62,431,558,-89,205,1000,763,-1000,688,-1000,744,-1000,478,400,-1000,439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{405,-901,-207,-40,-221,-775,-26,-1000,1000,-1000,-1000,1000,974,-685,-491,155,990,512,-1000,893,-171,803,-78,-1000,167,-134,922,-82,-1000,714,5,1000,452,-1000,1000,629,171,1000,-383,222,974,1000,-733,730,689,102,1000,-383,-355,-965,1,-665,-249,158,-539,-1000,441,743,158,-1000,1000,1000,-335,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-444,-107,-840,123,-483,-1000,171,-456,668,143,950,-948,491,-1000,33,-1000,265,58,-473,351,-299,-193,237,-839,407,-212,-338,264,-346,-756,-357,-320,505,-154,-802,963,-23,200,-972,-511,157,673,234,-188,-600,1000,-913,1000,215,-448,127,60,495,496,516,610,572,-225,1000,345,306,-1000,232,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{970,51,-552,800,746,1000,-958,211,-343,-319,250,-719,1000,-601,789,-169,-1000,-999,1000,-971,1000,-378,-855,-240,641,604,-1000,-248,825,121,1000,-365,-288,1000,90,-917,1000,-564,1000,-63,-1000,-1000,752,-67,-1000,-566,1000,-322,-669,-59,298,-184,1000,119,-556,531,-617,415,-547,-1000,-960,-1000,-534,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{475,458,-567,43,-638,801,-272,1000,1000,-583,337,246,1000,424,-491,282,-428,-185,454,-46,1000,-325,670,490,-704,-735,1000,-896,703,843,215,765,-448,609,311,323,-588,-332,814,-585,-172,-185,312,1000,-1000,1000,290,-84,-191,-290,888,356,-644,673,-575,689,239,-190,130,-1000,106,302,-1000,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{1000,-728,21,-429,-336,1000,297,577,575,-1000,-1000,-83,316,1000,-256,490,677,-31,177,745,630,-251,777,355,-214,-1000,503,-816,-1000,1000,-416,763,-370,-1,-1000,-106,-493,-161,-167,91,166,718,579,484,-1000,649,-330,652,-1000,423,145,102,-571,1000,-635,135,1000,163,-1000,-782,729,348,-1000,857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{167,975,636,-205,882,285,251,140,874,278,-144,-388,466,255,-107,-296,335,459,-645,496,1000,272,1000,-884,-958,-210,182,578,-92,-772,579,-1000,66,-977,-452,-1000,284,-673,-18,453,-354,612,-1000,-371,-766,897,-337,-89,356,-616,-837,161,-733,87,-850,-802,417,418,-62,-220,-765,-74,-510,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{843,341,-24,-796,40,238,-155,388,-658,1000,316,114,-1000,-1000,237,-834,37,-946,43,287,-401,-600,1000,-224,506,-660,561,-647,-1000,-194,-281,473,16,477,799,-34,683,1000,-214,36,182,-1000,463,1000,-632,1000,543,821,-1000,-126,859,1000,-693,127,1000,-530,1000,1000,1000,815,-171,-634,257,571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{1000,279,30,-307,295,-428,1000,-324,717,-207,351,-41,-986,-162,217,114,-766,-672,1000,1000,35,201,184,-249,-53,753,611,572,-702,-46,-798,1000,-307,-833,511,-1000,-1000,190,-237,1000,1000,1000,142,-1000,1000,-567,-716,-347,399,-428,-499,-1000,1000,-498,-1000,27,312,-401,-827,-338,780,76,-1000,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{1000,1000,0,-972,730,-343,1000,-1000,501,-243,1000,-236,-1000,-496,122,-574,-733,-317,1000,1000,420,222,548,-44,57,890,587,1000,-1000,990,-812,966,-77,-592,410,-1000,-1000,-722,-35,1000,832,1000,1000,-1000,1000,53,-1000,-500,117,-919,-1000,-603,1000,-1000,-926,289,-8,262,-1000,-1000,1000,394,-1000,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-824,-73,18,1000,-1000,-936,1000,-1000,993,1000,169,45,954,-296,-1000,244,-1000,-866,170,595,-883,49,-1000,1000,13,70,-984,421,313,9,-1000,1000,1000,-853,576,-656,548,324,252,-169,-176,-932,1000,406,-1000,1000,716,-829,-710,740,-272,-1000,-574,288,-939,50,705,-1000,55,-202,653,-318,-665,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{851,-141,969,1000,373,1000,-237,-118,-972,-645,-507,917,409,-456,-535,878,165,-711,207,1000,-393,1000,57,871,-609,1000,-368,1000,-860,-1000,10,-222,1000,-877,-100,-178,-1000,-244,1000,-349,1000,-1000,1000,1000,-375,-1000,923,1000,-1000,821,-182,668,-1000,333,-57,-494,424,139,939,-529,1000,-734,-900,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{88,-299,-378,-396,-492,461,622,-51,1000,-843,370,346,353,469,218,-314,250,-600,912,-71,246,23,-473,894,580,-645,-294,-400,503,1000,-346,33,706,-779,43,-1000,-1000,133,98,988,-262,-363,-690,363,-716,821,32,-1000,183,471,5,-1000,-398,-463,-1000,773,326,-925,-448,1000,513,-302,-552,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.model.NodePointer,java.lang.Object,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
