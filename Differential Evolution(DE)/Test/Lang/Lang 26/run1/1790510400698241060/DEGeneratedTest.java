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
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "equals(java.lang.Object):boolean",
            new int[]{374,101,-433,-385,-861,81,-822,-681,-213,481,-433,952,-878,-402,837,545,-411,246,413,-358,-517,-960,721,365,216,-285,-613,-126,-912,-363,-106,266,-452,-883,595,-947,-164,-614,240,-737,-979,-808,784,941,-980,-180,-867,-607,-92,72,691,-176,13,237,-886,-777,-307,-598,491,323,427,-403,355,-771}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "equals(java.lang.Object):boolean",
            new int[]{-278,565,-473,343,-550,628,778,189,294,251,632,-182,-102,749,208,879,786,-437,-745,-941,787,639,466,-565,934,108,987,365,38,441,337,513,-257,-268,919,-355,-306,-2,562,806,-348,-528,148,-832,-122,749,-758,-576,933,-669,898,910,-884,-360,-698,-144,248,-118,776,-908,217,-717,-193,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{-398,-505,-110,-367,129,-946,-143,259,979,80,381,465,-466,-844,969,-849,-24,-268,-668,-702,-15,-870,-907,478,-429,-201,-631,-756,-676,-800,-47,-553,-469,-509,-158,-518,76,-197,263,211,-204,284,-9,-90,-922,341,788,944,860,-457,64,-911,931,496,501,413,430,683,309,482,-189,715,-989,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{-142,574,907,-147,-579,-267,162,376,890,379,-89,633,-825,-715,533,203,-272,184,-288,45,-341,669,-847,959,616,210,829,438,-991,-801,-738,-22,303,732,-621,896,-416,-561,-143,426,341,-928,-451,-526,-459,821,-409,759,-561,-652,80,-694,-710,-623,-166,226,-836,359,695,-435,-616,216,54,644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar):java.lang.String",
            new int[]{65,-721,-810,375,554,-723,791,47,378,-847,925,833,-354,-14,-802,-272,576,-962,-37,-354,-137,-385,-767,-541,-501,-766,-800,-966,881,-281,844,513,-871,-957,77,456,958,-213,-781,662,137,939,-831,497,-403,424,616,386,613,-776,846,753,-909,257,274,-862,-997,267,-321,485,-758,906,-656,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar):java.lang.String",
            new int[]{-430,382,814,605,73,-764,88,-85,540,792,-525,771,-11,764,-814,618,-218,13,868,144,815,751,-275,398,-862,39,-237,-979,134,693,382,-152,458,961,592,533,353,382,382,-947,663,60,-568,-48,898,-706,-744,-1000,-967,947,134,593,371,-744,743,-354,-211,-146,242,-979,925,750,-217,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-453,-118,962,781,782,-169,-29,297,527,-4,-904,87,-886,-643,-900,-281,382,218,-28,409,-428,-117,839,-245,377,959,-615,-483,954,165,-369,473,-409,182,-380,109,-317,424,283,221,674,405,-444,385,728,-174,-307,-309,-2,438,617,-861,610,309,178,656,705,-818,-808,-176,-756,305,-720,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-663,373,560,-851,566,-587,703,-941,-320,605,-284,219,-1,371,-351,-160,42,640,466,-543,904,1000,-719,-221,986,-629,967,-698,521,-43,189,112,258,944,534,-961,604,707,-824,651,-458,128,-684,450,-258,34,-535,71,-597,740,620,-709,-431,-791,-696,201,958,441,532,-940,-371,-634,-121,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:V2VkLCAxNyBBcHIgMTk2OCAwMDowMDowMCArMDAwMA==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-988,-88,195,-624,430,-918,-30,704,582,517,435,-792,140,881,-74,-997,-112,-745,-123,-375,844,47,-91,-140,837,-940,-564,480,-829,686,234,617,885,-961,8,-694,-182,-883,-707,-133,-804,-444,-753,194,-206,403,-182,810,838,-518,601,546,-229,-614,273,-868,-222,-100,943,618,510,-238,942,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:MTIvMzAvNzEgMDA6MDA6MDA=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-315,619,-246,728,-740,111,318,-584,-290,-386,-758,303,10,38,-333,859,-342,-551,382,741,-515,952,-538,-877,273,241,-842,-531,-160,-929,38,-748,140,756,915,-52,612,56,466,-271,531,285,592,-264,-560,-621,561,544,859,-254,773,480,442,235,-439,-152,-522,302,-101,768,241,560,-918,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:RnJpLCA1IEphbiAxOTY4IDAwOjAwOjAwICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-135,-952,-342,-727,847,-786,849,-179,-438,-206,538,-153,217,-825,90,976,626,-569,328,550,-821,232,687,117,-522,82,288,312,-64,727,-437,-733,491,504,-394,-501,-682,581,-259,-663,906,575,33,-615,-803,896,365,249,-375,261,-429,837,-169,359,653,889,593,-654,-416,-917,91,-2,-627,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-883,692,610,-171,728,729,374,783,-163,837,842,-924,-86,-598,-448,-518,307,79,-76,-555,-643,-780,205,-511,613,70,963,-984,10,380,80,-176,411,423,-318,350,909,-31,518,425,-113,-737,-80,-836,996,694,-323,-518,-55,495,-114,-288,-754,-419,184,-206,-62,-607,622,607,-299,506,890,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{157,-791,505,345,-171,230,-521,832,890,194,-433,694,897,353,692,199,-484,-122,-465,-737,-720,-375,399,865,-649,284,610,376,-968,119,-282,-771,-753,935,306,-974,234,-635,244,-27,303,147,-128,-662,-349,670,789,-461,979,-287,-351,-579,891,81,723,287,951,610,820,347,648,-127,-378,-634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-446,656,-89,-394,590,-1000,88,-423,533,-579,-1000,269,-959,124,828,481,-866,-120,0,-136,-1000,254,1000,1000,-133,319,596,369,938,-36,-1000,-980,-557,-1000,567,799,277,856,-296,-494,-556,736,971,-240,1000,-1000,719,1000,-150,-1000,-1000,-584,621,229,-885,-387,948,240,0,1000,385,385,-453,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.String:V2VkLCAzMSBEZWMgMTk2OSAyMzo1OTo1OSArMDAwMA==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{5,-526,2,-57,158,-401,-15,-941,-51,634,-370,527,579,306,-861,753,-83,-542,925,-467,742,-956,230,-432,-255,127,-505,689,556,-256,-714,-888,736,106,-311,-732,658,975,-556,-208,-464,-752,-134,127,443,-204,552,-174,124,-559,276,-835,624,-441,-914,921,341,267,589,-694,-47,412,686,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:MTIvMDIvNTUgMTY6NDc6MDQ=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{-818,-797,-397,858,405,-453,186,711,528,-590,437,-105,238,217,-471,-884,-901,-458,866,360,203,912,190,-686,-192,184,927,959,3,-598,857,-816,117,612,291,-500,-419,-478,337,974,-715,65,536,-646,49,-49,201,22,-209,106,-898,900,-790,-471,807,-301,-648,-437,-45,-949,-695,-190,991,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:VGh1LCAxIEphbiAxOTcwIDAwOjAwOjAwICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{-128,521,-387,23,-372,-725,-575,-188,530,-881,-941,-393,401,746,-320,224,137,216,309,-422,-803,-184,-936,-861,724,376,-871,-961,-485,628,-268,354,-226,785,481,-655,519,904,-145,-552,877,-211,-701,682,-918,-809,-550,802,-60,910,325,-6,-232,-995,971,-782,-635,-200,31,-618,439,599,951,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-727,-640,732,977,497,959,463,-315,-806,167,-506,662,-835,-997,-90,928,-633,733,-936,-733,-973,-40,36,-616,-393,-387,832,249,823,-414,-31,-368,-948,203,-65,-702,-436,637,952,319,-756,-31,-552,520,-278,638,694,-777,177,715,-822,-884,449,655,-548,-649,572,-62,444,-236,340,-607,-513,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-412,-41,-254,628,-237,-564,658,13,837,224,377,796,122,352,570,-434,957,-557,-625,84,861,982,86,-888,-808,-376,592,663,-245,18,416,-596,825,-18,20,341,247,-131,-1,-60,455,-530,-239,-608,-490,-78,322,-136,-383,719,-11,259,-65,-822,854,271,-6,-546,264,-673,-491,590,730,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-858,-961,-342,54,645,-888,-501,8,-668,708,-473,-648,513,283,-962,-35,-147,800,755,388,724,825,-367,489,-800,227,-388,-328,-759,-959,291,-560,-320,468,-695,-402,-207,-24,-840,-640,-593,-21,-759,207,790,-328,757,436,893,264,515,88,-102,-935,565,-876,-512,-333,-981,-406,-223,-181,624,-116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{467,-653,-715,285,-818,293,-573,-149,-723,-546,-530,349,106,103,-945,140,701,261,-448,850,941,-318,-666,199,536,42,-885,351,577,-368,-987,-806,186,-263,-967,-625,-322,-694,843,-110,-440,-475,905,-447,-319,496,-722,758,893,294,776,-364,711,434,-481,889,601,970,388,400,-201,-699,-584,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{1000,0,-723,857,-288,0,979,1000,0,868,0,1000,-301,-1000,-336,-604,-56,0,1000,1000,1000,-1000,231,274,-578,1000,-11,-583,1000,0,907,808,1000,-469,815,-487,53,-532,-1000,-139,735,-168,-172,258,0,0,1000,0,-53,-1000,331,1000,-920,775,467,-51,-433,350,1000,1000,221,-229,-284,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-372,-174,-558,-539,-512,695,-580,398,-182,52,111,-844,461,1000,-579,-354,1000,-412,-817,386,1000,-394,-493,618,807,210,-111,325,-280,63,-772,924,-968,830,432,-147,273,118,-492,545,462,-694,-118,-320,20,408,-261,-121,24,20,255,-73,1000,-639,-554,-216,-1000,-170,378,-1000,125,-432,-31,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-1000,835,-1000,-11,509,-924,164,-1000,-1000,1000,1000,270,-1000,-437,-1000,-210,675,1000,-1000,-186,442,-1000,684,762,1000,1000,157,1000,-979,-1000,377,1000,1000,99,603,1000,-1000,1000,-1000,-1000,608,-1000,1000,1000,-149,196,1000,253,115,-549,1000,246,1000,1000,-1000,1000,1000,-420,-1000,1000,-906,-1000,-667,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-32,-156,-1000,61,-1000,-208,1000,535,78,-119,213,-237,-6,-51,-1000,-325,-431,1000,-10,445,-992,-382,364,437,1000,828,-137,0,-682,-1000,-886,-904,931,-976,-582,-739,-456,0,184,-746,485,194,877,1000,-317,-98,1000,339,218,684,-1000,1000,-321,-200,707,-1,407,-213,-231,661,-187,245,263,671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{86,846,-111,181,-156,541,-1000,-1000,675,-267,834,562,100,784,-162,1000,-901,-1000,-717,231,-257,126,183,57,1000,-82,-1000,-1000,-1000,-524,1000,-616,766,-391,-264,638,-1000,-717,-898,487,-337,-588,-1000,956,1000,510,281,-526,-733,503,-1000,725,-823,-1000,1000,334,494,125,512,-1000,-938,-1000,826,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance():org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-818,893,-371,-542,644,132,-1000,79,20,-1000,1000,329,1000,-828,381,-673,1000,1000,-937,1,368,-139,1000,-939,-463,-572,-312,1000,54,1000,417,140,-894,-495,190,1000,-928,-667,918,-1000,1000,1000,1,1000,997,-426,-1000,872,-349,-792,1000,1000,1000,1000,-81,-731,-874,1000,1000,291,1000,791,-652,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{428,538,-196,971,801,205,-493,-760,558,24,418,920,-764,543,-799,-775,690,-318,-250,18,-969,83,688,-175,-193,-460,-721,43,20,421,-77,-665,-489,143,-762,-499,-439,-351,961,-836,107,586,330,504,885,394,-501,972,45,-92,568,414,259,204,-685,376,-748,428,416,476,751,116,17,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{847,143,266,171,-212,898,-73,-380,466,-507,216,487,436,-245,-537,416,347,-63,23,-222,790,-549,-329,-1000,485,-380,-82,-360,58,686,-324,558,133,-959,131,-205,-1000,-1000,652,468,-101,45,904,-181,-245,-193,632,685,314,-156,808,-283,161,-140,159,531,-179,768,-206,-549,-454,811,129,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-343,579,-381,992,116,1000,-538,-379,1000,-1000,10,424,661,-579,-463,-1000,1000,693,-404,-431,139,-272,832,-404,-424,-49,-1000,924,402,660,822,-302,-5,-579,-1000,1000,-503,-524,789,-905,698,760,-206,326,-99,-98,-1000,88,242,-964,440,-97,694,1000,-65,-1000,-1000,807,751,-949,1000,370,-577,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{175,215,259,-542,-1000,1000,-959,-121,375,-334,1000,374,984,1000,381,656,727,-29,-1000,-1000,-1000,-486,-735,-854,-540,-1000,260,109,916,-711,673,-524,1000,-1000,-370,93,892,927,502,1000,-861,-873,-1000,-1000,-1000,1000,-1000,595,-650,-438,1000,-599,882,1000,-893,210,-828,1000,-909,297,-1000,337,-1000,-723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-34,1000,-205,-959,-676,996,-491,-1000,-194,-1000,478,-727,106,-720,808,-332,-325,438,-519,-698,1000,375,399,241,-167,801,689,646,888,1000,-819,547,-552,-226,222,20,-1000,-688,-81,-786,963,-156,552,573,-271,-474,-445,729,-431,-433,-212,232,480,520,644,-1000,-310,592,315,282,942,979,463,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-451,640,28,480,183,738,-916,521,812,202,243,-10,677,314,-672,-288,918,-328,-655,-431,139,-272,-236,546,-1000,-190,-539,-53,-438,-377,788,-302,603,-8,-414,-346,1000,-183,702,930,-808,760,-915,-485,334,-98,-1000,932,321,602,1000,-97,-166,1000,111,763,-784,-400,-106,-569,99,391,-835,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-270,-884,-527,930,-208,-59,420,-601,491,-176,292,562,-286,210,867,78,-972,-864,656,825,56,-127,-172,-866,-601,-533,382,-100,459,618,-86,-422,387,-615,230,295,729,-846,240,390,890,35,29,597,455,361,-151,749,-399,-639,-129,-573,388,-783,-34,271,-480,699,-460,-177,-649,-643,-492,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{47,807,11,374,-1000,693,736,-183,416,-11,677,-267,-159,-158,-805,1000,-629,-1000,1000,-1000,1000,-364,-1000,792,529,405,867,737,-306,-975,466,-140,1000,-387,-427,-781,400,354,-685,1000,-1000,-74,1000,-1000,388,-733,643,-1000,605,-791,-544,-1000,-899,-690,-398,168,1000,-482,-1000,-1000,800,-219,272,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{589,953,-32,805,-1000,42,-19,-393,508,-537,-200,-900,687,1000,-804,-675,-1000,-553,478,-449,447,-185,529,-1000,490,1000,571,-377,274,163,-1000,394,-769,91,-12,-729,-1000,-864,-1000,718,633,-438,1000,85,997,-727,977,-57,170,166,-1000,-686,-484,-588,622,1000,-69,-1000,-202,-360,941,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{525,-1000,933,-896,-753,578,630,-1000,942,515,-722,1000,-266,-994,-266,782,511,-125,-868,-356,869,-353,638,-548,321,-1000,1000,-351,-1000,-246,477,-1000,1000,-394,-273,-938,-1000,-929,-83,1000,-977,801,260,-1000,1000,-312,604,235,1000,-630,-419,278,-750,929,-316,157,80,-1000,314,527,-102,-580,293,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-819,-831,-635,-891,510,368,504,-552,660,71,-66,388,-60,-529,-139,-115,-920,-190,-992,-970,747,-3,261,-691,528,-853,938,-207,-804,-780,112,-802,664,472,-985,-417,-806,-929,-232,359,-860,-137,407,-794,815,47,512,566,871,-700,-388,387,-967,756,-31,21,985,-841,376,301,-285,-482,133,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-274,929,171,689,531,-597,-656,559,-889,76,659,-819,323,-925,767,292,-98,777,-672,863,-539,-638,379,993,-457,-645,723,-236,-590,-502,-832,-354,-93,828,-934,891,-129,-540,776,214,-737,-781,462,-933,-480,-958,904,-239,-572,839,928,844,-736,-625,453,219,896,658,194,-256,-918,-694,597,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{541,603,-629,813,790,-1000,1000,1000,357,-427,-604,-1000,260,1000,688,-1000,-1000,600,-239,321,-1000,-252,617,785,1000,142,-1000,693,1000,684,722,359,-1000,1000,-151,1000,127,1000,-970,1000,1000,-1000,433,-1000,-1000,1000,21,-1000,-1000,-141,1000,-609,-673,-845,-54,-201,-449,872,-80,539,184,706,-532,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{686,-147,-92,71,981,-764,500,1000,1000,-765,-156,-1000,551,-244,1000,-690,5,943,-547,448,-1000,716,-22,604,452,100,-495,-379,354,642,1000,401,-1000,-124,940,550,70,1000,-40,1000,1000,-791,1000,364,-651,1000,-855,-369,-1000,1000,204,23,-284,-338,-1000,57,-1000,717,-1000,778,221,-1000,901,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{429,6,89,137,-423,-1000,-1000,1000,316,469,-1000,-412,-312,478,-198,-698,-892,-148,-464,-242,-868,-1000,768,-21,1000,-959,-698,713,61,62,356,-621,-520,1000,-1000,1000,-623,634,-1000,1000,-371,-1000,-85,1000,-307,822,626,-1000,-205,1000,668,-430,-1000,41,424,-131,306,-329,612,363,-42,1000,-958,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{921,-44,404,459,-1000,253,-359,-765,872,-366,52,873,-1000,291,-983,282,-7,381,-822,1000,-300,-526,726,-144,704,-86,-679,-313,-614,98,-479,-352,531,-483,32,-137,-496,-1000,-787,337,-807,404,-263,-272,956,231,1000,91,1000,-177,380,-1000,-277,821,-446,307,-878,42,911,753,821,860,-572,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{590,-79,-1000,186,1000,-1000,318,1000,467,276,-265,-1000,330,-471,1000,-183,-635,794,258,866,-1000,397,-327,1000,1000,-1000,139,-629,1000,1000,1000,-72,-1000,-1000,-1000,1000,-37,1000,94,989,1000,-1000,1000,1000,-54,1000,-1000,-1000,-1000,1000,468,-607,-594,-1000,-519,421,-1000,825,-1000,1000,-455,-754,-139,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{686,478,436,170,-1000,-475,59,-580,1000,-300,-15,893,-1000,972,-1000,-444,-1000,-8,342,279,73,716,775,-378,452,364,-1000,191,466,350,-240,833,719,-171,-642,-756,-294,-292,-309,-1000,-556,122,-961,1000,468,1000,454,365,-1000,-824,-695,-947,454,234,619,794,-228,351,749,-420,763,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{930,-1000,-61,-991,-369,-667,-400,85,923,-620,-775,447,81,917,-906,-1000,-573,-99,-640,-1000,-130,-893,497,-1000,1000,732,-847,373,-654,-393,-265,-32,22,450,-119,59,-113,448,-777,473,-23,-384,-304,1000,318,1000,377,-153,18,-1000,193,-1000,-1000,916,-1000,-1000,38,-1000,400,669,961,619,-1000,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-626,-234,-284,-770,827,28,-656,-26,826,235,-296,-131,205,-826,831,65,-216,466,-720,863,63,1000,-83,-270,361,-1000,419,-285,-282,79,403,-447,-95,155,-91,15,-129,-434,262,730,-90,-424,1000,-445,-59,274,-268,277,63,531,59,857,-465,466,-294,761,561,-251,-331,-256,137,-694,898,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-883,146,-912,-339,-502,842,-529,922,-455,-1000,707,-761,-852,417,211,730,-701,-1000,-23,621,296,-408,1000,-1000,277,-526,12,148,-203,81,463,-523,-362,-996,-367,649,-879,997,298,501,-993,-354,-1000,304,639,-514,572,-300,-13,-121,-250,-1000,687,340,-695,956,547,1000,214,-1000,971,92,1000,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-338,856,-833,762,-1000,-889,-586,1000,185,511,1000,-1000,-1000,1000,510,730,768,-1000,63,1000,-379,-709,176,-1000,659,403,-494,1000,1000,-990,1000,-968,-288,-208,459,1000,-1000,1000,196,1000,329,801,-1000,-353,1000,-1000,547,-805,753,1000,-1000,-1000,687,1000,-1000,705,101,1000,136,-1000,852,628,1000,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-100,-195,-628,663,-354,440,19,586,907,226,344,676,4,-537,-934,-994,292,634,601,7,-654,525,-708,-620,-441,463,-385,-306,658,299,-10,119,-796,-157,253,61,964,502,584,580,-809,-363,758,-563,-683,746,941,-609,335,23,941,883,-749,54,-188,-287,-654,458,29,-577,-244,-874,53,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-738,-1000,-1000,-102,-1000,1000,-670,1000,213,205,779,851,-313,-83,971,52,304,-833,387,63,-704,911,1000,-1000,872,-202,-525,-1000,-1000,258,1000,-1000,-554,-1000,-637,-230,-1000,553,1000,876,-693,-1000,-1000,598,1000,-1000,700,-510,96,-196,350,-1000,105,1000,-786,1000,363,1000,-1000,39,896,974,1000,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-311,268,-813,704,-1000,1000,-409,1000,333,1000,1000,-1000,-866,572,739,417,1000,-698,236,1000,-820,361,1000,-1000,626,450,-1000,-203,534,-65,1000,-954,-1000,-1000,-731,820,-1000,878,1000,1000,460,-398,-1000,-432,1000,-1000,352,-722,-61,1000,-790,-1000,-1000,1000,-1000,1000,373,1000,-165,-1000,789,1000,1000,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-311,-351,-1000,781,-501,656,127,438,482,-244,-134,0,-703,174,-1000,-254,631,-553,1000,491,84,-474,-336,-1000,0,297,104,-517,690,-60,0,-464,-1000,-1000,580,210,1000,1000,322,954,-299,647,-510,-101,-1000,-116,1000,-1000,278,512,246,1000,-364,-28,-1000,434,-447,925,1000,-1000,53,-1000,817,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-401,-31,-951,599,-1000,-1000,-1000,1000,409,-721,951,-1000,-1000,1000,665,890,768,-727,598,1000,-503,-580,176,-1000,921,234,-162,663,168,-405,1000,-1000,-288,-208,1000,1000,-1000,1000,196,1000,22,1000,-1000,358,1000,-1000,1000,-1000,602,407,-616,-1000,-1000,1000,-1000,705,231,1000,-334,-1000,1000,628,1000,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-801,-772,-911,173,-895,731,-1000,984,0,-1000,836,-1000,-519,862,651,897,-17,-819,557,1000,-293,-618,1000,-1000,903,-103,-125,329,-311,-53,887,-916,-554,-997,-201,813,-975,1000,1000,895,-995,-398,-1000,732,924,-898,1000,-989,447,203,-465,-1000,254,862,-936,969,458,1000,-21,-1000,1000,504,1000,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{877,-2,-462,-838,-454,828,-530,1000,-619,449,-748,277,-485,20,-711,-1000,-1000,-1000,-461,1000,-996,-842,-74,-979,274,66,169,505,-21,18,1000,985,-1000,1000,-11,1000,-769,-39,-718,-858,-1000,603,880,808,1000,1000,-663,76,144,546,-592,-949,-72,-14,632,553,321,1000,-395,-1000,741,1000,569,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{877,-255,811,806,-243,-315,-652,363,-349,603,4,389,-597,-888,-401,174,1000,1000,-826,87,1000,1000,-245,1000,-1000,-26,-239,-505,1000,-605,-166,-952,435,-683,-1000,429,-1000,937,-274,-1000,482,278,179,807,916,109,1000,-349,1000,773,-1000,918,147,475,-17,-593,763,-717,-1000,309,457,339,223,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{511,-427,614,733,1000,-51,-490,13,-920,-760,944,-594,-1000,-1000,-1000,-1000,612,277,-1000,-1000,1000,940,-566,1000,227,1000,245,875,753,-714,-407,-964,932,184,-624,366,-1000,-39,710,-1000,545,-997,1000,-426,196,-327,875,-581,1000,-1000,-583,-321,-23,1000,-788,-945,-559,-189,317,-252,-1000,-19,1000,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{142,-523,391,-689,-1000,-559,-571,468,320,-1000,414,-869,-1000,-1000,-470,-1000,-400,-226,-949,-129,105,1000,48,20,-1000,326,874,762,1000,-605,-365,-1000,513,-683,-1000,1,261,-457,-274,-1000,833,-21,1000,-619,-166,-1000,823,-939,1000,-239,-101,-243,-273,-320,-238,439,229,-1000,1000,309,-1000,-204,1000,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{877,868,172,-798,1000,-392,-114,-1000,-3,1000,1000,365,445,-1000,-1000,624,-51,1000,473,-1000,256,-693,-907,-354,40,-26,502,-243,826,1000,-413,1000,695,570,245,442,-268,937,285,-1000,511,129,1000,-1000,196,-9,266,-880,587,773,911,-717,1000,475,-996,-1000,716,-1000,-345,1000,-990,-1000,-204,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-97,162,-130,362,-604,-514,1000,448,1000,-1000,727,-370,-341,308,-598,-798,-1000,-1000,-883,1000,-279,766,-676,-1000,139,-20,-79,944,-842,-400,-1000,-884,305,-1000,746,-495,-48,-174,-366,-1000,833,1000,1000,-456,-1000,-779,207,-587,-6,60,851,-299,-400,-886,-288,1000,578,-1000,143,545,-1000,77,831,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{558,-1000,348,557,-360,-280,-946,1000,-539,-373,-1000,-380,-327,-134,668,-642,175,1000,-1000,1000,449,1000,640,666,-1000,533,1000,-754,146,215,526,-1000,657,-400,-611,486,-668,641,1000,-585,880,287,-850,472,588,1000,1000,400,390,400,-247,352,-1000,764,808,-615,-199,732,-15,-690,-903,-430,1000,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{877,-1000,621,806,174,-495,-1000,1000,-1000,933,-1000,389,-325,-134,1000,-1000,263,1000,-718,1000,1000,901,1000,1000,-748,1000,1000,-1000,1000,-605,526,-522,1000,1000,-1000,477,-1000,1000,1000,-370,1000,-1000,-1000,726,588,1000,477,400,397,-1000,-1000,545,-1000,1000,1000,-615,-1000,1000,-1000,-1000,1000,339,647,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{137,255,1000,-345,253,-834,-350,663,-648,1000,1000,1000,-668,-1000,-732,-973,1000,1000,-814,-765,1000,159,-641,1000,-777,1000,1000,-901,-291,184,-402,-6,1000,1000,-723,610,-1000,-107,835,-542,599,-1000,440,-458,24,1000,1000,-421,990,-1000,-661,-859,-1000,1000,97,-1000,-1000,952,-1000,-580,-123,-350,-635,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-210,688,-1000,755,-1000,383,85,668,569,-1000,351,-527,629,551,-124,-416,-1000,-833,-603,1000,-666,667,-650,-1000,758,-101,-618,1000,871,-811,-82,-545,212,-1000,1000,-1000,75,-747,-64,-920,711,1000,488,-125,-1000,-1000,38,-660,-740,807,1000,-180,-196,-1000,-825,1000,899,-477,785,595,-1000,-773,1000,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-867,-421,806,159,-506,-877,260,-1000,-281,-706,377,320,44,-400,108,-987,1000,453,-578,-221,604,798,-442,676,-27,533,-281,-664,-374,251,-510,-1000,725,-532,-653,77,-906,134,136,321,813,287,172,149,-20,409,20,-671,523,400,-762,-303,1000,264,438,-187,-98,-52,-505,-117,268,403,-645,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-994,326,1000,-96,1000,-1000,1000,-1000,1000,648,694,579,18,-1000,-1000,-681,966,-591,-92,-1000,217,387,352,592,-34,-781,-357,-104,-971,1000,-373,-1000,766,-248,-446,1000,-550,-119,-460,575,933,-93,1000,33,15,583,-1000,-353,1000,255,647,-462,-204,-280,-1000,-909,440,-777,-1000,852,1000,-20,-950,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getLocale():java.util.Locale",
            new int[]{-699,311,-158,-714,-140,-720,-756,-217,695,605,-30,-87,-834,669,-384,-799,-455,131,-913,-4,463,-255,991,324,752,-935,-111,535,-796,320,298,-801,-509,216,-759,-351,38,230,849,743,1,-909,991,-20,644,-441,281,-110,432,-622,-96,-158,-121,-697,665,799,882,-485,-560,378,594,-623,750,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getLocale():java.util.Locale",
            new int[]{204,502,593,-483,-36,-594,839,-881,-302,-368,-464,29,686,179,-746,930,277,-713,279,-534,899,213,-129,-256,225,487,-917,267,-831,319,715,-283,-872,-667,-158,827,-599,-408,70,-120,103,-979,173,817,-973,740,-903,848,906,590,432,542,313,-835,432,-102,-971,63,384,622,-851,709,736,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzM=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getMaxLengthEstimate():int",
            new int[]{214,-559,-226,993,824,221,-391,132,184,-306,159,-283,-259,-53,944,287,-390,-937,-61,665,776,259,120,487,930,-104,74,-906,-563,-822,139,-252,326,669,-17,704,65,103,-586,-653,-626,401,-242,262,-638,279,178,913,-151,396,-54,-589,-73,-408,-129,-239,-500,438,-170,912,-150,-929,678,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTc=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getMaxLengthEstimate():int",
            new int[]{-223,-380,73,771,288,-277,-127,-103,-742,922,659,-769,-889,202,831,503,-614,-652,81,878,430,-491,901,-262,911,-845,-876,371,754,826,57,-880,406,358,-842,-917,-700,-381,123,113,795,235,769,-416,-36,-746,161,-868,154,263,-939,-569,413,-946,-57,-61,618,-351,609,-471,379,196,722,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:RUVFLCBkIE1NTSB5eXl5IEhIOm1tOnNzIFo=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getPattern():java.lang.String",
            new int[]{-372,-793,-277,966,-845,-410,558,-551,459,-314,-401,288,-354,956,171,249,-557,-930,130,637,119,512,469,-858,106,-690,-284,834,-313,267,812,-136,-695,-873,-281,147,-460,3,-85,859,584,92,149,911,-788,-796,350,-252,625,960,426,-204,-677,-342,836,663,-796,-273,274,807,464,-525,608,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:TU0vZGQveXkgSEg6bW06c3M=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getPattern():java.lang.String",
            new int[]{436,331,-152,-122,-171,69,-290,-239,366,-714,-199,127,394,-737,-782,-124,-245,-993,-37,513,848,-705,-524,-799,912,-992,-753,262,143,121,874,129,-616,747,568,518,-327,515,953,-906,-300,-695,-726,291,380,805,-384,-920,139,743,-413,-511,293,-271,-930,-558,938,632,109,116,134,135,774,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-254,301,-769,-577,-367,30,-309,-66,-621,651,406,-862,-983,-263,298,-172,194,561,-111,-582,686,366,76,-311,-535,349,540,664,-678,433,-397,-858,35,-146,325,666,-414,-439,210,-82,52,483,22,905,430,821,889,675,887,-301,137,758,-290,-242,-906,-958,-426,-452,505,-616,-348,-33,-884,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-138,-95,-699,881,273,-456,-121,343,-148,589,-312,378,60,400,-482,-498,-117,-335,-958,-149,-660,991,-148,-675,-73,955,-20,600,-919,154,-105,218,619,-248,-784,471,-848,626,617,930,-531,325,746,-911,41,-272,-631,849,933,-416,-607,-684,684,873,-26,764,-25,355,443,-494,301,-412,485,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-673,-119,-119,-330,-554,949,357,-934,5,-986,54,-799,264,-193,126,-329,-615,-952,811,335,-195,-922,361,-748,766,-879,-101,-454,79,-294,-467,790,-315,-20,-298,153,836,235,183,520,-130,832,-570,-935,-566,867,994,343,739,-537,-323,878,8,-683,-256,-581,570,311,-964,588,-713,385,158,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{201,-419,-467,194,-702,271,-837,193,-384,-105,-1000,913,1000,828,669,434,1000,-1000,65,-1000,-1000,-909,-744,-110,-691,57,-116,-541,-59,-424,159,-259,32,-427,875,571,215,145,-744,736,-1000,-1000,-781,-656,462,281,-1000,227,-150,-391,378,-158,288,-313,-203,875,-858,-27,-606,-315,-1000,-1000,-104,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{-967,614,845,-729,203,811,-520,522,133,342,-958,-647,-949,804,882,189,763,-743,-275,851,-239,-847,632,270,-234,985,281,772,783,-570,-531,646,-548,-130,861,524,570,-854,-501,-313,-1,-864,-650,-163,870,-667,-751,102,-10,-5,18,75,-340,607,-771,-449,-271,-571,-874,291,-356,719,543,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{-631,658,709,988,825,8,926,-533,550,741,66,-402,-397,972,-683,-29,602,-811,-400,30,-547,255,921,-667,-185,289,-746,341,-42,-454,-55,287,401,-474,772,509,788,-510,263,-812,-178,355,-371,-114,757,457,-209,-509,939,101,345,-781,-782,3,-949,-831,-373,714,440,615,-298,-15,473,670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZoneOverridesCalendar():boolean",
            new int[]{322,-430,178,-729,-20,-845,-502,-241,531,-31,928,-574,543,956,-418,823,-692,631,363,251,-751,-500,-879,273,399,-860,119,520,913,774,-566,-244,-572,601,-938,-828,-11,-845,-318,381,283,-459,-662,-306,930,492,585,919,-270,922,71,138,-93,860,-23,-287,-352,-509,221,-912,-476,417,-23,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZoneOverridesCalendar():boolean",
            new int[]{-52,-668,937,797,467,712,-79,833,-201,-100,-62,-194,434,-308,799,-188,-544,-679,502,467,-60,-673,-207,933,816,-720,-448,-445,-603,-562,868,330,631,-144,820,423,59,477,189,454,-540,-712,581,-610,-806,745,-219,527,-636,-613,-449,272,-868,-221,-467,-781,-304,-241,442,-648,-207,-660,-689,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-810,-925,-639,843,-82,-201,213,-434,-41,494,70,980,-304,-365,-688,915,512,-822,188,-689,748,-136,527,165,693,701,-467,-906,-496,-380,-995,-817,-859,-881,-627,759,857,286,-707,-548,534,-977,268,-487,281,-483,-303,-265,-917,47,-882,-508,-985,-929,993,474,-446,-812,-674,-542,870,-926,652,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{571,-383,-630,-17,195,-18,-19,-297,-521,723,-364,-382,-916,-400,451,-367,360,-24,672,-33,-371,-360,519,735,-588,-779,-609,-343,-283,871,948,-755,643,729,660,-210,15,235,-250,-787,564,-194,178,-568,219,-388,-731,-744,505,227,-69,53,-250,770,992,411,-180,-144,149,-143,-563,-893,-754,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:RmFzdERhdGVGb3JtYXRbRUVFLCBkIE1NTSB5eXl5IEhIOm1tOnNzIFpd", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "toString():java.lang.String",
            new int[]{-667,-388,279,738,-994,-54,962,-949,667,-906,225,-350,-231,754,-683,126,25,345,367,977,112,-949,675,329,-690,-420,239,925,-772,-211,-216,-21,202,98,759,296,503,961,84,457,50,-406,780,771,-42,602,-944,235,998,-808,887,-213,-252,265,698,769,-888,-191,-660,700,-275,779,271,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.String:RmFzdERhdGVGb3JtYXRbTU0vZGQveXkgSEg6bW06c3Nd", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "toString():java.lang.String",
            new int[]{739,-335,183,590,422,-855,-292,609,117,731,-241,565,611,-709,-254,-4,664,531,-356,782,-817,-375,324,-904,-283,-982,353,-228,807,-345,332,-739,97,645,-904,184,703,-264,405,853,857,882,99,-912,535,-362,134,-868,-554,175,-627,-437,-454,-720,-396,705,66,894,801,243,-59,-580,387,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{990,786,-496,-419,-551,330,-712,801,-1000,181,548,1000,269,1000,-841,-1000,1000,-86,42,845,-927,660,157,1000,824,1000,-27,-819,-1000,-1000,210,-392,-874,-1000,-1000,-761,-1000,-862,1000,394,609,-590,-656,953,-1000,1000,1000,198,-681,-918,654,-1000,1000,-377,-969,-1000,1000,811,-769,-1000,-1000,-1000,1000,640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.String:IDgyMSA=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{746,821,-208,-527,-766,624,-824,-264,-491,-131,-578,-789,462,-570,-856,-147,-769,-550,-947,-410,520,-868,-385,440,652,-844,214,-640,-254,-959,-202,301,-951,-339,839,286,962,395,17,969,563,950,544,99,-454,-270,267,595,416,-132,766,515,495,460,-438,-892,-149,407,241,358,594,-651,-210,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{62,436,-809,58,-149,685,-798,261,-1000,569,970,1000,44,1000,-318,130,1000,638,-671,809,-355,77,-337,-25,1000,1000,161,-1000,416,-400,544,-826,-158,-565,-1000,-833,-874,-1000,1000,576,589,-199,-493,694,-415,96,804,236,-776,-9,639,-779,996,-432,-1000,-1000,400,147,-838,-478,59,-604,891,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{558,1000,1000,728,1000,-226,184,117,-1000,-1000,322,-267,1000,-1000,1000,-729,-694,-929,-504,-506,-1000,403,888,561,-1000,-1000,-692,1000,-254,1000,-505,-724,-346,1000,931,-188,431,-507,-1000,-1000,-68,353,-78,438,-1000,1000,-148,-632,-888,-514,691,1000,-530,406,18,1000,734,345,-336,656,255,1000,-185,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{558,1000,-806,-946,957,1000,-311,217,-1000,645,-834,515,394,823,-421,487,778,638,-579,-110,-513,-375,-355,575,82,778,449,-1000,522,-770,1000,-1000,-336,-1000,-1000,883,-228,-396,1000,756,-254,-199,-645,754,-415,-485,750,-645,-776,1000,639,-444,819,-155,-1000,-1000,-47,-563,-1000,-278,-560,-505,897,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{494,-1000,-1000,-847,-598,-1000,-363,-1000,-400,1000,825,333,-1000,1000,-1000,282,685,-429,-363,1000,-1000,29,-962,-738,265,-1000,-920,-81,-303,1000,-670,-1000,1000,611,-1000,1000,828,-556,522,-1000,-586,-127,-1000,398,818,435,1000,6,-1000,-1000,406,-1000,1000,-989,223,-1000,-1000,599,-1000,-1000,382,-1000,218,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{716,618,181,-808,-32,645,333,476,-721,-200,-971,222,654,-122,175,193,505,923,-756,949,255,502,12,-956,509,-408,-275,-876,-418,-987,562,-442,-165,-691,125,-430,754,-813,297,-506,-19,-776,274,-272,629,371,-194,241,-876,-984,795,-908,534,-190,-741,-711,-103,322,-645,556,417,-343,705,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-563,-1000,-173,682,-204,964,-1000,-930,-741,184,-1000,-1000,-1000,-666,1000,-222,-834,1000,-1000,1000,-373,249,-989,550,782,-798,135,-1000,385,-296,-134,-915,-1000,-1000,-1000,-555,918,-869,279,672,-1000,463,1000,28,-1000,71,-270,377,-715,13,-949,84,353,-755,-273,1000,474,-359,58,-808,801,-1000,393,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-371,-673,-275,-558,-718,-1000,-1000,-1000,-1000,297,-1000,-1000,-1000,460,848,-1,-1000,823,-1000,891,983,135,-1000,1000,971,-607,350,-1000,628,-241,-344,-1000,-450,-436,-1000,-631,463,-740,607,313,-505,451,1000,358,-1000,-658,-255,890,-1000,168,-584,3,-484,-1000,-801,1000,259,-902,-487,-1000,837,-335,413,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.String:ODMxLjQyMw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{549,-831,440,423,19,163,-293,769,-703,-153,-935,730,263,921,504,-394,799,-31,461,42,234,-792,924,-187,485,637,-866,-494,-922,-128,-694,-3,-719,860,-756,197,951,484,501,-993,-621,-428,831,-393,405,272,67,-745,401,909,-707,-353,-691,473,174,-470,-123,-302,671,743,478,331,-982,-833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-627,588,-1000,61,-193,-1000,-228,-1000,-78,-744,400,-1000,-689,-1000,200,-1000,-1000,149,-848,548,-213,1000,-1000,568,-1000,-1000,1000,1000,1000,-1000,1000,-1000,-384,-807,-1000,-1000,-111,-736,-979,1000,-918,1000,1000,-666,-388,382,-203,1000,-877,-674,969,-842,884,1000,-807,522,558,1000,870,-1000,579,194,1000,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-978,1000,-1000,-540,-1000,-1000,-514,-831,1000,747,1000,-1000,1000,728,-1000,-525,-1000,1000,-1000,1000,1000,598,-1000,-171,-295,318,-951,-320,-276,-1000,215,-80,576,276,-25,-1000,421,-1000,-1000,706,-911,1000,-553,-1000,965,310,864,1000,-633,-1000,1000,-1000,410,1000,-1000,825,-673,558,-1000,-40,-304,-470,1000,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-708,652,-67,-699,856,861,875,367,-376,-164,866,-644,529,839,433,805,-382,825,-266,298,-465,-19,317,-163,974,-815,593,-789,-527,-676,624,941,-36,392,-696,984,-124,32,754,788,-754,746,882,544,-675,413,-580,487,-565,-84,-511,969,-530,728,-245,712,371,81,-409,77,-868,30,-713,538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{782,1000,-1000,-693,1000,-1000,-1000,-132,351,869,-1000,-310,-586,-261,302,-718,1000,-522,1000,420,-58,407,-988,-183,-1000,-1000,-1000,358,606,444,-1000,813,753,-1000,-1000,-916,453,-400,734,-655,-155,-1000,93,-661,414,-267,162,325,672,-1000,400,1000,580,-536,-898,769,442,-1000,565,-702,195,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{542,307,-53,97,261,-25,1000,399,-347,437,-1000,-505,-1000,-132,-428,34,425,-866,-645,-481,-277,-45,415,245,-1000,-267,400,185,-127,320,148,599,1000,-264,-1000,1,-256,-210,-468,344,421,-400,-321,914,-524,-469,-37,-577,739,-145,-694,-140,-400,-1000,1,214,-161,-166,-400,71,162,-919,-400,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-238,256,-891,287,235,-753,102,218,763,457,-1000,-138,-114,-566,-13,-1000,706,-618,-1000,-435,-342,906,-511,-347,1000,-187,-1000,-667,484,1000,209,457,-967,-1000,-403,1000,-609,-1000,340,1000,1000,364,-116,-33,1000,-694,-92,-1000,-198,-907,-1000,3,557,-1000,-715,-74,-1000,-840,903,1000,-73,-304,-388,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-465,-401,193,992,-1000,-249,1000,411,-153,559,-277,-291,1000,1000,-860,796,-1000,-245,-1000,-316,-496,-521,486,-1000,949,228,1000,1000,149,927,1000,-570,-1000,502,1000,1000,-1000,-687,-983,241,56,78,-152,1000,-1000,-485,323,-1000,-463,1000,-1000,265,-1000,-54,905,524,-1000,859,-1000,423,1000,436,45,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-322,464,-499,609,77,-392,433,-53,-257,167,-620,-298,718,-322,467,-571,26,92,-637,-924,-271,800,-205,729,501,-279,-19,-393,603,935,754,304,-990,-387,-576,230,-405,-962,4,880,810,451,-662,-855,67,80,-122,-784,186,-705,-630,786,664,-914,-428,-747,-698,-740,251,419,-248,-360,-807,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{847,4,-795,757,-39,749,197,616,435,587,497,-443,-428,406,-238,-240,-574,483,919,-576,244,794,-365,972,742,590,252,-688,-683,711,-322,648,148,-673,-5,-763,-663,905,147,-797,716,-936,930,-910,778,692,-210,164,-715,-816,375,-348,544,-978,707,-916,105,9,920,-317,656,-548,-974,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{254,343,56,-716,20,-651,-43,522,-512,924,-499,-1000,-1000,1000,-51,362,553,-1000,-378,-575,-731,-370,-14,-1000,400,903,1000,-505,-351,927,400,334,786,-264,-52,1000,-17,232,314,-149,-493,-1000,-592,162,-958,-466,-767,-167,1000,1000,-31,-222,-441,558,436,-204,171,859,-95,544,-1000,-849,-488,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{281,127,318,241,325,-754,-770,-617,-181,249,-499,-14,-1000,365,860,-58,-166,-31,781,636,-885,1000,-480,-898,-1000,-871,-1000,-291,1000,1000,-643,-555,421,-699,-438,-531,1000,-1000,340,-191,-5,-1000,561,-1000,442,48,851,634,531,-485,900,590,807,656,-1000,1000,1000,-262,1000,-739,-1000,836,-652,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-177,887,-1000,809,681,1000,-338,-63,445,813,-173,-401,887,-556,-482,-1000,1000,-763,1000,44,-472,-349,-1000,-225,414,-350,-1000,-526,-73,-96,-1000,-899,-242,-1000,-952,86,-527,-940,-799,-680,-190,-1000,466,30,1000,1000,-1000,-819,-579,-1000,1000,1000,400,-621,1000,-1000,-4,-509,469,-958,441,972,-1000,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{799,452,408,-512,1000,-936,-1000,-1000,84,139,-819,-136,-1000,365,1000,85,-611,26,1000,561,-581,1000,-120,290,-445,-843,-1000,-555,275,1000,62,283,974,-1000,400,-1000,1000,-518,567,-113,157,-1000,473,-1000,832,-313,-75,1000,51,-922,1000,639,1000,690,-1000,1000,1000,-688,118,-1000,-1000,-169,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{655,-1000,-471,243,-94,-883,1000,816,-193,897,689,-431,795,-1000,254,670,455,-145,-410,359,-634,1000,-754,87,-294,866,1000,-379,-141,211,-199,-312,-1000,-52,-536,-639,741,1000,-922,-48,222,-141,766,581,-295,-562,-311,794,-245,253,-952,-563,-632,-1000,33,614,-400,-494,-1000,499,-1000,-291,-44,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.String:Mzg3LjM2Nw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{277,387,-984,-633,-824,-266,-339,-41,240,-407,-746,507,-1,-880,211,-751,637,-92,-967,757,89,-41,227,54,-457,-693,535,949,627,-986,735,362,736,183,-964,-867,674,573,-662,-468,162,-586,-486,458,-427,119,-763,-763,754,-298,-129,-226,-254,958,480,882,-183,679,86,-413,329,560,983,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-417,374,-870,3,468,-1000,-179,550,-33,827,-562,-360,-734,-631,-294,1000,959,-208,-423,-3,-212,-270,755,-1000,-1000,463,212,1000,652,-1000,931,-620,-1000,-101,-447,-815,-361,-196,297,494,-1000,-1000,875,242,-1000,-821,924,-832,400,-671,-1000,586,366,1000,809,561,-879,-497,-786,-120,24,1000,769,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-417,16,-1000,62,267,-906,-1000,-517,577,436,-599,-672,120,-631,-294,976,-451,-1,13,-366,178,-346,759,-429,-553,317,212,397,652,-1000,931,791,554,-569,167,437,-361,145,-701,704,-567,-182,875,138,475,-325,-708,-835,1000,148,960,-346,-69,683,-1000,-45,-879,-738,-813,753,-268,1000,248,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{493,-1000,-471,-206,-242,-1000,788,489,-436,1000,1000,-1000,1000,-1000,891,-309,439,291,-1000,-1000,-1000,722,-610,583,162,1000,128,-1000,-1000,-386,-860,-1000,-341,-17,-400,-101,1000,-734,-1000,651,725,-1000,1000,-503,446,424,5,-15,463,1000,-1000,1000,-1000,-593,341,-94,951,-1000,-1000,1000,-321,-599,800,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-103,-1000,-1000,619,473,-460,720,-1000,39,853,556,-187,707,-144,411,7,372,199,-897,762,-428,562,-939,291,-1000,741,1000,482,227,-148,23,-17,-557,-762,-707,386,630,61,-759,-497,-714,-260,480,549,-689,-1000,-225,893,-544,-67,154,-400,-474,-263,-92,842,-1000,-277,-779,284,-430,13,-153,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{498,-786,705,-880,219,-889,-427,726,729,538,119,-449,817,-379,891,709,-205,361,-756,423,-792,792,-744,-4,-245,852,722,-591,-588,167,-272,325,-85,-133,-968,-65,-477,461,752,-164,313,-119,728,-87,93,-191,-705,76,504,618,-768,615,-141,-595,121,917,-168,-106,-504,354,-752,-699,793,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{526,-168,1000,90,-882,-984,1000,1000,-361,1000,-98,1000,-528,-658,-526,786,1000,199,-609,1000,-1000,1000,-59,-639,305,681,1000,-1000,-246,802,-1000,228,-1000,1000,236,-1000,-228,1000,-1000,-817,1000,141,474,698,-1000,-1000,-225,1000,-940,-836,-1000,848,312,-1000,1000,1000,288,1000,-84,174,-982,-534,73,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{431,-1000,-992,242,275,-918,46,271,464,436,783,-1000,120,-631,478,750,-518,660,13,-1000,-846,1000,-818,-190,-395,-319,999,-475,652,-405,1000,-518,551,-336,167,-1000,732,145,-701,1000,-144,-398,837,-1000,1000,-116,-1000,-835,-26,606,519,-427,-742,41,-228,-89,470,-646,-1000,649,-1000,41,919,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{286,-491,209,353,1000,-1000,-307,-666,101,625,-586,1000,-59,-1000,-897,1000,1000,-1000,-811,-1000,-971,1000,786,-1000,-871,682,1000,-955,1000,-671,837,1000,-341,-248,-479,-970,-1000,1000,-1000,-773,-936,-1000,1000,139,-1000,-488,-482,182,-777,-253,-1000,1000,-488,-1000,-1000,-255,-567,-608,-1000,1000,-644,1000,800,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{173,-1000,-3,-203,600,-1000,1000,623,234,1000,1000,-1000,1000,-1000,333,-483,935,-253,-1000,262,-1000,1000,-1000,342,-1000,1000,1000,483,-510,-57,-1000,-1000,-81,-1000,-1000,-688,1000,315,-1000,-822,123,-1000,1000,-503,-186,-764,555,181,491,1000,-1000,1000,-1000,-1000,117,721,-41,-1000,-1000,-14,-273,1000,172,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.String:MTZfMVw=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{795,-579,524,1000,-45,-669,1000,-1000,640,-642,-1000,-820,-499,272,624,-664,-1000,633,456,-670,1000,-1000,-149,-890,853,-727,-506,25,995,376,1000,-1000,-274,1000,1000,-273,-568,-751,1000,481,580,102,-524,-163,1000,703,-512,1000,666,752,723,-415,352,844,-828,-56,-894,952,-1000,-16,201,-931,793,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-307,1000,326,-358,-714,1000,-596,-527,-264,-193,384,241,886,834,-711,1000,593,1000,-1000,1000,922,804,-1000,1000,-1000,1000,1000,745,107,988,-368,-920,758,1000,1000,-966,-523,352,-626,-1000,1000,-1000,-545,829,646,137,979,-325,248,36,160,-1000,-462,1000,-1000,-373,-939,-1000,588,-1000,-92,1000,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{72,478,1000,511,597,1000,1000,-1000,432,-688,-487,922,-72,981,-1000,-766,1000,-19,362,-1000,634,1000,1000,-890,1000,-1000,-1000,1000,269,-621,630,-1000,-400,-707,1000,1000,-132,-601,372,1000,-1000,399,-400,37,1000,833,-1000,-82,1000,-152,198,1000,1000,-584,-400,-698,-338,560,-1000,-1000,-271,-864,107,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{141,-803,1000,326,-290,-484,1000,-1000,536,-877,-1000,-1000,-444,456,1000,-666,-1000,545,456,-670,1000,-1000,372,1000,1000,-939,-1000,-878,-3,1000,1000,-1000,-617,1000,1000,347,-584,-114,692,940,173,-297,-887,322,936,-51,-680,884,265,489,673,-580,1000,317,-103,-689,-894,1000,-1000,-612,137,-1000,294,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{239,-750,-330,722,-616,-619,617,-64,158,398,-748,-130,-255,-215,495,652,399,-905,-25,-871,524,-637,286,-900,-800,-968,41,119,-356,-885,37,-445,859,395,19,335,-255,-533,985,-554,351,154,-267,-331,618,405,475,-761,-69,174,703,-815,-843,983,-754,-52,406,859,-570,-480,736,-821,-340,884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-103,-338,594,-1000,-153,-820,-1000,1000,914,-379,-739,-1000,-987,697,307,-340,785,1000,-943,1000,-400,365,-564,-1000,1000,1000,189,427,-991,-839,1000,1000,-924,982,-816,86,-1000,480,423,-465,1000,656,393,1000,272,-143,-1000,-472,1000,-1000,-1000,1000,-1000,-1000,-470,-647,-1000,-1000,-176,628,-1000,359,670,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-5,-545,1000,55,73,-1000,-29,29,614,-1000,-742,-6,-987,792,1000,-1000,-283,820,-400,-1000,-400,656,756,74,1000,-281,-1000,-355,-1000,1000,-648,-286,-1000,617,108,907,-477,-90,143,675,181,307,-91,1000,478,-121,-1000,-387,8,1000,1000,1000,1000,-678,-656,-172,1000,269,-400,-883,-797,-1000,-1000,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.String:LTUxNTM=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-502,201,515,601,-209,-1000,-947,-570,-983,-441,341,1000,-798,-444,527,-761,261,-1000,-1000,95,33,1000,210,-1000,585,786,-704,-1000,347,1000,-100,1000,-1000,1000,357,167,-555,1000,-1000,-727,-778,-355,-959,-879,-921,-1000,328,-950,1000,-1000,-316,-1000,-709,-410,1000,1000,-598,-817,-1000,819,-1000,67,-1000,148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{867,733,-800,-250,89,745,20,-273,865,764,690,-266,-206,379,-691,912,-1000,619,523,-819,-507,-301,624,747,-377,-327,-27,1000,1000,-855,-793,884,706,195,-322,591,1000,-879,380,-329,-836,327,810,-309,1000,20,-1000,-573,336,266,355,814,163,-400,-20,-126,400,775,1000,-841,1000,-926,1000,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,847,959,919,610,1000,-378,0,0,-1000,350,-266,1000,-1000,206,1000,-806,0,300,1000,-313,-247,1000,-1000,-626,0,-372,-476,308,249,445,-939,0,-378,140,1000,984,1000,-600,1000,-175,362,-919,-212,1000,-1000,-1000,-644,155,0,-1000,-1000,-252,-360,1000,273,-348,-151,-1000,925,-720,0,118,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-307,333,920,32,197,531,-331,-313,-1000,639,170,-955,-1000,-1000,1000,-1000,-1000,892,-1000,-1000,1000,297,181,747,-1000,895,-276,-668,-608,1000,1000,912,-567,805,945,-12,-608,1000,-1000,-1000,674,768,98,-781,-1000,115,-129,-90,784,-707,-457,1000,-927,138,-211,-33,-872,360,-20,1000,456,829,-799,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-150,130,-557,-1000,-98,-1000,1000,704,-353,1000,1000,-546,-1000,-1000,469,-1000,-458,-399,718,731,259,421,-444,502,-665,-330,1000,-45,-507,724,-632,1000,243,269,-1000,-994,-315,363,-319,-469,-109,-284,-601,-671,-450,326,191,-952,-282,-1000,1000,-143,122,717,-193,257,-919,993,-460,-208,103,286,219,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,-113,1000,210,-329,-1000,1000,-716,-1000,-196,-282,1000,-1000,-568,1000,-1000,754,-1000,-1000,523,904,1000,-754,-1000,-1000,1000,-1000,-1000,-625,1000,922,1000,-1000,1000,-1000,-545,-1000,1000,-1000,-1000,-364,-1000,-1000,-1000,-1000,-1000,591,-480,1000,-801,-1000,-1000,-1000,-275,1000,1000,-261,-1000,-1000,1000,-1000,561,-1000,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{568,-227,434,-806,-528,330,-186,-371,-844,1000,-297,-333,-685,66,189,-1000,648,-258,-408,-149,-137,-345,-886,95,1000,-729,233,725,347,1000,-624,101,-665,-313,-253,565,540,312,-486,-131,-589,401,217,293,74,781,986,386,-788,-955,496,38,921,372,565,177,739,-387,400,413,-688,-1000,-593,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-632,13,697,361,-209,-1000,241,-652,808,-587,-722,744,-1000,-829,-726,-591,-54,-1000,-977,281,-126,1000,362,-1000,344,250,-1000,-525,168,1000,-131,776,-1000,328,886,540,-940,1000,114,-119,447,-124,-1000,-220,-1000,222,515,-1000,436,-785,-459,-753,-651,-773,912,400,-607,-1000,-1000,-773,-1000,181,-914,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,589,273,-207,797,1000,-100,-1000,-926,788,-171,400,-1000,184,-776,624,1000,80,967,1000,295,670,-1000,-370,717,1000,1000,755,433,-342,857,477,-1000,-160,791,-894,100,-941,-597,-288,-874,1000,-936,1000,861,-1000,23,-1000,1000,-1000,1000,1000,-1000,-1000,-948,1000,1000,628,1000,416,1000,871,378,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.String:MDErLjYwMQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-342,-18,335,-409,682,-824,-638,-861,-220,1000,-475,119,-533,166,-1000,457,-612,-975,-400,1000,436,51,537,-1000,-34,362,755,143,542,-561,489,-199,-567,330,241,1000,-1000,-1000,92,-531,1000,-324,417,862,526,166,445,138,156,-416,626,-267,6,31,-1000,-673,124,-731,-170,218,-754,-242,-792,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-32,-114,-832,799,-382,571,673,-778,-893,33,1000,-580,183,341,-493,24,-152,-605,647,-534,-56,886,-464,525,567,955,900,872,-300,91,360,580,-292,590,-301,889,-453,493,1000,498,-602,-319,792,1000,-580,887,783,231,-501,509,-580,-219,591,-11,-4,821,-236,657,-478,574,385,-756,456,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-459,-18,-72,789,591,-429,233,-478,-552,-378,62,387,183,-29,-1000,24,-990,-284,428,1000,303,405,459,186,567,-350,927,675,-651,235,297,59,-426,403,-396,303,-1000,-772,898,53,-602,-390,225,275,449,196,853,-779,-745,116,444,-340,471,-11,400,821,16,-389,491,488,828,883,508,628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.String:OTcyMg==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-355,281,972,808,715,-1000,589,-248,-1000,486,245,-1000,-1000,680,-718,318,-1000,-1000,-808,350,-36,471,396,-114,615,-1000,927,675,-260,310,922,1000,-1000,1000,-520,303,-1000,-1000,1000,-172,-952,187,608,1000,563,40,853,-569,-958,395,206,-1000,570,-604,-1000,718,-140,-963,1000,488,426,-540,477,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.String:NjYw", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-529,653,-1000,1000,603,1000,1000,-656,-1000,89,1000,-1000,-1000,1000,-521,65,-406,0,-505,-1000,298,635,-478,1000,1000,-78,-1000,-595,-1000,296,265,1000,-774,1000,383,-895,-595,1000,1000,1000,-1000,-1000,1000,993,-1000,912,-420,778,-1000,1000,-1000,-208,638,1000,-310,267,713,1000,1000,1000,386,-1000,1000,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-304,-787,495,1000,192,-678,1000,-343,-1000,508,-1000,568,618,-808,540,-1000,-573,-1000,-302,86,1000,752,100,-310,-152,-357,-1000,-1000,-1000,-205,-271,1000,1000,1000,-1000,51,-364,-1000,1000,312,-400,1000,-559,-1000,-169,1000,1000,-1000,-61,-1000,-39,-1000,1000,1000,376,1000,-1000,1000,1000,-1000,714,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-305,-131,1000,1000,548,558,894,152,-1000,515,1000,-1000,-1000,594,-314,-824,-737,-686,-1000,-897,94,207,358,1000,1000,-355,-866,799,1000,102,1000,-436,-190,721,-349,-1000,840,-893,-453,-217,-1000,912,1000,427,-1000,404,1000,481,-720,274,-1000,277,304,-1000,-280,1000,722,768,1000,1000,-991,-764,180,800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{101,46,-55,-63,-612,-462,877,683,-1000,851,676,582,529,-293,-179,-755,835,-1000,-267,-590,-64,-471,-525,-788,-1000,-763,-446,371,-1000,-1000,-623,180,-587,-582,732,-560,-788,14,-982,1000,-878,950,458,686,-1000,662,-393,180,1000,-922,1000,22,-630,179,1000,922,-1000,684,94,301,881,293,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{65,669,-402,-126,-411,-881,-652,-912,733,-788,-964,-83,-376,23,-945,-943,-775,-730,-373,-490,650,-635,-250,-68,238,-268,-569,151,299,701,776,740,341,-853,849,13,-153,656,850,101,-773,-559,134,879,441,-481,-138,402,960,696,-332,-726,235,820,-370,-105,658,-546,-975,-131,-909,-395,909,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-1000,-577,107,-182,-663,-607,1000,685,794,-563,1000,913,1000,-33,311,-1000,1000,-265,-593,139,950,1000,-169,-658,512,-1000,-835,342,-1000,-335,-1000,1000,980,-444,350,91,-954,-689,-1000,682,207,1000,936,939,-1000,1000,-393,700,-195,-1000,1000,-203,-1000,802,1000,880,-1000,845,1000,1000,1000,1000,-994,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.String:Xzk2MTYJXzY=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{981,-113,-664,-850,708,-1000,900,1000,-922,1000,637,-1000,1000,567,-951,-1000,-1000,-1000,744,589,-423,152,1000,-270,-784,-127,831,1000,-286,553,-705,-1000,-63,-351,1000,198,-62,944,-1000,613,-543,948,19,304,69,-1000,253,1000,-1000,-1000,-22,473,779,-1000,1000,1000,-214,-844,-271,48,6,-661,-1000,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDM=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-1000,105,1000,-980,-353,739,148,879,-293,-299,453,-363,-1000,-504,1000,-866,754,-957,610,333,-907,708,172,72,713,-824,572,375,7,749,789,-7,-1000,717,398,1000,-562,-502,837,-56,-1000,-937,-147,-270,-921,-390,-389,-1000,476,377,579,-613,-80,-1000,-121,757,464,-337,310,575,217,-941,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-590,-834,861,599,1000,199,141,1000,1000,119,-1000,-125,-381,513,80,721,431,607,-1000,1000,193,181,-106,-367,1000,1000,-1000,858,-1000,116,-400,824,1000,-499,935,406,-430,-594,-400,-1000,103,-1000,-409,-352,-1000,-1000,-1000,-737,742,-546,292,1000,0,204,-1000,-161,1000,-812,410,593,293,184,-241,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-1000,383,1000,1000,-134,-416,962,1000,1000,-1000,1000,543,1000,258,269,-1000,508,-1000,1000,245,-224,-524,-1000,1000,1000,-1000,1000,-1000,-1000,1000,1000,1000,-37,1000,1000,367,-1000,1000,-1000,-371,-31,-283,-314,-1000,-1000,-1000,-1000,932,-1000,-331,658,300,-929,-1000,-143,1000,-1000,1000,1000,-127,-1000,1000,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-567,-318,477,218,-177,908,887,1000,1000,763,-319,-321,-204,723,214,477,-1000,494,-1000,-799,-143,-400,646,476,570,1000,-164,1000,-1000,776,949,824,814,28,-120,1000,681,859,-157,-854,-134,-1000,-928,-960,-999,-505,-1000,1000,-366,324,613,690,-1000,492,-909,-322,-130,-812,1000,-171,-806,-921,-644,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{1000,-399,-514,-377,-325,-431,375,-1000,-1000,1000,-566,752,-1000,-631,1000,-1000,488,-809,1000,1000,825,1000,-453,488,212,-934,1000,-1000,932,-1000,-744,-49,-1000,1000,-129,-1000,62,-1000,1000,626,-1000,1000,1000,505,1000,-118,1000,111,59,89,-243,-1000,524,-1000,1000,703,-1000,1000,330,-1000,-399,-1000,-16,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-1000,623,325,1000,-741,-777,1000,957,-312,-1000,132,-131,-316,437,1000,1000,-116,400,754,-1000,540,305,-973,1000,278,-1000,-185,-1000,265,314,1000,956,-1000,1000,-56,-1000,-745,-463,434,32,-387,-145,160,-603,-118,-195,-1000,1000,-839,1000,1000,300,-1000,-1000,-57,572,-720,-391,-137,847,-511,515,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{18,429,-126,387,-520,482,44,-116,-75,-413,102,322,442,-575,-843,-1000,-1000,-71,1000,-734,126,582,-162,-507,-483,-1000,-432,-20,367,-404,-374,-14,-1000,986,-387,1000,-720,417,885,915,1000,-46,-356,1000,568,269,1000,-53,1000,89,-1000,-475,896,-1000,-938,285,-1000,142,-313,-542,-1000,-861,277,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.String:Nzc1MzM2", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{1000,-654,41,775,-1000,-507,288,-264,-916,-1000,484,309,-419,-1000,644,586,5,147,1000,-540,940,1000,1000,-826,-883,-1000,-240,1000,544,-231,-1000,24,-304,-1000,-176,130,866,942,660,1000,-328,-1000,1000,600,93,-349,1000,922,483,1000,836,-353,870,253,-1000,-1000,-614,1000,-1000,-1000,-1000,374,-570,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.String:NjYvMkFE", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-800,-1000,349,1000,287,680,503,148,919,-637,-810,318,567,626,-679,1000,1000,1000,-464,393,-435,-476,1000,-1000,-203,837,-1000,1000,-765,-1000,1000,890,1000,-694,-1000,710,1000,554,-220,-466,573,-1000,71,-321,-1000,-1000,-1000,148,358,328,1000,761,-543,601,-1000,412,678,1000,350,716,-658,-982,797,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-400,-1000,-706,746,-1000,-941,-644,-230,9,-637,-242,544,-870,-1000,-243,645,1000,465,455,-196,1000,1000,1000,35,-203,-251,-400,1000,828,-1000,-783,864,349,-800,573,-433,235,-322,660,689,-1000,-1000,951,1000,396,-723,131,767,1000,1000,1000,-1000,632,-244,-795,-1000,-466,1000,-745,-389,-164,-646,-716,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-922,623,1000,1000,-212,-491,-13,1000,1000,-1000,1000,-332,395,925,325,668,-104,-349,81,-589,244,-530,-756,1000,1000,-1000,645,-98,-1000,1000,1000,935,433,1000,685,-466,-1000,1000,-1000,-660,-560,742,105,65,-735,-1000,-1000,891,501,375,864,-401,-950,-641,937,654,54,-117,-734,-109,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{108,-837,398,-162,95,-616,-626,-676,261,1000,1000,-278,1000,500,-724,41,-169,-241,1000,-169,-98,-1000,-1000,-935,1000,740,-733,-257,-151,-4,-908,-722,468,85,-1000,233,-713,409,-171,181,-101,-1000,747,-316,-119,193,-1000,-1000,590,-61,-87,-1000,1000,-334,466,210,391,93,792,-1000,-975,191,140,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-206,1000,-177,-426,567,-767,-7,-903,-599,469,17,-764,-876,-1000,618,1000,327,444,-762,915,-492,-1000,-541,-1000,-49,-929,-2,-1000,839,1000,-1000,-822,-808,116,36,1000,558,611,1000,1000,-620,-1000,931,1000,-1000,-795,-177,-1000,-4,1000,-1000,-969,-1000,-981,-1000,1000,-405,-1000,181,-400,78,1000,-726,-906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.String:KzEwMDAx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-978,558,-743,-1000,494,-731,-519,-1000,-676,-1000,1000,-400,332,-149,400,-179,-422,-756,-207,181,-349,-1000,277,-1000,1000,1000,-450,-400,-1000,717,-46,-797,-871,-128,-892,306,-923,-433,214,-191,-12,-1000,1000,-307,-963,-1000,-914,-1000,261,184,-1000,-13,84,1000,576,213,-787,-536,742,-925,-217,355,1000,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-39,-931,-2,1000,-1000,-296,603,-608,1000,1000,-781,-225,-29,262,-1000,318,-759,966,-1000,-871,1000,-105,-406,-420,-1000,554,-750,-1000,1000,-295,-936,-562,-58,684,336,1000,1000,-232,-644,488,-881,136,1000,967,-1000,398,-940,-490,606,-99,598,23,-1000,313,-334,1000,1000,-675,-969,1000,393,-532,-1000,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.String:Ng==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-471,127,-802,-92,-843,-174,219,-544,-481,-7,431,-864,353,-565,-768,-367,-212,-237,568,-841,-111,39,684,701,-373,-350,-559,879,977,-748,757,233,-237,300,272,-865,387,352,-702,-954,638,136,-245,-639,-598,-841,944,-278,284,-419,-277,320,-89,976,929,472,989,-335,752,551,-925,-640,166,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,1000,-529,-1000,836,882,446,-1000,-1000,-1000,188,-1000,198,-250,1000,1000,251,-1000,-1000,-22,-1000,810,204,407,1000,-1000,1000,-1000,1000,1000,-591,233,-1000,237,1000,1000,-163,200,1000,1000,1000,936,1000,1000,-1000,-1000,-1000,-1000,-60,-4,-1000,827,-360,17,-1000,634,-822,-1000,1000,-1000,-390,1000,1000,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-39,-1000,559,768,-285,639,-6,820,54,130,505,-65,338,262,-474,318,-446,199,469,-871,-69,-73,-406,774,-250,-66,-750,400,834,-400,900,-562,161,780,-1000,-1000,1000,160,-1000,-1000,646,-40,-158,-1000,400,-49,961,-375,-195,246,598,-1000,378,313,188,-674,275,400,119,636,371,-1,-299,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{12,-1000,989,913,-1000,-147,103,376,400,1000,-72,540,100,1000,-400,197,-690,680,1000,10,1000,200,259,400,-1000,879,-769,624,-1000,-1000,758,-483,1000,181,-662,-1000,449,-932,-1000,-1000,-673,440,1000,-1000,182,145,851,-643,478,-265,308,-1000,-1000,1000,887,82,1000,109,-323,326,-441,-1000,-695,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-949,438,621,1000,-1000,-414,-226,257,1000,580,-1000,-130,-754,-400,247,439,-894,509,348,1000,1000,-726,627,-503,-1000,548,-472,-355,1000,99,-1000,107,-58,-539,1000,266,1000,-758,-136,546,-595,-574,-776,1000,1000,155,1000,172,628,-40,370,-1000,-952,-816,-165,1000,583,566,-771,1000,356,-914,-1000,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{747,805,-883,-1000,-1000,-615,872,-108,-236,1000,-1000,910,728,-1000,1000,471,-1000,257,695,-392,456,743,-749,864,74,-655,369,394,866,166,-806,778,1000,-473,294,-1000,163,-713,-766,-791,-913,-325,102,992,-171,521,1000,963,484,-1000,1000,-947,-64,-326,-1,1000,-924,-606,552,1000,587,8,-942,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.String:NQo=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-202,-854,415,525,771,877,396,-427,351,25,676,346,234,38,50,-610,50,-80,-755,-35,-520,-556,-410,-637,-627,-42,-509,-155,10,-425,561,-89,-507,-633,-837,669,640,14,525,231,744,316,-397,-744,-912,-65,-541,-961,206,790,-12,-131,520,654,-415,-231,237,647,-455,191,-861,-405,-172,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-9,-128,-691,-424,-1000,-856,801,-203,291,969,-1000,-1000,626,-1000,-1000,357,-1000,-70,-42,-1000,-317,1000,214,-687,1000,-492,-138,1000,521,271,-486,-237,847,4,-1000,-252,-731,1000,-348,-452,-314,294,338,1000,1000,-424,504,362,1000,-1000,662,-1000,-1000,-516,-267,1000,-459,-1000,1000,-404,1000,1000,1000,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-94,-739,-610,763,100,207,1000,188,255,398,276,-75,38,-1000,50,-115,-480,857,-68,-156,-608,-183,-776,-744,-243,-929,-229,912,615,455,375,-316,226,-890,400,806,-258,253,142,537,587,-21,681,-744,-499,140,-403,-449,-157,379,-213,531,1000,1000,-560,803,-58,209,-36,-321,-440,-29,-128,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.String:MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{591,843,-225,1000,-774,176,-875,1000,-627,1000,1000,265,886,-439,820,346,-1000,-197,797,445,-860,1000,-1000,-385,403,-1000,-1000,7,-77,561,365,-426,669,866,1000,-510,-75,-773,-1000,1000,-184,-538,449,886,-1000,1000,-580,-4,609,-580,809,1000,-1000,-174,727,1000,773,-1000,1000,900,-1000,-92,237,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{834,1000,-946,817,1,495,-1000,963,-633,1000,850,-637,862,969,1000,-774,711,606,-541,242,-524,996,-218,-617,-1000,-23,1000,126,-332,-572,510,-225,-907,350,762,1000,-890,-1000,-386,286,1000,-1000,-3,933,-751,608,-1000,-1000,886,-1000,1000,-958,-774,-668,-70,-104,-566,-1000,460,1000,209,887,1000,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("java.lang.String:LTc1MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-136,666,-327,75,-743,-233,-1000,1000,-1000,1000,-191,1000,546,-379,743,1000,-566,1000,-1000,-190,-1000,1000,-1000,-1000,-1000,-62,-333,778,-242,690,1000,-899,13,695,-681,1000,-1000,-1000,836,-1000,-404,-1000,-1000,370,-1000,-221,-1000,-326,140,-603,-1000,51,1000,-238,1000,-1000,-18,-421,34,380,948,914,-308,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{107,-18,-690,924,-845,298,-406,196,-547,1000,1000,-1000,-579,1000,1000,1000,-921,1000,-984,728,-522,1000,-1000,-122,-1000,-787,338,-1000,-205,-475,93,-245,-577,1000,873,-1000,-911,-951,-1000,1000,1000,-1000,-1000,396,-382,1000,1000,-1000,-861,1000,1000,1000,-636,-535,1000,1000,778,-1000,1000,858,-1000,1000,1000,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.String:UE0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-19,63,-900,288,-708,846,59,672,-353,392,159,-871,142,-949,788,-796,-720,649,-283,439,592,-465,-561,-313,-981,-999,478,43,-842,-497,-889,-301,-330,508,310,904,88,552,-171,-644,-920,153,303,802,831,-423,997,408,596,160,-586,-446,-276,-144,-655,85,500,-987,158,-196,-13,984,116,968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-129,-932,559,549,-1000,986,174,-131,287,-129,-1000,941,351,377,-201,-65,-441,-1000,-53,-1000,-447,46,829,228,-427,225,510,720,1000,-863,805,717,1000,626,-478,-94,33,963,-830,319,588,-829,-207,-577,-121,-14,43,-481,19,-860,-139,-426,44,-350,620,-233,585,-939,-203,543,-1000,-168,-254,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDk2", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-270,-215,-1000,880,765,1000,-1000,-60,212,-133,-115,113,-1000,-535,754,-878,145,-1000,-312,-504,899,63,-386,-208,-875,-1000,-1000,-1000,-1000,1000,1000,-1000,844,-1000,-1000,-610,-718,-1000,-886,-1000,-1000,-1000,174,-156,1000,-428,-345,-404,1000,-889,-1000,1000,-980,-648,622,455,851,-906,-1000,203,699,780,1000,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-81,-19,1000,-1000,-579,-817,225,-713,-764,-273,-799,241,36,642,192,1000,-11,1000,935,1000,210,1000,-1000,1000,72,1000,119,-180,1000,1000,-754,1000,-924,-573,1000,294,-693,1000,570,1000,-640,1000,-410,-163,-1000,692,-69,383,-1000,1000,689,-1000,988,1000,1000,-549,-995,1000,-565,-1000,-1000,-756,-802,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.String:LS03MjQy", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-755,377,-724,707,-17,-658,-207,334,48,640,1000,636,-915,-962,168,499,-980,-224,-1000,-1000,-1000,-968,-317,-1000,1000,-1000,-456,-380,-1000,1000,-569,-1000,159,-938,-1000,-767,-839,-1000,-11,-464,-589,-388,-822,707,1000,-1000,-189,-217,713,-691,-745,1000,142,-961,1000,310,1000,-436,-310,400,924,-1000,1000,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-884,-305,-110,-1000,663,-380,869,-565,-1000,-302,966,1000,-440,-526,-1000,786,-1000,694,623,591,1000,832,424,-1000,-751,1000,199,-1000,-1000,-815,-1000,524,-1000,984,30,-823,-407,147,-1000,1000,134,257,-269,-1000,769,605,-86,493,690,696,429,969,1000,-92,-1000,-329,1000,1000,-718,-1000,-1000,-518,-695,306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-708,94,-1000,1000,439,114,-607,1000,1000,626,1000,-478,-1000,-1000,1000,-613,-1000,-1000,-1000,-1000,-52,-1000,637,-1000,1000,-1000,-111,-1000,-1000,805,27,-39,1000,330,-1000,-105,-1000,-1000,-534,-1000,-1000,-1000,-1000,941,985,-1000,-428,664,1000,-1000,-1000,1000,-1000,-1000,-292,94,1000,-1000,-203,1000,41,-993,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{281,-356,729,-892,-426,674,-192,-208,-680,-282,359,990,233,-581,48,537,-214,859,-606,990,-129,869,610,675,68,495,-278,171,460,374,100,166,409,-97,454,-437,-328,307,-83,654,-167,947,913,-722,-872,-406,-826,-36,451,715,-504,-771,561,-657,-413,464,340,-427,736,-897,-671,323,913,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-795,-94,1000,-745,-112,-1000,1000,-601,-358,-235,-286,-418,-1000,445,682,828,183,315,-131,353,1000,-792,-1000,1000,499,1000,836,-1000,318,-615,-623,393,166,311,882,706,-480,1000,390,471,556,996,-174,109,-57,-76,79,87,-1000,625,934,-1000,-1000,1000,-524,133,123,597,357,-577,371,-1000,-800,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-81,669,-233,214,-234,-208,-1000,631,-1000,-406,-284,-1000,-895,-541,1000,-38,1000,-916,-436,336,-535,-477,870,962,423,-111,-227,1000,203,-473,1000,402,-924,483,403,294,-900,369,1000,-41,357,-116,398,1000,-811,-210,48,-416,301,-621,493,-971,988,630,-228,495,-319,-536,-801,560,-382,-526,-802,-730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDI4NQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-81,281,1000,-1000,-579,-817,225,-713,-764,-273,-366,241,-137,642,192,1000,-11,1000,98,1000,210,966,-1000,1000,653,1000,119,-180,1000,-617,-754,1000,-924,483,1000,294,-693,1000,570,1000,-640,1000,-227,-163,-1000,-210,-69,383,-1000,1000,689,-1000,988,743,-1000,-549,-995,1000,-565,-1000,-1000,-756,-802,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-717,191,-422,-304,-647,-810,1000,-468,89,-396,-227,169,219,-545,1000,-643,238,-765,-48,1000,-223,-759,-11,541,-145,366,704,169,-608,249,-205,-549,180,309,31,909,461,290,821,209,729,-335,65,-340,1000,-857,98,-905,-16,-492,418,-1000,-870,809,566,-56,546,-709,-1000,-561,953,32,-987,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.String:QU02MjAwNg==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{129,-290,553,-400,556,436,1000,-811,401,125,787,402,511,261,569,574,-706,157,-529,-732,735,400,-191,376,1000,793,-1000,588,-1000,634,-26,-1000,1000,-1000,1000,-254,-160,-544,366,783,-972,650,-914,-616,-1000,1000,1000,-99,-269,381,-423,268,-711,629,-417,-1000,1000,321,-270,-256,35,1000,782,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-135,-545,1000,1000,-264,-1000,636,1000,988,-626,1000,674,-889,726,-345,1000,-1000,-35,809,224,1000,-90,-200,-988,632,-523,-1000,-130,-477,1000,98,-477,1000,-650,987,-254,949,528,555,85,-892,807,-1000,-223,-1000,1000,1000,-131,-1000,-573,585,861,235,261,387,-594,200,311,718,-882,-1000,1000,-21,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDQ4", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-318,361,1000,864,213,-1000,954,-315,249,-347,413,147,248,177,40,980,-400,-1000,621,-40,226,285,170,-1000,414,-295,-1000,595,117,290,322,-404,1000,593,917,-467,400,305,-453,13,-925,540,-400,-500,-627,400,776,551,-1000,-101,511,1000,-243,295,1000,-744,363,518,220,-878,-1000,980,285,473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-615,-370,-45,613,-619,687,-296,-947,-3,520,-486,-1000,1000,-1000,-1000,-989,1000,1000,-1000,-337,-1000,1000,172,-1000,1000,1000,142,216,-212,-1000,-274,-1000,-612,1000,-87,-1000,-98,-1000,-1000,-907,-604,-1000,-463,-1000,1000,-42,371,659,1000,487,360,-470,581,307,1000,-983,1000,241,-133,562,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{199,30,225,-436,-785,-338,677,770,676,798,323,440,256,405,20,-477,413,-249,960,517,1000,-149,631,-1000,-168,-2,-1000,1000,1000,-670,65,435,1000,-46,101,-574,-820,464,-453,1000,-444,792,-400,-425,-197,-601,-288,630,-1000,1000,-1000,557,-95,591,-641,1000,568,-145,-719,-462,956,-258,1000,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.String:OTg4MzEw", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-787,-711,-988,-400,-255,417,508,-370,-433,394,-92,-909,1000,-1000,-193,-671,318,-237,-910,565,-1000,623,-209,1000,1000,939,-207,737,126,-990,-788,-1000,-354,1000,79,-1000,-1000,-808,-613,-418,-623,-1000,1000,-768,812,-1000,776,1000,1000,1000,443,-242,1000,12,1000,-1000,754,205,80,382,1000,-163,-956,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-15,-50,-13,152,919,436,-723,-811,401,672,226,463,-884,67,569,-388,-706,560,-249,329,-252,-714,-876,376,782,759,164,-894,-642,51,65,-115,-963,-334,-161,-69,-832,213,682,391,-444,792,-234,-781,391,-912,834,355,642,-449,47,-940,-711,788,-641,851,784,271,369,418,956,-143,-910,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-261,301,-1000,-71,-773,-394,-663,329,1000,1000,181,-200,1000,-265,810,124,359,-674,304,-261,-169,-444,-1000,255,-1000,296,1000,1000,-310,-1000,667,-35,217,1000,920,-1000,-488,-401,-1000,452,-886,-413,1000,-921,-152,-1000,172,1000,498,856,281,1000,26,719,1000,-592,1000,1000,-26,-291,50,1000,-483,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-536,-361,-227,-1000,1000,-377,916,-832,-1000,1000,702,-1000,-959,-1000,-1000,-592,830,1000,-860,486,1000,753,-74,-9,-659,1000,-293,-894,84,305,-1000,-229,-505,823,-1000,613,1000,1000,-545,709,1000,237,-374,-128,-294,1000,-370,-475,-485,865,572,7,-425,212,1000,-488,482,251,-766,389,-121,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.String:LS0zMTI1", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{623,-645,-951,-31,319,393,1000,-1000,-188,697,897,-77,1000,-327,1000,1000,1000,1000,-725,-80,-46,1000,150,-59,426,1000,-1000,1000,436,145,1000,-341,-8,-413,443,1000,75,49,885,-705,671,1000,1000,-84,-104,1000,336,1000,-880,1000,67,-901,-1000,1000,-631,631,1000,1000,-1000,840,1000,-117,494,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{470,-478,109,76,638,-63,-274,-676,-537,585,1000,-673,-371,-1000,-1000,-179,726,413,-228,1000,920,-714,1000,1000,609,208,16,-1000,-103,-358,-1000,180,452,-206,-400,224,400,1000,-89,1000,635,333,-1000,273,-14,1000,28,-33,-740,1000,1000,-480,-44,-271,1000,-1000,-501,383,-826,1000,-811,-912,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDA1", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-397,-250,-343,-1000,997,-689,502,-1000,-1000,974,1000,-824,-1000,-1000,-4,113,830,878,-172,592,158,863,-468,-9,251,1000,-384,-934,980,-1000,-315,-103,-1000,12,-1000,699,757,399,439,230,234,333,111,-353,735,867,218,-1000,-72,724,302,-27,-163,586,253,5,610,665,-1000,-161,-121,-353,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.String:MFBN", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{499,50,958,212,-561,502,142,365,1000,64,-30,549,343,539,152,317,953,584,122,98,-1000,426,11,268,1000,630,-1000,85,-1000,-1000,877,228,-330,-1000,-1000,-197,456,-666,931,-282,-1000,133,-41,630,912,-155,-49,-679,496,244,-332,-417,377,94,51,147,712,4,-279,-497,793,-6,-741,203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-1000,1000,-354,-175,192,428,147,798,-538,1000,-1000,-384,1000,1000,602,-869,1000,1000,-935,-1000,-241,912,-986,-1000,301,455,-1000,-944,-907,296,322,-328,-851,672,-1000,395,1000,-1000,218,8,-425,10,298,1000,-1000,310,-194,-377,747,232,-933,506,1000,-756,494,-232,704,390,-384,-619,454,20,-1000,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.String:MjIyMzY1", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-291,695,841,-222,120,291,1000,523,-118,357,-1000,-934,604,697,916,-983,1000,1000,-497,-1000,250,844,-437,-1000,-795,676,-869,770,-1000,864,-574,-21,169,393,-687,609,504,-1000,-531,1000,258,-375,307,1000,-655,409,-346,-250,208,122,-332,-21,592,-829,97,147,712,-720,54,141,1000,-6,-1000,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-814,-891,-433,924,-1000,113,-1000,1000,-499,-1000,-264,113,-1000,-349,936,-1000,625,974,-1000,356,-957,1000,-724,75,267,1000,-916,-321,-85,-935,367,-1000,-1000,-1000,98,347,595,-778,1000,-476,1000,1000,351,390,1000,-169,175,18,-665,631,-523,640,1000,-1000,143,636,1000,1000,-1000,891,1000,623,665,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.String:Mjgy", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-893,540,-956,-282,324,-411,278,-513,-992,-336,845,250,671,394,844,964,909,368,663,-956,-226,901,-949,-837,-315,786,-791,-957,448,-631,961,-455,-830,-455,-989,557,949,879,-10,-358,118,-70,558,-751,-526,114,-822,382,489,909,-665,80,785,93,-38,766,286,832,-383,-365,448,-103,568,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{168,1000,430,672,-786,-659,-998,1000,1000,-1000,-1000,46,732,-492,625,-653,131,11,332,174,-3,-500,396,357,-100,-298,-143,-409,-1000,482,-703,-417,861,-741,845,-794,-577,-1000,312,461,-539,-559,-1000,1000,871,340,453,-171,369,432,21,-17,-309,-499,104,-364,-493,-242,305,770,844,384,-449,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-1000,-846,-401,-324,690,589,1000,-926,-1000,1000,-13,-303,-291,292,58,495,748,1000,-840,-204,443,1000,-632,-470,-257,1000,-861,-649,903,-299,79,-743,-1000,246,-1000,1000,1000,339,313,-397,688,1000,1000,-653,-83,33,-665,-433,7,560,-1000,854,114,917,851,1000,443,794,-1000,-1000,532,-294,-309,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-291,-88,463,-1000,1000,-994,1000,-301,-1000,1000,724,-1000,604,-1000,264,-488,1000,1000,-1000,1000,331,474,-364,1000,477,1000,-785,-1000,739,-897,-315,857,169,918,-1000,1000,504,-1000,-311,541,-386,-628,-756,-207,-413,409,179,-850,583,785,489,293,1000,-829,-771,-862,422,248,-1000,141,-1000,-539,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.String:XzM2MTIxNA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,1000,-19,-1000,1000,-644,-1000,-832,1000,687,609,-848,-1000,-429,-18,-590,1000,1000,-879,-1000,-669,-1000,-91,-1000,-433,-1000,460,-1000,-1000,-1000,-61,924,193,189,-783,-109,1000,1000,925,-1000,477,924,342,351,-582,-1000,-1000,3,1000,-354,326,-362,-1000,551,-1000,759,-1000,1000,-825,-523,1000,-218,897,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.String:LS1HTVQ=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-869,965,-370,-1000,1000,-422,-292,-8,390,-614,-701,-126,-100,-470,12,-1000,75,625,-1000,-42,-462,-1000,370,-590,-528,-47,-1,-626,-1000,-523,548,214,344,1000,-81,-1000,589,1000,1000,-615,-524,1000,382,234,54,-1000,-722,73,1000,-281,113,35,-477,63,-693,-169,-97,1000,-421,-582,1000,-790,-558,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-956,-695,943,627,-399,87,-611,159,894,606,-370,677,936,499,156,427,151,-74,751,877,-874,-789,749,521,201,-219,-803,-583,991,441,517,-999,-247,984,-1,838,669,-60,-494,950,-416,861,-835,-901,-486,-38,-539,976,303,-828,196,-355,249,671,24,777,-801,-140,951,213,643,-629,60,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("java.lang.String:NDM0MTc=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{238,-832,25,-434,1000,-1000,-201,-212,-1000,-614,166,605,-268,-470,-1000,835,75,-790,-1000,277,-828,327,193,472,330,135,-1000,-1000,-1000,-6,-951,-596,-501,-403,1000,1000,-1000,1000,1000,-1000,527,1000,-828,257,139,-232,420,-1000,-1000,191,-1000,195,-1000,461,-28,-547,-850,123,142,13,1000,1000,152,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-922,464,382,80,-394,245,-889,-53,545,-147,-664,1000,-523,-592,517,520,-305,-23,642,862,-1000,-81,401,319,-427,-945,-883,-267,252,546,1000,4,-812,-684,-82,1000,-56,-98,-630,1000,-919,839,319,-725,-365,-1000,-316,267,148,-25,-949,-1000,854,75,-941,19,338,163,1000,-653,-349,67,307,591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDIyOQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{8,-1000,921,-1000,-1000,-177,-889,-53,-1000,471,323,1000,-1000,771,164,1000,1000,-725,1000,-258,23,1000,996,1000,203,-1000,946,-267,1000,1000,224,52,-1000,358,684,921,944,-747,-1000,668,-754,839,-1000,762,783,-777,-316,1000,-192,1000,-1000,302,854,-299,242,1000,633,-1000,1000,-246,-713,-1,-661,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("java.lang.String:UE0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-138,-230,284,1000,-1000,103,-300,331,272,-747,-1000,1000,-597,53,49,1000,549,-817,1000,1000,-900,-230,596,972,-468,-494,-1000,-753,1000,132,861,755,-352,264,367,355,435,-448,-528,937,-1000,-417,-16,325,-1000,-327,-956,313,543,-242,-163,-589,-283,-456,-424,1000,-729,-779,551,-68,-1000,-693,894,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{566,-436,-465,-119,-209,523,387,17,-1000,-421,-1000,-256,299,771,522,439,1000,-1000,359,-579,1000,1000,267,990,419,78,1000,719,212,975,308,1000,-1000,-351,-277,-124,93,853,-171,983,-1000,-234,-293,762,573,442,1000,457,-192,1000,-1000,349,382,-1000,524,303,1000,898,-232,-1000,-901,215,-249,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{736,-1000,302,845,-1000,-541,-1000,-948,-385,1000,1000,99,-793,1000,224,1000,1000,-323,1000,-1000,800,1000,309,1000,591,-1000,720,281,1000,747,55,71,-1000,470,267,1000,1000,542,-1000,1000,8,685,-1000,631,-1000,-1000,-112,838,-429,1000,-591,-81,-292,572,-126,699,-1000,-635,260,-1000,-271,155,-485,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,376,-433,-59,1000,-212,776,216,342,53,-868,-209,673,187,120,-1000,-486,202,-1000,-151,1000,-301,664,-573,551,-1000,-194,-320,-1000,-305,232,-61,78,984,469,-1000,1000,1000,1000,-358,221,334,132,-773,100,-70,479,512,50,-26,789,1000,272,141,659,107,585,623,-1000,-505,1000,-1000,-804,639}));
    }
}
