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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{-203,-756,-991,-128,-18,-17,912,752,14,27,-1000,1000,334,-44,-322,-640,-1000,421,-1000,28,-754,-521,-189,-625,-287,-867,1000,-1000,1000,-523,218,-769,19,292,1000,438,-62,436,799,593,-1000,-84,715,294,-48,-1000,-1000,-272,253,570,740,1000,-143,-494,-405,945,-1000,-743,13,-705,-152,-862,-227,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{-836,1000,-330,-983,517,783,-608,205,259,-618,-1000,730,799,182,52,-221,307,658,654,269,382,991,69,-158,-188,915,-914,226,-343,-891,-894,664,1000,-882,992,-10,1000,123,-878,393,123,1000,-912,9,1000,759,1000,-611,-52,273,-638,237,876,460,190,-838,785,161,-789,1000,695,1000,-877,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,-1000,-367,-239,521,-1000,819,1000,179,-757,-263,335,-816,514,1000,-273,-1000,-1000,1000,-753,-135,16,-986,-31,832,-325,1000,-1000,218,464,-231,-700,-480,776,-172,964,72,461,749,1000,591,-261,349,468,-1000,-1000,-1000,357,203,-1000,-950,-372,619,-1000,-964,-228,285,-1000,317,-1000,816,-1000,1000,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{-497,518,325,-862,-402,272,-137,-646,-420,-374,-465,730,491,117,-585,-761,439,645,-135,46,431,207,275,517,79,-162,-648,826,323,-733,-894,878,323,-104,992,-33,825,-574,-904,-243,457,672,-332,9,650,-38,849,-226,-823,851,-990,730,-316,868,761,-246,968,465,409,923,-705,445,-619,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,-854,325,-1000,798,272,544,1000,769,-1000,-1000,1000,-780,1000,845,-185,-1000,-604,-135,-1000,666,1000,-372,-799,907,-162,1000,826,-657,529,-1000,-700,322,347,301,-33,1000,921,448,1000,1000,-77,-332,1000,650,-501,100,-936,257,-1000,-1000,-615,-316,-1000,-133,-1000,1000,-1000,409,-995,1000,337,1000,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,792,646,426,-282,-705,293,411,-1000,1000,1000,-572,456,-959,-37,-696,1000,-490,-792,1000,-1000,-1000,-732,1000,256,-425,-885,1000,1000,-818,218,1000,-147,63,324,85,-1000,-1000,-570,-1000,-442,129,382,-446,-65,-415,-571,1000,-164,400,-187,1000,-956,777,-186,229,-1000,1000,809,270,-1000,-988,-879,-960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,-1000,-367,-239,33,-1000,547,834,239,-638,-370,490,-545,71,938,-158,-1000,-786,1000,-1000,77,366,-726,-31,797,-830,1000,-1000,253,595,-231,-244,-480,557,-172,893,103,244,736,1000,1000,69,349,223,-1000,-1000,-1000,357,-163,-1000,-1000,-351,25,-956,-684,-228,766,-1000,377,-1000,-19,-1000,1000,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,792,1000,1000,852,-705,-446,-280,-667,476,738,-1000,-573,-424,-38,-413,935,173,-613,1000,-756,-224,-1000,898,77,147,-1000,-888,539,-1000,815,201,905,897,-750,-146,-914,-250,-381,-688,-1000,32,838,-48,-65,-415,375,1000,264,644,685,614,-1000,296,-491,522,-1000,-1000,-21,270,1000,642,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,-749,-320,-851,809,-316,699,31,117,-1000,-1000,852,-16,1000,616,-888,-1000,191,645,-1000,1000,1000,-295,-617,876,-94,930,-1000,-1000,173,-852,-1000,479,883,-533,795,1000,509,766,800,1000,144,500,-961,-500,-666,-6,1000,-585,-684,-1000,-511,734,-937,271,-106,1000,25,-533,-543,1000,3,1000,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "abs():org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,-1000,1000,1000,551,-1000,1000,1000,179,-535,-581,-154,-1000,514,571,281,-1000,-1000,711,-753,-962,-81,-1000,363,1000,-645,1000,-1000,-1000,481,299,-700,-376,724,-1000,856,427,445,706,1000,535,-426,1000,-111,-1000,-894,-942,1000,44,-1000,-398,-372,258,-1000,-964,116,514,-1000,-194,-1000,996,-707,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-240,1000,1000,-773,-541,827,-1000,-761,758,2,1000,-55,141,-5,-48,1000,85,42,-247,1000,-464,-575,-1000,-364,227,522,435,1000,521,-377,-162,782,327,537,510,-1000,-400,-700,-776,902,1000,-295,-1000,262,-1000,-1000,234,688,-512,13,22,611,-1000,-1000,-729,116,-748,-1000,407,620,1000,30,-225,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-748,-154,1000,-125,447,410,-1000,-16,919,1000,364,1000,842,249,94,819,777,1000,962,999,-682,-887,-400,275,-415,1000,1000,393,519,-349,24,264,827,-365,-531,-685,-1000,-989,-1000,974,-169,156,-1000,899,-564,-761,-94,344,464,-353,929,1000,402,-1000,-198,-33,-1000,275,780,1000,1000,1000,244,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-627,841,-633,957,-108,-59,1000,-356,-1000,-1000,-1000,-441,-1000,249,-549,-1000,-1000,1000,1000,873,1000,1000,1000,275,1000,226,-1000,550,739,670,402,-14,-648,-141,-686,1000,1000,-735,-1000,-1000,-1000,861,538,-1000,100,770,942,-762,112,-361,51,-1000,898,1000,-287,-33,1000,805,-160,-1000,-1000,-1000,274,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-264,-154,-400,1000,902,-544,-531,-785,919,299,511,375,-307,1000,-234,95,-605,-336,-600,162,322,1000,464,-320,361,-433,181,-371,-313,934,1000,-427,-809,-624,-232,-685,581,-934,-518,893,-15,480,-219,-915,1000,113,838,344,-106,-1000,610,-341,653,1000,-742,-158,299,778,963,-1000,-564,-586,-426,464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-929,-396,685,1000,514,410,-1000,1000,507,-288,1000,1000,141,180,-48,59,1000,1000,1000,39,-1000,-624,-1000,-638,227,-372,1000,-121,-228,618,709,527,299,-167,-151,-302,-400,-886,-1000,827,-964,-402,-1000,695,214,-33,391,-1000,210,-931,1000,35,924,-498,217,-139,-1000,635,875,485,-1000,1000,416,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-22,-8,237,-357,-581,-1000,-16,14,-465,-1000,-513,-266,-384,-440,-743,979,-1000,-633,111,-104,-484,363,759,809,789,1000,-1000,-447,-1000,827,362,-155,-503,-187,601,-1000,-230,187,414,-798,673,-498,-143,-591,1000,-838,55,74,-93,1000,-783,-581,-440,528,-735,1000,1000,-368,96,-524,-486,-1000,-1000,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{549,-1000,222,882,334,-205,41,1000,447,-1000,-625,-628,-613,-963,531,-1000,730,449,1000,-1000,367,445,-663,-297,1000,-41,-447,549,-1000,1000,-192,-599,756,-148,627,1000,296,-502,61,-1000,-629,-305,959,447,-1000,55,65,-397,-665,114,961,-411,-101,1000,-183,521,1000,-296,-159,587,-1000,876,1000,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{356,-729,1000,15,-42,51,-1000,1000,1000,-1000,878,850,630,-71,235,1000,1000,1000,636,-1000,-1000,-180,-1000,-1000,608,-1000,1000,-95,-114,554,650,903,363,1000,956,-1000,-1000,-725,-535,1000,518,-1000,-1000,1000,-123,-781,130,-949,-575,-752,1000,478,-680,-1000,-1000,582,-1000,-981,1000,-12,-1000,1000,607,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-447,635,626,-815,574,11,-376,467,120,-231,-591,206,-324,-409,-592,730,-397,-33,92,891,-993,72,540,823,575,1000,-20,1000,402,258,-277,-412,40,108,197,-531,400,-1000,-335,596,333,722,-1000,-194,-1000,-1000,672,-267,-346,215,375,-210,-992,-891,195,-427,-485,-1000,222,143,1000,82,-369,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,207,338,1000,-281,530,395,714,-228,-964,147,-283,-727,814,-537,-1000,1000,575,-923,494,-218,-1000,523,627,533,221,381,261,-1000,237,839,1000,1000,-451,687,-508,-137,-773,1000,491,8,711,-232,-192,824,-559,-270,1000,-19,-48,-475,186,-980,1000,-548,1000,48,914,809,-737,502,-1000,-417,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,46,1000,1000,943,-849,432,-703,-1000,376,1000,1000,-809,-955,21,-830,1000,468,-1000,-1000,615,105,-1000,-214,976,-905,-733,54,-290,-1000,1000,593,229,-466,898,-805,922,-1000,867,-1000,1000,130,579,-1000,706,1000,1000,-1000,379,356,-120,906,281,-82,1000,201,1000,735,-318,-988,59,432,545,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,639,101,-312,608,-525,169,-863,-923,567,1000,1000,-751,-38,77,-1000,892,137,-1000,-1000,-149,-850,-616,-213,1000,-78,-765,-439,856,-1000,1000,715,356,87,773,-709,715,-1000,669,-1000,502,-265,288,-1000,-542,850,466,-664,557,139,273,354,1000,-915,734,441,1000,961,-118,-190,-328,224,504,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{382,-285,928,370,-476,935,338,1000,571,59,47,1000,8,1000,-541,-109,1000,1000,-1000,-63,232,-325,-717,400,530,-859,-347,285,-725,290,501,1000,1000,-582,495,-758,955,-1000,587,-328,944,1000,-447,-957,1000,105,224,1000,-427,129,1000,-10,-929,567,-555,1000,-1000,618,1000,-561,721,-1000,-363,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-112,173,291,279,-541,-1000,-426,207,-248,51,-728,-25,-237,-823,1000,-271,669,-788,400,-143,272,243,117,597,-768,-731,-1000,-754,-374,-377,-316,185,-950,-544,-246,-551,823,932,134,-187,245,211,-52,582,95,11,-482,-541,735,51,1000,-287,400,1000,937,116,573,227,-936,674,366,742,183,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-568,688,-1000,-713,-63,-1000,699,-1000,-1000,886,1000,-794,301,-302,921,-962,-461,-830,-1000,-256,-1000,-1000,1000,643,153,800,-792,-296,1000,-503,-395,-976,-1000,294,-775,-358,682,400,578,-31,-1000,-1000,-186,223,339,281,-665,-396,536,-1000,-444,-678,-27,-1000,79,-898,1000,-178,373,3,-90,-297,242,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,1000,-616,-1000,-541,647,323,56,935,314,-1000,-1000,122,1000,268,-271,-625,526,1000,1000,-647,-946,1000,1000,-209,324,-1000,-1000,636,478,-279,185,147,-112,-50,-543,93,1000,-129,1000,-1000,-221,-772,827,-1000,-181,-1000,1000,-1000,-155,1000,-1000,1000,-190,-1000,1000,-517,777,658,674,-1000,-321,183,851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,324,320,1000,-281,1000,263,-365,831,-964,147,-658,-1000,784,521,773,-910,-81,-1000,-168,1000,366,-843,-315,-311,221,525,-919,-1000,-789,1000,766,1000,-199,722,-995,-137,1000,-258,51,-193,863,798,718,-250,9,-742,25,-943,800,-475,-494,-1000,954,-1000,847,-645,72,346,-43,-142,-16,-233,54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{203,-360,299,-236,-226,369,-738,-103,-197,464,-747,-353,-636,-27,-35,206,-542,-673,-679,-192,973,-33,-868,71,184,-419,82,255,459,-542,-912,-713,-911,-676,-420,-229,-538,363,847,-893,-848,98,225,298,-78,-718,-378,-908,134,-959,-385,653,-403,109,219,157,-503,-796,-702,-589,-265,346,-455,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,106,-744,148,670,-486,804,-445,-1000,-1000,901,1000,1000,742,981,-1000,843,-464,1000,-811,-276,-222,-100,-703,-1000,411,460,-749,-1000,1000,901,1000,460,-700,889,802,947,-1000,1000,754,784,1000,-1000,-882,1000,-1000,-1000,961,-580,1000,-337,-1000,-551,1000,-1000,226,1000,1000,1000,1000,-1000,1000,632,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{723,-1000,1000,-450,-758,505,-81,-1000,143,387,-664,1000,-55,490,-299,61,-482,-627,973,-554,-283,-249,-205,-1000,-15,542,-555,-1000,-1000,1000,931,1000,237,-1000,146,663,554,19,-482,333,60,1000,-805,-706,-17,-133,-611,1000,-2,1000,453,3,-219,-362,-815,-894,-358,264,1000,-98,232,423,-1000,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-321,-455,1000,297,492,85,1000,-365,-1000,-1000,294,811,886,717,791,317,-895,-1000,724,736,-1000,1000,-1000,136,-1000,1,-49,398,-804,1000,1000,1000,237,-632,705,176,-743,-1000,86,829,50,1000,-120,-1000,-814,-1000,250,654,-1000,1000,-76,-1000,36,1000,-1000,-569,886,1000,1000,498,1000,1000,-195,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{3,1000,999,1000,-271,-385,-1000,-873,-1000,-1000,171,-1000,1000,-279,586,-590,419,-1000,948,362,1000,1000,-353,1000,-553,-696,672,-232,1000,-265,585,1000,-1000,-1000,-33,-436,-1000,-640,1000,-1000,-1000,793,137,-1000,1000,-1000,761,-877,-1000,739,1000,-822,171,1000,144,-1000,1000,-675,-687,1000,1000,1000,1000,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{991,-303,-860,-626,669,943,-657,1000,-233,530,-68,101,-369,995,-513,-906,-155,-269,-1000,627,127,-25,-1000,-318,-1000,-568,-673,-261,-86,90,-1000,199,355,-950,607,517,-1000,-254,731,-14,-1000,516,-103,139,384,-218,-863,-1000,-278,-117,490,283,120,816,351,-449,-3,-990,-1000,-638,253,334,542,544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,642,-1000,1000,-377,191,-1000,-604,1000,-923,-747,-1000,-1000,50,-1000,1000,-1000,523,67,-192,1000,1000,207,341,184,-568,-384,-778,1000,-1000,-650,-1000,-1000,-1000,-1000,-1000,-137,1000,125,-1000,-297,-1000,1000,864,-1000,481,208,-630,1000,-1000,-1000,1000,-18,-358,845,1000,-1000,-1000,-1000,950,-1000,-1000,-1000,-286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,-99,-452,1000,166,59,535,761,-821,-997,-941,-58,-240,-410,-874,1000,-1000,137,-1000,-649,182,1000,-957,421,-204,-995,496,1000,-698,-474,-1000,1000,67,97,354,145,-770,-271,-1000,-264,731,1000,-918,-1000,-107,-116,949,320,601,1000,-627,360,444,215,798,-277,-753,-1000,723,1000,932,-1000,-1000,840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{530,-1000,-793,1000,1000,-1000,1000,1000,-571,1000,514,-1000,411,-86,-807,681,-1000,-1000,-1000,-1000,-660,1000,-508,925,-1000,-549,23,1000,-1000,-1000,-28,325,1000,-249,783,995,-1000,491,-27,719,-76,1000,-291,-66,3,-230,132,183,-550,904,-1000,786,26,1000,-962,873,1000,911,1000,752,979,262,-519,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,-603,-1000,723,889,163,619,967,-1000,-140,664,-67,214,-1000,675,-960,431,829,-669,-1000,176,202,-492,-358,-23,-908,837,-211,672,-735,-452,869,685,1000,192,1000,-1000,1000,-1000,1000,-539,-1000,-1000,-897,-1000,-147,784,239,-1000,-856,363,340,-612,744,204,826,877,1000,605,-159,-1000,-953,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-618,145,567,268,-846,431,62,164,245,-447,89,947,-106,-145,-518,15,1000,-823,-47,1000,36,-761,-938,-605,-782,-308,152,-512,493,-677,-84,12,-25,314,-142,-726,877,666,-537,-189,779,-648,127,221,47,-374,188,-289,-79,-426,92,660,806,276,374,-669,-757,-831,-1000,456,-439,-373,-213,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{571,-470,-1000,1000,858,-56,834,1000,-1000,-1000,-1000,314,-752,-633,-443,539,-1000,-171,-1000,-1000,525,787,-1000,1000,-773,-451,673,1000,-1000,-803,-1000,1000,1000,186,766,-307,-1000,295,-821,1000,441,1000,-976,-782,-297,160,62,751,-138,766,-408,-28,-104,922,1000,813,1000,-466,1000,1000,652,-718,-778,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{395,-436,-512,751,-18,-256,589,679,-792,-86,-159,-873,789,38,-926,505,-755,-319,-1000,-128,-193,-1000,-935,123,-210,-894,408,1000,-7,-387,-458,516,-78,668,445,39,34,137,-819,-417,968,1000,-928,92,-194,-769,-126,-22,177,390,-707,841,138,-237,-438,-421,1000,-144,713,1000,502,233,-1000,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-417,-952,-936,268,595,-507,911,883,-959,302,117,-851,718,7,-518,-212,-435,-823,-642,-256,-10,585,-969,575,-782,-308,548,928,-14,-678,105,12,792,-121,896,629,148,878,-537,-135,779,665,-991,870,-421,-772,-119,262,-743,-236,-522,660,91,276,-764,607,979,911,987,712,37,-70,-989,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,729,680,712,43,-426,294,996,617,-1000,-1000,-671,195,-3,-1000,1000,-1000,-1000,-387,-306,103,1000,-995,784,-1000,-786,-933,1000,-1000,-866,-1000,784,434,-1000,407,392,-808,-397,247,93,-272,1000,773,-1000,685,-692,197,-398,906,1000,-1000,1000,782,601,-599,762,1000,-1000,693,1000,1000,130,-122,-311}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "add(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{405,459,1000,-715,-336,523,-491,-609,1000,551,1000,-820,215,432,-930,320,-919,154,-553,1000,-1000,948,-546,-418,-198,-1000,-1000,422,516,474,71,1000,-606,731,-1000,-191,691,-1000,-424,-732,-171,-1000,-1000,-1000,278,-140,-15,-1000,857,439,-729,1000,184,-1000,-1000,-104,-162,463,-1000,354,396,239,-389,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-1000,598,66,175,-790,1000,54,871,637,-303,1000,431,1000,-1000,-1000,-532,1000,1000,-1000,555,-738,-1000,37,1000,-1000,-477,-1000,492,656,-700,508,-930,-909,-642,-428,924,-751,-1000,-1000,-480,1000,-503,-780,642,-880,-848,-346,-1000,596,-133,1000,1000,-1000,-132,739,-861,1000,121,-811,773,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-1000,693,1000,432,239,1000,366,387,1000,1000,-731,7,432,1000,-88,-5,121,-1000,-212,-103,600,-1000,376,1000,19,-1000,-876,726,73,-1000,100,-64,1000,-53,-1000,-272,983,-1000,-389,844,-362,-620,-268,667,-403,-29,-1000,-142,801,124,-1000,-1000,477,1000,-1000,-257,1000,89,-211,485,-888,-162,119,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{588,-1000,411,955,455,-1000,-516,-1000,-113,419,-1000,34,-361,1000,418,-239,-431,-1000,669,76,1000,821,376,-688,654,226,324,-464,-400,-192,-364,234,1000,489,-71,-319,830,-1000,-63,795,-1000,82,286,-555,153,248,-1000,1000,169,1000,-688,-781,554,1000,-662,41,-411,-1000,-276,311,-1000,180,856,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTY1NTM1", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-728,875,1000,-165,292,482,-400,-654,295,-359,-38,860,17,-754,-185,-1000,356,729,-475,-708,337,-376,472,112,-473,-1000,-1000,513,656,286,443,-810,1000,-445,581,354,-692,-689,-1000,-404,196,400,571,199,860,-851,-241,187,1000,-131,930,14,19,-159,1000,-484,-855,608,-1000,382,261,-291,336,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-425,-270,-324,1000,-802,739,128,-845,-332,858,522,-638,518,646,-434,-10,594,-484,-567,-62,404,-1000,-1000,969,-925,-444,-558,686,314,-959,1000,-220,971,162,-233,689,5,-955,-815,-252,623,-1000,-1000,340,-905,-999,-1000,-201,769,-146,-34,-205,-103,916,-1000,-899,1000,-691,-907,835,68,-828,-818,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{112,561,1000,1000,79,-400,-1000,-69,-409,-1000,-802,-808,458,331,258,-800,47,-1000,261,-793,596,1000,-913,-342,407,-506,-30,361,947,99,783,1000,271,1000,429,888,-742,-1000,-607,-176,136,1000,-1000,-1000,-133,-426,-1000,1000,132,214,-766,-207,-603,-339,-1000,805,1000,-67,-246,-536,-400,-347,-355,172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{588,-882,587,1000,-51,-1000,-58,-210,343,822,-1000,-654,367,1000,551,-285,-361,-1000,754,-265,682,-75,376,-1000,1000,657,467,49,-400,-725,-1000,1000,1000,1000,-462,-159,864,-909,-63,1000,-1000,623,-345,-623,659,248,-1000,792,-386,317,-1000,-782,826,823,-927,678,578,-1000,248,-338,-1000,276,206,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-104,-1000,-117,-467,-125,-1000,178,-863,169,187,155,178,440,1000,-341,51,444,-1000,-141,77,475,-248,1000,117,251,571,133,-80,-400,-232,593,-51,1000,-270,401,70,705,-1000,-506,361,-1000,-1000,-880,329,-149,-518,-891,1000,284,352,712,-26,-846,993,-550,-702,701,-642,518,1000,-430,-331,231,538}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-172,634,-271,-434,903,-1000,474,530,-211,-152,-168,1000,631,1000,-810,-703,-557,-162,1000,1000,3,-1000,609,-1000,1000,-644,-685,747,-1000,-248,-769,728,-279,-1000,291,-814,606,1000,408,780,966,-821,1000,956,525,1000,-1000,216,1000,-1000,-611,-1000,-1000,543,1000,-996,-50,-710,-187,241,-532,1000,-873,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{17,633,179,44,-6,-973,607,211,-253,6,1000,619,-193,383,-207,648,1000,-404,-677,-617,-400,391,-606,-217,696,306,-881,-199,-521,400,-511,758,148,156,164,-901,-814,1000,-233,875,38,-334,-1000,711,-104,-1000,572,228,-355,-644,907,981,694,913,327,392,82,50,-847,996,1000,-376,56,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MTQ=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{88,-729,133,58,404,-76,23,-832,72,-363,-512,-661,946,567,-537,-241,-449,959,-258,-350,468,608,957,331,-210,-724,-14,-273,316,439,-533,-597,570,-865,-377,876,840,193,-350,748,961,-361,554,-980,761,-272,-483,-151,810,696,-391,948,362,58,2,873,-722,-886,685,499,-930,131,156,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{876,863,357,-25,553,-1000,1000,458,163,-1000,394,593,955,938,-730,811,262,425,694,995,-1000,-348,11,-532,902,-1000,-290,985,88,183,-1000,1000,-712,98,-113,-683,5,-314,190,70,-1000,-395,66,1000,-408,1000,-184,-626,-504,-911,-838,-1000,-1000,1000,-315,644,652,-934,-1000,564,1000,-618,-420,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTI=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-265,-146,179,966,966,-988,338,-329,5,72,281,477,516,599,-207,-215,265,673,476,815,-158,-245,-512,-722,696,756,-881,781,-761,507,-511,381,-777,-738,164,388,647,342,526,312,38,579,396,640,111,833,-445,61,528,-466,-393,-310,-236,913,-95,-140,940,-577,-847,-861,-126,678,-261,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-48,-518,419,117,1000,-392,242,-349,-365,-706,497,130,460,1000,-562,149,612,963,-528,455,-572,161,-1000,47,787,758,-145,529,-173,713,-561,-1000,27,-745,-420,179,426,254,529,630,-529,491,-23,-145,-222,818,-396,-1000,103,266,525,-194,188,1000,-305,7,775,-302,-1000,-1000,491,175,153,-850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-1000,210,1000,1000,863,-263,1000,1000,-1000,252,-183,-708,-729,-775,1000,542,983,-81,636,-1000,1000,814,851,-52,-665,-740,788,842,-970,-1000,1000,-350,487,618,-486,-1000,87,1000,-1000,-501,1000,812,30,-108,-487,-1000,941,682,530,269,-262,823,825,-840,188,-107,-1000,-269,368,363,397,319,731,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{103,171,1000,-523,527,1000,571,-38,203,-347,1000,-774,1000,-668,-70,1000,175,726,474,-1000,-274,743,252,639,1000,818,690,638,718,137,-324,99,9,243,-158,-595,278,-948,-92,-260,-635,842,-1000,630,-1000,-590,778,188,-503,-352,-405,451,986,608,-1000,193,598,1000,-763,397,308,-1000,247,-846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{239,333,771,-696,396,-28,456,-140,-123,-433,1000,-774,-788,53,-928,1000,328,278,-19,-201,-941,173,-903,585,1000,590,616,-86,628,854,-539,704,-793,219,-815,-177,144,-270,256,360,-752,-421,-1000,1000,-975,-575,-65,125,401,297,139,-568,810,1000,-816,193,517,630,474,935,584,-968,-265,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{185,-1000,-151,-418,890,-825,-422,1000,143,-725,436,-814,-305,-760,-895,215,122,-292,1000,685,-1000,-852,1000,223,825,-1000,803,-671,-869,-1000,-331,-82,-1000,467,-273,-101,-548,205,186,-115,130,1000,-196,473,-724,-916,1000,-1000,-569,409,-361,386,-300,843,-1000,20,1000,305,-336,1000,-1000,-593,862,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{1000,117,-1000,-505,423,1000,-933,178,923,-1000,469,117,-947,-361,181,-76,341,-906,200,195,220,-1000,1000,-854,1,152,-38,-195,-1000,-391,-920,1000,-350,-192,1000,662,1000,194,-17,-255,34,-536,-894,1000,1000,-909,9,435,24,612,324,223,101,-690,-1000,1000,1000,361,843,113,992,-1000,8,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{591,999,-217,372,722,-150,1000,-856,97,-167,-1000,179,-192,-872,788,-66,-285,-137,-350,284,432,352,-127,728,322,-246,-671,-915,-1000,260,996,879,-704,757,854,-398,863,561,-206,-978,737,-324,-904,-320,553,-493,-842,856,-516,796,617,1000,1000,-615,-283,-1000,586,-599,1000,-774,559,-371,-119,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-1000,902,1000,941,539,1000,1000,536,-599,1000,-1000,1000,1000,-560,-601,-433,-483,-137,-339,1000,-813,169,591,728,-367,-293,1000,456,517,-742,303,364,-589,-1000,890,-48,-506,-346,-88,1000,-56,195,-838,-320,-1000,-13,273,1000,-1000,-960,867,-866,-1000,-1000,331,-1000,-108,256,357,271,-63,659,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-826,-2,963,-1000,662,357,-786,1000,0,259,1000,-708,-53,-781,-1000,-103,70,-1000,1000,703,-1000,0,886,0,-753,-970,837,-865,-153,-1000,-370,-97,-745,-1000,1000,41,-1000,533,-304,-593,887,1000,-332,-226,-1000,-1000,846,-940,-633,-164,1000,289,0,878,259,-1000,1000,-737,0,1000,-1000,0,931,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{1000,-1000,-1000,583,-614,1000,-197,495,896,-1000,857,1000,-109,578,-993,674,-474,-1000,-329,991,-37,-541,1000,-399,-536,201,-900,-164,-1000,-479,-1000,92,-533,-1000,659,-472,830,-469,1000,174,-920,-1000,458,1000,-309,-1000,626,1000,260,822,166,-1000,1000,-741,1000,958,1000,-1000,1000,22,1000,772,273,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{1000,-671,-868,285,-1000,1000,823,1000,1,-259,-1000,1000,459,491,-1000,542,-632,-1000,163,1000,-1000,-1000,1000,965,-248,-183,434,-5,-756,-1000,-1000,-436,-740,-1000,468,530,-317,509,1000,613,-330,-534,299,257,-1000,-1000,927,1000,-572,-79,667,-1000,406,-156,906,-86,1000,-472,419,955,1000,127,1000,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{18,-441,-505,910,-82,487,258,811,31,-392,240,-400,757,-200,113,1000,-513,-376,-25,678,1000,1000,-164,836,-323,-272,-770,688,-971,543,342,999,-646,81,672,431,157,292,-543,430,-293,418,-196,-139,1000,-337,-162,-784,-617,-602,-911,-282,-1000,-679,-100,-391,-141,-371,505,385,508,33,-361,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{-1000,-1000,-80,-857,745,-743,-1000,-1000,208,-552,-1000,1000,191,229,338,1000,-26,1000,-784,-1000,-100,-1000,1000,-259,168,-1000,1000,-745,1000,-780,-654,879,803,-1000,-1000,-38,-1000,432,756,-738,94,-985,-1000,702,465,-913,851,214,806,-799,-953,1000,-202,-676,1000,7,812,326,-1000,-1000,1000,-474,-293,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{-872,1000,-1000,285,89,561,157,446,362,-911,-339,1000,-51,1000,577,-908,-314,38,-25,-401,1000,566,772,-146,512,-366,-1000,172,429,-720,-82,-242,-1000,316,66,117,-668,122,-1000,492,-952,69,-848,-1000,1000,231,-270,-190,-326,-40,-20,-282,-746,-1000,-411,194,1000,212,503,-437,450,-935,819,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{1000,-716,1000,285,1000,-665,-1000,-1000,-1000,-742,1000,-1000,1000,-1000,1000,1000,-968,-997,473,1000,-1000,-856,1000,1000,-1000,-385,1000,-32,-129,1000,705,402,1000,-1000,703,568,-351,-3,1000,-1000,1000,93,-1000,1000,261,352,1000,-1000,1000,1000,-1000,664,218,828,1000,-1000,-523,205,-1000,381,-1000,1000,-1000,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{-171,279,208,171,413,-273,432,386,-1000,-624,1000,-580,493,-511,169,-882,-125,-1000,868,1000,-1000,-1000,-392,1000,-362,-387,-527,576,300,119,1000,-997,1000,113,-163,471,240,1000,359,970,1000,612,-264,-136,827,193,186,-1000,-236,556,549,103,88,-327,-753,-1000,-174,-443,372,1000,-289,903,-1000,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{-937,-85,166,827,214,-410,-586,-569,-1000,-134,1000,-580,437,320,918,-1000,-576,-655,575,206,-1000,-586,186,1000,-23,-1000,-865,91,1000,942,-136,-1000,40,9,-678,604,-763,1000,-1000,380,1000,1000,-206,-62,805,392,22,548,-597,627,-659,-645,-255,-1000,-589,-456,-408,505,581,-769,-1000,-684,-483,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{1000,-917,1000,294,-507,-759,619,-735,-265,885,-63,411,304,-1000,1000,935,-946,-212,249,-635,321,-401,-825,351,-65,751,-501,1000,564,1000,-342,1000,1000,-56,-702,230,512,747,1000,-885,-820,93,40,538,261,-1000,680,899,861,353,-732,312,-788,-541,-572,-949,-523,156,-39,413,1000,1000,-1000,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{1000,-234,1000,756,267,-393,91,913,-1000,429,1000,-1000,1000,-1000,-117,-335,-251,-1000,532,1000,-34,-140,-615,1000,-1000,265,-68,1000,-1000,1000,1000,-892,1000,1000,678,1000,1000,970,817,779,1000,1000,1000,760,-128,370,1000,-529,-874,-779,515,-176,-1000,434,412,-906,-1000,-1000,502,1000,-1000,1000,-1000,582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "compareTo(org.apache.commons.math3.fraction.BigFraction):int",
            new int[]{-311,-72,-400,906,-371,103,614,1000,-47,237,63,400,264,400,987,-1000,229,-274,-108,19,1000,899,-164,198,58,-203,-1000,732,-971,-204,243,-979,-400,1000,286,664,157,704,-583,1000,-366,348,530,65,1000,904,-162,37,-1000,-602,1000,-282,-1000,-788,-576,-70,400,-371,1000,-6,15,-354,273,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{913,-499,-875,-575,-335,659,119,867,-788,517,-462,-778,-146,-439,-865,-46,88,-5,-426,-393,-127,46,-44,-241,-965,-819,-544,-250,562,793,893,-155,149,-317,-244,-288,-376,-169,-275,588,-858,-661,-978,-610,933,203,-891,-858,227,599,140,569,583,779,386,480,-775,304,-607,-354,-723,376,-982,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{974,-743,1000,-833,-782,1000,653,139,-1000,699,1000,449,524,264,352,220,1000,1000,150,-1000,-466,339,852,-203,-68,-156,498,-181,442,843,1000,-1000,-739,1000,247,1000,-222,16,558,-929,625,1000,-642,-960,697,-480,592,-498,38,321,769,268,745,-14,-716,42,-1000,390,-137,12,257,-710,-447,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-226,-88,-1000,-1000,-637,1000,1000,-382,-866,-203,527,72,-1000,-50,-112,529,851,1000,-1000,-366,-1000,-717,1000,-653,167,99,555,-563,-235,244,251,-1000,-875,1000,237,1000,389,-471,-223,-1000,-716,563,-190,-289,607,47,3,555,-121,330,597,318,-400,-237,1000,-382,-947,154,1000,-396,-1000,-686,-356,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-558,-828,230,480,-97,87,-197,-905,225,1000,990,1000,-551,-64,1000,-583,192,1000,456,-756,-50,-1000,803,-1000,936,-296,688,249,-1000,115,-70,234,-479,860,295,-21,-612,523,207,-112,703,-110,972,-77,236,623,321,1000,-824,-1000,-689,-722,-118,-157,935,-492,174,-830,657,-91,-164,-711,1000,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{799,-686,-88,-1000,-179,1000,-497,984,-260,1000,-553,-1000,-356,9,-616,808,998,217,-1000,-647,-1000,-272,1000,140,-744,-375,69,502,1000,648,1000,-276,15,489,680,-1000,-297,-9,-59,348,-1000,-264,-1000,-1000,1000,-197,-546,-812,548,652,223,233,728,-258,187,391,353,789,276,-317,-696,104,305,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-194,-95,-12,778,-16,-151,-511,-479,-13,1000,272,1000,-580,329,414,-363,-374,975,495,-331,515,-570,145,288,434,-414,320,-103,-885,-422,-501,825,-134,186,-576,-922,-331,465,-94,1000,144,110,987,56,131,648,-660,1000,-654,-782,-825,-421,305,516,969,240,-87,-377,-6,361,-263,-82,462,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{914,-341,383,-374,-236,-802,-706,1000,388,1000,227,-364,700,354,-157,-545,77,-278,971,-637,-130,465,-1000,937,-1000,-980,245,172,916,842,-339,-827,1000,46,209,-1000,-1000,923,474,-416,775,-543,-762,-83,-439,-829,942,-1000,53,-131,524,-424,59,1000,-565,-1000,903,-620,-1000,-331,325,-451,67,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{862,-50,-875,-566,-604,121,-341,453,120,-119,-523,-518,-996,-1000,19,527,-738,130,777,269,-1000,-555,552,217,-518,-793,497,-1000,127,1000,-290,428,584,748,-804,834,-376,-709,-124,588,60,-1000,-480,1000,-1000,-880,80,-148,227,757,626,-141,-494,1000,1000,-1000,613,-432,-607,-45,-1000,-321,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-322,117,1000,767,184,-93,51,-615,-213,913,1000,470,-560,973,13,760,-177,980,-557,-1000,878,-541,527,-50,1000,623,1000,-588,-514,-1000,-70,1000,-666,839,-807,-315,1000,-463,-650,1000,-403,514,1000,47,412,427,-808,322,-629,294,-421,-524,1000,-532,-172,1000,-602,392,-142,1000,401,-841,413,-573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(int):org.apache.commons.math3.fraction.BigFraction",
            new int[]{913,495,-456,-575,-434,-253,-924,-814,-788,-920,-394,418,-790,-887,991,-975,-467,1000,-426,598,446,-12,-137,-241,637,300,6,-250,-706,-371,-281,855,-1000,187,-568,-1000,942,-1000,-286,588,497,378,311,489,-16,519,-1000,365,-745,-690,301,658,668,-392,770,480,-775,231,1000,1000,-148,-378,-540,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,412,1000,397,-522,-1000,-187,-541,756,941,79,-597,-690,-1000,-720,-957,-887,776,-314,78,-337,-191,59,-641,-1000,657,1000,400,-537,820,-643,-569,-840,455,-270,-352,1000,210,-479,1000,-980,-1000,-1000,1000,589,-697,-1000,647,1000,1000,-1000,436,719,-814,374,493,-1000,582,1000,-349,1000,-1000,-430,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-769,757,-1000,-673,699,-1000,920,1000,-253,873,589,-167,312,403,356,-125,-1000,-718,568,982,-1000,425,1000,1000,-1000,-480,-1000,-968,1000,908,1000,-813,-924,-1000,-954,1000,-844,1000,1000,1000,504,-1000,-1000,580,1000,-594,-935,-735,-479,1000,664,1000,-514,90,-119,501,-1000,1000,-106,-48,-1000,398,-1000,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-175,882,-825,-162,968,767,-73,32,-435,-1000,700,-373,419,498,824,-1000,-585,-1000,957,100,1000,282,1000,1000,52,321,-890,-185,1000,-1000,-165,-254,-256,930,53,838,26,1000,-9,74,26,-83,182,453,-496,533,-131,-45,-787,-398,-458,133,141,-649,73,615,-174,352,-903,228,-677,799,-984,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{319,873,-428,-590,1000,91,210,510,1000,182,234,-19,-286,894,354,-1000,-1000,-1000,1000,606,-1000,890,886,770,-1000,-1000,-879,400,1000,-729,895,-1000,-1000,-1000,734,695,-224,1000,-158,1000,-56,-1000,-814,832,1000,-332,-862,60,98,1000,-184,1000,800,-331,1000,650,-884,973,520,242,-909,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{691,633,263,378,84,91,-292,-377,750,-29,-42,-179,125,-442,104,-1000,-585,-352,957,170,1000,282,-141,-32,-531,149,460,698,-266,-602,-486,-649,-429,464,53,838,1000,507,-488,401,-657,-683,-1000,1000,278,-93,21,-45,703,77,-944,479,1000,-583,976,224,207,262,490,162,289,799,-984,419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{657,202,374,58,-628,-42,-142,-822,734,1000,-243,-288,-1000,-9,238,-875,1000,-749,-1000,821,654,241,-566,247,-766,-684,1000,-804,-22,-109,36,-1000,72,-332,581,486,1000,-364,848,-50,-1000,-1000,820,969,-54,-993,-527,1000,1000,274,-890,767,716,-831,608,1000,-360,149,70,555,-65,-1000,-248,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,720,-428,505,-209,-521,711,513,-1000,1000,1000,-1000,452,-51,1000,-1000,-382,-8,-886,313,711,1000,59,771,-517,657,802,400,-637,-729,812,-591,116,705,-1000,179,256,-510,1000,-529,-56,-80,232,358,670,-184,-1000,536,98,976,-4,608,-166,-437,-156,437,-1000,973,386,-128,-196,632,-563,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{400,918,294,-937,839,135,-1000,-346,396,-1000,227,-484,-758,1000,-34,-683,-2,492,853,-666,1000,-710,1000,148,-789,545,-814,-793,1000,-17,177,-512,-1000,-823,173,-1000,374,1000,-400,715,-834,-845,-602,989,-1000,-97,400,-298,415,-732,-769,298,699,-934,-328,1000,690,-129,-42,425,385,264,-942,-270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{1000,774,1000,-588,452,899,-1000,-1000,1000,-1000,-288,31,-1000,1000,-673,-1000,-430,-674,387,-1000,1000,189,570,-865,-336,917,586,-397,1000,-318,-326,-514,-984,547,1000,-439,680,489,-1000,259,-1000,-764,-602,1000,-1000,497,1000,1000,1000,-1000,-987,-678,1000,-939,587,1000,1000,-1000,-263,468,1000,-937,-603,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math3.fraction.BigFraction",
            new int[]{479,818,106,1000,-307,265,-1000,898,-741,910,-225,-248,-800,674,547,-621,659,1000,-850,-129,204,589,-737,407,-1000,-749,-188,487,613,1000,-33,-333,893,-111,-997,1000,336,-287,-483,612,125,1000,-614,-984,-44,565,-1000,256,953,1000,1000,-926,-181,810,1000,224,134,-115,-33,-1000,-993,-1000,-239,-383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-496,-631,636,-68,662,137,-1000,-1,569,-766,-611,-363,-377,-1000,767,811,1000,468,-1000,-1000,63,1000,49,-1000,869,191,526,-82,-124,407,-465,259,417,-1000,135,-737,175,1000,328,-720,-100,-208,-326,-1000,-1000,-105,1000,-644,886,-1000,532,-1000,245,109,-17,-579,1000,767,514,-228,-610,996,-812,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{62,322,-92,-181,445,-544,-212,979,282,-322,889,-558,-666,-311,155,-270,-688,-360,-522,-357,0,984,669,-414,-447,-132,918,-471,-266,686,984,-602,-1000,-317,74,-227,-650,-6,722,607,288,613,-672,323,-10,-1000,579,200,-327,342,672,-501,-867,-484,49,-788,-141,-429,751,-133,193,215,-174,-78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{961,796,517,346,-190,489,-283,1000,-521,-437,519,-1000,597,-458,-541,210,-114,1000,331,-967,1000,-364,-1000,874,1000,-1000,4,1000,-114,-1000,-1000,-242,15,176,-667,902,920,-835,-1000,-344,-1000,-328,-212,49,-1000,1000,-788,1000,1000,-199,943,805,523,1000,-252,758,-594,1000,210,390,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{473,333,-321,593,-653,-97,-307,-1000,-177,494,201,-614,738,-82,-594,675,-23,-816,-201,444,1000,-983,-826,1000,-243,86,-155,-492,-450,-123,728,-809,-617,222,-41,-136,-962,980,386,1000,-142,783,-76,865,-912,-1000,-681,-496,583,428,583,57,-102,-260,-4,-500,71,-779,-234,-271,-992,802,-924,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{93,0,-898,-762,-341,1000,-1000,275,250,1000,-1000,-765,-694,-860,-1000,1000,-939,1000,-173,-239,1000,274,1000,450,869,456,-1000,214,-626,566,-1000,-1000,-1000,110,254,1000,1000,750,366,-1000,1000,-931,1000,-1000,1000,1000,34,443,-1000,-861,1000,88,626,-796,-1000,-287,-1000,-1000,1000,-457,1000,-1000,-957,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{553,4,164,-338,-548,-1000,1000,1000,-104,-322,1000,-161,-215,389,480,-1000,-749,-1000,-349,422,-341,237,262,667,-1000,-400,968,-164,131,-1000,1000,-148,-736,104,20,-227,-1000,-556,-456,1000,-540,807,-983,1000,61,-1000,31,680,-653,1000,-270,-199,-1000,-62,-123,-438,-141,-90,33,458,193,35,15,-702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{518,-1000,-104,227,438,284,705,746,-827,1000,358,1000,23,-864,-1000,464,267,-197,933,72,1000,-1000,-574,363,-6,-794,-567,202,-63,798,392,467,-384,476,754,463,-148,-55,-330,-345,-191,-657,513,-584,-378,1000,-203,-702,-1000,871,403,101,48,3,-5,416,-1000,-108,751,-1000,490,-395,-813,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-676,1000,-294,-361,494,311,225,44,823,-617,1000,-95,-594,-634,537,-917,-666,-1000,-1000,-1000,-657,1000,1000,-890,-1000,517,1000,-1000,-591,1000,1000,-602,-1000,-958,-405,-1000,-1000,839,1000,1000,67,1000,-1000,1000,197,-1000,1000,193,479,1000,672,-1000,-1000,-1000,640,-1000,730,-1000,641,-98,465,1000,-1000,633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-188,-1000,1000,-262,-473,-1000,1000,492,-232,-1000,1000,451,-577,1000,161,76,751,-1000,-820,444,-1000,-569,-1000,226,-1000,-936,1000,-402,339,524,994,-24,1000,-425,200,-1000,-984,980,100,1000,-1000,912,-1000,645,-512,-1000,1000,-457,147,1000,-1000,-393,-599,709,1000,235,1000,1000,-1000,-12,-1000,857,-1000,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(long):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-84,-871,1000,-33,611,-1000,-206,-163,-103,-1000,14,-1000,-103,-420,1000,305,1000,-873,-1000,-928,-171,1000,-964,-803,-1000,-650,1000,303,-245,171,237,729,800,-992,277,-1000,-242,1000,1000,333,-643,397,-1000,-142,442,-1000,802,-348,-103,-866,-115,-1000,-285,718,653,-294,1000,977,-314,-237,-1000,1000,163,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,-999,1000,924,-112,227,-797,738,322,1000,-1000,1000,134,-1000,-163,-669,432,-1000,-294,1000,-87,-1000,448,-797,1000,-454,-221,-1000,1000,-130,60,828,269,551,-1000,-1000,94,61,940,1000,-63,-1000,-899,1000,-33,-1000,-1000,-1000,-1000,482,246,-1000,-1000,-1000,1000,432,-1000,585,1000,1000,422,596,-341,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{901,-486,-1000,143,251,-413,283,-1000,-863,-16,-111,-272,922,390,-135,-783,1000,827,1000,-857,-1000,742,-1000,333,-442,-778,488,338,-455,-296,-300,-704,1000,-804,1000,-165,-407,609,-442,-698,-360,265,-932,-765,-458,-1000,-501,400,845,1000,-300,924,-39,-891,262,959,85,650,-835,225,1000,82,-39,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-1000,503,453,618,-163,-443,-1000,687,334,186,-1000,108,711,-1000,-363,-926,73,-801,-741,-89,1000,-1000,533,-746,624,884,206,-185,502,17,-843,501,-704,523,320,-1000,-856,736,204,337,307,-1000,-274,278,1000,-907,-1000,-1000,-811,-1000,-1000,790,-1000,-928,-1000,-82,-1000,-108,183,-729,-566,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-734,-970,152,545,264,528,940,-58,525,748,146,319,508,-610,1000,103,3,-530,931,1000,-515,564,-834,-720,812,212,189,389,575,345,662,-447,40,728,-1000,485,7,-521,-121,-67,256,798,-557,625,-1000,-2,-1000,-580,597,1000,-222,994,762,520,24,491,-591,-16,685,1000,61,865,-996,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{942,-517,-515,138,-905,-682,-547,-1000,87,777,-629,-265,679,288,-168,-421,-889,-139,1000,-663,-1000,-564,-687,812,-1000,1000,-989,561,-1000,-63,410,566,1000,-871,-413,-1000,-41,1000,-294,14,884,-547,-764,-341,57,373,-908,-1000,-519,-15,262,-686,73,-926,-1000,1000,-265,-450,-164,1000,-537,-342,720,-200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-95,-173,133,-122,867,-489,473,-69,605,-1000,-914,333,1000,-209,1000,-1000,-1000,1000,378,-857,-1000,-1000,-628,1000,1000,602,-1000,1000,536,1000,-109,563,607,359,-1000,-1000,-1000,-278,-773,124,827,-1000,-932,-922,-414,215,-1000,-706,-443,-703,-993,1000,-927,-1000,-1000,392,-1000,1000,-1000,-1000,-537,-1000,1000,-819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{959,747,-603,-429,855,639,784,-675,-765,-253,150,-221,922,349,-413,-762,-490,680,1000,-206,-389,756,-1000,333,-457,-1000,905,358,330,750,-959,-1000,262,51,1000,-688,-1000,72,-977,-456,-4,897,-697,-944,-708,-1000,-866,400,1000,556,-917,1000,-166,-279,262,131,4,352,-586,121,1000,82,-235,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{-217,-996,707,389,115,-307,-944,1000,862,-483,1000,298,-270,632,224,-998,1000,12,-1000,388,690,-514,493,-1000,992,-1000,1000,-1000,1000,-341,683,552,-177,1000,-1000,-344,-1000,-623,-178,466,-961,1000,-927,358,87,258,506,-1000,704,1000,712,-131,-1000,-346,1000,-1000,-839,183,461,-727,688,1000,-1000,932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.BigFraction", "org.apache.commons.math3.fraction.BigFraction", "divide(org.apache.commons.math3.fraction.BigFraction):org.apache.commons.math3.fraction.BigFraction",
            new int[]{817,-422,1000,863,-394,-1000,-1000,294,495,-632,-29,-295,-405,287,-168,-847,400,811,-813,173,232,400,-219,-400,381,-871,-294,-186,685,-63,400,-1000,-7,1000,-663,-292,-1000,-355,-1000,-446,217,642,-95,-763,990,921,10,-1000,1000,161,-223,1000,-320,663,400,-879,-1000,793,323,-1000,-771,400,-409,199}));
    }
}
