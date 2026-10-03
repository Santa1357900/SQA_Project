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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,400,-583,-662,-552,133,109,-516,155,247,307,-518,-767,-729,-16,-708,70,364,923,936,1000,494,736,157,-429,-290,-1000,-357,-501,1000,-617,-319,1000,457,-366,-558,-253,-1000,-96,-856,400,-103,340,654,-395,-1000,117,1000,43,-631,75,-142,-1000,544,-1000,-228,442,947,-775,11,296,-520,-1000,368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{434,1000,-104,-66,659,775,-58,742,620,-762,-615,-274,1000,-1000,-701,-230,303,-231,-919,266,10,276,-718,-208,-774,-286,325,140,1000,-466,892,-691,-184,765,-1000,-1000,-63,-265,-1000,620,-129,-1000,245,-574,-592,-782,-525,-118,889,-821,1000,-833,-126,893,645,758,-298,-682,543,1000,40,350,84,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{-147,-519,-361,-494,-782,617,420,906,-65,745,-167,-796,1000,1000,-58,179,-4,-446,-1000,-383,200,-1000,-1000,93,-1000,1000,1000,722,235,-648,1000,242,-743,-381,390,-601,-1000,-476,-1000,-183,-296,-1000,-311,-1000,281,804,-600,-273,915,1000,1000,161,571,519,-346,-604,-1000,15,1000,1000,25,1000,259,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{-878,-207,167,-410,-539,485,439,-205,366,-749,-239,609,219,-49,-117,-145,-200,-177,-315,-128,-864,1000,0,-789,579,464,878,-537,-1000,-286,-91,-910,-803,-1000,270,1000,699,-335,1000,0,-147,1000,329,241,151,1000,1000,-585,179,-645,547,829,15,149,268,-238,941,-448,212,-620,-357,-717,627,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{669,1000,-395,295,-571,1000,-58,313,620,-1000,498,-335,1000,-1000,-701,-967,303,186,-997,861,172,752,-609,-1000,-786,-275,1000,423,899,-466,1000,-1,-279,566,-1000,-568,-1000,572,41,927,853,-39,-183,-1000,-1000,239,-1000,-1000,1000,-400,1000,-1000,-212,1000,1000,606,-992,-682,543,1000,1000,625,507,-628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-784,30,91,-518,508,-255,-370,249,-638,722,-1000,-556,-285,-702,363,-325,-172,1000,-644,-308,-623,-10,-477,785,182,83,-230,-226,332,-698,-1000,-894,-998,-943,-616,713,333,529,-402,84,235,104,1000,843,-873,-939,-642,365,561,-431,-412,376,-119,567,-784,369,111,82,-473,-13,-110,670,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,126,117,1000,-341,-503,-452,95,1000,119,196,161,109,-487,429,753,677,255,-159,763,-428,315,10,308,-731,859,-997,813,-265,-47,-972,5,-282,-201,618,918,374,-413,-528,-217,436,-336,155,-385,-211,-83,-552,694,-338,395,-72,477,1000,272,-452,394,-665,-69,-624,651,306,757,-93,-342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-550,-128,486,-159,-756,-768,-862,101,76,-415,-583,-674,-564,-1000,-385,-143,438,687,-646,382,-1000,984,-817,970,-867,62,395,263,-665,1,-589,197,-452,410,250,1000,-610,-690,-1000,-367,-460,590,-156,-373,339,-331,112,974,191,1000,-676,-278,-383,1000,-180,1000,-657,-797,-1000,728,-336,547,1000,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-215,462,-38,-681,-337,-829,558,-210,686,-581,-396,-283,-1000,-44,161,321,1000,-1000,373,-388,543,-1000,791,-787,849,178,-393,-683,-475,-1000,52,-494,928,100,1000,-213,-1000,-745,-850,-367,325,19,-71,-718,-410,-1000,854,-313,1000,-290,79,763,817,-331,869,428,355,-697,1000,384,730,1000,-623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{270,-17,-300,152,1000,683,29,1000,-1000,1000,-600,-469,1000,-389,655,201,919,464,-1000,-122,553,-1000,-511,-462,193,-107,-717,-498,511,-960,-1000,31,-938,-1000,-416,54,323,-412,1000,-120,625,373,161,585,116,-60,-1000,-106,192,-614,567,-1000,1000,-621,295,-302,-1000,1000,336,539,397,832,454,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-324,653,283,152,794,170,19,-671,-916,156,328,-914,-378,240,-36,-100,256,-39,-53,-377,181,-1000,-1000,449,8,-701,-615,-85,322,-707,-34,-513,-1000,-882,-290,-792,502,1000,-1000,-76,26,742,1000,1000,48,-378,470,639,86,-511,-1000,738,-913,-13,-1000,272,963,214,269,-629,1000,1000,970,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-118,1000,-149,394,823,-155,-400,-1000,423,-535,-248,-1000,516,-382,14,1000,1000,379,-1000,463,-737,-399,1000,-816,21,-820,160,135,1000,901,1000,-17,-96,153,1000,1000,-618,-85,-431,-239,462,-32,-1000,-263,-480,659,37,119,-6,709,-505,-205,-345,-690,182,-170,1000,-1000,608,244,-1000,-28,755,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-170,-1000,-574,-395,513,397,1000,1000,181,117,155,1000,378,-657,-485,885,956,205,-1000,-267,-590,347,-1000,1000,-1000,-152,-922,729,-945,-805,-370,-820,-1000,307,1000,-959,-86,-50,-1000,-245,-573,370,580,-1000,-154,-1000,947,-496,-1000,-474,623,-653,84,-455,-794,-928,231,-20,457,-9,-357,-1000,-150,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-205,201,771,-354,739,1000,-1000,-198,564,1000,-148,669,-962,-412,-895,353,1000,112,846,1000,-160,-689,-374,-1000,686,30,1000,405,311,441,-241,-314,704,747,-1000,412,-532,-1000,1000,991,-496,-209,-589,-444,9,1000,-870,-1000,497,-124,-1000,797,983,-1000,1000,799,-1000,-163,-54,-551,133,1000,497,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{401,1000,51,-347,402,419,890,703,82,264,-215,1000,-418,-506,15,-421,-143,449,-1000,-428,-124,810,304,842,326,107,102,-837,-307,-612,767,617,193,-1000,789,-633,-82,822,-915,7,285,-354,-75,-5,797,-978,-243,74,-133,-154,418,100,799,18,-415,-709,299,885,-501,-316,-247,-450,1000,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{542,615,-464,-629,423,456,496,477,350,355,-562,681,394,-200,167,-34,184,103,-387,-62,211,-104,68,842,445,-166,-88,-189,-11,-138,957,629,352,-681,352,-607,-375,236,-843,-99,700,85,-400,32,139,-82,452,220,154,51,-543,-166,206,368,-662,-504,-265,-229,-42,-226,-254,1000,962,-209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{441,588,10,-226,-1000,-710,581,839,263,96,674,850,-349,835,233,865,-285,1000,-150,451,722,-545,1000,174,987,-259,600,728,1000,1000,98,-227,-1000,-1000,-1000,-714,739,-407,1000,946,323,157,-770,571,650,-639,688,638,-570,78,-352,867,-194,-962,724,644,460,597,912,-497,494,-132,1000,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-494,445,446,-641,543,643,-1000,822,514,363,-286,-43,40,1000,-1000,-1000,288,785,1000,121,377,1000,578,-660,1000,748,289,-291,152,857,1000,-267,834,1000,301,1000,-297,-187,-725,-1000,243,-951,-450,189,-1000,-701,-562,-1000,1000,-343,-1000,262,-1000,1000,-1000,688,-259,-267,93,814,-174,-1000,979,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-267,722,522,-867,1000,484,-392,1000,420,511,-1000,231,-1000,653,539,-629,157,569,553,178,910,762,294,-183,1000,611,-746,-817,-338,-381,443,815,22,-905,-181,580,-177,-1000,-657,-298,-279,-929,-207,-224,164,-1000,-1000,-318,-400,-745,608,-238,-218,929,-618,455,667,-1,300,-190,-414,-307,1000,163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-312,271,275,-214,-172,753,-47,959,-180,-14,407,-473,-1000,61,-322,-582,-81,-389,134,-779,-18,210,-605,-316,-784,-668,-1000,816,-16,-791,-106,-1000,-1000,-591,604,-396,-627,874,496,204,896,-1000,1000,-228,-303,314,-427,-937,961,153,1000,284,-786,60,278,-1000,1000,946,-437,195,-224,361,-406,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-751,-435,-8,-270,149,1000,393,-294,-165,1000,188,1000,1000,-556,-127,399,-78,485,167,-576,-27,291,-501,331,-403,560,-400,439,1000,572,-262,-1000,925,-646,553,-113,-1000,445,-218,31,73,-396,1000,359,316,-47,642,-1000,-259,925,364,-249,716,152,124,399,-143,331,238,999,80,275,-826,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-750,310,-325,79,-227,837,1000,1000,97,435,194,-54,-1000,687,410,1000,657,385,1000,400,-792,108,-543,554,62,1000,-77,-1000,482,-644,169,103,277,780,-355,-370,190,-1000,316,-226,-418,35,-228,-489,976,290,-463,2,-124,473,985,297,666,-448,-239,-1000,1000,806,-359,377,297,645,1000,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{141,-55,663,354,894,247,-854,-692,227,-100,700,-1000,721,1000,328,21,261,22,661,-1000,-397,54,-65,314,-262,-115,72,1000,-279,772,-829,230,-209,-761,347,-799,-1000,501,432,827,-1000,-177,-904,704,-486,-843,-509,-1000,-892,264,-779,1000,-128,-486,-64,588,119,-1000,-835,-547,74,583,-997,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-223,-1000,31,439,409,-40,-304,347,46,1000,1000,-119,1000,-702,94,-1000,371,-790,-476,-397,357,357,842,795,105,-441,-574,-163,-180,1000,770,-42,-843,-334,484,-63,-561,-1000,111,650,290,401,-1000,752,479,-427,-460,-245,1000,308,-591,-84,499,1000,-1000,336,-1000,-1000,-1000,-460,139,-502,12,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{622,-1000,663,337,566,669,-854,933,227,1000,736,-680,577,6,40,21,-120,-1000,202,37,-736,1000,1000,314,-816,-1000,191,-127,1000,772,-30,281,3,-149,-893,-753,-1000,-1000,-434,345,-634,-355,-1000,419,-522,-843,721,-305,1000,-734,-1000,1000,927,-486,-1000,-411,-329,-1000,-970,-382,608,-1000,-164,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{367,881,-604,-734,764,251,-74,-91,540,612,988,539,-971,-158,-189,756,115,-472,-259,-520,194,643,-859,288,-38,-821,471,638,-432,-526,162,-334,-260,411,996,30,-931,265,-685,116,-198,-441,-447,-220,-803,-43,379,-205,813,980,13,152,-80,-877,-822,-519,-376,-209,582,962,-690,-567,-177,-661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,-332,526,384,-37,469,-382,49,31,1000,-364,886,-609,-252,-724,-25,-670,203,-1000,725,-306,343,-41,-632,-149,-179,-380,585,-627,1000,474,-619,471,-736,-956,128,-696,-12,-783,912,-735,1000,1000,118,-615,-903,-420,225,-755,429,-1000,1000,-251,982,1000,116,-649,-509,934,-872,-173,-837,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,383,170,-569,-389,-179,189,448,250,709,454,-430,-180,-155,-177,-77,-318,-466,765,679,868,391,457,142,254,-403,-345,166,44,-627,1000,293,-339,70,188,-94,21,-317,317,247,469,-794,1000,1000,563,-303,-595,-936,65,298,-715,974,1000,-290,747,353,-292,-1000,-195,1000,-342,-277,-900,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{713,525,-728,-569,-194,-105,-1000,287,466,696,48,121,-180,-155,-257,-581,156,480,962,679,-777,232,-889,521,-256,585,200,-952,400,1000,273,123,-1000,-265,-307,1000,-708,269,112,247,1000,-1000,464,431,214,-303,1000,408,65,122,1000,-236,-390,152,-735,-188,690,-556,-198,593,-271,365,194,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,-415,138,743,-299,-475,-131,-554,-349,245,137,1000,244,-146,16,-623,-1000,213,280,693,-852,-88,-386,-1000,202,-1000,-125,665,-71,439,349,-514,-271,182,-704,354,-1000,664,-402,9,-588,1000,366,270,78,-797,386,-873,-537,-723,-700,184,-358,60,-364,-187,-183,328,-309,524,11,-750,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-69,661,-750,-283,-597,-165,127,-258,399,551,518,324,1000,-195,-327,39,293,-216,381,-481,676,-130,378,617,-256,-1,-781,-85,400,-1000,-148,123,-683,-57,-401,316,-926,-142,117,-396,782,-1000,1000,632,296,-227,56,-702,-547,-169,652,-236,323,-292,-22,198,-370,350,-23,593,-292,0,-1000,510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,138,322,964,1,-500,161,-806,-780,-353,34,1000,-98,49,202,-371,-691,-377,932,1000,-371,-746,-644,-799,657,-1000,53,488,679,-84,479,-747,-591,53,-365,483,-572,515,-601,-482,262,790,-340,114,296,-448,703,-500,-43,-937,-151,-796,-325,-375,258,-734,92,546,418,1000,1,101,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:LTgzLjE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{537,-1000,-831,-146,24,-302,-532,-256,-145,-607,-85,793,-65,574,-151,-865,280,80,-273,-312,-540,386,928,737,348,213,1000,347,651,-535,-253,592,874,1000,-665,174,-109,-515,-484,-462,41,312,1000,-316,-369,694,737,-417,767,-155,-539,120,-497,-371,-1000,477,-28,469,446,-24,-358,31,-422,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{734,301,-349,1000,609,-251,-408,-1000,1000,143,-1000,-345,-970,264,-713,-703,1000,345,-183,0,-1000,-520,-554,84,265,-73,-234,-452,-150,-1000,-309,58,798,893,-1000,-756,-941,-575,-1000,355,904,1000,916,-228,-44,-64,679,-503,751,-627,173,-957,-1000,390,487,658,759,110,1000,-55,-1000,-1000,-1000,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{262,-119,-553,480,-787,337,-680,-852,527,-597,-824,422,-1000,409,-893,-627,705,620,-288,-152,-1000,-40,-54,765,99,108,394,-621,7,-1000,-688,325,952,-108,-975,545,-546,30,-1000,0,438,1000,174,-553,-375,-1000,1000,-850,195,-473,905,-793,-1000,-268,238,-401,363,285,1000,-399,-1000,-280,-849,-351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuNjU2NjEyODc1MjQ1Nzk3RS0xMA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{371,818,66,-703,-640,152,-651,-919,-257,-172,38,272,-1000,579,-372,-1000,258,1000,-495,-363,-150,362,1000,1000,1000,-656,-331,541,211,252,-8,413,1000,-1000,-876,1000,-593,-71,-519,511,-258,1000,655,-1000,34,310,770,3,-88,-385,-274,466,-1000,-461,-235,240,-550,-221,-121,-1000,299,-199,223,134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:LTE0LjA=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{705,153,14,-481,-134,-874,-401,-783,135,-92,-507,158,387,115,-151,-497,611,688,219,88,-148,-590,462,800,1000,-433,-331,347,357,418,374,428,980,-210,-876,265,-503,-652,-524,459,-61,168,198,-511,231,241,186,-417,332,-285,-828,120,-619,-985,-354,710,108,469,-121,-285,299,-553,-197,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{772,-55,-240,-17,-486,146,1000,46,134,772,205,1000,529,190,-19,-735,-588,-722,54,-45,-1000,-957,507,-365,556,1000,491,-187,-459,605,-414,287,259,-322,577,-786,811,1000,179,37,537,588,-704,441,869,1,172,-228,560,210,310,664,-151,940,-945,-203,1,1000,723,-533,-317,-5,-249,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{200,5,54,315,-96,991,844,1000,745,764,404,159,744,-873,50,-1000,393,-78,-577,1000,-705,886,427,434,-269,882,-470,-642,-23,-935,-127,645,963,386,-240,98,157,-98,-1000,349,662,812,-951,54,-187,884,240,345,-703,-153,959,13,-198,1000,493,-471,789,575,799,296,-568,-702,-838,5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-639,42,347,391,-1000,-135,982,-311,48,-36,-897,1000,641,285,450,-627,-1000,-1000,959,358,-1000,-1000,1000,-393,-685,854,661,286,-563,522,-33,-723,553,214,723,-1000,1000,-129,884,-741,1000,452,-198,54,-284,-103,1000,-1000,-703,-665,-833,13,-1000,776,-73,580,635,1000,697,-910,-418,623,207,455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{489,1000,-96,-926,-461,-266,447,36,267,-668,338,27,-304,932,248,-305,-268,-227,618,361,157,-967,1000,23,375,1000,199,381,284,673,162,-634,1000,673,-449,-168,-105,-781,-299,213,94,-763,-226,834,666,541,718,-149,-346,-1000,-486,814,161,914,1000,689,1000,-707,1000,-86,-431,627,-725,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{1000,-742,546,274,511,-67,754,388,2,779,342,1000,36,100,992,-645,881,377,-1000,405,-686,321,-833,1000,83,1000,-367,-343,113,-828,-26,225,629,-921,-316,389,61,469,-596,146,518,742,-592,-709,1000,610,-187,-271,-1000,690,1000,147,153,557,-409,-500,355,975,564,-110,-807,-338,176,-666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-125,188,-431,-175,-233,-229,854,-363,-491,437,522,968,285,827,-986,-621,-928,-546,309,-493,-880,-844,536,106,-913,863,169,519,210,233,-188,-375,455,-440,-19,-777,234,883,685,333,0,224,-897,958,-224,-523,-782,-535,509,-342,-150,946,-947,915,-768,668,513,997,996,-970,-293,720,235,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{1000,-725,-183,-413,206,375,758,239,777,1000,609,717,405,-63,-992,-807,59,846,-795,211,-629,123,1000,467,750,1000,596,-278,-93,-145,-336,587,511,-321,-10,-68,300,1000,-160,543,-261,786,-869,-16,770,163,-1000,204,-75,-138,910,486,1000,1000,-1000,-345,-51,690,397,-178,-537,-161,-46,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-1000,-1000,612,-173,339,-763,52,369,-614,496,1000,785,407,1000,893,371,1000,-967,-599,-358,1000,-829,-450,-367,-730,1000,-107,-612,1000,970,-400,994,-1000,1000,-809,-57,147,-1000,-680,878,-587,827,-976,-543,-1000,-1000,-136,-254,104,-453,-1000,-951,-689,635,1000,-1000,-849,1000,-765,-327,-1000,-571,-93,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{600,-1000,544,247,271,-171,-287,713,-646,-891,824,-273,1000,246,-274,47,450,-415,-254,-55,78,-1000,246,54,720,1000,-1000,-915,647,972,-167,994,-101,176,-955,2,-329,-1000,438,856,344,375,591,-1000,33,291,-779,580,860,-448,-1000,-334,-29,628,877,-528,80,-703,-127,-92,-1000,-1000,-232,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Float:NTMuNg==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-625,-1000,536,-314,188,-661,174,-132,-377,98,407,-47,822,1000,519,-111,863,-662,103,8,1000,-1000,1000,58,-364,937,-438,-598,1000,577,-433,886,-760,613,-761,-471,-1000,-265,-24,481,-209,753,-400,-912,-958,-147,224,-313,473,-477,-1000,-1000,150,539,880,-382,-815,1000,-921,81,-1000,-748,222,658}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Float:NDMxLjA=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-625,-1000,431,-337,294,-1000,-419,-238,-121,718,407,626,531,1000,807,302,402,-471,-57,336,769,-683,-16,292,-564,824,55,115,1000,-134,-589,886,-37,723,-761,-190,-724,-286,-24,552,-209,1000,-953,-604,-842,-494,407,-712,473,-392,-619,-1000,-94,563,625,-393,-692,-233,-820,-201,-1000,-748,-71,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-1000,-995,1000,-210,-166,-1000,-585,-382,-221,1000,-1000,211,1000,1000,1000,-916,1000,-1000,823,318,-367,-877,-450,-209,-646,1000,-451,858,1000,884,-609,-948,341,1000,6,1000,-494,-461,-1000,-3,-209,1000,-1000,-552,-1000,-36,417,-792,530,1000,1000,-1000,48,737,1000,-1000,-1000,589,-1000,189,-918,-1000,-225,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{1000,-829,649,1000,583,631,174,395,-486,-102,407,-185,613,-644,-59,-264,-57,-107,1000,1000,352,-778,482,955,76,-1000,-729,1000,1000,577,-1000,892,173,613,-502,198,722,1000,986,-620,-572,1000,1000,-185,-958,-147,554,-1000,1000,-768,1000,-961,150,539,-584,1000,-930,-392,874,81,-400,-793,-13,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-687,-1000,631,327,-101,890,328,-392,-1000,-527,-967,-273,-87,1000,487,-661,206,-459,1000,-366,415,-929,277,-968,504,770,-1000,4,1000,1000,-118,-1000,-1000,1000,-354,206,-28,-557,-340,-1000,-715,-369,249,-1000,-904,212,-678,91,272,957,107,-1000,-1000,-127,496,-602,-1000,1000,-120,655,-1000,-542,-735,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{91,-518,451,-482,-827,-29,634,-519,-28,400,-427,939,365,181,-220,526,-63,21,-379,334,355,-733,405,715,-141,-213,-810,-248,1000,220,151,602,594,554,594,-313,-115,-562,122,-155,639,720,-112,-437,-273,-449,-748,212,-545,357,-198,-313,324,157,706,-64,-345,620,-504,263,384,512,250,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTA=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{451,900,479,538,-51,-153,-379,-1000,519,-1000,-155,39,73,830,-236,-578,-710,688,970,394,110,-933,-923,-6,314,-1000,75,-546,-1000,-555,196,-667,-381,-155,94,869,-521,-313,214,401,385,-152,325,681,262,-72,-414,-1000,1000,-19,-1000,64,333,-395,-496,-13,985,-1000,772,-947,1000,332,332,408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{-34,-585,1000,-173,-661,753,598,265,-194,-323,-387,362,757,478,-932,-65,513,-183,321,90,1000,555,538,208,-911,-336,-162,313,-973,1000,-243,1000,171,862,939,984,-970,-688,417,-428,-118,-310,-980,-178,-188,794,-823,282,1000,-302,-477,-982,598,583,-534,-776,497,-91,343,810,-202,-946,272,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{-169,-630,479,126,-51,-153,527,-277,627,100,-608,959,859,-335,-482,279,-1000,688,-117,1000,277,-604,-923,1000,-391,-1000,-462,-606,-373,1000,262,-475,-577,-203,812,271,-116,-996,-40,401,385,492,-587,-292,545,746,-376,67,-191,1000,182,-122,1000,1000,1000,380,-1000,964,-1000,-642,294,828,1000,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{-78,801,1000,479,-296,-837,-7,-580,502,-1000,35,451,493,571,-197,-445,-472,245,1000,935,596,-881,175,349,-497,-241,-253,-403,501,250,39,-211,-306,925,641,995,-842,-166,294,216,-931,128,-474,683,-387,94,-557,-373,353,233,-417,-131,871,-499,-59,-347,831,-283,671,-539,1000,45,72,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{888,-630,-310,795,-211,303,227,243,-287,785,-232,-317,-480,361,-242,-243,-128,744,-919,-1000,-74,748,-906,-559,1000,329,-354,624,179,-1000,36,1000,276,266,-526,-595,-70,-874,246,247,1000,496,1000,-1000,267,-444,-326,-561,-279,150,-1000,-323,-644,617,182,-27,699,823,63,112,97,410,872,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{95,-908,1000,187,-639,1000,284,43,-1000,52,-1000,1000,-742,145,-1000,421,444,67,288,-1000,1000,467,1000,92,637,-1000,-1000,-137,807,-590,-884,725,-1000,1000,1000,234,-882,-721,885,-3,1000,350,708,-679,-356,-406,704,814,264,320,395,198,-574,-14,-4,-79,210,-277,962,939,1000,1000,605,372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{179,360,-193,-101,-617,-288,165,615,-785,467,-68,430,-574,-1000,1000,869,1000,653,210,1000,86,517,-25,-933,-1000,661,-167,125,-246,-786,1000,-1000,617,915,-441,400,-1000,26,629,1000,-5,-735,660,370,-883,308,-490,-1000,-171,124,998,-412,-191,-1000,199,1000,477,872,-585,-654,509,-1000,-294,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{629,488,-136,-316,619,-334,574,-716,674,-820,53,-258,-188,-771,670,297,321,851,208,-931,33,-392,-265,-46,-662,876,918,883,-766,-985,-6,-538,-546,230,-540,904,-769,-280,259,362,-367,-173,746,-683,-437,-659,33,-494,-517,855,453,17,-268,-875,842,-344,-94,744,-943,-552,164,153,782,-756}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{402,291,99,-276,-776,-805,634,-358,-8,469,-254,-313,-426,727,624,299,-897,-734,225,-923,-845,-31,202,389,833,-782,-230,213,954,281,125,904,-720,-890,880,419,275,310,-293,792,-784,-830,876,-832,657,29,685,-485,219,-936,-51,325,482,589,-376,-954,344,340,-576,-226,626,910,853,832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{16,-32,429,-117,-107,-239,237,-385,792,-498,-102,-112,-201,243,-118,164,609,49,-505,1000,742,-185,-91,115,-802,442,29,-533,-531,-456,-385,-667,182,734,-239,422,-839,-544,664,-594,345,-247,842,-1000,-95,-336,-809,-855,-573,-106,-289,-717,-311,-851,-410,560,264,-36,216,329,723,-398,477,-982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{-19,-233,-449,309,-768,-975,838,-123,859,-399,73,-777,-408,652,1000,5,238,-847,277,222,-147,-104,-1000,219,-113,-868,-299,19,-1000,-414,-118,-643,-1000,123,975,-71,-1000,-424,-363,-56,-264,-934,1000,832,376,-619,107,-1000,-475,-172,-51,673,286,-938,-848,-477,986,763,-105,-1000,911,-703,139,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{531,-630,-326,-537,-543,-297,357,351,1000,107,1000,119,-587,-739,1000,60,496,1000,-58,-804,-249,-198,-866,-355,-1000,828,-295,125,-607,-1000,914,-1000,-105,1000,-737,-837,-1000,163,745,550,432,-163,301,912,-192,-15,44,-1000,-858,992,703,1000,-457,-1000,-465,-534,327,603,-851,-1000,741,-591,915,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{629,-545,-708,-564,329,57,714,227,-3,-734,810,-542,-398,689,-523,531,-902,317,-304,-801,907,203,908,-67,-459,-588,-920,18,177,302,-487,624,671,-91,735,-331,-66,127,929,151,-159,671,272,-187,-69,-616,-747,-508,40,-630,637,323,-308,-373,-964,71,598,-605,-744,310,-66,255,545,896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{-369,710,785,-449,199,622,564,-402,-141,1000,-1000,1000,-943,-188,1000,-495,-458,-701,1000,1000,-1000,37,-1000,-46,857,-339,-67,543,-1000,1000,756,624,-1000,-589,-554,-660,-53,-502,1000,-1000,-1000,-668,28,1000,26,-1000,1000,673,968,-1000,-1000,1000,-344,912,-1000,-1000,-859,-1000,81,1000,1000,787,547,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{228,-290,-278,2,127,981,-421,-146,-237,-144,136,-423,71,-169,-107,396,-322,-287,1000,715,-123,176,-187,626,268,-604,-381,-286,-885,1000,1000,206,-1000,637,-318,472,44,-591,10,-1000,-1000,-165,287,534,-300,-671,-212,-130,243,-569,-429,853,768,14,-1000,-1000,-312,-620,683,1000,863,912,-82,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{-496,411,579,495,-181,-51,-411,-560,-167,698,-372,-490,-748,44,491,-884,-599,760,729,1000,-734,-47,-664,483,804,-61,-166,290,-1000,878,571,754,-1000,420,-264,-282,390,212,1000,-413,-621,-342,-465,1000,-279,-465,415,-1000,1000,45,-533,369,-19,168,-925,-123,-352,-1000,-63,1000,777,367,382,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{444,712,493,-161,-741,-383,156,-857,-587,680,971,-639,-943,34,524,138,-523,537,-121,953,-677,-508,-73,658,-332,-129,-140,532,-667,114,-104,597,-904,353,-260,238,1000,-67,-833,406,213,-289,-396,239,-765,-22,68,-993,377,-561,82,141,680,-422,-1000,990,-412,-667,-1000,855,632,213,146,-464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{283,-646,-327,771,671,349,564,-487,-91,1000,26,-1000,342,1000,-569,53,-1000,128,834,277,253,-687,832,-7,736,-774,-1000,1000,-88,939,-884,28,-999,578,63,724,39,-1000,-679,-1000,67,-1000,377,616,895,-1000,-1000,728,519,-666,1000,219,141,-1000,-568,-613,-918,317,-288,1000,304,294,877,-161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-746,157,-303,105,1000,-306,-220,37,3,-1000,302,394,-928,-297,-269,-96,-868,437,-862,-1000,-1000,-471,-306,665,-1000,751,-653,632,-222,655,341,-358,-442,-371,1000,356,-1000,127,940,-480,-964,27,-1000,-867,495,-1000,-919,352,50,-919,902,-1000,459,73,-864,29,-1000,-1000,-274,804,-38,-1000,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-373,274,-540,-404,474,214,-775,-939,-648,502,809,724,-167,-529,-174,-919,-442,-153,850,941,604,196,-446,-792,621,591,890,-661,94,-448,438,-942,-434,530,-319,647,-52,784,-603,10,-851,-633,643,53,-827,103,-143,-534,-446,-50,-505,-86,-458,938,333,847,824,-996,32,-332,693,-546,953,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{467,-320,-587,-178,276,-479,-26,488,341,188,221,-238,-841,538,861,-131,163,656,-644,255,-837,-622,894,338,911,-432,807,372,998,811,-83,-850,766,-622,899,916,-800,902,208,-187,873,86,375,467,945,-90,-449,-78,916,330,-934,-1000,409,-750,-549,405,595,678,902,396,21,404,-521,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{604,549,264,707,800,-233,-601,25,325,-363,-352,524,-841,-240,234,-816,-598,595,639,915,-465,913,107,712,-430,429,289,-482,-367,68,-198,292,-51,548,114,492,532,-463,609,-901,-58,488,961,609,445,471,991,998,-434,-222,102,-434,-351,903,37,-215,-684,633,279,-211,-60,375,656,-996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{433,498,871,638,261,-393,975,-308,750,-336,773,57,-510,-390,982,47,-452,508,709,-610,935,-793,-552,262,53,-178,456,399,184,-751,-715,-888,539,185,943,-348,687,-306,553,356,-731,-795,-329,286,847,-971,-525,624,437,976,940,-959,-161,431,794,417,-790,422,577,-143,-480,578,435,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{-612,585,1000,55,772,461,1000,-1000,21,987,299,-919,-782,-1000,241,383,-720,659,1000,1000,141,291,223,14,-932,-1000,-886,-25,-228,1000,-672,-1000,-1000,803,-9,162,734,-1000,240,1000,716,-192,1000,-1000,224,109,-763,10,-1000,-104,-1000,129,-1000,-1000,443,-95,463,-1000,-823,789,167,357,1000,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{-226,-102,568,-731,-258,-589,-606,400,555,-456,-1000,428,-67,-231,309,-225,957,508,-1000,130,-1000,1000,-1000,-301,-1000,-765,789,-1000,888,519,-1000,-114,-1000,1000,1000,1000,364,-229,36,1000,-1000,-728,-22,-946,715,601,-613,597,-596,-7,1000,-255,-578,1000,-583,-1000,1000,-189,-1000,-1000,284,657,-16,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{307,723,-199,-186,227,-609,-566,702,406,338,191,-58,-1000,237,-576,-611,331,-499,-380,-210,135,357,1000,1000,-1000,-514,689,-795,-357,-37,-883,-1000,877,-1000,292,1000,443,-853,-1000,492,94,-196,381,1000,731,-594,796,-626,-1000,618,-610,-391,635,321,345,-225,-234,951,-1000,-1000,1000,-1000,-965,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{352,-505,-342,-743,-141,-21,-863,702,624,915,-781,-58,204,258,119,-12,-128,611,825,349,-409,881,-126,-252,-36,-398,-93,-379,-28,210,-556,-709,75,872,-689,532,14,-627,-735,665,-459,976,471,-706,216,183,-275,321,-611,-104,-456,-24,-438,-406,625,-747,-604,-170,-490,-627,845,-374,803,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{307,-1000,-1000,-754,-72,111,-1000,226,918,1000,-227,365,-175,8,1000,1000,636,1000,1000,1000,-1000,947,-859,-1000,-194,-419,-1000,310,1000,1000,-773,-289,-834,-425,298,1000,901,-1000,896,492,1000,-859,1000,-1000,-183,676,504,1000,1000,-1000,-1000,1000,-1000,321,-1000,-1000,1000,951,-1000,-622,-1000,-1000,-69,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Long:LTg3", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{-1000,-400,-872,-890,298,-745,-1000,452,-185,-411,108,382,-305,-452,1000,-661,1000,-539,-784,59,258,-485,443,-923,531,-589,-1000,-1000,-1000,-395,445,-60,607,-400,-400,-270,416,-878,-1000,1000,-279,-641,1000,-129,1000,748,13,-1000,-127,-1000,1000,99,-914,-766,-1000,1000,-885,-1000,-928,167,929,86,130,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{312,10,-909,565,-486,309,574,35,668,29,1000,-1000,-706,1000,-1000,-776,509,-462,-471,480,-103,358,-35,252,-678,-590,394,2,809,156,-1000,154,-157,828,1000,-487,-624,-537,-560,79,-189,-480,656,1000,-1000,1000,0,415,-538,-1000,494,44,866,-269,394,1000,-1000,1000,80,0,67,-219,671,-788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Long:Mjc1", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{1000,-400,275,215,-22,821,-606,-1000,657,-552,1000,180,-97,502,-393,1000,745,-251,1000,-201,-160,1000,-531,55,322,-417,-150,225,294,1000,174,188,-1000,-56,521,-8,-75,-59,153,-894,-406,-995,-1000,-400,-1000,-287,530,483,135,-88,-858,143,1000,548,-426,-447,-1000,481,287,-564,-1000,-6,1000,-4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{-314,-119,783,-462,-216,6,43,313,635,561,925,649,-700,920,245,-243,-818,212,373,1000,423,-503,-476,183,600,-387,320,1000,-947,1000,1000,817,-62,-183,632,-356,207,-528,1000,247,-632,-151,-730,-709,-1000,-292,1000,320,-103,-229,1000,-281,915,-248,1000,1000,166,345,211,34,347,507,482,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Long:OTk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{180,-445,998,-758,-329,629,-1000,62,-410,-86,1000,1000,533,1000,951,1000,1000,269,899,-1000,-199,1000,-1000,-607,-669,644,-1000,-1000,-602,999,574,-18,-931,188,-813,959,-969,-296,-412,-48,-689,-67,-137,-1000,-1000,1000,-636,406,382,-1000,-1000,528,504,-844,-488,189,-731,-300,-426,1000,-864,1000,757,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{261,-20,-1000,835,334,-1000,1000,457,441,-92,-302,1000,-1000,106,-237,-982,-204,622,-866,1000,-224,-1000,92,-806,-1000,530,355,-208,-90,-1000,-1000,-696,1000,-1000,1000,-287,423,-73,-332,-1000,-404,199,837,400,580,833,-426,509,-905,-1000,389,260,-549,919,-314,1000,-1000,1000,-433,-120,1000,57,-281,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{749,-576,779,514,192,457,-1000,-403,47,643,1000,235,835,599,1000,1000,-375,-157,1000,73,1000,-1000,-519,-903,-1000,1000,-398,-11,798,45,770,1000,157,535,-916,-779,511,-802,-328,1000,-743,730,1000,-1000,385,532,-1000,-528,1000,1000,408,952,-872,1000,388,68,782,-1000,-803,-38,1000,-738,-1000,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,684,367,422,1000,-48,-970,-895,-387,1000,-943,-471,-902,-728,183,7,-849,1000,-896,-186,-214,-1000,-644,-1000,-1000,480,-975,-138,973,525,920,1000,-1000,486,-1000,-1000,622,-904,1000,96,-203,371,-1000,1000,1000,-737,-750,-957,1000,-611,566,875,-896,476,-1000,103,-526,-1000,1000,1000,195,-849,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-463,1000,153,31,151,-826,341,-112,376,645,-1000,833,219,580,616,-4,170,570,399,686,-1000,-128,-549,-1000,1000,-878,420,620,-265,510,730,182,-77,-890,-292,129,-860,51,1000,-1000,78,695,-694,557,1000,-757,-515,638,304,35,463,-1000,833,22,265,349,-1000,-204,-589,583,-697,-382,211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,-1000,306,-217,251,912,-966,1000,-1000,519,692,-1000,-1000,-229,941,224,630,1000,-1000,-1000,1000,-1000,1000,-1000,-1000,-85,1000,278,1000,1000,304,1000,-872,1000,-1000,285,1000,300,749,1000,547,-1000,-689,1000,427,1000,1000,152,-433,-453,342,1000,-1000,427,-1000,-1000,1000,1000,1000,861,1000,-1000,-616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-479,-249,241,-77,47,-1000,137,-1000,697,-733,-915,263,-439,-416,551,493,294,-441,1000,-190,-185,1000,-578,-414,-112,-326,709,1000,-940,-1000,-671,-311,682,-1000,377,-550,-1000,1000,294,41,-929,610,541,1000,-673,850,-1000,-166,-489,-188,-1000,-1000,-520,-153,-227,803,-1000,33,-867,-1000,-1000,458,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{386,-1000,-549,-533,453,-995,-1000,492,-782,-293,1000,-680,1000,729,-408,338,183,-395,1000,13,-231,-126,177,-181,774,-920,-33,-636,-317,-325,703,-928,959,-737,-185,-250,953,-1000,19,719,-675,-832,-1000,11,-502,88,-717,-156,-485,1000,247,-407,-249,182,-1000,1000,-763,-528,482,-172,304,-289,-171,-153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{112,356,683,533,999,635,-1000,685,-897,-1000,680,-394,-911,413,-884,-517,-67,-219,419,1000,-504,-52,1000,-1000,926,-441,571,-629,367,-916,-951,1000,-83,-399,-677,1000,115,-484,1000,-260,1000,574,1000,-1000,-1000,321,-175,898,-276,-220,-358,-1000,1000,748,350,-913,427,1000,1000,618,-1000,888,168,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-564,841,-905,-634,-537,-579,-355,1000,724,1000,-867,650,949,1000,-217,-561,-637,35,-458,-235,-412,1000,-15,752,92,-923,-629,-632,-624,-108,53,-860,1000,-763,208,-201,177,-31,-1000,-550,-550,-1000,534,127,197,822,-406,-1000,51,695,-312,-303,843,-592,1000,-206,-326,-1000,503,432,443,492,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-883,-569,-758,910,-514,124,341,704,-996,-47,-875,1000,424,-653,218,1000,264,107,1000,-102,-1,173,1000,764,-1000,452,-417,-1000,-859,1000,493,-115,119,-507,-70,152,102,1000,-472,-458,-410,-272,403,-400,197,525,-507,-303,726,445,-950,551,164,-71,-25,-901,603,262,-121,-46,-447,-925,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,89,-77,538,993,-461,-275,-1000,-740,-475,189,-1000,703,113,-1000,-1000,481,-770,410,-935,745,176,-170,-232,321,41,-904,158,310,240,-239,1000,-331,527,865,-439,1000,-53,-686,-329,574,782,-1000,288,-17,91,1000,204,309,-942,153,879,169,-123,139,994,612,783,982,-651,318,754,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-707,403,-873,-248,975,538,-61,-897,128,-551,-30,-313,-571,920,-253,1000,-192,-326,-361,806,-632,246,318,880,-184,-1000,1000,892,-113,541,91,-650,517,-216,1000,824,-713,565,908,819,192,256,426,938,138,1000,709,-416,208,544,-1000,420,-495,-550,-866,11,-329,1000,147,-572,-98,202,-841,405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-658,138,-566,1000,-40,-161,754,-1000,-692,607,-1000,1000,1000,-283,-627,-611,1000,858,1000,-1000,839,1000,348,713,-236,-1000,490,-1000,-228,586,-457,-695,394,-1000,482,-375,54,1000,-564,-606,-329,272,-869,-1000,1000,18,-925,-1000,231,-1000,-790,-292,-164,-1000,-607,-1000,183,1000,729,905,1000,-65,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,-36,-156,243,1000,-149,-916,-578,-740,1000,-85,-1000,608,682,-897,-1000,856,1000,479,-1000,1000,112,156,-915,813,-127,-990,-535,1000,262,-793,798,-82,876,1000,-1000,1000,-986,-730,-954,-618,106,-1000,1000,-83,69,1000,-409,939,-844,-27,1000,390,-353,465,795,350,-386,1000,-454,510,605,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{103,1000,25,211,348,13,-756,145,21,-1000,-689,645,564,570,-890,784,-697,678,1000,-242,1000,15,913,631,991,714,-1000,847,139,314,293,-720,-596,1000,-1000,-192,-1000,-17,550,-1000,603,-1000,-1000,425,1000,-55,-1000,1000,-910,-931,188,595,-1000,-965,-589,172,-205,1000,1000,-79,997,-147,135,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-93,1000,-590,85,994,10,-1000,1000,-677,-594,233,359,1000,1000,-743,983,-362,201,-989,20,365,247,715,572,1000,1000,-263,220,-817,-154,742,-1000,-479,539,-1000,-499,-1000,-255,448,-673,657,-361,-1000,-1000,1000,-512,-1000,1000,-884,-463,-1000,1000,-926,-1000,855,282,-1000,746,324,532,-172,-16,1000,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{299,-1000,-1000,258,843,-671,-448,-857,307,606,-368,-725,-1000,1000,-1000,407,-324,1000,648,-1000,-927,279,1000,-5,465,420,-1000,-56,598,1000,-593,-118,-1000,257,-516,491,-1000,584,467,-1000,-1000,-1000,97,-1000,-276,-53,-1000,-412,-930,281,-321,261,-1000,-1000,344,1000,-144,1000,1000,-1000,1000,-849,569,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,1000,27,158,-438,-1000,-895,735,-1000,-1000,741,483,-68,1000,-327,-445,1000,1000,733,1000,285,367,1000,1000,1000,164,1000,467,1000,796,-1000,-755,1000,-939,330,-1000,-25,1000,1000,512,789,-1000,497,623,571,-1000,1000,-1000,-1000,1000,373,-1000,-771,-581,-614,380,1000,1000,-1000,1000,-82,-995,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{649,1000,-1000,163,238,-567,-1000,659,-89,1000,-263,759,677,753,1000,439,-874,-51,1000,-1000,-682,-1000,-851,-594,1000,-1000,164,-1000,-1000,1000,761,-798,904,-540,-1000,-252,-1000,-915,-1000,1000,320,789,-1000,670,1000,-396,-756,400,1000,836,-649,373,-189,-402,-1000,1000,-999,-700,57,1000,-1000,-543,-407,-150}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-653,1000,604,103,-171,204,1000,-161,467,-1000,-623,966,905,114,-824,708,-1000,1000,6,233,1000,91,1000,944,1000,1000,-1000,1000,-393,325,918,-1000,-696,1000,-946,-11,-1000,-165,574,-1000,-156,-1000,-1000,-147,890,547,-1000,1000,-1000,-1000,-128,-746,-1000,-292,-1000,-105,277,1000,1000,-856,1000,376,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-686,1000,1000,223,777,-677,-397,-1000,-3,-706,-958,710,-1000,-435,-820,399,-974,901,1000,367,643,244,628,1000,995,1000,-1000,5,1000,80,98,-1000,283,1000,1000,791,-1000,540,1000,-1000,46,-1000,-1000,913,623,817,-1000,701,-1000,-1000,1000,-1000,-1000,731,-1000,-993,970,799,1000,-1000,330,-11,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-210,133,307,106,389,-345,239,-759,-120,400,-727,-26,-452,-408,-759,405,20,961,769,162,-490,195,479,852,993,791,-590,159,761,688,-662,-24,-959,742,-41,289,-1000,340,1000,-1000,-362,-1000,567,-630,-126,-205,-719,-407,-687,-20,-283,-1000,-1000,-1000,-249,-229,1000,748,717,-1000,1000,-599,-527,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Double:MjkyMC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{940,1000,292,106,439,47,-1000,-113,-410,-1000,-521,1000,-873,238,420,-1000,234,1000,-124,1000,-252,687,754,392,-1000,-410,-39,-423,-452,-478,703,-804,338,1000,-345,419,1000,-121,113,-1000,181,-533,170,-668,-257,-1000,570,1000,-554,-353,1000,302,735,-387,1000,1000,-234,-861,-12,1000,647,53,-518,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:Mjg5MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{1000,464,289,418,36,-360,-111,212,224,-435,-113,403,-601,-34,-81,-145,477,511,269,433,-679,-369,263,-1000,-1000,626,-28,-915,-101,-325,-341,-545,449,631,-579,28,948,-745,-561,-545,1000,583,313,403,-89,-978,592,1000,301,619,560,3,138,238,468,1000,353,-1000,-617,-2,147,-676,-556,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{358,-1000,1000,-42,-936,-287,942,906,261,908,120,-467,75,-11,416,694,-1000,-242,-880,94,-1000,149,-787,270,-730,560,94,-1000,-508,-323,-780,-589,335,-823,-12,149,342,10,58,-277,715,419,-232,-213,498,-162,490,354,675,1000,924,646,-842,52,-304,256,727,524,53,-703,-327,-44,164,555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{609,25,2,259,324,-959,486,405,565,-252,665,-443,-691,-422,1000,-21,601,-128,943,-1000,-499,-329,984,-798,-629,641,-772,-47,-97,-306,-1000,-718,638,863,-891,-757,924,-1000,485,-1,829,854,654,-189,218,-1000,554,-770,1000,-376,36,400,921,895,-463,468,138,-466,-369,-514,-689,-509,-969,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Double:NzIwMC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{660,-1000,720,-746,-321,-422,711,956,712,737,808,-573,157,-1000,422,1000,-432,-97,-457,-613,-1000,-289,-74,-1000,-99,1000,-121,-959,-529,35,-1000,-523,687,-537,-340,-437,353,-1000,395,505,630,883,-178,836,160,-562,197,-698,1000,400,286,554,-61,559,-617,330,-354,-823,-353,-1000,-731,-663,-312,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Double:LTcwMDkuOTk5OTk5OTk5OTk5", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{490,-1000,-701,982,41,-447,1000,523,972,-335,786,-1000,1000,-1000,612,74,-851,-91,-1000,-1000,564,996,-1000,1000,499,-1000,-972,383,-202,-505,-360,-635,421,-1000,-135,-751,-334,1000,107,429,668,-546,-522,-465,-59,1000,-701,-1000,418,-124,229,373,-480,843,-914,-1000,776,-32,1000,-215,-1000,1000,515,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4xMzYyMzk3ODIwMTYzNDg3Ng==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{815,-467,-186,361,734,-445,309,-62,402,212,231,-533,-14,-788,439,-771,583,-467,99,-776,796,653,48,858,-401,-672,-815,692,-987,-473,-132,-766,-111,-619,94,-744,466,304,-384,-268,241,-210,105,-915,-801,825,-55,-793,446,-650,339,-608,924,728,-993,-746,5,816,962,-496,-491,892,594,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-47,1000,-1000,-1000,34,674,837,-873,1000,-416,275,705,-651,-528,450,-145,-266,211,-1000,237,-260,-383,940,-174,151,765,244,-843,-599,457,-401,307,463,-1000,759,-207,1000,-715,-25,972,1000,1000,1000,-1000,-110,-137,429,-721,430,-134,544,-958,1000,-1000,277,1000,96,14,-226,1000,122,-856,166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-47,813,-367,-906,-225,-71,527,-1000,-1000,-267,301,1000,1000,-664,41,10,-12,450,-981,-457,-146,30,940,-8,573,765,1000,-980,-758,751,-1000,611,-76,-1000,270,-148,720,-715,1000,1000,1000,581,1000,-1000,-510,-463,478,-1000,424,-534,928,-931,-545,-74,97,1000,-313,14,-138,693,-216,-304,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-848,-13,910,-1000,508,-749,-547,651,112,-676,375,-419,-1000,-223,634,-198,16,466,226,-925,1000,-109,-141,888,-193,822,175,57,-308,1000,-149,-391,-1000,1000,52,-346,-123,329,781,-7,1000,542,-421,-814,-277,386,1000,314,275,-346,-1000,-438,927,-681,-466,1000,-587,259,-102,1000,693,335,-366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{-301,32,662,-735,-197,-48,729,-627,769,425,575,-618,526,474,-512,-177,334,-6,-300,-928,658,-207,-827,-861,733,867,478,725,-800,-609,134,-711,510,-62,73,140,-424,335,-451,509,-876,-839,-305,420,563,-551,233,-196,-152,-623,326,744,736,884,-488,997,-839,31,-231,14,-5,686,303,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{-987,-1000,-138,-98,243,47,930,-464,370,-814,-385,421,657,1000,-479,-177,256,-232,1000,-18,1000,-447,-400,178,-1000,528,-314,293,-599,29,48,-1000,459,-1000,-815,-261,-187,145,-978,356,415,-453,800,-124,-754,-164,665,-781,-835,670,-628,1000,-231,183,487,-92,-758,-349,91,-142,-5,-591,-633,119}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{892,-1000,-143,235,-87,241,1,-1000,275,-255,-337,1000,-243,575,-427,-953,82,-781,1000,324,-237,738,-620,622,-159,-201,-128,-1000,323,53,714,-897,-458,-643,-1000,-1000,-138,-398,-1000,794,919,-711,611,-316,-786,-707,1000,609,-462,984,-1000,-201,151,828,-338,-505,265,-860,74,-1000,547,-86,-534,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{997,-705,-96,754,-407,-889,-966,674,-485,-195,171,278,596,283,-706,-87,-130,-79,-851,-893,-128,-400,-594,-514,-823,949,-396,925,-809,-49,688,-417,-148,790,-78,-164,635,-854,958,-95,66,-982,-52,-584,804,-398,-293,99,283,728,-858,350,864,968,441,492,543,-399,-161,226,-675,366,-835,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,377,369,-375,494,1000,-86,-551,653,-336,-707,-1000,310,1000,722,683,-1000,907,1000,329,869,208,-341,145,-407,-29,-306,-219,-230,-398,33,-841,-50,-1000,-445,-361,54,-470,-51,235,827,905,508,-741,190,-887,-197,-796,-731,157,1000,306,-460,-44,26,-5,65,-135,497,-1000,1000,-1000,-13,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-669,502,453,-890,1000,1000,-626,387,-958,-713,1000,578,1000,699,284,462,-1000,1000,162,492,136,1000,-530,-435,-249,486,-637,696,-1000,-1000,704,-1000,-566,-1000,18,-1000,1000,-750,1000,-1000,555,1000,1000,-765,880,284,-731,-1000,-683,-1000,248,1000,-1000,799,-826,814,510,-297,1000,-968,92,-878,-869,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-1000,-191,336,-12,249,-687,827,-261,1000,1000,-1000,311,-1000,-675,992,1000,335,-1000,867,-886,-1000,-974,1000,-662,-1000,888,69,670,1000,1000,470,307,-172,-318,1000,-972,-365,599,1000,-371,-892,-1000,183,437,657,-107,1000,1000,-543,-858,-934,732,-1000,981,-804,1000,-957,542,260,1000,-730,1000,-261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-1000,-346,-125,-522,835,-1000,1000,102,1000,-262,-1000,757,178,304,1000,1000,1000,-1000,1000,-1000,-1000,-1000,1000,-890,-1000,1000,-388,838,1000,1000,1000,725,-216,281,1000,-943,-379,1000,1000,-425,-1000,-1000,-119,728,-300,-269,1000,450,-1000,-631,-1000,982,-1000,1000,-1000,1000,-1000,883,853,1000,-1000,1000,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-463,70,226,235,943,835,-1000,-506,-527,-203,-780,-9,-831,-218,1000,-1000,-1000,1000,-455,92,1000,2,967,-530,87,687,-811,10,-951,199,1000,1000,148,331,-320,481,-127,455,-736,-994,168,932,-343,-1000,684,-50,363,268,158,-48,125,-797,1000,162,807,-451,-599,-868,381,1000,638,-878,1000,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-548,-721,319,888,276,920,336,361,359,-202,-938,-873,289,-275,-84,-178,-301,-266,715,809,-339,825,265,-421,159,-873,625,414,218,680,-919,-692,-854,-276,-117,-798,980,278,-397,893,739,31,-365,-362,339,165,770,464,174,541,78,414,-759,915,-638,-385,442,-694,-633,882,229,465,604,-140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{996,251,394,783,729,79,-668,-801,717,369,532,-522,312,967,217,571,-716,237,343,169,622,632,236,479,983,758,-48,204,-899,654,-366,738,318,600,126,-454,53,721,932,-905,527,894,245,-678,-668,11,217,-402,-457,536,898,412,989,462,352,-394,-969,274,329,874,716,-661,590,-544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{574,236,462,-503,-572,-985,-436,-654,-373,64,726,-733,858,376,-869,452,329,-679,410,-969,-502,767,-953,-157,790,331,780,157,-254,-104,948,-791,935,121,669,-520,-397,-396,620,-40,-479,624,-211,302,786,-84,21,-282,-572,813,465,-230,-405,293,-953,-238,815,917,-952,-611,-617,-505,-493,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-599,148,-1000,-208,1000,-1000,-431,-121,197,-111,-393,-423,-1000,80,-765,-969,1000,-476,-146,844,548,247,1000,661,149,-432,555,-1000,-1000,-926,-316,-216,625,-277,1000,-108,-65,-661,-1000,-408,744,-812,-1000,519,210,759,386,804,-140,-836,-824,1000,920,602,92,549,-1000,-1000,578,-164,-466,-358,66}));
    }
}
