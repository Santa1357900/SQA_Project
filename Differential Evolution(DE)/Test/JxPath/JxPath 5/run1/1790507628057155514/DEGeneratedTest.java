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
        org.junit.Assert.assertEquals("java.lang.String:L0B0cnVl", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "asPath():java.lang.String",
            new int[]{354,956,-562,-625,145,-24,1000,747,539,155,-335,-1000,414,356,322,303,34,-679,-273,245,-370,-1000,-601,-536,582,-1000,-359,-1000,-655,591,-1000,210,-598,1000,-277,499,-655,-547,234,-1000,-484,1,1000,1000,602,-1000,-803,1000,-954,7,22,75,1000,-979,-384,351,-564,382,-1000,-136,786,1000,-1000,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:Ly5bLTkwXQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "asPath():java.lang.String",
            new int[]{947,696,98,-858,896,266,1000,-62,-187,-202,-151,476,698,-499,-77,604,-225,-160,222,-916,-26,-382,426,744,-189,247,-724,-1000,1000,1000,-1000,-412,-1000,-753,-895,441,-369,-1000,220,-400,-191,-488,-148,400,1000,-243,-1000,305,664,-116,364,1000,929,-93,-81,7,313,567,-330,170,199,693,-137,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:Ly0weDIxNA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "asPath():java.lang.String",
            new int[]{-6,737,-982,-960,-894,180,55,-532,-623,-916,117,-390,-433,-350,-996,868,987,977,89,783,-883,-451,-649,392,-577,465,-754,-926,-108,-999,-118,971,-168,839,-427,-401,-844,143,983,910,524,-443,-186,-302,492,-858,249,12,-888,298,-760,-979,325,-210,105,-350,-100,152,897,201,-283,-79,518,-792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{987,216,496,-658,-1000,741,-1000,1000,1000,-677,-755,-1000,541,74,421,1000,428,-354,-205,-457,-656,427,1000,-1000,559,-652,-710,762,-1000,-92,-315,694,26,-1000,-1000,1000,57,527,1000,-390,-904,-184,-645,540,-588,-667,-397,-166,23,1000,1000,-1000,-707,1000,314,481,-1,320,476,1000,270,-1000,-1000,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{420,-120,178,-933,-806,583,-319,528,-537,-316,-929,-830,-913,-1000,433,-829,95,703,316,-1000,-662,-738,1000,-580,961,-363,-323,342,-1000,-712,-1000,820,594,166,401,738,401,-762,702,-647,-913,38,1000,718,-735,-879,-936,-213,199,328,255,-104,-33,388,20,1000,-424,-125,-20,1000,-46,-214,2,-981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanAttributeIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "attributeIterator(org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{139,1000,178,-1000,-1000,468,1000,594,278,511,480,-417,192,-562,714,-426,-627,-865,-482,-896,-683,-191,692,-1000,317,1000,-949,100,302,316,883,776,420,-1000,523,966,-578,-26,179,-167,-953,172,-1000,668,-684,-626,-469,784,482,-15,-406,-1000,-1000,830,1000,564,-468,1000,-20,1000,-1000,498,-417,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.PropertyIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{-9,-1000,1000,1000,-1000,595,-222,1000,1000,-115,1000,-986,-943,-513,908,604,258,-1000,282,-765,-1000,31,118,-819,-452,-480,-731,1000,-194,-560,-649,390,1000,-1000,-1000,360,1000,-461,-590,160,1000,-64,304,-1000,-1000,-705,-581,1000,1000,515,-471,-655,199,879,341,952,356,-1000,871,555,211,-689,-423,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.PropertyIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{416,314,507,961,-649,424,1000,99,-196,-382,-96,-587,1000,-428,1000,-592,461,542,-435,-301,-1000,1000,791,527,-822,57,-626,1000,-706,-100,-458,-425,833,-1000,-510,-71,62,-605,-105,-170,716,247,1000,1000,-772,-526,-1000,984,682,261,154,-81,-976,150,235,588,-904,-1000,-547,1000,-694,707,-52,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.PropertyIterator", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "childIterator(org.apache.commons.jxpath.ri.compiler.NodeTest,boolean,org.apache.commons.jxpath.ri.model.NodePointer):org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{107,-400,-1000,-806,528,80,552,-1000,-866,-764,-511,-268,-60,-1000,-684,242,175,944,742,-191,-146,123,764,577,535,-18,407,-1000,-1000,-708,884,1000,256,191,904,122,-137,3,1000,400,200,-295,-713,-262,346,-1000,639,-1000,-445,-1000,-763,-119,158,140,617,-1000,-891,1000,-707,-912,900,-299,31,439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.CollectionPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "clone():java.lang.Object",
            new int[]{-453,-753,-404,-990,199,27,991,-80,-727,-731,-401,-426,1000,601,71,-57,983,309,100,295,262,966,408,-1000,426,-1000,692,670,1000,741,-85,-701,-179,-183,-1000,25,-1000,-413,141,764,-114,-242,-3,-610,-69,754,713,-269,1000,-947,317,-1000,-942,558,-76,695,1000,-353,-1000,17,214,421,-399,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "clone():java.lang.Object",
            new int[]{341,-876,557,919,865,826,734,-573,1000,-187,-175,763,887,1000,-612,1000,316,-592,-185,-738,1000,700,-1000,1000,-574,899,-1000,-411,-1000,-1000,1000,-1000,-171,-1000,1000,300,388,973,-651,-1000,613,-245,762,212,376,-1000,-974,-1000,-1000,1000,970,1000,1000,-603,990,535,224,432,1000,-2,661,286,-1000,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-38,-320,398,-957,602,-223,-1000,1000,-1000,-1000,-68,-822,107,-971,187,-1000,-1000,1000,943,796,874,165,1000,-1000,1000,1000,1000,599,901,-1000,1000,602,-1000,1000,136,286,-1000,1000,-136,-1000,-799,-542,-1000,223,-104,-1000,-1000,1000,209,1000,1000,-1000,1000,1000,268,-872,-560,223,-1000,-1000,1000,655,1000,412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "compareChildNodePointers(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.model.NodePointer):int",
            new int[]{-401,1000,-1000,-533,275,298,1000,727,407,-383,924,370,-365,1000,848,1000,731,-444,-412,-1000,131,-1000,-1000,1000,-295,-1000,156,242,144,1000,-318,503,-354,372,-259,-298,-283,243,400,567,472,1000,1000,1000,-641,-61,273,-905,-344,437,631,-495,68,205,-934,-126,-319,-289,257,369,-416,1000,-920,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassCastException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "compareTo(java.lang.Object):int",
            new int[]{-577,-710,295,-514,360,-39,-480,878,-367,-891,-1000,-464,-468,362,53,246,113,-484,320,-207,-607,415,-719,1000,-219,-786,453,423,-1000,-195,354,153,906,-740,-565,-503,24,1000,406,-1000,-947,-288,-28,-628,-132,-389,927,331,-211,364,-1000,772,138,-796,-917,-655,64,867,1000,769,387,828,-490,20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "compareTo(java.lang.Object):int",
            new int[]{1000,1000,471,273,-1000,55,-969,-815,1000,554,-888,112,36,255,543,240,-659,551,237,-827,-137,-917,649,-283,254,122,-639,1000,906,-633,-804,-1000,-75,188,298,-860,-63,-79,-666,568,106,-530,811,318,1000,61,-168,-683,-993,-121,-1000,630,1000,-625,-869,706,1000,-1000,-1000,-912,188,242,41,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{162,-738,837,-1000,180,-457,-860,1000,407,975,-569,-1000,-450,-250,-811,1000,1000,1000,432,-530,-1000,-541,-1000,141,46,-514,1000,672,84,247,80,-264,403,-945,555,-1000,-1000,575,-554,-76,179,-910,-569,-469,676,-1000,1000,802,216,335,426,261,-374,-355,-70,448,-536,154,-118,-199,166,978,1000,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-181,214,1000,-1000,687,452,-849,860,-366,1000,-143,-1000,-431,-7,-835,1000,1000,52,583,-1000,-400,-1000,-160,-1000,388,-602,-251,-122,421,4,413,341,-626,-668,965,-1000,-1000,21,-841,-191,643,-1000,-194,-469,415,-1000,1000,531,716,1000,-842,787,-1000,398,422,241,-468,-94,629,-199,1000,-642,222,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createAttribute(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{566,904,-446,170,-53,1000,-1000,-787,278,804,-397,454,314,-1000,-504,1000,-936,-567,-1000,380,568,-597,-924,-1000,831,981,1000,-986,-322,269,1000,-983,-1000,-489,1000,-400,-1000,-609,-361,-720,-275,-810,-783,661,904,-30,963,806,390,1000,-866,879,-318,738,224,1000,908,-1000,183,1000,37,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{667,1000,344,-773,-570,325,1000,-1000,1000,999,-717,-1000,426,1000,-384,843,-1000,2,26,393,-405,-943,-549,-87,-431,-1000,141,351,1000,423,-802,827,1000,75,727,791,127,-211,53,1000,-462,-966,275,1000,-259,-834,1000,509,-1000,579,-1000,-917,1000,-644,260,952,-669,-964,626,-902,903,920,-86,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-339,326,-564,-126,454,-1000,804,-645,-145,-427,-881,121,387,814,901,1000,-1000,-992,268,1000,1000,12,850,989,188,-132,84,-908,454,916,-140,-787,-239,-536,494,-600,317,676,-770,-166,592,70,-814,898,507,-1000,1000,476,-7,634,921,1000,-300,240,506,1000,-983,-1000,431,369,-786,-161,88,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{503,-1000,-1000,-614,1000,-469,1000,-240,1000,613,189,711,1000,73,188,164,-1000,-369,888,-1000,1000,-456,1000,-779,403,-911,-113,-732,-517,1000,-677,253,-160,891,1000,-593,1000,-129,-1000,1000,386,-1000,-633,84,299,-1000,-1000,239,352,-406,-346,-865,353,4,-173,-1000,-397,-1000,684,1000,-424,253,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createChild(org.apache.commons.jxpath.JXPathContext,org.apache.commons.jxpath.ri.QName,int,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-567,-524,474,-683,-457,231,975,368,-951,-581,831,990,-513,-82,-751,-213,-144,-108,876,-275,-13,176,-272,728,-653,662,701,-314,376,-470,-307,92,527,208,47,437,-43,280,489,747,471,205,26,193,-237,-315,398,-417,-736,-209,543,716,-26,-475,323,91,534,436,258,-754,-497,81,375,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.CollectionPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createPath(org.apache.commons.jxpath.JXPathContext):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{867,-334,138,480,146,-433,185,309,334,-1000,469,76,859,-68,-725,711,-362,596,-624,1000,-186,-283,54,1000,-945,-993,-579,1000,-1000,1000,217,-961,-518,126,-19,529,757,-55,-548,997,886,707,-277,4,1000,-100,-953,64,705,943,-913,-918,-959,-87,-841,-705,-74,657,77,-1000,-617,-731,300,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.CollectionPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createPath(org.apache.commons.jxpath.JXPathContext):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-985,-1000,774,-691,351,-198,1000,742,-399,-819,136,-357,-536,-781,-676,799,1000,-688,388,590,891,457,1000,856,-81,1000,117,-1000,1000,507,355,81,284,163,217,-1000,-1000,121,1000,-1000,-1000,987,-1000,933,-1000,427,935,-839,-848,-1000,-1000,96,384,-1000,-142,-601,-243,-1000,776,121,1000,354,-935,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createPath(org.apache.commons.jxpath.JXPathContext):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-931,-222,-1000,-404,-792,1000,-946,14,-492,430,-1000,-173,424,1000,-893,999,-1000,-201,-229,466,292,371,472,167,-881,510,-82,1000,-479,330,213,-1000,-1000,-1000,312,1000,-71,1000,-1000,414,821,-784,366,791,1000,-775,1000,-1000,1000,1000,-316,-1000,360,818,345,-294,-249,110,548,800,-582,-31,-975,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.CollectionPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createPath(org.apache.commons.jxpath.JXPathContext,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-513,-1000,573,1000,-1000,609,-413,1000,389,717,938,1000,213,163,1000,-173,840,-1000,-1000,-1000,1000,-628,-621,293,-145,717,232,-1000,-1000,326,551,303,-1000,979,1000,634,-154,1000,-734,-598,905,-89,-871,-262,376,1000,169,-227,-1000,231,-822,1000,1000,505,-359,1000,-99,528,1000,1000,943,928,-319,470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.CollectionPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "createPath(org.apache.commons.jxpath.JXPathContext,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{935,33,-240,158,778,-130,-863,-860,-977,-939,-660,637,746,817,732,-312,-23,64,-2,587,-267,-504,-111,974,-720,-290,802,96,831,-855,-511,413,-640,-682,449,-500,703,-825,831,602,701,-666,57,-873,-181,407,791,859,-686,-330,350,-250,860,912,-756,766,-671,-516,93,455,313,251,-127,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getBaseValue():java.lang.Object",
            new int[]{-721,1000,-1000,-1000,884,109,-1000,-890,809,-1000,-79,1000,-483,-331,1000,529,392,-1000,-383,-409,659,104,931,-765,887,354,147,-9,-743,1000,333,330,-184,656,-1000,941,-468,-416,870,-351,-1000,1000,525,-632,-1000,874,669,1000,-839,107,200,-1000,-214,-56,-490,-513,21,194,-44,64,-728,721,953,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getBaseValue():java.lang.Object",
            new int[]{-493,-21,-1000,-1000,414,1,753,85,-527,-1000,-638,1000,-789,-497,460,768,227,-419,452,659,985,-571,-1000,73,1000,-326,-122,-1000,-343,829,1000,1000,-276,-358,-91,-737,-386,-783,827,-233,-621,1000,1000,81,489,315,-884,710,-121,-1000,-443,-264,179,-634,207,174,418,-1000,141,623,-1000,710,140,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getImmediateNode():java.lang.Object",
            new int[]{-21,-510,-1000,-771,-1000,-701,823,310,152,-715,689,-90,803,-1000,-182,564,1000,-618,237,1000,220,-799,-29,-516,-1000,886,597,-785,184,-447,-6,-110,745,-981,1000,-525,-535,-201,-708,399,-1000,1000,18,63,1000,-859,1000,841,-195,342,-362,-375,745,1000,23,-1000,1000,336,692,-84,-36,308,700,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getImmediateNode():java.lang.Object",
            new int[]{16,202,960,-282,-758,-238,-1000,759,-32,980,1000,-614,959,-873,491,-930,682,-552,195,763,569,-140,-1000,-529,-322,1000,73,578,-1000,888,269,1000,-866,-1000,1000,-1000,233,-1000,-53,-133,-173,667,630,269,888,-885,197,395,-1000,1000,-359,735,827,-587,-213,-1000,804,-1000,453,-791,-671,1000,-277,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getImmediateParentPointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-323,-924,-512,765,860,-801,51,524,92,66,591,-361,311,853,-881,787,136,-182,131,938,-462,-345,-966,-164,-938,775,-291,698,-823,58,966,787,-417,343,975,515,201,741,-367,828,-76,-313,-750,-235,222,-378,-37,284,204,115,-169,712,-155,-652,709,525,730,-48,-332,-477,915,389,-6,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getImmediateParentPointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{831,-265,-210,344,-72,167,885,-797,560,-854,-337,-1000,-25,833,130,-54,-1000,670,-900,-188,-222,219,-528,-835,519,404,-463,80,266,-135,872,760,-891,1000,-253,366,-104,660,-692,-84,665,-1000,1000,-45,752,264,322,81,197,942,-473,-680,14,-414,40,-4,-1000,467,714,603,-290,84,-207,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getImmediateValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{922,-795,133,936,53,-452,-516,590,-587,-360,569,503,-249,-797,871,866,14,662,594,-49,-664,-903,-850,-148,757,-850,284,138,88,-563,177,504,238,-741,-876,-597,-520,512,-4,380,-348,-471,-434,-634,-937,-654,20,554,-978,329,-529,-382,325,483,169,-948,781,-150,-794,-832,917,375,-989,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.CollectionPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getImmediateValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-33,-1000,-275,-1000,-181,-523,-928,-639,1000,34,1000,-1000,-512,-1000,-613,-777,970,-308,400,-824,163,303,1000,1000,909,764,152,490,-739,-146,687,-1000,-713,834,971,-27,-1000,-783,844,-1000,-273,797,-303,244,865,-208,-959,262,-1000,-240,471,-368,-864,-117,-84,729,114,415,-967,-233,-811,-178,-387,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getIndex():int",
            new int[]{679,808,43,-875,641,108,-271,-316,-25,-64,505,-461,596,-185,464,-716,654,983,-612,522,-689,50,525,198,-867,523,-104,744,-412,527,-452,90,-741,389,28,-976,491,-217,-713,397,425,609,197,-771,609,934,929,889,-268,928,-478,238,412,-10,202,-164,-586,-992,162,171,-796,-930,-86,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getIndex():int",
            new int[]{193,-105,7,-133,1000,-972,-122,-218,1000,-930,706,-413,753,-386,-993,568,769,496,-1000,1000,-697,-1000,302,1000,-392,760,-653,-220,337,1000,509,1000,1000,65,490,587,-1000,-788,764,510,1000,822,-649,595,-1000,-33,244,128,-515,1000,-912,1000,1000,-1000,-169,-1000,-389,570,1000,-943,1000,-828,1000,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getLength():int",
            new int[]{172,-184,1000,74,891,-165,604,7,-507,-5,371,-700,553,792,1000,-326,906,280,153,-920,397,1000,1000,-31,-817,-673,1000,1000,6,-243,226,395,472,458,146,381,-700,-442,-243,750,302,1000,-37,-475,-416,-1000,255,-1000,364,773,-181,-353,-163,899,541,-544,-951,-278,-619,-498,336,213,-592,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getLength():int",
            new int[]{331,234,32,147,-365,-774,162,786,-648,-1000,1000,-1000,789,-1000,957,-379,1000,-1000,-117,-1000,664,19,126,-1000,-1000,-729,-384,1000,397,8,-827,751,1000,556,-709,899,561,-794,338,1000,392,-266,-1000,1000,221,-652,-1000,-677,137,889,-955,-1000,-1000,-978,545,1000,45,627,-280,526,-33,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getLocale():java.util.Locale",
            new int[]{963,-899,-399,-286,366,-697,-767,476,-113,-757,379,483,28,-849,-651,789,-61,-903,340,35,-245,569,620,519,160,641,384,304,-873,-318,-243,-80,300,-638,134,435,858,839,-473,209,860,-585,-60,460,-960,-265,928,932,-172,612,-595,426,956,980,-419,719,453,119,440,483,28,297,-438,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getLocale():java.util.Locale",
            new int[]{199,474,-241,240,517,-975,286,-610,470,529,-989,1000,89,-627,-1000,-1000,-524,-405,1000,-299,-657,389,561,-644,1000,786,271,-1000,-281,-826,77,-721,1000,-142,-360,591,-655,-177,-763,-525,1000,431,-459,-1000,393,487,229,-1000,764,-420,-638,-1000,50,-72,1000,-111,49,1000,-220,-1000,15,-1000,42,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{-445,1000,905,-741,-386,1000,313,1000,573,381,-422,362,263,516,524,194,-480,1000,-143,766,-405,677,-509,86,-1000,-962,879,-1000,-122,-84,-123,126,38,-749,388,-95,-506,1000,-255,1000,1000,-1000,-397,-92,-365,770,1000,67,924,932,-459,182,-351,1000,-648,843,908,-976,107,-567,-1000,-480,964,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.QName", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getName():org.apache.commons.jxpath.ri.QName",
            new int[]{1000,1000,116,-775,244,-196,630,769,56,596,-982,1000,-518,-234,1000,290,1000,-559,-667,-16,301,408,-860,-449,-7,-519,551,-350,75,-325,524,-784,-900,-564,-260,-469,-927,-1000,-460,1000,283,-800,-169,-474,-1000,346,-790,-559,76,1000,-621,-109,291,1000,-166,-892,285,-778,1000,-814,-781,-624,440,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{-897,366,-449,-736,-910,-750,-692,1000,-242,952,1000,-1000,421,602,-755,-1000,689,1000,-349,-655,737,772,1000,-1000,829,-407,1000,-799,615,108,1000,-728,-421,-406,-33,-209,-709,795,1000,-195,-7,-1000,559,1000,787,-20,-1000,-7,1000,747,77,-9,-650,519,-292,1000,-255,-998,-347,-173,-549,-366,-1000,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.NamespaceResolver", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNamespaceResolver():org.apache.commons.jxpath.ri.NamespaceResolver",
            new int[]{387,293,-533,-752,634,-987,-177,1000,27,769,992,-854,-837,-154,-517,-1000,-130,328,-270,-773,1000,751,1000,-184,1000,-293,1000,10,871,1000,1000,-1000,993,-1000,841,472,1000,1000,696,-346,1000,-1000,872,579,-1000,-179,-978,-1000,1000,402,1000,1000,-1000,540,-1000,217,-930,-647,-965,124,271,-1000,-1000,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNamespaceURI():java.lang.String",
            new int[]{557,492,-407,51,376,-970,974,-158,231,-384,559,-884,-489,-1000,-515,-805,-152,-263,-249,-330,-591,-968,-358,485,1000,-870,1000,172,278,-315,553,-36,924,-463,-1000,926,852,1000,206,-962,552,-1000,-528,906,609,528,-358,1000,1000,-747,454,-709,-716,-1000,360,-667,400,-181,605,129,-26,-447,294,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNamespaceURI():java.lang.String",
            new int[]{-769,443,343,648,284,-387,839,695,-395,1000,937,-123,-707,1000,737,-239,-834,501,-414,1000,-1000,-235,522,-973,934,-873,340,-1000,-532,577,-416,-156,400,-947,-558,-1000,416,-1000,-334,-220,894,507,-627,-1000,369,835,-719,-95,-1000,-800,-49,596,-1000,34,754,487,-32,917,755,-385,-79,-670,1000,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{715,901,-651,364,-719,-356,-602,-828,-986,-818,182,682,-188,-687,-849,89,-914,404,597,-79,812,-221,-546,-368,823,-814,-575,692,159,-582,967,-115,633,881,-986,-524,-420,720,153,-946,176,-19,442,-37,-317,180,52,-561,515,-324,-539,-143,106,250,87,811,-775,-540,-101,135,609,447,-157,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNamespaceURI(java.lang.String):java.lang.String",
            new int[]{399,400,-1000,1000,-164,-977,-121,-103,-230,-728,-228,-472,1000,-655,-343,29,-663,42,1000,121,-56,222,-1000,-15,-760,-1000,979,367,-816,-598,286,-1000,328,-314,-98,77,811,400,1000,-25,-950,315,95,118,217,-537,143,714,-71,52,-1000,-400,385,-1000,824,28,216,169,-102,-111,-436,89,-400,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNode():java.lang.Object",
            new int[]{419,-202,-963,-179,-1000,943,997,-21,-866,-1000,331,114,-633,-416,1000,275,-600,282,-328,880,-656,342,409,-124,-618,-873,-521,-574,-408,382,-530,-201,-562,121,130,-444,-315,716,398,165,-415,-398,-285,245,618,-291,-383,-681,-1000,104,-363,400,-18,84,-735,-1000,142,-500,-381,-584,-435,-160,348,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNode():java.lang.Object",
            new int[]{1000,1000,-611,-875,-1000,24,-1000,1000,-638,-1000,1000,658,-703,-780,1000,391,-728,-807,-612,1000,-120,-1000,-288,-114,1000,-1000,-997,-460,-27,1000,1000,359,106,-826,193,1000,174,1000,1000,846,-1000,-177,691,-329,-762,1000,413,-1000,-1000,-693,1000,1000,-467,311,476,-801,-1000,97,1000,-1000,-457,1000,-954,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNode():java.lang.Object",
            new int[]{-994,96,-346,-300,890,-704,479,1000,-937,-974,-295,-533,-929,-208,531,-851,-134,-830,20,-762,501,269,659,-443,493,-304,-907,-618,-866,-491,388,-143,90,-818,842,-300,-756,-1000,-530,-968,-865,-127,-23,-99,374,353,-799,-895,-462,-287,-170,1000,464,-22,-104,-33,804,76,-179,-235,-430,-650,-442,-462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNode():java.lang.Object",
            new int[]{847,-1000,-820,-380,222,-334,789,-185,-291,-137,91,-646,-486,-1000,842,775,-218,-251,-490,723,-694,508,-421,655,-1000,-1000,512,-673,-1000,-23,945,-182,1000,-651,840,-574,-158,-807,957,1000,-107,-1000,243,310,-97,-492,-496,-499,-1000,-3,659,1000,-535,148,64,-15,-204,-349,69,-889,315,1000,-747,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNodeValue():java.lang.Object",
            new int[]{331,226,177,1000,800,-57,-263,989,1000,-73,5,-211,788,452,-828,-310,-807,-89,-965,983,412,-538,926,-640,531,740,616,-364,-458,-210,-627,588,-984,1000,570,-617,-1000,-208,1000,-475,-562,1000,1000,-554,449,545,-4,-336,440,-132,339,1000,-696,-472,103,569,716,1000,962,200,-564,213,-631,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNodeValue():java.lang.Object",
            new int[]{348,332,-188,865,468,285,-386,942,690,448,194,-293,138,-32,-569,-717,-88,-239,-391,-35,-10,258,159,-619,222,-739,398,393,-709,214,-646,-247,-272,527,840,1,-405,-250,414,-573,292,417,586,-932,-912,108,-314,-373,440,-71,184,994,-51,7,-60,601,868,830,842,224,-489,294,-398,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNodeValue():java.lang.Object",
            new int[]{146,-199,-337,-565,622,711,-452,343,-619,-1000,-1000,-975,760,700,-366,-726,233,1000,-354,-1000,151,-370,763,-1000,-210,172,-171,169,-206,-580,-307,-302,-925,817,1000,972,1000,74,-689,-307,1000,-330,-394,66,-1000,1000,-558,311,1000,125,-523,-301,-140,-698,-1000,-245,-512,1000,-72,1000,1000,-899,371,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getNodeValue():java.lang.Object",
            new int[]{-113,124,-553,-51,687,-334,-886,1000,764,1000,603,692,192,-44,955,-521,-23,-1000,-199,1000,1000,456,978,776,119,903,477,-11,-897,1000,260,467,938,148,-488,-558,-979,-728,6,-240,-1000,61,-31,-827,343,-1000,-196,-310,-227,90,701,1000,-623,1000,16,-427,31,-995,-81,-1000,-1000,1000,-757,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getParent():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-457,1000,677,-423,-1000,-1000,-1000,98,724,-493,245,743,1000,-390,-83,49,-541,-1000,-975,-9,-1000,183,1000,-703,944,-94,873,579,60,-207,-85,-351,-898,62,-337,-1000,589,-873,1000,11,-1000,964,127,11,-1000,1000,172,-1000,-583,419,814,993,65,650,-470,-145,-1000,156,-30,608,-552,856,-472,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getParent():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-67,-201,396,-570,-265,387,400,-868,1000,-796,2,-785,-72,270,-64,-323,28,-51,-7,359,650,-8,-208,591,66,195,-1000,48,559,-396,580,104,-258,-172,762,428,165,417,323,-394,-655,-731,837,400,-1000,-657,21,-783,799,-438,20,-24,688,-363,-1000,1000,-798,201,-594,-474,361,-474,-778,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{95,-698,363,998,-833,308,-416,770,1000,-1000,615,62,1000,-270,1000,1000,525,874,1000,179,153,-316,228,-623,396,260,-486,30,153,-498,-1000,-239,10,1000,-559,624,598,1000,-524,2,1000,-953,-730,211,-31,1000,1000,837,-708,-497,-29,-192,-1000,-753,95,-422,454,-154,1000,-861,540,995,-970,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getPointerByID(org.apache.commons.jxpath.JXPathContext,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{-418,-382,-936,177,656,-1000,-1000,-700,-214,-738,1000,-323,-1000,-424,1000,1000,1000,-745,1000,-186,-803,-1000,-267,-1000,804,-12,1000,375,99,382,-913,-440,1000,691,-150,1000,-1000,-940,-1000,-1000,1000,1000,-920,608,-1000,-886,1000,-1000,1000,983,-465,234,-1000,-654,1000,1000,1000,906,1000,-1000,106,-200,864,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getPointerByKey(org.apache.commons.jxpath.JXPathContext,java.lang.String,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{-733,-1000,-536,1000,-174,393,-254,72,975,169,583,-623,118,-1000,-288,-38,-1000,176,269,-1000,-488,-78,-279,-358,-150,-718,-223,1000,1000,608,1000,-10,169,222,1000,22,-202,-719,199,294,94,-943,1000,433,-1000,-254,-1000,-311,-1000,1000,-350,-214,-1000,-23,95,515,-614,-1000,-331,-466,1000,1000,0,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getPointerByKey(org.apache.commons.jxpath.JXPathContext,java.lang.String,java.lang.String):org.apache.commons.jxpath.Pointer",
            new int[]{-40,-204,852,670,-1000,-56,745,-1000,699,-320,1000,-1000,727,-457,-860,435,99,-283,593,-177,-1000,-825,1000,114,793,-946,-1000,654,-522,1000,494,574,936,-109,42,257,1000,-1000,-537,-602,834,-1000,297,131,-734,-855,-1000,-309,-1000,35,1000,-1000,-312,1000,403,565,528,-850,908,-1000,984,238,-303,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getRootNode():java.lang.Object",
            new int[]{-817,1000,-48,-492,-328,162,-31,715,-201,-468,497,-1000,526,147,-427,-44,-572,540,-720,-237,-301,1,17,100,-329,-16,1000,-508,-366,-1000,292,1000,48,1000,1000,-635,-1000,-1000,-852,617,340,1000,49,595,976,1000,-1000,-611,-388,-1000,10,-346,1000,1000,-1000,1000,-734,6,-1000,340,938,-121,-878,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getRootNode():java.lang.Object",
            new int[]{935,297,1000,-674,-271,131,-385,193,-1000,-751,85,-328,-1000,-638,282,-420,-388,1000,-509,-553,1000,983,1000,175,-179,-449,-729,108,185,734,-765,-1000,359,922,-790,-92,567,1000,725,-268,-91,-409,-774,-421,-82,-569,346,79,-229,-188,603,-460,-65,-170,-231,-193,930,1000,269,-419,-44,543,708,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mg==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getValue():java.lang.Object",
            new int[]{-637,-1000,304,-993,1000,-73,379,-1000,1000,-174,1000,782,-656,-703,1000,-1000,-289,1000,-1000,-7,1000,-1000,681,1000,-1000,645,-522,2,1000,-1000,272,573,-54,-245,471,-1000,1000,1000,591,177,137,895,-592,469,1000,1000,1000,-423,1000,-1000,363,-350,573,4,250,276,1000,-344,-719,300,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getValue():java.lang.Object",
            new int[]{-466,-1000,208,-124,768,164,941,-1000,1000,190,157,-548,-1000,-122,312,-547,1000,484,-678,-617,753,-139,1000,-718,-684,190,1000,-116,-1000,-100,-146,179,-469,210,-950,-717,-252,1000,-1000,1000,590,480,596,1000,324,124,-333,-179,1000,-100,-909,-1000,450,-36,208,484,1000,364,91,-689,-1000,268,-56,-812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getValue():java.lang.Object",
            new int[]{-1000,1000,454,-1000,244,-723,52,-416,548,-102,-123,115,-1000,-419,-892,-683,841,-285,-59,143,887,-783,740,-179,1000,-92,332,992,-1000,-10,480,-787,-166,438,-6,885,527,-1000,62,161,412,-628,78,-965,-760,-313,760,159,-55,582,-91,609,-1000,318,-632,-1000,-76,60,423,209,-901,734,-528,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-665,1000,1000,268,322,577,-717,-595,-734,487,-263,54,697,137,-525,-582,-119,-1000,567,940,857,-1000,225,523,1000,130,355,1000,1000,165,1000,-946,-1000,853,424,-1000,-316,933,1000,-251,-1000,122,22,-554,257,136,-1000,-398,16,-1000,-1000,519,543,-565,-1000,-472,352,-425,-27,-1000,-1000,-949,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.VariablePointer$1", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-160,804,845,1000,-317,633,-457,-391,-247,-503,325,-987,772,-1000,35,-789,-1000,-7,-58,860,1000,-1000,1000,659,842,-199,284,763,26,1000,1000,631,-451,-921,90,-284,-1000,-94,1000,466,-1000,582,-492,-515,-1000,632,1000,618,239,-1000,-890,260,518,543,-1000,-564,155,324,-462,384,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "getValuePointer():org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-750,-1000,939,-578,-199,1000,968,-693,-316,487,442,333,-594,1000,274,1000,1000,115,849,-58,-256,-1000,-300,-1000,145,270,-825,-60,-306,619,-414,-364,1000,-1000,553,357,1000,-434,-1000,-251,1000,122,366,338,55,155,1000,39,-724,1000,709,-371,-211,183,706,-472,44,-1000,70,1000,753,1000,-1000,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isActual():boolean",
            new int[]{779,-1000,328,-707,296,-981,463,-267,614,178,-912,-783,665,-1000,-501,850,-176,-1000,755,-509,-832,-958,-754,-1000,-793,352,1000,23,-1000,-515,254,-198,-770,-610,294,607,761,-220,514,989,-1000,-1000,129,-883,996,965,1000,77,-214,599,1000,973,627,608,94,-788,-1000,-1000,688,-271,-232,-116,108,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isActual():boolean",
            new int[]{347,-629,-335,-1000,-747,-662,-304,217,-1000,823,-1000,-268,-1000,-242,1000,292,-667,254,1000,635,-1000,-825,225,-265,-940,1000,481,-195,-865,-969,409,-267,-557,-593,1000,1000,973,-776,-248,1000,-1000,-71,1000,-748,990,1000,1000,-1000,-13,1000,100,302,782,513,-1000,6,-181,-991,1000,-547,-1000,1000,406,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isActual():boolean",
            new int[]{735,-388,586,-1000,-30,-854,-630,-435,-711,208,-495,-20,412,-1000,-998,1000,-198,-800,466,-17,-1000,526,-520,-984,-478,630,1000,-141,-1000,-436,615,-162,-1000,-888,303,1000,552,-890,627,1000,-1000,-1000,48,-464,733,1000,1000,717,-250,1000,906,1000,961,1000,575,-829,421,-1000,1000,-209,-487,252,974,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isActual():boolean",
            new int[]{263,-1000,-542,-87,1000,-850,1000,1000,293,-363,-9,-712,-1000,-1000,476,1000,672,-1000,832,-287,452,-130,662,-1000,630,591,1000,718,-753,-1000,1000,59,84,-1000,459,-1000,62,-145,1000,-332,-793,-267,-747,-1000,519,711,872,-1000,-246,1000,1000,-498,1000,-130,879,-1000,-557,-676,107,-917,432,816,-1000,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isAttribute():boolean",
            new int[]{977,1000,703,164,-1000,-451,49,846,-946,-577,627,-291,-1000,-699,-456,1000,-881,223,-904,51,-888,226,-1000,-488,1000,-455,-761,1000,701,-1000,-334,-300,73,229,1000,1000,331,-681,-69,-1000,-255,440,-52,420,632,-958,-963,909,1000,20,764,1000,460,499,3,837,633,317,701,1000,629,893,-415,-826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isAttribute():boolean",
            new int[]{-585,-737,-707,481,509,704,-222,-910,676,-407,239,-941,948,674,-399,-826,309,328,-236,-953,463,-559,235,-13,230,175,606,-704,-7,992,-831,671,279,-565,-313,-256,779,277,-451,281,883,-34,-622,-933,174,502,546,75,-793,-661,955,-821,-601,238,971,-642,-143,-442,472,-891,589,972,561,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isCollection():boolean",
            new int[]{-527,-1000,-648,205,1000,-611,-811,92,81,-5,-300,-93,745,118,146,414,-847,-111,-21,-122,-192,83,759,354,1000,-928,-671,-694,244,188,-206,-43,-1000,95,-131,-90,-754,301,-402,638,374,1000,509,1000,34,-659,-112,-925,707,-596,-986,915,-696,1000,286,-46,248,1000,258,-209,-430,523,-899,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isCollection():boolean",
            new int[]{-1,127,-1000,475,-822,405,402,-25,1000,666,-930,1000,1000,-295,1000,1000,-405,378,-344,-84,359,-1000,-721,-769,-1000,754,977,-882,407,-1000,775,-564,-1000,797,-547,1000,-1000,989,1000,702,-276,903,-1000,-711,-212,-444,-511,-33,-638,1000,767,210,-483,-234,450,-746,391,240,60,-1000,248,-559,-356,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isContainer():boolean",
            new int[]{247,-599,296,301,714,371,-155,-557,-205,-274,-355,97,584,301,-189,-1000,123,-1000,-103,-160,-1000,-1000,161,299,782,-351,-375,543,59,-277,-405,-1000,649,1000,1000,-540,-1000,653,400,-734,-696,-458,202,-709,-918,-388,-30,-795,306,-1000,219,1000,-593,334,-471,252,165,-1000,-185,-493,-542,143,553,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isContainer():boolean",
            new int[]{97,-190,254,772,-138,1000,-174,-96,-998,-245,-827,-662,423,792,-244,297,985,-829,-36,-583,-185,-1000,-1000,350,941,-323,-393,-725,-46,-186,-404,485,-878,27,-1000,-1,37,-290,-330,-486,-403,54,-223,-769,-53,75,760,-756,464,-1000,-261,1000,568,-539,-128,-32,643,-166,-41,-875,-956,-92,-723,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isLanguage(java.lang.String):boolean",
            new int[]{-637,-148,687,-794,508,579,176,-995,312,-60,-431,-906,-716,-505,-558,652,-821,828,-746,-370,-170,-558,-569,38,-866,444,-340,411,933,462,406,346,733,967,489,-380,226,-802,234,-646,371,208,-805,901,-534,-775,-272,-298,-941,-539,794,244,809,801,316,410,-496,-641,-906,-994,-698,834,874,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isLanguage(java.lang.String):boolean",
            new int[]{-41,-235,-524,-240,-656,342,-910,696,-171,254,-200,123,-694,-794,-803,-626,506,209,556,232,-928,501,440,105,-591,80,-143,25,-300,-812,-886,-236,-294,-160,-632,-997,947,474,-456,-605,697,-817,-218,-372,877,-399,-169,141,330,493,946,18,283,-617,747,-244,-173,130,222,120,-860,258,604,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isLeaf():boolean",
            new int[]{347,-1000,662,-1000,-301,-1000,-667,-1000,1000,-198,1000,1000,260,32,1000,-371,182,464,13,-735,-320,-812,400,-881,843,710,-926,-449,60,-1000,610,1000,-246,1000,-446,257,-323,-391,574,642,1000,66,172,1000,416,-833,803,114,-429,782,-311,381,-1000,-980,-874,-1000,732,954,913,-1000,-723,746,-673,-529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.jxpath.JXPathException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isLeaf():boolean",
            new int[]{1000,-730,1000,803,-217,-344,-281,-34,-1000,838,-1000,-7,1000,387,-730,-285,-129,-440,804,1000,-732,1000,-997,426,-1000,6,1000,91,476,123,1000,-1000,101,1000,1000,-398,-1000,-844,194,-206,1000,976,1000,-1000,58,1000,-653,331,-390,1000,-1000,-1000,-824,-136,-208,-431,26,-1000,689,753,152,-479,373,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isLeaf():boolean",
            new int[]{-622,419,-871,-1000,217,-460,-31,-67,689,-138,1000,-188,-1000,-557,1000,837,736,-564,308,-92,-491,-904,786,-597,684,696,-226,-558,-597,-579,-366,83,305,-747,-1000,-73,1000,593,-1000,319,-1000,254,-893,713,21,352,723,98,-703,-462,374,918,458,-521,535,363,-309,1000,-298,-675,-349,860,-585,37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isNode():boolean",
            new int[]{793,-692,-685,296,-522,-254,546,452,201,-622,13,-579,835,762,-503,563,-589,116,273,883,-113,-948,-139,838,585,244,111,-415,429,-423,942,214,733,-744,-603,-523,907,795,104,28,-846,-132,739,231,13,578,601,-753,-572,859,58,-727,898,-519,-746,590,-758,344,257,353,-474,-326,-427,972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isNode():boolean",
            new int[]{683,147,253,43,144,1000,-129,-117,202,55,-204,83,52,-51,-63,491,-533,785,672,152,200,-139,-289,391,224,133,-465,-274,618,590,-75,1000,2,-424,-932,-181,-445,350,61,768,0,-229,473,-109,471,262,-1000,65,-375,395,315,-40,-361,-102,18,34,332,47,996,-873,644,31,531,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isRoot():boolean",
            new int[]{-563,-778,-1000,-1000,395,-83,-853,-1000,266,-25,-331,1000,-1000,413,1000,1000,566,-175,758,1000,1000,-116,169,787,894,770,-649,-581,-417,934,-249,-843,1000,-805,772,165,-827,-237,-1000,26,1000,59,-568,1000,-888,1000,-1000,562,-489,-1000,-1000,1000,-1000,-284,-305,-477,1000,545,438,-798,976,128,695,574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "isRoot():boolean",
            new int[]{227,-195,225,533,-77,-509,-761,-276,-149,-536,-570,1000,-1000,76,-1000,1000,774,38,556,-298,839,-246,1000,663,52,716,-1000,-184,1000,-1000,-276,-503,1000,-38,-439,-1000,-89,1000,-1000,1000,701,117,1000,36,1000,-301,1000,-41,-1000,-1000,-669,923,270,-672,-79,-847,-458,-98,848,667,301,176,201,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{227,-558,1000,1000,843,1000,1000,-204,-1000,-436,477,-61,671,378,-305,-145,200,591,319,-564,-661,1000,-1000,490,-1000,-853,879,563,1000,-6,252,-492,294,170,485,406,-1000,663,1000,977,1000,395,-503,-1000,-680,218,-535,617,495,-525,-1000,808,-1000,-181,-1000,-867,-559,-125,757,-608,786,534,1000,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{1000,136,899,430,-816,-151,-526,-126,506,1000,-153,-196,1000,110,-1000,285,-594,-815,-200,1000,210,-1000,1000,-207,-1000,-164,-884,832,-63,-1000,-556,-117,534,810,1000,-266,-391,-465,522,-184,-1000,973,567,400,1,-307,-236,-251,-520,-100,-1000,90,-205,1000,-328,-639,-850,-1000,-495,749,436,325,726,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "namespaceIterator():org.apache.commons.jxpath.ri.model.NodeIterator",
            new int[]{759,-428,-1000,-1000,379,514,-1000,-478,1000,864,-790,-379,-71,-123,251,-450,-257,1000,-1000,-400,1000,125,283,157,-479,541,-1000,-1000,350,707,240,880,-592,-856,319,-10,847,-341,244,-1000,1000,-1000,147,1000,501,-615,1000,180,85,273,1000,551,1000,-1000,-366,-84,140,1000,-49,1000,-1000,-272,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-361,304,-824,-1000,-481,1000,-1000,-188,417,-1000,-1000,-1000,-489,1000,-328,1000,-249,-56,-264,-142,775,-1000,-293,-1000,380,988,-854,62,97,404,675,396,-705,567,400,-1000,-720,609,-1000,-1000,-237,822,-934,-1000,1000,676,473,-975,12,973,-1000,-798,-278,424,-591,1000,-1000,-791,-1000,-130,-1000,-957,929,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "namespacePointer(java.lang.String):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-1000,-1000,165,-301,858,-148,838,-1000,-490,-682,959,-409,-1000,1000,393,-16,-273,296,-1000,630,203,1000,1000,-1000,728,-1000,860,1000,287,-269,-79,-854,205,514,509,-91,-1000,1000,-317,-934,-979,1000,-989,-648,-756,-1000,313,-126,473,747,-1000,1000,408,-243,-636,-1000,670,416,-1000,51,619,1000,665,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.NullPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "newChildNodePointer(org.apache.commons.jxpath.ri.model.NodePointer,org.apache.commons.jxpath.ri.QName,java.lang.Object):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.BeanPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "newNodePointer(org.apache.commons.jxpath.ri.QName,java.lang.Object,java.util.Locale):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{-580,288,626,-863,886,511,-102,423,490,330,-664,62,-377,-843,734,-838,-472,-666,-138,-757,627,434,211,73,249,-308,-405,57,205,931,-776,231,-152,-329,20,-577,817,322,-660,473,457,279,-959,-472,95,-405,870,371,-204,-554,-477,-467,-999,75,-583,-537,541,-905,197,460,-83,79,313,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.jxpath.ri.model.beans.NullPointer", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "newNodePointer(org.apache.commons.jxpath.ri.QName,java.lang.Object,java.util.Locale):org.apache.commons.jxpath.ri.model.NodePointer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "printPointerChain():void",
            new int[]{34,529,-626,-671,-443,-613,833,403,892,891,-608,1000,887,-328,864,-467,-727,248,-796,-1000,-144,-261,-594,-187,261,-1000,-623,-199,-28,904,-321,1000,11,664,-399,401,1000,404,-88,539,872,-1000,537,-554,455,-564,-148,890,-544,-937,398,584,-216,-52,15,-1000,-706,-713,774,996,-263,-949,561,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "printPointerChain():void",
            new int[]{138,446,788,-900,-937,629,279,-188,1000,646,-1000,131,-355,-816,242,933,-875,443,547,815,-1000,-490,218,-942,-945,-910,-859,36,-759,-698,-760,-902,1000,794,4,1000,467,1000,-1000,-310,331,589,395,-975,-464,377,375,-179,-341,-153,-185,-791,-946,67,1000,-230,-655,-156,1000,593,32,674,1000,-596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "printPointerChain():void",
            new int[]{-129,1000,-1000,53,-1000,443,952,1000,-973,574,137,-533,1000,-365,87,366,-516,1000,424,-597,233,168,267,950,-410,-833,-394,680,679,84,-576,264,488,1000,-1000,-2,837,-301,-320,-968,-1000,721,-527,828,48,-969,106,-987,-572,542,-928,705,909,411,1,-456,431,242,592,623,1000,340,-1000,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "remove():void",
            new int[]{-153,-146,-811,-49,-10,323,1000,369,452,393,946,67,302,982,-151,151,816,903,388,146,-1000,-533,-1000,905,843,-627,-190,-292,630,700,496,80,417,493,52,-684,1000,-931,322,-625,23,928,-115,426,-455,211,-219,-518,-515,179,-84,206,46,658,203,-679,428,120,-880,-479,51,-444,141,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "remove():void",
            new int[]{-969,-943,-1000,1000,-182,882,553,1000,-992,-387,-270,451,1000,176,-38,-664,1000,-95,1000,1000,-67,-590,-428,613,698,-115,739,74,374,527,-940,-535,1000,-280,-1000,38,-556,-100,-90,-661,-1000,-369,-988,-29,9,1000,300,508,854,-639,286,1,853,96,263,-1000,1000,-152,-1,-1000,-1000,305,730,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("VOID|isAttribute=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setAttribute(boolean):void",
            new int[]{467,-172,153,504,984,-265,-144,256,993,-400,-162,561,-209,-1000,457,690,918,-170,400,-1000,797,-619,-400,-809,206,-72,112,-399,554,-285,-705,-400,127,578,184,-178,-369,1000,-68,1000,86,-432,400,97,-202,400,-532,191,-1000,316,-631,201,-672,-875,-147,-609,-846,-375,413,400,-1000,-706,-1000,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("VOID|isAttribute=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setAttribute(boolean):void",
            new int[]{735,187,-871,884,-431,148,-152,-185,964,-434,976,677,945,490,403,764,994,-709,636,-757,405,-404,-11,-779,-711,883,765,-79,209,-859,-358,785,275,781,328,357,-88,863,-977,-37,-479,-360,-802,-577,753,-645,-219,-745,-771,354,-30,-780,200,-399,556,390,-152,-61,-59,-575,722,615,792,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("VOID|getIndex=java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setIndex(int):void",
            new int[]{-669,1000,-289,476,463,1000,3,-499,525,251,1000,-624,272,-431,243,505,1000,1000,-646,1000,-534,-149,-592,291,739,-1000,253,2,-101,-58,1000,-768,211,623,-66,404,335,-686,1000,-1000,-1000,359,-977,-851,-748,1000,1000,-421,-1000,710,-205,871,-334,-331,1000,-78,1000,747,-156,656,462,-1000,704,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("VOID|getIndex=java.lang.Integer:LTk0OA==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setIndex(int):void",
            new int[]{170,932,-551,733,647,-365,585,-477,584,-139,-391,-639,-809,-704,-948,803,-154,565,-278,-200,-704,266,710,-880,-309,506,-43,640,207,277,-462,470,-881,770,-134,-125,-316,351,374,-332,-44,-679,940,355,-265,-169,299,680,-726,333,987,-577,51,850,-998,-692,-27,949,268,-289,-322,848,-725,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setNamespaceResolver(org.apache.commons.jxpath.ri.NamespaceResolver):void",
            new int[]{511,-20,-363,-542,-101,-290,60,-762,-77,-195,-812,-21,1000,282,1000,-553,851,1000,-176,493,-1000,21,-1000,-580,-1000,841,167,-974,-338,691,-1000,-549,-435,-306,-1000,-281,801,-312,828,604,-1000,696,1000,1000,1000,44,-692,50,636,-337,1000,217,-991,-1000,1000,1000,-1000,62,-1000,986,-54,539,1000,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setNamespaceResolver(org.apache.commons.jxpath.ri.NamespaceResolver):void",
            new int[]{-9,-317,-38,-450,-812,857,-998,-67,-207,607,855,278,-164,-105,-400,-87,-439,-459,-487,-407,922,673,990,-233,930,913,657,931,215,-685,-239,-432,-28,436,656,698,513,205,124,-501,-910,-736,645,-386,-677,-691,299,783,-372,640,951,-716,-648,604,586,-743,993,883,-764,-530,827,721,809,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setValue(java.lang.Object):void",
            new int[]{195,-1000,824,1000,-375,-739,372,-1000,-101,89,-1000,68,-1000,-201,7,-64,1000,-125,316,587,-724,1000,-956,-177,253,1000,-417,-390,-1000,-152,57,653,50,819,-862,730,352,-1000,-1000,986,-1000,149,753,-1000,-55,1000,238,-829,72,-564,-477,-392,-1000,-1000,-125,249,-1000,1000,-629,288,1000,1000,-574,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "setValue(java.lang.Object):void",
            new int[]{-909,79,335,-505,-907,523,-399,-914,905,491,248,-753,-521,596,-83,-983,-400,658,-948,676,375,-49,-21,-334,-487,-400,515,-542,-730,-733,-396,173,-309,-496,551,29,226,10,400,485,-387,429,615,0,599,-300,-28,-234,477,-698,-116,-796,100,252,-53,-771,218,514,644,-439,491,-400,-283,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{287,-510,711,-108,25,-1000,477,1000,-686,-138,516,-907,286,68,-1000,136,314,-1000,234,40,-1000,-50,358,33,-672,-109,-515,-526,-863,-1000,-980,1000,-407,322,119,-264,-1000,1000,1000,46,90,159,1000,1000,-75,39,-1,-1000,-615,-633,-668,932,799,698,-298,-862,-1000,1000,-995,-1000,620,1000,568,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-260,134,-369,-151,175,-278,-1000,-398,1000,363,-581,-114,-1000,-992,1000,417,-75,-144,-1000,-240,-51,-67,1000,-443,296,841,-656,-351,934,-1000,-1000,-807,1000,-624,-188,-628,-1000,-1000,-1000,0,-28,167,241,-1000,143,763,-1000,-177,1000,-71,883,1000,-541,-1000,233,-733,240,801,605,-407,-827,889,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.String:L0A=", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "toString():java.lang.String",
            new int[]{-58,-1000,-2,401,34,1000,-132,432,-65,79,-339,418,-1000,249,269,-840,804,312,-848,230,-395,423,-378,-859,46,-331,50,-979,1000,-1000,-269,313,-605,-243,911,-96,441,-1000,565,-345,-306,623,-874,650,-873,-790,-1000,-1000,-221,152,68,-150,-348,-161,-293,-66,326,-162,292,480,852,1000,-775,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.String:Lw==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "toString():java.lang.String",
            new int[]{711,-1000,-1000,-1000,-380,-327,-1000,531,1000,-1000,-653,1000,-101,923,-1000,-257,-1000,-863,1000,76,-763,-1000,1000,1000,1000,1000,-951,624,-1000,425,-120,-1000,976,-277,-513,1000,170,1000,1000,-1000,210,98,-1000,-200,-368,226,489,4,-1000,-1000,-1000,1000,-1000,-1000,1000,-169,538,221,-1000,-882,-1000,347,-1000,-945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.String:L0luZmluaXR5", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.NodePointer", "org.apache.commons.jxpath.ri.model.VariablePointer,org.apache.commons.jxpath.ri.model.beans.BeanPointer,org.apache.commons.jxpath.ri.model.beans.BeanPropertyPointer,org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "toString():java.lang.String",
            new int[]{-126,-693,-121,17,-585,-706,803,235,1000,-193,-491,-476,899,-558,-650,78,117,-555,373,-68,-1000,-513,1000,-414,-1000,1000,292,-197,-233,474,-1000,-290,-171,-1000,714,-834,-941,-908,94,393,94,176,-352,784,662,587,1000,919,428,198,25,-136,-1000,-573,709,1000,564,-299,223,400,132,-556,-76,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.VariablePointer", "org.apache.commons.jxpath.ri.model.VariablePointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{124,570,395,85,1000,790,951,-799,-1000,436,299,-1000,742,1000,-761,-719,24,148,-499,415,466,-683,-1000,144,-1000,-822,-814,520,1000,-282,92,568,918,595,-79,-1000,-634,516,-413,-112,68,-238,-774,-576,1000,-485,-276,-903,1000,-316,-987,-46,587,201,-1000,632,-354,260,-1000,990,1000,-1000,-686,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-427,1000,401,594,-784,304,-54,-367,-258,-570,0,152,180,-264,813,525,-298,544,1000,401,-297,418,-1000,-489,-36,-872,705,502,-52,291,577,-298,128,-314,292,-865,911,-787,546,0,612,-69,486,-617,-650,-97,-530,378,-129,-622,764,-398,852,393,-282,175,521,684,942,0,-870,-1000,-504,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "org.apache.commons.jxpath.ri.model.beans.CollectionPointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{226,-1000,-48,-246,-1000,1000,671,964,74,-988,-132,538,-1000,-212,406,-667,701,-781,-590,96,536,435,-798,-826,167,-969,792,955,-709,-98,-232,252,592,1000,-78,-449,-344,-959,71,463,751,818,-351,41,-833,-1000,1000,-569,751,-935,-1000,-476,-863,-5,-333,1000,1000,-375,-1000,441,-140,15,301,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.jxpath.ri.model.container.ContainerPointer", "org.apache.commons.jxpath.ri.model.container.ContainerPointer", "testNode(org.apache.commons.jxpath.ri.compiler.NodeTest):boolean",
            new int[]{-1000,-86,-258,-7,1000,-782,-279,124,-1000,661,1000,48,1000,357,964,763,-30,632,-793,108,-1000,-154,-463,-501,122,-470,-335,80,-455,96,161,-382,-581,-451,1000,-1000,-879,242,-1000,-621,1000,-632,-1000,77,-636,146,1000,299,-1000,239,572,-1000,-243,482,619,-2,-54,-260,329,-1000,-1000,718,208,318}));
    }
}
