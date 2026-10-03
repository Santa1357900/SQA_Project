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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(boolean):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-621,651,565,656,-700,-429,489,-142,-165,673,110,406,-861,636,745,-302,447,826,399,-310,-280,823,-43,955,-51,-22,-330,-470,-67,129,964,290,-902,667,-845,626,-336,-248,138,-152,-691,593,784,915,-634,-517,401,-134,-453,502,-588,594,208,711,-869,550,-767,-987,-552,326,797,-145,-895,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(boolean):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-910,-18,-737,197,-492,-886,76,910,443,-752,-109,-83,-400,-817,383,595,-473,881,-686,260,-229,-191,-309,-600,311,507,762,-646,968,405,796,34,-781,977,43,-633,610,-147,-118,-269,-212,737,715,-126,59,-349,870,-447,-654,324,-474,436,427,409,416,689,-568,-427,8,118,691,531,-304,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(boolean[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{932,-469,497,-1000,1000,961,183,219,142,408,517,446,-146,1000,-261,-116,-250,1000,157,-96,200,1000,491,216,556,773,413,-657,822,1000,-302,214,1000,752,-686,-1000,249,-1000,-548,916,823,-640,279,180,-679,-415,-1000,-1000,-9,316,781,-1000,-164,46,-1000,487,-138,227,-832,-744,-823,318,-720,-678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(boolean[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{58,-498,-824,982,139,-456,523,857,352,689,404,716,-536,-158,-277,-824,-670,-928,592,395,-880,-695,540,9,337,874,773,-126,-676,-638,22,433,752,-301,841,-159,-25,72,-870,321,59,-184,137,663,811,189,-788,-747,193,-310,-806,574,-878,402,469,-256,864,751,444,297,-26,-985,493,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(byte):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{181,-783,-771,-383,369,-313,225,959,554,531,-997,-895,-150,-258,-36,210,-536,682,634,-183,817,209,-187,901,-869,-150,-5,-420,-427,118,-686,-209,967,-395,-418,319,-848,-363,294,235,205,-41,-125,5,861,-844,894,-657,-236,-644,-508,-823,438,111,847,70,-764,-689,143,856,969,-517,228,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(byte):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(byte[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-384,-623,-140,-655,-728,423,220,-753,-3,106,-803,450,-909,389,-99,640,-212,19,871,883,206,241,-414,609,-758,272,-457,165,847,-785,579,170,-21,-353,-50,-407,-239,-544,805,994,-94,685,-911,-301,-709,-898,-302,-214,722,8,-274,-257,-604,426,-954,387,332,-825,123,-772,144,626,-36,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(byte[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{566,-988,932,-267,-481,905,-673,213,866,-254,283,584,756,-594,-635,670,275,-908,996,-261,228,438,695,346,-759,-345,-86,-748,961,844,-450,28,36,-964,-3,653,799,950,768,-94,-946,-433,-917,699,252,-366,632,623,463,988,720,-91,20,-806,414,800,-502,411,-33,-128,-249,-377,793,243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(char):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-114,973,482,-767,758,62,652,235,-321,-52,480,63,935,-215,884,815,206,148,-504,408,-241,-879,-18,11,707,576,-311,364,813,740,879,-764,-280,30,197,455,-528,-968,80,-857,709,518,327,34,31,-592,209,110,-340,-269,-816,-834,784,-220,-485,382,714,-71,929,-523,-639,-167,-190,-366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(char):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(char[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{58,-229,-71,944,288,-220,-97,256,-857,-811,698,-476,32,-144,248,-464,-212,-771,935,-438,-653,863,941,444,65,977,923,56,-794,-994,-456,380,41,-274,353,446,608,-537,-628,732,703,-854,-659,261,942,498,-929,-388,841,569,556,-688,788,-49,618,-297,-510,-455,-289,-781,29,-465,223,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(char[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-826,-308,-832,716,804,519,-32,498,853,693,-95,107,510,-623,-687,-536,361,-475,-455,527,791,712,-592,-870,-560,-98,872,-756,548,634,-667,-82,-115,-247,-251,-810,611,712,-254,-413,-79,-259,463,492,-422,-278,-666,-384,-410,475,263,-908,-842,-644,-260,-721,772,553,303,-712,-116,-729,-537,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(double):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-366,999,-782,113,-510,656,-869,-575,61,445,-441,-612,-653,-463,859,-676,-239,-678,847,313,-37,971,-821,-9,-208,469,90,-789,-646,-152,190,478,-266,-608,842,-208,599,-353,92,445,-553,226,-943,-779,234,595,220,441,-304,289,197,-665,-215,360,-830,-646,345,221,-595,172,-588,525,98,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(double):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(double[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-689,-69,820,-947,-36,15,206,-625,104,-423,438,-788,-629,660,572,170,-487,153,890,-546,-726,540,651,-906,983,472,-27,-968,794,658,-112,103,682,182,866,-117,993,-941,-201,-725,-331,530,776,772,79,-565,-131,373,-175,849,561,322,-166,81,463,975,616,-856,-454,-553,609,-169,-793,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(double[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-823,158,567,-169,-519,640,205,442,-930,143,164,-975,831,541,-581,122,640,399,538,-890,375,-65,886,-454,617,-472,248,547,883,-570,-655,926,392,967,923,665,83,-847,-167,304,907,-581,820,796,227,927,663,502,-391,-191,687,108,-788,-390,723,468,563,-839,118,650,567,219,-445,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(float):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-781,-733,-735,-575,-429,-142,-428,750,384,886,-218,-558,-955,-814,37,-340,-569,686,541,391,509,301,-953,-762,812,389,861,-718,-733,-775,-517,-579,821,145,-420,624,902,-699,34,-481,-153,-740,-493,915,-844,-894,162,-13,-341,-843,971,-897,-510,-107,384,-431,-822,316,-957,938,48,583,698,-64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(float):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(float[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-766,645,-503,686,-775,-707,953,-401,139,-979,990,516,883,-462,-945,789,-792,124,439,-405,723,676,-878,-855,928,107,-389,-772,318,-337,-205,437,218,568,11,36,206,-747,-767,104,733,-546,-419,318,864,402,-815,-451,934,470,857,-693,-639,-250,-10,-37,-139,-745,-361,-277,-134,689,-177,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(float[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{531,-452,-7,89,-184,689,847,657,-653,152,-338,413,976,-272,-772,-521,-143,494,926,783,-212,647,827,-955,875,-754,231,59,369,743,484,-71,-860,-986,-922,542,-520,-519,-242,-794,641,-101,-351,-847,836,365,-756,-624,340,-797,523,-857,-286,-467,441,457,224,-484,-471,713,65,-457,495,-146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(int):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{892,-995,-81,41,328,-779,-194,208,955,-655,138,960,692,-716,-838,334,-496,239,104,-427,-498,-553,236,-199,200,-631,-34,-546,889,647,482,-147,-931,249,351,-424,-874,-876,652,-655,-964,-559,714,37,811,12,350,-882,559,-146,-429,-682,602,12,-769,-318,917,751,-233,-734,304,-998,266,-307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(int):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(int[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-341,-759,514,-566,97,-463,-611,-719,-287,900,574,-227,-419,209,-802,763,-104,-627,-104,72,932,-862,439,-529,-987,389,-737,710,924,45,822,172,697,714,470,-706,236,891,366,-924,-126,-311,-152,727,605,836,-388,747,505,981,-892,-371,-381,-773,601,738,474,275,-486,-67,-701,524,869,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(int[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-561,574,-948,-40,-642,727,196,-73,502,264,-36,410,-908,140,205,891,-778,-522,364,-171,598,770,47,375,-575,744,126,-845,597,-555,511,-335,837,-821,-507,-603,976,-425,423,-13,-832,-782,325,382,-471,41,55,330,403,18,607,-697,-729,524,-769,421,-818,-786,226,-124,483,-755,396,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(java.lang.Object):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{829,-779,-504,495,149,243,71,32,-774,-166,-784,-649,-347,-761,-569,-973,-444,197,951,883,-111,781,699,811,-2,-698,352,-862,844,-489,999,310,925,-480,-270,-656,-419,-396,-539,830,-889,378,-668,-935,948,702,-936,-652,-39,127,829,405,930,-328,388,461,156,-690,-493,584,-697,652,4,544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(java.lang.Object):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(java.lang.Object[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{893,721,458,-777,-405,-454,129,128,687,316,-865,-605,263,-906,-594,622,-293,-216,502,272,1,-337,-612,-871,-130,-393,-488,-938,179,192,987,937,-223,-228,744,-107,-161,-967,681,-807,706,562,-993,-618,-819,-491,-709,-426,-687,555,-975,370,-219,-904,900,-902,209,789,-519,-118,924,-566,-556,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(java.lang.Object[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-136,114,795,610,-311,-828,816,459,175,858,-328,-339,594,-695,-439,-833,153,478,-884,-481,-369,-843,851,129,103,-387,-866,-764,853,-363,-294,352,413,-508,-188,364,-510,747,213,498,496,-981,485,-186,-634,-852,-670,57,-600,-494,-66,303,-16,-198,788,87,-789,12,871,469,660,867,590,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(long):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{640,521,28,-943,-854,-381,842,-956,85,-748,-799,580,-617,-695,85,-651,-932,466,733,-177,-387,-963,330,-662,-692,-30,286,-859,-714,-771,763,-425,-956,-505,-168,-743,-977,165,926,-170,138,-369,89,693,-965,901,952,-64,279,759,314,666,673,-128,752,-122,502,522,783,80,-420,-36,-259,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(long):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(long[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-804,-537,471,521,-124,644,-1000,-163,-236,372,-979,-133,-750,-415,-156,354,1000,-1000,506,-161,-599,-451,-1000,552,-819,1000,398,-248,-8,1000,-58,176,-898,164,-770,968,-651,1000,-176,-84,305,292,-985,-885,-149,280,-673,-1000,738,387,-755,967,87,-244,397,-672,428,973,-1000,-14,358,-1000,635,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(long[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{704,-674,412,226,-565,989,-827,441,-905,718,-689,-467,315,-258,-767,-254,814,-561,-513,-539,-883,-123,-157,-226,-394,-355,901,1000,157,996,53,817,-461,123,-688,548,-313,611,-754,359,-24,-989,-933,72,740,822,-228,-316,-644,-791,-828,724,492,-731,616,114,324,-233,-719,360,900,-67,990,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(short):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{491,-123,-744,-767,-787,135,-429,-548,946,51,-431,805,962,166,-671,571,204,992,-924,512,774,68,371,308,-588,439,-615,-379,772,857,-122,718,150,-861,59,749,-797,-160,-752,-455,142,-311,921,-901,-82,-921,587,797,-7,-686,-241,-137,-580,-732,-772,-468,-817,-319,-176,-4,-2,366,917,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(short):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(short[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-212,771,5,387,-601,410,-388,-791,-771,-893,507,-970,948,828,911,812,-108,-800,-260,-378,205,982,332,175,-268,-660,-811,399,-100,85,454,-984,30,585,-211,-303,-226,149,-586,464,-277,333,-858,718,611,-148,440,-480,4,-441,-225,-453,-138,289,-194,-436,975,294,336,132,880,-560,-633,850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "append(short[]):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{163,-398,888,-145,-67,-162,296,425,878,672,-802,-971,888,892,446,656,686,-207,875,-364,-817,942,-855,-783,-181,170,-287,481,890,719,-603,319,-959,980,-24,788,-614,-607,302,273,30,691,537,576,-809,-65,-725,-685,-463,-457,-429,-200,897,-139,181,980,-913,-237,-323,-59,-795,185,-456,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "appendSuper(int):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{-508,-531,-287,11,963,-655,-741,-669,-288,860,864,802,895,630,274,-788,277,-382,857,-198,467,-806,-668,-206,-35,-729,-22,366,-108,-289,-952,-949,-833,-890,643,-654,594,260,-341,447,-173,-881,593,-560,497,-31,953,-977,-58,-421,837,-74,904,-350,-183,498,-671,-377,705,685,973,969,999,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.builder.HashCodeBuilder", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "appendSuper(int):org.apache.commons.lang3.builder.HashCodeBuilder",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTgzNzEzMTAwMA==", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object):int",
            new int[]{-385,589,-555,742,-709,880,143,-612,712,33,-890,-62,47,-153,193,-184,869,233,-203,-517,607,-48,559,-659,-886,438,963,-96,-476,919,-685,662,-427,601,-257,-862,562,826,-765,330,-686,796,104,-74,-414,690,-258,472,-312,-152,-971,22,322,-954,339,-303,-962,840,-246,57,-885,449,-230,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object):int",
            new int[]{-575,466,-562,496,-551,212,-970,219,-392,-857,701,653,-446,-730,761,-88,183,-93,-408,741,995,-380,-810,-373,894,-128,225,162,98,-143,522,-944,188,609,345,642,-13,-367,19,369,-329,-1000,-887,-1000,1000,128,158,850,820,-770,-455,932,283,-774,-650,653,-449,-408,358,-912,123,-404,213,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object):int",
            new int[]{-6,-550,660,-822,499,-544,-586,-522,-1,-186,974,958,-893,-255,986,-506,-609,-790,-704,485,915,-598,-657,-347,575,-936,-381,419,-453,-751,270,190,-823,-294,156,979,660,181,32,-556,994,-341,-79,-364,-153,-233,-844,558,-760,-576,157,-48,205,157,-491,-340,-15,-519,623,372,-272,-784,917,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object):int",
            new int[]{149,682,-758,294,-860,-644,104,-210,964,-254,133,-760,-689,992,-814,-695,249,-958,-483,-562,-118,76,-507,661,-581,360,-485,651,-761,-351,971,492,-421,862,346,556,458,-130,-74,-388,-388,-87,-72,511,367,-555,358,-445,-385,-20,389,-985,-650,-76,658,-835,-397,-698,-492,-312,22,790,-89,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object):int",
            new int[]{-393,348,115,860,79,343,-670,534,-762,-340,277,830,-659,-847,368,610,626,-832,238,132,486,-436,-362,-277,932,844,538,593,193,-216,521,-623,308,533,837,128,-399,-737,814,116,-954,-841,-298,-990,694,759,503,826,596,514,-176,339,-83,-237,-587,581,329,-693,644,-469,-820,-773,947,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjI2", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{-577,803,843,-136,986,905,249,64,981,305,767,624,-110,-47,551,263,-292,999,695,665,-862,-194,-731,960,214,-551,-671,589,595,-588,-644,-936,658,372,-348,731,286,-535,-868,532,741,-779,-402,987,419,484,-846,-881,-598,59,676,927,728,-425,-549,101,86,-888,-5,-796,241,-494,-153,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{-417,767,891,892,-284,711,781,469,-730,-823,992,-471,635,905,-843,940,924,-561,-840,380,-649,115,769,-48,-58,-134,669,916,-361,-865,722,-830,-203,-604,-689,-348,-210,-750,-466,782,985,896,-280,-770,-306,872,-775,851,885,420,-197,-532,421,-469,-24,-688,351,846,787,-597,-569,882,54,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{933,819,-762,-864,824,-509,502,-39,-696,414,-103,765,251,-206,481,-656,935,421,-199,832,686,361,909,111,-554,752,-435,-677,-317,378,776,487,-987,889,-49,-229,-371,26,-215,898,175,999,-273,366,49,317,365,698,-711,-227,396,-247,-821,-964,989,314,-509,-615,-718,593,-102,444,-475,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{-603,105,447,-533,182,399,-346,-386,916,672,-710,-328,-48,901,689,226,765,-277,907,-325,-599,256,-684,-730,805,141,-938,490,673,-541,-707,205,433,-669,-861,665,648,551,-719,-748,711,-629,-321,-850,28,690,512,609,71,-679,-276,-691,-363,-216,687,-485,-525,80,-542,-890,-986,-677,850,-544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{-941,-761,515,-718,529,636,745,-834,-584,814,572,-283,-155,664,458,-55,356,-36,-870,-89,77,961,793,-820,660,-498,-346,-656,482,941,993,-810,-444,260,118,-394,292,-683,382,297,812,945,-829,-480,-128,-258,267,-361,-399,841,312,-910,256,664,249,-480,-13,-251,-464,-675,176,-912,-291,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM1OTg=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean):int",
            new int[]{-853,836,-974,-583,485,480,944,-178,893,470,818,-373,-486,-930,-751,-65,-238,171,-122,354,439,-153,509,675,-864,848,362,905,-771,282,-60,732,666,962,-859,-916,487,-652,78,353,-447,857,-210,44,-682,405,529,-828,646,489,-354,-468,-362,874,-800,-863,-755,967,-594,577,42,378,-247,829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM1OTU=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{-435,-81,-410,437,-295,-512,143,-915,249,-673,-961,774,10,-707,963,105,215,-547,972,275,108,639,-627,-937,878,250,-512,919,-280,499,376,24,272,167,-946,336,623,-154,717,-144,-375,643,-941,-430,335,-634,-104,925,92,-680,4,-991,-89,-977,-677,131,-286,-625,499,-30,206,-871,-901,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{215,-275,-342,47,205,867,0,1000,871,19,-1000,1000,90,453,171,-1000,-69,-739,474,-517,941,212,1000,0,664,-500,-52,-359,-441,-698,195,297,966,5,-1000,832,1000,-195,881,-9,-1000,1000,-648,-419,-859,76,227,1000,-166,18,226,-1000,-385,1000,1000,431,-913,736,365,-995,-1000,-806,602,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{-827,855,800,-36,-607,-968,334,438,-582,379,-965,131,-919,67,-524,858,719,117,859,-134,840,-106,-421,-319,-755,-595,-300,139,-546,-132,-47,-778,-298,894,-694,-483,-272,-442,160,-720,-865,-398,791,-603,168,-935,779,-185,-559,-227,865,-544,-654,252,-95,43,669,902,452,823,-718,542,808,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{-692,-27,-69,478,881,39,341,-750,-196,-453,67,550,215,880,388,613,-52,458,-215,-476,-539,447,-362,56,782,936,-832,729,110,71,506,-735,98,-446,-775,-210,855,-241,-323,288,192,792,27,126,-236,792,-994,772,409,946,-888,229,-799,-194,-791,-834,527,-212,-677,234,-171,-10,-216,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{-314,823,-609,994,658,-605,718,715,-727,242,-598,979,-815,111,-841,99,-791,-407,35,-296,936,87,801,-765,-554,-400,781,-835,983,-842,257,-610,-98,-565,-962,351,136,163,772,-317,-15,845,145,438,-874,359,-736,255,-616,-860,159,263,38,821,647,-75,221,416,581,-411,-689,-533,885,61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTE=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class):int",
            new int[]{-900,482,44,-40,817,136,246,-26,214,551,952,-891,950,812,-7,-144,639,223,-422,785,21,-134,726,-883,-632,-980,387,900,-281,-606,-917,-607,916,-16,-655,-564,-620,289,-796,768,139,-402,594,528,-346,-863,-397,521,708,-69,254,188,-197,465,-301,-283,-692,403,550,-797,949,-14,-544,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTA=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{424,374,-476,-69,17,-580,893,201,-298,-954,-102,-338,693,689,-485,-42,958,702,-943,-105,-205,-45,-79,163,701,136,509,-123,641,834,-772,-643,-726,74,870,-716,-515,422,-662,1,655,978,681,61,479,622,717,341,-96,-256,-870,-934,727,345,860,-126,774,-134,-140,-271,-781,424,-156,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{-678,709,-458,-195,592,638,717,-505,-129,229,202,899,-772,-349,-585,638,562,-285,431,30,-640,50,-746,-32,-482,168,575,237,-828,-897,270,780,-52,902,-715,-836,812,-355,-793,-929,796,939,-158,-957,281,832,409,884,202,-35,585,-857,612,455,-428,932,335,-545,594,937,114,655,784,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{-1000,566,-458,396,509,-209,717,1000,1000,-590,202,1000,72,-963,-171,78,-706,-254,1000,30,469,-857,-623,-1000,-482,168,1000,1000,-1000,-871,182,42,-1000,159,-1000,-836,-388,11,58,-695,1000,226,537,-247,281,229,-173,-522,-1000,-1000,1000,939,612,1000,35,-1000,-1000,1000,319,377,-1000,696,490,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{-313,-579,-310,-835,166,336,935,-367,741,-724,371,78,596,-461,443,686,-367,-656,713,502,-477,15,-957,20,-241,-540,711,749,-95,-845,-519,296,766,-564,-941,-745,914,-681,-977,-244,-856,-684,314,931,-191,727,-297,-269,698,758,217,557,151,-518,-488,-21,-549,139,346,-555,151,546,929,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{-216,-209,-92,411,-497,-388,403,-143,-385,-827,464,-760,327,-996,-487,-632,887,349,178,-93,495,910,-648,-623,856,-423,807,-446,-965,-698,-181,743,745,-135,683,146,-26,-329,-367,-872,937,6,702,568,753,142,355,672,-64,-390,350,640,860,577,-791,405,-377,746,929,-716,475,-470,-874,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM1OTg=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(int,int,java.lang.Object,boolean,java.lang.Class,java.lang.String[]):int",
            new int[]{679,-753,633,2,-347,245,802,-791,-598,20,840,-96,-556,706,-268,378,953,-989,400,941,-545,141,-241,340,478,-471,-205,-567,-99,342,742,921,-763,365,806,708,837,262,-443,-952,-888,446,116,-16,-762,-155,569,-177,573,675,-364,-628,-22,-966,-397,497,626,-430,714,-800,504,-191,-182,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE2ODgwNzk1MjY=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object):int",
            new int[]{-419,97,624,552,110,-714,482,852,162,193,-111,883,91,789,-334,-162,-769,-717,236,-270,283,845,857,158,466,-123,262,714,-332,206,783,-3,512,-661,978,-540,994,242,804,142,-874,967,462,787,921,891,-829,796,-79,355,-652,976,519,30,928,-797,330,-535,-106,697,988,988,471,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE2ODgwNzgxNTc=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,boolean):int",
            new int[]{-185,-702,873,713,69,-110,-611,-801,-607,533,916,-960,-824,538,-51,636,-617,973,-940,-305,-802,-708,-483,-436,921,-67,-482,-152,-250,976,866,517,-632,699,241,-256,-913,672,64,774,726,-109,439,276,347,-637,-399,-152,-467,562,-76,-421,972,200,-386,-55,-228,-102,948,-336,-856,484,139,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,boolean):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE2ODgwNzgxNTc=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,boolean):int",
            new int[]{-215,-297,-508,17,976,724,-40,-874,-743,-267,-676,-386,-265,54,591,767,-868,-405,553,-69,434,193,-780,673,725,323,153,-272,-985,972,-308,637,185,207,426,983,972,742,-907,-759,364,717,-429,359,-274,772,-403,433,-481,526,-571,76,-443,614,-210,-565,429,509,-884,-820,908,-408,401,-741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE2ODgwODA4OTU=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,java.lang.String[]):int",
            new int[]{145,811,-410,-857,540,-774,194,-540,-537,-912,31,-219,10,432,-957,799,488,-742,46,682,-155,-767,983,-66,-401,362,-53,456,-759,-489,-757,29,148,962,982,-836,-120,-222,670,-684,401,733,-881,-252,253,759,613,-984,3,-549,715,11,364,26,374,737,-662,311,46,640,440,-154,438,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,java.lang.String[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE2ODgwNzY3ODg=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,java.util.Collection):int",
            new int[]{-47,-551,566,664,-385,-369,-664,435,-458,-558,-35,835,377,-492,144,711,515,-306,-167,-505,172,225,202,-505,444,-191,147,188,42,334,770,237,264,160,-668,-52,-300,29,-608,380,124,-819,-862,-539,103,384,-801,77,961,-712,680,356,879,-247,-445,134,-803,620,-870,-60,249,777,-192,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "reflectionHashCode(java.lang.Object,java.util.Collection):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjc3", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "toHashCode():int",
            new int[]{-765,-459,277,467,-185,363,-836,704,694,-886,-508,183,-981,-179,18,797,-698,-972,-466,-544,190,953,-543,-365,-34,-609,456,405,-735,-831,-871,994,168,762,867,573,974,565,-181,-388,-251,306,93,893,-982,54,210,-931,900,400,-757,-487,358,508,4,137,301,-61,224,-171,-195,-572,729,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTc=", DEReplay.run(
            "org.apache.commons.lang3.builder.HashCodeBuilder", "org.apache.commons.lang3.builder.HashCodeBuilder", "toHashCode():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
