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
        org.junit.Assert.assertEquals("java.lang.String:MDQraDZfb18wMDA2CglVQmcKRlpfNg==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String):java.lang.String",
            new int[]{846,-377,415,39,-758,-853,622,501,-435,443,361,-1000,24,-1000,-49,13,-960,1000,212,-996,-15,605,-410,-285,-314,345,-1000,1000,-130,358,-294,-638,-409,-914,-479,1000,305,-1000,-207,492,794,603,-1000,1000,836,132,-757,-587,582,-21,891,1000,-29,-859,568,19,217,-944,-927,-867,-924,-358,367,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.String:Y2YxMjcxMzA5NTQzMjA0NzY4NzY5N3IzQjBZRUw2", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String):java.lang.String",
            new int[]{785,-819,126,39,-171,62,-59,938,-895,469,27,-68,-531,-804,-82,-315,-805,1000,-993,115,-216,248,-711,-304,351,-184,-1000,1000,416,469,-294,-638,-799,-54,-1000,921,90,-942,102,163,794,-209,-1000,1000,-504,-188,-49,1000,965,12,1000,1000,-78,-1000,-162,-882,889,720,-947,-119,-924,-692,367,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.String:MFJhLy14Sy0xNTExODI4NDg5ODA3V2hQMzNBLmlxdXBj", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String):java.lang.String",
            new int[]{311,-811,-418,349,70,571,-639,-515,-132,-785,489,530,472,-185,551,768,230,-304,429,926,178,-433,18,-613,385,-614,-485,511,-62,401,39,-29,-385,577,70,-655,-487,-229,400,-286,-309,-753,151,-492,226,-543,-251,-582,-11,328,-284,839,916,-243,626,37,600,484,-368,-42,595,-939,620,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:eGgtMjM2NDgtMTIzMTFcRi0yNA==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String):java.lang.String",
            new int[]{846,-884,-499,39,-746,-165,317,443,-585,164,-688,214,423,-669,-49,13,-486,1000,228,-369,-15,-639,-410,-116,376,-75,-732,568,430,305,-388,-114,87,-666,-122,589,305,279,-207,-276,511,139,-896,421,60,132,-484,-639,1000,-1000,532,1000,-1000,-859,874,-83,555,-257,-507,53,-1000,-116,1000,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.String:OTA5Mzg3NzU2IFIwMGk1bFRZRmloLTIwNDc2ODc2OTdWck9YeC01NTZa", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{380,137,-371,-916,175,-903,-49,-431,-586,247,-337,-421,-192,-797,602,-153,609,870,940,327,625,-754,192,-367,885,738,432,-294,-760,518,798,-759,-236,-888,162,548,400,-450,771,280,-4,198,-411,705,584,701,115,-922,591,320,-500,53,-509,-584,-40,49,372,-189,-326,-963,-170,347,609,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.String:NjA2X18wTzY0NjBGNkU2", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{-1000,-917,-113,-428,893,-985,-633,-58,1000,-1000,-1000,568,121,1000,75,1000,977,-527,1000,111,1000,792,-1000,-696,-1000,-108,8,417,-22,-1000,1000,-339,33,-986,234,-410,-687,-1000,879,147,287,-1000,-1000,110,842,231,101,325,376,-1000,-1000,-484,1000,-747,22,-500,-674,1000,-48,353,-324,-964,722,-711}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:MF9fMkxcY0k2X0MtMzU3OTFfNzBnXzZDNjZQ", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{-34,988,-499,1000,-591,-53,-520,-1000,-1000,428,402,-642,-982,-666,1000,-1000,109,661,-1000,-987,190,-197,775,1000,819,1000,1000,-91,-903,1000,-669,1000,872,-184,-652,-355,1000,778,-1000,-158,1000,1000,400,-459,-1000,335,556,73,-638,1000,-430,635,-752,272,-376,54,338,-1000,-1000,-959,1000,778,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:XzBMMGZCNStPMDZfNl9weF8wb0I=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{-803,106,366,508,1000,-530,-1000,-697,473,-688,-482,250,431,-719,476,-99,1000,-1000,1000,-1000,25,-109,-1000,815,450,179,677,302,956,-1000,1000,456,1000,-916,-1000,-317,264,-752,-171,-38,981,-1000,299,886,-261,673,841,-362,-848,-333,-1000,-624,1000,-479,370,-1000,930,700,-1000,-828,-784,-1000,163,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:X0ZfMDJMMTZfNjBiaTZyNjY=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{-290,841,398,316,-125,318,-1000,-669,-1000,-688,-211,-972,615,-443,1000,-1000,1000,0,224,-124,1000,524,1000,1000,81,1000,782,-695,1000,1000,-765,-54,312,-1000,-509,746,982,1000,1000,-298,1000,1000,305,53,254,908,-45,819,1000,1000,-164,-1000,436,873,-1000,-306,885,-1000,-1000,-538,517,-800,-442,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:Ul9oMC05MDkzODc3NTZBIGlXKzIwNDc2ODc2OTdfM182L18=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{220,249,767,1000,400,567,-941,-1000,159,-378,732,-35,918,373,129,-222,114,-1000,-565,-1000,1000,635,-1000,114,379,-1000,1000,-302,-103,-956,1000,1000,1000,506,-1000,-39,434,144,-1000,-935,1000,-733,-711,345,-575,272,1000,-328,-1000,-163,-779,-732,752,496,1000,-1000,1000,-190,-1000,-489,294,-758,-1000,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.String:X1I2LTIxNDc0ODMyNmo2Tg==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDuration(long,java.lang.String,boolean):java.lang.String",
            new int[]{-1000,-332,927,693,461,509,-1000,-160,1000,-256,-211,1000,161,1000,-519,833,733,-1000,119,-1000,-1000,524,-1000,1000,-354,-878,-582,1000,-371,-1000,436,1000,1000,713,-1000,-1000,725,-686,-1000,39,797,-1000,305,-167,205,-494,-45,-39,-1000,-1000,221,-415,1000,394,-1000,-1000,-604,123,58,40,517,-1000,-110,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:MDowMDowMC4wMDA=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDurationHMS(long):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:UDBZME0wRFQwSDBNMC4wMDBT", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDurationISO(long):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.String:MCBzZWNvbmRz", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDurationWords(long,boolean,boolean):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.String:MCBkYXlz", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDurationWords(long,boolean,boolean):java.lang.String",
            new int[]{-125,-178,-27,752,-359,364,-338,860,639,-773,-83,-494,-10,890,-420,-749,674,-38,614,673,666,-456,-894,258,-556,684,547,590,-503,-554,-399,-440,123,96,431,-316,-690,113,-719,-797,735,-691,433,-322,-926,-792,-35,-1,-307,18,757,369,535,-995,-883,-308,562,-74,73,10,451,240,897,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.String:LTI0IGRheXMgLTIwIGhvdXJzIC0zMSBtaW51dGVzIC0yMyBzZWNvbmRz", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatDurationWords(long,boolean,boolean):java.lang.String",
            new int[]{-806,-488,1000,-27,184,-1000,-989,-237,1000,1000,-1000,546,-136,404,-85,453,-907,-93,-608,93,637,-1000,-1000,-720,-144,-347,290,241,-520,-99,-43,964,656,-181,955,-296,-706,639,-1000,-673,-1000,1000,563,-555,1000,1000,1000,-571,-32,-1000,-932,852,1000,635,1000,1000,-1000,-299,-1000,1000,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.String:cUZfTFcwXzBEWS5lMA==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String):java.lang.String",
            new int[]{359,136,-713,1000,-321,-338,880,763,-471,-669,-1000,-95,200,-520,-1000,-697,-103,-508,-362,440,-37,-446,1000,-5,400,-425,-274,-172,959,-439,1000,-913,-392,145,541,-506,417,-739,-109,1000,1000,1000,504,477,-344,722,-1000,-982,-1000,674,-1000,-595,620,911,425,-277,-1000,227,64,-521,1000,-1000,-1000,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.String:NmdfLVpfTF9fXzE1ODYwOTMxODMtMnUJal82LzY2NlA=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String):java.lang.String",
            new int[]{1000,-1000,-21,1000,109,-763,144,-377,1000,-197,-1000,-434,-152,-1000,-663,-1000,-1000,-1000,732,-292,-850,-751,779,-478,-1000,1000,-643,1000,1000,1000,-730,-1000,-1000,1000,1000,755,-348,1000,-1000,541,868,1000,1000,154,653,-1000,382,1000,-798,1000,-542,815,461,1000,1000,490,-819,-1000,771,-1000,1000,-1000,558,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:MDBHOTZ0NjByRWEwNUs=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String):java.lang.String",
            new int[]{-96,-206,-88,-274,141,453,-175,-836,-972,-747,-171,-133,1000,-113,1000,-28,737,537,10,-469,-137,-451,-518,290,947,-724,461,-525,-1000,-440,-979,500,-142,297,293,169,109,-726,783,507,-353,-602,-1000,889,105,866,105,829,-254,419,-633,-539,-622,-77,410,-932,365,1000,-150,86,-43,432,436,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.String:X3JjNDZ4OEl6X2dnNjIxMTY3N3hsNktfay0yOTIyNzcwMjVw", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String):java.lang.String",
            new int[]{1000,260,-311,-421,15,366,-596,198,-1000,-186,154,-307,1000,-322,-205,541,-959,-1000,584,442,-420,-688,-845,956,376,1000,543,-148,-619,-392,-685,322,-255,-101,72,529,40,5,983,408,267,746,-639,622,-359,1000,-60,380,168,716,32,-1000,-553,-191,-33,-1000,211,-46,-863,-321,-81,-1000,943,557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.String:LS0xMDAwNjkzMDY4NDA2", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String):java.lang.String",
            new int[]{349,-1000,475,1000,-487,-1000,943,-1000,669,-655,-1000,195,-1000,-1000,922,-1000,141,278,86,-1000,-1000,-592,644,-1000,-1000,1000,-351,1000,955,197,107,-1000,-1000,1000,560,-284,-868,-694,-1000,689,-524,1000,1000,-1000,1000,-1000,467,1000,-978,-279,-1000,293,1000,1000,674,700,414,-1000,1000,-120,864,-573,632,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:L0QwOQl0KzA2dDBYRmxONm9yVV9fVjkw", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String):java.lang.String",
            new int[]{-96,439,-88,-660,-51,453,-550,-626,-146,-742,-17,-133,850,-113,-648,213,1000,810,84,-580,183,-547,-803,290,947,-399,624,-1000,-1000,-440,-559,758,229,-123,817,192,-164,-887,783,444,-378,-1000,-1000,777,-38,1000,209,409,-313,419,-519,-539,-481,-497,77,-1000,131,1000,-150,498,-463,-882,488,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.String:MjA0NzY4NzY5N1gtS0NyNktvXGJWNi03NzU4MDgw", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{89,609,151,840,767,-1000,-648,290,256,-154,-931,-451,606,737,1000,-25,805,-429,-273,696,1000,480,105,-27,-914,-760,-352,-1000,-1000,1000,-1000,-1000,-44,-1000,-469,-562,-452,722,571,103,806,513,1000,834,518,-281,-926,-1000,142,1000,-1000,-114,1000,424,23,-50,-1000,-940,1000,-747,-1000,1000,-318,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.String:RDY2al9vbmYxNTEzOTc1OTcyWWcrX180MDNjNko2Tg==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{-319,1000,-942,-774,-819,-241,-111,-354,-316,1000,1000,871,-1000,379,-900,299,241,699,-481,-790,-1000,-1000,125,74,-201,1000,755,1000,-519,-26,-68,-487,-552,1000,1000,1000,-139,-410,-401,247,207,1000,-1000,-1000,1000,520,-826,427,-273,-123,496,548,-563,-859,526,695,-384,1000,-956,277,377,1000,-1000,868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.String:XzA2ajZfNi0yMTQ3NDgzNjQ4QllyNmc2NjM2XzI=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{-928,1000,1000,-774,687,-793,-830,769,-1000,-875,1000,871,1000,-1000,1000,267,889,557,808,1000,584,1000,1000,74,1000,-1000,-566,20,-519,126,-65,-1000,1000,850,257,-1000,1000,-635,1000,1000,540,-1000,-1000,1000,-617,-1000,1000,157,1000,1000,496,-1000,1000,-1000,1000,-997,-1000,-1000,-956,525,-101,-553,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.String:eAkwX0dWb0YrNkw2NjZ0Cm9fT19fNnVf", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{-654,240,789,899,750,564,-1000,699,-322,-854,164,-1000,681,-795,947,-172,133,1000,-947,1000,1000,1000,526,-782,-47,-1000,50,-1000,-1000,1000,-538,-1000,-87,199,-782,-85,-21,-332,1000,1000,203,386,-245,1000,489,856,121,-913,-392,1000,-125,-655,1000,399,838,-1000,876,276,-184,-1000,-899,-113,761,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.String:MjMxMA==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{-291,907,789,658,553,231,-956,488,-979,-76,240,-560,556,-985,319,-662,333,281,-221,755,405,753,754,-964,600,-879,-338,-36,-834,585,-538,-913,455,966,476,-96,-8,-500,477,623,143,-366,-755,990,332,-667,207,-192,232,968,-125,220,691,-336,833,-462,-696,156,-216,-511,-559,-353,761,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:NjY2MzE4ODM2NDlnN19rWUItQkkuNS8uSjZORzU5Ng==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{257,1000,-900,625,-819,-490,370,-702,1000,1000,1000,74,196,-907,-348,-1000,91,699,676,-576,-815,896,916,-776,919,-930,755,1000,-519,184,469,-188,779,-577,1000,-997,289,-907,-1000,247,134,401,-585,-930,-21,704,474,224,413,201,-872,561,-266,-859,526,65,104,525,-499,315,576,42,-1000,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.String:Xy0yMTQ3NDgzNTcwcmJhbDZLNlhDME8=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{1000,1000,789,82,973,119,-182,613,-1000,-443,240,-273,223,234,1000,-25,1000,130,-530,474,405,651,125,-630,-800,-959,-931,-3,-1000,1000,-538,-1000,976,1000,476,-668,1,-555,-38,420,1000,180,1000,990,-358,-982,-233,-594,245,405,-186,668,412,647,1000,802,-1000,27,-408,24,-358,68,761,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:ZmFsLTIxNDc0ODNl", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{-472,70,-1000,1000,-261,-840,-433,-384,-137,1000,1000,-519,-46,-1000,-1000,-1000,-359,-488,531,-599,-1000,-114,1000,-1000,1000,-275,-299,953,534,-32,1000,-509,17,-1000,1000,663,988,-630,-265,-68,-1000,-303,-261,-1000,417,905,-265,705,506,997,-901,1000,-580,-1000,-416,-181,598,1000,-685,38,315,398,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:NlYtMzU3OTFYVg==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{166,-695,-1000,1000,511,564,-406,-295,1000,-298,164,-296,767,-274,1000,-1000,100,-1000,369,27,236,174,475,-782,135,-658,-297,-27,-539,1000,348,263,803,-748,137,-153,456,312,-801,-1000,-418,-276,-32,263,369,856,-71,-766,620,137,-385,341,635,-116,-391,-981,876,276,508,-872,-769,-1000,-206,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:MHgyNzA=", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{-709,287,435,-50,-393,637,-1000,-468,625,831,-1000,475,-271,-1000,1000,-1000,-1000,699,-196,367,940,-381,644,-151,-418,617,249,-544,-678,-306,-1000,1000,-760,1000,-1000,1000,-720,303,831,-522,-1000,330,-1000,1000,-570,611,952,85,-638,-122,851,262,1000,610,387,-74,523,293,6,-838,-687,-1000,47,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:eDM2RTBYNF9KNjZPSndEdA==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{440,-196,-858,685,637,-1000,50,-284,-180,358,1000,-386,389,-225,-280,-1000,968,-704,290,-305,-736,174,465,-681,694,-658,-45,1000,-284,964,1000,160,615,-748,1000,-153,842,-210,-1000,-896,143,90,-771,-900,87,520,351,-108,270,193,-738,338,115,-1000,-703,-335,432,322,-285,22,-143,-369,-1000,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriod(long,long,java.lang.String,boolean,java.util.TimeZone):java.lang.String",
            new int[]{1000,670,-396,-1000,-978,-131,102,459,283,-895,48,-93,-565,-1000,183,749,-1000,1000,-343,503,564,-1000,-62,1000,-1000,617,308,-850,241,124,219,307,-885,1000,-487,823,-1000,-730,1000,132,22,1000,50,959,-233,-585,-310,-468,-578,54,1000,240,510,974,387,-143,41,754,-79,-911,-327,114,-202,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:UC0yOTIyNzcwMjVZM00xOURUMjBIMTVNNDAuNTQ1Uw==", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriodISO(long,long):java.lang.String",
            new int[]{689,20,-12,1000,-117,-1000,864,-1000,-349,-571,-470,-1000,-1000,1000,-260,1000,-1000,482,-173,501,861,1000,-512,-725,578,709,-507,-324,-15,-1000,880,291,-1000,-681,482,-358,482,967,1000,-345,1000,1000,194,1000,-567,-472,-1000,628,644,-661,-840,-958,935,1000,848,-110,1000,371,340,8,507,-502,783,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:UDBZME0wRFQwSDBNMC4wMDBT", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriodISO(long,long):java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:UDI5MjI3NzAyNFk2TTIyRFQxMEg0MU0zMi4xNjBT", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriodISO(long,long):java.lang.String",
            new int[]{904,219,1000,-736,-1000,13,266,1000,276,-1000,-67,-308,-390,914,818,1000,785,1000,193,-217,494,464,775,-36,-545,1000,127,344,269,507,-509,-1000,-1000,-8,598,81,327,-140,637,104,633,-688,-1000,-484,-1000,-476,1000,-19,-439,-356,1000,50,-136,1000,-1000,62,1000,1000,-118,1000,-859,458,90,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:UDBZMU0xOERUMTdIMk00Ny4yOTVT", DEReplay.run(
            "org.apache.commons.lang.time.DurationFormatUtils", "org.apache.commons.lang.time.DurationFormatUtils", "formatPeriodISO(long,long):java.lang.String",
            new int[]{-195,1000,-267,-621,-675,29,-747,775,1000,338,-1000,175,1000,303,-224,-1000,876,-654,1000,-1000,717,-1000,1000,-329,-175,1000,-816,911,-386,761,-702,-819,366,-412,-514,432,-1000,-1000,238,21,-1000,-1000,40,-1000,-550,1000,1000,-1000,1000,53,508,-1000,-1000,-1000,-534,1000,-811,261,-781,-139,-191,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:MDowMDowMC4wMDA=", DEReplay.run(
            "org.apache.commons.lang.time.StopWatch", "org.apache.commons.lang.time.StopWatch", "toString():java.lang.String",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
