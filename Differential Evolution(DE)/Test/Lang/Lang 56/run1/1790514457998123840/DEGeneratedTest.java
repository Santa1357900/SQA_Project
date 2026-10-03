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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int):org.apache.commons.lang.time.FastDateFormat",
            new int[]{33,862,-654,560,-661,-1000,1000,-225,-506,319,-180,1000,557,1000,747,644,1000,-766,-1000,-1000,63,-218,698,786,918,-1000,1000,1000,-1000,587,1000,210,-549,-988,533,224,-1000,826,-1000,-1000,-322,-334,-105,337,-1000,-358,-1000,400,8,-1000,-931,297,-140,-834,682,230,-1000,-503,-1000,1000,-508,-342,-496,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{956,216,432,234,819,-332,293,368,-30,-873,495,160,197,609,78,448,206,-154,18,-406,702,-791,-65,82,378,-985,373,750,177,-55,-939,353,853,882,-359,313,-135,-159,-246,-387,-68,879,95,611,-795,879,-938,-777,-57,915,968,404,819,-449,-561,154,-453,-130,-430,366,-626,287,703,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{842,-726,-597,245,323,-642,983,210,543,10,-82,-706,-219,248,-189,-532,-534,-703,-677,-978,-369,30,-214,791,-829,698,853,273,337,843,533,204,-175,-702,-753,-308,809,-272,734,130,-695,308,16,745,384,801,7,-144,902,590,-200,-273,-332,984,-808,-790,-729,534,-412,-692,128,-985,-940,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int):org.apache.commons.lang.time.FastDateFormat",
            new int[]{207,804,4,649,506,144,296,-1000,896,-1000,1000,-341,1000,-82,1000,-959,921,-418,711,476,-927,306,310,791,-1000,891,-406,-527,1000,122,1000,-235,821,-1000,-1000,111,15,-1000,672,-466,93,1000,-852,-678,1000,-131,-64,-1000,-661,-89,-1000,306,1000,-1000,-132,142,40,1000,155,280,-1000,-958,1000,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-604,972,77,133,553,252,-1000,-97,243,437,1000,552,1000,690,-269,-956,-29,808,1000,-436,-832,-97,-1000,40,502,-469,-524,-394,300,242,1000,470,-63,704,-354,-154,-540,211,-501,708,-329,-178,321,375,305,297,-684,140,413,302,-140,-141,17,5,-168,533,565,-380,730,710,117,-238,-384,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-1000,984,-1000,445,1000,971,-567,-276,-813,-226,-1000,-152,-143,-317,-654,-707,-1000,621,-1000,326,472,266,-419,-724,-375,52,-1000,724,-364,1000,-1000,119,695,508,-200,300,1000,360,677,-358,-1000,612,-1000,396,186,-94,-52,1000,-281,1000,-669,-665,-1000,-495,-607,-81,1000,1000,743,-326,164,878,-318,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getDateTimeInstance(int,int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-625,822,388,-251,-729,-405,79,1000,609,304,983,-18,540,397,-1000,304,-39,684,-767,-369,661,318,254,425,-827,-372,806,-1000,-881,-1000,-300,691,-1000,735,167,701,553,375,1000,-863,-761,-51,-796,-789,345,490,224,-333,493,-124,834,601,438,228,727,-956,359,279,738,372,-1000,-617,121,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance():org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{765,-930,-869,-120,-1000,176,-585,785,-1000,-987,-723,1000,-600,157,858,1000,-440,745,-1000,-1000,498,-488,12,-112,294,-1000,60,-622,24,-1000,-24,-581,783,-69,302,1000,725,137,-753,-1000,-1000,-1000,1000,461,-752,-52,961,-447,-617,1000,-522,697,710,820,661,277,757,797,794,-888,658,-342,-412,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-34,-1000,452,652,963,697,-191,1000,-1000,-1000,-260,439,1000,694,1000,1000,-922,288,-1000,-1000,1000,-1000,836,-997,-15,-1000,-1000,-1000,1000,-1000,628,-1000,1000,-502,1000,782,599,-93,-724,-1000,-1000,-1000,431,610,-1000,359,544,-699,-1000,1000,593,1000,308,1000,338,-258,-1000,1000,1000,-1000,1000,-1000,-250,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-515,-899,174,171,-413,611,-185,1000,-997,-1000,-604,408,1000,612,797,1000,-809,543,-1000,-1000,1000,-801,721,-448,-4,-1000,-130,-1000,180,-1000,367,-402,1000,-142,1000,37,682,-152,-768,-1000,-1000,-1000,-224,387,-596,226,1000,-336,-1000,1000,476,1000,412,1000,207,-157,-1000,710,956,-1000,1000,-265,-57,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-49,-775,-1000,345,151,181,-1000,1000,-928,-1000,927,1000,1000,-1000,491,-1000,-946,-159,456,108,-1000,-1000,885,-1000,-8,-1000,703,-226,-20,-281,1000,1000,564,-168,1000,879,1000,1000,948,-494,-1000,-68,-88,-270,-918,1000,916,-922,-51,979,-549,1000,-1000,1000,1000,-26,458,100,489,-1000,1000,-486,997,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-51,-854,552,642,203,149,380,1000,-557,281,-245,154,590,-134,-955,21,-915,132,656,348,-1000,-1000,836,-272,-15,400,-192,-508,1000,1000,-341,1000,-205,-554,-1000,-60,503,35,-447,875,1000,-439,-172,-526,789,1000,226,-780,-848,-1000,877,-261,-860,-961,-580,-1000,1000,489,-1000,138,1000,-199,-786,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-49,-854,724,642,151,926,-206,1000,-997,-1000,-245,93,1000,-619,797,718,-922,103,-587,-480,518,-1000,836,-997,224,-1000,-192,-1000,1000,-922,367,-212,1000,-13,1000,404,607,257,-356,-1000,-1000,-705,-172,86,-710,681,246,-307,-826,567,877,1000,-860,1000,533,-455,-1000,489,974,-1000,1000,-265,597,-397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{364,718,856,148,-288,-696,-980,-752,-854,-829,66,305,466,-723,999,406,228,-446,791,-932,-588,-844,26,902,-960,-101,-612,198,462,547,-812,-374,853,-411,903,333,616,915,-270,-305,-151,431,543,-269,-177,43,319,-242,472,697,88,-742,341,177,773,-890,-892,226,-92,-627,865,-762,147,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-962,-515,-326,-17,-858,680,-1000,282,-309,11,-315,907,-401,258,1000,167,23,620,-509,-784,-620,-81,-1000,-218,-1000,-1000,505,778,1000,-345,249,-70,-433,674,1000,475,324,20,-611,100,-1000,-54,774,249,-99,220,-298,140,783,312,556,-608,269,400,400,621,-263,62,-274,-626,-142,49,288,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-481,-1000,-25,364,468,777,-185,1000,-1000,-1000,-277,-239,1000,-855,1000,1000,-809,542,-1000,-1000,1000,-801,721,-992,-264,-1000,-653,-1000,1000,-1000,367,-1000,1000,-13,1000,87,689,-4,-904,-1000,-1000,-1000,-350,54,-1000,226,277,-248,-807,1000,1000,1000,-817,1000,501,-256,-1000,1000,1000,-1000,1000,-665,1000,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String):org.apache.commons.lang.time.FastDateFormat",
            new int[]{79,-751,466,802,1000,687,-444,795,-1000,-653,-551,-853,-461,-977,893,1000,-1000,124,794,-1000,-529,-1000,944,-448,-881,-557,-122,-135,1000,306,1000,1000,536,166,424,-301,570,680,-230,-1000,-1000,924,309,-156,-872,226,-1000,78,710,-33,1000,1000,412,1000,207,-565,-1000,28,789,-872,608,-1000,-57,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-977,871,216,4,1000,324,-1000,1000,1000,-688,-158,220,-834,-229,493,-1000,-461,-613,-551,504,-838,-1,-306,1000,-1000,788,-701,-800,-1000,-584,60,-556,-1000,135,1000,619,-978,-286,-1000,-899,1000,1000,1000,440,-1000,823,663,610,-226,-1000,514,-229,-886,95,1000,404,-324,651,869,-1000,571,119,106,909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{831,-477,425,-819,345,-585,247,-1000,-293,52,355,439,215,-533,297,1000,-11,-945,-282,734,38,-794,-1000,-1000,-12,-1000,-384,-104,393,1000,166,-692,-220,-70,-1000,-623,-599,-1000,131,1000,-761,-1000,-750,522,388,-1000,-940,-1000,520,973,-173,607,1000,-767,-852,649,1000,-128,-575,1000,-1000,-55,56,274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-210,133,-117,72,-761,-321,-888,-390,-13,77,916,876,297,-113,736,717,496,778,-943,-272,12,234,-974,-589,986,-649,-508,-873,53,-234,-867,-786,660,-293,-396,-599,589,-832,511,-570,-773,311,401,-328,590,-672,-65,-573,-928,189,-38,-382,-512,995,270,593,-911,-29,487,904,913,518,809,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-578,-529,-41,6,652,-1000,-1000,207,1000,-416,-434,220,489,-554,-202,-934,316,-837,283,-533,688,-83,855,273,173,-313,-823,-800,-592,-201,593,173,400,135,-40,-119,422,-1000,-1000,-626,-400,246,-400,-515,-911,-68,1,-791,594,-583,874,-1000,-1000,-541,794,18,1000,-749,593,-1000,290,-13,-801,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{317,230,239,829,-1000,-591,1000,1000,-1000,1000,-1000,-1000,1000,1000,-722,1000,1000,1000,-131,-701,-223,1000,1000,-1000,-144,-760,1000,1000,786,-975,43,186,1000,1000,-975,421,-676,-766,1000,1000,752,1000,-75,-1000,1000,909,-602,-594,-326,622,1000,518,-681,-463,-1000,1000,1000,-808,997,1000,-300,-924,-592,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-209,784,-1000,643,790,920,472,1000,671,-1000,-369,-938,-1000,-619,588,1000,817,1000,-131,811,1000,-699,1000,313,-630,-1000,-1000,-882,-306,1000,1000,-1000,-796,1000,-661,-67,-676,1000,617,-328,1000,1000,-791,1000,457,413,689,-594,-1000,691,-1000,-1000,18,1000,-1000,1000,1000,-808,1000,-1000,574,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{732,767,275,-429,542,588,908,-274,95,383,-438,-668,220,-315,277,1000,214,67,-201,956,525,-427,747,-1000,485,-1000,342,329,135,885,7,-721,-86,694,-883,-237,-685,633,470,1000,77,-49,-320,-845,912,51,-628,-928,31,-566,-28,-640,299,333,-911,686,1000,441,657,-115,-1000,-538,225,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-977,49,-1000,187,778,324,-1000,596,1000,-1000,-1000,318,-670,-116,92,-1000,-836,-88,238,169,-973,256,-51,1000,-637,302,-156,-1000,-590,-478,882,-547,-434,-451,1000,136,83,931,-1000,-1000,17,930,296,691,-1000,823,1000,1000,245,-381,-748,-125,-1000,237,1000,404,-1000,112,56,-412,700,409,905,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-355,1000,-514,879,1000,1000,-384,851,-293,52,278,1000,-1000,-853,269,-1000,-11,-945,-282,1000,-357,-519,-255,-1000,-12,572,-254,-104,-958,-13,208,-1000,-1000,-1000,1000,1000,-1000,899,-1000,-1000,20,1000,1000,522,-1000,272,995,701,-1000,404,-1000,607,-323,351,1000,649,-1000,1000,606,-1000,753,54,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{831,-416,694,-56,1000,-995,-1000,966,1000,-861,587,220,304,-323,-627,-1000,316,-945,730,-108,1000,154,1000,273,-161,-851,-823,-660,-1000,-1000,963,-226,352,135,127,312,-404,-1000,-1000,-626,-400,576,-400,-596,-1000,663,-273,-1000,520,-653,1000,-1000,-702,-517,794,-220,20,-131,667,-1000,1000,-207,-881,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{831,1000,425,-72,531,1000,-492,-1000,802,506,-672,439,-784,-533,907,1000,-200,-945,-99,245,-787,544,465,419,-928,492,-4,-433,-782,-1000,89,-692,-1000,313,1000,1000,-981,853,-747,-975,77,1000,1000,-376,-461,980,1000,798,-192,-1000,894,-738,1000,420,-852,-27,528,1000,1000,-1000,104,-61,174,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-855,980,-164,877,29,1000,-120,1000,512,-623,341,-535,128,730,-1000,-705,223,358,-114,9,-949,1000,312,646,-737,861,822,-188,-322,-77,-2,-45,218,714,671,1000,-142,1000,-287,-662,-704,1000,903,81,-182,1000,1000,624,-559,-1000,186,-1000,-586,1000,660,223,-419,208,933,-984,1000,-899,-80,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{41,524,-155,645,-273,-92,1000,1000,-1000,-254,-1000,-687,301,904,-1000,133,1000,1000,-339,-462,-204,785,1000,-909,798,-230,822,508,369,879,366,559,559,445,54,366,766,103,71,1000,-1000,-396,409,-1000,1000,989,-181,24,-342,606,336,-530,-674,1000,158,525,461,-1000,1000,-149,265,-1000,323,393}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{141,1000,356,604,-652,1000,1000,688,-1000,-623,-1000,-1000,-723,730,-439,185,223,1000,-1000,-935,117,845,981,-1000,-1000,1000,1000,914,-832,797,-1000,271,-620,714,62,1000,-906,1000,1000,1000,1000,276,1000,-1000,-609,1000,-26,-378,-1000,-912,-336,-1000,-590,-231,-999,1000,349,-1000,1000,-1000,1000,-1000,1000,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-641,-1000,-813,-436,448,-325,-1000,-449,-1000,52,-1000,687,662,-472,611,-601,377,-945,-282,-1000,-1000,-549,-340,-520,-12,-1000,-384,-104,-114,238,1000,-701,-110,-1000,846,-632,-728,-1000,349,-219,1000,-1000,-903,-49,-511,-1000,59,1000,577,1000,-667,1000,-570,-1000,856,516,1000,-1000,-1000,1000,-1000,-9,56,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{735,546,-1000,407,-1000,-136,577,-548,-9,-777,-178,-283,1000,732,1000,-961,163,1000,-24,-1000,400,-1000,-228,-943,-1000,-64,-946,254,1000,-829,726,-385,1000,-254,-1000,1000,202,782,1000,-83,1000,1000,-696,1000,1000,520,-803,267,1000,-637,819,167,230,-1000,-654,985,1000,-1000,539,1000,1000,1000,-398,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-531,762,1000,-56,-314,-102,-585,1000,47,-653,1000,1000,-1000,-477,634,-9,-1000,1000,-882,-729,-747,524,-223,1000,1000,-1000,-1000,1000,-1000,502,-966,-502,988,-680,1000,903,642,676,-1000,-617,591,-1000,1000,-987,141,1000,-706,-527,1000,753,908,1000,-1000,-384,178,1000,-592,1000,807,609,-781,649,-229,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{364,44,4,-423,-587,-883,695,34,-165,962,60,-625,-97,-722,-869,934,854,-419,-510,-156,-756,477,841,-135,-478,807,798,-623,782,555,660,699,-756,473,706,-707,290,-420,-269,-142,628,921,399,840,510,260,357,-532,-133,-235,972,61,642,-850,-903,-937,682,217,283,-973,126,-676,544,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-114,919,178,476,-365,711,950,275,-210,363,-1000,685,253,735,982,-1000,-471,1000,-1000,-1000,-124,-1000,302,-277,-400,-418,-1000,-90,1000,229,584,-949,778,-478,-24,1000,632,847,1000,-358,685,288,-147,1000,-99,-563,-648,-306,624,127,895,417,18,8,-778,990,528,-341,743,1000,2,847,-1000,210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{350,-354,-198,40,-74,-67,-170,-28,692,-1000,910,-705,1000,-1000,-189,-694,1000,-319,485,-841,-1000,-570,-1000,-637,-1000,-125,1000,627,1000,-890,-600,1000,398,389,-1000,570,-425,-1000,1000,1000,678,902,634,1000,99,-450,887,1000,-1000,-874,-532,1000,-611,-1000,268,-459,-592,-997,-452,-1000,-2,-50,1000,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-531,661,85,-841,-107,276,-443,488,439,-29,557,-16,-789,469,884,-825,-592,989,-882,-904,-851,-592,333,882,1000,-233,-477,-992,-357,272,165,-57,8,-1000,463,903,728,1000,-367,-1000,634,-841,237,-169,-287,-104,-445,-8,613,610,969,1000,-129,417,-430,1000,-574,916,906,323,-432,364,332,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{191,729,-212,401,-1000,-668,-329,-840,-446,2,230,-265,-445,-1000,292,-839,1000,-539,-1000,-804,-334,-418,25,1000,-8,737,213,-555,1000,283,736,324,137,-634,-88,-399,-299,590,717,-606,1000,1000,1000,1000,565,1000,-445,485,650,-692,568,-273,63,-1000,-512,-827,1000,-325,-372,362,1000,448,696,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-899,593,27,-235,-764,-680,-312,-1000,417,-870,-200,1000,-589,192,722,-53,-744,1000,-543,-877,-686,524,-938,621,-148,291,322,652,51,-590,863,-304,443,865,-850,903,1000,290,-410,1000,1000,1000,-944,-268,-820,-765,-495,-527,1000,-644,808,316,-1000,412,-596,734,-820,1000,856,590,-820,-255,-685,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{735,259,-1000,-982,-497,-281,-129,-847,1000,-493,-178,-820,-89,1000,1000,-961,762,-65,-1000,-1000,-1000,-1000,302,1000,-510,1000,316,254,1000,-303,1000,400,1000,-354,-318,531,202,943,1000,-808,1000,1000,-1000,1000,1000,1000,-856,267,624,-739,344,1000,1000,-1000,-774,393,1000,-1000,400,324,1000,1000,413,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-151,340,-1000,81,128,103,210,-1000,153,30,-1000,-313,113,-202,252,-866,1000,32,-293,-454,-1000,-726,-357,90,-456,1000,127,-141,885,-1000,1000,-1000,1000,-876,-1000,671,441,687,-43,-322,947,825,-1000,1000,400,1000,318,-113,353,-409,541,-11,1000,-1000,72,403,1000,-706,213,-274,407,-287,177,280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-82,-801,-705,343,-1000,5,78,-1000,555,-800,-262,-647,511,-1000,-161,126,1000,-344,532,-261,-1000,-844,-1000,71,-675,1000,1000,-607,-1000,-706,-243,721,1000,145,-820,524,-431,517,127,489,1000,-810,-1000,1000,501,1000,723,-1000,-1000,-385,92,129,1000,-1000,305,-877,-902,-346,-24,-121,20,-676,400,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-551,-1000,-1000,-1000,398,225,138,632,672,-217,1000,-519,144,1000,-528,-141,-266,-1000,-1000,-957,-244,1000,-326,756,-279,998,48,568,-1000,-829,200,-911,663,268,-630,-321,809,-649,350,1000,31,443,574,30,602,1000,214,31,216,-195,-411,-648,-1000,389,-1000,297,-121,-569,932,-869,679,-5,-250,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{654,-990,194,-152,-262,43,-114,-777,610,-733,-300,-764,6,-200,526,1000,981,-534,334,-833,777,65,-331,-22,-30,69,-364,-834,-37,884,-1000,916,-2,-457,776,158,371,-38,-693,-1000,-407,-1000,-832,-471,-454,-636,-758,-1000,354,-888,777,-166,172,-817,991,-17,-623,904,-1000,1000,-1000,-1000,-885,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-324,-43,-620,161,-365,234,335,178,-912,-426,-664,-461,907,17,401,-533,-841,1000,-600,-270,-484,675,-534,304,645,613,669,337,259,-1000,1000,-203,-26,-1000,-729,-156,1000,-1000,708,1000,114,704,-860,65,33,-1000,-934,494,493,771,-669,-754,-495,986,-1000,323,-963,-149,841,-212,-205,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-690,-98,194,-991,-25,77,-289,600,-611,304,-300,140,-155,1000,860,1000,900,-106,-441,-1000,-1000,118,1000,219,489,27,-364,-108,-1000,884,-1000,-297,-311,901,-594,1000,187,-1000,-1000,-619,-1000,1000,-731,1000,588,-636,743,-516,354,-825,894,-166,-1000,433,-775,-1000,-904,835,-1000,-21,561,-492,492,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-81,-661,-774,-846,-649,-59,306,445,188,-230,619,-151,-170,264,-645,-722,-48,-42,-1000,491,-1000,635,-716,557,-433,651,1000,-150,-536,-1000,-61,-87,-702,-699,-343,-116,1000,-636,249,229,-324,19,-141,-198,-267,463,98,790,-801,700,-477,-648,216,507,-628,-805,527,-122,225,-7,-93,-338,-664,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{159,-106,-445,273,-604,281,696,1000,513,-1000,-1000,667,-90,-1000,686,-722,-48,141,-586,776,-333,635,-693,744,1000,191,1000,-150,-1000,-757,-83,422,-702,-1000,297,451,1000,-980,-71,229,-713,449,-101,1000,-626,983,-287,209,-636,1000,398,-1000,-1000,1000,-540,446,315,-1000,225,263,-1000,-903,516,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-770,-1000,382,736,-706,443,709,36,650,-1000,857,-308,84,-479,260,148,-1000,-580,-784,-887,-131,58,247,-1000,1000,324,-95,700,-938,-956,-502,-798,818,458,73,861,-135,-1000,-978,1000,-627,1000,-1000,1000,186,151,400,-868,397,-437,884,-710,-1000,186,-1000,340,-319,-805,334,129,238,-9,73,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{77,-942,-538,-798,-668,-218,-69,60,-218,259,801,-118,-573,-202,-1000,778,690,-1000,-68,-793,-497,499,-421,535,157,288,96,-24,-52,487,-1000,-533,360,188,86,497,-584,-199,-533,-20,-904,-162,-86,-1000,-622,372,478,-542,-62,-355,-344,-164,658,-325,-242,-601,-160,792,200,986,-373,-426,-315,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-610,-601,-712,-85,-528,-79,-541,127,301,99,609,-615,184,-1000,568,891,-69,-116,-426,-793,-497,639,246,-741,733,912,-572,-369,-367,708,-978,-33,805,-523,-100,150,297,523,-29,456,81,-522,-434,-440,607,-1000,-934,213,493,-456,764,-754,658,-760,0,324,-390,-149,-217,559,-205,-498,-908,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-546,1000,-152,10,-264,659,-1000,-124,-418,451,-1000,548,-378,815,-195,137,-62,80,-793,-689,-1000,-102,-6,-448,1000,-227,398,-645,421,-480,123,-888,-526,-241,-350,1000,-657,-1000,855,-1000,1000,748,1000,1000,202,756,336,273,-250,164,-584,-1000,-1000,104,-498,-760,-482,150,583,252,360,-467,270,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-738,39,347,-847,-940,-69,-1000,641,685,1000,-578,-394,-478,-583,611,1000,-646,358,-1000,-1000,-1000,1000,382,-1000,971,105,-228,-897,-167,1000,-656,-230,81,-867,-907,942,698,11,-666,-190,-435,-839,531,577,1000,114,-642,1000,-382,-342,-409,-1000,-73,-1000,-367,-35,-915,-501,918,1000,-267,-576,9,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{415,-859,-1000,-463,39,-120,1000,426,-587,-775,-381,-334,394,-938,-41,-766,-175,256,348,320,217,180,-1000,1000,675,1000,880,337,-1000,-757,519,-351,-702,-1000,-170,107,765,-1000,405,587,-201,956,1,481,-791,355,127,-318,-1000,1000,-448,-849,-1000,1000,-584,565,-38,-1000,478,263,-595,-1000,-884,935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-295,-1000,-985,-317,-755,352,956,738,47,-568,229,556,-195,1000,-520,505,-1000,1000,-809,1000,-313,156,-1000,396,-1000,188,1000,843,-1000,-1000,1000,-360,-962,-519,-694,-1000,727,-302,1000,1000,817,864,-138,498,-404,551,400,1000,-679,1000,-808,-138,-908,1000,-991,-527,289,-1000,1000,-1000,369,89,-805,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getInstance(java.lang.String,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-946,-381,-1000,-703,626,690,1000,1000,582,-1000,498,-659,-262,-712,480,885,280,-528,-1000,126,-1000,564,16,1000,1000,273,714,980,-1000,-1000,-358,-453,43,432,140,1000,1000,-1000,-622,1000,-1000,1000,-248,1000,6,1000,729,-1000,-1000,615,619,-1000,-1000,245,-1000,-676,405,-1000,446,-123,103,-480,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int):org.apache.commons.lang.time.FastDateFormat",
            new int[]{-278,97,-74,125,-1000,-332,-1000,836,-778,-1000,-1000,417,-267,-482,4,989,988,1000,646,1000,-667,-1000,-816,-72,1000,-838,-1000,-254,1000,701,-791,958,-232,879,327,-683,605,-295,559,-1000,1000,1000,487,-760,-589,77,-50,-64,-630,-1000,-429,-499,-1000,671,302,585,950,500,1000,-418,889,-685,1000,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{12,109,806,786,-337,-705,-854,-113,588,-325,-637,-184,191,-392,704,-137,-341,848,-553,-15,665,-370,-341,-149,-586,-392,691,-570,755,-67,735,-175,-80,866,178,901,-65,415,-557,-792,698,732,-437,-339,-666,250,-193,-504,524,881,-646,-865,220,982,-784,-239,395,259,414,-342,303,286,724,631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int,java.util.TimeZone):org.apache.commons.lang.time.FastDateFormat",
            new int[]{261,961,-103,-904,-1000,-49,-858,-1000,445,190,480,145,-1000,-1000,988,-313,932,1000,-572,-145,1000,-563,38,-593,1000,-647,-481,-78,239,-72,858,707,-275,-435,-160,-431,987,-1000,-509,-53,346,-1000,-518,-188,735,74,-80,-511,-1000,721,-254,1000,1000,-1000,-333,-896,-341,630,408,519,-1000,-339,122,790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.time.FastDateFormat", DEReplay.run(
            "org.apache.commons.lang.time.FastDateFormat", "", "getTimeInstance(int,java.util.TimeZone,java.util.Locale):org.apache.commons.lang.time.FastDateFormat",
            new int[]{681,337,-288,988,668,172,-766,133,109,-372,-817,-361,-621,594,869,219,-923,-287,527,-427,363,25,785,-258,-938,-976,-313,-459,-898,159,-224,-235,-228,788,971,397,-730,-806,212,-838,56,348,197,-597,-348,846,922,493,-323,-339,-816,-230,180,-817,735,-659,-417,397,-655,-11,967,979,688,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-1000,-195,952,1000,-931,-1000,-1000,-884,-1000,1000,756,-179,1000,-1000,-340,-43,1000,-1000,-1000,1000,420,-164,735,-653,1000,-1000,183,-1000,1000,175,-408,-1000,1000,-1000,1000,1000,-594,-739,1000,585,161,1000,-1000,-664,-614,-1000,-1000,685,926,617,1000,-1000,-1000,1000,-816,925,-1000,1000,281,-1000,-464,1000,-33,884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.String:QUQ2", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-525,191,-198,155,477,-881,1000,-1000,-680,400,-1000,850,-1000,-1000,-752,1000,1000,-1000,330,704,-672,-1000,-109,1000,1000,-414,817,34,378,-13,714,816,358,304,1000,259,-229,-1000,774,738,740,370,-1000,-1000,-481,-120,-1000,701,1000,306,-562,-853,522,-478,1000,1000,-1000,-1000,1000,-1000,-1000,421,-661,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-342,-66,271,126,856,-848,1000,-1000,1000,332,400,-525,323,786,499,140,-378,487,-700,-400,-246,423,401,944,184,-961,-470,-215,879,-578,42,-969,-235,-58,-400,-237,847,165,-515,120,-308,-1000,-828,413,-484,-1000,-349,31,-1000,-1000,-320,-598,-155,890,-944,-43,388,1000,-165,-341,1000,71,-337,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-211,205,400,400,34,-385,798,-99,-506,-190,-740,-400,-1000,-400,-1000,-25,825,-914,93,400,400,442,-188,927,400,182,309,124,424,-468,-458,-156,146,416,376,508,-586,-1000,721,509,418,226,-292,-400,36,-400,-468,1000,1000,-85,-1000,-1000,199,211,35,928,526,-1000,866,213,30,-26,735,-389}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-623,157,230,257,-220,-13,1000,-1000,-912,400,-1000,328,-616,1000,-407,977,977,400,756,636,-496,-882,1000,1000,455,1000,1000,296,837,-445,198,244,-251,1000,461,1000,-554,-1000,657,645,1000,1000,-1000,-851,-1000,-255,-47,1000,594,1000,-664,-842,1000,949,868,1000,-1000,-1000,1000,-549,-1000,1000,744,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-594,862,1000,-761,221,-342,517,516,555,-1000,286,-115,1000,655,1000,-630,154,-1000,293,-137,-1000,-1000,-1000,508,943,-1000,1000,-701,303,1000,-1000,245,-1000,958,676,-949,850,-427,90,-1000,274,758,-628,-352,-1000,-1000,-640,476,796,-193,-328,-979,-386,-1000,-360,95,944,-577,-312,-174,-1000,-666,-18,-668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{115,-337,1000,1000,283,-409,-400,1000,-976,1000,-891,-543,-638,329,-840,-911,-1000,-1000,-920,1000,-812,-251,789,-463,-985,-407,238,-798,-582,895,945,-193,1000,1000,-1000,152,-189,-310,-741,-1000,-538,463,-601,411,-293,305,979,897,-306,1000,-783,1000,171,687,-260,-49,894,1000,173,-1000,55,1000,-380,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.String:Cg==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-746,-419,882,-830,251,-569,-613,688,369,-214,320,488,418,-321,-842,-601,-557,379,-63,94,-590,12,-668,197,620,-450,735,-316,933,774,828,-995,-62,779,820,276,976,-24,-992,-863,-396,-820,580,-559,500,314,725,787,89,582,-7,106,-759,-497,604,-1000,-744,129,923,343,-905,-29,689,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-436,-242,1000,814,947,-776,999,835,-82,1000,-1000,-486,-1000,-1000,-441,-673,-514,-1000,-1000,-782,-896,781,142,-879,-776,734,1000,-555,1000,1000,1000,19,-11,703,-691,-877,151,126,-767,-1000,224,-729,-575,-929,1000,231,1000,1000,-320,818,-624,155,-527,634,-2,-179,725,1000,1000,-1000,-393,-1000,1000,686}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-163,-1000,1000,-896,-62,-147,387,554,39,-22,701,16,-234,-743,-1000,1000,-665,320,414,1000,1000,-276,512,-1000,-965,-127,1000,529,90,1000,1000,269,1,-1000,-617,-1000,1000,544,1000,69,604,1000,-844,-516,-257,1000,-1000,-1000,-711,-223,701,782,-411,320,1000,-54,-1000,-952,308,641,-1000,-872,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{593,-113,1000,811,-502,235,117,1000,1000,714,-1000,-590,-413,-1000,-110,-1000,-494,472,-1000,-721,259,-20,120,-408,-1000,-740,-146,-589,1000,851,772,-819,507,342,1000,-1000,-56,-525,-1000,130,-669,-597,1000,929,1000,1000,1000,532,-1000,-313,-1000,1000,977,1000,476,115,-182,1000,600,-1000,-1000,-779,-582,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{719,686,536,686,-458,-703,-723,-848,1000,851,-331,-639,1000,-1000,735,-1000,1000,15,-1000,572,-933,-35,439,-462,-938,-333,-1000,187,559,811,313,466,-105,1000,711,-783,79,-879,324,-432,161,-1000,1000,-904,-768,449,1000,-765,1000,-1000,-299,-599,-723,-946,1000,1000,-1000,-429,-1000,847,-959,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{291,717,455,460,49,312,1000,255,68,1000,-1000,-878,-690,177,-948,-573,-1000,-188,-1000,843,-1000,-1000,1000,132,-1000,939,782,-304,-316,1000,1000,-426,765,-487,-269,-353,558,110,-1000,-24,-1000,388,-32,404,1000,314,1000,1000,-573,78,-1000,741,-362,1000,-1000,-1000,952,625,856,-778,823,82,704,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{536,446,-210,-1000,-301,375,543,390,1000,-861,220,-580,1000,-580,-275,-4,378,-348,-822,-33,640,-413,-844,-1000,-189,1000,20,319,-273,-655,80,876,546,14,46,-1000,-289,-256,850,-889,492,-482,731,-465,73,212,201,855,465,-400,-49,-715,-522,178,563,-111,792,-283,1000,-998,-580,1000,-18,910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{365,606,883,-880,-926,816,395,869,-183,354,-23,-376,-456,303,-661,389,1000,-758,-693,-893,846,-374,-302,-1000,369,733,1000,780,-878,-1000,709,787,-399,490,546,-657,482,-190,143,-1000,507,-343,154,-543,460,679,-1000,244,-539,-679,372,-838,290,-1000,347,-398,878,-512,475,-119,-463,-988,593,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.String:NjYwXw==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-1000,622,-62,538,-671,1000,1000,312,-1000,378,1000,-1000,-1000,1000,205,1000,-1000,-335,1000,-219,145,400,-682,1000,-131,-1000,-1000,625,-157,-421,-9,754,-934,-1000,795,-580,838,-1000,-1000,-777,-1000,1000,1000,853,1000,467,-643,-1000,-1000,-990,1000,-651,1000,347,1000,-179,-792,-640,-1000,1000,-699,-674,297,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{708,383,495,1000,956,-1000,-585,-677,457,-792,-971,-535,193,626,-1000,469,-145,860,1000,1000,687,280,881,1000,1000,-1000,-695,490,-1000,1000,-1000,400,-463,-462,-998,-795,766,-1000,-315,-1000,-1000,1000,1000,-1000,529,-1000,-1000,-1000,-929,-1000,-941,1000,380,1000,592,-77,232,-1000,-1000,1000,-1000,290,511,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{1000,78,-1000,-167,-868,342,-1000,1000,368,-969,211,-715,404,-1000,-517,-938,1000,-317,-1000,-599,1000,-1000,-755,-1000,-101,1000,1000,-1000,-869,-1000,1000,909,1000,1000,-1000,-1000,-960,1000,1000,-354,1000,-1000,-1000,-1000,-1000,-261,-1000,1000,1000,1000,-1000,-1000,-1000,-1000,-774,88,1000,-521,1000,-1000,-446,-554,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{411,637,-891,652,872,-617,-431,-178,577,-536,-326,1000,-24,1000,-603,494,-249,684,-511,-686,-1000,850,779,-896,-1000,-1000,-1000,-353,1000,331,-1000,499,-1000,192,-65,-1000,-458,914,-1000,116,-407,-454,-373,-895,-977,1000,416,-138,-922,-339,621,-160,1000,701,422,918,-1000,1000,1000,1000,-1000,364,-85,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.String:NDBfXw==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-66,-546,97,-1000,-596,-456,-617,-1000,-1000,399,458,-261,-1000,-847,-792,-886,124,175,-811,638,384,128,1000,-834,-553,1000,180,447,-803,-498,1000,-517,202,922,-1000,-755,-1000,1000,-567,-1000,889,-1000,-676,-360,-119,-1000,1000,609,-611,-128,-37,25,-935,81,-274,-795,1000,-397,967,229,152,-645,-391,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-194,-370,620,-316,492,-137,81,-761,-551,-136,280,-545,-939,588,-41,-543,-388,-501,-297,998,-404,-178,632,471,114,-623,-702,714,-490,-315,-720,-855,218,-480,316,68,-834,659,710,-639,24,-736,248,-849,78,-33,849,609,590,-426,816,508,-888,-929,-319,-170,-556,542,371,353,-375,-445,-304,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.String:MA==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{547,-370,97,-316,-324,-301,671,-930,-441,148,-392,-112,-1000,-817,-552,-886,1000,-66,-297,638,1000,-582,417,1000,-23,1000,836,-268,-1000,-260,1000,-253,573,-1000,-72,70,-924,659,710,-1000,24,-241,724,-360,75,-1000,841,609,458,-426,-121,508,-935,-200,-408,-1000,1000,-298,-377,62,639,-645,339,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-376,-546,-630,852,64,-877,568,-639,306,671,370,-798,423,-532,351,-898,-45,328,-811,-799,-952,958,496,-834,822,-232,-590,151,702,28,-461,-16,-708,922,-815,-739,-284,584,-311,-606,309,888,-796,85,-945,422,243,-819,-810,886,936,402,67,959,108,82,-461,577,967,679,-140,-474,-462,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{648,509,1000,-52,444,768,785,-4,38,-1000,922,503,964,114,-839,-906,-890,427,-297,450,263,1000,1000,1000,-805,-604,-244,600,-1000,-132,-430,-191,1000,-908,-72,-736,408,1000,-637,-618,768,-718,724,331,238,-138,693,1000,-673,-379,1000,-163,-655,-447,21,678,786,-298,-377,1000,-1000,-1000,-942,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{293,-370,-223,-461,-767,-242,289,-838,-441,1000,1000,82,1000,-1000,-1000,-1000,1000,-66,-1000,458,1000,1000,-282,1000,10,1000,1000,-268,-1000,297,1000,-291,-116,856,-1000,-261,-397,659,1000,-934,487,770,431,1000,-341,-877,841,696,1000,-426,1000,687,-1000,638,-406,-766,1000,-26,-354,-606,419,-612,-690,-251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.String:LTEyODMx", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{1000,-94,-39,128,-807,-1000,167,57,859,1000,-1000,-252,-51,-859,-606,-89,-1000,-528,-93,451,589,-1000,698,147,-542,-821,-1000,-643,1000,1000,922,513,-821,715,64,567,611,612,199,-900,-765,-1000,576,672,-1000,1000,-1000,-762,-1000,-1000,-1000,1000,1000,-468,-1000,42,1000,-1000,348,-1000,-686,947,-513,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,330,-403,-361,106,15,-338,-378,-202,-702,888,-937,-196,1000,-623,135,-206,666,-1000,669,-139,123,-122,597,-578,194,-332,-417,-101,1000,755,-338,909,-814,-239,-1000,386,909,-738,-1000,901,1000,-1000,400,1000,-1000,-318,137,681,409,821,-562,-1000,-261,-477,-789,-1000,-502,-565,1000,-1000,417,901,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-906,14,-863,-115,346,-69,390,410,-407,448,-523,132,779,-967,-441,111,-181,-190,414,306,196,-431,668,-642,527,-332,92,-431,845,1000,14,244,-343,-477,-627,232,503,215,579,760,517,-600,43,668,-1000,96,507,-288,255,514,-8,-190,-769,-36,-1000,-642,-258,-384,825,997,-785,-111,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-1000,-34,-757,384,341,247,1000,921,-535,-949,-397,1000,636,81,-380,396,-663,643,468,-441,-79,247,522,-302,54,-800,-397,-794,144,132,-594,-909,-265,-3,-206,903,77,-138,1000,762,712,520,-120,-163,-1000,-623,375,162,929,432,-234,75,-313,-209,-577,350,-580,-1000,-307,997,-975,-70,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-382,-900,-51,-614,380,245,1000,1000,1000,446,-401,-1000,34,91,408,-160,203,-1000,498,1000,573,-512,-702,684,-1000,-1000,-1000,-1000,-296,957,1000,-162,163,-572,-1000,-729,1000,-73,-842,-52,283,-143,-150,1000,-242,-261,-856,-123,225,400,400,-242,-64,-843,-475,-66,-168,-712,-1000,400,558,-65,160,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.String:LS01MDk=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{210,-217,-812,509,-897,-806,483,143,-156,-184,-658,-804,53,354,-791,-148,-483,-345,170,320,450,-479,-522,-715,-472,117,-46,570,-315,-427,-172,436,-455,840,221,300,179,648,-141,-648,406,-497,845,522,-725,23,363,-412,-466,-304,-966,798,925,-909,-685,-661,952,-48,800,-783,-599,-780,-408,671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-1000,-601,-707,-1000,771,99,217,1000,685,-1000,1000,-719,627,1000,-223,-324,420,48,-926,519,-480,473,-766,976,-303,341,-452,-520,-1000,801,26,-1000,-430,-1000,-425,-1000,1000,290,-869,849,1000,755,-212,398,1000,400,-418,676,242,950,1000,-829,-885,-266,-12,-314,-335,-670,-494,473,1000,892,142,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-152,-498,319,-180,-202,-166,-1000,764,929,-431,-616,896,302,-288,-627,-296,-42,-52,450,429,-400,41,54,491,-655,589,400,-672,646,506,1000,-34,-325,406,-72,1000,252,469,-78,605,-86,1000,76,561,-341,-134,-919,-1000,-605,-1000,-538,1000,999,-1000,-1000,-973,622,-197,-54,-1000,-476,-62,400,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{382,-100,13,155,-187,-245,-526,57,1000,152,-938,-577,-596,-367,-606,136,-203,-191,-772,451,962,13,400,124,-393,390,-800,-108,1000,387,1000,305,5,287,64,-80,-8,232,-9,411,159,506,-340,486,-1000,248,-1000,-400,304,-400,-400,155,-16,-932,-322,-983,109,-667,-784,-400,-180,12,-111,-742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-424,-1000,702,-1000,-353,-60,578,-1000,891,203,-276,813,1000,-132,-1000,1000,-346,-652,330,-232,-130,230,1000,-1000,1000,-973,348,-20,596,-746,-137,408,797,-347,-394,884,-460,374,-978,103,437,836,-44,56,-390,816,595,18,678,-1000,1000,-755,-1000,1000,400,927,1000,28,903,422,320,225,-972,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.String:LS0xMDI1", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-122,3,-423,10,627,663,235,612,-855,358,-627,1000,-629,861,1000,-509,1000,603,138,-857,606,-331,483,1000,-577,-313,1000,1000,-1000,416,426,346,-359,-881,511,1000,1000,668,255,1000,885,766,-1000,-656,163,-1000,634,-314,392,-1000,-526,-671,324,-46,-1000,-608,-1000,150,17,43,200,-659,-233,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-485,-466,431,1000,1000,819,-1000,1000,-962,723,1000,190,-515,1000,998,-1000,859,1000,-16,-567,-414,-784,338,1000,-635,-489,-998,-751,-861,756,72,503,-698,1000,1000,-341,1000,884,1000,453,1000,-258,-434,-864,436,-1000,-73,-468,174,-357,-348,1000,794,-1000,-1000,-603,-830,925,202,553,-73,-534,1000,124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-322,414,-657,-685,-329,239,1000,106,-205,-1000,-1000,-696,-253,889,454,-1000,798,-542,-1000,301,-500,257,-1000,937,311,-576,741,1000,-649,461,248,-483,336,795,1000,-1000,-554,-860,42,-41,-144,734,18,951,-1000,796,-213,-354,562,1000,25,-1000,-100,30,850,1000,-354,-539,234,321,-955,-477,-793,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,450,-242,-1000,1000,163,434,446,441,152,1000,-431,142,-30,1000,-598,1000,-1000,-403,-22,811,1000,-750,12,352,466,-926,-400,749,365,563,1000,761,-964,754,-1000,514,909,-650,1000,1000,1000,-1000,-508,160,-1000,949,-1000,1000,-428,-915,-1000,-65,692,-1000,-50,47,-1000,-1000,868,-1000,449,-817,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{46,1000,381,-1000,1000,144,-6,-551,688,-56,594,-551,112,-962,163,531,237,-1000,-483,347,71,657,-493,-1000,205,1000,155,752,470,-434,1000,40,846,-1000,-772,-356,528,767,-1000,-86,166,629,-531,-106,-155,297,1000,-405,932,-357,-1000,-582,-104,520,109,258,835,-17,-858,907,-92,300,-245,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.String:LS0xMDAwMzY1", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-921,266,-839,-1000,891,609,602,-101,492,-1000,663,-258,1000,-84,1000,-541,1000,-587,-921,-22,811,998,-552,140,917,-299,-607,1000,362,178,367,495,981,455,1000,-550,225,40,-650,622,674,1000,-1000,-142,-1000,4,562,-411,885,364,-255,-1000,-430,684,-550,1000,283,-1000,-249,1000,-721,488,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.String:LS00Mjkz", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{264,-1000,-887,-429,599,-647,1000,525,-581,-1000,-568,1000,-651,782,258,-1000,1000,-506,167,-567,-414,605,-372,1000,1000,-489,806,1000,-676,-716,-986,-1000,64,-181,792,348,-466,-1000,358,516,78,-258,-1000,-864,-1000,122,-181,-190,174,-357,633,-1000,794,-1000,619,1000,-236,-1000,1000,512,-324,915,327,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-882,129,974,1000,-153,-490,829,-76,764,390,1000,1000,1000,168,-30,1000,194,-491,-1000,727,-589,84,1000,1000,631,252,86,-1000,-230,1000,375,598,-400,565,-101,1000,-819,-971,1000,-677,1000,-1000,22,140,1000,-1000,1000,1000,774,1000,984,1000,175,662,-1000,613,-1000,-354,1000,-317,109,-263,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.String:LS0xMDAwMzM2", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-1000,-702,-407,1000,-665,-609,505,1,570,1000,1000,1000,-1000,-1000,368,1000,686,179,-217,406,1000,-119,-796,-527,-1000,1000,-1000,1000,1000,71,1000,704,-1000,-1000,-762,-1000,-322,-277,176,985,-708,1000,129,-1000,-1000,1000,-401,780,-1000,562,-961,59,289,835,1000,660,504,-1000,-898,1000,-1000,-1000,-249,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-233,986,-130,977,-793,557,-996,781,17,-932,244,787,-506,-434,-658,736,-713,-316,-249,-797,332,663,171,48,-58,663,472,-795,57,-435,-173,-733,-202,-823,247,599,627,987,192,-543,642,-559,450,-42,-128,96,21,-361,745,919,-559,-166,-627,-468,636,101,-238,-746,189,563,598,413,737,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-164,-638,82,-548,908,-147,208,-922,-682,-524,388,398,132,-10,633,-193,-250,-405,-349,686,-489,84,-866,-514,742,225,119,-894,-714,875,750,-173,694,-987,-793,308,-604,-878,-26,-108,438,-828,173,865,644,830,-739,-818,-552,-522,12,661,40,-922,-551,668,983,54,-897,940,973,917,875,-695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{624,190,-354,1000,-1000,-692,-13,-911,496,206,252,-34,-633,1000,-305,905,-152,775,604,-444,244,312,507,-364,243,-883,930,1000,-381,211,251,701,-841,-378,227,-143,44,978,-131,-69,-73,-1000,-825,33,30,-298,1000,-580,499,1000,466,-318,223,-562,-219,211,482,1000,148,-996,-180,302,772,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{981,-489,510,104,-504,83,-1000,-1000,-364,-451,1000,-765,-1000,728,-871,-952,-14,-240,-839,-1000,-1000,870,95,852,156,-759,1000,589,116,-592,-1000,-1000,-1000,333,-31,328,-150,26,1000,-1000,1000,-748,109,973,-377,-278,575,-1000,1000,1000,1000,-994,223,-990,-1000,-503,647,346,-683,-833,1000,1000,50,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.String:LTYwMQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{323,1000,-519,-60,469,-83,1000,-1000,-870,436,-1000,-1000,389,-946,1000,853,-719,-865,-845,1000,-1000,1000,929,-1000,1000,706,-625,1000,-1,1000,-880,-754,803,705,-205,174,-833,-1000,115,-88,-844,-1000,626,-626,-382,-218,-13,-1000,800,308,-284,-94,-510,-668,-489,-416,1000,348,34,1000,512,-844,-364,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.String:KzEwMDAzMQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-1000,-478,153,-1000,-906,-1000,1000,-1000,-1000,-52,-1000,-166,-227,-923,1000,689,-1000,-1000,-1000,1000,-940,865,1000,-1000,227,-1000,1000,238,-531,260,-1000,-1000,1000,1000,-25,-1000,-435,-1000,268,832,-1000,1000,1000,-187,-583,843,1000,834,-1000,1000,-142,1000,1000,1000,-199,356,1000,-1000,1000,1000,-392,-388,-1000,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{874,408,-66,-795,-384,-543,20,-507,-870,367,-293,-35,-752,-1000,595,79,-224,213,-66,20,-199,798,701,-1000,281,71,-1000,1000,871,68,-1000,101,1000,-340,260,676,-976,-1000,-92,-1000,160,-1000,-85,95,97,-434,-638,268,1000,-125,361,-692,12,-1000,-1000,-1000,87,37,109,455,639,196,455,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.String:Njg=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-454,260,653,1000,512,402,1000,-875,-292,-200,-1000,133,332,-907,257,12,-478,-1000,-1000,1000,-114,816,648,-1000,962,213,-335,231,-1000,147,393,157,275,1000,-937,-1000,-259,-249,358,1000,-847,1000,1000,-846,828,162,1000,-277,-616,-121,-1000,846,-185,242,938,359,1000,-246,600,164,111,-380,-1000,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.String:Nl9fNQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-343,255,-963,-375,-798,229,1000,-1000,-1000,697,-1000,-1000,-540,-48,1000,-522,-1000,339,-1000,857,-33,1000,895,-1000,144,-541,446,-922,-1000,609,-1000,-1000,-462,1000,1000,89,-137,-1000,753,164,-1000,293,1000,-79,-1000,1000,608,283,-1000,704,-1000,1000,1000,163,-532,483,1000,-647,-872,1000,-177,-1000,-336,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-146,-532,-81,-726,-430,-679,1000,-747,-301,-58,-1000,5,-823,-61,502,464,-736,-358,-330,387,165,-400,1000,-1000,74,-312,394,247,-330,161,-1000,-371,18,323,386,156,-217,-745,-161,1000,-774,1000,1000,622,-383,215,232,574,-754,390,335,479,762,240,-1000,-442,159,-1000,-75,183,148,-249,-1000,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.String:X0dNVDQ=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-242,-170,820,-322,-1000,-178,964,1000,-1000,-406,-381,-797,89,1000,1000,-504,1000,-256,538,-603,-751,-87,-513,439,428,-358,864,271,-1000,1000,-1000,-144,-916,-222,777,-568,655,628,-1000,951,238,717,-1000,1000,1000,-66,-124,626,779,-492,-916,7,-100,471,-527,569,1000,-13,-1000,937,1,493,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.String:OTI3MTY5Xw==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-210,-1000,1000,-420,-701,252,1000,332,-1000,455,539,957,784,1000,341,-1000,1000,-421,535,-1000,-835,-375,89,1000,461,-550,1000,289,-1000,1000,-971,377,-1000,-487,1000,-1000,954,1000,-1000,1000,-1000,1000,-959,1000,1000,-835,583,1000,217,-1000,-1000,-671,31,892,-820,-383,-1000,-246,-1000,-134,-853,-499,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.String:MF9fX19fX182OQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-253,-995,1000,-1000,134,-895,-1000,-1000,-1000,-1000,-1000,-1000,-1000,-818,403,718,837,1000,-1000,-677,866,-1000,-1000,1000,-538,-257,-770,1000,-684,383,881,838,1000,619,-1000,-1000,1000,1000,-1000,1000,-1000,243,-189,490,373,-1000,1000,1000,1000,689,875,1000,244,928,-772,-534,-76,-293,-83,112,599,-787,1000,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-633,-561,161,-1000,621,-851,-477,-1000,-1000,-573,-1000,-810,-403,-1000,-138,329,1000,159,-1000,-403,240,-769,-872,862,-538,836,-956,217,-240,1000,735,1000,1000,759,-935,-1000,1000,297,-1000,1000,-1000,86,366,-22,-435,-52,903,1000,1000,-1000,875,1000,1000,-951,-739,-87,-675,-548,309,988,372,-1000,1000,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.String:MjY5OTc=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-1000,729,269,1000,-147,-1000,1000,475,804,311,533,1000,1000,-556,397,61,191,-1000,716,-896,333,-84,-413,190,714,-851,741,-1000,-691,-131,40,-605,-1000,-1000,657,-205,-902,-1000,821,-1000,942,-1000,-389,-729,-1000,-1000,-591,155,939,663,-706,-583,1000,-1000,863,-753,-1000,844,-1000,19,-121,-231,-663,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,653,862,452,849,-709,188,8,1000,-747,-1000,-155,-200,-112,804,972,484,384,150,142,90,-619,47,-227,439,-231,-867,371,-242,-904,394,-1000,-1000,-1000,-104,777,-193,-722,-85,-129,565,543,-99,-1000,329,151,228,-333,770,945,-580,-21,7,-114,188,-555,728,1000,-1000,-1000,1000,-177,440,-497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-217,-132,327,-586,83,-942,-497,-312,-287,-67,-985,-82,954,-545,138,609,-31,432,-729,-318,-169,533,-104,-470,640,296,-164,-296,10,534,50,-98,-280,-755,-795,333,378,-102,873,140,-419,825,-652,-852,223,-710,-176,-127,403,-185,928,-513,633,326,461,-788,-17,-231,614,-509,205,-996,897,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-770,-322,82,-573,256,-851,-764,-743,-866,-717,-1000,-810,-403,-1000,-220,928,1000,643,-961,-780,308,-1000,133,1000,-340,831,-628,198,-240,697,206,1000,1000,506,-1000,-1000,492,1000,-29,-265,-136,86,284,-299,1000,1000,804,525,986,-945,875,1000,1000,-1000,763,106,-885,-364,1000,787,-135,-875,1000,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-676,989,766,-210,22,-1000,-489,-514,-439,-848,0,464,479,-948,255,-230,-742,-497,-434,469,-544,-329,-301,6,-208,-738,-481,108,661,84,1000,-761,553,287,1000,44,154,-245,-1000,-456,452,-470,985,-208,668,-992,-19,161,549,1000,-724,555,254,-193,-194,-987,-1000,-884,419,-14,919,1000,-67,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-129,258,-1000,28,1000,245,947,1000,389,-822,-175,-1000,-3,1000,1000,-124,1000,-172,223,-336,-817,1000,-429,1000,650,-832,827,-11,700,-1000,-1000,-241,1000,-728,568,-522,591,936,-1000,452,410,485,-1000,964,1000,-47,107,580,81,-92,936,641,639,-1000,-147,830,1000,1000,-1000,674,-797,-296,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,-577,1000,1000,391,-568,-170,-1000,458,555,1000,248,-242,-264,1000,-1000,799,1000,226,-564,-808,-171,-227,-436,107,-720,187,1000,196,-1000,-773,426,-453,651,1000,1000,-511,66,-73,1000,415,-1000,-1000,436,-342,-127,-1000,-1000,-1000,-341,410,-839,406,620,-484,35,-236,382,-947,432,1000,67,714,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("java.lang.String:MDk3NgpfNg==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,494,207,715,306,-380,-245,1000,780,-1000,1000,-1000,-1000,1000,1000,-556,-421,261,1000,-213,-839,620,-244,217,-577,-705,676,-206,-1000,-133,-2,154,210,-1,-257,481,1000,-667,-1000,1000,-315,-555,-1000,-570,350,445,-290,-145,-902,178,-691,-503,-1000,123,-24,-1000,1000,-501,-500,790,1000,245,275,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-115,-648,-1000,-201,900,-1000,772,368,466,22,-97,-1000,968,-629,-445,342,1000,361,-766,-69,658,1000,-402,983,-349,-148,1000,-875,-391,-900,1000,636,-972,202,445,-1000,1000,-50,839,-1000,-1000,-1000,-891,934,114,-396,-852,-1000,-192,-269,-769,228,678,-286,1000,-764,723,-1000,-1000,-97,-212,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{38,732,-747,-622,-651,1000,-4,647,298,-14,1000,-849,-1000,1000,994,-598,-139,255,459,-374,-977,117,764,258,1000,-491,481,844,-875,-632,329,1000,600,428,-500,836,-808,-1000,1000,1000,-385,-1000,-1000,-59,-72,-531,-437,-636,-802,-497,-1000,-232,-1000,585,-905,-177,-208,101,-508,-379,572,-830,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-402,-417,281,362,106,230,-1000,-288,1000,399,1000,248,-1000,672,1000,665,1,746,1000,-1000,-579,210,221,-581,586,-515,867,1000,-1000,-776,-944,841,396,483,1000,1000,267,-1000,907,1000,-266,-1000,-1000,-517,-124,-351,-1000,-1000,-1000,-1000,-603,-343,578,897,-709,438,-83,-127,-1000,-141,1000,-884,805,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.String:NjMyMzM=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-499,-919,-63,40,-519,-52,79,565,743,-439,115,-430,-855,771,144,-402,703,787,993,-466,-868,142,-27,521,175,-315,-135,1000,-125,-173,-900,1000,708,-972,530,334,-372,-177,367,338,-385,-1000,-324,415,541,28,-116,-636,-701,-221,-338,824,-121,830,-286,210,550,-190,-508,300,707,-272,-100,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.String:NjE3MQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,985,-617,-44,505,602,0,1000,502,-329,-400,-1000,-1000,751,-91,665,-76,-29,-215,-161,768,1000,-53,-277,-944,-850,1000,1000,-1000,-1000,-1000,1000,652,-1000,36,-903,-710,48,-40,475,-200,1000,-78,523,-268,1000,1000,1000,80,-453,383,-1000,1000,-474,1000,598,-641,-137,-999,-531,76,184,-225,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.String:NjMx", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-641,-354,-1000,-1000,-923,1000,-484,-1000,1000,754,544,507,1000,1000,969,-959,-701,175,-216,-1000,1000,1000,1000,-518,1000,505,-225,1000,-986,1000,302,1000,520,-749,832,32,-1000,-906,1000,400,-1000,-1000,758,-748,1000,-818,-1000,1000,-1000,-371,-1000,-746,-1000,1000,-499,1000,-1000,487,-1000,-1000,-1000,-66,102,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{430,63,-298,-381,-308,1000,-212,196,-768,274,989,-1000,-1000,1000,357,-695,-130,-159,133,352,-1000,-338,102,1000,557,-630,138,-386,196,-899,1000,611,559,1000,-1000,843,89,-904,370,1000,-177,133,404,-69,-1000,-1000,154,-219,9,213,-1000,672,-1000,60,-228,-1000,437,-330,803,693,801,-520,1000,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.String:XzA2MTIxMSswMDAwNg==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-1000,686,-51,679,966,607,-1000,426,1000,616,-693,-649,1000,-57,-1000,1000,-460,-1000,-1000,-360,-1000,-127,-1000,-770,1000,1000,690,20,1000,760,855,-1000,133,-991,704,-1000,-1000,1000,1000,706,-485,-823,-1000,-802,-307,-517,-133,-1000,1000,-1000,560,20,1000,267,-534,-253,-822,-133,-88,-1000,-276,144,567,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.String:R01UXw==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,151,-643,184,-584,-473,958,-6,-1000,-1000,-484,282,-847,-653,-872,-1000,-1000,1000,562,649,905,14,-513,504,463,-1000,527,-680,-271,938,-1000,1000,186,1000,-1000,1000,-160,1000,-1000,-172,1000,896,1000,396,-60,299,-722,-1,35,936,267,195,-1000,-340,-1000,379,1000,55,332,682,-50,-186,-197,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.String:ODA3QU03", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-113,-199,-243,-34,-497,-922,-230,-913,-96,-434,-685,14,-368,-1000,-1000,-417,-1000,571,130,442,-67,-124,-855,-414,-1000,-442,542,-576,1000,171,-660,1000,807,527,-298,180,-719,-214,-739,-909,-1000,-410,1000,-425,-65,-1000,267,-1000,533,1000,475,822,-132,347,-616,108,-869,-349,1,332,-1000,-709,-791,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.String:NTU2", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-726,333,287,-400,557,-673,176,1000,245,375,263,1000,-259,-1000,-1000,641,-607,715,-318,-529,-186,-595,419,-526,-763,1000,-1000,-840,-154,106,-125,667,896,-1000,291,-450,-531,-1000,-1000,-1000,-1000,95,955,-574,471,-787,771,225,266,1000,749,846,5,-1000,-977,49,-317,-884,191,-271,-724,644,-1000,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-340,144,543,-604,-890,-951,-13,1000,-690,740,289,823,-1000,-157,-360,-1000,-899,870,795,148,-17,-784,-1000,-1000,-583,-1000,-400,367,890,-418,76,908,-464,685,1000,917,-109,-848,-426,-624,-750,1000,1000,1000,1000,299,-570,-253,300,668,1000,-98,131,-146,-893,-657,-935,559,282,1000,826,-477,-293,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-689,-885,-419,985,627,-34,-835,1000,1000,-230,-1000,-1000,1000,-926,-1000,1000,-622,-1000,-1000,177,-597,437,-1000,-245,-686,884,1000,-761,1000,397,338,20,992,-408,-374,-1000,-1000,1000,680,11,-830,-1000,-141,-1000,-806,-1000,596,-1000,1000,-23,148,849,362,233,-457,1000,-802,-1000,-1000,-577,-1000,260,-86,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-131,367,-436,819,-237,-323,-462,-686,-1000,486,-12,747,-926,-31,1000,240,324,214,759,-1000,-575,-614,-57,1000,1000,-1000,304,-239,665,277,13,356,1000,-503,-576,-759,242,-447,-419,-796,-1000,12,1000,84,-163,835,-48,-1000,-616,449,1000,-1000,-67,-452,1000,-887,-129,852,961,-747,738,126,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("java.lang.String:LTU0MzI1", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-912,567,-519,-543,-863,-777,926,223,-920,104,562,661,-691,91,68,-761,348,934,780,-569,-515,389,374,-123,852,767,186,-190,590,797,-545,-18,447,372,-448,-742,-554,479,-463,-918,89,430,293,97,-1000,-655,-93,-668,-151,797,-23,591,955,474,-903,926,-950,806,-133,432,360,-424,-484,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.String:MzkwMQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-1000,-476,-423,390,-1000,121,-297,425,875,-121,-650,781,352,803,-1000,-652,1000,1000,-419,642,-366,1000,215,622,-1000,-678,-1000,633,718,-335,786,286,895,71,1000,-170,-196,-890,864,-1000,-784,-1000,-1000,-1000,418,-301,726,189,999,-881,1000,807,1000,1000,38,753,-1000,758,-670,-10,-1000,138,-1000,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.String:NjY2R01U", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-1000,557,841,-1000,-196,1000,1000,1000,887,267,-1000,67,-16,1000,-695,-88,-104,-609,-306,1000,303,-1000,-1000,-103,-1000,161,895,-1000,-1000,-8,1000,-483,143,681,1000,-324,-387,-870,-1000,1000,973,88,-53,-1000,1000,709,399,-371,-1000,-1000,485,-1000,-430,1000,-788,400,721,-1000,806,-45,-1000,506,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("java.lang.String:NjEyODA3", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,836,-275,-486,-1000,728,1000,-404,835,772,591,312,-253,44,928,948,536,-779,-944,-654,-138,-1000,-418,991,356,-840,415,-322,-66,245,1000,1000,-942,1000,107,810,73,-234,-377,-1000,996,-899,433,-766,217,1000,-219,685,1000,-1000,332,999,-244,709,1000,-619,-593,-814,170,-815,-820,950,-272,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("java.lang.String:Xzk0", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,101,-466,-243,1000,-873,-1000,-889,-1000,784,-1000,932,931,107,-1000,-850,536,1000,-352,1000,-711,-1000,1000,-1000,-1000,1000,937,-1000,-363,623,-1000,-1000,442,-1000,442,-1000,-1000,-580,444,-865,-1000,-385,433,1000,895,-1000,-219,-1000,-1000,1000,332,-936,902,-1000,-238,-285,-945,-814,-485,1000,-212,575,-272,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,1000,607,-1000,1000,712,-1000,-1000,1000,543,-1000,-1000,342,332,512,249,1000,-1000,671,785,-1000,-1000,-1000,1000,1000,1000,940,-880,48,713,-173,-53,-1000,1000,-1000,-1000,-1000,-591,-476,722,1000,-800,911,-1000,369,-1000,-1000,850,1000,581,-1000,-400,387,155,-1000,369,-1000,-1000,1000,-1000,-190,692,-895,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,431,-291,-456,1000,448,1000,-221,752,363,790,-715,67,-1000,1000,20,-132,-1000,447,-1000,-626,-1000,-256,1000,959,-1000,278,499,362,-1000,-96,1000,-726,1000,-132,725,221,604,-1000,-1000,1000,-905,621,-1000,120,534,508,641,461,-1000,-1000,1000,-117,1000,543,-788,-1000,-1000,387,157,-878,-33,-1000,-859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.String:LS00ODI3", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{519,-608,-519,482,-637,-478,260,-125,987,653,439,13,-937,126,189,448,95,113,-595,9,691,-244,-94,998,935,560,664,-130,-398,39,-839,-114,-966,-379,-911,568,173,947,-713,153,-550,-133,-918,715,-959,-330,990,-856,300,405,-865,736,-858,-532,658,-200,-430,104,-71,-595,-385,918,-624,124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{323,575,910,-498,-1000,261,-568,702,-480,-397,-336,360,843,-827,-284,-1000,1000,1000,-415,731,-883,1000,1000,-415,-1000,-849,203,-1000,-654,-344,166,-263,-552,382,242,322,598,-1000,-544,-1000,-1000,-245,-495,991,254,-486,372,-609,-1000,-763,-66,-877,239,-737,1000,1000,123,651,-1000,1000,175,329,508,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("java.lang.String:NjE3Nl8w", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-556,957,231,-1000,330,1000,510,574,-1000,852,-911,-48,303,503,-478,827,1000,-442,889,431,895,-638,-341,-703,-1000,200,-433,-614,-909,211,217,-1000,633,479,973,733,-270,-981,-1000,-484,225,-671,406,-652,914,577,-264,-1000,-902,-1000,2,-251,-91,1000,1000,1000,1000,-1000,506,-44,-637,-143,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("java.lang.String:KzUzOTIyOQ==", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{954,-1000,-407,539,-950,-981,1000,628,987,-142,49,-1000,-1000,-693,761,-402,-391,-806,-1000,-1000,1000,-726,-865,-945,973,-1000,617,1000,-662,-1000,-856,926,-566,-1000,-355,991,1000,491,-304,-1000,792,321,-142,-87,-973,1000,1000,-117,539,255,-1000,1000,-1000,-495,423,-1000,-1000,-679,-311,277,-489,-1000,-94,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-632,664,-324,-317,194,-96,-815,-679,946,887,-541,875,820,-308,35,-32,972,468,-739,-789,-626,-479,250,101,-175,194,435,-918,122,38,-399,275,-483,-155,-624,-268,-797,-817,877,445,-144,-720,434,-171,968,-672,163,-139,115,578,487,700,181,-40,-296,-796,-971,-625,-860,494,-844,318,-457,-530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{808,693,-899,1000,1,-454,1000,813,1000,59,1000,364,-1000,597,516,-445,631,-728,-1000,58,1000,-425,19,625,668,859,19,718,-155,-1000,-503,558,-863,1000,256,1000,1000,425,-346,-486,-565,-362,-1000,513,-528,833,1000,155,-588,-1000,620,1000,-1000,-139,48,-1000,-260,177,-1000,479,-669,-655,-1000,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-251,-498,64,763,-680,-1000,-1000,996,1000,-172,-905,-873,1000,-153,691,-224,-1000,259,-13,-709,-1000,-299,1000,935,1000,1000,-474,-1000,731,-1000,-778,-1000,-199,-1000,-832,-394,682,-974,1000,177,-675,410,108,-925,-1000,654,-567,1000,1000,-1000,181,-769,-1000,-742,-235,-1000,-569,1000,-358,-357,1000,-1000,284}));
    }
}
