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
            new int[]{571,-979,48,-1,-531,-303,64,35,910,1000,1000,-273,-837,-711,-656,-699,-213,-1000,-315,-566,-373,525,1000,1000,-29,-75,-315,128,473,1000,292,81,1000,-279,-227,-416,-440,158,-668,-208,1000,622,-76,-29,-774,-791,-1000,-928,-939,-192,1000,-655,1000,-245,-316,1000,752,694,400,-475,-363,-723,18,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,546,-323,177,599,-1000,209,-1000,-207,1000,-849,-264,-937,-625,901,73,427,1000,666,837,1000,-720,1000,120,562,234,-444,1000,1000,-483,137,-215,1000,-1000,-798,-250,-929,-421,573,1000,671,-48,1000,-903,-830,-220,-384,-540,393,559,-1000,1000,-874,-914,-255,173,-1000,-242,-1000,922,-1000,-705,687,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{405,-608,194,-951,-144,437,-127,403,-515,-953,-585,-47,-3,799,-145,424,450,955,-372,-613,-604,490,27,-400,799,699,870,-582,609,-376,792,862,-839,133,662,-361,6,764,-206,-370,-805,165,888,188,590,382,897,-19,331,-916,-589,30,-763,331,-453,-895,-365,-488,834,712,864,659,-863,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{-53,-1000,951,182,-178,-621,708,672,-1000,163,-160,699,-457,1000,875,-235,1000,372,876,581,-600,28,645,378,1000,146,-564,641,1000,353,154,-294,46,1000,-659,388,718,1000,494,183,1000,1000,950,139,-900,-240,-122,-531,-778,662,-243,1000,679,318,-760,608,-493,1000,603,400,-201,-1000,1000,-246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{66,-1000,-846,27,439,-373,-992,-1000,-379,1000,-274,-795,-1000,-1000,-1000,-77,-81,-775,-978,-107,1000,-767,723,-240,-1000,46,-172,423,-1000,-1000,82,479,1000,-1000,301,-202,-1000,-1000,-121,267,867,-268,-827,-916,-1000,-806,-1000,-591,586,-1000,312,-1000,830,-1000,121,241,-103,-712,-1000,36,-1000,-590,1000,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "abs():org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-149,-714,-832,-291,-43,356,-535,-228,-766,-1000,232,295,-1000,-481,-49,487,753,-658,-555,-143,-553,891,42,230,1000,1000,-456,1000,-974,1000,1000,646,-471,652,1000,-47,-1000,599,195,-905,-1000,-443,-912,-466,717,-11,-1000,-96,1000,245,689,1,-964,-513,-511,-805,-310,-640,698,-92,-705,145,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{259,1000,-245,-621,914,633,700,1000,-766,3,200,1000,-687,-363,796,172,1000,-607,-502,1000,-1000,-428,424,-1000,-1000,4,-654,-1000,662,-1000,-1000,277,112,-966,-809,-490,-12,-793,-289,-199,-141,-1000,-510,-402,947,-905,-1000,-702,-921,-655,75,-450,791,-142,-859,-454,401,914,-996,367,-967,-823,-50,60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-766,1000,-179,-494,267,544,400,1000,-725,-1000,-622,415,-345,1000,-815,513,-704,-979,-843,740,-298,658,-1000,1000,-1000,-208,610,-1000,490,-552,-195,-1000,17,137,1000,-1000,115,-1000,719,1000,436,-1000,-500,-816,-116,-679,-1000,1000,-1000,-549,-250,-1000,223,-724,-1000,-1000,44,278,-279,-586,-1000,-62,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{743,-799,1000,233,559,-963,1000,-339,188,786,306,324,-603,854,-539,-849,-919,378,1000,-509,-951,182,-316,-53,-739,-1,485,225,-1000,688,231,-752,586,-209,-18,1000,-227,1000,-981,-506,697,923,-525,146,-1000,45,-1000,-1000,1000,246,-31,520,169,-631,1000,1000,313,185,1000,-600,1000,-443,-103,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,913,1000,-653,156,-406,603,-81,-1000,425,467,37,-33,1000,-202,291,-18,280,536,202,-1000,-1000,-575,-485,-443,-142,-329,-162,-380,265,55,-661,-629,-510,462,-293,39,-619,-1000,-528,166,-849,692,-545,13,-793,-352,-64,-687,241,-256,-853,340,-280,1000,-1000,626,1000,866,781,-517,-622,-654,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,231,-422,725,144,653,-103,-231,262,-684,-108,-946,-422,-89,851,413,1000,-1000,-318,-311,738,-1000,1000,-146,924,-1000,-10,-511,-550,-876,302,980,922,-633,123,115,1000,-1000,1000,-855,-1000,829,1000,843,-965,-1000,-1000,-983,1000,94,-166,98,-106,1000,-374,1000,-927,-361,-682,-963,-1000,-364,627,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{857,-400,-372,-35,349,692,-700,177,-863,643,-1000,1000,-695,1000,796,-346,-3,-1000,-931,413,-1000,85,389,-1000,176,571,469,62,-26,-781,-1000,277,1000,-1000,-1000,-867,1000,186,-328,88,-861,-41,-1000,174,606,495,-567,-641,-384,-1000,1000,-729,791,1000,-1000,-326,533,-121,-1000,1000,352,-1000,-192,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-331,-225,873,238,469,-621,-933,-1000,394,-569,293,-629,-431,1000,-445,455,37,-856,516,-407,698,523,-700,-466,-728,-549,-613,183,-547,-580,-1000,157,-1000,124,102,254,-1000,1000,-304,1000,-416,701,81,-458,-231,-529,-884,108,125,289,-568,-270,-375,-600,845,15,-679,1000,396,793,551,-1000,-191,301}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-841,-1000,-100,-473,424,-519,-1000,-257,-119,-1000,-1000,1000,353,990,223,247,-734,-1000,788,-377,487,-643,-591,742,714,-1000,-472,-131,-1000,-992,-1000,-1000,-645,1000,242,-1000,-1000,1000,-906,915,-399,161,-1000,1000,516,-180,-938,-1000,-327,1000,494,739,-813,-1000,766,-1000,697,-929,1000,-916,-1000,-1000,-1000,-831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-569,72,595,-36,279,-1000,-996,-627,263,1000,-637,-100,-598,576,-164,-801,-357,-223,390,-590,-1000,42,686,759,-444,-496,1000,-400,-1000,492,49,-1000,1000,1000,58,1000,-1000,1000,-157,-87,-170,1000,1000,762,-554,247,511,-863,35,-1000,-35,1000,1000,-4,-801,-1000,664,1000,-231,-135,1000,-216,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{778,-700,-990,-314,-367,1000,1000,-317,-233,-178,-401,501,668,-558,-322,-82,850,116,-1000,-274,-719,-371,148,-123,127,-792,-838,4,-486,-610,799,472,-400,12,-299,-920,661,-501,715,1000,413,135,-392,-21,98,805,-1000,-597,-215,147,119,-330,281,-300,400,-40,388,-231,683,-207,-650,799,66,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "add(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-475,-1000,1000,947,529,-359,-1000,-1000,-328,-610,1000,504,-1000,1000,785,-441,-722,-966,1000,-261,1000,172,-776,319,-423,-695,-237,4,-901,-1000,-897,-963,-1000,1000,646,-474,-1000,1000,269,1000,69,349,169,1000,460,567,-1000,-435,-913,1000,-224,-330,-96,-154,461,-1000,1000,611,571,-1000,-56,-898,-984,680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-679,384,-335,-53,412,924,-1000,-1000,938,-123,1000,601,-453,22,482,-1000,401,515,-6,525,365,-334,1000,-1000,1000,846,-160,-117,-505,-498,633,240,-459,924,-886,39,79,1000,32,-1000,-404,1000,57,455,283,1000,-301,-1000,-1000,-1000,90,528,229,954,1000,1000,-692,-1000,-535,-332,-1000,-133,315,416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-185,977,1000,-293,-353,-19,-857,-1000,-404,-198,87,-443,-219,-794,-498,-646,-542,-643,-225,952,92,1000,1000,-967,1000,-1000,199,971,1000,-1000,-1000,196,810,-1000,-451,208,-468,1000,-983,-120,-179,1000,732,221,99,772,1000,-988,-613,-707,1000,1000,212,-691,-1000,1000,289,-93,428,594,-168,-1000,398,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-1000,-815,-66,-653,-572,279,-1000,-525,-1000,861,338,-98,-234,-1000,417,-207,-766,372,-1000,561,-1000,-586,431,-853,-430,180,136,-1000,-489,-1000,14,1000,-182,590,-511,-722,-135,1000,592,-746,425,1000,-443,889,1000,-60,400,-933,-1000,-1000,400,-310,-372,544,67,1000,-292,-615,104,1000,-107,199,-299,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{515,648,97,1000,21,-938,194,708,462,-387,-655,-49,-1000,-691,-269,603,-1000,417,-609,158,-1000,-786,-707,-71,-1000,-746,955,346,-1000,970,-648,-720,-328,-1000,-963,805,-3,44,-938,634,-76,279,67,-200,-342,186,584,737,756,-186,552,-1000,-1000,-1000,389,819,384,-940,703,-503,879,910,-1000,-414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-1000,-207,738,-947,507,413,1000,-667,1000,421,-394,-862,-1000,499,-1000,702,216,1000,458,935,331,862,317,-448,108,1000,18,166,-710,767,580,-1000,498,-97,514,-174,-161,-231,572,131,-419,509,-913,241,-54,1000,-1000,772,-752,1000,-985,-664,102,285,1000,-148,-1000,-314,-32,-1000,110,1000,-764,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "compareTo(org.apache.commons.math3.fraction.Fraction):int",
            new int[]{-1000,-1000,-41,-410,464,337,543,-661,-1000,-1000,-1000,-528,-1000,606,-499,-1000,640,102,-1000,883,1000,-380,1000,13,-204,-1000,-456,1000,-270,-139,400,-1000,-590,366,485,-449,352,1000,417,255,167,437,-204,1000,-197,1000,90,-897,-450,-1000,5,-400,-223,709,-756,-400,981,-1000,-422,-806,-1000,-144,350,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-84,-397,-362,335,-371,-614,-681,152,-715,-348,818,-94,677,743,1000,-768,907,246,3,-208,-394,33,-993,105,720,393,402,-231,-1000,390,387,-484,-1000,-368,-214,1000,-348,-1000,652,-238,147,1000,344,-892,-660,143,-16,-41,-142,-1000,-628,-894,312,-181,-149,-25,-229,-951,-440,109,-79,-499,-142,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{511,-306,-623,1000,-219,85,-257,-1000,-508,171,522,890,-99,-614,-1000,813,426,221,-878,594,-519,-964,-530,-1000,-1000,714,1000,382,-1000,-22,-863,1000,179,-965,1000,396,1000,643,804,811,-704,501,-942,-554,981,-1000,235,-1000,-259,-150,824,-124,-286,-1000,483,249,-814,1000,-855,207,681,526,-1000,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{553,326,180,-55,-516,-623,342,396,-135,-660,223,-399,1000,712,-1000,380,508,-1000,138,-817,-1000,1000,-1000,-899,327,-656,218,779,-1000,385,-1000,1000,443,-670,66,-46,1000,541,-825,137,126,835,-765,-140,-805,-1000,-86,-1000,-711,-1000,-129,268,-608,-473,-942,1000,-134,476,630,259,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-338,-505,-763,696,368,668,704,-474,-439,-343,-878,-147,-395,225,-333,853,609,1000,-1000,1000,1000,-1000,-1000,-48,-942,349,979,1000,-1000,-981,-1000,975,513,-1000,1000,403,1000,1000,1000,368,441,-180,-1000,439,1000,-1000,57,-1000,461,91,816,-729,-1000,-782,1000,-391,-286,1000,-435,-391,1000,-45,-66,474}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-120,-794,-809,957,-479,867,719,-828,-1000,-420,-339,-59,-181,134,908,187,-193,-1000,1000,1000,-653,-1000,-1000,-367,-251,222,1000,-360,-257,-1000,-564,1000,-303,1000,485,-171,1000,-295,198,182,-508,-1000,211,1000,233,213,-1000,750,514,-84,-157,44,484,1000,-716,644,1000,-1000,-599,562,-1000,71,-409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,846,188,1000,-136,-1000,-983,-49,-297,-831,1000,123,474,676,469,972,722,-529,-891,715,699,776,-681,-1000,-1000,978,-589,1000,-502,-224,-107,263,-1000,693,1000,130,715,-149,9,-573,-1000,790,361,-1000,154,400,-752,-422,433,26,663,-21,420,-572,-4,-352,-1000,-325,-812,-218,1000,-451,-810,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-108,246,-1000,1000,113,569,-298,-1000,-287,600,289,725,-786,797,-377,-1000,762,-404,103,-841,-1000,288,850,1000,117,-107,-232,229,-142,-639,-1000,-20,-58,433,210,1000,-365,857,729,-1000,1000,1000,-667,1000,-562,-705,-535,-967,412,72,-47,1000,-1000,-627,-1000,1000,-1000,-259,-785,-462,-370,1000,299,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-699,382,332,-422,593,-958,969,-711,-80,351,-775,543,170,-702,769,63,-424,-675,-742,971,797,-353,14,-905,-784,68,93,-786,427,120,-479,-436,208,-801,449,-290,-604,513,-211,896,-955,143,464,-596,7,-749,859,99,-378,614,-738,-653,629,-2,126,-199,-426,670,-147,152,16,-703,-542,908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,-650,-379,-449,273,-488,559,375,165,293,-627,1000,-478,-928,800,395,448,-834,-368,-264,204,-534,-1000,-1000,72,-1000,-253,-35,472,-120,-1000,1000,-115,-68,153,1000,-1000,240,-1000,-19,34,324,619,-582,177,889,-1000,144,763,66,-21,1000,-700,-18,239,203,-365,967,427,436,-854,1000,-291,-530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-447,1000,-1000,498,743,-257,943,-880,-501,668,-31,-136,-106,637,1000,532,1000,-840,-104,-538,114,283,-236,218,-677,350,-23,-1000,205,-1000,-1000,-1000,317,-507,296,1000,-43,1000,854,-474,343,1000,1000,953,-343,-529,120,-132,1000,1000,159,1000,-379,-1000,-677,1000,-1000,839,-1000,-1000,1000,-130,187,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-562,981,-1000,131,754,430,-222,-618,-807,-57,78,969,-744,1000,-659,112,1000,-104,-750,-1000,-839,986,-229,1000,-957,547,1000,-1000,-1000,-60,-1000,165,-368,1000,-115,492,1000,1000,1000,-1000,808,333,-726,1000,-945,-920,334,-1000,-735,541,1000,709,-379,219,-1000,344,-549,-263,-1000,-555,-213,878,44,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-704,599,-907,187,115,-69,933,-1000,-981,603,20,922,-999,-9,766,63,810,-946,-497,-318,-48,341,536,-296,-687,-389,511,-852,647,200,-1000,-1000,297,-143,677,1000,-830,413,185,400,-276,1000,515,503,276,-1000,157,122,134,-359,-71,-449,194,-1000,-704,943,356,498,-1000,-76,1000,282,41,-280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "divide(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-1000,-393,91,-735,981,-1000,-58,26,427,-103,617,-1000,344,-842,-182,350,288,180,-811,-1000,19,-35,1000,821,-1000,612,316,-670,233,263,1000,511,682,-274,-536,-521,-1000,-144,-607,897,-985,-579,531,-585,-987,472,-865,1000,23,-143,-121,-1000,-18,-971,953,-536,-1000,285,400,-164,-493,-228,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{-274,-479,-313,1000,-431,529,804,668,-1000,-67,-330,612,-928,639,-1000,826,346,390,-650,-204,-625,-1000,-146,-137,111,683,199,-1000,775,-578,-669,915,-886,1000,933,840,-1000,-516,658,1000,22,1000,-314,1000,948,115,1000,-442,-672,-70,-660,-517,537,-716,-134,-357,171,-1000,447,-323,-570,-2,-132,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{1000,-1000,-472,-413,391,461,-577,-218,1000,300,-182,623,-378,400,1000,-906,-665,-683,949,320,-1000,682,-411,1000,-917,517,-663,-1000,595,-274,-275,565,1000,-558,-1000,-358,66,403,-635,398,-1000,-348,-1000,525,1000,-1000,-1000,-127,-1000,567,1000,-1000,780,-715,716,157,61,312,-1000,1000,-563,50,355,866}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{-1000,-479,107,163,-311,-347,-1000,-847,104,-33,978,1000,-928,-1000,-626,-547,362,-22,658,-667,1000,745,-273,-66,-507,683,-499,1000,853,68,-669,-1000,-618,-1000,315,63,-378,-318,-29,253,214,1000,-314,-1000,935,-169,-156,1000,789,23,-102,-859,313,-159,-633,-357,-1000,-1000,73,216,-532,-477,-971,574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{22,816,903,868,264,-528,-726,-1000,-149,-463,196,345,-1000,99,902,100,1000,-241,1000,482,59,-923,605,1000,1000,-465,-1000,122,432,1000,-475,-109,931,632,-142,1000,-156,-858,-462,1000,-764,1000,-562,627,607,-1000,-1000,400,-1000,949,1000,-919,1000,-1000,315,-1000,-272,-1000,-1000,1000,-1000,-1000,-1000,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{406,-336,569,1000,44,392,-396,-827,4,455,-227,354,-1000,1000,584,127,837,588,1000,-264,-1000,-727,246,1000,720,-110,-53,-1000,42,476,-1000,996,1000,-268,-181,680,111,-554,-8,400,-235,1000,-102,1000,400,-699,-577,-1000,-1000,345,1000,-400,1000,-788,86,-669,-525,-648,-1000,530,-400,-1000,-352,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{-707,-645,-24,507,-203,623,-547,1000,-608,538,669,913,1000,425,-1000,655,49,487,-1000,-644,-1000,469,-209,-1000,-783,679,1000,989,445,-294,-348,500,-1000,-1000,943,20,-797,-116,1000,496,271,-1000,183,-442,-970,1000,1000,-1000,1000,-121,-1000,-798,-1000,1000,-1000,-232,-1000,289,-446,42,1000,-382,1000,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMDAwMDAwMDAwNDY1NjYxMw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{219,-457,-626,1000,492,404,-396,629,4,-28,240,732,-740,639,-431,283,179,325,-650,81,-551,-666,12,-183,111,683,-53,-391,1000,476,-1000,915,-81,714,-181,683,-310,-376,477,775,-235,1000,-34,862,400,-50,267,-442,-1000,359,48,-530,1000,-466,71,-648,-358,-228,203,159,-576,69,-184,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "doubleValue():double",
            new int[]{285,-51,-24,1000,1000,-359,-699,-760,931,-147,528,301,-826,1000,1000,-1000,-170,-564,1000,1000,86,554,-1000,1000,-264,-479,854,-389,688,735,-153,173,1000,-1000,-932,952,-83,321,318,1000,-1000,1000,-1000,560,1000,-1000,-1000,-1000,1000,1000,1000,-68,671,-1000,-169,-885,-341,-78,-1000,1000,-1000,-1000,-728,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-736,-186,-650,19,621,484,396,-849,-526,-503,-694,-787,191,-791,240,998,-854,634,-745,398,122,108,-942,-529,563,931,533,-52,690,-938,499,200,217,-238,780,-339,402,772,588,-425,559,-923,480,-495,-257,600,-299,-442,333,-726,-408,484,487,-137,-83,-86,-958,-379,479,864,-672,-578,-828,352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-1000,601,-599,221,208,185,1000,-922,543,-223,-258,-1000,638,755,-1000,-373,-380,-791,57,-247,-978,-1000,-273,-1000,19,249,641,-466,1000,-307,-771,309,1000,-1000,216,-1000,-361,1000,-40,-391,-495,-836,-1000,1000,-1000,523,1,1000,1000,337,765,466,-395,247,244,1000,-1000,-465,1000,-33,1000,429,-1000,395}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{173,1000,101,708,-396,461,1000,-435,124,-1000,438,-21,443,1000,-400,-1000,19,-607,1000,-945,-642,-139,-13,-869,-125,-1,-751,-750,-689,-717,-257,-341,396,-271,-1000,-1000,846,732,1000,812,-797,-667,248,410,-725,409,29,1000,1000,1000,1000,314,210,130,-1000,400,-661,-178,705,-747,1000,566,-1000,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-815,-1000,-1000,638,402,249,-315,-85,-1000,741,-13,-342,-74,-1000,-132,999,669,629,306,1000,1000,-224,532,667,-620,-520,-421,-710,742,-201,388,-475,300,188,-1000,1000,-466,467,-560,-387,-5,723,36,-47,-358,-537,-1000,-10,-206,-658,-521,-521,-383,-340,-92,1,396,741,-1000,1000,-1000,-120,467,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{1000,159,-429,439,-784,214,345,8,478,-152,-37,-997,-75,-258,-600,-683,826,-1000,883,223,-460,-28,1000,-3,23,-627,-1000,-790,-169,-436,24,-652,-75,-51,-624,250,-613,31,-475,634,-806,-158,-557,1,-730,-1000,87,-889,257,94,606,-1000,415,-850,-214,-401,467,727,52,-282,-164,1000,378,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{1000,-1000,-852,-386,-391,80,-1000,1000,-219,1000,-1000,120,-123,-1000,574,1000,-1000,536,-130,-491,998,-545,-240,1000,-624,-347,257,-62,1000,311,-1000,1000,-1000,497,1000,1000,-1000,-1000,-1000,-549,1000,-125,-1000,-769,637,387,-1000,317,-1000,-642,-972,-166,99,375,597,-1000,1000,555,1000,1000,-970,91,-1000,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-516,-186,-326,-265,-1000,-345,140,-849,-526,-310,-844,-568,341,-751,612,999,-854,813,-705,679,494,395,-951,-361,777,965,334,464,795,-828,568,200,217,-238,780,-339,402,-280,549,-323,775,-961,531,-647,-50,796,1000,-1000,265,-1000,374,-201,308,-137,112,-149,-610,-313,479,658,-700,-435,-618,277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-32,1000,-933,775,-229,-343,578,-949,69,652,14,-41,7,-327,-132,587,18,35,229,-140,-230,-224,-137,-670,120,259,-114,-514,783,-1000,309,-235,446,-316,351,-537,173,840,400,-122,-262,-320,-610,-47,-480,210,-302,-10,533,-899,179,-99,-101,-902,-251,240,-971,-174,539,1000,-422,-169,-880,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Float:MTEuOQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{54,-1000,119,130,127,-377,1000,1000,67,-1000,1000,612,641,872,1000,-366,-555,-966,93,-800,960,774,503,-809,-304,497,552,-963,1000,101,526,-1000,-707,-102,499,-458,-594,911,261,363,528,131,388,1000,-1000,-1000,604,158,815,584,226,-364,-1000,573,-672,1000,523,118,194,321,-247,-1000,158,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-884,-60,-744,136,298,-143,590,-1000,-1000,937,-815,1000,799,403,496,1000,-1000,1000,975,-1000,1000,-952,-1000,-1000,379,1000,1000,-1000,-1000,420,1000,1000,962,-1000,-1000,-778,-1000,1000,1000,157,-365,-1000,1000,-1000,-743,1000,1000,1000,1000,1000,1000,-963,-213,-1000,-640,371,-1000,560,-543,-241,1000,-1000,40,-225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-146,56,335,-641,-726,-369,-859,710,1000,175,1000,-1000,-975,-727,-241,-229,1000,-688,-576,-249,-1000,294,480,54,-1000,-491,-1000,-65,796,-289,-223,-528,-1000,-363,153,94,158,-1000,-784,-116,833,888,-235,-176,-143,-1000,-660,-468,321,-414,-1000,1000,-509,1000,1000,-286,1000,85,-453,317,-1000,619,-263,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Float:Mi4xNDc0ODM2NUU5", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-729,171,-350,308,199,-569,353,376,1000,591,914,-1000,477,-433,-249,12,1000,-396,600,-870,-827,-727,460,-545,-478,-399,-1000,-145,-988,141,485,-913,-59,-1000,245,577,-61,-956,-165,201,-197,-642,40,-401,-545,-761,314,178,915,628,1000,-737,-286,199,-427,418,-54,-208,-499,-40,-996,-334,-715,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{1000,-945,721,37,487,-815,-1000,793,1000,381,537,-813,-483,-1000,-768,-338,1000,-19,1000,160,-444,144,574,1000,-39,-280,-377,918,-181,-929,-153,969,492,222,224,183,1000,-1000,-1000,-564,1000,-20,-499,-888,-331,708,1000,-1000,-699,-971,-1000,-272,-382,873,1000,-966,1000,-1000,1000,924,-1000,93,1000,-844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Float:LTE0OC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "floatValue():float",
            new int[]{-119,131,-148,311,-374,-549,-772,-371,674,335,-298,242,386,141,-181,530,1000,-603,447,-768,-121,-786,-701,-292,-147,145,-142,-1000,-304,70,561,-146,100,-352,-250,-18,-135,-306,232,126,249,162,564,-1000,-524,-478,416,640,32,535,78,915,-461,-82,-309,153,-4,563,-635,754,-1000,-334,-243,255}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{-1000,-113,-449,-977,-464,769,-471,-335,-191,-944,937,-985,258,77,-95,-625,-1000,-486,-231,-513,-1000,677,259,-165,-502,-1000,-46,1000,-1000,-1000,1000,-662,-670,132,-820,181,585,-1000,-571,-1000,-979,-800,809,-681,644,236,-811,153,-302,1000,-1000,474,-650,-743,-19,218,147,-545,809,960,-456,1000,998,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{308,1000,-427,913,-571,-339,-322,-487,156,379,1000,1000,-776,1000,183,641,1000,304,-1000,339,242,1000,-1000,-1000,-10,6,-751,-1000,1000,-849,111,-553,266,1000,-1000,-1000,846,-1000,920,407,-1000,-840,1000,-248,1000,1000,-1000,1000,-777,1000,-751,1000,824,-1000,456,1000,349,163,744,89,-1000,702,-26,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{-604,-754,36,-114,-411,343,-17,-904,353,-173,-680,-963,901,-180,-224,-465,-951,-811,956,-341,-522,-1000,304,569,-49,-931,-736,267,-784,-513,222,370,-385,-702,521,910,-861,1000,308,455,544,-62,-296,-36,1000,-388,670,-330,154,-115,-1000,-4,-1000,-65,344,-1000,-359,-373,-217,269,456,-889,712,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{486,1000,-1000,640,-53,-617,-848,-375,861,287,1000,-324,-1000,886,78,1000,1000,-247,-1000,1000,1000,1000,-943,-844,-87,1000,1000,-1000,1000,1000,-723,589,114,536,-1000,-1000,747,-1000,934,-394,-1000,-1000,1000,-1000,907,1000,-856,445,-409,600,-934,658,1000,-1000,125,1000,440,432,496,-104,-902,1000,1000,224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:Njk=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{39,-242,-910,-379,-697,130,-24,-23,-131,873,1000,522,-296,1000,-31,442,623,320,-789,1000,-582,791,-78,-140,1000,-287,858,-1000,-237,333,-380,737,-290,319,-225,-408,241,-778,952,1000,-670,182,401,-364,-301,749,-248,817,-305,-53,1000,619,339,-428,30,368,-119,-416,378,1000,-530,1000,648,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getDenominator():int",
            new int[]{-747,1000,1000,-418,367,-1000,372,-1000,107,83,735,484,-1000,1000,337,-792,980,-1000,-1000,-732,1000,-903,-653,-1000,-454,-1000,1000,643,781,-1000,-508,-926,910,-11,-1000,-1000,506,-1000,-1000,-428,718,-231,47,-1000,1000,756,-1000,544,-650,512,-556,416,-777,-476,-346,259,-896,-232,-670,-696,-1000,-437,-1000,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{653,1000,74,-17,344,-29,570,-1000,-1000,-225,-132,323,-586,125,279,-594,-271,-254,-96,-205,-979,303,712,828,-1000,-519,-255,-428,408,78,-44,-893,-676,-689,790,53,411,772,-806,334,-338,-462,74,-1000,-1000,181,-1000,528,-186,238,-1000,-923,-216,-214,-390,965,-551,-46,-610,-504,-598,-858,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{81,710,-19,-539,658,645,1000,704,646,323,-132,1000,-55,-143,-199,127,320,256,362,538,212,16,-305,1000,-461,-1000,-1000,399,-635,-795,212,238,294,-423,-17,-162,593,38,492,613,599,-208,-351,890,349,-931,1,-958,912,832,138,-604,-134,-385,635,579,110,791,-33,-2,-24,-892,-556,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{-1000,-148,-654,-113,-1000,-888,-632,-189,-24,206,963,1000,149,147,-303,137,14,-738,-576,1000,358,-996,-30,-1000,-818,-859,98,-944,575,220,17,-378,-421,-383,-548,-153,777,-261,-690,-1000,274,-329,799,856,858,-62,357,340,-1000,-191,-619,-403,-243,-1000,264,-505,311,-658,249,280,-714,-621,-172,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{-145,-984,9,-1000,344,845,570,542,909,81,-132,323,313,-610,235,-429,1000,236,538,904,-979,-43,-305,686,-1000,-1000,-764,1000,1000,1000,296,-46,533,682,790,-1000,300,-249,216,334,755,164,300,1000,1000,-931,1000,-1000,742,832,1000,95,76,-300,927,-908,1000,-841,70,527,-1,-858,-565,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{1000,1000,363,-228,804,103,1000,1000,975,-389,577,11,39,1000,-1000,1000,-381,304,453,468,1000,952,611,1000,430,-1000,-1000,-457,-1000,-1000,201,1000,-264,-964,-1000,351,1000,16,1000,1000,-358,-1000,-1000,1000,-303,352,-751,-1000,1000,1000,-59,-1000,-1000,-986,280,1000,-981,1000,-113,-1000,88,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{414,710,363,727,697,-81,1000,-129,-345,257,176,11,-948,835,-453,1000,-779,-231,-181,-388,212,-139,238,786,-345,-772,-783,-261,-1000,-1000,-213,324,-336,-874,-72,1000,915,90,275,622,251,-360,-351,352,-625,-216,1,-215,358,326,-988,-451,-156,-423,-384,579,-438,791,-135,-763,-47,-572,243,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{-330,-285,199,958,-697,-687,-352,912,-67,866,1000,1000,468,187,606,504,1000,736,-709,-257,257,-817,-846,-464,-886,992,-238,1000,-248,658,-316,849,-80,894,-1000,-979,684,587,-288,205,55,-125,-903,159,-1000,-393,-298,-1000,866,679,-532,869,-1000,694,1000,-101,-125,693,-519,1000,404,1000,363,220}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.FractionField", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getField():org.apache.commons.math3.fraction.FractionField",
            new int[]{235,487,-1000,-543,259,301,1000,1000,1000,1000,821,1000,-81,-313,725,424,1000,792,-37,1000,778,-903,-54,1000,-711,-1000,-889,-1000,-338,-519,899,-266,843,8,-184,-383,209,1000,-180,-387,1000,-659,454,1000,349,-941,-384,-1000,1000,1000,618,669,-726,-1000,933,324,1000,1000,677,315,683,-412,-1000,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{522,-125,709,787,883,85,-562,906,-1000,1000,1000,-1000,1000,-1000,-379,1000,-1000,1000,371,-211,478,-74,-1000,1000,1000,-533,1000,-52,-919,-690,948,767,536,263,585,371,1000,1000,-1000,1000,-877,-979,-1000,176,362,404,-839,168,-486,-383,-641,558,-1000,-415,1000,826,-4,-726,421,-1000,1000,109,1000,640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{345,-584,24,-267,-161,-310,675,1000,76,1000,-203,-117,1000,910,-519,-635,999,1000,564,223,374,62,-1000,1000,1000,851,1000,-39,-1000,-321,-1000,52,482,-37,513,-26,344,494,-640,814,46,-1000,-433,143,284,-198,980,-108,-454,870,-484,-703,-947,487,649,818,-1000,-105,1000,-1000,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{925,-280,464,-894,478,-383,-667,105,692,-1000,-595,-1000,126,-150,-409,-1000,912,-1000,-955,1000,377,1000,429,-866,-261,347,14,683,67,29,-111,-1000,-407,1000,231,-304,-1000,672,-685,-864,-21,-287,603,-1000,48,596,945,-268,1000,-1000,-591,-226,-939,-663,-378,479,-313,-1000,360,95,623,-86,-580,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTU=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{-128,-260,-5,-865,-587,-644,561,865,-320,1000,1000,261,602,871,-351,1000,1000,1000,1000,631,963,-340,-1000,943,433,-200,563,-484,-884,-835,-391,642,264,-49,-242,-294,843,564,-672,1000,-83,-333,-835,-224,112,-286,-1000,464,-893,1000,-807,1000,-173,620,1000,-204,-579,-179,1000,-1000,270,-1000,1000,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{-341,236,-470,9,-276,107,42,-302,1000,-443,-610,-87,-34,861,-156,-821,999,282,459,29,-25,540,1000,361,-271,345,-400,297,366,-56,-791,-650,746,-916,582,169,378,190,-453,-26,375,-251,-206,396,1000,253,728,34,146,779,-488,-581,-14,472,-797,-33,-882,-561,910,1000,-1000,-627,-20,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getNumerator():int",
            new int[]{648,841,15,588,542,-111,-862,463,-764,1000,1000,-1000,1000,898,-424,-1000,-1000,1000,991,262,658,570,297,1000,940,-1000,1000,-1000,-1000,-707,1000,1000,556,322,709,830,1000,1000,-1000,826,-53,-473,1000,1000,205,583,-834,147,-312,-1000,-1000,1000,-512,-793,1000,1000,928,-1000,36,-1000,453,-730,1000,-381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-864,1000,-344,4,1000,-681,-724,-1000,-541,-489,876,-499,217,45,1000,935,8,-163,-489,-1000,-743,-930,-1000,317,429,191,-271,552,838,268,-119,-634,225,1000,-245,-103,138,237,-1000,-543,-1000,788,1000,-39,-412,-422,479,741,358,897,604,413,1000,-701,-1000,725,197,-547,-1000,-29,947,-564,583,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{398,-28,437,-927,-559,-974,245,-35,639,-282,-365,203,-893,652,-562,-463,-688,-40,809,893,-447,-264,325,286,2,132,-122,753,-281,-801,-207,95,492,-988,-876,969,439,105,126,600,-164,764,554,221,-134,-623,-407,-727,-350,678,-983,292,218,475,453,-500,-44,256,520,-874,413,914,68,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{810,496,909,782,356,335,-11,-164,444,-642,-477,614,232,248,-762,394,-99,-52,954,-598,556,-371,-881,-389,922,506,897,889,-472,167,3,-637,521,-553,-957,532,-690,416,-890,-554,812,-423,-908,107,246,968,272,116,133,-426,581,568,-544,146,-812,-792,-241,364,-31,922,390,105,-161,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-753,-695,403,181,-806,-497,-902,350,521,-547,450,-153,103,-728,432,218,-814,-862,193,-109,819,-232,540,333,103,781,-376,-508,-258,-113,874,698,866,-706,698,862,-878,187,-427,-377,632,-323,-258,887,-426,-734,-60,116,852,-496,314,662,-384,273,-968,484,241,-60,-63,644,769,-546,41,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-919,439,259,565,938,-484,-928,233,-950,-204,767,-940,731,-183,520,930,-342,246,269,-661,-60,-3,-566,876,66,-354,-476,392,222,834,160,-749,899,723,-497,727,-243,110,-836,-591,-677,318,289,272,233,-404,-281,5,-535,464,845,98,288,-876,-870,252,504,920,-884,-288,-47,-377,194,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{-379,-1000,44,526,759,-811,588,-308,37,-355,146,1000,-1000,-311,-264,278,-1000,-313,-72,5,-610,-175,1000,-696,409,73,-546,161,-185,771,-10,-914,335,743,544,35,304,-1000,735,1000,1000,79,-699,-770,675,-361,-320,-673,374,-1000,9,-1000,29,-28,1000,1000,-873,1000,-807,-123,128,1000,74,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{248,396,-66,656,9,-321,-606,361,-904,-163,-94,-591,-369,23,-377,591,139,-273,321,-597,-446,-370,645,-708,-916,-870,405,-66,144,-711,-268,-400,771,-812,-643,-562,138,514,779,856,-881,-428,-322,-900,188,-164,590,574,376,-315,-474,517,485,-987,19,-804,326,-962,899,994,-673,-178,990,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{411,-243,-887,585,-162,-610,-702,-85,-75,651,-129,-770,1000,178,-825,353,-480,488,-341,16,-12,-494,-967,-238,-1000,184,537,1000,523,218,106,712,809,155,-974,-635,28,1000,838,-120,-1000,-331,117,-853,237,774,-515,604,669,212,328,578,132,-68,454,-1000,597,-302,1000,667,883,-435,430,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{248,-405,-359,722,-247,-317,547,-929,-377,-163,-94,573,-330,894,-377,696,24,-650,732,-739,290,-603,1000,-254,519,587,-501,698,144,39,-504,-1000,48,292,-76,890,138,-326,-879,-274,-190,1000,-660,-516,669,-1000,478,-794,496,-1000,-834,-425,485,-1000,1000,693,326,-962,-1000,-123,470,-178,615,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{1000,295,-1000,-321,108,-879,47,624,1000,-982,1000,-395,-780,32,461,-887,462,1000,539,1000,-1000,-12,-478,417,-705,630,-1000,-440,21,-1000,1000,391,1000,59,-344,-118,-210,-215,-445,965,-1000,1000,887,-1000,1000,1000,-110,1000,-373,-921,-353,-54,1000,-704,-109,-1000,-1000,-62,1000,1000,-737,-733,-198,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEwMA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "intValue():int",
            new int[]{469,-890,-1000,646,-42,-297,393,-83,-567,565,1000,-239,863,-75,-528,1000,-441,611,-409,477,238,-1000,314,-50,-302,434,515,-432,-15,160,-732,1000,794,-694,-804,-648,211,-882,678,965,-849,68,-524,-747,466,260,638,-94,1000,-151,454,913,826,-958,486,-1000,118,417,564,1000,1000,171,354,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{1000,-1000,-651,139,4,-477,1000,-955,-812,1,-372,-252,-1000,-465,299,942,4,-1000,50,1000,-186,-363,-150,640,682,-894,-291,-234,-785,445,-2,1000,-1000,-159,181,-201,1000,-1000,9,84,708,-523,-228,-1000,-762,-254,-684,31,-232,1000,-880,-1000,1000,-1000,493,-331,1000,-90,-1000,207,122,-101,1000,697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{1000,-963,-508,837,1000,-288,-401,-577,-61,-297,789,618,-140,957,-177,765,-1000,628,445,-82,419,113,-484,543,706,-796,-650,367,-546,-708,-357,606,437,-1000,-1000,672,137,977,-455,313,1000,82,191,-755,-1000,205,-918,264,495,-22,671,-452,-141,-682,-1000,676,1000,-451,877,82,731,123,885,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Long:MzU=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{687,-375,358,-350,-971,907,-848,-39,184,849,-145,-31,-774,-814,142,-685,-1000,619,-1000,237,-1000,423,-262,400,-170,570,755,242,1000,-533,550,820,1000,343,-504,578,113,41,-363,41,-682,1000,-92,-400,1000,-464,-899,-507,853,276,-400,896,-95,-400,-1000,165,-1000,957,-546,-340,-513,-212,400,333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{-167,-365,695,290,-771,243,57,-1000,-1000,-968,274,1000,-1000,-763,-1000,1000,-537,663,-662,957,-1000,-1000,1000,-1000,-1000,902,-823,-1000,-42,-421,1000,820,1000,-159,-546,-166,-755,-1000,-842,-1000,547,-523,1000,1000,-965,-295,794,-981,862,299,1000,-58,-95,1000,-743,1000,387,-1000,402,207,-1000,746,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{1000,-1000,38,736,-116,231,-566,355,-167,1000,-573,-252,-1000,-465,-301,-56,406,-520,-394,-398,-501,93,-165,478,322,-967,-168,-323,-96,675,352,1000,-1000,-178,-430,241,837,-1000,846,516,-1000,-930,687,-975,-228,-301,-715,-487,-232,950,-880,-1000,1000,-1000,520,-279,973,378,-907,-189,-138,163,1000,697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "longValue():long",
            new int[]{630,1000,59,1000,-262,137,-1000,454,-383,553,847,518,573,-44,-218,424,-284,1000,-1000,748,924,-51,510,-1000,186,789,-769,-634,865,-73,295,-1000,1000,282,1000,371,-718,589,304,-860,345,25,293,981,-373,-265,-525,-444,301,-1000,272,1000,116,732,-1000,177,-388,-84,1000,-1000,-910,116,-472,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{864,202,-486,598,-1000,1000,-31,421,693,1000,-273,1000,-1000,54,565,-25,359,-1000,1000,-260,-99,673,-1000,473,1000,436,-1000,648,1000,1000,1000,1000,1000,1000,163,-233,296,712,1000,1000,1000,902,-796,1000,1000,1000,-1000,188,-1000,-344,470,-323,-611,800,1000,538,-616,-559,-489,442,699,1000,-1000,-958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{369,407,669,319,400,363,-97,755,142,-244,594,952,1000,-1000,874,1000,629,-1000,-467,-135,1000,1000,47,-960,-775,-1000,1000,302,-1000,244,-655,277,1000,-1000,107,304,391,-692,560,-1000,1000,14,516,-752,345,-1000,-1000,-1000,-1000,209,697,1000,-1000,429,426,245,-1000,485,-118,342,1000,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{864,-100,-1000,306,173,865,-58,745,1000,-16,-989,617,-1000,71,593,-891,545,340,502,-634,-282,530,-1000,-163,1000,746,-1000,434,1000,409,1000,1000,578,1000,849,-529,236,-26,1000,1000,1000,224,-1000,552,1000,1000,-322,-314,-51,-470,78,384,-266,1000,569,93,-1000,-678,-856,80,-74,1000,-1000,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{305,-477,-1000,338,1000,305,-51,1000,474,-490,-168,645,-1000,-1000,329,-1000,437,971,327,-293,319,-539,-140,140,221,-132,-1000,-5,308,-14,1000,1000,-90,1000,863,375,1000,-774,1000,1000,-588,-462,-1000,623,-859,400,632,-49,264,-1000,-26,-235,-370,838,77,176,-1000,-958,-1000,-659,1000,808,-84,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-453,-156,807,723,-674,363,-97,-922,64,1000,1000,1000,-347,433,-70,-404,-150,1000,-467,-87,-927,295,191,-894,-1000,-1000,787,390,-1000,547,908,-663,876,-250,107,41,-303,1000,705,-172,-921,14,160,1000,-1000,386,-161,563,311,209,345,1000,-1000,-400,-1000,245,576,-631,-365,-251,124,-355,546,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-207,1000,954,-974,23,-273,-1000,-234,535,1000,-99,586,313,-1000,634,515,6,-519,905,-334,260,976,-1000,799,-398,72,638,-1000,1000,-34,-1000,-852,593,290,-1000,-641,-455,791,-305,-649,-842,1000,-1000,-90,-431,-1000,779,-24,-443,-1000,-1000,-1000,-1000,-813,-192,-683,677,563,-549,-1000,1000,506,233,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,457,1000,-435,230,462,374,-746,940,1000,359,-693,-286,19,828,223,-561,-281,89,124,-585,905,-591,1000,-537,-541,-239,-1000,1000,-609,17,-233,903,-527,-1000,-600,-176,941,-706,-84,72,115,-580,170,-225,-1000,1000,286,-691,-1000,-430,49,-102,-1000,-940,-771,439,-498,39,-61,1000,1000,707,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,-80,1000,59,-616,-268,-1000,-327,-533,-368,1000,82,422,-296,576,-1000,638,651,822,-1000,-315,-813,579,-787,503,-1000,1000,-1000,-213,350,309,49,410,466,286,137,-1000,242,-795,-1000,-516,-275,-314,30,-578,814,-60,-1000,558,213,-509,-461,-180,-202,633,-420,174,-791,96,839,953,-876,279,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-119,987,-710,223,-1000,-1000,957,-671,132,3,83,185,-575,421,-1000,1000,-408,-135,-1000,-801,-547,1000,-198,490,-121,-333,-83,479,-612,373,-753,1000,-220,647,1000,-173,-888,-1000,1000,-355,15,-192,168,554,1000,-58,-195,-1000,442,-432,497,772,316,-356,-1000,378,354,275,643,1000,-243,639,318,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-689,-531,955,-180,673,459,1000,-71,856,-245,-153,-426,12,30,35,908,-75,-107,-326,-457,-1000,821,-742,-160,-24,-401,793,-684,1000,-290,-814,-800,1000,-1000,161,-778,-600,1000,214,762,-546,-1000,-279,228,-439,-1000,-237,1000,247,-87,-147,1000,-1000,620,-741,-640,174,841,-676,-701,288,-311,346,59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,175,1000,67,-611,-905,-554,1000,429,-721,1000,-533,616,-1000,-1000,-1000,973,216,-208,-1000,-882,-1000,-123,-1000,1000,-1000,1000,-1000,484,1000,356,-94,-342,877,1000,255,-1000,-523,1000,-1000,1000,-330,-904,-1000,814,981,-1000,-1000,665,1000,100,-166,-1000,1000,937,34,746,-480,1000,-1000,1000,-1000,1000,-259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "multiply(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-569,-188,765,622,294,498,378,-79,266,764,425,-956,315,609,62,1000,-23,436,7,-594,-410,226,982,-713,792,-873,1000,1000,412,406,108,-360,196,-801,215,1,-777,223,-1000,502,354,-869,-1000,-32,-60,-414,622,759,-1000,-309,100,-170,-909,-710,-800,-1000,180,-350,-907,337,804,479,114,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{46,400,782,370,234,801,362,457,478,917,503,330,-190,541,-922,158,-388,357,517,-53,294,-1000,819,-257,-99,919,724,-288,-259,-38,-869,245,-324,408,-187,-351,949,811,1000,-599,-274,-445,772,127,-357,627,-716,635,-631,-457,-262,-341,330,-799,216,-718,-846,470,-1000,715,-848,-1000,-81,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{353,-1000,702,-581,-957,951,-375,-401,-679,863,-1000,258,-459,557,-194,-838,-446,-567,-1000,-684,294,-276,-626,356,473,438,390,-471,155,-665,1000,1000,926,-261,296,145,-1000,-146,285,-382,647,-168,772,596,718,176,1000,867,-558,-779,533,332,1000,-100,464,-621,-1000,-827,-245,286,1000,28,131,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-95,845,-657,924,529,-191,-869,785,344,125,973,224,-843,-168,-51,962,-163,-426,-834,-94,-58,-208,758,3,-154,287,-309,-762,-858,437,498,-385,511,-711,-821,-162,-482,61,-594,583,-128,-864,-85,-255,571,-440,498,861,40,-745,-642,-992,903,684,775,941,-686,-450,984,-64,956,-744,-19,907}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-1000,1000,-1000,1000,-327,388,-1000,949,-541,887,277,381,-367,-531,-1000,1000,-727,-502,218,-960,-1000,-302,268,-603,-1000,-983,-557,-523,-836,-17,501,-334,199,148,204,-952,-384,-53,-1000,735,627,-140,1000,722,682,643,1000,493,-555,3,-473,-1000,1000,400,185,1000,-1000,462,1000,733,-778,853,-1000,762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{769,-400,16,-445,684,-633,797,3,191,252,45,-321,271,-500,309,11,-214,55,-138,599,17,-1000,-226,-598,-527,104,468,1000,433,526,-274,877,-164,194,-666,1000,-182,665,-521,-755,0,840,-17,649,-314,1000,-618,993,-347,-627,1000,657,-391,47,224,1000,542,1000,-1000,68,-778,131,429,-779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{353,-1000,702,-581,-358,402,-375,-401,-345,863,-1000,371,-759,622,-194,-838,-363,357,-133,-98,294,197,95,74,743,438,601,-652,-94,-426,630,863,717,-366,-799,452,-259,170,1000,-317,521,-671,772,306,659,-1000,955,772,-558,-957,612,332,1000,-495,895,-621,-943,-1000,-1000,779,1000,-144,131,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "negate():org.apache.commons.math3.fraction.Fraction",
            new int[]{-190,1000,-161,-173,249,95,-723,1000,-175,-210,44,-388,851,-1000,-38,324,-784,949,-232,-371,-1000,720,369,446,719,219,-615,-945,-287,-1000,200,255,1000,-94,615,-619,-1000,453,-838,-829,-581,96,-746,-825,324,-1000,520,1000,-922,-825,-1000,-1000,953,531,-1000,1000,-464,-451,666,-848,-173,1000,-149,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTEx", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{1000,1000,-158,1000,174,-719,1000,-232,-387,-417,-281,760,-749,-1000,51,-400,8,-46,1000,-818,-509,6,-456,308,-438,-1000,-1000,387,-1000,-429,1000,-167,1000,1000,46,-400,-22,-1000,1000,-630,-268,1000,-571,935,-1000,1000,320,258,-400,164,1000,-135,933,6,-542,724,7,-532,-688,1000,51,1000,-143,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:LTMxNzAuMA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{-560,-648,-317,934,364,860,-972,-302,-238,756,1000,1000,931,249,599,1000,-761,1000,711,1000,-232,-756,401,1000,830,739,-115,260,824,-276,-803,-120,-1000,-1000,932,1000,615,1000,-1000,437,461,-691,-1000,-899,923,-1000,-1000,-334,1000,1000,-206,143,367,1000,-334,1000,-586,1000,361,-1000,-279,-230,654,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{1000,411,-418,703,-601,57,38,559,24,-316,1000,-629,-71,-893,124,612,-557,-809,419,-821,-344,-157,864,235,-1000,-264,-1000,1000,-1000,440,1000,978,797,135,706,780,-1000,-95,172,-424,-1000,1000,-1000,332,-822,268,466,130,1000,-367,322,1000,-636,-416,-398,1000,765,-407,-1000,355,-1000,1000,1000,-31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Double:LTM3NTAuMA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{15,1000,-375,118,422,-935,-972,997,-1000,1000,-717,-919,-210,841,-986,-621,1000,-1000,644,1000,-160,465,-1000,1000,842,1000,-1000,-88,637,919,261,-1000,114,327,932,-915,1000,-1000,482,-406,714,304,278,-1000,576,-1000,1000,-334,-1000,-400,102,-1000,-61,-391,127,-278,552,430,-1000,962,870,-812,799,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEzMDAuMA==", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{263,209,-130,994,275,-304,-38,33,828,586,955,-1000,-479,402,-1000,-125,-142,366,297,-355,344,1000,-961,170,-1000,-1000,423,-679,120,-605,-313,866,835,111,-216,-61,-213,-1000,216,-339,10,204,-550,1000,-490,-748,400,-12,405,-460,-73,586,254,1000,482,417,1000,-1000,-727,1000,1000,-400,799,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTEx", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{509,1000,-535,1000,219,-1000,588,-916,589,-429,517,-691,-729,767,-892,-409,31,-66,285,-1000,-48,-5,-1000,235,-892,-1000,-44,-228,-800,-483,83,956,1000,1000,-789,-209,-591,-1000,1000,-13,123,326,-100,47,-889,517,524,-344,122,-700,837,77,128,-521,-80,610,72,-708,-846,1000,336,400,912,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{-891,-53,275,991,-555,-476,-812,-258,170,144,846,-523,-471,767,-678,-623,225,666,-329,-79,212,691,-875,-840,363,338,566,-803,600,-602,-552,373,120,677,-737,-478,124,-839,386,246,442,-322,1000,-80,-889,-883,1000,-312,-298,-114,-250,409,-827,158,1000,-400,686,-1000,336,808,1000,-1000,391,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTEx", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{250,-400,583,1000,-796,-361,-972,-118,-527,-547,353,1000,-569,-1000,961,-485,-761,1000,386,-230,-232,6,173,1000,-984,-437,-389,945,-746,-1000,993,-167,699,237,830,-249,-22,-433,27,-575,-636,840,518,-899,-1000,-400,320,1000,1000,809,664,143,464,-498,115,252,-586,-1000,361,-1000,389,515,-362,-171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "percentageValue():double",
            new int[]{1000,-415,570,690,-37,-215,935,-82,-718,979,846,-523,792,257,-292,-61,225,587,1000,448,493,820,118,1000,-737,-293,-1000,-368,239,-335,-1000,373,1000,-1000,1000,-473,454,-624,386,-138,-50,1000,-763,725,2,561,-37,126,-87,244,832,381,588,1000,182,963,1000,-533,945,663,995,587,1000,-40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{-805,206,699,-542,744,-78,-976,684,1000,-818,114,33,194,-368,-123,-475,-148,-424,179,-789,360,-481,-806,472,269,67,881,208,-168,1000,-355,836,161,268,-194,484,529,117,433,415,470,-280,46,285,-782,-788,597,-175,47,528,706,891,-139,-81,949,861,-399,-909,664,-313,-422,-355,198,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{965,-1000,84,679,-767,640,-778,-347,691,93,-644,-410,-446,538,406,554,-282,661,-501,-239,-497,-430,941,736,1000,831,-234,-1000,-122,105,-1000,112,-389,266,316,148,-74,455,718,316,-110,1000,-448,-328,955,1000,-710,607,-1000,706,-22,-1000,999,-1000,-399,-126,1000,218,-225,-162,187,661,574,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{749,-687,137,-696,-1000,61,-646,-300,-630,164,-175,-1000,611,425,483,2,163,962,-473,-237,-465,-199,185,-617,200,-77,-315,-146,-511,134,-847,280,-492,-321,-73,-240,-828,622,916,78,280,904,221,550,-69,-769,-355,524,-195,761,605,319,240,-1000,-531,313,250,634,234,-120,-802,255,152,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{55,-279,553,976,-321,-1000,-907,-310,96,498,-608,-573,-473,484,-521,562,516,481,906,-956,-205,663,-660,1000,-113,-158,-388,-484,-8,-657,-709,168,-872,-486,-1000,661,-1000,-958,661,163,944,117,1000,593,-934,-362,643,800,24,584,459,-1000,915,-227,-1000,1000,118,-53,-953,-166,1000,1000,201,4}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{72,-1000,463,763,-862,-535,-1000,-150,-501,1000,-1000,-353,85,254,424,1000,1000,1000,977,-1000,-1000,882,-440,1000,-246,1000,-822,-1000,219,-1000,-1000,367,-1000,-1000,-1000,427,-1000,-1000,870,205,167,1000,1000,1000,-290,-1000,-98,1000,-1000,584,261,-1000,802,-890,-1000,1000,1000,-219,-1000,-380,1000,1000,1000,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{692,1000,-704,-545,471,315,-917,251,1000,-1000,1000,-689,-69,1000,-1000,-1000,-1000,-1000,-1000,-1000,1000,-1000,-294,-996,544,-1000,-204,-1000,148,1000,-316,-973,1000,1000,1000,1000,1000,1000,657,-716,1000,-1000,-752,-1000,148,650,863,-1000,1000,418,-652,1000,-1000,-389,-1000,969,-914,170,1000,514,-829,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "reciprocal():org.apache.commons.math3.fraction.Fraction",
            new int[]{-427,-543,314,-542,-407,-78,-322,263,-583,1000,-718,539,202,892,-633,1000,1000,1000,1000,-977,-1000,789,-565,1000,-738,323,-1000,-1000,-591,-1000,-1000,179,161,-1000,-789,1000,-1000,-1000,123,-21,485,-589,1000,909,-477,-790,-51,1000,-675,-1000,-643,-1000,769,-81,-781,1000,345,-326,-1000,474,1000,1000,1000,516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-882,-1000,-255,-629,204,1000,-93,-143,-110,-30,-1000,569,61,-1000,704,953,633,157,-803,-658,214,807,-206,88,812,295,-1000,394,-205,-839,-1000,-1000,-590,560,2,-609,-738,-255,-187,259,16,-479,-597,1000,81,-7,139,-1000,-56,-68,986,-355,707,671,194,-622,-1000,-1000,-119,708,-1000,655,723,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-39,-267,-1000,-375,907,913,-321,117,566,-394,-792,-373,-93,-965,704,97,792,591,865,-366,-135,1000,550,460,1000,-52,60,-1000,-320,-839,265,-1000,15,117,-81,-659,51,578,-390,-150,-486,-263,-395,1000,807,-304,142,-1000,1000,1000,332,759,851,32,474,-843,-295,-726,100,741,130,904,1000,-938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{216,457,-760,-303,1000,1000,-3,-129,1000,167,-162,253,46,-513,254,-80,10,-15,873,97,320,658,167,96,709,-1000,574,-264,332,-709,385,-255,40,144,-1000,-903,9,836,-851,-1000,-457,-76,-154,856,-688,-1000,703,-807,1000,1000,823,1000,1000,-292,1000,-881,-244,-973,487,-276,419,570,421,-948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{-3,20,-255,675,-731,351,-93,-41,-913,-820,-676,457,788,165,844,953,633,219,-237,329,-816,878,278,88,-415,957,133,394,-572,-680,-773,-858,-498,-474,773,910,262,343,225,259,-921,-288,-344,-127,501,575,-499,241,-383,-823,-318,220,-616,-35,-1,685,290,-24,-636,708,-902,-241,984,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(int):org.apache.commons.math3.fraction.Fraction",
            new int[]{713,1000,671,-227,504,773,-647,833,770,80,86,-715,-256,39,1000,149,986,-286,216,1000,-305,263,982,-134,-842,1000,1000,-1000,-643,870,-1000,-844,-75,64,-825,-307,-717,-604,-133,-877,28,788,-507,-916,-725,-517,-407,1000,-1000,-1000,-368,-556,851,550,25,-904,558,-596,1000,519,100,-1000,-745,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{666,376,-1000,1000,339,591,1000,-240,141,601,-121,755,-1000,993,-291,334,1000,-1000,379,293,661,-20,790,762,-575,604,-809,1000,373,926,634,-543,760,19,1000,1000,452,-363,609,138,83,-296,1000,225,-462,-1000,1000,-58,1000,649,1000,606,-1000,902,785,-297,-110,-1000,157,-1000,-294,1000,-979,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{477,-460,-1000,-737,884,-743,30,-1000,649,-980,-116,917,48,1000,-752,1000,-463,-1000,1000,-894,142,-664,0,226,-912,-194,367,1000,695,-201,461,-1,809,-1000,-439,-541,309,-1000,1000,-1000,258,472,1000,-1000,396,-576,858,-827,-26,1000,1000,305,757,136,549,1000,1000,-1000,-148,-1000,-725,1000,-187,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{180,-818,-801,141,1000,1000,879,-1000,738,-347,2,625,-395,1000,968,408,573,-762,722,-422,1000,492,-1000,-269,-346,-175,582,456,686,-111,1000,-730,869,-710,-430,819,-1000,368,869,-642,585,1000,1000,1000,1000,1000,-1000,-234,-13,-17,498,851,-185,-410,-4,42,-624,-1000,-606,-1000,-472,-547,220,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-478,-589,-57,479,-126,477,709,1000,864,-863,-735,355,-335,352,1000,-866,561,244,377,432,287,-86,-700,16,903,-242,708,-224,264,-510,-17,529,756,400,568,538,84,214,-1000,400,-462,841,668,400,-55,435,-267,1000,412,106,-643,-632,-437,-659,-172,306,-1000,319,366,-568,54,361,219,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{-152,440,-673,902,-152,680,1000,274,542,-493,-511,85,-992,1000,868,-482,962,-1000,178,814,74,911,-796,1000,-1000,184,-64,-525,400,271,232,-195,-400,1000,-70,1000,-822,1000,-725,212,385,-377,1000,804,445,-1000,-603,547,567,-634,-202,-33,-731,-133,218,84,-998,-971,-240,-9,107,-55,303,-59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{1000,528,-1000,-672,1000,-201,49,107,-1000,272,500,1000,120,1000,-1000,592,-244,-1000,141,-1000,1000,-1000,1000,474,1000,367,-968,1000,634,949,828,-627,821,-1000,1000,-95,1000,-1000,657,-1000,502,-217,1000,-791,-791,-1000,1000,-376,727,1000,725,119,99,883,439,402,-28,-1000,723,-1000,-1000,1000,-1000,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{437,-570,154,831,358,383,792,268,-939,849,554,565,155,-407,-538,66,113,-261,103,-496,706,-750,797,-296,427,92,-290,879,172,379,280,-282,997,19,755,756,566,-676,417,690,111,-133,75,218,-870,-563,822,-227,869,393,699,-948,-304,930,-67,-327,-112,-191,-298,-555,56,970,-996,-210}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{211,-59,-1000,637,377,383,1000,41,403,66,-928,-197,-246,-407,1000,-763,250,975,511,80,718,594,-1000,1000,352,-755,450,734,892,1000,1000,-924,-311,1000,-216,-124,-833,-676,-755,124,1000,343,75,897,-212,-859,-1000,1000,312,-595,868,619,-1000,-5,255,-327,-1000,522,236,-432,352,49,-66,634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.fraction.Fraction", "org.apache.commons.math3.fraction.Fraction", "subtract(org.apache.commons.math3.fraction.Fraction):org.apache.commons.math3.fraction.Fraction",
            new int[]{470,-912,-864,636,-126,644,-442,-237,30,-650,417,28,202,-131,-49,136,67,-294,-898,721,109,-135,-786,-627,-522,389,-534,-61,491,-705,837,954,487,540,790,-651,-421,658,-335,770,997,495,-231,718,-804,54,-440,-449,-362,921,-23,911,-701,-93,0,-452,306,-343,-232,697,843,760,278,-802}));
    }
}
