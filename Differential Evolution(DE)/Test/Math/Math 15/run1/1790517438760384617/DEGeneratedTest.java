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
            "org.apache.commons.math3.util.FastMath", "", "IEEEremainder(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(double):double",
            new int[]{-803,709,-271,626,753,-549,915,794,-404,-645,-49,826,588,533,-543,-580,-388,473,324,-865,865,973,-415,446,527,418,748,692,-824,784,755,-940,-168,-250,314,977,-389,342,475,-345,652,-352,9,889,158,313,-603,-315,213,-240,59,-192,-629,390,-174,-79,-91,-644,-406,623,-880,961,-397,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDhFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(double):double",
            new int[]{47,-776,641,882,715,-662,-296,333,998,508,955,991,-594,732,-791,-8,325,984,790,-83,92,885,158,-209,-527,-840,557,850,733,-131,199,-699,-939,-628,-925,-443,141,72,606,764,393,-970,-964,837,-432,801,-277,-338,-801,-15,-705,647,744,378,20,-606,168,-113,-324,-113,-821,-140,701,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(float):float",
            new int[]{749,104,-682,-955,930,-806,-707,-719,-35,559,-360,-277,-751,309,543,-420,-269,-737,-508,-786,304,19,-645,-453,381,-981,-557,-433,131,665,815,62,594,-433,512,774,649,627,-22,799,617,797,536,244,-775,-912,-549,909,-452,660,-134,-937,620,726,677,283,449,335,284,739,-672,-859,-922,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Float:NDYuMQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(float):float",
            new int[]{-461,-254,-486,632,810,337,-340,-267,221,970,223,761,637,87,245,-899,-786,-365,875,-823,-962,-503,-69,994,528,858,340,-384,883,-409,939,243,-432,801,634,-398,-421,230,460,20,88,-585,663,-285,-85,-804,-345,-399,-708,445,374,-784,-658,-240,-580,267,809,-745,-824,-290,-284,-457,-941,-706}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(int):int",
            new int[]{-275,-391,333,97,839,-843,-263,-590,637,-141,-392,-838,902,839,-837,-49,707,451,952,617,-35,-142,619,161,-840,-321,-803,-656,-823,-579,-802,624,321,853,-871,911,-400,-802,153,-226,131,125,-405,197,135,-172,721,-242,-605,221,833,-815,298,-768,61,662,602,-498,-532,-53,-67,-591,-934,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Long:MzI=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(long):long",
            new int[]{-327,-218,-698,699,464,2,691,-176,575,37,-923,88,-71,-343,207,986,-896,-661,515,235,942,-397,-507,-940,-398,748,-824,-53,-37,-199,183,179,303,-31,-114,211,-194,-563,-152,-214,-430,995,901,572,-468,-400,970,78,18,-242,-945,-414,-563,386,687,968,-911,-311,-421,-654,-16,509,-765,804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{-463,922,597,-720,897,436,662,983,563,-630,-438,-873,-961,-768,102,-336,381,323,944,-228,-213,-338,-140,698,930,-346,541,719,-420,-472,-153,136,-140,-892,682,-489,836,53,640,733,134,-648,8,-99,-278,-450,734,-481,-911,-37,605,-966,685,968,-5,-630,-346,-661,287,766,661,562,-638,-13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{744,-815,869,-90,-414,684,-141,-513,-943,-606,-425,-841,832,-785,733,907,-715,631,702,886,-114,-124,474,-138,-805,823,-398,823,-891,787,108,823,-160,910,687,-617,-533,-604,-545,158,841,-502,971,-6,-866,-125,-741,-17,-605,685,-803,686,-928,636,-234,276,-313,80,380,75,570,-776,-187,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{537,-622,468,-686,295,949,351,-465,358,730,-757,242,533,860,554,-436,676,-637,858,883,398,157,-784,-557,-548,-193,-726,605,387,609,-942,237,879,-859,-269,997,526,967,-486,672,-175,-151,409,116,-444,-496,-577,843,868,-273,561,-122,-19,534,-952,62,608,-156,922,-293,-48,205,-536,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{-346,-604,-291,-654,-897,398,-278,-549,740,-246,391,-511,109,-218,-103,-411,994,-978,322,-948,656,-228,-49,336,107,-886,-725,140,-576,-327,547,-773,922,898,63,911,-633,222,249,-495,361,93,-861,456,-462,-420,-191,-636,251,40,-29,-366,93,-874,83,149,745,349,-987,971,-571,330,-354,877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{1000,-425,175,-122,161,-359,-456,-398,72,-842,236,-717,-279,496,1000,-1000,1000,-383,1000,-1000,-76,-162,48,-262,-385,-18,-1000,747,726,-542,-994,532,1000,-28,-786,1000,-1000,-545,-550,-636,1000,-223,-452,1000,-1000,486,-495,-864,529,-771,-1000,-539,18,379,-683,-275,94,-845,-1000,-71,23,405,-206,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:MjIuMTgwNzA5Nzc3NDUyNTg4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{-655,-333,347,-457,-214,346,-307,464,123,676,529,-353,816,-281,-892,-154,-306,412,280,199,233,179,12,55,-746,306,33,-536,588,-278,385,932,722,-632,-636,-944,918,-630,114,-735,-177,867,939,43,-769,-449,323,733,-685,700,-411,-692,380,-173,135,-670,-5,912,280,740,-772,-326,899,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{-551,397,257,-356,-339,555,-888,146,-314,467,574,-276,302,-550,-343,23,-29,678,-263,771,496,192,317,552,-15,792,-341,-717,-154,-182,917,-460,892,-149,-819,-1000,697,-483,-327,-619,-534,291,1000,640,-1000,58,55,1000,132,492,-695,-867,-22,-127,500,-939,-168,988,423,378,-66,344,577,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{-307,-1000,81,-591,-168,-359,92,-635,-785,1000,-94,-234,34,-340,-28,1000,-578,1000,458,-223,1000,-1000,772,-167,-840,-770,-447,1000,1000,86,144,-534,-1000,-298,-803,-1000,-911,127,1000,222,331,1000,-777,-581,394,-844,-553,-174,74,-1000,998,436,-994,-1000,485,909,-713,591,-701,-1000,1000,976,51,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{812,-58,311,546,-919,-661,-91,-887,-785,199,54,546,-863,655,94,73,-842,707,458,-690,-69,316,463,776,-840,143,-447,-329,-946,-469,197,-534,-15,833,363,-144,265,-763,-739,238,-486,904,-126,-973,-293,-627,-312,853,-348,991,998,-51,-601,393,166,909,871,972,-701,-629,-130,698,-36,-810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{-392,280,-790,322,-536,505,368,-582,294,449,826,685,968,-366,-150,-91,-285,525,-15,-789,-900,-630,-168,865,-520,-405,-959,234,-200,951,89,440,-986,-885,105,428,885,-508,784,-327,-501,-394,-762,942,409,663,-434,-758,-989,600,-171,358,-503,276,177,263,508,-699,931,-965,-492,491,573,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45MjcyOTUyMTgwMDE2MTIz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{8,-74,-20,-834,-556,315,-156,-374,-793,554,1000,419,565,-1000,208,1000,664,-520,-636,240,1000,-441,-588,1000,178,959,-197,-1000,-874,-197,-65,560,-686,1000,701,67,-151,355,672,1000,503,-216,274,124,870,-92,-1000,-438,-279,-1000,-474,-1000,-1000,-319,-49,900,640,1000,671,-1000,-277,-427,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{-504,150,-320,809,-791,930,826,604,-405,403,299,390,491,249,31,-433,795,3,-25,-172,-69,300,822,-300,445,-360,136,411,-347,-512,384,709,687,-415,-375,-531,-25,-796,-747,-117,-791,710,115,-78,-767,-674,570,331,883,-763,-383,-105,-189,-699,-923,-24,583,-446,-994,382,-69,403,16,513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{-117,590,-1000,-660,8,400,479,724,257,133,313,-50,549,-7,-799,260,995,-215,42,394,514,366,379,740,-901,990,-601,-834,642,-945,420,-639,598,869,-877,740,521,857,352,945,-511,330,322,-185,807,-615,222,-657,-997,837,-157,-446,-894,-864,-333,-475,-521,188,-918,-170,-962,-310,831,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{-716,397,36,80,-1000,1000,564,234,-1000,1000,942,293,1000,-1000,404,1000,520,-695,-731,-419,1000,26,687,390,267,-461,-296,-1000,-545,32,161,1000,406,-211,524,-219,-869,-818,-86,1000,-127,981,1000,137,-368,-562,-672,-145,-86,-1000,-1000,-1000,-1000,-1000,-345,183,1000,-11,640,-253,-34,269,945,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{508,-986,-532,-496,-776,196,-749,83,-259,307,-70,401,526,-119,68,167,903,-905,111,60,160,-727,88,-103,-968,907,-44,-485,-816,-494,273,-70,-239,588,184,584,-236,32,337,460,-974,-377,269,697,925,140,-962,-45,608,-386,462,-773,-550,482,151,78,-219,274,754,-685,-533,235,279,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{-82,-533,1000,-1000,-505,-710,329,-359,-126,-663,-896,281,-183,-308,-1000,-520,-1000,398,1000,-424,-130,-178,-982,-497,-47,1000,927,-1000,-167,1000,496,-1000,-1000,879,-59,514,664,433,1000,1000,1000,402,-1000,-520,-28,876,-1000,-408,-1000,-622,-665,-6,1000,1000,1000,-1000,-358,332,1000,-235,195,243,-1000,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQ0LjM2MTQxOTU1NTgzNjU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asinh(double):double",
            new int[]{-607,930,-736,-629,-965,470,766,52,559,766,-850,579,-325,-55,992,-326,-697,-105,-33,-261,527,72,317,-294,-814,650,756,76,388,571,-927,-138,553,365,61,-252,529,988,-664,365,-236,313,-893,-633,565,435,-948,-316,-912,-530,35,205,-182,-844,629,-975,-935,234,148,335,255,-692,-809,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asinh(double):double",
            new int[]{-243,656,379,776,63,851,-504,-252,657,535,823,-801,23,-687,-303,-465,-73,-828,-213,-381,380,-163,-881,-911,-498,537,765,30,-264,-826,95,-384,161,-850,-115,932,-394,512,-504,815,712,-531,855,-747,-871,822,26,676,632,-580,786,-734,98,-657,-189,336,0,-813,-166,-234,371,592,-550,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asinh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2MzI5MjM1Mw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{805,-860,257,525,967,-969,-737,504,-871,-354,441,-89,-277,1000,-771,327,-782,-18,-406,47,-162,-375,-667,772,257,449,-539,778,38,663,-964,339,373,724,-969,579,825,250,-643,-969,176,-392,-8,217,-732,644,323,-684,727,948,256,-400,887,-178,445,-245,867,-300,-929,-605,776,125,986,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{865,-29,98,-875,491,280,95,-107,230,-729,-434,-722,-735,1000,-540,365,-39,-400,103,-98,-26,877,-105,-465,-515,-88,526,-415,-767,55,-397,-222,-474,656,-360,440,1000,-1000,569,-519,-197,1000,-681,-38,71,594,509,201,442,1000,177,1000,-597,-32,-590,-182,-300,-166,805,394,818,520,130,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{-919,-402,-272,-168,652,-768,-658,-274,-365,139,-522,-576,-824,206,19,-26,-547,305,-376,-454,-510,-972,579,833,560,-358,-882,751,258,154,432,-176,-833,-936,794,999,155,911,767,773,-254,-982,-712,-460,-201,-4,567,821,-266,-499,218,645,955,-496,-183,793,-222,-94,130,811,-829,-288,719,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{797,620,-594,307,-447,602,-957,-581,280,655,194,-989,44,-486,-5,403,349,-258,63,140,4,70,-864,-68,94,-273,547,-770,-668,316,-67,-462,904,516,-191,-141,24,388,-854,-112,835,-610,-58,-488,81,-655,910,-25,969,538,898,-314,62,-95,-609,34,700,805,574,-167,444,314,811,-962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMzU2MTk0NDkwMTkyMzQ1", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{75,-152,148,-248,416,-319,-94,755,957,-772,966,937,555,-683,145,-119,-788,-476,-447,-781,-737,-558,692,623,219,-301,-507,834,915,-940,129,-850,600,-180,-201,-738,-221,-719,-676,638,242,600,-659,744,-454,-486,-819,305,387,550,-903,-447,-159,-804,397,-66,-791,-785,259,-895,348,787,544,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMDExMjg2MjAyNDg1MTQ1ODU0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-760,-526,886,-566,-101,782,-261,-570,422,21,-1000,-372,-703,-608,622,-385,-185,129,-530,78,30,516,-570,46,755,846,-303,-616,-588,397,326,859,605,-75,535,-210,-895,874,-1000,-875,-1000,187,167,-680,-534,-17,214,138,400,-677,210,812,1000,-310,70,-183,975,-808,207,857,1000,-294,-66,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{8,894,-230,-11,-573,742,490,-184,110,860,-136,806,329,906,-482,328,884,-879,923,-5,869,456,550,-46,-91,552,244,530,956,-155,99,-408,-303,-47,-556,474,-145,57,245,581,229,521,126,901,-824,173,-452,-417,-561,815,-764,443,698,-649,-147,-179,-952,679,146,557,-296,942,290,24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-1000,1000,-345,-252,605,-735,237,606,282,-654,426,-1000,89,-1000,112,-463,-59,529,1000,452,-312,-818,454,416,-80,-83,277,-380,-1000,421,-866,1000,1,-633,-1000,1000,903,1000,23,-979,-706,329,-65,-214,-433,-452,593,-788,528,-119,257,1000,257,781,165,-153,1000,262,599,-521,553,-1000,-1000,602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Double:LTMuMTQxNTkyNjUzNTg5Nzkz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{8,1000,269,477,-449,546,564,-362,-64,91,457,1000,286,234,268,1000,412,-1000,-658,405,651,1000,1000,219,911,-49,-99,37,902,600,-7,74,337,710,-479,-174,48,70,816,-778,467,1000,-483,38,225,-442,-1000,-217,152,1000,-933,466,1000,362,-630,-346,-1000,-225,69,-432,102,869,-509,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-357,111,-309,-456,349,-965,-733,388,747,-1000,23,-860,135,-1000,304,-630,-801,-612,-69,850,-1000,109,529,-83,-263,-964,-430,51,-1000,-116,-186,-652,351,-1000,344,-393,-548,566,-162,-625,-873,-885,613,-508,-855,680,419,-453,71,-1000,189,-569,-506,949,-186,175,-241,-49,986,828,912,-1000,-549,650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{0,1000,75,728,-10,173,-140,0,0,-1000,-246,76,-1000,-1000,642,-257,-970,-222,735,853,0,399,837,-181,1000,-583,0,279,-980,1000,-895,566,1000,-1000,-1000,-237,-106,1000,-489,-129,-1000,0,-272,-323,-678,0,349,-342,1000,-157,64,0,837,1000,-893,0,1000,-44,761,1000,0,-982,-940,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-569,833,1000,-639,-1000,1000,771,267,-293,-325,-1000,-392,-865,432,482,-618,34,915,1000,1000,235,212,-585,-972,282,1000,-212,892,-636,1000,-905,462,-116,-1000,-585,1000,-388,581,-844,-221,-794,-63,664,373,-1000,346,697,-1000,-48,-989,-1000,756,1000,-1000,337,86,1000,1000,968,1000,-27,-412,398,651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-793,876,-1000,-310,1000,-1000,-1000,-257,-360,445,-291,-1000,1000,-915,55,679,789,-130,-164,-208,902,-381,267,-839,-64,-275,144,426,-227,-727,848,448,-531,791,-1000,366,758,1000,939,-1000,-273,162,75,446,-294,23,178,791,719,278,-505,868,420,812,638,465,334,145,-404,286,-717,-892,-658,-509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{922,-613,1000,-1000,-237,-318,1000,622,934,-694,-1000,-453,-1000,-1000,1000,-532,-1000,1000,-253,1000,-1000,-1000,-874,-23,1000,546,-1000,-1000,-366,1000,-442,-278,580,-128,118,-494,-876,-122,-598,-1000,-1000,181,-939,-799,133,1000,179,-548,-466,-362,1000,1000,853,-168,1000,-305,668,1000,967,-327,1000,-1000,41,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-518,571,-212,-22,952,-981,-825,42,-741,549,-786,-239,487,-635,574,958,667,979,-290,321,156,-757,-235,-749,-178,933,-703,-340,-81,193,-603,608,-523,6,-900,144,279,464,653,-989,-960,80,147,-24,290,636,-675,453,618,123,922,851,474,230,852,559,470,530,-291,-101,-696,-987,-388,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjcyNjA1NTc4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{613,195,1000,-766,-339,961,577,831,870,-191,-1000,-436,-1000,-746,559,-1000,-1000,1000,505,840,-442,-592,-492,-457,833,1000,-1000,-511,-455,1000,-615,-269,505,-1000,-845,332,-422,235,-1000,-474,-1000,119,-332,446,-1000,392,784,-388,923,-662,1000,537,835,-1000,953,627,673,-225,653,738,628,-385,470,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-210,885,-279,1000,179,913,-789,-518,1000,-805,1000,296,719,1000,-94,-179,795,-1000,-1000,1000,199,1000,651,-95,1000,399,-143,589,581,-104,902,406,626,-146,470,732,-1000,-920,-1000,973,-315,-366,660,722,-1000,-1000,-462,-692,864,-1000,-1000,1000,1000,-772,-445,-156,-180,274,1000,1000,-274,-479,609,231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-317,-1000,1000,-766,64,888,-261,658,1000,-168,-1000,-832,-1000,-1000,1000,-1000,-1000,203,-1000,677,-1000,135,-643,-72,1000,846,-1000,168,-789,742,359,559,1000,-256,836,332,-1000,769,-1000,195,-1000,-288,-276,-1000,-534,-17,597,500,400,-1000,198,922,943,-310,953,627,1000,-225,606,-144,1000,-1000,470,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:MC43ODUzOTgxNjMzOTc0NDgz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{276,-1000,798,-1000,118,220,-509,-280,-1000,-259,-11,901,1000,1000,1000,-666,-1000,-83,-1000,1000,-587,186,119,-58,-786,529,389,-1000,1000,-982,1000,-1000,-937,-588,-590,-897,-838,-1000,-781,174,1000,-398,122,600,-639,-469,-1000,-856,899,-850,1000,-785,898,1000,1000,529,-728,535,-143,110,-1000,-171,1000,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{303,674,730,-465,-679,-420,839,66,-260,-752,481,356,-219,682,-733,926,-805,-236,653,181,-921,156,630,277,891,-353,178,-144,844,92,53,681,-159,-912,117,-955,950,-106,-40,139,42,754,774,99,682,-284,617,289,-403,351,72,-368,133,-653,-950,-797,3,837,39,-802,308,578,432,-569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{818,560,-817,324,898,74,348,901,-498,31,370,740,-216,919,-875,-360,-213,-243,-913,920,274,-999,-178,-773,311,-674,376,-632,-810,-914,-970,307,254,258,548,-581,-972,49,552,285,702,942,920,934,437,-32,-778,254,248,255,-875,499,262,459,-586,-677,77,641,841,-455,-406,313,335,-986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{-254,287,38,-150,-918,778,-834,-355,760,-221,89,-609,614,609,341,-512,209,78,-779,-425,591,-12,-310,-821,-102,417,-930,603,49,-806,670,-185,694,-992,-917,34,-529,942,-595,961,214,847,903,149,-770,962,778,-123,12,-554,-433,-919,825,-923,463,-608,-508,-726,388,-889,-597,824,981,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIwOTcxNTIuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cbrt(double):double",
            new int[]{-157,-66,-494,776,642,115,-387,274,-384,49,-831,796,-275,258,910,-306,-566,-148,-830,-403,649,560,-170,-502,181,999,-271,-44,-159,615,-799,-96,-783,848,-747,-142,444,-29,97,-633,-107,-205,226,-580,-407,970,-948,-730,-596,957,-779,-419,-501,927,-833,175,-450,808,210,782,138,-504,-462,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cbrt(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cbrt(double):double",
            new int[]{-576,-137,833,370,-177,-423,-94,-110,-426,-979,-210,-68,463,610,-44,908,-325,-344,18,313,788,-378,-836,-574,250,65,820,528,-672,-146,-377,-208,-511,-623,608,-712,-554,-656,747,-409,684,523,424,392,-805,309,-724,194,341,355,911,-319,-685,665,494,687,-505,-458,-122,-193,-872,-442,-928,654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:LTI4LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-287,-662,-299,-67,943,768,-116,-164,-772,-123,381,748,-12,-52,753,-7,601,-614,885,42,-135,851,807,26,652,971,814,326,27,-115,563,361,-871,-81,-474,-386,942,-995,-382,-836,-291,-877,129,-409,-816,-995,-798,951,193,-384,984,-288,-971,501,-655,46,-890,-984,-433,669,-997,641,774,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-488,-990,780,613,-139,365,444,-860,-732,-283,-805,61,-259,210,-757,888,-220,-799,294,-732,99,804,-125,-908,-825,-647,-980,-950,-24,-707,-476,490,178,109,-340,427,-976,157,732,-249,621,981,-894,275,-107,-371,-502,-897,-302,753,-813,990,-339,881,-443,878,-812,151,926,-433,791,-14,948,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-1,-566,548,777,837,-225,-916,1000,-1000,-1000,632,-1000,872,506,1000,-1000,891,-1000,1000,-578,-1000,-1000,1000,1000,-583,-348,864,1000,560,-1000,1000,1000,-1000,-246,-1000,-769,1000,-1000,-1000,-1000,-86,-370,-1000,-1000,-1000,-1000,-239,-10,609,1000,1000,-1000,-1000,-1000,-103,-1000,-1000,142,-1000,662,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-651,-524,321,670,-114,-708,-377,-222,63,839,-884,115,-722,66,328,666,-380,364,-739,424,-333,570,-252,451,348,-330,29,782,-553,981,-4,314,331,-354,-751,748,-703,584,-410,-100,575,-294,-147,-737,479,-828,84,-449,429,-282,2,160,686,-296,-609,-47,476,-605,-373,-397,-85,245,-116,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-308,-799,-947,662,-529,-399,-950,-950,887,-38,-126,268,-766,-646,907,-389,-123,-169,-42,598,585,-916,414,305,-989,-270,-930,143,-831,-146,399,-975,-178,421,-273,831,478,541,-756,273,-641,-807,-937,867,122,-11,654,-447,400,-149,-98,709,-715,-975,-262,-216,764,787,-124,-409,-177,404,-502,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-828,331,938,566,249,-248,-105,710,-712,738,-715,-2,7,1000,-307,1000,39,999,-72,189,-1000,804,-1000,756,705,-171,43,13,282,1000,-95,1000,1000,-1000,-1000,595,-1000,-204,-487,-951,1000,79,-276,-813,114,-496,703,-669,654,-2,-18,267,1000,999,-76,284,-131,-493,-640,-318,101,-250,545,-425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{-334,318,-578,-901,208,-858,940,346,-793,606,468,744,513,-600,50,-260,401,207,84,-901,-718,-1,-921,-489,35,829,591,531,538,-4,-973,-33,204,-854,-411,-883,-89,268,-339,-620,-443,852,-851,-345,-336,497,-62,695,960,-803,-529,103,647,-818,717,30,218,-274,286,784,-917,-341,301,-446}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQxLjM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{413,-590,566,681,300,-312,-719,930,-222,391,891,444,-433,989,101,-691,-375,-504,-632,75,992,-688,-674,13,-627,873,225,856,696,-98,-794,-470,119,-272,870,706,512,533,-436,79,315,-302,-908,207,-149,462,-873,488,-333,1000,-469,-642,295,720,-39,177,-90,-894,714,428,849,720,-447,828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Double:NjQuNg==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{-646,-182,860,-328,-800,222,-216,-682,316,452,855,172,-490,-349,239,-485,-24,95,-428,845,-324,509,-15,-40,220,146,-578,-750,36,-509,644,793,861,-896,-63,114,-846,-460,-498,608,-124,569,-195,-487,-702,983,-269,51,-33,-857,291,919,768,-855,-152,-360,148,-17,-645,451,-993,-611,930,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{-673,729,-614,857,-651,-795,675,-867,390,618,901,795,829,172,377,30,-199,-9,-173,-760,-724,-199,-179,-398,677,-183,-743,-369,473,808,-377,-802,-220,888,597,583,560,818,-598,900,-285,-690,-648,-524,108,-818,-25,-354,332,62,227,810,899,-223,-564,-419,-515,590,-192,389,-196,-12,-370,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{-240,856,-413,923,649,574,43,518,314,806,580,-904,-557,-407,588,307,-539,692,-695,685,849,108,-669,-637,132,199,-637,798,287,-149,-369,-14,841,820,-601,797,558,-462,-276,-236,-699,422,685,-18,350,-948,251,568,305,-544,92,598,529,-835,-265,346,20,744,702,-754,597,-995,521,-961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Float:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{-680,-696,828,261,184,-35,185,438,-708,-860,684,-873,970,-754,-86,305,546,460,482,505,923,733,-126,-429,467,-744,196,-61,-779,-611,66,-58,-966,-307,-234,810,752,-121,-316,-276,350,261,981,616,-424,603,480,194,-499,-882,828,-862,-778,844,140,-668,890,587,527,-431,214,-777,185,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNjg4ODM2NjkxODc3OTQzOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-794,-837,-444,89,865,433,24,921,618,-56,532,740,-87,837,217,656,-617,-291,69,-574,25,-999,139,961,-354,-880,-846,-487,199,605,237,814,681,71,-515,216,-189,273,804,-325,938,210,551,353,277,-134,-660,534,902,-74,-373,-394,759,-379,608,-110,-653,-332,382,-702,734,261,-659,949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4wMTE4MDAwNzY1MTI4MDAyMzY=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-610,-306,-968,280,866,-208,-323,590,-266,964,-83,810,984,-276,24,-666,161,-975,684,92,934,-227,154,-569,759,-577,-690,937,61,256,-916,-240,-573,-593,-131,808,842,-706,-911,-33,-777,652,-680,433,-833,-871,-909,-830,-841,61,-153,-458,-707,891,-635,-763,-991,540,429,-250,274,-36,-483,896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Double:MC44NjIzMTg4NzIyODc2ODM5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-1000,-386,225,43,-299,662,-568,-92,1000,-797,-468,1000,-780,282,1000,216,903,1000,-657,311,-498,-732,696,1000,-267,-1000,464,-1000,346,1000,159,199,120,1000,-1000,-171,485,895,849,-644,1000,-1000,-202,833,1000,1000,618,-481,-568,807,1000,742,466,-422,-295,563,261,-1000,-158,-666,-4,1000,-458,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4yMzc4MTYxOTQ1NzI4MDMzNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{989,88,603,818,30,-486,553,418,787,862,-382,-929,-99,-641,-867,719,753,-702,-457,16,475,494,-647,563,-42,780,665,709,645,-745,-333,95,-83,-580,477,-122,-523,964,390,-588,299,-498,-249,45,-563,-701,453,640,254,699,-666,-519,483,-175,672,-747,-38,937,-627,-26,-182,-210,-524,926}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-854,321,836,934,473,675,883,544,806,521,970,-915,432,-888,156,841,740,-591,-538,182,-704,-563,-826,-872,-203,-815,364,-648,468,730,-706,-678,-516,826,370,58,-383,-315,-7,-198,447,-663,-202,801,-789,-418,-433,341,639,-655,-632,-785,-28,-33,-592,-143,-233,905,463,-459,70,-825,-196,-760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuOTU3OTE0ODA1OTAxNzE1Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-217,538,1000,916,-1000,499,349,53,895,-1000,542,-190,-400,578,472,325,668,1000,-1000,514,-728,1000,955,516,-1000,-1000,1000,-590,-371,-308,1000,659,-1000,812,-1000,519,-1000,955,-1000,-1000,182,-918,-1000,-324,1000,-434,-575,18,-660,920,-1000,-719,968,984,662,1000,1000,1000,-527,500,1000,506,48,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41NDAzMDIzMDU4NjgxMzk4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{677,-58,930,820,25,-381,403,-955,-702,-842,-975,-234,-611,796,111,-433,-91,-180,-741,845,-394,903,-179,434,-574,-832,-66,292,-374,-711,-933,44,381,426,142,939,-105,339,885,628,-758,647,-738,48,818,862,618,-355,-388,-437,431,217,-661,888,484,-305,878,-645,-304,-638,435,-7,-148,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-792,115,-161,-915,-782,582,190,-566,-597,283,-222,-831,-846,779,-581,-761,-269,977,-673,566,-138,-661,-902,955,-601,27,414,302,466,869,-493,-510,-378,-493,498,58,-882,-368,323,-349,509,-164,-834,54,-405,-731,-912,654,-51,-968,-183,128,-875,48,561,-728,-921,986,166,16,260,-229,898,-128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-748,455,861,-956,3,-588,-933,126,-671,800,229,-861,201,511,353,899,-441,322,-659,-338,-382,-25,972,-109,-984,-113,728,654,-119,415,-133,-97,-907,-616,-305,-782,-664,973,-474,512,-109,0,-489,-337,544,141,-829,71,475,690,281,-260,-469,76,557,632,-92,-427,494,775,-59,-640,-340,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{777,404,-420,627,721,-792,82,-969,73,-456,71,-864,-85,-605,-992,496,372,-272,-542,879,214,-603,639,-870,498,-863,-198,444,913,188,476,-94,-249,895,140,518,-276,-5,-538,829,187,264,879,765,-210,673,5,280,24,-701,-875,46,-291,819,-144,564,-934,-338,-57,-202,-303,581,-869,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NDMwODA2MzQ4MTUyNDM3", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-448,926,234,998,-722,786,601,-472,-44,629,-241,-377,-157,472,278,-754,-382,462,73,297,-161,130,-105,603,603,-181,423,-931,501,440,865,-973,-807,582,-428,141,207,338,-329,-850,-32,-825,-559,194,-555,655,-214,509,318,-99,-4,859,102,314,-229,251,284,241,381,-828,199,41,824,906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Double:NS44NTk1NzExODY0MDEzMDZFMTU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-37,-733,-499,180,-414,-541,638,-472,-196,-604,669,-441,-761,-11,106,-784,255,161,-141,541,150,979,829,543,-762,-484,-667,-488,-696,-736,-783,-71,157,-233,-959,519,-678,-497,258,871,253,989,13,-471,-131,-413,-163,494,735,-459,-696,-334,709,427,-440,-83,-463,356,-504,-782,-485,-950,-680,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi44NDI4NTk5OTk2Njc5NjZFMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{57,-793,317,227,376,-988,-22,371,-828,886,-170,419,528,687,-312,-323,587,26,-253,-180,973,-850,630,408,350,-173,-994,-17,348,554,-124,-113,-708,-800,-894,-695,-517,597,377,-75,-861,517,-645,-900,528,977,616,-599,-692,886,-406,80,647,660,675,91,-714,-357,-563,-945,-579,899,5,-174}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-480,787,-940,898,-469,-393,205,75,91,-897,-968,821,395,82,762,921,-261,487,-643,426,-733,-715,736,358,-19,669,-440,178,980,628,419,904,-880,766,762,135,-410,617,-519,-876,278,-349,-279,519,-824,-331,-831,451,-369,-663,-129,182,722,-752,433,763,-285,94,359,803,192,255,-750,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4xMzZFLTMyMQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{-739,551,1000,-469,1000,1000,1000,-395,-440,-75,-508,1000,611,-972,1000,-1000,28,454,-537,-1000,-1000,458,237,821,-1000,-247,-635,699,-1000,-234,-1000,-1000,380,269,-133,-1000,281,-1000,-1000,1000,722,580,102,652,1000,597,1000,-608,1000,-411,-1000,-311,-295,619,-1000,-676,-56,193,478,490,-985,-213,-79,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{-962,-9,-777,-859,953,562,559,-718,-937,663,16,916,725,109,980,868,886,-541,-172,-696,773,593,-298,-966,-270,446,-221,878,-828,947,-655,437,513,97,532,-618,-376,-662,919,-466,880,886,-622,399,184,-444,-499,584,487,724,770,596,-727,-422,201,852,-759,539,632,446,966,-138,40,-800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{799,124,373,-259,817,-435,676,-909,-906,668,-356,-662,982,-266,-482,-220,927,-838,-524,-883,-362,-301,-935,489,342,-327,-530,-952,401,55,239,233,-544,120,-369,667,-32,650,110,755,536,175,658,467,684,594,584,-447,-54,-372,-714,476,44,-931,-997,739,681,-472,-941,939,-923,490,281,-101}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{-743,-241,822,1000,1000,-257,1000,825,1000,-7,127,499,273,336,-1000,-1000,-858,1000,-549,1000,957,1000,1000,-1000,1000,-44,1000,-713,283,-1000,-776,1000,-1000,1000,440,-1000,-227,-1000,1000,1000,900,-1000,-1000,-110,1000,790,628,-915,1000,-1000,1000,-761,973,-449,-1000,-775,-1000,1000,-1000,1000,-987,-864,1000,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:MS43MTgyODE4Mjg0NTkwNDUz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{27,37,-925,953,703,74,-876,-88,-413,-983,112,-973,410,-344,573,233,997,-202,227,-989,669,688,38,523,-923,-860,-235,-214,-371,-367,596,-493,409,847,269,691,-111,414,-528,710,-391,29,-766,-801,-530,477,-460,-196,-658,626,-544,-377,-743,-11,686,-50,-359,-941,-342,-97,727,-699,2,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{-716,224,286,418,537,-606,984,972,-941,-788,-603,-517,457,-374,-998,-648,-535,386,184,-430,985,673,-822,896,-8,546,805,76,-803,234,348,-551,-850,-527,-275,828,-194,432,481,86,-517,119,-907,552,628,-442,542,-984,-183,-437,205,-103,18,445,-269,980,331,-404,-251,14,85,617,-967,-605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{904,-330,443,698,344,-509,-188,-188,568,-290,753,11,295,-318,349,949,-348,-58,401,565,-595,803,-814,652,776,845,-836,473,600,173,-154,628,394,-466,870,948,223,-293,-335,-287,-822,-80,-915,-825,-252,-507,836,61,-609,-629,630,-513,-788,-232,586,564,828,770,-362,-151,178,392,-595,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{-616,-665,-664,-833,549,-510,804,-899,830,96,-749,-580,75,-636,521,-492,858,408,35,-871,-168,320,-359,-499,975,383,610,-834,-457,-445,557,644,307,625,814,422,-989,709,-768,-634,-113,-854,-740,-680,533,243,-300,325,506,-147,587,-292,-430,-743,-751,-932,-323,-512,821,117,-469,-114,-761,-730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:LTE1LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-147,-554,809,-772,-183,284,-209,422,-388,-858,638,651,-307,-1000,-983,-390,696,-598,-312,68,-651,-519,-857,-883,-1000,964,-935,362,-222,-1000,766,795,-130,572,225,-224,-198,-398,-84,-83,524,-397,-278,-304,-264,-849,-727,-461,98,454,2,969,861,549,-1000,777,-485,-677,1000,180,877,-138,-310,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-544,-303,263,-462,-855,494,-443,860,-382,-808,-59,-154,-829,732,-892,-893,-938,-250,-648,275,786,-735,-815,-28,893,-282,-582,-846,-813,-235,779,-547,168,-201,-553,201,613,255,188,519,288,-482,177,43,-20,429,50,-346,-730,253,238,-67,-252,328,230,-47,-240,540,31,496,174,317,819,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:LTU4LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-58,743,-15,-896,730,-206,-301,936,313,-372,-592,889,671,-5,323,554,55,-247,144,686,83,778,617,19,32,-25,683,-203,356,-822,934,503,415,-670,-571,416,-517,-585,-804,-736,-816,345,170,454,-304,-776,764,161,-697,246,781,887,-847,857,-30,-973,461,-261,-847,426,-181,697,-425,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{852,-712,873,275,-748,760,660,495,245,67,-526,788,894,829,-708,37,-282,-259,-631,374,-205,301,-195,806,230,-353,945,397,-307,-483,-947,-269,709,857,953,424,-554,-213,689,161,852,282,-375,938,-848,192,-135,948,983,43,-598,-58,708,436,-27,-236,842,-213,-748,252,928,-315,-310,-278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{39,967,-445,-1000,1000,482,755,-1000,-32,-1000,-357,167,573,-88,703,542,1000,-97,-863,-1000,-1000,-730,-1000,610,-646,-369,-1000,604,1000,-579,1000,-332,1000,942,-352,-80,-672,329,-326,79,679,-278,226,102,-1000,752,-346,444,-591,454,304,1000,-1000,-1000,340,687,-402,-482,895,-995,-444,-567,896,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEwMjM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "getExponent(double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEyNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "getExponent(float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{882,720,-475,761,510,108,60,941,-708,473,-411,961,-668,236,-641,927,841,-437,-811,607,518,-951,-392,-488,861,-375,-629,491,-164,992,-196,90,-16,-566,-448,-886,-937,-732,-331,-117,623,-634,-282,540,529,-798,588,-578,373,-285,-961,-27,984,873,671,-994,759,329,-46,172,459,-249,241,468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Double:MzEuNQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-315,154,195,852,622,-359,675,441,48,917,281,701,108,865,512,-515,-817,449,-329,553,-441,797,-898,-873,-560,983,823,-925,-15,66,-701,204,-310,-518,140,356,-516,-700,933,54,155,-976,139,-698,-604,654,969,787,659,527,552,-750,262,-896,299,-973,735,301,-620,925,-704,248,-541,-527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-716,517,126,-161,345,214,145,-931,-411,1000,-196,-756,632,43,666,1000,28,770,-621,-323,-416,-223,1000,-688,-109,-380,-253,1000,1000,-545,704,1000,-681,-56,1000,-537,186,-107,230,-155,874,818,1000,-562,1000,1000,556,1000,644,-303,-157,-204,152,-1000,722,1000,-1000,-671,1000,-117,550,-42,-984,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-635,-974,-974,-843,30,270,-710,-910,-724,-84,889,-710,-181,115,-734,716,-10,91,520,-257,171,-105,924,-338,890,-534,-198,664,758,309,516,69,-863,-300,-615,-210,406,-320,-209,47,2,-678,396,-781,-963,758,470,-633,210,-735,-376,-528,-998,-643,-88,-325,674,282,-759,-41,-538,-452,267,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-512,-269,242,942,-693,-277,-26,553,-796,637,-970,547,-903,-568,868,-733,459,896,-812,-780,851,575,143,313,-834,-637,439,149,218,-986,-493,82,-6,397,-983,958,877,728,414,498,-538,170,855,-865,179,240,-40,803,-79,262,133,-377,55,-253,-159,742,808,18,227,-310,849,-310,679,-492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-859,81,998,265,-366,-904,-464,-792,510,-111,145,-705,-927,865,832,-939,-711,-673,-102,-403,22,-720,635,-230,-448,-401,-102,-489,618,-864,-156,677,-128,964,192,-717,669,-691,787,-114,383,-77,923,798,-905,-333,962,-557,-602,-105,-936,670,-672,863,-464,-888,-6,826,-748,-207,314,-236,884,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:NDMuNjY4MjcyMzc1Mjc2NTU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-511,353,60,847,941,620,948,-870,-309,101,397,50,416,298,-131,698,507,-840,883,-827,-259,624,159,644,214,417,40,-28,240,366,339,86,623,-698,-627,321,765,-663,885,-262,-629,-261,-205,-910,192,958,-720,-457,-805,420,542,518,-164,-347,829,-903,-311,-974,735,-686,581,-88,645,-643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-408,997,615,-340,-779,-689,-586,773,-976,195,-528,263,-339,-693,113,-828,680,529,-580,730,601,-561,-334,-3,-186,-345,-828,296,-786,-476,21,868,264,525,608,-781,-826,-621,-997,428,-781,-625,-698,321,883,683,718,-114,-530,545,35,525,108,153,217,-145,644,-269,-532,-381,-158,903,-159,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-156,223,-112,741,1000,996,-583,-1000,1000,32,1000,-1000,-312,13,-215,783,213,912,1000,557,-1000,-151,52,246,638,1000,576,365,1000,1000,933,491,880,-1000,-952,933,416,-907,1000,-931,453,127,139,-607,-522,-898,436,-1000,-73,-1000,799,-957,187,785,1000,-440,-24,381,879,-915,871,-656,-224,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-816,308,-381,256,395,-149,297,82,592,729,899,-468,-853,-276,-83,-496,866,808,75,39,-748,-974,-8,-456,283,943,697,698,64,521,783,839,364,-857,-423,-313,-862,-816,655,25,410,-11,267,-349,-407,-588,802,-681,256,-812,271,-341,637,309,994,159,775,-694,162,-864,61,-102,-268,-638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{94,832,-313,-369,-642,-853,-1,-379,6,-515,-845,-148,307,777,-752,608,659,634,-281,704,-566,443,716,188,950,-438,-64,542,-530,565,286,268,-191,-697,-681,755,199,253,330,134,379,-931,-768,-527,-486,558,922,107,539,-593,-347,-844,-217,911,195,366,-698,-356,-31,-725,294,-382,-765,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double,double):double",
            new int[]{446,-287,48,622,-648,44,-131,489,-663,-407,-781,-720,-82,893,-959,-846,-338,-303,-6,-78,-665,-170,-5,210,-30,34,268,-925,-548,603,-490,577,-309,30,-677,499,-540,-633,-903,-793,-408,553,-834,-565,-608,580,-742,688,-179,24,993,932,307,237,520,-785,-118,243,232,-20,-255,314,969,961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double,double):double",
            new int[]{-953,-772,891,-848,-779,-641,-615,-429,-768,847,504,566,-890,-429,-150,327,-46,577,239,122,200,827,-894,760,-917,-769,-841,-350,-402,-773,122,-67,751,599,-78,851,893,-301,-259,305,557,807,961,359,365,-52,-752,362,-713,191,-258,307,819,164,-332,-943,-493,-677,528,158,-376,396,-510,92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double,double):double",
            new int[]{-580,756,1000,-437,-1000,405,-580,806,-493,36,1000,150,-649,41,-856,46,243,155,-918,-773,-1000,-1000,-580,537,1000,-499,-905,-741,212,-389,-1000,-224,-142,366,118,706,1000,250,806,-664,485,-55,-476,434,243,-1000,-1000,-632,889,580,-728,-119,-743,751,-120,-1000,-795,-580,-840,1000,884,570,-331,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{-897,-47,918,510,-54,395,605,-682,-597,392,385,-365,-155,-743,284,498,-400,341,-569,-598,501,678,254,-990,-53,-631,-28,-834,-714,-682,556,-261,-533,339,-374,-338,932,398,-101,-339,-537,-178,-236,662,-668,674,280,609,-488,164,516,-459,-70,969,-510,151,785,646,-796,792,812,925,-770,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{-348,271,-670,22,685,-928,358,408,2,-860,-877,47,852,-187,454,-705,266,-21,760,-690,-76,934,-524,408,-427,882,941,-545,488,869,554,23,-381,-643,379,851,-922,-607,501,306,358,399,-631,-996,347,-54,-846,-473,547,-397,688,-743,197,890,957,-246,-7,729,308,-192,-19,358,539,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{20,-280,112,518,879,-564,-323,53,-9,541,-499,-54,914,274,43,-456,768,355,-983,-120,-62,-628,-535,822,437,-506,-340,488,-520,874,741,-791,-11,505,-692,134,-273,-953,-643,757,-461,-787,843,60,792,648,954,794,531,936,749,-525,62,354,163,71,767,-707,632,-903,-252,-84,556,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4zMzE5Mjk4NjUzODExODI=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{246,567,-457,-927,632,324,534,-780,142,-569,363,-546,-715,994,729,426,-504,-289,-772,46,-589,726,-75,-479,-171,-292,890,-592,-377,699,-720,131,-15,-313,-97,-734,405,617,289,42,538,595,290,-48,-250,431,955,-817,542,-678,20,-381,-754,493,-287,595,-507,-88,810,-706,739,805,839,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{3,-570,-839,343,-300,587,711,621,933,250,919,-444,-144,-466,143,470,209,-677,397,-896,260,502,-264,-813,375,-379,-663,536,75,164,496,-236,611,994,81,-465,-934,320,127,409,778,-434,180,-156,495,910,-213,-362,-934,918,-911,222,-266,723,-159,-392,98,477,-580,480,700,446,351,990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Double:NC40NDE0NzQwOTMzMTczMDI=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{839,670,-203,266,248,896,-270,-992,-132,-341,-583,-75,123,-85,-593,-76,-899,936,521,-827,-672,754,-698,961,-980,-67,-921,-113,-985,839,-664,-962,428,532,856,-38,230,195,145,191,410,-345,30,-194,-843,-96,-16,-853,-424,-181,-255,108,870,-719,-908,-294,628,-243,-738,578,857,-906,-576,905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{799,-992,-335,237,-383,742,-613,-719,246,-368,907,-891,-506,253,778,443,530,275,888,212,-477,-954,-690,526,-990,-208,658,165,-987,-114,-508,553,774,-351,-968,-671,107,906,-488,-413,-160,145,-138,-402,-401,-477,-890,405,268,225,-142,-919,-144,675,-475,-391,-775,993,-356,-615,152,745,-556,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{795,-292,874,-97,115,700,163,441,-836,478,247,-363,105,-570,-69,-665,453,-127,355,-746,360,-906,-887,308,-731,604,-332,88,19,938,204,842,-622,267,-238,393,-905,922,594,418,-962,499,11,243,-239,-344,708,-112,-70,915,-475,-501,432,-141,913,-532,-281,-79,-996,578,-57,680,-897,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{-109,-322,575,-314,655,-744,527,656,444,836,103,37,475,296,748,-704,-662,582,-972,907,797,256,-829,-748,591,-87,-953,223,-941,-716,-90,800,256,-250,1000,-964,392,-171,130,-169,870,798,784,610,-13,-5,886,-368,441,620,462,9,-796,859,654,722,1,28,-790,-66,709,-908,-505,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "main(java.lang.String[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{-99,644,-710,-737,630,186,181,123,-685,-252,-15,-317,968,170,-778,112,658,-100,463,-453,330,-273,900,62,152,-11,122,115,-766,587,717,881,-589,-294,105,-473,-950,-530,131,53,-690,-59,394,-399,-811,843,1,-724,-240,-814,528,-502,544,557,307,628,-315,292,862,202,704,-70,304,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Double:NzEwLjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{-595,958,710,11,-336,68,956,-651,262,515,-349,728,-774,512,-974,131,475,-772,-745,-797,742,894,-424,336,215,33,-864,-9,-99,-136,372,335,-382,-561,520,972,-259,-560,969,41,-656,-263,-378,-706,-779,-237,138,883,422,-421,31,555,-593,-911,928,686,-328,-716,-999,-813,830,627,-447,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Double:LTY1OS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{-659,-529,-270,318,-687,880,-49,-440,768,-363,600,-59,396,-806,-156,194,457,-828,286,724,-249,-677,-783,-707,-353,714,619,-408,261,-7,-694,81,-204,-827,-622,-743,247,672,335,-70,-772,-412,558,-506,475,70,-326,-748,770,547,336,-171,477,-933,333,-521,641,-480,-364,-379,-278,-337,-47,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{-266,-111,999,871,165,-94,731,539,-735,-117,797,821,-264,-374,-353,-838,-621,-567,-381,736,250,-572,-236,-532,566,26,-14,-917,-909,982,487,64,968,-812,371,44,930,402,-85,80,680,-532,508,-509,-156,-833,525,-476,364,-118,543,952,432,468,-819,906,178,-645,-385,-260,-838,-231,317,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{-326,774,-303,830,-525,976,896,447,980,-202,-648,-20,-873,-630,-622,-30,-593,-202,756,411,-646,-276,385,106,-458,-399,-198,651,-93,-283,-385,541,386,-320,-224,445,-86,-870,-748,-317,588,774,156,152,499,474,-310,-496,-869,-894,341,268,-477,-832,695,-228,-560,-288,-34,-783,-315,51,874,-922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{-600,-43,-143,94,-311,-481,780,580,236,-254,259,-493,719,-924,-465,865,-239,-517,-575,196,945,587,-713,859,-51,-684,173,-414,-713,22,386,193,-687,-295,504,937,153,987,603,928,-928,-144,894,260,-602,-185,267,-924,578,-950,-642,659,212,520,-348,806,-30,419,-124,263,174,409,-525,768}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(int,int):int",
            new int[]{-895,-724,-209,-677,96,373,474,201,-54,649,357,115,-240,472,914,677,-436,-539,773,123,-584,81,-547,689,147,-830,984,-677,263,-856,408,-652,-967,-983,118,-244,725,-295,-732,753,-923,84,73,906,629,203,-286,771,311,-251,-210,232,829,-669,766,-23,814,-475,720,-536,-570,-286,-163,583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(long,long):long",
            new int[]{666,927,143,-75,-425,-602,-461,79,219,593,353,320,-581,-814,-639,465,-124,101,899,-688,-177,-801,121,518,525,-246,-417,-735,-803,73,-781,-943,-869,-155,-281,109,915,-578,8,326,506,-866,-912,640,-379,928,875,635,-595,188,-218,-222,-323,-615,829,349,-821,-610,813,205,-801,741,702,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{47,551,-103,-341,343,279,-187,-655,470,485,-113,951,704,960,-130,68,-806,322,92,210,220,-292,856,971,-830,-280,-309,-164,-921,-142,665,206,-142,-546,-461,546,592,531,56,446,-305,-81,351,896,-913,496,149,68,-922,-544,-810,524,189,-852,-843,909,724,-778,-736,924,620,-441,-373,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Double:LTcuOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{-78,-854,-240,518,946,-219,-338,-153,-692,823,-320,-760,856,-37,-599,-729,754,76,-672,-6,615,-528,-789,-260,-288,3,-31,-100,-379,-538,-607,971,857,-487,358,483,385,797,824,-673,227,766,-427,-499,-174,-678,319,-726,315,392,-403,-569,-629,198,682,345,972,-825,651,421,-984,236,695,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{-468,-597,773,42,978,-309,798,633,19,-413,-323,646,-940,-554,741,789,-166,723,-630,-336,371,40,-68,491,791,826,563,656,534,-514,-228,425,-102,887,356,-566,9,-685,-291,544,-237,-2,-766,78,-333,807,-712,-505,995,-209,-149,175,251,-165,934,115,-497,932,642,-916,854,-712,757,-947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{682,-413,-121,919,426,141,609,-708,107,846,-438,391,370,-535,434,-236,-319,218,-138,-598,-54,-992,780,370,-957,406,432,-849,-960,920,-449,986,-161,-306,-532,548,48,-142,243,-3,6,-522,-667,626,29,284,706,-851,215,-819,906,974,419,-81,-924,759,384,242,659,989,436,-675,-865,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{-107,4,-615,804,428,-410,-97,695,78,-261,-900,-180,689,-122,-804,875,655,527,-494,970,-651,-699,266,-637,-984,-686,-380,-986,-208,401,-859,-916,448,163,-475,620,-442,-51,442,-675,871,587,896,-480,837,-81,929,203,-495,-974,-1,-561,272,384,-43,-752,-654,-851,-824,-402,205,271,-888,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Float:NjEuOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{113,-681,618,46,891,338,-612,627,414,778,960,509,-453,828,-872,27,248,-877,171,-114,990,292,-792,-852,536,-185,-905,810,441,85,-553,-925,618,-856,639,511,-636,673,-1000,-16,-644,519,768,-583,292,-714,-155,-727,-901,334,-75,950,-89,-86,-882,-771,408,-908,857,-656,507,-380,572,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(int,int):int",
            new int[]{-304,-796,-625,897,575,-766,-597,523,313,415,-32,-282,116,312,-12,636,-962,-354,998,-548,318,169,464,-584,-691,-836,401,471,393,-791,831,133,45,-828,608,240,-12,-753,-753,523,251,-677,506,958,872,-57,-290,391,957,687,37,-996,863,-40,341,865,-596,822,470,21,-893,-229,-498,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(long,long):long",
            new int[]{645,8,669,-11,420,836,798,35,574,920,-302,720,-381,541,-57,-65,527,-565,275,298,-345,-450,644,-827,-727,-232,-935,383,828,-829,-47,-611,-481,846,98,-584,834,722,513,-200,243,-539,3,-382,435,75,-502,-416,209,-443,950,-858,-619,-395,904,-291,950,-706,721,-912,-840,-600,892,223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4MDAwMDAwNUU5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{816,-284,-307,945,105,-202,-919,-964,83,763,-339,522,594,965,303,-208,204,-886,307,-297,15,602,-592,-791,493,-608,-647,656,-79,-685,328,645,430,-191,76,875,-278,-489,-187,261,-391,879,-760,704,232,98,843,426,-519,836,-516,503,510,170,809,342,500,-70,-581,248,840,-928,941,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Double:NTUuMjk5OTk5OTk5OTk5OTk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{553,94,-115,-30,-539,84,778,-534,518,-793,662,421,302,962,104,-50,923,-802,102,86,144,917,116,-544,636,-866,35,-253,-792,668,-935,-653,-835,-189,105,32,161,290,-553,467,-167,657,782,364,367,321,-525,-313,679,775,-226,-14,-645,-712,248,106,713,296,-917,-147,371,-945,-891,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuOUUtMzI0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{152,312,992,-56,369,812,-402,-999,-804,818,-658,-602,-562,-67,-90,-923,-776,143,-495,-497,-496,-30,519,876,514,39,449,-658,758,393,-727,-448,-617,976,-944,425,-976,-842,-161,825,-844,-69,48,-997,-230,-581,-456,-886,-179,763,-745,-90,293,-902,870,775,-48,-674,-548,700,918,724,-931,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNzk3NjkzMTM0ODYyMzE1N0UzMDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-983,9,488,-618,560,-550,866,-801,663,835,-57,-460,511,-337,-255,-908,89,813,-971,-885,794,-892,-221,391,-901,-311,971,-341,-55,-65,484,769,942,761,-418,-669,-439,579,-320,-532,-460,-65,274,-566,-521,900,-169,-693,205,668,223,780,-66,-523,-162,100,-557,979,908,674,-460,493,763,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{246,129,-363,-305,-761,-504,282,-460,-887,-760,615,919,241,2,-382,668,664,-391,-866,-275,966,762,270,193,-486,969,823,622,366,-182,-319,-407,-533,-258,732,223,971,930,-912,-959,453,727,585,-160,-387,942,-538,566,-364,873,891,-907,-179,-504,584,-194,303,-703,-889,-675,729,519,-259,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ3OTk5OTk5OEU5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{629,208,-369,110,-150,-612,-975,-409,930,-79,-255,641,-424,192,696,-68,490,-405,401,294,-259,3,602,903,-425,-112,910,-850,-887,-431,118,201,741,172,-636,-237,-844,346,-404,-592,14,-518,979,-23,-83,282,785,-976,-927,862,-905,-943,395,-734,213,406,15,-728,55,-49,236,-345,1,665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-345,792,226,-205,-371,-563,-1000,-173,-309,-515,186,1000,569,740,-185,409,-859,-121,-181,-813,-588,323,849,907,-788,-236,-317,279,-274,-787,415,898,1000,-552,-880,165,-751,-131,633,45,178,-535,1000,655,-338,-945,1000,-1000,-1000,-636,-371,752,1000,-377,-824,-693,493,39,289,311,88,285,236,-546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Double:MS43OTc2OTMxMzQ4NjIzMTU3RTMwOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-884,-1000,1000,661,105,-693,-285,-964,-548,-306,-339,1000,1000,-348,830,83,1000,407,-566,-297,138,190,-611,1000,-809,-855,115,1000,845,737,-1000,-371,380,-401,-562,-509,244,-922,643,739,1000,879,578,-1000,696,-885,-133,679,-483,-51,-1000,503,-1000,809,1000,-94,-54,-70,681,-851,-118,-928,1000,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{109,-281,-752,849,336,-185,-910,916,-639,770,-831,983,-983,-24,-599,348,411,944,-896,-337,362,626,646,801,907,416,993,46,-459,264,506,-931,-797,472,878,673,-792,162,389,-854,-730,-847,838,196,588,-947,405,895,541,-284,507,-122,973,922,240,65,273,109,-582,643,485,-684,396,-300}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMDAwMDAwMQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{427,-682,-561,-590,216,-668,136,-49,-440,432,67,945,940,635,-902,-882,936,930,-409,-578,925,83,-801,-538,-444,-686,779,-225,397,739,-494,-195,147,679,775,-562,375,180,617,974,880,462,984,558,-558,355,-515,-762,-999,287,937,71,-901,860,455,995,-181,-333,692,-468,-627,-372,-684,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzE1RTE4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{-821,221,411,-455,-558,770,-142,959,-53,-70,0,849,798,-159,917,-994,-897,-809,172,360,-521,-823,-684,-472,-887,981,-274,-238,639,-511,-275,-956,113,-744,472,28,916,-70,190,-373,-444,-280,987,97,12,856,-286,690,-469,458,102,-833,599,-245,-215,-247,-822,156,675,488,242,337,677,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuNEUtNDU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{-579,-384,-909,472,-3,-827,564,75,302,68,459,945,625,-484,-236,-186,-344,549,-26,-112,853,336,-711,229,-652,600,-87,-466,467,-67,687,581,-95,13,-190,585,174,-25,-480,775,72,-443,253,-405,-388,-12,11,125,82,94,-528,-469,-247,-444,708,680,105,-121,-721,-609,-620,266,-26,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Float:My40MDI4MjM1RTM4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{193,716,764,592,419,-491,-675,-396,654,784,-421,-544,-563,-92,-831,-32,-248,-620,-789,76,-283,-479,688,-990,-5,-295,253,716,-212,-776,-491,92,278,-895,322,600,948,469,-142,865,337,-674,661,-999,484,125,-666,-460,-455,809,156,-881,-87,657,-686,-654,-485,766,-661,647,-76,-328,-186,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{885,-454,339,583,-529,-598,161,135,-685,-917,477,484,458,-1000,253,352,213,-1000,-674,933,-546,613,749,-458,-523,206,875,-569,1000,845,866,149,-1000,-10,1000,-985,491,-925,-25,-803,-867,359,658,1000,-743,-226,-699,-168,-169,-171,680,804,1000,511,965,-452,-189,160,-89,1000,481,121,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNTJFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{164,196,740,-970,-465,-687,-169,-300,-340,-635,-407,-83,-854,-146,159,-771,-252,445,880,-993,964,-753,570,-321,733,436,-727,-502,-952,756,-449,825,276,675,-216,950,566,184,-882,502,817,-391,-694,-406,-573,166,956,55,213,-534,437,-679,-202,-327,-924,-162,-946,610,169,-764,-803,765,-200,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.Float:MS40RS00NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{590,-180,504,-349,-318,-217,701,553,-906,418,-541,-656,345,-642,222,185,-618,-344,-523,597,420,998,884,406,8,-439,96,-587,951,633,582,-97,-249,-639,337,90,769,223,-595,-364,-545,-822,846,508,-346,-184,980,-402,300,318,511,-226,561,-489,512,-678,-78,-78,128,119,620,-647,343,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.Float:LTMuNDAyODIzNUUzOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{650,741,813,215,-130,-791,487,327,381,-173,-257,-425,-498,258,-411,809,620,-881,-848,-682,-229,700,-958,87,154,586,-67,-277,573,430,503,770,811,302,-534,-912,-529,-667,-528,-959,-21,-325,342,345,120,-778,-685,595,-279,962,762,732,-62,-631,-822,-989,-62,820,-833,390,747,-355,601,678}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{1000,-833,138,-960,1000,-579,-394,-584,284,186,311,13,-898,731,-1000,108,-34,981,426,-185,-1000,-918,1000,916,415,-1000,-210,1000,-192,-298,-761,336,346,483,-684,-490,-702,1000,468,-579,1000,575,-280,-716,1000,-902,-584,524,1000,163,430,687,-316,416,1000,-573,-848,-984,958,-494,-673,-1000,-338,150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wMDAwMDAwMDAwMDAwMDAy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-860,745,-784,-895,-316,615,-767,31,-857,-353,87,336,40,-433,-866,-644,247,619,-558,-784,597,-845,225,455,262,-147,352,155,25,277,130,883,-498,622,-588,997,703,-638,269,933,853,285,-49,5,330,-85,682,-90,769,696,941,668,-77,448,315,33,-295,628,763,561,-33,668,-825,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkzMS45OTk5OTk5OTk5OTk5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-932,-685,-514,987,-971,272,-717,265,183,703,-157,629,-394,-817,-167,295,-651,525,-452,925,507,608,460,-865,-504,784,129,-588,-442,735,-946,398,-500,769,-61,-248,906,-549,-992,-123,690,184,-494,-328,-266,918,-664,185,578,501,-110,541,-348,-645,-435,832,369,-856,-787,819,573,-110,-189,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNzk3NjkzMTM0ODYyMzE1N0UzMDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-1000,249,242,244,-1000,540,86,-143,927,1000,-57,-342,-563,-1000,-273,-497,-522,166,57,1000,538,986,35,-870,-485,320,939,-862,-286,165,-1000,-51,-1000,1000,497,-446,22,-1000,-163,-354,137,-524,-1000,-266,-999,676,-1000,545,1000,730,-160,100,528,-301,-990,633,389,-1000,-941,1000,32,-1000,-491,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{216,775,-168,-1000,1000,-935,367,644,-507,1000,-104,-901,1000,379,-1000,412,-75,1000,-1000,-644,177,-1000,-513,1000,1000,-496,493,23,-766,886,1000,555,-1000,130,-743,464,406,-10,954,100,1000,-87,833,805,155,-996,-23,-835,-840,8,413,657,201,-200,1000,24,395,1000,1000,-297,-511,1000,-1000,146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-543,-592,417,52,-563,-905,121,857,-531,940,977,-793,-124,588,243,928,37,608,-249,-152,843,-346,945,674,-247,605,-687,591,-719,873,-378,837,715,175,-777,801,134,-30,-108,214,-592,510,-852,-59,725,-34,-197,526,-572,-515,-372,198,467,153,-568,345,605,851,-382,588,-337,-227,304,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzNFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{366,725,597,242,707,70,973,309,347,-143,-243,-222,-151,582,45,-45,455,735,198,542,966,-640,-176,-780,-538,-441,406,-604,473,-322,-616,-814,663,-237,359,-758,-95,-397,336,375,-239,-831,-578,95,-222,583,-740,171,-201,886,-410,-893,-495,831,-161,26,798,668,376,-127,303,-524,193,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Float:LTM4LjY5OTk5Nw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{-387,250,-118,-928,-724,297,-845,865,464,-302,19,525,-659,-655,39,458,465,165,980,-960,869,-134,67,-336,-87,420,736,-458,91,-660,-235,-824,-546,-289,556,928,-872,-378,-419,-498,-482,-853,494,305,233,362,275,-381,-514,-988,-76,-658,804,-612,662,616,255,732,-606,-890,43,66,274,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Float:MS40RS00NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Float:LTMuNDAyODIzNUUzOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{311,549,914,909,574,-36,587,980,-641,995,212,470,-419,-47,-344,-246,318,238,-91,904,817,706,625,-210,31,723,-389,-301,-501,-141,297,795,-83,-909,-870,269,-493,-932,-969,-610,-544,-507,-389,-176,90,16,-349,570,-778,-847,501,677,-858,-296,552,-552,-877,892,659,-658,-51,-979,519,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{18,-149,400,274,835,-950,-690,-702,-636,134,-246,-403,-391,99,538,626,69,-406,732,683,-764,933,-105,607,208,-501,-110,-76,-361,172,257,-95,-232,289,-21,-453,-945,348,-303,838,-912,-826,-34,839,845,-412,963,84,149,-232,-775,-392,190,396,-549,496,396,517,174,263,-430,-478,-791,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{641,488,-207,477,361,-628,838,277,-585,881,-834,-930,562,-263,703,460,-576,-977,-953,-862,-836,976,876,-133,-295,664,-176,979,849,576,-565,-235,-780,-953,-300,-340,694,140,-114,503,-855,-901,893,-696,748,-170,-773,-68,824,992,-193,-74,-718,-774,307,-751,-941,-196,-520,-812,-340,582,47,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-617,-670,160,793,292,428,317,-811,-361,-408,-580,360,843,719,-967,-753,-312,381,494,-576,516,145,-62,-838,768,-292,455,91,804,143,236,736,-629,-336,398,819,-327,894,957,345,599,864,341,247,318,667,771,603,-772,-943,881,334,3,-882,-12,663,365,-403,-384,-403,-867,484,-974,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41MjM0NTgxNzNFLTMxNQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{401,-535,-166,502,-698,-1000,1000,-573,-207,-1000,-481,808,201,774,-1000,-1000,-852,482,426,-1000,743,-623,-1000,177,-150,787,-502,-1000,1000,-1000,-457,420,272,-211,163,928,68,913,415,-254,1000,586,-277,246,-876,1000,1000,1000,-1000,-1000,1000,1000,-201,-999,27,1000,697,768,-1000,631,524,423,-268,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-678,28,383,762,723,-88,-1000,295,-814,-190,1000,1000,174,-786,877,-498,71,-208,-601,-702,477,406,440,366,-573,115,947,471,-420,327,-696,380,712,-1000,-1000,48,-1000,281,1000,-59,-989,860,-523,855,-1000,-1000,-536,-95,441,317,-140,659,-1000,168,1000,-517,-742,1000,151,-701,347,350,-86,939}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-19,-50,-48,363,-981,-1000,1000,-1000,1000,-1000,-1000,1000,1000,-52,-728,-351,-707,159,375,-1000,1000,-1000,-1000,-720,-1000,1000,-312,-731,1000,-1000,-175,-41,-10,479,1000,1000,-364,1000,591,-1000,400,1000,-1000,922,-314,1000,1000,1000,-1000,-1000,1000,1000,-1000,-1000,766,1000,1000,1000,-1000,511,1000,1000,-430,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{965,624,-647,-333,119,478,748,617,794,-22,-432,-936,616,-351,171,-829,-580,29,130,-324,741,-102,572,359,-909,799,509,-819,73,-415,703,-718,885,764,380,-396,312,79,-62,106,-85,-91,696,211,-208,-134,661,-561,-948,251,-662,17,889,-79,670,663,-939,-450,-665,711,466,292,-151,290}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-401,-309,529,847,917,476,-431,-526,-84,280,796,540,738,15,539,-284,132,986,-388,-470,-93,294,786,-145,736,188,-248,157,-787,967,-22,558,633,-918,-326,-619,-584,-253,862,-17,-188,536,491,-575,-902,-810,-568,-218,292,-813,-725,-223,-836,507,859,-201,-794,442,677,-641,124,328,-562,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-1000,-171,1000,-290,1000,1000,-1000,-48,-935,165,919,142,299,538,926,193,350,482,-268,783,-420,1000,-1000,-434,494,-476,1000,-228,-1000,874,-45,759,-1000,-251,-620,-1000,-782,563,415,1000,-1000,-809,1000,636,575,-484,1000,1000,735,-539,-1000,-763,926,200,-573,-745,436,-650,-121,631,-1000,-310,-268,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{405,-327,175,1000,1000,219,-1000,524,-1000,1000,900,-908,-949,836,756,-881,-536,471,299,779,-1000,696,1000,-286,-1000,-1000,1000,-426,-978,1000,214,361,-836,-1000,-863,-1000,-431,154,622,50,225,-1000,-1000,889,753,-1000,-370,-1000,1000,-318,-1000,-1000,1000,280,716,-1000,-899,-1000,-627,-1000,-232,179,-817,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-1000,-46,175,1000,-804,-733,400,-212,634,-960,-647,865,990,242,-109,-358,811,87,226,-1000,1000,813,-1000,470,-281,1000,-25,1000,430,-1000,-1000,-877,817,-802,-100,881,-663,939,989,-266,892,1000,-1000,1000,-220,441,703,415,-1000,-1000,911,1000,-567,-1000,319,632,427,247,773,-68,797,-197,385,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{977,686,-1000,761,864,-703,-1000,-1000,-361,6,-1000,360,137,-1000,430,-953,1000,-1000,-453,-559,-340,-365,-62,205,909,482,993,-670,804,143,18,329,465,-899,634,14,-340,1000,309,345,-951,1000,341,45,-186,-974,-166,603,1000,1000,-152,561,-1000,152,-1000,-129,365,728,-655,-727,548,1000,1000,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-159,-423,261,337,722,1000,-660,1000,-1000,357,373,-720,-1000,311,-907,-952,161,-94,1000,1000,-626,40,-993,-53,222,-532,1000,-114,-242,-267,1000,940,-904,-164,-930,-1000,-207,724,540,-387,-1000,1000,623,-974,-88,1000,-514,-1000,35,668,-612,-1000,710,-659,-406,-332,-252,-544,-663,-246,-20,-311,1000,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-13,-322,162,-794,-4,850,318,-902,950,121,-258,128,-171,606,-457,-454,638,748,-211,-295,-630,133,481,-550,889,-244,-858,-799,-493,-53,788,-125,534,192,795,-590,607,-238,64,795,226,201,698,236,612,514,-563,-625,710,-805,-935,-679,973,98,-904,651,448,-363,203,539,550,742,34,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,885,711,550,1000,1000,-323,1000,1000,-536,-494,-1000,-1000,919,1000,-760,592,-1000,-1000,-1000,44,-1000,-302,690,-140,1000,1000,0,198,-994,-903,-1000,1000,58,-833,1000,465,1000,1000,680,-759,-644,-519,341,-1000,-399,-221,-267,-242,1000,1000,-320,-457,1000,-421,1000,-1000,-556,1000,-1000,1000,775,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,177,-470,-682,-557,-873,1000,115,277,-1000,1000,-273,-494,774,-1000,-1000,25,482,-302,-736,1000,-614,-226,931,-19,1000,-502,-1000,113,-1000,648,420,844,517,209,337,805,165,-45,322,-928,-88,910,-126,-1000,1000,466,311,-688,308,704,685,320,-812,-680,1000,-367,1000,-490,1000,524,-335,-116,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-519,81,-443,658,655,179,-1000,40,-1000,1000,164,1000,-1000,-921,-675,-1000,1000,-1000,644,807,-1000,-13,515,1000,850,-1000,1000,606,-1000,1000,1000,1000,-941,-1000,-1000,-352,-487,961,817,1000,-986,1000,-567,-193,29,-1000,-1000,-1000,1000,1000,-1000,-262,1000,1000,-491,-629,394,-724,632,-677,-915,1000,529,-897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{145,1000,-776,297,723,855,-722,-1000,0,1000,1000,328,0,662,419,-155,82,0,1000,1000,5,162,-716,-668,-1000,-206,612,-356,-1000,254,1000,-1000,-1000,-792,14,-76,106,-264,1000,-350,-676,362,0,248,1000,778,1000,-648,-24,0,-1000,-193,1000,329,279,0,617,-68,-1000,-1000,1000,798,-520,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,int):double",
            new int[]{-636,-520,538,131,-643,-515,792,272,230,-398,-201,-760,-20,850,-636,962,-365,-464,201,-108,722,-743,-438,472,-152,335,-577,916,-992,-110,915,809,-198,307,-663,793,-104,-550,245,-442,822,678,898,277,-564,432,629,-347,-738,-41,603,-172,299,-376,-150,544,888,-842,479,140,737,-808,527,-735}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,int):double",
            new int[]{-708,-857,-76,977,-452,-612,679,-539,-286,339,201,-279,-557,-411,34,602,-512,698,618,207,235,21,-610,-760,908,185,207,-150,-30,854,729,-83,856,-21,997,-520,-682,333,691,974,-610,804,242,-155,252,-335,320,-852,142,-165,-895,818,-148,-9,-840,-559,-686,340,-265,-450,335,-165,-75,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.Double:LTM4LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-375,694,1000,-686,1000,-11,1000,180,50,706,133,-536,708,1000,874,-904,-744,-531,1000,47,-629,-194,822,417,-400,-300,-991,-193,417,364,699,-71,-498,12,945,-362,573,501,748,-413,350,331,525,239,172,-1000,-889,-318,-748,395,170,-396,720,-551,730,-188,799,734,-304,760,-170,-56,21,813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{998,140,265,527,13,378,-462,-324,-455,90,-595,17,413,-426,262,880,769,109,872,-787,-951,516,814,495,752,-57,677,-359,616,-141,338,-898,518,990,76,35,-19,47,518,418,571,465,-577,-725,-341,-884,201,833,-964,964,702,310,-316,-29,-701,334,-356,-774,585,-500,-623,204,-905,483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.Double:LTMxLjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-311,850,364,189,140,307,-166,-66,176,-725,-743,-495,316,169,-663,418,-738,-886,-867,349,-327,-159,-296,-361,-775,39,349,141,266,169,191,-626,349,-78,1000,-873,-846,192,194,746,219,377,49,1000,-530,65,-126,-55,-793,257,115,19,765,-128,120,186,-280,96,-789,658,-697,615,96,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-303,-680,-264,970,583,-545,385,-508,-591,-898,964,-189,-697,563,23,-760,929,968,570,636,537,-281,-257,696,656,-135,-565,600,303,187,530,-983,349,-445,-184,156,226,200,-129,-450,593,-41,263,-430,732,-755,-747,-736,-526,529,854,-927,282,470,589,206,733,-555,575,-977,115,241,651,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{266,381,387,271,968,-31,550,-392,-754,471,-261,511,-442,-35,695,-122,-759,525,-637,-389,401,64,-266,-576,806,-2,773,-419,971,-379,-52,-840,442,-160,-726,-873,-200,-993,-7,490,344,0,-902,137,564,400,712,538,-437,645,36,464,382,384,-953,91,822,890,-593,-569,-176,-621,-835,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-365,379,912,-641,-105,-17,636,629,701,936,354,-137,791,-129,-518,-986,276,-558,-476,756,-190,-951,-484,-437,-10,393,795,77,-97,-786,793,336,537,-663,1,120,-692,225,-544,647,372,668,144,-806,-443,909,588,793,781,-420,529,-876,649,111,-900,-65,-476,-32,-814,914,857,-987,-236,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{587,244,192,215,896,780,969,357,-127,411,-570,-521,-17,-527,-533,429,-50,381,-99,969,-573,-610,331,-551,-82,578,863,-284,660,-30,460,-110,650,134,188,329,-968,-895,668,887,-335,-16,-327,380,-163,-769,36,96,-172,293,-278,103,-716,993,-701,172,238,-155,866,161,994,391,973,-105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{-531,705,590,89,-84,-796,214,277,147,996,-76,763,913,84,55,-219,-892,519,-292,-352,-643,-759,-29,-404,-571,-958,538,829,-668,967,151,314,-545,899,-237,-445,-896,-418,-595,345,-578,846,-629,-664,-185,-556,-505,-859,730,-431,-921,-333,-74,-148,331,-639,205,456,48,146,-2,725,-288,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{-854,-616,216,-126,-312,92,-758,-875,-586,-88,781,-181,881,-858,50,171,-154,-551,93,-857,-888,712,765,-692,285,424,482,-735,104,-996,429,667,456,823,417,-380,241,181,649,-997,831,79,282,-946,-965,-806,-148,496,211,-830,403,-593,-589,59,-551,61,356,548,786,-947,378,-525,-378,-831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{-516,-449,803,-337,-546,-688,246,-648,-921,-984,471,-885,-278,-942,548,423,52,-623,-775,-608,-927,-746,188,-737,52,642,-574,-515,197,84,-428,-814,-615,671,-403,-558,297,-991,-680,-394,-120,687,789,-279,251,-618,804,-21,-148,-642,594,-887,958,915,-21,642,-512,-903,219,981,-535,-156,198,526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-143,202,-606,-647,-619,52,-591,-257,-353,459,-123,-675,53,258,-109,549,739,407,833,-907,-457,893,-305,868,-516,878,-698,461,-539,-436,982,-826,81,293,754,524,998,-677,56,-375,-54,-631,212,219,795,564,393,-706,360,-237,-366,200,-532,-710,646,968,806,143,610,-859,486,467,-83,-151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-312,-162,218,766,-997,-483,-814,466,-839,446,700,26,-319,377,746,346,-608,-935,4,-2,780,-243,654,268,-855,678,75,-407,-344,-622,-674,911,563,-177,-103,-826,-651,-951,87,818,361,845,619,332,-359,488,-412,-765,-954,836,-294,-251,-456,250,-54,-742,586,740,-857,221,866,505,904,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-568,328,460,-640,-194,-841,512,949,282,-542,-768,-207,-387,-134,993,364,565,-739,274,806,-859,882,589,466,883,-443,363,951,-602,-559,702,-210,-562,724,-400,-495,-629,678,-977,-540,-341,-909,-491,-822,-906,646,-515,11,995,-855,-717,-988,-744,579,-941,646,-735,31,-794,592,-981,-875,-304,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-728,257,-511,-251,-990,-688,494,-185,929,590,-422,204,675,1,857,246,718,584,-567,581,-145,-496,906,-53,683,436,-225,-673,-714,859,807,674,623,873,-978,-846,-458,999,-119,372,-806,-838,-573,-567,-298,-472,709,815,421,209,-395,-957,805,993,346,-68,23,177,496,762,-230,-638,913,-824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-340,-569,1000,431,-34,338,-806,410,-335,1000,-97,-74,-839,412,-482,579,-859,-586,-113,-886,89,-450,297,357,-576,1000,706,1000,-392,-1000,721,17,1000,503,-326,-543,1000,-1000,1000,193,1000,671,1000,151,185,1000,689,-259,-48,-4,-1000,-968,-842,-1000,958,553,1000,1000,-321,353,550,627,280,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{552,881,773,944,281,-108,879,519,709,796,282,602,56,443,912,-158,945,503,533,368,-514,-628,-567,724,-106,977,398,-285,-413,-434,807,203,-377,238,156,334,886,-322,-643,-860,-554,298,-256,20,-834,-142,-103,-776,-159,-510,-911,-874,846,45,-539,-200,-342,-334,874,-757,-565,586,792,416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{604,263,-231,81,-343,-221,436,-216,-124,121,-861,-737,217,-218,-57,125,-201,776,122,100,368,402,304,794,497,388,-786,986,701,802,106,763,604,701,452,205,780,236,-534,-155,973,-311,-333,628,-982,-274,822,765,966,78,-668,-503,693,798,-578,-440,550,-758,326,919,-465,-949,71,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{1000,732,-1000,-172,-54,-692,615,-596,306,-288,972,-783,1000,-400,-1000,-614,-686,-803,71,-600,526,697,-484,124,46,-1000,234,-1000,166,158,-1000,-813,-680,-387,1000,-1000,-130,-990,-239,1000,-189,1000,5,1000,1000,643,-256,1000,352,100,1000,-86,-1000,-400,1000,-104,595,214,-126,-414,-258,94,589,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-136,496,-559,-796,839,799,-778,-195,485,285,-56,-319,537,-445,-33,-664,-947,275,-983,124,-809,-391,10,-830,483,-137,404,424,339,469,-817,-598,435,-898,-933,632,-855,401,-419,-714,-746,560,-810,19,-203,-145,-113,691,66,-889,-663,264,596,4,-53,947,221,-824,-900,436,192,177,185,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-140,263,-108,-92,1000,496,385,-74,763,1000,-861,792,784,-466,498,125,362,1000,438,1000,-1000,-1000,-188,785,575,443,915,-3,-817,802,102,763,338,-399,-1000,844,184,493,-534,-1000,-805,113,-826,38,-1000,362,516,-458,-813,-1000,-1000,-205,1000,543,-578,627,-591,-1000,530,46,-689,1000,1000,731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-898,752,558,-1000,152,648,286,-489,-1000,-806,-388,-142,725,-10,-700,605,-1000,298,-402,-754,1000,-695,-767,-1000,-802,-933,984,-810,-183,4,98,-730,-922,112,794,-1000,-1000,-175,54,1000,436,-536,-216,-1000,1000,-669,327,67,-184,-282,1000,-260,-1000,-1000,-69,1000,-922,534,-556,-54,170,207,6,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-1000,43,-973,-400,527,353,-1000,-126,766,796,1000,-105,391,1000,-1000,558,-915,-810,-1000,-454,208,1000,-830,295,-649,-1000,627,-958,639,1000,-1000,-685,13,238,-393,-1000,292,-235,-348,210,-522,118,-1000,-423,1000,848,-87,663,3,899,212,365,7,163,334,855,881,-513,-955,224,1000,586,229,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{877,805,227,-613,346,-371,-1000,1000,373,66,434,-968,-233,-57,-432,-100,-1000,-348,-937,764,-755,1000,-900,-400,-492,716,48,-580,-168,-406,555,1000,1000,-364,1000,573,14,459,-341,1000,-1000,1000,950,-1000,-65,1000,169,-936,1000,928,1000,-1000,887,-519,1000,362,1000,-1000,275,-454,-660,823,1000,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{267,805,-237,-613,31,1000,-1000,-237,-754,90,-1000,-1000,11,187,-296,112,-528,-456,73,-204,175,578,-815,-874,-492,-1000,-897,-580,-995,-328,-1000,-929,1000,767,179,780,-214,-368,-422,-339,-1000,916,721,-1000,-372,176,437,505,763,315,-280,-530,283,-882,405,182,-920,111,-1000,-333,-555,-378,240,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuNjk0MDY1OUUtMjE=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{562,942,-132,-613,1000,725,-1000,501,862,475,18,-1000,-813,-610,-408,159,647,-120,-486,703,-172,1000,-394,-1000,-502,-1000,-305,-580,-318,-384,-180,1000,645,-237,206,328,1000,-1000,-180,400,-1000,975,691,-1000,-812,549,9,-197,924,1000,637,-431,435,-1000,1000,252,-403,-452,-265,-265,-48,85,-130,833}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-119,53,-442,572,-996,-726,-642,181,-791,95,-336,860,533,898,202,529,598,972,-244,-714,906,-369,624,-826,490,-5,-119,311,-821,798,698,333,465,202,492,-213,-195,744,-678,-763,94,259,-429,657,-19,-85,753,-57,729,-654,-969,-693,-608,252,-88,-296,247,-159,526,497,582,-7,847,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{69,251,-406,1000,-1000,-695,-4,39,-1000,-166,-808,747,388,1000,379,1000,426,601,776,-85,932,-591,449,-716,1000,-794,-884,1000,-572,-50,853,951,744,-965,-270,-355,-1000,498,-481,-617,-7,203,-558,-163,-579,577,195,358,261,-289,-1000,-1000,-1000,70,48,-790,597,-2,598,110,714,-430,-753,674}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-1000,24,-1000,-1000,-1000,-223,-751,-317,-291,-107,116,-1000,67,722,784,-619,-792,-286,-399,323,-1000,-425,977,-350,-621,54,253,-14,-1000,-1000,-784,127,-818,-527,1000,1000,1000,-174,-1000,1000,540,264,1000,-1000,157,-621,1000,-527,1000,-45,-1000,-238,722,-817,739,1000,-503,-126,-62,-931,458,-101,677,731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-390,1000,227,-613,-1000,505,1000,945,-235,-29,-1000,1000,97,-1000,1000,-1000,-483,1000,-724,-333,475,-1000,-815,1000,-492,440,-1000,1000,-1000,-729,423,1000,-1000,-522,-97,-1000,-247,-1000,-894,322,-283,-1000,950,1000,534,-686,1000,-908,-1000,-986,953,920,-467,1000,-287,1000,597,1000,157,1000,-660,539,-197,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.Float:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{907,710,-151,-613,620,-1000,1000,-32,703,-1000,-1000,400,1000,-1000,-1000,1000,82,-1000,-536,1000,-1000,544,-264,-601,-751,1000,-185,-771,-142,-64,-640,-194,1000,687,576,1000,-1000,-47,-750,1000,-639,992,967,1000,787,1000,858,-633,585,1000,274,756,1000,-309,591,411,960,-304,-281,-1000,-284,1000,1000,606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{539,-342,313,-280,-1000,-221,208,667,-646,-1000,-849,1000,1000,911,283,393,554,713,-741,157,1000,-299,1000,-1000,-660,-64,-1000,700,-1000,-274,579,194,566,628,460,-161,471,-481,-350,-904,-57,-967,-459,463,-481,891,1000,957,567,-390,-1000,-728,159,-153,-356,-79,251,-1000,-547,424,1000,-431,977,208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.Float:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{1000,198,818,1000,626,199,-297,1000,-19,170,-979,1000,-602,-912,-1000,230,822,462,-698,-1000,258,1000,704,152,128,241,948,168,253,380,243,-197,-60,-589,393,-915,-608,706,346,-88,406,415,415,-656,86,-12,-343,253,-984,-805,1000,-213,855,1000,-535,-42,-891,490,-33,1000,167,296,-944,-587}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{748,-843,468,992,508,-548,-945,871,-193,-150,106,653,193,-443,-762,896,191,491,138,-286,-366,982,530,457,985,146,811,359,107,-717,315,-368,-305,243,714,-951,-565,840,-338,304,-180,161,483,-852,-467,-167,-588,625,-691,-762,730,635,273,986,-29,449,-98,17,308,827,-62,-853,-785,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-1000,-725,-933,-1000,999,-534,-782,-410,24,-666,-105,-1000,750,299,315,451,171,-398,-434,1000,-1000,-1000,601,354,252,-413,-459,262,-859,1000,1000,155,233,-740,960,1000,1000,-725,1000,-289,32,-358,-142,-1000,107,-697,613,-295,-691,898,-1000,947,243,-80,623,686,-738,-750,-978,1000,-230,-1000,-545,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(double):double",
            new int[]{329,971,812,565,984,966,-32,-505,-938,636,970,779,-313,550,-468,739,369,631,649,895,805,-377,-629,-638,309,-850,-965,-996,-965,570,-655,-218,-852,394,-17,792,-844,-820,755,197,716,-511,670,423,415,738,-204,-188,-758,617,-716,258,-212,101,483,-755,-436,24,-241,212,236,-299,-685,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(double):double",
            new int[]{-242,-914,940,-692,-552,529,-558,200,-113,819,-379,827,239,12,-31,-866,-479,704,593,527,-380,203,41,-870,-776,602,-169,861,248,-73,-484,-334,373,-954,399,380,338,182,-649,328,733,-422,-450,991,227,-935,444,855,-594,-852,742,922,-330,-427,-178,-777,-975,283,576,206,24,191,450,850}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(float):float",
            new int[]{-579,-796,770,951,340,900,-40,696,-592,67,-547,-111,348,408,-926,-378,-785,833,532,-463,-654,-902,997,513,-861,246,493,389,-147,807,-356,626,-244,-844,-387,-363,961,-812,-760,670,35,496,-696,591,-878,-370,609,699,689,-312,-838,-243,269,-512,97,886,-404,865,193,-387,-618,-716,852,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(float):float",
            new int[]{-449,-116,955,748,-352,934,-551,332,-95,333,474,644,222,-909,587,-908,903,-56,-114,-632,-689,995,153,-34,-585,-59,-924,-755,820,231,455,-737,964,-73,-380,523,193,793,432,-125,204,-813,-832,297,-73,-301,-633,62,-627,-837,285,-145,-829,672,-25,-142,-431,508,227,464,-471,-452,605,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45NzEzMTAxNzU3OTI5Mzky", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{541,-344,-849,106,493,173,798,-417,264,653,-351,485,-831,-538,597,580,843,-784,-211,-694,-729,158,942,-212,-930,-864,610,178,129,-117,-732,517,533,-205,752,940,-252,579,-432,396,465,-789,-86,276,205,-518,-134,324,818,-911,-887,-936,-657,698,873,-427,-656,-297,168,-86,-190,494,-479,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNzI0OTE2NTU1MTQ0NTU2NA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{8,-933,492,-29,769,481,-471,59,90,905,886,550,998,-801,539,-602,-466,342,-145,59,-907,390,206,-331,-876,-357,613,-132,-294,718,-300,471,-621,-381,935,-307,-359,128,-886,72,-55,899,377,401,-266,466,-374,988,-908,60,181,416,903,-406,741,576,-912,487,264,-804,-579,75,270,-610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk5MzAzNzY2NzM0NDIy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-48,-199,-618,-500,548,-790,840,-647,970,511,165,762,-382,-302,-539,-879,-260,-858,-237,-468,68,988,-697,265,292,-969,950,-580,449,570,119,267,112,100,-842,-759,340,-43,221,-959,739,-938,-87,-607,-284,-222,-359,114,26,539,-480,896,44,407,935,-113,-154,983,-595,566,165,-757,-711,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNTcwNDY3NjMzNjM3Mzc4Mg==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-528,-458,-38,-732,1000,293,-225,901,1000,819,697,658,-1000,428,182,-220,-929,932,535,201,842,859,1000,0,-106,40,-639,-1000,176,-71,1000,-81,-568,152,-830,-30,-528,86,-837,-1000,983,829,-76,975,-548,662,876,563,1000,-519,558,-188,-552,-953,1000,-610,-168,95,-449,712,958,-843,-266,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuODQxNDcwOTg0ODA3ODk2NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-345,-190,-939,-195,184,-631,-4,-342,280,333,894,505,-1000,-801,-970,-376,1000,754,-559,-445,-907,-1000,-320,588,1000,662,22,-879,-120,1000,-148,-12,-617,-381,41,-857,313,-1000,-242,-730,1000,-114,377,-1000,457,-460,-154,988,-930,833,181,1000,-388,673,-1000,54,1000,955,-824,-643,49,75,270,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-257,297,229,-244,930,53,-786,500,917,189,-211,722,-738,407,217,-482,-723,563,535,887,581,804,745,-450,-912,-226,-544,-258,-102,-266,674,-194,-660,-149,-884,-32,-499,136,421,-910,-16,557,-75,356,-672,717,317,-103,582,-881,388,139,-171,-434,354,-656,-860,-504,-224,205,358,-836,-9,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-5,-161,-183,-636,-834,-704,352,-987,421,-915,-421,655,488,-740,-530,491,297,403,-645,-343,-87,-423,130,200,-893,282,126,599,-685,786,-871,-319,518,616,708,-637,655,-773,-6,32,-410,-101,385,-445,362,-216,243,-47,-532,-337,-403,563,-724,485,-894,-826,-681,14,-904,-951,-839,56,-653,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMTc1MjAxMTkzNjQzODAxNA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-364,2,-566,-99,-707,-566,-659,-872,-626,264,403,709,-856,387,287,601,-896,-981,-805,495,824,818,447,-829,-236,-147,73,-560,946,999,-487,348,141,472,-819,544,148,-53,-693,452,685,-13,202,187,-559,357,903,-938,898,-801,994,943,702,23,-642,-269,549,989,-436,811,-190,-926,663,-584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-806,258,584,144,473,248,-677,-518,-293,-604,622,270,-942,-771,-555,-421,-831,285,-20,706,-938,278,-375,820,-601,-918,-732,-400,-169,-30,873,413,-8,715,993,-193,579,863,-521,831,-90,781,439,-990,395,-71,-761,683,396,209,-738,-160,133,-781,-985,-381,-529,-234,183,166,702,96,284,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{946,-265,17,-1000,716,-601,645,-1000,489,1000,343,324,34,-255,-1000,-787,-352,397,269,432,228,50,240,18,69,1000,409,-776,-538,1000,487,381,-564,-238,76,1000,-90,-186,-1000,157,-666,-292,-1000,-387,-835,672,1000,789,-172,-1000,-1000,-981,-1000,-1000,-351,-255,-451,-866,-108,-184,-750,747,968,-609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4xNzUyMDExOTM2NDM4MDE0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{308,-47,616,-343,76,-2,890,64,-487,822,595,325,694,175,857,-546,-474,-232,409,-387,196,57,148,-365,-125,941,71,-680,-231,209,960,452,-544,-306,812,662,308,-165,677,44,798,619,310,-281,-541,724,818,417,390,-901,-608,-374,-696,177,79,-208,-650,-551,584,-306,-806,249,113,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTA2MzYwMjkwOTk3OTMxOUUyMzE=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-533,-13,146,850,911,-875,492,-732,63,-275,-858,364,-527,-586,-470,-645,-811,-205,-422,-253,-850,-106,-305,385,-614,-38,-902,392,-558,-702,-831,732,-745,-166,140,-639,626,30,96,659,-624,833,987,-829,-727,-646,-820,538,-61,304,-82,953,41,189,-21,483,320,832,826,-849,235,843,-429,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("java.lang.Double:NS42MjM2MDc1MDA2NjM4NDVFMjYz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{608,-373,-778,-1000,906,-392,-1000,180,1000,111,315,-17,-228,1000,-1000,-282,-925,1000,957,852,-65,838,-1000,1000,-550,-772,-865,192,-1000,503,926,319,-554,-1000,-772,-609,-450,856,-21,-376,-1000,71,41,455,792,-1000,32,1000,-1000,-1000,6,-1000,-1000,-185,904,1000,-940,354,492,-1000,124,612,-557,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{535,-269,-998,386,223,-560,644,-472,-783,467,572,1000,-622,-81,1000,824,-847,-840,-495,630,485,1000,985,-1000,-590,191,425,-1000,1000,54,-574,12,255,1000,-1000,1000,1000,-200,-594,145,830,283,-379,-649,-704,337,1000,-836,1000,-1000,373,613,730,-311,-706,297,-533,262,-951,1000,676,22,969,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sqrt(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wNTIzNzc5NjM3MzUxMzM4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{431,939,163,1000,-186,713,966,1000,141,281,470,448,204,498,-741,749,0,574,652,0,814,-1000,228,1000,1000,1000,227,-627,-559,-115,-693,267,645,-905,414,-1000,1000,175,-825,0,333,-745,-196,-528,-523,0,-64,60,1000,-627,1000,-652,0,0,468,5,1,146,1000,-226,370,200,-431,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("java.lang.Double:LTg0LjczOTMxMjk2ODc1NTY3", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{814,258,128,514,360,-630,229,212,315,-802,-357,562,-361,583,233,-705,-722,101,-31,673,-868,976,280,-835,212,716,-542,538,-398,-506,-227,751,575,-722,232,-242,542,-282,-203,-842,-628,877,-767,-556,96,-950,-227,585,951,96,-700,602,354,997,-158,898,460,681,-165,183,700,-464,836,-992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("java.lang.Double:NzUuNjU2NTk1Njk3NTYwMTU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{677,395,-693,-55,148,-134,-608,24,-331,892,-95,1000,198,101,-1000,-212,-401,18,-136,-613,313,630,-400,955,660,875,1000,-352,-998,-486,-1000,-884,1000,-866,413,-1000,516,-111,-364,-436,593,858,-1000,-638,168,-1000,-724,520,-237,11,-279,-369,455,-811,1000,-132,364,-58,686,-1000,911,-536,521,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("java.lang.Double:NC4wODQyODk0NTUyOTg1OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-477,928,-273,699,-924,141,768,-253,664,-36,802,-910,711,-278,526,-331,622,2,-594,-131,-287,36,927,68,-305,750,-478,69,-615,-945,921,366,-939,956,-77,924,192,-959,432,945,161,11,42,-17,-969,37,-620,642,402,-44,984,141,989,566,-421,27,-352,628,-741,454,299,551,-675,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTU3NDA3NzI0NjU0OTAyMw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-323,-166,-507,-24,-418,-157,433,652,-775,-377,546,-170,-15,-106,463,-88,-854,-771,-471,-297,316,-45,226,-932,841,-538,849,351,-859,777,425,-231,-365,202,111,695,-765,-155,202,567,-714,948,-766,17,231,55,-325,730,-674,268,-773,-453,261,118,-165,344,-319,894,181,363,-704,-331,-350,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{448,921,-881,277,235,-80,532,970,-45,-837,-676,597,457,-760,-864,-465,492,733,-820,857,-344,-208,-21,847,100,707,100,-55,-927,741,-521,-774,133,-590,270,-755,163,-525,-20,888,205,-80,38,-270,590,397,-39,314,787,-224,771,771,38,738,-567,-775,-106,-346,745,-440,515,407,328,-709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-99,-509,-860,192,436,-914,119,672,230,487,980,-276,346,90,145,868,-552,-971,457,323,-579,704,-961,-644,-873,-775,760,-460,-579,-376,-974,575,314,-407,850,71,491,-466,-968,-500,267,809,679,367,-502,-804,268,-406,-40,-231,977,-915,-690,693,125,-514,802,752,-797,279,-298,819,200,-488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNzYxNTk0MTU1OTU1NzY0OQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{580,350,321,471,280,825,5,719,893,-354,796,384,665,-654,480,977,-972,250,739,-338,-165,599,365,-387,-868,520,777,610,-941,505,86,881,515,605,-269,-292,162,474,216,115,-14,602,-153,74,174,-404,522,-900,381,-777,-378,848,596,52,826,-618,755,26,-710,-843,20,47,30,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("java.lang.Double:MC43NjE1OTQxNTU5NTU3NjQ5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-176,-167,485,24,966,711,849,-340,-467,-572,415,-942,471,371,574,-884,658,229,359,914,-772,-613,-167,968,908,-781,-130,-628,-795,-831,-153,-885,-656,-911,502,-685,-241,213,-252,954,-371,368,585,793,449,-490,418,-405,-28,572,81,506,970,-309,633,528,-772,-590,619,370,927,180,-113,-364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-166,-289,182,109,-942,-275,-290,-102,-153,-300,-419,-651,-403,631,-413,-653,-7,974,-960,-987,-54,950,-92,520,878,444,946,-607,-15,416,-362,-910,-838,332,-530,160,-834,-978,-380,-921,294,-415,475,-23,351,493,-421,-87,16,864,573,-908,841,56,498,-529,81,162,412,578,-712,788,721,53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-151,63,-819,134,262,-356,111,650,168,60,-631,-511,-738,930,121,859,-739,183,-842,-328,-192,-706,734,388,-877,-896,385,677,-308,327,-789,-118,-625,-551,-156,-228,195,-398,873,-140,778,-776,811,-290,-113,848,-88,50,-119,179,85,-705,-19,-215,-60,104,263,186,-352,-23,-34,-90,649,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-267,835,226,252,-1000,-267,226,99,-482,-777,935,309,665,1000,652,-1000,-972,718,739,-798,255,1000,-946,1000,18,1000,147,-1000,-566,505,-1000,-1000,-788,-110,-1000,-1000,89,-597,-1000,-1000,806,-883,731,-3,871,934,522,-145,212,1000,837,72,1000,668,957,-709,-433,467,-152,401,-740,1000,413,890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("java.lang.Double:MTkwMi4yMTk4Nzk4MzQzMzMx", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{332,682,204,-786,826,-274,507,702,-867,882,-755,-236,-799,-873,876,-876,442,-833,-57,926,-312,94,-828,-390,596,-615,733,-406,410,-326,-747,-556,396,-563,912,-353,-109,345,-946,-704,955,-600,-864,65,877,-831,566,-301,-549,454,-3,-97,820,540,-27,996,-33,817,498,-7,-523,0,-975,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{-941,-557,690,-286,503,-884,-858,87,239,688,-727,-316,476,-128,331,-547,14,-323,-555,-276,-746,-436,-582,-246,671,600,792,-46,89,-316,665,607,-557,668,-497,-641,404,751,284,943,250,945,-296,689,648,-427,907,166,653,-211,-804,-965,-852,-73,547,-393,-799,-874,-609,-59,-634,112,-924,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{639,-975,231,115,-426,316,-335,274,-23,-807,-692,-373,799,-346,-781,-325,-337,-852,103,653,629,-452,-882,593,666,969,135,325,-966,-628,-189,142,-838,-351,-411,245,-740,451,839,348,-86,-575,-255,-182,222,-304,270,625,636,465,-453,-988,624,400,370,284,488,139,138,405,-749,-330,550,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("java.lang.Double:My43NDgwNjYwMjcyODg1NjVFNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{-732,3,988,-852,188,186,181,-660,-355,-558,-740,-711,-278,-702,232,-863,986,-328,165,-718,705,629,-325,-400,404,824,359,503,-918,-870,717,-180,-437,693,-4,-948,542,-338,2,-469,-378,190,466,-770,-657,953,-215,529,620,380,96,134,255,101,-447,485,-706,649,-604,-464,860,418,940,549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMDE3NDUzMjkyNTE5OTQzMjk1", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{822,530,-541,194,969,-614,218,348,592,981,200,-880,-591,-755,-610,511,-911,-525,28,-809,-287,-449,167,-866,-675,-42,-668,-115,185,941,142,-757,544,-147,-392,745,-227,748,510,-721,-495,-374,709,383,53,396,559,366,-138,567,-325,-770,-28,-40,240,581,-432,332,751,181,558,687,-342,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{997,-88,304,768,-874,-14,525,44,-556,-234,-13,480,757,-780,-696,-3,-201,898,31,-52,24,-435,-108,-764,-18,365,-206,483,302,237,-127,-592,-552,-11,-319,178,-531,-522,261,425,-229,-319,-631,-130,-442,-923,-643,60,62,-258,372,-422,-77,825,-587,-719,-830,-340,531,-395,-888,-329,985,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("java.lang.Double:MjA0OC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(double):double",
            new int[]{476,-906,-116,-532,-867,573,-647,404,75,-422,-78,-727,-678,-711,-398,-798,530,540,281,138,766,-434,-644,-604,-668,-814,380,-86,480,11,357,-539,35,-653,926,-170,436,318,534,-470,512,469,971,887,-562,-417,98,604,729,798,-507,-168,-96,-775,862,-436,461,225,527,-194,-759,-744,-917,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(double):double",
            new int[]{559,909,655,758,38,393,357,342,-315,355,856,292,-653,285,732,-970,233,611,-785,581,211,359,-457,-45,743,-232,-713,123,-951,-782,49,616,257,852,495,-518,815,220,602,909,221,-100,-785,147,-736,-373,-482,-459,-595,-269,-121,469,-363,861,671,-242,273,879,-869,-234,-567,-389,103,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(float):float",
            new int[]{388,-929,-605,738,-853,465,-266,-828,-762,55,174,-477,63,208,304,-75,-775,-221,-686,-229,-464,-852,945,464,15,-675,-38,726,304,727,846,-760,724,-556,998,-454,-886,-528,-551,-72,533,713,-226,277,936,376,723,843,999,-175,-45,198,-565,-122,-847,-249,319,-894,823,-289,226,-213,89,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("java.lang.Float:MS40RS00NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(float):float",
            new int[]{871,752,-897,321,211,736,213,-71,-774,926,-220,739,73,212,675,462,-12,-184,-771,343,-92,-780,-365,167,-82,663,-767,138,476,-91,-91,-573,-779,-870,495,-573,75,472,-311,-543,263,150,783,9,-68,779,272,-506,124,35,275,863,-29,733,-368,526,-773,921,518,-488,-511,698,472,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.analysis.differentiation.DerivativeStructure", DEReplay.run(
            "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "acos():org.apache.commons.math3.analysis.differentiation.DerivativeStructure",
            new int[]{-475,139,-492,-23,28,430,943,27,355,-641,915,576,726,270,48,-919,-568,619,-327,654,-700,364,-607,56,385,-103,201,444,-582,151,960,-496,817,326,-967,-674,227,-155,-353,-552,-513,596,-828,848,-296,-695,912,361,641,-29,236,-6,83,693,930,-108,-42,-76,469,-636,-928,233,622,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.analysis.differentiation.DerivativeStructure", DEReplay.run(
            "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "acos():org.apache.commons.math3.analysis.differentiation.DerivativeStructure",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.analysis.differentiation.DerivativeStructure", DEReplay.run(
            "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "acosh():org.apache.commons.math3.analysis.differentiation.DerivativeStructure",
            new int[]{598,-89,929,714,96,-227,111,-190,-372,-141,941,345,359,727,-749,-175,-717,-391,-542,622,-881,938,-335,329,-394,-121,-178,449,307,839,-992,229,-192,154,710,990,-417,437,-650,856,652,-532,-747,464,-341,871,749,906,38,-609,-827,-63,-490,39,-865,-673,-682,79,650,-899,-670,-576,-554,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.analysis.differentiation.DerivativeStructure", DEReplay.run(
            "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "acosh():org.apache.commons.math3.analysis.differentiation.DerivativeStructure",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
