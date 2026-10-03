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
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{228,-639,142,-157,161,-560,904,-255,323,728,-784,122,-170,-1000,-631,902,1000,453,978,-913,-448,213,122,376,1000,-488,451,91,-1000,542,393,-270,-193,11,407,1000,623,-863,-463,951,307,-748,-791,-407,73,997,1000,326,-391,-563,867,-1000,333,28,165,391,384,350,254,206,-910,831,-472,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{-581,813,-24,-51,-393,598,599,525,840,134,1000,-143,31,-871,-544,-1000,-1000,-1000,-585,933,447,-100,587,-319,-964,1000,233,60,645,-324,-568,345,216,-521,-31,-706,577,889,633,280,-708,851,600,687,-118,-215,-1000,231,232,281,-10,-287,-203,-360,-346,776,-383,330,987,-1000,286,219,-840,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{-192,-577,72,389,-851,830,998,220,1000,264,-907,-226,-967,-1000,-624,1000,912,108,155,-997,-153,-1000,261,831,-21,-56,339,266,-1000,-233,509,949,66,244,58,1000,917,454,-776,44,436,-995,-1000,-590,219,1000,1000,-283,354,26,876,-741,845,-483,525,378,865,447,485,-307,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{-383,-873,-1000,-876,912,-1000,-399,621,-452,-72,-158,-899,-1000,-1000,-340,725,1000,-259,1000,-438,888,693,1000,901,1000,-1000,1000,-1000,-149,-1000,251,1000,-97,892,1000,1000,362,-126,-288,24,220,187,-1000,-412,-1000,-880,53,853,1000,-1000,968,262,1000,-1000,366,412,389,-1000,-1000,583,-1000,-465,825,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{-790,713,-674,759,-367,77,-929,-414,-922,-973,451,53,581,1000,666,-1000,-1000,-929,-752,1000,461,-333,622,-634,-1000,-1000,641,-1000,1000,-1000,-1000,1000,-597,-591,48,-1000,-272,849,463,-728,-96,1000,203,756,96,-1000,-647,1000,736,1000,-234,1000,-487,-715,86,1000,-219,-1000,-1000,-526,56,-198,-217,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{-383,829,-133,-876,68,389,-339,621,-452,-72,-158,780,109,-310,-1000,-1000,-1000,-315,-622,992,460,32,1000,-60,-1000,-28,-1000,1000,583,-753,-500,-565,617,1000,-1000,-199,578,-126,624,-71,109,1000,-262,-48,1000,-364,53,853,1000,1000,498,963,107,-250,133,412,508,-374,-1000,-705,-1000,-585,767,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "addValue(double):void",
            new int[]{-431,798,52,94,741,-1000,-283,823,415,-817,939,-671,-1000,-367,-1000,-248,-1000,215,-346,1000,1000,90,292,-232,563,-704,1000,-851,1000,378,-895,923,-594,-668,1000,164,-632,-246,202,-98,-1000,1000,-20,1000,-1000,-1000,54,1000,1000,-509,145,-308,-487,-985,765,1000,-212,-281,-1000,-700,489,-1000,-146,23}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{417,203,200,77,-360,493,-460,617,-105,-710,483,-61,-150,270,-859,-918,-963,-193,788,-911,-264,-301,-552,-751,211,659,-322,-725,-762,487,528,81,-340,-846,136,-933,361,-279,716,370,-537,938,-214,436,862,671,416,417,-763,875,991,-593,-411,748,-478,338,51,898,923,-524,-789,-107,-214,881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{-605,168,-861,476,-222,-113,-126,200,-90,-636,411,259,1000,270,1000,-848,574,-443,-1000,-694,-338,714,300,-374,41,1000,-915,385,-736,867,416,97,-676,57,-246,-300,374,167,304,1000,-1000,560,44,157,1000,777,-1000,503,234,308,1000,-965,411,490,148,349,620,1000,747,-558,-1000,-413,297,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{73,-739,-538,-338,249,-80,4,-879,495,838,972,-412,-456,186,-475,481,-843,20,790,6,-791,193,-44,-855,886,557,111,409,531,973,439,-357,502,-975,-152,419,-940,-57,416,-880,733,244,-869,651,-136,-173,-675,-46,-869,899,921,929,-291,-31,-838,232,436,-623,847,500,-133,-656,-753,-6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{-245,106,854,174,1000,551,-76,123,-878,-649,67,-54,30,-948,676,1000,-1000,-494,395,667,-264,214,-94,-562,-631,-1000,185,-225,1000,128,19,-516,-1000,-384,-690,-1000,51,-914,252,-240,1000,-598,1000,-714,862,383,148,-973,126,45,302,-947,690,-761,392,1000,339,-868,1000,-784,-1000,428,784,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{672,217,1000,-447,81,972,-330,-644,635,1000,-1000,-1000,-899,-1000,1000,1000,910,-452,1000,1000,475,1000,1000,-1000,-1000,-1000,1000,-763,1000,-1000,-1000,-1000,-696,323,130,-1000,51,-939,1000,-1000,1000,-1000,1000,-1000,-1000,-1000,1000,-1000,-1000,388,-1000,-35,-231,-541,-703,642,185,-1000,-1000,-462,1000,449,742,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{736,-529,1000,-803,184,20,1000,858,486,1000,-88,-1000,-1000,-1000,897,1000,784,-1000,-18,881,-1000,1000,1000,-1000,-1000,-1000,1000,-659,943,-1000,-1000,-487,-13,239,-508,-1000,1000,-605,1000,-688,1000,-988,421,-1000,-1000,-1000,-1000,-1000,7,1000,-550,276,-589,-411,-947,1000,600,-1000,-1000,-1000,945,502,296,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "clear():void",
            new int[]{30,-393,160,838,371,-45,1000,574,-33,1000,-288,-614,-277,-1000,1000,1000,1000,-859,-642,1000,-17,1000,1000,-449,-1000,-1000,554,31,1000,-1000,-1000,-1000,-976,1000,-481,-1000,1000,-447,1000,-92,1000,-1000,1000,-1000,-1000,-1000,21,-1000,234,-1,-1000,369,355,-1000,197,889,774,-1000,-1000,-643,1000,-218,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.SummaryStatistics", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "copy():org.apache.commons.math.stat.descriptive.SummaryStatistics",
            new int[]{1000,-989,1000,-646,-20,227,-785,438,430,227,433,61,187,116,-1000,319,143,11,31,-37,-72,-852,-195,-792,-298,-514,-363,-20,256,1000,464,-1000,885,211,159,1000,-110,-143,444,400,-1000,671,-42,-81,258,564,795,-4,1000,1000,-1000,-109,-189,244,178,-1000,-1000,131,-475,-232,-124,-140,709,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "copy():org.apache.commons.math.stat.descriptive.SummaryStatistics",
            new int[]{753,602,69,-394,515,-207,707,-65,796,-776,81,558,-916,240,109,460,696,-354,-749,-192,-465,-325,722,-360,941,775,236,-714,923,347,487,-747,251,790,790,-227,684,-584,-862,-279,-46,-891,299,-784,35,-36,-479,-183,-313,551,923,972,-686,962,682,126,-244,319,-193,724,809,-947,-574,-754}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "copy():org.apache.commons.math.stat.descriptive.SummaryStatistics",
            new int[]{230,-1000,879,-688,960,916,-290,-562,734,-546,920,-1000,495,-658,-1000,488,568,-354,195,-821,488,-315,304,314,-883,-747,728,960,-63,1000,-73,-1000,-791,-615,-971,1000,643,190,493,153,-509,1000,-750,-1000,197,1000,651,-426,516,657,940,508,-248,58,-565,781,-239,1000,353,-101,1000,-399,-973,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "copy():org.apache.commons.math.stat.descriptive.SummaryStatistics",
            new int[]{-560,285,1000,169,-451,-1000,342,139,-310,519,82,-988,328,1000,765,-76,435,1000,351,-44,-1000,-90,-167,-540,1000,693,-1000,-362,542,226,623,845,-1000,-54,-1000,129,616,129,-390,-1000,221,1000,-1000,1000,-675,1000,1000,91,1000,-37,-1000,-241,-1000,363,260,-392,38,66,203,-1000,66,-1000,188,26}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "copy():org.apache.commons.math.stat.descriptive.SummaryStatistics",
            new int[]{271,466,914,144,-259,-1000,1000,-645,338,1000,452,-833,-1000,-795,471,-1000,-1000,202,-423,-1000,-1000,-472,-310,-45,90,1000,1000,-1000,193,-1000,911,-186,-739,-1000,-165,1000,255,999,61,-660,-61,973,679,-587,1000,-565,-1000,-1000,890,-1000,-1000,526,-1000,49,-1000,71,328,-532,1000,-901,1000,-1000,851,-252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "copy(org.apache.commons.math.stat.descriptive.SummaryStatistics,org.apache.commons.math.stat.descriptive.SummaryStatistics):void",
            new int[]{-637,874,-946,-850,-690,-443,-800,-9,353,489,-623,-685,281,279,-797,134,-821,161,777,682,-16,320,595,-24,-239,981,852,258,-879,9,847,32,178,-305,-801,588,942,-623,-851,201,197,-377,349,-498,-488,-233,-775,927,300,-184,259,580,-413,-61,348,405,539,76,-228,93,653,548,245,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{1000,-65,-330,4,-1000,966,385,114,901,767,-1000,-114,-1000,308,-574,672,1000,1000,-646,-482,-1000,663,219,99,-333,-920,-686,702,-1000,550,975,828,370,644,340,114,-1000,-474,873,329,-521,-36,-609,867,624,883,849,-178,155,665,273,-543,647,634,-730,-1000,345,-127,-46,538,476,558,-80,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{-1000,-687,671,-94,374,324,-578,1000,-1000,743,476,572,-487,-788,-826,-740,-876,1000,-421,-307,860,495,441,169,355,319,459,-17,-1000,44,58,20,856,245,1000,330,-538,-805,661,549,1000,815,604,-44,-437,1000,-374,375,489,-92,-452,1000,1000,1000,-1000,-182,-733,-40,401,-58,-564,26,168,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{-357,110,489,-1,-1000,-1000,706,-22,-125,-1000,914,1000,-239,-534,-548,-201,-1000,-673,-463,-84,-822,602,-456,753,1000,-439,-723,454,596,-75,799,-82,-443,-240,592,-992,97,-647,-1000,354,-1000,23,1000,-948,-897,-1000,-851,311,531,417,621,1000,-229,-18,705,910,733,214,624,624,-14,1000,865,696}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{884,297,-1000,-796,-228,-979,750,366,865,744,-1000,547,-291,-266,-52,1000,1000,-243,-669,883,1000,60,456,937,1000,-760,801,1000,-1000,597,-1000,-1000,-685,-1000,838,781,-260,-1000,-377,-1000,-140,-615,1000,1000,-1000,-487,910,-1000,977,692,288,-1000,-685,738,-172,-1000,753,705,966,-1000,-1000,-1000,-298,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{142,831,-20,-412,-706,280,319,675,771,20,-1,466,-157,175,-476,518,20,20,-20,-323,-260,450,-121,701,-936,-20,71,1000,-20,378,490,810,-194,868,-91,19,-526,-20,132,-77,-1000,-18,-28,271,331,422,20,-396,-24,106,250,-441,-153,165,400,-706,446,-1000,-350,-1000,400,44,489,227}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{180,163,1000,-813,319,-599,638,1000,1000,-1000,-262,-165,486,1000,476,797,-1000,-98,1000,1000,-332,123,885,389,829,1000,-236,-1000,-275,377,1000,1000,-1000,970,-786,634,574,1000,306,-1000,-309,1000,-255,-113,1000,-1000,-982,273,-1000,-825,-846,1000,-904,-521,-1000,275,-1000,1000,-938,-729,-219,-178,1000,-835}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "equals(java.lang.Object):boolean",
            new int[]{-1000,195,765,1000,273,497,243,-1000,544,44,-606,-814,-6,-443,-179,444,-124,-236,-661,-1000,1000,1000,-1000,252,987,-240,-1000,-1000,996,245,-334,212,29,-1000,-349,404,1000,1000,2,-300,-216,-415,1000,347,-422,-1000,-156,399,-251,-317,-435,113,-150,-477,441,1000,-92,880,361,126,59,1000,139,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.GeometricMean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeoMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,-437,-1000,-542,1000,650,1000,-559,-822,172,833,-832,-1000,-389,1000,-1000,-948,-1000,1000,364,29,-1000,363,-1000,1000,-504,-2,-186,-447,-1000,-624,-336,-888,506,-64,987,293,167,1000,749,608,252,304,-724,1000,601,-270,327,813,-853,925,-533,-449,1000,228,70,637,1000,23,-1000,1000,907,241,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.GeometricMean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeoMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-269,-1000,-826,-91,-343,935,-24,-238,-504,-535,192,-415,-1000,-539,27,-260,-520,-245,215,788,-718,-874,-4,-944,950,-453,-120,-138,571,-462,-600,1000,-508,-632,-138,-413,150,536,446,-313,356,-638,-163,-724,-18,-383,-768,367,589,-685,1000,-279,452,968,-551,140,-625,1000,826,179,1000,1000,845,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.GeometricMean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeoMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{772,-375,226,-356,274,1000,363,1000,560,-389,-316,903,-1000,-1000,972,-158,1000,1000,-271,1000,-254,1000,639,221,-80,1000,-52,-129,-902,-436,-1000,-1000,-131,813,-893,333,-40,937,-1000,645,599,-839,-16,-1000,-1000,-896,1000,1000,-1000,165,-655,-674,-630,-727,789,144,-371,-1000,-75,1000,325,-1000,-1000,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.GeometricMean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeoMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{45,-663,-1000,-356,-1000,-461,-469,-541,166,-389,85,476,-1000,-1000,-649,404,89,78,-121,1000,-1000,9,-1000,-404,929,-223,-192,-129,454,4,-966,990,-6,-676,594,-1000,40,839,391,-1000,640,509,-839,-1000,-454,-1000,-493,386,-388,-760,1000,-637,1000,954,-711,-189,-802,1000,761,685,-1000,859,1000,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeoMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{119,-263,-826,-81,-118,-1000,-90,-1000,166,-1000,387,486,-1000,-288,-557,1000,-1000,-393,677,33,-1000,-361,-1000,243,1000,-1000,538,-129,860,252,-392,1000,349,-733,1000,-47,228,240,446,-1000,491,522,-371,364,257,-371,-536,-385,75,-700,507,98,755,954,-848,-695,-744,860,-688,-715,-1000,365,1000,283}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeoMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-349,-909,1000,-862,-421,-428,1000,175,757,72,1000,487,-215,-1000,-1000,-754,910,65,-565,1000,-1000,9,-1000,-1000,1000,-245,-915,1000,1000,656,-806,1000,-161,-483,-806,1000,1000,1000,163,-692,1000,1000,-1000,-1000,-493,-1000,-712,1,-77,-1000,1000,-386,-1000,106,-1000,-362,404,1000,736,1000,131,489,1000,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeometricMean():double",
            new int[]{-422,-419,1000,124,240,632,1000,-884,-779,63,1000,-808,-291,144,-391,-1000,596,936,-1000,-1000,-181,49,1000,1000,-1000,382,-790,-794,1000,-415,1000,491,1000,1000,564,1000,203,894,-45,-609,-1000,779,-1000,-1000,-413,-821,-1000,1000,-1000,-1000,1000,231,-652,-41,-638,1000,828,-1000,-1000,53,-670,906,27,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeometricMean():double",
            new int[]{1000,-999,-232,-181,-341,1000,319,-621,452,-25,1000,920,1000,377,-736,924,620,-617,149,-358,-933,1000,1000,1000,-160,95,717,325,1000,-1000,1000,366,-406,1000,797,1000,633,67,-261,-75,-40,1000,-1000,1000,-687,-625,-1000,709,-1000,-1000,1000,-31,-1000,-501,-159,1000,650,541,-382,385,-715,612,-398,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeometricMean():double",
            new int[]{-470,-287,524,774,-856,547,833,116,-286,-348,915,-230,1000,-1000,-1000,356,612,-1000,-903,396,-906,824,955,1000,1000,-165,-826,546,848,-924,-631,508,-649,1000,39,422,695,415,807,-444,-387,343,-1000,356,54,368,89,1000,-1000,-1000,-100,753,-193,90,586,1000,667,-498,-979,-333,-595,1000,-686,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeometricMean():double",
            new int[]{-1000,131,1000,-251,562,-1000,-830,360,958,-1000,-975,-1000,-1000,1000,144,-976,44,827,-936,445,449,-1000,-1000,21,-1000,437,840,-1000,-986,1000,-1000,553,458,-424,-389,-784,-564,-247,416,-790,915,-784,-153,-1000,-202,-500,543,820,1000,585,-699,-420,1000,645,287,-886,-992,-123,-21,1000,-469,-956,-641,788}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeometricMean():double",
            new int[]{714,-583,460,392,389,751,341,1000,734,-1000,396,799,767,632,-1000,-1000,1000,-19,-1000,693,-295,1000,1000,1000,-41,-51,-162,968,850,-729,-1000,55,-730,393,1000,343,1000,-228,1000,-754,-1000,301,-1000,967,-779,278,-219,1000,-1000,-1000,12,860,727,884,995,1000,39,-318,-1000,206,-1000,872,-869,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getGeometricMean():double",
            new int[]{-876,-419,1000,-841,-201,484,-1000,1000,1000,-1000,-486,1000,-378,1000,-457,-1000,1000,-125,-1000,-454,-95,640,1000,1000,-1000,224,333,-483,-436,627,-1000,-10,397,-478,1000,-578,1000,1000,831,-633,-1000,1000,-1000,455,-783,-425,562,1000,-907,-887,147,-317,374,1000,561,1000,-371,416,-616,1000,-671,-1000,60,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{134,153,-368,-6,-1000,216,1000,-589,437,72,1000,388,-42,-202,-952,-491,864,-969,1000,1000,36,506,1000,456,-1000,-1000,833,35,-604,331,-799,-745,-278,-885,-877,710,1000,756,138,-500,265,-33,93,-842,-319,1000,-900,-883,71,1000,-827,-215,1000,1000,466,1000,219,835,676,544,-679,337,-1000,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{-217,-256,889,-345,-506,-941,13,840,-528,465,-22,-700,-954,978,2,-44,-245,876,630,-356,846,-327,-772,-413,766,636,-869,-543,-35,796,913,-72,-446,-606,598,17,408,993,-123,-779,156,112,-644,492,-839,187,-285,418,-915,889,-716,-358,900,379,-18,-502,-657,980,837,133,-300,-98,-632,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{1000,-753,82,368,461,1000,-1000,387,-902,854,-585,1000,1000,1000,1000,-429,-383,-236,891,-364,1000,-1000,208,1000,-546,-94,1000,377,-1000,-729,-455,-1000,1000,-417,397,-114,466,377,-1000,-1000,367,1000,-623,-438,-1000,905,-360,158,224,398,-297,-755,-290,-1000,1000,-527,184,-1000,1000,-375,183,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{831,972,113,639,-679,1000,85,-478,-553,854,628,-209,734,1000,-987,-685,1000,-236,1000,1000,739,-173,1000,587,-1000,-1000,1000,1000,-799,-56,-941,-1000,484,-417,-1000,-114,-388,1000,-1000,-944,1000,1000,591,-473,-1000,990,-1000,-466,155,1000,321,219,-730,300,478,-527,966,-1000,1000,975,994,1000,209,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{-635,1000,-621,-1,-604,-282,1000,-887,346,-1000,1000,1000,620,-414,-1000,-270,1000,-625,-32,1000,-1000,619,1000,498,-1000,-1000,1000,431,258,-470,-1000,-191,-391,456,-1000,401,810,-398,729,1000,-1,-444,-659,-604,588,251,-563,304,597,304,453,95,-71,1000,741,1000,698,-290,-13,584,-620,139,277,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNg==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{-1000,111,472,959,71,-798,-439,-1000,-6,250,-757,1000,293,-239,1000,-5,-740,821,-602,1000,-133,-1000,-177,954,582,1000,-784,112,719,-531,471,806,1000,389,15,122,-229,-811,41,-106,-757,-6,-882,389,-174,-538,329,995,-1000,-986,1000,-685,-400,-960,-383,-1000,-59,-1000,1000,1000,175,64,1000,553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMax():double",
            new int[]{300,-997,520,393,-236,1000,-1000,674,-1000,167,-1000,1000,1000,1000,1000,1000,49,-1000,1000,170,-130,-982,-1000,803,226,-1000,-320,1000,-1000,770,867,461,1000,-1000,-25,921,-497,665,-1000,-1000,-41,1000,-575,-1000,286,1000,-1000,1000,-1000,1000,-866,-238,-1000,-1000,1000,-1000,1000,-1000,65,62,1000,-673,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Max", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-289,713,1000,-731,-1000,90,299,561,-71,-354,872,1000,-35,1000,1000,449,-1000,-744,776,-1000,-983,-634,-984,123,-1000,532,-650,-1000,-491,-1000,-624,785,1000,-706,-1000,-458,480,-776,-1000,-756,653,-799,443,-246,163,273,1000,1000,1000,1000,1000,-1000,344,645,-1000,771,-1000,-1000,-1000,414,-170,-1000,-821,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-807,-469,1000,929,-532,-379,-447,197,1000,223,-1000,461,-377,719,1000,-1000,726,-249,376,-928,-690,183,1000,640,-225,576,-1000,-523,-1000,1000,444,4,321,-1000,400,87,276,-91,191,152,-269,-264,-70,766,-1000,1000,-618,382,109,-224,616,-206,99,-1000,381,-852,756,-106,1000,-498,908,-1000,-473,895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Max", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{311,986,378,-76,561,-725,170,-827,-7,773,413,494,287,875,979,968,258,-453,-529,-881,918,-18,753,588,607,147,-376,399,226,-713,59,132,-289,-127,347,780,1,442,345,56,-38,314,-920,495,-973,-747,-33,-193,-398,-158,-986,-365,696,-515,584,-874,-289,826,-507,-466,795,-371,-460,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Max", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{373,-79,-896,359,481,1000,223,1000,542,166,-330,-74,854,978,-1000,701,1000,1000,-832,709,445,173,-68,132,1000,878,112,-49,-6,678,711,-336,-465,-1000,383,-1000,-1000,862,869,297,-616,441,-215,-142,975,-989,-351,-450,-96,-707,1000,827,880,-820,-107,92,-293,-163,563,-93,730,1000,-14,-826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Max", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,-136,-161,-581,-1000,-1000,1000,592,856,483,368,1000,-615,-377,1000,1000,706,-20,-1000,-997,1000,79,-126,-607,293,1000,-254,-450,1000,-561,-960,106,-289,-1000,-467,-896,-1000,571,-457,49,632,-196,-39,62,490,-1000,-695,934,1000,1000,678,889,847,-435,-833,533,-1000,-697,31,666,-1000,406,-1000,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Max", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{451,71,254,-668,169,-609,923,1000,-548,496,244,170,-260,-755,-105,28,406,-588,-520,-146,510,-142,307,-625,769,1000,-854,82,185,58,94,428,-588,-1000,-983,-607,1000,-1000,-104,-1000,488,-1000,-11,13,-43,-807,534,161,524,484,-750,291,1000,468,-107,842,618,546,-751,774,-1000,-80,-756,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Max", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMaxImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-205,-817,-1000,-557,-936,1000,165,-257,-382,-149,1000,336,1000,1000,-131,829,-1000,-1000,-1000,202,833,252,-495,1000,-439,1000,83,-609,-890,-253,-1000,-94,950,-1000,-1000,-1000,675,-380,186,-22,448,-827,-9,-1000,1000,-1000,272,880,291,240,133,1000,-16,-550,-25,433,-1000,-1000,-266,-240,215,-238,-321,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{-662,815,1000,-206,-400,-494,1000,189,901,-636,893,980,72,782,53,-446,299,483,-371,1000,972,1000,-347,-560,1000,-1000,-481,1000,1000,-340,639,778,-1000,-1000,-137,-997,1000,27,701,-573,-1000,-122,-328,979,1000,1000,245,-691,1000,536,-986,-641,-342,-1000,-1000,-1000,921,218,252,-1000,-246,-1000,-1000,109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{-129,-1000,99,-47,527,316,699,-70,-124,404,-129,-46,-1000,-171,-1000,-288,-1000,59,1000,606,-735,890,-371,-497,768,-760,674,110,511,118,759,67,771,-397,215,-1000,-463,-1000,868,-1000,463,425,-1000,285,727,1000,947,34,-794,23,670,-543,781,417,293,-445,-971,1000,915,376,1000,-963,59,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{294,205,-20,-752,958,-888,-255,783,896,884,928,-851,-355,-813,-1000,585,-165,1000,-630,511,881,517,-125,-785,614,142,1000,-120,1000,-1000,269,1000,-308,13,-715,-1000,-1000,-11,722,-478,20,930,-206,39,161,620,-673,-731,-949,1000,-523,-340,313,497,358,337,-579,-17,-265,-818,-15,-1000,189,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{-60,-61,-438,894,1000,1000,-1000,-419,-358,1000,945,-1000,352,-1000,-1000,1000,779,-1000,432,-534,801,-1000,4,-967,81,-739,804,-1000,1000,-1000,-359,791,-366,-129,628,728,-1000,750,-982,-694,1000,191,-352,1000,-1000,-940,-763,-191,175,641,759,-1000,965,640,806,1000,-829,-869,1000,-69,-454,1000,1000,236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{192,-37,-18,264,1000,795,-1000,-129,454,1000,953,-720,-32,-1000,-1000,1000,248,-1000,-167,-490,927,-686,34,-1000,81,-739,804,-952,1000,-1000,69,1000,-366,-129,-149,-387,-623,766,-96,-805,1000,1000,311,965,-792,-838,-268,-1000,104,594,759,-853,1000,397,806,692,-829,-235,1000,-69,-454,422,897,319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{-1000,269,1000,-167,-59,-71,710,696,-264,349,481,464,-870,-653,-445,-298,672,302,-188,425,378,425,-36,195,1000,-400,-280,290,-20,-50,-199,-129,5,499,823,-1000,746,-88,196,-1000,-1000,-90,230,-356,-964,1000,145,-45,1000,-28,-186,496,1000,-579,-1000,486,-79,-383,1000,-161,292,-370,-550,222}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{-567,327,-383,-470,234,-56,-351,-419,515,67,1000,-165,-1000,-640,-1000,-846,694,-810,-308,-734,157,598,71,474,227,-15,-681,1000,524,411,716,227,-597,-547,790,-76,-388,-312,-221,-769,-379,-1000,222,-256,-919,91,612,30,74,-851,-68,-1000,-493,-272,-948,337,781,283,714,142,-133,-462,893,694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMean():double",
            new int[]{865,50,-531,-771,341,361,-619,1000,-965,934,206,748,-912,-680,-72,929,-434,-261,-340,-1000,-123,-1000,-129,-227,-848,313,473,-257,-1000,-1000,921,150,1000,1000,33,330,-1000,491,-1000,-19,780,14,-56,-190,270,-900,-221,-1000,1000,-1000,1000,344,647,426,1000,1000,-1000,622,27,1000,1000,-334,288,737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Mean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,-145,-1000,-616,-1000,1000,487,-1000,782,-18,-69,603,-1000,-1000,1000,-99,-559,-1000,-458,-427,-189,-1000,831,-839,-1000,362,-64,-973,1000,1000,1000,-1000,-44,-1000,-513,1000,1000,326,-1000,1000,1000,343,1000,-233,1000,324,399,190,-1000,116,712,-555,-10,-612,-1000,-1000,-445,-1000,967,-1,456,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Mean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-203,729,8,499,-808,-633,628,-385,400,117,-1000,379,-145,-1000,-469,586,1000,-532,28,-940,-206,-785,-581,397,-973,361,-771,-20,56,381,-594,-16,52,-695,-925,353,576,201,139,29,988,-601,589,-411,185,985,1000,871,-180,1000,-16,-1000,387,-9,-1000,-998,565,-1000,-321,487,-157,-1000,-357,-818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Mean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-629,45,1000,543,579,-339,-1000,-428,38,805,199,-890,1000,157,-1000,388,507,153,1000,-337,170,1000,-1000,83,1000,-1000,-131,349,186,-1000,-1000,1000,293,637,-17,282,-654,-23,1000,-1000,289,273,-333,-500,-1000,1000,285,-803,809,-1,-565,-715,-1000,606,672,168,208,764,-81,-855,66,1000,-67,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Mean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-399,551,286,613,-1000,-77,-1000,393,-439,856,-1000,779,51,-506,-261,1000,748,-897,570,-705,927,-201,468,-578,-65,351,-906,305,-585,381,-1000,261,-72,-29,729,909,30,672,501,-905,690,-86,833,-1000,-508,382,1000,1000,1,1000,-1000,-710,-296,-277,-1000,-1000,806,-666,430,-913,180,-1000,-37,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-100,-321,-507,-504,518,984,-409,965,1000,584,354,885,-519,-337,855,-189,-825,744,207,396,434,-441,474,-1000,785,653,78,-266,148,566,-1000,-651,798,-320,-319,-1000,321,-49,-488,-1000,544,1000,770,-189,-441,-273,-255,283,-659,-959,62,-613,1000,-106,278,211,922,-762,182,-837,320,-711,1000,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Mean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{218,135,-291,134,263,-208,-400,785,-427,951,-769,-145,62,-713,250,924,587,-859,1000,-887,171,-1000,1000,-1000,-204,1000,-704,69,80,1000,-197,-126,1000,-37,-390,855,773,307,400,-653,1000,1000,1000,-803,321,1000,429,1000,-1,507,-526,-475,-426,-162,-826,-746,141,-1000,1000,652,617,-1000,986,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Mean", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,441,-1000,-121,-81,970,1000,1000,-664,1000,794,-28,-1000,100,837,-1000,-580,1000,1000,1000,976,-1000,1000,-1000,1000,125,-1000,-1000,1000,143,1000,-486,1000,-1000,1000,1000,616,-224,-498,-18,-1000,-1000,1000,-1000,-1000,-1000,1000,1000,-1000,1000,81,-98,-1000,498,1000,771,532,-1000,-360,-1000,26,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMeanImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-458,816,919,314,-1000,816,953,-1000,-439,-113,-1000,779,-407,-1000,256,-767,227,-1000,-1000,-1000,927,-572,501,1000,-1000,-155,-571,-882,439,-115,1000,-644,-1000,-1000,1000,163,648,-501,909,1000,690,-86,-731,78,1000,382,1000,274,-660,848,807,-710,1000,-415,-988,-1000,441,311,482,1000,-989,35,-1000,519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{-355,-511,1000,-282,1000,104,603,921,-107,-648,913,-888,-1000,963,386,528,274,-1000,-341,-56,-266,1000,-1000,-1000,1000,1000,-1000,202,-164,-763,552,871,-1000,1000,344,446,1000,655,777,1000,-980,-400,-1000,-839,678,1000,1000,1000,-703,-1000,1000,82,-435,1000,82,-1000,-265,-1000,178,-7,642,-1000,419,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{-54,-465,1000,-181,-233,104,-1000,1000,-824,-551,-327,-949,-1000,1000,-909,-809,166,-1000,-1000,-85,-1000,1000,-1000,-1000,484,-80,-954,859,393,-915,-879,1000,-1000,1000,-695,1000,1000,-155,511,1000,-138,-948,-480,310,-211,724,-1000,1000,-718,-956,1000,252,-1000,1000,-960,-1000,396,-464,-622,-122,426,-330,278,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{668,-59,796,-686,856,858,-261,-405,187,258,744,-610,803,845,778,457,-740,-648,-194,-802,-763,94,190,273,480,168,563,331,-842,-503,772,-346,279,-106,536,-644,74,614,-592,962,-700,-596,-631,-296,-660,14,-122,651,-370,-689,615,-63,-493,-328,-735,-244,-172,-831,714,-436,-88,-774,-213,952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{18,443,614,-721,-349,1000,-39,-503,-505,-78,34,-1000,1000,1000,112,1000,306,-1000,-381,-1000,-757,99,-161,-524,-260,102,712,483,-1000,-931,821,513,0,712,-686,-51,249,411,755,151,-1000,-965,-994,-530,-355,1000,633,669,-495,-708,-82,-150,509,-200,-1000,-151,42,-635,-176,-976,-389,-1000,103,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{-185,331,614,-891,802,-1000,1000,1000,-1000,1000,1000,277,-1000,1000,631,500,1000,670,1000,-1000,1000,-298,1000,-192,1000,1000,-1000,-1000,-1000,922,1000,-1000,617,886,1000,217,-163,77,-1000,-789,-810,1000,-1000,433,1000,1000,1000,-416,1000,-442,-603,-943,1000,1000,1000,330,-769,420,1000,-691,1000,-790,-306,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{-157,1000,-76,-721,-159,1000,328,-1000,151,-78,827,32,939,107,1000,1000,306,1000,1000,-1000,1000,-176,1000,938,-1000,495,386,-1000,-1000,-931,821,-1000,1000,786,-686,-1000,-1000,-1000,34,-1000,-839,-965,381,-1000,710,1000,-1000,-596,227,1000,-1000,-1000,-42,-168,971,1000,42,379,-407,433,-315,768,281,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMin():double",
            new int[]{323,-284,103,384,391,-1000,566,-731,-301,1000,-1000,-1000,-1000,-795,443,-758,935,1000,663,-498,1000,-1000,-49,757,1000,1000,1000,-49,-746,-414,609,-723,-648,-482,106,394,-856,883,-641,-798,-746,850,-873,-126,170,1000,271,11,-72,174,1000,-907,331,440,1000,11,513,-287,1000,449,90,46,-1000,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Min", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,943,1000,-106,1000,-400,349,571,-52,-261,-400,-406,1000,511,683,-1000,-816,-478,-796,-401,-1000,181,-1000,-62,-578,-356,47,1,1000,400,1000,230,1000,-553,366,400,-1000,1000,-911,-817,-1000,-257,-572,489,-647,89,400,138,367,-685,93,-455,-292,-283,1000,521,-1000,-939,-1000,-192,-458,1000,465,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Min", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,-353,997,-484,334,997,1000,-1000,-1000,-748,-336,-1000,-211,-562,531,1000,-374,-254,-271,653,-233,-179,-706,317,978,455,-698,-626,200,0,530,715,-291,1000,-577,-870,-1000,243,-935,-129,-455,276,-781,1000,1000,-266,-708,18,-1000,-1000,1000,-600,450,-397,-262,-197,-641,-884,622,172,-265,-657,-697,-131}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,314,864,413,736,785,1000,493,-1000,-425,585,-1000,556,1000,-358,-82,-1000,-255,-325,892,-1000,298,-591,-814,420,1000,-684,-143,-133,338,-175,1000,455,-1000,1000,-751,-1000,103,-984,-888,-745,-1000,-851,784,-661,-1000,-1000,980,-1000,-1000,175,-497,-727,-1000,-141,-31,-1000,-1000,747,1000,-787,258,624,-753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Min", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-400,-229,42,-596,1000,931,-70,-571,-1000,-1000,159,-659,-69,-585,271,559,-482,-1000,209,578,222,1000,1000,618,822,1000,1000,671,716,-390,-229,953,1000,818,-1000,-178,-392,885,-949,-179,1000,214,-1000,470,1000,1000,-808,-33,-978,-1000,233,-895,866,252,-1000,-1000,-714,-886,452,447,942,-7,593,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Min", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,1000,478,-426,1000,-1000,1000,571,1000,-1000,-1000,-1000,12,226,1000,-1000,-1000,-164,-718,-426,-1000,-1000,-1000,1000,-998,-1000,-388,-714,1000,1000,1000,-1,1000,-1000,-939,877,-1000,1000,-911,-1000,-1000,342,-572,388,-997,-406,1000,1000,367,-684,266,431,200,-389,1000,1000,-1000,-1000,-1000,-418,-1000,1000,298,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Min", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,-749,1000,-921,212,651,1000,-1000,-1000,-1000,154,-1000,-1000,-626,287,1000,-1000,-31,469,901,-169,-1000,-1000,1000,1000,-101,-407,-741,1000,591,-23,1000,333,-485,-160,-922,-1000,308,-1000,-1000,-29,-149,-1000,1000,-513,-230,-1000,973,-1000,-1000,1000,784,1000,-360,552,85,-1000,-1000,735,1000,-387,-1000,181,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.rank.Min", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getMinImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,27,40,272,-651,1000,-184,-401,1000,1000,-861,356,1000,-605,-967,-277,1000,-294,-680,-314,-204,778,20,-905,171,1000,-1000,378,-1000,-1000,1000,633,-7,296,-269,-447,852,869,383,808,-1000,-243,1000,-1000,-711,-914,29,-1000,907,-167,96,-508,100,-697,-258,-326,-96,720,1000,-173,563,35,-83,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{257,-55,412,689,-1000,415,-781,764,111,988,615,80,1000,-1000,-1000,-147,-70,1000,-1000,712,791,-1000,-682,-364,1000,-1000,191,-763,-718,494,1000,1000,-572,-1000,1000,-661,-1000,-94,395,-479,453,-1000,1000,1000,555,167,189,-296,1000,-1000,-400,-1000,1000,120,-982,318,-829,-1000,-1000,-1000,1000,655,-1000,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{502,-67,48,129,229,-1000,-380,-678,206,935,-414,-885,-85,-806,778,753,493,1000,-1000,531,1000,488,-1000,1000,-988,1000,-220,-1000,-1000,-266,1000,1000,545,499,1000,-1000,-1000,-1000,-1000,-408,-775,-1000,1000,294,-64,1000,-131,-400,721,-1000,-400,-1000,595,726,-1000,-923,-362,129,-401,-1000,-64,1000,-792,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{-454,-649,843,754,-991,792,484,-843,378,392,126,548,863,-393,-577,606,86,400,1000,40,81,24,255,-746,150,1000,627,-640,2,577,-27,197,-346,988,-47,196,975,673,-696,-686,321,-135,-77,324,185,-686,520,643,238,-86,-1000,-245,712,-573,816,122,-604,161,-296,-202,755,-1000,-638,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{909,-671,996,789,-779,423,90,-535,-325,-91,67,-992,572,594,-775,415,110,697,-551,235,844,-864,-262,230,-708,-956,-463,761,908,988,-40,450,-909,-650,102,-444,-841,-388,-442,682,301,858,992,-104,381,708,142,215,181,-873,994,-561,-805,-937,-966,-155,359,-295,-540,-93,305,317,24,332}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{-920,-457,702,-553,22,1000,812,1000,1000,1000,-1000,1000,468,-1000,1000,171,239,-1000,-591,-797,530,1000,-617,942,342,175,1000,994,-1000,-1000,1000,108,709,733,857,-175,1000,534,1000,-894,-1000,-472,-705,865,-720,-465,714,-394,608,-328,-284,-502,1000,1000,-362,-1000,-877,1000,1000,-1000,-249,393,-908,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{-3,-647,1000,482,229,-869,332,252,-109,-761,-1000,505,-217,401,1000,-237,1000,-930,131,564,185,-63,-36,45,1000,-732,66,-1000,296,62,935,132,-464,1000,207,-584,-565,-508,1000,187,-533,-170,116,-244,692,492,-195,1000,-893,-809,1000,-11,-1000,696,421,-1000,-226,728,1000,-1000,-1000,60,849,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{538,-889,-908,-311,-231,-606,170,-646,-1000,736,-191,-1000,-150,514,268,-1000,911,-1000,787,368,297,-1000,-332,1000,-610,1000,-1000,700,-36,-171,-1000,1000,354,545,578,-216,659,193,-28,-193,1000,883,1000,-489,-976,1000,-290,416,328,-194,1000,1000,-1000,-130,302,-365,943,-1000,465,-725,-742,-1000,567,-952}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getN():long",
            new int[]{-158,-903,127,678,568,314,-189,92,-487,90,-628,1000,-32,-1000,471,709,260,-310,-538,-360,-324,53,831,-1000,352,-771,944,762,-1000,1000,1000,514,366,-88,703,510,156,1000,1000,-1000,-660,43,-152,843,-280,-456,898,-1000,-190,-160,-208,-667,1000,404,32,-110,-1000,1000,-327,1000,-298,-655,-779,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{684,161,884,69,-510,205,1000,-599,-837,691,2,1000,1000,383,-1000,223,192,-379,343,166,399,12,-348,-805,227,555,289,-209,1000,-794,-555,-515,-229,429,326,-351,-646,492,186,-431,-1000,330,-673,354,435,249,-1000,431,-11,112,-1000,-63,20,961,-328,-853,-330,-1000,-632,1000,-549,294,-811,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{539,-963,793,-1000,399,824,-1000,-229,-1000,58,-331,-251,-1000,690,1000,706,1000,-837,-291,227,-760,-793,-1000,-601,864,-1000,-377,1000,-568,-1000,-145,445,216,-182,383,-252,604,1000,218,-1000,302,-717,629,-991,-1000,434,256,1000,1000,192,-79,1000,941,-385,-663,259,-1000,-1000,-192,249,-622,1000,-1000,-217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{203,-305,1000,249,376,-68,-693,-599,981,-306,-114,-480,1000,-809,575,-575,167,-695,1000,42,1000,334,-975,275,-469,-524,-154,773,583,-768,-1000,-1000,62,688,-512,-638,883,-594,446,429,403,436,1000,1000,456,1000,211,1000,1000,-1000,1000,104,62,-848,-1000,-127,599,-547,-896,-842,172,-146,-61,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{975,-87,-326,-681,1000,48,-847,111,1000,-1000,-80,-104,-81,119,329,-436,-1000,-3,-868,-1000,205,785,-519,294,708,-1000,238,1000,543,-834,1000,-87,-1000,185,-1000,-1000,841,658,891,-57,747,769,-881,209,1000,-22,-521,-896,-109,-252,350,212,-955,426,309,327,-114,462,1000,-823,-356,-503,-879,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{-141,-696,724,449,1000,-740,-1000,-79,1000,1000,-544,-1000,405,-1000,18,-211,-582,-108,-221,-895,887,704,-1000,554,-425,-1000,-865,692,1000,-269,213,1000,345,-300,-667,907,803,-1000,-460,287,1000,-205,-760,-776,419,1000,632,-669,861,-52,-152,-671,-1000,-11,-1000,-328,835,83,1000,-842,1000,166,-300,202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{-206,589,-506,-281,-109,9,562,-355,-972,-4,-426,388,167,727,-248,393,-666,17,640,618,-444,98,-159,-202,-327,320,447,-676,-404,-512,-554,-778,-233,-512,303,-278,742,420,-91,-518,-892,521,133,-362,275,-149,-667,558,-936,904,-663,-577,-517,116,671,-430,-416,-431,-923,915,-110,106,-249,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{-1000,45,647,-44,498,742,-1000,117,1000,-1000,40,436,502,-89,811,-328,-117,-715,-693,-732,554,475,-1000,525,-1000,-1000,-775,1000,1000,-162,816,635,-207,1000,-860,-874,-175,490,875,353,985,1000,-787,687,855,647,266,-787,1000,-756,1000,-84,-17,-355,-700,966,188,205,-469,-1000,-574,47,-569,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getPopulationVariance():double",
            new int[]{-317,-208,113,494,-1000,1000,321,681,-1000,-195,678,1000,-775,1000,1000,771,1000,-1000,349,-1000,-855,-600,-148,915,1000,477,-218,-1000,-758,-180,-45,-1000,899,892,957,163,-642,1000,1000,-164,785,372,386,-616,-538,-132,-81,241,622,527,1000,1000,612,-234,-297,405,-1000,125,-307,-287,-1000,-299,-948,596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{446,723,-616,-146,-400,-20,1000,21,65,-941,-362,-423,-638,271,1000,985,-29,-1000,807,-441,524,-958,-239,706,-1000,-1000,-986,-604,418,373,-994,1000,-367,1000,1000,1000,-22,189,379,555,-323,220,-331,-891,1000,420,400,-515,-460,1000,-1000,1000,-694,-64,-56,-550,48,-714,400,-1000,1000,31,1000,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{839,-848,129,-561,667,-726,-522,259,774,-444,-360,232,740,474,125,471,365,-772,-965,-450,873,170,-972,-638,857,571,-626,144,79,256,-445,-31,-451,-173,-368,796,943,214,586,914,324,114,210,19,398,-498,749,508,87,859,-526,652,53,-985,641,233,26,-419,859,950,237,821,-172,465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{1000,-310,463,582,533,-404,80,-691,276,1000,123,460,1000,1000,-381,-1000,769,-850,-1000,-524,656,-200,1000,1000,47,1000,-1000,-44,-916,154,-875,-888,471,-1000,489,510,493,-732,1000,212,-748,-315,650,-355,1000,890,-411,146,-1000,-272,947,350,140,-904,238,-1000,696,-564,-780,-1000,-395,1000,38,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{-1000,519,-1000,608,921,-400,1000,-409,-618,402,1000,-1000,-705,-505,1000,653,-1000,-773,685,-620,452,-1000,-1000,-1000,-512,-1000,-990,1000,992,-1000,3,616,-1000,1000,1000,-400,1000,147,-1000,680,-1000,-160,1000,1000,-1000,186,316,1000,32,-556,-1000,696,-4,-237,499,47,287,1000,-219,-1000,141,-1000,36,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{202,-198,-191,-231,1000,-530,553,-1000,-272,372,651,-474,4,238,159,-1000,-409,-1000,-583,288,585,-487,1000,279,1000,331,-1000,1000,-356,641,-385,-1000,-125,-943,421,-483,801,124,176,547,183,141,833,-848,345,174,-615,1000,-1000,177,501,274,-617,-817,-56,-1000,528,215,-920,-1000,1000,521,-824,34}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{-266,212,758,-996,1000,274,-275,1,-1000,1000,970,778,-65,-1000,-1000,39,150,-979,817,-170,726,108,319,-219,437,331,-1000,-400,285,627,-981,-1000,-142,-901,-587,-594,683,388,-382,877,-748,196,50,127,781,-206,-937,1000,123,23,1000,-389,619,-279,41,-1000,297,-25,-863,-1000,-696,-47,-974,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSecondMoment():double",
            new int[]{-1000,457,677,1000,142,-8,-1000,1000,-678,1000,1000,772,-208,747,-3,637,883,206,-732,1000,-100,1000,-104,-35,-1000,-1000,-1000,-420,-1000,-1000,-209,-60,39,-327,69,-541,-1000,-841,-47,427,-523,447,213,-1000,1000,699,-1000,-1000,172,-1000,1000,725,-517,1000,-1000,803,-1000,-1000,-1000,-100,532,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{1000,15,159,448,13,-49,-730,-353,-721,402,823,1000,-452,587,1000,783,-1000,-315,117,1000,-356,-819,28,600,-917,-323,1000,-41,1000,96,-134,-253,1000,-1000,527,-438,-1000,1000,-1000,-1000,548,1000,-539,738,-277,13,-733,-241,1000,-1000,1000,-1000,1000,-172,561,-146,496,-1000,-609,-416,359,-424,184,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{-312,387,206,179,27,495,-596,-752,102,-273,861,725,-619,-117,631,331,-1000,946,309,-31,815,-508,-472,195,-228,140,701,-147,1000,-430,103,-162,996,188,367,-411,-337,119,-714,73,-881,969,293,462,64,117,-20,-536,219,-361,363,-297,876,137,-159,-475,283,-432,-633,13,-311,748,169,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{286,272,984,-418,-1000,-1000,466,-461,-1000,50,1000,1000,22,295,1000,-480,-1000,1000,-549,353,-932,-988,902,-304,-765,1000,1000,-62,58,-205,1000,672,-858,532,-1000,415,-17,202,450,-691,-1000,1000,476,1000,1000,107,-428,-1000,238,1000,1000,-216,899,-1000,1000,-188,836,215,-231,-1000,-705,492,-498,-903}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:MS41MTg1MDAyNTA2OTUxMzE1RTk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{-796,485,15,1000,923,1000,-1000,-239,186,-1000,1000,1000,-1000,-1000,200,702,-512,1000,842,10,1000,-1000,-1000,-393,-1000,357,1000,-1000,1000,-1000,-401,-996,1000,991,14,-1000,-450,673,-1000,707,-1000,-211,269,389,662,108,332,-126,-341,-1000,-949,-693,1000,826,-314,-1000,329,-526,-1000,-616,-1000,-545,997,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{756,619,1000,-317,-627,406,447,8,-475,335,-898,930,-92,354,766,-501,-1000,487,-197,689,-371,-983,1000,423,-1000,864,1000,-628,-580,-112,1000,473,-352,454,-914,765,183,-940,1000,-912,-400,1000,412,995,789,-902,-974,-1000,356,1000,500,-656,634,-286,1000,-262,632,1,1000,-916,-1000,-104,-1000,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{1000,818,714,629,-939,-496,1000,-1000,-1000,672,1000,1000,-729,691,1000,-1000,-500,-140,364,1000,-244,-1000,-1000,347,-237,1000,-675,572,-738,1000,-266,-1000,1000,-126,294,491,-1000,-826,-355,-888,1000,710,1000,1000,349,346,41,-39,1000,1000,1000,-1000,1000,-172,349,201,1000,-867,-1000,1000,-383,228,-225,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getStandardDeviation():double",
            new int[]{-881,-549,1000,218,-666,481,-315,407,66,-273,-649,1000,-501,-530,453,837,-683,394,95,1000,335,-1000,233,-1000,-228,993,1000,11,588,-512,742,874,-1000,757,-1000,655,-174,271,745,621,-20,88,-222,562,194,812,-320,47,128,89,-272,-297,537,-550,664,-1000,546,-246,-10,-855,-1000,237,-424,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSum():double",
            new int[]{120,-147,435,-966,499,-1000,1000,-1000,-446,-49,-492,1000,-1000,-151,1000,-78,-1000,322,681,-517,-870,1000,-1000,485,-541,-508,-466,1000,28,36,1000,1000,-6,10,-1000,1000,19,-932,846,-612,-496,-1000,880,1000,-365,-1000,845,251,-112,271,328,34,102,-107,1000,-1000,460,217,858,1,-1000,-263,243,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSum():double",
            new int[]{384,-41,435,72,499,-926,1000,-1000,-446,81,-325,1000,-1000,-267,1000,-78,-454,322,681,-312,-421,1000,-1000,485,-249,-50,-372,1000,28,36,1000,1000,-6,-1000,-632,520,1000,-932,410,-586,-715,-1000,1000,1000,-290,-1000,1000,161,575,57,497,366,404,548,1000,-1000,288,-38,695,1000,-460,-743,243,-468}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSum():double",
            new int[]{928,803,-416,804,-39,-277,126,-833,-400,277,-63,-408,355,924,-340,-400,703,-556,72,348,-1000,-645,618,367,1000,-503,-466,-201,-543,256,-1000,-345,227,-408,-530,598,669,208,-1000,-48,385,1000,531,433,785,664,388,-101,-177,-981,902,970,1000,-584,-1000,-96,-1000,-326,-380,225,438,-166,-112,618}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSum():double",
            new int[]{1000,-519,-1000,-807,901,879,-1000,866,1000,-635,423,223,1000,320,-276,1000,1000,1000,-1000,605,1000,23,660,-1000,0,1000,1000,-1000,1000,1000,-1000,-693,1000,455,-1000,-621,1000,1000,-231,-947,-113,1000,611,-1000,-711,-981,-543,-11,-1000,-997,1000,-323,-32,337,1000,1000,651,375,-590,-401,1000,-71,-1000,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSum():double",
            new int[]{962,-431,-818,-556,762,-1000,1000,-1000,921,53,-302,-37,270,-1000,-354,410,-480,1000,-670,1000,-870,1000,-857,-459,582,79,334,371,-328,-779,764,245,1000,966,-1000,1000,-372,625,549,-1000,41,-482,-1000,54,-731,828,-176,292,-926,-800,544,416,-29,-694,-450,-974,82,-289,279,-594,-1000,593,150,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSum():double",
            new int[]{393,191,-1000,-178,-131,571,-106,82,-104,-208,450,-947,1000,-874,1000,1000,1000,565,-1000,179,722,-990,-450,-135,-770,1000,1000,-213,-995,-724,-399,-1000,273,-1000,-969,-1000,1000,236,-1000,-269,725,1000,1000,227,-186,1000,632,-1000,1000,-1000,1000,1000,933,112,1000,734,-563,279,-17,1000,93,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.Sum", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{108,-687,-881,188,738,-639,1000,-167,710,-718,219,-572,-1000,-776,37,433,920,-302,-167,-446,-537,386,-1000,224,-250,543,292,4,-929,89,-335,581,1000,102,-348,-596,-107,-46,305,111,263,-189,-302,-218,-398,656,-43,519,-788,198,-962,-447,351,1000,-674,-937,9,-111,-1000,-243,-503,91,-1000,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.Sum", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-579,-145,-732,449,592,63,767,-776,371,-833,503,927,-345,-938,-237,-758,-383,-20,510,292,927,130,-82,789,977,992,-314,-381,931,626,-759,-852,78,410,961,-814,-149,-601,881,56,148,-828,4,-519,147,452,-705,-891,664,-948,-278,420,-711,788,-720,489,-962,858,-151,-401,50,899,-62,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{504,-539,-728,394,936,-585,-62,719,1000,1000,-138,-1000,1000,398,893,-518,27,-839,-417,-371,-676,443,923,-577,682,317,1000,-347,-1000,1000,-386,297,-426,403,-749,702,-1000,-47,802,1000,537,579,112,1000,-625,524,1000,-723,-512,77,-670,-497,-441,1000,-192,-382,-667,109,-332,1000,19,297,-400,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.Sum", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,-359,-1000,988,494,-639,-400,-452,354,359,-727,-1000,-693,-3,187,1000,1000,-349,-1000,-937,-1000,386,-1000,-1000,-584,739,-1000,1000,-1000,-222,-335,581,832,-248,-348,-298,-107,612,441,-942,728,-189,-308,326,-706,656,848,626,-806,560,-915,-1000,700,961,-829,-937,172,-312,-1000,-4,495,-1000,400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.Sum", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-206,-232,108,259,10,1000,316,-349,-491,861,-225,498,257,-593,-122,-461,-824,-937,-1000,-987,719,-796,12,630,803,1000,-623,-211,556,690,-1000,-1000,-1000,1000,825,81,1000,60,1000,312,884,-923,-1000,-501,419,-1000,529,-770,853,-1000,933,323,-1000,-261,1000,1000,-1000,1000,-1000,1000,-156,1000,-1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.Sum", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{24,-409,-520,604,122,-168,1000,325,-547,962,367,369,762,437,103,224,-213,-958,-377,476,-1000,-51,237,-989,123,25,255,-381,-887,1000,-352,149,370,-170,57,-865,-143,1000,-530,-689,-554,514,530,965,-69,134,23,-383,11,11,-1000,164,24,-698,1000,19,-211,-1000,1000,-354,-795,68,597,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfLogs", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumLogImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-328,79,-952,-191,-209,1000,-376,-956,993,1000,-1000,1000,1000,463,423,-235,-982,776,-65,1000,589,-163,-1000,749,-429,1000,1000,334,-358,52,-292,802,711,863,760,-1000,1000,-1000,-550,-800,550,298,-1000,-1000,-234,889,641,242,-459,-1000,-330,1000,79,33,-885,1000,1000,895,1,-1000,-554,-1000,578,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumLogImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-950,-727,-214,-111,48,-647,157,-685,-1000,-1000,1000,-128,1000,-1000,-596,1000,-468,-1000,-574,1000,-1000,-1000,1000,771,-428,347,597,85,1000,445,-1000,-1000,1000,-1000,-1000,-89,-1000,330,514,-1000,-929,1000,836,-903,-1000,1000,-197,-1000,-1000,-1000,-673,431,1000,1000,-359,-384,474,-271,-1000,88,540,604,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfLogs", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumLogImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-257,607,0,-897,-421,461,-160,-505,-1000,-972,-598,-25,613,-429,1000,1000,-410,-1000,-237,1000,-597,-555,591,422,-537,37,876,760,1000,-96,-500,140,1000,-1000,-723,-180,-672,-633,234,-1000,-414,29,-497,-974,-1000,1000,628,-299,551,504,417,187,-221,311,-1000,-956,248,766,-1000,-332,-347,-977,-725,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfLogs", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumLogImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{37,-7,-1000,-287,-346,1000,-535,-266,-869,-65,51,1000,1000,907,93,1000,-1000,-1000,-1000,1000,212,-342,570,1000,-1000,1000,303,1000,972,1000,-159,54,1000,-1000,279,-1000,1000,-701,234,-1000,940,774,-1000,-1000,-1000,850,426,431,131,-896,602,162,1000,321,-926,1000,702,436,-1000,54,-621,-586,375,128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfLogs", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumLogImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-480,731,-1000,-996,342,41,81,-1000,-1000,-1000,-1000,-1000,1000,-1000,1000,1000,-634,-1000,-1000,1000,-1000,-1000,1000,300,1000,1000,-233,1000,1000,1000,-706,363,-1000,-1000,-1000,430,758,-1000,-1000,-571,-1000,1000,955,-1000,-1000,484,1000,-1000,666,1000,1000,317,-534,1000,687,1000,178,1000,-1000,-571,-1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumLogImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-1000,-121,-967,-660,79,41,235,-173,-534,-329,-238,-1000,20,-212,355,1000,64,-516,-1000,-322,-1000,-922,902,-659,771,895,-1000,992,797,1000,344,965,-776,-1000,-488,985,-217,-949,-665,-571,-396,945,-552,-242,-474,145,546,-878,-419,1000,814,-514,-534,363,176,-86,-64,1000,-154,115,-1000,177,-662,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{1000,-169,-190,314,1000,1000,25,-919,66,1000,-1000,-774,1000,1000,-1000,67,1000,528,702,-524,1000,165,702,-1000,-535,-619,-256,-152,883,736,-1000,-9,1000,-1000,-735,1000,937,-101,-1000,1000,-458,-537,-938,1000,989,352,-1000,774,-724,-1000,-1000,538,895,1000,-1000,-1000,-1000,-1000,-1000,1000,-1000,-905,8,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{-523,-349,296,-121,-166,1000,1000,332,-255,547,1000,-1000,624,353,-679,-840,-963,918,-1000,63,-72,-538,1000,-509,-1000,267,-747,-1000,815,1000,-906,-340,-1000,-996,-946,-254,138,175,-1000,308,611,1000,-105,-186,1000,-171,-1000,-943,-845,-1000,-399,245,866,1000,-806,431,728,-1000,-77,-1000,-1000,591,-136,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{-517,-1000,-646,-18,370,-576,768,723,-261,-220,-814,-399,-1000,-282,180,779,-1000,-57,-341,-243,-1000,-724,183,407,705,-485,-325,-633,-322,-8,-663,390,462,905,1000,157,184,605,513,-721,-1000,203,334,-895,60,558,315,355,-339,-915,87,-677,-607,286,833,606,823,-5,410,746,-530,362,319,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{284,-211,-1000,79,841,-1000,-741,-1000,-1000,963,335,126,263,-951,-1000,530,108,-241,256,1000,-496,944,602,-465,-329,-140,-343,-660,1000,272,284,-847,1000,1000,-339,369,-88,-1000,1000,-1000,759,1000,-1000,1000,441,329,-317,-202,-505,349,-751,760,-13,468,259,-818,-598,-1000,1000,-1000,-294,88,354,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{-930,-365,-427,-1000,-796,-313,836,798,-1000,152,1000,-1000,-1000,548,-33,-513,-1000,1000,-808,1000,-802,-1000,1000,1000,-1000,1000,-178,-366,514,1000,-286,-1000,878,1000,7,-585,135,343,447,-1000,336,-1000,1000,-444,901,1000,-388,258,172,521,-211,-659,501,-1000,138,687,689,161,-104,-795,502,271,-1000,-352}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{951,-287,250,688,479,1000,-422,-557,25,1000,-1000,-532,1000,353,509,-133,1000,331,687,-239,861,0,533,-792,-408,-731,42,-152,830,848,-1000,-340,736,-1000,-989,926,205,-506,-896,893,232,-168,-679,944,305,-412,-1000,191,-508,-892,-944,978,1000,89,-1000,-950,-342,-1000,-772,245,-688,-874,-701,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumOfLogs():double",
            new int[]{-1000,-714,622,-641,-399,1000,-422,1000,-841,251,-1000,-1000,484,14,-663,-1000,-1000,1000,-795,-239,176,-1000,1000,-517,-1000,-548,-938,-1000,-1000,1000,-656,-566,-1000,-1000,-434,-440,-120,235,-173,252,232,-795,71,-598,305,-256,-1000,-87,-508,-1000,-554,-34,1000,89,-988,280,684,-1000,-772,-806,-1000,1000,130,564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.StatisticalSummaryValues", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{-361,-653,-1000,199,11,1000,410,652,-545,-1000,-492,531,1,92,-190,50,-1000,555,299,-144,-1000,-1000,-297,417,-1000,-1000,144,585,-388,55,-1000,388,193,179,-1000,-746,559,-422,534,-571,708,841,136,118,394,-757,-587,-366,-67,-1000,-318,-845,-123,-6,-606,-1000,1000,-915,794,-197,329,271,-852,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{197,-723,-797,-1000,-41,352,27,523,-264,-1000,172,1000,-197,904,-1000,914,-1000,331,1000,-53,-1000,-1000,59,-111,-1000,-752,530,1000,-857,396,-1000,1000,540,98,-504,-904,-163,-1000,-515,-219,616,505,56,690,1000,-965,-1000,726,-642,-1000,-1000,-1,371,-154,100,-1000,1000,-454,842,-1000,-11,-497,-1000,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{-1000,985,-645,-84,-366,89,-1000,1000,1000,-226,-1000,509,267,109,-365,-1000,784,-343,1000,-966,848,-717,1000,878,190,-634,321,-894,621,133,-966,1000,785,-251,-1000,862,1000,-1000,-1000,-871,-378,1000,-37,-479,-635,-1000,-1000,650,-1000,-647,1000,452,465,1000,928,1000,-122,-1000,883,-462,-1000,1000,1000,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.StatisticalSummaryValues", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{788,-271,798,-471,352,1000,697,-444,-102,500,-917,486,101,-531,1000,960,-470,-38,-533,-727,-456,-28,-285,-1000,-633,-106,535,1000,-255,-257,406,1000,938,-491,401,-1000,-356,109,655,371,1000,-1000,-186,-549,543,111,1000,-977,342,-339,-883,-167,1000,-1000,-1000,-1000,241,-424,-482,-1000,635,-218,-707,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{-945,623,-645,1000,479,89,-489,1000,1000,-648,-939,594,-2,73,-365,-584,784,-347,407,-1000,78,-1000,994,1000,57,-754,521,-253,1000,-613,-966,-764,785,-277,-1000,570,-484,-774,-910,572,-217,637,15,303,-978,-1000,-1000,480,-943,-647,1000,677,465,1000,887,1000,394,-1000,-573,-490,-1000,1000,776,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{-275,-681,-698,-31,364,-1000,1000,1000,91,850,-1000,813,774,1000,118,404,-583,277,-92,-1000,-1000,-303,-102,1000,-186,-1000,4,1000,1000,-667,-1000,-304,376,426,459,-1000,144,-1000,214,-40,1000,-1000,527,-418,-276,-1000,974,-653,-1000,-1000,-618,142,1000,-681,-791,225,794,-1000,-1000,-1000,-21,400,-1000,325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSummary():org.apache.commons.math.stat.descriptive.StatisticalSummary",
            new int[]{1000,907,1000,-328,971,-195,772,-1000,686,876,-195,-312,-791,908,1000,961,845,-477,-253,-971,1000,1000,-33,-581,1000,1000,278,43,90,-285,1000,-179,-1000,-968,1000,-689,-814,1000,45,-1000,-566,-1000,-872,-1000,-1000,1000,1000,-668,377,640,747,578,56,-1000,-143,-548,-1000,-86,-1000,-1000,366,272,612,731}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{-321,-453,-1000,434,1000,-1000,417,469,-669,257,91,255,1000,222,-400,-154,-968,-437,-96,-582,-98,-203,-711,-644,-46,1000,743,-123,54,-217,613,-1000,-726,318,-1000,-453,332,-524,-1000,-413,894,-852,487,540,-94,600,-564,1000,-421,-106,889,-943,471,1000,763,131,98,148,-719,202,-321,1000,-269,-328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{-611,731,-186,804,169,993,554,266,792,371,594,388,-487,-476,788,30,678,262,-690,-503,-771,-96,-326,939,-53,-608,-302,750,218,-482,362,560,910,700,-516,899,534,79,855,-639,-699,-581,-910,-791,-109,273,-192,743,812,-815,-44,475,62,-683,121,177,-505,-170,-540,-785,-254,583,553,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{-242,134,-396,478,-118,925,-809,-1000,1000,-179,687,1000,-1000,764,1000,-715,833,-68,1000,-1000,-1000,-1000,-1000,-1000,1000,-99,865,-942,218,-185,-126,557,1000,751,1000,1000,-563,834,1000,-512,433,-57,-1000,-1000,-1000,-118,502,-1000,354,-1000,1000,89,-420,1000,-1000,692,769,1000,-244,-285,-410,580,646,760}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Double:NDE2MTYuMA==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{-616,268,-222,679,-439,1000,204,395,408,123,1000,115,-944,12,846,83,-548,802,79,-503,-1000,-1000,-168,939,-754,-651,121,779,218,20,-84,774,1000,-970,132,663,-857,642,202,-939,-1000,-669,-989,-1000,-736,808,198,-320,-218,-236,629,1000,45,-678,301,868,409,-881,-707,-1000,215,1000,305,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{75,-535,-1000,763,152,-519,1000,314,-696,222,-456,1000,1000,640,1000,77,-385,-389,-231,271,-1000,1000,-482,-1000,-1000,1000,-15,250,-541,-130,-610,-147,-580,-282,394,-900,710,-1000,-1000,178,-170,-1000,1000,-1000,403,610,257,471,-3,-446,948,-86,262,-463,1000,-586,625,-1000,-938,-386,188,-309,-606,-988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{-1000,600,349,314,861,993,-2,-1000,1000,-1000,1000,-105,1000,-409,-426,-724,-1000,-682,-690,-1000,641,-760,-1000,122,1000,1000,1000,-4,218,-305,-449,-480,351,-135,-1000,807,-1000,546,72,-130,-699,-121,-281,1000,-1000,574,-1000,-202,-276,327,1000,-1000,-843,1000,-781,563,-505,1000,-312,-785,-1000,-997,-484,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Double:NjkyMi4yNDAwMDAwMDAwMDE=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsq():double",
            new int[]{148,112,-176,444,1000,-832,754,-214,273,-492,-678,-206,302,641,-432,868,-1000,232,184,82,-727,-821,112,855,917,798,1000,437,827,295,-1000,-376,-140,174,1000,-534,1000,-357,-558,12,1000,306,-75,522,-1,854,331,-291,-982,-228,889,-4,-602,1000,1000,290,129,1000,-520,-1000,-975,-824,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfSquares", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{169,-137,148,119,400,85,-1000,358,941,-296,493,-195,696,-50,978,-1000,-184,81,239,-307,-107,-422,416,355,-558,51,-255,80,796,200,786,260,432,1000,-400,522,152,918,-232,-265,179,358,312,591,-695,284,-110,-123,-263,786,223,-170,-349,-543,-400,553,239,1000,421,-400,710,357,-144,617}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,573,68,-286,-862,603,-1000,-301,-14,217,-1000,555,881,-804,-152,-948,618,-314,741,-24,-1000,1000,-672,-481,668,867,-45,-598,-78,161,-733,530,-1000,1000,174,-620,-1000,523,209,650,-313,-1000,-43,-324,-1000,-617,-1000,-630,1,257,-778,1000,616,514,-1000,-413,-783,-1000,-392,1000,-1000,-576,-362,661}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfSquares", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{103,969,1000,-797,989,-548,1000,1000,-1000,-1000,1000,-1000,-1000,1000,1000,572,1000,-323,1000,1000,446,-1000,1000,19,-1000,-1000,822,1000,-1000,-1000,-550,-142,1000,536,221,1000,193,-1000,747,-1000,1000,1000,867,1000,1000,-584,1000,-171,-1000,-1000,1000,-1000,-842,-1000,1000,1000,-600,712,1000,-1000,1000,1000,-878,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfSquares", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{221,569,-997,1000,-506,765,-874,-1000,1000,713,-1000,145,1000,-993,-935,-388,-169,81,375,-11,433,1000,-1000,1000,945,1000,-668,-1000,237,480,660,1,-1000,1000,102,-1000,593,-628,637,822,-1000,-1000,422,-888,-1000,160,242,-697,525,416,-1000,802,44,438,-452,-363,-395,-117,-191,1000,230,-1000,656,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfSquares", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{103,-409,1000,-797,-139,-548,1000,1000,-177,-1000,1000,-903,533,-435,1000,83,694,-514,448,501,446,-1000,833,19,-1000,389,-392,1000,-871,-1000,80,-91,752,536,409,986,193,1000,1000,-866,6,1000,867,1000,864,-584,728,-34,-1000,-998,1000,-1000,-880,-1000,782,1000,-600,-467,1000,706,1000,479,-162,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfSquares", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,-529,344,177,664,1000,1000,779,-567,-1000,1000,-505,-1000,126,1000,-1000,241,908,-134,678,654,-1000,-559,1000,-1000,-1000,-623,554,159,-1000,1000,-144,1000,80,-99,1000,-27,-811,205,180,-37,1000,1000,1000,-232,294,-632,221,-887,-1000,1000,-367,-609,-218,255,1000,417,-262,1000,-443,1000,592,-138,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.summary.SumOfSquares", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getSumsqImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-396,758,-253,-21,851,354,-1000,-987,333,602,-1000,-1000,282,-145,-1000,-1000,-326,468,1000,-374,-1000,1000,-1000,-205,1000,38,-623,-1000,-566,1000,101,-215,-987,218,-1000,1000,1000,-1000,367,-508,-495,-1000,61,-1000,-809,1000,-464,492,-1000,210,-200,1000,1000,487,-1000,-1000,763,-696,-1000,-333,-223,-586,-613,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVariance():double",
            new int[]{-941,-757,-684,-102,-1000,828,-1000,261,763,533,1000,-283,-578,877,-831,110,-211,887,960,831,1000,-987,-240,932,-116,-1000,-772,1000,1000,446,965,395,-1000,1000,-677,-232,-1000,1000,-864,604,11,988,1000,917,-1000,-736,-168,1000,1000,75,-730,322,-617,-961,1000,763,-1000,1000,373,1000,1000,1000,1000,-945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVariance():double",
            new int[]{-1000,207,1000,659,-238,-794,988,-673,-1000,-40,997,-1000,553,-792,-1000,471,600,456,273,-272,-753,184,1000,-442,371,1000,-127,1000,-1000,-739,481,-554,-990,-1000,-302,-1000,-1000,340,593,59,701,219,-412,-1000,419,715,643,108,737,-419,1000,-1000,-505,655,1000,603,-1000,-276,812,-920,1000,945,1000,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVariance():double",
            new int[]{-102,-1000,-326,269,-369,-1000,663,-1000,335,271,904,-641,352,1000,-113,665,-600,-404,-1000,983,493,-945,-873,-736,1000,457,-1000,544,318,241,-143,-588,578,-1000,-1000,-572,-353,417,-664,158,-754,-393,22,-142,-1000,-389,733,894,34,-1000,1000,-542,818,108,-55,1000,468,1000,1000,12,119,-641,623,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVariance():double",
            new int[]{-1000,1000,134,464,1000,1000,-495,261,-239,354,195,726,955,448,812,1000,-47,-91,236,476,455,-948,506,-1000,-325,-468,-447,655,70,-855,730,87,423,-359,-1000,-783,107,-1000,1000,1000,-818,-622,-1000,425,1000,-998,-743,963,1000,1000,183,-1000,448,105,230,-199,52,470,220,-385,79,-407,-1000,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVariance():double",
            new int[]{-1000,229,848,-296,-1000,818,-844,-157,-447,-129,740,-695,972,320,-16,356,-926,133,-272,1000,727,-385,-842,154,-960,-170,-723,743,-69,342,957,218,368,527,-1000,-2,42,-12,-530,656,-585,68,-589,99,-582,-378,160,1000,156,-102,219,-788,3,861,-1,466,67,326,780,-400,746,-668,293,-983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVariance():double",
            new int[]{-228,67,-1000,149,-98,70,-347,-250,-513,831,796,-142,976,1000,533,1000,-597,-524,-597,1000,503,-973,-1000,-751,436,254,-688,270,1000,37,-300,-137,1000,-941,-1000,-656,-747,-605,-185,685,-1000,-420,-539,529,35,-478,87,-153,73,443,-91,-1000,911,-80,-282,1000,911,-39,756,-611,-292,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,423,1000,-206,800,910,-860,501,257,-534,-1000,327,-243,484,671,721,1000,994,776,685,-517,-437,-89,533,291,1000,497,158,942,-149,-6,-1000,961,-523,565,658,-690,-81,-210,-735,1000,-162,-962,1000,168,-1000,95,1000,-1000,-791,-455,-1000,-1000,-612,-823,958,-578,400,-15,-279,364,806,-384,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{1000,789,-1000,53,-118,-1000,841,1000,-505,-1000,-640,1000,-303,-999,844,288,1000,310,700,-272,712,-1000,-1000,1000,-1000,1000,865,-1000,1000,811,-918,-1000,366,37,1000,844,-1000,-416,1000,-524,-1000,-162,971,1000,1000,1000,807,-318,775,-1000,-1000,-970,-1000,475,-751,1000,-1000,-419,-64,1000,1000,-16,-1000,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-120,-175,44,-557,339,391,-167,1000,-429,-1000,-403,20,397,-189,1000,-1000,1000,710,1000,-12,16,-1000,-1000,1000,1000,1000,68,-1000,917,504,-616,1000,1000,-420,1000,726,-1000,-1000,1000,-247,-1000,-976,464,1000,1000,-755,1000,997,-1000,-1000,-1000,-1000,-1000,-400,180,399,-1000,819,942,251,902,-1000,-738,437}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{-275,1000,-801,-766,-309,-1000,-1000,-990,-630,-1000,-825,-699,60,-338,1000,-610,1000,1000,1000,494,129,-1000,-1000,1000,1000,1000,791,-929,1000,585,-318,836,1000,-464,1000,382,-1000,-699,1000,-704,-922,-725,633,-1000,-1000,-746,1000,673,-1000,-1000,-1000,189,-1000,697,1000,1000,-1000,651,-43,901,880,-914,-722,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{319,397,908,53,231,20,-20,300,454,1000,1000,-1000,-285,994,-65,288,-146,937,178,529,-735,1000,1000,-1000,-1000,-1000,-89,452,-54,-829,227,322,667,712,-995,-1000,290,-157,-1000,-320,756,1000,93,-780,-213,-550,-441,901,183,601,1000,-222,266,-241,-92,-56,-134,949,-157,-1000,-939,467,-390,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.stat.descriptive.moment.Variance", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{51,-11,908,-611,-889,228,483,825,-93,179,1000,400,152,552,-686,1000,953,933,-636,-493,-304,530,83,-134,-1000,-863,-704,-64,-91,232,-1000,581,-313,96,-251,-157,-170,-82,-1000,-599,68,-9,-103,-822,596,556,-590,-640,236,447,385,182,886,-106,-913,229,275,-451,-1000,-109,-264,-494,-1000,282}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "getVarianceImpl():org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic",
            new int[]{246,-939,400,-66,-921,-1000,-1000,1000,-1000,1000,-1000,-1000,-1000,-327,1000,1000,689,1000,954,1000,486,932,400,-1000,-124,13,1000,1000,961,-1000,867,-1000,1000,596,-1000,-751,1000,993,-1000,201,39,1000,-1000,424,-1000,-400,-1000,1000,-1000,-533,-192,-1000,-1000,1000,-988,1000,-250,1000,-938,-795,484,-1000,851,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-909,933,-524,718,-1000,-1000,-1000,652,-709,1000,-809,-386,-300,1000,-1000,-1000,645,-312,951,-511,-460,1000,1000,-938,-49,1000,1000,-504,-897,501,328,669,-546,129,-676,1000,1000,477,1000,-1000,-545,-1000,-1000,-73,-585,498,-653,-58,1000,-447,-483,980,-1000,-511,1000,618,1000,816,481,852,-478,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{417,-495,1000,909,-521,-54,-833,118,1000,1000,-686,-1000,731,-266,-145,38,799,618,380,196,-1000,858,624,-1000,-651,-1000,-814,199,610,138,1000,995,-231,-197,1000,1000,412,-292,-1000,597,384,-621,-190,-534,223,74,-303,268,-995,-273,456,-1000,1000,1000,461,60,810,313,69,1000,1000,400,509,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{418,-202,9,84,-1000,-568,485,129,665,521,-67,386,-1000,298,-576,138,603,646,-166,-417,-25,-400,477,-584,726,-24,789,568,1,749,-9,-516,-937,427,-129,753,752,1000,-32,-225,-730,-707,-125,-355,400,103,426,-120,190,-388,-40,289,-747,227,25,266,-400,-633,382,784,-605,-984,-683,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,1000,-456,-681,-181,-929,-650,392,-1000,-352,598,-316,594,-320,-1000,-301,564,-1000,1000,-304,-293,914,1000,-549,35,818,1000,-658,-1000,757,370,117,108,-507,-899,1000,286,1000,1000,-771,-1000,-609,145,-278,-465,-290,680,742,1000,262,188,658,-1000,-749,617,316,1000,821,-259,319,-148,-919,-370,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-93,397,121,1000,-316,352,-650,-889,1,-139,162,-882,615,737,-784,293,636,170,-526,677,533,797,652,-573,123,-1000,-735,848,1000,-669,460,992,-365,684,3,465,286,723,357,-442,-2,605,816,402,155,-683,-1000,-685,-1000,338,-939,-660,804,255,57,-104,739,611,994,-502,-628,697,534,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{337,-807,-789,1000,-193,484,-639,974,170,-725,-1000,-489,-78,-663,-1000,712,570,531,-526,-549,-734,824,543,-984,-1000,-1000,-484,-875,945,447,781,711,-325,422,1000,199,-989,1000,-1000,1000,166,-203,-1000,420,236,716,-1000,-807,31,-35,-700,-606,903,-139,177,704,682,317,1000,88,-642,-703,534,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,-393,871,-258,9,-384,589,124,963,-641,1000,-9,-980,180,-1000,190,717,1000,-596,16,548,-1000,-1000,-507,602,-922,-554,975,-19,595,-52,-1000,-32,-249,-337,-699,1000,413,-711,55,-725,-448,-1000,-369,1000,-833,1000,-267,-570,551,68,842,-262,1000,-392,420,-1000,-786,25,358,-651,-1000,403,451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{649,-43,-1000,-836,1000,1000,1000,440,-291,1000,702,871,683,128,354,-768,-151,1000,291,-1000,130,1000,-1000,-879,-81,-624,-1000,1000,-408,-376,-1000,-55,684,276,-198,-245,119,62,-179,238,251,-1000,250,-296,-843,-123,-10,491,1000,-282,796,926,1000,370,-1000,610,212,-108,-943,196,958,705,1000,-25}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-944,-427,160,-416,957,-726,-1000,-247,-903,19,744,-994,-702,-540,-451,308,1000,149,826,671,194,399,-388,581,775,-92,224,-1000,-1000,558,1000,-132,-264,409,65,-653,-1000,380,-1000,-485,189,-724,751,72,63,-876,-667,-772,464,182,995,191,-207,687,-78,869,8,-749,-468,-634,1000,341,-611,-303}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,-193,-1000,228,732,1000,1000,261,16,785,534,135,1000,-15,-696,-1000,702,1000,194,-1000,1000,824,-922,-1000,1000,-1000,-509,589,-1000,-260,-590,641,346,-470,123,-916,1000,-697,141,595,685,-789,-1000,-811,-711,-221,675,641,3,1000,346,-194,1000,-1000,-1000,-339,690,-543,-629,-183,497,714,1000,488}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,7,1000,-287,-496,1000,400,-242,-832,-176,1000,-635,-758,95,22,-63,529,-847,-201,-563,598,-440,864,410,322,616,-337,-975,70,-251,452,-448,-786,723,-1000,-446,25,-711,-1000,-663,1000,475,7,-3,-530,840,-1000,-548,-790,156,494,-792,1000,703,-1000,-654,494,-1000,-71,182,290,557,622,-815}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-276,-395,1000,-11,1000,-1000,378,751,-964,-485,386,-1000,568,-489,-1000,1000,-314,474,604,1000,684,-1000,-357,-99,1000,-603,-370,-651,973,1000,1000,1000,-1000,-1000,-121,-317,-292,314,-567,84,-1000,497,-68,-564,-305,124,-887,-1000,587,-373,-1000,-1000,-645,-652,-435,262,504,-144,253,-1000,628,-105,-685,391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-15,-447,708,-317,549,11,-309,1000,-249,1000,560,1000,-86,-330,-240,973,-400,202,-434,608,212,-889,516,125,380,148,-1000,1000,458,-629,-85,-24,-234,-320,304,-258,-665,62,442,-514,172,-611,146,-751,423,-501,-378,61,1000,-594,-620,247,-609,-634,322,1000,689,1000,-614,-138,796,705,387,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-94,711,467,484,87,-386,-799,78,507,-972,-246,817,503,-35,-201,-600,4,-21,-546,-532,435,-647,-87,-501,926,-913,-310,-332,985,-301,-310,634,483,-527,426,-540,793,140,-938,923,-777,-812,720,286,515,-994,-514,322,111,-107,-106,812,117,575,-682,-935,467,367,-325,-916,666,-681,-903,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-888,-1000,244,-1000,-919,-1000,701,-81,-1000,1000,932,-1000,-1000,223,-356,458,-36,-251,1000,1000,-1000,719,-1000,311,996,-671,503,-1000,-173,640,1000,710,-686,647,-1000,-311,-675,336,-1000,-191,-509,-1000,643,452,-1000,-1000,-1000,-1000,1000,-516,1000,1000,-95,289,-384,-189,1000,-1000,-1000,-866,1000,529,-71,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-556,659,-1000,-206,1000,39,202,-369,967,-16,195,-328,-1000,-210,-373,-1000,-235,905,791,-4,486,1000,834,-756,-922,1000,-256,-448,-190,-772,-473,-374,-698,547,-1000,-1000,1000,-1000,-1000,27,-366,1000,1000,-407,1000,1000,-1000,-547,638,-1000,929,1000,1000,603,1000,1000,-121,147,894,200,-538,-1000,-1000,98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{499,-741,757,994,744,-272,-181,-674,522,260,507,-175,-716,-18,952,-581,-684,-382,-111,296,912,-240,-228,684,145,-308,558,-782,503,733,-53,-315,-611,981,-793,687,-168,-625,-488,-482,597,92,152,-526,357,-606,956,674,-444,280,757,514,-811,-430,-120,879,830,-671,-740,-619,936,3,13,-953}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-727,101,-990,627,581,20,-121,-878,824,-976,615,1000,1000,982,-397,900,-1000,-374,17,1000,819,-386,-1000,1000,-1000,-170,919,17,850,-1000,-758,491,531,-123,267,1000,-1000,-461,830,812,1000,-656,455,1000,1000,-1000,-1000,780,304,-710,-110,-617,-1000,-733,-153,-151,-1000,833,-880,467,-1000,1000,918,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,843,-524,229,-698,-1000,-615,1000,35,1000,326,-789,-856,-459,664,-1000,-1000,1000,535,1000,-350,849,1000,277,1000,1000,1000,1000,-677,237,356,-675,546,261,-33,-1000,1000,932,1000,-489,-1000,-159,-1000,-1000,-1000,1000,896,-36,427,-1000,1000,-456,1000,300,-411,1000,1000,177,1000,1000,-808,-364,-307,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{663,-758,-87,309,681,-1000,1000,795,-391,-218,-1000,977,-333,-541,1000,-1000,-855,-1000,1000,-738,371,-1000,-200,326,-1000,-225,512,-739,-336,1000,381,-787,-139,-263,1000,-5,-1000,1000,41,-232,-293,1000,789,892,1000,539,699,431,-292,1000,552,1000,243,212,-82,744,-820,-1000,-930,-456,804,-223,-330,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,600,39,444,530,-318,1000,503,-46,-292,-568,345,-862,-584,615,-1000,-1000,-422,1000,758,398,1000,138,-179,-488,-1000,110,-926,267,-443,-570,12,718,978,359,1000,1000,-729,430,-366,-746,749,989,191,1000,578,464,-233,-473,1000,-169,30,-576,-247,435,-1000,-344,-886,-98,-390,342,-174,598,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{623,500,494,374,-329,265,-230,218,-869,-85,759,-737,-241,226,409,-854,-936,143,447,-102,-739,-621,440,-26,884,-637,-56,401,-199,-312,-492,533,111,-812,634,-337,-284,776,-423,549,-760,-743,-669,-275,425,-529,84,-532,-900,346,978,-803,411,671,-915,192,130,762,-802,598,-485,572,221,-138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,231,-362,-127,-1000,97,329,-78,-839,-159,308,-164,507,219,-848,310,-1000,-514,160,-304,484,1000,-947,-1000,-542,-472,-605,-814,621,-575,-153,-320,-1000,-22,-696,-1000,329,-566,1000,282,-273,439,-239,752,127,-1000,-512,376,986,1000,605,393,-88,336,-663,-1000,891,7,-376,1000,-83,-201,49,521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{868,-970,29,76,-992,299,-623,-426,-715,-584,123,-827,271,-579,267,-353,273,643,-14,-116,470,136,503,457,-710,-1000,-96,-153,-1000,552,227,-539,356,-260,-499,650,453,-32,98,-246,152,789,301,29,-205,-229,-937,-233,720,-983,712,-566,491,188,777,717,-447,-1000,85,608,-610,1000,259,-932}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-227,-566,-216,-21,-739,724,-1000,400,-586,801,405,-561,706,-697,377,-675,-160,228,988,197,-744,-703,735,-408,158,-1000,481,663,-221,988,-495,-397,593,-358,302,1000,483,-359,-247,-1000,5,70,74,-517,-175,264,854,-95,298,-1000,548,-493,297,-29,304,34,-855,-1000,-368,-373,-51,625,1000,484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,-263,-397,1000,-792,-688,1000,-1000,-994,-1000,947,-495,1000,-639,-290,347,-1000,-1000,-3,1000,1000,-860,774,1000,1000,-1000,342,-834,-1000,-806,539,-1000,80,-30,-694,550,-994,1000,462,926,-1000,693,1000,242,1000,-1000,1000,526,1000,-1000,-1000,-475,1000,1000,-477,413,420,762,1000,-354,-517,-392,9,-692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,-1000,248,-661,-1000,79,1000,343,-1000,-323,-147,-630,-164,-715,808,-637,217,581,1000,-1000,739,-860,-903,-407,1000,-573,342,-761,-1000,664,-586,359,408,-1000,-599,342,1000,-1000,888,926,-80,-627,1000,1000,588,235,-400,-306,1000,-931,1000,-549,1000,210,1000,-997,-302,-833,-932,1000,-550,1000,341,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{183,-981,470,493,-716,-269,-9,182,7,-843,165,948,-826,-713,-889,443,-528,260,-785,-629,675,-679,298,-245,581,-268,77,-847,-833,-127,-355,-193,-933,-8,-191,741,571,-557,768,943,51,621,841,925,984,-759,-265,217,513,171,198,-378,-79,-447,790,-561,286,-357,-2,284,-893,461,-55,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-416,949,-627,-412,-211,-20,-880,-242,138,784,61,-405,-359,127,-391,-151,-737,-77,750,309,-490,208,-617,-376,437,1000,-1000,-241,1000,-409,34,-821,310,1000,-449,382,-102,-183,-416,-315,1000,-448,347,301,31,232,-326,-590,-515,-210,-1000,-1000,-132,310,1000,-181,641,913,205,707,-645,-49,335,-417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,976,224,903,-146,177,-268,970,-132,-321,-1000,293,-400,-483,-1000,-1000,-70,-251,277,-739,531,-811,-554,1000,1000,1000,-1000,145,403,-708,1000,400,-385,649,-565,247,-356,-190,-117,228,740,-1000,1000,186,-1000,-303,-213,-403,-462,-912,-352,276,-154,2,131,-12,-377,-518,-60,50,-1000,-1000,389,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,1000,224,659,1000,-1000,-520,818,561,686,904,-990,688,669,-168,-899,-50,-1000,-386,-757,-1000,-394,-185,664,-1000,-1000,1000,1000,276,333,248,1000,-1000,-1000,958,-533,1000,-1000,671,-375,-874,-832,-581,952,-709,1000,799,-403,1000,-697,-46,-50,-1000,-1000,-1000,259,-837,139,-1000,275,331,1000,-406,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{361,553,-454,-66,402,-1000,590,-1000,-155,-1000,-848,-613,-303,-331,-1000,1000,1000,105,34,1000,178,1000,1000,955,1000,751,186,156,-849,-138,-17,81,403,-550,341,-659,859,1000,-1000,416,-219,-1000,860,400,378,-813,86,199,-790,960,-840,-898,-110,664,748,-384,730,-1000,563,192,-1000,-840,1000,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{930,797,-413,-1000,-652,414,-1000,723,1000,291,-579,-1000,-15,1000,-1000,-1000,-1000,-893,552,-937,-1000,1000,-1000,-1000,164,-297,499,-473,1000,116,31,667,845,829,1000,148,213,-421,1000,-1000,625,866,149,770,-240,-440,-777,453,-1000,291,-690,-353,-882,139,1000,1000,1000,-58,-43,883,-525,283,-795,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-175,13,676,89,-400,1000,106,-255,-410,-348,895,-679,-40,405,1000,-394,-891,9,801,711,-778,1000,1000,907,877,-307,-652,-134,-447,-96,-255,-125,-404,800,446,-869,15,225,456,-409,14,-672,-390,715,-1000,-1000,413,24,-63,-214,-1000,946,-494,475,-727,719,1000,1000,749,-110,368,140,-174,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,-759,636,264,-161,-1000,-993,1000,853,-214,-226,-292,1000,-1000,-934,1000,-1000,-3,-398,-140,729,-773,-42,426,524,-664,-1000,584,-211,313,-330,607,-114,118,213,-219,102,-135,529,-240,177,-91,218,1000,-767,718,1000,432,-1000,694,550,306,-1000,806,-361,-106,27,-274,109,-1000,-313,-579,-1000,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-2,-963,197,-724,-161,441,384,231,228,-781,-238,-860,293,400,1000,-740,-647,-624,-26,-170,-462,583,-45,564,301,-307,-652,-402,-343,99,-627,138,322,12,-13,-729,-124,-355,-413,-883,-255,312,58,1000,1000,-832,-7,147,-140,-541,-904,-1000,-401,736,-1000,1000,1000,-81,-886,-341,71,185,340,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,13,-283,510,-418,202,-1000,587,-1000,2,845,-679,-875,249,971,1000,-1000,1000,731,426,-354,169,609,-119,1000,-526,-1000,1000,-838,-1000,-969,407,-1000,667,458,-671,-458,-349,632,291,-263,-102,-390,221,-1000,-360,1000,685,278,521,-1000,-497,-681,-940,-597,-36,1000,1000,569,-269,-1000,780,-505,956}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,611,-372,43,-249,-275,-1000,189,-192,-553,1000,758,1000,307,126,901,-558,978,855,1000,543,978,525,880,1000,-521,-1000,-255,50,-475,20,378,-592,603,1000,1000,111,265,600,1000,90,123,505,-673,-23,480,1000,677,-327,1000,-231,-677,238,1000,-1000,-581,-875,954,-273,-274,-875,-812,-404,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{253,-769,-276,403,14,-1000,-857,328,-1000,191,708,130,-892,539,534,652,-1000,422,-485,-1000,238,890,-194,830,810,-948,-1000,-493,-619,-152,649,-854,-491,-44,175,181,87,-828,-444,261,138,238,581,-882,610,77,1000,-326,-143,1000,-443,-946,1000,1000,351,-18,1000,235,-127,-206,-670,-37,890,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{609,457,-1000,334,1000,349,1000,-465,-399,101,779,535,470,539,781,501,670,-694,924,1000,-135,-1000,-716,647,531,-356,1000,724,-179,-377,-300,-1000,-781,1000,-399,-914,-1000,-527,66,-688,1000,-9,-166,534,1000,873,-919,828,-400,-1000,-267,-516,779,-245,138,922,-253,754,1000,-891,-253,800,-488,757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{130,628,854,209,251,607,-497,-743,-603,-482,-41,-351,-1000,-1000,-1000,-1000,-349,567,-678,886,131,359,925,184,1000,-1000,74,1000,761,667,-633,426,769,936,387,-293,389,1000,-345,337,450,528,-1000,7,-843,-700,807,382,-892,725,966,764,-137,748,-267,-704,-326,-393,-615,-159,-955,1000,-605,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,85,-1000,994,-716,-99,1000,1000,-1000,-1000,-1000,-1000,533,1000,-155,939,-1000,297,1000,-649,-568,-1000,-567,-1000,-8,-1000,-1000,-473,-1000,1000,-266,1000,676,113,-1000,-615,251,587,1000,1000,1000,1000,-1000,1000,1000,-1000,-1000,-668,1000,921,-635,-231,1000,1000,-1000,-780,-30,1000,-1000,-30,857,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{439,-569,-27,-54,-32,-544,62,-405,398,-283,451,169,1000,924,774,863,126,883,611,126,229,-109,-1000,345,-557,1000,128,579,-207,740,299,-272,-576,115,523,303,23,66,1000,-814,-129,-536,-523,334,303,1000,-808,98,-229,-686,-382,-1000,597,-837,146,-231,-1000,1000,-144,-154,-147,236,679,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{793,-633,-1000,168,942,-1000,1000,402,-1000,-1000,1000,1000,1000,1000,-351,1000,1000,-1000,1000,-215,-13,-1000,-1000,418,226,1000,-168,-930,-621,807,-31,-334,-1000,1000,842,-287,-1000,-1000,284,529,-466,-1000,-1000,755,794,1000,-1000,122,-931,-552,-922,-1000,1000,-324,1000,995,-764,107,1000,-1000,-841,310,1000,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{991,-67,-1000,214,-201,-680,1000,453,613,278,885,-84,1000,1000,314,1000,992,-1000,1000,-654,-160,-1000,-1000,-476,133,489,-668,-1000,-1000,1000,-226,1000,-1000,599,344,-482,-832,-1000,895,-429,400,-892,-1000,1000,-1000,1000,-1000,-1000,-367,944,-1000,-1000,1000,-73,982,-846,-663,1000,-400,-582,149,325,235,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-992,447,732,159,1000,560,-765,263,671,-634,-1000,-944,-1000,1000,1000,921,-86,-299,986,-1000,1000,-559,-858,358,-826,-368,1000,1000,1000,-1000,-152,488,-685,-856,676,-932,-893,-146,-104,171,35,1000,-914,660,838,-567,-269,-1000,-920,-870,-66,-626,-459,-1000,296,1000,373,-102,595,1000,658,155,-116,-565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-309,289,744,-366,131,-427,-18,517,745,0,-103,-182,959,1000,-487,336,864,474,-633,144,46,-252,-1000,1000,-1000,-580,657,1000,-368,-788,-121,-597,-225,-515,121,1000,110,-1000,-887,1000,1000,-864,-43,1000,-1000,-732,-1000,41,717,-856,-306,398,177,765,43,-501,195,743,551,-575,959,807,-1000,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{344,-789,1000,568,14,-1000,-680,-267,-72,-882,-400,301,-627,571,-1000,-607,1000,1000,-435,-215,-1000,-805,-631,1000,-820,-829,-816,-598,1000,533,342,239,938,-1000,-456,-352,-920,-1000,1000,1000,-464,-1000,274,1000,-982,-1000,-716,-279,303,-1000,1000,-523,-972,-864,-78,-881,366,-1000,-474,-614,552,1000,446,-749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,55,-1000,-966,279,1000,-1000,1000,551,1000,-176,1000,-1000,1000,-995,1000,-1000,-600,-1000,220,-1000,-937,-523,-844,-1000,-1000,400,-1000,969,-1000,465,1000,443,1000,-1000,-1000,-1000,64,-682,148,-669,-1000,814,-1000,1000,-844,1000,733,655,1000,-1000,-1000,1000,905,-1000,-116,1000,825,-354,382,-1000,658,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{938,-665,352,-366,-568,-1000,975,-695,745,0,367,-198,1000,-309,-316,-588,1000,246,-1000,1000,-873,-252,239,471,-105,372,-736,412,-555,830,-241,348,-225,202,503,1000,-128,-1000,-173,-208,410,-435,-46,744,-1000,-186,-745,695,1000,-1000,907,819,177,-520,735,-1000,-1000,-234,-487,-1000,1000,-131,-14,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{304,-79,-1000,-928,494,-899,-162,61,217,598,927,597,-254,1000,1000,-939,705,-792,-352,431,-360,831,-322,-943,-505,-1,-988,549,856,116,-217,701,-445,1000,44,1000,-927,-206,-383,244,-1000,123,166,167,-794,435,102,264,-19,698,495,-762,-943,-570,642,450,-15,354,-1000,180,1000,530,-1000,-514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("java.lang.String:U3VtbWFyeVN0YXRpc3RpY3M6Cm46IDIKbWluOiAtOS4yMjMzNzIwMzY4NTQ3NzZFMTgKbWF4OiAtMi4xNDc0ODM2NDhFOQptZWFuOiAtNC42MTE2ODYwMTk1MDExMjk3RTE4Cmdlb21ldHJpYyBtZWFuOiBOYU4KdmFyaWFuY2U6IDQuMjUzNTI5NTg0NTMxMDI2N0UzNwpzdW0gb2Ygc3F1YXJlczogOC41MDcwNTkxNzMwMjM0NjJFMzcKc3RhbmRhcmQgZGV2aWF0aW9uOiA2LjUyMTkwODkxMTE0Nzg5MDdFMTgKc3VtIG9mIGxvZ3M6IE5hTgo=", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{1000,-45,-1000,594,-29,870,928,1000,944,1000,-641,-666,1000,-964,372,1000,1000,-661,521,1000,897,-1000,1000,-1000,-162,607,-1000,-1000,-381,-954,1000,-311,-478,-1000,1000,75,-1000,1000,1000,664,-1000,1000,964,-1000,771,1000,-808,1000,976,1000,-1000,503,1000,-1000,1000,1000,1000,-1000,1000,934,-1000,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{405,-18,389,-36,-32,547,-378,-157,-677,276,539,-980,-67,-290,26,634,834,-615,508,-12,562,-588,-254,-746,-427,602,-298,-421,-449,-986,-128,852,594,-742,-564,-526,-907,-858,-117,-582,329,-691,-677,-439,308,425,-387,844,-856,-855,-353,147,-576,616,660,-60,-563,-415,256,-255,873,327,-982,-742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{-413,955,419,-1000,-796,269,-1000,874,-139,-531,-574,212,781,-840,-105,-196,750,-829,1000,-52,-793,-1000,1000,788,-838,442,423,-820,-902,1000,103,912,862,-580,-75,-1000,58,69,-118,-422,-656,-965,-1000,-557,-150,896,-129,462,24,-159,-1000,224,-836,-1000,-150,141,1000,-351,-175,498,-162,132,246,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{-1000,-653,1000,649,-671,-959,163,421,500,323,-707,-168,-581,1000,-1000,989,909,15,-1000,-42,-1000,666,807,1000,-175,-1000,511,-181,1000,917,1000,-1000,-1000,-188,954,-431,848,-58,-696,-756,417,239,-1000,-347,-487,-1000,1000,451,353,-742,-239,-429,827,224,-525,769,-1000,-491,1000,339,-865,-732,-335,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{18,-438,-871,-16,-48,1000,-474,-297,285,-981,281,-667,813,795,1000,62,-714,-298,-158,-1000,-750,251,-746,544,800,-323,-867,-275,-818,89,-1000,123,837,-780,-230,153,749,-895,-1000,-25,1000,1000,-133,557,448,297,-292,560,639,1000,734,402,-218,929,850,-46,509,-890,-313,-400,1000,400,-729,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("java.lang.String:U3VtbWFyeVN0YXRpc3RpY3M6Cm46IDEKbWluOiA5LjIyMzM3MjAzNjg1NDc3NkUxOAptYXg6IDkuMjIzMzcyMDM2ODU0Nzc2RTE4Cm1lYW46IDkuMjIzMzcyMDM2ODU0Nzc2RTE4Cmdlb21ldHJpYyBtZWFuOiA5LjIyMzM3MjAzNjg1NDc0NTFFMTgKdmFyaWFuY2U6IDAuMApzdW0gb2Ygc3F1YXJlczogOC41MDcwNTkxNzMwMjM0NjJFMzcKc3RhbmRhcmQgZGV2aWF0aW9uOiAwLjAKc3VtIG9mIGxvZ3M6IDQzLjY2ODI3MjM3NTI3NjU1Cg==", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{1000,-153,-1000,-376,-509,1000,-653,-511,78,-525,830,-666,845,221,-124,-531,-870,-850,1000,-824,-162,-774,-1000,371,-645,1000,-737,-678,-1000,-881,-1000,867,1000,-372,-934,-700,396,-1000,1000,200,526,207,947,303,266,1000,-1000,-514,-577,-30,452,916,-600,498,194,-616,-96,1000,-671,-281,1000,618,-1000,244}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("java.lang.String:U3VtbWFyeVN0YXRpc3RpY3M6Cm46IDAKbWluOiBOYU4KbWF4OiBOYU4KbWVhbjogTmFOCmdlb21ldHJpYyBtZWFuOiBOYU4KdmFyaWFuY2U6IE5hTgpzdW0gb2Ygc3F1YXJlczogMC4wCnN0YW5kYXJkIGRldmlhdGlvbjogTmFOCnN1bSBvZiBsb2dzOiAwLjAK", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{33,3,-508,-641,-1000,-557,-676,588,505,-219,627,926,976,-760,-468,148,260,-285,604,-1000,58,-1000,1000,835,-923,546,673,-1000,-442,-11,282,-120,759,-877,703,-367,-372,74,-558,1000,-983,-332,896,-886,-257,544,-774,437,-32,897,-1000,273,824,-646,330,47,777,-1000,-531,777,88,-1000,-335,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SummaryStatistics", "org.apache.commons.math.stat.descriptive.SummaryStatistics", "toString():java.lang.String",
            new int[]{1000,915,-1000,73,-29,762,1000,53,944,401,1000,473,1000,-153,515,1000,-1000,647,1000,-294,415,-1000,-865,1000,-427,62,-1000,-1000,-1000,-1000,-1000,-311,589,-922,-1000,221,-1000,106,-637,536,-437,-514,1000,308,1000,48,-1000,-1000,1000,1000,-413,503,-847,-875,-1000,274,367,788,-306,-890,208,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,-47,563,1000,-317,-1000,100,-400,-613,-789,-1000,553,336,-325,-127,90,929,669,952,121,400,-386,236,-344,-1000,1000,734,-1000,443,-131,30,513,1000,-431,1000,-441,-426,-788,-119,-967,-478,-387,-548,-538,29,152,-1000,-610,-1000,1000,345,1000,-34,-13,-24,-830,-1000,1000,-234,1000,468,-400,258,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{745,-25,-1000,204,317,-377,1000,194,1000,86,-1000,-1000,336,1000,1000,776,567,-1000,605,-22,-800,519,1000,1000,1000,1000,-1000,-1000,-1000,242,1000,-1000,1000,-1000,1000,639,-1000,1000,1000,925,1000,-1000,-1000,1000,1000,1000,510,1000,-233,-1000,816,-1000,-1000,-952,692,1000,-1000,1000,731,-1000,-1000,680,-1000,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{672,470,834,-296,-611,-400,804,-567,-575,-670,-308,492,666,168,-741,-177,950,1000,1000,-1000,1000,-999,-135,-666,-1000,400,761,564,1000,-932,-279,-12,1000,-919,383,-335,-174,-811,-280,-450,987,-744,-866,-677,798,516,-258,-1000,-1000,602,207,1000,491,-568,1000,-870,-929,52,73,997,164,-1000,39,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{217,441,-1000,634,319,601,-91,1000,-1000,-240,-1000,116,809,-1000,1000,-1000,508,907,801,-1000,-1000,1000,1000,9,122,-34,1000,-1000,-1000,-936,-1000,913,905,-1000,298,-748,666,105,477,-269,-701,-651,-1000,1000,-1000,-1000,-733,655,-1000,-366,963,249,-424,1000,-895,-635,-1000,-359,-916,695,819,1000,678,682}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,643,-194,597,-98,1000,1000,-1000,1000,108,-1000,-1000,-1000,1000,1000,1000,697,-1000,1000,-170,-500,-233,-1000,-195,1000,1000,-659,-923,-230,788,1000,-1000,-110,-1000,1000,1000,-474,1000,797,122,864,-1000,-1000,1000,1000,1000,830,1000,1000,-811,84,1000,-1000,-936,941,1000,-1000,46,88,-1000,1000,189,-1000,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{863,411,-378,584,311,-912,892,-1000,758,776,176,218,-197,1000,-92,662,333,479,-362,887,-60,-50,-886,651,-988,-1000,-992,-824,-284,25,1000,-917,-836,875,884,-501,-358,-147,1000,-927,547,204,-1000,580,636,271,365,197,-1000,29,394,1000,-870,-875,-776,916,1000,56,402,95,185,398,153,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setGeoMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,98,-107,-156,-69,240,-111,239,-450,-933,496,229,543,-396,288,-108,155,222,208,-400,-400,175,104,-280,-597,351,-21,999,-151,-85,282,136,1000,-523,69,455,-121,20,349,78,507,-387,-431,459,-330,703,866,374,-70,119,288,20,638,400,894,138,379,-651,116,196,113,-97,-1000,945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{113,21,-1000,314,1000,1000,-469,583,291,785,750,318,-1000,209,-362,-664,561,-735,164,912,-625,495,-445,494,-1000,769,230,250,-631,-388,-218,790,-537,46,-119,-28,-1000,-1000,177,-1000,-559,-123,-1000,762,1000,267,-254,-412,-274,-1000,-73,-1000,609,352,-1000,754,-651,-988,-727,-400,616,211,444,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-170,-539,-458,549,444,1000,-429,292,291,477,-358,-200,-1000,209,-1000,-374,-159,-674,208,129,-283,-534,-446,25,-783,769,230,392,-472,46,-795,663,-58,247,-119,-28,-495,-1000,-1000,-202,-537,-236,-798,1000,650,238,-299,628,312,-906,-263,-1000,540,296,90,27,-400,-961,-1000,-400,547,-756,254,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{60,-603,1000,639,599,1000,-5,663,-532,-533,527,-936,72,-496,645,1000,-240,-1000,464,1000,-212,-1000,-788,1000,333,120,235,1000,-409,1000,-1000,-506,-378,1000,673,-1000,-925,-1000,-1000,-574,477,-1000,42,973,1000,1000,-171,924,-457,-870,-1000,-131,1000,-1000,-144,1000,-1000,-53,-546,-117,809,-1000,-1000,-292}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-467,661,1000,519,892,1000,-812,-647,-1000,-979,-1000,91,-991,1000,1000,1000,-1000,-1000,-95,1000,1000,-1000,-1000,1000,553,-1000,-1000,469,-1000,1000,-1000,-700,-1000,1000,-617,-598,126,-301,-717,1000,-480,882,-188,1000,589,-1000,-1000,742,142,-882,376,-1000,1000,-1000,-1000,1000,-651,-733,-677,-586,820,-1000,-329,-987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{654,357,-179,-466,-278,-759,-864,393,71,562,592,-712,-284,-715,-577,804,115,-874,-999,-717,-392,721,217,-609,772,714,-391,822,63,521,990,348,-986,-822,794,-169,778,381,-609,-678,-120,-406,-794,-105,643,941,-343,-514,964,-526,634,700,860,380,519,245,-488,689,-390,-511,436,-848,-801,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMaxImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-143,329,-1000,-13,-371,-389,4,1000,-532,-651,-699,1000,-637,532,-437,-717,123,564,156,789,-657,77,-111,-400,65,122,39,-99,239,-729,-92,683,-977,-605,-533,-24,763,506,679,365,-185,-244,-665,-1000,-72,-4,249,790,1000,618,694,80,-400,453,-574,-986,400,227,-88,54,289,116,937,688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-140,301,-938,88,-630,485,194,-509,-366,673,879,83,-534,916,1000,-783,-1000,-222,1000,-771,501,-569,299,-435,465,-610,-279,215,1000,-27,763,-1000,-717,-1000,-356,669,-595,280,237,110,652,-520,8,-1000,-135,-220,-1000,1000,-1000,539,-1000,1000,-968,-71,416,1000,-910,-636,282,97,140,-958,308,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,715,-29,802,-511,749,-482,769,-83,-315,112,299,-109,1000,331,-680,175,87,1000,-418,887,613,-377,-1000,1000,-269,-271,720,-1000,1000,-1000,-1000,1000,-469,891,-699,-734,939,58,-123,-114,-1000,883,-529,-99,1000,713,-553,404,-444,-1000,842,-867,-5,-590,-369,-788,-593,-232,-128,1000,-1000,688,-877}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-131,-623,-667,1000,-296,302,1000,-322,-1000,1000,1000,527,-805,1000,-818,-1000,174,-1000,460,1000,252,-969,-103,63,635,77,-124,-267,-188,-235,325,746,1000,-1000,252,-1000,349,-1000,124,-525,865,160,14,-376,-393,922,-1000,-561,-917,316,-538,1000,-785,517,-1000,-905,-1000,-918,689,160,795,-500,-1000,140}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,-519,-884,909,-1000,1000,-997,-509,-839,-1000,-362,-253,-764,324,538,1000,1000,1000,1000,-606,1000,-425,-1000,-696,1000,-1000,252,-249,-1000,-198,1000,-499,952,857,1000,-1000,-282,-940,1000,157,1000,-231,1000,-65,-1000,563,115,483,573,-211,-1000,-847,-1000,718,-1000,-1000,-478,-1000,1000,1000,1000,-1000,-586,209}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-575,-969,-417,1000,-52,99,240,-273,61,-234,-344,177,-1000,878,-1000,-587,799,801,1000,559,521,-76,-1000,507,1000,-536,-421,-749,-722,-1000,1000,1000,660,-57,815,-998,336,-1000,1000,-1000,-245,447,-99,484,-1000,-311,-1000,-1000,710,320,-59,1000,-248,1000,-988,-1000,-217,-923,1000,1000,903,-1000,-1000,989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,674,-667,-806,-1000,1000,-752,-377,-1000,611,1000,1000,-1000,1000,-431,53,-361,-1000,460,-1000,1000,-1000,-78,-1000,1000,-1000,830,-79,-631,1000,606,58,506,-828,-82,-1000,-1000,1000,947,1000,444,-1000,1000,-1000,-1000,-1000,-541,1000,-1000,-830,-538,494,246,-737,1000,-1000,-823,-1000,689,137,1000,384,54,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMeanImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{132,-1000,-426,4,-769,-855,1000,-1000,-881,1000,1000,892,-1000,-92,-862,-138,-67,-1000,-436,812,56,-1000,218,1000,526,513,-1,409,811,-1000,693,1000,720,146,133,-868,654,-1000,-742,-54,1000,1000,148,1000,-307,892,-1000,-1000,-1000,30,169,1000,-494,1000,-294,-829,196,-109,1000,537,-184,-1000,-1000,-985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-795,453,-1000,239,-1000,731,363,-921,1000,-1000,-58,-599,98,-757,-25,-1000,-134,951,-75,1000,819,945,-1000,400,-1000,1000,-1000,221,1000,-141,-1000,217,812,-734,1000,970,-379,-1000,476,-790,-582,127,1000,-703,-616,-221,-822,556,-268,122,621,-621,1000,953,-470,-67,192,-955,-86,290,-50,-443,-224,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{550,367,714,-606,-313,-942,-644,343,489,512,747,-1000,-142,561,893,559,138,-328,174,766,1000,254,-468,-534,105,-419,-1000,194,-596,547,616,331,911,773,-770,1000,1000,1000,365,-377,-32,877,874,-1000,-758,27,-389,293,-363,-502,-402,-711,532,667,-738,376,-277,-306,-1000,-129,845,-516,-358,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-120,1000,-561,-468,1000,1000,346,-951,-1000,794,-725,-1000,39,607,220,-595,-594,-1000,598,-1000,229,-1000,1000,862,1000,-339,1000,665,-1000,1000,1000,287,-247,-92,-1000,231,-812,-626,1000,199,-71,-372,893,630,853,-1000,370,-1000,1000,-743,359,-116,-724,-739,-23,-241,-1000,848,-38,1000,866,-315,1000,529}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,-1000,-316,-316,-1000,-939,1000,-379,1000,1000,-685,-803,1000,166,-1000,-1000,1000,1000,790,1000,1000,1000,-342,-1000,-1000,-1000,-1000,-261,1000,1000,-2,1000,-309,-617,786,1000,1000,1000,1000,-1000,1000,-769,-1000,-1000,-1000,482,-1000,1000,398,-758,-154,-1000,1000,1000,-1000,1000,1000,-1000,-1000,-1000,1000,-886,-305,762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setMinImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,202,-883,-561,-949,-1000,1000,-979,-1000,-1000,-363,-719,216,41,497,-753,-646,366,-82,-380,-410,192,-482,507,-364,1000,-661,835,572,-513,-1000,-966,-195,223,772,190,-85,-755,940,-165,-44,103,799,-547,-1000,-403,-813,1000,1000,-391,1000,-1000,-741,-901,-862,-608,-216,-378,818,-21,-227,-571,143,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-375,25,-224,129,1000,-450,1000,417,871,714,954,327,51,1000,1000,482,323,44,64,-430,-1000,173,-2,-437,947,713,193,296,-361,-892,-1000,-400,212,146,592,1000,1000,-137,-1000,710,192,876,-675,-107,917,403,905,-281,-362,-163,295,123,-1000,132,-376,-696,173,-228,500,-862,-298,-166,-568,564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-296,130,104,-351,-495,28,-293,-115,-428,173,-401,-463,-781,531,-480,-803,-232,-119,-513,-506,796,-219,134,16,984,227,-408,391,-714,-314,-542,-84,794,-330,318,-984,-735,826,964,-967,598,-365,955,354,739,248,-476,749,-551,727,913,-80,175,-983,-772,737,-129,-654,613,408,-993,429,-849,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,-376,-271,944,1000,-178,-1000,63,-128,-257,225,284,279,1000,1000,-961,773,-892,-91,-659,706,-1000,-196,-1000,817,1000,-1000,-130,-1000,314,-1000,-520,-228,-781,-160,1000,1000,-1000,-1000,-80,-1000,1000,-1000,1000,1000,-1000,1000,-1000,-728,1000,730,-193,1000,1000,1000,633,681,-374,229,-1000,-212,-1000,181,513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{923,-319,-263,-1000,-663,-708,1000,-411,-710,831,-1000,-714,-236,260,-480,861,-232,682,-956,1000,-79,-142,551,-407,976,227,868,-287,546,620,-701,600,393,-330,-1000,-139,-735,730,-716,289,1000,645,955,-1000,-331,1000,778,749,1000,301,313,660,101,-1000,-1000,-1000,-238,291,-1000,989,135,887,274,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-270,-68,38,439,-1000,-851,-242,-433,-137,-551,798,197,-1000,498,480,-941,669,-152,-1000,987,1000,390,-1000,-246,974,584,-528,-796,206,-117,-1000,-761,-1000,230,1000,1000,829,787,400,-203,-789,-271,-606,523,728,-589,1000,-9,-737,607,-443,-563,1000,995,189,173,908,-217,378,-1000,-1000,-851,-18,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{385,-287,-974,-131,-459,190,477,-247,689,-119,535,151,152,-207,988,-710,-197,-141,-618,-276,492,260,-293,26,-707,-261,89,-5,387,276,73,-225,306,985,930,958,-532,483,499,-725,-536,127,-450,-216,-610,521,-649,613,671,436,222,255,295,945,200,171,-876,-39,-369,-966,-928,-409,-498,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-127,-451,1000,-91,309,-678,-140,-681,500,518,-12,-610,407,535,1000,421,752,-480,-1000,-317,803,529,-348,-1000,-892,-330,-890,-914,-385,1000,205,165,1000,158,15,-295,-498,-1000,1000,488,767,-448,-23,-41,-421,-1000,-390,-96,665,-968,1000,322,15,207,544,-1000,1000,-47,-900,236,-224,-782,-400,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{146,677,146,89,-309,964,-1000,-9,-968,436,-786,412,-438,-337,1000,392,-463,93,-244,283,440,-352,395,1000,-1000,257,-355,-509,-28,34,204,763,-318,211,400,445,335,158,297,-383,493,1000,480,1000,-1000,400,-1000,-149,410,893,-1000,364,-567,480,83,-400,-371,435,-285,287,-449,-122,573,-54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{734,-451,-208,3,181,695,-667,804,-686,-5,-1000,-760,150,535,566,568,28,-283,-204,1000,-768,-1000,-299,331,799,144,-890,433,-603,34,188,165,-491,-68,-987,-295,-254,273,-552,488,767,985,834,-285,256,-1000,-82,604,-916,273,58,427,15,570,-883,837,1000,-47,-248,-249,-14,-346,835,-454}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,469,-1000,198,-948,1000,-267,-255,-491,-124,306,-145,105,-236,684,-163,-877,361,-276,1000,164,-787,198,-45,552,294,-58,922,-258,-147,-427,590,-686,519,-395,51,-9,945,-502,-345,483,1000,366,-76,-255,-519,-6,922,-411,1000,-438,248,81,943,-338,533,-29,247,-367,-633,-840,-1000,-379,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{335,585,133,-548,567,-946,-839,-374,298,-98,-1000,-7,-524,1000,1000,-185,679,-567,-852,-1000,734,1000,-560,-621,-105,99,-230,-1000,-291,734,88,-1000,94,-517,10,589,412,-962,899,671,-38,-369,1000,-278,-389,-179,-345,178,-54,-518,102,-261,-311,319,-367,255,-570,-1000,-66,-847,-223,664,177,482}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumLogImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-547,82,-173,649,401,-1000,-1000,1000,919,759,-1000,1000,141,1000,-112,1000,1000,-1000,536,48,-489,749,-1000,-875,131,67,-1000,-1000,-1,398,114,1000,1000,361,486,-644,-1000,-1000,-310,565,-362,117,1000,-216,311,-762,-1000,-838,-649,-1000,932,506,114,808,-1000,585,1000,992,416,519,368,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.MathIllegalStateException", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-1000,265,1000,312,-729,-1000,1000,1000,-1000,181,884,233,285,-249,563,1000,1000,-565,746,-152,-1000,-35,1000,148,961,-808,-1000,-1000,565,190,-1000,757,516,1000,543,487,208,-310,1000,-369,776,-1000,1000,27,-1000,-1000,-1000,-474,-886,-915,117,647,-669,958,516,-1000,-743,-72,157,-660,196,-700,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{30,994,-356,582,-123,-764,905,-72,830,453,-117,536,603,708,-933,144,63,-257,757,472,-823,686,116,536,-866,-539,-338,-419,-838,-196,388,672,403,214,-171,965,149,-836,613,196,-281,489,297,-969,317,-40,614,-413,-702,768,-343,-370,484,-113,-22,-232,351,-910,-910,-723,-724,-215,460,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{978,-731,-969,-390,729,-816,-390,-975,1000,57,-487,1000,43,-1000,-326,-651,1000,-545,-1000,997,-406,596,1000,-132,-724,-619,-1000,-118,-387,-1000,-232,-1000,683,-364,1000,1000,-691,-1000,-559,-767,-138,227,1000,-1000,814,-1000,-97,405,-1000,978,307,1000,-266,-299,-1000,42,995,260,-1000,-672,-232,1000,36,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{567,-439,400,229,729,-1000,-390,-975,1000,-1000,978,752,527,-927,-689,-651,627,86,-1000,696,-694,596,1000,-844,400,-931,-1000,-118,-387,327,1000,-941,-220,-207,-207,-1000,992,327,1000,-767,-138,-399,1000,-394,1000,-1000,97,537,-1000,-1000,-585,809,-894,1000,-1000,-175,397,857,-1000,-222,-1000,488,1,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-475,-951,230,-397,482,-180,1000,173,577,125,-44,1000,87,-1000,1000,790,777,-681,400,-1000,-1000,-323,861,754,349,-528,-1000,-343,523,-619,-234,-316,-698,595,525,1000,-671,-1000,460,-1000,1000,-178,1000,-2,-766,-663,-1000,-985,-1000,-1000,-86,-1000,-478,1000,597,-659,-599,-1000,-1000,-457,-294,-545,-143,-579}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setSumsqImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-960,836,403,-91,481,363,954,248,33,499,-838,476,-157,-772,556,937,581,-637,906,124,-630,-219,-734,455,49,184,-977,519,-671,-420,-357,-398,-764,88,4,305,172,678,345,-441,-92,41,186,-514,-122,-121,-123,-833,-760,-374,-182,627,-647,842,-125,-874,569,339,0,-816,-501,-514,-319,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,571,-331,-208,84,930,-761,-1000,337,566,-159,491,-949,1000,1000,-22,1000,1000,138,-1000,-295,-545,796,-753,-708,-1000,75,1000,283,-949,1000,-1000,-1000,-1000,-1000,340,-279,-699,1000,1000,1000,1000,1000,1000,-654,1000,891,1000,-884,45,1000,-444,177,-152,988,-1000,82,1000,1000,767,-1000,1000,-42,834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{761,89,-1000,-346,-386,1000,-1000,-626,417,-105,-426,1000,-122,1000,1000,788,1000,-72,-551,-5,-1000,377,583,-1000,-578,-1000,953,1000,-368,623,1000,-337,-1000,-1000,865,-156,-1000,-301,974,1000,1000,1000,1000,247,44,1000,1000,168,-541,395,714,-696,192,-1000,849,-504,-1000,-75,-732,-348,-1000,-677,-610,-14}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{833,643,-244,304,-564,547,-278,-154,-98,-696,-961,466,820,79,452,1000,907,275,-1000,825,-420,-290,-406,370,-617,195,310,654,101,1000,176,247,-455,-196,1000,767,-1000,-301,-657,446,524,53,294,289,1000,621,287,198,57,-831,674,-665,-424,-1000,-123,400,-892,1000,-639,-237,220,-990,-910,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{798,59,1000,248,-271,951,-870,692,-1000,711,-939,729,403,-247,739,-558,857,1000,-204,673,-559,517,903,751,-1000,-290,-429,712,404,830,-629,-300,415,228,1000,-81,-1000,-1000,1000,400,597,653,-822,-224,652,141,641,-575,-1000,-1000,-74,-270,889,-294,-954,-526,-317,-1000,131,-1000,386,640,-513,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{293,747,172,688,583,208,-142,-69,910,1,635,-806,226,-619,729,-667,562,1000,143,-931,159,605,1000,-477,707,229,-341,-334,81,542,-263,-308,99,-236,797,-934,-697,88,1000,1000,266,453,1000,825,58,505,-892,-904,-1000,-764,617,-403,177,-708,761,-144,-495,374,743,384,329,1000,582,10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{1000,855,-1000,333,592,930,-242,-1000,790,1000,360,88,-821,1000,1000,-456,652,1000,215,-1000,-458,-1000,1000,-753,-858,-946,323,20,259,-1000,1000,-1000,-635,-1000,-1000,1000,-606,-1000,822,1000,1000,750,1000,1000,-830,1000,1000,1000,-798,43,1000,-1000,-113,-152,1000,-1000,293,1000,263,1000,-1000,1000,-362,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "org.apache.commons.math.stat.descriptive.SynchronizedSummaryStatistics", "setVarianceImpl(org.apache.commons.math.stat.descriptive.StorelessUnivariateStatistic):void",
            new int[]{-400,693,-320,-237,-726,-109,-1000,-863,417,359,-632,498,-1000,1000,1000,488,1000,1000,-17,-420,-295,-717,339,-753,-708,-1000,606,760,331,-14,775,-1000,-1000,-66,1000,310,-279,-16,756,-839,350,1000,624,911,-654,1000,297,700,-525,-146,326,-444,851,385,689,-1000,-287,1000,1000,-644,-859,1000,-395,440}));
    }
}
