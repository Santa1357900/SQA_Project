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
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(int,int):int",
            new int[]{26,-635,-58,116,-147,-477,384,-390,640,326,156,-97,537,-609,-523,507,-2,635,-97,-130,-457,-668,87,678,-898,-14,890,54,-122,834,-527,-527,884,-641,-875,-872,-21,887,420,-588,-438,-921,-485,235,-126,917,-634,943,320,-345,-988,-244,-852,-876,-884,-138,390,-225,505,-448,36,866,581,122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(int,int):int",
            new int[]{-289,1000,1000,917,1000,-1000,124,108,-1000,1000,354,75,-541,405,597,175,-1000,339,-1000,345,958,1000,-190,-446,587,429,-37,1000,83,933,1000,-817,-107,-576,584,222,-1000,-1000,-442,-387,-1000,-36,-127,622,280,1000,-507,818,-679,1000,-480,116,-64,-930,-1000,847,368,-481,458,-81,-171,415,-489,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM4NDg=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-200,323,-854,1000,-717,-418,-227,-461,137,-571,1000,-956,646,-107,-858,484,-378,-194,-349,-949,-367,-310,-553,170,542,-441,123,916,227,117,-245,-359,-1000,-260,-281,-311,818,-637,174,-606,-986,1000,-465,824,282,297,-317,365,-971,707,-1000,-985,20,498,-176,245,-120,358,-631,-1000,673,516,-1000,-540}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-130,-962,-649,-978,-287,680,538,-946,84,-701,-240,237,833,-993,229,-64,706,-274,-310,452,841,-237,-245,185,811,-423,503,823,771,842,37,-764,-564,569,889,-341,332,686,915,658,44,-770,-378,459,33,107,-223,-773,7,217,549,89,-211,-303,466,-160,172,982,20,-392,-658,-530,894,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{91,667,-563,746,310,-139,330,174,965,622,127,-449,180,-279,-212,716,896,408,237,-800,-919,-562,-530,698,-499,-309,-164,494,632,-184,-297,331,-532,893,931,-961,716,-156,330,-729,-889,-13,-420,619,474,994,-22,-435,514,-673,-187,772,357,173,-831,489,319,884,-464,-920,539,-35,-297,31}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0OA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-800,-9,289,733,553,2,65,-419,307,-560,160,-563,23,-103,-572,36,-535,-543,-359,-63,-185,432,408,-289,237,-152,869,-160,-609,194,-455,-445,-551,-932,92,469,82,191,-182,-249,-1000,1000,116,163,-564,-136,-1000,806,-229,333,-233,-930,-1000,157,428,132,231,397,316,-657,-439,687,-40,-530}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{473,185,-17,-609,-563,1000,619,-1000,-330,342,1000,-566,-668,485,-309,333,113,-346,-819,-952,-575,954,440,167,-589,-737,-922,480,-833,879,-281,1000,-717,547,-87,755,-813,-747,1000,366,-5,265,8,1000,-1000,-1000,1000,624,1000,-115,1000,897,118,-70,971,-1000,515,1000,-737,-247,507,-50,-122,527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{681,394,400,-974,719,40,-1000,581,752,44,560,-772,-316,-882,-479,-1000,381,637,-867,108,1000,71,662,304,646,1000,-1000,-1000,1000,1000,-75,591,444,-697,1000,495,1000,1000,-1000,869,-1000,-1000,53,-87,-770,-1000,-254,1000,1000,202,647,515,-1000,968,-850,-1000,762,-16,147,-894,1000,1000,-395,-715}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{381,871,-465,899,866,562,935,844,60,-870,894,936,-944,328,877,861,-328,-366,-331,161,-646,-223,940,808,-736,689,-128,536,-487,-469,-226,-816,-897,545,-202,-635,-499,866,-28,-993,-18,-847,-448,999,-114,909,-581,66,-260,-269,684,-758,-136,-672,-18,-101,-517,-834,-974,119,-331,829,-96,399}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-519,796,348,-819,573,234,734,-527,-785,-394,914,-6,219,-565,170,-343,545,388,-446,990,-491,9,638,119,-505,-115,357,338,-235,-208,-14,940,608,486,-432,-1000,-599,183,-880,-892,363,-523,810,638,-171,-503,-40,128,623,420,-778,-472,823,119,-465,-333,-404,-325,-386,162,69,-221,600,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{283,-387,254,683,127,-279,-389,-335,888,569,363,825,532,-939,-597,-715,166,50,-52,-468,-734,-883,442,888,-804,262,-945,-102,316,11,986,719,-466,-523,51,-386,552,319,-987,-76,249,-706,-962,-427,-345,-934,-735,8,797,320,992,-197,-686,-143,-735,-348,-780,-686,-257,384,416,-574,656,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{261,715,-589,-955,-666,-34,-596,-648,602,667,-401,-935,-800,-174,-473,24,-278,41,-736,-206,54,862,-642,-259,41,-889,-582,184,-427,160,361,755,-774,-748,549,73,988,-656,99,185,793,-316,493,-208,609,-667,272,266,-384,201,104,-340,-308,805,-218,503,-175,929,-187,-971,-639,633,-805,654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{989,836,-541,-918,237,195,107,-767,-99,-1000,96,495,-553,-979,-84,-759,-315,1000,-7,-712,-502,300,857,1000,-297,-343,-155,827,1000,-465,-955,954,1000,-557,528,-1000,545,1000,-917,-380,-817,-100,678,339,-1000,-587,-105,650,1000,-731,-1000,133,-599,675,-796,515,1000,202,435,-85,496,-1000,244,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-595,-21,-681,988,205,137,385,-890,573,-728,-45,426,-218,376,-697,-847,727,-112,-564,344,-774,837,424,-867,-304,909,-730,403,503,185,104,-266,902,379,-342,510,-169,812,-939,-625,-959,78,-163,814,-956,369,-372,-395,185,-229,-296,-895,788,498,-471,-92,-243,-13,-159,-741,283,-952,550,830}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Long:NDM=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{431,274,878,-95,453,75,734,-272,914,-507,1000,447,219,-633,-924,-1000,842,1000,-668,-547,700,-526,638,119,304,620,43,244,926,424,110,940,-182,-618,957,-642,512,806,583,-621,939,-534,-685,716,-604,-626,-389,487,1000,34,715,445,-450,-389,-1000,-1000,506,-1000,-386,824,858,-1000,880,-93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-410,564,-143,839,538,766,-433,-329,898,-44,362,-527,-825,551,652,947,-634,572,-467,-761,-273,-969,397,164,-795,-479,-386,-975,-197,623,906,-590,414,23,-268,234,303,-835,30,937,346,-860,10,-760,-644,128,329,-854,-570,145,949,983,58,136,264,194,-29,-501,-367,-687,176,-975,420,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-78,-285,961,-601,-302,-305,-240,447,-672,-583,-953,740,910,-849,-99,440,223,-991,933,102,-370,555,753,676,281,196,639,799,-874,-471,-202,-147,574,-978,332,-963,256,430,31,-575,-726,152,463,-566,-434,382,-517,421,572,-652,-763,238,258,193,-974,474,-269,126,965,-133,183,-636,-717,-114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-491,-843,995,472,-614,475,-40,780,957,-247,416,269,-374,-495,142,-543,797,-467,485,882,404,268,278,-713,-932,541,242,835,533,351,-80,-296,401,-562,897,-763,-244,444,99,529,224,-706,-703,-242,261,-957,-421,915,997,894,547,636,-134,-651,-773,806,-605,-473,-680,296,-945,-431,-729,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{317,715,460,-40,-5,-509,773,-693,-690,-325,-675,-564,527,100,-199,307,-35,-820,686,-557,-965,144,-338,333,939,-808,221,-437,78,559,912,408,539,628,-647,-576,648,-725,-810,303,499,447,471,-332,630,-413,615,843,544,998,66,-322,-309,-528,34,-535,645,-399,-705,-314,764,-940,923,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{745,630,509,914,129,439,-444,-400,845,-257,-309,598,188,-699,121,-629,339,-647,-859,133,0,-506,645,246,-340,235,-29,-521,361,-489,-666,197,-843,-382,795,-668,-959,63,722,685,-910,-367,80,599,-219,624,-507,-714,-973,-369,-985,264,321,527,-202,-801,-436,375,458,-751,-335,-140,826,489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{989,-721,55,-804,-732,409,-508,-320,-534,-940,228,-827,-8,-366,484,113,638,-453,704,303,333,850,205,693,484,-986,-558,363,-592,778,-248,-668,651,-834,147,713,620,-223,757,-620,878,288,859,787,-298,723,76,895,66,-475,309,231,-947,-317,-793,49,927,276,-394,284,544,-349,-760,-489}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-949,-1000,-526,-59,-1000,-1000,628,-929,212,160,346,149,1000,-1000,-713,-465,586,-378,-1000,-168,-383,-330,1000,213,-206,81,874,45,-401,1000,1000,142,658,786,590,1000,-537,436,-834,585,-64,-1000,933,899,-821,432,-497,771,-1000,143,-528,-455,104,973,-695,-462,-1000,913,24,793,976,458,1000,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{-523,937,475,-380,560,796,773,-712,-27,-224,-977,31,511,831,543,756,-109,329,-832,752,130,250,327,-121,-289,541,-830,592,-160,361,329,-692,-786,662,311,867,-137,-225,-38,611,-79,524,980,-67,-584,-277,-995,-904,586,892,487,-446,604,494,-795,-942,132,-906,-879,428,-379,-569,-960,-973}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{-471,-802,-552,926,860,-574,-515,89,797,492,202,356,938,-10,-628,-942,690,-58,99,-897,-900,307,809,-747,-833,226,36,386,-942,-105,-283,-600,-230,-390,-865,611,957,-202,58,-699,-160,668,172,-588,491,471,484,-722,50,-677,991,-224,947,-970,-353,302,-377,419,404,-435,823,320,944,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{918,-379,-544,679,417,-568,942,-909,-298,895,411,-357,448,-594,706,853,210,-107,82,-229,641,625,-637,-895,-331,835,-161,-35,222,-591,258,780,195,620,709,162,-482,-884,609,-743,432,207,-456,-497,385,844,-709,586,-703,-354,985,885,387,828,-893,708,244,938,-268,-763,-647,951,308,-762}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{911,-561,126,-672,-544,-676,609,-125,-468,979,543,-571,443,383,-526,26,10,419,628,-349,138,445,916,690,26,-182,-145,286,874,-836,-430,200,-938,917,429,-46,959,-159,-691,-133,-272,-866,45,-106,833,26,140,118,275,765,502,-791,35,367,-337,180,676,-419,685,391,-386,134,-170,897}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NonMonotonousSequenceException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[]):void",
            new int[]{490,-508,-490,10,161,-422,-604,362,-675,-805,151,32,-349,-900,-276,552,-997,420,-722,259,944,-306,-568,238,-208,-746,760,147,654,156,-338,121,-797,-27,-766,-477,131,185,-909,84,205,-333,210,189,-398,-189,844,485,-126,-800,-724,780,222,-569,792,289,67,673,-387,-316,894,-139,-277,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[]):void",
            new int[]{128,-137,-29,-623,808,524,-961,872,104,819,826,686,990,-6,417,239,-758,25,-706,-242,-307,-809,671,-164,-613,-294,520,-565,755,-206,112,703,-754,-286,368,-441,66,-354,665,-202,-409,-861,866,-368,884,843,-708,-953,-727,-383,-888,129,-559,-186,-804,606,-367,512,881,-768,-310,774,-741,391}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NonMonotonousSequenceException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[],org.apache.commons.math.util.MathUtils$OrderDirection,boolean):void",
            new int[]{-896,717,182,336,778,1000,-1000,6,-334,134,175,-3,1000,-460,332,180,462,185,41,-1000,280,82,-762,7,-698,-629,-1000,893,-264,-1000,-1000,-616,-956,-12,-1000,1000,-523,-631,-569,575,414,170,-112,-20,-1000,-1000,-1000,1000,-1000,-58,-291,1000,1000,1000,1,1000,-719,-703,1000,520,448,232,441,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NonMonotonousSequenceException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[],org.apache.commons.math.util.MathUtils$OrderDirection,boolean):void",
            new int[]{221,-500,822,-716,1000,1000,-1000,274,-1000,-362,-7,454,462,435,1000,-868,564,-1000,1000,-1000,74,-9,-1000,-248,-240,762,-693,271,190,-1000,-1000,896,-224,-10,-950,882,-907,-276,-1000,640,872,398,-82,307,7,-1000,-356,570,-1000,812,338,307,467,584,-945,1000,-909,1000,1000,490,-355,-20,62,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NonMonotonousSequenceException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[],org.apache.commons.math.util.MathUtils$OrderDirection,boolean):void",
            new int[]{785,661,-1000,272,-366,454,-8,-176,-840,822,41,-437,-261,115,753,-35,397,-979,735,-475,613,-43,1000,-568,-1000,593,-652,245,867,-313,102,-611,-28,562,939,-579,753,-63,-191,1000,1000,-77,439,782,930,-66,1000,-141,-561,-260,292,477,-91,45,560,-203,-153,231,681,-835,388,537,249,-705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.exception.NonMonotonousSequenceException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[],org.apache.commons.math.util.MathUtils$OrderDirection,boolean):void",
            new int[]{196,-893,-1000,-712,1000,-1000,256,1000,-295,861,-1000,-1000,-1000,-1000,-683,-1000,987,-1000,-400,1000,818,1000,1000,-225,-662,1000,581,-1000,144,-941,-742,-1000,1000,625,-759,-1000,859,-560,1000,625,-156,898,-938,702,356,-704,-865,-1000,1000,-795,1000,-1000,447,-1000,752,-663,181,1000,-1000,-1000,321,-338,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "checkOrder(double[],org.apache.commons.math.util.MathUtils$OrderDirection,boolean):void",
            new int[]{-418,-741,883,-349,491,-700,-427,-532,115,69,988,-320,78,-860,380,444,-181,330,-235,251,-614,-146,-873,-489,-812,700,615,-668,867,-472,-992,498,118,-631,510,994,-248,-373,629,66,-390,436,286,782,-982,-692,-399,-696,-595,185,852,-276,-582,498,-11,150,430,647,-197,998,670,428,-669,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{992,273,-973,418,-598,-1000,560,-499,297,433,-1000,-721,754,-1000,186,-229,982,73,12,-1000,-56,-388,447,-172,14,-1000,-302,1000,-501,1000,651,283,797,642,-294,-426,665,-835,-61,232,286,-211,-1000,-1000,-95,424,-297,187,-1000,-342,-1000,800,-1000,66,955,898,56,487,605,-155,841,-755,542,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{1000,499,400,283,423,1000,251,-315,278,933,774,32,788,-880,27,-1000,618,785,13,-984,118,129,-1000,227,-879,-74,-923,-129,543,106,-548,-174,-933,-654,1000,120,-337,1000,1000,-942,-117,372,281,152,83,792,166,-400,-97,820,-1000,270,1000,414,16,89,1000,1000,1000,1,1000,-556,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{42,-920,34,124,-926,75,666,1000,473,-1000,480,-1000,977,1000,-232,851,697,642,-295,926,-702,674,836,-612,-283,-530,401,-132,-559,-757,308,338,-40,1000,-585,721,115,-640,176,61,648,-187,693,-34,-753,-759,472,-348,107,-460,162,331,-1000,-116,-524,1000,326,-671,400,322,-878,-259,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{215,273,431,722,-56,795,686,810,297,393,290,970,-301,-467,-386,-160,-254,1000,12,132,-56,-327,-954,521,949,-708,-45,-281,255,-93,458,-462,74,-951,997,-426,-103,489,-395,550,121,940,-273,332,36,-980,514,169,-548,388,416,800,303,68,176,715,670,-315,763,111,841,-425,-777,-172}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "cosh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance(double[],double[]):double",
            new int[]{621,-372,-273,-50,-576,-530,-317,-997,136,523,-876,-416,496,731,604,421,-827,490,-193,825,963,-561,718,-510,-690,-113,209,905,765,-292,646,872,423,-122,673,-659,437,-595,-656,-476,-698,869,-554,-898,-245,-544,12,203,-147,-974,-203,-173,-914,479,789,-652,-776,503,-282,877,-5,639,-57,-646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Double:NzYuMDI2MzExMjM0OTkyODU=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance(int[],int[]):double",
            new int[]{626,-866,-715,772,-602,735,346,-611,-423,-995,287,-151,151,-270,-476,172,-722,-57,-396,-528,-995,-79,-268,943,-43,850,11,624,454,-602,-831,408,27,409,98,-961,-575,-5,-308,157,-149,-597,112,-143,-630,-789,896,-325,248,-353,-866,435,-692,123,297,-37,282,-577,-736,-663,645,-565,-425,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzkwMDIyNkUxOA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance1(double[],double[]):double",
            new int[]{-778,-388,-294,-763,539,8,-922,-758,685,3,-825,-936,21,923,516,13,-319,288,586,293,-115,-267,761,155,-26,-21,41,825,459,-416,-177,-809,-747,-216,-995,-748,915,693,-509,678,-798,368,835,462,-472,-207,-818,-359,-966,-281,-56,-709,-122,-537,-284,641,85,-710,809,-987,-143,759,-507,512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance1(int[],int[]):int",
            new int[]{410,-596,520,866,619,-667,753,776,221,-76,560,-59,52,-356,837,128,-684,774,235,268,-34,653,327,-204,154,-203,59,-386,-120,-187,-448,-655,52,-569,138,980,320,938,-527,56,-758,-142,-468,24,626,-212,-643,-371,190,-18,-473,793,-889,-526,-499,-257,-538,335,215,966,-934,-6,631,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distanceInf(double[],double[]):double",
            new int[]{-418,-757,-959,-464,323,995,-56,866,601,929,-599,-860,-996,-218,-953,-484,725,-761,-511,-368,440,680,816,-771,872,-765,-171,780,129,141,-790,-562,-91,875,-516,17,-641,-675,-980,-645,15,-689,559,463,-429,212,797,109,478,-862,394,160,-666,121,-537,882,898,-862,54,-298,149,428,451,-559}));
    }
}
