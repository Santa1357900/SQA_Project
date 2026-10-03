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
        org.junit.Assert.assertEquals("ARRAY:[Z:1:26:java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(boolean[],boolean):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("ARRAY:[Z:5:26:java.lang.Boolean:dHJ1ZQ==:26:java.lang.Boolean:dHJ1ZQ==:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(boolean[],int,boolean):boolean[]",
            new int[]{346,-822,-555,-709,218,377,-390,182,547,-450,-437,551,64,839,668,68,-180,-963,737,123,395,17,280,881,-263,944,609,948,270,-692,766,-257,-404,599,213,957,-152,697,231,331,545,994,-945,386,673,-338,-88,717,-379,-503,-650,985,-383,-675,-515,845,320,108,155,707,-630,537,-663,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(boolean[],int,boolean):boolean[]",
            new int[]{850,185,658,-74,855,-899,-680,686,-321,-918,391,-233,-548,-173,-19,-528,534,666,613,-381,-583,-390,786,562,-541,133,-374,-604,-626,227,349,-530,-915,89,-714,618,268,56,158,-617,-264,-676,455,880,-346,398,-177,954,-497,238,-334,792,716,617,343,268,-79,-642,-250,936,887,771,407,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("ARRAY:[Z:1:26:java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(boolean[],int,boolean):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(boolean[],int,boolean):boolean[]",
            new int[]{-958,-392,610,213,-81,-920,-616,77,-860,433,-957,107,-404,-749,-610,-504,-461,321,-413,525,657,417,-715,828,589,-389,-513,-443,-347,243,-762,-338,-762,-500,207,793,30,-180,-766,186,68,-758,984,274,-548,-745,-979,-213,492,688,-213,-425,-972,770,264,-472,738,601,419,622,-664,-30,-86,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(byte[],byte):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(byte[],int,byte):byte[]",
            new int[]{-658,-114,-415,-530,17,471,-725,-757,761,-38,555,-215,-995,982,-839,711,43,-593,-394,-373,854,669,255,-433,657,-797,345,714,398,11,810,697,-920,967,-468,-302,-872,-765,-82,639,-167,966,-380,791,-682,305,-368,-724,-546,-338,997,-835,669,184,948,-878,711,-579,388,-125,-949,274,360,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(byte[],int,byte):byte[]",
            new int[]{-227,-721,946,-58,-271,384,-555,-705,674,169,-986,347,-996,-769,210,58,165,603,-412,395,828,309,-797,-893,12,629,-459,-743,153,-912,-970,308,-216,814,-991,560,823,-320,368,-287,-724,416,935,-560,596,-238,-873,970,-103,-356,-608,-74,747,-660,532,-130,384,656,-891,-589,-166,250,845,-947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("ARRAY:[B:1:19:java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(byte[],int,byte):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(byte[],int,byte):byte[]",
            new int[]{397,-409,-130,469,334,307,627,-108,-724,237,826,137,-20,929,-355,707,987,522,-751,-553,-164,-181,621,-281,695,-116,-91,109,354,-140,872,694,234,-598,-187,-135,371,842,-362,-271,145,-10,-386,163,-26,-624,814,-238,131,-385,218,120,-617,-411,-942,153,-733,195,-945,-508,-565,-510,-46,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(char[],char):char[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("ARRAY:[C:5:24:java.lang.Character:EA==:24:java.lang.Character:Rg==:24:java.lang.Character:ew==:24:java.lang.Character:Bw==:24:java.lang.Character:eA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(char[],int,char):char[]",
            new int[]{-26,-570,379,391,-648,404,312,784,-946,-79,757,945,130,-319,89,775,-772,-619,-486,-746,-371,-29,576,100,64,-486,-639,-938,-820,-948,508,452,-514,-555,-633,624,-308,-753,25,749,426,437,254,489,-468,991,-48,-73,-660,-825,-894,-541,-351,-243,940,288,472,-685,-496,-416,73,285,716,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(char[],int,char):char[]",
            new int[]{-322,-464,639,642,-58,-493,-506,958,-596,-397,-898,751,645,-485,328,726,913,345,796,401,269,353,-497,292,-816,-723,-367,-4,81,231,-725,-596,-705,-219,-958,388,959,-96,910,923,468,241,-27,886,295,-712,302,488,-767,-994,-436,713,-620,687,692,-25,-420,868,482,736,-567,573,752,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(char[],int,char):char[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(char[],int,char):char[]",
            new int[]{-535,-956,69,443,-339,-188,820,479,-210,-341,-212,89,777,-134,132,411,-413,118,710,-742,30,434,521,310,-268,328,-904,-842,351,-693,104,-503,38,324,745,874,245,993,788,11,-928,7,545,-989,513,493,496,-533,-793,-177,817,-99,-397,806,628,825,794,87,448,-793,-290,123,999,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[D:1:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(double[],double):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("ARRAY:[D:2:25:java.lang.Double:ODMuNw==:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(double[],int,double):double[]",
            new int[]{-419,173,-396,85,499,837,682,-933,-434,-237,902,-846,401,-386,85,392,-612,-874,997,-302,331,-711,-598,-869,103,332,858,320,643,-474,-272,427,898,175,443,910,-292,113,-285,844,-972,-469,-920,-429,397,-990,-67,-937,69,-724,48,534,-108,372,-533,546,-372,-292,582,-843,203,-262,282,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(double[],int,double):double[]",
            new int[]{-961,-15,935,-639,940,865,39,458,-354,231,613,404,609,148,-431,-940,461,945,689,510,-294,-97,-239,441,-296,-900,-455,699,-372,498,-136,-29,335,-252,-264,476,494,-877,46,-1,-279,484,-978,645,-54,-723,493,284,-191,138,783,-189,-525,-79,-53,-920,940,681,-947,636,-366,-520,-818,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("ARRAY:[D:1:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(double[],int,double):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(double[],int,double):double[]",
            new int[]{681,994,-211,-574,748,933,-946,-306,-64,21,209,-471,223,-519,53,-978,-408,-577,-762,-575,988,592,884,-145,555,37,-7,278,542,-763,177,-278,549,-354,831,534,169,532,471,18,494,-525,713,-230,-575,999,104,679,322,-619,-137,-339,-930,-80,471,965,-619,670,-990,146,-158,-28,172,649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[F:1:20:java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(float[],float):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[F:5:24:java.lang.Float:LTEuMA==:28:java.lang.Float:LUluZmluaXR5:32:java.lang.Float:LTkuMjIzMzcyRTE4:20:java.lang.Float:MS4w:28:java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(float[],int,float):float[]",
            new int[]{592,-652,261,-72,-522,327,721,-98,896,-665,-630,918,-178,-353,-87,539,-589,97,-177,605,-593,-137,130,-755,585,-945,-530,825,924,-502,-602,601,585,-726,-853,-896,252,391,237,-880,874,-692,677,-546,464,915,796,678,-781,68,243,482,-41,817,119,402,629,20,-227,-765,-545,-753,432,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(float[],int,float):float[]",
            new int[]{-628,-326,535,460,232,354,-440,122,-886,-121,-887,-339,678,440,589,384,-97,883,709,-69,-239,-161,188,-942,-202,-243,-919,199,893,261,355,-328,825,468,448,-402,752,-282,-231,-544,242,470,-553,552,25,56,940,-380,-661,-551,586,289,398,-771,-371,-795,301,-473,-167,-783,539,-105,-615,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("ARRAY:[F:1:20:java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(float[],int,float):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(float[],int,float):float[]",
            new int[]{-777,555,-540,-250,-885,34,-185,-241,548,-170,-935,-268,-447,804,-840,801,345,-83,126,-673,990,989,-215,-23,279,569,-302,413,-689,304,596,-104,-366,-571,779,-625,275,-231,172,-575,487,-999,-968,799,-351,-169,-333,280,360,-800,-49,22,-486,681,895,-346,870,814,-903,-993,-497,302,496,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("ARRAY:[I:1:22:java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(int[],int):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("ARRAY:[I:4:22:java.lang.Integer:LTE=:34:java.lang.Integer:LTIxNDc0ODM2NDg=:22:java.lang.Integer:NDIz:22:java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(int[],int,int):int[]",
            new int[]{435,-50,-994,423,-733,-181,-707,-543,553,-259,-380,-775,816,-107,-669,719,427,956,-818,784,28,664,-995,401,810,-884,456,-741,-332,-112,479,667,42,708,-695,201,388,-82,-628,-361,652,-681,354,729,843,88,656,-881,904,439,-800,-620,-610,-571,-930,-527,196,905,726,-760,-463,961,838,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(int[],int,int):int[]",
            new int[]{-778,-844,-415,494,-395,-73,297,-94,508,-400,92,-776,994,-89,509,-359,401,437,231,-746,685,-754,-562,28,-904,-443,872,-292,-978,760,-938,-968,622,859,-649,-48,7,-105,910,733,151,235,748,783,443,-248,889,-136,888,-145,341,913,-716,104,-741,-850,-574,712,794,359,-643,-400,530,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("ARRAY:[I:1:22:java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(int[],int,int):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(int[],int,int):int[]",
            new int[]{277,797,264,-317,-921,-342,-940,953,-380,879,-106,-223,254,-248,265,-2,-862,-811,593,-870,-441,270,-713,221,-371,688,832,442,967,-538,-399,-722,-245,29,-656,-436,-271,-713,-201,-230,43,-485,-280,-84,-272,-782,-221,295,509,-92,-753,-229,-898,558,-871,861,824,78,-456,7,305,525,506,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:5:29:java.lang.String:b2JqZWN0MA==:4:NULL:29:java.lang.String:b2JqZWN0Mg==:4:NULL:29:java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(java.lang.Object[],int,java.lang.Object):java.lang.Object[]",
            new int[]{-512,-442,-675,652,-823,-852,49,108,-102,457,-621,589,722,841,226,744,664,956,-749,538,-617,49,-554,877,428,-723,-940,-775,809,-912,452,-759,252,342,260,-371,105,8,172,-897,-496,-925,-455,683,-805,562,-870,-665,-842,-532,-745,429,-921,-142,-76,415,615,-638,557,-311,-615,-126,-708,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(java.lang.Object[],int,java.lang.Object):java.lang.Object[]",
            new int[]{183,150,-977,499,391,-463,-657,-679,-479,244,725,437,-938,-350,555,-864,417,112,-33,-315,88,-124,563,771,254,-351,309,930,269,-666,-565,-54,-875,71,-90,398,585,904,-960,220,-687,-856,618,453,105,842,120,185,-896,607,-485,-899,-261,507,368,-965,-924,-255,-835,-560,219,390,31,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:4:NULL", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(java.lang.Object[],int,java.lang.Object):java.lang.Object[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(java.lang.Object[],int,java.lang.Object):java.lang.Object[]",
            new int[]{589,-265,-621,-952,663,457,566,-228,482,-161,828,775,257,994,-631,81,807,462,85,450,-655,-824,673,-956,-412,-303,-97,605,545,813,193,228,-109,232,439,987,459,-715,-57,-605,40,315,267,-58,-583,-266,-191,826,-627,-396,26,384,259,59,336,-300,-359,-149,958,-586,524,905,-791,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:4:NULL", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(java.lang.Object[],java.lang.Object):java.lang.Object[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[J:5:19:java.lang.Long:MA==:43:java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=:43:java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==:31:java.lang.Long:MjE0NzQ4MzY0Nw==:43:java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(long[],int,long):long[]",
            new int[]{802,369,-84,63,-571,622,51,-560,789,-423,-587,259,537,753,200,-399,564,-528,475,-995,137,-974,511,28,659,426,837,508,-547,493,62,-890,-608,924,-566,-694,56,667,330,268,190,-553,374,-673,681,-380,-263,638,8,643,-687,-537,175,-576,930,-888,524,540,534,-21,-522,931,361,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(long[],int,long):long[]",
            new int[]{-509,321,271,989,969,-695,-787,83,-168,478,-825,996,-975,-910,-567,-109,481,598,395,894,-253,349,-942,856,-660,266,858,-784,-845,-985,454,-226,766,-999,-113,-253,225,259,764,785,-862,-218,-355,848,177,-799,649,778,968,-192,437,-787,-671,-91,215,96,-781,-771,-854,423,421,665,-333,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[J:1:19:java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(long[],int,long):long[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(long[],int,long):long[]",
            new int[]{389,637,-391,589,-338,-820,-538,506,-965,-200,940,624,-4,-717,-938,287,-801,465,662,-272,896,376,-570,-57,766,-460,-288,993,-158,251,-25,919,-262,579,747,5,186,-485,63,458,999,-783,-21,-228,-979,588,517,982,376,609,616,840,-651,-252,-941,-470,-343,-432,184,951,-513,188,-779,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[J:1:19:java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(long[],long):long[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[S:5:20:java.lang.Short:LTE=:20:java.lang.Short:LTE=:20:java.lang.Short:MA==:20:java.lang.Short:LTE=:20:java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(short[],int,short):short[]",
            new int[]{-98,874,831,583,976,-155,965,-252,237,-782,-311,554,452,-493,448,-517,841,-800,-443,-439,-823,342,386,810,-757,-335,-807,-684,-739,-203,490,-464,433,-590,500,184,994,648,-329,-61,284,-907,-507,-468,-731,-311,941,-656,676,-455,-264,540,-581,-664,-674,992,395,-846,-12,760,-166,75,-210,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(short[],int,short):short[]",
            new int[]{388,-549,-498,212,-779,18,-418,214,-576,647,-574,892,-21,87,107,-70,-60,595,-381,-779,-444,58,365,-490,-180,259,527,28,632,-329,232,53,215,339,-918,-9,-287,-961,-915,523,-966,710,225,-354,878,-614,-954,-981,-342,317,-526,930,514,777,-201,-593,-972,211,259,-470,381,741,-10,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("ARRAY:[S:1:20:java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(short[],int,short):short[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(short[],int,short):short[]",
            new int[]{-370,694,100,-479,-251,937,560,930,-493,15,-285,-309,20,775,-503,-105,-780,662,-949,-385,12,740,490,-564,-358,-715,-805,539,751,-887,922,-891,-911,-938,-426,450,124,582,984,-819,927,284,-719,-854,568,449,-486,267,64,31,-945,-542,-621,444,-948,955,408,-396,-349,-94,974,63,-913,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("ARRAY:[S:1:20:java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "add(short[],short):short[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("ARRAY:[Z:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(boolean[],boolean[]):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(byte[],byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(char[],char[]):char[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(double[],double[]):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[F:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(float[],float[]):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[I:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(int[],int[]):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(java.lang.Object[],java.lang.Object[]):java.lang.Object[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[J:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(long[],long[]):long[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("ARRAY:[S:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "addAll(short[],short[]):short[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("ARRAY:[Z:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(boolean[]):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(byte[]):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(char[]):char[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(double[]):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("ARRAY:[F:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(float[]):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("ARRAY:[I:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(int[]):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(java.lang.Object[]):java.lang.Object[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("ARRAY:[J:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(long[]):long[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("ARRAY:[S:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "clone(short[]):short[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(boolean[],boolean):boolean",
            new int[]{-503,337,-886,920,-663,223,-678,-172,853,166,337,164,-263,-915,457,22,-830,-307,-703,-208,168,618,126,-355,810,728,-487,-120,647,804,-368,101,-396,-605,665,319,434,807,718,550,412,-518,405,-753,-618,770,854,994,-538,-965,-768,296,797,378,-864,999,-428,868,158,-77,-829,100,-216,590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(boolean[],boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(boolean[],boolean):boolean",
            new int[]{904,697,534,793,792,478,-503,620,-422,578,28,88,-890,-397,-900,-618,-448,-632,-452,-288,-499,-35,-764,181,931,-931,241,-31,-967,-334,-317,-704,-951,-935,912,805,941,177,764,-531,367,-650,-690,-117,843,-208,-862,911,-790,-441,-455,-964,532,412,-729,-492,-544,481,-643,363,694,-432,-429,769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(byte[],byte):boolean",
            new int[]{-818,957,353,-988,468,-373,371,554,564,955,-666,-926,150,908,928,-282,972,141,-595,-845,-729,-997,-415,386,-169,882,-910,-57,104,366,428,-553,217,802,109,-933,-180,-703,-732,232,727,-84,-602,-907,-927,-90,-239,735,348,-834,-197,850,-955,-232,464,742,-89,515,646,873,-166,605,-656,-90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(byte[],byte):boolean",
            new int[]{235,-740,-103,252,-486,30,683,-669,-426,-256,191,674,-268,647,-129,-374,-553,110,-605,-48,182,-503,66,-897,475,274,970,-175,819,-773,-817,26,307,-212,-159,-681,-838,923,208,-485,259,730,18,-537,527,997,298,144,-265,-681,669,-359,-444,-947,648,-246,71,393,-93,378,-116,148,-391,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(char[],char):boolean",
            new int[]{311,-599,-295,797,-711,-12,-571,657,48,-2,674,899,27,-776,-336,-122,-861,936,-690,-291,623,833,366,579,-509,568,550,-368,-277,-203,-337,404,-163,650,-147,-302,681,772,54,929,-601,-282,405,492,-601,332,-45,507,-171,7,-657,-606,-386,200,-726,-800,395,451,-363,-693,-140,-721,855,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(char[],char):boolean",
            new int[]{1000,-796,-549,-238,-616,-1000,-691,-1000,191,-554,1000,1000,292,700,-650,861,-376,1000,-408,-1000,186,1000,-567,1000,-436,1000,-716,-40,-1000,-63,-1000,-830,682,834,60,-658,273,1000,455,344,-383,1000,-467,660,63,1000,-802,702,283,-577,-651,266,-191,1000,-1000,540,-1000,1000,-855,-110,673,333,-437,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(double[],double):boolean",
            new int[]{-250,-863,274,746,-374,-301,590,-513,-790,428,260,-189,-787,611,795,-465,699,-167,679,-512,644,543,-501,514,-849,-662,613,80,735,-366,243,758,-934,-201,-745,-121,893,-9,597,-467,471,-23,415,727,749,-992,174,-983,-170,-511,787,-807,760,611,-285,-258,-431,364,251,-325,696,448,821,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(double[],double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(double[],double):boolean",
            new int[]{377,464,-977,316,-571,-535,333,944,956,472,926,-942,-70,864,-63,-871,374,691,570,-297,-109,-79,3,-328,688,366,740,322,897,626,-789,-430,8,766,773,871,-461,-366,6,97,652,-250,-781,-636,410,502,408,-705,-430,41,569,442,-626,-316,-769,731,695,-66,343,715,32,259,240,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(double[],double,double):boolean",
            new int[]{632,-840,-895,-138,-380,966,-721,953,-493,452,-718,-189,133,59,555,748,446,857,14,-945,-541,930,-649,-960,-170,781,-348,-217,237,934,-127,918,-873,-549,-354,103,331,-261,-655,282,565,-267,-616,305,-121,292,305,-944,748,-500,-606,810,-296,415,242,236,-858,827,-996,-786,251,530,909,389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(double[],double,double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(double[],double,double):boolean",
            new int[]{1000,433,-857,348,-199,1000,406,-1000,190,-375,359,-20,-129,-1000,-40,1000,-1000,-129,1000,-647,395,744,-849,-307,1000,903,520,976,-378,-26,-246,1000,609,-178,-418,472,895,86,698,1000,589,-1000,-236,1000,-500,437,-748,-20,-360,-393,553,342,350,156,1000,1000,-1000,587,62,-712,639,-24,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(float[],float):boolean",
            new int[]{-495,789,-274,-133,-841,989,775,335,-449,-582,646,-150,171,-317,84,567,250,-625,963,511,-590,521,145,957,-900,985,-181,-910,-802,-610,-818,404,-269,635,300,430,494,-713,-741,-727,-424,238,280,-820,-741,-107,-575,897,588,838,-323,926,10,466,731,973,925,-651,681,822,-268,284,905,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(float[],float):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(float[],float):boolean",
            new int[]{5,-109,-44,841,-188,-621,988,89,-140,-284,399,-760,159,763,707,49,186,183,453,343,169,-96,-119,-986,-982,-226,-450,-762,-293,-354,-357,-450,-566,611,693,502,-230,-677,-222,-452,-791,470,-264,541,980,-713,953,749,830,752,-608,-343,593,569,-863,0,-221,-602,510,435,-824,-88,-4,-943}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(int[],int):boolean",
            new int[]{-993,412,394,-215,195,-638,339,-271,-272,120,-255,-888,246,236,98,534,-925,859,27,-470,-38,-85,838,-956,-5,-481,477,-20,-194,-15,-432,516,616,-39,628,237,-459,206,270,264,753,-708,312,-497,507,-852,-978,-111,856,754,456,93,-52,-464,-182,-477,205,56,81,665,-603,417,-272,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(int[],int):boolean",
            new int[]{-362,522,-584,-5,-310,741,575,-194,593,887,-442,753,-387,-533,789,-331,764,107,-441,-725,-240,-215,-399,-877,-847,23,393,-212,-393,-432,247,-336,-189,-755,-314,86,358,644,-664,592,52,95,-359,116,607,-5,54,-356,-566,957,-939,-544,335,485,-633,-562,295,911,897,169,-697,-784,-118,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(java.lang.Object[],java.lang.Object):boolean",
            new int[]{-989,511,436,-818,595,700,-444,-684,-869,744,-65,-791,419,-635,-839,199,966,666,-280,-51,-920,952,-607,-792,170,-790,186,737,155,-83,943,-786,-494,461,-985,501,921,-530,-473,-503,-132,569,652,18,-695,714,57,-381,-209,-876,-788,-926,-130,72,616,372,-849,279,157,485,-203,-446,801,954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(java.lang.Object[],java.lang.Object):boolean",
            new int[]{-1000,301,729,-297,384,-373,330,-1000,-1000,-161,789,-1000,708,-994,-833,1000,1000,1000,401,1000,-623,1000,187,-115,1000,-1000,19,1000,436,1000,1000,-172,-1000,-1000,-1000,714,4,-203,-352,-783,-195,999,1000,650,-1000,-50,-726,866,460,-958,-897,-1000,1000,442,656,1000,-687,627,813,-69,948,-549,482,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(java.lang.Object[],java.lang.Object):boolean",
            new int[]{424,801,992,109,333,-499,737,-764,514,-915,338,861,728,-456,708,708,-425,632,464,261,648,-514,671,936,962,-661,669,-216,-529,872,-37,429,53,-632,-529,474,-158,816,501,-499,20,401,-225,-30,248,-646,-479,794,-169,-893,-791,206,370,841,-145,628,198,99,986,-305,191,-73,337,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(java.lang.Object[],java.lang.Object):boolean",
            new int[]{93,253,-47,-415,-302,-560,559,-939,446,366,676,550,143,803,-564,463,-611,-903,978,-503,201,-239,808,441,-902,832,-407,247,-455,-618,-31,387,244,202,-755,94,-776,-367,976,955,958,-165,-695,490,769,319,-290,851,-290,-498,295,602,-227,-740,-824,621,-181,-53,-383,-362,903,229,-387,625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(long[],long):boolean",
            new int[]{581,934,158,-150,720,-880,-653,970,-840,-115,965,396,973,-8,-589,505,-664,-599,79,-802,-412,-973,157,307,361,971,598,-154,-198,-867,-599,-352,213,-882,115,692,-673,-576,-54,318,845,-932,778,439,894,-19,-26,-163,622,-205,-93,698,252,-46,-157,-360,639,-508,992,653,596,262,-635,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(long[],long):boolean",
            new int[]{-86,934,-606,816,1000,1000,-696,582,308,-820,344,-1000,264,127,-201,1000,-876,147,937,598,133,267,-740,-668,-802,-462,752,-164,305,57,-1000,-27,407,673,-914,427,340,1000,344,-277,135,1000,305,-852,126,24,872,870,-407,-824,-13,711,11,416,454,-1000,-1000,547,980,72,-983,401,291,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(short[],short):boolean",
            new int[]{800,255,898,-657,-719,-442,-199,-325,3,480,-599,-419,-246,889,-557,-459,468,-817,-124,-492,84,-393,-832,927,874,542,79,-231,297,-804,-627,758,959,834,-973,406,-699,-524,37,-931,-721,-226,127,-181,-506,727,-652,607,-40,801,876,-70,39,-929,121,-974,-81,63,-292,-558,498,-876,835,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "contains(short[],short):boolean",
            new int[]{-758,603,512,243,285,-406,-13,730,-723,675,-912,-853,-324,-858,680,899,530,-244,562,-86,-794,-909,434,-660,255,993,676,586,-156,-54,-191,237,711,-127,540,-122,-358,654,-96,362,114,-524,-851,-260,917,-982,-249,664,-654,-565,-775,882,845,125,241,-806,-133,332,948,-870,-825,788,-577,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "getLength(java.lang.Object):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(boolean[],boolean):int",
            new int[]{611,327,296,691,-411,-343,910,-875,-311,656,604,-179,855,704,992,-988,-365,541,364,654,323,206,-316,-983,507,209,685,261,-957,-394,459,-650,483,908,-358,995,-74,626,-874,-845,420,-354,-930,-902,-875,-396,-78,-995,682,-41,127,681,500,-425,-722,-959,989,258,539,-237,174,-78,384,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(boolean[],boolean):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(boolean[],boolean):int",
            new int[]{-604,-285,41,190,203,-363,-34,676,-906,-919,-258,-798,403,-870,886,745,129,-725,-599,811,57,416,753,-715,982,-684,300,378,880,471,374,354,-165,-334,-52,-979,-454,307,611,838,-685,-953,-544,954,563,-806,-620,475,126,899,318,-182,852,253,941,-832,336,-945,-505,700,-946,-766,612,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(boolean[],boolean,int):int",
            new int[]{145,-724,-847,1000,597,-137,661,-23,158,-520,-401,141,176,18,-265,1000,503,1000,578,48,-126,-242,346,639,632,768,-457,-56,-1000,1000,803,-393,1000,-124,-1000,202,-705,-353,-897,-261,-768,-614,171,-1000,-59,722,-715,-123,-539,1000,905,-293,796,598,-1000,-398,-261,1000,761,-48,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(boolean[],boolean,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(boolean[],boolean,int):int",
            new int[]{771,-416,-114,-953,197,-206,-215,217,320,-956,-926,55,-800,934,653,832,660,22,-785,891,716,-392,-397,521,-942,776,820,929,690,-678,904,-207,-674,-605,272,-764,-996,-370,904,159,-485,-639,-216,858,726,-913,-607,-424,612,797,-289,235,-37,-16,-784,-35,-948,210,627,250,350,953,797,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(byte[],byte):int",
            new int[]{-505,376,-350,245,900,976,-836,690,-746,105,603,-382,-812,690,319,-540,680,-760,717,825,455,-121,-514,-544,-665,-130,765,-718,-54,-165,-458,822,-362,-207,-339,-939,-265,449,-253,-910,317,-258,76,-698,-408,-455,959,757,640,658,-418,-577,-188,-279,-634,-556,388,-457,-56,335,220,476,599,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(byte[],byte):int",
            new int[]{-928,932,-361,-717,77,-87,573,868,345,-476,-776,509,122,811,-100,-84,98,19,-355,386,474,353,-760,668,-779,686,85,189,608,-701,-570,319,490,-294,463,172,157,888,747,678,-429,-158,171,-895,-263,275,154,36,618,-549,-825,-50,-449,-236,-111,928,-580,399,45,824,-31,-344,-769,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(byte[],byte,int):int",
            new int[]{638,614,-150,355,305,-736,269,390,-579,785,-772,538,440,-264,633,-854,331,78,-392,-850,226,-834,775,-691,-956,596,468,-207,234,561,888,901,527,277,300,-897,500,467,-510,587,638,885,937,-988,-403,448,-608,-748,-607,-352,909,608,-622,624,-246,-851,-977,-495,-923,-348,764,479,-255,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(byte[],byte,int):int",
            new int[]{-539,-40,-889,-419,742,-561,-924,904,266,-329,-59,-771,-892,439,-421,-947,-727,-232,-606,483,-683,-397,-621,759,776,293,16,265,-50,222,929,14,343,-221,-689,-67,969,-681,235,296,614,764,218,142,154,51,635,657,84,765,934,109,503,-8,978,189,-15,-336,-676,-639,12,492,-285,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(char[],char):int",
            new int[]{-149,864,-990,-2,-671,-971,-544,-263,894,895,906,997,-495,515,-76,427,848,-8,-616,-556,-471,905,207,-258,-373,400,782,-79,734,-646,-462,880,157,40,-323,903,53,-266,242,696,916,835,340,14,68,-748,-382,998,-421,-978,-95,814,391,438,300,-172,335,-659,919,-867,-815,622,-948,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(char[],char):int",
            new int[]{1000,817,-909,1000,942,1000,-548,-1000,-306,486,-1000,-288,-174,651,-750,135,1000,238,610,-409,-134,-101,804,609,-644,-203,411,-910,-1000,-78,-1000,-414,-357,-426,196,963,412,-433,-205,711,-347,-677,249,17,-850,458,-362,83,815,959,412,-583,514,-1000,-1000,-147,-1000,-919,611,49,-58,233,-69,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(char[],char,int):int",
            new int[]{999,-933,301,502,-698,999,-130,426,295,660,445,-206,39,446,355,-796,365,220,-121,284,33,-866,-639,68,-996,250,-123,-442,-937,-236,-267,-269,-676,-76,-984,-551,224,282,113,227,477,-143,706,611,163,988,-950,40,-343,174,-456,503,387,34,-431,35,-416,-304,361,-824,-893,351,-716,-159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(char[],char,int):int",
            new int[]{-2,-1000,896,-1000,610,-1000,-781,601,49,1000,-870,1000,-1000,1000,-1000,450,-1000,-1000,-320,-1000,-1000,-383,-1000,-297,-359,128,1000,1000,553,651,-819,955,1000,1000,481,249,114,-1000,-652,600,1000,1000,-1000,-261,1000,1000,-1000,-121,145,-551,77,384,944,1000,-1000,-304,-1000,924,1000,848,1000,630,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double):int",
            new int[]{-848,710,666,-214,707,-702,-183,266,-482,-213,988,490,434,584,954,-322,-913,958,854,-770,737,-912,907,-923,-9,175,-12,-235,540,-954,-984,653,-337,-729,916,-195,817,52,-173,863,-670,838,161,466,931,-15,165,636,670,-379,690,-58,626,347,31,-738,736,366,-106,437,55,241,-163,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double):int",
            new int[]{-283,-396,1000,384,-804,-696,-1000,-923,807,-1000,-120,401,-564,-665,-251,173,-933,1000,330,-583,-295,-156,-1000,1000,1000,-683,-1000,360,-553,-205,-1000,-723,-80,-449,1000,645,337,271,-928,-751,-281,112,971,-776,-930,143,-281,-553,-1000,242,614,74,958,-1000,-967,1000,-1000,662,655,-876,-1000,58,563,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,double):int",
            new int[]{170,-193,368,-896,-995,-955,419,679,568,156,138,-158,-523,124,632,465,-659,-869,577,381,399,25,-132,888,-138,650,299,240,866,590,378,303,828,-694,-496,-445,-106,-331,937,137,-819,-562,-713,-252,536,-641,537,835,687,273,-959,-227,779,838,376,-527,-423,318,-544,244,887,-755,-124,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,double):int",
            new int[]{1000,601,-551,197,-306,-45,864,-330,1000,916,1000,492,-516,-400,20,-501,-474,-1000,436,1000,483,-118,631,141,96,-959,-128,-131,-852,-250,929,134,216,515,835,-92,70,432,-186,-221,-578,-562,-509,-400,-476,-719,-1000,529,716,-169,-20,-1000,1000,1000,224,82,-1000,-854,-378,421,480,-1000,586,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,int):int",
            new int[]{-375,783,-596,363,753,941,246,-190,470,-555,530,909,329,-503,-686,12,-497,712,733,-818,-14,192,-256,571,392,737,167,-994,-61,99,683,240,834,410,310,11,942,-389,870,-625,-646,-914,-64,585,-724,-481,-131,-778,-484,492,666,257,-228,295,565,224,-87,-994,606,828,-508,35,-307,926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,int):int",
            new int[]{101,-490,180,-322,-918,-251,954,643,512,-705,-382,992,-682,242,-173,156,807,-177,-989,250,815,-125,579,199,104,147,-939,503,35,297,253,18,-819,625,343,-153,179,332,-37,-276,891,429,-901,-870,-273,264,889,759,-154,107,496,-42,874,-781,-589,271,-229,-613,-161,323,514,67,151,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,int,double):int",
            new int[]{-644,732,-895,-465,-593,632,343,572,499,-581,540,-767,-238,355,496,-58,-847,-112,544,-139,-88,82,497,738,-727,971,-223,379,55,384,457,880,-883,-245,-237,-407,-520,-437,-953,600,-606,-837,-841,-524,-367,960,-285,13,-160,191,-168,714,-110,859,573,919,931,-210,560,-387,996,233,-4,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,int,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(double[],double,int,double):int",
            new int[]{-98,116,-459,169,-726,-779,1000,234,209,-1000,1000,-1000,-90,56,425,-1000,625,-1000,-255,-1000,1000,1000,-820,251,320,136,-412,-687,1000,900,385,-49,-314,-399,-1000,1000,-375,542,1000,-102,-447,992,-989,-60,-515,-600,-396,843,-166,-1000,-136,1000,570,717,239,1000,128,-757,366,-396,-1000,-1000,1000,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(float[],float):int",
            new int[]{-118,-488,-356,-386,-330,-100,-458,-544,-172,-422,-419,659,-743,595,-29,-99,-116,558,-446,644,-366,-397,-170,459,-424,-637,-199,752,253,-654,-709,-249,492,-876,-173,17,973,-964,-684,470,743,-254,-210,-77,-525,-98,317,476,-203,-587,-213,761,274,-610,752,989,753,704,-241,36,-328,-759,-350,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(float[],float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(float[],float):int",
            new int[]{743,0,-135,671,368,839,577,398,786,320,777,796,757,18,920,-244,122,-105,-500,-923,-393,-723,-792,-641,372,308,-810,262,-739,-237,-973,424,-14,33,-565,-248,-545,449,-129,-494,367,-307,261,609,-172,-994,859,-37,-775,134,919,217,901,-494,-731,366,261,-997,167,851,-133,-102,-126,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(float[],float,int):int",
            new int[]{-376,-942,-451,406,-862,495,397,-253,266,888,298,-164,490,472,736,385,-267,-869,-502,-366,-267,622,53,-234,701,717,115,-327,947,-407,-438,586,-758,313,-567,-798,-864,561,-465,956,-816,-55,370,852,-895,106,-471,884,-759,-360,-504,-509,-608,430,890,-284,-916,329,545,783,637,-620,-405,654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(float[],float,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(float[],float,int):int",
            new int[]{-403,-807,693,603,-126,-116,600,-595,239,154,-197,-88,-660,-461,-683,689,-385,-148,122,469,-749,-359,-18,-117,-799,-251,748,155,778,-81,-315,-619,447,881,875,-887,864,666,-419,982,980,63,-385,885,-954,50,-783,843,766,-201,716,80,495,-559,-659,-918,387,-862,960,-562,704,-771,-791,137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(int[],int):int",
            new int[]{-49,-35,495,138,717,-41,689,780,-349,-873,-411,-111,-913,-703,-962,-732,974,-239,208,-222,-335,468,-434,712,-109,-231,-136,636,-714,214,-662,56,-84,253,-509,-944,-923,-710,859,-3,335,466,973,777,-785,-182,-640,-248,281,-20,880,-595,-592,-502,962,123,-990,988,360,17,673,860,938,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(int[],int):int",
            new int[]{311,343,-659,-680,449,696,-299,790,86,215,-796,956,893,-38,-949,-390,-281,333,-108,605,-266,-905,-979,-537,754,175,972,386,608,-52,649,-216,-563,-922,347,-859,-307,588,36,71,245,753,65,746,-507,680,-866,-456,-307,961,-233,699,760,-315,843,49,782,-736,-254,528,169,13,-349,977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(int[],int,int):int",
            new int[]{155,-205,231,162,-878,379,682,257,-711,-749,630,-874,761,457,412,718,907,37,-104,-549,51,151,-310,451,326,-173,49,316,-397,-32,-557,-806,-74,-877,428,179,263,592,-19,-472,544,909,767,-931,-80,459,970,315,988,-937,-765,237,-685,726,48,-538,25,426,-552,723,426,-195,-660,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(int[],int,int):int",
            new int[]{550,-584,-454,1000,814,797,-822,-1000,790,-1000,-372,-81,846,-1000,-818,-644,1000,418,518,-54,-296,-455,-1000,1000,880,-133,-357,857,580,423,362,-277,1000,130,-294,-180,357,224,-1000,-1000,1000,-43,1000,230,133,648,1000,328,-133,402,-465,-740,159,-775,667,1000,-832,-98,-162,1000,-695,1000,1000,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{735,-45,660,-223,-386,-649,-798,34,-736,-678,-425,-677,-578,474,681,-992,-474,8,-168,-695,-985,-954,-562,672,-975,-189,-593,-861,233,502,-692,-872,-497,4,-883,-370,50,-596,-851,-536,-105,704,641,805,-468,-909,374,-848,-70,-358,-285,486,268,462,511,477,-872,-101,-646,313,-745,-314,-51,945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{-461,-995,901,-762,-407,-918,383,-345,-155,-184,-941,867,543,-358,-705,224,728,24,-96,-736,915,980,708,-170,343,-327,-258,17,-137,9,-957,-906,477,467,651,-157,594,-769,-710,-339,102,113,28,183,529,-296,155,-793,-228,-252,496,243,-890,817,435,174,-403,21,-972,151,264,706,-246,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{-279,865,706,211,235,357,245,-780,546,-784,-393,-226,784,284,39,-888,-733,-542,294,506,-191,691,-64,76,184,-585,219,-751,747,488,879,646,113,-965,-111,-472,859,-572,-974,763,287,-979,972,764,-615,-729,-403,807,-596,-643,41,736,-337,565,-733,163,570,-700,888,-18,220,871,217,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{69,151,-887,643,-920,561,-543,-714,389,606,-806,159,-635,635,-639,-879,-46,244,-470,351,738,854,-663,-740,-671,-75,563,948,408,993,-85,-438,-326,-990,412,-715,-987,317,76,519,-753,957,492,-710,-23,62,713,-101,-754,511,-12,399,-989,-780,-169,-387,-272,39,58,-898,777,-547,602,-267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{845,664,603,723,-1000,404,-474,544,1000,676,661,-985,184,1000,-933,446,451,-1000,862,165,392,-663,84,923,499,-29,-874,-839,395,1000,-1000,269,-746,1000,-1000,577,919,251,-132,214,-95,965,1000,-294,929,-613,579,897,265,959,-501,983,-278,-447,-1000,1000,47,6,234,39,209,139,-707,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{329,154,673,925,-43,170,-930,-248,607,279,378,-577,-173,658,454,546,-4,561,-861,-625,2,-433,622,362,-743,909,-264,727,-612,521,-443,249,-422,685,-387,-828,-828,-134,-54,-569,-67,672,272,-767,109,-267,-404,453,-659,729,-240,252,-643,823,607,868,416,-699,731,646,-542,-667,81,996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{-367,-171,826,-294,-96,-271,696,822,464,1,-572,935,-528,-182,20,-158,-97,-784,957,-21,998,-22,-78,-316,-984,681,-709,-271,941,402,138,141,-580,729,-672,30,872,520,264,-573,-539,129,-129,999,-602,-283,450,883,927,-299,-786,-108,-750,389,500,836,661,421,-70,843,158,-232,115,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{-47,-539,-371,-504,-843,-296,450,116,-209,-439,587,-646,-45,313,431,484,-361,435,331,221,-450,808,213,666,907,812,-391,956,-726,693,-760,-448,158,836,-793,-169,-257,-721,758,-73,894,-842,662,604,328,-382,-722,371,-247,-803,-460,-997,-339,-353,312,-830,-520,778,-188,970,-195,985,-865,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(long[],long):int",
            new int[]{-328,-524,-575,-580,-823,718,172,-588,-385,-712,-896,835,-982,41,-752,-731,-178,906,-606,869,-726,-39,-264,167,343,-80,630,886,757,-440,-967,-105,46,-195,427,865,-767,-584,854,718,-839,-475,72,544,-628,-368,768,-712,-100,84,271,-769,-622,-438,276,-953,486,642,714,-949,-137,103,97,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(long[],long):int",
            new int[]{89,-505,867,-840,-870,302,268,-39,-884,-148,-174,894,-392,-775,-841,-609,-655,-662,-904,-839,-498,-287,-117,-45,-316,866,-901,656,478,-441,-993,523,623,304,363,-184,-373,-243,482,-382,25,941,-249,-834,-651,-420,727,-270,385,-721,-559,718,-696,-264,-388,490,857,765,-912,-821,-18,465,35,-282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(long[],long,int):int",
            new int[]{944,-849,-391,543,635,458,316,-907,497,-851,931,-876,-399,-959,-616,261,-508,-296,-29,18,201,-109,-927,242,-388,-887,-114,942,716,-902,825,-963,623,-358,-953,-35,-104,-55,-400,-675,777,880,131,-237,160,-123,791,-824,-199,76,987,-56,177,-740,996,-560,-340,-313,-925,-730,-780,-599,-305,898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(long[],long,int):int",
            new int[]{340,532,-512,972,-607,-203,194,185,114,24,9,-453,108,-518,512,416,-715,896,765,-567,-35,-218,975,187,-669,-716,-910,-582,-664,-434,-951,-879,-467,-345,-755,-659,-4,-874,-202,678,482,453,-337,558,287,37,-697,-827,880,913,-328,-328,333,990,-376,661,779,-718,-538,-658,-267,-931,357,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(short[],short):int",
            new int[]{-118,911,-549,-769,64,-686,324,-910,-311,-297,336,-651,-711,768,817,827,-27,480,-730,-659,748,-186,727,392,-821,-697,116,691,526,-864,153,521,-594,-565,-219,757,726,-449,-434,-514,-865,-267,598,-406,568,-615,451,428,-418,-738,-488,-282,163,407,-368,276,-618,438,434,-279,926,-144,866,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(short[],short):int",
            new int[]{848,-546,610,-845,-888,614,-175,-241,-473,-678,359,597,-557,43,-947,-584,-725,-724,32,-693,314,543,10,570,-417,867,532,-603,545,-635,-203,-214,372,-500,275,-75,159,-288,666,372,2,735,-96,825,163,-327,923,982,693,-881,646,-911,810,29,314,-896,120,-153,635,113,314,471,-494,-382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(short[],short,int):int",
            new int[]{-820,-613,-246,882,90,817,-763,804,821,-721,-490,233,142,-327,-105,-413,-693,-44,444,435,471,92,-360,-445,-839,-506,-444,147,-643,59,-137,20,255,832,600,821,888,439,-216,732,431,493,-426,-572,250,-609,26,559,530,-890,220,-466,-352,-29,972,959,412,-371,-762,734,-316,-960,-130,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "indexOf(short[],short,int):int",
            new int[]{917,109,-457,252,-205,119,511,-390,548,-450,915,835,507,272,930,67,379,95,-956,163,651,-276,654,-366,-680,-294,-588,-130,-223,-13,-517,494,-790,661,405,-34,-37,-434,-589,-120,983,483,731,775,-324,859,-352,766,296,230,919,-203,-675,-196,510,-102,-785,29,580,-513,-662,401,105,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(boolean[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(boolean[]):boolean",
            new int[]{509,-116,-856,-713,-529,-930,-264,-930,-348,-817,-638,-308,726,-811,-291,-825,506,-208,716,705,-235,-380,249,-72,-204,2,917,-665,473,-877,-437,540,-544,-122,-218,983,431,98,-62,-904,-234,991,576,-721,820,-382,-123,-827,-709,20,-145,670,-550,-805,-626,865,-118,582,722,-305,447,884,507,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(byte[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(byte[]):boolean",
            new int[]{508,997,-738,-226,-919,348,-284,944,611,66,-775,565,378,398,-49,810,793,352,-311,-720,-980,-27,701,64,-755,-115,672,-219,-736,-864,76,-915,-731,-634,-365,542,-228,-348,763,456,-548,-46,882,-695,363,77,315,736,-548,-743,121,735,-403,-933,649,576,482,-806,887,-627,-431,-174,712,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(char[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(char[]):boolean",
            new int[]{855,-673,-793,996,347,-530,41,-971,816,52,923,-304,964,-809,104,108,-925,340,214,136,-223,701,-574,212,408,341,-684,-219,764,145,693,659,759,371,-792,186,-642,-287,175,-748,-955,-502,708,16,-964,933,-997,-542,-535,-830,-590,-211,-146,339,-209,-821,322,706,582,244,-816,611,977,-656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(double[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(double[]):boolean",
            new int[]{225,26,-152,46,298,-569,-441,306,-735,-989,-670,-342,283,818,-558,-345,508,246,844,-618,368,-308,-212,-900,956,-729,224,-449,-481,825,-191,565,-140,23,707,890,855,-106,258,552,202,-201,-906,-543,32,542,-719,-555,92,-907,683,-462,809,-766,-106,-230,749,-701,807,-389,749,-812,-838,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(float[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(float[]):boolean",
            new int[]{-818,367,271,902,748,-725,892,-477,405,69,796,-482,766,-50,-686,-828,243,-788,-672,-264,869,-733,-72,-545,-16,532,958,883,-241,-218,174,-716,-46,-561,-797,10,-748,-823,-527,-991,-928,-765,-139,846,840,856,-68,950,-635,379,314,-774,-817,-279,-963,584,-477,-830,-543,484,874,748,-247,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(int[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(int[]):boolean",
            new int[]{345,-802,-130,894,701,504,877,690,987,-187,-616,-327,-152,987,-56,-128,-422,-778,103,-137,-653,466,635,234,-809,15,-97,-735,-234,693,227,864,907,767,39,635,362,136,855,-429,734,714,673,343,-391,954,-757,376,-862,-567,-149,-986,924,103,775,926,-33,390,-672,635,-378,653,572,-465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(java.lang.Object[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(java.lang.Object[]):boolean",
            new int[]{695,461,-834,339,644,-460,749,716,1,-46,995,-321,-776,-183,84,-820,-805,-8,747,91,428,1,215,-461,-945,-367,-355,760,57,762,161,342,-736,-518,400,265,559,544,961,-70,-610,338,543,470,211,-877,690,117,-178,115,-920,-911,662,143,-968,-444,112,66,-425,591,-425,921,-230,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(long[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(long[]):boolean",
            new int[]{-601,7,695,932,574,-782,-896,453,362,-308,141,-391,246,-665,36,-989,13,873,862,-478,-69,806,-829,135,-802,180,-642,-448,274,-803,-246,-121,-377,-667,908,330,-93,306,-434,-693,727,-975,438,750,655,-509,996,-711,455,488,306,389,184,-132,-886,431,-857,265,-683,-564,-455,-862,326,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(short[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEmpty(short[]):boolean",
            new int[]{-112,-707,-930,790,96,777,-301,-934,455,-563,-307,656,387,528,-574,-609,-916,242,985,504,-794,38,-545,55,-257,-269,954,78,807,337,-521,-739,-432,-739,19,-280,-669,827,58,-979,850,395,-415,978,349,330,-943,939,461,960,-668,-681,308,-686,-414,911,-729,907,401,-817,-318,-847,-160,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isEquals(java.lang.Object,java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(boolean[],boolean[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(boolean[],boolean[]):boolean",
            new int[]{7,-143,-84,695,-656,513,-698,-625,650,-908,-378,259,100,-178,291,277,950,92,889,-945,742,-148,-608,-336,-838,-37,-980,191,259,-47,-928,-57,427,-694,-162,6,-165,-829,30,-71,-130,-858,-983,375,516,644,-390,-282,-392,383,131,778,402,291,-917,147,-985,463,-157,895,936,675,498,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(byte[],byte[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(byte[],byte[]):boolean",
            new int[]{580,-365,-845,861,-752,-516,-29,-337,12,-250,580,665,315,407,592,-755,-529,-865,476,358,263,516,505,647,-611,461,974,-449,-682,-19,-795,-965,-806,-769,480,163,-959,-437,175,286,-269,214,-889,-658,901,-783,-599,-529,350,-853,-67,966,-700,-619,-970,-579,353,-306,803,-122,991,-764,-766,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(char[],char[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(char[],char[]):boolean",
            new int[]{-735,-437,53,-335,349,-735,965,875,-874,-557,594,-159,-767,-210,-613,-358,152,590,-309,843,-676,680,-358,371,435,940,-648,-535,80,811,-25,-187,-173,246,-632,-566,535,-76,-864,816,741,558,-870,-659,900,-303,675,170,286,-448,-842,350,-468,59,19,201,897,-359,-403,-239,631,-39,-161,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(double[],double[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(double[],double[]):boolean",
            new int[]{966,995,704,509,491,338,-709,-294,298,-477,-6,435,40,-328,-922,398,-930,-541,-330,103,-615,609,-491,-775,-836,153,-15,-542,108,81,-887,53,-577,585,503,787,993,-941,882,-848,64,-366,249,162,-429,-789,-612,410,166,-369,-878,710,-81,-64,408,14,-279,-481,218,-860,468,348,963,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(float[],float[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(float[],float[]):boolean",
            new int[]{575,-802,-244,-694,563,-680,263,270,-767,-693,78,-110,-689,-439,-90,827,482,-584,-758,-877,938,-269,21,426,-99,219,820,632,-546,-718,-452,676,236,-104,-341,-155,-482,-951,459,-117,65,635,604,-558,-237,309,-832,966,-531,-196,743,-35,-191,-543,-6,-176,687,-684,771,-213,188,-937,-539,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(int[],int[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(int[],int[]):boolean",
            new int[]{-658,-277,-234,-708,-697,466,956,-467,718,50,-583,557,-381,787,-666,174,-117,528,70,994,-753,-848,-3,931,518,784,-525,457,703,121,-206,379,-963,-384,-482,-234,268,206,276,168,0,-202,778,-733,325,743,588,726,601,203,14,-865,-227,-512,-628,-312,-644,169,-153,-494,-95,134,-836,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(java.lang.Object[],java.lang.Object[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(java.lang.Object[],java.lang.Object[]):boolean",
            new int[]{225,-211,-373,-989,-307,-589,-134,-402,450,-643,-608,-635,686,-932,659,197,1,585,311,-210,342,565,689,-18,-584,458,477,-94,-326,412,498,486,-778,976,115,-244,-837,572,934,-214,755,-115,-383,-460,-501,791,447,-60,-151,-718,-964,-413,-459,945,-971,125,729,-12,23,-905,111,552,-982,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(long[],long[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(long[],long[]):boolean",
            new int[]{242,-336,-370,558,100,954,883,994,658,-379,972,555,878,-700,-78,-56,-198,488,852,638,-234,-779,-694,532,755,-866,-12,-922,-303,-305,771,-550,307,-270,295,528,756,24,813,37,-155,-282,-23,58,-337,541,241,-700,-253,454,243,-974,678,218,-919,-691,-278,601,-247,-538,-363,458,204,854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(short[],short[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameLength(short[],short[]):boolean",
            new int[]{-275,-362,179,-942,-204,-507,-198,-536,340,-203,-200,-50,155,-286,-943,-923,-161,183,-895,-647,-182,-767,412,904,74,-254,-227,-708,-572,343,754,940,-529,276,612,-603,401,-948,629,48,-633,895,348,-260,284,-936,-96,-682,-530,-413,275,-953,-12,-17,-897,-475,-913,237,-466,-154,-220,415,-570,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameType(java.lang.Object,java.lang.Object):boolean",
            new int[]{59,146,-273,-276,-32,957,-213,37,448,-515,-332,-555,-671,-849,-674,708,821,-210,336,-270,706,-717,-480,-918,-324,-731,-646,-143,-98,-955,747,-143,829,91,744,-209,-339,-894,163,817,47,-91,299,574,-959,124,-597,657,-383,-844,859,388,-306,-333,-264,-320,926,-519,694,-173,-617,641,103,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameType(java.lang.Object,java.lang.Object):boolean",
            new int[]{31,-588,-578,-622,-789,-224,132,-289,232,-497,213,132,-7,684,-922,764,-6,247,-310,378,278,-233,448,311,653,74,-922,883,211,-541,-414,573,137,-347,-699,811,-767,171,487,-31,622,-899,115,900,-298,965,-13,-802,-430,-103,-410,255,861,-953,-165,128,217,867,-873,275,-522,-277,90,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "isSameType(java.lang.Object,java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean):int",
            new int[]{45,-569,-501,-610,123,630,-756,-324,-101,653,172,221,2,-866,2,927,-885,-304,527,714,301,934,880,-680,123,-891,-884,-783,971,288,328,-396,-89,982,-745,-257,420,153,160,-227,443,-97,582,-404,-659,-917,523,413,-474,-364,596,-944,-188,-434,-280,401,892,-932,-952,-184,676,-388,14,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean):int",
            new int[]{-173,-361,-86,-371,789,-424,610,267,-817,501,-63,113,-628,301,-335,984,235,-974,-828,384,-189,711,-569,758,392,745,147,-587,-181,927,-389,-575,-302,-384,639,-814,257,249,714,-898,665,-132,338,399,755,323,510,995,-690,307,242,972,-649,485,74,-716,154,660,-353,56,-827,-891,582,-946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean,int):int",
            new int[]{-269,-459,-504,769,195,502,959,265,159,-214,-618,-813,404,607,-360,619,735,562,980,-870,13,961,-228,477,580,-185,-639,-291,872,-143,970,-725,-213,6,-430,210,-997,-936,381,-778,844,120,-772,634,-487,-873,-606,490,574,-577,768,871,-604,-421,137,-494,-54,915,257,-950,812,-772,-2,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean,int):int",
            new int[]{-1000,975,-174,-603,-821,817,1000,109,-414,-101,1000,-104,-419,-2,29,381,-599,-1000,-694,-284,-1000,-786,-435,119,-1000,151,-407,-544,415,-301,-639,950,275,-36,373,-253,-825,512,-1000,-278,-775,1000,-77,226,-926,262,-537,967,162,-133,560,-103,-451,-699,-691,-98,175,-1000,586,916,883,134,-990,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(boolean[],boolean,int):int",
            new int[]{386,701,296,-217,-227,394,22,189,-739,709,526,573,736,-266,-469,753,-728,-983,-984,36,-17,854,165,266,865,971,-79,156,-277,767,395,330,-939,360,363,-59,-542,35,-178,-473,-678,-627,148,838,-840,347,-197,793,-431,958,718,-605,-49,-372,-514,516,-316,-526,338,-264,899,-150,-500,-861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(byte[],byte):int",
            new int[]{-538,129,391,797,116,-185,-998,22,-881,32,743,72,-435,822,256,224,601,-886,131,7,141,-167,733,908,153,-151,442,-347,-806,87,154,334,138,-888,235,48,-961,796,363,450,-39,-686,667,-390,240,618,415,945,-836,816,124,605,291,413,432,-304,-209,-744,294,963,63,197,196,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(byte[],byte):int",
            new int[]{485,72,174,-125,-142,502,-66,-450,-466,-470,363,509,690,338,-409,-591,-872,-934,351,179,398,659,808,-385,646,553,396,469,-258,395,364,199,499,-20,-864,916,-199,897,761,-202,362,-6,753,-754,-566,693,-555,422,-667,-537,606,427,-102,-734,529,369,-898,-291,46,829,982,785,-993,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(byte[],byte,int):int",
            new int[]{997,703,249,-285,932,239,-527,642,-812,-882,5,678,-21,111,-872,822,799,-284,-449,507,64,-372,380,49,64,-585,893,-57,-22,12,-207,-541,472,843,-553,-513,745,-311,992,-802,803,-199,110,681,212,-857,813,477,-401,172,-396,-365,792,-904,-976,818,-372,-850,-199,-198,176,-863,538,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(byte[],byte,int):int",
            new int[]{-586,-458,667,414,567,1000,33,-1000,-575,-1000,-1000,1000,1000,-1000,-1000,-214,-166,396,756,208,-665,-887,-358,839,-347,1000,327,857,-857,-1000,19,634,214,-38,174,-249,-418,1000,593,-129,-572,-45,-590,-97,1000,-430,-1000,-1000,-174,124,724,21,143,-128,1000,349,1000,59,885,1000,-77,-1000,1000,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(byte[],byte,int):int",
            new int[]{-68,674,-116,568,-480,319,-771,-383,-913,-60,-986,914,916,-35,-492,-681,618,352,156,-517,111,-504,-421,-464,57,332,300,622,567,-223,-60,298,-247,901,-559,317,-941,-492,-723,-96,221,166,498,277,-859,-834,-973,854,-947,-639,629,389,887,380,627,-931,950,-648,650,532,733,-101,391,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(char[],char):int",
            new int[]{-875,-135,499,11,321,436,-576,-294,-725,261,-194,642,-301,672,27,193,197,-746,106,715,510,-759,-668,151,570,-308,-548,299,-617,251,184,919,-666,281,-163,-450,-540,-8,166,-637,480,821,-769,685,916,-234,-997,-86,-476,-370,-271,478,424,214,650,218,-181,381,592,97,985,-402,114,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(char[],char):int",
            new int[]{1000,-577,1000,-184,-937,1000,685,766,-1000,-1000,219,427,-307,-420,-883,566,867,1000,-432,-17,498,-146,537,883,-582,-1000,484,-609,620,-707,10,1000,-36,-1000,303,349,295,658,1000,748,675,-204,828,586,-1000,-758,-537,-742,652,-372,1000,-639,329,1000,-75,-974,-801,-244,1000,-718,-847,-1000,688,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(char[],char,int):int",
            new int[]{-301,564,712,444,-262,194,-184,710,-153,-816,77,-70,484,-19,126,-770,486,667,-5,283,-625,500,756,-865,934,427,164,836,-47,-923,887,189,184,716,-150,907,-228,182,188,59,667,904,-640,793,869,-211,85,17,749,947,943,987,-854,-961,701,-465,-559,372,748,-924,420,-251,-639,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(char[],char,int):int",
            new int[]{-85,-686,-459,-354,-849,-975,-41,-788,-119,-957,886,900,898,-614,865,-330,-633,-941,283,128,771,791,-358,102,-615,396,144,257,291,-587,307,-831,121,-398,265,286,-112,467,358,-228,-441,21,713,350,345,697,-423,-46,-937,517,386,-744,-596,416,755,934,-561,-35,881,-58,-715,-352,234,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(char[],char,int):int",
            new int[]{-980,-47,960,117,699,-850,-179,371,258,126,540,256,-179,148,-836,-911,246,-834,-802,-289,-788,96,18,269,932,256,138,207,-140,376,-659,689,853,-371,-360,703,487,-389,5,651,485,-660,-164,-645,535,964,-702,98,183,113,777,580,-64,-392,972,479,220,573,-565,662,-573,925,726,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double):int",
            new int[]{-529,-92,999,910,-939,-706,-219,-905,480,-181,-380,-932,-610,867,-820,872,-696,-64,135,-719,-501,154,-883,-843,-276,-184,-678,-620,-42,-327,-925,-842,-317,620,-641,-803,861,-655,-405,-71,126,569,801,-170,609,-456,-313,687,194,-353,181,-387,-924,-904,449,-397,982,88,-462,-552,-378,997,457,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double):int",
            new int[]{543,-360,-301,622,894,-898,-977,-971,-654,-888,-360,739,-141,-461,-138,-407,-131,229,741,-96,12,-884,260,458,537,277,-754,-205,-790,-528,-704,854,687,570,777,743,78,230,-794,116,-230,231,-817,471,417,908,443,505,466,-697,580,611,-414,-145,-484,-418,-71,-307,-117,-365,-722,961,-832,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,double):int",
            new int[]{-296,496,-147,543,-203,-688,-265,430,-904,566,-374,-858,988,-318,-996,919,592,-635,351,-658,-149,784,133,604,-540,589,-297,631,-747,413,-766,383,991,836,-852,-798,449,132,460,-247,-880,-452,-70,-221,-74,-643,30,-310,-105,-536,-699,804,-61,963,-90,970,-411,719,-48,260,797,-224,346,-577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,double):int",
            new int[]{-103,997,-981,-113,428,324,844,-553,-806,-390,-100,675,99,-57,-539,-742,808,481,-180,-33,166,426,-575,569,-984,885,664,-296,-890,111,8,-631,192,-62,571,-764,-806,583,-920,697,-2,-593,783,-814,-781,716,841,158,-908,226,-573,870,653,-564,-104,-940,-802,-168,-818,531,-792,549,870,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int):int",
            new int[]{644,-60,-364,591,-305,54,82,95,-892,599,-187,-705,597,-487,-182,913,696,-963,-418,-259,802,556,265,241,-962,260,563,-381,-627,-885,565,975,-775,502,-983,544,-352,332,-464,-985,104,-572,-474,-569,830,560,-191,-713,-137,611,-670,102,855,62,-330,-59,47,-758,563,-828,-52,-419,137,-929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int):int",
            new int[]{1000,1000,-382,1000,233,194,-1000,482,-1000,441,-418,959,-803,-368,-762,1000,690,-1000,-481,-785,935,740,398,356,-428,165,110,392,-1000,-885,1000,1000,-33,-557,623,122,-573,-489,-1000,-151,-27,552,-552,-1000,945,1000,151,-1000,-760,811,-1000,407,-253,246,-1000,-602,-285,-154,1000,-1000,-130,-897,1000,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int):int",
            new int[]{904,128,-397,761,-800,752,-620,-667,721,223,870,-649,-763,272,-877,549,580,-231,-777,-638,954,-470,476,-321,636,-992,667,120,-590,-465,-882,59,865,-386,361,-906,551,233,-622,-429,-72,149,555,-637,4,675,-211,145,-376,613,496,-890,-518,682,-334,-736,88,-975,-434,-778,180,-791,226,-201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int,double):int",
            new int[]{-1000,-91,-138,-1000,-1000,-35,-393,712,-1000,382,1000,-713,-325,669,-188,282,613,1000,-1000,126,-1000,-1000,200,-184,-331,-713,-1000,275,-122,-458,-168,-431,361,93,754,12,-1000,1000,182,-1000,1000,-319,-1000,1000,-353,1000,-545,400,-1000,634,-717,1000,-1000,141,-1000,1000,423,-837,-470,-1000,1000,-786,-136,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int,double):int",
            new int[]{-977,664,822,490,-731,431,-905,816,-112,252,-746,428,-924,318,297,281,111,-510,-958,726,335,-789,-748,-25,-299,613,474,25,-154,-934,-841,637,120,-434,-4,-721,893,348,190,100,561,902,-567,-87,-815,158,-292,-35,429,-958,-255,812,578,827,922,21,812,729,746,612,616,-920,637,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(double[],double,int,double):int",
            new int[]{-322,-742,828,46,-966,621,-412,666,413,-660,297,61,-171,-132,275,-246,-507,157,747,500,951,531,-438,553,-668,-473,837,717,-283,785,-340,101,-804,-757,410,845,-996,-151,981,226,257,-180,292,460,318,59,782,323,549,776,-677,267,329,572,934,66,598,-702,23,-717,916,839,-530,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float):int",
            new int[]{-151,-246,-31,906,-391,51,-672,-992,356,558,8,-877,-744,842,291,-357,-760,645,990,101,368,-457,814,496,-551,-941,985,64,-203,962,-414,836,927,-878,-245,96,-82,648,602,162,221,834,-488,-213,-262,-134,-623,925,163,-794,188,-409,-222,109,-552,130,143,-173,-118,330,-514,689,44,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float):int",
            new int[]{-35,-492,-397,812,424,-496,307,344,817,312,-57,977,-993,-465,-48,48,-663,-219,-189,546,-965,145,738,978,-695,705,-130,-397,789,758,-357,-79,-49,228,-207,54,-65,983,-877,-25,401,628,993,-586,8,-250,336,-294,260,579,940,868,726,532,-287,724,843,231,-294,50,887,231,-237,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float,int):int",
            new int[]{-779,595,281,-454,49,-74,-640,122,-991,139,603,-423,73,-323,-897,-131,724,507,483,292,-834,408,672,-703,-334,-767,109,873,-579,-409,66,803,-34,-596,-237,834,-930,601,322,242,-692,893,-774,569,-927,-59,943,435,623,-416,-386,-784,-24,-226,333,-888,950,-175,414,701,431,230,-734,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float,int):int",
            new int[]{-940,-115,824,-377,22,328,-1000,-92,-743,1000,954,321,-63,941,-458,1000,374,-821,-286,344,-230,349,1000,-418,-78,480,288,-211,316,-699,680,-1000,843,-403,-282,689,-31,-589,54,934,-1000,459,571,746,387,533,-360,663,-41,-407,992,837,1000,10,355,-976,-95,-12,-304,-1000,-48,1000,670,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(float[],float,int):int",
            new int[]{-22,331,-557,582,-208,-763,-47,75,173,-397,629,-78,-207,676,-171,-171,-542,-5,-214,321,616,445,-511,661,-551,-109,367,-924,268,647,-232,445,-460,-959,227,-725,795,181,-118,-730,938,-377,-992,-909,6,130,-348,1000,-686,-60,-423,-987,-907,-941,350,-105,233,-48,292,-113,-464,726,213,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(int[],int):int",
            new int[]{-970,879,-229,894,-541,939,823,-393,-204,49,-359,-393,932,574,-517,153,-637,817,-73,-217,233,-70,885,-876,335,557,615,7,416,-565,-793,144,704,109,411,559,213,383,-913,467,-892,167,-962,-641,136,966,556,677,24,419,597,-349,571,32,-814,-362,154,-501,-598,586,-610,885,-934,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(int[],int):int",
            new int[]{357,-473,-635,-510,368,183,-869,-940,865,-312,-325,241,514,-837,-913,-757,-472,357,-873,-759,730,900,-564,25,-245,-296,217,-172,-387,894,-707,-750,447,-413,-192,106,956,298,258,581,-346,780,-978,70,-98,710,-880,776,487,-220,-469,273,-584,-506,-923,35,529,-774,-384,-784,580,-39,-901,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(int[],int,int):int",
            new int[]{207,944,654,-246,392,-746,674,-284,432,120,423,430,720,-934,108,78,357,124,568,851,677,-597,-241,-574,703,133,864,-973,56,-969,-788,632,-392,-139,134,-629,-216,-539,618,927,826,168,249,-178,580,577,-751,910,-628,-127,-329,-942,435,-394,-214,86,-18,682,-462,307,-491,616,566,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(int[],int,int):int",
            new int[]{-19,-596,-435,381,-193,252,893,438,426,-596,-479,-982,-693,657,295,-684,622,450,-66,101,-291,-156,942,-944,-869,-928,-827,161,575,-766,977,-564,-348,-216,795,-159,236,-182,848,880,-574,-162,-886,-442,-587,694,195,748,601,-315,-156,723,-839,431,-24,-363,-106,-945,760,-912,-282,464,52,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(int[],int,int):int",
            new int[]{487,-681,-587,-643,307,-746,369,224,938,-247,-27,287,489,-568,485,231,-970,346,815,-563,465,-880,282,-99,452,37,-981,-987,468,255,318,591,-84,384,103,-929,756,33,539,-516,597,-408,301,-17,357,553,-544,38,-293,927,78,176,-692,-15,747,136,-442,-561,-120,-345,-985,449,-487,-242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{-5,-347,685,-278,659,-573,812,-818,847,868,-6,-744,-327,69,609,186,603,-376,207,856,422,937,452,-116,524,-807,531,234,-525,98,-448,337,-452,489,-302,403,-755,-82,-215,-536,705,-619,-736,513,-675,540,-323,25,931,-44,117,163,-609,132,-671,976,717,507,-237,64,340,915,86,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{-274,651,-28,-10,-114,535,-569,-361,6,-27,207,222,-620,-437,-478,-244,-55,-488,-836,887,411,-47,945,-992,237,457,638,-699,-896,-688,-891,631,318,-836,-356,992,870,-446,-659,972,770,-959,-189,592,-298,-329,435,764,60,829,-71,-352,-29,-287,-914,183,63,41,-786,223,-595,991,-995,844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{681,340,177,-336,-435,-428,212,884,805,-457,-346,923,-151,-433,621,-726,-241,-238,596,-636,383,-105,1000,803,-998,474,598,-922,-945,-871,391,80,304,-737,950,-424,110,-7,-37,-455,-633,475,-778,168,93,-168,188,159,653,140,-133,-867,-821,170,-231,-239,-125,-81,425,542,557,366,132,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object):int",
            new int[]{1000,-1000,1000,403,434,-1000,1000,-793,463,-522,837,902,1000,-835,279,-361,-151,16,-1000,457,936,-832,1000,-113,-81,-680,530,1000,-1000,562,541,-342,-1000,624,394,-86,525,1000,1000,-85,-1000,1000,1000,-783,-197,440,-1000,243,159,1000,83,131,791,358,462,-1000,179,-910,1000,466,-693,-776,138,-829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{214,683,-200,-223,373,962,-258,859,792,-329,229,628,-393,648,944,-842,-732,-407,432,30,-431,586,-528,848,708,654,-223,-646,352,-825,637,984,-170,-485,-729,-413,306,-29,-528,994,71,-182,-629,-877,916,83,-924,414,992,-510,355,504,-507,-13,-455,-732,-924,-782,-573,630,978,-628,-64,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{-65,-866,696,813,202,-120,-164,509,294,979,155,462,988,-563,349,795,828,59,-428,-755,-724,564,558,706,448,-207,-32,507,-416,-530,-249,-48,-410,618,512,529,666,-626,603,-847,352,-304,999,844,158,274,-123,-717,342,-229,256,-123,394,496,-919,134,-709,-435,659,424,712,655,-475,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{-343,-318,-47,297,130,-405,602,239,7,-1000,1000,-903,11,-1000,-1000,-704,1000,1000,1000,537,-702,-555,1000,772,54,302,1000,-1000,-152,712,-763,-505,-295,-804,178,899,659,1000,-346,491,1000,993,-1000,-752,312,43,-129,-390,-1000,317,1000,161,1000,-1000,-155,-491,-443,-237,865,905,51,677,226,-90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{778,392,-912,-585,-705,997,290,171,734,836,-97,-63,680,-876,917,-173,886,174,-367,153,-155,896,276,-987,-561,614,637,291,-305,-174,941,261,-102,161,324,854,978,439,-932,619,-573,-522,41,-581,788,-886,-333,-17,-782,725,-786,579,567,-491,-853,276,-46,734,909,-463,921,-784,-426,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(java.lang.Object[],java.lang.Object,int):int",
            new int[]{-503,953,-730,-572,688,160,201,-836,926,-145,-776,502,330,335,539,-216,-50,-461,806,-234,89,708,124,-616,166,-983,-256,565,123,-746,-367,-295,-468,211,-758,299,-716,153,531,212,30,-565,-347,50,-629,-828,565,198,227,701,269,-770,-240,-494,-603,-407,-208,-733,-586,-255,-291,738,832,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(long[],long):int",
            new int[]{-182,-861,185,659,-738,771,-549,266,-373,919,-104,313,381,-17,-426,-796,71,-284,606,919,747,970,-487,-870,487,-767,-129,-898,-876,890,-802,-165,-431,-976,-36,-352,305,-308,-619,493,-459,79,266,-396,286,742,-341,-189,166,-820,-875,-171,217,-566,696,-870,-950,88,-720,636,819,-918,4,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(long[],long):int",
            new int[]{-25,728,960,-857,-198,-715,-135,501,97,-402,-18,-312,-629,-462,-55,605,-997,768,315,69,481,-977,-484,511,468,689,640,293,-559,268,-338,-273,915,-869,-423,-381,-552,154,-256,-762,546,-747,-651,-32,348,-359,311,-426,-449,-303,158,21,414,747,-522,-908,-121,768,-617,657,-147,56,88,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(long[],long,int):int",
            new int[]{-515,-1000,-881,154,-419,54,-1000,2,-324,-293,-292,-821,466,681,85,928,-701,-629,1000,-414,-252,107,764,-1000,-305,373,727,-683,283,298,-966,-213,402,-575,324,-889,-1000,-169,-823,1000,812,-238,977,-32,832,595,1000,723,211,880,-385,-426,146,-260,1000,-1000,-331,-511,333,-863,-644,-59,1000,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(long[],long,int):int",
            new int[]{-595,-1000,-1000,-1000,1000,-1000,-1000,-1000,-1000,-724,1000,-986,-1000,-1000,613,715,-1000,-1000,-966,-296,785,-1000,-1000,-1000,-1000,1000,1000,-162,1000,1000,666,1000,1000,1000,-65,-298,-1000,21,-1000,1000,-73,400,350,-1000,100,1000,-177,1000,1000,384,285,1000,1000,1000,1000,-1000,-590,228,-1000,-1000,-1000,49,-453,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(long[],long,int):int",
            new int[]{213,-839,-167,-522,687,190,-100,-642,-194,25,-319,542,490,586,741,-648,-539,1,-71,971,251,-73,971,-887,55,-750,849,869,838,470,-580,967,791,625,834,-105,-771,-5,-285,-629,328,-953,-254,526,712,-936,-618,796,842,725,696,-967,406,-428,-942,591,-568,-304,-720,-691,-218,-721,142,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(short[],short):int",
            new int[]{-159,951,302,519,872,193,262,928,743,34,-825,-22,397,831,-997,514,582,401,956,285,-311,-75,90,548,-823,115,-11,14,-504,-764,974,874,-463,68,802,802,83,227,643,829,448,-603,-720,-243,-408,-20,953,-617,330,408,-975,-762,-54,955,736,753,539,272,-269,822,-303,-957,738,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(short[],short):int",
            new int[]{-825,643,-727,715,342,-442,125,-965,-797,260,-640,-413,-85,619,-357,478,563,-297,844,328,-836,-558,52,102,127,-767,-984,180,522,997,-98,323,-367,-275,-254,-609,674,-129,-158,-378,349,-930,450,-903,-77,-370,-326,-411,887,984,123,-561,985,879,77,314,318,-555,-744,749,607,508,-973,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(short[],short,int):int",
            new int[]{335,-213,-717,-893,-907,-967,-603,-256,-599,-308,682,142,-202,461,927,-323,-640,-683,265,670,243,222,-773,182,-151,-981,911,-554,-177,-728,-763,-984,368,851,311,847,-306,774,18,-779,-170,999,168,655,-572,-328,-290,767,-220,654,884,-540,-217,-919,483,689,-726,170,-125,-391,-167,528,-124,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(short[],short,int):int",
            new int[]{-45,282,701,-245,489,-132,-160,-639,349,-832,282,-715,-420,-729,-820,829,144,950,-933,867,-548,394,327,90,-883,555,-972,-438,-293,-402,144,943,486,-908,189,-116,-493,196,455,-225,-201,333,164,281,-159,-376,126,-82,-967,-290,-243,294,-530,-531,-292,-411,-63,650,744,329,790,-568,-975,-395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "lastIndexOf(short[],short,int):int",
            new int[]{-669,-639,731,624,204,-965,-813,-139,-370,909,482,-545,0,-928,298,-960,714,-936,-838,-965,40,-518,-961,-877,-167,15,-384,-323,512,311,-551,912,970,206,178,-331,-567,494,116,411,647,622,722,-722,260,-506,-325,-139,769,-83,-425,725,970,109,-325,-345,517,-822,-569,99,885,-479,-801,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("ARRAY:[Z:1:26:java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(boolean[],int):boolean[]",
            new int[]{164,-879,579,-152,942,266,436,1000,147,-314,-464,-53,924,-643,1000,-613,742,850,-454,-84,1000,-744,-247,-565,-594,-952,-854,-155,-218,-909,-593,234,323,-219,-109,-376,592,499,916,-335,-591,-59,-401,600,-953,73,-1000,-473,-192,-424,1000,1000,-109,-3,-254,-1000,-814,-1000,1000,-776,850,-810,-258,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(boolean[],int):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("ARRAY:[Z:1:26:java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(boolean[],int):boolean[]",
            new int[]{-1000,804,-752,-770,877,139,532,-147,368,731,181,-1000,-563,54,-451,-387,-1000,-427,-408,-730,-419,796,671,-724,417,-476,326,212,197,381,115,251,-156,-744,897,-659,628,-40,90,298,-1000,414,730,1000,-1000,414,334,165,485,462,2,-969,1000,30,-989,185,1000,404,-48,-367,706,-1000,-152,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(boolean[],int):boolean[]",
            new int[]{480,327,136,-571,-173,-342,-737,-771,979,580,-523,704,-414,592,596,588,-119,-582,-673,-391,-464,-451,744,335,-503,-789,75,363,-561,431,-766,-431,368,389,-698,-185,-876,600,329,-996,-950,-331,-797,732,927,-727,-872,-104,457,-96,275,-446,991,413,558,-867,-983,-90,159,610,985,-293,-572,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:MA==:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(byte[],int):byte[]",
            new int[]{766,938,837,-764,930,221,-427,-723,416,887,619,260,-868,-846,530,-896,743,-270,388,226,68,-588,367,764,686,-239,-174,-985,-558,-700,-665,-427,-147,797,-527,-854,611,-371,-381,-120,341,964,63,168,-427,557,716,392,-518,325,-548,804,373,-457,716,852,-883,-493,363,-334,-228,-191,-48,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(byte[],int):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(byte[],int):byte[]",
            new int[]{385,-1000,-1000,-103,984,1000,242,765,-231,-919,-606,185,206,119,-391,-260,-1000,372,570,1000,316,-332,-568,119,814,-134,-585,870,827,1000,-1000,-429,3,-492,-1000,-374,650,1000,481,-132,-567,1000,558,-511,895,-99,789,251,1000,-1000,-209,657,-135,908,-372,794,-750,-26,1000,106,1000,923,884,788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(byte[],int):byte[]",
            new int[]{-846,329,-831,-578,-227,347,331,708,-738,-105,-639,-263,-920,-110,240,161,262,889,998,167,-877,-293,-752,631,-167,408,-184,-41,842,920,156,864,-509,-347,-264,908,-702,925,-99,-567,-588,-252,868,674,-519,-423,426,311,-180,-986,727,490,164,-901,-948,416,516,-128,-103,56,647,-865,860,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("ARRAY:[C:3:24:java.lang.Character:RQ==:24:java.lang.Character:eg==:24:java.lang.Character:EA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(char[],int):char[]",
            new int[]{856,-250,965,634,400,578,564,-94,634,-433,434,-757,-830,-944,904,293,148,813,891,-270,956,976,94,513,63,-799,348,397,-270,561,801,6,-567,134,453,204,-20,226,-499,-501,523,642,-68,801,909,-407,-140,946,155,420,-808,125,240,-432,412,-421,834,29,-518,758,-884,-199,173,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(char[],int):char[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(char[],int):char[]",
            new int[]{-905,535,475,588,566,-285,-35,-895,875,-804,573,286,383,62,-827,932,-576,-986,726,390,489,851,936,223,-489,84,-104,-695,-545,461,363,370,543,-434,170,458,562,619,-819,-260,-101,-834,-796,722,-752,415,335,993,-773,-355,-737,-646,569,-667,325,-576,966,435,253,621,215,-411,908,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(char[],int):char[]",
            new int[]{-812,642,-241,-697,110,-58,-541,624,768,-809,-537,689,339,-426,-157,561,-638,254,790,-411,-139,-109,733,-761,509,594,-955,635,-946,807,-602,-954,252,980,769,-3,-240,-77,960,685,597,191,534,938,-932,31,-930,89,-622,987,787,-177,560,570,755,311,-232,117,113,-249,545,-665,-923,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("ARRAY:[D:2:25:java.lang.Double:LTI5LjE=:45:java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(double[],int):double[]",
            new int[]{327,753,935,-291,-806,661,366,-596,870,-276,478,-734,-242,-541,-211,-366,-850,-870,-85,92,527,-958,-88,-565,-212,-529,339,309,-869,713,239,612,301,-869,609,766,-769,-737,652,-156,-300,-928,137,172,607,242,513,-456,-345,49,-922,441,-170,-200,495,781,-88,-926,170,611,447,-916,650,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(double[],int):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(double[],int):double[]",
            new int[]{109,1000,1000,-1000,-24,-9,1000,-1000,676,843,1000,651,-282,-1000,931,-863,-1000,-443,-685,-1000,678,-1000,664,-316,1000,-1000,4,-579,-834,1000,-830,885,859,-1000,196,1000,-455,146,539,108,-1000,-330,446,-1000,-281,1000,-506,546,-341,1000,-1000,-338,516,-901,388,1000,-731,-1000,319,-370,396,-1000,1000,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(double[],int):double[]",
            new int[]{98,360,-948,184,90,-346,785,950,609,462,324,-758,420,908,-345,413,-279,-155,-857,-814,326,982,41,-456,599,-386,655,457,-712,-897,-775,-288,367,617,34,77,319,-117,922,966,-96,661,439,378,401,514,-800,389,-475,63,651,763,831,354,-580,-651,215,259,708,845,384,865,-250,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("ARRAY:[F:2:24:java.lang.Float:MzguMw==:32:java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(float[],int):float[]",
            new int[]{633,79,-204,383,94,896,-669,419,-761,-939,-485,311,245,183,-795,715,-913,-804,-772,-79,-737,540,-799,-970,-595,156,62,-204,-20,-381,369,-49,-872,926,739,759,-131,599,-627,262,424,-401,500,-771,173,-733,802,-828,-86,-997,415,963,507,739,914,547,746,368,852,-938,-673,-734,-98,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(float[],int):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("ARRAY:[F:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(float[],int):float[]",
            new int[]{-677,-645,-962,-216,-629,-221,655,28,-781,719,727,-874,327,865,-638,385,-715,666,-847,-557,-860,761,684,368,933,442,-728,243,846,-578,760,-474,242,-583,-579,387,-957,-851,656,970,-600,-745,-976,50,413,263,409,-78,-229,-653,-397,-485,630,-152,-542,717,-238,-269,895,658,100,-961,516,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(float[],int):float[]",
            new int[]{-855,235,360,-531,406,820,410,-786,4,-938,867,469,920,827,-66,-1,964,542,-154,364,-548,114,-746,568,-121,288,-299,800,-624,-631,-105,705,629,469,-234,561,146,559,868,-549,195,437,603,60,602,469,953,98,679,429,-85,-515,993,-878,-301,450,-563,574,-119,447,123,555,164,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("ARRAY:[I:3:22:java.lang.Integer:MQ==:22:java.lang.Integer:OTMx:34:java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(int[],int):int[]",
            new int[]{148,-871,817,203,-71,931,299,-912,903,-310,-108,-211,439,494,-753,-218,-956,422,438,249,917,197,770,560,659,961,-729,-724,868,300,-37,261,272,-705,530,-104,567,97,988,-116,188,-728,-414,-320,861,-9,312,-805,-8,58,109,93,668,693,182,735,-594,-977,616,-374,-389,-688,951,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(int[],int):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("ARRAY:[I:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(int[],int):int[]",
            new int[]{721,935,-867,-599,-198,-780,-439,968,943,426,-393,-767,-276,-183,527,-918,-187,436,441,-772,-455,-590,698,-915,-281,505,-757,819,386,609,161,-266,-653,-493,74,-465,-401,-15,56,-462,-998,-919,356,410,-899,-872,377,-357,-803,-869,62,-327,530,962,-858,224,-262,895,-477,328,664,-934,-472,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(int[],int):int[]",
            new int[]{920,-981,-990,203,-912,-135,818,-165,370,414,833,-48,-991,-84,-699,104,-751,804,-939,901,-108,-883,963,520,194,104,-285,-494,220,-210,182,-409,732,-52,636,693,-822,618,488,-259,-589,-926,-366,-491,-594,992,688,-733,-589,442,119,-271,758,290,-425,503,817,-377,522,-944,518,-134,-235,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:4:4:NULL:29:java.lang.String:b2JqZWN0MA==:29:java.lang.String:b2JqZWN0NA==:29:java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(java.lang.Object[],int):java.lang.Object[]",
            new int[]{95,-489,770,-803,901,-845,199,-961,-335,-232,6,-395,951,534,-740,216,-559,-778,-676,535,897,-583,-137,-639,-921,841,506,-166,-931,-554,-508,-608,858,-451,-445,-825,-24,-101,756,-988,457,-690,19,590,-509,-792,-291,979,734,330,760,-730,455,658,-423,782,-559,-360,220,16,-122,-209,218,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(java.lang.Object[],int):java.lang.Object[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:29:java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(java.lang.Object[],int):java.lang.Object[]",
            new int[]{236,523,513,-738,94,157,975,884,-703,165,-195,-776,-24,202,-629,369,-415,-394,232,484,782,106,-479,-488,-778,756,-478,-530,-146,896,-114,823,-286,520,-124,746,96,-897,464,524,-458,-403,171,-159,-896,119,-891,-770,-996,-344,855,581,960,5,-484,-713,-746,146,-402,321,-127,-770,-818,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(java.lang.Object[],int):java.lang.Object[]",
            new int[]{342,808,-223,-789,-561,-497,126,-181,-352,-945,-761,703,-515,-648,320,-662,242,513,61,492,-785,91,-92,472,630,899,359,-420,250,919,-324,399,52,-649,266,890,-716,-532,501,-292,-279,-306,736,628,11,-965,-77,-474,612,-117,-663,962,-977,54,683,101,764,895,-884,-580,869,-663,-442,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("ARRAY:[J:4:31:java.lang.Long:LTIxNDc0ODM2NDg=:31:java.lang.Long:LTIxNDc0ODM2NDg=:43:java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=:19:java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(long[],int):long[]",
            new int[]{-7,-169,184,423,-893,761,-512,870,-954,809,-394,-25,685,591,-457,219,-196,-432,489,458,-622,-170,153,936,210,-42,-591,296,-490,-318,506,-596,-445,568,286,-770,875,818,830,-225,543,-595,-281,82,104,240,741,-237,-560,832,-755,113,413,-4,620,-71,-446,-432,-637,-597,231,-218,117,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(long[],int):long[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("ARRAY:[J:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(long[],int):long[]",
            new int[]{421,-724,-629,831,-876,705,-332,-523,-381,-827,-929,580,-467,911,-976,150,-380,136,-998,643,-689,529,-918,-589,-281,-314,751,-634,327,889,-172,281,123,-951,-420,-390,243,532,-954,-571,-659,-291,-577,-818,832,568,-68,260,926,-548,25,132,-59,-874,992,759,773,732,-884,297,-429,945,316,736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(long[],int):long[]",
            new int[]{-333,975,905,-194,-693,109,583,627,-908,-387,-930,-628,804,-873,-450,525,-378,712,122,281,645,819,349,-882,296,-204,-926,485,-444,-560,-915,-799,452,-340,369,-348,-433,130,700,-428,460,-21,47,261,852,24,-38,480,164,-191,446,925,589,-160,-905,-821,270,-238,298,-640,310,-31,174,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("ARRAY:[S:4:20:java.lang.Short:MA==:20:java.lang.Short:LTE=:20:java.lang.Short:MA==:20:java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(short[],int):short[]",
            new int[]{419,-924,-610,-285,96,-661,494,-143,993,-301,206,841,-881,-804,83,-468,881,80,-740,784,-217,-580,867,789,-877,651,-749,321,149,692,677,-720,702,-422,262,995,486,846,588,861,-179,331,623,-961,960,477,-619,-785,876,777,-893,675,-185,540,-337,-645,821,-161,-739,912,-24,176,747,-624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(short[],int):short[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("ARRAY:[S:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(short[],int):short[]",
            new int[]{-863,-521,-817,135,-744,849,-421,-292,106,416,-396,109,720,-725,-478,21,-905,-326,-636,-914,695,578,-92,-310,-918,-705,-565,-277,-961,-25,313,-601,-871,71,597,-763,725,158,-522,-572,280,932,-376,988,816,-369,252,337,-534,671,119,-773,233,-162,-490,-673,-877,43,793,-832,263,-655,130,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "remove(short[],int):short[]",
            new int[]{378,-300,-842,666,494,-912,-418,821,801,876,-279,391,-859,-399,-724,395,437,547,174,-913,-458,-122,-401,917,502,-431,-898,493,-836,-985,-572,681,611,-547,-211,1,-942,-322,-11,-375,-494,-87,12,422,-276,-839,462,-239,-75,-125,612,55,-102,360,706,417,-713,-19,-363,-804,200,925,160,671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("ARRAY:[Z:2:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(boolean[],boolean):boolean[]",
            new int[]{-15,529,400,-979,-148,724,663,-513,-414,-64,-902,923,-46,221,-826,506,619,262,189,850,568,-525,-318,838,116,563,397,-469,-102,543,379,521,725,-824,714,652,10,414,-376,380,-444,977,160,-808,-26,524,-438,519,702,0,-475,330,-321,672,53,227,-911,163,-629,-740,-738,-661,450,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("ARRAY:[Z:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(boolean[],boolean):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("ARRAY:[Z:3:26:java.lang.Boolean:dHJ1ZQ==:26:java.lang.Boolean:dHJ1ZQ==:26:java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(boolean[],boolean):boolean[]",
            new int[]{160,174,-568,-420,345,333,-189,1000,-1000,884,45,812,-38,-409,-923,-165,1000,-943,832,1000,271,-351,205,952,1000,296,-861,21,624,-796,1000,-921,-750,14,-151,-653,-528,-853,231,526,-284,1000,-876,428,-481,-132,-120,28,1000,503,630,599,969,1000,-371,289,-1000,-245,-465,417,-1000,-750,-767,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("ARRAY:[Z:2:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(boolean[],boolean):boolean[]",
            new int[]{524,-153,337,-560,197,-618,973,889,-300,-449,-605,658,996,-486,370,-532,442,275,38,296,117,-71,128,-838,651,932,-980,-405,-8,-282,-210,912,2,845,253,31,-361,-412,-730,845,-455,365,-919,398,324,-251,-733,-969,-462,-95,107,515,477,-715,-443,-272,-824,136,831,-86,775,978,693,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("ARRAY:[B:4:19:java.lang.Byte:LTE=:19:java.lang.Byte:MQ==:19:java.lang.Byte:MQ==:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(byte[],byte):byte[]",
            new int[]{-289,-380,-748,924,469,-35,369,-759,-899,236,-415,-797,787,671,-95,921,-337,-569,910,881,-431,216,914,612,335,-577,-963,-398,325,149,541,-872,788,-607,-865,-415,74,-14,971,788,-300,-42,542,717,-115,-659,841,-767,-758,-287,732,-616,47,-793,-218,111,729,-869,507,407,-942,-93,405,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("ARRAY:[B:5:19:java.lang.Byte:MA==:19:java.lang.Byte:NTg=:19:java.lang.Byte:MQ==:19:java.lang.Byte:LTE=:19:java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(byte[],byte):byte[]",
            new int[]{911,293,984,581,-302,909,-503,312,617,863,-419,-486,311,433,-173,144,-443,-913,464,883,333,162,-241,385,-22,112,404,-79,423,154,-996,-854,361,197,-833,-413,-349,-876,680,368,267,-342,-209,-340,646,341,707,-571,455,-529,71,127,427,242,413,167,-768,775,-654,257,120,-730,631,962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("ARRAY:[B:2:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(byte[],byte):byte[]",
            new int[]{873,-436,989,-10,137,-286,564,-566,600,-393,-524,29,-556,647,-802,-482,851,-418,-263,703,711,457,-401,-179,337,-599,818,693,-524,306,394,631,679,-437,918,946,617,146,-910,829,881,-455,-576,337,-622,315,-517,314,-414,348,659,130,584,345,763,348,54,448,-584,621,-751,493,631,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:AA==:24:java.lang.Character:YQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(char[],char):char[]",
            new int[]{585,768,-1000,353,-1000,-248,-297,-282,1000,1000,655,-94,-587,482,1000,-724,-311,-356,-1000,-1000,-769,841,-561,282,1000,-437,-473,-1000,-368,204,294,1000,968,473,-1000,10,707,-1000,-1000,-245,1000,873,1000,968,729,79,-600,729,1000,276,-1000,-701,20,-1,-516,273,-633,-485,-779,51,13,-655,-768,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("ARRAY:[C:5:24:java.lang.Character:EA==:24:java.lang.Character:eQ==:24:java.lang.Character:PQ==:24:java.lang.Character:eQ==:24:java.lang.Character:PA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(char[],char):char[]",
            new int[]{-751,-752,505,-963,-391,-68,402,-927,-67,384,790,730,-947,-228,320,366,674,-799,857,632,-866,-105,-67,-182,66,-179,-95,-323,110,-115,88,-650,-720,644,316,760,-906,792,-269,300,-817,-378,920,497,714,-897,973,-82,675,-732,-486,-431,838,-812,-715,-265,-959,983,-479,349,347,21,-36,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:GA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(char[],char):char[]",
            new int[]{-1000,-1000,1000,1000,1000,1000,274,-859,646,-169,-982,1000,-69,361,-260,-990,978,-552,1000,1000,-1000,-846,-1000,-784,111,-943,75,-459,-593,366,-1000,234,-240,-311,565,-255,-296,233,-975,-1000,-348,1000,464,1000,-504,428,-630,-79,1000,1000,942,-1000,931,52,348,657,-561,-168,-1000,173,952,-1000,327,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("ARRAY:[D:4:37:java.lang.Double:Mi4xNDc0ODM2NDdFOQ==:21:java.lang.Double:MC4w:25:java.lang.Double:LTY5Mi4w:37:java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(double[],double):double[]",
            new int[]{269,-212,-873,574,732,-664,-499,-692,923,-227,-393,903,-535,-714,7,-707,-772,3,-585,539,-274,-927,-844,-549,852,488,-95,-269,-160,-654,318,348,740,-644,-334,-620,-156,847,947,-598,-536,249,-971,-345,273,-892,378,-322,-979,-955,651,871,-155,746,13,547,-366,88,-568,-577,926,784,390,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(double[],double):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("ARRAY:[D:1:29:java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(double[],double):double[]",
            new int[]{-1000,291,-771,30,856,340,-884,96,-1000,426,-99,-306,145,-251,40,-1000,-1000,-724,-313,513,-810,166,-198,897,-249,683,-332,-1000,225,766,1000,-563,-274,-1000,236,315,548,1000,-240,1000,-507,1000,1000,371,189,-832,558,654,-632,-307,-490,-712,-1000,-740,162,428,-378,-780,253,-1000,213,181,-671,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("ARRAY:[D:2:37:java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=:45:java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(double[],double):double[]",
            new int[]{626,750,-596,-74,773,-591,-400,667,-832,-455,-66,-803,-177,-118,-54,637,753,-468,352,279,439,-922,-372,877,-919,-366,667,-867,482,-272,-468,762,716,-21,-162,-983,940,971,-375,-574,-338,459,690,-405,575,122,-662,-533,-324,-574,235,113,-200,846,-364,303,-77,707,632,-552,640,-827,-714,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("ARRAY:[F:2:24:java.lang.Float:LTEuMA==:24:java.lang.Float:LTg0Mi4w", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(float[],float):float[]",
            new int[]{-363,-368,-322,-1000,352,-842,647,928,1000,-428,108,57,-1000,-335,-311,518,-1000,520,512,257,186,424,-96,490,468,-553,-834,-112,57,-823,-1000,-797,-947,-409,398,-171,50,-479,362,118,-157,-890,-401,-541,-617,-327,1000,374,-895,-524,568,-227,-866,-829,-459,1000,134,-297,-252,277,398,563,-48,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("ARRAY:[F:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(float[],float):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("ARRAY:[F:2:20:java.lang.Float:MS4w:28:java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(float[],float):float[]",
            new int[]{-99,1000,373,680,-1000,630,1000,581,184,227,837,691,-83,476,1000,49,-568,985,359,551,-182,-1000,-141,-575,-1000,943,-492,-995,-691,-407,-194,895,1000,380,266,-1000,-485,-663,1000,182,1000,-599,1000,981,-627,-14,618,723,-339,-493,-973,-259,-95,694,516,767,814,-638,-1000,-162,796,-901,777,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("ARRAY:[F:2:20:java.lang.Float:TmFO:36:java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(float[],float):float[]",
            new int[]{-448,-608,895,-488,616,-112,-901,276,882,227,-421,691,914,281,669,-597,-749,-550,736,551,145,-295,-736,-827,-876,943,300,231,-897,69,-409,895,763,-944,-434,603,11,-343,139,-148,474,-599,272,694,184,-941,-801,-567,875,-180,-730,-794,-345,-861,482,767,193,-638,-59,70,25,-901,-268,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("ARRAY:[I:4:34:java.lang.Integer:LTIxNDc0ODM2NDg=:34:java.lang.Integer:LTIxNDc0ODM2NDg=:34:java.lang.Integer:LTIxNDc0ODM2NDg=:34:java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(int[],int):int[]",
            new int[]{809,-124,-308,-667,808,642,65,-806,-963,-888,-915,-914,74,-267,446,808,-920,-538,-739,-965,-662,-980,149,-259,-17,-287,-479,-225,-478,19,-398,706,843,-696,-477,-900,-647,-527,-591,-215,-309,-851,824,303,-33,-932,-864,-216,-66,-243,437,-249,631,-709,-523,-1,100,-44,-244,-368,561,-43,397,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("ARRAY:[I:2:22:java.lang.Integer:MQ==:22:java.lang.Integer:MzA=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(int[],int):int[]",
            new int[]{-904,-8,697,308,310,453,369,-11,-275,-701,-582,-832,712,-763,281,-762,-328,-264,454,-592,-153,629,138,-312,-308,-433,629,649,-992,-567,-851,-538,-772,-16,595,-191,-852,429,304,600,184,456,341,-868,-210,276,-965,302,78,-308,-456,810,854,-136,80,-105,305,-481,86,84,407,-347,-867,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("ARRAY:[I:2:22:java.lang.Integer:LTE=:22:java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(int[],int):int[]",
            new int[]{891,318,-862,984,-546,229,513,709,-855,627,-931,-143,505,-759,-128,-633,-70,215,274,-875,-877,341,-110,395,908,-928,730,297,-671,616,872,-974,-922,547,-158,587,261,-142,-517,-833,-747,131,508,-20,973,593,153,-638,303,-754,396,266,545,-60,-944,-138,871,-688,-238,-70,470,-167,-757,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:4:29:java.lang.String:b2JqZWN0Mw==:29:java.lang.String:b2JqZWN0NA==:29:java.lang.String:b2JqZWN0NA==:29:java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(java.lang.Object[],java.lang.Object):java.lang.Object[]",
            new int[]{-55,896,-247,-293,450,-370,114,461,539,-374,600,734,-745,-84,347,441,-11,664,-867,375,257,-460,952,-235,-570,55,-927,157,-372,551,-417,562,-390,801,-576,854,-426,-504,649,-475,498,311,823,61,895,681,275,-539,662,-69,263,692,98,-5,6,270,92,604,-965,542,987,-486,312,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:2:29:java.lang.String:b2JqZWN0MQ==:29:java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(java.lang.Object[],java.lang.Object):java.lang.Object[]",
            new int[]{-1000,415,911,5,1000,987,-49,845,134,-1000,-1000,303,-710,-782,476,-1000,624,387,651,-747,111,-436,-451,57,-990,143,459,-81,1000,-1000,998,830,623,985,251,-1000,-119,363,-350,715,452,242,-780,1000,111,55,1000,954,-340,817,-693,-761,-616,-574,798,-23,-631,-918,-267,-23,62,965,-1000,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:1:29:java.lang.String:b2JqZWN0MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(java.lang.Object[],java.lang.Object):java.lang.Object[]",
            new int[]{266,-623,-1000,0,0,0,-315,-1000,-1000,-389,-457,0,819,270,0,-194,782,1000,-938,-274,-1000,0,913,1000,3,-683,1000,0,57,469,649,-902,230,-213,296,0,637,456,887,-164,-29,-285,803,820,-269,1000,-314,0,0,-801,9,0,237,1000,-1000,-195,-1000,-1000,303,605,-761,-297,41,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:3:29:java.lang.String:b2JqZWN0MQ==:4:NULL:29:java.lang.String:b2JqZWN0MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(java.lang.Object[],java.lang.Object):java.lang.Object[]",
            new int[]{-663,377,286,-282,284,-709,844,197,-941,824,-139,497,714,-629,764,-370,402,954,888,208,603,849,-509,79,-746,906,-589,-569,858,583,-878,634,-822,-43,-929,103,262,-683,-205,606,238,313,-434,599,-440,128,91,405,209,626,125,-630,722,-978,787,-532,-129,-745,816,-735,-689,-32,-952,-363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("ARRAY:[J:4:31:java.lang.Long:MjE0NzQ4MzY0Nw==:43:java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=:31:java.lang.Long:MjE0NzQ4MzY0Nw==:43:java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(long[],long):long[]",
            new int[]{233,-110,579,-479,265,-785,-183,-1000,-789,-865,42,530,289,104,1000,-34,-1000,214,583,387,399,-1000,964,-1000,-1000,758,60,-604,-1000,-272,589,-127,1000,866,-1000,-366,-274,-90,566,-829,-825,-1000,-134,547,910,-923,910,-299,-1000,-162,-109,637,422,318,459,-421,90,-136,82,-1000,-622,174,-366,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("ARRAY:[J:2:43:java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==:43:java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(long[],long):long[]",
            new int[]{-874,335,-367,930,521,823,931,2,88,-239,843,200,-898,-505,-320,202,-767,940,291,73,-703,-197,-147,310,760,-329,559,137,-822,-439,329,232,-971,-297,-82,-475,121,572,-395,213,-850,470,-961,-281,-135,340,-270,980,-222,888,-795,-861,380,613,895,-517,-559,-146,449,305,102,-384,-634,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("ARRAY:[J:1:31:java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(long[],long):long[]",
            new int[]{944,-892,1000,-197,333,80,-762,-190,-757,-419,-898,-576,-342,145,1000,344,-952,-837,407,-227,-470,-323,-16,998,612,-597,770,-1000,1000,985,-680,-177,732,1000,512,405,1000,-411,327,-478,1000,284,801,-65,189,-153,104,-321,-276,449,913,194,695,-407,-817,252,644,-1000,-803,780,255,320,207,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("ARRAY:[S:4:20:java.lang.Short:LTE=:20:java.lang.Short:LTE=:20:java.lang.Short:LTE=:20:java.lang.Short:MjI0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(short[],short):short[]",
            new int[]{311,406,-43,379,-857,317,-823,-699,662,224,-553,-509,333,767,585,823,535,285,-167,789,701,-726,-743,551,32,-335,-760,34,544,895,-185,-332,-160,825,-220,-627,338,-40,586,-327,898,130,-142,-541,-191,-160,44,-139,914,-200,-794,-105,-732,-162,-786,-843,-791,319,-253,-327,-56,-495,396,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("ARRAY:[S:1:20:java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(short[],short):short[]",
            new int[]{-149,853,-104,-234,554,-835,-930,92,-426,-292,398,534,-193,-56,314,774,-554,-630,297,-882,-900,-920,-392,349,-236,-835,154,475,119,817,353,-643,158,-916,97,225,556,-335,986,373,-626,794,816,-27,-293,-685,-995,-159,-278,404,100,270,134,996,-206,-425,-349,-5,24,874,152,-429,569,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("ARRAY:[S:2:20:java.lang.Short:LTE=:20:java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "removeElement(short[],short):short[]",
            new int[]{357,-494,-598,-883,-969,-663,787,-337,-560,-213,-438,782,-665,-973,-279,735,-626,670,593,449,-209,-591,-306,-96,273,-994,272,905,-534,960,-624,-333,-968,-764,-526,666,-378,-140,723,481,294,-105,-171,996,920,324,-899,-757,958,-274,-212,441,-408,260,-928,-703,-310,448,206,548,-706,527,182,891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(boolean[]):void",
            new int[]{328,-850,-527,-824,-706,207,-393,-166,384,-572,-612,-353,-973,428,-592,546,-497,-252,336,443,-140,458,386,-940,-472,-808,188,-493,357,-432,824,691,-666,-214,488,879,-410,327,751,243,-510,-935,700,51,-461,-101,405,337,-323,-974,65,326,-895,372,-23,678,-800,369,-961,-300,-658,263,-141,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(byte[]):void",
            new int[]{251,28,340,511,902,-318,-605,805,-395,-593,573,-817,-921,162,667,-764,906,593,-17,273,-432,390,-452,16,-935,671,209,-634,703,683,-637,-994,-425,319,-424,586,-132,222,462,-697,-763,-424,820,-225,180,73,298,-695,-399,-994,585,-150,561,944,-493,326,591,43,-698,218,892,-326,826,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(char[]):void",
            new int[]{503,-5,-431,-292,-251,-507,670,-18,7,-457,416,728,543,-512,572,453,308,-100,273,969,670,-494,-116,315,635,-292,-99,-473,-289,234,-778,508,-270,817,211,739,-491,-31,433,919,-951,877,678,-274,-887,260,-68,-139,-476,616,340,-709,-759,-290,895,-817,-576,-614,-461,-621,-955,805,-274,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(double[]):void",
            new int[]{916,439,892,990,-579,-144,-923,986,105,499,892,642,113,229,54,179,153,-860,-31,-279,-249,-859,539,254,827,-569,577,-534,536,947,155,938,-71,613,-297,49,854,723,500,634,-234,179,304,975,-395,457,301,1,-227,144,-701,716,-629,-307,149,-168,313,638,-244,320,739,949,-306,-805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(float[]):void",
            new int[]{548,-164,998,347,-714,873,399,996,-738,-255,846,-584,927,-167,867,274,243,-902,94,342,584,-790,662,324,-125,-367,-284,764,239,-249,202,274,568,-50,177,320,-114,-863,-760,529,-824,-398,364,79,-602,388,409,101,-158,-677,905,-781,-325,-237,298,-622,561,753,-197,-77,-428,766,-761,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(int[]):void",
            new int[]{-700,-626,880,754,-688,-775,-280,456,128,-676,229,831,952,-535,247,-501,-377,-927,400,28,793,70,-919,-50,-955,-249,-391,-822,351,-199,-19,603,-254,-40,-820,-585,118,377,-966,421,620,-804,-725,-532,-635,-427,-390,-10,-226,962,389,640,-739,-735,751,971,738,-773,38,-752,-116,330,654,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(java.lang.Object[]):void",
            new int[]{-830,458,456,-254,-495,161,-459,921,-408,-386,826,309,-680,464,34,-607,382,-986,889,-495,369,155,-253,-143,-655,502,-183,64,-26,860,-923,-275,-448,-619,888,850,-670,419,80,-626,-556,-64,-215,-148,427,559,-334,498,815,-101,354,205,662,588,-120,897,923,-367,-322,-354,618,153,354,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(long[]):void",
            new int[]{-176,911,-237,647,100,167,-291,401,-3,481,390,-224,758,-546,-47,273,-442,-638,-474,-730,732,572,-703,-604,891,-194,461,838,920,945,-521,-226,133,-385,-522,64,987,-315,-78,-361,644,-31,-251,-433,415,922,-960,730,-337,-60,-3,212,-913,-31,-491,-938,-522,-409,915,898,15,-537,-299,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "reverse(short[]):void",
            new int[]{695,-131,626,-100,349,-628,-718,539,328,138,732,-793,-737,93,476,-283,-648,-901,-293,-836,-887,-550,214,881,418,-265,76,-230,281,802,797,217,897,750,-889,-221,-18,165,-269,-116,-395,-866,-987,-108,-624,-34,-291,-968,-883,-1,-237,1,954,-593,-529,244,-860,-534,-797,374,-125,-565,378,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("ARRAY:[Z:5:26:java.lang.Boolean:dHJ1ZQ==:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:ZmFsc2U=:26:java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(boolean[],int,int):boolean[]",
            new int[]{401,-542,835,-563,209,-1000,-717,-733,587,958,482,1000,481,452,-771,953,778,12,562,-290,-1000,-232,1000,-197,952,-734,239,-489,460,-1000,-1000,157,-139,648,847,9,337,179,911,141,954,253,-399,308,796,1000,-666,1000,1000,-637,172,80,325,-55,645,476,410,80,1000,569,484,-224,-1000,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("ARRAY:[Z:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(boolean[],int,int):boolean[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("ARRAY:[B:3:19:java.lang.Byte:LTE=:19:java.lang.Byte:LTE=:19:java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(byte[],int,int):byte[]",
            new int[]{705,377,458,-340,677,91,-299,575,628,350,-614,-559,961,-267,495,-644,-332,487,-922,45,-420,51,-314,362,-587,-834,-14,170,724,245,699,777,184,952,-44,224,-357,-472,885,283,-653,303,-224,-228,344,45,-461,-58,658,327,-683,137,115,-78,117,-384,830,394,98,563,337,-488,-247,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("ARRAY:[B:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(byte[],int,int):byte[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:cg==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(char[],int,int):char[]",
            new int[]{-406,498,0,-1000,610,879,-964,-222,-256,-465,-326,-857,34,1000,-321,631,273,0,-143,-751,-1000,-1000,727,1000,103,-340,75,-1000,-104,-484,-132,0,523,-1000,730,-279,-605,-499,-270,693,-746,-671,-336,-210,-1000,-422,-1000,0,754,-1000,-978,-869,0,114,0,336,0,754,-267,705,851,-19,-1000,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(char[],int,int):char[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:25:java.lang.Double:LTk1Ny4w:25:java.lang.Double:LTEuMA==:25:java.lang.Double:LTEyLjI=", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(double[],int,int):double[]",
            new int[]{939,-957,-973,-83,-430,-122,-134,-684,82,694,-573,218,316,204,-412,228,-298,971,10,-247,536,-428,-370,-160,339,-99,908,-840,372,-98,452,-620,-196,829,921,-998,-438,-544,-617,83,473,-424,672,240,943,-395,722,-194,128,134,321,-907,-678,861,298,-678,-891,504,746,-998,725,20,-402,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(double[],int,int):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("ARRAY:[F:2:24:java.lang.Float:LTQ3MC4w:36:java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(float[],int,int):float[]",
            new int[]{-724,-470,515,1000,1000,375,-260,-1000,-304,-574,-1000,178,92,-198,-659,341,-1000,-88,-1000,-1000,678,-1000,413,346,344,345,-702,-637,-405,246,422,-1000,-679,376,-54,322,-1000,1000,-180,-730,1000,716,-835,134,932,-440,707,979,-88,-862,-873,486,-257,1000,-501,-521,-747,-873,-1000,1000,-764,-715,1000,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("ARRAY:[F:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(float[],int,int):float[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("ARRAY:[I:3:22:java.lang.Integer:MA==:22:java.lang.Integer:LTE=:26:java.lang.Integer:LTMxMA==", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(int[],int,int):int[]",
            new int[]{-63,266,36,163,221,-310,-901,-302,988,1000,-277,177,346,493,82,1,698,274,22,1000,-1000,-275,-1000,-822,475,69,793,197,1000,143,1000,1000,118,497,-274,247,1000,-824,478,130,25,656,-1000,979,741,504,691,-348,-346,-609,-7,-1000,-798,-1000,528,-574,269,-185,-971,337,553,-215,-572,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("ARRAY:[I:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(int[],int,int):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:4:29:java.lang.String:b2JqZWN0MQ==:29:java.lang.String:b2JqZWN0NA==:4:NULL:4:NULL", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(java.lang.Object[],int,int):java.lang.Object[]",
            new int[]{1000,1000,-344,1000,114,636,-243,-927,1000,-277,-93,121,-1000,1000,-1000,-527,606,1000,-593,-1000,-250,151,430,-1000,-118,731,-360,155,51,284,375,-1000,-474,-878,604,-1000,-540,823,-1000,-1000,-1000,692,42,-576,-526,-89,-684,67,1000,-572,-100,-461,211,-228,-416,778,105,211,-1000,541,28,257,-854,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.lang.Object;:0", DEReplay.run(
            "org.apache.commons.lang3.ArrayUtils", "org.apache.commons.lang3.ArrayUtils", "subarray(java.lang.Object[],int,int):java.lang.Object[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
