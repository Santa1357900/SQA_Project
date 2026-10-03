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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-439,-1000,434,1000,-430,257,857,1000,-709,-616,157,307,1000,1000,827,1000,-760,-26,683,-1000,-386,541,1000,-552,-360,281,472,826,725,393,-558,1000,1000,569,-180,-1000,-1000,-288,-233,-415,-1000,968,854,1000,813,-676,-344,-435,1000,-718,629,391,363,-369,-874,381,-823,239,-48,-113,504,-1000,-527,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-827,672,733,179,692,730,-857,641,-952,336,876,-453,-208,76,-576,419,-545,83,-135,956,915,650,192,-790,317,176,-713,-62,-109,233,-22,933,-626,899,-378,472,948,-723,-458,-413,627,87,296,847,823,-412,239,553,485,734,132,-192,629,420,647,-184,349,957,555,-99,-533,-493,-931,-714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-496,78,259,416,979,-248,-1000,513,-1000,1000,-458,-14,-260,-98,-344,516,62,-820,30,1000,-29,466,-5,-503,312,-481,-550,-517,-416,-1000,677,-195,-1000,562,-128,1000,205,-838,650,461,508,1000,-1000,1000,-276,-52,1000,479,754,1000,-372,-875,1000,215,1000,-198,-783,617,1000,252,-630,-830,-1000,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{122,-976,536,1000,286,-109,-447,951,-1000,-666,948,-133,198,-126,395,593,-547,-867,-23,137,1000,769,1000,-319,-1000,1000,-722,391,-454,678,-300,1000,839,-305,-412,-59,739,-473,-1000,-768,-211,764,544,817,1000,-150,-1000,721,1000,-1000,-25,889,-1000,-207,139,-58,-88,982,-770,636,-827,-206,127,-115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{542,-767,-726,460,143,277,161,-1000,-151,-177,-30,915,593,880,-643,274,903,1000,-724,-767,294,921,111,-187,-338,-623,-1000,960,-552,339,-614,-71,-896,347,-1000,813,536,-235,-65,385,-874,502,754,-885,98,-349,330,373,20,-490,-737,69,800,-297,-404,-1000,-141,232,523,220,778,1,-340,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-58,-1000,-623,-1,-197,-597,570,-328,224,577,-1000,1000,149,694,379,-816,1000,-439,143,-1000,-436,-1000,10,-71,284,-1000,-550,-812,909,-382,343,-484,92,440,582,-324,-1000,534,996,-23,-606,1000,-499,612,-411,-66,-462,-1000,200,-1000,-626,-1000,1000,-24,-833,-825,59,378,148,-1000,477,-97,111,780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{188,-601,-784,-834,-352,184,672,-329,429,105,-551,1000,149,407,327,-369,16,-131,249,-1000,1000,-1000,508,114,142,-1000,-720,94,1000,265,507,1000,386,221,245,-928,-616,-74,-215,-1000,-219,250,-147,466,-392,93,-1000,-1000,-1000,-1000,398,-455,167,882,-284,-793,314,20,-405,-496,1000,122,194,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-28,455,-973,63,433,199,422,429,-750,-35,882,1000,-458,-355,-287,-441,361,-756,256,214,-472,-261,540,599,-435,-278,901,-328,-100,-873,-575,967,902,594,462,650,-435,-61,308,-739,-687,750,705,-556,14,456,-497,324,293,375,678,378,107,-644,1000,-298,0,876,1000,904,-1000,59,-597,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{288,618,269,-99,1000,397,-1000,182,-58,1000,693,-723,690,-1000,-4,-630,142,656,-97,445,-530,-497,1000,709,358,-395,370,-1000,7,-140,87,778,996,1000,-952,245,-286,-344,-269,387,-648,944,-507,-551,596,-640,992,422,-737,-12,-306,52,437,347,-611,-668,891,319,166,-710,-582,-492,-447,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{903,-890,1000,84,44,-41,8,839,1000,1000,206,-1000,443,-278,-8,-24,223,877,-1000,333,-85,-476,-919,-705,-982,186,445,-413,262,-433,849,661,372,396,-1000,302,-723,333,-987,707,589,608,-1000,-120,-548,151,664,1000,-363,743,-860,-708,343,-101,-1000,889,234,-529,-1000,-1000,-181,227,-492,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-29,-97,-208,359,248,324,-10,604,-143,800,1000,570,172,48,546,-448,361,-491,-777,869,-681,-301,-115,307,-841,-410,383,-413,263,-683,-316,262,902,514,138,539,-371,-213,239,-53,-549,1000,-337,143,-362,-32,-538,343,-166,447,-29,-64,-187,-287,314,-165,0,264,248,904,-939,-110,-793,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{27,-1000,81,-1000,-1000,-611,535,187,421,805,529,-419,-94,-134,556,131,665,-538,-401,333,142,-18,13,-586,-607,-109,974,-599,42,-58,-568,1000,561,529,-64,481,-70,48,-137,474,653,492,-1000,-685,53,929,-163,525,293,1000,-301,265,200,-1000,-404,311,-85,761,396,201,-732,354,-122,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{287,915,-646,452,1000,700,442,705,-803,-49,882,139,61,-426,-453,-859,394,-805,1000,349,-40,-196,1000,920,1000,-88,1000,-328,669,-628,190,1000,1000,827,-72,445,107,97,-296,-1000,-896,750,716,-506,-494,168,753,599,-99,226,604,-131,347,-381,1000,303,21,1000,1000,227,-889,-997,-489,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{872,398,-1000,-114,661,-456,909,879,-752,114,431,1000,-1000,-531,18,-715,-473,-987,-1000,-493,-249,-671,419,882,-1000,31,1000,57,-517,-904,-547,1000,483,403,495,649,-89,-203,536,-643,-101,1000,705,-1000,279,175,-618,262,610,-497,-1000,-41,189,-1000,1000,-591,-406,668,683,904,-1000,261,-89,-929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{439,278,910,-219,1000,-622,1000,185,-1000,-886,760,-788,-236,517,234,254,7,-749,-108,262,995,125,1000,58,-82,-1000,-999,1000,1000,1000,899,-1000,353,369,-874,-1000,104,-1000,-309,-1000,-229,777,-1000,1000,-252,137,-900,243,733,-486,-26,-450,11,417,148,969,1000,481,-1000,-732,125,-66,-874,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{357,1000,33,957,379,-273,-934,-937,158,-1000,-100,359,531,521,-694,348,-622,822,-329,90,1000,114,972,-778,347,-1000,-1000,331,1000,-1000,-908,1000,235,1000,-1000,-228,-736,732,21,-671,455,-272,-420,-356,-982,-1000,-1000,459,1000,-240,101,573,-1000,980,153,-520,-361,-955,-20,653,-1000,-132,-209,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{768,-495,62,66,-1000,666,1000,-168,512,-566,-145,211,-323,642,-400,-512,-777,-400,-503,-707,472,-1000,-315,-1000,-650,151,-745,-1000,863,898,576,-428,421,-400,968,-442,-571,-685,-700,-136,269,-246,-957,200,547,0,-114,261,-103,1000,249,-1000,633,543,643,1000,98,-1000,751,-844,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{100,656,591,1000,1000,-889,-360,-1000,-539,-1000,-232,58,-809,774,-762,1000,206,-686,-469,-252,-742,1000,732,-210,193,-673,-237,501,801,-182,114,1000,545,-417,-489,85,677,277,-933,-1000,-1000,187,256,385,-118,34,-805,-799,1000,-305,544,412,-935,-43,497,22,-998,-752,-1000,615,-462,-375,1000,-293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{370,584,714,-610,-43,-302,681,-672,91,-1000,-71,-18,-623,109,-1000,-476,522,1000,-246,774,592,-848,1000,324,-514,-471,-655,111,967,173,798,-362,682,449,1000,-1000,-41,-550,170,-1000,-1000,151,61,578,-635,-810,-863,-580,984,437,-812,-875,-955,1000,740,244,802,-1000,-1000,-587,837,-976,-331,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,593,-610,-299,-796,12,377,-1000,-826,-100,73,-674,-540,-284,-1000,280,1000,-105,332,1000,1000,-312,1000,-959,-106,-1000,272,1000,1000,1000,-1000,446,1000,1000,-1000,-736,-1000,1000,-671,-592,-440,409,-72,1000,-810,-266,459,736,1000,-812,573,-663,1000,153,320,1000,912,-870,1000,-200,-909,-1000,-369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{155,272,770,-933,-647,-373,-21,448,1000,600,-530,-11,-1000,-829,-257,1000,689,244,692,-234,140,228,-833,-748,-616,218,82,740,149,451,628,-154,-306,315,-38,-986,-86,-1000,-866,263,-199,409,-858,751,101,1000,589,-718,503,-378,-217,-430,-629,63,-1000,177,-165,-171,73,231,-33,-1000,915,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{718,-1000,-96,-1000,-388,-802,522,-319,-1000,-1000,-804,358,-1000,-193,339,562,-233,-1000,1000,834,585,-882,-1000,1000,-623,1000,-901,-54,805,1000,972,-1000,863,-1000,1000,-1000,-661,-468,336,-510,-1000,175,728,1000,1000,822,1000,27,286,321,-1000,-835,-984,111,-332,1000,-32,-1000,-57,-150,1000,-1000,-1000,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-802,1000,-241,-578,17,-61,797,-944,-269,188,1000,-1000,998,-278,427,1000,-587,451,-1000,-383,1000,-1000,535,102,8,816,-1000,-157,-163,-173,1000,782,-136,-1000,76,-546,-408,1000,1000,1000,1000,1000,-881,529,-1000,-1000,-507,410,-1000,-1000,660,1000,76,-651,-695,1000,-273,1000,-526,471,-919,1000,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{962,-452,912,1000,464,227,184,830,-808,1000,923,-446,-1000,686,-1000,1000,1000,1000,682,-244,-500,1000,462,-440,575,100,1000,-837,565,-709,-905,-1000,-727,136,-193,607,-409,63,-810,1000,52,948,-881,-1000,821,-1000,-142,-398,-263,-1000,906,377,947,422,582,-273,28,-880,-292,986,-26,-660,511,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-318,291,628,-1000,-52,230,-818,1000,-898,221,352,-720,1000,712,1000,-987,350,531,80,-65,-716,289,-1000,937,-229,-584,-902,-469,-1000,34,-1000,505,-178,194,102,361,-722,-198,-1000,-431,469,189,902,-894,-1000,-349,-214,506,945,-613,-270,142,854,-1000,512,-1000,187,-879,-158,69,1000,-773,-566,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{664,-1000,1000,1000,703,1000,698,519,961,1000,671,-1000,-226,288,-1000,873,359,1000,-1000,1000,-1000,393,1000,-1000,920,-305,256,309,253,-1000,-1000,-1000,483,-788,197,-1000,1000,604,-1000,1000,444,1000,-1000,200,1000,761,1000,-1000,-1000,416,1000,1000,1000,960,1000,752,225,-514,-292,603,-1000,375,701,980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,689,781,-1000,-1000,-808,147,857,-783,142,1000,-405,-1000,186,-549,899,503,714,326,-738,-568,1000,-1000,-118,-624,1000,-205,-1000,391,-1000,936,1000,412,558,-1000,266,578,-651,703,1000,-354,871,1000,-1000,102,-1000,-1000,596,-298,-946,-1000,180,1000,-1000,-1000,-1000,1000,491,1000,-1000,302,-650,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-925,900,-821,241,-48,830,888,200,236,1,-197,964,917,511,900,-683,331,-71,-436,984,369,-688,-319,-339,713,657,-773,-585,542,649,-435,-137,317,908,-680,-368,912,315,906,773,-383,448,-428,-945,373,706,-809,348,941,-760,-103,-328,679,-527,171,916,-912,-288,0,-297,-652,-914,327,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,97,1000,-805,1000,598,35,202,1000,-168,1000,180,-24,-1000,896,349,708,-624,253,-531,1000,726,-1000,1000,899,1000,-150,271,-1000,-45,948,-567,-1000,-1000,-1000,1000,629,-253,217,1000,650,-898,690,1000,-1000,1000,-343,-1000,121,-49,1000,840,296,1000,352,93,192,82,-484,-1000,542,1000,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,832,677,-181,357,317,-694,-249,-226,757,333,-651,1000,446,430,433,-599,979,-708,-383,139,-1000,1000,-807,-33,-1000,-261,-309,-163,-1000,1000,-753,-236,-588,-327,25,563,1000,1000,206,1000,1000,64,821,-1000,1000,-1000,185,-1000,-1000,301,685,207,-550,1000,1000,-470,1000,-539,-1000,-1000,218,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{399,-574,850,-382,6,24,195,-422,49,11,-286,-779,67,7,1000,-73,55,635,-204,1000,-947,408,449,-269,-934,-833,-343,-736,-743,-559,-253,426,-615,-255,-433,74,-365,-262,-583,970,-106,-558,454,100,228,-603,-923,259,-1,316,246,-323,-210,-171,157,867,119,54,-1000,-344,-770,553,54,14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{928,-627,679,94,43,-339,-779,-833,-835,-764,-472,983,624,722,553,518,-212,423,-937,-724,159,482,985,45,790,-241,347,46,-631,-96,430,235,-150,-995,375,400,924,-30,740,20,937,-673,-110,-817,1000,-21,-563,464,413,206,461,1000,-358,367,490,-261,190,-995,877,-536,-1000,-439,-493,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-200,-1000,754,297,339,-412,-621,-676,-515,-864,-1000,412,997,900,-348,-160,-348,962,-592,223,1000,1000,-114,13,-56,-941,-1000,-304,-1000,-1000,-212,-494,-349,-9,-121,-723,-17,-1,-577,311,768,-603,1000,127,1000,-161,-840,564,1000,154,1000,256,1000,-958,925,780,1000,-1000,128,-526,-1000,228,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{37,-275,513,-967,684,304,-160,1000,-5,-666,-1000,-133,349,1000,915,-324,-763,-4,1000,260,470,1000,1000,478,-297,469,-752,-1000,-1000,161,460,839,-676,-109,889,-1000,-1000,-263,43,1000,273,1000,-570,150,1000,-102,-245,-1000,899,975,-100,-930,-749,318,-65,-431,1000,1000,-729,-1000,-900,-556,-75,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{10,-1000,-500,-255,339,336,213,-381,1000,357,577,-165,-524,-144,-348,-608,307,1000,-230,-1000,-274,-428,-114,342,407,-173,423,-304,-419,802,-1000,459,-834,41,43,1000,-864,162,-577,432,202,-1000,1000,397,-228,-161,-662,-7,-792,-672,-52,-603,-1000,1000,-653,1000,202,-1000,-788,582,-40,961,999,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-128,-32,795,229,367,162,684,402,-518,1000,1000,956,146,50,567,566,-744,-69,1000,231,746,604,-864,408,-544,83,19,-349,320,-1000,206,499,-472,309,-885,222,-521,-1000,993,-949,119,183,827,796,-294,604,-50,-404,1000,-645,193,447,739,649,120,-970,-1000,-204,-993,265,200,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-472,557,-205,388,1000,627,345,-568,-1000,84,59,280,730,1000,-1000,-1000,722,1000,-526,-106,871,739,-1000,-444,133,-660,-1000,-1000,775,630,-770,616,-793,32,-256,-1000,1000,-606,542,893,39,726,-141,1000,-976,-444,505,-623,-533,648,-1000,-615,-191,-12,708,1000,462,-414,-753,-689,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{613,-1000,-1000,755,882,371,-201,-1000,2,846,395,216,551,-458,-1000,-1000,-1000,-1000,-1000,-1000,406,613,1000,-885,-517,736,402,1000,449,1000,-1000,-718,-1000,-499,630,734,1000,-116,-193,1000,27,-660,-189,361,-1000,-1000,1000,-1000,1000,-702,201,1000,-1000,1000,442,-1000,-1000,-808,1000,-229,350,822,857,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{16,231,54,34,-447,1000,-429,-308,38,-387,261,1000,-1000,1000,-821,-517,-1000,-7,1000,1000,691,1000,8,-755,-52,1000,-518,1000,-1000,-826,695,325,386,-1000,429,944,-1000,779,820,490,-33,393,-1000,945,-604,1000,-622,1000,-415,-163,833,1000,700,-295,351,338,-1000,618,-1000,-489,1000,1000,-1000,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{791,-906,-391,-604,-279,310,663,-766,-237,90,146,908,-369,483,-1000,133,-1000,-1000,-686,-88,-187,89,1000,-814,-683,-225,402,1000,-951,1000,135,300,-133,429,-123,-131,1000,1000,-31,1000,-259,-227,-1000,78,-1000,-1000,595,-1000,574,-344,173,1000,-1000,894,-490,-1000,-46,925,64,-784,-66,642,1000,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-956,216,-763,-711,870,1000,-86,-366,-348,476,-611,-384,422,-173,87,-107,-94,83,238,-17,208,516,-859,-1000,349,619,-7,-759,513,1000,-100,515,-884,557,-927,60,-304,398,1000,-315,21,-1000,-566,512,-400,387,99,21,91,749,658,143,258,-144,752,-454,-139,-255,-115,-594,-104,443,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{791,-1000,1000,-953,-279,642,663,87,-235,-65,219,450,-369,456,826,-685,-839,-226,689,1000,58,89,774,-606,-973,643,221,30,-1000,464,435,34,492,-526,261,-289,-668,-226,661,1000,-109,1000,-1000,-775,50,-102,429,353,-329,-275,173,1000,599,699,-336,-1000,331,113,-1000,-407,-390,-488,1000,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,407,704,-265,896,659,207,1000,-1000,-563,-215,-324,-880,24,1000,-719,1000,250,1000,911,-153,775,-465,-430,92,400,-586,45,-130,-952,286,-246,206,-1000,803,732,-1000,-875,1000,-660,-819,1000,93,-964,308,760,513,-146,79,-1000,-58,673,1000,1000,410,-1000,219,242,462,1000,82,112,-71,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{98,-643,449,-1000,-413,444,-172,378,-17,-723,459,-547,-783,690,258,861,231,539,1000,1000,82,169,-443,-709,-883,1000,1000,-853,-794,-764,919,281,995,-689,-57,1000,-1000,-304,672,337,-118,111,-627,-525,1000,1000,-307,993,-672,-141,34,606,274,-1000,1000,579,279,-1000,-1000,-632,406,-920,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{430,572,482,-313,-714,-426,-108,350,174,661,-1000,560,-27,657,544,671,-771,169,-850,72,-1000,-1000,-715,394,-299,-283,-507,848,140,1000,-717,-1000,-746,1000,49,-151,967,-795,-316,-893,-326,323,-365,26,-393,402,33,390,828,785,-806,-103,-855,104,824,-909,917,1000,135,476,-507,-1000,564,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-950,408,-16,-993,1000,-63,-1000,291,-923,-698,1000,-543,40,-702,-151,-18,-138,403,1000,-208,333,111,434,-995,-202,-1000,1000,544,197,-473,606,-461,240,-104,977,-529,-1000,-158,1000,-1000,399,-64,-415,-219,1000,1000,-947,646,1000,614,-158,-605,-1000,-911,859,-640,-515,111,112,527,281,-297,-1000,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,604,-1000,1000,109,108,1000,-555,-410,686,172,246,241,11,-558,-1000,776,-806,-459,-455,744,-955,324,-9,65,-145,1000,1000,-903,216,-1000,-592,-306,1000,-337,-78,241,-244,259,725,1000,394,-755,-500,-818,459,-703,679,891,-682,236,-351,706,-260,-547,-320,-92,97,254,110,1000,-83,-617,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-667,185,-143,-182,935,659,-241,327,-720,-1000,1000,367,201,-737,-764,94,-432,454,1000,-206,1000,-148,866,-536,885,-697,1000,-914,-149,-513,1000,559,630,-1000,266,212,-1000,280,694,714,848,494,-49,431,1000,405,-376,-308,-401,599,1000,-798,-680,-1000,-222,377,-935,12,1000,219,545,243,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,680,-43,1000,-1000,1000,924,-836,-691,692,420,1000,297,-496,-634,-988,-481,-395,-1000,-600,821,-1000,127,-382,932,709,-55,47,-1000,1000,-625,-424,-1000,-172,-1000,732,1000,-871,345,750,547,1000,-1000,217,-1000,-461,-1000,2,1000,-1000,552,-191,-685,-260,-1000,-524,405,-12,881,-282,384,505,-468,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{91,-865,-341,-491,67,-574,783,102,299,460,-859,-318,-83,-888,-156,-562,83,-260,-836,957,-80,599,-758,-352,547,-1000,-381,550,113,-643,-700,128,385,1000,-205,-867,-299,1000,1000,-755,569,-255,-421,-942,-194,-179,422,324,213,-348,-25,160,-426,1000,-418,1000,20,385,-43,-482,-709,936,-1000,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-767,-481,-784,480,-1000,794,129,-404,-933,-976,877,654,538,-151,-804,-539,1000,-874,1000,961,1000,296,1000,-920,895,394,114,-713,-1000,-59,924,1000,-339,-399,696,887,1000,1000,-406,1000,1000,27,18,858,969,-317,-555,-237,19,-311,1000,103,1000,-1000,623,1000,-1000,877,473,758,1000,-92,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,743,909,560,612,-307,90,417,-422,721,-1000,335,682,1000,-214,-2,-238,409,-660,-102,-722,-1000,-418,1000,464,-590,-591,112,-226,727,235,-166,-13,-141,-777,-249,14,-471,120,-1000,-341,464,69,-443,-872,693,-563,315,924,481,-968,-606,-450,601,95,-1000,518,1000,704,597,-368,-1000,-683,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-771,950,373,-203,633,-357,-781,908,89,-781,466,239,749,-596,-934,12,-510,-660,-858,400,-757,869,181,467,881,-684,-65,-956,-777,965,-250,356,531,-181,183,-793,854,-724,978,-958,-475,886,-282,-698,167,520,-182,177,689,-86,924,-968,-73,872,-788,-386,-201,168,-377,451,174,-413,639,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-876,889,246,398,-401,-218,-74,-63,-728,-269,-592,-580,-284,-741,961,-500,-6,916,993,626,-485,-460,739,29,278,192,-886,-225,32,662,-89,-31,-291,-124,660,85,-553,-456,-301,997,-538,-65,-763,341,-878,-172,-440,501,-346,-676,882,-241,-888,513,833,901,440,765,-930,-77,810,805,100,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-355,552,-181,652,-444,-126,-304,721,-268,321,-649,-699,165,-1000,1000,-269,-213,-426,210,388,-1000,1000,-251,-1000,-757,1000,445,554,-393,-33,-844,738,-964,512,-8,-1000,482,-305,274,-328,-240,275,-1000,913,-93,29,-1000,-1000,-499,46,656,230,540,-94,594,251,-331,1000,-1000,44,400,1000,-975,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-398,854,928,1000,-780,-126,697,577,-1000,-6,-555,-717,-929,-1000,1000,-269,-386,-489,515,339,-732,623,-649,-1000,-823,1000,-185,-19,742,796,-847,904,-1000,-126,148,-1000,-69,-130,-239,282,-896,-328,-458,1000,-311,229,1000,-452,-752,-32,1000,-18,-16,827,-444,1000,-10,674,-1000,-985,1000,1000,386,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{670,749,-13,1000,-603,-126,697,481,-1000,454,-575,-1000,742,-852,-35,-1000,-1000,288,-37,-674,-945,-1000,-400,-1000,-478,1000,-1000,-902,-510,129,-1000,891,-1000,570,148,-869,-1000,-257,-679,361,-1000,1000,380,190,-413,-388,1000,-438,-218,-281,-286,1000,-994,957,-444,1000,745,343,-1000,252,-675,1000,386,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{300,320,-196,257,729,-1000,919,130,-53,605,-1000,322,1000,50,79,670,-862,-1000,-250,1000,173,425,8,-1000,-1000,868,1000,-107,-743,382,-1000,1000,-836,-117,-192,-769,303,-363,367,296,565,827,-1000,986,-650,-89,-194,-98,-751,771,65,621,1000,358,167,283,-1000,-389,-1000,1000,-1000,1000,1000,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,229,398,-411,-448,708,-325,-96,-64,-644,225,321,-524,-934,12,355,816,13,978,405,272,738,467,57,7,56,229,-777,662,-250,-135,-55,-443,-197,-231,-553,-525,606,387,245,334,-282,593,-616,-6,-919,1000,-126,-560,1000,-968,-73,972,418,-27,-375,168,-445,67,829,517,358,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{396,560,-262,175,203,749,-168,-282,498,-841,581,-167,-264,908,-940,-510,-417,794,-953,586,-478,-139,-943,157,466,-558,27,368,658,-668,586,43,954,-839,990,538,-435,62,-4,223,272,-715,232,-24,-802,-486,945,745,-654,-619,389,-563,-756,-132,-405,-802,326,-854,753,481,938,-249,-959,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,844,-278,-941,-750,474,-631,-177,535,505,77,518,1000,426,-444,-383,-518,-843,-166,565,1000,878,-1000,669,-554,-1000,107,-496,-953,1000,-14,135,-1000,168,-922,-44,1000,688,1000,273,217,-1000,630,170,745,929,-1000,-1000,1000,-406,-1000,929,717,-947,558,-54,1000,-239,-173,68,-208,951,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{111,558,-962,-418,72,251,-72,1000,-75,10,-40,36,-607,391,-475,792,286,1000,320,-477,-880,815,-552,-1000,-323,-869,-1000,-1000,317,18,1000,955,468,-400,-805,96,-506,446,1000,-304,-744,650,-580,625,509,664,18,793,1000,-185,374,361,91,174,-380,-328,616,154,-501,117,-59,202,-522,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-994,-182,-197,-534,106,522,-1000,-394,-272,-572,134,422,-374,1000,-494,1000,610,-156,1000,-517,608,431,-38,43,-1000,-979,-397,65,-570,20,-626,-389,837,498,-1000,977,-774,396,890,693,-317,311,-1000,794,721,-112,-216,1000,231,549,-583,-966,1000,-536,-204,212,-659,1000,-135,407,1000,-48,341,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,443,1000,-885,-113,-257,-971,17,-905,-131,16,587,-663,1000,-456,1000,1000,46,1000,-129,1000,-557,-503,735,-761,540,522,426,-263,88,-854,-999,955,-953,-1000,752,-259,771,-8,624,-631,199,1000,268,-651,-911,-677,850,-503,1000,204,69,1000,43,-228,1000,-10,1000,37,1000,865,127,164,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-944,-543,664,-771,-442,-295,-952,-292,-689,-835,661,272,337,389,-690,789,830,-276,616,-338,706,997,-386,-206,-858,-617,465,88,-614,477,-884,-269,894,915,-783,796,-445,530,-132,188,-858,-6,927,890,-556,204,-537,609,565,194,-278,-578,785,-677,474,858,-928,662,-9,915,829,32,46,-998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{92,-514,1000,-71,1000,-400,-752,-532,-541,-714,570,534,-1000,1000,-31,373,781,94,691,-120,-272,-374,848,427,186,235,-1000,290,-41,-438,1000,-204,652,-407,-558,741,206,1000,569,1000,-54,954,-743,689,408,17,587,-218,-1000,1000,-708,629,-168,739,-1000,851,258,1000,-651,492,775,-60,-669,500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-612,-289,-881,-1000,-349,1000,-246,699,576,-635,365,-315,577,316,337,400,353,635,400,-82,-1000,1000,-71,13,-400,965,-701,-898,-1000,400,706,-643,1000,400,-351,-1000,-988,138,330,-1000,387,-749,1000,1000,-1000,-1000,-1000,1000,1000,-772,817,-1000,-312,-919,410,-889,-412,-59,444,104,-482,-307,-364,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-168,877,-94,-576,409,-299,234,-598,-626,309,609,-773,399,660,716,-1000,-964,-114,-1000,-703,-635,1000,89,-1000,-286,9,-1000,-750,26,-778,1000,306,-692,-694,546,1000,-772,1000,796,-361,-867,811,-1000,528,-370,859,409,578,-64,-604,875,-580,951,784,-951,-697,437,403,-936,-1000,-630,-1000,261,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-73,240,144,362,-202,-694,114,235,377,-639,-366,-568,-1000,-461,181,390,248,89,-1000,413,-179,-553,-1000,444,-19,-1000,-264,333,284,410,174,-326,1000,-1000,269,43,-12,677,1000,701,-304,-791,-218,271,869,619,722,403,-305,558,211,1000,-295,-19,1000,18,1000,-1000,484,-947,185,232,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,740,-238,504,-743,-336,-470,-241,1000,1000,-161,549,-209,1000,-149,1000,-383,469,-26,735,-1000,643,783,1000,489,-874,-975,-407,-574,1000,-765,-962,-667,-7,-606,996,-1000,770,1000,-783,-1000,-245,919,-278,-953,-1000,1000,1000,614,-1000,-386,397,414,-1000,-815,1000,-139,691,-1000,891,167,126,-269,-377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-262,-603,-733,183,40,1000,81,31,1000,1000,-71,-261,-204,-608,223,43,-710,1000,-20,837,-1000,76,-639,1000,187,1000,-723,-105,-1000,-381,396,163,-424,-329,-435,-95,274,511,351,-1000,104,-474,-517,427,753,437,1000,-698,-100,-432,42,290,-577,967,-145,303,-686,-60,-1000,-442,-1000,-307,-180,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-315,956,-832,1000,-458,354,432,1000,-542,562,-257,1000,176,1000,-277,1000,-469,1000,-680,1000,-1000,580,516,430,967,-359,-872,1000,-1000,-181,-890,26,-925,435,-3,-167,-948,1000,987,-465,-1000,-308,-179,886,-215,-726,322,1000,198,-1000,-694,186,758,-1000,-15,820,175,1000,989,538,-381,148,-446,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,269,60,149,-381,373,1000,-1000,311,276,543,354,-400,-668,589,-810,1000,336,1000,400,-216,-183,-258,387,415,-625,1000,250,-1000,-217,-33,-1000,1000,1000,-1000,-778,1000,1000,181,-224,-1000,1000,1000,-632,-1000,-1000,1000,-501,-689,-1000,-1000,180,-101,-55,-492,458,1000,1000,319,1000,1000,-1000,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{218,524,-723,1000,-319,75,-696,439,-288,440,-1000,797,422,-631,-301,601,97,-564,-1000,935,-1000,279,608,780,760,-1000,-1000,-1000,-1000,34,-1000,-695,-1000,1000,-731,121,-207,1000,1000,-627,-627,-1000,1000,173,697,-259,1000,1000,362,-846,-520,740,1000,-1000,-880,-102,496,518,-1000,1000,-540,521,-1000,-612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,740,-1000,504,-614,1000,454,298,1000,109,-259,924,-556,-324,-84,990,-1000,202,474,702,-1000,920,1000,1000,1000,-530,-605,85,-932,446,-1000,-789,-680,-306,55,236,-758,1000,461,-858,-1000,-245,-105,1000,-482,-1000,500,755,494,-1000,46,242,414,-1000,-382,1000,-907,1,-1000,-192,-1000,-258,171,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-6,-1000,1000,1000,-942,-752,369,-784,-660,-954,165,-906,275,-1000,1000,214,-1000,1000,-585,1000,-850,-883,1000,484,-972,1000,-1000,98,-1000,906,1000,-1000,676,-1000,-693,89,-1000,1000,-62,-886,1000,-1000,768,-727,1000,1000,913,-1000,386,-567,-1000,1000,-1000,1000,-316,-1000,-862,1000,-72,-945,-428,697,-1000,756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-821,665,-1000,226,-1000,725,-62,308,-229,-557,-307,-452,119,-92,493,49,-770,-50,287,585,-547,604,1000,11,651,-624,-353,-618,-381,-7,-801,-677,-215,-415,-431,-1000,-408,317,-1000,-407,-1000,10,-256,950,-509,-743,-379,1000,371,-232,101,273,1000,-1000,-828,92,-465,-773,7,-1000,176,-540,-414,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,651,853,-980,-594,-566,-769,-1000,-809,50,-617,332,-1000,1000,869,-840,771,-400,787,-948,-208,237,548,-1000,992,-1000,-1000,-648,1000,1000,-1000,1000,-698,-455,1000,-785,448,719,-716,-726,-1000,1000,-1000,675,1000,1000,-1000,-383,-1000,-994,673,257,231,-1000,-1000,-330,971,-13,141,362,94,-951,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{710,-1000,-1000,-1000,1000,517,222,-6,171,1000,-1000,-1000,457,689,802,-170,630,-1000,-778,-135,-207,414,-113,-1000,351,864,1000,1000,-1000,-597,-1000,1000,915,-620,-683,-91,-1000,-765,1000,-477,-8,1000,-1000,283,449,-27,-1000,29,97,696,647,1000,54,280,-1000,1000,-1000,-689,1000,198,-1000,558,5,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-170,843,-716,-568,531,-231,-245,284,-17,1000,776,-25,271,20,-248,220,-231,-436,-464,-909,1000,-47,508,-335,-400,786,-776,895,394,387,-703,218,-1000,-1000,515,130,-57,255,-58,228,211,429,-648,-1000,151,725,-1000,-247,377,852,148,-89,545,-421,-468,-91,343,-657,110,851,-225,735,213,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-666,46,-491,-761,798,-987,-595,-619,-404,747,499,-242,480,397,57,-652,-266,185,-134,-239,-130,-789,518,-176,-592,-276,-666,571,-586,-763,-544,632,-193,-563,540,429,-572,477,943,985,337,954,-633,-354,881,-366,460,563,-311,818,90,394,-26,-851,-305,-640,218,-416,-600,518,-309,309,447,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-454,-243,-1000,-729,1000,3,731,322,-179,424,1000,74,70,-636,946,760,-471,-721,1000,-282,485,367,528,393,-1000,1000,371,-40,-46,-420,-374,1000,673,-23,-775,-665,-828,747,-354,-648,834,-698,1000,755,265,-331,-602,341,173,-1000,-202,1000,844,-261,-361,274,-726,1000,-1000,-93,-25,-84,308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{741,-115,-567,233,129,1000,292,54,-816,848,-793,-634,-647,-671,219,-370,290,-807,1000,236,-1000,92,-1000,412,-342,177,52,1000,-594,96,-1000,501,1000,11,-1000,842,-82,-261,922,57,-1000,-378,-728,1000,-439,150,-1000,-837,-73,564,-518,578,732,295,-1000,759,-299,-321,838,-1000,-83,1000,-931,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-388,-862,-1000,-1000,1000,-711,77,-407,-450,400,-92,-863,270,245,1000,433,-420,110,-770,-402,-337,247,839,-898,-973,460,187,787,-1000,616,-1000,-528,-566,-1000,-840,-1000,-567,439,-723,774,-8,1000,-1000,-126,427,25,-621,-1000,-296,-60,1000,1000,50,-1000,-1000,890,-1000,-137,793,657,-145,733,707,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{400,-1000,-608,-1000,231,-280,-755,-85,661,394,924,1000,1000,672,-1000,1000,182,365,-1000,400,637,514,1000,-504,1000,-948,1000,462,599,96,-5,-611,-429,207,757,-587,-906,-400,922,-558,519,1000,-334,9,-439,28,-1000,260,835,-562,541,71,313,218,-1000,-184,578,-315,461,198,245,-1000,1000,862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-528,965,-920,-368,-278,-157,724,-101,376,-193,-386,780,888,-950,-301,325,594,251,-715,757,-621,-928,642,-194,-871,-40,287,131,-432,-368,-327,-443,20,289,-996,-958,-43,-39,-91,-495,-549,-342,409,638,530,-127,58,-564,743,-396,-1000,149,218,-489,869,1000,-390,840,813,216,968,-860,29,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{300,-240,-142,-358,382,-643,-509,197,-238,432,-1000,-93,-842,341,717,545,174,673,1000,181,1000,1000,-856,-928,-603,284,-363,-179,-612,-1000,-940,754,312,-995,-450,377,-19,648,357,763,-334,-541,37,476,-621,-543,787,83,-559,638,-339,1000,-386,-581,61,527,898,149,171,362,257,42,233,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{471,-253,-173,654,-346,-416,84,-400,376,1000,-472,-701,-657,413,744,-440,-831,-172,686,702,1000,276,-820,-1000,112,1000,1000,682,482,-742,-762,382,364,256,-177,1000,-540,220,-483,1000,-1000,413,-157,360,-982,-420,1000,-292,-38,1000,14,1000,-270,1000,-237,-404,1000,-49,-737,-833,100,377,587,-85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,1000,-720,750,28,-68,1000,1000,857,477,642,-701,1000,650,247,854,-1000,-1000,354,562,188,-93,-665,-371,270,225,-336,550,-1000,1000,-910,255,418,-1000,-485,-404,608,-1000,303,474,-1000,-565,319,1000,-357,559,407,1000,-1000,9,-672,-912,1000,594,314,1000,948,1000,-985,-812,-746,288,-1000,-768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,540,-911,209,-268,-697,-413,447,962,1000,-286,-495,-70,292,786,1000,-818,-259,175,-248,1000,-13,-354,-1000,-5,1000,523,157,1000,-1000,-128,1000,246,-548,-1000,1000,-705,-305,23,1000,-1000,-227,-278,171,-826,-916,1000,-708,-1000,882,-1000,911,-1000,1000,-178,705,1000,5,-1000,49,1000,-939,-502,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,203,-107,423,833,-1000,-222,406,-602,979,56,-1000,-486,1000,78,357,-535,-824,857,-119,996,975,-856,-1000,628,1000,-34,1000,-346,450,-627,933,312,-995,1000,377,90,-653,82,763,-823,742,162,868,-1000,9,736,1000,-1000,628,-232,140,-209,1000,-751,426,1000,-290,-1000,-306,-1000,456,-855,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,395,688,1000,527,-687,978,66,-1000,1000,827,482,-745,-488,-1000,1000,-883,-1000,1000,-505,-1000,-422,-1000,-1000,841,1000,-630,1000,356,1000,534,-956,1000,-790,1000,-854,-432,-1000,-900,842,-795,1000,678,1000,143,-242,5,1000,-769,1000,914,301,1000,1000,452,-159,1000,547,-583,-1000,-1000,1000,-535,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{549,923,413,302,120,351,561,882,914,-114,-456,626,653,400,-166,-54,77,326,-134,244,-907,-474,-140,1000,-874,-903,-425,-326,-601,495,1,-779,435,-417,-182,-564,-52,-74,321,-263,-222,120,27,319,93,-215,73,257,-47,-333,-247,-67,-128,-1000,550,1000,-292,1000,-478,-120,983,-174,-1000,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,38,-468,506,395,-1000,-937,1000,-483,979,504,-1000,-680,797,1000,-300,-934,-1000,1000,-631,1000,-77,-1000,-1000,1000,1000,452,1000,-1000,594,-410,1000,46,-1000,1000,1000,610,-965,832,1000,-1000,991,-210,879,-1000,428,791,1000,-1000,556,-453,-165,-779,1000,-1000,561,1000,-945,-1000,666,-1000,640,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{584,330,299,259,808,-364,317,-131,-869,985,-463,750,-848,494,634,833,748,-596,-934,-714,-888,-352,-990,-254,93,393,-428,943,304,-595,501,-860,-429,445,-758,879,21,-724,439,-277,325,313,732,897,-559,-513,75,-944,-414,450,-313,753,-906,906,236,595,317,-444,-127,598,-195,-100,334,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{356,128,625,-438,1000,-696,896,109,-683,1000,-498,1000,-680,-193,-136,983,1000,-1000,-1000,986,-569,575,-1000,90,-210,778,-683,1000,-892,-292,412,-1000,107,214,-551,706,47,-725,-763,-139,537,509,116,407,-959,-937,435,-52,-705,614,-559,1000,-1,1000,981,845,-292,-116,-148,699,-537,531,100,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-277,853,-502,681,159,-588,1000,493,221,700,-498,-1000,-453,-217,764,-131,-844,-771,-1000,1000,136,780,-903,1000,-412,-617,647,329,-1000,-1000,1000,-1000,812,686,-285,-398,-1000,-955,-64,-447,-287,191,-460,758,-78,255,-1000,174,-1000,64,-85,381,-542,444,221,380,1000,-276,-1000,-189,605,-501,649,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{429,135,-701,649,401,-550,1000,-193,119,787,-369,-478,-638,-373,-906,-235,115,758,785,1000,922,-477,1000,-89,-290,-267,137,-889,-791,-24,568,1000,414,-400,-735,-335,-322,133,-1000,-323,-1000,-165,285,-94,277,715,-381,1000,305,-1000,679,-1000,-793,-935,559,815,-351,-547,516,-1000,-188,-358,253,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-211,304,-36,-595,-317,-458,399,299,209,48,20,484,-1000,1000,708,-766,-877,-682,-404,835,-1000,-522,-468,1000,-199,-141,303,41,979,-175,1000,75,-125,1000,-933,-686,82,-1000,705,480,-746,-553,-232,-287,-1000,281,-635,-1000,-1000,-155,-791,660,-748,543,749,1000,1000,-1000,-1000,636,42,-303,280,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{597,1000,-1000,745,-427,412,253,923,57,-429,-59,-1000,-175,83,1000,-677,-482,-1000,159,1000,913,519,-872,1000,-909,-781,148,-648,704,-540,1000,785,-43,398,-244,-926,-1000,-305,717,376,-83,-836,282,683,-232,1000,-1000,1000,-775,419,28,502,-1000,-533,-32,49,994,-220,-1000,-784,1000,462,-668,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-865,-288,-57,-717,-876,-299,-171,256,269,-1000,261,-1000,967,438,-826,103,-412,646,683,-1000,805,359,353,648,165,-592,944,1000,247,562,1000,242,1000,-1000,-905,170,-434,1000,-415,-1000,-654,-177,428,-57,-38,-500,-1000,-36,-1000,-940,-1000,-783,-207,1000,521,366,-1000,-293,52,-467,-316,-1000,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{283,1000,1000,469,-271,309,-1000,74,-489,1000,372,586,850,-907,394,146,-101,406,293,149,421,50,681,-267,118,-785,-738,-76,-1000,-461,-854,-603,252,-5,348,712,-1000,-47,-1000,13,-373,-1000,-1000,1000,76,473,-816,1000,-260,367,427,12,1000,-32,-906,-573,378,-475,-117,-313,-1000,-1000,480,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-912,1000,1000,1000,-291,-606,272,-43,-1000,-514,-1000,-508,-89,900,907,993,-468,513,-594,-106,4,591,443,-689,-1000,-450,-753,-1000,-1000,-125,-224,-675,-159,1000,1000,1000,-526,885,-1000,-893,-482,56,-801,1000,-225,1000,279,591,-437,-437,741,1000,1000,470,-1000,918,108,832,21,-781,-713,1000,-108,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{978,-400,-400,-233,1,408,-1000,627,674,1000,1000,844,-515,-1000,1000,-375,224,-817,1000,1000,-200,-731,-162,-1000,-396,-790,-863,799,400,-301,-69,-825,311,-430,-463,177,400,212,-9,-6,263,400,11,-204,1000,105,-382,-400,-248,1000,-862,-548,-400,-1000,-585,-1000,56,42,-1000,712,60,-378,-453,-522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-226,488,1000,509,1000,-1000,993,-989,-1000,451,-1000,959,-1000,286,22,-1000,-493,806,-593,59,-1000,788,457,60,869,582,-292,-1000,-1000,235,585,17,-1000,-416,1000,-468,-1000,645,1000,-566,-718,-975,-953,764,-547,1000,-132,-400,-230,611,1000,35,20,1000,-391,6,-823,-408,888,244,-1000,804,313,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{798,-924,-791,228,178,-649,-411,-326,593,4,28,-60,612,-475,-700,-769,732,-331,328,-747,-131,-132,66,-195,758,-664,-643,904,504,540,726,405,-919,-2,-854,-919,720,696,485,285,807,788,575,-546,152,-269,591,-37,-662,-511,-181,151,193,-945,759,-325,-589,-851,9,-209,808,681,-605,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,1000,875,-776,552,1000,334,-711,84,-95,205,1000,-492,600,1000,248,326,868,14,416,569,1000,-1000,93,-82,-226,-1000,-424,-559,-1000,-696,1000,1000,1000,712,-1000,-302,570,188,-909,-1000,-726,1000,-1000,1000,-408,1000,-331,5,240,606,1000,977,-1000,281,-301,665,-1000,-1000,-1000,-480,654,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-43,-1000,764,-1000,167,-927,1000,727,-179,745,-853,513,-1000,-667,-436,1000,-1000,1000,-3,-101,-538,404,-121,-252,583,-1000,1000,1000,481,324,127,1000,1000,-772,576,-30,710,-1000,723,-336,854,-363,-239,-151,574,-81,978,-1000,-441,-1000,-345,1000,-127,190,131,-91,1000,-1000,636,16,1000,-1000,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{920,269,324,981,-614,129,565,837,1000,130,-300,-824,443,954,638,-300,-171,819,703,398,-1000,1000,298,-1000,-403,-1000,-398,-1000,478,-1000,-41,26,283,-676,1000,782,400,-426,1000,120,539,-843,-785,917,-1000,-453,483,470,-401,1000,832,627,1000,-926,-1000,-1000,871,-547,-99,-683,1000,-665,445,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-951,-1000,-703,-1000,357,-146,-2,398,-1000,519,1000,-162,-1000,1000,1000,-1000,409,735,27,27,-742,-1000,921,374,1000,-79,442,-639,400,962,-1000,143,-334,-614,-933,-181,452,169,1000,8,453,-1000,1000,232,155,-451,66,1000,-1000,-1000,902,400,-101,400,-199,-917,604,815,1000,176,635,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-954,-1000,46,-255,602,-1000,-766,1000,1000,238,279,-143,175,799,-239,588,-291,484,129,428,842,-1000,282,426,-687,958,778,-1000,-524,124,303,-143,526,851,177,-159,-364,323,-337,165,-279,543,974,538,744,-594,-470,-266,-515,-446,-874,-780,-117,-1000,933,-796,749,513,-913,740,-203,896,993,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{681,212,400,338,826,-493,-1000,-714,578,1000,-647,505,-1000,1000,-323,888,-1000,1000,-651,267,615,-1000,209,-1000,-1000,-140,1000,165,913,216,224,-1000,-449,641,1000,1000,-2,-270,1000,1000,832,-839,62,324,-653,387,400,-1000,-1000,1000,-274,867,-662,502,-1000,-575,-1000,-32,-1000,-924,-45,-672,-492,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-879,-766,-333,8,228,-341,179,907,867,49,411,-874,-569,390,-59,33,-426,-1000,1000,-110,497,-65,265,1000,1000,290,-539,406,-1000,440,703,900,1000,127,-560,-46,-92,1000,-669,-445,1000,795,455,-144,874,397,-1000,314,373,-1000,-32,164,759,91,901,-419,-713,214,539,-481,-510,916,1000,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-408,1000,-1000,-384,1000,257,44,26,-474,-897,1000,-305,790,-731,733,-220,1000,-19,-1000,-958,-409,-598,777,-1000,-1000,-1000,145,-1000,150,827,615,-1000,601,445,767,-24,1000,-625,1000,-712,-1000,26,-947,-125,112,1000,659,766,1000,473,1000,-802,-1000,-846,1000,-1000,1000,-1000,1000,-633,212,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{921,269,382,-980,-120,720,-215,387,-16,1000,368,-207,-424,687,-72,-300,354,346,-845,1000,46,69,14,-217,-164,-1000,823,497,1000,-629,-381,26,413,-676,1000,939,-560,-185,806,15,539,-84,-224,503,-1000,-212,483,-417,-913,1000,889,598,-112,750,-422,350,-372,-547,223,-683,810,-483,-658,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{197,56,382,-954,-114,735,-183,584,321,711,368,-107,-712,886,222,186,-281,670,-639,764,439,-389,84,-217,-496,-527,737,816,980,-721,-381,-626,666,761,828,939,-234,-82,469,-322,-213,-84,73,983,-956,-534,350,-739,-913,959,469,981,66,173,-71,-126,-372,126,-196,425,238,-177,-356,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{777,-444,-106,791,963,-721,-202,763,-1000,-153,1000,-60,674,320,-17,466,34,-688,533,141,-1000,326,-228,-129,1000,56,56,-484,1000,-272,636,329,226,-574,476,713,199,-748,81,1000,217,921,378,62,620,550,996,732,921,-400,629,94,541,912,823,610,208,603,259,-713,76,-138,-527,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-251,-1000,-408,1000,-135,449,499,257,381,-395,-1000,-815,429,-119,932,-956,-60,100,801,116,98,-1000,-258,-69,396,-631,-1000,-411,-1000,378,899,666,486,356,288,909,872,-213,662,-543,1000,-417,-576,218,-779,-56,400,-192,659,1000,201,1000,808,38,-1000,-1000,1000,24,736,-1000,287,-93,-4,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{915,-145,274,299,160,-317,290,712,-1000,553,-894,1000,93,-1000,-596,-28,1000,526,1000,580,1000,-686,-708,560,1000,-810,-1000,-449,-475,247,1000,-756,407,281,745,-306,-263,97,-80,-1000,-459,-988,818,-252,421,998,836,-72,-448,850,-51,-140,1000,663,168,225,-292,124,-1000,640,-786,-231,-43,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{901,-510,924,162,380,59,-838,-677,1000,1000,271,668,409,62,-516,1000,1000,289,-116,-573,40,-556,-164,-698,-337,-420,-911,-431,309,1000,813,1000,720,951,658,-865,-1000,-433,-1000,1000,-187,-54,400,-1000,688,-1000,-492,-855,645,-10,-177,328,372,-1000,1000,886,-130,596,327,65,-680,-865,-353,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{132,-1000,878,-1000,-703,1000,519,297,583,786,-632,-685,602,1000,-641,323,-1000,82,-195,-1000,173,1000,-407,-1000,-1000,518,932,475,1000,-469,-403,-806,-453,-1000,-1000,133,149,-592,95,1000,-228,1000,-862,400,-7,-302,-921,-968,947,-501,-67,545,-428,-753,1000,245,572,514,1000,-714,-1000,-1000,1000,-680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{888,-563,544,33,212,34,-703,-413,381,949,-26,528,302,-257,828,772,1000,436,-181,-244,301,-680,-415,-188,-303,295,-1000,-601,70,1000,869,534,924,948,660,-906,-894,-327,-96,-641,477,-286,580,-859,-619,-400,-45,-846,583,293,-28,950,539,-1000,990,764,55,139,-344,325,-776,-470,-547,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{893,-414,274,451,479,221,832,-1000,282,553,-950,-183,117,-515,-1000,-934,1000,526,-181,-747,60,-770,-1000,-210,427,-1000,-1000,-38,396,1000,538,1000,407,281,1000,588,-1000,-192,-1000,921,-434,-620,537,-1000,533,-24,-499,-561,142,1000,-721,-140,172,-1000,1000,1000,947,-1000,-142,90,-803,-231,317,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{859,-688,-344,-266,-180,-26,-388,202,422,830,-929,541,54,-1000,-641,239,681,779,927,522,909,-968,-1000,1000,-222,122,-1000,-1000,-485,1000,1000,867,602,940,664,-1000,-647,-592,-1000,1000,-280,-827,1000,-530,144,1000,1000,-826,439,1000,320,40,90,-938,966,478,572,1000,324,930,-1000,454,-1000,950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{753,-865,-320,217,-251,69,-420,329,-873,346,-839,232,835,-1000,-1000,-360,635,632,-491,1000,1000,-378,-1000,796,632,9,-129,-544,58,-1000,510,-327,-300,-144,266,-315,714,-673,154,673,-464,-796,1000,-1000,1000,1000,-178,186,509,1000,650,-383,948,-518,875,967,-357,48,-43,324,-326,-271,580,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-205,-865,-349,-368,-1000,824,1000,-1000,709,1000,-250,-118,-754,24,-640,-802,-528,87,-12,-1000,382,1000,240,-1000,-1000,-882,-173,136,1000,910,598,304,-203,-793,-251,958,-603,-360,-147,1000,-1000,537,-1000,-580,53,269,-1000,-998,1000,115,-1000,176,-135,-1000,1000,1000,1000,-777,484,-1000,-1000,-912,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-151,422,173,1000,738,-636,639,932,-798,-376,1000,-383,529,-447,-939,-1000,-409,327,-862,-67,1000,10,815,-205,223,-1000,763,-1000,400,1000,-774,241,-1000,271,411,1000,72,809,1000,-871,-364,1000,1000,1000,-1000,-330,895,368,-865,1000,-599,449,-643,-1000,-1000,1000,719,-677,-732,305,1000,97,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-153,-1000,348,-62,10,1000,-171,-603,-869,601,40,-400,156,-1000,25,-510,413,-271,-401,36,20,-761,741,-330,109,-206,1000,506,980,-266,-400,269,-850,-1000,-234,43,-25,-1000,1000,641,87,739,-400,-577,-409,-335,-139,-205,-400,1000,-1000,1000,220,237,-91,-649,-88,-267,86,-172,-298,192,130,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{17,-1000,535,-46,825,639,-562,-1000,-427,-641,202,758,-216,-1000,652,678,-551,-993,-680,-298,269,-209,154,-1000,-52,565,-1000,-1000,545,-315,-178,64,275,-450,-1000,-488,818,-1000,197,419,-13,-497,613,339,-211,-587,-293,677,352,101,-1000,1000,198,-126,-392,1000,586,-293,627,-275,118,671,18,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-784,-384,887,1000,-766,896,-790,-352,261,-417,313,1000,-484,508,105,1000,-1000,48,-441,665,-20,647,-646,-290,-1000,-18,643,781,-1000,16,994,549,370,-397,1000,-919,154,-779,-996,1000,-142,-1000,514,1000,1000,166,-29,1000,368,137,20,-1000,290,-1000,-256,604,308,1000,144,350,-930,-31,627,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,787,268,1000,-77,-589,-1000,619,-981,-503,1000,-470,151,1000,448,-45,134,-1000,-1000,1000,-308,-1000,-1000,-460,1000,-1000,-1000,-93,-461,498,-1000,-299,-312,35,-1000,1000,-1000,-154,-659,877,-290,1000,240,646,-785,-239,1000,1000,1000,-1000,1000,-103,-482,-42,779,532,125,-749,-139,101,1000,1000,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-705,-1000,1000,1000,1000,285,-596,-1000,309,-111,-1000,-123,-595,-1000,1000,315,727,-709,-1000,-127,1000,-585,241,-1000,-264,128,-750,-673,212,461,558,223,-314,-697,470,-1000,192,-1000,408,330,1000,-642,-59,344,-409,-35,-712,944,1000,1000,-1000,1000,-67,-1000,354,425,250,149,1000,-1,543,980,1000,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-109,-824,839,1000,693,-528,-572,-532,-869,601,-509,-605,-395,-161,303,488,551,1000,-239,-21,-1000,-604,-59,-132,228,-912,1000,-468,980,784,-92,-418,-994,-1000,-377,-370,-1000,-401,1000,857,163,-1000,-1000,487,-987,484,-968,-688,341,-292,1000,-130,248,-866,174,495,183,-259,-765,-77,1000,-393,-309,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-45,-1000,-480,-446,-1000,577,-859,409,309,287,758,318,-949,916,-473,699,612,1000,59,420,-37,-1000,-172,1000,34,-1000,1000,834,519,1000,-1000,-185,677,469,425,1000,-1000,-133,1000,-196,-1000,1000,-236,-1000,-1000,355,196,-1000,-571,-1000,1000,-493,-212,311,-366,-29,-346,-1000,-976,709,631,484,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,306,-83,693,941,277,-788,253,1000,-1000,-1000,402,-716,315,-1000,372,-649,-206,38,-1000,-769,360,93,1000,-65,-1000,1000,-1000,-359,-1000,-1000,-1000,-1000,-1000,202,-1000,-932,1000,504,-1000,879,-769,-735,970,-1000,-968,91,-1000,910,-908,560,35,750,-1000,-751,511,-1000,-383,-1000,-709,-24,-53,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-82,-1000,1000,-191,-784,5,699,510,-958,405,613,234,184,127,-866,685,667,-614,272,-262,-485,1000,154,36,-810,-1000,-43,173,368,348,-809,-1000,-31,1000,-263,977,539,-17,-248,-518,-686,-645,614,251,586,-542,830,-964,995,1000,-1000,-1000,540,-1000,800,-331,527,440,1000,753,-1000,504,-467,687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-603,333,-341,673,-173,1000,-913,-148,1000,-209,319,769,-671,685,187,902,-679,-822,-1000,-913,-449,104,925,-349,-310,-184,-27,-1000,64,266,-782,-1000,192,152,-1000,-1000,811,-171,-1000,-1000,63,-1000,792,-139,-220,110,-363,1000,768,-1000,-819,-503,928,71,825,1000,385,1000,-1000,-481,-396,1000,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-945,1000,-718,-110,-56,-113,131,-840,-16,-672,-256,1000,-736,-77,1000,-1000,-405,-331,712,-586,973,1000,-1000,-319,-616,-753,251,1000,-344,746,712,775,199,-82,-89,-222,-91,-31,1000,-595,1000,-622,-1000,-715,303,57,590,-714,-438,1000,-670,504,-597,501,-283,-634,328,-982,-606,162,-625,-683,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-160,-1000,-549,-1000,70,-453,844,-952,-12,1000,436,-423,-108,24,6,-129,640,-32,292,-1000,-1000,-220,1000,720,383,-945,329,-1000,-1000,-289,1000,-906,-1000,111,1000,350,-1000,917,-760,-1000,-1000,-49,-336,591,203,602,-1000,-141,934,1000,-1000,-690,-1000,995,496,217,942,627,1000,-1000,-789,-168,1000,-704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{198,98,-1000,653,-586,-219,529,849,-135,-1000,-529,521,91,-20,-617,275,752,-1000,982,-668,-242,-484,-708,-825,-1000,43,524,590,1000,-403,43,-186,54,-717,717,627,958,-358,735,60,-145,-1000,838,808,264,-600,792,-191,615,1000,-240,52,660,-1000,295,36,-170,-504,135,1000,-821,-445,-648,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{963,-4,-400,22,609,468,106,-1000,684,-866,851,-152,-386,1000,990,-1000,762,736,779,-979,853,-274,1000,-207,-5,869,435,-1000,287,-525,157,952,-64,-330,953,-123,519,1000,-1000,498,-40,952,419,-138,-40,1000,-1000,-15,-119,-1000,678,-355,-1000,321,304,1000,-191,1000,-1000,-602,-776,518,550,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{682,-1000,945,-422,-1000,1000,74,-648,-497,889,509,230,-184,-715,61,826,591,0,290,251,432,-942,-267,1000,770,74,-1000,-467,-975,-601,85,-878,-372,-232,679,618,-1000,-97,-1000,167,-617,1000,394,868,-552,-307,-461,-134,900,-780,431,-1000,-335,-45,624,462,977,1000,1000,-727,-804,-116,-701,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-294,737,286,-968,775,996,-358,456,-318,-201,676,174,-842,-408,-382,701,-607,-207,59,145,217,601,-797,-650,-268,480,40,-176,447,-868,831,86,469,-653,662,808,47,-444,514,-285,868,129,-734,-424,914,514,-607,829,-626,-857,-299,387,164,-266,401,-6,916,47,-949,-864,973,502,462,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-805,-237,918,77,781,145,-673,-371,-108,209,-437,69,-371,404,967,216,314,493,146,594,-118,-148,-610,43,-473,-259,-11,-533,764,211,433,685,-478,872,-148,116,700,196,728,602,643,243,-98,35,-537,736,-786,694,-219,286,729,163,-456,-808,436,-441,-132,-233,549,-594,531,412,82,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,1000,-457,46,668,366,-1000,224,-1000,42,1000,1000,-1000,-1000,-166,527,-15,966,-1000,-1000,319,1000,228,-1000,754,1000,-570,437,1000,-635,1000,806,120,-424,1000,913,1000,787,807,850,1000,177,-1000,-1000,-275,-652,-815,-376,-1000,-1000,-1000,270,870,-376,-680,-584,562,190,55,658,19,-440,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,247,-644,780,359,406,-1000,170,-726,1000,907,1000,-24,673,54,-461,1000,464,406,304,74,947,-1000,-863,64,-74,-952,-1000,-706,606,1000,1000,-570,174,1000,-537,587,-129,603,860,863,-753,-1000,-1000,-136,-550,-1000,270,-1000,-1000,353,246,-252,-370,635,-792,288,194,-33,587,417,1000,380,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{157,-1000,449,1000,1000,598,669,175,1000,655,-1000,-1000,-82,1000,1000,1000,101,-383,1000,1000,-499,-361,-1000,1000,-1000,-144,-203,-313,1000,-361,-980,-1000,-1000,96,-59,-675,1000,-197,-856,-462,-882,189,-62,-48,795,941,1000,1000,-250,34,-761,1000,639,771,1000,929,1000,-773,1000,-1000,784,1000,-27,-600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{835,98,853,-1000,1000,1000,383,961,-1000,854,774,567,-672,-201,-816,-352,857,1000,-808,-1000,-729,-463,-618,-695,-577,422,608,-1000,400,-711,1000,802,-434,666,1000,891,-804,-575,478,666,462,-421,-62,-810,244,365,-579,256,68,-1000,-549,-446,-15,-1000,632,-1000,-841,-872,-497,-298,-312,-68,513,153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{484,77,-528,201,1000,519,1000,-508,848,-683,157,69,-248,248,-332,-692,753,476,440,863,-151,-189,-987,265,64,-59,-55,164,-1000,-405,33,-649,-80,581,-822,2,92,-746,-362,-431,343,125,-117,468,224,563,858,1000,-208,-1000,982,941,-351,71,26,-33,451,-345,-1000,-1000,1000,-153,179,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{-967,-531,-102,-548,115,752,764,101,-1000,390,495,-878,548,34,365,919,792,-904,738,503,619,-1000,-278,420,-512,-1000,734,843,-1000,-554,-673,818,17,-116,-547,-529,710,19,-683,342,-286,-276,845,-469,357,172,72,-935,-267,1000,673,-448,596,-767,-89,-263,-819,-129,-992,900,850,253,-1000,-812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{-778,686,-1,288,503,-1000,-301,-978,-94,179,-489,918,-69,717,-489,-1000,376,626,-1000,-171,-1000,-1000,-272,-917,-724,686,6,-1000,706,58,718,-1000,53,260,-1000,-1000,-754,-877,356,-1000,435,-161,-587,975,424,-47,-140,439,567,-487,1000,1000,-592,1000,-559,513,1000,-1000,1000,514,-1000,393,935,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{60,-250,-591,-343,-127,-422,-602,357,-269,1000,-1000,-459,103,-180,35,417,905,-509,-270,195,838,413,-383,121,1000,-617,-403,-644,172,513,-421,1000,-175,-597,-214,630,20,495,-203,-326,-490,104,-429,-387,-309,204,127,-635,338,104,876,439,-324,-197,-563,-57,81,-1000,120,-42,708,258,-159,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{935,-86,472,-198,-922,502,825,-207,381,959,204,-978,964,97,117,884,703,21,-78,-201,938,147,-242,550,448,-782,160,268,-616,-579,-50,739,-900,887,-705,-830,586,75,-386,59,-440,-159,402,-587,183,392,968,-827,-163,677,604,435,422,-668,-249,815,-742,789,-646,125,786,-214,-195,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{205,172,680,-686,-477,472,-835,1000,952,423,94,-214,-216,828,37,-943,454,872,39,302,-169,1000,-727,770,211,891,-1000,-390,1000,1000,-151,1,-1000,1000,-937,1000,-797,1000,316,-757,-1000,853,-500,-117,-775,-412,198,-695,695,-778,882,1000,-198,837,-98,111,-125,-1000,994,-1000,-212,297,1000,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{673,-58,-605,-686,-536,789,427,-999,-222,-594,94,-214,708,-598,-351,1000,454,-856,459,376,1000,-144,-431,-1000,1000,-970,1000,1000,-1000,-42,-36,1000,611,-1000,-632,-494,1000,-827,-501,-36,-195,-560,-329,112,735,-202,221,-695,-42,741,1000,-633,71,-673,-740,-554,663,-1000,65,1000,88,155,-902,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{58,-1000,-431,-332,-213,709,-349,-472,-1000,-116,424,-224,796,-403,314,1000,618,-1,-803,773,1000,306,-1000,-400,1000,-928,400,662,-1000,74,-97,1000,127,765,-37,-528,1000,-170,-235,1000,-349,-283,178,-106,709,-420,-357,-707,50,168,1000,175,1000,-1000,-749,305,164,102,-588,662,362,-223,-154,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{-236,272,-656,-448,215,73,119,-1000,-201,-795,-41,-214,708,-112,-348,356,61,-425,-651,-10,179,-548,347,-1000,878,-358,1000,78,-1000,-275,1000,1000,611,-863,-506,-1000,810,-1000,93,-185,-168,-1000,25,343,1000,-214,656,353,-415,815,1000,801,1000,662,-1000,-554,1000,-651,-50,1000,-15,-592,295,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-878,-43,-921,782,344,-271,-378,-408,-398,417,888,-625,669,211,1000,331,-299,400,284,-1000,189,708,-88,-559,721,-34,-759,-430,-570,-163,11,-407,-52,445,-772,-845,-664,1000,-1000,960,-196,198,122,-1000,-658,842,667,-249,-364,-209,256,1000,-836,194,-87,968,1000,-344,423,416,134,882,-416,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-18,438,-717,1000,576,-219,-507,-1000,-41,-975,-114,858,528,-65,-31,491,487,338,77,-95,92,-288,412,-268,-1000,-746,-1000,1000,113,-110,135,-1000,-1000,1000,1000,210,-1000,329,75,485,-97,-177,1000,801,88,-798,185,-871,72,765,-10,-38,584,1000,-866,1000,-169,-580,-1000,488,-1000,-727,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-827,1000,-1000,742,59,956,-1000,-898,-216,932,1000,464,176,17,829,1000,294,1000,37,-1000,336,512,233,-375,933,225,22,-690,-1000,-900,190,-297,-431,785,780,-860,416,1000,-932,-116,-531,1000,1000,-1000,-171,1000,1000,155,-1000,-305,150,1000,-1000,249,277,1000,264,168,-610,1000,1000,857,164,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{39,692,885,1000,459,-629,464,-467,272,-816,483,-112,858,-774,39,-384,-202,847,1000,-343,803,318,-1000,-531,-1000,492,-368,619,126,-1000,4,-505,-1000,1000,620,-450,-282,-1000,138,-939,-348,-95,170,-803,964,-790,198,-392,-729,-301,315,-809,803,584,1000,712,-202,-863,110,-481,991,1000,217,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{259,-25,752,668,1000,-537,712,-510,-625,-834,764,274,1000,-448,-164,-522,894,-1000,912,-629,1000,1000,-1000,-95,-3,190,480,-569,824,548,211,-149,544,1000,-94,379,208,-684,-905,1000,-830,-239,1000,83,-1000,162,-47,-297,1000,-919,659,-840,736,762,1000,-44,343,-354,-59,-233,1000,876,285,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-827,-43,796,708,344,350,-722,-537,-398,370,943,-896,254,211,880,423,606,400,122,-1000,458,510,317,-411,1000,-118,-43,-382,-570,-158,11,-222,-52,1000,474,-747,-459,1000,-952,384,-240,198,122,-1000,-665,842,667,238,-364,-110,256,1000,-918,194,-579,968,1000,-343,-833,416,342,1000,-715,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{435,-1000,1000,1000,-697,311,94,-370,1000,-599,-76,197,-172,-548,1000,289,-928,696,-1000,-70,-1000,1000,876,1000,-925,739,1000,-17,1000,-1000,-1000,-822,-1000,522,207,335,-254,1000,-1000,-238,795,1000,-174,-1000,-1000,671,-950,27,-1000,-1000,1000,-1000,1000,-876,-117,152,263,-511,1000,1000,-343,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{463,804,248,-1000,-493,281,957,618,-1000,1000,-838,177,56,23,-1000,-1000,-725,-768,1000,-318,-376,-1000,-1000,77,-174,1000,-432,1000,-1000,-698,-94,-628,-537,-176,946,-556,-809,-1000,814,-412,1000,-526,-357,-93,616,-459,-917,158,589,1000,-763,-1000,-1000,-568,619,-1000,-818,-556,-1000,-1000,-481,-1000,-611,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,327,-220,939,-616,-509,73,-262,-664,-68,-1000,-642,329,1000,644,104,100,-553,453,-378,1000,-588,929,349,-330,-99,-1000,513,348,592,416,-360,780,-193,-828,782,-200,281,-915,835,-84,433,-213,518,-1000,734,1000,237,-1000,792,148,222,480,256,180,-44,614,135,-279,125,833,130,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{901,-352,1000,816,-1000,-225,94,228,1000,-578,-1000,290,524,-386,842,-165,-1000,1000,226,-562,-1000,1000,1000,1000,1000,337,679,-77,176,-1000,-594,-502,-774,522,662,176,-1000,733,-482,-238,527,373,-1000,335,-1000,668,-980,-94,284,-374,1000,-674,979,91,1000,592,169,-838,1000,635,-563,834,-1000,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,601,-773,1000,-862,-487,564,-848,-1000,1000,-898,-356,183,240,1000,406,63,-722,313,-89,588,-1000,950,217,-1000,-500,-1000,659,1000,982,316,-994,869,-597,-828,1000,-740,794,-1000,554,-50,1000,397,629,-1000,1000,1000,1000,-1000,555,1000,1000,241,461,-88,-538,1000,394,-98,-459,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{72,-1000,982,1000,98,1000,-176,-712,1000,-569,-281,518,-639,-842,618,402,-174,-69,-1000,574,-1000,1000,995,1000,-210,1000,1000,-19,1000,-699,-1000,-925,-216,361,244,-566,-844,1000,-1000,173,-279,-847,-422,-1000,-662,-62,-978,-390,-1000,-1000,-275,-1000,757,-1000,103,-314,596,-706,1000,1000,-130,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,768,-232,1000,494,-365,702,742,-803,1000,-929,-198,-568,916,680,1000,-443,-585,1000,732,580,-1000,785,50,1000,6,21,-91,342,1000,-134,95,-707,-418,-954,99,199,-100,748,365,1000,-1000,-1000,139,-782,-12,708,452,-913,1000,-865,-1000,994,47,1000,-689,552,-668,-1000,1000,1000,-389,517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{576,-1000,813,1000,170,1000,419,-855,1000,-774,-525,-340,-460,-1000,1000,-14,-1000,752,-1000,-723,-1000,1000,1000,1000,-975,1000,-10,26,1000,-917,-1000,-1000,-865,-325,509,-430,-1000,1000,-1000,-115,-793,-181,-831,-1000,-1000,896,-1000,-127,-1000,-1000,1000,-1000,1000,-1000,310,151,682,-979,1000,1000,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-801,10,1000,372,-961,799,309,1000,-1000,-1000,1000,-928,529,-224,-37,440,-1000,1000,1000,-1000,-95,993,959,-271,388,-548,-618,-891,96,970,70,1000,-370,-464,-563,104,1000,-1000,751,-1000,1000,1000,450,716,433,520,703,791,834,-757,389,1000,1000,-235,-1000,708,-1000,907,1000,-463,679,-51,1000,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{240,979,296,-522,639,-677,785,991,-90,-603,1000,-241,-436,-749,418,-25,1000,-776,-315,724,-958,-507,-468,-66,-331,649,-835,-527,296,-4,648,-90,-651,-212,-384,873,349,607,562,902,850,-432,1000,396,712,723,933,-67,767,-1000,-196,-490,114,-628,762,-229,-102,-512,232,-446,-688,529,750,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,134,-1000,177,44,-642,-731,-953,-382,665,-904,1000,489,-322,-56,55,-1000,1000,-993,-653,1000,873,1000,953,-555,1000,161,1000,-842,-1000,-74,-372,544,-56,142,-21,901,-1000,-268,431,703,194,-324,1000,-1000,-797,-1000,53,-619,-393,526,1000,-494,267,-1000,935,275,493,-774,912,717,-1000,-337,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{646,-813,946,-234,-150,723,-243,-1000,-1000,-652,-852,992,497,1000,760,300,827,662,-851,-133,730,302,-519,1000,1000,1000,274,-598,542,-657,-157,-135,718,245,1000,691,876,59,1000,1000,-780,214,824,-651,175,421,211,-277,907,-887,268,884,20,-660,-771,-81,-468,714,-80,1000,-294,-104,-415,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-400,-567,325,-206,-818,-420,918,1000,-97,1000,-1000,-656,-711,-311,-400,1000,-1000,448,400,101,400,-110,-277,1000,1000,285,127,1000,443,-365,81,-662,-513,1000,-453,-400,112,-1000,455,-317,-355,400,-358,-258,-400,838,32,1000,68,-20,276,756,-400,1000,145,-925,-196,-136,202,-456,-1000,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-198,971,1000,36,776,-935,399,-809,-1000,-731,-1000,-851,846,-339,665,401,569,142,296,-390,-672,612,-778,383,1000,1000,-738,170,237,484,-1000,1000,-429,525,476,-148,1000,364,859,1000,-504,-8,-258,-1000,-460,232,960,797,-371,-1000,-1000,1000,-957,-929,928,-946,-1000,1000,564,833,-470,-960,1000,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,254,1000,-204,933,423,837,1000,339,-367,1000,-1000,96,-1000,1000,260,1000,-1000,551,1000,-1000,-779,-1000,417,1000,99,35,-1000,-67,1000,-443,306,-507,-121,691,1000,377,1000,5,-1000,-391,-654,467,-1000,1000,1000,1000,805,1000,-1000,-1000,-978,518,-694,1000,-967,1000,492,805,74,-765,534,725,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{789,269,-852,581,307,-183,239,-218,258,95,606,-121,559,-576,15,441,-746,-144,624,219,772,-472,1000,326,627,148,-743,-454,409,21,-290,-1000,273,-240,-290,1000,-1000,-639,901,-166,352,468,28,741,1000,519,-846,1000,-429,-669,-352,406,164,1000,-965,47,1000,-20,843,-608,1000,-1000,63,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-637,216,-61,-167,659,-322,245,-913,-617,-649,-179,880,482,-1000,389,-273,-746,-797,1000,-42,772,685,157,115,1000,-52,-878,-668,-341,-288,-272,-1000,967,685,-290,841,-1000,-744,901,-386,971,-70,706,1000,-133,74,-1000,1000,-544,-803,-1000,1000,468,-551,-1000,854,1000,347,843,-470,660,-61,-527,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{497,431,-409,-261,118,229,244,-109,927,833,472,-1000,-1000,-329,-920,-537,-3,622,470,-383,69,1000,1000,514,1000,400,449,-1000,-506,-582,55,-480,-562,1000,-608,554,-344,1000,1000,770,42,171,-400,1000,-278,343,1000,847,1000,-761,-1000,1000,1000,-1000,-180,-395,-758,-4,-851,-885,-400,309,1000,-538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-262,232,-415,-224,44,136,1000,1000,-1000,-56,-364,-272,-1000,-1000,277,-1000,612,1000,-1000,1000,257,446,-413,405,438,-184,665,-1000,655,937,-1000,67,-976,119,62,1000,248,1000,1000,-634,855,-171,568,571,-486,550,1000,-95,-685,1000,-1000,206,1000,-400,-682,-1000,-17,-1000,597,-1000,-891,645,856,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{115,166,135,-278,44,716,-1000,278,578,-1000,384,-272,1000,224,-73,-501,-1000,-256,24,-485,-83,860,1000,140,768,695,-533,-20,-95,-1000,-639,-688,710,1000,404,1000,12,-323,227,79,855,-66,1000,665,1000,1000,-1000,61,-1000,1000,-698,1000,-700,732,-150,1000,-17,1000,-400,-482,-891,-1000,16,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{425,-112,-480,-536,1000,131,614,-57,-1000,434,-132,1000,-1000,-869,-435,-10,222,-15,-762,1000,-430,406,-1000,-121,784,-692,368,-1000,-88,1000,-217,531,-485,-706,835,-664,145,354,832,-433,1000,-539,-461,1000,-1000,1000,-1000,777,-369,-237,-1000,616,1000,-666,-1000,109,-393,-496,1000,-1000,128,1000,-410,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{870,-454,396,196,280,581,-281,1000,-338,174,203,-1000,-193,516,-884,-689,-1000,-26,1000,-194,-160,45,-885,-426,-1000,200,-766,-24,1000,-119,-444,252,852,1000,-307,-10,-847,631,-348,-267,1000,-129,430,-858,120,274,-568,-548,1000,-872,-339,969,1000,-974,91,-333,368,-1000,497,1000,247,-6,-395,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{784,333,254,-996,191,1000,138,-191,-511,1000,187,406,-1000,1000,526,-508,664,161,114,704,1000,746,-1000,166,-36,28,334,-203,818,1000,455,21,-67,-334,-323,-486,-960,327,733,233,901,-1000,879,-368,-374,-470,-573,-1000,26,118,1000,-17,-1000,336,-535,-1000,711,31,394,733,167,-510,-526,552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{539,-1000,674,-275,81,459,101,114,-1000,619,346,158,-216,274,-264,151,-250,407,894,-31,-495,162,-812,414,-676,-204,68,-158,338,141,110,960,-263,-216,-338,-892,-750,-255,463,121,976,-500,610,692,553,200,389,174,-173,-401,-155,182,797,-627,774,-202,1000,-623,268,216,218,-1,1000,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,1000,1000,-1000,-799,948,637,-1000,85,286,234,356,-503,-48,1000,-539,54,906,3,-1000,627,-1000,1000,-808,141,1000,-1000,-345,1000,1000,1000,1000,741,-968,585,-322,-560,1000,1000,-1000,-966,32,-288,1000,765,-932,778,-1000,-728,-1000,166,-1000,-1000,-516,-47,1000,-242,-589,-1000,-50,1000,1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{73,-266,-316,-55,-62,626,468,-911,229,554,944,885,264,-633,-434,915,-387,-296,34,135,-393,938,-902,-339,830,989,-226,455,-275,841,685,720,-141,-561,-522,618,-34,-61,550,336,-589,224,707,-580,402,-947,779,829,963,254,706,225,-551,955,165,-380,222,23,422,720,992,-417,-257,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{261,-940,385,1000,-568,-401,1000,-176,-1000,165,-193,296,-102,-39,281,502,-137,-498,-1000,187,-1000,1000,-1000,1000,-358,-264,911,-684,-355,679,901,1000,544,-981,-404,593,-1000,-909,645,1000,-787,-759,308,471,793,123,115,1000,-629,-113,-1000,-360,166,-610,1000,283,573,-433,-651,30,-349,825,783,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{945,533,400,597,-225,692,687,-460,-299,850,-31,-146,-593,-5,463,137,26,396,-400,193,-370,-413,93,-325,-124,92,1000,-400,-372,1000,552,59,400,-400,-439,-428,-789,-335,777,1000,41,-1000,476,-72,-117,346,-600,-1000,-400,-734,-400,-810,-1000,-478,-1000,-342,842,-300,356,939,-628,255,297,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{3,-1000,-1000,1000,-499,-278,59,19,518,-235,1000,1000,722,1000,-578,431,-405,-1000,-699,940,-665,1000,-261,-16,854,-994,-574,1000,791,852,85,-853,959,492,115,-167,894,-1000,-1000,331,435,453,-641,-1000,496,371,-468,1000,732,-879,321,20,621,293,143,-440,-582,559,503,967,-176,-1000,1000,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{465,-444,-61,597,-843,62,342,-103,-174,-699,138,780,221,-772,963,431,-273,-974,-628,11,-926,991,-867,-301,-35,-677,-102,800,680,829,-671,-434,844,-983,304,-472,989,-583,-347,144,264,88,14,-671,710,-789,-98,60,905,-570,800,644,-569,189,229,499,-356,676,106,282,-491,-799,444,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{-9,-739,-332,652,-928,944,-469,-217,793,400,-53,-522,187,-634,-506,-339,-189,-971,-153,-904,-976,1000,-810,-937,386,-65,-410,572,164,934,672,-538,-137,1000,1000,-1000,388,-1000,-708,1000,1000,600,-975,-344,536,-1000,474,-120,1000,-1000,355,551,25,-176,682,1000,-596,594,1000,1000,62,-1000,-103,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{-577,1000,997,424,-496,712,-901,469,1000,306,437,-792,1000,324,831,-1000,431,882,860,465,339,459,103,-659,-291,-17,1000,-645,-711,74,791,831,-1000,1000,516,573,143,-67,601,457,-189,-635,-515,470,145,-250,269,-1000,1000,-1000,250,1000,166,-335,1000,807,1000,-349,-76,-1000,222,-84,-318,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{370,-1000,-1000,1000,27,-273,723,-526,638,160,1000,1000,281,1000,407,1000,-128,-1000,-1000,1000,-1000,1000,-71,929,591,-1000,-1000,1000,1000,1000,750,-1000,1000,125,-338,-1000,1000,-1000,-1000,316,1000,356,-1000,-1000,135,850,-162,1000,950,-993,106,901,387,139,-94,-833,-819,1000,860,1000,-513,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{190,-1000,-722,1000,230,-1000,483,-761,57,773,1000,1000,61,964,202,198,-247,-1000,-316,1000,393,1000,-81,711,-209,-1000,68,-348,232,609,-885,-346,775,-317,-468,636,-17,-1000,-1000,-792,-598,-401,-272,-1000,-543,1000,-434,1000,-60,-291,-695,567,840,1000,487,-1000,-307,1000,7,-238,175,-773,-39,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{36,-1000,-1000,1000,-1000,-1000,833,-1000,254,-16,1000,866,-1000,944,343,952,-602,-1000,-1000,605,645,643,874,203,176,-424,990,-1000,607,737,357,-1000,922,10,-656,873,1000,-220,-1000,716,1000,743,-734,-783,1000,978,-499,1000,673,-31,1000,118,1000,368,-1000,-949,-1000,2,-418,-697,-849,-364,-619,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{-492,-1000,-336,941,-1000,237,-268,-61,-936,-1000,1000,117,1000,121,-995,-286,-1000,-923,-1000,139,-937,1000,-1000,-1000,1000,-360,-1000,1000,1000,183,1000,-1000,-402,1000,1000,-1000,1000,-1000,143,1000,1000,772,-900,157,377,-570,-78,644,1000,-1000,1000,370,-755,658,-1000,1000,-1000,-642,1000,1000,22,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-730,-1000,508,1000,-1000,-742,912,-178,470,-385,407,298,1000,-165,-237,-333,318,-88,761,-606,633,716,-1000,1000,-120,-746,-694,529,-1000,35,-980,-1000,449,-446,512,1000,1000,-1000,-293,-1000,1000,-385,-1000,-1000,123,-705,678,25,-323,568,-40,483,-773,-109,-1000,-396,-1000,276,650,1000,868,1000,-295,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{396,-771,-886,-306,-962,915,536,741,-24,-364,-851,-551,-406,527,-288,925,-161,888,-496,691,466,-324,-983,-604,172,-74,-30,-295,911,87,-76,-283,964,-168,795,785,-475,-18,-407,797,958,841,-179,597,243,468,-828,-451,573,193,-646,-5,-375,-990,-786,-215,552,-574,663,975,-563,786,346,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-915,-544,-495,-231,-1000,519,-50,147,31,130,-219,-111,645,207,147,-602,-431,740,-65,-500,-305,-1000,-1000,-330,-101,-700,93,-135,448,-322,-521,-228,518,374,-1000,540,-550,-747,-303,1000,1000,337,498,-30,1000,457,-78,294,-346,-458,-259,-642,285,-596,283,144,-79,-1000,250,-156,-735,-321,22,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-783,-544,-653,344,-1000,294,738,-356,-136,-549,-1000,-199,1000,-96,-188,-275,-453,838,-42,-482,-485,-1000,-1000,-330,330,-605,414,343,1000,-389,-1000,-792,1000,500,-1000,1000,-550,-1000,-521,78,877,72,-4,-30,1000,-39,-167,115,-914,-458,-507,-517,-471,252,51,-541,-79,-1000,900,505,39,-321,-93,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-47,524,-102,137,583,529,-1000,381,663,444,15,-436,-400,240,978,-463,164,552,-65,217,480,-145,400,-289,-168,-11,-93,-1000,400,171,386,-330,-27,721,-82,-400,-1000,400,-95,-200,-400,-265,400,-20,-33,600,-1000,-387,796,-98,-693,-809,1000,-587,130,952,-20,632,725,-618,-1000,218,138,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{781,1000,-1000,1000,-817,473,-776,-748,101,-524,-778,-949,972,557,1000,262,337,1000,-942,976,-992,-1000,-1000,-1000,414,1000,588,-560,1000,-447,-375,880,878,461,-1000,679,-1000,-180,-922,378,733,1000,190,993,1000,-50,-1000,-810,300,-455,-848,-1000,814,-11,998,-704,573,511,1000,-1000,-1000,-884,-515,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{1000,350,791,-1000,-1000,-791,1000,-781,122,-351,-513,-807,1000,-1000,560,-1000,729,-47,1000,-110,1000,746,1000,1000,-646,-1000,-1000,663,-1000,-86,-1000,-1000,738,-950,1000,1000,1000,-1000,825,-349,1000,152,-118,-1000,-564,318,1000,690,-1000,-350,975,1000,692,-392,-955,608,-512,302,866,1000,979,1000,323,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{710,-997,112,-1000,-1000,-196,1000,-806,-649,-367,-1000,-1000,1000,165,350,-728,-57,384,505,-287,-39,-175,1000,1000,-346,-1000,-82,956,400,-104,-1000,-854,748,-460,208,1000,-85,-1000,1000,405,914,-723,579,-321,-391,990,534,1000,-1000,-1000,987,868,-337,484,-219,914,132,-838,796,807,990,203,958,-928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{-66,263,-1000,244,326,1000,-521,-1000,-73,996,-61,674,119,503,-214,826,1000,-1000,1000,-1000,-199,-1000,-72,-1000,1000,1000,-431,-177,-101,-853,-1000,714,1000,1000,-550,287,-1000,-309,-1000,-340,1000,-1000,-469,92,1000,545,-1000,1000,-1000,-642,-1000,-572,1000,-1000,1000,-60,-1000,-1000,266,1000,108,-205,975,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{44,-462,-291,-547,415,774,-719,404,-764,1000,145,910,20,652,-1000,-889,-88,-1000,-239,-684,-281,-894,351,1000,-813,241,-1000,-731,-1000,-1000,414,-113,-534,183,803,-707,-1000,-1000,1000,-177,156,-1000,-184,-312,576,125,311,520,-634,-1000,-1000,-1000,-139,-115,-124,-927,740,-40,-1000,-200,-1000,-1000,1000,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{283,-349,474,272,119,9,-333,705,258,545,-856,417,-13,238,-428,-816,-366,-803,-6,-444,798,-788,-403,883,285,572,311,-561,-158,-862,-60,274,-549,194,1000,603,-1000,-999,312,703,-166,-638,-313,-360,510,-387,58,195,517,-169,-200,-447,-1000,-484,-348,-509,251,-747,-674,-194,-631,-315,540,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{896,-658,869,244,301,783,-26,-789,-73,256,848,674,-1000,1000,316,-455,871,-1000,774,-1000,-199,-984,614,-1000,34,-702,-431,1000,-303,-853,1000,1000,-870,-73,1000,-834,-1000,-671,294,-66,601,-155,-469,-1000,802,329,430,-494,402,-642,-309,489,-1000,-156,-1000,-1000,1000,-1000,-471,827,-963,-1000,986,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{-317,82,-926,-500,818,-628,-1000,660,38,230,1000,182,467,19,-848,-96,-816,-1000,-496,-456,407,-1000,1000,548,1000,1000,849,-968,-159,-952,-1000,-262,420,144,1000,1000,419,-539,484,602,-130,-481,179,-1,302,588,-962,1000,-268,-288,-1000,-1000,-160,-193,270,326,-586,-808,-99,-46,-363,274,515,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{569,263,1,303,172,-79,-503,82,-73,986,-61,469,-149,71,-306,282,1000,-1000,1000,-1000,281,-1000,-453,-188,261,1000,-431,-570,-203,-1000,-1000,625,522,191,1000,1000,204,-1000,-172,1000,88,-1000,-857,-319,846,-16,-1000,1000,-450,-114,-489,-818,375,-717,105,-555,251,-1000,628,-390,333,-62,924,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{-100,680,-544,-182,117,99,-612,-544,368,678,-1000,287,700,68,-624,-16,44,-716,474,-619,237,-965,-271,101,500,999,419,-1000,-912,-898,-529,177,918,436,75,813,-975,-308,-238,172,114,-895,-378,371,1000,224,-956,1000,-361,183,-864,-1000,-253,-694,452,-321,-462,-1000,494,185,-667,-1000,778,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTAw", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-837,-424,172,-820,-479,85,-76,759,1000,-238,1000,-915,-544,673,-105,922,-923,265,-678,472,1000,226,707,369,228,-127,318,-898,718,-529,-293,1000,-888,-1000,-506,630,-816,-210,-111,601,42,212,-179,727,-694,709,608,641,805,992,-628,491,35,2,746,1000,-333,151,618,178,699,-1000,-422,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-207,-501,246,1000,-1000,335,-928,1000,976,855,258,-1000,524,-188,-777,-874,1000,422,-96,-650,596,400,222,-730,-346,-1000,-148,168,1000,789,1000,-771,-1000,600,-459,-291,-598,-851,-1000,78,-118,-778,291,-470,-196,49,-877,143,-1000,1000,1000,418,-1000,-918,341,961,-845,1000,192,-1000,-585,120,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-543,-680,131,1000,234,-763,1000,-1000,151,-627,-740,160,192,-397,205,-168,1000,-671,-537,943,46,1000,-797,-345,-1000,549,-411,1000,-433,-611,533,372,-1000,474,891,-311,1000,-465,-1000,-448,1000,-563,-246,-494,564,-624,549,-1000,-634,-1000,896,-194,481,-1000,495,466,-232,278,-629,1000,-788,963,194,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-971,-424,639,-800,-1000,1000,-9,759,673,-1000,-1000,-746,-165,-399,763,-248,298,1000,-342,-169,951,426,-1000,-1000,123,-1000,-786,-867,560,229,-1000,-371,502,577,227,364,-1000,-948,1000,1000,1000,212,-179,-1000,394,709,870,404,201,-181,-1000,1000,-171,1000,1000,639,-1000,1000,284,178,1000,501,-327,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-991,152,210,486,315,117,432,904,-1000,501,-673,-395,-436,-193,-1000,909,460,883,997,-63,294,-400,18,11,192,-126,-497,457,657,-108,441,438,139,705,-344,-5,-772,1000,-760,-400,-529,-205,358,-529,524,840,1000,577,440,-101,-291,413,417,-1000,627,633,34,1000,433,25,235,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzU=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-642,-544,-13,618,383,-142,949,-608,752,-467,753,-263,-81,653,-495,249,925,681,-275,473,738,806,-679,-57,180,-458,486,149,463,-445,164,799,-440,-981,136,535,844,-359,-676,-421,754,-162,-521,-278,-414,-472,72,-469,-56,468,886,-332,869,-855,-381,835,215,-136,587,999,-560,-150,-443,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-1000,-240,428,-60,896,117,432,421,-1000,-1000,207,182,-1000,-230,581,1000,747,-80,478,1000,950,-1000,-497,1000,-953,936,-290,865,631,-1000,-1000,100,166,850,775,-1000,-734,512,1000,114,-760,-403,-1000,388,-1000,1000,-1000,-1000,-1000,-1000,1000,934,589,-253,1000,-1000,1000,-1000,1000,-315,1000,-1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{745,392,841,-984,150,854,-751,-117,-144,328,-363,362,549,623,-450,-883,711,782,-738,668,186,368,-309,-790,558,-584,139,653,-29,-680,-167,-119,42,-752,39,504,477,-514,-725,299,-875,535,-965,942,-690,760,-647,-351,528,-75,508,908,812,-983,-742,609,-549,465,-946,565,-897,-964,-104,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{949,525,469,-549,217,-231,234,-119,-1000,-131,-1000,-291,1000,484,-628,-462,-1000,466,-145,1000,-135,-502,-1000,-845,1000,378,-47,994,-519,-777,1000,-1000,713,-941,396,1000,740,-704,-445,1000,221,-243,1000,593,-727,944,-181,-1000,1000,528,-152,859,1000,-1000,-1000,-1000,267,-478,-497,342,21,-966,709,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{-1000,-100,-641,676,128,-145,701,1000,-1000,-916,1000,208,221,718,265,1000,-88,-853,1000,-351,-943,657,-1000,305,1000,-638,-419,354,129,-213,282,-469,39,-939,691,378,-1000,-904,-47,781,994,-111,-309,396,-977,495,-7,-1000,621,575,-512,-118,337,662,-431,-117,489,-1000,76,-1000,1000,-108,-651,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{462,772,-480,-74,-23,-476,832,-171,967,602,-283,42,903,507,-751,886,-601,-226,609,717,-983,-275,104,170,965,729,438,-47,-737,431,623,-196,-101,-341,697,-419,511,-406,188,304,-774,174,-862,-562,421,693,-570,662,357,-939,119,-16,959,328,-438,-844,-730,-308,-802,38,-435,453,-935,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{771,-1000,-130,-131,-73,1000,-981,-659,921,790,-9,254,1000,963,-748,509,-598,-257,9,-663,773,373,0,-1000,-1000,1000,-435,1000,588,518,-188,1000,-702,-339,-1000,468,563,1000,426,-482,-877,-1000,615,1000,-716,1000,-381,716,-255,292,1000,789,-260,1000,-138,-1000,-649,19,-394,834,-1000,-1000,786,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{-743,-1000,-987,1000,724,-115,714,-303,203,-269,178,-1000,-165,-876,-235,616,-545,-1000,467,-966,-604,987,187,477,-515,-431,871,-46,370,457,-149,493,-940,1000,-250,819,31,717,252,227,-654,-115,1000,-944,-1000,-536,1000,1000,-1000,674,-103,965,-768,-357,988,-1000,986,-159,761,-1000,309,-445,406,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.Character:LQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{1000,-649,-121,679,905,-533,667,-563,-621,-284,-1000,-933,794,-1000,-498,1000,-334,-715,1000,816,-648,375,-525,-591,-594,506,-754,470,104,-515,879,-343,107,568,-46,686,145,-259,-440,1000,1000,-1000,308,-555,-1000,263,665,-383,127,479,76,1000,491,650,-346,-1000,805,-770,139,-1000,85,-291,353,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{907,-1000,-432,993,359,88,-568,237,632,-116,-228,-502,526,121,-1000,-469,-930,-1000,820,85,10,292,28,599,-744,385,-181,741,332,-229,438,178,-521,108,-1000,1000,-434,418,688,298,646,-363,905,955,-841,789,553,-31,-557,737,482,385,-519,1000,-10,-1000,484,156,162,-708,-476,-70,94,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-109,-1000,-50,-625,584,-11,-79,-1000,-315,1000,737,449,-289,253,1000,1000,-321,1000,-1000,192,768,-190,1000,-126,-666,565,426,387,-1000,-1000,128,-322,-459,1000,175,-885,-184,-164,894,-36,156,349,130,526,206,-616,-34,1000,-1000,395,-1000,392,959,-978,1000,1000,-287,-250,1000,-363,553,476,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{643,-216,-181,-817,-881,199,-183,1000,-133,-929,454,249,358,-419,-504,15,-739,-499,181,919,194,-805,-273,-157,-74,56,333,355,1000,-842,583,-1000,-448,118,520,-1000,784,-106,-526,968,407,-586,-4,412,-1000,-1000,48,1000,651,-258,265,-837,73,542,-128,-1000,-706,-212,-1000,-769,-825,1000,767,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{438,575,840,-712,105,299,388,362,407,835,-525,287,41,162,661,-85,-391,29,-921,-467,23,-1000,-234,-216,317,-59,117,179,-1000,-306,-325,168,567,1000,-494,-421,193,252,-511,-752,-90,-127,166,504,-754,15,-20,85,390,272,-660,141,134,-1000,-147,-909,-43,729,364,-192,509,501,547,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{251,-1000,428,-489,-891,1000,459,54,381,-1000,193,-500,-1000,55,692,-696,-1000,336,26,-390,-920,-637,-143,-908,-310,460,1000,1000,-167,237,507,-362,700,116,4,-1000,1000,-120,442,1000,184,7,166,-62,-329,-116,665,357,1000,-404,-60,848,966,23,-1000,-1000,-1000,-324,-184,-1000,-116,705,1000,-482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-708,730,-485,-584,153,-799,1000,-162,1000,-796,-272,1000,687,-620,191,178,-8,-1000,-10,-1000,-223,182,411,-149,710,1000,-301,-1000,1000,-374,316,-81,-942,298,417,1000,-1000,759,1000,-1000,113,1000,1000,-850,874,-567,-800,385,-1000,742,-135,278,-1000,115,1000,-262,141,1000,-148,-695,-1000,55,-214,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-937,470,-1000,51,109,806,788,169,-1000,691,1000,-36,738,-325,506,1000,1000,-729,-635,-462,938,25,594,1000,-1000,-872,1000,978,277,-1000,-1000,-274,-1000,-1000,435,831,-1000,-404,-491,749,-947,606,1000,-850,526,767,-1000,205,980,-1000,897,-1000,659,1000,-1000,-854,1000,-1000,-1000,-508,-497,1000,-37,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,728,-864,-1000,-173,902,591,966,-1000,-1000,-811,1000,17,-1000,-773,1000,931,-841,1000,1000,843,1000,303,1000,-1000,-365,1000,1000,-472,-1000,-652,-1000,31,-1000,1000,365,383,-257,349,-476,-222,-145,512,1000,-541,-187,-391,1000,-795,-1000,589,-888,1000,738,-1000,-337,512,-903,-1000,-423,86,1000,714,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{1000,-640,197,1000,127,71,234,524,531,-730,-1000,1000,-1000,-778,780,347,111,-1000,432,127,-511,938,1000,-350,-389,1000,1000,107,1000,-145,789,-617,-157,987,-1000,-821,-1000,202,76,986,-621,937,787,72,-343,-126,1000,1000,576,-18,575,-251,-823,-333,171,-207,362,-1000,-430,-597,-1000,-518,327,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-364,-426,-456,-893,-90,175,-724,-1000,1000,-1000,-295,-7,-790,557,1000,350,-83,-1000,1000,-1000,-148,258,943,269,677,-59,1000,-1000,635,-124,709,-310,674,1000,-37,-1000,-692,338,-257,647,-134,-614,-717,580,1000,-280,1000,-698,762,476,-69,679,-1000,15,-574,785,1000,-969,692,-827,-180,629,1000,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-994,-1000,164,-127,-1000,98,-244,1000,800,-414,611,-1000,570,382,7,-1000,-291,-972,1000,379,351,-461,51,148,1000,572,-403,-977,119,-242,123,-1000,576,-579,1000,477,1000,208,236,400,-118,-666,-176,-417,1000,979,-600,-986,-264,-668,389,-638,-390,431,-775,422,-439,-41,1000,832,640,-212,1000,-636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-591,-86,-377,168,588,-476,-592,-513,-1000,-930,242,1000,-973,-656,-441,-807,-701,-554,482,394,-652,374,-292,608,-1000,419,454,860,-331,-686,808,-264,316,983,-724,-1000,1000,1000,-954,1000,14,-201,-600,1000,-21,593,810,-199,-544,337,-1000,1000,-201,-279,902,1000,736,126,-348,-495,1000,-400,694,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-479,143,933,-278,-583,-1000,439,849,847,-791,-205,211,-116,880,1000,-545,455,-411,523,-1000,-366,656,685,682,-96,474,164,-191,-580,-246,593,-728,356,1000,710,-454,740,1000,-597,1000,140,70,-250,-751,755,650,-37,221,499,1000,-200,342,-1000,-966,-792,983,306,149,335,343,1000,-116,621,-746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-155,-1000,30,345,770,1000,-337,-558,276,-625,-391,852,-1000,-397,1000,1000,181,-362,1000,1000,-632,-846,816,957,1000,-125,-225,172,1000,223,473,-1000,-1000,1000,-1000,-336,-1000,41,-1000,1000,-1000,296,378,704,-42,-1000,-20,-68,-1000,-665,20,-3,401,482,30,-809,1000,-1000,288,1000,-1000,-20,252,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-1000,218,506,-476,-304,-614,-607,-881,963,196,-902,-1000,1000,1000,-614,1000,-770,-407,-42,515,-867,-801,-802,347,458,-835,-1000,912,-364,488,110,-16,725,449,52,-216,778,754,174,973,-255,-1000,344,155,-920,-609,-1000,-663,-394,162,-368,870,1000,481,624,-512,-148,350,842,846,1000,903,144,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-591,-34,-736,-455,852,908,-261,-943,1000,-206,242,258,-1000,-1000,-323,-1000,-1000,-1000,530,912,-186,593,445,-933,4,166,1000,-488,1000,-800,1000,72,1000,741,-1000,-1000,408,510,-919,-200,-1000,-1000,-600,1000,453,807,1000,-775,1000,515,-1000,1000,-1000,126,1000,1000,930,-1000,-1000,-1000,-1000,1000,913,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-985,-535,-51,-578,-687,-598,-794,-303,394,-1000,336,-1000,882,-981,521,-353,788,33,-528,35,23,-233,-59,1000,-548,-832,-1000,642,-320,46,549,170,319,57,350,-335,867,-541,-243,-694,-23,-200,428,-264,-868,-615,72,-103,-581,159,-587,-575,450,870,-316,-167,98,-155,106,1000,392,-1000,309,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-1000,-591,-701,-628,-387,1000,318,-986,-583,1000,-1000,463,-1000,858,-437,934,292,-1000,162,1000,565,-1000,296,1000,-1000,1000,-441,893,192,780,-1000,-1000,1000,-1000,-1000,1000,-111,1000,-471,785,-997,-1000,-1000,1000,-204,1000,1000,-1000,-681,768,42,1000,1000,-1000,-900,272,1000,348,-939,-1000,-1000,-186,600,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-1000,-1000,264,-126,-1000,368,-838,-318,364,-1000,49,-1000,1000,-1000,334,-158,85,-248,-15,1000,-293,-611,280,1000,-1000,994,-1000,1000,863,-890,182,-504,141,25,-548,-99,-984,592,-1000,64,921,-759,511,651,-1000,-178,-949,-936,513,15,383,-618,-1000,145,615,-705,1000,337,-1000,-1000,1000,-355,93,-542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-1000,-1000,1000,-435,-40,1000,-286,-361,-606,-156,-557,-379,-281,-790,-1000,-461,653,198,1000,1000,699,-234,607,927,-1000,1000,476,1000,1000,1000,-805,-1000,-114,867,-1000,-66,-1000,-347,-1000,195,1000,-1000,-699,1000,-452,1000,242,-784,827,-1000,728,786,-683,-1000,1000,-419,798,273,-1000,-1000,1000,750,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-172,-1000,559,-1000,-610,113,178,370,-171,-337,1000,-177,816,-641,129,868,-1000,328,-1000,644,-499,-541,234,488,309,1000,-164,421,-255,-432,1000,932,-120,113,-218,-42,-275,1000,-521,479,-250,611,317,203,-629,851,-363,-1000,9,278,981,-102,-810,440,610,-1000,971,653,-392,-1000,157,873,-725,780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-202,-902,-400,-759,668,-221,496,-13,-284,475,84,1000,-300,1000,-74,656,731,-400,-1000,-1000,-99,-133,-21,550,-505,-727,-51,-719,-120,1000,-1000,35,629,-47,-100,-1000,-477,-382,62,-506,-449,779,-469,29,14,1000,1000,-759,-467,144,-860,690,944,-1000,-1000,-106,-240,-405,902,-344,-1000,-348,798,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{423,-451,1000,-435,-154,-1000,-12,-500,-426,-801,1000,401,237,636,-1000,-150,346,1000,-1000,-1000,-111,185,-93,-389,301,-823,570,-746,-948,1000,1000,1000,107,867,487,-819,843,-396,369,-691,90,450,-238,-629,81,32,721,194,-402,62,68,-710,1000,-495,-889,-96,61,-381,1000,1000,-909,155,-368,701}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-96,-607,641,49,198,-636,1000,596,367,-998,1000,-786,1000,-1000,358,910,-1000,874,-1000,867,-1000,280,-274,465,1000,544,804,-634,154,-746,1000,1000,-89,750,-823,-212,-681,717,-79,839,-1000,586,834,519,-844,1000,-168,-806,635,859,1000,-1000,-1000,358,1000,-1000,611,-110,-259,-1000,267,873,-541,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-597,-823,704,-377,-515,709,-299,-644,-154,1000,-520,-660,424,-1000,-746,-894,460,591,569,-945,-50,-186,412,-307,-1000,-712,70,121,1000,-97,-695,52,-1000,355,-304,1000,-792,617,415,1000,1000,151,7,-421,-968,-1000,-389,-46,639,-584,-543,575,-918,-205,400,518,-200,272,-1000,-1000,1000,789,-865,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-244,-679,-126,107,932,844,307,-1000,1000,752,744,743,-62,708,-300,451,371,607,-328,-471,-673,-611,1000,-262,-201,-699,219,-615,549,147,776,-810,194,-548,154,827,152,103,-626,-977,470,-291,886,-130,515,159,-163,-48,198,55,-1000,-210,-299,-726,-78,123,1000,-1000,985,-1000,271,204,-31,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{756,-246,94,-418,63,-270,154,-590,-1000,266,415,181,-383,706,288,-24,672,-203,651,856,-259,-749,317,-76,-56,-26,-742,505,482,-785,119,-148,194,-534,-353,351,-102,-513,-535,-1000,-78,-503,-194,-466,622,-978,-163,-232,-344,732,-1000,-210,470,-468,1000,610,1000,-175,-958,-526,-707,141,-385,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-556,-276,849,194,860,927,-437,-600,860,652,498,37,662,-878,570,865,-107,422,-516,-118,-682,-141,425,-672,319,-497,-537,-506,852,303,-831,-695,103,472,-430,360,-673,317,-975,19,495,-359,847,426,-740,953,594,-39,489,270,-183,-522,-840,-939,754,-75,763,39,967,467,-484,-92,-738,-944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-562,929,-1000,-247,-1000,-981,-76,992,871,-470,-296,104,-738,301,-359,681,-434,-701,-397,-1000,-187,352,376,-849,550,-863,806,355,67,1000,1000,-177,-1000,195,28,-952,-128,-123,675,517,807,-668,396,-910,110,-939,724,-927,-692,798,-825,-223,1000,-886,948,-11,460,-703,-1000,1000,-406,409,-1000,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-283,77,-388,-843,-128,1000,-782,227,1000,575,-65,425,-748,1000,-1000,536,453,350,-1000,-1000,-385,-415,1000,-922,-881,-391,594,41,-35,637,647,-1000,430,317,-1000,-495,-497,843,1000,285,-343,-1000,491,556,1000,587,1000,-87,579,-348,-416,-1000,68,-1000,534,-257,1000,-564,180,186,-245,928,361,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-1000,-1000,-549,55,1000,-732,-113,-46,304,762,839,199,276,-608,-95,403,623,567,312,-95,-425,-1000,-156,-378,-1000,-46,914,-437,534,764,285,-1000,-502,-758,1000,407,-508,-163,-1000,-1000,1000,37,1000,-60,397,-1000,420,-368,-1000,404,-1000,-492,-170,-96,492,-171,-136,-1000,871,-593,940,672,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-1000,629,-752,-1000,1000,-116,-278,-282,239,227,-128,-1000,784,-327,-818,160,-849,1000,315,-126,354,-1000,622,-1000,-1000,1000,-714,469,-905,765,-688,48,1000,-1000,1000,-1000,6,351,-593,-85,-1000,-1000,450,-433,1,224,1000,1000,23,-1000,-1000,422,129,-520,-391,325,1000,1000,1000,1000,-1000,963,-1000,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-8,1000,-1000,-634,-631,311,559,-1000,736,-749,-47,705,1000,1000,344,-216,-1000,-1000,945,-1000,816,226,1000,-1000,77,-963,-704,-563,1000,903,1000,-213,-68,-302,192,-319,1000,-705,-636,-1000,495,668,377,-1000,1000,-64,461,-294,206,1000,-1000,12,1000,-1000,-548,-542,1000,-1000,-762,365,597,-739,-529,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-334,-1000,-300,-218,550,-166,304,800,-991,-1000,170,-368,-960,-335,274,641,-119,-1000,709,493,1000,244,285,119,-221,-878,331,936,1000,-166,302,656,-1000,-1000,326,47,-211,199,-683,-390,135,-72,278,-1000,1000,-989,225,-966,-387,-564,-867,-24,538,367,-562,-593,141,494,606,190,-1000,554,-939,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-106,1000,1000,-1000,-647,-202,-18,944,964,526,-1000,98,-351,437,-382,-1000,-79,217,-1000,-1000,547,1000,-594,666,-533,691,-560,-46,-870,498,437,871,958,1000,-1000,819,-853,-1000,493,-706,-489,-1000,1000,1000,-1000,-435,-14,166,505,-152,-7,704,1000,1000,502,-332,289,-1000,-530,1000,-227,-781,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{404,-822,33,-196,942,1000,350,671,398,-1000,-1000,1000,145,-1000,-146,-315,-190,-177,-990,1000,-349,44,-213,61,-788,-282,16,-845,-617,-142,-487,1000,837,-245,-98,-177,-629,-27,406,83,1000,1000,45,-459,-92,1000,778,-878,87,364,-976,-1000,-639,-322,36,-1000,-105,284,59,-722,774,-219,267,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-377,517,49,177,819,827,612,359,-671,310,-313,476,-619,-136,-365,890,-819,-1000,438,288,455,106,90,-1000,-455,-575,-435,778,-157,-18,-1000,454,-508,-708,-81,567,151,-147,-85,-102,1000,-150,383,-1000,-310,247,160,-135,-69,27,-2,-187,1000,-97,-199,-709,-589,601,136,338,-1000,-52,-1000,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-475,65,-399,454,790,594,612,381,675,-521,-343,599,-805,-945,-365,721,119,-1000,746,30,431,-946,-174,-1000,-37,-486,-927,59,-92,-386,-1000,215,-86,-311,-867,532,-1000,-148,-719,724,281,829,-498,-552,-1000,-1000,888,-87,-418,504,-591,-36,1000,699,-928,-714,763,-653,-198,338,-1000,156,-1000,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{242,440,-290,223,555,-576,-149,843,176,-262,57,-186,594,-1000,239,320,544,-203,-535,-32,42,365,405,-555,967,67,582,711,-1000,-1000,963,289,762,-302,145,-497,204,1000,-238,-723,-523,536,-372,-85,-633,-1000,687,433,-351,591,98,-23,1000,101,-967,-400,703,-1000,411,-413,-823,-560,-1000,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,771,106,626,613,-461,-216,-277,-315,412,-729,786,-564,477,-280,344,-1000,-328,-373,89,-45,1000,251,-518,-117,841,1000,-926,-309,1000,437,-621,-24,-388,-99,468,9,-507,544,325,-197,-289,-672,19,798,-482,690,520,-419,-128,466,491,250,-336,-317,-278,-242,-1000,327,-496,-185,129,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,806,304,456,188,177,446,777,749,97,-347,170,319,-711,328,-588,-82,898,-689,307,200,651,584,1000,-778,483,544,161,-722,-464,370,629,1000,899,671,-1000,318,400,495,-737,-985,764,39,801,165,-78,1000,3,203,1000,488,-843,-537,557,332,-291,-216,-574,-525,-654,-276,400,-813,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{472,-562,-187,-152,782,635,-148,304,254,1000,16,-172,93,-13,1000,-919,-601,316,-1000,-845,-36,142,-367,-114,284,216,-83,-501,1000,-622,128,541,398,-87,766,1000,-335,749,418,-830,100,951,728,544,-301,44,726,-383,-350,245,1000,-74,727,-21,345,-257,-105,642,-229,765,414,-1000,78,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-255,-187,-58,497,358,-128,655,542,1000,258,554,-679,-353,-1000,-58,-797,506,1000,584,151,615,152,1000,673,-264,880,-1000,-976,-1000,-990,-435,-177,163,1000,1000,-407,-299,448,-166,-178,-250,847,750,-1000,285,852,-1000,1000,743,953,392,862,-1000,-1000,1000,46,888,132,34,-96,-15,563,581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{438,-562,-398,743,647,229,-403,-272,925,986,16,-1000,-695,184,177,-1000,249,462,-1000,-470,-36,458,-84,-113,583,671,-92,-259,1000,-688,84,-105,-618,-621,753,615,-499,749,519,-305,285,951,-174,609,354,-350,830,-107,-1000,137,1000,-620,968,-1000,345,-338,-794,180,-289,631,198,-1000,139,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-19,222,29,-723,666,349,-594,-354,810,870,-563,287,156,-872,27,-1000,-671,-52,192,559,-775,-503,361,429,-499,-541,96,-663,1000,-379,427,949,-1000,437,805,1000,-1000,-723,1000,-1000,772,-840,903,866,121,-323,245,-561,889,835,55,-213,1000,-548,580,-508,-108,838,-582,362,937,-1000,495,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{541,-473,270,357,113,-349,-422,408,228,584,208,-1000,-984,-374,926,-857,-1000,1000,693,141,896,289,1000,-209,-191,1000,60,-284,-155,-1000,633,-216,-945,-1000,594,564,486,766,158,-967,-87,208,-370,567,-599,-869,824,818,-462,-217,-32,-907,-133,1000,1000,-816,-909,107,349,621,262,490,-428,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{225,-1000,766,130,678,1000,104,1000,241,998,1000,398,-306,-566,-1000,1000,-29,108,288,-1000,808,1000,668,379,-65,-870,-556,-1000,-328,-469,-775,-80,-130,32,601,886,-154,1000,680,-887,-685,1000,1000,-208,-1000,-142,-1000,-87,703,-230,815,421,-194,-316,-1000,1000,137,-157,-74,349,222,1000,-331,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-516,101,160,-133,479,789,259,-1000,911,782,-560,-312,279,-1000,167,-728,-51,411,571,585,-560,-244,1000,599,-750,-657,-411,-469,-571,-201,573,-553,-1000,-197,822,814,-954,-569,995,-743,1000,-589,98,202,825,-1000,-167,-565,147,513,-826,-670,877,-1000,1000,-992,-948,433,-927,391,254,-162,247,294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{825,-892,-1000,-959,567,-1000,-229,-323,986,-338,1000,1000,632,1000,1000,-66,584,1000,-331,481,373,1000,-1000,1000,-25,955,1000,119,628,-1000,1000,33,111,253,174,-1000,887,261,-185,133,-244,234,1000,78,-750,-268,513,-1000,-974,1000,-102,-1000,541,-1000,-736,852,-290,1000,-432,-1000,-1000,-1000,178,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-170,1000,-909,29,-76,-658,-77,148,-979,-73,1000,361,205,389,1000,-289,540,408,691,-145,-147,-686,-226,1000,-379,-1000,-596,574,304,65,89,-52,63,-461,194,-927,-1,643,-553,-217,-809,776,527,-361,-189,417,660,414,140,1000,1000,-405,-154,-38,-5,129,1000,896,-418,380,-1000,-48,1000,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{266,-1000,-331,474,-1000,-671,263,208,-1000,-900,175,964,-388,1000,-1000,783,1000,994,-770,-228,114,667,-1000,-550,-281,-1000,1000,788,1000,466,1000,811,771,-544,-572,-1000,1000,-570,-601,-271,-515,805,1000,-351,413,-586,640,-516,689,1000,-1000,-953,28,1000,66,664,-1000,647,274,1000,-478,109,-12,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{316,-792,-656,791,-288,-1000,-1000,384,191,-339,365,660,34,1000,-1000,941,1000,903,116,-183,620,-509,-1000,926,-575,-507,459,272,-564,-558,1000,29,294,-697,451,-1000,1000,-1000,-951,805,-1000,362,1000,-715,321,16,229,-646,481,686,1000,-1000,242,-5,41,1000,102,1000,243,-325,-672,-824,1000,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{290,749,147,-94,-64,449,324,323,-97,28,268,138,-129,81,61,-953,150,-117,717,-402,136,-473,446,829,59,-814,-53,485,486,-14,235,-300,-848,-265,517,-451,928,731,239,-989,-224,799,-33,-394,-678,-646,517,487,15,704,-438,-467,751,205,1000,963,573,793,-798,126,329,678,755,-670}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{921,-1000,-863,1000,1000,-676,774,-802,1000,-114,1000,87,485,-111,-1000,-270,710,566,477,449,778,-256,-392,-71,-869,1000,838,536,-156,-705,-203,409,-1000,-1000,-1000,-509,-359,1000,-1000,829,-262,129,744,738,851,-197,643,-1000,1000,1000,976,148,1000,-244,-973,-43,746,626,-432,-394,-1000,-16,770,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{947,401,-46,-328,-395,844,34,372,291,-548,197,118,-637,100,-888,-901,247,-147,-347,-582,571,-370,118,-53,-59,-735,-94,294,694,-449,186,-359,605,-480,435,-235,972,-832,770,415,-258,89,-53,-582,-540,-887,-375,-643,-356,893,-983,-509,957,646,437,-303,-15,-263,-630,652,818,668,723,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,266,-1000,-1000,1000,-414,1000,291,-614,-678,444,-1000,1000,-453,-901,22,625,358,-833,138,833,-538,1000,-681,-459,837,731,1000,-1000,186,-996,757,-1000,435,-235,925,-1000,655,415,626,956,-53,-582,826,-1000,-606,-832,1000,893,-1000,-1000,401,1000,990,1000,-1000,253,-1000,652,818,634,-24,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-402,89,-374,-938,141,462,123,224,1000,358,597,399,-11,268,-880,-372,-180,424,-289,-525,310,936,-91,1000,274,645,307,-178,336,-751,439,-579,275,119,436,-359,867,26,1000,-95,-1000,161,25,-32,-1000,-1000,-429,-762,-1000,563,-393,-605,984,-1000,37,-733,253,566,-635,48,539,90,-120,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-142,1000,-249,-675,444,-288,959,473,-721,192,-275,769,-689,-256,924,499,567,411,1000,-979,920,-71,240,-136,-986,-610,-1000,195,243,-1000,-686,-804,3,-776,-725,617,-653,-622,865,-19,-1000,-75,-648,1000,349,375,-289,-932,195,-369,974,162,354,122,-182,1000,582,793,596,304,-1000,-308,472,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-454,-304,-632,929,-347,-175,14,155,-445,1000,25,868,645,-987,382,-109,162,-551,-536,-982,140,-185,356,-1000,105,675,86,140,-711,-25,-172,-755,-976,-54,-429,1000,783,1000,-440,-299,1000,-216,1000,-446,258,-715,116,-609,492,1000,-986,1000,-1000,-1000,685,-414,577,-641,491,-1000,-276,28,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,902,-665,-1000,225,153,-388,715,-398,-371,34,-158,-399,-390,491,183,-153,366,-668,407,840,333,236,-720,-396,858,-352,-351,530,812,-1000,391,-119,-720,-14,-238,1000,293,316,728,-9,-478,-21,628,-763,529,83,366,325,-157,-504,257,-663,982,956,-96,-71,526,-443,930,-282,-769,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-400,-236,42,-665,-286,223,1000,125,-11,669,-129,829,725,315,897,-73,239,562,-20,558,-232,566,10,1000,-194,-614,1000,709,495,260,78,-568,235,-510,-179,-358,-1000,-334,1000,956,1000,168,722,-156,-653,-1000,1000,812,-454,-1000,336,-1000,266,-702,822,151,-439,-928,333,-446,-470,165,-1000,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{25,-872,-633,193,344,17,440,-913,231,-324,-376,400,-36,-4,-516,491,1000,-1000,-147,414,1000,840,-345,26,1000,-709,858,1000,-750,-499,773,54,-61,173,672,991,638,401,-81,-580,-346,-1000,1000,-503,1000,-843,41,1000,1000,520,-1000,644,889,-1000,982,-1000,-1000,262,879,1000,-522,993,-769,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,860,-522,-1000,319,-152,216,386,-140,-2,447,203,553,211,1000,-222,470,234,-526,-556,-556,10,764,-599,-539,695,-600,-93,1000,868,-1000,130,-68,-600,-449,-567,1000,-9,748,215,416,-359,311,-249,-850,301,548,-193,-564,586,-834,-356,388,-157,938,564,-529,-112,-564,900,-778,-283,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{802,-1000,-143,-695,-59,-999,449,-352,786,-1000,-870,930,-1000,47,-661,644,488,-1000,360,-72,645,565,857,241,-572,-1000,765,513,-596,-329,212,-717,689,26,53,-14,226,1000,293,392,473,-1000,-411,-224,1000,-763,393,-983,571,1000,-1000,129,1000,-769,452,226,-959,1000,605,471,371,569,-769,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-340,-1000,800,-1000,-59,118,422,-561,-659,-1000,-755,1000,-36,225,-278,1000,573,-609,-37,27,46,310,448,465,504,-1000,1000,-13,33,1000,381,-720,183,395,104,996,-21,1000,95,623,74,-1000,483,-748,394,-1000,780,543,335,-284,-802,-559,347,544,160,200,-525,476,-877,-779,-208,-1000,9,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-63,-762,-161,-511,20,-1000,304,-758,1000,-1000,-1000,267,-653,-1000,-893,194,656,-1000,917,732,1000,416,1000,-399,-1000,-450,1000,-515,-868,-553,-190,-1000,1000,-613,-400,612,740,1000,1000,45,1000,-1000,-926,-611,1000,-359,732,-1000,954,1000,-1000,-75,1000,-1000,160,1000,-1000,1000,694,548,1000,-642,-312,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{886,560,246,-665,1000,400,-761,405,612,-1000,205,-542,393,313,-577,259,629,-918,-1000,-830,304,1000,1000,273,727,1000,-1000,857,254,-403,-425,1000,-313,-1000,-489,544,-45,-947,1000,1000,-1000,514,1000,-878,-231,-448,78,-1000,513,-508,262,622,-475,-1000,-1000,547,318,1000,193,877,673,-429,-952,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-495,-168,-547,-248,53,-526,908,120,951,-781,-544,-19,-86,-928,249,810,510,137,-1000,-986,-481,799,1000,930,599,-146,-665,352,-157,-8,25,-1000,-59,23,315,-880,-710,1000,-664,1000,363,-265,579,-1000,249,-520,-1000,67,269,-1000,267,-569,99,-79,-459,-1000,1000,-373,169,840,-371,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-730,-1000,673,133,-385,-124,437,862,-122,436,-1000,-527,-1000,506,203,153,995,-526,16,-1000,658,1000,1000,193,-574,-994,-665,300,301,-27,748,-963,-72,-62,598,150,-727,427,527,1000,456,-756,794,-1000,428,-867,-415,101,-114,-427,1000,-833,-1000,-1000,-83,995,500,-405,1000,63,-1000,442,-246,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{207,-859,-585,1000,-1000,282,827,-686,-728,909,-127,1000,1000,251,366,87,828,677,-68,-1000,-160,144,-135,-432,-546,-429,660,-418,906,-627,906,758,200,-464,-672,724,1000,-443,63,-516,-488,-1000,548,452,-392,-1000,-200,849,854,-226,567,1000,784,1000,-662,-83,-668,300,169,922,-792,-93,-193,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-495,-1000,30,162,-420,723,861,120,1000,-116,-799,-894,-672,-120,961,-81,855,-445,445,392,-481,1000,188,317,-522,460,-261,352,-49,184,-349,-375,197,-338,787,1000,-228,-735,913,215,-88,-265,1000,-674,249,-931,-191,1000,848,-552,414,1000,-426,-83,-338,-440,479,-206,169,-528,-725,635,421,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-321,-336,34,-493,-237,104,-810,922,181,-521,-291,-144,-583,565,-909,-561,312,-139,515,649,379,-678,-116,655,692,-814,-151,-485,-681,-828,812,37,-114,538,845,-654,-473,228,378,980,-782,666,-455,-831,-717,598,154,386,-264,-125,396,-801,-981,23,596,15,-277,-970,592,-619,-112,927,-346,-821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-25,-1000,637,-1000,-253,-637,-345,808,-944,-265,-877,-650,-865,1000,456,833,897,1000,377,-221,241,1000,655,910,-702,1000,530,-45,-415,-851,1000,346,-225,273,440,98,-364,-251,-50,-146,-869,-756,37,-95,-625,740,984,559,166,-1000,620,44,-2,-173,-1000,1000,-292,-400,-283,-324,-978,534,-391,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{800,-314,-204,1000,-612,-379,1000,203,253,-170,52,1000,179,-928,170,651,294,568,-1000,-986,765,327,1000,619,895,-777,-72,762,154,-441,-640,-981,-313,23,276,-1000,18,1000,-1000,359,1000,-1000,345,-471,151,-520,-942,461,836,-968,424,-77,1000,693,-659,-695,128,-373,943,840,3,592,-821,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{385,995,704,303,270,743,583,312,243,34,695,-441,-102,450,-732,-1000,871,507,1000,-19,25,-181,369,288,-809,-193,457,854,-1000,-380,1000,758,581,-197,321,972,-64,-427,356,-41,-488,-907,1000,82,-272,1000,1000,849,178,670,-8,558,-548,282,422,216,-190,1000,-1000,587,-322,456,-227,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,575,-790,1000,-250,-611,-1000,1000,-578,804,-937,-893,1000,674,374,-660,-535,-427,697,154,-257,-261,-284,-925,-1000,-217,1000,268,156,-68,514,-859,-459,245,-104,1000,430,994,193,-697,84,354,944,1000,415,-930,914,1000,-1000,-661,1000,1000,1000,-645,-382,-246,1000,-369,767,1000,257,846,-1000,-791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{130,-510,603,-1000,186,-280,-352,184,-831,1000,-207,509,25,1000,764,131,-630,813,-82,1000,1000,-685,-80,76,-1000,-428,265,-48,-261,1000,843,-358,-1000,1000,-136,1000,-935,-185,1000,-619,686,371,646,1000,-1000,-689,-858,1000,356,-938,938,-476,982,192,1000,1000,1000,1000,1000,1000,-1000,986,127,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-293,290,427,102,-41,-394,-938,-1,-598,637,-548,13,291,1000,241,-660,-331,385,-336,830,609,-828,-328,-925,-1000,-378,958,291,148,767,242,-639,-927,726,-88,774,-974,68,417,-615,-314,138,613,755,212,-912,-251,739,-910,-522,763,56,1000,-359,131,180,667,358,488,969,-532,336,-463,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-197,937,96,198,43,415,71,304,1000,216,94,-447,-401,648,401,-194,-1000,891,-552,1000,415,-324,-562,-824,-1000,-524,310,-16,-312,1000,196,66,-927,-71,-566,429,-162,-1000,-48,-175,106,-691,-79,487,59,-476,-985,518,-193,-713,-107,-79,847,-947,356,-122,-194,896,-247,810,-759,241,713,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-531,-1000,1000,1000,-282,-628,1000,403,-103,1000,-25,498,-1000,-1000,-1000,-935,-56,-248,-449,-859,1000,-855,-17,-1000,1000,-982,-265,195,1000,448,-128,-892,853,-152,1000,-935,-1000,1000,161,-854,-400,365,-83,-391,850,494,-1000,-1000,-265,-388,725,276,102,157,237,-341,353,838,492,970,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-399,290,843,937,93,-857,288,153,-840,374,-228,-346,-629,550,0,-1000,32,827,-825,526,518,-795,265,-1000,-1000,-291,1000,392,-62,249,-218,-1000,-992,993,-394,834,-1000,-873,940,-334,-1000,-391,1000,988,-185,-1000,-402,816,490,-848,520,187,295,239,794,-475,211,688,1000,1000,-871,-103,-482,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{863,-1000,501,-416,880,-294,-441,-738,251,403,-103,918,-12,1000,-561,-1000,-775,-237,-514,168,137,803,1000,-1000,-545,-791,877,106,-265,249,1000,-143,-1000,508,1000,428,-160,1000,212,372,-1000,184,1000,1000,-511,-754,608,494,-892,-728,642,131,826,276,514,588,456,232,863,994,36,882,-681,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-859,914,-1000,-730,-121,301,-487,819,992,859,-375,357,920,-1000,-1000,1000,-801,-1000,442,-1000,386,-608,156,1000,77,734,1000,-600,1000,-933,-394,-575,225,-1000,-615,130,-532,298,-485,148,1000,-33,-620,-307,950,-260,144,1000,-242,653,-202,477,-525,379,-736,663,714,-980,125,682,-365,-141,-919,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{321,-1000,-608,-50,724,986,-408,-141,451,1000,-691,1000,226,-171,-716,-1000,-775,-73,-519,129,-1000,1000,1000,-166,-545,-1000,811,-336,-175,566,1000,-213,-620,-72,415,1000,1000,1000,-1000,1000,-216,-1000,397,416,-475,-724,608,1000,-1000,-1000,642,-121,1000,1000,196,-106,488,183,489,844,-279,882,102,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{329,686,-867,-40,321,929,841,691,1000,-714,-101,-37,-1000,648,-1000,-835,-13,858,-1,484,-1000,738,1000,-1000,161,-65,-1000,-1000,-131,242,-633,-620,-1000,-250,-1000,1000,603,-82,481,-299,963,661,-1000,-181,-521,-389,875,-692,86,1000,-1000,-228,-180,549,490,1000,1000,39,-29,-185,-3,639,-350,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{552,516,-64,722,967,382,82,-367,152,-41,761,496,60,-662,-1000,-21,-41,71,183,525,-1000,546,168,385,68,-8,-795,-226,26,143,-1000,-458,-1000,551,181,-575,-147,-420,994,-14,-135,-660,1000,-595,-517,-817,-103,113,-294,339,125,667,-1000,561,1000,706,392,-1000,-253,150,492,-472,-236,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{108,-1000,-166,664,-356,619,77,541,280,-400,97,-93,-744,186,-1000,-349,-37,923,-57,147,-1000,414,432,-269,201,-154,-1000,-595,291,-366,-90,-595,-875,128,-571,360,325,-701,-594,283,57,89,489,39,-249,102,601,-714,-482,735,-308,31,0,1000,430,456,919,207,-195,1000,-176,-207,-59,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,1000,177,-1000,-1000,-1000,-1000,1000,1000,-1000,1000,-1000,1000,1000,1000,-1000,-1000,176,472,-55,-1000,1000,2,-1000,907,1000,-1000,11,1000,1000,1000,191,1000,-1000,844,904,-409,-145,-745,-1000,1000,577,1000,797,-1000,1000,1000,-1000,18,1000,355,-1000,-1000,-1000,-1000,489,23,1000,1000,603,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{244,822,472,-398,495,851,232,896,714,-640,-502,-256,-663,255,-493,-330,-847,-15,-266,539,-306,596,197,100,316,-851,-763,-387,-262,-911,-354,-973,-378,-789,-612,56,-738,22,-851,-577,-570,-501,-519,-487,-1,513,-590,-256,85,520,10,-931,-858,498,695,189,699,593,757,-695,-763,432,-7,-474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-299,620,-1000,551,-368,960,974,117,1000,-632,244,-759,-1000,-579,-1000,-484,600,-783,456,-234,-901,390,-247,-587,-394,728,-105,-607,-339,640,120,9,-1000,904,-426,599,244,-524,542,526,-599,741,-250,-771,-490,-884,700,-627,429,475,-811,822,371,506,378,1000,152,-338,-239,-101,-154,266,-150,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-792,34,-721,672,-852,-377,-126,-961,252,1000,1000,186,-295,1000,-622,-751,888,727,480,-533,539,-335,797,664,-785,-23,687,-221,-166,-61,-843,-237,-905,1000,262,408,-423,188,922,-1000,-606,-185,253,-334,34,-598,-283,-53,-1000,-1000,-767,304,788,-93,-142,128,55,520,1000,671,178,943,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-560,29,1000,672,-884,-915,-1000,-460,137,399,-947,805,-1000,-127,359,1000,-1000,-610,-765,207,-230,-49,-537,-224,-564,808,793,-631,526,366,1000,775,753,-374,97,704,1000,394,-16,377,468,1000,408,161,-405,-324,695,960,-562,-939,1000,321,-1000,-334,-580,-680,-509,-1000,86,318,990,-48,787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{459,749,-179,-176,-255,1000,997,1000,877,-1000,-101,1000,-1000,1000,-1000,-735,-523,1000,3,795,991,-140,121,400,46,208,-1000,-821,-1,136,-1000,-902,-982,47,-621,471,123,-181,-43,-325,-25,-739,-1000,-1000,-605,139,379,-912,-112,707,579,-391,-293,1000,798,741,1000,-278,907,-185,-858,377,203,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-678,-1000,-64,293,967,-852,-898,-1000,-1000,901,1000,401,1000,-1000,-1000,919,951,71,-808,-164,-1000,-756,63,290,751,-751,311,1000,-1000,-10,-282,1000,1000,-854,-210,282,742,603,269,-479,149,608,814,-817,1000,34,-588,1000,599,339,-423,667,421,561,-1000,-142,-1000,-31,-518,177,120,797,746,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-1000,807,-269,108,983,-564,-250,-321,70,702,439,455,-798,-1000,-198,753,-143,-1000,-740,-1000,778,435,256,-205,366,-438,-310,269,-193,93,-75,149,-589,-735,-458,204,-1000,894,-272,-221,533,318,-322,-24,672,1000,90,-633,-785,107,632,-872,1000,700,-676,115,-1000,19,560,-232,285,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{134,-801,604,-460,162,-210,-139,-851,-385,-1000,493,1000,174,-150,-1000,-897,215,1000,-1000,370,-763,-447,-918,1000,-409,-1000,378,996,450,220,-699,1000,-381,-393,749,130,189,417,-389,675,964,-246,-528,-1000,-1000,-754,-388,-967,-512,663,92,301,207,762,1000,1000,-904,71,208,844,-20,-784,-339,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{519,735,964,-111,1000,475,-875,325,-719,478,4,553,397,280,288,618,-276,622,-309,181,-321,372,945,113,-487,60,-170,-246,724,-813,-944,966,271,-187,-335,-327,591,43,-356,-324,1000,-196,-768,-1000,508,688,33,941,549,639,72,-362,-402,12,60,-684,219,88,-180,-375,555,-920,1000,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{581,28,545,-533,-846,-993,1000,454,19,-701,898,618,-670,113,-1000,-1000,147,1000,-1000,-697,-533,-767,-1000,1000,1000,-1000,593,-33,-1000,538,471,1000,475,-80,340,74,578,-905,228,1000,-1000,877,671,-27,-1000,1000,-339,-1000,-171,1000,-1000,-72,-225,-858,705,-743,-1000,-1000,36,776,-1000,-659,-771,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{564,-1000,-1000,363,-1000,-243,504,-564,972,157,70,435,439,861,-55,-64,-400,924,-1000,-591,99,-1000,-138,-362,599,254,-772,-276,439,1000,1000,-627,-129,211,-512,791,-759,-550,-197,68,375,-1000,1000,1000,317,-800,1000,-1000,-1000,565,-590,454,-120,-412,73,-1000,-666,-601,379,923,1000,937,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-28,-40,719,-494,-730,-283,-596,-942,-44,-973,668,900,-171,520,-798,-227,431,345,-727,890,-220,-858,-415,964,-559,-844,-691,230,897,-131,-670,441,-529,-526,582,594,960,640,-220,4,543,142,-205,-724,-427,-864,-515,-291,-629,140,-143,720,328,79,126,967,-738,191,434,53,250,-993,-217,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-265,785,249,289,329,-594,498,440,-553,-424,-445,630,237,170,144,-51,816,754,-1000,-417,-1000,618,287,869,-586,-417,-188,-898,119,-1000,473,411,-321,540,-542,-5,49,-628,1000,-1000,982,-61,-464,-978,433,-239,1000,541,690,-818,-1000,-655,-715,341,-44,-1000,-417,-1000,-869,-911,-516,102,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{697,-1000,0,1000,675,-6,758,840,57,134,-170,606,700,807,364,1000,-246,986,-290,216,-717,-692,-412,265,142,714,-788,-326,-598,-369,45,-717,1000,-385,1000,57,-36,636,29,33,-295,-563,928,-865,-629,-127,593,870,-137,1000,-1000,-145,-927,-636,434,-855,-316,-927,400,-754,-374,983,-406,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-709,-1000,-1000,-1000,738,403,604,-723,1000,-913,-584,-217,-1000,-522,749,134,-327,-1000,-1000,-319,-1000,-1000,-1000,1000,-409,293,1000,532,871,-813,-791,-192,-1000,-229,-435,-607,272,1000,427,-586,-631,1000,598,401,1000,209,-1000,-1000,1000,567,1000,1000,-1000,-553,-6,1000,15,-1000,-1000,-1000,1000,89,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-1000,396,-92,-37,864,82,-816,257,373,-658,-115,-1000,399,-437,-352,-267,-231,667,151,-300,124,129,136,-577,-413,-642,123,-964,217,-8,-736,789,-456,61,839,86,222,-1000,104,176,-1000,1000,-299,315,165,594,-732,361,-220,112,-47,228,374,357,-911,-19,831,-350,-249,-428,28,-134,-228,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-740,-136,-541,-1000,1000,77,-298,-1000,1000,-539,885,554,-1000,-589,1000,1000,-569,-1000,-67,-353,-999,177,132,362,-495,304,1000,371,1000,994,712,1000,-248,-618,-376,-604,988,639,723,-429,308,268,-152,-502,1000,376,-569,-885,1000,-412,-40,1000,135,-354,511,916,-138,-1000,-1000,-1000,1000,-629,-1000,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-44,-1000,-1000,-780,1000,-498,-456,-1000,30,-913,-404,678,36,690,1000,329,-499,-179,-1000,1000,-640,-323,-600,1000,-825,1000,1000,596,44,-813,258,-557,-1000,-519,970,891,482,1000,1000,-586,-840,629,740,11,226,740,-937,-270,1000,-1000,1000,-472,-1000,-217,1000,1000,612,-319,502,-240,1000,-324,-856,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{610,-1000,-1000,198,-887,617,-732,992,921,342,-1000,739,-141,109,-385,64,-47,4,-368,885,-714,-1000,745,841,-817,-26,-249,864,-320,6,878,-849,-510,-81,868,-540,-546,-57,35,-402,519,629,464,-254,-458,-694,-1000,-1000,-211,-386,285,-221,-1000,-454,393,572,-76,-1000,40,-1000,-724,-790,368,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-431,-1000,-704,-214,746,384,-609,-912,30,400,89,401,-211,-179,662,246,101,-179,-484,-189,-640,450,-254,646,-48,544,1000,-126,236,229,96,-205,-429,-191,367,569,-80,748,514,216,404,-511,395,40,-48,126,-500,-85,713,-307,133,-73,82,73,567,178,576,-319,265,214,996,-602,-618,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{118,-1000,-949,-1000,1000,98,-81,-897,1000,1000,-58,423,-1000,-828,219,934,111,-985,72,-589,-689,-462,727,-149,-17,498,623,1000,1000,291,-1000,177,-1000,-418,-660,-290,-767,1000,-385,-110,112,-75,-7,-786,394,-974,-363,-814,-186,-345,-345,1000,-713,-865,521,745,-314,-59,-1000,-272,502,-944,-947,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-1000,-469,-321,-1000,1000,298,-789,-997,-317,301,1000,-1000,-206,-1000,-233,-515,-38,-664,494,-1000,-916,1000,-1000,-1000,-149,-428,1000,-978,1000,-52,929,-192,132,4,-496,258,15,1000,531,604,-1000,-1000,-453,21,426,-123,-182,142,101,494,-1000,1000,796,938,-569,-39,914,-1000,-819,611,842,-491,-885,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-267,-689,-966,-545,382,-268,1000,-923,885,-785,-1000,-46,-136,868,352,254,360,-272,-971,842,553,-1000,699,1000,442,531,38,409,-724,237,-1000,-1000,-609,155,-742,-355,239,696,-532,-87,-438,1000,970,76,713,-1000,-560,-303,1000,-542,-251,-215,-99,-596,155,0,-612,257,482,-621,987,1000,-598,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-372,-961,81,165,-490,249,236,100,-130,-595,-829,-960,112,261,423,-640,800,364,572,101,606,39,-234,542,356,-343,-297,-192,-466,-692,-812,-402,162,597,-772,318,-424,-522,-486,550,-719,-187,99,158,-164,-380,85,97,-510,-114,-764,-532,-849,-755,560,272,225,813,97,903,810,69,448,473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{540,-1000,-602,-290,8,975,724,-75,-788,-734,-325,375,907,63,-985,652,943,584,-228,-163,658,-1000,-924,334,1000,-78,561,-593,-108,187,-433,-1000,-279,-1000,-369,498,1000,-225,645,-590,-87,1000,-332,-506,1000,-1000,610,-948,-973,-971,1000,286,-623,-32,-992,-429,88,918,230,330,-481,100,355,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-445,-887,-167,66,-601,511,277,-126,-330,-688,281,270,939,-745,-687,405,-652,849,-113,597,1000,-546,101,359,817,684,85,-1000,172,522,-373,-318,514,-279,-1000,-80,696,1000,1000,-809,-1000,510,-226,549,988,-1000,789,557,-515,-746,13,1000,-31,-353,238,-1000,590,-206,-300,463,306,489,-387,910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-445,260,-916,-1000,688,1000,63,161,-623,611,757,-581,848,-41,-1000,-733,208,-503,-1000,-151,128,257,-713,-231,622,72,-592,-160,-622,-1000,411,-609,492,-1000,-73,509,696,-1000,-729,-575,-181,522,788,549,361,934,583,-855,-774,-746,836,-1000,-393,487,-167,255,-572,1000,-300,463,-203,-268,444,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-858,-571,-940,359,811,-338,-20,-1000,-233,226,285,837,63,-985,385,726,367,-159,213,714,-225,-589,334,898,532,213,-490,-342,-234,-274,-442,275,-1000,-147,214,990,-444,225,-590,79,1000,33,-602,1000,-909,1000,-960,-1000,-1000,766,1000,-623,326,-1000,-205,-217,1000,-636,266,14,-123,86,573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-263,-1000,192,157,-617,523,724,-191,-138,-1000,-393,-52,526,-222,-181,390,400,495,-386,408,281,-1000,-788,889,-111,-22,-24,-14,579,341,-62,-1000,-217,-480,-654,241,1000,630,1000,-266,-421,988,-277,357,242,-818,42,43,-157,-371,484,-1000,-326,-324,-186,-1000,366,39,-1000,-177,-193,507,687,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-260,-1000,983,684,-902,-446,187,-510,468,-1000,-543,-490,-505,797,1000,819,-887,124,831,-118,-184,-814,-531,963,-467,-153,683,-301,817,-389,-1000,733,764,764,-1000,-276,-822,976,119,671,-1000,956,-900,-146,-897,-818,-370,-763,704,1000,106,-304,-1000,-275,-88,-756,428,534,-304,-168,-193,345,995,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{734,749,117,-1000,448,678,704,391,-189,919,961,-1000,872,357,-784,-1000,479,-701,-1000,-1,1000,564,-752,-102,464,400,-1000,1000,-450,-1000,1000,395,920,-1000,-146,368,-1000,-64,-787,-310,-150,176,768,255,-686,1000,210,1000,-404,-631,365,-1000,-482,-766,223,597,-564,860,552,-921,655,20,846,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{1000,-490,-869,638,571,659,508,-163,1000,82,1000,-256,-474,-264,-223,171,-1000,708,-610,-1000,-474,880,-731,-268,-1000,957,-32,-269,-627,-430,438,336,30,-293,542,1000,-214,781,212,-308,212,-466,-267,440,190,311,1000,346,1000,182,511,1000,754,-422,-908,421,-207,-389,1000,-403,-784,806,437,565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{-198,915,-891,-373,-815,64,716,-60,1000,-1000,1000,577,284,-1000,610,-1000,-1000,509,193,-1000,-713,-250,-480,-528,-958,323,-865,-240,-283,196,-557,164,781,230,1000,-525,-191,-737,-965,-325,-421,-277,171,-434,1000,-462,1000,-110,363,-207,-361,-592,1000,-1000,77,69,840,-354,402,-975,546,874,798,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{1000,-1000,-502,1000,30,343,372,-1000,631,51,-617,457,273,-78,181,-389,-1000,449,-511,-1000,125,-378,-461,-523,400,803,-896,-745,-765,970,-10,204,-5,311,1000,199,-245,753,290,-504,-257,504,-252,440,1000,357,1000,-299,1000,-163,699,563,-578,-260,-908,452,248,-1000,303,-517,-681,1000,1000,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{1000,-347,13,-427,569,350,578,145,620,1000,-762,-136,553,1000,870,-746,-79,-172,-248,940,330,-913,1000,439,-25,-1000,-249,-760,413,1000,-1000,-798,1000,-300,767,-1000,781,-944,-1000,-906,1000,685,483,-557,-259,-325,52,963,-368,-1000,-838,-130,3,55,556,-1000,-1000,71,-1000,568,-653,729,-28,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{49,-286,-67,-362,-1000,322,-87,369,-837,-417,952,1000,-364,-4,503,-956,-495,-17,613,-650,-770,-181,-381,936,274,-596,-947,150,-481,-294,-848,-626,395,-441,266,-1000,-123,-553,-641,186,-851,816,66,-772,1000,1000,69,-457,-498,-304,477,-1000,706,-643,-658,-319,1000,27,-359,-697,-2,671,234,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{38,-1000,-513,461,569,601,692,689,548,-39,669,-758,-260,-293,-7,-13,-656,910,-508,-494,42,1000,-911,-25,-1000,926,-1000,327,-111,-609,346,239,-82,-300,664,830,-30,482,-369,-294,198,-1000,-1000,-6,-911,809,957,1000,-107,563,412,-1000,427,55,376,-310,-684,631,554,287,-623,-1000,-28,593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{-1000,-724,238,1000,41,-1000,-356,-218,74,-417,174,682,1000,-65,-181,1000,604,1000,-423,-716,-963,-493,-344,1000,547,398,-276,-511,79,-294,336,940,383,111,-1000,122,777,184,99,-430,-444,-863,1000,275,-111,821,637,736,444,-210,970,392,-606,-239,-875,-978,-770,-651,591,-697,-225,-357,-1000,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{1000,-781,-928,1000,-268,-282,81,-1000,1000,308,-616,116,534,169,27,-142,-1000,534,-553,-1000,-210,1000,-374,-719,1000,1000,-1000,-1000,-867,1000,365,327,-145,316,1000,589,-167,1000,1000,-1000,-28,933,-715,1000,1000,1000,1000,-1000,1000,-221,777,-335,8,-915,-1000,974,-205,-1000,848,-1000,-828,1000,1000,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-84,367,96,1000,128,-261,686,-1000,1000,-1000,211,400,100,-438,712,378,-926,398,707,829,-767,-797,-368,260,-801,1000,110,-167,-1000,-76,844,47,-1000,-283,626,1000,508,-351,523,-159,470,-1000,-331,1000,1000,-660,-143,596,650,-1000,366,-719,501,901,1000,-455,-98,-677,115,-33,-247,-1000,647,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{955,900,664,-88,920,-555,-380,978,-693,-1000,59,-133,139,79,-511,-235,-979,-521,-925,782,-807,500,438,-37,1000,-1000,555,-376,1000,-265,-511,-1000,-938,405,-1000,-508,-534,734,-932,-622,-779,870,440,689,-686,756,82,99,-904,114,218,-742,-687,-1000,-20,296,-570,-538,648,605,341,85,115,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{119,-1000,-1000,1000,-1000,102,198,-326,715,255,1000,-549,491,-151,-899,-653,-959,794,706,1000,-1000,71,679,-1000,-986,600,574,651,-1000,740,1000,1000,-441,-786,-472,-1000,1000,7,-164,-1000,-335,157,62,1000,1000,97,-793,219,404,406,330,-1000,1000,653,1000,-77,1000,509,1000,1000,-1000,-51,-437,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{20,-1000,-610,1000,-63,96,298,419,-1000,378,1000,-91,-516,-1000,710,107,-1000,685,625,1000,-487,-176,1000,-1000,654,-602,1000,368,-823,853,744,47,-890,-809,275,-88,782,559,-178,-1000,-1000,1000,285,1000,14,868,-66,-45,3,-737,-414,91,237,-46,1000,330,48,-828,872,-46,-1000,-438,-318,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{461,-1000,-444,1000,281,-146,-153,767,-467,364,1000,-207,-172,-933,710,-147,-1000,681,625,1000,-808,32,1000,-1000,-383,-112,1000,194,-710,522,618,47,-1000,116,-118,-303,732,469,-82,-1000,-820,1000,231,1000,957,1000,-64,-200,647,-737,-414,91,408,495,1000,202,48,-52,924,583,-1000,-736,-1000,786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{536,1000,459,429,939,-74,138,219,679,-1000,139,640,506,-226,-496,211,-1000,-437,603,1000,-938,-62,-47,260,263,221,269,31,-633,-422,640,-1000,-909,79,197,910,176,-654,955,-483,-173,-847,417,1000,463,-256,-426,216,560,-628,366,-4,-292,175,648,333,-715,-9,333,215,231,-1000,-357,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-397,-405,794,-490,118,-892,828,-5,-4,392,-381,-556,-273,774,-806,707,300,96,114,191,725,52,-179,971,-252,-355,-130,574,-315,555,26,-843,-37,-213,-748,25,967,336,77,-506,-927,-185,-517,945,-592,15,517,567,-244,-856,157,-193,-671,-816,617,-214,-371,-787,-785,81,-466,-199,258,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{721,-1000,-540,349,784,678,-923,791,-235,-1000,904,649,715,479,955,517,-444,400,799,-426,779,-1000,505,-870,136,-669,433,881,-1000,-455,-127,556,-1000,573,1000,-790,545,837,-429,1000,824,1000,-667,-1000,-989,518,1000,-106,800,-1000,106,329,-139,-7,-807,1000,-8,-967,958,-920,-546,857,1000,-739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{720,-400,1000,-61,-304,-758,-218,-216,757,173,494,474,344,1000,-1000,1000,-30,-43,917,111,116,-1000,-276,-398,-703,-161,-231,643,-1000,247,-760,-337,-149,51,639,373,400,677,-351,491,-400,212,-804,-21,275,283,361,-259,-515,-15,-480,143,646,6,400,-77,591,-373,-121,2,-288,854,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{928,-640,193,688,-523,-481,-214,58,973,796,-399,-81,731,1000,947,1000,-906,-396,1000,951,3,-1000,-64,-107,-1000,-630,1000,1000,-1000,1000,-663,-383,-600,-387,380,1000,928,418,-347,-216,-688,-707,-559,170,-206,160,120,-270,-883,-360,-334,-764,624,-604,247,-64,-279,-624,592,722,-53,199,730,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{403,-1000,1000,329,-340,-178,796,621,1000,696,-302,-379,-464,-174,-1000,-455,832,-81,108,1000,16,-880,-521,-198,-1000,2,1000,452,-1000,1000,-322,-766,-1000,-1000,-476,1000,408,1000,-685,-694,-1000,-115,-804,1000,58,257,10,-54,-1000,-717,-1000,4,-692,-241,49,202,216,-931,-1000,-1000,21,394,497,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{928,1000,-1000,-383,319,472,1000,-242,938,-1000,745,1000,-1000,563,1000,1000,1000,-1000,-86,348,-1000,-575,1000,-1000,-806,1000,-493,958,201,1000,1000,1000,127,170,1000,1000,-1000,-359,-1000,-316,1000,1000,-1000,-1000,1000,1000,581,-1000,187,1000,-1000,969,-1000,1000,-1000,-1000,1000,861,-886,-491,-252,941,770,-700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-37,-1000,124,108,574,917,-31,497,653,392,317,-36,-157,597,-1000,-462,447,367,1000,126,-395,-520,48,48,-387,-135,547,733,-521,238,171,-601,-798,-678,386,498,-475,972,-119,-65,139,313,-466,932,-123,269,612,7,-674,-232,-286,476,-819,-7,1000,745,289,-179,40,-444,-261,415,249,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{1000,708,314,-403,-234,-953,-336,-576,4,-702,710,1000,377,543,305,1000,-196,-161,917,-253,-72,-720,194,-955,-338,-1,-1000,830,65,19,-711,90,27,-387,1000,103,113,-378,-400,1000,-688,427,-941,-1000,572,549,260,-596,657,693,-89,357,1000,339,63,-703,-279,348,26,-231,-537,929,914,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("ARRAY:[C:100:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{485,-640,1000,-1000,-442,1000,-36,1000,421,1000,-1000,-299,1000,467,1000,-674,-788,-251,-583,-806,501,1000,-1000,-1000,-447,647,-286,49,-26,1000,-1000,939,-736,319,377,1000,-750,776,-1000,-1000,126,714,185,845,1000,866,621,-527,733,-30,-586,379,404,-161,-71,620,1000,-1000,1000,-1000,-1000,554,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("ARRAY:[C:4:24:java.lang.Character:MA==:24:java.lang.Character:GA==:24:java.lang.Character:fA==:24:java.lang.Character:Lg==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{566,-954,54,912,-625,-663,445,367,-1000,-240,644,-23,-1000,-1000,-905,1000,176,-1000,892,814,-1000,-1000,435,-1000,-1000,-1000,-879,249,118,-1000,1000,-72,262,-505,-788,-1000,99,-727,1000,624,-223,214,1000,240,-836,893,-1000,1000,-1000,421,-410,-1000,-160,-483,-1000,-402,-1000,1000,-592,25,605,420,1000,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:EA==:24:java.lang.Character:aA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{25,-82,-196,-141,217,832,-591,-161,42,170,886,-654,155,463,-408,363,4,-348,-331,-213,-96,228,-1000,400,1000,-115,-537,141,-610,327,558,126,-722,98,-546,1000,-326,289,-1000,-228,609,-489,74,466,-342,508,-198,-400,-768,-296,-142,106,162,-126,-641,-147,-185,332,172,-38,-400,-282,-400,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("ARRAY:[C:8:24:java.lang.Character:SQ==:24:java.lang.Character:bg==:24:java.lang.Character:Zg==:24:java.lang.Character:aQ==:24:java.lang.Character:bg==:24:java.lang.Character:aQ==:24:java.lang.Character:dA==:24:java.lang.Character:eQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{-114,-1000,251,-1000,-219,709,-512,406,-590,1000,-552,498,-400,-71,-223,-111,-15,-1000,-1000,-1000,-715,912,-51,-214,-623,1000,-692,443,845,1000,-402,-503,-835,-756,756,1000,-494,644,-399,-859,294,1000,-1000,1000,112,191,338,-539,-655,-1000,487,-95,1000,1000,-722,660,986,-94,423,-198,-944,583,18,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("ARRAY:[C:4:24:java.lang.Character:cA==:24:java.lang.Character:ag==:24:java.lang.Character:Ag==:24:java.lang.Character:Cw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{-36,-731,-157,438,722,1000,-616,32,-196,266,1000,240,-662,898,-117,655,638,-90,-290,487,-32,736,-108,-98,308,103,-744,1000,-1000,-124,423,104,-1000,-770,-395,1000,1000,-118,432,-700,1000,137,465,457,359,1000,-599,-521,-1000,-20,-762,-392,260,394,-1000,-918,-451,515,-464,-432,419,448,-283,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:aA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{-1,-640,193,455,20,693,-1000,-389,-611,-333,814,-97,-277,679,1000,155,433,1000,343,716,1000,-772,407,268,-965,647,-783,49,-776,1000,-211,223,-952,-285,-48,-1000,923,-566,-1000,115,44,1000,999,-55,730,146,-449,-61,-605,492,-1000,-8,-388,734,-869,-676,-415,-680,-476,-1000,397,554,732,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:Kw==:24:java.lang.Character:GA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{469,-1000,256,588,-1000,-262,1000,1000,-598,-503,942,578,-725,-1000,-581,1000,-546,-878,64,1000,-1000,-1000,1000,-28,344,400,-878,70,140,-1000,1000,-82,262,-284,-878,-1000,-905,-944,1000,607,863,311,1000,431,-1000,1000,-1000,775,-1000,1000,45,-1000,-940,-395,-1000,325,-1000,1000,165,25,-353,-3,927,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{245,-1000,632,544,-343,667,-11,727,1000,550,948,-1000,-983,1000,2,192,494,882,1000,-383,-1000,4,-1000,-590,-1000,1000,-1000,824,1000,1000,232,368,153,54,-675,466,-1000,787,552,492,1000,995,-1000,-62,-398,-681,297,290,-491,-1000,1000,670,-995,109,-468,-426,-1000,-1000,-875,-389,1000,-942,88,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-1000,-723,-347,492,68,804,-313,227,952,-81,-1000,-421,-498,-575,914,-1000,-1000,475,562,-531,576,-532,-680,-952,301,-368,1000,-673,417,827,-531,874,-482,-1000,-284,384,1000,688,-513,-1000,-764,-430,-186,-523,745,1000,-273,219,126,1000,-614,-838,829,283,-835,-144,1000,-582,1000,-1000,927,408,-908,531}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-33,-500,732,1000,-52,-29,1000,87,1000,-624,806,-63,1000,-1000,-1000,1000,717,-1000,802,523,-729,1000,-837,-1000,-1000,1000,276,-1000,840,1000,-1000,-734,1000,-93,513,-318,-1000,1000,233,1000,856,242,-1000,1000,448,794,1000,-1000,-1000,-716,-607,979,252,-1000,-717,-448,-1000,1000,-1000,1000,1000,-861,-655,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-1000,-658,-557,-281,1000,-82,3,-694,-174,433,-436,-498,325,-395,-1000,-706,-1000,-510,-772,780,361,108,95,160,-712,233,-304,574,214,518,-172,-737,74,-504,-796,1000,340,-188,-939,-1000,-691,-654,269,330,965,-365,-997,74,924,40,-329,241,-158,-1000,8,-213,454,1000,-308,348,-419,1000,602,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-1000,-1000,-968,1000,807,1000,-358,-14,1000,492,1000,-183,-884,-1000,-1000,-1000,-659,-330,-32,607,-1000,520,-1000,-590,990,353,-1000,537,-760,-41,-881,-1000,426,-1000,-1000,567,-611,107,63,-1000,-312,-1000,547,865,965,-1000,-1000,-979,-48,-388,1000,1000,-1000,-777,111,-1000,-360,-845,-385,726,266,60,-19,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{705,413,895,-396,-1000,-1000,398,911,264,-1000,-668,547,-831,241,1000,1000,998,981,717,-994,-177,-1000,578,-644,-563,343,864,344,1000,-1000,1000,955,18,1000,-76,542,590,-285,-642,100,-108,1000,-213,-586,-460,1000,492,143,491,-26,-614,-1000,957,217,1000,45,1000,-26,-624,-52,-123,672,-748,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{577,320,431,810,-975,-563,-529,-140,770,-624,81,540,117,-910,-96,688,198,314,972,-568,-729,-288,-837,-771,546,17,315,388,167,-200,589,820,-25,-93,59,-219,975,783,233,-754,55,920,229,198,438,486,665,-383,-410,-175,969,-782,858,-24,809,-872,994,-403,-446,563,896,-223,-655,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-213,-1000,-226,1000,218,-652,-1000,900,1000,-342,-187,-450,-347,-531,-897,-515,1000,-400,404,641,-960,212,-1000,-713,-304,602,-741,-398,190,-928,269,-400,1000,-114,-446,566,-1000,1000,-738,1000,574,-1000,8,284,42,1000,-629,-403,521,-91,-625,853,987,-57,816,1000,-24,517,-1000,-328,235,7,-177,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-1000,-658,-912,1000,710,574,-270,-417,264,-1000,1000,-5,-245,-873,-794,-1000,-588,-1000,-52,557,-944,1000,-948,-415,111,158,-985,344,58,-447,-819,-1000,131,-1000,-857,542,146,-434,-367,-850,-410,-1000,506,522,976,-272,-1000,-107,708,1000,-614,373,-173,109,857,-108,-1000,627,187,-75,25,638,88,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{-515,950,-1000,-6,-658,-341,-217,-899,-375,425,-1000,1000,-123,78,85,-511,920,-330,-627,648,-763,638,1000,1000,-795,-629,-230,-678,-824,0,241,287,1000,-310,-245,122,-713,-520,1000,-1000,-242,-258,-596,-223,-628,-1000,527,402,660,-40,160,631,557,-770,-732,-132,1000,60,60,-402,976,-796,-26,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{-784,708,-102,219,-897,-237,-80,-970,-131,828,-61,8,-737,-29,79,247,319,-628,512,1000,-696,-318,1000,580,-372,141,631,154,453,-250,-425,-407,998,296,-774,-261,-219,-77,333,-687,329,-393,-103,-767,-276,146,858,714,-502,-712,-432,-335,455,-260,480,64,864,168,-682,-4,976,-357,-625,840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{-595,1000,-381,77,-996,-466,-336,-106,-307,1000,-547,-353,-713,-139,-1000,-758,516,847,1000,-742,-548,40,887,-917,77,979,274,-1000,620,-481,208,190,1000,525,-820,-279,-593,-45,135,-1000,-78,-661,259,-35,-94,6,1000,-485,-203,-893,-104,-123,1000,-272,707,-1000,887,85,-58,-269,-360,-778,-755,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{760,-775,1000,1000,-1000,-1000,-312,399,-260,933,938,1000,-789,-1000,-348,1000,-521,1000,1000,-876,236,-1000,-155,1000,951,588,1000,-357,1000,-641,979,-383,274,1000,453,-549,1000,612,1000,-1000,928,-1000,204,-893,-480,-263,901,-274,400,-434,-1000,-1000,-335,247,1000,1000,-555,-66,-879,189,-1000,898,182,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{-375,103,1000,697,222,-608,64,898,-1000,-873,775,615,901,-1000,-1000,1000,415,2,1000,-1000,-659,-183,-22,-646,823,143,1000,-777,399,81,1000,-336,-210,10,54,210,1000,519,223,-435,551,295,-621,1000,-1000,-482,-260,-108,1000,-261,-1000,-903,-385,-1000,-1000,1000,-1000,-1000,-347,6,-118,1000,87,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{195,-193,20,53,-1000,-626,-203,194,462,759,186,1000,657,-53,780,-92,-702,514,-311,-419,261,-148,366,847,1000,-198,-9,-17,72,-338,-765,74,361,455,1000,601,-117,187,1000,-668,180,339,-495,-1000,-125,-82,-960,-554,1000,-851,-797,-123,141,-310,-158,441,167,-371,-1000,-269,1000,1000,393,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAw", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{1000,-379,-975,-699,-1000,-801,30,-919,1000,1000,-410,-567,1000,977,972,-514,-741,-184,-1000,546,-341,965,302,27,-844,-1000,189,-102,419,-402,-976,1000,1000,-1000,1000,1000,-433,255,1000,-1000,97,1000,-281,-1000,-130,44,1000,402,729,-1000,-352,-1000,560,703,344,-1000,1000,1000,-1000,-535,441,-335,565,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{942,-259,400,3,-434,-831,-655,151,572,996,296,-444,281,206,813,560,-760,-402,-87,-278,-155,187,-952,-228,507,62,505,-801,619,345,465,-329,-944,-442,574,535,82,-282,218,-898,667,762,-65,-931,8,546,374,-484,-257,-417,-946,-778,-185,925,797,-320,249,165,-840,-794,-824,390,-533,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("java.lang.String:MzA2", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-752,-631,-1000,-990,912,295,619,-523,675,-350,-252,51,-663,315,-631,323,306,-334,-73,-1000,798,-6,-459,722,-615,-926,-921,490,235,685,880,489,242,892,377,780,619,116,-709,1000,468,805,554,271,316,250,102,-560,619,-776,-16,-555,-767,265,-925,-644,1000,-717,455,-544,1000,-355,987,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-563,-219,28,-569,698,58,-323,-311,-112,-103,438,-173,485,-439,-502,643,757,1000,713,89,644,428,-56,-889,474,235,-1000,869,-935,685,221,-190,397,195,195,726,830,452,-47,476,340,382,-978,58,401,815,-375,-560,27,-833,622,-1000,205,-497,-475,-302,-1000,-303,-62,-180,145,489,1000,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-474,1000,-1000,1000,-666,-488,1000,607,223,104,-183,-1000,1000,1000,366,922,-231,-429,-464,-890,-1000,770,1000,413,137,42,-43,-390,1000,1000,-868,-580,282,-1000,-99,-349,1000,-1000,-103,469,-1000,589,1000,967,-526,915,-277,909,-1000,-700,-1000,479,-840,1000,1000,691,494,-311,-938,527,229,279,-994,-848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-748,-979,1000,-1000,788,1000,-997,-502,-20,270,-266,-400,897,-909,-1000,-9,111,582,-240,87,1000,-124,-1000,-469,-697,-602,-884,506,-1000,581,-114,979,569,937,-374,436,319,789,-325,270,14,-654,-287,438,58,273,790,-514,746,-145,693,-1000,-546,-42,-1000,-1000,-1000,-513,349,-114,266,-545,1000,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-501,-1000,-492,-1000,1000,354,-539,-743,435,235,-896,40,-892,-1000,-606,-471,-463,345,-535,-595,970,-1000,1000,547,-1000,-593,-929,706,-1000,552,278,554,-250,900,11,421,-8,218,216,789,335,-723,-465,400,-5,-260,620,-631,805,-61,760,-456,-582,438,-1000,-1000,-265,-427,179,-28,833,-1000,993,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-124,-496,449,-456,580,407,436,-123,-412,306,692,1000,-161,-1000,-230,1000,664,519,-563,300,-490,1000,-768,-9,-87,607,448,835,-1000,706,-1000,-888,-221,956,-24,569,548,-93,-171,212,-939,1000,1000,-1000,339,-132,-853,1000,995,-103,1000,-992,-994,-644,-1000,-670,820,16,681,1000,487,-190,1000,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-1000,-1000,400,-1000,1000,936,-985,-610,-112,-112,-926,20,928,-1000,-452,-467,-323,105,-770,-1000,-1000,-901,-1000,401,-1000,-557,-1000,-157,15,805,723,1000,-60,1000,601,679,-205,437,359,1000,23,-1000,-737,388,-781,-650,330,792,1000,-296,185,-599,-857,527,-157,-1000,-620,-291,1000,47,1000,-1000,1000,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{364,-1000,-952,1000,-98,-177,179,-559,247,-704,-157,292,-714,631,-163,532,-383,199,202,955,1000,-106,-180,-239,347,692,-380,-236,358,-244,587,-240,924,258,-288,-554,-90,474,206,-1000,-43,1000,1000,1000,-161,242,-617,-172,-728,721,-17,-335,-672,275,-1000,579,418,-926,598,-1000,271,489,-1000,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{-505,37,797,-831,284,-1000,656,725,814,-190,-583,1000,-411,-877,1000,559,-71,352,-400,-1000,946,578,938,-1000,-231,-481,-1000,874,-506,538,-42,-681,-1000,663,-1000,-1000,-242,230,-134,-845,-868,-1000,-254,231,1000,1000,-430,-1000,-707,1000,1000,-182,998,87,283,-962,-294,-487,-230,400,435,1000,-687,746}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{-146,-1000,-439,508,999,950,-207,680,-468,796,-806,6,712,-458,895,856,420,-1000,547,-135,-796,-1000,-1000,-1000,-999,1000,1000,-541,-1000,-714,1000,-573,-1000,603,470,-229,-915,1000,1000,-338,871,1000,1000,1000,-1000,517,1000,-79,-407,-1000,1000,-709,1000,1000,366,-1000,-1000,1000,910,1000,-133,-574,725,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{555,84,-141,186,-41,215,-258,-129,20,-529,-465,292,110,252,65,502,-190,-595,1000,283,47,-228,-183,-467,-151,-283,-472,-694,425,-345,376,34,-1000,-930,-83,-688,73,-109,156,-322,-441,-323,439,-185,-213,469,-116,-181,358,484,288,-9,271,493,248,-398,7,235,649,628,-20,-5,-400,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{197,122,-896,770,733,-22,-457,-420,324,-88,-388,911,-427,-998,363,-31,800,-577,-974,-910,-177,-448,173,-780,-986,520,-976,331,-543,783,-981,-844,487,830,-818,-641,-330,630,-560,-129,-618,-405,113,-536,-485,898,-964,-150,-404,847,447,-381,-422,-338,-341,-882,958,497,828,39,465,828,-368,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{50,-1000,-723,1000,129,492,1000,-1000,-977,742,-677,-176,-1000,-394,-316,335,-1000,408,1000,904,1000,-1000,135,-101,-151,-796,-362,-1000,358,470,-719,-544,-87,169,910,-673,851,1000,766,-1000,-934,265,633,1000,786,-188,346,46,-1000,1000,-250,-46,104,-848,-1000,571,536,-924,719,-1000,1000,898,-1000,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{1000,-1000,1000,-1000,-311,-1000,311,744,1000,-1000,357,717,-592,646,986,898,995,301,-595,1000,-791,549,-284,-574,-768,1000,-1000,-1000,1000,-1000,1000,1000,-405,-701,-520,-944,-388,-1000,1000,62,1000,1000,1000,-1000,-373,697,317,264,1000,62,1000,1000,-1000,1000,1000,-456,1000,1000,26,-468,-1000,-284,1000,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{-681,392,686,145,-572,-1000,304,489,-381,-87,-1000,-782,-835,-493,598,1000,-320,1000,434,-1000,641,-1000,717,66,-931,274,-469,-417,1000,-311,124,-585,206,558,-1000,124,983,440,631,-1000,263,1000,-835,1000,-612,332,347,-792,475,-200,641,-462,1000,-773,-1000,19,-171,1000,272,-992,22,220,-723,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-242,584,-1000,900,-736,-512,39,929,-1000,-450,-1000,-873,-358,294,370,-819,-310,-436,-1000,167,347,716,815,790,-819,388,-388,324,-1000,-169,1000,907,-466,-494,-237,58,1000,-819,-42,1000,-822,-1000,944,273,-42,891,-935,335,1000,-1000,510,-486,230,-241,1000,650,-1000,-506,-950,1000,747,-1000,1000,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-137,1000,-1000,-776,68,-947,-606,1000,-560,477,-1000,-34,-358,1000,454,-1000,-1000,-592,-1000,-82,860,1000,1000,-772,-1000,-156,12,-211,340,-1000,1000,1000,-189,-962,-869,-796,1000,-470,562,1000,-1000,-1000,1000,1000,248,640,-276,656,1000,-993,804,-1000,-497,149,1000,432,194,-203,-1000,1000,-1000,-534,1000,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{296,-675,-786,-265,306,-76,-308,-358,434,603,784,302,547,-322,561,-355,101,-10,-839,-462,-201,-485,690,-51,305,367,-190,-448,-11,438,-282,-400,-1000,-658,-210,374,-567,8,33,-714,474,1000,1000,-278,60,808,333,56,-35,171,560,-601,-38,112,400,645,-682,376,929,-1000,-1000,189,836,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{898,-559,-476,335,-105,9,784,-834,779,-270,-133,-735,-1000,-383,824,-356,-534,721,531,-171,-1000,-1000,-861,685,607,447,891,-669,566,799,691,-1000,-882,848,974,529,-316,-229,-765,-537,596,-1000,-135,386,-795,323,-271,555,-543,-759,-378,991,-154,-1000,1000,-262,893,-862,461,843,-633,-873,-98,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-691,473,-1000,656,-821,-902,523,-353,236,37,135,-201,940,-187,1000,-1000,832,-399,-1000,-262,936,716,1000,-140,580,258,-648,-424,27,116,1000,907,-857,-541,-226,289,-29,-433,-42,1000,-50,-15,1000,-887,-505,1000,-662,-51,253,841,1000,-153,-855,1000,-572,542,-979,-408,-263,-1000,1000,1000,1000,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{836,-784,-628,-147,485,1000,-347,-358,-421,726,73,135,-530,-104,402,-431,-466,374,119,-570,-982,1000,944,838,1000,770,769,-514,589,552,-819,-1000,479,-196,770,215,94,-565,129,-526,413,-567,-268,362,-353,1000,-291,759,-20,-1000,101,-751,975,-339,1000,295,-425,-636,1000,-437,-878,-269,400,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-186,626,-848,1000,-895,-687,-5,504,345,-1000,-807,-592,117,301,491,-1000,394,633,-717,-826,423,-1000,-74,960,-1000,751,68,-625,-1000,-671,869,907,-1000,777,673,329,606,-824,315,114,-1000,-550,1000,-131,-625,1000,-1000,-64,450,-92,-25,1000,1,-1000,1000,-83,128,-1000,-786,400,1000,-931,-332,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{1000,-1000,-219,299,1000,1000,-857,-984,-543,-614,1000,-836,-640,-506,-504,524,679,1000,1000,-1000,-1000,-1000,-1000,689,998,540,1000,-1000,1000,134,-1000,-1000,-1000,1000,1000,417,-1000,-406,-631,-1000,81,134,-961,-604,-698,470,372,-32,-1000,462,-1000,1000,734,-1000,518,-1000,342,-1000,1000,-777,150,-193,-1000,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{617,-547,543,-1000,1000,571,817,391,-95,1000,1000,-815,1000,-1000,742,-1000,-1000,-1000,1000,-650,360,661,-1000,-804,-489,-478,147,-761,-548,643,400,138,-766,796,-764,-501,1000,1000,-506,-57,2,989,471,-648,859,-1000,22,1000,-1000,-1000,498,399,-330,-1000,-20,149,205,-414,-1000,-947,-60,-905,-344,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{383,-78,-368,30,707,381,-49,400,-1000,511,662,-464,1000,-671,400,-756,-604,-615,846,-461,461,-385,-427,812,170,400,592,702,-367,-551,400,-224,-68,185,-312,-1000,-400,312,-1000,-72,-400,1000,810,287,783,-11,399,217,-519,-788,-619,-532,-243,-308,166,-65,463,567,-656,152,-662,-1000,-498,-822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{165,734,-243,-429,836,-282,-780,74,469,837,756,986,795,78,-294,-351,-107,-500,-355,-350,-410,4,231,-980,604,486,-611,-653,-509,336,623,-284,-407,-8,-919,24,242,277,-341,155,266,-424,933,735,-503,555,-34,-253,-587,-877,-829,-853,-679,-440,-695,832,-453,-632,416,-246,-14,-728,-914,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{116,761,174,-718,119,-138,-675,93,682,591,1000,1000,-198,-710,1000,-276,236,400,-1000,930,-820,135,1000,-822,1000,-400,319,-1000,775,227,-341,678,-517,1000,141,-740,-519,642,20,1000,37,374,-12,-1000,1000,52,161,-286,687,-582,286,1000,-1000,-1000,-1000,863,6,-1000,1000,364,101,-191,-385,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-615,503,304,180,-94,44,811,-304,-515,668,659,17,-1000,-955,-44,571,-447,-665,815,55,-280,-1000,1000,-412,386,-483,-776,-421,1000,849,-1000,-851,-272,504,-440,-643,-64,89,90,52,250,1000,-985,-1000,-212,-1000,-452,-203,-134,910,-13,920,-340,248,-115,-101,-444,71,163,-532,1000,169,-194,741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-783,-289,808,57,374,-464,817,-474,-14,869,-1000,-962,1000,249,-204,-601,-1000,-476,1000,-695,148,-1000,482,109,-644,-584,-712,743,-31,813,-667,-246,153,112,-700,-1000,-321,714,-500,853,591,1000,115,64,-917,-659,-191,-278,-540,563,-199,611,-330,649,-593,149,205,-81,-523,166,1000,-834,581,741}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-1000,1000,1000,-99,368,-1000,-536,-1000,-295,422,1000,637,1000,-647,-1000,273,-1000,231,310,-1000,-210,-182,976,265,1000,-1000,-1000,-241,1000,-348,-1000,-1000,-553,-1000,5,-184,1000,-329,-787,438,1000,1000,1000,-630,-1000,-1000,-542,-751,-386,133,847,1000,-1000,-1000,-1000,1000,1000,839,-1000,-863,-1000,-1000,-1000,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{400,-238,-319,-1000,-316,751,-953,929,-719,-101,-238,206,648,-256,1000,-58,699,-437,-944,996,-1000,-343,116,433,-831,400,785,-469,-938,-332,400,1000,-639,1000,900,-271,-1000,870,920,868,-566,-1000,19,-247,763,176,883,268,-995,-765,-193,-91,-6,-780,-240,-282,-1000,-1000,-637,701,238,619,611,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-711,869,-332,-153,-618,-457,-972,-873,-524,-164,199,880,417,502,-44,-882,645,-882,-854,-671,-366,443,610,266,-550,-142,750,150,-953,-769,-426,-921,-891,-752,-43,-992,289,-730,-484,-355,476,-870,-58,814,-616,923,968,-579,-804,808,-775,3,262,-890,169,43,897,-380,-30,715,258,-145,-161,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-418,191,634,-174,303,-730,-526,-32,-284,-546,861,-303,973,49,407,-656,-481,-777,-331,-1000,-1000,1000,-1000,1000,-285,-638,991,457,-1000,-887,1000,1000,-661,-246,371,-659,1000,220,-690,96,-269,-1000,-849,1000,1000,1000,893,-219,269,-972,-540,-331,-90,-736,462,918,510,-742,-543,1000,-617,-1000,-137,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-829,918,306,-983,84,-397,-369,-235,662,1000,1000,1000,-339,-592,400,298,-135,-152,-1000,395,-517,1000,-350,-1000,1000,-580,-320,-1000,1000,459,-539,175,-529,707,-108,-546,-64,636,-558,488,326,531,-1000,1000,400,-264,-187,-500,313,-591,42,559,-819,-1000,-1000,904,-564,-1000,911,-45,95,-1000,-569,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{462,497,-108,599,72,349,-863,728,-1000,-1000,-209,993,377,683,-528,859,958,-445,1000,-1000,630,-624,1000,-1000,592,354,-1000,-317,948,218,-643,976,175,-10,1000,17,516,-84,-1000,412,-798,328,1000,-1000,-914,1000,853,-910,-383,-712,811,-194,-440,-1000,-954,-233,-1000,705,-799,875,-128,723,-1000,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{821,-357,-931,119,919,-980,732,78,-731,-392,203,-219,302,-211,-978,-1000,-228,1000,384,795,500,1000,567,115,557,381,1000,942,-20,-709,-71,-456,-551,507,-131,534,1000,-769,-639,673,421,162,-285,39,-1000,1000,75,400,533,-1000,-1000,997,-1000,323,-23,-4,20,-1000,23,-474,-18,1000,1000,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{222,-1000,318,402,654,324,783,1000,115,1000,-868,-1000,-611,-31,-257,-1000,549,997,1000,-159,584,-840,1000,-394,305,662,-444,754,-636,-750,-259,-229,961,927,-1000,-670,-1000,-141,-421,1000,924,392,-1000,-1000,-658,1000,1000,-1000,387,-1000,-1000,1000,-412,1000,481,-1000,1000,-679,-327,-10,-454,766,600,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{445,-1000,-1000,-691,-377,-1000,593,577,-373,1000,-509,-1000,1000,421,-64,-1000,37,1000,-1000,285,-56,1000,1000,-150,898,1000,1000,48,-821,-1000,1000,1000,-1000,1000,85,748,1000,-1000,-769,926,1000,-1000,-553,1000,-417,1000,1000,455,1000,-1000,-1000,1000,-685,840,1000,796,1000,1000,617,-732,1000,-255,1000,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{876,497,-1000,-886,894,-935,559,-166,-761,-1000,-209,112,377,-257,-528,75,613,-97,321,-1000,-13,-157,121,-617,1000,669,870,-495,47,-84,-1000,1000,-307,967,466,1000,921,-1000,-834,325,-771,299,1000,-1000,-881,1000,406,-346,-24,-712,-332,-194,-440,0,-25,1000,-696,-851,-799,330,-559,1000,1000,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-352,659,-1000,-632,448,-1000,537,329,-124,1000,1000,119,302,-1000,-976,-1000,-720,1000,-1000,207,1000,1000,747,809,114,297,1000,1000,-312,-994,1000,-417,-1000,552,500,-1000,1000,-240,203,1000,1000,-1000,646,1000,-1000,499,-111,872,-8,-507,173,528,-1000,864,-330,-373,1000,-556,23,875,-284,-255,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-1000,1000,417,-322,-181,259,-576,600,1000,775,1000,425,-1000,-561,483,43,-48,1000,-400,-545,1000,-667,-17,64,-903,1000,400,1000,200,678,-435,-1000,290,1000,175,-251,690,753,517,1000,255,1000,1000,-395,-77,410,30,-173,202,-514,-207,226,62,-576,-376,-802,-580,-1000,-1000,1000,-213,1000,655,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{809,926,-750,-1000,359,-1000,34,1000,-359,-586,-634,-599,921,-325,-76,-717,-464,673,-1000,494,-196,1000,-489,-749,561,1000,320,536,-778,-681,378,174,-362,1000,-642,-1000,1000,-1000,-1000,1000,-151,-1000,-437,590,-401,-10,227,547,518,-1000,-204,-1000,670,601,825,335,106,6,1000,-1000,-133,328,140,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{711,-1000,-334,408,79,-633,-129,1000,-1000,-663,-747,-1000,777,1000,-374,-191,735,512,-209,1000,716,25,-773,-949,531,890,-334,-1000,-202,222,-224,658,-696,1000,-29,803,656,-1000,-1000,269,-699,188,627,176,-839,-582,640,-886,-644,-1000,-550,979,-584,50,-54,303,36,1000,185,-812,-505,661,-1000,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{663,-1000,-383,-334,494,-646,-1000,669,-1000,-1000,-642,-288,777,901,-677,-1000,-250,-213,-1000,847,655,25,1000,-1000,906,856,-606,-918,-35,537,-2,339,408,1000,234,298,656,-1000,-1000,-471,-1000,-494,1000,101,-881,-1000,-42,-111,-808,-361,-1000,-598,-422,-835,-236,511,-944,1000,453,-1000,-182,374,-1000,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-382,-421,176,244,-267,-587,-933,363,978,303,164,-728,-248,-395,586,27,-617,-2,-648,-434,-549,973,720,111,-780,89,105,53,-893,-361,-284,472,-877,621,-480,404,-233,149,291,41,997,780,-872,158,600,628,59,205,499,-425,-871,787,389,-571,719,508,454,443,-260,554,845,-747,831,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-511,-145,1000,197,832,-344,-61,-4,944,361,776,554,611,-1000,1000,-711,-386,-1000,-273,1000,17,1000,23,-1000,-325,-644,-307,1000,468,-477,106,1000,-143,1000,-1000,-171,-150,150,7,890,1000,-109,-45,-649,369,187,203,607,-204,656,1000,214,727,-1000,239,-1000,1000,-81,228,341,737,706,-560,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-762,-635,5,-446,94,-13,175,125,-543,913,-114,1000,51,-648,222,-53,463,0,165,-972,616,-1000,906,928,-1000,-805,5,204,-660,-213,264,2,-442,643,-989,-216,-1000,760,-72,-951,837,-857,-1000,-126,781,738,-237,-345,11,253,16,-718,950,574,66,618,-759,628,-878,956,-1000,1000,333,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{545,972,-668,106,141,-699,694,963,-610,-325,-1000,-806,-378,-83,-974,-714,-1000,243,-756,1000,233,-603,-714,-915,1000,1000,84,170,1000,632,209,-1000,-308,-1000,531,957,156,570,-664,-518,1000,673,541,846,812,649,272,952,1000,33,-488,-262,537,-476,-1000,-811,560,-1000,1000,-577,1000,107,720,937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-1000,-1000,1000,708,315,385,202,-208,1000,343,-641,262,689,225,195,-730,397,301,-429,648,377,656,813,-1000,-665,541,-430,48,-28,876,995,1000,-282,1000,-84,29,-567,-86,-577,-61,111,-809,-366,-270,774,1000,537,818,836,-184,259,-474,-20,-167,-1000,-889,-264,766,154,941,-201,1000,770,-346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-1000,-1000,400,-622,-1000,-817,-882,868,-240,988,52,421,-266,-458,242,405,348,-155,651,-1000,233,-937,1000,960,-1000,-1000,1000,377,-1000,1000,897,-945,1000,-126,-1000,632,-526,-1000,-292,-735,590,-143,895,1000,-296,684,61,-324,-1000,-986,790,494,1000,851,-73,1000,-1000,906,-1000,1000,-1000,706,712,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-1000,-1000,-383,655,-974,-867,-88,648,-1000,-131,-474,381,5,-88,286,-371,29,-1000,-935,-125,-180,-424,298,1000,-756,-415,71,-1000,-958,603,259,1000,-586,682,89,1000,-1000,607,-1000,-1000,303,673,-1000,-476,850,218,-814,431,440,-306,1000,-795,1000,1000,-1000,-624,-324,1000,-679,1000,-1000,1000,1000,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{542,-190,-121,-548,-377,-506,385,838,104,694,-364,-471,-76,-224,1000,-340,-851,71,-1000,1000,403,754,958,-387,325,400,9,566,257,-206,-584,-1000,434,-1000,-1000,798,-56,-496,369,81,1000,1000,199,1000,-1000,-605,358,-305,503,330,-1000,-333,225,-275,313,111,-462,-708,-282,696,400,-107,16,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-56,-775,687,470,640,363,-249,232,659,253,-20,62,776,90,-546,409,188,-336,16,-321,756,-197,-962,17,-913,1000,-305,27,-1000,97,-131,345,1000,-202,900,1000,657,10,171,1000,-400,65,291,-92,400,-653,-26,288,1000,-284,300,-570,525,409,266,-1000,1000,436,-247,814,-662,-1000,309,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{301,105,-276,-212,-267,-574,663,-416,107,-527,77,262,1000,-300,554,1000,5,-741,304,440,203,1000,120,61,-21,-655,58,-778,389,-722,-196,774,247,-245,-183,476,125,684,283,308,-368,-485,-679,-158,137,708,1000,386,-41,136,59,652,97,518,-48,311,-910,63,135,1000,716,820,-527,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-844,-1000,347,-579,-666,-559,163,25,281,-1000,-348,-1000,-18,1000,-145,-434,1000,-180,-452,1000,-650,723,-1000,275,778,-137,-997,-1000,247,-946,726,1000,-262,-937,-190,-46,810,1000,-645,-622,1000,345,56,371,263,-809,838,182,-221,1000,-844,-1000,-1000,-25,-306,-12,309,-931,-261,915,-86,-1000,90,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-1000,-657,-2,-1000,-1000,24,684,-927,-108,47,1000,-864,1000,865,-1000,1000,200,387,-1000,1000,330,1000,582,1000,981,1000,1000,-1000,-906,1000,-1000,1000,-799,-320,103,-1000,692,246,-62,-1000,-444,-1000,-1000,-1000,-253,1000,491,1000,-1000,-210,137,1000,1000,1000,-470,-576,1000,-627,-1000,-342,-1000,1000,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-293,-358,-880,-1000,-190,-636,571,-696,-674,-1000,768,-816,1000,75,-516,-448,1000,677,-842,216,-1000,-451,-1000,781,827,164,958,123,1000,909,275,-679,947,219,-67,-184,-788,-312,-936,-535,-1000,-494,-713,940,-408,-276,265,-744,803,1000,-1000,-1000,-1000,367,310,413,-94,-437,-1000,-225,-280,561,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-886,-535,131,-1000,-447,448,-121,376,134,56,390,-954,531,444,-678,-655,-257,-433,1000,-788,-1000,-31,-1000,720,182,-1000,1000,-1000,-1000,82,-642,431,-233,-1000,565,-19,811,248,50,653,-50,-337,35,-936,-999,791,641,-456,375,-912,1000,493,1000,-1000,-375,-644,-303,774,-1000,1000,-139,445,-976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-856,-494,339,365,863,-474,-1000,-214,-367,242,-440,168,-33,-1000,-811,902,1000,-609,163,1000,-285,950,44,148,284,264,-5,1000,51,1000,1000,-107,-38,-111,373,-57,194,528,541,-80,254,702,-839,80,81,-248,498,-613,-354,-638,958,337,84,-945,977,-53,-148,-586,228,1000,1000,-706,-465,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{991,-1000,-1000,-7,-835,992,-992,321,1000,854,1000,-651,-1000,-306,-14,-932,212,647,-614,1000,-1000,-223,-1000,194,1000,138,913,143,1000,-6,-1000,40,-279,1000,145,-540,-956,604,802,1000,645,-105,-651,-1000,1000,1000,-189,-412,1000,895,330,-152,1000,-1000,-591,739,-1000,-1000,809,-1000,496,541,1000,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-127,-987,-971,1000,-569,219,-273,370,97,1000,1000,-400,-1000,-348,1000,1000,229,-128,-682,1000,-131,27,-1000,489,-253,-862,1000,281,1000,-838,-727,-359,231,1000,51,456,-286,-1000,118,1000,-487,1000,166,554,1000,-751,658,30,-159,900,-631,-796,1000,-1000,3,-183,-947,372,393,16,-1000,1000,-560,-951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-281,-926,-472,694,-181,126,-147,-202,616,497,493,119,-459,-1,-327,-855,88,466,-582,526,-739,109,-769,-61,1000,34,324,410,386,76,-509,-240,-243,632,-631,95,-699,-555,468,121,1000,-58,-372,-480,602,20,-350,-465,596,117,511,235,505,-597,-288,-75,75,-1000,-615,-695,-37,175,89,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-29,-1000,-633,-21,1000,-377,-244,-8,858,-24,417,-244,696,162,-132,-920,-1000,-123,-1000,174,-325,-677,-622,15,-443,1000,-166,536,-1000,1000,-536,66,209,54,-128,-1000,-378,848,-680,-988,-426,-1000,-336,-1000,759,1000,-979,683,531,208,-19,-823,-164,-293,-103,-903,582,476,-260,-725,439,-942,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-748,268,-435,-137,-875,864,-986,960,520,-359,174,-106,931,255,-50,-506,-454,-591,-88,-800,-1000,-922,338,-382,-658,-504,918,563,-160,1000,-290,614,26,120,-1000,-250,331,131,1000,416,-890,-351,270,-208,1000,-383,1000,-31,1000,-539,-885,-89,638,677,-254,24,489,1000,1000,119,-942,-304,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-478,-331,-387,-943,412,99,-197,-79,616,-314,692,-293,413,-768,-315,-355,194,132,168,-154,-578,1000,-918,873,-400,1000,-309,1000,1000,-54,56,979,977,-400,1000,-1000,804,-1000,-212,612,-835,-984,-176,-1000,-48,940,545,-411,935,787,-57,554,-373,-938,-795,476,107,-113,130,187,294,-1000,704,-981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-1000,-153,525,418,-586,1000,-84,511,1000,163,882,1000,-510,767,-582,-480,-539,99,-226,-1000,-1000,526,1000,-101,1000,482,-653,1000,-1000,389,394,542,917,-632,-747,1000,640,-1000,-48,-972,-40,-882,419,940,-851,-392,630,631,23,307,-136,-1000,-465,-556,1000,-1000,-1000,-717,-897,147,1000,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-283,-1000,-544,728,-487,1000,-539,127,-101,208,1000,-337,-1000,-885,-136,-325,386,53,-393,1000,-814,747,-1000,926,513,-131,565,1000,1000,397,-1000,-531,174,714,907,-862,-638,-1000,299,298,363,357,-394,-1000,481,688,326,-674,-95,1000,-355,-250,622,-1000,-1000,1000,-686,-955,67,-1000,-77,-418,326,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-222,509,-285,-1000,587,174,401,-492,910,-375,1000,-413,507,-794,-83,666,-1000,445,-461,-731,-911,261,953,-488,-72,-594,1000,-1000,783,-1000,-958,-118,1000,606,846,556,-544,1000,-798,336,48,860,579,1000,-1000,-380,311,-545,-286,-253,357,1000,391,386,212,944,129,-473,-596,-990,-234,-1000,625,851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{986,88,940,863,-306,202,129,-212,244,366,-118,-894,785,970,944,-584,-173,-523,103,-277,-324,361,263,448,126,800,60,-830,-901,916,922,820,366,-147,-824,71,950,-968,590,280,387,-423,549,476,593,67,973,-383,812,-449,-972,-364,801,-961,708,-385,-757,604,433,853,-764,629,-31,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-893,-57,-101,-828,290,311,467,-64,552,551,293,-1000,-904,630,-242,-832,705,-74,-268,578,1000,-964,-1000,-1000,447,134,294,178,-681,-210,-585,-1000,-1000,174,-1000,-689,1000,-1000,1000,-535,696,-902,261,-1000,1000,36,819,376,-472,-206,-461,232,-690,-215,-188,-1000,632,-503,717,1000,-1000,35,599,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{420,-253,765,350,-1000,-1000,-911,595,-651,385,-118,-966,907,-62,775,-503,-1000,-512,329,-312,449,-144,-95,350,321,1000,-1000,915,356,72,1000,820,645,-639,1000,-575,745,-401,1000,1000,-213,1000,549,598,593,-768,-679,-610,-771,1000,-645,-1000,1000,-1000,-850,-1000,-569,-171,-172,-230,889,629,-733,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-572,1000,-1000,-828,987,132,470,11,489,-446,461,-959,-78,559,-1000,-1000,705,-28,109,569,1000,-303,-1000,-1000,1000,-884,499,-1000,567,-210,-555,-831,-1000,966,-1000,-1000,583,-1000,748,-1000,1000,-1000,-34,-1000,1000,633,337,526,28,-336,-911,232,-662,755,-83,78,687,-135,1000,917,163,35,597,203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{521,-876,538,-16,-336,155,-806,276,31,1000,-118,-853,108,635,1000,-139,-273,-187,-1000,-1000,-184,585,642,50,40,528,-219,208,66,-73,1000,-357,498,359,181,150,1000,123,734,1000,-369,1000,1000,961,593,-956,488,-50,-253,-185,-69,330,-537,-1000,-758,-722,-569,-346,-736,314,311,-263,80,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-165,14,-355,-614,-806,-794,500,268,169,-707,183,-902,-412,-349,791,-41,-1000,459,-743,470,-104,-86,-618,-495,526,1000,208,-506,-499,-1000,-159,-37,878,289,50,-259,1000,-142,1000,-130,57,-192,456,-563,438,-1000,1000,128,-1000,701,-251,-236,1000,-1000,57,-850,-310,-392,-470,792,-753,-43,26,-472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{170,-1000,240,-285,-807,-766,-534,1000,-373,173,-793,-1000,-1000,718,549,-168,-1000,9,-897,234,-78,639,-639,277,815,1000,-346,391,383,-161,360,-595,-39,943,421,-1000,1000,-220,1000,433,-775,-43,631,78,755,-1000,716,1000,-1000,400,-48,104,-621,-729,-1000,-1000,664,-350,-481,1000,-694,-846,305,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{146,-70,-230,-384,1000,634,437,262,-168,112,-1000,298,-1000,666,1000,592,349,876,-257,-1000,322,598,368,202,-1000,-975,506,-402,-461,-730,62,-192,-1000,288,-445,-1000,1000,-1000,-971,911,879,-602,-260,-671,-387,740,137,-635,162,-509,220,889,-152,389,405,286,104,-427,-307,831,-1000,-785,-970,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-145,648,-871,453,184,-138,-1000,-594,14,-359,1000,-263,861,-1000,-108,152,-253,-918,605,-758,283,-141,1000,-1000,1000,720,-632,-205,1000,-1000,374,382,1000,943,36,1000,106,-1000,318,-287,-191,-269,216,-766,-1000,-23,-381,37,-381,-174,-1000,-977,506,-156,589,-700,-649,-42,-570,-670,405,496,-112,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,338,-877,-1000,-267,314,-207,-678,721,-141,-217,-367,1000,-1000,-39,397,199,-1000,704,-575,827,-167,843,-1000,1000,659,770,-78,1000,-782,519,1000,733,356,518,-38,959,-20,1000,-536,416,1000,-141,564,-995,1000,701,-803,0,-1000,-133,409,1000,1000,746,163,1000,-950,332,65,-921,-831,-853,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-541,1000,69,-818,-148,-644,553,85,736,-693,817,-599,-833,-540,138,203,-5,-1000,193,314,-806,926,1000,-563,1000,1000,-287,455,1000,-1000,615,34,1000,-292,-999,412,1000,-769,800,-99,288,456,320,781,-745,1000,-355,-15,-115,-781,-1000,-367,664,709,507,-334,1000,-155,-502,141,-209,-935,-372,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-876,1000,-895,-979,-366,-687,1000,30,1000,-614,-78,-1000,-998,-1000,-88,899,-576,-997,630,-959,615,-183,950,-326,89,343,36,-1000,1000,228,341,483,101,-197,930,-85,917,1000,802,-563,801,967,730,936,-378,829,-474,-234,1000,-1000,144,923,-43,804,-1000,547,1000,-324,831,615,-934,-874,-669,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-820,926,797,-1000,-1000,1000,598,245,144,384,-768,712,-1000,901,964,1000,-283,-1000,-1000,33,558,459,-945,52,311,-267,1000,-385,-746,-3,685,-105,1000,-685,-503,34,1000,-820,93,441,-309,-795,-447,891,-73,624,1000,-776,-8,-1000,1000,756,50,423,1000,101,-473,-1000,-710,48,425,-824,-365,240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,254,512,-1000,-55,729,738,55,789,-225,439,756,-563,305,-729,808,374,-141,-287,1000,-547,206,-238,-255,-505,919,998,152,-994,1000,70,-1000,1000,545,-329,-56,1000,-117,-914,-289,902,-794,197,1000,399,956,339,701,-78,583,1000,-878,-696,-1000,1000,-733,-200,-75,-396,271,-1000,-358,156,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,359,1000,-961,-1000,479,-436,712,0,-500,-978,1000,311,-644,0,900,842,-453,957,-1000,-1000,444,1000,-28,-758,0,421,-740,-509,306,-723,0,290,-662,676,-789,225,-95,383,1000,0,350,0,487,-261,-107,985,-60,1000,982,617,-1000,-1000,-1000,131,0,-19,226,-894,0,-105,256,0,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{217,-492,189,-474,-240,846,-392,433,585,-871,-384,-27,429,-1000,379,930,314,1000,442,15,-268,611,619,-1000,-623,192,1000,-1000,-534,1000,-25,125,-560,-1000,-1000,-1000,-425,-191,-475,65,-257,-666,133,679,645,44,807,825,-9,-187,1000,-677,486,-665,249,682,-407,-496,287,296,-269,-646,1000,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-687,-1000,443,515,-1000,971,83,639,15,-251,520,-1000,-938,535,1000,249,-530,145,-542,1000,1000,955,362,583,-209,67,787,-1000,229,-388,-103,512,522,-1000,639,53,-546,664,-318,-162,-844,27,-1000,-511,370,-845,514,193,56,-412,-1000,743,887,62,-465,10,751,-980,405,23,-678,803,-122,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{592,-301,-655,939,1000,-56,-532,-264,-842,-262,866,-698,-221,-929,-411,382,-20,-91,-232,490,-251,396,-353,-1000,289,73,315,-1000,56,207,-297,28,-688,-732,-826,-583,-697,-1000,-865,-114,-473,-752,1000,752,-52,1000,254,468,215,-132,126,-221,419,172,-841,591,-286,-755,1000,-1000,-133,-1000,1000,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-349,-1000,435,-1000,-315,-396,-669,495,677,-1000,-678,840,1000,-948,975,-612,1000,426,-711,1000,704,-850,-245,1000,1000,-515,722,-575,-627,-215,1000,-1000,1000,1000,448,1000,1000,-1000,-12,417,-130,422,-318,-369,-983,-1000,138,-1000,368,258,-979,571,-132,-688,-82,-18,1000,-480,284,-674,-492,394,-1000,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-478,-1000,-452,456,-127,-728,-187,57,872,70,1000,129,0,987,1000,152,-185,352,91,1000,-142,-120,-287,335,702,-1000,660,-373,-534,-983,-743,206,216,304,484,973,-30,1000,-26,-873,-244,243,-44,-407,-364,582,419,350,1000,-194,-388,-191,855,-156,-102,-74,363,497,-58,1000,-151,-17,-686,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-74,366,-390,248,314,-97,910,-802,-205,409,-445,1000,-257,-315,623,1000,-1000,968,37,627,1000,296,-347,90,194,-53,-399,1000,1000,485,-1000,241,343,-1000,159,582,-1000,318,710,-618,480,-218,901,-771,-432,564,-756,-654,-60,429,380,-405,-608,-222,-248,-421,568,442,-1000,-540,247,580,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{63,263,-130,-521,-1000,-671,-1000,728,101,301,772,348,767,317,194,534,435,-697,-147,-104,-159,278,-509,920,728,-420,-338,480,-1000,-606,146,9,672,-211,-93,-114,127,-565,375,195,-15,-1000,342,-280,-712,559,489,-415,598,137,1000,-28,255,-962,-390,903,43,465,-391,-914,-350,-98,-1000,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{687,587,-224,47,858,723,-137,-435,-479,-748,-473,53,-703,-679,-555,361,355,292,343,194,-486,7,141,-1000,-476,-630,832,-1000,-415,770,-103,229,-774,-495,-364,-435,-1000,-367,-1000,-114,-992,-932,758,806,-97,438,512,73,215,373,-142,-221,10,172,171,1000,401,-388,-737,-369,328,-493,1000,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{875,875,1000,272,333,-843,127,1000,-478,1000,-193,819,804,-1000,-371,758,-713,362,-535,-1000,620,-1000,-1000,375,998,-411,-1000,282,204,-1000,303,-1000,-937,573,1000,-1000,-1000,-1000,-1000,922,-777,367,886,-523,-776,-957,1000,885,894,-228,-1000,-42,330,-357,-1000,957,-404,-523,387,75,-908,272,-701,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{966,501,154,834,-402,-528,995,-676,338,363,633,136,146,-826,-13,701,-413,-824,-654,626,245,-315,-332,866,27,792,-781,-579,-608,-684,353,846,-874,897,-912,4,854,359,13,240,-818,-838,-985,401,-926,-841,344,16,-90,-335,605,-691,465,364,408,740,-947,-482,609,-748,-3,-454,369,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-975,-1000,-259,1000,-222,278,-1000,95,714,-651,306,-151,-462,60,536,-1000,221,-86,-557,1000,-730,166,739,987,-1000,685,1000,-1000,-1000,60,88,1000,-994,-531,-1000,779,1000,1000,-1000,-96,372,-687,730,1000,-1000,-327,-1000,-334,643,-403,359,-409,905,731,1000,-357,534,-180,-637,47,-551,-534,1000,523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{107,-91,-683,943,-1000,-1000,-131,1000,-809,-78,68,-423,762,214,-486,624,-559,-1000,-1000,-1000,-314,-208,-627,613,998,851,-950,68,204,9,1000,46,-183,-285,-711,-1000,-829,773,-706,-114,-777,37,649,-344,-799,-271,-1000,-472,519,-918,359,-511,501,1000,508,478,-83,-523,-498,-674,-370,272,524,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-468,1000,1000,230,333,-843,75,246,674,32,-332,-170,-283,-1000,321,1000,-965,-422,-535,1000,524,410,-264,1000,998,-875,810,-739,204,-1000,661,-269,-924,573,281,-258,-725,-1000,-1000,1000,-1000,-735,454,1000,-776,-1000,1000,1000,-357,-87,-1000,96,-90,-974,-538,-115,300,761,1000,-1000,99,516,-8,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-43,-63,230,1000,-793,-918,429,-1000,601,-1000,-856,-234,198,744,627,-418,-581,-94,-249,-794,-811,128,584,477,324,-370,-1000,6,284,428,184,-1000,648,-272,1000,-1000,-676,-472,658,140,186,739,222,376,312,178,608,41,-362,-1000,441,-161,-362,-1000,-1000,1000,1000,801,31,12,653,-638,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-750,-157,192,20,882,-494,133,-81,-1000,1000,-416,-1000,264,-84,1000,480,-516,-1000,-47,-778,-414,570,-302,541,212,-328,-1000,-1000,-12,-788,35,-232,-1000,-478,-635,1000,-887,-581,18,409,503,220,882,185,1000,-607,-441,303,-41,227,-677,560,-1000,-460,-1000,-1000,964,1000,610,-246,591,415,-1000,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-883,104,-1000,1000,-493,-924,97,-213,303,-289,-265,-592,187,289,-873,-302,-56,435,12,104,298,856,395,779,-98,-1000,-60,1000,1000,-415,-687,827,-186,-56,596,16,-272,-281,4,-75,-567,-1000,-78,605,-1000,44,30,184,110,457,-851,-421,-91,129,-480,-400,-323,1000,275,-492,-943,-503,-763,476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-717,-354,-16,951,78,202,-205,334,-373,-50,1000,-230,319,972,-220,838,104,542,-293,95,197,131,684,102,-809,-364,23,582,533,235,490,224,952,-714,1000,85,56,-303,494,564,-409,-457,-686,108,-453,160,34,139,367,-40,-377,353,-938,760,-666,-53,30,-593,-38,86,-1000,-671,-399,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-792,-989,745,-29,954,676,701,1000,-1000,643,587,-1000,-317,1000,902,172,-312,1000,17,-870,651,917,198,-591,1000,-810,-242,21,2,-201,742,-571,821,714,-236,-243,-1000,862,-493,-76,-449,-69,-129,-791,-490,-633,984,36,-755,1000,-295,32,1000,693,667,774,815,-1000,-528,567,757,-85,-14,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,47,-520,-118,105,-768,-946,453,-666,-792,-5,844,683,432,928,217,774,689,-501,-832,827,988,498,-1000,-291,-1000,-555,55,105,1000,287,-1000,306,-84,250,-186,-387,-689,794,386,173,-1000,-433,-1000,-900,-1000,453,-574,-1000,-482,-1000,664,-855,665,419,1000,859,-127,-123,-34,-35,-769,-24,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{229,-1000,672,-916,793,917,27,-1000,-875,881,437,-144,-732,740,-636,-1000,-1000,425,-857,-54,-1000,-164,-1000,-130,-674,-394,608,432,-973,582,559,-909,-1000,-1000,-159,589,1000,-1000,-562,1000,-141,89,-1000,-351,399,821,-433,305,-203,61,115,-655,-1000,739,736,750,745,708,937,-708,371,417,266,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{112,-865,689,-126,453,499,-646,33,236,823,-257,-998,-625,423,898,314,-262,647,340,-410,342,169,-648,73,603,-500,111,55,-557,-636,-14,-449,-192,-168,674,-312,-403,578,-972,-288,-617,524,379,-746,395,-770,-16,-443,-789,889,-205,740,346,561,191,846,523,-677,-465,417,750,485,442,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-64,108,284,28,345,-282,-1000,849,76,-566,807,-201,834,-176,-652,-82,488,414,-289,-931,899,198,967,-658,1000,-245,91,39,471,392,1000,43,1000,515,-692,416,-982,-33,40,524,-289,796,-57,394,-783,341,54,-472,556,345,464,-83,798,303,470,-115,437,364,-1000,-162,19,-83,-988,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{364,-1000,-1000,-1000,-80,935,629,244,-123,1000,-853,-83,406,924,937,616,76,-741,-787,-1000,1000,-785,-437,-45,-695,-650,-535,-32,-39,-435,173,948,42,-254,420,513,-672,-1000,397,-829,-274,882,-1000,-541,-640,-545,415,-188,282,224,-775,-12,-1000,-1000,-203,-739,-733,1000,-492,-534,611,646,-93,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{206,-954,-921,-1000,444,263,142,325,-553,942,-925,279,770,560,666,155,-451,-556,-755,-1000,990,-480,-713,251,-811,-563,-763,-374,87,-712,-1000,973,244,143,704,752,-496,-902,519,-913,-318,50,-705,-428,-537,268,879,-188,134,-67,-885,-192,-1000,-1000,-203,-867,-864,1000,-741,-762,355,713,-489,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{693,863,-656,833,-482,134,543,-504,-390,238,-1000,385,336,1000,-672,81,-538,-471,-542,384,480,-517,96,-318,-1000,-1000,121,-658,-119,-335,-1000,666,997,79,367,-357,-1000,39,989,-183,271,-90,-517,949,-859,402,-764,1000,222,753,-1000,355,22,-216,1000,-677,43,-318,76,-231,-4,1000,-1000,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-568,-376,-184,-1000,-842,1000,-241,959,657,626,-299,-791,-496,180,-23,431,815,-891,-353,1000,-372,644,1000,-157,210,823,24,1000,150,-348,213,-469,88,312,-1000,-1000,550,671,-175,824,42,-1000,438,516,-918,915,239,-1000,257,-461,-837,-337,583,648,-986,288,1000,-1000,1000,1000,870,-1000,705,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{892,-1000,-559,-811,-313,97,-203,-114,138,753,-301,1000,1000,1000,991,569,279,-583,-569,-1000,1000,-1000,-1000,523,-1000,-480,-1000,-1000,-39,153,-102,-202,-247,810,724,1000,-832,-1000,350,393,-791,300,307,372,377,-524,-479,-4,-413,521,-907,62,-1000,-1000,36,-595,-1000,1000,-985,-1000,1000,620,-784,-363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-288,-200,-598,-370,-423,397,452,-609,9,234,-1000,700,-101,900,-344,528,-1000,-802,-27,-642,-380,497,-393,-582,-574,-509,41,-804,-1000,33,-1000,59,849,349,145,665,-1000,-540,276,119,335,615,-32,85,-794,-844,-582,424,-129,-1000,-1000,274,-482,-678,1000,-727,-970,-338,91,-79,543,1000,-299,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-231,-583,-81,-1000,-831,77,-1000,553,158,68,311,-26,571,-848,561,-617,-101,-815,701,-257,-794,-260,150,1000,-31,830,-1000,1000,1000,288,-286,-202,-666,1000,-680,-257,705,348,-910,-518,-1000,-1000,1000,-12,955,1000,-57,-1000,-761,-603,-122,-533,-299,-27,-1000,611,551,837,508,614,907,-880,224,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-312,-352,1000,1000,216,-442,-212,546,1000,-447,996,-754,-1000,-133,880,-398,353,395,13,-86,-1000,-742,-256,302,950,377,-580,501,-1000,172,284,-1000,565,898,-717,-54,-50,-6,376,-1000,405,-727,-947,250,-499,-604,-846,688,-1000,-713,328,-402,1000,3,-275,-480,-386,560,107,-1000,-148,-262,1000,-432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{92,-701,-489,-231,-252,-840,454,-671,-282,714,-436,-928,-936,-367,-921,715,570,496,-66,498,-550,606,-710,-546,857,233,-1000,310,-142,180,1000,261,8,1000,476,649,917,859,-86,884,-502,-250,298,614,268,-135,-940,291,-1000,339,-681,-1000,220,1000,272,-762,-254,150,102,-309,69,268,-928,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-133,477,179,294,-471,-59,523,-155,-798,-570,775,239,-110,-572,-39,627,-196,986,-589,-530,300,-560,365,70,12,-804,762,-525,187,-956,-440,702,-984,619,-907,-981,765,-423,452,625,920,-207,-383,477,-794,-506,-809,-743,368,584,-33,-502,-117,651,275,-245,394,-860,-921,-650,614,990,-849,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-689,224,353,1000,1000,769,-82,1000,840,1000,49,-1000,761,1000,191,-186,-922,1000,642,1000,-166,-911,-1000,1000,-252,-212,934,497,328,-903,-275,115,-1000,1000,1000,539,-232,-38,610,71,71,-946,1000,-1000,-672,609,264,-608,-645,-1000,1000,-638,1000,1000,-1000,-802,-407,-803,-651,-1000,-1000,-1000,-537,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-376,-652,14,-766,-439,-857,66,-524,38,956,-994,807,-905,86,263,22,834,482,681,-447,-128,57,666,-686,-218,717,-790,-626,-873,924,258,-746,463,765,20,-583,722,-596,291,-847,-233,617,619,435,-411,-386,260,-408,-379,-206,-771,-338,-77,-763,516,149,-356,-222,511,-437,-925,287,56,-373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-355,931,109,-738,-301,422,-454,-167,874,1000,-862,286,121,799,291,-447,462,471,782,-108,501,303,795,-516,-382,1000,-1000,-563,-503,412,1000,-697,685,-431,137,-370,581,-349,627,304,636,881,-25,-70,-334,-849,264,-252,-525,415,-661,-791,1000,-757,482,764,1000,-259,438,719,-280,1000,-657,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-871,689,-448,96,1000,-1000,-1000,738,1000,602,217,1000,1000,1000,253,659,-1000,1000,928,249,-505,-706,-255,37,-1000,-276,334,22,18,36,34,969,-806,-1000,781,294,-247,-1000,829,464,-143,962,-352,-1000,-1000,-470,659,-850,-470,-1000,1000,693,466,252,-892,-351,1000,-771,-689,-848,-1000,-346,-1000,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-736,-487,365,-1000,-1000,127,-452,886,284,744,-1000,-1000,68,1000,1000,-204,-808,792,-107,672,1000,215,851,355,235,-170,-115,-483,1000,-1000,-1000,556,421,-782,-1000,-1000,818,501,-1000,1000,926,475,279,635,-1000,-1000,-605,576,-146,621,992,844,-710,-383,-710,-312,-614,-1000,-209,1000,92,-1000,387,684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{696,-27,-377,539,-659,-545,686,1000,-847,-10,-433,-1000,433,682,720,772,-1000,1000,1000,375,70,-83,1000,-1000,530,732,-512,-734,704,-307,888,1000,-168,-518,58,369,-317,185,186,380,476,760,-1000,-1000,646,-151,-1000,56,188,785,423,-909,-676,-126,-1000,-161,391,146,1000,109,352,-1000,-11,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{0,-958,-268,45,-1,56,-609,-730,1000,70,14,439,-48,300,15,-219,-122,-461,190,-837,201,321,-325,-445,771,154,-605,-708,163,-290,368,-380,-182,-416,-813,161,-154,-390,-710,537,728,705,809,373,100,-268,-543,-16,-76,-41,823,-1000,242,1000,-956,-253,-282,-1000,-63,1000,111,1000,764,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{19,-14,-679,-265,-711,-13,1000,-198,-1000,-436,-354,-788,1000,-1000,-416,1000,-429,1000,805,-1000,-1000,559,-412,-633,1000,-516,432,-413,-71,850,1000,826,-740,739,58,371,99,359,241,-431,802,665,-436,-1000,356,-282,-1000,826,644,-506,-648,-868,-298,106,-284,-1000,-111,-257,-462,986,-148,-760,1000,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-398,-1000,-146,-678,228,-1000,-491,-126,-80,1000,328,1000,-1000,1000,513,-1000,433,-1000,-1000,574,-1000,74,-176,1000,-87,1000,-1000,-285,-15,522,801,12,-950,816,-123,1000,-88,-1000,-1000,-430,-998,1000,611,938,-422,330,-1000,-815,39,-120,-68,-329,-601,-136,-170,-264,391,640,1000,949,10,736,391,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-1000,422,-253,-1000,-78,-404,-1000,325,-90,837,58,-310,-765,-24,549,-1000,-757,447,-626,1000,130,1000,-1000,-418,-1000,1000,1000,1000,877,-1000,-1000,835,631,1000,-1000,-1000,-179,-823,-997,1000,29,604,-160,-160,1000,-1000,-359,-1000,-765,533,-811,824,-630,-100,1000,1000,1000,153,111,1000,559,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{878,-451,-427,592,-828,-103,956,392,-1000,-750,-851,-834,676,-49,414,233,-1000,372,766,-170,54,671,512,-545,87,-303,-84,-20,43,1000,1000,894,-851,-44,878,-1000,-439,-8,540,-279,434,806,-1000,-233,-420,-337,591,-18,210,-43,-137,-1000,-421,-748,-466,-388,-42,-90,-7,380,264,-1000,491,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{289,-313,-800,1000,-739,-827,196,-163,-1000,-1000,876,-400,407,-293,-75,925,-164,1000,753,124,-759,-7,762,-1000,945,457,143,180,-78,-316,1000,1000,-377,524,-164,-558,-112,412,-761,-139,-310,342,-915,-1000,392,391,-1000,-740,700,-81,810,-987,759,-883,-508,-159,496,-773,-39,-1000,-164,-1000,695,431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-227,-673,239,8,-1000,-944,-205,84,518,-54,-68,-863,994,-1000,-124,-529,1000,142,-208,160,66,-594,595,-1000,1000,472,-252,727,1000,306,-1000,297,-734,902,1000,-103,-33,92,47,-72,-412,778,510,387,-1,-507,-1000,-1000,1000,-917,-618,1000,-147,-843,894,-1000,-343,-53,-274,-242,-1000,-1000,1000,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-53,894,267,24,-736,-820,-1000,902,1000,-650,849,541,-68,423,-1000,255,614,335,2,-553,-1000,68,1000,-922,1000,-500,968,-74,374,-761,-423,-828,-1000,1000,1000,-264,-1000,70,-1000,-1000,59,-1000,-342,582,-882,-33,1000,-1000,829,-1000,1000,1000,471,561,-1000,-497,700,-1000,-337,401,-1000,-1000,-627,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-287,787,784,254,879,-118,-999,838,349,785,-573,-176,-286,-555,-270,-446,-112,1000,317,-488,327,521,-331,-690,-1000,993,-729,943,-4,62,-737,-666,93,-1000,101,-479,-1000,-106,723,425,13,-49,-77,-25,1000,1000,-414,948,-924,134,-466,372,-52,-36,51,-222,-536,-48,330,-452,-245,-769,-38,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00523() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{530,-1000,-232,400,393,-326,129,-643,1000,139,211,437,571,-182,793,-226,458,-359,-452,-574,1000,608,44,409,1000,-811,-621,443,166,340,-868,-1,275,-725,843,-578,150,-399,1000,997,252,1000,506,179,270,-1000,1000,891,-416,792,-272,566,764,-1000,-651,416,-594,334,705,700,1000,974,-289,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-370,-274,-554,1000,349,-315,660,78,1000,326,-53,-1000,1000,1000,-1000,-1000,-351,971,-967,1000,913,-1000,505,-1000,664,179,-345,-647,-389,323,-304,1000,-234,-1000,725,-686,-796,1000,148,-736,248,21,187,647,337,-867,1000,-1000,720,-411,-236,513,827,-755,1000,-510,-1000,758,311,1000,484,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{514,-1000,-224,-1000,1000,-11,1000,-465,-518,842,542,-389,-1000,968,623,-631,-246,-896,454,239,1000,-441,44,367,-459,-811,-957,727,-67,1000,737,267,275,-600,-436,847,413,-1000,1000,543,-882,1000,1000,179,1000,-266,-1000,891,-263,486,-1000,-1000,-188,-309,676,416,-403,-994,1000,-592,1000,1000,452,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-493,269,-1000,759,-1000,1000,-1000,451,-62,1000,596,-506,1000,446,199,-277,621,252,-566,698,-499,-1000,-70,-254,1000,-323,119,-306,653,988,-349,229,386,-1000,1000,117,-1000,1000,-303,-1000,726,376,-34,753,337,-218,1000,-128,-78,313,-1000,55,1000,-811,-1000,-244,-1000,-126,907,1000,632,-96,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{705,-1000,764,-992,818,-949,539,854,593,1000,661,1000,-859,1000,1000,1000,-103,661,-578,-331,1000,1000,-829,-1000,376,374,882,-327,-1000,1000,-955,-1000,-417,-417,1000,-703,618,-1000,-1000,-1000,1000,936,-544,702,-1000,743,-588,-1000,-564,-186,-281,-1000,-1000,-184,1000,-760,1000,1000,-395,388,412,-242,-1000,-695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-7,-909,-761,-988,72,42,797,-177,635,1000,676,629,-422,1000,-912,628,437,801,245,650,-599,1000,-99,-419,202,338,871,125,-59,422,-224,-1000,-202,51,135,730,-176,-662,341,230,1000,-528,-1000,379,144,-452,-915,143,1000,150,504,-1000,-528,-1000,950,-544,-294,742,-303,56,1000,-674,-485,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{1000,-298,264,-723,1000,-1000,689,504,199,728,-94,-354,304,-294,838,243,-472,241,315,303,-40,1000,287,-96,612,862,-993,-1000,-1000,-299,-458,338,-310,-1000,-639,265,569,-895,-1000,-872,-468,-1000,793,-85,-1000,422,1000,882,1000,-945,-774,-194,-946,735,-81,346,725,257,202,493,987,-145,398,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{1000,638,1000,-486,1000,-692,803,577,973,30,449,-560,787,-752,132,1000,-40,89,69,-350,-719,1000,496,-387,344,377,-511,-1000,-921,-452,-395,898,-1000,-417,-989,811,346,-1000,-1000,-1000,-579,-1000,339,-934,-1000,743,1000,-1000,1000,-1000,-281,906,-256,585,138,463,253,531,27,119,-816,-263,-111,-597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{53,670,58,-766,149,-1000,-228,654,923,546,437,-451,516,1000,-173,60,-653,-244,-1000,462,910,-568,134,-973,313,-554,896,-649,588,748,1000,-1000,-1000,-723,105,-1000,942,-1000,-833,-728,1000,-1000,-869,-1000,-224,-366,1000,-1000,897,-236,-1000,-996,-564,-585,527,230,-56,1000,72,259,-566,-1000,574,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{1000,950,286,-807,1000,-1000,-211,693,371,-359,-361,-773,-1000,-439,149,1000,-424,-133,650,-853,103,366,-773,-869,504,365,-1000,-1000,-1000,-211,253,121,-1000,-779,-266,230,994,-811,-572,-634,25,-1000,-1000,-316,-1000,300,1000,-382,1000,-587,-357,600,-1000,197,-1000,1000,912,30,145,842,-1000,-326,-581,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-660,-649,-400,-633,126,774,-445,-64,346,776,-14,624,218,161,557,135,-218,1000,-1000,-30,188,775,-568,905,69,-230,1000,1000,984,1000,215,-872,1000,131,1000,466,-951,1000,-661,941,351,788,295,-85,959,682,-847,-754,-1000,178,-392,-194,264,50,1000,-1000,-588,527,1000,217,-428,-191,-560,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-216,548,445,1000,1000,233,-527,-208,-339,-445,-750,-45,1000,-630,-134,897,299,-829,-801,431,-907,34,-200,-617,-895,-1000,-278,-363,87,-837,62,172,-1000,487,729,-956,-879,231,-1000,-123,443,355,393,-1000,671,390,233,329,395,612,328,-771,417,-163,-742,1000,-161,-697,1000,24,415,1000,-870,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-1000,855,149,359,906,-1000,585,-888,-888,1000,-765,-534,-144,285,225,-1000,863,-744,-721,-268,-398,-1000,1000,-355,-1000,-1000,860,-1000,270,-1000,291,997,-521,1000,535,1000,276,651,-1000,-1000,330,834,-878,-526,1000,-1000,954,651,1000,-1000,-1000,-701,-353,-746,1000,-191,597,1000,939,-1000,892,1000,1000,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-437,574,-509,587,939,-712,231,-637,649,429,-560,-375,-237,-685,111,394,-552,656,-501,-1000,1000,1000,-461,697,220,1000,-766,-553,601,1000,-619,-648,4,458,-473,-861,-463,-1000,1000,-220,88,-1000,-46,738,-624,116,-1000,-1000,-1000,274,-475,95,804,-336,-324,180,-737,-997,-1000,-200,-416,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-457,305,1000,-861,453,-1000,529,-920,-81,817,504,-81,-286,-200,-713,-232,850,237,-81,-156,1000,740,85,-838,-389,343,-1000,56,-193,282,494,45,231,240,-781,-329,295,-307,-948,573,-875,-446,-408,-95,-183,-68,391,209,690,-387,-1000,-51,578,-464,-139,-580,-614,-217,-228,-341,140,107,72,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-1000,-238,-2,-713,1000,-1000,-527,157,234,-479,-177,-71,-736,-653,-517,209,1000,-49,981,-265,847,34,-192,-952,-895,-1000,-278,-278,805,99,-791,441,-906,-695,-151,-351,-76,-508,400,-123,1000,-472,-470,-263,671,434,-222,321,970,944,-1000,-707,-50,-341,-1000,247,-912,346,963,-430,654,1000,53,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-274,125,-1000,-459,389,262,144,616,404,-732,-1000,-899,440,-3,-1000,159,226,413,857,111,394,890,495,-74,-434,-651,255,-66,451,343,-1000,1000,-796,-753,1000,-254,-285,-124,809,785,1000,-330,-678,-1000,-644,482,-43,-266,133,1000,-475,-1000,132,-599,-1000,205,-991,4,467,501,-28,150,20,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{12,173,-1000,-204,414,1000,174,442,468,-526,-937,676,88,380,-492,-542,-130,-408,127,882,-335,-223,823,931,-43,-212,580,72,75,-470,1000,1000,-521,806,1000,-1000,-939,-198,-475,1000,1000,-359,-748,-580,160,768,954,-243,452,644,-73,-1000,1000,-115,-956,-126,-341,262,208,318,340,445,364,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{185,575,950,132,277,65,-30,-718,-229,306,-358,-1000,-566,432,-551,734,-17,1000,407,-1000,153,1000,-918,-1000,204,560,455,658,632,1000,-797,-860,51,-1000,585,924,906,-508,336,1000,579,47,262,-126,-1000,38,-643,-491,-1000,626,-267,-445,-192,-854,1000,-49,239,-177,-1000,409,-420,-161,175,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{149,551,-208,1000,1000,-152,-283,843,-510,526,452,-90,-831,210,-35,-521,667,439,-807,-217,-1000,-566,-334,-738,640,-432,1000,780,-461,-312,-767,1000,-204,-1000,1000,812,-262,1000,-1000,92,1000,262,-682,-1000,684,499,409,289,437,-1000,908,-187,-390,146,400,-54,873,1000,1000,931,885,369,1000,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-710,-1000,514,-5,-690,383,768,-24,112,-165,631,686,-446,862,-6,727,806,-230,515,52,-73,1000,-833,70,1000,-167,196,878,-648,1000,-1000,-96,25,-9,-998,-848,-601,754,-9,-216,-584,424,952,767,76,663,258,145,46,429,-202,-317,827,1000,1000,-2,98,-495,865,-720,260,-799,-64,-321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-299,-351,-916,1000,863,735,-888,-322,83,1000,-878,362,-81,-902,1000,-598,-1000,-673,-402,1000,394,-715,829,906,-431,370,722,-1000,-110,532,-304,1000,-190,669,645,1000,12,981,720,-318,-10,673,-1000,905,755,-88,-929,1000,-336,102,1000,214,48,-1000,371,-94,-1000,389,1000,466,184,-467,-1000,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-1000,-1000,-300,858,489,-286,-626,1000,400,-129,-289,360,-299,158,238,819,392,-1000,198,282,-1000,1000,-83,622,-497,274,1000,-1000,-407,779,211,388,1000,457,240,189,-89,-40,16,400,-400,531,-959,986,346,-289,241,-256,-34,400,-129,-374,-1000,-1000,-1000,707,8,618,330,-343,-228,-1000,-388,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-1000,-706,-177,903,756,-893,-1000,767,91,792,-1000,-1000,457,-478,789,528,-250,-143,-18,1000,-743,595,-921,-124,-1000,-256,1000,-295,590,1000,-599,1000,146,-208,1000,1000,552,1000,-70,-154,-213,262,-976,1000,85,-1000,-568,314,-664,939,767,313,-389,-830,-1000,315,-656,105,-58,133,912,148,-364,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-668,-1000,240,1000,838,-207,-983,-1000,685,-396,-383,916,-764,-758,112,803,33,-1000,266,-436,-1000,-283,473,1000,-312,821,1000,374,694,100,1000,-97,128,224,-86,442,-501,-921,451,195,-972,409,-1000,-371,1000,-207,359,-673,119,779,227,-419,131,159,143,1000,-1000,-21,-187,300,-861,-1000,-1000,-581}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-214,-1000,-882,699,-56,-638,-312,-880,-666,651,-1,114,432,810,1000,880,-141,-556,964,716,-722,1000,151,409,691,-750,1000,-437,-894,-238,-1000,671,708,697,401,1000,76,-1000,-703,1000,525,827,-415,592,625,-621,-622,1000,-687,-571,-224,936,-395,-228,1000,660,902,-335,960,-231,-682,-940,-291,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{6,-622,-386,1000,785,-25,-141,395,-963,916,-307,-760,112,513,827,1000,-259,-535,747,456,-1000,1000,723,1000,-249,-1000,436,-1000,-960,681,-730,240,1000,1000,1000,-81,1000,861,-1000,-1000,1000,1000,-1000,-780,487,-916,-1000,-204,-1000,-1000,1000,983,835,595,899,1000,418,-895,143,623,-121,-1000,-664,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-534,-878,-635,705,359,407,-971,272,-1000,954,-316,-225,-348,207,1000,1000,-563,-643,-299,1000,172,-170,377,401,-595,-973,684,-984,-1000,1000,-1000,1000,617,460,-473,789,833,1000,-444,-136,482,454,-1000,1000,225,240,-974,809,-1000,-495,1000,-235,329,-870,10,57,-150,-133,1000,-425,-1000,-1000,-672,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{422,-1000,815,1000,1000,-713,-1000,1000,-1000,90,170,-298,-986,630,-1000,-73,1000,1000,569,632,-484,770,906,-749,601,1000,-729,1000,745,-119,-796,1000,207,1000,-1000,663,1000,-410,-627,392,-186,1000,1000,181,989,1000,1000,-625,-968,1000,134,115,433,-871,220,29,-623,-78,-78,-365,-457,549,-937,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-1000,-438,859,485,-459,-1000,-400,-106,-418,514,503,730,-577,-493,-225,118,-60,144,-103,-290,-483,1000,519,226,569,400,-579,-1000,-676,508,-199,-404,387,76,-692,22,-679,-753,-213,-400,-1000,-697,-207,-7,-293,217,350,707,-770,-1000,-835,-1000,-218,-1000,115,-280,1000,-21,-535,-625,-1000,-552,-685,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{927,308,30,1000,1000,-656,-1000,400,-938,-237,419,92,-1000,741,-1000,-70,1000,988,-116,601,-465,1000,901,-698,-399,558,-864,881,222,-1000,-947,1000,168,841,-1000,764,1000,15,-966,318,-430,1000,400,369,392,1000,202,-737,-1000,763,865,-219,3,-870,-146,-280,-1000,245,450,-555,-20,602,-723,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{525,568,-1000,-409,392,-343,-1000,-251,-93,364,-699,1000,-1000,-253,-801,76,-514,283,819,-903,-764,394,-254,894,-1000,829,483,-1000,-664,-217,-409,523,-1000,-273,-1000,-615,-242,-516,167,239,-1000,447,-757,-655,790,1000,-1000,-704,-357,327,1000,-546,-769,284,-859,-948,-1000,1000,618,920,-180,278,-1000,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-1000,-610,271,-111,-772,-713,-182,924,722,57,162,-344,-986,-206,-157,806,1000,533,-383,-564,-536,770,239,114,1000,496,-279,-1000,733,1000,678,-1000,304,-949,-518,181,-1000,290,-627,-1000,787,1000,-805,-1000,-207,-1000,280,1000,22,394,-1000,383,-913,-192,-801,-1000,-623,-247,-579,-169,-1000,-1000,-633,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-1000,-1000,-1000,-1000,-400,-703,500,-1000,-1000,-1000,1000,1000,-1000,1000,-271,1000,-769,1000,1000,-827,-883,733,706,-90,633,-761,-211,-856,-1000,-580,694,-673,-1000,-1000,64,343,443,-1000,-992,-759,-1000,973,-1000,-1000,-1000,777,-1000,-725,-1000,-762,-415,-1000,-1000,-1000,-1000,-1000,987,1000,-634,-652,720,-20,-155,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-168,-175,641,-206,1000,-114,-976,511,-329,-1000,1000,-706,-1000,1000,-149,-152,540,1000,609,704,-626,-164,1000,-1000,-157,591,-598,-1000,-856,-312,-200,180,-834,-426,-174,484,1000,-309,1000,1000,-612,915,690,-206,168,717,780,-136,205,707,-70,-1000,21,-889,383,-904,-390,-227,-55,-578,746,-100,-398,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{614,-712,-106,-894,373,-483,538,-452,-792,-618,1000,443,-761,400,-515,-546,18,630,78,256,948,-491,152,-1000,-799,-684,68,-93,-247,-303,23,-281,1000,-28,-20,741,1000,-264,104,1000,-742,1000,-1000,-20,-425,83,-270,-1000,-800,-737,289,-32,-20,-460,-274,-20,-1000,20,198,-1000,1000,-352,-418,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-744,-283,553,431,-1000,269,788,687,335,910,-778,204,-588,-263,-1000,-34,-898,-152,-552,347,-364,122,-42,-1000,-462,1000,43,145,916,936,336,-867,573,278,423,-776,-566,189,120,381,-284,-313,-1000,848,1000,-502,216,-1000,214,763,296,868,631,1000,122,551,-1000,-740,733,468,-566,-986,-1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-1000,-1000,787,958,-1000,499,1000,-158,-254,66,595,346,509,-64,-1000,116,109,522,-702,104,142,572,793,813,850,-1000,-41,1000,645,1000,881,-1000,1000,667,864,522,-311,-812,-363,-400,-305,401,-1000,369,-145,-523,-631,-254,-343,-1000,-80,-384,-1000,-1000,847,1000,1000,305,-825,483,32,-441,-869,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{365,266,-853,321,-284,-343,299,315,784,554,-1000,142,-609,-253,-192,1000,-331,-886,819,-412,-720,1000,-254,867,-1000,-258,-819,-548,560,-367,407,-409,-384,134,-353,-678,-685,-516,-17,102,-430,-437,-757,-117,1000,547,-1000,-56,-911,327,1000,423,-826,325,86,-948,-482,-50,764,853,-1000,-655,-1000,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-356,-1000,-640,-673,714,-841,-1000,922,-614,-1000,757,720,-988,677,511,1000,-1000,1000,1000,-170,188,-425,-333,650,-421,-1000,682,-1000,-567,-766,389,-848,-1000,-473,-534,-49,835,-139,513,1000,-1000,1000,-1000,-602,-1000,-868,-275,112,-2,-931,-915,-391,-386,498,251,-1,-98,-537,-1000,-903,235,-908,-310,-656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{299,-1000,-1000,-1000,829,-1000,-12,818,-24,298,779,-1000,-739,1000,-439,-467,587,88,-638,947,-381,-119,747,-825,-1000,-1000,1000,-820,-542,1000,1000,44,-1000,-759,331,701,-279,97,-1000,1000,663,1000,505,263,339,375,363,-418,-183,-7,-695,-749,271,-314,-97,-621,-700,-994,-446,-260,-1000,423,-97,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{122,63,-131,604,399,464,501,717,36,-499,-259,145,524,-189,-9,-476,1000,-228,445,-1000,-1000,-411,495,-1000,389,585,870,622,-527,173,-641,-670,-555,-291,1000,-553,785,66,-1000,-656,56,856,-748,156,-490,699,1000,345,-477,-1000,-821,-327,1000,757,1000,598,-84,331,-160,138,-1000,-1000,-544,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-704,-1000,11,-719,-345,-253,-675,402,469,114,1000,-119,1000,963,500,-83,587,604,-1000,270,-1000,144,1000,-414,-1000,-1000,1000,-97,-317,-246,936,-87,-982,-176,689,-573,57,661,-1000,-412,232,1000,637,337,1000,1000,-697,235,-225,1000,-305,-632,693,-130,-492,-671,-496,-768,-132,1000,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-686,68,128,159,-1000,842,-889,-270,-1000,-110,-873,1000,-385,-276,1000,-1000,736,-83,536,-1000,-321,-259,-1000,-709,-80,-41,-976,400,-1000,-1000,11,-107,1000,125,632,-745,1000,-975,-863,-1000,-773,739,1000,-459,550,1000,21,273,-449,-65,-24,-167,1000,367,-156,717,-1000,132,-730,267,-382,-463,481,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{431,710,23,-501,-590,608,204,-1000,-1000,-38,-464,287,-1000,-467,138,-1000,-613,-2,-748,-642,698,740,646,224,1000,276,-976,-170,193,391,650,337,969,-597,-501,645,987,-983,11,-1000,-1000,-882,-814,848,-894,-1000,-700,-586,342,-922,925,-167,-178,1000,-449,-451,-1000,-1000,-805,-951,-459,-463,370,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-6,-580,46,-858,137,254,253,-275,-1000,-283,697,266,-627,514,336,519,-34,-841,122,-138,-57,1000,1000,-171,1000,-395,-342,671,1000,441,-290,-258,-687,-201,113,-80,584,822,-254,-502,-1000,105,-1000,-1000,-344,330,636,393,189,1,129,135,-199,551,144,-1000,-1000,132,-287,-833,-1000,-1000,-305,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{112,-118,-983,-1000,-966,258,54,-349,-321,335,-569,-431,-692,-18,835,-1000,840,1000,-224,-492,-356,985,-396,-797,-491,-636,956,-1000,-956,703,1000,647,845,-1000,-362,-180,755,-1000,-1000,1000,409,424,886,1000,19,-400,1000,-1000,-680,-775,-826,-761,-1000,1000,-1000,46,-146,-349,-1000,153,-854,1000,887,-497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{826,-1000,-546,968,46,1000,37,58,668,-65,-388,1000,992,287,-305,-262,-803,-1000,-814,-645,777,-1000,132,133,-486,1000,38,-509,-399,814,-1000,-95,172,215,489,971,399,-242,-1000,-1000,-716,-128,-210,-96,-26,1000,183,-428,-907,-886,618,1000,857,-842,126,248,839,380,-648,-1000,529,-728,-1000,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{1000,-124,-1000,-154,-367,-447,379,67,-360,-508,-744,-941,10,-203,-618,-1000,-1000,-287,-1000,472,400,588,1000,61,1000,-354,-400,-933,-564,1000,1000,508,402,-1000,-522,1000,480,-1000,-407,-87,-43,-282,932,1000,-1000,472,-1000,-959,130,-1000,347,-546,-577,1000,-430,54,-944,-1000,-928,-1000,358,-581,-105,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-615,-1000,-983,790,-966,187,54,631,-99,335,-581,921,-515,-18,-287,-651,-42,-1000,-586,-492,281,-1000,79,-1000,439,370,1000,1000,31,703,-412,76,84,198,359,244,1000,-540,-1000,-751,-1000,1000,481,485,-387,1000,1000,751,-57,-821,69,410,1000,95,115,768,127,162,-650,-823,-581,-611,-1000,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{1000,653,234,-1000,1000,-1000,82,-708,-47,-691,-941,-1000,1000,1000,592,-83,-530,-52,-1000,-818,1000,1000,1000,1000,-233,-479,-1000,-1000,-859,1000,1000,198,1000,-1000,-936,224,98,70,250,-396,409,-1000,998,337,-127,-1000,-1000,-1000,471,-898,1000,1000,-1000,574,-585,-956,-209,-1000,-597,-1000,1000,-834,944,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{716,880,-457,-217,254,443,-627,-1000,-1000,-998,-468,-1000,247,530,599,-497,-1000,-1000,-779,-1000,1000,161,1000,514,1000,242,-1000,70,-118,655,-24,-1000,814,-413,317,948,1000,-931,-1000,-765,-1000,-1000,1000,-314,-867,-1000,415,-168,291,-1000,677,1000,330,557,1000,-389,-645,-430,-786,-1000,693,-1000,94,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-18,-1000,628,-721,-1000,769,-481,101,-160,-211,1000,1000,1000,889,288,814,1000,-45,-708,-34,-1000,-797,1000,-1000,-156,210,1000,1000,1000,-1000,148,-737,-1000,652,1000,-1000,488,1000,-733,-723,-711,1000,-72,-900,1000,546,1000,1000,826,1000,-1000,-276,1000,52,-564,-845,464,883,-247,731,-76,345,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-126,-379,491,-540,-1000,-411,228,1000,-1000,172,791,731,-20,608,-1000,-1000,-139,-871,63,74,-408,663,-1000,-515,-166,-964,-990,729,-276,0,-113,-74,-224,-687,-558,-1000,739,86,189,-592,90,472,450,324,-88,817,1000,496,-139,916,338,-99,-376,82,-1000,659,-1000,389,693,-1,-282,429,1000,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{71,-563,-773,186,134,-867,-960,78,357,326,-24,-903,129,-13,-940,-680,349,580,-913,519,-878,-849,-862,-531,957,-183,-82,-436,-93,-205,-10,-371,-933,506,-395,-751,583,-892,215,-450,-77,-104,-4,-562,-479,-623,758,-470,120,-417,-538,-305,-867,-826,226,-991,193,-751,196,-792,652,414,934,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-387,828,119,-609,1000,-807,1000,537,333,180,-242,-614,71,253,-159,338,414,-55,-6,574,1000,-622,377,-50,125,120,-600,224,179,-604,-93,735,-549,-366,222,367,295,944,-76,619,395,534,-701,-547,-341,471,-366,-18,866,84,929,226,-94,-290,452,-1000,-66,520,1000,373,-380,107,-183,777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{1000,-1000,1000,-1000,-1000,1000,-191,-608,-61,973,166,-493,774,11,-547,50,-516,-502,1000,169,-1000,341,-76,1000,168,-1000,-433,-555,-967,-583,614,1000,1000,486,-48,405,1000,-1000,537,1000,236,-1000,-690,1000,1000,846,1000,-918,-932,1000,358,820,-321,-1000,-1000,1000,-312,-275,1000,-665,1000,58,-463,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{884,-1000,205,-1000,-1000,1000,-736,333,537,1000,-1000,1000,320,-405,-436,271,-586,-724,-1000,1000,-1000,-678,-1000,370,-1000,-407,-1000,-1000,468,741,1000,323,1000,999,1000,1000,1000,-549,-205,108,-796,-288,530,673,1000,298,501,154,1000,1000,-58,338,-78,-1000,-1000,127,-545,-413,789,960,1000,-986,-761,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-648,-453,34,-218,-974,-731,-736,1000,-1000,-827,-195,-101,-752,1000,-436,-541,-35,-671,790,109,468,111,267,570,-571,-91,-738,1000,-433,741,422,-459,-532,-931,-75,-314,489,-549,161,-497,774,521,-213,-91,-463,955,-221,180,8,1000,511,-312,123,578,-506,4,-754,1000,748,835,-611,-204,344,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{1000,-1000,544,-1000,-1000,974,-257,-1000,-354,1000,793,1000,1000,-33,-193,271,633,-891,835,1000,-1000,-338,-797,-284,806,319,-433,566,-887,517,1,-832,1000,-93,-463,190,1000,-831,-252,1000,-750,-62,716,1000,1000,802,1000,1000,67,1000,548,620,-1000,-1000,-911,795,-451,-881,830,-1000,1000,943,104,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{546,353,323,66,-469,-82,80,431,-281,-600,149,-633,1000,214,-551,-295,161,458,-69,625,63,-81,172,-1000,-251,-611,-125,-929,134,613,1000,-243,939,843,1000,549,569,-311,292,-79,678,-855,373,-338,747,717,-438,-877,242,-222,-225,617,-1000,-80,630,-251,304,797,882,-32,-787,522,465,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-810,-671,702,-718,-470,-943,-374,762,-965,774,-822,1000,368,-83,883,656,550,604,-267,90,-1000,362,-1000,764,-990,414,52,-14,1000,317,189,344,276,-718,1000,870,106,-201,-774,294,-1000,472,-1000,142,1000,-265,-1000,658,-1000,462,-945,-910,502,1000,863,134,-225,336,809,-276,1000,875,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00585() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{400,-475,-31,-28,-295,-281,-846,254,872,-1000,-1000,539,-297,-634,-580,98,-130,590,-34,453,-588,-1000,-332,-1000,-683,-578,-216,224,922,973,161,-183,1000,-328,432,118,1000,-1000,-434,-822,1000,610,482,-1000,-454,-357,314,-1000,378,-884,665,1000,-323,91,563,494,724,36,133,491,-410,3,1000,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00586() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,137,76,761,-522,-104,-931,-783,1000,-803,991,-1000,865,573,-674,-208,350,632,-246,959,525,-584,366,-746,203,-520,-47,-938,-239,1000,564,704,184,431,666,165,20,22,448,-1000,465,-775,472,-447,459,1000,-906,-992,105,-721,462,-1000,-941,253,721,-606,121,209,20,-33,-431,548,638,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00587() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{37,334,589,1000,-991,766,-969,-711,872,337,1000,-830,-1000,1000,914,44,879,769,524,-766,1000,-63,-332,1000,-683,246,-907,-107,-145,-706,516,515,-1000,167,432,-1000,-983,-1000,657,-822,-331,610,88,830,-1000,-357,-1000,803,-655,650,699,-1000,-959,-767,-765,-1000,-78,-609,-1000,-460,-751,140,-319,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{588,-598,761,311,-180,867,162,-974,-555,204,-296,-573,-709,-348,-425,-751,33,647,181,-1000,1000,542,-462,853,216,-639,461,-902,-109,-332,775,855,-1000,456,978,-84,-913,205,-311,-509,75,460,19,1000,668,720,-69,617,50,-345,-61,1000,-407,-773,95,-852,-741,-949,792,42,26,-485,300,-784}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{212,192,99,324,-944,562,1000,-247,-102,-817,-424,-757,32,1000,37,-213,949,865,-783,-176,350,294,-191,-380,-1000,-711,-496,-603,-1000,455,-396,1000,1000,1000,941,-615,1000,510,1000,-880,768,-547,1000,22,591,1000,-680,-797,278,828,-700,-974,-796,-72,-667,-605,932,1000,807,-446,-1000,551,65,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00590() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-536,-1000,-720,144,-687,544,-701,-61,603,-323,897,-626,378,1000,-518,880,1000,514,224,-299,54,-506,-400,-163,67,-288,-34,446,-837,352,-78,1000,472,711,-256,-26,698,960,402,-712,-669,119,1000,-735,-275,1000,-588,-548,202,650,-519,-132,-801,84,927,-693,872,589,-15,-158,-625,223,760,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-502,992,1000,165,208,-829,-871,1000,684,-119,462,169,-325,372,137,-637,-1000,-1000,-184,66,380,-858,-297,587,-108,480,-859,963,506,-28,-295,-433,398,619,656,26,-659,-1000,692,-460,-411,-887,-952,290,156,-232,-1000,-354,458,261,-38,1000,182,-1000,183,441,-1000,965,-791,1000,249,156,-188,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-1000,-345,-906,18,-1000,-262,-80,-2,176,-354,261,261,-248,-130,1000,45,-221,1000,-688,-108,620,-119,660,1000,-873,-6,-341,-148,1000,505,542,-864,422,-961,1000,623,223,-439,376,170,-229,-255,-400,-935,-1000,858,1000,-69,792,819,426,-242,423,36,145,312,236,966,-849,198,-297,-1000,-1000,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-146,-65,-562,-312,-376,10,219,800,-253,107,376,324,-1000,-503,984,738,1000,1000,143,-1000,316,-766,1000,583,-1000,-360,948,111,120,-48,1000,130,-150,-696,90,665,151,571,-389,1000,1000,-563,766,-1000,-468,86,100,721,956,1000,1000,393,1000,-486,696,-1000,1000,-516,-266,-129,-422,-1000,-1000,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00594() {
        org.junit.Assert.assertEquals("java.lang.String:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-872,-361,848,827,560,647,-673,692,-985,-186,1000,-592,1000,693,-143,-959,-508,389,-122,2,380,-358,-149,213,302,-324,-859,170,307,-28,964,950,-362,827,-629,449,-391,-899,360,322,207,-1000,1000,1000,956,-1000,1000,353,506,130,796,403,1000,-456,-143,-569,-466,1000,-70,1000,249,-76,217,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-1000,-1000,1000,-911,-1000,81,-801,729,209,190,495,267,511,-588,1000,469,310,1000,143,336,-1000,1000,1000,-976,-419,132,946,-441,1000,1000,1000,-297,-460,-877,594,977,-62,982,-240,281,502,-877,1000,-810,-873,352,21,278,687,897,1000,370,1000,27,754,-1000,22,557,-401,132,-77,-1000,-1000,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-1000,653,1000,275,270,-607,-367,926,-499,306,1000,-201,-293,774,-21,-85,277,120,621,343,348,-1000,1000,392,-419,132,-911,81,1000,-30,1000,945,-21,1000,-157,558,-1000,-1000,331,940,606,11,-905,-236,785,-940,-409,718,1000,642,423,56,448,27,754,-644,22,870,-572,512,585,-502,51,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{727,947,472,-18,520,242,-582,942,654,-570,-363,-278,-922,-583,-643,-606,168,-780,-461,-437,-18,652,-615,779,-160,749,25,341,-603,-855,-720,-654,29,213,-316,-626,802,788,58,-900,543,-466,-145,273,609,-367,-799,-978,-159,344,117,238,-128,-780,-251,-83,-507,-718,613,755,-936,288,-176,64}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{1000,755,-639,-610,889,429,513,821,32,33,-227,-59,-962,-1000,-738,-453,1000,329,303,-872,30,630,502,-344,-754,91,248,-504,1000,-1000,-475,-218,1000,-3,-332,-797,650,1000,-283,200,725,-590,9,-705,-171,660,-427,-767,492,942,-275,-1000,752,-560,159,-339,1000,1000,186,135,-1000,-128,-421,311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzg=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{69,-148,-173,1000,-1000,-147,386,-968,-466,320,637,381,-458,244,-703,-1000,442,-1000,-308,1000,1000,-1000,-828,502,-985,1000,-398,-528,43,478,1000,-1000,-1000,-936,-298,-849,-1000,767,-559,1000,-1000,215,-1000,1000,-1000,-276,304,24,-1000,955,-901,485,1000,-1000,-400,1000,254,561,489,41,-145,955,574,323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{-569,-858,-482,-528,280,933,-756,-509,-586,153,-47,362,399,-618,373,681,451,1000,244,189,-235,455,1000,-80,1000,-1000,-730,219,1000,1000,1000,264,-819,-452,1000,-542,1000,-147,394,951,-1000,-606,-164,444,462,-910,-1000,165,1000,844,-319,-532,663,-120,-257,-627,-509,-1000,699,-229,-492,-102,-790,-545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{-1000,-670,-1000,249,-540,949,204,-763,-875,-311,1000,389,637,-1000,-596,-153,23,1000,-463,1000,619,-209,1000,49,308,-863,-935,-757,1000,654,1000,-329,-959,-764,1000,1000,1000,363,947,1000,-77,-1000,-414,1000,-538,-1000,-677,215,1000,929,-1000,25,1000,-238,-1000,396,-871,-1000,1000,-948,-120,968,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{-538,-370,-288,-252,801,638,-933,606,-527,31,-793,-33,-852,-39,253,930,310,1000,102,-202,-1000,1000,1000,-370,661,-770,103,1000,298,1000,1000,996,298,857,1000,-523,1000,-339,924,595,416,-453,177,-261,210,465,-242,394,391,76,-75,-367,319,655,1000,-711,-379,-373,217,255,-1000,151,-447,11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{-973,-815,532,-296,11,-876,-960,-396,119,419,269,153,-613,125,-464,638,-555,513,1000,696,-99,-149,-387,192,-74,-237,-1000,-248,-580,835,292,-142,-577,616,-840,92,1000,460,-228,772,582,361,687,408,69,-562,-154,853,391,-675,-53,-111,524,-132,-400,689,-225,-377,165,50,-889,-464,158,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{444,-832,161,414,-530,214,-347,-871,-567,-293,168,296,-1000,-777,-58,439,373,1000,1000,637,20,-544,294,503,412,-457,-860,-647,218,1000,-11,-226,-952,-293,563,-1000,-369,16,-1000,389,-895,-659,-1000,-45,-1000,-225,-648,-477,-46,-6,-555,256,827,-431,-867,739,-667,-911,154,491,-859,565,-321,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00605() {
        org.junit.Assert.assertEquals("java.lang.String:dkM2AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-621,260,61,-192,-286,903,-182,-246,1000,687,61,-616,-494,-797,-263,1000,71,778,-855,170,-544,-1000,980,284,142,-505,972,-252,904,-192,3,154,997,-382,438,-1000,1000,1,-87,-68,-257,-617,-132,-616,404,626,209,-777,-144,1000,171,650,50,-1000,-1000,-70,465,188,-1000,126,72,1000,-101,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{1000,42,794,423,993,-882,-246,122,-470,333,-276,527,-182,653,-661,-240,-287,481,631,-914,-1000,487,-1000,-530,230,-26,136,459,552,696,-569,-875,572,318,624,-102,-737,-562,1000,1000,-30,128,342,91,1000,907,43,875,-503,777,242,-100,-345,435,349,-495,-483,-267,313,527,-50,853,-602,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{279,128,-162,57,-23,145,-1000,990,-386,35,407,-229,302,289,1000,-140,441,-222,-401,576,-634,-1000,1000,404,-85,-1000,64,-414,145,-602,-198,22,-3,-377,1000,-858,-67,628,468,-189,419,-1000,-14,-141,-20,-1000,172,-579,-1000,777,201,-540,56,-157,649,1000,387,75,-1000,-292,-682,523,-609,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("java.lang.String:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{1000,-70,1000,711,1000,-1000,-7,255,-243,57,-494,324,529,243,-334,-378,-363,90,793,-821,-548,460,-204,-1000,-861,-112,-617,-1000,1000,-1000,-1000,309,90,246,1000,642,-437,-448,148,643,99,50,-158,-295,-624,-602,-1000,1000,-1000,-110,-551,106,-1000,822,347,10,-1000,-1000,1000,-381,326,178,-1000,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-700,-266,310,1000,-286,-884,846,-831,444,-997,-1000,243,426,618,-398,-325,-588,1000,-448,-930,-1000,-1000,1000,638,736,-288,1000,-1000,-243,829,-1000,-1000,46,-720,1000,-1000,-334,-1000,-346,-406,-976,621,-355,347,280,1000,98,583,-1000,392,-522,988,-1000,-1000,-404,-538,-928,-295,1000,-483,508,137,-484,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-590,77,685,-79,790,-41,-399,-686,322,838,447,195,-682,128,559,324,106,231,704,214,-348,-466,-176,499,-662,-897,-206,-271,416,-70,-599,595,624,-232,137,95,-241,356,1000,1000,349,-492,281,-629,667,-1000,76,-634,130,236,60,-164,107,614,-281,-185,-567,-259,-631,597,1000,1000,-778,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00611() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{290,104,-849,-1000,-590,250,163,-152,-399,581,607,-793,-546,603,181,-286,747,767,545,563,-36,201,-420,-539,471,-223,1000,714,937,402,409,-1000,894,252,119,-62,-241,-155,861,-186,459,-175,695,102,1000,-1000,1000,-505,195,-486,60,223,926,-386,651,-809,951,1000,-435,450,-611,897,-42,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{1000,-43,-208,1000,563,-732,186,-539,-105,-288,-1000,892,-532,455,144,-211,-1000,278,415,-187,-941,317,787,-266,-419,-174,-283,-1000,249,-497,-998,582,-555,-382,1000,-380,673,-214,495,1000,-44,-919,17,-1000,-1000,152,-845,186,-1000,-972,-169,254,-668,-157,-252,1000,-33,-73,821,336,-897,223,-607,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-460,-700,377,902,-39,434,-131,257,1000,418,-719,-597,-689,530,-183,523,-377,219,406,-102,335,249,-423,976,-1,-923,-591,-267,139,-755,378,717,822,379,1000,340,-983,-506,-740,-282,316,-331,-444,-635,-107,1000,-428,-141,277,55,834,829,-940,-1000,579,453,1000,441,846,983,-434,-199,-478,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-918,-657,979,1000,-473,-998,-204,166,-1000,1000,-871,-363,-1000,-586,1000,-1000,-1000,-652,1000,-244,1000,592,1000,-75,-1000,746,1000,695,1000,1000,1000,-1000,268,1000,966,1000,-1000,-71,-894,-1000,195,189,1000,-873,-1000,-848,253,-49,1000,-817,-666,-898,-740,-1000,296,-995,1000,1000,862,1000,41,748,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-938,-284,518,564,-241,-626,952,454,-424,1000,-296,903,-364,-516,949,-911,-1000,471,476,-671,697,-73,204,693,-803,293,402,305,579,826,432,557,111,-632,137,854,-1000,645,-633,-1000,-950,380,-358,-1000,-564,73,-473,201,1000,-929,1000,-1000,463,-923,-861,-582,1000,534,953,796,-642,1000,-618,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{812,-970,464,505,-761,464,-1000,-385,-187,249,395,-659,-723,404,0,-789,-530,-440,961,-752,413,219,734,-1000,-567,485,1000,160,969,1000,-154,-354,146,203,186,791,176,-112,-561,-425,-823,205,-390,408,-478,-67,558,-28,692,355,-741,-1000,-51,-39,1000,238,-427,-160,-1000,-375,280,-791,-84,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-815,23,-610,821,-284,1000,-782,259,157,380,287,-1000,552,1000,874,-708,-1000,-917,819,-542,945,-1000,432,-196,-723,-110,-185,-459,-911,302,470,-1000,435,-635,223,495,21,611,725,-480,-1000,1000,541,721,785,-1000,-611,205,-52,-121,1000,-529,-96,31,1000,-1000,-527,-589,-259,-1000,-159,-419,281,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,377,-49,511,-1000,-243,-445,127,409,-62,-1000,69,429,1000,-957,-1000,636,-135,-1000,1000,-530,-1000,29,-478,410,602,-819,-269,1000,1000,-1000,-149,-1000,-402,806,-111,1000,1000,-511,163,1000,-444,-1000,320,-1000,-1000,-141,206,-1000,465,237,373,-977,1000,453,527,-268,601,-370,237,656,-1000,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{608,-721,35,725,-11,43,-844,811,-573,491,272,901,-942,615,532,-930,690,-731,-316,-625,412,707,-249,-669,827,267,-131,-675,809,-196,73,930,-356,489,494,3,-698,-32,-121,695,-837,473,-332,65,9,482,946,-163,328,781,-492,58,63,372,-979,868,816,570,-241,-564,-783,851,818,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{531,481,-481,46,-996,517,258,299,572,662,-1000,-376,317,-412,315,-1000,-191,-871,-270,217,1000,789,192,-1000,-243,407,-1000,-977,-418,-1,185,-1000,220,-1000,-745,-546,-71,977,-158,1000,-181,-1000,397,-695,215,604,1000,1000,504,66,938,-1000,-656,583,658,1000,-268,389,806,-625,-410,-1000,346,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-50,798,19,-224,-435,232,-943,7,211,-68,1000,74,-360,163,1000,294,49,-598,191,179,72,603,-151,-426,413,699,73,519,80,18,-497,534,-386,-1000,549,1000,-274,-35,-533,-969,-928,-191,-508,-179,1000,331,-116,-424,362,488,-410,-313,-306,300,-54,887,-583,638,274,1000,1000,345,542,266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,1000,-1000,931,-336,-1000,1000,-204,-328,-545,-416,-1000,1000,1000,-221,1000,-475,854,1000,64,451,16,-1000,1000,1000,-1000,271,-67,1000,-736,187,-1000,36,1000,1000,-105,-553,642,1000,-1000,-1000,-1000,-1000,1000,702,1000,-1000,1000,1000,495,-1000,-50,1000,-184,1000,879,-89,-890,-30,317,1000,-628,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{129,-1000,321,403,-749,-446,-730,892,0,695,-492,1000,-877,1000,275,294,863,-1000,-308,196,-229,681,-687,-742,203,492,-784,-663,851,-43,-569,534,-1000,162,618,471,-958,682,353,377,-928,819,-508,594,-634,331,876,73,-602,1000,-410,127,-186,92,-144,887,778,890,617,-779,-1000,-832,850,-301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-246,-1000,-400,1000,-400,-242,-293,954,-941,194,944,399,-1000,1000,-59,620,673,93,-1000,-585,-511,612,-860,303,-6,-56,513,-199,970,-332,-129,1000,30,325,157,999,348,32,669,-400,-355,1000,758,690,-1000,199,398,-972,-1000,467,390,718,695,-3,-842,596,634,-212,-242,65,95,-577,719,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{77,611,355,113,-578,-596,617,-796,1000,531,958,901,-593,355,235,787,-384,25,-316,359,-775,707,-1000,568,827,-1000,527,-675,-423,-849,-991,1000,-217,441,494,-770,630,40,280,-1000,1000,1000,143,-71,150,-415,-811,-780,-743,-1000,-1000,1000,1000,529,-353,-124,816,-189,-370,135,-461,317,818,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-562,-834,-1000,578,-41,-462,866,919,82,1000,670,64,957,60,1000,12,-1000,1000,-641,1000,95,13,1000,1000,975,1000,-435,-721,1000,173,912,-340,-9,-741,168,630,-679,130,60,-941,-1000,392,132,-1000,-748,-193,-1000,164,531,1000,-537,-1000,-388,555,453,-1000,-1000,1000,604,962,1000,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-813,-101,-754,-1000,318,-37,-83,1000,760,457,970,828,-425,1000,-564,850,226,-18,430,-4,1000,-1000,669,1000,1000,803,907,-69,-721,1000,173,1000,-340,-741,-482,-586,95,-853,1000,258,270,-1000,841,235,-1000,-177,-121,-241,-518,1000,-21,-357,1000,-388,346,288,-296,-602,117,617,400,1000,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-101,-1000,-458,-672,-1000,1000,403,-324,1000,-322,-414,476,588,-1000,449,-989,68,-182,782,1000,-4,684,-640,951,-422,641,-579,1000,1000,-858,1000,866,396,44,460,-496,-293,-1000,-853,-1000,314,-1000,-208,914,-1000,1000,513,-776,-1000,280,1000,74,688,383,326,-1000,40,464,-394,619,710,-1000,-1000,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-637,-1000,27,-1000,1000,-37,114,527,-202,82,1000,364,400,274,-332,1000,423,56,-235,-997,1000,-1000,-250,1000,1000,975,723,169,776,1000,-779,1000,-964,-1000,493,-1000,-1000,547,1000,-554,-862,-605,373,-800,-230,251,-238,82,-1000,1000,-91,-44,1000,893,220,1000,1000,-394,84,368,300,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{563,542,-99,-644,426,1000,198,110,987,375,361,1000,1000,-533,1000,722,-1000,654,773,275,859,373,-109,-710,-431,1000,1000,-166,776,1000,-74,1000,-585,1000,243,-1000,-1000,-773,-616,-853,443,-1000,614,962,149,1000,144,-452,-198,247,312,-1000,-503,20,-486,856,-518,-305,-1000,-534,-49,-11,-619,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00631() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,162,-658,-1000,284,-258,-150,907,1000,82,1000,670,572,957,-289,1000,119,-1000,284,1000,1000,45,297,1000,1000,975,1000,-1000,-1000,1000,-73,1000,-359,-577,-741,244,630,-151,569,217,26,-1000,535,289,-1000,-748,44,-1000,-51,772,-893,-733,-1000,-529,923,990,-1000,-614,1000,286,1000,1000,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-320,-658,-181,579,-29,1000,643,1000,82,1000,1000,580,957,1000,-649,119,-1000,92,1000,1000,45,-553,896,1000,1000,1000,-796,-598,-1000,1000,798,400,-798,-1000,-145,565,-151,715,6,67,-1000,535,24,1000,-1000,44,-346,1000,-226,-517,-818,-1000,-839,1000,-253,-1000,-140,1000,290,1000,-1000,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-668,-1000,354,-606,1000,-193,279,-313,-375,-1000,1000,-1000,1000,780,-1000,1000,438,-729,-518,-418,868,601,1000,459,1000,28,647,-518,1000,1000,-173,450,-1000,533,574,-822,-1000,1000,698,331,-1000,52,-26,-1000,610,-109,-156,-322,-1000,1000,-995,-231,-119,852,743,1000,1000,-749,-571,1000,857,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,1000,-1000,-777,190,69,-946,-687,-1000,-220,1000,-1000,-413,698,274,97,-975,1000,277,-1000,-225,-736,695,-457,307,1000,-290,101,-97,489,-495,845,-1000,-715,386,166,-790,-246,-428,204,-285,1000,587,546,-682,649,1000,444,71,-1000,-667,715,237,492,-534,511,1000,362,1000,-446,-196,600,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{600,-336,904,411,-1000,-545,-403,909,947,-620,537,-533,757,-236,486,547,-370,11,-873,219,34,-881,411,1000,-208,-703,987,-969,683,95,-61,816,-562,128,-110,-117,-167,593,-117,-1000,-769,-209,-856,177,1000,-170,899,918,61,1000,467,325,-527,13,554,-41,-194,-1000,108,-339,4,-652,417,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-524,-1000,268,-167,925,-1000,708,97,795,-905,-1000,-286,-16,1000,920,76,-109,-1000,-674,152,-1000,-777,-1000,-857,1000,-191,173,-722,457,-1000,-1000,496,783,-1000,-416,-927,-348,257,79,-936,882,586,1000,1000,928,-443,542,-887,-635,305,-448,-378,-676,901,1000,-578,-26,-556,74,-591,-557,-1000,77,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{800,-52,658,-1000,-577,-551,207,442,467,-1000,-743,-523,-1000,23,1000,219,-119,-1000,1000,1000,-1000,-300,-1000,-705,574,84,737,-381,505,-1000,2,-81,1000,-411,-747,-529,724,-1000,-1000,-874,-98,-455,1000,1000,733,-682,412,944,-58,305,-1000,-811,997,391,1000,-1000,-1000,1000,-500,1000,-446,-890,593,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-341,-1000,-262,-902,-999,-794,-982,-994,707,785,143,477,862,-77,-109,66,-59,994,-1000,-1000,-1000,-922,-1000,-1000,-314,776,-610,-586,650,-1000,-8,1000,-1000,824,1000,-916,-41,87,709,-1000,-547,1000,654,813,538,1000,920,1000,-24,-1000,759,4,932,178,-78,-53,-1000,-214,1000,-1000,-274,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{866,-1000,-1000,-52,1000,-1000,-404,-564,94,169,1000,1000,29,501,164,-65,1000,-889,-49,-242,-1000,-886,-1000,-1000,515,-183,1000,-29,-434,-418,-505,502,1000,-1000,110,864,-320,-1000,-246,5,-416,-306,1000,1000,442,1000,919,810,1000,475,-1000,336,1000,1000,283,-1000,507,-415,240,1000,-1000,-478,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{388,-52,-400,-10,-851,293,-192,795,1000,-44,-743,-976,1000,-11,-142,-443,801,945,-1000,-111,890,-686,815,-187,-14,-255,737,-948,-245,158,-42,46,-891,983,-747,-315,-352,43,173,92,157,133,-1000,-711,-719,177,147,-133,30,609,-720,157,-253,-466,-561,674,-658,-532,660,-591,217,437,-120,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-464,189,-166,-817,366,1000,-698,-199,561,-1000,-874,-360,-577,-1000,-1000,1000,-506,1000,1000,-1000,-1000,-74,-1000,-1000,671,1000,-917,-12,13,270,542,1000,1000,1000,-559,1000,1000,763,1000,-1000,-1000,1000,-541,1000,-401,391,484,-914,-729,-438,1000,-1000,-714,413,20,1000,-394,839,-1000,332,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00642() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,78,3,702,149,-734,-466,-1000,236,-1000,378,912,1000,1000,773,104,-473,-1000,1000,-504,605,1000,-1000,-1000,-556,-69,1000,160,504,-1000,-1000,1000,-1000,516,456,775,1000,-1000,-696,254,-84,226,-1000,-649,-254,-143,1000,160,-400,1000,-297,-512,-102,-192,-1000,-714,-197,304,106,370,1000,-1000,81,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00643() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-764,-1000,-403,1000,-876,-348,-817,366,-317,-978,-736,460,98,1000,1000,-1000,359,997,-142,-619,-494,187,158,-715,-1000,-1000,-1000,225,-663,388,-12,13,26,387,-239,1000,1000,108,-427,-1000,-98,220,1000,-543,1000,80,511,263,391,312,-1000,1000,-195,-93,367,79,244,933,331,307,339,-918,-302,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00644() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{400,-14,851,57,189,487,400,424,1000,-198,-1000,463,-538,789,-450,-1000,443,-15,-287,-528,82,732,-415,400,-593,-474,1000,1000,-433,-191,-934,921,-1000,944,-152,19,341,841,920,988,292,1000,165,-255,804,108,-352,-687,-14,-518,-94,-349,-1000,23,-161,-714,55,1000,229,-228,534,-743,423,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00645() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{642,-476,-449,-27,224,132,817,-746,113,-341,-1000,-898,206,236,-579,-1000,-130,-586,477,-790,-1000,1000,245,680,-995,-112,-248,-798,-1000,415,422,-510,-841,-964,-1000,388,140,931,271,973,-1000,-296,-42,1000,-447,-671,-941,-17,1000,815,-847,489,-1000,-812,1000,892,420,690,-1000,-1000,299,146,932,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00646() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{217,-1000,-1000,878,1000,-771,614,156,-90,-552,-1000,1000,1000,970,-48,-1000,436,15,406,-624,222,954,-49,-9,-1000,215,-586,-72,-640,-381,-565,-46,1000,-210,376,148,400,399,-570,-1000,-1000,201,-228,1000,-327,-622,130,413,-1000,464,-1000,168,-684,1000,929,1000,512,1000,-1000,1000,1000,-1000,-87,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00647() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-457,-16,-228,-262,-775,-1000,-41,-442,1000,-653,1000,654,-1000,1000,-913,-1,793,-103,827,-34,-1000,64,-386,190,-106,549,612,-664,-1000,-65,-966,-7,-1000,-1000,-742,-362,-687,41,690,1000,-96,1000,155,-69,-327,572,-728,-593,1000,811,790,171,-440,-30,510,-761,494,267,174,-1000,412,629,1000,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00648() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-1000,-628,990,675,-769,711,549,-1000,-1000,-1000,847,1000,381,-1000,-153,-7,-1000,-1000,298,743,1000,441,-850,1000,-293,-662,186,-1000,-160,523,1000,467,709,662,1000,-66,260,-926,-1000,-169,-990,207,-380,-495,-973,1000,1000,-836,-1000,-202,293,-1000,1000,1000,1000,1000,371,458,1000,-1000,-553,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00649() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-1000,-1000,1000,1000,925,-1000,150,1000,-1000,-783,1000,-330,-1000,-19,231,-733,-466,-907,-163,1000,-216,631,-171,390,151,-323,-532,1000,1000,-163,-898,-577,943,-911,230,1000,-402,-1000,-1000,-421,563,949,986,-784,-504,-86,-1000,-407,289,963,-629,-835,586,466,1000,1000,-1000,46,1000,-1000,-292,651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00650() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-53,-1000,596,252,505,-153,532,-1000,-575,608,-689,-1000,642,410,-1000,-209,-548,283,37,-883,-629,770,1000,415,-746,-61,134,-189,272,893,399,-301,56,743,-1000,825,657,-559,-297,358,-234,-1000,-999,578,-533,-1000,-915,251,1000,869,-1000,946,-490,-336,-1000,1000,110,337,-279,-566,123,-1000,824,-467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00651() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,14,-1000,282,-538,-1000,1000,-595,-1000,252,671,-129,740,1000,-885,-20,456,-591,-884,1000,522,-1000,749,-448,-898,-1000,1000,-503,-1000,1000,1000,1000,1000,305,-1000,1000,-1000,-1000,-1000,731,39,-827,-1000,424,-252,-1000,483,-144,-172,-1000,-1000,1000,536,708,444,-718,109,1000,1000,787,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00652() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-146,92,272,340,344,619,-345,-550,-968,-333,-780,304,-67,364,31,611,500,361,-156,-395,-124,-248,757,81,-768,-32,182,-165,71,50,54,-435,1000,-342,-991,178,-549,-781,-539,-340,238,-747,116,-412,-71,-189,128,-256,504,462,-46,597,-6,-805,365,-199,-299,501,-965,682,539,-115,-316,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00653() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-756,-103,-526,-1000,-553,-61,-973,1000,803,48,238,-300,-901,271,197,906,-88,-686,65,1000,286,1000,-281,-1000,-140,-985,1000,938,690,-1000,-1000,6,1000,1000,-179,815,-660,1000,824,144,-1000,836,815,-705,108,-1000,-456,1000,-189,-368,901,-95,896,631,708,207,283,-947,922,-653,-598,-1000,-313,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00654() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{542,343,1000,501,-626,-374,-16,-282,789,-718,-140,-609,-91,225,-777,-778,-153,689,-344,-381,-52,-92,-334,20,-622,136,916,655,1000,1000,40,-444,-645,-752,576,-372,89,333,-249,54,1000,-65,-751,35,-1000,-149,-1000,-361,-1000,-997,-158,402,635,-818,583,17,1000,-55,917,431,-740,150,180,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00655() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{316,-372,-491,-386,-658,92,-967,1000,-524,-1000,251,-1000,-1000,-148,822,84,-280,-1000,-275,363,-58,688,-353,150,-1000,374,-286,-1000,81,1000,975,504,1000,984,-1000,1000,31,487,-537,-937,-750,-1000,-233,228,-1000,320,-1000,-382,24,-113,584,107,431,-9,-1000,-1000,829,-1000,33,-162,788,200,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00656() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-96,-196,176,-484,-568,-496,-981,528,-448,-92,87,-1000,-1000,-1000,884,258,67,-232,-795,-139,180,-520,-929,893,535,-801,-26,742,186,-681,-782,171,628,48,-277,256,-660,337,-1000,293,-581,824,1000,-99,-800,-895,859,1000,701,-447,357,-326,1000,407,997,238,-1000,1000,-29,192,790,-737,1000,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00657() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-667,1000,1000,-555,743,239,864,-1000,-1000,-15,58,596,-386,926,-1000,-8,1000,-515,335,-193,-23,849,1000,517,906,820,-72,100,-90,62,-828,-46,470,274,-1000,1000,-52,-468,239,1000,139,-1000,-590,-24,465,706,-772,-679,224,190,-554,-664,433,286,624,-311,-585,443,757,480,1000,149,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00658() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-646,-1000,-303,425,-1000,647,-1000,452,191,11,1000,898,-858,298,-312,1000,330,-525,-1000,743,108,-25,213,-854,760,-531,1000,611,1000,-1000,-1000,-658,268,546,182,356,1000,541,689,1000,-18,383,1000,-907,799,-1000,360,720,358,200,1000,312,536,-168,440,126,1000,-895,268,-946,1000,344,-655,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00659() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-557,-103,-217,-686,-1000,-701,-899,1000,-401,-753,975,-293,-1000,478,654,-236,-149,-283,-761,-398,300,-227,-483,-350,-1000,-932,-338,-85,1000,779,-867,-56,718,409,-318,1000,-385,782,788,-1000,-983,142,792,229,-1000,-1000,-921,699,481,330,1000,842,1000,389,321,-283,1000,-538,583,-1000,62,232,-222,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00660() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{199,-508,-602,-939,-1000,374,623,-863,1000,671,-141,-1000,-1000,595,1000,146,689,-405,608,203,-1000,379,316,1000,614,14,-893,150,340,-993,441,-1000,1000,1000,715,130,1000,1000,-145,-394,443,1000,-841,-600,558,1000,-633,-553,17,-1000,-747,990,-998,913,-1000,-1000,1000,-816,171,-204,1000,-795,-1000,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00661() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{965,-72,184,-958,192,173,-23,-55,20,876,101,-591,1000,1000,233,-183,-1000,-1000,1000,363,757,-1000,-173,918,786,490,-119,-403,418,490,30,-1000,387,1000,-988,690,126,-315,894,-849,528,788,719,-162,576,785,99,-432,-91,-388,-823,487,-483,-553,-146,-269,1000,-1000,82,713,214,-1000,-952,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00662() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{577,-718,-1000,-634,-1000,373,399,-621,1000,602,-878,-1000,-487,918,707,256,-66,-28,684,72,-888,639,660,940,556,591,-916,193,49,-1000,1000,-1000,1000,652,332,-91,891,1000,5,-789,72,950,-755,-591,752,942,-1000,-447,74,-1000,-981,829,-1000,-482,-948,-690,896,-958,-90,-289,879,-1000,-886,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00663() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-378,1000,-142,-616,-991,-1000,1000,-163,-468,716,-1000,-30,-16,946,-1000,-1000,246,74,-145,-264,1000,1000,-460,476,-1000,414,1000,77,-15,-1000,468,-1000,1000,-300,501,582,1000,970,-224,820,628,60,-264,-218,-620,1000,1000,1000,357,262,-473,-670,853,441,-462,493,1000,-1000,-338,-540,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00664() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{344,193,707,1000,-82,463,142,-895,1000,-1000,-286,704,-769,-1000,-205,-77,203,416,-669,-520,411,-614,910,1000,-190,-581,-858,155,-278,-1000,778,1000,764,1000,138,-140,-39,640,-659,-1000,-447,-1000,-142,-806,1000,739,615,-290,258,896,845,1000,818,558,-428,605,-169,-214,405,908,1000,1000,252,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00665() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-571,535,-1000,1000,518,-376,-821,-894,1000,-933,-827,1000,520,392,365,-896,-1000,337,689,-1000,613,12,321,1000,-848,337,-1000,283,1000,-160,-1000,-1000,661,-1000,1000,-55,512,1000,-46,1000,1000,-71,879,-228,53,-1000,-463,617,502,-277,-273,-895,-868,-1000,875,555,-507,-289,1000,-1000,-973,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00666() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{367,-1000,-291,-564,-1000,1000,654,-439,1000,671,-423,-1000,-29,-1000,525,-192,794,-173,-1000,351,-212,-860,737,1000,469,-885,228,40,-1000,-160,695,-1000,485,1000,-267,1000,1000,1000,910,-918,1000,163,-132,361,1000,907,-399,-349,690,1000,1000,1000,-523,250,-1000,-1000,1000,-1000,816,1000,26,-1000,-592,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00667() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-622,-1000,-400,-643,-400,269,-619,372,-314,931,263,-983,1000,-287,-118,229,1000,-434,-924,-566,-150,246,816,171,-854,-422,215,-957,917,806,608,-392,-750,2,-1000,779,256,-1000,663,-225,131,-3,422,665,523,-96,-741,-63,1000,780,589,188,-798,-869,-775,762,452,-781,271,836,-1000,-1000,-278,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00668() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{742,-1000,-312,-1000,-844,232,-571,-708,15,1000,-477,-787,-123,433,-413,-588,-1000,1000,-404,-178,-672,-824,-530,-657,-434,799,-1000,-696,-837,587,-119,-274,-383,-612,-1000,-1000,393,1000,2,-210,-1000,1000,248,393,593,417,-88,507,455,-745,-601,454,-828,1000,896,-70,1000,-1000,-178,-828,-300,119,-368,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00669() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-11,-1000,-1000,-1000,-1000,559,-571,-64,503,1000,-103,-1000,-733,958,875,-618,-1000,1000,-76,510,-927,-924,378,-945,-539,1000,-1000,704,-869,-117,-86,-653,-144,1000,-1000,252,-467,153,966,-614,-1000,928,-333,1000,1000,-2,1000,1000,444,-767,-764,1000,-1000,1000,1000,786,938,-1000,-1000,-1000,493,384,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00670() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{225,-1000,-1000,1000,-539,706,-897,-889,659,1000,136,301,-435,571,1000,-264,-975,1000,410,-622,947,860,1000,-1000,-721,1000,-1000,1000,-789,-100,-1000,-745,-340,739,-935,155,-636,-216,831,-216,-1000,334,648,1000,876,882,-1000,791,982,-1000,-1000,1000,-984,1000,1000,165,-291,-1000,-1000,-784,-207,-120,-1000,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00671() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{677,-159,-487,647,-444,-424,-837,-986,866,-52,-394,1,616,-616,-758,71,-66,-749,745,-210,-722,881,-266,-619,128,-970,-242,26,595,-650,573,918,77,12,-193,-697,78,2,-496,782,-361,826,724,10,391,351,-552,-426,248,-652,-666,-659,-61,918,-457,-185,-839,447,806,-796,-986,997,187,743}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00672() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{506,-1000,-1000,-1000,-891,327,-871,-885,191,1000,-799,-786,-306,590,-853,-597,-1000,1000,-288,202,-748,-930,-400,-785,-616,859,-1000,-276,-978,538,-640,-497,-217,-976,-1000,-1000,-1000,859,1000,1000,-964,1000,537,575,715,291,239,655,608,-730,-359,618,-879,1000,927,-394,1000,-1000,-425,-879,-524,-358,-557,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00673() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-410,-1000,-1000,400,-787,233,-871,-639,-49,1000,-892,118,-752,708,-301,-307,400,224,81,469,10,56,662,-1000,-250,1000,-340,560,221,-604,-507,-1000,-266,1000,400,-160,-916,-221,1000,1000,-145,23,-96,-81,208,-83,361,1000,982,476,233,928,-322,-311,-327,-174,-397,-101,-1000,-1000,-459,-46,-43,918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00674() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{532,83,573,623,-351,715,842,-654,719,247,848,739,-145,-905,-93,982,-253,-283,296,-988,-592,368,437,-563,-678,102,-119,-281,835,-750,8,771,341,-838,435,368,-221,416,-495,-191,-722,-140,-5,100,-597,-113,134,622,-612,149,-565,708,-13,572,437,850,-878,947,-560,-640,-605,679,-393,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00675() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{77,370,-503,98,513,578,-518,753,603,-68,382,554,214,-80,97,288,-569,-922,537,357,-42,797,-231,74,-117,763,-811,1000,-839,-257,314,499,-972,-1000,749,1000,378,-419,-771,-131,-512,545,460,-220,-150,263,-339,-1000,-1000,-1000,771,454,-321,371,-89,-193,276,631,362,-78,-103,-190,758,-1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00676() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-343,-1000,-120,-1000,-510,954,-1000,-1000,755,1000,-716,885,-1000,258,1000,838,881,904,925,-479,-252,-1000,1000,-1000,-544,1000,-815,891,-1000,-370,-699,-762,-499,892,690,-143,-477,-573,805,-151,253,63,484,244,872,-994,454,1000,-312,-372,-751,950,-1000,-748,1000,-698,-1000,-785,-1000,-992,324,-35,-874,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00677() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-21,-1000,-1000,-1000,-1000,556,-571,-434,533,1000,-538,-935,-733,958,49,-618,-1000,1000,-58,684,-927,-1000,235,-987,-230,1000,-1000,704,-1000,46,-616,-763,-50,152,-1000,-160,-930,266,966,727,-964,1000,10,1000,1000,-2,1000,1000,600,-745,-472,1000,-1000,1000,1000,205,1000,-1000,-1000,-1000,31,-172,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00678() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{546,-151,65,-56,-715,229,-1000,-14,1000,-930,-999,224,-129,839,-1000,-1000,-865,-453,878,-456,-639,1000,114,-468,-1000,51,909,-835,-581,-528,468,1000,-1000,-324,-1000,503,266,-1000,1000,236,-380,1000,1000,-881,-22,-572,1000,-23,569,-1000,-156,-743,-17,904,525,48,1000,-290,286,-555,-812,-605,-177,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00679() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-392,-1000,400,400,-1000,98,-571,794,212,1000,-548,287,667,918,503,-1000,-1000,246,-1000,608,473,476,-295,-272,-1000,-400,-1000,-696,531,-1000,-197,-1000,-233,1000,400,-728,568,979,-377,76,-910,68,-1000,37,-22,403,-400,854,444,-1000,-751,449,264,-266,-400,510,208,-403,-718,-1000,225,1000,400,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00680() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{212,290,636,-266,-394,-869,38,106,1000,-28,1000,-962,-1000,-812,4,189,182,409,814,224,-1000,1000,136,66,667,277,76,925,-314,-1000,-1000,-557,1000,-1000,88,-658,-801,1000,672,-628,-926,293,-465,572,-21,-232,-877,-1000,335,162,311,-129,400,703,-1000,-1000,1000,-698,-677,-1000,166,-1000,1000,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00681() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{614,465,-591,-101,-1000,-263,-1000,-911,872,-281,1000,1000,-108,-1000,307,8,-828,-1000,-571,668,1000,-1000,-1000,-1000,963,-511,-1000,-546,-221,-846,900,73,-1000,602,-591,1000,-768,212,620,1000,-1000,317,1000,695,359,-348,-43,-1000,-1000,73,619,-589,-542,54,538,-926,5,-1000,-180,-802,-713,1000,511,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00682() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{704,269,-303,-103,-742,654,-704,-308,-422,-29,116,1000,-1000,-1000,152,165,-1000,-671,-416,262,556,-1000,-861,-766,963,-359,-678,811,-302,-301,611,5,-545,-1000,-203,836,-768,305,268,866,-537,-226,321,529,683,53,0,-75,-785,242,263,-589,-705,-43,620,-588,-479,-1000,94,-488,-895,871,242,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00683() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{725,202,-19,179,-396,-819,145,392,-49,-506,1000,979,-1000,190,48,342,925,-213,-227,-376,-463,-571,-675,-556,-259,1000,-1000,540,-179,-846,-568,563,-9,-164,-28,12,-928,868,311,190,-732,950,-58,943,215,-413,-693,-940,-130,-385,64,-534,-142,501,-121,-1000,1000,-367,-618,-1000,-380,-248,721,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00684() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{872,8,588,-351,-694,-747,-36,77,848,-119,730,-1000,-968,-1000,-779,48,-155,-194,779,417,-365,1000,-689,406,1000,-184,-442,1000,81,-904,-302,-425,350,-678,-500,-766,-1000,868,949,-808,-1000,111,26,1000,371,-1000,19,-1000,693,50,-48,-1000,-1000,590,248,-822,587,-471,-151,-1000,108,-643,1000,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00685() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,233,-332,-217,-52,53,-421,-171,341,442,277,-453,-108,-1000,-214,5,-626,-1000,-546,-8,711,-26,-1000,-513,1000,-176,-1000,111,-31,28,1000,244,-986,650,-1000,186,-669,413,-62,266,-513,284,1000,516,617,63,410,-170,-447,350,160,-1000,-1000,-298,1000,-359,-1000,-1000,86,-547,-832,1000,-83,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00686() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-250,53,-16,170,-1000,-842,-596,-667,-1000,86,-412,-1000,952,-698,233,-1000,545,-84,667,-197,192,-450,380,-1000,1000,511,-251,-650,397,-1000,1000,-546,-168,-1000,-1000,428,1000,377,247,437,-1000,-501,908,-483,930,-347,-798,-448,-314,789,884,58,-645,173,98,-629,1000,-94,562,-1000,-1000,360,359,-350}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00687() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{677,-1000,-1000,685,-404,82,284,752,1000,34,-122,-75,-9,-1000,-1000,-384,365,490,-694,139,-143,64,934,551,-1000,1000,944,562,-1000,-754,-1000,545,-328,1000,458,-113,988,941,1000,-547,-1000,1000,87,-638,-1000,304,227,693,-580,-30,-171,-88,1000,306,-416,-1000,813,-504,817,803,435,-491,564,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00688() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{-81,483,-647,641,-982,-306,-805,532,-282,-752,431,72,521,31,-498,881,7,355,-470,-66,-524,-923,-163,566,-400,180,840,833,-189,-438,-808,-191,-130,-561,-174,-658,338,-476,830,-194,524,917,17,-519,893,530,429,310,-128,779,65,27,561,-750,668,-139,900,127,-479,419,-442,-744,932,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00689() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{87,671,612,257,1000,4,-43,147,384,897,-600,366,-1000,954,239,541,-357,-20,-260,-130,789,-13,692,-391,-490,51,970,-846,265,-1000,-155,12,465,889,348,-608,-1000,191,-118,397,-254,-1000,211,-774,95,178,-205,533,-1000,-198,770,710,-882,480,-189,295,-468,-2,-283,257,-6,1000,-550,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00690() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{96,817,-1000,394,923,110,-786,-240,917,1000,-654,477,-1000,-205,386,303,-13,197,627,-1000,1000,-346,-1000,-1000,421,-1000,420,-873,-427,1000,630,-1000,-792,1000,664,313,-146,-134,1000,1000,295,-887,709,171,-99,-6,-1000,-1000,-467,-333,-1000,819,-1000,935,-1000,-456,-884,1000,-641,912,-1000,1000,-1000,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00691() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,93,1000,-1000,-1000,-96,671,1000,491,97,1000,1000,379,-743,-949,-1000,1000,214,1000,1000,1000,-134,-859,-1000,-934,290,1000,-1000,-1000,655,1000,-1000,1000,-1000,-839,1000,-1000,824,-405,-1000,1000,-430,-784,1000,477,124,-159,1000,-509,365,731,-946,-194,-517,316,1000,-530,287,1000,536,255,-516,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00692() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{774,407,-336,1000,-1000,-793,1000,-113,504,583,-50,-595,1000,625,-1000,695,-1000,871,-860,1000,603,193,-351,-896,-836,398,-975,1000,-44,-116,1000,-822,-1000,595,-438,-379,-1000,-112,-53,-474,-1000,1000,-1000,-1000,-55,79,155,327,281,-212,174,746,-137,45,-152,1000,1000,-148,1000,110,1000,-83,-1000,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00693() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{150,-565,710,850,1000,1000,294,-531,1000,248,951,124,-1000,-417,-1000,-394,388,-568,-113,643,70,-1000,-1000,761,-886,1000,1000,-453,172,1000,-949,1000,-63,-73,-241,1000,-1000,-1000,1000,-913,837,1000,-41,-849,-929,-912,48,858,327,-1000,-975,64,40,506,895,-1000,-676,-1000,1000,-430,51,-1000,454,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00694() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{18,126,229,-139,-938,-370,197,602,-60,-300,-758,-988,602,-935,56,216,403,-50,-931,-102,850,639,877,136,-789,-248,-257,-488,-100,-478,-284,572,-817,929,522,-491,-110,147,-402,894,547,-562,-635,151,-375,362,-834,584,437,965,-826,-238,-350,-509,998,263,310,-955,741,-41,849,678,799,-695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00695() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{482,-485,1000,443,63,327,490,-901,236,62,263,1000,-249,-1000,15,-615,508,143,-190,-267,648,-582,-720,-120,134,226,-1000,-398,444,-526,21,-125,-1000,122,-12,-87,822,-376,-468,474,581,542,-378,-1000,-81,-361,-605,-118,191,1000,-63,476,-20,58,-1000,749,-893,242,508,759,-165,82,-872,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00696() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{-549,905,-103,664,-1000,-1000,-262,937,375,379,-831,-95,1000,11,1000,1000,1000,-374,-466,591,-678,988,61,-222,1000,-1000,1000,1000,-834,-574,-1000,-1000,116,-1000,-1000,-1000,-1000,378,-45,-1000,1,-1000,1000,1000,-228,293,1000,-1000,-1000,-329,-1000,381,-1000,1000,-282,-576,-292,1000,-1000,-550,1000,-1000,-475,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00697() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{88,-1000,797,-152,694,1000,-934,-741,448,174,-277,-1000,-1000,160,-1000,-397,-1000,312,245,-363,-282,-1000,-843,1000,-1000,765,839,-476,711,488,-1000,1000,9,-16,-738,1000,-276,-833,1000,-900,1000,1000,-764,-637,-610,614,388,1000,453,-1000,-1000,-801,136,-415,-492,-225,924,-1000,125,-183,-849,-838,442,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00698() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{1000,-413,-547,1000,-452,-894,28,-232,-1000,516,-614,4,-346,362,-1000,901,209,307,-233,958,-1000,1000,-67,-245,-198,-1000,1000,1000,325,313,-1000,469,1000,-725,-1000,-1000,-1000,-248,318,-1000,-926,-1000,-854,826,518,949,486,-1000,-1000,-1000,-1000,-318,-1000,1000,1000,-749,103,1000,-1000,-66,897,-209,30,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00699() {
        org.junit.Assert.assertEquals("java.lang.String:Nw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{-1000,530,677,557,298,307,137,73,1000,382,464,537,374,-705,400,-47,1000,-975,1,-119,174,-568,-777,550,-68,346,1000,64,-518,341,-1000,-282,-604,-966,-1000,306,-522,-613,674,-1000,1000,393,1000,-1000,-1000,-1000,622,1000,-49,-836,-1000,-118,-613,1000,119,-707,-476,-67,426,-909,963,-1000,-128,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00700() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{389,440,-208,703,-981,117,212,244,-193,803,-190,194,73,-350,-235,-451,-698,-27,350,-178,-519,-403,-600,229,1000,-178,208,585,1000,1000,-812,1000,-1000,-985,-295,-90,-334,-15,616,-887,-331,-1000,300,960,-607,14,100,-11,-210,-372,-1000,79,-145,384,639,-237,-16,-232,-430,-475,400,-777,-597,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00701() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,562,-1000,121,-776,-251,-129,936,-1000,-1000,766,914,-439,756,-18,1000,-962,1000,74,-677,1000,154,731,1000,1000,1000,-646,253,330,807,1000,-149,-830,-516,-1000,842,216,-1000,-647,452,-1000,277,1000,1000,-549,-368,-1000,-234,782,-196,-358,-1000,72,1000,-827,-383,-1000,832,945,704,471,284,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00702() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-482,198,354,-843,-820,916,625,433,790,-1000,-230,1000,429,1000,-464,-349,787,470,-1000,-988,-997,296,-816,105,1000,-1000,-915,-241,-593,1000,151,-124,457,-266,955,1000,332,-791,171,-1000,313,615,1000,-866,154,242,-191,-403,-307,281,332,-94,-1000,-1000,-65,-69,-277,-160,815,1000,-241,669,140,-517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00703() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-587,371,997,1000,-63,103,164,617,113,-546,392,1000,-402,540,-1000,308,-785,-66,-1000,-582,-130,-1000,1000,272,337,-1000,-1000,684,252,948,-241,263,445,-1000,141,936,274,58,537,175,410,-318,984,530,-158,106,323,-161,-209,-150,1000,991,842,482,26,517,-822,-730,-282,534,-112,-666,89,70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00704() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{898,932,531,28,543,454,256,930,709,270,994,1000,-1000,293,-1000,1000,394,524,-366,-1000,1000,-178,1000,272,-1000,-914,-1000,185,287,980,-1000,-1000,528,-964,538,-165,1000,1000,-132,958,526,890,299,-508,270,-992,1000,551,-1000,420,-41,1000,1000,1000,-752,184,-863,-466,-730,563,1000,-1000,-402,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00705() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{192,-1000,1000,-690,411,809,419,-911,824,1000,269,1000,945,799,-608,1000,1000,718,746,1000,803,589,1000,1000,621,-764,-718,1000,-594,1000,-448,-494,813,-1000,-98,-360,1000,1000,554,1000,-419,-128,-1000,390,-160,322,253,-633,-803,816,-1000,1000,1000,686,-979,-275,-1000,-1000,-864,269,1000,-483,740,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00706() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-587,113,145,1000,-293,-331,-94,617,457,-1000,392,1000,921,462,-214,308,-1000,268,-1000,464,-1000,-841,1000,1000,337,-1000,-1000,-327,771,768,887,544,753,-235,1000,1000,-72,-1000,654,-251,373,690,834,530,205,-414,-397,522,327,-346,1000,959,-1000,-831,814,1000,302,-655,520,534,-1000,-206,815,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00707() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-437,-147,-392,432,-382,-461,-385,-732,-338,-76,-407,349,-10,21,-880,-260,-958,182,1000,-1000,508,-876,-811,270,660,713,542,236,-542,1000,1000,416,213,216,-1000,-944,-1000,168,-776,-345,-487,632,301,-408,1000,-1000,-462,1000,-1000,-1000,-376,-629,-5,21,-287,-28,-274,-198,711,-1000,-694,686,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00708() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{38,-910,-992,289,1000,-303,-91,-1000,-1000,260,-957,-1000,1000,-1000,753,-656,43,-1000,900,31,-1000,394,-1000,-188,1000,385,-45,646,1000,157,972,1000,52,490,292,-1000,-1000,-1000,-1000,-325,-702,-518,397,-585,456,557,-699,-259,1000,-429,1000,294,-1000,146,1000,317,1000,-24,-344,551,-1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00709() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-921,911,291,-770,-1000,279,-1000,741,-1000,366,1000,972,-580,-547,783,283,1000,148,677,62,551,-228,-306,-524,-379,-631,1000,507,599,-102,150,-358,78,-79,-1000,38,575,-353,-979,548,-1000,-29,-439,409,107,458,1000,-1000,-643,1000,-1000,291,1000,-733,1000,-558,-665,1000,1000,-1000,-1000,-256,788,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00710() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,132,874,-1000,349,-270,-833,1000,-152,-59,926,842,-1000,-241,205,326,377,1000,-232,-5,1000,-145,235,-1000,276,178,115,502,-307,-102,-157,194,218,296,-1000,-596,-439,607,-489,208,-882,-840,40,-719,625,54,790,350,-827,464,-1000,487,1000,-1000,193,-1000,-1000,1000,1000,-328,-180,-1000,384,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00711() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{355,-259,433,-869,-309,-883,281,-1000,-435,-806,-1000,557,551,-494,128,20,126,-752,1000,602,-255,1000,-1000,137,-623,-1000,1000,1000,-291,-758,1000,-1000,-360,-1000,109,-351,317,-405,-859,-1000,970,-847,-1000,-1000,980,613,656,-775,826,420,569,441,-1000,699,762,463,1000,-23,-945,-98,-215,1000,1000,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00712() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-98,-34,172,-1000,-1000,-534,-986,-389,-544,-515,-356,766,-382,-251,344,492,137,384,808,1000,-141,862,-945,-1000,-624,-576,1000,1000,-359,-890,975,-1000,-37,-984,-1000,-221,446,532,-579,-842,-679,-1000,-1000,-496,1000,-416,857,-1000,-313,655,-351,103,879,-626,122,-1000,-336,614,443,-271,-316,13,1000,493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00713() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-71,-397,1000,395,400,551,-246,1000,1000,369,544,-297,-850,374,-1000,191,557,-237,-14,-540,1000,-613,1000,803,1000,-265,-711,-1000,-400,352,-1000,884,351,638,-551,-703,185,-436,448,322,649,308,-448,1000,-1000,627,-601,1000,345,-382,-638,1000,644,515,286,1000,-320,-601,-1000,273,682,-641,-275,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00714() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-841,259,-1000,-593,-247,477,-735,-1000,-586,-193,-136,877,628,-527,741,745,-704,-631,809,879,53,118,-1000,-327,334,-1000,1000,967,156,-974,1000,-1000,96,-1000,-415,-177,-135,-454,-1000,154,-500,257,-319,-676,1000,1000,1000,5,55,652,531,1000,593,1000,-486,565,541,246,-379,-978,-616,611,826,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00715() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{333,137,416,292,343,168,297,118,444,-893,-519,877,142,694,-422,-25,149,-130,40,879,-58,-367,-1000,-334,345,118,-115,967,-455,-436,225,-3,939,-612,591,-177,687,-400,509,-711,756,257,498,66,-721,803,-790,-490,55,-521,37,617,-1000,-504,451,400,91,-741,-1000,620,199,-69,-260,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00716() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{840,-913,1000,1000,-1000,-1000,279,157,394,-157,-1000,-324,-1000,-1000,-108,-295,-740,815,925,958,677,-795,-794,-326,-1000,-528,758,-1000,495,-653,691,882,1000,161,-80,49,1000,-1000,188,-782,449,-420,145,551,-486,-253,321,1000,-667,182,-298,322,784,-661,-220,-520,637,1000,431,1000,-232,-710,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00717() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{376,-933,-307,-34,773,256,1000,184,-899,-708,1000,1000,984,-42,-335,970,289,130,-1000,-1000,-1000,1000,-809,1000,1000,-597,340,898,-69,-436,481,-1000,-416,-749,-444,-1000,-677,1000,1000,-330,-1000,877,-210,-552,-368,-818,284,-412,-1000,-770,-1000,1000,-1000,874,891,-1000,728,-1000,-1000,-1000,1000,-65,970,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00718() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{288,1000,521,832,-456,1000,427,28,163,292,81,-547,130,-1000,-345,-300,1000,927,256,1000,775,1000,-203,-669,-825,-278,686,-1000,466,-417,1000,201,1000,89,-1000,1000,-195,-682,-172,-1000,612,-1000,-507,1000,748,-844,-761,-254,1000,412,453,-370,1000,201,-640,1000,987,1000,477,354,8,-270,-850,875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00719() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{911,-1000,-959,1000,-693,943,529,-16,286,-803,-291,1000,-304,-171,364,-334,-1000,-213,-466,856,-1000,185,-1000,-425,563,-1000,172,172,834,471,-986,340,-45,65,-186,-1000,-502,-176,750,-11,19,-1000,15,-752,708,274,1000,466,-723,879,-1000,-660,956,-80,-1000,-417,721,365,-1000,-174,-140,-94,912,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00720() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-996,944,-760,903,-225,-378,694,482,-922,667,-170,439,552,-364,-724,334,-869,-213,-865,856,739,126,81,-871,600,-801,377,414,834,906,-986,207,-966,-259,455,-574,-346,-457,-109,576,-813,108,-643,564,377,-194,584,206,-23,-243,33,-660,956,11,-795,-37,-547,740,-978,-174,-710,-549,852,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00721() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-827,-1000,-280,821,-344,-1000,-846,556,229,-719,-362,59,1000,129,470,-461,-558,427,365,955,1000,-1000,-611,-333,-474,-951,-228,-37,-737,977,-1000,-1000,290,389,-165,-1000,1000,1000,588,-802,995,-715,376,-272,599,781,1000,337,617,225,520,-1000,272,-1000,-1000,145,1000,15,356,771,1000,-592,799,312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00722() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-945,-1000,-224,1000,1000,-801,689,-1000,112,460,1000,1000,968,-1000,612,-690,-1000,-242,-771,-1000,275,-776,-642,317,341,58,25,362,-1000,1000,1000,-1000,-387,-349,-312,-302,818,707,692,-751,-399,-1000,272,-131,735,1000,414,438,-1000,1000,426,1000,-1000,-304,-285,-1000,-619,-1000,529,-459,1000,497,-672,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00723() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-661,254,1000,-721,195,-477,-392,125,-428,-210,829,772,1000,-397,19,-400,-1000,112,26,811,-270,-128,-1000,930,354,424,-390,-106,466,176,-345,-277,1000,-1000,840,-237,-1000,-1000,-888,-658,1000,-307,904,-66,737,16,-1000,1000,471,906,972,264,-839,310,-690,953,-1,786,-1000,-167,-529,400,-538,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00724() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,759,149,976,-1000,-1000,-1000,702,441,1000,847,-121,-113,-369,-1000,-872,-1000,1000,-286,1000,655,485,-386,-1000,531,-1000,894,351,-361,-999,-1000,-1000,539,1000,222,960,-1000,-350,410,-1000,494,-240,-1000,590,-870,-1000,103,-1000,-806,-859,1000,913,363,120,-151,156,1,1000,-606,-1000,1000,-831,1000,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00725() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{41,694,331,210,-376,-766,-596,-636,539,-165,-612,436,220,-194,-583,-779,-587,-107,214,933,-302,-562,-656,464,465,-1000,-1000,686,596,-371,-1000,-1000,1000,-442,129,-541,-1000,-1000,-893,-619,537,-1000,-38,-991,737,-483,-355,903,699,851,1000,-197,669,291,-743,1000,-594,900,-907,-15,126,655,-49,823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00726() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-669,-1000,-1000,-618,-1000,-80,894,-353,-1000,-708,-1000,179,198,-1000,1000,1000,890,-465,-1000,-1000,-1000,1000,499,703,1000,-1000,-853,-949,-1000,-1000,589,1000,-1000,-1000,-1000,-306,911,475,975,1000,-1000,888,-46,702,1000,1000,-1000,641,-928,-531,-1000,1000,-372,629,1000,-445,-414,-1000,-1000,423,-713,-535,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00727() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-405,-916,-1000,99,-1000,473,389,-738,-862,-727,-969,719,792,-401,979,834,1000,-1000,-1000,-1000,-1000,33,412,607,359,-625,-1000,87,-53,-584,-96,804,-630,-1000,-797,-615,411,157,263,552,-1000,-276,849,320,1000,1000,-490,792,-15,-1000,-188,382,-368,11,971,595,-227,-1000,-1000,968,-840,-723,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00728() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-209,-1000,-737,-1000,-61,-1000,407,-353,187,-360,-314,1000,-400,-1000,221,151,1000,-174,94,-1000,-375,1000,-113,-7,77,-604,-243,709,-188,-521,-1000,732,-895,-296,110,312,-278,475,1000,1000,-1000,456,-363,872,245,189,232,-599,-890,-611,-831,374,241,-151,810,-544,1000,-280,-309,-191,-155,-331,681,-913}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00729() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{961,-1000,336,602,-777,673,9,-50,-711,216,-1000,412,198,936,1000,1000,-352,-835,-20,243,-277,-869,808,-181,-556,709,-1000,342,-133,-534,-1000,-347,282,-404,-507,-822,458,76,99,-665,211,-691,596,-56,203,-595,449,-374,120,-1000,547,-245,-770,-895,-819,57,-414,-1000,-756,-191,213,-872,-553,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00730() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{625,677,186,-170,163,444,211,-690,-524,-982,-677,654,881,-51,-594,-346,708,-605,617,315,-716,832,-1000,-20,-81,-867,-796,-1000,1000,-455,-166,-956,-808,-367,270,96,289,-1000,-745,-894,1000,154,455,-693,581,-161,821,510,-682,169,473,-771,-346,987,-533,-918,1000,-450,451,-631,-56,-692,931,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00731() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjU2", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{410,-276,-836,-21,79,833,-361,811,-544,-1000,-299,792,-421,603,-701,457,321,-546,1000,208,26,769,-501,637,256,-877,-937,-1000,506,-780,-103,482,-739,1000,247,771,-343,-653,351,-583,756,120,-258,-794,-251,832,56,652,-531,423,-292,-401,-559,751,634,-878,343,-169,1000,-747,161,-187,-19,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00732() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{680,730,-962,1000,364,-376,-784,811,-905,-335,206,-88,855,1000,-1000,-791,798,-1000,401,-538,421,999,-1000,342,-1000,-1000,-233,-850,758,-286,-8,60,-866,-1000,247,-183,-944,-1000,-346,-630,1000,1000,-1000,-170,1000,-271,1000,-744,-1000,-333,1000,1000,-1000,1000,484,-1000,829,58,-879,-754,-21,1000,-974,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00733() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-957,41,1000,195,-911,-2,-867,-371,-515,243,-508,277,1000,-252,-304,-448,1000,849,-763,235,871,-418,-637,-1000,-871,710,-302,311,-780,-173,446,-266,268,804,-32,14,998,-675,-1000,-1000,512,25,1000,-1000,-149,99,14,441,952,817,317,-867,482,-1000,-1000,314,317,-1000,963,1000,-560,-227,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00734() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{241,953,929,-325,80,-56,-367,1000,-408,-532,148,282,1000,-155,70,-280,711,28,455,-127,326,243,-1000,162,-602,-150,-452,-99,743,97,41,-1000,44,-510,233,-1000,1000,-566,-1000,-887,1000,323,1000,-794,1000,-1000,1000,215,-115,324,588,-593,-26,365,-7,-25,1000,-744,74,-35,59,-248,1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00735() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-888,509,-428,-469,-497,562,-374,-535,-953,-305,63,-154,-240,-866,604,85,-610,-645,719,807,-982,-813,-146,-180,-379,934,859,-766,392,-854,-226,-601,-548,-52,438,241,798,888,-39,311,-197,168,977,401,-606,808,861,-70,174,-190,559,483,156,699,560,-161,-456,-27,676,-357,-967,-78,-246,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00736() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-898,-576,109,685,-1000,449,-1000,832,-1000,-453,217,-527,979,325,-338,-480,-362,-511,549,1000,-1000,-1000,-1000,373,716,-817,1000,-1000,1000,-1000,172,279,-465,52,858,499,652,-434,546,-686,127,1000,701,659,-388,-528,821,323,68,-89,361,-580,474,765,450,-1000,-458,-210,960,-675,-699,405,931,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00737() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-685,-643,128,1000,-570,714,-449,-1000,-1000,1000,-626,-86,247,-439,-124,-970,634,233,-1000,-22,-512,1000,681,230,-517,1000,714,178,-423,-95,-235,-475,418,-239,-1000,640,-1000,266,545,-1000,-343,-1000,-1000,-1000,-224,599,-1000,1000,-364,1000,-25,-433,-1000,-669,-483,370,45,1000,-775,943,669,-546,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00738() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{849,602,1000,1000,-332,-552,-341,1000,-816,-215,-242,768,262,-214,853,461,550,-828,952,742,-717,-702,539,-442,17,1000,358,-116,82,-33,-1000,-901,-1000,1000,818,577,-69,-488,-1000,-1000,240,-829,682,28,216,-266,-192,-357,-706,-1000,191,340,-700,-235,15,453,153,-1000,360,1000,-280,444,-458,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00739() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{1000,-503,699,142,24,213,-17,140,981,-1000,-579,443,156,-458,-206,572,165,539,-1000,672,-799,-210,-227,-181,-689,-35,763,278,106,-761,-353,233,-741,-570,1000,192,796,52,302,-328,1000,417,-1000,790,393,-535,711,-815,-1000,672,139,-96,-714,294,-61,-1000,-33,-335,242,-247,-195,-323,976,360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00740() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{1000,-1000,1000,-468,-161,-172,-636,948,446,-1000,-837,1000,-267,-400,-1000,1000,499,872,979,1000,-1000,-493,-851,40,-1000,501,619,1000,126,-874,409,200,-1000,-160,976,536,1000,376,379,-636,1000,46,-1000,1000,-26,-1000,277,-1000,-1000,724,196,146,-694,137,1000,-807,-1000,-265,609,-1000,-877,-456,285,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00741() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{127,-161,177,102,117,-510,-41,-160,1000,-578,-259,-230,110,-1000,-124,34,1000,-918,48,-352,-533,-67,-296,1000,495,-1000,650,-511,1000,-912,-263,359,-100,545,796,1000,288,402,337,381,1000,-1000,-132,1000,1000,-1000,38,-1000,554,-480,-341,-135,-784,603,1000,-1000,296,461,-302,-555,86,809,771,-506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00742() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{311,-721,-374,798,-279,-407,-614,-252,-192,422,-73,441,463,618,36,561,278,-53,-47,-918,1000,432,-864,-558,215,368,-447,-745,-259,294,546,1000,970,-685,-491,328,-1000,-913,-419,928,-703,-190,469,-663,150,580,-275,739,-617,327,514,1000,965,-592,-632,-80,193,186,690,999,977,-883,-1000,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00743() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{1000,-433,142,267,745,-1000,-462,-33,991,514,-840,1000,160,512,271,656,653,-1000,-215,-409,727,-1000,-1000,-156,-91,-733,-298,120,170,-528,-605,695,539,-355,-1000,1000,-105,67,790,-591,-62,-422,404,17,563,57,899,-46,-1000,-135,294,849,387,-227,-340,-1000,-217,275,485,1000,957,-286,613,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00744() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{449,-342,-612,19,1000,688,-838,-1000,-133,1000,1000,-503,1000,647,749,276,703,-52,-1000,-1000,1000,1000,-462,1000,-163,-104,47,-910,166,-408,-587,1000,1000,209,-1000,-1000,611,-385,-75,1000,-1000,897,15,-886,-657,1000,745,622,-622,1000,906,1000,-352,1000,-430,985,-966,-716,-1000,1000,1000,-366,-1000,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00745() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{1000,-772,496,-5,108,-258,-1000,887,502,-314,108,1000,58,242,-556,1000,1000,1000,314,365,-342,-119,-1000,408,-953,911,933,580,519,-1000,-700,416,88,-326,578,300,1000,759,240,-539,926,206,-1000,661,-813,56,853,-440,-1000,1000,908,1000,-435,1000,881,-740,-1000,-414,167,-494,215,-1000,163,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00746() {
        org.junit.Assert.assertEquals("java.lang.String:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-68,486,-121,-216,-1000,504,29,-60,192,745,-319,-308,-368,-35,186,-232,-1000,-400,-787,-917,-535,929,393,-551,9,1000,1000,-420,-1000,-1000,-91,31,747,10,387,81,-42,-154,590,373,-364,405,146,557,250,-314,87,481,-897,41,-949,-34,717,-1000,431,-282,724,-1000,619,184,-1000,21,-400,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00747() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-62,734,-35,255,-846,-110,-50,945,1000,228,-779,474,-809,104,-944,524,270,-312,155,-451,-1000,29,-88,-343,-768,-100,-947,-760,395,-1000,654,629,352,94,-382,-1000,-622,-1000,519,355,-183,138,396,-133,153,-116,345,-3,351,-349,-31,-560,-446,-719,-69,264,-251,-1000,401,-144,291,532,-636,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00748() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{34,1000,471,-318,363,1000,992,492,95,478,200,-456,2,-1000,1000,248,129,-281,-138,-130,-1000,1000,-239,401,23,370,88,-385,-551,-138,-759,353,-762,31,672,889,190,102,710,266,-186,285,815,110,-256,-734,351,487,701,-232,-712,-182,370,192,1000,-33,-117,-644,25,208,489,-73,521,-675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00749() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-30,-769,349,202,904,1000,-27,-298,-232,-1000,744,366,911,1000,1000,-754,361,1000,-1000,1000,386,-104,1000,56,-373,-1000,-754,-11,102,1000,723,806,-823,345,1000,805,-1000,430,1000,-451,1000,908,-1000,1000,458,-699,138,389,-1000,1000,-144,-215,496,-81,-1000,1000,644,2,1000,-1000,-1000,-423,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00750() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{137,1000,-279,-739,364,1000,107,94,-401,-329,936,-901,-338,317,1000,-853,637,535,-523,407,843,400,1000,-773,-233,36,-5,-491,-960,155,-1000,1000,-227,-585,642,879,-346,297,985,815,763,934,-699,951,-898,-1000,-1000,267,173,-132,-400,169,1000,-1000,35,446,778,-901,533,-282,1000,-1000,737,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00751() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-762,134,1000,-118,-306,-842,874,826,393,186,-1000,874,48,-677,-577,1000,-139,-875,785,-186,939,930,-1000,371,1000,838,-608,-741,421,-947,-178,353,-129,1000,-753,-981,300,-806,671,396,-1000,-1000,1000,-30,854,147,928,1000,1000,40,-1000,-1000,62,1000,509,-307,-255,-137,352,791,434,1000,276,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00752() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{632,992,176,780,-233,424,22,-23,-778,-317,294,672,-838,993,-969,-873,-769,-218,-364,715,-48,885,-599,878,662,-389,-122,316,912,191,-171,-577,281,73,492,-876,29,-507,-523,-402,-844,-182,-280,657,-932,-746,-394,-39,-730,701,-341,-288,-692,-246,-39,971,-489,-828,881,-678,999,-530,-384,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00753() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{-68,-121,-502,525,462,863,177,400,179,-648,-928,178,-550,-193,85,-375,-684,400,-656,-1000,52,398,953,-1000,-594,50,591,1000,-118,695,633,-187,-400,-458,237,1000,1000,-15,-113,537,-550,-590,-920,276,-645,-69,-323,-707,-778,-52,-1000,-301,1000,-577,-744,400,380,-1000,138,-204,145,709,435,-285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00754() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{688,172,-289,-236,-217,373,-999,185,-318,-1000,134,880,65,1000,-79,1000,-1000,212,-76,918,-1000,-743,457,-1000,222,-707,-185,498,493,350,1000,-966,615,802,-30,1000,717,-338,-96,611,-660,818,-79,1000,706,-695,-423,-198,-61,-1000,-1000,-208,-684,57,-534,1000,752,-346,-78,-6,-1000,412,-877,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00755() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{-978,-387,404,812,272,75,-553,949,199,1000,579,-305,1000,1000,24,-282,-560,-252,-355,400,-1000,-447,-1000,394,803,1000,-537,237,-646,-1000,-1000,-1000,-400,1000,1000,-386,1000,-1000,357,-1000,650,1000,391,232,-506,1000,-370,-794,-668,387,-619,610,-1000,64,483,173,-1000,633,-492,486,-443,596,592,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00756() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{42,-283,-509,1000,661,-786,867,361,1000,871,-175,-743,-349,486,-898,-123,-1000,-625,-724,-203,171,228,22,-320,-296,827,164,732,-408,-950,-131,-183,1000,-1000,1000,338,653,-181,312,-927,168,-218,-1000,-400,74,-738,-244,-20,-741,162,-269,470,1000,-1000,-621,707,-913,-575,-223,-1000,-679,913,-33,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00757() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{289,128,239,317,554,505,883,8,552,446,-772,339,-476,977,-768,643,462,679,-419,-881,499,-214,-925,55,796,905,423,784,-706,207,884,304,-86,-139,867,165,-467,-590,921,-38,-624,830,-654,558,335,-929,-873,63,182,-957,-197,453,638,-808,-547,775,-194,-896,71,-132,632,-118,238,-320}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00758() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{634,-757,-737,91,647,1000,-3,241,-240,-1000,-255,449,-908,9,1000,-339,-347,262,-408,-758,154,82,953,-942,572,404,1000,312,610,900,690,-464,-251,676,-1000,-400,601,970,-422,344,69,-14,-227,-400,167,-556,76,-271,-28,389,-383,-557,461,228,-190,-41,912,-398,443,137,60,874,299,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00759() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{-1000,-532,-960,-643,662,-992,-1000,-873,69,479,1000,-1000,929,-17,307,1000,-955,-1000,1000,1000,-716,-867,424,-612,1000,983,-1000,155,870,-1000,-1000,-534,1000,-951,-92,602,955,548,-858,-1000,1000,147,1000,909,-1000,1000,-400,-160,-896,1000,-701,-623,-48,-613,812,-1000,-1000,1000,-1000,696,-606,1000,-350,-775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00760() {
        org.junit.Assert.assertEquals("ARRAY:[C:436:24:java.lang.Character:Ng==:24:java.lang.Character:Ng==:24:java.lang.Character:LQ==:24:java.lang.Character:Ng==:24:java.lang.Character:Zg==:24:java.lang.Character:Ng==:24:java.lang.Character:Ng==:24:java.lang.Character:Yg==:24:java.lang.Character:Qw==:24:java.lang.Character:OA==:24:java.lang.Character:TQ==:24:java.lang.Character:LQ==:24:java.lang.Character:Yw==:24:java.lang.Character:dw==:24:java.lang.Character:Xw==:24:java.lang.Character:RQ==:24:java.lang.Character:TQ==:24:java.lang.Character:Ng==:24:java.lang.Character:bQ==:24:java.lang.Character:Xw==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{1000,194,317,639,-1000,-905,1000,1000,-363,1000,-624,1000,1000,-486,677,-418,545,-8,12,-536,-1000,182,-875,1000,-475,-1000,-101,339,297,-508,246,715,663,-207,-596,318,174,-824,666,-637,-431,722,436,83,-58,-310,78,-761,-668,-146,-302,856,-1000,-730,-1000,1000,-1000,230,-191,-830,1000,-346,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00761() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{-617,1000,1000,-901,219,-399,695,947,225,877,-1000,789,-174,-259,1000,1000,1000,217,-222,-98,-111,1000,991,-975,73,-1000,69,450,1000,687,-282,553,635,534,-123,-164,1000,-1000,-1000,-25,516,175,-187,-84,-62,-928,-1000,56,1000,-315,1000,-314,-658,1000,-325,1000,39,-1000,-12,696,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00762() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{893,89,-58,72,367,1000,-177,-519,-599,0,-716,952,-157,228,-147,153,-528,515,-316,0,0,-219,-877,0,-178,984,-731,76,-248,-1000,-383,253,0,418,29,-1000,121,482,359,445,-870,-183,920,-250,-244,586,-27,-216,-278,0,0,0,283,534,30,-15,-360,1000,-879,-619,-897,-788,1000,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00763() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{1000,-1000,-479,608,608,-286,539,673,-843,-529,1000,-153,98,-1000,-69,248,69,341,1000,-625,-735,-919,-340,269,424,-301,617,-600,-5,1000,931,-139,-220,-117,679,346,629,-528,89,358,1000,-358,-1000,738,30,-197,20,-651,306,443,-355,-109,-771,-821,-1000,1000,263,-1000,-160,803,1000,-961,-1000,983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00764() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{644,-746,-178,549,-231,1000,-483,-679,-936,299,-682,-462,589,-987,328,1000,1000,1000,-672,-162,786,-106,-84,-790,-326,-1000,-1000,816,1000,-676,-200,817,37,142,804,-1000,1000,870,1000,1000,-240,-578,557,46,-750,29,319,-892,-739,865,-296,-625,302,-46,-127,-585,426,-1000,-1000,-582,-49,475,90,-959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00765() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{559,728,912,-441,-1000,-282,899,-624,43,-1000,-1000,1000,-136,741,34,898,1000,-156,-946,-821,639,454,-482,-404,606,1000,-732,-233,318,337,-753,-251,974,1000,-1000,-1000,1000,1000,799,-899,-1000,615,-661,-1000,-905,261,-161,332,524,-20,376,-746,358,1000,69,1000,-672,1000,-1000,-1000,-1000,1000,1000,-19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00766() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{777,120,259,477,112,496,434,893,-1000,1000,-426,967,-213,-182,583,-279,-69,62,-105,-86,-430,821,-843,-1000,-349,64,84,144,-78,-528,-229,386,282,172,-229,-152,-111,-1000,-1000,121,-909,363,-562,850,-6,289,-476,-210,-245,-259,26,701,-351,-628,-814,420,-695,580,-256,-734,937,-1000,-161,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00767() {
        org.junit.Assert.assertEquals("ARRAY:[C:77:24:java.lang.Character:FQ==:24:java.lang.Character:Yg==:24:java.lang.Character:XA==:24:java.lang.Character:Xw==:24:java.lang.Character:Zw==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-896,-214,-786,-465,-57,680,-1000,82,778,-1000,-126,138,-317,790,848,-890,-105,-828,770,-542,-424,-300,0,277,-866,811,253,-633,385,-1000,-586,1000,-156,-983,-297,-963,820,-193,-967,922,-431,937,860,-952,209,-1000,1000,330,-54,572,398,512,702,-119,57,-564,379,447,-1000,-166,-379,1000,-487,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00768() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{1000,-633,-877,-573,-750,62,749,700,-68,-781,500,-984,-373,-1000,-1000,129,720,-242,22,-1000,-968,742,1000,-1000,-646,-285,737,-1000,1000,-1000,-905,712,-961,-752,-272,-335,-23,1000,1000,-1000,-1000,-775,-833,-958,980,768,-8,25,979,56,-629,-747,252,994,1000,221,987,253,-553,-1000,200,1000,-276,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00769() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{297,-310,-1000,-391,-1000,54,794,-823,237,-11,1000,-104,1000,221,-416,789,-504,669,709,71,428,-836,582,-542,-675,-583,691,-330,272,-357,-256,-114,-593,-951,-276,-933,-978,-84,-1000,406,-918,347,-743,244,-242,1000,335,713,594,-964,309,-456,1000,242,238,881,-992,-239,-705,-894,902,321,-237,835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00770() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{787,1000,-752,-965,-46,671,691,912,-992,-598,-276,-305,-753,-1000,-1000,263,1000,-934,-1000,608,958,-761,1000,-982,1000,-839,-302,-223,829,-23,-1000,-1000,849,-546,105,384,-544,639,1000,-1000,-947,-1000,-400,-428,-1000,332,632,-1000,934,4,-42,-1000,-353,-157,477,550,-686,853,959,-524,901,-434,-103,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00771() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-642,374,-575,509,878,504,-455,968,944,-301,-366,291,-350,1000,1000,-951,64,-915,307,333,227,-761,-5,-378,-942,445,959,93,-420,-154,593,348,426,-1000,816,-181,821,-1000,-342,1000,-733,435,1000,-258,1000,-1000,1000,458,-20,666,594,-518,177,-487,538,-139,297,-615,-1000,-695,-17,1000,-79,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00772() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-440,404,-133,-806,800,-815,889,299,165,-2,-175,600,27,-75,6,843,-521,-995,-855,249,621,-304,429,-928,327,-158,602,541,-557,209,685,-963,-466,-349,919,376,-511,-528,487,624,569,-345,646,12,804,-696,965,180,42,-167,599,269,-574,255,-293,157,-458,-766,469,-464,131,513,481,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00773() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{1000,908,-96,-1000,1000,614,-361,489,808,1000,861,-948,-659,-221,81,1000,1000,-1000,259,1000,1000,-1000,-557,-1000,447,40,-1000,1000,-196,1000,497,610,-1000,1000,-725,868,242,-639,1000,1000,-681,-745,571,1000,730,958,122,-781,810,1000,513,198,-312,939,-90,163,-498,986,972,342,69,-848,1000,197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00774() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{319,-130,-426,-362,-159,-816,54,267,145,17,-293,496,-721,219,-678,-558,42,-1000,-615,759,-31,-107,651,509,-441,-45,-980,1000,-190,1000,543,-1000,-531,-592,846,1000,677,-123,-334,-923,993,963,59,-498,-436,-11,-752,-458,-416,-219,559,172,582,241,463,-372,607,1000,-1000,8,-1000,-171,-821,846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00775() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{388,-300,-231,-709,-163,184,-1000,574,-30,1000,-513,862,-1000,276,-151,-679,-1000,-656,-34,770,-697,248,502,-149,633,155,-56,922,227,849,-431,-734,194,-1000,203,620,721,269,-197,167,127,1000,-506,-702,-502,-974,-920,143,242,116,371,93,760,111,808,-174,418,281,-827,79,-239,-131,-744,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00776() {
        org.junit.Assert.assertEquals("java.lang.String:aA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{-701,-580,-834,-955,-655,626,-1000,839,-459,-545,258,1000,-929,687,98,-746,128,610,-662,299,156,170,-301,32,692,738,-492,884,176,-208,58,1000,-475,422,-549,-865,-342,201,547,809,-480,1000,-533,-990,-1000,-1000,1000,-609,168,-1000,-555,-219,142,36,742,-1000,929,518,413,196,-181,-459,330,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00777() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{-132,-662,472,787,474,-461,-947,-413,167,-586,126,297,-684,350,62,-52,462,487,-35,-141,408,-580,815,421,650,-694,-564,-319,-899,474,447,182,560,500,-740,-976,-813,453,-587,-518,-652,472,20,692,-353,-791,0,850,693,-232,857,-774,206,739,-724,847,-552,676,558,-873,621,-53,10,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00778() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{977,-1000,112,25,-388,-132,949,256,1000,706,413,340,398,558,-395,-489,-1000,1000,-23,1000,466,10,-12,163,28,-582,860,-601,-371,-1000,-220,982,-859,-626,-1000,358,1000,-64,1000,-365,-961,887,-844,1000,9,-182,-351,528,297,-874,401,182,181,1000,-1000,676,573,-177,1000,-351,922,-1000,-661,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00779() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{-524,-634,-15,-175,-1000,-998,-810,672,101,-998,-149,1000,-725,1000,-717,-878,-705,570,11,756,-392,438,621,1000,-615,885,-545,-255,347,1000,1000,1000,-165,1000,525,-201,177,-749,606,-506,631,49,-328,-485,541,-833,651,-507,308,-1000,811,-964,659,489,-1000,-1000,1000,1000,-644,-271,146,99,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00780() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{99,-706,-88,-1000,840,643,-77,337,-450,468,-666,252,-1000,972,-863,-1000,-811,-553,-764,756,-732,-588,961,-370,624,790,-44,-34,-934,-776,823,-393,129,-177,474,-626,572,-170,-922,48,-51,1000,-1000,191,-1000,-1000,245,-1000,-618,1000,-1000,-514,1000,115,1000,-400,277,39,-355,253,-555,-1000,838,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00781() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{168,-568,1000,-875,-592,1000,399,-413,-727,-593,437,572,-278,844,100,394,-770,305,732,976,-316,65,-249,-1000,-481,980,197,-1000,-817,-657,-389,-782,662,-407,216,-578,-1000,-142,-730,161,865,777,739,378,224,1000,132,-785,5,-74,685,4,-39,130,874,346,-583,-848,865,-261,222,806,-34,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00782() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{185,-741,788,-404,-1000,1000,1000,-138,-1000,869,-624,-280,548,-333,629,324,-942,1000,-126,-8,-71,-457,-974,-1000,-852,723,-154,-1000,643,-524,987,-1000,290,-366,906,-648,-923,-96,-54,927,1000,-193,-40,641,-88,1000,-542,615,440,-400,1000,448,624,-976,-613,-367,918,359,-1000,-304,595,-713,-419,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00783() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-554,-725,-246,209,-321,-342,-1000,-544,384,-61,262,576,51,-129,-400,-743,423,-541,94,73,-349,1000,-1000,-619,-64,696,735,499,-646,-278,-181,-633,496,46,254,-411,647,-205,-511,-556,667,1000,1000,-1000,532,-662,-317,-711,616,-568,337,60,384,83,412,-675,-1000,-1000,339,-549,159,-1000,423,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00784() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-340,-271,252,135,199,2,618,216,-312,1,-767,-830,-168,-917,163,-1000,-753,-437,-179,-578,656,-228,-548,115,250,726,-43,-505,-177,-747,992,-1000,-636,-481,705,-943,-424,146,227,200,1000,-322,-717,403,129,470,-569,649,579,-130,701,104,-777,514,-1000,888,1000,-34,-959,-976,737,-1000,1000,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00785() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-990,-1000,608,787,-481,-26,133,-223,487,-360,401,389,211,1000,-918,118,-253,-655,692,257,-147,724,-454,-802,-146,1000,320,20,-662,-451,-595,92,370,-136,240,-645,587,176,-545,121,17,814,716,96,448,-504,-52,1000,-505,-139,1000,931,206,-969,616,1000,-401,-1000,807,-388,111,-538,678,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00786() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-1000,-1000,-500,708,449,1000,-116,-398,317,-1000,208,836,-1000,1000,-1000,-301,-4,-1000,148,-891,261,567,-527,-1000,743,985,608,1000,-17,-78,-480,-65,954,156,-532,1000,-198,798,1000,609,509,712,892,-365,-648,195,-1000,850,-188,-582,153,1000,202,-443,156,621,-425,-1000,1000,132,739,-746,587,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00787() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{826,-697,98,-163,2,464,964,781,-889,832,-938,1000,262,-1000,1000,-1000,-1000,1000,-262,381,-1000,-1000,920,299,816,502,-588,-612,-1000,-912,-1000,-1000,628,-1000,457,-442,483,417,-350,287,518,-1000,-638,26,-702,586,-142,283,1000,-8,343,-730,979,-633,-1000,817,1000,1000,-1000,-730,-430,-1000,1000,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00788() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{425,-1000,596,1000,-457,512,-828,1000,1000,-568,722,586,494,-565,-400,-167,-686,-100,1000,526,-866,1000,-770,722,-400,1000,699,-544,846,850,-556,-326,-1000,225,82,-768,-644,-445,-1000,-1000,265,-1000,-605,-653,-480,-1000,997,393,611,368,-238,868,1000,-978,-400,1000,-1000,858,-448,280,1000,-213,1000,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00789() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-501,884,984,-360,75,4,1000,1000,-493,282,-938,886,-663,1000,-337,-1000,337,1000,-891,1000,802,-1000,-1000,1000,-184,-567,-1000,1000,876,-1000,758,-722,-1000,1000,-1000,-6,-111,900,-1000,501,-73,-339,-1000,-1000,-646,1000,-1000,792,-1000,-804,1000,-970,-1000,383,1000,-1000,722,-98,305,-1000,1000,444,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00790() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{26,-916,443,988,-252,-888,-866,-41,206,154,282,125,852,501,-360,-337,-169,-353,967,89,-58,802,400,291,-400,955,1,289,1000,992,-934,-23,-617,-838,-358,-699,756,581,-400,-1000,667,188,-1000,106,-595,-889,762,251,111,-954,-804,283,581,400,425,524,-710,340,-98,-609,292,-249,444,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00791() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-947,554,-1000,-1000,-412,-1000,-586,-294,332,107,-1000,-1000,-243,-613,1000,449,-528,-1000,1000,-555,-431,-908,-1000,-506,-1000,-132,-1000,-1000,430,-581,-457,525,1000,81,876,1000,1000,298,519,142,-118,171,461,1000,-472,912,-461,-315,531,965,-67,249,-1000,-1000,-663,-1000,-1000,-800,1000,-1000,-686,-359,-914,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00792() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-208,-491,837,259,-757,337,683,700,832,405,-342,864,792,698,-732,-688,-305,748,836,344,827,290,-361,-679,-414,724,175,679,493,815,-209,82,-423,-353,-918,78,-401,-483,252,557,-118,222,896,-674,-661,-57,582,228,720,842,-971,323,56,-630,-311,-99,-184,296,-910,-658,-615,-489,445,-837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00793() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{508,332,21,-551,413,-1000,-498,-872,-550,569,-708,321,-268,-377,1000,-256,-262,-445,-338,247,477,-201,559,-448,974,-400,-1000,-1000,1000,-548,757,-89,321,-623,580,400,1000,465,1000,409,-467,534,1000,490,-504,185,309,-133,287,-986,-833,19,-973,-471,1000,-798,598,-392,840,-765,-1000,142,-672,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00794() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,794,126,-86,277,-400,-561,-766,1000,1000,-420,1000,-148,-235,1000,926,-1000,-12,-873,473,730,1000,-1000,-445,400,109,44,-215,1000,45,1000,-896,-30,-1000,222,-317,829,223,351,-13,-478,513,778,-174,-779,-50,606,183,-1000,-1000,-883,-653,1000,-1000,658,-599,304,-242,1000,-546,-108,435,1000,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00795() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{95,-304,224,578,-397,949,-314,-347,695,-1000,987,-518,760,8,-457,-1000,-1000,787,1000,-400,205,647,-199,-737,-309,-1000,799,23,1000,906,-1000,115,-483,-420,39,-493,-1000,-1000,255,-930,863,-399,-339,-180,-704,85,868,-1000,777,170,-1000,886,-268,-105,-810,414,-1000,618,-677,-220,400,-75,725,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00796() {
        org.junit.Assert.assertEquals("java.lang.String:XnY=", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[]):java.lang.String",
            new int[]{138,-290,34,866,-189,-825,281,342,-786,-979,810,-86,-951,-335,-87,131,-102,-372,-415,-887,809,405,832,973,-44,-211,-629,2,478,-10,-978,462,-379,-868,622,794,-134,935,-623,-892,833,-232,-755,-71,479,609,-864,342,-674,-71,536,933,285,25,-542,-804,-984,-96,-407,462,-298,480,-59,-192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00797() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00798() {
        org.junit.Assert.assertEquals("java.lang.String:Lw==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{535,-369,412,934,-396,-353,188,1000,851,224,379,431,-574,-707,-455,61,537,515,379,-1000,795,-1000,1000,904,167,-1000,-1000,-294,-1000,-483,-964,294,967,-100,-1000,-400,-551,-699,406,-1000,-570,200,-907,-1000,44,-818,-876,-20,365,580,-1000,-1000,789,-265,1000,-1000,-400,-639,170,-324,357,-837,-1000,-366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00799() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{247,-755,-562,-210,-674,-228,-159,392,-503,109,-818,429,540,349,-420,-544,-114,-119,-164,-782,672,869,-883,82,89,744,-884,631,-730,-421,319,662,-421,-8,525,793,45,-273,-434,523,919,446,-728,-876,823,408,203,71,-165,-300,358,-160,-16,479,181,-157,86,-716,506,59,-343,-117,-195,458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00800() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{32,647,192,-159,441,-481,-97,367,-204,893,6,946,743,938,-362,-472,-740,-309,120,-816,-25,-739,-281,-41,-207,-928,197,-281,716,676,450,924,328,396,961,-905,318,567,668,-511,-349,-459,918,962,-530,755,-378,365,-293,156,498,911,498,-943,-713,-73,959,110,-726,-840,-321,-497,38,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00801() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00802() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{352,-745,-425,59,-1000,-338,1000,1000,-161,234,-55,1000,-841,871,-345,236,-1000,615,-553,-887,517,1000,3,1000,390,-461,358,551,-603,-655,-405,-956,-252,1000,776,1000,-542,-529,-474,-106,-660,880,-710,792,957,-1000,-1000,-446,784,894,-1000,-984,-119,874,-280,-749,49,-1000,1000,-871,-1000,-1000,-756,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00803() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{69,701,100,581,-985,431,765,102,770,-181,899,-482,802,547,-411,-178,-928,567,-294,-587,149,645,4,856,330,-408,287,-306,504,293,236,-673,78,-618,10,-154,184,944,-464,401,-405,979,-487,198,320,544,599,549,-334,-560,-19,-483,-290,-867,606,-342,-396,-829,-311,128,880,-195,-180,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00804() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mg==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(java.lang.Object):java.lang.String",
            new int[]{-240,-138,831,328,-876,-226,507,-298,969,-323,0,839,-727,-589,-242,974,-280,165,932,176,-593,164,-176,961,-923,2,-119,204,734,-45,-340,30,-351,931,-342,-848,-966,-527,-621,-559,-71,-360,662,468,230,523,-141,-817,-803,-364,203,965,-723,-469,475,-159,-386,-448,879,-143,-753,893,-50,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00805() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "", "replace(java.lang.Object,java.util.Map):java.lang.String",
            new int[]{818,-554,254,-864,-523,51,-789,-365,976,604,408,-690,753,-108,-113,501,496,-999,-245,-501,331,694,851,816,823,696,655,-552,958,-941,-718,825,-722,553,-188,-360,-487,-318,804,-990,-317,376,831,554,433,662,-858,-115,629,698,-845,712,-333,415,353,339,789,746,-578,-350,-171,-92,937,-124}));
    }
}
