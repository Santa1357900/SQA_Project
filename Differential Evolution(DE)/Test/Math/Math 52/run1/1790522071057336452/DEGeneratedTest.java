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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Rotation", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{-255,321,0,1000,37,652,562,-1000,-873,-1000,1000,547,-98,0,-473,-273,43,1000,-1000,309,-192,0,-576,-149,596,-1000,387,1000,876,284,1000,617,569,-1000,-918,0,86,0,583,-1000,-1000,699,-409,-1000,427,1000,545,-1000,-651,110,288,967,-656,659,-216,-163,-820,1,681,1000,284,668,362,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{-1000,-1000,1000,-378,-1000,98,-671,910,84,-420,57,-1000,-838,180,111,-79,-1000,-1000,11,-963,-76,128,1000,-1000,-1000,343,-1000,475,-276,1000,1000,-1000,-874,1000,34,-974,1000,694,-562,-337,1000,-1000,-1000,-481,403,-396,1000,178,-1000,865,-743,1000,94,-1000,-1000,-589,303,318,-439,-1000,672,1000,-306,-751}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{725,-967,295,-478,-1000,172,10,-290,177,1000,-404,-794,-258,421,-503,-1000,-563,-1000,-252,-45,1000,388,181,403,780,721,-815,1000,1000,809,1000,790,-1000,-63,-340,-1000,1000,258,536,580,61,603,-1000,196,-208,-503,322,270,-1000,1000,-911,-174,278,-354,211,-1000,706,482,548,-20,-1000,1000,-76,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-558,-423,615,-932,-134,-294,-447,575,-12,-434,32,-908,-333,264,431,-339,-657,586,-781,510,-297,461,-7,513,238,535,-390,197,763,-875,-945,-583,-281,766,-53,513,416,-430,-544,604,-783,-284,789,514,130,518,-612,741,272,737,503,894,208,41,-79,38,348,898,267,-471,-786,-475,520,-925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-987,-764,-1000,53,1000,253,274,-1000,1000,116,306,1000,577,299,28,-43,497,437,-717,-920,787,395,139,-1000,1000,-269,84,411,-348,1000,5,487,-394,-67,201,-527,68,-932,-248,-544,-1000,542,752,-684,-97,215,721,176,541,-390,585,1000,326,-881,391,-773,1000,-1000,678,-791,-741,-735,-234,-933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{708,-1000,444,874,-651,1000,-1000,-46,964,-641,790,-301,-333,-888,810,133,456,-330,869,510,228,-425,-7,1000,-184,-62,1000,1000,570,367,306,379,50,766,-1000,389,-235,202,1000,163,909,-869,651,514,583,51,-612,622,381,1000,1000,894,-457,-430,244,-1000,-1000,1000,-627,470,-1000,-752,576,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyInverseTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-255,179,-882,-420,634,491,1000,-512,-1000,1000,-1000,-320,-608,-1000,388,-1000,80,-14,-494,165,-1000,1000,208,-1000,1000,-1000,-1000,-1000,-680,-850,-141,297,-583,1000,80,-1000,143,-850,-1000,-1000,40,-941,-1000,164,512,483,159,1000,287,-1000,105,227,417,234,-164,385,982,-1000,-234,-454,-327,249,386,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Rotation", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{-670,-21,38,-378,-1000,283,-346,940,-256,11,-698,-1000,971,-859,535,-278,-1000,68,271,706,609,448,-742,1000,-173,-177,1000,-1000,2,-753,-1000,-681,249,1000,1000,91,154,-351,-593,263,1000,1000,209,-442,618,831,-1000,-209,2,1000,-854,-1000,199,14,42,-477,-843,202,-78,-61,-1000,277,-1000,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{-1000,-1000,-39,-1000,741,1000,-784,-812,-488,-692,-1000,-561,1000,-1000,1000,639,-858,-431,-1000,-154,607,13,117,737,479,360,453,-1000,1000,704,-1000,716,979,-642,-1000,621,517,1000,1000,-280,301,-723,1000,-180,353,1000,669,1000,-1000,22,66,169,-1000,1000,-1000,-948,-288,1000,-863,50,-285,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Rotation", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{-995,46,862,-353,-603,406,-551,867,-641,-1000,-362,-1000,323,-607,1000,-415,-985,-542,-678,1000,385,812,-920,269,68,-373,1000,-509,253,793,-158,-610,910,-396,1000,-42,7,480,320,-119,1000,328,-308,409,-604,-805,759,285,460,-386,-1000,-531,-569,-385,-447,-378,529,43,662,188,-1000,303,-408,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Rotation):org.apache.commons.math.geometry.euclidean.threed.Rotation",
            new int[]{-208,553,-1000,-1000,-1000,712,-351,186,-545,-772,-648,-795,-775,-386,1000,-1000,-525,-493,-604,196,-153,1000,-597,1000,582,-1000,1000,1000,-405,-1000,487,-1000,-1000,-639,1000,189,1000,-1000,-1000,-140,1000,1000,-707,-385,-14,-486,1000,162,-1000,1000,-161,-1000,-233,661,-875,-672,-1000,624,597,1000,-1000,71,-536,672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{607,849,135,889,280,592,-35,71,625,-883,-649,-443,-636,-549,482,934,685,-269,238,649,-868,329,-145,-893,-107,-653,307,836,-249,978,997,-952,-585,818,688,656,723,630,315,400,-680,303,-703,-898,779,777,260,-727,-131,560,-253,-675,697,136,371,-580,-213,-964,-861,-332,800,-364,-766,806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-197,-932,-2,-523,674,-515,876,545,-1000,588,470,519,1000,-387,307,347,39,-98,319,-144,915,8,216,904,1000,-324,-690,-1000,-325,-19,-1000,112,317,700,-1000,-209,826,-701,1000,417,1000,-656,977,969,-910,-744,-747,-928,-533,-170,-793,1000,503,112,-256,-362,532,27,90,68,-1000,372,421,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-38,-310,-1000,1000,280,-396,-212,237,710,1000,50,-1000,-242,-173,954,870,-1000,205,-476,-843,-212,-1000,-388,-150,520,-1000,150,-560,1000,-427,1000,-1000,1000,1000,-1000,-32,-1000,-389,315,-855,-538,-82,-665,-28,87,877,260,-226,-396,560,-430,-675,697,994,193,-850,27,-1000,-844,-100,-742,62,-1000,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "applyTo(org.apache.commons.math.geometry.euclidean.threed.Vector3D):org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{353,407,885,-1000,95,-331,-743,1000,-418,-650,-492,-163,41,-542,570,-83,1000,-1000,1000,-720,1000,-27,-1000,-178,-93,-467,255,-851,-1000,-804,-1000,199,723,423,-248,-138,1000,338,335,-999,710,-344,-59,602,-481,942,-305,158,-565,1000,-558,-666,-73,827,-79,728,-652,965,-1000,-1000,-772,127,339,-10}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{525,882,67,67,-346,1000,-1000,-1000,1000,-175,184,1000,1000,-1000,-1000,-496,742,827,1000,-1000,-274,-1000,-291,-1000,-804,-1000,-613,-1000,-563,-200,1000,-1000,-584,150,-555,-278,1000,133,880,-1000,-1000,1000,-1000,-790,1000,365,1000,-32,565,-671,101,-331,-688,1000,-1000,-1000,1000,-263,-402,-298,-689,557,867,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{0,-429,-284,1000,632,795,-1000,-1000,1000,260,28,-391,-902,-1000,924,0,-791,137,0,0,0,-1000,423,0,-406,1000,0,551,-516,903,1000,-340,-1000,-408,-905,-638,-659,134,-656,380,-683,408,424,162,-536,-710,1000,1000,864,912,-887,-242,0,1000,-1000,800,645,524,-1000,-643,826,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi40NDY1NDE1ODQ3OTgyNzU=", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{779,-80,95,-610,1000,774,-491,1000,444,-1000,-495,-369,-841,-210,752,406,-714,-693,-702,695,-77,766,-671,1000,-529,608,558,-217,910,674,-1000,-487,-876,194,-64,1000,770,1000,-1000,-1000,1000,-1000,934,-1000,-1000,-549,-1000,27,-1000,100,26,310,400,-930,-453,1000,98,1000,389,-627,-993,207,-545,-90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{-26,736,419,156,-1000,689,-633,76,-729,-433,-80,1000,-447,125,610,-821,-160,-360,34,-1000,-313,-544,-571,630,-518,114,-401,-1000,-1000,1000,-755,106,-1000,-1000,487,-1000,48,931,1000,-470,-250,421,-708,155,315,671,-1000,109,1000,873,54,-1000,-144,162,-1000,-344,806,193,654,85,-561,826,110,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xMjA4ODEyMDA3NjM3OTE2", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{-837,-849,-42,661,900,216,-116,-942,955,-948,283,-920,57,-295,137,982,252,-259,-219,-302,-418,-468,907,176,45,634,-967,-995,533,-6,845,-853,-869,-224,310,-344,-49,183,-3,908,464,795,-340,20,247,694,805,932,948,919,-287,900,346,607,176,58,430,649,-935,-884,585,-405,785,296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{547,-649,-145,-612,325,1000,-92,-857,-513,-1000,-1000,-77,382,504,-95,-218,-945,1000,105,-351,590,458,-213,1000,-940,-356,1000,-497,-1000,399,-1000,-1000,-1000,165,593,-870,549,625,1000,348,-573,383,254,-567,102,1000,-992,-1000,1000,-222,570,-172,-897,-457,-507,-1000,1000,-824,-752,201,-1000,401,475,442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{1000,-649,-543,675,-1000,-774,901,-751,-848,-1000,765,278,-59,-214,22,181,356,-550,-399,-93,1000,-1000,-381,-964,-541,131,-618,603,-783,-405,563,-807,-727,332,-5,-442,-854,722,1000,-784,-1000,456,-124,871,525,1000,841,-1000,671,-494,558,89,-812,807,653,-963,649,-1000,-140,212,-1000,-504,-1000,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{1000,-449,-996,-203,1000,779,1000,1000,723,-1000,-323,-1000,-27,-24,-735,614,-735,-847,-1000,210,636,899,147,833,-324,358,1000,-1000,261,-69,-1000,-1000,892,57,546,193,1000,890,-929,1000,912,-718,-1000,-1000,732,1000,-931,545,-315,974,-735,1000,350,-1000,-769,922,1000,1000,844,-505,-430,227,804,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi43OTY5MjAyNDg1OTU4MDc2", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "distance(org.apache.commons.math.geometry.euclidean.threed.Rotation,org.apache.commons.math.geometry.euclidean.threed.Rotation):double",
            new int[]{-1000,1000,743,1000,325,486,1000,369,-165,477,245,505,-1000,-561,1000,-155,688,-927,-223,808,-215,-1000,-860,-1000,-785,663,509,298,-1000,1000,-1000,492,-1000,431,-785,777,-399,-285,1000,-1000,-657,1000,289,-297,751,1000,-691,-99,93,-456,-22,478,-540,1000,-991,1000,1000,826,1000,-241,-1000,-297,-950,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi45NDM2MDc5ODAzMzE4Mzkz", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngle():double",
            new int[]{-1000,-471,1000,-1000,-223,1000,1000,-1000,1000,-265,701,-1000,-576,1000,-1000,1000,-799,580,285,1000,-750,714,-528,560,-142,1000,-1000,740,-533,-20,-923,-1000,-1000,646,-1000,538,-1000,171,1000,1000,746,1000,634,-1000,911,-1000,-145,1000,-990,-452,-59,641,653,686,1000,-174,1000,1000,-399,-1000,695,258,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngle():double",
            new int[]{292,1000,-430,-322,610,-1000,-389,1000,-697,-980,604,-955,-128,-1000,967,1000,1000,554,755,-734,640,-287,-1000,-220,-1000,85,-1000,1000,1000,1000,843,267,896,-1000,975,850,329,-393,265,629,1000,835,-590,1000,653,116,-106,-564,367,-496,1000,572,172,554,-769,656,-542,-731,-1000,1000,-284,-20,-285,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:MS43NTM1Njc5NDAxNTE1NDE0", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngle():double",
            new int[]{-1000,-64,50,583,1000,-384,262,-103,-228,-440,-508,-346,1000,-852,-1000,527,666,498,-174,-721,484,-478,668,884,551,-353,-509,532,-505,-1000,576,-264,-1000,249,457,-1000,-987,-22,534,42,-871,-517,-1000,-287,63,196,-67,-326,515,-676,592,-961,877,597,223,120,-968,1000,740,-627,-172,-840,-307,-338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:My4xNDE1OTI2NTI2NTg0NzA1", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngle():double",
            new int[]{-499,-475,-852,181,-975,-584,937,-82,37,-347,-718,-474,547,311,707,-77,397,142,505,964,-770,241,119,-862,-375,412,117,-631,-2,-399,661,-162,-237,331,-828,-550,-517,384,-19,52,-766,-816,-766,-116,-731,-711,29,806,209,-805,-177,-136,-554,260,541,-532,234,-23,-536,42,13,45,6,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4wODQ3MTk1NzQwMDM1ODQ0", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngle():double",
            new int[]{598,189,717,29,478,-244,827,-621,230,-855,820,-344,-36,298,-695,-300,70,704,542,-27,596,-565,-547,-893,-27,-221,-219,-796,181,-916,-731,-485,309,-76,515,-167,-308,-907,288,452,-657,67,-481,849,218,579,914,398,-987,-276,-778,-644,657,621,519,-855,-577,-923,-404,-505,-302,-72,483,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{1000,-182,1000,377,-239,1000,211,887,664,-1000,-811,114,-1000,444,-605,-843,-1000,637,59,-1000,-1000,1000,-1000,1000,-1000,225,1000,958,739,-885,-1000,-713,1000,116,-1000,-181,255,-620,-925,-1000,1000,1000,140,589,-488,810,-1000,976,70,-463,563,-314,-83,1000,702,811,1000,506,1000,787,-305,-1000,412,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-1000,-579,251,-701,1000,24,-289,1000,-185,-159,-701,122,656,-482,-1000,-229,52,-6,-445,117,-430,177,155,1000,263,367,670,-318,945,539,14,-1000,-755,-930,137,-977,-1000,177,-862,-1000,909,208,1000,920,217,360,-131,-157,-878,452,609,210,887,722,-503,473,-348,-354,-197,1000,-604,-16,-59,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{965,-1000,-470,624,-492,574,956,-528,-726,-951,-1000,-1000,-822,217,-623,-260,962,-166,-329,341,-705,-707,-990,1000,1000,-121,1000,-648,1000,-950,-205,-212,-50,-48,-814,-243,-137,-215,-225,-368,942,1000,-233,195,-545,-1000,-1000,381,-1000,-17,-512,507,921,-496,193,971,611,695,858,1000,55,1000,102,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.geometry.euclidean.threed.CardanEulerSingularityException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-1000,1000,-1000,-308,1000,-1000,22,1000,-1000,1000,-1000,-146,1000,-751,-276,-864,682,-1000,-88,1000,1000,990,1000,957,1000,-1000,-1000,-1000,1000,-158,301,345,466,-139,1000,-817,-644,-425,1000,-562,354,-1000,1000,-244,1000,1000,-103,-1000,-556,193,1000,-1000,-1000,744,165,-739,-530,-1000,-942,-282,-441,-1000,-121,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-911,-883,1000,624,-492,584,-1000,-528,-423,422,-6,858,0,-921,959,-856,-1000,-838,460,-1000,-879,949,411,-490,-268,1000,-4,659,-1000,-291,73,-212,-39,-737,871,922,-137,-300,-354,-1000,289,-1000,-424,-202,1000,980,1000,1000,999,857,-512,253,1000,485,-383,-183,-525,-424,-1000,-217,-349,-964,-600,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{1000,-914,1000,-509,-1000,1000,396,725,1000,-957,-431,1000,-1000,1000,-434,-901,-254,1000,-203,-1000,-1000,433,-1000,254,-1000,498,1000,1000,-456,318,-1000,-367,357,-48,-1000,300,433,-450,-663,-635,876,1000,523,124,-1000,318,-1000,1000,722,-1000,140,-540,994,1000,687,627,1000,682,1000,357,-604,-337,-258,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-909,1000,-1000,-795,1000,-695,217,1000,-1000,-299,-1000,-593,1000,-200,-1000,-991,-693,461,-966,1000,-785,248,1000,-431,1000,-53,-194,233,1000,-123,-1000,-649,1000,260,920,-1000,-1000,241,139,-1000,1000,153,1000,988,-100,-297,-907,-1000,-349,-586,323,-73,-1000,834,-1000,-18,-777,975,-143,1000,63,50,-156,652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-35,-152,432,-586,-1000,-691,335,210,78,621,29,1000,792,1000,-447,-338,-416,-296,-253,-1000,450,712,-14,-409,-1000,-1000,88,704,-871,867,-652,631,-86,-613,-554,-689,-832,-110,1000,266,1000,5,1000,1000,-1000,1000,1000,-146,788,-1000,207,-1000,-830,1000,337,-495,729,-719,512,-495,-704,-1000,-1000,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{1000,-182,-1000,-810,-910,-1000,-42,887,-846,-118,-482,187,-822,-965,-623,-215,962,-724,110,605,1000,1000,760,1000,403,-1000,-132,-198,-500,-950,-592,-162,-926,-710,-333,-1000,-18,-709,-225,-275,-224,-741,1000,1000,244,1000,1000,-1000,-987,-118,657,-1000,921,-948,1000,888,-661,-341,858,224,-1000,-993,-650,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:45:java.lang.Double:LTEuOTE4MzMyMTkzODgzMDI4Mg==:45:java.lang.Double:LTEuMDExMTA3NTAxMzg4NDk2Mw==:41:java.lang.Double:MS4wNDcyNjk3MTI3MDk3MDE=", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-133,1000,-1000,-1000,-206,-1000,-11,1000,-36,629,-958,1000,1000,-375,-775,-1000,528,342,-47,1000,707,1000,536,1000,905,-703,-1000,-960,-836,165,873,-207,-59,-81,979,-827,-659,-1000,623,-733,1000,-1000,1000,794,-264,1000,-69,-1000,860,-1000,1000,-891,-358,1000,1000,-385,314,939,-480,-356,-655,-1000,-416,749}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{1000,604,1000,-521,-1000,-706,1000,-678,235,517,-530,1000,-1000,1000,862,1000,993,1000,-515,-303,451,-592,-1000,-154,-1000,-1000,-126,1000,-1000,361,112,-1000,145,-412,-1000,-413,1000,-189,1000,1000,918,1000,664,673,-1000,351,-331,682,1000,-1000,1000,-1000,354,-1000,440,-997,1000,1000,-769,1000,249,-540,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{1000,-536,1000,-734,-1000,621,92,440,821,-1000,-1000,-42,-1000,840,270,-990,676,1000,126,-1000,-1000,497,-1000,217,-1000,202,1000,1000,1000,-140,-1000,-946,1000,-159,-1000,131,403,45,-1000,-1000,1000,1000,744,60,-1000,-334,-1000,1000,603,-937,-625,-489,-137,1000,876,1000,815,343,1000,844,-560,-346,-741,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{499,-302,-1000,444,-845,-306,22,210,966,58,-131,795,-508,-190,416,-645,845,-77,786,485,132,1000,227,-929,-818,-421,-438,-164,-896,-449,-339,235,275,-156,-396,-245,1000,-110,729,-169,22,-1000,-254,-244,-256,1000,-386,-552,788,124,207,-989,-338,893,234,-71,-79,103,-1000,-890,-813,-1000,-121,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.geometry.euclidean.threed.CardanEulerSingularityException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{1000,-691,1000,-1000,-920,905,223,659,796,-1000,-431,921,-925,1000,-1000,-1000,1000,1000,-402,-1000,-1000,276,-719,-446,-153,419,1000,121,-456,646,-1000,-935,720,-170,-994,-555,1000,-433,139,-1000,1000,1000,523,733,-1000,-232,-1000,78,838,-1000,-325,-447,843,831,25,1000,405,-76,1000,1000,-491,-3,90,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.geometry.euclidean.threed.CardanEulerSingularityException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{830,-499,465,68,86,265,-546,675,-609,673,529,-270,-125,-786,199,-641,-457,108,353,136,-260,-225,-869,-951,796,519,546,-45,68,-596,43,504,262,750,-432,-935,607,-283,-750,202,237,50,-698,-94,-53,-812,-596,-18,-597,703,-542,703,-180,-718,-608,219,-839,-681,436,19,-30,615,202,239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.geometry.euclidean.threed.CardanEulerSingularityException", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAngles(org.apache.commons.math.geometry.euclidean.threed.RotationOrder):double[]",
            new int[]{-699,203,-1000,1000,-36,-487,122,1000,-977,-682,-956,-704,1000,-1000,-1000,76,589,-220,-283,1000,-161,399,1000,1000,1000,411,-70,-1000,1000,107,578,-1000,163,-1000,1000,-744,-1000,103,1000,-380,305,62,1000,1000,805,1000,474,-472,-1000,81,1000,-506,-1000,620,60,468,-52,567,-881,1000,-566,-53,-140,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAxis():org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-578,789,-1000,1000,-830,356,-518,871,342,-1000,-769,1000,-764,-1000,997,-790,-150,-36,-896,959,203,331,714,-481,-643,-63,704,93,-323,-613,-150,192,1000,-773,-993,-374,-233,387,329,-1000,311,1000,-776,836,-130,817,-1000,685,756,-144,-179,150,-636,-869,775,-947,457,447,-395,-990,623,1000,-83,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAxis():org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{673,-596,796,206,266,-693,632,-986,-428,995,-769,-357,662,395,997,652,815,-231,-454,-875,-544,953,-869,199,820,-207,-797,-932,305,-613,513,-846,-264,950,-594,-374,539,-663,787,941,143,-427,-420,665,-130,-922,-500,-500,33,-278,396,-905,-33,316,-463,-947,-622,-294,-197,319,470,-768,972,57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAxis():org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{234,722,-370,965,-394,244,-55,871,-157,163,-865,-872,915,-134,-903,539,-680,448,708,296,-595,844,522,426,-143,-72,10,550,491,-412,-607,580,713,15,-607,-597,-192,-361,-992,175,540,458,-164,-642,-541,876,-627,-658,-189,568,-761,-853,-67,-593,-672,499,-358,-998,-463,-603,-713,-961,878,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.geometry.euclidean.threed.Vector3D", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getAxis():org.apache.commons.math.geometry.euclidean.threed.Vector3D",
            new int[]{-32,-157,85,-1000,-1000,770,-59,1000,920,1000,1000,317,-1000,1000,-1000,-366,-643,764,7,1000,1000,-1000,1000,-737,-951,130,-385,854,208,-420,-1000,-783,0,-1000,-117,484,-1000,746,-778,-1000,822,856,553,-156,0,945,1000,1000,-1000,1000,341,1000,860,1000,-742,-943,176,-1000,36,1000,430,1000,-985,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("ARRAY:[[D:3:149:ARRAY:[D:3:41:java.lang.Double:LTAuOTg1MjM4MjkyODg2NTA0:45:java.lang.Double:LTAuMDk5NTQ3NzA5OTMzMTc4NjY=:41:java.lang.Double:MC4xMzkyNjg2NjAwNjgwMzky:153:ARRAY:[D:3:45:java.lang.Double:MC4xNTE1ODUxODcxMTQyNzQ3NA==:45:java.lang.Double:LTAuODg1MzQwNjc3NTQ4MDEwOA==:41:java.lang.Double:MC40Mzk1MzgxODQ2MDU1Njkz:149:ARRAY:[D:3:41:java.lang.Double:MC4wNzk1NDUxOTAxNjAxNzAz:45:java.lang.Double:MC40NTQxNjA5MTY1NTQ3OTIyNA==:41:java.lang.Double:MC44ODczNjE0OTYwMDc0MDE5", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getMatrix():double[][]",
            new int[]{-638,1000,-245,1000,1000,1000,1000,-820,1000,-942,1000,1000,1000,-1000,1000,304,628,1000,368,1000,-893,882,860,-579,-1000,-628,-201,-794,-97,-1000,1000,-939,-183,1000,-1000,-83,-190,876,-17,-989,-214,1000,-1000,1000,1000,-1000,-1000,-1000,26,-422,208,962,-271,-1000,717,683,954,59,1000,420,1000,-1000,-177,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("ARRAY:[[D:3:153:ARRAY:[D:3:41:java.lang.Double:MC41NDAzMDIzMDU4NjgxMzk4:45:java.lang.Double:LTAuODQxNDEyMzk4Nzk4NzMyNA==:45:java.lang.Double:MC4wMDk5Mjk0MjIwMDQwMzQ2Mjc=:157:ARRAY:[D:3:49:java.lang.Double:MS4xMTAyMjMwMjQ2MjUxNTY1RS0xNg==:45:java.lang.Double:MC4wMTE4MDAwNzY1MTI4MDA1NzI=:41:java.lang.Double:MC45OTk5MzAzNzY2NzM0NDIz:157:ARRAY:[D:3:45:java.lang.Double:LTAuODQxNDcwOTg0ODA3ODk2NQ==:45:java.lang.Double:LTAuNTQwMjY0Njg4MjI0MjU4NA==:45:java.lang.Double:MC4wMDYzNzU2MDg1NDkyODY3Ng==", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getMatrix():double[][]",
            new int[]{-719,153,-528,38,-132,-923,-719,-611,114,-660,-585,-527,870,664,526,554,-570,-26,-166,-4,394,-274,-453,-136,-985,-990,-252,-1000,307,-43,165,989,-804,606,-14,945,939,235,416,832,696,427,-975,-828,664,624,103,410,500,785,-781,-43,-93,-89,970,991,-817,-353,511,456,309,971,-138,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("ARRAY:[[D:3:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getMatrix():double[][]",
            new int[]{520,-112,785,514,-460,-980,-588,-848,-148,-670,-404,-935,176,897,133,679,-373,-550,285,-704,402,-275,-103,63,-376,-101,-638,833,802,-393,589,-199,437,-352,-169,-694,208,-622,-189,988,-166,7,-104,624,255,538,-734,-632,-934,-78,991,-89,300,167,711,-925,581,576,241,784,-563,-901,-181,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("ARRAY:[[D:3:89:ARRAY:[D:3:25:java.lang.Double:LTEuMA==:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:89:ARRAY:[D:3:21:java.lang.Double:TmFO:25:java.lang.Double:LTEuMA==:21:java.lang.Double:TmFO:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getMatrix():double[][]",
            new int[]{-138,-91,1000,-458,-676,-371,-719,233,677,-1000,-34,191,852,-202,-163,1000,-570,-40,-400,497,-556,-233,525,-136,-670,-200,470,533,1000,-119,331,-469,341,-23,140,-305,-397,-1000,-670,1000,-394,-781,-18,1000,907,42,355,6,-901,522,1000,-43,-93,-336,-14,-324,790,-130,-253,455,-1000,-778,304,281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("ARRAY:[[D:3:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:85:ARRAY:[D:3:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getMatrix():double[][]",
            new int[]{-108,394,1000,957,-367,-1000,-706,-1000,-90,-1000,769,-1000,1000,937,224,679,-184,340,636,-423,1000,799,-177,-717,-788,783,-1000,-279,-425,-1000,680,-1000,941,154,402,-753,-744,-665,-1000,955,-292,223,-79,355,372,1000,-65,-99,-1000,713,75,445,345,140,951,-851,-223,289,-362,872,-1000,-930,-355,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getQ0():double",
            new int[]{-679,-219,-271,327,-85,-71,-267,-608,-264,-747,971,360,-551,807,128,-811,514,193,-97,145,31,-779,-406,-587,96,-259,247,274,404,-209,968,-157,-596,873,509,-738,241,765,-960,923,908,604,-859,-166,-421,124,471,-482,-106,963,-675,23,-820,687,344,524,977,-182,-988,-214,87,568,248,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getQ0():double",
            new int[]{1000,1000,980,530,-1000,-1000,-277,1000,203,1000,969,-758,135,-259,345,-538,1000,440,-623,-788,-107,1000,-918,-1000,641,1000,-1000,177,489,218,1000,-18,-1000,-553,-1000,-51,1000,720,-1000,-276,272,-683,-532,-1000,1000,400,1000,1000,488,-490,268,541,-1000,1000,-269,-1000,1000,71,-343,-1000,3,-76,-232,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4yNzM3MTc5ODExODQwODk2Mw==", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getQ0():double",
            new int[]{-805,-64,712,694,-130,8,4,-344,1000,456,-456,447,-745,147,1000,-396,1000,383,1000,-808,-819,553,-131,966,476,585,-762,820,240,-903,419,777,-1000,343,-456,1000,648,94,-403,-899,-1000,-636,145,-835,1000,-703,1000,604,318,-683,-1000,-409,-227,-435,102,1000,220,1000,-310,105,644,446,1000,68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:LTMuNDQ5OTEwMTg3NTM3NTkxRS0xNw==", DEReplay.run(
            "org.apache.commons.math.geometry.euclidean.threed.Rotation", "org.apache.commons.math.geometry.euclidean.threed.Rotation", "getQ0():double",
            new int[]{-546,71,-450,563,131,-163,591,-38,976,222,332,627,72,272,752,-191,944,986,935,30,-307,278,-827,-101,-98,407,-626,-362,763,-529,608,287,-528,260,-895,451,821,345,-146,-868,-849,581,36,-288,327,-575,986,486,265,-601,-901,-760,-336,-145,-831,-629,90,775,16,745,282,-220,-71,126}));
    }
}
