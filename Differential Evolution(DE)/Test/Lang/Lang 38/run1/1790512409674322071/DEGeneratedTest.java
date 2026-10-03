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
            new int[]{559,875,12,279,-325,-883,-13,-464,-920,60,-882,-479,-694,394,848,755,28,-597,185,-595,250,992,683,201,543,-218,-175,863,-591,-252,502,-301,-496,-172,84,364,-74,146,191,432,68,-907,-225,-594,655,-684,-689,-960,464,-79,-956,449,-1000,622,-946,392,-100,-576,-348,-682,-776,512,-906,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "equals(java.lang.Object):boolean",
            new int[]{841,-917,-799,-477,-802,63,884,768,-344,-378,355,794,711,-258,121,450,913,-899,-315,-276,687,-34,-531,351,667,751,521,685,935,-464,999,314,-799,-216,231,219,454,721,799,957,-112,-593,534,271,-963,-590,-433,846,-67,146,670,491,-131,-527,-21,934,-148,-85,-90,-362,-779,11,584,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{674,173,871,79,358,-388,-185,-71,278,-92,-66,-32,-614,-379,393,-449,232,-587,-191,431,-307,-700,-575,-24,-881,-641,575,286,-697,-53,220,-49,-354,-266,-968,-513,907,725,322,-606,994,846,975,-823,-587,632,-539,709,187,-903,991,-130,-675,968,-72,501,-756,-903,683,442,971,124,488,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{447,-518,591,60,237,123,-856,-625,538,-848,-196,-633,-795,27,-505,657,75,800,-947,-767,539,633,-360,-631,-524,-590,932,-131,509,565,-600,-570,-475,-506,-262,-756,-600,786,299,828,960,908,253,483,-646,948,101,850,-798,563,190,517,177,-639,-118,825,987,-389,-819,670,117,406,777,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar):java.lang.String",
            new int[]{180,746,-208,-156,877,-753,79,-522,627,-93,266,-271,301,512,57,-492,693,928,-991,529,369,-926,331,-942,135,-665,-705,169,619,541,-150,214,-573,-327,-156,-600,605,-548,-519,499,-283,615,-887,-422,777,-705,-722,313,449,226,25,-985,485,-324,-32,750,-804,-1,308,132,896,482,-945,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar):java.lang.String",
            new int[]{-930,688,957,-931,-744,653,-756,319,-163,104,269,110,518,797,-216,-973,-194,324,125,-557,962,885,711,605,-30,910,200,540,-185,946,701,-211,-661,319,-742,182,-772,991,52,-10,258,-222,220,650,-171,-385,-962,-46,35,932,289,117,41,-651,-890,444,-617,-116,-677,-529,928,663,-658,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-248,404,-125,836,606,240,-556,388,190,743,-637,-888,223,-103,-847,462,815,809,605,410,-910,-95,-33,-578,-728,-54,-608,777,375,495,-702,491,147,287,26,310,-690,-949,-346,-245,257,793,-828,51,-612,272,356,-76,25,-536,-388,689,-516,-822,835,-136,-747,670,-104,607,-497,-896,753,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{463,-923,228,-253,47,-228,711,392,-778,-327,61,-205,888,-548,531,-556,890,580,-163,680,-172,122,544,753,920,852,-174,-834,-111,939,618,650,377,211,881,317,760,194,-824,92,-614,470,110,-668,-762,721,324,707,-162,-167,639,879,-222,816,759,291,-304,989,-532,-544,766,852,-318,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:VGh1LCAzMSBKdWwgMTk2OSAwMDowMDowMCArMDAwMA==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{150,461,360,-154,456,298,-531,201,-164,-123,228,539,913,-178,264,-376,114,-639,656,474,-234,872,-849,-398,-42,-398,585,252,934,-751,360,-951,749,953,665,-634,-932,632,-51,142,49,-395,122,-100,-471,-75,901,257,-37,-998,805,-621,887,-302,-609,-526,-898,330,-488,-273,-792,-203,-404,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:MDgvMzAvNjcgMDA6MDA6MDA=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-283,-656,62,-855,-62,610,190,-875,-304,469,45,328,-252,504,-437,-830,-562,-750,517,-590,170,863,-22,945,-337,373,-956,-409,855,334,-172,-643,-661,543,-508,673,-700,-105,985,-589,59,-581,67,576,-456,84,-825,-905,-748,30,756,260,-356,-430,-223,72,-764,513,-674,988,-814,-865,427,-618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:U2F0LCA3IE1hciAxOTcwIDAwOjAwOjAwICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{672,602,-218,65,716,-483,713,-621,-630,647,-755,337,105,-112,-606,-542,203,-809,965,574,-133,-389,-488,-563,-893,337,-370,280,-582,665,-307,-18,-767,694,-43,-604,259,-59,810,824,-73,-940,-124,-364,-609,347,-355,578,784,-153,-607,-531,-233,-491,223,630,-198,71,248,-292,-975,134,200,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{898,-757,317,533,121,-944,-227,-39,-801,959,-506,899,313,-526,544,-321,280,-124,-856,-467,153,-961,-993,766,-246,-240,-27,-799,122,877,485,-678,-215,-389,-701,-279,56,-547,-995,-390,160,656,441,-23,852,951,878,-295,-544,-571,-26,-546,-265,719,-68,461,856,-437,36,-496,881,-328,805,-730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{410,-383,-599,417,-743,426,643,-108,689,583,156,453,204,868,835,131,810,239,-283,991,-591,-992,705,-910,838,745,-551,-250,-946,-987,948,798,923,136,-707,708,346,-899,-809,663,-963,460,330,-599,-823,-409,547,-554,378,79,-206,812,909,104,379,-397,987,730,-305,52,74,-238,-312,-502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-382,35,-377,-298,-658,94,-427,-978,-853,-228,-957,-593,-151,491,-805,-750,551,395,162,440,-771,-475,378,796,-416,-666,-967,-497,-149,323,-618,896,-808,669,-452,227,-389,667,-398,-913,-117,510,-776,69,911,468,-966,-52,-863,913,823,146,136,765,-675,599,401,931,938,261,-574,109,827,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.String:V2VkLCAzMSBEZWMgMTk2OSAyMzo1OTo1OSArMDAwMA==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{895,-820,-895,-656,934,781,5,998,370,-406,953,393,-410,284,162,-277,-725,-770,645,-964,-867,66,-249,-129,-138,883,252,604,-786,-891,-13,797,693,630,-300,658,-472,-661,891,617,-694,-52,-736,983,-66,676,664,664,-571,-831,-744,-990,465,931,181,-36,-444,411,866,963,302,923,790,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:MTIvMzEvNjkgMjM6NTk6NTk=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{-486,-659,502,-234,-562,67,-849,326,-350,-254,-145,350,492,464,773,-837,-689,-278,-817,840,671,776,-813,738,-524,843,153,-160,635,695,-880,-858,-515,652,15,180,359,625,-265,-336,383,-453,-121,153,-99,214,-743,721,-252,-563,133,-363,999,172,665,-765,824,311,943,-649,564,-258,622,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.String:VGh1LCAxIEphbiAxOTcwIDAwOjAwOjAwICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{255,830,378,558,-805,432,-268,-615,-203,164,329,347,145,58,-413,188,-836,316,-670,32,-554,-63,-787,-785,367,-566,-774,-803,300,539,162,-290,278,333,-456,153,-340,859,738,86,-954,-56,669,-307,977,685,940,36,812,-130,-30,58,575,-545,255,406,-476,142,820,-653,750,91,520,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-147,833,-577,-531,764,536,812,628,-486,394,-757,-131,426,-930,686,-42,829,-547,846,-935,641,336,113,966,-788,125,94,581,109,113,266,102,423,-297,822,-156,867,-784,126,6,446,-580,380,-979,815,771,-932,876,540,-505,535,-150,476,109,-323,499,191,759,107,-609,-46,823,417,-926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{356,-362,637,900,-682,-588,-191,112,197,-248,996,168,-672,292,-961,142,517,-996,-763,401,337,-35,367,-964,562,813,-33,558,-986,-977,-958,-208,62,-959,875,677,-365,-490,-984,-170,-813,-631,652,140,318,-568,-454,417,931,202,-448,-825,735,-855,-720,-15,649,-583,916,536,-719,-856,-555,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-280,272,-402,743,453,607,-330,951,-820,21,-976,-572,-474,-242,987,-174,-207,791,-97,911,908,412,761,-864,689,48,-928,-945,-183,-5,-782,47,765,-341,-973,650,657,-879,682,-98,514,-648,-739,532,691,250,145,-422,-585,-172,-648,81,319,332,457,851,-409,-163,147,4,-60,1000,-272,-47}));
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
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{37,418,295,1000,-90,26,530,-578,-182,-262,706,-1000,1000,-411,-707,-1000,-299,238,133,225,-287,-281,112,-66,-602,165,-18,643,-201,-60,371,-74,-645,-172,-1000,537,101,160,-432,129,1000,768,-155,410,-286,-570,-851,-91,-662,-361,-95,58,6,1000,-362,-553,1000,56,-406,-785,-849,1000,72,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{412,-5,-205,52,-165,878,-443,397,291,-763,426,-669,445,-415,-821,-794,534,662,-736,594,546,-6,585,533,-844,-646,-741,507,60,181,338,264,22,750,405,-164,-300,-801,-991,-987,101,402,-43,887,379,115,-357,89,626,824,-217,846,275,283,197,-901,793,-134,-373,584,451,-451,935,975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{39,514,-549,-610,-80,-389,478,-104,-749,544,-400,302,51,-865,-1000,110,-199,128,-571,-433,-966,-501,-146,535,1000,400,113,32,-572,-154,511,10,1000,-492,-631,-400,-404,-118,-267,162,373,400,-1000,-1000,20,-669,-69,66,372,-55,-791,111,289,-206,-205,357,-334,-1000,-697,-909,847,944,343,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{23,118,-400,-100,1000,1000,1000,245,-594,305,137,-99,411,1000,364,-131,-460,-474,-745,505,613,761,1000,1000,135,37,119,81,146,491,-490,-400,-246,-941,-936,-1000,1000,-88,702,455,-351,-360,734,-296,588,1000,750,-767,-218,194,-829,492,-250,-1000,0,793,1000,-400,-305,14,1000,-192,91,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-84,-293,1000,-227,1000,-876,193,488,-1000,1000,-598,-1000,1000,-387,-1000,1000,-751,1000,-297,858,1000,865,-1000,-408,799,-891,746,366,-1000,-219,-72,-464,819,-57,-627,1000,-1000,654,1000,457,969,120,-21,15,-494,361,-1000,-349,1000,539,-1000,-1000,484,-1000,-796,381,-673,-254,501,336,468,-1000,-252,24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{749,-402,-324,241,-191,837,144,-577,697,313,687,-815,677,626,282,-698,-307,389,476,-848,994,-701,-890,-150,456,-133,-843,626,-943,94,-215,446,-263,-600,-294,354,-523,-475,-363,770,-138,749,-739,-905,613,-163,470,350,596,-643,859,-702,-289,683,-531,523,689,917,573,-718,323,-814,950,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-400,-432,-600,805,-1000,1000,-385,-348,-254,1000,-99,685,1000,1000,-1000,66,916,631,-116,-45,1000,368,-343,-625,-365,-1000,1000,-1000,-194,-599,-1000,1000,720,-1000,-513,-1000,1000,1000,1000,820,-697,1000,-88,-1000,1000,-202,-745,-879,291,-430,-32,1000,848,-327,-689,1000,321,-417,-255,443,-253,693,-1000,306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getDateTimeInstance(int,int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{265,403,134,973,737,-220,635,792,-572,377,134,734,-630,-357,471,665,993,172,1000,182,11,-146,-147,-34,-128,367,-379,198,-377,958,-573,-9,1000,-463,254,-66,-105,301,337,-163,289,446,771,-176,861,-899,-799,979,832,555,-950,-223,281,880,751,-1000,714,410,-55,423,-8,-757,-340,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance():org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{991,-108,648,-22,-1000,-548,892,396,603,-844,-199,882,-165,1000,512,607,-476,-1000,173,1000,-708,-914,615,689,23,-51,-791,-685,1000,-125,-747,786,-155,-593,-428,-944,607,497,403,-436,-20,466,66,-291,1000,-397,-351,608,865,1000,917,1000,71,1000,-640,-332,342,1000,31,-1000,439,810,-641,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-179,-146,821,330,1000,610,64,1000,-1000,654,-1000,-1000,1000,-546,-636,-67,43,-298,-572,-1000,696,444,1000,-754,-1000,871,-1000,1000,-543,-155,985,655,1000,-637,490,-1000,-736,-1000,249,-601,-736,-256,-185,743,-1000,578,-1000,609,-1000,-805,-513,1000,-772,1000,-1000,1000,743,574,617,-381,1000,-576,1000,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-899,206,999,-122,1000,514,751,981,-1000,758,-491,-1000,293,-682,-180,-347,173,189,-69,-1000,0,510,1000,-828,-969,1000,-483,1000,-880,198,989,880,1000,-608,131,-1000,-378,-891,-344,-418,-550,-948,58,709,-1000,-1000,-544,544,-1000,-863,-659,-868,-681,1000,-1000,1000,220,541,732,-869,640,-703,1000,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{495,-294,258,248,756,256,925,1000,377,459,-439,-608,1000,58,422,1000,-1000,-1000,-1000,-1000,891,349,1000,1000,-832,-336,-1000,1000,1000,-1000,-20,711,1000,-746,-291,-1000,-277,-373,534,-1000,-1000,59,-640,-564,-501,-1000,-1000,1000,-1000,-721,211,1000,-617,939,-1000,988,-552,1000,124,-1000,974,401,1000,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-260,846,239,-219,497,-154,27,466,871,-354,-207,-691,880,-951,464,-230,934,983,373,363,-518,369,-868,-963,-247,-106,-240,-384,-926,-444,737,423,-577,800,603,-745,-949,-405,-308,398,284,928,649,542,-43,-195,-516,-893,304,-896,88,775,-397,407,-605,-196,-623,242,-371,-885,542,-852,483,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-275,215,1000,-637,-533,-237,581,-1000,-743,-1000,-410,824,-983,-165,2,-1000,1000,99,1000,-1000,-492,-1000,1000,-1000,485,-993,-567,-1000,1000,1000,623,727,613,450,1000,457,-1000,-51,123,1000,-1000,167,1000,-18,1000,583,-621,60,1000,1000,52,-1000,357,-256,387,-711,-574,-746,499,-908,-769,623,1000,-571}));
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
            new int[]{-545,845,1000,-769,-1000,-458,717,997,156,-468,784,255,-895,-163,512,838,-1000,-1000,392,-400,-8,-1000,1000,86,-222,1000,-1000,-347,-1000,326,1000,-557,932,-689,-942,-733,649,-162,-103,-103,-1000,236,586,-688,1000,-1000,1,936,-535,156,-63,1000,-298,226,-1000,691,-196,1000,1000,-1000,298,252,386,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{61,-897,44,-760,-1000,-1000,1000,-1000,-65,-1000,297,1000,-1000,1000,-357,952,-504,-518,864,1000,-1000,-1000,711,689,36,-1000,-351,-1000,1000,1000,-929,1000,-1000,-1000,195,-227,607,1000,700,94,1000,-78,960,-1000,1000,583,649,681,1000,-1000,1000,1000,763,1000,387,919,-1000,201,31,170,243,944,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-179,-878,573,293,-1000,-1000,420,1000,1000,-1000,54,1000,118,1000,-483,-47,-325,-996,549,1000,-1000,-915,190,689,-1000,-524,74,-1000,554,-155,-976,223,-834,-719,-281,-184,694,1000,292,-285,-1000,560,-36,-652,-1000,578,-30,380,1000,-1000,-264,1000,960,1000,-1000,-1000,1000,734,-407,-1000,533,1000,-1000,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-545,270,368,424,742,-1000,-324,-445,15,-371,-1000,255,-597,-532,-784,-954,1000,1000,-1000,1000,-420,-1000,-792,-1000,-264,-654,1000,-944,-1000,655,1000,-557,-745,1000,871,1000,-1000,-197,525,702,1000,-666,469,290,976,736,428,-1000,1000,-1000,-502,-1000,460,-428,1000,27,-333,-993,279,1000,-18,-12,198,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-100,-121,300,-233,705,968,-377,-221,957,774,990,-337,300,-946,672,-965,-808,-346,270,599,-551,-239,-374,209,-574,488,-442,-456,-625,83,658,282,794,-280,695,647,436,-297,-949,-697,-951,702,54,-300,896,623,63,181,628,671,-238,-554,-812,206,-816,-743,69,-859,741,726,-506,761,751,903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{664,-226,556,-572,-1000,1000,-145,-1000,1000,1000,-79,-623,420,-448,28,799,-952,1000,-1000,-211,833,-1000,-1000,-615,749,-869,-400,-54,1000,-901,839,1000,572,625,977,-1000,-845,1000,-1000,343,796,610,-558,421,-432,-596,-778,-334,-182,-497,-1000,334,-949,-1000,-497,-512,473,-285,-843,57,233,-83,-336,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{990,302,33,-813,-619,-378,-272,400,1000,-1000,-400,386,1000,-5,1000,-365,751,328,-1000,-220,-1000,-1000,1000,-279,1000,330,1000,-890,-524,372,96,1000,906,196,-159,-1000,-755,1000,-1000,832,1000,-985,29,435,-209,319,379,841,331,-714,-55,-1000,-98,1000,-1000,-691,-304,392,-115,878,-712,671,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{563,892,-220,-622,-948,94,340,-720,-576,174,331,302,334,-290,-269,-14,666,-335,-41,293,-648,760,847,133,625,29,34,-980,566,-82,831,330,119,-995,-34,-364,-420,241,-522,673,-23,442,-545,646,-268,-269,338,-219,524,-446,-942,-707,369,-991,411,-713,601,-713,-245,-530,630,-781,-618,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-353,-740,-926,-641,200,-1000,-887,-737,-7,1000,153,-1000,1000,-618,-556,-596,592,636,406,562,1000,-831,-1000,120,773,642,-947,-649,975,278,-594,964,598,1000,523,-73,-851,672,559,676,-302,193,259,-97,1000,1000,-231,-915,910,809,471,916,524,721,-107,1000,-1000,-1000,846,-613,-27,104,390,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{338,446,-282,-1000,-697,202,-539,-458,257,323,295,-469,917,567,-380,322,-252,1000,1000,119,600,-589,-832,-312,1000,-295,-525,-458,580,1000,368,739,487,759,645,-329,169,1000,-149,1000,-389,1000,-170,656,974,62,-46,-103,-105,-777,-555,486,-124,74,186,1000,-1000,-707,113,-1000,-240,55,870,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{222,814,920,805,-631,-294,1000,-1000,35,-629,-176,315,1000,1000,405,798,-81,-294,-1000,-1000,-1000,659,-1000,-174,-948,-1000,-107,19,1000,-535,-1000,242,1000,-1000,1000,-656,1000,-1000,-1000,517,-1000,1000,809,607,-1000,-1000,328,182,765,-757,-583,-1000,916,-309,1000,323,575,1000,313,1000,1000,-906,-389,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-259,20,4,852,-883,895,649,-985,-882,763,697,266,-367,19,762,192,-228,513,325,-721,73,625,546,-467,-916,440,599,681,108,21,-244,-527,215,-671,-130,-487,730,-756,-791,-636,288,-728,638,-751,327,-934,-976,-435,379,-341,467,-364,919,445,-558,675,655,-623,-734,745,-842,578,451,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-194,437,-821,587,1000,-1000,-1000,1000,-862,325,-953,-361,-1000,-858,-1000,1000,1000,1000,1000,-1000,-1000,-982,906,-1000,693,1000,-695,-1000,-225,868,1000,-1000,-1000,-903,782,428,286,42,-288,-378,208,-293,-1000,286,1000,198,170,1000,458,960,4,1000,538,-1000,-518,1000,-336,-353,-259,-529,-847,-360,-393,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-371,414,61,-789,430,-457,330,223,1000,-820,697,615,240,-272,-1000,142,-275,-1000,303,219,73,-1000,-398,150,-928,1000,-278,1000,108,384,-3,1000,1000,13,-1000,-515,342,-1000,-44,-600,838,-1000,-838,377,1000,-843,285,700,-485,-562,-820,-364,96,445,-687,155,590,683,643,-1000,685,253,-544,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-193,-437,-1000,345,1000,-1000,-688,491,-919,963,-832,-564,-598,204,-1000,1000,1000,753,1000,-851,-1000,-726,241,-600,1000,309,-46,-840,138,654,872,-573,-1000,-954,236,-115,58,-325,-377,-20,-109,217,-622,717,669,-615,-534,864,128,-4,291,836,-232,-725,-424,1000,583,-257,-1000,-199,-453,191,-314,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-338,-1000,-1000,317,1000,-648,-713,656,-1000,1000,-1000,-142,-839,-1000,-754,193,1000,812,1000,15,-20,-1000,1000,230,1000,1000,122,-888,-497,1000,1000,-456,-1000,-590,-492,-191,-845,53,816,-1000,796,-151,-1000,514,1000,-513,-1000,282,-267,461,1000,1000,-973,-176,-1000,697,1000,-987,-639,-897,-673,536,-259,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-51,946,-136,119,886,-648,-259,810,-800,-27,-1000,-100,-1000,-826,-455,1000,28,658,-433,-1000,-1000,379,856,844,-983,-1000,-242,-931,-1000,581,-306,113,-124,-684,331,-769,89,-733,924,-682,617,660,-899,939,1000,-815,231,-430,1000,-1000,-366,1000,566,-1000,-140,25,1000,1000,-726,-1000,55,1000,-2,-300}));
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
            new int[]{-194,515,-821,578,1000,-753,-1000,1000,-862,325,-785,383,-1000,-318,-1000,863,1000,1000,1000,-1000,-847,-785,906,-718,511,1000,-826,-603,-101,783,1000,-1000,-1000,-664,846,-34,213,186,169,-401,617,-1000,-1000,-280,934,113,51,913,426,658,152,877,626,-1000,-666,1000,-625,-267,-132,-573,-915,174,-633,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-225,-1000,-784,463,-1000,936,1000,-514,-904,1000,76,-1000,240,1000,-1000,142,74,-1000,-883,1000,1000,-783,1000,1000,-928,1000,589,1000,108,-1000,131,419,-521,611,-1000,-515,342,-161,-353,774,677,407,-735,-416,277,-1000,-1000,-1000,-296,-1000,901,134,-1000,1000,-687,-794,-97,304,-21,-1000,1000,563,-437,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-19,954,-737,-654,635,-1000,-171,-1000,-4,325,213,189,400,-330,-1000,121,803,-9,1000,-1,-249,-1000,-1000,268,1000,1000,-309,1000,-40,87,1000,-486,-290,-471,-114,1000,472,552,543,-378,993,-1000,-1000,-833,263,198,1000,615,-182,924,-1000,1000,538,-1000,-80,1000,-336,-1000,120,-345,89,-1000,-899,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-695,-664,-1000,327,1000,-808,-1000,491,515,791,-581,-376,-425,-630,-1000,-7,1000,1000,1000,-210,-188,-967,972,-1000,752,-209,-46,-1000,218,107,458,-1000,290,-954,140,-129,17,-571,-721,239,1000,-180,-591,1000,895,-391,-534,864,-237,631,622,678,709,-447,-1000,35,576,57,-250,-1000,-770,1000,694,-742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-434,423,1000,362,110,493,0,700,-1000,783,-383,772,-597,-1000,665,64,621,1000,-875,517,639,-231,654,998,-1000,-984,-500,-22,214,-777,1000,1000,-7,-966,18,163,-123,128,-12,-27,902,223,-1000,1000,-16,-151,-1000,-90,410,1000,543,-279,1000,-1000,-412,-136,1000,-361,292,-262,321,563,-423,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-468,-721,-211,-920,-487,432,-916,-268,1000,-946,1000,-1000,774,370,649,889,-469,507,-1000,69,-1000,-813,1000,-1000,1000,-487,-145,782,396,901,-580,603,-1000,1000,820,-643,-400,712,971,2,-929,-553,-51,20,266,-768,442,430,822,164,-756,848,484,443,-236,948,-1000,-25,-430,-701,-552,-911,-583,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-930,491,717,923,8,602,-520,773,-817,325,-587,189,5,-990,927,233,541,983,-385,-101,530,-204,880,421,-726,-674,-879,140,830,-813,451,967,-499,-299,648,-248,-56,811,503,328,391,-258,-910,900,475,-48,-402,544,908,995,319,87,902,-738,-855,386,602,-737,-501,-869,59,100,-996,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-951,-491,202,362,-182,95,-529,700,-272,200,-322,-168,304,-1000,248,578,458,388,306,-38,-1,-42,36,123,10,129,-395,239,881,-239,-325,188,-496,90,753,-564,-1000,868,213,1000,1000,-481,3,-100,976,-151,942,681,1000,161,476,215,510,-126,-104,1000,-271,-1000,292,-1000,-259,-388,397,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-434,423,934,-188,432,70,-622,773,-440,783,712,104,-1000,-966,665,64,-233,912,-450,517,-443,-231,654,1000,-856,-1000,448,-22,-1000,-645,1000,1000,-166,-843,12,-156,-56,1000,632,-27,391,-545,-719,900,136,-151,45,544,85,535,-16,-279,1000,-738,-855,-115,1000,14,-501,156,-217,838,-1000,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{575,-936,846,-824,-66,271,-337,690,212,-288,-47,220,600,-582,514,-10,-147,169,-987,-266,91,-746,671,807,508,514,-756,-261,322,568,-790,-241,920,918,784,972,435,737,-116,452,216,-412,-497,321,-409,-934,-944,-969,-873,-598,247,566,-798,-551,-267,-3,114,144,461,-29,172,532,909,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-995,-973,-217,-264,1000,458,1000,257,-820,722,-1000,278,-543,446,-1000,-1000,65,1000,-1000,-1000,-79,464,886,406,106,-796,-95,-698,777,-116,-1000,-1000,1000,-702,-1000,1000,1000,-1000,-339,-671,417,396,1000,581,1000,-600,-1000,-1000,228,4,417,-778,-1000,-1000,996,-1000,343,1000,1000,425,1000,335,-169,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-434,-977,-175,123,1000,839,269,-768,-874,-425,-1000,874,-645,330,-1000,-1000,-130,386,-1000,767,149,-124,606,2,-1000,-830,-220,118,1000,-777,-780,-424,854,-966,-1000,163,985,-1000,-356,-1000,523,-238,427,761,-700,-1000,-1000,-90,410,-87,-140,-246,-1000,-1000,958,-1000,1000,859,1000,482,303,-787,400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getLocale():java.util.Locale",
            new int[]{225,-331,-654,4,31,412,-572,330,-485,-487,962,-356,163,10,-101,998,440,-695,-431,-951,-744,-289,-123,278,235,-211,575,669,758,-3,1000,-401,851,551,527,-832,109,398,914,164,68,-283,-312,-561,703,471,781,603,-943,175,741,621,-617,-715,5,-844,-318,392,962,-683,972,-275,-164,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getLocale():java.util.Locale",
            new int[]{198,190,-726,356,93,-616,-692,-679,-106,548,-619,-976,-767,270,102,5,337,-656,-739,897,817,-710,-728,621,-607,-801,-117,-868,-358,548,-326,-395,-24,7,-289,-594,-846,-720,-350,-834,-121,499,581,874,264,-167,-62,-1000,316,599,-74,-622,-62,404,67,796,443,623,744,105,-926,852,-135,968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzM=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getMaxLengthEstimate():int",
            new int[]{855,-664,-392,-454,440,-565,-506,324,360,939,-275,880,213,-781,747,651,-778,-807,-680,-40,-161,133,610,863,-910,475,-782,-238,373,-936,-163,-35,705,-788,398,431,960,650,60,866,-402,28,-796,973,-462,926,715,-769,-586,-449,-729,-163,235,242,77,-352,653,-292,585,-159,516,534,61,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTc=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getMaxLengthEstimate():int",
            new int[]{-788,445,787,-695,-820,-694,803,143,4,774,420,-351,153,411,-475,297,779,-734,950,-726,-937,138,551,-766,506,548,89,419,685,-818,399,-334,-730,476,528,-150,-320,751,9,-778,661,857,239,-216,-713,601,-931,-645,399,-546,117,-678,-660,-39,-921,-833,-996,-897,-627,-144,659,443,798,892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:RUVFLCBkIE1NTSB5eXl5IEhIOm1tOnNzIFo=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getPattern():java.lang.String",
            new int[]{706,-28,-296,317,995,792,-153,-585,895,-422,-875,-567,501,-668,338,730,-517,539,-450,17,-278,-277,-694,527,967,136,523,-755,804,494,-644,272,940,-183,675,517,285,-992,-30,332,-329,613,469,-551,-836,-630,48,-348,855,-390,-877,-41,-175,-649,113,-210,-502,-981,-687,597,-986,-886,-967,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:TU0vZGQveXkgSEg6bW06c3M=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getPattern():java.lang.String",
            new int[]{-565,-119,-254,-212,624,-967,606,251,-568,950,229,-849,496,396,-126,-272,-633,-213,307,554,-30,-719,-502,486,459,-390,-909,-923,-587,388,-939,922,26,-62,377,544,-555,124,643,-173,-263,-437,-361,783,96,727,524,477,855,894,24,286,619,-285,-637,401,424,-788,930,422,-819,-543,-300,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{-215,529,-571,-443,94,721,475,251,89,-208,297,518,812,507,-353,-934,84,748,859,-574,-121,-58,666,-360,742,-206,347,-993,491,683,82,-695,959,-418,589,384,829,191,76,-283,800,-844,337,602,-785,-323,427,40,75,-403,-235,-987,924,-845,745,882,-515,-20,-27,-78,-899,-381,-904,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{217,625,824,1,497,-568,-1000,-698,83,382,-817,1000,-67,-23,-109,-292,28,-1000,-745,108,850,23,-832,186,-454,-416,524,-1000,169,-517,21,113,-38,404,178,435,480,997,-548,-249,-80,210,-665,425,55,-59,-492,113,932,-852,101,44,-64,592,599,1000,-809,-501,699,-238,-852,-58,-714,-796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{912,49,-505,12,53,-694,672,-718,242,300,-870,-593,4,-232,-989,-112,-928,-777,850,263,-903,-835,664,782,-918,-979,-5,293,956,7,-487,999,-263,259,-674,698,-750,-102,215,-809,541,-2,-289,648,-214,-189,-886,-124,-241,-909,348,-293,-270,837,918,990,-940,388,-909,49,-340,-581,-48,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang3.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang3.time.FastDateFormat",
            new int[]{41,61,921,126,333,182,-638,216,658,630,826,996,-166,-243,171,-690,74,543,536,976,871,726,479,427,-707,-492,-455,-538,-423,205,985,-196,424,873,749,-46,132,282,-492,387,-179,912,-492,530,-624,-364,328,141,-513,619,-759,749,136,-345,831,-405,-721,603,721,-802,-936,895,-128,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{-37,-124,-930,289,666,476,-880,-495,860,344,734,-208,395,268,937,477,995,-18,854,-816,-742,577,282,-177,-33,970,47,-200,968,800,-116,-125,251,-208,-866,129,155,589,944,-40,609,424,-139,694,-805,549,-309,452,496,-668,-640,234,645,-862,-898,-158,-695,684,-204,222,395,-573,479,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{657,106,33,-906,-273,565,752,343,333,-4,296,480,-824,336,613,-59,-26,-134,65,-752,706,-673,517,-264,816,195,-598,15,-552,-966,-152,647,245,583,497,638,-576,170,-945,716,-84,-598,-215,103,-812,-334,-801,-491,59,-70,-25,-610,258,553,830,-785,-183,-599,-545,375,-558,402,926,729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZoneOverridesCalendar():boolean",
            new int[]{-744,719,-8,-46,192,-136,120,-787,703,-480,712,-741,238,143,665,-820,346,890,-904,760,-680,132,538,234,557,-805,277,-464,713,731,335,-292,142,-604,-602,154,-152,676,-801,-824,265,-360,-729,-134,-489,-834,-201,811,472,737,378,-663,-948,319,864,8,237,-989,-739,-997,243,-837,562,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZoneOverridesCalendar():boolean",
            new int[]{-692,-356,226,818,540,164,-815,622,653,-175,681,-848,419,-703,-923,-85,366,-201,-541,-736,913,384,-746,-451,127,605,736,464,361,614,-909,992,-565,-925,-438,-723,-195,-572,329,-429,194,948,-730,13,539,854,490,381,780,-759,614,-768,-962,41,-760,836,991,377,658,215,-79,-285,954,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-214,-703,-868,842,-117,-133,-422,-858,-276,-915,-702,873,-720,-293,606,-605,438,-498,-760,-960,557,231,-330,481,495,473,-11,531,-489,372,-650,-690,-845,-674,162,-599,-377,657,-874,473,896,390,-418,657,-564,402,628,925,-792,-589,-15,689,-627,548,-304,-115,103,-433,388,931,680,-836,-933,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-141,247,283,-301,-236,961,688,-911,-818,-377,804,-238,427,251,198,-135,483,-266,-717,961,116,369,19,-243,181,145,-504,-779,-356,-70,-535,-884,-429,-254,161,-273,682,-731,638,-642,904,820,186,39,-165,233,438,794,-995,185,-743,-186,400,-132,-381,-50,844,178,-662,-2,498,-986,739,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.String:RmFzdERhdGVGb3JtYXRbRUVFLCBkIE1NTSB5eXl5IEhIOm1tOnNzIFpd", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "toString():java.lang.String",
            new int[]{-147,-760,48,809,928,-363,316,-309,1,984,-496,-472,-685,-885,-655,-167,-895,48,-737,-428,342,-136,345,-723,-547,-225,402,465,-105,-770,104,267,-124,-705,219,410,-310,243,821,378,146,-656,-976,466,-588,-845,267,-306,-796,-724,-554,137,718,-501,339,-968,-51,-446,631,-721,-881,220,-385,-615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.String:RmFzdERhdGVGb3JtYXRbTU0vZGQveXkgSEg6bW06c3Nd", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "toString():java.lang.String",
            new int[]{-24,-485,28,26,-494,-853,425,-442,-753,196,422,-756,-233,397,-58,262,-913,-758,597,539,-533,-58,-387,-232,-419,-78,690,-285,307,474,87,511,705,-364,262,925,984,41,271,-791,-747,-461,-223,-928,668,699,-2,566,-761,-16,239,221,-4,318,715,646,474,75,-665,981,-448,-921,-202,98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{686,493,-248,530,301,-430,-174,1000,-1000,-1000,376,885,1000,1000,538,-18,1000,356,1000,1000,-1000,-1000,1000,1000,-1000,1000,-1000,-162,1000,1000,266,-972,771,1000,-1000,-1000,-1000,1000,1000,-1000,719,986,-513,1000,-1000,449,-47,1000,944,-1000,1000,-1000,1000,-1000,-1000,1000,-1000,-1000,-1000,-904,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{446,552,75,429,1000,-355,603,1000,-934,-1000,1000,74,1000,258,-425,282,784,-249,329,-304,-1000,-287,1000,1000,-1000,488,-1000,-186,1000,1000,-244,-1000,-1000,1000,-435,1000,367,1000,345,-888,286,-576,-484,850,-863,7,-589,543,-272,160,-44,-400,92,-432,-1000,297,-320,-607,799,973,-511,-1000,-6,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{738,800,797,794,-515,-503,-145,550,-115,582,-828,-589,410,528,979,-245,564,414,212,-127,-488,-989,-383,315,-220,819,-789,2,-44,-107,577,-262,713,-693,750,-68,-789,-7,107,354,-79,-657,-449,596,-691,-568,593,494,-591,-970,-575,280,-686,656,991,417,-114,-137,-769,-774,-452,639,-840,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{189,-1000,-1000,658,1000,693,-152,642,-1000,-1000,1000,970,-625,465,-1000,-540,-1000,253,1000,-143,534,571,1000,1000,127,-219,-564,-997,163,180,-1000,125,-479,1000,-131,63,979,-602,-1000,-1000,-474,11,1000,-1000,649,1000,-1000,-1000,-449,776,676,-995,-80,-1000,-466,305,893,-171,1000,-1000,197,-1000,-420,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{558,332,-1000,-741,-1000,330,330,1000,-1000,-1000,-1000,951,1000,1000,400,-104,1000,1000,688,280,-1000,-1000,1000,1000,-1000,593,-1000,-142,961,1000,-245,-1000,665,1000,-460,1000,-1000,1000,724,-686,-910,575,1000,1000,-1000,1000,-1000,609,-93,-781,673,-1000,902,-667,-1000,976,-549,-528,-1000,1000,-614,-1000,201,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{334,-617,75,202,1000,768,43,240,104,-605,758,712,563,516,-169,-728,381,306,-903,259,264,147,1000,1000,-1000,-749,-898,-46,-166,1000,-173,242,1000,914,510,443,-517,461,345,-1000,318,583,-343,497,-161,-464,-515,-399,148,160,-198,540,534,-535,201,625,112,44,606,193,14,8,-863,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{25,-1000,-1000,619,-1000,753,199,-1000,703,1000,-513,161,-1000,-292,-633,-444,-1000,583,-760,-904,748,770,987,-772,1000,-767,1000,-639,-705,-1000,-4,1000,335,-1000,956,-1000,979,-1000,-1000,-426,-186,656,1000,167,997,667,-894,-977,1000,1000,-65,138,34,-259,1000,397,905,472,725,-872,446,1000,-514,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{-769,-1000,-917,44,-1000,-1000,-561,-1000,-962,1000,-1000,503,-1000,968,1000,-1000,-1000,990,-1000,202,1000,1000,991,-1000,20,-1000,1000,-700,-1000,-1000,-1000,1000,1000,-1000,1000,-1000,-337,-1000,-1000,-434,-420,72,1000,-988,1000,1000,-369,-1000,1000,1000,-455,54,654,-195,1000,304,1000,718,821,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{-644,320,-327,261,-425,-481,-241,-404,-875,-941,11,-993,-323,890,-756,-403,-98,-63,-190,-837,668,-98,332,243,327,-486,-140,705,606,-900,-511,560,-18,417,-240,-127,143,536,-190,-324,174,-791,-862,588,428,677,-858,493,-291,-893,-916,-927,-659,16,64,-753,-21,-107,720,-351,-384,-999,-104,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-306,436,-825,-520,1000,1000,-869,575,-1000,1000,884,-194,-1000,1000,-1000,-227,259,-208,-1000,1000,-395,-723,-825,27,-994,1000,1000,-419,549,-1000,1000,-902,-1000,-1000,640,-1000,-1000,-1000,-954,1000,1000,-1000,-1000,599,37,-271,-1000,1000,-1000,836,-1000,-1000,288,-1000,1000,-120,-1000,-81,1000,-492,-767,628,1000,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{223,-1000,145,588,-1000,562,887,1000,84,-174,-1000,181,19,1000,-548,-1000,-762,-855,411,-1000,419,303,4,-198,265,745,-285,591,-527,593,-222,-909,645,330,-508,716,660,-926,-986,-963,117,-1000,699,599,440,-996,-1000,-20,-1000,598,-480,240,-47,-83,1000,65,-1000,-81,-1000,776,1000,382,734,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-606,-1000,170,233,-1000,-1000,-131,13,-322,-474,-960,6,1000,-204,-345,958,-899,-500,-229,-354,1000,-945,-843,302,85,249,1000,1000,-570,321,34,-85,231,1000,-1000,-894,548,833,-1000,-1000,2,-37,116,1000,278,-1000,905,-270,-273,-255,-571,732,-634,-729,864,-197,-1000,-503,402,-39,-497,-974,542,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{413,1000,-939,-747,1000,1000,-662,865,-1000,-203,515,-243,-1000,1000,-1000,-1000,207,-868,-841,623,1000,-20,-725,-25,-910,860,63,-165,-1000,80,1000,-1000,-161,-968,-165,-32,-952,-507,-899,950,764,-1000,-627,-429,-513,-271,-1000,1000,-1000,1000,-1000,-960,422,-921,933,164,-912,-1000,1000,43,-431,1000,387,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-338,-1000,-1000,604,-287,562,1000,531,84,-489,-839,301,76,965,432,190,-643,-453,93,-20,806,303,1000,-429,265,-169,-474,711,-1000,727,131,-434,297,520,1000,716,1000,-926,-1000,-621,-515,-1000,101,-18,10,250,-1000,-88,359,37,-153,-272,-323,425,-389,65,-1000,426,-664,459,851,754,734,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{205,1000,414,218,58,1000,-749,-266,1000,-327,1000,0,-918,-1000,769,538,1000,1000,-813,324,870,718,-1000,201,-484,19,-1000,-369,-258,-1000,-866,1000,-1000,-1000,1000,1000,-1000,-357,1000,928,214,-1000,-1000,-1000,-684,1000,1000,-1000,1000,-921,1000,826,854,752,-738,1000,718,1000,1000,-130,-475,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-436,-1000,-62,737,-1000,-1000,1000,934,-125,-90,-661,-893,1000,517,-740,-440,-357,-294,400,-211,-287,105,451,117,718,-989,1000,1000,636,287,-515,751,-466,972,-1000,-393,156,-90,-1000,-856,95,292,1000,601,413,-1000,-20,76,-548,-51,764,621,-973,849,-400,-684,249,-566,-850,150,248,-1000,264,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-1,-319,156,307,-31,727,-51,474,-302,374,-401,119,961,609,234,-390,-887,-315,-880,-444,-735,442,-287,838,-883,-941,-368,543,-874,849,276,65,-166,225,307,-201,593,284,96,-138,-314,-403,-653,22,869,-273,-409,662,-222,772,-977,-8,-734,-212,-770,972,-220,349,-238,432,-823,674,-773,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.String:IC0tNDM3IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{954,437,-181,-459,847,-497,-750,765,102,889,85,-689,-898,-553,-751,-815,-39,154,807,529,-795,-954,-826,-320,337,390,-58,520,-869,-890,678,267,624,992,-399,64,218,534,-139,684,13,-579,-687,452,-402,-430,127,114,-146,-571,-639,-228,-974,816,702,245,317,-75,988,-732,-455,384,843,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{383,-582,137,-230,-1000,577,-940,775,1000,-604,1000,1000,-455,689,-1000,-724,1000,-602,-11,196,-217,-431,-103,-962,1000,1000,-154,-1000,-1000,190,-981,-607,-1000,1000,-1000,-1000,-520,878,-1000,-1000,1000,-1000,-450,-776,-263,-265,-1000,-88,842,-717,-1000,-1000,805,311,603,-1000,-445,1000,1000,456,175,-1000,130,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-371,881,1000,460,886,121,1000,1000,550,355,-1000,-1000,1000,-442,1000,795,-163,-898,-1000,1000,238,-682,178,1000,-1000,-794,663,-631,-1000,602,-1000,-414,673,650,-85,1000,-157,-7,592,461,-1000,985,74,825,1000,-386,774,452,-944,1000,29,95,-944,-29,-978,1000,-705,-1000,-1000,-256,-1000,1000,-128,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-247,404,1000,-147,448,-1000,1000,896,917,-607,-182,-1000,1000,-168,985,857,563,400,-561,-1000,1000,-753,1000,182,-677,72,706,-1000,-961,399,1000,-1000,421,356,-1000,732,-156,639,-175,-93,-1000,1000,1000,492,985,-895,1000,-383,-89,1000,72,-599,-1000,820,-1000,938,-512,-1000,-615,142,-969,-110,16,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{398,-1000,897,368,-385,1000,-9,245,-93,-133,-401,616,1000,462,-93,-1000,-887,-617,-1000,394,-802,662,-295,763,517,315,-813,-857,-1000,1000,140,50,-738,525,-5,-614,324,780,-30,-1000,213,-734,-1000,76,1000,-809,-1000,543,138,671,-1000,-542,-669,-694,-1000,904,-1000,600,1000,832,-1000,46,100,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-142,417,517,707,940,610,-791,-900,333,357,588,-289,-397,463,-307,703,-90,-401,823,105,716,-742,869,348,-172,735,488,-106,-318,-307,559,682,919,-563,871,-675,-305,-713,-161,-98,-56,-385,203,-945,-144,51,113,-354,-797,96,-220,-328,822,-7,980,360,-420,66,72,-584,-590,927,916,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{637,998,-862,48,293,685,171,-309,1000,251,234,205,574,-135,-14,-501,-1000,80,57,-1000,-768,116,-634,838,-861,-1000,-281,835,-874,-281,-175,-346,400,-1000,307,-1000,1000,1000,182,677,-302,-711,-542,823,311,863,1000,380,753,359,-1000,140,-674,-345,-470,963,904,313,-90,-294,-483,1000,-143,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-212,419,-71,496,-921,213,-668,283,742,-906,1000,858,-1000,-216,-1000,-662,1000,-861,1000,378,1000,-257,1000,-866,847,1000,69,-1000,45,-243,-1000,-1000,-81,-973,-1000,61,-1000,443,-778,-1000,1000,-952,-57,-811,-587,952,-564,354,44,-1000,325,-317,-1000,-182,963,-588,582,538,879,522,313,-1000,-77,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-979,34,-1000,856,1000,-1000,-1000,745,-1000,-1000,619,70,-1000,-1000,-469,-392,-270,-701,-290,1000,-1000,1000,1000,-277,-388,-643,1000,-1000,-991,-1000,618,-191,-572,20,-1000,837,-336,-1000,-1000,-296,715,691,-312,-1000,916,581,-373,1000,-1000,1000,-470,497,621,-269,838,-1000,-199,1000,-1000,847,-1000,-850,-509,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-561,1000,-1000,535,543,-274,17,-965,-762,-300,1000,-1000,-204,-925,663,709,966,-791,316,-486,-1000,-72,-18,491,1000,-194,174,-1000,-70,-272,-969,1000,521,-814,-1000,-59,-268,73,370,246,-1000,703,-335,834,1000,-1000,-1000,1000,-526,-1000,-939,1000,-384,903,525,-1000,1000,-147,388,961,-64,87,-1000,-638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{898,904,-903,-759,-1000,-683,-523,-469,242,677,6,68,-1000,197,668,372,32,1000,222,-628,715,-423,-826,7,307,920,-788,-609,-119,-285,-41,10,-744,-704,-165,-1000,526,-485,69,80,387,277,384,-606,-1000,781,252,-292,80,-616,-788,1000,-195,326,323,-340,370,55,585,-51,-585,-1000,-189,539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-979,-805,-506,749,1000,-818,-1000,-950,-585,-28,-183,-1000,-1000,-1000,1000,669,-270,232,508,99,-1000,88,286,-372,932,782,-896,-1000,-515,-642,-400,608,701,20,-533,354,-580,-1000,-417,-503,-116,891,296,-1000,521,-661,-1000,817,-646,504,-799,497,-516,-33,787,-647,-106,283,-1000,606,-456,-1000,-968,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-391,307,-237,813,1000,-183,-952,483,-1000,-1000,1000,-482,-455,301,-657,798,-622,-246,-91,-32,-477,1000,685,49,103,-700,-72,-604,-1000,525,892,480,1000,142,-1000,-1000,-614,-1000,-1000,21,261,779,470,-113,1000,-1000,-981,1000,-1000,883,-902,107,881,-615,1000,-455,-422,1000,-1000,936,-352,55,-493,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-2,819,-371,886,-1000,839,-1000,1000,-809,-443,-752,-212,468,1000,-466,413,741,-545,-1000,269,1000,115,1000,631,-593,-1000,514,857,-104,685,544,-1000,-665,1000,1000,341,190,33,-416,824,650,-1000,941,-884,-1000,795,-373,380,544,1000,-765,-1000,1000,-724,648,1000,-54,1000,-1000,504,994,365,960,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{781,323,67,-805,-640,990,943,-602,-672,306,-286,-400,-499,-382,-535,386,526,148,747,-400,171,400,-345,483,-413,-263,139,-214,-86,-352,-20,871,-317,-1000,-869,-1000,475,-697,400,81,-78,1000,1000,-71,-860,-323,-400,740,191,-672,-967,1000,-302,571,236,-416,863,55,643,-530,-141,-323,-423,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-391,73,-585,937,1000,-626,-992,483,-781,-1000,1000,-1000,-492,-196,1000,-50,895,-631,-40,-492,-1000,1000,685,49,380,-503,329,-1000,-1000,-586,379,39,64,291,-1000,1000,171,-369,-796,950,420,779,116,-1000,1000,-431,-1000,936,-1000,1000,-902,141,-76,199,903,-812,-1000,0,-1000,847,-722,55,-779,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-129,405,-824,-542,-417,562,-952,-421,-486,595,407,-352,447,-948,-118,-275,1000,-1000,-91,-132,-637,-350,-580,792,-507,-152,-601,-400,-310,-425,-979,453,3,-270,-264,647,233,351,559,472,-1000,163,470,223,400,-1000,-65,839,-1000,-1000,-539,400,-736,1000,591,-143,800,-232,1000,936,255,-60,-400,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{875,111,1000,-194,-127,-475,-1000,-1000,1000,-1000,-1000,-179,1000,461,-79,1000,687,-1000,1000,-1000,-14,351,-589,1000,389,1000,-1000,-1000,-1000,323,36,788,-1000,-325,-722,1000,-17,847,-468,-179,1000,1000,1000,254,-323,1000,1000,257,604,282,1000,1000,331,495,478,-856,-151,1000,-580,237,1000,1000,800,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{157,-482,-1000,-859,398,-783,712,-877,1000,-179,80,-196,1000,-133,-523,352,422,-270,1000,123,1000,1000,578,493,-756,878,86,-225,-69,107,123,381,-567,684,-288,999,25,1000,-429,-1000,605,-101,-1000,324,-619,-336,893,-771,-896,189,315,801,400,161,316,1000,-701,-587,-400,506,111,827,372,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-566,-19,132,582,99,-443,-932,-1000,-1000,632,-923,-607,84,135,-265,-143,-534,652,1000,766,-730,-964,-232,1000,1000,-871,159,1000,-726,-888,-187,-1000,-834,-654,877,12,156,65,1000,228,980,-17,-625,-788,-112,1000,343,1000,1000,-145,204,1000,147,766,-618,-1000,1000,125,-186,-760,-1000,256,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-477,959,439,-805,572,155,506,-100,1000,461,554,-919,1000,1000,-254,-613,166,-1000,1000,32,44,-332,733,-1000,1000,255,-400,-1000,-598,784,123,-1000,732,170,-1000,505,409,449,-552,486,-1000,-313,944,-127,-738,-13,646,1000,639,809,-823,605,-1000,-465,-1000,1000,259,1000,985,-214,1000,689,845,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDI=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{621,-839,-1000,1000,-293,1000,-1000,-537,-1000,1000,-1000,1000,1000,626,-576,1000,1000,843,-1000,-672,-1000,275,968,-1000,1000,-434,-1000,805,1000,923,-1000,1000,-478,-1000,965,-809,899,-461,1000,-261,1000,-1000,-1000,502,-1000,1000,1000,355,1000,-192,-388,-1000,1000,-341,1000,-1000,1000,1000,-1000,-441,338,683,-1000,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-385,-1000,-933,-426,-977,585,-1000,673,209,146,-530,223,-776,-750,-1000,345,-72,899,-882,622,-1000,-809,1000,591,-30,343,-412,-1000,343,-493,210,1000,-785,1000,-124,-161,-148,-296,-1000,-764,798,-457,390,683,905,675,-785,-1000,1000,1000,-108,257,874,779,858,-912,-580,1000,464,699,77,1000,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.String:LS0zMDk0", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,249,-309,-497,-659,-1000,-962,-56,-9,1000,961,-853,1000,-498,850,772,873,-419,62,125,236,781,-422,1000,-675,38,-595,1000,-131,-857,-1000,-906,1000,-142,487,-330,450,833,1000,310,-232,-138,-550,-1000,-518,608,25,-454,-31,81,-804,1000,-1000,838,-17,208,-936,1000,-1000,-234,-356,-804,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{10,174,-1000,12,273,-761,155,461,699,-1000,-363,-1000,-26,446,-857,-1000,-1000,-1000,1000,-376,338,-952,-104,-74,664,770,390,-1000,-612,623,-34,-662,804,-125,-789,-635,-587,-969,-535,-32,-287,830,539,856,1000,-166,513,300,-368,327,1000,1000,934,579,-880,580,-242,-1000,776,593,-379,247,487,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,542,-41,1000,464,611,1000,-211,568,-1000,403,-834,-1000,653,-1000,-1000,-982,-1000,872,1000,-1000,293,143,-843,192,-937,-1000,1000,1000,1000,313,268,224,-507,-169,-543,654,-758,91,300,319,326,1000,-1000,752,-631,51,144,1000,-1000,-1000,668,1000,182,383,-1000,1000,-215,-862,-159,502,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-49,223,-699,1000,484,-652,64,252,553,202,-206,-782,1000,-115,-857,-844,401,-1000,1000,-333,-1000,-1000,-521,307,17,1000,390,-1000,-236,347,-674,-1000,1000,438,962,-271,452,-71,-115,-165,-1000,668,249,343,931,549,593,-20,-1000,1000,763,812,-128,1000,411,613,-321,-70,-590,5,762,147,648,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-577,1000,-510,-615,346,-948,-419,-375,-384,-56,-1000,-391,-541,345,-511,409,437,123,555,60,111,-837,-16,-229,-320,-122,1000,210,-660,58,-362,605,845,-527,23,627,-640,755,685,-419,-426,-516,-721,573,941,609,-453,-265,626,-610,1000,909,-1000,-1000,-292,187,955,-782,-1000,-347,-635,841,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{595,94,1000,327,-676,213,409,-1000,-1000,-758,-301,-1000,-581,17,-421,-539,-765,-489,1000,1000,58,119,-1000,104,789,-622,-442,1000,-490,444,666,83,709,86,-931,-300,-95,-1000,-645,783,-740,-376,-975,138,1000,480,-606,119,872,-192,-513,931,1000,-277,-551,-997,1000,998,-205,-64,-308,-225,1000,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.String:OA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{235,798,-193,147,-699,-378,-425,327,780,851,-199,-825,217,277,-427,-566,116,152,-976,-1000,374,881,-617,1000,-147,643,175,532,-354,517,173,-219,1000,-593,409,-347,851,222,-288,1000,949,-648,-1000,860,671,977,-26,-400,-1000,-66,158,671,1,-400,202,265,221,-540,-423,-1000,512,595,-693,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{235,589,606,-245,-87,-274,-1000,-1000,517,-528,-669,-572,457,-712,-322,831,273,44,-889,-748,-206,1000,-825,713,-725,-585,-235,1000,-908,1000,75,233,400,-479,-400,589,-98,-1000,400,-1000,758,1000,-1000,-395,134,794,360,-317,-606,400,485,-1000,-849,388,53,226,849,-1000,-1000,-1000,1000,706,-844,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-1000,429,1000,1000,-959,-782,-525,-249,323,-836,-165,-100,-268,60,-906,746,109,211,-1000,999,-962,82,-940,-637,729,1000,-1000,526,-362,841,-902,172,-889,-773,526,-563,-250,111,-1000,-1000,654,1000,-91,-201,436,715,322,1000,-342,614,120,-629,-1000,1000,783,961,113,-821,-1000,-189,-303,603,-462,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-1000,-402,599,260,-976,54,-600,-522,323,-242,-32,1000,639,60,-756,-150,-43,-1000,-1000,883,1000,152,-1000,-368,-241,502,-367,501,-362,326,-1000,172,-58,-886,-38,-59,-501,111,-615,-587,654,1000,676,246,643,715,1000,1000,290,1000,395,-193,-1000,672,306,277,-640,-450,-432,-189,-723,-156,764,825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{106,238,1000,-972,-985,886,-1000,-1000,826,366,869,408,1000,-48,399,1000,-786,-1000,-717,1000,1000,1000,-1000,-1000,-1000,824,-97,1000,-594,-512,0,1000,197,-1000,84,590,109,-1000,-346,-944,897,497,392,-495,1000,587,947,1000,-524,1000,791,71,264,327,493,-876,-113,-1000,-903,-295,-108,28,-1000,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.String:KzEwMDAx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-944,505,-1000,-202,367,-47,-421,-39,739,-252,54,225,-1000,233,-241,-55,19,347,-966,-641,-1000,-223,598,671,-579,-468,198,614,305,186,145,-452,364,1000,-734,-400,-513,-99,1000,1000,303,-888,-783,-138,1000,-910,1000,-1000,-329,769,276,-1000,430,-1000,622,-765,159,67,320,400,207,-129,1000,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-375,-30,-898,829,-981,-536,933,-616,-83,553,809,-720,-446,-105,181,-145,13,-372,124,57,203,-20,590,407,305,-287,737,825,752,-463,-680,-301,269,-626,125,61,-440,90,53,265,910,151,608,-790,-930,-442,-745,-214,-357,-632,-589,982,-575,-880,675,887,-931,573,299,-325,-112,298,892,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-344,173,416,26,946,-739,-463,-1000,-40,595,-699,232,-1000,-518,177,1000,746,-400,-425,-676,-634,821,-1000,848,-441,388,840,176,123,-910,683,530,246,287,-1000,-247,305,1000,-481,318,-820,-427,-169,1000,240,-657,102,-1000,1000,-1000,565,-844,562,-248,732,-98,-393,561,-768,-231,171,-783,974,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.String:KzIyNDM=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-134,297,224,538,649,1000,-705,699,-164,206,-656,991,-1000,1000,91,833,362,-852,-241,-672,-1000,668,-435,612,-813,-597,171,694,719,-668,1000,-993,999,-801,-375,554,538,-501,-213,393,-674,1000,1000,980,790,758,346,-559,637,-1000,525,315,97,-398,-463,-508,-266,81,-194,-337,218,-1000,-48,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{920,-942,512,402,346,-430,318,-890,-426,612,824,996,-973,446,843,-50,-303,283,653,-674,-342,-876,443,193,681,434,805,438,582,63,328,459,-651,-801,194,185,862,222,805,-70,-948,-672,948,212,-270,-497,-219,-941,999,-960,-5,-409,144,141,937,-343,-783,-623,-846,-788,-861,761,878,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-715,270,233,278,988,-74,-607,1000,457,444,-759,700,-1000,25,243,1000,714,-296,-1000,-972,-1000,615,-596,711,-275,-118,161,-63,923,-427,1000,830,-162,-121,-956,96,-81,541,-1000,-133,-844,-556,-109,986,625,400,628,-691,475,-762,94,124,335,-272,-1000,-693,72,356,482,182,764,-695,29,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.String:MjA3NQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-469,617,207,396,19,-553,-309,-1000,820,499,-806,-873,-181,407,-764,-11,-774,-251,-350,737,-1000,-516,813,536,641,980,503,148,1000,-67,-346,468,204,-1000,-447,-1000,-391,779,-142,818,-991,251,119,-336,283,-485,-289,-879,-687,-1000,-386,-53,-467,591,-377,214,-1000,-577,-317,684,165,504,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{319,227,269,-919,20,-60,226,-387,1000,1000,-178,-160,1000,-327,85,-1000,373,-188,415,1000,326,1000,124,-991,-1000,220,-1000,-419,1000,738,379,-650,-1000,-1000,535,155,-872,553,455,-1000,-168,577,-545,-1000,-416,1000,478,891,-366,-856,-11,-750,1000,921,-437,-211,-1000,-551,-1000,202,1000,1000,771,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-543,893,-1000,-1000,598,-804,-309,581,451,93,280,-316,-57,-400,-847,-460,-774,1000,1000,-294,-857,341,-172,60,-413,-1000,254,559,1000,-67,1000,-400,-108,465,296,-794,-391,-1000,-1000,-118,580,-895,1000,-524,100,208,839,1000,830,540,-386,984,223,1000,748,487,-542,579,1000,-10,-667,450,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.String:MjEyNQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,447,-535,21,-232,-502,-1000,374,-1000,-567,-887,-1000,-1000,-972,489,1000,-1000,-593,159,-1000,639,-1000,-96,1000,1000,293,838,1000,-1000,1000,-1000,205,1000,340,82,-1000,-707,-183,550,-184,373,-1000,726,1000,-173,729,-798,632,-565,411,-91,856,-979,-1000,591,351,1000,-1000,23,-363,-671,1000,-708,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-741,638,189,1000,1000,-352,-1000,-392,920,1000,1000,607,1000,400,-400,193,-148,-113,-827,-62,1000,1000,192,-285,-766,1000,1000,1000,290,232,330,256,580,-346,-158,31,-529,1000,1000,400,561,226,-759,120,-80,429,-185,-400,-400,187,-725,-1000,188,-1000,-918,-670,-449,-605,-69,-496,146,-764,-75,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{818,-217,-465,294,138,-506,-487,467,667,68,-712,707,918,917,-64,-465,-344,821,537,673,699,562,-84,-299,-153,-172,-594,848,691,-567,-576,816,-983,-353,739,-335,-662,506,-720,267,-335,327,-920,-383,52,326,-455,-657,686,257,590,-783,334,264,408,108,-213,-205,-360,-78,-349,-684,532,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,1000,-387,-977,-913,-485,-974,711,107,-1000,-1000,-646,-874,-1000,-143,324,-1000,349,1000,-1000,-86,-229,669,843,835,-493,-1000,430,-779,1000,-1000,395,-1000,-475,1000,-1000,-492,577,-211,-952,-822,-610,83,1000,13,1000,112,1000,1000,799,1000,949,-457,-1000,-6,881,574,-905,200,237,-956,751,-972,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-738,903,318,-495,-413,-926,-510,833,-433,-565,-652,-613,-1000,-1000,-2,1000,-886,103,595,-1000,-57,-1000,-1000,1000,986,-1000,-1000,-471,-1000,1000,-1000,-545,-1000,67,484,-1000,-346,490,-206,-577,46,-939,810,1000,144,835,-789,1000,432,261,431,283,-1000,-141,1000,795,1000,-1000,693,-249,-1000,120,-322,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-118,1000,143,-19,-879,998,996,646,32,-206,110,929,-946,-259,-243,433,-498,193,-771,-98,-661,-405,-111,144,484,642,83,-345,366,760,-1000,524,1000,1000,1000,542,-14,509,-687,-287,100,77,611,-407,-168,-479,-682,-687,1000,321,400,-453,368,506,-589,525,614,728,194,-362,-189,124,941,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,482,623,261,-1000,292,1000,1000,233,-351,886,556,-1000,-805,-1000,424,-1000,-1000,-1000,-222,-1000,576,78,1000,-1000,1000,232,-1000,1000,-733,-1000,1000,599,771,1000,-719,-782,385,-1000,-1000,1000,-158,-153,1000,-313,-1000,-1000,-1000,1000,1000,707,-1000,1000,1000,-1000,870,1000,-733,1000,-1000,-358,1000,851,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.String:Nzc2MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-645,1000,681,-776,-1000,-563,755,515,-745,1000,79,693,-652,-1000,-1000,899,-730,988,629,-1000,-1000,1000,-514,-27,-293,1000,1000,-1000,954,-68,-157,1000,1000,893,1000,-52,-1000,1000,-1000,400,1000,798,-102,693,102,-490,-1000,-1000,1000,1000,-693,-964,-346,277,-1000,1000,1000,-436,1000,-599,1000,2,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-952,1000,143,1000,-365,-318,996,887,-1000,-154,417,-741,-1000,-317,-443,759,-673,1000,1000,-818,-1000,620,-1000,1000,-620,1000,843,-908,1000,-1000,-1000,1000,1000,1000,1000,1000,-14,697,-1000,-1000,903,-489,388,1000,1000,-1000,-1000,-1000,1000,1000,870,-453,-67,943,937,1000,614,-1000,1000,-1000,-189,1000,1000,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-165,-66,703,-443,68,-693,158,-1000,-317,1000,290,-241,1000,-490,-480,-471,609,-209,-315,-957,192,863,1000,-400,672,185,433,740,1000,-649,521,1000,-1000,-1000,1000,1000,446,223,-1000,-1000,781,-1000,152,-860,-1000,379,814,-1000,-1000,1000,-432,-1000,1000,-1000,136,-464,-621,455,-1000,-685,-669,156,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.String:NjIx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{741,-247,-813,621,-99,-955,-167,-125,270,-127,38,-717,924,-23,-648,678,-895,445,408,598,-27,464,104,394,-904,136,-329,-652,723,-742,404,561,403,-76,696,954,-718,456,-744,-955,-451,-305,901,-648,-54,10,-836,227,962,520,89,757,-557,952,71,-78,-342,-660,-637,-347,599,-662,-52,649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-449,306,543,-281,-1000,231,634,-143,-167,625,-407,764,-93,-1000,-966,819,-81,-193,-570,-750,-1000,787,-576,-27,1000,211,1000,-600,491,791,-58,307,1000,-878,209,-52,-435,610,-258,1000,707,884,-1000,144,16,129,64,-428,-175,299,-1000,-1000,-849,-148,-1000,687,-401,156,260,-599,1000,-217,1000,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{425,483,157,-320,1000,864,626,-1000,1000,309,513,996,1000,1000,1000,-108,-11,193,1000,456,-58,421,58,-485,377,-502,341,820,-777,937,556,-33,281,-1000,-820,-682,-95,-255,1000,912,-339,547,-506,-1000,-1000,331,-835,831,350,-980,1000,-613,693,-598,207,-1000,-820,176,-198,940,-703,-260,-537,-194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{417,-1000,575,-963,654,962,-1000,-1000,1000,681,-1000,-850,400,278,1000,-433,1000,-802,-1000,-291,1000,-467,-281,-1000,1000,385,792,1000,-1000,-406,457,311,270,-849,-1000,-537,281,363,1000,1000,1,383,180,-1000,270,572,400,1000,-129,-982,-1000,-701,-105,-1000,1000,-1000,-1000,682,-1000,400,-1000,-1000,-770,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{127,-432,-497,-2,-1000,324,357,-670,839,-562,-203,212,-455,324,-299,752,-715,558,-100,-122,1000,90,400,-329,-511,888,237,877,-1000,383,-985,-463,-54,315,786,28,-126,-1000,-148,-757,1000,1000,6,1000,863,35,17,-544,-1000,-1000,400,1000,-1000,-1000,-1000,75,1000,-179,400,-1000,611,1000,-894,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{1000,1000,-353,444,-196,-602,-36,1000,-795,-1000,-1000,-27,1000,207,-698,-866,1000,-1000,-231,-465,-1000,-83,997,957,-167,-1000,843,-1000,1000,682,97,938,-1000,862,382,806,1000,1000,107,-29,-343,353,-764,-925,-1000,20,466,1000,323,-318,-450,-1000,953,-191,-1000,-246,-1000,42,-266,544,40,-1000,687,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-228,400,-563,79,-1000,448,74,-591,-1000,-837,1000,1000,-1000,490,-584,-1000,-1000,-399,241,672,1000,-1000,-1000,1000,809,1000,414,84,1000,-859,200,-713,-7,-118,-776,-990,-367,-1000,1000,-1000,1000,-527,220,1000,153,-910,330,-154,-1000,-1000,1000,1000,-1000,101,-654,-1000,-221,-1000,-1000,-891,839,922,-1000,6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.String:LS03ODUzNjU=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-238,-406,-487,-785,-533,597,357,-567,-561,-301,862,990,-1000,198,-299,752,-1000,558,110,127,877,-689,-746,-267,726,888,414,877,-109,-631,123,-812,-168,315,-319,-572,-587,-979,-225,-931,1000,-256,5,965,863,-1000,-272,-544,-1000,-843,400,1000,-927,-641,-1000,-1000,375,-488,-1000,-1000,738,919,-400,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-980,-886,-755,725,1000,328,835,-309,53,-842,-273,-817,-626,-770,-113,714,-256,1000,-1000,1000,-1000,-1000,-476,620,314,132,1000,58,460,-747,1000,-1000,-1000,841,-525,-255,96,592,132,862,-1000,-171,-951,-29,1000,-1000,1000,220,478,728,-1000,122,619,-808,-1000,-1000,-240,294,-1000,-785,-773,431,-1000,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-111,776,-334,-141,-64,830,320,817,-192,-179,-507,-327,-324,471,-999,-254,-589,-531,-80,519,510,820,-825,-903,920,-427,-882,-914,373,614,-806,-798,197,473,174,-501,479,476,354,749,917,-998,-360,200,-271,-939,-509,729,-397,-997,331,207,806,728,-784,-185,-695,-510,-690,-12,30,91,-983,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{14,-271,-754,-627,-1000,-294,-920,1000,1000,-607,-48,148,-294,937,-947,222,-1000,1000,877,-966,566,-173,908,-705,99,408,1000,671,-526,405,434,-340,22,138,1000,-132,295,76,-422,-657,1000,335,-939,-284,844,1000,-468,-944,-1000,162,1000,510,46,-46,-825,1000,1000,-1000,-20,-987,760,217,-492,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.String:LS0zODMyNQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-653,-237,537,383,807,447,-814,125,-575,-366,-135,1000,-650,396,704,-1000,-1000,-1000,-611,-459,-173,239,1000,-1000,-1000,190,-970,169,1000,908,1000,389,1000,507,-625,684,301,227,357,462,789,907,-796,794,614,917,-436,1000,-1000,266,662,-628,176,-1000,1000,25,1000,-820,756,109,743,652,-300,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-196,42,831,1000,751,-355,-875,-1000,1000,1000,-923,1000,1000,588,873,-1000,-164,1000,209,-1000,-693,95,-233,-1000,-734,-1000,826,517,-102,-981,1000,460,-207,-995,1000,-160,84,30,44,716,1000,847,-318,-56,1000,-1000,842,228,-1000,440,-1000,-1000,-1000,-1000,1000,1000,-1000,-11,245,401,578,-58,950,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-1000,196,911,-939,1000,-888,1000,1000,-1000,248,1000,-1000,-1000,259,598,291,-118,-1000,-1000,1000,652,-1000,1000,-889,-517,1000,-1000,1000,880,1000,372,-1000,302,1000,1000,-190,1000,-526,1000,-1000,521,-1000,-295,791,-1000,1000,-1000,819,759,-1000,-1000,-504,320,1000,311,-1000,149,-1000,363,1000,-228,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.String:MTU1", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-673,-350,9,15,280,-755,-524,-316,891,-596,339,-1000,270,286,1000,-1000,-218,-326,-322,1000,379,317,645,-1000,-1000,15,-42,421,1000,-81,1000,895,1000,-65,1000,400,-327,594,520,873,633,20,28,624,-114,-173,464,644,-1000,-17,-587,-1000,-1000,-4,993,-510,314,-391,937,225,1000,138,28,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-1000,-761,-323,-521,807,784,-409,125,-575,-870,395,1000,-650,63,805,-281,49,-1000,-611,-1000,-173,239,1000,-1000,-294,354,-970,169,341,470,928,-50,523,91,-162,684,301,227,357,462,274,677,-1,794,614,917,-755,365,-1000,266,143,-802,514,-1000,51,25,168,-1000,756,593,40,652,-300,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-1000,-703,130,1000,1000,1000,-1000,-335,189,25,-919,121,71,-1000,1000,-977,-1000,-1000,-1000,-1000,1000,-440,1000,-238,782,-114,-183,608,45,-678,1000,1000,199,1000,1000,1000,572,-1000,1000,-1000,-480,-927,1000,1000,-723,-414,716,871,913,-216,-733,400,-57,235,-600,1000,-1000,-191,1000,877,-205,78,-80,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDMzNg==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-549,-606,-999,-1000,-1000,-297,1000,-1000,1000,-224,17,-1000,-133,513,-656,974,931,-166,645,370,173,403,-576,1000,51,806,1000,-537,-337,-877,-400,-1000,-340,-239,188,-673,-14,-291,152,95,-61,151,-265,-1000,460,-377,1000,-1000,-173,75,204,145,-1000,168,-236,722,-1000,1000,-724,-622,-302,715,169,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{526,344,-482,180,-766,-403,-571,-1000,1000,304,-990,1000,1000,204,-29,-1000,-562,1000,296,-357,-324,651,-479,-680,-628,-1000,950,-472,1000,-253,777,825,662,-995,-178,-1000,-224,444,-25,1000,1000,1000,-1000,39,1000,-1000,1000,632,-1000,723,242,-602,-833,-1000,1000,588,314,31,-90,-379,1000,-1000,1000,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.String:IEdNVA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-338,-835,1000,-423,847,816,1000,1000,191,-646,398,-174,-110,240,403,-623,872,1000,-577,-185,464,-1000,1000,897,-282,1000,-846,-1000,-1000,-1000,-1000,-824,-285,-515,-760,-491,886,804,274,-1000,-621,-415,608,-1000,1000,-498,-104,-722,-1000,936,291,-1000,-894,-1000,703,1000,-1000,1000,1000,1000,-1000,-41,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("java.lang.String:MjQ2XyAwMA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-675,-715,-719,-19,588,1000,-1000,705,93,-546,1000,-994,-110,626,-1000,-767,1000,480,-502,-185,29,-1000,943,678,347,827,-702,-803,-1000,-1000,-1000,-941,101,-515,220,-113,1000,804,274,-1000,-106,-460,608,-1000,1000,-57,-523,-1000,-1000,560,749,-936,-530,-1000,-27,1000,-1000,919,1000,798,-1000,-268,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.String:CTU2", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{880,-465,1000,1000,-397,-996,-520,1000,851,119,-134,584,-400,-747,1000,117,433,-259,-199,-747,-536,787,1000,-285,-329,1000,645,-1000,1000,1000,-420,634,1000,-371,378,307,-859,-970,636,41,-251,-1000,484,-872,1000,223,716,696,58,367,-289,179,-673,-60,990,1000,803,630,419,639,425,-717,-394,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("java.lang.String:MTJcXw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{829,413,-118,451,603,372,991,-1000,-822,-310,-190,330,-58,-1000,248,-994,429,1,-939,-609,-1000,340,-628,-618,-335,-236,400,-1000,-555,-1000,-1000,-197,-1000,-526,-640,-633,447,1000,1000,1000,-849,722,1000,233,312,967,1000,945,-23,-1000,1000,236,-392,1000,-1000,-1000,1000,-1000,1000,1000,323,-1000,1000,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-777,-370,808,803,185,43,-953,602,1000,-135,976,388,1000,854,-2,942,102,-834,-532,-904,-857,1000,837,974,-484,514,-391,-478,496,1000,735,854,954,-517,-456,-1000,960,-1000,-1000,-492,-84,723,1000,-738,1000,-1000,-214,-1000,531,1000,513,-494,261,-212,303,-1000,-356,299,946,-1000,-587,784,-857,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.String:MC8=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-571,429,-91,-949,227,898,209,-829,1000,-305,-214,140,478,-1000,-304,168,192,-441,198,-543,-1000,478,-484,1000,-641,-1000,629,-1000,-706,-256,-1000,-1000,-832,-851,-165,-929,-812,411,1000,1000,-1000,-678,1000,-902,-649,1000,369,1000,365,-1000,21,-1000,-1000,1000,-1000,43,334,-1000,1000,1000,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.String:NzU1Mjcx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-103,-755,1000,993,470,-625,-250,-1000,168,-244,752,-1000,-622,509,-740,707,731,-582,-450,-930,-555,-589,211,554,760,-1000,-320,-597,-1000,-498,235,-1000,417,-1000,1000,414,1000,845,635,-354,722,-1000,1000,-348,-158,743,-455,-1000,-92,563,1000,759,-1000,-1000,-673,1000,100,765,1000,-720,-578,538,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{953,-401,480,861,374,267,790,-427,813,-228,353,622,-682,-494,286,-766,811,-311,461,-463,-630,609,188,858,-240,-169,676,-998,298,798,-751,113,-737,26,379,649,563,704,940,743,116,-80,782,-173,493,967,711,767,-217,-778,281,-63,-646,-46,-596,-288,193,-942,732,625,851,-779,0,-874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-308,300,-921,-710,76,-205,-647,-916,-604,-781,-767,-99,-518,902,-924,-79,772,-701,-364,-421,-119,-780,247,919,-298,588,400,-61,-967,-722,-902,169,853,416,456,661,856,88,-652,342,609,-155,-381,-742,791,-380,-652,-415,-619,694,973,-459,-633,-778,695,-719,568,496,-486,-271,511,575,-503,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{378,111,-1000,342,246,667,-1000,395,-1000,-422,-297,1000,-406,1000,-83,752,869,4,910,-656,658,-763,15,901,370,457,-655,-49,-793,64,33,729,16,867,461,-489,1000,1000,-460,-786,365,1000,-846,1000,-786,1000,649,-1000,-1000,578,1000,1000,1000,405,-286,689,-99,725,-902,82,-1000,273,-353,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,-339,534,73,-408,-451,-764,790,958,344,-40,-581,155,1000,7,-1000,137,1000,-717,-580,-1000,654,1000,-167,580,-206,740,-685,1000,227,-876,18,-545,-379,878,1000,160,548,-51,-731,-68,124,-1000,-688,627,175,1000,-560,-1000,731,220,-173,1000,-1000,546,-394,861,61,-353,1000,-1000,793,-147,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.String:LTg3NDk3", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,-471,874,265,177,-670,254,-220,1000,-456,-152,-805,-1000,77,622,-2,-90,113,161,-246,-152,-3,-371,-548,946,655,-1000,-908,916,-471,-663,174,-817,-989,4,-123,-553,-453,-332,-741,362,-719,268,-523,687,1000,104,-149,-432,831,235,993,885,-418,-335,-430,1000,-521,365,139,-145,997,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-96,621,-983,-709,-691,352,531,1000,-842,3,196,-523,1000,417,-742,997,741,1000,838,-193,-508,-1000,-1000,707,-1000,-465,773,-1000,633,-746,908,-125,-308,-673,-735,-244,-813,-1000,-1000,678,-83,157,186,-273,-571,736,-612,-620,-360,880,-487,-325,221,-41,-643,-503,1000,622,-445,1000,-601,-820,473,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.String:NTc1Mw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-623,-663,-575,920,-125,-861,-145,-485,577,-823,461,115,-523,97,-378,652,-559,-469,-298,-731,117,-592,779,470,-631,-171,-477,667,757,-212,-518,140,567,-390,-712,-468,-65,113,-364,629,743,-834,275,-567,97,-424,712,291,-510,-923,830,-800,993,-487,236,321,227,320,-249,185,261,845,-798,-861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-867,-322,-1000,-1000,263,1000,1000,639,-735,-1000,-1000,1000,1000,-591,-1000,-430,459,-428,943,1000,688,-1000,-1000,1000,-1000,-810,-358,-357,-1000,-1000,627,1000,729,798,98,-1000,485,-1000,-1000,33,791,842,1000,1000,929,623,-400,-965,1000,59,-954,1000,-170,1000,-257,349,114,1000,1000,934,48,-1000,796,-554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,159,-1000,615,-913,540,-936,865,-1000,539,193,1000,1000,597,-1000,-1000,1000,-1000,-940,1000,-871,-1000,599,1000,-1000,-986,-453,-408,-1000,-1000,351,1000,1000,-322,1000,464,1000,986,-191,896,-122,1000,-1000,1000,-565,-1000,669,-445,1000,585,-1000,-615,-275,-1000,904,-81,-474,940,-1000,33,58,-1000,1000,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{298,290,740,330,903,-423,-111,825,-41,-509,-100,-673,642,-270,851,850,-189,49,-641,-281,-504,-724,873,384,240,910,-352,-641,186,1,-5,-304,786,531,723,448,63,694,991,-699,-550,-899,178,706,432,254,856,689,431,393,443,804,-325,-780,-289,-337,171,955,963,879,308,-342,-616,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{400,-193,528,1000,-989,-1000,-846,458,390,995,272,-1000,501,1000,790,-400,219,1000,-1000,-1000,-1000,733,477,-482,1000,25,-851,-1000,1000,521,-1000,-92,-361,-1000,1000,1000,-286,1000,429,-303,-372,184,-1000,-1000,-185,1000,1000,188,-1000,1000,-878,-1000,1000,-1000,845,-447,1000,-1000,-889,20,-639,1000,-117,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.String:NzYzMzQy", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{341,-471,763,1000,177,-1000,254,-1000,-14,1000,1000,-1000,-1000,77,1000,1000,-90,1000,-1000,-246,-464,-3,1000,-548,1000,1000,224,-1000,1000,-165,-1000,174,-817,-1000,4,1000,-1000,774,130,843,347,-1000,66,-523,-1000,-1000,987,-236,-1000,831,720,-1000,974,-1000,870,-1000,1000,-345,-1000,-1000,1000,915,-709,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,-436,1000,1000,926,-314,851,-1000,1000,-619,-669,-737,-835,-753,811,142,281,782,-738,768,-243,63,616,-354,357,595,39,-1000,654,-748,-1000,-1000,-965,-655,-991,-386,-886,129,-265,-496,266,-980,532,395,1000,1000,-408,-1000,725,1000,-61,1000,1000,-322,-986,-1000,1000,-29,139,-786,205,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.String:LzQtMTZc", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{621,-498,413,674,-412,-145,-4,472,63,801,-784,599,-901,688,-105,252,280,273,994,990,-950,412,86,967,-186,90,208,66,861,119,-740,733,-46,-474,317,-285,-446,-827,759,-982,91,438,-60,418,403,-20,-717,638,577,491,-841,-101,-272,154,-343,976,-3,73,-533,-872,-1,-689,478,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-303,-285,-227,-1000,-1000,249,-1000,-1000,900,-1000,33,-1000,-1000,897,-589,-788,-1000,1000,-140,-1000,-598,562,400,-616,1000,423,1000,-1000,-877,819,472,185,1000,630,1000,-166,537,821,325,-662,-52,1000,-147,-269,1000,-619,1000,-953,-300,-1000,1000,647,1000,-901,673,1000,1000,-578,-322,-764,1000,32,1000,-896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.String:KzEzOTM2NQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-523,-734,617,-139,942,903,-1000,-1000,600,1000,241,-932,324,-576,1000,98,-1000,-719,922,-124,-272,-330,-174,190,-523,1000,-522,651,143,-1000,612,-648,-1000,-477,-913,-123,499,-209,-128,842,395,1000,-544,-1000,693,358,400,744,795,-721,-1000,-1000,-1000,1000,961,-1000,-801,-178,-762,799,337,-63,-990,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-617,158,1000,1000,-130,-409,1000,375,1000,-1000,1000,-1000,-653,1000,105,1000,507,1000,1000,-868,-1000,-275,125,-1000,1000,-406,1000,1000,-314,-1000,333,-958,-951,-546,-657,-1000,-764,626,-306,-901,1000,-1000,-860,1000,-347,-1000,1000,1000,-1000,-1000,459,-1000,1000,1000,-967,-1000,130,1000,-914,-770,-9,-745,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-497,-448,604,193,-755,-473,33,-513,-583,-707,-318,109,-91,660,132,-829,-482,-41,-93,14,-213,711,-297,759,641,-719,-568,-312,-563,156,343,-904,887,244,676,647,403,43,-160,-580,-125,-102,941,801,602,-332,651,186,-816,86,831,46,540,-149,156,324,-112,66,21,386,534,-979,-549,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.String:LTQ5MTE=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{747,-722,617,491,-155,73,-587,596,-903,681,-626,344,324,534,334,-437,-1000,-596,170,205,-29,435,-788,1000,23,-929,-768,459,545,916,-604,-358,457,-460,1000,-497,68,-668,-673,-477,-442,524,1000,1000,693,-284,-580,744,147,670,576,-807,590,-807,-474,527,798,341,-1000,163,-504,-1000,-739,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-674,845,578,-1000,-84,-940,-234,-1000,-888,-894,-92,-85,1000,-1000,-656,-1000,401,-1000,-1000,-88,1000,-754,1000,867,-22,279,-508,-389,1000,-211,-357,1000,-230,1000,-199,780,487,-1000,-1000,992,1000,1000,0,375,-958,411,624,-815,-224,1000,-224,-1000,-1000,441,973,1000,646,-1000,-71,373,-1000,80,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-59,102,959,-1000,-1000,-241,-794,36,-665,-1000,1000,-1000,-1000,-44,-1000,758,-1000,-1000,-1000,757,601,1000,849,337,1000,-702,11,-780,330,-416,-1000,-1000,-1000,1000,1000,498,113,810,1000,-533,-340,-1000,697,1000,-1000,92,-1000,61,-182,395,166,1000,-1000,-1000,1000,1000,-729,-134,411,-337,-1000,-896,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{663,370,910,1000,360,432,-1000,-891,-513,1000,-412,-3,-1000,-1000,1000,-331,-616,-1000,227,1000,622,16,-413,389,-881,1000,-1000,1000,1000,-468,-1000,27,-260,-400,-1000,-601,-823,585,-1000,314,-1000,943,-302,354,836,-506,-1000,1000,1000,1000,-1000,-1000,-590,428,217,-833,-482,596,-99,673,-1000,-463,-1000,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-819,102,-226,-109,112,458,-1000,-368,1000,-1000,674,41,-1000,-613,1000,1000,856,-1000,-205,-336,-620,-586,-486,235,1000,470,-515,1000,-993,1000,1000,411,-405,-1000,-199,1000,790,-52,429,-697,534,1000,-1000,1000,333,1000,-144,-1000,-289,1000,125,-499,-692,401,-231,-1000,-192,-119,-1000,240,-225,-1000,155,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwMDAu", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,849,-593,-201,371,477,61,135,1000,-685,671,1000,-163,-416,-655,322,-339,820,9,-221,1000,-99,-464,852,183,-214,-1000,1000,370,0,-926,1000,-934,774,1000,118,833,-913,-568,251,306,-845,-784,-502,-1000,-681,-487,-203,531,1000,-556,1000,-1000,-626,-60,565,110,1000,438,-217,-1000,455,1000,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDI1", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-217,-333,-7,-1000,1000,-81,-270,249,-258,125,-770,-1000,589,-1000,-67,1000,-294,-1000,924,1000,650,-373,-516,530,-673,-1000,180,-1000,947,662,935,-1000,-150,699,-1000,-181,-675,543,-62,-1000,-322,541,-195,-642,699,656,783,334,-1000,-1000,-601,-1000,1000,157,1000,-340,1000,-135,-973,904,914,-787,309,-257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{833,1000,-739,492,413,-18,-236,1000,924,346,1000,1000,-592,892,-1000,228,101,-441,-412,41,-70,950,-549,-1000,819,-347,-662,717,619,-1000,-1000,558,-1000,-744,547,0,897,-506,-588,-229,-526,-753,104,50,-1000,-1000,-605,483,648,488,-476,1000,-1000,-513,-375,-140,-709,1000,1000,-290,-755,414,948,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.String:LTEzNzE=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-641,-401,201,-137,-979,-111,1000,299,-1000,861,1000,-681,98,-645,-85,483,-836,1000,889,655,409,532,-1000,450,-1000,-953,-407,-202,-804,683,-992,-1000,-575,383,-627,-1000,507,1000,124,540,-1000,-627,82,264,690,-502,305,-792,553,-583,-452,-113,-920,1000,602,-61,1000,333,906,650,355,307,-189,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{227,-1000,-257,-438,126,-532,10,-526,680,-783,-1000,-208,-519,472,1000,-1000,-56,-810,-160,-316,-164,-1000,540,-376,1000,655,1000,148,629,1000,1000,-1000,1000,452,945,759,-723,-565,1000,963,188,-92,1000,-397,1000,1000,945,604,-564,-258,1000,344,137,400,-794,-38,-422,-1000,-1000,-115,112,-1000,-810,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{393,-707,767,-334,-147,-576,-406,-887,-512,-31,-803,242,-429,712,299,-681,463,-530,274,118,-814,-480,-532,69,503,-16,781,-357,640,1000,762,-12,787,-186,1000,-752,-834,-819,1000,181,744,-1000,148,10,692,1000,629,676,-672,-171,546,726,980,-73,-476,-268,-397,-586,-1000,-536,-428,-137,-359,-116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("java.lang.String:LTQzMzM0MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{269,544,-567,433,-979,285,519,462,-334,710,530,-617,-662,393,-762,-917,-283,831,-415,-745,298,-571,-234,-224,-67,309,546,555,-804,683,-547,-102,-419,569,773,597,-776,483,892,996,866,-615,457,68,129,-332,94,-776,813,-747,948,965,886,943,-798,-195,127,141,906,-98,-53,-268,-71,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{414,428,-434,657,-1000,-703,752,1000,-1000,1000,671,481,721,1000,-1000,-1000,790,1000,344,-180,-1000,862,-620,-247,-403,-96,380,-1000,-3,-583,-1000,-372,159,1000,455,-1000,-380,638,236,149,-686,55,1000,-434,491,-943,722,43,249,-823,1000,1000,1000,426,-448,989,102,-201,-73,253,-975,939,-1000,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-514,-748,-68,509,760,718,-534,-323,-297,362,930,-768,665,199,274,258,765,828,329,844,-638,-246,-760,-86,-447,-474,-83,171,547,964,938,-795,203,-215,-420,-963,-304,789,-666,-646,-676,421,-849,532,-205,24,-82,-843,987,374,-761,-760,146,-803,443,518,-255,-714,-73,-611,-184,-454,451,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-283,-63,383,216,-979,-861,1000,1000,-1000,861,1000,1000,827,-303,-455,483,-332,1000,1000,964,-497,1000,-768,541,-1000,-969,-841,-1000,-106,-425,-1000,-832,-629,383,-1000,-1000,675,950,-410,-981,-1000,112,144,126,342,-1000,179,-62,1000,-459,-452,256,-920,768,988,1000,1000,553,822,670,-406,1000,665,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-115,-585,-83,-65,-563,-16,551,-983,-417,-27,-1000,-873,292,695,634,-171,-303,349,472,673,1000,-587,212,1000,-637,-495,313,-819,-663,1000,1000,-1000,1000,913,-91,-857,-925,110,1000,583,-225,-739,1000,-1000,1000,1000,629,871,-580,-708,101,-358,-1000,76,300,-72,648,-1000,-1000,90,488,-833,-877,-256}));
    }
}
