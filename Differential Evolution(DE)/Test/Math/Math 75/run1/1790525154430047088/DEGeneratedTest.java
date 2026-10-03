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
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(char):void",
            new int[]{954,1000,629,-1000,-1000,212,-325,-671,-679,-240,-1000,823,1000,-123,-275,-558,-1000,288,1000,1000,488,638,106,849,1000,377,941,-324,412,-774,1000,-1000,153,-64,191,-437,700,-292,-1000,376,250,197,-752,-560,-577,961,-1000,-351,-674,-1000,-1000,842,110,1000,1000,515,-1000,491,276,-1000,1000,772,730,306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(char):void",
            new int[]{33,869,53,-584,-249,-249,-824,-168,-305,-276,-606,-446,443,852,-163,364,-534,547,-641,-349,-18,789,-619,-112,-557,-363,-489,139,-255,445,939,-485,794,568,-124,-146,617,233,-325,-150,872,-257,-295,-641,292,-819,-488,667,390,-586,380,-858,-433,333,991,868,-347,-16,-426,-369,890,541,-345,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(char):void",
            new int[]{1000,-293,-121,-444,455,820,-467,184,-549,-1000,-1000,1000,-1000,-168,841,-258,871,-1000,-1000,-518,882,474,-724,-579,306,1000,26,-1000,1000,-1000,464,1000,1000,558,-173,-83,1000,540,25,-142,-588,1000,-1000,-1000,702,-80,602,-921,-676,-228,1000,-1000,200,1000,-81,595,648,-304,-963,-1000,-822,44,828,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(int):void",
            new int[]{804,-557,-916,174,-459,1000,1000,-322,-612,473,-386,728,787,568,102,-963,-5,-588,-374,485,1000,1000,759,-759,1000,325,-93,576,-185,-1000,-232,1000,1000,1000,909,-1000,654,-982,-1000,1000,130,-990,-696,884,-800,-400,-1000,-1000,630,-1000,322,364,-859,-1000,-625,-126,-352,-582,1000,392,1000,1000,268,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(int):void",
            new int[]{-1000,-760,-267,683,721,1000,404,-981,900,473,75,1000,787,339,-945,61,756,-1000,-76,400,511,1000,1000,-20,-571,661,746,1000,-805,-884,-892,965,689,822,1000,-1000,165,-821,504,1000,-463,133,-696,894,1000,1000,-1000,-694,299,-1000,958,889,-250,-1000,-1000,33,-1000,-505,953,300,415,104,-510,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(int):void",
            new int[]{1000,1000,-582,-56,1000,-694,362,1000,487,-1000,-125,-65,-1000,-275,218,-642,-305,-67,1000,747,202,-1000,-1000,-809,565,-598,-1000,-852,493,-488,-55,97,1000,-1000,-1000,-894,-897,724,-702,-1000,990,-1000,1000,-1000,-1000,-1000,-598,180,-509,-129,958,-273,-1000,871,300,-1000,1000,1000,818,-209,135,-1000,-175,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Comparable):void",
            new int[]{66,-258,34,-944,-26,-36,-45,-458,-718,166,461,336,-183,454,981,131,1000,95,-695,-303,448,185,114,-1000,795,-738,223,448,-658,-1000,-45,1000,-673,1000,34,431,-727,801,-271,-402,709,709,-24,-267,-71,-877,219,497,-561,104,63,-180,-119,-609,637,-697,-507,-1000,480,-1000,7,237,-365,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Comparable):void",
            new int[]{703,61,-221,-33,521,389,-853,-1000,552,906,843,1000,-698,458,453,1000,935,1000,-565,60,221,634,214,-683,707,493,-240,-305,-1000,-579,644,1000,-274,1000,443,188,-669,-647,-1000,686,-413,585,770,192,-98,-754,1000,1000,-1000,562,863,686,-1000,-442,1000,71,252,-1,-5,-46,438,768,-1000,536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Comparable):void",
            new int[]{1000,253,123,-364,-21,453,-903,-493,557,30,929,815,-862,212,431,1000,834,1000,-546,-85,-227,59,695,-944,694,-400,-637,86,-43,-706,1000,994,157,1000,221,568,-485,-400,436,117,741,983,-369,-170,-398,-401,976,1000,-877,-131,1000,894,-477,-630,1000,957,225,-675,159,-1000,1000,1000,-662,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Integer):void",
            new int[]{548,-270,-476,-556,1000,82,950,-80,1000,-918,-1000,1000,-1000,-1000,-1000,1000,232,648,-653,395,775,-1000,-1000,-725,-1000,-1000,676,-1000,-1000,1000,983,31,403,-1000,1000,1000,648,-1000,1000,787,1000,-488,-211,1000,1000,-234,519,1000,624,531,1000,-1000,756,-32,-798,-242,-598,-1000,-1000,812,106,473,813,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Integer):void",
            new int[]{130,-767,-766,-968,522,-874,-699,-954,503,-554,-17,-417,-353,681,-1000,93,1000,913,-191,-732,-148,-953,663,-1000,-458,1000,-843,142,-939,-723,-78,-984,757,-335,35,-766,-738,-521,1000,650,138,659,-1000,1000,-400,1000,-580,1000,1000,537,1000,514,260,796,1000,-1000,-1000,768,159,777,-1000,-33,-1000,268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Integer):void",
            new int[]{1000,92,-891,-28,1000,-836,615,-3,1000,-311,452,-402,-1000,-1000,-488,1000,-905,-72,-688,582,958,-961,-1000,235,-407,-1000,645,-1000,-1000,1000,1000,867,-398,-1000,1000,-220,1000,-1000,1000,122,1000,211,681,208,1000,126,1000,1000,-18,154,504,-1000,954,-479,-698,97,-204,-1000,-1000,-252,1000,-793,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Object):void",
            new int[]{-1000,-219,-506,761,-533,1000,-1000,489,1000,338,774,993,687,1000,-295,-981,1000,1000,313,-160,820,860,-190,-1000,-1000,-1000,771,1000,-1000,-266,-1000,-311,105,1000,-419,-593,556,1000,-235,-949,798,-1000,486,-743,-1000,-294,-291,-90,362,-542,-629,1000,-1000,601,651,-1000,614,-55,-915,-997,-755,1000,-400,568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Object):void",
            new int[]{728,1000,839,77,1000,429,-1000,-297,750,1000,1000,-302,309,181,480,-753,-1000,1000,-1000,679,-59,133,150,-1000,566,1000,-116,1000,-1000,793,-250,-879,-473,195,540,340,-1000,-1000,-1000,-808,-282,-1000,-710,-980,-1000,582,-598,1000,1000,1000,-441,-1000,-1000,-420,-1000,-413,967,-830,1000,-1000,221,-1000,-838,922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Object):void",
            new int[]{-125,-610,119,641,210,1000,-1000,489,1000,-88,1000,67,1000,578,-204,-1000,402,588,1000,791,-724,263,11,398,-644,-349,-221,1000,-1000,-538,-987,-614,625,1000,-1000,-1000,1000,1000,1000,-1000,735,-1000,-1000,-1000,697,105,-291,49,362,-1000,-629,1000,524,1000,260,-1000,17,-1000,-1000,1000,183,396,-986,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(long):void",
            new int[]{-367,1000,309,-584,-938,-10,-489,-266,414,1000,-1000,515,962,-1000,770,877,-863,-112,-133,-720,73,1000,1000,-638,-839,730,-520,-741,-134,730,-929,1000,-1000,-333,192,164,-869,977,-969,771,-677,-141,472,1000,-538,718,-918,-1000,-1000,745,-640,201,-111,-734,1000,-890,1000,-262,906,-1000,1000,176,554,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(long):void",
            new int[]{-595,725,-76,1000,-707,389,-955,-798,-369,-678,-226,-216,253,-395,198,232,-539,-564,-957,-345,-362,171,-270,-811,-1000,-871,-385,-531,110,348,-1000,-972,-310,-472,993,-1000,1000,1000,1000,-1000,-621,991,219,1000,1000,-296,-1000,-113,-455,-51,-863,-1000,-1000,1000,809,-1000,655,-451,908,-1000,-585,827,1000,-60}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(long):void",
            new int[]{168,795,934,218,-1000,-312,-1000,139,788,200,-545,449,759,-1000,652,650,-57,-239,-38,0,-413,774,-207,-979,-101,-514,-849,-902,-147,-464,307,313,-459,84,609,-419,-208,1000,196,261,-198,595,-637,620,142,800,-694,-372,-704,-699,-526,1000,-243,-74,1000,-511,884,-647,-533,-874,-803,-1000,307,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{820,466,274,59,-473,1000,-13,-462,1000,584,1000,-1000,207,956,-12,-521,2,856,1000,-257,-1000,-257,-256,567,98,975,-502,582,60,-157,-1000,-392,-910,200,706,865,223,-1000,-1000,-1000,1000,-838,-717,-263,6,-776,1000,-343,-63,-307,438,-479,-176,637,9,613,669,331,590,-165,312,-245,-677,381}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{482,-327,228,707,-769,1000,648,316,939,1000,935,-1000,776,-242,-1000,-521,-850,722,1000,-1000,-683,1000,-710,-1000,98,-715,-1000,159,-873,1000,-1000,-271,-1000,593,-335,619,468,-1000,1000,-1000,-810,-913,-370,-499,-913,-393,1000,-375,1000,-309,1000,874,-1000,-1000,-1000,-204,369,653,650,14,312,-547,11,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{-521,308,469,-496,-1000,-909,-605,-434,-92,1000,648,525,161,504,-129,-400,-281,351,400,72,849,-350,807,-214,732,1000,1000,489,-370,-215,483,994,133,754,1000,-205,400,-1000,-880,-124,157,-432,165,457,566,414,1000,-648,76,574,-106,-473,485,209,-131,985,-162,-610,-193,386,-94,521,569,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{-275,274,544,-216,64,38,647,-547,-315,-920,1000,-1000,61,1000,-3,-245,1000,-1000,-959,-372,-1000,332,-735,577,-1000,1000,443,-135,-391,307,1000,631,127,91,-650,388,313,355,-1000,-73,1000,1,-521,-373,965,-992,-1000,1000,-368,-1000,627,-784,-244,-1000,-322,1000,799,670,1000,-1000,-1000,156,-1000,-113}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "equals(java.lang.Object):boolean",
            new int[]{107,-810,354,61,733,1000,-1000,-343,-675,-87,-433,1000,1000,617,-686,-731,60,761,-1000,689,-282,-548,248,-822,-1000,-1000,1000,345,794,-1000,428,1000,-440,198,910,-578,185,813,-406,110,-365,542,1000,687,-1000,-1000,-251,-390,997,-920,-312,697,-611,1000,-170,193,995,-1000,-651,850,-49,-621,245,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "equals(java.lang.Object):boolean",
            new int[]{38,385,-256,-115,-818,-557,223,427,613,200,-408,-325,1000,-890,620,-258,-527,-1000,362,1000,-946,-638,-688,563,887,1000,-1000,-870,-1000,5,-690,-985,786,1000,-1000,594,662,764,-308,230,1000,-853,-1000,-67,151,-197,594,-1000,-183,252,436,-1000,108,858,-224,-775,-330,1000,-139,-400,-1000,-542,129,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "equals(java.lang.Object):boolean",
            new int[]{-984,-1000,389,1000,-1000,931,-194,507,1000,-983,-290,228,-516,-925,-1000,-361,69,1000,-1000,-1000,-1000,16,-1000,-866,1000,966,-522,1000,-225,-907,1000,1000,101,-166,-1000,126,-378,106,-1000,-667,-1000,-1000,-566,-1000,-891,-696,-1000,-360,897,1000,621,195,-444,-1000,1000,-155,557,1000,-1000,-807,447,1000,350,-601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{621,-295,344,760,1000,393,160,-490,27,-1000,-532,897,-14,740,-413,-1000,-590,20,119,-496,-543,-402,-608,580,-349,877,-400,-314,-991,1000,110,119,171,-1000,1000,-1000,-1000,-395,1000,-1000,-355,1000,-658,-480,-672,-451,570,400,-1000,130,-509,850,1000,-1000,-701,-479,559,407,-860,-47,2,548,-400,-20}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{673,-854,-282,-669,761,224,-363,1000,28,-233,-1000,1000,-916,-211,697,-1000,-965,-1000,163,1000,-923,1000,-1000,-1000,-31,-905,-46,1000,-971,551,1000,1000,1000,-312,-282,549,-566,1000,-282,-1000,1000,976,993,480,-1000,-1000,1000,1000,-958,1000,1000,1000,-431,-139,1000,451,-283,1000,-1000,1000,1000,-1000,465,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{714,-1000,-287,-528,413,-1000,-402,490,-1000,60,-780,1000,504,437,1000,-29,-763,-900,92,595,-773,1000,305,-394,-469,-5,1000,-318,-818,-121,1000,1000,1000,389,-1000,1000,-897,435,-1000,-707,661,691,1000,-1000,-1000,338,599,-776,-929,980,288,1000,-644,-1000,-246,1000,-1000,321,-1000,770,1000,-1000,1000,13}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(int):long",
            new int[]{-136,-448,-841,109,953,-1000,-1000,-470,189,1000,528,573,648,1000,-42,-168,-127,-7,-22,1000,208,-972,-675,546,-45,238,-1000,1000,1000,-305,-1000,364,-150,562,855,255,-1000,287,243,-291,-1000,-642,212,316,120,-1000,-309,1000,1000,279,195,88,1000,963,-126,-1000,-408,-354,192,1000,1000,1000,182,-70}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(int):long",
            new int[]{-78,-205,834,-723,1000,-16,-1000,-389,329,332,868,890,-208,405,-36,465,77,227,-698,741,-541,-1000,-812,483,-1000,-660,-513,727,44,26,-713,235,253,753,709,134,-334,335,388,-877,-404,638,-743,415,199,-740,763,621,327,620,-805,-1000,1000,-397,-99,-1000,466,395,558,476,348,1000,-853,-330}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(int):long",
            new int[]{-959,878,-293,144,-51,-91,1000,297,-763,-781,936,-372,-175,-447,-1000,-981,-49,-1000,911,-941,-3,-252,926,-4,-1000,238,-611,-1000,133,-138,709,269,694,-1000,-860,375,316,-216,486,1000,17,-364,970,-542,813,1000,-1000,-309,-921,-479,1000,625,-1000,548,1000,581,-874,-1000,-12,308,339,193,-136,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Comparable):long",
            new int[]{298,557,-251,186,82,-943,481,692,-518,-87,252,426,196,-612,-686,-1000,847,-1000,-749,554,-226,1000,-554,1000,-453,-922,1000,1000,909,-766,-221,-157,-810,-1000,782,340,-1000,-1000,1000,-1000,556,-365,1000,906,566,-458,1000,-636,953,159,-245,-557,1000,-97,-128,-473,1000,-360,744,-1000,1,-664,-777,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Comparable):long",
            new int[]{568,1000,304,264,-219,-628,687,361,1000,-256,626,1000,-52,-473,-998,-1000,-905,-919,-618,-1000,735,545,-1000,1000,-859,151,630,1000,-373,-1000,1000,146,740,-1000,662,861,1000,-192,-1000,-1000,7,-272,-630,400,106,384,556,1000,-130,-316,-570,-247,982,-941,146,-356,495,-72,-115,-400,575,-669,-292,-671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Comparable):long",
            new int[]{-293,1000,-777,-663,1000,-563,877,-1000,-716,-816,224,-1000,-1000,-313,469,-1000,-1000,-1000,-1000,1000,1000,421,-1000,1000,-1000,-1000,1000,-525,900,405,1000,-1000,-1000,-391,-1000,410,-1000,-1000,72,-1000,-1000,-6,-1000,-1000,-1000,1000,1000,-1000,-1000,716,-938,-615,-1000,1000,1000,-623,1000,-618,-975,1000,1000,776,-110,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Comparable):long",
            new int[]{686,977,884,-637,-200,-1000,1000,1000,-304,-21,608,1000,359,-862,-464,-166,1000,-786,-618,-781,735,352,-884,883,-1000,-935,1000,303,-155,-750,1000,436,52,-1000,1000,724,26,-985,-1000,-1000,-5,-356,-448,-472,-762,-315,901,670,53,623,-564,-662,1000,-1000,-671,-395,1000,134,84,-508,-875,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{-831,-916,-276,-1000,142,1000,-1000,-718,724,4,-1000,-565,1000,511,-1000,-1000,1000,37,1000,-841,419,-1000,1000,-1000,1000,-873,-1000,97,-161,-175,400,1000,1000,-1000,-849,-1000,-871,-1000,-1000,1000,86,452,1000,-1000,1000,1000,505,-1000,-1000,-1000,-248,-1000,422,230,-1000,-1000,-1000,517,628,63,254,1000,1000,-111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{93,49,553,-634,62,320,-863,38,248,-437,-544,-1000,55,-221,-439,220,-741,55,-85,-38,294,-419,-495,544,-272,225,117,-599,-524,-270,-942,423,1000,-1000,-117,-564,238,147,-421,1000,485,5,671,1000,-36,-835,-927,-293,-303,-267,-719,-915,55,-15,166,-808,-48,1000,552,-636,-1000,-103,346,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{-1000,-1000,-777,-682,625,946,-486,-300,345,38,481,131,1000,-5,-620,-139,1000,796,488,-87,-615,362,51,-530,690,-827,-580,-26,783,-215,922,1000,-126,-809,20,-1000,-1000,-828,-40,833,-185,-87,493,-116,1000,1000,1000,-1000,700,-242,-85,121,348,-709,-842,-565,-515,28,-614,154,1000,340,219,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{480,897,-506,-399,-717,-314,-240,224,-172,211,121,447,317,-756,-81,812,-797,-136,-2,-862,283,-367,-803,-612,-791,773,874,-40,-804,290,28,-393,-201,719,978,472,-179,368,-976,-532,-366,-187,-997,-410,-487,710,-728,-341,931,-18,-734,456,-819,897,484,-738,202,667,546,-620,9,-229,-231,403}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{1000,387,649,-676,1000,526,887,16,960,-892,-422,1000,-1000,616,511,-839,603,223,1000,1000,564,-165,382,269,596,-200,36,-1000,-1000,1000,-437,782,887,1000,-646,1000,56,-537,-692,-94,1000,166,-1000,-198,848,1000,-623,169,168,172,-65,1000,-1000,-286,-136,1000,365,1000,-962,-1000,-1000,-10,1000,535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{970,24,913,-785,813,900,-95,-166,-574,1000,517,1000,-1000,206,796,-927,-924,-36,135,812,450,552,1000,673,272,-467,141,-1000,147,1000,-21,-71,989,-796,-949,-475,967,-744,-1000,177,971,417,970,273,-360,-1000,101,-963,656,857,-459,1000,-1000,-550,-812,-948,-648,-101,-620,-342,-499,-965,1000,-405}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{75,-667,649,112,81,415,-1000,-654,-105,-771,1000,-887,-589,-535,506,-369,-171,-315,1000,-713,-502,415,89,-94,-501,719,85,-1000,926,89,-351,570,858,-224,-596,1000,549,-451,-402,1000,124,456,-769,-198,848,-1000,1000,974,183,645,-65,66,-212,-204,-150,-1000,-994,662,1000,-418,-1000,121,1000,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{695,827,619,-1000,-93,-1000,-39,-1000,-526,1000,718,484,-409,-1000,1000,-171,199,-126,212,-176,-1000,322,1000,401,-126,-735,-68,-16,345,432,-127,-1000,469,-876,-1000,596,1000,-1000,-567,1000,200,-331,770,217,856,1000,828,-1000,-9,641,1000,1000,1000,13,134,-1000,-1000,-1000,816,-754,-815,-245,57,224}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{623,324,-602,280,-757,481,-481,-118,-14,443,786,126,-34,767,-1000,-713,1000,-513,561,-224,581,-1000,-908,985,-167,1000,-351,485,139,-98,-1000,914,566,137,320,-459,-563,1000,-1000,740,178,502,746,-298,-716,374,187,-112,-1000,-934,-1000,-168,-1000,-867,-492,-160,1000,941,-864,550,207,-348,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{899,-87,294,-744,727,-31,473,-454,497,1000,-189,-759,1000,-300,-156,306,-348,-155,100,38,-1000,-28,-583,559,-822,-911,-482,-666,-296,1000,-215,-245,-85,-366,-527,-659,-1000,-7,-699,454,-720,-605,191,267,-203,654,417,-14,-471,-623,-1000,-1000,15,241,-717,202,268,-804,112,682,-1000,1000,189,-640}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{-714,-386,-716,-1000,-1000,-70,-830,-214,-1000,-10,255,-1000,828,410,554,-839,1000,115,-546,-372,-1000,-973,-146,583,964,1000,399,874,-711,-605,-937,1000,-644,597,-535,226,-1000,463,-1000,528,172,876,-100,-632,213,-9,-236,761,-1000,-490,-167,-133,-1000,-213,391,-565,1000,-980,1000,1000,144,-89,52,58}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{-507,-62,368,-133,442,837,1000,-1000,112,1000,-966,910,470,-1000,1000,627,-592,-1000,748,-371,-1000,732,803,550,524,-793,-167,-265,946,1000,284,-6,-422,-52,-1000,-1000,193,-54,530,-977,175,-289,-380,-369,250,-29,157,407,312,673,1000,1000,407,-800,-680,-918,-1000,-767,883,1000,454,154,-741,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{495,-481,284,2,882,197,707,-1000,-234,-70,-837,538,-523,-856,1000,620,-261,-1000,860,-1000,172,704,-27,-147,179,817,83,175,331,894,-426,771,-1000,-1000,-528,-1000,149,859,-272,-1000,-111,499,-1000,174,1000,920,582,-223,-751,589,89,921,-860,-428,-553,-1000,642,536,-843,-210,-196,-281,-105,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{475,-73,69,216,-686,-984,-599,-886,-568,725,-543,-1000,1000,792,184,-235,1000,-439,352,-965,-982,-1000,-280,-1000,-97,1000,-386,340,955,761,-350,1000,443,-169,-49,-348,-1000,1000,-1000,49,387,569,-703,132,-571,1000,334,1000,-1000,-548,-1000,138,-1000,533,-793,-621,1000,1000,231,1000,-152,-154,-153,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{562,-856,-127,288,-141,702,396,931,-352,-807,-240,-459,-687,-375,-882,950,393,149,-31,-271,861,859,727,-942,-305,-377,-883,-181,-750,270,-706,-121,-223,-657,-312,-114,264,60,721,936,701,129,734,947,879,-362,-265,705,-138,338,-83,-805,382,374,-720,-953,-401,720,138,712,-127,-576,-168,343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:Mg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{679,-165,174,1000,360,106,-715,634,552,-104,-4,-424,-35,-369,-23,-102,344,-621,374,-473,-324,-757,-677,184,771,-276,143,-410,-876,658,-422,-552,-138,-568,-353,-565,-1000,-528,-484,53,-515,188,-342,-1000,-487,579,869,-48,-622,689,976,534,920,540,555,258,-460,836,-138,-69,709,374,537,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{453,1000,-96,-486,-1000,-1000,-216,616,1000,991,-1000,303,4,-1000,1000,1000,-1000,704,-435,1000,-1000,-138,26,-1000,1000,-111,-1000,372,-637,-984,-293,773,-1000,-41,-1000,411,658,809,465,-1000,38,-135,1000,854,-1000,1000,-492,157,-1000,-1000,-70,-909,-244,755,-455,-923,-148,1000,-1000,125,763,1000,-11,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{-482,-1000,68,-371,1000,1000,-1000,414,511,-1000,1000,-862,-140,211,-1000,-644,1000,-1000,1000,-1000,1000,-905,-416,1000,-1000,1000,1000,-420,1000,1000,-89,-189,-287,-317,1000,-515,-59,-1000,-1000,881,-1000,1000,-1000,-1000,1000,-1000,958,245,-1000,1000,1000,1000,-480,-1000,805,1000,-296,897,1000,936,-292,-1000,182,-250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{-659,806,318,-17,-1000,681,914,-822,405,191,175,1000,-23,987,258,451,-1000,1000,-146,1000,-925,-423,-80,-615,487,1000,238,410,483,-1000,466,480,-541,-298,31,1000,-511,19,-784,-783,1000,-250,945,401,-1000,948,67,-625,477,-990,573,-578,1000,1000,-1000,-1000,14,298,-1000,-606,912,1000,228,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Comparable):long",
            new int[]{-273,1000,358,373,-438,-451,412,783,-802,-694,-541,724,797,744,-1000,265,580,297,-999,55,863,-772,375,-961,285,317,300,-721,-242,713,870,-206,-979,347,-1000,-448,604,-252,-61,889,-190,495,132,-909,1000,1000,884,-1000,1000,-116,-232,780,52,-764,670,-542,-274,-252,-590,-1000,-682,-1000,1000,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Comparable):long",
            new int[]{844,179,-781,5,-1000,-561,-1000,-1000,-856,-760,41,1000,776,-510,-801,672,795,-1000,-999,-698,695,-684,600,901,115,-830,1000,-344,-595,560,640,-236,-1000,1000,-694,-9,144,-447,469,816,-331,-5,-973,-454,-368,1000,797,-484,276,-733,-1000,686,-431,398,-597,-1000,-763,-959,175,-984,-787,721,966,-553}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Comparable):long",
            new int[]{-730,-850,969,-422,426,86,-176,-110,691,729,308,60,-969,448,186,-964,-669,774,-131,351,-513,368,601,405,-3,-420,-662,741,-703,530,-183,387,-134,817,939,526,67,-342,255,-494,-397,578,71,-476,-474,583,502,-815,781,177,-331,581,430,-447,133,-459,-99,-202,668,-743,872,102,-472,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Comparable):long",
            new int[]{-535,-771,-852,371,-837,291,-417,67,400,-815,-405,564,-422,-730,161,1000,-137,-11,260,-417,-163,400,-283,-171,197,1000,-301,780,271,-400,-514,-441,400,497,-316,-134,-9,-650,1000,573,-230,664,-192,-1000,-519,-400,-400,400,294,-888,545,591,338,-80,-608,400,-1000,394,-92,-183,-913,807,751,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Comparable):long",
            new int[]{-155,-1000,-786,1000,620,808,-1000,-241,1000,-430,-787,-1000,-1000,-1000,345,677,171,-472,1000,589,-732,1000,516,-673,815,845,-818,320,-358,-1000,-442,-202,1000,-1000,-399,-1000,-1000,-693,1000,390,922,399,847,543,-1000,-1000,-1000,1000,-1000,-1000,784,-619,-152,506,-796,1000,577,1000,-1000,1000,-825,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{1000,832,-21,396,1000,-571,-1000,-1000,1000,162,-1000,-877,816,1000,1000,-1000,328,1000,693,-1000,825,-482,-1000,909,-954,-842,-400,463,-1000,1000,-656,994,-639,986,1000,500,-1000,-761,400,678,-26,-436,507,562,-221,-408,770,-1000,-698,547,-1000,793,1000,583,-619,-884,-1000,-1000,1000,1000,-1000,-245,-972,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{899,983,-106,1000,-402,-734,1000,-389,316,-1000,-1000,1000,-412,359,-285,646,-58,-1000,509,1000,-532,915,-941,-1000,483,-722,15,-479,-435,-1000,-359,-156,-848,-824,-1000,1000,-518,-334,431,-1000,-68,663,-747,-1000,-438,-766,-1000,22,-182,1000,197,-149,-913,113,-253,1000,-206,-50,-671,-280,1000,1000,-692,942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{53,838,-561,-163,-877,-949,444,-516,1000,407,-1000,448,-712,362,1000,-1000,305,1000,827,626,584,-481,-1000,388,479,213,-153,1000,-1000,-984,403,674,-88,1000,677,161,-424,-261,400,-935,-445,-231,-1000,-802,-777,-113,659,-153,-1000,-152,-100,937,1000,-855,413,-1000,-859,-645,522,-268,280,-1,-199,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{675,-1000,-27,-336,53,-52,-51,34,85,919,1000,-1000,-247,-7,-109,-1000,881,1000,663,-1000,1000,-892,-193,-270,-687,-468,-382,198,-1000,1000,772,1000,899,373,885,324,-1000,1000,527,1000,312,-314,812,57,55,-1000,1000,-1000,1000,-599,-1000,-123,-12,-352,425,134,-414,240,1000,830,-949,-1000,-566,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{-901,177,-756,578,-1000,142,834,1000,-191,-1000,-1000,-735,134,-1000,-330,-706,-784,618,674,861,-751,-420,-1000,-434,-851,151,-624,-225,907,-635,-23,-1000,-1000,918,-235,249,962,16,-1000,-735,-712,-1000,-742,-778,-448,-252,-1000,973,49,8,686,524,-74,-985,-347,954,-311,1000,-15,-1000,273,495,713,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Long:Mg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{1000,1000,369,-1000,1000,1000,-905,1000,-77,-94,190,237,-746,-1000,928,-1000,-82,1000,383,12,-804,-870,1000,-647,718,1000,-960,1000,696,-274,-1000,1000,640,1000,1000,1000,1000,1000,888,362,1000,-167,-562,-1000,-1000,66,-142,-652,739,51,-905,-1000,-435,-1000,-237,-1000,-937,513,-552,1000,-159,848,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{1000,715,-377,255,-114,-120,487,15,-161,707,1000,-869,-575,-780,1000,-1000,345,646,1000,1000,-832,-870,354,220,438,335,-1000,951,468,-849,-400,292,-507,990,769,-196,-745,694,796,-109,-783,-700,-389,-390,-752,-1000,494,-1000,1000,-171,-700,-879,347,129,916,-1000,277,-169,-61,-910,-392,-545,-233,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{-1000,-969,-326,553,-213,811,130,389,-260,-542,439,-1000,-993,-566,-295,-101,802,870,248,-1000,108,697,523,650,-412,46,-612,741,1000,-1000,-1000,649,223,131,591,640,400,307,641,-1000,-748,-466,-30,-287,-1000,176,1000,606,-47,15,1000,373,-475,-416,-1000,-725,1000,93,287,24,-956,536,717,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{1000,897,84,-564,127,-215,594,-636,-138,948,599,-122,1000,-74,561,-461,-948,-541,1000,856,-969,-1000,453,-871,-1000,51,287,-379,-432,-1000,-1000,-891,-743,-706,241,-14,-1000,757,1000,541,-566,-1000,-308,187,-568,-1000,-107,-1000,956,-264,-1000,-910,1000,-79,677,494,-1000,-71,120,-864,-1000,41,66,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{-995,-248,-626,543,1000,1000,-838,1000,-304,-981,405,691,-1000,514,135,45,-18,880,-607,-439,-268,-366,1000,-282,353,1000,276,683,524,1000,301,1000,81,1000,1000,872,1000,776,-1000,400,1000,474,-512,-598,-1000,1000,-585,-803,-370,-1000,-266,379,-258,-563,-143,-1000,84,301,-438,857,-331,395,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{213,1000,-341,-225,-203,-381,-584,-192,858,-196,23,578,287,-129,-534,11,-161,361,-1000,-133,-569,-715,-485,1000,-1000,969,1000,504,111,310,-542,-364,1000,1000,-401,1000,915,623,-1000,420,-935,-117,130,-1000,516,-1000,-1000,979,-137,-947,428,1000,236,-574,-978,859,-930,-490,1000,-1000,-400,-431,314,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{1000,426,-807,945,-255,-507,-453,553,655,897,-355,-943,-168,943,-948,-164,785,335,-685,-168,-1000,676,-1000,972,694,763,-285,668,-1000,-99,-190,675,1000,-633,277,1000,738,333,264,-933,-411,781,961,675,-953,622,180,1000,619,1000,232,-776,1000,-753,164,-714,285,79,1000,-226,718,-612,-400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{202,669,534,-928,-1000,919,-867,389,1000,-1000,699,271,-242,860,-381,-394,-1000,443,384,317,-38,970,274,1000,-787,737,503,499,-391,53,-1000,-696,1000,690,-63,627,357,-428,1000,-652,847,158,-888,-354,1000,-676,-547,222,1000,-880,1000,-789,-850,-423,-720,286,-1000,1000,911,-1000,-1000,385,1000,524}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{533,-1000,474,-834,-849,991,1000,1000,-903,1000,-1000,346,545,515,783,-1000,-496,-940,-1000,-160,497,359,-183,-1000,858,-720,-598,-265,-1000,-746,269,27,1000,-1000,-1000,1000,720,-843,-1000,-1000,-852,-92,662,71,-842,-882,1000,-1000,-582,704,835,1000,653,-240,-978,-431,728,1000,355,1000,-1000,72,-176,-323}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{213,1000,-752,956,-673,-318,-657,-569,935,160,-610,319,-258,774,-1000,726,-402,382,-1000,-1000,-1000,-634,-1000,-1,-1000,1000,1000,1000,-15,626,-739,968,1000,1000,408,1000,1000,984,101,-1000,-585,-396,-748,-1000,720,-648,-673,1000,1000,-1000,1000,1000,-1000,-215,422,-879,-741,-605,795,-1000,416,-1000,20,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4zMzMzMzMzMzMzMzMzMzMz", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-161,-599,-116,-1000,-773,-637,295,-405,54,-192,-1000,-1000,-205,803,-257,797,689,1000,-1000,-254,1000,1000,-495,-633,1000,1000,-537,1000,382,152,1000,1000,-278,-770,223,244,300,-500,928,1000,-1000,847,-1000,-691,288,401,599,1000,-1000,944,-1000,-198,-211,-1000,1000,-1000,-1000,-743,986,1000,1000,-567,-256,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-297,28,-756,-577,-796,272,1000,115,-404,974,-738,-525,-420,103,803,78,-422,744,245,719,-315,1000,993,698,1000,736,-215,-424,144,751,622,656,-202,87,-15,105,-548,-529,123,245,-674,-1000,-375,-1000,368,1000,700,415,-1000,-1000,-281,-266,213,-454,-680,-1000,-721,-422,362,-1000,825,-1000,84,71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{1000,-521,-472,532,-1000,1000,-1000,-1000,472,-526,172,897,-1000,509,-431,-1000,-990,1000,-60,839,240,-1000,1000,-233,629,-155,634,-794,-595,159,1000,-1000,1000,799,-1000,-725,-1000,255,503,-609,1000,117,1000,-1000,666,-347,373,-1000,-1000,-1000,222,-715,-209,117,-941,-1000,125,284,-1000,-194,79,-1000,590,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-1000,1000,498,-1000,1000,-1000,81,1000,645,451,-1000,-1000,-288,233,1000,1000,-630,-1000,555,-1000,-792,-335,-1000,-182,280,212,767,818,1000,-1000,-1000,94,-769,-442,707,1000,125,-863,-82,772,-851,-506,440,1000,-809,-598,162,-728,497,1000,563,1000,45,-977,1000,-916,-421,-1000,1000,957,352,1000,-845,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{1000,-864,979,-584,-1000,808,-587,-803,1000,228,303,379,-1000,472,-633,-37,-313,1000,95,-467,1000,754,767,462,746,1000,-211,602,-573,-549,193,1000,970,90,-318,813,-1000,418,-353,601,-556,-658,-458,-146,-34,-6,-33,-764,-283,428,-848,-1000,-920,-1000,650,-599,-1000,1000,302,614,-922,-1000,-1000,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-549,-475,-361,-331,-1000,-1000,193,316,440,-1000,-1000,-853,-687,820,-164,578,637,173,-448,-348,1000,774,1000,-1000,1000,400,525,1000,482,-549,861,942,-399,152,960,355,1000,-306,550,442,-1000,648,-1000,773,-347,855,146,1000,-1000,397,1000,638,-358,727,1000,-931,-1000,-702,777,1000,72,141,-84,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4zMzMzMzMzMzMzMzMzMzMz", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Comparable):double",
            new int[]{294,186,454,219,-275,-570,530,-74,-425,398,-42,1000,292,-770,-649,659,526,210,187,61,687,-536,1000,-95,223,1000,81,-926,-307,77,237,706,-428,830,-941,9,-480,87,227,550,-47,-928,-928,-285,-732,487,-221,118,-226,-557,-46,-1000,1000,61,1000,-604,711,-581,-18,869,-96,38,474,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Comparable):double",
            new int[]{-1000,265,-283,-502,-1000,-754,475,-1000,80,1000,-100,729,1000,-480,93,1000,742,-41,-410,-295,-655,566,-1000,-1000,-1000,-820,1000,-1000,364,1000,1000,-1000,158,1000,-1000,-319,352,1000,1000,1000,918,-1000,-1000,-1000,-1000,705,-374,-1000,1000,479,-781,-1000,79,767,783,696,-72,-1000,-1000,104,-431,1000,-441,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Comparable):double",
            new int[]{-560,-368,-101,-268,-284,305,-934,-147,797,479,1000,1000,12,743,-454,231,-320,1000,412,-317,1000,-248,-887,-307,-118,807,0,-668,-460,-148,272,-276,-119,417,-503,1000,1000,-717,-481,920,1000,-414,203,383,-1000,600,-229,-600,313,-641,388,-964,-251,1000,1000,-194,-869,-1000,231,757,1000,-319,-480,387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Comparable):double",
            new int[]{468,-1000,779,-1000,894,514,-932,931,318,-411,364,770,-210,401,-497,-813,-23,-1000,580,733,1000,-223,-1000,-675,130,606,-375,437,-574,-261,-1000,-855,-836,485,210,1000,-191,-170,-791,13,391,1000,368,1000,595,-447,60,1000,-110,-768,427,742,-786,-367,117,-525,-611,809,906,686,262,-287,311,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Comparable):double",
            new int[]{-886,1000,747,550,-245,-710,-150,-8,-1000,-146,-287,655,274,-1000,-785,-558,826,514,-1000,1000,123,-1000,1000,-401,127,1000,690,-46,338,-875,-713,654,1000,-287,-651,-284,-1000,276,1000,-222,-357,386,-1000,-670,-305,1000,88,-45,57,37,-1000,957,-1000,-172,1000,466,1000,726,465,675,96,74,1000,659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Comparable):double",
            new int[]{468,-832,779,865,894,11,266,599,701,-411,-190,524,-574,-910,-441,-177,-687,852,580,312,112,-926,-483,-382,-37,-916,-337,165,-358,-979,-90,-237,854,260,762,-411,-249,-975,326,-7,391,-801,-910,-326,-523,-407,109,-468,-621,-369,-750,742,-811,-238,117,-504,-650,777,371,202,262,-372,311,967}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{53,1000,-232,515,-868,-863,-1000,-1000,1000,-199,1000,1000,250,-840,1000,178,-1000,-1000,1000,-1000,235,910,-912,1000,-873,352,-649,-1000,850,1000,-628,512,-594,191,-536,82,436,-1000,1000,820,1000,-1000,-430,-212,-609,1000,1000,-929,1000,-1000,-323,1000,-472,-679,-171,1000,1000,-1000,539,662,311,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{99,-333,-721,-594,637,174,1000,947,-1000,165,651,520,1000,1000,-197,1000,-96,232,-386,303,-849,487,1000,-1000,1000,337,97,-1000,141,602,-608,-741,1000,1000,-472,-475,1000,-92,-912,-16,-1000,-342,1000,-699,921,751,-1000,48,934,1000,-615,-152,-11,365,469,-218,775,554,-516,-645,1000,-354,-1000,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{405,550,-871,539,767,545,-646,-413,968,416,-358,727,-1000,-1000,184,-1000,1000,-97,847,49,149,-577,-1000,-192,-342,249,-828,-632,-1000,-468,855,456,-356,-324,308,438,-47,-52,1000,-176,-177,954,-364,680,410,-1000,1000,363,436,-1000,1000,486,1000,-16,531,-719,-164,-1000,1000,1000,-796,324,-122,813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{-459,1000,-756,951,-1000,-387,-1000,-1000,1000,-549,1000,1000,469,-1000,1000,-470,-1000,-1000,949,-1000,36,1000,-1000,1000,-1000,825,-940,-1000,628,1000,-1000,757,-1000,44,576,391,120,-1000,1000,783,926,-1000,-1000,599,-1000,1000,1000,-1000,556,-1000,-1000,1000,-142,-1000,-591,1000,1000,-1000,-502,834,806,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{-149,-224,923,1000,-136,1000,-681,871,299,214,497,851,-1000,300,-655,74,688,409,1000,1000,1000,-1000,256,-963,366,-1000,-649,86,-904,-733,522,-312,1000,1000,1000,-503,-559,1000,-1000,1000,-808,385,-1000,1000,-1000,-476,812,-456,-124,-1000,-1000,-1000,953,-164,1000,60,-378,686,-313,1000,-475,19,553,-667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4zMzMzMzMzMzMzMzMzMzMz", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{1000,-1000,-916,-807,-388,142,-958,-689,-609,-844,1000,-1000,60,1000,-161,-1000,-773,-1000,333,1000,-767,-63,-882,-838,-370,-1000,1000,-1000,-1000,-1000,274,-1000,138,-1000,-149,-924,-1000,710,107,-1000,-1000,1000,1000,-1000,-118,546,-149,-511,7,-733,-478,-739,-854,-1000,-613,-1000,1000,-462,-618,501,1000,813,551,607}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{-1000,-609,894,-865,318,1000,-969,-131,-438,-1000,-1000,228,-741,-119,1000,557,-725,545,-151,498,-458,-418,-605,-870,362,-853,-263,381,960,-537,-48,822,-573,224,-478,-22,1000,356,691,1000,1000,-256,-8,245,391,-95,199,0,-1000,739,-868,-484,-172,234,-35,1000,-383,-943,961,-1000,1000,302,686,-167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{1000,-1000,894,-672,1000,676,-987,-1000,-1000,-783,1000,-830,182,695,-1000,-730,-516,-286,61,1000,-1000,1000,-1000,-742,-484,-1000,456,-318,-1000,-622,1000,-1000,1000,-846,-1000,106,-1000,1000,-400,-1000,-1000,756,970,1000,-1000,-75,-183,257,297,-1000,-113,278,-1000,-34,-673,-1000,742,-499,-1000,977,-179,1000,686,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{-457,876,-292,-29,627,779,606,-912,-838,287,373,-553,-898,780,903,-528,-880,249,59,-944,-547,925,-933,-22,-518,-884,-743,282,200,437,-273,79,828,63,-624,648,-45,544,-349,756,810,-667,366,-365,-483,196,-23,-772,537,-612,-174,965,870,668,-963,602,66,-969,967,472,598,-648,-559,-130}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{-1000,-1000,-621,-807,457,544,-976,-1000,52,-884,-388,-1000,-450,1000,-1000,-896,-414,-375,-489,1000,-203,1000,-1000,-799,-130,-1000,981,-1000,-1000,-1000,1000,-1000,1000,-782,-1000,-97,-1000,1000,-1000,-479,111,1000,442,1000,-1000,301,310,263,-43,-1000,-1000,78,-1000,-1000,-586,-1000,1000,-629,-314,-1000,-638,1000,550,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{-143,238,-971,343,551,-304,-681,-664,-625,-242,270,-175,-109,-24,11,-759,978,346,379,-52,1000,22,303,1000,316,662,345,63,216,178,8,-256,-125,233,586,333,848,-679,-36,360,-37,-689,-351,380,-171,1000,-1000,1000,-358,-358,-283,-822,-92,664,924,-1000,-193,807,-132,-148,102,99,578,930}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{137,197,-96,-286,424,-1000,-401,751,268,-3,-780,-984,882,-1000,-501,181,304,537,1000,-1000,-109,-672,-1000,-906,708,1000,1000,1000,1000,-811,381,-1000,554,900,1000,-1000,1000,-57,380,-441,-1000,-274,864,-1000,306,679,-226,577,414,-559,274,-570,1000,-229,49,264,1000,1000,697,-329,-984,1000,-563,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{1000,-1000,464,-920,491,-956,-467,-171,-77,437,650,1000,-611,1000,-960,67,868,253,-266,1000,-755,839,1000,306,-908,-251,-44,-1000,-387,-855,466,1000,-214,318,-1000,626,1000,174,1000,1000,1000,452,-675,259,-459,34,487,-392,-81,140,-629,447,-977,1000,1000,-544,111,1000,743,-1000,132,-555,-512,-53}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{197,20,857,-574,1000,-1000,-216,39,-664,55,462,-612,-207,1000,-256,-518,-13,1000,931,-647,355,-285,106,640,15,-265,581,-316,1000,-186,730,-320,172,-42,-54,-292,1000,-135,656,445,174,514,-259,-89,23,-134,-1000,968,581,-792,-321,30,682,-576,-130,-1000,1000,1000,-697,-174,-849,430,328,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{-381,-925,-637,-1000,528,49,-620,-1000,-887,725,-106,838,515,-93,770,-920,1000,90,1000,419,715,-771,-273,945,-1000,-57,155,-234,1000,227,1000,-214,-23,1000,-805,1000,-573,-928,943,-630,-230,-589,1000,-691,-1000,521,268,601,-812,-525,-633,360,-633,-11,-682,656,-110,261,-303,1000,447,368,-334,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{-1000,296,-262,1000,-737,77,-773,676,-212,-851,-930,-810,554,593,-1000,-1000,-540,703,-631,-987,115,-1000,1000,-619,647,321,-68,-1000,-580,-297,-1000,937,-205,56,-383,-1000,236,537,52,535,465,1000,-92,-242,703,-701,-844,18,1000,877,-710,899,-1000,689,-588,1000,1000,-937,-1000,1000,-995,-1000,-789,577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{-1000,465,254,1000,-441,-799,963,704,-379,725,45,-1000,-115,600,-481,1000,-1000,263,1000,-567,-270,110,-28,-1000,576,-1000,-540,121,-678,-621,-1000,-214,910,1000,243,-277,1000,-928,149,1000,1000,1000,-1000,74,1000,-778,-969,-796,539,66,127,3,-123,424,-387,-352,1000,-517,-1000,-428,-756,-1000,67,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{-502,-527,-296,802,-920,-411,-24,-462,-763,-212,-848,-112,620,-147,225,690,-38,340,142,-51,645,-1000,-707,-497,-308,160,844,-909,-1000,149,49,-81,132,-583,-400,264,-1000,228,-1000,-44,535,-111,559,-82,-124,-240,-699,-1000,255,-468,-1000,274,-579,-147,299,-207,705,-94,-302,-251,-580,-914,-124,-310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Comparable):double",
            new int[]{-694,-433,933,809,1000,-510,-451,19,-1000,-170,236,1000,488,-1000,-806,324,-1000,-846,-162,37,-1000,-420,-1000,-826,-279,752,931,-931,578,1000,9,224,-227,-855,-1000,-655,-1000,156,-1000,-583,1000,1000,-1000,-62,1000,-885,-888,-99,-957,-1000,1000,872,223,-716,473,37,387,-1000,-1000,-104,-72,-1000,91,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Comparable):double",
            new int[]{-1000,-1000,-281,54,463,683,480,-165,-593,-910,-1000,1000,-791,174,-259,-926,-217,-975,591,-560,-400,981,-993,-15,-748,75,1000,-1000,-307,238,1000,-1000,1000,-1000,-1000,-1000,-331,-160,458,-628,1000,265,-1000,-670,-208,-321,-1000,-404,-1000,52,792,917,736,-162,-1000,69,-682,-1000,1000,-835,767,-169,-816,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Comparable):double",
            new int[]{-206,-403,-611,360,-64,-319,480,-860,1000,1000,742,-687,1000,-1000,-235,-919,1000,-954,-793,-47,1000,-797,-1000,-565,212,-1000,-66,-1000,-558,-505,83,1000,1000,-429,-1000,474,-850,-796,1000,-874,1000,777,92,-684,150,-861,-1000,-129,273,683,1000,-638,-31,-1000,-487,277,-1000,1000,-342,-1000,-993,556,-306,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Comparable):double",
            new int[]{-27,36,767,487,628,324,261,-561,-171,-317,72,410,-56,-1000,-365,-171,454,-1000,-326,-261,-165,377,-956,1000,1000,-1000,128,-570,-1000,-445,-610,-829,422,-1000,-1000,-551,-55,98,-910,-639,578,1000,-171,-585,371,-194,-916,-506,-400,-326,913,495,539,-76,-214,182,255,20,-1000,-580,156,87,-510,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Comparable):double",
            new int[]{-399,519,519,564,587,737,364,-100,-790,-894,258,335,998,-337,-431,277,-414,-143,-396,873,677,-103,-340,-198,958,529,-388,591,646,-457,-835,146,178,-458,-232,655,-130,830,517,-360,293,-389,330,-438,591,-886,-797,87,-24,-221,-627,-110,259,139,892,706,406,694,-748,233,-135,695,-482,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{-296,1000,-731,-628,-1000,816,-343,-691,-1000,-1000,636,-1000,-872,1000,-410,240,-1000,370,18,-1000,1000,-519,-734,-1000,599,-1000,-168,335,109,-667,643,-77,368,-847,90,1000,428,-907,304,698,511,-513,1000,33,-846,916,1000,979,1000,861,-177,-1000,-1000,1000,795,977,-1,-37,258,1000,-1000,56,718,909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{843,-149,-663,-73,-833,926,-311,261,-634,-689,-124,-154,-145,739,-64,814,-459,-31,505,-934,225,-823,351,-141,591,-322,-21,296,-232,-646,327,43,117,-301,-338,-245,-104,-210,204,-216,774,-298,707,-415,751,868,242,877,12,-263,-211,-873,-534,-308,-132,203,-728,656,427,139,-457,65,-541,-123}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{-908,-438,659,-1000,291,-300,-695,12,-466,-774,1000,312,-161,76,-173,-862,149,427,317,1000,1000,225,329,1000,-856,-1000,-1000,-456,181,1000,-63,499,-566,1000,754,-796,146,497,-820,-1000,-457,1000,1000,-1000,459,-169,125,126,-104,876,-542,-334,-531,-30,1000,36,306,-830,-281,453,-63,-892,-1000,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{-1000,-676,-747,-959,1000,-1000,-93,-1000,804,492,1000,297,1000,253,776,42,-1000,-690,1000,1000,945,1000,-669,-577,-551,-1000,915,1000,-1000,-84,-636,53,946,190,-1000,41,1000,-492,161,-1000,912,1000,1000,12,826,-444,-271,-436,-1000,-737,828,-1000,796,1000,-295,1000,395,435,1000,-660,-1000,-1000,-307,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{734,1000,223,-120,-675,-1000,-967,-1000,-829,-1000,-1000,-1000,-1000,1000,1000,1000,-765,1000,1000,-1000,1000,-1000,-20,-1000,1000,172,1000,1000,-511,-903,1000,-1000,-381,-1000,1000,1000,1000,-1000,-243,86,1000,-1000,535,1000,529,1000,1000,1000,1000,1000,1000,723,-293,1000,-776,893,-1000,1000,1000,1000,-1000,309,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{506,519,49,-605,-73,677,-783,-452,-145,1000,-235,247,-63,-74,1000,53,-1000,-428,-144,239,-236,-72,-274,-86,400,-158,951,-1000,76,-825,1000,-203,-30,321,61,-442,780,-652,344,-264,883,-614,-4,-1000,-923,-77,149,1000,-30,-116,-1000,-1000,-505,-51,-287,-110,-53,-17,-1000,41,1000,150,400,-336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{-336,-1000,-291,601,-1000,-426,594,-584,288,495,1000,753,-1000,-1000,-1000,-468,832,-148,163,846,1000,222,-431,-846,-1000,1000,-431,204,-611,676,-820,45,-616,-417,512,193,446,1000,-1000,393,292,443,-568,-221,1000,1000,193,1000,-1000,-1000,72,-1000,-922,1000,447,-408,325,-1000,487,83,-599,503,-417,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{-898,-238,269,1000,914,-388,1000,24,633,590,-220,-1000,545,-287,-357,567,1000,1000,-647,-1000,397,686,-76,119,219,-998,98,894,-841,36,88,995,44,-614,443,7,-293,-748,-370,-1000,-1000,1000,-1000,-1000,-658,270,-570,1000,19,648,679,1000,196,-896,-1000,-385,-241,1000,-701,427,-1000,-1000,-231,382}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{-1000,164,-276,1000,-203,100,-250,-665,1000,628,1000,-22,265,423,18,665,298,746,950,-1000,-539,937,-1000,719,395,-1000,452,-613,1000,783,156,695,-328,-899,392,-1000,-586,-512,-1000,-1000,120,-467,-748,804,-892,-324,-1000,-453,-1000,736,1000,1000,-117,556,-710,993,-46,1000,507,67,-718,-210,-1000,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Long:Mw==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getSumFreq():long",
            new int[]{1000,-235,-556,820,-436,-618,326,358,393,-263,-272,1000,246,-694,1000,-617,-399,888,428,982,-390,-941,580,-740,588,-408,-186,480,820,1000,315,596,-68,646,-97,-429,326,1000,-382,615,393,232,-381,-1000,755,-1000,1000,-1000,-1000,-8,1000,-810,96,-499,385,661,500,-294,356,70,-637,1000,1000,744}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getSumFreq():long",
            new int[]{1000,280,253,221,-571,-1000,1000,320,-333,565,727,571,-1000,-270,1000,-454,-125,-77,167,142,-1000,-521,1000,-1000,572,-490,-1000,617,-116,656,973,139,-48,-559,665,84,565,400,247,832,1000,-152,-316,-459,1000,-858,1000,0,-1000,-1000,1000,-161,-390,-1000,-464,149,1000,518,-695,343,332,1000,1000,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getSumFreq():long",
            new int[]{-115,-1000,-101,567,656,-339,-12,64,-37,-964,-1000,-847,153,-458,-463,-1000,-382,1000,951,835,1000,85,-773,-326,-835,228,354,-255,1000,505,208,-1000,702,182,-59,101,-832,-343,-726,-99,-706,210,-540,339,-464,-251,910,118,1000,1000,1000,86,808,227,-950,708,-560,869,1000,-1000,90,836,-400,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKLTkyMjMzNzIwMzY4NTQ3NzU4MDgJMQkzMyUJMzMlCi0yMTQ3NDgzNjQ4CTEJMzMlCTY3JQoyMTQ3NDgzNjQ3CTEJMzMlCTEwMCUK", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{-1000,-405,124,-492,-1000,501,236,466,1000,-1000,302,-1000,151,314,-1000,1000,773,469,-1000,-337,-41,385,356,-1000,774,1000,-878,1000,1000,-429,776,-917,359,1000,1000,-1000,-44,-1000,-691,166,1000,577,-879,459,-1000,185,-1000,-1000,-885,-1000,-731,1000,-1000,1000,-341,-1000,33,-1000,1000,-1000,968,115,-292,-848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKMAkyCTEwMCUJMTAwJQo=", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{-72,1000,74,305,-225,1000,803,457,-965,136,198,151,1000,-355,241,-144,-903,872,-204,926,491,96,-18,-184,-161,104,566,192,-884,134,-1000,-319,1000,-1000,-102,-124,412,1000,1000,935,90,780,600,-1000,60,-1000,1000,-409,368,1000,158,-1000,1000,-1000,1000,607,-106,-589,-1000,656,51,-475,-167,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKa2V5MAkxCTMzJQkzMyUKa2V5MgkxCTMzJQk2NyUKb2JqZWN0MAkxCTMzJQkxMDAlCg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{-1000,838,259,436,-305,772,762,319,-1000,-680,120,-493,652,-549,1000,-1000,-718,1000,809,109,99,759,-1000,44,120,-98,313,395,-858,428,-589,-506,1000,-324,-545,-1000,-155,673,1000,831,-131,768,-177,-327,1000,-1000,580,273,-550,1000,-158,-915,1000,-1000,1000,-209,-1000,-232,-583,-346,73,130,-956,-368}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKFwkxCTUwJQk1MCUKaAkxCTUwJQkxMDAlCg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{442,705,-81,-280,791,158,-58,982,-1000,428,-440,56,1000,-247,1000,-408,257,911,-151,1000,-625,754,399,-113,-173,-183,26,-803,-729,-1000,451,-501,-189,-887,81,59,258,938,1000,1000,-432,774,776,-1000,733,-1000,-181,-701,464,165,-316,-1000,1000,-1000,1000,990,705,40,-862,1000,859,892,488,-221}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{-469,-821,-796,-1000,-556,356,73,484,-400,-290,830,-578,479,413,101,-497,-831,-236,-1000,869,1000,638,760,702,-814,-557,1000,483,-650,-1000,-340,-151,479,-396,-95,1000,355,867,695,-1000,-934,-1000,-130,1000,-656,49,644,243,-216,-49,118,-1000,-883,-1000,311,-218,478,546,-1000,-94,417,792,916,459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{-469,1000,-896,-317,-407,356,945,1000,1000,207,585,-1000,-875,-407,-577,-1000,-359,-385,-574,-311,-205,762,1000,-456,287,231,1000,13,309,99,-355,199,853,-463,1000,3,234,825,177,-531,-160,-63,-711,-399,-1000,-154,769,437,1000,447,-29,-668,-78,180,1000,1000,226,-680,393,1000,907,-11,291,895}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{1000,-149,-717,-367,-639,620,-1000,-1000,368,647,1000,259,-297,262,1000,1000,1000,1000,208,929,-1000,-1000,1000,-137,235,-1000,-201,952,-918,-1000,-802,969,1000,509,-346,-545,338,1000,112,-441,555,-703,-465,1000,-299,-247,-667,1000,370,-561,-396,326,-1000,300,92,225,-67,1000,-1000,28,332,-307,920,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{731,-182,649,126,-333,627,1000,-154,1000,1000,417,955,-1000,-4,592,648,881,995,249,-195,-1000,-1000,-124,271,667,-260,-638,932,-367,239,-116,640,47,577,713,-1000,-407,632,-811,-1000,953,807,-804,-945,1000,237,-751,1000,1000,786,271,1000,-938,952,471,520,-1000,-164,-1000,1000,-437,-1000,-901,-234}));
    }
}
