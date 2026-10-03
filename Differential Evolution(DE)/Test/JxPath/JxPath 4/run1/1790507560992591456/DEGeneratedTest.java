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
        org.junit.Assert.assertEquals("java.lang.String:aWQoJ05hTicp", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "asPath():java.lang.String",
            new int[]{-601,-268,741,298,-61,153,-629,528,163,-207,-21,505,833,-382,-185,-822,-483,-924,38,-59,-761,845,-901,-658,995,-371,-48,824,-426,-112,-224,-532,-381,-351,-437,-648,-858,-937,-612,-883,828,-728,-618,-413,-645,-669,570,-107,-289,396,-441,-560,-992,-766,807,290,658,-800,-325,19,949,278,819,997}));
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
            new int[]{-80,700,505,694,235,647,-915,80,145,441,718,824,374,-281,779,643,549,706,359,12,-406,94,684,-480,226,-455,-341,689,649,917,-60,401,107,507,565,760,-837,202,639,134,752,277,-194,-561,-93,749,324,289,830,-270,-551,543,-500,-14,519,7,374,-955,648,538,882,-349,260,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{711,-34,-184,297,-129,-330,2,380,361,505,457,819,387,0,836,-191,-609,409,-490,110,-418,-977,-771,801,562,28,-60,-439,145,-701,252,486,-745,399,86,866,643,498,241,-699,106,907,-246,-921,852,520,-752,840,737,-455,-228,-807,175,-372,16,-812,-277,710,271,322,564,768,996,94}));
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
            new int[]{-730,-311,-145,-942,871,-305,-31,736,469,-669,-429,-195,285,559,-432,-120,821,-451,-446,-767,-107,826,897,409,-270,-511,-64,-400,663,-191,-826,722,740,-566,-515,-442,436,821,-525,288,582,-434,-673,263,-397,828,-242,984,-797,191,21,-109,-233,-12,292,806,-100,376,-775,632,765,247,997,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.DOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{298,-385,-531,-644,-414,-550,769,-792,999,-314,-483,-870,-167,-491,462,-690,-292,-220,670,653,-957,527,350,-785,623,-804,-277,-534,-197,-631,904,-845,125,799,9,-2,899,-125,523,-59,-347,918,334,323,-746,-926,-218,897,982,987,581,-599,870,-77,-489,95,-785,-419,446,-438,327,-589,-583,-92}));
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
            new int[]{-692,-44,235,-500,917,-547,425,-626,455,748,-817,-484,-21,422,450,-605,-855,-48,-778,-709,510,-268,-217,-732,214,811,659,933,619,325,797,-442,-281,764,709,551,894,474,-841,821,-55,-939,312,131,255,757,247,192,-630,-796,-803,-43,-638,614,971,328,904,825,-369,337,-853,-663,-316,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{137,725,387,-657,-609,146,-91,-813,-373,872,244,662,-374,925,555,802,476,888,-38,-347,984,497,936,-158,369,-127,-342,-649,-182,802,415,589,186,695,-553,980,605,-634,855,-265,122,605,-877,171,-406,697,-377,-937,36,472,74,-174,356,254,317,-963,8,188,-962,-203,461,-218,72,728}));
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
            new int[]{-813,-521,-418,453,276,601,-774,-463,997,294,578,-890,429,127,-797,-296,-416,-107,-603,555,-558,-734,-292,434,-604,792,661,-134,-353,361,-806,56,-218,-510,-270,958,298,285,-74,-643,141,773,143,-415,-541,-516,-530,521,741,-438,-266,940,-759,532,340,913,-818,-130,-971,-17,-388,987,664,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-959,644,-323,661,-292,-710,463,495,792,-554,193,730,-982,-51,-63,690,596,-300,-342,-47,970,514,343,-156,252,516,-486,-161,-185,292,-446,-841,171,484,688,-713,959,545,-53,245,652,-36,-10,71,392,-558,-708,-863,-716,209,-167,696,-572,951,220,358,-33,-322,917,84,529,256,157,713}));
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
            new int[]{-412,316,14,-963,447,752,109,-975,838,-840,-530,-97,-535,196,-826,485,181,-941,-423,-578,-18,94,174,833,491,856,-570,164,199,875,279,58,363,-165,420,531,-825,-66,-982,-199,590,-250,976,-252,-6,122,543,-821,844,281,-295,808,316,727,-453,203,37,205,554,792,64,-450,708,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-117,-646,619,-897,402,-620,91,535,201,332,-384,604,546,-878,-96,81,-721,-402,644,707,-798,688,341,253,877,815,770,477,606,-278,624,-723,-711,103,701,-293,900,-680,-219,-638,390,-143,165,85,-34,159,818,-29,-101,-977,22,-828,-703,811,485,178,-409,-593,-15,-116,880,67,-213,-931}));
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
            new int[]{494,-419,332,-643,457,537,275,987,513,-39,-52,365,-131,237,262,529,444,336,959,-206,438,-833,-331,-819,866,-802,964,969,-737,-291,985,-588,757,-717,-54,973,696,40,611,-675,-880,-515,-271,-218,-519,-172,-698,850,258,-826,-425,-864,345,59,-339,-26,-559,481,847,-145,-938,796,-204,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{1000,47,849,46,-1000,192,-335,-441,-616,-412,-1000,-545,-96,642,-657,628,-557,42,-36,453,-1000,-1000,-878,28,-69,-91,-150,-1000,357,468,-27,-744,956,159,-706,700,422,-1000,-804,1000,1000,-886,-3,1000,115,-548,815,567,1000,-136,-1000,-81,1000,-1000,568,121,-1000,1000,402,975,1000,874,1000,1000}));
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
            new int[]{-567,919,-375,583,-167,-75,-209,584,-946,312,-112,-99,768,643,-895,696,241,-141,939,-575,352,76,-503,10,-560,317,484,-574,559,-556,614,-669,7,919,-300,-749,356,-60,406,-667,-344,-945,-742,-311,-63,-648,-640,952,716,-989,27,842,796,-844,-516,239,352,710,-285,-446,999,-727,-448,25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{-475,-883,-289,-803,-538,-376,830,709,291,359,632,740,833,-514,482,-86,26,287,-800,-371,-925,735,128,552,-617,281,874,781,-672,-158,951,688,-431,897,-47,-613,-804,65,-734,-744,-279,561,-141,45,719,-457,-797,681,-284,506,1,-886,-164,600,90,264,-327,39,587,-518,860,606,178,-237}));
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
            new int[]{-101,685,-708,-549,-39,-813,832,-879,-457,308,131,-77,184,-868,-177,789,717,749,-540,591,984,795,863,-414,-666,-924,442,326,-193,325,998,-465,427,998,-90,-540,-284,540,12,-362,211,-469,-35,152,-432,-232,459,-988,-370,464,-776,-459,-243,468,-538,356,665,721,-916,878,-979,-38,-86,-831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{235,-760,170,-715,608,-831,827,144,288,-595,-309,-464,-665,-66,-25,81,-595,99,454,452,-15,-148,-220,-23,-609,-384,624,-75,-527,673,962,528,-671,557,-301,147,-702,576,346,-553,-28,-709,-569,893,63,543,-5,453,-437,-523,619,0,837,846,-815,-420,39,907,-150,429,559,-828,-451,465}));
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
            new int[]{405,-224,-573,-82,-797,171,-773,0,-722,349,-98,-247,861,653,-110,-989,-703,-225,434,891,959,-616,833,68,929,814,-746,638,338,141,355,56,-392,580,862,-995,-207,-240,725,-522,-290,137,513,849,-379,-413,-701,233,844,39,-562,-919,887,913,-622,601,-602,-944,904,339,-388,-69,-903,-928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getDefaultNamespaceURI():java.lang.String",
            new int[]{-762,-16,-255,365,760,-92,-171,-928,787,174,215,257,-818,953,85,-175,-955,-846,-493,162,946,144,469,-699,-268,-851,-955,-379,545,519,505,-887,-564,-303,973,-836,-753,494,491,-167,-740,-955,-713,686,-461,-304,-569,399,-158,-991,108,-484,540,14,267,55,-954,-54,-446,962,-60,762,-881,-194}));
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
            new int[]{238,355,595,853,500,39,-476,-210,-153,714,-974,-385,-155,-770,803,-214,676,983,75,615,654,-682,-964,116,886,521,819,343,-213,-858,829,-220,902,-940,97,-677,757,-555,606,378,-687,-441,-581,713,-5,218,882,818,-735,641,160,746,-802,-599,971,674,662,-729,-550,-247,-666,-189,818,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-817,-571,128,-919,-754,-523,30,952,128,-349,41,-789,-39,-546,-570,776,-805,641,161,-818,-879,-521,-943,536,799,-183,685,-33,381,-864,117,-880,-855,168,-704,-824,-89,561,-614,310,-68,621,171,-424,-166,186,371,767,752,337,-465,-14,-628,-168,-913,-642,-674,-104,-953,619,-930,337,-26,150}));
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
            new int[]{603,-974,692,955,136,422,-717,71,-40,501,-679,439,-12,-248,215,491,511,473,-413,504,205,424,-639,-549,7,541,62,-35,590,-637,-343,-64,481,213,919,176,186,100,892,-938,-839,-182,-247,-921,901,786,152,812,-980,-212,-99,-932,-237,-21,827,-454,91,-743,511,738,-795,-579,-314,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getLength():int",
            new int[]{620,-877,529,-451,562,-963,120,523,706,144,-885,18,846,51,578,883,-163,-2,-680,345,736,-624,553,-343,-824,-437,41,497,807,695,-295,265,482,720,-124,762,-31,-423,898,-91,391,662,631,136,363,883,547,-139,834,-438,-419,92,-980,-818,-330,597,489,-435,8,-698,463,-558,430,643}));
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
            new int[]{-313,514,477,363,-124,578,243,122,-352,325,60,142,-214,-383,607,-602,419,67,281,-183,49,-457,10,896,-224,90,-745,-692,-213,882,-95,-910,987,505,940,-615,-222,-611,-682,849,420,-482,-673,828,-863,692,-163,-796,-782,564,313,-610,702,-591,252,407,-61,209,793,163,-702,-49,-383,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-232,-682,228,-807,313,708,238,789,5,-762,-781,223,926,-150,486,499,-790,794,256,-177,320,787,8,-62,-645,846,605,435,959,-419,-18,-743,571,-583,65,-774,-208,241,-757,-653,-530,345,205,-107,699,124,-781,-161,423,15,77,70,576,387,-196,-24,-254,-280,-25,532,267,273,-963,-205}));
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
            new int[]{-692,-305,-641,826,70,519,-77,-485,495,-648,-460,516,45,-315,257,-453,47,765,236,-300,-139,-324,-197,60,-288,936,-420,963,-613,-738,-328,-515,217,891,-289,-580,-225,880,-740,-105,515,126,517,38,-748,501,587,-327,-203,-525,664,-131,341,859,403,365,240,-102,-518,-615,-247,829,891,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{535,-112,-230,586,-76,-87,3,-227,604,276,981,777,-640,-737,801,690,484,-779,177,35,989,15,-457,-767,-844,201,-850,-502,423,-800,178,-368,-12,133,-788,771,-588,266,421,-250,958,497,-754,500,451,755,491,-376,-961,776,311,-866,386,-709,-180,258,552,-643,-251,-881,-901,-705,931,169}));
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
            new int[]{938,-407,504,-272,675,-119,433,761,665,-414,-570,769,-783,-292,301,-883,826,-255,959,-560,-343,153,330,341,843,-890,-552,-739,803,900,489,434,184,482,-196,-549,-73,-788,16,588,406,81,706,758,-339,-653,-706,-292,-702,951,-667,-70,-169,749,655,-373,924,-736,522,47,298,369,-727,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-571,-595,339,632,-392,305,239,22,782,-700,365,302,835,-747,983,-499,480,-185,-937,106,422,999,-17,296,905,-701,888,312,37,-359,700,-563,945,410,-417,-592,253,11,301,713,-266,1,-634,-797,-972,-304,-880,-283,267,319,973,-993,-821,-720,908,159,-245,953,-954,906,954,-535,-401,751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-928,589,-946,210,562,747,752,-3,209,-955,-717,130,359,-955,257,-404,-141,-22,-170,22,666,-509,170,-101,-699,543,-48,715,-132,555,178,270,968,846,-546,794,-638,-331,467,168,504,656,859,-552,169,402,983,-757,332,80,48,-984,166,469,-907,-667,-423,-411,-115,-739,45,-741,-607,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
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
            new int[]{305,611,-399,334,-99,-899,59,-798,740,-529,376,465,272,983,692,770,-331,226,96,-343,-292,207,-754,850,301,-165,2,-328,-643,292,-932,313,217,756,258,-71,983,992,-927,-100,-253,441,20,-53,905,633,-703,277,-432,-76,-661,446,-606,945,-940,-812,99,-579,574,-873,342,517,-313,-21}));
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
            new int[]{-321,-578,50,938,-918,888,-200,201,642,-82,65,75,37,-772,71,300,428,987,-146,836,802,767,215,687,174,467,331,-645,-789,-464,-84,-495,398,-135,394,726,-64,731,674,-448,-751,303,-194,351,-821,-612,-957,-888,-274,-666,-343,-310,565,-696,230,-357,442,500,82,445,-948,331,-7,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "getValue():java.lang.Object",
            new int[]{-521,383,90,565,657,-968,-290,808,-369,815,-895,-773,385,744,-923,611,994,-538,-80,-878,-756,-599,-767,381,942,929,61,-136,-451,719,-92,-941,204,253,-827,-692,-675,164,182,806,573,-78,-887,828,-851,290,867,971,-818,-94,-809,-132,-777,-606,-764,-301,798,445,-897,-360,368,-495,938,-679}));
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
            new int[]{67,-143,646,371,-857,201,399,161,172,124,-229,957,-229,-725,-452,-103,835,-494,-945,423,80,-256,-825,90,-884,-41,-847,-931,-853,-774,-253,38,955,-414,309,-314,845,175,-998,571,-107,916,-514,-289,-17,-5,144,927,-69,230,-798,-268,239,537,945,337,-38,-281,618,194,-425,977,603,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isActual():boolean",
            new int[]{-198,-46,871,-233,-452,-498,-522,169,-666,77,822,-286,545,-569,385,-167,664,103,700,436,888,356,-309,-860,-932,27,963,564,-773,532,-578,762,-424,949,-632,816,268,-181,-493,633,-46,-877,222,-938,622,736,873,-390,-826,605,887,209,-276,-141,-15,784,689,-872,11,-314,-577,454,-497,-556}));
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
            new int[]{-440,808,931,-195,-566,308,938,-341,636,-703,-123,-721,-11,559,744,-173,244,255,96,656,600,-745,608,-199,31,184,642,334,-397,-853,689,846,-304,748,-714,308,323,-203,-665,-427,-406,849,-382,-139,759,-381,-242,699,270,229,-150,25,-208,-794,585,-608,233,-751,-216,-431,-516,833,973,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isCollection():boolean",
            new int[]{325,-472,441,335,-91,-227,296,-912,-509,816,-776,624,692,-289,528,307,418,801,844,533,-988,-992,-782,779,634,-810,-510,-864,-619,911,226,492,311,360,711,710,-167,-374,261,-638,684,30,-523,-108,-854,818,-26,-552,54,-846,-429,-970,671,37,984,98,-435,-426,-363,347,-766,-169,394,919}));
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
            new int[]{707,-920,796,872,-129,548,-210,-533,-715,769,472,805,-817,232,-549,358,-566,-750,400,326,-430,484,556,611,-905,-531,141,923,-14,-861,-123,328,-26,-243,999,-266,-554,-907,-62,-952,627,-767,203,21,-928,-46,891,179,447,265,735,649,65,-576,624,-821,733,468,122,-802,50,-816,690,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-500,845,-997,-237,557,413,-674,873,780,-685,-656,-118,-919,-500,610,-245,-367,980,-601,-831,-513,-287,704,-199,272,60,440,-36,-843,-123,231,-413,-996,662,-142,130,722,444,419,-609,273,-882,565,-633,430,792,804,-643,-516,-614,779,286,-628,184,-308,-125,393,-650,581,-235,614,-496,-954,887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{-639,385,176,530,-234,-645,-34,-634,-748,-977,-741,-550,-964,643,-571,677,274,93,-589,-473,-702,226,-560,59,-459,-835,-69,-951,-777,742,273,947,-354,659,24,-115,-997,218,-41,-639,-945,476,753,-593,712,-446,682,95,-428,488,671,998,-153,489,-746,925,707,760,-441,640,-920,-845,-667,-434}));
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
            new int[]{157,605,-299,22,944,-486,-783,735,36,-731,549,-498,-222,860,-777,-466,18,589,939,-919,-360,281,-483,937,256,366,-301,-573,-674,-470,-241,-895,894,-548,212,926,387,-197,531,-89,-517,-892,-213,-549,-104,-662,-633,-695,-848,172,534,-945,876,760,489,369,956,498,-211,-289,-953,-139,-455,385}));
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
            new int[]{784,-992,863,-801,-981,268,677,391,357,-605,64,-296,-17,-192,30,-574,-226,631,-393,-29,-12,-120,296,76,-97,-220,-150,906,-957,-720,729,-281,-421,-320,-128,177,701,-104,92,753,-653,351,-631,-295,534,736,576,-926,703,949,785,848,-992,428,168,-16,268,749,-251,-636,-574,-71,778,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-674,-343,17,419,-654,133,-235,-283,-114,-915,116,-289,-286,-599,32,870,82,725,895,-141,-190,861,620,-128,-679,251,497,-512,494,-15,-476,-273,-317,-479,-939,-516,-691,64,-805,-935,-924,-645,-604,155,-971,-446,-260,232,-542,-177,-718,163,919,-757,976,-466,-427,-207,-15,-890,431,-762,161,-237}));
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
            new int[]{146,-5,32,165,-488,301,716,-363,-16,363,-160,-402,227,513,-226,-714,-79,912,-743,627,900,799,856,-575,67,-92,584,-553,450,-694,506,333,293,-416,155,-177,-871,-848,211,-719,395,166,41,-863,365,543,-593,-455,904,-534,-48,-733,885,780,-88,623,-43,-202,-101,180,830,144,595,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.dom.NamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{104,605,-393,-137,743,-911,135,-962,-310,721,-498,-984,969,278,632,860,599,746,606,-635,286,-316,-209,-760,647,-480,-990,374,-184,822,-214,985,832,826,814,478,317,870,450,-45,-316,-255,253,-507,325,-556,365,-448,63,-697,-102,-853,-465,113,377,-176,96,-586,-243,-149,-650,898,607,-429}));
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
            new int[]{-594,634,888,140,106,-256,-459,-883,525,-247,194,701,-103,236,293,148,754,-589,-807,-620,349,809,823,451,680,-755,-245,649,311,228,558,613,-285,-814,-580,893,20,-750,691,-526,711,694,-793,1000,-898,-377,44,-375,-797,-29,690,-252,-12,-489,551,228,990,-847,-853,133,699,-417,-584,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "remove():void",
            new int[]{628,-805,861,125,176,-4,-838,-798,479,-875,-502,-275,-416,803,486,-882,955,677,338,-543,134,-946,-813,-678,310,305,-77,516,-148,-1,546,-660,868,-243,470,120,-846,-34,906,-428,526,548,-498,217,434,-241,-292,-432,-964,656,56,-901,441,318,-567,-928,703,139,-251,281,348,617,988,-565}));
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
            new int[]{920,-572,84,904,141,52,927,719,-25,-754,-816,-927,928,-551,80,-955,813,-234,744,-337,-579,959,-589,934,-892,-2,-814,731,-539,-105,531,-679,261,912,12,-622,203,-587,569,171,-496,678,602,279,430,-208,-508,-314,-975,353,-172,-643,-102,222,-413,548,-237,758,777,983,632,-920,630,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{492,77,825,-311,975,248,543,330,-510,527,-713,528,-249,67,109,-301,287,361,45,890,-96,-306,157,-379,838,-705,116,-15,999,-30,886,-336,628,28,450,402,91,-486,-515,373,555,-91,469,-104,-147,776,734,-13,-31,-876,-304,333,264,-322,680,-528,-184,674,547,-803,-934,358,-308,-531}));
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
            new int[]{227,646,-791,321,-103,-866,640,804,506,-219,838,76,-972,-247,-399,683,-226,26,-718,85,-932,911,675,-287,-888,989,-695,724,930,-535,-339,860,-842,972,-738,635,-109,-948,-538,47,789,-295,-587,943,230,-317,-310,-719,-607,-456,774,-429,-124,854,773,-987,-316,388,564,-952,245,-289,889,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-148,-106,348,586,352,-82,197,-817,-645,-530,681,-610,-797,98,313,57,303,944,267,357,269,-140,-353,-838,622,187,-530,-133,-940,885,265,-394,-351,884,879,-366,878,-435,-859,328,467,72,328,-257,58,804,-931,937,469,892,263,387,-924,-299,865,-250,-951,505,984,-85,-317,324,-347,-122}));
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
            new int[]{267,-557,-370,-387,505,94,817,-794,155,-37,298,-576,564,16,559,-353,496,-335,-573,83,583,-921,-369,-326,-612,-324,804,-848,-549,-150,-790,-436,53,181,-837,403,-994,-590,-855,194,748,-742,973,-172,226,815,2,-764,-350,-332,315,-886,33,-800,-846,-699,-372,-433,-621,168,883,-275,-292,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "org.apache.commons.jxpath.ri.model.dom.DOMNodePointer", "testNode(org.w3c.dom.Node,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:aWQoJy0tMHg4MDAwJyk=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-200,-838,-192,18,467,670,-58,1000,352,451,103,-757,954,610,858,1000,-407,-1000,579,824,223,1000,1000,-291,1000,-1000,213,1000,-485,-590,-13,733,-1000,-1000,-1000,-240,473,-155,1000,575,-38,428,-400,805,237,654,-1000,88,-871,-1000,1000,1000,771,616,69,798,-823,-701,795,-1000,-558,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-952,-738,-192,798,592,-724,-759,619,736,795,-984,-21,954,741,-105,-93,-589,-574,735,-327,738,863,666,-13,-279,-312,0,205,298,-296,505,-419,-648,-600,-707,-258,473,192,833,-299,140,-435,338,5,-430,-147,-834,869,-149,-66,753,536,-63,-224,69,526,577,497,641,-95,294,-685,762,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "asPath():java.lang.String",
            new int[]{-53,-29,-654,-776,1000,-1000,409,402,47,343,-721,244,-55,379,219,-1000,-69,1000,-1000,30,-647,-939,-1000,1000,-1000,-29,-1000,946,-937,104,-105,-145,317,118,-220,905,-1000,-651,160,31,-680,-618,-135,105,-436,-159,794,1000,679,648,1000,-10,44,209,-377,-77,535,1000,376,703,97,-416,-568,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,779,873,-1000,-1000,-82,-1000,979,983,1000,-532,-1000,1000,1000,1000,1000,303,1000,1000,683,830,-299,1000,-766,1000,-815,888,-609,-1000,-237,39,-1000,-1000,805,518,573,1000,973,1000,-63,1000,-217,330,1000,513,1000,665,184,-1000,695,110,1000,-1000,1000,-624,1000,-400,-115,-1000,116,755,1000,930,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{362,439,879,999,915,-604,549,-80,44,-35,-417,-703,120,-437,-518,16,249,893,-405,107,621,-984,-857,895,-350,893,-619,-74,279,872,-74,228,327,105,-580,-100,63,731,-297,-231,358,778,449,-82,681,-737,-181,229,-10,670,113,-186,-887,131,-383,-851,640,-991,889,-923,-499,-701,-408,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{961,450,165,941,1000,-500,787,-810,1000,-325,291,534,-656,-1000,-598,-821,-381,123,-783,304,-27,-1000,-1000,382,-412,1000,-1000,-750,83,233,-881,254,1000,-1000,-559,334,-381,1000,-611,284,452,919,338,-487,1000,-1000,-1000,-259,179,-461,280,19,112,-163,145,-1000,1000,-250,1000,-82,-1000,-134,-1000,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-477,896,-513,-153,438,930,-752,-608,-294,-262,-686,-187,-230,395,-676,750,615,434,562,655,176,400,526,598,603,-636,-138,-1000,-272,663,-1000,-17,29,777,-14,-279,-1000,1000,-1000,-1000,134,-440,959,1000,-985,-773,-1000,-1000,1000,948,-753,1000,658,1000,-362,780,325,262,917,858,-1000,-884,-189,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,-221,525,-122,-291,-790,-1000,1000,-982,-1000,-336,-437,1000,-809,681,-1000,-1000,1000,918,1000,215,966,-786,-400,-1000,-110,-329,-601,771,929,-1000,-1000,6,-1000,24,475,-1000,499,1000,-1000,-1000,-1000,-71,750,168,-379,349,477,864,-697,1000,1000,-82,1000,373,-471,502,288,973,532,452,-666,-276,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNodeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-273,57,-172,614,212,-214,-86,-511,769,756,-931,-868,903,-152,-727,-743,808,683,857,-204,890,-399,406,852,327,-315,347,-775,552,-47,-576,-567,27,424,621,-61,-599,196,-198,-164,-719,880,295,-340,227,-187,-13,171,282,285,99,965,-602,-294,739,594,438,-886,-408,-472,-902,709,639,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{179,407,651,-253,1000,-30,1000,157,-362,-372,-366,-677,70,844,951,91,215,-365,1000,-327,-641,41,676,285,169,-327,-336,1000,-161,-66,547,486,-833,-18,-516,21,299,-350,157,-91,191,752,-45,-666,20,-253,987,-186,-680,621,-707,539,-255,-713,-152,-360,-38,679,-139,6,-866,-913,-815,-678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-13,1000,-126,-356,-1000,-848,317,-292,-586,-90,375,-1000,-752,1000,807,485,215,-893,142,-583,-1000,245,394,666,-478,206,-428,-1000,-405,-862,-873,767,-1000,35,-308,203,-245,-1000,271,1000,-1000,887,-476,-1000,-872,465,938,-1000,-822,207,-256,1000,-500,-534,-265,108,-416,21,20,-419,821,735,-364,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{72,-600,621,252,453,-278,-1000,910,-676,-1000,-1000,1000,-121,-459,119,587,-156,-630,-652,936,0,950,637,-20,-285,-572,731,43,-120,-201,107,1000,-279,1000,238,-266,-522,-76,-38,325,-843,635,-416,-971,-396,-225,722,34,-376,130,-57,400,-221,865,-814,941,1000,441,-787,-36,628,559,-804,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{842,908,396,771,-1000,-710,-903,201,-430,262,-448,524,-1000,229,922,57,477,-114,1000,449,-629,-132,-831,-1000,369,-180,447,218,-325,330,-594,211,-374,1000,-135,-830,-918,55,-1000,-160,720,-33,468,-1000,-105,-1000,857,396,-575,1000,-858,-1000,-235,-992,-556,302,-1000,-519,-32,-730,-598,61,-710,851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{83,814,-606,903,344,360,170,466,-789,-86,-449,-541,-907,425,-510,604,-180,-450,879,-505,-921,839,466,-472,57,874,885,-749,557,-608,-699,997,101,498,208,-209,-142,727,-947,-776,658,-34,-475,222,772,-10,569,-536,-325,738,-494,582,598,354,992,-808,-714,-501,-41,352,-881,-547,204,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-596,135,-252,-1000,588,-748,8,-93,-1000,-1000,421,516,493,-365,46,-470,-949,637,-181,-228,343,79,70,1000,-1000,-846,378,-41,221,-90,-1000,120,-47,737,804,198,-206,-784,257,214,-304,397,-143,-675,1000,1000,-304,263,614,-561,-1000,-281,-124,227,-241,247,1000,709,-873,-724,1000,-968,-344,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-399,-1000,894,1000,-1000,1000,832,-1000,-781,-382,363,1000,483,870,643,464,-1000,619,-1000,1000,-772,270,-768,-1000,-450,-1000,1000,1000,1000,1000,-926,1000,-1000,-280,1000,-1000,414,170,1000,-1000,1000,1000,1000,-332,-507,-481,22,171,254,-1000,587,1000,-303,1000,-771,-101,-1000,1000,1000,-1000,-172,1000,-613,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-1000,829,-777,714,-1000,-716,-169,1000,-1000,1000,-33,1000,670,1000,-1000,645,663,-94,-1000,913,893,1000,1000,-1000,73,-293,1000,1000,1000,-648,604,365,-1000,1000,513,-749,-1000,32,-43,-770,-181,108,742,976,1000,-1000,-955,-272,354,-266,663,1000,-1000,778,-525,-280,-1000,-400,1000,-1000,-637,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-748,-948,-708,820,-1000,-1000,-249,1000,399,42,1000,-1000,99,-921,-1000,-421,106,-254,433,-702,-705,-369,1000,884,-560,1000,220,-324,1000,-972,-32,-1000,1000,-734,357,51,554,1000,28,1000,227,-876,867,1000,278,470,-857,434,-1000,1000,487,1000,-401,697,-1000,1,634,-952,-918,-1000,1000,-454,1000,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{131,-241,-693,467,-1000,1000,968,1000,456,825,671,-1000,-240,450,1000,747,626,244,77,-231,-2,400,-1000,-710,1000,709,-482,-6,957,1000,-1000,107,472,565,561,18,-235,397,832,-895,-1000,-315,-1000,607,-146,-1000,-73,255,-1000,-1000,1000,650,-6,-1000,1000,277,694,-429,-236,-1000,-466,575,-716,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-389,-774,729,-189,798,-292,-937,-1000,364,-1000,-206,-227,368,-226,-630,-18,-599,-174,56,-1000,706,554,-377,-35,116,-374,225,-372,690,-1000,217,-180,850,-49,879,-1000,769,291,-563,-490,307,-737,460,115,724,834,1000,-246,1000,374,-146,1000,-353,1000,-1000,575,-1000,-564,452,-84,98,166,340,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{358,-716,-603,373,-305,-556,914,-975,-995,750,496,-402,-992,1000,748,503,-1000,339,849,-55,-611,715,-242,-789,-73,-734,-1000,-36,52,-764,-391,-1000,978,349,1000,67,-1000,393,417,1000,-442,906,907,1000,73,-1000,-1000,1000,-1000,-246,446,494,-369,-1000,-177,1000,1000,-258,-329,1000,994,-826,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{371,554,351,-111,164,823,-510,232,-1000,-986,-32,-163,-21,619,669,-1000,-1000,736,254,-882,-271,80,-492,344,-627,-311,1000,-473,599,-997,444,1000,-823,944,-406,933,-860,-229,-781,683,799,-582,362,1000,27,834,-1000,1000,742,-896,-360,539,-272,-476,-607,-529,-989,791,-1000,1000,329,-968,349,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{691,645,942,474,179,736,255,-142,394,186,-600,-334,423,-53,-994,-281,558,-183,183,797,256,736,444,422,459,887,867,392,262,151,-612,711,-280,-648,-815,21,-537,140,-733,541,-321,387,574,126,43,528,-823,-904,191,396,487,-820,-512,-194,774,120,959,-621,753,271,-463,928,526,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "equals(java.lang.Object):boolean",
            new int[]{936,1000,873,-711,-846,493,95,-739,-163,-947,442,-788,-1000,1000,515,-354,231,1000,-392,-1000,-515,-167,-59,-403,-568,30,-186,-1000,526,-1000,195,1000,-182,937,-31,544,-860,-168,-414,-137,-352,48,-235,225,-1000,626,-524,353,-72,-668,-1000,-673,-1000,9,-600,758,-1000,1000,-911,282,318,-782,166,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{485,350,33,-389,-769,-200,-588,33,156,172,68,144,-601,-825,-835,-953,1000,74,1000,260,-603,-887,-1000,-166,-572,-1000,-1000,-16,-1000,575,-241,-398,-383,242,-456,894,1000,-185,223,1000,-54,446,1000,-957,-354,-706,903,-513,732,-364,540,-311,-526,-428,1000,-569,-1000,-1000,-40,-1000,-1000,-798,652,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{502,-422,-66,-446,-46,-284,637,942,1000,1000,-1000,-148,397,137,-986,-138,777,-688,625,451,-1000,-742,-685,1000,534,-54,1000,-593,229,1000,57,-630,-180,-628,-775,541,-250,-241,-192,352,716,261,566,-219,-417,924,511,1,-1000,-541,-1000,-700,-616,-424,1000,-102,-86,-1000,317,-425,122,-1000,1000,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getBaseValue():java.lang.Object",
            new int[]{978,309,-108,-753,-679,-884,-47,970,28,1000,286,129,-601,449,-1000,-561,547,-913,429,743,-385,-913,-796,-400,-630,-1000,1000,-16,-903,774,-146,-398,-383,-31,251,-155,1000,-185,474,712,-514,446,878,-427,-1000,-439,903,-513,377,-364,488,-311,-591,-774,846,-620,-1000,-212,-61,368,-1000,-1000,652,311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{459,-1000,-306,-477,987,286,397,-342,-116,-418,945,-1000,-556,-689,1000,-440,-921,-1000,507,-1000,-305,-591,-216,661,661,-115,860,-1000,-754,-656,-983,-514,-1000,-145,-31,463,-1000,855,-495,847,723,957,1000,1000,86,1000,219,-293,497,-1000,396,-317,252,-127,-1000,246,169,416,-1000,-54,-202,-77,-480,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{-279,580,-312,-332,544,52,587,256,1000,1000,-405,897,-700,34,-1000,-613,270,773,-866,1000,-1000,-126,957,672,-260,227,-62,-1000,1000,270,1000,69,28,125,168,-107,784,3,719,31,-1000,-148,-1000,1000,-337,-462,-1000,1000,-536,616,-459,-1000,765,-153,335,-786,-618,1000,267,-773,133,-789,337,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getImmediateNode():java.lang.Object",
            new int[]{703,36,-1000,168,-393,1000,658,-585,-231,-83,257,-1000,-1000,-1000,765,944,321,-619,1000,-881,-654,-535,-845,-138,1000,-51,1000,-1000,718,-1000,957,-1000,121,-161,504,-1000,478,425,-273,44,-555,-466,-30,313,985,538,-296,342,-218,-1000,-55,-1000,973,-614,-708,1000,-791,-192,225,-699,-1000,621,-687,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{-661,119,-552,-784,-424,-404,-267,166,791,-283,1000,1000,-1000,-235,-1000,685,-178,-937,81,662,-795,-1000,-400,-134,-864,263,949,-1000,-55,-78,-207,-460,-15,-692,916,-334,149,8,-1000,-309,726,-1000,6,-612,-985,-877,-590,144,-1000,-1000,764,330,-1000,907,247,-56,384,414,678,-1000,1000,-463,611,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{284,294,249,986,684,508,205,1000,1000,-859,-1000,39,951,400,-1000,173,-1000,-131,-370,-37,934,-1000,454,-89,572,-366,117,-295,-672,457,-53,523,463,-1000,-400,751,48,131,553,582,495,564,1000,218,-1000,-521,418,-122,-653,-60,-646,-90,15,-411,161,1000,-380,90,203,-111,834,548,-885,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getLength():int",
            new int[]{794,1000,-687,-111,1000,372,-191,1000,-1000,-741,722,-1000,-1000,1000,-808,-437,393,426,-272,-1000,1000,-428,1000,-83,856,628,1000,-1000,200,817,484,1000,-1000,-1000,-575,741,343,164,1000,292,955,149,626,86,15,-193,299,295,-1000,-382,-1000,-21,1000,-794,1000,1000,-1000,-783,-354,858,-82,1000,-1000,316}));
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
            new int[]{-794,-1000,708,-917,-430,54,-56,-565,-751,-117,1000,108,11,-1000,312,504,-82,-486,-379,-319,515,824,1000,305,33,-478,642,207,847,-522,-148,28,675,1000,-1000,734,-51,797,96,-1000,-233,1000,817,932,-487,-978,-1,533,-19,-30,678,1000,-632,189,461,-850,-214,-595,-411,-818,-483,82,-1000,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-1000,-368,-75,-428,-1000,669,138,72,294,121,1000,724,480,-763,-132,655,-542,-79,72,-1000,-61,450,-1000,347,-1000,-509,-782,-645,-1000,13,-1000,152,121,-1000,-243,407,143,-548,90,-1000,732,-506,358,103,851,661,-687,-32,-1000,-609,-293,378,-976,-597,-416,1000,-253,-137,-274,1000,-498,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-478,-996,-342,-965,829,-522,453,-1000,112,991,1000,508,-26,439,995,-1000,1000,1000,-1000,-719,280,-62,345,389,269,-460,388,-423,1000,579,1000,-660,-2,-668,1000,571,-492,24,-1000,484,-1000,-831,-601,-258,-570,-1000,-889,-666,854,1000,-441,-1000,172,1000,1000,-1000,-192,329,1000,-892,-1000,-449,-546,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{-1000,641,954,368,-104,-640,-748,-282,543,-717,1000,482,1000,-425,994,-843,-1000,814,-616,-1000,14,309,1000,-167,789,-1000,-573,454,-1000,790,-406,848,-1000,1000,-341,-1000,131,553,62,605,-415,-150,-1000,136,-1000,362,442,-567,-1000,160,-1000,404,-1000,-747,423,1000,-966,719,-429,400,-861,1000,460,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{245,-839,-945,-723,99,57,140,-273,941,56,-685,49,-661,-203,791,721,259,258,-875,187,-883,-48,371,-777,-335,-912,952,-794,591,307,-646,929,-277,-579,83,-877,-96,-775,-46,-869,-782,894,-210,263,72,749,-164,-705,850,-782,803,-685,338,-249,367,-384,915,86,-860,-260,-345,839,664,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI():java.lang.String",
            new int[]{997,504,-945,-1000,742,239,-78,554,400,1000,-685,-124,-364,-56,234,-471,-222,-414,-622,-632,-1000,-1000,82,-1000,1000,-1000,928,-536,1000,1000,-1000,1000,-364,184,418,-1000,-1000,-970,463,-384,-1000,1000,-1000,-161,-366,1000,-1000,-140,1000,-1000,803,-685,338,-693,-104,-1000,565,-737,-195,506,-1000,884,1000,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{1000,-1000,453,6,-1000,-23,1000,1000,-690,-314,1000,1000,-216,-1000,1000,49,1000,1000,-1000,277,-811,489,-911,-1000,1000,907,1000,659,95,162,842,-600,967,1000,1000,-360,-676,1000,1000,-1000,1000,-1000,-1000,217,1000,1000,-1000,-720,1000,-861,-1000,338,626,-653,-439,-782,1000,1000,-56,314,1000,366,-1000,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{1000,-905,21,624,-782,101,1000,670,-971,-501,1000,1000,-871,-448,228,-335,207,1000,-927,-350,306,-798,370,-218,1000,1000,1000,154,-258,-876,-550,-515,182,1000,51,-1000,-1000,486,1000,1000,809,-631,-1000,-50,945,927,-1000,-391,971,-735,-1000,-458,813,-972,630,-335,1000,189,283,143,1000,-523,-573,-976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{-153,-522,693,-997,1000,-107,631,902,-157,-305,-313,164,374,-245,499,-1000,-16,-1000,1000,-586,-155,-1000,904,-291,1000,812,-235,-1000,-712,-1000,385,165,47,790,256,-350,-104,-662,-1000,-42,-1000,-964,-687,1000,1000,-21,91,-188,556,-25,-1000,-1000,1000,-1000,446,-254,482,-373,894,-1000,133,-799,-298,-192}));
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
            new int[]{944,-553,-168,-1000,285,1000,875,-916,1000,-417,1000,559,604,395,997,-849,431,1000,1000,-726,-1000,91,462,1000,1000,-584,1000,322,-1000,124,1000,847,497,-255,404,364,1000,210,291,138,604,473,-376,-231,1000,1000,108,-1000,251,246,-356,-617,-116,415,-111,-220,405,-192,-411,560,481,242,105,706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{311,384,-144,-671,-612,-20,-21,-305,-171,-812,-882,721,74,994,98,122,378,931,-715,-858,405,535,209,45,-966,-146,752,51,-334,268,-95,229,920,-865,-796,-102,690,126,-370,191,-738,-536,932,624,425,790,-817,-259,323,-146,-344,-794,171,828,71,72,923,684,-312,394,-574,767,800,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "getValue():java.lang.Object",
            new int[]{920,-425,-255,-537,494,-561,-61,-160,124,900,483,-561,90,640,-235,627,430,-590,-934,-374,-590,-918,913,-274,475,879,-457,964,-217,93,760,-684,-48,-177,587,-516,806,-380,767,-531,593,309,372,289,-633,-287,583,462,102,-461,40,144,407,-774,-934,192,728,244,552,832,-644,12,-699,-884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{-739,-1000,123,-194,-1000,1000,921,362,-37,930,149,609,587,239,1000,-991,23,319,11,-1000,669,282,-1000,-618,118,393,120,-804,-739,-1000,-12,148,962,-793,668,-1000,-864,-520,1000,-392,-1000,-409,369,925,1000,845,141,1000,-994,-636,79,-46,143,-1000,64,-327,-760,1000,695,1000,-163,-1000,-1000,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{-30,1000,390,338,-1000,164,711,1000,1000,-126,164,1000,-394,-755,927,642,-1000,647,-1000,-678,613,-476,261,-524,693,-547,-593,-951,-339,381,508,718,86,541,-124,-844,1000,453,-1000,277,196,-216,-1000,504,37,1000,149,-122,105,1000,-1000,-514,-303,1000,1000,-585,1000,-386,-197,-109,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isCollection():boolean",
            new int[]{-1000,-468,81,-1000,193,-1000,-688,1000,1000,874,-433,916,1000,876,-651,-243,-1000,-377,1000,-66,-129,1000,780,1000,1000,1000,983,-625,-1000,653,866,752,-756,276,-585,1000,653,-234,621,89,-1000,-566,574,462,1000,-108,-64,517,-1000,-652,1000,-1000,1000,787,763,1000,-400,-432,-352,651,1000,-1000,201,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{85,833,-73,-191,-432,712,-20,595,822,826,711,-474,-801,-429,-772,-355,121,-352,106,-414,-974,244,-613,-692,694,723,935,690,53,569,-323,-211,-190,-487,514,-917,11,16,-512,-522,-993,-432,-810,-505,190,628,-701,638,327,-542,-312,-298,-600,-409,-442,515,382,-793,-294,-556,610,324,228,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{57,-279,-375,-904,-532,-578,-659,-650,478,-1000,-520,1000,1000,-604,-1000,-272,943,342,-44,-1000,-761,-1000,1000,1000,-77,-983,1000,-980,476,-205,-1000,221,-412,1000,21,-33,-980,1000,630,-608,1000,1000,1000,544,-88,-1000,-1000,-617,355,1000,1000,-109,-287,400,-1000,-664,-645,66,1000,375,-365,-720,1000,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLanguage(java.lang.String):boolean",
            new int[]{1000,1000,-680,332,-372,313,952,252,1000,-362,1000,-886,50,-360,-477,1000,-113,1000,-1000,1000,1000,346,-213,345,77,1000,-1000,923,-791,-23,1000,-147,-268,-1000,-68,230,-1000,-76,-257,123,-790,-404,113,-617,-955,-542,484,-999,-493,-359,-1000,174,540,403,1000,1000,-863,427,-494,-463,-666,16,-1000,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{381,-1000,675,151,943,1000,803,744,-358,217,496,691,751,-44,902,429,578,-1000,378,552,0,1000,-1000,785,457,-593,-961,-1000,619,-1000,-266,-599,609,-1000,114,522,354,-178,-627,1000,-285,219,616,1000,-706,424,-98,447,-779,-601,-592,123,1000,-1000,393,-1000,997,953,223,-692,674,-574,-573,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{85,-957,540,-501,-520,655,-528,654,-321,-82,-659,287,-912,-171,-266,685,-155,-660,853,-475,131,-656,-697,-237,-367,-389,687,-109,-884,325,589,469,88,-263,341,-315,-384,-149,-186,-841,497,-967,411,-800,188,-43,-587,288,-829,-582,-103,681,761,-830,533,742,-250,208,754,824,740,644,-964,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "isLeaf():boolean",
            new int[]{-365,-443,705,-881,654,0,1000,434,1000,464,242,936,278,1000,493,417,343,-1000,-305,979,-1000,1000,-1000,908,363,211,-1000,906,973,-890,-161,-1000,1000,540,-400,558,223,-37,-1000,1000,226,708,-295,1000,90,1000,-758,-1000,951,-903,299,-553,1000,14,534,8,1000,168,-875,-1000,-150,-841,782,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-1000,956,-210,-294,-495,391,1000,-1000,-570,188,-192,-202,-996,-104,63,364,400,558,-1000,1000,-927,290,1000,439,1000,434,1000,-460,1000,1000,-1000,26,-625,491,-519,-1000,-1000,1000,557,424,-1000,147,743,-645,691,-440,155,-223,-212,438,1000,-788,781,-1000,-643,-960,-1000,241,-615,-585,349,-124,-631,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{568,-936,627,-873,910,-390,-304,-543,23,-359,-754,-40,691,-269,-468,-821,-671,143,-812,-684,-1000,1000,-898,-973,160,341,813,1000,371,856,-93,304,5,297,-408,775,274,-211,419,61,357,521,-445,-1000,-448,331,-10,-439,-705,112,885,-484,421,1000,550,617,-156,-533,-1000,554,1000,-683,1000,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespaceIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{568,115,-873,-533,-511,517,-983,-1000,1000,-102,210,-663,-1000,-838,-468,18,-582,-781,-737,1000,-817,-68,1000,-362,-450,692,728,102,-183,1000,224,686,-1000,224,-1000,-178,37,1000,1000,1000,-902,1000,801,-393,1000,453,-706,-1000,1000,755,1000,-484,421,-9,773,21,-1000,-440,-1000,37,1000,-799,-37,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-735,-1000,-807,-1000,-546,533,167,-799,-158,763,189,1000,-208,-501,410,-23,1000,655,-413,58,622,315,166,600,-422,-759,-296,-1000,-1000,1000,381,321,-794,627,65,95,667,862,-341,82,374,-1000,300,499,215,377,634,-1000,-1000,219,998,-167,1000,733,-1000,1000,-1000,1000,-1000,-928,664,614,1000,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-863,255,593,-925,220,-70,102,44,-189,-377,231,779,-344,194,-530,-239,-355,695,645,-911,-965,52,-989,-13,27,168,199,-761,-995,-40,239,-553,-234,919,543,924,-101,89,221,-702,96,27,381,451,618,-749,179,-730,-568,984,298,907,558,572,262,700,-902,851,-481,-264,-924,-32,620,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.jdom.JDOMNamespacePointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-239,-326,-66,-333,-555,-57,-643,-276,787,-67,-744,-616,796,-240,470,844,-743,324,-351,868,14,-469,-614,507,-134,649,694,815,68,237,186,-174,-580,-875,22,50,-258,259,-876,935,184,-125,-507,-654,263,-585,-461,949,772,908,-750,571,782,76,-228,143,-156,-621,507,562,-802,-616,168,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{440,416,-216,690,-584,74,331,1000,-358,-243,452,-418,-94,-836,-236,-713,-1000,1000,1000,-1000,-298,1000,379,507,-783,922,1000,488,1000,50,376,246,1000,28,-585,-320,-1000,-893,-662,915,-55,266,-1000,281,-282,576,530,-669,547,345,-1000,-318,735,77,559,-137,-303,279,169,46,162,957,-1000,74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{220,168,-813,-488,-133,-755,282,-981,-876,186,-970,-928,296,911,79,-317,-616,78,-726,-259,-663,-282,48,985,-645,-979,775,-908,-919,-854,93,-424,-741,-408,980,-852,-285,402,-362,206,-664,-722,157,-796,-826,549,348,779,742,-169,143,746,-890,248,-477,307,505,-904,64,-810,783,-40,875,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "remove():void",
            new int[]{498,-95,156,76,-291,0,-298,1000,-360,-1000,772,-712,-699,-629,-661,385,-646,805,0,-666,141,-21,305,1000,-4,449,310,0,1000,-225,1000,560,755,73,1000,-1000,-1000,-556,-630,0,0,-858,-1000,0,-107,-634,1000,-874,-1000,1000,-726,709,861,-81,623,0,-475,-140,470,-671,330,947,0,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{559,-226,57,-1000,-198,606,625,-678,-667,638,-317,1000,455,-194,-55,1000,-1000,361,1000,-1000,438,462,-384,-133,805,-495,433,-895,-1000,-1000,-586,770,-699,509,-461,1000,778,1000,378,-233,-1000,-319,1000,221,-504,-1000,30,-1000,286,-582,-810,-780,-1000,426,630,1000,-1000,568,-428,-1000,1000,1000,846,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{458,-351,57,628,-542,-477,-172,-411,174,-890,-666,892,935,-910,-694,-507,-332,79,-125,-443,-460,815,997,794,-102,-382,614,-670,602,850,-561,702,842,380,424,-746,-561,-292,5,-737,254,-685,-994,447,-178,56,-251,159,631,972,359,544,537,756,-509,-814,764,309,803,402,-371,-705,385,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "setValue(java.lang.Object):void",
            new int[]{940,247,537,709,65,-158,-657,-631,211,-1000,-265,749,389,-1000,157,-1000,-570,-10,-830,176,-368,558,282,-296,-212,-134,-335,0,689,1000,-1000,620,1000,189,-128,-1000,-623,-281,-343,-1000,1000,-940,-664,636,-260,1000,-70,1000,584,221,236,110,1000,1000,-531,-1000,1000,591,1000,1000,-884,-1000,-385,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{997,-349,-615,21,580,469,49,1000,677,276,300,913,-124,837,775,399,-372,1000,646,463,721,868,-437,-229,-830,-735,-735,424,208,975,-783,1000,874,124,-274,1000,-646,-211,518,625,291,785,704,997,-716,-679,1000,-422,1000,180,679,-799,-300,-311,-756,-580,89,-490,377,-87,641,-1000,-107,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-183,471,168,187,-1000,196,179,-249,558,-133,1000,-866,973,-1000,651,-643,-1000,-1000,1000,-786,-498,-633,-395,-106,-383,-440,1000,54,-1000,-875,-1000,-841,619,1000,-37,-914,281,967,-557,-691,35,411,-110,-363,999,-929,-369,917,-550,-286,194,669,-1000,-447,-385,417,203,319,-159,584,131,283,581,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-848,-170,954,-321,612,200,-1000,-340,984,396,-991,-692,-758,1000,-254,-762,45,1000,-21,606,878,-321,-733,1000,219,-1000,-148,637,-685,1000,-9,66,1000,85,325,1000,-662,-1000,-178,-1000,-412,305,-171,474,14,-1000,-441,1000,-119,1000,-719,-1000,655,-465,-215,-351,355,-473,1000,-231,-906,572,-454,749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "org.apache.commons.jxpath.ri.model.jdom.JDOMNodePointer", "testNode(org.apache.commons.jxpath.ri.model.NodePointer,java.lang.Object,org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
