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
        org.junit.Assert.assertEquals("java.lang.Double:MTAwMC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-1000,400,-1000,287,-871,9,1000,926,619,-974,639,-814,230,-1000,-20,-415,-63,789,-372,1000,428,-1000,1000,1000,359,565,599,232,-526,-881,400,194,1000,-503,-92,380,1000,-1000,365,-1000,-512,1000,202,-1000,897,779,1000,-142,475,-284,1000,-551,-248,1000,-126,744,-873,215,-1000,-359,1000,1000,-557,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-301,-645,662,510,-569,451,-661,-685,-793,-525,-56,543,-76,763,-409,300,-456,-185,933,106,203,509,-767,-926,-428,497,623,824,-21,307,306,465,-210,860,571,547,-790,598,-666,814,-765,-769,642,769,-146,226,-920,613,-278,602,328,946,560,314,288,761,-728,615,-903,753,-580,-912,145,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDhFOQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{958,-705,1000,301,534,208,-776,-304,-189,691,-555,202,798,46,-220,145,-171,-640,428,-743,663,546,-336,-811,-15,-4,-192,-184,878,902,-439,-13,-786,-299,-673,187,-59,118,-352,374,1000,-938,557,248,561,-759,-465,828,182,-244,-54,579,-158,-390,-1000,370,631,-46,532,718,300,-646,433,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-778,586,348,828,539,387,-317,1000,-863,19,-137,-1000,219,550,-1000,-779,598,-692,-1000,549,418,-129,185,-1000,-126,14,-124,-48,-607,367,587,-309,-843,-376,572,-44,-69,524,287,-404,1000,1000,762,-261,-134,913,-184,1000,217,-489,1000,944,-187,1000,24,612,-484,1000,57,842,513,886,309,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{457,-751,1000,-445,1000,-675,-367,1000,818,-709,513,736,-602,885,160,-256,583,-1000,944,-725,313,-234,1000,-170,897,101,-998,-1000,757,870,-366,-672,-786,1000,92,630,-673,-414,-354,553,1000,-1000,1000,248,-573,-759,-474,25,-123,1000,-362,1000,1000,-990,-1000,831,752,-1000,-868,1000,1000,-1000,1000,520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{748,-256,-1000,483,529,183,-628,1000,166,-652,-963,-1000,-274,-731,788,-525,-355,-338,-160,735,7,-72,586,-489,-31,119,328,602,-194,1000,1000,389,-56,-1000,1000,733,-1000,310,891,812,-1000,1000,1,-788,0,452,522,-257,128,-97,-28,-934,-478,537,1000,1000,363,-471,173,130,-1000,244,-773,915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{83,289,-319,1000,194,1000,-281,1000,-1000,-294,1000,44,-975,-1000,757,283,-1000,-305,1000,478,1000,164,861,-740,-1000,-248,54,-463,1000,-1000,721,-1000,-223,-1000,47,-692,-1000,74,684,1000,-472,1000,25,1000,1000,1000,-66,-137,1000,1000,-873,-1000,-1000,1000,1000,-696,-1000,1000,-846,-1000,-15,-536,-46,256}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-471,-285,1000,546,876,369,398,-367,-857,-497,43,483,469,-647,376,138,70,-1000,-832,-399,1000,761,1000,1000,1000,-110,-449,-1000,992,-357,-1000,-757,-146,-277,-152,152,1000,672,-507,-413,914,932,17,-740,284,-285,126,175,-1000,-285,-565,476,-318,-1000,-54,-541,430,1000,-17,-694,-536,267,-508,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-20,71,-1000,771,-135,79,7,131,570,-1000,-842,-858,415,-984,930,-529,-355,623,993,594,-226,-764,775,-1000,-107,-249,132,840,-770,350,1000,177,-775,-1000,265,663,-47,-341,747,800,-799,682,892,821,0,752,822,-348,-400,-141,451,-901,-390,710,414,556,-3,37,386,130,-50,-679,-207,749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{407,771,195,-575,956,-324,-776,-948,-132,1000,-489,-42,-279,-475,250,152,-677,750,1000,420,319,-474,-921,-330,174,-726,647,229,-522,1000,-1000,-355,-212,-529,-137,-335,1000,64,449,-1000,-536,-283,1000,447,366,1000,1000,930,288,-928,-951,-1000,527,-596,-346,819,572,-535,-224,-1000,496,-547,-991,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{-830,545,998,-788,-330,261,-6,670,348,-869,-602,-927,-973,-807,-207,-287,1000,637,775,271,-622,844,501,92,98,-459,33,377,-476,-735,97,-693,919,378,-313,-231,246,655,-229,-73,386,-190,58,-56,876,702,-1000,772,-1000,-605,664,952,813,-322,571,-286,310,1000,-357,461,1000,-121,-171,253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,357,324,-619,-1000,-557,79,843,-219,1000,-731,1000,-1,-75,-1000,368,817,320,-911,-33,-569,-525,-888,-372,-871,-502,67,952,444,-831,-1000,1000,-493,-862,-294,-141,-33,-212,509,435,-932,-493,-612,-873,27,-479,214,-1000,-1000,878,-489,-1000,-123,-94,1000,1000,823,-559,264,-378,-31,-619,-195,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-1000,1000,673,-1000,-391,64,-504,502,977,40,-1000,355,1000,-151,1000,1000,398,871,-793,-1000,809,234,-1000,824,1000,-772,1000,-1000,1000,-1000,1000,-571,-1000,1000,-683,-436,-670,-1000,1000,1000,-296,1000,-998,933,-935,-1000,-1000,-1000,1000,-1000,-342,-612,-882,362,-248,-1000,-501,-1000,1000,94,1000,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-390,-169,-1000,-7,766,-773,686,176,-667,204,-393,1000,-57,417,-447,102,211,97,-544,-838,-266,-537,-1000,842,-1000,-111,1000,-1000,59,-1000,630,-799,1000,-749,-627,140,-1000,-779,1000,-180,-687,1000,-513,-181,-1000,1000,-682,689,1000,1000,845,-1000,527,-358,-1000,594,-92,-1000,1000,596,677,-486,-991,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{32,1000,-830,1000,648,933,219,-141,-830,221,-66,208,215,-298,575,-89,-24,-315,172,715,-650,-810,345,-288,59,153,316,-24,-1000,-874,-13,47,845,1000,-748,489,209,324,-827,463,405,-439,-553,-49,364,-497,463,200,759,498,165,-583,-418,1000,261,-1000,191,38,1000,-193,1000,83,-1000,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{22,-591,-362,591,-430,764,552,514,419,-857,181,-53,-958,-938,-957,-785,594,786,-650,-651,-72,-29,-229,537,-425,-282,334,-768,-892,995,-470,-108,182,137,-94,682,590,588,939,645,-827,406,835,798,-890,-818,45,-546,211,-53,746,601,-812,-558,317,-342,553,-343,940,-897,-190,958,-189,-457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-750,663,-1000,1000,280,1000,-307,-1000,85,221,679,823,1000,-1000,757,-89,548,401,103,1000,-1000,-945,157,377,-445,-33,316,1000,-1000,-394,9,-410,1000,1000,-897,870,741,-517,-671,110,-253,11,-553,-28,-631,-1000,236,-294,1000,1000,165,-1000,-1000,720,190,-1000,664,-691,1000,-444,1000,1000,-1000,-347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-1000,-1000,662,-891,961,23,-1000,223,-121,1000,-1000,588,-1000,657,-86,-985,633,133,-67,24,1000,774,-65,-326,-1000,954,1000,-1000,-390,-890,-877,-1000,-1000,-1000,1000,-669,-763,-834,807,-1000,921,267,-1000,-409,1,750,559,831,-528,1000,207,-1000,58,-1000,-536,482,353,872,-1000,512,454,-147,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{740,-769,-446,-591,1000,259,-116,1000,174,248,164,-225,-37,193,505,-1000,-213,55,-1000,-235,483,253,841,-450,80,233,41,-743,916,-1000,117,437,-162,-1000,127,-179,-1000,1000,425,207,330,-903,-115,375,1000,-322,861,1000,-1000,130,-688,353,746,3,305,981,-763,1000,-1000,-1000,-444,-851,276,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{1000,-1000,138,468,-441,-431,611,-895,557,454,-808,1000,926,-1000,-419,805,75,384,90,-893,-227,18,1000,388,-23,-151,652,-153,406,726,278,195,62,-1000,835,-358,469,743,585,611,-838,1000,-18,775,1000,182,1000,547,812,-1000,1000,527,-662,1000,-1000,-190,-526,-1000,74,536,-985,-808,1000,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{202,433,-106,-433,-1000,657,-722,214,838,655,648,-625,1000,-528,1000,-746,1000,-631,-321,-189,-640,315,-647,938,-603,-171,-706,55,-186,742,-349,1000,-216,97,228,1000,-533,-325,-853,615,-580,570,1000,-333,1000,-970,74,-790,357,1000,1000,1000,506,1000,-802,-151,-400,434,-356,1000,827,-365,-984,-120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{592,-723,-1000,852,533,-142,898,685,-790,-771,961,-346,-1000,-153,-189,-871,123,1000,-320,-962,525,-80,-1000,1000,-981,-920,-384,678,-905,-385,336,-1000,512,888,-894,869,161,-1000,1000,-878,-183,-576,1000,637,291,-60,-231,-247,-148,795,-489,787,-366,-186,-269,-402,1000,1000,369,822,-589,-1000,-789,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-653,697,503,795,64,703,-381,1000,-1000,205,-776,-766,47,-873,571,-1000,744,-1000,-656,157,-14,904,-717,243,-254,-465,749,-1000,1000,-298,-541,142,-662,959,-564,1000,-896,-1000,-484,-1000,-1000,-597,-398,-130,-1000,-863,-594,-429,-1000,280,-1000,-124,-328,1000,-212,-243,-1000,1000,-274,508,1000,45,-614,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{286,-1000,-583,-71,339,1000,-35,574,646,1000,739,49,-495,-107,343,262,600,1000,1000,-1000,-383,-408,-863,-437,-1000,-446,1000,1000,-416,-849,290,-823,-743,858,86,1000,1000,132,1000,737,-597,331,1000,872,293,231,19,-107,635,178,654,-1000,-656,692,826,-1000,-398,927,437,-261,-1000,-1000,34,717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-58,-689,-594,-984,602,937,629,-99,-59,232,96,78,-761,-1000,-1000,1000,-637,-527,1000,91,-193,-21,-800,-777,-1000,-490,1000,1000,-494,419,-1000,-861,-1000,-283,-21,852,1000,31,152,1000,491,1000,1000,-1000,-1000,970,-330,38,-646,25,457,-1000,-930,1000,1000,-1000,-689,-1000,-139,-1000,440,-260,-869,791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{466,-57,1000,-114,354,-1000,29,1000,1000,400,971,-1000,661,1000,169,163,1000,-1000,992,-631,459,827,-508,-349,248,43,-718,412,539,408,758,-1000,1000,1000,1000,-749,596,-344,817,-604,-680,889,964,-499,-995,-576,80,-112,1000,278,527,428,948,-372,1000,-1000,528,-465,420,-906,-6,-697,618,-365}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{170,287,50,940,-130,-977,-881,-659,480,-706,-779,-341,-608,884,-858,538,384,-538,823,-963,581,-90,-363,379,-740,-975,-223,-811,-548,-573,845,-390,813,-16,953,-852,-449,-55,-438,-181,341,91,130,240,105,687,208,-321,273,802,-940,-260,759,645,99,-618,-787,-45,-517,245,-636,-781,-954,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{-974,-875,-890,-432,-139,84,103,960,-678,251,-555,310,-426,-471,538,-854,-425,66,509,-674,866,109,792,195,743,-300,784,337,-895,-183,982,115,744,125,-787,676,194,300,-494,-211,-627,81,-15,995,-801,-372,-263,-133,-861,575,-958,-108,-809,-777,566,475,-776,749,-826,862,760,988,872,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-580,-377,105,348,677,-1000,-806,1000,-1000,961,-1000,1000,319,509,-156,225,-1000,1000,1000,-728,-1000,1000,795,-40,763,1000,309,1000,57,614,-529,-88,1000,718,769,-734,-838,1000,-983,434,-597,-427,771,329,-177,-788,1000,-157,-105,1000,-280,-1000,287,1000,-318,444,-3,199,391,-1000,-393,-1000,-160,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-456,-269,-97,-1000,-687,679,854,107,-1000,-648,-683,1000,-632,1000,-749,1000,-104,1000,-804,18,-1000,717,1000,1000,326,1000,-392,-483,-1000,166,1000,-1000,282,-484,-110,-1000,168,1000,-546,596,-317,87,177,970,-804,-535,1000,1000,1000,-292,-903,53,-224,-1000,-1000,-739,1000,457,-866,167,1000,-783,999,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-694,-457,1000,48,375,-346,-726,-13,-81,-965,-180,-689,703,57,745,-864,-1000,-931,507,942,-1000,263,188,115,343,553,-1000,-161,-661,1000,725,-73,1000,-173,-476,-308,-322,-604,605,-121,-663,337,-1000,421,-242,1000,786,-390,333,1000,499,-1000,-1000,977,-353,-289,374,1000,724,315,-663,156,510,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{1000,726,149,486,-81,596,488,571,-220,-281,-610,542,-593,-542,-479,-819,-277,1000,785,-112,948,1000,1000,631,573,974,-694,-1000,-196,81,322,810,-1000,-411,-15,125,319,-1000,-1000,-460,-1000,210,492,106,211,-303,649,-759,-5,-445,1000,1000,169,-453,-961,291,-192,-456,320,-251,-777,-121,-163,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{1000,817,-1000,514,-605,-965,524,1000,-787,168,-910,1000,-1000,962,960,-876,517,1000,-1000,-195,-815,-1000,121,-398,11,918,1000,-1000,511,981,-187,-1000,-587,-1000,-587,108,89,-941,-1000,1000,-1000,-1000,-337,394,317,205,-1000,-1000,-65,-490,-528,1000,1000,-636,88,1000,-437,1000,130,-173,-843,-198,-761,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{1000,443,609,530,461,550,869,308,-183,-413,323,159,252,-394,-931,-674,379,-6,609,79,1000,756,1000,828,-49,710,-375,-271,-134,1000,561,-100,-841,-515,310,569,-1000,-700,-1000,758,-866,879,692,75,47,-1000,1000,398,-95,-527,835,914,268,-366,863,-298,310,-926,392,150,-126,193,-327,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{-285,-161,840,118,1000,-39,-501,-1000,-297,-584,-97,300,170,72,486,701,7,-1000,-51,146,338,225,164,42,435,-217,-35,-191,-261,1000,40,761,526,-382,-310,837,-542,571,330,-289,611,-249,610,869,105,922,-573,234,295,-144,-292,343,-717,-921,-1000,876,486,-331,650,148,-565,-463,153,224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{570,427,411,-58,-718,-713,469,-619,-1000,848,-160,540,1000,1000,414,-303,-1000,-106,-313,-1000,1000,977,369,-519,-287,23,370,268,-916,227,1000,1000,240,544,390,116,213,1000,1000,-64,682,-708,-684,-1000,-61,-251,-109,-1000,978,-552,1000,158,501,-645,-1000,468,45,-143,820,1000,665,414,-1000,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{213,429,554,853,-48,-748,-391,-28,-932,-620,-425,983,998,665,705,799,-624,-698,-169,-599,882,787,82,44,522,91,700,651,-593,161,913,619,876,-258,-122,667,-313,932,891,-654,915,-87,204,334,183,841,-870,-229,899,-980,385,510,-708,-839,34,416,206,-197,884,593,-152,-245,-364,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{-151,500,900,1000,-882,-1000,413,-449,-1000,74,-949,525,1000,1000,-186,-72,-1000,-653,-961,-1000,449,933,-267,-407,773,479,878,430,7,-333,225,-134,1000,236,-369,-250,417,1000,-332,-317,1000,379,-790,-60,542,248,-952,-1000,1000,-1000,27,-45,-104,476,-410,230,-701,158,869,583,571,662,-1000,483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{1000,-487,285,-195,1000,256,154,33,-627,-258,-457,209,998,464,594,701,90,-1000,627,-145,612,225,787,42,-420,-615,-609,1000,-1000,1000,939,761,-168,61,108,1000,-320,514,1000,-46,-326,-249,154,869,105,947,-55,234,103,-432,481,684,-567,-1000,-304,746,-163,-736,784,379,213,208,-555,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(double):org.apache.commons.math.complex.Complex",
            new int[]{233,-256,98,-451,-657,-93,443,-223,29,353,-420,-825,-195,955,-999,-291,-814,642,954,-998,-334,475,380,-208,870,449,-327,-558,652,-410,-573,-304,707,-178,-581,-718,146,478,-784,946,377,373,-999,21,653,-121,483,-267,674,610,-917,-884,745,979,-26,606,-815,172,24,200,879,866,-801,-634}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1,739,-676,231,943,321,-167,659,-997,-943,474,-470,908,74,909,742,897,964,681,-701,116,-628,-856,-501,-802,65,625,923,-825,-293,431,-183,-354,-181,-916,-999,-519,-282,-410,-997,236,-451,-555,752,-55,998,-382,-115,969,-288,795,487,181,-955,801,-101,935,-687,302,140,-18,475,655,-563}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{842,1000,-845,-1000,-22,-1000,-532,451,1000,235,-82,273,536,1000,615,341,821,240,543,879,117,-456,538,-206,-1000,-1000,541,-216,288,1,-1000,-614,673,69,1000,-996,-1000,-367,-550,-890,167,493,102,689,408,878,-1000,-824,635,-590,663,273,73,-1000,958,-282,-467,269,177,1000,222,686,964,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{390,-59,256,-594,820,415,-261,-412,526,-419,-282,-409,671,-295,-232,-766,-169,1000,196,-345,198,1000,-649,804,-1000,460,-1000,938,-202,-335,486,479,-475,849,404,-236,633,-953,-761,-817,-61,-684,-714,-217,612,58,-1000,368,-97,1000,-378,-814,807,-561,-877,-783,-783,-169,15,-764,-368,-104,75,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{160,-1000,-676,231,-56,1000,-968,-193,-997,-943,474,676,1000,-1000,413,623,-487,-1000,424,-701,116,1000,-856,91,-675,-603,-457,177,-738,-554,-567,1000,181,-313,-946,1000,185,360,-375,-646,-425,69,-1000,302,-299,998,-162,1000,97,-288,26,13,813,-575,-719,-564,580,589,948,-1000,244,376,680,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-204,921,-600,-225,721,-739,-493,380,354,811,143,-217,-470,362,917,-951,654,797,36,-385,-408,-462,915,627,-107,-561,957,70,546,915,-855,866,260,366,934,622,-395,-780,262,-713,-727,308,-503,628,151,-318,583,-866,979,510,745,-280,-71,-820,-257,-547,-405,409,819,383,653,-212,288,551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{625,-1000,608,-1000,903,1000,-701,-161,-135,526,-778,293,923,-673,544,-57,-225,-713,1000,257,-785,1000,561,808,-624,-605,183,-913,-1000,-1000,-589,376,1000,398,801,648,986,-36,-18,-336,29,-564,-1000,153,-141,-406,-901,1000,1000,1000,-94,470,1000,915,-1000,-429,-1000,1000,19,-1000,64,-155,601,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{335,-701,245,393,123,-333,-747,-79,-885,831,-7,279,259,-1000,-1000,1000,513,365,-626,469,-772,-1000,-780,1000,-1000,-1000,-778,-325,-427,-1000,1000,-894,468,722,-525,-1000,632,531,-333,-237,1000,-707,444,67,-599,-1000,-627,-1000,-7,35,497,173,-1000,706,346,545,740,-643,410,-127,-798,-788,-1000,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{755,-607,93,-803,-714,283,144,-32,-832,771,-3,-707,-47,46,-769,-632,-172,-452,-944,-119,500,-529,-741,-147,-832,-78,-500,-126,-112,-972,-373,194,-961,832,-396,-568,-497,495,110,-844,-302,991,-95,-997,658,277,-348,-374,986,737,39,312,-316,-407,767,816,638,-60,270,691,-183,-946,-157,179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{1000,29,-1000,291,186,-1000,-162,1000,-509,834,-233,1000,293,149,-1000,649,-521,-586,-1000,1000,273,139,-518,554,-385,1000,28,580,-616,-611,-242,-366,663,-497,-804,-235,964,1000,-407,1000,1000,-706,96,-700,-713,-1000,567,-1000,681,658,478,-73,-483,-43,-204,1000,-828,624,1000,604,-119,433,-770,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{12,-1000,1000,41,-396,125,1000,-58,-737,1000,803,-586,-197,-325,638,469,1000,-773,-97,81,-329,-397,874,440,-1000,-1000,1000,-1000,1000,-1000,235,-537,156,194,-750,577,-1000,20,-134,-963,699,30,40,1000,-462,1000,-735,-664,744,-485,-391,-260,1000,-554,1000,354,1000,400,-497,-494,1000,-8,1000,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{-94,969,-473,-675,-153,643,-226,962,-586,-344,929,822,38,147,457,947,-237,722,-788,906,748,48,-288,-887,-482,927,800,-676,-864,852,595,755,910,553,289,-701,692,-901,156,885,-594,756,-69,223,-943,998,645,862,110,789,-70,-205,-80,-390,-158,-317,632,554,-682,173,884,-743,537,766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{526,197,-1000,191,731,-436,-46,-782,-386,-773,-431,-1000,-510,1000,-528,-1000,714,-845,-704,-354,-1000,269,1000,868,-713,-1000,1000,1000,-1000,-789,-149,-946,-239,204,1000,-1000,-926,-1000,-835,-1000,280,1000,490,-133,545,980,1000,-824,900,-870,534,-884,917,1000,583,246,914,-618,-768,733,-508,1000,150,280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{-353,-1000,238,-647,-246,1000,278,343,663,-541,1000,739,422,-515,55,105,-878,-1000,-851,32,-758,-33,-1000,73,-259,-1000,60,910,633,-398,-1000,1000,135,202,-508,-383,-530,-367,713,-922,-394,897,1000,-907,-827,-433,-835,-772,1000,92,-657,-447,-565,456,-503,-440,-821,-380,418,-395,60,-715,-282,285}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{-891,-385,1000,-362,606,7,-233,-447,663,-541,-104,-574,-1000,-200,55,105,-602,417,-540,32,-515,-400,370,400,-197,360,904,539,-1000,-398,190,1000,135,1000,534,-433,40,626,109,-589,-800,443,1000,-622,-1000,585,-835,673,1000,-976,400,-447,351,241,-118,1000,1000,-596,424,790,60,191,1000,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-461,-708,386,-30,438,-32,-452,-1000,431,-41,83,311,-220,-425,213,-467,-30,671,543,-951,252,-796,-2,-230,35,-570,261,280,1000,462,35,-160,233,-576,674,-732,-1000,-147,-192,-134,993,-683,1000,-1000,281,-2,1000,-193,211,-525,610,-364,-957,-662,641,-195,-120,1000,173,906,-696,-332,-717,818}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-430,-365,719,1000,790,631,-741,-244,481,429,143,771,408,-1000,-705,-677,1000,462,115,-1000,499,-416,25,-283,240,-1000,149,-301,1000,306,440,-723,-183,-383,449,-1000,-1000,-725,-730,-302,1000,-834,1000,-1000,-12,340,773,1000,-312,-903,478,-213,-1000,-623,972,-238,-420,1000,1000,375,-1000,-462,-1000,761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{-819,-708,-1000,608,749,179,371,400,1000,-478,489,-1000,-304,-713,-1000,352,-30,137,1000,210,-230,-796,172,247,86,-182,-982,280,-109,703,35,443,233,-404,-336,-585,803,1000,-1000,-787,-260,-683,705,1000,-260,-616,359,1000,-79,313,610,-1000,1000,-321,-165,889,-186,-1000,-949,34,-1000,1000,389,176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{635,1000,591,-459,-311,1000,419,-881,701,702,1000,518,657,-43,993,745,-1000,-353,-1000,-1000,326,-286,-203,31,294,-173,-979,269,753,-492,-1000,-647,-1000,969,438,626,-176,307,-110,1000,1000,172,-343,203,28,748,497,1000,-591,-866,-308,-764,777,-1000,-635,623,74,876,-620,569,-1000,-351,-271,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{61,523,-1000,650,-423,391,269,-815,-843,-362,-19,1000,-711,140,518,938,147,-285,249,-846,-733,-170,1000,389,883,-568,-362,293,1000,695,-669,-313,1000,-91,87,565,-892,-599,-74,283,160,1000,619,823,-105,955,-524,-144,1000,-550,232,-408,621,204,-639,152,398,231,-508,-291,-817,-508,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{142,354,1000,868,-86,26,998,-361,-351,-1000,-548,760,-69,-1000,-921,511,321,-561,286,862,780,-1000,-236,-464,634,1000,-916,1000,1000,863,242,286,-505,-445,157,272,-841,-1000,377,1000,-1000,-560,-1000,38,-669,-529,76,-222,-769,724,-310,465,1000,-352,-459,-253,223,791,369,84,1000,-779,905,110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{303,-739,683,14,-125,979,-241,-389,-355,29,-625,547,1000,-1000,-671,-145,796,-408,-605,-1000,1000,-664,-936,1000,1000,297,-874,319,-42,290,438,-481,1000,-874,429,-1000,-792,-590,952,1000,385,-38,-1000,-251,-1000,-1000,216,328,-600,-96,584,290,1000,497,462,-500,-981,1000,784,1000,-785,-490,1000,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc2RTE4", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{-803,-554,813,-126,-486,-391,992,763,1000,-714,610,506,68,-646,-509,-429,825,-1000,213,-827,-1000,-1000,210,-271,114,1000,-885,735,1000,408,-892,279,166,914,210,880,-738,-64,-62,1000,203,577,-922,796,-538,-812,-586,-1000,-797,-779,-492,-533,1000,960,26,-257,124,-394,573,346,-1000,514,627,111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{1000,767,228,-56,702,440,-601,-437,-136,117,-438,-138,965,1000,199,720,1000,-881,-330,474,199,-108,172,1000,-1000,-746,1000,117,-56,-294,144,930,-288,917,-670,-289,1000,-1000,1000,1000,537,165,899,-684,1000,-1000,992,-901,501,768,1000,424,-1000,-1000,69,145,921,725,1000,1000,1000,199,1000,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{280,-245,501,365,-238,-413,413,374,-569,-676,294,-566,34,79,1000,-262,211,1000,-342,-1000,1000,1000,-37,524,-589,-1000,768,-1000,386,-380,-68,320,-922,254,779,-248,102,-604,199,-802,555,1000,-530,-622,-646,980,512,-315,303,428,495,529,762,1000,-1000,-867,296,221,-445,1000,114,-972,-952,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{1000,486,254,1000,-156,-891,625,1000,-138,248,463,-558,599,-893,514,857,-192,223,-349,-1000,-489,-209,-1000,-1000,-109,604,-563,267,78,1000,-27,470,-415,-98,817,-429,80,-532,1000,150,-85,-1000,-802,69,-340,874,499,172,-1000,-1000,-860,-563,75,-362,-68,61,-1000,-477,-1000,-50,122,-140,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{-400,479,1000,1000,97,-185,-496,1000,438,124,-730,-1000,295,-577,-923,315,70,-211,-1000,-754,121,563,-1000,-81,1000,1000,1000,506,876,1000,-1000,360,-707,1000,137,1000,97,-1000,1000,-355,-857,-362,-1000,270,-529,291,-1000,370,400,1000,400,156,-268,-384,792,168,-1000,1000,166,-932,-292,548,559,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{421,-349,-1000,375,425,666,704,1000,28,810,143,-226,1000,9,-1000,1000,-677,130,-130,369,38,885,1000,137,1000,-1000,270,310,841,-1000,-1000,625,-1000,-57,-310,-643,268,-909,-318,-98,189,297,806,225,258,1000,682,-619,1000,213,-718,35,-177,-87,56,1000,307,1000,767,-1000,-793,-411,-535,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{483,99,-1000,-357,435,511,-371,1000,-1000,-742,-725,390,850,-307,383,1000,988,135,-279,297,-354,8,691,322,1000,895,150,762,163,426,-188,1000,-397,349,-357,30,-294,-676,-674,-1000,1000,1000,167,1000,-593,-125,-703,361,681,459,-794,-639,-70,-1000,-500,-137,-24,267,385,-719,1000,-1000,-876,183}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{100,-356,-597,162,-112,-516,98,-363,39,-402,248,187,647,633,1000,-46,1000,11,-421,-298,843,35,-757,-585,-778,-444,1000,-245,-419,1000,-464,-203,140,-139,-464,1000,507,-403,-227,-1000,2,506,-529,920,-992,236,-1000,2,-956,-476,-329,-1000,-353,405,-138,-966,-751,272,97,1000,1000,128,-606,129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{1000,656,174,-636,152,303,208,-277,476,-1000,-717,-852,919,-1000,-719,892,-41,1000,421,1000,-1000,-211,-405,1000,831,716,633,1000,76,-424,1000,1000,-542,-737,38,-1000,-685,-933,-1000,-227,940,663,1000,245,923,-981,648,1000,945,384,-613,-595,-176,-1000,664,113,-656,824,319,-772,-244,-1000,-80,-17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{817,459,-577,-705,-509,-316,588,262,-286,-90,917,-708,-494,456,183,159,-620,202,-464,-238,237,-7,584,-139,-332,8,744,-734,-78,-34,-318,-530,-927,51,504,515,802,532,774,756,-314,167,400,90,-175,-275,-23,-778,-737,-801,241,-422,752,-387,987,-410,-899,744,-14,-746,616,500,-752,91}));
    }
}
