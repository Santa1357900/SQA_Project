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
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(double[],long[]):double",
            new int[]{-1000,1000,1000,-1000,-628,464,754,-866,-1000,1000,-1000,408,-1000,1000,1000,1000,670,-446,-182,491,305,-1000,-1000,-487,-335,850,1000,-1000,-483,-597,-1000,-1000,1000,627,1000,-666,1000,817,1000,-958,239,-870,1000,970,1000,-1000,1000,-801,-978,366,-1000,1000,-1000,644,-1000,-266,-199,-596,-574,-823,1000,1000,-1000,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(double[],long[]):double",
            new int[]{400,-132,-400,400,514,-599,303,-845,172,776,400,-33,400,-157,-400,-195,1000,-914,75,-861,1000,460,-313,-535,680,1000,-400,400,458,-1000,112,271,97,-181,397,440,580,1000,-366,-481,-470,-1000,1000,1000,626,32,-191,-643,422,142,-869,-122,-1000,-546,291,190,-347,1000,-12,-295,208,123,332,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(double[],long[]):double",
            new int[]{-399,-40,-276,427,996,-352,711,344,-958,956,-365,671,8,678,-858,-441,937,-10,316,268,-795,-983,-92,-122,-711,-835,-561,393,-668,200,-642,-737,-943,-931,-741,-401,-146,462,377,-579,-542,783,-952,169,-478,-248,-350,-581,626,-757,651,-777,-244,-920,-188,-148,163,541,350,823,626,61,927,-939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(double[],long[]):double",
            new int[]{629,286,674,-206,-269,986,183,-639,-503,613,341,485,747,297,-761,-227,-377,-49,978,208,951,798,-882,221,769,141,-200,-433,-503,456,-923,677,-791,17,-100,413,745,162,364,-766,970,-954,320,653,-583,-728,266,-422,733,-877,556,-372,889,314,373,837,954,-864,689,303,382,679,-529,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(double[],long[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDM3NUU5", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(long[][]):double",
            new int[]{-1000,1000,-474,287,-1000,-1000,-574,-585,46,-1000,-1000,-674,-59,1000,-1000,-30,129,-1000,-1000,1000,-1000,-856,335,-1000,1000,220,1000,1000,-1000,428,574,-789,-906,-1000,1000,-520,-186,-1000,-1000,-1000,-607,-431,1000,1000,1000,-77,-1000,897,-438,-312,-1000,1000,1000,-1000,-1000,1000,-523,1000,317,-1000,-288,-550,484,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(long[][]):double",
            new int[]{532,1000,-1000,-544,-1000,1000,-789,-399,60,-592,-1000,154,-440,1000,-1000,-1000,1000,-1000,-1000,552,-1000,-1000,-467,71,-406,-174,1000,536,-1000,-714,122,236,-376,-42,109,978,521,1000,53,-1000,1000,-28,142,1000,296,119,753,365,-1000,-51,55,1000,448,457,-354,1000,-1000,-346,-191,42,-193,-79,128,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(long[][]):double",
            new int[]{474,-204,294,-829,241,247,442,153,698,-993,-331,176,-927,260,56,-464,-251,-724,-690,-83,-1000,-865,-493,963,46,229,60,-389,687,-241,600,490,341,-533,694,-309,-298,-697,-629,-456,438,402,250,606,342,772,-220,-947,-809,-534,530,879,179,-380,797,855,-731,-282,-127,106,32,-263,-743,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(long[][]):double",
            new int[]{-411,206,186,-683,-603,-377,-729,-492,287,46,524,614,498,91,119,298,966,485,-857,167,-1000,966,-652,-966,714,962,-942,-247,-788,682,-858,942,-925,-626,-793,-159,-936,-108,-822,915,200,-577,-688,388,-664,-749,329,-146,-792,-217,864,340,536,704,-308,-254,255,87,-697,-502,144,575,-674,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquare(long[][]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Double:Mjc2LjkwMDM2OTAwMzY5MDA2", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{-187,1000,364,94,873,-1000,-14,1000,-252,996,-1000,-1000,786,-626,-58,-389,1000,-608,-191,-859,-1000,464,203,-1000,985,-682,307,-996,-642,-149,-951,54,129,146,1000,1000,-202,203,-835,-523,481,163,-365,385,135,917,-64,393,454,-619,-1000,479,-1000,1000,-579,930,333,-445,1000,1000,-444,-853,1000,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{427,343,-1000,-1000,1000,-1000,-81,-760,128,-601,-1000,-174,524,582,-58,1000,-1000,455,-233,1000,1000,-1000,919,-535,1000,763,599,1000,699,1000,-107,-462,-740,-1000,475,-1000,1000,1000,1000,-1000,-1000,-1000,-1000,-1000,-1000,378,138,-1000,-1000,801,-346,-939,-1000,-515,-1000,-1000,-84,1000,-534,-1000,943,600,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{-579,932,-844,-294,644,-580,44,797,751,368,-181,-470,694,487,719,-5,691,-474,132,306,105,670,832,-315,978,-598,571,-994,-145,-707,-930,202,-224,638,-244,-243,234,558,738,-319,-284,-704,459,-196,-953,757,217,11,160,55,327,139,159,807,-932,640,558,235,-215,825,482,743,-522,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{-1,271,-1000,2,1000,-83,-665,355,-1000,1000,-1000,-1000,607,-1000,107,364,400,-725,1000,-1000,-1000,886,504,-765,1000,-1000,-733,-1000,-1000,269,-1000,1000,-209,-1000,778,1000,-1000,1000,-645,-1000,1000,-1,355,256,309,1000,292,1000,-550,562,-1000,1000,-1000,1000,-404,1000,1000,-720,629,-1000,-1000,-1000,449,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{500,524,-1000,-1000,643,-1000,-492,-275,296,-1000,487,249,70,354,985,521,-1000,-1000,-325,1000,104,626,911,-1000,87,-575,73,1000,288,-1000,1000,-86,-1000,-1000,-27,-1000,1000,-1000,-320,938,-708,195,556,-14,69,-1000,296,-226,-190,-727,-600,-1000,562,100,-1000,-916,158,775,-330,-1000,810,-739,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{166,-770,665,245,-626,48,-815,713,-274,-934,219,347,169,-963,145,-487,-504,-961,-996,-835,-391,864,-400,-864,369,-441,944,222,986,111,842,-539,-912,712,761,903,-448,774,246,-741,-992,935,112,-673,488,651,-626,-168,161,663,485,440,267,608,788,-929,-549,372,-652,-625,-477,-627,-726,-753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{-689,524,-174,69,-860,352,559,-614,-776,-964,-488,497,611,-230,774,611,-223,-166,-8,242,496,-188,226,-526,-779,-851,-124,523,-425,-306,113,-342,-324,-725,-414,359,-964,-891,290,767,-448,-88,440,-77,-296,-80,-36,-934,499,-253,-236,378,-70,-29,-662,-86,-250,-336,490,225,229,-312,-85,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareDataSetsComparison(long[],long[]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[]):double",
            new int[]{-1000,1000,-1000,-1000,-1000,-1000,-1000,487,-1000,-794,-1000,-20,-1000,742,670,-290,-938,1000,-600,-1000,-1000,-770,1000,1000,-966,-1000,-970,880,-524,-420,190,1000,1000,1000,-1000,807,1000,1000,1000,-293,-764,-1000,1000,-904,-414,-855,-1000,-1000,279,-377,-1000,-1000,699,448,325,1000,242,1000,-1000,-1000,-1000,1000,1000,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[]):double",
            new int[]{-955,1000,-1000,-1000,-894,-1000,-362,339,-1000,-600,-1000,186,234,-307,832,-266,-938,630,336,-1000,-1000,-550,-400,1000,-809,-931,-300,715,-470,-351,-418,350,1000,1000,-627,955,1000,-107,307,-193,-764,-130,30,-243,36,-855,-1000,-1000,374,162,-580,-886,432,-513,-78,-400,213,-134,-1000,-562,-1000,951,1000,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[]):double",
            new int[]{-1000,1000,90,1000,-731,967,617,1000,-1000,-912,-1000,-1000,1000,-564,-445,-1000,-1000,607,-946,511,1000,-1000,-327,-707,-277,-810,31,1000,-254,1000,-161,1000,1000,678,-1000,-24,898,-439,1000,-1000,966,1000,-595,-1000,1000,-609,1000,-1000,420,-104,-386,-1000,1000,1000,1000,-852,-1000,-1000,764,-1000,1000,-547,1000,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[]):double",
            new int[]{-482,518,90,543,-128,-17,-7,34,170,-820,-738,-391,977,-621,334,-817,-881,973,-241,454,903,-657,-327,-132,-370,-831,776,602,-497,-466,631,498,312,860,-623,881,125,532,857,-681,619,936,580,-543,-344,-449,940,-662,253,-291,700,-172,173,713,-156,-864,33,-906,619,-216,47,972,820,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[],double):boolean",
            new int[]{-1000,-1000,1000,140,-134,-1000,88,-962,-1000,-386,437,-1000,-444,-1000,-557,1000,-1000,615,-701,-224,1000,1000,-1000,-17,1000,-123,-22,-838,-783,-613,-143,985,236,-69,-921,1000,194,-1000,1000,-1000,-1000,392,-994,1000,-1000,218,-341,1000,-877,170,-537,-448,333,42,1000,-1000,-1000,641,-1000,1000,1000,252,1000,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[],double):boolean",
            new int[]{847,68,-1000,386,33,903,263,1000,380,-251,-569,846,67,326,-161,-546,1000,47,419,1000,-239,-702,218,-416,193,790,-317,-172,-32,-545,-159,622,92,1000,-1000,-1000,43,252,240,497,725,97,-125,-607,814,990,652,-751,-696,-995,-119,-1000,-807,236,895,-38,1000,-85,404,-1000,43,915,-1000,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[],double):boolean",
            new int[]{-687,-910,1000,-1000,-580,35,115,-1000,-168,-829,-965,-1000,-548,662,-454,853,-1000,-500,-876,-182,-189,321,-845,-616,1000,90,347,-1000,-739,40,-151,324,-1000,-1000,-444,1000,627,-613,109,-287,1000,-1000,-367,667,-1000,-377,-886,794,-249,894,-585,-617,1000,784,-1000,-755,-1000,-52,-1000,834,1000,-1000,1000,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[],double):boolean",
            new int[]{879,-682,-975,355,311,74,541,791,175,520,-110,545,-623,646,356,-912,822,262,556,688,-289,598,78,53,-156,519,-544,830,-197,-137,-721,132,413,552,-556,-752,315,454,607,978,-354,-238,-247,-311,835,618,444,-898,377,92,-184,-955,29,249,658,8,374,-982,33,-842,-84,-158,-202,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(double[],long[],double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][]):double",
            new int[]{666,344,310,-1000,-1000,1000,-1000,-129,-1000,-1000,-71,-1000,1000,-1000,1000,-1000,1000,651,770,1000,-36,1000,-519,-1000,-151,1000,1000,1000,-384,1000,-854,1000,704,-1000,-475,1000,147,643,-1000,1000,-125,-530,-1000,1000,827,772,-60,297,378,1000,968,-1000,27,1000,-1000,-867,1000,-535,898,-347,-1000,-258,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][]):double",
            new int[]{1000,-368,451,841,-1000,1000,37,-86,-1000,-1000,115,-400,400,-400,1000,271,-196,535,-193,-106,1000,656,-1000,-1000,-1000,-379,-452,400,1000,775,-451,481,775,-997,-1000,1000,-1000,279,-1000,297,548,27,1000,400,1000,326,-251,869,565,3,378,-30,1000,-915,-391,-307,400,-674,69,1000,-400,-1000,-832,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][]):double",
            new int[]{-49,-482,638,-315,-547,-810,604,185,963,-497,464,898,-581,682,-237,-935,-214,952,-31,-224,-521,-512,722,379,-622,-224,288,-681,-468,636,-356,670,23,974,605,-678,-444,-496,202,897,317,118,127,789,-615,-618,-550,424,549,697,-916,-150,-194,97,-526,21,946,-661,864,-170,708,979,158,-200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][]):double",
            new int[]{-302,-726,-434,66,70,372,253,-781,132,-679,105,-412,-2,-779,-919,280,-766,320,518,345,176,-386,469,779,67,352,695,-856,-448,-954,106,400,-677,457,448,-663,885,145,-177,-999,-645,-356,208,-67,-61,-868,587,666,-654,485,-485,961,748,79,-6,-457,103,-642,-629,-258,683,256,-353,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][]):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][],double):boolean",
            new int[]{-700,-720,695,-478,1000,1000,-373,-408,1000,-203,1,-45,804,1000,1000,-771,-415,-1000,-1000,1000,774,-448,937,-449,-28,41,448,-1000,785,-844,262,-468,-1000,814,166,186,831,1000,-395,68,1000,1000,394,-869,1000,-175,-251,870,-1000,850,983,-1000,-1000,1000,1000,-249,-1000,10,-128,-200,115,-1000,-97,-471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][],double):boolean",
            new int[]{593,-1000,978,-683,-768,-299,-1000,-1000,921,479,-420,-558,-841,22,-354,1000,-1000,937,-625,-1000,1000,882,386,212,-917,1000,-762,-509,341,-908,-700,-998,-1000,538,-1000,-405,-852,-298,-759,-465,1000,193,-178,-869,1000,1000,480,1000,66,1000,1000,-1000,-551,-111,-199,1000,133,-670,646,1000,-782,87,1000,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][],double):boolean",
            new int[]{1000,-856,1000,-1000,120,1000,-830,-500,1000,-765,-1000,888,1000,-738,-544,-245,1000,1000,-862,-1000,678,589,271,327,-942,-21,-822,-89,-574,-936,-790,-686,-418,-32,-1000,-584,1000,740,-123,-608,-129,1000,1000,492,160,-983,1000,-678,-1000,-1000,1000,22,-307,1000,1000,659,-1000,-291,65,1000,-847,-377,595,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][],double):boolean",
            new int[]{-715,900,637,109,-321,-29,-949,-848,-809,174,310,-762,147,521,-234,526,28,233,-746,-706,-664,319,296,-370,-869,743,-858,608,-309,-773,-615,344,455,-833,-911,-536,-347,-434,-802,796,-160,-578,293,-636,934,580,-296,-864,-709,709,441,410,-148,-242,-562,187,475,-976,-790,-551,-838,-215,187,-134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][],double):boolean",
            new int[]{1000,224,1000,-977,-294,1000,-605,-650,210,-1000,-600,1000,779,-1000,376,-918,1000,-1000,-694,212,-22,-1000,-828,1000,-62,15,-1000,-1000,-1000,-1000,-1000,-1000,459,208,-465,-1000,1000,-1000,131,623,-1000,1000,303,-1000,-177,-1000,-1000,419,-1000,-1000,1000,1000,1000,1000,1000,-1000,-1000,-588,940,-623,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTest(long[][],double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk4NjQ0Mjc2OTc2NjM5", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{962,-400,800,692,-1000,-827,1000,-365,-82,-140,-501,625,322,-511,-225,290,374,-776,932,1000,183,-363,1000,-557,-625,-487,893,-479,-307,-1000,-739,54,799,-8,1000,1000,-75,-654,115,714,-890,-673,206,-1000,-293,-273,-566,-287,656,-1000,-490,-445,1000,835,819,-23,-1000,335,572,-731,-1000,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{-24,20,1000,788,-1000,-1000,422,-1000,-658,569,-1000,1000,464,1000,-466,514,151,1000,-349,110,438,709,1000,314,964,-145,493,313,-762,-288,-1000,-104,1000,-609,1000,731,1000,1000,949,1000,-1000,395,1000,-1000,235,245,-656,174,985,-807,-1000,196,156,1000,1000,-1000,-704,580,-385,-662,-649,15,1000,-619}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{878,-1000,205,698,-999,-1000,207,-395,-1000,820,617,400,1000,1000,-88,-445,535,-12,918,-37,55,1000,808,-488,-424,-1000,-362,228,1000,1000,1000,1000,-667,-60,-76,353,85,513,-481,-1000,-1000,322,494,-841,901,-340,-336,481,549,-1000,-958,447,100,1000,-716,-521,-506,-729,204,-283,1000,432,1000,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{1000,-400,800,698,-1000,-1000,1000,-365,-82,-140,-275,1000,283,-201,219,346,-42,-776,1000,1000,-19,120,1000,-895,-643,-1000,68,-288,318,-1000,-1000,404,445,-1000,1000,353,-75,-654,85,399,-1000,-198,-658,-1000,1,-547,-851,-48,656,-1000,-613,116,1000,1000,677,-23,-1000,1000,-105,-105,-1000,-813,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{210,-1000,205,698,-1000,-1000,-225,-383,-1000,1000,202,400,576,-15,-21,-35,247,-263,903,845,1000,3,1000,-18,-915,-267,181,727,108,342,-400,436,81,-1000,283,353,297,911,-957,1000,-1000,322,494,-1000,-1000,-90,-593,659,1000,-1000,-954,398,1000,-210,-797,-363,-5,50,38,-258,561,197,1000,-478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{-206,-94,545,329,21,469,887,639,813,329,144,392,152,-215,-548,-46,-853,-709,-733,614,-319,-370,-399,35,-704,-379,961,-160,-487,27,-576,-630,-311,-903,188,729,874,-937,369,-603,-882,-183,-191,-139,-782,6,242,-975,-13,788,277,-563,770,-342,340,-176,185,881,3,-175,-191,-679,-102,-632}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{1000,1000,-685,-232,-917,-1000,197,45,-1000,791,1000,463,1000,1000,-167,-1000,1000,-166,271,-326,471,-1000,262,-934,-1000,1000,-88,-578,1000,-930,1000,1000,-1000,258,-389,34,-1000,-340,-93,-1000,-254,-350,865,-231,-1000,-955,772,-76,-931,-922,659,914,744,1000,-667,741,628,-1000,1000,-1000,107,679,916,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[],double):boolean",
            new int[]{1000,-458,-1000,1000,1000,-1000,445,1000,1000,289,-1000,-1000,1000,1000,-1000,1000,359,1000,-548,535,-1000,-1000,775,-1000,1000,458,-399,216,-1000,1000,-1000,-1000,-204,-1000,1000,1000,-1000,1000,631,630,-1000,-1000,-1000,603,1000,260,-1000,-1000,-211,760,-1000,1000,-1000,-343,968,899,-858,1000,1000,448,1000,-1000,-1000,303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[],double):boolean",
            new int[]{648,950,-825,-182,-581,231,70,-1000,748,1000,263,1000,-343,-22,540,-1000,1000,477,-1000,-1000,743,639,582,6,-497,998,1000,-998,1000,-232,553,245,628,-53,505,1000,-398,338,811,438,184,264,-906,1000,-1000,37,338,-1000,-1000,-591,400,-201,266,-935,249,243,-836,-829,-1000,99,187,1000,815,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[],double):boolean",
            new int[]{939,926,-510,-769,-671,-768,914,964,437,691,88,833,-75,520,-585,-582,-103,810,211,-989,17,-53,-21,-560,82,251,377,877,-809,-900,341,-813,-639,176,-23,565,-322,952,671,452,894,12,-815,984,-255,-565,421,-366,-779,-933,-987,-906,-483,-767,-79,961,435,206,830,-696,-320,579,273,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "chiSquareTestDataSetsComparison(long[],long[],double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "org.apache.commons.math.stat.inference.ChiSquareTestImpl", "setDistribution(org.apache.commons.math.distribution.ChiSquaredDistribution):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
