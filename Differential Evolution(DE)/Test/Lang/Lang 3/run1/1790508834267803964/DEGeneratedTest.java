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
            new int[]{-634,809,231,542,-15,-350,-1,278,402,-59,-723,896,601,-363,-293,-413,492,-898,41,771,579,-477,986,6,-450,-298,975,964,-958,944,-31,761,-117,-632,343,770,421,-498,-870,-331,216,280,472,923,-653,659,989,170,822,-974,-50,-969,-779,249,655,778,548,800,-813,-34,521,-284,-116,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{626,536,696,119,756,444,517,-443,-58,-682,622,534,810,-315,757,52,-436,-675,92,-490,-984,-69,553,183,-514,636,677,-856,-352,386,-75,225,-597,847,-701,448,-759,-19,-637,1,-837,-262,-677,-730,-787,-596,689,-300,957,-936,221,332,810,-632,-626,748,29,331,-782,-148,406,-762,996,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:Ny4wOEUrNjg3", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{-474,708,1000,685,-198,-239,-921,278,805,-1000,-723,616,139,-423,172,300,821,965,-180,662,902,-116,1000,33,-191,-1000,403,1000,147,-1000,-318,1000,510,432,-77,774,27,-498,1000,51,-1000,280,564,48,-561,595,311,-63,57,-224,-979,-969,137,-618,-424,186,136,-34,621,-1000,-272,311,485,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.math.BigInteger:LTIzNjExODMyNDE0MzQ4MjI2MDY4NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{1000,-433,437,497,517,-506,398,328,1000,263,402,1000,1000,-709,-1000,-336,-1000,-389,356,286,323,-565,-347,-372,558,43,86,236,736,268,-40,1000,723,251,775,-331,-600,-164,-40,-811,-1000,336,-107,-561,278,-300,-85,-1000,602,-1000,-823,1000,865,-389,255,373,-1000,-142,-941,-577,1000,552,550,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.math.BigInteger:NDkx", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{388,-491,-318,1000,-553,-909,627,469,1000,-1000,-847,1000,-432,-1000,-774,-928,-408,-144,-858,504,34,89,588,367,-556,-112,-575,-240,575,-1000,-1000,-23,-778,1000,-18,-142,-10,-50,1000,-904,-1000,1000,232,423,-96,762,1000,-645,-301,-1000,-1000,867,-178,-1000,-856,-357,-348,537,-250,69,923,1000,-666,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{-833,-1000,-740,-478,-994,-117,1000,-1000,533,370,-1000,1000,-63,-49,-1000,576,-162,-710,-831,1000,-501,591,1000,356,913,1000,-1000,-1000,1000,-1000,-262,-1000,-179,1000,-1000,1000,-1000,-932,85,-1000,-1000,1000,978,1000,400,709,1000,1000,256,-695,637,-1000,-526,-1000,1000,-956,-1000,-1000,1000,-1000,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{-74,678,-723,-877,227,449,-318,-379,633,24,-176,571,342,-574,-191,337,-200,-291,180,8,107,-857,-873,-278,975,-570,-783,359,509,-613,31,362,352,-92,-306,-558,-861,-32,936,-672,-425,914,-16,780,-9,-624,-587,-301,-257,-266,861,-719,-547,-712,441,-73,-257,-986,609,-126,-114,112,-699,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Float:NTM0LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{-758,534,-158,799,-770,699,-678,-460,-73,562,503,-453,322,97,878,938,-695,-187,-98,38,190,-398,979,201,-296,650,-137,-101,924,-894,134,836,-403,-16,741,-725,477,-558,-726,-898,924,-991,-434,-794,822,590,-480,721,-884,177,631,-634,-303,-963,594,680,-768,-114,559,-713,607,38,-821,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTEx", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{739,111,865,-951,362,120,488,487,94,982,258,-491,-748,-281,953,371,-196,446,-996,242,-121,996,917,72,144,864,219,220,591,499,-578,-792,-199,-626,512,74,684,-434,719,-256,-201,611,-86,519,-77,315,-103,-637,-711,888,30,7,72,265,203,-953,-891,826,972,207,-408,-166,-168,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Long:ODc5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{-361,-879,-244,195,178,-70,662,177,597,-313,-69,-983,-224,541,464,-464,-862,-621,687,465,687,-593,-381,340,-20,-483,8,-470,-178,-981,-796,-819,-428,-839,500,-213,464,-592,49,-139,-917,-98,142,777,-773,-251,608,-819,832,-204,444,-359,-732,463,-635,-848,-571,-209,-99,-32,304,430,947,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFLTQwMg==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{374,1000,1000,-405,1000,1000,1000,-1000,325,400,-370,511,1000,-687,-1000,-656,653,613,1000,-398,-453,-699,-547,646,-1000,1000,1000,1000,723,1000,1000,-1000,1000,1000,-1000,870,1000,-1000,1000,-1000,700,845,783,-145,1000,-533,-1000,1000,-141,703,1000,251,-970,562,-433,982,507,1000,-589,-1000,-251,1000,-1000,-686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Long:MjQy", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{521,242,836,-505,-1000,788,1000,-955,365,256,-1000,-1000,777,1000,-1000,-707,226,766,501,429,328,-1000,-1000,1000,712,455,-957,70,-214,-292,-954,42,882,298,847,-251,585,489,-679,-609,85,177,-180,1000,-572,517,1000,-459,-383,-575,899,752,-844,305,989,-1000,399,491,-253,117,607,64,-428,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.math.BigInteger:OTY5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{788,-969,-469,667,-1000,-1000,924,240,1000,-1000,216,-135,-1000,518,613,-69,311,709,812,751,-1000,-241,-1000,414,824,1000,-1000,471,374,-527,-92,1000,430,-1000,713,722,-1000,-304,-477,1000,360,-1000,672,-62,-200,1000,1000,-345,371,393,222,-256,-26,239,-721,616,873,609,-1000,-248,1000,-510,-739,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.math.BigInteger:NjA0NDYyOTA5ODA3MzE0NTg3MzUzMDg4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{1000,-32,-1000,-421,186,-601,-173,297,669,-1000,309,-2,-890,782,1000,1000,352,799,-574,-548,427,478,393,-680,-495,-466,-593,380,-32,-685,-118,456,-598,-1000,835,805,444,439,-477,1000,-410,-1000,141,-137,-1000,720,692,6,283,452,1000,-145,1000,-330,3,12,424,1000,335,-192,-16,55,47,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{517,1000,367,149,734,1000,-20,-833,-1000,1000,570,508,927,-914,-788,674,134,899,409,-367,207,-405,-372,519,1000,529,1000,81,495,657,799,-1000,339,1000,-20,376,1000,-507,865,-580,514,1000,-964,1000,209,-1000,-1000,757,-979,645,70,1000,-474,-25,-667,585,-777,775,1000,-133,287,779,-654,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{237,1000,577,859,-311,1000,1000,-801,1000,383,99,-270,157,-447,-711,1000,761,930,82,1000,-1000,445,367,1000,1000,762,46,655,463,-129,534,1000,1000,163,1000,-213,445,-968,-51,-456,1000,1000,946,-196,786,-46,-887,263,-1000,78,-1000,730,-839,-590,-483,1000,630,1000,-1000,-664,1000,-160,-556,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{521,485,-461,395,899,407,1000,-612,1000,-598,179,282,-1000,-356,889,288,234,1000,501,543,270,562,138,-657,-6,-874,-200,1000,458,607,-954,438,407,283,-394,1000,-234,-641,-679,176,166,415,764,2,-733,272,65,325,-530,847,561,752,-359,-640,173,1000,-91,314,112,-878,-113,570,-428,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQ5Ni4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{729,496,-715,122,637,21,-104,368,345,-235,403,406,324,-50,801,1000,798,355,527,-2,398,21,-82,-94,-377,-1000,-648,1000,258,-120,1000,347,-281,-378,-307,923,187,-813,-599,183,490,-505,945,-277,-604,126,-494,212,-522,933,501,-1000,353,-160,312,587,353,-811,-635,-475,-303,629,-877,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:OC4yOUUyMDU=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{806,-829,1000,203,1000,-520,1000,-1000,-427,-988,-984,-735,-66,-1000,192,-654,-451,1000,809,1000,1000,-118,-1000,-1000,1000,394,1000,743,-1000,485,1000,-160,-1000,-591,-792,1000,-427,89,553,276,-975,-1000,581,1000,700,-610,1,494,1000,993,923,-551,-1000,-128,1000,894,288,373,37,-812,187,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Float:MTA5LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{697,-109,-806,-395,648,382,-173,-381,-219,-60,-405,-517,144,594,724,-434,-450,95,-901,432,817,-257,745,-867,905,-559,807,200,-961,-125,590,110,329,-391,835,488,-227,439,125,300,-954,-628,87,941,-991,253,-129,235,502,221,-714,108,635,-23,944,-28,-448,93,749,-296,-585,535,-819,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-755,969,937,971,167,-567,-270,958,-427,531,274,490,-188,-579,854,908,-917,563,-755,-765,-602,442,202,256,731,-695,284,-797,-258,311,-687,873,-151,-550,-540,442,828,76,609,-702,624,-27,460,-207,-414,496,-373,-12,-34,504,-106,-833,-811,597,-237,-798,138,-582,-592,-325,-504,-268,-730,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEwMDAuNTU4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-779,-1000,-267,558,1000,-1000,255,-262,-1000,98,365,-16,-550,-1000,105,-704,-1000,401,-1000,1000,-600,963,539,-42,632,-965,-892,-573,-1000,112,306,-874,89,-1000,409,660,225,-406,504,432,-245,1000,-503,-197,-879,-945,-1000,101,102,1000,-585,-791,947,-1000,626,-12,-1000,314,1000,-16,-1000,377,343,-876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-949,-929,-1000,1000,570,-1000,-448,-49,844,-1000,455,1000,-1000,-1000,1000,-656,-405,998,67,-416,-1000,1000,60,-1000,-331,-695,-815,-591,-222,-30,-1000,-72,170,-1000,-399,631,-1000,470,-1000,1000,178,1000,657,-1000,-213,-554,570,907,-796,1000,-125,-678,-47,-1000,680,-669,-659,639,867,-708,-794,641,1000,563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{286,673,-106,566,466,-204,1000,-726,663,-956,-84,560,-292,-684,-62,33,638,1000,791,1000,-1000,-466,-722,643,1000,1000,-909,520,500,612,1000,394,1000,22,-399,879,136,-796,-243,587,839,935,1000,400,294,-580,102,438,-955,732,686,-424,-511,35,-379,976,470,1000,-625,-1000,637,781,-246,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTE2", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{756,516,818,-345,-714,561,616,-818,725,234,-905,-668,984,490,-585,379,805,928,774,566,421,-748,-821,850,2,-444,-962,951,62,-426,635,247,314,7,221,561,935,-486,-872,-121,646,-951,107,685,-411,812,912,-297,-406,319,-971,-403,67,806,-558,-548,522,-471,-707,-320,534,-634,-569,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Long:LTM2MDI4Nzk3MDE4OTYzOTY4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{1000,1000,-151,-687,610,931,1000,-681,343,1000,243,-57,144,763,564,-3,620,566,211,571,727,-488,-114,155,238,-762,-226,1000,638,521,1000,1000,527,182,588,593,764,-453,1000,-677,577,324,232,962,-1000,605,-572,-267,-179,-442,67,-1000,166,686,-813,1000,-327,1000,-281,-704,-653,-496,-769,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{-147,50,232,865,1000,-1000,-1000,-459,594,1000,-806,-530,-335,-408,-442,778,-1000,-378,-1000,-1000,-49,1000,-1000,35,222,925,1000,-1000,388,-861,-778,-964,-584,-587,-288,-335,622,181,-7,1000,302,-606,836,952,-476,-765,-252,-234,830,-64,-192,739,479,10,-395,-645,-187,-112,567,301,389,-1000,-1000,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{403,807,-101,-203,484,-192,-1000,-305,-380,-487,828,-400,636,506,-484,420,1000,-159,-174,-912,-33,819,-755,-1000,65,-683,-826,-1000,88,-345,-449,-629,-262,635,-425,22,933,181,-341,100,83,-438,-1000,952,-67,324,-25,-519,435,1000,-1000,446,365,-582,-818,-645,-187,-527,343,301,966,1000,-393,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-218,-1000,-511,-513,-410,381,-1000,413,-845,1000,1000,1000,1000,-1000,-109,-1000,189,1000,-1000,-1000,-118,1000,-59,1000,56,1000,1000,63,307,-489,1000,-1000,-807,-464,-1000,-1000,1000,-731,-1000,-594,349,1000,-41,1000,1000,862,-1000,-365,-1000,-1000,-1000,1000,1000,-1000,1000,1000,-464,1000,-1000,-771,-1000,-475,-1000,949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{327,965,1000,216,-620,-450,-1000,656,-1000,-667,-899,1000,-1000,-545,1000,-858,-481,-889,191,1000,104,123,-1000,-1000,-339,-83,435,394,108,-923,58,-1000,761,1000,978,635,-528,441,-1000,-368,66,-218,-209,439,-982,-282,-253,-38,172,-1000,-596,1000,324,-495,110,458,491,40,-33,-29,-729,-763,738,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{425,281,-448,-991,-43,-1000,-26,36,-119,-440,-944,-301,310,-1000,150,-193,862,252,166,66,-1000,-813,681,-4,-171,71,-1000,-1000,-455,-246,345,-17,-150,-631,829,852,429,258,-986,395,-199,-1000,-60,-1000,-271,-1000,-34,433,57,178,-221,95,-1000,908,-838,214,-444,579,-786,532,325,-200,-939,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-174,-134,-907,-551,127,163,-989,668,-873,965,879,578,467,-644,-61,-902,59,951,-410,-925,10,312,-61,939,-177,918,784,-164,-329,9,941,-603,-772,255,-340,-797,878,-7,-897,77,-97,516,-184,366,713,807,-505,-447,-869,-996,-595,727,263,-113,336,710,-384,711,-871,-26,-249,-169,-515,-134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-279,233,-584,-977,591,1000,-428,1000,-438,1000,-500,164,-510,-724,-128,181,226,233,-172,-105,372,-381,-651,-1000,1000,579,473,1000,-471,485,382,-93,-641,441,-940,-400,461,257,-1000,-426,-678,700,183,296,1000,1000,-187,115,564,-457,-590,-460,1000,-511,1000,825,-873,1000,906,-1000,-825,-619,-386,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-824,-542,-822,-472,-292,-67,-305,412,158,-792,716,256,-924,573,-36,160,-903,154,-255,-189,-242,-999,-767,-355,-679,-203,148,-536,-835,-456,-926,171,27,-786,390,-769,-334,128,105,509,369,584,791,-806,120,-843,-791,663,-780,993,-749,356,698,408,-620,-13,-74,-842,-123,-240,531,857,879,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-343,-766,-1000,1000,-1000,-694,-513,558,355,1000,325,1000,686,-65,481,-598,-1000,1000,-1000,-775,412,931,-522,1000,-1000,-101,1000,190,220,-1000,514,-816,-861,-883,-114,-426,-337,-397,165,-706,-1000,1000,-814,1000,474,-332,400,-900,-1000,-1000,400,1000,101,-1000,858,302,401,765,-1000,-371,-656,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{41,-134,-824,348,681,-1000,-502,695,-881,1000,1000,622,1000,756,-671,-332,-516,1000,-1000,-1000,533,-90,442,1000,-1000,527,365,-1000,-632,-799,970,-1000,-849,-119,892,-1000,-205,614,503,126,-412,678,-889,145,73,-593,19,-1000,-869,-794,587,1000,-1000,-350,-300,-690,354,-72,-1000,1000,758,-939,174,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-487,-825,-1000,-699,944,1000,-1000,393,-107,-200,1000,258,-562,-787,-1000,-740,-806,1000,-20,-151,-1000,1000,-38,1000,-1000,1000,1000,942,-56,-41,713,-1000,-550,-402,-1000,-1000,-1000,-1000,-1000,-1000,722,1000,-259,686,1000,1000,-1000,-167,-169,-1000,-1000,1000,1000,-478,175,1000,-1000,1000,-802,-1000,-1000,128,-585,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-551,978,1000,740,439,1000,756,299,-1000,-338,-1000,1000,-582,1000,-1000,580,-703,-421,-840,-1000,-260,-1000,-1000,-386,-44,-546,853,1000,-1000,-799,621,619,-168,-631,815,436,-1000,606,-1000,349,-148,-458,1000,473,580,-7,8,22,-1000,271,944,1000,-1000,-205,-1000,999,401,-662,1000,279,-11,-412,-107,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{501,189,1000,1000,-109,1000,56,220,-748,-750,-714,1000,-72,1000,-826,465,-453,-506,-594,-413,1000,-569,-671,-567,-1000,-334,1000,940,-553,-195,-90,-424,1000,-167,-206,-167,-646,708,-989,930,1000,-47,1000,1000,468,-166,320,848,-1000,-220,59,1000,616,-981,1000,1000,75,314,803,-691,-504,553,-550,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-115,191,-529,-851,288,805,895,-146,-748,57,-703,912,230,182,-578,-836,-388,-170,104,-413,816,-569,-470,871,-77,408,824,-331,-553,-172,730,971,-439,802,-96,980,-613,646,635,-63,779,-365,717,-635,-529,-970,604,-609,-592,-8,994,975,-375,973,-429,-825,-884,239,717,876,-182,268,747,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Byte:MTAw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{-408,-904,-158,-36,1000,-398,-1000,-64,1000,728,100,-576,568,-83,93,393,-216,-1000,123,276,-855,-129,-960,-220,-912,-551,751,-286,269,84,958,40,594,1000,481,1000,141,-1000,-780,351,724,766,-476,1000,973,463,-1000,322,608,-190,-157,640,604,-51,29,-258,1000,-430,-694,174,475,-549,-1000,602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Byte:Ng==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{-205,826,537,-794,-99,-952,177,194,795,63,-218,610,-87,825,-555,-925,-424,-981,-310,562,-626,310,385,623,-674,-967,-48,-912,562,-655,-52,177,-580,-691,-549,-167,848,-884,788,-775,-220,708,-880,-181,773,-561,-873,212,581,-916,633,-1,-398,-497,-614,-659,161,-416,-597,629,431,692,-701,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{995,-560,457,-356,762,143,87,-20,725,156,-574,-984,-280,112,386,444,386,108,606,45,-454,19,357,675,-193,-156,147,709,414,-533,352,-713,371,106,208,883,384,-236,-936,-997,250,-12,-76,137,-988,-603,-12,495,391,582,-839,-504,-202,-752,-676,-486,-420,-264,498,943,59,-560,-967,-683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(double[]):double",
            new int[]{1000,-1000,-357,-445,-903,300,-895,1000,-485,1000,1000,-979,1000,-990,-10,555,1000,807,-429,644,211,172,-1000,1000,477,359,-1000,833,-818,-590,434,1000,1000,-48,-759,131,-431,1000,-411,-117,-328,781,-197,1000,-1000,981,-5,374,624,-631,467,-33,-193,1000,-811,801,1000,-1000,4,1000,1000,1000,-517,818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{202,152,424,-602,614,-963,-103,858,-175,259,-117,206,-166,468,912,-997,712,-682,-519,-811,69,-44,90,-194,-58,536,592,212,758,-887,-977,-726,179,-246,-370,-811,12,-973,-357,568,-841,-814,485,213,624,-725,-425,603,-58,191,-328,540,-883,259,-232,-376,110,300,964,-730,-751,-646,459,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(float[]):float",
            new int[]{179,1000,449,-1000,-680,650,272,345,-713,-1000,-355,595,499,-283,-1000,-1000,-195,-618,-255,-874,-1000,-43,-395,784,-705,-528,-229,-11,-636,996,573,-745,-328,400,-948,-335,-811,-800,-682,876,-454,-776,-1000,451,184,-1000,493,-400,-326,7,249,-273,-720,-478,1000,-932,-1000,-734,-620,-317,783,-683,188,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{-276,-325,956,833,3,24,374,777,-213,-240,645,-463,-773,-172,5,397,-397,-278,-450,-800,-591,492,973,-315,-369,917,-985,-410,-614,895,675,451,-309,839,716,-58,4,-609,-168,-754,-582,136,-67,-471,-106,-841,450,833,788,-741,312,-92,23,-565,288,393,-220,-141,-233,-430,-672,966,738,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{-110,749,-121,-446,-215,-977,-669,443,-922,77,960,-67,792,-512,-786,110,-122,465,40,713,-527,276,345,-821,383,-235,-631,861,393,708,902,-993,611,-100,36,-542,-657,30,326,468,-295,-375,-907,-408,-417,-339,-34,-848,-571,790,693,-575,856,558,766,429,-963,475,-550,931,500,348,461,-878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{927,-594,-437,-181,50,422,-1000,-115,363,-1000,743,1000,179,-409,-916,822,698,1000,1000,-1000,1000,-882,63,-263,-504,-402,-1000,-662,-248,1000,-259,-267,-1000,337,-58,780,-1000,-402,452,248,118,-349,629,1000,-360,868,1000,1000,866,1000,-57,383,-226,-1000,-1,917,1000,-289,1000,1000,-787,-400,-218,937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{299,669,-236,944,865,-348,455,311,-275,84,-467,-74,436,-789,-381,266,-432,-429,-226,777,402,-638,-846,949,-580,-909,-138,778,-902,-120,59,-970,-494,916,325,-956,469,432,-608,853,-361,346,-102,-716,-266,-768,596,408,409,-366,588,-783,735,-96,-419,-271,-860,-252,-105,840,758,407,-982,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Short:MjA2", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{-942,754,-1000,32,206,287,-646,-1000,548,630,1000,815,-63,-1000,-431,697,-484,-1000,-373,1000,555,-837,-543,-679,1000,361,876,953,-1000,-309,-825,-179,-552,-213,471,358,-328,-788,-366,178,-156,135,533,-125,-28,-387,-1000,-201,1000,-1000,-558,388,828,482,731,-544,-683,721,998,892,301,128,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Short:MQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{177,415,-876,-212,-935,226,367,175,-996,-99,438,-281,-206,901,-677,783,495,-724,-312,-799,610,693,-873,-919,60,-756,-994,-594,-97,652,-181,251,286,147,972,906,171,-895,-447,-227,991,315,-242,464,-402,-305,395,270,614,169,159,-159,602,-511,-778,755,358,-168,-927,515,-935,-841,552,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "max(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTMw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{126,849,-824,653,-301,190,-1000,1000,-529,1000,-29,-836,-348,-1000,-240,-1000,-1000,-1000,825,673,581,-1000,-897,-1000,-151,-182,-1000,-776,1000,1000,-1000,-1000,681,1000,-577,1000,876,-735,-666,961,1000,1000,1000,-44,1000,-144,765,-10,208,1000,860,295,1000,1000,83,87,1000,523,-333,-968,1000,115,1000,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{238,467,234,264,-235,-7,562,-432,-772,453,-482,506,-275,-684,-768,418,253,-251,-141,-52,-975,-678,215,-805,-593,-770,-846,336,-263,-59,469,360,-624,271,-538,221,-571,-364,698,-82,-259,-590,-79,821,44,-788,634,885,391,-763,-595,-697,393,773,-373,651,200,-535,-842,553,653,19,-979,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{334,-420,-178,100,1000,-720,-20,300,996,65,-946,-907,611,-148,557,-364,367,119,-417,951,166,-999,691,836,-35,-75,238,-97,-311,-479,-104,955,-662,-179,982,-909,-681,-645,-1000,435,-67,-374,289,-826,-376,-296,57,-285,110,154,-762,-782,864,394,578,-513,-492,701,165,258,659,-443,-803,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(double[]):double",
            new int[]{-49,185,-166,64,57,443,-528,-416,427,1000,911,-1000,749,-1000,-361,1000,-141,202,-487,724,-89,-39,-460,675,-913,255,-1000,-462,1000,1000,-1000,469,606,-169,-36,406,-188,-241,35,1000,-805,-429,-253,-663,-620,534,-764,41,-561,959,326,-201,1000,-429,1000,1000,1000,311,-472,-156,1000,95,-4,496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Float:LTkuMjIzMzcyRTE4", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{-301,-253,672,524,246,428,788,-235,-370,673,818,-727,706,441,194,697,165,447,-871,199,-974,237,-635,842,-525,-803,-281,580,-468,985,403,561,317,784,-751,-470,256,485,-509,989,482,-510,526,498,-96,129,239,974,918,904,-125,203,-401,-307,193,882,-655,891,708,-513,423,-952,179,-9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(float[]):float",
            new int[]{1000,-119,-1000,-436,1000,-90,-477,-699,487,-188,363,-645,-725,468,-1000,-607,-808,-108,802,1000,1000,-231,-1000,109,-634,721,74,609,27,-244,-779,631,296,-1000,-914,-1000,-558,-764,-742,454,1000,-779,537,863,1000,-303,751,-271,-1000,207,-501,-1000,519,518,122,-1000,1000,-1000,-969,1000,150,-947,-410,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTkz", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{-571,-724,432,257,-93,-601,779,-226,-618,410,-220,-233,631,117,-178,179,-657,50,-876,861,-816,172,826,-745,-392,-325,-511,428,-815,-89,62,-265,727,-510,-18,-338,622,620,174,-264,-588,-103,-574,-235,-106,-98,849,141,795,204,950,449,425,-312,-758,-790,-393,454,-780,-775,677,988,67,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{921,-574,-134,185,808,-720,618,417,30,-694,-188,694,-383,-829,-72,667,-914,-927,435,48,-775,497,-884,-900,518,182,-776,25,-999,-8,-825,493,-477,-505,688,-997,-812,-658,151,288,-890,-191,310,-427,481,244,-620,-901,-519,-442,115,-924,535,-580,501,-35,-943,341,303,-178,969,445,-60,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{1000,-883,-111,-13,-218,-584,-570,761,-534,-899,-967,-69,-397,-163,-1000,-975,-692,-36,-115,387,275,532,-1000,-116,-624,-204,-204,372,869,-179,769,18,-793,23,-263,-1000,538,895,101,982,847,-14,579,1000,-3,-737,247,969,-1000,193,-43,-527,-159,-815,-359,-309,618,-341,609,110,-1000,-259,-549,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{413,873,-173,-56,-790,351,720,283,284,-479,783,-777,-596,11,-82,584,-806,24,-142,-451,685,-967,-38,215,-926,-346,-570,-838,-347,-679,-500,822,216,384,48,152,114,-634,415,-946,-432,220,250,831,-130,656,703,33,-369,875,-612,-983,409,361,194,-265,681,787,-739,-637,449,-446,-817,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Short:LTQy", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{-670,-192,902,-586,-421,586,-265,-797,-332,995,-512,-369,-256,674,48,493,885,-968,-63,-26,-11,25,914,-440,175,285,966,-448,826,-254,785,-597,-609,285,207,-834,378,121,811,-819,-411,-313,912,782,-439,555,-499,-680,-540,576,344,41,-821,-89,423,-489,-685,-179,158,149,-161,-795,462,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Short:LTIxOQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{971,98,340,275,-951,-766,-120,-951,-756,-219,-457,368,85,563,-854,-499,-53,-237,437,-714,402,928,351,-970,-132,-165,88,-667,202,-174,-607,-272,-497,-476,535,56,483,-857,127,-392,-535,1,-858,258,331,334,287,-977,526,962,-781,-197,640,-985,8,-599,993,496,-277,-633,186,-772,37,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "min(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{-722,809,84,204,-871,495,483,171,-924,936,-23,809,37,218,296,374,-442,-683,80,-533,-516,-457,-759,467,-364,299,-605,-20,-474,-759,-192,631,-747,-679,171,988,-179,150,429,362,367,683,-99,849,-971,292,943,48,-478,-28,546,641,876,631,426,-798,-730,-782,378,493,368,-234,-985,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Byte:Ng==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String):byte",
            new int[]{-161,-92,314,-599,1000,-296,208,-1000,-446,989,977,-599,1000,-925,986,1000,-1000,687,1000,853,-1000,-795,4,1000,-916,1000,915,-269,-961,-498,62,-1000,384,-177,353,730,359,-1000,-1000,1000,-130,149,-659,20,-938,-1000,966,-520,254,-276,-795,-1000,-399,-95,1000,236,1000,-377,-580,-844,-495,-578,684,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{232,504,572,-18,-659,964,-451,-761,-73,-716,-709,688,224,-948,-256,-472,321,933,-65,-305,535,-736,15,-468,-259,608,680,-803,800,-917,827,816,-529,-362,406,-69,694,-370,878,78,-18,-995,409,-497,-198,214,-977,-67,-706,562,260,118,455,124,611,-605,987,-402,-31,939,-566,482,-355,546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Byte:MTE1", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toByte(java.lang.String,byte):byte",
            new int[]{515,115,351,569,-821,-89,-668,300,-512,-742,-602,146,879,-802,-825,-1000,1000,1000,617,-1000,-854,165,-489,-438,-605,-185,732,-1000,1000,-1000,716,434,-471,-524,-159,-181,-24,616,1000,-535,402,-483,496,-476,1000,769,-308,13,-331,553,2,999,-588,186,788,-576,1000,-947,717,-227,-278,591,-535,869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{444,-313,-714,891,623,176,-606,980,878,-581,-780,-729,-186,-373,-857,251,-822,-428,627,378,919,-54,-288,-944,255,370,-585,-372,624,308,248,612,-545,-945,70,559,-866,-45,27,-767,-276,-933,504,-905,580,577,-338,512,-722,471,-583,-478,30,182,-497,542,102,-757,-336,-46,-321,337,940,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:MzI0LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{851,324,499,-648,-791,914,684,-219,-577,95,-975,-227,252,373,-723,134,286,266,-979,-619,-19,-644,417,526,517,-860,-535,99,-544,518,68,-867,152,-370,-850,-888,-610,-229,681,-906,-147,157,-444,-393,622,421,999,239,208,891,-620,-625,319,940,179,-16,978,-995,179,996,473,219,-64,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{854,569,551,447,125,-583,841,958,-84,849,-733,89,-294,435,-898,-244,882,777,579,240,473,622,51,-798,247,235,950,216,990,463,-990,-367,679,-28,589,482,-309,307,-65,998,196,-375,737,-619,451,-812,-570,-566,-184,93,358,-169,-953,-951,130,947,616,-504,298,-593,-320,-115,-210,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Double:MzE5LjA=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{835,319,-743,-978,-158,706,-588,272,-85,-394,-382,-274,-21,-973,717,-88,643,358,-813,186,841,474,222,771,347,-967,-155,724,925,849,457,85,-237,237,587,330,-733,-561,631,171,-692,396,-135,-251,-445,416,128,-710,696,-366,216,418,-18,-489,277,-495,838,-874,-763,642,55,-517,681,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-179,246,-574,890,324,66,181,660,702,-788,-277,-791,-59,568,-278,-52,-510,-564,-857,320,-437,534,-529,924,106,431,-734,-49,384,27,156,-753,-154,677,-855,-326,362,-238,783,-940,263,-922,-817,9,-318,302,125,-872,620,-69,-444,-218,-92,-350,700,-571,753,812,-337,-229,912,-88,942,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Float:LTM1OS40NjM=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-923,-359,-959,-537,-373,607,411,248,298,256,237,512,27,871,609,761,-408,-3,493,-824,38,464,664,614,698,487,292,314,-744,-294,-781,-657,-40,936,-196,-840,573,700,-166,-684,-68,98,-529,-711,99,-501,414,701,4,-313,594,-217,-803,607,553,-169,881,644,371,-383,744,501,527,254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{-562,625,614,-746,-240,-951,150,-755,321,199,-423,546,-441,201,-787,232,703,50,-130,715,785,984,-989,-109,-72,525,346,951,458,55,857,26,-139,-867,88,852,-705,-127,-337,-621,476,680,489,-81,87,370,846,-223,559,-706,677,-418,-927,-546,-218,-439,919,823,326,-149,977,47,-890,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Float:LTU4MS4yMDM=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{37,581,389,-797,866,-99,-341,-117,557,-88,807,801,500,912,-691,690,-462,-442,708,644,-657,-845,554,-254,-124,296,-843,-244,880,-624,65,171,821,-160,985,-992,-577,160,45,-606,-15,-540,736,-311,-935,505,-341,-718,-225,310,283,-598,937,-975,-666,349,-349,-56,130,-585,-552,-489,-788,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{570,663,-443,-287,-443,-237,-46,-543,-507,-728,-25,-659,496,-115,380,-507,275,872,-447,650,-479,-737,638,-109,422,-459,168,618,580,-984,529,-121,307,-400,683,358,269,126,-9,-592,736,510,664,-659,-655,104,-575,-125,-809,-628,275,-907,-694,690,-78,-423,-831,497,-603,-517,-285,-710,-353,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjkz", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{-749,293,-291,532,296,954,613,393,-958,397,627,-993,-375,-934,305,646,465,-817,-362,910,-574,756,-646,323,807,341,741,-764,70,331,-61,-265,-101,161,769,533,188,141,555,-186,850,-106,93,898,-33,333,-31,231,720,956,761,690,797,877,295,-507,-467,450,-899,-909,886,589,-500,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTY4OQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{935,949,800,-689,587,361,-629,-996,940,-996,741,859,155,-579,338,-144,-75,-570,445,-488,9,-550,301,686,289,961,562,-125,-452,891,754,157,-43,985,222,-517,619,474,377,552,406,772,-924,204,-193,299,377,-79,-450,68,-948,819,313,174,825,348,-931,-36,508,-216,-754,347,944,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTYwMg==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{243,-602,9,-307,641,265,774,-139,864,-801,-618,-558,-963,-515,184,3,296,28,278,599,914,-623,-347,-190,258,-873,921,997,-171,755,-220,-237,-610,-762,239,-952,-434,-944,966,-71,184,-424,-921,795,108,-789,-792,291,-660,-744,694,202,-225,840,455,-750,-870,40,-798,-304,-678,898,470,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{-945,-635,-418,-670,488,119,-584,-481,347,-147,-522,-309,948,750,485,-2,-825,597,-436,271,186,878,164,223,865,-80,-468,823,495,148,992,946,880,-460,-295,519,870,977,-809,-36,-900,-663,-537,-24,-363,886,308,-635,175,835,-990,84,-537,750,565,-648,-305,-74,346,-687,759,-212,59,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Long:NjIw", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{36,-620,604,378,-859,-960,847,248,983,237,-6,-329,957,110,726,118,884,5,-532,-912,573,247,972,407,96,690,-245,857,173,271,56,-53,-213,803,385,-336,-109,866,351,676,-63,795,35,680,-194,-521,-158,-718,-287,1,-474,182,644,-53,868,-765,-11,-336,-662,478,-831,-315,23,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{283,533,-172,937,480,402,42,376,51,-713,-66,374,-627,379,-144,0,-356,451,161,-697,141,-12,-107,840,529,-392,940,270,199,232,-135,275,-471,-344,685,-334,-296,647,778,-589,240,-481,782,-438,758,-750,-146,-432,-335,-374,3,-703,-570,664,-685,-751,-100,592,-149,-106,-295,70,-230,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Long:OQ==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{942,-959,868,-574,790,978,-782,-212,-566,87,-104,-736,-92,115,869,516,-274,116,133,-979,-623,728,-622,-248,-97,627,-592,696,321,648,812,6,-434,-755,-545,168,355,463,228,-531,-67,396,219,256,988,218,-51,-985,-791,-673,658,-885,-71,851,763,-753,-911,-129,-474,-997,-631,22,786,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{78,-464,179,176,-218,-287,822,-396,874,-829,513,917,-820,-765,-158,239,-893,724,-886,-19,-335,-494,-495,-515,952,-779,257,-872,-909,366,769,-936,-641,-927,-456,-452,310,856,-561,409,46,382,435,640,-509,-139,-776,-688,502,61,-671,55,966,-859,165,-798,648,-498,488,362,495,-307,-994,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Short:MTkz", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String):short",
            new int[]{292,-193,-742,-995,246,-251,250,828,-865,588,-907,559,-403,344,146,-549,938,-476,-523,-268,-936,286,174,-180,-837,929,-69,967,-270,-763,-176,335,-738,-623,-651,298,-978,-46,-743,-616,610,-388,498,659,-193,-853,668,616,107,-541,760,-358,682,884,728,22,145,-530,405,-938,-607,-467,636,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Short:LTc5", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{945,-104,-591,-799,226,-484,-74,-101,-213,981,-886,519,210,-331,-474,317,300,-368,439,-510,-796,912,-854,695,-254,-671,-588,-655,-837,591,943,674,589,869,-167,-867,203,-783,-606,944,760,-880,-485,-174,394,-693,744,805,45,-471,676,-170,-375,-555,879,-564,133,-39,634,-870,343,432,-987,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Short:LTQ5Mw==", DEReplay.run(
            "org.apache.commons.lang3.math.NumberUtils", "org.apache.commons.lang3.math.NumberUtils", "toShort(java.lang.String,short):short",
            new int[]{948,493,-239,-700,-527,-328,606,256,-74,-589,152,-806,226,-804,108,593,-487,88,-635,-1,627,974,288,434,-215,-172,931,-238,-39,-88,887,636,690,155,-437,-858,-475,-822,148,601,-771,-639,239,-907,-67,-841,-803,-741,946,481,616,642,684,-652,33,453,-728,293,943,-7,779,-13,532,-816}));
    }
}
