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
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "convertClassNamesToClasses(java.util.List):java.util.List",
            new int[]{656,-896,72,134,919,-901,-773,-98,-399,-112,-280,-162,-772,-683,996,607,-719,909,-764,-822,-919,-743,-28,14,138,258,-628,-834,-266,-53,904,117,-537,102,-525,289,-199,951,883,-43,367,-645,430,1000,-514,646,869,-629,-109,-70,542,498,-17,136,-504,886,-87,-688,653,142,941,785,704,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "convertClassesToClassNames(java.util.List):java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getAllInterfaces(java.lang.Class):java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getAllSuperclasses(java.lang.Class):java.util.List",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassNotFoundException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.ClassLoader,java.lang.String):java.lang.Class",
            new int[]{913,-314,470,615,-964,235,-451,-679,-580,-425,969,-234,-911,-515,914,864,558,791,-850,-637,-115,-735,352,-34,677,560,-819,-930,-810,314,842,-716,-529,315,-884,-756,600,101,321,-588,526,-327,559,-477,736,-819,712,-865,-943,-660,-871,-662,250,132,-279,-901,-257,900,-725,-51,845,323,180,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.ClassLoader,java.lang.String):java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassNotFoundException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.ClassLoader,java.lang.String,boolean):java.lang.Class",
            new int[]{694,-762,-80,646,-288,-729,733,-968,95,-821,-632,-924,590,772,-177,689,146,244,344,444,-345,252,305,317,60,-322,-727,528,724,704,-272,63,372,-411,121,-778,-558,889,-725,979,-736,775,487,-181,134,-422,108,265,-949,708,873,332,635,697,795,-176,-386,-713,44,-482,122,-813,935,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.ClassLoader,java.lang.String,boolean):java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassNotFoundException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.String):java.lang.Class",
            new int[]{-638,-275,441,-876,-679,54,-85,114,-563,-772,24,-436,344,-601,-523,-355,-598,920,-504,114,-198,-491,-107,-265,-259,-407,-997,-978,145,-252,4,557,165,87,457,-128,3,-484,717,-297,487,777,301,-754,259,-564,-754,27,447,-914,49,913,-758,-621,845,908,946,-226,172,-558,-267,327,-139,56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.String):java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.ClassNotFoundException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.String,boolean):java.lang.Class",
            new int[]{710,-952,-506,736,834,219,-750,59,-952,-230,743,65,197,-98,125,-668,-317,59,488,-371,900,970,665,-356,-399,203,257,-784,177,322,-786,550,360,468,807,-239,803,696,598,805,189,-198,-810,366,-960,-511,909,572,239,737,364,568,-557,18,-140,-79,3,-405,-987,565,795,-427,-388,-398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getClass(java.lang.String,boolean):java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:amF2YS5sYW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.Class):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:amF2YS5sYW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{458,343,58,528,-73,846,50,-807,622,77,315,-104,834,-238,935,-922,-999,-599,358,-267,-217,-196,243,-260,-43,-220,-443,608,-378,-967,-515,849,-900,-995,-628,-77,862,716,-338,25,845,479,604,-140,586,784,-673,972,747,617,863,-761,-175,-996,617,-344,-907,-317,893,-297,137,-565,149,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.String):java.lang.String",
            new int[]{637,704,941,292,421,34,-947,-341,406,-692,249,-222,661,559,186,989,-915,-528,10,-308,624,576,23,-521,-81,194,302,-7,866,697,-752,704,939,898,410,361,999,-214,873,524,836,227,990,-706,660,-247,-680,-890,-582,-923,242,327,-86,-373,914,171,477,-964,-985,631,-833,265,970,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:RGVKcg==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.String):java.lang.String",
            new int[]{397,36,-864,-841,891,-625,258,-541,211,-717,871,-249,100,-325,-488,848,-410,232,62,-652,-137,190,-126,218,-906,-44,-405,-785,-140,904,234,665,-947,55,-571,694,127,-318,778,675,0,305,-369,343,-62,-965,-228,788,-616,-235,86,391,-199,-664,446,-357,376,451,-145,-256,658,-126,-832,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageCanonicalName(java.lang.String):java.lang.String",
            new int[]{-31,-326,997,204,245,-713,229,276,-101,-824,-569,563,501,-252,-729,-484,630,-512,785,595,-724,76,-905,614,-511,-601,43,71,19,248,940,903,-973,-215,39,675,866,-757,269,-145,401,717,-748,550,-638,423,-268,806,-167,-675,111,460,-877,661,44,369,-6,-450,736,834,509,629,-47,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:amF2YS5sYW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.Class):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:amF2YS5sYW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{-301,-541,961,-145,-80,136,648,644,208,655,-715,546,-26,505,336,651,-770,135,78,416,837,912,-192,373,-15,512,664,570,-390,-72,343,-182,-416,-863,-732,-886,-590,687,-150,-834,885,-93,336,878,759,-700,474,853,-262,763,-309,-507,-640,729,-152,-889,263,54,-265,-353,130,549,-564,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.String):java.lang.String",
            new int[]{-5,-567,-455,-45,-356,-251,-939,356,-984,-248,-477,851,189,-914,673,69,827,-854,-152,-670,-901,838,121,-759,-60,-556,-920,379,528,734,798,536,573,175,284,964,-687,-246,31,53,-71,802,145,984,-477,22,972,28,778,-137,176,355,-94,-634,729,506,666,-338,455,-994,-196,-592,-563,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.String):java.lang.String",
            new int[]{-671,390,510,-303,-229,-716,433,37,-215,-491,119,-671,-25,340,-137,869,349,400,975,97,-399,-609,894,-795,42,562,399,-414,322,321,-291,859,-204,-942,-1000,-858,643,-18,-777,-726,748,580,936,-232,-291,165,350,-587,331,907,746,652,512,717,21,-874,-517,413,-777,-887,66,712,638,977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:NzQ0", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.String):java.lang.String",
            new int[]{805,744,-752,-805,619,-950,163,51,-25,302,-940,678,477,355,541,-262,-752,664,878,775,-492,577,641,16,353,579,-658,-757,891,-838,430,-773,619,665,705,206,-802,-813,-453,867,643,442,227,409,-652,-674,3,-620,316,-162,-279,188,-459,49,-819,-514,538,-206,-316,-100,-291,925,-163,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPackageName(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getPublicMethod(java.lang.Class,java.lang.String,java.lang.Class[]):java.lang.reflect.Method",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:U3RyaW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.Class):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:U3RyaW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{794,-492,-11,837,614,-418,565,-814,667,618,448,727,449,123,-490,-498,442,50,-940,-151,-23,758,829,870,935,-486,416,-126,-350,184,-919,387,-378,-971,-308,540,-254,117,-659,-866,618,750,725,-539,-802,543,700,-870,-389,-718,-397,-480,-813,557,918,-729,-19,298,-360,266,961,-180,-694,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:MTQy", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.String):java.lang.String",
            new int[]{-443,638,-824,-858,234,-145,991,-237,569,-505,-15,724,623,-43,-859,503,269,-240,-125,-806,128,810,-265,-499,272,340,185,-421,-438,487,-781,978,743,-263,179,780,78,-594,-741,-303,-793,-794,-557,-635,-813,552,331,378,193,717,-761,-311,-734,-804,-768,-69,644,-263,369,-47,720,-660,-702,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWE=", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.String):java.lang.String",
            new int[]{220,311,-944,-386,436,-725,594,-540,-542,746,58,444,255,-375,-834,690,379,42,936,-383,-991,67,-484,200,774,814,242,222,-32,262,466,101,-852,-709,356,676,-322,455,-56,-950,733,823,3,25,-490,869,3,989,976,-170,349,-799,309,209,387,327,-591,179,237,-158,335,525,-561,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortCanonicalName(java.lang.String):java.lang.String",
            new int[]{495,-728,-421,300,459,609,631,714,344,171,-960,-454,858,-167,103,854,622,-846,-39,-352,870,18,-553,528,-824,549,-150,-484,-562,-36,210,-540,687,208,-522,269,-112,230,864,904,-171,586,88,523,461,-313,-143,-540,582,450,-133,842,643,-615,909,541,629,-794,-752,918,-534,955,-680,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:U3RyaW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.Class):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:U3RyaW5n", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{71,-477,749,471,671,244,467,-51,-168,-426,712,872,-229,194,-832,912,-928,926,-216,165,962,-527,898,-270,735,57,231,-467,-663,-423,-652,159,386,-88,984,-786,-190,94,59,-355,719,209,-812,-669,-876,-920,8,257,679,80,735,-349,764,-285,54,539,-103,54,-379,-618,-280,491,286,-331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.Object,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:KzcwM2Q=", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.String):java.lang.String",
            new int[]{-567,-703,298,626,-328,494,-709,724,693,-56,934,701,-275,-897,-525,-724,-17,-871,762,-714,203,-995,-500,-855,60,329,62,-793,93,605,0,418,-92,763,-903,338,-777,584,-910,336,610,-260,57,-438,664,202,830,164,-217,-26,812,143,-268,240,-917,-781,743,348,-227,-862,6,-659,352,949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:N1BZTA==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.String):java.lang.String",
            new int[]{591,941,181,431,869,845,-135,-872,-934,615,508,667,377,-366,-694,-223,-708,-102,815,349,-480,-820,-193,-730,298,-270,-419,-301,-997,189,-810,-8,-822,235,730,156,-189,-550,147,459,75,-53,-158,609,-467,311,782,777,-662,614,-217,-325,894,434,165,-120,-956,79,-917,629,953,-435,486,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.String):java.lang.String",
            new int[]{-143,1000,-1000,629,1000,1000,729,-200,-773,1000,-516,175,-211,1000,-868,354,-11,84,289,-113,1000,1000,856,1000,634,649,-17,-466,123,-75,-117,-827,1000,-163,334,908,-351,750,-454,-452,660,-1000,-586,4,1000,-239,1000,-166,1000,880,532,706,737,-1000,-1000,886,1000,-37,1000,707,1000,-275,-906,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "getShortClassName(java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class,java.lang.Class):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class,java.lang.Class,boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class,java.lang.Class,boolean):boolean",
            new int[]{695,824,-96,224,-61,-231,-515,613,-936,389,327,108,-942,622,-136,-431,-859,595,516,55,123,721,467,155,-811,-386,-278,-740,-130,-831,-947,-360,-203,424,-213,147,-933,-97,914,-942,-517,9,942,-846,-208,739,-895,430,123,-489,91,-946,292,-811,244,-869,-648,-431,-601,638,-147,493,940,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class[],java.lang.Class[]):boolean",
            new int[]{944,974,434,472,901,56,435,409,-84,-691,758,-665,-911,464,802,97,-211,-818,-749,-838,194,-303,-52,996,-948,242,-873,-603,-764,410,-120,824,-706,574,721,-595,141,344,475,827,263,-360,-648,565,368,456,47,-980,-701,-414,966,72,-821,721,278,247,-713,156,376,-723,944,83,-424,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class[],java.lang.Class[]):boolean",
            new int[]{-739,-630,96,-9,-331,-408,-501,60,641,80,-747,-42,-976,78,-354,-187,-473,552,978,-681,-360,479,-752,821,-336,-136,-744,66,961,406,-15,-80,180,729,-792,47,-902,920,535,10,-993,769,819,536,-894,-479,789,-189,232,231,-978,572,453,888,-988,-510,-465,-650,938,901,-904,-181,-21,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class[],java.lang.Class[],boolean):boolean",
            new int[]{745,319,440,1000,-208,990,-832,-467,-346,-1000,-299,-284,-455,311,-56,-1000,738,955,940,945,817,629,213,-747,-994,-1000,-179,16,923,161,276,225,838,640,-1000,-304,428,549,-564,-531,762,976,-1000,-602,-111,-392,702,1000,-225,117,-610,759,1000,-1000,-1000,-1000,-138,-104,-886,-410,-1000,1000,366,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class[],java.lang.Class[],boolean):boolean",
            new int[]{761,503,685,919,412,408,-710,-775,-485,-972,-216,-401,-989,742,-672,-461,970,495,331,818,326,893,249,-78,-824,-430,-499,-260,895,442,428,-158,108,450,-816,-876,179,832,-820,-620,290,592,-717,-551,-608,-139,378,984,379,139,62,608,669,-977,-715,-758,-951,969,538,-280,-869,866,864,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isAssignable(java.lang.Class[],java.lang.Class[],boolean):boolean",
            new int[]{-869,458,672,503,-144,-567,-448,458,77,-936,-402,436,743,693,-609,345,230,-640,15,-877,-647,771,251,-834,343,-161,374,134,736,-465,216,-353,-82,905,13,829,-342,436,-611,-531,382,-365,351,611,184,-271,584,-169,-409,-383,-486,130,-48,-85,-371,-572,-978,-211,-634,-27,-266,570,-501,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "isInnerClass(java.lang.Class):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:java.lang.Class", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "primitiveToWrapper(java.lang.Class):java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Class;:5:20:TYPE:java.lang.Class:20:TYPE:java.lang.Class:20:TYPE:java.lang.Class:20:TYPE:java.lang.Class:20:TYPE:java.lang.Class", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "primitivesToWrappers(java.lang.Class[]):java.lang.Class[]",
            new int[]{215,-665,21,-428,870,-9,156,863,400,-384,-781,-137,-625,702,615,970,78,730,-466,583,-987,907,561,118,109,191,-421,691,445,331,622,704,-323,-539,293,275,-539,-60,-809,525,118,611,-640,-877,563,398,-786,133,65,-727,596,230,805,17,695,657,410,-95,391,897,-387,-517,241,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Class;:0", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "primitivesToWrappers(java.lang.Class[]):java.lang.Class[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Class;:2:20:TYPE:java.lang.Class:20:TYPE:java.lang.Class", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "toClass(java.lang.Object[]):java.lang.Class[]",
            new int[]{170,-155,309,154,900,128,-389,-746,-646,430,-556,-22,-334,835,-100,-337,-289,233,845,-867,-361,-616,544,-621,-913,-949,215,282,-901,-800,642,-137,213,-919,-816,320,-268,338,852,532,743,212,-555,-395,-903,-646,205,590,287,820,-103,-491,-96,-596,-204,492,-813,-381,-330,-983,-896,370,495,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Class;:0", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "toClass(java.lang.Object[]):java.lang.Class[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "wrapperToPrimitive(java.lang.Class):java.lang.Class",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Class;:3:4:NULL:4:NULL:4:NULL", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "wrappersToPrimitives(java.lang.Class[]):java.lang.Class[]",
            new int[]{-765,-775,555,-857,-948,982,305,-499,174,-411,-768,-141,314,35,655,879,-921,583,495,-888,977,520,-811,-915,151,-783,304,-968,690,652,-446,-539,801,-546,-941,608,-585,830,-427,-985,-162,-662,167,963,971,314,348,-697,303,-950,16,327,402,281,-248,782,-66,409,-36,-143,-596,-955,188,-976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Class;:0", DEReplay.run(
            "org.apache.commons.lang3.ClassUtils", "org.apache.commons.lang3.ClassUtils", "wrappersToPrimitives(java.lang.Class[]):java.lang.Class[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "getField(java.lang.Class,java.lang.String):java.lang.reflect.Field",
            new int[]{593,309,819,-355,381,-737,565,454,562,450,298,-616,903,452,909,307,-839,-384,-432,693,-434,383,-27,590,533,-660,-988,639,476,154,-920,543,154,550,792,661,64,-452,27,723,56,-741,-138,-927,-432,761,266,-703,655,-252,187,589,342,-990,81,-180,-261,-881,-148,903,805,131,-324,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "getField(java.lang.Class,java.lang.String,boolean):java.lang.reflect.Field",
            new int[]{964,798,606,1,-729,130,666,868,-273,106,170,480,555,629,-514,414,-81,70,-416,286,47,761,-306,-646,-690,869,-660,355,-828,-120,958,801,-87,-110,-687,118,533,833,-518,324,360,881,311,-769,541,-691,929,591,798,339,169,-981,650,676,547,-934,601,771,-171,-589,-247,197,180,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "readField(java.lang.Object,java.lang.String):java.lang.Object",
            new int[]{-4,-858,793,-439,718,630,-922,-190,-668,819,-457,-486,527,116,-517,-468,-726,472,384,-265,-472,605,-452,288,-455,-226,940,759,817,129,404,-816,694,850,355,-372,203,-628,382,958,239,-195,807,-427,-629,360,176,640,808,46,911,32,752,195,-99,608,-700,504,671,-269,363,157,980,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "readField(java.lang.Object,java.lang.String,boolean):java.lang.Object",
            new int[]{-278,-739,-366,-711,-453,976,-27,-607,-789,145,-482,169,-571,344,-886,-131,-22,-912,990,878,-304,429,-412,784,604,920,-804,-325,-116,964,-349,362,-718,-739,-933,918,-476,360,-741,-702,-707,181,-654,-374,436,-244,-121,-785,-813,-581,-240,374,-582,228,-804,-391,171,458,-4,971,86,703,-375,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "readStaticField(java.lang.Class,java.lang.String):java.lang.Object",
            new int[]{-74,843,-427,572,-113,302,115,-969,-956,-127,-366,200,-969,-348,-195,424,445,-5,-147,-837,439,289,508,-64,-928,-918,672,-180,-30,322,-79,80,643,-583,-733,278,-910,-145,430,-996,210,177,296,211,-18,268,183,-905,-633,-874,-884,-590,-541,-486,340,-811,543,277,-968,-65,381,450,331,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "readStaticField(java.lang.Class,java.lang.String,boolean):java.lang.Object",
            new int[]{-867,-8,-877,817,-83,591,498,445,91,539,93,-810,170,465,988,815,-927,350,895,-972,-580,-491,982,-484,-104,-428,204,-314,-572,423,-769,603,-18,117,-815,942,-499,-83,-915,156,-340,735,162,-460,950,-457,-997,-636,-78,-426,886,-169,340,524,-426,-713,-550,375,366,772,623,962,-742,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "writeField(java.lang.Object,java.lang.String,java.lang.Object):void",
            new int[]{301,-222,-284,69,410,685,712,417,-932,-608,-268,-530,637,-104,484,-55,977,668,897,-595,11,726,142,443,-529,38,-276,16,373,424,679,134,220,800,493,224,109,-396,737,-144,-993,-450,-388,-737,278,-511,614,85,237,-954,-668,131,-37,4,-946,-420,-88,-80,283,15,-41,335,-475,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "writeField(java.lang.Object,java.lang.String,java.lang.Object,boolean):void",
            new int[]{-106,976,-321,864,-869,494,185,914,-873,949,-483,792,-766,-312,409,848,629,48,233,-553,-612,631,-832,-431,93,745,-554,60,-272,11,-743,917,414,674,246,303,-65,-283,-550,319,926,528,966,-972,-890,575,-282,-351,28,317,-762,469,333,-614,-425,542,-87,-992,145,-988,856,536,-383,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "writeStaticField(java.lang.Class,java.lang.String,java.lang.Object):void",
            new int[]{-778,-57,221,-80,-334,88,816,-653,357,277,590,-760,950,-586,-105,120,-926,-331,126,128,-388,615,-888,-558,360,472,-30,-625,-493,-894,-262,170,-601,490,838,438,-144,-345,-178,-562,954,798,221,935,-366,-780,-648,266,752,-23,910,405,-743,784,-144,322,90,-440,-308,-260,42,238,531,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.reflect.FieldUtils", "", "writeStaticField(java.lang.Class,java.lang.String,java.lang.Object,boolean):void",
            new int[]{-489,-280,460,643,-971,531,-885,-344,713,734,-715,-767,-599,-447,-776,-837,534,484,219,-541,-709,-739,534,907,293,215,-317,81,762,384,-205,872,720,229,700,-489,-632,633,-922,-734,-876,-59,-488,527,-20,273,612,-187,819,-941,512,-591,-907,-187,987,-209,-82,-474,203,-236,0,748,676,353}));
    }
}
