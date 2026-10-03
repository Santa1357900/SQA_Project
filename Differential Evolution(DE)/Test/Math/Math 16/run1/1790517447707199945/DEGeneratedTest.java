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
        org.junit.Assert.assertEquals("java.lang.Double:NzQ2LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(double):double",
            new int[]{746,-205,907,675,-571,-248,862,-586,671,-847,617,416,-654,-575,877,-330,409,124,-669,-763,837,-631,850,-515,-593,-696,-888,-942,-882,-124,260,-504,906,-650,-192,596,-807,-247,-327,-603,-248,-342,945,-59,420,-375,-170,862,-778,988,-972,-692,663,655,760,666,-883,-876,630,155,514,23,-440,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDhFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(double):double",
            new int[]{804,-956,-389,74,-267,890,-204,561,595,-866,535,426,99,257,-309,322,164,161,397,-185,493,757,-454,516,597,602,400,-456,-781,-993,-943,354,941,192,-900,-828,-388,-257,440,2,240,-19,-907,-472,962,-375,435,-940,831,591,-651,154,-748,718,573,-484,-841,-254,-416,-634,236,969,368,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(float):float",
            new int[]{-323,685,341,-155,-244,-119,-341,285,-670,546,549,-150,-278,316,274,657,162,16,-577,333,-262,-180,109,-119,-845,172,815,-579,303,-886,-63,-523,675,-826,-821,188,-757,-494,550,-297,592,573,907,-110,818,-636,-239,438,-39,844,-657,-540,-369,166,846,-216,853,-2,-850,-738,146,279,388,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Float:NjAuNg==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(float):float",
            new int[]{-606,-326,236,-377,-864,-923,-38,-177,-415,-417,-62,-854,893,564,604,-416,-30,-302,640,300,-496,96,-479,294,-265,58,550,326,-970,368,966,-465,-707,585,-201,-73,895,-726,-251,-233,928,-219,588,780,309,796,307,30,-418,-295,553,-631,50,530,794,-593,339,-908,-941,-863,421,2,128,499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(int):int",
            new int[]{-433,-770,435,-731,998,-708,495,-356,-949,-802,-163,-48,483,-951,-634,171,-336,-196,-82,410,-246,-846,969,966,-964,199,-898,-770,-143,-235,266,361,592,-754,-279,-141,351,-159,752,-738,-302,-286,-276,-923,587,-790,865,-249,906,-437,-733,-21,-441,730,-952,8,851,944,-655,-307,99,387,-129,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "abs(long):long",
            new int[]{-651,558,954,581,944,295,-3,-886,836,479,896,-470,969,268,115,605,-638,26,451,-684,-165,557,-421,917,-164,881,-276,-9,95,-144,783,-527,-902,585,-159,-20,-116,0,-974,-152,582,-728,885,-781,-735,-227,81,-750,191,183,100,20,314,-937,-140,117,67,-305,-338,-974,849,327,734,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{-841,-601,400,-605,232,-239,-526,915,-518,622,196,795,913,899,476,-768,-602,-32,502,-795,301,-560,-74,-937,-410,483,-968,-609,101,83,535,-459,473,568,464,-658,416,570,-398,-527,-549,139,-671,-298,9,192,-582,-185,-104,686,288,-600,-496,931,570,-118,-117,-655,433,-807,798,597,42,289}));
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
            new int[]{1000,949,-690,538,-429,-229,143,-1000,-46,-416,-651,1000,268,-668,1000,1000,1000,-257,-533,-692,-438,-194,-618,41,-74,1000,-7,380,331,1000,-303,133,309,-250,194,220,1000,-737,356,290,30,429,59,635,-847,469,-568,-869,-189,89,-707,799,1000,-1000,-800,-451,320,746,231,575,-1000,-1000,-279,148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{759,926,603,737,245,-499,461,415,-853,-321,-646,946,-504,-931,-378,-839,970,-230,-321,166,36,473,194,786,728,-681,-818,-824,-915,-996,563,-431,-892,-448,-582,284,238,-984,-693,61,841,-74,316,521,931,-249,-774,-826,476,856,-832,-271,-975,-517,-553,694,95,124,831,-751,-203,-652,-473,-799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{-397,353,958,211,-977,669,-206,-352,-969,149,482,-647,578,-576,-102,912,720,-710,-53,322,-970,703,481,841,-27,-241,350,-244,-714,440,-352,-172,-734,827,-47,306,997,316,408,353,-982,-58,-111,-507,-282,-634,-848,-756,824,269,-126,-498,-263,982,-219,435,-786,991,716,-355,-118,658,-619,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acos(double):double",
            new int[]{-765,-437,800,-632,490,-184,541,83,-101,-517,738,305,-405,-800,-40,56,-795,-368,-688,-852,355,-422,385,702,38,18,395,302,-587,621,565,-196,476,-754,623,-19,-839,-289,262,-869,-88,743,-136,-465,-465,-574,-613,206,-780,-302,379,790,-948,468,-655,-362,989,821,-673,-888,381,-605,497,983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:MC40NDM1NjgyNTQzODUxMTUz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{11,-770,-1000,265,1000,-202,-898,-1000,-261,1000,-1000,1000,1000,-767,-1000,-98,687,-1000,-1000,-952,-1000,1000,1000,1000,462,-1000,-1000,-46,684,-20,-1000,842,-1000,-572,644,-1000,1000,104,-1000,555,-113,1000,1000,-1000,-502,897,1000,-1000,795,484,956,-1000,1000,1000,887,1000,-864,-1000,-909,741,-1000,1000,575,613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{810,-323,-487,-245,-858,-941,18,-794,-893,-815,-954,283,865,107,817,-259,34,-849,-746,14,-396,782,0,763,569,-434,530,-60,863,139,-806,535,-499,-475,241,-213,466,-518,-545,85,890,-383,-97,-940,291,-26,-547,429,274,476,-754,-954,962,177,-912,-697,-333,-365,-145,-467,798,855,447,410}));
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
            new int[]{-708,32,708,936,859,347,309,341,984,707,115,-492,-262,-690,-495,503,86,-761,-133,-663,-950,-234,307,497,76,-811,632,-368,557,508,792,-64,-208,643,229,369,301,262,137,347,-596,324,488,-254,227,-589,-115,-822,-117,628,-224,-641,-488,378,-615,80,13,-291,-170,836,898,51,-681,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Double:MjIuMTgwNzA5Nzc3NDUyNTg4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{620,351,-1,-378,-5,-215,-584,554,-191,72,916,138,-395,543,-687,-767,182,979,-327,-474,-503,-709,880,-661,820,-142,142,332,333,-967,870,-134,694,-187,629,439,760,-46,-589,61,-394,913,-658,-320,-745,932,-877,-157,-862,277,10,685,180,220,908,998,-359,-315,327,-325,-654,645,330,131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{-753,242,-474,107,-840,371,83,-824,192,-410,667,-882,-547,-997,763,-176,324,-556,608,320,-658,154,-107,-602,42,781,-840,90,-467,288,-120,824,884,-179,-507,731,78,883,-810,-960,232,-736,-847,715,57,-907,77,612,155,97,-640,279,-416,717,-891,-763,414,55,-757,93,-551,809,413,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "acosh(double):double",
            new int[]{-50,-966,-840,-827,636,683,427,-241,793,696,-882,-15,656,-904,-682,822,-223,645,594,-652,-212,581,-981,-10,604,117,57,244,205,680,-328,-914,-526,-686,318,926,-803,-53,-35,403,308,383,-755,-429,743,721,-66,-893,846,-305,-490,-949,157,941,-680,291,-330,-461,23,-485,-890,374,-620,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{576,-315,54,407,-691,228,168,-129,-358,-241,197,615,628,505,407,782,924,-618,0,-19,-218,-743,653,772,-100,-488,-612,851,493,-704,615,986,-687,209,576,-655,-786,-527,337,499,378,100,13,-922,-689,-956,926,-158,331,-140,75,-256,437,-958,446,-535,138,827,-542,-158,-846,50,166,653}));
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
            new int[]{131,-322,58,-1000,-151,772,-476,-300,343,-466,643,-423,542,454,-86,228,371,-434,687,-34,-1000,1000,715,300,-80,-862,-376,375,408,542,1000,877,-730,792,150,-60,590,-717,354,766,-117,95,132,1000,-445,-804,653,-552,404,-116,-633,-254,309,-1000,-297,882,282,802,-375,469,1000,52,540,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{-385,-131,857,-612,375,223,-1000,-840,-16,-1000,197,164,-738,-633,675,-7,-1000,-666,318,-19,-402,-1000,1000,46,174,-92,1000,-1000,-809,-957,266,-323,-213,-973,576,-71,-786,-651,1000,219,-793,291,-51,-572,991,-1000,-1000,-830,-578,-140,-61,-937,311,996,-117,-1000,-888,1000,-118,-158,-1000,499,77,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{483,-530,-524,433,441,334,403,-783,366,826,-754,-419,-895,-711,280,-511,-306,-704,214,-366,641,-686,-76,536,-773,635,260,-704,138,712,264,293,127,-662,474,680,-718,456,-50,174,-26,822,-850,-651,-436,230,-145,-605,568,545,-35,-840,123,416,-829,155,-677,-790,-715,-823,275,-575,287,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asin(double):double",
            new int[]{-427,-593,181,627,-696,-344,-678,-574,-562,-814,-634,176,266,410,-623,-993,-353,493,-213,-534,-14,-581,-51,-516,-139,-183,-962,-533,612,-611,-926,-976,538,100,233,-611,255,645,-462,272,260,122,539,-456,-264,-652,-363,-40,285,-225,345,942,-612,604,-24,-681,125,348,-18,907,-3,-161,-509,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQ0LjM2MTQxOTU1NTgzNjU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asinh(double):double",
            new int[]{-821,954,214,-423,-369,-320,-401,446,-197,200,-361,794,741,-408,148,-260,607,-149,-806,641,-560,-751,-761,218,60,-870,-399,613,-725,-448,345,507,-198,-955,918,-531,-86,547,308,-373,708,-928,331,431,140,-962,-225,-220,440,356,531,133,-580,340,-375,-674,293,-703,-905,798,-902,-723,-969,658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asinh(double):double",
            new int[]{219,-316,-963,-101,112,-391,798,502,917,-476,831,900,-708,-912,-622,788,-586,-349,449,874,400,231,121,616,638,-519,-452,-316,674,-208,-182,564,-365,631,996,756,-300,-84,171,-69,-920,786,-861,104,139,473,-516,-620,-149,145,-789,160,593,-419,898,830,452,305,307,-102,-340,-559,838,-692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "asinh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTY4MzgwODcyNTU1MzAwMw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{-414,419,959,-409,-844,191,-916,-531,159,761,676,-438,-693,441,493,-684,54,-810,-119,-596,277,-627,45,-571,-951,-240,470,19,627,422,-171,-838,-245,-292,4,748,-588,-320,-853,-78,275,991,-866,-915,321,530,180,162,-771,888,946,-908,-442,145,8,-579,-979,-626,226,-668,-401,-218,-752,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{-1000,91,633,181,452,171,-1000,-531,-283,133,676,-242,139,441,5,-503,54,-810,176,164,1000,-336,-1000,-541,975,-773,-699,264,1000,422,-817,-173,-1000,649,-837,-954,-488,-320,-1000,-431,108,405,-252,-915,409,984,180,162,-1000,-268,-661,709,169,-224,1000,-194,279,-145,226,191,-534,-628,-1000,-822}));
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
            new int[]{494,30,-646,504,184,30,-26,373,581,631,-156,-497,-973,-585,-646,100,-352,945,125,105,-343,-668,698,-336,157,-538,-749,262,253,-14,904,-650,310,-234,-563,373,208,-430,-414,-199,-432,524,472,944,-895,70,-826,-839,-517,979,-34,370,369,-625,-674,714,-290,-354,-529,-313,-267,-386,-128,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan(double):double",
            new int[]{-3,728,-582,-412,-536,-10,83,407,-197,-229,-892,322,123,-524,571,-518,756,-448,10,508,-301,-996,302,-871,-546,-511,942,249,-215,547,506,-760,-47,142,-669,367,981,-223,816,870,666,146,-904,500,-800,-450,325,43,-511,-902,318,755,-404,113,867,-520,-946,80,704,-226,241,-3,789,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMzU2MTk0NDkwMTkyMzQ1", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-581,-8,-696,1000,515,-660,680,281,69,1000,-790,47,1000,-38,182,-49,275,53,1000,-782,1000,-657,662,-167,-405,-225,-33,-356,56,-854,762,66,-1000,1000,445,-560,-658,586,-1000,654,-694,-359,-485,199,-263,614,-1000,-159,-147,133,237,95,-601,-127,-384,-599,-554,-329,585,730,964,-1000,-1000,716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMzk4MTU1NjMwNzUxNTg1RS04", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-515,-194,678,-621,-447,712,-152,617,-169,-83,-688,-647,807,-402,641,-95,-12,830,864,-10,-593,-383,-60,938,503,6,-808,-971,-158,776,358,-41,-547,267,420,-58,765,-592,682,820,-921,-316,-285,679,318,891,737,-47,-606,859,728,-250,-62,33,-794,252,395,-230,908,412,-513,543,878,-102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-280,-420,973,-75,-1000,246,749,923,296,-959,944,1000,-1000,-1000,302,1000,154,-606,16,1000,800,-245,989,433,518,590,545,29,1000,-419,-207,-1000,-671,-1000,-761,992,-655,-434,1000,230,652,1000,433,-628,136,258,740,-923,46,755,647,312,-1000,-524,1000,-80,-1000,979,-1000,-897,-1000,404,-602,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{339,-78,976,-755,-789,-1000,-647,-890,753,-464,-1000,315,256,1000,27,-674,-59,-995,-604,-119,-342,1000,-741,742,-803,-1000,120,1000,-331,-1000,1000,291,-393,-59,-444,-622,28,-248,-277,-832,-393,-580,-127,1000,-436,-672,-572,-334,894,765,-387,-648,-654,215,-177,446,7,85,-317,258,-47,557,361,-30}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-286,1000,-1000,480,-489,-354,551,-688,299,-174,-61,-565,-668,1000,657,-105,920,-1000,-1000,534,-820,1000,-1000,-315,90,-1000,922,447,373,-927,-370,1000,-1000,-962,-380,1000,731,-755,866,-1000,-942,-1000,-940,1000,323,-798,1000,515,284,776,-1000,-1000,-848,-1000,320,-164,673,1000,-45,-146,-133,493,-131,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Double:LTMuMTQxNTkyNjUzNTg5Nzkz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{60,-666,37,897,776,909,672,-78,-140,-83,-688,231,807,764,1000,600,1000,141,-935,427,-948,-383,890,-307,-534,-427,1000,-971,748,-377,-989,410,-20,685,-392,-379,662,-283,388,536,486,185,-159,1000,1000,531,1000,-1000,-606,-546,444,-58,-411,33,87,949,476,-423,1000,1000,1000,585,878,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-1000,965,-1000,96,-344,-861,-735,-827,750,-381,-1000,-710,501,1000,-652,-764,474,262,-1000,1000,-1000,1000,-120,-59,306,-1000,-586,477,-296,-313,-511,-877,-1000,77,182,-1000,872,-893,1000,214,453,491,-1000,1000,279,-919,882,1000,-167,1000,-289,-488,-1000,-837,-46,136,-760,18,1000,263,230,451,-124,620}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-952,-145,302,-580,650,1000,798,1000,-235,827,-456,56,960,-279,735,-491,-25,1000,1000,654,-223,-375,176,-123,-762,1,-774,-663,-1000,93,628,137,-1000,-162,92,-992,1000,-730,-208,869,-1000,52,-140,-224,359,846,657,-1000,239,725,814,313,-916,1000,151,972,-389,-568,1000,760,226,-342,27,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-573,903,-153,-411,270,324,1000,785,397,1000,-973,103,768,231,-318,-286,-1000,1000,922,824,591,-664,730,488,-1000,-17,-399,-210,-1000,-181,1000,-174,1000,979,-647,-1000,100,-267,-1000,878,-1000,-609,-5,-460,-276,-631,-1000,-1000,459,691,1000,1000,-669,715,-367,-52,-1000,-957,246,733,152,335,48,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-123,-420,-351,-186,227,935,749,-959,352,-449,944,904,815,165,338,414,242,102,-650,924,-205,812,989,433,-623,207,545,567,703,-546,-980,425,720,10,159,992,37,114,234,-327,987,684,890,638,762,644,302,-859,625,-939,647,747,665,147,478,-513,755,-796,956,372,419,857,-881,-608}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-478,601,531,-1000,-68,-213,279,188,-521,-714,-1000,-1000,278,925,-555,-538,564,556,1000,-114,-1000,-468,-365,607,-126,-803,478,-916,92,1000,-907,1000,-678,77,725,-268,1000,-730,491,-103,-588,-777,-250,1000,1000,826,-130,944,-1000,749,-203,675,-77,-194,-358,1000,-285,604,1000,799,960,446,1000,433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{396,-977,-360,105,-111,118,36,963,387,-768,196,435,228,-38,-334,560,275,810,-274,343,619,33,662,301,-115,-225,-896,-450,56,162,24,-842,-905,-818,539,-9,-213,586,949,148,225,-197,609,199,-867,922,157,-84,-147,133,-617,-26,203,753,-384,166,-337,-103,860,93,392,-779,-866,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzNjQ2MDY1OTMy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-212,903,-812,574,-948,633,310,-238,-27,-194,-247,-346,-696,648,-579,-319,175,-62,-888,824,-276,717,-947,-157,644,-138,-399,962,170,-173,-638,330,-584,6,-546,-876,100,-433,20,13,725,-609,-834,805,21,-223,502,628,91,942,-804,-289,-886,-939,15,-52,-321,-461,246,-159,-135,-447,-452,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NzA3OTYzMjY3OTQ4OTY2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{-808,-427,-660,169,691,383,-9,760,116,953,-82,760,172,743,834,-182,532,816,934,-46,852,-911,166,-347,-912,266,446,-387,-586,-458,796,233,-628,247,-81,-540,83,60,-968,446,283,164,-89,-387,794,736,-946,-800,580,595,625,128,-980,452,428,-887,-797,243,512,355,121,-694,-755,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTcwNzk2MzI2Nzk0ODk2Ng==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{49,-507,-175,-846,-929,-119,-983,-516,878,587,-656,629,-942,708,988,-396,-234,360,456,984,-521,-201,939,-115,732,798,-131,-134,528,0,560,983,487,89,365,787,929,61,-954,849,-896,610,247,-403,551,707,-127,671,167,815,-63,789,4,876,-966,233,17,-822,959,413,54,-460,-802,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atan2(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Double:MC44NjczMDA1Mjc2OTQwNTMy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{7,538,576,-38,11,-70,-256,-238,27,696,195,527,-169,-460,688,-942,718,379,40,-827,-212,18,491,548,168,83,338,-259,-780,-807,927,709,-468,-704,-612,884,-204,116,-645,661,569,-253,-721,-993,-669,-439,-27,-148,277,-204,-492,862,839,490,697,294,-957,716,76,700,-472,-517,-524,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{-452,-423,892,-502,558,-308,-697,330,631,81,-392,422,837,-437,190,-340,153,478,791,-576,-201,125,-876,-430,184,-434,601,-984,129,-343,440,-346,-145,-835,682,-932,470,814,510,299,-386,196,661,792,-650,-374,-311,-423,366,223,-624,-303,162,769,698,-9,-31,-786,370,403,-101,122,-737,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{367,782,-765,979,-764,-600,546,131,-629,880,-637,-381,597,-641,683,754,387,275,88,807,-945,951,936,163,-764,375,457,-884,790,-276,-76,203,957,-892,327,687,-32,69,-141,-392,130,-601,900,685,-103,30,175,271,-591,925,285,545,551,183,-89,187,719,-115,-351,-118,-836,-381,651,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{-725,148,106,-664,928,643,372,509,-44,467,589,-964,-148,-15,224,912,-943,316,297,587,376,821,-785,-808,263,87,447,-551,-448,-865,-848,-163,962,581,197,361,23,475,-196,-933,924,622,-484,288,-739,351,92,561,-368,-127,529,97,-397,663,-534,794,707,-379,887,-169,-204,-847,-710,-896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "atanh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cbrt(double):double",
            new int[]{968,-502,-522,-313,-296,-50,189,835,-559,-537,-633,814,757,105,-768,474,53,-847,663,-683,435,-91,-852,-248,18,-870,-584,408,-152,760,-756,359,-206,-273,-504,865,264,-575,388,750,416,229,-865,-79,-359,-115,-301,672,527,-67,-803,406,270,-519,-362,203,-458,563,180,-740,-808,94,-188,-360}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cbrt(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cbrt(double):double",
            new int[]{-177,128,-6,-250,442,-920,916,940,656,998,308,-713,-495,581,410,293,768,716,-980,-72,659,702,307,-963,-898,-518,-698,-233,-53,240,740,-422,-320,815,-495,-891,-324,250,846,-266,-168,-498,-283,967,-443,-157,152,-926,-474,-194,-594,200,323,670,-265,129,196,-593,-818,391,-513,808,-531,837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Double:LTU3LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-579,202,-283,-294,-375,536,-759,1000,981,-814,1000,-1000,355,-728,-919,1000,-810,483,635,-372,1000,-213,300,356,-179,985,94,-1000,302,-64,8,545,-290,889,-250,1000,-1000,797,-593,-583,-534,458,928,-30,1000,389,1000,627,673,652,-341,282,1000,488,227,-49,540,1000,650,1000,-1000,557,449,439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{254,210,-391,394,-573,-74,-750,-930,29,698,944,60,-316,-962,-466,536,21,-893,-851,-172,-529,973,138,984,315,-79,257,-191,-881,-572,-876,-991,797,510,-963,-571,186,-814,985,738,-743,67,-96,403,862,-780,51,261,-754,-153,-246,637,-507,-397,730,554,522,849,659,-701,-722,615,777,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Double:LTY0Mi4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-642,-853,307,-394,102,-35,191,421,-382,-276,-187,-665,-697,879,272,-619,-267,-189,749,302,-376,-634,-816,-801,628,-823,529,-494,330,376,307,-608,-102,336,317,50,-495,-798,-4,-315,-154,-863,-760,511,-123,-547,262,-443,-769,94,-537,-335,686,282,-771,716,-474,601,64,823,669,-765,-461,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{-412,557,-656,-163,154,-382,537,502,-381,-692,-872,-88,-806,112,-258,-80,792,793,-511,232,-882,-573,-400,-238,555,-480,408,-381,-736,351,412,407,-981,-241,-649,-160,-42,-315,-125,73,629,-33,638,-409,-469,110,-508,42,658,72,803,-48,493,-571,-137,921,-654,-233,-121,-408,-29,-436,-930,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ceil(double):double",
            new int[]{568,115,-765,703,-352,352,486,-810,196,830,-280,-627,-621,164,13,1000,-452,-1000,650,418,-568,231,1000,698,207,409,-941,611,1000,-471,526,117,734,950,-1000,-104,183,760,-460,571,-1000,1000,760,-1000,1000,611,1000,-1,-352,-392,-433,175,1000,238,832,89,845,1000,564,-148,1000,710,-1000,-65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{-871,-327,-925,-421,447,-594,215,-683,-707,771,-466,376,-532,-758,883,829,948,-912,-544,-629,686,-502,-906,309,-46,448,-657,942,662,-266,735,-167,-695,-500,-502,-882,913,-483,158,-221,720,-471,358,-452,128,18,631,-962,-25,-90,816,556,158,946,364,81,160,-300,-149,-65,224,-438,-305,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{126,528,120,-834,109,-694,430,207,771,566,-937,-972,-820,-870,-387,-174,163,-320,95,63,136,444,700,118,371,-237,-956,-937,-673,-369,-419,736,-124,-753,541,964,995,507,-925,515,823,439,-892,-817,840,632,814,239,-612,863,-388,-579,734,-676,91,636,-548,-866,397,-440,-972,133,-761,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{-434,398,-717,596,279,-950,-501,-809,-599,-641,-15,708,992,-19,-600,715,-988,-117,360,468,271,-849,847,136,827,478,404,128,789,532,628,-708,-294,321,-808,417,-540,-399,890,251,-106,399,78,980,294,811,242,-964,-731,363,460,984,589,446,999,-744,-908,-415,-591,507,908,-360,154,540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIzLjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{-234,-890,-101,202,814,446,-438,560,857,507,-302,-788,-893,752,-232,749,545,619,722,767,-934,-144,144,-881,402,613,-208,33,-386,-297,138,-391,-433,767,-692,24,-806,794,358,835,-396,-569,-744,-821,122,822,72,376,-812,329,-47,483,748,437,243,665,-802,-626,-258,-97,-729,533,-422,-457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{895,-609,967,-298,-767,-18,-404,-510,153,289,242,-60,629,646,453,547,-369,-409,365,-652,-375,-958,47,-485,-150,-146,997,789,-51,-842,695,-156,-858,-719,875,-603,-463,-282,-243,3,411,614,-140,872,-680,541,210,741,-222,940,782,863,-126,580,-185,-462,-294,-430,-701,-773,-211,64,358,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "copySign(float,float):float",
            new int[]{-657,666,380,430,-441,-12,-443,993,-105,-173,-664,523,-165,-735,933,113,662,-662,731,243,-426,-218,666,851,-231,-941,-858,851,458,-520,776,-446,-623,-436,994,190,-251,-510,-907,-138,-74,489,781,603,930,-689,367,987,-991,537,-176,706,961,434,737,-81,-527,301,-195,788,5,793,-991,597}));
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
            new int[]{169,15,944,300,-520,562,764,-2,459,190,846,491,217,-955,-54,-844,-850,-90,-321,252,-808,-900,103,-676,281,986,-604,-441,-93,884,784,379,378,-958,297,624,167,-910,540,315,-439,-568,235,144,693,-168,992,629,37,-329,-528,-834,-580,-741,941,-921,-284,938,-946,239,-491,721,618,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4wMTE4MDAwNzY1MTI4MDAyMzY=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-563,-426,-664,-508,-515,-807,230,255,450,-717,227,-244,589,-857,872,769,565,762,-18,11,656,42,-86,90,565,-291,-928,583,-48,614,514,-26,518,-880,969,-716,-766,-90,171,848,-853,-324,-35,-138,-918,-119,-615,103,507,80,-422,508,867,-888,-719,220,-258,-99,-575,86,-641,556,324,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuODAyMjA1NDI1NDIzMDQ0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-779,550,-518,-292,627,-866,591,-220,-162,-589,355,158,-679,-538,-867,-485,-303,554,630,-348,251,-279,783,-757,-896,-785,553,-770,-134,-294,158,791,284,-875,-232,-99,-831,664,222,-885,712,-412,947,-605,205,-761,937,573,123,-111,370,319,544,-30,-746,399,978,-423,-647,-570,-212,-852,564,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4yMzc4MTYxOTQ1NzI4MDMzNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{1000,388,-1000,5,533,1000,43,683,1000,-257,-61,-77,750,-462,-342,136,408,-1000,-766,1000,778,576,-713,342,-707,767,429,190,-767,-1000,34,-732,-395,-108,-819,107,-76,990,-35,-995,314,291,783,492,882,681,569,908,1000,-957,-737,1000,-297,688,-1000,880,-932,816,123,-61,679,1000,-286,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{836,837,607,749,-859,443,-247,-985,-312,640,-341,729,750,624,-888,-545,-922,737,-817,-150,141,748,-126,711,-684,-649,-233,679,-806,-171,34,-880,-162,906,674,797,645,367,389,551,258,199,120,-569,882,-258,-68,409,-695,-413,-304,203,955,-160,-227,-464,-610,-245,820,-337,-230,-279,-286,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41NDAzMDIzMDU4NjgxMzk4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{944,-190,-579,-520,651,996,854,582,540,-198,359,-821,-361,460,-238,940,83,-988,-869,855,123,540,-606,-178,-877,883,638,710,-231,-369,-931,-249,-528,260,-906,228,-338,838,-498,27,542,900,393,814,-261,804,124,345,250,-814,-704,747,107,-56,-379,479,-753,608,-661,96,242,935,-195,-2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cos(double):double",
            new int[]{-787,-53,-126,798,-503,589,281,-465,-659,-535,-593,-258,-186,545,-971,-915,751,-238,261,-605,-844,-451,-371,284,-770,-15,-636,593,763,-438,-156,-754,306,655,603,-794,-685,-655,-106,-207,-715,404,-627,-128,-210,-12,178,-875,-703,856,-613,961,637,-222,-350,497,-899,203,-350,456,-726,-753,975,864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi40MDczOTY4Mjc2MDkyMjU3RTEyNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-294,119,-788,288,516,-27,-836,-941,-557,35,-162,572,-634,-599,-523,796,509,-98,-705,-127,-238,-198,-138,-882,265,-532,747,102,-674,-1,-7,-84,-466,-640,558,-709,728,758,461,486,-592,692,48,-449,-904,-590,-472,952,199,343,723,-638,997,-509,855,-92,515,724,-911,131,-751,465,570,504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-663,-379,605,-683,-541,-422,258,-753,568,568,-17,856,-385,201,592,527,385,872,-580,855,-609,409,-338,410,-486,-206,-593,-885,367,799,-184,-796,-914,-33,447,320,101,-441,-661,-484,-650,750,160,685,-691,-288,421,-775,272,-998,-401,675,378,-876,-151,436,15,93,906,-252,-544,349,792,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41NDMwODA2MzQ4MTUyNDM3", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{422,662,49,570,-574,-823,-488,-164,100,-917,890,312,-540,647,-901,-896,-22,783,-726,-845,917,842,-702,-804,804,-730,-683,-366,40,-742,181,107,375,112,392,59,-141,204,-333,874,-280,-879,964,875,-378,892,-488,786,-270,-995,-796,-846,126,-629,-423,648,240,-672,507,-825,-826,-662,757,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "cosh(double):double",
            new int[]{-650,67,-936,136,-51,-31,290,-125,9,-810,-971,1000,296,-904,553,187,-745,479,96,753,265,806,1000,183,778,1000,-1000,-390,481,-457,-1000,1000,521,394,184,580,-82,-422,763,-754,115,29,114,645,727,860,402,166,646,-1000,-230,-431,-185,-375,-589,-120,-459,-617,736,1000,-826,1000,300,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4zNjc4Nzk0NDExNzE0NDIzMw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{-497,2,-32,932,738,-600,57,148,836,290,-751,226,-259,-525,222,-783,-79,-75,-545,947,748,200,-906,-646,-418,615,-94,87,55,-162,124,468,-534,801,187,-854,623,619,-399,616,-976,-466,-555,-876,-198,813,411,-563,547,-987,-301,451,-206,46,550,-322,745,-927,-219,280,-385,-571,-727,-499}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{939,207,860,-984,-765,665,730,-629,-389,-736,566,422,-752,315,-444,701,-265,973,638,-671,808,659,794,-975,-901,-556,-28,200,630,30,-830,-301,148,205,393,993,895,-584,435,-538,882,452,726,760,354,-867,859,201,-260,-602,-183,625,116,168,428,-763,969,16,-885,561,859,-223,605,838}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "exp(double):double",
            new int[]{-420,-63,673,349,334,244,236,-928,411,-282,428,574,-268,-153,-274,-263,340,-17,333,-957,365,-73,240,-521,-447,350,783,575,457,772,-933,-281,718,73,-184,-834,-621,274,-566,945,-356,671,375,-726,111,320,271,-997,116,344,-312,-851,570,-451,875,587,-603,-536,-581,666,550,40,-677,641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{-719,-49,-1000,234,58,543,582,-1000,-1000,1000,943,1000,-278,-83,1000,-590,22,-1000,270,-1000,-326,1000,101,-387,206,211,380,-24,1000,636,-1000,-857,1000,-1000,-422,-605,-1000,-305,-141,1000,513,649,305,28,-275,1000,-1000,-797,-522,-723,1000,-190,-345,1000,764,1000,-1000,-886,1000,-1000,968,278,72,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:My4wMzI5MDc1NDQ5NTk2MjhFMzU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{817,-854,660,-107,15,-715,-820,-928,-249,-741,-840,-78,-71,187,-725,444,291,935,100,747,769,-495,230,-283,-803,-488,385,-628,-779,326,340,897,-902,779,-578,981,577,798,824,-221,86,792,-930,604,32,-843,573,269,182,-91,-581,-96,-164,-904,275,-981,744,395,-382,601,610,-909,-633,133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{914,431,-591,-16,283,-458,-138,-642,-792,566,886,892,288,81,792,-568,-269,-453,679,699,-316,-275,544,-920,294,-706,55,673,205,388,-770,228,-882,134,-833,253,-510,670,-502,288,521,665,-712,76,126,833,-268,244,-217,630,204,-59,858,920,-63,877,-724,189,-41,-779,281,-350,-236,861}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{-784,-56,-510,-122,605,-960,-414,-149,-273,-31,450,-488,-397,-198,-685,-549,23,126,-937,-236,916,36,-385,480,-755,-381,-855,615,334,328,-479,-795,-571,-982,-668,-756,774,-178,330,-753,-485,549,-769,831,335,-181,391,630,-100,-225,98,668,-118,880,-28,-664,-356,40,-753,-908,497,-909,-364,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "expm1(double):double",
            new int[]{474,-653,752,314,-924,496,-950,807,726,-342,78,-345,213,-954,29,-369,-531,802,-122,-688,972,242,-680,157,-661,-524,840,-334,87,-327,-51,-720,470,-453,-512,332,-236,126,-517,77,-596,-696,-969,582,981,239,-630,76,247,-706,-559,-710,23,-149,-958,230,155,102,-62,-130,552,-306,217,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Double:LTM5LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-385,-146,-645,-672,-787,-744,-478,718,808,-191,-715,-204,885,-492,-447,24,-769,280,-91,812,-602,-667,-865,-70,486,713,863,-934,-473,191,689,150,369,-230,952,-194,634,849,-610,-448,-960,-585,-160,234,-781,494,-711,579,971,539,-322,-285,-135,-569,-913,66,-946,340,-793,90,-772,-190,306,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-913,93,381,398,819,372,247,-434,17,905,188,373,238,893,95,-931,-540,-120,-387,596,122,-930,290,692,-199,273,107,-11,665,-548,-430,-410,486,-963,488,-639,-341,-63,474,437,-600,466,267,307,119,-680,993,217,-215,-868,696,655,79,174,-381,-847,-940,319,8,-216,-836,-493,544,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-559,-226,-813,180,0,317,-818,-15,-147,383,-729,-753,-687,392,-133,-922,440,299,331,-536,-761,923,320,5,-563,-799,81,-80,981,56,-648,37,-11,-232,-555,217,-432,-513,43,917,659,844,-758,-499,837,491,468,241,905,834,-974,-21,-6,238,263,937,-101,-463,566,238,418,815,-223,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-266,908,-252,797,703,-901,844,-89,431,-700,-748,110,-314,43,-782,632,-187,136,-871,946,-36,830,-13,-245,528,706,-689,955,-354,-254,-105,-994,-703,-803,-250,905,115,-355,481,-753,-146,982,422,698,-822,855,-128,594,351,628,965,-616,-686,-149,-106,142,-395,-147,461,477,-345,151,-714,836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "floor(double):double",
            new int[]{-672,-281,14,421,342,-639,728,-263,176,-1000,-1000,161,-1000,319,-864,1000,-779,277,-452,595,898,669,34,-348,748,1000,-464,-139,286,40,-75,-1000,-175,-845,-1000,1000,-410,632,410,-1000,328,1000,1000,313,304,1000,-556,1000,247,186,387,-888,-891,843,811,-35,-340,-798,834,11,-883,-134,-1000,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEwMjM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "getExponent(double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEyNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "getExponent(float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-69,-455,10,-703,612,-588,-596,18,-513,217,222,-250,778,924,-539,574,654,380,75,34,-949,-816,-253,33,-425,-82,273,-377,-25,978,828,236,-356,-583,404,487,-722,-263,642,574,-593,338,-71,-468,839,-520,-797,107,-760,25,-842,-143,41,988,-263,-766,655,-36,-281,-111,609,-120,966,-829}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{1000,-6,-115,831,-474,1000,-384,-913,655,-635,-499,510,214,-1000,-472,-1000,368,90,794,-299,-70,-503,-609,1000,1000,456,12,988,59,-420,880,-380,-999,1000,556,-282,1000,-1000,-1000,-749,-422,649,871,-205,-24,114,-729,380,-517,622,775,-867,177,117,408,265,-1000,-1000,-72,-345,-709,-291,863,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{598,268,24,-77,-868,620,192,604,-170,181,-395,99,214,910,-25,-844,456,355,-121,259,50,-503,235,650,518,-969,445,-479,63,999,349,109,-999,-156,212,965,550,-537,-156,581,54,642,667,-205,-876,366,-964,887,-695,950,-554,-867,866,652,408,-267,121,505,-301,501,-734,481,-535,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{-285,-391,920,-735,937,848,-27,724,286,996,801,-717,-318,363,-139,354,380,657,372,-459,733,837,-1,-225,560,-298,-31,599,-584,498,124,709,965,-18,-475,399,-600,917,-920,478,391,726,-630,433,375,205,-624,-853,215,585,101,301,175,-999,-762,-235,-999,-518,989,-291,461,920,865,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{573,-185,-286,-542,-92,743,395,59,252,-296,937,182,-294,1000,201,-977,516,239,-452,-1000,-201,273,-35,-227,-632,1000,446,-318,245,339,-72,194,-213,-119,180,-422,-110,186,746,1000,605,-766,121,-81,364,-52,-112,-171,-140,110,-621,1000,325,-346,-20,-639,257,342,-332,-206,-457,390,-318,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "hypot(double,double):double",
            new int[]{143,-244,-768,529,-421,856,264,-836,974,-225,-121,-81,678,-229,-613,-298,355,-544,106,-792,16,82,544,976,687,445,312,381,794,721,557,-812,246,704,800,-154,941,-546,-57,-387,-50,116,900,844,0,-171,-175,-751,-978,64,226,-196,533,-529,-606,143,-599,-626,-475,-303,-868,-584,654,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Double:MjEuNDg3NTYyNTk2ODkyNjQ0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-724,-177,-344,823,19,-174,486,592,-413,-400,-602,294,831,948,609,-116,-296,-778,822,802,-339,909,-546,-912,812,992,784,700,626,-470,132,621,4,-362,-830,139,-128,436,-345,-639,-896,923,471,420,541,-252,-266,-868,155,-170,-149,913,985,-529,988,659,375,-832,-852,-16,276,-339,-789,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-146,-47,712,937,-452,-22,878,756,-201,-586,-508,426,208,-269,-454,-434,-404,974,289,-668,558,17,-686,-522,-499,-934,-410,567,862,41,6,-448,226,121,-106,784,-590,242,-751,153,-773,-404,949,-522,598,-692,-908,-424,-29,-622,807,-553,581,686,-650,-38,983,429,118,-910,530,692,331,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{1000,-797,-823,408,816,-855,-1000,-311,-956,1000,-173,-98,-927,692,98,4,-75,85,520,-359,396,1000,-463,-1000,234,636,498,403,1000,418,-175,-911,-919,384,-521,-488,1000,1000,920,-936,934,345,500,-1000,660,-1000,-75,705,23,-326,-412,-524,354,-693,-196,1000,931,250,-1000,87,1000,1000,639,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{924,872,-651,554,560,-701,-800,-663,-670,446,-229,-444,-786,467,839,836,-659,452,-134,311,-57,-65,522,-433,757,960,740,17,-473,32,-687,138,-687,-33,976,865,630,-855,169,38,92,-59,-769,-730,-775,-783,-361,857,-652,-310,-909,402,-969,-308,727,748,-444,405,-407,488,979,-8,-443,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{-53,16,-429,-479,34,827,95,221,662,-628,904,-22,-321,-618,-65,883,-859,161,386,-507,357,-43,225,-444,-365,572,196,-688,-664,979,-701,246,-125,408,-395,-965,-379,825,547,-986,-673,-975,382,-42,-332,-338,-645,-744,738,462,-670,-421,965,-419,92,69,-231,618,-624,-237,-461,-846,-223,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double,double):double",
            new int[]{1000,-805,-1000,517,-203,-632,925,566,204,296,133,265,1000,42,-113,803,82,-589,1000,-387,683,-390,-219,116,-442,-1000,-901,-1000,-16,-1000,-202,-262,382,-1000,779,-1000,-787,-407,1000,756,-1000,-53,-962,376,-509,-739,338,-1000,431,370,965,1000,41,815,-512,53,-241,43,310,-147,-1000,1000,589,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double,double):double",
            new int[]{-293,-154,815,236,751,991,925,803,403,-253,-543,265,82,-10,-516,-789,142,200,-775,632,-119,-574,819,-229,-615,-165,-534,-847,-424,-751,-354,487,-328,991,-369,961,9,573,877,539,643,-747,-962,-676,514,-611,338,-202,46,-410,809,-230,41,-797,525,296,-536,-615,358,443,291,152,-970,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log(double,double):double",
            new int[]{-863,288,-38,319,418,231,-888,135,1000,173,-337,-79,-198,-115,-713,988,789,646,347,34,987,251,75,258,73,718,-862,-1000,-182,1000,315,456,-365,-464,3,1000,-509,-1000,-1000,99,54,-1000,-84,-979,1000,221,-612,-699,221,167,-454,-165,1000,293,-260,428,-104,1000,521,-334,-226,634,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{21,-971,-878,295,-735,-826,-926,-793,-970,479,-176,-541,-352,-566,952,-925,-52,-996,-96,-438,274,-402,-121,192,-206,-635,968,-472,868,-434,66,864,-547,447,-77,876,530,-883,-601,726,-642,-786,-568,67,-994,843,540,-995,42,197,-230,901,-470,-431,-71,96,-434,40,-332,-864,445,-963,-495,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{112,823,-299,-269,-801,621,215,999,185,-46,700,-977,334,831,-604,722,59,-890,-880,516,462,-425,6,780,-343,67,-813,-43,53,787,320,-615,925,604,711,500,653,196,-540,26,796,227,518,3,-171,-602,-142,49,-203,158,34,-869,423,-834,358,-688,257,-847,614,411,-653,770,799,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{1000,-1000,875,1000,-1000,349,56,-793,679,-549,-486,368,-463,1000,-624,-925,-52,-187,-96,715,904,153,-105,1000,-463,-950,613,-472,-716,165,-878,-1000,-1000,923,500,-299,702,450,-601,726,-503,408,-848,67,668,816,540,-619,-1000,197,815,48,827,-612,-71,250,-956,440,949,429,1000,-963,-1000,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMDk2OTEwMDEzMDA4MDU2Mzk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{8,94,400,-551,106,-204,386,380,-273,352,-156,-89,400,-400,-36,-1000,710,-1000,1000,-182,44,576,-514,-1000,-701,720,283,-212,798,-438,1000,-92,204,-764,111,-333,1000,-206,52,-362,1000,220,-414,208,580,616,-29,68,-558,376,145,-1000,84,-602,797,1000,-377,-1000,340,64,-215,358,1000,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Double:MTguOTY0ODg5NzI2ODMwODE1", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{880,-319,132,-556,703,-335,335,47,692,918,-530,-313,779,-992,406,487,-748,315,-941,957,-240,124,-734,-991,376,645,465,-165,-456,-742,743,886,-37,989,713,828,-558,-731,-670,-754,-393,-670,-793,-859,955,-725,337,818,220,809,-818,-382,-760,894,-966,-871,-356,-834,-729,40,487,-105,-332,549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{209,590,-103,-893,-458,-65,-702,-495,664,644,-121,-354,-201,922,-824,-411,77,516,361,529,58,-197,-981,275,-601,660,125,-98,430,-433,252,469,-442,121,-169,337,-530,-854,995,511,-86,-74,-14,-283,-553,-249,-587,448,-961,923,750,110,622,618,-625,-447,-205,-355,-826,796,-254,976,-705,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log10(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Double:MC42OTMxNDcxODA1NTk5NDUz", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{-627,529,279,771,590,-450,660,98,578,-542,45,93,-123,-36,-596,-197,953,904,675,170,885,87,514,630,655,-375,667,819,872,-394,-703,140,694,283,564,-402,142,-575,241,17,81,-64,-781,521,686,395,373,968,423,-203,-526,-32,-756,-563,-449,9,24,-376,442,-713,-915,689,-843,-116}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{-304,-632,-700,516,-173,-598,-338,921,-199,-288,-240,-197,610,-546,-171,-454,-426,755,-930,551,-62,472,56,114,325,554,813,-473,-21,378,-39,-261,573,841,-707,-468,885,-613,614,-105,640,-347,-225,37,436,939,429,300,-192,-644,-747,-855,-772,150,-887,-679,107,-562,958,362,-291,-581,-855,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{-638,-556,-865,-809,306,-486,-231,404,-11,-404,-311,856,-389,242,-267,451,980,-493,930,-223,81,-730,645,286,847,603,-378,338,-642,432,663,-750,-421,42,601,-875,-766,-561,-277,-53,438,58,873,-803,307,-164,96,-929,-70,-680,-141,280,-205,-893,638,-645,50,-310,-995,413,841,554,170,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "log1p(double):double",
            new int[]{-615,-838,-549,383,239,-1000,-455,1000,-117,-584,18,304,1000,-642,-304,-916,-763,-200,-45,846,-512,1000,155,100,443,-8,1000,675,721,1000,1000,-436,825,1000,-1000,-794,-27,-84,994,722,413,-530,-967,-232,641,1000,282,794,-192,-578,-516,119,-183,365,-1000,410,-306,-987,1000,925,366,-532,150,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "main(java.lang.String[]):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{999,283,-376,591,316,-498,-228,936,-27,-756,935,-930,-238,-531,-940,310,760,643,-973,-185,125,-834,-471,-133,-381,161,-85,-473,662,210,16,500,-36,67,187,-307,-487,-276,401,-630,606,-751,56,110,-89,785,364,-209,127,-208,683,-237,721,-834,941,-974,-588,228,450,775,616,-267,803,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{-495,939,419,-208,-645,13,909,463,871,53,-300,-693,356,-27,74,273,-881,510,232,-25,292,-150,481,893,-845,382,-193,618,-461,-515,-360,311,216,701,907,156,827,-38,-38,34,863,623,-91,-75,-492,996,-13,586,712,766,-820,970,780,257,-969,526,976,841,755,36,-838,-45,-828,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Double:NTEuOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(double,double):double",
            new int[]{518,-662,22,-360,-845,-507,-87,434,946,-312,-815,-22,-643,632,589,-819,-914,-487,22,266,-925,764,-630,-601,148,-950,-644,-404,-892,-103,3,103,-500,-127,-448,55,-136,-516,-756,-313,-716,107,-759,-388,-701,-852,136,-230,858,-624,-990,531,860,895,778,-881,-921,-393,720,-942,754,150,484,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{-747,-726,162,-773,-945,170,-347,-424,419,929,-108,42,-286,-850,-170,-939,78,-685,137,351,-523,-546,-456,-751,30,-402,-426,91,936,-735,496,958,89,773,-126,728,320,-645,702,-419,-918,-216,791,-155,-658,462,816,-623,492,-191,-798,571,-870,186,-68,506,739,-468,248,-237,482,476,-780,989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{-288,45,422,-46,-53,-123,402,318,23,-522,-233,-696,-921,50,16,445,227,-107,540,258,978,103,56,191,-943,574,786,738,168,525,-60,519,7,310,584,745,683,476,612,670,-428,-301,211,379,615,-422,920,-394,46,-946,-782,-889,671,654,408,616,901,745,519,280,-998,484,882,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(float,float):float",
            new int[]{-470,421,-540,134,272,391,-276,-253,-492,-753,-196,-927,-917,-546,-752,119,215,899,-274,451,-385,-1,-165,-212,379,-291,721,-816,853,-488,232,959,-571,82,-151,232,93,-534,267,-953,407,359,158,-129,573,-684,397,531,-740,599,820,-448,923,-230,460,-705,520,-901,-894,277,707,394,40,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(int,int):int",
            new int[]{-487,-419,-747,713,-787,-693,-164,-526,-118,-228,576,762,609,119,-935,506,-645,388,-915,-373,-887,664,-754,294,-934,49,-917,615,901,24,-412,-68,426,428,-908,719,996,781,671,713,-106,927,-219,344,-221,-872,708,-178,599,614,-392,-736,929,-656,397,180,-379,272,-445,-708,855,751,-963,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "max(long,long):long",
            new int[]{982,-45,513,49,-965,-970,401,600,933,517,-35,-661,516,-414,718,-797,570,913,-224,-243,-528,910,487,-555,-199,-415,882,-941,783,-691,-135,-247,745,-833,-884,192,21,-731,-488,97,721,-556,551,-668,-397,-940,-250,956,-57,-629,248,-798,-290,752,993,-783,654,464,763,588,-58,169,-960,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{-4,691,340,-420,-974,648,650,989,-631,-327,152,-348,221,959,252,-966,-626,416,-733,598,82,-96,-346,-792,561,831,-936,599,643,-6,-428,-670,-546,-595,-615,355,-915,-21,161,-583,210,-561,631,827,340,-446,-325,-100,-22,929,-804,-728,332,942,-797,-337,441,167,347,261,-436,627,838,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{-454,-303,474,492,659,-33,-941,524,-99,-938,926,381,-177,646,718,-954,-516,705,-234,810,-806,209,-569,-376,589,976,-935,571,-13,274,826,-989,-722,567,581,-296,736,-70,-83,-729,116,145,876,-292,121,-745,448,376,-84,-556,98,865,-243,-494,624,-569,982,-118,510,-643,637,-717,595,468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(double,double):double",
            new int[]{143,-664,609,-81,-159,962,-183,-964,-853,507,-875,29,-484,546,-989,-159,61,64,422,600,387,566,-392,454,637,655,835,434,-21,420,-438,730,-863,-153,-411,-524,654,601,-466,229,542,1,556,670,746,-233,678,762,-601,238,-707,-439,-754,-885,455,220,-202,545,-543,313,-196,-651,-200,-432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{896,931,-983,277,577,32,-905,393,807,-388,774,-529,-159,642,-260,-23,-643,440,-295,173,988,230,-174,-23,579,-474,108,305,385,821,-785,526,72,-316,-591,230,-207,-634,448,335,918,-752,178,806,24,-902,281,-316,-314,-33,-795,987,-758,-208,624,-883,282,-720,964,889,894,-500,-498,983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{865,-132,140,539,799,964,704,689,-623,-430,1000,-32,368,-322,2,-458,804,-437,-203,-33,170,241,-489,-139,-768,735,926,702,-940,-310,56,-33,437,-441,251,-598,-690,527,482,-942,-852,635,-588,937,218,132,-449,566,603,-4,351,625,539,784,449,-105,117,475,-564,-812,747,830,486,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Float:LTMuNQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(float,float):float",
            new int[]{-533,637,-35,454,-975,871,774,-197,23,718,-437,-450,-786,-969,992,439,-622,-671,-276,-445,911,175,959,639,-207,634,-972,-549,-281,-371,-473,-365,-152,650,-158,-875,-938,247,959,988,870,7,405,-212,199,-699,-127,-959,156,-12,-351,-859,-419,485,-721,264,701,-878,112,-821,513,-447,322,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(int,int):int",
            new int[]{-607,46,795,-435,664,917,-916,894,768,-391,363,-275,-337,-455,409,562,461,-971,-294,153,336,748,-802,626,983,-463,403,-518,377,-758,944,986,-500,-177,-669,-188,-269,679,571,-313,693,-438,-906,-795,462,461,-628,-18,268,334,-384,919,90,-332,-23,410,-143,-476,-222,81,-620,-70,503,859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(long,long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "min(long,long):long",
            new int[]{-808,848,-744,-401,885,-929,35,222,352,-899,478,-571,454,555,75,-742,493,-383,-665,-699,284,-891,-805,-113,785,567,-564,27,210,845,518,414,-435,907,607,379,-309,-866,927,951,-950,-251,48,-25,-272,759,-779,-475,440,-792,477,829,-331,448,807,-662,-491,677,-496,639,-747,3,-552,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMDAwMDAwMDAwMDAwMDAwMg==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{975,182,-940,-66,-842,790,927,649,36,-153,531,-593,-97,487,-903,575,-328,-271,527,-688,-858,98,-552,431,798,-970,-509,885,945,138,-471,413,-312,-706,245,907,-706,111,-685,850,809,-56,745,845,-544,754,445,-315,305,-663,17,-332,965,305,-112,949,-327,944,-574,-408,-999,-379,-513,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wMDAwMDAwMDAwMDAwMDAy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{52,-299,111,659,71,-947,-721,88,683,190,105,563,12,738,-564,-183,-419,-146,-609,-436,-950,-798,352,-881,-370,-133,979,718,-217,-406,-208,358,518,-385,-509,38,-348,-26,-878,-927,698,753,441,-200,745,-109,-630,443,610,387,-676,-843,-162,589,369,356,732,-981,656,-707,-547,-300,381,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-1000,0,306,274,-100,-1000,-61,141,-686,-493,-606,0,-744,-263,-853,472,568,-292,134,-140,424,-1000,327,-375,528,0,1000,1000,1000,-586,-512,-722,1000,0,-158,394,874,-1000,-565,-217,1000,-336,47,0,1000,-1000,1000,-1000,933,344,-1000,-439,-1000,-1000,-579,-84,1000,661,-1000,474,698,-810,593,778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNzk3NjkzMTM0ODYyMzE1N0UzMDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-424,141,-10,828,-254,464,304,-826,-32,691,-904,87,141,544,465,697,-353,770,732,662,997,286,261,-227,408,-458,-113,-643,561,-856,106,-49,477,-388,-147,-269,-497,-190,-431,9,696,88,552,-268,-413,-570,-889,377,-759,42,-81,-105,-183,-292,156,-584,410,-30,505,-671,872,747,889,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{481,-671,-339,835,-763,-224,119,-26,503,413,-262,-233,-566,505,-411,281,-593,571,-598,550,752,970,657,-607,461,248,830,-307,-590,796,758,-354,308,-774,709,-508,648,-456,-342,-930,225,678,526,166,500,196,-304,87,977,-824,-21,-883,111,-915,400,642,794,260,-343,543,-608,-871,907,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ3OTk5OTk5OEU5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-490,-656,-49,-791,-915,-30,-297,-361,387,872,520,23,-214,-19,721,830,649,552,-675,-726,279,-276,281,923,101,-866,-564,882,-745,-232,-172,680,444,-917,523,7,-88,-900,-899,-736,778,-348,202,-484,593,-749,46,906,-57,-844,-260,826,-782,124,362,-770,-767,65,293,344,296,369,96,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuOUUtMzI0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{310,516,-721,1000,-525,1000,1000,220,-278,-235,307,-531,-345,1000,-1000,381,-620,506,1000,-98,-730,-693,111,-246,1000,-1000,-481,440,1000,-518,-915,564,-549,-297,487,748,-893,400,-418,1000,724,-345,1000,199,-365,694,-250,75,339,-264,446,-1000,1000,-633,235,133,-254,1000,-561,317,-58,552,299,731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Double:MS43OTc2OTMxMzQ4NjIzMTU3RTMwOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-729,272,-418,-247,-293,-351,83,144,361,-977,-123,-442,-769,-302,-852,550,300,-196,-862,493,367,-740,556,-757,219,-581,958,769,818,-645,-282,-834,899,-388,-192,-535,377,-104,-420,-504,322,-337,938,666,896,-125,876,-998,-380,253,-781,-974,-397,-686,582,134,965,199,-938,11,575,-190,-22,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(double,double):double",
            new int[]{-328,751,-44,450,-540,-488,-732,-635,999,482,660,935,-504,134,941,92,768,-240,-58,-565,-168,801,505,875,349,778,-166,-316,-378,-935,-631,-864,579,-383,-59,962,-841,402,-94,-381,911,783,206,-936,212,385,-152,-888,-483,83,169,770,290,-337,446,-29,-100,-567,876,-960,-390,619,-200,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("java.lang.Float:LTc2LjgwMDAx", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{-768,-782,373,810,953,-856,-658,305,369,-687,840,36,-638,704,-650,-541,697,284,-441,809,779,730,606,-268,-491,-408,-582,52,812,812,425,705,-497,481,-525,504,-543,-538,468,808,820,88,394,-632,-95,528,-199,720,787,603,-544,-715,199,853,-6,-940,-728,474,-323,217,900,-614,27,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzE1RTE4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{344,725,273,742,898,-806,352,-240,350,893,651,-342,-668,-469,68,-715,13,815,430,-78,-491,-478,833,-723,-222,-998,-142,-90,-827,-609,96,982,-114,752,836,812,-105,-890,586,-833,-322,955,-830,415,992,-887,-473,-273,-712,149,86,370,975,427,-418,778,-347,789,-460,-414,-286,459,-704,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("java.lang.Float:MS40RS00NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{143,696,-783,-856,-833,-556,676,-379,-808,782,860,693,661,-838,778,-858,345,130,-441,-524,-729,-281,646,645,-804,250,843,676,852,119,-862,-82,-789,55,743,-841,419,-898,500,-678,-309,178,962,646,-495,-603,-210,-669,685,-404,876,452,-382,462,-414,-375,780,-467,892,394,960,-75,-237,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("java.lang.Float:LTMuNDAyODIzNUUzOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{-487,237,360,395,355,325,665,-758,-734,754,662,-794,-623,-111,554,571,409,745,21,53,-350,-577,350,273,215,-537,-688,57,609,974,-492,542,-329,362,-565,-305,344,-782,-746,240,401,-487,353,-775,237,632,-812,-488,-826,-41,-408,313,312,770,-201,-430,-98,970,716,-550,483,-122,674,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{171,131,214,967,73,471,621,546,-78,-148,545,398,576,-615,178,682,846,95,-228,661,-337,484,883,-185,-94,5,168,-917,-485,672,-439,65,558,946,155,611,-707,-173,881,656,-60,620,-838,368,-114,-732,-882,-260,339,-698,-380,584,70,506,-491,584,829,-824,632,505,-767,529,-997,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("java.lang.Float:LTAuOTk5OTk5OTQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{802,-238,190,251,814,-634,-227,126,351,-652,-774,424,710,468,-743,758,283,-304,270,578,-262,-641,-72,947,-968,-781,-309,-671,643,-277,-572,-224,-529,-405,-263,463,-758,-255,314,961,-98,-21,-153,850,505,-555,300,-677,-604,-105,145,296,114,877,-843,-826,-639,-205,-307,-58,650,-596,59,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuNEUtNDU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{-495,696,446,100,-35,-843,617,-209,688,1000,-1000,-504,-813,-731,71,-40,345,467,-318,132,-757,-821,922,-354,915,250,-746,1000,689,1000,337,-933,791,55,-1000,-841,786,1000,88,37,-778,-505,-1000,-371,1000,455,-210,400,-1000,658,-673,1000,-917,-947,128,-1000,780,-467,788,863,1000,-75,-237,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("java.lang.Float:My40MDI4MjM1RTM4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{293,188,102,-210,155,-1000,-1000,-872,495,-786,-1000,-730,6,232,-1000,-650,-1000,-458,950,-662,-633,-1000,-648,919,-1000,-195,495,24,643,157,375,-182,-1000,-1000,-642,-203,-632,389,532,634,-483,-715,19,271,1000,171,882,559,-1000,1000,-610,-946,-341,700,-782,-879,-1000,-195,-1000,82,175,-636,-62,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextAfter(float,double):float",
            new int[]{-458,-305,833,264,-645,-438,681,554,835,-836,673,331,729,-247,945,409,-739,-864,-737,577,507,-317,-133,-626,158,761,-251,-106,-881,-904,3,-236,401,533,-597,40,-818,437,-915,-827,-443,-491,-649,457,510,-773,-36,365,-64,275,60,-917,-823,361,781,-833,-554,634,-869,-223,-227,552,-864,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDcwMDAwMDAyRTk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-61,987,436,386,730,104,-502,395,77,25,-675,-917,-318,-254,211,917,351,694,-705,-224,596,978,921,330,855,964,-735,-321,806,257,-534,-772,241,490,706,-559,-125,-65,-305,-823,-919,885,-166,646,-945,999,-26,652,193,-650,-880,774,-433,811,-300,-595,292,556,101,-843,-668,-8,-973,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEyNy45OTk5OTk5OTk5OTk5OQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-128,-121,565,-52,-821,526,458,-65,-978,834,-813,-702,-602,621,-797,-249,253,-890,-332,-77,-679,-136,427,266,-760,355,617,641,627,203,899,7,-182,-531,461,-646,839,980,-23,-965,850,600,-498,-4,753,612,-48,-938,-6,-75,229,-619,635,695,-668,419,739,124,-125,-510,802,704,281,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNzk3NjkzMTM0ODYyMzE1N0UzMDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-192,201,-30,-9,225,-871,-441,52,234,156,912,920,469,460,206,262,846,-391,-325,243,-728,213,960,-320,-849,-868,-654,285,-700,-255,-213,-150,-87,-931,-195,957,-906,164,393,-198,111,171,-159,69,33,-417,-941,-540,-321,896,405,-597,933,681,-699,-214,374,264,973,116,-92,726,-442,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{-493,-689,920,533,487,343,602,-885,238,540,-881,-480,627,-88,-569,-831,-27,-316,-147,-138,-222,-514,550,-922,246,888,548,-583,-253,555,193,310,372,601,-805,861,-349,-650,-609,-300,-889,643,191,-938,-141,58,-681,-39,-329,-6,-132,-703,-855,408,-989,-11,416,210,-296,-155,655,-220,681,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(double):double",
            new int[]{106,-100,-323,998,775,515,361,-940,-948,151,759,-971,-479,-426,107,-9,11,-661,-1,155,-465,222,467,-724,-812,232,197,36,-698,-103,473,-580,190,789,634,849,681,928,852,-138,288,583,-168,468,-642,-734,708,-57,828,-739,-174,808,-688,894,378,-1,165,-56,-75,846,912,74,-284,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4wMDAwMDAx", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{175,361,-70,-73,-208,902,-741,-173,467,-843,-476,497,-40,-185,589,-253,518,-190,760,-104,-414,-330,-696,742,-330,-298,272,-372,525,-454,564,-35,558,35,-559,-262,-460,727,-586,741,-405,-963,-152,-388,-238,-575,462,-911,-606,-223,443,319,-859,811,-139,564,-649,-908,-270,-830,327,-902,-839,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("java.lang.Float:LTkuMjIzMzcxNUUxOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{-905,642,249,-510,959,-962,-925,453,-81,422,516,144,-176,-885,-1,-548,-188,644,-657,-805,-575,275,112,-210,-329,875,-54,408,972,387,569,689,624,-402,420,314,-367,-665,-401,919,-377,731,189,-817,870,806,929,-754,488,235,-342,-946,-754,866,7,711,607,-641,403,-532,183,102,-400,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("java.lang.Float:MS40RS00NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("java.lang.Float:LTMuNDAyODIzNUUzOA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{-1000,921,805,-770,82,-332,-183,-1000,827,-271,-79,1000,-283,830,135,850,913,-634,316,-1000,-172,-825,612,75,-96,20,-129,82,-622,-747,158,-55,-288,188,1000,433,-1000,513,184,303,-302,42,-1000,-1000,-762,1000,-914,-1000,-116,-600,159,-261,-958,-233,-426,-23,-402,-591,-883,-500,644,-996,-373,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{-385,-389,777,983,745,391,767,-364,760,-456,779,-943,45,-264,836,-284,783,442,-121,-714,-633,292,-379,-806,208,312,-738,488,-532,-310,840,998,-57,-921,260,886,550,-67,788,-64,-293,-146,176,-736,-736,-466,-532,421,588,507,-991,368,886,637,-767,-790,-46,31,384,-806,-878,-111,474,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "nextUp(float):float",
            new int[]{-757,-232,86,-65,-966,-338,-69,-363,-233,184,656,723,-464,653,709,489,478,-376,348,-844,-65,532,937,-232,-623,541,-691,981,-931,443,-54,390,167,-142,-17,232,-819,419,799,549,562,160,-685,-840,-476,907,29,-805,-814,-898,-718,-659,-411,-795,293,-562,-767,-424,-922,618,387,-408,-454,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,782,-347,-61,-479,-464,-457,-31,191,-742,200,976,-280,440,1000,-262,-782,-902,-866,1000,330,-167,-386,785,471,-653,435,118,-111,-1000,-41,535,-739,701,-283,-447,284,494,574,-443,78,-730,143,1000,-119,-146,269,-154,911,-625,677,397,572,414,-612,649,322,634,-126,-1000,-1000,180,87,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuNjU2NjEyODczMDc3MzkyNkUtMTA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-539,1000,-1000,638,-336,-1000,525,1000,462,-1000,1000,307,84,-381,287,-259,-971,-172,-1000,-328,-554,686,92,386,608,-601,-1000,939,-432,-898,859,-382,158,-656,1000,126,-54,-1000,438,-226,-410,157,29,8,-600,-763,1000,-522,126,-1000,51,-784,289,-105,-307,1000,-690,863,-283,-442,-271,707,137,-923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-1000,1000,-833,870,-862,-299,-234,165,77,-11,-319,1000,-157,1000,266,425,761,-1000,854,-468,-867,-1000,181,-317,412,-271,-694,405,929,-617,-50,414,-1000,1000,-764,-1000,-378,-1000,-370,34,-317,248,-650,316,1000,254,-60,-516,264,1000,-554,-768,-722,802,-792,-494,-935,-829,633,-813,826,-4,1000,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,-380,1000,-685,1000,1000,767,-871,320,435,-1000,1000,1000,-1000,-825,-1000,-919,1000,258,-1000,-20,230,-1000,-1000,-1000,1000,-701,-1000,1000,1000,1000,-377,1000,-1000,-416,1000,-1000,917,-1000,-496,-1000,1000,1000,-1000,1000,1000,-1000,1000,1000,1000,1000,-586,-1000,801,1000,66,-1000,1000,-254,1000,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-594,0,674,-760,379,625,550,973,-409,390,-364,-165,-6,725,-416,-915,-913,-401,-123,-201,162,-101,-124,438,668,-19,690,605,-818,-491,254,-408,-651,-53,-707,300,-641,-854,412,-200,982,-654,-507,-652,-977,-376,-250,-952,173,-694,345,-503,-367,215,-699,719,685,734,595,-473,385,-385,-229,-1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{710,742,-249,-701,-496,-551,569,-130,-672,-162,541,921,601,436,769,-108,-985,88,-366,605,-76,-386,-216,984,887,-59,133,-344,674,-849,82,390,-89,-897,-558,-773,731,401,129,-89,281,332,605,737,-243,119,-768,320,735,-681,276,933,20,555,75,40,975,860,-907,-537,-669,898,8,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{677,-999,1000,-746,736,1000,-21,146,191,1000,200,1000,241,440,-796,-262,-414,914,1000,-162,24,-1000,-69,785,-299,-306,1000,-387,2,-99,-1000,-24,-930,-423,-949,-447,-854,1000,-1000,633,-204,-730,70,-428,1000,-126,269,-667,1000,1000,1000,957,-1000,748,-30,-339,942,509,1000,-1000,1000,-354,87,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-610,285,113,1000,-1000,-1000,-757,-488,-1000,702,1000,570,199,1000,-277,516,-256,126,-370,1000,-97,578,-59,-93,487,-1000,257,-59,-372,-1000,1000,-825,628,1000,-223,-1000,524,-1000,971,-665,763,754,134,359,395,80,438,372,26,-181,-680,-448,67,194,-537,436,-141,545,-152,-980,281,67,665,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,-430,1000,-859,-125,1000,633,-221,986,183,-7,-115,906,-702,-337,-1000,-1000,1000,-733,272,507,-713,-1000,229,-324,-252,1000,-520,269,953,292,-1000,1000,-544,-9,230,-894,1000,-1000,-96,-748,-247,1000,751,41,270,-1000,159,833,255,1000,774,-1000,-254,206,656,1000,1000,-178,98,801,1000,-1000,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-1000,369,-57,-191,-442,-206,906,301,-338,864,327,684,-559,488,-481,-74,-756,388,-693,36,-557,-1000,218,370,421,223,-130,425,-136,443,985,-184,-366,771,193,-375,-1000,-1000,134,-188,-117,-100,89,623,-413,400,-107,-410,79,-244,-148,-846,-400,237,-213,-1000,-1000,651,186,-219,272,-440,1000,-8}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,-262,-701,-734,-227,-1000,210,-1000,-720,-128,991,223,-294,123,1000,922,-762,230,0,1000,771,-595,-543,99,0,814,1000,-791,443,-143,395,607,944,-1000,527,795,402,787,478,-644,317,802,1000,0,-259,1000,-1000,1000,-547,-193,912,1000,0,339,671,-598,877,1000,-347,-356,-1000,-383,836,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{1000,561,-667,-217,-245,-89,-382,-255,191,-1000,-276,1000,651,150,-79,-858,-496,-696,-1000,850,-215,251,90,1000,424,-1000,-863,-144,697,-1000,796,232,-739,763,-118,-170,284,-150,-648,-225,8,-735,605,422,284,-448,243,-162,808,-1000,643,-497,572,299,-1000,1000,-412,-24,-506,-932,-570,906,-443,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-848,-289,247,-615,188,-538,-631,-86,-946,898,256,-473,-633,-606,-690,266,132,-603,871,-918,-548,-147,336,-138,214,874,-488,-512,832,847,140,-374,966,-606,-155,-134,325,-305,-690,545,170,-441,401,-849,-72,328,190,540,-566,211,-692,366,788,-37,948,-887,514,-46,950,901,795,641,-116,842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{-957,782,-585,177,-741,-464,-457,698,-194,351,-448,664,-280,440,778,962,-567,-902,101,-690,-593,-816,57,27,-946,75,719,-359,-763,219,-974,525,-494,701,506,-365,381,282,-60,-837,-479,-730,-608,459,409,285,269,-69,-709,468,585,-209,572,28,-561,-494,575,143,-126,-415,655,-807,758,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{869,66,764,-712,-960,53,623,584,576,-171,633,811,468,-775,-355,-182,278,268,-955,-952,-277,-714,-302,-995,672,385,-740,-282,-712,404,959,-708,-109,660,243,585,-687,-91,-734,-256,-986,-194,-908,908,13,866,-301,703,837,-587,408,359,-904,352,181,222,-944,713,389,-750,625,883,136,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,double):double",
            new int[]{77,369,400,271,-900,367,1000,-320,129,474,-410,532,1000,739,-117,-1000,-644,19,546,-454,-547,-1000,-198,-11,261,1000,-6,316,1000,54,-741,-431,453,353,-1000,-1000,165,-38,-873,272,-177,-280,768,1000,383,1000,-400,-385,25,33,813,-581,-346,-121,210,-318,-1000,160,-166,-203,1000,463,-783,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,int):double",
            new int[]{-906,-947,-527,-922,-171,895,-695,-330,-340,-408,-890,217,-68,-110,-668,-628,-284,289,198,-548,-296,-917,861,319,501,-887,-702,732,-738,30,410,-367,319,49,123,-35,951,719,461,-951,797,77,532,284,491,-317,-836,-992,122,-488,529,702,795,-545,517,422,-767,-741,835,332,-342,138,-131,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "pow(double,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("java.lang.Double:LTM4LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-385,-326,164,-628,-215,271,182,-85,-688,832,376,23,-1000,-231,661,-199,-578,-262,-505,-74,-30,-867,-614,381,-749,500,-877,1000,-54,-1000,-422,734,149,1000,-1000,516,-294,-794,783,744,815,44,-428,526,-117,-137,-486,242,491,-200,-663,-14,403,1000,-493,-24,528,1000,131,-619,155,-647,-149,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{606,981,233,788,826,439,51,-820,856,225,619,609,-268,-600,933,292,-351,808,-934,807,753,551,-255,262,-450,822,34,-387,-381,-671,298,-660,44,416,-810,659,25,-354,377,-670,-573,-918,928,695,717,801,888,-936,-333,-720,374,817,505,-791,769,668,-238,-2,47,401,-20,-182,-931,-884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("java.lang.Double:LTk0LjA=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-943,-122,164,-628,-215,-405,625,-344,-688,832,769,734,-548,-253,549,-959,-912,-288,-641,194,550,-867,127,327,-126,921,-906,692,-739,-515,81,336,786,980,-381,712,-594,-826,257,839,705,837,-362,329,394,769,445,-10,621,-236,-782,12,350,665,218,-198,-269,429,2,-599,-803,-362,-814,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-4,214,1000,-836,-365,-379,965,-443,-1000,1000,737,765,-1000,361,768,438,-1000,-1000,-1000,1000,-644,-1000,59,943,-1000,872,-991,1000,1000,-1000,589,1000,-196,1000,-1000,1000,-358,-1000,-916,1000,1000,216,111,-69,900,42,-768,463,1000,-632,-767,126,127,1000,-1000,-953,-1000,1000,-568,-1000,1000,-1000,-1000,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{189,-572,773,411,-816,-759,-513,-704,-954,727,-712,-599,128,944,-484,559,758,-407,576,-943,-302,-598,250,-111,-50,-442,532,49,967,820,537,433,-924,766,604,932,919,331,-327,115,78,245,259,-464,678,269,879,-845,-246,-736,694,605,403,2,-721,-868,381,-626,-60,904,-353,205,-193,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{-360,812,-581,-843,811,216,-885,-411,-388,845,-829,-361,-962,784,3,-12,392,-154,881,5,201,142,600,-304,6,726,764,-147,-601,-522,964,-977,376,209,-210,-122,-894,667,-474,-552,-453,-400,-388,-738,6,300,557,921,688,182,-205,-123,-448,854,208,-734,-589,864,515,520,-948,-150,525,188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "rint(double):double",
            new int[]{759,-713,831,-691,858,-106,-572,734,99,-899,639,-702,-250,562,950,784,590,-279,255,-562,297,352,142,-446,-109,-145,-857,-11,704,-709,832,549,30,14,292,205,661,-585,-598,-97,824,-242,-370,-604,711,400,583,-62,-247,785,-569,977,-970,-499,508,-980,-146,-552,-126,755,420,674,446,200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{944,-22,-162,529,-931,-998,879,568,-363,362,34,-439,172,349,-185,-992,-961,116,-862,795,-286,25,-826,807,206,822,109,437,-68,352,244,691,879,369,225,-495,568,533,-368,425,-981,-336,622,-177,683,-49,-115,492,-843,-31,-217,-381,-647,-940,196,903,803,937,368,-94,735,204,817,578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{475,18,572,729,-413,-785,-125,394,466,1000,947,-513,769,575,640,-1000,-1000,66,-596,4,36,-271,-902,975,-390,110,-254,150,266,299,-375,-466,1000,-278,-50,-324,1000,141,-1000,209,-107,540,351,-70,1000,67,165,404,-1000,-315,-1000,-881,-1000,-214,97,424,725,401,-349,-127,332,207,928,89}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{-761,-907,-767,956,-332,-730,406,392,513,338,-733,365,664,496,54,-917,-543,-168,489,-802,621,488,-692,997,-656,-466,355,-171,-335,-321,625,-95,-3,841,995,538,-800,72,776,380,363,376,756,309,193,542,-95,207,758,274,559,-909,-793,-372,-453,-202,63,-957,480,729,-312,103,-776,62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(double):long",
            new int[]{584,-209,608,204,351,326,730,520,882,302,-954,-460,16,392,-533,984,-983,-649,-518,433,-566,-264,-551,-668,-631,-459,-830,-930,-294,670,-558,110,-618,-901,-374,-971,-523,22,697,-141,548,344,741,-836,452,437,394,939,699,-719,-658,-420,278,-218,-332,958,864,392,-417,899,313,964,-157,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-21,-622,432,678,259,-485,490,-848,-313,240,432,-61,-38,-373,649,-60,53,977,-926,676,-239,-527,-194,141,215,590,-655,-337,112,632,-72,982,599,-235,-666,-302,-73,422,-444,-152,-715,-915,743,471,-489,-930,-24,733,831,188,560,-595,159,-69,809,-665,-919,177,-458,-84,171,-693,921,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{-214,-114,-738,-366,-836,-31,-953,-296,510,-607,634,95,-559,-411,627,-626,104,940,434,95,-611,173,946,-272,-482,-995,-620,611,-233,-835,-782,323,-808,-154,-902,-131,-523,-779,515,653,251,-296,-796,307,565,-662,-763,175,-376,884,-5,-540,-688,-28,438,902,558,43,387,190,-808,688,900,273}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{273,-284,-241,-330,184,54,-198,-75,-436,-798,-320,141,556,-233,-866,927,-640,971,-509,698,-168,700,-773,539,85,-355,-160,-289,406,699,-450,250,-62,-250,-775,-880,794,-451,963,-61,-488,411,777,-559,-879,174,946,495,456,-929,788,-200,829,935,821,133,-328,518,597,459,362,-397,845,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{828,-319,872,-184,-933,-92,-308,356,-595,-598,25,575,-551,-212,65,-269,871,-817,-940,401,-471,-403,-121,553,474,913,361,-922,841,804,662,448,766,921,668,130,-332,-810,-395,-26,511,194,979,-81,-10,242,89,-969,-20,961,566,-836,-977,-976,587,339,179,-14,731,304,-884,-911,-188,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "round(float):int",
            new int[]{738,475,380,-823,-464,-706,-64,-190,-908,-100,-580,-310,911,-514,-466,160,-834,206,-824,915,147,-292,962,933,-961,-421,-784,-176,-762,39,-676,-294,-439,-838,-712,725,922,-539,-711,-100,293,-234,-878,-902,128,-39,488,-359,-216,397,-727,282,-36,560,219,129,-298,921,654,446,-6,-180,-666,-790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-102,178,374,752,317,-256,-729,-996,888,81,382,-363,106,-748,482,346,-538,-895,-251,-801,412,-905,832,-50,908,854,-750,-238,-938,206,-758,124,-233,-678,59,-412,49,-218,-934,-229,296,-516,32,33,-441,509,-516,-802,-333,-656,-892,738,-980,-811,-136,975,616,453,-354,-8,138,24,-836,215}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-590,-610,754,405,966,-780,-709,-311,-931,151,-364,821,-425,241,-28,-741,-919,608,399,-935,-419,864,721,-496,-179,-188,81,188,195,-86,891,421,366,20,347,125,653,969,230,666,-319,780,-346,-254,-222,384,-24,-420,177,-906,158,-426,204,-647,538,-123,330,686,-807,300,195,-752,-153,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{960,888,1000,-1000,-450,-899,-1000,-917,844,-787,1000,1000,1000,1000,1000,-1000,-413,-912,-796,697,-1000,-298,281,-1000,412,1000,793,-826,-305,965,-638,511,1000,1000,454,737,-1000,-1000,92,-611,-563,839,-1000,1000,306,-1000,-723,-707,-401,107,1000,1000,868,-921,-1000,284,-793,819,1000,-142,140,284,846,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{706,771,-523,-525,-985,-676,610,-744,531,731,-194,475,65,680,-367,119,-306,-523,-500,-890,-541,843,74,-600,-97,978,125,-437,-821,-744,147,-626,-205,-61,-113,-320,-111,375,386,703,-568,-712,-951,-139,692,976,852,-823,918,-505,-171,369,-720,-759,692,875,-176,735,628,183,-562,872,108,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{521,-301,644,556,851,-35,-1000,-200,-980,-641,-1000,-1000,582,-580,-390,-556,563,768,20,1000,-804,-155,309,925,101,972,862,-415,-39,-364,803,-189,92,329,1000,-248,-58,648,988,441,-380,626,549,823,-36,917,-1000,32,-954,-43,820,816,-582,50,1000,107,1000,-430,656,216,-406,970,76,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{-680,-844,-706,-537,-599,-405,603,-702,-8,-455,-100,346,-563,182,932,593,-212,810,-175,-813,-696,-986,-736,-921,-861,582,-819,-231,161,512,-997,104,-814,-530,339,-94,741,-872,-602,-961,973,958,-601,-528,261,275,779,-130,693,561,-921,-713,-317,471,-931,661,-312,-727,872,-16,251,-38,-345,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(double,int):double",
            new int[]{526,7,11,-261,903,-271,-962,96,-876,615,-492,-474,935,14,444,385,-650,978,838,740,347,-379,137,503,22,748,-638,772,344,-241,79,5,-49,-110,305,-498,-665,898,822,493,170,465,349,64,568,783,-438,-852,-407,66,35,851,-510,42,846,-516,803,-94,797,-128,-243,632,6,716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{320,448,215,-313,1000,-1000,1000,772,-1000,1000,388,-728,491,1000,702,-1000,-948,96,-1000,1000,1000,-279,-877,908,1000,8,363,-348,-1000,-330,1000,624,-1000,-158,451,1000,-378,711,1000,922,111,503,256,137,-705,-1000,708,608,1000,1000,-1000,2,1000,892,619,-1000,809,-1000,174,751,498,781,1000,-518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("java.lang.Float:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{303,234,-276,-313,1000,-606,927,772,-403,1000,1000,251,-1000,316,1000,-1000,142,239,-499,1000,940,-182,-247,927,1000,154,-786,-93,-641,-506,1000,-848,-342,-215,737,1000,664,1000,633,292,873,553,-1000,232,454,-604,537,285,1000,1000,-93,-538,1000,487,1000,-1000,748,-186,1000,-257,-438,1000,1000,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("java.lang.Float:Ni4wMTg1MzFFLTM2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{466,665,-180,-313,-234,-648,1000,429,-74,-400,-1000,-595,1000,378,-1000,-1000,-116,-466,-1000,476,537,-128,-1000,1000,929,520,828,600,-358,91,722,725,-1000,-290,-1000,1000,1000,92,474,-368,-479,1000,494,-656,-1000,-77,37,365,308,1000,-48,-112,844,408,-923,-776,-1000,-1000,81,751,1000,550,944,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-402,-145,619,-657,-706,207,299,-148,-694,-407,-99,-152,286,769,-354,892,-473,504,-148,-1000,419,940,249,856,-766,-1000,655,-207,623,1000,-413,-1000,1000,553,722,817,-1000,-895,-123,1000,-1000,-896,879,464,-849,666,-824,-202,-1000,-769,-764,-1000,-641,1000,-1000,321,-718,-782,70,-264,581,-341,89,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-234,-959,328,-411,95,353,-13,475,317,-621,535,-955,-205,-513,-105,200,225,748,284,-997,442,540,-989,-858,-927,26,694,152,-616,622,232,667,-13,910,203,-432,-994,-235,333,-481,-243,-60,153,97,-961,-198,-944,487,723,-534,561,546,-512,-790,-196,-67,266,695,-976,-61,260,-728,-885,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-751,-480,772,548,111,442,-876,920,-660,34,-480,282,-208,-440,937,-653,28,897,-697,-789,13,-329,-840,410,-671,-576,-47,48,-469,-552,32,-735,606,272,836,953,403,-883,678,-750,-621,-929,30,36,-973,227,310,-549,974,562,-259,-756,855,947,-895,-292,833,384,921,859,878,243,-83,562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{466,862,254,-313,-1000,-1000,769,827,-1000,1000,-312,-377,527,-210,-552,-1000,-948,-213,-460,-400,657,-279,-39,316,131,-855,807,1000,317,-199,422,305,-19,-95,-570,519,-65,574,1000,-287,-1000,7,494,7,170,-47,1000,-727,-50,1000,-1000,1000,996,1000,-1000,-319,-325,-1000,-553,751,1000,1000,1000,-845}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{466,39,-276,-973,-1000,-136,810,922,1000,-640,-400,-377,50,-46,-1000,-1000,1000,592,-1000,-400,560,-279,-1000,1000,226,-96,941,-460,-227,-319,-594,-1000,-249,-959,796,1000,1000,574,802,-1000,1000,7,-1000,136,-304,810,-266,176,972,1000,1000,-1000,996,65,-1000,66,1000,-186,782,-1000,-1000,1000,-49,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-995,317,752,-513,947,163,-742,1000,-320,-102,1000,-136,1000,991,1000,-234,319,-65,-476,1000,788,-6,-1000,-367,1000,1000,-1000,-217,-616,-436,117,148,-1000,-463,1000,1000,-428,659,1000,-497,-550,364,-1000,-968,-22,-1000,-345,12,1000,1000,-111,-773,1000,-290,379,-1000,283,1000,679,661,507,1000,-907,567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("java.lang.Float:LTAuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-847,-222,-377,-421,-726,87,-991,131,850,-453,-163,804,687,421,-23,13,202,-783,-726,-691,15,668,-796,316,-563,125,-822,-968,310,-651,-573,-905,943,-34,-266,-771,851,-765,233,-290,444,189,-977,393,-150,692,-644,624,-348,-479,842,-822,-509,167,260,262,187,759,342,-427,-672,-801,-802,434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-885,464,935,80,660,-177,-550,344,913,-316,405,-828,912,771,921,-324,-834,343,679,-233,-385,-664,-188,-220,-150,428,188,-3,57,291,-453,451,487,-392,285,6,-840,-262,658,325,11,-583,-353,-938,662,280,-184,-618,668,629,-892,-285,-818,390,710,585,-247,969,-114,278,-780,-635,-27,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "scalb(float,int):float",
            new int[]{-1000,-305,-1000,-1000,-1000,308,142,1000,1000,-1000,-218,308,474,-43,-822,1000,1000,-128,408,-1000,-320,1000,-810,-1000,-1000,-921,701,-229,750,-429,-1000,-767,1000,265,-1000,-1000,188,-841,-164,-696,169,-187,-1000,434,-181,1000,-481,385,-609,-1000,1000,836,142,-1000,-669,1000,77,1000,-1000,-1000,-1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(double):double",
            new int[]{542,-611,-762,31,677,-395,341,462,-697,847,-419,-554,-778,-716,584,542,114,416,575,419,-667,882,-989,453,577,-119,-388,178,-110,-527,229,658,-615,731,-875,-995,914,-200,218,746,483,266,-446,490,-366,-51,892,-540,257,-811,-368,840,320,41,-460,606,-145,-624,-282,531,-762,-144,104,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(double):double",
            new int[]{-770,957,397,95,571,-200,-212,253,26,399,-914,320,-341,133,962,-395,894,-268,-143,-325,415,-128,843,-251,-657,-395,218,218,268,-724,-72,276,728,-689,-929,-192,471,340,657,119,-519,-531,574,882,147,200,282,-232,177,-491,-868,610,911,866,416,-318,543,637,-748,-305,-586,-794,245,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(float):float",
            new int[]{965,-241,-183,967,-336,95,386,-889,386,-646,-441,-48,639,556,288,-83,978,-44,889,-631,587,-381,-871,-346,-64,-542,-759,-829,-654,825,106,728,28,223,-313,403,688,-475,-461,291,173,198,-303,73,759,-535,574,-831,-292,81,650,388,457,449,668,-535,-537,195,800,618,98,-34,856,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "signum(float):float",
            new int[]{-211,561,-467,-137,217,684,905,153,509,503,447,846,89,-87,992,-200,84,955,214,-293,-204,-297,449,58,269,-710,-621,-43,104,953,-880,-138,395,503,130,268,201,-605,-953,-457,-675,727,100,-346,-665,898,19,-636,736,-574,543,-370,11,462,547,-622,823,-80,-410,-355,-887,-185,224,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45NzEzMTAxNzU3OTI5Mzky", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-469,-8,-323,552,-754,34,-794,507,-469,-698,5,427,217,-97,-736,881,-792,109,180,-473,-363,-569,-121,483,324,653,569,449,272,-849,895,-513,988,-221,-54,-943,810,-634,117,-746,-929,431,-858,-529,948,-886,-8,-281,141,-480,70,-302,146,-323,124,-598,-174,-864,684,476,-791,869,-572,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNzI0OTE2NTU1MTQ0NTU2NA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{225,207,533,-526,-787,84,998,-904,-715,-607,-888,-590,747,-718,746,-961,13,-868,-582,-188,-358,-223,927,-337,479,177,566,546,216,-23,440,398,-282,263,-95,757,857,641,-101,-910,-751,296,44,-923,295,101,-240,-828,-748,30,-593,-404,-353,-857,423,580,-707,223,829,185,-572,-199,86,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk5MzAzNzY2NzM0NDIy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{515,749,-627,-233,628,-302,509,209,510,452,-5,877,337,-141,-820,-903,-290,-721,867,659,-19,890,259,-681,953,-72,-917,-469,716,479,420,139,-547,-602,198,369,-595,-350,112,-111,-125,993,-211,-101,148,266,-555,294,489,-242,-22,780,37,149,-528,-533,312,325,-572,989,301,19,671,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuODcxMTQwMDAwMTY5MTc2NA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-335,214,-238,21,-133,629,127,-276,655,-810,567,-55,624,159,736,-738,-366,-579,119,-702,-664,989,277,719,-664,-49,399,-448,965,164,-138,-740,652,-49,-57,-409,306,-205,-370,-352,744,625,-451,-151,249,-350,-562,-506,-391,914,290,-497,-373,-273,344,-443,-984,-8,404,-651,-160,-967,655,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuODQxNDcwOTg0ODA3ODk2NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-939,734,320,-570,-215,139,-151,105,-322,61,-738,506,427,-267,549,-529,-572,-889,-779,-401,157,-852,-999,662,747,999,-727,-659,776,-261,-469,502,-713,126,506,-196,-515,-296,-548,881,-820,89,-869,534,-922,717,463,438,-676,461,-504,-288,296,-398,400,-645,606,-585,-489,-950,-491,-721,-893,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{44,117,1000,863,-22,190,-1000,946,-1000,643,-271,-413,131,605,549,638,38,620,-154,777,150,-797,87,-653,151,-119,-164,-608,-720,-279,713,1000,-222,1000,450,165,659,56,1000,-336,7,677,-41,-754,914,-123,-212,1000,182,37,30,80,1000,337,-166,-812,713,-45,33,-4,-1000,1000,-1000,657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sin(double):double",
            new int[]{-973,583,897,469,353,809,203,-917,886,-788,-992,-618,-66,438,916,274,932,535,663,-880,484,468,984,29,308,586,908,-415,-759,-972,-651,-886,508,13,355,199,812,698,-875,580,520,-121,-989,790,158,188,-884,-61,-191,-362,-194,-105,546,367,-510,769,-965,277,357,-873,-360,877,-605,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("java.lang.Double:LTg5OTM1LjkzMTEyNDA5NTc2", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-121,778,655,-244,-571,387,-658,876,454,235,801,895,885,66,303,410,-493,88,-112,271,980,76,993,422,317,-76,932,-613,611,376,916,-528,-535,-212,966,-916,-184,949,369,67,-403,-781,-909,451,55,-462,-23,-409,434,-770,-514,-663,-794,-48,220,-466,-921,-399,-435,988,581,-423,-603,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-801,280,-322,337,184,849,-308,-216,-619,327,850,-738,181,276,-951,394,561,227,-554,-788,-985,879,-514,164,-142,-636,587,-704,-537,-947,-91,-637,640,-477,-657,62,573,435,69,-976,537,359,-97,-294,943,-174,964,923,-815,-694,-801,-605,206,-822,738,-630,780,299,-816,207,40,275,507,-453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("java.lang.Double:MS44MTg5ODU0NzM4MDQ0MDI0RTQy", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{980,778,-930,-244,1000,-1000,-658,-1000,-176,1000,-440,-784,-20,497,-1000,-948,760,356,207,-152,-99,-1000,-1000,-473,-1000,907,-664,-1000,184,-747,916,111,1000,-96,-1000,-526,-320,-1000,-583,725,535,-200,682,191,1000,-95,1000,1000,1000,454,663,-321,-22,1000,-408,-878,697,-268,-276,-1000,836,-867,-1000,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4xNzUyMDExOTM2NDM4MDE0", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-100,-935,308,970,-858,511,311,-110,-524,-888,254,-989,-406,-240,-117,842,87,-92,538,978,322,-356,207,865,554,-773,-127,802,-846,678,441,-453,-485,99,206,309,390,600,-922,342,-763,-55,-400,896,456,293,162,-148,-665,-168,763,991,-30,-537,174,-400,-503,229,-590,-179,-490,700,51,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sinh(double):double",
            new int[]{-25,583,-1000,-94,1000,782,600,154,1000,-257,-50,-198,221,-1000,154,420,-51,1000,774,-360,665,198,-50,-581,220,-576,-407,264,-850,-422,401,410,-243,511,-647,185,464,465,-305,193,51,1000,-1000,-88,-794,1000,-400,1000,425,-192,1000,-758,-137,-7,-1000,-1000,905,-506,-985,-580,1000,-650,403,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "sqrt(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wNTIzNzc5NjM3MzUxMzM4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-440,975,-141,-229,-782,-102,-785,-65,30,501,-780,296,721,348,-733,-103,720,93,-633,-35,314,-745,-136,-844,256,-718,-605,-838,32,-30,-892,243,-659,-824,-833,-971,461,-118,322,976,116,-854,961,-761,246,265,454,393,-389,526,836,-553,597,360,927,-113,-260,-599,-190,-902,527,991,299,-527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("java.lang.Double:LTg0LjczOTMxMjk2ODc1NTY3", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-225,-894,674,-651,-4,-437,-527,438,-949,-162,-191,-240,591,-719,-955,23,666,618,389,44,-305,873,-897,727,481,341,-220,-869,-558,-855,-348,660,-463,733,874,147,484,-180,-477,647,860,754,644,145,-630,-950,-887,-945,-786,-81,-718,-624,-202,-952,453,-309,287,557,37,799,-522,-876,-37,708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4yODEzOTcwNzI5NzUwMzg4", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-383,179,-399,-154,-239,714,423,176,-794,-442,747,-857,-51,157,261,167,999,-684,-629,828,28,-503,-291,-74,976,-415,131,-822,94,160,975,596,-851,175,294,372,704,-927,-417,-898,313,-153,-876,590,230,325,483,-861,806,48,-75,228,-852,498,-446,-209,-232,-834,836,911,-986,967,-160,-287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("java.lang.Double:NC4wODQyODk0NTUyOTg1OTM=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-76,1000,-1000,462,194,0,-478,365,1000,344,229,-239,-374,678,-249,-11,-1000,-28,359,483,-407,-1000,-29,0,-288,98,0,1000,511,-97,346,-15,855,-1000,-487,-392,664,-704,301,0,-299,212,-117,-1000,292,1000,1000,1000,744,-1000,128,-688,264,-56,1000,-779,718,-41,0,-193,0,1000,323,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuNTU3NDA3NzI0NjU0OTAyMw==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-921,62,-339,-707,-733,425,-359,211,-232,-826,-909,754,-50,950,-383,766,-692,-628,-937,180,682,-408,-922,-898,657,653,-528,85,995,-641,16,-216,263,-811,324,-34,-40,-23,-776,-122,388,489,665,-632,278,849,-409,809,757,844,456,-105,612,-500,-901,678,19,-896,-472,812,-86,805,875,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{-356,-231,606,-258,-938,192,296,725,-903,-833,-593,604,-554,-959,145,-505,295,514,-864,-238,566,30,763,963,-942,805,-920,-985,-271,868,859,965,696,302,-684,446,658,-951,-161,27,402,94,-343,968,801,610,792,607,293,-246,-120,614,218,190,-523,-402,-430,-874,-678,-757,-241,-309,691,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tan(double):double",
            new int[]{771,799,470,816,764,-685,-154,313,-402,284,-373,917,-986,-727,127,111,302,-548,261,-326,152,955,61,-18,-924,-740,-855,750,316,854,873,968,470,311,-122,-430,190,-982,-545,400,273,287,902,434,-787,345,-681,865,853,-213,-400,699,-827,-521,382,-239,-568,614,599,684,905,626,921,-658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNzYxNTk0MTU1OTU1NzY0OQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-355,218,561,495,-309,-960,292,-204,0,270,790,-688,194,-755,386,115,755,-51,958,-998,515,342,512,-709,-803,-105,542,-392,488,680,-390,534,-491,-3,-831,63,-190,538,28,253,-974,-600,59,-883,-805,-144,-236,-124,-571,-252,124,-2,-939,-403,799,-886,719,84,205,-142,877,420,227,964}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("java.lang.Double:MC43NjE1OTQxNTU5NTU3NjQ5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-422,601,526,678,-416,719,380,411,683,283,-223,-725,-271,625,619,424,-764,-93,-359,-51,875,-436,-478,66,166,-470,475,-714,-316,-828,-911,526,-492,538,786,234,-150,-516,-350,-46,-121,606,559,531,563,-533,-414,124,70,585,-568,562,-306,-133,-679,239,609,-266,-810,-327,823,59,317,153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-453,-531,-895,-278,999,923,-147,535,426,932,-502,936,-666,737,866,746,600,268,-285,576,424,-742,100,236,125,245,381,-394,947,-64,29,58,-583,141,-66,-451,81,-550,-714,-38,438,157,343,922,-235,-474,366,411,-804,450,-151,775,887,-217,-208,-586,-608,-805,227,392,486,-752,-544,471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{-606,512,-780,-98,-564,-143,-566,678,952,-610,-482,-558,-986,137,867,-726,-313,-616,-681,-536,855,516,-217,20,-675,-575,-249,-323,-21,-384,470,-922,-829,850,-910,-82,-210,103,-473,212,-553,982,-77,-428,113,317,652,418,-821,595,605,-647,401,164,-869,-379,493,-351,473,492,-795,936,769,731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "tanh(double):double",
            new int[]{371,67,-817,-258,-43,-680,-179,-256,-447,-84,-159,-886,-175,958,800,-676,177,493,869,-117,-386,907,132,783,601,-241,-765,-585,904,157,856,-889,-166,464,-947,557,-474,96,918,-290,943,521,174,579,131,135,-647,255,940,676,16,900,-130,-964,-882,-936,-969,-88,71,670,284,-392,-714,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("java.lang.Double:MTQ0OS41ODMyMjE2ODA5ODI3", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{253,-290,667,-473,-61,-748,-626,191,229,-870,857,-987,793,586,-200,781,451,-165,52,858,474,350,543,926,613,978,570,-488,988,-98,199,-471,633,454,-681,-607,-29,-7,569,922,-218,-382,750,204,-677,382,640,869,-793,825,177,818,323,-844,862,-658,678,-789,-59,-228,-650,443,-567,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{-981,499,357,848,-760,557,411,-182,727,907,-705,677,-82,730,-829,485,-744,-824,518,-696,765,-237,-619,566,977,-220,-114,-49,-602,296,-183,-441,353,338,873,506,570,-5,873,-292,111,-182,587,-393,388,-293,-820,836,-85,-312,-718,-573,155,-537,-377,78,402,-570,-172,-828,-63,983,513,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toDegrees(double):double",
            new int[]{-72,333,126,-130,453,-149,113,-499,-121,-38,-61,-554,391,-979,-737,-44,894,248,113,-336,753,-171,-678,450,854,577,667,939,333,-846,-846,328,-4,-173,739,-414,916,-80,-612,685,985,-261,-160,528,-679,570,678,480,316,551,421,-990,-128,-285,-815,-140,-116,305,208,-649,-595,579,-990,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4wMTc0NTMyOTI1MTk5NDMyOTU=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{287,-263,825,958,666,-819,-954,377,799,-98,274,408,996,853,293,-787,363,495,380,-970,-809,761,373,84,914,-592,-969,-835,759,-141,397,-435,963,-33,952,-67,223,986,387,972,-93,-498,977,912,-513,60,752,-784,621,783,-84,-950,-619,436,295,743,-773,-971,10,650,-508,-639,-9,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuMDE3NDUzMjkyNTE5OTQzMjk1", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{-435,-862,-529,501,490,-498,938,70,-780,721,-339,-883,569,889,-542,-243,951,-815,-782,607,707,-227,980,703,545,-288,-719,279,-633,372,-819,10,525,-801,-632,163,318,-477,-134,-484,426,563,950,865,883,-230,-848,-518,182,-777,-567,981,173,916,-7,-309,-515,461,-463,-106,425,990,296,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "toRadians(double):double",
            new int[]{163,-1000,-965,570,477,-63,1000,-103,-900,-59,274,-1000,665,1000,-1000,-782,1000,-859,-682,388,615,317,1000,39,-364,-592,-532,651,-38,422,397,769,884,-1000,-601,-67,617,69,-8,86,779,741,1000,125,1000,-1000,-1000,-81,904,-411,589,1000,59,1000,325,-1000,-490,759,-615,706,472,953,1000,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("java.lang.Double:NS42ODQzNDE4ODYwODA4MDE1RS0xNA==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(double):double",
            new int[]{-427,863,-733,-169,-557,-589,370,30,-501,149,213,464,-135,-919,302,-165,910,-938,-310,-984,-358,406,791,-378,18,-82,-705,917,-776,-579,-362,720,-542,-163,-428,-168,-21,143,278,685,-739,124,228,-580,3,-280,-494,391,410,12,689,-773,-319,-611,520,-990,-26,-714,-391,-135,-759,-961,-827,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(double):double",
            new int[]{997,-351,-563,-196,424,327,743,790,-225,-428,-968,721,264,593,-403,551,-730,-794,94,-420,289,476,992,868,812,-998,-661,905,-432,304,642,-428,18,-250,-200,291,259,705,911,-560,552,-784,-484,613,578,389,-613,-894,-765,-106,-681,-880,281,498,-462,626,-540,401,332,562,888,-699,-690,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4xOTIwOTI5RS03", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(float):float",
            new int[]{-806,578,-46,-533,-951,159,24,548,-875,128,-277,252,-715,275,-851,-110,-328,682,-390,-303,635,17,-242,-869,509,-93,43,779,-959,-811,171,-622,-999,-678,148,352,35,-914,-827,-500,-787,749,177,963,-169,-608,-902,468,59,-246,-870,972,-205,-927,-847,313,-322,-272,263,-767,649,192,941,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("java.lang.Float:MS40RS00NQ==", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.FastMath", "", "ulp(float):float",
            new int[]{249,512,494,-883,-881,-547,-677,-119,-827,458,-726,-885,21,213,-381,269,721,516,334,-928,350,122,626,909,-434,-8,436,-74,494,-181,172,413,-945,-831,-200,-50,-646,-581,147,994,135,761,739,-892,427,-20,-142,-103,-448,-516,-393,821,-616,80,-259,575,408,-613,769,-559,-489,452,-595,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.analysis.differentiation.DerivativeStructure", DEReplay.run(
            "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "acos():org.apache.commons.math3.analysis.differentiation.DerivativeStructure",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.analysis.differentiation.DerivativeStructure", DEReplay.run(
            "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "org.apache.commons.math3.analysis.differentiation.DerivativeStructure", "acosh():org.apache.commons.math3.analysis.differentiation.DerivativeStructure",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
