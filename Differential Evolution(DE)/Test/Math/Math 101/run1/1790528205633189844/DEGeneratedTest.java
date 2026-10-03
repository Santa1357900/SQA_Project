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
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{150,-402,454,-605,-699,144,431,-292,293,1000,-1000,-844,258,-612,686,9,-713,-374,-373,-283,-117,-344,-331,-33,-102,-245,-1,505,-445,-1000,779,-440,-756,511,477,233,381,-361,165,-615,52,1000,-1000,-962,-531,-539,1000,-123,-1000,-1000,736,-737,54,1000,-835,500,-1000,-261,-13,-1000,-256,-1000,1000,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "format(java.lang.Object,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{179,535,317,342,383,165,57,-106,779,630,-515,-612,1000,-300,-1000,413,1000,-341,1000,-399,245,-332,984,-146,-834,-321,1000,-1000,-734,519,585,-522,-1000,-123,279,1000,251,697,1000,1000,300,-750,1000,117,784,505,-70,-42,-239,300,388,-55,300,-825,-76,141,-291,-681,-1000,590,880,-568,147,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "format(org.apache.commons.math.complex.Complex,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{1000,126,-176,1000,335,232,-627,-400,-911,-474,87,379,-318,-1000,-29,-258,642,-654,-336,-1000,328,-933,58,611,-499,-583,452,610,-1000,793,-684,-439,-1000,0,1000,868,683,739,-229,-270,-483,-444,-440,54,318,157,-216,646,8,173,-78,398,305,-144,443,-1000,323,-918,-343,-562,386,-408,730,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "format(org.apache.commons.math.complex.Complex,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{1000,-155,-5,-1000,-1000,990,578,744,-559,-348,-34,1000,100,-20,-735,187,-1000,-531,567,892,-382,306,-1000,495,930,-56,1000,1000,-381,807,-1000,1000,1000,-790,775,-99,-1000,-570,779,1000,-617,532,679,-754,-43,-192,-596,-700,-111,1000,52,1000,1000,-184,47,1000,1000,1000,648,778,732,696,938,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "format(org.apache.commons.math.complex.Complex,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{930,-131,-132,-1000,-1000,173,277,1000,-1000,-532,-16,1000,-500,-1000,-1000,335,-938,-563,102,344,-30,-231,-1000,649,386,-774,1000,1000,-804,913,-1000,1000,748,-651,1000,170,-274,-807,560,335,-667,-312,335,-402,327,48,-844,-137,-484,-571,1000,1000,1000,-167,-1,1000,506,730,353,370,-661,1000,1000,-982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:java.lang.StringBuffer", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "format(org.apache.commons.math.complex.Complex,java.lang.StringBuffer,java.text.FieldPosition):java.lang.StringBuffer",
            new int[]{-1000,-47,506,1000,1000,169,-1000,-1000,-861,801,-79,-1000,-256,960,-735,-1000,261,384,567,-1000,501,735,1000,495,1000,-162,1000,-519,854,-674,-455,-1000,-351,827,-595,512,-101,1000,-295,1000,-170,619,-984,947,-199,-96,1000,765,-760,967,-589,-1000,-658,212,119,-1000,-400,1000,-61,-234,732,-1000,938,434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.String:MiwxNDcsNDgzLDY0NyArIChOYU4paQ==", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "formatComplex(org.apache.commons.math.complex.Complex):java.lang.String",
            new int[]{434,-609,87,633,-233,647,-417,-96,899,-813,31,95,-806,834,-711,-402,143,-696,-705,975,-347,-822,-356,-38,884,-747,-303,206,587,-6,-490,699,-571,822,-588,150,876,-2,-814,-139,-108,-22,530,74,-117,315,681,187,152,375,926,796,450,-528,-890,-949,96,367,152,500,669,-488,-866,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.String:KEluZmluaXR5KSAtIDksMjIzLDM3MiwwMzYsODU0LDc3NiwwMDBp", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "formatComplex(org.apache.commons.math.complex.Complex):java.lang.String",
            new int[]{-402,-875,476,-566,438,742,934,-892,193,957,847,-606,462,899,-289,-169,925,-883,331,825,-83,429,-190,-129,-656,599,425,999,-994,-317,796,-443,947,889,-705,-318,517,865,793,-845,-463,-606,523,-279,123,-306,-920,430,-448,740,419,-973,882,305,49,66,939,-180,-392,-9,763,-467,463,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.String:KC1JbmZpbml0eSkgKyAxaQ==", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "formatComplex(org.apache.commons.math.complex.Complex):java.lang.String",
            new int[]{-383,642,-399,-313,805,719,-364,-842,-611,413,46,-33,603,-977,913,668,229,226,86,-744,-747,-266,550,8,-694,367,-930,319,306,215,-208,-563,-803,-443,91,271,817,-779,-944,791,566,-770,-288,894,-225,-289,315,109,439,291,-120,-998,367,-471,473,731,652,246,-259,847,886,85,-338,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.String:KC1JbmZpbml0eSk=", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "formatComplex(org.apache.commons.math.complex.Complex):java.lang.String",
            new int[]{808,-144,333,-897,360,794,-845,656,15,-224,661,-709,-602,-60,629,-794,753,471,-384,860,11,-506,507,392,-913,-499,617,21,537,734,-184,-910,-737,-62,-292,686,16,934,-4,74,127,-423,895,604,122,860,663,197,638,117,-818,-981,417,-374,-153,547,-931,230,-997,-117,-254,-943,656,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("ARRAY:[Ljava.util.Locale;:748:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale:21:TYPE:java.util.Locale", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getAvailableLocales():java.util.Locale[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getImaginaryCharacter():java.lang.String",
            new int[]{-192,727,1000,-1000,1000,-1000,-677,198,960,563,743,923,1000,876,-800,1000,267,1000,862,59,94,705,1000,-19,-1000,-384,-663,1000,1000,231,178,286,873,-1000,264,551,1000,536,672,-1000,-1000,-545,-409,346,-1000,1000,1000,1000,-253,-1000,1000,-161,1000,82,52,317,-1000,121,896,602,-1000,348,442,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.String:aQ==", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getImaginaryCharacter():java.lang.String",
            new int[]{1000,-120,-316,-1000,-807,31,499,-437,1000,-886,-1000,-84,690,254,1000,1000,1000,-825,-1000,-938,-1000,-557,550,640,-402,854,241,-832,191,1000,536,987,-217,738,776,489,-832,-471,890,573,-644,1000,467,1000,999,-808,-205,1000,1000,-494,950,1000,-80,383,-853,522,948,-1000,-708,1000,1000,-900,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:java.text.DecimalFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getImaginaryFormat():java.text.NumberFormat",
            new int[]{560,-11,296,421,255,-1000,-506,1000,-837,-163,758,-152,1000,133,503,1000,-1000,1000,-959,-1000,572,-1000,1000,958,619,69,1000,104,-345,1000,-201,1000,-1000,-1000,780,-201,-1000,-1000,-169,1000,-1000,1000,-242,-351,-1000,171,333,-78,-739,365,-408,-686,1000,1000,400,-1000,738,-88,-877,-1000,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:java.text.DecimalFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getImaginaryFormat():java.text.NumberFormat",
            new int[]{-493,492,-936,17,612,578,1000,663,778,1000,-1000,101,-759,844,-185,-1000,-1000,-588,1,1000,-131,1000,-294,-522,753,482,-380,419,189,-1000,-1000,-118,1000,585,-538,43,-592,952,-601,19,634,-487,-53,-203,-119,-1000,-406,-593,697,1000,-514,247,-1000,-1000,-1000,1000,-71,-1000,487,1000,-45,-53,-1000,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getInstance():org.apache.commons.math.complex.ComplexFormat",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getInstance(java.util.Locale):org.apache.commons.math.complex.ComplexFormat",
            new int[]{234,-949,68,-119,-345,-751,-837,-17,669,-400,901,916,-146,373,326,181,782,716,-85,672,-344,460,-32,846,-114,-937,343,252,250,-607,959,-410,-767,0,-500,681,697,505,-679,-12,-888,777,652,-339,238,-311,-59,477,-615,-589,-634,-510,136,441,-581,-266,-215,864,-215,-102,-363,707,-255,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:java.text.DecimalFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getRealFormat():java.text.NumberFormat",
            new int[]{-506,-852,-161,-1000,312,-484,31,-174,-1000,-438,-232,82,-1000,-1000,-899,658,-1000,-120,-1000,-894,-632,610,938,1000,-326,67,73,860,147,-1000,-400,-1000,649,-39,1000,-510,-1000,1000,-133,436,1000,-1000,1000,-473,-119,-1000,-258,-97,-326,-155,26,-97,-1000,1000,-236,-920,414,-1000,-405,903,742,-263,374,92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:java.text.DecimalFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getRealFormat():java.text.NumberFormat",
            new int[]{785,343,-1000,-1000,-956,-951,-92,-1000,-475,1000,-226,-42,6,-1000,60,-279,624,-762,-383,102,403,543,1000,985,139,887,-1000,1000,-1000,668,848,-497,422,-412,992,1000,112,-796,201,-608,967,-1000,117,-575,-670,-481,-830,21,-921,-1000,-1000,-1000,110,350,975,473,723,686,1000,201,-338,830,-850,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:java.text.DecimalFormat", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "getRealFormat():java.text.NumberFormat",
            new int[]{-851,618,-473,-1000,738,-240,-577,-167,-813,100,1000,-597,-1000,-1000,-368,-87,-250,-1000,-882,658,-1000,-86,-920,1000,296,752,670,1000,-1000,-303,-412,-1000,1000,-1000,1000,828,5,-1000,-317,1000,1000,-1000,637,395,676,-88,-278,602,-1000,868,993,382,-802,1000,18,-1000,-447,-1000,165,-572,142,376,310,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String):org.apache.commons.math.complex.Complex",
            new int[]{218,618,293,-821,-1000,939,800,463,1000,-859,628,-321,463,-311,-700,-452,-327,-400,-26,-661,204,-464,376,-275,668,-935,-87,1000,-141,-398,-565,765,251,398,79,515,861,-478,-143,-755,48,566,568,-1000,380,-36,-876,96,82,-272,665,1000,-925,658,-974,942,524,-158,285,-586,817,950,1000,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String):org.apache.commons.math.complex.Complex",
            new int[]{405,391,490,-927,-1000,975,202,446,1000,-872,786,-176,27,93,-415,-863,-182,176,-135,-733,189,-831,484,-458,122,-748,917,-69,-50,750,-338,624,177,-165,-133,927,266,-251,-334,-189,-1000,-65,55,-914,321,438,-513,199,-918,-285,518,456,-159,350,-977,600,798,-250,616,-129,743,406,980,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String):org.apache.commons.math.complex.Complex",
            new int[]{-787,-113,158,-662,-703,-46,61,-545,673,-669,679,-495,865,-27,-222,595,-213,345,-492,112,1000,905,-1000,618,1000,-592,-137,446,-1000,-227,-691,962,907,-175,-372,-1000,222,-235,183,-1000,-993,-142,1000,-1000,-471,-361,-151,-49,1000,325,617,844,-1000,709,-916,-486,-104,-721,430,-850,1000,715,-271,-285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.text.ParseException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String):org.apache.commons.math.complex.Complex",
            new int[]{495,798,-501,-821,-1000,444,25,30,842,-646,1000,-271,1000,-613,-614,-349,-905,208,-1000,-646,1000,227,-345,223,122,-956,-549,126,-270,-251,200,-161,1000,-538,-95,366,994,-580,-182,-1000,-983,-703,1000,-1000,-102,-574,-1000,96,669,-198,-81,1000,18,-48,-974,673,419,-608,-448,640,1000,1000,954,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String,java.text.ParsePosition):org.apache.commons.math.complex.Complex",
            new int[]{1000,-221,-252,-844,945,102,-1000,400,14,1000,694,-583,-746,-467,-812,1000,-1000,803,986,25,-669,1000,-1000,-170,-1000,32,-1000,997,1000,680,-760,-533,1000,1000,632,363,545,-675,248,1000,274,1000,1000,-1000,-11,1000,-1000,-430,-1000,-1000,707,1000,-50,-713,781,-36,1000,667,701,-404,-1000,807,606,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String,java.text.ParsePosition):org.apache.commons.math.complex.Complex",
            new int[]{-624,654,21,-225,-288,1000,634,-838,-313,-227,-991,64,-695,-640,747,-1000,596,-586,587,-322,-382,-1000,932,359,584,-776,188,-819,-344,-47,1000,1000,-346,-274,-327,-948,-230,-198,-771,-53,-344,-663,-38,-552,-736,-1000,-359,-7,464,351,312,-1000,370,490,-704,697,162,114,850,-1000,155,-1000,-394,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String,java.text.ParsePosition):org.apache.commons.math.complex.Complex",
            new int[]{109,-504,-106,-582,1000,362,-274,1000,273,559,-677,-804,-1000,-520,-75,962,253,-465,1000,53,-378,-140,-1000,664,298,-71,-1000,-28,1000,996,458,-50,1000,-1000,-1000,1000,150,-1000,-87,1000,801,1000,945,-1000,-309,47,-405,553,-1000,-1000,663,755,-449,-846,865,364,1000,1000,665,-29,-1000,514,510,785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parse(java.lang.String,java.text.ParsePosition):org.apache.commons.math.complex.Complex",
            new int[]{-1000,-923,1000,838,-114,-25,168,866,689,1000,-1000,-268,-1000,926,-230,-826,510,-701,266,1000,-1000,692,-209,-612,1000,-218,1000,-505,-1000,-368,1000,-978,1000,649,-1000,812,-914,-673,-249,-70,662,79,-470,-887,140,-399,-1000,1000,137,-136,1000,488,380,-157,937,48,-732,741,21,-567,-432,230,433,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{449,-6,502,-193,-170,-1000,-852,492,87,-608,-1000,121,-303,-553,-269,-1000,-143,-144,-761,73,-455,80,-1000,1000,-394,23,262,-362,-464,-108,-220,44,30,-383,1000,-954,1000,983,-43,-685,-1000,357,-644,187,-1000,-468,-621,941,-722,936,632,-71,734,467,-933,-398,-602,-875,-1000,362,-197,198,166,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-146,-359,882,-9,-115,24,-1000,349,74,-1000,-1000,957,-51,152,-1000,82,138,-945,-446,558,692,-1000,155,831,-164,-948,-819,1000,910,-223,397,-584,415,-984,1000,-326,-1000,776,879,109,168,-1000,-55,203,-1000,763,-416,467,517,-314,382,-315,1000,291,529,1000,515,1000,656,65,-158,1000,177,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-613,-6,-451,457,681,124,-241,899,750,-1000,1000,-1000,1000,-553,990,1000,-143,-1000,1000,625,-1000,80,1000,-848,301,584,262,450,1000,91,-1000,-1000,-996,908,-1000,-954,1000,-1000,437,-889,-127,530,-1000,-754,-533,-1000,-746,1000,1000,936,-544,-169,672,397,301,-1000,378,260,781,-841,1000,-131,-750,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{105,-845,-1000,-979,-845,-31,57,-466,-1000,482,416,1000,192,420,-17,-15,401,-450,386,-565,980,-415,971,961,-698,-271,-542,-1000,-291,-1000,-735,188,485,-165,-564,1000,-877,359,510,436,775,-900,-101,-307,776,-671,-989,-1000,1000,-916,-958,-918,-1000,176,1000,393,1000,-1000,-714,71,243,-481,836,627}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-1000,-264,669,-311,264,983,1000,197,-625,1000,1000,353,61,92,711,58,934,-1000,1000,529,955,-990,969,-400,109,-1000,-449,-906,224,-1000,0,-430,-1000,1000,-325,-1000,-711,-28,1000,-409,827,102,1000,565,55,1000,-786,-1000,1000,-1000,598,-925,-516,137,1000,492,-1000,1000,685,-559,-1000,223,839,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.StringIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "parseObject(java.lang.String,java.text.ParsePosition):java.lang.Object",
            new int[]{-26,-707,-487,-915,-1000,336,518,-1000,-266,45,-464,1000,-252,-568,452,-532,109,-1000,532,1000,505,-324,1000,1000,-1000,-520,-586,-1000,-423,-1000,607,495,765,-201,1000,-77,-1000,800,301,590,131,-715,600,442,-680,256,-732,-907,148,-623,593,368,-719,-312,1000,-421,-896,1000,385,411,663,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID|getImaginaryCharacter=java.lang.String:OTc2LjA=", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "setImaginaryCharacter(java.lang.String):void",
            new int[]{1000,445,1000,20,847,-904,-81,-1000,96,349,-614,476,1000,958,1000,-27,-976,476,1000,569,795,1000,309,-1000,-765,42,49,-1000,528,275,651,-368,863,-625,-1000,-183,-1000,687,-575,555,122,264,440,664,-830,411,850,-20,547,754,99,-14,-432,709,923,-571,969,-834,-414,-1000,-349,207,-1000,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "setImaginaryCharacter(java.lang.String):void",
            new int[]{-3,822,334,-890,695,-908,-266,432,-107,289,153,538,67,417,148,81,-417,245,206,232,575,62,1000,-53,-345,136,312,-139,231,-833,196,-967,-124,194,245,-362,-666,-284,216,1000,-183,389,-69,254,-20,-242,435,316,335,353,215,654,180,10,42,-703,229,-313,205,-861,959,55,139,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "setImaginaryFormat(java.text.NumberFormat):void",
            new int[]{668,-486,328,-1000,-1000,-75,304,974,-446,402,-1000,-1000,-330,556,-135,539,3,769,573,-529,-517,360,-980,755,52,1000,832,591,-628,-804,655,75,-831,931,82,542,-156,-520,315,884,-358,-10,-1000,-422,446,331,917,610,-1000,1000,-154,-390,611,-1000,-1000,556,-1000,72,-706,93,-1000,-185,1000,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "setImaginaryFormat(java.text.NumberFormat):void",
            new int[]{1000,-269,214,1000,-779,-75,669,821,300,-815,1000,1000,728,-758,987,-1000,80,973,-368,300,79,100,789,-1000,1000,-1000,234,846,388,722,64,-938,587,1000,859,-36,45,-520,-113,-271,815,-244,138,1000,244,-503,-1000,-871,-1000,864,1000,-390,-670,-1000,209,708,-1000,-684,749,126,617,116,-20,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "setRealFormat(java.text.NumberFormat):void",
            new int[]{-842,985,1000,-739,224,709,-941,1000,-885,990,2,-1000,1000,-921,-1000,-1000,-359,244,-689,1000,-1000,-1000,625,-949,-677,-622,-239,1000,-531,1000,21,-801,-472,-1000,1000,397,-1000,-1000,489,819,-550,851,-172,1000,-941,989,-1000,-366,1000,1000,214,490,91,227,1000,1000,-347,57,-45,29,-12,-795,635,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.complex.ComplexFormat", "org.apache.commons.math.complex.ComplexFormat", "setRealFormat(java.text.NumberFormat):void",
            new int[]{-523,-234,742,851,-386,-1000,682,-997,-80,-56,1000,1000,629,-470,-564,-844,-346,-316,-518,-319,-1000,-956,58,-1000,550,-594,-1000,-446,159,-108,1000,-88,-195,-791,-951,1000,-1000,805,-959,640,346,398,483,-1000,-625,-1000,-601,-1000,1000,130,-1000,1000,24,-1000,373,106,-733,-505,1000,-86,195,445,-149,551}));
    }
}
