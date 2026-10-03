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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{222,-1000,-1000,-185,987,35,1000,535,570,-24,1000,237,-1000,429,-1000,-315,1000,1000,708,1000,-173,1000,1000,-214,702,-266,779,-241,-226,-499,736,-85,1000,-319,449,603,749,-1000,-186,-1000,-1000,375,-377,59,-284,266,266,-637,-1000,603,411,-729,289,87,1000,-298,881,-15,-309,1000,-1000,1000,100,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{525,1000,1000,488,-1000,1000,-633,922,1000,-1000,-1000,1000,1000,-1000,1000,110,-1000,-336,-1000,-1000,-987,-1000,-1000,1000,673,1000,-1000,-678,-1000,-248,-1000,1000,32,1000,-1000,902,1000,1000,-998,779,1000,-552,-1000,-1000,-295,1000,796,-518,-598,861,-136,1000,1000,964,-360,1000,469,-563,-48,-1000,213,-1000,-206,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{382,-294,81,744,-314,1000,-789,-1000,1000,-1000,-120,134,854,-1000,1000,-449,-1000,102,-1000,-549,1000,-470,-868,1000,1000,1000,-1000,-484,-559,294,-1000,756,-411,1000,1000,595,1000,434,-162,556,370,-824,-41,-947,201,1000,859,-541,-286,1000,182,1000,735,854,-474,1000,-69,-448,-146,-437,-1000,-1000,-1000,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{-206,394,675,-512,-676,-347,-897,849,-79,-756,171,-505,-266,-776,-469,742,-790,-416,-390,-470,-584,-755,-491,-369,882,-29,-309,-100,-715,557,156,601,396,-509,893,-179,-781,-158,711,722,995,585,-707,967,847,-221,-747,35,240,-938,343,331,149,-230,513,26,823,681,516,-638,432,-173,-45,-197}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{541,120,-313,-330,1000,727,-464,-564,1,-884,-618,343,24,-338,1000,413,-746,-57,-1000,316,-599,-800,-616,899,1000,1000,-1000,-1000,-1000,-1000,48,358,49,1000,-111,-422,1000,-337,-292,-544,620,-630,557,-13,683,1000,1000,-784,-658,917,-1000,480,881,-235,385,692,-509,-221,-222,642,-475,-727,346,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{541,1000,-357,-586,20,-275,331,662,-319,318,-661,237,-792,790,-104,267,876,968,999,355,58,1000,812,-921,-20,-20,702,264,-342,-841,486,-210,780,-791,781,-422,219,-191,-292,-919,-852,500,-370,570,400,-381,1000,-486,-1000,93,507,-745,881,-340,697,-737,399,97,-104,195,-28,703,346,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{-195,732,875,-199,-110,125,1000,-940,-454,834,-282,-76,1000,-584,346,-228,638,377,250,-879,185,194,-65,-787,-935,-1000,-265,475,142,38,-553,-241,-180,-844,-3,668,295,-160,-155,-395,-457,419,-815,89,34,46,-41,-206,-891,-86,596,-257,-503,463,-69,391,636,-785,-253,-400,-882,609,730,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{-820,1000,757,428,709,5,-647,94,-709,140,262,52,977,-1000,258,-507,-406,-531,-265,-1000,1000,568,-632,-865,-717,-949,-1000,-511,-1000,1000,-660,-1000,-872,-71,-616,289,-1000,98,-358,-85,-307,-929,-697,233,-1000,-542,-957,994,706,-145,614,289,-578,1000,367,-81,-527,-340,-439,-1000,-38,-875,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String):java.lang.String",
            new int[]{13,-113,-685,515,-242,1000,-210,-729,-384,-354,893,-1000,-718,525,1000,1000,561,955,-272,945,-302,-841,929,36,906,968,290,1000,-414,-581,-383,620,1000,-623,-1000,1000,169,440,-387,519,-787,1000,-1000,-1000,-449,1000,1000,-459,-1000,-704,742,937,159,1000,-438,-157,1000,-619,148,-1000,-539,-1000,-27,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-946,1000,719,-768,-1000,62,-1000,-604,373,-265,-784,501,-230,631,-509,-429,-357,1000,587,-1000,-530,-634,-608,-1000,153,-1000,1000,-836,-937,-226,-1000,-1000,-348,-1000,-1000,388,-1000,312,-1000,558,1000,966,-1000,355,671,71,-909,-1000,-606,-64,473,1000,716,-867,-1000,1000,-89,-635,-293,-337,1000,-1000,-1000,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{703,-35,-930,-413,-1000,1000,-238,1000,-101,397,-1000,-991,1000,1000,-122,-1000,-1000,552,1000,-1000,-1000,-275,-1000,-276,-7,-337,411,-1000,-400,-1000,853,-1000,1000,-1000,83,-205,846,1000,-893,483,-1000,1000,83,1000,1000,-1000,386,1000,1000,-439,-574,67,-46,60,-596,1000,-1000,-1000,-1000,57,1000,750,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:NQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-269,5,830,-662,-886,852,-388,-593,3,-868,996,-80,804,31,-99,719,58,-377,-810,-192,-988,10,716,436,933,-718,187,960,920,449,-879,-260,756,-913,-286,-48,155,-374,34,-601,676,893,-333,135,-501,838,-376,60,-700,187,-829,-512,-454,-794,62,156,-364,702,-391,126,-427,-64,-13,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-882,-584,119,-805,162,-1000,678,-1000,301,-356,1000,535,-503,59,-314,1000,1000,1000,-867,1000,-145,116,1000,46,1000,759,1000,1000,1000,869,-807,642,-1000,-348,744,609,148,-1000,1000,-696,-851,-1000,-642,-92,-553,844,599,-647,-480,1000,-7,-1000,80,-145,4,-127,1000,1000,-557,747,98,-512,326,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{703,1000,261,-266,-520,1000,-994,1000,-540,-177,-1000,1000,79,-524,88,-1000,-1000,106,-366,-992,-571,-842,-97,-1000,72,-1000,241,-1000,73,-1000,-578,-1000,948,-647,-741,99,988,-792,-1000,505,676,868,-784,336,1000,-605,-1000,-670,-145,-157,342,1000,1000,-1000,-957,1000,-1000,-1000,178,1000,-1000,-995,-937,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{302,-26,-114,90,-1000,-13,-71,-122,-281,-324,-400,-502,452,671,-411,0,-820,478,844,-1000,-329,494,331,-1000,508,-236,567,-529,-310,-1000,-263,-1000,1000,-693,-182,-96,334,-236,48,-106,-878,1000,-832,1000,101,218,-188,789,479,-262,-95,1000,716,119,60,793,-1000,-1000,-643,27,580,-453,-1000,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-227,339,379,-532,-607,572,-211,-548,598,-466,-686,1000,-1000,-93,143,-400,-119,1000,-907,-8,-589,-218,-728,-315,965,-86,764,-1000,-82,309,-123,-1000,20,-1000,-1000,1000,-89,-1000,-457,-1000,155,320,-1000,503,222,1000,-334,-1000,208,139,475,415,356,-80,-716,991,-319,-90,654,-210,1000,-1000,-592,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-403,920,-601,-303,659,-1000,-108,895,-870,-1000,1000,-119,1000,-1000,-438,930,-1000,1000,-1000,814,399,-1000,1000,-875,764,-431,1000,1000,1000,-976,-1000,642,1000,-124,-619,153,-885,-1000,127,1000,-127,-336,-1000,565,686,574,-844,346,-1000,1000,1000,400,1000,-448,-322,-42,-1000,821,-352,523,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{316,-338,258,-309,864,333,-430,725,545,410,332,-307,-930,163,-383,-94,428,991,-901,416,-887,15,369,434,-281,104,-143,197,736,321,-769,862,-795,395,692,-330,663,-30,-831,-208,752,-902,-874,-971,779,746,394,-143,-439,907,-715,-830,-46,-662,52,-478,617,-101,-272,681,-248,7,343,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-17,1000,-1000,708,1000,-249,697,1000,-239,-1000,-771,-396,906,-1000,-1000,-1000,-577,-1000,1000,1000,1000,1000,79,466,-25,7,-111,-798,-1000,-1000,857,1000,414,-1000,-112,1000,919,-946,547,-1000,-1000,-270,-328,-669,-106,-1000,-1000,-1000,1000,35,-1000,1000,964,266,-1000,-1000,1000,-1000,83,940,1000,-898,123,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.String:LTg2Mw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-461,-863,-881,113,-480,-94,812,-111,-923,496,380,-354,-728,444,-99,129,448,-967,718,2,-389,-822,-426,-417,-909,671,800,-886,790,836,529,990,51,382,-324,-699,-518,923,725,86,-112,481,-745,-247,894,108,563,-718,-214,710,747,21,-324,152,191,-839,-863,729,-501,-711,-844,256,-377,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-499,-314,-881,47,-649,1000,1000,46,538,1000,-649,-1000,246,635,236,1000,225,1000,-1000,-306,-1000,-548,1000,400,-1000,370,-149,656,275,1000,305,-212,-474,404,46,-430,-317,-700,872,716,839,809,217,-731,162,-1000,838,-576,-1000,548,1000,-1000,-1000,601,14,373,390,-236,110,-766,71,-1000,530,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-499,77,-1000,-546,-297,1000,987,207,862,1000,-1000,-1000,669,458,236,1000,568,390,-1000,-231,-1000,-1000,977,508,-1000,530,-465,910,-212,1000,512,-209,-857,495,-431,-307,-1000,-977,1000,1000,1000,1000,217,-1000,-121,-1000,1000,-992,-675,548,1000,-1000,-374,901,299,1000,390,1000,-587,911,62,-1000,667,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-430,292,-667,-734,924,-449,411,888,-404,21,-1,-71,988,-460,-871,261,-280,-65,850,182,693,-749,-292,996,-986,236,-367,-926,-859,-286,-726,364,-624,-812,597,287,56,-293,829,-27,701,-130,-245,-155,727,671,-213,-252,355,836,-770,977,843,-726,-254,-140,273,-686,-871,869,805,527,199,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-835,-140,-1000,-12,110,1000,763,514,1000,599,-1000,-372,456,211,255,1000,-615,1000,-1000,-453,-39,-331,1000,1000,-1000,-206,-940,731,-94,63,575,43,651,154,1000,-1000,-263,-1000,1000,884,1000,741,476,-1000,-652,-983,355,-1000,-1000,548,328,-812,-596,578,-1000,1000,467,-796,-419,-766,-684,-227,508,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-499,1000,-79,-727,210,1000,-585,-369,354,-83,-466,-690,275,207,-1000,140,-756,-103,-1000,-112,1000,99,284,-149,581,-1000,-1000,-239,-1000,-820,384,1000,620,-1000,322,-1000,399,-270,-282,-1000,1000,-728,-171,28,-843,99,-562,-1000,1000,-1000,-455,-117,1000,901,-1000,-1000,-927,-1000,576,911,-1000,-1000,-1000,-517}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{892,-793,1000,885,1000,603,-759,-141,1000,-942,108,-278,-500,-61,-242,820,-465,628,-1000,1000,430,746,90,-184,1000,-389,-1000,860,-265,-89,183,-718,461,700,-173,632,264,-513,-771,60,-267,-637,921,901,-884,132,341,30,-133,-463,734,-836,744,319,-1000,545,1000,-95,-49,205,166,1000,1000,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{397,-1000,-63,392,-1000,1000,1000,1000,111,1000,111,-1000,-455,1000,915,1000,70,1000,-1000,-1000,-298,-397,855,-357,-999,1000,91,1000,1000,1000,577,-1000,142,1000,548,-807,433,-395,477,1000,1000,848,950,324,127,55,1000,10,-1000,638,275,-1000,-1000,178,621,1000,-628,415,110,-1000,-942,-991,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-723,1000,-1000,844,474,1000,923,-211,361,-297,-413,-1000,-571,632,1000,488,-771,929,-575,45,-991,905,163,1000,144,1000,-336,-387,-1000,464,1000,-776,525,481,-979,-747,756,1000,-787,-183,547,-665,591,1000,-238,63,1000,1000,-1000,616,-1000,909,-448,-833,-1000,-255,903,462,1000,-34,-178,-356,734,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:NzQ1", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-333,745,-228,-99,-559,-313,649,-670,965,-51,136,-46,402,203,-497,831,-481,821,-974,269,528,-814,287,154,-693,796,-204,-325,388,-163,130,927,967,-165,370,-316,907,192,-56,745,-814,-66,-862,680,605,-155,-436,-413,-346,311,603,-531,-561,657,-505,250,284,534,-864,-866,-779,-992,879,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{271,567,345,546,-96,134,1000,-637,-668,-522,926,274,1000,400,-776,-286,273,309,-130,49,-367,84,-987,229,-417,-1000,-272,901,652,-719,-857,204,1000,-624,980,-886,208,-847,-633,-420,-1000,-202,7,935,1000,994,9,-418,-990,-126,-164,-923,-443,731,-409,596,-130,-1000,337,594,-1000,288,641,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{335,-1000,-1000,-608,316,-214,394,948,-103,1000,-460,1000,-293,861,-1000,-1000,-390,-59,1000,-80,704,-1000,1000,-1000,1000,-366,-1000,1000,1000,87,-1000,580,-1000,-1000,-919,679,-123,-877,1000,-480,453,-469,934,-1000,-1000,-917,-566,-948,-1000,1000,1000,1000,-383,-1000,1000,-1000,870,-583,-622,810,104,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-455,759,345,445,-99,234,1000,-637,-20,-756,-582,-527,895,400,204,588,-276,309,657,-656,-367,1000,-783,591,-810,-1000,-232,901,607,-959,-756,204,1000,45,1000,-787,-265,-191,-1000,-420,-242,-891,-442,1000,1000,1000,189,-825,-191,-22,-164,-1000,-56,1000,-1000,867,-270,-883,-83,594,1000,-1000,641,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-723,442,-1000,794,994,446,1000,-211,843,-186,-851,-1000,-1000,-860,1000,1000,-1000,1000,606,-439,-991,930,163,1000,198,1000,-422,-660,-1000,464,1000,-749,464,542,-12,-797,33,1000,-1000,1000,547,-665,-190,859,1000,206,732,1000,499,-46,-1000,602,-210,1000,-1000,671,-1000,892,-55,-34,833,-1000,180,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-179,551,-341,582,230,847,846,-420,-379,-352,457,-38,-437,1000,-16,-181,-148,475,-8,-246,-686,243,-146,-1000,-157,20,44,378,400,-356,90,-309,-429,-173,-454,32,1000,95,-417,-1000,115,-901,655,183,-238,335,646,-102,-1000,616,225,1000,-260,-965,-762,-1000,1000,-465,305,273,-1000,275,734,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{335,-1000,-490,102,-1000,-297,1000,-400,-1000,-174,452,-115,6,508,277,-1000,-55,1000,-1000,593,-362,-125,-1000,868,226,-789,-513,329,-383,-159,-80,285,1000,564,-467,-1000,452,-173,1000,488,-474,451,48,368,-1000,157,-566,391,-1000,137,-1000,-205,-17,-491,-603,129,770,-457,1000,366,104,343,544,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Calendar,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-641,1000,345,838,125,550,1000,-211,489,-342,-815,-1000,-1000,-1000,1000,1000,-1000,1000,-718,-150,-1000,1000,-705,1000,-810,976,-372,-1000,-1000,-959,1000,204,1000,1000,-12,-1000,77,1000,-1000,1000,-242,-527,-190,1000,1000,428,1000,1000,499,-255,-1000,602,-210,1000,-1000,671,-700,1000,431,-318,583,-1000,1000,892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{171,654,-104,15,587,-548,1000,915,-1000,611,985,-252,-52,1000,1000,1000,377,394,573,-849,-910,-376,-1000,199,-1000,-132,1000,6,1000,1,-1000,1000,-1000,1000,-811,931,742,1000,-100,829,-1000,-662,-1000,242,-3,-410,-747,130,955,1000,1000,357,-138,-1000,1000,-823,-923,346,338,1000,-1000,176,-231,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,-627,373,-1000,660,936,933,573,-811,505,272,-1000,-896,656,469,285,-1000,-1000,-1000,-673,-325,-1000,-200,-991,-792,371,321,-723,-164,760,342,537,-1000,580,-724,820,-1000,209,86,-305,134,1000,-216,1000,1000,653,-1000,1000,-967,478,1000,-486,1000,909,681,-1000,973,-808,708,-175,-319,-1000,-1000,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-248,286,379,-589,-43,-179,-447,-907,-605,-247,671,984,396,333,-533,-690,-539,-187,-533,-7,782,129,1000,-947,-271,58,-1000,-133,-1000,764,-342,-5,263,145,-787,631,-469,-231,344,-89,655,808,2,725,-412,-410,-682,-601,26,-13,-751,-882,890,175,-1000,299,110,103,570,102,389,-1000,-1000,715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-278,623,985,17,390,543,-832,-1000,675,-1000,525,18,-1000,1000,355,964,-1000,-65,-1000,-1,-1000,-1000,-1000,-836,1000,-642,226,496,369,21,-1000,-1000,1000,-1000,1000,1000,-796,-541,662,-1000,838,-187,1000,134,-87,-6,-557,1000,-52,1,-266,669,819,1000,-874,925,-252,-1000,-1000,1000,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:MTE4MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-486,377,-118,1000,-23,-949,-112,-87,-539,784,-43,-1000,-178,-1000,944,-607,1000,-1000,-43,23,-494,367,90,1000,-586,179,-929,1000,-905,1000,208,1000,-252,98,-1000,152,720,-197,802,-100,1000,1000,-634,-856,703,-662,652,-499,-3,-551,244,314,-441,-933,-859,596,-985,1000,270,468,-1000,-392,393,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,626,-717,1000,79,-44,131,-344,-1000,-377,378,-1000,1000,-922,567,-1000,182,-1000,672,259,83,-753,1000,435,1000,-1000,-495,1000,-873,1000,408,-26,-1000,-576,722,-90,-870,-1000,-21,-1000,1000,1000,471,340,738,8,-607,231,-143,-1000,1000,1000,-1000,-1000,-937,-1000,-1000,1000,472,-344,-1000,770,514,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-470,717,1000,505,615,-993,-69,1000,-181,-830,-142,-184,979,703,1000,855,-469,-266,-1000,24,691,824,-185,968,-626,465,-424,389,-875,1000,446,482,179,-1000,-1000,-583,790,-1000,-99,849,-237,502,409,-755,1000,-378,682,-271,558,348,315,824,-953,-650,-261,1000,-787,571,-916,1000,-1000,459,1000,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,173,1000,962,136,-313,-1000,-1000,-790,-985,615,-734,758,1000,515,1000,42,828,-439,-410,454,1000,172,-120,371,276,882,-950,-307,743,172,-1000,9,102,1000,310,-585,-508,-1000,-1000,515,-763,453,849,-777,1000,-16,1000,623,238,744,113,204,-873,-410,97,145,-338,-965,-58,-299,1000,745,279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-65,-818,223,1000,-120,-462,-345,20,899,-918,1000,-165,583,-766,268,450,-17,-398,47,-683,1000,663,-376,936,-911,20,-482,-1000,-971,-781,610,77,321,455,801,490,170,-934,-107,891,670,-314,21,-1000,65,598,922,74,693,438,228,400,-70,200,-276,-726,682,579,12,-333,906,-1000,1000,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{1000,-659,-599,-955,-84,1000,1000,1000,117,-1000,1000,-1000,-903,184,1000,376,-142,1000,640,-643,-109,-889,1000,445,-1000,-1000,-420,-1000,-1000,-1000,686,-337,1000,1000,1000,-1000,1000,-1000,1000,1000,-234,-597,-995,103,-1000,-1000,1000,-1000,403,649,-1000,-842,-365,1000,-494,-1000,1000,-252,1000,-1000,-681,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-1000,-226,1000,1000,-856,-1000,1000,-480,-371,1000,-750,1000,58,64,-720,1000,930,-1000,1000,1000,925,-918,-1000,401,138,261,-1000,1000,682,1000,-1000,89,506,77,-693,1000,970,1000,-154,-1000,-720,237,1000,-563,-747,-155,-633,1000,1000,1000,368,-495,885,286,-929,-29,-626,181,-315,-935,1000,400,-687,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-424,562,708,309,990,6,-1,964,-262,-400,-323,556,-454,-980,715,457,492,-433,47,-330,-540,231,784,834,-998,32,134,-875,99,-453,557,-662,-969,482,-22,-875,930,202,-288,193,412,149,202,109,-816,-712,579,-705,-539,-127,650,781,645,-877,972,314,811,727,356,-431,253,458,334,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-416,686,946,882,-289,-962,-486,-255,-638,-917,3,52,-193,-476,-989,-721,-102,-882,-493,-659,-373,-723,985,207,-984,-829,886,-96,-630,-987,871,-535,-366,184,767,-918,-48,-146,915,544,-628,866,-3,905,-903,749,440,-781,677,976,991,210,107,810,175,-241,-86,-550,995,-781,15,-457,464,739}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{1000,62,601,-329,83,-230,191,1000,-217,-1000,247,-969,-1000,-549,1000,921,504,-45,319,-853,422,32,260,880,-812,-1000,-754,-1000,-1000,-404,196,129,-1000,591,463,-947,216,-223,400,556,649,359,888,-241,-266,-1000,1000,-691,-572,197,1000,1000,13,-202,161,-543,1000,951,623,-716,-482,-1000,1000,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{392,62,557,-849,735,-250,-344,616,543,26,247,691,-440,125,-594,921,497,-639,-162,455,437,884,-31,-360,-541,178,1000,363,-447,-641,196,1000,-107,-859,-408,945,6,-417,442,-689,-344,396,-208,-465,-178,705,-67,116,-530,568,513,-616,-71,-493,-334,-82,-1000,802,90,71,-909,-386,-29,43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-629,511,-385,1000,487,-1000,1000,-287,-110,-639,-946,745,-681,1000,367,-230,1000,1000,-867,-726,-22,977,-938,1000,-1000,-650,-1000,-219,325,280,-749,1000,-2,1000,-1000,599,-110,-25,1000,876,-1000,-22,-961,-284,909,567,-355,472,-153,-403,1000,910,-374,-1000,-1000,-751,232,-269,-582,1000,138,1000,1000,539}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.String:Mw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{795,-258,-811,660,801,332,172,-1000,601,1000,-687,213,-15,-458,351,-27,956,865,-114,-729,901,1000,-934,821,300,-699,885,82,-978,146,-150,563,-1000,1000,-981,232,-1000,560,-111,-217,-304,-424,816,-578,1000,1000,-1000,251,-489,827,598,1000,-263,90,-530,-973,144,-199,476,-92,130,700,-279,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-629,511,553,-200,342,-338,-532,-287,1000,-378,-727,58,-1000,-464,367,529,-129,1000,-273,-517,285,675,303,1000,-3,87,-1000,-346,325,-103,-936,-332,-439,1000,-1000,-329,-85,-120,-289,-270,-1000,-684,-152,-28,358,1000,-14,1000,-315,-403,-581,286,276,701,12,-1000,-53,653,376,-480,-1000,-713,748,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.String:Mw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-534,-258,-811,-197,801,-510,-876,621,338,-572,-596,929,-66,589,670,-27,956,977,-538,-265,-795,-84,-506,106,-769,-678,-619,771,-978,-553,-133,165,537,745,56,232,424,558,-111,-217,398,-956,-852,-739,209,-559,-335,-564,-62,-894,-922,150,401,56,-530,609,884,749,543,-92,114,700,588,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-261,541,-870,1000,382,-378,1000,-32,-881,-289,-23,263,87,716,-261,589,771,1000,-1000,502,-573,881,-1000,1000,-559,-824,-569,-663,-766,135,842,-86,-544,865,-807,277,-711,-352,1000,361,-532,259,-719,-706,683,-111,-860,472,-201,674,1000,1000,-960,-1000,-1000,66,377,-370,-1000,804,432,1000,-31,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(java.util.Date,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-381,383,-956,-1000,113,-1000,1000,-159,1000,-809,-1000,1000,-1000,621,672,-1000,578,293,-535,-1000,-193,-295,-1000,544,-576,-479,53,513,-1000,-1000,-1000,537,1000,-154,1000,350,599,874,-1000,1000,-284,149,-578,-132,1000,889,1000,-615,675,-1000,-1000,-867,-42,1000,1000,-581,-500,-458,1000,400,-478,522,1000,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{1000,-230,-177,1000,-1000,-755,816,-337,874,1000,1000,93,1000,1000,100,-668,-1000,-1000,1000,-1000,-829,307,1000,1000,-924,1000,-1000,370,-1000,-82,1000,1000,-1000,-830,-1000,-912,-972,-880,516,1000,-227,-757,1000,-1000,-1000,1000,-1000,-1000,295,-1000,-1000,1000,1000,-617,1000,1000,-1000,1000,1000,-521,1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.String:MzcyMQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{-186,1000,681,372,1000,565,-2,737,928,1000,-282,-744,-725,-393,224,54,-881,70,-472,-523,-1000,-450,522,-1000,601,-846,1000,-1000,1000,884,28,-95,-1000,138,400,-46,-284,340,1000,-94,16,309,205,-170,953,1000,-569,-302,353,1000,1000,-1000,273,-1000,-773,-173,29,-1000,-321,-1000,479,855,1000,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{418,1000,926,563,-24,744,916,1000,1000,-1000,-68,-977,173,-940,939,-1000,-1000,-291,-400,-484,-180,-434,1000,872,218,460,-114,328,622,-205,260,1000,-1000,644,208,268,-961,-1000,-243,-1000,62,-665,-678,-389,946,-240,967,-132,-400,598,-349,-775,-1000,1000,1000,770,-400,146,-55,665,-1000,-282,859,-626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{1000,-832,-177,1000,-540,-191,-1000,-865,-225,1000,-1000,-558,1000,344,-331,838,262,-1000,-1000,302,-757,-1000,-559,881,1000,1000,180,848,-197,-1000,1000,-357,-1000,-133,-1000,652,-610,1000,800,1000,-1000,-1000,1000,-148,-1000,-1000,-1000,-972,1000,-1000,-1000,-463,-1000,-1000,835,1000,-1000,1000,-1000,-1000,-1000,1,69,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String):java.lang.String",
            new int[]{1000,-673,382,1000,-1000,633,-1000,1000,-100,224,-1000,-1000,305,296,-93,613,-363,-1000,-1000,-1000,-643,-1000,496,1000,1000,-1000,-129,1000,-289,-1000,-115,213,-1000,399,-725,1000,-379,1000,1000,1000,-148,-825,-1000,-1000,-1000,-1000,-388,-650,-1000,-680,-551,305,-963,-499,1000,-1000,204,-860,-1000,-1000,-1000,372,1000,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.String:LTEwMDAyNQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{20,363,137,-1000,765,-381,-26,-938,-1000,-33,1000,-185,-1000,-646,863,382,-779,-1000,-650,202,523,-1000,-249,-850,-133,-949,851,-1000,-400,-1000,116,1000,737,-265,1000,-404,569,-1000,-708,351,1000,-61,1000,-196,149,742,-1000,-4,-1000,-1000,-692,-474,-43,-658,1000,-1000,257,-1000,34,-1000,-206,-264,449,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,5,29,-106,-498,865,-23,320,965,-553,-1000,-215,-673,799,-1000,-1000,-1000,571,589,35,-1000,-440,1000,390,-702,97,453,1000,-1000,-678,-123,472,211,-33,-1000,879,800,272,-105,-1000,1000,-496,-159,-1000,1000,-195,-341,-129,1000,577,457,1000,-76,-42,-1000,325,-1000,-23,-1000,-834,578,-653,-1000,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.String:UE0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{530,-129,-436,-494,647,958,121,-684,965,-104,-909,412,-960,-775,685,123,-856,-942,286,821,-163,-509,98,399,26,-954,-455,-241,195,-401,-68,109,303,748,248,-804,244,-817,888,974,656,-779,529,-416,-567,-581,457,64,293,907,208,722,432,-52,570,-284,455,90,689,-918,-462,255,-674,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{875,41,802,326,-645,-85,359,-691,-785,45,235,-106,87,-767,-401,539,-469,-223,-272,976,-132,383,-38,-154,121,-631,471,884,-626,-53,441,-685,-390,892,637,-575,-473,-512,234,633,-274,-723,-775,819,192,848,-187,140,-958,-332,737,118,-861,799,-330,-598,379,203,527,-731,181,339,834,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,1000,-273,-756,1000,989,1000,-301,128,149,661,669,-522,1000,197,950,-1000,-899,-920,1000,-335,-932,-799,-1000,-1000,-1000,1000,-796,1000,-922,887,1000,-464,625,1000,-1000,1000,694,-1000,242,24,1000,77,5,-933,815,532,-1000,-1000,-1000,-92,1000,1000,-214,575,-1000,1000,-1000,-309,-1000,1000,467,-1000,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-40,-1000,541,-15,-994,-429,84,71,-806,516,1000,-33,-1000,-1000,221,243,-709,-1000,362,-1000,1000,-1000,107,1000,1000,983,344,138,-236,610,847,-1000,928,-961,625,-275,-701,-1000,566,128,30,-1000,-1000,876,832,-1000,1000,-232,-1000,725,-927,-856,-720,-854,331,-1000,-424,1000,87,-256,-487,707,1000,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,46,431,500,-189,291,-181,-184,-1000,465,752,-162,-1000,390,76,1000,-1000,-982,-677,-7,482,-971,-701,1000,-463,741,-589,-866,67,-1000,1000,-1000,639,-104,-659,-36,6,-172,434,680,283,399,-708,442,-112,-371,-478,-40,217,637,-540,-149,-148,-17,-79,-773,-54,1000,88,557,607,1000,-405,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.String:NzgxMzM2", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{1000,-618,89,781,-1000,201,47,-166,80,-535,321,631,-91,79,521,1000,-1000,317,-1000,1000,558,511,-1000,-290,899,752,-41,278,676,400,262,219,708,-1000,189,-385,875,-254,1000,-419,-416,-697,-224,-534,-1000,363,1000,-1000,388,-400,513,-776,-267,339,284,1000,-314,1000,1000,-796,274,-854,-997,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{333,-787,-625,631,-236,-433,-167,-1000,1000,-7,670,1000,-1000,-50,904,434,-460,790,-662,-36,141,156,431,-963,-1000,845,386,710,-345,-119,432,344,395,-1000,1000,-437,70,-1000,1000,53,671,-7,-577,-71,-946,121,-1000,-1000,730,-489,14,-178,324,-110,235,999,-458,-1000,-955,339,408,-856,-339,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{-222,-1000,415,1000,573,869,784,-1000,1000,-810,636,1000,363,1000,1000,1000,-1000,-502,-725,1000,1000,1000,-1000,-787,992,986,577,1000,1000,1000,-1000,378,531,-603,-1000,-185,-1000,448,815,-1000,422,-1000,-914,-1000,-878,1000,817,-1000,-4,477,892,869,666,1000,1000,1000,1000,-656,-1000,1000,1000,-576,733,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone):java.lang.String",
            new int[]{1000,869,479,240,32,-638,105,-488,935,1000,1000,1000,-1000,-989,1000,-297,212,1000,605,-1000,230,1000,-414,-276,44,1000,-1000,785,-365,605,-534,-1000,1000,-1000,-459,-1000,294,-1000,557,-1000,1000,459,1000,-363,-1000,1000,780,-53,1000,1000,1000,-1000,-670,1000,-317,1000,-1000,-1000,-871,473,777,-507,-21,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{576,1000,-577,31,395,360,294,537,-1000,-510,-697,-4,-591,952,-256,-126,1000,-255,-609,864,-624,-467,267,-1000,50,-34,-1000,122,-511,-160,1000,-1000,821,1000,-393,311,-811,1000,801,-543,523,789,-1000,143,-86,-255,-38,-1000,1000,-802,348,-896,320,-1000,337,342,1000,939,-1000,605,-41,-253,206,683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{1000,1000,-483,286,49,690,-1000,-776,-1000,-487,-1000,-309,-1000,568,-933,-181,-63,250,-1000,979,-1000,-695,4,-1000,-691,813,-1000,1000,-1000,1000,1000,298,393,738,-1000,421,-83,1000,1000,-1000,1000,1000,-674,1000,-849,-670,347,-1000,862,-1000,-163,-659,1000,-1000,774,324,231,678,-968,512,-1000,-692,244,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{-377,1000,-177,-113,477,840,981,753,1000,-936,-776,-889,-656,503,-1000,-45,761,1000,-1000,36,1000,-304,654,-560,220,1000,-637,1000,1000,-775,224,-1000,1000,-36,-1000,709,-238,493,664,60,-651,545,666,784,-1000,-875,691,-237,844,-912,-310,664,1000,-670,900,206,1000,280,-1000,-789,-1000,-1000,113,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "format(long,java.lang.String,java.util.TimeZone,java.util.Locale):java.lang.String",
            new int[]{1000,1000,94,-619,1000,421,-977,1000,-1000,-1000,71,-1000,-1000,1000,-448,-88,1000,825,-1000,-1000,-1000,-82,1000,-932,1000,413,-1000,1000,1000,531,1000,-1000,1000,524,-775,-1000,-1000,1000,1000,-291,1000,822,-1000,1000,-1000,206,70,-1000,716,-1000,45,105,882,-902,1000,1000,1000,-1000,-1000,-863,-874,-1000,-1000,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-1000,958,389,805,-186,-320,-43,1000,-1000,581,-1000,-75,-1000,910,-181,-731,1000,-723,-1000,153,-1000,435,-808,-1000,-541,25,-628,-1000,715,-52,820,-916,-1000,559,-1000,862,335,875,638,671,-638,-181,-978,-1000,1000,-730,1000,-802,1000,991,98,-515,-1000,-1000,1000,-21,-388,387,-1000,979,961,-433,796,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.String:ODE0MjA=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-712,-903,-814,-1000,747,1000,-236,1000,1000,-1000,1000,-68,-1000,145,-1000,-1000,-53,1000,251,-1000,80,-1000,-1000,1000,-1000,-1000,-1000,516,1000,-297,51,-612,891,-1000,451,950,1000,-821,-397,-329,-111,-107,-791,1000,171,-167,-710,116,1000,588,-574,-37,549,1000,305,-239,891,731,153,623,1000,463,-634,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.String:MzAx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-1000,-519,30,1000,-119,1000,-1000,1000,127,-691,-57,1000,-1000,-461,-1000,-1000,465,-343,-1000,-731,245,435,-1000,240,593,-1000,-628,-749,733,-52,1000,-916,-407,-1000,905,-846,203,-1000,1000,671,1000,-374,-978,866,1000,-267,-462,-586,305,795,-625,1000,684,1000,-782,-1000,-634,-792,153,-116,256,-1000,153,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-654,-275,-645,-177,194,138,205,855,-851,-69,998,-94,193,685,240,-868,590,-547,-388,540,-691,742,-742,-354,-222,-472,105,59,709,-354,869,538,-811,-389,-581,587,363,-323,814,167,-405,-220,-587,-243,232,-91,256,628,817,393,-865,-46,-63,-383,-334,-814,735,755,-957,958,603,-736,153,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{490,-740,322,-260,-959,-553,-947,908,-510,763,951,209,-257,-112,-1000,611,-216,534,1000,-535,263,-405,-1000,-238,816,1000,-535,900,1000,-288,633,-1000,1000,1000,378,1000,542,-685,-1000,1000,1000,1000,-1000,999,27,554,673,938,107,439,587,263,-498,18,970,319,-778,546,-203,-174,1000,-114,645,-866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-409,733,-1000,-778,439,1000,-974,291,214,-573,1000,279,-619,1000,-1000,-816,603,1000,-785,-853,313,-1000,-835,897,-1000,-60,299,1000,279,-1000,555,-78,772,-997,451,1000,621,-821,-221,-577,471,228,-706,821,-584,-196,-709,335,1000,632,-1000,-815,93,810,657,-631,891,266,-271,863,944,-413,269,-229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,637,-1000,509,121,-267,411,-1000,-644,-658,1000,866,-772,631,78,123,-445,535,-291,301,-183,-460,-70,-895,14,-79,1000,-78,-29,-563,440,126,-439,-874,-1000,439,-405,-391,-571,-73,615,850,-970,-569,-1000,183,985,-119,1000,128,443,-1000,-933,-612,1000,-264,1000,1000,-1000,649,300,1000,-781,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{-1000,-718,-438,518,-614,12,-1000,1000,-460,762,1000,-624,589,463,1000,-1000,1000,-434,-832,262,-97,1000,-1000,228,-486,132,524,66,1000,500,1000,-1000,-1000,-600,-175,1000,-1000,-1000,1000,1000,754,-1000,-966,-1000,276,-670,65,684,1000,841,-94,260,160,-281,-239,-1000,-209,678,-366,637,1000,-1000,619,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.String:LTI0MTI3MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{1000,697,-241,609,609,-1000,789,-1000,-1000,207,806,-533,-861,1000,1000,415,-1000,1000,1000,305,-956,-1000,-896,-1000,163,685,1000,270,195,-459,685,498,-817,672,-1000,1000,390,993,-1000,248,714,962,-739,-1000,-1000,367,1000,-214,1000,38,394,-856,-1000,-1000,1000,693,1000,1000,-1000,1000,750,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{216,301,-517,783,610,252,-576,-222,-644,-69,117,-979,-581,989,-523,-795,499,72,-681,-125,-295,2,-808,-217,-351,-575,928,-78,721,-171,546,225,-421,397,-522,439,1000,723,527,45,1000,59,-899,-298,-1000,-252,-85,-764,1000,713,-92,-627,-355,-243,661,-784,246,403,-327,868,1000,-701,205,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String):java.lang.String",
            new int[]{234,463,-1000,447,193,-114,-562,-222,-17,311,-143,-533,-861,1000,-290,-795,1000,263,-739,304,-945,239,-1000,-814,-1000,-792,1000,240,715,360,183,225,-560,559,-57,1000,1000,-186,414,-64,1000,-790,-1000,-1000,-1000,-302,-343,-371,1000,633,251,-616,-986,-544,1000,-898,673,1000,-693,1000,1000,-25,-67,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.String:LS03NDU5Nw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,153,745,-385,-747,253,1000,759,805,-925,651,-545,936,-39,1000,517,-1000,1000,814,213,-658,794,-1000,-104,366,-420,493,-535,-1000,-578,183,-1000,-414,-501,948,-617,-745,710,439,205,919,-546,-619,618,-620,-948,-466,-337,1000,464,-1000,440,-705,910,50,742,553,773,-792,-1000,1000,-537,856,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,-177,1000,-688,516,117,141,853,-193,-597,-910,-457,719,-32,-293,-170,-540,150,-52,-114,1000,607,302,-1000,670,490,1000,-1000,-898,-1000,-143,561,-934,-260,1000,1000,857,-945,662,770,-576,-845,-914,-754,-437,-485,-1000,-1000,-69,154,459,-1000,66,-1000,-44,600,915,4,-664,-142,337,-1000,-688,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-859,734,1000,-990,-779,1000,276,1000,-1000,424,-230,-1000,403,1000,190,-1000,908,1000,-580,-253,-1000,-519,-1000,-1000,1000,116,1000,-1000,-1000,-359,-242,-188,-1000,-1000,1000,260,1000,294,1000,625,190,-1000,-854,1000,-1000,-1000,-1000,-1000,470,474,29,212,-620,-463,-1000,1000,361,570,1000,-944,869,-1000,-136,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.String:KzEwMDAx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,297,1000,706,-911,990,-1000,1000,-1000,-2,-1000,-1000,1000,945,467,1000,1000,1000,-763,-1000,-1000,-1000,-1000,-1000,623,-309,1000,-1000,-1000,-1000,106,394,-1000,-1000,81,-745,1000,-1000,1000,-888,-1000,-1000,92,1000,-1000,-747,-959,203,-55,-228,-299,-61,-1000,-770,-1000,-524,1000,1000,1000,-686,553,-834,-1000,-758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,-18,1000,-114,-134,1000,-484,656,517,1000,130,-350,1000,-484,1000,236,-1000,1000,871,170,-1000,-1000,-1000,-376,94,-125,276,-336,-366,-268,1000,-861,-163,-288,173,-552,-510,302,199,-499,496,-663,-456,455,-457,-926,-237,-54,751,-532,-1000,1000,-624,240,-383,911,361,708,249,-853,1000,-696,527,-994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{945,812,-429,72,336,1000,-327,38,-1000,-65,-558,754,442,-93,-1000,-514,74,756,26,-391,-1000,-194,-698,-288,536,-1000,479,-178,-36,-1000,1000,401,854,-165,531,-265,-206,-1000,-488,289,-194,-218,869,624,145,54,1000,-45,-216,416,201,-15,1000,104,65,561,-581,515,-307,-1000,-409,1000,980,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.String:IA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{785,-670,-253,888,510,-559,863,220,519,632,-75,906,-407,-257,249,832,311,-354,-552,942,344,729,-703,-535,-645,-299,-995,805,-976,566,-906,-24,898,-783,220,-623,469,797,49,650,180,-323,-386,376,901,-256,447,464,818,-501,-767,194,662,445,-288,-871,720,188,154,325,192,-252,820,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.String:LS0xMDAwMTY3", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-199,9,1000,-993,9,1000,-782,1000,-1000,916,-275,-1000,799,1000,441,-1000,57,1000,135,-171,-937,-1000,-731,-1000,1000,423,1000,-1000,-1000,-870,115,-488,-1000,-1000,1000,-171,827,-465,1000,543,79,-1000,-1000,1000,-1000,-1000,-1000,-614,580,234,-270,342,-895,118,-1000,1000,1000,676,1000,-615,653,-844,-60,-838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(java.util.Date,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{444,-146,-462,-844,-216,-218,678,-936,891,-149,27,-227,499,58,346,216,-401,-218,-721,182,964,-1000,732,110,-493,854,-781,-539,401,-624,-400,55,670,-751,523,931,-143,-362,-394,88,-436,509,-940,-1000,138,366,-876,-182,-544,-192,37,-849,596,-238,-393,329,299,144,-529,-198,119,289,-324,871}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.String:CTE2Xw==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{848,201,-401,-1000,380,-172,779,-477,-1000,1000,-476,400,-878,-1000,-719,-531,155,-23,692,176,-302,400,-1000,-209,-44,659,-1000,1000,930,762,903,362,-537,-583,-400,553,709,-85,212,-580,-589,-400,427,-133,-269,-473,-435,1000,-1000,-463,1000,-1000,-940,-764,-737,-997,458,-619,608,17,-301,-376,400,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-669,527,1000,-986,755,-173,1000,-675,520,-343,784,257,-1000,650,-1000,763,788,98,1000,-248,-1000,622,-674,916,-78,-648,971,-223,-152,1000,-14,1000,1000,-1000,-1000,-497,-544,-534,-1000,-172,1000,-439,730,1000,-897,-325,1000,441,-641,1000,-941,-508,1000,-1000,-81,168,-821,-841,5,-534,-674,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwMDBfOA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{158,1000,877,714,380,-172,487,-1000,931,233,1000,400,23,-642,737,-1000,-513,-1000,180,176,1000,-1000,-420,1000,-1000,908,-962,1000,309,-620,-950,-1000,-1000,-1000,1000,236,1000,1000,-1000,1000,221,1000,239,822,1000,-468,926,-1000,-28,1000,-709,1000,1000,-764,1000,-997,458,318,1000,-53,666,-16,-1000,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.String:OA==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-232,-34,478,118,451,332,514,-592,189,-659,822,139,-1000,1000,-1000,0,166,-33,-1000,-148,1000,-49,-477,376,54,-759,-215,-295,473,1000,99,-1000,-409,-589,-104,-18,-306,455,-587,-1000,400,-337,-423,1000,-340,600,114,-328,-408,838,973,409,1000,-409,-490,871,-1000,435,1000,971,-449,391,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.String:KzEwMDAx", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,646,-183,-1000,-918,313,1000,-1000,-572,926,-868,308,-1000,-895,692,1000,879,-440,820,376,-9,188,-125,-384,1000,-44,-862,375,827,538,1000,-231,-1000,-853,631,461,1000,110,770,-406,-236,-132,636,-350,-770,-1000,-298,556,-240,-344,1000,-966,-1000,-1000,995,-506,1000,-133,-37,105,-65,-1000,146,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-1000,-226,477,-1000,784,-668,1000,-1000,585,-366,406,1000,-561,-569,-1000,743,659,73,-603,-244,70,622,-901,733,392,-980,1000,-260,-458,768,429,1000,1000,-1000,-1000,-31,-731,-534,-1000,-297,1000,-365,914,-114,-912,-758,1000,113,-734,1000,-587,-356,1000,-1000,-108,136,-902,-518,333,-120,-321,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.String:KzEzODIyOQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{-1000,-1000,265,138,-918,-39,374,-191,592,154,636,-1000,1000,1000,-625,1000,362,788,736,-495,908,545,209,686,343,-298,1000,-159,278,-152,-1000,-24,-294,-532,1000,192,-278,850,71,1000,-70,-68,199,-108,427,-1000,627,-646,1000,-1000,-1000,1000,1000,-33,191,145,657,-821,-1000,523,-708,-242,281,-292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,-1000,-948,-517,931,393,-784,938,771,756,-434,1000,-1000,-573,338,-681,-466,251,-1000,1000,787,-1000,243,151,-105,-554,-77,634,116,728,1000,161,1000,1000,116,-470,-949,-1000,-1000,460,-495,334,-44,79,-630,569,675,559,-1000,-382,1000,586,-759,237,-150,-365,437,-1000,219,741,-513,29,-253,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{1000,425,574,1000,1000,65,1000,-1000,1000,609,752,784,-1000,839,916,328,-778,818,-532,-1000,1000,-937,537,1000,1000,-1000,1000,-956,-223,-41,729,-1000,-635,143,483,1000,139,-544,166,916,-686,-1000,-371,488,-9,1000,238,-1000,-310,1000,-1000,1000,1000,-1000,910,1000,878,-1000,-90,1000,156,22,-686,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{91,643,-97,-119,1000,535,-716,711,1000,46,-1000,877,1000,-371,237,941,1000,-594,-3,-602,1000,-754,-176,-704,988,-472,-246,68,-435,22,1000,271,-382,274,-198,-126,197,-931,-356,1000,547,843,-901,1000,-1000,-926,954,187,592,-1000,694,-159,553,-1000,628,-44,615,-943,-654,-507,927,433,28,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String):java.lang.String",
            new int[]{952,1000,-371,-1000,-689,24,996,-1000,-676,1000,-414,558,-1000,-1000,938,565,-189,-470,26,1000,-357,523,-1000,434,328,-129,-271,-252,1000,1000,1000,-204,400,-1000,-38,1000,1000,-579,721,-523,-867,-1000,636,-934,-1000,-521,-453,1000,-906,-121,1000,-1000,-567,-1000,-352,-649,841,-749,434,741,787,-1000,-514,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.String:MUdNVDAwNjY2XzgxNg==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,421,845,-633,1000,761,-820,-959,-167,-380,1000,1000,1000,-1000,8,671,1000,-1000,1000,724,698,300,883,-1000,1000,-538,-776,-629,1000,-475,-201,1000,-1000,877,311,1000,434,-1000,-648,-1000,1000,1000,473,-865,612,-1000,-1000,536,1000,158,-956,-590,666,-487,-215,-1000,108,-710,621,-1000,-157,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.String:NjEyNDA2MDY2MTEw", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,421,845,-489,152,761,1000,-709,588,472,1000,213,1000,1000,-798,1,852,530,1000,724,903,-524,-919,481,-969,677,81,-143,709,-56,-399,1000,-1000,-243,311,-591,434,-187,-1000,-79,-1000,282,-1000,-480,132,200,681,-1000,-1000,158,-956,-590,1000,-424,-705,-1000,-1000,1000,1000,-677,-157,1000,1000,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,919,879,994,-1000,41,1000,190,-854,-1000,-232,-1000,-1000,707,-1000,1000,-1000,-366,-1000,490,-477,-1000,-951,1000,-1000,1000,469,-769,-273,179,-1000,719,1000,-1000,-517,-1000,-511,1000,-1000,-913,-624,-1000,-1000,686,-1000,1000,471,-764,-1000,-388,920,-504,-98,1000,-1000,1000,320,1000,-1000,292,-898,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.String:MTAwMDM0MQ==", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-659,400,25,-1000,1000,171,814,-204,1000,-873,-1000,-1000,-1000,1000,-1000,968,-1000,1000,-1000,1000,-1000,590,838,-1000,-1000,89,1000,473,-989,1000,-412,-400,-439,-1000,-685,-922,1000,975,1000,-1000,1000,-1000,-1000,-1000,1000,1000,321,-168,1000,1000,1000,-714,704,-472,1000,78,-1000,-1000,1000,-852,-1000,210,-439,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.String:QU0=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{709,-554,316,-624,618,-165,-906,6,90,107,645,657,790,-479,441,354,778,-502,880,-594,488,405,702,-677,964,-373,260,-124,779,-884,431,440,-791,81,265,612,-734,-784,-386,-768,914,914,-68,-595,468,-720,-816,-95,522,465,-616,407,47,-209,-327,-588,289,-501,30,-548,975,-489,722,152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.String:NTE2", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{762,594,-531,-604,215,952,-421,-974,580,21,-1000,-228,20,743,186,-714,636,1000,234,1000,-996,590,623,-332,268,-1000,1000,-493,196,1000,-502,871,-1000,1000,-685,1000,1000,975,871,360,750,823,-1000,32,103,-335,-521,1000,87,-620,1000,299,110,168,749,552,-1000,-89,1000,-438,-785,-149,-400,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.String:MjE=", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{-1000,676,633,2,-1000,91,1000,-21,-854,-598,359,-338,-606,320,-1000,824,-161,-203,-853,-346,-210,-1000,-461,1000,-1000,1000,-60,41,606,-285,57,134,836,-858,-287,-883,263,1000,-889,1000,-1000,-611,-1000,1000,-856,171,626,-554,-1000,-871,-60,-638,-699,1000,-932,842,153,1000,-996,-247,-1000,486,-911,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.DateFormatUtils", "", "formatUTC(long,java.lang.String,java.util.Locale):java.lang.String",
            new int[]{1000,980,975,-803,234,749,-1000,-676,-1000,698,1000,1000,1000,-1000,-298,44,1000,-1000,1000,937,1000,133,199,14,1000,289,-1000,-1000,1000,-1000,328,1000,-1000,1000,660,1000,91,-472,-1000,-411,467,1000,339,-239,1000,-1000,-1000,146,520,-895,-1000,-414,18,-903,800,-1000,1000,-227,1000,-813,632,-642,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "equals(java.lang.Object):boolean",
            new int[]{894,-775,-914,122,-151,290,-559,-641,-885,-53,265,-519,-992,-955,-339,-266,522,-142,-509,849,670,603,220,822,-122,-947,806,691,308,-910,-385,864,5,854,186,410,-187,558,463,-819,191,20,105,-357,-775,491,-587,-172,-570,445,-490,968,-760,664,475,-923,-492,182,353,948,-249,-597,-845,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "equals(java.lang.Object):boolean",
            new int[]{-272,670,722,468,881,596,-482,621,230,878,515,114,-738,-314,-895,846,-560,987,-940,-486,-879,-250,719,-209,-970,566,22,991,513,599,-776,334,-207,-363,933,863,546,390,953,-550,242,-801,378,-652,907,800,-673,-666,243,934,-219,566,343,-782,-905,-982,668,-85,925,-424,-630,52,547,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{397,-628,453,-935,561,-913,785,247,689,176,185,641,26,-160,-739,469,442,380,125,-360,-975,647,518,-37,251,562,-985,-714,-578,-834,138,778,455,-627,-983,986,-231,-785,292,-479,10,-280,-623,-724,678,-230,-90,936,-674,317,-767,-813,927,118,88,533,260,817,-623,-97,734,647,-232,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{-177,-536,253,24,693,-55,-701,-65,-775,-475,-504,264,-775,-691,475,-491,143,-270,87,-588,380,976,292,270,-285,-701,-180,-625,-637,973,260,137,-464,384,-992,-194,-910,-643,-405,985,-197,-826,-671,127,-852,939,-125,-891,63,-15,441,-414,113,-210,-431,359,-280,557,175,-759,-332,-988,-538,-956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar):java.lang.String",
            new int[]{-776,146,334,362,305,219,-990,-204,22,552,-895,613,-633,-60,-269,-727,743,246,-120,-358,794,-379,994,-854,-50,-203,-615,819,-299,336,-892,-761,-404,817,-263,-478,-778,860,702,-307,29,-408,301,-423,-278,752,-647,97,230,932,544,-665,689,639,963,70,-186,858,-937,-382,-894,-441,348,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar):java.lang.String",
            new int[]{-142,-866,198,-840,-842,636,169,-219,-784,-83,-486,-967,854,714,511,-662,-842,-22,-293,-546,-768,557,-685,58,269,576,424,-60,-830,47,-196,-558,-135,-605,-46,-953,-198,-520,-782,83,635,418,-696,-598,47,-929,-667,583,-123,397,656,734,196,43,-854,266,-114,524,-633,-842,-648,-853,113,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-580,-340,803,-468,-957,725,-636,-550,-452,586,-762,-236,-41,47,282,765,-539,74,-254,-983,25,516,940,703,-721,-535,771,-371,76,887,484,-250,-464,-663,651,-56,-45,-404,463,-580,903,-138,753,-374,73,-188,-220,819,13,-769,818,552,722,-762,820,-732,297,140,62,888,-714,-110,-468,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Calendar,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-334,-464,-371,103,528,-124,-355,518,983,648,-85,108,-493,498,899,73,-938,125,-141,647,-281,-364,-591,708,722,-79,-110,-474,-290,-385,615,-27,50,-531,749,-606,-72,-108,618,-9,701,-684,548,-934,-668,610,-963,739,225,272,980,870,-323,335,-39,-148,-650,43,224,-387,-364,-797,-192,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.String:RnJpLCAyNiBEZWMgMTk2OSAwMDowMDowMCArMDAwMA==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-574,-757,726,-6,-881,191,588,610,617,547,-730,-83,627,441,-688,380,187,-676,-913,263,-910,575,204,-112,946,528,-907,-695,368,-196,135,-562,763,106,-584,117,195,-507,997,-753,-113,-465,-562,-168,-948,761,-731,-445,982,301,44,-796,544,-251,691,-681,612,-77,992,-823,317,408,-838,444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.String:MDMvMjgvNzAgMDA6MDA6MDA=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-761,-425,417,86,987,836,-843,-544,958,299,46,428,-998,-733,-877,-715,-200,271,861,-687,95,-354,-424,155,687,-743,87,-270,-461,-900,-163,659,-632,-149,463,477,46,212,-444,-247,-734,375,-760,730,35,-145,-876,-960,787,-515,-849,664,-834,-528,929,-598,-158,64,242,824,163,595,600,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.String:TW9uLCA3IERlYyAxOTcwIDAwOjAwOjAwICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date):java.lang.String",
            new int[]{-838,-874,-43,340,868,-151,765,717,-231,-62,887,820,-811,-164,567,-221,394,-602,-545,158,781,256,469,782,507,537,-755,257,-235,-13,236,414,-156,874,220,293,153,-149,797,95,441,386,-691,-991,616,343,550,810,-250,812,-406,689,-864,406,-845,-100,-412,-133,975,-489,569,-905,-80,774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-481,-790,160,139,370,-41,491,794,514,127,-981,-498,243,600,-391,290,484,777,546,524,-78,-747,-117,-997,-822,201,526,-645,974,768,-859,384,156,-719,490,361,217,818,-692,964,-485,-209,989,546,776,589,424,-286,-313,-958,-805,839,354,-504,964,-965,-740,620,-591,-613,975,-281,320,-222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{453,544,-853,-655,540,-484,213,200,282,881,-437,701,-502,-608,-332,655,99,551,-710,453,-191,-780,302,-442,467,279,-442,-303,-109,-795,928,-78,-754,933,207,130,611,111,39,298,13,969,-808,655,-418,400,618,-622,674,-409,-482,958,-176,-464,362,343,-780,137,229,-772,997,-783,-803,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(java.util.Date,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-921,-916,325,-785,935,-561,-66,-380,105,-558,-835,375,-269,360,337,509,86,894,79,930,-478,856,-850,498,972,10,17,-5,198,-71,8,686,-45,174,488,490,-34,-98,-105,491,218,68,728,-491,432,-192,-412,737,-211,-707,-737,104,-15,372,478,922,-491,-339,444,-360,-297,381,-400,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.String:U3VuLCAxNyBBdWcgMjkyMjc4OTk0IDA3OjEyOjU1ICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{245,41,-85,349,785,521,-98,-402,34,654,898,-858,284,53,550,652,-644,433,-509,-986,323,521,928,720,421,792,821,974,875,-61,210,-69,79,698,214,-181,248,-16,-741,505,517,-817,-863,-516,-714,855,-323,-756,-456,121,986,490,905,582,-492,-183,-894,-279,-545,-958,-131,-553,590,690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.String:MDEvMDEvNzAgMDA6MDA6MDA=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{143,-275,465,-512,607,672,479,-238,-201,657,517,842,-970,-434,-209,181,-504,-856,532,-563,-358,-143,112,-165,322,-618,-596,-58,341,-794,-702,403,629,559,-758,442,53,-755,-309,-186,-347,781,429,-736,952,711,877,547,-72,-33,695,-417,-789,-299,-735,-574,-895,143,964,-634,-819,-257,-479,735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.String:VGh1LCAxIEphbiAxOTcwIDAwOjAwOjAwICswMDAw", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long):java.lang.String",
            new int[]{-440,-625,-607,-468,-29,275,-435,862,-985,925,394,252,-592,-593,-106,432,-593,534,-925,80,688,721,558,340,200,-242,-450,478,-536,-155,-21,-918,434,997,509,-503,-193,232,-774,-953,-562,-138,-197,978,-609,-399,164,59,84,248,794,-726,961,-751,194,171,-809,559,-769,-934,973,936,585,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-723,-691,-160,-588,-118,459,-137,-205,-224,627,93,388,-810,-419,871,985,-118,-549,408,673,110,554,373,174,-792,173,-643,798,-763,-781,-226,-761,-57,-377,1,228,-422,-633,418,831,-1,97,-555,-958,-90,452,-402,-747,-877,395,153,163,-748,149,84,747,410,-359,616,995,-845,750,943,338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{529,-875,772,834,702,-926,461,-336,158,452,-415,129,-538,-480,-657,311,-682,-371,-606,401,459,-598,-980,-725,778,-31,-950,102,840,88,202,633,682,916,853,-414,83,-780,-967,-684,-602,916,-112,836,472,-73,806,178,-846,606,-56,-954,500,-812,-19,-193,881,-897,-410,929,-109,356,334,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "format(long,java.lang.StringBuffer):java.lang.StringBuffer",
            new int[]{-521,530,770,409,853,-928,-996,506,545,-515,626,515,775,663,761,381,-763,18,611,-569,314,-939,125,272,623,-183,-153,235,473,120,-409,732,-385,-223,-99,-332,-58,-388,-925,-195,-311,-509,-39,861,-859,-460,687,-934,806,-980,34,836,-359,-457,-752,-675,993,-318,-259,-307,-361,390,376,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getLocale():java.util.Locale",
            new int[]{385,-190,239,-995,-319,-774,-389,-21,782,-964,-65,-46,-860,-694,609,796,16,-925,-17,-924,299,-686,-346,208,203,402,-251,-253,232,-621,-175,-230,-793,98,482,-585,113,319,768,-132,72,-217,17,-656,818,352,-90,938,744,-605,-779,-368,777,799,-50,164,140,210,-600,-974,406,393,87,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getLocale():java.util.Locale",
            new int[]{909,988,-615,760,188,-584,-987,560,266,-784,453,-500,-682,-875,-830,468,-581,82,188,296,78,187,107,-891,-808,-634,-792,83,928,135,394,656,452,694,196,-588,386,-459,-961,37,-574,752,-456,-973,-370,138,770,-864,-451,603,992,-8,-390,181,-160,705,-87,496,475,847,979,666,-177,-233}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzM=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getMaxLengthEstimate():int",
            new int[]{52,323,-674,-32,-964,146,-825,435,-766,-525,-818,878,509,-476,589,41,-685,-511,177,720,-244,-449,-974,-691,-979,65,-466,-987,-382,173,129,499,-582,256,-941,-545,219,-599,531,809,239,-639,548,212,-205,371,-169,804,239,-372,-612,-762,177,228,461,-522,-30,940,885,-18,-433,-703,205,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTc=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getMaxLengthEstimate():int",
            new int[]{562,-473,635,-907,-820,-823,-709,584,-688,-510,-26,631,599,755,829,577,-694,-212,-671,-700,811,-97,938,372,812,387,-589,479,245,-254,-260,915,-514,225,653,-320,-905,-182,-441,22,-902,327,789,67,604,-106,132,-383,403,-149,758,172,-869,791,-163,736,559,-755,-549,-907,-372,567,-303,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.String:RUVFLCBkIE1NTSB5eXl5IEhIOm1tOnNzIFo=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getPattern():java.lang.String",
            new int[]{-510,425,566,290,444,-303,945,-153,229,180,-911,-445,6,-743,-879,357,11,851,-675,94,742,338,459,-2,293,-884,778,-695,-924,213,478,-153,-318,444,74,-897,-855,194,-948,-664,-474,-863,-995,155,379,924,-545,154,-605,221,530,88,-643,-701,808,-524,337,893,720,504,64,975,157,-411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.String:TU0vZGQveXkgSEg6bW06c3M=", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getPattern():java.lang.String",
            new int[]{365,-92,281,-865,337,-520,-466,-779,-536,-996,-979,-820,485,-65,510,75,-234,375,-72,877,-841,-561,168,-422,434,-386,-894,-690,678,645,672,-502,-488,554,611,620,540,-986,391,341,48,-494,604,489,-343,-582,-824,632,352,-412,-305,859,-878,630,-727,-224,302,867,-515,-302,-368,-346,127,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{-433,560,608,-548,856,-131,88,-452,-28,807,640,-335,-766,-172,756,-823,657,-663,27,952,-397,716,723,585,29,557,701,-106,-646,484,672,598,-606,-370,-610,-788,-974,829,749,-428,304,883,919,-866,594,-104,-266,-790,948,588,293,-377,-561,-258,417,961,-541,522,-952,507,-926,221,-94,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:sun.util.calendar.ZoneInfo", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "getTimeZone():java.util.TimeZone",
            new int[]{474,-773,-166,-952,251,-535,106,469,-192,374,-290,998,537,-379,81,635,583,161,-867,-334,968,-991,531,132,679,147,111,44,877,355,-879,-798,-156,434,-497,-459,497,-716,-135,-657,-680,623,-71,-662,-545,-285,317,-214,-662,451,-708,927,-269,-663,779,858,335,516,357,867,213,821,257,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Integer:NjYyNjk5MTIz", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "hashCode():int",
            new int[]{89,497,-135,667,-133,-47,-170,-624,628,503,-25,192,483,993,17,-369,-413,-100,471,179,-908,679,199,-667,-577,-389,-776,-43,878,-878,605,-467,-615,706,-242,-892,143,-708,-696,-518,-170,-804,246,501,67,628,74,-812,930,-849,-716,-120,-580,438,-324,574,21,-778,830,189,741,426,-825,-348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDE1MDU4OTQ1", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "hashCode():int",
            new int[]{429,91,555,743,-370,437,210,783,394,-519,-180,559,196,736,-277,340,934,146,688,-656,471,-484,-285,997,-305,934,771,186,518,-929,-343,-929,104,-550,874,-852,470,455,-35,83,586,-848,-401,88,120,-160,-578,535,23,-342,603,896,-666,-463,-130,-844,-250,-673,891,-651,-196,-976,823,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.String:RmFzdERhdGVGb3JtYXRbRUVFLCBkIE1NTSB5eXl5IEhIOm1tOnNzIFosZW5fVVMsVVRDXQ==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "toString():java.lang.String",
            new int[]{-965,413,-57,-771,376,931,40,-107,-172,-647,931,587,119,-44,634,-500,998,-833,-557,-654,20,-413,93,-251,-41,112,952,978,577,-893,-161,-941,904,-389,-831,-786,480,-198,-961,740,48,533,-402,-90,87,670,-417,346,-724,153,264,356,478,252,-801,233,-839,305,188,-806,312,380,579,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.String:RmFzdERhdGVGb3JtYXRbTU0vZGQveXkgSEg6bW06c3MsZW5fVVMsVVRDXQ==", DEReplay.run(
            "org.apache.commons.lang3.time.FastDateFormat", "org.apache.commons.lang3.time.FastDateFormat", "toString():java.lang.String",
            new int[]{-930,58,-640,118,164,-242,-721,-296,-553,-542,-45,-378,868,-912,-991,-675,-777,-551,-44,818,904,955,879,161,291,-943,-279,-738,177,-462,-489,-787,-897,832,-132,-722,710,768,-875,-168,304,-236,78,-945,-296,-639,-61,-28,-849,239,-570,553,551,-662,-911,-956,-325,-416,877,-606,27,-156,-67,-238}));
    }
}
