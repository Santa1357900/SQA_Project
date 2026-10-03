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
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "clear():void",
            new int[]{1000,918,-182,-724,96,-986,116,-113,-1000,-528,1000,343,-1000,970,37,144,434,741,-873,-31,821,587,-886,-1000,-457,-396,-893,820,488,-116,148,797,394,925,-1000,661,-921,164,539,1000,-1000,-701,777,-691,888,596,510,-442,221,-55,-534,-101,-228,-226,1000,-450,452,-532,235,-360,-124,1000,620,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "clear():void",
            new int[]{914,668,419,812,616,261,157,-188,-790,-568,987,-556,-134,-195,494,-319,698,33,301,-464,875,137,-439,-607,-698,-685,-277,801,481,-751,-245,370,833,-714,-982,-766,601,-283,-929,983,-713,842,-166,908,528,489,-281,-42,-713,-764,-357,-110,262,706,-867,-245,-412,-13,-856,96,12,498,655,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "clear():void",
            new int[]{1000,203,-465,-312,-175,-630,-1000,-46,500,929,-551,1000,-873,-379,-640,-367,1000,-790,257,-399,-327,-253,-286,881,-390,-132,559,283,-518,1000,-743,-655,973,361,63,897,1000,-1000,-304,246,889,-352,-173,-426,-486,341,214,-828,-921,780,-1000,56,-115,-1000,-1,-1000,1000,-474,-455,940,529,-443,-972,-48}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "clear():void",
            new int[]{738,-132,157,1000,448,-67,732,400,-466,-602,1000,-595,1,-607,1000,-204,-15,-1000,-170,-371,1000,535,196,-1000,-612,-423,-1000,62,1,-1000,319,-67,400,-267,-623,-1000,620,316,-1000,-209,-518,365,358,-144,-321,-171,-130,41,311,-624,-387,-400,557,337,-683,-381,830,-122,-1000,630,-794,91,618,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "copy():org.apache.commons.math.stat.descriptive.moment.Variance",
            new int[]{1000,-37,526,-1000,674,-400,-919,170,139,-594,1000,953,301,857,-493,-487,202,540,909,80,461,134,-258,814,-850,-827,-361,-76,633,-113,-568,82,-854,261,-992,127,-411,580,650,686,-984,-159,-855,-627,524,-1000,524,-163,-263,1000,426,1000,135,526,-129,-655,1000,918,1000,930,165,-568,767,472}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "copy():org.apache.commons.math.stat.descriptive.moment.Variance",
            new int[]{-80,363,334,-666,-172,168,25,-24,-1000,667,-738,113,-673,-490,29,281,-817,518,25,-552,-119,-441,-859,560,796,61,-263,633,-169,-580,621,-166,542,158,455,1000,-286,951,-655,62,-92,-439,702,343,327,939,789,178,144,-502,-309,-1000,-172,1000,-19,431,253,-317,-323,-94,8,126,-106,221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "copy():org.apache.commons.math.stat.descriptive.moment.Variance",
            new int[]{-148,-302,-98,116,-772,214,-846,-419,448,716,-211,-36,818,180,11,-379,388,553,518,494,-603,-87,169,736,-395,879,-213,380,-404,197,-664,48,60,488,-304,101,93,-706,-832,-167,318,907,-207,191,-484,-832,-186,414,504,-467,503,102,273,-1000,608,-648,-230,469,-897,725,-415,-700,669,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "copy(org.apache.commons.math.stat.descriptive.moment.Variance,org.apache.commons.math.stat.descriptive.moment.Variance):void",
            new int[]{851,71,8,-865,-578,-928,637,-205,887,-422,-557,-104,216,421,279,488,-564,507,-524,278,851,718,-233,-86,-85,337,114,95,272,681,283,-747,433,-753,-93,-576,966,-801,65,-491,663,-736,910,-400,166,260,-199,313,-239,359,534,-879,681,-72,-214,-431,119,340,-316,974,-603,-513,-473,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "copy(org.apache.commons.math.stat.descriptive.moment.Variance,org.apache.commons.math.stat.descriptive.moment.Variance):void",
            new int[]{633,909,139,-60,648,-23,-792,566,311,-83,882,707,533,-293,929,754,-650,757,-35,-412,619,-418,442,841,-607,187,-411,66,-745,-607,-539,675,996,57,-786,325,304,-440,-56,-925,804,259,-999,79,-247,701,550,823,966,-695,329,30,-871,-59,-936,188,-426,-600,790,398,-220,74,-550,719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[]):double",
            new int[]{-1000,-122,-619,-361,-838,-907,-482,-409,-1000,-858,1000,546,-1000,1000,120,-1000,1000,1000,637,869,1000,-1000,1000,-871,582,-1000,-1000,1000,197,710,-343,-323,83,367,-1000,420,743,940,150,-473,-735,-1000,-16,-1000,-1000,-1000,-1000,-1000,725,-200,704,1000,-1000,-1000,-175,247,1000,-1000,-1000,514,1000,-60,965,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[]):double",
            new int[]{-1000,383,-230,-386,-551,-543,-621,-86,-889,-1000,1000,860,-1000,843,121,-433,1000,1000,1000,1000,1000,-1000,1000,-910,-56,-1000,-778,878,-162,-26,715,234,-1000,-803,-1000,1000,1000,1000,-1000,-1000,-612,-1000,-727,-717,-1000,-1000,-1000,-1000,931,248,1000,1000,-1000,-1000,-189,585,1000,-1000,-1000,1000,1000,775,-45,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[]):double",
            new int[]{548,323,-113,636,269,1000,909,375,564,1000,349,644,835,-48,120,887,259,167,-819,-42,173,-1000,-628,-615,-1000,608,1000,6,-1000,484,-1000,-323,83,-1000,219,-618,1000,-985,-1000,-1000,664,-363,-318,123,1000,1000,91,-1000,-1000,-123,-208,-1000,366,1000,-27,-339,-518,-685,567,514,-1000,299,278,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[]):double",
            new int[]{-989,458,-506,-1000,350,-1000,-984,-1000,-419,-1000,-103,-164,-1000,-217,1000,-1000,-334,-705,1000,1000,104,713,1000,92,1000,-949,-1000,-259,33,-783,1000,-1000,-1000,156,-58,1000,-28,1000,1000,-262,389,-1000,65,556,1000,-1000,400,-1000,868,1000,-713,1000,-1000,1000,-1000,366,1000,1000,-1000,-1000,194,1000,822,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double):double",
            new int[]{537,733,-554,-176,714,693,1000,465,-1000,94,-274,-1000,918,-695,1000,-288,705,250,823,101,-1000,1000,-202,-530,-705,201,-814,-559,-858,-21,799,-400,-313,859,-1000,-1000,578,-224,-641,233,756,-692,-361,1000,1000,-1000,-579,1000,492,923,-1000,697,18,-88,-1000,-1000,384,-1000,668,-160,1000,-1000,626,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double):double",
            new int[]{745,663,-300,-612,-574,79,-701,-872,393,459,731,-291,-530,730,-1000,478,714,445,420,1000,932,-1000,897,-253,1000,198,137,1000,1000,-1000,259,-570,316,-354,-356,-827,472,203,757,269,1000,687,-1000,657,-143,273,-58,-1000,1000,783,942,-389,584,154,799,1000,1000,-767,-1000,472,-847,-218,-1000,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double):double",
            new int[]{427,-322,-926,-952,-1000,-761,-1000,-1000,1000,-1000,-221,-406,1000,152,567,10,478,778,-323,528,1000,-1000,1000,-814,1000,1000,1000,1000,1000,-1000,-141,1000,1000,1000,-422,-144,352,-1000,1000,-802,-116,366,-434,1000,-881,877,20,-1000,-1000,1000,401,-1000,1000,-512,1000,820,355,-740,1000,1000,-148,650,644,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double):double",
            new int[]{1000,663,-8,1000,-876,1000,1000,-375,377,1000,144,291,101,-1000,291,1000,1000,1000,577,1000,-604,1000,-737,769,-1000,-1000,50,-1000,290,-160,-868,-1000,-113,1000,-381,312,313,612,-1000,890,-674,-1000,-1000,88,1000,-1000,-625,-914,686,138,339,411,-764,1000,-1000,1000,54,-911,1000,-1000,-182,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double,int,int):double",
            new int[]{55,-912,1000,333,-1000,1000,465,-848,1000,-1000,776,869,-529,490,-844,162,304,1000,-1000,400,-1000,-968,-400,521,1000,778,1000,60,733,-386,-1000,605,-287,-709,-428,244,-1000,1000,-569,833,1000,136,1000,-317,1000,1000,-715,-1000,-1000,-1000,-341,891,-1000,-887,-270,-29,238,-190,162,-1000,-448,-608,-837,-186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double,int,int):double",
            new int[]{1000,872,-1000,339,1000,1000,27,-1000,950,-1000,580,-389,695,380,1000,-1000,329,-649,-776,1000,550,-290,-734,-793,-174,-1000,118,1000,-328,1000,-1000,-71,-1000,965,-1000,-1000,1000,569,-1000,1000,1000,-643,1000,265,1000,1000,-416,1000,-1000,-1000,-1000,-401,-343,-1000,918,-1000,-774,153,309,-1000,-1000,137,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double,int,int):double",
            new int[]{562,-829,709,512,-1000,-17,591,1000,1000,436,-83,-312,-209,802,-667,1000,-148,910,-363,-1000,-385,217,1000,1000,1000,-899,1000,925,258,-1000,-748,-227,-51,685,860,995,-1000,-60,-50,893,-915,1000,-649,-335,-14,384,407,1000,-467,-904,-176,-869,-1000,410,725,659,158,1000,1000,37,676,-741,-1000,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double,int,int):double",
            new int[]{1000,-987,-396,417,1000,1000,95,-548,1000,-312,1000,369,14,-36,-397,1000,1000,1000,-1000,80,108,-37,192,1000,1000,1000,1000,-271,905,953,-1000,50,235,-409,140,943,665,1000,-1000,1000,1000,1000,954,422,551,-153,246,173,-1000,-994,-623,702,-1000,-175,1000,665,-398,1000,-1000,55,-1000,624,-744,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[]):double",
            new int[]{-1000,-327,-1000,1000,-1000,-981,-476,273,1000,-1000,-1000,-647,404,-57,1000,-446,-106,553,-1000,-1000,400,148,-113,-455,-509,1000,-875,-1000,1000,401,234,-120,612,-1000,1000,886,-1000,999,-63,1000,810,498,-1000,-222,63,-738,1000,-931,-1000,-1000,153,-941,487,1000,-1000,-44,116,-3,848,-1000,109,637,-954,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:Ni4wOTY2NDg5MTYzNjEwMDdFMjE=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[]):double",
            new int[]{-994,164,619,-212,-836,-427,-269,-865,598,756,-1000,-896,1000,-491,1000,-644,-1000,806,42,-1000,-817,-862,1000,-901,-1000,1000,196,-970,387,-1000,1000,-1000,-784,856,984,1000,-414,-1000,661,875,802,833,278,275,-709,-1000,-336,763,31,441,-692,-397,-248,595,-652,526,-236,579,553,261,-819,-267,167,44}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[]):double",
            new int[]{-220,-259,1000,1000,-352,-586,-413,678,400,-1000,480,-69,-437,-468,-874,-103,1000,-822,-745,-396,-446,506,-697,224,578,-235,-456,-363,358,559,-435,-70,557,854,929,-764,-640,969,-71,386,-22,-479,-1000,-16,-928,166,1000,-411,-627,-784,773,-1000,-74,785,-1000,389,-197,-381,-72,-463,1000,428,-1000,-24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[]):double",
            new int[]{-1000,397,623,116,-676,-489,-247,34,493,-644,-364,-412,62,-582,406,-553,138,-469,-283,-643,-102,129,-12,-160,-33,369,-115,-525,750,21,217,-654,68,754,1000,235,-911,-251,265,751,490,-203,0,689,296,-623,108,229,-306,93,-243,-561,-1000,826,-891,-430,-123,39,-62,-362,208,106,-400,-100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double):double",
            new int[]{761,163,236,661,-387,682,567,-872,1000,628,-648,1000,1000,1000,93,476,-1000,542,1000,311,-400,-302,-406,228,-1000,-216,360,-128,1000,-1000,-723,-431,-288,366,1000,-228,-801,799,439,281,-309,528,-965,-201,1000,-1000,1000,-439,245,-1000,1000,-852,16,1000,-597,799,1000,-757,376,1000,1000,142,365,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double):double",
            new int[]{-386,-71,-603,344,-318,-390,162,-779,885,675,-696,1000,-790,-1000,696,1000,-111,-699,-645,-283,869,-878,-969,-945,1000,1000,-534,472,-1000,713,-318,1000,297,-1000,867,639,-783,-832,-363,-123,1000,-1000,1000,-1000,-410,1000,289,1000,-127,1000,-172,1000,-864,-1000,976,-250,1000,-699,-291,-1000,-757,-1000,1000,-617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double):double",
            new int[]{-885,78,-670,458,-669,-617,913,848,378,-533,-620,-1000,1000,-607,223,473,1000,-747,576,-863,1,911,-20,-1000,1000,1000,-50,1000,-684,1000,-286,754,-699,-579,287,-222,706,393,-161,-783,-1000,-972,283,-159,-759,655,508,615,-1000,-320,142,-476,-805,-1000,1000,832,1000,44,-1000,-609,-249,-542,275,-125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double):double",
            new int[]{-765,518,-178,182,736,-1000,456,1000,-361,923,77,-1000,-608,-108,-45,375,1000,-1000,219,-799,349,669,-897,-639,1000,1000,303,241,-1000,829,613,949,122,-856,474,1000,-1000,-346,1000,322,304,-1000,986,-1000,-964,938,761,337,1000,900,-86,376,-535,-1000,899,32,-1000,-607,-1000,-973,143,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double,int,int):double",
            new int[]{650,3,230,-831,387,-1000,-931,-1000,-349,-856,1000,-1000,848,-460,-591,-1000,-391,1000,1000,-763,-385,392,-38,349,-803,-1000,742,1000,1000,81,524,791,779,-273,-1000,-1000,-654,1000,426,132,1000,675,-71,1000,-370,555,598,742,624,191,717,1000,1000,-711,-456,1000,-837,1000,-1000,1000,1000,928,-394,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double,int,int):double",
            new int[]{272,73,-273,1000,954,1000,972,1000,-563,457,938,1000,-1000,-323,243,1000,246,-193,-1000,190,-237,-1000,-1000,-5,50,1000,-488,-1000,-1000,951,1000,-1000,-46,1000,1000,888,219,-987,-1000,-694,-915,392,350,-1000,-17,-1000,-345,-1000,1000,1000,-986,-777,-579,198,575,-847,664,-979,110,-1000,-1000,-1000,131,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double,int,int):double",
            new int[]{33,-677,-22,-824,943,341,740,-618,1000,149,-303,-250,537,764,223,425,-1000,99,400,-321,-997,914,29,789,-68,-1000,620,-824,-66,1000,-69,-1000,-360,-6,1000,-17,107,-632,-957,-629,-334,-722,545,-229,-392,813,-594,272,-14,919,-664,-806,-1000,226,-566,-1000,-414,-79,-979,400,184,-471,581,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],double,int,int):double",
            new int[]{1000,633,84,512,1000,1000,-261,539,1000,-417,-890,140,1000,612,480,1000,40,-70,805,-1000,-1000,-801,-699,175,-991,-216,357,-37,-1000,662,-368,1000,1000,434,1000,9,-146,325,-1000,-286,-659,671,1000,38,-1000,717,-1000,374,1000,1000,-1000,-527,-1000,974,-1000,-815,1000,-962,34,936,811,-1000,800,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],int,int):double",
            new int[]{-1000,503,-1000,-482,-1000,1000,785,216,-39,814,170,-1000,431,903,1000,-433,422,-747,677,-335,552,-854,738,807,-116,-682,1000,-757,432,-1000,-1000,1000,-1000,-1000,724,463,-1000,-771,896,799,1000,-4,791,102,591,-99,251,-1000,63,354,-575,-218,1000,597,145,1000,-5,-1000,-1000,-364,-50,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],int,int):double",
            new int[]{-841,513,-46,1000,-1000,-378,929,-372,-1000,1000,-460,-433,805,848,-538,547,939,825,202,-552,-1000,-1000,1000,-648,155,259,1000,-591,292,-789,-1000,1000,-196,-1000,-465,-1000,-1000,225,77,-107,-176,-592,-440,1000,-756,192,-1000,-369,-274,-499,-121,-643,-235,944,1000,519,-547,-299,-1000,205,-270,1000,-229,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],int,int):double",
            new int[]{-896,-157,126,-1000,111,-1000,1000,691,482,1000,10,-1000,340,1000,1000,-510,-1000,-254,102,1000,-664,527,338,-586,-46,-743,449,-547,1000,-1000,-1000,448,-285,518,-930,775,-987,1000,52,241,329,14,1000,-103,1000,-148,-1000,-276,86,584,-873,1000,1000,-667,-407,-798,-890,-1000,297,-115,-945,738,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],double[],int,int):double",
            new int[]{-711,-627,352,-826,-974,-781,-340,600,-985,-139,-391,73,-691,-109,-923,736,-175,-343,-148,375,260,386,-357,-663,478,89,905,-756,-459,-972,825,610,-6,420,-272,35,-129,740,-645,992,-37,500,-983,405,365,699,438,153,21,286,-723,494,72,-94,765,929,170,967,665,-487,605,657,878,-211}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],int,int):double",
            new int[]{81,-907,368,305,-168,737,-508,-56,449,425,708,-891,40,-300,787,-41,-983,984,-942,678,-400,289,639,81,286,131,-364,-363,-518,114,733,862,319,-867,-563,784,970,548,367,659,-760,-180,-625,-557,-917,-917,-946,-326,-162,924,256,-644,427,242,573,-806,556,466,464,99,-979,652,654,-708}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],int,int):double",
            new int[]{780,-934,944,-547,-828,-88,925,-55,867,557,-627,564,863,170,-562,-144,445,-303,219,802,839,133,-429,-720,583,-621,206,640,-500,-698,-760,-435,-3,-832,-891,-927,671,969,947,-961,-303,329,-113,950,351,-355,-448,-303,106,-68,14,-783,284,415,-112,493,949,359,915,52,-139,-177,365,441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],int,int):double",
            new int[]{249,443,794,-1000,206,-960,547,1000,-670,196,-1000,-561,-491,1000,-1000,947,1000,-1000,372,-947,-1000,-1000,-1000,-28,-560,-275,959,1000,-1000,-875,627,-317,-946,1000,927,-831,343,-659,616,310,-669,-1000,441,-1000,647,354,-376,-212,-255,492,-737,-318,-30,33,1000,515,-1000,1000,-1000,-443,977,-1000,-86,-214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "evaluate(double[],int,int):double",
            new int[]{-15,642,1000,-61,203,1000,673,875,-1000,842,419,-1000,900,188,1000,926,-483,44,109,-981,660,962,1000,-309,-970,-819,-1000,1000,-939,-1000,-90,-1000,1000,-174,-141,320,114,-287,736,342,-317,910,-327,1000,-45,-865,-219,451,-667,999,546,-460,-523,789,386,103,-1000,-1000,143,401,-717,547,-369,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getN():long",
            new int[]{-523,783,711,-746,-231,940,-620,-848,-943,-526,-275,-672,181,181,-639,-798,167,499,81,-655,908,346,321,673,-89,-471,-800,-472,-188,829,-923,-626,-258,-332,36,-982,654,239,-318,437,188,-63,-780,577,796,979,-104,818,-511,236,587,-807,781,-562,-435,-111,206,177,-725,105,117,-572,360,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getN():long",
            new int[]{842,-367,-1000,378,1000,305,87,1000,626,-18,858,42,3,1000,-1000,-1000,667,-441,-352,223,828,829,703,-297,404,721,-399,1000,1000,476,66,1000,-57,-1000,-1000,275,-1000,-1000,-254,952,296,-1000,-486,508,445,1000,-1000,345,-1000,-624,383,-457,221,1000,-405,-444,-272,723,-668,-1000,-129,340,-1000,9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getN():long",
            new int[]{383,-347,84,250,82,996,816,498,316,-448,309,-372,859,-756,-880,251,524,-534,-278,-384,845,290,595,114,962,-185,914,-277,428,5,-682,929,79,232,-318,788,-925,-339,-173,-999,756,620,-339,-846,-967,-890,-643,593,731,-247,-99,798,-522,644,-455,579,-448,792,566,939,-578,-842,-941,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getN():long",
            new int[]{-330,-347,292,1000,82,492,-929,822,-476,-1000,301,-703,-987,361,-179,-1000,1000,-1000,1000,-1000,845,290,-168,-522,-950,866,1000,-1000,1000,-612,-421,-534,-1000,1000,-1000,1000,-1000,672,-35,-999,959,1000,-1000,321,1000,39,-160,1000,490,-97,-1000,1000,670,-60,-215,233,201,167,153,-1000,-443,70,18,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getResult():double",
            new int[]{662,918,-1000,-297,-439,1000,1000,303,-62,-1000,590,457,-619,-603,-1000,736,642,-520,566,848,527,880,-487,613,543,-768,1000,396,-106,-73,-241,-1000,-871,385,461,-1000,-451,648,74,986,-304,999,-219,1000,-343,-239,692,-754,-1000,-503,-907,-228,-147,1000,1000,1000,39,-1000,-337,162,-602,1000,1000,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getResult():double",
            new int[]{743,823,-758,-772,-58,1000,909,298,-62,-1000,708,699,-1000,-875,-708,-1000,642,-354,318,631,731,-950,-915,460,1000,-480,419,881,796,685,-49,-1000,-645,-570,1000,-1000,-189,543,-2,1000,-73,1000,-860,987,-105,-329,735,-1000,-1000,-503,-1000,-527,360,1000,1000,1000,-87,-1000,169,162,-287,1000,833,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getResult():double",
            new int[]{813,-787,966,984,92,1000,-624,485,-1000,975,711,338,145,-1000,-196,-119,-967,-748,-1000,-875,-644,42,-487,-256,543,-768,-681,-323,938,540,499,423,-871,385,683,662,539,527,-678,-592,-296,-874,-219,-887,-458,-418,-580,-904,-765,1000,667,1000,-147,-252,-270,91,39,966,-261,-1000,905,-488,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "getResult():double",
            new int[]{-144,458,-371,-1000,-634,1000,-325,860,315,-278,-278,-411,173,562,-124,294,-438,588,415,857,1000,-1000,369,-206,-184,-840,761,43,780,906,1000,-696,620,-755,-818,1000,463,-147,358,-125,-75,804,-213,505,653,220,-610,279,-265,-819,-225,-164,-438,-248,-184,1000,-60,-1000,-183,-362,-46,725,768,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "increment(double):void",
            new int[]{1000,-712,-664,1000,-291,265,-62,-768,696,700,1000,-1000,-952,-296,-977,-564,-286,472,937,84,207,-568,1000,-1000,-337,494,-1000,1000,-76,1000,-493,-866,660,306,1000,-690,-1000,459,74,-297,-321,615,-721,759,-102,-690,748,-1000,143,1000,-687,642,194,338,-580,-238,-1000,267,429,-247,783,-32,1000,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "increment(double):void",
            new int[]{1000,-932,-916,1000,-1000,934,-280,-1000,1000,669,434,-1000,329,-852,-1000,1000,-507,-39,1000,1000,-746,275,573,-1000,-707,307,-1000,27,-1000,665,-893,204,1,-524,1000,-1000,713,1000,218,528,-1000,768,-828,-422,-53,-1000,-677,436,174,-229,222,1000,584,839,222,631,-47,143,1000,-192,-740,-182,-153,969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "increment(double):void",
            new int[]{112,-607,-1000,-272,893,-1000,-1000,-978,-981,1000,1000,-1000,399,-1000,712,1000,253,1000,1000,18,-973,-637,-796,-1000,-308,438,520,-1000,431,852,-1000,1000,428,-797,1000,-1000,-1000,1000,80,1000,-1000,936,-51,-1000,1000,-849,-585,-799,1000,-930,-1000,432,1000,1000,882,755,-860,383,-1000,-452,-485,963,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "increment(double):void",
            new int[]{1000,-452,-773,602,-287,-55,105,-1000,231,376,857,-250,-1000,-1000,-803,836,-400,920,1000,373,51,-2,1000,-1000,-126,-7,-400,497,164,726,-562,-984,16,199,1000,-1000,-1000,1000,-30,-423,-1000,218,-834,789,-65,-40,872,-675,785,1000,-818,311,320,531,-1000,474,436,847,-266,218,808,117,750,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "isBiasCorrected():boolean",
            new int[]{-757,-7,-290,322,-739,458,-1000,-39,833,177,401,178,-615,-266,-480,-6,236,-1000,-956,751,-389,-707,-605,192,-1000,27,-672,-157,540,-792,602,-85,-321,1000,-382,1000,328,59,249,-500,577,-1000,696,-293,-976,683,263,426,-366,977,-796,-434,780,365,-598,394,-655,-1000,38,912,1000,-865,634,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "isBiasCorrected():boolean",
            new int[]{-349,588,-616,-576,-866,-263,568,701,-1000,519,-1000,1000,951,922,256,-1000,1000,928,-64,731,-1000,-843,1000,477,1000,121,480,33,601,-567,370,-212,1000,-665,-1000,-581,-1000,1000,394,-1000,-479,56,313,-117,1000,-169,1000,-656,-947,698,-187,348,-144,-346,-146,306,-910,1000,692,-139,-1000,757,-833,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "isBiasCorrected():boolean",
            new int[]{201,748,-379,364,27,698,845,-277,-389,862,-718,288,635,-649,447,-746,-370,-120,-147,287,-173,208,-782,-444,430,465,-746,-942,6,404,-689,861,604,274,-177,731,-854,343,229,-154,677,901,865,-783,115,35,155,877,-691,371,368,880,395,583,204,-269,723,504,497,-4,957,378,457,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "isBiasCorrected():boolean",
            new int[]{-929,138,467,836,374,61,630,344,690,615,-126,-347,-550,-548,462,-1000,-480,-545,-239,-1000,542,734,-420,-390,1000,509,246,68,-545,929,-213,1000,-475,-836,330,-694,-1000,-177,485,-668,384,-932,525,-14,228,431,715,360,-322,1000,14,1000,-853,-855,-193,413,-182,-421,-586,-265,393,759,200,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID|isBiasCorrected=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "setBiasCorrected(boolean):void",
            new int[]{-1000,478,231,19,-969,1000,1000,-918,-399,-882,1000,-122,-618,1000,1000,123,-718,-786,109,-895,-492,-741,-382,234,-1000,997,-407,-786,169,209,1000,423,-1000,99,-1000,-442,221,369,65,828,916,-165,-986,603,-914,-1000,-924,89,-393,584,1000,-1000,648,987,252,-741,1000,-146,-409,392,127,1000,-350,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID|isBiasCorrected=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "setBiasCorrected(boolean):void",
            new int[]{1000,933,-496,934,140,642,-680,-592,367,-774,-1000,-27,173,765,-945,877,-549,-1000,-891,-313,-303,-982,-106,1000,-50,1000,1000,992,1000,163,-801,1000,-1000,-328,4,-138,20,187,-873,-718,-1000,150,-300,-761,-390,-321,-1000,-934,-99,-347,1000,-736,-1000,-96,-400,-456,-393,-577,795,217,503,1000,270,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID|isBiasCorrected=java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "setBiasCorrected(boolean):void",
            new int[]{-1000,-327,920,-292,-1000,1000,1000,-877,-852,-1000,579,-205,-360,-176,1000,451,1000,-306,-746,-1000,728,-322,-1000,-519,197,25,-778,-536,1000,820,1000,505,-1000,239,-1000,-1000,-795,-800,-157,647,1000,-587,1000,552,411,-874,-1000,333,82,1000,549,-1000,469,1000,-1000,-887,1000,-408,-117,-1000,432,-218,900,-34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID|isBiasCorrected=java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.moment.Variance", "org.apache.commons.math.stat.descriptive.moment.Variance", "setBiasCorrected(boolean):void",
            new int[]{-113,-327,-28,908,-614,-748,1000,419,-1000,-603,1000,-205,-488,870,1000,949,4,85,1000,-224,-1000,-24,933,-519,903,951,832,-536,-675,1000,1000,-1000,-1000,239,303,1000,673,1000,1000,-282,1000,-1000,-660,-426,-566,-176,-125,676,-1000,1000,-369,-1000,79,360,1000,-1000,666,408,-117,1000,432,1000,-1000,-1000}));
    }
}
