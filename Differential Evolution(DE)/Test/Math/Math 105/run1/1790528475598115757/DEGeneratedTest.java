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
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "addData(double,double):void",
            new int[]{53,-191,-801,878,10,-490,-342,978,-158,-220,-688,-456,767,-347,-503,191,-569,213,-688,82,377,-279,557,98,-263,802,382,703,879,-377,723,883,165,229,758,-290,430,931,864,-29,-713,55,-142,628,973,-787,-346,-161,68,-243,903,761,-855,108,-341,430,150,231,679,-372,-818,713,-445,845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "addData(double[][]):void",
            new int[]{799,540,-171,-497,-767,-514,-248,902,-12,506,404,388,-133,710,-26,518,353,-45,449,520,446,-95,-1,-415,965,-361,397,888,-630,871,-73,-314,-778,272,89,-756,-457,-668,-676,382,551,-233,997,-47,-501,521,-98,752,-300,-866,-341,-36,-822,-597,575,-401,879,-897,-255,104,-135,-160,494,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "clear():void",
            new int[]{-534,317,187,327,241,242,547,188,-680,-765,-116,310,-487,849,-811,222,170,426,332,579,145,-902,908,10,143,-344,735,-651,-170,-289,496,-85,816,633,997,-13,-711,-889,-714,-645,-564,-117,-593,-45,-946,-848,995,972,-214,518,78,-483,252,900,-510,-987,-957,807,-901,749,-5,-391,704,-273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getIntercept():double",
            new int[]{161,-65,859,310,1000,-257,120,-670,676,-1000,646,-390,1000,-1000,-63,102,1000,-75,-792,-554,-524,572,-694,-248,-224,477,-141,1000,881,-286,1000,339,-859,-1000,868,544,-377,-1000,614,950,-367,-1000,-1000,1000,267,201,74,578,-78,112,1000,-388,-1000,-234,302,-352,1000,-380,1000,438,389,1000,-1000,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getIntercept():double",
            new int[]{-199,-602,57,1000,-390,619,-400,-241,-220,1000,-1000,-342,583,84,1000,-206,-1000,1000,1000,780,400,1000,-1000,-850,256,-170,1000,-1000,-565,-195,-1000,-241,156,703,784,176,226,-430,480,-400,937,-302,-68,-1000,-10,11,-1000,-776,530,-539,737,-717,1000,1000,-1000,464,-1000,1000,1000,-384,-50,-1000,205,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getIntercept():double",
            new int[]{599,114,-321,185,361,-358,887,909,-860,118,101,-8,-1000,354,-624,-1000,220,190,-177,-190,-441,456,800,-370,-373,398,1000,442,937,-889,-187,222,509,1000,-737,319,-267,-596,-1000,381,1000,133,721,631,-5,-532,626,514,-537,516,-116,1000,620,-629,131,610,172,-161,-885,13,-269,211,24,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getInterceptStdErr():double",
            new int[]{530,818,-351,-805,477,473,142,-741,-210,1000,-613,-835,-990,382,-575,-662,-283,-1000,-1000,-884,-294,508,488,-887,475,-1000,1000,114,460,1000,-1000,264,407,-1000,348,-105,1000,-529,1000,-1000,1000,430,-434,1000,449,-306,-352,551,994,1000,328,485,-413,-297,1000,-521,-1,473,-1000,1000,-444,-773,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getInterceptStdErr():double",
            new int[]{-170,-187,513,-350,-730,-163,569,305,-284,6,932,-822,-386,-831,132,26,923,-751,-770,-320,190,-710,-292,895,440,374,301,764,-784,-742,-275,538,-100,-707,443,66,561,-317,-850,223,-990,469,493,-325,502,759,-317,138,-457,-320,-855,851,-317,-742,84,456,804,55,-10,649,446,-280,-563,-955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getMeanSquareError():double",
            new int[]{-273,-276,-506,947,789,-342,-308,-275,-642,673,783,-885,828,845,462,-195,655,974,272,-364,672,356,-99,-567,-250,-517,601,-733,-154,-956,-758,-474,548,345,-910,710,829,108,568,449,-52,507,959,-36,149,-769,333,891,162,418,-223,-804,353,-655,-546,-224,-168,-68,-34,-730,821,302,-954,-431}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getMeanSquareError():double",
            new int[]{598,-560,474,125,130,290,-814,659,535,703,-859,-219,-617,-718,625,-281,-644,-883,16,445,216,-698,43,463,-195,376,993,-841,-610,-674,620,-644,682,-484,-901,207,-471,757,530,-790,438,615,217,-207,846,11,-780,-365,384,195,552,729,243,232,758,-994,-814,316,633,-709,-234,398,-672,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getN():long",
            new int[]{574,25,954,-888,1000,-1000,-341,-677,244,-83,-242,443,-683,608,702,-227,-229,1000,-1000,-199,-772,77,833,292,-487,-491,-218,-1000,-403,383,-758,866,681,-544,-1000,502,29,1000,142,-153,619,-187,-548,-744,1000,779,34,-117,659,159,-1000,93,407,-584,1000,14,414,401,1000,877,-275,-469,253,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getR():double",
            new int[]{-483,-729,-246,1000,391,812,802,-821,1000,-460,1000,666,-148,-530,626,249,-905,553,195,167,711,395,-40,-462,-751,366,400,586,116,953,-1000,-685,-349,739,-662,-272,535,210,1000,949,410,524,400,1000,-476,281,-820,551,412,-798,-487,-720,556,-233,1000,-853,67,-370,-161,-1000,1000,97,514,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getR():double",
            new int[]{-117,134,217,-272,410,363,289,-159,-689,66,717,-856,-195,-564,740,949,-373,-278,130,-232,-780,85,-547,60,993,-165,-116,79,166,81,607,-534,229,597,545,-581,-584,848,-327,807,753,550,-514,428,-786,862,324,760,-672,407,-778,40,-845,629,-315,-771,-15,733,387,578,-650,-586,-844,847}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getR():double",
            new int[]{1000,-144,-284,-53,746,-128,-845,-395,1000,-1000,883,1000,411,260,-115,215,229,777,384,1000,322,1000,148,-394,428,831,84,-390,1000,231,181,-672,-1000,1000,-1000,-1000,1000,-554,1000,195,471,843,-1000,1000,-46,-467,-1000,507,532,184,1000,-1000,647,-478,-233,58,229,1000,-1000,66,-333,475,783,460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getRSquare():double",
            new int[]{-555,-701,-211,-551,-863,334,-829,-80,-583,44,-549,-814,362,268,638,478,-778,-764,-848,-644,-754,11,302,406,-140,671,-313,451,-399,250,430,-257,-150,-986,292,-213,-551,41,69,528,-459,-740,418,454,633,-864,275,853,-200,976,358,-65,-822,510,-89,-181,-523,802,-209,-507,91,-164,307,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getRSquare():double",
            new int[]{-672,-580,354,415,-574,985,722,-420,22,-968,-41,838,-689,-898,217,473,-259,-371,464,26,-152,404,857,181,-911,829,-445,-250,-908,-742,-571,964,399,284,231,535,818,220,166,984,-235,214,-446,568,616,-539,243,845,13,-221,-161,341,-672,-536,-594,-884,-989,782,-704,127,-532,-283,21,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getRegressionSumSquares():double",
            new int[]{-307,-554,-316,305,-735,763,528,-765,-1000,-59,-1000,146,-288,-939,607,205,-211,-173,-51,-850,567,1000,219,1000,-762,-412,226,1000,506,781,-799,38,-1000,284,661,53,-2,-397,-818,1000,1000,-1000,381,799,1000,369,548,-52,-430,483,-1000,-580,-241,-589,329,1000,-641,580,-508,1000,320,-19,-214,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getRegressionSumSquares():double",
            new int[]{1000,827,-158,-1000,1000,-514,-1000,589,1000,-422,942,1000,813,1000,-1000,-566,-204,-521,912,-494,-366,-1000,1000,740,1000,-1000,-1000,-1000,-346,-975,1000,-1000,-582,556,-477,-1000,702,637,-1000,825,580,120,-918,318,4,663,731,1000,-720,-357,-197,-399,-891,1000,1000,-433,-1000,-1000,799,956,68,-215,22,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getRegressionSumSquares():double",
            new int[]{-29,-290,653,460,-550,-445,822,430,-146,784,-77,-792,490,-383,1000,-631,-640,-273,-457,558,-387,542,729,111,9,-331,-1000,103,593,120,346,-863,158,-155,-734,169,-92,399,559,-299,-298,35,257,130,-124,-509,-127,-655,-751,676,310,-528,-522,-1000,842,-146,793,-437,-615,108,-729,493,-455,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSignificance():double",
            new int[]{-596,479,-502,554,-890,-291,568,-813,-119,387,-193,-281,957,-486,372,-932,643,-247,166,-380,556,-849,-539,-925,203,-699,175,-801,119,319,35,-867,-855,-989,184,-894,-802,-488,-591,423,863,-710,78,-602,-493,-62,163,558,240,-579,215,-122,-968,588,102,72,562,-354,-559,-958,949,45,180,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlope():double",
            new int[]{178,680,4,369,1000,1000,1000,917,-1000,702,370,83,849,1000,1000,-304,-214,58,-652,20,-643,-532,-1000,109,-456,-1000,-1000,933,1000,-458,1000,-996,1000,884,1000,-1000,-984,594,-599,-588,-1000,-932,1000,-159,822,-783,-553,-952,-1000,-1000,399,-1000,518,-1000,456,-1000,805,-642,-1000,-67,-889,-495,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlope():double",
            new int[]{498,1000,594,-905,1000,-895,1000,675,-1000,-123,662,700,773,1000,670,-1000,650,54,-456,517,-460,-214,-1000,5,588,950,-780,-163,1000,-11,1000,244,436,603,1000,-629,-631,-18,-592,-588,-322,208,1000,-742,-527,-523,-1000,-567,-1000,-1000,1000,-562,518,-1000,589,-1000,605,823,-335,-241,-889,1000,-236,977}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlope():double",
            new int[]{-833,570,907,-839,344,-1000,-1000,-1000,15,-617,57,32,-313,848,-276,60,748,-574,-617,763,-966,-359,947,-602,598,-203,-701,-754,607,559,1000,1000,-1000,987,997,185,-472,-454,-1000,281,1000,1000,1000,1000,-539,-589,178,-59,-857,-155,518,832,-791,-333,-1000,434,-1000,436,-177,-627,103,-134,-174,111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeConfidenceInterval():double",
            new int[]{-846,-11,-211,-280,883,-979,688,-616,-483,44,496,-988,-606,-247,591,-551,-939,-183,-735,-166,-762,-962,745,-947,253,-406,201,-609,-450,597,-730,478,-44,-704,426,702,-621,-245,-862,-364,337,587,153,687,-680,-564,492,199,-657,273,774,-484,268,302,339,152,473,306,314,-854,264,-437,231,-89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeConfidenceInterval():double",
            new int[]{-391,529,-131,-739,787,-1000,536,-407,-1000,1000,830,454,-881,-648,-568,-1000,-1000,-100,-86,-694,-864,103,347,159,725,-1000,-152,-252,465,614,-481,617,271,22,348,730,-79,-696,-338,62,-607,1000,-114,159,-551,477,-529,-74,289,-268,-354,-75,-177,355,-206,655,-1000,435,177,-220,-421,-626,1000,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeConfidenceInterval(double):double",
            new int[]{-797,-881,-316,-741,802,-395,-707,-34,586,606,604,197,-802,349,577,-294,-561,37,-791,-452,774,-247,919,-789,615,-883,-739,473,464,908,-749,545,-499,764,-497,-406,-20,760,556,-890,-50,-819,353,-252,-779,-879,601,222,259,679,948,677,738,-524,-642,500,854,-41,819,10,181,-446,646,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeConfidenceInterval(double):double",
            new int[]{-797,20,354,-341,300,-638,-42,-1000,586,257,-619,-237,-178,865,-888,395,-567,1000,-236,197,1000,-922,575,457,-183,760,1000,2,-1000,498,-175,-1000,-698,-778,-314,431,229,-185,1000,-1000,213,838,772,336,465,-238,381,-1000,-338,-1000,483,130,-562,-1000,179,-497,-27,-41,343,227,-1000,-789,-244,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeConfidenceInterval(double):double",
            new int[]{174,1000,-556,369,-222,-264,536,-796,193,212,-1000,1000,101,282,-635,-677,616,434,-782,188,821,-400,-849,-828,-626,337,-837,1000,-96,264,-501,-471,-224,-308,227,-341,-665,-701,-523,919,-521,-635,305,-661,-621,466,460,-19,-969,-1000,359,-678,45,142,-861,-994,-390,961,-256,-376,1000,981,544,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeConfidenceInterval(double):double",
            new int[]{-149,-908,-991,-618,138,146,37,773,-848,918,-100,-950,-722,-859,-446,-266,239,-907,-449,850,-783,268,219,798,-661,-490,834,-147,16,498,-27,32,112,319,147,-431,-269,617,-986,632,534,273,-984,43,-81,-94,-903,765,-859,-369,347,711,-471,439,-178,-984,286,337,-362,352,78,955,-541,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeStdErr():double",
            new int[]{-911,-155,982,-445,-692,-957,-681,-499,-301,-153,-440,-367,903,-477,-271,1,-752,-844,738,387,-267,816,-436,516,636,-183,-579,-296,-520,-558,-90,44,-184,-697,-423,922,-122,560,-495,-365,-443,-346,-369,-807,-696,904,-686,18,-375,-658,366,-839,607,520,211,931,-391,-363,-839,360,-381,338,864,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSlopeStdErr():double",
            new int[]{-874,-228,927,-758,351,-877,-568,28,619,-307,591,-744,-668,-148,-825,-729,489,-311,477,891,84,35,573,-650,772,675,439,95,-804,-959,-942,975,107,-563,-901,-21,-23,-45,-35,-265,-800,-385,-967,-765,170,9,799,-75,526,-284,-735,-833,699,129,-582,-611,493,-47,920,-247,928,628,-759,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:OC41MDcwNTkxNzUwMDQxNjZFMzc=", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getSumSquaredErrors():double",
            new int[]{-640,173,603,224,-189,97,951,231,-205,793,-657,-925,950,570,3,898,572,201,-545,-907,266,495,538,938,-25,628,114,364,345,-576,798,944,-299,-282,-280,-331,365,-578,23,-913,513,10,-431,-977,332,-277,151,-810,838,-982,-592,801,394,-472,593,492,-296,-900,364,-213,863,376,-170,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getTotalSumSquares():double",
            new int[]{1000,127,-291,404,-587,194,641,532,-387,-267,19,-385,1000,1000,748,-272,-145,-439,28,1000,-594,194,302,-289,-150,-318,1000,-650,729,-613,694,-1000,753,1000,1000,655,-388,-623,-54,-1000,-1000,765,48,764,-169,138,-237,529,1000,-1000,730,633,-1000,227,-908,1000,405,346,576,-1000,-821,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "getTotalSumSquares():double",
            new int[]{-103,1000,-58,-383,734,795,-1000,938,1000,961,-861,333,8,743,-12,690,48,-1000,-739,510,-150,767,355,-1000,-949,-1000,890,777,-266,-879,446,327,-511,1000,1000,1000,1000,210,-170,-1000,-601,45,-418,-253,-609,-10,-388,377,600,-70,-529,-883,-250,-643,-1000,1000,820,404,-644,43,-187,-81,275,170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "predict(double):double",
            new int[]{1000,-592,13,935,-959,243,593,-142,-508,167,44,-1000,-710,-742,-289,-1000,1000,596,-383,122,1000,-1000,194,1000,482,333,399,969,192,-478,-614,629,-942,504,988,-646,-709,1000,227,671,1000,-884,1000,308,248,-812,310,8,141,32,-557,515,13,888,7,651,522,-906,346,-1000,-527,422,50,29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "predict(double):double",
            new int[]{-408,-1000,-501,-126,-251,827,1000,-198,-448,988,-821,1000,-1000,1000,-488,-20,1000,693,1000,1000,1000,-1000,-560,1000,1000,808,-613,1000,-29,-302,219,1000,-1000,1000,1000,1000,-582,1000,-1000,1000,1000,-1000,110,-551,-971,-477,-96,-663,-741,904,-1000,-702,642,719,-1000,1000,904,-1000,1000,1000,482,-20,141,-597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.regression.SimpleRegression", "org.apache.commons.math.stat.regression.SimpleRegression", "predict(double):double",
            new int[]{-207,1000,602,1000,758,1000,1,-1000,1000,-448,283,1000,317,1000,154,1000,-1000,519,1000,-225,-414,1000,-1000,-586,566,-574,27,-822,915,-408,1000,1000,-274,133,-743,824,-1000,-87,-989,-1000,738,1000,637,-1000,1000,1000,-1000,734,-193,-347,1000,1000,-746,-974,655,1000,1000,-485,-129,-617,-454,-456,228,-413}));
    }
}
