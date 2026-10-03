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
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDhFOQ==", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "abs():double",
            new int[]{1000,-1000,-1000,4,-402,249,-19,-157,1000,50,-1000,952,-701,-482,498,-1000,-905,1000,1000,287,-218,-688,-304,782,823,978,-288,379,-179,-915,1000,-1000,976,-1000,1000,652,1000,98,827,1000,-548,-631,1000,435,579,672,-225,272,340,-1000,176,764,284,-1000,-522,961,1000,128,-390,-371,745,1000,-1000,769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "abs():double",
            new int[]{108,-259,1000,114,303,163,-762,504,-272,-67,631,-686,938,1000,-316,-58,-118,354,-543,-555,-266,-616,1000,996,1000,-481,182,1000,-579,-46,433,-1000,1000,59,822,-263,-949,88,909,-1000,743,1000,51,938,-1000,-847,607,23,-798,487,-108,-1000,12,-536,940,158,-398,-339,671,73,1000,478,1000,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "abs():double",
            new int[]{775,-1000,-860,-132,-431,500,961,-1000,637,903,-1000,952,-1000,-927,-83,-547,-905,823,657,-111,-283,-688,-304,785,823,1000,320,372,-1000,-1000,1000,-1000,976,132,170,1000,1000,492,965,1000,-1000,-544,568,-162,687,1000,-282,632,572,-597,423,764,807,-1000,-395,1000,986,874,-992,-219,360,1000,-945,557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:NTcxLjA=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "abs():double",
            new int[]{124,-315,760,300,571,-505,-866,-861,1000,-1000,534,-1000,314,-977,-769,891,-482,1000,712,793,-1000,-1000,1000,1000,1000,-785,214,-349,-665,346,1000,1000,-218,-459,-164,-1000,-538,-665,-1000,21,1000,909,1000,317,710,-609,432,27,-1000,1000,-446,-1000,430,-868,1000,967,-476,-787,1000,-1000,1000,-905,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "abs():double",
            new int[]{1000,-645,-787,1000,190,884,494,-489,-481,796,242,419,-821,727,215,-978,-1000,1000,1000,436,-1000,-397,87,847,876,1000,-336,814,-38,-283,686,42,1000,-1000,1000,355,247,-244,-3,-465,-232,-170,1000,1000,-1000,369,124,660,-1000,-10,261,-762,1000,-1000,-398,1000,-398,958,-559,266,-1000,581,-427,745}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "acos():org.apache.commons.math3.complex.Complex",
            new int[]{274,-1000,-1000,219,-161,-704,599,-497,-35,-12,-268,-365,-18,-1000,20,157,20,1000,-1000,-107,-344,-217,709,-689,-1000,721,1000,-400,-650,-692,-387,-41,1000,797,-400,20,-20,-1000,20,-400,1000,1000,897,-1000,-792,-1000,197,445,898,996,187,-135,-183,47,-20,-1000,-379,20,337,417,-719,-1000,-1000,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "acos():org.apache.commons.math3.complex.Complex",
            new int[]{-1000,743,-400,134,-326,489,-352,-448,-340,-638,756,-87,666,-473,435,-334,250,943,-580,168,355,148,4,-602,-226,400,-275,539,-321,-434,-517,-449,1000,611,-1000,672,-525,-498,817,-205,-547,476,261,-454,-663,-88,-174,343,462,1000,-541,-741,325,-977,-1000,-630,400,664,-262,-936,-422,326,-857,299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "acos():org.apache.commons.math3.complex.Complex",
            new int[]{-892,477,-96,1000,820,580,-1,-77,-1000,-236,-197,255,450,535,-189,740,589,851,248,380,475,-1000,-257,-532,-761,498,-261,-1000,-633,-455,-619,-879,-400,420,-864,-400,400,-855,-400,-892,321,183,920,282,988,-400,-400,822,604,1000,-384,1000,-1000,-1000,400,1000,798,-319,-400,-489,-512,-783,400,720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "acos():org.apache.commons.math3.complex.Complex",
            new int[]{547,295,-184,-613,185,19,-28,-84,-877,27,-231,-965,248,601,274,597,-978,243,520,-59,-20,-205,-364,-18,990,-493,-395,192,108,259,920,370,-679,818,379,614,600,-209,-58,-591,228,-235,-357,-589,-358,12,342,-868,720,-892,-661,507,776,-200,-258,-916,-569,-426,882,-515,-95,-504,28,-337}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "acos():org.apache.commons.math3.complex.Complex",
            new int[]{1000,1000,1000,529,-523,-822,-627,735,529,847,-254,-1000,-1000,463,-365,-442,211,846,814,443,-781,602,825,-288,-698,-1000,-1000,1000,1000,432,708,-1,-212,236,1000,88,-374,766,-134,220,1000,-604,-441,-466,-585,70,1000,-1000,-1000,-937,-34,487,554,836,451,-367,-1000,171,-163,6,157,288,330,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "add(double):org.apache.commons.math3.complex.Complex",
            new int[]{183,808,-1000,795,-251,-951,653,-67,-805,-168,765,-1000,-195,331,-577,144,-587,-29,-1000,-1000,-1000,-585,718,579,-527,-420,567,-257,-1000,18,-964,-67,1000,-1000,-1000,-855,-787,113,938,1000,391,-1000,1000,-87,-721,998,-407,1000,-1000,-1000,-1000,875,-1000,24,-716,611,454,790,-615,241,1000,-1000,225,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "add(double):org.apache.commons.math3.complex.Complex",
            new int[]{-975,-313,-178,776,-772,-977,-338,417,-585,1000,490,-1000,-1000,644,713,330,-437,1000,-535,353,-20,562,-403,-217,712,-161,243,742,-512,-501,-2,699,336,99,-487,283,514,-17,418,-406,-70,-470,-214,-476,831,-559,615,-373,-160,-56,-230,-857,926,334,-772,-1000,174,567,996,576,710,180,834,50}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "add(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-904,1000,-81,41,-37,-245,-941,-518,529,546,-857,-1000,-968,1000,32,-10,199,50,1000,-402,-178,-1000,1000,-222,1000,-450,234,-179,-414,-1000,-710,-937,1000,-581,452,-931,-1000,-977,-59,-280,-525,-243,1000,-1000,-139,1000,-981,1000,-260,-781,-1,-1000,974,-61,1000,811,769,1000,-553,495,87,115,625,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "add(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-121,47,-695,716,-929,-17,119,-715,497,918,-237,-706,-710,267,743,-809,393,-748,511,348,-900,591,483,-781,-700,361,316,321,-876,-304,-481,-800,458,-876,570,-735,833,-709,216,64,-422,582,384,900,798,718,-781,990,-264,-206,186,-626,392,427,860,620,868,675,-921,89,872,-764,436,944}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "asin():org.apache.commons.math3.complex.Complex",
            new int[]{-505,322,-405,244,464,107,215,14,-371,1000,-533,66,-1000,430,-538,741,953,-568,-1000,43,-825,-1000,1000,379,-463,50,-1000,325,-459,636,-1000,-1000,1000,1000,-74,857,580,97,-172,-1000,701,-1000,373,-743,-394,1000,102,-15,-888,404,541,1000,-356,117,-1000,1000,-969,-35,-259,-760,228,1000,1000,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "asin():org.apache.commons.math3.complex.Complex",
            new int[]{86,437,-418,4,-712,-39,42,370,840,776,871,-314,-75,-668,314,-89,78,-264,678,-3,-438,251,515,-570,270,-387,-188,371,-152,71,972,-291,-182,-77,-404,-422,-578,-496,171,532,622,-923,641,-588,227,875,543,206,490,685,-111,689,-335,765,319,486,999,-345,147,-648,441,-517,923,-79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "asin():org.apache.commons.math3.complex.Complex",
            new int[]{-657,-695,-730,411,114,-141,824,300,401,1000,527,-1000,-1000,613,513,574,1000,-665,-1000,449,-1000,-961,1000,509,-548,49,-135,1000,3,-764,-1000,-879,1000,1000,1000,274,1000,-21,-452,-19,1000,-1000,-280,-1000,-475,777,-459,213,-1000,448,326,491,-901,-170,-1000,1000,-1000,-870,-1000,-606,1000,996,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "asin():org.apache.commons.math3.complex.Complex",
            new int[]{-94,-1000,1000,362,-68,399,-873,467,-1000,599,-617,994,5,-1000,-1000,472,-86,506,-394,-499,1000,391,-1000,-498,145,-937,-458,-1000,318,380,553,-424,347,870,-247,-706,-1000,-157,-562,-227,-805,360,114,-847,-270,-1000,-340,-326,53,584,-844,-850,-194,180,7,978,361,-540,-81,-1000,1000,-1000,-243,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "asin():org.apache.commons.math3.complex.Complex",
            new int[]{-843,-1000,875,847,44,-834,438,12,-690,-476,-344,385,-691,175,-2,225,1000,171,-77,152,770,-910,-1000,-1000,517,1000,-77,-275,872,-1000,-726,-218,-202,423,1000,-532,-1000,1000,836,487,297,559,881,-915,919,-166,576,-390,491,246,-908,285,135,-209,676,381,1000,1000,-1000,91,-1000,1000,1000,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "atan():org.apache.commons.math3.complex.Complex",
            new int[]{1000,552,220,888,-217,603,-141,728,1000,-924,439,1000,745,-867,583,1000,1000,1000,609,-575,-1000,1000,-1000,-46,-1000,368,1000,699,1000,-377,848,451,499,99,-1000,-815,-1000,-401,-957,-485,-1000,-994,-895,-467,1000,321,-195,-723,1000,348,292,-144,363,200,1000,-428,-412,-1000,-421,-226,980,-382,-637,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "atan():org.apache.commons.math3.complex.Complex",
            new int[]{170,197,262,-510,-615,57,-287,1000,-867,87,-488,479,-425,1000,291,590,-999,1000,175,-144,-95,-351,-50,-841,-780,-192,-17,252,-127,1000,1000,719,-1000,-530,-393,-290,152,621,138,1000,82,706,-33,-539,1000,230,-664,1000,304,1000,1000,-468,-858,301,981,920,-232,-77,818,-150,1000,-239,1000,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "atan():org.apache.commons.math3.complex.Complex",
            new int[]{-672,-153,-1000,680,1000,343,-286,-1000,852,-482,-118,-214,857,-583,-748,439,-259,519,-501,-51,1000,-806,811,1000,-1000,-378,-174,-251,0,-141,849,306,-518,262,-1000,352,411,834,857,-1000,-137,-117,184,450,112,381,277,-657,-887,261,149,-368,1000,193,2,713,1000,276,48,-739,1000,1000,529,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "atan():org.apache.commons.math3.complex.Complex",
            new int[]{1000,263,1000,-275,-654,-298,-496,-157,-1000,-326,813,783,-1000,-532,-616,1000,452,1000,-1000,-275,-878,1000,-450,-175,1000,-985,-706,145,-280,640,-1000,-310,-723,548,-598,542,-308,347,-493,-1000,159,1000,845,-913,-1000,-265,1000,-851,-286,837,809,-1000,-229,-614,207,653,1000,257,-1000,-1000,1000,462,256,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "atan():org.apache.commons.math3.complex.Complex",
            new int[]{-984,-407,469,-888,-1000,-455,819,-10,-1000,1000,184,-921,335,1000,-430,-1000,-974,-373,-65,179,81,-436,229,258,397,532,-502,214,-1000,659,435,111,-838,-159,-263,808,850,1000,401,661,872,1000,-662,1000,595,-574,196,1000,-1000,170,789,-269,-1000,632,-703,615,234,400,1000,926,746,-112,1000,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "conjugate():org.apache.commons.math3.complex.Complex",
            new int[]{1000,-430,17,1000,-912,359,-757,1000,39,423,-329,667,1000,193,1000,-378,-81,582,-1000,606,81,-235,-676,1000,213,-466,791,36,504,1000,347,-142,-1000,-662,-672,1000,-653,280,-1000,-140,1000,-857,390,120,251,1000,-2,873,-1000,-191,920,300,48,-316,667,-662,-1000,59,-543,390,31,-9,427,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "conjugate():org.apache.commons.math3.complex.Complex",
            new int[]{-486,213,-963,-970,48,-17,-278,-979,764,211,-672,224,-377,-735,-918,902,896,-131,-123,-423,-419,480,892,727,-908,454,-101,-207,748,-988,451,-500,-415,-776,686,-554,577,-496,582,-18,472,429,-854,-480,-846,94,246,-431,689,664,-420,43,10,317,-386,674,601,322,246,740,-538,678,399,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "conjugate():org.apache.commons.math3.complex.Complex",
            new int[]{-215,-57,-594,-202,-353,-1000,34,304,-761,-330,-373,41,1000,-1000,1000,1000,726,-301,460,352,1000,919,-773,-1000,-307,932,-1000,805,-414,-451,1000,-1000,-489,7,-627,207,-344,280,-22,930,-1000,-1000,-587,-397,-36,-436,911,217,-438,140,359,1000,48,-686,850,339,-1000,-469,-498,226,546,950,221,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "cos():org.apache.commons.math3.complex.Complex",
            new int[]{446,801,-222,-411,825,-562,-596,561,-337,257,641,-600,523,-428,818,-350,-751,416,-972,550,-298,956,306,-774,596,-639,17,713,-551,-634,968,240,-384,-247,-78,719,-667,-422,468,-499,-235,-948,-467,95,155,-274,-773,774,356,823,133,792,243,-571,716,256,-181,596,64,-376,-807,-634,-148,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "cos():org.apache.commons.math3.complex.Complex",
            new int[]{-80,295,-1000,91,-995,-887,649,-273,196,61,186,819,-425,1000,-354,1000,-483,541,851,1000,-732,330,929,140,1000,-353,1000,-1000,1000,521,686,-779,491,-590,-157,-328,767,-1000,-701,-116,-771,216,-199,-556,-753,1000,152,419,-757,-595,-638,-1000,1000,79,-1000,1000,-223,-86,-582,583,324,-1000,-292,737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "cos():org.apache.commons.math3.complex.Complex",
            new int[]{-308,557,1000,-361,359,1000,814,475,-453,664,23,294,-651,5,-1000,138,-474,60,-1000,-509,1000,-1000,-46,895,159,1000,-1000,158,120,-1000,464,509,1000,501,961,1000,-327,-842,-1000,-1000,-272,-414,1000,1000,-824,596,-1000,-755,570,-1000,308,272,1000,379,365,-233,418,497,1000,-1000,408,1000,-462,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "cosh():org.apache.commons.math3.complex.Complex",
            new int[]{91,286,-1000,-201,-321,-137,243,882,-193,1000,-1000,-1000,-1000,-1000,-505,-653,1000,-413,204,1000,2,891,-200,1000,1000,1000,218,-631,867,9,601,-901,943,-1000,-872,-1000,-272,-1000,-1000,673,-442,27,-1000,1000,504,217,-39,-668,-1000,-1000,31,319,-1000,1000,-1000,-1000,-151,34,-20,280,604,129,155,-975}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "cosh():org.apache.commons.math3.complex.Complex",
            new int[]{760,-177,-259,899,-85,-269,-128,343,-315,707,639,-902,-400,-1000,-155,406,377,112,-703,-812,1000,1000,-145,1000,549,278,-1000,134,1000,-804,428,-776,612,-352,476,-400,-343,-567,-400,-69,-1000,-663,-372,666,304,-767,-915,1000,117,-1000,-787,373,-1000,45,-1000,-417,-259,1000,506,100,1000,-455,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "cosh():org.apache.commons.math3.complex.Complex",
            new int[]{-978,-140,-703,-1000,164,912,-434,295,-709,-523,919,-623,806,-60,-264,94,-574,-547,158,-785,-1000,516,851,-142,671,-415,272,-38,506,1000,-38,-19,-731,1000,741,762,-83,1000,-607,732,739,-484,1000,741,-429,-344,447,1000,-521,300,303,-200,-40,760,-531,540,-137,-71,-1000,172,-797,32,-560,-339}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(double):org.apache.commons.math3.complex.Complex",
            new int[]{1000,1000,1000,1000,-82,1000,253,-1000,297,108,-1000,-388,-235,-735,847,473,-602,427,1000,-1000,-1000,-1000,-1000,827,817,-221,269,1000,-739,206,-723,-1000,-182,1000,462,288,-1000,-181,-774,-689,666,1000,3,1000,-354,285,-1000,-369,466,-751,1000,1000,1000,378,-425,-1000,154,-1000,1000,218,1000,1000,-998,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(double):org.apache.commons.math3.complex.Complex",
            new int[]{852,-643,-126,118,-54,139,-707,-354,-234,-699,411,-909,668,383,519,-1000,-838,1000,857,-333,760,-256,1000,31,289,-1000,1000,928,838,1000,1000,-680,-21,930,186,1000,-513,567,-382,1000,396,-817,825,-368,1000,-974,-2,-1000,982,-364,525,625,-259,1000,806,175,-872,1000,184,363,696,-1000,549,-692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(double):org.apache.commons.math3.complex.Complex",
            new int[]{-1000,-452,-1000,-1000,628,-865,979,1000,-493,-510,1000,198,1000,-1000,-84,1000,-641,-137,-1000,260,688,1000,34,-447,308,-225,368,33,-302,29,-902,375,-1000,-832,-1000,-840,-669,1000,-844,-511,-366,378,81,-274,-556,-1000,1000,875,1000,644,968,-1000,-860,-642,-112,1000,1000,-417,728,-498,-1000,-51,-801,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(double):org.apache.commons.math3.complex.Complex",
            new int[]{1000,-770,-1000,610,264,664,272,564,91,-375,538,1000,57,-425,1000,-489,727,78,554,370,-427,1000,-588,-212,-234,-415,-83,-609,-1000,1000,-943,-1000,-1000,-1000,-1000,-159,-719,-1000,-1000,133,2,301,360,-1000,358,285,336,-729,1000,639,1000,-323,-655,-810,39,275,-912,-98,1000,-811,-802,-70,-998,-155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(double):org.apache.commons.math3.complex.Complex",
            new int[]{-829,-175,-1000,-99,433,-1000,379,1000,-897,-1000,785,-620,970,-653,-70,584,-780,459,-476,-437,-260,-760,228,-287,802,-699,807,779,744,-919,-1000,811,381,-638,-1000,11,-560,174,-527,-893,300,150,1000,-53,394,-727,486,-261,682,-536,1000,71,-1000,728,144,681,702,-131,1000,-152,-216,-101,-563,973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(double):org.apache.commons.math3.complex.Complex",
            new int[]{1000,-123,-312,1000,-375,973,503,-690,356,-615,-269,938,560,-1000,647,283,688,-269,629,209,-595,-197,-1000,552,64,-58,-65,635,-1000,1000,-1000,-1000,-780,-141,-290,114,-581,-552,-485,407,1000,344,260,-1000,-572,363,-69,-234,394,119,979,72,456,-324,-210,-893,718,-417,886,-1000,603,676,-1000,-688}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-385,-292,417,-207,-396,586,-602,290,-807,-209,-257,-781,-67,-76,-106,621,695,218,-35,319,-428,686,39,-430,-15,-338,917,-145,-124,-293,-670,-364,-574,998,41,197,-433,294,-990,-139,685,-614,866,-228,418,-491,270,256,-821,-356,472,273,161,842,-676,-77,957,-246,-656,-880,656,397,23,589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{230,513,73,1000,-1000,1000,-52,461,-936,-1000,-387,-365,-481,-1000,-725,267,-15,1000,-356,836,-83,-133,624,-1000,-830,-1000,1000,-312,-880,-511,-1000,379,1000,1000,-197,-1000,-941,-258,823,282,140,-615,-122,-900,-520,-1000,-215,69,-609,664,1000,346,-260,1000,215,1000,213,-40,-238,-395,48,-738,-779,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{1000,-891,357,-1000,-209,751,-547,827,18,874,-276,-1000,-52,441,609,-386,-434,1000,1000,-972,721,-468,444,150,470,-90,-1000,669,-383,147,-186,262,-351,-1000,-1000,1000,754,-973,-1000,284,397,-901,348,147,-332,-804,92,283,-323,160,223,476,95,-782,-115,-698,642,-277,152,-250,35,-717,-646,-309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-334,-872,661,193,-396,464,-602,1000,-1000,-209,-257,-781,1000,-531,-845,-21,-327,502,700,270,-612,873,904,-596,-597,-879,-567,-606,132,295,723,1000,-100,740,41,1000,-676,-205,-1000,784,-486,-1000,866,386,-624,-601,1000,-1000,830,1000,814,-275,-387,110,-682,-1000,957,-1000,-262,-880,700,688,110,6}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{25,195,638,-1000,-396,-400,249,773,-1000,-253,-358,-1000,-1000,648,-354,-138,359,-908,-1000,1000,-771,-454,1000,-391,-435,-862,655,-526,-1000,409,-1000,324,-735,74,336,-1000,-872,-1000,-267,-51,188,-83,1000,1000,1000,-993,1000,-37,-1000,709,738,577,-1000,-747,814,1000,-553,-1000,1000,-487,-12,-1000,1000,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-1000,285,-388,430,-282,1000,-301,-599,1000,795,-701,-786,1000,270,733,627,-1000,1000,695,498,-190,388,-917,24,-55,588,-665,453,722,-415,627,-1000,-718,174,-907,178,1000,80,422,-1000,295,-451,762,-555,519,1000,145,-1000,962,-569,-810,-1000,422,251,-477,-588,-295,493,159,-40,-340,969,525,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "divide(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{1000,394,-1000,697,-356,-940,719,-1000,-174,-1000,358,1000,-491,-173,-901,285,-601,-248,-594,146,189,-725,0,-499,235,984,240,-161,713,649,-120,-251,1000,1000,-338,-559,-1000,-829,1000,-85,389,760,604,691,-342,-357,-378,-298,-372,-168,585,506,399,-49,1000,983,-1000,-247,-219,135,378,-295,-674,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{-840,-51,388,-215,939,558,-636,-638,338,-910,-963,541,896,64,-628,800,543,-250,356,983,-529,737,-422,-245,-8,-997,49,328,698,120,-641,-209,246,-234,-530,451,119,-149,837,281,855,-738,884,-226,370,-540,-643,-715,290,6,-415,290,438,-545,-757,-82,-936,983,871,540,741,-348,-888,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{403,-243,601,-503,-753,-329,-661,95,-949,564,-475,662,-841,926,761,208,-505,-251,416,-851,-953,-556,-645,-158,54,-838,-968,-699,-741,-584,566,-810,-429,462,-364,314,536,44,413,-541,111,749,-911,-327,-561,-462,-933,844,522,-229,-646,-201,21,-542,-708,41,750,398,429,707,-129,-637,-13,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{1000,-779,1000,713,-521,-1000,749,-1000,686,-960,1000,-909,864,-905,1000,1000,-638,796,-1000,-221,960,620,-762,186,98,1,447,512,1000,-577,-1000,-670,-1000,1000,41,1000,451,866,-129,-846,-20,978,-757,1000,-1000,401,-154,372,614,-25,1000,-85,1000,498,-540,346,1000,-886,1000,-652,-587,-1000,-1000,-934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "exp():org.apache.commons.math3.complex.Complex",
            new int[]{-776,182,166,394,-501,1000,-302,472,527,313,55,487,-450,-479,-1000,656,331,253,106,-1000,82,287,22,-625,-766,1000,1000,-480,-1000,105,-825,-1000,261,371,826,-140,-85,205,120,-325,323,-139,473,-371,195,1000,233,152,108,-1000,42,-120,1000,262,1000,-199,-599,-385,-531,-444,-358,1000,-69,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "exp():org.apache.commons.math3.complex.Complex",
            new int[]{126,583,-123,-362,244,127,-352,560,55,-1000,61,-62,-337,49,538,-910,-950,785,-868,1000,-582,-10,84,460,114,81,-125,638,-412,-293,-347,986,-910,223,-881,333,-511,703,-745,-113,1000,458,-264,1000,539,299,-196,874,-16,-391,-170,458,-64,712,-367,-1000,-317,582,504,-33,-554,-461,-1000,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "exp():org.apache.commons.math3.complex.Complex",
            new int[]{-470,1000,270,-297,-857,727,-597,-1000,1000,-592,-1000,-400,4,2,1000,-46,-345,958,49,-17,322,870,-212,675,-1000,-1000,109,176,-412,-925,-1000,-770,-1000,-966,-1000,-1000,-336,372,828,732,235,-297,562,544,20,888,-476,-161,192,-816,833,481,-374,825,-15,540,-1000,-1000,1000,-458,-983,-258,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTM1ODk3OTM=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getArgument():double",
            new int[]{769,-344,-421,722,-721,-903,332,339,-430,402,-561,-386,406,277,787,91,-523,-709,617,-490,139,767,390,-194,-439,78,-340,1000,205,-130,1000,-174,-995,-121,-537,875,233,-506,942,-144,219,275,-525,-1000,-367,108,1000,90,149,-896,222,637,176,1000,-5,206,-654,1000,-648,515,108,-1000,-1000,-649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getArgument():double",
            new int[]{-1000,266,-1000,-233,169,-246,-1000,-665,-453,-1000,-112,731,-1000,-993,223,835,-853,548,-20,473,-351,337,456,678,-316,-32,-1000,-34,-572,800,583,395,-1000,619,-721,908,-416,-483,856,-516,225,-1000,827,764,-696,-574,-38,-722,-783,-779,294,-94,-297,1000,188,610,-924,-342,754,69,1000,-267,-323,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getField():org.apache.commons.math3.complex.ComplexField",
            new int[]{-796,-1000,675,669,-927,189,-835,1000,-332,872,-341,-1000,520,804,-453,-1000,-132,-1000,870,52,-928,-858,1000,435,966,-891,568,1000,591,1000,-1000,337,-112,485,-533,-222,-662,644,723,1000,-359,-414,-330,585,-398,-1000,1000,-269,150,926,-1000,60,393,251,213,-385,726,-259,-708,-626,652,41,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getField():org.apache.commons.math3.complex.ComplexField",
            new int[]{-414,-278,1000,727,309,477,-722,461,686,550,327,472,-557,-177,1000,1000,-419,-1000,1000,-939,-1000,877,-862,958,1000,-1000,969,1000,1000,126,-396,389,1000,-1000,-544,-493,497,-1000,-239,427,-79,-891,-418,-1000,734,27,-1000,-1000,1000,-817,-1000,-32,1000,-541,1000,279,58,812,1000,-1000,-500,-1000,-1000,-22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getField():org.apache.commons.math3.complex.ComplexField",
            new int[]{-433,-1000,-349,-1000,859,-4,25,-1000,783,-95,-984,378,172,-1000,-80,716,187,-840,1000,-1000,-1000,1000,-424,-554,-303,253,799,-1000,-630,76,-1000,1000,-1000,1000,-1000,-13,795,-119,-822,-39,-1000,-68,-1000,1000,-395,43,-1000,163,439,-134,-1000,653,-692,600,408,784,-1000,-1000,427,1000,-494,1000,-1000,153}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:ODYuMw==", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getImaginary():double",
            new int[]{-794,-741,1000,-340,863,-38,-396,-639,617,-1000,-1000,178,422,-907,-973,-730,56,-349,271,-1000,-1000,-753,-795,1000,-1000,746,-1000,992,-88,270,-1000,1000,425,-1000,-1000,-861,860,-941,-801,52,-941,-393,-1000,-1000,-1000,-410,1000,-1000,1000,348,-6,-657,-1000,-1000,-1000,-562,-1000,293,74,-395,-117,-807,-1000,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getImaginary():double",
            new int[]{-794,-1000,1000,-197,-377,19,57,360,-33,-73,-338,-907,107,-907,278,-961,728,-1000,-289,400,111,11,-795,1000,-1000,77,323,-364,-1000,215,220,713,947,400,394,-402,-400,1000,-1000,676,-51,-462,-1000,-425,400,-994,-400,-1000,457,-322,-486,261,-412,96,400,182,-1000,-415,-826,-395,-1000,198,-275,-973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getImaginary():double",
            new int[]{-165,-185,-120,25,557,56,-451,-524,154,298,-230,990,-221,-631,-538,-691,215,936,368,-362,-580,-650,-478,110,-59,-783,-410,496,968,1000,133,400,374,-418,-713,-694,658,-1000,-79,-728,-410,-388,-83,847,-710,1000,-156,-612,963,304,-211,-498,-1000,-1000,-925,-160,-1000,293,407,-51,206,-627,-65,-298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getReal():double",
            new int[]{-417,-1000,-194,444,524,-91,901,648,-928,-159,-629,-597,-351,-327,817,-275,-1000,-88,-447,1000,-355,-69,965,-1000,1000,751,-890,799,-784,1000,241,-248,537,-96,74,-116,1000,-944,162,-733,-1000,60,-284,81,599,-1000,-157,1000,-618,1000,714,374,-1000,951,-692,1000,194,-676,151,-231,-942,-794,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getReal():double",
            new int[]{-1000,-503,994,137,1000,739,424,-340,640,-1000,756,-1000,-424,-967,1000,-1000,1000,1000,-503,193,-777,-1000,436,1000,-181,1000,-1000,-247,1000,1000,272,-279,976,89,-1000,-1000,-387,-1000,-1000,695,479,302,1000,-544,-248,-1000,555,-1000,-1000,1000,145,-2,1000,1000,681,-1000,435,-1000,-1000,1000,1000,-1000,911,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "getReal():double",
            new int[]{639,-340,-186,737,48,-163,551,-198,-714,1000,-1000,-1000,-82,67,-1000,65,-668,157,-1000,-375,-270,-1000,1000,-705,970,-261,248,-167,-453,673,357,225,49,811,249,428,1000,-616,-252,705,-493,-782,243,767,1000,-143,-506,561,377,1000,-698,-1000,1000,-233,-1000,-337,-956,-723,-339,-484,-337,-645,75,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "isInfinite():boolean",
            new int[]{653,1000,241,642,-76,-1000,1000,-172,-281,491,-55,-348,373,525,-1000,-691,-17,284,-1000,602,-46,183,275,1000,839,141,-1000,1000,18,153,-209,211,-926,-645,-379,367,123,134,-853,1000,-623,527,475,-453,-23,-873,1000,-556,-548,-202,-527,284,503,81,-775,11,282,1000,-16,614,-244,1000,85,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "isInfinite():boolean",
            new int[]{-1000,5,171,-1000,-150,-401,-221,219,-685,472,744,695,1000,415,-462,-43,660,121,-812,1000,1000,-746,14,-400,2,117,-1000,-476,-559,-1000,-565,886,374,423,-372,-572,98,852,-959,368,-195,-513,-337,499,-16,0,32,1000,-456,931,-1000,398,406,75,564,-320,-374,-179,546,117,-696,69,-832,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "isInfinite():boolean",
            new int[]{664,1000,-572,-87,-867,384,-791,-757,-59,727,21,-536,1000,-329,-659,-135,-447,-1000,831,177,437,-588,151,-615,-547,579,-798,833,-235,1000,245,110,-1000,-1000,1000,-398,7,-252,175,-956,-8,188,-1000,-365,382,1000,362,930,-635,40,827,-1000,-790,1000,827,-115,-134,-233,124,-1000,109,-1000,959,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "isNaN():boolean",
            new int[]{-441,701,332,1000,-688,68,-246,-284,232,-37,642,-889,307,165,-1000,986,296,-1000,203,-341,891,1000,-769,-152,280,250,-320,-1000,-731,755,-54,802,866,-338,963,-763,-166,-628,994,-377,-814,-679,895,-1000,223,273,292,-984,206,507,-385,1000,303,649,-794,-994,-614,172,-949,-652,-1000,-512,-576,645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "isNaN():boolean",
            new int[]{259,379,1000,505,-779,127,668,-356,830,-643,741,-893,-573,266,-161,-219,-1000,-523,-996,345,-479,282,-377,929,957,-1000,720,-1000,-1000,-556,-326,266,582,1000,-158,-783,166,-494,168,40,-91,-857,-401,-1000,-504,227,-921,-458,1000,-549,1000,94,458,-1000,-1000,-1000,1000,174,-102,-1000,-803,-821,-1000,-59}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "isNaN():boolean",
            new int[]{-478,1000,197,1000,-171,213,215,-53,-439,612,1000,-781,-521,-86,-659,1000,55,-1000,1000,-251,1000,1000,-1000,-171,1000,712,533,22,-1000,1000,-816,-203,-343,729,985,-356,-636,-740,164,-646,-280,578,1000,-106,-546,504,428,-395,795,354,570,1000,865,1000,724,-1000,-313,417,-1000,-1000,86,-318,-759,-683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "log():org.apache.commons.math3.complex.Complex",
            new int[]{-876,-1000,-1000,1000,864,863,760,1000,-845,355,754,-1000,-560,-1000,1000,667,1000,-773,-1000,620,-1000,-585,-1000,405,-1000,-680,-1000,-1000,-638,-1000,-591,-1000,-672,-654,-1000,1000,475,-1000,137,1000,1000,967,1000,977,1000,-748,-799,-1000,228,1000,-637,1000,1000,1000,1000,2,1000,-569,-1000,-605,-1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "log():org.apache.commons.math3.complex.Complex",
            new int[]{-158,-721,931,23,689,-425,-467,1000,-459,462,921,350,-1000,-152,179,1000,1000,1000,-1000,496,-1000,-528,-626,-587,-834,-1000,-987,-403,275,-818,-19,-492,-1000,-1000,872,-603,291,-1000,-77,331,369,996,-102,1000,1000,748,442,-319,198,1000,-1000,1000,-114,1000,1000,810,244,-1000,-223,-273,-44,780,-758,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "log():org.apache.commons.math3.complex.Complex",
            new int[]{-437,382,-1000,48,636,-509,287,361,-949,-223,776,-562,-1000,-1000,5,897,1000,1000,135,551,-880,-55,-1000,729,-278,369,-410,-1000,-1000,-298,-719,-1000,-587,-183,-679,1000,490,-557,-999,700,953,247,1000,217,568,-397,-1000,-984,-728,791,-346,764,663,982,1000,-733,-1000,-46,-1000,-755,-1000,-160,-849,-848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "log():org.apache.commons.math3.complex.Complex",
            new int[]{995,657,363,531,-437,1000,-242,-400,-549,156,743,418,-366,257,-48,615,-65,348,-42,-111,108,-90,214,-972,-327,192,-148,1000,347,-168,965,18,341,60,915,-398,1000,85,391,-190,-367,1000,400,158,242,178,-91,224,810,-554,434,-91,-489,-113,298,476,-603,-49,131,586,1000,663,294,68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "log():org.apache.commons.math3.complex.Complex",
            new int[]{-149,-733,749,26,49,-1000,-782,293,-712,340,-281,110,60,-353,-989,-900,377,1000,-887,1000,535,-1000,-750,1000,-718,-441,-238,808,-822,-520,790,-1000,-315,-1000,571,344,392,-357,854,-1000,-1000,540,-121,247,216,124,-214,549,-1000,1000,-403,285,944,861,969,968,970,-274,931,-197,371,267,-117,-852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(double):org.apache.commons.math3.complex.Complex",
            new int[]{-1000,-1000,-538,1,-146,-922,618,1000,-693,370,1000,-585,-195,-427,612,-307,596,-797,883,808,-248,249,524,-290,-597,-59,815,-867,712,1000,519,-805,999,625,-1000,-965,1000,1000,376,-424,806,-240,466,672,1000,119,-470,-1000,-663,-153,100,588,-1000,-1000,109,-1000,-347,172,-1000,361,-931,-309,-1000,974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(double):org.apache.commons.math3.complex.Complex",
            new int[]{-833,-733,-170,24,-1000,-1000,-576,70,-1000,-929,-535,-66,-823,577,1000,475,-210,-1000,-53,916,-672,-182,-532,514,462,1000,1000,-573,-135,401,506,-1000,31,-149,-1000,-761,1000,701,1000,-1000,1000,-482,601,413,516,-99,121,32,-977,820,295,-504,582,-535,336,-1000,-9,362,-1000,-1000,-511,-339,-795,996}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(double):org.apache.commons.math3.complex.Complex",
            new int[]{-161,-581,-480,-829,-268,727,112,349,-1000,1000,1000,-1000,396,-1000,-169,726,1000,-1000,155,-1000,1000,974,880,-872,-947,362,-882,-1000,639,1000,1000,-936,-316,-414,-1000,724,811,1000,-1000,1000,-1000,-1000,618,116,1000,1000,-344,-1000,-1000,63,-1000,-620,-367,-1000,1000,1000,129,-1000,153,1000,-361,-1000,-384,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(double):org.apache.commons.math3.complex.Complex",
            new int[]{412,-55,-659,-668,-982,949,539,245,-770,-182,-303,145,-24,-139,-233,-461,-1000,255,1000,-1000,1000,-121,-309,-136,-346,-1000,71,650,349,714,503,-907,-161,-1000,-1000,822,-445,374,760,-713,491,-648,-820,181,1000,-42,1000,-641,560,787,-408,-534,997,1000,508,1000,-536,-649,381,-840,-310,489,972,-970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(double):org.apache.commons.math3.complex.Complex",
            new int[]{-181,-315,-1000,-1000,-1000,537,679,95,-886,-623,-215,737,-705,-938,265,-830,-1000,333,1000,-1000,752,-38,-558,528,-150,-1000,575,131,1000,1000,915,-1000,-318,67,-1000,1000,416,868,1000,-1000,420,-996,-1000,327,1000,-254,1000,-548,-851,1000,545,-248,-1000,1000,529,1000,-1000,-889,-734,-223,-379,983,1000,-989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(double):org.apache.commons.math3.complex.Complex",
            new int[]{-847,-726,435,-572,-152,-892,1000,1000,-925,468,1000,212,1000,-1000,907,-1000,595,-711,702,207,-454,872,427,-1000,-376,97,-708,-398,1000,1000,1000,-656,1000,184,-390,-691,898,1000,45,-217,-124,-1000,-721,733,694,83,215,-1000,-413,-1000,94,653,-1000,-1000,-946,-210,-578,-517,-103,1000,-435,216,-569,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(int):org.apache.commons.math3.complex.Complex",
            new int[]{-917,610,991,-179,-426,-617,-934,-834,622,-552,-133,219,-737,-627,-286,-293,-41,-483,442,-4,-377,30,-498,131,-981,192,590,-769,263,163,730,-695,922,668,468,-845,292,-665,-236,-631,851,467,-460,642,-427,-117,-588,-339,-165,112,92,606,974,-279,183,-35,-783,-160,-350,-206,637,-953,100,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(int):org.apache.commons.math3.complex.Complex",
            new int[]{279,771,461,-910,870,-125,-956,845,239,422,1000,-375,-239,-578,582,-960,-329,424,101,1000,1000,83,-556,106,-680,-69,-208,-1000,-482,-270,-352,-249,-145,678,-361,36,900,-208,474,420,303,442,158,-258,-197,-414,-929,428,553,646,490,727,-551,-460,-285,91,232,382,311,555,-155,-358,1000,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(int):org.apache.commons.math3.complex.Complex",
            new int[]{208,579,438,1000,-1000,-1000,-287,-1000,323,-1000,-1000,823,-162,979,702,-285,-998,-792,-239,443,-845,1000,887,-163,-1000,462,-1000,-1000,946,69,1000,-362,665,464,659,-516,3,116,-1000,-885,1000,1000,561,785,759,398,-77,-534,-137,516,-97,112,-1000,1000,642,-983,509,-231,825,-186,-448,-462,112,887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(int):org.apache.commons.math3.complex.Complex",
            new int[]{-329,47,-638,392,-249,-98,-31,400,-957,-1000,-13,-897,-607,674,-566,-1000,-29,266,-534,175,-370,388,-26,-659,463,1000,-912,327,370,-268,176,676,5,-631,-653,554,538,-757,-157,650,-356,-148,45,224,1000,1000,-1000,-190,373,109,-302,-724,-977,-406,183,156,1000,442,232,-1000,-780,981,-565,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-236,745,202,-246,-745,135,-772,-417,675,718,624,494,-749,-61,859,754,-781,-615,-279,-675,54,-463,-140,-455,-579,-431,904,-808,-204,-445,1000,-4,-1000,344,801,602,514,143,-578,-1000,-320,-48,-1000,632,-54,182,527,1000,95,832,-394,-762,278,550,-41,304,110,1000,1000,-184,87,-499,-1000,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-856,1000,548,115,132,524,-165,-268,-505,456,-52,-488,475,-1000,-373,286,-952,-605,484,-1000,-270,-572,479,799,-205,-992,766,635,856,-184,526,-873,-330,226,-126,477,303,954,-541,88,-467,-1000,46,114,220,797,780,-20,312,996,-1000,-715,-371,365,-1000,-588,93,-39,8,695,-461,-436,1000,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{436,-628,507,388,54,-817,-903,1000,58,269,825,-66,-23,-1000,-541,626,-422,-1000,1000,746,271,260,-1000,-202,-333,-943,620,-240,-148,805,236,1000,-121,898,-260,-1000,356,-208,-936,414,515,-665,386,-208,-137,179,898,686,667,40,-1000,-905,82,-180,415,-557,-661,-413,-87,-771,92,483,1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{1000,-1000,1000,-177,-947,277,-733,1000,-497,1000,1000,-754,-1000,-1000,-169,326,1000,-1000,-1000,-1000,-315,-1000,-855,-346,-590,-1000,827,-329,369,667,-962,-852,17,165,-199,-43,-296,-72,-1000,1000,1000,-171,1000,370,47,-109,1000,-581,823,290,-1000,-1000,-864,-110,-575,-877,-577,-647,-528,-1000,802,-19,1000,456}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{5,745,1000,663,110,284,-212,-170,633,-842,213,265,121,1000,-426,766,-946,73,-606,-434,-89,-894,-523,-407,40,-180,199,-808,280,-71,-225,42,-621,929,801,866,-358,-527,-1000,340,1000,-997,81,-411,477,-175,-372,-191,236,-91,-1000,-785,1000,133,1000,-807,-1000,285,-1000,-1000,-484,312,234,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-263,-580,1000,-1000,73,-1000,-862,1000,429,-261,1000,985,492,151,-655,1000,249,-1000,1000,477,1000,616,-872,-358,-417,-1000,1000,-114,130,-400,367,516,-395,653,-897,-1000,-82,428,-1000,1000,338,863,433,-1000,-432,-25,-211,-318,1000,-1000,-798,-662,427,-299,-18,-413,-314,-269,-1000,-947,729,556,1000,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "multiply(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-101,1000,-626,-364,387,50,120,-1000,-715,655,-891,-404,-1000,-1000,-716,723,835,182,-23,-1000,1000,-1000,1000,877,-279,-1000,442,-180,462,-67,411,42,397,590,100,-1000,1000,439,-808,990,-918,-173,-133,160,-126,23,-1000,-405,-302,14,1000,216,176,180,845,1000,1000,-269,-1000,-692,178,-325,-38,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "negate():org.apache.commons.math3.complex.Complex",
            new int[]{-842,394,1000,-144,389,-1000,-78,379,7,-1000,706,907,1000,-1000,443,-145,-1000,556,360,1000,-495,721,-1000,-342,234,326,855,-1000,-621,122,-164,-1000,-584,-169,65,-406,-1000,-619,-763,-464,-476,-771,-920,-835,-747,-188,-909,534,5,71,816,-466,677,-808,-1000,657,122,198,1000,-78,128,-426,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "negate():org.apache.commons.math3.complex.Complex",
            new int[]{-934,-97,-545,-689,545,-31,548,-852,-283,-1000,155,1000,657,354,-193,176,434,385,449,-645,980,53,-1000,-255,188,431,982,-183,783,590,-690,-329,-346,-379,353,765,130,1000,-767,419,-622,-147,-5,125,-65,-63,-1000,918,20,-536,109,823,423,-255,-516,-117,482,291,1000,26,354,390,-369,466}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "negate():org.apache.commons.math3.complex.Complex",
            new int[]{-6,-883,-848,54,21,-1000,-131,-275,-999,-818,1000,1000,-65,-380,-813,-1000,602,911,50,-259,-99,138,-832,-184,623,-718,213,-546,179,857,-1000,-789,-1000,-179,-931,1000,1000,-146,-865,-937,55,-562,-58,-1000,-223,-195,-713,647,-1000,543,-207,707,88,259,103,-420,637,54,115,-625,-629,30,3,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{301,-16,1000,924,678,43,-1000,-1000,-917,289,20,812,-1000,1000,388,259,-122,373,-33,-871,1000,606,156,-1000,382,-9,212,-398,-1000,814,777,-19,1000,-255,421,-435,1000,-242,177,849,282,-956,-548,-168,-215,66,-564,1000,-482,702,-1000,-445,-869,-305,-92,468,-1000,503,208,-356,-227,919,406,-142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NotPositiveException", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{-1000,211,-259,-230,845,345,379,-390,-412,655,-759,105,230,-1000,166,536,-1000,-672,-936,958,-1000,-1000,270,-660,1000,-564,-514,-1000,-1000,-592,-643,-432,128,-277,-50,-635,1000,1000,168,116,-137,513,1000,415,-492,1000,-100,285,1000,-22,210,-174,643,385,-322,-1000,-1000,96,-204,263,-207,96,-702,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{-320,209,-112,-425,676,1000,434,-671,-204,225,-227,-489,-657,-225,-525,446,-1000,-1000,-1000,241,-621,-283,1000,-605,531,775,-571,-1000,-987,114,166,-197,1000,-860,231,-526,-161,1000,894,846,958,1000,-243,552,687,311,-373,1000,78,912,-685,396,332,-290,-677,-92,-1000,623,915,-621,-807,1000,1000,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{18,614,17,-235,768,-891,897,414,250,94,470,-1000,22,464,-466,323,235,237,395,-793,317,-789,183,8,2,1000,9,-902,68,-1000,477,-279,225,-810,-482,513,238,-90,-113,408,900,50,762,-20,-25,388,-1000,-181,30,320,-299,-596,525,888,-917,-11,-168,-1000,352,169,1000,311,-450,298}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{686,821,-629,4,-663,329,49,-283,362,1000,-199,-1000,1000,-34,-611,366,-1000,-783,-890,808,-1000,-131,-959,141,449,809,-788,-935,91,-1000,-1000,-456,-1000,-626,-548,578,-435,698,220,227,827,219,652,85,198,595,-931,93,1000,855,635,-130,160,588,-1000,-512,446,-903,877,-541,-262,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:java.util.ArrayList", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "nthRoot(int):java.util.List",
            new int[]{-180,717,-535,-791,72,-1000,679,779,1000,150,642,-1000,1000,-614,1000,256,-23,-49,634,236,7,-1000,-213,115,-150,1000,182,-842,-1000,-1000,-105,-601,-926,-602,-496,832,1000,418,-429,-530,1000,205,1000,-89,146,716,-770,-1000,1000,459,642,-396,1000,1000,-833,-478,635,934,1000,486,600,-116,-1000,901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(double):org.apache.commons.math3.complex.Complex",
            new int[]{-1000,-1000,989,1000,178,675,759,80,-228,-521,623,657,-925,1000,-21,198,44,-59,389,1000,494,568,583,-230,-1000,1000,899,-1000,-125,471,575,274,897,-72,1000,-438,-621,1000,-1000,921,-884,-974,359,-1000,579,925,1000,191,-531,463,514,-1000,-1000,857,677,-1000,-593,63,363,1000,1000,-255,36,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(double):org.apache.commons.math3.complex.Complex",
            new int[]{-1000,51,1000,395,1000,-881,599,1000,494,-346,-633,747,153,332,902,1000,590,-773,756,705,-33,-576,985,-230,-1000,1000,1000,-530,-1000,-1000,-474,545,258,745,1000,-1000,1000,404,-906,-1000,-111,-1000,1000,-502,501,-363,60,924,255,-100,70,239,-1000,552,246,588,-1000,1000,52,895,279,-1000,-1000,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(double):org.apache.commons.math3.complex.Complex",
            new int[]{-216,-625,274,-62,-570,462,-716,-331,-551,-3,-452,194,908,-1000,-477,151,-630,761,110,-193,-1000,-613,1000,1000,-229,106,1000,-73,198,602,130,457,149,108,-568,-244,-26,-587,-444,146,-1000,247,1000,762,985,-977,765,912,-812,288,-773,345,-744,1000,1000,-710,922,-164,249,-59,380,240,358,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(double):org.apache.commons.math3.complex.Complex",
            new int[]{-64,442,920,516,588,576,645,-711,-1000,-356,211,-515,-31,409,718,529,356,-992,-306,277,1000,-545,949,-549,-1000,1000,144,-923,-1000,-1000,-155,-315,1000,-520,685,-813,570,786,-885,-378,602,-988,1000,-919,-1000,114,481,1000,1000,-1000,882,781,-240,695,-722,-385,-1000,-870,698,651,1000,-56,-1000,-461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(double):org.apache.commons.math3.complex.Complex",
            new int[]{1000,113,-516,419,-71,-604,-768,330,1000,-1000,47,-1000,-794,-563,597,211,-537,396,220,299,-467,-1000,-620,-844,709,347,267,130,996,635,1000,1000,716,1000,209,-516,233,94,-177,412,-450,239,-808,-205,80,-1000,-334,-662,-222,-16,-913,556,-294,-1000,568,482,521,1000,245,317,-688,-1000,1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{1000,-874,-856,423,-867,318,-869,-34,-761,-648,-379,-423,-444,840,-773,-135,-253,-620,-99,897,-303,-1000,-1000,-203,453,-685,-526,459,347,-125,351,-67,208,-628,-313,-570,497,271,592,463,951,65,1000,1000,731,198,-343,1000,-100,-4,688,-956,502,-887,838,-675,1000,-696,210,248,627,-173,-434,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{453,525,-518,1000,1000,7,459,-1000,547,-1000,-448,-46,-194,146,-399,-24,127,-367,-1000,-729,1000,884,-604,526,-1000,39,-614,-1000,-482,1000,-642,-939,677,1000,855,1000,-1000,-45,-232,-240,1000,303,-1000,-358,153,323,783,350,177,368,-454,776,-49,655,240,-533,-52,1000,264,527,-1000,105,631,-638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{285,969,-86,338,-1000,1000,-876,-328,-117,-776,860,1000,-638,875,303,694,-53,-320,1000,1000,1000,-1000,-389,-15,985,5,1000,550,68,1000,722,-1000,309,-1000,1000,497,656,-1000,1000,283,1000,412,-1000,86,136,-1000,-1000,574,-777,-1000,-853,-1000,1000,-684,817,35,683,-1000,1000,-1000,537,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{541,-874,-706,300,-953,521,44,582,-182,-342,-886,-676,209,880,-731,59,-232,-145,-744,752,-635,-814,-917,839,857,-869,-526,131,60,-360,819,-669,-366,-95,-407,-983,650,422,357,903,46,130,768,-47,-17,-339,-270,490,176,912,806,-176,-308,-770,323,-868,416,-675,210,-680,394,-279,-444,-577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-883,771,-160,4,-356,-1000,-132,807,839,-1000,9,-1000,1000,386,-1000,366,439,884,-1000,938,151,901,-33,-593,31,850,-66,357,98,-7,-731,-313,-819,472,1000,-511,131,1000,-646,485,-17,443,-798,-798,740,918,557,-1000,688,1000,201,836,-33,-264,-550,876,195,231,414,260,-890,-130,-5,-513}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "pow(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-433,-140,623,1000,149,-22,-531,-609,707,-652,102,1000,415,-539,509,1000,187,-574,-139,1000,701,727,-75,367,-3,-35,309,-239,401,398,198,728,631,603,679,168,-961,-706,766,671,879,561,-858,1000,826,637,-638,656,-65,168,-1000,279,568,67,-410,1000,-325,4,7,925,-571,552,953,414}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "reciprocal():org.apache.commons.math3.complex.Complex",
            new int[]{-901,-108,-1000,899,424,688,-558,-982,692,597,-377,-308,-124,724,789,441,-104,948,264,777,171,-1000,-1000,-535,-705,377,-644,-753,947,321,1000,1000,-998,634,-635,187,875,66,-916,-571,526,524,338,-915,-533,-119,303,341,-216,754,837,-582,155,-1000,584,-736,588,-681,-804,-993,-1000,-355,825,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "reciprocal():org.apache.commons.math3.complex.Complex",
            new int[]{-1000,-243,-1000,228,294,373,-991,-1000,1000,901,-53,-259,1000,732,439,1000,-503,1000,-290,891,-323,30,-1000,-341,-1000,786,-1000,-1000,1000,597,478,1000,-921,461,211,699,1000,-236,-1000,-700,-14,67,502,-940,-691,-473,-5,1000,-591,1000,1000,-925,-76,-1000,709,-815,407,-929,-863,-1000,-701,-281,878,-978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "reciprocal():org.apache.commons.math3.complex.Complex",
            new int[]{1000,547,1000,723,641,379,-122,315,-928,-938,-1000,-488,-1000,-414,828,-122,710,-552,792,-245,531,-1000,400,-115,1000,-984,1000,1000,-754,-441,345,-463,-487,-1000,400,-166,-1000,17,763,-388,-400,400,-766,-241,-306,507,1000,-785,-278,-330,-665,379,-205,726,-400,-345,-623,1000,400,548,-593,-444,-81,-471}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "reciprocal():org.apache.commons.math3.complex.Complex",
            new int[]{-460,803,95,-457,-647,440,414,351,-261,514,-622,-1000,-556,-1000,-500,71,101,-261,788,-512,-621,-173,-978,-425,-208,-984,-387,529,-583,-769,-81,-463,-487,273,-1000,-166,-373,-593,-380,668,456,-392,-309,317,989,130,-924,-379,-359,-828,406,71,-385,-24,-359,-534,295,-1000,-177,160,329,-275,11,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "reciprocal():org.apache.commons.math3.complex.Complex",
            new int[]{400,1000,211,-384,989,-91,463,286,41,-508,-1000,-181,-654,-1000,230,31,330,-1000,1000,932,-401,76,1000,821,-884,-566,391,-580,852,-1000,-1000,-620,-322,-1000,879,-124,-183,909,498,-497,-1000,400,-319,-626,46,1000,409,-38,-507,79,-400,-750,-683,-549,130,-430,-529,-580,1000,67,-307,-212,-518,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sin():org.apache.commons.math3.complex.Complex",
            new int[]{-396,-701,435,990,19,729,-756,-624,891,-272,461,290,-864,-986,373,-152,-710,843,381,-617,570,-454,-173,-689,772,-726,-689,-298,397,-515,855,-7,524,-173,-957,-683,-106,-457,-110,-693,444,316,-388,900,70,-476,-335,-317,831,-678,-493,591,-54,-372,348,441,-2,171,-136,-339,-448,48,-728,858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sin():org.apache.commons.math3.complex.Complex",
            new int[]{-26,11,929,562,-567,-485,339,65,938,-624,851,-146,495,962,309,1000,974,813,191,-611,379,395,870,-451,-298,-636,-296,95,965,99,-420,478,245,421,-712,-226,388,882,-274,594,-112,775,-654,929,-13,-905,488,-319,-570,-887,-725,-24,-503,-96,-393,-31,965,-786,459,-240,-539,-623,-843,-690}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sin():org.apache.commons.math3.complex.Complex",
            new int[]{645,119,393,831,412,969,678,-1000,789,-653,513,-182,117,-477,1000,1000,218,-400,1000,-848,522,-878,301,-512,968,-790,-583,-58,794,-1000,-400,1000,-384,-125,937,-1000,-1000,782,-45,-515,-394,-665,-704,77,-1000,445,185,214,716,-1000,-1000,686,-452,-139,348,722,-118,-448,420,-683,-866,-274,-271,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sinh():org.apache.commons.math3.complex.Complex",
            new int[]{910,684,-539,785,794,747,419,719,551,313,770,-971,509,430,283,-235,329,436,534,-466,862,-158,-317,157,669,634,589,-817,190,-2,243,-950,151,-715,-355,298,17,965,971,-108,-969,-769,-832,474,688,-38,-46,137,-595,-176,-402,-799,-461,419,-139,366,-52,-378,-43,-358,-174,492,555,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sinh():org.apache.commons.math3.complex.Complex",
            new int[]{671,-867,426,-701,413,690,767,45,323,475,1000,-249,-1000,510,628,-385,-293,-46,227,208,365,-122,465,871,318,-170,-243,-940,214,-723,-590,-409,638,-178,-314,7,334,131,1000,172,-955,-1000,-413,-244,-562,-734,1000,-676,-324,-308,147,-232,-871,-12,-819,23,-320,-102,-927,-568,-1000,-76,185,-367}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt():org.apache.commons.math3.complex.Complex",
            new int[]{-90,-938,-694,383,-806,4,-298,983,-244,763,-149,711,-745,-58,303,776,402,-615,-983,206,914,-158,-137,-409,-556,-756,-135,-202,248,-758,-298,539,90,455,411,361,-535,-51,473,-385,613,-144,-519,-735,975,-204,-531,-965,780,-273,625,291,-762,263,603,339,-508,71,-64,-663,-120,994,155,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt():org.apache.commons.math3.complex.Complex",
            new int[]{1000,193,-715,696,24,1000,-942,646,584,1000,-272,1000,731,539,-300,-882,208,-939,332,-669,-923,643,957,923,1000,-580,-667,497,1000,73,-550,701,135,-956,402,-393,198,-435,319,303,863,-848,25,-84,1000,516,192,14,-797,-528,397,-666,-413,1000,-465,-908,-532,-215,167,-1000,879,162,-778,-874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt():org.apache.commons.math3.complex.Complex",
            new int[]{366,758,128,811,38,762,-110,148,469,315,-279,147,430,909,-346,-261,-44,653,692,334,99,-194,42,-440,805,-476,437,896,-660,-624,237,504,-585,986,397,211,759,-792,640,619,255,682,201,611,956,308,364,-855,320,-800,678,156,-197,-967,224,-366,606,-670,-413,-496,799,227,296,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt():org.apache.commons.math3.complex.Complex",
            new int[]{-656,-976,-753,300,379,1000,1000,-328,-1000,1000,898,-455,-86,1000,545,923,115,1000,-493,98,1000,-624,-1000,602,-848,-664,379,667,-1000,758,348,-690,502,-283,762,-42,-706,-1000,575,228,747,198,-253,-669,861,-1000,-600,-1000,1000,-509,366,726,-566,-409,-981,-451,-287,69,-1000,1000,-543,247,217,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt():org.apache.commons.math3.complex.Complex",
            new int[]{173,-281,-385,730,99,153,389,-279,-795,-180,-324,-827,475,443,-1000,-653,-1000,1000,-593,779,444,-985,-1000,130,-601,-259,1000,739,-1000,629,-1000,463,647,578,-1000,-198,-160,-70,59,531,916,-87,607,346,36,-1000,-713,-923,-203,168,63,322,500,-1000,233,716,1000,-955,357,714,-252,43,-127,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt1z():org.apache.commons.math3.complex.Complex",
            new int[]{116,-1000,-1000,353,-567,-236,-1000,20,-157,280,675,419,358,905,-545,-257,-482,-216,964,-386,-383,-96,1000,-148,135,612,1000,-448,-332,132,635,-1000,-846,-614,-864,-392,-1000,888,394,184,110,-45,1000,619,-620,-587,796,122,-1000,-663,963,-1000,655,-460,-1000,1000,-671,244,-100,-205,892,472,602,55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt1z():org.apache.commons.math3.complex.Complex",
            new int[]{-566,1000,-941,-425,-571,324,834,433,1000,-41,-91,1000,-637,1000,457,-801,-391,231,563,-820,-923,-1000,1000,-427,675,749,53,-665,461,-661,1000,-953,-1000,-1000,-1000,-88,-1000,800,-99,495,-143,-392,966,1000,-1000,-1000,219,1000,-1000,-655,-1000,-20,674,-595,-1000,1000,-1000,453,864,-373,1000,1000,-76,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt1z():org.apache.commons.math3.complex.Complex",
            new int[]{782,-531,-578,-728,-85,940,659,776,-891,-647,-58,-266,-279,-194,164,-153,-673,273,170,-986,315,-569,-193,646,-795,103,323,-424,871,-726,-400,763,-577,-333,-657,286,-438,892,404,-555,900,300,-824,181,960,849,187,-527,340,-480,-126,-96,-117,-874,-111,-850,592,710,-790,806,516,-840,847,649}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt1z():org.apache.commons.math3.complex.Complex",
            new int[]{-1000,-223,294,1000,-783,608,-241,682,300,414,325,-1000,1000,-433,473,-76,-1000,883,-29,1000,-29,414,-1000,341,-1000,-1000,-1000,624,-344,1000,-1000,1000,1000,544,1000,-820,1000,-1000,839,-1000,1000,584,-1000,-1000,1000,700,1000,-1000,1000,-644,-252,-1000,-1000,69,1000,-1000,370,-720,176,1000,-1000,-1000,1000,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt1z():org.apache.commons.math3.complex.Complex",
            new int[]{-706,-1000,7,865,688,1000,-636,100,1000,-1000,211,-85,416,-1000,230,373,-915,-287,372,-769,952,-544,-1000,213,-1000,-885,-105,-522,944,-1000,-423,325,585,887,714,-63,1000,-834,1000,204,-202,1000,-371,-362,693,1000,861,-774,294,-740,-274,-330,1000,-1000,422,-503,-809,723,198,-5,-12,-824,1000,819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "sqrt1z():org.apache.commons.math3.complex.Complex",
            new int[]{-329,-171,1000,-1000,-100,351,9,-215,742,-381,-49,-488,131,-741,93,-571,-838,1000,1000,878,1000,-1000,-545,91,-500,-1000,-1000,912,375,-144,-242,-739,637,194,-1000,1000,-254,-929,-819,-527,-715,-149,-1000,-1000,712,482,717,-470,-374,724,-414,-754,449,1000,165,-1000,-370,975,813,1000,-388,-301,760,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "subtract(double):org.apache.commons.math3.complex.Complex",
            new int[]{828,-319,-730,1000,-598,-1000,98,-184,-1000,592,-965,291,-275,786,340,-29,549,-941,-827,-196,-714,942,-706,-737,-406,1000,716,-95,-761,1000,1000,-890,453,1000,-823,-1000,662,810,-1000,-310,-1000,-436,1000,-1000,377,-341,659,1000,1000,-582,1000,551,-477,-464,-1000,1000,-147,1000,673,610,-834,121,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "subtract(double):org.apache.commons.math3.complex.Complex",
            new int[]{-966,-449,-737,1000,185,331,34,473,310,516,418,-1000,-771,1000,-221,-215,563,-916,-21,1000,1000,181,1000,1000,-152,1000,1000,1000,595,-1000,511,-1000,747,1000,-737,-538,-81,604,1000,748,-876,-443,925,-1000,-1000,851,-891,-31,31,-1000,1000,-672,648,288,860,535,1000,11,332,210,-1000,-520,815,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "subtract(double):org.apache.commons.math3.complex.Complex",
            new int[]{279,764,803,-348,719,-1000,-395,-713,-630,1000,186,504,1000,-1000,569,463,1000,1000,432,-1000,488,143,479,185,-1000,864,1000,-296,-991,581,-4,-1000,61,400,666,293,660,-1000,-869,1000,-823,-274,1000,589,355,1000,-257,-213,638,-595,1000,720,268,1000,1000,-1000,-478,-357,-1000,-62,398,-163,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "subtract(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-883,259,-1000,-986,-649,-294,19,-166,-1000,-1000,602,-14,619,1000,-400,-547,221,31,-30,-986,-1000,-1000,106,1000,-1000,-1000,-802,281,647,1000,-1000,798,-873,-61,-1000,1000,122,-274,919,1000,1000,-378,-1000,-159,406,1000,-1000,-1000,-154,-1000,-217,934,1000,-555,38,-1000,246,-712,400,-72,-1000,873,208,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "subtract(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{111,13,-254,110,-1000,847,809,-556,376,1,431,-1000,-428,233,134,222,-711,-62,458,954,-1000,614,570,636,410,-318,271,-462,-1000,556,-835,-89,-547,924,593,-1000,-789,-116,-157,973,1000,643,-938,-646,743,-69,5,188,-715,-538,-632,-612,502,1000,-296,-271,-33,954,692,956,-1000,-295,445,213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "subtract(org.apache.commons.math3.complex.Complex):org.apache.commons.math3.complex.Complex",
            new int[]{-747,758,-878,922,427,-545,426,-237,410,-533,730,-751,667,269,-102,307,-769,885,663,-579,946,928,858,-855,488,831,218,-963,159,-361,-376,-399,-582,42,353,-890,127,134,-260,98,572,-156,-512,715,472,902,-192,156,356,778,532,946,20,-893,-574,-25,36,-236,7,870,-268,-992,357,560}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tan():org.apache.commons.math3.complex.Complex",
            new int[]{1000,926,-296,-921,454,-163,-859,248,-442,721,-237,-1000,429,-616,115,-353,1000,1000,-272,-797,-494,-1000,95,-910,627,-1000,-678,666,-440,-947,-891,-523,-937,885,247,-1000,1000,511,-1000,-759,584,-1000,-575,466,1000,-517,-499,516,-565,540,-471,1000,628,638,-810,-428,682,101,998,208,186,92,-1000,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tan():org.apache.commons.math3.complex.Complex",
            new int[]{-722,811,871,-875,-1000,55,-163,162,-363,-377,763,-41,354,-290,-864,-1000,-288,606,-112,-253,115,580,-1000,-625,-244,-402,413,-1000,-244,-418,343,-116,551,817,-665,-1000,1000,-937,-612,1000,106,42,140,-211,-151,94,-362,304,420,691,602,454,-713,-669,-127,1000,-76,1000,74,995,-1000,535,107,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tan():org.apache.commons.math3.complex.Complex",
            new int[]{-1000,-539,1000,394,152,-524,-547,122,343,199,272,-483,-1000,-463,-1000,1000,-370,3,1000,18,-1000,-747,-811,-1000,-1000,-381,1000,409,216,-825,-582,-1000,-180,85,-780,514,573,-121,-618,-723,1000,1000,-1000,1000,230,498,-26,1000,196,-1000,1000,-1000,562,295,-1000,-200,-243,288,1000,-300,906,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tan():org.apache.commons.math3.complex.Complex",
            new int[]{-366,-783,-1000,-444,253,-1000,328,27,679,870,210,1000,36,826,334,-605,1000,80,85,60,-555,1000,623,900,-264,1000,-48,-73,173,1000,1000,1000,779,-731,-781,-1000,-1000,-671,691,645,-728,-1000,-575,-1000,313,248,303,-1000,-560,977,-1000,1000,-987,-1000,814,-73,360,-471,-1000,71,106,-964,1000,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tan():org.apache.commons.math3.complex.Complex",
            new int[]{1000,1000,-761,57,-486,-630,-512,800,-1000,209,486,-1000,639,-521,-219,-373,1000,1000,-871,-281,-764,-871,314,-1000,413,-1000,-174,-1000,772,-795,12,-179,255,1000,-191,-1000,1000,16,-1000,543,115,-1000,-1000,557,672,-625,-639,-337,-35,295,-312,1000,0,613,-692,-197,716,487,1000,-1000,-1000,-292,-448,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tanh():org.apache.commons.math3.complex.Complex",
            new int[]{-180,794,667,794,-301,402,556,-21,312,-63,307,-979,-125,971,-765,384,-507,274,-345,-883,-434,-560,887,355,-26,472,481,-604,-406,-790,321,-868,556,580,677,509,128,-250,-749,-435,64,-461,803,-357,-123,620,21,-632,573,-531,-32,125,-903,-667,663,931,997,-9,-873,-159,-390,580,-422,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tanh():org.apache.commons.math3.complex.Complex",
            new int[]{-10,1000,322,847,-166,593,39,-191,107,1000,321,-455,-314,211,1000,-48,-315,76,-635,-65,-137,-690,-288,115,-348,396,68,206,-420,-423,404,-573,281,533,534,647,-10,-172,-530,779,-215,103,435,93,-30,103,-1000,1000,725,-487,77,102,-580,-767,-890,-850,19,77,-676,-179,-538,588,-50,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tanh():org.apache.commons.math3.complex.Complex",
            new int[]{-205,-186,335,846,-897,-298,584,-214,543,-493,567,-273,417,1000,-65,143,-958,984,605,211,519,-126,50,-96,38,603,-197,-51,-347,550,716,-408,244,382,-178,509,260,-565,231,-377,385,-420,631,-52,17,-925,54,1000,782,449,-681,-241,-346,22,536,8,535,-112,-333,-362,-276,589,-422,139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tanh():org.apache.commons.math3.complex.Complex",
            new int[]{111,-780,265,-700,-182,323,146,-22,389,-1000,-717,-1000,433,-363,-54,-460,-704,-989,298,766,879,-35,-673,921,79,-592,894,771,-638,-260,-651,-229,102,-463,-43,-498,-316,-196,824,-1000,770,-920,816,75,-269,85,738,-400,1000,1000,-227,770,10,-400,-192,-951,403,171,-1000,-137,-986,162,119,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "tanh():org.apache.commons.math3.complex.Complex",
            new int[]{984,-303,-893,365,-840,-771,378,243,532,172,-186,1000,728,-448,580,163,994,1000,-919,-408,-465,650,-66,373,-10,757,323,126,1000,-359,11,360,-1000,-555,-1000,1000,331,-117,-484,1000,-1000,1000,-1000,-673,231,-967,-707,984,152,678,131,-767,1000,1000,-140,303,495,20,1000,-1000,1000,-1000,250,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.String:KC05LjIyMzM3MjAzNjg1NDc3NkUxOCwgLTEuMCk=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "toString():java.lang.String",
            new int[]{-948,-685,343,-798,589,-886,548,-792,-66,-58,11,60,-682,889,445,415,-226,181,993,835,744,-677,310,-671,-69,-466,-210,-573,377,591,755,-815,22,-607,-845,-690,652,590,348,-677,43,412,-139,106,-616,-500,-304,-494,-418,953,-312,745,965,106,-282,-678,-960,582,-836,810,472,-911,864,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.String:KDIuMTQ3NDgzNjQ3RTksIE5hTik=", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "toString():java.lang.String",
            new int[]{-286,-273,-77,-225,1000,-41,789,-789,-883,-915,1000,921,-467,889,863,-217,-840,-1000,-1000,178,417,390,1000,-1000,553,-1000,-1000,-259,-74,1000,444,326,-417,-386,-1000,-147,1000,253,-632,447,391,728,576,559,-385,819,-1000,390,-553,331,-1000,479,492,426,-345,52,376,-217,-550,-244,-546,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.String:KEluZmluaXR5LCAwLjAp", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "toString():java.lang.String",
            new int[]{1000,-582,-995,428,-691,895,32,598,-748,-1000,1000,1000,-254,-126,271,-403,-1000,-1000,-13,-1000,-696,1000,265,498,495,-607,-523,229,-1000,94,-387,1000,-417,425,28,-220,1000,-1000,-1000,954,7,-30,651,134,1000,555,-149,648,-539,-1000,-277,693,-592,-868,-728,847,1000,-889,916,-673,-666,-778,-761,810}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "valueOf(double):org.apache.commons.math3.complex.Complex",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "valueOf(double):org.apache.commons.math3.complex.Complex",
            new int[]{-413,823,33,821,152,-877,-469,975,-254,773,631,830,968,385,862,-518,-871,-976,-670,49,386,-588,350,-372,309,-798,899,-235,-362,-405,-633,338,-391,-821,350,314,674,-136,351,735,-308,-955,142,427,-224,-275,-690,-539,-195,215,-996,-817,-624,-735,298,742,-582,974,-283,492,-935,691,466,384}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "valueOf(double,double):org.apache.commons.math3.complex.Complex",
            new int[]{569,-181,-199,-951,343,747,137,-301,448,348,836,-187,921,449,-385,-835,-549,-250,-47,750,-253,537,-127,417,-40,-985,329,964,-441,-874,639,946,-363,-818,-706,566,-518,-101,-200,-102,-571,-502,779,466,509,402,-563,-304,-798,787,-240,-847,751,725,-707,-382,889,-999,-800,-962,-401,804,-530,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "valueOf(double,double):org.apache.commons.math3.complex.Complex",
            new int[]{819,973,573,787,368,590,-196,-812,-815,502,525,32,-413,-536,470,-480,-224,427,-893,-222,741,682,-749,126,-931,-354,387,-199,6,-399,31,846,-311,155,427,-66,-700,446,-833,850,-885,187,-114,-933,895,-470,-894,390,864,435,-510,999,662,727,328,398,77,345,881,-266,-899,-552,-622,-236}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.complex.Complex", "org.apache.commons.math3.complex.Complex", "valueOf(double,double):org.apache.commons.math3.complex.Complex",
            new int[]{487,415,-845,406,-48,-950,-540,252,596,998,-306,645,-236,-697,-126,-938,390,-594,494,521,-36,982,-654,492,642,-591,433,677,-353,-503,-889,656,-314,-442,597,493,967,-848,198,353,-417,918,-736,842,-475,-220,734,393,-605,-473,354,-675,7,-738,195,386,-512,539,-87,580,252,163,367,844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.analysis.solvers.LaguerreSolver", "org.apache.commons.math3.analysis.solvers.LaguerreSolver", "solveComplex(double[],double):org.apache.commons.math3.complex.Complex",
            new int[]{473,-878,-230,-869,950,482,-897,358,426,517,734,76,-10,138,-228,-996,-596,-837,40,-364,-221,669,980,-80,571,-367,319,-193,-313,881,887,-788,898,-279,-969,221,-67,-634,-105,-19,-354,291,-468,-312,-533,-28,-741,-733,1000,-859,-3,962,37,627,-597,-137,-424,-845,537,-366,438,-971,-452,-852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math3.complex.Complex", DEReplay.run(
            "org.apache.commons.math3.analysis.solvers.LaguerreSolver", "org.apache.commons.math3.analysis.solvers.LaguerreSolver", "solveComplex(double[],double):org.apache.commons.math3.complex.Complex",
            new int[]{751,-746,281,-278,883,-585,154,172,292,179,-790,-864,-464,-195,486,-441,-717,-987,-252,-737,477,794,607,497,-72,776,-85,636,-855,-552,-677,-637,648,896,996,931,142,443,280,747,-992,-394,-184,-555,-670,-883,57,856,-649,-594,433,847,686,917,-466,-149,-133,-117,-426,-866,-107,795,127,-729}));
    }
}
