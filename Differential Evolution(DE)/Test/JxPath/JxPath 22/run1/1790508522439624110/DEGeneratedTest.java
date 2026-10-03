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
        org.junit.Assert.assertEquals("java.lang.String:aWQoJysweDI1Micp", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{-531,-394,421,623,-465,15,-935,-656,582,-591,508,159,-781,415,80,-185,45,767,-336,-359,344,-986,604,-586,900,463,903,-594,-842,314,-298,248,112,31,-345,-1,813,-144,-311,328,185,-917,874,-921,971,233,639,658,-958,-444,-596,653,-932,460,873,891,591,390,659,-258,916,-555,-281,-105}));
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
            new int[]{307,-647,-107,-619,219,-268,684,932,556,-110,163,202,664,-61,-261,-416,522,-76,302,-633,-641,660,264,-48,868,630,-396,960,5,-76,228,-597,557,297,-486,-167,135,287,828,909,79,44,1,-814,-141,30,300,93,-991,-945,240,38,346,288,753,395,325,778,681,-317,-300,825,345,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-28,-907,-890,467,-313,-797,109,502,-524,16,719,214,-221,-621,-738,-191,-273,-74,694,322,-941,-843,-515,201,524,-977,-29,-90,748,-993,308,380,257,-641,-83,85,629,893,-362,529,599,539,650,-980,-649,-282,699,747,186,-484,143,-627,-682,630,-98,636,605,-886,536,784,-269,-955,340,-967}));
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
            new int[]{264,-101,461,-581,-69,787,-438,-636,805,-102,470,-111,-996,-637,693,-497,436,-13,-536,366,-753,350,466,768,232,221,-916,-588,127,-30,807,539,465,-622,128,899,916,-46,-381,-431,926,683,729,-284,838,-19,-704,-474,-333,563,-244,-241,-762,909,614,-390,-518,-931,-144,937,-657,-335,672,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{607,20,628,-804,17,70,791,-762,-45,-211,-391,-983,621,849,-930,-550,-247,-842,116,-329,-529,-339,-21,-771,866,549,-852,-498,894,-982,7,530,-465,438,758,-415,-553,-236,-386,162,-673,345,-518,828,102,888,303,364,-296,272,-886,979,-338,172,463,-975,-936,966,303,617,507,-682,721,-184}));
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
            new int[]{-709,-470,570,-82,955,-288,-641,89,-779,-835,-292,-34,334,-135,-687,-845,-582,-274,-244,-128,-837,472,-550,739,886,-24,415,-720,394,834,254,430,-557,-406,-710,-543,857,-906,-798,-907,-611,-367,-293,384,-745,-650,-184,798,975,89,60,-534,572,289,-330,72,-467,-996,169,894,4,989,96,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{978,995,575,-360,-575,-978,773,-773,66,-382,378,-70,-590,-650,-396,-640,-630,3,-153,-152,730,-856,-463,166,740,-309,-682,896,-136,254,-34,412,420,512,-654,370,409,-301,-603,-222,-811,-620,632,-738,-228,132,3,-2,-422,-485,62,883,-821,-275,-662,-954,-742,-258,-407,505,-651,427,-808,-73}));
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
            new int[]{211,43,-792,806,686,-27,80,102,-221,13,-337,414,-666,-415,308,87,189,-596,-739,-681,954,-236,-873,267,-326,394,-631,-527,574,-909,-69,-535,761,-226,-85,-659,-642,-546,-23,-695,-143,-306,584,-295,-348,-392,-383,902,719,143,-57,504,151,-506,498,923,912,828,-777,-995,-451,214,729,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-803,-580,428,-949,250,-730,141,-283,-894,845,-230,891,843,98,765,-462,-866,-860,526,-781,842,-584,366,979,-902,268,273,727,-395,-921,934,-443,-523,-564,562,-506,35,120,-264,262,696,84,-316,855,314,-935,206,908,-827,-28,820,-803,-814,-207,562,-89,-183,377,403,-179,722,-805,-763,893}));
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
            new int[]{-267,445,468,-875,93,835,726,-880,161,-459,293,262,-319,-613,-291,699,-160,-778,89,243,500,485,-74,-514,-173,-482,-519,6,531,45,163,-144,480,-501,432,-491,608,192,388,-404,-311,-270,448,934,-173,526,-708,-450,319,-223,600,758,929,864,-993,648,531,754,-802,826,-382,-282,59,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-176,842,171,-301,-944,-794,-669,1000,588,-189,-661,-1000,-359,-673,-722,-883,-17,750,530,694,-602,-558,681,480,-615,270,-681,-1000,-921,712,601,-890,135,933,1000,-150,428,1000,944,276,-604,930,-1000,261,-328,248,652,224,562,-292,698,-673,-242,-499,1000,-484,58,1000,416,-1000,639,-912,-642,1000}));
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
            new int[]{-636,226,77,-435,993,920,263,88,330,841,295,324,992,151,185,391,662,492,-312,-420,966,-428,-196,1,302,-12,-42,822,936,316,344,481,-4,807,-553,132,-670,52,-538,92,-337,390,967,46,-96,976,-957,-583,-909,-2,-195,407,-11,-239,-762,974,84,71,-826,299,-555,696,261,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{850,-904,231,534,-189,-71,-706,15,399,695,794,0,237,150,-405,-161,289,-463,-413,722,-172,569,629,-402,-268,-699,450,-285,605,-564,-9,-144,-692,-568,238,894,89,592,784,-84,696,180,-959,823,-375,617,354,60,-56,783,-933,923,28,544,-966,-243,-21,785,-979,274,499,402,-18,220}));
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
            new int[]{-420,373,-500,-399,115,-269,1000,871,-1000,-349,-107,-1000,220,766,1000,-992,418,1000,-216,-497,1000,-1000,1000,-252,-1000,234,-620,-285,-1000,479,393,899,-229,-62,-545,-373,510,801,1000,780,189,-1000,574,-1000,-153,-400,1000,309,-683,-1000,1000,-771,-809,1000,-621,-200,-477,316,1000,-1000,-116,-1000,-358,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-330,-187,671,-360,543,757,-30,170,873,27,158,368,-858,-612,880,-228,-697,454,-311,-341,-794,-751,215,798,860,300,985,749,-662,-152,542,690,-46,258,-130,-18,-291,293,-983,111,957,579,-709,13,722,258,-571,321,184,-368,-244,581,-527,393,475,-867,-919,-34,984,-9,983,-588,372,825}));
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
            new int[]{229,-836,-831,301,964,-619,352,-723,638,-367,-733,602,580,498,835,-810,23,-124,732,422,-837,512,-666,145,842,-691,-31,714,-864,221,86,745,932,31,302,-452,-27,-96,-784,-443,-212,-754,-695,-480,398,104,-700,-330,-194,108,992,-323,841,-208,-383,766,-817,11,651,-920,-916,-849,159,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{851,-571,-660,84,-408,-574,-952,-278,-607,-919,-242,467,-648,-142,-312,-861,432,-117,395,932,-184,420,-449,928,-530,711,-271,182,-166,290,895,69,35,-891,-85,-750,-681,396,-154,522,492,999,-970,163,-925,-814,-904,-95,-388,351,-158,-373,-883,871,-314,-983,977,-505,859,-972,826,80,868,757}));
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
            new int[]{900,-368,-128,514,-834,544,881,678,697,-727,706,-391,755,-759,-425,734,-166,-25,927,319,-610,-808,-989,-442,-659,95,-131,389,864,588,2,331,514,395,675,339,999,607,-763,562,-4,54,114,240,-626,97,-809,-499,863,-112,193,765,786,6,367,-351,-196,-545,-721,-201,-200,-544,859,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{-151,-871,264,6,-6,-334,983,613,-653,-640,-367,823,-851,442,731,-856,-264,949,892,-956,75,678,-633,-252,722,650,-37,-587,-951,-427,-44,345,-988,-371,-961,47,-313,-125,375,98,-670,486,352,-859,844,-839,-197,563,147,83,-882,-121,-799,43,929,-752,208,-468,879,-911,862,928,-660,-650}));
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
            new int[]{-661,103,304,863,186,-302,657,506,268,313,906,538,215,-803,501,572,917,433,272,-708,934,-559,-767,288,-732,-162,-76,712,566,-921,-358,887,228,-773,-682,132,-122,41,-182,-728,-819,-352,-999,-433,80,-964,5,-870,200,758,-101,-699,-637,-586,124,875,979,-313,-74,-360,373,-979,-610,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-121,-799,916,884,308,-579,747,-120,-194,143,-752,476,-716,672,353,-870,-372,-884,851,-761,-210,132,-684,-36,-377,774,-538,-40,-418,570,179,308,503,-825,482,-731,69,-981,945,-257,287,-810,-224,265,329,474,-355,240,-589,-44,612,-275,-930,869,-942,-474,-889,-525,394,629,827,-299,505,-931}));
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
            new int[]{-237,-512,288,-215,763,-247,721,629,-831,-916,-482,-788,573,675,-388,540,68,510,673,-103,-397,-947,961,-424,574,498,-403,-865,-946,225,-706,467,-862,-616,577,379,311,503,817,556,-564,-64,-957,-201,994,-421,411,259,544,-205,462,0,24,-230,851,712,-537,617,-894,-401,-17,466,-846,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{-797,-424,428,-327,-273,-299,138,889,265,562,498,687,-753,812,898,-772,536,-694,-1000,602,595,834,-20,-109,242,315,-38,-895,-160,355,-27,253,743,-180,-77,-86,605,-880,-967,170,-230,-728,-74,-494,373,506,129,399,-995,146,-849,-290,-141,829,-516,-308,91,730,-108,-538,824,-605,-840,-855}));
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
            new int[]{-298,40,131,-505,162,-296,-710,821,745,546,419,274,624,732,-82,177,-684,-139,287,-57,-970,-303,922,367,-497,62,-62,-402,-690,223,-986,919,633,-199,-587,-356,242,321,-732,-142,81,881,-16,-441,222,-907,-821,795,-777,-548,988,-175,-70,-411,-293,-969,647,492,-167,-665,238,-296,297,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{495,149,233,-418,-460,51,554,-911,-525,901,289,384,361,-565,459,-53,154,631,-339,398,774,-951,-222,-496,580,-351,-241,-504,200,891,-933,218,-557,910,-613,60,644,994,-125,-560,192,-789,-124,667,-784,-916,-627,-589,481,-529,-358,738,-664,460,601,681,294,3,-838,660,-760,-739,529,709}));
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
            new int[]{-291,175,975,-752,-947,-417,-105,-291,778,75,228,720,-802,959,371,-982,458,143,-669,-141,416,-723,-813,-231,391,774,-385,-591,-439,-920,-559,-828,585,-400,134,-438,-345,-912,-119,212,-451,954,503,-542,529,-36,829,808,957,-896,-662,-665,-665,993,-96,77,173,-827,-696,-157,-454,842,-245,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{-737,86,-286,76,10,877,-892,539,-951,968,206,441,766,-637,581,-225,502,-621,298,-395,-234,964,-519,429,-532,-822,-767,300,-73,-822,548,619,-727,339,156,-619,28,189,783,419,-822,745,249,710,-173,-486,-979,177,644,939,-637,-436,774,-593,-926,-25,584,46,942,417,716,493,-492,-674}));
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
            new int[]{39,346,-803,-718,-442,-493,-877,-482,252,-865,376,-76,-858,-778,541,985,-966,514,874,121,119,-639,51,-568,642,-973,474,-67,192,-869,-428,471,54,-847,859,-559,-776,0,45,150,196,-476,428,205,-558,-421,-1000,-335,-396,-292,233,-129,-137,-123,319,821,-294,-194,840,-226,-665,-592,-748,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-770,-376,-641,-735,-589,-3,-14,246,609,-540,21,750,-842,642,-163,-666,100,-602,-466,-819,553,-463,-255,50,-111,-530,-673,356,-994,-692,-18,-95,552,594,-291,989,555,6,876,-764,-408,-853,658,-74,369,852,-401,299,376,295,129,-185,-852,651,-956,280,-406,39,114,293,-188,-929,-719,-440}));
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
            new int[]{-803,961,-353,746,-497,371,976,-858,-72,639,574,-1000,-575,492,92,910,-546,-225,-762,-474,948,354,-382,57,-156,-820,-732,401,81,-892,-506,-476,433,699,-510,-171,238,-576,70,993,685,-557,-167,-960,-878,-555,-924,-774,-418,-97,863,780,-349,554,792,-543,-329,-97,-786,435,-711,-879,-74,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{454,935,942,700,-376,857,-769,870,886,710,-525,28,940,996,-325,842,-585,984,835,-603,-369,247,567,-224,631,-122,-36,716,-62,47,-743,-291,990,-986,-743,636,206,153,647,-232,256,136,-165,-172,251,-993,245,666,301,-440,-312,82,-833,-821,-231,-664,-429,83,157,970,157,-219,986,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-364,430,492,953,84,49,80,-19,-330,-15,-1000,-50,298,131,-437,1000,-1000,1000,415,-1000,-721,-167,-305,-645,575,-107,333,-146,1000,450,-580,-423,-134,-221,288,-521,256,-417,1000,-1000,667,-415,214,119,1000,-174,40,896,34,-1000,6,212,-1000,-259,-1000,966,589,-454,-172,1000,-416,-191,1000,-3}));
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
            new int[]{-203,-700,-245,-328,-672,-815,639,934,933,-843,495,780,164,-290,170,617,-826,-312,195,301,386,-879,-168,-918,-456,993,119,-62,78,777,213,226,448,-638,263,-166,805,-722,82,883,-841,35,953,416,68,-200,59,-488,822,-337,-786,755,-967,-703,-131,-871,-358,931,659,-975,438,276,186,307}));
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
            new int[]{-542,970,81,-142,-639,-625,514,589,-257,-118,-290,208,-830,-867,-117,494,857,-187,269,-145,132,472,76,-761,674,477,-209,-266,615,360,-234,667,301,-437,-9,308,-747,917,-19,-876,625,69,754,-62,-632,479,64,326,-797,658,-791,89,-511,-155,-952,792,142,-533,149,938,858,406,-573,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{842,779,-54,-351,797,-562,153,-301,-944,428,-804,943,243,-329,-447,-80,-596,-141,646,-191,867,441,844,859,-746,439,353,-169,-826,119,-982,-278,-559,477,242,112,-777,-762,713,-617,-572,582,-536,719,877,-809,868,337,-719,-643,646,706,-49,883,-326,-353,247,-715,-546,166,-707,431,-520,-392}));
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
            new int[]{-826,-491,596,-65,-780,549,-926,-503,163,-459,824,-154,-389,834,-729,708,-671,661,265,-495,948,-576,673,25,7,523,709,718,697,58,846,-787,950,698,-685,708,-912,698,718,770,-830,-387,57,-195,365,-388,-366,926,223,-566,681,-689,548,-962,-667,-750,-285,776,-383,-964,762,-485,504,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{181,776,723,976,-649,-47,921,879,-618,684,503,-119,-845,-923,-666,-935,-15,751,-96,-807,832,269,210,-64,-730,-205,-944,293,-929,-953,-545,566,-562,-662,-784,-522,56,730,-740,-201,373,-862,-275,183,838,-381,-512,-147,158,993,393,216,84,-529,-826,-472,-286,-995,-194,-659,835,-274,898,605}));
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
            new int[]{-786,499,-499,-413,639,-654,-720,991,640,-463,99,-26,723,-887,210,845,-844,-221,-76,-150,301,99,-852,377,-11,320,-410,-798,718,760,176,-12,539,454,-335,-466,704,913,-152,-20,-644,-232,290,580,-159,347,-892,-476,625,256,-44,-212,-852,483,-936,994,518,635,-30,-102,-572,-467,-608,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{-140,209,-768,-150,-324,-296,-675,-189,896,103,-157,-697,942,-910,621,-900,-887,-888,628,-931,705,886,-796,-944,-603,973,-9,16,-947,-655,456,-2,-949,-548,127,753,-664,-614,-964,335,459,478,218,-671,87,863,598,-917,669,411,-896,-289,-751,-242,-580,-504,-202,143,-559,-166,835,237,209,867}));
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
            new int[]{-474,-413,620,-21,-308,-859,432,74,-608,637,899,148,-179,-271,-578,-157,-113,-460,-161,823,564,961,-558,-830,421,-68,72,-216,149,-900,381,388,-31,819,-358,868,782,-418,770,275,98,657,-327,-889,898,-37,-559,230,-140,21,-545,95,660,217,-959,992,426,523,952,307,836,341,-339,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-774,182,763,551,-431,597,-607,-416,272,-737,758,-778,-345,-396,-913,-953,-30,-272,-10,827,-542,402,-611,-424,-647,553,614,755,-526,389,-10,-918,-778,362,343,-462,211,822,-111,-157,90,456,-777,-63,616,-625,-699,-193,526,-946,641,383,-651,260,312,-840,-68,-455,718,718,-822,944,660,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{374,475,516,-883,705,-252,875,-145,282,-9,467,-745,897,-357,-268,-618,37,50,-783,-218,231,83,-595,-359,-760,552,811,964,898,-509,412,717,702,489,513,-19,535,-110,-709,-136,-643,926,405,-820,-905,-386,306,-306,-473,599,184,-870,508,-978,847,74,540,994,-749,401,-609,250,885,-81}));
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
            new int[]{493,206,-517,-245,-926,892,366,-837,-668,-316,-908,336,813,716,-175,318,271,-374,95,403,778,-548,258,-72,1,178,-595,770,455,-458,-100,-906,-312,112,7,687,350,408,-773,400,888,-100,-85,663,-810,-927,-519,830,-49,436,83,180,-268,350,-916,809,-255,849,-253,372,-755,-745,-342,638}));
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
            new int[]{144,166,198,-94,-25,-908,-358,141,987,296,368,-648,-710,-841,-152,-728,-827,-892,-605,6,-301,-386,177,483,446,936,561,-104,-517,43,262,441,-119,-470,70,-453,-996,134,-890,-101,-373,-541,479,48,-449,955,919,-827,751,-198,715,635,397,417,411,364,36,-451,-853,-135,175,-414,-612,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{62,170,913,-496,-907,599,-823,631,-827,985,-129,-747,425,154,-516,859,-690,-172,192,-792,-406,-49,597,-801,-389,-576,-401,-352,-681,418,668,-272,-903,-309,-622,-537,-455,849,546,-352,-366,-585,544,-179,-517,-297,-535,525,-238,-979,664,-18,258,514,-61,-494,-499,-906,-925,550,-484,-681,855,-951}));
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
            new int[]{980,400,-690,-520,326,-229,-570,-863,977,-758,-415,726,496,-751,-180,204,908,6,-619,-20,-840,839,-283,-334,265,475,35,225,-576,-956,-999,-682,949,807,808,-629,83,164,272,-200,905,378,-91,-858,-558,614,-27,-607,-168,419,357,-202,922,-464,933,492,-963,-907,431,-13,-175,-371,-727,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-821,-889,-606,15,353,255,508,681,143,-982,-768,564,732,-15,102,-792,792,812,-593,-523,-229,313,629,207,664,568,-541,-403,498,826,884,-685,-242,552,66,-592,-978,56,86,741,-827,-114,-988,940,772,368,207,-432,348,525,-690,624,724,963,902,-633,811,265,-493,-80,-245,928,-696,169}));
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
            new int[]{293,-773,413,-956,-722,-552,-589,-890,857,-495,266,-704,-426,156,77,959,609,791,-608,-389,-936,148,-892,513,-322,-861,-677,-628,-193,863,-281,962,-526,-479,-712,-408,349,15,-414,-998,656,-256,50,-499,-877,-663,393,914,391,-113,-395,-721,543,628,-330,-860,809,935,731,-268,-87,27,-814,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{-123,-727,605,153,-315,-12,828,379,-37,991,-540,550,995,679,587,-787,111,-633,-233,-782,-574,-416,-568,448,-225,718,130,287,970,352,-938,717,579,-313,496,211,92,554,-658,20,544,703,-575,-112,-512,276,-218,-239,-495,-316,-279,-381,-618,-536,296,-788,558,112,359,-806,-560,389,-580,895}));
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
            new int[]{1000,517,-1000,84,847,35,180,192,-478,462,151,886,1000,-296,817,677,1000,-866,-893,-758,-795,-997,341,590,-665,-1000,-889,1000,688,553,-1000,-532,-922,-1000,-853,-1000,-325,250,-1000,-1000,432,-700,410,-1000,261,-393,-354,-172,1000,325,433,-325,-782,-331,-1000,376,-732,-629,358,-111,-1000,-1000,-879,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{-802,80,580,-818,146,-988,-220,742,161,-739,354,815,773,-407,799,-294,-591,-319,679,717,-840,233,17,447,-734,-310,-854,-327,928,-143,551,339,368,-826,652,746,975,-346,-581,-283,851,-39,-823,-836,-898,596,-926,-702,-193,-331,256,-475,765,-595,-960,972,844,139,-926,-448,-981,355,834,-528}));
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
            new int[]{462,-347,-223,524,-88,-876,-32,149,-970,526,-151,-214,-498,-830,533,-41,683,264,40,793,-497,-206,-63,178,176,514,-356,-910,-630,267,564,-25,877,-814,-426,-385,708,636,353,-408,-982,230,-494,-869,-695,-289,549,901,-95,44,397,157,-17,684,227,-480,-77,-353,-682,-360,326,-237,793,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{335,404,927,-501,999,842,-184,248,-249,-51,-954,-511,61,294,340,772,488,-189,-89,-103,-51,367,944,675,783,-559,-168,-673,631,-181,53,-674,787,986,-854,395,-460,-609,980,-954,255,-853,-324,-425,-537,-120,952,-262,76,422,-583,475,968,-170,381,-11,672,834,132,-739,-340,-800,-690,629}));
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
            new int[]{-202,826,-35,598,-123,-423,-433,-503,-79,523,-696,820,341,-736,71,-490,754,-825,-283,-715,-239,209,-54,983,-479,-179,-387,463,-22,-730,-823,-790,180,-757,501,639,-958,330,-913,813,-889,207,-150,-198,406,553,-930,-787,-733,714,-192,580,-720,-93,209,219,-189,-630,-565,962,453,278,672,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.w3c.dom.Node,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
