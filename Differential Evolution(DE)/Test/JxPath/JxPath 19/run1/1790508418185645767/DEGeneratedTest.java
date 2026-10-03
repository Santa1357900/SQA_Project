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
        org.junit.Assert.assertEquals("java.lang.String:aWQoJzN3RDN2QVNSJyk=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{-518,-853,450,890,-26,-696,-97,-589,-427,8,-352,742,962,784,173,-248,-585,-373,580,-467,32,-308,326,-117,828,-594,-797,-15,811,188,-352,507,161,276,849,403,951,-604,-125,548,-565,-989,-979,-10,913,700,956,-895,-635,867,-122,-794,653,-583,-162,292,-62,-711,712,-501,-870,838,-176,583}));
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
            new int[]{494,118,-47,948,-918,-797,496,743,-231,-673,149,-705,-264,-263,890,-549,36,-873,-888,-637,450,-18,-311,-458,370,-600,361,949,-576,880,638,-828,564,948,-226,830,637,-308,-872,167,-928,-721,-778,478,272,-636,-858,475,-485,919,-399,12,-265,467,927,681,-558,970,-482,697,183,-321,907,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{858,-295,-255,340,41,504,-200,428,349,327,95,465,-702,-462,657,377,967,-286,-691,-958,-741,106,-39,166,-261,-990,779,182,449,-150,-205,0,674,-305,865,354,-706,379,-663,3,-950,-535,249,-890,-191,-153,426,744,769,974,-505,-353,420,-616,-550,-856,23,-473,188,418,-581,-306,-789,-791}));
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
            new int[]{-461,-50,-934,63,251,146,-23,744,386,471,-228,474,-586,-240,434,703,-972,-149,396,326,-711,485,3,-260,784,843,-243,885,-372,-532,345,14,745,226,795,-104,-538,424,-438,-371,212,239,786,-664,-133,220,561,-376,55,-239,-370,-120,-258,179,-388,-774,268,-681,-400,-708,82,426,759,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-679,17,428,865,-821,-84,-690,-574,-440,-398,-94,-791,-688,789,-225,-780,641,562,-653,-860,187,-485,349,-186,-854,-99,613,-548,799,397,-654,728,76,768,-804,-41,628,-454,-647,-336,302,688,-617,-91,-129,-891,448,62,407,-49,-837,867,-206,541,-531,-953,504,-957,-433,-946,-972,47,238,-954}));
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
            new int[]{385,-425,273,807,-998,-946,907,-616,742,873,-802,563,496,139,99,510,-230,-925,-667,-948,526,-44,542,-635,786,-284,-250,370,17,740,-650,-587,-428,24,-875,-158,-667,-261,699,-847,92,51,250,550,186,10,317,-66,427,281,-897,-372,-520,556,804,-520,478,587,-835,-665,904,956,750,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-600,413,332,417,-248,-71,30,-337,-832,891,-213,-576,-705,-825,-418,-860,-236,-629,379,410,891,328,555,-836,-535,-786,-170,795,-158,-883,-328,525,562,-657,-740,-96,-636,-919,-56,-788,-932,-88,851,766,-892,759,-199,-345,698,299,-994,247,-437,606,159,-997,979,-138,497,544,-543,-689,132,733}));
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
            new int[]{674,37,921,569,416,811,680,8,64,-205,-507,-80,-162,618,526,333,-412,865,803,-777,-208,-924,-922,-954,-968,100,156,482,18,-596,-24,746,-790,76,870,-531,770,815,-697,-990,138,960,-932,283,-111,-255,-550,-264,662,-591,172,-88,961,-486,-905,-796,-590,900,554,-130,-362,60,25,-848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-696,-76,-911,289,127,126,946,606,-850,-24,-607,-726,64,793,-705,-377,48,-286,-98,63,-842,-458,-527,-218,-880,-125,-957,572,35,400,855,559,-867,748,-922,-136,733,736,832,-755,880,3,235,633,-284,-958,-750,-866,-381,489,396,-442,-659,119,100,809,-22,-514,-635,604,-567,384,758,866}));
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
            new int[]{969,-200,-782,-532,-53,940,-867,279,898,-130,-537,-600,760,88,-703,-135,-257,-766,86,129,-881,-874,383,988,-203,208,233,-863,959,581,556,-519,-277,-646,947,524,-180,-575,-269,204,-474,37,799,-612,765,403,817,936,200,584,-312,-323,-636,384,842,975,478,-494,-138,632,-572,457,248,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{624,944,887,-692,-61,342,-377,-800,392,-1000,-714,1000,1000,223,1000,597,-631,1000,599,1000,991,1000,647,-610,493,1000,-303,-430,732,687,-1000,-1000,380,-991,551,1000,-1000,-402,-751,-623,-19,-1000,-1000,175,572,300,80,576,300,-1000,-883,-226,-84,-316,-1000,209,95,1000,-1000,-830,-511,317,552,746}));
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
            new int[]{1000,-581,-824,-267,1000,-90,-479,-425,577,-302,-547,146,180,-819,1000,845,462,-881,-388,-1000,-204,1000,-899,656,251,-97,-655,1000,352,506,133,424,434,1000,1000,1000,820,-979,-166,-1000,676,-523,172,-296,-1000,-906,765,168,-393,655,824,-199,-160,623,992,-1000,-1000,-690,1000,-640,1000,-203,-958,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-266,-406,-439,550,904,-172,-191,-970,-216,511,-783,877,224,443,279,-250,-404,682,-37,990,892,749,450,-791,-138,-685,362,692,-641,-103,-415,-969,-134,-672,-346,860,499,-949,-19,-795,697,657,213,538,-692,-475,452,399,754,-126,165,-849,-375,-883,-753,323,-859,-987,841,837,-297,-236,-655,-588}));
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
            new int[]{9,-638,-346,135,-195,-450,953,696,579,382,-50,-313,-110,701,659,94,-194,912,-642,-52,-342,-330,61,-79,781,978,-344,-995,-891,104,-41,-959,-795,993,-191,423,-282,-867,-350,855,-882,213,27,695,60,307,-92,120,748,-171,789,-880,272,-948,222,-858,41,-435,-479,-875,-828,-234,-778,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{989,632,-118,-383,407,-320,890,-373,-598,-854,656,-424,119,932,157,-367,-261,761,-881,752,869,419,-164,-320,643,859,-748,760,-721,801,-987,-562,699,-898,165,-568,452,90,53,822,-285,-693,-442,15,371,654,-697,460,-671,812,-329,-708,118,983,-399,-338,550,-503,964,-591,765,-368,-941,-63}));
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
            new int[]{-278,-521,-936,-132,-213,5,-352,811,203,182,336,373,217,524,-784,-858,-1,-366,-253,-251,945,182,-336,281,380,161,36,583,803,433,-456,751,-375,-787,58,133,62,-942,410,495,-726,189,-90,655,185,-504,-429,-146,470,-496,-696,-586,154,-317,-614,801,-23,58,408,-850,211,-182,-407,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{212,-850,759,-717,147,-976,714,-922,-145,321,890,-384,338,272,-246,784,-286,45,-437,-29,-741,-606,297,-757,824,340,-450,-279,-971,-684,-276,-875,662,25,-522,971,360,35,499,-434,-154,-920,-546,-452,-283,-54,182,538,125,-123,-895,204,-443,-515,-801,925,-653,-690,850,172,-414,-631,-541,699}));
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
            new int[]{406,997,267,91,753,864,-197,-77,-183,196,788,403,-979,-421,780,-51,436,185,-594,-186,-826,249,494,-527,835,57,-739,-358,-770,-216,-231,-230,735,-721,-534,-625,190,-282,-218,225,-490,-50,476,-991,-120,-690,700,-79,-264,-315,-376,-660,-372,717,-179,674,-531,-854,-355,352,-300,512,309,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{-583,-88,-531,960,-477,742,753,103,-539,-127,974,440,280,969,-88,-136,-160,158,-200,131,80,-940,-120,-719,-141,157,570,-101,696,48,949,-480,-603,197,-21,911,785,-8,104,-630,-491,-852,-304,-259,288,-194,200,-594,822,-988,771,-854,-910,-283,-334,-494,795,900,-227,17,-722,979,-771,494}));
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
            new int[]{356,-866,194,253,885,-533,-679,-310,402,276,-360,-169,805,263,296,468,-616,-159,-532,-395,-528,-617,-991,-998,0,-110,-975,261,-438,-896,913,-212,251,-749,-965,-625,780,739,455,-391,-865,-621,997,-201,390,-985,263,751,-784,781,148,-720,-928,644,955,153,-887,387,214,-176,-559,-295,-6,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-838,-382,58,801,-704,997,-609,182,-920,-233,-242,-580,-94,789,592,198,-361,-582,377,-454,-923,739,-36,-973,567,659,-470,-555,859,316,853,57,335,-162,628,-968,-679,938,179,-248,-630,533,108,-572,-891,-817,-824,457,531,913,-971,968,474,779,258,-902,-599,-617,706,-349,-391,-504,-25,27}));
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
            new int[]{-763,-236,-619,330,314,273,-205,-939,-505,-416,135,307,-394,-720,152,-124,503,-974,846,-707,-369,361,-705,-709,420,-770,120,-822,472,951,-343,-254,603,724,-23,110,-651,-610,504,-272,-278,-254,-811,-266,-269,-617,935,662,-555,-233,294,682,-758,65,326,-493,273,107,-42,331,-823,-236,169,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-430,-748,-246,-450,-853,341,695,-148,-351,151,470,-471,35,-990,135,-697,977,-216,-298,-481,-469,75,-760,877,-366,-650,223,141,389,526,-805,887,208,-889,4,217,-344,-309,638,-644,-917,-703,-22,-962,992,-31,673,-312,-500,706,798,-477,226,-89,913,939,-557,-578,773,774,-523,282,985,-589}));
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
            new int[]{-58,499,-320,-965,-347,225,449,852,-154,22,-347,568,-359,-822,576,-989,-354,-597,832,447,-973,655,-72,797,-583,320,-17,-216,-348,552,182,820,440,-844,773,385,-172,-920,-846,549,591,343,896,360,-469,-21,459,-136,53,483,-246,677,-596,-859,-247,-753,430,-376,874,-820,872,-731,10,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-212,-820,-415,-731,-538,84,857,-53,794,503,-586,-159,-871,-982,-250,-342,179,617,-981,-130,-913,345,944,335,-235,-201,265,-112,-368,821,167,-882,-643,355,518,235,-21,853,-583,589,856,-161,539,708,-683,273,101,-21,576,228,-154,-928,522,46,421,37,303,-377,728,652,314,-242,-580,-975}));
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
            new int[]{828,682,-502,-966,-327,134,-190,-505,-13,397,-794,-938,225,966,-266,-898,903,-633,-352,789,349,-326,167,-199,839,974,-776,200,436,-191,-160,-654,331,270,152,508,225,-212,758,346,-573,-58,-916,886,681,-401,-246,-254,-481,596,574,436,-101,-191,-333,336,-730,-47,560,-387,829,732,541,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{-332,257,-998,-23,-337,-273,-297,-157,546,768,811,-705,988,38,374,118,-240,-4,-759,-582,900,-718,-516,577,-937,-532,-718,-882,-401,-678,-177,-184,611,-91,-893,475,722,406,-291,254,649,-679,551,158,770,984,504,344,857,614,257,-694,-125,-949,299,-355,-656,348,-898,140,-864,-218,608,155}));
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
            new int[]{920,49,995,439,-996,133,-218,-238,-784,500,-818,45,-314,-626,227,731,499,-849,-143,-353,-678,-211,546,418,828,-215,132,-509,441,570,845,-709,601,-606,897,191,-723,-726,-822,-647,-236,433,-451,-696,-55,-204,40,-889,134,-421,-149,428,-604,786,-525,-656,791,508,493,981,378,-684,-375,-934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{334,-214,-157,742,241,-956,-424,253,986,21,-347,743,-848,-295,643,501,-408,-726,-292,242,718,247,852,-399,142,-334,904,-633,196,598,301,280,71,105,-545,931,54,813,-210,-31,-336,211,-665,-309,-646,-113,226,905,897,-920,-602,887,582,-914,290,-730,-121,-626,500,698,394,554,-44,248}));
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
            new int[]{-243,-788,969,216,-588,784,165,-819,315,953,974,-304,181,-511,-293,284,-89,-30,627,206,664,768,-677,932,-40,-299,246,435,-682,-940,-604,594,-921,-395,898,-353,848,95,462,373,274,916,-408,487,803,469,-208,591,427,-894,18,940,507,-380,-876,-716,275,-446,170,80,761,940,-882,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{934,-919,-426,512,984,-257,-588,-58,-706,-389,-986,-989,680,318,599,-310,-919,-47,-591,-41,-881,525,-12,-644,-231,-650,146,348,-361,-476,174,651,389,125,-43,407,411,-665,-769,963,-933,-712,437,885,473,108,-617,-210,-865,367,219,-404,-442,-33,119,-113,378,-687,-482,-786,530,-413,-292,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-193,-149,580,-85,-810,632,-122,-272,-679,-962,621,697,555,766,-239,-896,598,-256,550,-448,93,-656,-252,-57,39,-164,701,147,711,-371,511,410,-239,353,-403,-692,-214,-613,-440,909,799,33,452,668,770,-779,-687,-451,-317,-629,153,-103,-609,814,-585,628,978,75,418,-499,-709,-804,-726,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
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
            new int[]{947,44,-315,923,-494,299,595,-123,-275,-424,-372,-67,-338,683,-967,-32,157,171,452,-863,842,-204,751,936,599,-986,347,334,-12,425,-117,607,438,248,829,-597,-376,-40,-390,-823,383,-999,-807,-328,-228,147,724,-637,-586,538,759,926,166,646,926,677,-697,535,420,-330,932,-370,-354,693}));
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
            new int[]{123,937,-449,730,404,-855,-327,647,656,375,816,818,-772,-536,-611,-777,113,636,-101,-871,426,-843,-105,884,84,-855,-285,611,-412,106,-189,-413,132,-60,-871,-24,-156,-811,-951,-751,942,121,727,-926,790,861,-660,801,-566,-750,675,717,-973,-640,-188,193,773,-333,206,574,484,-235,397,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{948,-943,-978,-326,-83,722,-293,98,-346,217,-213,617,-60,33,9,-946,985,346,-981,163,-615,-932,-973,-604,-893,-405,-717,-765,-242,-162,-362,-195,-228,402,790,71,32,-849,-199,539,-72,-192,952,-882,886,-265,-947,547,-17,593,996,615,448,-827,-526,-669,541,374,584,105,-393,58,324,-938}));
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
            new int[]{-346,616,963,-42,484,-686,-59,-339,-448,-644,-773,986,-416,385,674,688,-109,194,-38,-369,567,930,-40,-452,-94,-445,903,930,-617,672,682,-681,-127,160,942,-122,701,-583,625,714,-410,420,80,183,583,-686,-62,120,649,782,-420,-169,-202,-939,200,505,296,148,-758,720,-184,-545,272,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{-170,143,-795,633,278,192,161,382,-244,-958,749,-473,-731,-916,-496,-835,606,-936,721,-350,-968,-75,422,219,-749,-436,-823,605,-236,616,526,-22,-911,-909,-448,20,-995,-683,196,-847,981,-25,346,-44,231,-756,435,-301,-509,79,277,567,-483,-562,274,-377,-435,-43,693,854,659,980,761,765}));
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
            new int[]{-648,-836,-311,-353,-732,520,290,732,641,-953,-469,989,197,153,220,-743,-432,506,-795,-559,857,-741,-244,147,18,7,-503,500,-18,873,467,-170,-186,-99,-376,327,-128,481,258,-10,-61,929,-899,236,-415,-448,716,408,-330,-487,-299,44,798,656,633,-705,785,75,-699,146,780,-340,743,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{-523,575,-680,-819,-440,-713,996,-719,-940,-842,-992,646,989,-345,-648,689,746,812,-304,0,37,-474,-730,-536,840,-387,-503,491,54,511,639,-420,-834,-777,519,405,-413,780,-335,606,-880,343,-869,593,450,918,651,-258,526,560,-181,-347,197,-56,-467,325,619,-750,-931,-495,559,364,-150,-203}));
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
            new int[]{-414,796,-827,-382,922,-812,824,-385,918,666,-783,208,-947,816,861,372,-197,108,-670,-588,246,-172,-271,-667,100,171,-890,-333,-283,516,-487,-373,301,640,108,-562,-114,-687,189,-311,453,-454,-332,646,-19,-384,149,603,202,-95,-327,-448,346,-405,-228,-279,-728,304,623,-140,-961,-158,752,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{371,716,990,178,-812,-146,503,594,-856,-886,-727,-579,929,50,819,70,-544,-262,640,701,360,-344,965,-94,-495,-996,-115,-30,835,20,-608,-668,-121,660,-788,330,-556,-360,-54,-400,569,304,6,618,-269,-984,129,775,421,878,785,-884,-257,419,-235,-606,462,-358,-609,485,92,297,-654,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-681,-170,544,-450,-694,-926,-356,-469,-747,-46,-28,-144,421,-67,494,410,872,-448,815,484,-449,299,-321,-704,-242,-458,-906,-684,559,786,788,-292,622,-713,79,-350,190,735,848,596,-522,133,311,659,-361,-724,-472,-817,-366,304,-811,-666,825,-321,124,-954,926,-358,170,40,57,720,-401,785}));
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
            new int[]{920,-529,-884,-100,404,-182,700,-345,-402,483,19,-235,253,-365,7,573,861,270,722,956,549,441,-153,-905,-973,-102,-844,21,-677,354,-93,290,-640,343,-596,-862,253,430,-96,542,-381,400,-578,-216,-838,972,34,-730,-669,562,823,-315,-55,698,-250,-152,330,-246,-599,36,-252,-107,-156,-987}));
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
            new int[]{80,661,-71,-210,28,411,152,-360,-297,925,69,-213,-928,-378,38,-558,-64,858,-935,-707,65,413,648,-552,70,-551,-250,-838,-324,26,-290,-443,-706,20,-753,-434,645,75,558,410,625,-904,-4,-934,-477,-305,751,373,290,-997,592,710,-402,-695,798,-504,145,-934,-875,160,822,92,-89,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{938,389,670,-694,779,935,688,640,-667,-388,77,-359,-827,931,769,-760,-629,599,-120,-810,-688,-24,626,989,-620,754,682,269,905,842,-569,525,94,114,611,-262,-928,870,-901,702,-411,663,-241,689,-506,489,612,444,641,-757,-70,458,-460,-200,-712,-34,763,-848,85,-835,-102,573,-383,-192}));
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
            new int[]{-335,-5,354,-525,-310,-535,802,620,-585,-153,12,771,607,779,640,-847,-959,310,-704,-552,49,-413,-135,394,-157,-463,-559,-498,-206,540,-396,291,165,781,-169,129,-805,176,-939,23,895,543,-374,-481,414,-943,-927,-760,-561,903,-220,542,-295,551,-336,247,435,-921,-206,524,-752,-425,236,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{233,182,504,245,277,-166,500,979,174,948,-462,-542,-188,-754,512,-592,-321,13,-102,-906,-850,-90,649,868,285,922,731,-385,889,-833,983,473,-492,-744,-762,-239,-982,497,854,-876,511,329,-832,-431,-705,-916,784,-704,12,419,26,995,89,-425,436,640,56,989,775,707,-384,-257,607,231}));
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
            new int[]{-212,-176,267,120,-88,223,-213,784,-135,-446,-833,244,458,-958,274,653,628,486,636,-587,816,125,-643,-67,-322,-795,-475,473,175,-524,-421,-503,983,881,330,487,176,-779,587,265,151,-265,682,535,-84,227,-434,555,998,597,-384,-317,492,474,-211,-191,-950,-984,386,-318,991,880,-741,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{319,743,-239,35,-999,369,-128,-670,704,-711,-859,-346,-869,-667,-777,-988,-31,338,-245,-665,882,24,680,286,-653,967,868,910,507,308,210,-895,-237,-735,-737,-954,677,792,-133,-437,-34,791,-312,226,-158,-828,-432,444,-228,-253,783,197,840,472,-197,-767,334,-159,-974,503,-75,-597,-924,-568}));
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
            new int[]{414,856,-120,260,-869,-467,-743,-822,-672,735,969,-354,-263,761,-855,-413,-981,-737,-936,-593,-855,6,-577,519,-448,704,450,-695,-531,811,270,-234,396,-395,870,207,567,130,318,240,407,-323,-551,101,504,338,-847,810,-579,-128,597,-909,-241,-361,-746,-906,-621,670,430,-39,358,-917,-842,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-938,737,-522,-116,-83,-435,-5,756,787,-13,-626,-538,295,597,-956,611,-941,-969,329,44,746,-937,-986,657,882,740,575,-385,-336,-170,-192,862,-638,193,-63,475,-185,-983,-751,-15,967,293,-218,547,-538,389,684,576,887,-853,454,-690,410,502,-501,653,300,339,-836,-399,-929,7,-265,-425}));
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
            new int[]{-984,-140,701,-877,-250,619,-176,-872,-453,-783,-421,380,-661,-723,-758,-81,-70,-104,613,-876,-389,-296,437,-821,295,542,-786,582,-436,447,-228,537,-600,-46,-645,638,215,-937,911,-77,-665,654,789,-953,70,-380,-909,122,-863,-469,-433,-420,337,262,132,323,-879,682,823,361,-42,242,-648,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{554,-10,-122,159,859,750,-108,-917,27,-334,-189,324,449,922,-236,-787,-702,-258,486,719,-601,879,747,-108,196,343,-434,-262,999,-312,-20,278,-594,760,180,-233,-254,-823,-735,-879,-248,-987,561,-605,227,-818,-529,-106,467,-181,-823,915,-617,611,147,444,874,453,385,227,-166,-912,609,-461}));
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
            new int[]{930,550,-952,586,735,412,-239,-853,-648,-147,537,-676,417,-976,905,855,758,-807,519,645,-559,-154,-649,-219,191,786,-345,929,-222,-62,839,-763,-400,116,-562,-899,-275,411,-196,-798,-60,51,849,-937,979,587,168,47,-975,101,-753,188,-143,734,86,-445,-478,319,534,939,317,123,922,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.w3c.dom.Node,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-293,357,819,-66,-271,767,-1000,959,-675,-841,-506,431,236,1000,-282,-234,-351,260,-110,-413,-1000,-326,660,-753,31,-400,-180,275,469,522,1000,1000,1000,556,365,630,-833,-65,479,1000,-508,-749,50,201,1000,47,93,-558,-1000,-217,-355,705,-104,675,-410,-937,-32,806,448,-132,210,15,668,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:aWQoJy0weDgwMDAwMDAwMDAnKQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-927,-1000,435,1000,-1000,-696,-238,1000,-1000,369,-72,1000,1000,895,-375,309,-386,-138,-1000,164,614,-153,1000,265,1000,1000,-64,1000,49,987,1000,1000,-862,277,1000,1000,-158,20,-1000,1000,-134,-132,-652,806,-806,-1000,303,-215,-1000,289,876,-221,427,1000,-949,-1000,-1000,115,-87,-907,-533,-363,1000,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{1000,1000,819,-66,-524,-120,230,959,844,-398,-1000,620,964,756,-481,-1000,1000,441,-1000,-851,589,1000,-1000,-201,67,-400,851,1000,913,188,-1000,1000,1000,300,-1000,-1000,-274,-794,1000,115,-738,-423,-316,-362,-414,-891,1000,-1000,-513,-1000,-775,1000,-104,1000,-410,521,847,281,-202,1000,-31,-1000,-417,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-446,872,954,851,-294,-779,915,-601,-289,-774,33,316,-217,824,-839,-574,-532,622,-440,-151,329,-906,-51,-301,-678,526,515,-639,-720,797,795,584,-963,-414,-83,786,-843,924,-527,-37,-36,-855,-523,366,660,226,329,735,-366,-713,450,-726,806,-428,377,787,790,632,-517,-548,-985,-378,-786,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-487,-372,744,-4,-932,783,-690,-410,-387,-464,118,-45,-649,916,-314,813,948,-137,-237,-116,288,794,545,80,-165,-549,-180,-995,392,-239,876,-855,215,965,546,-664,840,591,-423,-980,740,979,690,631,984,-995,-858,53,-562,426,374,479,-282,-163,-560,-729,131,599,-722,424,107,321,-369,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-396,775,285,162,859,-411,510,-924,-104,-326,-319,468,-78,-883,-961,-760,612,-512,508,-7,-420,-264,382,-594,-860,-61,859,111,375,641,-19,933,678,960,58,-471,353,46,507,-583,607,-460,-829,213,-673,-332,654,-535,387,353,979,909,944,-700,976,-813,-864,-609,185,-877,-57,-64,795,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{1000,-1000,66,-1000,77,988,434,981,1000,941,-1000,-891,580,-289,-716,-1000,-485,-765,-12,1000,831,461,-219,1000,674,94,79,-72,-950,626,572,-945,-428,-1000,-37,-789,-841,-1000,163,-349,65,225,49,-690,289,1000,-406,-531,-525,481,460,1000,1000,220,-1000,897,73,-362,-500,722,998,499,-746,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-251,-698,561,-203,-265,104,579,-1000,334,50,-691,806,470,216,-779,-712,76,1000,319,103,-656,18,1000,46,142,-164,974,-6,-643,400,338,79,1000,10,-1000,-339,-1000,-113,311,597,60,637,-777,1000,-547,-1000,-377,658,277,-114,-229,-824,219,-809,-888,-251,-54,368,-34,257,248,1000,-953,835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,-318,-435,1000,-679,-459,-1000,-460,1000,581,1000,1000,179,-592,258,424,1000,-1000,840,-593,270,237,-203,850,166,-987,-745,-1000,178,1000,-702,-585,68,752,-566,-789,1000,-646,663,336,1000,945,842,1000,1000,-96,565,-1000,-65,-1000,-1000,812,815,1000,859,777,-1000,1000,-500,722,-763,-679,-746,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-985,-535,-174,995,186,946,2,189,-967,662,-716,-632,518,-824,-821,-401,-275,610,-285,-825,188,-24,308,462,402,-851,-879,454,-666,-607,-537,450,-466,-194,-542,-168,507,-745,176,325,-556,416,-227,308,-909,403,765,-259,-418,-890,514,-864,391,-102,-758,572,194,-478,-557,-723,443,-685,-550,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-317,-99,975,-1000,345,303,-1000,157,1000,-1000,-700,-164,-699,-492,-5,-1000,529,498,802,1000,-108,-318,-191,-196,402,1000,-856,-1000,1000,402,505,-1000,868,-164,1000,1000,-1000,22,776,-1000,1000,-941,1000,-1000,-1000,-939,-1000,-476,886,1000,-285,-311,557,-538,1000,938,-583,296,-564,1000,-904,1000,1000,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{362,1000,-675,313,25,694,-1000,177,1000,-1000,-705,1000,221,-564,1000,-1000,1000,1000,-697,-299,-1000,872,489,-686,1000,-1000,-737,-145,1000,217,-661,66,-761,1000,-1000,-1000,1000,-1000,1000,-1000,-916,-155,238,-945,-1000,690,-1000,-1000,-166,660,-674,-1000,1000,229,1000,1000,227,-695,956,1000,382,966,187,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{544,381,-882,967,705,-823,93,-239,513,-386,683,-920,-577,719,-123,-907,674,853,-162,-983,493,-406,-862,148,-786,-349,-526,358,214,995,401,-788,-928,165,-171,-375,-152,-949,347,375,-53,-629,799,-257,-281,-470,946,8,-398,-426,558,-349,418,-441,793,683,-762,797,-617,760,145,192,-238,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-504,-958,948,-240,-467,92,-22,-443,-916,116,-620,-21,160,712,-632,-322,53,-95,795,-190,442,549,-766,645,720,-811,456,-573,162,-232,-484,-187,-6,-713,899,-15,968,-41,-538,977,595,214,-708,620,863,-537,2,79,-54,508,-516,972,189,-899,694,-281,-504,317,-369,-69,107,122,377,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{644,1000,45,264,538,-1000,914,-583,-995,541,1000,-1000,-496,71,455,-1000,910,-935,-303,94,1000,526,-164,-797,-1000,-1000,678,1000,-1000,1000,1000,-1000,225,-1000,866,-914,-941,-1000,1000,-1000,-389,581,407,1000,1000,-974,-174,-1000,-280,-1000,-23,-1000,1000,-314,1000,524,-115,852,1000,1000,350,-784,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{148,324,-540,213,-1000,-165,-589,-1000,-602,389,-612,590,464,653,-835,666,-919,568,767,631,-837,-267,25,660,-710,-1000,-506,-44,-452,-181,776,-862,-240,-490,1000,-1000,94,-14,-1000,869,-12,449,68,-822,382,115,-1000,931,-231,-1000,147,-338,1000,218,-196,-592,-1000,146,561,-310,-839,88,-178,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{435,121,-540,378,-1000,-190,74,-1000,-251,478,-633,-174,-144,-162,-529,215,-1000,350,231,631,-837,84,90,660,-689,-460,-506,6,-938,582,776,-155,-889,-354,1000,-117,-25,-404,-1000,-79,58,-49,-692,-822,774,-155,-1000,-108,-248,-559,308,-490,865,-31,-177,-153,-1000,669,266,-310,-898,429,-178,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,566,66,-317,1000,199,-1000,886,-456,-557,534,214,-629,-403,4,-461,716,1000,673,400,328,-408,174,-419,1000,723,-193,-4,461,1000,1000,345,1000,37,-405,-1000,743,-1000,297,-61,1000,-214,1000,-51,-1000,-1000,-339,-1000,556,-552,1000,752,6,917,943,197,-333,-279,-227,327,819,846,693,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,11,-252,-1000,48,589,-716,99,521,252,-127,92,-779,-1000,138,-1000,-930,468,-1000,-556,-1000,-1000,519,-46,890,-1000,1000,762,-537,-1000,-1000,655,382,-855,-451,1000,1000,1000,-378,-57,877,-1000,543,966,1000,-90,833,-617,184,615,-771,448,-938,108,477,352,-1000,-71,-363,50,-653,803,-472,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{967,1000,843,693,-457,-157,-562,-363,87,213,1000,-1000,1000,584,1000,-31,-318,-560,1000,968,177,535,-1000,106,-551,-845,1000,-191,-1000,-1000,-694,563,280,553,1000,-466,181,1000,1000,-219,520,-993,-248,227,-418,-328,601,772,163,-1000,981,1000,772,226,318,-983,826,-1000,-373,303,-1000,-945,-767,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{587,792,483,-670,-415,-352,178,1000,1000,1000,-160,68,-634,-265,-421,-277,-149,-859,-1000,169,-1000,513,1000,-1000,-1000,1000,171,-44,-175,151,-388,505,264,204,-1000,-628,551,1000,-1000,-219,1000,-291,-819,-473,1000,-256,1000,-1000,1000,286,-1000,-35,-1000,1000,-867,8,-306,301,763,1000,-983,1000,78,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{299,-1000,-96,-1000,487,0,-425,-185,-291,1000,1000,101,-206,1000,-72,142,-1000,550,-356,-601,694,1000,834,-609,260,479,-629,-1000,-244,-407,876,-104,-587,-50,714,-288,-379,327,-491,13,-667,-371,-599,1000,180,1000,397,988,-262,20,-684,-100,55,-1000,-70,429,1000,-79,-1000,584,-109,327,146,-218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{248,192,-867,-950,-227,28,525,-838,-315,-448,-61,-345,-488,-549,157,-409,30,-305,841,22,335,-330,-920,174,72,925,218,199,427,828,663,746,542,886,-564,409,-396,55,-165,893,-973,960,805,677,-159,-362,-677,865,-286,-875,454,-29,-140,39,586,-562,511,-849,-191,849,571,-132,-607,983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{621,-986,-324,-711,-710,-260,926,665,-971,503,275,428,-885,309,749,222,91,398,136,-363,-753,691,-838,-221,368,365,-709,-461,-408,-165,-396,-713,-742,-147,188,502,202,-969,-821,-326,143,-329,-270,689,-498,-517,-53,-101,283,-523,-826,52,503,169,-158,-613,49,-968,-431,283,-180,103,-811,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{1000,-706,-168,-804,177,-9,736,1000,1000,281,503,-756,-681,1000,-915,615,-710,-1000,-809,-178,464,72,-656,-308,771,694,144,434,-174,58,-29,-1000,-490,838,-1000,882,-389,505,345,274,693,-335,-353,400,-794,-185,1000,1000,-671,-694,-230,857,-396,-445,-277,1000,-14,-182,115,313,-807,412,47,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{277,-584,-558,379,1000,-834,231,-48,-409,691,-336,278,-296,285,375,-871,1000,466,831,-410,-592,31,1000,-392,-233,924,799,-542,381,-1000,519,344,457,612,-204,245,738,3,-994,-126,-927,50,811,500,792,138,652,947,-1000,652,314,-667,-114,208,713,75,-197,-856,-1000,-876,894,-780,-459,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{-1000,-201,-120,-1000,-592,315,-1000,-432,-643,1000,-662,-84,-538,-343,285,389,1000,-213,454,719,-341,-589,98,-1000,-789,-228,1000,-1000,-60,-1000,-577,358,1000,1000,-502,124,-358,-490,-534,-935,249,281,214,558,924,-828,-150,947,-904,1000,-334,-601,124,90,73,1000,-1000,770,1000,1000,-926,-842,630,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{119,251,-555,558,167,-581,-628,311,-98,298,118,174,-1000,-511,-430,835,380,-512,35,228,-216,-382,-826,6,510,496,-104,778,556,188,-314,706,244,499,997,424,-517,-367,1000,66,-222,-1000,338,-304,196,-331,-275,-1000,447,1000,-727,314,-1000,-227,-291,567,620,-1000,-259,142,-375,-55,-139,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-741,828,-888,-412,696,-847,-916,593,976,-146,973,-653,68,187,-961,752,251,-173,-811,669,-860,-723,-509,-256,51,232,675,663,10,-855,814,381,-638,335,52,-770,-852,-825,497,-681,-773,-891,-777,-893,-563,552,699,-367,12,-890,890,534,-59,951,794,682,987,656,234,-981,682,-560,-651,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-923,1000,-501,-618,-501,580,-32,1000,989,-559,340,-582,-284,105,90,838,1000,-449,-1000,1000,604,-973,191,1000,521,458,-96,173,-531,-183,764,-94,-261,-254,-90,350,-209,-194,175,-1000,-1000,-335,-756,95,-1000,1000,1000,759,1000,-1000,553,21,-1000,1000,1000,1000,1000,595,-429,-672,1000,1000,264,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{211,533,-516,-118,-262,-554,-728,1000,-723,616,910,-1000,-732,1000,-807,-1000,1000,-1000,1000,273,-970,-861,-90,-470,139,-871,-989,1000,-4,344,-590,1000,-1000,1000,1000,-168,808,-611,920,622,1000,-1000,-822,-852,-1000,91,-301,956,1000,930,223,325,-840,1000,1000,-854,-1000,-709,658,150,479,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-1000,-684,-54,-312,-679,1000,802,835,484,454,177,218,-711,-1000,754,949,-163,850,-611,-233,450,-1000,1000,-653,-645,-740,1000,-94,-137,302,326,770,1000,-610,1000,-384,-1000,754,1000,-525,-1000,740,-719,-460,810,-1000,1000,-1000,-1000,1000,-1000,418,-189,-233,-1000,-987,-1000,-925,-31,-710,-216,-164,268,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-597,-11,660,364,-883,1000,166,1000,484,803,729,-805,-847,-828,1000,442,-228,-196,789,-759,203,-1000,1000,26,-409,-1000,759,236,-1000,-92,-192,1000,1000,-300,986,-699,-192,623,-133,-6,-944,61,-1000,286,-50,-1000,843,-104,-474,1000,-336,339,-1000,245,-259,-1000,-1000,342,336,-285,-216,922,1000,224}));
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
            new int[]{-308,-1000,-315,823,-1000,-340,71,-207,-400,-614,314,666,536,-254,132,-1000,-61,-218,481,912,-1000,-557,10,407,516,258,-1000,-250,-938,668,599,1000,-1000,-340,-410,827,-345,251,972,-237,-107,787,-1000,-130,246,-406,-345,-607,-829,-385,258,584,1000,186,-743,-964,73,117,1000,-15,-97,-490,17,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{553,-551,-153,29,-446,592,1000,-284,1000,-338,-1000,1000,1000,-1000,1000,-305,791,-1000,423,3,546,-1000,343,1000,566,-797,374,-1000,-1000,112,-337,95,87,-61,-739,-1000,-1000,96,1000,976,-432,908,-36,199,-1000,-368,922,-797,-1000,-1000,-881,150,1000,592,379,188,-547,331,-184,-165,-290,-142,-995,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{238,-795,-54,-163,-1000,-295,-1000,683,-51,-370,-772,1000,-593,-1000,335,1000,-766,-520,1000,-94,-490,789,-107,-893,1000,-1000,-1000,-105,-1000,-233,617,1000,929,-356,-141,-307,-65,121,745,214,-23,1000,30,248,1000,-609,1000,380,-282,432,-82,1000,989,906,-1000,154,282,-426,140,103,339,-864,-377,-663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{-341,-226,834,-312,-807,-278,580,637,60,-206,412,-809,-901,1000,-28,-99,-99,-966,-256,-344,153,-219,412,353,21,150,-1000,421,220,-9,794,-368,-365,268,-122,84,-267,-38,670,-366,-440,798,-29,354,1000,735,684,-1000,96,235,638,-174,672,-1000,-6,162,988,-160,1000,720,-369,-531,85,937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{731,-200,309,139,647,-288,-560,70,657,866,-210,419,565,-316,609,-53,-691,631,239,-430,203,89,254,78,945,-485,-483,-384,-668,-102,604,299,-355,301,222,-546,-567,-699,182,-520,42,-463,246,-945,629,220,742,-466,663,509,-779,-219,-65,-103,-355,364,-59,432,569,110,204,-518,646,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{629,-987,357,-683,855,-288,-387,-138,-167,-957,491,527,1000,-316,609,311,-1000,-753,-946,-1000,243,-152,923,78,123,-485,-747,365,565,-102,1000,-674,-857,349,1000,764,-1000,-1000,-54,-1000,1000,-357,810,363,818,-48,1000,-738,-200,-866,259,-219,614,353,244,-394,487,432,1000,110,204,-518,-807,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{305,440,927,329,-237,790,-964,217,-423,385,-8,-228,-1000,337,582,-1000,-637,-654,566,-198,839,-882,-1000,854,-1000,709,-556,701,84,542,-286,-787,-786,-277,-1000,-492,1000,-1000,-194,457,-89,375,-105,-494,156,-968,98,597,-338,572,-851,-787,-718,481,648,1000,-315,985,-42,-577,-1000,-245,345,117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-401,931,423,-511,935,844,306,-313,253,-20,229,-364,347,-623,290,211,-227,367,464,-54,-825,960,206,121,-45,-244,-404,873,-799,825,-897,600,-500,-355,798,-154,-438,-61,-849,-78,-610,54,433,-445,187,-630,178,26,129,173,175,445,672,385,-3,108,169,-377,-766,163,209,-853,-744,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-75,-258,513,-15,-301,189,-18,383,-895,-874,744,771,-847,974,967,-342,-799,-692,-377,-129,-484,-368,-940,-533,113,938,-638,80,-862,558,398,-435,-227,513,247,-261,901,298,983,50,-339,806,667,65,-622,-978,-131,-164,369,963,-2,-615,-368,-601,-665,830,531,885,743,-991,-758,-228,749,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-1000,-1000,-489,-380,-307,-697,244,-312,-628,1000,-839,827,-226,-717,1000,1000,-127,-480,-1000,1000,-1000,1000,896,-1000,696,-1000,-1000,-809,1000,957,-191,-1000,1000,-855,1000,1000,-896,1000,1000,1000,-1000,-1000,-190,-1000,-270,1000,257,-957,-495,627,821,-1000,-1000,-1000,-370,1000,-1000,707,-1000,-595,1000,1000,-954,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-813,-276,-804,511,-652,-95,1000,483,1000,682,-597,630,-771,-1000,1000,1000,505,984,447,919,-751,-903,-183,717,867,41,-264,692,1000,370,-1000,-1000,616,-1000,-1000,1000,1000,1000,1000,1000,-1000,134,-564,1000,217,538,-472,-384,501,738,589,-1000,-671,22,-514,1000,-1000,-409,-146,-145,1000,705,-158,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-955,523,-270,233,-771,-258,1000,740,1000,879,-385,486,-832,-499,901,517,389,938,-41,515,-1000,121,291,90,873,-560,-987,425,1000,319,-900,-571,-571,-1000,-649,949,925,507,1000,655,-1000,204,-1000,1000,1000,340,1000,-322,338,823,434,-868,-662,-417,-651,867,-743,607,-1000,888,1000,522,-609,-935}));
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
            new int[]{90,-172,-643,-456,-590,431,881,-648,87,-703,-689,-741,-446,84,-218,146,-610,-68,-805,296,-761,-80,-730,-231,149,882,759,487,-737,-727,-367,779,200,-285,-898,2,-239,-845,-700,-956,-367,133,-863,-14,-313,-424,413,-952,-878,727,605,-988,-750,-632,-76,790,-979,-687,216,524,-473,298,60,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{-1000,-978,54,-55,-260,733,-734,400,-136,-727,276,1000,-274,465,-195,-999,-80,308,-981,-295,858,-219,91,717,662,55,-1000,663,469,-430,682,-392,-400,176,-359,682,-626,892,-462,465,-729,5,-500,803,400,440,777,620,22,-1000,745,-266,24,-703,159,-17,-725,9,-915,-818,-218,-626,-911,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{-708,412,-1000,397,-292,-69,-1000,1000,477,-278,-32,94,82,-1000,-970,-735,33,362,-121,1000,237,-1000,328,-848,-122,228,199,748,-186,-534,140,374,1000,764,214,989,-710,897,-788,-1000,773,-572,-1000,362,545,169,480,141,1000,-1000,-1000,-532,17,-554,107,1000,4,-273,-139,42,-866,135,-796,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{-191,-133,-231,809,-152,-202,542,-602,-277,-530,-429,253,-237,754,-813,-289,641,850,205,817,636,91,351,99,-367,-878,434,361,769,-434,770,-870,42,698,-584,727,-616,-651,-376,-459,-275,-696,595,-996,-628,-217,-919,-963,-310,247,-313,266,496,738,812,195,-147,-767,-887,692,-986,88,56,-350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{284,-668,-666,8,1000,-1000,-281,-1000,1000,-165,-473,1000,297,-1000,1000,1000,-344,1000,535,1000,1000,413,1000,-1000,950,652,-1000,-45,894,1000,-1000,-1000,-481,6,1000,1000,220,-410,-406,153,-833,-1000,143,-153,-250,-964,-78,-1000,-1000,1000,1000,-1000,452,117,695,874,560,1000,-799,-1000,-350,1000,723,996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{-267,105,-924,1000,303,-1000,-467,-1000,-72,-106,-1000,593,-308,704,154,-1000,275,291,-340,-31,-1000,-597,390,343,-772,-1000,-289,-1000,227,206,-98,144,-768,669,-584,637,-1000,-1000,460,773,214,-1000,973,-1000,-1000,-1000,-1000,-1000,-1000,739,464,303,61,956,841,1000,178,714,955,-421,467,1000,-817,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-347,-1000,-439,419,541,-760,168,420,-857,-218,133,-362,-1000,-587,-307,924,516,162,1000,-524,-1000,703,-538,1000,-106,62,-427,557,-474,1000,1000,56,670,778,102,1000,1000,-718,-8,866,-933,620,1000,1000,-610,-1000,-270,732,366,-288,722,527,484,-252,-85,-1000,-51,947,491,-351,543,-710,-1000,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-237,706,318,-232,-61,-157,1000,-184,-225,386,-775,28,309,-109,297,-496,-41,-135,-519,249,-22,337,-47,-134,899,-334,55,-349,35,860,114,1000,-565,377,45,57,766,410,-825,1000,723,-284,-148,-138,-173,1000,2,-498,-980,-739,-221,-233,-270,423,-866,-660,-234,-153,-1000,-719,857,836,72,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{352,735,-619,-112,189,162,797,938,946,255,-661,755,761,-764,-441,-728,-415,782,-309,-588,180,-923,-577,-420,188,450,320,-963,329,-285,-508,-670,-579,-457,22,-365,-446,947,623,-798,521,266,-18,-107,-611,918,380,240,952,629,262,-597,-252,849,685,962,288,631,-191,552,-783,361,935,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{486,146,-609,507,-1000,858,-967,254,876,-708,1000,1000,-906,-46,-1000,827,-280,-209,-706,-869,-1000,642,1000,765,-1000,1000,485,-1000,1000,-129,-969,847,219,66,368,1000,275,1000,-149,-507,1000,282,69,-220,583,83,1000,463,552,-1000,-765,-934,303,-148,-869,-1000,-1000,1000,967,1000,806,777,-1000,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{67,-519,-144,1000,169,186,786,407,-411,254,360,600,-386,418,-716,76,69,-815,-826,25,-100,278,58,-820,1000,646,-169,1000,307,508,807,-129,-830,-564,636,67,175,-406,-105,-422,62,1000,656,-921,379,-494,-587,-813,242,1000,-1000,-438,484,-914,565,-446,-837,585,1000,-573,996,-140,618,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{766,1000,846,-556,-825,1000,-304,-672,484,-124,1000,-381,-337,-927,-1000,27,1000,600,498,-1000,-802,-347,1000,1000,-1000,1000,1000,-1000,-31,-1000,-1000,1000,1000,1000,-1000,1000,501,1000,919,602,1000,1000,-1000,-18,769,480,1000,1000,1000,-789,38,-1000,-367,1000,-1000,-640,-246,449,273,1000,98,1000,-1000,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{83,-208,-447,986,775,551,-982,-195,525,-367,665,26,866,316,-311,-641,115,371,692,-220,730,901,-111,-476,-807,333,145,886,1000,-241,535,-1,534,-195,520,115,-314,520,-966,-327,-107,396,774,415,-46,558,-191,-798,476,-591,274,-223,-949,-420,-661,-264,421,885,923,-792,752,178,-316,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,-158,-558,-591,-11,-1000,-898,-305,420,-119,-1000,-316,-1000,-463,-1000,747,-453,-908,-1000,976,-532,573,1000,-476,638,-85,-131,-703,-570,1000,491,-1000,155,-1000,-1000,-285,1000,-279,-616,-1000,38,-767,915,-1000,1000,-1000,1000,-633,154,431,-557,1000,483,-1000,-272,-252,-631,93,-734,-648,873,-1000,256,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{1000,-330,-747,1000,555,354,1000,1000,-680,-348,1000,-83,84,-55,-353,137,-958,1000,-131,-276,-279,772,-1000,174,298,238,-396,1000,192,-1000,168,-217,-84,-797,-731,681,-1000,-522,-1000,1000,-795,209,379,1000,9,850,-1000,-830,208,-406,-1000,-1000,-1000,185,61,1000,1000,850,1000,1000,797,1000,-519,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-8,437,330,-964,188,-287,248,329,143,216,775,520,151,-893,284,331,-290,95,-179,308,243,-354,158,192,-1000,43,215,-451,-120,289,145,1000,80,513,499,688,-168,278,759,93,652,-157,-141,254,320,189,736,-784,931,1000,-568,-320,-14,-703,333,-3,1000,63,66,1000,30,1000,467,-767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{400,-599,-579,-153,-691,799,-621,255,-102,-90,-778,-65,-1000,1000,284,-393,-561,768,-278,-108,117,-167,-1000,-338,-487,-217,-400,-402,-1000,-1000,-1000,-217,639,265,1000,-603,-1000,409,-1000,-1000,-413,373,1000,428,162,-251,-1000,1000,359,-1000,950,1000,836,-724,284,1000,-37,881,308,31,394,-1000,-337,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{50,-882,-630,1000,-111,608,-1000,574,-335,977,-1000,39,-101,22,133,-1000,38,497,-1000,948,-384,-1000,760,686,813,537,1000,103,-793,-1000,-1000,898,1000,-1000,1000,-1000,-557,54,-769,-59,-465,402,1000,1000,953,-691,-558,-141,-156,-996,873,472,-406,-1000,39,738,-1000,-603,379,-1000,1000,-1000,651,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{360,-1000,-645,1000,82,-310,-406,-350,-537,646,-905,-886,699,-442,59,-224,715,594,-276,-51,620,-604,-1000,-1000,-98,-1000,-1000,-641,-933,-1000,963,3,-18,-108,-127,24,451,20,1000,-1000,-641,527,-1000,425,27,-45,803,-835,67,912,-392,-223,1000,840,1000,1000,-566,1000,-351,721,-454,758,845,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{-350,238,-888,-227,408,760,747,-948,997,-541,820,-459,-715,443,-321,201,31,-640,471,-933,970,-841,124,-997,-21,-733,893,-867,-835,241,-304,565,-151,-915,-420,359,-946,440,330,-946,-678,120,-482,562,-511,-368,-924,732,366,-349,-134,462,217,784,-340,907,350,-633,441,3,-777,534,-996,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{601,-603,117,426,26,385,-1000,-117,-439,-526,-501,957,-160,-865,-463,-328,967,-543,222,-101,527,-359,-203,-553,24,-968,-750,-1000,-1000,223,164,1000,-1000,165,-388,969,882,-714,258,-664,-1000,-1000,-896,747,603,332,-224,-106,-192,-645,-52,-1000,-101,-1000,1000,28,-213,200,618,-1000,-638,36,-106,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{870,-328,-66,-105,-622,880,930,-296,428,-901,-137,-286,1000,-752,-230,138,-1000,653,1000,-372,1000,1000,-520,-21,-1000,45,1000,-275,-438,345,137,262,-211,-722,621,-356,209,-876,223,-400,-178,-767,-755,360,-1000,143,-543,-315,833,501,256,-875,-377,-1000,284,1000,-97,-1000,-303,546,-105,-189,161,570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{358,97,-276,-546,532,508,-728,995,487,730,-228,247,-807,274,35,131,595,-502,-361,-655,-595,960,676,773,579,78,-825,1,-421,510,827,308,788,-90,-426,686,-486,320,342,921,915,359,725,603,871,-422,828,-989,-680,871,-427,297,-76,689,-186,-906,-949,922,-633,-237,15,818,657,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{336,-93,156,1000,-1000,-1000,-58,897,-1000,174,-837,-1000,-528,-1000,-546,-206,963,-644,123,333,412,-498,529,-853,675,-789,122,-789,-15,430,-1000,-197,1000,1000,1000,355,-1000,1000,703,667,-909,-1000,-628,-52,1000,-891,-955,454,682,1000,1000,239,-1000,53,447,1000,379,1000,-244,935,-153,-311,-226,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-962,-412,711,-156,-1000,-969,-1000,582,-63,285,506,-17,859,481,-225,1000,-194,-923,-140,659,240,137,600,179,-1000,492,1000,1000,-1000,-640,1000,-328,-266,816,777,-920,196,-151,405,-534,-541,-878,836,-355,493,-478,393,652,-1000,493,875,465,871,-478,-759,326,-1000,526,328,1000,-1000,-1000,-76,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-264,564,525,-525,-926,-114,-551,503,-665,-848,291,-265,214,197,407,-642,496,783,-694,672,-383,772,-87,-495,619,-932,461,872,-578,-80,777,-996,25,901,-323,167,957,206,-810,-483,-869,-202,426,-325,-908,-660,-961,-349,580,-111,-552,288,-682,-126,231,497,984,994,449,-795,431,401,60,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-1000,580,-57,-191,-1000,-336,-1000,1000,-759,-404,126,806,-211,907,778,1000,613,-1000,506,-514,-335,929,675,-635,-1000,-812,1000,-943,-1000,-670,771,908,-474,1000,1000,27,350,592,1000,-830,-1000,-443,-69,1000,530,27,1000,-1000,-268,849,922,427,1000,-298,1000,305,-1000,1000,893,1000,-782,-1000,344,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.model.NodePointer,java.lang.Object,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
