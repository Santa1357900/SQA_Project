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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{678,529,-348,119,1000,1000,-422,-213,-199,-134,1000,-1000,-1000,1000,-880,-629,697,-275,1000,298,-120,-572,758,-453,-769,355,-1000,-1000,953,-890,578,301,-1000,508,310,1000,207,-356,-547,-644,-1000,396,409,-1000,1000,751,1000,-1000,333,-1000,-755,-697,-525,-747,-535,401,1000,-786,-508,-1000,1000,7,-809,-25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{584,657,291,-789,744,-57,572,-261,186,-211,80,-241,-707,68,-784,303,624,702,-760,656,-169,-556,195,1000,402,90,-290,261,-857,95,207,-655,-434,184,-235,-239,-782,-643,-526,-18,881,794,609,-6,993,316,844,-1000,-794,609,-160,-239,-230,-1000,739,993,-1000,196,990,171,-1000,-401,316,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{-966,350,1000,-173,90,-607,-432,495,146,-273,-348,-482,777,-1000,-1000,-296,1000,1000,-532,-581,1000,668,-1000,116,731,-69,432,1000,60,1000,-887,105,-651,1000,1000,134,-116,548,-764,-1000,314,506,-449,712,76,73,345,225,-884,-797,-96,-1000,240,1000,-146,32,-233,625,-125,-295,587,1000,192,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{489,784,716,1000,407,387,-397,99,571,-361,225,-519,-197,-861,-688,9,1000,1000,-327,-31,569,475,-990,732,630,267,169,175,187,1000,-608,-293,-1000,1000,757,-123,-226,-2,-505,-1000,612,762,-110,-61,388,25,621,-83,-1000,-307,-381,-958,65,1000,200,560,-230,223,438,-504,-177,253,55,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{712,657,291,-108,14,271,388,-423,366,-213,-872,-282,-1000,1000,378,493,-668,-1000,981,479,-1000,560,153,-1000,-528,342,1000,-1000,1000,-1000,207,459,-437,-845,-831,-1000,952,-1000,590,-738,-902,794,1000,-108,993,-1000,844,-1000,-571,932,-506,-1000,-1000,-755,485,406,894,881,1000,-272,-1000,653,859,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{-129,-293,1000,-507,1000,1000,-907,426,325,211,1000,-1000,132,1000,-1000,-289,1000,454,174,-525,1000,-140,-1000,348,-1000,352,397,-735,1000,44,1000,-903,665,941,1000,1000,1000,1000,-60,-753,-1000,401,-144,-1000,676,220,-376,-666,83,-1000,-1000,-75,-501,-726,-1000,-282,417,-563,-1000,-1000,-422,-1000,-340,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{345,486,276,1000,-136,300,308,-1000,-485,315,-1000,-365,-300,1000,-941,221,270,-1000,613,300,-560,-182,620,-1000,0,408,790,-300,137,-213,493,1000,-300,-1000,-258,-255,-763,-787,-311,-123,-785,914,494,-355,300,-1000,1000,-594,541,-208,1000,-911,-1000,-356,226,726,1,1000,772,136,634,1000,-660,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{173,-141,952,-400,-417,-468,97,435,160,-421,-46,347,-315,410,561,15,885,638,-642,-373,-99,884,-888,616,1000,-66,-750,-357,465,1000,390,-794,-288,364,-249,-1000,1000,-111,513,-619,755,378,486,722,114,-584,-489,444,-267,1000,-601,-169,278,-387,510,361,-567,67,260,192,-337,-118,1000,367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "abs():org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,601,1000,1000,-1000,387,-638,299,571,-124,-803,-999,-1000,-861,-688,597,-133,-114,-1000,1000,-1000,-674,1000,732,647,340,-1000,175,187,-1000,-267,574,-1000,-1000,-563,-935,-966,-1000,-849,1000,1000,133,1000,-449,823,25,621,489,895,1000,884,1000,-371,327,1000,1000,-1000,223,657,797,-1000,761,552,571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{916,-356,138,406,-879,-92,-761,-641,36,283,-971,290,3,-1000,1000,610,-371,-725,1000,626,732,-1000,257,794,-298,1000,1000,-420,-959,636,1000,308,981,741,53,1000,550,29,-819,418,-873,653,1000,-645,-332,1000,-1000,857,-376,-160,-422,-25,43,349,-543,436,-1000,743,-804,234,-185,-200,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{921,-1000,619,1000,-852,-789,-1000,-299,-891,-133,-1000,-378,-499,-1000,-124,1000,-431,-501,410,-394,349,-566,401,846,-997,896,-33,-761,-181,-622,1000,-1000,718,446,1000,-930,1000,372,-447,659,135,-307,-145,-453,-46,-630,-218,1000,-717,-180,362,-924,653,571,27,-234,-1000,1000,805,-373,-189,-218,325,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{302,-1000,-457,-315,-572,-1000,40,-710,448,1000,-513,-644,-53,-1000,1000,1000,-481,-493,738,211,1000,-1000,540,1000,683,361,1000,1000,-1000,168,703,1000,446,0,-446,1000,1000,1000,56,12,3,460,1000,344,-66,777,-166,155,516,586,-79,708,835,-703,-65,558,-211,-160,-1000,-453,-430,916,973,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{-474,506,451,-585,613,398,323,906,342,-359,359,919,1,188,-201,148,508,549,-897,-1000,437,32,-376,415,-6,-453,-599,735,450,174,1000,794,-615,643,-642,436,-943,-725,105,-149,-23,-746,-25,1000,-576,45,-634,20,627,1000,1000,-708,-65,233,529,-339,-226,-118,647,681,-578,739,-240,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{273,-1000,1000,-431,-98,-1000,-387,265,144,123,-891,871,-96,-1000,315,1000,-330,-605,281,-831,791,-1000,565,820,299,939,841,288,-543,129,1000,20,239,1000,207,1000,43,-123,-747,26,672,129,1000,-61,-534,-375,-1000,1000,267,392,179,315,1000,357,199,77,-1000,665,-620,50,-327,780,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{404,-1000,825,158,-83,-1000,-574,-93,-271,267,-715,46,-299,-1000,597,1000,-606,-616,583,180,224,-1000,412,781,-63,912,906,-587,-176,487,611,-400,765,595,236,1000,280,-150,-28,370,432,-114,625,-555,-427,177,-574,994,197,-181,-279,-94,697,173,529,457,-424,376,-777,-382,-604,47,462,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{306,-755,1000,-1000,676,155,171,-462,1000,-363,-96,959,140,-662,315,1000,249,-560,-214,-476,1000,-518,752,191,-588,939,151,256,602,443,1000,1000,835,1000,-65,1000,-1000,-779,-692,56,535,-329,748,37,-927,-846,-1000,741,236,687,668,-891,846,827,579,77,-1000,-153,-620,433,-327,683,1000,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{-545,-398,-517,-921,253,-409,204,265,-65,277,424,871,-96,-970,1000,584,60,47,-40,-831,772,-637,57,1000,-374,-337,124,288,79,174,1000,1000,239,-277,-1000,-911,-396,-458,469,26,864,129,1000,739,-882,510,-35,422,1000,429,102,315,-85,-299,452,1000,30,-379,-616,432,-1000,852,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-400,-379,-1000,1000,-1000,-249,-356,836,619,-167,-683,1000,-598,-887,89,-1000,-732,400,-123,1000,1000,1000,0,573,-513,0,1000,461,-324,-270,1000,537,-232,-552,-1000,258,252,-559,803,-1000,-387,1000,-1000,689,-1000,-632,683,919,-368,1000,-304,159,-931,0,132,81,-297,-959,62,554,-906,-1000,170,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-397,-549,-729,136,-914,-397,-1000,953,-123,-1000,541,278,-782,-733,-608,-654,-830,343,141,370,1000,1000,-538,985,-864,-1000,1000,398,381,-531,581,432,167,324,-1000,58,-681,-558,296,-145,24,-1000,-666,-568,-108,355,1000,-764,-132,1000,90,317,6,-849,-246,246,-412,-1000,878,987,-901,-799,-518,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,-857,-1000,486,-1000,198,-81,1000,-28,-477,-961,573,-1000,-1000,-69,-1000,-1000,1000,-1000,959,300,1000,-1000,390,-305,-1000,1000,41,-480,-1000,608,302,585,-409,-1000,623,-510,-795,1000,-962,-557,-175,-1000,297,-855,-611,1000,596,-580,1000,763,-140,-845,-495,-240,-383,34,-1000,517,1000,-1000,-52,137,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,517,726,103,329,-189,-163,1000,-38,184,786,762,-848,-76,-1000,708,82,-312,243,-144,830,474,-237,1000,-1000,121,150,526,915,-246,1000,434,-105,-157,-227,1000,-709,-104,1000,-557,-296,-814,572,-786,-1000,131,1000,196,-795,822,400,334,-48,-1000,605,979,-108,-562,1000,486,216,-1000,-743,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{797,27,-330,156,-437,-307,371,1000,-275,-551,778,645,-864,31,-791,-1,-1000,-42,351,-452,1000,429,-10,1000,-813,558,1000,-174,960,-383,903,836,-1000,744,-1000,118,-443,-1000,567,-559,-106,-1000,1000,-1000,-299,502,1000,-1000,-342,184,-282,1000,369,-735,655,513,-160,-892,340,71,-879,-602,649,579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,-987,-418,410,-553,779,-1000,982,-448,-258,-532,979,-423,-98,-182,371,-504,553,-979,702,31,-34,-1000,1000,-1000,-1000,496,-73,479,-1000,426,-806,295,-982,-250,1000,-1000,1,1000,-747,-271,-851,-1000,297,-1000,-93,1000,1000,-1000,1000,1000,26,-331,66,578,194,867,-573,-312,-123,-158,-1000,112,783}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{830,-329,-367,-218,-742,491,-1000,440,-1000,-706,972,739,-660,36,-1000,81,-396,-1000,3,-239,1000,598,-461,1000,-1000,-1000,1000,-252,389,298,604,353,-246,-1000,-1000,797,237,-58,1000,-956,1000,-1000,-1000,-1000,539,-84,776,138,-950,819,1000,420,-442,-1000,441,800,-615,-154,-67,-177,-168,-800,521,965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-574,-1000,1000,471,919,-532,1000,791,-712,-1000,680,37,-929,-265,-249,-546,-508,-37,241,-239,822,-1000,-720,366,127,935,814,806,409,-98,212,51,-120,-58,-705,-294,-1000,-882,279,708,121,-981,-400,-1000,-482,569,967,-811,-747,690,747,970,691,427,1000,-51,237,-905,-1000,935,-952,-1000,-94,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,587,187,177,300,-439,-276,-53,-999,-369,-179,402,-483,750,18,676,1000,-1000,-474,478,731,-1000,-1000,825,-830,-572,20,693,908,1000,437,-754,-369,-1000,1000,-405,272,1000,-75,1000,49,-665,-1000,514,-832,1000,-64,1000,-351,-406,458,1000,1000,1000,1000,683,658,-533,-1000,-1000,1000,-658,-343,-283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,-556,440,-1000,1000,-543,-991,-1000,888,-1000,-1000,1000,535,193,1000,691,255,1000,963,1000,-59,329,436,632,-749,52,-359,1000,5,973,-43,186,263,384,-29,1000,1000,503,-1000,-721,567,-1000,-42,-45,-792,-36,510,467,-828,640,167,-127,-1000,1000,-1000,1000,1000,-1000,1000,-1000,-835,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-242,-378,-313,-470,-123,-417,-1000,-306,787,-1000,1000,1000,583,189,910,175,379,456,963,888,1000,-685,453,188,-721,1000,1000,731,-85,-623,873,-102,-152,91,965,881,1000,89,-679,-394,1000,-1000,-1000,125,-157,-36,1000,-933,107,668,-1000,-29,-721,1000,-925,277,1000,-511,115,-1000,-695,742,725,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-934,340,1000,-598,-968,1000,523,1000,34,-198,-1000,-1000,-1000,-293,-1000,1000,502,410,-1000,-523,490,651,380,1000,-563,60,-1000,-439,1000,564,225,1000,761,782,913,-806,-967,889,-1000,-96,-376,-1000,-547,20,541,-1000,-571,-329,-570,-552,-949,137,1000,181,535,-763,-1000,241,962,1000,-584,-970,-1000,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-184,-1000,-585,463,327,416,400,778,653,-117,-439,-904,-509,706,-794,261,-302,-97,-561,-241,-285,370,-412,255,544,1000,-319,-347,-353,1000,423,-561,-958,-223,776,623,801,290,-260,-182,-34,108,77,499,60,-1000,-400,400,-895,-884,-820,-1000,695,-540,783,-876,157,1000,806,557,395,88,-72,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-985,-810,680,337,481,815,-681,754,676,84,-287,-163,-265,-550,-570,494,-712,996,-376,681,-927,-993,854,328,232,-249,-706,365,440,472,53,939,975,-311,185,-855,-877,-158,-837,-313,892,-985,-925,763,904,-956,613,160,569,633,42,-937,143,317,767,-592,-877,-962,868,718,594,-86,-991,351}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,-770,-126,-471,1000,913,1000,1000,99,-35,-1000,-315,-39,351,-740,666,-157,52,-497,337,-59,329,-1000,1000,-749,52,-359,-1000,1000,973,-884,74,-1000,485,269,1000,-9,789,-568,-721,-740,-922,-42,-57,-327,-837,-1000,865,612,-944,-236,-201,918,-146,103,178,-14,-552,74,113,-1000,472,-485,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{211,470,-152,1000,-47,128,315,-317,162,453,293,-471,588,690,238,-103,263,-410,-554,-781,-614,366,35,-578,8,1000,2,54,-211,701,767,-520,973,-205,339,433,277,-498,557,657,696,-765,443,1000,830,-1000,1000,-959,-573,807,-1000,-969,-495,-1000,456,16,-307,-685,-971,671,548,215,-814,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{937,470,-152,1000,-807,-1000,-524,-1000,132,1000,-82,312,-66,-546,1000,13,-297,-949,-1000,-1000,244,1000,587,-1000,374,404,-270,1000,-385,-1000,895,572,1000,-141,-1000,-1000,71,173,-18,297,142,132,-605,902,-4,-243,1000,-567,-1000,1000,711,-969,-440,-274,-390,-164,-1000,-140,1000,-1000,1000,-1000,-814,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-242,444,-313,-470,-433,-417,-666,-723,876,-457,1000,1000,1000,1000,1000,-675,1000,884,610,888,-702,-1000,1000,188,-632,1000,1000,526,78,99,1000,-1000,1000,-158,965,1000,1000,-1000,-679,832,1000,-902,-1000,320,-652,-149,1000,-933,844,668,-1000,-1000,-1000,349,28,1000,1000,-1000,-1000,-379,-452,1000,365,-950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-584,-450,-1000,-434,592,-373,-266,-300,1000,-628,1000,1000,-455,1000,1000,422,-135,925,985,1000,46,656,435,431,365,479,-239,193,958,228,547,-531,306,-45,844,1000,1000,252,-775,-829,-186,-160,-1000,25,-1000,-98,-114,800,25,57,-743,-860,-480,1000,69,420,1000,-741,552,-731,-820,1000,791,750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,513,1000,1000,383,91,-698,659,-218,1000,1000,506,667,-278,771,506,141,-601,811,-1000,-148,76,-331,-1000,-1000,161,-777,531,1000,-888,-1000,-700,1000,-400,93,764,-319,-1000,1000,191,-18,-709,-34,-70,-394,-421,8,-370,1000,-1000,-291,-314,781,-223,-1000,268,912,116,-636,-527,40,160,59,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,-253,688,-5,222,-673,1000,-961,-216,215,410,-352,200,-1000,1000,877,891,755,1000,940,79,1000,-759,919,-558,1000,174,-749,283,-1000,-1000,1000,-230,-454,-619,92,789,-173,160,-996,526,1000,644,-1000,-1000,-764,-1000,-1000,358,-277,95,-617,-1000,-1000,502,-414,368,-628,111,-782,-209,1000,21,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{432,-945,508,468,-46,392,639,-343,14,141,355,475,-218,1000,877,380,241,-657,-924,-1000,810,-1000,631,-1000,334,790,-121,21,173,503,-947,-1000,945,-761,-493,315,-300,-932,412,897,-770,127,398,555,667,-900,-901,-618,-151,-1000,200,350,-15,-389,-584,-1000,989,880,-834,-685,-285,161,-712,175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,-575,-939,-503,1000,-959,639,357,-1000,-1000,-1000,-1000,-340,1000,797,870,377,-930,-567,-492,697,-526,1000,331,1000,1000,-1000,-439,175,503,-914,-323,321,1000,207,-737,-140,-1000,-369,1000,212,-379,527,829,395,-530,-1000,-650,-1000,-248,-789,-354,-930,-948,-414,-1000,833,1000,-616,322,-1000,931,-860,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,289,1000,401,507,-773,265,-261,42,121,806,506,298,-150,521,63,794,20,-448,318,239,474,10,-324,-1000,317,-1000,-1000,835,-696,-1000,-354,1000,-93,-229,-336,98,-1000,1000,-1000,726,300,420,-703,-443,-695,-393,-674,622,-1000,-141,47,671,-1000,-1000,927,611,-266,-398,-685,92,145,-214,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,927,1000,586,734,-605,891,986,463,1000,953,-401,773,-1000,373,587,391,-256,1000,878,-912,1000,-1000,1000,279,1000,-538,720,1000,-1000,-1000,161,547,-1000,-559,-1000,475,-292,872,-1000,1000,1000,855,-1000,-379,-523,-932,653,571,-745,763,645,638,-643,-1000,1000,100,-1000,184,-1000,855,669,-346,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{367,1000,808,-361,412,-149,-515,1000,-351,-402,-1000,-85,966,-593,66,-693,90,-1000,1000,536,0,-1000,-329,103,-105,-1000,959,-373,-910,883,-32,-685,-1000,311,-605,901,-162,653,-1000,-223,244,657,-1000,575,690,-780,-611,-1000,-1000,643,-991,-866,-998,-404,698,-752,388,710,-700,-781,-633,-985,-207,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "add(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,-277,-1000,1000,-1000,528,460,29,-1000,121,-1000,381,-1000,-1000,-1000,-896,242,594,-147,1000,-486,556,-437,1000,-89,1000,1000,-474,-732,-536,1000,-224,105,-1000,-750,-550,98,-266,-1000,-1000,-9,1000,-33,-129,-142,-471,-89,548,-797,1000,1000,1000,-286,100,980,1000,-354,431,-211,428,476,114,848,-749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{538,99,-1000,1000,569,-1000,-315,805,867,-39,-640,1000,1000,0,1000,-1000,-304,461,-1000,-1000,550,256,-1000,969,843,-678,-1000,542,-73,51,-516,1000,814,291,-494,1000,-1000,-1000,354,-497,-677,-1000,356,-1000,-1000,-665,69,-819,-1000,-375,377,1000,1000,-1000,868,1000,-582,1000,649,-427,-1000,-555,1000,-225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-1000,989,-198,883,-516,-1000,-372,-1000,-911,-672,559,-453,-258,-603,512,-1000,-314,361,-445,-1000,1000,627,1000,-196,-514,-1000,-120,-1000,-129,1000,1000,-1000,-545,-1000,443,1000,836,1000,-245,1000,-237,996,-1000,1000,279,-1000,237,421,713,638,1000,-32,-842,-45,1000,924,-983,-1000,-1000,1000,1000,-192,333,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{858,99,1000,636,-271,-1000,625,805,501,-1000,1000,1000,-585,1000,1000,-1000,-442,-1000,-476,571,550,1000,-725,-1000,-466,-1000,62,1000,167,1000,-1000,-693,1000,-1000,-1000,-82,59,37,-53,-497,-677,-465,-542,-1000,168,-1000,522,1000,491,86,377,1000,1000,775,-359,1000,-1000,1000,-942,1000,589,187,-113,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{538,-50,-554,-219,372,-671,-12,-220,267,-39,17,714,612,686,229,-713,-234,-412,-853,-757,550,677,-698,969,843,545,-456,927,721,635,-529,715,776,291,-494,719,-406,-948,-29,-692,-797,-182,673,-701,-708,-557,976,-958,402,81,-901,-126,979,-835,481,926,-864,320,447,313,-673,-338,127,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTUwMQ==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{764,162,-501,419,953,-393,-401,53,-287,61,375,130,176,-613,731,79,-522,655,-349,-769,1000,1000,1000,370,188,-1000,-449,-185,66,382,691,-359,-155,283,-396,472,836,251,355,348,-1000,350,35,50,279,-267,-387,-50,199,378,452,-107,362,-362,522,793,-1000,-8,-381,587,277,-167,1000,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-1000,410,-894,1000,-516,119,-356,400,-51,-672,-1000,-735,229,-481,-316,-266,31,250,-445,-1000,1000,560,673,1000,-1000,146,-120,-871,345,-73,1000,-383,-684,-409,246,727,630,1000,-79,212,-249,609,-737,1000,-594,-686,-732,-865,859,-161,665,-1000,-170,-561,886,359,-70,-542,-153,495,519,-685,623,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-909,444,-669,-219,-307,-671,-1000,-220,582,83,-629,-1000,616,-1000,778,-713,-1000,1000,-1000,-1000,-76,-725,-970,414,843,545,-1000,-1000,699,635,854,-173,-16,545,-494,1000,-445,579,364,1000,548,-1000,-846,629,-735,-254,976,-179,402,81,1000,1000,-849,-1000,1000,585,1000,88,447,253,383,-594,1000,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-461,325,-891,132,128,-657,-198,-917,189,-214,649,258,445,-848,252,-514,-149,-628,-112,-109,948,537,378,-813,327,-540,712,-347,-350,423,895,-210,-213,2,5,856,-938,150,-701,438,350,-110,-681,-982,819,-118,-521,91,484,-86,307,-195,-591,922,674,334,415,542,709,225,420,387,-191,809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTQ1Nw==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-158,235,-457,263,-746,719,-396,1000,-437,400,-1000,-76,348,1000,91,1000,-740,858,178,-627,92,-881,-1000,218,-355,971,-96,-295,225,-1000,-556,1000,-781,692,360,127,-157,940,400,-426,642,9,-502,-209,-196,-58,-1000,572,211,202,-630,1000,-591,-257,286,-1000,867,1000,930,-416,-877,-754,-931,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:OTg=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{-477,18,980,214,-369,-657,848,-864,490,765,-394,738,-119,-463,-78,1000,-393,312,611,-109,-1000,-668,217,1000,1000,126,384,-79,-222,-453,1000,-987,407,529,435,-1000,-285,553,289,-17,676,-976,-1000,783,105,1000,-303,1000,117,-1000,31,5,476,332,-907,-273,1000,-1000,596,1000,896,1000,416,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTE=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue():java.math.BigDecimal",
            new int[]{1000,-279,979,830,-341,206,-315,1000,-557,-1000,416,1000,150,1000,828,9,1000,-1000,124,370,550,-250,-1000,-1000,-1000,337,-751,1000,477,51,-516,1000,-328,-637,-610,455,659,-1000,-941,-665,-1000,-1000,1000,-867,-428,-778,604,-1000,-845,709,-1000,876,961,33,-141,1000,-771,1000,1000,-1000,-1000,-1000,124,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{271,549,-745,1000,-52,11,688,1000,535,83,734,-176,298,104,607,1000,-781,765,59,-347,894,-641,-428,-43,233,266,1000,-1000,623,502,-486,496,-612,774,180,-704,-494,-631,-333,44,-1000,-240,72,-66,-181,-601,310,-983,1000,-564,559,753,839,674,-176,-172,1000,-1000,-1000,168,-195,-600,-247,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{733,611,613,-401,330,-1000,-445,608,668,-847,-281,62,855,-362,-1000,229,589,-209,-601,-1000,269,-421,95,-1000,-543,-809,362,-790,-202,3,-602,-1000,39,-1000,1000,-1000,1000,-1000,210,35,4,362,-715,-970,-425,-427,-420,909,425,-134,-567,-443,699,537,-646,-821,976,470,-493,231,21,193,328,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{442,169,-587,1000,744,-574,558,541,881,681,223,-535,115,-199,493,-97,-505,871,-207,-35,513,-523,-697,103,-1000,1000,-777,-322,737,-485,91,256,-154,1000,-1000,190,-607,623,-605,-891,-609,-247,580,257,-702,224,363,91,-34,141,1000,940,176,143,562,-470,-446,-232,233,477,-202,-599,-1000,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-761,657,-1000,-768,24,-816,759,-744,144,-481,739,202,217,-1000,-1000,433,551,1000,178,-1000,452,-1000,-752,217,693,-446,1000,51,1000,1000,307,-1000,1000,-212,-925,-621,1000,171,-987,84,935,485,-625,117,91,-365,76,303,1000,-239,-626,-1000,-52,793,1000,-1000,1000,-15,-1000,748,1000,-281,328,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{559,-51,-356,758,654,303,209,-504,623,545,-771,-164,301,-561,752,424,-363,523,300,303,603,584,92,532,492,436,45,-780,445,900,-764,-79,-48,-409,-557,-371,553,-308,-417,-1000,-230,-262,489,-121,470,671,-778,-526,-645,-472,139,196,-483,1000,-589,33,-214,-430,51,-182,-380,-633,-759,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{81,-964,1000,802,-725,-465,-671,700,571,-357,-1000,601,-160,-473,-1000,-5,-396,-333,647,-430,62,-108,-57,-700,1000,-627,362,-315,-229,-388,-235,-852,500,-953,342,-461,1000,-1000,-645,-658,-215,746,-747,-308,741,48,-1000,1000,390,-168,-535,168,1000,84,-1000,-697,1000,291,690,-560,633,-992,-383,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{78,310,557,-351,-772,-755,-203,1000,782,-1000,-568,69,-219,57,432,159,-1000,476,146,-739,-42,-651,-358,-904,319,-1000,1000,-1000,921,-312,-553,-827,-85,-1000,-656,-805,591,-1000,-422,-216,-181,133,-813,498,562,-1000,-653,928,861,181,-104,1000,-389,477,-804,-697,1000,-638,-533,85,-227,-585,55,724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-551,-763,-1000,1000,1000,-698,-72,1000,-649,601,1000,624,611,-257,134,45,-783,586,307,-504,246,540,657,720,-725,-1000,504,-1000,-222,-314,541,-422,349,636,-238,-213,-1000,-111,-58,-478,-1000,-243,-28,-1000,1000,12,-556,736,719,472,-440,-900,323,-302,1000,4,916,904,-664,573,-180,40,1000,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{-1000,1000,-1000,-285,-293,-733,-675,1000,-797,-852,1000,292,25,1000,-324,-427,1000,1000,111,-924,192,-526,77,36,-454,-1000,1000,-907,1000,227,1000,-168,513,-503,-282,-1000,-454,-837,-310,-345,-414,474,-1000,-153,62,-1000,-642,98,1000,-458,-1000,-259,10,1000,1000,-1000,-89,35,-1000,1000,252,133,1000,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int):java.math.BigDecimal",
            new int[]{688,288,191,-127,-816,309,-539,999,521,-237,969,-980,135,137,-819,670,-261,-247,-682,-749,215,-631,430,-879,-981,-319,-551,-189,863,-968,707,-842,-870,684,858,-101,229,575,-286,806,-49,674,-890,-395,-220,-258,595,723,38,600,451,153,-208,862,-603,687,529,470,135,-879,-282,376,865,-893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.math.BigDecimal:LTI2OA==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-221,-36,-268,731,-97,150,1000,-610,775,-155,-1000,-1000,-1000,743,374,-81,1000,217,444,-602,228,-1000,-683,793,1000,852,276,-52,364,-52,-1000,-408,584,1000,851,547,-1000,95,-333,1000,-463,718,-837,-439,-1000,-1000,890,1000,1000,-418,-714,922,400,-517,69,1000,-115,-661,-834,-928,192,1000,1000,-449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-404,-788,508,-365,463,358,587,-1000,364,1000,171,-201,-1000,-606,-864,-643,644,665,733,-1000,-1000,-296,148,270,-140,-171,-95,574,-1000,-377,169,618,1000,-273,955,-45,695,-547,271,102,-190,-146,264,-53,339,-742,840,1000,935,982,-518,-230,-1000,-74,111,424,375,-955,566,472,-530,976,438,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{969,340,636,-95,722,272,-267,-341,801,215,-672,-465,-337,-494,-141,540,847,380,650,-986,845,-732,441,676,843,898,535,-11,448,-836,-546,286,794,556,-11,892,-396,101,324,-516,-320,220,509,-673,-890,-308,-30,-288,-11,984,982,-108,901,-353,-934,131,557,-871,134,114,8,449,-154,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{432,1000,-771,1000,517,-781,-460,-309,1000,119,983,-704,-449,-501,229,522,1000,-527,942,192,1000,-1000,-437,1000,-1000,1000,297,591,1000,-343,1000,-419,856,11,324,1000,-1000,-331,-963,-442,123,622,-828,981,-1000,-48,368,-256,311,840,50,909,1000,95,-30,372,29,-214,268,-7,-178,-115,618,3}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{1000,688,1000,-483,1000,1000,-806,225,313,-357,210,-189,1000,-523,-987,1000,-87,1000,486,-1000,1000,-1000,1000,474,839,535,1000,-1000,250,-1000,-1000,1000,-848,1000,-838,621,397,1000,1000,147,-1000,280,1000,225,605,619,-1000,-813,1000,1000,1000,-1000,966,-508,-1000,-123,-419,-1000,-670,-857,1000,64,-1000,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-530,538,-861,1000,336,-721,138,-770,864,668,-745,-519,-913,-580,-277,-306,1000,-328,1000,182,150,-940,-1000,1000,1000,686,-145,1000,615,-22,556,-187,1000,-569,1000,871,-1000,-784,-1000,-9,214,365,-1000,496,-1000,-351,976,646,-458,838,-1000,823,819,290,702,577,1000,-273,570,244,-555,254,588,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{364,-76,-206,-1000,-169,476,3,-506,-667,-965,171,710,-1000,-365,-864,-717,-1000,-342,-631,334,-1000,210,464,-1000,-539,-1000,-967,31,166,616,1000,-435,-510,-525,785,-1000,1000,-477,611,86,-322,-1000,-624,1000,-31,-742,-206,483,-77,-1000,422,-947,-90,589,1000,-107,-540,1000,-998,64,-378,195,438,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-1000,-113,311,-770,1000,-757,323,-1000,-443,874,1000,688,-1000,-1000,-720,-451,315,316,1000,-778,-1000,611,368,-74,-1000,-1000,-305,1000,-1000,-634,1000,932,1000,-1000,866,-309,1000,-554,-80,-272,252,-28,860,1000,916,1000,199,1000,-1000,1000,-745,-1000,-1000,1000,1000,31,1000,-156,1000,1000,-1000,1000,151,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{1000,558,585,1000,569,387,-667,-473,941,-446,-1000,-982,690,-478,-296,557,1000,171,1000,520,941,-1000,-252,755,1000,713,232,-46,1000,-112,-177,-153,654,231,485,1000,-1000,-265,229,31,232,452,-349,-486,-895,463,603,-949,-515,887,118,-144,1000,-394,-557,175,1000,61,775,-294,154,130,-568,789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "bigDecimalValue(int,int):java.math.BigDecimal",
            new int[]{-826,-202,109,-565,851,-703,119,-1000,-294,650,1000,1000,-1000,-1000,-1000,-1000,-37,191,1000,-514,-1000,730,187,-405,-679,-930,-460,1000,-1000,-106,1000,524,743,-1000,704,-433,1000,-1000,159,-546,241,-435,-643,520,923,1000,505,1000,-1000,1000,-720,-1000,-1000,427,1000,-308,790,179,1000,1000,-1000,947,44,275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{107,319,250,-171,425,-311,-787,-441,-114,1000,922,191,284,75,221,-570,-618,-682,-12,-507,7,-540,-532,489,820,-142,-116,265,832,1000,299,277,-147,-110,619,408,600,227,664,441,-591,12,-296,413,-24,255,114,797,-383,251,-59,294,157,-308,1000,193,-542,-1000,108,653,-347,323,-411,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{400,675,-609,1000,-212,-992,-1000,627,291,1000,653,-178,26,74,-813,-991,163,-349,-422,953,1000,-182,151,-444,-346,626,491,-499,1000,-565,78,-1000,318,831,-697,217,-97,-705,-38,-1000,695,1000,-411,373,246,422,-1000,-2,1000,1000,-99,362,-95,-1000,580,275,844,49,-686,-168,-705,-243,-1000,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{859,1000,-580,-1000,47,-617,1000,403,789,974,1000,-222,-576,-614,408,38,-65,-629,65,-673,165,1000,864,-653,-165,-461,-669,-900,1000,629,-694,-1000,267,-1000,-347,-859,-399,98,-709,-362,1000,-106,270,1000,-273,1000,1000,611,380,-6,744,781,-747,-671,760,-769,290,-1000,-1000,760,-141,-187,-463,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{-891,409,-121,379,455,-1000,-517,1000,-837,253,230,-103,-742,-324,-715,582,611,-1000,-200,873,324,-915,-966,-133,-260,-299,107,-224,-79,1000,-580,-29,-239,1000,554,47,666,40,668,65,22,-181,-409,-294,999,-431,729,946,-745,233,-535,196,261,-295,-115,1000,-167,-469,512,805,-492,420,1000,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{935,1000,-752,-690,514,-1000,28,1000,-496,978,238,-425,-1000,-893,-396,1000,-337,-1000,-511,-729,-364,502,-79,-1000,-818,-1000,92,137,1000,1000,-1000,-1000,555,400,-660,-906,-282,604,-247,-530,-156,-933,-276,781,581,-2,1000,573,-842,-543,-239,1000,-649,-671,-167,466,873,-1000,-271,759,-416,537,-300,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{1000,1000,-151,850,-149,-829,-1000,-309,681,516,452,519,113,598,-131,374,298,-198,-80,-66,1000,608,741,-670,-239,122,325,-1000,283,-1000,-980,-1000,3,-249,-1000,-173,-520,-1000,-444,-1000,1000,-923,-1000,-540,439,798,-1000,-1000,-164,-418,9,1000,-487,-859,880,-339,1000,-43,-498,141,-1000,168,-375,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{-1000,643,-1000,-987,344,78,-328,-1000,1000,-810,-105,-585,945,-1000,812,-1000,-559,1000,862,-401,-634,1000,-1000,348,1000,-905,-118,1000,961,-105,-71,-1000,-834,-1000,1000,-1000,13,-27,-1000,-1000,-674,1000,-211,1000,-215,279,-1000,1000,1000,1000,1000,-1000,793,-688,-1000,-494,-1000,-576,-979,1000,-1000,-1000,-394,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "compareTo(org.apache.commons.math.fraction.BigFraction):int",
            new int[]{-923,899,432,-824,377,142,-62,850,-896,-965,552,444,-170,-633,-28,335,677,-263,39,1000,-551,-1000,-559,439,490,-741,-1000,576,-1000,571,-466,534,-482,400,862,-711,645,869,479,770,-560,174,117,23,915,-862,-409,962,-369,820,-113,-171,313,-789,-919,954,-489,-225,-498,890,-402,318,636,545}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{652,189,-690,882,-191,1000,1000,-894,447,202,-610,-416,671,501,163,1000,-1000,-1000,1000,980,-1000,386,-653,138,160,-1000,-321,211,-727,-426,259,699,564,1000,-995,-883,-583,-1000,-628,150,-339,450,1000,-777,-840,709,1000,-971,-100,-445,-574,-40,988,-563,-342,-672,-205,-1000,-64,405,-1000,119,-523,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{709,409,528,-266,-298,-872,-1000,-108,-37,-277,377,234,235,-121,220,-240,131,318,-1000,-266,709,-783,-417,-177,1000,322,-148,-221,784,1000,58,118,-875,-522,576,506,-1000,-1000,757,-325,69,-548,-343,1000,277,-455,-761,371,34,198,38,-13,-909,962,-756,1000,-297,-82,-230,472,719,-259,-149,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{-662,602,-792,691,-408,-602,-539,-220,-16,-668,727,1000,251,-176,801,738,-248,-1000,-1000,-551,1000,-1000,-285,883,1000,-1000,-443,268,-495,1000,-257,187,-950,-98,-379,1000,817,1000,-538,-232,-572,-597,184,859,230,-796,-1000,-537,523,-716,521,-1000,507,856,-613,1000,-110,-388,1000,928,1000,-231,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{-976,189,-229,-134,249,216,14,-798,-503,202,-31,752,671,-991,917,794,-326,-830,340,313,-933,71,983,255,-765,-587,114,522,-707,-426,-242,-842,-622,586,-995,-657,672,-774,-928,851,557,450,768,-577,-403,830,247,161,729,-17,464,-513,988,-957,663,470,386,-806,368,405,-886,-49,-143,-607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{154,-1000,895,-374,-1000,233,14,48,291,-125,925,701,300,-991,-764,-25,-146,-430,340,992,1000,-130,25,536,565,-166,-663,200,485,-800,232,77,265,167,801,-298,198,672,-343,178,-927,336,63,-4,-446,-310,247,-534,395,-103,464,-711,-832,394,663,-229,-241,266,-850,217,713,-615,384,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{-323,363,-449,254,218,446,-699,328,776,261,-416,131,440,-383,307,909,-144,-128,-38,6,-664,69,723,673,-1000,-258,323,553,-226,-515,-487,1000,538,638,-599,63,631,7,-196,-889,-384,518,1000,260,-128,399,-915,-639,-1000,145,-447,-133,266,-28,-117,354,-153,-475,-160,-421,336,-148,-721,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{-323,0,0,0,-1000,-872,-686,-1000,-713,-672,1000,573,477,771,379,946,-846,-336,-678,1000,716,-1000,-1000,0,841,-853,-458,326,181,893,-201,1000,-1000,583,1000,613,172,1000,0,-776,-1000,-499,1000,794,0,-1000,-680,53,-589,0,149,-397,79,0,0,282,-1000,-352,-1000,834,1000,99,296,-886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{23,985,-439,441,-173,781,862,220,358,527,-1000,-103,1000,652,-120,712,74,-1000,-143,-216,-984,86,-940,-29,300,-1000,-56,-835,-294,1000,852,-224,635,952,-1000,-510,-207,-20,32,-348,271,457,-165,-901,-60,1000,341,-309,1000,-39,151,-53,411,-769,210,-49,952,-125,850,301,60,-193,-503,-989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{958,-981,-784,520,249,-521,-599,35,-391,197,55,-815,564,551,512,331,-191,370,1000,779,-221,-223,-643,-317,-957,72,427,-134,996,703,-1000,-1000,266,42,-809,581,204,-1000,69,-291,639,747,421,-1000,271,-859,783,266,-828,-121,240,596,908,-526,1000,886,-1000,-251,-432,-489,1000,-39,-849,-543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{95,-222,-145,244,119,551,1000,-1000,555,-29,-45,-467,-411,176,-51,698,698,-881,647,770,-1000,-787,953,63,-1000,-552,-459,-74,-581,-748,489,123,-136,710,-653,-787,-258,-841,-559,442,127,866,819,-632,-370,556,970,-307,138,142,312,213,955,-404,678,-671,-404,-864,35,-393,-1000,-502,-13,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(int):org.apache.commons.math.fraction.BigFraction",
            new int[]{255,-817,8,503,1000,-206,1000,-264,-566,359,786,-624,394,821,-533,-1000,-1000,-584,19,-19,-356,774,-7,425,-1000,-1000,-653,-911,662,-530,860,-74,870,779,-302,-58,378,185,163,-274,-414,159,-809,-1000,1000,124,-173,-485,-801,26,-555,-103,166,246,347,-264,-849,-634,1000,-326,-960,-852,-679,529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{327,-1000,-490,-385,-722,956,-59,-1000,379,35,-126,1000,1000,-333,256,394,-62,460,201,-1000,-769,-1000,923,-1000,724,-579,285,-1000,-555,1000,238,389,-439,-201,-231,160,-197,-937,-565,-663,499,-1000,350,-1000,1000,672,-245,558,-1000,-953,-348,751,-639,-220,580,-341,-796,1000,-99,-461,1000,669,-1000,-61}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-212,805,-644,-701,-215,1000,-37,-267,889,22,473,1000,1000,-842,1000,-201,555,99,859,190,559,-1000,1000,-525,224,-1000,-188,-767,511,1000,-1000,417,426,-201,-1000,645,44,-372,-844,-386,1000,-792,713,-837,1000,1000,-7,1000,-1000,-1000,-1000,-158,-1000,250,-679,716,-975,1000,119,837,1000,1000,-1000,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-277,922,1000,175,-1000,-1000,708,-730,-1000,-807,883,-761,1000,368,969,648,380,-1000,-781,682,-1000,-721,-101,-482,-1000,1000,613,-1000,264,-1000,-127,-575,-360,-1000,231,507,883,829,903,1000,-1000,123,-203,1000,-1000,354,205,-902,-902,1000,-136,-1000,822,395,506,753,1000,-1000,-794,-1000,-113,-114,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{342,374,-1000,190,288,1000,708,-137,-82,1000,-862,1000,205,1000,-1000,-1000,202,1000,266,-779,566,72,-53,1000,210,-1000,11,1000,686,-584,500,-42,-1000,1000,1000,-1000,-1000,214,1000,-1000,1000,1000,-555,-1000,391,-1000,-362,-977,355,339,961,-445,-472,-702,821,-497,-1000,374,-9,909,-674,-411,283,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-376,1000,-50,655,-902,-965,-447,244,-1000,-342,1000,-1000,971,1000,140,-6,-198,-873,554,1000,-1000,1000,-545,1000,-1000,1000,318,881,69,-661,338,-1000,1000,-549,293,-798,-5,1000,1000,1000,-1000,1000,-466,1000,-1000,-1000,-769,-945,515,1000,736,-1000,-77,1000,-1000,1000,1000,-1000,-664,-1000,808,-1000,1000,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-319,410,-77,9,-800,-1000,-341,-521,-810,-92,781,-81,1000,-921,969,-193,400,-702,159,756,-1000,-756,679,-150,-743,1000,203,-1000,-676,-228,-127,-442,638,-684,-1000,259,214,595,-388,1000,-593,-948,528,862,-429,354,-3,-492,-1000,556,-136,-1000,-251,558,-249,1000,1000,-762,-270,-1000,725,48,285,292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,-216,447,150,-471,-949,-1000,151,453,-626,1000,-1000,-69,585,-83,-312,-1000,-346,1000,1000,-833,1000,-677,-70,-416,1000,-458,317,-536,-1000,-869,-1000,941,-451,518,-115,603,-156,295,426,-1000,236,-400,1000,-1000,-305,-1000,-29,1000,1000,38,12,517,366,-875,1000,1000,-759,-400,-816,1000,-492,722,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{-362,-140,497,67,-1000,-402,309,-1000,-756,827,-924,1000,1000,-1000,550,589,491,-668,-1000,-1000,-1000,-35,30,-1000,-240,1000,1000,-1000,-726,657,1000,754,-1000,-621,831,890,436,-564,-1000,-15,-515,-601,856,-1000,611,897,638,602,363,-595,-744,-38,1000,471,1000,1000,1000,-623,168,-1000,-1000,801,81,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{498,-864,-375,161,-188,-1000,511,-1000,-965,17,-1000,638,-1000,544,-1000,1000,-914,484,-763,-1000,-1000,1000,-393,-22,1000,1000,1000,-892,-1000,-1000,1000,-1000,-1000,938,1000,-1000,-1000,-1000,937,-244,-796,1000,-348,91,-223,-1000,-473,-1000,1000,1000,1000,1000,-1000,-1000,-1000,-1000,854,-186,-764,-1000,-149,-943,-105,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,677,-1000,717,-298,51,366,248,83,700,-862,1000,986,681,-652,-673,-416,563,575,-549,-521,203,-947,-1000,-350,-233,-357,1000,847,-1000,1000,-83,199,811,926,-774,-721,424,1000,-300,371,872,-759,-124,-639,-725,-1000,-980,849,224,1000,-294,-678,-496,-541,-77,-863,42,308,2,-674,-1000,747,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(java.math.BigInteger):org.apache.commons.math.fraction.BigFraction",
            new int[]{1000,207,-1000,672,145,1000,1000,188,329,1000,-1000,488,-199,544,-785,-1000,242,1000,152,-1000,428,-470,131,1000,103,-1000,-610,1000,289,-963,1000,-382,-1000,730,1000,-718,-1000,206,1000,1000,1000,1000,-431,-1000,-71,-525,-470,-1000,913,-548,1000,51,-783,-782,1000,-509,-1000,568,524,903,-875,-1000,215,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{77,288,-914,-83,893,-737,278,204,-533,771,-993,241,670,-849,-405,147,-74,-198,25,-873,213,-812,-210,-555,439,-292,87,194,-36,-484,268,412,877,-950,-333,-497,-252,-447,-550,26,3,204,967,-64,816,-417,-753,137,475,161,730,811,-464,-734,-895,398,-229,535,219,347,903,265,419,776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{189,-302,982,-459,17,571,42,1000,-1000,1000,479,1000,-173,-417,67,1000,336,-290,-421,-560,1000,-711,-133,-1000,-240,141,21,-342,1000,-159,71,-143,-1000,1000,-427,412,-1000,-309,-239,30,-694,510,610,480,-5,-324,-1000,-191,157,-799,-305,200,-388,689,-510,-737,315,-51,255,678,-1000,936,509,-76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{461,430,-903,-1,1000,-85,-226,871,-570,1000,1000,1000,9,708,1000,732,-628,-213,-96,-255,368,190,886,121,747,-622,-594,-1000,-380,1000,91,-297,773,411,-519,624,1000,-1000,1000,-933,613,-73,-688,13,-454,-1000,1000,-160,1000,-793,-16,-1000,859,-310,-856,48,-146,-994,1000,-980,-481,-484,-190,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{644,-530,-631,1000,644,-718,-990,67,375,-1000,870,-1000,167,1000,1000,465,-1000,557,-318,423,-1000,1000,1000,528,304,-149,-729,123,-34,1000,5,-576,666,856,-121,-496,1000,-326,1000,-1000,1000,-1000,-1000,-247,-1000,-1000,548,-694,113,-245,-249,190,1000,-315,-250,872,-114,-235,1000,-776,119,-643,-665,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-321,-298,-676,61,1000,-1000,1000,-299,302,564,-1000,507,170,-1000,-1000,-337,372,-232,773,-538,1000,191,-450,-885,800,142,192,431,1000,-1000,-224,-262,1000,-396,648,318,-495,12,-1000,811,-36,-475,1000,221,710,413,-1000,-151,60,695,173,1000,-693,-1000,-958,375,-493,189,247,1000,440,429,878,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{-332,1000,-225,-97,3,-578,1000,408,683,99,1000,984,-1000,310,25,46,-429,-272,17,390,951,996,-113,646,-96,1000,-830,-1000,-1000,1000,257,-1000,-1000,-435,463,1000,113,1000,-1000,142,-1000,-1000,-1000,488,-979,-59,955,-1000,-1000,-297,829,-546,114,-823,76,-524,518,-1000,138,-1000,-1000,-1000,-403,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{44,237,259,0,877,-2,-85,520,-5,980,1000,804,-255,434,1000,1000,-676,57,513,11,807,359,1000,-949,-241,-461,52,520,-80,1000,-612,-799,598,1000,-1000,1000,1000,-786,1000,-1000,652,117,-674,79,-1000,355,1000,-324,880,-584,-503,-1000,39,750,183,-649,-37,-1000,1000,-792,316,753,-1000,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{341,306,-581,-738,17,515,-163,1000,-901,1000,382,910,624,-333,1000,749,-109,-297,-129,-1000,279,-1000,793,-870,-58,-737,-801,-342,-413,803,625,430,550,281,-1000,-734,797,-808,571,30,-694,1000,246,-709,42,-1000,421,508,1000,-1000,670,-602,-388,-437,-1000,38,-232,-937,856,-523,-92,-278,-191,-467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(long):org.apache.commons.math.fraction.BigFraction",
            new int[]{66,104,-1000,61,-332,-1000,522,-513,290,1000,-710,1000,9,-335,-1000,-787,-581,-751,-899,314,368,929,886,1000,941,-622,-594,-61,474,-3,315,-341,-470,-563,1000,-8,-1000,132,-734,1000,-933,-1000,205,13,505,868,231,-1000,-1000,844,-824,732,436,-998,16,125,140,513,-211,693,164,-484,313,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{395,80,-388,-228,-579,368,917,-797,-611,563,670,390,906,633,-796,208,65,-92,-303,-137,-361,-340,386,-561,34,-56,-134,-535,461,-256,-591,583,147,-852,-224,513,-315,-734,-601,-125,-366,-92,3,-824,-665,670,741,271,-398,-10,750,955,372,-993,663,1000,762,578,57,-115,4,715,81,793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{54,-514,-495,-833,1000,-753,663,-1000,739,203,1000,-841,711,124,206,1000,-53,279,-63,872,-183,-772,-387,-984,736,2,791,898,531,-121,714,175,-847,-890,603,-659,-1000,226,1000,407,26,-236,139,-835,765,-18,405,-481,400,-336,1000,181,1000,384,711,-400,-1000,-813,593,306,-52,-694,1000,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{892,760,241,775,-1000,1000,-721,-74,-652,791,1000,1000,1000,-1000,1,348,1000,255,1000,-1000,-205,-963,1000,270,756,-1000,-1000,97,-866,591,-1000,204,-672,-1000,-281,-1000,-541,-1000,-737,-1000,525,-202,756,-586,-515,1000,-155,1000,1000,-680,1000,475,-523,1000,854,1000,-51,-365,-358,-1000,-1000,1000,-144,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.ZeroException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{296,-997,-389,184,-122,-754,178,-899,-723,-850,975,433,-852,-203,-315,915,823,-535,4,804,632,-9,-256,-984,844,817,518,-684,911,-996,158,-93,125,-813,607,851,997,437,-593,136,-251,-704,101,-55,120,-114,-120,-305,-609,907,-376,-259,988,-411,127,-694,325,231,731,167,-362,-191,871,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{-488,-119,-33,1000,314,-883,-721,0,1000,-918,103,26,261,557,266,348,-288,701,260,-361,-218,-310,-568,-123,756,-829,1000,352,-420,-821,914,592,-672,138,263,-1000,1000,-120,610,-589,-477,-150,-26,-423,1000,-247,-584,-259,-554,-707,475,331,95,-200,-299,-75,-1000,-853,-574,-428,82,-642,212,-647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{278,-501,1000,-833,-122,-113,-629,-1000,168,203,-1000,841,100,-102,206,165,-53,279,-52,-472,-771,-853,-211,-984,174,358,537,898,-117,-376,-380,116,221,-890,-747,268,-123,-248,-287,-1000,26,-240,543,296,-762,-745,-120,-259,400,907,-621,-524,-362,-103,711,1000,325,-64,1000,-1000,413,32,-256,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{-1000,-1000,-1000,907,1000,338,274,-1000,1000,-828,815,-929,25,1000,-1000,1000,-519,1000,-1000,138,-909,-1000,-591,-1000,1000,-89,1000,1000,1000,-1000,1000,1000,-1000,-656,849,-533,291,1000,1000,-583,-1000,-228,-1000,224,1000,-797,-216,-1000,21,-578,-343,1000,1000,-1000,-118,72,-1000,-1000,-447,-341,387,-1000,851,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{180,-450,551,970,629,-623,-538,-900,104,-1000,-81,-386,683,-89,-56,233,683,72,529,-187,-577,-977,-228,-951,443,281,717,689,-218,-870,143,74,-215,-866,1000,-761,-1000,-64,717,-388,128,-710,-781,-52,-32,288,-467,-479,579,394,380,-87,145,644,-479,893,-1000,-825,674,-898,209,-711,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.BigFraction", DEReplay.run(
            "org.apache.commons.math.fraction.BigFraction", "org.apache.commons.math.fraction.BigFraction", "divide(org.apache.commons.math.fraction.BigFraction):org.apache.commons.math.fraction.BigFraction",
            new int[]{451,398,1000,513,-856,1000,-1000,-340,277,302,-119,419,1000,-587,118,-1000,999,237,1000,-1000,-817,-1000,389,1000,-1000,-1000,-1000,710,-1000,677,-1000,330,219,-648,-1000,-1000,-1000,-1000,469,-1000,325,-197,413,31,-797,926,-1000,862,1000,101,1000,371,-1000,1000,103,1000,-1000,263,-27,-894,-652,1000,-637,1000}));
    }
}
