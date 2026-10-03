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
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "buildArray(org.apache.commons.math3.Field,int,int):java.lang.Object[][]",
            new int[]{-191,828,-214,604,-826,932,17,434,437,668,-847,-516,756,351,447,233,821,-871,681,-650,-268,-312,660,852,738,469,-685,333,956,884,384,-358,746,-254,167,-720,-643,680,-372,-537,636,624,260,335,140,699,552,-891,831,701,628,-925,653,-718,716,337,-203,-557,436,924,207,-568,613,-528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NotPositiveException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkNonNegative(long[]):void",
            new int[]{-416,-409,96,818,-428,52,-399,589,-766,-717,-460,641,-601,39,52,932,373,-994,-303,-147,-111,-473,250,654,-684,445,392,-168,52,988,-396,2,-497,650,-320,-329,-699,256,196,-527,-537,0,-3,960,-876,145,-696,-192,-878,287,400,459,269,-381,-985,-378,-574,-566,-488,-196,-329,323,772,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkNonNegative(long[]):void",
            new int[]{86,714,-989,-368,581,554,426,363,-154,-208,747,486,410,848,-745,96,-989,-389,-420,-499,948,-992,-211,305,-569,-786,-581,-824,-772,-410,-391,-569,330,-669,-411,436,-365,160,698,975,718,912,-144,282,-377,818,-80,-809,341,-537,725,7,-873,965,-208,-878,122,-298,-373,-855,-87,-880,-634,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NotPositiveException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkNonNegative(long[][]):void",
            new int[]{-524,924,-874,210,-204,-769,-612,448,95,-53,-2,125,597,-219,-194,-204,66,123,-173,-814,880,64,-656,913,767,-227,-675,490,727,382,-148,430,-433,26,442,244,-638,623,312,655,-839,596,-88,-531,306,831,-593,162,-169,874,140,506,590,-381,-393,-698,11,-465,945,666,-360,-110,564,-804}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkNonNegative(long[][]):void",
            new int[]{-1000,-1000,720,605,-813,896,-653,-66,-331,954,1000,890,-283,-135,-90,1000,-1000,824,608,1000,-1000,-365,-944,-819,633,-299,431,-785,-144,-123,-1000,-657,-326,14,1000,581,764,-46,267,1000,339,502,229,-796,232,-1000,291,1000,-793,-353,150,-162,70,-355,521,260,-220,-609,1000,-759,66,-910,671,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[]):void",
            new int[]{952,-773,662,572,-25,-267,871,-483,199,-32,270,-931,-929,-275,670,-138,903,951,-695,179,-331,252,225,-788,400,420,815,111,543,653,-9,-629,733,-280,-657,598,285,-759,54,-492,452,-558,778,-876,-46,-723,802,-425,663,668,831,-363,400,727,489,-773,810,321,-738,-595,991,100,14,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[]):void",
            new int[]{-325,-514,-721,-38,-373,-149,573,661,-551,546,-381,-880,541,989,-734,761,-403,-441,-726,976,798,-882,574,928,931,-218,-130,882,813,-96,-355,-594,-480,-186,412,-729,750,729,525,673,-604,-486,302,-469,867,452,-711,-956,76,50,555,430,-919,27,-378,-167,372,-855,317,747,517,250,-826,993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):void",
            new int[]{-80,759,72,890,55,86,246,479,901,-763,-520,-88,300,-682,219,23,452,-578,60,-431,-175,-776,856,-520,979,-999,-466,597,429,244,878,490,171,-232,757,774,-33,610,-443,-313,-887,-104,-15,-324,15,-678,780,196,-369,-50,397,23,590,-413,252,998,-366,206,-251,303,926,547,954,-875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):void",
            new int[]{472,-548,-125,-367,849,506,-978,-419,68,152,72,836,297,112,879,415,228,-46,-493,806,-586,643,-9,-772,290,-201,973,-242,252,-477,621,611,-448,-617,-476,667,334,-465,496,709,-301,-145,652,-204,-681,405,467,-329,-157,631,-906,187,103,-761,684,356,934,-103,-849,812,-642,-393,407,230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):void",
            new int[]{-463,-610,-111,-1000,536,315,-1000,-1000,-395,526,759,1000,401,592,1000,627,-92,574,435,859,483,532,-395,870,-1000,145,993,405,-606,-1000,-1000,-70,-692,68,202,-477,1000,-593,338,-168,-804,461,-1000,-519,-1000,1000,301,530,579,527,-1000,103,457,-685,148,-30,-100,149,-1000,-1000,815,-1000,-1000,812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):void",
            new int[]{1000,145,-337,765,-426,450,1000,1000,1000,-955,-665,-1000,798,121,-923,-884,1000,-354,-366,-1000,-866,1000,1000,-324,804,67,-1000,-454,119,1000,-580,-675,840,-280,1000,-1000,676,784,295,-81,6,-1000,412,-686,-503,-922,-33,-559,1000,-22,-1000,126,8,-139,196,1000,-1000,1000,1000,-250,-496,-373,1000,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):void",
            new int[]{-254,167,198,523,228,331,1000,1000,386,-1000,-290,-704,969,-853,-695,-471,798,-421,-331,-830,-591,662,1000,-1000,481,-187,250,331,-214,1000,1000,-846,848,-752,637,664,-672,1000,-152,11,-295,-536,-137,832,-172,-157,152,-482,-11,-364,337,54,342,-605,-549,767,-465,68,719,1000,6,1000,774,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean,boolean):boolean",
            new int[]{946,1000,-1000,169,-139,-158,-590,-226,-765,953,-1000,1000,-910,127,-362,1000,-276,540,-1000,-1000,-178,-38,-696,-868,204,-527,-1000,-290,303,-166,-198,1000,-1000,-899,-663,-803,112,873,1000,140,932,-206,-124,-1000,484,149,503,-134,-116,10,-38,-1000,443,-234,-1000,837,688,-373,-1000,-106,78,-40,-1000,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean,boolean):boolean",
            new int[]{183,857,672,331,-224,778,-851,403,-925,217,238,755,-391,615,-724,-796,270,-678,737,764,-333,899,112,-300,-524,-32,-125,-958,368,-780,461,49,-68,-314,-484,-622,-635,-10,-374,116,667,908,158,232,546,-706,914,490,-653,-816,-126,-566,899,-274,466,-356,401,-535,-461,-852,199,490,-538,614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean,boolean):boolean",
            new int[]{-94,400,266,68,994,886,-334,-405,-630,76,89,323,-996,258,-595,-80,-309,647,924,-523,96,-171,922,-600,711,-239,-552,278,-622,489,-590,-198,-143,261,-725,46,-727,-699,-370,-165,-761,-846,-686,312,-70,-797,875,313,472,-252,-293,-275,621,840,348,-420,-104,-16,-813,-656,-910,-235,-934,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NonMonotonicSequenceException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean,boolean):boolean",
            new int[]{-435,-428,-449,-348,649,-1000,503,136,653,-736,-662,-923,836,-891,834,69,-788,463,-870,-462,-287,-853,-687,-161,-167,-370,97,429,255,-33,-725,-59,549,-365,1000,589,-849,-558,-12,279,-383,-496,-454,-187,-665,482,-1000,-616,1000,251,532,536,-1000,-189,-466,389,-494,405,882,-99,-674,-670,-952,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkOrder(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean,boolean):boolean",
            new int[]{215,206,-114,632,-746,-220,-3,-648,-1000,704,-326,1000,910,785,-748,130,984,181,-485,46,-871,-404,5,85,1000,97,241,1000,67,764,-361,-630,-1000,298,260,593,-208,-321,197,515,210,302,-463,622,-147,763,647,-641,227,-598,-575,-197,-85,-630,-487,-273,359,-146,-405,1000,771,-1000,-1000,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NotStrictlyPositiveException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkPositive(double[]):void",
            new int[]{806,601,-69,-295,530,-753,-121,15,-883,-438,238,-814,-761,-194,404,87,798,28,245,-139,924,-242,991,-214,-505,-980,-374,-813,-318,-161,19,454,-112,-773,-97,346,820,-511,255,848,-551,-391,636,-893,103,485,596,197,72,786,-600,829,725,-626,6,480,-603,354,477,895,60,80,836,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkPositive(double[]):void",
            new int[]{511,-535,-947,266,687,-869,-790,667,412,136,519,-361,4,-969,-901,-852,121,759,232,-925,-741,725,-695,-689,-635,126,-703,-226,936,580,-100,289,-142,-84,-733,360,-415,-160,817,-999,304,131,518,-983,-806,968,651,358,-971,-914,159,-467,27,543,-592,449,42,-948,409,493,-995,83,379,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkRectangular(long[][]):void",
            new int[]{1000,-583,639,-1000,228,644,531,519,156,123,637,-1000,725,-321,-460,-126,294,-200,-948,-677,639,-10,-412,-340,901,223,-868,-169,130,1000,-611,1000,-537,676,-139,541,-923,-1000,-1000,-702,-367,329,690,-832,-783,777,73,-441,54,-756,-1000,-1000,-34,464,871,-81,853,1000,1000,652,-747,221,-697,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "checkRectangular(long[][]):void",
            new int[]{-1000,-1000,1000,220,-169,-618,-484,-1000,191,133,-650,-1000,944,29,1000,474,-1000,428,189,268,748,-1000,-77,-375,-1000,1000,410,-454,445,-87,-709,-877,881,178,54,-1000,733,553,1000,726,-1000,-605,441,1000,462,-214,-870,293,-5,42,941,1000,-137,1000,-303,-842,-947,1000,-1000,278,-369,-433,742,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("ARRAY:[D:7:29:java.lang.Double:LUluZmluaXR5:29:java.lang.Double:SW5maW5pdHk=:21:java.lang.Double:TmFO:29:java.lang.Double:LUluZmluaXR5:29:java.lang.Double:SW5maW5pdHk=:29:java.lang.Double:LUluZmluaXR5:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "convolve(double[],double[]):double[]",
            new int[]{-735,466,926,-703,-640,-43,288,-37,-409,476,574,-498,85,386,-150,829,467,532,-766,-310,-175,120,638,895,-877,-914,698,-712,139,-898,-141,188,-155,-341,-159,-124,-656,-261,-692,-497,-28,-560,455,-712,-19,-331,453,247,-678,-532,-795,-143,-493,-90,714,46,242,612,-498,662,607,-144,-3,-778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NoDataException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "convolve(double[],double[]):double[]",
            new int[]{-385,416,529,-72,24,-126,-540,-953,628,-909,672,-102,702,777,469,981,-253,-793,769,-761,459,991,336,-888,402,-79,-645,734,-999,395,413,-175,413,-785,186,724,-37,583,-224,-240,342,40,-518,677,-409,-228,55,-784,-25,223,978,76,-680,-730,-842,503,105,366,583,510,669,984,932,351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NoDataException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "convolve(double[],double[]):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "copyOf(double[]):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("ARRAY:[D:0", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "copyOf(double[],int):double[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("ARRAY:[I:0", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "copyOf(int[]):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("ARRAY:[I:0", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "copyOf(int[],int):int[]",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "distance(double[],double[]):double",
            new int[]{981,-552,-758,511,-181,-869,-471,567,354,75,334,-682,-90,-559,116,30,-19,355,-681,-414,61,-687,-152,362,611,895,-720,-269,-917,-789,552,-596,-548,547,431,-961,798,-323,198,925,154,-546,-340,908,-951,-549,884,-807,-60,650,816,-815,110,-484,366,-917,481,88,-537,-386,-713,-788,-924,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDhFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "distance(int[],int[]):double",
            new int[]{-623,-41,-970,-133,-983,248,157,-496,35,-101,314,-874,919,-535,897,-414,-706,535,-74,-707,374,27,837,485,-354,-719,-425,540,6,739,-37,-101,-103,-502,575,-217,-313,290,895,335,427,556,392,-889,479,-791,-627,864,-987,389,907,496,-652,58,971,-277,-743,659,-938,519,-60,-799,-42,722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "distance1(double[],double[]):double",
            new int[]{-124,843,854,26,764,507,152,-340,19,-242,848,-40,-64,781,901,-626,-203,882,-162,57,-920,-650,-934,618,-411,-794,-876,-728,553,246,859,-29,-46,-415,561,49,746,512,-524,-327,449,-124,-195,951,670,457,256,-33,-504,-173,565,154,437,-971,1,495,603,-900,-215,796,157,933,-365,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "distance1(int[],int[]):int",
            new int[]{-347,-998,63,-917,874,-129,-569,-964,370,-442,-1,773,407,-901,902,-98,-119,-630,-201,215,-701,80,321,-485,289,927,544,-147,653,11,-62,672,-235,888,92,834,-523,881,-144,-873,567,-307,516,447,286,747,-917,595,545,168,-119,373,909,-100,209,-505,-286,24,-938,738,340,-957,654,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "distanceInf(double[],double[]):double",
            new int[]{777,-797,117,-618,-616,-428,825,-769,-105,-882,-843,374,-569,-912,-867,330,586,-548,571,864,943,886,-489,-454,475,-429,-508,13,-876,-57,383,88,695,-215,691,-153,408,-853,907,-547,-85,-556,-687,-912,-562,-223,-86,723,-252,58,664,-434,-804,807,1000,62,705,-348,857,-923,702,500,790,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzUyNQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "distanceInf(int[],int[]):int",
            new int[]{472,-738,240,122,299,-208,909,464,-408,701,746,769,-826,-525,-472,105,-338,-900,706,1000,-685,670,768,-87,561,520,615,187,-565,419,381,606,-157,-628,-377,697,337,200,355,-406,946,-791,-994,421,-563,987,-937,944,412,646,898,-467,607,675,-942,-644,-141,984,187,-825,-645,674,652,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[D:5:45:java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=:37:java.lang.Double:Mi4xNDc0ODM2NDhFOQ==:29:java.lang.Double:LUluZmluaXR5:49:java.lang.Double:LTkuMjIzMzcyMDM0NzA3MjkyMkUxOA==:25:java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeAdd(double[],double[]):double[]",
            new int[]{131,-829,-331,542,373,-741,862,-176,-693,-942,147,971,-398,898,-893,579,0,369,488,978,-607,-656,-327,-781,923,17,472,870,365,834,192,-23,821,593,664,122,-165,-754,859,865,-544,944,-933,-895,470,737,213,-147,-941,-987,819,-228,-516,-775,366,-556,32,752,-630,798,-570,-159,-652,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeAdd(double[],double[]):double[]",
            new int[]{-331,-942,-215,-801,589,-770,-618,-376,511,-847,-505,409,66,-624,603,792,-872,260,-57,-707,-497,24,495,925,970,-194,-716,41,-449,-472,-859,-406,-274,753,740,122,-656,-46,-292,195,-585,-326,941,208,668,-138,835,-518,771,-600,134,373,496,261,-734,811,-315,57,671,61,922,833,424,-182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:MS4w:21:java.lang.Double:MC4w:25:java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeDivide(double[],double[]):double[]",
            new int[]{849,-22,437,-998,-778,143,-114,-879,357,809,-714,-795,979,989,538,-998,63,266,893,-947,104,-535,481,350,-425,-216,-589,-832,299,866,-551,826,944,290,877,-670,-18,-10,234,-755,105,-754,-654,-518,-708,-964,305,188,-465,808,853,-604,548,-4,34,-275,714,989,889,-990,979,-322,-522,496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeDivide(double[],double[]):double[]",
            new int[]{530,-748,839,-539,-523,976,525,999,399,460,184,-285,-372,-520,610,-746,-101,-131,185,-472,923,-942,-78,-289,-335,38,153,779,-107,-212,-808,438,-939,-729,944,-500,-292,84,480,-873,202,-4,355,754,-126,-223,-354,929,395,942,534,-129,556,360,-381,-915,-440,-998,-907,37,368,565,406,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:25:java.lang.Double:LTEuMA==:45:java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=:29:java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeMultiply(double[],double[]):double[]",
            new int[]{-753,-419,-659,-38,-478,372,-907,699,-284,-106,773,-510,291,765,371,-393,-434,78,-34,129,-335,-862,274,178,-140,913,-611,143,931,-543,616,892,-140,-680,-345,-392,-184,-913,-329,-85,-281,-472,-203,251,897,-374,488,62,-649,710,-213,336,975,296,75,-782,-314,884,-858,-971,907,914,526,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeMultiply(double[],double[]):double[]",
            new int[]{-353,351,-354,976,-970,524,692,976,115,170,513,395,347,961,630,241,-34,208,505,-22,789,-450,-742,-917,-368,352,811,967,-319,555,543,215,-110,464,-32,752,-957,63,-841,579,-488,-158,706,-12,-68,836,-164,814,31,-405,970,203,680,154,-479,-392,-165,-292,974,313,533,-324,-579,606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[D:2:25:java.lang.Double:LTIuMA==:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeSubtract(double[],double[]):double[]",
            new int[]{-268,76,-130,360,-516,110,-381,-431,736,972,-202,1000,-293,841,880,-104,-333,-717,1000,272,366,-540,674,881,-1000,221,919,542,-884,-264,131,-443,436,-785,-1000,-549,-214,-870,-304,478,-281,192,660,-899,707,1000,811,-283,-859,-683,-992,1000,821,470,-314,726,-245,1000,102,-141,-1000,420,-385,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "ebeSubtract(double[],double[]):double[]",
            new int[]{723,-237,-900,-925,-574,-846,886,-852,489,-326,461,-843,774,360,-154,-117,653,301,784,-920,-747,-155,695,354,817,-506,902,290,-501,-382,268,785,-210,-980,949,284,-523,-393,-679,-216,-461,-74,281,866,710,176,-868,-222,-428,-539,32,-340,-6,503,-407,329,478,-192,645,-217,568,377,391,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equals(double[],double[]):boolean",
            new int[]{554,396,-1000,996,293,98,-1000,-1000,1000,-823,-931,591,-1000,-662,-278,729,-38,879,520,241,-1000,-610,771,9,-91,1000,-140,-132,-1000,-724,1000,-152,-711,-317,-1000,-410,75,17,812,792,1000,-69,-1000,-108,224,-185,-1000,-300,1000,-360,-257,-472,-39,448,366,573,-218,1000,231,111,-471,728,980,-426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equals(double[],double[]):boolean",
            new int[]{680,329,-1000,409,780,242,375,-1000,555,-531,-552,846,-524,-635,-826,-400,-176,584,1000,1000,-1000,-2,234,-574,-823,192,300,225,-20,-1000,1000,-904,-1000,120,-264,-843,-403,-280,1000,605,299,259,-819,109,-168,-398,400,-676,20,-529,595,65,789,-805,222,-316,34,1000,-862,63,-321,280,769,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equals(double[],double[]):boolean",
            new int[]{182,600,-609,106,-791,631,-121,701,106,911,-11,97,-788,293,480,875,120,455,80,908,-840,-960,766,93,-167,780,-649,585,369,-433,914,97,-660,-712,692,260,152,-297,208,968,136,-362,598,-777,-19,-671,-632,-960,252,868,170,-354,-109,434,529,455,758,174,-43,-969,486,-511,381,654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equals(float[],float[]):boolean",
            new int[]{-1000,-1000,-1000,-395,1000,-1000,659,-1000,213,1000,-1000,-115,1000,-270,1000,1000,-941,-291,1000,-1000,1000,-1000,629,778,-426,626,268,-1000,-593,1000,-511,1000,612,-1000,-1000,749,-1000,-1000,-6,-501,340,-570,1000,-1000,1000,84,-627,-1000,240,-1000,-1000,-1000,-1000,-1000,-741,-1000,-657,527,1000,1000,1000,260,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equals(float[],float[]):boolean",
            new int[]{-1000,-1000,-1000,771,-401,-1000,1000,-1000,333,615,-168,-1000,513,-1000,-194,1000,1000,-304,1000,683,-1000,-1000,581,479,98,-103,108,-1000,-1000,-334,-482,473,372,-1000,-1000,1000,55,-59,1000,716,-369,-1000,1000,-890,516,1000,-1000,-1000,1000,-1000,-1000,-900,-1000,-744,-32,-822,-1000,79,1000,1000,939,-361,-1000,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equals(float[],float[]):boolean",
            new int[]{-272,-891,-502,79,812,-786,55,60,-876,-337,-437,-390,103,-624,-577,814,-911,949,365,-268,323,-896,880,-387,916,983,-310,-538,-353,744,-810,705,21,-538,-558,-503,-827,947,479,-890,961,775,441,-191,823,-453,359,631,-138,218,-695,559,10,-845,45,-259,-912,-436,-655,590,139,-146,647,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equalsIncludingNaN(double[],double[]):boolean",
            new int[]{1000,361,1000,-717,-698,-803,1000,-1000,175,604,1000,556,1000,1000,-1000,-1000,-417,699,-357,-835,179,-235,-826,294,-1000,220,401,29,369,518,921,-615,-1000,-627,523,1000,199,-346,-1000,-1000,-1000,-361,-32,-198,176,-506,143,12,-1000,-1000,-1000,-1000,1000,-727,468,-1000,112,-141,389,-356,298,877,609,605}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equalsIncludingNaN(double[],double[]):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equalsIncludingNaN(double[],double[]):boolean",
            new int[]{-887,854,434,-97,650,917,379,-839,-24,843,232,-369,549,703,-612,735,-228,-466,627,-364,-702,518,-993,-572,592,808,-80,571,801,974,-760,548,-768,-405,-34,-336,849,399,-999,182,145,532,793,-404,422,-972,-560,718,117,359,-432,-513,-910,-924,787,-74,40,-40,874,515,-889,-138,935,-612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equalsIncludingNaN(float[],float[]):boolean",
            new int[]{-867,-620,-105,311,-391,-611,805,93,-81,507,-980,647,-87,893,420,-412,26,660,-41,427,-437,-968,68,904,-404,-219,469,-174,-683,-48,608,226,-687,-986,608,64,499,739,910,-875,-826,350,552,303,-940,786,-554,-978,55,268,936,223,918,805,-578,-3,-851,-244,-98,771,-116,83,-83,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equalsIncludingNaN(float[],float[]):boolean",
            new int[]{104,1000,-1000,966,-741,-1000,999,-1000,-1000,603,-637,-1000,-997,1000,1000,-903,-1000,515,1000,333,104,-1000,283,184,-400,299,409,369,1000,479,-442,-119,-1000,-647,1000,1000,155,-309,602,-642,-1000,-1000,-77,729,-1000,-1000,461,-455,-1000,-1000,-352,-1000,62,451,-574,-204,-1000,1000,-469,-449,530,671,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "equalsIncludingNaN(float[],float[]):boolean",
            new int[]{445,-31,-531,-254,-651,-536,135,413,549,-344,74,77,208,54,784,975,355,-836,-741,-706,-396,319,515,256,-318,-930,-525,-621,371,197,-280,-718,-860,-509,268,-231,-56,-352,837,-619,487,855,830,-853,537,927,-316,-190,-151,-767,123,472,667,-933,-576,-692,864,609,783,-259,-681,-836,-415,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-591,993,-449,794,702,-536,-462,-813,-802,386,232,958,-154,35,108,25,628,516,690,-344,976,508,212,337,-433,-660,279,-456,-778,-303,166,528,48,-456,-738,-831,727,-984,266,763,-99,968,252,217,967,604,-536,-482,299,244,811,-407,877,-414,409,-284,637,110,-744,447,-542,190,138,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-231,-1000,-304,899,-821,258,775,368,299,-1000,1000,546,194,35,696,-843,-1000,318,49,130,-220,509,530,337,-1000,219,-1000,-1000,1000,-451,-179,-283,-1000,-1000,931,333,-502,-984,266,-1000,-140,566,-288,-171,-304,-593,-858,-482,265,317,-360,323,589,828,883,44,666,-502,-644,-1000,1000,-1000,-933,-758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-507,-969,606,-785,4,588,-80,-286,810,227,-911,792,620,-451,546,134,-602,-267,-100,445,664,-229,-703,419,-309,-47,177,51,-670,-813,611,-816,-330,-973,-773,-881,-570,-574,900,950,89,-798,822,865,93,-85,-634,64,179,-520,228,-799,-165,-815,745,270,-755,-998,-10,722,657,-916,-728,-731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{387,382,862,350,232,-992,-862,-763,397,819,706,-22,-618,189,-586,377,71,-156,-517,-746,908,-777,220,-494,866,-348,780,189,-819,869,852,-50,239,746,-976,138,782,-792,697,698,619,277,-100,-424,-585,938,132,745,815,98,430,-770,-857,-707,291,-480,-762,987,749,634,587,328,984,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{269,-601,-906,173,-302,948,-540,177,-202,263,-174,660,443,-857,613,503,-671,400,94,-118,956,-1000,425,33,527,161,1000,-1000,939,-590,56,1000,-1000,-479,-1000,-205,-25,59,792,462,911,357,-508,-403,-554,-419,-172,-411,1000,1000,-718,-1000,63,-481,-327,-22,-705,-662,359,393,1000,-435,770,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(java.lang.Comparable[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-487,-288,-570,906,691,867,467,417,518,-171,578,491,415,205,-794,652,-377,691,958,-676,879,-563,-399,594,911,-29,401,717,-639,91,229,167,367,-835,812,732,989,-944,897,-397,-132,-425,-273,-292,93,781,-819,-345,119,-285,-770,-451,-254,216,919,677,670,-608,-982,-377,931,951,669,676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(java.lang.Comparable[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{968,264,953,-865,414,-276,-350,-383,-988,179,-71,238,308,-180,107,-722,680,-551,786,-895,978,949,-869,-475,548,-581,-597,-208,893,-973,764,-284,839,617,-623,-8,985,949,196,320,473,-936,444,-795,-318,745,-204,-599,293,-734,-495,-78,-623,-274,337,-787,639,-870,-941,670,-236,-913,-550,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(java.lang.Comparable[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-290,-883,529,-203,361,-920,900,356,-393,300,857,591,789,764,791,291,-542,-495,468,73,204,424,854,550,-531,-102,80,-898,-793,826,199,-706,862,-187,-605,-316,-891,-42,628,-773,-19,-608,389,-215,635,-856,156,338,-182,-839,450,417,-683,397,970,27,-860,-228,170,-179,-989,-525,-122,697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(java.lang.Comparable[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-254,-55,89,-384,844,696,-323,238,994,246,-317,-520,-487,86,263,-92,-352,103,25,-819,-139,-990,632,312,675,657,-538,435,-427,-454,565,-215,-943,821,221,240,911,-135,-294,-744,948,258,558,339,-908,298,567,-737,-501,-461,11,195,611,-105,-768,-404,-464,713,558,317,931,-57,-215,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "isMonotonic(java.lang.Comparable[],org.apache.commons.math3.util.MathArrays$OrderDirection,boolean):boolean",
            new int[]{-440,-571,-633,1000,-149,621,-1000,-1000,1000,325,959,-710,-872,-18,313,123,-368,371,-1000,-417,-242,-877,-787,-500,-400,10,-622,-1000,458,354,-579,696,-310,95,25,670,401,116,320,709,-1000,-598,259,-4,-424,-834,529,-325,501,-1000,602,672,-440,-150,597,-470,-322,996,-699,114,495,-877,-674,-993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double,double,double,double):double",
            new int[]{901,-268,572,-989,-618,-707,538,-968,-423,189,658,466,-323,129,-655,-886,-967,460,-220,-468,971,-250,547,656,929,-597,956,834,714,-31,196,-746,-85,619,-984,-874,820,-367,191,828,-465,677,-315,771,-569,163,107,-300,-478,-945,-145,805,-49,72,-855,-889,-660,492,32,-583,459,-559,-531,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double,double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double,double,double,double,double,double):double",
            new int[]{934,574,602,-460,830,42,907,-48,-389,-49,-213,-604,474,-881,146,241,-617,-83,276,9,591,735,245,-390,-571,427,-352,356,194,-444,-107,-866,403,248,326,-953,-79,-270,403,-54,583,207,-161,-176,767,-345,-774,-184,114,296,936,691,78,58,316,-871,311,-829,154,-154,-961,-830,-827,675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double,double,double,double,double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double[],double[]):double",
            new int[]{105,58,725,-421,128,-353,56,837,237,-48,995,-845,-294,-94,563,22,340,-998,-556,634,-753,705,843,960,719,472,-619,-792,-698,268,-398,-697,-167,720,24,77,-273,-472,-191,-653,108,647,-863,747,-466,846,-852,-709,997,762,894,971,-356,310,-791,404,-761,-590,589,507,-231,402,240,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double[],double[]):double",
            new int[]{629,-998,248,276,-930,465,663,-900,-98,330,723,-420,203,418,-949,732,-149,-763,-477,-585,227,-962,-184,91,-566,-733,-725,-698,109,-393,201,239,652,0,8,-84,-819,-412,-122,-126,-160,-675,521,-910,-757,194,-584,828,759,385,548,-468,126,605,-959,-457,-558,753,-672,419,-46,-393,545,-56}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM0NzA3MjkyMkUxOA==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "linearCombination(double[],double[]):double",
            new int[]{874,1000,-367,-348,1000,502,-300,-535,-765,-140,-246,-94,872,1000,307,-862,13,1000,-955,649,-590,1000,-523,285,-930,-394,-179,1000,933,321,-200,-931,-43,1000,1000,-992,-24,-458,939,831,269,1000,-400,401,700,792,-402,-627,-761,1000,1000,-1000,-1000,-670,-207,1000,184,1000,300,-243,-523,1000,1000,-66}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("ARRAY:[D:4:25:java.lang.Double:LTAuMA==:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "normalizeArray(double[],double):double[]",
            new int[]{982,365,-560,17,-21,970,779,572,439,-638,576,562,830,-335,556,-515,-385,-599,12,675,-589,-738,809,-601,-404,-488,-369,913,302,509,533,174,476,-100,835,404,-665,-599,-856,-509,-65,-251,-122,-745,-204,-571,22,-949,595,-936,-929,-132,759,982,112,119,-517,427,824,-343,152,-930,-206,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathArithmeticException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "normalizeArray(double[],double):double[]",
            new int[]{1000,365,-221,794,432,-100,348,-773,439,-688,-405,1000,-974,-909,556,-609,-699,17,1000,-831,-325,-899,-795,-2,-789,1000,777,913,479,-1000,-730,1000,946,-357,835,-801,782,234,783,-680,-369,435,-122,-745,1000,-164,22,-1,-1000,-936,-762,-733,-580,247,-219,-501,-968,-208,-802,102,-803,-744,-206,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathIllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "normalizeArray(double[],double):double[]",
            new int[]{1000,1000,487,-1000,-261,-750,-975,960,-73,1000,798,1000,1000,161,726,274,-1000,1000,-301,-705,1000,461,752,1000,51,970,-1000,-576,1000,1000,338,268,1000,1000,44,718,-780,33,-200,-1000,1000,-735,1000,1000,941,-1000,1000,1000,1000,342,213,-225,-179,957,-264,-510,-287,1000,216,-1000,481,630,331,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathIllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "normalizeArray(double[],double):double[]",
            new int[]{-530,-889,25,253,-344,-952,-255,-889,-968,447,703,690,-969,484,39,-652,-824,-134,-518,655,-10,-148,-814,-169,895,-476,774,301,199,-799,-642,-971,-997,-57,-791,-304,524,478,516,-370,687,-455,-728,-582,-886,-350,669,-421,-353,-461,616,362,423,-687,-10,775,-842,35,38,-725,-212,929,-979,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MathIllegalArgumentException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "normalizeArray(double[],double):double[]",
            new int[]{-76,-175,-410,-926,637,371,-315,856,-944,728,799,-524,-47,931,335,37,-959,-67,-517,-18,83,462,570,-333,40,229,121,-233,-501,-186,44,-955,-613,558,-726,-517,-392,223,-17,-437,616,-210,20,554,-783,-167,659,-915,882,-853,-114,-30,749,-7,522,298,129,-823,-755,-865,822,515,-758,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "safeNorm(double[]):double",
            new int[]{47,-754,-597,-329,-156,287,742,669,-88,400,224,-67,-133,-573,590,2,836,-690,734,-328,-400,-1000,844,-896,745,-37,-513,52,390,-99,-151,-318,683,410,792,279,768,-659,744,-215,115,564,-1000,-66,-1000,-65,122,637,91,-352,-466,-985,487,-722,-129,-1000,804,-762,602,884,-637,642,-119,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "safeNorm(double[]):double",
            new int[]{248,-923,291,-107,-540,500,678,-434,-498,-881,-908,-293,-284,103,-935,603,125,-500,-27,-803,-620,-608,273,-316,199,-337,259,959,421,734,-525,-280,-299,-981,-162,667,347,-759,-939,-72,-178,-738,-17,635,47,-390,86,369,786,-486,-354,-866,-891,-82,710,-584,241,-315,424,961,918,9,-896,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "safeNorm(double[]):double",
            new int[]{-737,-526,504,-738,1000,172,1000,897,-251,-1000,-890,-479,-1000,661,1000,-293,103,1000,672,-986,-1000,-497,551,428,798,-158,-881,-237,1000,-750,149,-368,1000,-848,792,1000,-57,-614,-835,-520,30,1000,36,510,-1000,-532,-656,203,1000,1000,507,1000,-450,-235,1000,-1000,343,-1000,102,1000,235,-698,-1000,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "safeNorm(double[]):double",
            new int[]{1000,401,-324,-1000,346,919,735,72,787,878,-903,-369,-1000,-185,859,416,422,-1000,-1000,-1000,-1000,-616,256,-372,-553,1000,1000,930,569,-259,1000,-1000,-509,-875,1000,-1000,-512,-432,641,13,-779,135,-1000,795,-1000,192,-1000,12,1000,-749,-161,-512,-1000,1000,-6,1000,-1000,-935,1000,-112,1000,-1000,-449,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("ARRAY:[D:4:45:java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=:29:java.lang.Double:SW5maW5pdHk=:37:java.lang.Double:Mi4xNDc0ODM2NDdFOQ==:25:java.lang.Double:MjEuOQ==", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "scale(double,double[]):double[]",
            new int[]{997,-167,754,-401,209,-724,716,-534,-21,219,-38,-605,704,904,-957,406,21,985,0,348,-853,888,916,499,-816,-707,-457,-311,471,-111,243,331,-125,-344,500,264,-467,-738,941,40,-64,-135,447,-586,512,-402,-729,-836,-122,-684,300,-890,-193,810,-399,202,-400,-366,217,-106,-951,906,-190,-314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "scaleInPlace(double,double[]):void",
            new int[]{-341,993,-520,-94,924,444,12,-510,-721,416,396,449,250,625,122,641,3,-404,260,558,-210,347,338,-293,367,391,-822,449,951,-386,-894,863,392,-359,-898,563,-101,-576,126,-236,855,-60,-867,350,77,145,-919,235,191,600,616,856,705,588,-917,314,-140,918,-27,-647,332,138,-984,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "sortInPlace(double[],double[][]):void",
            new int[]{110,1000,-573,-1000,582,-5,-1000,1000,-677,-1000,288,-1000,-1000,-322,700,1000,-143,-60,-1000,108,-10,568,135,1000,603,-754,788,-96,-897,-678,-939,658,1000,-816,634,1000,730,-686,-501,188,-1000,-476,219,-1000,-159,1000,-1000,10,-174,-1000,-1000,1000,-24,-400,383,-339,-1000,230,-1000,717,-319,825,-332,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "sortInPlace(double[],double[][]):void",
            new int[]{-403,-982,550,-131,-927,125,-141,-258,-561,452,-295,824,-379,694,-67,-816,-253,-729,290,396,326,261,604,309,-61,467,-542,7,-910,-242,-808,506,-473,-934,612,-824,306,-436,-375,119,126,456,250,821,93,-477,-91,-706,-81,133,-276,-422,-180,758,669,-967,-124,222,-154,136,462,-136,-87,662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "sortInPlace(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,double[][]):void",
            new int[]{153,-396,280,-1000,-85,180,-798,1000,-430,-603,-374,249,-1000,-154,211,1000,243,35,343,475,950,277,-685,215,119,60,-204,980,772,-239,696,1000,362,-670,1000,342,896,-1000,-157,788,582,518,-431,477,-254,813,-374,326,28,853,544,-812,394,-196,-311,964,716,177,180,-226,-283,-995,316,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "sortInPlace(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,double[][]):void",
            new int[]{-184,129,149,-258,-506,993,805,-592,-496,808,992,89,-797,439,818,-508,-865,-188,-67,200,-507,-138,13,-123,-528,-546,-490,-791,553,-656,255,969,929,481,18,321,-486,795,-422,-106,290,942,-493,994,-908,-513,455,852,-634,-573,-540,801,920,-895,-431,-120,223,-887,892,544,453,-971,69,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math3.util.MathArrays", "", "sortInPlace(double[],org.apache.commons.math3.util.MathArrays$OrderDirection,double[][]):void",
            new int[]{320,569,44,-696,-487,42,-356,944,-163,585,54,714,713,-274,-259,456,779,-60,749,-655,461,324,-724,-739,666,-154,662,-700,-770,568,476,634,-80,-496,895,706,-963,-426,-961,104,-49,-488,-474,357,285,-498,-836,368,-144,-167,-623,-767,-7,-65,-250,-204,392,757,-95,444,279,671,791,349}));
    }
}
