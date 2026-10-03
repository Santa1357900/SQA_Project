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
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{338,100,-817,840,509,752,635,-372,-690,360,-488,53,103,605,312,1000,-100,-254,-16,973,912,-687,-834,652,-564,617,-84,-75,-746,737,931,366,167,212,-419,-105,63,114,-171,-93,-969,383,21,572,379,365,917,-840,197,328,-100,-680,64,-588,240,-12,896,943,565,-700,-478,330,-799,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTc5NS4xNTc=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{-875,795,-527,-843,-259,103,978,-557,-295,243,-692,-404,-726,-61,588,745,-595,605,-237,386,-287,-986,-422,193,653,-311,612,122,910,973,129,-924,429,-661,-63,183,-681,-86,-921,296,907,-580,219,699,-325,775,849,-761,-517,447,95,-939,514,341,816,-13,739,-564,936,-797,-680,725,-484,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.math.BigInteger:NDM2", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{260,-436,-396,-847,185,773,-437,-259,421,766,297,702,848,-519,946,57,502,-902,-38,407,-108,-932,-293,902,667,298,833,330,-664,477,405,-386,-927,52,-42,612,-720,642,-776,588,554,749,160,-37,990,-391,75,-946,300,673,135,-648,-812,-177,372,633,576,-271,381,669,842,943,397,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{774,-784,156,389,828,-703,602,65,958,444,765,818,-532,826,228,-418,393,906,-605,775,605,-355,435,-89,880,232,35,-509,644,-178,-509,-518,-55,712,-795,-547,-57,-445,-982,-376,687,778,965,-547,301,166,-962,-841,792,769,-712,-352,692,-289,-731,-361,-538,-721,166,-480,-812,759,252,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Float:LTE0MS4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{244,141,909,-654,33,696,409,-344,879,-305,-702,195,224,552,-761,15,-882,-68,-730,-221,-248,-836,848,-676,-380,366,-916,769,785,939,-324,389,518,627,549,135,-622,521,-135,814,716,-871,-275,-262,-472,865,-398,-43,621,-793,-610,52,95,151,750,923,337,-437,-575,990,886,589,134,-189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTcxMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{980,-710,-27,912,560,24,771,409,-459,-662,-869,-258,470,832,-782,-932,-825,-317,883,-92,5,172,-685,-420,-479,-495,320,950,125,-943,-876,731,326,-657,846,969,149,-734,627,-77,513,-534,755,84,325,-10,-941,747,517,-116,-620,247,261,851,958,732,-784,602,-781,184,806,191,-374,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Long:LTQwNA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{563,-404,349,-770,279,969,929,-768,-786,374,891,917,900,-589,-912,-662,-982,32,792,21,-589,-149,570,29,-823,-896,794,379,970,-255,618,-516,506,-335,-643,255,199,966,-567,-957,-168,841,-691,678,230,689,174,539,-15,-181,-485,-205,497,325,184,-253,789,-448,-911,-451,891,-74,-812,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFLTQ0NA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-58,1000,-826,-447,-347,313,190,1000,793,56,-780,416,-667,-1000,-721,-1000,676,-48,202,-348,-961,468,670,-713,-519,380,652,1000,-255,-1000,1000,-29,121,612,-212,-140,1000,217,-414,198,-1000,261,-297,1000,-47,1000,49,-223,-380,385,917,285,-1000,-1000,-562,982,-147,1000,-27,657,-209,-1000,-103,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Long:Nzk5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-167,799,-744,-248,-332,-608,932,291,93,-790,-844,-1000,-58,370,945,-585,-930,-792,456,-501,49,-91,199,-357,564,-832,-462,-543,-555,1000,1000,27,-214,366,142,1000,-1000,-986,394,-935,538,-944,738,24,-248,71,-175,193,586,855,-444,-1000,-914,-1000,116,-780,943,1000,191,651,-829,-1000,-58,-519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Float:MTAwMC40NzQ=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-747,1000,-1000,474,-312,1000,866,547,208,472,863,-75,1000,-272,-69,-206,554,179,-482,616,364,87,-122,767,1000,1000,603,-232,-920,-488,-668,-536,-229,809,-126,-870,218,-704,-1000,913,207,696,-398,196,421,1000,778,-187,-77,-1000,486,580,280,-460,334,392,1000,712,30,-757,-307,-301,297,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{79,1000,-15,-738,537,607,297,-400,601,-714,412,805,-90,400,-281,-797,-62,869,-441,-9,171,852,-100,395,-208,635,-174,-400,557,-1000,-275,-171,-617,-785,-205,-186,1000,247,-887,552,-1000,140,-687,277,-285,-97,225,-533,1000,385,259,15,-732,-1000,-25,756,442,978,-274,-238,245,-120,780,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:Njk5LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{233,699,1000,-81,512,899,497,1000,1000,-50,146,1000,134,-411,-1000,-626,-483,741,-1000,-978,-725,474,114,-158,1000,-927,-385,708,568,-568,819,317,-966,-1000,1000,-1000,165,1000,920,-850,-1000,-860,-960,135,-199,-742,-934,-1000,812,446,813,-1000,643,106,-939,-670,208,-333,-1000,716,-77,479,553,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Float:MTAwMC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-327,1000,-1000,661,419,-360,-365,-1000,-1000,-100,703,-145,-176,1000,1000,1000,-145,-17,60,1000,-1000,-452,-551,582,242,1000,-343,-1000,75,1000,-822,-1000,-107,-178,-990,767,494,-349,-1000,987,-323,150,443,-316,546,-567,-489,380,-1000,1000,-59,308,607,434,1000,-251,389,-612,129,-1000,110,564,262,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wRTU2", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-218,1000,1000,53,11,-1000,-1000,1000,-1000,-404,823,-453,-1000,1000,894,1000,322,-804,-1000,1000,-535,-1000,-1000,360,1000,1000,102,-1000,-248,1000,-1000,-1000,840,175,-1000,1000,-231,-1000,-1000,1000,-99,405,1000,-654,-207,-340,623,823,-283,1000,-993,-811,445,-155,1000,-1000,451,-792,482,-1000,1000,580,1000,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{443,1000,400,67,1000,742,632,400,124,-797,111,1000,-507,12,-400,1000,-847,889,-1000,-546,-1000,580,124,-10,553,-349,-1000,196,747,322,-239,-78,-1000,-1000,410,-633,842,1000,-63,-308,-1000,-1000,-929,-66,-643,-1000,-1000,-1000,44,902,1000,-1000,722,1000,-357,-212,-165,-1000,-1000,201,-516,571,-92,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDkx", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-460,-491,-60,-75,873,492,-752,299,634,-975,-546,-35,-97,692,-757,936,-616,-520,-199,-70,-960,160,-170,-816,727,155,934,-13,-479,-467,579,94,-721,-240,589,-673,152,653,-800,607,181,532,37,434,-56,-955,96,-614,238,-690,782,-389,256,106,-634,466,-626,-329,852,-511,-292,339,172,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{238,77,-1000,-681,-115,-349,-764,-903,-108,16,85,-67,-116,-761,-249,-585,28,-35,1000,612,-367,561,-600,444,948,-126,947,-387,-608,-1000,882,-779,643,1000,288,1000,421,-531,-558,1000,309,1000,-374,578,-479,894,-1000,-493,-254,977,-91,841,88,-1000,381,-915,475,346,769,-827,-283,410,-1000,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-995,1000,-1000,-81,-418,23,497,1000,-513,-50,-1000,1000,-656,159,1000,-777,-483,-652,-172,-978,746,-622,1000,-1000,372,-426,-385,170,-321,839,819,926,-966,946,-639,1000,-1000,-1000,7,-1000,1000,-1000,482,132,373,973,-934,500,-337,917,-706,-1000,-1000,-732,426,-1000,1000,-333,-399,1000,-1000,-1000,-409,-799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-179,-491,818,133,947,-124,-752,-1000,-212,-1000,1000,13,487,690,535,1000,-55,115,-164,-12,-960,725,-1000,1000,700,155,-88,-798,483,540,-171,-649,-721,-81,589,-369,-188,-360,-728,59,-1000,380,-1000,974,-126,-942,96,-233,-539,-429,-1000,-374,1000,537,565,-241,139,-271,57,-1000,718,477,-7,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{958,-267,-147,-248,-332,-494,900,-634,27,740,453,785,598,31,-991,-739,947,-2,456,-501,49,515,-6,178,564,305,130,-967,-409,715,-629,-687,-638,445,-39,582,469,-167,110,757,-308,-143,480,62,278,471,410,-875,586,-910,346,841,-562,-897,-754,826,-785,129,-658,277,763,411,-398,-85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-179,618,399,-531,435,817,669,-1000,9,535,-489,-619,1000,-371,78,110,-980,-173,-19,-729,-163,-236,720,-610,-695,-837,-1000,352,483,425,-400,-333,-67,-1000,589,-1000,-1000,701,591,-1000,-1000,-1000,106,974,177,-1000,-1000,247,681,-298,-1000,-400,1000,400,-1000,897,-711,-538,-1000,-1000,-502,-222,-7,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-1000,1000,-215,1000,690,888,-27,-238,-6,-861,-412,-1000,743,1000,421,-752,-491,-1000,-294,589,693,54,150,1000,851,-948,1000,-12,201,-863,1000,1000,-517,427,-882,-970,-110,-355,-341,-8,1000,418,218,1000,1000,879,328,1000,-401,955,938,-839,1000,-17,1000,-1000,1000,1000,1000,84,-1000,-1000,756,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-479,-793,-563,263,-83,-653,-209,24,-936,-798,-402,531,-965,-738,-213,342,-382,-791,499,-70,309,953,-14,118,-434,-592,-196,-597,821,-4,298,670,623,-189,-822,891,417,189,-9,375,-70,7,694,900,910,-511,-795,606,-941,-514,801,-62,-608,-738,-457,-890,-34,666,520,-429,963,227,-346,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{72,-200,-748,523,-557,673,275,-785,-876,804,-212,-896,862,-793,329,-270,872,649,-237,-859,897,-470,581,85,630,876,-304,474,661,-74,-453,-370,261,398,473,-260,45,-816,-887,-124,-163,-18,-148,-29,885,143,265,221,-976,-105,899,-489,-688,-974,776,693,-773,-923,539,778,594,-819,-703,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{644,464,-1000,1000,-1000,406,797,-302,-762,263,-205,-247,1000,-284,836,622,-350,810,-421,-555,628,307,413,-696,991,406,-368,-292,397,183,574,466,1000,739,-187,67,-88,-916,51,-346,353,-153,295,354,439,-52,468,666,-1000,637,673,66,459,873,1000,1000,-116,-804,-169,-358,910,-1000,-1000,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-618,85,-216,-714,-345,-53,1000,-65,-82,-742,-244,-1000,-1000,578,-130,-1000,1000,844,797,25,328,1000,-1000,152,-527,258,377,-776,725,-771,-978,-518,899,210,495,272,-472,-462,1000,246,710,104,1000,-951,752,673,-334,370,888,4,1000,-802,619,-698,1000,1000,1000,499,1000,313,-891,1000,-1000,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-233,1000,-783,-778,-606,-1000,-343,326,1000,-365,496,834,-1000,-726,-1000,-1000,1000,-915,1000,-1000,899,-562,-814,1000,464,-742,1000,791,1000,-1000,-440,1000,688,-1000,-55,583,-1000,-596,-1000,1000,302,1000,1000,-866,-851,1000,1000,51,-905,-1000,977,-857,-997,-575,-1000,-766,-1000,83,-595,1000,-818,-816,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{25,-1000,-132,-19,903,-1000,125,-548,-441,297,-962,-747,-1000,427,-1000,-1000,592,-764,1000,-954,1000,-1000,-1000,960,-703,-964,-400,574,-192,-1000,668,-369,673,-400,-55,-321,-677,-175,-1000,933,302,522,1000,-1000,-628,1000,817,51,-360,-1000,1000,-1000,243,106,-171,486,668,350,529,-543,-818,-57,-1000,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{786,-816,202,80,-179,-916,-113,158,406,167,297,331,591,-577,813,161,-271,765,-809,-37,838,-748,819,168,231,-532,-619,885,-174,784,-19,113,271,249,719,-481,699,-749,-509,-550,-719,767,-669,-34,8,-5,19,65,-833,512,-142,982,-184,657,-440,-531,257,833,-36,-673,956,-368,987,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-727,-91,-767,-569,-508,-283,1000,274,925,-1000,114,-1000,-372,-174,-154,-788,1000,-143,-80,-851,-28,466,626,876,461,-609,1000,1000,833,-849,9,-816,194,613,-42,-1000,-1000,-1000,1000,896,-100,-199,-94,609,1000,476,-734,530,-449,-67,1000,-1000,1000,-102,809,-863,1000,-39,255,34,114,1000,-499,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{788,-564,86,-210,-76,-856,-18,-801,-707,-742,-244,-539,394,578,109,-746,550,866,149,-270,328,779,-703,191,-527,546,-923,-570,-500,113,-858,-298,899,562,-203,-204,-472,964,-929,-602,145,510,-52,-951,-696,766,746,430,840,-810,853,355,-945,690,-991,942,-637,906,169,-272,-367,-835,-395,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{217,-1000,1000,1000,-1000,727,314,873,42,1000,779,1000,879,871,-551,144,-828,1000,1000,-712,860,-1000,471,-1000,95,1000,-1000,-289,1000,419,-214,645,577,872,-1000,343,-148,1000,-1000,-1000,-1000,649,26,187,-495,-284,1000,474,208,747,-991,720,-751,-263,216,-768,-1000,-543,1000,347,40,814,1000,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-295,-1000,301,402,625,-221,379,-5,232,-1000,-525,99,-670,1000,-857,-912,1000,104,-510,-879,240,281,-384,643,-357,-462,-412,-505,608,-303,294,-987,758,315,380,433,-626,1000,-1000,246,629,-664,483,-706,-437,766,-611,445,-301,-380,872,-655,703,-354,1,5,7,-497,-327,-93,-541,769,-582,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{25,1000,-1000,-741,-1000,-96,125,799,1000,-1000,463,-747,-851,-1000,-630,-987,1000,-628,-1000,-906,-100,-1000,-65,876,-394,-392,1000,927,1000,-1000,256,858,1000,-427,267,-363,-921,-1000,1000,886,397,836,1000,165,887,1000,-1000,1000,-454,-1000,570,-161,-180,417,-1000,-427,668,-317,-1000,1000,1000,-377,-433,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{25,-527,881,788,848,135,733,475,460,1000,463,-208,-851,-399,-1000,-987,645,1000,661,562,341,-938,-643,-1000,342,-392,-751,407,-665,819,-367,-37,959,-28,-881,732,-1000,-526,266,-370,-507,629,-914,-261,-808,665,-613,481,-1000,609,906,727,-144,417,931,-92,-136,-125,1000,109,84,1000,-433,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-155,-91,-475,-868,-132,852,-302,279,327,652,484,-180,-447,-174,846,695,234,74,-80,-851,-880,-905,-958,-880,461,944,203,-373,411,246,-700,721,-601,-380,531,981,121,36,52,-658,148,-479,549,133,451,603,-201,565,128,-771,-529,683,-492,-551,-565,751,436,-842,-333,447,74,384,472,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-417,-857,208,529,1000,-115,466,500,504,203,-719,87,-1000,587,-1000,-470,-462,-74,277,595,-694,-115,-398,-369,-243,-187,-236,-496,1000,-26,873,640,256,318,585,1000,-1000,1000,-967,-414,1000,-990,1000,-523,-578,890,-763,335,-547,-893,774,-177,169,-67,316,-128,-254,-1000,429,-80,-21,1000,235,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Byte:MTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{125,-1000,-719,391,119,730,-189,1000,810,-95,898,-865,-931,1000,-570,316,609,529,480,587,-141,-925,-1000,-698,-460,376,795,-444,-238,73,-586,439,-139,-1000,523,-883,984,-507,-76,718,147,76,236,-316,688,-296,-1000,563,788,-141,703,95,529,871,-1000,-832,798,542,213,-326,-738,-50,-281,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Byte:OTg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{-171,783,611,-926,-781,-697,488,419,389,-105,-228,419,340,-7,211,-408,899,232,403,40,-423,706,525,-137,-911,464,794,-329,952,969,-136,996,805,-323,-835,648,924,-576,-501,348,773,876,-840,-650,-604,953,288,-833,-97,-155,197,17,441,973,-966,151,-400,137,270,-169,-568,-78,238,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{497,-209,-771,-114,-69,621,371,-770,753,-858,364,-22,52,-602,319,372,-348,-838,584,-96,-422,-971,279,-526,-846,572,-177,415,137,-812,683,-612,-952,-212,-116,455,285,59,759,997,-487,680,-138,586,337,559,-979,-631,335,-50,-909,-890,-862,-882,131,-376,-538,-872,-556,16,-291,276,213,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{-325,11,569,482,-347,390,41,-511,608,-1000,439,1000,-1000,-1000,-403,-760,-844,629,262,1000,-268,-934,1000,602,54,-226,377,-230,-274,758,-494,829,-1000,35,-770,871,922,-1000,-1000,-223,1000,1000,976,541,-485,-1000,834,-1000,-892,3,832,-81,-864,839,122,213,-41,-1000,832,1000,89,-452,-470,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{1000,-391,593,-421,-64,-367,-1000,-682,466,260,-141,-601,603,42,-23,-416,753,966,927,-329,-880,633,-401,-427,305,-545,279,-278,363,629,949,-719,516,559,-74,-381,519,-864,555,-3,1000,336,-1000,-1000,478,275,-1000,-546,-423,-246,259,1000,796,1000,1000,1000,1000,584,949,-16,321,200,-594,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{682,-673,950,1000,-861,-1000,374,-662,907,1000,379,288,776,-1000,-1000,667,-216,-1000,911,698,837,711,-136,276,-724,-1000,1000,1000,-690,268,163,-1000,-367,540,-963,-513,-141,378,-656,-870,1000,151,346,-1000,233,-222,-1000,-759,-1000,1000,901,349,1000,-932,388,485,1000,364,-1000,325,1000,-792,800,-615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:NzU=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{-576,-44,-708,313,750,-290,-942,-440,823,363,272,-6,994,-263,-578,474,-540,-206,-319,297,548,497,-6,-501,833,341,-942,-735,-178,-445,-729,-716,-543,-468,-885,-386,-592,530,-927,-813,253,854,217,-498,34,795,-102,219,-621,-526,-425,-607,419,359,-236,902,-940,-251,340,-731,825,-218,957,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{615,281,521,-824,154,-550,932,735,-926,-954,757,-530,223,722,457,-484,-861,-643,767,-659,818,733,-177,-575,12,376,108,203,605,377,-966,-39,-88,131,-973,492,57,567,81,-5,977,-332,-62,-17,73,-117,273,-242,23,-663,-427,385,874,706,748,50,-845,42,-49,192,-537,909,552,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{-34,424,-893,228,674,-731,-658,-91,-601,-707,164,-930,-670,-564,846,-442,738,108,640,-503,-991,360,-599,-609,800,-2,76,178,-978,-844,-923,660,461,-820,-85,-495,-123,-488,565,-760,-806,-762,10,59,-24,-596,148,77,-157,818,-152,-260,667,123,289,-500,465,153,949,-416,-273,-720,-410,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{381,110,474,36,352,500,-678,-198,869,833,-883,74,70,-647,-362,698,995,-477,-98,-582,6,432,-776,-764,469,-435,60,-851,204,668,-690,472,176,-351,-730,-70,493,-277,539,765,-104,-673,843,200,601,750,-61,-210,-374,-150,-427,417,540,-244,792,-15,-56,56,820,-759,942,-431,200,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Short:MTAw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{597,476,-1000,-752,1000,370,883,-839,137,-282,1000,885,248,-1000,-641,15,257,-394,-469,-765,636,-354,-1000,-487,789,698,-887,-137,915,-154,-399,220,-497,-282,-329,897,-95,-440,-252,-723,-525,301,-390,390,1000,-601,-409,117,469,-280,-1000,-1000,218,-729,-3,-1000,-1000,1000,353,491,488,580,639,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Short:OTIw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{-884,347,333,-240,-609,920,-229,96,455,552,713,326,224,-336,-686,388,-818,-504,743,544,149,-23,90,-741,479,-255,887,-221,708,204,190,-411,-536,-861,167,-66,-813,338,987,-63,629,-98,401,-501,423,-493,-118,-888,-887,-272,-552,-977,939,714,-653,740,-589,-968,679,796,250,253,747,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{-114,445,177,-966,-838,-454,775,-502,-936,-250,-928,971,-917,-661,-820,128,-398,939,-622,119,530,961,782,730,-499,225,-814,105,-529,-542,714,-774,888,-767,-70,-877,738,22,-180,814,-293,541,678,-623,-519,-680,-669,196,748,-498,-608,-540,885,323,-770,270,479,388,-751,337,-136,-92,-317,-805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{478,-830,-111,458,799,949,-86,553,-844,-222,434,-823,-912,448,620,536,-56,477,-846,-61,560,-474,-274,-535,-207,505,-704,-602,-79,308,540,795,-110,-216,-597,18,-109,232,679,520,791,-514,-836,740,-395,496,-941,-446,-661,-830,55,430,340,253,766,140,557,-25,-916,-286,10,319,378,153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{53,-572,766,-124,-218,104,572,-572,-115,-765,285,-838,402,-158,-941,869,-834,729,-797,-37,-394,-12,92,606,597,-625,959,-741,846,-754,-93,309,37,-112,-398,-755,61,-961,-400,-572,-42,-893,-978,-64,-48,-761,-623,178,895,698,-261,41,-225,-729,-801,140,-439,-602,528,-543,566,-447,341,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{-997,-848,-484,-544,204,797,-199,968,-532,568,-893,-439,276,149,348,581,-437,268,187,241,509,553,-439,710,-212,536,586,742,-552,667,-128,-304,-647,388,-595,577,-841,201,-368,363,-810,491,-857,-736,-758,-628,-551,811,-668,-728,-252,-293,746,-896,-719,217,-675,-47,-99,566,-123,950,843,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{77,-500,-23,422,396,114,213,439,-646,-5,317,298,-125,201,-75,882,626,215,809,833,-686,-78,-130,1000,571,408,-678,-151,314,-729,867,-754,537,-495,-489,-523,651,-992,598,-628,-318,284,633,-27,-85,-977,498,282,883,-122,790,-643,-989,8,-813,241,684,-301,961,47,992,773,85,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{1000,-300,-480,717,-183,128,481,204,583,712,1000,-219,-222,1000,-1000,-1000,-281,411,1000,831,-1000,-62,-1000,318,-478,603,236,-237,-616,1000,-726,1000,1000,-1000,-1000,-323,-1000,-1000,1000,-1000,-1000,-1000,-1000,1000,187,-303,-717,-1000,1000,-76,-1000,-1000,-1000,-1000,-961,800,1000,-1000,-1000,-901,1000,-236,89,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{461,394,713,529,679,185,-504,865,-212,-289,863,63,-239,102,380,27,-787,896,-791,887,-829,841,54,874,-18,-141,-714,-3,140,438,228,-827,-540,976,64,929,996,711,-233,30,366,-204,574,494,-252,793,68,-703,707,-586,-294,801,-793,687,-237,-558,-276,-129,-901,-480,-813,-529,-108,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{-451,642,-161,-317,772,807,407,-908,-454,363,-167,927,295,-147,-939,-246,-414,240,-392,677,-269,-887,549,728,190,-80,610,-730,645,703,321,783,-4,490,251,-906,-126,-301,-464,-638,-252,-293,153,942,-775,75,687,623,-305,-162,687,-298,151,-284,-838,-181,678,-822,212,-434,-76,-169,-619,513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{169,143,-997,-176,-290,630,-206,-842,809,-431,34,-757,297,-947,351,494,499,246,208,325,-578,-957,295,-434,361,-366,-49,690,-709,442,-689,18,339,-806,620,389,-338,725,937,656,141,531,-677,-186,-576,-950,-476,553,80,184,162,-386,882,678,473,438,559,625,762,-710,-235,-419,906,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{-571,-942,-144,830,837,-964,-719,825,-45,-124,-287,621,798,-16,-753,-860,557,-122,834,723,76,-760,-123,-3,368,556,-87,430,848,142,963,-125,-143,-392,-593,362,190,892,837,101,-125,-125,222,-799,-766,-718,943,972,-614,-900,-206,-162,775,42,854,875,-940,742,-685,758,-241,302,792,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Short:LTc1NA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{290,1000,-200,-1000,-754,335,400,-1000,-666,-515,208,391,-705,-202,996,382,831,-1000,1000,884,226,-219,1000,451,-4,-285,1000,-1000,869,765,526,-596,1000,-177,604,-156,139,313,787,-132,751,1000,50,-79,558,-832,-603,301,-113,-1000,-264,171,451,437,-148,-680,-35,-355,420,-993,1000,1000,63,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Short:LTEwMQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{35,816,-790,-157,-310,-101,107,732,-313,-67,-959,616,-216,-620,-562,781,-907,579,887,-527,-553,948,828,164,807,856,-684,189,-156,-817,113,84,-262,30,941,-678,29,832,152,823,-101,-791,298,605,684,-707,-866,263,-430,-31,-585,562,469,-482,560,386,-116,-536,-327,505,-800,-907,763,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{540,-655,284,-664,705,136,873,-588,-802,-269,599,134,711,285,93,-9,-705,970,-298,-506,240,-780,-610,705,-777,241,208,-279,961,956,511,555,-539,129,-856,-179,635,-617,600,-955,-472,46,-166,994,678,321,824,445,-739,151,-737,51,556,-667,-198,559,30,-88,189,-455,-153,714,311,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Byte:ODc=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{-269,87,-1000,702,-528,646,-205,366,496,-254,-402,-1000,237,-584,-539,1000,-749,-768,-235,397,329,394,320,445,151,-1000,-144,608,-1000,-1000,-69,1000,537,-334,1000,1000,-1000,1000,-1000,833,893,60,-1000,8,1000,-482,369,-177,-110,-175,1000,-304,-223,398,1000,520,-1000,-231,-252,1000,503,-1000,671,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Byte:NTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{929,-507,-719,-973,-613,50,-279,-134,-75,923,168,11,-535,203,731,124,-644,-21,-413,891,134,595,-31,997,-842,-104,232,-837,51,619,355,-124,-845,593,679,372,-666,-688,911,742,-642,623,-438,-392,-149,742,-211,702,290,-338,-612,916,-63,82,128,-94,725,-534,988,-840,108,-448,962,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Byte:MzU=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{67,35,549,1000,-1000,982,196,-1000,-745,517,-1000,1000,732,-861,789,1000,-1000,956,-1000,417,-955,-1000,213,-1000,331,1000,1000,832,937,899,-404,-1000,877,-217,153,1000,-1000,640,-643,1000,-1000,-265,1000,515,-618,-72,-1000,-927,453,1000,753,-221,-334,898,-765,1000,1000,1000,-295,-1000,-1000,-671,-562,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{-857,-585,87,-79,174,216,688,36,-899,733,-770,170,-995,8,129,950,-716,941,380,16,734,688,-637,-373,-899,46,721,528,260,26,-628,-874,-388,-833,600,959,-979,273,274,286,703,176,-261,-827,793,939,-300,-93,57,-578,470,-211,-825,582,-589,-622,-679,19,51,145,-574,-666,-560,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:LTM0MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{-605,-340,594,679,-184,375,-433,706,-738,-556,-358,826,143,-894,388,905,-753,-310,945,42,-391,-775,793,712,158,-784,-547,50,690,-548,-873,490,184,-942,-908,907,175,-892,-171,-783,-4,99,336,5,-953,-702,-170,141,939,603,-860,-405,-648,901,-484,-302,-248,109,-757,935,737,-879,-475,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:Mjc0LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{-899,-5,-825,352,-253,-739,274,-949,-157,322,-677,238,-346,-916,29,239,-205,-249,891,675,-544,-285,-601,-941,-493,824,-98,626,-176,-479,-843,-896,-604,719,-275,-336,-709,-552,804,565,419,278,825,-807,137,-689,210,-901,255,-843,34,-557,-262,-303,-949,769,509,692,-499,-516,147,318,-552,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:LTU3NS4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{996,575,1,-36,-788,169,-276,366,-361,-42,-251,809,-986,-659,-299,-874,-983,66,394,606,-94,849,431,-231,-899,-56,358,908,961,-690,-197,756,227,-584,176,-832,103,-157,-87,2,831,-122,-994,542,-292,-975,626,-264,-1000,12,-25,-708,-20,262,248,-756,-638,26,518,990,876,431,706,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-162,125,901,-998,-53,712,-463,-380,-289,-444,461,340,-452,-332,-479,818,457,314,-786,43,-173,-544,-893,115,-692,376,-269,-644,953,-688,604,-594,712,-370,-926,946,-11,-84,460,-939,497,667,-375,-117,893,789,-434,-864,-194,996,-154,463,785,372,789,-982,403,854,213,-310,-677,225,224,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Float:NTg1LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{202,585,-628,-256,529,647,298,-158,378,86,209,-739,987,180,-86,216,-915,988,-445,160,-411,-61,-853,721,811,976,764,814,-669,-472,964,3,542,-744,76,224,534,411,762,293,-541,916,-55,286,-173,-189,-403,-145,-728,598,-826,-212,245,-813,-463,-261,815,-367,-906,936,-674,693,-405,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Float:NzU1LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{337,759,605,755,-553,-671,113,92,-663,-734,247,141,-660,528,-776,-157,-698,-507,525,-285,-480,-338,957,135,105,-371,-148,63,890,709,389,-22,-179,-886,-671,-926,142,166,-620,149,634,673,882,12,669,290,524,761,879,-534,417,-213,922,404,977,19,-58,356,-166,550,-853,-484,224,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Float:LTk4My4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{-701,-983,-911,864,-362,654,948,521,813,428,-523,601,909,-614,-462,520,286,787,-620,418,-515,483,113,300,485,-436,540,149,-520,-773,372,-650,200,327,183,31,-864,648,396,312,-14,-415,33,-137,157,637,-969,-323,-815,851,-528,474,-404,-768,-714,-181,914,970,675,654,-756,-647,786,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{-196,770,-316,877,87,979,-429,364,-256,-867,916,502,-364,-372,-366,429,-1000,-233,185,616,-115,-450,-820,617,584,986,815,308,123,-423,519,319,202,-481,155,-940,-903,-743,-65,842,-8,-455,767,-66,-18,-99,227,-904,-477,-548,-479,116,-462,565,-117,176,542,359,691,-488,-813,243,248,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzgy", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{-828,382,-368,-903,-340,476,-528,-426,-157,622,134,576,-384,738,-378,-968,547,-28,282,-251,-193,-346,-736,-524,307,-699,474,-31,984,-746,265,598,-503,322,85,811,-270,-25,-890,-193,440,94,470,-855,-837,-949,121,-306,-966,993,-389,-659,-400,-417,-936,142,-131,199,525,-734,873,-426,506,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjI1", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{394,-883,798,625,455,819,-171,911,-345,-451,599,907,-838,350,242,-615,-213,992,257,828,-64,-552,973,914,-144,-860,-139,545,-172,239,-936,619,779,-859,-638,-163,-803,-924,-727,-51,212,740,953,-931,-780,838,-865,-33,-440,451,-509,541,-643,269,463,100,927,-93,-121,-481,864,-257,484,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTg2Mw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{-188,863,365,535,724,-54,-876,-910,675,-533,-889,-294,-237,-531,-267,-124,481,-931,444,292,393,138,-148,822,-882,557,-351,940,428,786,371,722,91,245,887,-404,500,-123,-55,-840,100,208,107,-383,-835,159,-16,608,-441,972,-215,594,786,-328,-104,293,-144,-309,467,-846,260,502,-227,-634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{820,-571,299,524,816,-564,-474,-251,-651,-529,638,-564,4,-878,623,633,-689,-718,-100,482,891,384,1,-834,687,-965,-551,-892,-134,-570,-938,-117,-843,588,-909,184,96,98,-628,155,932,-194,-25,-588,-197,679,-290,158,-816,924,-708,932,-401,-596,964,748,-893,-685,962,-357,178,-783,-710,-23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Long:LTgwMg==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{820,-802,85,-637,-940,-894,50,-528,40,-118,663,-246,635,20,431,-222,-639,-61,545,-998,-496,148,252,-266,-632,52,104,156,318,-324,-68,213,-538,148,537,71,559,-499,-119,-221,-19,60,-161,-181,444,485,482,-730,-400,-584,-726,-502,-427,565,592,453,-949,-881,519,-65,-373,330,-993,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{645,263,-519,-274,90,-175,-125,666,700,898,-718,-635,-905,931,-58,368,-169,-916,-960,-456,-428,-30,-640,548,357,403,463,338,-611,428,-9,-826,-717,996,-310,-533,624,-106,245,-182,337,788,-867,-357,426,-426,366,-111,383,-388,198,526,784,903,673,-276,-587,-258,-790,931,655,-990,-634,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Long:ODYw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{-205,860,-646,-600,398,-698,943,873,999,-65,-38,-213,948,-96,795,617,467,-942,948,994,694,-754,80,355,-151,700,736,465,-514,518,-599,-462,276,441,487,-402,-267,-720,403,-299,888,-350,-64,-302,-973,972,828,328,-704,-869,687,-403,-756,-422,-893,642,-823,-749,-743,583,-437,-791,-925,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{-94,-482,847,955,-285,-682,-150,691,-841,-622,433,165,-538,383,-299,935,884,-753,759,100,-573,-327,455,-37,767,146,437,-850,-91,771,449,600,-993,-418,55,765,883,60,594,68,356,466,-958,-761,-759,275,-303,-520,29,-447,94,569,-323,-621,438,34,675,-383,948,254,-357,225,-985,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Short:ODUx", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{-365,851,-847,239,-635,217,744,-767,129,-958,-867,-331,616,3,638,422,-658,-673,551,671,954,980,379,849,638,-374,838,797,-988,-666,870,-381,740,-723,-779,-317,286,-253,-290,-86,-260,-336,233,-928,428,508,740,83,160,-954,953,-383,-618,619,438,-877,259,-183,-205,308,767,386,173,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Short:LTQz", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{-55,-567,317,179,-434,-194,-574,-514,855,440,-197,610,23,64,119,811,-744,-930,-923,-343,-955,-117,693,-349,-528,386,-394,565,-519,950,-251,-129,985,-870,133,-993,19,-349,334,-465,112,978,-209,708,-466,-549,-969,420,143,-273,-446,-922,-807,137,232,548,641,-591,-145,-15,-752,-804,-328,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Short:ODY3", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{-908,867,680,-693,-32,-929,-210,-312,610,764,965,-613,659,563,-7,86,-123,-431,611,-971,-790,-856,-618,-223,32,-503,944,172,-889,-590,20,220,-312,-66,-62,861,-598,-526,-688,14,-216,342,-989,661,-527,-487,561,-764,458,-845,740,-852,-815,-739,-722,-146,646,-325,-719,592,-757,-310,-692,-397}));
    }
}
