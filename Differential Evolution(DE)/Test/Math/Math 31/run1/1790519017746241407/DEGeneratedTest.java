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
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.distribution.BinomialDistribution", "org.apache.commons.math3.distribution.BinomialDistribution", "cumulativeProbability(int):double",
            new int[]{194,-602,-761,-347,632,768,489,-1000,727,1000,-247,885,1000,223,357,-229,1000,764,530,-233,-1000,-590,-945,661,-714,109,105,-1000,1000,400,1000,525,-32,1000,913,623,208,-1000,-203,-568,99,-766,240,-429,-1000,1000,743,-400,-1000,21,786,-477,532,-924,810,1000,-1000,1000,-1000,1000,867,85,251,842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.distribution.ChiSquaredDistribution", "org.apache.commons.math3.distribution.ChiSquaredDistribution", "cumulativeProbability(double):double",
            new int[]{-215,172,350,929,-79,-341,-976,250,1000,457,-1000,-444,-15,-414,96,-1000,-117,-1000,666,984,-1000,8,377,330,-920,104,-131,-468,-394,-1000,-656,-463,-67,1000,-56,-1000,-207,-84,-878,730,-397,507,-1000,-152,-1000,-818,13,1000,-181,1000,1000,-149,552,-706,-926,-140,1000,77,429,618,589,1000,616,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.distribution.ChiSquaredDistribution", "org.apache.commons.math3.distribution.ChiSquaredDistribution", "cumulativeProbability(double):double",
            new int[]{-533,121,-475,661,-555,-1000,-1000,1000,886,1000,53,21,185,-1000,289,-645,687,113,1000,1000,10,-408,1000,-175,-619,523,-1000,457,-328,-1000,-1000,1000,301,1000,714,-1000,109,965,-1000,-58,632,1000,-1000,1000,-858,-1000,-336,353,58,677,1000,-875,1000,-794,-1000,53,625,394,-318,198,-936,1000,554,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk5OTk5OTk0MTYzODA3", DEReplay.run(
            "org.apache.commons.math3.distribution.ChiSquaredDistribution", "org.apache.commons.math3.distribution.ChiSquaredDistribution", "cumulativeProbability(double):double",
            new int[]{1000,-326,-115,497,-1000,-316,809,412,-37,184,780,95,-1000,-750,-1000,1000,-1000,-1000,1000,1000,-342,1000,-697,255,-1000,-547,114,-1000,-564,534,-775,-915,1000,-1000,-1000,1000,535,1000,1000,-611,-487,-739,-262,-150,1000,1000,852,-949,-57,16,-575,-240,1000,-744,17,-794,-24,-675,1000,-1000,1000,128,188,-916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.distribution.FDistribution", "org.apache.commons.math3.distribution.FDistribution", "cumulativeProbability(double):double",
            new int[]{-977,-812,1000,-645,565,238,-1000,846,-265,-931,62,302,-1000,1000,-488,1000,-100,513,-1000,-153,-1000,1000,1000,595,-1000,289,-1000,-475,915,137,1000,836,-282,-29,-1000,-462,-1000,428,1000,1000,1000,663,-694,-555,-788,-1000,-870,-1000,1000,-749,635,-820,348,-573,-1000,-1000,427,-695,1000,136,-93,1000,-223,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.distribution.FDistribution", "org.apache.commons.math3.distribution.FDistribution", "cumulativeProbability(double):double",
            new int[]{-364,-446,-291,329,1000,718,-535,877,-115,46,-657,-745,-1000,1000,625,1000,-1000,1000,1000,285,-1000,-742,435,-41,-1000,-821,-1000,72,1000,-1000,100,-128,-1000,-1000,-1000,55,-351,693,1000,259,-34,-100,-865,948,261,421,698,1000,616,-1,-393,-334,-1000,-543,828,1000,1000,-1000,-1000,-1000,247,207,-1000,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.distribution.GammaDistribution", "org.apache.commons.math3.distribution.GammaDistribution", "cumulativeProbability(double):double",
            new int[]{-451,838,-229,-777,637,281,-165,-55,-964,-473,748,-843,-845,-428,-912,232,935,-188,-454,-551,105,-936,4,-713,253,941,-662,-118,-980,-209,-38,-982,633,-155,219,-21,-138,223,-995,113,501,5,201,-938,528,710,-122,283,190,207,608,680,446,340,659,971,-565,364,-607,-252,-68,-346,-248,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.distribution.GammaDistribution", "org.apache.commons.math3.distribution.GammaDistribution", "cumulativeProbability(double):double",
            new int[]{-1000,1000,250,-923,1000,795,-1000,1000,185,-339,672,-131,-468,1000,-1000,-1000,-478,819,1000,1000,298,386,1000,575,-1000,712,1000,-1000,-1000,1000,1000,139,575,921,-525,-274,-1000,-580,-295,-879,-562,845,130,1000,1000,-414,938,-22,825,1000,-835,-771,499,908,-1000,1000,-168,-510,-913,1000,545,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk5NzgzNjkxMzgzMzUy", DEReplay.run(
            "org.apache.commons.math3.distribution.LogNormalDistribution", "org.apache.commons.math3.distribution.LogNormalDistribution", "cumulativeProbability(double):double",
            new int[]{-992,105,446,597,154,-972,922,-38,-570,49,-769,943,-121,-213,149,319,-554,-687,282,-380,595,232,32,838,160,106,872,634,-422,-799,-340,-198,197,89,-648,566,-438,265,651,199,257,-416,-114,-623,-792,-915,743,-269,147,-441,-683,-887,-596,-941,-570,-736,-888,527,125,740,467,-376,-153,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wMTc1OTIyMDk0MTI3NzM3RS0xMDI=", DEReplay.run(
            "org.apache.commons.math3.distribution.LogNormalDistribution", "org.apache.commons.math3.distribution.LogNormalDistribution", "cumulativeProbability(double,double):double",
            new int[]{-989,612,866,407,-345,-900,776,-318,-881,-395,-777,896,324,285,-274,-478,83,-460,-704,-571,229,-551,9,646,294,219,792,-141,53,-978,-98,825,-974,-757,398,855,-724,-71,150,538,-298,8,871,956,433,-293,-958,-241,900,542,-353,725,695,-782,-833,643,415,-177,-579,726,367,853,547,-873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45NzcyNDk4NjgwNTE4MjA5", DEReplay.run(
            "org.apache.commons.math3.distribution.NormalDistribution", "org.apache.commons.math3.distribution.NormalDistribution", "cumulativeProbability(double):double",
            new int[]{-106,-436,400,494,-1000,-275,-1000,1000,126,-785,-887,-1000,889,-163,-1000,-760,-1000,406,-1000,1000,-1000,1000,-965,1000,-1000,-115,885,590,1000,-1000,-228,-1000,314,464,-437,182,465,1000,885,120,1000,106,-346,135,-84,-457,1000,-405,-1000,-44,-1000,-1000,869,814,-1000,-966,-671,-1000,-114,262,-721,-49,-1000,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.distribution.NormalDistribution", "org.apache.commons.math3.distribution.NormalDistribution", "cumulativeProbability(double,double):double",
            new int[]{1000,309,-551,-20,-805,-413,-893,120,803,-65,713,-649,-15,208,-103,127,-692,450,-315,-960,770,533,-724,-157,526,-49,870,-1000,404,-1000,-213,-811,20,698,-130,-339,343,-281,-402,-744,848,1000,-229,238,-1000,-45,1000,737,1000,133,238,1000,-345,-938,341,-665,642,1000,-171,-692,909,319,-131,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.distribution.PascalDistribution", "org.apache.commons.math3.distribution.PascalDistribution", "cumulativeProbability(int):double",
            new int[]{-827,248,272,635,-812,924,337,304,234,-279,303,921,-618,-327,250,-400,-488,-300,-577,-243,-299,-949,-43,368,-733,-654,430,-387,901,-457,-791,-641,-863,768,78,-449,-810,122,269,-227,114,-736,-303,-998,-817,880,-102,-132,-594,-12,25,636,-825,-574,-939,561,190,-146,379,240,998,127,728,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.distribution.PoissonDistribution", "org.apache.commons.math3.distribution.PoissonDistribution", "cumulativeProbability(int):double",
            new int[]{444,877,1000,-662,-760,-557,-293,1000,192,-133,-1000,-951,1000,-470,161,-1000,235,-1000,-175,-1000,920,-1000,1000,-939,764,1000,-1000,1000,-85,751,1000,-960,391,1000,-1000,-1000,-209,-1000,-159,294,1000,-1000,-1000,806,-29,281,-382,1000,364,-761,-1000,460,178,-1000,1000,825,918,660,698,177,1000,454,-462,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.distribution.PoissonDistribution", "org.apache.commons.math3.distribution.PoissonDistribution", "cumulativeProbability(int):double",
            new int[]{-839,-209,-475,152,868,-254,768,613,147,-541,-437,-553,283,-550,399,-772,185,969,705,-439,811,983,-117,497,-185,842,-125,324,333,373,-483,-487,-343,445,295,-452,780,90,636,-940,239,-832,-176,250,-470,242,792,-64,456,-124,-383,-287,-777,-168,335,717,-169,848,884,239,376,-443,-582,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.distribution.PoissonDistribution", "org.apache.commons.math3.distribution.PoissonDistribution", "cumulativeProbability(int):double",
            new int[]{499,-808,-602,368,-993,363,-606,-916,984,-596,-788,373,-431,-525,172,954,-627,-416,-397,-20,920,-299,614,218,-732,246,-833,-111,-85,388,962,646,935,879,-125,86,-511,204,-687,924,-633,-289,-263,-508,-29,98,-194,-417,340,116,-850,-173,178,706,-420,-240,-355,-113,725,-460,698,-323,-462,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4yNzc1MDMwNjE3NTQzNDJFLTMwMw==", DEReplay.run(
            "org.apache.commons.math3.distribution.PoissonDistribution", "org.apache.commons.math3.distribution.PoissonDistribution", "cumulativeProbability(int):double",
            new int[]{1000,1000,799,875,510,236,-854,-1000,184,-395,459,-293,506,-873,-1000,414,-1000,-1000,-1000,-179,-1000,-530,-309,-854,-175,-1000,-271,510,750,-1000,400,-1000,-125,1000,-44,414,565,1000,-1000,839,-1000,-1000,1000,-1000,-1000,-1000,-494,350,210,-1000,-1000,1000,-550,-83,428,23,660,-98,265,-1000,1000,-985,-879,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4xNTk1NzExNTcxMDI0MkUtOQ==", DEReplay.run(
            "org.apache.commons.math3.distribution.PoissonDistribution", "org.apache.commons.math3.distribution.PoissonDistribution", "normalApproximateProbability(int):double",
            new int[]{-932,633,346,-854,-572,-980,-1000,449,673,458,-694,-1000,717,979,1000,-138,-938,357,-13,220,959,-24,1000,-364,1000,-338,-404,-827,400,-173,733,-692,105,1000,-451,-419,-925,-231,-1000,372,-352,4,-259,630,80,466,129,-95,702,1000,-1000,1000,46,-247,-108,357,205,1000,239,-705,400,969,-1000,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:MC43NTAwMDAwMDAwMDAwMDAx", DEReplay.run(
            "org.apache.commons.math3.distribution.TDistribution", "org.apache.commons.math3.distribution.TDistribution", "cumulativeProbability(double):double",
            new int[]{404,-349,176,97,-902,301,-1000,-1000,-515,-253,607,300,-772,137,-477,-313,-977,-476,645,1000,358,1000,-947,-356,798,13,853,2,-1000,97,-284,-791,887,1000,-810,18,898,-586,203,201,700,-287,-455,-446,104,139,632,-245,-1000,279,1000,348,-461,-1000,298,3,-1000,-1000,-387,925,1000,970,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math3.distribution.TDistribution", "org.apache.commons.math3.distribution.TDistribution", "cumulativeProbability(double):double",
            new int[]{28,596,-690,905,-555,-766,-539,284,899,-21,962,-948,-579,267,-304,271,924,-514,-734,336,272,-973,216,-673,-896,-418,38,-687,-65,17,-121,-459,-131,-968,940,-113,-897,-757,712,870,320,-655,398,110,-904,-444,116,-833,436,689,-144,-614,706,-126,-514,579,-596,214,635,151,-594,-531,739,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double):double",
            new int[]{406,420,-450,901,-186,-983,786,400,729,9,-544,355,-1000,265,472,-347,66,-399,-944,-1000,-601,-1000,514,-981,-728,-620,466,-111,-1000,1000,306,975,1000,879,405,-419,110,674,1000,104,-1000,-559,161,1000,-1000,-1000,1000,849,780,134,-502,-789,-586,-718,967,859,-716,-98,-1000,323,1000,-146,-177,993}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double):double",
            new int[]{849,168,-1000,-1000,1000,411,-754,-1000,828,-321,-193,-1000,1000,-1000,108,-1000,915,-1000,407,1000,568,-949,-968,1000,-997,-1000,-1000,-414,810,-1000,-528,1000,-839,-153,532,-391,542,-827,163,-824,-633,-1000,-1000,-312,735,550,-1000,1000,224,-356,-709,288,1000,-973,467,1000,-601,939,248,1000,1000,-1000,641,-888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,double):double",
            new int[]{-412,960,766,694,845,87,-864,1000,436,-629,-1000,789,-1000,895,107,-542,-52,-13,-940,-1000,1000,-975,115,422,439,-103,581,-818,-451,95,13,-144,428,-91,-570,-1000,481,577,668,1000,-1000,-218,-805,-1000,1000,1000,-414,-323,-547,-371,1000,-580,-626,-914,802,516,1000,-967,534,-824,-394,474,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,double):double",
            new int[]{-1000,924,486,238,839,-1000,-770,711,-294,-682,-371,-339,-1000,-964,1000,1000,-1000,1000,906,-79,-436,8,-608,668,455,399,-253,396,978,770,-458,584,1000,-909,627,-1000,-73,-755,-683,804,-837,1000,-1000,-415,-1000,1000,-35,-1000,-502,1000,-562,1000,-1000,-1000,-1000,-606,-394,-838,73,-522,354,-400,955,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,double,int):double",
            new int[]{-1000,565,814,145,-344,161,1000,-192,-527,152,601,166,-395,683,189,-478,1000,-1000,-510,-127,-398,101,-1000,459,-193,-1000,-664,186,-91,1000,-252,-143,305,217,275,389,493,-818,672,-400,-565,443,-898,585,-1000,36,-612,-905,470,-707,-1000,610,865,1000,-219,-314,1000,-1000,-407,-328,-161,-764,-192,802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,double,int):double",
            new int[]{-362,85,1000,497,772,841,1000,946,254,-1000,1000,488,319,947,666,164,215,-1000,-1000,-902,1000,-239,-227,-1000,-1000,-1000,-1000,1000,-153,1000,-597,-939,-116,-708,-1000,-911,-1000,-901,659,286,-1000,908,224,689,-898,600,-617,-1000,462,750,-715,-911,1000,1000,-832,-1000,1000,-546,-670,-1000,1000,1000,69,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,double,int):double",
            new int[]{231,228,-1000,-117,-1000,-1000,697,-1000,-1000,512,-900,-530,-272,362,86,-1000,782,843,1000,419,-1000,-1000,-938,478,875,1000,659,-1000,-500,-1000,676,1000,705,1000,885,-66,1000,-947,500,-501,618,687,-86,622,681,590,185,1000,-225,-946,1000,213,69,-648,628,1000,-502,549,908,889,-1000,-1000,520,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,int):double",
            new int[]{876,876,1000,37,263,-105,713,-256,1000,334,-1000,-1000,-503,243,423,231,-494,716,-114,1000,554,345,-88,-722,808,-452,64,851,-1000,-424,-269,-1000,416,131,442,-491,991,-304,631,370,-776,415,1000,529,-563,416,770,-70,762,-119,923,102,-357,-848,518,-435,-835,952,-222,-721,984,-1000,930,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,int):double",
            new int[]{1000,876,1000,-1000,-1000,476,-478,261,1000,238,-369,206,-918,-470,905,673,-501,22,-1000,981,207,-76,1000,-459,-606,-571,684,-1000,-845,-530,-348,-884,-960,-985,513,-83,318,-622,-284,1000,-152,1000,925,400,937,-747,949,867,-419,-312,225,1000,-456,-528,-535,-1000,-863,-931,226,-853,571,1000,-1000,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Beta", "", "regularizedBeta(double,double,double,int):double",
            new int[]{880,984,370,301,-1000,20,196,-1000,1000,502,411,-1000,-1000,-1000,-492,997,-1000,909,-715,1000,1000,429,226,-55,289,-745,1000,-1000,-1000,-136,505,-631,417,793,832,-1000,-469,-1000,621,-1000,-1000,652,1000,481,-567,785,1000,-350,77,-736,501,674,-336,-1000,1000,-1,-1000,659,-1000,-1000,712,-236,562,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math3.special.Erf", "", "erf(double):double",
            new int[]{-7,-937,-812,990,-788,-931,124,27,416,-584,265,632,175,610,-110,-460,-182,-435,818,571,248,882,652,-584,-510,243,81,-378,-443,-440,-646,404,789,-290,941,-306,536,420,331,-933,760,202,-41,-931,-53,102,307,-144,585,-521,-599,-963,364,6,-910,141,-731,-311,-203,-777,-229,-214,743,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.special.Erf", "", "erf(double,double):double",
            new int[]{-168,223,359,-206,636,653,281,-142,-610,383,-122,-702,-122,577,-953,-269,180,-767,-474,-249,-220,-544,765,161,624,269,765,94,257,-951,780,-238,994,-148,-577,745,982,-402,-649,-497,951,371,478,73,487,936,-568,-157,-279,-4,-135,-326,483,-354,108,696,-826,74,-298,638,-126,-413,324,677}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Double:NS4zOTU4NjU2MTE2MDc4MjA1RS0xNzY=", DEReplay.run(
            "org.apache.commons.math3.special.Erf", "", "erfc(double):double",
            new int[]{20,671,327,-293,746,-703,-214,916,438,-327,577,520,688,-711,936,349,-500,-814,858,-135,881,915,-426,-363,-513,385,456,122,-421,90,-729,-709,846,342,225,-870,-134,580,561,-80,906,-463,597,336,-397,133,60,865,-364,-672,-694,-181,-88,828,-394,-683,67,-448,60,999,-952,-919,-109,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaP(double,double):double",
            new int[]{380,-657,-840,248,-266,-305,675,248,1000,502,461,-828,-116,-535,-344,-346,-1000,-261,55,-1000,207,-1000,-1000,-279,292,1000,-701,-1000,-1000,712,-262,511,940,-1000,-1000,867,-694,549,71,289,105,281,7,45,-159,-156,177,-84,189,52,246,563,-351,-1000,-703,390,643,-1000,719,743,-143,-870,-780,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk5OTkyMjMwNDIwNDA0", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaP(double,double):double",
            new int[]{373,142,743,-794,-1000,1000,1000,-882,-1000,3,-874,-216,1000,1000,868,-163,122,1000,1000,-606,1000,1000,1000,1000,-1000,964,1000,-262,-426,-217,1000,-1000,-1000,-843,1000,-745,296,-1000,64,713,-1000,-528,940,-168,1000,-1000,-1000,-190,-1,-1000,-719,930,-502,1000,-1000,700,1000,831,-533,-1000,1000,-1000,1000,-622}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:MC45OTk5OTk5OTk1ODczMTg4", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaP(double,double):double",
            new int[]{-684,293,-1000,701,997,-557,1000,97,166,502,682,-1000,-116,-1000,-1000,-346,-1000,534,-637,653,-119,-171,363,-1000,220,-566,-1000,63,957,712,-1000,523,438,1000,-1000,330,-1000,1000,1000,-128,-237,358,1000,-364,-614,453,601,550,153,-302,-174,-762,-433,-1000,532,870,402,-1000,746,1000,-756,-650,-780,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaP(double,double,double,int):double",
            new int[]{-606,-813,804,-1000,907,-608,-296,807,-1000,-240,934,-33,1000,409,1000,-740,-1000,529,-483,434,-1000,182,-1000,1000,345,-783,260,598,-106,-1000,-834,-632,-150,-1000,442,-202,218,-287,1000,-126,604,1000,-674,-443,692,266,-1000,-865,983,451,939,-1000,971,959,956,-400,-678,190,1000,-1000,-1000,381,291,654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaP(double,double,double,int):double",
            new int[]{26,63,-1000,-1000,705,1000,862,1000,-54,-1000,1000,1000,-576,-232,1000,-71,1000,-65,-66,1000,-222,197,201,224,-635,618,-674,-37,835,203,102,-1000,1000,-693,-1000,-783,703,776,-1000,-446,-1000,450,1000,-804,-368,386,-1000,-1000,472,511,-582,-632,-217,-550,765,696,-1000,-270,1000,-225,-383,-61,958,-885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaP(double,double,double,int):double",
            new int[]{-1000,-1000,1000,-1000,296,202,40,527,-1000,-950,622,-475,1000,648,457,-955,-1000,575,194,392,-1000,1000,-589,-405,978,638,225,634,965,510,191,331,-946,1000,-134,-756,869,-30,1000,201,-550,910,822,632,-741,-337,-1000,332,712,128,-889,557,689,1000,269,-1000,900,-663,-1000,-981,-1000,-564,1000,-495}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaQ(double,double):double",
            new int[]{-648,809,138,-292,-298,-1000,-542,-1000,336,1000,1000,-1000,410,557,524,1000,1000,517,894,1000,-1000,-92,-121,-933,-1000,-45,-384,1000,-60,-104,452,53,1000,904,-34,-1000,573,617,1000,238,-422,-534,-298,965,55,-137,-256,1000,-265,-504,504,1000,-245,-1000,45,1000,1000,-250,1000,1000,413,1000,1000,-718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaQ(double,double):double",
            new int[]{383,-602,-314,-319,-1000,1000,-1000,445,683,-963,57,1000,121,667,-240,-408,-371,467,-120,-83,-812,961,123,-126,-4,-1000,859,1000,98,-1000,937,-908,-115,-290,1000,719,-525,-1000,-437,170,-739,158,532,-1000,-558,1000,694,129,-42,-538,-487,640,573,-1000,994,-1000,1000,-1000,481,319,181,1000,-720,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.MaxCountExceededException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaQ(double,double,double,int):double",
            new int[]{-140,-33,-621,989,-112,-152,-557,812,180,-1000,-311,-579,363,254,1000,592,-373,-765,-166,-43,743,440,347,169,36,163,538,-1000,-298,-715,743,-327,670,-586,250,653,-97,-198,-343,889,-1000,-840,34,-297,-90,-84,-829,131,-791,-55,-337,-435,255,-452,52,-823,874,1000,-913,508,492,649,402,113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4wRTUw", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaQ(double,double,double,int):double",
            new int[]{166,-55,-922,989,-664,-811,-557,812,180,-1000,-311,-201,835,-831,580,-426,16,-983,-52,-220,1000,827,-353,782,21,121,764,73,-118,-563,886,-89,-808,-288,939,391,262,-198,-245,253,158,-1000,-1000,-580,-193,385,-2,-395,-787,501,-337,-1000,1000,-276,-1000,-823,-295,-1000,-173,138,495,1000,154,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.ConvergenceException", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaQ(double,double,double,int):double",
            new int[]{-306,-635,-238,-688,-172,59,272,166,1000,-293,471,760,1000,-52,862,-96,-946,-65,38,-458,-1000,1000,1000,949,-539,266,1000,1000,1000,28,-440,-330,557,-1000,1000,-631,199,696,1000,-219,-1000,-498,288,105,1000,218,-1000,113,-1000,-73,-935,-162,-920,62,-840,-331,-80,1000,154,753,1000,1000,248,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.special.Gamma", "", "regularizedGammaQ(double,double,double,int):double",
            new int[]{765,814,-1000,-679,1000,-1000,-496,-213,117,1000,-654,-1000,693,1000,1000,-834,-1000,1000,-208,1000,561,1000,446,-67,470,-706,1000,678,1000,506,961,-1000,1000,133,1000,-964,464,-793,887,1000,400,-537,-1000,-1000,531,30,-358,-1000,-295,755,654,1000,669,704,-1000,1000,657,-485,-91,-132,-1000,-886,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.stat.inference.ChiSquareTest", "org.apache.commons.math3.stat.inference.ChiSquareTest", "chiSquareTestDataSetsComparison(long[],long[]):double",
            new int[]{1000,-875,-439,968,-968,-1000,389,-583,608,-314,-1000,-893,745,560,541,754,-44,89,975,-21,333,-548,-50,1000,1000,-537,529,-382,698,-240,-491,1000,-181,-1000,-1000,755,-1000,346,-678,-83,-769,203,-209,447,134,150,-246,-1000,154,-400,-1000,-939,1000,-372,760,4,703,467,1000,129,192,435,629,416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4wNjQwNzc1MDY0NTEwNTk0Nw==", DEReplay.run(
            "org.apache.commons.math3.stat.inference.MannWhitneyUTest", "org.apache.commons.math3.stat.inference.MannWhitneyUTest", "mannWhitneyUTest(double[],double[]):double",
            new int[]{367,641,787,582,-1000,436,1000,-1000,-155,-206,1000,-1000,1000,-391,-1000,27,84,-446,562,494,1000,184,-677,-1000,288,315,-1000,1000,-379,275,-792,892,-345,-501,1000,619,-46,-884,-625,1000,-120,926,-1000,869,414,479,-1000,969,337,26,-703,514,-1000,324,-125,346,454,1000,180,-1000,-236,823,1000,-1000}));
    }
}
