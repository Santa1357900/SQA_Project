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
            new int[]{-398,-13,-625,-1000,0,-616,535,0,1000,-238,-511,263,1000,636,126,-775,-306,669,33,-1000,775,-1000,876,-1000,-1000,0,321,-378,1000,-164,452,0,418,0,1000,363,-725,0,-570,90,-322,-1000,747,710,166,0,-993,1000,1000,-228,1000,-331,-235,0,-814,364,251,153,-375,-469,-818,-30,-679,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MzA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{-657,710,-478,-823,-849,-568,654,775,-813,-102,-604,948,743,-245,164,540,-863,794,-18,746,28,797,-952,927,473,458,-909,533,-605,646,11,529,-253,644,66,527,-944,665,-571,881,273,-25,-655,431,896,-779,-718,272,-828,208,515,-39,-127,287,376,921,594,-231,-937,427,835,-678,318,549}));
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
        org.junit.Assert.assertEquals("java.math.BigInteger:NjYw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{740,660,-806,-817,506,117,-113,993,-652,654,-477,53,-15,-969,652,-423,-554,995,926,312,390,651,-574,133,231,36,364,695,-61,205,715,-139,690,79,-308,-928,-471,-340,-782,-502,-911,-198,-480,-146,-943,-311,818,104,581,-620,707,911,-923,372,-731,679,13,-21,235,279,162,-484,561,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Double:ODQ2LjYyNA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{-459,-846,-404,624,137,786,-382,-907,-632,319,279,656,691,493,422,563,-287,-752,-841,-41,851,394,233,586,178,313,-902,-803,-515,-412,-995,-434,-625,884,-990,540,281,-382,857,-999,-288,15,955,737,-268,-16,336,305,714,-72,-648,734,-263,-933,-817,-420,661,158,719,-707,-149,-421,-714,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Float:LTY0Ny4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{-925,-647,-674,444,-578,-36,-199,71,-330,-633,710,-730,-914,903,879,-545,631,789,568,-973,281,-709,-836,852,798,-524,-147,386,947,-450,-418,-43,-250,-572,-593,857,937,338,-759,-373,-546,65,-28,194,-392,-296,913,-621,-748,697,-341,-316,239,-633,545,250,325,146,-886,406,-314,288,-722,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDI0", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{-252,-424,-276,336,115,-258,986,-881,-881,-779,-659,500,-51,870,-252,-322,910,-577,-864,156,273,-708,-317,645,-174,-165,-333,223,14,286,447,-316,332,626,-921,-503,254,14,815,-448,392,-898,324,976,805,540,846,-324,-585,-719,694,-984,360,938,524,-984,-251,333,627,-152,800,-329,-892,-1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Long:MzM=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{-765,33,193,-770,-1000,-735,-508,324,633,-76,-780,-1000,-751,-813,-831,1000,14,-205,4,-486,257,1000,-898,-845,-261,713,966,-252,12,-736,-196,154,139,-624,376,-743,803,404,-1000,1000,734,-1000,-114,285,-509,789,712,-302,-221,-501,-24,-810,227,21,230,-1000,327,-999,-399,-86,-985,-474,353,149}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFLTkzMw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-666,-1000,-1000,-936,-1000,-142,-301,1000,192,820,-28,299,1000,627,460,1000,1000,-430,587,-1000,421,-1000,533,-261,-988,749,944,1000,-1000,-1000,841,-78,-360,-488,-1000,206,-1000,757,95,1000,1000,143,-1000,1000,-613,775,1000,749,161,713,-1000,484,239,325,-346,-905,-1000,-1000,-808,78,-539,-1000,1000,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Long:NjYw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{569,-660,-636,-908,-963,906,417,728,494,413,-624,22,350,243,440,980,881,-968,236,-790,-59,-699,892,-126,-983,-63,954,649,-956,-749,495,-116,107,-827,-571,106,-785,826,-85,875,620,171,-966,627,-151,-250,450,569,-448,608,-370,970,557,-572,414,-744,-887,-365,-830,204,-812,-262,628,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Float:MTAwMC45Mg==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{357,-1000,-524,-908,-97,83,258,873,1000,1000,-293,909,-167,-217,-745,-456,1000,423,-673,-790,576,1000,892,96,-750,212,807,180,-1000,-757,495,-73,504,-861,-42,-334,-783,-1000,21,-410,467,565,-256,1000,-88,-283,400,-744,-104,564,-392,-953,-603,-312,41,-197,-1000,-1000,-51,-1000,493,102,840,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-51,-763,-167,172,968,-54,-142,-431,839,-358,837,-21,559,208,217,-248,-591,805,-949,667,-259,-908,579,-889,-917,-127,743,336,-27,-383,477,171,608,-862,-776,-396,462,-631,62,421,312,680,-925,766,-187,450,-855,-80,-300,714,-765,-166,-41,-369,379,-925,-66,-754,-873,670,-807,-460,-18,-929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:My4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{905,-3,476,932,-710,-598,-597,-501,-671,-180,724,946,-681,106,543,-999,-143,-998,-372,128,-390,-789,8,-352,285,-244,-273,-114,-639,752,755,-588,-106,547,727,-538,-299,-110,46,420,-415,-773,204,-70,-100,-460,660,582,735,246,545,-552,-752,-433,-396,-236,301,-449,292,970,-598,-588,-242,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wRTI3Ng==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{902,1000,1000,273,103,-944,1000,640,1000,1000,348,-352,-928,-1000,-1000,-1000,-498,268,-496,960,-615,-1000,-1000,1000,1000,-284,596,-216,662,-82,-849,-254,808,1000,-607,78,-306,-603,939,-677,-958,811,548,-232,-165,904,-1000,-323,-882,-1000,-224,-408,-638,511,226,1000,216,1000,980,-997,-806,593,1000,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{569,-612,494,959,-313,-1000,-95,-685,1000,740,-45,-649,350,-1000,-1000,-868,-76,-727,-740,-22,-623,1000,-581,-126,-305,-182,1000,692,-1000,-344,-322,-509,57,-827,1000,747,1000,1000,386,1000,620,1000,-110,174,1000,-1000,994,569,-1000,50,418,752,994,607,4,332,-788,-575,1000,-33,1000,1000,-538,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEyNi4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{201,126,657,-420,-870,1000,332,366,-243,-342,-95,-463,185,291,187,982,171,-409,871,-941,-12,-1000,259,-757,390,-1000,168,-894,-274,-967,170,66,915,573,554,539,1000,1000,986,419,918,-939,-457,-242,-453,193,-286,422,284,231,838,1000,-315,-922,445,-1000,-1000,529,264,387,-718,-32,-977,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{301,-930,410,-605,-83,1000,234,1000,124,-179,-819,-242,980,221,440,1000,468,-1000,290,-299,-163,-1000,1000,-467,-961,-153,-1,454,-1000,-278,441,-4,307,-821,829,106,-1000,126,-436,44,-340,-350,-1000,977,-1000,1000,-586,-398,152,1000,-454,314,-37,-649,15,0,-533,372,-1000,379,-812,-544,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjQ1", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{419,645,-807,-874,215,798,-996,575,-71,157,-723,384,-471,-705,672,-863,260,-641,-170,690,-526,-318,-988,-483,-87,401,380,-874,-284,109,70,-967,-933,-573,-113,-227,-515,-969,318,-749,48,-834,-141,-615,-475,954,179,-573,334,-76,512,-392,-470,-924,-128,-173,-386,576,152,-999,-154,-352,995,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{583,-1000,-562,-1000,701,711,1000,-589,-691,1000,-1000,-926,829,-1000,286,-1000,1000,-640,776,-70,762,1000,219,-638,-1000,-998,835,1000,-1000,592,91,905,-1000,-826,-828,1000,1000,1000,1000,1000,593,1000,448,1000,226,145,-1000,-1000,-1000,-269,-210,-333,1000,1000,1000,-1000,-1000,218,383,-1000,-1000,-181,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-1000,-1000,494,959,809,-1000,-700,-678,1000,-433,697,263,841,1000,817,-307,639,491,102,514,933,-1000,410,125,-581,131,-592,412,-988,-830,-5,66,248,-327,1000,-939,-199,-623,-674,-997,-736,129,-1000,582,177,264,-1000,930,560,-207,-770,266,-864,-472,693,-547,-495,-953,-1000,563,82,-256,-305,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI3MQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{199,-271,193,-4,-17,742,182,731,-428,127,-1000,-398,491,-870,-655,274,17,-407,-267,-1000,245,-1000,-419,-369,-143,-646,-440,643,177,-39,-507,-461,797,732,-481,1000,-787,807,-916,-21,660,-159,-272,800,-438,697,-1000,-336,1000,435,209,-190,1,1000,335,-144,36,271,-459,-686,-1000,-77,-349,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{914,-419,562,202,798,-707,945,-162,153,731,950,241,-667,-991,201,-118,873,274,-924,455,334,-655,528,919,-185,-959,892,-657,-555,55,-562,363,899,114,86,-910,14,-197,-424,-887,893,-83,680,892,-422,-491,576,-739,-714,891,-633,-954,-481,-243,-69,-144,-78,-647,-241,-998,676,-875,273,-813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4xMUUrNTY5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-954,-111,-268,567,595,1000,1000,-1000,225,-450,1000,895,1000,-542,1000,-115,675,502,-278,162,-92,-1000,449,129,307,-41,267,48,37,-479,513,583,1000,-962,154,-534,122,714,-165,-165,1000,12,-838,116,-40,437,-661,-947,816,519,-1000,-96,-83,935,-129,36,-119,-262,1000,-459,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{59,-329,-318,-1000,-534,733,129,801,1000,565,-577,-1000,668,-390,-303,796,105,-661,-180,-252,-520,-522,-130,952,-97,-22,1000,12,-27,-1000,-950,-294,680,-483,-27,1000,73,1000,-613,759,-133,733,-925,766,-161,-663,-611,919,-300,-23,88,1000,1000,296,379,-357,-598,939,199,-299,136,503,550,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{-154,432,840,-760,987,-706,-887,23,560,827,401,756,-619,848,-955,492,533,-635,584,-560,-822,956,-410,-722,419,756,-686,-29,-953,-506,441,-762,-810,-171,248,-303,335,-188,891,-461,977,-271,175,-902,211,-170,193,117,-969,436,500,672,758,830,-861,743,86,968,718,-761,556,528,40,924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{516,-427,660,-1000,1000,-1000,-427,-176,1000,1000,1000,669,-1000,1000,-265,-171,581,255,1000,545,-397,802,290,-576,354,1000,-1000,271,305,-280,367,-1000,-482,-79,779,-1000,1000,-158,-277,-408,999,-979,842,-852,143,759,400,119,-1000,-471,452,411,634,221,-6,229,315,1000,473,-976,503,135,-210,502}));
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
            new int[]{-346,337,-423,-669,-389,-872,158,1000,1000,180,613,505,-779,-724,-538,1000,-166,407,-1000,179,208,499,477,-990,-722,736,-313,131,626,1000,-815,993,480,-627,-503,-182,-179,101,430,-166,-584,379,1000,256,281,249,-11,-377,-1000,538,-188,40,1000,-728,-1000,-102,567,1000,1000,77,1000,832,1000,526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{983,926,1000,581,131,182,1000,-122,-829,-1000,-779,-1000,5,841,340,-1000,1000,-1000,-321,1000,1000,-991,-682,-1000,1000,-1000,-1000,-1000,512,1000,-1000,-1000,1000,941,1000,136,870,-804,-942,-1000,28,74,1000,-902,267,-494,-827,1000,-1000,-675,-982,-1000,-579,-877,-1000,1000,-1000,1000,1000,-836,-37,-550,-1000,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-151,905,-496,461,-429,1000,912,569,-113,-631,-114,83,1000,1000,828,77,-442,95,-1000,1000,30,-270,270,-857,-510,55,646,-671,166,-847,509,802,532,1000,-703,130,313,-607,184,-213,297,52,-236,820,-1000,-334,196,665,-1000,-1000,905,396,-520,-983,-1000,-758,-1000,777,976,-1000,-1000,-516,1000,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{770,-75,-992,-248,257,-184,-910,-569,901,-45,125,-195,-491,503,408,-448,720,707,838,509,-51,-607,-611,-774,-534,771,185,463,-576,383,49,-29,940,-655,271,-935,-633,616,-875,10,821,904,-850,-966,-314,58,610,905,-636,313,673,-552,831,585,869,108,-287,165,-860,-269,536,-357,-574,-895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-951,958,-1000,397,123,302,-214,1000,1000,-240,855,1000,1000,328,-93,247,-1000,-356,-1000,426,-381,-452,83,-870,-484,871,758,-209,76,-664,141,1000,159,-1000,-384,229,-1000,582,1000,474,-1000,-333,1000,-71,440,5,974,-1000,-459,-1000,319,905,179,-1000,-1000,-1000,-28,587,1000,1000,-437,283,1000,358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-738,-1000,-551,-9,1000,-635,-506,206,288,1000,-767,1000,-502,-1000,531,1000,-244,-80,-1000,-360,378,313,-1000,-982,-1000,704,-191,-141,-383,1000,744,788,-581,282,1000,-985,316,-376,725,449,752,516,95,-842,-19,1000,595,1000,359,978,1000,-494,478,-390,-1000,877,-91,-1000,-594,1000,1000,561,-824,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{217,-251,-1000,-884,587,816,-616,443,1000,1000,-264,1000,-236,-1000,1000,442,-1000,-702,-1000,-937,-1000,772,-687,-870,-1000,1000,813,1000,-1000,-112,1000,875,-1000,-43,634,-482,658,-1000,1000,1000,1000,539,-1000,-607,420,1000,1000,-1000,698,-1000,1000,-1000,1000,-1000,-1000,287,-266,-878,-489,1000,546,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{297,968,-1000,909,-456,1000,-1000,-20,1000,294,-465,1000,1000,-1000,1000,-956,-1000,-764,-926,-1000,-1000,1000,-1000,-449,-1000,1000,1000,1000,-642,-1000,1000,-320,-1000,-178,71,27,-298,-1000,333,1000,1000,-332,-1000,-1000,337,1000,1000,-562,1000,-1000,1000,-1000,1000,-1000,-663,-1000,-1000,-1000,-1000,207,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-951,-810,-1000,410,1000,986,-459,1000,1000,1000,398,1000,1000,539,1000,-928,-1000,-999,-472,-1000,-1000,1000,-367,-747,231,1000,-69,-209,-414,757,-145,1000,-131,-27,-384,-624,-1,-1000,991,474,-532,-705,895,-1000,-25,-740,136,-1000,-533,-1000,-464,-860,224,-1000,-1000,360,-355,82,1000,1000,-64,1000,-458,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-235,720,969,567,-206,838,-342,387,-217,435,-147,423,822,621,290,945,-868,395,-990,594,-742,-196,439,-668,-300,591,796,199,115,-994,976,7,498,730,-979,315,-193,-538,-40,504,387,587,-599,581,-741,-406,558,469,-182,-580,602,956,-161,-280,-826,-762,-187,244,-283,-966,-431,-50,864,-374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-641,73,-1000,-905,1000,-1000,-648,697,574,-164,-17,1000,-241,-921,240,-570,-429,-156,959,-396,-59,1000,-248,-789,-1000,1000,-969,170,562,421,-904,79,481,385,1000,-806,-663,-950,1000,865,129,59,1000,-653,326,1000,-319,-1000,885,-277,-637,-443,626,-767,337,748,24,-180,641,1000,1000,1000,334,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{334,-1000,476,-107,-199,42,-327,161,-777,-931,-14,-331,-656,-63,430,-210,-1000,-4,1000,858,171,530,-470,622,1000,1000,151,-704,-597,26,746,-1000,110,642,-948,296,-452,-270,104,-115,1000,928,-52,-704,359,629,604,248,70,204,-381,-429,288,1000,389,710,-750,-303,-1000,394,98,865,-999,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Byte:OTc=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{1000,-1000,-428,1000,978,82,-1000,-640,1000,-43,-86,255,-338,1000,141,-466,-1000,571,1000,-345,-99,-593,-1000,1,677,183,-554,1000,990,98,-666,-534,191,-404,-1000,792,599,614,948,473,806,-136,84,1000,-1000,-788,497,-693,-128,618,-161,75,-127,-816,-62,-1000,-1000,-1000,182,-1000,355,838,-664,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{-27,-41,129,-80,-863,198,92,758,215,-738,909,-80,565,-572,-494,639,435,-396,-775,984,293,-768,-714,-762,18,-265,715,-115,-666,215,282,-775,-3,-883,959,-362,-547,-913,-657,-923,963,362,-419,-478,497,660,-577,774,129,806,-794,261,-927,-933,809,-677,-936,-469,923,-188,333,793,-663,-198}));
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
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{-261,-336,-158,-581,8,589,255,939,481,593,22,324,82,-511,533,613,-491,11,5,-919,-899,-436,-151,454,-339,901,79,-234,-881,-889,-664,221,-66,-482,634,-600,-307,341,32,209,505,-679,101,-916,-545,901,-741,-890,657,-20,-144,0,-748,-175,201,121,-940,-744,676,160,561,-103,-498,-572}));
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
            new int[]{-793,-362,83,-692,-346,223,-316,981,-254,615,-821,-990,790,1000,-110,237,-181,-1000,973,-767,624,-1000,225,275,495,-846,-712,-904,-763,-943,638,-292,449,1000,-20,-1000,-173,-821,1000,-444,-1000,-202,-32,574,653,-372,-577,197,116,-1000,-684,-170,-928,1000,-358,895,972,-335,956,-748,-161,-261,863,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{466,-722,878,-592,-957,888,98,-978,-946,-235,-799,-562,501,-756,929,-752,-967,786,355,-546,-425,-37,987,456,-43,-919,-809,496,-267,720,520,-208,-89,-185,-176,996,-113,-200,362,541,508,810,-422,954,923,976,683,-753,-379,-562,-257,-820,898,-135,-448,-695,877,-22,298,537,189,-127,711,221}));
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
            new int[]{322,-16,789,945,287,652,-587,-826,-629,135,927,342,-477,496,624,-996,403,496,-741,-144,225,-415,685,-711,855,-195,901,605,-742,-976,-501,805,930,-711,-221,227,499,968,169,-746,343,842,-974,833,-225,337,-652,-434,-888,668,-491,-172,770,848,723,477,-580,212,-385,551,-791,-581,-976,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{339,-919,823,-505,-73,-376,-245,-816,-436,-716,462,340,-122,308,-651,-640,720,785,315,230,-363,-608,-185,620,66,389,-505,-217,571,-485,-543,57,522,432,-298,-622,968,96,51,619,139,-1,-46,799,-972,632,962,-749,-230,712,408,696,199,709,792,824,-142,48,-214,466,17,992,347,-259}));
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
            new int[]{-829,-851,-853,-18,-376,811,-113,-310,885,613,-393,518,907,673,931,-388,-90,783,-788,17,190,-952,-321,557,242,614,-8,-252,-436,960,390,471,764,-527,375,23,-745,-10,-151,787,-277,-162,2,287,814,-453,877,-365,-955,-183,502,537,-207,634,765,874,-579,-536,-219,-321,82,-661,-583,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{-981,-512,7,-489,-340,881,254,937,-634,-634,-324,22,-9,909,-761,592,-704,584,-437,331,-919,513,-876,749,62,712,934,546,518,-444,452,959,560,323,-679,-265,-734,-826,999,537,-53,-175,-677,770,-731,-841,-369,-889,-871,94,188,326,-221,-889,-656,-619,947,16,-882,-718,477,787,699,-390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{688,228,-251,249,32,-980,-45,368,-95,-132,889,341,-2,-305,-371,38,-193,-818,-761,-958,546,371,-315,-796,490,47,904,-587,-563,19,211,-477,147,-81,-869,929,922,-729,861,-596,-221,-779,631,-481,-901,533,-567,-812,66,-733,817,-259,737,864,746,-756,156,-701,-138,790,-411,432,-35,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{-787,215,-4,-945,-945,636,-172,986,805,-798,-268,596,-484,-194,-33,188,-722,936,2,327,209,615,-222,625,247,688,-585,-236,525,-601,429,-613,873,964,921,243,-448,955,-548,-112,722,695,-655,-380,335,581,-996,-112,162,-724,531,397,474,-945,359,-667,540,-769,-40,-293,555,-438,-262,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{-399,488,-190,329,379,-518,692,-261,-508,-573,-518,-830,106,878,292,-508,-566,794,520,-552,-897,441,466,-641,-958,-969,-480,-243,-514,-324,-504,-816,677,243,893,994,-234,-742,-858,998,-486,138,-801,560,-511,-287,281,858,-398,-744,-49,-86,122,56,318,259,-462,935,652,53,53,68,409,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTEwNQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{987,-735,-756,170,-105,-577,-524,-473,-475,-508,-631,-17,-226,485,-226,569,502,208,-473,666,243,-679,-584,-180,-967,-754,-965,605,248,371,-581,420,-417,220,-688,-698,425,911,628,-665,-313,-942,-805,-967,950,-451,260,-807,181,-901,355,-838,-292,368,144,173,179,541,636,-488,257,-246,-242,-410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTk0", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{603,525,679,-334,-464,-94,563,189,-710,762,507,54,-354,-449,-315,-448,-152,275,-34,225,-387,809,733,411,-366,957,-819,913,130,-183,-613,139,303,567,690,-243,237,19,468,-730,-288,449,280,-727,-284,81,-487,127,-9,361,-833,-246,-130,672,227,908,-135,535,182,793,2,-551,-310,225}));
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
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{892,926,365,-139,-286,-732,-141,-499,-760,729,-674,-777,563,171,591,-77,-843,760,-164,-279,-854,739,-841,-835,-29,-774,832,580,-990,-121,65,-760,6,350,148,173,-569,-895,939,93,-633,-593,-145,-792,-98,-932,-261,469,566,-122,297,201,914,562,-857,-12,-986,-422,928,-799,-721,-912,500,618}));
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
            new int[]{1000,-1000,-1000,-1000,-819,-1000,-1000,-1000,415,1000,-22,1000,-419,-536,-896,-597,-1000,-1000,1000,-1000,-1000,-244,-759,-444,1000,-1000,1000,343,1000,855,152,-1000,28,-614,-1000,427,-1000,-926,-1000,1000,-830,-1000,-329,-903,384,-877,1000,-1000,-378,-462,-1000,-199,-675,1000,373,1000,-1000,-823,-684,202,1000,-1000,-1000,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{-505,52,466,-439,791,917,66,113,-592,737,-677,541,702,351,-755,-542,141,-67,866,-812,-623,1000,162,306,494,-549,-751,-93,839,424,594,-553,495,520,908,294,-786,-744,-566,-46,-598,-524,-607,-618,635,-91,-473,-292,-905,37,841,-714,297,30,-31,-951,-470,607,853,-190,-432,-608,878,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{-705,995,-983,-179,-191,-170,-534,-541,-93,374,439,708,-439,524,-441,-119,959,-283,375,747,330,381,-329,-730,52,-302,208,-708,299,-915,-23,-903,130,515,-4,75,-317,132,-320,577,-93,512,-94,421,160,-694,-945,511,890,68,477,-448,641,587,-867,-63,-373,-805,477,-454,282,192,36,-584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQyOQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{238,-889,-835,-190,-429,-613,-844,24,958,213,-650,-521,979,773,-12,-441,-990,-21,877,-553,-75,-795,-998,-960,-891,-978,288,-197,499,-979,-717,-384,65,-592,635,-147,128,428,639,24,237,870,-196,-875,-86,-74,488,-471,-455,-509,899,721,-85,622,494,468,442,335,-293,133,-952,-833,-750,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{-891,-395,102,260,-417,-466,-175,-817,907,-92,292,291,-265,759,160,486,626,-501,560,-681,-888,16,504,686,103,-113,-949,-244,591,-923,791,-8,-344,-5,-764,885,-622,310,357,875,-616,556,-325,-211,-465,205,-918,-878,-518,667,-118,473,721,-583,405,534,128,363,-5,-803,-9,-533,189,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Long:LTM3OQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{184,20,771,-635,-379,575,-65,-740,-425,381,866,-412,-730,562,-366,720,-153,-648,540,-960,-818,581,544,580,5,-884,-619,824,-323,353,-35,189,-789,-476,-429,342,97,-336,918,-595,353,377,-219,385,-182,-788,37,689,-814,-580,149,-534,-487,132,-965,-286,-979,-408,-53,-447,-111,979,594,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{-537,716,869,997,-320,650,-584,-971,20,-789,-652,-513,553,-132,734,537,-465,308,-293,919,-318,-215,-719,-786,609,-928,360,-475,-17,379,-968,336,-740,-687,332,32,-696,135,-157,774,583,-626,750,284,209,-804,-771,435,-355,842,-545,498,542,-98,904,-606,-130,-749,-879,-631,-686,46,698,-867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{995,670,62,9,371,170,918,932,-40,-601,-649,-556,-977,825,590,177,736,561,-498,306,-486,-583,386,-223,721,-645,-736,20,576,573,79,-756,-682,-196,-158,-706,-890,-627,-635,548,-464,-948,-62,-650,59,809,95,-924,357,174,412,-918,46,-836,188,-486,993,408,-172,230,153,482,737,950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Short:LTI5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{-229,421,-234,-914,444,-294,-782,653,104,-651,-131,-698,-792,-241,814,-650,445,855,-929,971,-911,-402,-451,-40,424,9,84,-326,166,961,-243,-211,-162,375,892,282,-83,-950,-819,-967,-760,-707,157,-185,-376,756,-86,48,-55,455,-614,706,55,-198,785,-13,-222,665,-160,4,11,980,403,-416}));
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
            new int[]{597,-41,652,220,-553,606,376,-328,5,222,-586,-207,419,730,534,162,-802,906,-727,704,-585,-318,764,-20,922,321,933,-873,714,785,-134,-300,199,926,-338,-869,-881,-778,-980,746,-862,728,-89,402,-303,-414,259,634,629,35,298,-56,263,-827,-563,-641,237,919,864,700,-877,-416,881,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Byte:Nzk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{547,79,-943,992,1000,-217,1000,1000,363,-813,-1000,639,188,-230,327,293,580,-1000,874,1000,1000,1000,-144,-1000,1000,-307,-274,1000,-1000,-427,48,1000,-1000,212,32,-1000,-1000,-131,-943,-1000,425,-981,45,668,-1000,511,814,412,464,-112,733,-1000,29,187,-424,-807,-30,84,1000,-1000,1000,-564,-1000,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{786,984,-924,-171,-436,746,-430,586,606,783,-547,-773,-159,-603,-396,365,-205,165,365,184,-811,162,-602,-791,-933,163,-459,634,252,127,-577,-370,946,-293,-971,-912,586,-322,74,128,702,217,-864,-664,-577,-227,-243,-791,-438,-87,439,-449,730,966,391,-330,-166,553,154,161,-545,167,-153,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{-49,822,225,676,710,-131,-110,-72,-554,73,-285,993,-838,716,50,684,222,-827,332,-150,616,-120,-676,-250,-922,152,-14,-339,-498,773,-617,-234,-931,-286,-564,-594,-148,-477,0,-357,244,912,-155,732,494,944,-622,-399,-92,-213,-348,-298,-218,-478,-857,-276,50,-310,-944,558,772,-618,-483,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{-270,732,448,131,-40,-376,912,425,-778,611,-125,-494,905,775,-514,-255,406,-912,-934,882,-287,38,251,-914,-491,212,163,248,998,-3,326,-174,-900,-60,-443,-685,709,-171,-889,-864,281,89,11,-394,213,-295,-338,-486,-838,546,-403,-196,-252,722,552,-121,-58,-618,894,104,-170,236,-99,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:LTU4My4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{890,583,601,891,302,399,-16,-45,656,-408,-206,-481,-392,293,719,515,176,693,-123,533,-159,471,611,861,-725,-412,-20,-840,-169,348,182,-677,-571,972,-436,790,-503,472,0,353,-799,-868,22,318,-605,540,-741,-225,-594,-259,-461,692,-924,-111,-726,-444,316,498,651,392,842,316,-406,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{168,973,-727,737,150,-195,-155,792,-807,281,228,677,433,-496,902,-119,196,322,848,-887,-510,-686,302,-904,-525,280,557,-627,-599,127,-867,190,497,-682,434,-160,274,889,745,757,808,445,347,48,-292,540,407,179,-336,347,300,-905,-230,-364,77,164,-891,753,894,-954,505,-532,-607,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:LTU1NC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{41,554,245,-472,-978,467,-262,-869,-104,347,78,191,202,-523,582,-136,101,-480,-830,-844,175,-843,-148,-216,-21,-335,769,-421,-934,291,-155,982,171,548,-132,-531,254,-587,592,677,-823,821,337,371,-68,796,-522,460,557,131,-807,786,121,-423,902,563,-483,207,931,-970,157,-187,-887,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-615,454,-669,-963,-982,-872,-632,857,-262,605,61,635,720,316,382,806,311,447,420,-152,772,-986,415,-359,510,-126,-154,-510,-714,-586,-422,-754,-285,-606,-229,681,388,-295,982,615,-63,-351,-308,-850,-566,-263,343,-196,680,3,-90,-625,136,-658,2,-132,505,-515,-448,-996,327,875,241,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Float:NTQ4LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-471,548,-882,476,129,770,60,214,-690,-698,-774,-772,-156,563,484,498,743,605,758,-359,904,-255,573,783,389,859,-618,194,-520,-715,356,-722,-453,1,-948,-715,-270,19,-959,417,-567,784,-550,-123,732,774,172,841,929,488,459,-636,-317,497,-418,751,-571,381,304,-907,130,-8,-997,-671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{-472,824,-854,-865,-49,-911,-35,352,531,-736,-334,70,8,-407,386,650,685,175,-263,890,-736,-36,-559,943,-117,833,-738,-806,414,51,706,977,-725,-437,65,312,575,581,-898,-749,710,-833,-119,882,-745,722,109,-474,-625,595,701,-708,-128,394,230,-250,460,990,-577,-781,-109,-292,936,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Float:LTM1Ny4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{922,357,-543,-852,-463,-748,672,-208,733,245,165,-422,-428,-994,346,-754,-59,768,-348,-709,-916,137,17,486,-654,678,-91,-936,199,-928,322,849,-336,-337,270,-28,955,-647,978,-798,-655,-56,245,353,78,-695,269,775,459,-12,-422,-495,189,333,675,286,-519,477,-506,380,497,215,835,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{-630,-195,93,115,-937,975,-803,-820,574,114,-56,-367,-937,-807,-563,-506,-618,-26,377,769,-904,-257,-162,-773,504,722,836,-601,-779,-547,-557,-440,-129,656,691,466,11,330,-361,-367,-481,-801,-382,64,401,-13,-109,526,-815,477,-241,-304,910,807,-524,779,86,63,247,606,-53,-163,64,-394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:NzU0", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{-205,754,-384,462,-918,-232,-366,-560,-876,-503,894,410,383,-145,678,-159,-223,83,-983,-116,932,-878,-851,-544,-894,214,359,876,931,341,40,669,-882,368,-676,-861,-156,992,749,333,-350,944,-938,184,-616,-727,-498,-266,964,-990,-328,623,566,-982,-531,-701,-809,402,439,775,538,-460,-601,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{143,578,682,457,-436,716,495,-113,-229,-824,-183,-785,-304,-673,621,895,656,851,-31,-424,832,-959,-905,780,-536,56,-620,-444,-4,-721,-898,466,-895,-91,643,590,999,110,-549,-125,-313,-362,-456,145,3,712,-431,-662,-903,-910,509,-867,-166,-206,-970,35,70,-263,-861,192,-854,649,-658,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTc3", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{980,577,-318,-661,889,-797,342,-1000,-229,-247,-1000,1000,-473,801,-178,103,757,391,-28,1000,-467,-479,-1000,-1000,1000,-126,1000,362,768,585,-156,466,1000,33,-1000,590,930,423,-203,-125,295,-362,497,-1,-1000,576,722,900,-693,729,-1000,665,991,-206,-662,1000,1000,1000,601,-572,986,-417,970,54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{792,-451,-79,86,-520,-643,890,193,-23,-777,-640,484,-636,-132,-73,693,882,-235,-363,-242,-639,-222,-186,-972,636,-987,381,424,-529,575,-501,-5,292,-309,733,934,579,760,208,-826,175,281,-753,933,949,-677,-172,-748,273,239,901,-555,575,-357,-986,-324,-96,-658,-918,400,841,856,136,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Long:LTEwMDA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{643,-1000,418,377,1000,516,1000,-19,127,378,-978,348,-1000,-870,-403,1000,465,1000,880,-538,-603,1000,-543,1000,-101,717,-1000,1000,1000,107,215,-156,621,140,253,113,751,-405,1000,-1000,884,767,146,1000,-184,491,303,290,-1000,881,901,68,-51,-357,-18,986,-1000,1000,-1000,510,1000,-523,-405,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{-742,-758,401,-96,-51,-796,-89,-359,-375,-353,-887,902,194,973,338,380,-967,-523,516,-363,-743,-724,915,681,131,334,-926,740,985,-693,-982,-173,-510,-116,709,238,-46,919,927,-545,-580,229,-724,-875,787,-786,875,736,893,588,-569,-578,-401,495,830,916,-763,1000,221,-899,488,-463,570,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Long:ODk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{-13,89,-355,905,177,-426,477,-171,-328,-492,979,620,-647,616,-216,-422,-4,557,-603,636,515,344,-774,-555,359,-364,-712,680,-162,-386,92,-911,-317,-13,-898,353,-55,426,-593,191,432,194,-262,-707,364,-924,826,-338,-391,-141,-783,709,-16,-464,161,-912,406,-816,-68,636,432,-959,-947,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{458,910,894,525,734,-838,413,37,111,947,-386,-344,-34,888,774,973,313,-717,-111,-660,442,870,-552,-383,261,328,-92,-785,566,-853,809,-112,-11,369,-622,-706,-853,-579,-322,-310,649,753,889,277,-940,-675,-864,-284,-354,-879,-556,112,783,-193,844,-644,306,-571,448,-837,718,-409,-611,-670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Short:Mjkw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{772,-290,-196,259,535,-291,-933,159,685,-40,681,764,-861,-56,92,164,702,953,-852,-981,707,-282,702,-791,-220,51,245,843,548,-517,-370,962,-379,756,-208,-234,-21,707,-346,-409,7,-372,856,-6,-124,473,859,-690,-325,-563,64,811,793,-627,81,226,401,-718,61,770,553,662,69,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{-747,-997,254,86,980,451,554,-410,807,679,530,-958,921,-605,306,-422,335,-632,911,-384,-424,237,208,210,138,176,557,706,826,906,524,496,246,511,-126,629,-75,32,-946,619,-213,-78,-543,912,-950,-900,827,166,-978,565,-619,748,-316,-688,413,-331,-474,627,498,-448,-388,-230,726,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Short:NDA4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{-140,408,-870,-1000,1000,-466,362,-1000,511,815,-756,1000,-1000,-287,429,1000,-1000,-488,186,251,700,903,485,-1000,-436,-310,-101,-853,897,381,-55,-1000,-437,609,-511,443,-1000,334,-448,651,-455,60,182,490,61,245,-127,893,-670,-352,-501,295,-874,716,254,485,930,443,1000,-272,-708,249,-459,-1000}));
    }
}
