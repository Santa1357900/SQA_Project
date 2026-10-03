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
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.apache.commons.math3.optim.BaseOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver,org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "getConvergenceChecker():org.apache.commons.math3.optim.ConvergenceChecker",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.optim.BaseOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver,org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "getEvaluations():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.optim.BaseOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver,org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "getIterations():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math3.optim.BaseOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver,org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "getMaxEvaluations():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math3.optim.BaseOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver,org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "getMaxIterations():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.TooManyEvaluationsException", DEReplay.run(
            "org.apache.commons.math3.optim.BaseOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver,org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):java.lang.Object",
            new int[]{-950,679,888,620,506,-991,667,507,874,-957,-840,804,705,-638,897,-833,-500,-24,71,618,875,-734,-258,262,-937,-93,247,-625,249,-774,281,-308,747,865,-933,780,-63,-70,-665,371,-978,805,365,-816,-801,-218,320,358,613,-727,-758,942,596,945,653,-923,128,-608,522,-541,-812,130,-488,-520}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.BaseMultivariateOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver", "optimize(org.apache.commons.math3.optim.OptimizationData[]):java.lang.Object",
            new int[]{108,736,285,862,-479,-548,259,-797,-392,-105,441,39,5,-998,-694,9,-352,538,418,-93,-941,-704,-610,-869,454,35,836,-244,-787,-712,-461,-524,789,744,207,-896,-555,-293,-847,54,-683,-712,-939,281,297,477,570,-461,948,-16,-151,491,248,-155,-463,-811,-633,-808,-500,769,-166,-724,-323,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.linear.LinearOptimizer", "org.apache.commons.math3.optim.linear.SimplexSolver", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{460,872,-624,902,820,-420,481,-8,477,-654,706,508,804,-189,-381,-392,679,-527,473,-993,-786,-914,-602,-727,-577,459,-937,-937,-156,162,504,474,732,270,144,225,906,-715,-917,236,579,-402,700,408,592,-518,310,825,-669,-514,-837,481,-666,-673,437,699,76,-121,-974,-127,-239,-267,-83,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.GradientMultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-69,-300,-271,-925,-217,174,-719,-347,-804,547,-716,419,-67,426,261,674,-217,243,244,-891,-683,-274,612,-812,-283,-541,814,-179,79,58,813,-498,797,243,142,-969,17,708,-180,701,-983,-871,82,422,-629,826,847,-400,227,-310,-836,-168,-314,811,503,-559,332,-99,679,747,-716,-882,436,548}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.GradientMultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-202,154,-700,-203,394,-427,-850,-340,785,290,-852,246,-539,823,178,-810,-340,480,721,-62,706,-2,-756,114,208,-673,592,686,-571,-792,-392,725,-681,-974,306,583,144,-41,-768,-13,-566,-60,-626,-804,-954,-903,-42,698,552,-557,-219,140,870,390,-665,212,-925,378,-65,-10,676,329,278,917}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{312,-440,221,-482,-139,847,975,-369,-317,-932,618,-113,378,-773,-554,549,-370,-642,833,-976,147,494,109,939,-621,708,-371,-951,-674,-910,-847,880,-341,-975,-566,-302,588,520,-932,877,-571,486,41,-891,673,-151,205,985,-492,-827,935,462,-818,66,-400,-536,-578,64,-833,454,157,-87,-759,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-782,-980,-369,125,-17,289,-918,32,314,-306,599,-547,383,-846,-501,-423,918,-764,-35,950,-247,985,967,163,-666,502,902,130,-323,-198,-746,-449,193,-994,361,816,-999,-680,517,-853,-133,-639,-941,-930,763,-565,831,-537,173,-907,-525,-638,-256,957,373,-522,659,183,747,932,182,173,401,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{227,-89,715,910,-839,145,584,-419,-74,-876,-747,-8,475,-662,-445,160,735,456,-599,454,-741,-768,-91,-134,466,837,-778,357,613,-246,-85,-493,-478,-41,730,-659,314,415,-536,755,923,-892,-689,263,986,822,-349,935,-119,625,386,322,135,676,252,970,-812,340,243,-372,-394,857,-736,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-180,174,-135,-768,383,704,-357,-850,624,-142,79,-253,-797,-435,-961,-146,-344,506,771,-689,387,-655,-489,70,900,55,-288,-132,30,120,465,-696,823,-67,431,840,-55,70,-213,-234,-989,280,-948,466,-933,6,-527,496,-393,-943,-363,-113,-15,637,412,-110,-182,-627,944,-80,127,182,474,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer,org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-314,734,-428,-47,1000,875,-856,-1000,400,1000,-600,740,1000,748,-1000,400,-227,566,-186,-1000,1000,-891,-1000,1000,-1000,688,-97,-1000,400,-1000,280,-302,273,-646,459,-1000,152,-25,-154,1000,-898,-96,1000,1000,524,1,302,-749,-1000,584,353,-142,1000,1000,832,-812,-400,-442,-675,-1000,5,573,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-133,807,547,-930,868,-245,194,-484,-429,553,-474,35,240,596,438,-191,-810,-627,-435,806,178,808,-461,-400,668,-699,309,0,552,-613,644,498,-127,-921,-129,-185,-624,-168,-970,889,-690,-171,-831,533,-221,-184,263,701,183,60,-603,-211,-204,-810,-734,738,359,633,-212,832,758,178,695,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{252,-128,-846,-571,439,-769,976,700,450,429,950,356,632,468,-230,559,-324,367,-443,-583,-135,-595,-422,-830,-102,295,-141,188,-579,-969,-112,-907,-679,207,448,-441,176,577,829,410,-232,293,-853,-468,-470,865,301,-183,979,-832,234,840,709,61,575,-666,375,103,513,618,237,-328,763,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-324,-761,2,229,215,-129,139,-757,628,559,-241,-205,-358,976,-374,-124,317,646,886,-119,-58,230,-146,-855,7,-466,967,421,807,-602,770,496,-425,-507,793,865,-846,-807,956,-553,187,138,-248,691,-461,905,867,-889,-740,271,127,279,-227,-41,-290,-400,162,478,395,832,-545,684,-683,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.JacobianMultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-903,-319,658,301,-51,97,-700,-673,-115,836,-881,-838,645,923,84,-356,-413,-836,-708,948,562,-110,-947,391,-805,-291,969,-332,323,905,185,-154,862,-183,-960,950,402,-722,920,-473,493,-945,-702,-835,-241,-891,-352,-353,-286,-565,-800,126,-491,-702,-623,533,665,-285,132,-737,-672,204,378,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.JacobianMultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{495,220,-464,297,-724,95,-502,-248,847,205,766,-178,21,-494,418,-75,-542,237,-799,-449,970,525,-654,724,336,-297,756,-904,457,-479,-165,-415,-839,-857,224,-36,-947,595,325,316,-597,147,654,-62,291,11,764,-111,948,-303,794,-824,-442,-621,516,-686,678,702,-825,-102,845,-387,391,732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.JacobianMultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-576,-354,91,-634,-557,709,-680,-378,-114,-539,998,262,-322,923,-29,-630,-20,205,-453,-297,-809,429,-581,-80,-739,-8,-812,12,-15,-779,-392,-290,-527,-824,711,145,71,159,-449,-438,-400,274,498,-458,-3,-703,-308,803,-818,85,584,-558,-703,724,-684,-763,-676,-243,-886,-472,555,-80,-179,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.JacobianMultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-201,552,-1000,573,524,-321,-119,515,-502,26,1000,-338,526,-606,432,-1000,-906,-1000,-325,-312,320,593,-385,601,1000,476,-712,918,-809,-953,-447,-577,-265,-801,-940,1000,102,994,1000,129,-59,-551,-977,942,-529,-1000,1000,-329,1000,969,-125,-1000,-610,351,990,-1000,1000,1000,-1000,558,-195,686,1000,262}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.MultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{997,797,219,376,463,614,-24,148,510,517,-799,-616,-140,-172,931,-324,-830,198,861,-20,440,878,-870,67,551,-136,-885,704,-105,-101,30,-676,-366,-633,700,190,182,100,0,409,-811,-712,-849,-455,-490,802,305,-990,441,526,661,-41,832,543,-236,985,606,439,-482,-461,-315,352,20,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.MultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-551,-869,-400,41,-890,104,-190,-971,305,-225,948,-198,37,-62,-403,-598,-660,-401,-269,714,-161,-803,216,513,323,401,984,-984,370,-934,-245,-790,614,-90,-277,576,322,-472,778,91,607,-539,509,-176,-161,-767,466,889,587,469,33,24,766,-447,557,-350,-416,-406,401,921,-645,946,-973,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.MultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-834,-596,-660,191,-130,277,-427,276,778,-647,929,490,-738,203,497,293,208,79,401,-180,-723,-865,-425,-393,77,950,-116,549,348,494,-481,-20,804,654,-826,-8,-646,508,605,501,-877,-364,896,-110,855,907,982,582,-871,-529,-191,-639,-902,-71,-111,868,438,-958,-68,-662,-401,-228,-250,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.MultivariateVectorOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-407,-615,-754,-778,-663,566,47,-660,376,371,1000,-115,804,-510,-1000,164,-1000,-132,843,-671,-1000,-649,1000,343,-182,-535,1000,-1000,1000,-54,-589,-790,40,-301,-924,858,-95,-559,674,-102,-139,-1000,1000,304,-682,886,-778,1000,547,1000,-401,-580,1000,176,-926,106,-230,-91,1000,449,317,821,-1000,623}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.jacobian.AbstractLeastSquaresOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{165,-824,-692,997,-393,-970,-995,678,484,846,422,954,-293,321,858,392,-23,-477,627,-558,-35,930,-388,582,-668,334,-19,-383,128,789,967,657,-347,-548,91,196,-212,-258,-973,409,15,589,-583,292,-150,709,386,-407,-711,805,-759,617,972,423,-98,933,-726,748,-24,-223,-725,75,-926,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.jacobian.AbstractLeastSquaresOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-495,-640,-85,39,-573,814,-321,492,-1000,-1000,-931,794,417,963,-109,-246,481,-536,702,364,-906,540,1000,-263,613,582,-931,-878,-599,-1000,392,-585,358,805,710,-831,-119,-994,1000,-1000,-705,352,-668,-302,-1000,-837,-1000,-60,-1000,-1000,453,-323,430,-332,63,-384,308,54,1000,-1000,-172,-549,369,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.jacobian.AbstractLeastSquaresOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{-550,848,-408,-331,387,235,726,-710,-528,224,-827,365,522,-272,-298,-929,883,-157,402,-40,-307,-3,928,-630,985,947,-773,56,-667,476,-136,256,-657,-670,-511,-692,149,-479,848,604,349,437,-172,-779,-718,383,470,-171,-574,169,628,686,-177,-885,6,-594,-82,488,672,-12,957,773,-163,165}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.jacobian.AbstractLeastSquaresOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer,org.apache.commons.math3.optim.nonlinear.vector.jacobian.LevenbergMarquardtOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{807,402,-972,-91,-604,-11,-163,882,940,431,953,-968,-758,-563,907,-843,-314,89,126,427,-20,104,1000,739,917,-119,982,487,48,870,-124,222,-398,-711,-470,-773,615,504,-559,419,-353,940,785,146,-893,-995,462,585,-291,-987,-1000,-37,6,-289,-706,795,191,-932,446,-68,-547,486,-881,933}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.TooManyEvaluationsException", DEReplay.run(
            "org.apache.commons.math3.optim.univariate.UnivariateOptimizer", "org.apache.commons.math3.optim.univariate.BrentOptimizer,org.apache.commons.math3.optim.univariate.MultiStartUnivariateOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.univariate.UnivariatePointValuePair",
            new int[]{-470,-642,79,-933,-505,313,-774,-409,-988,993,-393,639,257,747,-952,-195,-620,-519,-629,151,-881,286,499,-227,305,-332,635,233,-215,629,757,746,594,599,-35,978,-966,15,113,-545,733,199,-414,659,-260,-899,-915,152,-789,502,858,-714,-611,249,928,-572,182,290,-437,-1,-90,-631,499,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-133,807,547,-930,868,-245,194,-484,-429,553,-474,35,240,596,438,-191,-810,-627,-435,806,178,808,-461,-400,668,-699,309,0,552,-613,644,498,-127,-921,-129,-185,-624,-168,-970,889,-690,-171,-831,533,-221,-184,263,701,183,60,-603,-211,-204,-810,-734,738,359,633,-212,832,758,178,695,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{252,-128,-846,-571,439,-769,976,700,450,429,950,356,632,468,-230,559,-324,367,-443,-583,-135,-595,-422,-830,-102,295,-141,188,-579,-969,-112,-907,-679,207,448,-441,176,577,829,410,-232,293,-853,-468,-470,865,301,-183,979,-832,234,840,709,61,575,-666,375,103,513,618,237,-328,763,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer", "optimize(org.apache.commons.math3.optim.OptimizationData[]):org.apache.commons.math3.optim.PointValuePair",
            new int[]{-324,-761,2,229,215,-129,139,-757,628,559,-241,-205,-358,976,-374,-124,317,646,886,-119,-58,230,-146,-855,7,-466,967,421,807,-602,770,496,-425,-507,793,865,-846,-807,956,-553,187,138,-248,691,-461,905,867,-889,-740,271,127,279,-227,-41,-290,-400,162,478,395,832,-545,684,-683,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math3.exception.NullArgumentException", DEReplay.run(
            "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer", "org.apache.commons.math3.optim.nonlinear.vector.jacobian.GaussNewtonOptimizer", "doOptimize():org.apache.commons.math3.optim.PointVectorValuePair",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
