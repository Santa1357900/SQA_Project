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
        org.junit.Assert.assertEquals("java.lang.Double:NTE4LjA=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{998,-444,-518,-973,829,697,879,-306,-155,-403,259,566,-798,-608,675,-224,-331,999,-425,-183,-775,-604,-180,-324,-494,-123,313,807,715,728,331,501,-674,411,176,861,-246,-537,389,-241,80,59,443,-75,832,-536,-429,-380,879,-554,-834,-790,-438,130,68,432,-690,-258,981,-889,232,-769,232,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-802,697,421,482,-1000,-196,-596,-213,-784,-181,358,-1000,851,829,516,909,19,-1000,-699,-762,372,250,-109,-956,-221,1000,1000,-731,-1000,225,-441,-1000,-338,-1000,157,-751,-857,486,-999,-993,525,-774,-1000,1000,-146,261,124,621,-400,393,1000,1000,-747,370,-282,-351,507,703,-1000,1000,-998,877,-948,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{1000,449,-55,-1000,1000,163,523,-465,-1000,-55,1000,-610,-53,-770,1000,-613,-631,1000,278,-1000,-1000,-1000,-210,-1000,357,256,-176,465,1000,1000,515,52,259,-250,-1000,905,-1000,-1000,-32,-523,-469,-1000,-237,-492,1000,-229,-882,-1000,1000,-555,-689,188,-1000,-878,551,191,-913,873,232,-1000,-435,-975,-262,919}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:MTguNjI2ODYyMzIzMDAwMDgz", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-332,343,-726,638,186,-542,-406,104,278,455,29,-525,604,915,920,693,659,-399,-682,-429,-282,-397,-303,-369,-544,-172,858,-486,-425,247,-1000,-1000,518,-916,365,-272,-886,170,-651,-1000,179,-828,-1000,549,-1000,50,-390,513,-239,418,928,481,-704,-321,-229,-394,167,375,-494,456,-666,-777,-275,747}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-58,-142,858,96,-428,727,-601,-364,-1000,-885,-45,-994,530,-106,411,291,-1000,-305,-692,-1000,-211,-455,235,-1000,1000,-133,416,-177,-157,328,429,-746,516,-850,689,-1000,-249,1000,-1000,-1000,302,424,768,1000,1000,540,-519,722,28,-253,408,1000,-379,1000,-936,90,-217,-13,-557,162,412,1000,-409,982}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{400,1000,-718,-847,-358,1000,-1000,-1000,381,269,-120,-579,-400,-641,142,1000,112,658,75,4,-291,1000,955,-935,689,1000,-353,1000,1000,-453,646,-1000,-646,376,-348,659,1000,679,289,1000,-1000,-1000,-1000,-400,226,-882,-130,-633,507,400,-641,-1000,1000,-367,-587,984,43,-602,-1000,-870,-598,1000,-1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-419,1000,937,530,621,684,-464,406,-42,1000,-441,-467,626,-644,-727,-340,-141,7,-963,-461,554,-771,-106,-274,-927,134,176,-573,320,-281,-376,640,111,241,442,-1000,-623,379,-1000,1000,246,-194,576,422,52,-636,1000,-688,-1000,644,1000,-960,310,-1000,-1000,121,1000,662,-543,1000,-1000,-231,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-190,-851,843,231,-1000,1000,929,-1000,335,-773,523,-20,627,-276,433,91,518,22,7,-258,-91,1000,-182,-41,180,-255,656,751,-67,73,457,-509,-760,223,893,626,-322,-1000,400,-470,523,-541,-1000,-641,-379,60,-1000,445,217,-400,-189,886,440,-309,-1000,-369,1000,816,678,-971,1000,-1000,350,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{263,-463,-1000,-487,1000,-665,533,538,-462,407,-1000,653,-1000,-387,-949,1000,556,329,-927,325,-338,-1000,706,-154,793,308,-525,-207,593,-776,915,-966,-229,679,462,-607,1000,1000,-1000,1000,-625,-828,-1000,-632,-151,-843,1000,-1000,638,1000,853,-1000,986,-1000,1000,-740,-1000,-662,-757,1000,-1000,944,-305,-74}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-969,1000,1000,745,7,-898,-362,871,-205,1000,-390,-149,-99,-409,-1000,-1000,236,491,553,630,-231,-1000,-846,-1000,-1000,-607,-1000,-1000,-449,-821,934,1000,1000,-516,58,-1000,-1000,1000,-1000,674,-809,1000,-486,-1000,62,-985,395,1000,730,1000,146,561,-1000,496,622,-845,401,-599,-1000,1000,-1000,-299,-574,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{628,112,127,-1000,-91,763,-1000,-440,-1000,1000,334,-1000,626,-224,127,457,504,-767,823,189,-399,-697,350,94,363,431,563,-447,-818,-685,-752,-759,-581,728,804,1000,1000,-473,436,205,241,1000,-681,803,-1000,-736,-1000,280,-1000,1000,-1000,1000,-948,1000,-1000,-1000,731,772,1000,420,-1000,641,-245,390}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,-75,-269,-1000,-534,763,-154,-165,-993,280,-742,-261,-291,-237,-695,576,-492,485,885,143,-312,-687,285,-306,47,254,-171,313,-256,-877,-697,-492,-1000,1000,804,1000,-400,116,634,487,1000,603,336,803,156,-736,-1000,-316,-1000,726,-453,832,-893,-158,-732,-625,782,207,787,-27,-1000,404,-276,-370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,1000,539,314,744,1000,857,-633,506,-495,703,-1000,989,115,-579,904,-88,907,1000,-219,-1000,1000,-6,378,-1000,-1000,-1000,-787,-916,-1000,-1000,1000,1000,-1000,1000,941,-1000,-1000,-1000,1000,-530,-585,-701,843,630,-1000,246,-159,1000,-1000,-1000,443,1000,-1000,-699,-1000,90,-1000,-1000,-794,1000,-1000,-958,525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-283,-87,-399,-449,-225,855,-562,202,830,432,411,-497,-328,1000,605,345,213,862,931,1000,352,-22,-1000,354,-1000,825,315,1000,40,-401,767,289,-379,-1000,-4,122,837,-200,1000,-468,-238,543,-1000,-691,556,-384,118,-694,-1000,-1000,440,-670,701,-162,-250,-664,443,612,-128,-921,-189,407,226,931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{710,844,587,1000,-82,974,1000,-307,899,-311,-126,-747,-504,-305,-769,-232,-480,-1000,-480,166,-143,1000,576,-241,-644,-521,-591,-736,29,247,-803,890,587,-391,16,-444,1000,1000,-596,-880,800,-288,618,-125,804,992,485,-782,279,297,93,-284,58,-97,35,147,63,-772,-229,-1000,707,-327,161,-765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{432,-440,-980,-541,958,834,-592,-413,-122,346,-52,-547,305,-57,55,-561,-29,1000,1000,-297,-831,524,-749,112,-696,-1000,1000,331,768,1000,129,425,1000,711,-1000,-314,-1000,-456,129,-298,-27,-245,864,900,-36,13,1000,-745,378,-429,304,182,1000,-958,-644,-1000,694,638,-10,1000,94,-296,363,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{463,-921,-228,-598,-47,309,-831,-265,-1000,-510,-401,-782,-1000,-224,-795,-307,374,250,60,448,-78,142,-556,1000,-842,-694,-651,-323,-76,312,328,-849,400,1000,-826,-701,-519,983,-625,-577,-738,165,109,328,-465,-779,-1000,653,-1000,573,105,-506,388,-247,-768,-88,-626,-558,372,123,402,-11,-1000,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-1000,561,-554,78,1000,677,74,1000,727,-356,-926,784,1000,-1000,-667,-618,1000,1000,-1000,-815,-295,275,349,493,415,-653,-585,1000,-149,524,1000,-592,-489,1000,1000,-351,691,475,-1000,982,-879,642,-696,-1000,-228,230,-1000,-934,1000,32,-903,-610,738,-1000,1000,37,1000,-1000,-84,-1000,1000,179,428,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-681,919,892,-247,1000,-857,309,1000,869,-1000,237,1000,1000,-1000,-974,-466,1000,1000,-1000,-1000,-1000,744,321,1000,966,-1000,-1000,1000,664,1000,1000,-1000,250,1000,1000,1000,-1000,-816,-1000,529,-971,-445,260,-661,565,60,-1000,-323,617,-968,-269,-631,1000,1000,1000,-461,810,1000,-155,-509,947,-1000,113,138}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{609,-1000,333,-515,-782,-1000,451,-1000,531,1000,1000,-725,-446,-93,684,1000,-587,155,320,-297,-1000,-1000,-104,1000,1000,-133,-626,476,511,1000,-1000,-256,-1000,-416,-1000,-124,-1000,-282,-488,1000,-76,-1000,-116,-722,809,-1000,1000,-157,1000,1000,-1000,-910,374,-1000,-1000,1000,1000,-171,1000,173,-1000,-846,-257,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{1000,-643,-68,805,-216,1000,234,600,-495,-401,270,1000,982,-185,454,541,-341,-175,938,-1000,874,113,697,197,515,676,744,-560,-524,213,697,-172,-1000,1000,556,-223,-78,1000,370,-1000,-154,-1000,1000,-23,270,1000,-198,-1000,-348,-252,-788,279,-139,68,-1000,-985,286,-994,898,-464,-631,89,785,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{1000,-745,-222,838,-274,740,-542,803,-746,-23,800,555,162,-79,379,728,-1000,1000,1000,-927,241,821,281,320,-671,711,-1000,36,95,155,649,-662,-1000,1000,904,-486,379,888,370,-452,-1000,-1000,680,557,-477,417,-378,-1000,534,-381,-474,691,194,60,-1000,-410,-1000,-719,555,-108,-962,-792,686,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-1000,803,-84,-504,-909,-274,-332,-262,-590,940,-155,42,-1000,780,367,-394,113,22,-620,-134,149,-540,-359,421,-400,-29,371,-446,-640,306,625,-400,-112,-1000,54,-1000,-347,870,1000,163,-1000,1000,-438,-1000,643,136,734,723,-94,133,159,218,107,887,854,386,-1000,-455,91,891,-162,-258,-11,-548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{215,-455,733,333,219,67,-322,1000,-145,65,29,555,-400,-372,341,465,-738,-325,747,-539,530,-90,61,294,386,522,1000,-581,-1000,255,1000,-201,-528,696,-381,-361,-502,11,1000,177,-177,-471,84,-492,489,-209,209,161,-754,-213,-674,305,-396,99,-46,-370,917,806,1000,-1000,-532,653,662,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{91,1000,1000,-886,-673,-1000,-925,-1000,78,1000,895,518,-351,-1000,-968,-677,-1000,90,121,-122,1000,735,-552,636,-1000,288,1000,-595,-1000,73,-177,279,-839,653,-1000,701,1000,646,-398,-262,-186,-608,-1000,-62,1000,-1000,516,-552,-562,1000,-924,-960,-750,-1000,-1000,1000,-1000,-565,1000,1000,554,-338,-1000,635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-200,384,-1000,264,-333,-205,503,-556,572,55,464,-254,19,-667,-990,-370,162,1000,734,-432,-182,-107,-361,305,652,378,-689,-393,771,-570,1000,-84,129,-599,1000,334,-741,274,277,-396,-621,-518,63,-352,-124,-318,-279,-12,433,1000,-228,273,345,-108,1000,-400,479,1000,-97,-50,-1000,6,615}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{1000,1000,-985,151,943,981,577,-1000,-285,-1000,285,-396,-1000,705,-968,1000,624,413,100,333,103,962,55,696,-1000,672,1000,134,621,-1000,538,-427,-625,1000,1000,886,-1000,31,-135,854,997,323,-276,-1000,-357,899,823,1000,156,1000,-97,637,1000,-942,169,702,1000,-1000,1000,803,364,686,515,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{1000,433,44,447,1000,-580,-166,-425,1000,1000,-1000,951,-143,-272,-509,-1000,-1000,-585,1000,1000,565,1000,-475,175,-1000,-352,1000,633,-557,-249,-338,-470,764,-1000,593,-35,-1000,1000,-1000,72,38,-97,624,-523,669,627,-747,883,-400,-1000,855,270,774,800,261,-511,-372,906,-935,-1000,557,954,-1000,663}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{1000,895,-694,-16,733,823,-738,-209,768,452,-289,1000,824,-958,-422,-712,-1000,1000,-238,68,160,-438,-1000,43,496,1000,767,-251,-939,-606,713,917,1000,-1000,-668,783,-1000,1000,-637,656,731,703,652,-36,-305,-799,-188,577,-1000,-456,813,873,1000,1000,-584,-1000,1000,1000,-103,-1000,-369,34,-1000,-329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{859,-495,888,508,965,-211,549,-297,1000,935,-570,308,-1000,275,-627,-913,-279,-1000,676,379,1000,776,504,1000,-1000,-1000,-193,1000,505,632,-800,-1000,-528,-946,1000,-786,104,442,-1000,-400,-785,561,-773,173,798,694,-887,1000,1000,-433,-371,-904,495,484,1000,-932,-1000,-494,-527,-972,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{-560,236,495,-431,-941,949,526,-672,710,900,518,-283,-41,-335,-360,-407,748,835,-777,809,714,-415,758,872,-571,878,-758,-242,-994,-636,-707,862,666,-780,-770,-863,774,-399,935,-172,-338,-808,715,896,69,-606,621,-763,-523,108,715,-925,-235,789,633,551,641,446,-535,-535,130,-356,428,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{446,87,-34,-1000,-54,-569,68,1000,165,927,21,-694,446,-25,-552,415,-632,605,-494,468,-224,-412,188,1000,-758,303,-841,271,-996,54,915,-703,766,-1000,876,-768,1000,598,104,301,-885,-515,834,975,131,348,-66,37,106,-961,817,53,-843,943,-663,666,-1000,-12,681,-404,895,-1000,798,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{-423,-174,120,1000,-941,1000,646,-702,-202,182,518,369,-396,-1000,698,730,748,835,-128,-856,1000,-415,1000,872,-619,1000,-73,-353,-157,-636,-620,397,251,-997,-1000,109,-511,-889,202,289,1000,-808,378,-752,-833,247,1000,-455,-477,459,-674,-925,661,526,633,471,1000,1000,-1000,234,254,-389,-1000,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{-1000,267,-268,849,1000,-1000,-426,565,534,-377,55,460,-584,120,-216,-131,451,-899,-317,681,844,-863,875,-115,-1000,1000,702,-913,-212,-937,-238,-596,935,-772,-514,375,1000,-569,-1000,-4,584,1000,-87,-417,-211,44,352,1000,161,1000,53,881,829,-167,-442,197,229,-131,-636,-160,-816,974,1000,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{-214,-982,-603,127,464,-1000,-437,565,951,-271,158,-474,-279,-400,840,-287,772,-653,-855,1000,806,625,-174,364,367,952,-809,84,141,764,223,159,52,-285,-467,1000,105,549,-157,1000,1000,400,-149,793,-671,255,253,87,870,542,-394,102,914,877,-383,422,-142,-612,-21,59,551,290,-143,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{-394,267,-439,-67,788,-215,-192,-1000,-35,-173,587,368,307,490,-309,367,354,260,-651,679,225,-1000,327,-679,-1000,661,646,-558,-711,-1000,833,-336,935,396,52,0,319,-1000,-942,-9,-98,1000,-9,-1000,872,-745,1000,370,-424,1000,-562,1000,1000,-162,-18,186,1000,-110,-608,-429,-1000,274,660,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{156,-207,298,472,28,-1000,-596,484,1000,-1000,-13,-657,-955,79,571,330,1000,-1000,233,-375,104,656,610,985,276,804,-1000,484,1000,-660,-734,-462,303,-876,-210,124,342,427,484,-74,-163,301,357,-22,-953,781,-465,545,1000,56,71,232,292,463,235,-193,972,-1000,494,-457,582,-176,-315,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,267,-307,-806,204,1000,-192,-775,-139,-173,587,368,355,490,64,349,389,1000,-865,552,194,-763,17,-1000,-768,465,566,98,-711,-1000,1000,-305,1000,1000,-697,105,23,-1000,-885,-606,-245,1000,50,-974,1000,-1000,1000,40,-842,694,-314,1000,1000,468,204,226,940,-143,-608,-504,-904,763,91,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{-24,504,-508,-660,69,133,-71,-1000,-455,-44,454,573,349,-266,-445,738,-302,1000,-766,866,259,-1000,1000,-1000,-1000,-660,917,-713,-1000,-1000,1000,-303,33,1000,-328,-500,780,0,-991,-657,143,1000,-523,-1000,1000,-691,1000,286,-554,1000,-586,1000,1000,-474,-247,83,762,-23,-1000,-578,-1000,-120,-178,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{94,-199,622,519,-1000,-1000,-356,1000,1000,-1000,-624,-1000,-1000,-1000,732,847,491,-1000,837,-756,89,559,1000,1000,495,48,-1000,533,1000,-762,-1000,-525,-1000,-947,-497,-576,1000,835,-1000,-627,239,511,-161,201,-1000,1000,-1000,1000,1000,374,306,243,505,-581,-71,-634,655,-1000,313,-188,957,421,-375,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,786,-159,-147,-256,-1000,-396,-845,-1000,-48,1000,492,125,-846,655,463,1000,-774,1000,-1000,-1000,249,234,326,-1000,1000,-935,935,-315,73,1000,-1000,1000,1000,-1000,-952,88,524,-836,201,-440,503,63,-655,864,-1000,1000,-200,-49,-34,-915,-1000,1000,-111,1000,1000,998,-1000,-812,-117,-1000,-837,-175,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-199,483,-740,1000,17,-1000,-372,-1000,1000,556,-899,1,-1000,1000,137,1000,-839,459,-888,655,1000,-505,-243,98,1000,-366,-736,-605,-779,478,637,-820,-308,-930,1000,383,77,522,-765,-323,72,817,1000,-1000,1000,1000,-841,885,1000,1000,551,-474,-498,888,-1000,536,-828,-449,-502,-932,1000,-577,-2,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{779,-38,-1000,739,-11,-632,-1000,405,397,51,-1000,1000,24,-1000,929,1000,-1000,616,-1000,679,-798,802,98,831,157,-249,21,-655,569,-1000,1000,-757,-1000,1000,-400,892,1000,223,-89,1000,1000,-544,-1000,1000,-1000,1000,-224,-1000,-187,-711,1000,-283,55,-1000,1000,-265,-1000,313,1000,-405,1000,-946,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,-509,921,818,-684,-143,-777,-1000,340,77,-863,275,205,-183,120,475,613,1000,308,-746,-1000,1000,264,110,283,-445,-460,73,1000,-759,-140,-1000,1000,-547,-540,271,19,11,-1000,51,-805,-404,-646,-456,-20,-480,0,-1000,-1000,-357,-63,-1000,870,-766,193,1000,-1000,-1000,540,-929,689,-886,1000,-540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{747,1000,-1000,1000,384,-963,-791,-909,226,-1000,-302,74,532,1000,-555,123,-12,-234,-14,403,1000,483,202,-363,127,400,-1000,-1000,539,886,-805,9,-10,-1000,1000,1000,-475,1000,-867,-570,319,-347,1000,-1000,676,-66,-338,1000,1000,984,-1000,585,-1000,1000,-55,-151,-1000,-942,-1000,-985,954,-1000,-253,841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-919,532,579,-406,-721,-674,447,-1000,277,-835,208,436,415,-290,-780,-75,398,918,1000,-478,-339,-1000,84,-1000,-377,1000,406,136,-147,1000,-263,189,-217,-263,292,-861,-969,1000,-1000,-621,-1000,809,413,-774,609,-1000,206,1000,1000,12,-1000,917,-339,-50,993,1000,1000,1000,-418,673,-133,103,1,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{960,-172,1000,-564,51,-364,-384,-544,-664,-315,660,1000,1000,-525,1000,561,250,524,829,-901,-1000,1000,1000,-3,-651,400,-1000,274,1000,-675,-251,-273,1000,930,-1000,590,379,1000,-225,-472,-528,-277,-1000,21,-612,-175,1000,-200,143,-387,568,-161,1000,-747,1000,1000,375,-1000,916,607,-750,-810,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{-104,-426,385,-907,-467,-849,186,295,-830,165,-849,-563,-158,958,247,-924,279,-53,-695,-712,-343,-111,321,358,254,-537,397,-342,688,-436,180,279,997,-622,-40,540,-691,-757,695,371,89,-399,-870,737,34,-77,920,-226,-441,373,-792,119,-475,236,453,14,368,598,478,222,100,667,-315,-483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{-263,67,-1000,1000,-289,-1000,-891,486,-1000,-197,158,-497,34,345,-62,-500,1000,658,96,-69,685,1000,118,-294,-261,573,169,-1000,-664,132,1000,-1000,1000,1000,-622,862,-719,686,-431,-371,-776,-316,-158,1000,-177,1000,-424,1000,-148,463,-430,-1000,-687,494,845,-33,961,-57,-11,-401,-850,1000,460,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{-731,851,519,-859,919,499,-903,519,988,-230,-430,36,-356,-950,-981,237,12,-743,687,-875,-653,-351,802,983,432,783,-803,-851,768,-843,970,-2,749,781,-230,-126,-543,-183,-641,654,-20,-225,-943,755,-778,265,356,908,61,281,574,-157,411,-859,751,128,255,773,-841,834,14,-143,777,-524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{-956,-1000,-1000,-501,784,-1000,1000,-173,-315,1000,-663,-581,-1000,-216,-1000,213,1000,-1000,-1000,-533,-667,-581,-759,-580,-733,-338,-1000,-1000,-407,1000,-1000,-710,-709,-1000,1000,-814,-661,-1000,639,-1000,1000,1000,1000,-1000,-1000,6,-49,838,-737,106,-178,424,672,453,1000,1000,787,226,180,827,1000,1000,619,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{47,631,-979,1000,-396,775,327,832,-628,-30,609,-134,-694,-287,-1000,-814,852,291,-410,-827,-74,-751,361,80,-476,903,-876,1000,371,459,852,-586,-252,-905,245,1000,-339,-1000,-877,-701,888,282,-55,-324,991,558,-695,-722,2,-218,-595,-819,76,847,1000,698,1000,413,-725,-17,33,80,28,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{101,239,-574,-255,-246,298,-326,155,27,-57,-179,-3,-196,169,178,-507,559,400,-148,111,-250,-161,-772,656,-444,367,-606,-638,-761,103,-385,-222,-418,-507,583,1000,-562,-64,580,334,179,41,223,-43,-310,136,-211,460,48,374,-1000,237,881,745,507,-400,83,270,282,139,-42,1000,878,249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{-640,1000,-264,-1000,839,1000,-136,1000,-231,-418,661,-122,-107,842,1000,-77,-954,275,-473,-389,-564,-821,755,-992,337,-1000,-1000,-1000,919,-136,286,1000,851,651,-1000,-596,-1000,-340,901,989,-1000,1000,441,305,881,-1000,19,613,-516,-275,514,-1000,19,-218,-1000,-251,-1000,705,-113,-1000,-1000,1000,663,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{-320,-869,180,-153,-1000,691,-621,853,33,-1000,708,1000,-1000,545,-987,643,-939,-242,-141,1000,-944,-299,-1000,407,1000,-214,-447,159,84,349,312,-159,1000,101,-217,-92,38,127,714,-595,1000,316,-137,-1000,-431,855,-596,139,-334,1000,-1000,-573,64,-63,734,-1000,433,326,-240,544,1000,1000,-220,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-70,1000,-336,370,178,471,-1000,1000,-1000,1000,-79,-316,-657,-585,571,-429,238,-485,-154,1000,654,400,1000,-619,-1000,1000,-221,-554,545,639,-887,952,-1000,-588,1000,371,-255,-354,1000,-726,19,-245,1000,1000,-942,-299,576,-1000,11,1000,928,39,1000,-1000,1000,698,-844,601,-278,-715,-744,693,-493,-391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-364,-103,-691,443,1000,715,-261,-371,-777,-99,-90,1000,-699,678,-946,-66,-113,-552,1000,1000,-796,814,-1000,741,862,-368,-354,756,327,33,10,-586,1000,118,-180,-161,-64,-926,-621,763,234,-1000,-722,-778,-748,-1000,-741,435,-892,989,-658,-664,-457,1000,-341,-924,1000,-1000,836,-635,1000,-252,1000,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-1000,-64,-1000,1000,79,-272,-139,46,719,-77,-431,791,-271,472,1000,100,271,6,352,1000,788,-1000,350,764,-792,-272,601,-1000,-305,-766,-821,-170,-247,-737,-1000,-1000,-987,137,-46,87,699,-619,-576,-524,949,-302,732,1000,-1000,-1000,-244,836,255,-293,-1000,157,220,505,-803,918,-737,-777,-179,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{791,117,709,-1000,-325,1000,-436,-209,-873,-352,-1000,-1000,-461,701,1000,-1000,729,-312,1000,-1000,-1000,1000,211,600,561,53,1000,-91,-1000,-1000,287,-1000,192,596,-1000,710,252,-986,-980,-486,-580,-503,-263,-1000,-1000,318,-82,1000,-718,1000,613,-1000,1000,-313,-106,-964,810,-1000,-41,-573,-115,-843,1000,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{-1000,-583,-121,159,29,-101,-222,-788,475,-7,-52,439,-625,-137,-114,298,713,702,194,768,-764,80,873,899,-464,919,457,-1000,1000,-18,594,54,175,1000,451,244,-494,-13,-353,184,473,843,-516,974,-20,304,521,-152,-160,499,922,467,714,29,-899,-106,-450,13,489,-972,-219,-50,-725,178}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{195,-116,337,215,-636,993,117,-670,-357,-144,-556,-334,-754,295,1000,-664,44,122,618,-148,-611,722,552,166,710,730,1000,-1000,-27,-608,427,-817,-701,432,-275,614,149,-786,-1000,-740,482,-290,-458,-483,-885,-34,246,924,393,1000,245,-816,984,-745,-725,-772,-32,-720,285,-536,-1000,-132,324,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{77,1000,-821,1000,-246,189,-1000,245,1000,-1000,-60,-653,-93,-223,806,831,896,573,-436,121,-756,-771,884,-1000,207,1000,1000,-1000,-548,1000,517,1000,-329,-751,106,599,84,-1000,-1000,-1000,176,1000,432,-600,80,-562,-229,-496,-792,534,454,-1000,-1000,-842,998,-665,925,47,-147,-1000,1000,-661,500,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{30,-957,286,1000,633,-533,328,-1000,-1000,749,-33,-653,1000,-178,806,-424,146,930,1000,-1000,1000,1000,1000,607,1000,669,905,281,-1000,1000,964,-1000,1000,1000,-1000,-317,84,1000,1000,1000,997,-1000,-1000,1000,-1000,-1000,-22,824,-900,1000,-785,-1000,1000,1000,-1000,-219,605,890,-1000,921,-1000,-204,974,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{1000,503,1000,-634,578,-1000,-402,-1000,98,9,-91,-407,1000,-413,250,507,75,1000,1000,-24,-935,1000,648,347,192,1000,699,1000,1000,501,1000,-1000,-202,-232,-448,1000,223,-579,859,705,1000,-257,-1000,1000,-720,-914,1000,-1000,-1000,1000,-1000,443,-880,-741,-69,-1000,232,-663,-192,186,-154,-1000,474,865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{-363,723,-921,145,-1000,153,-66,825,0,665,597,249,-175,-689,-376,729,-34,331,143,-795,160,-453,-809,1000,813,-469,-942,-69,-708,813,1000,134,568,391,-1000,990,281,1000,255,-40,606,448,-275,-405,-1000,-66,-403,-443,-606,469,615,567,-120,-47,509,413,-841,-857,-1000,-516,185,-75,-678,72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{-276,-601,-57,308,-532,199,-131,-45,1000,-267,-624,-172,979,-1000,511,-95,1000,459,-1000,478,77,-1000,1000,106,332,944,-719,592,507,-1000,-172,-276,860,-765,193,1000,904,1000,-62,-94,1000,619,-450,110,-1000,-303,86,-1000,-483,220,-8,-285,667,542,-1000,-58,391,-796,1000,-70,115,-84,369,330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{178,130,377,-1000,643,701,333,-772,-305,34,155,133,-3,797,1000,-735,-970,-346,1000,775,-316,740,934,-206,-842,193,118,465,511,375,-694,-934,-147,463,967,-975,-66,-535,310,-218,-1000,-1000,280,1000,-717,1000,-482,512,-1000,933,-18,-82,635,-209,1000,-1000,-589,305,1000,140,-37,13,-91,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{51,213,507,1000,-580,-171,138,-976,321,164,-179,1000,900,625,471,139,598,386,-624,7,107,104,-150,-1000,-679,930,358,437,1000,1000,82,-513,357,1000,363,-216,-1000,145,-157,710,558,-76,-11,-603,-201,-404,-1000,394,-1000,-658,-641,589,331,-985,1000,166,-812,402,587,-628,-278,-211,206,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{-541,21,-283,92,1000,-689,873,329,234,166,656,-391,-909,-445,-206,547,777,894,-164,-542,-143,-316,-322,-775,118,1000,204,-305,218,309,88,8,329,1000,640,-1000,-898,27,-557,375,-99,-53,-42,-646,-413,545,131,824,296,-233,-552,-22,-153,-5,464,865,-644,-210,281,-959,51,623,-38,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{916,-233,1000,493,-364,-592,-786,-1000,152,-413,-334,914,-954,1000,743,-916,647,-124,-350,-1000,-1000,758,-409,-240,-386,127,1000,124,943,1000,335,-470,-416,805,60,-514,-790,1000,-90,-250,706,416,-101,-632,-251,659,-1000,726,221,-965,-714,659,1000,-920,-798,-907,-773,-469,528,-611,-220,640,-486,728}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{641,864,108,-382,-626,-42,-40,-687,548,-935,751,324,-23,690,-447,-959,100,136,61,496,-886,46,126,-262,-812,-38,-885,205,585,-150,523,9,139,610,-314,-196,210,911,-291,-592,-520,436,450,508,346,482,-700,401,-820,-1000,-984,972,-715,619,-398,912,-914,497,-108,-748,642,-584,292,-39}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{517,-339,-883,118,-17,-316,-533,-205,-773,-958,-365,727,-147,903,-679,-897,-550,-388,-719,-674,-628,619,-147,-584,504,320,-283,-827,770,138,15,97,251,-44,-949,996,270,-395,133,-21,-439,-439,-199,107,-269,-817,-293,347,-362,915,682,-309,-15,-312,171,283,-492,456,141,-741,416,-746,-200,594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-918,-497,-936,643,-248,449,-61,423,-733,-392,282,14,780,-770,967,529,-305,878,-431,-399,598,-991,133,45,-171,-476,-475,-198,358,69,-556,-390,-199,-394,-758,-237,836,434,56,108,103,206,-559,-34,725,756,-309,-920,997,558,332,881,-493,521,-96,-645,974,474,75,649,-568,167,357,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{594,1000,-379,-960,54,407,-1000,-681,1000,93,481,-1000,-824,-143,164,-191,1000,477,1000,978,-1000,-799,-804,-922,-233,-1000,-1000,-895,-846,-534,-221,-380,1000,1000,-648,-734,195,-783,234,238,-942,1000,-216,-539,-274,674,598,-555,528,-369,46,-546,-388,-893,-1000,647,-82,-865,-217,331,472,204,-980,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-554,475,49,-263,-186,-848,419,-947,644,-44,468,-838,47,353,75,174,-104,-111,895,-529,588,940,342,-743,595,428,-84,-646,430,969,-581,917,-162,-267,-331,645,445,-122,691,837,-86,851,37,239,-295,60,904,-40,975,-506,-20,-5,-176,676,634,852,174,50,-74,-381,-161,333,209,467}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-122,9,-609,59,-881,-569,-400,889,-421,24,1000,718,971,-1000,622,-1000,-226,1000,-1000,-595,-250,220,-224,-896,128,-73,-269,-1000,-439,1000,-1000,-1000,400,-1000,-1000,-802,255,313,-1000,-384,-1000,-55,-602,-1000,874,205,-875,972,-190,-471,-330,-521,-832,-1000,-885,1000,1000,1000,344,703,1000,-1000,-489,-546}));
    }
}
