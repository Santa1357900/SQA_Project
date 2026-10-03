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
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(double,double):int",
            new int[]{-57,783,98,727,-917,-196,-67,956,625,542,-53,724,530,-318,-190,-548,-587,900,-110,-511,489,139,-138,992,552,342,-640,-29,172,-409,-311,-175,104,-885,987,983,-690,207,-923,649,-126,-253,800,-962,-333,706,-425,5,620,810,577,514,-883,-665,-925,986,-768,927,164,-733,-779,283,-544,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(double,double):int",
            new int[]{-754,-185,342,-81,202,-420,-705,-214,-992,-310,-838,150,-123,847,374,54,995,-641,125,-72,-712,897,-239,-42,690,-22,672,238,-157,-437,943,999,929,729,-651,499,745,-88,171,535,42,880,-168,937,-315,301,322,924,209,-471,-972,713,-966,-938,-293,-579,722,37,-906,942,476,-948,-120,233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(double,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(double,double):int",
            new int[]{-58,-121,11,-956,219,521,953,846,-886,633,-343,658,-363,53,39,-958,-412,499,-428,823,264,-27,-148,702,55,878,-353,987,-455,443,357,926,492,-428,167,408,499,-177,75,-821,81,-141,441,131,-635,416,754,-283,-295,611,-103,87,-869,221,-655,150,-260,489,-532,462,-121,-67,-630,-472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(double,double):int",
            new int[]{-972,347,-927,-664,784,710,-407,-335,911,788,-272,-994,-79,886,233,107,153,872,148,-771,-644,-493,288,999,242,-525,8,-155,-453,-846,-890,935,-66,-304,-874,828,-530,-464,-941,419,326,129,218,173,869,385,732,145,-620,-936,992,799,-761,213,380,-700,621,620,-599,-417,518,208,-417,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(float,float):int",
            new int[]{-278,-989,-547,-244,773,-521,-996,288,-637,-891,-844,781,-426,-921,-3,855,22,-831,-43,-813,-493,758,-709,-858,-750,255,-365,-77,-884,810,-494,592,-950,120,424,-539,-887,941,325,274,-30,533,359,-611,444,-349,-185,727,-148,951,-77,-372,231,715,579,988,-719,632,678,-990,866,80,645,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(float,float):int",
            new int[]{273,-216,-975,-557,50,-947,-478,320,-761,877,776,382,66,115,320,779,-236,-321,680,845,-922,226,-640,779,-756,160,-405,685,-265,965,-817,988,-86,-167,438,-641,886,-645,-624,-747,-690,653,611,911,678,-269,120,-759,-293,-933,-970,402,-166,-501,-116,84,562,-683,461,-492,403,135,-823,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(float,float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(float,float):int",
            new int[]{826,-239,994,-960,-729,683,-18,513,42,-689,-323,-332,364,-687,859,601,-860,-281,-861,736,-341,842,174,216,39,-199,568,835,-498,946,577,840,-551,-504,479,55,-570,439,-473,-275,-425,-596,784,-732,832,-410,803,-360,428,-912,110,467,408,-45,789,-760,-241,271,-979,-90,-277,-930,-564,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "compare(float,float):int",
            new int[]{-655,-446,247,-191,-787,91,701,-81,-34,812,414,552,502,-594,430,-664,661,125,-565,73,274,-385,379,-11,468,904,600,890,947,-529,-780,-822,-158,314,-718,-209,-153,726,-819,-875,-156,79,306,-306,971,-656,-518,929,189,-692,-190,-178,815,-532,-206,396,169,-543,277,-75,-458,-481,-370,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MzQ2", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{-316,346,-270,-806,861,-223,-982,-446,-775,166,-310,504,-162,-179,-787,248,-496,-392,-352,24,654,991,581,-465,-234,931,-806,904,-542,947,572,-199,620,-798,-644,-55,169,576,877,322,-611,336,36,-837,-811,231,-656,927,56,-368,-185,425,29,683,-359,-476,-433,-666,9,855,-149,-415,792,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.math.BigInteger:NDQw", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{131,440,308,-502,-691,491,-442,-218,-991,426,821,225,767,-648,772,419,920,217,-962,-32,-221,134,781,-23,757,-396,687,-529,919,-122,-45,170,833,847,877,-6,-359,-932,-895,-375,788,765,-184,-462,-986,24,636,993,-117,-260,-592,-322,-630,385,669,913,864,-874,-950,636,301,-546,720,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Double:LTMxMi4w", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{-38,-312,125,-898,144,-220,-633,-500,446,-524,181,-892,-976,306,-171,-192,-682,180,-920,-183,-838,605,510,293,809,791,559,79,483,363,-683,331,407,-745,919,522,398,528,-724,362,46,-709,448,548,283,696,365,954,687,60,879,-177,283,568,113,-111,773,721,972,925,159,737,321,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Float:LTgxNy4w", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{-950,-817,-923,-947,906,-464,753,163,-487,-656,-759,-357,876,693,-713,167,67,431,-771,840,-735,-403,-910,132,619,210,733,844,-205,493,-403,-924,756,914,116,692,-769,-483,786,-41,-332,-51,14,162,-3,-696,390,860,466,-554,822,-856,-982,834,-890,785,185,36,-67,-547,-663,-909,-994,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:ODE=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{39,81,1000,-879,-1000,65,148,-720,911,1000,619,-723,154,-637,1000,299,-591,922,-84,-7,656,481,233,-11,1000,182,-91,-839,76,-112,21,924,-650,-847,1000,61,36,-260,526,839,-133,734,-22,-581,956,-1000,366,500,8,54,-788,-1000,-762,145,597,-272,1000,-1000,228,260,-599,391,-594,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Long:MTAwMA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{-429,1000,-400,261,496,1000,-1000,-638,-1000,-427,958,56,1000,1000,58,876,1000,732,338,410,-233,-1000,-1000,685,1000,51,-315,-400,1000,-64,273,1000,-117,250,105,-736,1000,176,1000,1000,950,159,-250,1000,-479,830,228,527,-455,284,303,4,1000,539,130,-564,1000,408,390,-746,1000,1000,-952,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFLTQ3Mg==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{662,-1000,-1000,-475,488,534,-1000,36,1000,177,-406,-1000,1000,826,-1000,454,1000,1000,-1000,682,-1000,421,-1000,-459,1000,1000,807,1000,-220,1000,-1000,1000,302,-669,1000,1000,18,-359,1000,860,820,579,450,647,-573,257,102,-607,-351,-987,-848,1000,616,-864,-297,-414,99,838,-176,-6,-381,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Long:NjI4", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{649,-628,1000,191,-1000,-189,1000,249,1000,490,-827,-704,-394,810,280,-26,153,39,-1000,-459,1000,-1000,-1000,-1000,-534,456,1000,-915,1000,1000,-290,56,852,-1000,516,1000,-343,-740,478,1000,-465,579,1000,977,-1000,-741,1000,1000,-1000,164,-603,1000,-1000,-864,1000,1000,350,1000,-1000,1000,-411,-182,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Float:MTAwMC40MjM=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-155,-1000,-866,423,660,754,-931,-204,481,175,-837,-1000,522,245,-124,-199,942,822,-396,-445,16,13,-857,365,1000,618,568,893,389,699,61,-367,-3,-483,601,-56,176,-372,1000,178,-44,110,-1000,-55,-290,435,-884,-955,-389,-707,-931,700,567,-365,-513,369,435,806,-248,-46,-68,1000,-795,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{125,-93,561,538,1000,13,-208,-58,-196,-400,-1000,-451,793,-729,156,-1000,822,1000,-325,-635,-400,1000,715,-749,786,704,264,735,228,566,158,188,-667,-707,49,-309,-178,-620,322,591,238,93,-431,1000,-948,400,161,-236,-306,-1000,348,122,1000,524,551,-131,-309,1000,205,-490,1000,-1000,-627,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:MTEwLjA=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{905,-110,860,-801,392,413,-929,-332,200,1000,-172,-481,401,-746,602,-345,756,65,-188,-340,1000,284,-22,-647,577,35,1000,-262,607,1000,-825,-673,-391,-533,857,155,-481,-523,1000,295,-1000,560,-223,318,-851,-1000,679,841,-276,-68,195,234,-281,488,568,530,70,1000,-895,467,1000,-23,-700,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{125,445,778,129,1000,239,-101,-237,-59,-177,839,-1000,-1000,319,857,-1000,-700,-650,746,-1000,470,-464,-429,129,-164,-510,335,-1000,812,-658,-1000,-864,-566,-500,-569,-550,179,-1000,-413,-36,-844,1000,-390,-113,-31,-221,118,-44,-611,-190,1000,413,1000,-601,586,612,-204,409,159,-123,396,1000,158,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuOTlFMTMx", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{214,499,-111,129,-623,1000,-1000,54,444,400,1000,-317,-1000,319,284,-133,-1000,-992,531,-1000,793,-547,-584,553,-661,-1000,-145,-603,-486,-1000,-390,-864,-566,-497,-357,-835,632,130,-819,500,-767,1000,929,-113,778,-1000,689,150,-842,1000,1000,-199,1000,-601,465,838,-204,-669,212,84,-1000,1000,645,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Float:MjExLjA=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{41,-211,860,-810,529,413,106,-689,-777,796,-902,-965,281,3,-223,16,635,-458,931,-577,783,336,-571,-415,394,827,819,-28,607,-68,-901,55,-338,-224,295,155,-135,-523,842,-59,-221,560,-300,894,25,-942,679,-260,84,803,439,658,-878,970,344,859,-26,570,459,-949,192,-778,-209,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-659,445,778,129,963,-29,400,-686,-586,-177,918,-1000,-1000,319,790,-855,-1000,-650,250,-1000,69,-464,-104,-118,-164,-38,-152,-1000,870,-658,-1000,-374,-690,-500,-569,-550,179,351,-413,-221,-44,1000,-390,-548,-31,-213,23,-367,-611,-190,1000,586,1000,-864,586,612,60,595,-332,-895,396,745,-986,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjkz", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-28,293,-488,-760,693,-938,-880,-154,-267,-378,91,-949,-698,17,-547,-482,-338,-723,-173,-501,-610,899,480,-821,-991,252,-385,-401,-261,-222,101,-638,943,90,682,-110,148,369,73,-453,464,-175,296,588,-325,-597,89,625,-690,-573,-673,38,604,373,976,600,-576,639,324,487,-69,797,-36,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-369,-270,113,-502,-773,679,-394,1000,1000,186,-298,-83,-542,1000,755,802,-543,15,-1000,-861,1000,-981,-1000,-236,-718,-844,1000,-1000,174,-416,966,-256,1000,-1000,20,396,47,-825,-29,1000,-253,1000,1000,1000,-90,-1000,1000,1000,-1000,1000,-328,571,-1000,-1000,1000,1000,435,-575,-673,882,-164,166,-629,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{1000,-211,-382,417,-711,53,-1000,827,635,144,484,-659,281,1000,534,-356,40,-518,-1000,-1000,107,-636,-1000,636,394,-1000,1000,-205,-218,-1000,317,-1000,-140,-1000,295,745,409,-15,-235,1000,-269,-750,-300,1000,-64,-1000,315,-260,-1000,1000,809,46,-699,-1000,344,416,650,-418,-580,711,192,959,-209,-519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEyOA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-1000,-70,513,741,639,1000,-452,-55,-473,-153,699,-903,-49,-1000,368,640,1000,-9,-1000,1000,501,-410,-1000,-1000,-993,480,756,6,-1000,1000,1000,-1000,761,-436,579,322,409,-1000,1000,665,-768,-176,343,103,-1000,895,255,-265,-122,1000,-1000,693,341,479,47,-264,-411,702,-1000,1000,893,697,1000,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{433,20,-997,928,-903,778,399,417,-82,-1000,-42,-210,739,320,400,600,1000,1000,-636,-767,-1000,319,-913,-160,912,-114,1000,63,-164,236,98,-1000,-793,-426,112,934,-583,-446,358,1000,-868,-957,-1000,1000,-1000,-27,-970,-577,-1000,574,650,-610,639,669,-812,481,-47,-914,-777,391,1000,-461,727,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Long:LTI1MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{985,250,561,538,-404,-151,442,343,-783,852,656,-205,-711,735,473,-1000,-720,463,713,-635,-143,-68,715,-236,855,405,-623,-695,152,347,-1000,554,-706,-173,-710,-518,-285,265,-215,-367,-553,1000,-1000,-542,437,665,-651,-1000,148,-1000,826,274,1000,163,-170,167,-309,513,202,-490,1000,1000,626,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFKzQ3Mg==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-202,1000,1000,469,591,-228,1000,-479,-962,176,737,-1000,-1000,977,1000,-1000,-1000,-864,1000,-1000,930,-1000,743,594,-956,496,782,-1000,1000,-1000,-1000,-418,-1000,-118,-1000,-1000,1000,-1000,-1000,-201,-1000,1000,-868,-1000,726,-694,-261,-331,-934,-218,647,703,1000,-1000,1000,960,393,1000,947,-737,972,1000,471,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{-519,-674,-200,-241,473,29,846,377,439,-266,-835,-769,901,837,482,-735,415,788,325,509,-684,-616,884,340,315,-119,-867,-1,-889,829,718,-221,-699,588,-715,652,503,598,-415,-523,-170,638,930,380,668,-442,-574,453,-989,259,768,-558,541,61,300,791,-881,-978,-414,-928,-645,-749,768,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{769,458,820,-377,378,-610,966,-69,833,437,-690,-439,516,-334,484,-34,660,-328,-452,-406,-50,-272,665,792,259,872,914,52,643,541,236,-749,-169,-562,629,-146,-886,404,-601,435,-260,-720,543,369,-286,929,-886,991,253,-66,-154,183,29,825,-785,918,-143,-890,-365,-953,-556,-117,-450,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{-701,896,-776,738,-605,683,315,727,714,924,-151,19,819,-631,532,198,309,-671,-934,-481,-631,-104,486,-644,-784,498,-676,-442,-98,-760,-112,183,-993,508,107,-604,-558,449,-949,-301,795,-914,-495,-857,-598,-911,-218,652,-262,-497,-658,-785,-445,114,724,-699,-263,527,-211,-415,804,-437,-645,827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-778,162,-907,-263,-8,996,-433,194,-1000,166,455,-188,-1000,-255,577,557,-213,1000,-460,538,-25,-816,99,-815,-543,-577,1000,802,-324,908,1000,1000,-531,75,1000,-468,-475,-286,1000,227,-452,-985,-1000,-324,766,-42,300,719,-711,201,-28,148,-529,254,-512,8,-1000,362,-1000,-438,-174,168,388,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-297,-1000,1000,-384,-889,1000,0,-702,-1000,0,-451,-295,-1000,0,1000,-529,-304,-903,353,354,1000,-18,562,-509,-1000,568,-991,968,567,554,-822,-475,1000,28,1000,0,-107,689,0,-127,-73,664,143,-24,-286,-1000,74,0,-221,585,-1000,0,-1000,-619,0,-459,-260,290,-543,125,0,1000,1000,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{553,1000,665,-679,39,840,161,-1000,-623,-24,1000,-392,-1000,-1000,1000,769,-925,395,-834,151,-1000,-1000,347,-220,-1000,-60,769,-606,-544,1000,-411,872,-850,-1000,174,-774,709,865,1000,1000,-1000,182,-1000,691,-1000,1000,-278,690,1000,880,-1000,-428,550,-703,-469,-427,500,-527,-332,1000,132,-370,-563,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-190,385,-827,-618,730,-781,635,249,511,-839,-444,-78,609,765,-811,868,740,250,-878,340,-342,908,-294,-767,295,-936,267,-411,-274,-960,412,-815,-508,-393,-136,12,-487,-484,-58,-202,987,-95,-939,233,516,560,-553,30,991,-633,619,-430,716,-390,-843,482,697,207,-252,265,-932,-520,-406,772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{73,641,-551,-252,494,1,-37,-859,-904,-613,391,651,-406,-237,187,103,12,672,-290,-374,-106,-184,276,-1000,257,201,144,842,354,477,688,1000,115,-626,399,135,-826,1000,393,-173,-1000,-1000,-171,287,1000,1000,-908,-755,1000,-543,-251,-416,268,-281,357,-451,-321,-429,-702,71,555,589,284,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-195,833,435,684,632,-585,554,-30,810,-550,-998,316,-831,-353,-788,-443,-925,832,-618,-570,-801,-28,-374,-465,447,172,598,-488,-220,-93,199,-309,815,777,-879,822,709,201,-344,-129,-931,-350,-544,-670,-580,208,751,4,-921,-372,-102,-178,-759,318,67,-146,-504,205,-590,-572,79,-167,71,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{1000,-843,-526,1000,-627,-1000,-423,391,-397,-476,-1000,1000,-534,-223,-11,-256,-1000,-415,-790,570,-1000,-158,-121,-242,876,705,-722,122,670,1000,-1000,-557,-366,-495,-112,-573,-34,1000,-793,798,-173,187,-849,234,621,1000,-105,-928,-717,-856,-1000,-44,-20,-150,-1000,342,955,288,194,-885,9,124,831,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{633,-424,589,1000,-8,-1000,1000,-622,1000,-398,-1000,-1000,-1000,-1000,-409,-1000,-1000,963,-1000,-753,-913,-1000,-132,-354,-653,834,788,-673,871,-556,-609,-475,-836,1000,-1000,1000,1000,-1000,-1000,-334,245,-307,-556,-1000,-880,-430,1000,622,612,780,1000,84,197,-414,340,-980,316,-139,-1000,-100,317,218,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{393,177,257,357,1000,1000,554,-891,-334,129,-536,855,-1000,-1000,904,-443,-448,425,131,-213,-353,-1000,-374,-1000,447,-669,598,1000,-639,1000,606,1000,-589,661,900,-228,709,-840,974,1000,-826,-1000,-1000,186,91,435,1000,776,-453,871,522,1000,500,318,707,-1000,-1000,456,-1000,-394,79,-75,397,-540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-727,1000,-748,-1000,-217,-1000,12,-234,770,-852,464,771,1000,1000,-222,-52,1000,-47,-521,328,-496,1000,-700,-384,1000,465,58,-1000,186,-885,-28,-1000,1000,-1000,-290,1000,-363,136,925,-33,91,650,68,626,30,-19,-1000,83,1000,237,-237,-154,-873,-893,-659,771,1000,-271,1000,1000,-1000,97,412,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-787,-1000,-899,-482,-213,268,-1000,-647,796,-1000,-623,1000,881,630,-250,1000,396,-1000,-576,-788,-955,1000,-224,1000,609,-571,-1000,256,95,-1000,-1000,-309,638,780,-1000,-1000,686,-854,233,-614,851,1000,1000,-1000,591,71,-1000,-1000,1000,-413,-211,-587,-1000,-1000,-1000,-509,1000,375,41,-1000,-879,-1000,1000,-178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "maximum(int,int,int):int",
            new int[]{-573,-94,853,624,977,337,728,690,-965,-494,-790,773,-466,-648,-665,398,-257,-812,367,901,-609,-71,-638,271,-654,78,580,731,-618,-764,381,913,318,206,290,333,594,-340,-35,257,-773,-249,959,48,4,-918,-618,-417,-250,971,807,475,589,129,127,-573,167,-494,448,334,-945,-662,545,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "maximum(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "maximum(long,long,long):long",
            new int[]{421,232,34,-146,-739,-465,-863,947,467,-944,-606,755,203,25,57,-599,27,-905,-881,-102,-909,-103,-656,327,289,47,-47,-74,498,972,549,624,986,914,109,103,-933,55,119,-434,101,807,-737,-974,73,20,-531,-811,-861,-889,-422,-249,-209,910,-602,-699,-441,440,-481,-95,-451,732,867,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "maximum(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "minimum(int,int,int):int",
            new int[]{320,-871,-632,-14,-678,496,-18,-834,-918,916,29,-533,699,-863,-559,-668,-50,-802,963,867,70,-262,-506,852,651,-658,23,479,-403,89,-806,40,755,739,556,640,698,-111,586,-592,-48,-119,-211,-331,912,223,316,-947,668,860,850,613,-5,766,830,715,-82,1,-542,623,372,-358,423,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "minimum(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "minimum(long,long,long):long",
            new int[]{-602,341,956,94,873,-212,-977,207,947,-340,192,-695,-815,-562,277,245,-355,609,822,-720,-406,174,870,-429,72,-545,-13,826,-708,-797,847,-733,62,66,253,816,-121,-627,379,-53,-816,878,-774,-978,-633,-379,817,49,-451,-618,-511,836,-92,-649,783,257,-622,-431,-80,978,920,-540,-409,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "minimum(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "stringToInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTY5MQ==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "stringToInt(java.lang.String):int",
            new int[]{595,-691,-511,22,271,-895,-667,403,-714,-991,79,57,107,-201,-653,-149,863,363,-225,-48,776,177,209,-945,-121,-555,533,973,-968,-596,-330,37,-812,-291,-773,389,299,-811,-240,-19,297,783,701,-164,-798,-995,611,967,522,895,-388,-753,-124,-920,612,468,-968,333,244,-150,384,780,314,350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "stringToInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDUx", DEReplay.run(
            "org.apache.commons.lang.NumberUtils", "org.apache.commons.lang.NumberUtils", "stringToInt(java.lang.String,int):int",
            new int[]{-684,-451,816,-805,-514,156,178,923,188,-383,-714,-306,70,479,699,-499,-373,126,-901,-325,589,-217,897,-999,100,-220,843,372,393,-305,503,-394,-738,-91,832,7,-732,-439,-487,-286,315,938,-811,4,-987,-69,-708,-728,-941,336,-241,-325,-227,508,-368,-889,879,155,-774,572,623,905,-594,-256}));
    }
}
