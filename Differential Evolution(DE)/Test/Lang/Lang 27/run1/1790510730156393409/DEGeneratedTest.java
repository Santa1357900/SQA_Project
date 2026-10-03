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
            new int[]{-654,56,-845,727,434,379,-1000,-247,-820,-56,-907,349,-1000,643,-266,1000,293,-238,-1000,-1000,1000,-335,1000,-1000,39,-495,678,898,616,238,642,-851,-152,-237,-210,565,234,-682,-499,-521,1000,45,190,499,696,-105,15,42,702,-910,-1000,1000,-948,-838,-244,-559,1000,-268,-407,233,1000,-507,219,-77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:Mzg4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{868,388,-68,630,-32,-875,50,9,-489,198,151,-617,-480,-665,-808,338,24,-25,-692,-703,212,359,-129,-557,-290,467,52,-296,585,176,913,-926,79,-52,730,-984,454,890,-77,-262,-134,42,772,-301,693,-576,-115,-97,-628,-469,325,-782,479,-272,-793,270,-491,-678,-356,-715,-383,-65,960,-313}));
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
        org.junit.Assert.assertEquals("java.math.BigInteger:MzUw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{996,-350,398,333,-33,-23,-818,-556,613,-750,861,876,906,-389,-322,-255,-47,-427,-322,486,-962,-365,-798,-736,740,260,-66,-267,-767,-946,199,-212,690,903,-511,728,-28,-130,-971,-991,683,724,-374,672,134,-892,993,-592,702,579,283,-754,706,-7,-334,-125,992,60,-563,736,128,-716,358,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Double:MTkwLjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{-237,190,364,-142,750,-289,837,-170,449,637,-726,356,390,-149,-668,248,242,-620,45,4,349,691,936,-475,971,-400,462,626,825,623,-242,-925,812,-474,297,-655,-709,314,820,281,737,1,-400,-486,144,-915,998,588,-829,-108,109,-513,704,654,281,543,219,541,-700,-442,-842,-281,-893,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Float:NzguMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{435,78,-497,-631,887,-602,517,-559,547,533,-265,-316,-464,-94,281,67,-177,991,248,346,-555,-915,431,394,610,788,-920,-881,639,-668,203,936,440,-174,-806,511,530,-451,-575,-715,-489,-555,-159,-835,313,178,574,-343,-443,-746,-874,739,924,-981,-247,807,565,674,-639,-873,-743,812,430,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTkxMQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{-204,911,-375,-692,-519,-659,116,338,-13,460,987,389,-806,-569,-308,-792,289,-333,837,-54,652,259,-794,-629,-418,438,898,-687,501,51,-138,-4,79,-945,87,452,277,410,451,-75,-224,-555,-180,-693,450,737,344,-979,602,900,-867,-518,-473,233,472,310,-369,171,575,782,828,680,512,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Long:MzUx", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{371,351,179,980,-238,-527,479,-382,-346,-366,-820,-615,-914,825,-980,356,930,-569,337,838,592,241,525,-738,41,-268,-308,-96,262,-962,-858,-492,-912,683,-980,593,-633,-539,163,-443,389,703,-980,-437,-383,557,-638,-364,-114,891,409,400,-548,-42,12,-559,518,-938,-436,-571,241,-881,426,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFLTc0Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-506,-1000,716,-750,-1000,-1000,-1000,-1000,-1000,-748,649,783,-961,-688,169,412,855,-772,370,-293,-952,-619,-424,998,1000,401,-865,473,584,89,-1000,-209,-662,91,-561,1000,-759,-228,449,1000,559,190,-24,281,536,-898,213,1000,1000,816,655,1000,-388,-651,-77,431,-253,-1000,872,1000,990,-923,-1000,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Long:MTAwMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-823,-1000,-684,-350,-1000,-1000,-1000,-1000,-1000,-1000,649,718,-1000,-1000,-1000,-718,1000,-772,521,-293,-637,-1000,-424,998,723,202,841,869,504,89,-1000,-209,-1000,91,-794,-716,-759,-228,-104,633,948,190,-228,-699,536,-986,-265,1000,1000,-484,655,1000,505,-651,731,-21,-85,-893,872,1000,990,-1000,-164,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{15,654,-201,139,-362,1000,622,1000,1000,1000,-202,-248,70,626,596,814,111,-476,-1000,943,111,-1000,-780,716,-21,313,600,-503,-709,-893,1000,556,-618,1000,805,-1000,831,-140,-614,-913,-692,221,896,-1000,976,-142,622,138,740,-549,-306,-412,104,959,-845,-2,-236,-852,-842,-117,-584,366,850,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Float:MTAwMC4yOTM=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{213,-1000,804,293,-554,-572,5,-1000,-565,-692,1000,-4,-1000,283,-1000,-575,74,741,417,610,-439,220,-1000,73,-464,1000,-781,-225,7,-1000,684,-848,1000,556,-823,710,-759,-908,-1000,304,-446,41,117,1000,-579,1000,-1000,667,266,-1000,-1000,-1000,138,380,812,136,253,-841,-227,290,817,-81,-372,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEwMDAuMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-871,-1000,729,-725,-338,755,-197,-144,203,471,248,364,-166,-300,784,-1000,633,-534,-178,1000,-753,656,-926,549,1000,501,-289,-819,-591,-732,-916,320,-451,1000,-78,751,1000,-1000,119,125,-1000,1000,922,-54,-571,190,-439,-627,-1000,-154,-235,309,386,-194,348,1000,-413,32,-130,1000,298,352,-341,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:NDQ4LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-487,448,-1000,-1000,-601,-104,876,331,-808,517,-273,-296,144,600,-1000,112,1000,-580,92,-957,-253,-1000,449,-1,-320,-40,72,15,-62,862,-581,-1000,-941,-131,-1000,-322,-1000,1000,1000,499,-238,-1000,544,-153,155,-1000,-432,612,1000,-1000,-506,577,545,-945,883,-3,-813,-848,-699,-278,-1000,-183,-166,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:MS44RTI5Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-554,180,-684,295,-121,-859,662,707,1000,-656,-216,-166,-52,603,-1000,976,817,499,-940,-1000,-414,-1000,1000,-614,371,1000,-1000,283,197,1000,-515,-1000,-1000,-1000,-1000,454,-1000,1000,-1000,753,-440,-1000,306,-80,-977,-326,-422,1000,1000,-1000,-929,1000,459,-1000,769,6,-1000,-1000,-169,1000,-609,226,395,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-306,835,-1000,648,-220,-274,475,432,0,-22,-13,-1000,-239,1000,979,1000,393,-23,321,-374,615,233,-563,800,-194,-580,466,980,-492,242,210,-844,-1000,476,52,519,-1000,1000,119,520,1000,-923,201,-319,502,-191,-505,-267,1000,-757,-235,-382,753,-102,53,-986,-451,-1000,-352,-596,-1000,107,339,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nzkz", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-217,793,978,-69,706,821,550,216,-738,-486,802,440,174,-115,947,-587,-199,318,-812,85,-697,-728,-213,641,-232,-218,-432,-730,327,-250,-681,-536,314,76,-821,625,331,998,-399,44,725,-336,779,812,-559,926,-496,148,440,618,-646,-221,994,173,-157,501,995,-677,693,878,178,-151,739,-861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{505,799,-18,556,1000,-114,597,267,1000,830,-312,1000,-13,870,-1000,886,-485,-204,190,-1000,-93,-869,969,-1000,-554,-750,436,1000,-28,1000,-110,1000,628,-1000,-906,-64,-801,329,909,-71,193,-675,30,860,680,-1000,-333,-271,-265,-434,119,-528,-68,-504,303,-95,-1000,483,-923,-527,-1000,87,701,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-473,-1000,774,-319,-1000,658,365,1000,915,674,38,1000,111,-472,1000,661,399,-1000,-461,1000,-644,233,-84,1000,1000,1000,-165,-479,-952,-1000,1000,626,-655,1000,401,-299,-1000,354,-762,789,-594,1000,1000,-226,1000,-1000,-258,-267,-366,193,238,539,-146,-605,-1000,446,-244,315,-344,1000,496,100,-87,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-35,-869,561,-114,-1000,-1000,-662,-881,-1000,-748,649,532,-829,-344,-245,575,1000,548,370,-754,-974,-23,605,491,1000,221,-1000,463,584,508,-1000,-318,-1000,-732,-604,864,-759,-228,1000,980,604,167,-184,140,-189,-382,-383,-708,1000,238,338,-1000,-413,-1000,951,542,-550,-1000,-345,552,997,-916,-1000,-544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{494,-497,-762,481,-532,-150,479,-209,-462,-139,305,-229,-843,896,-926,-921,-433,432,-212,-245,-194,-496,-453,365,-826,417,-888,366,-732,-416,622,862,750,-614,146,-90,796,240,-723,743,-171,-214,209,850,583,-17,-103,720,805,-752,772,-772,966,-675,237,159,251,-526,706,-864,-216,-206,293,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{15,825,1000,772,986,531,750,74,-1000,512,86,1000,206,-514,1000,521,308,401,-612,-533,-1000,-1000,-484,-543,-678,-1000,304,298,1000,-742,203,-539,276,676,-539,1000,1000,1000,-628,-154,1000,87,1000,-148,142,6,-1000,605,612,682,-1000,408,1000,-681,-1000,-160,675,-735,994,749,-270,361,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEzNDIxNzcyOA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{1000,1000,-203,-634,-100,0,-1000,-941,951,398,0,406,-799,0,-756,471,416,-61,157,-741,303,765,563,-1000,-1000,502,531,995,998,-1000,659,1000,-627,-1000,-1000,-138,0,0,-515,98,1000,-1000,-679,-155,0,-132,-344,0,729,110,-1000,-1000,-334,0,5,-691,-551,-1000,719,184,-524,-860,-543,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-334,31,719,-319,196,212,-336,-997,939,61,-956,-919,200,-447,833,197,-481,-510,979,541,713,671,380,-661,496,-779,887,388,-591,579,-982,827,839,-365,681,910,708,-133,-263,781,-933,-33,-524,-189,-746,413,-777,932,551,724,179,566,342,710,-659,283,-337,-60,-110,-940,-275,866,780,-784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{-572,-323,216,709,263,136,25,-1000,82,-823,-341,-793,349,771,-13,-887,567,-757,-721,889,226,-509,926,-480,-33,-407,502,-130,804,-391,-169,234,-688,445,-124,524,374,-240,-60,-338,-712,49,-62,-844,-186,-701,-709,430,95,903,386,-33,292,868,490,-943,248,787,-519,-430,511,544,-133,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{-330,-767,140,814,-909,430,742,-932,873,585,-340,-829,134,-921,-33,-274,558,-740,519,-552,568,-361,-535,280,458,-632,194,-919,317,89,951,466,737,-706,452,767,880,465,521,439,-232,404,-954,-307,390,444,322,-317,608,-518,-721,-882,-284,-822,987,-165,-1000,-942,-640,571,683,-239,-968,-64}));
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
            new int[]{-26,679,-1000,-686,-819,-1000,950,494,-1000,-1000,-741,-735,-414,1000,483,1000,-177,593,748,971,492,289,-404,-1000,-1000,-995,-683,-1000,-844,986,-1000,681,-1000,-909,-1000,278,-1000,-130,-752,-584,-1000,-508,-281,-347,425,-403,-130,-745,-1000,1000,733,-977,-167,-1000,-1000,-583,603,835,-260,-182,-1000,-1000,1000,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{679,495,605,-195,683,544,-866,-516,713,542,-485,-73,-618,-217,-465,371,-68,-465,-451,-1000,-1000,-507,-329,-588,229,742,-320,629,-467,-1000,1000,-714,107,367,58,-224,-1000,-277,291,-29,711,-440,-241,820,164,-787,284,531,455,-719,-651,528,-470,267,826,-1000,885,-599,-19,-750,964,498,-720,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-471,1000,189,-247,6,-585,1000,-804,-1000,-1000,501,1000,134,630,-600,503,262,153,1000,1000,119,665,-606,-713,951,-1000,-333,-1000,-192,775,-573,266,65,-386,-71,-353,-368,-6,462,-433,175,-1000,719,679,943,327,794,1000,-1000,379,-584,-465,-1000,571,-509,60,895,779,1000,-969,-280,617,-119,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-670,906,-675,-545,927,-752,-921,-350,799,-187,-166,255,4,-918,-192,947,-67,-938,282,559,76,212,554,960,-368,-57,495,385,-852,-148,112,-838,-750,5,586,367,-697,-892,149,-140,-671,-810,842,347,-92,545,728,637,923,-874,437,126,-987,300,-929,-344,-642,-28,430,-610,63,397,289,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{217,569,-1000,-317,-412,-1000,125,-364,129,-1000,406,795,-75,329,139,1000,-818,292,1000,1000,-247,914,10,-607,-180,-1000,-1000,-1000,617,-55,-924,887,-931,-3,165,359,-226,-544,39,457,-160,-1000,617,1000,289,-88,-71,1000,-125,-313,11,-1000,-1000,533,-509,810,826,566,1000,512,-469,79,346,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{581,-660,-970,-859,-336,96,79,139,632,-376,169,24,-654,560,-17,487,258,-283,521,429,77,-871,-359,-595,-954,-604,-888,299,-933,-649,187,523,-134,196,-235,-378,-900,-204,-925,72,-146,-401,545,-743,-672,769,4,158,-321,-429,702,99,-34,765,757,-927,931,-156,595,-393,-37,309,-299,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-471,-1000,1000,1000,1000,1000,1000,558,741,1000,-1000,-398,-299,295,-1000,-469,1000,-441,686,1000,-353,328,-80,-723,-1000,627,759,739,110,-358,1000,-1000,-8,-145,-547,-1000,-567,1000,71,46,1000,1000,-398,-554,527,145,1000,333,-48,194,-283,285,929,797,1000,-1000,199,1000,-195,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-471,-464,-1000,-330,549,-752,1000,558,-546,311,158,-1000,1000,1000,-664,267,1000,-443,540,-870,186,328,908,-461,120,971,1000,-813,-1000,-271,-572,273,-612,-4,-618,-102,690,-44,-1000,534,-830,-1000,47,-1000,-92,1000,728,333,-742,-536,-1000,1000,-676,-1000,-1000,-549,-525,188,-218,390,-1000,-583,571,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-631,1000,189,-639,66,292,-14,-1000,-1000,-1000,855,221,269,932,-696,478,458,366,925,-728,579,285,-733,-782,937,-1000,-331,-1000,-651,702,-651,266,-340,-127,-7,-153,-321,-71,-30,-873,473,-1000,598,69,648,726,1000,896,-1000,418,-822,-326,-1000,188,-509,-586,521,839,758,-864,-810,442,701,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-359,1000,712,122,-59,-1000,208,-1000,-470,-1000,19,1000,-1000,1000,992,821,-1000,1000,1000,1000,-462,1000,-1000,-578,216,-1000,-1000,-803,345,-726,663,-537,1000,-486,1000,60,-1000,-1000,1000,-1000,1000,2,14,1000,634,-1000,-229,401,399,1000,654,-1000,-1000,1000,1000,-202,1000,-1000,1000,-924,515,1000,-1000,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-91,845,-763,576,387,-1000,-1000,190,484,-307,-453,-650,-148,-685,-421,826,-757,165,291,798,-381,1000,351,214,97,-109,636,-502,894,755,30,24,-366,-499,-142,1000,-166,-696,-204,-75,8,371,-749,1000,876,-324,85,-440,661,-945,576,-706,-903,386,-304,953,-303,872,707,-797,-695,-713,153,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{573,-456,-589,-297,-922,558,934,-892,-210,-129,-995,818,143,-973,516,-887,882,865,775,-350,-357,-116,58,-665,698,-797,-547,-599,-489,518,-654,-631,658,-437,9,141,378,-844,-395,-603,601,650,631,-354,-396,781,-665,848,-348,299,-35,-677,-56,-471,822,844,-36,79,83,253,-37,-766,54,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-273,-1000,1000,928,-412,292,622,293,1000,-1000,-1000,552,-882,329,-265,522,-818,520,1000,1000,-481,-886,660,-735,-1000,-81,-361,144,743,-285,-48,-631,-568,214,-217,-712,-294,680,-250,903,-342,-85,-201,309,289,-264,616,176,472,-513,782,-778,581,533,799,-580,826,1000,1000,-913,1000,225,-831,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Byte:NTQ=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{450,-696,1000,841,549,-602,285,-373,1000,-1000,1000,216,-112,766,754,656,-381,618,926,-120,251,136,-497,-1000,607,-14,-550,583,-1000,-1000,840,-300,-627,-90,631,1000,33,592,596,-1000,351,-823,62,-356,350,-396,613,89,1000,803,690,-997,-924,-105,332,-1000,-60,-117,692,-148,-230,-812,-850,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Byte:NTc=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{-99,596,45,436,269,570,-482,-941,-54,603,433,-410,250,116,-521,-353,674,-584,-742,66,292,420,-555,-739,-188,-493,-903,-935,815,-258,485,-56,77,-711,-211,-144,-167,488,-600,432,-671,-223,673,672,-923,370,514,-148,852,-498,383,1,-334,-127,-6,-60,702,918,278,-362,497,-449,-966,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Double:Njg3LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{-740,348,311,236,193,-846,-778,687,95,-736,-324,798,948,-219,-829,885,267,440,145,-609,-566,-485,883,998,899,-471,-593,584,-150,-228,-197,234,963,-94,218,-183,-182,808,529,524,-324,-829,-441,-803,-559,-595,636,-713,-159,-145,278,-159,125,500,-157,-966,909,-81,77,-520,190,557,-320,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{-127,-486,851,878,-412,-464,125,-394,69,286,-881,70,-485,-148,-401,171,-534,-127,915,-915,980,-233,-867,340,-713,628,-34,-732,-59,150,358,435,431,-742,-703,-67,220,941,-746,291,-716,215,-502,232,349,-897,-553,-531,-770,44,223,323,946,-740,-382,-937,629,255,-218,551,-394,830,138,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{-499,-128,33,710,-416,994,854,-887,-220,758,-291,621,-235,514,-93,-180,-235,-341,114,-149,-300,253,-713,-339,-232,688,-271,-653,75,-35,-439,156,877,-634,-622,-338,592,-414,9,-291,-137,674,901,815,621,-409,-67,-111,-618,-421,-822,391,-196,-877,-459,-685,905,-214,71,-235,304,-67,-782,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{-379,-532,-110,408,321,-615,65,624,305,560,847,545,-551,558,48,372,541,-116,-723,553,624,-264,-642,-706,-530,-73,-748,509,-873,-62,105,108,500,-751,-403,543,501,392,-130,571,410,-551,943,-702,780,181,773,739,503,-643,890,-607,-587,424,870,588,-766,208,-157,-253,585,998,685,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDMx", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{936,-583,948,-384,431,-397,509,416,-389,-825,224,431,-573,946,435,-120,162,-415,465,1,633,632,-234,-433,-197,-307,-285,484,133,337,588,742,-439,486,377,69,151,451,736,-285,114,-890,574,993,177,221,-379,-568,-927,371,583,681,327,-384,-521,694,-593,-456,-834,128,882,-810,-884,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{-866,-303,-581,-249,521,-274,-767,126,92,-529,590,122,169,618,701,10,-182,952,816,972,-318,684,-266,785,-687,307,-451,-511,-503,67,-502,102,610,479,129,-172,602,-767,-704,-868,870,290,-16,-437,964,21,-613,277,-152,825,-723,-175,279,817,-755,-26,-268,915,-866,72,283,-82,662,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{-237,-572,-440,-533,901,-791,708,864,-765,712,60,781,-55,-931,-709,209,490,-711,-19,-672,787,-447,412,-740,-386,-114,-769,362,-955,469,-959,409,230,90,-339,-342,118,809,-782,-247,-190,-256,-496,-307,-825,-888,103,696,-949,-877,-334,668,-63,-875,435,214,403,562,-407,-346,-387,675,367,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{-104,-49,-411,-74,202,-441,176,-397,948,781,-58,-680,-886,23,248,912,772,-255,-76,653,565,-918,354,-756,456,499,405,478,623,-811,-507,-377,-205,218,-66,-539,98,559,880,-224,496,-894,-433,652,-667,-466,-572,-630,101,410,681,913,-382,382,-602,-849,53,-558,-202,-406,-109,277,-480,-685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Short:MTAwMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{1000,-340,-336,276,1000,407,-799,652,-534,1000,-206,1000,1000,148,629,-1000,-1000,-691,-73,-563,534,594,1000,-845,-745,-1000,390,81,-1000,275,-821,-932,533,795,238,137,-256,1000,-271,-119,977,-26,-201,1000,862,-224,1000,-1000,-511,-426,-400,445,-789,1000,314,758,-532,1000,-1000,-1000,-1000,-1000,-706,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{-613,-459,-717,503,381,573,-934,483,-243,573,-560,-843,880,-389,-997,816,552,988,624,877,-711,-95,237,-636,-40,429,-938,825,111,-272,-729,-711,-490,-649,-634,912,-650,-932,-540,-984,926,-299,70,386,-418,-751,-956,-109,252,-361,-216,751,28,-538,98,121,823,-836,658,36,-559,-652,-679,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTgw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{830,961,913,780,-801,-110,-203,-55,-788,-203,-14,87,-163,128,-389,660,-412,-154,-141,-1000,1000,1000,1000,-412,894,-580,-336,-369,14,-1000,282,522,625,1000,730,121,-1000,283,597,247,-1000,1000,-427,1000,-983,853,176,595,-87,1000,-387,742,-928,-1000,-386,-1000,442,1000,198,-719,573,-18,175,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{-247,-762,683,-431,549,238,-738,605,-615,682,-989,838,-324,749,224,498,-97,-199,-831,-38,-10,341,-12,-226,227,-633,-736,-771,128,578,72,873,-667,693,-19,-664,-309,-151,382,-299,975,-501,779,-738,361,-463,-777,955,-248,-528,948,-966,-701,384,-555,-388,58,-852,-300,575,-354,-92,141,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{83,344,-830,-17,-416,412,856,589,820,-280,-411,-640,595,-399,-594,-777,84,-823,-870,-541,-449,-235,-191,-267,-830,-204,734,-626,645,446,5,-578,-858,116,209,749,-212,104,-912,703,-510,323,-726,-856,-864,781,114,476,-603,-336,424,-648,-719,339,-400,96,350,-621,-51,-446,-908,-221,-709,89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{670,-929,212,-50,-98,661,272,-532,-221,148,375,-495,640,647,426,-715,212,484,12,190,526,967,-132,-814,530,969,501,429,-655,60,757,618,983,868,642,442,-941,513,-920,577,-487,534,454,150,717,529,-263,306,-563,897,838,-424,97,523,370,-867,-461,39,-530,665,47,364,-463,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Float:LTE5OS4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{502,862,-421,-199,575,-132,947,-111,-310,-351,-679,-500,952,-54,995,-511,-313,-563,489,-897,-951,426,4,482,-232,-548,691,-241,-374,294,971,-310,500,832,529,-858,-172,-454,-199,-522,-769,-974,-217,922,-321,832,-184,-715,56,267,-378,-869,300,-822,345,-617,-635,-212,-438,-314,191,728,-643,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{-991,769,-911,634,804,46,23,826,630,-703,-641,-119,347,432,26,-457,-697,-503,62,317,-836,-154,-189,123,419,247,-673,772,-380,590,204,937,-333,-110,413,-356,-544,587,930,58,101,-273,965,-656,577,-410,403,731,119,-464,-287,549,6,-585,-968,-14,-534,757,-626,565,-666,-875,14,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{857,464,792,576,-432,266,-526,823,-938,822,132,-572,800,-138,850,-196,-704,-294,182,-689,-290,863,-610,-84,-116,910,556,-974,-499,-361,354,912,-160,-905,474,-443,-287,664,-780,869,622,707,643,221,894,-672,-288,46,631,-134,506,-179,662,-595,-842,-781,-620,-782,406,377,-668,404,-35,-656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{105,462,-654,351,43,-773,-94,-190,-47,311,-495,-552,745,-235,-798,-350,-947,52,-146,548,513,675,453,-811,-218,407,431,-259,-28,-922,881,-763,48,-630,-546,7,977,481,800,513,-69,-368,868,583,673,408,-891,-810,-573,691,-303,875,986,-430,-762,661,-427,-4,128,885,-909,206,-282,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Long:LTEyNw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{-974,-739,-886,890,-127,491,914,634,127,597,-365,-354,753,712,846,-84,124,572,-104,612,-420,444,-429,655,-786,-767,617,415,493,200,19,-757,-299,-765,160,181,-192,901,-459,-342,359,572,636,212,-776,908,595,383,674,-370,42,-233,-892,-889,-3,973,-997,645,-912,-336,-836,-586,624,-743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{-313,60,-647,-699,906,546,-193,-106,344,950,234,293,-367,730,811,435,-572,312,-712,148,-120,-912,-121,-842,745,-216,193,120,832,-684,-176,337,718,386,-510,323,-557,-649,383,-156,-762,148,-182,672,256,-523,-827,-69,-269,798,-369,614,-957,257,295,612,-253,148,-134,431,-202,-842,-715,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{862,-590,-1000,337,-669,-1000,-332,169,-646,-275,367,418,-357,-840,-570,1000,-879,-774,478,121,1000,1000,1000,36,-45,-971,-658,-369,800,-1000,1000,12,-1000,-80,-1000,-564,-105,72,-1000,-531,1000,-659,818,-946,-1000,-1000,-120,-165,998,-772,95,80,43,-1000,-584,-651,1000,-597,1000,-1000,-810,-216,-193,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{-818,140,-716,93,966,485,-789,100,208,583,-777,-4,639,-316,-585,582,594,928,790,462,-904,-21,-412,-14,158,443,-777,-583,-292,945,369,-399,-796,-975,-665,-111,-70,110,-284,283,305,-263,495,894,-946,-54,-733,-359,42,267,47,851,-237,134,-718,918,-4,658,-941,490,5,-274,-690,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{-922,443,674,243,-179,-501,650,43,-611,754,-253,678,496,908,954,859,847,629,-865,-430,797,-276,85,374,506,954,-466,468,-922,-706,-200,-164,-157,-560,250,-79,-276,970,-699,851,476,-268,298,-547,-570,-260,-736,-958,-608,150,-724,270,-692,726,17,225,-573,635,-115,384,813,-654,10,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Byte:Ng==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{142,-795,1000,-249,1000,-400,-482,233,-1000,-70,400,-204,-1000,344,-1000,-1000,1000,400,791,-1000,595,16,216,-765,425,-1000,71,409,-493,-1000,-89,771,1000,-1000,144,734,200,325,373,380,-184,808,1000,168,-103,725,-759,1000,1000,810,1000,-827,215,458,-1000,354,-221,471,-36,-1000,-413,358,-1000,690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{710,42,-335,-14,980,633,-974,288,-174,186,-723,873,-887,-697,-956,667,-846,-799,-934,-170,-461,-827,91,-141,-884,-991,212,-561,564,262,874,-757,409,868,-835,674,-840,-984,-343,-918,290,426,93,-596,-785,197,-512,-645,117,279,863,304,-498,257,-267,811,695,633,-263,416,-840,70,338,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Byte:ODY=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{532,86,-678,1000,567,-257,-968,362,-20,408,-162,759,-32,-1000,-99,-157,-764,-622,-1000,-787,-1000,186,265,-1000,-460,-95,-137,-988,991,-226,1000,-1000,1000,1000,232,978,-979,-1000,89,-1000,-686,954,1000,748,-841,-218,-245,-151,384,1000,-114,-378,-214,-244,1000,546,1000,1000,664,-809,617,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{843,38,-118,86,-675,858,-672,458,-311,-751,-54,-340,899,562,-845,-746,141,-10,-992,101,441,-26,244,-238,440,357,594,84,-896,100,729,727,-71,29,-571,-204,318,633,101,880,278,-783,-417,792,268,888,-651,83,-856,-236,678,-974,-530,574,-881,-853,-813,397,-305,662,-419,-359,670,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:ODg4LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{-567,-888,910,979,-742,-424,-779,-600,-343,-885,206,-724,368,765,-354,-310,-381,-257,-155,616,-222,977,212,50,-326,519,23,-285,-47,981,-146,4,623,835,-642,-877,83,904,595,605,329,726,-28,-678,-187,590,-31,918,-541,-16,330,-809,727,-24,-852,366,-729,-398,-643,435,-220,727,-431,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{-807,-72,462,-871,637,296,117,-746,44,528,-474,360,-783,267,-272,56,410,-635,-79,417,985,-63,333,-821,512,-454,-452,-65,-137,97,80,-877,-169,596,-769,-312,-142,-953,-379,-3,-439,-549,-836,-263,933,132,-283,390,-696,-592,-861,157,494,26,-904,156,-114,712,574,160,634,-325,779,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:MTg2LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{-380,-186,664,189,-96,-562,-44,-481,684,834,-957,-593,-18,620,315,-179,458,-755,-14,997,-503,700,673,740,-723,789,-435,-139,803,-792,871,695,816,-768,-505,758,592,939,-760,-759,712,-394,-801,274,87,136,493,-335,740,-792,-24,-823,167,-776,167,304,424,46,-730,-21,-439,32,-143,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-462,-444,181,615,446,967,410,948,-935,-946,-596,-545,-853,487,-610,-255,575,-792,-812,929,-74,-518,82,-617,-82,-361,826,671,422,806,-886,-1000,217,-573,-990,13,-984,-804,820,473,-606,-783,-331,-551,216,624,-347,-30,921,-297,-143,-176,-426,-376,752,-868,-985,-697,525,674,592,628,761,539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Float:MzM4LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{217,338,650,-273,44,212,-488,635,132,88,-583,343,501,-43,600,-115,559,489,-84,271,-489,-346,449,-354,-432,-371,561,-800,-341,544,-760,-7,878,-840,-224,266,200,392,-217,-405,140,-250,347,254,989,-767,726,-447,999,-10,-610,-400,484,138,-518,909,166,-459,760,-405,314,-938,972,523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{940,-103,-309,777,62,492,666,-191,175,-734,251,-701,-329,-271,444,362,359,881,-450,-669,-241,586,-184,-388,994,925,-847,405,-453,-158,789,-73,358,655,-819,-482,-46,-217,-971,386,603,-531,810,635,865,175,887,705,-704,840,964,372,-399,273,425,68,28,-444,-84,-572,-547,-467,-569,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Float:NjU0LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{-151,-654,272,31,610,378,910,571,932,644,621,189,-760,170,492,239,-923,-910,-340,-962,549,-468,-656,60,-44,630,-496,870,312,-93,451,336,-318,-471,134,476,-550,739,415,597,954,161,165,-665,293,-391,-313,647,-486,528,-327,851,-107,-739,665,-602,-446,-28,-183,230,-698,228,663,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{152,-808,42,39,-293,-650,676,592,467,781,76,-73,-314,400,863,542,612,-409,673,211,343,-727,-992,428,631,-111,-813,-40,-124,-606,748,800,840,966,-702,121,113,617,761,936,289,-867,-742,66,-62,838,436,774,2,-419,974,939,12,20,190,11,-336,-193,-787,-847,714,-416,-989,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTY1NA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{740,654,109,764,579,-902,829,242,603,-352,-730,-360,-995,-264,840,997,-649,-633,990,762,777,912,-463,-192,-833,-273,45,269,173,-976,-270,308,162,67,-50,-692,-238,204,341,-25,512,508,931,857,341,-120,128,436,58,-658,416,536,870,937,-232,124,47,200,-661,753,297,-570,828,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{902,467,825,349,314,681,810,-161,666,520,-508,266,-248,-432,278,773,880,472,-680,-627,-879,-485,-365,-192,699,935,-795,519,718,-196,-289,891,241,504,362,-534,32,-860,675,376,-770,597,988,-91,656,354,-5,806,510,-317,645,-313,797,374,-593,793,-3,-227,-958,-835,-813,575,-178,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTQ3", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{-861,547,426,-672,731,-833,-381,-994,450,-978,-726,-436,702,-272,282,178,-798,-877,-525,-338,812,537,-197,548,826,51,-300,-971,969,-176,823,-292,107,826,166,630,-949,577,933,627,736,894,469,-334,-495,-961,-905,-396,297,-830,701,-514,107,-473,627,401,250,415,530,-245,692,702,-319,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{-147,-638,-388,-104,-700,324,195,-261,953,-757,863,-672,-470,-353,-248,-725,302,941,-682,369,-416,397,-998,-868,640,225,-304,98,365,-542,738,-101,340,160,-310,537,-813,900,707,163,-546,-700,982,-869,-250,43,431,-846,-725,729,-442,-55,-506,-361,273,947,272,628,333,-609,-522,-871,3,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Long:LTM4OQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{-781,-389,-755,-786,-530,206,-180,279,-302,842,858,45,-657,579,255,-997,-834,786,-385,-202,695,417,259,-210,228,376,791,324,-268,376,-578,973,209,559,763,387,-609,506,-618,-735,-981,197,-934,-548,-664,991,-585,191,-45,-238,198,148,788,-50,695,17,333,-521,162,-817,-694,-759,507,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Long:MzU=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{-873,520,38,353,-602,131,488,-129,832,-195,58,438,72,739,-449,856,677,-33,-636,-677,-790,-769,771,-898,74,618,-521,-178,-843,148,-393,-846,300,-438,-945,-166,876,491,-545,-874,-618,-93,-633,-351,805,-688,186,236,768,457,374,964,978,-434,-980,-152,331,-3,515,-188,179,95,157,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Long:LTY1OA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{-237,-658,454,82,-585,715,326,55,521,386,575,444,-23,-665,324,-274,-755,842,629,411,106,-109,-409,-672,-77,162,-151,159,119,390,183,-418,-523,972,-147,629,224,-363,469,968,-626,-304,-311,-335,749,-41,-157,-286,298,-275,698,-894,867,-540,-973,546,384,-287,358,-942,-382,-354,373,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{-201,520,647,-871,68,263,660,-33,296,-102,260,-244,503,174,-713,37,945,30,31,-829,-192,-31,-992,687,-446,801,-681,-692,16,-181,-633,-298,305,-284,-771,1000,-808,765,62,-853,743,159,612,-705,660,-222,81,-403,-944,-886,416,7,-810,-407,-586,987,-921,126,958,-609,-181,-648,154,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Short:LTQ2Ng==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{-525,-466,-545,697,-193,415,-687,-370,701,-978,-332,182,88,780,984,-988,855,610,139,-657,696,891,648,-648,-980,-796,355,-470,-307,-596,573,993,-555,-241,-813,-227,281,196,-677,128,409,981,871,-970,-130,-411,332,183,293,950,-729,-883,-966,798,-1000,-740,334,399,-997,192,42,-385,-884,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{987,-70,257,882,-836,-912,721,-763,-616,-716,765,-214,-252,-241,158,-690,-681,-311,-469,990,408,-467,224,-728,867,908,702,-275,-343,385,282,-241,-586,698,665,663,-686,467,-391,622,114,721,976,19,959,-724,-681,700,245,320,-773,-359,658,450,-318,926,-656,-170,-126,-105,106,153,-920,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Short:OTU5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{-269,959,713,786,877,119,-603,-463,495,633,-487,-249,236,404,984,-539,-397,-381,122,-582,333,29,-191,618,-976,772,137,-893,817,949,267,-767,379,195,-954,86,-982,-861,736,34,352,-168,-871,-999,-366,-39,248,-620,-434,-13,860,351,257,-755,-875,781,795,-561,821,817,876,8,-193,-121}));
    }
}
