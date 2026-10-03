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
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-47,-85,-1000,-1000,-270,-1000,889,643,-1000,-137,626,396,-1000,-1000,-753,673,-1000,-445,400,1000,784,607,-747,-479,-393,1000,-1000,656,198,428,-673,-898,-935,-428,-880,-600,395,-26,1000,-650,-777,1000,1000,1000,-462,-884,772,93,-627,270,-322,-355,-940,1000,617,996,-620,1000,229,1000,-1000,-336,76,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{972,-969,1000,1000,-1000,-281,217,-68,-147,794,-1000,-940,-433,-648,-412,-469,-201,674,-1000,-1000,679,1000,1000,604,1000,-449,1000,1000,25,-1000,1000,202,1000,72,1000,1000,698,286,-933,77,-69,-1000,1000,-1000,-446,1000,-277,1000,836,-652,-1000,-1000,832,-1000,-940,-78,-141,-1000,-94,-1000,1000,-120,1000,-443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{324,-129,-519,-1000,728,-69,-236,-443,-15,239,320,553,883,-785,1000,1000,-201,-866,-146,838,28,-887,-486,1000,-1000,461,-1000,-571,-418,183,-1000,232,-1000,461,-770,-237,180,908,984,949,1000,454,701,754,-847,905,205,-1000,-692,304,320,566,-1000,856,1000,63,-856,-156,-1000,1000,-882,-590,155,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-47,-816,-1000,-1000,-279,780,-47,-1000,-1000,-137,626,-742,1000,-463,694,673,521,-1000,400,64,-1000,-240,-822,-479,-793,-326,-691,-379,-403,526,-784,-898,-58,-561,-684,-600,-362,1000,1000,-754,890,1000,819,28,312,-748,68,-1000,179,-1000,911,-1000,-187,1000,617,-707,-68,356,-497,309,-844,276,397,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-280,-502,-915,-858,-931,745,514,-1000,-1000,-1000,821,-835,925,-169,998,-598,-529,-1000,-1000,781,-631,-10,533,-503,-1000,-161,-484,258,170,1000,297,-1000,665,-1000,-828,-1000,-753,1000,-453,-1000,192,827,1000,592,-434,-616,991,-524,144,-664,313,-227,472,453,766,-322,-1000,43,-303,-5,-890,712,-1000,259}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{434,-915,-1000,-1000,-1000,-779,810,-1000,-1000,-1000,174,-1000,1000,-410,1000,-1000,1000,-1000,999,1000,-298,514,-984,-909,-966,-164,-276,626,365,268,35,61,442,-1000,-617,375,-580,1000,1000,-887,567,1000,1000,191,-477,-137,50,-1000,1000,-111,96,-1000,99,1000,-722,154,555,50,-499,434,-1000,360,545,394}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.DimensionMismatchException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{398,-652,-204,-1000,-171,-60,-176,-574,-64,-93,0,405,-14,-788,1000,494,901,-1000,-146,683,129,-129,-209,1000,242,649,-817,52,-444,183,-851,-110,-888,127,-666,-11,517,642,712,780,619,454,701,754,-633,-37,464,-718,75,87,320,-220,-952,653,567,115,-499,-171,-864,1000,-882,-441,578,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{-682,-1000,1000,-1000,221,-1000,540,585,431,-700,283,-586,1000,36,1000,-1000,-1000,1000,229,-599,-320,1000,-516,714,802,-800,838,-1000,-762,1000,710,392,489,794,-968,-557,729,28,987,-199,210,685,-914,-670,-1000,-280,-319,1000,-1000,673,-1000,-374,-581,-631,-381,541,467,-1000,793,959,632,986,-1000,-844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{741,-508,940,343,-358,179,115,-64,341,-31,-880,-130,483,-763,395,-557,-746,968,-490,-159,380,-439,797,102,575,-158,-96,429,661,624,986,259,41,842,-153,590,-600,-686,-292,442,-427,-568,-514,67,370,-242,-524,542,-203,408,149,694,-389,-659,411,3,-575,247,124,39,695,596,-56,-692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{-37,-1000,1000,-1000,16,-631,379,653,687,1000,930,-976,1000,-1000,192,-1000,-586,-80,-122,375,913,1000,-186,1000,687,44,910,-372,-215,529,1000,-655,342,354,-1000,-376,-1000,1000,215,188,-292,137,-1000,297,-990,-947,-1000,121,-896,460,-1000,897,-29,83,-618,1000,1000,-939,578,-723,1000,1000,-294,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{-439,-1000,1000,-1000,546,1000,1000,171,-1000,1000,1000,-692,1000,-1000,1000,-1000,-1000,695,-1000,207,555,1000,-305,457,723,-59,788,-1000,-1000,311,1000,-1000,499,500,-1000,1000,288,-808,788,1000,-1000,-1000,-863,958,-811,-583,641,1000,-1000,887,-1000,72,-1000,-1000,-861,742,651,-1000,-993,724,1000,1000,-806,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{321,-775,250,-921,-533,-1000,-934,1000,1000,-741,-624,232,805,483,289,-1000,-857,1000,1000,-888,220,682,-1000,686,-5,-825,1000,-174,1000,79,22,1000,1000,915,215,-1000,77,1000,1000,-1000,612,1000,-525,-1000,1000,-340,-431,273,-148,1000,-20,58,350,1000,211,822,100,-859,1000,472,1000,1000,-610,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{-661,-9,202,-573,-88,400,375,261,-465,753,593,-859,964,-728,-846,-245,341,39,-834,-239,490,580,533,767,-1000,884,-275,-655,184,268,438,-599,-281,216,286,60,1000,-643,-123,379,-491,-129,627,141,-934,-77,-444,-52,-577,275,-984,-1000,1000,485,-901,617,489,-800,160,-947,510,844,-650,313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "add(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.RealVector",
            new int[]{-1000,-1000,986,-1000,-216,-400,706,-176,84,-373,660,-479,1000,-1000,1000,-1000,428,-965,-324,496,758,1000,176,561,-531,423,851,-27,-620,261,958,-250,689,-442,-1000,401,1000,-80,1000,174,-789,-207,-976,-341,190,-148,-1000,400,-1000,950,-1000,1000,466,-1000,-1000,1000,1000,-1000,-400,-1000,474,1000,-1000,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-349,-280,1000,-766,409,-363,1000,-7,-673,-797,533,445,-126,-946,-1000,515,74,-802,-483,-1000,-1000,822,-258,-1000,-129,1000,292,-24,-205,-26,473,-415,1000,869,-681,806,64,-590,-928,-404,956,315,-963,390,407,47,868,-1000,739,675,-147,-626,820,880,-945,89,-358,20,-985,994,-165,396,-1000,502}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{544,373,429,836,1000,35,-148,-1000,-97,-489,255,-218,-58,-45,-515,-180,684,-520,-235,-220,882,575,6,1000,-6,-646,321,1000,-391,264,-610,-362,-58,-909,-19,-495,-70,7,-302,-125,224,-520,-1000,932,901,-2,582,-397,244,423,1000,-107,422,-244,-403,252,-322,-314,1000,574,-75,1000,96,148}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{560,-478,1000,40,-868,552,1000,952,-1000,-1000,732,1000,-1000,-528,-784,1000,122,-588,-97,-1000,-413,1000,-709,-1000,-254,1000,426,-710,-1000,1000,606,-456,828,642,526,-402,-797,-1000,-1000,-781,109,-844,-1000,96,1000,-343,1000,-1000,19,490,-1000,-1000,1000,1000,-1000,1000,131,-576,-623,708,707,-766,-1000,544}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-1000,-792,-166,-1000,1000,-1000,-79,-1000,935,43,-1000,-933,1000,-961,-1000,1000,-307,-1000,1000,-1000,-1000,804,-504,-988,-6,875,140,1000,-391,-1000,-610,697,-36,1000,232,726,816,110,-810,1000,1000,1000,-1000,33,901,609,101,522,-20,-338,1000,136,948,145,531,-603,-322,1000,-1000,-223,798,342,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-416,-230,49,-365,-50,204,1000,-1000,-269,-52,-1000,645,-1000,-391,-1000,-127,-378,-1000,604,-1000,-770,313,-1000,-1000,-443,809,597,295,-688,1000,21,-18,1000,-299,-381,335,787,368,-596,-935,885,184,-1000,747,1000,1000,-489,16,-874,60,1000,-770,81,1000,-206,1000,-943,1000,-1000,722,1000,601,-713,790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-789,370,51,-373,-985,107,-36,-1000,-511,151,-1000,157,-1000,-60,-120,260,627,-1000,195,-881,125,-965,271,-1000,-63,249,719,-491,-400,1000,-511,312,1000,149,-362,1000,52,-283,343,-26,-171,-52,-486,-611,527,1000,19,-800,-560,-284,1000,70,-1000,-286,759,594,-318,18,-20,1000,195,204,-83,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double[]):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{1000,-915,-430,134,356,-736,128,-1000,-525,-406,-1000,-778,-1000,0,1000,-1000,1000,7,539,1000,603,573,-1000,-625,697,906,-291,-664,68,1000,630,-714,-816,1000,213,-1000,924,579,281,-783,584,-1000,956,367,556,-1000,294,-738,1000,-660,87,-121,-714,306,-1000,1000,1000,-300,-1000,-455,-1000,570,286,-43}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double[]):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-86,-731,891,-292,-437,-32,603,-883,697,-1000,-1000,-331,-877,1000,-143,-334,53,550,726,841,808,317,-447,222,-158,-593,-1000,388,-212,-267,-216,580,-1000,-591,345,-108,-570,457,-756,-272,811,409,295,-242,-1000,-818,741,-1000,157,-642,-1000,-1000,-297,-1000,-844,-118,523,-880,693,607,125,934,-580,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double[]):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-1000,-1000,877,41,-640,-686,568,1000,234,551,-1000,654,658,-103,-1000,-499,-1000,373,-1000,-263,-127,-315,614,-450,1000,1000,-157,450,-671,-201,-153,-714,1000,1000,1000,-192,-802,-1000,-417,-827,-45,1000,163,-783,-1000,-1000,-231,-1000,730,1000,781,-460,189,-700,-1000,-14,117,-1000,527,-249,293,-953,-351,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double[]):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-228,-377,-966,-327,-66,493,738,51,-426,123,47,2,-1000,-363,836,205,1000,445,-669,-254,-175,1000,-30,186,-53,-230,-633,-433,-35,677,728,-187,400,227,135,-595,-504,940,1000,-272,-828,-163,632,-567,-120,1000,-149,199,156,-11,-476,-536,-636,-95,-708,-39,-1000,-489,-138,681,-9,1000,400,799}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double[]):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-288,-731,311,-1000,-37,330,-439,-728,302,-853,-1000,-77,-614,650,-325,-302,-43,685,-87,610,20,215,-812,240,-244,-1000,-835,230,35,-173,60,400,-680,-199,68,327,-650,766,-256,-418,539,409,-118,677,-812,-400,320,-1000,-277,-369,-137,-1000,-556,-1000,-1000,-118,400,-521,397,584,1000,949,-400,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(double[]):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-1000,-1000,335,-1000,894,56,1000,1000,-343,1000,-1000,1000,-143,-331,-1000,-512,-545,-349,-1000,-1000,-517,-730,1000,-1000,787,674,501,1000,503,-775,1000,-1000,1000,1000,1000,1000,-518,-1000,-322,-1000,518,1000,-532,-987,598,-585,-1000,934,306,1000,580,-928,-452,-1000,-1000,-153,550,459,788,-1000,258,181,1000,383}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-27,-1000,1000,-1000,-1000,196,790,1000,254,-511,-1000,-436,-917,465,-199,-1000,-1000,1000,764,572,1000,1000,-65,-839,-838,-1000,947,965,162,164,457,-872,-363,-1000,400,114,-308,-595,731,-1000,362,-760,1000,663,-202,-354,1000,-295,-1000,-12,64,-423,66,-20,594,680,1000,-716,422,-235,203,675,-649,824}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{829,-478,665,-627,-749,631,326,986,-32,-299,-770,-223,-590,291,-590,-501,-26,710,65,157,412,889,-108,-594,-803,-177,729,587,276,166,350,186,528,-373,505,46,-741,-684,498,757,79,-628,996,301,647,-142,-891,264,-71,170,248,689,-149,-972,790,10,540,-511,168,399,-317,-639,-392,-655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.OutOfRangeException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-485,285,-398,-964,80,-456,-758,138,-383,-583,555,-629,613,-345,-136,-315,-414,-13,-154,-831,225,17,-840,-340,149,120,129,-445,930,202,610,-135,956,187,327,-898,-284,670,288,-351,90,283,169,-28,406,26,979,-796,125,62,767,-546,322,-714,-355,505,368,363,977,-496,-367,855,249,703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-1000,-667,817,-1000,-585,-446,913,1000,166,316,-1000,-340,-661,24,-1000,-584,-744,1000,1000,354,1000,18,-65,-958,-994,-784,928,516,-85,815,448,344,382,-561,-675,-553,-14,-1000,731,-670,-412,-620,1000,984,-365,-471,297,-1000,-1000,935,231,-748,-1000,-1000,-541,814,1000,-580,-3,-193,243,1000,-476,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.OpenMapRealVector", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-272,-20,481,848,224,-221,-677,-1000,-1000,-91,1000,-207,-704,-498,113,50,-94,954,-1000,190,376,-166,-1000,923,102,264,-92,669,42,184,902,101,107,-39,2,-714,-745,-842,355,259,245,-511,-677,1000,-1000,1000,593,36,-1000,-841,1000,337,-81,1000,114,-1000,-144,-516,-968,-98,522,462,-381,-399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.OpenMapRealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{1000,-1000,1000,402,-1000,-434,397,846,309,-1000,220,933,-1000,291,-1000,-1000,-416,797,-656,168,-517,374,-375,365,31,-319,1000,-184,-249,1000,969,-677,73,-1000,-147,985,573,37,691,631,1000,-502,1000,-168,153,201,220,1000,-311,-82,226,782,443,115,-525,-268,-238,-146,-876,33,764,-273,-847,-466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-722,641,1000,-346,-1000,-1000,1000,-1000,-1000,721,1000,437,-1000,-758,1000,-236,-1000,212,132,1000,188,1000,1000,317,-1000,1000,-508,-30,1000,-1000,-1000,-1000,-518,994,-1000,1000,700,980,346,212,1000,1000,1000,960,-1000,-678,-541,866,1000,1000,-1000,-1000,699,-1000,1000,-1000,-1000,-90,-437,-357,1000,91,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{63,-152,316,-1000,-556,-298,380,411,-847,-69,-1000,186,338,894,347,29,859,234,-353,423,-647,-604,817,916,203,-165,-795,-131,282,-288,1000,443,451,152,-723,88,925,740,-201,1000,701,-1000,243,-757,-929,-534,782,-243,-992,944,-911,-1000,-536,-946,635,-163,-191,-724,-1000,1000,-954,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-1000,-409,1000,-748,-849,-269,158,-1000,-953,403,263,1000,-801,701,-1000,-920,691,1000,-262,1000,370,739,1000,-767,-1000,464,349,-541,5,-112,763,270,1000,1000,-1000,-88,-199,144,1000,1000,1000,-285,713,-1000,-31,-1000,691,-988,992,-953,349,-711,1000,-1000,246,-361,-565,-748,-943,757,-338,417,-406,411}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-322,1000,498,-208,-41,-450,-467,-1000,-456,1000,119,-133,1000,-348,349,354,-1000,468,804,242,-578,1000,1000,597,-375,1000,-362,-795,423,-1000,-88,-55,-6,-55,-995,-940,359,487,-133,362,-69,1000,1000,358,-1000,-831,1000,-216,-190,454,1000,-791,451,-73,954,145,-1000,-561,471,-196,1000,-687,214,-33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{-890,-770,358,-230,41,-569,-267,-960,-792,-701,957,-527,531,-857,128,-56,-37,-965,-527,-228,448,-663,984,805,-635,930,480,-162,-749,504,-628,-639,968,-432,793,216,-642,181,-313,-626,923,-152,961,-240,-909,507,-136,815,-776,78,380,314,686,-348,626,-142,-999,-908,976,398,-566,525,966,194}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.linear.OpenMapRealVector", "org.apache.commons.math.linear.OpenMapRealVector", "append(org.apache.commons.math.linear.RealVector):org.apache.commons.math.linear.OpenMapRealVector",
            new int[]{229,958,1000,429,-1000,41,654,-1000,-327,318,246,404,-1000,-1000,-660,-1000,-39,-1000,-1000,1000,640,1000,1000,1000,-1000,1000,-464,1000,1000,-711,-1000,-128,-773,-565,-1000,1000,1000,1000,-377,248,1000,1000,1000,1000,-1000,-634,-771,1000,-1000,1000,-1000,-1000,1000,-1000,745,-1000,-1000,815,-796,-97,882,-207,1000,-366}));
    }
}
