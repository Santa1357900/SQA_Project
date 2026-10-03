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
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "asPath():java.lang.String",
            new int[]{-1000,1000,-97,-866,-1000,-629,206,98,-273,163,-261,-325,629,-1000,-1000,233,672,-525,61,1000,-361,-535,971,-407,7,243,1000,561,1000,877,-352,-266,208,-1000,349,-703,-459,563,-1000,-640,571,-479,897,215,-261,88,-327,1000,-633,-103,1000,87,-150,-1000,-128,494,-522,862,-1000,-1000,-320,-1000,-283,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:L1swXQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "asPath():java.lang.String",
            new int[]{-1000,194,358,-27,-1000,-248,610,423,-814,-562,-689,-1000,453,-1000,-968,1000,-1000,344,1000,1000,953,683,-865,1000,-1000,946,289,-278,1000,-460,-927,-480,1000,-1000,-229,-361,413,-764,-1000,-1000,1000,-1000,841,1000,-1000,-1000,1000,1000,-470,390,538,91,-592,-206,980,-332,1000,-1000,1000,-1000,998,-249,13,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,-1000,-746,-246,479,-312,543,494,921,475,78,1000,1000,971,-530,-258,818,-712,-214,1000,-997,-210,135,-189,-599,-589,-614,104,-1000,1000,363,33,-958,904,1000,280,-1000,-625,-418,-367,693,1000,1000,782,657,-402,-33,-1000,-247,-1000,88,-742,514,826,-502,-753,626,-382,-760,843,113,-1000,151,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{964,-737,-542,-965,-352,469,158,799,35,997,-434,1000,-548,-317,-1000,1000,-1000,-309,80,818,-581,-261,151,-86,167,-1000,-253,-696,-820,1000,-835,-443,-822,-40,513,-643,418,-986,114,-1000,619,-1000,621,867,1000,1000,-321,515,-480,-1000,-484,-1000,355,-988,-633,696,1000,-212,492,-218,-23,-976,546,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{318,-542,-151,-425,577,-445,375,1000,212,699,585,866,710,881,149,-194,1000,-361,-481,1000,-439,-29,-324,-141,154,-409,-1000,134,-1000,462,1000,114,-1000,699,-165,600,-804,-431,-881,-797,-198,576,850,484,-143,-311,-229,-1000,405,1000,-208,297,-1000,1000,-446,-1000,88,-462,-262,1000,145,-678,-206,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{161,651,-821,10,791,-212,530,220,317,871,-295,814,-778,227,240,-318,-31,-239,188,716,815,243,-826,-955,-243,-260,-726,273,-341,-774,938,1000,451,1000,-544,1000,267,-517,-169,100,133,303,-459,-1000,139,-1000,-969,866,948,82,-445,-227,474,-374,1000,243,88,210,508,-472,-736,-29,-825,-604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{300,-675,119,-193,217,-576,-1000,-85,962,-1000,-549,855,684,-334,349,-339,-811,752,965,1000,451,597,746,1000,411,1000,-173,-787,-384,-621,-602,-252,-239,-467,658,306,-1000,-273,248,518,8,145,-407,-291,-1000,1000,-639,-269,103,557,-778,490,8,1000,-471,-767,-579,-849,245,643,264,-239,-533,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{484,992,654,-89,894,614,-659,-469,67,924,-981,-406,384,-498,-178,-410,-954,49,-836,-116,194,450,934,133,430,743,-404,-192,39,867,-996,158,809,650,837,154,-576,872,943,-997,632,452,-397,671,-915,794,-238,-476,606,170,-412,677,951,642,393,-81,631,-524,-99,201,276,255,124,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createPath(org.apache.commons.jxpath.JXPathContext):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{423,1000,-111,-374,-94,349,-362,-522,-1000,-1000,330,-433,557,-391,-361,1000,-343,-321,989,295,211,-96,159,-74,-449,-292,-1000,212,-536,195,-850,657,328,363,-306,-195,-196,307,-789,-385,-620,629,211,-612,1000,-259,1000,-55,-1000,-469,1000,-777,128,-1000,-86,649,-312,951,-525,192,-633,1000,420,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createPath(org.apache.commons.jxpath.JXPathContext):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{886,-336,308,-355,368,153,-861,-1000,-1000,-1000,1000,-585,42,-857,-645,-584,1000,84,-74,-124,-514,76,-604,-1000,-1000,-596,-178,515,-1000,-219,-1000,-449,-323,-226,-188,878,-917,225,-1000,-961,-635,1000,-568,-148,-531,479,-750,625,47,-1000,736,-613,1000,-861,244,1000,-1000,1000,-1000,633,-1000,-179,166,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createPath(org.apache.commons.jxpath.JXPathContext,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-486,-966,179,-657,-1000,-1000,53,-1000,553,-885,-303,-230,-91,127,-445,308,-858,514,-607,668,980,665,197,1000,663,-307,-621,-425,379,776,719,367,-46,-448,1000,-481,27,-309,245,-1000,167,-992,674,665,-635,-942,603,381,3,-482,-1000,-1000,-884,860,-418,884,552,1000,218,-679,85,891,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createPath(org.apache.commons.jxpath.JXPathContext,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,-942,234,809,-1000,-740,-191,-944,-40,506,271,251,863,-29,-121,322,-1000,-414,-410,335,136,153,851,257,675,-202,-1000,330,265,1000,220,-242,-400,176,1000,-598,162,560,206,-1000,609,-1000,-295,423,175,-1000,1000,246,560,-591,-477,-1000,-1000,1000,-1000,339,711,396,130,-1000,85,-438,889,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "createPath(org.apache.commons.jxpath.JXPathContext,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{255,-447,-786,-690,-1000,-530,59,-382,79,908,178,-941,-885,385,-746,1000,-858,966,-874,969,980,387,486,1000,620,22,-321,32,985,310,719,657,121,-11,411,-435,-783,-609,1000,-1000,535,-992,1000,665,971,-942,657,942,-21,44,-752,-1000,-884,1000,-543,928,552,1000,112,-998,-341,747,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getBaseValue():java.lang.Object",
            new int[]{-364,403,-166,-30,392,176,215,-82,-1000,-690,1000,234,1000,-1000,-180,458,-440,-44,-66,-1000,-1000,1000,76,-851,450,-753,662,-928,-139,-516,-867,1000,796,-170,-186,960,-1000,585,-442,-624,-655,668,-645,-436,449,-403,1000,-187,743,338,354,-1000,-669,1000,818,547,753,406,242,1000,576,-420,197,77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getBaseValue():java.lang.Object",
            new int[]{84,-51,14,-877,22,-841,-963,-656,425,-464,695,-156,-987,-687,610,-718,230,-813,448,-280,-264,-717,350,569,16,106,671,-164,-597,-47,926,711,194,-311,871,-197,752,490,499,-996,-527,-822,413,-149,-659,-881,-501,-479,415,313,-888,-859,44,232,118,-27,867,-708,-985,-794,722,-617,107,590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getImmediateNode():java.lang.Object",
            new int[]{-563,35,794,-210,1000,908,588,-561,634,1000,960,951,-419,52,-683,1000,264,-400,436,305,82,1000,-365,-1000,-587,376,-489,49,1000,-421,90,-965,-1000,238,-1000,-83,-988,-245,77,732,-274,265,-525,-1000,250,-1000,-82,-1000,-270,1000,1000,95,187,499,805,-1000,633,160,-148,-83,532,11,142,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getImmediateNode():java.lang.Object",
            new int[]{-478,275,919,-769,637,-256,-137,579,355,385,-992,167,-169,112,388,-706,-184,750,1000,1000,-318,574,-1000,534,806,-677,614,-513,1000,-1000,-227,-511,20,-729,759,-1000,-1000,-364,-400,1000,385,1000,687,11,1000,-1000,801,-476,1000,-617,1000,895,-234,-1000,-410,-1000,-115,-1000,865,457,831,836,1000,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getLength():int",
            new int[]{-874,-165,-926,110,-38,-515,862,154,165,-767,-609,-915,-99,-390,548,674,584,-678,-166,408,-291,687,330,81,699,-420,-922,677,580,-28,-548,-983,912,-807,-887,-641,-689,-165,-511,-402,185,-132,-631,994,-185,-15,148,485,819,452,-424,-464,203,799,384,-793,-306,597,147,-318,860,240,869,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getLength():int",
            new int[]{500,478,64,-869,4,-448,130,-8,-962,-70,-485,103,47,-744,-454,480,531,-673,960,-170,89,734,-725,229,-729,-687,-50,-1000,-421,81,486,239,-628,-9,537,-1000,1000,317,682,-329,-171,-133,-584,413,722,-90,-1000,-154,30,1000,-1000,119,410,-406,-380,924,-65,-1000,-257,517,517,1000,1000,-685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-1000,-146,233,-574,346,559,-129,-830,-475,-277,523,442,-232,-273,-734,-144,829,-412,532,-1000,252,-767,-191,-1000,205,-654,439,-1000,-1000,113,533,-429,415,-400,-495,-440,804,-597,955,239,-541,1000,556,-1000,-250,299,928,-201,-189,1000,-1000,-400,-1000,859,794,1000,235,-158,-319,198,-824,-1000,510,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-669,344,-76,-649,-1000,-592,-21,261,-402,-51,1000,-587,393,-619,-162,413,-1000,-420,63,1000,722,575,-22,-1000,375,-585,910,692,-738,-1000,-140,-590,247,618,404,-1000,174,-612,-903,159,482,1000,400,534,-1,1000,214,-15,525,-521,208,895,-356,1000,255,-297,-857,-173,-5,-195,413,-881,-190,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{259,-509,-211,939,-775,204,716,262,-905,-1000,238,686,-514,-315,-1000,-980,-1000,-337,124,-1000,-336,626,-1000,1000,795,-1000,-1000,-31,-165,-189,1000,1000,-405,-1000,-591,573,-576,1000,1000,430,-975,-889,-1000,-1000,1000,260,246,-577,-1000,-232,-1000,-1000,1000,1000,-706,-749,-80,-783,32,-74,-738,383,653,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyCount():int",
            new int[]{685,339,-991,231,-171,-558,400,-221,-45,436,-575,-235,-345,-1000,-1000,-58,21,-590,-1000,48,-71,800,-245,246,-66,-79,-286,-1000,168,-598,282,-1000,-203,733,-135,299,-240,-34,6,-154,-724,-894,123,1000,-832,-562,108,32,-393,212,-1000,612,280,198,764,1000,357,-583,-871,847,-3,507,1000,-37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyCount():int",
            new int[]{346,8,8,729,-904,250,94,-450,-817,-286,-871,-751,246,-980,-118,1000,-706,487,-1000,-1000,-560,216,406,118,42,801,-183,-283,297,-51,-400,43,227,-780,929,-859,-130,966,-53,-751,261,-1000,1000,-189,-246,-87,-837,946,700,400,-66,1000,193,-403,-1000,281,-487,-218,-68,1000,553,820,946,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyCount():int",
            new int[]{-1000,232,463,-234,1000,855,391,-681,-573,-1000,-47,317,461,-355,1000,-77,15,820,1000,-199,720,-30,202,663,97,-641,1000,974,-1000,1000,-396,1000,52,-711,73,-227,194,-33,-728,-478,841,321,-9,-1000,-165,1000,-175,-968,416,-240,963,1000,-1000,-448,-1000,-718,539,707,980,-621,783,651,-870,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:Kg==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyName():java.lang.String",
            new int[]{372,-155,-722,314,721,-161,-89,116,-198,68,-560,-920,-75,397,-691,-354,-684,661,-627,109,781,-979,-27,371,-144,549,839,-869,-967,-890,-512,-86,-577,-485,48,42,-469,961,316,475,697,855,236,-708,177,244,972,-472,-128,299,275,-957,-750,-443,952,173,-858,-45,873,5,795,83,-776,300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDg=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyName():java.lang.String",
            new int[]{1000,1000,-821,451,-482,497,125,186,-666,539,991,459,-264,1000,11,-314,-1000,-1000,-828,486,-546,717,-15,-21,1000,30,59,-161,515,1000,-1000,-9,1000,479,-1000,-1000,442,1000,285,1000,-120,-518,-713,618,-1000,-1000,285,-450,-258,-420,-68,828,-350,1000,665,-1000,-1000,100,129,470,-820,-1000,18,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:X0c=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyName():java.lang.String",
            new int[]{887,-1000,-331,503,1000,-778,59,-673,24,-843,277,-1000,-29,1000,-435,-607,-1000,914,-1000,-593,-850,-1000,560,486,-395,138,639,-188,-983,62,-1000,777,471,-175,-364,-512,-387,1000,685,-1000,693,1000,138,58,354,1000,1000,221,520,783,-1000,1000,215,709,-596,-1000,284,1000,-831,-456,-4,1000,500,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyNames():java.lang.String[]",
            new int[]{384,1000,-1,122,281,108,677,72,1000,-417,-1000,59,46,-482,163,-942,369,-562,-492,553,223,-665,30,-326,207,-705,-26,1000,218,-524,372,676,1000,1000,-536,-861,-717,-731,-40,1000,-190,-824,-89,151,-474,407,480,-440,349,241,-1000,742,-528,-1000,845,1000,-707,468,1000,547,-235,66,342,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.String;:0", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getPropertyNames():java.lang.String[]",
            new int[]{687,616,-277,859,-719,1000,448,-843,116,-575,567,-317,-1000,748,-1000,-10,-1000,-964,-1000,-284,962,1000,1000,1000,-458,684,568,1000,75,990,-548,-382,-572,135,1000,-1000,1,82,-4,-909,428,461,780,1000,-309,-443,-341,-624,84,1000,1000,-71,-215,41,-826,1000,-871,-186,1000,864,567,-45,-441,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.NullPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{118,709,-201,1000,-398,1000,690,-730,479,-247,360,-88,-418,690,231,879,-1000,-386,270,-606,-1000,-15,566,1000,191,614,1000,90,1000,477,1000,662,-240,-648,-488,-619,-565,-238,117,9,-83,-503,515,-862,-861,-814,38,1000,-45,1000,699,-1000,882,-207,610,1000,-237,-552,817,374,-517,640,-483,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.NullPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "getValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{367,-107,164,835,-692,-100,-1000,-750,-193,-291,-405,955,-393,-485,1000,-619,436,-928,367,-66,-397,51,-519,-226,296,-841,-906,-1000,46,1000,-300,-371,943,-683,-924,855,-932,-121,-404,1000,-257,7,-703,-1000,-1000,915,-1000,1000,1000,-96,580,-341,55,558,652,1000,-165,-539,-674,-96,567,-295,-910,-955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isActual():boolean",
            new int[]{-900,1000,614,1000,362,1000,674,687,-1000,47,-539,1000,-627,-1000,1000,1000,1000,1000,1000,69,1000,1000,-320,-1000,-1000,1000,-583,1000,-130,1000,-503,492,339,1000,369,-689,866,-235,1000,80,-760,1000,86,511,958,-1000,-1000,-1000,144,-1000,-251,-764,-1000,696,-869,-1000,359,1000,-197,-1000,-744,583,-841,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isActual():boolean",
            new int[]{-170,-147,758,499,194,-1000,-782,623,864,54,-32,-249,1000,-337,380,1000,1000,-1000,630,-58,-313,-355,1000,864,798,300,-148,1000,-15,8,-1000,389,-532,522,1000,160,227,-392,-713,-1000,-929,-932,-219,-1000,-936,-570,1000,590,420,44,-587,-207,793,-1000,-219,1000,-1000,-1000,1000,-509,-1000,-555,681,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isCollection():boolean",
            new int[]{-1000,-727,-127,-130,675,-481,338,-161,-667,288,685,-1000,1000,728,-238,1000,-1000,170,520,-840,438,573,503,-483,737,-478,-635,-670,1000,-1000,-149,-295,-1000,-319,452,-582,-304,1,-879,495,800,-316,1000,-128,141,-1000,-345,-395,473,980,478,-1000,-1000,449,646,-582,-510,-817,508,1000,-588,-264,336,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isCollection():boolean",
            new int[]{-448,777,-556,-458,-3,-311,-985,67,524,-648,877,326,759,-109,722,934,720,-364,12,1000,-25,-390,52,-141,-1000,331,-767,-503,-101,879,267,-443,878,644,252,-279,1000,763,827,-1000,-422,30,-80,-390,348,1000,700,292,333,-139,37,-25,-662,-398,-524,432,-861,738,-342,302,257,-1000,-44,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isCollection():boolean",
            new int[]{-706,966,33,295,1000,-510,-685,495,423,423,-433,1000,516,571,1000,402,-1000,-233,598,-6,-703,494,676,-955,405,-63,-370,-1000,154,373,-1000,-855,937,338,497,2,260,-128,611,-305,-106,-311,870,-791,513,-448,618,296,32,445,334,-495,-64,-801,-684,701,-587,763,708,68,68,-149,-77,468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isContainer():boolean",
            new int[]{1000,-231,658,586,1000,27,-681,38,780,-242,786,-812,1000,266,57,-943,-1000,-1000,400,-366,-783,722,-400,-1000,-812,515,-400,495,-1000,789,1000,-545,373,703,-52,530,969,-75,-282,407,-72,612,848,-28,-400,1000,481,1000,-1000,574,-167,49,-1000,-31,551,-28,233,264,40,456,1000,-533,-318,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isContainer():boolean",
            new int[]{721,1000,-61,-741,520,600,-557,-353,741,-1000,-570,-389,20,1000,-204,327,-480,839,-748,-576,-363,1000,321,196,322,-860,1000,-970,575,617,-347,-275,389,-785,-956,-139,70,-659,-266,521,367,344,-1000,627,534,296,457,956,-81,-843,23,-602,-1000,-900,388,201,-1000,384,-429,-396,263,-107,-1000,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isLeaf():boolean",
            new int[]{792,-776,-411,-310,-937,221,-473,-669,219,-72,-324,-1000,-891,183,274,124,-1000,-863,-313,-964,352,737,89,-1000,-719,224,447,368,505,686,711,-736,1000,-1000,139,92,1000,31,300,-673,1000,22,139,645,164,-1000,426,-357,-1000,455,-464,-357,-206,-810,101,-954,-220,911,-1000,-1000,-627,1000,-493,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "isLeaf():boolean",
            new int[]{1000,47,19,1000,-563,294,-428,-890,988,-788,848,-176,-9,-223,763,1000,-336,-129,1000,450,-529,628,1000,-386,-198,-1000,883,-1000,429,891,71,-1000,-196,344,205,-124,753,1000,1000,-539,733,266,113,785,-1000,-896,1000,1000,-433,54,-1000,297,915,752,-1000,-978,-274,1000,319,-199,-111,808,-281,104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setNameAttributeValue(java.lang.String):void",
            new int[]{121,-107,529,10,-346,-965,-1000,711,-53,-596,-234,-119,-1000,-400,-952,-1000,-241,365,-58,907,822,-1000,604,689,128,-650,638,1000,-1000,576,1000,-228,-400,616,-133,750,-314,1000,1000,696,-1000,-636,571,-126,-463,-597,-590,-688,-576,526,-1000,-395,-211,659,-843,1000,1000,-406,1000,-289,-1000,-716,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setNameAttributeValue(java.lang.String):void",
            new int[]{909,518,-122,253,243,-1000,987,-159,698,1000,303,-904,-47,20,-605,-1000,1000,1000,828,226,398,1000,191,566,-1000,-391,941,225,-38,-5,560,580,465,1000,519,-836,999,651,-408,417,-792,-444,-341,52,-54,-641,-208,-988,-427,62,-392,-371,774,479,-105,-616,158,605,-1000,-692,-682,-701,754,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID|getPropertyIndex=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setPropertyIndex(int):void",
            new int[]{1000,-361,104,-529,1000,1000,-445,-1000,-786,1000,1000,1000,-929,625,-1000,841,-253,-908,980,-1000,-1000,798,603,781,963,-636,1000,1000,-976,-25,1000,-1000,263,-921,1000,261,-944,-296,1000,-893,1000,1000,-389,1000,1000,1000,-1000,1000,-1000,1000,-1000,1000,974,-452,576,-1000,-1000,-1000,45,-561,-1000,730,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID|getPropertyIndex=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setPropertyIndex(int):void",
            new int[]{-6,995,798,122,260,955,-855,922,943,-257,-104,-901,-714,420,-370,-819,-500,187,374,-435,268,226,-875,-571,108,-78,-618,-346,-166,857,-256,-321,26,-288,-388,-464,-845,530,-969,122,826,-329,793,-806,432,-766,65,-462,473,709,23,-709,-262,206,-796,507,641,-293,-317,688,-918,-475,486,-744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID|getPropertyIndex=java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setPropertyIndex(int):void",
            new int[]{-6,1000,894,39,260,865,-851,800,289,-250,36,-901,-675,571,-370,-1000,-459,256,858,-939,-83,125,-816,-1000,739,-59,-618,-346,-352,1000,1000,-826,-489,-309,-388,-77,-1000,455,-1000,63,826,-329,1000,-806,280,-413,353,-328,473,709,60,-709,417,-101,-1000,507,950,183,-469,221,-806,-840,-86,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID|getPropertyName=java.lang.String:KzMzTA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setPropertyName(java.lang.String):void",
            new int[]{-1000,-549,-236,-630,-79,-793,63,357,-1000,795,1000,685,-747,346,-567,-33,-178,-781,-1000,910,503,-1000,1000,-1000,-563,-1000,1000,-219,1000,512,-1000,828,-479,-1000,431,-308,-78,-1000,898,451,60,-419,-1000,-766,1000,410,-1000,344,-930,-211,-775,-1000,400,-1000,-1000,439,-759,-392,-555,1000,-1000,-1000,-54,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID|getPropertyName=java.lang.String:LTQ4MS41MTI=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setPropertyName(java.lang.String):void",
            new int[]{1000,-997,-506,771,1000,1000,226,601,-812,529,-928,-115,503,1000,803,-299,6,835,677,-481,-479,512,1000,499,713,347,1000,-179,-30,-855,-1000,-242,611,-305,-392,975,157,-414,-779,-1000,-1000,916,-1000,-789,-64,617,-25,-1000,-240,53,313,765,-1000,470,-220,-905,-600,1000,-644,-904,-1000,-800,306,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setValue(java.lang.Object):void",
            new int[]{-332,857,649,-742,-85,-1000,-809,-30,352,355,-80,531,-438,453,22,-613,-214,-139,263,1000,-989,130,-464,-156,-1000,906,586,-480,721,-831,-555,-515,-1000,52,-645,-945,-460,199,-1000,698,684,563,-155,-323,-559,605,-11,1000,-72,-1000,515,-141,-959,364,-394,-656,-85,-274,-930,-309,-680,179,-480,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "org.apache.commons.jxpath.ri.model.beans.NullPropertyPointer", "setValue(java.lang.Object):void",
            new int[]{-239,-748,839,149,-187,433,302,332,-297,-638,-320,27,-641,213,-621,-164,493,24,-30,1000,240,303,76,-361,1000,-546,-214,-89,-675,-343,760,-299,1000,-1000,-179,4,1000,1000,1000,-1000,-882,91,42,-429,1000,-334,-104,399,-522,402,-721,-458,113,1000,572,1000,-653,545,479,71,160,259,-824,47}));
    }
}
