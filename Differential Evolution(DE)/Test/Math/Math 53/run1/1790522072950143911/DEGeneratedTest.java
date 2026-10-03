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
        org.junit.Assert.assertEquals("java.lang.Double:Mi40MTY2MDkxOTQ3MTg5MTQ2", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-135,184,80,697,22,634,673,931,-321,555,-612,-865,897,647,-167,914,309,395,-209,99,-689,-199,206,-315,463,-685,-194,738,-764,801,195,314,-503,695,410,905,610,-288,-683,-713,-101,-584,-997,-269,346,761,373,-456,-109,-671,-492,-393,-579,376,38,-792,-299,68,796,792,826,-709,-729,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{159,-446,-400,-558,-1000,1000,-621,-1000,47,-1000,162,-194,-500,-1000,-400,-587,-105,-1000,223,450,910,-1000,615,-249,-1000,-661,-1000,-946,-400,-1000,-506,1000,1000,-283,1000,126,-29,1000,-1000,459,-821,-7,400,-400,-1000,230,-1000,-471,-58,1000,503,31,1000,-400,-574,435,379,34,1000,-358,-654,1000,1000,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{665,715,495,683,-367,-135,549,432,34,410,-8,312,707,-998,629,-453,-720,-413,786,-367,337,355,479,-353,403,544,503,-905,749,229,-897,634,176,592,16,506,-504,-675,-911,-136,-744,-208,-568,-726,399,-621,-877,-341,26,65,848,-321,-810,640,-465,143,739,-738,47,399,650,-949,302,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "abs():double",
            new int[]{-385,42,-622,-683,525,19,-622,-768,587,513,689,1000,-1000,-1000,-1000,-1000,-1000,-1000,1000,45,1000,1000,1000,-376,-902,1000,556,683,-738,-781,-1000,1000,616,1000,960,-678,-865,128,-1000,-470,-975,-281,1000,-1000,1000,13,-320,-932,-1000,1000,-222,691,1000,-1000,-1000,941,1000,-1000,-706,1000,-1000,-666,293,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{1000,1000,1000,63,152,-296,848,-1000,-1000,1000,-492,842,545,-1000,-649,730,108,-1000,-1000,219,508,1000,-1000,-167,487,522,1000,-512,1000,25,86,-204,802,-1000,819,359,-290,408,-796,-204,-534,209,-513,623,357,-71,552,955,-1000,764,295,-743,-1000,-283,-615,743,-1000,-301,-42,-984,896,-1000,-399,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-541,339,-870,550,330,-124,764,-500,-837,-925,817,-30,825,148,653,75,-446,-202,64,-143,-139,-444,227,-513,238,421,422,-168,322,428,-755,163,-788,-996,-917,154,51,11,-120,-478,-305,239,887,672,326,-253,989,-472,184,981,-543,-638,-732,831,-462,-108,795,-72,-858,-825,495,406,101,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{1000,918,1000,-467,61,-648,525,-881,-1000,1000,-392,948,707,-1000,-294,728,-274,1000,-600,289,269,1000,-780,181,405,-295,529,-727,738,334,-535,-558,-220,-1000,1000,-886,351,1000,-1000,-744,-462,-233,382,284,797,-323,1000,812,-247,625,-201,-1000,-1000,-170,-66,-482,-1000,-375,-305,-1000,-344,-1000,-239,94}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "acos():org.apache.commons.math.complex.Complex",
            new int[]{-84,-977,-540,564,-303,475,829,-467,-186,-642,-681,800,-145,-305,678,-74,1000,968,-843,-854,566,-214,-122,-243,-703,185,735,-1000,-147,674,-306,210,1000,-784,199,-178,228,505,445,818,-166,632,-990,930,-65,427,397,1000,-1000,501,215,-704,-253,855,-268,-1000,93,668,-929,1000,694,163,-838,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "add(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-151,-982,-57,299,523,-896,-607,710,-682,-637,-214,-238,-833,550,961,-144,223,-404,-109,-620,-710,21,-430,187,696,-446,-301,625,-491,-616,444,87,-591,395,603,923,797,-989,464,723,-174,903,-654,489,-135,656,377,16,-337,-240,384,912,947,-231,973,136,539,346,-172,662,190,-964,-935,938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-1000,36,518,1000,787,-873,-882,1000,533,-486,1000,631,-784,1000,1000,740,83,1000,463,1000,-439,707,-1000,-424,411,423,-66,824,1000,-1000,991,1000,-1000,1000,41,1000,-72,-511,-1000,-1000,854,679,236,-1000,1000,734,-607,183,-727,477,-1000,1000,-604,-1000,1000,32,-1000,1000,1000,759,908,-509,-740,263}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-642,1000,-1000,554,-858,621,-548,-1000,-254,1000,-916,400,-45,-289,740,1000,1000,412,-1000,707,-1000,-669,-521,135,-757,-1000,153,849,-658,752,1000,5,1000,1000,-1000,315,-1000,-1000,924,-1000,-692,1000,1000,960,6,-524,-1000,-548,708,1000,-1000,180,-658,-635,-1000,1000,1000,46,-1000,777,-1000,232,598,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-429,-227,207,-669,-1000,1000,518,-963,260,1000,9,-687,-713,-1000,1000,-362,-890,-550,1000,-125,423,1000,664,157,-1000,-889,-154,-486,452,-411,-548,-309,461,416,1000,1000,1000,-423,1000,142,-205,-719,253,-272,943,99,-14,540,1000,557,-277,733,-596,-1000,-421,42,1000,-1000,973,669,-237,554,1000,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "asin():org.apache.commons.math.complex.Complex",
            new int[]{-87,1000,-1000,1000,216,91,-388,-781,-996,1000,-1000,1000,324,-390,-668,58,1000,533,-963,1000,-351,-422,855,-1000,920,-788,893,1000,-877,-835,227,1000,1000,1000,-1000,847,669,-673,-790,-332,475,1000,1000,-106,-60,862,-1000,-1000,-1000,1000,-1000,230,-1000,-419,-1000,1000,911,-997,-1000,1000,259,-977,-759,-641}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{312,-1000,-1000,804,1000,1000,-667,-414,1000,-942,246,216,8,616,-988,707,-799,-1000,-107,-141,646,-1000,-1000,-128,-953,1000,1000,759,216,-1000,37,848,-1000,-1000,-102,644,1000,730,-88,-159,684,905,506,-718,-263,1000,-594,-1000,-365,-395,202,-883,293,390,-639,656,-782,-46,212,-1000,36,348,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-748,-1000,-1000,804,217,812,348,-1000,-136,-955,-290,-334,871,1000,-288,1000,-751,-1000,831,-91,781,-1000,-890,-1000,-338,1000,1000,-14,-484,-829,37,1000,-1000,-1000,-371,54,1000,918,-1000,-159,-247,1000,303,-1000,455,1000,276,-1000,-75,-1000,-394,-1000,457,599,-1000,1000,-1000,-1000,1000,-1000,583,907,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-422,-1000,1000,529,-1000,482,949,-1000,445,-47,347,-681,-823,1000,-1000,448,-743,-704,-1000,396,-744,-999,5,1000,236,-924,222,981,-405,279,-1000,173,1000,-1000,-776,-831,-508,-582,-412,-1000,-655,1000,-542,-1000,-183,-196,242,-957,651,-609,-800,-977,0,-335,1000,-803,-150,-1000,1000,-417,-1000,334,-541,-921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-657,-1000,768,825,205,-817,-1000,693,-1000,-136,439,1000,1000,-637,-1000,-652,76,1000,1000,541,-989,-975,-802,739,-363,210,-903,1000,-880,-1000,171,-1000,-515,-1000,-1000,1000,1000,-669,1000,811,491,510,-1000,1000,1000,-1000,-1000,759,-1000,-279,297,-877,-665,-737,1000,-1000,-898,1000,-990,1000,749,952,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "atan():org.apache.commons.math.complex.Complex",
            new int[]{896,34,-491,389,-82,367,-122,-216,242,-707,126,-439,941,606,-844,-469,-123,-831,565,-895,313,-354,-295,-593,-759,355,297,797,-370,-494,-835,-940,-711,437,-29,-112,-339,398,166,-781,462,-155,-168,506,834,313,543,-959,389,462,948,110,412,106,-631,-89,340,707,397,-48,-396,151,200,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{-142,-1000,1000,773,286,-1000,446,683,-347,-451,215,-1000,-245,0,-292,1000,-536,3,563,-281,-602,-1000,600,1000,0,920,-416,0,-43,1000,-216,0,-1000,-746,-280,-846,-1000,149,-259,1000,1000,0,-861,33,111,-351,0,-1000,-1000,181,0,-607,-469,-233,-459,0,-1000,-149,-502,-738,1000,365,311,-357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "conjugate():org.apache.commons.math.complex.Complex",
            new int[]{-1000,-935,-263,776,-1000,-509,-407,-357,-1000,1000,1000,-1000,-1000,1000,869,150,-1000,-315,1000,267,5,1000,1000,-1000,1000,-1000,-10,307,-783,-776,-205,-620,328,253,-1000,1000,377,365,-1000,358,438,-1000,-1000,-458,394,-1000,-1000,944,209,-956,480,-545,596,337,901,255,-346,760,743,230,-1000,613,-995,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-1000,732,-1000,-21,1000,56,-271,195,-306,-612,-521,1000,614,-986,-1000,760,-1000,-907,777,293,119,-1000,-1000,-298,1000,-1000,-377,795,-151,-435,-336,-1000,65,1000,1000,445,-473,-1000,-1000,746,318,-753,451,831,599,-985,-201,1000,96,-970,-118,-1000,-796,302,1000,282,148,890,-643,1000,-56,352,1000,-606}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cos():org.apache.commons.math.complex.Complex",
            new int[]{-432,479,-771,282,-984,583,-247,62,-183,859,-864,251,57,656,428,333,823,493,-443,563,-653,380,-817,-861,-484,656,376,-340,-170,-432,-405,818,881,102,-882,543,416,-584,931,712,140,-365,895,-9,554,741,-28,-667,334,826,258,-720,-991,70,-381,300,52,450,-153,-917,-142,-559,-574,-990}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{233,-452,-883,284,-491,273,491,375,-358,-448,-669,455,32,450,154,-43,939,-910,252,809,-195,-210,-30,7,469,-496,-953,924,-177,-944,-878,-926,-50,121,-992,-253,-202,-622,-612,708,-950,797,-758,986,4,-353,-534,655,-996,-507,-67,492,-762,165,532,374,-426,493,617,-590,-503,467,721,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "cosh():org.apache.commons.math.complex.Complex",
            new int[]{28,-1000,-120,433,147,-269,754,615,-425,11,1000,571,793,-843,-876,-884,310,-558,1000,1000,-670,-886,677,1000,-1000,1000,-163,-854,-240,-261,-394,-944,-1000,784,511,-696,-367,1000,454,727,-1000,-62,-111,866,1000,-727,101,-460,882,-1000,785,176,-62,1000,390,547,-630,-1000,61,1000,705,-258,-184,-595}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{400,-30,-202,681,-1000,-92,244,-203,-516,-866,-172,677,547,-122,632,697,-1000,1000,-946,215,1000,-1000,61,-208,400,582,601,-933,-299,414,363,422,1000,480,-1000,297,-625,432,400,533,-530,-880,-400,-1000,614,255,-400,773,-705,1000,-1000,1000,1000,-616,540,-1000,-319,159,524,379,7,-922,400,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-847,163,-118,-903,867,69,-36,-347,-502,405,298,-563,116,494,-748,-263,227,-202,168,-10,167,-26,-735,720,-428,-230,224,-40,973,-351,703,-140,-349,-438,750,-254,751,227,622,629,385,-523,-122,364,926,740,763,7,39,595,909,729,136,465,-869,664,-136,-23,-260,452,16,305,-826,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-874,-237,731,68,-86,-995,442,778,923,-895,-333,-596,-226,547,-196,-358,-842,491,20,-222,-415,-236,-958,-605,-550,740,-304,876,86,573,41,66,-441,852,-291,-50,851,-335,-422,10,-248,-761,-744,39,-681,-131,53,632,-943,683,-950,-699,131,-60,705,-760,859,23,18,-888,-513,-951,-310,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{851,373,-974,839,-821,936,447,-722,-188,-848,-970,205,-876,382,-206,65,-888,710,-645,-305,793,-156,-350,165,98,163,693,-825,-95,-892,-318,568,920,-300,-451,48,-958,-118,514,-9,-513,-425,-357,-679,492,885,-500,923,-248,-273,10,728,828,271,-573,-266,779,-903,896,553,-37,-291,517,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-1000,-5,36,-1000,182,936,-306,-1000,-161,610,1000,-164,-677,321,-1000,-1000,-997,1000,-388,-1000,605,-518,-1000,428,-682,894,266,-434,1000,867,-318,-151,54,-300,1000,-264,128,461,-669,-11,1000,351,1000,1000,77,1000,1000,-456,834,388,890,-431,731,152,-1000,308,-246,1000,-1000,1000,-728,1000,-691,35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "divide(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{192,1000,-1000,705,-1000,991,-652,-238,123,-583,-90,-660,57,-84,-36,-4,452,1000,66,-1000,847,691,-372,460,-404,-1000,119,-1000,383,-827,-281,-135,-177,182,407,-149,-796,-1000,1000,555,683,-952,-382,749,-261,886,57,563,447,1000,650,1000,1000,-105,-1000,673,-137,854,588,485,-1000,-361,-347,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "equals(java.lang.Object):boolean",
            new int[]{-423,816,762,510,984,-651,-296,-622,979,-839,370,77,554,261,-694,917,-603,778,-950,452,-821,270,848,6,859,277,-401,425,-906,727,545,626,-464,681,850,521,-152,550,138,-275,578,387,319,71,-285,-809,551,426,676,-795,108,543,-215,-843,61,774,-131,-913,195,335,-497,963,390,519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{-982,23,2,-534,677,-119,-252,-880,-841,706,-188,-948,837,-913,386,-198,-304,962,303,475,290,-270,-645,-790,211,924,-891,-633,-500,-12,-590,-867,-196,562,-824,426,93,607,-897,-853,249,680,456,-887,961,722,263,338,0,836,900,703,-819,-290,-684,548,-899,-447,-31,179,68,743,-96,925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "exp():org.apache.commons.math.complex.Complex",
            new int[]{-728,486,-510,-917,119,941,-737,-326,-195,6,352,432,905,-256,-691,628,961,705,-688,693,757,-941,-320,900,582,-684,-838,453,242,-728,15,303,986,-326,336,130,485,33,100,-837,-460,-9,-66,-245,-722,694,774,-39,268,747,-752,789,199,490,-127,-804,555,570,984,-524,-948,89,658,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getArgument():double",
            new int[]{732,109,-1000,963,-614,756,-412,-1000,-221,475,-1000,55,-668,-363,105,-387,738,-674,-41,-62,530,1000,142,-48,-1000,-650,734,-690,-311,282,80,411,453,-649,-942,488,-582,73,42,910,781,825,-775,-1000,117,453,-203,216,-698,-137,-552,-583,-208,-314,299,62,-563,249,-272,-287,-118,-604,413,27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.ComplexField", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getField():org.apache.commons.math.complex.ComplexField",
            new int[]{1000,-408,673,-810,-683,249,968,-872,-113,94,-468,-1000,-320,1000,166,354,596,446,-556,-582,185,-1000,309,351,-454,-271,833,495,-45,-48,829,-456,-1000,67,412,-199,886,789,755,-480,-78,560,418,35,-142,-160,-560,-1000,-305,-747,1000,842,1000,-461,-433,-1000,424,548,-310,800,-201,-357,-606,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getImaginary():double",
            new int[]{-653,-575,-674,-486,-35,216,179,959,-300,-393,-253,353,488,309,-234,-673,-910,-411,984,-371,857,-1000,-373,-533,351,-145,652,297,321,943,283,-660,122,827,-567,-287,-121,950,-624,-230,-557,-278,-703,-73,-512,-201,196,-891,-1,812,-961,-244,819,-3,-996,308,-65,-684,220,-984,-247,-2,480,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "getReal():double",
            new int[]{-396,241,193,28,-1000,908,-312,178,-464,196,-60,-499,-1000,-946,613,267,-1000,1000,91,-785,-58,-101,-429,487,266,-258,277,1000,1000,1000,836,434,590,168,572,-894,-1000,-943,522,465,467,-727,1000,473,-30,372,725,768,-803,-566,357,466,-746,813,306,854,569,-1000,960,-271,-411,511,365,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isInfinite():boolean",
            new int[]{40,317,-123,495,585,-135,991,523,-962,-53,-998,-783,-845,-606,-674,-575,-220,-893,557,-537,-384,260,-60,807,-635,-384,-5,635,-517,464,384,-897,597,213,446,-436,47,245,-255,-761,-81,271,442,337,-553,151,323,-929,-252,895,934,564,-564,-598,-578,-721,-93,379,-613,-762,778,-168,-647,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "isNaN():boolean",
            new int[]{-643,346,-964,-440,-1,-555,-334,-931,903,-138,-249,245,-988,-436,-61,-550,-380,-236,650,583,-623,-247,-137,-220,-64,-326,-726,-981,738,-830,509,230,-345,-846,218,56,729,164,-717,-311,-921,-312,-858,386,215,684,-749,-817,436,95,129,-899,793,-410,58,-303,-843,969,533,-770,-645,-255,-127,855}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-524,-996,854,634,-258,519,237,-648,-350,-719,-469,738,955,261,-881,-372,869,823,-771,685,-9,184,-334,269,-728,-615,-556,-328,-191,-580,467,-262,-465,998,964,-824,112,784,-81,-165,-246,-91,-579,-353,-909,-981,-473,-15,-306,244,31,793,414,39,-670,-24,-688,76,512,-717,622,120,694,-803}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-51,1000,978,-81,-849,-661,89,-792,452,-748,1000,1000,-1000,1000,-488,1000,383,879,414,-1000,-729,913,-810,359,-125,-696,797,-735,925,-453,-660,928,-973,-802,-410,-837,299,-917,813,-501,-420,-237,-482,-42,469,-1000,-365,-951,657,-97,-41,120,544,-213,197,359,-81,751,-39,-59,-673,-1000,969,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{76,723,-1000,165,-1000,-1000,604,-648,-1000,333,-469,-1000,877,-1000,979,-39,869,127,-384,685,1000,637,-334,493,55,-154,-129,-328,-299,-526,-879,929,995,998,-831,349,-273,784,-772,-319,263,-1000,-579,-744,-248,-76,-1000,879,-633,172,1000,-408,-350,-51,-356,1000,-1000,666,467,-717,-478,327,694,306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "log():org.apache.commons.math.complex.Complex",
            new int[]{-89,1000,-605,1000,-1000,883,337,-202,1000,1000,-144,-1000,-1000,-297,643,-38,-451,194,-1000,1000,-1000,-571,-894,-1000,-419,-775,-97,-393,378,-436,-30,1000,751,-3,-171,31,942,-907,1000,-7,-939,907,-64,305,1000,-1000,2,487,88,88,-140,1000,809,-167,665,414,-86,-1000,-1000,-1000,555,-1000,945,664}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{-486,961,-131,-491,-978,1000,-761,-600,1000,-314,288,661,319,-47,-688,-869,173,1000,428,-1000,115,1000,810,312,-442,506,88,-325,-1000,-544,-1000,-1000,-1000,1000,297,-773,593,861,-331,-292,791,732,-381,982,-349,1000,377,-779,428,-490,-675,262,807,42,-412,-146,-653,-426,-344,-890,-1000,-1000,969,586}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{-210,551,-349,344,-967,861,-647,205,874,928,699,-308,-681,-579,-6,1000,-324,1000,-508,243,-502,-111,18,-129,-663,1000,199,98,-593,-698,-1000,-1000,597,980,-1000,578,-3,-197,-611,454,100,-893,-1000,-540,-39,-121,866,1000,-1000,-544,813,303,520,-826,60,-353,-155,-1000,904,-256,1000,498,990,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{376,749,719,1000,-1000,1000,-788,-1000,-292,443,1000,844,-1000,758,-1000,338,-695,942,-737,958,290,-456,-468,822,321,883,1000,381,217,966,-836,-244,437,1000,1000,-73,178,933,661,-110,-400,-757,-221,665,837,595,943,-443,-871,-490,-566,-765,1000,356,-1000,-585,142,1000,263,38,991,-968,901,914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{-1000,-169,208,-103,-710,309,-683,287,-727,692,538,-824,-1000,315,-155,544,-179,1000,-357,-210,397,1000,222,965,-887,-415,-985,-265,-1000,-635,-1000,-123,94,1000,56,173,-944,-28,1000,-1000,519,155,1000,-502,-705,-665,126,-868,-777,-304,-356,342,-538,233,380,130,-766,12,-88,-118,-251,-365,-45,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{223,224,-1000,-736,-254,-340,-28,-458,11,-1000,-477,-280,422,-590,-102,-298,738,-1000,-279,-605,-594,389,218,-717,-22,179,24,42,552,-343,-34,1000,-61,-52,-1000,-383,127,-609,-151,-421,235,96,-864,477,-104,500,60,-533,294,-2,357,855,-840,201,-409,-119,-288,-435,194,-506,-547,-165,-757,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(double):org.apache.commons.math.complex.Complex",
            new int[]{194,-695,-88,-471,699,-797,429,-576,-33,154,-122,-784,450,274,524,1000,355,128,-934,-224,-848,981,-2,-675,636,-582,-394,-372,-548,-605,-1000,114,658,-359,-84,-1000,-577,160,520,-388,-147,569,-221,-113,33,-238,209,-1000,-125,-1000,-39,924,-539,747,-714,57,1000,587,555,-900,-829,-501,11,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-643,-983,981,521,36,567,-417,-812,673,677,779,-380,-281,162,507,-1000,466,293,1000,85,-369,-1000,29,-812,-1000,-629,131,-141,-24,-1000,701,133,1000,-742,-247,400,-522,-849,87,-440,-716,-285,1000,1000,1000,-261,454,-501,429,38,223,-33,-343,409,113,163,93,170,-341,-770,723,717,-341,-362}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-449,540,740,568,-60,-644,224,-312,-207,767,-333,-948,-482,426,354,-457,589,964,7,929,562,-975,104,-575,460,389,-383,815,-380,415,465,705,-432,-101,-150,8,303,-849,910,-892,-504,-379,-416,343,-467,681,473,-610,-929,-636,-389,-33,856,750,-489,818,-112,498,-339,-578,-270,562,-310,-949}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-277,525,859,855,210,192,233,527,461,72,898,-273,933,-780,968,-42,-253,535,-281,865,826,59,-788,24,-18,-911,-12,-787,-547,-37,41,889,865,307,-836,124,-596,178,-31,-383,114,94,429,559,648,-774,590,107,-444,188,297,-973,-736,-203,-789,637,226,-67,-836,640,-535,-789,26,628}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{862,-497,1000,1000,1000,78,527,-707,-829,-1000,250,604,-1000,1000,93,692,1000,867,-389,-1000,91,-1000,-693,1000,-1000,-595,-133,267,-1000,-1000,1000,555,70,-1000,-1000,-1000,-951,-966,-488,343,887,-360,-23,595,-104,-1000,1000,-1000,-3,-1000,-697,250,-393,1000,925,657,-479,1000,1000,514,-257,1000,-975,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{1000,-538,-1000,-871,925,-579,-553,177,-1000,-975,-1000,365,-1000,308,-1000,1000,-729,-34,744,94,-382,-36,-586,1000,-639,683,1000,556,174,615,1000,-1000,-478,-142,-238,-110,1000,400,-1000,1000,-916,390,176,718,-1000,-63,215,-488,771,1000,368,1000,554,1000,-821,150,12,1000,1000,1000,90,1000,-409,-193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{906,-372,402,-195,430,769,654,703,-143,-856,479,635,823,102,519,349,394,471,-660,-732,-57,776,189,-461,-264,-170,875,278,-944,-348,826,353,197,-416,-752,537,453,-846,552,-748,-643,-257,-293,270,845,-541,-944,-281,-217,-289,-759,-449,-676,-18,379,-804,-40,-998,-618,587,800,131,-577,-73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.complex.Complex", DEReplay.run(
            "org.apache.commons.math.complex.Complex", "org.apache.commons.math.complex.Complex", "multiply(org.apache.commons.math.complex.Complex):org.apache.commons.math.complex.Complex",
            new int[]{-178,-595,704,595,259,531,-516,-308,7,455,570,-348,-911,-396,-181,-599,714,515,812,396,-609,-613,243,-190,-905,63,621,-399,499,-897,508,-547,773,-505,419,130,-719,-756,715,-855,-387,-413,369,794,813,-88,295,-393,423,582,98,28,-989,825,558,-224,226,606,-57,-275,98,962,327,-618}));
    }
}
