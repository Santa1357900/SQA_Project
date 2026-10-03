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
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isFalse(java.lang.Boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isFalse(java.lang.Boolean):boolean",
            new int[]{-299,-880,-782,-565,55,166,-985,-50,-400,198,696,-505,-493,-86,588,-206,-134,694,-946,913,-521,-728,-895,-33,-387,-513,398,-31,-5,-657,-8,360,86,-454,-529,-451,-985,-262,928,-444,-606,680,-307,-277,-311,834,-339,403,839,-63,-396,473,-646,495,-36,628,-736,394,-733,-527,980,260,-484,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isNotFalse(java.lang.Boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isNotFalse(java.lang.Boolean):boolean",
            new int[]{-923,-130,-530,-884,765,-66,556,49,492,-863,-784,-180,-369,-941,910,-334,-504,173,114,539,-903,391,655,-196,-558,-447,-881,-278,-57,435,643,928,112,-530,-376,700,-807,-614,37,-354,-364,-827,920,-338,-637,454,372,471,709,-821,-119,-126,566,-209,215,-27,813,-444,241,40,414,-438,-983,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isNotTrue(java.lang.Boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isNotTrue(java.lang.Boolean):boolean",
            new int[]{-247,763,-265,640,-311,-978,-487,-724,403,629,-719,-3,55,779,628,-294,-733,923,-767,790,-402,586,-261,-984,-897,604,-865,-353,-541,160,85,452,-977,-351,664,-709,820,306,-227,-681,-47,-200,144,481,-764,538,-707,-58,913,295,-951,377,282,967,326,-376,-252,756,-259,108,-623,-145,330,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isTrue(java.lang.Boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "isTrue(java.lang.Boolean):boolean",
            new int[]{-539,-326,-218,-13,-670,693,10,-252,253,-515,754,377,-899,743,-194,-270,-392,173,906,196,821,771,-605,521,23,485,-874,-428,511,210,731,36,-687,69,803,-858,682,-444,149,80,47,-749,841,27,-4,570,468,404,801,-752,-233,329,-950,154,555,-915,980,-599,793,-119,304,706,496,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "negate(java.lang.Boolean):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "negate(java.lang.Boolean):java.lang.Boolean",
            new int[]{469,653,-546,423,-42,149,750,-810,-160,-379,-655,-265,660,354,3,491,-236,-178,-136,-641,-525,964,-245,-812,598,261,787,687,-775,-660,149,-468,-536,-720,446,-75,-577,-151,412,-83,545,-523,-619,458,-780,-53,-504,292,496,483,333,604,661,-776,322,612,488,-98,192,189,18,-50,-528,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(int):boolean",
            new int[]{-740,-519,381,486,193,-292,-535,591,-116,-287,-122,255,-990,-67,548,256,301,771,-909,-215,869,521,-698,904,266,428,958,-512,-976,972,32,51,645,-722,954,257,-671,945,105,-213,68,508,-610,-291,-816,-734,445,558,-161,-742,-222,-111,631,-188,-989,-747,-15,265,242,261,706,-782,532,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(int,int,int):boolean",
            new int[]{603,-552,-78,257,355,-387,-702,-628,-67,-3,995,-85,-502,920,747,363,71,-902,916,-597,495,474,-498,-234,-847,343,615,398,-223,14,-778,746,513,-694,-236,103,-426,892,288,-134,-737,168,-918,-645,915,699,-474,391,423,553,828,230,-618,845,-362,636,617,693,150,888,888,443,246,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(int,int,int):boolean",
            new int[]{165,704,996,-221,713,-304,128,793,-482,-143,-184,-927,-923,-867,-265,482,702,301,-426,-742,466,77,561,-904,163,-228,355,735,429,-896,955,-828,-988,709,13,722,-681,-258,16,-871,849,513,-864,652,-139,541,281,673,-235,-352,810,696,79,-277,742,874,527,-320,-134,-857,886,-234,107,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(int,int,int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.Boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.Boolean):boolean",
            new int[]{93,-965,266,-980,-117,561,927,-725,961,-349,-291,221,-360,8,893,-488,-305,-777,-54,966,-727,162,655,-120,-692,127,-731,378,-945,-433,440,661,273,-15,80,470,854,273,916,412,-767,190,-523,867,792,945,525,-871,653,-91,534,-851,-256,-611,-283,795,833,61,312,916,344,216,-630,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.Integer,java.lang.Integer,java.lang.Integer):boolean",
            new int[]{-228,847,267,-591,479,-741,-531,-555,-598,-155,-403,-58,956,-40,338,-891,-438,-130,-732,-912,24,-312,745,94,966,984,-206,459,738,-348,-830,-572,-714,-692,-812,803,664,759,-644,-705,-878,880,375,-201,954,-382,641,709,719,791,-976,-126,-201,843,-581,-194,-188,-225,314,-633,891,112,461,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.Integer,java.lang.Integer,java.lang.Integer):boolean",
            new int[]{816,552,-847,-67,651,-276,-770,-594,571,456,-439,-995,-226,267,-117,-219,75,992,603,-716,807,939,-874,503,174,927,693,-983,702,-986,142,-521,537,-535,456,-236,-484,-276,-632,894,-800,994,838,-794,-699,-606,-212,138,-647,-402,133,464,-123,-621,-174,-138,-247,-471,443,-726,118,-746,618,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.Integer,java.lang.Integer,java.lang.Integer):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String):boolean",
            new int[]{-381,86,-430,1000,-731,-1000,-784,104,136,-1000,-549,69,1000,-36,315,-49,-841,-398,-573,174,1000,-876,-251,-62,-1000,952,960,765,582,1000,-716,1000,-653,269,1000,-875,-275,1000,-130,-995,-329,1000,474,-823,584,-634,-812,726,674,430,-855,-1000,1000,1000,-1000,-175,-706,-122,559,171,-69,421,287,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String):boolean",
            new int[]{779,578,1000,743,53,-1000,-1000,271,145,-153,45,-21,927,-1000,939,-828,1000,1000,-882,531,727,1000,217,1000,-229,911,78,-178,-990,266,618,1000,-660,704,1000,-704,-319,1000,-1000,-1000,-111,78,-394,-464,-964,-599,-44,-326,39,-93,193,286,351,-160,84,-597,-1000,610,288,295,-1000,-267,-678,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String):boolean",
            new int[]{371,-701,124,629,665,-180,-549,446,-397,251,-886,797,760,-972,-326,587,718,-870,-871,-784,831,701,106,-295,70,687,299,-180,812,746,-966,14,-85,-633,-587,68,621,144,-113,-254,868,-572,-720,462,456,-766,-666,-996,-823,-817,622,681,495,517,-653,-68,643,-219,-19,-638,-868,-367,-57,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String):boolean",
            new int[]{257,704,-615,-54,335,-465,-602,103,-62,-683,997,-132,71,-127,224,-546,-440,-53,-559,517,-892,222,258,359,923,869,-959,585,713,29,-716,703,279,-660,708,-793,-845,-407,338,-907,794,-91,-2,-500,642,-542,-327,133,120,-331,-973,529,-4,-881,-20,-167,945,849,-305,729,163,730,-383,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String):boolean",
            new int[]{555,-332,-64,-1000,405,987,-483,495,-97,-1000,1000,-445,-467,-857,252,-928,593,895,-1000,203,-72,837,-217,458,849,32,-292,253,4,7,-286,-424,1000,416,465,-295,-952,396,152,-370,174,-129,1000,442,-684,-858,-551,274,-898,2,-36,1000,-486,62,1000,670,979,721,910,1000,905,-266,-874,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String,java.lang.String,java.lang.String):boolean",
            new int[]{485,-162,-474,342,158,738,-604,-850,-687,-599,-782,296,-736,400,-450,-27,424,111,-541,-587,-519,-804,-243,229,-558,-320,-913,368,283,163,377,278,160,120,975,-838,960,-193,113,456,-634,-169,246,52,-486,118,315,194,-144,-319,983,491,892,400,496,996,58,-177,-992,954,-747,-100,878,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String,java.lang.String,java.lang.String):boolean",
            new int[]{-256,-967,-724,226,140,936,-570,-804,-253,-293,749,301,470,130,-11,438,765,-184,111,-508,909,192,-637,50,-998,712,-609,767,-683,-55,302,-323,-131,-314,-602,958,913,738,-370,942,397,-403,-266,565,418,-91,496,-172,149,-705,606,-633,-158,860,-845,748,-931,-395,24,737,625,762,85,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String,java.lang.String,java.lang.String):boolean",
            new int[]{-430,-159,1000,-1000,1000,400,280,306,1000,769,-775,-1000,130,-90,1000,211,608,-1000,-63,-1000,334,1000,983,696,307,1000,-172,-482,1000,-204,124,965,-1000,-513,-262,296,-1000,1000,1000,-1000,1000,-133,909,-1000,-938,784,791,-1000,1000,1000,643,-1000,1000,557,67,-355,417,595,-1000,-255,65,19,-705,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String,java.lang.String,java.lang.String):boolean",
            new int[]{880,-891,-860,247,-664,1000,-608,-937,48,874,665,1000,675,192,1000,-711,-1000,-121,640,-39,-259,-574,-336,-598,382,-1000,56,-633,-501,-991,1000,-223,-1000,-1000,-581,-677,276,287,-76,-164,1000,-558,1000,-395,436,673,-156,651,-1000,-86,141,783,691,823,143,1000,481,-915,-1000,1000,-166,930,-732,704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String,java.lang.String,java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBoolean(java.lang.String,java.lang.String,java.lang.String):boolean",
            new int[]{17,908,1000,-543,1000,1000,942,34,-1000,664,-1000,-536,400,1000,178,-758,1000,-1000,-499,-812,342,476,-906,1000,-902,1000,-832,-707,641,27,75,-351,-1000,568,-76,-813,145,257,635,-294,188,-326,-223,542,-1000,1000,-1000,-102,-617,928,966,370,603,339,-59,870,115,1000,-1000,910,-394,-655,-71,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanDefaultIfNull(java.lang.Boolean,boolean):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanDefaultIfNull(java.lang.Boolean,boolean):boolean",
            new int[]{423,-701,762,779,-612,-843,-743,879,-180,719,931,-385,744,-293,411,365,449,493,-650,-322,-244,253,701,-603,-429,-101,912,-857,692,-320,-510,-106,71,-80,385,-26,-20,438,-335,881,867,-151,-906,75,-919,187,-616,-902,-894,-953,133,218,443,815,-254,-842,-109,439,28,-585,575,-318,527,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(boolean):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(boolean):java.lang.Boolean",
            new int[]{-357,328,512,549,-352,-313,533,-688,4,573,-122,-769,-472,-837,-702,283,211,-996,599,636,-983,-640,885,-984,831,138,-635,554,-361,-808,-730,-868,956,813,-381,-298,-497,759,-128,-773,194,-666,797,481,-421,-302,-689,-772,-911,-170,-523,21,659,-582,385,-728,-413,-716,193,89,876,917,286,-94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(int):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(int):java.lang.Boolean",
            new int[]{-501,299,614,997,-714,92,739,89,822,730,-505,171,-100,-859,-81,-974,-843,-423,-396,-223,660,810,608,-257,-856,-113,395,-745,-503,304,633,592,-624,-17,925,-193,-477,707,85,690,293,-228,458,366,-803,533,954,65,827,-748,-28,551,-538,-803,177,-668,-160,-709,-762,313,-879,-231,35,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(int,int,int,int):java.lang.Boolean",
            new int[]{-43,413,815,-618,91,515,80,847,90,-681,-149,44,609,-977,-303,-424,946,-402,905,-140,-371,-723,-53,524,163,-372,-392,-802,-863,725,-641,512,305,223,-541,-908,3,308,374,126,323,418,573,-306,842,-593,-444,454,-639,385,-966,508,-868,857,-407,337,339,-21,882,497,-771,-239,401,-507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(int,int,int,int):java.lang.Boolean",
            new int[]{-791,-127,-723,680,476,324,-892,-646,314,772,-127,303,-965,-566,-790,-24,750,673,-49,200,-368,218,-316,-128,-847,647,-655,-562,-128,-694,-642,121,827,135,-310,-153,274,16,311,599,-381,-789,-946,-457,-279,197,606,687,-536,-192,-529,-961,450,-49,-858,-686,-996,374,-575,48,-719,971,-98,-327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(int,int,int,int):java.lang.Boolean",
            new int[]{-402,-68,901,-611,279,681,940,-31,-478,-211,477,132,-339,-32,837,507,992,392,707,-309,-710,-527,-580,-131,933,-891,810,869,66,462,-939,498,-258,606,-231,971,-343,539,-393,54,-806,-10,428,-870,-155,-767,455,812,-240,648,234,-20,-827,-781,491,126,757,-722,692,-233,-424,898,-819,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(int,int,int,int):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.Integer):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.Integer):java.lang.Boolean",
            new int[]{-793,-80,-215,-337,-878,841,494,-810,148,108,941,-513,-178,28,-361,641,495,169,-717,563,-569,648,-162,-578,-569,-610,-180,456,-404,-522,745,-586,240,-767,-348,244,-107,364,-923,-963,-164,180,880,-226,678,662,-78,-92,111,11,-234,-508,-837,-832,-735,185,34,776,212,227,-101,-938,61,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.Integer,java.lang.Integer,java.lang.Integer,java.lang.Integer):java.lang.Boolean",
            new int[]{998,-936,548,-747,-867,493,-980,766,-343,902,-459,-818,-889,-742,315,-223,859,445,754,-949,-427,141,-982,-849,-43,471,327,-909,177,-492,-927,-119,152,-123,702,-623,-539,-509,244,-48,-585,729,-580,-65,609,-718,-676,181,-868,294,808,81,-434,-543,-69,889,842,265,-932,315,124,-235,-362,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.Integer,java.lang.Integer,java.lang.Integer,java.lang.Integer):java.lang.Boolean",
            new int[]{612,769,512,-102,348,689,826,421,-395,-629,218,-809,237,-665,567,843,908,-417,-290,-752,312,794,551,237,27,-609,-428,-807,-540,976,734,-948,920,-156,-353,953,-434,-970,-151,-911,668,640,50,-866,-533,-874,269,42,739,582,-48,-10,121,-865,337,742,-772,-903,635,844,-363,645,893,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.Integer,java.lang.Integer,java.lang.Integer,java.lang.Integer):java.lang.Boolean",
            new int[]{384,-327,-480,-554,17,825,-536,-250,-555,-512,-121,-769,482,-262,476,169,-35,41,-433,552,20,493,-363,-996,701,228,-500,-537,-396,-102,-315,46,-343,-639,-411,262,-19,-599,889,-980,-578,680,-262,723,197,951,305,146,-92,-961,431,763,147,-938,307,-263,873,965,-601,-146,-207,-465,-852,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.Integer,java.lang.Integer,java.lang.Integer,java.lang.Integer):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String):java.lang.Boolean",
            new int[]{-261,-544,766,1000,-1000,1000,-1000,-889,411,560,1000,-671,1000,-1000,185,616,541,359,1000,1000,-1000,927,-1000,361,-1000,-1000,1000,-1000,720,-475,1000,498,-157,1000,-202,565,-1000,-1000,1000,1000,-1000,18,1000,-115,-507,1000,215,-733,1000,-517,-324,-1000,-698,563,-1000,-1000,-1000,-1000,752,-1000,708,120,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{709,653,-764,-426,156,92,916,691,-207,-513,629,-298,863,458,61,-784,-939,-733,-889,-759,-505,-606,-392,169,-208,-541,-874,-630,-713,20,319,672,-776,-106,532,647,-935,-655,-162,857,-878,-273,-899,976,15,999,-815,78,717,-122,9,257,-674,371,-205,257,143,904,551,769,-921,329,-391,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{-928,-1000,-1000,-962,3,-599,-1000,132,-1000,462,-341,-618,528,855,-1000,70,-812,-993,-1000,146,-741,892,421,-61,1000,-327,-399,-82,824,34,-169,651,-470,163,-854,-757,119,350,1000,1000,120,499,249,-283,1000,-817,-853,1,-1000,-262,-1000,-1000,-288,-861,382,452,668,393,-158,-426,1000,-1000,-643,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{194,-50,1000,449,-1000,882,1000,-931,366,-495,258,969,452,990,43,520,1000,1000,-359,76,185,-435,-1000,170,-102,245,-516,-346,-1000,462,1000,810,-35,-955,-527,1000,-1000,-740,-551,-754,386,-289,-179,-724,-606,375,-885,571,76,-200,742,1000,377,-382,-789,30,-1000,-623,-1000,-1000,-1000,734,-241,884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{624,-829,86,373,61,-1000,643,-537,-143,429,-832,598,-1000,295,-41,975,112,-924,1000,20,344,349,-1000,563,1000,371,726,138,-685,-81,354,949,37,-75,-218,-200,-228,454,-264,788,411,-362,-1000,598,-1000,549,394,-998,175,-340,487,-100,659,812,318,1000,-49,541,-911,-809,-1000,1000,-332,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{-1000,-579,-81,172,-570,-967,-535,-182,-1000,-310,363,-448,-205,-873,-680,965,-774,-1000,-67,775,-1000,591,173,564,884,55,-330,286,57,-899,-163,-1000,-404,193,-475,-802,709,729,1000,347,-488,317,-1000,1000,522,-136,1000,330,-668,-276,-1000,322,973,-1000,-104,1000,625,-684,223,-962,1000,-31,1000,30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{-304,440,-1000,-95,984,913,96,1000,-1000,-473,919,-1000,1000,-1000,-240,1000,-1000,1000,-2,686,-136,-776,-856,-584,1000,-720,-252,686,1000,710,825,-600,-1000,787,-509,13,-127,438,1000,-979,-382,873,-49,1000,691,-261,-113,458,482,-258,-1000,206,1000,558,1000,-725,244,392,548,-1000,-62,681,-1000,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toBooleanObject(java.lang.String,java.lang.String,java.lang.String,java.lang.String):java.lang.Boolean",
            new int[]{1000,721,-552,1000,-1000,-419,180,-1000,1000,-584,-378,-626,-356,350,-575,-31,1000,1000,446,-1000,-302,-1000,-1000,-976,-285,116,-866,332,970,-113,1000,673,-382,-550,247,-518,-714,128,-61,77,589,-1000,581,-115,-352,160,-186,-706,1000,-677,-660,819,593,454,-427,412,903,-344,-1000,-732,-994,-606,-490,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toInteger(boolean):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toInteger(boolean):int",
            new int[]{-793,-653,527,-147,-78,921,-78,24,590,-944,-118,-616,499,-293,-377,-706,-771,461,-311,574,-706,492,790,843,-633,871,909,-818,247,459,-894,204,-672,-434,-540,313,258,403,-79,368,755,-5,521,-691,-951,-333,-681,374,338,-546,762,-553,797,-108,-290,-160,190,-493,-712,-686,928,26,-59,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toInteger(boolean,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toInteger(boolean,int,int):int",
            new int[]{-149,749,-278,-563,-184,-825,-409,-935,967,-132,412,-587,751,348,112,-489,-669,-593,-302,879,-998,133,291,-700,295,955,-280,436,138,-153,456,-368,-794,-670,-429,176,136,-281,69,462,-386,-833,-519,-943,-477,-482,-87,-507,-682,685,-123,-236,-416,-631,197,970,-673,276,21,-674,986,-334,549,-174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toInteger(java.lang.Boolean,int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toInteger(java.lang.Boolean,int,int,int):int",
            new int[]{839,433,-991,887,68,820,433,876,-184,651,-317,-399,-212,432,-495,-17,-689,-186,-650,240,187,-72,375,622,-219,-170,434,-971,984,67,-481,-949,-959,233,101,699,361,-474,-395,135,-647,-244,-143,131,135,-910,771,-408,483,-177,736,556,-186,954,252,317,-819,-15,181,984,-749,429,-334,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(boolean):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(boolean):java.lang.Integer",
            new int[]{725,-694,-936,582,936,-57,-997,-911,-3,937,-664,839,731,-996,631,-154,-580,312,-40,-828,336,-118,-186,819,-434,-426,-380,-498,722,70,-86,-297,-789,51,-155,-999,-653,-427,-194,-764,-211,583,745,793,-455,-585,-32,-23,-926,165,103,-709,928,-83,-713,-750,-216,912,647,332,741,989,-380,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(boolean,java.lang.Integer,java.lang.Integer):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(boolean,java.lang.Integer,java.lang.Integer):java.lang.Integer",
            new int[]{-983,-984,-622,-442,945,291,938,-960,574,-275,423,524,-221,408,431,-285,0,917,645,-531,-563,-681,-688,151,990,390,259,428,941,91,919,178,728,632,209,782,-960,-161,47,-302,956,-65,795,-65,885,-644,985,795,634,763,-706,106,-866,-993,-42,29,14,519,-478,509,-431,271,-823,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(java.lang.Boolean):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(java.lang.Boolean):java.lang.Integer",
            new int[]{845,-214,756,-253,356,-659,-412,212,829,-845,706,-211,-966,744,245,415,817,824,119,187,728,-720,944,544,133,-309,-752,-893,67,4,-917,-296,-387,-286,-350,948,-435,-563,-662,468,276,-904,717,-631,169,154,447,866,-799,-760,807,110,-276,-624,-388,-768,-345,562,650,350,-359,-88,-550,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(java.lang.Boolean,java.lang.Integer,java.lang.Integer,java.lang.Integer):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toIntegerObject(java.lang.Boolean,java.lang.Integer,java.lang.Integer,java.lang.Integer):java.lang.Integer",
            new int[]{-991,-149,716,-847,579,-487,-66,-389,241,799,397,-796,-651,-185,-977,246,-44,-926,-833,48,-109,-702,889,726,-917,625,220,-763,816,-574,-403,194,-490,-321,754,140,-512,-60,345,462,723,270,-728,863,37,188,-666,424,-595,722,-862,673,-921,907,-554,802,109,-281,124,-808,482,376,702,-256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toString(boolean,java.lang.String,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toString(boolean,java.lang.String,java.lang.String):java.lang.String",
            new int[]{363,170,690,752,50,628,-994,805,-60,-347,838,-615,340,-962,621,42,143,-726,467,15,-184,-42,208,159,173,227,-521,-690,-895,556,-95,57,1,-643,-241,-105,987,195,429,14,-703,171,-72,-363,102,-950,-657,709,-38,10,-425,15,-331,607,36,-651,-271,442,71,-778,-36,338,-233,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toString(java.lang.Boolean,java.lang.String,java.lang.String,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.String:MjM2LjU4Ng==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toString(java.lang.Boolean,java.lang.String,java.lang.String,java.lang.String):java.lang.String",
            new int[]{73,-314,-451,499,416,-907,236,196,-414,209,624,-533,878,832,38,-76,132,-832,813,-597,444,842,-835,675,-796,-130,161,-699,80,34,-329,651,345,-831,649,198,-356,-538,666,801,185,3,209,-75,180,-422,945,433,362,-56,-119,961,-605,711,-853,-274,70,318,-1,-925,350,-398,-440,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:b24=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringOnOff(boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.String:b2Zm", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringOnOff(boolean):java.lang.String",
            new int[]{837,-483,-960,33,-526,987,-318,62,180,91,298,602,73,715,-794,-633,484,-435,-854,-866,653,360,449,-548,66,-578,453,863,-666,325,-16,-942,-440,694,528,-158,330,179,-308,760,610,-808,-491,-176,-840,-916,205,-108,-652,77,-848,954,-302,-471,902,-188,-100,-159,-560,-523,583,706,91,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.String:b24=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringOnOff(java.lang.Boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.String:b2Zm", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringOnOff(java.lang.Boolean):java.lang.String",
            new int[]{-933,126,182,392,-28,769,498,-780,670,861,-119,-311,-775,6,178,-822,-879,-423,566,-802,-420,-236,-522,-459,-750,24,-361,-622,-195,-271,-127,-673,395,982,325,-609,-77,733,-729,-315,-341,654,-230,-89,-150,496,-894,-360,667,91,-859,657,74,604,-471,830,-968,-851,51,301,-179,464,-734,-884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringTrueFalse(boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringTrueFalse(boolean):java.lang.String",
            new int[]{219,770,456,-753,644,-247,-465,-777,-629,-800,-626,46,-398,198,160,86,171,-399,573,272,776,378,727,385,-49,553,694,-114,-843,276,-204,-363,-563,853,-955,-356,907,-812,138,931,44,28,-808,-959,-810,635,93,249,-989,67,748,-401,-293,141,-216,98,740,-715,-658,-972,-462,43,-546,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringTrueFalse(java.lang.Boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringTrueFalse(java.lang.Boolean):java.lang.String",
            new int[]{333,470,-141,-653,469,478,-36,657,-832,-685,-271,-564,-400,-185,-768,112,-165,-479,-586,-864,479,655,-562,492,59,-765,-427,164,-915,-993,888,751,-210,892,148,720,-757,766,-996,201,709,-418,822,676,-40,30,306,-566,-456,-702,449,-762,942,-202,286,-705,43,-822,-578,-655,-659,-579,-626,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.String:eWVz", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringYesNo(boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:bm8=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringYesNo(boolean):java.lang.String",
            new int[]{399,988,-542,-404,-581,746,-442,-651,211,132,-62,-274,-788,917,516,-230,-280,487,-608,-867,-666,291,-97,-207,-646,911,-742,-602,-568,-8,439,-677,769,-675,728,500,981,-280,694,-711,-614,921,771,255,915,821,656,534,111,-520,-333,-292,-255,-954,-56,-448,-528,588,905,-126,644,-954,726,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.String:eWVz", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringYesNo(java.lang.Boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:bm8=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "toStringYesNo(java.lang.Boolean):java.lang.String",
            new int[]{189,-364,-571,-594,620,411,381,477,-121,405,406,-560,19,934,-352,-56,283,410,-837,366,-660,918,-591,-972,-639,-699,-978,-141,131,141,975,-714,404,-887,-118,40,520,462,-305,-181,948,49,-683,314,-126,-634,775,960,-824,171,504,-39,259,14,-979,630,-952,-187,-187,-95,-824,-168,618,-3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(boolean[]):boolean",
            new int[]{-711,-983,-558,-79,-38,-96,-408,472,-185,-576,-896,-632,-640,963,-610,-370,895,-625,578,687,-995,976,-298,-679,-530,57,240,320,65,574,-329,-413,669,-476,26,906,-661,121,-886,874,-428,369,-987,596,-156,-152,-278,-328,-327,638,-550,-273,-996,-836,915,-737,-551,456,-747,987,-910,838,-369,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(boolean[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(boolean[]):boolean",
            new int[]{-801,-587,438,-404,254,347,695,-578,-737,-684,571,-962,-835,-473,360,388,382,423,-335,-784,-816,-370,268,-894,61,787,-879,5,404,-755,-87,297,-277,-568,33,-76,870,-662,389,-603,-689,-994,512,-416,320,-825,643,144,-789,-702,84,130,144,-954,-983,901,917,91,-817,778,225,-504,-658,-933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(boolean[]):boolean",
            new int[]{961,-661,-463,-362,-929,317,-157,-505,886,-322,194,924,-436,868,92,-514,910,366,-929,262,-979,-196,-178,862,884,-962,788,296,-170,-508,-190,-422,658,-80,-274,-550,-505,-605,476,876,-980,734,236,-945,549,-142,371,-345,634,668,-405,655,-621,864,-386,750,409,-135,-243,-385,617,-501,-406,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(java.lang.Boolean[]):java.lang.Boolean",
            new int[]{-75,-802,-865,-623,393,748,940,-198,816,907,-423,946,313,325,661,-857,983,526,-750,145,625,728,-309,13,61,668,87,664,-935,827,53,644,785,-117,-397,602,-564,-775,184,552,67,-57,168,29,220,795,433,967,-573,384,-954,302,-327,-987,-468,-887,160,-516,729,-547,-959,-477,-208,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(java.lang.Boolean[]):java.lang.Boolean",
            new int[]{-128,-958,855,127,-116,822,273,-800,825,625,926,-875,-626,-810,570,579,-261,-649,677,-5,170,421,298,-510,-579,-502,349,-44,-652,-489,-347,774,649,-772,233,-434,717,-517,-465,-61,-462,413,-456,274,-380,641,-929,250,-936,-966,-102,54,-966,903,-87,-30,150,-668,879,-591,605,700,-917,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(java.lang.Boolean[]):java.lang.Boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.BooleanUtils", "org.apache.commons.lang.BooleanUtils", "xor(java.lang.Boolean[]):java.lang.Boolean",
            new int[]{-400,309,-541,-973,-504,-376,988,-242,-340,-10,-243,604,-227,-268,-345,-157,-850,-771,350,588,-317,372,988,22,157,-596,685,-28,-481,-124,-405,208,-409,-96,460,-171,-402,894,796,617,-98,-512,75,-637,359,598,655,-217,-157,-919,-444,913,-361,874,666,-383,661,-789,-546,206,742,-75,868,73}));
    }
}
