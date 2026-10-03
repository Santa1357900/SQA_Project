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
        org.junit.Assert.assertEquals("java.lang.Double:NTEuODU0NTA4MDAwNzUxNDk0", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{184,-763,-517,46,-4,239,-686,892,-170,-571,93,768,554,-101,29,-410,18,-288,-770,957,-761,65,-717,314,405,654,144,675,893,-589,228,78,-467,-964,-871,928,867,1,473,431,325,-983,-905,-547,-111,-623,-84,277,111,-980,-270,-3,117,-316,-611,-422,647,103,847,963,99,-159,956,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-10,-589,-381,292,0,-473,-902,75,-1000,-1000,-1000,352,172,20,1000,-68,10,1000,-303,-1000,-140,-583,4,-1000,-425,-807,89,460,-891,368,0,-1000,1000,730,354,1000,785,338,-575,-1000,0,318,0,12,-726,-1000,767,-1000,-43,-104,620,-724,1000,111,-988,-249,565,-1000,-650,-264,1000,-164,-1000,821}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-171,-418,-482,288,-246,406,-539,505,-253,-548,57,240,341,181,-410,566,-264,-207,-833,141,-1000,-148,-343,124,93,442,-135,737,593,-72,1000,-9,-79,-593,-413,740,292,-165,-312,21,299,-452,75,-683,81,-478,-795,1000,487,249,-1000,225,-140,-333,-6,-328,1000,602,513,820,-469,-588,629,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:Mjk3LjAwMTY4MzQ5NjkxMjI=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{401,-801,-494,14,297,131,-686,945,-300,-560,-361,768,586,-101,270,-1000,18,17,-770,849,-497,101,-388,232,295,368,95,481,671,-555,-630,-145,-467,-693,-514,843,537,1,-76,-43,146,-697,-940,-47,-111,-623,263,216,1000,-807,-122,-42,550,-316,-802,-326,647,-506,719,963,241,-235,834,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-1000,301,-1000,-285,-916,357,-96,-369,355,654,1000,-482,379,-175,-1000,616,-985,-1000,-6,181,617,-122,204,887,-30,357,-1000,1000,217,-514,870,394,-279,-489,577,-806,-32,245,-102,-1000,732,200,1000,-751,-693,1000,4,839,-662,1000,-1000,-1000,482,1000,-94,364,569,711,-1000,1000,-528,-1000,336,781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-393,-357,606,459,-899,1000,-486,-381,551,-1000,-284,-101,-173,1000,218,-149,10,224,-581,-162,-786,352,-136,-833,1000,1000,-562,-1000,-454,-628,-176,-359,112,-858,190,903,-486,-62,813,332,-902,210,-87,-554,-649,302,-202,-1000,938,-130,-1000,808,261,280,-650,49,-180,-422,848,437,133,89,-173,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{290,-501,550,-655,-1000,-856,409,-278,-1000,20,1000,657,955,-507,450,903,-1000,-150,-1000,1000,524,627,244,-572,-670,1000,78,-485,-411,-1000,-249,-929,-936,-1000,847,1000,201,-172,564,-740,-500,-1000,1000,280,-282,-626,-376,-628,486,1000,-190,629,-88,13,-799,524,91,-824,1000,-972,-838,55,-803,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-44,1000,-731,814,659,-480,-1000,1000,-665,-259,-174,-59,-1000,-5,-1000,620,666,36,-471,201,1000,-1000,461,1000,-207,-1000,816,-1000,-3,1000,-616,-42,134,-1000,-290,704,1000,-1000,1000,-549,736,-926,-1000,-498,871,-1000,-599,-826,-1000,-138,468,649,-801,-205,-209,944,807,-410,1000,-609,1000,289,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-289,-578,266,-89,379,-754,573,-386,-320,-258,452,819,899,-593,-321,822,8,32,-323,586,-263,-49,-712,-486,-292,677,-788,-72,-448,1000,-43,41,-633,150,-364,-635,1000,-545,-392,203,16,66,95,1000,-262,-522,-128,625,348,-102,885,-385,-401,-583,-784,118,-171,223,758,-381,386,191,-207,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{130,943,-252,400,-679,524,-523,-849,-9,211,-410,98,139,663,-130,-129,0,246,-497,-904,-453,-660,-443,-416,-1000,400,100,702,537,-400,905,-1000,-823,797,543,-935,494,865,770,-465,-400,377,912,343,-55,-3,-400,-39,238,-338,106,480,-508,504,-534,63,400,796,522,-414,-41,433,-89,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{197,83,779,-123,227,475,-166,619,1000,-220,-157,-620,558,91,-922,-943,-1000,-480,-538,-1000,-1000,-972,-625,-214,-867,-1000,-293,-518,1000,-656,-518,93,657,651,715,399,-741,381,227,-474,-193,1000,1000,-1000,-145,-828,-1000,260,-809,-24,793,1000,-166,-91,6,1000,-12,1000,-239,337,988,286,496,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{464,1000,-1000,-1000,-981,-1000,-1000,-1000,1000,1000,-821,-355,-1000,-1000,1000,-509,-458,640,475,235,1000,72,-939,1000,-1000,-1000,1000,243,205,1000,781,31,-1000,-91,-1000,1000,511,910,-697,466,643,347,-1000,1000,1000,-1000,-322,164,-836,571,536,-430,-958,1000,239,-1000,-1000,512,124,-1000,-635,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-1000,763,-1000,26,-353,-1000,-152,565,-931,-277,68,114,240,79,-1000,795,226,-594,63,1000,-209,572,-842,1000,151,-122,645,-197,-1000,-59,246,-1000,-992,-100,-283,401,-420,699,472,386,40,988,381,882,-602,267,1000,-675,743,-316,-779,-532,305,113,-43,-66,-192,1000,-603,-998,1000,389,-1000,815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{485,3,197,-1000,776,211,629,-436,418,-268,-172,-419,358,-69,-184,-198,-320,420,550,-220,-576,229,-1000,189,234,-381,-610,-441,55,492,-493,1000,-498,-463,-128,-428,271,557,-1000,-486,272,284,15,103,290,363,960,-1000,160,580,-459,155,-399,-397,-367,-1000,719,498,48,67,-524,-319,-766,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-242,143,-1000,664,522,-1000,-21,36,-947,-97,425,-59,167,1000,-1000,753,804,447,-677,974,-86,237,-406,1000,687,207,467,-222,-397,-583,611,165,-923,-461,-1000,148,130,1000,643,384,312,1000,888,951,507,1000,1000,-1000,1000,-587,-661,-519,880,-654,-227,-1000,441,-152,336,-701,734,-1000,-1000,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{954,594,720,-930,-827,-1000,169,295,725,-1000,382,319,-1000,1000,1000,441,529,-248,-1000,1000,1000,-398,386,99,-444,267,1000,-794,-82,1000,-293,-467,-1000,1000,1000,197,-888,-46,-828,-663,631,974,-175,-624,1000,299,-1000,748,68,594,-1000,1000,-188,1000,-689,1000,-933,409,-886,1000,1000,719,-942,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{664,-857,-176,-598,529,752,64,-387,-532,-1000,-1000,-952,1000,-665,1000,-26,188,890,1000,-630,-1000,-440,-898,-233,817,-543,-1000,860,486,193,1000,-366,-15,-1000,-58,-546,980,-31,-1000,-64,-813,-1000,966,357,-379,406,55,-925,-852,-318,1000,-678,66,-1000,-37,-482,1000,-1000,1000,-1000,-685,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{379,155,-549,-526,451,-814,-462,-34,-394,391,-727,926,114,-200,-918,-648,-814,-275,534,-376,-906,726,222,-139,-93,-500,143,994,53,-690,40,-871,721,-51,-183,336,-824,-537,146,-592,-525,-67,718,295,789,413,153,-35,-456,-149,-426,-754,-42,-757,-947,-176,167,-411,712,70,130,334,134,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{854,869,-645,-686,-201,691,38,-83,102,-54,189,-43,-857,813,213,-218,-139,1000,-1000,728,-211,141,-632,-1000,379,372,41,630,296,1000,-720,-682,-626,57,1000,471,361,-176,-846,-244,-857,769,199,-904,630,-176,-1000,453,3,406,-470,194,-1000,-581,-1000,1000,-960,-203,299,1000,550,651,82,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-1000,1000,-1000,-275,118,1000,979,-413,-543,855,1000,154,-1000,1000,-1000,-145,-906,1000,-1000,-742,-658,1000,-567,8,1000,526,-154,1000,1000,-267,-1000,112,516,611,-313,1000,934,-427,1000,1000,-1000,1000,442,-192,-43,788,-1000,-218,803,348,-1000,329,-1000,-1000,-295,-522,-1000,-769,293,755,-604,-692,891,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{391,1000,-867,-600,928,-782,-245,-137,-1000,-1000,1000,-1000,228,490,73,-598,1000,-1000,-40,-1000,1000,215,209,-443,589,-541,938,-459,56,371,-444,554,1000,1000,-409,557,109,-990,-169,-239,1000,1000,373,-964,678,433,-1000,964,-1000,-1000,-885,506,-1000,-861,-1000,-1000,110,-354,-407,788,1000,-33,-132,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{895,867,-759,-902,-399,-1000,629,1000,-1000,593,126,-718,794,461,-1000,-1000,379,45,-509,-1000,-1000,-87,288,-929,-645,-646,962,297,229,606,-73,333,-484,581,-59,-211,519,-98,1000,-3,-376,991,-307,50,1000,-265,-255,-1000,-1000,-644,-800,586,-1000,819,-37,91,-1000,202,-129,-45,-274,-398,586,528}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{476,35,-1000,432,-209,-814,663,-816,346,-134,921,-622,210,-122,-400,-865,-59,-480,349,-21,880,-1000,-167,-840,702,-668,578,-145,1000,287,-549,158,617,681,16,877,-696,-48,1000,27,-141,920,-101,55,778,3,122,-120,-331,-44,-23,-878,-716,-809,-218,-368,-132,-1000,23,-335,-184,44,-241,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-732,-586,703,571,-331,787,-754,242,196,502,-158,78,787,-705,-147,-120,-806,703,-361,-32,887,242,-695,-972,702,151,-207,-504,92,287,939,101,-613,-577,958,299,-906,341,-290,-912,-83,920,45,954,-50,-323,-438,550,-749,142,-831,-878,909,-147,-992,-333,-401,794,483,330,-350,618,-585,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-258,812,71,-898,138,220,-1000,-1000,237,164,137,-161,-748,-1000,727,302,1000,354,1000,-155,936,-38,983,381,-1000,-584,-815,-496,-658,-1000,-1000,-1000,1000,-1000,-124,718,1000,698,120,-1000,-1000,-686,381,686,-845,541,342,-1000,0,1000,-1000,1000,1000,759,-1000,460,-994,995,1000,703,757,-1000,459,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{-1000,1000,-1000,1000,-491,87,363,782,1000,-1000,259,228,285,-580,607,-1000,-490,440,436,1000,1000,-294,-182,-878,-635,863,-115,597,435,1000,632,395,48,-328,538,1000,-1000,1000,-646,-713,-1000,1000,-668,685,-776,183,-1000,387,152,84,-1000,-740,3,168,-1000,-69,-643,830,829,-313,-18,-76,593,889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{-447,759,-985,876,-328,-581,628,-541,190,-850,497,94,24,213,397,236,-52,654,869,966,838,220,474,-702,219,546,105,854,752,205,793,158,120,387,9,956,-345,653,-836,-272,-979,561,-261,-104,276,-269,-719,61,704,381,-657,-895,-18,309,-600,-74,-582,842,-276,203,-716,-652,296,666}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{1000,890,498,120,33,-428,322,145,1000,-1000,-653,665,-969,-1000,1000,274,-579,1000,-152,-370,1000,141,920,-881,276,-737,1000,-556,1000,1000,-459,608,619,-1000,1000,68,639,1000,-1000,404,370,-206,-1000,1000,346,-376,125,-140,1000,1000,425,-1000,-370,1000,-1000,1000,486,312,50,1000,-785,-209,-737,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-427,-1000,-932,-769,-986,603,440,-1000,-329,-116,-803,-1000,-433,-68,154,-585,-772,-917,-209,-71,54,-1000,-405,1000,1,1000,-1000,-325,-538,1000,1000,532,-1000,-1000,-445,-742,-515,505,-401,288,693,-479,-626,475,239,-1000,384,-284,-435,621,554,-268,-1000,-57,311,1000,598,546,-911,101,1000,16,-282,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{116,191,300,428,67,-929,767,-28,-24,261,587,-1000,773,625,340,-346,-948,355,-310,899,927,312,-81,-809,205,242,565,428,327,-64,602,-873,-47,78,51,919,-111,-727,387,-565,27,-383,243,385,673,333,686,515,111,-257,-527,-450,712,-11,-296,-247,172,310,18,364,-502,-625,-830,-869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-274,-669,-125,278,-947,-1000,-311,-54,106,774,279,401,843,456,-1000,97,-599,1000,-683,943,-211,1000,-274,434,-1000,1000,684,99,602,78,491,-616,200,1000,-452,1000,-1000,-1000,267,534,1000,-517,316,376,-39,-1000,12,1000,-164,551,-1000,-381,1000,607,-443,-146,662,1000,1000,144,187,1000,256,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{180,-139,571,507,-1000,353,589,-413,42,-1000,410,-646,-111,-208,-1000,-234,-653,-9,-269,378,-377,-1000,290,898,152,1000,-60,-48,-1000,-43,384,1000,587,439,8,134,-782,-1000,-380,-564,-450,54,40,521,-378,817,-528,-103,-453,1000,85,1000,-166,47,-253,-444,-472,137,239,-369,330,136,248,-403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{566,279,-479,-563,1000,439,-757,107,-674,-408,-1000,1000,-963,744,365,-183,773,-93,1000,-1000,-458,1000,-222,-865,17,-1000,807,454,136,-961,-855,-231,-157,-528,261,-679,1000,50,-202,382,1000,806,-603,-787,878,442,1000,46,-175,-1000,504,161,894,-156,708,-131,1000,-1000,1000,1000,-268,-1000,-823,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,-510,-467,1000,644,1000,541,-1000,-1000,603,-689,-843,-768,1000,535,-163,-787,2,1000,1000,-380,-35,-628,824,1000,-6,-160,418,357,46,1000,1000,-301,-785,304,-1000,157,1000,-103,925,657,381,57,232,-1000,-1000,930,431,596,-1000,378,437,843,-1000,-1000,19,1000,400,-156,-1000,-1000,1000,-400,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,684,992,-185,-157,-196,-613,446,-363,-221,103,294,-571,609,458,-209,-197,309,1000,155,-461,443,-1000,59,945,-145,55,725,1000,-574,286,333,-879,149,-127,-486,608,381,494,588,-499,86,-426,505,441,-1000,790,348,-563,-1000,-252,-344,-461,-134,191,617,973,1000,-136,309,775,-452,-1000,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{913,-17,114,1000,1000,824,298,-997,-485,-242,-1000,-698,-339,1000,998,862,880,1000,1000,363,-1000,623,-155,-54,178,464,98,333,635,-313,-66,458,823,635,613,51,252,1000,-199,464,699,1000,325,185,-879,-719,686,-1000,731,-663,-1000,609,973,-1000,-687,608,442,-489,-66,-352,-804,-767,357,-455}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{938,589,1000,-1000,-1000,83,-126,1000,-747,1000,191,155,-1000,670,282,-1000,-757,-230,-74,991,778,-137,-1000,-109,1000,-742,40,-370,1000,-382,-67,176,-161,-951,-508,434,860,-427,1000,1000,314,389,-270,1000,810,-769,710,836,-977,432,331,-1000,-1000,-320,61,760,1000,1000,466,418,9,-424,-260,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{965,-95,-343,-489,1000,824,-751,139,-878,-242,2,-1000,-1000,19,-102,69,-1000,638,-621,363,-230,-108,-608,60,902,-855,-251,534,691,-209,721,152,-88,-1000,-980,-314,324,-427,783,268,-447,-45,-274,279,-879,-890,854,972,-685,-895,568,-392,-546,-451,369,608,1000,503,530,355,974,2,-897,-264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{762,309,412,1000,159,-1000,298,-809,-424,708,-737,-1000,-720,-1000,897,1000,1000,1000,127,79,229,1000,-155,-179,299,707,411,-1000,-351,-38,-1000,-62,455,-187,471,1000,897,-4,-647,1000,706,630,181,714,262,-60,205,-1000,-329,234,-937,532,238,-361,-1000,1000,442,-1000,282,-222,-1000,-1000,1000,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-1000,191,1000,-775,-32,-1000,-566,33,-276,-1000,1000,1000,-345,1000,459,-476,-873,-33,589,255,1000,-1000,-479,-557,671,665,754,69,-1000,-816,515,-1000,22,1000,480,-261,772,299,1000,620,-148,1000,-635,-1000,867,21,5,-1000,564,-595,-1000,54,584,257,389,-1000,419,-1000,-557,149,-814,-383,831,181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-331,-269,-781,586,-689,189,958,53,-528,1000,-1000,-978,82,-43,-86,-590,-773,-1000,-1000,1000,1000,1000,-732,-1000,-585,-889,-1000,-630,703,-1000,-1000,1000,765,-1000,-1000,498,-1000,1000,150,-1000,-960,-782,-1000,-603,-270,1000,1000,236,-89,-1000,1000,1000,1000,49,1000,-1000,107,516,377,-745,1000,-320,-910,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{233,-383,-756,-330,-532,223,-991,101,194,-811,675,708,-783,116,-939,-277,518,-922,350,-748,-77,-315,939,-793,-477,-644,630,972,-638,-781,84,-776,-195,-731,-248,-761,-852,-489,-953,747,-439,695,474,-64,244,947,-403,572,-219,-893,281,-655,140,-89,-524,-452,495,286,944,985,82,-22,833,598}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-29,-555,-181,1000,-632,1000,544,596,-577,443,299,538,497,-1000,-1000,-180,1000,349,478,1000,-156,237,535,840,-1000,285,1000,-635,1,-157,557,-1000,566,1000,799,-267,1000,-393,-1000,1000,266,-851,1000,-1000,716,36,-1000,-301,-479,-517,-447,-1000,454,410,587,501,-451,177,736,874,218,393,-151,-747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-971,-883,676,88,-575,-469,123,58,-330,-893,946,-968,-562,-615,132,-252,353,56,743,-312,-248,-844,-239,-199,-903,-195,712,700,382,-521,355,-148,804,55,946,-818,110,946,244,-735,536,-475,269,-227,67,740,-929,258,-133,-684,-734,471,142,-717,967,-590,-533,696,850,-903,651,346,-411,657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{85,-1000,-749,1000,-846,1000,-188,499,-786,766,-237,-839,-447,-468,-1000,-1000,1000,279,-400,-494,1000,-15,185,615,567,775,-400,1000,-939,-275,-970,-1000,248,312,1000,-474,-75,267,-1000,-400,251,-1000,55,400,-400,-21,-56,617,86,-1000,-534,-715,-292,240,593,-187,439,1000,1000,-608,1000,1000,-655,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{-363,-225,-342,-823,-882,-466,-231,-541,-120,-959,-962,407,-306,-1000,-89,-1000,-521,895,-136,629,-468,-1000,546,960,1000,-817,-1000,1000,1000,384,-1000,-132,-393,-636,319,171,-606,669,192,333,821,831,-929,68,-1000,484,-160,-1000,340,936,-472,-273,-367,162,-273,1000,-592,19,-803,964,740,-693,1000,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{75,-887,-521,-202,-571,499,664,679,180,-882,657,-781,-707,-226,293,-382,-359,-362,392,148,940,-884,679,926,-727,-898,450,-212,-270,-132,555,876,738,654,-453,-183,-372,243,-100,979,672,-730,-915,179,-608,838,-994,424,275,-863,-215,813,-782,168,229,-460,976,451,-571,-893,-293,635,-94,-685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{54,-466,-481,-541,-772,1000,-1000,19,-215,-663,-1000,12,-905,-1000,-238,-222,467,551,1000,1000,743,-151,1000,847,450,-905,-555,-1000,524,523,23,762,498,-1000,-341,-95,-1000,145,-1000,-789,787,1000,677,930,-1000,983,-32,-1000,-404,708,-1000,946,-920,378,-823,285,837,1000,302,-32,1000,-138,741,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{551,-830,-27,660,-746,-484,127,789,-461,-65,792,-25,1000,-631,-383,81,-167,929,-178,-149,-584,-365,571,-820,613,-507,-161,365,250,999,396,647,-733,-372,47,167,896,317,860,-791,-1000,-706,-114,371,588,-335,389,-19,842,591,231,452,-1000,-879,-542,928,173,-469,979,-410,538,775,892,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{723,-168,77,283,78,711,-380,-155,-341,949,-479,-302,-177,-310,-1000,370,-367,898,-105,-53,533,-176,791,-1000,621,-170,-84,207,632,1000,-1000,782,287,-460,-452,125,18,451,-736,614,-880,-913,-502,-640,1000,1000,615,626,747,-581,635,160,79,171,-430,956,-223,-583,855,371,665,1000,800,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{-150,588,489,-1000,604,-552,1000,-521,779,-965,-18,1000,869,-659,457,-461,-787,1000,1000,-820,1000,296,-1000,-223,-925,506,7,-1000,550,368,288,-275,-408,785,482,592,-603,-431,-1000,186,1000,774,24,640,723,-680,659,-537,893,1000,362,-380,-925,-127,-1000,182,208,-25,1000,862,984,-760,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{112,1000,-83,-313,578,-577,389,-521,920,-12,402,169,-523,-284,1000,-587,-113,1000,908,526,1000,1000,149,-468,-913,1000,535,-508,32,965,-4,83,992,459,-541,-522,336,638,-1000,243,641,203,-708,396,545,-735,1000,-606,260,-339,-316,487,-1000,-53,-757,182,-414,354,1000,-538,-165,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{-445,607,-633,-713,405,338,828,621,467,-539,553,-118,625,-336,1000,-228,-751,0,1000,-1000,193,-408,137,729,1000,983,357,96,-989,-179,224,3,-902,1000,659,948,290,-822,1000,-1000,-794,744,-216,310,-550,-862,-371,228,1000,596,241,936,-921,-83,396,-354,-559,-1000,-1000,4,-835,120,-328,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-1000,-328,315,-583,54,-704,-80,67,910,-187,249,553,236,-1000,708,-785,-1000,-485,163,639,803,179,-811,-794,817,641,92,-528,-67,-207,349,543,-627,-1000,402,418,-626,-643,-546,-666,-440,-392,-136,-213,210,27,1000,1000,591,864,-483,188,-1000,1000,-649,928,135,358,-491,396,-719,966,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-1000,-1000,494,-137,143,440,-90,1000,351,-307,-415,-1000,-605,-569,412,-1000,-696,-53,-665,1000,1000,-315,-306,212,-269,229,1000,-764,2,449,519,-754,-634,603,-1000,-1000,-1000,-749,-284,584,-315,417,102,-1000,475,1000,947,153,467,631,-966,-1000,-241,171,-913,-685,336,1000,-1000,-789,-162,150,-925,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{1000,567,-729,1000,-818,920,-392,-987,-10,-419,-98,1000,601,-292,376,-252,-797,-126,702,-847,-228,872,1000,-297,1000,588,-234,-600,1000,-403,648,299,-869,-156,726,1000,333,1000,263,-1000,-1000,-1000,-1000,80,-568,-1000,-294,584,111,-95,737,1000,378,-509,-628,-352,-247,-689,1000,622,-852,1000,-369,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{-1000,-713,-656,-214,-96,-159,-201,-1000,-718,295,333,887,43,-446,302,-580,-157,-559,240,1000,903,732,-777,-1000,447,-749,256,-806,817,-211,-606,-475,687,-491,-918,-1000,653,455,-687,802,603,-1000,320,872,-333,968,1000,-1000,-1000,-227,-516,1000,-1000,261,-251,1000,-333,260,138,-1000,-639,905,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{382,-591,711,197,-884,103,264,-956,-1000,-224,-527,867,767,-427,-1000,314,-999,-106,-625,-882,883,250,-1000,-508,-1000,363,-245,-444,544,125,-305,423,-390,1000,502,778,795,548,-1000,-22,984,-1000,197,1000,-882,950,-358,398,-1000,255,1000,1000,527,-241,-199,-161,597,-1000,239,-24,344,-607,-931,-527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{-298,563,863,-474,-1000,-219,-586,443,573,452,610,1000,583,-83,-941,-1000,272,84,594,-321,-719,1000,398,564,-152,166,-291,-972,-617,-990,-195,820,1000,1000,-395,-1000,1000,-1000,-529,-690,988,1000,974,1000,528,-273,52,-551,-1000,1000,-627,-579,-752,-508,490,790,791,520,-1000,75,325,520,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{-388,621,258,724,605,477,-536,-919,-48,-1000,-97,-34,428,499,-480,-527,-519,924,-855,-84,-248,1000,-379,778,2,888,175,601,-871,-134,-515,114,-1000,356,123,1000,529,-286,-844,-776,1000,366,191,41,-176,-71,886,467,707,104,55,105,-1000,328,937,819,641,862,-30,-1000,36,376,352,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:NTc5LjA=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{-1000,177,579,-709,-764,307,-892,-756,35,-740,595,-56,-410,498,-560,-556,-899,-416,-1000,-272,1000,-581,1000,845,-368,888,-177,121,574,-1000,-127,-478,-786,647,336,1000,1000,100,-800,6,-106,290,-118,-55,1000,1000,351,621,439,-723,882,452,58,306,1000,324,785,1000,324,-1000,142,-112,1000,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{1000,170,-240,-1000,133,-1000,146,1000,555,736,1000,43,-95,-343,1000,17,1000,-1000,-213,-622,766,-1000,-34,-538,-115,-1000,-780,-646,1000,1000,-630,-901,670,658,-85,793,-867,1000,812,1000,-1000,84,-969,-761,440,130,-842,-1000,-166,-642,263,-185,1000,286,-1000,-512,-1000,-1000,-1000,1000,-325,-1000,-409,-58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{1000,-778,1000,-476,518,277,725,36,1000,270,103,-65,-287,-1000,-254,-956,-1000,1000,-1000,-349,-1000,-1000,1000,-1000,1000,569,-918,-347,1000,416,-903,262,28,-1000,-494,-142,-218,1000,1000,1000,287,908,986,-939,1000,-1000,1000,-971,38,826,209,188,1000,-315,210,75,-32,611,1000,-1000,-793,-513,-1000,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{1000,-99,-354,1000,474,-509,-447,1000,-1000,1000,-571,1000,-1000,410,-969,-727,673,-1000,674,-303,1000,681,-404,1000,-535,-496,-343,1000,-358,-42,1000,-612,924,994,556,-818,-1000,-824,-1000,-833,42,-475,-1000,1000,-1000,864,-131,-226,970,-229,309,-1000,158,365,1000,1000,-291,-145,-1000,1000,-223,1000,1000,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{20,-738,-678,296,518,-590,56,607,-673,-884,422,-65,-287,637,352,-152,207,-688,-255,754,431,-76,154,-328,133,-215,-959,180,-572,936,-221,345,35,674,-494,-503,-218,-544,-668,-129,-460,174,-562,327,-656,756,-865,640,525,-869,-883,188,-661,-319,-210,224,798,-520,-153,417,-793,865,-944,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{-410,-118,659,-546,839,-681,750,1000,1000,255,563,-185,734,-662,-941,840,-1000,440,98,1000,450,472,-649,137,-126,397,-24,-136,1000,112,-859,596,91,552,-644,-370,95,110,-269,-399,-303,-533,520,-248,42,-1000,28,1000,1000,35,-964,243,-1000,-298,855,-1000,-472,762,207,-822,-732,-63,-84,-614}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{951,-541,217,805,-125,249,114,-640,-1000,-281,-1000,301,-913,-955,-1000,1000,-932,-1000,337,-768,376,-634,1000,-792,-516,1000,-1000,-532,-321,-192,-1000,94,203,-494,842,-590,-109,-1000,-1000,-1000,-969,-1000,587,826,697,1000,228,959,389,-1000,1000,466,-72,-399,-403,-919,1000,729,1000,738,193,1000,-77,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{-594,-973,-861,1000,-1000,955,-917,-802,-128,-1000,573,456,487,-984,648,-419,-855,704,776,-194,623,-1000,1000,300,-699,834,-1000,-936,-1000,30,-358,32,-74,-47,-321,86,-1000,-322,577,-3,418,-1000,-505,-467,-429,425,-94,-166,-1000,755,507,-917,-1000,-540,695,-798,-203,706,741,-281,387,67,-463,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{643,301,-398,362,71,-660,-506,-242,819,859,987,-25,-539,659,89,955,102,339,376,-829,-596,-693,959,-640,576,-869,-728,-761,-819,-974,662,431,-764,-420,364,-722,79,916,282,-539,723,-604,-819,432,-973,-270,408,-449,-112,855,51,-490,-90,330,554,139,-517,-841,276,1000,-910,904,178,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{873,-577,1000,340,-525,-689,-522,-526,554,-838,-427,-627,-335,-667,-1000,104,-633,-80,-276,728,-696,292,-87,526,1000,-16,800,192,182,283,702,-606,-669,-71,100,372,1000,-377,1000,199,-197,1000,-469,-1000,-282,-315,1000,-12,660,294,645,1000,-312,428,999,-180,-224,38,-51,-262,-108,826,578,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-147,370,-642,408,-701,863,468,251,-1000,-1000,-1000,-1000,-135,-1000,-800,403,541,-40,376,-729,-489,1000,113,505,464,476,261,-1000,-1000,-1000,725,431,241,-807,-1000,-722,-400,531,552,-669,-520,0,305,154,-637,-85,-9,-764,-112,22,198,119,76,460,554,458,854,714,-224,486,7,-526,-59,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{1000,891,1000,-477,72,354,703,-1000,-385,-1000,1000,-53,-749,895,-239,-1000,174,1000,-518,-280,-1000,564,-184,1000,289,183,-1000,-376,663,-345,183,-295,-527,1000,-197,-412,-1000,-583,1000,-1000,1000,1000,169,1000,-712,1000,1000,-1000,934,1000,-558,-81,-76,1000,1000,446,801,-253,1000,104,-36,-330,1000,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{445,-613,735,1000,773,-1000,594,-1000,756,307,1000,-331,1000,1000,819,163,-977,-369,896,-1000,-402,-914,8,409,-257,-515,696,141,390,462,315,330,-121,644,1000,160,455,33,-350,700,1000,394,-486,-557,-506,109,1000,-541,599,718,-607,-13,738,-460,1000,-423,-1000,-1000,-248,207,-1000,258,726,885}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{782,-1000,1000,1000,-216,1000,734,-137,-736,-517,-495,795,-1000,-294,76,763,1000,-397,-305,-1000,-1000,-1000,-1000,-88,426,253,777,-872,149,395,92,27,-1000,-1000,1000,-1000,1000,-891,-392,-401,459,1000,1000,510,-155,-1000,-211,-1000,794,-669,224,-65,353,354,1000,-515,-1000,-58,218,1000,-1000,-250,-1000,976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{-124,-295,-1000,835,-828,-656,927,-359,-1000,942,-744,766,1000,-492,-36,-470,-856,1000,1000,288,-125,-203,-1000,-19,-97,-842,-322,-85,683,-253,-1000,-977,248,250,-698,-1000,574,-237,-905,-482,98,-62,-144,175,-759,-1000,40,1000,-475,-904,-172,-206,386,-1000,613,-793,438,425,326,-286,1000,250,-1000,48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{-519,-747,-1000,878,419,-435,-706,790,831,-991,1000,1000,-993,-749,-937,523,-28,-993,32,-597,-731,-228,610,1000,950,180,454,1000,1000,-122,757,7,855,946,479,575,-607,258,-1000,1000,-500,3,439,638,217,1000,-470,939,497,-582,639,-281,304,265,801,-510,490,979,-636,766,-590,-630,-458,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,-134,262,1000,-266,-952,-14,-496,-553,-930,748,1000,-1000,-1000,400,79,759,-860,478,246,-269,-755,-899,-90,534,121,928,-1000,920,33,-39,-145,-538,-692,-676,-292,1000,-701,-1000,201,936,997,1000,94,-1000,-808,-490,-1000,463,-368,-567,-557,-129,504,1000,-738,-833,-828,-231,-264,-928,-738,-916,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{-1000,-747,-182,-1000,1000,-1000,-706,181,1000,1000,1000,1000,1000,1000,-937,-1000,-1000,-993,221,-597,-512,1000,808,-221,14,1000,-777,1000,853,774,-25,-1000,989,416,-1000,1000,-524,830,1000,822,-1000,-1000,-67,1000,-303,1000,1000,1000,-1000,1000,-755,752,229,-618,303,798,-72,968,492,-719,1000,-1000,-458,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{477,-449,-1000,-189,-571,388,-757,713,587,-1000,-394,740,-508,1000,-692,-273,626,-124,-517,291,-77,727,-2,-8,242,-12,-871,-448,-1000,-227,-306,335,735,-420,-449,-180,-280,-143,-1000,120,446,47,-1000,-733,-373,691,-432,-584,1000,-917,-887,1000,1000,-272,-303,-565,-88,-1000,-1000,498,-259,407,269,-18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(int):org.apache.commons.math.complex.Complex",
            new int[]{173,-387,-750,-325,38,-314,204,-529,-459,-420,-266,397,-975,13,-189,-462,124,-237,643,520,-481,709,843,-348,-24,-1000,232,-740,-140,630,-562,-576,-742,-1000,-715,580,1000,-825,-341,-967,602,-747,-805,-133,-705,255,403,-870,-602,-543,1000,-338,-130,-20,-511,-59,868,-978,-391,-321,-2,-232,-1000,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(int):org.apache.commons.math.complex.Complex",
            new int[]{-1000,985,-1000,-357,36,-1000,24,1000,929,167,431,-1000,494,-1000,42,427,895,154,-66,1000,-706,-1000,748,-234,1000,-1000,649,-409,-1000,-961,810,351,2,-1000,30,-541,1000,-1000,-433,1000,168,-1000,-411,-1000,-818,360,1000,-750,958,745,-1000,-306,-165,-1000,-1000,-905,960,498,-158,556,-339,1000,-258,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(int):org.apache.commons.math.complex.Complex",
            new int[]{-789,901,742,211,792,-36,229,306,-170,-413,88,-930,-353,-651,494,466,749,-745,440,-335,-368,893,286,67,912,-465,115,-725,-761,-408,176,-677,-124,27,-111,-265,-749,-875,-904,788,-671,-629,-436,714,219,-923,87,558,84,-109,979,-132,109,338,-776,-517,-926,-856,86,302,888,257,630,-891}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(int):org.apache.commons.math.complex.Complex",
            new int[]{-38,820,-1000,-1000,-26,-1000,-631,1000,929,479,-41,903,147,-1000,-321,-457,201,-113,-41,-856,-236,-1000,748,360,518,-1000,935,-52,-1000,810,702,1000,147,-1000,-1000,141,404,-1000,62,608,925,-1000,-1000,-996,-1000,-502,1000,-1000,1000,116,160,-869,366,-909,-1000,-81,954,1000,-978,-271,-1000,1000,-959,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{631,-682,244,1000,229,46,390,-394,-261,-400,-1000,-569,911,672,-294,1000,-87,-1000,-927,-122,-47,-1000,1000,483,-1000,400,-216,-1000,-958,-899,-1000,-891,-802,-1000,-1000,482,482,-677,-36,-846,490,-1000,-694,101,-110,-470,-1000,-858,660,-255,1000,623,-114,1000,1000,279,-400,-84,539,-149,391,-1000,-1000,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,-374,668,967,899,-128,-1000,34,-137,-236,236,-189,439,341,-5,-1000,393,991,471,1000,561,-25,-868,73,225,-1000,31,259,-186,-532,589,-764,67,-192,789,271,-419,997,-487,65,273,-1000,-325,-477,151,394,510,-973,-274,-17,920,170,76,-921,-466,-738,-722,-253,1000,858,994,587,-418,-582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-172,-1000,1000,47,-16,66,-317,-341,-193,343,531,472,31,862,22,-977,803,921,-822,855,1000,340,-686,719,71,400,-637,459,-498,-460,758,-953,179,47,916,-146,880,990,862,548,457,-1000,-148,543,-1000,-118,367,416,-234,315,927,341,607,-1000,296,-1000,-833,-1000,-92,-585,173,167,838,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{132,-1000,754,-290,594,352,-347,-926,295,-435,1000,1000,1000,862,-473,-552,1000,602,-315,855,1000,-24,-200,247,-311,-222,32,5,-864,337,253,-671,946,491,655,-92,588,526,255,1000,124,-20,133,497,-1000,-140,25,558,-533,-88,1000,47,-14,-172,801,-649,6,-1000,742,-877,-348,-686,-107,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,321,-239,1000,437,116,-441,-495,-231,-1000,-1000,-70,569,231,-752,1000,91,-1000,285,109,54,-417,1000,69,-1000,-251,157,26,86,-899,-1000,-564,-635,-1000,-1000,40,123,-1000,-1000,-1000,0,-187,-773,-583,-214,122,-1000,-761,693,-433,1000,165,808,1000,863,-582,-1000,-234,1000,163,118,-858,-971,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{274,1000,-764,681,-16,156,1000,1000,151,-1000,-384,415,449,-449,-767,1000,-474,-1000,1000,322,289,-196,1000,719,-772,38,239,-255,491,291,-1000,724,-1000,573,-483,-146,-978,-1000,-998,267,-210,1000,-1000,-534,1000,307,-992,-497,341,-1000,-72,-160,-1000,98,-206,1000,-149,-487,1000,613,-32,-1000,-1000,-416}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-75,-41,-259,-884,-167,872,-271,-926,109,802,31,51,-693,-40,-433,399,337,-899,154,958,130,868,195,317,265,-600,-268,591,-316,547,-893,415,940,-654,125,-259,764,-588,-807,-516,169,371,649,135,809,521,-752,-995,827,556,-385,-39,-922,361,-654,229,-750,632,-576,87,-836,422,600,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "negate():org.apache.commons.math.complex.Complex",
            new int[]{-232,-36,-768,-369,54,-148,861,-449,98,849,516,-821,-873,-547,815,-18,152,-114,447,380,-523,-74,545,76,-556,-277,402,420,-799,-374,285,-144,946,-463,-524,-959,-229,-702,-786,-747,26,-747,698,356,-56,273,-20,775,-859,-770,204,-330,610,-765,-575,843,-223,-253,994,754,336,123,-592,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "negate():org.apache.commons.math.complex.Complex",
            new int[]{-202,-201,-1000,593,496,-29,398,-1000,-217,884,942,-1000,-1000,899,1000,339,705,717,-198,468,-1000,757,-258,-197,-1000,-23,-115,184,-202,-531,909,-569,869,937,-1000,-1000,417,240,-1000,-128,188,-1000,491,395,833,142,-38,432,-589,-1000,210,581,325,-294,-798,1000,-122,321,1000,1000,-466,437,-470,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "negate():org.apache.commons.math.complex.Complex",
            new int[]{747,136,1000,354,-146,-1000,11,-979,195,216,-58,-149,-477,495,165,-598,-1000,523,-987,1000,1000,-628,641,1000,-273,-754,1000,146,263,713,1,266,796,1000,-556,654,1000,-722,-442,-108,-1000,-262,228,-822,514,1000,929,1000,-32,90,518,1000,-134,191,-595,-124,-999,135,758,-870,-1000,255,-49,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{446,-368,-631,-311,448,-339,-390,-774,-533,-208,787,305,467,241,69,573,182,-325,-381,889,-1000,-764,-699,-1000,1000,465,17,835,312,711,-1000,266,-284,884,-156,1000,-145,-444,32,-559,-405,795,-1000,704,1000,-949,-341,623,322,1000,302,-771,531,4,202,-37,779,-431,-20,150,465,265,-377,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NotPositiveException", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{-990,515,1000,-84,1000,-929,-991,67,607,-217,842,-1000,28,-371,-609,-107,1000,88,-1000,-135,-272,337,-11,520,-417,-234,-329,1000,-720,-186,1000,-48,-689,1000,833,-541,419,466,234,984,-1000,1000,-911,244,569,-1000,-92,1000,-620,-1000,338,-484,413,337,381,-410,-592,-434,-1000,283,911,714,959,-796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{774,935,597,150,1000,164,-147,119,1000,301,-634,1000,846,-905,-389,-744,-573,1000,743,-1000,-665,275,630,-668,-273,-918,881,-1000,-166,-1000,-40,-629,-9,941,-1000,-1000,1000,-1000,-200,459,100,-1000,400,-600,-519,599,234,-940,-676,1000,926,-1000,468,206,-181,527,1000,1000,-1000,957,312,1000,130,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{-734,766,1000,-84,759,-287,-598,665,992,-902,469,-157,-989,-1000,-609,133,1000,1000,-271,-135,-95,0,-797,-1000,-682,0,-329,481,-553,559,782,-1000,-689,1000,941,-1000,-494,1000,-683,1000,-716,1000,-206,0,1000,-1000,1000,344,-521,-1000,339,0,1000,209,1000,-333,-1000,-726,1000,-205,601,880,1000,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{76,51,-682,347,1000,618,-719,259,388,1000,1000,-1000,-959,-62,-674,58,-1000,-626,-381,1000,-963,-520,-1000,349,-138,-915,-557,835,-1000,-775,-1000,1000,-1000,1000,1000,-1000,-1000,213,-1000,-119,-405,1000,-1000,-458,1000,-53,-67,1000,946,-347,-337,-524,866,574,1000,476,779,524,-340,348,606,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{286,867,1000,48,154,-701,32,394,-526,538,85,1000,568,495,463,935,-206,85,559,-129,-1000,-656,1000,-1000,1000,1000,-206,600,-1000,1000,157,-493,-1000,-765,1000,-439,146,-1000,827,737,1000,351,561,530,-585,-1000,1000,-447,-1000,693,1000,-573,1000,1000,398,-1000,-190,-1000,-539,-1000,-434,-223,-447,-486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(double):org.apache.commons.math.complex.Complex",
            new int[]{-164,777,-20,706,770,-703,-576,150,-315,-518,-365,-1000,-164,140,1000,1000,-559,-19,353,873,66,-301,187,-911,289,348,439,191,999,858,52,638,867,590,341,949,102,1000,-340,-59,578,-389,-413,-835,-743,1000,662,-32,759,538,-1000,121,-1000,426,727,-1000,265,-88,-484,721,-1000,-776,-454,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(double):org.apache.commons.math.complex.Complex",
            new int[]{-628,-1000,-1000,-305,2,85,-751,-280,1000,-563,896,1000,965,-148,-353,-522,-809,-565,-621,995,-322,1000,-64,1000,-187,-469,-1000,-372,-1000,-1000,196,740,-492,-466,607,-614,-93,-1000,1000,-1000,-867,-893,-191,-168,1000,426,-1000,998,-1000,531,-916,852,1000,759,-809,1000,1000,-1000,838,-88,1000,757,-511,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(double):org.apache.commons.math.complex.Complex",
            new int[]{-535,142,-1000,89,-386,-23,-1000,-626,-502,51,-745,-931,1000,-1000,315,138,-559,-749,-1000,784,301,-301,-1000,559,-90,1000,-232,443,821,-43,873,-4,695,-746,-446,-48,776,1000,1000,384,129,-581,687,1000,576,1000,908,1000,202,-1000,-942,102,1000,555,-934,122,-569,-880,1000,-1000,-465,-64,726,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(double):org.apache.commons.math.complex.Complex",
            new int[]{-838,-284,76,324,794,219,-777,302,-676,-982,-667,-862,-278,353,700,319,-116,-52,352,-995,-743,88,-203,255,-229,357,668,767,656,960,-100,255,951,153,-254,868,-896,-236,-86,164,-137,-946,-640,-941,932,-771,-136,926,-736,803,438,-484,-246,-284,795,-230,-855,-403,-344,735,-554,925,-493,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(double):org.apache.commons.math.complex.Complex",
            new int[]{-943,1000,-1000,597,420,827,-257,1000,56,983,-508,-1000,-298,-1000,-530,-818,-920,210,-536,-1000,-606,-1000,354,-613,-421,-602,311,-60,913,-1000,-173,-1000,-224,760,1000,486,-1000,1000,-698,-378,923,961,-224,-606,-800,-548,1000,-860,781,734,1000,284,-1000,-660,-93,-50,-630,461,-334,-232,-444,-452,0,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-943,814,855,-754,83,-400,890,387,7,-327,176,125,126,-100,13,835,1,-198,-410,55,803,454,-312,157,-1000,-187,-546,-78,1000,-403,-87,323,611,990,-410,-160,-567,262,454,-664,615,-107,-830,476,783,-1000,522,-471,739,-1000,-367,-273,529,-514,-575,-1000,509,479,907,532,-512,439,966,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-386,639,-277,-395,833,-29,-228,-375,53,-611,1000,940,597,-672,254,408,162,566,-926,-675,34,645,-117,-185,166,-286,-137,-701,20,-1000,1000,-204,863,14,1000,-515,970,-27,-1000,-646,989,-459,-362,109,706,-1000,206,-340,-384,-1000,-844,409,172,-663,-681,15,1000,1000,376,75,-119,20,1000,293}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-879,-465,855,518,22,1000,618,210,-91,-726,1000,905,-305,-441,-700,542,400,366,-414,-1000,-134,719,-520,-383,453,-429,-394,-239,456,-1000,358,-1,518,604,1000,-364,304,-436,-44,-449,290,133,-1000,1000,1000,-476,498,-1000,-134,-419,-464,-344,428,-1000,-1000,-609,1000,1000,1000,993,305,156,82,-146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-356,-1000,536,60,-811,558,-431,964,161,-233,-1000,750,-648,-283,-841,154,241,14,-322,-1000,-166,493,58,-566,400,117,206,860,-400,1000,440,991,403,-187,64,1000,-1000,390,151,292,255,310,-925,363,1000,-1000,328,-1000,1000,1000,-356,241,-222,-1000,-1000,232,-1000,58,672,495,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-689,26,268,-1000,559,-50,481,743,336,-130,-281,-363,-120,-787,560,-63,-818,-639,71,1000,1000,-197,1000,-99,-568,934,716,810,-654,8,297,-477,-407,-891,-263,-838,-97,662,-1000,529,345,503,361,-302,350,-137,181,-1000,742,219,387,752,-494,512,194,-508,440,-40,-228,-769,-471,-82,14,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "pow(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-112,-838,842,-644,714,1000,-675,115,-222,-46,1000,219,-883,-1000,-166,949,-1000,193,1000,-598,289,-149,196,-266,431,557,598,-164,20,-596,641,-918,-233,-107,37,-1000,169,-96,-372,-470,-559,-603,-878,529,-685,-1000,84,-37,498,-199,449,-122,-250,250,-1000,66,1000,1000,199,468,190,-28,-788,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "reciprocal():org.apache.commons.math.complex.Complex",
            new int[]{193,1000,-1000,337,-952,716,14,-91,561,887,508,306,237,643,-119,-881,78,-757,1000,1000,-1000,-1000,-1000,-384,464,1000,446,1000,218,1000,508,-911,-1000,439,549,685,-747,-341,-76,-1000,1000,445,-530,-424,1000,-1000,-1000,664,-1000,290,-407,-1000,716,-1000,1000,-1000,288,142,509,-509,271,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "reciprocal():org.apache.commons.math.complex.Complex",
            new int[]{-717,757,-816,-793,-409,495,613,-91,767,579,-133,903,-964,412,26,-978,236,167,583,676,-791,-321,399,387,-507,816,-856,895,380,674,506,-658,-924,593,790,-398,143,-50,-415,-319,628,-47,-882,-699,-590,-730,-348,272,-870,199,-204,-935,802,-686,971,-880,700,63,440,-156,423,583,-716,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "reciprocal():org.apache.commons.math.complex.Complex",
            new int[]{-129,-543,802,-1000,536,235,69,716,-271,1000,-425,-323,167,-587,-610,-1000,983,1000,52,946,-1000,255,-171,1000,165,1000,-1000,-1000,-1000,94,-1000,-159,-1000,-264,572,-396,-865,1000,347,-688,508,-1000,-74,-1000,-355,542,-789,-353,-543,-503,-784,1000,966,-83,493,691,-850,-958,704,-1000,55,827,-218,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "reciprocal():org.apache.commons.math.complex.Complex",
            new int[]{200,721,-739,-996,-81,801,243,-233,795,1000,80,-353,534,13,318,-612,384,-450,74,708,-670,451,-504,-243,-231,899,-1000,1000,-1000,763,-1000,-675,-122,576,214,219,-913,263,-422,-163,930,-783,-439,-890,-1000,83,-378,-131,-625,-194,-460,-1000,18,-673,961,400,-59,-342,436,-733,598,-874,715,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "reciprocal():org.apache.commons.math.complex.Complex",
            new int[]{-856,230,-218,-612,273,656,-340,715,945,378,137,1000,-1000,-717,-695,141,1000,559,26,1000,1000,-49,-504,188,-241,823,-1000,-1000,-625,-1000,537,810,-1000,309,-1000,416,-200,1000,-40,-242,-688,-243,201,355,-1000,-118,1000,-162,809,-968,-980,-1000,-230,438,979,-612,-1000,-1000,-1000,734,871,-44,-856,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sin():org.apache.commons.math.complex.Complex",
            new int[]{833,-597,139,387,1000,-457,684,541,355,1000,279,-717,-1000,425,500,136,-1000,-18,847,-119,-641,506,143,319,-197,-620,1000,1000,946,128,605,904,-583,-116,-16,-157,-233,89,-315,-587,-128,354,479,-858,-1000,105,88,1000,-448,-868,511,-694,-634,-469,-1000,-174,744,-442,1000,-49,174,-1000,-65,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sin():org.apache.commons.math.complex.Complex",
            new int[]{-620,799,349,-1000,1000,-185,-382,368,1000,-472,-427,170,439,601,-1000,-851,389,-397,933,285,-31,-611,909,-395,-394,542,1000,-212,-1000,198,-155,-865,1000,-932,979,-1000,-110,-876,811,1000,1000,-521,917,-1000,-126,-304,-68,-1000,-1000,-749,-878,-1000,314,589,-3,1000,-549,349,-95,961,-195,838,-35,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sin():org.apache.commons.math.complex.Complex",
            new int[]{873,-300,1000,722,798,1000,234,211,-383,679,638,1000,-152,-208,1000,-46,335,176,-445,-6,866,-752,-1000,253,-336,-918,1000,-59,-1000,656,-256,341,-25,63,542,140,-277,-491,-845,-54,-1000,1000,-1000,429,101,355,466,-261,-146,-456,-1000,262,1000,-1000,1000,103,-789,1000,-1000,478,-94,-352,200,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sinh():org.apache.commons.math.complex.Complex",
            new int[]{800,1000,363,-765,324,363,-210,859,278,-345,-339,-195,-188,-1000,-436,-1000,1000,162,606,185,-617,967,-914,142,310,-170,425,1000,990,-43,282,-243,-346,614,148,750,1000,630,91,-1000,1000,-875,-1000,-270,-1000,688,72,-1000,1000,143,242,-1000,20,-1000,469,-314,783,-945,-721,394,-415,-1000,413,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sinh():org.apache.commons.math.complex.Complex",
            new int[]{432,713,834,486,-740,343,23,56,-1000,-381,69,110,488,408,1000,1000,-82,606,422,858,-426,1000,-766,-856,-611,1000,275,706,960,-1000,-555,-731,985,-1000,-178,1000,306,642,-334,103,-418,-611,-1000,-769,-476,1000,268,439,1000,1000,69,136,-374,393,391,-1000,462,387,-429,-922,-209,-781,96,-195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sinh():org.apache.commons.math.complex.Complex",
            new int[]{956,-208,-149,1000,284,200,917,-140,515,-588,-797,535,-147,-431,1000,332,-213,610,908,-522,-204,-690,483,89,1000,774,-670,567,1000,667,-372,545,-1000,778,412,224,-33,340,-728,-632,425,282,543,-789,-381,140,-939,-871,1000,-65,-978,-1000,-6,-68,592,222,352,-1000,-263,43,459,-1000,-971,-284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt():org.apache.commons.math.complex.Complex",
            new int[]{-690,1000,55,810,-731,-1000,1000,1000,-317,157,518,-1000,1000,979,-791,1000,71,-776,962,-750,-637,689,1000,981,474,-565,-250,116,286,829,-109,-398,-669,-157,-971,-1000,-137,731,-1000,-827,389,1000,-967,1000,756,430,1000,-633,714,-1000,-150,458,-2,1000,1000,1000,361,-56,-194,-439,-513,-665,347,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt():org.apache.commons.math.complex.Complex",
            new int[]{1000,-297,-1000,768,174,262,599,-266,1000,1000,-876,627,-646,-674,-200,-590,218,1000,-953,120,-1000,617,1000,94,-1000,938,-1000,1000,-294,-1000,-1000,929,1000,1000,-524,973,-150,99,-137,506,-918,35,1000,1000,-315,-1000,-576,650,-155,133,-479,-637,231,944,203,-687,324,-639,1000,1000,935,-467,-1000,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt():org.apache.commons.math.complex.Complex",
            new int[]{460,149,148,715,510,1000,808,-1000,449,-407,-53,215,652,-625,297,-1000,270,1000,-152,-746,731,-820,300,-826,-794,565,-1000,1000,1000,-1000,-77,567,618,1000,-554,664,5,983,930,-620,353,-507,509,-219,230,-808,-333,-4,-582,1000,-606,-535,1,-1000,-154,-782,-67,-608,1000,464,219,-986,585,-983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt():org.apache.commons.math.complex.Complex",
            new int[]{489,-344,-733,960,-592,-285,562,-1000,-247,62,-9,1000,171,-83,-308,-1000,-463,-435,511,-49,265,233,-1000,67,-796,107,55,-1000,-423,-926,-995,-1000,896,-336,482,438,1000,230,-1000,1000,-113,435,-760,1000,-224,1000,-87,1000,453,-352,-269,926,-126,-22,1000,358,-936,109,-100,1000,360,287,-907,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt():org.apache.commons.math.complex.Complex",
            new int[]{1000,707,-964,342,-296,81,-193,-194,848,-792,209,-293,431,-1000,429,260,-611,422,-322,-697,1000,-659,-503,-111,-360,1000,915,921,-1000,-396,342,-537,849,1000,-397,562,893,1000,1000,662,-2,-155,622,541,-1000,-938,347,642,-855,713,1000,-1000,-144,1000,-228,-1000,127,-17,1000,346,1000,475,251,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt1z():org.apache.commons.math.complex.Complex",
            new int[]{-753,-1000,1000,603,743,-383,1000,-141,1000,971,331,-352,-721,299,-617,478,-578,-188,-694,-1000,-1000,1000,654,138,-1000,405,-279,-836,726,-1000,-1000,1000,1000,-1000,-1000,384,-409,-1000,-406,-1000,-283,-595,-1000,-1000,-962,768,1000,-440,1000,876,41,-1000,-470,-559,-863,-824,-849,631,387,312,-404,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt1z():org.apache.commons.math.complex.Complex",
            new int[]{-637,-137,-310,247,-387,202,308,-419,54,595,504,56,-494,347,-823,-927,-317,-942,370,-543,-866,678,-104,585,-127,-492,200,919,-916,892,846,573,-852,635,797,-930,526,-566,-928,14,-35,9,266,-945,976,-985,958,228,539,-671,65,504,-407,822,840,-753,-939,-619,414,123,-185,566,499,7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt1z():org.apache.commons.math.complex.Complex",
            new int[]{400,179,1000,1000,907,796,397,296,1000,-59,-1000,400,-509,545,-952,-571,-471,-1000,637,-748,411,-118,295,-1000,211,-1000,-600,-593,514,642,79,449,76,789,1000,909,363,336,-891,-955,-133,404,419,-213,88,-836,-611,711,-224,-1000,-400,-455,-132,-367,-347,-155,-701,1000,-356,609,693,577,822,470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt1z():org.apache.commons.math.complex.Complex",
            new int[]{-485,637,309,784,781,476,-96,191,-501,-441,469,866,-156,893,353,-606,32,386,559,-902,-798,-593,-903,809,227,-66,-953,300,-929,12,-551,-531,-844,-487,-88,-80,-515,-523,-765,-890,224,665,-743,929,884,392,-342,252,295,-819,-484,773,161,351,-982,-721,-879,233,-58,-610,-3,402,265,-234}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt1z():org.apache.commons.math.complex.Complex",
            new int[]{-234,694,478,794,-427,245,1000,-284,263,-111,-401,-687,-156,-1000,-1000,-470,336,-614,277,-86,1000,411,977,-24,-776,-483,288,817,-401,459,171,242,-418,1000,339,-94,-32,-440,-726,48,368,-3,341,-1000,454,-106,-310,543,29,-400,1000,-318,1000,-4,259,121,-208,-13,348,-76,-243,273,340,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "sqrt1z():org.apache.commons.math.complex.Complex",
            new int[]{-20,104,710,464,-476,749,-71,912,466,442,663,-228,-830,-192,-985,-293,-1000,-510,-753,-209,691,250,-137,1000,274,-2,227,-73,-519,1000,66,231,511,-777,363,1000,268,-608,620,-1000,447,526,840,-464,765,-436,-626,1000,1000,933,580,-348,-832,119,333,-405,-254,-125,-427,684,138,-378,-433,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "subtract(double):org.apache.commons.math.complex.Complex",
            new int[]{-400,-400,400,209,-496,-1000,-400,367,933,-446,-390,1000,-54,392,1000,263,43,651,43,-448,-34,-400,-554,-749,-400,-275,351,-400,-587,318,-303,-134,381,-456,-210,395,474,-321,-503,-607,-400,-171,400,279,-1000,879,-595,569,-626,-656,226,348,77,-400,-245,182,-8,468,218,-183,-400,-455,54,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "subtract(double):org.apache.commons.math.complex.Complex",
            new int[]{-1000,251,-82,-558,359,-965,-841,-577,1000,243,-137,318,924,646,-900,-143,398,261,85,-870,938,663,-747,-37,579,556,34,-280,33,799,52,372,-821,128,113,-247,-561,-337,-1000,-36,1000,-531,170,62,-944,231,562,714,-917,334,-154,-582,234,152,193,-1000,-1000,20,479,52,-117,111,-90,-728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "subtract(double):org.apache.commons.math.complex.Complex",
            new int[]{469,1000,-253,153,-781,1000,351,390,-934,752,-101,-537,-118,451,-674,-746,-497,632,582,151,1000,-45,-679,701,234,-259,-328,-119,914,-1000,1000,1000,-854,-524,60,-1000,748,218,-138,-684,-269,-379,-149,-357,-219,-1000,-100,-408,-345,-119,-22,-592,1000,-417,211,-243,632,-1000,1000,-1000,-975,-54,-341,-315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "subtract(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-140,-1000,-672,1000,448,-611,-908,-1000,199,868,-1000,434,73,323,1000,971,389,-1000,1000,415,346,-224,634,-549,-634,486,716,-39,520,207,-1000,195,-791,-1000,-676,911,-130,-573,-1000,530,287,1000,-658,1000,737,303,-547,-1000,81,27,1000,-164,-425,-1000,-273,-847,555,-632,-1000,-744,722,-864,-528,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "subtract(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,607,72,-13,-1000,319,692,-465,262,-112,-186,-936,-1000,-1000,578,161,97,490,405,870,146,153,-482,756,-1000,756,-286,-550,226,50,1000,-47,-155,154,-245,656,1000,-43,-69,1000,70,-647,161,-742,-1000,659,596,527,756,73,420,-124,395,381,-65,34,-170,682,282,877,-115,20,-1000,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "subtract(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{614,492,-686,86,-277,-60,221,-343,1000,-199,235,-492,217,-254,-793,411,360,-1000,811,31,638,-125,119,-144,-1000,39,832,143,684,-360,-144,101,-217,302,-38,323,-204,107,-875,-684,441,964,-295,-408,-7,-1000,482,-74,1000,-419,-548,630,-261,430,64,-801,476,-55,114,277,-99,216,856,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "tan():org.apache.commons.math.complex.Complex",
            new int[]{695,51,193,-237,-806,-1000,544,-36,923,-238,406,-915,415,942,-233,-1000,435,566,62,1000,1,-774,-473,-786,783,-202,-373,57,445,502,-108,910,871,-358,481,-194,-693,172,424,623,-755,-615,157,82,121,-443,272,-1000,704,-669,283,357,574,44,-259,-662,-1000,901,-971,-1000,-493,-706,897,339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "tan():org.apache.commons.math.complex.Complex",
            new int[]{979,137,-185,-796,-511,631,668,109,-425,407,285,168,-442,-146,-336,983,229,57,653,-343,90,477,83,-1000,-578,-443,1000,-663,910,-1000,1000,183,665,857,-1000,1000,-11,-597,269,-493,147,812,281,-28,-521,-409,-579,-448,409,-172,409,189,-604,-837,597,-1000,-449,710,851,544,-59,292,498,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "tan():org.apache.commons.math.complex.Complex",
            new int[]{-372,183,-708,207,-652,-1000,904,-1000,960,1000,1000,-1000,-86,-109,212,-1000,-965,533,318,1000,1,-561,422,-528,304,-601,-373,-468,-955,112,-1000,-404,-314,1000,-588,-1000,-204,1000,-37,1000,-215,-1000,674,-199,-799,-1000,140,-407,876,-1000,441,354,970,82,22,-48,269,120,-271,-1000,-493,-657,1000,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "tanh():org.apache.commons.math.complex.Complex",
            new int[]{111,77,326,-84,-1000,728,524,713,-303,831,501,770,835,732,-173,-574,-349,400,-878,122,-111,1000,-1000,1000,611,-112,-1000,688,302,468,400,-772,269,-712,1000,-187,-560,-400,48,-67,461,691,-547,-264,547,-1000,1000,834,-69,865,-242,-1000,75,-164,-876,-333,-424,883,-33,-477,787,-594,178,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "tanh():org.apache.commons.math.complex.Complex",
            new int[]{651,237,737,102,357,115,393,751,466,485,182,-786,-40,-58,77,649,-179,-174,749,649,924,993,-762,-840,-197,988,671,-586,-352,-894,-425,-323,323,-305,227,180,-937,748,-329,927,590,-650,-393,261,-176,-123,146,-966,129,946,-389,-970,-936,-898,-7,880,2,961,-503,130,-365,-894,-369,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "tanh():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-223,757,-307,667,563,624,647,-837,831,34,-380,1000,-540,981,-688,-571,859,956,-1000,38,-149,-1000,-1000,-101,808,135,-1000,1000,-1000,-1000,152,-331,1000,-1000,308,-1000,1000,-189,1000,613,-61,213,-462,1000,428,-773,-244,957,-1000,801,-1000,1000,1000,1000,-37,1000,120,1000,38,697,-583,1000,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.String:KEluZmluaXR5LCBJbmZpbml0eSk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "toString():java.lang.String",
            new int[]{-948,387,347,644,1000,548,769,-737,-334,1000,-1000,-1000,580,-1000,279,226,-91,890,251,-495,465,-186,-479,-185,-683,-417,-1000,-446,563,1000,1000,-164,-697,-663,893,-395,-538,1000,-756,-126,-486,1000,-1000,807,-676,943,-273,750,1000,-20,-993,431,-200,945,1000,-25,-668,-1000,-134,-266,-218,-213,-456,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.String:KDkuMjIzMzcyMDM2ODU0Nzc2RTE4LCBOYU4p", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "toString():java.lang.String",
            new int[]{997,153,1000,797,-1000,-833,879,-146,778,-536,-259,575,-1000,-332,-1000,-351,493,1000,-651,-771,177,532,191,-786,-81,205,372,1000,1000,-1000,-1000,-53,-32,358,-1000,993,1000,764,1000,-1000,-641,-524,1000,-676,-1000,-88,453,-39,-776,-527,1000,830,-70,48,184,141,44,322,-768,-240,1000,627,-822,-643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.String:KC0xMDAwLjAsIDAuMCk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "toString():java.lang.String",
            new int[]{176,-122,-1000,-457,-642,-425,-148,1000,-124,-870,209,-1000,1000,-426,-29,66,-131,-1000,-884,-446,-579,-215,491,624,-36,460,663,-587,456,11,1000,-1000,1000,1000,-286,566,-759,-231,-896,1000,-1000,-878,-1000,-539,-286,181,-31,164,-65,1000,-1000,-209,-463,-1000,-636,422,1000,-1000,-666,324,-1000,-146,1000,505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "valueOf(double):org.apache.commons.math.complex.Complex",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "valueOf(double):org.apache.commons.math.complex.Complex",
            new int[]{-298,-161,274,-405,688,187,574,-507,319,739,-764,-965,262,-451,463,-978,842,798,650,306,721,-916,118,-968,-700,-247,-494,727,-679,797,-555,332,-639,-466,344,212,562,-110,864,-922,-705,-95,972,-179,-756,-191,340,-696,832,957,-698,325,891,765,-429,-850,974,817,-504,-704,851,357,-241,-437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "valueOf(double,double):org.apache.commons.math.complex.Complex",
            new int[]{945,862,430,-39,513,141,-625,789,443,-547,362,-316,-665,-437,-995,-449,-545,-436,-75,-663,636,-227,487,-828,192,-333,-77,41,-814,-4,345,-728,-185,-679,-919,92,683,458,-676,-547,309,-430,174,-572,320,-45,-185,-482,491,9,773,460,30,274,12,312,-663,-3,946,895,-179,-779,-637,-935}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "valueOf(double,double):org.apache.commons.math.complex.Complex",
            new int[]{71,1000,17,-809,-966,1000,1000,-300,1000,-714,966,-317,75,525,-588,959,884,1000,-1000,1000,970,1000,402,-218,19,928,-622,78,395,965,433,-359,-1000,498,-110,886,-819,272,-285,-62,-446,399,1000,1000,758,-1000,203,1000,-21,978,-231,-1000,-1000,-20,799,-232,746,1000,-141,465,36,65,-404,770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "valueOf(double,double):org.apache.commons.math.complex.Complex",
            new int[]{-696,-821,-642,-54,-946,-135,-754,731,-969,859,336,414,942,-889,706,325,-179,858,483,53,400,-855,91,-165,-373,-272,724,617,302,-644,668,488,273,624,-892,132,608,-235,-476,929,750,18,-61,-625,762,-435,904,615,471,-622,335,35,583,819,-906,-864,-361,-678,569,163,-944,-925,-600,-651}));
    }
}
