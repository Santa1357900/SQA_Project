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
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(double,double):int",
            new int[]{343,758,-429,-941,-924,8,-281,-111,985,-343,764,571,952,534,448,-972,199,-361,-680,174,-421,-995,-632,-425,716,-323,825,927,407,324,972,527,-126,794,835,864,888,495,-975,212,-429,-61,671,-324,-204,977,-625,-460,-547,-670,-579,948,620,-154,120,970,649,594,55,925,-431,380,284,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(double,double):int",
            new int[]{959,427,-367,291,91,268,89,136,658,694,949,-428,-848,-122,-331,-669,-458,-617,-269,-821,53,-188,-49,-753,-132,168,692,683,989,758,-385,97,631,232,-329,-757,-1,993,-581,139,80,-406,391,384,147,-14,290,-170,91,-479,43,-37,-589,451,-611,-658,-303,-634,-379,293,-192,-561,-692,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(double,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(double,double):int",
            new int[]{267,323,-751,562,937,44,666,555,410,79,241,639,-422,-281,18,-859,-866,-621,564,689,-415,157,-71,-364,753,-609,424,-194,469,-754,-160,-218,-692,-823,780,876,-309,-327,137,704,-567,162,442,858,-76,134,-198,528,-475,-391,-561,-826,-545,923,-521,290,513,467,567,-802,261,500,-891,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(double,double):int",
            new int[]{4,-563,-464,320,803,139,-190,196,101,-391,620,557,-935,271,159,364,-312,521,-763,194,-101,598,-458,-963,452,995,-640,-581,-811,605,-264,714,458,590,187,-551,-110,-444,387,183,-459,47,970,-901,-519,-414,552,838,-679,-452,-573,-704,863,-24,956,-876,-880,161,-352,491,-758,-771,147,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(float,float):int",
            new int[]{-109,-737,-339,58,-290,-54,-623,502,-810,-800,-963,-128,327,837,646,396,-960,-125,14,419,603,704,-895,404,-349,80,-31,-917,-449,465,574,-154,-890,-855,586,-865,-643,468,-601,-685,-543,603,-406,345,663,287,394,-623,682,-836,-332,-625,-177,-474,-451,815,839,-453,-153,581,553,407,-910,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(float,float):int",
            new int[]{950,668,-1000,175,651,221,1000,578,200,625,-666,1000,-537,899,-109,208,-95,515,788,-201,-712,887,832,746,129,-1000,-172,1000,58,1000,1000,-1000,-615,-1000,871,247,-1000,45,290,872,1000,254,1000,641,540,-1000,1000,1000,-935,-1000,-214,493,114,-1000,-955,905,1000,-961,-1000,-780,1000,-1000,297,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(float,float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(float,float):int",
            new int[]{-214,577,-609,526,492,-159,611,949,78,320,235,502,-256,517,158,-646,218,934,163,-297,-520,209,677,579,256,-982,-655,382,363,783,498,-744,-841,-856,-654,95,-883,-911,-7,709,857,-347,223,464,824,-814,410,596,-718,-654,874,293,246,-874,-449,653,584,-430,-73,-343,614,-912,-154,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "compare(float,float):int",
            new int[]{-406,-438,626,574,-667,-667,997,-721,-920,937,-322,-539,887,-462,-577,-380,-757,448,-101,-846,871,14,650,-714,594,643,470,-378,-588,586,303,-974,-894,-25,-419,161,-542,-420,-852,774,-528,-223,-216,-374,858,265,-256,809,497,253,-291,-883,592,14,-946,-861,779,-15,-531,-227,-404,917,592,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{-878,-878,405,851,-179,-62,-929,834,-171,91,-838,434,-851,-401,328,912,270,-747,301,20,-794,365,42,-442,-327,284,-875,-668,-220,936,-11,-549,-969,-482,153,28,404,-623,-531,586,-210,-871,-933,696,-792,-921,-233,749,816,156,419,-878,20,-292,899,-539,-286,782,279,-651,51,774,90,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTYyMQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{355,-621,585,-138,477,-878,390,165,400,685,612,419,859,135,775,467,-77,-191,-103,117,-516,793,217,-557,794,307,733,201,504,-899,780,-406,109,839,-681,-161,-353,-662,634,-858,-962,570,-340,-8,-432,324,316,979,-363,-408,-83,767,-849,-138,-44,-714,-292,-207,389,-267,-942,992,934,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createBigDecimal(java.lang.String):java.math.BigDecimal",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.math.BigInteger:LTg5Nw==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createBigInteger(java.lang.String):java.math.BigInteger",
            new int[]{-429,-897,-262,315,-544,-290,-353,188,812,857,-80,208,-307,-192,918,-1,-732,640,983,-666,-450,-253,-783,31,683,327,794,-49,382,-958,-148,-240,546,174,786,-240,-334,167,578,-783,217,672,712,348,-66,962,-841,-581,575,868,954,238,-394,-694,-59,746,-930,868,427,-631,-144,-14,-305,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:Nzg1LjA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createDouble(java.lang.String):java.lang.Double",
            new int[]{-237,785,-925,707,425,-603,633,-683,193,529,337,-869,-235,-6,-585,-336,-110,-184,486,416,-697,-971,414,-406,39,954,-462,564,-98,584,-883,-716,-845,761,695,809,-614,879,-507,798,962,-982,891,878,-556,-414,630,724,945,-158,-592,729,-494,-10,-726,-706,276,938,359,-102,526,-764,348,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createFloat(java.lang.String):java.lang.Float",
            new int[]{-822,-1,913,74,-960,342,736,-390,-784,209,-337,120,-176,94,-187,343,326,-941,617,-499,746,930,105,496,114,298,-253,-570,523,-414,821,-209,171,533,132,880,33,359,111,-853,363,626,799,-26,-808,-681,-963,143,-886,59,-696,-241,-731,485,-936,666,198,875,-759,603,426,-196,-503,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:OA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createInteger(java.lang.String):java.lang.Integer",
            new int[]{-280,0,0,-1000,139,-1000,-1000,-246,-438,692,676,-649,1000,-191,-94,7,397,-118,61,0,751,-1000,951,522,-261,284,99,330,0,-8,73,545,105,504,-801,1000,-170,116,-274,-300,-101,1000,157,115,-476,-1000,542,-148,1000,639,198,-650,-593,1000,-388,1000,392,-1000,793,-886,962,325,-158,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Long:MjA1", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createLong(java.lang.String):java.lang.Long",
            new int[]{-413,205,-244,-383,391,-552,30,-185,946,950,604,-929,853,266,-359,976,860,176,773,-673,-409,750,-558,-539,616,-378,777,263,434,310,-497,-572,24,-137,-793,61,761,-954,246,-358,-303,852,533,996,-523,176,-491,991,485,-811,712,421,-783,-850,532,-266,-680,168,-35,135,270,-211,501,-88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MS4wMDBFLTMyNQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-730,-1000,832,-328,-33,1000,-1000,757,840,966,-6,-1000,-1000,1000,-1000,467,-1000,941,-456,-822,478,1000,-1000,-1000,1000,768,-925,-144,-645,-914,-1000,-600,73,-665,-781,-1000,1000,-446,192,-1000,1000,1000,-809,1000,-1000,-995,627,1000,1000,-1000,509,1000,152,771,-37,1000,-638,-973,989,-196,-607,673,-1000,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Long:ODU4", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-727,-858,-20,208,-549,-787,-693,-44,636,787,57,-560,-1000,348,-476,-393,941,1000,366,-1000,-919,714,-359,-562,-301,1000,314,-522,143,687,436,-587,-1000,-764,145,-469,-205,-1000,151,-466,-754,-133,-557,578,-807,892,-648,677,-634,-992,-367,-400,85,48,8,598,-961,131,616,-46,-377,-1000,-809,-456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-883,737,498,365,916,-883,-817,-719,1000,450,510,1000,605,-1000,866,-1000,199,201,-868,-648,-1000,-902,-230,1000,1000,-76,1000,-1000,689,446,366,532,343,358,282,1000,821,-348,-638,-633,1000,-25,797,708,-337,458,-1000,-588,371,839,-727,-381,153,-747,-787,19,868,561,-481,-22,1000,-780,-57,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Float:MTAwMC4yMDg=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-123,1000,-902,208,977,-748,29,-719,704,230,81,890,1000,-773,1000,-347,736,-275,1000,517,-1000,198,1000,211,1000,291,514,-567,930,289,1000,28,274,1000,231,669,-499,-461,-199,-8,1000,-1000,-488,1000,-424,1000,-383,-1000,-1000,679,-870,-1000,-146,-321,-1000,136,-1000,1000,78,-469,-34,40,495,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:NzE3LjA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-759,-717,608,357,822,-222,-459,-687,905,581,618,187,288,-955,-146,-538,-430,-26,-631,-548,-679,178,-943,741,149,-517,938,-426,-245,-337,157,424,16,-387,142,793,798,342,-225,-620,704,861,-378,217,328,-123,210,107,658,391,168,744,284,702,-381,282,-234,155,652,-217,143,-987,-87,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Float:LTc2NS4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{713,-765,409,-828,-570,-700,-893,279,184,-391,81,687,854,923,69,-866,-544,1000,1000,-592,-581,-600,1000,193,-272,357,389,-769,190,1000,685,-1000,443,1000,453,-1000,-285,-1000,-860,-283,31,1000,421,-997,-268,1000,-1000,612,458,149,1000,12,-964,176,-1000,685,898,-243,510,-469,679,40,-434,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:NS4zNkUyMjA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{118,536,-1000,218,922,-172,-917,-134,1000,-39,-216,-682,868,-989,1000,-341,-169,314,1000,388,-641,1000,953,-1000,-394,1000,-1000,886,717,232,-896,-1000,1000,560,110,-540,-990,-245,798,511,-278,-1000,238,741,-1000,411,-935,-1000,-662,-252,-1000,570,885,-62,127,67,327,-477,472,1000,-675,491,-101,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-389,-1000,-568,7,-329,937,-1000,757,995,1000,46,-1000,-368,1000,-303,577,-1000,165,292,578,478,1000,-250,-1000,-1000,624,-751,-200,-869,-1000,-1000,-175,-1000,131,-638,-1000,1000,-314,-626,-1000,-877,1000,-1000,409,-699,-995,935,704,403,82,509,1000,80,1000,-37,1000,-1000,-413,328,-1000,-607,690,-1000,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-547,-114,241,-106,1000,248,-772,-1000,910,426,246,-747,923,566,101,207,133,477,20,156,1000,-902,-340,174,-637,-292,-190,301,-186,-148,-474,751,719,-373,419,-626,79,94,187,-433,-634,197,-1000,-432,-580,-535,96,-8,-311,317,180,575,-20,848,-261,290,39,-949,500,-781,-340,-1000,-54,338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTc0MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{900,-740,409,-828,953,839,464,-666,184,-131,-571,218,900,-288,241,-512,229,412,202,274,-113,999,-543,-983,-951,417,-425,-248,-908,424,343,-528,-112,-269,-537,-990,5,817,-860,-984,-304,-564,-221,-997,-114,-368,-76,-780,458,232,-579,132,-842,278,-653,685,125,591,-321,188,679,792,-82,-335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-419,960,-1000,359,1000,866,-841,-720,983,1000,590,670,1000,-652,989,-684,32,-678,292,1000,-217,1000,435,26,1000,953,341,-543,609,-1000,158,-750,-1000,1000,-51,-665,272,860,283,-635,321,1000,-1000,1000,277,-19,90,-676,593,1000,-464,895,138,1000,-1000,608,-1000,206,-522,-1000,536,-713,221,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{1000,1000,-58,818,-1000,-357,-476,543,107,738,-1000,-1000,-777,693,-189,960,151,178,734,-607,-655,99,1000,-933,493,999,-1000,-145,1000,446,-414,-1000,-881,-468,-774,-806,-840,-1000,806,809,-55,-99,-549,-887,-367,573,-1000,-262,586,-1000,-600,599,974,462,25,1000,236,-991,-947,12,-1000,319,360,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTk3MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{599,970,-851,912,-796,270,-952,-708,521,916,-852,-493,-304,928,106,219,-664,839,-771,-264,-899,736,891,-635,261,931,288,258,997,-82,197,-872,173,128,-74,-898,967,-762,3,25,-737,309,-664,2,-959,-157,-476,702,126,-959,-192,988,608,-30,-240,832,-999,-860,-794,135,-210,74,126,-926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NumberFormatException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{498,-974,835,-234,376,-123,-777,34,-615,-196,856,-119,912,725,254,-970,64,506,887,431,-493,-207,157,862,-689,-337,373,-482,-681,-87,-925,-791,417,714,-408,-431,798,-453,-939,-195,803,901,333,-35,-728,8,121,413,-562,77,586,-712,-618,928,-875,-922,791,-285,-432,115,357,-491,-817,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTEuMDAwRSs2NDg=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-714,1000,-131,645,1000,-126,145,-1000,877,531,1000,1000,1000,-1000,1000,-1000,1000,-73,-1000,1000,-1000,60,-351,1000,1000,447,1000,-594,1000,-591,131,1000,-596,930,-347,1000,919,1000,183,-11,1000,513,-1000,1000,484,476,-545,-806,1000,1000,-1000,219,377,574,-1000,-107,-1000,345,-334,-342,1000,90,994,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Long:LTEwMDA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "createNumber(java.lang.String):java.lang.Number",
            new int[]{-695,1000,353,-278,1000,113,-814,-456,1000,581,250,-869,519,1000,630,-291,289,25,510,-140,-150,1000,-1000,-630,-470,586,-923,-616,1000,-142,-1000,-750,817,-296,95,-665,884,12,122,-635,-277,409,763,139,-1000,550,-501,-901,718,72,-584,1000,-512,-223,189,568,897,-923,354,929,-905,374,-156,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(byte[],byte[]):boolean",
            new int[]{-838,-857,698,-334,705,626,-118,941,293,-675,512,809,787,202,-105,772,116,-400,492,-152,356,72,-691,715,748,-765,444,-205,-926,109,-863,-456,-774,-273,-521,-776,-451,-39,123,-821,595,-581,754,-699,305,58,905,719,-708,711,-250,-966,930,127,922,-573,225,310,130,830,75,943,-653,21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(byte[],byte[]):boolean",
            new int[]{-1000,-561,927,-1000,389,-100,142,581,97,-265,-966,1000,1000,-764,1000,819,-1000,289,288,-775,393,636,-1000,-1000,106,-850,672,-817,-1000,491,566,196,-154,-156,-500,450,-1000,975,1000,-901,-784,1000,1000,339,-179,-248,-28,134,158,288,-37,-1000,339,573,1000,-1000,-705,-481,112,1000,-1000,1000,-128,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(byte[],byte[]):boolean",
            new int[]{376,111,418,415,342,883,646,366,-166,653,-755,-555,285,-350,-852,916,-335,-907,-933,156,-954,658,75,-154,707,480,306,3,919,-81,868,-130,17,632,-120,678,-922,615,-287,-929,602,852,966,605,-56,950,-518,-862,953,711,-189,-855,-768,995,-89,67,-222,-606,-398,618,348,278,-691,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(double[],double[]):boolean",
            new int[]{-1000,543,988,1000,1000,830,-222,1000,675,763,-1000,-27,-1000,819,587,-124,-512,663,-1000,-410,-380,1000,-676,-300,922,871,-1000,-1000,-49,-331,-949,-827,-475,-1000,-143,93,-462,-1000,-586,1000,-502,-755,868,-1000,-1000,309,-661,844,1000,-259,-1000,842,-553,1000,-1000,62,182,874,826,-755,628,-1000,-501,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(double[],double[]):boolean",
            new int[]{1000,-379,-1000,1000,-209,-293,705,-1000,-1000,1000,1000,-1000,1000,780,556,-1000,-1000,1000,1000,1000,-1000,296,1000,-549,-123,-1000,-259,1000,742,634,-1000,-1000,-1000,91,-1000,511,1000,-875,-966,-829,791,-386,-72,1000,152,20,1000,161,-1000,-607,568,1000,799,1000,176,-809,173,1000,1000,1000,477,367,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(double[],double[]):boolean",
            new int[]{-436,-327,-1000,-541,1000,-1000,-816,-832,-74,-72,1000,1000,222,1000,-390,1000,-98,890,-452,-161,-47,-1000,1000,671,-335,-1000,430,1000,387,-673,810,1000,903,260,-1000,-1000,540,-1000,732,377,5,1000,-49,287,37,-1000,1000,1000,-534,233,-200,-263,139,1000,244,1000,238,1000,913,610,42,-1000,-992,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(double[],double[]):boolean",
            new int[]{-1000,-157,1000,1000,118,74,-422,1000,643,1000,-1000,101,-774,1000,-846,-773,-1000,1000,-1000,-158,232,1000,-1000,47,1000,1000,-1000,-1000,-667,-706,-964,-949,-495,-1000,-101,-263,-448,-1000,-779,1000,-1000,-1000,1000,-1000,-924,-68,-802,1000,412,-838,-1000,862,-929,-915,-1000,-511,603,1000,1000,-315,1000,-1000,-417,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(double[],double[]):boolean",
            new int[]{-1000,-1000,-1000,592,916,-1000,-1000,-1000,309,1000,1000,801,-144,1000,299,-368,-695,1000,-1000,-160,-1000,233,1000,397,-161,-953,-1000,33,1000,-18,-794,-571,-234,-466,-1000,-186,1000,-1000,1000,530,-1000,12,-736,777,1000,-1000,1000,339,-558,1000,-1000,-655,577,1000,-1000,-188,1000,1000,1000,1000,1000,-1000,-1000,511}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(double[],double[]):boolean",
            new int[]{-576,-248,-767,-290,-913,-277,314,-919,483,-980,501,-690,63,943,93,-917,484,1,-646,-298,-447,-397,990,596,532,240,-934,577,272,-745,734,-832,155,-991,-748,795,891,-17,-713,197,-185,-758,718,2,568,523,843,-887,-254,211,-814,-731,-547,-553,-65,947,555,721,483,637,867,-574,462,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(float[],float[]):boolean",
            new int[]{-106,-802,-1000,65,-554,-1000,-676,-112,987,91,-751,-1000,912,-109,448,-70,803,-244,-1000,632,-931,-219,-775,1000,-952,105,-994,511,-813,582,-1000,-192,95,-294,645,1000,339,1000,749,-91,-874,-227,263,-543,1000,350,-260,729,895,-1000,-815,-1000,922,-300,-1000,411,-663,-568,1000,601,-131,1000,-1000,786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(float[],float[]):boolean",
            new int[]{434,-713,-459,-29,-457,-604,-1000,753,-470,-1000,653,189,941,-286,-26,896,789,-546,-162,-117,451,-336,37,365,-206,568,-202,178,-1000,-1000,-535,-522,-1000,-163,276,64,544,-123,283,-602,820,-224,-961,-1000,2,-1000,-545,979,38,-282,-1000,106,206,-1000,915,70,51,-1000,929,745,-697,-111,983,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(float[],float[]):boolean",
            new int[]{-106,199,-347,-280,-538,-1000,-676,121,-866,342,-20,-869,537,647,-282,-691,-1000,-244,-525,-274,254,-219,715,-404,-533,599,441,-798,-1000,162,-77,-1000,400,-912,-1000,752,-562,386,-556,-748,-874,-227,346,-992,-301,247,-260,-386,-326,497,-947,-1000,951,-491,-740,-349,264,-1000,-335,-822,-1000,-222,-202,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(float[],float[]):boolean",
            new int[]{-88,-1000,127,-299,-951,-1000,445,210,-142,-60,-604,-181,452,-173,84,224,-663,1000,280,-1000,85,900,-13,301,-510,-307,-26,16,1000,-420,205,308,-641,-1000,762,916,1000,859,706,-786,1000,225,-1000,-395,-209,-785,-482,-634,225,-1000,204,-1000,366,-801,684,-1000,752,1000,136,309,-838,-1000,-675,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(float[],float[]):boolean",
            new int[]{175,1000,325,859,-860,-95,-230,484,-222,61,-561,-670,321,-299,1000,-38,-311,216,267,-12,-707,-362,-601,281,-189,430,-482,-249,174,854,751,690,1000,-85,1000,96,-175,-1000,464,-407,1000,338,-1000,-550,1000,-678,1000,-1000,1000,-753,-65,-1000,-582,13,718,1000,-634,419,20,-92,-978,1000,400,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(float[],float[]):boolean",
            new int[]{265,-11,764,266,-8,-538,-248,215,314,254,157,-638,193,692,745,-224,543,-531,395,167,-687,564,-478,862,-634,-97,-508,631,-692,384,-537,767,579,-431,563,42,243,-398,-320,912,195,-257,-255,749,-234,251,-104,-278,92,318,-398,-535,-975,256,-495,-313,854,-826,521,-532,-347,53,-441,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(int[],int[]):boolean",
            new int[]{169,213,-403,361,-733,-34,-445,177,574,-1000,309,1000,660,238,1000,-658,-1000,1000,551,844,-187,105,1000,464,-989,886,-389,-1000,199,98,-601,-153,-323,652,350,-322,492,1000,434,1000,-494,283,1000,-165,1000,840,-163,1000,-1000,-1000,-974,952,-672,220,270,-787,-403,177,-1000,-889,473,591,687,642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(int[],int[]):boolean",
            new int[]{-1000,1000,1000,616,-938,-1000,1000,1000,-1000,1000,-1000,-1000,-366,1000,-1000,1000,-13,-1000,-8,-1000,-482,269,-1000,-1000,984,-580,1000,-1000,1000,618,305,-716,1000,853,-328,504,-1000,-937,689,-1000,-1000,997,941,147,-1000,1000,596,-478,1000,1000,752,19,1000,598,-398,699,-518,374,-273,-1000,-149,-1000,-983,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(int[],int[]):boolean",
            new int[]{361,-99,-74,837,-678,235,268,137,-682,183,-663,168,-156,-892,935,-877,952,883,24,168,229,-908,-434,478,-796,903,-611,100,989,-782,-609,181,-803,933,-660,309,275,-235,219,194,-737,996,-367,-280,685,670,152,-459,-203,-639,599,-211,722,392,307,-757,-831,619,-867,-662,338,276,-355,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(long[],long[]):boolean",
            new int[]{-967,-367,1000,377,1000,200,-306,-1000,1000,-1000,-1000,587,1000,1000,1000,-921,-1000,-1000,1000,822,594,1000,242,1000,928,-1000,-748,-914,-1000,165,829,572,-60,379,-861,-1000,1000,-1000,-1000,-1000,-1000,-1000,1000,-399,-713,-736,1000,653,-455,378,284,-408,-327,-712,-1000,-348,-853,-1000,-507,-1000,1000,-764,1000,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(long[],long[]):boolean",
            new int[]{1000,129,-1000,-1000,-1000,144,1000,1000,-1000,1000,960,-1000,-1000,-1000,-1000,1000,1000,-151,970,-355,-1000,-513,-1000,-1000,1000,1000,1000,-750,1000,-1000,694,1000,763,-1000,416,776,-1000,1000,648,1000,165,1000,-1000,1000,840,883,-1000,1000,1000,-559,1000,-931,1000,1000,1000,611,567,1000,1000,1000,-1000,-400,-1000,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(long[],long[]):boolean",
            new int[]{-920,-679,997,166,723,837,203,-397,915,35,72,-909,-552,-278,-719,-878,-58,-66,474,163,636,-102,756,334,266,-837,-567,811,439,-900,625,-278,-410,18,866,-584,-566,-241,-651,-448,590,755,-911,-295,-3,281,946,-108,669,914,-808,-246,369,-954,-892,-979,-155,711,-64,-716,-566,-33,475,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(short[],short[]):boolean",
            new int[]{-259,604,770,-446,932,448,942,-920,768,-405,415,-385,492,-688,519,317,-50,859,-25,-286,722,-239,-768,-258,748,-168,-54,725,778,143,-65,-947,-573,810,492,404,789,-529,-666,724,660,-364,672,934,229,705,-122,-673,-195,-624,60,-36,-427,-569,-511,44,-278,-592,262,163,350,-415,-145,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(short[],short[]):boolean",
            new int[]{596,-99,-43,307,615,-1000,633,-1000,456,-346,-828,-1000,-115,-967,510,465,503,345,-67,42,417,-343,953,-494,-410,702,0,584,192,0,418,-1000,200,178,-607,-504,-781,-970,225,0,251,-217,-547,-1000,65,1000,-203,266,615,-605,-595,-547,-83,-849,-1000,1000,0,1000,1000,702,264,-1000,-891,450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "equals(short[],short[]):boolean",
            new int[]{-32,-955,713,-948,928,740,-933,-301,903,-790,221,-223,921,-264,738,-948,-691,353,553,-869,-248,10,-993,852,159,-361,-757,-417,-773,-326,-623,19,-907,-769,433,161,618,-827,410,546,944,-417,-940,-471,794,-229,939,436,-367,-89,-851,-267,342,-394,799,-974,-720,257,-333,-337,763,994,-190,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{601,-174,240,-939,-68,-533,-125,-519,483,811,124,-512,589,85,620,-3,-267,-626,908,-639,-463,-871,945,-403,186,5,716,-821,-516,562,-581,-807,-933,818,906,251,-37,-71,-370,23,-375,-610,222,-490,876,-942,-14,254,-235,27,-891,537,233,-74,197,373,728,571,835,546,-603,-948,343,320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{228,-230,1000,-109,121,-408,219,263,282,-792,1000,93,740,-482,-107,-510,-21,-545,-54,-1000,-39,-186,1000,1000,135,-541,-1000,862,464,-1000,802,-54,565,394,-669,279,1000,-391,-115,989,1000,-614,847,-966,605,293,-316,-857,327,-350,-282,-1000,-986,-707,-174,-1000,-842,243,70,-411,940,589,577,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isDigits(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-538,279,-732,-1000,215,3,-484,987,339,-628,933,-1000,646,-759,-590,-821,-441,818,-1000,-1000,-545,-39,-667,248,-794,760,-638,1000,147,-309,113,1000,-858,318,4,-78,332,-353,912,-322,471,-1000,415,1000,748,186,1000,295,157,-599,22,776,-481,898,345,-53,1000,634,792,-1000,-277,230,463,312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{711,1000,-579,541,-1000,472,1000,602,1000,-237,3,221,-90,1000,1000,1000,-446,1000,1000,1000,1000,1000,-492,-1000,-433,20,-534,-774,1000,887,1000,-1000,-759,-1000,261,937,1000,737,-1000,420,-301,975,-577,-1000,202,-1000,936,-1000,-383,1000,-9,-1000,278,-452,-1000,-1000,-955,-1000,1000,1000,-528,478,-1000,-540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{745,-83,1000,-91,215,1000,768,987,-229,505,188,898,557,-759,-70,-191,-11,-421,-190,943,-1000,-1000,-840,690,12,348,655,-984,-945,74,113,1000,799,182,-228,596,275,-1000,1000,-372,234,256,36,729,1000,590,1000,946,757,-313,22,-956,371,-422,-862,202,-1000,939,-1000,-717,-465,-230,139,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-30,31,-843,1000,-513,-1000,-615,62,1000,85,330,1000,478,-1000,368,-157,667,-387,1000,1000,1000,-312,-440,651,-314,-246,112,-1000,1000,-505,964,-871,117,-585,-166,-66,-61,506,-824,834,-89,886,948,-839,-397,-687,-139,-239,80,-140,-715,-86,54,278,-572,944,-803,436,582,1000,-39,647,554,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-631,40,-200,205,214,97,-876,-455,13,-153,383,-917,-969,-383,-909,24,-878,970,658,376,-513,-839,625,606,-349,822,796,-996,-270,169,-578,433,160,866,823,-592,-41,-103,924,54,354,238,69,-627,-899,999,-852,468,230,751,-374,917,69,247,331,297,-587,444,47,25,-536,820,865,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-231,798,2,314,756,-601,32,446,-545,634,-638,626,-983,699,-933,808,88,-776,59,-67,-51,-681,-364,-409,93,780,-672,999,-349,94,-454,-656,240,781,-196,805,662,774,381,-807,-332,37,129,-999,-509,-578,-126,-110,255,899,-664,186,-519,-290,-157,-249,-58,752,-787,-219,865,536,-828,538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{169,612,-1000,-44,-1000,-1000,-755,-312,621,-1000,-205,1000,-888,872,-231,2,-80,-1000,-338,783,923,47,-1000,-344,-39,-827,-1000,146,798,-748,111,369,-1000,132,-1000,1000,908,-1000,-585,91,-516,-518,755,-409,1000,-1000,1000,-441,-1000,-624,868,-871,-49,-72,-1000,32,427,606,400,728,985,-1000,874,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{-711,-726,-1000,198,-269,-851,1000,-387,997,-527,1000,887,-1000,667,-920,1000,1000,-610,-1000,1000,1000,-820,243,-552,-806,-706,844,-581,1000,-378,1000,-1000,173,-782,1000,-1000,-1000,407,-1000,1000,-1000,-247,1000,-314,-869,-1000,1000,-814,1000,259,886,-458,966,606,1000,47,-760,-456,942,1000,-319,730,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{21,-530,-703,575,-441,-688,395,-909,996,-368,-328,528,-537,-70,938,450,-31,124,883,-51,949,642,57,-362,-370,-289,459,-826,977,309,541,143,68,-888,-272,-543,675,959,-390,-5,547,846,-156,-896,439,-733,-483,-196,-727,-163,-790,59,3,573,-657,324,-283,-63,509,707,-694,-173,-551,576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{13,102,132,205,79,440,-1000,-1000,-1000,-313,-702,-1000,-969,342,310,24,-1000,446,1000,100,-289,-120,1000,717,-58,669,355,-1000,-270,855,-464,399,718,47,420,-592,945,1000,738,-147,1000,1000,-790,-1000,-66,1000,-1000,526,-984,863,-1000,-493,-79,268,-1000,278,-1000,297,-531,25,-873,-47,-160,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{169,963,876,-1000,1000,539,266,759,-1000,-44,-1000,-612,-888,874,-57,156,-536,743,-15,-657,-1000,-409,-169,-9,-39,969,-263,253,-1000,932,111,1000,-261,580,-970,-73,330,409,-1000,1000,-516,-973,-1000,-409,224,1000,202,-542,393,921,-704,-538,-49,-72,-107,-1000,427,-565,-1000,-833,200,699,-916,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "isNumber(java.lang.String):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{-1000,50,1000,1000,-1000,853,1000,-257,-183,-225,990,482,-790,-446,-195,-667,-1000,560,-1000,62,1000,-268,515,-1000,-726,-100,-235,387,1000,10,-79,23,-312,625,194,84,-1000,816,-8,143,1000,73,-1000,855,577,-536,-1000,1000,-509,-1000,43,1000,-744,-875,-1000,-1000,-1000,418,-1000,1000,410,-421,-445,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(byte[]):byte",
            new int[]{-722,-871,-127,520,-857,720,-924,-914,22,930,331,-387,497,840,506,-510,432,925,268,-974,-47,-75,-626,-208,-14,197,-672,27,-993,-848,303,779,112,568,-150,67,814,-825,-595,351,-911,730,802,846,-920,31,-858,811,636,773,190,847,367,-33,215,456,279,-212,-330,-385,-816,-899,-548,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(double[]):double",
            new int[]{178,163,-598,-49,304,-967,-879,39,-105,759,-225,123,-723,171,583,-832,842,349,289,-904,94,489,882,-381,237,307,918,-981,464,769,125,368,-843,35,292,-618,-691,218,834,-695,674,799,898,-11,-647,342,-624,703,866,-438,-242,-887,-918,-293,455,318,825,730,846,-108,459,-204,-850,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Float:NDIuMQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(float[]):float",
            new int[]{251,174,302,-174,-866,-110,-229,-468,-396,421,-86,700,-703,-958,858,-681,618,-269,-715,-974,-895,312,820,76,264,368,-484,-823,-821,81,609,112,-899,-985,229,-140,904,-916,120,-919,833,-505,590,-783,-748,-741,882,-750,278,-974,953,-822,-909,483,248,664,507,299,-515,-19,949,704,668,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(int,int,int):int",
            new int[]{557,16,-78,948,11,-241,681,965,-525,127,-112,15,751,-614,-982,-507,186,7,-536,-743,-825,393,-952,-210,-273,-985,-780,98,8,-426,395,-611,-681,-175,599,-799,-551,389,446,-209,9,-994,-628,325,994,474,-16,682,573,826,-596,-453,-101,160,593,857,-415,-14,744,655,439,-34,70,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(int[]):int",
            new int[]{-13,413,389,-859,-598,-769,-432,-598,818,-736,825,-836,138,19,89,430,-741,872,591,774,140,977,375,745,-681,244,372,372,-932,716,-573,462,-97,605,612,904,943,-120,-673,-426,-253,882,275,-411,-314,-305,-838,-374,653,186,200,-168,-37,559,-113,449,678,4,-645,-323,258,650,-342,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(long,long,long):long",
            new int[]{-444,-542,758,-77,-255,169,48,998,760,-277,-750,260,-219,402,981,763,120,582,87,331,372,-370,899,-414,629,398,-53,506,-903,-890,734,349,-81,610,-231,-547,-713,557,-451,-653,60,989,424,-72,993,-138,-679,998,-583,681,-753,408,-236,-880,-865,218,668,-317,-868,-155,553,853,-248,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(long[]):long",
            new int[]{-295,-16,274,607,117,-580,-835,680,894,523,109,-16,-961,312,345,-752,114,-987,-682,-472,-429,397,104,830,-974,861,-122,69,-490,944,-522,668,-466,-443,-343,-293,-405,372,230,-259,679,-474,10,-880,231,90,144,-992,-876,-411,-737,-861,-787,278,-261,-197,-804,-162,236,539,734,696,759,619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Short:ODA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(short,short,short):short",
            new int[]{-411,-133,222,-388,806,-98,151,967,347,-976,-855,-89,507,334,411,630,890,920,304,644,909,-906,-336,991,-82,252,912,-316,45,885,-579,-429,440,-203,-901,161,-623,-428,608,-893,377,-42,187,626,397,-15,-326,-656,-21,-655,-27,782,-765,-138,967,64,-440,714,670,226,-977,483,-733,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(short[]):short",
            new int[]{-481,-795,-982,610,818,149,750,-931,27,120,402,100,842,-876,263,309,-898,263,636,-284,-503,-883,-900,-557,-920,-645,-566,-54,39,470,-429,703,-519,123,-582,-794,93,-587,621,407,436,613,-555,-662,-619,-646,795,-465,-270,-885,146,95,-592,915,596,750,798,-389,657,-862,-260,-106,455,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "max(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{-295,205,408,103,-553,51,-524,-191,377,-397,-740,130,709,-667,884,366,313,413,164,-255,-216,-62,-437,338,245,-631,-108,-451,-630,508,856,678,904,-222,733,995,-54,-416,631,324,193,-910,85,-806,424,-133,42,320,-395,587,702,354,-833,-862,-865,655,-749,403,-902,168,-997,-710,-935,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(byte,byte,byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(byte[]):byte",
            new int[]{687,-457,160,69,2,350,-145,-692,114,-323,-936,-234,-229,-80,171,-510,234,959,-573,-954,246,434,547,568,-436,-923,725,-762,-79,-133,-817,659,730,-368,853,683,-224,644,-221,-520,704,-266,369,898,-816,-475,-204,-504,319,456,-505,-982,-948,-521,-10,-462,-865,-472,-937,25,554,-836,693,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(byte[]):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQ4NC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(double[]):double",
            new int[]{-860,249,-280,892,-590,-484,815,-371,-665,167,-907,-170,-356,-120,-610,359,-698,-700,-264,-231,556,796,37,-266,-393,881,-155,-12,584,410,-313,-921,-722,-588,830,-47,-357,517,-407,-646,472,363,356,-651,-147,12,265,-95,1000,-476,316,-645,916,85,-54,869,929,-198,-410,-421,-364,199,-268,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(double[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(float,float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(float[]):float",
            new int[]{89,880,843,543,380,8,524,660,-240,-597,-803,58,-7,50,-983,472,-87,872,-401,-44,155,-30,876,-158,301,-288,933,-521,345,-652,731,-454,-82,-369,-605,-85,-920,24,-791,-400,-358,530,805,141,-31,919,152,-56,994,157,-86,-472,776,261,493,455,-618,-567,655,-246,-949,-638,11,-917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(float[]):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(int,int,int):int",
            new int[]{-103,-1000,332,-518,-954,1000,-86,359,965,-827,-632,199,-484,854,218,-88,-272,-1000,397,53,155,-34,-582,-413,-287,473,-851,438,-150,-626,611,-829,-583,-314,-1000,-967,-153,188,660,-234,-481,-575,385,-974,-927,-251,-335,-267,-1000,-1000,781,742,57,348,-350,1000,72,-237,92,357,942,-794,-779,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(int,int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(int[]):int",
            new int[]{39,-660,-113,-970,811,-934,725,265,64,818,-223,33,-983,-909,545,883,-784,306,492,-639,292,-455,-790,468,819,-761,108,-202,662,-605,816,-795,-678,-363,876,-887,-32,-401,782,298,-952,-1,-92,387,725,140,469,-825,-311,465,299,359,-645,-635,919,-687,938,-562,-832,-880,429,70,456,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(int[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(long,long,long):long",
            new int[]{290,627,291,830,-1000,1000,-243,-1000,141,772,1000,574,323,421,20,-920,1000,479,-182,-1000,354,73,-541,-410,126,-668,581,1000,385,-812,-862,708,162,282,-375,-796,-800,1000,-972,317,-84,247,292,235,-1000,721,-1000,490,693,-615,483,532,-1000,-160,567,82,-806,-663,152,445,-119,611,-658,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(long,long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(long[]):long",
            new int[]{-266,-626,272,-975,-761,41,-19,-800,920,-848,479,771,615,108,-159,-812,-206,253,-353,-994,213,-40,-322,822,337,-37,-733,117,984,737,-994,-508,53,-371,966,947,-853,338,-437,48,468,-650,796,-920,418,960,680,-354,-959,652,413,539,-384,-248,58,172,544,530,319,391,-35,212,-594,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(long[]):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(short,short,short):short",
            new int[]{454,-938,551,-498,-7,605,624,-607,-990,637,-349,-545,104,-137,-775,471,388,371,138,475,-399,-790,570,628,756,956,-707,-229,483,9,-260,-478,769,-42,919,231,92,117,159,-156,115,559,454,774,10,374,-230,272,281,-310,-884,-564,-502,76,369,-963,764,339,-56,592,-89,-554,186,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(short,short,short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(short[]):short",
            new int[]{-171,-389,727,153,-991,-501,-198,-669,-928,-837,708,872,-235,-578,-339,680,620,371,373,-377,-713,-680,-903,-520,208,299,302,-398,-385,802,-440,531,231,-151,-740,-922,223,161,624,-988,22,265,157,427,-634,-317,163,-895,-356,13,-782,756,999,672,-383,-142,-66,338,733,-953,-746,-268,-70,452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "min(short[]):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "stringToInt(java.lang.String):int",
            new int[]{-961,706,75,-731,-103,592,-145,-780,-741,-142,126,288,914,-658,325,-641,-805,-671,41,292,-564,584,624,-421,-833,192,-459,-356,-936,-118,-7,-805,-257,-400,-308,669,585,-219,69,32,-28,68,631,11,519,-15,724,132,227,479,523,-101,797,188,705,863,-191,772,6,-579,-60,-270,-431,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "stringToInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:ODMz", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "stringToInt(java.lang.String):int",
            new int[]{-268,-833,-704,378,603,-60,462,267,757,-640,729,-611,714,198,-319,465,824,-904,744,-432,-428,283,625,-982,-976,808,55,-971,-736,223,183,-212,708,840,334,349,-685,-700,198,-86,863,518,996,868,707,832,-316,-713,740,573,-167,-467,-365,-770,453,-965,-151,865,-232,-634,-593,-493,675,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "stringToInt(java.lang.String,int):int",
            new int[]{718,-349,119,-276,32,-958,-761,-874,-533,90,-668,215,325,426,599,147,-262,269,510,413,999,691,443,416,-89,-264,-383,115,-459,600,-754,-385,-323,-220,-431,286,-782,-626,-885,349,-220,598,-629,-275,828,940,891,385,847,-608,786,-563,-778,701,544,767,-208,700,-831,-846,-643,401,-269,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "stringToInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDc0", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "stringToInt(java.lang.String,int):int",
            new int[]{-925,474,-633,-420,618,-501,454,-454,341,866,69,-989,364,-757,-331,58,819,-914,902,-611,-644,-289,204,53,595,-906,-493,614,-21,-135,555,-583,-676,-308,979,-675,355,-466,50,-352,-486,-926,796,830,413,-922,-961,-88,-556,-635,856,819,-719,634,-82,-984,229,470,-772,-713,780,520,-98,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{-684,-34,-285,293,838,-329,-354,359,-681,-862,63,-386,-360,-137,986,339,-290,57,-740,-696,809,-894,620,-587,-694,-581,-993,917,343,371,874,84,-103,52,223,728,189,940,-161,-274,884,-732,-228,-214,688,874,-495,-902,-943,609,-563,219,-476,13,319,-416,866,894,116,347,-222,680,384,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Double:LTk5NS4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toDouble(java.lang.String):double",
            new int[]{-967,995,713,-371,191,-251,40,-194,-136,-761,-293,995,-763,239,-963,-111,113,862,-131,586,-896,-892,42,908,728,-572,-894,-537,190,396,692,281,-629,619,914,296,-788,-604,935,-258,20,-680,406,607,530,3,869,-302,536,-590,403,569,789,-79,824,615,-469,162,551,-71,-524,876,-109,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{583,286,-578,-624,-282,98,705,-415,-968,502,-280,67,-821,648,835,-147,-280,14,-793,477,536,-155,-100,-312,-665,347,494,441,430,100,-199,-516,-680,50,-72,360,68,-23,-357,674,-94,373,-223,-953,87,-693,194,-628,-404,45,791,680,-999,924,-83,213,-516,333,636,-830,858,479,321,953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Double:MjUyLjA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toDouble(java.lang.String,double):double",
            new int[]{969,-252,-222,-413,-869,1,983,409,700,440,706,-824,-755,459,93,958,-597,-182,-568,604,-610,637,-938,59,-215,-739,39,-388,912,558,280,-473,465,-853,558,-994,-510,-429,-561,440,296,479,994,518,419,524,-31,-86,388,-152,-14,2,-292,819,-709,313,-800,941,-404,312,655,-204,640,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-72,-840,-621,77,15,-984,552,-737,-652,487,288,-857,484,-635,816,-22,401,-385,994,569,-250,-683,-503,302,-315,639,574,-579,-707,250,575,-365,835,-47,550,-292,530,-547,677,949,-529,197,-9,-287,386,979,-22,-78,-545,614,936,412,124,419,-299,-546,-4,-556,331,210,232,442,-359,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Float:NDQ2LjA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toFloat(java.lang.String):float",
            new int[]{-358,446,-888,-269,655,442,-388,-910,-304,148,-374,869,157,172,-754,321,98,937,-968,843,-895,-316,-952,-220,-694,-430,864,484,462,448,125,399,230,-763,-594,458,285,996,504,392,734,-251,56,-315,-495,939,450,877,-231,968,-674,646,-708,-291,-302,693,663,-915,661,837,775,888,20,909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{237,32,499,576,-457,150,713,-526,-851,868,150,-777,-653,-640,-429,900,16,-896,-698,56,685,-777,782,661,-837,45,-710,-168,-727,-880,247,607,11,124,632,-262,-539,960,515,163,-772,-824,-519,-828,759,371,823,-462,-9,649,756,-82,-993,-625,644,695,-957,-500,-761,60,104,-455,-720,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Float:NTc4LjA=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toFloat(java.lang.String,float):float",
            new int[]{404,-578,362,711,-1,361,410,-61,495,-652,798,200,502,142,-140,-317,30,-516,-27,762,-963,407,-188,696,-549,929,864,714,33,-281,399,-967,-806,434,251,-878,-60,387,226,-839,-519,963,727,32,-278,759,-725,-567,-137,-167,8,715,612,626,-54,941,-829,136,842,-723,-948,735,776,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{909,780,122,153,-915,205,-671,577,384,500,-232,-906,-247,904,-485,615,-773,-254,-484,863,-957,-32,393,-767,435,289,-232,-691,83,942,382,-234,-672,608,-303,557,-590,411,111,123,-740,-325,-562,653,630,504,-114,391,759,182,400,399,-862,-92,-40,251,486,-566,179,733,536,-197,360,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDM=", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toInt(java.lang.String):int",
            new int[]{-61,43,930,461,940,-890,448,-907,923,170,-891,-920,-724,434,-158,710,-403,-861,290,-424,941,343,-381,-520,146,9,913,-796,-698,144,-958,-824,572,-538,-732,-680,-261,298,-686,-866,51,-712,-846,-599,-690,740,339,-162,-574,529,325,464,-841,880,986,498,-803,447,244,-803,311,-275,-414,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{-116,540,-123,513,-955,411,674,-946,685,589,-432,561,81,-682,-274,390,-451,175,854,-420,588,543,-30,-960,-561,310,962,-23,551,311,-457,206,-457,-759,241,373,650,-190,-656,322,-659,-217,344,-171,850,-820,365,-229,-337,-567,-700,-584,-312,-462,-77,979,-224,393,-857,-164,543,-613,-283,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTkyNg==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toInt(java.lang.String,int):int",
            new int[]{35,-926,252,341,-326,257,-876,-798,136,-785,-929,657,-667,932,-364,608,-477,-815,382,157,-288,124,874,-924,966,603,-228,995,47,-80,-934,-415,867,-153,488,440,-363,891,-571,530,332,575,-680,121,92,173,352,866,-465,-150,685,432,586,409,-202,442,426,977,579,800,398,-124,844,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{-666,874,-81,-602,328,-343,-937,618,958,-237,398,719,-575,-344,-634,617,513,987,-800,292,-310,-828,610,450,371,-63,821,-92,-616,668,-425,-477,-52,-329,-439,-362,-462,-969,-499,-465,-100,-322,-348,178,183,453,-163,-344,-361,518,-722,157,-288,278,-498,66,408,130,349,818,848,840,759,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Long:LTc1OA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toLong(java.lang.String):long",
            new int[]{963,-758,-573,-751,731,433,-311,-299,130,394,-483,392,-778,-779,886,976,-374,-344,524,599,363,-980,778,-88,70,-87,711,220,-85,-246,240,-161,-268,-482,64,-115,33,620,854,-763,391,-217,701,-800,414,956,199,529,-979,853,-316,909,-927,556,-857,-289,-477,-272,81,925,220,489,264,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{237,379,753,409,44,346,67,-680,803,-936,-817,642,-960,-282,-328,-954,979,-853,247,-903,-547,281,314,-359,-247,-353,446,763,729,-19,409,866,-478,-981,547,746,986,-322,957,943,68,-26,-798,87,-798,-706,-931,-509,-888,922,299,814,338,-920,130,-742,262,167,-5,480,445,390,809,-847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Long:MjQ1", DEReplay.run(
            "org.apache.commons.lang.math.NumberUtils", "org.apache.commons.lang.math.NumberUtils", "toLong(java.lang.String,long):long",
            new int[]{-285,245,751,-444,1000,166,739,-1000,-1000,724,-1000,-295,1000,1000,-9,83,-192,-92,81,1000,816,-935,224,1000,66,-507,-751,-75,760,-721,1000,-985,-976,333,824,668,-587,-934,562,52,253,-988,-529,-480,-229,-187,-260,-333,-186,-148,-1000,-891,213,-4,1000,935,-197,-433,-1000,-1000,-1000,-1000,-29,1000}));
    }
}
