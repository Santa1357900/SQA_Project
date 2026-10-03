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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{300,-21,-975,311,1000,44,-1000,1000,-669,-221,115,-746,-86,-605,629,300,-256,665,445,177,-883,-621,1000,-239,-371,-525,-264,905,158,-673,-450,330,432,-768,-1000,-73,-99,309,1000,954,-425,701,1000,192,145,819,-630,-777,-15,638,-576,612,-506,187,399,204,-1000,819,-1000,1000,789,-220,1000,108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{68,-365,-374,169,635,-291,-845,642,285,149,-204,196,640,-271,250,944,265,714,424,894,-936,-540,-48,568,-973,169,-108,264,-680,204,-692,339,-134,-875,-693,-501,-401,-407,757,-9,-625,632,404,-110,-815,426,-621,-832,731,740,216,151,-292,356,-612,122,-786,516,115,335,551,-774,744,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{845,-808,-1000,139,571,-425,-842,-782,-637,-754,126,287,151,-386,-786,-1000,-46,-229,-876,1000,-140,-370,265,-418,-89,-1000,1000,258,380,0,109,-180,-454,1000,281,-1000,575,1000,698,12,240,494,-565,388,307,257,-609,-907,13,534,1000,934,-939,1000,791,151,55,150,119,-746,-775,403,544,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-485,61,-145,495,-697,-306,596,515,980,196,1000,570,-78,-386,-1000,-503,198,-668,118,940,218,-311,-1000,340,459,622,1000,87,-846,1000,-245,-102,-60,590,415,760,-246,1000,441,-348,611,-900,792,509,-792,-18,601,720,-922,534,1000,-873,351,-323,-313,566,235,-1000,670,668,119,52,-484,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-800,-321,-103,104,-891,22,586,809,257,721,892,-281,-333,21,-457,-172,26,-972,533,-542,-626,-135,320,101,-213,24,722,489,-942,619,-253,-992,574,960,475,248,-412,888,729,381,914,-348,-196,294,-300,-244,760,998,-600,180,-60,662,-700,-367,-753,832,48,-904,-522,871,-494,-175,515,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,895,361,-742,-1000,-1000,1000,-1000,-1000,1000,144,1000,-1000,259,-532,-893,-1000,-497,1000,-1000,1000,845,-215,85,-114,-1000,-1000,-971,519,-618,-1000,-383,-97,1000,871,1000,-1000,655,528,406,998,-717,-810,-1000,853,170,1000,1000,88,-301,985,-1000,1000,693,-495,-1000,1000,-763,1000,-791,997,-1000,354,-625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-191,-74,-1000,-71,452,1000,-1000,-18,-78,-1000,-1000,-1000,-256,-54,1000,-607,-1000,-1000,-754,398,-658,-1000,-621,901,480,1000,236,-723,-201,71,-315,151,-318,-1000,-1000,285,717,-361,856,276,1000,-935,805,-709,-315,200,-548,1000,1000,-77,-656,1000,1000,-1000,6,-966,-275,872,729,-783,346,-386,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{1000,-1000,-99,148,-523,-341,397,873,325,-87,618,-579,462,297,902,-781,486,893,203,-437,-274,536,252,6,1000,-407,195,-875,-1000,-89,149,-622,887,-425,-262,712,-1000,549,895,-707,-56,-73,-283,-675,-339,460,1000,570,584,291,-424,48,317,859,-222,-266,-788,778,-122,165,-140,-162,-310,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-193,-571,52,-933,378,-796,246,-435,604,440,-382,-541,-37,678,223,-870,404,-30,702,206,-298,662,-680,329,-421,-599,345,865,157,-826,-738,-179,1,99,923,290,-969,-704,688,521,-67,698,-171,-612,348,917,-948,450,-935,-605,-166,-939,743,-10,484,76,-750,872,295,558,-358,142,-996,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{1000,37,-1000,1000,733,1000,-1000,804,-598,-608,504,-608,-906,-796,144,-762,-1000,1000,-25,-431,-454,-1000,1000,-1000,-338,709,239,-819,-410,1000,-980,-339,853,-369,-960,1000,1000,1000,364,-707,816,-879,-780,-194,-1000,-120,-83,771,-327,-511,758,1000,-34,337,-936,-384,-892,1000,-477,-691,-1000,-758,1000,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{394,-428,-633,1000,1000,877,-735,-762,769,178,-299,-357,-953,-840,343,-670,-51,1000,1000,-260,-346,375,-451,-569,-1000,-768,1000,94,-127,-1000,-232,706,327,-391,-1000,739,864,809,852,1000,-120,332,-468,-1000,952,1000,-909,621,-893,-1000,1000,-724,1000,1000,-402,569,-782,1000,582,-495,-334,-1000,504,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{394,-428,-1000,525,176,635,650,-841,744,460,-457,534,-953,-59,-432,-901,344,1000,1000,-158,-621,1000,-1000,-669,-152,-1000,950,926,-127,-664,-232,1000,512,-391,400,325,-69,-160,708,1000,-369,1000,-462,-461,1000,1000,-518,816,-1000,-306,1000,-1000,497,1000,-849,1000,-717,1000,1000,-162,-349,-1000,-544,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "add(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{1000,-1000,-599,-538,1000,749,438,27,66,404,-167,-579,-520,-167,-848,-1000,-1000,848,400,-710,-844,1000,-752,1000,-510,-26,781,698,-344,539,-835,-591,716,354,1000,736,-1000,217,-72,728,388,400,-399,665,311,-4,-1000,542,-892,541,742,-59,-481,557,-414,-507,-634,720,-218,-586,-548,448,225,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-1000,641,986,-653,-252,-267,-1000,314,-243,-408,308,667,310,-4,-100,411,-780,150,-1000,-1000,1000,-1000,-1000,-241,407,1000,-1000,-815,-1000,-148,-1000,-492,-93,994,628,-146,1000,-1000,1000,-367,-1000,1000,-48,-1000,-61,1000,-1000,313,89,1000,1000,-401,-770,-36,-394,789,54,1000,907,-990,-1000,966,-1000,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-179,528,114,-463,186,365,-1000,-159,-213,-346,106,162,987,301,234,983,-300,258,-1000,-1000,711,-1000,-1000,0,692,1000,-1000,141,-601,896,-531,-162,7,461,219,257,24,-1000,1000,392,-873,1000,-570,-519,103,1000,-924,-257,1000,1000,1000,-154,200,371,-32,721,54,1000,426,-273,-560,742,-780,409}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-393,-293,-1000,-1000,-1000,600,336,253,669,-1000,-965,1,394,-766,-1000,82,3,846,-1000,531,-1000,-1000,-2,1000,263,-869,1000,-1000,589,282,833,31,1000,-1000,1000,190,496,-571,1000,-697,415,-317,223,-379,1000,-1000,1000,-1000,-39,-1000,1000,603,69,-16,-491,1000,1000,-536,-922,-1000,334,1000,216,-849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-170,330,-52,-252,907,-597,500,-886,150,779,-61,-16,300,645,706,632,34,-413,447,53,768,434,-344,-356,540,11,242,475,1000,-646,-92,342,-371,373,-183,158,-1000,288,348,43,-1000,490,-667,982,263,422,286,260,-439,91,-609,-1000,-277,-122,744,-462,-1000,-353,371,828,-588,330,-47,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{434,878,-664,-592,496,-852,-311,-491,-642,-91,108,-296,953,-277,228,670,-134,-121,-1000,495,-188,-794,-195,-51,440,430,131,1000,523,80,920,1000,-266,109,-412,-930,-232,99,-146,-519,476,-705,-417,-63,778,126,668,-38,438,-47,894,1000,-847,271,-71,-32,732,-319,424,730,-15,374,213,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{58,-679,1000,-358,-153,339,-216,582,974,1000,-820,-29,642,-720,1000,-1000,-36,-789,-243,676,-1000,1000,-52,-176,454,370,-284,-483,-527,135,-1000,818,1000,-101,562,-599,-220,767,33,-1000,98,-404,1000,649,1000,-592,-1000,-354,604,599,-167,-1000,-1000,-1000,-179,-69,347,-292,53,-451,-643,-201,436,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-274,-632,1000,81,-646,-1000,-536,803,249,-224,72,-81,291,-397,-468,49,-885,-1000,-242,538,-127,-373,276,579,-597,-1000,-585,-426,94,-552,-561,433,-372,-1000,-372,-471,523,142,-162,-410,-1000,-745,21,-226,936,-7,79,-47,707,-86,-465,389,210,540,-961,-66,-202,-736,721,-771,-508,209,77,335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-1000,-816,1000,-626,-583,127,-1000,-359,-42,-376,-232,790,-199,-171,-96,-737,-326,-221,611,688,-621,-631,271,627,-792,575,-512,-501,-72,-949,-239,237,42,720,-1000,-349,-313,664,-609,-1000,-634,431,-918,214,310,-834,-433,1000,698,499,291,837,253,801,-418,-1000,-860,75,1000,-806,-1000,1000,537,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-1000,-1000,1000,-392,-542,31,266,-597,45,-698,6,284,-166,-420,222,-481,-88,897,125,341,622,335,-372,-692,-428,703,-658,-173,-515,-964,204,-90,232,838,-620,-606,-519,765,-88,-1000,-961,-608,259,290,-557,-884,-603,-765,543,649,635,820,1000,753,-609,263,268,-356,-190,-358,-1000,-195,571,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-946,509,-172,-1000,-250,-56,-1000,-389,-853,-1000,-935,-665,939,-365,-354,332,300,-315,899,-161,301,-465,-501,-791,-1000,805,-527,-632,749,-364,-523,56,1000,273,-1000,-561,-797,765,-1000,-681,-66,394,451,-763,444,-797,-1000,400,227,-52,748,815,-273,649,-861,-389,-819,532,1000,-1000,-1000,1000,200,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-1000,487,-95,309,1000,164,109,-912,-931,-1000,-635,-442,-272,-341,109,313,917,-981,1000,1000,-512,371,330,397,455,-448,-484,786,909,-973,168,400,1000,289,403,-900,-536,1000,472,56,-750,-243,-58,-1000,-43,-374,-58,575,-1000,582,-79,423,442,1000,-182,122,822,-1000,-184,-584,381,399,700,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-427,141,83,129,-306,1000,-978,-1000,550,959,-1000,1000,1000,-185,-1000,-687,1000,-61,-96,-254,1000,489,-385,937,-390,956,387,-1000,995,-1000,-1000,1000,1000,262,373,-1000,-602,1000,-1000,-207,-750,-987,-115,-397,-1000,1000,-1000,1000,814,-238,1000,-1000,1000,237,1000,618,733,1000,-547,-166,-1000,-506,-119,-308}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-100,134,-544,243,-472,-1000,-41,-563,-144,-370,14,498,-1000,1000,310,-948,-957,-1000,-490,-166,483,-184,-195,1000,-547,-624,79,680,266,1000,726,404,-1000,636,-1000,88,473,-596,-14,421,488,773,-598,-733,1000,-13,187,75,-448,615,-383,717,-547,219,118,-359,936,305,-382,-341,946,690,-668,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,-189,-1000,-447,-1000,-1000,577,-571,260,373,-284,736,-465,-697,1000,-822,-582,1000,-107,38,1000,-471,1000,149,31,853,-156,17,-322,1000,178,-1000,168,-1000,-1000,1000,-427,76,445,1000,1000,1000,587,-475,785,1000,-170,-1000,271,545,289,-921,-901,-666,-992,-1000,-1000,996,739,20,-481,1000,573,-758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-1000,661,-121,-806,-549,903,415,-1000,-108,173,-378,-580,977,-467,1000,1000,913,633,1000,-330,-707,929,-81,533,-228,1000,-62,1000,-752,1000,291,1000,1000,-448,759,-749,-1000,1000,951,-941,-1000,862,1000,-1000,-987,-13,390,1000,-996,-67,50,808,689,161,-574,-975,65,-1000,-190,-1000,-842,-142,-103,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,-1000,-968,674,-1000,-183,396,445,169,573,-591,634,384,-1000,645,-105,577,840,-382,1000,1000,351,1000,-65,-684,687,-1000,745,397,759,-181,-1000,983,-704,1000,577,-415,508,-199,1000,970,-700,-577,1000,285,1000,-685,-1000,1000,-103,1000,-1000,343,54,-875,-636,249,-576,167,-119,-994,257,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "append(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{616,-1000,-573,-53,-744,-690,-431,442,-531,-746,-638,-438,-597,-592,711,196,408,-1000,1000,347,527,-1000,792,-393,799,-1000,-1000,670,-42,166,-842,631,-1000,-132,1000,491,-508,-838,672,422,-765,-776,-201,-483,989,-875,-144,-714,-147,1000,-446,80,-970,-503,-1000,-141,32,289,1000,227,-377,93,220,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "copy():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{665,-184,1000,260,1000,-354,392,-691,-695,-883,-1000,459,-655,748,438,-486,1000,1000,468,-1000,-382,446,-50,-564,-1000,523,-1000,-597,582,-626,904,353,-1000,-667,-46,273,1000,964,-1000,-981,-890,-16,-1000,-1000,441,-734,-949,1000,92,1000,1000,643,-275,-587,543,-1000,-1000,-1000,-883,-541,-963,-336,-1000,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "copy():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{245,-926,1000,-1000,1000,-566,529,24,323,-312,-1000,886,-655,164,885,288,1000,1000,540,-980,221,1000,-887,-1000,-15,127,-1000,-704,-400,651,-1000,-1000,256,-237,1000,580,1000,273,-761,369,-1000,-1000,-619,-751,-1000,-1000,318,158,-325,-504,1000,-392,-675,-1000,1000,-955,-1000,-54,1000,-451,-476,-307,544,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "copy():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{467,-1000,1000,-400,637,-986,1000,797,-994,-421,-793,-71,-737,150,332,-1000,1000,714,737,-1000,-135,-275,-634,-1000,-1000,89,-986,-252,-742,-579,-291,648,-958,-153,970,168,430,-715,-357,-77,-609,-45,-904,-931,-480,-178,1000,1000,-400,1000,798,701,766,-1000,543,-1000,-1000,901,400,-758,-1000,-53,249,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "copy():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,732,-807,703,-114,-219,-515,387,-807,-511,176,-547,-868,-359,25,1000,-336,-325,419,-27,-720,-61,765,1000,-1000,388,443,-498,-511,-1000,220,-1000,-55,-284,-784,427,-325,-529,-762,-355,761,-57,-522,-737,900,323,554,11,-383,131,-441,-352,-650,-326,19,701,801,-935,-1000,-804,-216,1000,241,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "copy():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{354,-1000,665,-1000,1000,-1000,1000,748,-496,-421,-1000,99,-913,-553,712,-919,1000,633,341,-708,-311,993,-16,-1000,247,394,-1000,369,-1000,-416,-1000,-1000,306,-707,1000,757,1000,-419,-312,1000,-898,-1000,-648,-504,-1000,-214,701,798,-247,-60,1000,-886,-115,-1000,601,-1000,-1000,214,1000,-685,-882,58,1000,-681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{-1000,61,1000,-1000,204,-376,-927,-222,1000,-849,-554,-1000,137,-508,-91,-624,-1000,-864,-758,-332,1000,-693,65,1000,180,1000,550,180,-354,-953,894,722,859,-870,1000,-676,-362,750,456,-350,-1000,-942,487,-515,1000,-168,1000,-243,899,1000,869,-27,-1000,893,1000,224,-991,-1000,-127,-688,886,-283,1000,241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{-678,-255,1000,-258,543,905,-1000,1000,1000,-1000,-701,749,-156,-626,-419,-22,2,78,631,-677,650,-444,-121,902,-1000,687,512,445,-569,1000,-571,-71,813,-372,1000,-1000,-474,110,339,-170,-577,-441,-122,499,21,338,507,466,648,1000,745,242,-534,47,-418,-1000,-115,-359,-743,-930,-377,348,-843,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{-198,-222,650,-447,830,-225,-1000,1000,630,519,338,400,-790,382,-286,-881,-888,-5,-195,141,-1000,-143,564,177,423,-690,239,-480,-693,-762,-310,324,873,-157,224,-70,-68,-184,-165,-346,-306,-289,-924,-659,102,-769,143,111,-430,-400,-587,139,154,-467,1000,1000,91,180,-658,-343,-227,113,-400,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{177,-371,659,-1000,758,-426,-1000,618,809,793,191,1000,-398,734,-179,-1000,-1000,422,573,418,-433,-497,1000,1000,109,553,413,-816,-1000,-481,-14,929,1000,-574,1000,115,122,-365,-335,-817,-1000,-154,-1000,-188,-283,-985,-115,461,126,-1000,-216,-226,-197,-1000,-1000,-57,559,402,-511,-203,-695,123,-1000,-282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{336,-966,-45,579,516,959,305,215,214,-1000,12,807,717,-93,-1000,-352,1000,364,-978,-882,-1000,413,279,-686,-287,892,-1000,85,377,1000,-565,-815,229,687,656,-1000,-138,-292,153,1000,652,494,497,676,-1000,-641,-119,-1000,-1000,-1000,-565,-1000,1000,53,-621,-1000,577,1000,-281,265,-889,357,-151,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{42,347,748,348,-763,1000,370,1000,197,-271,143,887,929,-197,-351,470,527,443,-259,-665,466,146,-1000,-171,-44,1000,-367,676,377,1000,-1000,359,-104,-507,360,-857,-262,85,-136,75,-501,-516,1000,467,798,617,-98,-793,-496,-1000,692,-453,1000,212,1000,296,-529,84,418,-305,111,203,1000,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.RealVector):double",
            new int[]{613,-1000,-548,619,1000,579,281,-36,455,1000,-516,943,-1000,1000,228,-475,726,377,-1000,624,-90,520,1000,381,-1000,1000,-957,1000,-519,695,566,498,-142,720,-777,867,878,21,92,95,377,861,1000,1000,-420,227,711,577,186,-410,-371,706,123,-72,485,1000,30,-778,-702,780,283,-828,342,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.RealVector):double",
            new int[]{804,-1000,-179,823,928,334,-549,-1000,408,345,-300,-108,-1000,387,654,-908,965,-814,-1000,845,-486,-1000,480,429,-326,864,-108,130,255,969,1000,-68,836,1000,-845,100,811,-6,960,-56,-1000,551,248,-134,392,938,197,1000,115,-625,-1000,110,598,916,762,1000,184,-1000,-748,1000,-511,-1000,38,337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.RealVector):double",
            new int[]{506,602,54,-1000,493,322,534,-108,467,178,859,-225,-947,-832,-234,275,-1000,1000,581,-579,-1000,-466,1000,-295,25,1000,-217,923,-1000,794,-1000,1000,-527,1000,-321,-1000,683,-437,1000,1000,-1000,658,-729,-673,336,-1000,-1000,1000,468,-1000,-1000,-429,972,-550,1000,-267,660,59,152,456,-922,881,-942,634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.RealVector):double",
            new int[]{636,-933,615,856,373,504,54,662,1000,1000,-689,1000,-1000,894,-288,-305,22,33,1000,-221,-676,-663,1000,274,-1000,-184,1000,1000,-587,1000,-58,752,-913,-637,193,-845,-1000,-522,-50,-262,-589,1000,-1000,-229,1000,-1000,788,846,1000,143,-42,141,439,13,-417,605,190,-809,-869,803,-5,-894,412,854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "dotProduct(org.apache.commons.math3.linear.RealVector):double",
            new int[]{949,-486,-1000,-907,1000,695,407,-1000,860,-443,609,-1000,-1000,89,-267,-1000,3,1000,-1000,1000,-884,-1000,877,-533,355,1000,-1000,-645,-501,630,-25,-518,1000,1000,-1000,-15,1000,-1000,1000,1000,-651,-1000,1000,266,317,1000,130,1000,-519,-193,-1000,889,-738,-100,1000,-1000,-742,-871,-251,1000,-232,-1000,-687,49}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeDivide(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-400,-1000,1000,1000,-730,457,891,836,-120,841,-1000,679,3,-637,-98,1000,347,-281,-1000,560,330,-327,718,-1000,879,-197,-428,1000,619,-901,900,575,-521,-1000,770,1000,-80,-513,226,-1000,521,-1000,-963,-1000,345,117,-407,-744,1000,-58,-1000,-939,-1000,-75,-1000,985,165,-809,1000,-535,-29,858,626,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeDivide(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{48,102,904,17,-1000,720,925,-37,296,164,754,233,354,-1000,-248,562,1000,837,-370,195,-1000,-928,395,370,118,-324,-465,-330,192,-231,-119,451,-287,894,1000,347,1000,1000,977,340,1000,931,765,753,-1000,1000,-579,274,489,-1000,1000,-157,268,-1000,-22,312,-476,-920,-818,-58,1000,789,-301,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeDivide(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,-1000,1000,309,1000,-706,-449,-599,-235,-505,-52,403,-960,1000,-491,-521,-350,659,1000,-910,-1000,-1000,295,-178,1000,169,1000,691,183,-468,820,-1000,-1000,-728,1000,-696,1000,1000,-309,97,358,-1000,-446,1000,-1000,1000,-623,57,-636,-134,426,-156,947,-514,-171,-1000,-794,873,-576,496,1000,-1000,-737,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeDivide(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-877,937,-705,-431,-171,857,-267,-60,-722,595,831,383,621,597,762,-348,519,-343,991,491,624,178,108,412,-465,-509,-50,-294,-336,-594,452,-30,935,960,-612,-348,-371,-530,645,619,-160,404,15,-369,984,-654,629,703,-401,-619,-930,854,-722,595,16,462,984,-935,-219,588,-759,719,-453,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeDivide(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{659,100,-470,-512,588,-580,-204,344,77,-160,-111,664,-27,240,429,-800,849,851,837,-310,-618,-304,-909,-135,556,-997,729,229,-621,-990,829,-857,-722,-612,337,-37,750,627,-332,-495,972,-844,-206,931,-941,721,200,336,480,601,6,741,-309,594,-674,-2,513,-582,526,192,987,-865,-624,-481}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeDivide(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-400,-1000,1000,806,166,-709,541,-1000,-802,-764,-1000,-53,-882,324,52,542,288,-22,347,-310,-618,-1000,596,-119,1000,294,621,388,621,-391,483,-959,-1000,-1000,921,473,954,884,1000,-870,673,-82,263,120,-941,736,-597,-1000,-125,-1000,-720,-397,-47,-475,273,-1000,513,479,-1000,1000,1000,-609,-803,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,251,95,-329,-182,885,385,-282,-695,-1000,245,274,-995,331,543,-801,656,842,456,-304,511,138,-1000,275,76,-722,86,-428,-1000,423,1000,-797,-1000,-929,10,489,344,-766,-834,-326,429,-1000,-379,-1000,-1000,1000,1000,1000,496,-639,239,-31,-445,-133,-1000,-51,418,-791,384,756,398,-82,-287,-276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{420,919,476,482,647,-361,-1000,-174,-823,58,1000,-1000,-990,631,936,-369,65,-520,42,816,-290,-431,-54,492,410,518,-687,-481,-1000,-491,-316,-1000,-693,1000,428,-568,-867,-89,376,730,-159,-129,71,-619,-799,-438,-123,-1000,89,-81,-757,148,-1000,-103,347,1000,261,700,321,-649,1000,840,-330,107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{816,-1000,-271,-41,566,550,378,-514,168,-800,-176,-1000,105,-59,-912,-588,-271,446,-294,-1000,613,-262,-718,-1000,227,-309,-546,495,-1000,623,747,-67,-992,-876,288,1000,-788,-632,-1000,-310,1000,-880,-906,-1000,763,691,118,1000,925,-26,-802,-263,106,592,-338,-87,378,-146,-996,-104,32,-1000,1000,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{125,366,921,154,649,-671,-892,266,-673,-569,486,-872,-679,180,299,-886,711,-400,150,514,17,-886,-5,586,-148,-8,-824,-571,-555,-487,-690,-802,-489,985,65,-115,-735,-270,-222,728,-737,-725,207,-859,-830,92,727,-999,585,-544,-198,-907,-412,-749,640,599,-161,883,-371,-421,694,864,345,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{988,889,-1000,-1000,-591,1000,805,819,-112,159,1000,1000,-630,528,1000,1000,1000,1000,-54,268,-723,1000,612,761,-1000,1000,167,-50,1000,182,471,-700,1000,-1000,-1000,-1000,1000,-1000,139,-1000,-1000,-325,-55,1000,172,-760,1000,302,-1000,990,1000,1000,1000,-1000,-730,1000,-801,-1000,1000,1000,499,1000,-882,-913}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{648,-1000,-635,-994,365,578,966,246,731,-1,173,414,-232,-287,591,-981,159,743,-429,-576,-560,841,150,-530,-226,-678,-239,531,1000,593,1000,-123,790,-983,-891,122,12,-876,-674,-612,-494,-489,-779,326,-21,-91,1000,614,-946,484,-99,952,876,-708,227,158,731,-646,-44,488,2,171,566,-444}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "ebeMultiply(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{995,-601,-264,-237,617,1000,1000,894,265,-635,950,1000,474,669,-450,1000,1000,260,801,187,-674,1000,58,-1000,-567,898,-145,613,-732,1000,-931,-986,564,-1000,-1000,367,-510,-875,-1000,-1000,-645,-942,-604,903,-5,-373,505,642,-981,711,495,1000,926,-842,-201,-87,-106,-1000,750,1000,780,311,347,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,14,-186,414,237,-59,113,-1000,1000,1000,-857,1000,-314,-164,453,-609,452,-624,658,-1000,-587,-1000,230,878,335,-400,99,-396,1000,-1000,546,-451,-285,-1000,1000,198,-539,-368,-1000,905,-205,-448,1000,473,14,1000,-1000,1000,578,-530,718,1000,1000,302,-1000,-1000,725,449,-919,-400,340,1000,795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{-107,59,-715,-481,330,-1000,-318,-1000,891,657,-1000,722,447,872,-433,-401,-811,-856,986,111,1000,1000,-189,-642,-1000,1000,1000,-861,-600,-363,-425,737,1000,-1000,1000,-1000,-1000,-807,968,1000,1000,-265,537,-1000,-1000,-1000,-831,925,-1000,1000,-904,1000,-1000,-712,939,355,354,97,-1000,693,1000,1000,-1000,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{-915,-71,-128,910,70,-585,816,-235,-113,748,89,88,23,607,735,-286,-545,-750,-177,763,228,-362,-890,66,402,440,164,-691,-382,-560,221,-210,438,-824,933,-617,-928,-886,-271,-62,161,117,542,188,-145,-481,-728,-5,-397,748,-421,722,-217,77,326,-585,-413,864,-699,-454,-579,404,764,111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,-920,460,746,-589,18,-1000,-720,622,773,-471,578,586,-372,1000,-1000,-371,-956,-572,-1000,-1000,520,864,984,787,-418,-1000,-468,1000,-1000,471,-83,143,311,975,1000,-347,-644,-324,1000,-1000,-933,706,180,-191,1000,-1000,-91,198,-1000,1000,1000,64,624,-1000,75,1000,-20,-6,-465,-751,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{-1000,-1000,-1000,732,867,-741,302,-767,345,514,-228,663,540,507,1000,108,-520,-427,-70,-1000,-72,-431,-59,20,-218,1000,152,-56,1000,1000,1000,-1000,161,-1000,-924,674,643,-712,-1000,197,1000,-315,-141,544,-1000,-455,395,553,80,705,-1000,577,1000,-56,820,1000,1000,332,271,1000,-769,175,1000,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{539,432,-867,-607,-500,959,-4,-898,248,587,200,366,71,-1000,193,-265,662,-239,-1000,569,895,-714,74,-1000,142,-801,680,-665,-659,87,-780,1000,115,-154,458,-878,-136,1000,122,947,1000,485,820,103,1000,31,-1000,-1000,-787,522,1000,-151,458,-915,495,-423,410,694,1000,-216,1000,-658,-730,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{-1000,-300,-250,532,682,545,514,-809,1000,236,307,414,235,427,-348,-97,164,-1000,-1000,69,-685,-387,1000,-38,760,842,489,-971,146,558,1000,1000,416,-73,656,-930,276,-485,-1000,1000,1000,-294,820,-69,184,-68,84,-682,-304,352,-1000,1000,314,-998,272,1000,1000,-116,1000,-592,1000,-1000,439,-32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "equals(java.lang.Object):boolean",
            new int[]{57,1000,-10,877,348,418,-150,-571,87,-933,-254,888,614,-53,-187,110,-55,-424,-1000,-288,400,-464,328,-203,980,570,-172,1000,159,-1000,1000,-143,221,-48,-1000,401,1000,-158,-346,-71,-91,440,-595,706,-594,412,1000,755,419,206,-9,-344,-1000,-868,70,400,1000,-265,-812,230,935,-1000,-37,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{1000,-1000,-1000,461,-1000,-806,322,204,-654,86,764,-1000,1000,275,-790,-391,-246,-999,-685,-1000,-894,1000,1000,-116,1000,900,427,-995,0,806,140,-866,578,-181,-987,1000,-1000,464,167,-579,-180,-111,937,564,-117,-287,-1000,-1000,670,205,114,531,164,-711,-443,-132,305,259,-1000,-1000,1000,-724,-892,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{1000,-1000,-817,1000,-1000,-227,-356,28,-1000,-462,325,-1000,818,-852,-1000,819,243,-1000,-580,-1000,-1000,1000,191,-785,318,-395,-361,-1000,964,-1000,-948,1000,637,454,270,1000,-1000,-168,535,1000,1000,-1000,717,842,-1000,-20,-524,-1000,840,1000,810,-1000,1000,-1000,-1000,480,1000,-172,-1000,-726,70,-1000,-961,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{-611,-1000,-142,-798,-1000,-918,1000,69,108,361,45,684,967,-766,-347,-1000,-1000,-1000,-1000,-79,-1000,1000,1000,1000,-132,-466,-354,-1000,-1000,672,1000,209,1000,-1000,-400,1000,-1000,-365,-1000,934,-1000,256,1000,1000,-828,-1000,125,-150,-911,-1000,-375,432,-1000,397,606,-667,439,696,-994,-197,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{895,580,-487,-918,993,-167,-32,300,704,760,134,344,-132,861,118,329,-374,-410,707,-559,960,324,247,310,401,-229,-499,543,-835,-693,554,314,773,-717,-796,194,600,-96,177,756,729,920,972,-56,993,-900,-816,-361,151,-805,-255,-560,-235,952,676,-733,-444,-384,668,-914,732,332,-902,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{-309,387,339,-1000,94,301,-33,563,-342,209,454,25,776,-809,-307,329,-627,-1000,-321,-720,74,1000,444,110,-315,-971,300,543,-1000,-362,554,-1000,-274,-1000,-364,1000,898,-1000,3,239,-158,-419,1000,1000,-372,-828,1000,-361,789,570,114,-560,771,428,-1000,-45,908,-384,-232,-647,172,-508,-902,-231}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{817,-266,-809,512,703,112,-400,-46,208,406,58,-165,-347,-225,-78,-534,3,21,1000,528,-1000,193,-253,261,-1000,35,-436,503,198,-382,698,-157,1000,486,400,400,238,-400,-95,-303,960,800,1000,1000,940,195,-462,-87,129,-520,-326,-484,344,-1000,880,-391,1000,-1000,-232,-731,32,538,-54,-494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDimension():int",
            new int[]{195,-1000,1000,-281,503,-1000,-400,69,-1000,891,-1000,-477,1000,488,187,-863,-696,715,726,445,-775,158,-79,906,92,709,308,672,-1000,-373,-680,-626,303,1000,1000,162,-294,929,-1000,-303,458,928,1000,1000,495,-358,404,-1000,729,-712,-404,-242,344,1000,-1000,-707,170,-93,-638,-1000,-773,926,281,673}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{601,-992,-262,-1000,973,1000,-1000,1000,-1000,837,140,-327,304,286,236,54,-489,-1000,145,-970,257,-698,-490,-1000,339,1000,-1000,-1000,-487,-317,-431,-1000,-452,1000,1000,-1000,99,-399,541,-968,470,-1000,-999,482,563,1000,1000,-1000,-421,-635,-356,1000,390,1000,854,-1000,-964,-771,23,933,-410,-923,124,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{252,-1000,572,399,654,-771,523,854,-822,-271,-186,878,-599,-208,281,225,707,-1000,688,221,-630,-905,270,-869,332,704,415,-742,854,-1000,994,-643,-534,611,406,-65,-517,322,1000,-1000,-790,-1000,-1000,-369,770,601,-927,-361,-39,-1000,-55,410,1000,-661,350,-390,378,-748,379,-531,-13,-1000,1000,894}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{468,-756,621,-844,654,-477,635,854,-673,-406,-238,178,-552,-404,236,494,634,10,-66,354,-669,-772,-300,-611,0,704,415,-47,854,-18,740,-721,-195,662,406,-308,803,378,376,-404,-1000,-472,-449,-302,894,-326,-1000,-145,203,473,227,-1000,-971,225,593,-23,572,-748,654,-42,-13,577,871,306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{145,662,186,519,-586,-144,4,-908,-965,345,358,736,-173,162,692,-715,154,-227,-959,-798,-855,192,-441,-578,82,-634,-662,579,275,992,-829,121,-45,-274,452,155,24,143,-32,-251,867,-281,1,930,-147,641,828,-390,-366,-723,526,-706,893,-206,758,572,-165,-394,-826,-886,341,-958,954,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{1000,314,1000,-694,479,530,737,-745,612,895,-159,340,459,-844,1000,366,-966,443,-289,1000,-1000,1000,-1000,-480,417,775,388,-80,11,14,-445,-1000,1000,841,839,126,771,343,12,987,-775,-645,407,1000,411,181,570,771,720,1000,-808,601,-1000,450,-210,-1000,99,653,1000,1000,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{-556,-18,-591,847,909,945,108,1000,843,37,-1000,958,107,569,-642,18,-1000,-491,-832,-184,1000,166,-1000,-762,-341,-1000,35,331,840,-44,881,575,-142,-451,208,-1000,719,-860,-548,-1000,-240,-1000,375,743,-1000,-688,661,-1000,-587,-121,167,-275,308,252,1000,-295,532,-551,915,-380,554,252,-184,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{-1000,-753,242,597,-701,-1000,1000,-649,-364,-793,-327,489,-502,-1000,344,264,-672,-799,-683,-560,-1000,-871,-1000,837,647,-137,1000,856,-502,-443,1000,373,540,1000,300,-128,-673,25,-1000,1000,741,-377,411,578,-847,-176,468,-618,-146,-609,539,-1000,6,-485,134,1000,1000,973,475,370,-395,902,756,-121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{917,-654,-715,-252,-153,120,212,593,507,-616,-917,461,-768,1000,-1000,-961,-716,66,-583,303,-897,-246,-845,-205,311,-1000,1000,-751,1000,-67,-228,668,928,360,80,6,557,-1000,907,-37,354,-1000,1000,1000,442,367,1000,-604,75,-82,-530,400,-239,897,-166,367,394,359,967,430,278,-652,-972,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{-837,-668,455,752,-384,-355,1000,-1000,29,-415,-1000,636,-921,-751,-12,-726,-1000,-1000,-31,-806,-1000,-215,-627,-133,-1000,-1000,1000,1000,-303,-1000,1000,1000,1000,1000,603,-147,1000,-885,-790,711,1000,-1000,894,525,-933,-520,1000,-362,1000,-1000,-299,-1000,1000,-698,738,1000,1000,1000,1000,-1000,1000,1000,321,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{1000,-167,-891,925,1000,951,-249,-422,780,300,-1000,689,-457,528,-206,-501,-870,-113,-756,-600,1000,592,-1000,-883,-449,-1000,100,-184,922,-134,-381,510,265,-1000,429,-415,862,-747,1000,-1000,360,-968,319,874,-350,-826,417,-544,-362,103,356,893,474,-50,293,-200,-421,-424,1000,343,1000,139,-561,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getEntry(int):double",
            new int[]{1000,-1000,-1000,-896,286,-839,383,-706,43,120,-1000,-1000,978,7,1000,1000,1000,1000,292,1000,128,1000,-688,-27,1000,944,388,1000,245,-921,1000,-1000,306,137,619,-316,1000,632,-1000,1000,4,856,29,1000,-1000,1000,325,151,43,488,-1000,1000,1000,79,-519,1000,1000,1000,509,465,619,422,350,-879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getEntry(int):double",
            new int[]{280,-827,453,144,-1000,1000,157,-1000,-269,932,872,807,-364,107,828,269,1000,-93,670,1000,570,199,725,591,-383,1000,-249,-210,853,132,-19,-50,298,294,-418,-329,102,-229,-538,84,-122,291,-938,-442,352,-624,-984,-723,854,850,-96,-289,82,-280,465,421,939,390,430,748,-70,537,15,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getEntry(int):double",
            new int[]{972,11,-691,786,-1000,1000,1000,762,1000,993,-489,1000,127,-252,563,-1000,-1000,-1000,698,35,-1000,-1000,888,978,-1000,838,-1000,862,-714,1000,-1000,924,921,70,-35,316,734,-607,82,-1000,253,-1000,1000,-1000,1000,-4,-374,-1000,1000,556,1000,-1000,-1000,-342,143,-756,-887,-727,-947,-1000,-837,152,335,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getEntry(int):double",
            new int[]{-588,-181,148,-223,-712,309,-226,-345,478,786,972,-165,-1000,343,444,-262,583,-126,1000,1000,34,1000,632,-444,432,290,-243,334,472,-240,-1000,1000,673,-415,262,-1000,-282,-125,357,-1000,-309,-819,-541,-320,492,-1000,-792,-785,29,96,-565,-445,627,-362,-558,686,37,-73,-454,-494,403,465,333,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getEntry(int):double",
            new int[]{665,-915,844,-1000,-858,-1000,792,-653,-947,1000,688,1000,-385,428,335,1000,175,522,214,1000,1000,1000,982,-948,1000,275,71,-579,-405,784,-15,-708,1000,592,-1000,-1000,975,1000,-784,-288,-1000,867,-1000,1000,610,-168,-994,-406,-1000,-905,380,553,338,-1000,-870,1000,1000,511,-116,-265,1000,1000,-215,258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getEntry(int):double",
            new int[]{1000,-828,-26,-1000,-467,-775,668,-410,291,865,-538,-791,-1000,343,1000,629,583,450,155,1000,-411,1000,-221,-444,968,1000,1000,1000,151,-180,-346,-1000,1000,110,-382,-1000,949,1000,-61,-39,-1000,296,-838,880,-311,1000,-597,835,67,297,-802,1000,1000,-394,-558,830,967,188,321,-8,578,1000,-23,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{744,-32,-1000,-1000,-153,-1000,263,-622,-389,-1000,-683,300,1000,-1000,-192,606,-1000,1000,-247,1000,-48,-1000,-333,-786,1000,-87,-1000,-1000,-1000,394,120,152,-1000,929,-1000,273,373,35,-1000,1000,621,-592,764,7,-788,1000,1000,-1000,981,-1000,-1000,372,-1000,-727,850,-1000,-415,-1000,786,-293,599,-1000,1000,-203}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{1000,-690,-1000,-297,193,-1000,-974,309,-190,-1000,226,-1000,1000,136,1000,288,-1000,880,-180,837,-1000,-405,1000,-1000,173,-585,-1000,-1000,-849,876,636,-1000,-1000,-830,-1000,1000,1000,42,-301,-833,359,-1000,153,-1000,152,375,1000,-574,-310,-544,-1000,-620,-283,597,503,482,1000,454,527,-749,-432,-1000,951,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{-306,-909,-179,2,689,-321,-83,13,-435,-771,-251,624,854,-161,959,-339,418,52,77,732,-81,648,-789,-411,-292,771,-743,-737,238,756,-413,-582,534,4,-707,224,541,488,978,-762,211,-894,811,-721,110,965,107,66,-14,907,42,550,-717,107,289,260,885,421,319,754,512,-702,937,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{-1000,-1000,794,-155,1000,635,444,8,-674,-183,15,940,-43,920,216,-1000,1000,-1000,543,-1000,84,-1000,-1000,-396,-62,1000,714,-199,277,312,-1000,1000,292,-244,-879,-1000,-937,42,872,-1000,500,765,-63,339,1000,401,-746,185,-1000,1000,-97,740,300,648,-590,405,1000,-315,344,-802,31,-176,400,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{620,-142,-710,819,564,-11,-244,-297,-918,182,281,274,599,481,-108,-132,-519,-809,-171,-812,-352,693,818,-281,833,-420,-586,363,-514,-364,-226,-617,-221,-856,-646,-394,-569,-709,-72,45,117,17,-962,-435,-848,-445,-438,41,-200,141,397,-429,97,866,-535,735,371,-492,-813,649,-195,316,392,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.OpenMapRealVector):double",
            new int[]{-13,-240,76,-804,-326,493,813,228,-773,308,-904,-1000,-1000,-1000,-774,-704,73,650,279,-1000,274,-1000,-614,1000,-405,729,988,-58,-714,698,-279,1000,-13,585,193,-617,1000,976,-67,720,638,1000,1000,1000,-998,577,120,-243,498,-728,-286,1000,-467,-787,660,-622,492,-541,362,-862,932,192,737,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{-578,237,275,-491,628,-160,503,458,-434,-513,407,493,217,308,-61,744,-396,-218,-144,-357,511,90,-57,-673,246,-194,-1000,-631,1000,-319,-504,-769,-254,-114,-650,-1000,-414,290,652,-205,-750,-712,315,-43,1000,702,423,334,-424,-1000,-213,346,-33,-654,-555,712,-379,-468,-450,120,279,328,-313,-917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{814,-472,748,-447,-779,-596,-261,489,-639,-681,-168,-937,742,-857,-111,438,217,-453,-16,-583,404,-1000,594,-317,630,-375,-255,-374,357,-507,-114,-129,-1000,-625,-635,1000,-378,222,-853,369,313,-490,704,-408,1000,-75,-349,-370,-410,83,-16,-109,-983,199,-285,5,-274,-76,-1000,-132,295,373,12,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{772,-630,-785,1000,-1000,-882,873,-696,-539,828,-215,322,590,-1000,106,1000,1000,519,1000,-877,-536,-347,-97,-1000,1000,-836,834,1000,-1000,375,-1000,906,250,650,-888,1000,-207,322,479,671,-144,-1000,960,556,535,-827,-1000,-513,181,503,168,-787,366,334,-1000,-337,701,-615,-827,1000,-159,595,166,451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{196,-82,1000,-721,904,-290,-1000,771,-25,78,-1000,1000,-1000,444,-189,-101,-295,384,-468,1000,501,-1000,1000,-487,477,-813,-615,-702,473,186,-290,-281,1000,-187,633,-885,319,-987,-131,-905,21,-1000,112,59,523,939,-275,220,253,-1000,-833,148,237,31,783,363,1000,105,-1000,823,1000,130,-748,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getL1Distance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{51,573,473,605,897,-640,48,-302,-72,530,-122,229,1000,98,-189,-183,-18,1000,127,1000,-355,-265,-463,-182,-671,-362,-224,-11,1000,-363,-184,396,291,-345,799,-710,259,-155,769,100,-508,-965,587,-1000,902,662,-374,-411,525,-1000,-1000,-199,-83,-349,-851,1000,181,-240,-530,878,770,268,-748,-384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getLInfDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{-116,-316,-279,-358,-1000,-442,-243,30,1000,-649,780,-1000,849,-805,1000,-632,-747,-205,-330,-1000,-403,1000,84,-38,-955,-349,-319,-752,-844,-619,265,101,-313,-144,-520,-23,685,-199,-846,287,-98,33,-1000,-722,-196,-200,221,-508,-160,194,1000,450,-637,363,-945,-532,742,-763,-555,-360,-67,-1000,-554,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getLInfDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{1000,-893,1000,-315,13,1000,-1000,248,-752,-900,-1000,1000,77,-278,-603,301,1000,533,689,-1000,-625,-734,-1000,304,-1000,1000,1000,626,-746,465,849,-609,774,-583,1000,-1000,-761,-1000,581,-908,896,1000,1000,-1000,811,-619,-529,939,-511,662,-1000,1000,-164,466,-1000,68,-940,-1000,-17,-1000,114,-384,-559,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getLInfDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{1000,635,1000,374,-647,-442,94,30,-83,786,-462,-182,-924,6,391,1000,114,1000,-623,-323,910,660,-651,841,-438,-50,-41,77,15,-619,288,-756,41,-479,-520,121,-1000,-895,325,-567,864,569,135,-722,1000,-1000,-540,-325,-950,234,-104,184,727,-231,178,940,1000,-763,-1000,-537,-1000,-553,-2,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getLInfDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{1000,-1000,388,573,1000,646,-115,-1000,-364,1000,-804,793,-70,14,-101,-262,1000,-216,253,-1000,565,-798,-1000,185,-85,754,1000,181,-1000,782,-376,-1000,640,-20,774,-963,-1000,-1000,330,-772,-21,582,726,-1000,292,-82,-507,-429,-1000,1000,-230,88,-112,184,-402,1000,289,-561,-500,-697,282,-117,-48,112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getLInfDistance(org.apache.commons.math3.linear.RealVector):double",
            new int[]{940,-801,577,-469,-36,571,-259,1000,7,-1000,719,-766,1000,1000,1000,-1000,-732,-431,-1000,-859,-617,-852,-1000,-855,854,-62,-153,616,-1000,-138,597,1000,-146,-94,373,-971,-517,-455,-1000,-210,-881,-1000,-621,-891,-935,1000,1000,-187,1000,-59,1000,936,-1000,1000,-170,-1000,68,-1000,873,-792,1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSparsity():double",
            new int[]{-657,-1000,-1000,656,70,-18,451,839,126,807,1000,-13,587,151,-1000,-1000,1000,-888,-85,-1000,-1000,940,279,-1000,843,294,-977,836,-1000,1000,-212,543,-450,-361,-168,-1000,-558,-188,-1000,-1000,1000,1000,202,-1000,420,-1000,1000,587,1000,-1000,-1000,454,-1000,1000,167,848,1000,879,1000,149,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSparsity():double",
            new int[]{-952,683,791,-143,59,846,-527,-72,747,-885,72,387,664,539,-216,953,-990,674,-517,682,807,-668,-396,361,-643,573,-432,-67,897,-893,439,804,507,325,98,589,877,52,130,402,-714,848,-179,143,-688,145,-804,-591,191,-118,-145,628,518,-620,-464,808,7,-798,-235,902,-587,-545,-234,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSparsity():double",
            new int[]{258,-1000,458,-85,-234,51,-387,-101,920,275,20,-722,-1000,384,-528,-546,361,-133,132,91,-189,-68,-39,103,1000,-1000,-832,980,-1000,125,-88,-371,84,-41,-148,415,-362,637,-702,931,-133,343,223,-62,247,-466,-158,-421,988,-238,1000,265,27,350,-448,508,-89,-373,239,923,89,102,-627,348}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSparsity():double",
            new int[]{-1000,454,-226,-1000,485,241,-1000,-966,900,115,589,363,664,825,-168,1000,-990,353,-643,120,-43,42,331,1000,514,117,-307,-608,1000,-105,300,367,-827,-611,235,1000,1000,644,-65,133,-1000,1000,-1000,-738,464,-229,-407,-206,1000,374,1000,442,-570,-620,-20,1000,-176,-232,1000,994,-685,-1000,262,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Double:MC44", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSparsity():double",
            new int[]{-492,-1000,-253,-13,507,88,-214,522,948,1000,798,185,-475,534,-1000,-1000,685,-942,-532,-1000,-1000,154,-808,-553,-606,-395,-1000,1000,-1000,1000,-86,-468,981,-532,-839,285,-197,-106,-1000,-1000,1000,1000,883,-367,-33,-1000,723,-54,607,-1000,-479,117,-1000,1000,692,1000,1000,453,1000,464,505,1000,-980,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSparsity():double",
            new int[]{-858,-1000,512,-366,334,224,-818,-318,798,-861,293,-513,726,485,342,1000,-759,672,-219,498,87,-846,-493,-328,76,-786,-356,-790,-974,-742,-96,778,-55,167,-249,802,955,283,-682,1000,677,738,-158,788,-552,437,-836,-580,462,153,244,401,492,-635,-596,854,-817,-945,-652,638,-1000,-920,-75,-828}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-121,-1000,475,731,-856,-451,-696,982,349,1000,-716,-1000,197,-838,1000,-217,705,-121,1000,-1000,-626,-547,-357,668,15,-363,1000,166,-67,-1000,321,-1000,1000,-169,-120,1000,788,-727,-260,-104,-33,29,-380,131,-1000,-154,209,-608,-668,610,341,705,-535,970,-1000,521,157,140,194,225,1000,-646,144,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,58,-250,193,996,1000,-404,123,-819,159,-1,-327,1000,162,574,-711,1000,-825,-19,549,-941,754,37,-661,1000,-157,-214,-56,-63,-1000,-675,447,-154,999,1000,731,648,-1000,-466,-1000,68,712,-695,584,1000,676,537,-1000,921,1000,-1000,-12,72,-176,-484,-492,411,229,-187,1000,-417,1000,108,-777}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-686,-1000,578,-154,-748,-139,-1000,239,-1000,414,-431,-166,-216,558,265,-1000,-533,-444,-574,-748,-459,-611,725,836,-1000,-492,200,-774,404,665,-824,-269,1000,-943,-98,-801,431,995,-1000,184,-37,1000,980,724,-1000,1000,1000,-1000,-376,-226,999,-984,1000,-495,-717,1000,280,139,-378,-1000,1000,-1000,-578,-860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{998,-190,13,564,974,587,-556,462,-677,525,-602,-394,562,-100,1000,-47,867,-881,429,368,-1000,656,-53,-1000,208,-846,-284,-531,312,-233,202,67,185,-70,542,378,-518,-874,-1000,-1000,29,-340,156,878,1000,1000,1000,-1000,1000,1000,-734,-599,1000,-288,-1000,281,861,230,-1000,1000,-315,1000,-228,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NotPositiveException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{339,-667,-835,179,518,90,-622,279,-746,568,610,-734,310,-129,-986,671,465,-780,1000,525,-1000,-755,-687,-374,-1000,-1000,565,616,-1000,-739,-423,1000,-129,37,852,786,1000,802,-400,-413,-153,483,-534,1000,69,592,457,-1000,344,446,-745,294,-15,-274,-981,135,-198,8,-778,81,-430,413,-1000,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-893,-484,502,-294,-1000,579,-2,-246,420,-1000,-344,-237,-156,934,-574,-245,-353,696,-1000,38,48,-509,235,732,291,-130,-17,-1000,821,1000,-631,83,-229,-840,-140,-967,479,1000,306,930,79,904,1000,89,-1000,-421,715,-412,-749,-1000,31,1000,-114,-928,-3,143,-94,-400,1000,-1000,1000,-994,1000,-598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-13,-1000,904,1000,162,567,-1000,518,-1000,306,-1000,-540,506,625,547,-989,777,-1000,-433,-602,-443,-315,262,567,269,-952,93,-143,1000,-68,-549,-336,-39,-98,52,-280,1000,691,-1000,-141,1000,1000,528,789,30,120,842,-1000,427,428,-1000,-1000,1000,-525,-1000,-271,1000,795,184,-227,-152,-1000,-884,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "getSubVector(int,int):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{308,680,-908,-67,349,0,-622,-53,-178,498,1000,-760,704,-321,-117,-681,1000,-685,388,1000,-21,1000,263,302,345,990,703,475,-1000,-1000,-818,-315,-1000,251,990,1000,-74,339,976,-347,-45,1000,-531,-151,-325,-302,-249,401,-1000,473,-875,1000,-612,196,-184,-348,-712,-1000,947,-168,-411,-462,103,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{416,-434,-99,-417,884,300,-835,657,656,626,520,-701,474,-2,417,-719,-72,300,274,-592,658,299,1000,-741,-1000,697,633,565,-940,309,-959,-851,-318,985,-884,116,-614,-613,-337,-415,-1000,607,843,218,1000,393,-1000,634,89,229,1000,658,596,981,1000,-325,756,37,-324,-894,-302,1000,-568,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{465,-93,-1000,495,227,-555,811,-404,-159,-217,246,-1000,716,538,688,-462,-888,-73,1000,-418,998,-548,577,-315,-807,-541,240,-879,-291,-517,-85,1000,-479,-78,319,-381,-680,-153,-64,-1000,-1000,327,898,-416,-237,63,539,-479,263,566,-552,-750,551,-651,912,-576,-890,351,226,-359,-343,-49,1000,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{-781,-107,-98,-125,884,300,-843,-920,656,10,469,634,474,660,-746,-264,-628,265,819,-727,594,-863,-361,-741,-125,495,-4,79,-681,673,-138,-291,576,777,-884,116,594,-665,-337,961,-852,59,843,-607,-455,165,623,-744,426,881,-489,-655,-565,737,-140,479,673,37,306,-894,-697,-494,-568,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{-1000,-937,1000,1000,80,438,1000,376,119,1000,-788,-1000,-1000,933,471,1000,894,237,-339,-599,888,-1000,-459,772,-1000,-311,-315,-1000,648,-1000,-274,-866,-1000,341,-889,-818,-833,-640,209,-508,1000,-1000,950,761,49,908,840,513,524,931,78,-1000,-1000,-512,1000,366,-1000,-105,96,581,-553,-64,1000,-868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{337,-615,-902,-796,98,-305,-862,482,226,196,637,-946,832,-553,-178,-941,972,-162,208,713,827,-556,438,-527,-903,896,515,688,-243,636,-480,-127,583,-126,143,778,481,-724,796,-650,-514,939,917,-670,597,-394,-807,799,57,187,-163,252,579,672,823,-46,153,288,30,-745,-475,376,611,-677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{-1000,416,505,566,-438,152,483,286,-672,85,-857,-1000,-1000,686,553,1000,199,-450,73,281,-170,-1000,-1000,561,-540,-1000,-639,-1000,712,-831,475,482,-498,-576,1000,-122,192,490,438,108,782,-1000,-361,605,-249,719,1000,-406,-443,40,-511,-1000,-1000,-1000,99,-211,-1000,643,44,994,-1000,-282,81,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isInfinite():boolean",
            new int[]{-496,937,-1000,-1000,388,-237,817,-58,1000,439,-102,-293,1000,-14,229,599,990,320,-990,468,1000,-321,-495,501,-818,1000,-203,-549,-438,-1000,-344,-1000,-829,-598,-1000,-442,967,-1000,-719,-842,1000,-11,1000,462,435,-421,-1000,-1000,-1000,126,861,-1000,-203,906,-186,1000,-215,812,-902,-755,102,518,1000,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{563,-854,-456,-862,-915,555,-1000,-1000,65,581,-143,-456,-558,-1000,907,842,391,148,1000,-1000,1000,951,-1000,1000,-143,-640,-839,907,86,-783,4,-1000,-935,-164,193,-60,-929,-501,-837,1000,-1000,-687,-456,38,661,-1000,322,-700,-1000,664,-494,671,1000,-40,-633,-803,608,713,652,1000,261,-658,-674,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{1000,-1000,391,-703,-1000,-521,87,-334,1000,-465,225,-849,-1000,-737,642,-729,571,-98,1000,-999,-487,766,-587,1000,1000,-5,312,-3,505,-75,-532,-1000,-426,-338,839,-994,-830,940,-733,382,-345,403,1000,737,-399,-361,-1000,-349,544,489,-1000,-145,865,-288,-764,552,433,598,-93,1000,-631,260,392,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{-125,-432,1000,-315,-1000,-546,276,72,431,-396,-932,249,-1000,1000,1000,-1000,-386,1000,-1000,1000,-353,677,270,441,1000,965,-671,-661,535,-237,-319,-1000,1000,-464,1000,1000,-1000,377,-1000,1000,-431,1000,-1000,-343,296,-1000,-33,-235,945,217,-783,-858,-150,-763,-45,111,716,13,-15,210,-1000,-533,1000,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{376,-1000,391,-739,131,-591,-1000,-1000,1000,201,225,390,-1000,-449,642,636,571,-505,1000,-1000,1000,500,-891,1000,-434,-280,-391,867,892,-757,-464,-1000,-426,-172,359,628,-181,-287,284,1000,-1000,-656,1000,120,91,-1000,282,-655,-1000,-712,-1000,530,865,456,-886,-947,416,598,1000,1000,45,-694,-535,-597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{993,837,197,-80,709,965,357,465,-452,347,-293,-335,-534,-132,-518,-673,-630,890,-912,-247,-320,283,-164,968,960,-735,539,-232,-591,-698,249,-359,224,813,367,-817,90,-449,445,761,408,-816,-912,835,-758,803,-473,652,441,891,711,794,889,615,-983,-539,-559,-779,-929,314,-281,783,589,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{213,-57,73,-739,594,-630,-1000,-986,207,507,-16,-277,-1000,-228,-24,-567,520,840,1000,-1000,1000,-168,-191,89,-1000,3,224,1000,1000,-307,787,189,-426,123,-393,-662,597,-287,284,-334,-612,-1000,1000,719,91,-16,282,36,-1000,-1000,203,713,-54,369,-886,-38,-581,-127,1000,-870,297,-656,-1000,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "isNaN():boolean",
            new int[]{356,-654,645,-856,249,-538,1000,-922,883,152,-915,-32,816,165,1000,-226,516,265,1000,-105,57,425,-973,1000,-1000,1000,140,1000,1000,-75,-862,-976,247,355,-1000,1000,-133,689,389,993,-86,1000,519,419,559,-867,-609,-795,-294,-1000,445,320,-316,-1000,-684,296,-247,-1000,1000,106,644,-464,232,-50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAdd(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-72,-866,1000,970,1000,1000,-450,1000,-232,459,33,818,1000,1000,786,363,-826,534,78,-872,575,-877,-498,820,-722,815,131,-559,-532,-681,-1000,399,-389,567,396,817,-226,-974,-567,837,619,319,-136,288,142,873,360,-109,-913,-419,-508,-1000,-730,-989,880,-1000,-1000,1000,958,779,-869,821,880,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAdd(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-376,776,-1000,413,1000,1000,909,849,944,-101,-775,1000,521,-564,-650,-718,-141,465,-493,1000,513,941,-446,252,789,831,-758,1000,1000,-1000,1000,-936,780,-12,396,-432,-229,31,538,-937,-220,-15,-51,-929,-500,581,711,1000,532,-253,72,982,-527,634,1000,897,1000,841,130,-651,791,-555,304,228}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAdd(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-369,-1000,586,273,-287,759,-270,1000,-1000,-202,89,564,-426,-388,733,398,186,767,-690,638,-448,175,-672,881,254,1000,39,29,-388,832,1000,103,-1000,338,-478,305,405,-780,-183,-187,916,-769,962,-537,1000,6,-318,-384,-1000,-757,856,-256,655,768,1000,-76,825,164,1000,-1000,-914,-624,798,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAdd(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,-288,-880,-297,-287,2,-102,-178,-1000,338,822,533,-675,-486,-960,-1000,-531,-767,-1000,4,1000,91,-445,622,-696,1000,1000,431,710,-424,1000,913,-302,-195,1000,395,-385,461,3,295,966,28,-683,-581,1000,-185,-360,419,-731,1000,1000,-976,-66,82,1000,561,2,425,-900,-713,56,-624,966,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAdd(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{450,-208,765,-376,-1000,524,-744,-648,-1000,-171,909,312,-678,234,79,-839,422,-471,-1000,-182,238,-380,-106,980,-927,658,-190,-179,-256,-1000,306,-224,55,189,-225,1000,773,800,1000,911,395,670,-893,129,382,-450,-649,665,-1000,189,584,-1000,61,-1000,663,298,-103,-216,-700,-138,110,392,-1000,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAddToSelf(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{299,-217,-529,1000,-103,-1000,42,-1000,-321,50,46,496,332,-387,-129,1000,234,-582,-1000,425,-963,-864,549,-77,-931,-239,-85,467,-59,737,283,-304,384,124,970,-2,11,686,-572,-1000,-365,-183,-1000,117,581,-120,-240,1000,-983,137,-667,801,-879,-321,535,-569,595,137,-67,-191,598,-893,-367,519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAddToSelf(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{1000,-649,604,922,1000,402,148,-526,910,-543,-253,421,-728,156,-383,720,516,-4,-1000,329,-469,430,185,-1000,-761,-697,-570,-158,897,-301,-557,1000,390,-1000,440,375,691,979,-391,-1000,-46,-272,1000,164,-55,1000,113,-1000,-225,-557,-705,985,-787,1000,532,-10,-102,-1000,552,-890,-499,-57,-280,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAddToSelf(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{227,-365,1000,-556,13,1000,996,427,648,-511,1000,1000,-793,-291,-910,619,547,1000,68,306,-265,-441,688,-514,-528,337,-122,321,450,311,-1000,753,475,-204,1000,-300,-936,57,-478,1000,663,-613,849,234,-596,1000,474,283,-916,-575,1000,-724,623,-114,-1000,752,-1000,-641,-847,1000,286,-596,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAddToSelf(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-6,685,-690,-226,-103,-37,-1000,-187,-374,263,-966,496,396,-570,775,69,176,566,-808,-774,-642,-518,-638,-263,-931,-229,-565,440,948,-486,-335,-522,305,618,603,-697,-926,603,-796,-66,-82,-274,298,388,-245,-942,378,761,393,-541,-651,-593,-210,-733,932,-572,-92,-296,-1000,277,190,-414,187,-772}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAddToSelf(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-486,-1000,256,-711,1000,1000,-988,823,1000,-821,-910,329,-159,-879,-346,-761,-247,1000,607,923,148,-257,318,-1000,-263,1000,-58,31,797,-977,-1000,1000,661,-1000,1000,716,-278,341,-252,-218,1000,-326,-1000,-830,-1000,1000,527,-371,-324,-292,-597,503,698,-171,373,1000,-42,110,-417,-792,-1000,986,-1000,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "mapAddToSelf(double):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{705,239,-235,1000,621,96,509,1000,-1000,1000,760,-20,-384,-525,307,1000,-1000,961,-842,-297,688,17,502,412,-539,-1000,952,62,416,1000,-721,680,-11,1000,378,-1000,-758,944,-913,436,-719,-1000,1000,-570,-1000,-600,1000,-700,52,-1000,-522,135,-1000,958,1000,923,127,-1000,133,1000,-555,-954,-980,-930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "projection(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-122,-74,1000,14,-217,-463,-24,-369,14,865,181,-351,-468,-755,-215,-335,-43,-756,-1000,-678,-1000,-242,-621,-962,1000,1000,817,932,171,-922,302,1000,137,-1000,220,711,-249,-206,-987,424,99,1000,303,-1000,546,460,1000,-743,26,-949,384,437,546,-179,656,42,-79,-892,-1000,-1000,-694,-86,-196,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "projection(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{656,-218,264,-143,-696,-613,-427,84,609,-306,773,958,950,235,-247,86,638,-561,-174,-652,5,-258,830,-331,-497,423,75,-613,437,447,667,-559,902,264,-792,-972,991,198,-104,889,408,-161,-344,-105,244,-328,-905,256,871,-916,763,-689,-345,-172,-460,-373,-508,-950,-246,-88,81,-934,94,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "projection(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-925,-1000,-1000,503,-1000,-337,560,-194,348,-1000,-1000,-474,-681,-702,576,-746,-295,-1000,-630,-361,1000,503,-44,-780,1000,-439,817,25,-225,-1000,898,1000,-1000,727,493,1000,-1000,-1000,-1000,-700,1000,-1000,1000,-1000,-658,93,1000,753,-788,-124,353,1000,-717,92,1000,-558,-990,-721,-1000,-1000,-1000,754,-94,334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "projection(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-147,91,152,-5,-595,-1000,483,-642,-72,224,-361,-1000,23,192,151,-748,205,-421,-502,-1000,-1000,-725,-236,-337,924,2,452,940,-454,19,974,-782,-719,-1000,409,834,51,-13,-791,720,-7,1000,49,-824,658,-81,922,260,830,-837,168,-65,458,-114,195,428,-10,470,-728,-351,-918,-535,-658,379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "projection(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{931,-678,-1000,751,-1000,633,548,-194,-838,-522,-1000,360,339,-702,576,-1000,-1000,940,-899,1000,1000,-553,-644,147,1000,613,684,-1000,555,-1000,-171,1000,-949,-535,211,1000,-444,-361,-1000,-696,1000,650,1000,523,-767,93,1000,500,-462,690,1000,1000,-1000,-433,1000,-507,-1000,382,-1000,-1000,987,1000,396,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "projection(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{737,-357,286,202,65,-852,-693,-863,-666,1000,752,-218,728,-211,-458,58,685,-689,188,-1000,-141,153,602,-1000,-78,605,999,978,787,-294,80,127,186,68,-1000,367,318,-493,-789,572,91,-1000,-237,422,1000,961,771,-194,-170,-825,-530,210,785,-305,22,-44,-248,-468,-312,-493,594,80,-1000,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "set(double):void",
            new int[]{16,518,1000,-612,399,174,-497,-903,151,400,791,-454,-678,630,-332,45,943,-792,-1000,-1000,-528,375,-425,764,47,19,-1000,1000,439,-166,455,-771,-1000,378,1000,-1000,47,-1000,208,51,-1000,-1000,328,-1000,-408,959,971,243,589,-214,-932,521,-247,-489,1000,1000,14,278,-801,1000,-1000,-146,424,775}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "set(double):void",
            new int[]{-642,-104,-957,-519,1000,-27,-749,-190,-728,38,-1000,864,-630,-698,-75,191,-1000,61,-952,-692,-73,28,-272,-847,376,416,1000,-1000,-386,-362,213,239,732,199,-211,-321,851,-159,-175,356,-675,223,204,213,55,-110,114,532,1000,-78,-439,-841,171,-425,53,-706,-303,-236,196,-377,-445,-1000,585,130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "set(double):void",
            new int[]{-1000,-1000,-1000,-824,-115,53,593,502,-250,1000,763,691,-630,42,101,191,-914,1000,-1000,-48,-937,-999,-1000,-847,376,1000,-274,844,37,-43,1000,940,-286,199,248,-1000,-1000,-1000,1000,200,-655,1000,1000,47,152,922,166,290,-1000,80,529,-841,-811,-1000,-749,972,-997,-236,196,684,-859,1000,-510,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "set(double):void",
            new int[]{-1000,-894,-981,-371,-547,-990,-11,1000,-681,-226,1000,1000,-222,-478,-251,-8,-130,309,1000,449,-467,-380,109,-828,84,-182,91,83,829,-921,614,605,800,1000,-818,-418,-1000,-1000,460,1000,70,794,-593,1000,-522,889,-966,900,-915,-913,223,1000,-556,-304,-923,-1000,370,-936,-287,-110,-214,1000,957,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "set(double):void",
            new int[]{-906,-297,-372,41,-1000,-1000,90,-7,599,49,-480,423,-610,-323,-1000,54,1000,-104,-494,-994,-293,-369,-23,-1000,154,-259,841,215,653,19,548,-293,-17,278,762,-1000,-836,-601,151,-1000,-112,142,673,66,-158,389,507,593,-658,633,-483,1000,-126,-597,-905,-428,-135,996,-268,450,-56,346,254,837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setEntry(int,double):void",
            new int[]{591,842,-836,559,-1000,1000,209,1000,-1000,-164,526,-117,80,278,822,47,460,-718,-46,90,-1000,643,-19,-454,853,-891,1000,89,-9,-212,1000,-906,-5,395,672,746,610,498,-663,1000,-388,-205,737,1000,-60,952,550,522,-763,281,365,-891,208,205,1000,1000,401,582,1000,-400,-94,1000,-587,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setEntry(int,double):void",
            new int[]{526,-1000,-777,183,-828,977,-580,768,-399,668,174,-90,-465,67,122,-1000,15,-1000,-409,1000,-1000,422,543,857,-714,-1000,530,-64,1000,-82,360,-1000,-947,-842,700,-400,-224,-811,1000,-73,-1000,784,-416,-1000,1000,1000,-211,145,404,799,-146,-990,832,1000,597,1000,-198,234,-87,-1000,1000,245,682,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setEntry(int,double):void",
            new int[]{-799,572,958,-831,57,-498,-408,129,1000,-1000,-146,-521,-182,705,-44,776,-374,-85,-286,54,-744,-536,514,119,-204,-994,-334,413,-549,176,676,-348,378,-536,341,-355,1000,388,-20,-188,620,521,715,849,-2,-349,-1000,-1000,698,312,979,986,-142,-494,-862,-642,1000,-40,219,708,98,-1000,102,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setEntry(int,double):void",
            new int[]{-742,69,-824,-1000,1000,1000,439,-410,-309,-996,-1000,-26,-292,-874,-1000,596,-567,-278,-239,-21,14,-700,-6,849,-704,622,662,-680,-900,40,-349,319,-1000,775,-318,-1000,845,630,1000,906,995,-226,-423,-690,530,94,-406,362,-536,976,228,-1000,762,250,471,-660,-862,-281,-186,493,-1000,232,42,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setEntry(int,double):void",
            new int[]{-278,-324,73,502,-827,523,860,267,-804,-931,-261,-997,-566,-674,-904,761,504,-750,698,201,89,-385,239,341,114,789,518,-455,-614,-869,475,575,377,157,-942,739,601,-826,-732,-523,440,-364,625,87,207,-873,-227,-286,-283,589,977,217,-251,162,843,68,344,852,784,67,131,-871,443,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setEntry(int,double):void",
            new int[]{-39,-100,1000,-198,-619,871,368,349,-241,703,-84,-138,-339,819,561,-864,377,-847,-100,464,-527,499,892,784,-742,-430,329,382,286,-140,-460,-1000,-320,-1000,48,-1000,-319,-187,197,211,-339,666,-336,-107,1000,1000,-454,-602,798,621,-378,-873,874,827,322,980,-721,580,-182,-1000,1000,65,591,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{1000,-458,-1000,736,-1000,-922,-1000,-346,1000,428,347,-1000,34,1000,1000,1000,-1000,1000,297,-642,-873,-1000,1000,1000,710,-819,66,-1000,1000,1000,-800,1000,-489,1000,636,1000,1000,395,-1000,-1000,-328,-949,-1000,763,-240,751,-1000,1000,-1000,1000,-856,-1000,923,-1000,-510,-1000,-1000,-1000,-1000,-269,1000,1000,-927,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{1000,165,-297,330,-844,512,-1000,661,1000,258,534,620,1000,168,858,717,-611,266,1000,631,1000,-1000,209,125,765,676,-1000,-19,400,-121,408,337,-110,1000,1000,-1000,-575,-469,819,-1000,-216,303,-750,1000,-567,-650,-258,731,-754,1000,-858,-530,-1000,-1000,-1000,-1000,-400,459,-1000,-1000,988,678,144,-905}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{-896,-1000,845,-1000,-161,314,-263,-488,-1000,-670,370,-702,264,-958,-1000,147,-835,-992,-258,-430,-400,997,-1000,-106,1000,-666,916,1000,-1000,-606,1000,-469,-1000,-1000,363,-602,-289,-73,-26,-643,-1000,-1000,-682,217,-1000,1000,-167,473,1000,-579,908,1000,1000,1000,-1000,1000,1000,-303,520,212,-46,-262,880,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{794,-691,845,-1000,258,314,1000,-1000,322,200,-36,882,188,-1000,-1000,540,563,-135,-684,-1000,674,297,66,464,-132,-229,916,128,-1000,-560,591,-791,-1000,-1000,-445,861,-289,1000,669,315,-1000,-592,1000,-720,613,824,-618,-863,98,-713,33,-376,-1000,520,-825,-534,840,369,894,1000,-1000,-1000,-40,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{-1000,-447,146,-1000,-703,612,1000,-115,-173,-567,-237,143,-661,-1000,-883,-1000,-717,-1000,-1000,424,-480,977,-1000,-330,1000,68,-117,1000,-1000,-1000,1000,-1000,-1000,-1000,88,-495,148,-437,736,-1000,-1000,-971,-310,-945,-1000,1000,606,60,1000,-752,1000,1000,-495,1000,778,120,1000,1000,220,-740,315,-422,1000,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{1000,349,86,481,-1000,612,-342,23,-173,-318,-237,494,943,-876,-1000,1000,-1000,-296,-314,-303,1000,572,-507,673,1000,99,431,1000,-1000,-1000,-304,-500,-1000,387,831,332,148,-70,1000,-1000,-510,292,-459,-945,-1000,1000,-710,735,795,725,563,-477,-495,36,-1000,-91,984,-834,-115,61,221,967,-131,356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "setSubVector(int,org.apache.commons.math3.linear.RealVector):void",
            new int[]{-308,-372,442,-72,394,-382,-404,639,280,-217,558,-468,-36,-379,576,-154,-555,-481,10,750,1000,732,329,-596,-1000,1000,-108,1000,-264,477,603,551,828,-505,-681,-1000,494,67,703,-1000,-1000,380,-232,974,839,-189,-624,861,-141,-297,72,-351,146,160,-224,-1000,248,-157,1000,-531,131,652,1000,495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector$OpenMapSparseIterator", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "sparseIterator():java.util.Iterator",
            new int[]{696,226,-292,803,-428,501,-873,348,-531,394,-1000,543,268,-946,-198,330,-695,-1000,-47,-528,-59,335,-293,-395,429,-1000,277,186,275,-725,-1000,272,-430,112,-864,277,-726,43,-300,234,-1000,-815,-513,-99,-454,-1000,947,404,-295,-508,-797,-604,292,376,110,-141,85,465,350,403,73,-930,-265,501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector$OpenMapSparseIterator", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "sparseIterator():java.util.Iterator",
            new int[]{-879,-1000,809,-987,-424,-725,-259,868,-490,1000,-19,586,-130,-356,791,862,-426,-1000,589,-103,-552,-983,558,-596,533,394,-273,151,-333,-314,-1000,389,296,-550,1000,617,-776,-220,241,691,1000,262,561,114,-602,-519,1000,527,-1000,-628,-804,22,793,-277,692,456,-816,-833,-393,1000,-673,-932,218,572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector$OpenMapSparseIterator", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "sparseIterator():java.util.Iterator",
            new int[]{989,-456,944,-171,-441,317,-623,610,-489,13,-430,168,836,-530,-150,834,-859,-965,716,-452,317,184,229,-531,333,-638,685,223,-653,-324,-321,-46,-420,185,-58,824,-858,-176,-859,94,-618,-754,-799,-459,-776,-733,650,566,416,-991,-987,-915,666,309,-706,-956,-764,-467,-720,227,-301,-463,522,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector$OpenMapSparseIterator", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "sparseIterator():java.util.Iterator",
            new int[]{-481,-22,734,991,700,-3,892,257,509,288,-897,78,585,-561,-136,539,-637,-480,-592,-983,-891,-144,-122,957,-217,78,98,-698,-97,-219,528,977,-188,-726,256,290,526,151,-338,606,-323,-286,-190,-746,-999,-363,527,256,-733,677,-348,-825,578,-261,-8,154,587,-864,146,-970,318,512,565,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector$OpenMapSparseIterator", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "sparseIterator():java.util.Iterator",
            new int[]{347,-107,-70,-1000,-854,142,292,-896,395,-1000,-990,-1000,-690,7,-803,422,-918,-1000,1000,-538,888,-570,1000,-750,930,-19,-927,252,-824,1000,900,-255,1000,1000,-839,-480,-1000,920,-751,-520,284,-475,-668,1000,-363,-705,887,-229,1000,-642,-218,-951,1000,-114,-84,-1000,-1000,312,-1000,-68,393,320,1000,312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector$OpenMapSparseIterator", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "sparseIterator():java.util.Iterator",
            new int[]{1000,-456,1000,-17,624,49,-87,205,426,-128,429,-1000,852,-578,-150,404,-897,-965,126,-334,-213,244,229,-701,707,17,1000,477,106,957,325,-46,-174,258,397,824,721,256,-1000,233,1000,-316,1000,-1000,-1000,458,-661,-253,116,-991,-1000,-1000,203,-199,-1000,404,-764,-1000,382,-40,-301,200,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-918,-244,134,-1000,-1000,1000,881,604,238,553,246,-5,722,596,299,232,317,225,147,-251,452,-792,-1000,-1000,171,-248,627,-741,756,-410,94,-13,-73,348,-791,971,1000,811,670,-462,-777,-905,-380,-761,-235,-596,-602,-356,1000,-40,383,1000,-1000,-1000,196,-46,65,359,-66,320,-329,650,19,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{581,-1000,779,1000,492,-1000,-912,479,-213,-81,-541,1000,1000,879,871,474,-1000,855,1000,300,908,-242,1000,1000,483,-748,1000,-152,-1000,724,-1000,1000,-1000,125,1000,817,1000,-661,1000,1000,-301,-665,-783,38,-1000,-1000,-1000,-31,958,1000,1000,1000,785,-175,930,1000,-1000,-1000,-1000,641,546,-283,718,63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{165,-603,234,-89,-117,-582,-201,576,-281,763,414,883,-1000,-309,-762,-925,46,-798,826,902,661,-479,-577,-457,368,-277,881,-679,-403,633,-392,-467,-1000,-738,-154,202,921,-199,386,736,-104,649,-635,490,-712,-625,-1000,30,-1000,463,402,483,-632,-107,1000,795,-962,497,45,414,-260,-312,-472,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{134,262,400,643,-50,624,415,393,88,-752,128,-629,-1000,-127,-160,-11,211,-594,183,-438,800,-781,-315,-106,1000,-396,220,-400,-65,-892,-783,671,848,-1000,-511,129,400,-400,1000,662,-468,118,-159,-184,-39,-78,-824,640,-44,-840,457,662,-177,-1000,770,1000,-515,-874,-313,-448,95,-926,-1000,-638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{430,-888,-1000,1000,-216,-1000,-53,-495,-1000,-1000,-1000,736,-129,538,1000,-787,-119,365,822,1000,1000,1000,289,1000,-976,-770,208,1000,-1000,502,-1000,392,-249,38,-329,-1000,-1000,-225,1000,1000,-692,1000,-325,1000,-1000,-108,189,218,-400,1000,1000,349,874,-29,766,1000,-585,-35,100,979,133,732,-56,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.OpenMapRealVector):org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-349,86,-1000,-434,-1000,871,643,-471,-372,-763,-830,-447,-375,-123,803,-757,1000,-36,-755,622,530,697,-1000,650,-1000,-309,471,753,-293,-339,-72,201,1000,667,-329,-783,-86,444,654,-38,-540,1000,1000,683,744,1000,857,386,110,821,-722,-212,-312,-645,1000,651,330,495,1000,960,-222,236,1000,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-287,147,201,1000,-1000,-91,-692,396,674,-698,286,-1000,679,-1000,225,-407,-909,-566,-801,1000,845,-247,1000,-1000,-922,957,-172,112,484,-1000,-467,703,-1000,-811,-1000,-142,-564,50,1000,-696,-1000,-513,1000,-1000,1000,-49,-662,-862,59,229,328,-279,94,-659,-567,675,-334,-708,132,287,-65,-1000,930,-324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-192,331,-656,34,-826,120,124,967,-904,-1000,597,678,89,-138,-41,-246,-66,222,-234,1000,863,384,518,-223,301,629,332,885,672,-845,81,904,269,7,426,-853,5,154,720,-286,-111,-880,608,-380,594,-328,304,-1000,547,-124,384,-270,1000,71,-154,247,-33,1000,299,373,-369,-497,1000,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-233,174,-558,-554,-1,-373,306,924,994,254,-172,126,251,-324,764,144,357,-523,-783,1,860,763,-144,496,-323,-156,240,621,-39,0,122,-463,400,754,358,-302,-669,-671,495,-242,-767,-597,377,657,879,59,-399,-951,-431,352,-531,135,416,-566,78,257,280,57,-779,337,588,-275,1000,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-417,389,-1000,473,-662,-793,-655,-1000,1000,-904,-92,-557,587,-636,-406,-693,433,-73,-293,-133,1000,1000,1000,625,942,982,-876,-1000,471,-337,816,1000,976,675,-1000,113,-1000,1000,864,-838,-1000,-1000,1000,-194,559,984,675,-1000,-1000,-1000,-320,-711,62,1000,-581,159,1000,-571,-132,1000,-1000,522,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-506,-178,-655,-1000,108,-373,87,529,-358,183,-172,-308,-584,-206,-136,-132,745,-63,-994,510,180,1000,-122,460,-970,553,71,-283,801,-252,31,-620,400,-128,154,-302,-844,273,590,-1,-1000,-597,377,753,454,59,-46,-1000,-657,-1000,396,-245,333,333,-473,-273,87,574,-601,-233,-40,158,262,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{55,584,-1000,-657,-822,-34,1000,338,75,-30,-552,618,-490,-388,129,-102,527,-742,-1000,1000,1000,1000,-75,731,-137,946,-523,-648,669,189,485,590,-555,1000,1000,-778,-844,580,1000,786,-750,-1000,53,-699,275,1000,-172,-966,35,-46,91,-687,584,-437,-269,-700,1000,851,-700,304,-81,-1000,1000,-962}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "subtract(org.apache.commons.math3.linear.RealVector):org.apache.commons.math3.linear.RealVector",
            new int[]{-625,-1000,-1000,473,-265,-165,-1000,-491,-177,-65,-846,-1000,-42,-1000,1000,-529,-630,277,-293,479,241,210,1000,-47,1000,736,-876,-1000,363,-337,-209,-437,-141,-691,-1000,-502,-1000,1000,589,-43,-1000,454,721,-350,-54,-392,675,-1000,-960,-1000,1000,-511,-405,1000,-581,57,1000,-1000,38,1000,-1000,8,-192,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("ARRAY:[D:1:29:java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{233,237,-389,-658,645,598,643,63,978,-217,-323,-997,319,-912,155,-710,-913,-778,-542,435,351,279,402,-760,951,186,-111,-84,-246,-258,806,537,-17,-38,-837,49,-886,660,297,-156,-441,-77,-40,120,56,488,110,161,-511,847,-689,-968,-251,420,-218,-830,137,-929,-643,-105,811,-155,862,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("ARRAY:[D:100:29:java.lang.Double:SW5maW5pdHk=:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{445,29,1000,442,1000,20,214,1000,157,288,65,-633,-145,-44,-372,-1000,543,-516,246,-1000,-112,-174,478,-361,163,1000,-426,-611,70,-783,246,894,-786,-366,-635,859,483,-735,399,1000,-904,-336,335,-308,590,-386,241,831,-406,123,-114,-421,-1000,1000,-811,-459,400,-751,-1000,-725,516,498,-205,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("THROW:java.lang.NegativeArraySizeException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{-852,85,-538,-428,924,-95,542,80,523,-650,-786,-228,780,33,-759,-60,-825,435,-775,707,-802,533,367,-851,375,-39,100,560,53,-439,-506,-274,-384,-201,115,194,397,721,-42,-730,235,863,4,753,355,849,962,249,58,-700,-122,-658,-447,89,373,837,-793,-989,539,-147,456,473,743,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:37:java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{-1000,-579,-1000,-435,-1000,-114,-67,223,-39,-680,324,819,352,-504,-943,1000,-981,1000,-95,391,-1000,1000,-834,-950,90,-1000,1000,1000,121,284,675,-1000,-862,-1000,1000,-351,512,7,-1000,-1000,1000,1000,-907,-180,924,1000,1000,392,-788,-1000,162,1000,1000,-246,63,689,-153,-286,1000,823,178,-706,665,-266}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("ARRAY:[D:2:21:java.lang.Double:MC4w:29:java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{400,-244,254,-568,948,731,-484,1000,364,638,-270,-860,0,-583,-166,30,-579,-581,254,303,-210,-203,513,-1000,1000,1000,-400,-654,25,-827,315,-308,-34,-1000,-886,441,68,504,347,347,-400,-138,254,286,124,106,-229,225,-67,59,-1000,-396,-827,1000,-55,-400,306,-113,-400,-615,594,891,221,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("ARRAY:[D:1:37:java.lang.Double:Mi4xNDc0ODM2NDhFOQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{89,732,-515,-893,819,688,1000,-421,978,-585,-627,-1000,451,-912,492,30,-1000,-1000,-1000,384,910,431,629,-592,1000,-124,-203,-84,-403,97,806,1000,-34,-1000,-1000,-207,-1000,1000,347,-156,-734,-138,18,253,-261,642,97,161,-511,524,-888,-1000,-962,213,-292,-1000,-119,-1000,-1000,95,594,395,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("ARRAY:[D:1:37:java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "toArray():double[]",
            new int[]{1000,-1000,199,834,-573,277,-660,-930,-818,-996,586,359,-1000,-810,823,-875,835,-284,315,-877,-541,-1000,-949,400,615,538,-1000,-1000,-590,-351,594,1000,-800,826,12,785,34,-361,610,673,-1000,583,6,-186,-92,-1000,422,-597,212,-855,818,1000,-168,1000,-154,-1000,1000,909,-1000,-1000,-274,400,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitVector():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{186,196,-1000,-103,213,829,1000,1000,-1000,328,1000,183,-172,-1000,1000,-552,-1000,-1000,837,315,1000,-1000,674,1000,723,254,60,1000,1000,-407,16,815,439,54,527,556,114,1000,-990,-73,-17,-271,458,1000,613,459,1000,-353,588,-908,-761,566,904,-730,965,1000,-387,-1000,1000,448,1000,-142,-596,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitVector():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{262,42,362,1000,230,626,453,290,92,-262,843,-412,713,-930,288,-702,-877,-551,89,295,55,-136,348,1000,129,734,-690,816,445,-422,-359,277,454,-295,-747,679,855,1000,-52,1000,-28,-119,-264,1000,119,608,-324,-1000,370,-375,-663,462,-336,3,-97,277,-1000,-84,458,496,361,-210,1000,933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitVector():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{305,-428,130,-902,-1000,-183,648,537,503,-173,-355,-37,-270,-850,35,-256,577,-562,206,-758,-371,-88,547,10,545,347,1000,-169,736,-932,127,980,110,784,1000,-8,248,-245,357,330,-1000,534,395,67,-1000,232,809,119,27,545,-58,-1000,-246,123,-324,-1000,107,-115,862,-16,690,-375,-331,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitVector():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-488,499,-1000,-1000,-250,62,1000,1000,-862,-54,477,-325,-1000,-1000,706,-279,-362,-894,-360,13,231,-1000,-51,895,1000,292,-273,386,1000,-128,-234,614,904,649,274,454,126,1000,-215,-852,97,-330,-165,1000,998,340,1000,-660,162,-105,-696,-704,-525,805,658,816,-1000,-694,924,713,859,-142,-848,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitVector():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{261,-937,1000,396,-538,937,-1000,233,1000,547,-694,443,49,303,586,-274,607,1000,-312,525,-539,621,90,-154,-763,687,-236,-585,-489,-1000,-448,-265,268,-122,-373,-484,231,-148,-705,-393,143,1000,516,-430,-914,-630,-589,158,438,443,-69,-1000,-297,428,-1000,-1000,1000,-591,173,284,12,122,1000,497}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitVector():org.apache.commons.math3.linear.OpenMapRealVector",
            new int[]{-289,-505,-337,-771,-199,-107,877,1000,1000,812,626,1000,-332,-946,429,883,194,599,-1000,-696,198,-857,-1000,-355,234,316,-1000,-1000,1000,-332,-1000,-264,1000,1000,-826,908,-44,267,-366,-563,1000,586,-755,1000,-1000,-585,737,725,490,1000,993,-590,-1000,-134,-883,78,808,-667,580,1000,264,-475,49,381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitize():void",
            new int[]{426,-1000,-2,-175,-1000,1000,-712,1000,-1000,1000,1000,354,342,-1000,-1000,-490,-699,-226,1000,-123,1000,320,-270,-662,-374,-901,546,123,779,-847,-705,667,-223,589,120,314,-1000,765,482,650,-1000,-1000,-128,36,-460,-640,-1000,1000,-1000,-420,-90,108,-297,10,-627,-218,-73,-154,-556,-1000,97,912,-557,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitize():void",
            new int[]{-1000,195,-824,-643,-174,946,-66,-1000,-300,-29,-664,-347,35,108,-300,414,-216,755,-928,893,-851,-861,-191,-252,366,1000,-311,-626,-641,265,844,-1000,34,-391,-443,-177,1000,-149,-935,-424,281,-144,31,521,-123,370,141,102,-809,-649,-1000,-325,177,590,351,295,518,-711,402,-73,-514,-192,933,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitize():void",
            new int[]{1000,-624,-2,-175,-397,217,-779,278,-833,681,516,244,92,-1000,-972,-95,-384,5,720,-102,604,653,-285,-828,-113,-109,233,316,-540,-884,-767,-33,-582,790,1000,377,-1000,729,-244,650,-1000,-557,-71,508,-725,-640,-600,761,-370,-67,-48,-356,-626,381,-149,9,-70,-154,-516,-573,-89,324,-480,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitize():void",
            new int[]{-184,-1000,-1000,-954,-528,-1000,-1000,173,-489,-316,528,-122,-642,-1000,-1000,568,737,128,104,-393,189,1000,-302,-1000,221,-721,-536,69,433,-922,892,-569,-1000,1000,-965,911,178,-400,-1000,790,1000,48,-104,1000,-1000,-106,-1000,-1000,1000,291,-57,-821,-1000,-1000,263,544,-815,-900,519,-1000,-475,-600,959,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitize():void",
            new int[]{-139,-902,-531,-1000,652,-627,-850,-822,-738,882,-45,112,-579,-1000,-1000,606,983,295,1000,694,285,821,-664,835,236,870,-1000,585,357,-1000,761,-1000,1000,750,-1000,144,1000,1000,38,548,-1000,54,267,302,-724,-143,462,452,793,791,-190,-1000,-1000,-380,290,1000,5,-293,942,-1000,-278,-289,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.linear.OpenMapRealVector", "org.apache.commons.math3.linear.OpenMapRealVector", "unitize():void",
            new int[]{-621,-189,-1000,-1000,-528,-1000,-1000,-300,-480,-761,48,115,-1000,-728,-1000,211,1000,261,-111,15,-1000,1000,337,164,-218,-230,-1000,54,-384,188,1000,-752,-766,47,-457,1000,199,-1000,-1000,877,1000,578,451,1000,-1000,1000,-1000,-1000,1000,187,706,-340,-733,-739,871,970,-1000,-936,1000,-827,-46,179,1000,1000}));
    }
}
