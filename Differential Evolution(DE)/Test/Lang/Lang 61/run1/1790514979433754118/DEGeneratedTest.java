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
            new int[]{323,-724,-212,837,-1000,904,318,-399,-946,-777,820,-1000,162,727,395,-1000,-267,-361,-1000,-1000,1000,-421,557,-965,-969,-696,-1000,-183,-1000,-1000,1000,-1000,1000,1000,1000,-985,-883,-933,-1000,1000,-1000,1000,-339,193,-242,-1000,540,1000,-814,-44,1000,-920,-1000,-438,1000,1000,1000,356,-317,-400,-249,-1000,370,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-299,-732,-772,-468,-1000,1000,-432,247,-1000,-400,-776,-1000,-60,887,-1000,-445,-1000,-155,-202,-155,170,-172,-1,619,1000,971,979,-70,584,-1000,-1000,436,358,1000,739,-341,703,1000,382,-126,351,1000,995,624,1000,712,-426,1000,1000,-283,271,-489,591,511,347,-475,1000,644,-1000,232,1000,-1000,-1000,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-239,-844,-783,901,-244,733,-1000,837,474,-977,116,-654,1000,901,1000,444,1000,1000,1000,1000,1000,763,1000,317,-1000,545,-668,1000,851,1000,-692,1000,-588,-205,1000,-1000,-586,-353,674,342,1000,-972,1000,893,176,-1000,-1000,-1000,-1000,1000,181,-863,327,-259,-101,649,456,-31,1000,20,-596,408,302,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-489,-901,-485,-683,-881,685,-451,17,-797,-163,-329,-845,-359,-163,-613,-7,-527,-780,-887,-389,852,-112,409,-387,476,-102,-95,-116,168,-996,-601,-945,237,678,544,-818,-368,825,-217,-237,-461,686,-243,-326,28,-448,-747,861,649,-216,992,686,-563,415,979,742,712,106,-977,606,271,-392,-918,-459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,505,282,148,-16,-1000,884,641,1000,881,-3,400,162,-1000,-519,274,260,735,-43,-392,-253,774,-676,-429,-185,-1000,-1000,-1000,-52,-6,518,-402,-525,-1000,1000,-129,-1000,258,753,506,-995,330,188,-1000,-1000,-1000,1000,-543,-918,47,-1000,-920,-154,-412,-357,1000,-44,87,-1000,-261,-1000,1000,1000,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,496,-1000,44,749,92,282,217,1000,-74,248,954,294,-740,301,-1000,428,163,409,11,555,930,1000,40,504,-302,-320,1000,311,-1000,248,-338,-595,484,-61,616,1000,408,136,1000,-165,-218,-419,-773,-522,-560,-372,1000,334,-30,598,-243,-125,-240,1000,817,-628,-299,1000,269,-1000,93,311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-641,258,-74,-173,564,800,2,123,-254,-952,-1000,632,727,-253,1000,-1000,1000,1000,86,-1000,-666,557,1000,-556,1000,-1000,1000,-14,636,293,957,1000,-352,1000,1000,-798,-1000,-235,1000,-368,1000,1000,773,-138,-1000,-642,-888,-1000,403,1000,761,576,702,1000,1000,325,-744,841,-1000,-663,465,370,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{70,-310,489,1000,-749,1000,409,742,839,1000,400,902,-92,-119,-412,-84,570,1000,-757,431,408,-320,1000,50,808,395,124,-715,-485,-739,-446,921,-463,-289,400,146,-162,-1000,-1000,-863,-1000,1000,-681,1000,-305,512,-206,-438,109,-45,835,-998,668,-814,-710,439,740,-116,-400,441,-215,1,198,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-744,-141,-373,620,960,-247,-1,509,547,-535,937,1000,-545,427,1000,-193,-39,-845,1000,-158,628,1000,569,1000,533,651,-131,1000,741,-866,-492,729,772,-469,-263,-480,838,1000,-447,446,-141,-579,-572,793,-267,606,-360,704,105,1000,1000,-795,-333,-36,558,1000,-1000,310,-1000,477,-53,-497,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-400,-748,354,-216,-203,528,-338,-502,-1000,673,196,516,-563,-113,-434,177,-658,-713,-1000,45,189,-1000,-1000,-25,-36,-1000,85,-695,190,-234,-851,64,-1000,-722,-162,429,1000,37,92,-533,19,-1000,1000,-206,1000,376,388,625,-1000,183,-1000,-134,1000,208,690,-1000,1000,187,-432,220,207,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-586,-935,-451,-1000,-361,29,-389,-770,-1000,-1000,-876,-338,-1000,183,-1000,-119,-863,-719,-1000,-1000,1000,73,152,-1000,-440,-138,-1000,-1000,-1000,1000,550,403,828,-1000,-1000,199,203,1000,138,756,-207,114,-778,1000,-531,1000,1000,791,958,-1000,863,-955,-1000,1000,-159,-928,-93,1000,-882,571,31,-372,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,493,1000,-376,734,-528,287,397,-237,-319,-1000,30,248,554,-509,-387,172,1000,-601,936,1000,-204,-1000,398,-1000,358,213,-1000,-1000,209,97,-686,130,1000,80,-1000,-1000,-1000,682,305,1000,-166,1000,-236,190,161,1000,-1000,-221,1000,-1000,-164,135,-214,-558,-1000,407,29,1000,-656,511,-281,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{427,2,-273,1000,-640,1000,679,31,868,79,-1000,-1000,-729,-1000,-20,442,867,601,511,84,462,85,910,-75,824,1000,-195,3,-757,566,372,1000,-601,893,-13,8,-366,-764,3,-829,402,1000,-1000,-184,-7,116,835,560,230,-169,623,182,-3,-1000,-289,932,643,-493,900,-323,-481,-16,-553,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{122,-82,-1000,-261,495,-1000,-946,-153,832,-872,874,829,1000,265,-422,1000,48,1000,-1000,-39,-1000,-639,131,1000,-884,1000,104,412,171,169,914,615,294,-1000,1000,1000,402,186,1000,-1000,-1000,-1000,439,-803,1000,1000,-696,261,-1000,-1000,862,-1000,1000,-232,669,1000,-218,434,499,190,645,-999,-313,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{305,996,-603,-123,621,-121,977,871,-741,712,-307,444,468,555,-808,445,-293,291,348,-300,666,133,105,-159,920,-531,795,-200,-174,-1,-429,-268,-725,823,-291,275,-327,-774,-480,543,176,-758,324,-12,-443,-190,560,-624,373,-735,445,533,103,-715,827,-808,783,960,875,344,-640,651,52,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-292,947,508,-681,-399,-675,134,-329,199,-532,-221,-837,128,1000,556,1000,-741,878,-400,400,-1000,888,-378,1000,-1000,-733,-1000,508,678,-1000,-381,-422,1,-528,-20,1000,24,-521,-111,-40,-1000,-683,1000,-639,265,24,1000,822,-849,-717,-296,-933,216,-1000,-1000,748,-781,-660,-610,526,-87,-1000,-95,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-419,14,-41,-1000,-758,423,576,519,-990,524,34,-378,365,1000,1000,67,-1000,223,-866,-1000,-1000,1000,-865,-158,-702,367,-989,1000,420,1000,-726,373,-1000,-1000,-449,-833,87,-1000,8,-569,1000,-382,-426,-1000,-64,732,-910,-287,-341,-351,1000,979,-979,633,-1000,-887,233,-1000,-1000,-1000,-218,-1000,-1000,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-341,1000,629,1000,-506,119,810,948,-1000,102,-958,-870,-964,-1000,195,-517,143,-174,687,967,101,756,968,677,-447,433,-95,867,-1000,-1000,153,104,-946,-567,1000,762,617,393,371,-1000,25,436,709,-1000,-96,580,612,782,-1000,-643,211,0,-257,851,597,687,-1000,563,741,-254,994,-832,446,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{469,-1000,527,-304,395,-661,240,-1000,364,-869,1000,1000,1000,877,-583,1000,704,1000,106,-1000,969,-1000,-29,-562,5,1000,529,144,475,1000,-179,899,779,-1000,554,579,1000,-1000,1000,-1000,-654,246,-1000,1000,1000,370,-336,30,-631,283,246,-1000,1000,-1000,1000,1000,-65,-349,1000,794,-639,-192,-368,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-340,-1000,0,-700,961,29,-444,358,-19,1000,-455,-896,-385,-52,-827,0,1000,735,-544,-1000,-630,661,1000,847,-854,-920,-627,1000,425,0,-1000,-1000,-338,-667,-565,-1000,924,1000,341,274,-161,0,0,265,601,-48,589,-118,766,-967,980,361,-1000,347,683,220,-1000,201,-60,-681,-958,-1000,-520,-472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{325,-166,973,-568,-267,-845,-2,984,-144,-586,-398,-6,7,371,743,-495,-141,-223,-354,1000,412,458,643,74,912,49,-1000,1000,-25,-584,709,-166,321,-219,117,-699,527,226,345,-298,-937,-575,1000,482,542,425,-718,-1000,-59,-1000,1000,1000,-123,485,-477,-832,1000,773,109,-467,637,-923,-459,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-454,99,863,-749,389,85,-324,-1,1000,492,-68,775,-288,-123,-794,913,801,31,582,1000,535,-840,-555,899,-249,-917,1000,-865,-84,162,-1000,-118,-952,367,626,1000,-689,-161,497,649,1000,-659,-1000,-129,136,-553,-91,1000,-573,1000,507,-1000,-366,-728,278,-199,-24,-1000,-1000,877,-14,-415,-565,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-599,260,973,-507,219,-217,-480,-518,-438,1000,-1000,-428,-732,-553,-281,-839,-582,101,388,-1000,-1000,-110,-1000,237,-370,-460,-737,-140,-347,-184,-747,163,835,-670,1000,432,-744,-404,184,245,1000,-250,882,-1000,-171,818,670,554,601,518,331,-899,-845,626,-1000,409,-29,-200,-768,816,1000,-421,148,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{951,-1000,-91,571,-213,-262,179,-562,-1000,-158,-1000,933,441,-342,-24,1000,-428,-1000,816,1000,-744,594,203,-1000,1000,-39,198,284,46,-1000,-803,-612,1000,-139,280,-1000,78,111,625,1000,-644,100,1000,65,-1000,1000,1000,32,1000,-1000,1000,285,-1000,244,29,-1000,1000,325,-425,-1000,637,365,-1000,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{43,1000,1000,-978,198,-19,-597,-30,542,-298,518,515,-391,55,-695,599,-113,-46,-309,319,601,543,50,473,-439,-1000,496,83,557,413,-991,115,-357,-506,437,415,-294,-501,439,-62,950,-717,-430,-260,1000,712,84,-652,-555,407,698,-606,309,-63,-290,-79,357,1000,-1000,4,211,-184,-177,143}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{799,-166,573,-371,-909,-625,-712,323,789,154,-235,220,1000,362,600,-1000,676,298,-531,-858,476,1000,138,948,208,-40,-7,-1000,562,766,-526,-323,127,-260,36,1000,-335,786,-534,-547,-214,-428,-1000,153,1000,-394,332,-93,381,747,753,-946,-493,838,863,-358,-196,-892,-669,444,577,-173,-353,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{574,-166,400,-286,219,-297,66,-618,-89,513,-25,-841,400,382,-345,465,-374,-975,1000,-852,-411,-398,-228,-696,-370,-1000,-563,831,-322,-362,100,-562,-538,-1000,1000,432,-292,340,516,514,1000,-512,-953,-561,355,425,1000,1000,645,518,1000,-899,-1000,390,-555,-428,-205,-52,-1000,1000,576,846,-815,711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{296,200,878,-334,567,-95,325,147,-359,974,-232,-901,-193,-335,-773,-858,-94,-59,634,-24,545,-277,144,-624,-181,130,889,637,986,394,703,973,700,-551,794,-805,676,944,846,-690,913,-139,-352,-410,-977,-170,-54,162,-437,733,-45,149,-167,277,-279,60,82,811,339,201,727,-117,-34,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,1000,229,-346,124,353,198,-1000,-864,899,-856,293,-384,1000,287,627,-318,376,1000,-37,-123,-983,-244,-885,-866,875,91,646,1000,560,-449,713,575,659,947,-107,818,883,1000,-388,-556,-1000,-1000,181,-970,1000,-1000,-77,-568,-893,764,26,1000,214,764,705,-1000,-149,1000,-1000,1000,-567,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,270,-21,338,732,-520,1000,1000,502,-762,-966,264,-960,817,-492,-794,776,454,163,674,-1000,-728,199,-823,-273,-700,638,752,398,-1000,705,-945,234,1000,926,1000,-439,945,-47,336,335,-1000,-714,-197,1000,-230,1000,499,-1000,-1000,-886,-404,-183,-915,-786,-815,1000,-372,-1000,-441,-747,-11,-269,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{280,1000,1000,-327,-516,837,86,-160,-1000,201,933,-852,-728,-658,324,-395,554,-369,978,221,803,-186,-15,-22,-298,-171,665,409,709,1000,-117,803,-462,-759,-116,-105,97,1000,889,-336,584,169,-127,-444,25,-1000,269,-832,-718,-646,-1000,973,-483,1000,593,1000,141,-634,1000,412,-637,834,-676,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{925,-1000,-1000,-239,126,-576,188,666,1000,637,-1000,-493,-102,505,-418,226,104,-1000,-333,-939,844,-1000,1000,744,-769,675,-924,694,657,-1000,416,-217,735,-1000,-1000,-807,-170,-438,-230,-854,295,1000,0,1000,-1000,1000,-1000,560,1000,657,1000,-822,144,-111,-437,-305,-469,421,148,-816,1000,-1000,-467,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-160,-482,-177,-331,32,100,128,1000,-49,-831,-114,-390,-259,275,-1000,-838,-589,-981,755,-369,1000,281,-522,786,-833,440,1000,-928,403,-945,234,-939,-239,1000,-243,-161,-47,-1000,666,812,180,761,-1000,1000,-1000,-27,549,615,779,-939,-287,783,68,437,133,289,636,-989,1000,-879,82,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(double):org.apache.commons.lang.text.StrBuilder",
            new int[]{509,-118,-463,-1000,-1000,394,-367,1000,-1000,349,1000,740,-308,919,1000,1000,-461,655,-863,-697,1000,-352,1000,1000,-893,-348,-125,470,38,893,103,-650,-171,-1000,-1000,721,-297,-255,821,-494,593,1000,-69,1000,-26,140,-224,-515,1000,-1000,1000,-41,-780,670,568,1000,381,-1000,867,-441,291,-763,255,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,151,106,508,184,-253,586,142,994,804,700,283,-740,521,270,-1000,726,-1000,580,688,955,-28,-670,863,1000,248,301,-521,770,-489,-1000,1000,-548,888,-196,-659,-841,230,29,928,-396,-702,-684,-357,35,881,-1000,750,198,698,1000,-394,866,-527,401,-64,368,-138,15,792,-192,1000,977,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,995,-895,563,1000,504,-630,432,-965,-524,878,493,784,329,1000,-353,330,-1000,-622,-184,-89,-944,-564,-528,-1000,-1000,1000,1000,-427,1000,-833,86,671,-1000,-31,-726,290,633,-636,-262,835,-890,1000,-1000,-1000,-94,297,826,-1000,383,1000,1000,-1000,231,912,-64,480,-1000,-1000,1000,-910,447,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,231,629,-1000,-738,280,192,-472,1000,-770,682,35,891,-360,-62,981,38,-810,-463,-773,-31,-945,-535,-839,-393,542,528,-188,225,1000,-369,857,-591,908,-710,770,-975,1000,-551,456,1000,843,-1000,-210,1000,1000,-304,212,957,843,-796,881,-700,56,-144,966,-1000,251,558,-818,-523,-859,-407,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,151,-1000,-126,-166,594,-592,107,61,1000,-25,854,-975,703,1000,706,1000,-1000,197,-660,894,-560,-269,854,741,-476,931,-481,358,-1000,-1000,-608,694,-35,-1000,-1000,-326,950,-1000,459,-193,-1000,-338,-1000,-1000,512,-1000,798,-941,771,1000,726,730,-527,653,-1000,-688,-1000,136,792,-517,1000,977,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,521,-375,-357,-116,602,-711,-370,152,89,-805,507,-739,367,-664,1000,961,-866,109,-498,733,-715,-151,676,946,-425,557,-863,511,-400,-1000,-506,506,-223,-931,-738,-269,967,-1000,365,-932,-934,-525,-1000,-262,628,-881,508,-865,489,1000,967,1000,-1000,-34,-1000,-1000,-937,684,181,-441,1000,684,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{730,-370,971,1000,-24,-434,-246,-594,471,-168,676,579,1000,399,193,1000,-699,-676,-483,333,-422,-171,208,-598,88,-242,908,1000,217,242,-214,-981,-610,-291,450,-275,208,310,548,99,689,994,-1000,1000,1000,348,457,453,711,535,223,-532,-1000,55,1000,342,-51,-481,-508,1000,-1000,-371,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-400,563,-304,-924,-675,-516,1000,-759,1000,488,-1000,-949,-722,-907,-56,776,-107,-186,-254,319,463,773,580,1000,1000,637,98,-1000,-99,-890,-98,-251,-395,724,-488,708,-367,647,157,1000,-1000,-806,-400,670,552,1000,-828,530,620,-423,-1000,173,1000,-554,-40,96,-558,744,1000,-246,-24,59,-234,493}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{424,280,-785,144,454,757,769,-494,-937,87,-626,766,-214,314,-553,326,834,-716,422,28,400,-722,143,-194,23,-772,931,808,458,726,93,105,-390,-502,-8,-6,137,714,-98,-991,-945,602,848,257,-675,178,-632,-568,-195,-400,724,-19,-842,-361,450,280,-610,64,-644,-612,-499,-616,-915,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,318,-651,230,-445,105,-261,-879,-1000,1000,-251,-111,-735,1000,1000,909,-720,-762,-1000,1000,-958,-634,1000,-1000,201,-1000,-1000,423,-1000,1000,1000,-362,1000,-388,-844,-952,-69,896,1000,256,-678,-740,561,528,-760,1000,-113,308,-220,1000,-162,-1000,-1000,-1000,1000,1000,1000,1000,899,1000,845,-194,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-137,-894,-717,-524,1000,-1000,-808,512,-973,367,-99,-477,-721,457,306,1000,-928,148,-1000,-782,-817,79,169,389,-475,1000,-1000,-522,1000,-846,121,1000,-563,778,-650,-651,-720,-750,673,937,-486,-581,535,1000,1000,1000,479,1000,-928,1000,338,-1000,329,-485,-365,-784,1000,845,-251,165,551,-360,-301,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-680,-919,197,-1000,1000,-541,399,-363,-1000,-882,292,-256,-389,-859,350,1000,633,-221,-459,-206,495,96,-921,1000,-895,-1000,-984,-1000,636,-887,1000,1000,-181,-107,168,-1000,-959,647,307,498,-194,-517,-325,1000,555,111,-176,299,-674,1000,1000,-502,-754,-1000,651,1000,947,185,-92,400,1000,315,453,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{476,362,-925,613,677,174,164,445,-1000,671,72,-708,108,-47,316,-778,1000,-850,475,596,558,-722,1000,323,285,-266,-90,445,665,-1000,1000,-243,624,208,871,1000,571,706,-185,-317,-226,1000,380,100,-44,199,657,-271,-193,-266,663,259,-1000,-1000,80,-49,-350,599,-358,-617,-506,229,-484,748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,349,-1000,-41,-1000,116,-136,-1000,-496,-341,-1000,-556,-1000,1000,880,-566,1000,-1000,-649,-555,-985,665,1000,-660,-207,-931,-1000,1000,-655,-108,1000,-418,807,229,-659,-527,427,907,1000,-1000,-1000,-1000,856,305,-4,-1000,-133,-1000,1000,1000,-267,800,-447,563,-891,947,81,-808,-746,540,538,-800,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,677,544,1000,362,111,1000,-905,243,-1000,-549,871,526,1000,-1000,528,527,-826,428,771,727,1000,-1000,-1000,439,-1000,1000,1000,-335,1000,-459,-947,708,-1000,939,-724,178,1000,-1000,-1000,-1000,-691,937,-330,-345,-197,-1000,-472,-533,-348,-39,193,-1000,1000,-2,-112,-1000,276,-1000,-248,420,-1000,-693,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-220,-408,-766,349,1000,-616,-1000,-359,622,-826,299,602,494,901,61,-664,125,-33,-1000,-53,63,301,669,645,415,531,-679,-1000,-83,-123,32,-39,396,811,121,423,1000,193,89,135,1000,-1000,-176,215,-271,-176,1000,-275,-657,-14,-792,-190,-648,1000,-916,397,-50,99,822,368,669,-1000,446,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{395,-811,440,72,-103,213,334,1000,849,-533,-1000,-853,-308,626,-784,1,-27,158,-144,-250,314,-228,-1000,583,1000,1000,-95,85,1000,-916,-174,821,-840,-39,-1000,1000,-575,-374,-854,-1000,239,343,-1000,817,-1000,-1000,-805,-419,-1000,-782,349,-123,841,-191,132,-753,1000,826,955,-16,42,-244,-1000,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-734,-39,-766,787,1000,-458,-1000,-916,-642,-76,-50,1000,545,41,625,-400,-70,-865,371,-239,-268,447,1000,316,-306,-1000,-633,-1000,-171,-761,-1000,-349,725,694,-190,-394,554,141,-273,283,529,-985,652,-6,935,944,1000,-466,626,691,-756,318,131,110,114,518,-1000,388,-341,100,560,-580,123,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,181,166,-567,624,-483,-513,91,192,-1000,-1000,20,-467,813,534,93,-1000,-237,-787,-1000,-412,299,-216,1000,-1000,-1000,-345,-1000,208,476,636,610,-428,-749,-930,1000,-384,-742,-720,133,-460,77,-914,-388,-88,59,538,-908,-663,456,-1000,702,12,-227,-750,1000,370,1000,-578,1000,332,-974,283,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-181,-301,284,848,-592,-1000,-117,-966,-608,685,-301,-858,-666,-172,54,19,77,144,662,1000,506,-124,273,-1000,381,-254,1000,179,-571,-149,736,689,-447,401,-37,337,-291,-904,-1000,-806,21,899,-118,500,1000,658,-611,19,593,715,-262,42,177,295,-820,6,509,-21,1000,-77,-514,381,64,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,304,-982,250,-76,-33,78,622,-1000,-25,-819,418,1000,-615,-1000,-84,-497,-1000,-53,732,-615,-990,-841,721,531,299,755,347,101,32,319,-630,-899,-288,646,701,-253,176,-822,1000,-384,-1000,263,-1000,-1000,-454,93,32,-1000,-48,-652,-133,803,-1000,-542,1000,10,1000,90,669,-795,-699,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{341,-313,-1000,296,284,-271,63,-936,414,1000,-1000,-36,800,-622,-512,427,635,-758,-1000,-413,40,1000,-526,-824,538,432,-958,-108,-324,-648,-1000,1000,444,0,-464,1000,-619,312,375,-771,513,-217,403,-39,826,-169,-420,62,0,413,-108,519,-1000,165,-643,-991,-527,-721,-80,-300,-502,-131,-899,629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-652,-79,-869,-391,44,-816,538,827,-497,796,-979,-946,-611,-936,640,339,389,-125,-172,496,-222,-229,670,160,109,-374,416,-607,555,-895,450,246,-360,464,70,-817,-359,-600,-153,584,766,656,85,-284,-636,863,-41,452,888,703,269,-161,-877,909,-541,137,235,-175,490,-636,-334,314,-474,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{999,699,-156,-643,-106,-215,-209,-1000,-322,176,564,-486,188,416,442,-461,1000,36,86,-435,114,57,-343,-679,-86,-1000,359,265,996,-845,-962,-880,721,951,834,-119,442,175,-305,-128,528,-916,-534,134,-473,-297,-259,-773,-596,672,-468,-672,-318,679,1000,606,-332,2,212,665,746,-747,219,123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-478,-1000,-1000,1000,-127,107,-754,1000,-306,911,1000,464,-481,359,426,1000,286,1000,-750,-491,-558,-967,362,-1000,964,955,-813,1000,68,396,810,1000,-1000,-123,-1000,438,1000,741,116,1000,128,767,-238,538,-696,241,-772,-1000,1000,-1000,68,-547,481,-189,1000,825,-115,1000,804,1000,-1000,697,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{903,448,172,-152,-671,-580,-479,-52,595,-170,628,370,-86,-498,254,353,328,334,-143,38,449,449,-429,-151,-699,421,327,585,-58,-19,214,-81,616,-785,66,-991,52,-539,532,185,-188,108,96,-415,204,78,201,-1000,-869,929,-52,594,648,1000,1000,29,-538,-167,1000,559,-1000,-302,421,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,56,-131,184,-145,-746,131,-724,-348,-569,-83,217,1000,-149,-643,401,863,-198,533,-410,266,813,-1000,110,864,544,75,551,-745,488,-560,1000,826,-1000,-763,-743,452,325,441,708,-853,32,-218,470,-255,762,-216,151,-583,19,240,1000,-251,1000,1000,-327,903,367,1000,-318,-338,-506,-249,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-205,723,-42,-468,-148,90,-563,-987,-230,112,745,289,649,280,320,-875,848,155,668,313,-585,-254,-880,4,1000,-83,664,1000,645,-422,-556,-260,1000,591,164,-886,611,165,-368,-12,-472,-1000,-650,671,-6,840,-224,-183,-1000,385,-400,-88,-179,900,729,-616,-95,325,994,-364,-701,-1000,222,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{568,518,160,1000,-413,-966,-978,-555,-611,-1000,92,33,1000,788,-365,1000,532,-22,-1000,73,-803,1000,-995,222,1000,242,-842,-305,125,1000,-1000,953,769,-1000,-1000,-1000,-1000,-1000,313,220,-116,-258,1000,-741,974,799,-269,-94,-1000,1000,1000,-692,-875,735,812,-882,-537,282,-321,-82,111,1000,-103,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{679,-23,784,-461,-413,-264,65,-347,829,204,446,33,-628,-103,137,25,-662,412,-10,807,1000,155,207,-285,-825,-503,998,158,433,-559,947,-865,-284,262,718,354,336,-303,124,202,210,433,-289,41,-361,-665,181,-614,285,-271,-267,-692,-523,369,274,347,135,-178,-321,961,237,-302,975,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-336,469,-819,-413,36,-45,-1000,829,432,-384,-182,-232,758,-266,-182,-516,69,1000,920,259,-213,-37,-1000,-1000,-1000,1000,-1000,1000,-668,1000,-949,-85,1000,663,958,930,587,-754,-77,1000,-1000,-289,370,316,-1000,161,873,285,-1000,-610,-1000,-468,369,274,667,854,486,-1000,608,1000,-1000,975,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-93,-319,1000,778,-902,-613,703,579,1000,-217,846,-612,-81,-993,769,411,-68,1000,1000,-1000,634,-147,-687,453,52,1000,251,1000,365,651,687,-546,595,105,-821,-600,-90,487,1000,-745,910,-1000,-1000,48,-728,-1000,715,-1000,-1000,13,-251,-495,71,-626,-544,1000,854,1000,-879,1000,849,-866,-768,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{985,-449,-942,-860,-847,-1000,-476,287,-1000,-671,-1000,-188,-432,461,-504,809,-1000,638,-953,-341,-741,13,-248,-1000,-95,-1000,1000,-1000,1000,-682,-137,277,889,447,1000,-1000,-1000,-1000,-1000,580,-1000,-1000,1000,-711,-247,1000,-823,1000,168,1000,-711,579,1000,1000,722,-1000,-1000,-1000,-69,975,763,1000,-275,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-332,204,633,501,-203,-724,-1000,-26,1000,254,895,-498,-412,394,8,-1000,752,-81,256,-653,1000,1000,-1000,-269,-111,257,-466,469,230,835,-184,651,-446,680,-1000,-1000,436,439,1000,-286,1000,394,-967,-236,7,-1000,14,19,685,613,1000,643,738,454,653,524,-1000,1000,233,-257,-402,-125,-785,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-902,-364,591,91,65,-992,-624,-329,700,-77,-708,-688,-298,196,-556,-176,-640,252,-590,-493,682,999,-109,-300,-587,-708,863,84,744,910,-62,167,-600,-767,-97,-664,-37,-140,-976,555,-203,-683,569,349,-286,-325,-752,63,-305,447,-239,548,347,397,522,412,-420,-899,-271,749,-4,968,-533,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-1000,-1000,547,998,477,-674,-715,-1000,-617,517,-271,-42,-1000,-1000,-96,-1000,700,391,436,243,1000,-734,1000,388,-1000,-954,300,-984,-411,1000,1000,538,11,-847,-580,401,-1000,-1000,-642,553,-109,-898,473,-1000,744,636,1000,116,771,350,-500,-570,-109,-991,619,643,968,-1000,-1000,659,-303,-783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-105,624,404,-157,673,-1000,709,376,698,508,1000,-618,-684,19,593,290,-587,-139,547,-704,673,763,-841,-167,-158,520,-201,-212,-510,400,119,-340,-222,767,-532,-751,1000,-905,105,459,693,1000,-1000,-648,-1000,-1000,-103,575,-1000,-388,708,-249,-746,-1000,1000,635,-1000,652,-238,580,402,-1000,-671,362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-273,-364,400,91,-709,-992,-624,-329,-1000,-77,399,580,-298,-439,1000,1000,271,252,74,594,811,-534,-109,148,928,-566,-539,400,390,-153,-62,-269,1000,-374,-188,-123,-1000,1000,486,-728,-203,-775,903,303,346,1000,-752,-215,940,318,-239,94,857,456,522,19,508,400,254,-131,-1000,1000,-533,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.String,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-382,473,543,1000,370,-1000,-1000,-640,452,-829,828,-512,-932,547,-484,-1000,368,1000,1000,-619,868,1000,-1000,870,15,-745,-1000,-18,710,727,157,112,229,450,-1000,-615,1000,-752,699,-300,1000,1000,-985,696,-545,330,1000,-661,-715,-1000,1000,-575,-1000,-1000,1000,1000,-1000,1000,563,975,998,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-718,-1000,1000,-56,252,807,275,99,1000,-1000,-1000,1000,-1000,-333,546,-1000,417,-1000,-1000,-4,-352,918,279,-1000,-684,304,-1000,-1000,1000,-490,589,991,1000,-1000,-294,-1000,1000,-1000,-107,1000,1000,1000,-951,472,1000,1000,1000,-482,925,1000,-482,69,-1000,-1000,1000,1000,-664,-400,1000,-505,-1000,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-450,366,729,-96,-568,1000,-994,-819,-559,-381,-1000,1000,122,36,-163,433,111,-742,-65,-219,190,-330,-647,210,-230,-20,-713,166,431,280,-84,719,-465,-152,-82,1000,-44,883,-424,683,-1000,-769,-517,-426,713,-956,-1000,-155,1000,-788,-24,-968,170,-1000,-178,368,428,56,832,364,516,468,-331,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-693,-1000,-1000,293,292,-1000,559,948,-815,609,328,-1000,877,395,-912,-296,-756,880,-177,-1000,-616,456,-313,1000,1000,-1000,1000,-698,-1000,-1000,672,-849,-221,-938,1000,-666,-898,-1000,820,-1000,-925,1000,218,1000,-322,1000,1000,970,1000,-1000,205,1000,-1000,-1000,-1000,-39,1000,-39,-934,-133,171,-1000,-426,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{445,295,910,-140,919,492,-311,705,-914,161,-1000,85,-155,-634,-402,-15,769,-265,992,139,140,-1000,-209,375,-724,-127,230,-103,375,856,242,-848,574,-1000,-1000,520,-1000,-59,466,467,-1000,-266,1000,1000,703,-413,-572,458,198,-204,341,-247,-651,-944,-211,-795,428,874,-411,1000,169,-536,423,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-151,850,979,47,-486,895,-458,600,601,-876,560,286,306,386,-566,276,-130,-269,-393,627,629,-857,-794,107,271,754,-866,-688,601,360,-608,426,-185,516,-199,-531,-79,520,-196,949,-225,-809,-425,469,-16,-481,-735,5,-146,-43,894,-649,489,-567,127,-485,183,5,994,-239,322,468,-568,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-540,-862,488,-556,513,-148,-696,-812,-267,1000,-308,71,-358,960,72,601,-1000,-732,334,-67,-904,942,-1000,566,818,-300,489,420,-740,-1000,186,-4,-350,457,1000,-86,-156,-593,-43,-516,232,554,-50,-142,-317,833,343,182,198,-247,-400,278,-787,-1000,-935,-743,124,-108,-556,-77,-660,40,-916,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{-192,-1000,540,-392,1000,1000,692,-453,11,-476,179,-1000,-764,1000,826,889,205,-1000,-195,225,406,-1000,70,1000,1000,584,-229,-1000,-762,-1000,-79,638,356,1000,-208,-592,-1000,47,1000,-998,-1000,355,565,1000,-1000,1000,710,-837,988,-1000,1000,-866,-1000,-1000,-1000,660,1000,1000,-1000,-1000,1000,-525,-1000,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer):org.apache.commons.lang.text.StrBuilder",
            new int[]{676,98,-208,71,136,33,994,1000,-710,123,330,-1000,-135,206,-204,-418,314,-669,641,-220,333,617,-538,-178,161,-648,580,-256,94,-497,811,-454,900,278,665,-308,-399,33,368,11,-124,9,223,1000,-526,742,370,173,1000,-922,360,596,-583,-17,-856,-222,353,848,-1000,-192,176,-48,229,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{642,-508,-524,760,-861,738,35,704,282,183,-240,-773,396,874,1000,-226,-1000,-108,-81,1000,-147,-1000,-677,515,601,1000,-561,-108,-1000,51,-1000,1000,155,-1000,1000,-1000,-753,301,-307,461,298,688,783,-1000,-481,-867,-1000,563,965,-1000,637,-449,-421,-172,-676,300,451,-55,-1000,-194,-614,490,-221,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{735,1000,-139,934,884,892,-1000,142,-606,1000,-1000,-324,51,606,928,-1000,458,1000,874,-1000,-938,-924,-1000,557,457,1,697,881,-1000,-158,-1000,621,60,465,-570,627,256,160,291,-1000,258,-61,-983,16,-86,-1000,-453,-661,325,504,-270,-1000,45,-207,-768,-149,-1000,-429,-259,-225,-604,482,213,716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-213,-252,-526,-344,670,1000,467,291,-482,-1000,-1000,-337,126,-337,-680,442,39,598,-992,-35,-1000,-366,767,226,33,-751,-536,502,170,1000,563,947,758,1000,-787,1000,1000,-99,964,-1000,857,591,522,762,-444,573,325,323,1000,504,483,-730,-1000,633,535,191,-461,850,735,-777,163,1000,-1000,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-151,375,-896,1000,-396,-12,-891,1000,-586,-121,152,1000,870,799,398,-649,293,-1000,62,614,-1000,562,-154,-839,855,460,-809,-665,1000,-507,285,55,46,504,380,224,-142,-498,106,-117,-745,742,1000,593,-466,374,-702,508,1000,405,-313,1000,766,535,-13,-1000,-738,334,698,-111,-176,-493,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,23,-98,1000,-35,1000,58,386,-768,1000,-967,-1000,-758,567,810,-612,-822,1000,782,-596,-673,-795,-103,1000,-295,500,97,284,-32,183,-259,937,1000,-565,426,-449,-1000,-1000,319,-647,922,882,-786,-1000,-1000,-309,-47,525,1000,180,705,-939,-1000,-839,-1000,37,-102,-662,-1000,-1000,-100,634,651,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{433,-571,-600,1000,-936,673,-136,924,-279,-42,-584,-267,-368,890,1000,-389,-672,-107,746,1000,-497,-1000,870,751,984,255,-336,102,383,-26,123,1000,145,11,146,-717,-517,277,299,207,706,1000,883,-726,-572,-550,-1000,946,1000,-748,46,134,-425,-770,-355,-121,415,353,-819,37,363,404,-325,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-847,176,-784,23,-1000,263,-797,926,-402,589,637,111,-65,-21,372,-541,-454,-410,-242,1000,346,886,-572,-428,923,429,-1000,-596,479,-333,770,-1000,-237,653,-107,-1000,-543,-623,1000,-298,584,1000,231,109,244,405,508,-31,1000,299,843,1000,-245,647,-288,-464,-875,-809,1000,749,-858,424,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-213,-126,-772,407,-396,-287,-404,440,-117,370,14,951,-467,-337,-171,-522,196,-67,513,87,-421,923,-537,-308,996,-33,931,-730,-901,-499,-751,947,-836,460,-787,-58,-219,132,242,979,384,735,783,-99,-386,99,-700,980,229,651,165,671,995,-832,768,-593,-235,-318,242,459,871,-688,370,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(java.lang.StringBuffer,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-682,-712,-651,1000,-499,-298,-741,412,-58,704,334,256,459,-329,146,-686,134,-531,1000,577,41,840,169,-308,1000,-18,943,-946,-1000,-678,-722,1000,-1000,8,-1000,-634,-574,77,404,1000,-482,874,818,-159,-500,-274,-848,609,206,723,-1000,792,1000,-1000,389,-799,-274,-242,-86,630,1000,-380,798,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-93,-930,854,-865,358,815,-944,299,580,-583,972,-419,-611,-912,-968,1000,431,-763,31,954,-923,-902,478,-580,-767,-243,1000,-741,-580,1000,-574,43,613,-707,-917,364,-157,-627,-542,-580,624,683,1000,-148,916,-562,843,-935,-1000,-950,448,-989,700,127,-593,-1000,919,-913,-729,-411,-711,-15,79,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{7,-589,-404,698,438,121,-527,-256,-902,290,516,-483,686,-583,-849,730,346,55,-877,575,-633,-393,-982,626,-400,840,-478,23,839,74,-242,296,-472,-116,-741,-540,60,337,-733,622,-332,-729,-126,212,455,-68,269,-815,-551,-578,-172,-945,-253,-192,124,-533,791,-487,61,-843,-404,-329,302,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-314,393,210,494,799,146,-229,-3,259,16,229,-1000,301,-247,509,1000,707,-69,-56,-302,-440,-1000,835,328,1000,-1000,-914,-822,299,-440,1000,-149,-499,-1000,1000,-449,-1000,-1000,-845,840,587,446,-506,377,-284,436,-1000,112,-1000,-280,-813,907,-1000,-563,-128,430,-354,-100,-1000,-661,-683,-549,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{158,-1000,-528,232,567,297,-682,-1000,-1000,-231,1000,-171,462,-785,-1000,1000,443,55,-890,1000,676,-135,-1000,626,-501,1000,-478,-410,513,543,-468,1000,-1000,-907,-1000,-464,1000,110,-733,432,-1000,-764,845,665,1000,-102,1000,-1000,-690,-1000,-385,-1000,803,-699,124,-1000,1000,-159,-604,-1000,-907,-976,720,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{661,-351,854,499,548,699,308,-900,-378,509,972,568,313,1000,602,135,190,92,368,-65,656,-152,-686,492,-530,-303,393,-644,-946,-819,603,811,-837,223,-501,-1000,257,395,-1000,1000,624,-117,-1000,-148,-616,853,460,-391,475,-101,448,-189,-694,127,-1000,682,577,-1000,242,382,-19,-655,-377,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-476,-1000,-98,-220,1000,225,363,607,1000,-1000,-315,186,-1000,416,789,1000,1000,686,924,525,54,-1000,-485,-1000,-64,657,1000,-541,-1000,938,-657,1000,342,913,-594,1000,-146,-1000,911,-1000,1000,1000,1000,-1000,-645,-763,134,-24,358,74,-887,923,1000,-958,-876,690,-30,1000,-1000,-1000,-986,-593,519,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(long):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,593,552,733,115,-664,279,-574,-1000,409,49,-682,960,525,28,281,-652,-232,108,-836,283,346,-698,621,585,326,-237,276,917,1000,7,-157,-880,-81,157,-947,1000,841,55,1000,-119,-623,-175,314,-422,1000,539,766,460,504,-463,-285,-952,20,-201,194,1000,-740,490,-492,-112,-672,175,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-220,-177,373,-822,-933,-1000,-691,-413,-923,554,611,376,1000,-10,-1000,-1000,-516,103,-277,-636,-383,-144,2,477,-110,838,-339,-191,310,-369,-345,-528,452,-411,-607,-3,455,546,-87,1000,-1000,509,-44,995,1000,-851,78,751,1000,-372,-547,-338,-618,-139,-1000,-964,-1000,373,667,305,-543,-1000,223,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{793,593,1000,887,564,325,-931,352,962,-749,1000,-384,262,913,1000,444,-107,508,944,-167,1000,362,-1000,1000,-184,-735,1000,-1000,-690,-1000,1000,1000,-116,817,-370,1000,472,-1000,614,-1000,1000,-1000,-1000,226,-371,-532,-1000,-54,-15,1000,1000,-123,103,726,123,108,515,-946,-324,1000,-206,1000,154,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-462,1000,1000,628,-6,327,-639,-413,-453,550,590,-455,801,1000,176,-80,1000,1000,1000,411,430,1000,-1000,977,267,267,10,-625,-387,252,347,-528,1000,-834,653,1000,489,-1000,-911,-1000,188,151,-1000,1000,1000,-1000,-1000,-14,-107,-446,181,-296,-217,-1000,968,-964,1000,-1000,1000,568,947,512,223,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{476,566,463,1000,1000,1000,-119,384,560,-1000,380,556,-777,562,1000,121,-714,354,-48,-671,-526,1000,-1000,-266,1000,-594,546,-1000,121,-139,239,837,-791,671,565,-457,-1000,-1000,-224,-1000,1000,-813,60,-730,-1000,1000,-355,522,302,-1000,36,368,1000,1000,-46,1000,551,-842,-560,508,598,1000,-936,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-572,98,1000,155,405,-1000,729,721,-1000,265,761,330,-253,460,323,219,1000,355,750,-1000,-238,-613,-763,877,638,745,1000,-224,-813,-869,473,364,-154,195,-489,932,350,65,-1000,-640,784,-597,-900,1000,-707,-834,-293,98,1000,906,-198,133,-932,-1000,631,-1000,-122,-735,-411,666,765,101,-992,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{604,-175,1000,-392,192,-830,63,712,273,-181,647,115,603,-492,323,-295,38,724,303,-1000,-238,-323,-763,877,-215,-128,1000,-653,-247,-1000,309,140,-1000,-306,-1000,400,-1000,806,-345,-185,784,-597,-667,150,-1000,-185,-293,726,945,1000,-173,556,-831,-374,-219,-1000,-661,72,-734,666,-325,101,-623,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{996,1000,-710,1000,-177,-783,159,-385,-721,147,344,-38,277,-267,-696,-1000,64,-1000,-696,-1000,-678,1000,-776,479,658,-664,282,-1000,-1000,-215,486,562,540,916,-463,106,1000,5,1000,244,172,-408,604,-755,898,-692,356,278,173,1000,-330,18,-50,841,-172,-771,-337,589,57,1000,-67,755,-620,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder):org.apache.commons.lang.text.StrBuilder",
            new int[]{-634,-216,-411,486,405,1000,858,-136,-67,-1000,851,1000,741,820,-824,-661,-1000,-73,-1000,-1000,80,-613,-552,-987,-1000,417,474,-78,-1000,-309,-1000,-193,-804,212,-733,-1000,-1000,177,-315,-1000,784,-754,994,995,-1000,1000,357,641,742,-111,34,-106,407,1000,-1000,-943,-420,-248,-1000,-279,270,591,804,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-904,-963,888,-73,840,-758,864,575,-780,-677,217,-339,911,-938,-677,830,-485,672,-177,822,-281,371,-306,-48,-786,-835,991,282,-519,842,-513,461,-901,-937,847,187,-117,-60,345,741,887,-505,644,-914,40,-995,726,-326,33,-604,617,292,-30,564,-712,12,-765,777,803,646,-936,-810,609,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-625,-283,-877,654,-7,572,-295,-636,-547,551,-96,732,-511,1000,547,-501,701,-99,-479,-1000,-99,-380,627,70,-391,1000,-604,-109,251,570,-980,-443,-56,-50,-1000,249,498,-312,-416,-860,-344,588,-1000,111,-316,-672,-228,-323,-1000,-814,481,-919,1000,234,-670,522,393,159,-130,-857,-42,1000,890,-440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,500,-911,296,196,-83,864,-159,848,-473,217,-1000,-32,-938,-1000,830,1000,1000,1000,-178,657,-874,-27,-106,291,-1000,334,-155,780,994,1000,233,207,-937,1000,298,-210,-294,136,148,478,161,249,-43,-273,-226,1000,442,1000,308,-1000,457,-768,-340,-329,-675,-765,217,901,1000,-394,-475,609,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-184,371,159,193,-889,17,-637,-168,-232,551,-21,732,1000,-398,-1000,745,-201,224,971,-423,581,-368,1000,-291,-451,434,-904,404,917,-651,-88,-171,-478,1000,-1000,126,-450,-767,-1000,106,-191,-81,850,274,-637,-1000,-55,-161,-153,-497,154,419,1000,-69,-1000,702,744,-352,-35,-570,399,-520,-725,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,499,758,1000,-201,-72,539,-1000,-979,900,-386,448,-883,-7,-515,142,-315,-162,445,-301,136,-1000,674,-469,-1000,-88,934,-1000,1000,-1000,-280,-1000,-752,-869,1000,758,1000,-784,110,1000,202,-402,-1000,-89,-552,1000,-669,345,1000,1000,-232,-740,-178,-262,-569,-1000,1000,-424,-386,-1000,446,996,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-751,293,-563,27,563,503,-889,741,-335,324,646,1000,-91,-711,-471,-184,1000,507,-491,-199,-190,42,-603,-396,792,61,-232,338,-756,-283,199,556,-72,1000,-453,1000,652,421,-926,-1000,565,607,-350,-1000,-211,-359,670,950,1000,898,-338,-1000,-905,668,-65,1000,-453,436,-1000,-1000,-108,157,-921,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-727,-53,1000,981,399,-876,1000,-632,-732,912,628,970,-606,-137,-1000,1000,98,-112,-535,240,-599,427,639,-1000,-482,-603,1000,357,947,-797,-968,-992,-740,-1000,1000,-63,1000,-1000,32,-111,1000,-901,-659,28,-24,1000,-1000,-906,47,871,-1000,914,-127,-560,1000,180,1000,228,-1000,-19,794,608,358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-225,1000,-725,177,14,553,-1000,207,65,139,212,1000,911,-87,-854,385,400,354,-294,-799,8,371,-90,-529,803,-304,-761,747,-229,-1000,716,461,156,1000,-90,187,-117,-163,-1000,741,-78,1000,55,-178,-304,-995,223,1000,672,329,-1000,-590,-30,241,-17,12,-17,-466,-955,646,582,-810,-1000,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-832,-727,-1000,787,513,944,-242,22,-851,354,306,1000,970,760,924,-990,1000,-20,-896,-1000,-141,-245,258,217,-301,888,-744,224,-84,699,-1000,-653,-193,-758,-1000,488,488,185,-742,-1000,-656,928,-1000,592,-337,-398,108,-526,-1000,-830,796,-1000,1000,275,-187,665,41,411,-77,-1000,-161,1000,1000,99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "append(org.apache.commons.lang.text.StrBuilder,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{875,-283,511,-614,789,-563,674,-822,628,-354,-299,476,970,-295,-811,521,-1000,752,-898,586,-468,118,-119,-288,-152,-897,1000,206,-9,-909,1000,-443,-367,544,-1000,-1000,-909,-1000,160,-860,-344,588,943,111,250,619,71,783,456,-814,481,681,-701,234,-269,-660,-200,-773,655,-857,-42,-1000,145,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-617,1000,1000,889,404,1000,-1000,-1000,1000,478,-1000,1000,700,232,1000,1000,1000,-1000,1000,-686,376,-321,853,-153,1000,-899,78,281,717,-1000,417,-612,1000,-375,1000,-499,186,-1000,-1000,-1000,-1000,81,1000,400,-969,1000,274,-1000,-1000,-196,-1000,-1000,835,167,585,472,-1000,-1000,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-457,-38,-684,520,269,374,1000,-279,823,206,-579,511,628,-518,407,272,-884,-903,323,346,464,-572,-39,420,-534,-630,204,228,193,988,-290,-1000,592,930,-888,-26,466,-1000,667,390,-728,1000,478,124,-489,-69,2,212,761,-597,-838,317,-363,-552,-368,515,-405,320,-1000,-37,-357,-417,664,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-234,-888,-686,292,1000,751,-514,1000,-333,-716,277,974,-1000,1000,436,-674,1000,1000,1000,-1000,143,-501,554,-747,853,-153,1000,-1000,-155,-339,1000,-431,33,-1000,1000,-730,-159,-493,-282,529,-83,-1000,-795,335,990,-263,-1000,957,-212,138,-838,-663,-862,-754,591,66,639,207,-677,-1000,-617,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-120,752,325,-985,-1000,-561,223,-914,849,636,-1000,939,1000,-1000,-289,218,-1000,-1000,-827,1000,565,20,-747,1000,-1000,-882,248,-1000,1000,630,-1000,-783,1000,689,779,828,-271,-1000,1000,1000,-161,1000,1000,-142,-1000,-376,-720,80,594,-103,-1000,1000,1000,-121,-1000,487,-163,-1000,607,1000,524,-360,997,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{742,248,-368,368,89,126,-486,480,-180,-142,633,-869,110,1000,-410,-1000,848,711,466,-946,-1000,-57,832,-995,412,-882,-86,-670,-816,-630,20,-439,1000,-880,890,595,-40,974,295,205,-198,-1000,-278,-171,603,426,-408,60,-857,904,-1000,-435,333,-1000,-683,497,-519,435,-294,-867,-703,610,-767,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{632,584,744,535,284,-301,-51,717,582,-677,476,-22,299,882,-75,-518,636,587,-35,-536,308,211,886,-560,596,-674,-146,-354,-737,-34,-635,-895,-909,-209,842,-441,-735,950,-771,-547,-211,-957,33,111,736,-137,315,-674,-796,931,-190,-321,-405,197,-429,69,-962,-206,-681,-799,-372,179,-980,-961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{584,980,891,-7,230,600,959,-26,-596,-415,984,1000,325,-423,19,240,316,973,612,-127,1000,-1000,186,739,459,-819,458,915,-845,863,-65,-1000,-73,394,779,-288,213,-741,642,-950,-24,-792,-400,-670,257,670,1000,-1000,-337,-369,-151,575,-831,589,-375,-279,-680,645,-32,528,-252,-6,-419,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-580,-911,643,267,-859,-1000,1000,208,59,143,-1000,669,1000,273,-1000,854,20,-964,-524,469,-988,-323,-1000,180,-230,-339,-1000,115,201,1000,-1000,-638,-1000,1000,-355,-1000,988,-208,-283,532,-20,381,257,-225,-459,-1000,-199,-1000,1000,1000,-1000,1000,385,-1000,1000,-1000,-400,-350,-384,-1000,-100,-923,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{695,815,-468,-871,-328,1000,259,-306,-1000,-700,-1000,248,177,-1000,1000,-338,644,61,1000,-1000,-1000,326,922,819,334,1000,1000,-375,175,58,487,645,-502,-525,-1000,1000,-977,1000,-422,329,1000,-1000,1000,-355,-604,-744,-1000,49,-311,603,626,-383,69,-1000,-317,1000,96,-1000,-485,957,697,-793,-232,-447}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,293,-726,991,445,739,236,140,-716,435,-714,-764,-1000,-1000,-148,-914,-1000,443,509,-16,-1000,1000,998,-471,955,-779,-880,732,-4,342,65,1000,771,-1000,1000,-781,-1000,415,-79,148,513,-1000,554,689,-589,1000,1000,763,127,-1000,444,-469,305,-207,-944,-842,-805,-1000,-611,-234,1000,-193,344,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{184,-954,-537,857,172,391,607,991,-921,1000,-3,625,-626,400,-591,433,-523,165,509,-1000,165,1000,1000,-1000,383,-1000,-969,1000,-119,1000,-1000,554,278,-529,1000,-1000,-651,-315,29,1000,-383,-1000,115,1000,189,1000,1000,-157,-25,-1000,845,58,256,-452,-1000,-1000,-701,-1000,1000,-253,1000,65,1000,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{905,766,-418,1000,-576,-566,748,1000,-1000,1000,-208,672,-504,61,-1000,-612,-884,-41,-1000,-448,-1000,461,1000,950,1000,-150,-1000,-451,119,805,-738,672,-1000,177,-443,-359,-1000,444,-206,-695,42,-664,-791,811,-1000,1000,1000,-146,-725,236,946,753,-1000,-163,145,-725,-506,-778,1000,-1000,63,20,-1000,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-750,428,-96,89,-195,-487,549,992,-709,1000,-310,891,478,-64,-1000,-594,42,350,-1000,522,868,225,-47,-833,1000,-264,-1000,-834,-247,269,-771,1000,-943,283,1000,108,417,-1000,289,-564,-51,598,-1000,1000,-1000,-1000,1000,-465,-90,-640,192,1000,-428,444,753,-1000,-963,-536,1000,-1000,290,239,-395,738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-852,-781,812,808,595,-669,798,942,-970,97,15,-342,881,895,367,448,617,30,-553,890,442,604,926,-112,-998,-1000,-1000,991,608,152,-439,-685,-234,58,707,-859,1000,-1000,-847,1000,-281,-539,-1000,41,417,362,713,540,639,-202,-142,-241,273,-787,-3,-732,683,103,1000,-1000,-105,0,812,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadLeft(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-46,-804,-842,-623,198,139,21,58,-192,-1000,1000,54,-1000,816,-1000,1000,295,428,-1000,-1000,1,686,318,1000,1000,969,-629,-128,1000,-1000,803,501,-215,-1000,1000,-911,-16,-314,-444,1000,-611,918,-373,-1000,-87,-1000,824,-618,1000,710,334,59,-1000,279,1000,-967,-1000,-585,472,737,-860,-1000,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-479,-1000,430,-46,753,153,-110,638,134,-996,890,402,1000,-866,-798,-883,129,-147,599,-91,-1000,124,522,1000,-1000,293,-631,-275,850,-56,-118,-930,367,-834,-671,-160,984,731,154,-1000,455,-617,277,-65,-782,-1000,280,1000,343,496,261,-658,508,80,431,813,-1000,-887,96,-663,-590,839,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{935,-173,818,-65,324,-817,1000,-2,-319,585,894,-1000,228,-271,-331,-1000,696,-634,-386,-97,167,487,263,994,857,1000,136,-313,-1000,1000,-286,-909,-273,-203,24,-302,-302,835,-249,311,305,-595,849,-844,-892,-483,-522,-700,-1000,411,1000,-31,929,-79,-844,-152,-212,-1000,-914,-257,-358,802,-924,608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{705,-51,764,-265,102,-1000,252,272,-817,842,1000,-164,-271,-357,-383,-879,1000,-496,-779,-462,915,-710,450,476,944,1000,493,-1000,-949,1000,307,-817,-795,89,583,-110,-870,438,264,481,477,-614,982,-658,-538,-138,-796,-1000,-1000,658,1000,-484,826,334,-945,-732,-388,-1000,-90,561,-1000,-85,-10,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-571,-19,663,214,414,243,247,-223,282,-545,-134,-104,332,-162,317,-820,667,-708,-785,282,477,-462,205,-272,184,233,-575,-720,-458,712,209,-377,-548,764,531,454,9,-410,709,78,-138,-449,456,360,-340,-526,12,-1000,-59,355,580,-763,73,-285,245,-733,375,-373,1000,179,259,22,414,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-842,-1000,109,433,1000,356,-143,-233,376,-483,75,-1000,954,86,6,72,-17,218,1000,-905,401,587,232,-265,-1000,258,-155,1000,575,-1000,-1000,238,-199,-688,-1000,-82,1000,-225,842,-634,127,-1000,-614,713,-1000,-864,1000,296,923,684,-20,-511,-667,505,777,1000,525,-316,354,-822,318,407,-1000,-440}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-2,-262,350,1000,1000,296,204,-1000,192,-638,-83,-357,540,495,621,-255,1000,257,-1000,646,292,-46,17,-570,-1000,1000,-685,-132,782,67,-2,602,-1000,504,-881,432,-628,-459,746,-545,376,562,36,466,-438,-1000,-65,53,-324,389,-231,-1000,-238,-941,1000,446,-253,-713,1000,-83,168,640,-904,-49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-225,-406,-992,-466,129,637,-858,-510,1000,-231,-1000,-118,-555,-261,294,-515,111,443,-400,725,726,416,884,-1000,-972,318,-793,-232,-31,-100,148,-859,-330,782,1000,385,-1000,-467,-1000,-885,632,1000,211,-1000,330,905,254,-119,44,355,31,762,-657,-1000,-484,-954,-68,867,-471,-537,1000,1000,826,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-57,83,-463,-666,-322,-486,-766,326,-466,798,139,-192,-296,279,808,419,-772,-115,244,1000,184,-146,-148,-615,1000,646,137,-379,-251,-661,742,-489,-17,-680,-600,-705,-363,-34,-458,1000,-119,-705,-1000,402,-557,1000,37,-132,-75,799,262,-730,-552,660,104,-685,-358,-87,-713,-800,466,-891,-1000,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(int,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-294,207,1000,276,274,-735,103,-310,205,-657,837,-317,-165,254,69,115,1000,-717,-119,1000,788,617,-714,551,-452,1000,195,187,-1000,-36,-44,-523,-671,177,-554,333,295,-1000,826,-791,-446,-45,-934,-497,715,400,264,512,17,-100,-618,4,603,813,459,-425,-12,-425,53,832,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-845,248,-743,226,813,426,-951,-375,-708,-1000,622,-169,-310,98,730,756,-875,699,-126,-824,-283,675,418,-170,712,557,261,376,-775,-225,888,34,-22,182,-393,804,688,-433,98,1000,670,480,-539,-569,-538,-738,-745,-731,431,79,-106,-764,-706,244,-743,256,-870,521,687,-308,-857,-208,810,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-168,-128,-611,458,949,347,-205,1000,-838,40,491,589,-920,160,784,-206,-1000,-950,1,1000,456,310,-68,-909,-770,134,-589,124,-1000,-569,-155,957,1000,-113,1000,57,1000,-186,-49,-114,-398,305,101,-421,-583,-210,-136,-103,-645,-1000,-519,-150,757,-34,141,651,-1000,670,-22,-179,-267,-817,-165,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,816,308,568,201,667,1000,-49,818,659,-1000,69,-449,1000,269,-731,12,144,-155,-498,900,-660,-647,-337,-115,-1000,860,400,-166,-509,205,-81,334,-221,205,-286,96,179,660,225,-86,-223,-808,-465,1000,1000,-500,1000,-242,-383,-911,1000,-481,350,1000,481,871,597,541,609,977,-1000,1000,424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-187,-90,-1000,200,484,259,1000,952,-1000,-521,1000,-749,-540,1000,-690,-1000,-698,69,-650,428,555,-1000,-1000,-1000,-980,-576,-206,-950,-1000,-582,377,-976,-868,92,172,1000,-803,-619,397,-37,-908,670,1000,49,-153,1000,465,328,-1000,-204,-1000,1000,424,66,-859,-702,791,113,439,-482,-779,-974,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-878,-1000,560,1000,396,-222,1000,542,-258,-656,389,417,54,-509,505,-131,-366,-987,-537,-1000,458,70,-525,-369,-470,-33,372,-583,-730,-532,-589,65,-22,-1000,458,420,464,91,-528,843,1000,-176,589,853,309,102,693,1000,18,-49,-1000,-1000,776,847,-199,-531,-373,45,-333,294,249,-1000,-743,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-27,209,992,120,-203,541,-1000,703,77,-1000,-668,-49,498,313,-1000,-91,449,-1000,-1000,-535,-1000,-65,-92,-268,-47,757,-947,50,-1000,-36,310,-48,-235,10,1000,-1000,-597,40,1000,-196,-458,-366,334,301,11,558,-974,-1000,-1000,208,1000,893,-620,-133,-1000,-11,429,-547,1000,-826,-853,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-475,-1000,845,1000,407,504,-648,-1000,858,1000,224,-765,547,205,630,-1000,1000,-349,36,-1000,1000,-1000,-285,532,332,1000,1000,-1000,-686,-1000,682,1000,31,557,1000,946,1000,1000,1000,765,1000,540,-419,-1000,-1000,-1000,-1000,530,1000,-207,-1000,-1000,537,-847,-399,-1000,777,363,-1000,-1000,-1000,-788,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{430,-406,945,879,616,814,835,447,1000,-510,-123,433,-307,51,-179,-1000,1000,823,519,-1000,-199,1000,344,1000,611,-779,1000,971,942,811,-654,81,-403,-198,1000,524,-906,-103,552,-815,-505,936,850,-475,133,-155,886,-346,-497,-1000,-690,-1000,-1000,1000,-186,-1000,979,983,224,-585,352,331,917,39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendFixedWidthPadRight(java.lang.Object,int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-787,-1000,511,-4,373,263,263,124,-856,-63,1000,718,161,-13,-441,-12,-566,-384,524,-559,-881,-288,-988,361,288,-537,-199,867,312,272,-231,375,-301,-63,-283,-320,46,680,-330,349,232,-217,198,-50,-616,-788,511,-1000,-249,-204,893,-215,-185,363,-88,992,167,607,-124,56,-112,1000,-1000,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{-234,-117,814,873,864,-1000,477,781,550,-259,-419,762,565,-386,-237,126,509,567,-2,875,-67,967,-341,602,619,136,-869,-833,800,-827,-477,-639,66,25,166,-411,-101,207,-285,522,-27,-1000,-13,-1000,12,-739,195,155,953,1000,-557,314,-256,-611,-1000,-171,1000,652,17,-70,-409,-380,706,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{450,-793,-392,1000,-461,379,794,251,140,767,-819,117,54,958,-576,-537,-1000,-954,-1000,692,650,1000,185,602,-701,572,-1000,-1000,-193,1000,-351,524,358,-86,309,1000,369,191,282,920,-737,501,-681,214,935,-849,216,352,-922,516,-804,238,-546,90,170,754,842,-140,438,130,807,-1000,-126,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,784,-897,242,-261,-974,324,134,1000,287,-1000,842,214,759,-695,601,774,817,375,684,255,780,-409,-26,-759,429,-495,-557,1000,-590,-731,-1000,720,-121,425,494,720,278,876,149,1000,230,-871,-1000,-69,-874,420,316,973,922,-803,203,-1000,321,-127,628,1000,532,-263,705,-239,278,-758,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{542,106,-626,370,-236,952,372,-434,666,1000,-1000,516,-812,977,170,699,-15,-591,-768,487,250,640,-5,653,-656,-87,-10,437,711,928,25,137,751,132,-980,864,-103,675,-311,448,101,-283,-216,539,455,-540,-556,437,-736,846,-523,359,-577,-22,247,857,502,619,-568,-211,772,-295,212,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{307,-366,-91,-804,-1000,1000,889,-138,-633,114,726,935,253,-871,1000,309,-630,882,1000,-1000,1000,-199,186,-687,-825,250,-604,-297,924,19,-227,891,379,939,842,-372,-841,-437,406,-1000,-88,1000,1,1000,655,181,-613,-416,91,332,422,-1000,487,-944,1000,365,51,-1000,-16,504,1000,1000,-58,819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,704,332,-1000,-778,981,889,644,-355,-142,726,660,780,-549,1000,235,-730,811,1000,-755,810,-199,186,702,-825,875,-436,-400,1000,-121,-299,-203,-608,987,862,-870,12,586,690,-572,-645,767,649,652,655,-257,-127,625,1000,124,-1000,-933,487,-1000,949,294,457,-1000,-51,444,262,357,205,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{437,-1000,496,224,-405,-511,-996,962,-745,1000,-610,199,-1000,507,200,424,-373,-183,-1000,719,50,-720,745,-741,-688,85,-337,1000,-1000,1000,-41,1000,460,-435,-74,905,-216,-849,1000,272,-160,761,-575,-68,-1000,94,302,-625,-652,1000,-763,873,516,99,1000,628,-426,-495,-229,-307,-167,114,-82,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNewLine():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,422,1000,-164,-418,-530,559,206,257,-240,389,1000,816,-211,-700,104,703,1000,1000,-183,1000,619,-302,80,103,982,-883,-1000,1000,-434,-689,-792,565,515,972,-315,52,26,1000,971,-813,-502,270,-956,494,-314,103,301,1000,-348,-209,-798,-696,-320,-1000,183,1000,204,169,-258,-358,926,-39,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-709,-1000,337,1000,733,-841,523,-1000,551,-695,251,-421,66,-604,803,110,167,62,-673,-1000,-1000,218,-1000,-774,799,-980,1000,-1000,-354,1000,-491,-148,-1000,1000,-549,-639,-1000,926,914,628,782,-1000,1000,622,-890,250,-193,-1000,-1000,94,821,335,-683,641,-1000,-394,1000,42,-1000,-1000,956,-86,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-972,897,839,-1000,137,-907,1000,-123,-1000,-117,671,565,-823,880,453,363,589,-246,-1000,780,1000,490,-545,267,839,711,-1000,-765,756,501,995,-1000,403,171,-1000,-453,-548,1000,262,-1000,-1000,1000,729,-471,-395,919,335,-1000,524,-380,250,-152,890,35,-1000,-686,-1000,-1000,506,333,1000,-366,-132,284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{614,-116,584,364,-447,-861,-240,-163,273,587,-41,-1000,556,725,-580,1000,-303,330,-331,8,-96,848,1000,-1000,417,-115,53,125,-968,1000,677,229,-443,-736,354,1000,993,-1000,-644,-79,61,275,-400,-25,-386,-721,-258,181,-323,-686,1000,-917,954,1000,-206,-331,1000,647,-471,-341,-1000,368,-549,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-3,923,492,207,806,325,-916,847,-1000,-222,519,-1000,-1000,-104,873,-560,357,671,-87,1000,-1000,729,-1000,267,573,260,288,-65,125,-1000,854,-644,-1000,-415,546,-1000,-1000,385,336,539,1000,564,-491,1000,-136,95,-333,337,-1000,-36,400,-240,-287,-1000,-121,-1000,-702,-1000,915,-69,892,-237,-843,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-786,512,783,-133,532,-535,-342,-267,281,61,634,-978,-54,431,218,1000,-318,-941,-671,1000,1000,709,774,-47,545,-500,-708,-515,-463,383,-513,181,492,-152,-1000,1000,922,194,-1000,-1000,61,-415,1000,-640,-612,155,36,-115,768,714,1000,-1000,798,1000,-977,343,1000,-173,-645,373,-85,-198,-1000,859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{994,-475,-507,361,-790,-584,-686,531,-505,-48,735,-74,-608,1000,-965,664,-318,-1000,-984,-582,6,3,71,-47,135,-179,-1000,-513,-1000,-598,-1000,361,12,-152,710,-43,922,194,-525,-1000,61,-551,-36,616,-482,-1000,542,230,-573,1000,1000,-1000,798,-433,224,-713,1000,-173,841,552,-1000,-217,-1000,116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendNull():org.apache.commons.lang.text.StrBuilder",
            new int[]{-466,-589,736,462,-149,516,-481,372,-668,-826,449,257,-524,-431,255,-1000,-590,745,-484,904,-645,-294,-553,-609,99,384,748,-824,617,-142,433,416,-193,360,-531,-429,-100,748,-80,-328,1000,-599,545,-126,-842,608,-325,971,-147,341,-732,-362,-442,-351,-739,637,-20,1000,152,342,923,-537,-382,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{252,-1000,-573,-138,636,329,52,149,-73,165,-980,-646,-370,-707,578,34,751,591,265,193,251,-660,-665,-881,250,1000,168,689,-265,377,-864,742,1000,88,-225,261,1000,905,86,-739,761,-179,519,1000,262,290,296,-69,-594,169,1000,481,-117,362,-89,-550,-647,740,-310,-551,742,787,873,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{340,342,-821,-98,145,270,-801,579,-362,-917,-919,129,1000,-512,-38,78,1000,804,539,49,-825,-1000,384,-300,511,489,599,-865,-1000,418,347,1000,29,-63,1000,706,433,512,-524,241,256,-19,345,-45,290,490,558,-1000,-916,1000,1000,1000,-218,1000,121,-222,-988,386,-283,176,1000,-8,-555,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{839,1000,-1000,633,-322,597,12,-467,-587,401,-158,-1000,299,-210,1000,-656,1000,1000,-428,1000,1000,-496,-514,1000,274,-643,1000,718,99,119,709,1000,91,3,-329,-10,318,995,-831,-322,1000,546,1000,1000,-15,-716,-476,330,66,12,1000,276,179,-780,-1000,-95,-1000,-92,-832,-1000,1000,748,127,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-500,-581,1000,1000,293,-404,692,335,115,-761,572,892,227,400,-233,-945,762,-640,264,-641,-475,-819,1000,-388,-810,-995,576,1000,642,-1000,-208,992,-1000,-117,400,1000,891,-250,672,98,170,8,763,-494,1000,670,490,1000,10,956,1000,856,623,55,521,1000,718,-975,-1000,547,48,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-686,725,-663,28,-713,-345,97,-108,487,250,-569,-646,-989,-304,521,-536,977,459,13,978,404,133,-911,-484,-475,551,-533,986,841,-475,360,742,935,-366,-93,954,838,53,-855,-638,539,-424,198,703,179,-262,-957,-88,-647,167,927,-522,-960,202,-672,228,-319,-820,-781,-763,139,787,287,587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{780,-1000,-1000,114,675,-234,-961,-19,-1000,-620,-3,752,1000,-107,628,-366,1000,1000,719,1000,261,-1000,839,-1000,937,-586,1000,-1000,-442,568,1000,894,-541,626,1000,-432,-485,1000,-765,667,2,720,56,173,252,-58,96,-599,-1000,769,1000,1000,674,-353,-747,523,-1000,847,-985,-889,1000,-55,-970,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{238,-808,475,-398,26,997,617,91,1000,173,-37,-511,-224,174,-1000,20,-1000,-1000,488,-1000,-435,292,-1000,903,-361,-560,-1000,354,9,245,185,-1000,101,226,-1000,626,-1000,-760,1000,-1000,998,-1000,-13,-531,92,-208,798,341,1000,718,-1000,-108,-1000,669,1000,455,798,-300,811,671,-1000,-96,15,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendPadding(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{414,-1000,-624,717,1000,264,371,-278,-572,-520,101,1000,-106,1000,-658,-1000,-232,-1000,-97,-719,-1000,1000,-173,416,369,-1000,966,-244,-1000,-900,38,-1000,-1000,-526,-1000,261,-75,-1000,-44,-298,-109,-137,-214,-1000,-516,21,-366,-652,-1000,842,-949,-100,-320,-698,1000,-732,369,-1000,179,-412,-1000,-1000,-1000,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-246,-76,381,119,539,-514,-243,763,-911,-451,514,594,576,-655,-184,-914,-1000,-132,554,-419,-264,1000,455,738,-683,-227,-806,-523,-651,-1000,-69,-231,500,-926,668,-1000,186,-445,-214,379,489,811,-464,-226,-960,-77,-710,-149,-173,-396,-399,1000,1000,167,424,166,-465,760,-43,-633,-572,738,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-814,-733,-936,647,-8,859,-506,-769,-610,-380,-560,-398,856,-319,986,-963,-973,-570,-959,543,-941,346,533,804,706,493,-956,303,-868,531,-688,-778,-32,843,-926,182,-829,-896,-683,-527,890,487,147,4,731,-432,235,-120,-446,-50,-943,274,251,654,993,-976,854,-599,783,285,-282,39,780,-547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-86,286,-55,-468,-7,-226,283,-339,-1000,669,-242,1000,-386,-1000,-410,-1000,400,457,296,-825,-1000,279,997,1000,-434,875,-1000,-629,-1000,-533,490,-726,484,-926,559,-330,-400,-59,25,688,1000,187,-907,-400,148,173,-1000,-400,813,-205,176,688,727,-348,-400,356,-650,760,-407,196,-1000,617,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-450,92,144,-485,-773,52,494,-176,1000,-703,867,382,367,982,-746,772,-1000,-1000,1000,-383,743,-378,452,538,35,-67,1000,-1000,109,-1000,-1000,1000,-501,-145,815,265,-44,1000,-96,322,-195,-162,1000,-506,817,-576,-555,2,110,769,23,-710,990,499,-913,201,-687,-209,-579,-1000,-746,-1000,-695,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.lang.Object[],java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{694,-439,894,-1000,-220,416,-377,-814,33,217,-530,582,87,392,175,1000,167,159,-106,98,550,-703,-366,678,-389,-312,780,-616,647,244,-503,2,-897,-1000,1000,234,1000,1000,106,-810,-1000,57,-136,243,242,11,286,686,126,485,1000,256,-85,555,19,895,355,-511,-1000,-1000,-867,294,207,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-517,137,-529,582,-716,-726,195,114,12,75,256,-625,91,-217,204,-771,-358,139,-35,189,-16,-405,104,-823,393,-996,924,-599,345,-152,-259,293,140,138,971,807,-1000,842,96,962,-1000,-83,466,708,934,-282,314,-619,-442,847,-1000,135,916,-1000,435,124,519,-444,-872,616,262,232,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{966,-987,-342,287,-1000,-488,462,-1000,-485,-802,-615,349,215,-806,324,-277,-512,955,599,272,993,374,1000,455,831,315,-1000,-564,454,-12,-255,-286,-546,1000,-568,307,864,-102,-1000,1000,93,45,389,-577,678,-165,602,-602,17,-238,-772,-788,-1000,-1000,831,27,-157,582,-1000,-540,-396,-1000,-425,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{142,-271,-342,-32,-291,-76,346,188,41,261,94,-245,489,-107,302,899,400,55,-350,-330,-526,412,111,-80,79,40,780,254,-941,947,-4,1000,-674,477,-484,-367,886,196,440,-1000,360,136,228,61,-187,592,681,233,961,47,711,386,-115,-227,337,375,355,0,-1000,681,499,-207,527,-290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-698,-727,-1000,-709,-89,-702,454,766,-545,-658,708,-297,-1000,14,1000,354,-913,153,343,-632,-629,-861,98,-627,1000,-1000,-783,-1000,-985,930,550,796,-165,545,1000,-442,-51,1000,-842,-1000,1000,149,-342,-725,0,-1000,485,152,1000,1000,-1000,1000,-1000,-668,1000,-725,611,-342,-1000,1000,698,-240,228,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{657,-945,-27,-194,210,-503,-557,602,-437,-119,-859,261,-629,-403,-107,-706,-493,74,435,421,279,-92,-13,782,253,191,-866,533,-538,551,-624,-752,246,537,148,647,475,-800,778,694,-75,-734,85,222,449,237,198,-685,-694,-77,251,-924,-92,-20,-589,203,311,754,-623,170,-151,-330,61,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-42,-725,454,643,-706,187,-381,430,-411,-159,1000,-235,-1000,-918,11,-1000,-602,-231,403,514,-848,-1000,-344,-31,537,88,-794,-28,1000,446,265,-439,235,392,-204,537,-606,1000,-25,157,318,389,-693,587,-728,-1000,164,537,-805,1000,-571,349,-892,-719,367,-414,986,-1000,609,865,-554,-940,729,-502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Collection,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{394,-1000,-944,-463,-432,-102,1000,-1000,527,-1000,-59,257,101,-95,-658,668,-450,183,504,359,719,118,1000,480,674,-771,-537,-1000,-548,-839,430,-400,-359,-258,-372,9,1000,-878,-186,513,-230,-303,260,76,372,263,517,224,701,-250,86,-569,-58,-336,-249,615,-746,1000,6,-1000,144,-893,-772,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{29,-1000,408,-540,88,-544,43,157,1000,-397,308,976,-104,751,332,-1000,-236,482,1000,250,36,885,1000,173,-359,-287,-20,-1000,1000,-1000,-17,40,-464,494,-309,-904,499,961,589,-259,404,1000,443,-220,-732,567,285,224,844,-1000,-814,-192,-520,-895,401,1000,-1000,-806,988,683,-164,-20,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{175,288,289,524,-889,-18,-323,317,535,-143,35,-944,-93,-868,500,521,142,-730,-462,-244,125,421,-213,569,-6,973,-174,178,665,-667,783,197,313,261,-69,638,307,273,-751,-887,-578,-24,285,983,91,-477,-388,-117,-104,818,-332,-284,-434,577,-176,996,-695,-81,163,253,477,736,-361,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-365,83,-416,-698,-1000,-971,851,805,1000,70,184,661,1000,-122,-276,-61,-944,196,925,-1000,-415,-98,335,1000,20,362,-480,-483,-1000,-207,-729,1000,-619,766,58,-1000,-82,229,402,221,-157,837,-484,-567,-1000,-439,794,1000,935,101,-1000,-1000,190,-715,383,-561,-1000,581,-843,625,-915,179,-876,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,1000,125,113,-466,-135,401,284,361,-623,-1000,967,-278,304,961,-939,856,209,719,37,1000,207,770,1000,-785,1000,13,413,-656,921,-1000,-1000,1000,-1000,1000,361,1000,468,359,-609,1000,-868,265,-16,1000,-179,1000,858,-182,-1000,-45,558,-1000,3,863,-677,403,409,629,650,-1000,-1000,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-169,-340,-1000,1000,-300,355,-786,-236,164,158,-22,-623,1000,-1000,253,280,220,-515,-822,-498,-232,-894,-1000,-1000,-1000,-833,-531,-817,247,-76,144,819,763,-442,1000,-760,-247,-633,143,648,1000,221,266,926,421,-1000,96,-40,602,-1000,88,711,-627,691,219,-118,-268,1000,245,-209,315,-507,626,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-644,-1000,12,-1000,-362,-821,-66,-774,16,-374,490,-4,-237,194,-1000,-88,744,509,879,-525,1000,-233,701,-807,-1000,-502,-1000,-1000,524,-1000,-1000,-798,380,-388,-306,-1000,109,1000,-76,-1000,1000,586,-118,-1000,235,-423,-514,-756,374,-1000,-186,-270,-511,-827,59,1000,-1000,-1000,531,493,-1000,-1000,1000,891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{600,980,1000,377,-1000,-187,679,394,-991,391,-345,-45,230,911,-730,380,-621,261,-393,-56,1000,711,970,-1000,1000,343,1000,798,615,-371,51,65,706,480,-1000,951,600,1000,-377,-255,-1000,878,-839,-323,47,1000,-805,492,318,888,-1000,-373,-268,-1000,-385,804,-574,-279,-46,287,542,827,-1000,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "appendWithSeparators(java.util.Iterator,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,854,-303,-861,60,-276,-293,798,-1000,210,241,524,437,366,-959,-320,-660,1000,28,-588,212,-295,619,-210,-541,-156,142,1000,832,402,-421,-1000,-716,-900,-1000,-96,-285,156,194,-421,-1000,999,246,-1000,-1000,1000,-49,248,-644,172,-770,-773,-979,-602,-92,115,-677,-1000,576,-26,33,296,-591,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{1000,-1000,375,-79,977,-601,54,-767,159,-208,-1000,-328,151,323,-581,-161,500,785,-918,761,978,-636,-738,-932,393,551,662,601,524,422,523,1000,-940,-1000,347,66,26,242,-1000,215,510,-746,95,703,225,36,-460,-886,459,-671,672,-1000,674,-112,102,99,1000,813,-1000,1000,-1000,-646,1000,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{-25,217,-318,211,-572,401,-556,928,-43,82,751,78,-163,920,1000,-61,-4,-493,519,-1000,215,-641,1000,612,-875,663,-1000,-643,-1000,399,-253,-668,996,260,-582,-228,-77,362,653,-785,1000,1000,101,-1000,-1000,-798,874,1000,-579,564,-1000,946,-286,-256,-382,45,-930,-541,160,-1000,797,862,471,-522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{-115,396,499,-79,977,-582,467,422,442,-1000,264,32,-702,-933,-825,660,500,231,180,761,-572,652,-670,1000,-875,551,834,601,-14,422,-895,1000,996,-175,-114,743,900,-388,54,-490,510,-971,-517,703,151,-358,-472,28,682,-392,-686,653,-1000,36,-8,-647,-911,138,702,1000,987,187,-598,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{1000,-691,710,558,157,230,-696,-789,472,199,-501,262,132,792,261,-646,172,893,-379,-328,1000,-825,-333,-460,529,576,-404,187,524,285,1000,442,-201,-1000,69,720,-249,135,-1000,354,1000,191,1000,-86,-261,-729,387,88,885,171,6,-1000,1000,-131,-642,-321,777,162,-781,71,-702,754,574,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{394,-1000,-15,-79,562,-992,778,-319,403,369,-399,155,874,-305,-536,604,158,-131,-152,393,562,-400,-757,-984,-75,-103,833,445,-992,181,-118,912,-772,534,714,66,-247,-13,-1000,-521,-705,-498,-984,1000,347,431,-1000,-1000,-726,-564,430,-1000,-25,1000,-504,1000,435,289,-1000,1000,-150,-1000,29,591}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderReader", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asReader():java.io.Reader",
            new int[]{564,-235,-784,-280,1000,-269,972,249,694,366,969,1000,-72,20,-733,30,240,-936,590,241,1000,-1000,328,-112,-1000,1000,-1000,834,-989,-42,811,-925,47,1000,-658,1000,848,-495,952,-1000,-387,-52,8,897,-1000,703,-414,-131,-840,-628,-408,-353,376,-1000,453,21,-1000,-983,-344,624,-285,554,53,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-636,-958,-516,-928,-147,-144,-611,-391,-518,-581,-400,-1000,702,775,-230,10,664,-648,-908,188,522,225,-965,-56,-479,-137,-183,159,-888,439,598,-318,127,5,13,525,-637,-164,-1000,-224,359,184,540,123,152,130,766,-580,465,-35,465,-1000,744,-268,141,-742,-805,132,-225,862,-514,220,383,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-506,-951,-151,-534,-581,1000,483,273,1000,-176,-907,1000,259,990,-1000,524,-1000,757,1000,282,222,-280,614,-212,-573,223,1000,1000,1000,909,-1000,494,1000,1000,-907,-644,445,-1000,1000,903,849,-515,561,1000,-190,1000,247,-254,-259,-20,1000,-597,-158,-1000,1000,-160,-1000,-883,-1000,-243,108,-765,101,892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-442,-1000,771,-955,-1000,123,661,-51,-552,-488,-474,1000,-196,-288,-238,-843,815,628,-821,410,1000,505,-1000,-856,-959,-319,154,-232,-476,1000,480,1000,-628,1000,229,653,-167,-46,778,-207,740,791,1000,1000,-242,1000,936,246,209,-503,1000,-686,-450,-305,-771,-1000,-119,430,-1000,-312,-690,-539,826,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{118,-80,-355,-480,658,-77,-394,-892,-637,374,-1000,-441,-336,-111,640,547,231,-62,-947,41,-802,-1000,-903,443,526,515,-172,212,158,-958,-110,-554,433,140,448,1000,930,300,-910,629,73,-55,-460,-83,-120,-335,804,632,-504,389,-1000,143,169,-1000,1000,405,-834,2,-53,-130,-602,1000,-275,471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-135,-859,433,-981,-170,-747,-178,-122,-125,-318,1000,431,-279,-625,-623,260,361,227,-691,564,-104,-1000,-884,-779,-405,-636,-810,540,-792,836,509,334,-550,-311,766,829,514,299,-760,-271,408,261,944,871,-1000,260,930,-234,-403,-331,71,-504,788,305,141,-1000,-236,438,-625,-538,-517,-157,859,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-1000,-859,1000,-1000,-789,-275,-611,417,-696,-233,677,278,-1000,-1000,-426,782,-740,997,-850,259,18,154,-884,-1000,-1000,628,-611,1000,-79,999,873,-274,435,-1000,1000,-742,-365,840,-1000,-625,266,-769,-51,159,-646,234,951,-679,-631,-267,-166,-343,193,-181,1000,-810,-533,-1000,-381,-1000,-199,-438,-1000,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderTokenizer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asTokenizer():org.apache.commons.lang.text.StrTokenizer",
            new int[]{-640,-748,-288,-938,-14,479,-800,-258,-743,-582,861,1000,555,1000,1000,-68,308,1000,-820,-103,365,430,-1000,-400,-741,601,-485,-658,762,950,1000,-176,-152,305,-760,-747,-579,166,-715,-294,984,-3,862,831,485,1000,1000,-72,-729,178,974,110,539,-1000,1000,-1000,-615,-951,-402,-768,-878,-357,-611,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{393,-460,1000,586,1000,294,-656,-995,247,308,-434,-74,410,-1000,-486,734,-102,954,-685,351,1000,51,265,984,663,-297,-632,586,37,1000,-838,877,-602,1000,151,-349,-630,-76,-605,-208,1000,-75,974,-780,1000,-474,347,1000,801,-366,-421,-138,1000,267,346,737,980,-31,-401,488,66,738,-229,706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{274,-120,-171,62,-122,568,-285,130,52,-1000,7,-72,1000,381,126,-825,-493,-304,-436,-109,-982,250,-525,-868,-158,1000,-304,-193,51,-315,166,-13,-318,314,305,706,64,-206,342,-473,-1000,-376,268,-205,448,-197,-257,756,-216,-856,-68,-864,349,-202,438,198,574,-405,843,618,350,516,-331,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{862,-460,801,201,673,774,-644,-954,-988,-753,262,952,575,-479,-80,734,-624,826,508,351,576,-369,235,95,625,-826,-632,620,-185,880,-730,667,-741,701,-933,193,-313,-443,577,551,515,-757,620,352,174,-548,949,-328,801,-113,-292,405,128,-176,516,713,609,-49,84,781,673,-169,610,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{-33,-1000,-4,679,-212,285,-547,931,158,-456,-995,-86,58,852,-1000,-178,244,534,-108,897,-253,340,94,-918,160,361,1000,-53,936,-557,224,-8,-566,-459,818,677,-783,1000,526,-697,-609,476,-950,2,-4,322,1000,1000,417,-766,-184,-684,376,-445,695,844,665,-196,-472,-1000,501,640,194,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{586,985,-971,-324,189,283,381,-1000,-723,207,857,-32,1000,-257,862,336,-1000,645,-610,-943,589,755,771,1000,734,-145,-656,88,-196,-297,108,719,1000,742,-435,-482,188,-471,191,-64,-248,880,657,-474,901,-199,-1000,480,60,-634,-360,-1000,-1000,-268,308,-257,-1000,-1000,959,902,1,711,-13,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{710,-175,936,-91,445,526,-771,-343,-806,-654,676,832,1000,-897,-326,798,-1000,-363,1000,379,1000,-395,1000,270,1000,-401,-1000,972,-437,92,-952,633,-794,164,-1000,-405,294,-200,1000,602,359,-372,278,937,1000,-782,650,-1000,405,-1000,-1000,616,-643,-1000,-343,120,612,719,122,802,993,-237,261,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{609,-1000,-18,931,249,887,232,87,-201,-1000,-266,-1000,781,1000,416,-1000,-372,-607,919,-889,-1000,-275,-1000,-1000,365,-586,-304,941,-202,-475,191,-577,-228,-23,1000,575,939,-309,311,-1000,-719,-546,634,-650,-866,-435,-1000,756,-569,-1000,529,-864,768,-680,996,98,364,-504,1000,945,-244,723,-676,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder$StrBuilderWriter", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "asWriter():java.io.Writer",
            new int[]{189,-688,0,1000,392,907,87,-681,-412,-1000,-99,-40,223,1000,387,-1000,-1000,-542,1000,-299,-1000,-811,-1000,-516,32,0,-1000,956,-1000,-361,-379,-122,-1000,-385,1000,854,953,-306,0,-484,-541,-1000,81,483,-586,-1000,-1000,-76,-586,-1000,702,-310,-289,578,1000,725,-506,-73,400,124,208,0,113,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzc=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-633,-625,329,-1000,1000,819,-102,642,171,-890,-90,84,1000,-1000,1000,773,-977,1000,-807,-853,1000,-96,-892,1000,-416,287,-406,131,-600,1000,-1000,-249,250,-282,139,-1000,-1000,1000,-159,376,-377,-714,-602,-16,985,868,1000,106,-810,199,-252,1000,-1000,-488,1000,-485,418,1000,-151,522,433,-980,309,-749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.Integer:OTI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-522,-361,-223,922,-86,769,1000,-633,1000,710,-1000,-823,-885,1000,889,-1000,-834,1000,-679,922,212,394,-742,-671,1000,-432,323,1000,1,244,-756,-182,-960,-570,-202,-686,776,-467,1000,1000,1000,-1000,-1000,438,-467,1000,-84,47,-335,-1000,24,1000,-398,1000,1000,1000,-182,-275,811,-980,1000,-1000,-870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTA=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-1000,-1000,789,-1000,255,-911,-224,-1000,1000,-283,313,-466,-314,-223,295,-810,553,1000,-1000,-1000,-1000,919,-1000,1000,811,-1000,1000,1000,815,1000,-607,479,738,1000,50,-227,-444,-1000,126,371,1000,-228,-1000,478,-1000,-1000,326,1000,-1000,-1000,978,407,1000,-539,685,1000,377,-1000,465,822,-386,568,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-792,-272,503,950,389,-163,-641,-87,57,529,317,971,539,-309,563,367,737,96,887,-743,-404,-993,-365,-924,772,671,-273,-482,956,-977,548,517,-8,740,462,-55,-353,-945,-314,-443,-284,616,-614,-871,979,-837,970,719,470,727,-230,-503,-509,-317,-437,691,869,-929,720,-144,-951,324,-831,-95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTY=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{143,-277,-530,-68,-373,-251,42,161,-1000,1000,749,242,-505,400,-442,-276,-359,857,1000,-471,-1000,-233,388,-400,752,264,-416,424,1000,-419,623,-853,929,-443,-24,-360,-1000,-495,-816,334,1000,1000,-293,-1000,1000,-473,-956,26,902,-1000,-1000,464,400,1000,36,381,-593,-400,-195,-1000,-3,986,84,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-1000,-1000,703,692,266,-561,532,-1000,1000,-126,-319,-893,-1000,1000,566,-1000,1000,1000,-1000,869,384,395,-742,729,629,-602,676,1000,385,-1000,121,618,-242,361,-1000,-615,805,-318,131,986,1000,-757,-1000,497,-622,262,-34,47,-215,-697,926,522,-347,820,1000,-1000,-219,-1000,1000,-345,606,286,530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-1000,-127,723,1000,-662,-681,232,-868,1000,1000,1000,-803,-1000,-1000,311,113,1000,646,-276,-228,759,-1000,1000,-1000,960,1000,-1000,161,-281,160,-1000,409,519,-89,-1000,-699,-435,-1000,452,32,1000,-1000,151,1000,1000,677,1000,1000,-1000,412,-1000,-1000,-392,-892,680,-601,1000,-1000,-321,1000,1000,768,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzg=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-73,-559,202,452,-427,163,-712,-635,-721,942,327,-516,208,-804,-964,390,-310,-735,534,-767,364,-865,66,-385,-193,-413,-899,13,838,-374,-212,-895,-660,-253,-252,680,206,-60,176,620,765,193,-685,896,971,-287,495,440,500,-936,-840,345,928,995,542,876,189,873,-998,496,637,295,-579,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "capacity():int",
            new int[]{-1000,-565,160,301,658,-251,-231,-196,-624,-317,-90,-1000,941,-862,748,240,672,1000,920,-605,1000,1000,373,-480,1000,-187,-1000,-1,795,801,-1000,516,212,-30,-409,-444,359,561,885,-845,1000,106,96,-397,599,5,1000,-1000,-243,-649,-122,1000,743,1000,1000,1000,418,901,224,522,-377,-1000,-1000,-86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{911,-1000,1000,-196,870,-112,-46,441,416,-290,429,81,1000,-232,-339,1000,537,1000,95,493,-883,-112,-4,594,1000,724,-943,-822,689,276,781,-184,-1000,-697,1000,-867,816,-826,1000,-1000,-7,800,937,-198,100,614,-192,1000,364,771,-1000,1000,390,-799,684,605,665,934,-685,-44,211,-800,570,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{-1000,-240,118,-511,-143,156,-273,-453,-864,-188,691,-404,-470,-1000,-1000,-1000,171,28,60,-219,318,519,1000,-367,-480,-281,-424,-455,108,-570,10,1000,-498,-65,330,-571,214,743,-183,-765,172,475,-253,-537,-281,1000,643,449,-99,-634,804,377,-194,544,-1000,680,145,-317,-369,-160,-579,-93,-1000,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{1000,86,961,1000,973,-112,-523,222,409,-879,-364,450,1000,264,-430,-756,402,1000,1000,-137,-1000,-1000,606,863,1000,573,-1000,-1000,565,348,504,-936,-524,-785,646,-59,1000,-790,1000,-337,22,172,329,191,30,799,-400,1000,309,1000,-1000,1000,1000,139,1000,1000,355,771,-70,1000,964,-1000,-540,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.Character:Mw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{16,-892,-43,326,580,-128,788,318,1000,-598,1000,-189,-968,783,-1000,677,1000,913,-1000,-773,927,19,730,816,-863,749,736,-600,-247,146,176,-810,451,-1000,253,-389,1000,701,-272,-372,304,1000,-999,-1000,669,-1000,36,360,-388,-309,-1000,-456,-672,499,-247,-305,119,-280,-35,148,1000,346,-227,-696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{635,892,-399,170,282,705,872,-147,-257,-357,851,-127,-251,-605,-976,779,396,822,-914,746,-313,-906,668,-480,-334,644,-610,-757,722,-988,-418,-546,-719,889,-916,-365,170,54,-790,-216,654,-776,-820,-350,399,353,-894,531,423,388,960,-199,472,96,866,-275,201,220,525,524,-956,-104,617,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{911,-1000,552,1000,634,-1000,-256,699,992,-619,1000,180,1000,22,-522,8,-42,1000,34,778,-528,-1000,-857,1000,1000,-1000,400,-955,253,1000,842,-328,-498,-752,606,-556,816,864,308,364,-149,332,1000,-202,-116,110,1000,846,620,1000,-1000,1000,995,857,-63,1000,1000,558,-434,-647,256,-31,-1000,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "charAt(int):char",
            new int[]{1000,-1000,896,1000,-100,384,-728,1000,-8,-904,189,-141,-98,-1000,-76,779,246,1000,-240,535,1000,1000,-419,-749,1000,-1000,-1000,-166,1000,-710,289,1000,-985,588,866,275,687,-111,1000,-110,-18,532,-167,-441,-704,1000,-66,1000,-383,1000,-1000,684,721,-681,-779,400,-507,400,-641,-1000,-217,-143,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-82,635,635,1000,-967,-1000,-511,-783,199,35,96,-1000,1000,-409,-1000,-290,742,-779,-806,-1000,964,-874,-824,-1000,-890,376,941,-1000,398,-744,57,1000,-324,702,171,1000,-587,370,701,956,-553,-751,-953,15,459,-1000,-962,-1000,1000,85,-1000,1000,1000,312,-939,-1000,795,-867,679,-1000,-932,-809,876,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-63,-474,629,1000,-911,-879,782,-956,272,241,714,-77,1000,-227,-1000,-953,917,-939,-59,-1000,929,-653,1000,313,-994,-175,904,-477,359,-778,306,1000,160,-1000,1000,847,501,391,-855,224,-556,-666,-768,-3,-527,761,-484,-72,1000,-837,-638,878,663,-81,-578,-823,209,-662,679,-339,-994,-826,183,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,635,57,1000,1000,695,49,37,-371,345,509,308,69,718,-562,-290,48,-1000,-622,-345,1000,1000,247,-1000,-930,-572,36,125,-634,-744,272,655,360,639,171,-298,-919,1000,-646,-774,-1000,233,-1000,27,779,-1000,-414,-236,552,85,1000,-144,-591,-1000,-776,-186,795,131,955,924,-471,-133,1000,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-432,-731,635,1000,-927,-1000,-473,-1000,517,16,749,-341,1000,-362,-1000,-1000,-105,-1000,-1000,-1000,1000,-1000,1000,-373,-1000,39,1000,-1000,342,-1000,590,1000,-408,702,1000,1000,-374,1000,-1000,233,-1000,-1000,-1000,458,238,200,-865,-1000,1000,-1000,-692,1000,1000,-632,-1000,-409,1000,-1000,679,-372,-1000,-1000,1000,-103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{-406,-124,316,-92,914,1000,-492,186,-157,-473,-1000,-139,-1000,1000,934,234,-658,-305,720,-1000,-274,189,-646,1000,435,155,580,488,58,867,-350,-377,-60,1000,-233,127,622,24,720,57,-815,18,168,-9,-1000,-712,-642,288,-840,599,1000,-1000,326,365,1000,-703,303,780,247,403,1000,-103,-913,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,515,175,928,-1000,-1000,-147,-510,507,-1000,-473,-646,-400,789,-221,393,-1000,-547,-945,61,461,880,861,400,-747,1000,-498,-348,45,74,250,244,783,674,1000,-182,823,485,-681,1000,-749,-1000,-941,-1000,-997,244,-40,-925,136,-1000,-1000,254,1000,1000,-431,612,730,-83,1000,848,-424,90,799,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "clear():org.apache.commons.lang.text.StrBuilder",
            new int[]{264,-1000,-464,-951,646,371,-128,-131,467,-847,-362,39,-46,383,-752,1000,-32,-235,-727,-1000,-90,102,11,398,-300,712,288,-147,501,535,-638,685,-1000,253,-254,-466,-187,550,-275,61,-891,-579,-298,-542,-447,285,112,-893,326,-217,-955,-195,399,1000,190,-123,553,303,1000,448,69,-6,506,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{131,428,1000,-287,-193,-661,-456,1000,-13,-1000,86,52,649,-793,-829,622,438,-435,-680,-565,1000,-1000,1000,-1000,862,-1000,345,-1000,-1000,1000,841,-1000,943,-416,-1000,712,-201,-1000,-1000,-1000,194,1000,-809,638,-1000,642,-791,146,-1000,-1000,-1000,770,1000,-605,452,594,-325,-1000,391,674,-301,-588,-1000,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{115,-360,-66,-504,-1000,682,76,-106,1000,-1000,-93,697,-671,-457,1000,-114,-888,-1000,-137,-259,-730,-871,-1000,-1000,-562,-794,-545,75,-529,158,-447,-1000,97,-1000,-1000,1000,-647,-1000,-494,1000,-1000,-612,-124,-194,148,1000,335,-747,-1000,1000,672,735,-809,238,-852,2,-606,-583,82,-61,479,1000,-766,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{592,-1000,457,750,-328,606,-417,-676,-271,-741,1000,738,-603,375,1000,-578,-473,275,541,1000,-1000,-282,677,300,494,-335,410,-58,-1000,-562,-591,-1000,-67,117,-553,609,-843,-93,-1000,-155,-1000,893,875,733,736,-14,-992,-55,-1000,558,420,-617,-319,-1000,783,175,-518,-1000,804,1000,-246,527,-1000,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{1000,1000,-379,141,-571,-245,-268,-876,865,-524,1000,503,415,432,1000,1000,-1000,150,-845,1000,327,-174,1000,-1000,-661,-1000,-372,-315,-108,-1000,438,-854,-1000,-1000,0,981,1000,-265,-824,-606,-936,209,-878,518,1000,1000,463,380,-298,404,585,261,-1000,711,-770,-1000,142,-405,117,95,399,1000,-305,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{1000,-379,-544,-325,-18,-737,-381,-662,703,-613,812,897,70,-383,1000,1000,-651,-503,-1000,1000,1000,-656,-131,169,-296,-1000,448,-598,-365,-500,99,-1000,-968,-963,-731,1000,93,31,-677,222,-338,737,-6,-836,-140,885,-225,539,-210,370,950,102,-1000,466,191,-1000,163,-847,374,351,-501,578,-710,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{535,-576,983,219,144,526,466,-248,81,-961,-643,46,245,-361,-180,-42,-87,-216,277,218,23,-237,355,-699,574,-1000,35,-269,-282,247,1000,-848,-270,931,299,-301,80,-597,-1000,-240,-173,1000,546,1000,-143,597,-427,76,-1000,249,-929,1000,250,-1000,208,-937,-530,-1000,188,1000,497,1000,-537,-65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-294,-1000,110,638,-338,138,-747,45,-179,-503,-476,1000,-292,475,1000,510,379,449,-922,999,-951,-668,-355,933,226,803,-1000,-268,-1000,-484,-1000,-1000,1000,-339,298,752,-1000,-1000,411,152,-316,-52,1000,-681,1000,738,-467,-155,-1000,234,381,-847,-1000,-687,-433,1000,47,-1000,1000,1000,-776,-360,-873,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(char):boolean",
            new int[]{-206,17,-287,394,-753,-721,388,-162,-1000,-64,351,764,-144,74,706,953,-6,-301,-1000,1000,-1000,87,-362,-425,-234,22,1000,-416,-53,10,-1000,-387,-47,-325,757,271,-86,-588,-744,899,1000,-104,116,42,723,-670,-405,-50,-330,889,47,-745,-192,-225,637,640,851,-149,599,874,-269,-125,59,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-413,-637,404,141,-177,-407,520,-1000,62,1000,478,696,728,95,-388,-1000,-1000,-716,-1000,138,-997,-1000,-372,1000,859,-1000,-251,818,786,-649,131,-1000,-303,1000,89,-957,-611,-1000,-22,80,1000,-41,311,438,387,1000,-712,791,372,-1000,1000,-1000,-144,-149,-1000,-123,161,-517,14,-1000,-2,-305,-459,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{71,1000,-298,526,354,-450,-298,-126,-824,-854,-446,942,-611,-176,109,892,518,269,619,302,962,-93,96,-1000,-254,-1000,520,-683,-532,-147,125,1000,-979,403,-471,1000,-167,-652,439,612,710,-370,-239,194,-989,414,-679,481,386,-246,-1000,1000,-1000,449,-940,1000,-799,208,-63,419,753,211,-858,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{471,-1000,-1000,1000,-1000,-991,-46,-567,98,608,1000,-893,1000,-1000,173,-861,-1000,269,-408,1000,-814,-526,598,1000,1000,-906,261,526,4,-144,-656,-487,-1000,1000,291,-1000,318,-1000,-97,1000,273,-1000,-1000,1000,844,552,107,-867,-616,405,1000,-712,593,488,497,-737,531,-1000,-808,558,-462,722,-463,766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{1000,-37,-1000,1000,-907,-39,-699,-418,242,1000,1000,308,-20,-1000,-912,389,-1000,-1000,7,-360,-1000,903,955,535,358,-1000,591,1000,-739,-127,-1000,-436,-1000,-459,1000,-344,1000,-1000,198,-639,1000,14,89,1000,630,1000,-517,62,188,1000,-1000,-347,-858,-364,-726,-333,-362,-1000,-1000,-458,-1000,1000,530,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-225,-193,-1000,170,114,-1000,-243,266,242,1000,911,871,-713,-570,-1000,-729,668,-1000,-997,1000,-1000,15,701,1000,1000,-416,417,1000,-1000,885,-304,-837,-694,40,669,-65,-235,-568,124,1000,1000,828,246,1000,-79,1000,-763,815,352,400,196,-363,-1000,-724,-723,628,-1000,-719,-677,-1000,596,610,1000,242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-10,-666,-7,995,-658,-7,-893,629,-822,1000,502,-247,-1000,210,-1000,1000,-1000,-857,-1000,-437,523,-416,1000,413,228,-874,1000,-337,242,-1000,-639,-791,-811,-935,666,619,718,-987,-424,191,1000,-270,671,425,941,-81,-1000,1000,1000,724,-949,1000,-803,-1000,-1000,-284,-1000,-380,202,-868,-913,34,-964,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{245,-1000,937,640,-847,489,-558,692,1000,-839,-333,-103,902,-529,1000,389,-1000,1000,311,-1000,-1000,71,-414,-608,81,1000,-185,-1000,688,-1000,108,-821,-269,-69,771,-532,-9,199,42,-1000,-561,353,-1000,-932,1000,-61,1000,-217,-1000,-1000,901,199,1000,-471,206,-1000,875,530,861,184,-1000,-543,1000,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{468,-1000,-579,1000,-1000,-1000,-558,-1000,1000,-272,742,-954,1000,364,463,-1000,-1000,910,378,741,-684,398,-414,1000,1000,-401,-208,613,-483,-71,-445,-1000,-649,319,-777,-1000,-147,-908,311,-1000,-561,-578,-1000,742,1000,1000,431,-217,-1000,-575,1000,-1000,848,-471,206,-359,1000,-1000,-597,-432,-625,609,-306,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{779,191,-66,161,609,426,-1000,864,-56,193,366,-475,-118,68,-873,1000,-1000,375,618,112,-814,-343,-400,479,-600,-278,775,64,29,167,-749,-563,164,-99,-607,57,856,-42,278,246,-1000,-539,-318,-743,701,-504,1000,636,175,-400,-1000,1000,118,-345,1000,-610,205,435,-698,1000,-1000,838,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{-712,-1000,1000,-257,-847,490,387,-1000,954,576,-336,483,1000,1000,807,-783,-1000,432,-421,-1000,109,-1000,-1000,1000,672,-1000,-1000,-315,1000,-1000,860,-1000,19,-69,72,-1000,-886,-1000,172,-1000,-561,-45,-1000,-463,1000,823,-332,-217,-624,-1000,1000,-1000,1000,-471,-520,-323,1000,477,1000,-775,-1000,-1000,-1000,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(java.lang.String):boolean",
            new int[]{590,-1000,656,1000,-1000,-17,-406,-964,547,-1000,301,-94,1000,1000,982,-222,-1000,1000,-384,-517,575,-719,-680,176,559,730,-479,-366,580,-303,84,-1000,-212,-240,710,-790,161,208,-322,-1000,-549,272,-1000,-402,1000,103,1000,-857,-981,-1000,1000,-174,339,-726,643,-1000,-272,-381,861,-34,-1000,-543,-96,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-540,-574,-369,332,-624,-141,-1000,-1000,-1000,1000,-64,1000,1000,-328,275,843,-968,260,35,274,1000,461,1000,892,871,1000,511,1000,-1000,-782,-1000,442,-488,-524,412,570,1000,-370,-493,1000,-737,197,-115,587,633,-583,-1000,1000,841,1000,1000,1000,-927,-250,-1000,212,-446,365,-297,-222,-614,1000,555,-369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-234,-288,-981,23,-198,161,314,-643,178,-715,-919,193,-129,-530,-755,-455,-495,427,-190,-427,-524,-313,542,991,-555,-282,15,592,893,616,93,531,336,-30,65,60,-346,-926,-398,-875,-310,369,-44,-817,-156,-655,45,-281,165,486,510,484,-400,720,212,878,-679,-547,122,-694,156,587,-340,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-1000,1000,-415,-20,494,-1000,-899,-1000,89,-461,677,-838,-335,-88,-460,-503,-243,-1000,-784,-413,1000,-165,1000,-849,1000,787,-1000,-189,-677,343,88,-55,-101,1000,-849,-1000,552,1000,-704,-358,675,-780,426,-311,417,-392,-748,825,794,1000,-1000,1000,1000,-1000,-256,698,-779,1000,-531,1000,-1000,324,-464,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-314,-211,-769,-15,-305,-175,96,-641,-847,598,-983,530,455,-143,-88,12,-887,41,-417,109,503,-113,923,259,398,638,-422,716,-651,307,-179,272,192,-228,719,197,-318,-486,-134,500,133,-280,-639,-342,287,-17,-578,861,-82,912,527,751,-46,268,76,461,-472,150,-438,-581,141,211,-22,550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{1000,565,-654,-14,-441,-903,1000,-671,-803,-33,718,-357,-238,399,-460,-663,-459,126,-66,-328,261,-391,583,-481,534,79,-853,-221,-511,-278,-264,-55,-899,373,-651,-456,-214,-406,-750,18,-212,-747,-204,297,484,209,-375,922,223,886,-56,383,161,-493,-504,189,-704,619,-33,378,-887,-1000,10,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-352,128,568,341,-731,-332,-49,219,704,-105,-31,-1000,273,-836,818,-24,-12,1000,1000,1000,279,626,-282,-999,-471,-444,-379,-349,-1000,305,373,-644,697,-114,-1000,282,-378,97,-603,-773,1000,-191,-804,-127,-957,678,-463,1000,-820,-914,1000,1000,639,571,-281,-390,112,293,-720,528,-664,750,88,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "contains(org.apache.commons.lang.text.StrMatcher):boolean",
            new int[]{-195,-43,48,357,-11,-166,-121,-298,-153,844,-250,707,544,-547,-272,16,-165,182,137,329,-943,-611,699,714,1000,703,-213,658,-1000,-428,-379,-644,-615,242,-713,228,871,-169,-1000,0,-527,-84,-43,-124,-185,-872,-1000,-596,297,930,1000,1000,501,-974,-1000,306,-719,-182,-355,-556,-641,-980,-26,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-613,-1000,-866,-1000,-1000,-190,-228,576,-266,-697,-1000,648,1000,-1000,436,957,218,-973,1000,1000,159,663,184,984,-143,636,575,-633,-452,-76,473,-483,652,-1000,564,-1000,1000,-462,-1000,-577,342,839,626,-1000,120,221,-1000,-123,1000,998,1000,749,-1000,-1000,1000,-213,-757,-1000,120,-502,490,-1000,-610,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,405,838,301,-373,1000,-60,-214,-236,461,644,285,389,-198,75,-839,844,-491,591,487,280,379,-631,-529,-1000,130,1000,-221,622,-418,751,-235,1000,-461,400,140,-632,-751,14,-150,293,-801,202,524,-1000,-557,-127,453,126,824,1000,885,-1000,-126,134,151,-113,125,-780,826,303,-1000,556,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-294,-707,-387,-594,-701,-987,-495,761,-737,-93,-124,-413,-726,-464,141,456,-840,-159,163,-216,-212,-223,429,-260,-569,491,-424,830,248,-302,-947,-923,-755,999,627,-827,729,245,214,755,662,724,-914,-951,-454,-56,887,-472,-459,-883,84,921,-673,21,507,-19,933,895,-571,707,194,-765,-706,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-412,-742,-452,932,-423,119,506,-906,-907,1000,230,42,563,654,786,-1000,-965,612,-847,-775,663,1000,-1000,658,-1000,-672,1000,455,-553,-705,667,-907,522,-575,1000,113,-456,276,911,1000,190,-1000,805,642,-1000,713,1000,-92,448,1000,26,-1000,1000,996,-797,777,160,-1000,-162,788,-612,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-210,-296,-914,1000,-417,128,-697,-1000,-1000,-38,1000,-143,949,814,1000,-1000,1000,-680,260,108,832,1000,216,1000,-485,-418,385,-137,358,-662,581,-1000,-180,-885,1000,-843,-549,-113,502,196,212,-1000,601,791,-1000,64,1000,587,-67,1000,-1000,-955,1000,261,-1000,-154,-412,-700,-421,-1000,-590,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-714,-1000,-429,1000,-832,611,474,-863,-1000,592,761,89,721,1000,538,-1000,485,458,-669,-601,1000,-386,-83,1000,52,-786,9,200,-528,235,-300,-803,389,84,1000,-39,-607,974,1000,1000,294,-1000,534,824,-1000,217,-1000,217,1000,815,19,-1000,1000,708,-699,-951,480,-976,-259,820,-967,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,726,-1000,-1000,1000,53,244,889,-1000,-1000,31,-253,44,-942,299,785,-1000,977,846,-398,158,-143,1000,-642,-89,-54,-870,794,186,218,126,425,-682,-1000,516,1000,-175,119,471,822,-261,676,-1000,576,1000,-1000,1000,1000,912,1000,876,233,-1000,1000,-498,-827,-1000,325,-1000,-42,1000,-212,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "delete(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-613,-901,752,-75,1000,777,-683,873,-400,1000,1000,-1000,-30,685,468,-1000,291,-516,-586,-299,-1000,-6,-444,-1000,217,-946,372,-590,311,-474,-952,87,105,-753,-1000,120,-334,469,327,938,986,-1000,1000,1000,-1000,-1000,-1000,549,-1000,600,-713,788,514,-213,-699,1000,1000,594,-142,236,1000,1000,1000,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{123,-607,1000,227,-672,-281,-316,577,1000,394,-774,-1000,-1000,165,-82,270,784,-892,-78,304,535,-1000,-192,-1000,-754,332,-531,-530,-863,1000,-477,-823,-1000,-678,-570,-939,888,616,-720,466,8,-446,1000,-512,-264,444,-639,1000,227,-475,-714,1000,228,-1000,1000,-1000,-228,1000,170,280,-197,261,-388,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-278,-1000,270,-1000,476,11,-383,-1000,-885,-953,-1000,434,-278,1000,567,484,-642,197,-499,716,-435,749,327,-344,616,-297,-27,-84,607,-1000,1000,-750,-476,714,105,-497,117,430,54,-845,204,-150,400,243,-79,-366,560,-1000,376,652,276,89,598,278,103,-257,554,-509,-73,-180,288,-337,209,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-981,-751,-399,-439,-415,-380,-671,-776,-1000,708,0,-218,-979,426,655,1000,784,-1000,3,92,1000,25,-1000,-757,-892,837,261,-759,1000,-634,-178,-1000,501,-751,707,80,-305,1000,606,-340,-1000,1000,379,-876,137,-5,1000,-140,403,803,859,347,88,569,-1000,198,-927,901,513,1000,1000,511,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-30,-1000,1000,304,-723,1000,193,-374,-149,163,-586,-268,-429,-647,120,495,526,1000,-257,268,432,-121,-151,-807,-861,1000,-143,-309,-165,1000,235,-814,-282,-247,-254,-177,501,-72,-89,1000,200,-1000,-332,454,23,136,232,1000,116,96,510,695,-285,-1000,661,-441,408,228,326,280,-94,-1000,400,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-144,-926,-590,1000,334,-298,30,-448,11,295,974,-178,-166,-258,675,-164,1000,-1000,-522,441,727,190,-217,1000,-425,508,499,1000,-1000,173,1000,-479,-39,-518,-1000,-1000,709,-1000,1000,1000,-81,68,421,-1000,861,-620,1000,-376,818,848,718,1000,-1000,-1000,261,287,770,-110,-489,1000,1000,-1000,1000,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-689,-1000,-128,481,-1000,67,206,-487,675,586,-176,-32,92,-112,-175,668,-521,-399,596,-388,363,-922,-32,-1000,-462,1000,-398,1000,207,1000,-1000,-295,633,-209,-177,-752,559,379,-601,88,-187,-527,-654,456,1000,-29,807,-39,-790,-782,637,699,155,-880,967,-208,1000,-386,637,742,-212,-1000,45,481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,956,301,-884,800,-12,-940,-236,146,-1000,256,697,1000,-638,672,-973,-13,1000,-121,9,450,279,-1000,527,28,-37,695,1000,-587,1000,-980,1000,983,738,-1000,323,1000,-1000,-101,923,-860,-1000,1000,1000,-1000,874,599,-1000,-1000,138,-1000,1000,-483,67,-422,1000,-1000,-28,-228,959,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{880,-991,1000,-436,-371,-1000,591,-576,-873,644,1000,-1000,-675,-1000,1000,104,93,-136,493,65,310,507,891,508,1000,573,-388,701,396,-54,-1000,-411,-672,303,754,294,1000,1000,-853,-233,-1000,-1000,-1000,-277,-122,-1000,103,1000,-1000,-201,511,431,-269,-578,-958,1000,969,824,1000,970,1000,1000,-232,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-399,-275,-949,283,199,438,47,-1000,-567,-1000,-1000,-498,1000,-554,-454,-1000,1000,-141,-1000,1000,-639,-689,691,97,27,-240,1000,1000,74,-482,337,-104,872,-1000,-741,254,-271,-446,-328,-272,-498,976,-747,-1000,1000,490,-495,18,-789,-726,-1000,-222,-357,857,731,-141,-343,67,857,-724,689,168,-527,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-949,732,-358,604,-916,-197,52,418,-1000,71,1000,580,-204,-119,302,89,-416,-151,-417,-737,345,97,-817,-793,108,338,-844,520,-145,624,1000,-399,-793,821,-1000,-207,148,-837,663,1000,256,-1000,900,884,98,-184,445,163,-116,-929,934,955,946,-1000,-746,-266,57,-902,198,-1000,-870,991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{342,156,274,832,-391,-666,-1000,-184,744,-1000,-555,298,-982,734,-309,-784,1000,-1000,-931,-1000,-338,1000,-778,-863,153,855,-994,419,-351,-459,-349,-1000,-126,-78,-462,-1000,-24,950,343,161,321,-719,-533,-276,-101,-210,1000,-38,741,58,773,-255,-151,-787,445,-179,-416,291,-455,-842,-686,-509,-1000,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-454,-544,-470,-231,281,413,-746,657,205,-894,-878,710,-111,-529,-829,467,-983,-777,680,129,-376,-833,297,418,177,338,-34,-511,-562,978,-9,-780,744,-441,392,-220,-235,-600,-910,906,-349,757,397,-16,985,-311,-226,739,-37,-476,912,96,859,241,797,603,-62,-243,-398,-288,-251,732,-956,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{158,-253,515,-629,1000,-557,-574,751,-602,-123,-552,-1000,-1000,-1000,400,357,444,-371,110,126,556,-11,560,483,1000,1000,-56,429,-93,-779,64,-1000,655,785,570,-173,8,-362,-324,244,-1000,-790,-928,896,366,-1000,-42,1000,-529,-194,295,-885,238,-949,-277,471,-147,502,-948,-146,-658,-126,-1000,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-32,-22,-1000,-1000,-545,-715,-162,-88,-1000,-1000,746,445,913,-309,-644,300,818,340,-666,335,-452,-1000,1000,-1000,122,1000,-18,428,449,188,4,512,61,-739,-1000,-624,-638,-1000,964,402,-384,-697,-966,-1000,1000,1000,608,-151,-637,343,-158,786,-89,649,1000,-824,-33,-701,690,489,696,-251,1000,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{20,-1000,257,374,-127,724,-78,-760,-742,822,385,-185,-19,70,471,297,1000,302,-590,67,-835,198,-376,350,116,-308,1000,1000,739,-985,832,146,193,-218,-574,418,-546,211,736,-1000,-155,-1000,-578,-904,-23,-1000,-762,-214,-477,-320,-1000,233,-208,63,-28,205,95,121,851,-445,-2,-715,1000,620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{966,-1000,-988,-449,270,97,555,-806,-800,-901,712,199,909,328,-591,310,1000,-1000,-366,1000,85,-270,1000,213,537,1000,1000,1000,658,-480,116,-41,-262,-1000,-217,636,-402,-330,479,-143,-1000,-332,-580,-714,905,-414,-567,108,-1000,-739,-1000,497,-591,3,380,1000,-343,27,1000,-724,451,-70,590,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{395,-253,400,-1000,881,-911,475,-427,-1000,566,1000,-1000,-518,-1000,921,-8,1000,-816,-197,699,-1000,319,1000,56,1000,1000,-266,1000,449,-178,-400,-1000,648,746,-561,-918,1000,-764,331,417,-1000,-670,-1000,1000,332,-1000,47,1000,-1000,-1000,968,786,-1000,-1000,-268,828,-212,784,-208,-384,-499,195,-822,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,230,-1000,1000,-582,-687,-87,840,-299,1000,-834,-1000,842,625,587,1000,-1000,-513,1000,189,-997,-90,1000,57,-790,-903,-1000,605,92,-144,13,167,1000,-1000,-841,551,841,1000,225,-712,-862,232,-410,-356,881,115,-312,-681,119,-109,-410,-190,423,-19,85,-237,-289,-767,943,883,538,-1000,839,-341}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-309,684,-807,410,650,163,-461,-518,-782,505,-1000,-373,-1000,254,-1000,137,332,-104,854,-1000,-625,-504,-247,350,48,464,-564,-1000,589,998,-1000,-249,589,960,-506,-1000,-574,372,668,1000,-825,-832,-248,-1000,-468,768,1000,-875,-196,47,-719,718,737,495,1000,-462,284,-663,-5,135,997,-723,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{673,-328,161,-244,-21,879,864,160,-1000,-1000,467,-368,906,-1000,-152,239,-699,-1000,-867,-650,-134,-1000,-412,840,-1000,-912,210,910,-1000,-468,249,-837,-1000,1000,151,-57,1000,395,-585,37,-215,-115,-1000,-462,-380,-745,185,231,-746,765,-273,24,78,961,1000,-301,229,480,1000,-1000,-1000,-829,-1000,-407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,214,-1000,1000,-617,-381,-593,1000,151,1000,-674,-1000,1000,322,1000,1000,-1000,-1000,1000,652,-1000,-377,1000,121,-1000,-1000,-1000,1000,746,-705,488,-157,1000,-1000,-1000,1000,1000,1000,568,-64,-1000,-520,-874,-1000,916,-246,39,-721,1000,401,-1000,109,489,723,1000,-418,-759,-494,1000,1000,538,-1000,839,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-228,464,272,-30,-21,58,373,-797,-835,-796,-108,1000,311,807,686,239,758,1000,-721,-1000,-134,-268,-602,-217,227,1000,1000,1000,252,1000,485,1000,-1000,1000,-43,-254,1000,-423,-585,-1000,-804,-46,396,121,225,-230,185,-869,-1000,-858,1000,368,-269,-579,-367,-717,229,-846,-917,-700,-1000,233,17,-228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteAll(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-811,995,-668,117,-270,29,-736,-160,92,1000,886,432,31,1000,787,585,-1000,1000,954,-237,-187,740,215,-757,-703,192,355,-1000,1000,817,376,765,782,-467,-32,843,727,-1000,-3,-70,-369,-880,1000,1000,261,-980,538,578,1000,-150,251,684,996,-465,369,267,-157,-131,-957,636,172,968,977,537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{745,74,-746,-667,-104,1000,-381,532,934,-1000,778,1000,206,-288,-988,-513,869,826,254,66,-162,422,-121,684,572,1000,50,334,176,-737,-1000,-273,1000,770,1000,434,170,-620,-1000,-838,-762,17,384,-112,-579,-1000,205,172,-118,606,-819,758,75,8,-262,-277,-1000,-839,1000,306,85,908,453,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{6,384,319,1000,566,-467,843,363,134,-502,130,649,22,-1000,9,1000,-1000,683,-634,-873,242,1000,-92,388,-1000,564,708,-248,-976,1000,-695,807,-45,264,-724,779,743,528,-202,-804,-837,811,-998,-557,-455,146,791,751,-840,-424,-305,738,508,155,-1000,817,1000,-14,293,941,-475,743,641,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-733,-1000,-1000,83,431,-526,74,-746,804,452,-1000,-506,-63,-1000,693,986,1000,1000,-570,-1000,55,1000,-1000,-17,1000,-1000,638,-618,-409,-627,-813,546,308,-723,146,-599,1000,-1000,-41,-1000,1000,-600,-171,-648,-881,308,-676,-142,1000,-968,157,342,0,-1000,48,874,-1000,1000,1000,1000,253,821,-326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-86,-421,-748,-878,497,-82,29,-144,-534,634,-168,-816,-767,-160,-609,523,279,863,932,-760,-519,-92,793,-774,-830,971,-552,810,164,-808,-706,-98,23,696,379,189,-109,357,-906,-390,-612,927,781,-650,-748,-542,209,172,-321,81,-117,999,655,-156,-687,383,954,537,496,731,936,212,130,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{977,-175,224,919,445,212,-83,-438,-101,-436,-223,389,-677,-229,138,-1000,637,-938,-353,429,207,296,-255,539,410,191,598,-473,481,-515,-1000,934,-97,938,-672,363,549,-893,369,-386,-243,-349,-982,-615,237,90,-395,940,-144,-477,-66,-111,491,108,1000,129,52,-326,-61,-926,-151,-159,-32,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,179,1000,79,-62,246,-76,613,751,-895,-798,1000,-539,400,685,240,14,-130,-1000,-608,1000,-1000,-1000,342,-366,-185,1000,637,698,-1000,85,442,-828,1000,-419,89,-680,-1000,1000,150,470,-1000,212,-278,864,558,-808,254,-82,94,171,-512,346,171,383,93,-400,27,-703,-1000,-644,-1000,-1000,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-469,-104,668,-226,274,856,579,-316,203,432,-870,21,-929,253,765,1000,-451,504,-204,-889,1000,-536,-467,-575,-494,1000,432,818,-404,-769,-532,570,-654,344,792,325,-46,531,601,-899,899,74,-766,-869,582,-1000,-457,766,-102,-992,606,-316,1000,131,192,322,-703,-101,-320,110,-501,-570,-823,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteCharAt(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{256,-1000,-15,38,1000,-587,74,-331,-1000,537,-1000,-404,-1000,1000,421,353,31,664,793,418,488,-1000,-824,-790,-882,-183,779,844,630,-1000,449,608,50,749,-900,-566,-365,-570,-541,-549,373,-668,1000,-1000,-990,-30,-1000,1000,1000,683,551,-349,1000,-49,-195,-39,1000,467,443,-999,1000,-1000,-1000,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{594,380,-152,1000,1000,-369,479,-593,354,826,-191,-727,-330,654,-884,-713,478,847,533,777,1000,-1000,1000,-1000,-1000,-251,704,834,-483,785,-449,1000,73,-1000,545,-668,1000,1000,-1000,-530,-750,788,1000,-1000,289,-183,-1000,-1000,1000,800,138,-93,-1000,-1000,-541,450,-1000,-1000,832,-115,913,-724,-39,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,162,439,-548,-610,834,945,-471,-294,990,-359,182,294,383,-608,374,-249,-483,882,-621,155,-365,-553,504,722,-808,-1000,129,424,-67,670,-1000,-60,793,-684,-1000,-610,-465,-448,1000,389,-168,-1000,1000,1000,-54,539,1000,-563,-1000,-396,-404,1000,722,413,-344,-507,370,108,771,460,-742,-532,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-673,-1000,153,-502,-794,962,-351,352,-863,1000,-253,-1000,886,43,-1000,-42,787,-1000,342,-1000,981,925,492,-1000,1000,1000,-685,-522,958,725,-390,530,-61,-608,-216,380,146,974,-862,617,-1000,425,-107,162,162,-705,821,-1000,-243,-835,560,69,-200,205,-574,1000,-141,618,18,462,-1000,939,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,321,-11,-461,598,336,-1000,1000,-241,-359,182,468,436,-1000,114,1000,-391,882,1000,-747,886,-724,30,434,1000,-866,-91,843,-400,1000,-412,-598,1000,-684,642,-610,-106,866,-153,389,810,-690,184,461,91,1000,1000,-465,-1000,-902,433,250,64,-67,-671,786,437,454,42,-1000,-462,123,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{594,-1000,-152,1000,-430,-462,479,507,367,-1000,-219,828,954,-682,341,574,1000,-321,533,-1000,-790,881,-832,171,1000,705,348,-1000,1000,-56,873,-1000,-213,747,-1000,279,-813,-736,109,259,1000,811,-1000,356,279,1000,497,1000,-1000,-1000,-1000,-93,1000,520,587,771,931,251,832,409,677,-443,-257,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-664,-1000,376,1000,565,209,834,-214,-301,863,-704,-811,294,-158,-286,369,739,-159,1000,316,970,-484,1000,504,-361,864,-741,1000,424,-317,61,-41,-737,400,289,167,-374,421,-414,101,-920,698,1000,-1000,804,-54,400,-1000,702,92,-648,-925,-1000,722,201,-255,-593,370,708,-61,88,-89,409,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{304,-893,637,-510,-747,-523,14,658,58,-1000,-348,1000,-1000,-10,196,-154,805,1000,-293,-315,24,296,1000,501,849,-331,-211,903,-509,1000,-554,-165,1000,-134,882,-1000,431,1000,-581,-173,84,828,-913,-28,-602,-363,-1000,263,126,221,184,1000,784,-1000,441,818,-51,-585,858,1000,945,552,-261,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-989,-34,0,1000,1000,-76,-792,-1000,911,-160,1000,-1000,-16,-99,135,-1000,-161,-523,-1000,-1000,-1000,856,1000,-627,-1000,551,1000,-38,-543,-784,-1000,1000,-1000,280,1000,628,1000,1000,-450,-57,-1000,305,1000,107,-1000,-120,-241,1000,648,-1000,359,1000,21,-1000,-53,-428,1000,-1000,1000,-691,833,1000,1000,-674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{864,-1000,364,549,1000,-905,438,-153,1000,-727,-440,682,667,214,1000,-1000,120,-1000,-536,-310,780,784,366,-1000,-1000,429,24,651,-60,-967,959,-1000,-430,78,-1000,441,296,235,-456,997,-1000,-1000,740,-844,26,-465,-958,1000,1000,1000,-287,-1000,-387,438,1000,1000,-1000,-1000,-369,20,-1000,-884,-603,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{504,-525,153,-966,930,426,623,43,-120,-371,-99,902,891,360,889,-169,384,-1000,301,94,-400,802,725,-126,356,429,251,-257,-555,832,400,-818,-199,6,-222,441,-396,-1000,65,-453,-325,83,-60,-1000,-550,283,210,400,218,5,26,-400,-31,656,447,81,22,-1000,-15,400,257,-156,-218,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-641,-358,-1000,1000,-129,-619,-9,-22,272,115,1000,1000,-461,48,1000,-747,921,-1000,733,-850,676,228,-248,718,-1000,628,-963,28,-346,1000,-832,-699,710,940,36,1000,-1000,-1000,-109,-366,-1000,346,888,425,-183,-401,-905,-781,635,-1000,797,-437,732,-382,613,-289,-1000,-366,-782,706,306,-400,795,-234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-101,-802,-680,-291,906,620,858,-654,690,-34,714,302,-97,-726,630,558,745,-918,287,478,351,-153,466,423,-667,-492,738,-131,-543,-805,-506,-205,967,106,681,349,-829,587,-264,146,-438,-422,904,-60,950,779,-621,-134,893,-922,510,-45,-961,-857,711,240,-98,-613,633,-638,-918,980,672,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-274,-298,434,-428,827,-177,139,-1000,1000,1000,671,-789,680,-329,1000,916,-200,1000,193,1000,-979,282,485,610,1000,-918,-297,-843,1000,-1000,-65,1000,610,1000,-943,-1000,-944,-164,-778,-1000,1000,1000,350,817,1000,354,-1000,246,-1000,1000,193,773,-1000,447,-1000,-570,-89,314,-1000,865,-727,-172,-855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{116,-532,193,1000,161,-97,-598,400,126,199,4,660,229,1000,580,350,438,400,-503,-499,-1,-943,1000,433,64,-311,525,-48,-1000,293,-441,-42,217,-601,1000,592,-308,-430,62,-331,239,-631,312,-1000,845,645,-121,181,1000,5,570,1000,-111,-206,613,-1000,-661,-1000,1000,-1000,306,1000,967,728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-270,-298,-376,528,928,159,139,-850,1000,1000,1000,1000,815,-1000,1000,702,1000,574,-613,-760,-1000,1000,1000,175,-1000,482,-1000,-1000,440,-996,1000,71,-1000,1000,-1000,-1000,-1000,1000,-1000,1000,142,979,-1000,607,1000,-150,-745,-60,-1000,1000,1000,-417,-265,59,-1000,722,-1000,1000,-1000,1000,-480,1000,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,1000,-298,-966,-238,-128,231,515,-644,1000,1000,1000,1000,185,1000,432,677,1000,733,-480,-873,-418,579,902,207,-1000,395,-1000,-247,1000,-191,715,118,1000,1000,-1000,-1000,-784,713,-1000,162,953,625,-738,446,323,30,-833,-1000,-1000,1000,-23,823,912,59,-1000,1000,-805,-213,1000,1000,-1000,1000,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-960,-243,-888,179,-339,-463,-1000,1000,1000,1000,638,57,-1000,1000,1000,186,1000,-311,1000,-613,461,-1000,-266,547,-622,-360,-1000,1000,-1000,-756,1000,811,1000,-1000,-1000,957,251,-1000,-909,1000,1000,-644,895,-259,-506,-1000,-736,-1000,1000,1000,1000,-671,440,-1000,70,-363,-1000,-20,914,-1000,180,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{345,-1000,-509,-557,-401,664,357,653,458,352,-563,-396,257,972,622,328,-295,1000,-167,-1000,1000,-770,-865,253,-137,255,1000,-743,331,90,938,-230,-210,139,716,-223,794,-305,-645,251,-1000,750,-294,-336,-749,388,-1000,273,1000,-374,847,1000,389,204,-693,391,376,-1000,-4,-76,-345,1000,-1000,406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,672,-351,666,1000,813,-1000,-381,-1000,-105,-268,1000,-1000,107,21,611,581,-746,-939,-75,-570,204,-74,-208,-501,-273,719,-342,569,62,-1000,658,-868,-397,474,-432,201,-271,-10,-227,-1000,925,420,-102,-458,936,-581,1000,1000,-393,554,-1000,-209,478,432,365,395,1000,-654,76,-603,-898,-563,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-711,571,-168,83,379,-777,-720,-722,263,4,544,1000,-947,-646,-79,26,663,-1000,-436,1000,-751,346,-390,-24,510,217,75,179,1000,877,593,36,-441,182,829,241,511,781,645,-1000,801,750,1000,-76,87,-683,559,-618,-259,333,114,-1000,692,-1000,1000,-691,932,1000,315,702,-118,116,263,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-874,767,140,289,1000,-656,-800,558,711,645,-34,446,-602,-324,-214,-526,195,-413,-1000,-778,-615,297,-716,-123,-484,-45,569,1000,220,-19,-78,-541,80,304,840,-87,785,507,-638,652,-174,-537,-883,305,481,-64,463,-296,-21,38,-1000,154,400,994,-295,5,998,112,-398,482,749,152,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{148,-573,-971,372,-372,-245,807,1000,623,-181,-681,-1000,393,1000,1000,-817,-1000,358,152,-915,1000,-299,-1000,417,50,387,1000,-187,-122,363,1000,-1000,-379,358,-243,-740,1000,176,-1000,-445,-922,825,867,-535,-1000,-59,147,-917,981,-655,748,1000,573,112,-1000,-439,835,-1000,298,859,-264,944,-1000,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{-613,-1000,-1000,606,1000,-752,679,-679,1000,283,-621,189,-259,-1000,-182,1000,274,254,-1000,1000,-237,-445,1000,-1000,-538,379,-1000,-835,1000,1000,-152,-23,-153,325,1000,1000,-945,425,-569,-1000,466,1000,1000,283,-983,687,1000,521,1000,65,126,-278,1000,477,397,546,616,427,1000,-303,-1000,1000,-23,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "deleteFirst(org.apache.commons.lang.text.StrMatcher):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,635,-640,182,-347,119,339,-735,-618,336,595,1000,-1000,-271,101,675,1000,-746,-335,283,56,676,43,65,45,-384,-548,564,-248,-346,-1000,25,101,-786,348,-1000,518,-628,668,-1000,-656,-400,344,331,-764,-62,-906,-219,-472,-712,798,-1000,-461,183,366,185,598,1000,505,928,-19,-208,359,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-150,-1000,-196,886,927,-589,-171,-71,984,849,-709,-343,-98,800,347,-421,731,-643,350,-316,-708,-1000,321,243,-1000,-1000,-509,335,240,-897,-1000,976,1000,-902,-249,1000,-28,491,-1000,-671,1000,626,668,1000,-1000,18,341,691,16,-1000,1000,1000,401,-978,-1000,-212,-155,-1000,54,858,565,476,45,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{1000,282,-886,102,-265,-305,-1000,428,516,-787,-176,-359,397,-1000,879,505,-285,1000,1000,-341,-326,-1000,471,509,297,-535,284,156,-562,-710,-1000,-1000,-370,-1000,-106,-29,337,975,-819,-29,-480,1000,596,-658,-137,326,1000,-698,248,-498,1000,400,899,74,521,-945,-83,-1000,359,1000,368,-192,-345,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{1000,-544,-1000,828,554,-1000,-126,121,-1000,-342,-400,-1000,341,-98,311,-1000,905,-241,89,-632,1000,-407,-410,142,394,356,-542,-133,-1000,1000,-25,667,446,-1000,490,-874,482,1000,-372,607,-1000,-822,737,-796,628,-221,1000,-1000,-1000,1000,-1000,47,679,667,-551,-972,-311,529,-970,255,-275,-1000,-316,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-742,-1000,78,762,408,-841,-674,374,251,-409,-240,1000,-226,800,986,74,-85,278,1000,-226,-256,-645,872,1000,118,-938,268,883,240,-1000,-1000,-1000,-1000,-324,-401,434,-28,572,-495,-1000,591,1000,849,1000,-219,88,-137,-217,-310,-768,1000,-277,881,-1000,-980,81,1000,-932,54,1000,758,-167,615,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{147,-1000,1000,802,-400,-712,-1000,-1000,963,-1000,-172,193,-1000,-1000,-36,-797,-728,-985,1000,242,-55,-1000,1000,1000,-1000,-512,586,1000,420,-881,-1000,-595,-490,-1000,-294,613,-611,978,1000,834,-896,1000,1000,-862,-1000,-1000,529,-690,1000,-1000,-2,207,574,1000,-68,738,1000,-588,5,1000,1000,-437,245,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{1000,140,973,696,194,-1000,-216,0,-971,-189,298,-1000,-28,-25,1000,-781,592,410,1000,-1000,833,-1000,-456,394,146,-180,-1000,287,1000,-894,-1000,580,327,-1000,-615,1000,-120,1000,-1000,-547,-326,-761,905,147,-712,304,530,-556,-1000,200,773,555,170,-413,-1,-226,-397,-1000,-1000,669,665,-935,30,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-691,1000,-442,-665,-41,429,363,764,-1000,-231,-1000,-166,959,652,-274,-1000,563,-614,-697,-450,-110,-470,-1000,226,-60,-964,-1000,-706,-402,993,926,1000,631,131,176,163,617,669,-1000,794,822,-1000,546,-730,12,978,819,-1000,-242,1000,-107,477,162,881,-250,53,-815,488,-1000,-904,-974,-537,646,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "endsWith(java.lang.String):boolean",
            new int[]{-75,-349,-576,319,-1000,9,189,1000,-1000,1000,189,193,478,127,1000,-65,844,-829,-279,54,451,294,-951,-285,1000,-10,647,-1000,-568,-570,61,-982,-359,-576,-1000,-764,-1000,483,-161,463,-617,1000,-19,486,-1000,1000,-679,-1000,1000,993,383,-383,617,906,1000,966,-709,-678,496,975,-1000,-638,-388,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-653,-1000,-727,1000,1000,1000,728,802,334,-805,-670,-231,384,1000,891,139,1000,-783,479,1000,-312,1000,522,-218,517,-1000,705,800,1000,647,1000,-1000,1000,564,1000,83,-222,1000,-301,1000,1000,787,601,432,146,-57,-241,508,158,-235,525,-1000,-109,-814,109,190,1000,196,1000,-794,165,-266,-1000,-612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-766,-720,-666,397,247,1000,163,122,296,-508,146,-578,-233,217,367,-1000,510,-540,541,-1000,775,900,254,-1000,225,-1000,-854,661,361,-95,-36,84,184,1000,-215,1000,-694,719,-401,658,-621,165,540,617,36,741,126,713,-397,38,96,-1000,-580,-333,718,-836,1000,-1000,846,311,190,32,-191,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-967,-956,835,510,614,789,-769,22,-404,-870,660,284,849,984,339,-383,161,-529,376,-195,-672,831,20,-691,41,-132,426,919,556,886,389,-966,414,24,691,876,57,607,-800,793,93,-317,664,-475,-825,-732,263,797,-15,-24,407,-45,-358,-295,35,-534,27,-167,688,54,949,-21,42,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,256,547,1000,-116,1000,196,1000,-279,309,1000,994,111,-1000,-1000,600,912,-73,88,-906,805,-1000,944,1000,-356,-163,-484,-315,553,-302,-687,545,840,80,655,-590,21,957,1000,901,195,-1000,-223,-529,-1000,1000,1000,1000,1000,-66,-1000,-1000,-1000,-399,814,1000,1000,278,-1000,574,503,-526,719,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-765,-187,-837,184,53,-475,749,-816,-230,-805,-670,-753,463,-515,-254,604,85,-276,479,1000,-26,-535,992,-1000,1000,503,861,-365,-378,981,285,-894,-522,-503,576,83,39,16,478,381,697,787,-522,432,26,253,-475,388,-349,297,411,267,1000,322,109,234,1000,891,-1000,-894,-866,148,57,-612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{925,-232,-511,-355,753,-256,-1000,504,-645,852,279,471,497,67,-132,964,-490,-965,1000,-513,-527,1000,515,-206,-388,828,36,555,808,-15,178,-1000,-294,-569,888,-916,1000,1000,147,696,755,-1000,-70,-551,-409,70,360,-636,512,-2,-705,1000,-825,-487,-516,679,-762,1000,-1000,284,-652,341,357,-37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "ensureCapacity(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,932,144,-468,-339,-227,-344,-678,405,677,107,294,855,-1000,-1000,1000,-43,-188,294,221,87,-1000,891,-152,-152,1000,509,-1000,-816,991,-1000,-512,390,-871,1000,-1000,-250,554,1000,-158,1000,186,-999,-1000,-1000,829,946,876,441,297,-1000,332,-1000,830,-571,760,116,413,-1000,-590,443,-9,320,933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{-478,665,-385,317,-1000,217,-1000,91,-90,-862,-329,143,-950,-220,533,-442,-816,-1000,-152,988,-1000,-607,-1000,-951,369,-557,193,427,884,-850,-1000,1000,1000,-819,841,-264,-768,-503,339,-325,-608,1000,73,-849,-173,-308,-864,778,-34,340,-216,-279,989,504,308,73,1000,-581,-531,651,311,-212,-651,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{-1000,-873,-516,372,-1000,98,-1000,136,-9,-1000,406,587,-951,47,747,-357,-1000,-1000,-452,908,-208,-1000,-1000,-778,653,-120,-166,552,810,-229,-912,1000,1000,-783,619,-267,-1000,-754,688,-817,-573,474,-172,-623,372,-599,-1000,315,-648,-261,-830,-429,535,927,467,-387,907,-1000,-688,74,524,-621,-973,754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{659,-1000,-542,-565,247,-747,-432,-1000,776,-236,498,376,1000,5,461,37,-443,-395,1000,-316,1000,215,-363,1000,1000,207,-46,-353,-1000,1000,1000,648,-407,708,-281,94,-641,-340,177,-492,713,-865,-422,212,19,957,-435,-1000,-580,206,-718,-210,-821,483,130,-693,-712,-291,-114,1000,401,416,241,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00341() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{496,-1000,-655,-1000,-199,-924,-241,-688,1000,1000,-260,-51,267,-405,-219,619,415,-364,977,-1000,1000,210,765,1000,1000,1000,-356,-391,-1000,1000,598,328,-78,-34,-1000,436,-670,-1000,99,240,629,-1000,119,-32,-90,-270,-302,-276,-350,1000,-1000,870,-1000,163,236,-624,-1000,-25,178,503,-1000,844,646,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{-481,-873,-801,712,366,143,767,98,-65,409,-954,-126,527,885,525,-134,-411,-457,-382,958,-280,541,723,-513,634,-120,613,595,-594,665,-353,-153,-221,239,-755,-665,-702,659,614,521,605,-753,-195,-952,372,-713,839,935,5,333,-147,692,543,-971,-976,144,-46,890,-654,74,444,-955,250,-894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{1000,-875,-836,-960,169,211,-263,-108,851,-458,659,-73,1000,898,297,437,-149,-655,1000,-646,-598,1000,-151,1000,1000,-429,510,-1000,-912,1000,955,1000,-526,124,-1000,42,297,-1000,-1000,625,-668,-564,-446,-151,-1000,1000,80,-185,-590,-58,66,-221,-60,49,-1000,-834,-1000,-923,-1000,1000,-513,1000,628,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(java.lang.Object):boolean",
            new int[]{66,-1000,-784,567,979,-403,200,-614,-150,31,-190,261,1000,1000,-246,575,-569,-624,148,617,724,1000,1000,1000,964,-511,611,116,-1000,1000,553,-408,-404,1000,-1000,-944,-546,409,114,1000,1000,-497,191,-257,-223,63,1000,517,-764,-268,-291,186,-192,-1000,-1000,-315,-1000,1000,-1000,12,691,-412,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{1000,32,302,-698,1000,-204,-1000,-227,680,-726,-675,-1000,-145,-1000,-157,652,514,928,795,1000,-645,830,-935,170,-1000,-983,-772,503,-638,576,395,-1000,-211,-402,-755,546,989,-871,-462,1000,-1000,527,1000,375,-703,-298,-1000,-1000,-1000,1000,-438,-633,761,194,1000,-56,-1000,-1000,1000,-1000,-806,-959,-733,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{22,-979,1000,-833,-3,-770,-993,482,72,165,-424,219,314,960,1000,557,1000,436,-250,123,-431,-150,181,-564,303,135,-433,170,-444,87,170,749,-453,293,-548,-780,-576,1000,1000,-90,-241,-629,-384,952,-102,1000,249,679,-583,779,27,958,39,274,-79,-296,1000,-201,-591,890,719,400,-356,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{756,-253,944,1000,139,501,-612,236,1000,-216,-1000,-151,-239,145,539,682,732,479,181,-245,74,-670,669,507,233,-176,-567,148,-350,224,537,-763,-1000,93,-1000,-566,958,346,755,-1000,-459,89,210,107,249,314,1000,600,349,-1000,-943,793,548,-369,-438,-499,-161,-79,793,33,-532,-1000,-810,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{911,276,443,381,374,-600,171,-101,-467,-297,-35,-109,1000,1000,270,77,873,670,-274,-576,-516,-142,106,-3,966,-417,-330,-825,397,140,391,615,-732,-151,-283,115,-569,589,568,-531,-950,156,396,-154,-291,671,-106,-8,877,616,115,-115,-132,217,344,-1000,-524,-176,300,-40,1000,176,-346,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-718,-1000,1000,-1000,153,-363,5,-1000,1000,618,-861,-664,933,-1000,-536,1000,-582,1000,1000,773,1000,-996,591,-1000,1000,1000,-1000,518,290,360,591,-1000,-1000,-222,-1000,-529,-1000,-45,1000,3,-533,-1000,-442,523,1000,1000,1000,1000,1000,-779,-1000,1000,-470,-1000,-896,214,-95,737,-614,1000,207,-519,-1000,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-430,-1000,922,-1000,1000,268,182,1000,583,1000,805,-681,230,-840,-71,1000,-1000,334,1000,1000,621,-996,608,-723,719,1000,404,1000,842,1000,1000,-1000,1000,-492,-1000,-114,-1000,-28,-268,458,-445,-1000,-833,-313,1000,1000,-205,1000,779,-634,-893,1000,-247,-912,-153,1000,43,441,53,1000,-1,-736,-104,595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{934,-1000,-772,575,630,-151,967,151,-890,870,1000,175,1000,1000,225,-208,-582,-579,400,477,930,982,1000,-418,1000,1000,-374,105,360,66,390,18,113,529,259,-1000,-537,1000,1000,-383,-1000,-416,-187,1000,-79,204,111,1000,-491,1000,883,1000,-85,-209,823,107,-915,545,-353,1000,365,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-22,453,-377,1000,-1000,-631,-993,-885,622,-1000,-1000,-907,1000,325,-653,946,305,1000,-223,-1000,-144,408,-1000,740,-803,-1000,-1000,-1000,-1000,151,1000,-677,-1000,-211,-1000,-971,-205,625,1000,-1000,-424,-1000,378,89,956,-854,1000,-160,-570,25,-880,-418,310,-340,-1000,-1000,-1000,-129,782,-500,-312,-1000,7,-183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equals(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-1000,1000,914,-131,423,199,-640,-1000,129,819,400,651,215,-1000,962,-526,-176,-165,-1000,-810,-1000,-582,-1000,1000,761,-663,1000,-1000,-720,1000,321,-256,340,610,444,1000,1000,-1000,-1000,-950,440,454,1000,-1000,-647,-1000,-400,-671,-578,287,-229,-400,939,-461,344,-239,109,-1000,411,-1000,235,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{672,-1000,1000,698,-960,-1000,743,138,-861,504,-911,-331,196,-34,321,-583,879,-853,243,-14,1000,-515,1000,706,155,-933,-859,-1000,382,-161,-716,125,-465,1000,1000,-223,-221,194,811,-877,252,-165,-339,14,837,-588,1000,1000,1000,-477,1000,-1000,792,-18,-218,-106,-912,-864,134,231,-1000,-612,854,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{847,-1000,-791,-74,-671,435,284,1000,-413,1000,-946,71,-1000,612,-363,-1000,508,-846,268,-685,-59,186,-663,294,1000,-97,-310,-1000,540,1000,-569,868,-1000,1000,1000,-1000,-811,-326,59,-295,1000,1000,-373,-693,137,-1000,1000,-285,1000,185,280,-990,105,-800,-234,372,-1000,-146,-1000,520,-501,102,-1000,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-813,221,125,-714,-794,-117,377,-696,-568,-896,639,-42,-891,-82,141,-946,-880,96,739,-691,-971,630,-71,667,-226,299,591,871,-433,950,264,540,-402,-903,200,-345,540,-828,226,-296,482,-616,-461,26,-20,455,-67,-90,150,782,706,-165,-591,502,-506,-37,-640,-168,621,-593,572,-366,219,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-544,-1000,-658,24,238,-1000,268,114,299,-745,-1000,388,695,-724,-539,717,95,-1000,-192,422,32,543,1000,1000,134,59,85,924,-737,37,32,-234,-106,-828,1000,-28,-379,1000,756,-1000,-275,-118,181,-460,-600,1000,-235,-1000,1000,-1000,97,-1000,-1000,-1000,-511,-1000,-1000,1000,-268,-987,-759,1000,39,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{733,275,-494,-928,-809,-331,1000,-311,365,201,-676,-1000,462,1000,-1000,-693,-574,6,-1000,1000,1000,-1000,-308,-1000,-83,-1000,-1000,-1000,550,447,-602,-425,-1000,1000,-1000,-1000,-854,-1000,548,596,-888,-196,-1000,624,-683,-1000,-33,1000,800,899,-172,1000,989,1000,-176,864,-284,-967,1000,1000,-395,-1000,-75,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{-154,-307,1000,485,-256,-147,464,104,778,-570,-176,-438,-813,163,569,-185,-493,-921,-194,-3,343,-194,555,1000,1000,375,-726,-400,-325,677,855,1000,-498,-413,1000,-226,-40,260,902,-131,-307,864,-528,-713,416,-127,767,-437,592,681,545,-1000,-59,-354,26,216,125,863,-1000,69,39,428,-39,-498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "equalsIgnoreCase(org.apache.commons.lang.text.StrBuilder):boolean",
            new int[]{587,-175,692,1000,-71,-952,419,-835,-250,807,-561,-513,32,292,-587,-782,-837,-15,137,364,502,-1000,1000,744,-44,-728,-742,-1000,266,361,-72,-805,-419,1000,1000,260,-1000,-415,927,335,-515,235,429,839,443,-1000,972,1000,592,-91,715,-944,515,522,-614,217,-127,-254,291,630,-529,-886,-332,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("ARRAY:[C:80:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{-1000,596,-519,-631,-317,-1000,859,-32,1000,-71,888,-426,1000,1000,-768,806,-506,741,1000,-42,1000,-574,790,-1000,-1000,571,-1000,-862,276,846,-400,591,-754,1000,762,-1000,1000,933,540,460,-222,611,1000,605,252,-207,969,-588,-1000,-1000,-1000,-1000,-983,-837,-655,780,-143,25,-1000,-760,783,727,820,401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:YA==:24:java.lang.Character:UA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{676,597,-631,545,6,482,-376,238,449,102,530,176,-32,807,-164,782,2,-800,-688,-88,416,-573,-862,554,-681,-852,619,-227,-189,885,-553,502,-823,-79,-1000,-29,417,428,77,-660,-1000,-61,984,-638,-979,106,-975,-597,460,39,400,396,222,-440,603,923,882,513,-936,-284,1000,809,851,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:KA==:24:java.lang.Character:BQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{117,1000,-243,1000,-336,-461,972,-712,444,-620,-190,-743,128,-810,536,-901,898,406,-1000,-344,133,927,872,279,-163,1000,1000,-45,-135,-222,768,-751,77,988,409,-357,297,204,567,-17,-17,801,-40,508,98,976,238,-66,204,438,83,-832,-1000,-201,230,918,122,330,-40,-336,-68,-425,925,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("ARRAY:[C:100:24:java.lang.Character:GA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{-537,-1000,-211,-100,-677,-1000,449,247,1000,-554,472,-339,334,736,-1000,954,-1000,489,-708,55,198,13,593,-1000,1000,-130,-1000,198,-554,1000,-1000,722,-842,982,762,-598,306,147,-685,926,951,556,1000,571,173,-54,1000,-34,347,-1000,-1000,-1000,-1000,1000,-774,721,-305,-643,-123,1000,-782,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("ARRAY:[C:2:24:java.lang.Character:Iw==:24:java.lang.Character:dw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{1000,530,-262,80,1000,-638,104,833,241,647,-993,295,-144,-1000,803,-649,12,-1000,-459,1000,-1000,843,597,-487,13,-992,988,1000,-611,593,138,-1000,1000,-1000,113,1000,-1000,-86,357,-304,808,-1000,-539,158,-379,-98,-1000,1000,-489,1000,-1000,-958,1000,1000,469,-1000,329,-930,1000,-124,-1000,-812,-510,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(char[]):char[]",
            new int[]{862,-19,-640,706,-161,264,659,-640,-518,-242,858,293,327,1000,-16,808,521,-448,42,-249,748,825,-712,629,675,-120,334,-1000,-1000,1000,-313,686,-730,422,-1000,-1000,631,255,550,-616,-237,109,-371,-613,-201,164,-631,-621,141,400,1000,1000,523,927,-263,966,-172,1000,-722,-853,796,758,844,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-53,-676,-546,-97,-13,-223,1000,138,-371,-258,493,-926,250,-155,-818,-235,-553,944,1000,-52,-1000,-253,444,677,846,427,78,-371,-700,274,973,-1000,1000,788,-579,406,905,-529,-92,400,-705,-1000,-201,1000,-94,969,568,767,50,128,-175,198,-407,-730,1000,520,695,552,258,-597,-608,454,34,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{1000,-489,-981,634,-91,405,-242,-168,-537,1000,-940,-897,-358,566,-628,-1000,275,-85,352,32,-3,-162,201,537,-758,-9,-399,1000,531,-1000,575,-1000,817,365,175,-536,343,-365,294,-1000,-903,-179,608,69,-1000,170,1000,-260,-792,111,-453,-846,461,-452,85,-299,-28,626,-1000,512,-1000,-1000,827,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{632,122,20,701,-780,834,437,-638,-734,692,10,421,511,-394,1000,-403,409,-578,-1000,1000,226,565,-1000,331,824,-722,-1000,-658,835,441,938,-1000,-1000,1000,-225,-179,-1000,-1000,-53,-195,898,-150,-125,390,304,-488,-1000,1000,-247,-313,-62,-673,190,-368,-1000,803,-319,1000,549,1000,-1000,10,1000,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00370() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-272,-158,-163,775,-524,-823,-525,-594,-720,253,979,273,335,-1000,-1000,31,866,702,-15,1000,-316,619,-1000,143,674,-684,-1000,-576,220,841,986,-1000,-1000,410,-221,521,-544,-1000,-613,420,789,-989,109,-286,222,243,-517,727,-193,-99,-716,-589,-487,-760,-1000,1000,734,929,17,629,-898,320,281,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00371() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-712,536,558,-1000,828,1000,782,188,1000,153,-387,1000,-666,-735,1000,379,670,1000,561,-409,-1000,293,-1000,222,-748,-1000,1000,-754,-960,-439,444,83,939,-1000,-507,1000,1000,608,1000,-180,-444,935,-138,222,344,-39,565,-503,-198,276,-1000,-430,-82,224,310,-91,1000,-956,-813,-692,1000,1000,-640,-798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00372() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-53,-1000,128,249,1000,-674,899,138,-101,-959,-524,91,788,-540,859,-612,-958,180,293,-52,-113,-1000,111,39,-233,-181,400,-230,-444,261,-61,-591,-188,500,-853,111,986,836,1000,-617,331,292,-236,1000,260,-98,1000,740,50,-467,835,-134,-369,-730,-895,1000,52,486,216,565,-1000,-223,-199,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00373() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-896,-682,20,557,1000,834,97,929,-734,-504,10,421,434,-1000,1000,622,-202,-1000,250,831,-1000,392,614,-604,-281,-615,1000,-1000,544,-695,886,-1000,-273,-14,-1000,-389,1000,-140,1000,-195,12,-150,2,-85,-922,127,955,940,-247,-500,583,-72,-324,846,511,-1000,303,611,191,-257,-1000,-477,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00374() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getChars(int,int,char[],int):void",
            new int[]{-382,-1000,1000,-73,916,-99,644,832,967,-927,897,20,512,-936,688,685,499,1000,722,-606,-1000,-789,-1000,218,1000,-687,1000,-1000,-755,616,1000,-573,-958,-73,-993,1000,1000,765,436,1000,1000,-624,-1000,698,823,941,1000,1000,475,-54,179,-1000,-1000,-447,787,1000,1000,-175,490,974,-347,1000,-1000,-675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00375() {
        org.junit.Assert.assertEquals("java.lang.String:IC0xMDAwIA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{339,329,159,-235,488,108,689,-1000,478,-191,-259,-68,738,-1000,-456,-868,454,502,-622,-534,-1000,-315,-409,498,-538,-114,577,391,496,902,653,714,-647,-511,633,-266,-95,595,-518,-61,311,-207,-897,263,661,435,-623,-1000,-708,470,-441,-713,-1000,-508,-346,-1000,13,-499,-237,-899,352,715,31,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00376() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDAwMDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{290,-354,624,-1000,-631,-233,89,-832,117,743,42,-147,1000,-1000,63,959,433,-987,-255,332,1000,494,1000,1000,-52,-380,-406,1000,1000,1000,722,476,325,-443,851,-886,-74,1000,494,638,918,1000,382,-966,-763,-1000,-377,113,-1000,-244,-832,-1000,-63,1000,469,1000,386,975,346,-1000,-325,966,-818,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00377() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{-97,-44,249,-538,-52,246,-453,-960,1000,-1000,604,382,38,-652,-1000,226,544,528,-602,-106,93,15,-1000,-76,407,-1000,855,426,369,-1000,31,75,-1000,-1000,1000,474,1000,776,-243,-442,871,486,-827,1,-171,-153,-459,939,242,1000,346,-1000,-1000,793,-490,1000,-868,-817,1000,243,118,-1000,-258,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00378() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{343,-1000,747,-24,-288,571,474,411,-270,234,668,-433,1000,379,1000,-1000,-859,1000,-1000,1000,397,1000,235,-372,-1000,-693,431,418,-417,1000,883,669,-1000,-806,-651,-1000,-843,-661,232,-1000,1000,-866,-201,617,653,-314,1000,157,637,-141,238,-1000,-1000,-1000,1000,807,1000,-318,268,-1000,314,426,-650,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00379() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{999,311,-431,-748,600,693,-253,440,-25,524,1000,458,-389,-1000,115,286,260,-231,280,-327,-403,-152,-156,-6,-78,-1000,-733,117,1000,-153,574,584,-389,648,319,-335,-438,1000,-277,200,-157,925,-1000,1000,-981,-752,-1000,950,-141,630,883,-812,-413,612,1000,278,-531,-83,-518,52,-494,-1000,595,-704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00380() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{875,-757,752,400,524,626,-183,691,-515,581,1000,-956,-464,649,1000,-42,-606,1000,340,-494,171,210,-637,-446,-1000,-66,-1000,-400,-400,33,877,1000,242,112,-1000,-459,-813,-1000,-400,-968,572,-577,-671,911,1000,69,1000,-178,918,418,237,1000,-231,-1000,1000,-552,657,-100,738,-1000,-342,1000,480,-857}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00381() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNewLineText():java.lang.String",
            new int[]{-239,-1000,715,1000,63,80,801,-266,-544,1000,167,-1000,622,181,625,-536,-964,1000,283,287,-473,1000,63,-317,-868,-822,-1000,-360,-1000,-872,916,1000,132,-481,-7,851,-583,466,-706,-1000,209,-1000,164,-376,672,1,1000,-285,834,-183,116,1000,308,-1000,624,-1000,1000,46,531,-296,-1000,-1000,561,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00382() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-1000,-1000,-353,418,-118,866,646,-1000,-1000,-991,-1000,279,959,-1000,57,840,460,748,171,-216,131,-904,239,662,68,-323,56,-400,-53,211,139,-928,355,-1000,-278,532,-938,-414,-282,728,-447,934,1000,-578,-224,180,-68,188,582,-477,-1000,-416,-1000,-879,-638,628,-651,458,-910,-86,-247,493,-224,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00383() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-1000,-350,-334,396,-541,456,-208,-430,-1000,-958,-309,1000,794,-671,-1000,1000,798,683,-1000,489,1000,-1000,-1000,913,1000,-1000,-1000,1000,1000,-870,1000,-483,-825,-1000,-1000,-943,-1000,-606,-1000,1000,-132,1000,1000,-1000,-541,-252,682,1000,-88,280,-1000,-1000,-1000,-199,-1000,-858,-1000,76,-795,-1000,-370,1000,834,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00384() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDA=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-1000,264,-162,631,459,796,-801,-338,-972,-53,-756,1000,174,-1000,-975,1000,-275,1000,-1000,-795,1000,-1000,-1000,1000,1000,-1000,42,1000,558,-312,1000,-1000,1000,-1000,-1000,-695,227,-565,385,1000,-341,1000,1000,-1000,1000,-268,1000,-16,-635,969,-84,-1000,-1000,-999,-1000,667,-1000,455,-997,-1000,-777,1000,-634,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00385() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-1000,-98,-215,694,759,472,558,-686,-499,-824,-1000,579,1000,-593,-1000,853,348,1000,-571,-86,1000,-692,3,916,752,-1000,698,-124,1000,-1000,901,636,70,-1000,-1000,-850,-1000,168,-476,1000,-1000,1000,1000,-1000,-327,1000,1000,1000,-284,-120,-797,-1000,-744,-602,-1000,27,-454,1000,382,-412,842,435,172,949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00386() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{-552,-139,-96,285,462,18,965,-530,-853,1000,625,-633,1000,835,-542,794,440,1000,151,670,-118,625,1000,69,-54,-509,69,-329,255,-703,-306,705,-538,1000,-891,-1000,-1000,-640,-1000,-134,-1000,-537,730,-266,-1000,209,1000,1000,266,-943,-1000,-842,775,-700,153,-469,57,722,322,-12,1000,1000,551,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00387() {
        org.junit.Assert.assertEquals("java.lang.String:LTMxNA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "getNullText():java.lang.String",
            new int[]{1000,-1000,-287,918,-184,244,-131,-890,543,1000,964,-721,212,-314,-915,-257,115,557,1000,-499,-123,697,548,172,-763,651,331,-1000,-485,-786,-996,-428,859,541,31,-795,-381,1000,-327,76,-61,235,980,95,-541,-448,-1000,-506,439,-861,-885,358,171,-294,674,580,-184,913,-347,406,119,-21,-1000,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00388() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{143,620,-605,1000,516,-508,-788,-1000,-226,-263,321,-397,-11,-101,-1000,-872,1000,-417,-468,-1000,-675,811,687,275,-884,417,-1000,1000,-1000,-377,279,-495,-20,371,664,-942,955,656,592,-258,-1000,-1000,1000,260,-263,-446,948,-356,-49,-490,482,-608,-726,-439,1000,469,1000,323,203,1000,-1000,1000,-379,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00389() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{446,-552,939,-481,83,-141,-223,-394,-850,477,802,663,-152,642,-3,1000,1000,-51,-162,356,1000,-84,1000,-1000,-48,1000,-1000,-569,-156,-851,268,-1000,18,-1000,218,-634,-18,326,-146,-574,-943,-1000,-723,-178,-1000,1000,221,-63,-105,-1000,-698,1000,-120,291,1000,1000,-80,77,-1000,822,-556,467,226,-590}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00390() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{878,586,-1000,1000,869,-98,-576,-617,-1000,836,1000,-813,1000,1000,68,-1000,1000,634,-1000,-543,-1000,1000,-105,-1000,-274,458,623,713,-1000,-1000,1000,-1000,372,1000,1000,56,630,535,-50,-268,-1000,-449,1000,1000,-1000,131,573,-1000,-1000,617,473,1000,-773,-1000,-769,133,1000,-603,681,1000,55,1000,-29,12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00391() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{44,-43,209,953,-120,629,-131,172,-1000,294,1000,1000,-739,-39,-458,1000,-60,-321,-319,1000,1000,42,-762,-1000,-67,144,-175,-703,1000,-234,-1000,-552,793,-1000,1000,202,-294,409,1000,469,-546,-371,-877,-691,161,1000,-132,-512,447,-259,-304,260,983,138,249,497,-1000,464,-1000,341,417,10,-664,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00392() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{-294,297,874,460,-228,-607,-695,451,464,-989,-315,718,-93,-980,-408,777,-1000,-653,814,524,748,-253,-951,821,95,-375,-123,419,677,797,-739,-572,382,698,-834,-688,-640,937,577,969,225,349,-800,-184,599,713,-568,19,848,-807,-205,-990,889,170,-22,251,-716,684,-145,842,-503,-80,432,-968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00393() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{121,380,-489,844,776,-671,-938,-302,-1000,-496,1000,389,601,48,-279,-412,1000,-82,-133,14,31,811,-687,-457,-166,-154,894,1000,-1000,-392,28,50,632,1000,757,-747,882,234,856,565,-673,-240,96,857,1000,677,43,-600,-548,-490,-6,95,-58,-1000,-895,312,783,211,749,1000,403,675,86,-354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00394() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{-863,251,436,606,316,-176,-600,-218,-686,198,373,953,-630,-58,-1000,999,400,-1000,1000,1000,1000,929,-173,-335,-795,-704,-310,205,416,-455,-1000,327,1000,659,374,-825,69,217,-297,1000,-371,-464,-1000,-1000,938,1000,-568,-589,179,2,-205,-1000,737,1000,-682,289,-170,1000,135,842,-747,-80,-31,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00395() {
        org.junit.Assert.assertEquals("java.lang.Integer:OA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{354,-253,813,-673,-40,-766,-170,-91,464,-463,-1000,1000,93,937,301,1000,1000,-866,338,-124,661,313,1000,-859,172,-62,165,-439,-494,-458,149,-661,482,-1000,-275,-1000,4,178,-681,-438,-403,-870,-1000,35,-1000,1000,-499,-615,-920,-1000,-154,1000,-345,-493,491,996,1000,456,-528,1000,-437,-12,149,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00396() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char):int",
            new int[]{-1000,173,-416,-246,-580,123,-145,-223,1000,-197,220,109,-438,-398,-621,-72,8,-698,-65,-691,-68,-800,432,1000,-340,-1000,-994,372,-183,829,203,-1000,-378,-274,-883,-585,-1000,-94,-335,337,-583,-542,-345,469,-611,-256,52,193,637,-1000,-409,-380,123,419,849,388,1000,811,-601,1000,-1000,-232,570,-539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00397() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-420,-874,-999,285,-701,421,-356,-850,-856,-361,-408,-1000,646,185,-540,-1000,-113,1000,-1,925,-409,189,-790,310,-395,-522,-328,-1000,-1000,-572,1000,-252,1000,1000,-1000,1000,88,-1000,755,1000,-1000,220,234,495,773,1000,-405,511,596,1000,582,1000,-364,908,759,1000,970,-273,691,-1000,-399,1000,-55,-255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00398() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-422,-426,-551,-5,-98,-498,143,-641,24,74,176,-61,-84,506,-343,1000,-665,-400,-564,144,-476,406,-512,489,-24,-451,539,-53,-153,-582,-400,445,327,559,-589,-361,553,-657,475,-488,-268,-259,589,262,578,349,439,-122,518,-114,24,525,-66,809,306,49,914,283,270,-493,-443,84,546,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00399() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-905,152,-551,-1000,266,-697,143,-343,1000,366,172,671,-450,-121,-80,1000,-865,-728,357,-661,23,406,211,-934,-24,930,1000,-53,-944,-1000,328,1000,-459,370,-67,-361,558,-39,293,-488,-142,-849,462,-213,4,-193,976,-760,125,-1000,-1000,89,-66,222,306,-816,732,624,270,6,340,140,1000,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00400() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-513,-77,-1000,453,258,-703,296,269,-26,689,-549,-402,-112,1000,123,-1000,-634,1000,-98,-952,-716,-1000,897,-84,732,1000,-223,543,858,-1000,1000,1000,-1000,27,-937,140,83,130,-84,240,-700,-1000,86,1000,-79,-648,-283,-136,-349,-606,-1000,93,444,820,591,18,1000,-488,-588,-7,-207,470,4,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00401() {
        org.junit.Assert.assertEquals("java.lang.Integer:Ng==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{1000,-43,166,-650,497,-1000,44,251,1000,-337,-380,-138,-156,-385,631,1000,901,1000,-933,390,-140,561,-393,-179,329,188,1000,191,560,-892,-516,633,-524,-1000,-368,-649,869,-841,-101,83,464,-535,674,39,-121,-627,881,-1000,-260,-1000,-928,442,-212,191,297,-1000,722,-963,-590,268,-366,-556,574,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00402() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-110,-820,688,-473,-750,-731,-942,815,370,784,1000,-3,-378,973,-208,1000,1000,-1000,-612,779,-998,447,-1000,1000,234,-1000,-10,-1000,606,-1000,-1000,1000,1000,-17,-1000,1000,-145,-264,921,-1000,-71,-944,578,640,-1000,-137,1000,-673,52,657,-1000,615,-383,971,747,1000,1000,713,-495,151,-803,-240,410,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00403() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(char,int):int",
            new int[]{-974,-142,-114,426,27,-1000,-51,-1000,-230,597,412,-1000,-907,785,589,434,-192,-960,-1000,-952,-932,-311,-288,843,-295,-146,-362,-613,503,-73,-265,939,944,-262,-987,510,1000,-1000,-616,-246,-16,-296,1000,-138,147,-368,216,-549,171,-826,153,1000,-625,1000,-896,-1000,1000,247,-96,-239,-1000,-74,240,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00404() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{13,995,830,522,-718,772,-668,-315,1000,851,-72,-1000,-334,-689,-49,699,610,514,855,-193,-1000,1000,798,491,709,-542,-190,-977,72,-78,289,971,328,291,1000,-278,-92,-853,231,42,251,361,229,-212,1000,-108,-1000,600,462,639,-488,327,-1000,438,260,327,980,-112,-7,150,-107,-1000,-831,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00405() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-1000,-150,204,1000,239,242,1000,-339,57,243,-187,296,1000,465,40,1000,615,625,200,737,632,-807,-1000,-1000,618,-1000,-874,-143,-336,-558,127,470,-43,-316,-298,1000,1000,1000,-128,-797,417,-720,-1000,-541,-1000,1000,-44,328,-261,-1000,-1000,797,-1000,1000,12,192,-836,-439,530,1000,-121,-357,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00406() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-918,689,-1000,567,-343,-351,-84,401,-400,-417,201,-94,1000,814,1000,1000,216,280,398,527,872,1000,1000,161,161,321,-850,-533,-302,-1000,1000,690,-1000,-850,845,1000,156,1000,266,-179,487,15,-812,-326,-259,175,-186,-106,291,-1000,-1000,842,-434,-474,-238,367,491,-1000,787,-1000,-1000,171,-234,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00407() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-1000,-1000,-881,868,251,-1000,9,398,-1000,-1000,1000,824,1000,179,237,950,-576,1000,782,-985,1000,400,-267,1000,1000,-1000,-6,-663,998,-246,380,645,-1000,-223,1000,1000,196,1000,1000,623,1000,236,-801,1000,-143,815,846,78,838,-967,-1000,1000,-743,-511,1000,108,840,-808,1000,-405,-739,-1000,-31,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00408() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-580,-947,-306,576,164,-1000,8,628,-1000,1000,-153,691,875,176,616,863,-697,-333,1000,341,857,-871,-957,-1000,-472,429,370,521,-161,425,-1000,-120,-919,1000,-1000,762,-765,-550,177,-193,-339,1000,-921,-463,-673,381,700,1000,305,-1000,-587,1000,-657,-346,-658,-80,-724,-822,-488,1000,-464,-481,887,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00409() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-1000,-763,-1000,697,448,-481,241,387,-975,-1000,1000,234,1000,10,-252,1000,-849,695,753,908,1000,1000,991,-187,-425,-1000,-296,-761,348,-1000,1000,1000,-1000,-719,1000,1000,98,1000,1000,-48,1000,1000,-868,-1000,-143,459,846,-89,1000,-1000,-1000,889,-743,-729,1000,1000,1000,-186,1000,-1000,-1000,420,-870,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00410() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-788,-1000,-963,-1000,727,-751,-866,694,194,-574,1000,1000,951,-338,70,-2,-416,436,647,-1000,1000,140,-110,1000,-1000,-382,-224,-228,-411,-1000,898,223,56,541,1000,588,-51,928,-389,-1000,-613,508,-88,607,-438,431,294,-20,1000,-398,1000,1000,1000,222,801,748,397,120,787,-1000,-164,-622,682,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00411() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{358,192,-616,-520,-903,-182,-479,491,-166,137,242,423,-830,-193,14,356,-53,-112,833,743,-469,-1000,-61,-1000,-915,-598,431,849,195,1000,-457,499,882,665,-250,968,-1000,117,-238,557,-1000,-453,-331,-873,-663,427,625,1000,-143,-698,-985,254,-620,778,-542,-962,-1000,768,-558,290,-551,783,683,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00412() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-149,689,602,-930,-343,-706,262,-474,320,97,-494,397,736,814,-1000,-23,418,333,-96,1000,872,-165,197,999,248,934,290,13,-48,-349,158,-540,419,49,-273,-344,545,368,-799,-255,247,60,415,-32,-259,775,-997,-932,281,817,900,570,343,481,-238,1000,807,67,676,210,31,-546,-400,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00413() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-463,-1000,-769,109,164,-515,209,917,256,112,176,-754,159,-91,616,-316,-938,-584,-393,341,857,289,557,-383,-472,-539,-842,216,-1000,-1000,1000,1000,-919,316,695,379,-582,239,-787,-345,-1000,1000,-674,366,-1000,383,519,369,614,-739,926,790,1000,493,257,171,1000,109,-488,-619,-359,-477,833,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00414() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-1000,-1000,-1000,-536,-605,298,-696,732,-164,-1000,1000,225,82,261,618,359,-398,927,139,-411,1000,1000,-521,1000,-1000,311,-765,-1000,490,-1000,1000,443,-317,-1000,1000,878,622,1000,604,-890,-1000,310,-228,116,695,210,641,-1000,1000,-579,1000,842,876,-284,-1000,1000,1000,-341,1000,-1000,-790,72,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00415() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String):int",
            new int[]{-467,-1000,400,-94,-667,-201,-757,973,-1000,-489,1000,531,53,1000,1000,301,1000,683,1000,715,380,315,-819,448,179,-206,695,-390,1000,318,849,-623,-931,791,-977,1000,-473,1000,1000,631,112,51,-426,129,-1000,559,166,1000,715,34,-109,1000,-514,1000,320,498,1000,-486,1000,-855,-1000,608,-1000,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00416() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{402,476,1000,1000,-81,149,-86,-244,808,-1000,-1000,769,1000,662,109,-88,-1000,-848,1000,325,-766,-205,1000,394,-226,-511,-782,-972,491,-965,410,-180,306,-122,-147,-1000,706,-485,1000,-296,-524,-971,204,835,-1000,-791,-562,-738,264,544,-172,334,-462,-1000,-314,-138,-370,-579,849,159,469,42,984,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00417() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-21,-157,-978,186,-264,1000,-2,-571,-112,201,-19,117,539,89,-73,1000,706,-557,340,602,14,707,270,905,789,503,-672,222,-738,787,553,-1000,389,-187,0,400,729,595,292,513,556,394,702,279,-1000,-100,107,136,17,700,-337,62,1000,256,173,-927,93,-465,41,-587,-423,1000,-338,-723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00418() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{468,-249,309,442,1000,-293,603,-1000,808,-507,-608,11,-93,355,642,-926,-643,-948,-169,65,327,-726,-121,-969,933,43,-678,-962,1000,555,-146,653,-894,369,834,682,243,-352,-355,445,-626,-4,-876,-1000,26,-236,-870,50,975,-283,-517,-827,-763,482,1000,307,639,-228,381,-411,-164,-370,1000,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00419() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{209,-212,-526,-278,154,-102,-225,-455,-387,546,1000,132,-433,-82,135,371,-863,-609,949,-446,1000,373,-983,-869,-490,438,669,669,-201,733,-130,266,38,-211,348,606,-978,430,-217,-249,722,1000,-333,-208,376,-284,221,-1000,196,399,-326,-932,-674,86,650,-323,-375,42,301,388,-1000,-593,130,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00420() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-619,-157,-1000,506,-467,1000,-461,476,308,99,-165,197,512,511,-520,992,-2,-551,539,602,-315,972,440,1000,901,368,-372,222,-1000,-144,832,-1000,866,9,-628,-545,352,532,219,449,921,250,1000,1000,-1000,495,107,-569,-539,1000,-419,285,878,-175,-478,-1000,-781,96,-13,419,-537,1000,-572,-906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00421() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{70,-1000,1000,1000,711,1000,-1000,1000,876,388,-1000,365,78,1000,-1000,335,-584,-1000,1000,805,-312,545,397,1000,806,628,-130,-329,-1000,-896,1000,160,-808,-645,-1000,-1000,-296,-928,387,-259,1000,683,1000,1000,-1000,-631,-1000,-398,-1000,1000,-1000,165,515,384,-1000,-618,-1000,209,-238,252,-1000,969,-612,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00422() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-176,-1000,-573,631,711,369,-949,608,210,-445,-1000,621,-115,776,-520,-458,-1000,-669,1000,1000,-426,-91,1000,320,-249,-422,-130,-1000,-1000,-813,650,-717,619,-719,-1000,-892,-763,-501,141,-1000,8,322,815,890,-954,-92,-1000,-791,-539,1000,-1000,-447,-424,384,-739,-485,-972,445,908,973,-198,969,115,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00423() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{727,122,-494,-472,-298,555,882,-1000,808,1000,1000,-16,-309,-686,-236,1000,-11,-577,221,-208,1000,-300,-1000,-677,-205,897,398,1000,22,1000,-827,82,162,-242,-435,554,-1000,1000,337,-474,1000,1000,1000,-157,384,1000,-562,-875,73,260,669,-492,-326,773,900,-236,-550,-524,228,-39,-1000,-443,-1000,242}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00424() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{766,-541,154,10,939,-466,41,-1000,777,92,162,1000,-358,1000,-36,-899,-1000,-677,1000,-733,435,-1000,816,-1000,-1000,-1000,366,-31,852,-158,-1000,-156,-321,-1000,376,-401,-164,-375,-168,-1000,-783,1000,-1000,-217,-89,528,-684,-1000,130,489,654,-1000,-1000,-915,469,-507,-980,454,1000,1000,-721,-115,-285,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00425() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-1000,341,25,988,-217,1000,452,852,455,-46,-233,1000,-68,239,-6,469,-1000,-523,-206,-213,478,-891,-463,842,676,543,227,-764,-56,-252,781,1000,-646,469,358,-1000,947,447,-797,380,828,323,967,400,-470,-1000,-1000,-1000,-397,756,-388,548,68,-1000,-257,-662,-908,1000,-601,654,-651,-900,868,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00426() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(java.lang.String,int):int",
            new int[]{-180,341,-400,654,-645,949,291,78,323,-231,-416,206,1000,320,-12,1000,-301,-567,678,-733,-286,565,608,403,505,447,-381,-116,852,-366,439,-387,495,388,113,-682,357,587,1000,593,821,-96,338,-794,-1000,-90,293,-887,264,665,-152,7,203,-637,51,-747,-375,-278,140,50,-607,42,-47,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00427() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{615,-622,542,759,1000,496,17,96,-299,-51,-327,-359,1000,-414,369,388,470,-640,-231,837,-576,559,-775,-11,-1000,-803,162,-207,206,-430,-367,-248,-373,-242,-754,157,1000,-146,15,448,410,184,50,-739,339,58,-880,1000,-170,-483,921,-884,914,38,-361,-214,-324,100,222,486,174,401,436,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00428() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{1000,168,14,-224,-852,-47,673,-33,587,830,-651,-517,-1000,-270,20,910,683,287,-950,1000,-772,-713,1000,692,-820,539,-275,764,485,-1000,-1000,-185,-283,30,-998,1000,1000,1000,-284,-1000,792,-197,-66,-574,964,-1000,-1000,677,-815,-776,-290,261,-1000,-846,-696,732,-915,-854,165,528,589,1000,-56,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00429() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-386,-1000,-669,-779,-21,878,42,475,-684,-681,176,164,794,99,-159,-1000,884,153,902,-409,135,243,353,-219,-189,417,110,-619,-449,-114,309,-600,943,589,-171,-66,-89,-20,283,814,-529,-488,45,-72,422,-14,-1000,-285,110,-64,-866,167,134,249,40,289,167,-912,-314,305,348,-45,510,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00430() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{1000,-1000,444,554,510,694,399,175,282,288,-150,-132,1000,-56,-246,-928,449,-906,-75,-923,620,1000,-602,-77,-674,-1000,836,496,-1000,446,-85,-1000,676,-164,830,-157,-1000,1000,-284,588,-244,-581,-528,4,376,-1000,468,628,1000,756,400,1000,-174,749,-230,-769,454,-32,407,-535,339,152,953,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00431() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{1000,-1000,1000,667,781,1000,-386,481,613,475,-351,-138,818,979,-555,-941,451,-631,-262,-925,1000,1000,-1000,39,-83,-1000,1000,1000,-1000,650,875,-333,1000,-335,-610,-160,-1000,-652,429,519,-324,-1000,-1000,1000,289,1000,1000,607,791,1000,344,-557,218,1000,784,-888,1000,439,314,-1000,1000,-679,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00432() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-20,-1000,-943,-596,-330,664,332,266,58,-1000,-82,519,-192,931,65,-1000,1000,802,842,-182,14,-31,868,324,342,496,-967,-1000,-321,-560,118,319,1000,631,-491,-442,588,879,872,637,-500,-375,-360,-563,295,-1000,-833,-330,-1000,-403,-1000,1000,-631,-128,708,815,487,-56,-1000,-692,148,-274,-1000,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00433() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{719,-934,-880,326,-1000,-82,825,522,-561,457,991,550,-1000,295,-1000,-241,631,-325,-102,1000,-1000,-942,1000,-255,-1000,1000,-896,-735,1000,1000,-1000,1000,-572,-162,-1000,1000,1000,840,845,109,10,-183,189,-1000,971,-547,-1000,-47,-1000,-1000,-1000,-450,-1000,-1000,-437,1000,30,118,-367,304,1000,339,-1000,-866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00434() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{1000,-830,1000,458,42,397,-911,-683,378,177,-337,-1000,1000,735,276,297,163,179,-563,-1000,-359,882,-312,423,-217,-496,1000,1000,-685,-636,-451,396,278,320,7,1000,521,-260,660,267,-370,198,-789,222,1000,138,621,1000,110,-203,-477,-299,-490,1000,-1000,958,-721,1000,268,159,789,1000,-241,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00435() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-262,-319,-571,-282,-971,958,-111,637,540,-853,914,-784,-1000,298,245,413,85,-790,1000,91,1000,-1000,-105,252,-595,-1000,592,88,-1000,-980,456,1000,732,381,1000,-1000,507,-108,-733,14,-1000,-954,-62,417,-1000,-1000,-1000,-108,161,-73,-33,-556,-642,339,-1000,1000,-636,936,1000,-1000,-129,812,497,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00436() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-861,124,-511,-484,1000,-536,-368,671,-1000,121,1000,461,855,428,-429,727,81,-593,-1000,95,377,-888,-401,251,-321,-232,333,-224,110,-1000,381,312,-156,154,510,1000,0,-1000,434,-673,-850,-720,1000,-1000,107,528,-123,-204,-538,1000,646,304,-40,-137,404,653,847,324,-1000,-108,-59,767,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00437() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-556,-399,-336,769,-936,633,-843,391,1000,256,814,513,-1000,273,116,773,1000,-757,-583,634,141,405,-764,889,950,1000,727,-798,454,675,405,1000,-772,1000,-1000,883,1000,-852,286,142,-268,47,628,503,324,1000,889,936,-939,421,939,1000,1000,-1000,-1000,434,-1000,725,334,-377,-189,-209,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00438() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{788,-784,832,1000,-1000,-861,-519,560,589,-972,97,1000,491,833,240,-817,-1000,-53,52,742,-624,192,-777,-141,-1000,-923,556,435,738,-468,141,-893,776,-816,204,677,-101,1000,-1000,762,-22,-974,160,837,262,-6,-1000,-805,-899,756,204,-130,-317,-356,275,1000,1000,780,-603,-245,-151,-1000,1000,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00439() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,91,-552,-303,808,630,-79,651,934,-486,-528,393,793,-305,954,459,1000,786,178,-683,1000,1000,-709,-566,1000,-17,1000,637,-450,-777,-686,1000,13,-1000,1000,-999,-390,-701,-1000,286,-697,-606,-556,915,-634,-195,350,1000,1000,-1000,1000,1000,1000,-1000,-1000,-1000,580,1000,-1000,-1000,235,1000,-59,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00440() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "indexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{657,-142,-754,-276,819,523,-272,-628,287,318,61,-90,-155,-580,268,321,733,-282,-108,-631,835,201,48,339,695,399,980,86,-133,-47,141,823,126,-833,998,-783,828,-591,-336,-161,-673,-365,-745,754,-238,-32,423,172,767,-738,905,444,681,909,-877,-576,-283,-272,893,-773,173,733,102,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00441() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-746,-694,-1000,-765,1000,-587,-771,406,814,-507,1000,-866,-681,-1000,-1000,-344,-797,-1000,155,-533,262,-109,-87,-1000,-1000,-397,615,-777,754,-139,-1000,273,-232,-536,-1000,-1000,153,1000,-910,-954,-711,430,-99,624,-209,700,-589,-1000,-932,-1000,-1000,-73,1000,-345,418,592,-1000,-1000,-1000,-1000,1000,1000,370,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00442() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-626,-348,979,269,-463,515,-596,110,-918,-229,727,692,-55,485,-72,-65,1000,1000,-853,-407,-638,142,-693,-359,-178,29,137,787,82,-634,-509,801,182,377,-745,53,176,677,-415,-233,154,341,-820,-280,596,622,-385,967,140,-368,5,-624,743,26,-424,-1000,326,670,190,396,-496,-586,360,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00443() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{540,866,-528,-416,-400,-987,-1000,1000,558,-200,-696,-365,566,713,-294,129,281,-1000,-647,1000,872,454,-196,1000,-453,761,373,1000,-719,1000,-738,832,-629,-162,77,685,751,-260,-1000,418,467,919,-536,-327,-645,-769,-256,833,546,829,-1000,-325,1000,-168,1000,976,-205,675,602,1000,-728,-140,787,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00444() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{-706,-341,-356,1000,-629,-636,288,238,-534,1000,1000,450,-1000,-22,580,877,542,334,658,440,-132,-132,-87,-1000,641,-1000,268,275,466,-433,98,-503,1000,1000,-1000,1000,-1000,81,628,-1000,490,-59,-515,-31,-1000,-562,-325,-302,1000,302,711,-470,649,-434,306,-375,-285,1000,1000,607,193,-1000,515,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00445() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{580,68,52,606,-40,-102,-531,503,467,-534,331,-1000,-810,485,-79,-373,206,-757,300,-407,-1000,142,-929,131,334,29,409,-55,490,172,-360,10,182,-189,-150,-819,653,206,-338,-666,87,-124,-259,350,88,622,498,-252,-942,-76,-314,-137,421,-28,419,872,-38,-108,-408,545,301,227,39,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00446() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,536,-1000,474,566,-383,317,23,885,504,-407,487,-113,464,840,-737,-173,1000,304,-694,582,-1000,-87,-423,194,1000,507,-409,468,899,603,-833,-187,-211,1000,-522,153,2,-833,-954,-793,-806,-1000,247,-624,161,322,351,184,-600,301,1000,1000,-836,418,1000,-825,680,952,4,-404,-348,-380,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00447() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,boolean):org.apache.commons.lang.text.StrBuilder",
            new int[]{774,-385,-815,-584,742,-453,-785,797,674,372,659,-330,-876,313,-560,-821,-509,234,-612,-627,742,-1000,353,-423,-458,310,679,398,621,734,392,-398,-790,-776,727,-670,-689,826,-279,1000,-880,-316,-329,478,379,723,-472,-347,-461,-155,12,865,-213,-129,-522,1000,-892,-650,-227,-122,-770,669,-470,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00448() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{338,-1000,290,-1000,-1000,729,-467,509,-303,125,-700,879,823,1000,-5,-1000,17,-1000,-275,-320,-633,903,-922,-825,829,291,-1000,-877,149,384,-1000,-1000,-914,1000,-187,-352,125,785,941,358,-269,-950,116,-866,729,895,-1000,483,-844,-48,174,-572,240,493,1000,857,1000,328,1000,-608,1000,130,-357,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00449() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-709,-611,461,173,103,452,-1000,161,631,-465,-174,-1000,584,1000,-706,-1000,-1000,1000,259,-1000,-155,431,812,-1000,217,600,-371,1000,-513,538,531,-679,-1000,1000,1000,-186,1000,734,-699,888,55,-807,-771,-64,766,-500,-489,-505,378,-1000,1000,-104,600,-144,707,163,851,-440,409,854,-355,37,451,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00450() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-969,434,-334,746,317,-1000,659,21,22,-505,607,910,-418,-187,13,-1000,-610,-755,-603,-1000,-696,1000,-245,-1000,817,577,514,651,1000,376,-613,406,-653,198,1000,-330,1000,20,1000,731,-360,-1000,-978,-720,-1000,-1000,-1000,546,-1000,142,-1000,-616,-33,556,-313,357,780,296,1000,-486,-698,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00451() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{297,-1000,1000,-1000,-894,-19,838,853,1000,533,-647,-1000,342,1000,407,-1000,260,-314,49,100,327,565,1000,-145,771,-903,-1000,-267,-844,-1000,853,739,-607,-311,-222,443,1000,282,-701,554,-295,468,-856,-1000,-1000,1000,-1000,-1000,-356,114,521,-911,269,646,1000,863,86,465,1000,-543,768,1000,1000,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00452() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{578,158,437,676,-464,-68,-91,-38,12,-590,161,-515,256,292,-434,-678,234,890,528,-168,837,-469,85,-463,-707,-346,-377,-37,-231,-940,915,734,-946,978,775,-271,950,-947,-501,-901,-470,226,938,67,795,557,609,27,409,-515,519,259,166,-231,-461,-261,-337,-602,124,-12,224,145,-108,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00453() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{335,182,-239,-681,247,298,12,236,192,80,193,1000,380,135,878,-1000,210,-1000,-1000,553,999,112,-991,-848,-673,-341,435,-1000,-387,-465,-561,313,59,814,-360,313,-1000,254,653,-861,-108,843,-31,527,-364,59,753,188,395,402,-921,-522,-364,653,-51,1000,425,269,772,-590,1000,152,142,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00454() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-688,576,595,309,284,-90,304,-655,273,-213,-351,389,-1000,-58,879,-65,-220,-755,-467,-847,-836,689,292,-997,1000,-190,48,712,606,82,-466,218,-331,888,1000,296,181,-95,-203,-234,72,-779,-562,218,-632,-533,53,163,-1000,479,184,-428,-96,852,699,-206,685,992,1000,-189,984,-173,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00455() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-17,193,560,-807,739,-368,155,-324,-237,728,229,-62,189,-728,-589,-755,-992,-1000,-1000,556,-1000,-748,1000,-695,-225,1000,1000,58,-1000,399,1000,1000,1000,818,721,-302,1000,-169,-387,-1000,499,1,-694,-1000,-1000,-577,-1000,1000,-129,-269,-380,1000,311,675,472,-174,1000,-365,-1000,-42,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00456() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-675,596,-1000,-363,972,-1000,-678,562,876,273,-768,1000,-145,142,601,524,394,1000,-1000,695,641,-1000,1000,-146,-904,1000,-1000,-309,-1000,96,4,197,-1000,-671,1000,1000,1000,504,-349,-1000,703,5,152,1000,1000,-293,496,228,-924,-850,1000,-1000,580,777,-1000,-706,-1000,-1000,1000,1000,-1000,1000,-562,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00457() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-53,-387,714,-249,-1000,-1000,508,-715,-324,1000,977,-480,-396,-1000,-1000,165,187,1000,503,-51,1000,-181,-372,1000,-674,545,-410,77,-1000,1000,-949,-1000,-767,220,-466,380,34,870,-745,-716,-23,105,-636,196,-741,-1000,-182,-622,-1000,-348,961,-786,-187,1000,400,213,304,-291,919,66,345,64,-1000,-620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00458() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{124,-185,824,-54,-641,-696,304,866,-666,318,727,-82,-781,-936,-162,576,833,1000,85,368,201,621,-245,1000,-177,398,139,684,-860,885,-1000,-470,833,-184,90,-957,692,679,-1000,-399,339,-474,553,710,-51,-1000,607,401,-834,-1000,811,-838,667,1000,172,1000,911,-517,267,77,1000,390,-1000,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00459() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-352,462,134,-254,-558,140,611,-1000,643,-125,-245,-1000,61,-56,-143,-1000,-166,1000,-63,-156,1000,1000,383,-558,-638,192,264,76,1000,-837,823,82,-710,-234,-358,-880,-778,115,-12,77,853,-876,544,-757,466,-199,512,-1000,28,-673,-128,-966,-1000,-147,-712,-630,399,-5,-167,-1000,152,-888,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00460() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{314,872,301,-700,92,1000,-301,696,-636,-170,260,-148,-923,617,863,-50,986,-1000,-294,-441,164,1000,-1000,-528,296,-1000,985,276,731,-71,796,-671,465,151,730,-565,-238,381,-545,534,267,-376,130,-833,16,390,-138,-774,708,-456,98,-138,-41,-59,-519,557,-20,490,139,-471,855,1000,193,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00461() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,1000,-668,-629,-36,601,-596,-567,308,-276,-761,1000,-380,-667,128,-191,1000,152,-589,27,1000,-738,720,387,-202,-64,270,-138,-61,-11,-830,-950,-118,1000,1000,1000,648,806,-1000,-209,358,-1000,699,94,763,-179,568,-194,-7,-278,1000,-833,1000,814,-987,253,550,-219,1000,1000,127,1000,-638,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00462() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-675,881,468,-851,972,-413,196,711,-642,313,124,-913,-844,-32,-823,-415,300,-365,518,143,173,725,320,-426,237,-946,732,766,647,654,-771,-730,468,-689,-529,-916,-498,911,-851,640,455,-521,-114,-994,-785,511,-787,-855,313,628,855,-288,321,925,-834,-706,-100,440,369,-719,-537,122,-530,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00463() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[]):org.apache.commons.lang.text.StrBuilder",
            new int[]{-188,-1000,592,-512,-1000,-498,3,99,-440,417,-638,632,-975,-97,-258,836,1000,148,-301,-670,503,580,130,372,-951,269,-730,-1000,-753,-274,-334,333,52,559,920,127,1000,582,-1,-1000,480,389,-789,974,364,-1000,580,-512,-1000,-1000,92,-1000,-477,-108,284,795,1000,-718,587,273,262,538,-972,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00464() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-166,-1000,1000,-637,-123,-800,-182,-1000,-243,1000,874,1000,678,1,1000,-756,-597,969,-258,-1000,-156,-1000,-1000,1000,1000,-1000,1000,-752,-545,-1000,-509,-1000,-806,-483,-500,967,1000,-469,-1000,-1000,1000,-88,499,213,277,-45,-468,1000,393,137,-417,-126,1000,-1000,-280,1000,-772,1000,-1000,928,628,451,1000,-383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00465() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-423,-534,-71,-1000,-671,-4,705,-538,-925,-921,-297,597,333,21,1000,-558,500,-771,427,919,-367,-831,-504,977,-836,-256,-272,-891,260,-334,-519,-897,107,-513,858,60,221,59,618,803,652,680,13,471,-392,496,983,742,-73,427,-366,-395,-730,-622,438,876,897,204,-83,-759,-306,-405,-82,-252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00466() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{25,137,-784,-1000,54,-631,842,-209,-766,263,-534,-356,-1000,-963,42,-84,-396,479,288,273,-1000,348,-221,1000,1000,-144,-32,338,-741,1000,687,754,-714,-161,431,589,-1000,-1000,-1000,734,-35,546,-729,-183,-371,119,73,186,-477,703,-599,-325,213,-711,1000,-577,-332,-450,234,-836,1,1000,-984,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00467() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-402,-1000,1000,-1000,-1000,-1000,1000,-1000,-635,957,443,754,129,-185,475,236,-239,652,763,-120,-1000,-533,-230,625,758,-1000,817,-878,-455,-860,-575,-549,-806,276,-499,529,629,-469,-970,-678,1000,-269,499,-169,-178,865,-468,1000,232,1000,350,-522,-47,-1000,923,258,-523,936,-484,928,277,451,535,-383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00468() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,979,-753,-1000,403,16,-1000,-464,-2,706,358,1000,581,911,-1000,606,913,-470,127,228,248,-818,-227,-368,-876,170,-874,296,-1000,-888,-600,443,-990,-347,1000,300,88,-588,163,1000,597,2,-382,137,43,853,109,640,-311,-645,256,1000,-256,-1000,616,65,1000,-713,-80,386,-378,798,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00469() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{236,-818,442,328,-561,-583,-288,-559,574,320,98,-802,741,-362,-957,-93,859,-663,938,833,223,639,-908,-174,80,-803,976,-531,-101,-865,-24,-789,-753,347,-426,230,723,882,796,-116,-692,-513,-907,-473,627,481,352,-827,-848,6,-310,-929,-842,-622,845,-431,367,-190,554,834,652,-934,-903,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00470() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-124,17,-637,-735,441,184,-374,-441,217,185,631,361,641,483,-173,1000,-672,222,1000,-25,1000,211,-456,243,-1000,-1000,-452,-630,-978,-1000,-999,1000,-333,429,582,1000,1000,781,86,935,1000,983,466,583,-366,1000,376,93,-1000,-183,1000,676,-523,-1000,118,226,485,-568,1000,136,-148,530,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00471() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-284,251,-404,549,-163,260,-587,-141,-330,-30,122,83,22,-450,13,798,744,-648,734,1000,924,301,208,-617,-249,-997,-820,-112,425,-435,-670,-733,362,-61,191,386,597,158,264,466,-14,-254,659,287,544,39,853,106,-9,-839,329,1000,-534,-200,-483,-843,751,26,-174,-529,179,-378,-545,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00472() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,char[],int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-805,698,-1000,-23,-1000,1000,-889,-287,-973,-956,-945,-123,187,-61,-97,488,1000,-1000,810,1000,631,1000,471,-1000,-751,-815,-1000,338,1000,-356,-1000,-60,1000,454,637,246,-941,480,569,1000,329,470,433,-388,770,296,1000,-517,211,-525,1000,1000,-189,-185,-93,-1000,1000,26,580,-1000,185,-898,-190,622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00473() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{130,-1000,-722,1000,113,-98,291,-1000,364,857,-21,372,745,621,-835,-315,1000,-966,-27,-336,1000,-343,-6,-137,320,-122,-1000,-381,-562,-903,109,890,-356,934,-670,-156,-239,-1000,-252,548,582,-703,447,1000,-979,-277,-1000,-435,594,-1000,-873,439,-559,-78,1000,855,1000,-825,-176,904,567,282,-197,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00474() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{751,-98,-101,472,253,123,1000,-657,-339,-210,1000,-63,961,-26,-1000,1000,461,494,-133,-1000,-460,-434,668,-1000,-486,1000,973,629,-121,48,1000,-292,1000,-761,-1000,-4,1000,-178,-1000,-953,316,-1000,-1000,-121,-971,-111,-1000,-531,-183,1000,-58,1000,199,-678,-97,-180,-1000,-579,-1000,515,-1000,306,-1000,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00475() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-656,834,424,84,170,451,271,-479,716,1000,-162,426,10,682,988,532,279,998,601,251,394,408,642,-432,1000,-690,-1000,-936,-353,-37,-491,44,245,357,-1000,-763,409,-12,-518,575,-1000,-820,-311,1000,-416,243,303,280,1000,-351,-1000,-1000,315,1000,34,33,912,841,200,-762,-808,251,317,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00476() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-302,872,-816,909,291,926,-163,919,-798,-366,-744,635,664,-767,-989,-209,958,648,920,-974,-823,-978,247,662,-704,608,294,222,112,-727,-580,668,514,-544,-458,-574,-820,397,978,546,296,-351,149,-959,160,477,75,-616,-335,-392,892,-580,552,959,674,563,-950,-494,-289,-993,-910,-794,130,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00477() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-932,1000,1000,1000,-71,1000,364,1000,-312,834,1000,602,-1000,680,-104,-148,1000,-1000,218,-936,1000,-723,-79,1000,1000,-451,-1000,-1000,854,1000,1000,1000,361,-597,1000,-1000,739,-434,-86,-1000,-1000,1000,-1000,-1000,-1000,-1000,594,-150,-785,222,822,-82,1000,739,-1000,700,-176,53,-455,282,-1000,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00478() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-229,-646,-602,1000,1000,1000,-277,-264,-775,684,-227,-106,669,1000,-277,-317,513,1000,-205,316,610,38,914,-94,493,-790,-604,-1000,-218,-383,-273,843,852,906,398,-622,-285,-554,161,-38,147,-576,-522,1000,-969,161,255,-79,769,-1000,-988,-1000,638,886,804,670,-72,195,340,126,-12,1000,147,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00479() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-409,-1000,-1000,546,69,-201,859,-841,-638,840,334,829,977,-140,-1000,607,562,-1000,721,-1000,824,-1000,54,-771,284,1000,-844,1000,-1000,-1000,1000,1000,-252,1000,-277,-421,661,-692,-641,303,270,-1000,113,908,-1000,-1000,-940,-1000,435,-676,-409,977,-1000,-78,438,1000,723,-473,-393,1000,1000,654,-1000,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00480() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,double):org.apache.commons.lang.text.StrBuilder",
            new int[]{-442,-1000,-1000,147,329,-114,-476,219,64,308,-532,486,603,50,-917,-870,398,-1000,175,-371,39,-1000,544,-92,-152,245,267,491,-902,-819,227,1000,246,1000,-154,-370,-936,-1000,1000,562,441,733,646,627,-249,-835,-550,-959,1000,-736,-400,226,549,-519,1000,1000,-1000,-125,-298,494,-292,881,-444,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00481() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{238,-1000,135,1000,-929,273,-518,300,395,-139,179,181,-1000,-197,-14,-1000,1000,623,34,-531,-192,-823,822,-650,760,343,1000,1000,-38,527,136,1000,-865,1000,461,1000,-255,-558,903,-832,-238,-958,602,-1000,-189,871,1000,-411,933,1000,-347,-1000,1000,-587,-327,718,1000,-622,660,88,-1000,1000,1000,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00482() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{39,-470,844,-265,-811,177,94,302,-788,-754,253,889,414,-856,-86,583,89,-440,232,520,625,384,-291,-347,-284,332,754,912,-630,692,-966,693,465,635,-228,-96,290,672,-161,245,-289,-6,718,810,390,-326,-743,-375,839,-998,497,335,980,609,-217,-118,386,-578,-95,710,-642,-986,-961,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00483() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-4,100,-794,985,949,702,-235,-120,-1000,-582,343,1000,567,749,-324,-336,-296,675,283,-646,-7,-450,-599,-94,-50,-705,316,56,-705,-212,-585,839,-247,485,-1000,-1,39,-27,-178,693,198,433,83,-600,447,-207,-264,-584,-254,-273,940,-970,170,600,70,-497,-145,958,-1000,253,-265,-766,-146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00484() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{413,-543,328,311,-1000,424,971,767,-633,-43,-208,1000,-528,-1000,717,108,1000,619,-33,-127,1000,108,959,-756,373,1000,1000,-310,-240,733,-1000,1000,292,1000,901,1000,-270,374,56,-4,162,-1000,425,-331,433,1000,-896,-1000,897,901,821,-1000,1000,294,-848,1000,1000,-1000,1000,939,-573,-990,1000,639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00485() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{-954,776,-804,1000,-577,-262,-606,-581,-16,-699,-518,166,272,268,665,722,1000,-286,1000,359,-414,-645,117,-1000,238,-103,389,-297,-658,-454,335,-33,655,103,-214,209,-443,171,-1000,-4,1000,-448,761,1000,-370,-69,484,-35,-844,313,1000,-115,-442,589,1000,-587,1000,-919,-91,1000,1000,506,615,-729}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00486() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{436,-914,270,-204,-73,967,1000,11,618,-14,310,895,-654,-270,77,-1000,718,169,133,-1000,-76,-128,822,-369,1000,96,1000,-351,-499,734,-439,758,310,1000,360,907,171,-796,869,-876,-335,-488,205,-1000,-541,1000,324,-598,848,1000,-227,-847,1000,232,-68,708,696,-983,798,-258,-824,-1000,1000,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00487() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{424,-877,877,-477,761,1000,783,-1000,265,46,977,1000,-339,-854,-29,-1000,-1000,-178,-357,-1000,255,314,9,949,1000,658,1000,7,-489,173,-773,607,162,-571,-255,277,-319,-565,1000,-401,-972,288,180,-112,-206,1000,-350,-15,910,77,61,178,1000,744,-314,559,-597,1000,754,-801,-352,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00488() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,float):org.apache.commons.lang.text.StrBuilder",
            new int[]{894,-1000,-384,-693,-307,1000,919,660,1000,952,-1000,1000,-695,521,-1000,-1000,1000,1000,-808,-1000,-872,-963,1000,-213,1000,123,152,771,-153,1000,693,-286,460,1000,620,1000,1000,-1000,1000,-1000,219,-566,126,-1000,-281,1000,1000,647,1000,966,-830,-1000,-333,342,321,-692,17,417,-602,-817,-1000,393,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00489() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,858,-543,410,553,-296,1000,609,799,1000,-538,63,631,32,556,-134,-245,-60,-1000,-258,-1000,483,738,610,482,539,772,-659,-797,-1000,97,-805,-693,-1000,-1000,930,-1000,1000,608,-1000,400,356,477,163,-1000,212,631,-791,-68,-1000,1000,1000,-944,608,1000,-1000,-837,-1000,-1000,-1000,-114,46,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00490() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-723,-387,863,-513,785,-401,-1000,642,-112,-418,491,-11,93,-277,144,-686,-872,-456,-373,-615,1000,-314,638,1000,782,-291,-1000,694,-815,-289,5,-106,122,-1000,133,1000,-453,-894,132,211,656,-1000,197,338,134,969,-440,1000,-1000,296,-1000,-891,-1000,919,-1000,-213,-1000,612,343,-336,-613,521,454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00491() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-377,313,933,524,126,251,-950,-518,-1000,-348,931,-886,839,-118,-1000,966,-162,1000,-522,-1000,772,-1000,-1000,607,-819,-80,1000,1000,732,1000,-340,-231,955,1000,-125,-918,1000,-1000,-822,-289,-688,-368,-1000,771,-964,210,-34,-18,-90,328,-358,806,326,-1000,1000,9,-1000,440,1000,1000,-1000,-1000,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00492() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-241,-877,-447,106,-438,-997,-483,203,54,869,-1000,-882,793,948,186,857,-778,495,322,96,-1000,-595,811,977,-1000,160,1000,703,-1000,-1000,-1000,-1000,-1000,-1000,-358,-416,755,-508,346,-1000,519,-921,618,-681,-1000,231,-869,33,-583,-1000,-1000,-841,-598,618,-306,-248,753,-50,-549,81,-1000,1000,400,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00493() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{226,-1000,-384,-392,-617,-576,-953,-149,1000,0,584,0,-307,753,-1000,-1000,-235,-130,-1000,36,23,-103,355,1000,231,333,-164,1000,911,-1000,874,-339,-1000,-1000,85,-308,399,-1000,14,-1000,0,-1000,848,-100,496,-191,-886,1000,-1000,-424,-574,56,-496,203,820,-454,0,-737,864,822,106,-438,0,880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00494() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-988,-1000,1000,-141,-1000,183,-226,868,-388,-149,339,19,-419,319,346,-26,-714,-1000,878,-1000,-481,-351,-836,-444,1000,-330,-619,1000,1000,-385,-703,107,646,220,-665,-580,-714,788,107,1000,-687,749,-241,34,810,-893,634,333,-854,701,1000,319,1000,-282,-160,1000,-599,-1000,-549,49,1000,-1000,306,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00495() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-805,-1000,417,-702,-56,961,133,360,-621,865,-279,980,1000,1000,-1000,279,-816,234,-1000,-798,1000,1000,-382,797,464,576,23,-859,-820,-629,-113,-802,-939,-1000,-143,509,275,-1000,-1000,-888,946,-489,-397,-1000,-674,1000,-1000,784,-1000,-859,-445,-708,-207,-644,-519,-660,396,-333,-875,92,1000,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00496() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-290,176,-219,229,-89,-288,-889,-303,562,-2,-197,127,-256,-242,-314,-477,-284,238,-811,-165,-781,741,926,-81,126,285,-1000,1000,199,-26,654,-320,199,-696,-524,1000,514,-735,21,-1000,-454,-599,158,-602,-589,-684,92,1000,-961,-419,-407,-427,-358,170,234,1000,334,614,193,872,1000,-628,-891,132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00497() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-87,-135,-696,-386,-676,-993,-808,337,81,-180,-359,26,-95,-930,105,-1000,-57,-293,-535,-1000,567,-180,-354,31,-744,-694,-488,216,122,-912,1000,585,-181,-42,-207,631,-254,-279,800,-1000,686,-1000,200,146,-410,-234,23,-470,-30,-32,-2,-790,-371,399,1000,804,1000,-449,9,-103,79,426,-329,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00498() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-500,-580,-1000,-1000,-389,-254,-711,126,95,47,-660,-693,-495,-652,-846,63,-541,377,-1000,-787,-467,84,1000,370,-120,515,-788,238,1000,-914,744,743,1000,-580,-967,1000,-352,-340,45,-992,-53,-745,-62,-592,-272,-630,795,136,-585,493,235,-355,-553,449,-157,1000,-173,968,689,397,486,-480,-131,8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00499() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-716,-19,-520,1000,-154,-393,143,134,104,506,641,1000,-783,-137,1000,446,-433,1000,-600,62,-919,615,-1000,689,230,-644,1000,-361,-897,1000,-893,-337,-1000,993,-84,1000,-1000,-1000,131,807,-745,808,1000,-300,-332,-277,-949,1000,1000,-349,-39,589,352,-132,-57,260,1000,1000,-245,-14,-612,-533,893,-830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00500() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{784,1,158,-500,374,184,835,532,774,-924,-633,695,652,-303,-576,-449,60,-543,-618,-202,641,262,-167,-805,44,322,-482,630,-308,169,-542,341,-696,440,486,654,595,-766,556,373,446,513,-783,926,492,-69,-533,-246,-447,709,-158,51,-359,-245,26,-64,-651,-927,-458,429,379,-292,-845,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00501() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-369,-268,1000,1000,-1000,-155,-677,891,-740,-503,-367,822,127,-145,330,63,-33,377,866,-1000,239,-599,1000,370,798,-61,-532,-1000,17,-1000,360,-583,576,1000,-967,1000,-352,243,-967,1000,-748,-721,-641,1000,-956,-796,487,-1000,-1000,493,-1000,242,-801,1000,-157,1000,907,-39,-449,-1000,876,-480,542,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00502() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{912,-259,-1000,433,296,0,-473,837,-368,-276,593,678,1000,119,-911,5,715,-1000,-1000,-1000,439,-122,887,-345,537,-261,479,370,-571,-94,643,-32,-744,316,165,148,-810,73,406,-740,-175,-1000,753,557,-1000,95,703,1000,718,-888,-836,287,188,1000,902,1000,-699,-39,-494,259,-339,36,-7,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00503() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{-976,-1000,209,-1000,-709,537,-797,-195,-963,453,-1000,-1000,460,-720,-1000,64,73,580,555,-401,308,32,769,1000,-676,154,-1000,-418,1000,-1000,508,145,1000,-326,-1000,-16,113,269,710,359,-167,-500,-368,445,1000,-745,1000,-1000,-1000,700,285,-219,-1000,734,-479,-505,-418,358,650,-1000,475,-1000,-369,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00504() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{292,89,-1000,591,-576,-1000,569,-380,607,-947,162,1000,504,-512,1000,-107,-92,-481,-733,-614,662,461,308,-595,689,481,350,-680,-1000,-236,-340,-425,-475,1000,640,1000,81,-837,-82,-713,-273,138,400,948,-1000,-339,-673,1000,-340,72,-1000,13,-426,442,11,563,418,290,-1000,1000,-158,-271,1000,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00505() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.Object):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-280,224,-400,650,116,-388,762,500,-694,-522,954,346,-311,-1000,-1,-362,274,-555,-1000,-364,470,114,-303,388,703,-678,738,172,194,-28,495,-96,-335,-402,1000,-323,-910,980,-375,189,-49,-44,-135,-83,288,-131,734,85,714,209,362,-522,-112,-824,-199,-1000,15,500,-398,836,507,-1000,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00506() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{66,608,-606,1000,-1000,137,610,-283,265,-218,-1000,-756,580,-238,-187,-450,888,-729,-96,90,833,1000,736,1000,17,230,-506,-744,995,631,509,1000,268,305,595,-612,191,-649,1000,303,432,282,-1000,452,-10,777,1000,-540,1000,845,-325,-973,508,-744,-1000,441,1000,686,238,1000,-658,600,809,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00507() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{178,519,-606,672,-1000,137,1000,-348,624,-218,-984,1000,1000,-749,-760,1000,888,266,192,-102,833,980,1000,299,-1000,345,316,-600,558,919,-453,1000,1000,1000,1000,-399,-560,3,1000,14,1000,-33,-1000,422,-1000,1000,-615,-575,1000,1000,-541,-1000,406,-744,-999,-414,451,804,-531,698,-695,600,809,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00508() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{607,-1000,762,121,1000,799,193,-186,-541,-712,384,-273,186,-924,850,944,-613,-39,-184,-671,286,332,-938,-1000,-1000,1000,1000,387,-98,-653,-266,-1000,-404,339,-201,1000,-1000,-1000,-1000,-1000,-454,-1000,1000,1000,-32,-496,-1000,300,-617,356,-435,-443,-1000,829,344,320,-551,68,-1000,-804,888,-386,-756,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00509() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-221,988,-894,537,-637,485,-102,-140,-327,949,-1000,-841,-281,272,-167,-322,115,-801,-527,-243,485,226,-172,282,669,212,-1000,184,1000,809,318,133,-386,-125,1000,1000,-1000,444,963,948,-420,1000,-202,708,227,243,1000,-821,-39,-19,992,457,610,-531,-1000,540,1000,448,577,109,-284,-37,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00510() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{746,98,673,431,293,572,-651,576,117,-220,532,913,63,-588,516,-293,966,-409,-594,-207,-335,427,-186,275,306,212,-195,728,912,-353,1000,-408,-1000,-656,-52,234,301,196,-113,-757,-1000,26,936,-390,360,-428,-527,-89,111,-847,51,1000,-326,516,245,-84,74,-1000,304,-163,385,-959,58,955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00511() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{474,-568,1000,49,-952,-649,774,-999,774,-796,610,136,-129,-1000,-606,-1000,142,-328,-300,-77,1000,204,-673,-573,142,402,48,825,783,258,-624,42,-661,-396,-8,-85,-725,-264,-598,-1000,-258,-1000,669,1000,123,790,406,226,-77,1000,-397,-575,-929,-144,-290,-71,-286,-1000,-1000,350,-485,520,370,-68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00512() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-787,432,840,26,667,-231,769,548,1000,-1000,762,244,32,-570,225,-6,-102,237,-1000,-149,745,966,-9,339,-769,-607,238,1000,377,164,1000,1000,-278,993,886,-262,782,1000,278,1000,-916,649,-330,-557,634,1000,286,196,-138,694,-697,203,-280,-875,-412,-103,-1000,-23,-736,1000,714,551,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00513() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,740,825,404,-278,-889,756,-24,1000,-306,421,-764,-774,-59,-1000,81,562,-556,1000,33,664,-585,719,1000,-74,-1000,-1000,-231,556,1000,574,1000,52,1000,121,-881,734,869,877,-806,-667,296,-749,-831,1000,466,-354,635,672,1000,544,-79,-336,661,-1000,-512,1000,-1000,850,997,-840,992,638,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00514() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{497,11,-840,196,472,220,273,-379,476,-739,355,372,-743,-261,-601,-221,-490,-620,-27,-377,-644,-84,255,-530,-741,138,103,-888,579,-444,159,172,559,548,108,-327,61,1000,1000,407,1000,1000,547,857,-343,1000,-777,211,263,-1000,446,-763,602,1000,-250,819,382,318,-237,-127,-936,-243,-327,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00515() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-178,278,392,-983,154,28,308,-344,10,-293,1000,-178,418,-817,-903,-861,1000,-701,-201,860,-403,9,-829,-176,595,-893,-1000,160,-119,481,220,683,980,272,1000,307,-119,239,1000,1000,137,879,-1000,-1000,93,1000,-508,263,1000,-728,354,1000,-325,-365,-396,-717,-549,221,1000,-74,1000,-175,1000,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00516() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-103,-788,-522,432,-246,-212,170,-966,175,-1000,-33,463,-773,-304,194,665,861,-282,-320,-723,40,-27,58,-421,-319,-304,-773,-211,679,-190,901,426,493,386,-828,-960,137,887,253,161,214,-255,-400,333,403,793,-754,165,731,-177,96,-499,871,982,2,-31,253,875,695,-389,-98,-1000,15,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00517() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{176,-639,-632,-1000,984,640,-60,982,851,618,691,198,-138,-1000,20,-935,-55,1000,1000,860,-652,101,397,-620,-982,1000,353,-549,-174,-856,-1000,122,921,-600,1000,-682,-1000,-1000,-765,-1000,-703,-76,-1000,481,-1000,893,-588,-1000,-726,-816,1000,194,-283,784,-1000,328,881,-1000,-1000,1000,-878,1000,-971,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00518() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{565,11,-33,-976,296,332,766,1000,229,-186,501,185,726,-647,114,877,-511,1000,1000,-605,-213,423,414,-859,-209,884,-482,339,-674,-72,-634,-191,818,-212,530,-897,-1000,-1000,-216,-1000,185,721,-1000,-1,-1000,700,-691,-813,509,-1000,431,777,-1000,974,-519,-473,1000,-1000,551,870,443,1000,-657,822}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00519() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{584,11,-771,-976,1000,644,813,864,414,-194,-28,185,-228,-204,-743,1000,-617,819,716,-377,-729,287,545,-924,-335,884,572,-825,-42,-72,673,-548,517,-235,-398,-950,-336,-770,-654,-668,137,797,392,845,-1000,1000,-582,-481,-287,-417,585,-343,-206,1000,-216,596,1000,-745,-93,535,-884,679,-1000,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00520() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{725,-1000,-383,-807,1000,28,543,1000,1000,-281,134,87,-207,199,-506,1000,1000,1000,1000,-1000,-797,490,23,-1000,-1000,1000,439,-1000,-119,-1000,-727,445,875,-886,1000,-671,-160,-1000,-1000,-1000,557,584,-82,-1000,-1000,1000,-721,-1000,-879,-538,1000,-748,-325,1000,-977,162,1000,-1000,-976,1000,-1000,1000,-1000,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00521() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "insert(int,long):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-608,939,-665,-186,-39,-633,464,-1000,-111,1000,-1000,-214,-900,974,1000,-372,-603,860,-459,-235,472,-691,-727,-569,-337,-1000,1000,-984,-568,-433,510,1000,-1000,-858,1000,1000,-84,1000,571,198,1000,1000,1000,1000,-1000,1000,272,-791,-301,-1000,1000,1000,400,982,-9,1000,-47,-362,-1000,-1000,221,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00522() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{49,-736,-1000,-839,68,9,509,-1000,-578,388,-49,522,-161,-140,-248,285,319,-821,-49,467,125,324,-242,832,-399,-882,-604,-194,-139,311,663,-483,666,381,200,354,445,-607,760,-277,123,127,-240,-183,503,-470,80,-647,84,-478,-467,-297,526,-480,-92,469,108,296,-743,-335,912,-1000,-51,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00523() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-503,-533,-429,557,-146,-824,220,473,122,-498,-1000,-168,-207,-18,392,-76,392,196,-955,1000,739,-1000,1000,764,847,49,811,1000,-773,452,-298,245,-392,612,104,1000,-341,-762,-908,-478,473,-925,-431,416,-297,101,-465,-977,209,-1000,-250,549,102,831,983,1000,-568,89,-95,398,-41,-703,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00524() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-290,-153,-441,-601,442,619,-388,-123,-642,1000,619,719,-80,661,-1000,371,-924,-1000,1000,-249,-882,973,239,-1000,-1000,-1000,1000,914,-1000,749,349,-846,789,960,400,1000,144,428,791,121,157,-1000,118,-53,926,-882,-245,-754,-71,-1000,-204,-147,1000,-1000,264,695,560,1000,-123,-150,-30,-835,193,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00525() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-148,-1000,-1000,-1000,191,153,4,466,397,-125,-834,-169,-1000,1000,-79,-617,-885,-890,437,-191,-607,-34,810,-891,141,-491,941,365,-332,-17,1000,722,1000,-116,-1000,-77,-455,364,1000,13,-1000,-935,-304,-478,-655,242,-327,-1000,1000,740,-373,-1000,-451,92,559,-770,1000,33,703,-918,857,565,-1000,470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00526() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-612,779,-655,1000,623,-911,592,-1000,-1000,1000,-684,1000,694,-562,1000,1000,-699,-298,1000,251,941,732,-354,1000,-1000,157,-613,-287,-980,1000,-520,-1000,-342,1000,1000,1000,-159,-510,-125,-1000,1000,-1000,-470,180,1000,-827,665,33,-978,-1000,407,372,1000,-698,1000,-448,38,1000,-1000,-431,-1000,-1000,425,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00527() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-1000,11,84,650,129,673,649,-724,-175,718,-213,599,101,-679,316,1000,-241,-528,1000,117,-222,553,175,187,-790,549,-119,1000,-1000,641,-493,-1000,-654,943,1000,1000,-230,-809,-111,-1000,1000,-1000,-91,581,359,573,335,-223,-513,-1000,207,-8,1000,-126,832,565,400,672,-1000,-341,-616,-1000,813,174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00528() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "isEmpty():boolean",
            new int[]{-1000,-1000,-976,21,-254,989,120,-609,-175,718,-394,599,-591,-126,635,710,-1000,-971,1000,-241,-917,691,774,-131,-790,549,416,-160,958,1000,-157,-774,591,1000,506,1000,-351,-391,906,-1000,340,-1000,-672,442,180,415,342,-892,246,-762,375,-454,937,-271,1000,305,1000,110,-412,-1000,-528,-885,-207,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00529() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{339,-184,-782,10,755,162,747,982,479,598,-301,-696,1000,612,-68,546,67,1000,-240,1000,-1000,1000,1000,-565,-344,867,661,-751,810,-761,-419,-770,-688,1000,416,-570,-550,-885,-1000,915,-700,966,572,-865,-1000,750,-401,1000,1000,1000,335,1000,-1000,523,-850,988,686,459,1000,293,-1000,373,-427,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00530() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-832,411,-446,627,948,-437,-234,885,501,894,765,782,-992,868,977,-285,104,-373,-95,-629,538,-604,910,-212,-282,-14,974,-943,-258,230,729,414,216,-531,604,-78,-934,-669,789,-789,-108,945,-85,804,-70,456,-159,455,649,273,-271,-399,-813,805,-261,-424,-636,-375,-937,-490,-145,-15,819,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00531() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-91,830,-687,-895,380,-777,-540,-336,922,243,-375,231,841,846,-696,684,-805,865,-169,-68,-695,-9,-490,205,-773,788,-835,911,-279,-310,359,86,177,-237,-354,-451,-843,-936,-15,-106,-96,646,805,588,-602,137,-387,-163,-264,-683,-93,-518,-499,858,-686,271,102,-424,426,376,-139,-247,6,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00532() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{155,1000,-211,365,354,-531,-639,148,90,960,-829,-139,440,-3,441,613,-330,109,-869,-265,-718,688,431,-158,-610,609,0,-49,765,292,159,59,-693,796,-383,-754,-132,-1000,-855,701,979,405,771,287,-822,739,-401,457,165,637,606,483,-865,1000,-955,-227,847,-713,786,200,-429,354,-800,-644}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00533() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{160,878,-613,452,1000,-706,-857,931,13,174,-933,-867,773,332,-79,602,337,867,-1000,-502,-996,828,483,-1000,-881,832,-882,-844,688,-284,-1000,-1000,-1000,1000,1000,-121,-861,-660,-302,1000,-596,1000,817,-38,-909,3,-1000,-661,804,929,1000,229,-885,589,-759,1000,893,1000,1000,861,-1000,1000,-959,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00534() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{593,-379,-699,1000,-284,64,-432,354,544,901,-940,-612,332,-354,64,316,-124,-290,353,-575,-645,993,682,793,-332,285,277,-725,769,51,-109,858,-397,214,-318,181,-482,153,-892,837,-345,-19,460,119,-909,1000,404,1000,378,986,570,745,-563,468,-883,123,796,-447,786,379,-389,179,749,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00535() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{443,41,-1000,1000,859,950,116,917,436,865,-1000,-1000,1000,184,-229,788,911,945,-1000,-687,-586,1000,1000,-1000,-727,1000,1000,478,1000,339,-1000,-1000,-1000,1000,136,-454,-1000,-1000,-1000,1000,-947,1000,1000,-509,-1000,1000,-1000,985,1000,1000,1000,1000,-1000,-106,-492,536,1000,844,1000,1000,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00536() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-486,-235,-400,290,-485,964,30,-878,568,-539,-872,-29,684,992,395,429,-501,514,466,-733,-137,307,-277,487,-521,20,-877,771,-946,98,862,212,-913,312,50,580,-626,207,-525,-283,-414,213,-334,665,-134,888,6,45,-711,-689,-574,-99,-757,-38,-668,726,310,655,858,710,-920,-4,-720,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00537() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char):int",
            new int[]{-608,-397,727,-1000,27,1000,-648,-192,510,644,-110,-218,-1000,-794,504,173,16,-4,1000,-1000,526,-836,1000,340,830,-907,-152,-256,-186,553,1000,1000,-1000,-1000,-427,-391,-281,827,16,-749,720,-1000,-646,-1000,-760,1000,1000,505,914,663,515,-770,-444,-213,-156,-785,533,-461,-665,-35,1000,308,-716,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00538() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{302,-745,42,445,412,548,-173,385,-373,-287,897,642,-345,648,-1000,639,1000,-601,-1000,473,-599,681,-551,-497,-1000,1000,-941,-924,296,143,-1000,-730,-176,1000,-789,-738,147,-37,172,1000,397,868,-1000,-216,-744,330,396,790,-132,1000,-1000,284,-101,-695,292,-685,-279,278,92,-598,-392,995,-657,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00539() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{508,-78,-531,369,-107,-587,-5,-605,41,-539,699,-1000,458,-838,183,-1000,684,-287,561,-696,675,156,775,-187,141,-784,541,365,-162,435,1000,736,1000,-33,-456,584,458,1000,1000,463,-346,-1000,89,730,-1000,1000,194,506,-1000,-508,161,700,-743,402,-1000,-291,743,929,896,-847,604,36,-148,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00540() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{-1000,-383,-1000,636,554,-298,483,-1000,-794,563,857,-98,-1000,222,-1000,1000,986,1000,541,157,-1000,605,1000,-1000,-694,910,-29,-861,-765,701,794,1000,1000,1000,-1000,-1000,-1000,706,1000,-298,348,-300,1000,-1000,-629,1000,-1000,-269,-1000,-644,-1000,-1000,-1000,933,-130,633,777,170,-1000,-1000,713,-1000,-592,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00541() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{45,122,-1000,630,214,914,-167,-123,-1000,-629,-266,1000,-821,1000,831,-446,-272,890,-890,1000,-599,-1000,217,-601,6,531,391,-742,1000,-54,-298,1000,832,599,-1000,-782,1000,-1000,-801,683,1000,-95,1000,742,-570,648,-314,-1000,-539,21,-1000,-1000,-1000,-972,191,-1000,-212,-178,-101,590,1000,-254,-1000,574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00542() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{1000,-616,164,-9,-1000,-336,453,-865,1000,877,-183,-1000,-1000,-1000,440,1000,739,-1000,-725,896,1000,694,452,-179,969,566,-578,-1000,646,-623,-990,-1000,574,613,813,-533,1000,-57,-403,62,-1000,-779,-856,1000,1000,-45,144,1000,-516,638,644,531,450,-110,-1000,145,706,-1000,-854,410,-334,-19,-275,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00543() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{426,-1000,-854,1000,-695,-696,1000,-868,804,43,-326,-476,-74,-1000,712,1000,1000,-1000,121,355,1000,690,1000,-324,1000,629,-420,-1000,564,-161,821,-512,1000,1000,1000,-425,1000,116,353,264,-191,-1000,843,698,-733,693,-1000,473,-1000,1000,1000,753,-251,-13,-44,617,1000,822,-1000,-97,-593,400,-473,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00544() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{997,-1000,-767,-430,-266,559,1000,-283,-1000,-273,59,163,1000,-772,349,1000,1000,-822,171,1000,381,183,-273,423,1000,-129,-85,-558,-1000,-56,363,230,1000,695,1000,1000,986,-641,547,-433,1000,-1000,1000,934,-813,1000,-750,1000,980,219,-157,-329,-860,-1000,921,732,1000,1000,478,-368,-1000,-1000,-631,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00545() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(char,int):int",
            new int[]{1000,-1000,272,-64,-1000,-336,1000,-356,440,-120,-1000,-146,108,400,1000,984,-661,1000,-449,1000,286,1000,-400,-452,-695,231,7,-1000,1000,-1000,-327,-1000,-646,24,-103,-422,1000,-640,353,-1000,-1000,-169,-1000,82,1000,-740,1000,604,-528,150,680,-400,683,687,191,740,-1000,-1000,-1000,919,-330,387,9,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00546() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-136,-1000,-413,-226,-400,-607,-1000,-498,173,850,334,-1000,1000,550,911,117,1000,199,1000,-307,-417,-1000,-738,400,-32,815,-1000,637,-249,1000,-1000,-985,-247,349,-1000,-390,654,-320,357,111,258,-1000,-283,67,-1000,53,1000,-964,150,-1000,-1000,344,407,105,-21,-1000,-184,-912,-1000,-1000,-973,1000,985,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00547() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-124,-250,-730,-344,1000,-691,328,939,-962,-192,-111,-861,982,-513,-388,-1000,-893,435,111,480,525,28,641,-225,515,-253,208,-395,-617,371,-630,-143,-668,-1,530,420,-963,-845,-324,-442,981,1000,612,-1000,115,-569,-24,1000,393,287,655,695,487,-50,-147,-399,364,-42,609,768,759,-1000,-1000,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00548() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-54,-426,638,342,-401,-522,481,27,543,-437,19,-837,-694,-487,198,-36,-197,854,-270,-488,-195,-658,636,257,-900,366,930,680,511,-720,-676,772,-113,-831,-909,53,-764,653,562,-461,302,58,131,-744,42,-500,71,-288,686,-741,25,-936,-594,-793,-436,-367,-81,-34,-103,-109,825,1,-514,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00549() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{642,-445,698,641,-771,244,109,697,-789,386,-568,668,150,403,899,439,202,519,-640,-209,644,984,-441,70,-168,980,-697,-37,281,-798,957,116,-430,-336,-8,369,-191,407,-413,-922,-500,503,752,196,-153,-55,-169,577,740,-105,-214,-702,0,-923,269,736,791,88,65,-442,542,877,281,864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00550() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-572,-1000,-473,453,646,46,136,-1000,-55,958,289,-1000,1000,88,3,369,337,599,-1000,-1000,-645,-148,28,992,-581,-722,143,411,-451,203,-1000,-181,48,326,-910,-894,71,387,1000,-54,771,-326,1000,-713,694,-1000,1000,-356,847,-327,-399,806,578,1000,-1000,-1000,-995,195,-1000,-851,-281,-1000,-403,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00551() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-942,-1000,1000,565,-449,-283,350,1000,-993,-1000,303,391,706,-57,161,1000,53,168,-1000,-579,71,289,185,-183,-770,74,703,63,298,-1000,103,33,820,-299,-1000,-725,967,-178,188,-164,-275,-1000,439,-31,-1000,-975,989,-903,1000,-694,223,-205,-179,567,68,-57,215,-488,-647,456,101,987,1000,-148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00552() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-653,191,801,-833,-565,-942,381,-339,296,-802,224,768,-868,-623,24,-920,-453,249,997,916,-264,-51,30,-571,383,924,-655,-180,-108,-874,507,39,520,295,907,427,87,-845,144,398,-2,71,-849,-494,-486,-77,360,12,441,-188,698,413,-308,-441,816,299,-437,-91,-425,390,957,-151,527,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00553() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{1000,-316,-240,1000,-147,-246,361,1000,-569,-455,-599,-664,-374,205,770,1000,-352,1000,-648,-443,1000,58,803,-442,-1000,-172,-820,982,-327,1000,-1000,-174,-1000,-111,-1000,847,-790,-520,-596,-993,-540,-1000,-539,515,-917,523,358,1000,1000,106,-1000,395,-88,-740,-1000,-1000,1000,-1000,443,-632,-473,1000,-389,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00554() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-136,-302,-619,1000,748,147,1000,368,-868,850,-911,-391,-701,-1000,1000,442,-716,1000,-1000,-307,1000,-1000,423,-529,163,-1000,-1000,637,-249,-1000,73,-208,-1000,138,1000,1000,-1000,-1000,-1000,-885,346,487,513,67,-1000,265,-474,-964,-91,-1000,879,325,-280,-623,-999,939,-184,-270,699,-1000,1000,-564,-382,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00555() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{-530,221,-6,-211,-650,400,242,1000,900,1000,60,1000,-589,-18,-894,449,-356,162,-24,482,-94,-398,-336,-290,251,1000,-478,1000,-660,-860,-520,-402,219,-175,92,1000,-814,415,926,167,-1000,-180,310,-105,-1000,-479,-603,-400,-429,-481,227,-612,578,302,808,130,135,-171,117,277,-87,391,173,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00556() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{961,-691,-872,917,325,-347,683,-133,-279,-299,-457,-1000,-348,-596,1000,508,255,894,287,155,952,-806,603,39,53,-605,-1000,139,-505,280,-832,-784,-1000,519,28,634,-347,-1000,-1000,-562,708,-749,167,-334,-929,111,480,544,467,194,-254,1000,-210,-256,-627,-265,-22,-434,-400,-612,351,-20,218,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00557() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String):int",
            new int[]{710,-736,-709,-47,439,-883,89,-350,-827,1,693,20,312,-421,1000,259,-367,440,110,-90,75,-528,34,-570,74,272,-1000,41,-1000,-507,-718,-771,-482,228,530,373,-221,-1000,-47,-505,432,124,-986,-578,-743,943,533,942,1000,-21,179,233,928,456,-773,-820,-24,-497,-375,768,199,-1000,803,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00558() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{1000,-1000,670,707,-712,287,262,213,-440,-771,191,955,-229,-384,1000,-1000,-110,-573,907,562,-950,-1000,462,394,-542,-1000,-231,1000,-536,-546,435,735,913,-211,1000,-1000,-336,176,-7,38,777,728,-54,-108,-343,162,1000,-649,844,-1000,-590,1000,-1000,10,-1000,-121,-628,-168,1000,749,623,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00559() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-534,582,238,897,174,1000,256,128,-536,-229,114,-1000,951,-95,1000,-301,-1000,383,-1000,933,632,-427,-973,817,269,988,-310,-114,-309,631,-424,-1000,-88,-815,951,18,-666,-1000,895,-375,1000,1000,389,-1000,-283,-710,1000,-1000,-23,925,-507,-571,-1000,-492,743,302,-997,846,227,360,-1000,-1000,1000,-665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00560() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{809,-1000,1000,1000,137,467,391,274,130,347,440,-350,235,820,211,-613,-173,-673,527,313,-478,-1000,-127,523,633,-159,1000,-191,-244,411,-541,-196,-63,-407,948,-184,-512,61,447,-406,788,333,402,-630,-235,-321,830,-708,-433,-1000,397,856,-201,-81,-104,119,71,-147,-790,161,246,-38,869,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00561() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{487,-1000,-400,808,765,249,315,307,346,755,888,-993,240,1000,-497,-256,-206,-767,389,-117,-30,-321,-209,-33,437,276,1000,-382,-73,708,-333,-487,-63,352,509,248,162,28,467,-873,989,222,1000,-253,914,-470,-83,-745,-527,-1000,654,79,-132,55,-1000,547,233,-41,-627,581,-295,-243,492,-221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00562() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-718,-1000,-1000,185,964,356,-291,729,320,1000,859,-1000,966,1000,369,397,487,-514,762,128,-945,-39,-1000,-746,357,657,107,-1000,-216,1000,1000,-1000,-783,-727,-18,609,127,-415,724,-1000,1000,657,73,-1000,-400,485,105,-654,1000,-1000,337,-1000,-1000,-400,867,1000,702,-334,-1000,1000,-1000,-1000,1000,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00563() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{470,-1000,1000,937,-640,-212,471,-79,500,-792,-643,1000,293,-618,228,-1000,-1000,315,841,933,388,-968,-26,-154,1000,-1000,-124,224,-1000,-234,-873,-128,817,-454,1000,-557,-722,-116,325,1000,35,1000,42,193,1000,949,612,-858,670,-487,-410,1000,-848,306,321,28,88,-662,67,-451,676,1000,787,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00564() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{77,-41,-419,-13,-891,-283,1000,-1000,196,293,-713,904,-656,-276,-1000,-796,-1000,85,-542,-495,1000,-469,888,-189,-1000,-485,295,378,-1000,-369,690,261,230,363,23,119,-481,-1000,-288,693,81,472,18,380,294,747,700,-591,-279,-1000,-225,723,-288,1000,-1000,-787,-1000,904,-80,-1000,-550,779,12,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00565() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTI=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{1000,-1000,1000,575,-838,-71,150,161,808,-207,91,-9,-503,-29,750,-751,-602,925,83,-1000,358,-624,606,-138,588,-931,-110,804,-372,-503,-723,566,1000,257,1000,-550,-1000,855,203,818,-887,924,590,350,124,952,-514,-327,-229,-542,901,352,1000,158,723,406,518,-1000,986,-307,545,1000,32,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00566() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-81,-1000,-1000,726,1000,-453,421,779,993,337,830,-1000,940,884,-1000,-197,-676,271,405,13,62,845,-75,-1000,720,-380,907,-365,-421,757,-327,-958,-1000,-588,-340,-383,129,-616,832,-350,509,330,1000,-462,832,-245,-654,-669,150,-985,832,-834,-301,1000,1000,800,1000,404,356,1000,-92,-1000,-33,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00567() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{482,-549,-201,38,-197,-503,391,-383,-228,335,167,312,-1000,511,187,-1000,-173,-585,-497,562,105,-712,1000,228,-1000,-162,738,151,-538,67,736,880,217,-211,924,-171,-365,82,381,-342,-49,29,-80,151,924,-1000,1000,-508,-433,-613,297,1000,84,631,-231,-193,-1000,407,-386,20,-47,1000,-53,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00568() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(java.lang.String,int):int",
            new int[]{-391,-1000,-244,508,-301,-1000,127,404,1000,-899,-399,-55,187,-204,864,125,-1000,-1000,345,185,1000,726,408,-1000,650,151,462,215,-1000,44,-170,-972,-359,-384,-431,-1000,-955,515,466,757,205,608,-995,-21,1000,800,-1000,-612,891,145,-279,-981,-723,1000,-1000,689,989,-301,1000,-905,-546,83,-110,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00569() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{945,23,362,-944,670,-556,-746,975,-298,791,-982,1000,-561,163,-1000,1000,760,-522,-1000,-174,-557,-1000,741,-1000,-1000,26,454,-848,-1000,-24,602,-648,-450,-168,431,130,-1000,-370,-712,-46,467,-347,994,406,564,736,731,532,757,596,303,1,457,253,-1000,-1000,1000,218,-228,713,-819,-520,-1000,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00570() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{209,-297,-512,-672,881,-478,81,932,272,1000,-64,931,223,-64,490,1000,-205,-1000,613,-399,-1000,-544,878,-73,916,-964,1000,364,-1000,1000,-84,-287,1000,164,1000,-918,-348,1000,879,251,-203,-528,-604,1000,-801,-401,591,-442,-55,-741,1000,1000,163,1000,1000,543,528,640,272,990,401,258,-83,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00571() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-140,-1000,383,-73,696,-150,444,514,865,1000,-1000,535,-33,55,-312,973,-1000,119,-1000,-87,-1000,-1000,410,315,54,348,33,-978,294,-109,-175,-1000,-444,-1000,1000,-582,-89,-447,-136,1000,1000,97,-835,-39,-1000,35,1000,791,1000,562,450,771,1000,-764,1000,-495,-935,247,1000,113,-825,-1000,-1000,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00572() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{600,-313,168,380,995,-282,-71,-168,563,-863,-906,552,-994,984,-1000,419,432,-407,613,-208,-557,-1000,386,1000,-955,-389,-278,-638,-224,477,-657,-338,-747,-651,-127,-1000,-371,171,-1000,1000,1000,-1000,-878,-802,-419,253,642,956,67,-455,-7,46,1000,337,505,-1000,486,-198,785,-474,-1000,-863,-1000,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00573() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{207,-332,-1000,1000,404,821,-81,1000,-1000,-385,-439,-771,594,1000,-657,-333,-1000,1000,-164,75,-394,-489,-1000,412,1000,149,-869,-262,222,1000,-231,-822,-268,-927,213,169,-359,304,-487,807,819,-865,985,-503,1,296,1000,-343,-109,-76,217,943,-454,-956,326,-1000,1000,-761,-1000,-1000,-813,-1000,-11,851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00574() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher):int",
            new int[]{-728,845,272,-502,-217,-871,-702,-739,935,388,726,740,412,215,-716,996,621,-158,-423,-476,-227,-177,525,-188,-500,-82,845,905,-549,161,315,424,-110,650,367,616,893,-153,452,-219,-552,885,-422,653,-527,724,-566,-811,582,-153,611,-367,-722,660,534,661,-801,722,982,980,984,535,279,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00575() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,998,-1000,-1000,-627,1000,-221,524,668,670,1000,909,-155,217,1000,200,557,201,803,472,1000,1000,1000,122,747,753,1000,-537,-329,1000,1000,395,-1000,1000,702,-505,-442,1000,-636,-411,-1000,1000,-1000,309,1000,-1000,-1000,-1000,-1000,-1000,938,-607,936,-453,275,-289,-677,767,843,779,-1000,407,989,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00576() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{460,531,68,-314,-936,197,-962,72,939,1000,494,-751,383,835,-438,-588,-36,427,-1000,-792,253,852,1000,-937,-567,1000,-527,971,225,9,-1000,18,174,-1000,1000,184,-187,-190,-1000,-496,-83,141,625,481,-1000,-400,-196,510,-1000,1000,-23,663,-798,850,-316,460,1000,628,-509,-82,168,400,663,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00577() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-1000,-134,139,-1000,-456,-638,-41,-240,1000,-458,892,438,-459,-1000,284,318,-856,-1000,558,-56,-1000,209,-1000,-1000,1000,350,983,1000,-1000,-1000,-862,1000,-364,312,653,-241,737,120,-748,207,-929,1000,155,-59,96,1000,1000,972,578,-346,1000,663,-159,-239,657,1000,1000,535,-7,1000,-1000,895,-263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00578() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{128,-844,-372,733,-909,-376,-582,153,132,1000,61,106,454,945,-818,6,75,265,-788,-115,34,-329,132,-1000,-569,1000,408,577,233,-620,780,-765,940,-818,-667,1000,-662,406,-779,-847,417,-457,695,704,-832,1000,-11,1000,1000,1000,522,629,-598,1000,1000,517,994,854,-505,-457,984,-714,839,-459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00579() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{556,517,-906,40,908,-561,-393,-648,-3,422,-907,-552,242,91,923,-397,262,203,-1000,-215,1000,-172,642,-929,-997,242,-1000,1000,128,-220,-846,414,-282,-651,334,734,928,140,-298,-761,853,-1000,936,265,-424,611,273,1000,177,705,-1000,-467,775,708,-641,159,82,60,-460,1000,508,432,-186,996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00580() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-55,-427,1000,-274,-287,906,487,787,154,183,994,64,1000,-142,-448,-321,-361,499,-1000,-306,1000,1000,215,803,149,297,-108,999,404,-287,209,-432,-909,-705,235,-1000,-299,588,-188,683,-1000,-55,78,1000,-615,-1000,-552,-526,-17,-50,-133,205,-348,419,-393,894,-347,86,693,284,-120,92,-390,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00581() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{-211,-1000,-532,1000,-1000,-1000,84,81,-230,618,12,1000,237,-73,-1000,-58,750,417,-1000,516,-27,118,-191,-691,-514,724,-400,539,573,-445,-1000,-899,107,65,490,302,-685,578,1000,-142,1000,-349,1000,581,254,-327,1000,636,345,-557,-109,1000,642,-561,25,943,634,517,676,192,1000,-1000,181,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00582() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "lastIndexOf(org.apache.commons.lang.text.StrMatcher,int):int",
            new int[]{1000,-1000,-576,490,0,-967,-677,714,-402,315,-573,932,678,626,-523,1000,1000,-464,266,949,675,-846,657,-590,-938,-895,546,575,404,-502,305,-835,284,1000,-1000,1000,386,947,-226,-893,592,-533,840,-324,239,480,766,1000,1000,-490,666,-689,808,610,1000,-782,403,1000,596,475,-321,-800,-120,953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00583() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{949,-337,-246,-567,-135,-806,97,520,-37,-1000,226,1000,375,-946,-368,369,-649,1000,-285,-158,-1000,-1000,565,-1000,-1000,252,-894,1000,379,1000,-609,1000,255,-468,290,-485,-376,299,-126,-791,-761,374,438,-37,909,242,-163,169,415,-1000,825,-1000,-1000,939,488,-77,-685,744,409,391,219,-345,667,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00584() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{158,471,559,114,-175,-1000,-44,426,-1000,736,-482,713,-345,1000,181,426,933,-870,-540,-335,635,862,-226,-567,-212,-385,947,-1000,-50,-870,240,-351,-96,-1000,931,-1000,-613,482,-318,1000,400,-930,-140,33,-534,-535,-426,225,382,-1000,-23,132,-1000,-474,-126,1000,424,509,-303,747,-687,-603,-371,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00585() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-1000,-946,-549,243,59,-689,504,-753,1000,-142,159,159,-1000,-669,-104,427,-1000,-793,116,-285,-278,250,459,990,763,654,-934,432,43,422,692,502,-469,-666,-463,-93,-299,239,-276,-400,-60,-973,-501,-245,92,-832,-640,-217,-308,-642,1000,1000,339,133,1000,-400,-881,-125,-1000,1000,734,-261,511,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00586() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-1000,325,738,312,-366,-383,174,324,-1000,974,-784,1000,-1000,1000,748,1000,73,-1000,-939,-285,182,-808,1000,-723,-898,758,129,170,219,-873,-57,-73,-214,-1000,585,-747,-1000,174,-433,1000,350,-624,-239,-354,-28,-585,-188,-373,499,-1000,232,1000,-589,-1000,324,-313,-481,337,-355,1000,-1000,-1000,-4,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00587() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{291,-397,-1000,-437,675,-1000,-366,289,-27,243,-285,-352,-409,1000,-197,190,904,209,443,1000,677,536,565,296,-267,-792,608,22,-74,254,310,-122,-403,-994,290,-485,-336,334,-652,262,515,-68,-176,-144,-657,-763,186,238,-494,-296,-873,445,-252,557,-58,-77,677,5,-717,391,-328,-284,120,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00588() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{229,-496,-675,312,464,-1000,139,-199,-58,-715,581,432,-112,-1000,-556,52,-268,1000,-558,-285,-656,-808,1000,-1000,-898,758,-1000,899,226,1000,134,1000,381,-928,-533,966,-1000,301,-72,-535,-318,267,480,-346,1000,166,-645,474,623,-1000,1000,-1000,-1000,787,409,-313,-1000,741,283,-261,115,194,949,-476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00589() {
        org.junit.Assert.assertEquals("java.lang.String:LQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{418,-1000,339,-177,545,-277,391,-298,1000,-379,-37,-1000,1000,-1000,-1000,-489,692,325,-365,631,-911,666,1000,-810,-1000,-936,-813,-411,-923,900,-1000,395,-326,-341,-1000,1000,1000,-152,-429,498,-1000,-136,-59,1000,1000,-1000,251,899,-833,549,879,-1000,990,853,194,1000,-8,-1000,-660,-1000,1000,-108,-101,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00590() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "leftString(int):java.lang.String",
            new int[]{-26,-1000,-736,678,199,-478,721,756,414,-265,1000,-194,-606,-647,-854,-662,324,1000,249,-770,-223,195,832,-1000,-1000,550,-869,-387,-416,332,981,567,832,-540,-558,725,-421,768,-367,595,-170,187,588,-67,-420,550,-1000,556,435,-1000,1000,376,-857,748,721,-408,-740,589,-216,-776,227,392,-390,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00591() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{39,-1000,230,-238,-1000,-287,844,502,1000,-661,135,-704,-136,92,505,-1000,600,498,-371,-314,871,-400,353,-424,-1000,-944,1000,-400,304,-227,-634,-1000,-504,1000,212,-51,-466,-38,-1000,-906,577,-811,522,1000,226,315,400,707,97,-111,1000,1000,487,364,32,580,-400,-452,-1000,1000,812,-708,638,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00592() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{529,-159,34,-543,-130,573,267,-742,-229,146,443,-368,441,-721,-962,376,810,780,-805,-230,1000,400,305,-495,416,20,-608,562,-674,686,-548,-690,-739,705,-400,963,-295,-304,-232,-16,279,233,-83,195,-635,-611,-400,1000,510,-1000,-455,-1000,-531,-329,-252,-211,981,269,-1000,365,-399,-667,-455,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00593() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{-535,-905,-1000,918,-477,63,-93,476,1000,-860,-278,-431,972,1000,-165,922,391,291,1000,584,-446,-1000,-61,418,2,-1000,180,-1000,-1000,-679,809,2,-1000,-410,525,-961,-1000,-54,171,-144,-801,-732,1000,-882,860,322,955,-1000,111,-453,1000,411,-1000,352,-492,971,482,-552,1000,290,-821,1000,-381,863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00594() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{878,-1000,-139,-235,-1000,-842,359,739,400,-1000,395,231,-33,615,486,-348,67,49,-968,-142,1000,-827,-94,-173,-1000,-1000,1000,169,-584,-378,531,-412,-197,50,387,-1000,-850,238,-612,-1000,-370,-1000,67,823,1000,870,833,-535,-434,24,1000,-155,-649,1000,-541,701,-101,-728,699,1000,-641,553,-698,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00595() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{1000,-1000,785,330,-83,724,-560,539,494,-1000,-442,-1000,-1000,674,-558,-302,-670,274,-1000,-23,1000,-1000,630,768,-1000,-1000,-870,1000,-723,-602,1000,-966,-154,613,1000,-1000,789,12,-843,-1000,-1000,959,50,-229,1000,578,1000,269,-926,291,1000,525,-907,1000,-1000,407,-990,-833,327,1000,416,200,-322,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00596() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{1000,302,-151,-550,64,337,128,-476,-28,901,1000,-169,-360,-810,384,1000,263,-572,-800,-40,-494,-188,-1000,-5,413,223,-164,-281,25,-577,178,653,13,582,-400,311,1000,1000,632,25,597,838,-363,-656,-858,-829,-677,-535,818,-780,80,-1000,-639,1000,755,1000,193,-783,1000,522,1000,-103,1000,446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00597() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "length():int",
            new int[]{259,-1000,704,25,-889,-301,212,352,656,358,-281,-1000,-503,573,-497,-943,721,-58,-652,-272,-283,-244,285,113,-1000,-1000,400,-900,108,545,-132,-698,999,984,-766,-334,-283,875,-566,-1000,538,-1000,533,1000,876,1000,765,488,-137,685,109,949,777,877,-501,472,400,-825,-733,997,-607,-156,212,-712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00598() {
        org.junit.Assert.assertEquals("java.lang.String:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-1000,239,1000,1000,-894,114,-446,-421,1000,816,-248,1000,-1000,-95,-423,1000,238,-791,177,-295,-155,601,178,1000,-314,909,-394,-787,-717,-322,-780,1000,-1000,-528,-27,846,442,-1000,-238,274,473,566,-517,-1000,224,880,-400,-27,-1000,1000,1000,-46,-201,-388,1000,601,-870,1000,-543,183,592,-1000,-101,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00599() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-135,-255,-141,1000,-8,71,-392,1000,481,-525,-1000,1000,848,757,776,1000,635,143,-838,-1000,1000,1000,-1000,-707,-1000,-1000,-1000,1000,-672,-1000,833,-1000,-812,473,1000,-1000,-1000,-475,-754,1000,-1000,425,-856,-986,-1000,1000,1000,-688,-1000,762,-310,-1000,-797,-1000,583,873,-626,-953,406,372,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00600() {
        org.junit.Assert.assertEquals("java.lang.String:XAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-522,-779,193,382,314,183,-355,-853,222,902,339,166,-193,911,358,678,-674,-515,-922,-380,-210,468,-205,535,-431,-752,-721,553,440,641,547,558,-774,285,672,1,514,-477,993,735,256,671,786,-292,-478,102,-896,399,126,598,-690,-552,-580,107,620,130,150,-836,895,6,938,46,853,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00601() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-96,-1000,-628,-1000,-291,1000,206,197,476,919,915,-1000,1000,-1000,815,-1000,837,-5,-445,1000,-900,-1000,1000,-1000,-400,-258,1000,-1000,485,1000,-447,-1000,386,1000,349,297,-362,-835,577,860,1000,-836,198,-15,-416,-685,-228,-867,1000,-1000,-1000,-822,1000,1000,-413,-1000,937,23,-703,-745,-745,43,122,412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00602() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-331,-1000,-557,-1000,-1000,882,-743,392,799,644,-114,-625,-533,-1000,-46,-80,-744,-451,222,133,-1000,378,1000,46,1000,396,1000,-1000,-123,384,-1000,50,-462,893,245,1000,-853,-1000,1000,1000,1000,-49,12,885,-209,-734,-1000,-454,1000,519,284,526,1000,1000,862,-303,616,612,728,-1000,-238,1000,-511,754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00603() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{613,43,-1000,196,678,102,-19,720,76,-508,-428,-1000,528,-344,521,96,121,-1000,136,-305,1000,-597,-32,-652,-941,-398,33,-712,1000,-640,-1000,-1000,-217,980,1000,-1000,172,-677,-646,-717,41,-704,301,1000,-1000,-1000,1000,-153,693,-1000,-1000,-1000,-527,496,-357,-723,63,-761,655,27,-730,-70,-472,158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00604() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAw", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{342,-1000,-1000,-77,784,846,-362,-626,66,859,549,-636,-864,277,1000,903,1000,-515,-1000,106,-210,-650,-140,-297,-866,-1000,-203,171,1000,527,209,-446,-377,868,1000,-925,245,-1000,1000,338,230,860,758,733,-767,880,-848,586,773,-576,-1000,-552,-630,687,-328,223,894,-1000,1000,515,-133,551,650,36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00605() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "midString(int,int):java.lang.String",
            new int[]{-634,-319,-48,380,1000,698,205,-119,-804,-60,-411,-566,-276,698,-536,-571,-968,-540,-295,70,-370,-99,1000,-726,39,-486,-396,1000,697,1000,452,-6,821,-107,-119,408,-670,248,955,597,-277,665,558,-1000,419,-140,-369,624,612,-342,258,-604,-1000,-121,-38,-269,-51,-1000,701,1000,-43,-1000,689,-399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00606() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-423,1000,811,-435,-977,481,-460,780,-964,-648,672,646,-775,-444,1000,156,-1000,185,247,1000,271,-801,-592,-132,-430,944,47,70,-634,-752,592,251,-991,1000,-1000,358,676,720,-476,607,-755,999,132,80,-1000,-345,1000,-625,1000,327,1000,678,880,437,-1000,663,1000,-1000,1000,-836,-1000,-989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00607() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-508,312,389,-224,-1000,-591,789,894,-202,839,-598,607,656,-1000,-1000,-509,-218,-529,506,58,1000,-788,143,-305,95,-664,1000,-224,91,-1000,-609,1000,1000,539,-119,873,1000,-89,272,-213,-585,-795,-446,632,737,-478,-268,163,992,696,-114,1000,298,893,-1000,-193,-118,-798,750,1000,-12,-371,1000,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00608() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-905,-1000,-438,626,-871,-6,-147,244,898,-31,-484,-72,977,194,217,-238,-12,-268,309,-533,550,647,-847,-1000,-988,-795,-963,886,1000,-381,-572,-1000,244,301,-685,470,641,1000,230,1000,43,423,91,1000,-1000,-76,495,482,1000,1000,-27,206,595,218,1000,-606,31,-242,1000,-130,-231,-66,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00609() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-108,950,-111,-368,-196,856,-178,577,311,-232,-9,189,-709,-1000,-1000,-716,707,-110,0,-464,-99,-1000,68,-977,328,176,-223,86,-147,-29,-257,-67,838,141,-202,-389,-90,275,-459,-203,1000,124,889,-99,459,-887,-665,-266,280,-42,-609,1000,780,-30,244,963,-842,-1000,332,-1000,-1000,-1000,171,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00610() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{634,-1000,-94,-289,-20,-216,613,201,-453,1000,1000,-467,444,662,282,1000,-161,400,-750,591,224,-399,79,321,-635,-1000,-1000,1000,-306,1000,-173,-360,-596,85,-331,-908,-139,1000,1000,-107,1000,739,-990,508,-354,398,947,-1000,-221,400,1000,-1000,756,37,314,-1000,967,497,255,-1000,1000,-210,-1000,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00611() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{-752,-590,400,1000,-286,-253,535,481,-199,560,188,-601,-197,440,1000,1000,-207,266,-552,0,-49,651,-601,690,1000,-1000,-1000,-151,-55,1000,-373,474,-689,440,-87,-1000,400,1000,788,-839,259,323,51,-1000,-1000,474,1000,-441,-1000,1000,-131,-1000,-788,-1000,188,-1000,-128,1000,-1000,311,465,-336,-1000,714}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00612() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "minimizeCapacity():org.apache.commons.lang.text.StrBuilder",
            new int[]{66,431,160,1000,-1000,14,-1000,784,612,122,-928,182,163,-810,-1000,-1000,74,-190,-153,-1000,-70,331,-404,157,864,-303,-432,-377,827,531,-1000,421,832,-215,-444,418,720,393,-42,529,-298,436,549,331,458,398,-1000,-559,-327,-580,-40,1000,-72,69,-132,521,-1000,-1000,744,196,-537,-937,-253,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00613() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-594,784,-664,-673,-578,-1000,-527,-443,-1000,755,1000,451,512,-1000,-455,-448,-45,-263,1000,-401,-821,331,-199,479,1000,1000,1000,1000,-374,56,1000,455,-337,-706,-738,-904,-1000,-97,485,-248,512,-1000,-832,190,-653,-183,257,-938,1000,-949,452,209,-912,187,-1000,783,1000,-627,281,-809,1000,-828,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00614() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-460,-265,-34,-856,-483,371,526,1000,-991,-685,956,1000,385,1000,-969,-178,-1000,-839,-810,1000,210,136,-822,-675,-478,-1000,-319,514,1000,262,-1000,1000,594,-297,-440,-626,-1000,227,-1000,-869,-1000,916,-870,-1000,1000,-925,1000,1000,-1000,1000,-28,58,-678,-1000,1000,680,804,968,-408,-713,-1000,973,-547,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00615() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{228,-349,360,795,261,-332,488,-618,835,550,-120,-164,348,-41,956,-84,921,-234,510,-915,-119,-97,846,990,-842,252,467,-332,-742,-349,939,-513,-55,155,-642,479,323,-705,644,813,634,-690,815,949,734,23,-705,-762,-37,-592,-600,608,-70,719,-848,385,-426,-479,-233,256,808,-36,354,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00616() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-145,300,-38,32,-627,661,-723,569,-779,-263,-281,208,912,-639,-469,616,-873,-279,-728,409,489,-604,-1000,-431,1000,198,1000,-732,162,437,-1000,1000,108,-455,-515,-306,-733,208,-788,-1000,943,746,-1000,-1000,-1000,-466,771,1000,286,433,30,-1000,509,-767,-260,233,-46,719,-990,-1000,-1000,382,-719,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00617() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{185,120,239,-514,-148,1000,-1000,860,-831,-192,303,545,614,-383,-607,893,-1000,-691,-150,635,655,-394,-1000,-1000,1000,-674,71,-716,246,259,-1000,952,492,-1000,-434,-506,-755,1000,-1000,-1000,-80,1000,-1000,-1000,-1000,-1000,1000,1000,-54,207,96,-1000,1000,-1000,810,450,-264,1000,-1000,-1000,-1000,367,-977,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00618() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{633,-251,829,816,564,1000,-312,606,-685,982,250,322,199,527,548,629,41,-876,-180,-858,577,-701,-1000,119,288,-996,273,-902,-734,-408,-898,808,239,-1000,-670,1000,-457,753,-230,194,219,290,432,-1000,490,-952,933,498,-666,267,-653,684,568,-838,56,1000,-1000,-1000,-1000,1000,413,-245,-310,89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00619() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-124,125,-38,138,-481,304,-216,-106,-999,-914,-393,348,1000,-676,-683,118,-329,-366,-174,963,343,-263,400,-53,1000,641,-221,-539,162,585,-886,602,-69,-455,81,-306,-635,381,-593,-767,617,265,-602,-701,-643,-126,818,467,52,464,-192,-779,473,-144,1000,-356,760,719,73,-1000,-589,384,-503,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00620() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-21,-1000,304,-339,-106,-958,-1000,1000,1000,786,1000,-966,-656,42,-366,-1000,1000,-581,-1000,-1000,-4,1000,641,1000,448,219,-1000,-1000,1000,219,-1000,251,-297,-895,-349,-120,-719,-684,-59,-1000,-1000,65,-1000,1000,467,-1000,1000,-1000,42,724,-949,61,-1000,1000,35,-395,291,-132,258,-1000,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00621() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-506,80,-1000,-1000,528,373,803,-951,507,-86,1000,-932,-633,-1000,-403,465,360,335,-1000,-23,1000,-199,-1000,1000,-492,61,1000,-424,1000,-129,136,1000,-807,-384,-904,-1000,241,-876,880,-83,-267,1000,190,659,-1000,-790,-536,279,-252,-297,-841,-309,-327,-925,1000,514,-627,1000,724,1000,-1000,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00622() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{13,-526,-112,1000,173,592,557,47,-878,553,-457,537,155,1000,716,-34,455,-1000,-105,-820,-38,-1000,-1000,1000,1000,-1000,664,-817,-1000,-686,-818,336,81,-1000,-261,1000,-451,216,574,-671,98,-521,814,-1000,1000,-975,1000,237,-1000,-169,-1000,1000,-39,-296,-392,404,-391,-1000,-426,-299,1000,-485,1000,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00623() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(int,int,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,732,-991,-1000,-575,-931,72,-544,-1000,905,564,1000,-592,-1000,-683,-655,454,-531,1000,-738,-853,233,-379,-302,1000,809,67,1000,-167,456,814,-51,1000,-604,-1000,-968,-1000,-415,265,-347,808,-1000,273,349,359,-771,-34,-832,1000,-912,-983,-153,-1000,-287,-1000,1000,1000,-3,166,102,1000,-1000,-974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00624() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{260,-709,-968,1000,1000,-197,-708,487,557,-913,462,958,-470,1000,-400,367,428,889,-564,-832,-1000,462,-1000,-30,107,-307,517,137,-541,-590,189,1000,1000,-313,-1000,929,-246,-198,-416,-1000,-93,1000,-635,-1000,-1000,846,1000,-1000,207,1000,593,813,-254,-579,778,1000,-1000,-848,229,-126,-307,213,-912,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00625() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{537,-417,609,278,575,-295,630,-186,-364,-141,-37,672,-596,952,1000,-382,402,-651,165,261,-321,354,27,-309,11,9,157,1000,-323,-996,-4,-269,-147,-398,-1000,506,70,-941,-163,-1000,324,305,-548,566,-791,-98,-66,315,-55,1000,195,185,-479,-655,212,281,-172,-197,1000,261,-181,-1000,-268,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00626() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{122,-443,-1000,1000,-296,341,293,579,293,-261,-528,544,-169,384,-234,809,615,799,-1000,-252,316,1000,-926,556,299,-545,755,1,-343,189,-571,158,-599,-568,-153,373,-82,1000,-779,-954,-1000,725,-356,-265,-1000,611,1000,-1000,-191,1000,134,518,-801,368,238,1000,-639,460,-527,-183,-475,304,-1000,304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00627() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-194,263,1000,444,264,358,-962,-316,1000,-450,-461,766,-984,412,745,-579,151,-741,130,-215,-1000,793,-1000,-688,128,72,1000,-763,-1000,-356,-659,120,238,-448,-163,-18,-103,35,461,-1000,-429,-772,-260,-800,-547,295,1000,-714,331,1000,1000,819,687,13,-939,867,-894,-79,-687,-1000,98,-66,186,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00628() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{711,770,-1000,-1000,335,-1000,798,-931,-63,990,1000,157,-1000,-1000,1000,-1000,-371,-494,1000,1000,-1000,-1000,-1000,-1000,-1000,182,1000,-1000,-527,-1000,1000,-382,900,-334,-340,-144,34,-291,-1000,-81,1000,-262,-632,908,1000,-1000,-1000,152,889,-482,1000,568,-396,-313,978,997,-938,-502,-1000,-1000,1000,177,571,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00629() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-900,-1000,754,247,307,334,-521,202,-549,-1000,-998,-213,871,-500,-853,909,-162,838,-827,-1000,-867,-322,230,-239,-870,-167,305,-472,-1000,511,-716,805,1000,874,569,-174,213,438,-763,-620,-1000,-2,-350,-1000,-84,353,-1000,-596,222,-1000,-601,923,640,261,1000,409,139,-684,536,-381,87,264,-381,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00630() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,434,-400,565,134,139,793,-188,374,1000,677,253,-1000,-129,122,-417,215,-40,745,-301,-1000,-1000,-31,-24,-448,668,-1000,769,1000,35,1000,-760,-179,-364,-135,98,118,-432,-1000,476,199,-938,812,885,-688,-1000,-1000,1000,-1000,-382,496,835,352,-596,-659,494,-639,-850,437,-1000,835,86,662,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00631() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replace(org.apache.commons.lang.text.StrMatcher,java.lang.String,int,int,int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,803,717,-211,-1000,1000,-646,-582,236,711,-758,675,-828,-710,-627,-193,-955,-88,159,240,-648,560,-272,-1000,-1000,-255,-884,-1000,-631,-110,-1000,-201,-686,1000,1000,-552,-247,1000,-1000,-968,-426,176,152,264,469,-706,-51,-640,-449,-738,1000,-466,1000,948,514,-297,683,976,-656,-940,362,38,316,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00632() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{268,116,332,-228,505,-623,-736,339,1000,-296,-349,796,-43,645,515,673,-198,-673,-768,-833,-608,-897,-428,825,-132,-856,-212,21,449,542,383,-424,-514,-652,985,687,480,-404,-44,857,853,-261,496,-645,895,496,-511,-758,-976,225,279,108,172,448,945,-518,109,-222,-168,-50,-309,128,634,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00633() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-661,-219,329,-314,30,-1000,-729,-238,1000,93,312,772,-157,1000,-400,1000,320,-961,-883,-862,506,-1000,745,344,-343,-299,370,544,1000,1000,744,252,79,-914,1000,1000,-389,-583,466,1000,1000,869,1000,-806,631,-663,-821,-512,-1000,1000,-354,-1000,993,-687,1000,-1000,389,144,308,369,-618,-210,1000,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00634() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{368,407,133,571,-315,289,-842,-184,130,1000,-181,297,757,46,-318,580,-374,-1000,-497,-1000,-223,1000,213,-868,581,1000,551,-254,1000,-511,402,-299,545,-369,-1000,244,740,-1000,-1000,1000,326,64,1000,1000,157,-314,631,1000,-1000,16,-751,-463,-1000,266,-496,802,259,900,-1000,-250,-82,-1000,-465,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00635() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-661,317,58,-314,-337,584,-151,279,-433,93,478,-5,1000,137,22,529,-96,-744,-385,-811,-353,1000,442,-1000,349,-299,370,-163,938,-190,200,-363,205,-468,1000,159,230,-812,-1000,859,-478,308,690,1000,-1000,-1000,628,1000,-1000,558,-1000,-898,-994,296,-1000,1000,389,1000,-704,-442,-618,-1000,-491,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00636() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-333,-1000,-358,817,-966,694,-103,506,-243,1000,-463,-331,960,-776,-401,-1000,-384,-729,1000,-775,1000,1000,1000,-641,-47,1000,-420,-1000,88,-47,684,-1000,801,1000,-1000,-170,59,-1000,-1000,-1000,-1000,-1000,246,1000,-1000,1000,-612,1000,-7,-1000,-644,-486,-1000,-1000,-1000,728,-530,1000,-604,-324,-190,-1000,-1000,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00637() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-128,-13,225,-146,474,-201,251,1000,195,817,592,365,731,774,1000,600,199,-307,-608,-564,-793,328,-102,91,-464,-131,-1000,150,361,1000,95,-515,-1000,-793,-390,565,-249,-136,-146,655,-297,86,52,139,-1000,-853,-515,368,365,1000,-1000,-513,180,492,-370,-152,399,19,254,-324,65,-49,597,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00638() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-400,7,-51,-652,-901,-447,-1000,163,-478,527,97,639,575,311,-400,-498,847,1000,-187,1000,-734,814,467,-715,-829,-316,-1000,-738,-197,1000,843,-1000,-130,-191,985,-1000,-277,-662,-513,-1000,-831,-29,-882,-628,461,-737,830,1000,-1000,807,-253,-726,-287,-816,218,-643,956,1000,-160,603,-1000,782,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00639() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,109,7,432,449,-901,417,1000,1000,-478,-89,-983,685,575,1000,-400,142,-320,676,-187,210,-134,1000,694,-1000,-584,-844,-1000,-400,-393,857,-1000,-915,973,1000,625,-638,-1000,453,-1000,-1000,-596,-373,-1000,-322,501,-1000,-397,98,103,-1000,-73,113,-575,-816,-880,-474,1000,853,-585,-930,-206,824,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00640() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-893,-817,1000,-335,1000,881,-1000,-904,-1000,288,743,677,-1000,421,382,-1000,703,1000,1000,-919,-238,428,123,824,-22,-1000,-1000,-195,-737,-820,-221,1000,-963,710,-1000,-1000,920,1000,-942,271,250,835,-1000,-209,1000,-4,1000,-595,-24,-1000,1000,1000,-1000,-1000,-699,-936,-1000,-1000,891,108,-696,-157,9,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00641() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-245,-13,-160,1000,-1000,694,-290,839,311,1000,-780,-441,1000,-263,-440,-1000,-78,-1000,707,-762,401,1000,1000,-1000,-87,1000,-591,-797,894,-701,390,-1000,1000,1000,-1000,-126,787,-683,-1000,-702,-350,-1000,325,1000,-433,356,-265,1000,-7,-502,-1000,-297,-1000,-1000,-1000,631,-893,1000,-604,-937,-635,-1000,-1000,-933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00642() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-478,1000,302,1000,229,-124,224,322,813,922,1000,297,1000,1000,-34,-57,882,1000,-1000,-1000,-522,-545,969,1000,461,-1000,-1000,169,-444,-95,175,-59,631,951,-959,-1000,-1000,-1000,1000,297,162,-126,432,961,1000,181,-1000,-1000,874,-1000,-427,-774,974,-578,1000,-750,-1000,-1000,-1000,1000,1000,-1000,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00643() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,22,1000,-327,-586,-273,1000,213,372,-296,-210,-559,-403,-147,-249,403,191,-393,-772,541,2,-149,-452,-494,400,-1000,-374,96,132,648,-399,574,-1000,880,443,-877,1000,-94,-470,-1000,-1000,19,-316,706,748,454,225,405,-1000,-16,-652,746,306,443,-58,-581,1000,70,-537,277,279,-377,-446,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00644() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{983,-343,1000,-404,1000,556,-487,-686,-320,-499,922,836,-890,-305,1000,-608,-470,1000,946,853,-300,658,383,615,256,271,-679,-1000,833,-500,-1000,335,-585,-412,620,-641,-1000,142,97,1000,1000,-623,600,517,-262,230,-311,-1000,-915,-197,216,-351,507,974,-644,1000,-91,86,1000,-190,-1000,519,-1000,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00645() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,717,292,798,1000,-481,1000,-277,416,-52,-234,-988,-1000,685,1000,100,625,-541,973,34,-239,-1000,866,476,-658,407,1000,-1000,281,1000,-367,704,14,839,348,-681,-226,-1000,729,568,708,415,-1000,-194,-321,51,-721,-524,-649,-366,678,-418,1000,29,232,-881,1000,-1000,374,-651,894,-1000,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00646() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{251,252,534,-905,1000,-536,-545,-600,509,667,441,584,260,511,415,896,-486,-135,-24,-286,41,-126,-1000,-686,226,92,-692,-414,294,371,-865,16,66,645,695,441,-961,130,470,831,-528,-91,581,476,-52,235,580,-503,-375,509,-404,34,-492,454,-364,-477,161,-574,551,-421,-31,695,-1000,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00647() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-18,419,1000,524,1000,1000,446,-636,-782,636,-1000,-1000,166,1000,-1000,1000,1000,-968,-1000,376,923,291,1000,850,-439,-1000,-1000,198,406,500,-305,607,1000,179,754,1000,173,1000,1000,-471,48,773,143,-1000,-200,-1000,-524,-265,1000,1000,538,26,-668,638,-189,-1000,-950,-127,-1000,-775,1000,850,-130,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00648() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{45,-1000,551,-327,1000,-273,-327,-833,247,1000,56,-559,302,-147,-249,40,-1000,-393,1000,-491,-165,-418,776,309,155,438,-158,182,1000,879,-572,-53,-1000,-1000,-301,-758,29,394,520,1000,-426,838,880,918,-737,-818,-290,-741,-1000,-492,-419,-278,370,168,-677,-581,-399,745,1000,1000,-1000,1000,-932,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00649() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-265,310,-525,-422,-593,1000,-956,-116,-165,1000,-1000,1000,-1000,-665,815,560,819,120,1000,394,-1000,-1000,-245,-606,1000,829,-850,508,-32,-821,-623,-753,-1000,310,782,-393,684,264,-297,756,744,1000,99,-376,-293,247,-222,-381,577,-272,1000,-1000,-760,-790,107,-437,-13,-57,660,1000,570,1000,927,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00650() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-4,-1000,-400,-692,-400,691,-113,86,-443,-625,-322,1000,297,-1000,255,-571,-1000,89,89,-3,-31,-14,307,149,-547,829,-289,400,1000,-211,-417,312,1000,-119,108,41,-263,506,806,-360,1000,-989,1000,710,-224,-373,-699,-292,1000,-808,322,-636,1000,-374,44,680,988,-609,1000,-32,1000,916,400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00651() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{332,86,-562,307,-331,794,830,-977,-28,-781,566,184,413,-220,-985,699,36,-851,-408,631,-695,-111,700,-61,-840,-906,577,894,381,-578,853,747,732,106,-49,320,-92,50,-345,656,588,-27,-883,-414,66,-299,401,-869,864,168,232,-975,476,-528,62,-657,3,412,769,183,787,-663,-632,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00652() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{920,597,-421,-204,-118,757,-248,-96,-795,855,23,-1000,95,41,568,-871,255,-191,1000,633,-373,-392,-769,832,1000,1000,-1000,-730,1000,1000,-937,-120,-1000,-319,-801,-1000,-1000,-786,-88,-1000,-1000,1000,-836,880,287,899,-486,103,-439,394,322,-421,-437,1000,790,810,-320,-660,-1000,127,-297,538,-713,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00653() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-698,-1000,-496,618,-871,-391,-247,1000,1000,68,295,-782,918,181,-1000,-1000,184,247,690,-283,-903,188,570,75,1000,-137,-1000,-50,652,-251,-208,-703,-703,49,-886,400,-4,463,-275,1000,137,205,-1000,-415,-842,1000,-659,135,-739,451,-1000,-1000,-31,-1000,-1000,-1000,676,-529,-487,-522,143,-542,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00654() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{298,-1000,-1000,-270,414,354,773,-283,607,193,-301,-193,-1000,-254,-1000,295,1000,682,-805,-971,-136,-952,-29,-580,787,761,-1000,154,1000,-135,-521,-961,-185,1000,-338,361,667,419,-1000,1000,-88,571,-1000,-116,-192,-402,44,-734,272,1000,-895,-1000,648,325,275,-1000,444,1000,494,188,1000,-296,-891,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00655() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{298,-1000,-1000,-611,656,89,454,-109,755,-23,-301,-68,-892,-1000,-1000,217,1000,698,-204,-971,-89,-1000,90,-466,912,1000,-1000,362,1000,456,1000,-48,-531,1000,-1000,361,309,419,-1000,1000,-125,122,-1000,-116,-77,-402,-451,-1000,-31,864,-895,-1000,280,324,275,-1000,812,1000,494,188,764,-117,-1000,-742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00656() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-403,81,-525,669,59,-125,902,715,-911,612,-447,1000,-673,-400,-560,773,-564,39,-612,-133,160,802,536,-501,-780,158,1000,379,210,84,1000,-600,257,375,335,-1000,-489,-1000,96,-15,-890,-178,765,229,136,-1000,-1000,-667,696,-1000,-796,687,-584,1000,357,1000,706,-457,-400,49,15,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00657() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{142,1000,1000,245,-811,1000,-532,-765,-1000,1000,295,-248,621,889,-775,486,-1000,1000,-575,1000,-915,647,356,-1000,-1000,-415,-102,-1000,139,869,-871,-513,-1000,-636,-60,-1000,-4,-1000,1000,-143,842,339,466,-1000,-1000,403,1000,878,-359,-1000,-1000,595,209,287,-243,1000,-671,-941,1000,-229,838,-1000,-177,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00658() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceAll(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{766,591,824,-348,-778,-951,698,-716,-640,697,789,340,-548,1000,984,592,-195,910,617,865,313,-265,-846,-828,-753,-77,-253,378,12,571,370,436,-693,698,640,-707,621,-179,561,-961,-672,225,409,-406,-233,483,795,-703,672,-794,507,107,-976,-182,-819,849,28,-780,64,888,255,-398,-832,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00659() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-344,-478,-230,270,-218,-546,-249,7,410,522,317,522,-685,-1,247,1000,990,1000,75,947,328,222,-338,-728,-197,123,-1000,387,-337,-640,-321,917,-369,226,-431,204,194,220,440,-427,-569,601,213,-1000,-912,-631,344,373,-570,786,-188,940,-116,251,-710,820,-324,69,-883,-216,-33,723,503,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00660() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-849,-456,-396,-453,-140,-72,-18,-535,41,303,30,23,-659,617,195,1000,39,346,251,445,-652,283,299,68,-531,-416,292,224,-149,-1000,-276,1000,1000,1000,-355,204,459,713,696,286,-491,230,-790,278,-847,246,414,655,-1000,621,-41,247,213,337,-425,-11,486,-544,940,199,-178,-51,412,377}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00661() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-383,-112,206,898,-519,-976,-618,620,734,543,-100,228,112,-62,208,1000,1000,1000,535,1000,-117,190,-1000,-301,398,-857,-1000,683,155,-1000,-432,859,-703,-1000,-33,321,-554,-946,1000,-482,-267,321,652,-1000,230,-1000,200,-18,268,1000,-504,807,-195,40,-797,695,-1000,-331,-918,-916,127,647,583,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00662() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-854,-124,335,-392,-109,846,864,201,-184,949,-1000,-141,471,160,806,654,-940,420,814,-325,385,20,519,444,-487,-156,-778,-241,-732,1000,-466,-1000,-437,-424,-537,-1000,631,-389,-1000,56,991,-255,-537,-397,-1000,938,1000,972,-615,200,-395,-664,-1000,120,-534,854,-1000,-514,281,-98,369,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00663() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-740,1000,-1000,-1000,-73,-8,-329,60,-606,-240,462,-330,496,1000,316,-949,-937,-867,-306,0,695,-1000,-1000,-1000,-959,-584,-957,10,338,-678,287,835,-814,-215,-836,399,799,-294,-700,-669,-1000,-750,479,-816,497,-496,-34,-99,36,1000,962,449,559,-287,-294,-467,473,772,-1000,528,1000,305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00664() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-187,-485,-401,-102,994,-313,-268,-538,518,-676,-283,523,-692,587,656,-410,-246,-69,161,339,-1000,222,1000,-206,-1000,26,135,-275,-337,618,-1000,-449,365,-378,-923,155,194,-1000,1000,-1000,475,353,-352,403,-295,283,344,411,673,1000,-214,957,-214,-1000,-659,-400,135,435,101,-965,-480,908,14,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00665() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-280,-61,988,-984,38,515,-17,644,410,163,566,637,373,-875,283,604,702,950,691,947,632,-405,-880,410,-197,577,-540,-767,72,-17,821,-401,-835,951,830,-850,-447,81,-300,-502,-81,168,300,-146,-912,639,-84,173,-570,-887,-380,531,359,624,772,-142,800,-775,-520,-409,279,-94,650,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00666() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(char,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-67,-478,-176,970,430,-323,-142,146,745,543,617,-23,-400,566,288,-310,763,452,-942,120,-826,582,989,-186,-879,-719,401,-418,665,588,208,-479,412,986,545,-338,-904,388,440,470,-569,-374,-668,697,-790,660,736,-688,738,770,-446,-431,333,123,-850,691,847,-738,629,66,-33,-689,503,402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00667() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-1000,-226,-615,601,-738,-68,1000,-1000,-463,-1000,-1000,-1000,426,-335,1000,1000,-1000,53,-42,1000,-803,458,297,-819,1000,104,-688,-1000,-1000,-743,278,-1000,763,1000,-509,1000,1000,1000,-532,-1000,-909,183,53,-1000,-1000,-1000,-820,-626,1000,-1000,-622,-738,-766,189,-1000,-735,841,96,632,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00668() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-133,427,-435,-329,-866,-621,835,-163,-858,-143,1000,250,-38,-71,659,41,-1000,-1000,0,-255,-785,-178,-393,1000,461,-605,-468,-568,-1000,-77,-745,737,1000,632,486,-1000,-385,799,-760,694,757,-603,-278,761,635,53,-326,-302,-397,-640,362,835,-642,-317,-1000,-282,-488,-117,-942,564,-638,302,595,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00669() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{543,389,-44,-838,-130,938,-77,1000,-885,-263,-636,-388,634,277,41,-746,-149,-12,-270,163,-1000,-134,293,-219,1000,445,10,50,-335,-78,-329,1000,-139,104,510,1000,-94,-1000,614,140,-438,1000,-400,563,1000,-458,743,297,-618,-747,-1000,-324,-1000,1000,529,-39,-1000,153,-798,685,-1000,-1000,686,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00670() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-21,-165,-507,561,21,938,648,359,-116,138,-149,-725,-55,-684,194,-594,-229,-138,-653,-272,-858,-179,-269,307,175,-965,1000,-718,-237,-355,176,606,216,246,338,75,-791,-972,-737,209,783,-340,-332,679,-301,122,545,-279,611,-863,259,426,273,-180,-652,-285,-572,377,-171,-243,-212,483,380,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00671() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-518,-964,336,-525,1000,27,572,685,-111,782,504,-383,-445,-1000,793,-1000,657,-140,370,-912,-1000,8,-321,-415,1000,-811,-732,-1000,-254,-1000,147,-882,-269,-799,-283,477,-1000,-486,41,1000,632,253,-615,1000,-700,1000,-650,-446,1000,-161,86,1000,598,208,-668,-1000,-200,-646,1000,589,339,657,-645,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00672() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{318,956,401,-584,-624,-598,5,-1000,-400,281,-1000,-505,786,895,-452,-414,-311,903,134,-45,157,137,1000,-502,433,1000,-573,634,-136,946,-1000,1000,-584,-38,-200,1000,542,-1000,1000,-468,-329,817,947,1000,1000,-773,1000,209,-1000,116,-741,-872,-1000,744,1000,880,-475,124,-716,-184,-693,-1000,755,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00673() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,243,-50,352,907,617,322,94,581,-470,-995,-935,-779,-963,685,220,448,-78,-832,-289,148,112,-107,-174,-656,980,757,1000,-980,-1000,-1000,-575,-219,-884,74,-668,-510,-317,921,501,-785,-100,403,261,855,-393,85,-17,-73,1000,-72,-233,-260,-329,51,1000,515,1000,182,735,1000,-1000,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00674() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-1000,652,-1000,-1000,411,-148,-488,526,1000,-1000,1000,1000,1000,315,-1000,-1000,-1000,998,-1000,-913,868,1000,1000,-535,659,-1000,-1000,631,939,1000,1000,1000,1000,-1000,-668,-1000,-704,-675,-1000,1000,947,1000,932,-136,1000,-1000,494,-219,1000,-1000,53,725,-211,-123,-669,1000,-1000,415,1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00675() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-1000,1000,-927,490,1000,-295,1000,-1000,-644,-1000,-1000,-1000,-306,1000,1000,1000,-1000,-354,535,-216,-1000,687,51,-1000,896,1000,-1000,-1000,-1000,-1000,-77,-1000,1000,72,-368,519,1000,1000,-1000,-1000,-842,-338,229,-1000,1000,-700,36,1000,-1000,-785,133,545,115,179,-1000,1000,1000,1000,954,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00676() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-26,-178,410,-1000,-969,-225,-174,-1000,-47,45,-170,-850,-428,784,144,-547,45,189,-787,-100,243,330,-1000,-723,397,1000,878,515,69,896,-590,20,-63,-538,398,-178,-1000,-685,-37,3,331,713,1000,-691,706,959,40,-1000,-466,980,-146,-1000,-839,229,708,-41,1000,480,1000,898,-233,-95,216,-144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00677() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,1000,-1000,1000,1000,523,1000,-23,1000,571,-348,-1000,-1000,-1000,-21,1000,1000,1000,-1000,-147,492,-943,-1000,27,-149,-1000,896,1000,-1000,60,-1000,-1000,-77,-1000,358,-17,-167,519,855,1000,-1000,-46,-626,-756,750,-1000,1000,-700,177,1000,1000,-785,-105,722,133,127,-1000,1000,1000,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00678() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(java.lang.String,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-913,-433,4,-197,668,803,1000,454,-194,1000,-945,-250,289,-560,-1000,1000,-373,154,-9,-407,-264,-541,1000,412,-466,-671,160,348,-128,-462,-25,-345,82,-1000,122,-482,-165,-712,-1000,458,1000,-1000,-1000,909,-111,-418,-17,855,-334,-948,962,-416,-137,-384,-647,-586,-492,-153,172,-116,818,564,-261,-145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00679() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-379,-1000,498,-578,-436,129,119,422,-159,-752,858,547,544,898,34,-1000,-723,698,1000,-298,729,14,611,784,-357,-718,-754,-24,88,-933,1000,-357,-371,-1000,-707,773,-250,456,-755,-784,334,-478,-330,492,-1000,-387,-155,403,-172,260,779,1000,-746,-1000,-181,-225,-491,238,-635,-801,653,-269,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00680() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-587,75,-817,332,-164,-588,-131,524,262,-565,-1000,941,273,-390,316,-595,-872,-821,-75,302,-986,703,-469,563,1000,-937,89,-903,167,492,-366,1000,-357,-566,-1000,-293,401,-523,456,-755,-509,457,-818,556,387,-400,548,-220,588,-603,595,1000,1000,165,-1000,-700,227,-957,578,-706,687,393,-439,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00681() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{816,937,550,415,134,956,974,-370,-898,-790,796,228,-149,-862,-167,-364,-159,-610,-519,227,-889,-546,-137,-484,-378,-402,-245,-492,1000,285,1000,-753,183,1000,-652,80,-328,-581,-1000,724,1000,-1000,-293,5,19,1000,-1000,636,-370,-111,281,-941,40,889,233,281,912,-387,646,716,-776,-703,-25,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00682() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{594,-310,899,318,70,534,411,647,-595,-189,226,387,-515,-671,-263,-1000,-179,271,-1000,-113,-1000,-212,480,-600,-615,-50,1000,189,294,-86,1000,80,-1000,743,141,112,-493,31,190,127,1000,-923,-1000,-800,-555,-1000,119,445,-729,-212,-144,612,711,786,-828,-57,1000,86,62,890,861,-464,588,602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00683() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-756,-5,-784,-1000,112,402,1000,454,364,782,-130,-144,1000,-546,-961,-373,762,1000,-637,89,308,-244,-826,195,146,-160,-569,-1000,-1000,101,-336,71,-20,-416,274,-1000,-390,-1000,-1000,-437,-965,-922,-347,-1000,-1000,-1000,520,-737,-1000,-860,40,-202,42,1000,-815,782,-375,-1000,437,393,-65,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00684() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{605,401,98,70,-327,789,1000,-864,-861,-704,1000,129,16,-1000,251,746,-238,-1000,-884,535,-902,-1000,-7,582,133,-517,-1000,462,1000,-404,973,-158,-147,817,-497,-426,-548,-1000,600,1000,663,-1000,71,-805,-172,1000,-693,818,-601,-496,-564,-797,-47,822,461,243,926,-394,604,624,-406,-1000,206,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00685() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "replaceFirst(org.apache.commons.lang.text.StrMatcher,java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{532,125,-400,585,182,362,587,74,-1000,-1000,-826,406,149,370,-288,-874,-594,-892,-484,-113,-811,312,-210,-826,634,-1000,466,-519,841,-209,540,920,-812,540,-1000,-71,-38,556,677,213,676,11,-872,300,490,20,548,866,123,145,738,707,400,-831,-728,-700,433,-957,928,-25,543,310,6,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00686() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{-153,902,60,749,-293,272,837,-783,383,-541,-711,-748,483,333,-685,-400,-905,-780,-583,115,392,-963,102,15,229,687,-543,-70,-955,-32,727,541,-764,273,477,-258,-654,115,-11,-813,-171,145,-121,-133,-34,712,654,911,574,283,-139,220,558,-962,-152,-308,-939,854,726,-124,-990,517,-922,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00687() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{940,519,-471,-473,-1000,-454,120,437,123,-65,31,-1000,-1000,-107,321,-1000,-1000,-1000,-573,-116,-265,-150,1000,-836,980,-1000,-1000,1000,-1000,610,840,-902,-1000,-677,1000,-991,1000,29,-1000,-631,-476,1000,1000,638,901,-608,1000,-29,1000,-769,-37,-357,875,985,-42,-29,-312,1000,376,-821,-1000,1000,-378,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00688() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{325,341,-372,829,-484,-183,564,946,-188,-718,330,-139,-280,415,-306,317,-450,-569,-391,-739,452,-750,721,888,599,1000,-519,940,-145,1000,1000,-323,-968,-268,752,4,-206,-433,-829,-1000,-739,553,979,-368,-363,565,428,-404,1000,-563,-388,-757,1000,550,-700,-618,-1000,1000,-641,-359,-875,-120,-490,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00689() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{539,722,369,384,415,684,-152,687,-564,435,-164,-1000,349,1000,-1000,655,-29,-344,155,454,484,-516,-87,-618,1000,430,-947,-769,-786,-395,565,-201,96,174,615,1000,-599,-206,-1000,584,295,-519,1000,939,364,322,1000,27,1000,-936,-1000,721,351,-85,-557,-1000,-541,430,-154,787,-550,1000,-582,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00690() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{142,1000,40,1000,419,59,332,89,-954,-1000,-926,129,-124,-282,447,453,-1000,-708,-7,-217,439,1000,340,832,562,338,-305,1000,-1000,1000,1000,125,-1000,-1000,1000,-1000,-992,-342,-1000,-934,-1000,1000,842,-1000,-1000,1000,1000,481,1000,-315,-469,-1000,-122,963,-496,-738,1000,1000,85,-1000,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00691() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "reverse():org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-136,-352,1000,1000,-194,149,600,-1000,-286,-366,-118,602,485,880,716,-175,-163,-395,558,-5,-1000,1000,159,1000,-275,112,-1000,-872,1000,-885,-657,1000,-676,294,1000,-1000,-939,-595,382,-344,-364,161,1000,130,1000,487,253,-577,-919,-1000,-1000,-390,-550,1000,-774,-601,838,-1000,878,-541,1000,-1000,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00692() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{-367,-1000,-713,-1000,1000,349,-1000,1000,-303,657,635,-868,-50,-72,168,-674,-51,929,576,977,568,1000,1000,-1000,-907,-665,-876,-376,809,-1000,-578,797,784,876,426,-895,24,1000,923,-385,645,-242,344,567,365,167,851,443,398,420,400,-1000,482,-983,34,-1000,839,1000,1000,-134,-362,-739,105,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00693() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{1000,117,144,-459,1000,-575,54,412,-982,1000,-694,-359,1000,-225,514,220,1000,576,1000,888,-195,-653,-415,577,-614,-184,738,-64,495,1000,467,-778,-663,160,253,-598,132,1000,642,798,117,-700,409,582,389,-1000,87,-923,47,195,-466,69,413,-1000,90,-1000,1000,1000,-25,-1000,-1000,-516,722,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00694() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{-934,-781,321,-474,460,-411,-931,-485,219,-934,-1000,512,-1000,1000,-907,-1000,480,-9,56,169,-320,-283,595,121,-599,885,-1000,-469,806,1000,-9,410,-493,1000,1000,5,-799,1000,953,-40,759,-241,114,36,663,-579,165,485,-142,524,-78,-324,1000,-332,-19,-1000,-603,-1000,-235,-474,-49,495,320,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00695() {
        org.junit.Assert.assertEquals("java.lang.String:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{-343,-1000,324,-1000,1000,77,-86,-296,-749,-453,-641,-989,323,559,-1000,-344,373,1000,-72,724,-121,547,499,-1000,-982,554,-405,-754,765,-224,-161,1000,638,1000,991,-427,-353,748,880,56,623,-222,649,836,1000,321,365,657,-75,649,1000,-983,707,-491,-19,-985,-637,991,401,5,-45,-103,583,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00696() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{897,-200,-380,311,-646,-1000,-883,-756,660,689,-865,1000,1000,1000,841,-1000,704,1000,1000,-1000,-1000,1000,-1000,1000,192,406,-1000,29,-180,1000,-125,-815,-1000,257,438,-829,-1000,871,1000,-1000,117,-1000,-427,576,-126,624,-605,215,668,-122,97,-832,557,-1000,-629,149,950,1000,800,976,-1000,1000,-841,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00697() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "rightString(int):java.lang.String",
            new int[]{-824,-1000,176,-1000,1000,961,-428,1000,-623,690,-703,-736,1000,-947,241,100,-1000,-7,682,-689,640,738,1000,-1000,460,-453,-121,-1000,-180,280,-903,684,711,1000,-52,-1000,-458,-465,923,-398,686,-921,606,673,164,-173,-364,-145,328,1000,40,-425,918,-981,-931,-1000,408,1000,784,-663,-292,-1000,-527,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00698() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-93,505,310,-601,-785,-191,852,-1000,1000,-238,482,323,-1000,-254,-1000,-523,111,-1000,-636,655,1000,384,-885,-829,834,1000,1000,-1000,-648,-1000,-98,601,-860,-397,-134,13,-1000,-935,171,1000,430,279,-458,490,1000,160,516,-800,-170,626,1000,990,-861,-927,1000,220,-1000,-378,-387,1000,359,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00699() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-661,-951,224,902,426,302,967,-1000,-307,829,548,-464,810,-602,-764,-783,16,1000,-320,1000,-741,-230,-145,-596,948,-696,-1000,-484,1000,277,118,559,-597,911,818,704,738,1000,793,-104,-906,-925,1000,-25,539,-342,57,37,518,-800,798,539,137,-571,138,460,1000,1000,-571,1000,460,-33,-1000,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00700() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{873,-1000,-928,841,862,-646,509,-358,-500,-254,1000,144,256,102,5,-558,65,354,790,-384,-656,429,-721,1000,-675,224,173,-62,-173,561,-1000,-1000,-239,673,311,-50,22,705,-642,-178,-633,-10,487,1000,14,-448,-1,367,908,215,-505,-683,252,-22,-62,-393,623,824,1000,174,-806,754,-52,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00701() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-827,-1000,-671,-106,1000,257,1000,602,409,157,636,612,-1000,-1000,664,-1,381,916,-42,234,-974,292,-233,-207,1000,1000,292,1000,400,960,167,-364,1000,-393,-788,1000,-1000,637,961,-148,-712,-1000,-1000,524,1000,-61,-873,-151,177,724,-804,177,-996,1000,1000,-749,451,-1000,13,486,-449,-1000,652,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00702() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-290,-595,382,-694,24,-113,-1000,31,-1000,322,-269,-82,293,-933,-200,-902,-1000,28,-557,-219,1000,52,1000,-585,-802,669,743,-58,-450,-479,-804,-735,1000,949,-1,-833,77,-909,-369,1000,861,1000,-366,-1000,-106,1000,-251,854,-881,-410,254,1000,306,-372,-290,774,7,-138,-886,-1000,-12,-295,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00703() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setCharAt(int,char):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,196,1000,567,24,187,-138,-1000,-18,1000,-269,-8,-1000,186,752,-1000,-254,-1000,654,-1000,1000,1000,-860,718,-317,1000,-410,-970,141,895,1000,-282,1000,158,460,-833,-723,224,-305,1000,861,-1000,-1000,591,1000,1000,1000,854,-1000,-1000,1000,1000,1000,-527,-534,538,124,491,284,-1000,-210,471,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00704() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-216,-868,109,865,-417,788,-121,-422,-127,-1000,136,-1000,275,517,-683,1000,-1000,1000,-657,-582,-266,-1000,914,926,-537,-712,524,779,-431,75,421,376,-1000,880,924,-686,71,205,-1000,-461,-603,-255,476,-433,82,1000,-766,1000,960,489,8,1000,-1000,-1000,590,385,1000,1000,304,-1000,1000,-581,-146,-25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00705() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{177,-93,703,150,356,302,1000,824,-828,-487,408,-560,1000,-322,-707,1000,-148,635,78,-1000,177,920,1000,829,-330,-611,-898,984,-365,990,835,-1000,-1000,66,1000,-549,-423,322,-904,-1000,-1000,-1000,555,-201,101,1000,580,376,-41,-740,-1000,745,-799,-1000,479,-232,-385,411,-523,-355,708,-1000,-883,-165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00706() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{711,-712,-184,1000,767,134,577,-531,-271,-619,903,-884,981,-198,-614,1000,-53,653,-825,-898,471,695,637,-517,-44,-1000,-395,-189,223,1000,-111,-1000,-1000,445,951,-8,163,1000,-103,-1000,-667,-1000,654,-399,1000,1000,756,280,611,-184,-1000,644,215,-1000,30,-1000,-800,26,-253,-561,-69,-1000,-1000,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00707() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-336,-902,-737,946,-398,694,-1000,707,716,-686,-41,-784,-311,1000,-1000,588,-557,1000,1000,-1000,849,-1000,678,56,-707,-748,177,-926,770,438,521,861,-999,1000,199,-384,802,306,-574,74,-1000,537,684,-629,-786,370,-1000,1000,-60,-63,1000,1000,-255,-358,198,603,1000,800,1000,-1000,1000,118,-165,-759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00708() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{-382,-1000,-15,1000,-1000,819,57,-1000,-113,-40,694,-958,717,613,-614,908,-1000,1000,-994,-49,347,-970,1000,1000,-999,-1000,1000,1000,-1000,295,1000,1000,-1000,-226,799,-381,-1000,234,-840,-377,-970,-320,648,-1000,159,1000,-1000,1000,1000,1000,49,1000,-1000,-1000,633,-354,714,487,447,-35,527,-1000,193,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00709() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setLength(int):org.apache.commons.lang.text.StrBuilder",
            new int[]{834,-1000,-432,937,-782,447,-726,-1000,390,-1000,-160,-910,280,707,-1000,754,-1000,1000,-794,651,-1000,-333,634,739,160,-865,487,805,-1000,-702,908,913,-771,941,355,195,-551,627,-644,223,-1000,315,1000,-1000,807,-398,-1000,-503,1000,505,-311,1000,-904,-1000,-631,-1000,591,4,-441,424,199,-694,-492,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00710() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-800,560,403,452,457,-102,467,108,395,176,409,-120,163,986,145,-14,-266,422,725,611,-1000,-78,426,590,-113,-108,77,1000,-225,-167,149,-806,573,-1000,-1000,-30,564,-565,-848,-558,849,341,222,-125,154,-465,-773,471,250,6,529,676,-306,-454,507,-875,-628,-458,622,564,668,-521,465,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00711() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-293,438,164,276,-1000,593,40,-58,290,-103,-156,52,-630,-1000,-244,619,217,-43,1000,587,-420,-1000,-317,50,323,-705,152,1000,1000,-666,498,-558,750,433,-1000,1000,728,429,397,-768,-601,-325,507,-68,466,654,-721,-317,1000,677,1000,-78,-291,399,-467,195,-1000,-438,485,672,-573,74,764,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00712() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{107,61,-613,198,-791,204,719,-196,-606,-2,-443,1000,455,-512,-494,-184,577,-247,797,-1000,883,-802,-627,324,230,-108,-387,1000,366,-362,578,399,179,-1000,-743,402,564,356,1000,-439,-671,264,225,1000,634,528,-56,82,667,6,318,365,429,200,-750,217,-555,-653,-1000,564,-1000,-686,-135,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00713() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-674,-1000,-344,424,0,999,374,392,1000,-1000,807,-1000,457,639,1000,369,-58,1000,898,163,-363,-286,-874,984,-373,-1000,224,1000,-1000,-703,-1000,-178,1000,1000,-878,381,412,879,-1000,-190,726,-1000,-1000,-186,-117,555,-1000,880,731,-1000,620,480,-936,191,96,-1000,761,1000,1000,-237,81,1000,270,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00714() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{125,803,-824,-896,-172,809,883,1000,-593,-185,-1000,389,785,1000,-551,-1000,-408,11,-593,-673,-44,32,-1000,-217,-21,1000,-1000,389,-1000,1000,-270,931,-946,-794,1000,-186,-805,-232,574,-9,-460,1000,51,297,-360,1000,-155,-400,-385,-1000,-963,483,1000,-250,-551,551,716,411,-1000,-1000,1000,-681,-676,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00715() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-1000,-1000,-1000,-1000,1000,233,-113,-496,-1000,456,821,1000,-967,-396,-352,-348,-1000,829,-1000,1000,-1000,-1000,1000,-1000,-984,552,714,-1000,-791,-442,30,881,1000,354,1000,894,1000,1000,-176,-723,-530,-949,1000,941,980,-461,1000,843,-1000,720,1000,694,106,-920,-377,333,1000,-649,-452,-652,1000,554,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00716() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-5,-1000,-223,162,-1000,952,144,586,692,-1000,-18,-302,-87,-969,-320,324,290,336,1000,-429,342,-1000,-1000,212,13,-1000,-338,1000,378,-543,-369,175,864,792,-518,1000,-465,1000,402,-409,-633,-562,-37,-183,-132,1000,-926,-246,997,-128,957,-187,-265,-152,-677,307,-327,874,563,1000,-1000,607,321,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00717() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNewLineText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{1000,-1000,-48,1000,-336,847,-658,1000,176,-344,-499,-947,-1000,-1000,1000,-533,947,-1000,77,-418,821,509,170,-144,148,-947,-336,885,1000,-775,-80,878,100,1000,1000,-254,346,2,1000,-655,-711,143,-988,788,-1000,-45,120,-1000,182,-787,31,4,-184,11,-527,1000,-377,1000,608,356,810,1000,81,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00718() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-356,257,1000,-860,665,-306,833,737,-173,1000,-708,1000,-433,-396,-50,1000,-156,161,-1000,755,-76,-402,926,-438,-461,-1000,-930,1000,1000,-911,360,812,-330,210,-43,-1000,-1000,-667,1000,1000,1000,506,-567,1000,1000,-790,-1000,128,-705,-815,876,1000,-1000,1000,999,363,124,891,-700,-699,1000,-411,-945,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00719() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-120,-742,-807,224,307,913,286,-147,874,114,837,-1000,-624,-897,-156,-998,-198,-254,1000,-619,-737,713,47,-766,756,-1000,1000,400,-259,306,1000,982,-163,1000,-104,-1000,395,1000,1000,1000,974,-1000,159,1000,759,-202,128,87,-243,747,969,-209,-105,1000,1000,-892,-243,266,-110,-749,-1000,-753,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00720() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-24,-1000,729,352,421,-461,558,227,-507,356,235,981,-825,404,146,464,-1000,1000,91,400,-107,501,-1000,-1000,-1000,-1000,-1000,601,-446,-186,1000,367,1000,-53,-25,-503,-128,222,697,183,793,298,-233,625,959,-136,-897,301,-742,-147,-473,387,39,-1000,631,102,-577,-135,525,-452,-59,1000,-579,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00721() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-485,257,370,-1000,886,-246,861,1000,-173,1000,-1000,1000,-483,-479,-69,1000,2,161,508,755,-564,-402,6,-1000,-461,-1000,-997,864,442,-1000,-282,812,-1000,211,34,-1000,-277,-886,1000,1000,1000,574,-632,1000,1000,-1000,-1000,-337,-588,-1000,1000,1000,-1000,1000,1000,-219,199,1000,-540,-1000,1000,751,-1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00722() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{359,629,752,-467,-437,804,827,-820,165,-933,862,973,-773,-363,-311,-387,-774,516,-190,-564,956,181,865,489,631,33,-518,103,-944,59,465,23,682,276,-900,-615,681,472,585,-70,704,-571,54,-290,941,416,-509,-748,-722,47,-72,-363,-112,795,778,-689,-573,-589,878,-480,-840,297,-113,-726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00723() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{518,-1000,405,944,904,322,513,-223,-288,79,-24,-329,405,1000,948,460,246,718,1000,834,-573,1000,1000,327,-180,-264,144,-103,976,-370,967,1000,828,97,523,751,-717,258,572,-667,-816,429,-273,474,-552,1000,138,964,1000,450,191,-722,1000,-495,-838,1000,49,-725,288,290,-441,-290,-711,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00724() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-717,-1000,-671,313,1000,-947,-24,1000,-1000,1000,-593,-981,-340,1000,1000,571,156,881,720,1000,-1000,858,842,-831,-1000,-282,-1000,1000,361,-828,291,1000,-177,-690,508,22,-1000,-773,794,-140,592,1000,-1000,887,-952,1000,-469,888,91,-888,-194,798,519,-1000,454,1000,-602,-605,-741,-110,-405,-503,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00725() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "setNullText(java.lang.String):org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-470,-20,-270,479,343,282,772,-64,527,-245,839,-488,-217,639,171,-467,1000,352,1000,-221,296,-341,-1000,-946,-880,-941,532,141,-182,-257,1000,39,489,-699,-1000,-790,-234,248,12,382,1000,-954,122,690,-557,-819,323,83,-1000,264,1000,17,-1000,1000,752,-986,656,89,-1000,635,-321,-762,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00726() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTY=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-396,35,-690,36,1000,665,345,692,1000,-845,967,130,1000,-767,189,-265,1000,167,970,-228,-1000,14,39,-1000,-91,-1000,783,820,-598,561,226,366,-106,-505,-739,19,-126,400,-1000,262,955,65,-152,-994,1000,-4,400,203,321,-470,273,1000,-228,-705,-230,165,-15,-726,-775,-865,-1000,1000,1000,101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00727() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{1000,-840,154,630,-738,949,-250,731,1000,307,619,-45,392,-1000,744,1000,1000,1000,603,-673,-586,-881,-375,-514,-170,-1000,1000,-556,-158,696,-689,-782,-542,572,856,-959,-334,-509,623,-279,243,-715,-1000,-380,769,-1000,414,8,415,-163,630,1000,443,6,-1000,594,594,-950,524,1000,411,-151,582,473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00728() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-433,-454,-296,-1000,313,317,-776,3,-131,78,932,-576,1000,-190,328,530,617,-947,684,-185,-106,694,329,-413,-643,1000,-900,-768,337,-42,-998,308,915,-80,-909,-594,-288,992,-384,-346,332,1000,-610,-1000,477,217,901,1000,-1000,649,1000,363,-299,321,2,-115,-153,225,295,-635,10,-1000,1000,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00729() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-1000,1000,-624,-399,-352,419,-702,-428,836,-838,120,714,902,73,-1000,80,-390,532,138,787,-919,1000,-388,481,40,847,-1000,488,361,1000,1000,781,843,-885,785,507,81,519,-247,646,32,959,291,277,673,47,59,937,803,769,-1000,352,131,301,558,-662,-341,-800,-1000,-1000,-221,-648,56,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00730() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nzg=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-171,-118,-860,254,620,573,59,-480,1000,-337,1000,7,781,-146,289,2,536,-110,342,-61,-598,155,-713,-635,-488,-1000,1000,1000,-247,566,-327,586,-346,-880,-44,-156,110,330,-924,123,96,-131,-194,-574,670,271,-334,419,888,-68,53,1000,565,-1000,-643,241,763,-929,-536,-212,79,1000,400,83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00731() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-319,542,-442,104,595,-218,529,101,1000,-108,51,195,-207,73,-575,1000,-153,532,-1000,764,-559,43,-713,496,-550,-1000,556,1000,-1000,566,-166,-526,8,-690,335,-122,-24,-1000,-247,233,111,-454,1000,277,673,-63,-1000,-678,521,-412,-296,352,66,301,135,207,-206,-800,-400,-896,-572,1000,10,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00732() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-112,-118,29,1000,255,1000,668,-464,1000,-1000,607,607,781,-477,-226,476,272,376,749,-701,-736,344,-822,-233,-1000,-37,1000,1000,295,351,-827,586,-346,-1000,189,-143,110,414,-1000,884,96,-194,-194,93,567,-64,-39,475,698,-298,-740,-564,612,608,-567,-253,612,-719,-529,251,3,891,-338,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00733() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "size():int",
            new int[]{-177,-202,80,774,8,123,-173,494,1000,174,-91,1000,-113,350,-637,358,-423,869,-139,169,-285,-345,-1000,-807,-1000,-869,919,828,-210,-48,-1000,-955,-200,79,871,381,710,-1000,63,842,70,-1000,596,39,-332,1000,-648,-597,726,892,35,679,772,665,-1000,452,905,-425,-490,31,-541,959,-342,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00734() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{97,-1000,-245,1000,728,219,-577,-79,631,439,554,222,-199,1000,-888,1000,-50,-405,85,1000,42,-277,923,-1000,1000,85,-1000,-809,107,1000,316,616,-217,-836,-46,359,855,-416,-818,724,-1000,612,-1000,516,-325,-656,241,685,1000,-665,1000,33,-310,-470,203,469,89,-387,-316,-744,-500,982,641,-37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00735() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{437,769,392,-59,199,-421,-62,294,-68,-665,-537,293,273,-268,-526,621,-140,-17,653,676,-16,-960,-748,636,366,375,268,-22,609,302,-560,-781,58,-648,53,292,-887,-678,608,-991,-685,251,527,827,-31,-169,-795,332,-509,514,-989,-294,192,929,-909,-207,-204,158,206,-658,-374,-157,-733,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00736() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{-590,-693,128,752,1000,-170,615,-781,272,74,771,449,-78,1000,-1000,1000,321,-366,701,825,736,443,1000,-1000,1000,-551,-632,-427,-590,1000,-176,619,-647,-285,239,-273,228,-604,-1000,1000,-91,1000,-583,-678,85,-1000,-3,634,1000,-461,712,323,-366,312,597,1000,47,-658,-841,-163,-1000,676,1000,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00737() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{-213,740,1000,-400,-1000,-1000,-969,-174,-1000,261,-1000,-1000,1000,-1000,1000,-1000,-470,1000,-917,947,1000,1000,-1000,1000,-1000,-1000,-208,588,-422,-1000,436,-1000,-868,1000,1000,-1000,-1000,1000,-591,348,811,-465,1000,-1000,743,1000,-1000,-1000,-400,-1000,-1000,1000,228,-1000,1000,-1000,-1000,1000,-1000,1000,-114,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00738() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{202,152,912,-820,112,438,-151,369,-577,-478,152,-513,762,-1000,-425,778,-150,805,-218,833,-843,-1000,-145,1000,1000,785,497,-960,1000,167,-260,-1000,-1000,-911,430,1000,-622,-210,368,-955,-1000,1000,162,557,888,-620,-569,353,-1000,-48,-1000,175,492,1000,-1000,-887,-790,-93,102,-1000,-445,0,-863,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00739() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{11,-1000,1000,1000,555,248,-663,182,1000,949,882,1000,-824,608,-1000,734,-586,-448,1000,825,-234,-1000,-522,-1000,883,377,-1000,-1000,1000,401,819,731,273,-1000,-985,544,1000,-430,-905,793,390,807,-813,1000,-528,-413,-160,968,1000,-91,1000,874,380,-702,-219,1,1000,-1000,310,-689,205,1000,1000,133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00740() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{815,-854,374,1000,-717,246,-14,380,1000,882,166,107,218,1000,-1000,-702,-191,-273,1000,-660,840,-436,-947,-681,1000,1000,614,-56,540,-10,1000,1000,688,-1000,276,1000,-1000,-1000,-274,1000,662,846,-1000,-835,-334,-702,-357,312,-939,802,-791,820,-514,-191,1000,719,1000,-934,772,1000,860,752,1000,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00741() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "startsWith(java.lang.String):boolean",
            new int[]{667,359,-676,167,281,1000,-701,39,991,-334,319,-1000,1000,1000,91,-326,679,-188,184,-63,1000,-773,15,210,247,-1000,-189,617,-50,632,-105,590,-381,-519,-760,177,-77,-1000,89,99,-94,-452,-1000,-210,-727,318,691,252,-1000,103,401,-160,18,754,-502,2,-1000,1000,1000,1000,50,964,-1000,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00742() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{1000,-850,814,78,722,849,514,263,616,286,571,800,-444,650,614,-1000,-655,-409,675,11,1000,621,-362,614,509,-226,-132,-669,34,243,138,816,-1000,626,299,867,785,-74,759,-758,-418,853,197,-680,871,-789,990,126,-1000,-981,-71,-1000,-106,-1000,522,617,-16,-420,15,-685,91,943,-414,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00743() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{472,-462,374,727,-423,-631,153,-884,1000,537,119,-728,665,779,666,-438,-330,209,93,178,279,166,-803,256,-166,-256,141,-878,38,-439,-459,1000,267,4,541,793,462,-309,-1000,258,-944,10,-404,394,36,646,794,746,-1000,-989,-706,-471,616,-370,719,-108,-717,1,-980,143,257,1000,-448,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00744() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-428,-733,-868,-427,-587,-1000,-696,-1000,-168,346,635,-563,632,-113,376,-1000,432,9,-1000,-1000,-295,436,-200,-277,-656,768,725,-790,58,-333,660,51,384,-992,728,514,353,-828,-761,5,-479,-138,589,927,-1000,1000,977,497,-140,248,144,290,-311,395,298,-152,340,-396,-311,959,-570,-960,541,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00745() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-726,-226,-461,-305,-622,314,-618,386,-243,-1,-287,-554,116,-301,-688,1000,357,385,-1000,654,-641,-43,38,-163,-861,627,347,-351,-385,-936,-1000,-615,603,-1000,748,-506,1000,490,-65,849,718,-699,284,153,-1000,1000,-263,-956,156,422,-245,903,596,950,-278,40,-270,-22,-592,1000,271,-464,1000,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00746() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{-834,661,160,727,-501,-554,-546,132,265,-209,-122,1000,-85,-793,-39,391,-1000,-668,-582,901,115,-374,176,-684,-170,-379,10,179,1000,1000,63,-335,859,1000,-852,-903,-1000,177,-545,835,-774,1000,720,-343,-282,524,17,-835,576,323,110,269,-882,-111,-612,-88,-136,-236,580,-290,-1000,473,375,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00747() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int):java.lang.String",
            new int[]{384,-40,0,963,1000,42,-913,19,785,1000,1000,-81,73,-1000,729,-1000,-66,236,827,1000,-1000,-331,-517,992,190,-896,-918,-756,321,0,-118,704,800,1000,-182,-411,-583,0,-787,-124,-1000,-1000,-889,924,-943,-734,1000,-587,-1000,78,-722,-733,1000,0,-1000,292,-386,377,-451,268,637,-659,-377,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00748() {
        org.junit.Assert.assertEquals("java.lang.String:YQ==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{690,-916,-548,427,-131,-13,924,-713,-480,401,-351,433,603,1000,-804,943,-1000,1000,-613,-605,661,474,-137,1000,-405,-115,219,-892,-728,-39,1000,-331,1000,752,-862,1000,393,1000,-95,619,212,697,929,180,919,727,969,-1000,-430,-159,1000,-1000,-257,-183,-802,422,-690,-441,1000,271,1000,-1000,702,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00749() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{597,-897,-991,-219,-307,47,-302,397,372,-37,446,12,-975,-378,-408,-894,-63,480,-22,1000,295,-598,-72,-787,-1000,201,-1000,-28,-1000,-221,318,-1000,649,428,-864,807,-320,-330,-1000,176,977,485,-1000,-380,1000,-782,1000,763,-1000,712,-1000,868,-1000,-783,-937,-781,-884,-1000,-202,118,-606,335,880,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00750() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{474,-1000,401,-80,1000,389,-1000,-176,-235,-30,209,21,333,111,-1000,611,-786,1000,-441,-1000,464,783,212,255,78,-1000,-1000,-853,327,-161,1000,1000,580,871,-358,724,84,-21,-271,1000,-318,110,1000,-89,160,716,1000,112,-251,-1000,1000,-880,-130,516,-761,925,-489,610,1000,-1000,-578,-650,-363,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00751() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{555,-724,-70,1000,-440,134,-143,-1000,-342,442,-1000,-425,-616,448,993,-184,-475,-565,-1000,670,531,1000,779,41,190,-1000,-128,686,1000,-1000,762,540,-529,661,-409,-1000,630,599,320,624,-63,-454,350,-288,-738,1000,432,-466,-1000,-264,266,240,14,-459,-1000,-336,803,1000,91,-1000,-1000,-273,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00752() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{1000,-662,1000,79,-342,69,309,-941,-35,-969,1000,652,525,10,1000,-239,-384,-1000,1000,-20,761,-286,1000,-1000,-681,1000,-252,1000,709,1000,-1000,-687,-1000,-776,-965,-238,888,-829,-352,1000,-1000,-878,-651,-109,-607,-1000,-391,29,1000,432,-1000,517,103,222,305,-407,444,-317,-847,1000,206,1000,-311,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00753() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{409,-76,-586,-390,1000,669,-951,298,399,565,-1000,433,-426,-272,-1000,943,-168,364,-556,-208,661,137,-28,1000,-666,-115,543,-98,-728,-916,540,1000,664,981,-454,290,-529,474,464,619,-102,144,-65,180,354,176,789,-817,-430,-182,-154,336,-693,390,-934,229,-263,506,196,-1000,-81,-775,80,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00754() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "substring(int,int):java.lang.String",
            new int[]{1000,80,512,829,637,-226,-708,-176,-186,189,-536,223,1000,433,240,823,-377,-781,687,-1000,-690,186,726,-465,-682,307,1000,-1000,817,884,183,923,858,-1000,-840,-872,-1000,396,53,1000,-1000,102,484,-674,342,628,-614,-487,803,-68,-93,310,-553,1000,15,557,400,133,17,-78,569,-470,145,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00755() {
        org.junit.Assert.assertEquals("ARRAY:[C:41:24:java.lang.Character:Ng==:24:java.lang.Character:Mg==:24:java.lang.Character:NQ==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==:24:java.lang.Character:AA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{-934,-85,-397,625,393,134,-6,-841,104,103,-578,270,371,-714,-161,896,429,-773,417,-26,631,-262,233,-182,66,43,-442,-409,777,244,-417,323,-162,130,702,447,-544,979,-1000,-270,362,693,-161,-953,712,-373,25,-343,437,-117,664,135,451,-1000,1000,389,257,546,44,267,222,-303,-425,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00756() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{341,786,-946,-193,466,-210,371,-19,737,-151,-588,-750,-481,-606,-87,-73,838,408,-367,-1000,1000,350,-840,118,-193,-424,285,374,-887,1000,-1000,491,563,-188,-426,-108,-204,-102,-1000,1000,389,114,-746,-801,474,-597,-679,361,218,538,215,247,-254,333,864,841,1000,1000,-559,263,-14,1000,-1000,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00757() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{698,31,-141,-797,54,220,552,98,193,-539,-249,-708,-27,-315,53,-939,496,173,263,-857,-77,747,-167,-226,-26,-482,-281,411,-1000,428,-470,896,391,249,-763,226,537,-428,-490,-37,120,428,67,-506,-372,-372,-384,599,474,1000,-224,-428,-154,210,-103,454,1000,614,-639,513,199,1000,-672,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00758() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{-232,-1000,-31,-1000,1000,-401,615,-192,1000,376,-474,-781,36,-1000,198,-576,664,-94,-428,-1000,590,1000,-1000,-652,-603,745,95,1000,338,1000,-1000,-2,952,-182,567,393,538,-494,-156,1000,-831,-307,-429,-521,1000,460,-707,470,686,-716,499,-971,1000,-1000,730,1000,161,1000,0,-305,117,75,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00759() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{1000,-1000,400,-1000,-473,442,924,113,400,242,734,1000,-20,314,684,1000,-773,264,361,-321,-94,718,671,1000,238,64,-147,347,966,-281,1000,433,-121,621,-928,-357,552,54,706,-223,-245,16,75,1000,-282,86,-75,400,-324,623,-1000,-742,-647,460,-1000,1000,953,-941,-84,847,-667,521,280,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00760() {
        org.junit.Assert.assertEquals("ARRAY:[C:22:24:java.lang.Character:MA==:24:java.lang.Character:eA==:24:java.lang.Character:OA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==:24:java.lang.Character:MA==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray():char[]",
            new int[]{1000,-1000,-1000,-1000,-1000,779,432,-764,-1000,-1000,1000,834,-800,860,1000,-782,-861,-196,1000,1000,-850,748,563,-530,-1000,252,208,-1000,713,-728,290,-1000,-430,352,-1000,862,1000,-671,181,1000,-350,-1000,116,-21,-1000,1000,-775,231,862,-453,-1000,-1000,-1000,1000,-395,943,1000,1000,197,1000,-366,386,1000,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00761() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-388,326,-1000,-1000,-262,-115,949,532,1000,347,-417,186,-384,-40,-1000,-102,-48,842,-435,1000,-1000,-1000,1000,1000,-878,-873,-1000,-1000,-222,7,-1000,-144,-1000,25,-216,-356,152,-1000,300,1000,263,-1000,-860,-414,-1000,466,809,-1000,-579,-328,1000,-1000,1000,-1000,-1000,0,490,1000,461,-538,-654,540,-1000,981}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00762() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{1000,-614,-1000,898,134,86,419,-211,-273,91,858,-985,-402,-68,-413,209,167,-873,487,-723,-504,89,-1000,138,900,286,-62,-258,-736,1000,-144,302,711,1000,-745,357,-314,-243,300,185,1000,508,-10,-969,405,643,809,-1000,-667,249,-1000,1000,385,-922,-821,245,-975,1000,-928,-584,-131,540,1000,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00763() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{197,252,-41,600,-224,882,918,-469,-78,-587,-755,-701,-179,463,756,-915,-752,-387,-314,-248,408,-500,-1000,-213,303,-1000,-392,597,291,4,1000,-422,294,708,59,325,611,-741,1000,382,475,-313,706,992,920,-868,320,522,624,-847,149,-1000,357,292,1000,-151,1000,-1000,206,461,400,387,1000,-369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00764() {
        org.junit.Assert.assertEquals("ARRAY:[C:1:24:java.lang.Character:eg==", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-388,-43,-513,759,-490,-115,603,-419,697,-635,-947,327,-384,292,-1000,-437,-48,-194,1000,902,-780,-183,-667,-335,-861,-426,-357,-280,905,888,309,-143,-357,1000,-471,207,-1000,-590,300,1000,1000,123,287,138,-107,1000,-193,-382,590,1000,802,-1000,755,-690,-446,-128,261,1000,-1000,-538,-647,-17,-396,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00765() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-640,-679,-471,64,-467,-739,263,-604,822,-341,-190,199,-351,-36,-1000,378,-104,505,687,-707,-679,-947,-481,37,-841,-613,72,-472,437,601,31,-265,-814,1000,211,-88,-1000,-1000,68,1000,541,-899,-463,-316,-531,1000,-214,-565,723,617,797,-613,960,-938,-713,-86,-605,1000,-1000,-516,-718,-1000,-598,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00766() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{1000,-1000,-675,-964,-153,1000,-676,137,1000,513,-714,408,1000,1000,1000,-626,760,202,-168,-1000,-217,192,218,505,-448,1000,1000,719,1000,-1000,8,298,-74,196,-1000,136,171,-1000,-255,1000,1000,479,380,719,-454,-1000,-766,-1000,-93,126,-738,572,-1000,78,1000,1000,-224,1000,1000,-114,503,-1000,973,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00767() {
        org.junit.Assert.assertEquals("ARRAY:[C:0", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{-1000,678,299,1000,-388,-167,-213,-671,192,-662,86,-192,587,-1000,-685,575,4,912,1000,528,-386,-732,-865,-1000,429,-231,-1000,755,-604,-42,1000,116,475,-157,562,-1000,-413,-673,824,620,-926,-1000,-210,-408,28,703,1000,-115,916,1000,732,-708,263,-765,212,-85,319,-443,-1000,-577,-1000,-106,-605,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00768() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toCharArray(int,int):char[]",
            new int[]{770,-1000,-768,-807,-105,718,1000,-463,1000,51,786,-96,-393,-957,-1000,1000,115,946,673,-707,-1000,-392,286,85,1000,285,-695,-1000,-672,374,-1000,838,686,-379,-736,-1000,-1000,-992,-1000,955,-339,-1000,-1000,-1000,-531,884,-266,-1000,-943,617,606,683,1000,-938,-1000,-86,-1000,1000,5,-881,-1000,-1000,-598,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00769() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{-1000,83,201,691,750,1000,459,677,70,947,811,-143,-640,1000,-519,1000,650,-234,-1000,-131,736,1000,-1000,836,-698,-1000,-9,-1000,649,1000,-52,-1000,767,-1000,314,-7,754,417,558,-966,-494,116,-1000,-777,797,1000,1000,-1000,-296,106,-896,1000,-304,259,926,-419,231,421,1000,-542,-298,-709,417,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00770() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{15,831,-536,-491,-791,-860,-414,-218,-632,85,-681,141,-896,-638,-320,-313,12,422,810,-22,-5,-459,-57,202,568,914,-506,-411,566,-933,569,-738,-739,621,698,-423,804,985,-625,754,-905,-105,-856,58,-252,892,-635,149,-386,-177,561,327,517,199,-698,-628,279,908,938,-583,925,131,189,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00771() {
        org.junit.Assert.assertEquals("java.lang.String:GHg4MDAwMDAwMDA=", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{1000,-1000,-1000,-45,-1000,528,854,668,-1000,-1000,-1000,-945,-584,953,-762,-1000,933,-63,823,-1000,-646,96,-1000,1000,-1000,1000,1000,-279,1000,377,1000,1000,1000,1000,845,-1000,-1000,-778,1000,115,-1000,66,832,-814,797,-149,-583,1000,473,100,1000,-1000,1000,-920,-719,714,-172,-1000,-1000,-783,1000,-301,1000,403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00772() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{1000,124,-555,131,184,-99,-170,8,-1000,947,-1000,-1000,-586,815,181,-1000,1000,976,1000,-458,-957,-451,-886,322,-774,1000,262,433,1000,234,90,1000,1000,1000,1000,-1000,-51,-115,505,-173,-907,251,871,-854,1000,-745,791,-1000,772,675,968,-277,-304,259,-947,721,231,-414,1000,-783,1000,413,1000,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00773() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{-1000,182,603,813,-43,267,9,-966,-293,1000,173,1000,111,-607,-1000,-78,291,-647,-1000,-886,727,1000,804,722,975,-1000,-1000,362,-809,348,660,741,-152,-729,635,1000,25,107,-419,-333,-847,1000,-1000,166,-445,188,-1000,-912,727,-723,620,1000,-1000,-865,-475,971,-172,1000,1000,1000,-390,-493,-463,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00774() {
        org.junit.Assert.assertEquals("java.lang.String:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{-670,-112,-432,-226,-157,-382,-468,-548,-166,100,997,609,922,747,372,175,-96,477,278,209,754,820,-551,-274,-958,-318,863,413,928,-655,-21,-871,-131,-407,-656,-825,887,533,79,505,-611,-479,-279,259,279,1000,1000,97,750,124,665,-869,-189,-353,504,-1000,1000,638,-1000,-547,533,964,826,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00775() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toString():java.lang.String",
            new int[]{1000,-631,-863,164,-470,-217,197,-67,-1000,-791,-1000,-1000,-357,1000,848,-577,1000,1000,1000,-245,-1000,-1000,-1000,1000,-1000,1000,1000,-261,1000,403,-291,687,737,854,1000,-1000,124,-121,1000,505,-1000,66,647,-975,1000,-43,1000,871,728,1000,236,-1000,742,-176,339,-974,424,-1000,553,-1000,1000,715,1000,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00776() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-194,-211,988,788,1000,562,-61,-453,552,-877,456,179,595,335,-1000,-134,802,966,118,95,468,-185,290,-1000,712,-837,300,-381,-576,-60,309,263,-274,-243,799,499,-134,-1000,619,-290,-140,292,-140,1000,-20,-164,335,-303,-523,573,-625,553,540,-92,-634,400,426,449,884,65,-917,-934,187,-428}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00777() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{945,-120,144,405,724,-137,-599,615,712,816,355,-668,-124,994,628,4,983,77,-612,904,461,-6,579,532,-354,-797,-696,790,-24,-149,-65,-76,-822,-47,-911,323,647,707,-989,-344,314,372,-933,-449,120,767,-897,30,897,350,221,280,-67,-884,156,945,-236,-908,-94,146,897,70,-497,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00778() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-1000,-1000,-1000,-1000,1000,-123,233,-992,1000,-842,-201,-1000,886,-290,1000,-51,952,1000,115,424,-1000,694,-857,-1000,-1000,-801,1000,1000,-36,484,524,1000,-1000,329,-520,885,-1000,822,-1000,-609,-513,-728,-199,-707,-1000,-1000,533,974,-1000,387,-977,249,344,101,-923,934,-739,1000,-672,-1000,-1000,-898,558,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00779() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-712,-353,629,-260,-908,134,-352,-1000,122,-1000,-773,-1000,1000,-854,-531,-1000,635,495,-258,-302,104,570,-541,-1000,-473,-8,1000,-439,-1000,530,59,-33,-620,-859,-217,-1000,136,-100,77,618,935,575,-1000,-66,-1000,-1000,-51,980,-675,1000,-22,-1000,196,288,550,35,204,575,208,-457,-775,-728,-688,826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00780() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{-382,-1000,798,-192,886,1000,-582,-1000,-133,-771,-59,-801,989,-912,-832,-65,1000,-434,-4,-801,914,336,-94,-1000,-85,-340,1000,599,-461,-178,-27,1000,81,-673,1000,589,246,-33,-160,426,568,-362,-1000,510,-1000,-1000,-745,-157,-789,217,-155,-674,4,1000,190,-1000,258,-629,417,247,-579,-662,-413,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00781() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{928,485,764,849,-911,235,-961,831,-416,-369,-205,915,-130,669,-207,1000,-511,309,-623,-1000,-27,-249,743,-568,755,-117,-804,-978,-102,-514,-467,-937,870,-414,-356,-524,1000,378,604,-1000,188,4,-620,913,923,876,-691,-1000,-228,37,-182,389,-637,-225,-264,142,-194,315,228,696,-1000,552,795,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00782() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "toStringBuffer():java.lang.StringBuffer",
            new int[]{160,-1000,560,-1000,1000,564,655,-104,326,201,-553,-1000,744,-626,1000,1000,799,-389,927,-3,-811,769,-346,793,-1000,70,-397,1000,870,-1000,129,1000,772,1000,657,1000,-534,250,-1000,-771,-810,-1000,437,-753,-526,-455,-24,-1000,-1000,-786,-825,791,-247,-114,-1000,748,-419,445,-252,-319,-779,375,-275,159}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00783() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-256,-523,126,-114,-190,1000,-616,139,341,-1000,-133,76,-1000,-278,-1000,-1000,927,506,-577,-506,-323,-1000,939,1000,-48,347,-257,-1000,-335,-210,-870,926,-917,-414,1000,521,-164,-460,-383,-685,-857,-348,-882,527,594,44,-1000,-667,-882,-771,303,489,-237,-361,-404,490,499,-647,124,635,-6,629,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00784() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-1000,-58,53,-510,1000,1000,289,101,1000,-348,-710,-297,319,181,-564,1000,-229,326,-1000,1000,-281,1000,1000,199,1000,1000,150,-70,96,-22,-28,240,535,1000,-449,750,-817,120,853,106,182,1000,824,-567,-1000,960,-645,-1000,337,20,871,1000,-917,-1000,277,-547,655,365,8,-1000,-160,858,510,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00785() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-736,-945,494,82,112,252,-723,-186,1000,-879,464,-597,-215,-976,47,50,111,539,160,928,-1000,794,1000,478,1000,-238,1000,-440,-314,253,-280,1000,-306,383,1000,451,-575,-568,-637,1000,241,-1000,136,19,-506,199,-845,-490,-1000,400,343,907,224,-177,-191,968,-75,-1000,-87,-155,-160,-80,261,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00786() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-13,-406,-38,428,475,456,-282,715,701,-400,41,-751,-496,-576,-400,-400,-519,-804,514,208,-400,95,-46,400,583,85,-324,-375,452,690,308,-112,-276,270,-675,400,169,-106,-484,515,349,52,29,-136,-2,-630,-1,-423,-360,-442,-163,293,480,-431,1000,-550,769,427,1000,470,587,-212,400,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00787() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-503,-1000,617,-114,-44,295,-381,-148,1000,192,328,186,509,84,564,400,-133,-11,990,-913,-976,869,840,619,-181,1000,-178,-1000,300,-329,67,926,-592,611,1000,860,-189,-716,142,-1000,-550,9,-208,-574,-925,698,-797,-667,-882,400,303,1000,734,-124,-280,500,499,-36,258,635,189,-38,328,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00788() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{-216,-1000,562,339,-434,734,-1000,-689,473,1000,1000,-71,-258,175,1000,1000,-112,1000,918,-925,1000,645,-753,-426,344,1000,1000,-927,516,105,-1000,781,-1000,1000,1000,-382,30,803,-171,207,-82,-1000,34,294,152,1000,-729,1000,-1000,-1000,695,-236,-1000,1000,359,-275,312,-1000,261,-325,-1000,282,-586,104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00789() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{404,1000,1000,1000,-316,-921,-3,736,461,-821,201,456,556,-92,-163,1000,-613,-654,-197,481,1000,1000,-99,-1000,342,754,1000,893,405,374,718,-614,-744,681,-1000,-664,415,-593,288,1000,1000,496,538,-215,-1000,240,-652,1000,1000,771,911,-702,-327,1000,602,-800,-326,660,-135,211,-209,-431,845,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00790() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.lang.text.StrBuilder", DEReplay.run(
            "org.apache.commons.lang.text.StrBuilder", "org.apache.commons.lang.text.StrBuilder", "trim():org.apache.commons.lang.text.StrBuilder",
            new int[]{811,-1000,288,-1000,-1000,388,-1000,564,-456,336,-873,-247,211,-43,-313,-1000,1000,288,-1000,-782,1000,-1000,658,-456,267,92,463,-94,-565,803,-1000,207,-521,-762,109,-1000,427,958,-199,1000,78,-179,-469,1000,899,-533,-82,133,-75,-840,1000,-1000,-1000,-1000,520,128,183,-531,-266,1000,-547,1000,-40,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00791() {
        org.junit.Assert.assertEquals("java.lang.String:XgZYWw==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[]):java.lang.String",
            new int[]{-809,-129,-230,-765,-804,222,-109,-65,-34,912,-370,-758,862,-506,216,475,903,-221,917,171,220,816,-850,-2,794,919,-288,-140,-95,73,283,-635,-793,-96,-798,346,-844,119,792,-519,626,-176,990,-763,-990,-558,-115,-667,804,105,-55,-303,755,321,-875,-478,-968,490,882,-584,534,427,162,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00792() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[]):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00793() {
        org.junit.Assert.assertEquals("java.lang.String:GA==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{-381,-1000,1000,-403,881,-998,857,-1000,731,1000,-488,-667,1000,177,1000,613,-735,-743,-292,-749,1000,666,638,625,49,-1000,1000,257,-230,1000,-1000,224,608,327,-737,60,-1000,-555,-258,374,-120,738,-1000,-135,1000,-1000,-81,83,59,-1000,-512,1000,-1000,923,309,-1000,-806,-341,530,-12,-1000,510,266,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00794() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{-70,319,-157,-313,-644,-220,127,578,916,-471,-927,471,695,376,-212,-796,-695,-362,-840,-75,-250,-155,-691,733,331,447,-610,-430,-162,363,-781,149,-265,-278,-119,720,506,-779,283,85,871,-876,398,438,-769,-850,-118,296,330,-781,673,-881,881,-633,208,-1,-286,991,-19,282,828,-952,288,-119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00795() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{293,-997,979,297,697,785,614,-942,40,714,212,-830,964,101,889,890,-79,-690,156,-91,768,535,287,47,-175,-719,449,254,-636,845,-734,-340,947,-373,-777,-538,-502,145,-936,26,-451,-723,-829,-275,721,-579,-193,783,163,17,-937,662,-596,223,362,-637,-106,359,452,-546,-654,216,-33,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00796() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00797() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{-412,154,439,-196,-883,-930,-166,926,-587,-374,498,-881,660,-681,297,-578,84,-428,47,-503,-300,-246,226,645,-869,-337,-739,-138,399,239,-978,-852,48,-251,-140,-737,-577,-213,265,760,481,8,-909,-746,128,760,-840,638,748,482,12,-797,320,94,612,-506,323,-568,-140,-868,-508,-725,-54,802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00798() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(char[],int,int):java.lang.String",
            new int[]{-989,725,-401,-846,-867,-141,-781,-71,726,-828,-332,367,654,779,378,-174,227,561,-178,863,-239,-671,-797,513,809,-571,-758,405,-437,885,-208,-279,381,-320,-291,-937,855,846,153,-94,-611,646,359,-578,883,-667,777,236,-235,725,450,926,379,-875,-468,-72,517,36,-118,927,744,511,880,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00799() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0NA==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "org.apache.commons.lang.text.StrSubstitutor", "replace(java.lang.Object):java.lang.String",
            new int[]{-480,-894,-306,135,-826,513,682,-794,263,340,253,-321,576,-960,327,-207,954,865,919,-278,-970,-741,85,-671,-872,-594,435,-214,-960,-743,-494,-262,-751,-629,-245,588,626,-884,847,382,615,-66,483,-240,-503,-340,107,502,-541,-786,458,-703,-162,374,-804,526,233,797,580,129,929,-29,-454,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00800() {
        org.junit.Assert.assertEquals("java.lang.String:b2JqZWN0Mw==", DEReplay.run(
            "org.apache.commons.lang.text.StrSubstitutor", "", "replace(java.lang.Object,java.util.Map):java.lang.String",
            new int[]{-178,613,-212,-995,-172,-799,819,631,671,-300,-656,-523,649,39,231,133,-993,961,-658,840,370,-989,997,315,939,-174,390,-403,141,626,-437,-856,448,-132,197,406,879,-681,-38,-702,349,-493,-534,585,-519,-583,-76,-968,-261,-375,-382,-619,826,609,-82,695,-824,902,-818,-590,56,961,-770,-425}));
    }
}
