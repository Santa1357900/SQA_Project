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
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(int,int):int",
            new int[]{504,-1000,779,-635,-584,-220,-649,1000,1000,-144,-1000,-559,96,-458,872,-387,-58,494,-198,480,-16,715,-586,246,-116,-941,-16,-486,-539,499,6,-1000,173,-1000,143,-473,-877,1000,-458,285,0,-899,-841,-772,1000,392,522,1000,1000,88,-1000,-600,-1000,919,-469,931,124,78,-35,-257,752,107,248,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(int,int):int",
            new int[]{312,-44,-561,374,-476,-22,-2,-424,828,-543,781,465,83,492,900,-778,409,999,-100,77,191,842,-42,144,-250,173,481,-193,833,628,-88,-726,-926,-781,-885,-106,-873,32,785,-118,-688,177,11,203,-688,-174,300,-450,689,-373,-796,-420,-188,297,-1,876,181,793,790,451,50,-917,-119,-938}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{86,-322,-243,-284,127,702,366,-658,-5,723,-808,-559,765,-965,-17,171,617,755,-142,278,-638,529,-211,-486,-693,-925,206,228,721,-368,-628,-478,557,-301,-944,296,896,-426,-835,-649,549,-813,474,-723,-286,420,334,295,-814,-259,378,445,670,-674,-401,-451,-981,44,904,237,859,711,284,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-96,1000,-503,-291,-444,-171,-257,-463,336,1000,284,-121,541,192,127,-588,-65,-959,27,-298,-213,-448,90,-969,178,-246,665,-556,564,1000,235,-443,540,61,635,239,1000,-309,515,-99,-422,-383,-29,912,458,53,83,-332,-397,-681,667,47,567,799,980,357,657,-729,-304,518,-502,-1000,277,603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:OTg5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{989,-13,-535,175,283,-261,-581,-105,-584,-481,203,90,279,-865,865,-748,598,-869,-973,169,-414,-251,188,18,-487,-748,-892,531,201,-577,-333,370,-113,383,492,791,-386,987,751,-480,-607,-984,-1,302,734,-840,-273,-938,-169,312,355,-195,680,-864,-184,444,-295,26,577,580,-888,593,986,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNDcwNzI5MjE1OQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{996,761,-519,-848,244,79,447,-646,728,812,-93,701,-115,168,145,-412,-178,-447,798,-545,-220,277,133,-579,723,367,912,-462,698,913,886,-574,2,721,818,13,971,157,73,-114,239,-967,-719,396,-291,-863,-661,-974,124,-434,137,820,154,453,760,642,210,-193,-343,961,-197,-734,271,464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-577,548,-513,99,177,709,-659,-328,1000,-266,-701,-285,-407,1000,-519,1000,-755,41,-125,-472,515,-129,-115,-521,1000,869,1000,-1000,560,1000,394,-136,-135,-140,-316,-768,612,-431,-1000,1000,682,958,1000,-394,345,893,1000,495,-905,-1000,-736,-1000,-1000,1000,383,265,593,-525,-971,-1000,1000,-2,-1000,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-529,-1000,137,-913,-256,-1000,20,1000,1000,-1000,-960,1000,-232,-915,920,272,-586,-522,-140,-232,996,196,150,-1000,641,-194,1000,932,274,96,915,318,-1000,603,660,1000,-203,1000,468,319,99,1000,801,1000,484,-1000,-552,138,228,-581,442,-637,35,54,-344,-216,-305,882,221,908,708,172,121,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{628,-722,955,-886,-539,-598,319,832,748,-696,542,-232,392,280,-70,240,-419,241,-625,-933,986,-524,959,-891,484,-782,842,-979,-91,-97,616,-733,219,454,455,-604,-647,-64,590,-862,-917,459,914,-48,187,-620,-160,340,-623,-503,944,-573,609,222,-640,-861,-370,495,-571,-401,-99,-383,676,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-186,148,-589,-279,86,-799,102,-445,-125,-277,525,219,767,-284,-694,-99,-575,-29,-894,-461,894,-214,359,-909,561,-941,-26,19,147,-753,549,-887,-74,-889,-254,770,-674,170,-271,-640,-960,-622,781,420,-190,618,-404,-926,185,339,-903,283,-298,-108,-883,335,-795,-61,-240,307,271,690,-114,160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{825,769,-707,224,-524,50,-893,-183,-266,701,-29,-103,-424,-558,333,204,-905,277,-198,-642,921,88,-929,493,-246,-420,758,-54,-912,-472,887,-919,-931,732,-62,347,332,62,-481,158,675,569,54,-675,456,745,162,371,728,698,-181,343,-719,-698,844,-532,16,125,-245,372,362,-603,-344,-515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{432,-911,835,-759,-558,-948,332,-425,286,-985,442,-706,-390,130,-888,892,333,-470,384,-925,-310,-71,-724,157,-640,62,10,-167,440,-633,-449,402,953,-217,572,492,-564,533,143,849,-806,305,748,884,-171,-583,520,291,-235,182,9,299,-339,994,-759,-109,-131,434,-664,-330,-655,100,-438,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-917,426,167,110,-92,348,503,-651,818,-324,-878,393,-253,-609,905,253,-501,337,908,979,672,-985,121,-929,-371,-59,720,419,-876,-688,574,152,45,158,932,768,673,156,-579,415,822,-158,740,328,302,-892,-221,52,224,-481,277,-671,-129,-713,597,278,-921,506,694,884,375,664,956,681}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{630,-813,-752,-792,545,-646,401,855,833,-657,-740,704,531,-622,-748,832,-179,656,-175,-605,-920,289,197,-845,219,693,-721,692,-449,-494,625,569,-786,554,436,-382,-850,390,-161,99,-392,488,-84,554,-804,310,-686,-153,-406,-856,-362,-899,-738,872,-707,13,653,-363,-34,401,798,-248,-507,712}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-664,-765,14,-979,162,-31,-103,-360,337,284,310,-102,922,-132,-994,-47,979,203,-441,505,-518,-895,-802,446,734,810,57,873,-389,-193,-158,-179,808,-926,-949,-959,-788,-648,-908,-323,483,-323,917,-198,2,503,-892,-571,428,-549,-252,-180,-693,-49,-118,-284,383,889,-149,-543,-401,715,-586,844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{994,-424,-317,-587,381,-324,707,382,-695,859,530,458,-853,-197,-536,288,198,-91,-933,-548,-303,-194,495,-525,-194,94,498,211,448,177,-299,-394,-154,-550,928,-246,463,-892,538,-228,101,418,-995,32,900,420,330,-974,-870,704,-790,-709,-169,-834,422,652,591,473,313,-623,47,324,-59,717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:NDUuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{106,-638,23,-734,241,-122,-1000,-1000,-991,-997,10,536,1000,-1000,-1000,-1000,151,-364,571,-74,1000,861,-1000,-969,892,1000,1000,-254,-1000,-376,1000,-231,1000,-627,1000,-997,-1000,1000,980,-1000,1000,687,-230,-583,-593,-1000,430,178,150,480,9,1000,1000,1000,1000,1000,970,-1000,-851,351,-1000,933,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:MS44NTA2ODUyNDA1NjM4MzM3RTEwOA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{510,143,1000,286,568,-683,-952,-1000,-320,-1000,321,841,656,-1000,-757,-824,1000,-241,-569,529,1000,1000,1000,-939,-302,1000,521,339,-1000,-560,860,-828,-20,-1000,915,-487,-486,491,-717,312,-20,375,1000,869,-952,-171,1000,-543,-548,-95,716,767,1000,-36,581,-52,1000,-1000,473,-996,-1000,1000,1000,588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-304,950,179,-567,8,372,608,114,176,955,-862,79,55,360,-642,702,-632,76,631,430,-451,165,-880,-92,-411,844,102,-48,-70,-732,58,-264,498,-352,-342,101,617,643,-130,-491,-284,-97,-813,-169,497,-453,212,-91,554,408,720,622,308,951,-304,34,540,905,838,-833,-142,-141,538,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{934,-648,449,351,521,-718,420,-949,-61,-89,-239,-350,-47,-880,-350,-680,798,-834,762,586,489,-290,175,511,463,566,37,-624,-855,303,-475,503,-952,-381,-768,-608,-313,-781,-744,-7,747,830,997,585,-296,945,-33,-532,-576,-106,717,-508,129,-69,-543,281,557,-839,857,-37,-837,-658,-911,186}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{633,610,361,1000,957,634,952,1000,959,189,658,63,-872,-1000,214,-1000,675,-970,-547,-84,-215,980,1000,-831,685,559,-1000,1000,-1000,65,-400,159,-1000,-1000,1000,-1000,-1000,-765,-1000,658,-1000,1000,1000,-404,-1000,-12,-1000,-1000,-1000,1000,1000,-197,-450,-510,-1000,-1000,1000,-1000,-396,-643,-1000,859,-818,-748}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{976,-360,-1000,158,-1000,-614,1000,466,-139,-7,-1000,1000,-827,-65,-143,279,1000,120,-632,523,-1000,-1000,-1000,-250,-257,792,512,359,16,284,-276,1000,804,-63,1000,1000,-217,986,580,-1000,-1000,-725,-552,-838,895,-1000,38,1000,-641,-110,-1000,-603,-398,-1000,1000,1000,-152,-528,831,-1000,209,-614,-184,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{1000,104,-89,564,-900,169,1000,-319,-433,765,-757,899,611,-1000,-663,-654,1000,-249,-119,1000,1000,509,-652,351,-1000,302,-307,475,-770,356,-421,1000,25,-142,1000,1000,171,337,-627,-867,429,-475,1000,485,198,-1000,34,-277,-783,-154,-791,-925,-162,-251,427,-541,375,61,1000,-280,-945,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-489,-729,963,277,-819,750,239,-217,596,785,-405,186,381,-705,-353,725,268,120,295,463,781,35,887,997,-880,-255,439,561,-853,797,-276,4,411,-843,688,-376,-217,986,789,-505,-535,-940,562,557,895,-755,38,-493,-641,-569,-569,-691,-965,429,-20,-984,602,-528,-253,-500,-439,162,743,-299}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{-16,644,640,559,-515,-233,-151,-195,975,144,-812,788,-872,-990,-292,629,252,829,722,54,-179,514,766,398,952,657,908,-745,718,-121,301,297,-944,796,-211,-537,-466,493,886,398,236,-91,509,821,666,-923,483,277,691,755,-994,697,277,-304,-962,69,67,506,-130,590,678,-148,-975,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{716,223,235,769,186,-762,-967,-287,780,939,-411,-653,483,634,619,912,419,778,-717,-218,262,301,759,721,313,851,130,-334,-906,503,-595,-13,-859,854,405,-137,191,-144,-198,100,-11,204,-398,486,516,-327,-958,-383,-377,-620,29,-2,749,643,5,-787,717,843,-548,943,-587,-533,118,702}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{734,523,-955,-1000,-640,-459,763,883,-628,23,899,496,-103,-588,-863,-1000,743,-498,1000,70,-491,1000,166,-737,824,-78,311,-580,-1000,-922,465,-1000,951,-700,515,-660,107,-225,570,588,259,1000,852,991,-19,745,-527,1000,-14,-38,932,1000,-728,649,513,434,912,590,759,310,-125,-1000,765,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{173,530,537,27,153,384,120,-619,548,-120,-178,-614,-31,-298,-346,56,-730,-487,105,363,235,-216,-192,-700,-620,203,-884,-350,853,16,-725,-788,823,-916,-412,-721,-729,-433,-262,-283,-390,728,-767,205,194,-435,888,-115,435,-278,159,948,6,440,-814,-446,374,201,-509,-118,-207,134,393,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{357,351,503,828,143,-91,-405,-384,878,138,-999,808,194,-800,-886,-253,745,-272,173,-997,250,-159,466,446,870,-830,-257,369,775,-186,-156,506,123,629,299,-186,208,-671,388,492,49,511,998,870,-673,889,-485,-496,228,-218,903,-872,-485,-777,202,-78,-86,-78,-300,996,801,756,208,946}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "compareTo(double,double,double):int",
            new int[]{-371,511,333,367,-839,-308,-1000,-627,215,-400,-889,-268,-40,1000,371,-179,-291,-371,474,-277,30,-476,976,400,1000,-699,334,-328,-400,1000,-878,650,-1000,177,-329,548,-288,689,-185,-882,-594,55,310,-573,-372,-891,-90,872,273,-488,-943,-255,299,-1000,156,-591,-1000,374,-816,-346,243,234,-2,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "cosh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance(double[],double[]):double",
            new int[]{793,-293,-508,85,-387,-727,873,872,313,676,776,600,957,-792,-200,514,63,371,-195,-274,-553,564,-768,221,-974,562,640,-742,779,55,-810,790,-930,278,-55,-360,-689,-201,792,679,571,-86,554,-13,116,777,453,83,-871,-611,-579,725,879,-133,21,455,-531,-958,-319,133,960,375,-183,16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Double:My4wMzcwMDA0OTg1NjE4MzYyRTk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance(int[],int[]):double",
            new int[]{-609,-70,-143,947,471,-185,627,-315,493,-543,889,-4,-216,-258,-657,-243,362,-435,-835,-395,-32,-837,-772,273,505,734,237,-883,199,955,48,318,-440,-681,-784,-191,575,454,224,517,478,-22,-233,174,-98,-152,973,658,256,678,-492,574,769,-994,136,-368,-200,-855,500,271,-283,-103,634,921}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Double:NDgxLjA=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance1(double[],double[]):double",
            new int[]{-485,392,-924,595,481,-757,-256,-192,-950,-768,173,-847,577,-250,141,232,-262,17,608,792,-743,273,704,-517,-866,196,-945,-746,35,177,885,-904,-642,678,-895,-499,-273,-802,-260,579,-819,-416,-527,-967,173,369,-191,-628,631,-372,464,-325,815,-558,-109,961,442,-525,-398,-171,-298,397,880,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distance1(int[],int[]):int",
            new int[]{271,7,678,470,247,-360,218,-302,822,-44,-478,-206,712,946,-297,353,867,41,323,-418,-88,-265,826,394,-530,-539,103,-551,584,365,295,797,-265,210,417,728,-925,-519,651,-287,376,975,-801,935,-39,-371,505,752,405,-853,328,712,139,-863,-332,-786,285,852,-122,-217,996,-588,-602,45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distanceInf(double[],double[]):double",
            new int[]{992,848,399,621,860,-615,530,797,893,-53,188,-142,814,-870,-609,-548,-737,-485,-386,-888,326,-520,403,835,358,609,419,-54,-413,-88,496,860,-889,-61,-460,-367,813,-938,-809,762,-565,678,34,-359,613,-83,845,223,5,-477,894,42,831,-957,-407,-482,114,-819,-14,-341,-307,240,454,-271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "distanceInf(int[],int[]):int",
            new int[]{123,-9,508,516,-122,-674,550,711,658,-983,-23,801,-637,338,442,-752,922,-190,-954,514,450,785,436,439,-308,-950,337,420,25,83,-552,-821,143,44,-220,-113,-537,600,721,-63,300,-794,-621,763,874,-353,-925,-248,-642,289,-732,-483,330,-246,-364,-408,-772,-430,553,-54,-285,679,-203,621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double):boolean",
            new int[]{-456,-521,4,-914,338,650,-486,-125,-246,-40,-847,-846,-843,-765,-575,340,-831,500,-869,940,-267,-986,69,-100,-634,-868,59,826,142,-945,-15,273,73,-219,547,706,-84,-412,-307,-95,-816,228,358,636,134,711,51,171,873,1000,-934,954,-666,-759,657,-918,880,278,410,578,-463,73,-822,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double):boolean",
            new int[]{-1000,487,-793,-989,-342,1000,-285,-494,-1000,-71,1000,-1000,1000,-845,-1000,-916,97,-1000,1000,-405,324,525,363,-1000,1000,-1000,-39,1000,20,1000,-1000,1000,911,-1000,154,-75,1000,-840,-1000,286,-625,-355,-820,1000,-1000,13,282,-1000,-238,717,1000,1000,-1000,1000,9,198,-811,901,-1000,-492,1000,-92,957,192}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{-379,-605,98,1000,342,740,70,-611,-269,553,1000,-787,234,949,-1000,594,-413,-580,-398,117,-1000,2,-1000,438,-410,550,-1000,1000,865,1000,637,-419,958,-38,232,128,514,402,-768,-755,898,368,-255,-334,351,698,-1000,886,56,1000,-91,246,-398,-803,-116,45,-534,548,38,802,-199,328,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{-290,515,-333,-241,-804,-487,947,-483,618,-947,-270,889,-314,-105,-634,955,-959,-19,-824,-506,128,-466,-420,251,-10,-985,-167,-808,156,-287,-946,-433,241,453,769,-271,-933,881,602,24,-815,95,-75,-192,522,-979,-703,644,-467,-153,349,-903,-267,90,-605,177,-241,417,500,447,234,205,13,100}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{537,-221,1000,-713,392,-490,-921,-568,-511,1000,466,-1000,463,1000,-996,453,-21,-501,651,21,1000,183,-883,-120,1000,1000,1000,-697,-262,-388,-780,-616,-197,-1000,-765,-582,-1000,-496,665,922,703,154,-121,734,444,1000,266,-615,-1000,-735,-198,-1000,-1000,59,-83,-830,-1000,-1000,175,1000,-976,243,-962,-679}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,int):boolean",
            new int[]{-493,11,-1000,-158,722,-809,-1000,1000,-421,1000,-434,-317,567,1000,-1000,-583,232,1000,-1000,1000,-92,227,-583,-818,994,-1000,-1000,83,-438,1000,-765,1000,1,-276,715,-553,285,793,-10,-163,1000,621,1000,-27,878,1000,175,1000,-145,-81,-418,-177,-427,459,206,572,-114,-366,-739,1000,104,-152,-590,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,int):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{1000,-1000,-1000,1000,763,-220,-1000,-1000,458,1000,1000,-1000,-1000,1000,-627,650,388,1000,1000,1000,-1000,-1000,1000,1000,1000,-925,1000,1000,1000,156,-723,1000,1000,-1000,-1000,1000,-1000,-1000,-1000,1000,-1000,-1000,852,990,-78,-761,1000,1000,53,-76,-1000,-1000,1000,-36,1000,1000,1000,-588,969,1000,-1000,486,-400,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{1000,-571,-665,668,1000,-118,-189,-1000,1000,1000,225,-257,-1000,1000,-455,58,-1000,208,-1000,288,-730,-1000,-765,-677,-596,-1000,1000,1000,706,1000,-1000,1000,-112,-1000,434,999,400,-1000,695,-797,-1000,-388,-687,-1000,-1000,1,803,-622,894,-1000,239,350,20,-687,288,-170,728,-884,699,-665,350,692,1000,-373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{601,-400,-1000,-833,-752,-1000,-731,-678,94,853,1000,-727,-1000,60,226,-644,783,-327,-69,-228,1000,-966,30,52,183,-14,-1000,1000,1000,-751,-806,1000,-1000,-817,-131,1000,1000,960,-1000,577,1000,203,-521,379,1000,156,-482,1000,-1000,-617,941,-1000,-1000,-220,479,113,637,-283,452,-372,-507,-1000,-24,790}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{-818,496,404,-379,-947,-259,-13,-433,956,893,-470,669,-450,-97,-812,482,262,791,-595,-517,-730,233,-681,-845,39,-509,-484,750,162,806,761,377,-349,-741,237,810,880,-786,-313,476,-609,967,-599,-574,-506,-61,442,815,-809,-96,713,365,-679,-905,514,-373,22,-229,104,-542,-371,-168,-578,78}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorial(int):long",
            new int[]{-234,-454,100,-725,210,916,-28,-99,86,-152,78,-569,-399,842,349,-895,-29,-708,-607,163,891,5,-920,-506,-726,412,-48,149,-530,239,-238,-977,-701,994,-336,891,969,-691,315,953,-770,-81,-531,-942,965,843,-488,-154,815,-784,-151,209,422,-890,-980,651,393,718,-977,425,192,-731,-579,-374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorial(int):long",
            new int[]{-23,-316,546,612,-478,-572,-357,-782,916,-275,412,-703,-41,-718,-694,-482,-748,-895,-540,323,-816,-735,938,760,-243,411,685,-676,-379,-541,-290,-233,856,503,-824,-213,750,693,868,-595,-878,527,801,912,-435,829,-396,-706,-592,528,-236,-763,151,288,-169,-27,-313,-970,-797,535,-317,-588,298,-717}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorial(int):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialDouble(int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:NTE0NC45MjA0MjYwMjU1OTI=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialLog(int):double",
            new int[]{888,-169,86,927,961,-470,-542,891,-494,147,626,874,-919,-740,-489,989,-520,832,441,472,592,681,-647,668,-58,63,922,867,611,962,949,-943,-490,-314,778,28,991,-349,-319,19,647,-107,158,73,813,721,-67,-339,760,238,-942,-275,862,958,985,311,-870,634,715,191,409,-386,-777,494}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialLog(int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialLog(int):double",
            new int[]{-728,503,-768,-751,-821,730,-891,956,836,753,-843,839,-166,42,-81,-738,-357,-271,-769,-64,832,897,-302,23,957,809,-946,-994,22,83,424,279,-395,-180,-128,831,-194,979,861,80,-972,918,-777,979,-761,-35,61,805,-110,110,-324,-264,908,474,-994,261,-683,80,371,-249,945,-371,362,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:NA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-173,1000,164,899,-150,513,-739,893,177,-1000,1000,-1000,-377,420,1000,1000,-1000,62,1000,1000,-331,1000,1000,-256,361,1000,-224,-1000,-1000,211,-895,1000,1000,-1000,1000,2,1000,588,-373,822,-1000,-1000,-1000,-1000,-1000,1000,-938,-1000,31,-142,1000,306,161,-220,779,-924,-1000,-1000,-347,-130,-1000,-909,781,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-1000,525,-513,1000,-635,78,673,509,-114,-1000,-162,-585,1000,452,-504,1000,101,222,-81,-320,-356,-224,-75,-599,1000,-6,-1000,-220,-1000,900,-1000,-915,167,-233,-262,-601,216,1000,102,1000,-828,-308,-1000,-412,-510,-47,-602,-1000,-475,1000,1000,728,250,-1000,1000,941,-486,-416,-1000,658,140,-349,916,-820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-954,-176,-623,-509,-224,-370,-681,-488,554,92,747,-139,-642,-531,-408,644,5,-8,946,13,-200,-937,-819,-996,-602,-459,710,-369,270,-665,696,-381,315,320,63,-860,-379,-812,-461,637,151,294,-870,419,-783,739,450,80,-972,-990,-417,-987,-742,329,-158,260,677,547,653,818,-842,-126,-912,95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-390,-861,878,946,-756,228,-443,-189,634,178,380,-250,988,-13,-680,49,-169,380,109,-795,789,317,23,-256,805,-475,199,-199,259,-852,-621,189,-355,-642,28,654,880,397,-851,-239,134,-68,801,-581,137,833,-696,-535,-685,-479,-546,-110,847,-112,652,332,721,-530,-192,256,236,-996,-880,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-823,-600,675,1000,944,321,1000,679,338,-598,-898,288,338,-91,531,322,-1000,-446,-1000,-432,769,-1000,1000,-936,-145,625,-79,-230,-658,376,140,-782,999,45,132,-594,1000,-314,180,476,630,-1000,-347,301,-210,240,-675,247,-452,1000,1000,88,-478,583,1000,391,427,875,-1000,1000,-366,433,-305,-97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTg3", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-187,-889,859,438,94,486,-780,-907,-736,99,511,-453,60,417,992,902,487,111,-303,743,706,568,881,607,-433,199,-824,683,-292,-440,806,440,294,-927,227,784,929,757,972,147,-958,-214,121,-550,166,914,-149,-716,-117,867,786,855,-36,635,522,232,-765,-736,605,-737,39,-744,515,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "hash(double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "hash(double[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(byte):byte",
            new int[]{-418,-518,664,-805,-824,990,-906,59,-762,-655,637,-743,-334,763,370,708,-621,746,956,152,-993,-727,823,380,-215,985,380,-668,-474,929,229,-970,656,341,581,631,201,110,149,434,-634,43,-287,-401,851,-97,189,546,-556,-748,-386,-482,-445,-381,-293,-367,378,215,877,-92,-882,-503,426,-797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(double):double",
            new int[]{-650,-446,-590,701,-754,290,-751,825,375,-623,-694,176,603,588,55,145,755,-395,938,162,228,441,829,510,850,-544,121,-201,-415,939,893,676,875,406,931,-239,198,999,158,354,-209,675,406,-999,-631,-782,-580,-323,-862,550,-524,48,-530,-700,45,-983,-846,-870,595,-385,343,-73,-230,187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(double):double",
            new int[]{-294,571,571,-476,613,-852,-631,603,683,347,467,-798,-776,-918,137,-4,-705,-487,-65,-834,484,945,-225,676,-343,357,-84,-748,-773,-733,844,832,-301,-202,247,721,-937,-955,130,-805,-75,150,470,-122,189,845,763,-146,563,-351,-688,197,208,402,174,-640,434,-651,-705,-324,-496,-388,589,-842}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(float):float",
            new int[]{-28,-848,637,-604,769,-857,-909,731,-192,-814,-550,-286,-775,355,-878,-120,570,-377,9,-807,-205,-285,-659,-706,799,-791,-73,-482,-401,72,792,-323,473,641,-363,-982,-334,412,-810,595,-324,966,124,830,-508,844,-70,-976,-295,-743,-90,-786,-81,143,-716,891,-492,264,276,-151,-529,801,-961,-304}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(float):float",
            new int[]{-438,-53,-102,18,-817,272,467,846,-756,801,350,562,-192,421,492,571,162,439,33,549,110,-621,920,782,-427,821,187,922,-683,926,-919,133,-166,135,-284,-12,729,868,-495,339,-694,23,-714,-104,484,-439,757,-748,646,520,582,705,-409,-555,392,376,-646,-595,-231,-142,344,507,-717,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(int):int",
            new int[]{284,-807,-981,-235,737,-328,-273,419,837,-879,448,-651,-532,582,499,-935,-596,-249,416,-525,-384,-731,-311,-585,-667,961,302,14,-259,-660,-868,-805,-176,-410,-380,575,-555,-301,-427,-482,-480,-517,-582,156,-135,798,-222,-162,154,-588,887,-371,294,-255,975,-730,-487,936,548,270,424,569,257,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(long):long",
            new int[]{-395,-128,806,468,-619,-230,827,412,-479,-87,-304,978,-512,829,947,-51,801,543,-51,-966,102,677,-602,622,-970,889,810,873,-920,-803,-686,707,146,-326,426,200,145,-576,-115,-617,-207,-170,-863,-468,-620,621,-396,-374,-778,-512,459,879,357,697,-657,-22,-985,-589,-844,972,-607,304,887,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Short:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(short):short",
            new int[]{-444,-868,580,-111,-39,103,-163,-256,197,-431,858,459,-165,-90,619,-409,538,-844,-830,-980,916,-183,-978,-167,-199,560,-720,378,-614,-498,732,-42,690,396,345,-303,-194,-835,-56,-199,564,-111,273,632,-642,633,53,-196,-843,291,194,864,15,19,-104,-985,733,760,20,-173,782,-135,-220,-112}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTE5MDA=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{-476,995,505,154,-592,476,466,30,760,567,275,-867,-8,957,421,-381,497,617,-196,-346,1000,942,1000,857,-535,-1000,1000,823,-886,740,-519,-65,173,129,673,-594,49,215,-15,795,-85,-362,-1000,417,1000,359,1000,-51,-80,1000,-432,984,291,-478,217,-1000,-82,333,514,55,578,635,-608,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{269,-614,653,-188,853,472,9,-508,1000,1000,-505,-1000,821,1000,1000,-1000,952,-1000,-942,-302,-1000,507,-1000,1000,-281,-974,1000,1000,119,1000,-1000,63,1000,267,-168,-931,890,641,355,1000,968,1000,-1000,1000,247,537,-1000,694,184,603,-950,1000,109,-694,-737,-1000,1000,561,1000,-1000,-1000,371,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{-392,-272,-861,-584,898,793,-692,-741,522,507,42,660,62,499,-346,-525,115,285,-814,-848,981,-563,-371,-339,211,-443,696,500,395,-888,-66,339,846,-246,-598,-188,-64,-375,-582,256,762,-689,929,-219,-437,-379,-36,949,-190,-840,-690,-440,-710,-200,75,-432,641,-443,-895,975,-210,-834,-610,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{-719,1000,507,-911,125,-590,267,1000,1000,1000,687,698,1000,-1000,-250,-1000,340,727,73,1000,851,692,-466,993,1000,-938,512,-506,-919,-673,-653,286,-517,-159,1000,-20,-1000,1000,-743,-428,-803,539,-842,115,445,-376,429,-390,-177,687,126,211,608,-302,-1000,-263,-25,141,-1000,-25,333,385,734,-780}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{116,260,-923,-6,680,-456,-750,568,-5,-490,-768,-772,532,-360,-346,-686,572,174,57,-130,-492,907,-490,-70,195,58,69,-367,-320,978,-665,939,946,-955,-197,-318,397,973,-31,-25,724,-102,-769,-429,539,156,-498,670,682,-568,-858,907,480,-906,-649,-270,-617,-38,-542,283,395,491,-308,-15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{-1000,923,1000,-111,-369,-137,156,303,793,583,332,87,829,-402,1000,-782,-564,174,-1000,-172,-492,1000,-490,1000,58,-1000,521,999,-993,145,-452,347,946,-426,-197,-702,669,835,-201,1000,18,262,-769,-91,1000,26,-498,-258,146,1000,-255,907,888,-460,174,-714,911,1000,151,34,604,259,-725,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "log(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(int,int):int",
            new int[]{610,375,484,-177,-184,75,949,-3,-650,-121,210,159,409,-425,736,-440,442,198,352,603,458,73,-558,802,923,-904,867,-351,-98,-834,50,-23,267,-270,252,-867,-683,312,-2,-198,-91,557,-213,-292,-621,875,-230,-453,923,-943,919,-486,-141,395,-667,563,266,752,832,-853,-164,-159,722,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(int,int):int",
            new int[]{-224,-817,-222,-909,-189,156,977,354,-908,-14,8,221,439,-992,3,-878,-189,-89,235,868,124,483,-71,772,-46,-641,782,634,-848,58,-15,-456,572,-350,-815,-813,-386,-720,688,886,-905,482,438,961,57,-276,-187,-853,-414,-60,-857,-191,434,-59,973,-542,303,559,591,701,-983,117,-914,155}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDc=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{193,-21,-237,290,536,-457,555,-505,126,-879,385,-385,360,-660,897,-264,178,174,314,-933,28,814,317,-128,-837,75,-961,-107,542,-701,-213,12,258,-179,-146,310,-362,236,7,-875,-54,956,-939,-786,-36,825,-687,745,-369,931,-424,437,398,187,-982,-88,988,-284,-884,63,-168,653,623,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-341,773,-839,765,-284,707,464,-649,-464,-946,754,-458,243,-317,239,858,967,147,528,116,663,3,-438,211,-414,719,-8,76,987,668,-582,-383,-777,-197,625,-198,714,782,-822,534,953,558,577,-542,138,750,-475,-558,-131,448,-177,-907,-134,-445,886,-595,666,255,-29,362,-140,714,-210,198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{90,-221,-336,880,682,-461,-608,-22,526,557,1000,250,-185,72,210,-1000,1000,-307,673,-58,-511,-173,-1000,237,-90,765,70,504,418,-620,551,330,-1000,363,925,1000,1000,-140,-654,584,308,-1000,475,-729,4,1000,74,-117,-1000,-301,-1000,1000,522,-371,-910,-8,-172,-957,-1000,-215,572,593,-894,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0OA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-844,722,-280,1000,-176,-758,865,-459,1000,866,-997,679,-289,-1000,1000,64,-45,-1000,528,-184,817,-1000,539,-262,899,-574,173,-85,1000,668,82,-1000,506,120,-861,-198,1000,1000,-110,1000,953,558,-435,151,138,-692,950,8,-249,907,-217,-637,-1000,-938,1000,-336,-1000,78,-1000,425,-121,83,661,-476}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-569,413,-767,289,-255,680,1000,579,1000,638,-1000,-293,292,-1000,-385,1000,744,1000,606,-645,-220,1000,-119,-26,1000,-480,718,598,-922,1000,397,982,44,-193,-948,-1000,333,979,-415,1000,572,1000,-638,168,-501,245,-117,620,-601,-422,592,-1000,378,619,1000,859,212,414,-230,1000,-764,-1000,-34,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-120,1000,659,-579,-793,-291,164,295,-1000,-1000,397,-194,945,-900,687,415,318,335,399,541,243,-680,749,-979,-161,-91,1000,370,337,1000,-390,-622,-838,123,374,-459,264,1000,53,-959,1000,146,111,-128,909,-410,552,-921,131,-1000,770,-949,-1000,-275,-107,-1000,1000,1000,854,57,-607,1000,64,247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-60,860,-78,-225,-591,-510,28,708,-456,422,64,68,-977,-740,-23,-801,-22,854,514,1000,-137,-1000,224,-1000,-138,-375,770,1000,-98,-51,1000,-224,-643,289,-95,-259,1000,-111,-1000,1000,17,-758,1000,178,915,-400,-56,-302,-686,-1000,-155,-347,-243,-871,-236,-635,-258,292,-273,-227,261,463,-1000,491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-761,469,436,751,10,-623,-517,854,520,701,-209,668,484,309,495,698,817,-844,881,-733,-131,-574,998,-428,-632,-39,-702,-319,728,-410,-119,387,69,76,-431,216,483,590,323,-660,900,833,-693,698,225,257,-703,-851,-668,158,56,175,-12,-110,811,343,787,-136,-965,478,-208,634,-663,-637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuMjIzMzcyMDM2ODU0Nzc0OEUxOA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{306,330,318,-986,911,565,503,-336,753,-22,628,-32,721,876,-967,192,-436,-475,-450,-669,-584,-925,-420,793,-521,-141,-143,-587,862,-1,45,-562,-416,114,72,-948,824,-676,-15,432,-335,-272,-967,-690,-238,-954,-346,613,-360,241,128,845,660,557,713,-390,-916,666,326,-135,-877,-618,-10,-630}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{-474,33,-589,-636,-153,-596,-842,272,-482,-688,158,-370,898,-831,-557,583,155,65,496,34,-545,739,37,149,789,-804,480,682,902,880,-378,748,748,-898,910,514,-985,892,512,743,-617,-653,-675,-888,-606,150,-948,811,-923,963,-476,-978,-198,116,-447,609,227,-813,-227,-440,662,885,-828,-427}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4MDAwMDAwNUU5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{206,88,-531,-3,-275,-955,-639,-716,171,43,352,-409,-818,116,623,-900,-27,79,-804,359,-868,209,-533,-847,578,313,-720,655,-415,272,-935,180,-217,-988,362,765,-859,249,915,192,62,349,-550,134,-58,-851,-765,-397,-192,699,549,63,-40,-463,347,226,983,-670,515,809,-119,497,415,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Double:Njc4Ljk5OTk5OTk5OTk5OTk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{679,-217,655,556,346,-358,-564,582,599,780,204,178,-198,-764,-140,-353,971,-41,307,255,-275,16,724,-146,-106,287,-349,-716,474,761,678,409,-370,-563,-642,519,-481,191,-207,-718,-149,979,-700,-586,329,270,528,819,343,681,444,71,652,472,742,783,175,-241,372,-313,-550,-909,136,47}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuOUUtMzI0", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{596,276,-1000,1000,-992,-1000,4,564,-400,414,1000,-727,-1000,-571,705,1000,-1000,770,567,1000,910,1000,761,-1000,316,1000,-263,400,491,-791,-711,1000,-180,-145,-762,849,-286,371,-351,-1000,1000,-535,1000,1000,-1000,-289,-1000,-1000,91,-615,-900,433,-689,-1000,-922,-1000,1000,-300,-1000,611,1000,43,803,252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{-310,967,-691,-469,-536,850,-423,-448,728,42,-716,822,-643,535,-170,-540,926,931,787,-702,671,188,-676,660,-872,-359,-942,-42,622,-811,192,252,867,-579,-447,-211,974,-150,-948,-21,314,377,-32,271,-93,678,474,943,-751,268,868,-27,842,666,-943,814,65,316,60,181,-503,688,-508,742}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeAngle(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("ARRAY:[D:4:45:java.lang.Double:Mi4wMjk0OTAwNDAwMjE5NzgxRTk=:45:java.lang.Double:MS4xNzk5MzYwNjk3ODAyMTk4RTg=:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeArray(double[],double):double[]",
            new int[]{-962,-172,862,-852,-898,213,991,-558,43,-350,-21,338,-937,82,-697,-600,515,827,-219,693,-15,-485,4,849,479,837,35,794,-818,-354,59,613,667,-558,-687,-962,902,-647,444,-335,-22,330,251,-853,67,477,-350,344,822,202,-315,-321,-862,139,391,809,0,749,-575,122,30,-141,-494,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeArray(double[],double):double[]",
            new int[]{479,1000,686,1000,1000,455,1000,-1000,-257,980,-867,-920,-191,1000,-768,204,527,-1000,812,109,-1000,-628,1000,-337,663,-1000,-471,-342,604,-120,1000,806,397,1000,-427,304,540,-1000,1000,311,-229,267,307,1000,-264,218,-1000,45,-822,827,-489,-433,-884,509,-188,367,1000,2,-452,-1000,-112,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeArray(double[],double):double[]",
            new int[]{1000,-33,347,-431,295,178,-67,306,318,1000,396,-564,909,-716,-354,98,-20,179,-154,-1000,486,-770,-939,222,-1000,543,-1000,-6,-383,1000,1000,-961,-778,1000,1000,406,-1000,1000,-1000,-118,-441,-1000,165,-98,737,-879,-476,590,-555,662,666,385,18,483,-89,435,353,152,291,-677,522,585,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeArray(double[],double):double[]",
            new int[]{-237,-843,1000,-1000,-579,814,1000,-669,283,945,412,991,-250,1000,-753,-255,962,164,27,1000,-1000,-1000,-274,516,821,-309,-342,-431,251,-1000,-26,475,-741,264,688,-799,-284,-558,603,242,-704,390,-352,-463,-515,296,-1000,-457,304,-502,-1000,-718,-656,395,698,1000,647,54,-559,210,1000,230,408,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeArray(double[],double):double[]",
            new int[]{-767,832,-1000,-168,-1000,675,289,-962,-732,547,-395,897,-461,-206,1000,640,-601,-1000,580,35,-46,485,555,553,-956,111,-10,-911,56,219,-442,628,338,320,-108,384,428,-488,248,740,523,976,822,152,193,-354,-430,79,-1000,374,-366,1000,-1000,909,-1000,221,-194,-32,-388,-1000,-1000,1000,-1000,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(int,int):int",
            new int[]{608,582,81,815,109,591,-617,259,-156,561,229,511,-365,-125,586,-272,-694,837,70,219,544,427,-513,113,-714,619,891,-48,444,-29,212,-675,589,-10,-685,-291,645,-334,844,865,-408,453,-824,-696,-260,-19,381,635,217,-151,-769,913,-52,918,-55,-472,677,744,-332,-413,318,-18,-559,-689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(int,int):int",
            new int[]{38,798,-401,-254,885,683,-917,308,-226,-703,-376,-238,169,-418,923,268,-39,-740,516,-617,389,790,-947,499,-727,-310,-804,-170,-33,552,-218,-338,-705,892,-247,693,139,833,829,-602,-690,-456,-130,-410,885,-664,-544,596,-733,-618,341,-203,897,-877,106,695,763,606,627,-908,-359,-773,736,-484}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(int,long):int",
            new int[]{-440,-38,622,431,-983,632,-829,-773,-843,493,-587,-380,-772,989,149,-633,-348,841,776,-834,955,581,-139,282,887,-141,806,846,383,823,-712,755,820,-521,679,698,838,113,301,55,882,-458,-992,-863,-409,275,-649,-415,163,500,-646,575,919,211,-175,-29,417,784,-986,515,-314,-182,-564,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(int,long):int",
            new int[]{424,-476,120,-860,-840,-924,-430,1000,-728,-574,-856,-504,-924,126,273,969,-488,-841,-596,999,-41,-762,840,609,-270,-28,-888,671,783,-936,424,311,975,340,281,721,997,-460,989,994,26,543,429,-666,-897,953,220,-685,-777,862,-826,736,-750,-38,847,65,878,196,-134,462,85,-359,741,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(java.math.BigInteger,int):java.math.BigInteger",
            new int[]{-569,-341,935,228,-701,844,-614,763,-342,394,-416,526,-380,464,-199,-216,42,499,615,255,-333,-748,830,410,967,-876,-44,175,449,-987,-797,906,387,-824,42,-980,-528,-212,-176,-45,700,-123,-284,-676,-14,-924,-779,22,436,-753,-32,-171,998,476,-891,-375,294,107,-336,-284,839,592,-539,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.math.BigInteger:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(java.math.BigInteger,int):java.math.BigInteger",
            new int[]{227,-378,-533,-488,382,36,218,-345,116,-174,-87,45,-98,288,371,227,-423,-913,522,392,208,-552,-279,-121,-500,-943,605,228,38,616,461,925,856,354,207,-498,345,-37,-487,967,376,-607,-681,676,-373,760,-913,-283,600,-804,-511,613,-425,888,-113,-240,-173,-189,-2,-654,-151,666,410,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.math.BigInteger:OTgwMDQ1OTMwNTEwOTU2Njg4NjMzODYyMDgyOTUwOTI4NDI0Nzc1MDkxOTIzMjE5NzE5NTAwMTY4MTUyMjcyNzk4MjA4NjQyNjI3MDY4Mjc3NDQxOTk1MjU3Mjc2MTcyMTkwNTM3Njk1MTAyNDYwOTU3MzQ5ODE4OTc1MjQ1MzQ4MDY2NzczMTc3OTA5NzE4ODc4Njg0MDk0NjkxOTExMjUyMjM0ODgyODQ0NTI5MzMyMDU2OTQ4NzU2OTcyNzY1MTY3MTAyODYzMjg3MDM4MzkzNzA5NTk1ODcwMTg0NzEyODk5MTU4NDQ0MjQ0NTM0OTY3OTk1ODg2OTIzNTMzNTgwMDQ1NjYxNDAxNzcyNjk2NjY1MTczNTM3MTcyNDI2NTA1NTMyMjExNjkyMzY4NTEyNTcwNzY1NDQyNjU2MTU0NTAxMzQzMTI5MzQ2MDkyNDkyNDU4Mjg0Mjc1NTk3MzYyMTQyMzQ1NDM4NTQzNTA4NTMyNzQ3NTM2MTI3MTk2ODA2OTc5ODgyNzkyOTE3NzU5MzA1NjU2ODM3MzUwMDMxMTMwMjAwNTk5MzM3NDkxMTkyMjk0NTU0NDUxNDA1MzExMzkxMTY2NzgwNjA0MDMwODkwOTkyOTA3NzY3NjA1MjA0MjYzMzI3MzUyOTY1ODA0NzExMzc0MjY3OTQ2NjUzMzgzNjg4NzkxMDU5MTYzMTgyODUwNzIyNTA1MjQzMTM1NTM4OTI3MDgxODc5ODQyNTIzODg1NDY2NzMxNjY4MDEyNTM5OTI3OTI1NzMyNzU4NDEyNjAxODYxNjE4OTMxNzk5MjE1NzQyMjgwMTAyMTY1NDU4NzIxMzQ1MzUxNTI1MzU5MTgzNjQ1MDc5ODQ2ODA1NzQzNjEzNTc2NTAwMjU3NjQxMjUxMjg2MjU2MTAwOTQ4NDUzNDkxMzc3NDExNDAzMzQ3MTIyNDg5MzU5MzU4Mjg5NjQ0NTgyOTY4MDYwODI1MjcwNzYxNjc1MzcyOTg2OTQzNzUwNDE0MDA1MDYxNzYxNzczMjU5NzcyMzUxNTQ1Njk3MzQyMTY2NzQwNTAwNjI3ODk5NjgzMDk1MzE5OTIyMjE4OTEwMzg4NTc3OTA3MTQ2NDk0NDA1ODYwNzExMjIzODY1MDEyMDgzNDc1MDkwMzg5MzE0NzgwMzg3NzAyMjE2Nzk3MjM4MjgxMTY4MzE1NzUxNTI5MjUwMDIwNTI1NTIxNDYyOTM5MzU3NzExOTU5NjI5OTEwMTMxMjAxODU3MDM1OTg4MTczMTM4Nzc5NzE3MjU0OTY5NjYwMjkxOTI0MjEzNDAwMDkyNjIwNjg5MDAyMTE0OTM3MjcwNTkwMzQ3NjM4NTEzNTY3NDU3NTE0NTc5NDU3NzY4ODc0MzM4NzE1NDI4MzQ4MjI2NTA1NDE2NTc2OTc5Mzg3MjE0MzczMDMwMjM4MTU3ODcwNzY2MjQ5ODAwNzgzODY3MTE0ODYzOTk3MjY2MjkyMzM2Mzg1ODEyOTkxOTExNDU2NDYyNjIwNDM0OTIzNDY1NTg1NjU3OTQ3NTE2NzcwMjEwODU5MTAyNjM1NTc4ODc5Njc0ODY4Mzc3Njk4ODM1OTk0ODk4MDIxMzU4ODI1ODQ4NDQzNjAwOTM2MDgwOTkyNTE5MjYxNjc2NjU2NTk1MjgxNDQwNDUzMTgwMzc3MDk1MzAyMzQzMjg3NjY2ODMwMjE2MDY0ODkxNDkwMjk3NDYwNjE4MDk4NzMxNDY0NDMwMTQzMTQ4Mzk3NTEzNDgxMzQ2ODIxNjAxMTc4MDIxMjI5NDIwNzk5ODcyMzI4NDA5MzM5NzExMTExOTQ2Nzg5MzMyMTY0MTY4MjM1ODE4OTc1NTg5MTg1MzU0MzgzNTYxNjMxMzY3NTg1MTMxOTExMzM3ODQ5OTk0NDEzOTU3Nzg1MjEwNTM0MzIxMDgzNjExODY4Mzk2MjI4MzAwMzAwOTk5NjI2MTA4NjE5MjY2NTMwMTg2MTk4NzU5MDg2OTkyNDg0NzE3ODEwMTQzMTcyMzQ0Mzc0NTc2NjkwNzAxNjM0Nzc3NjY0MDg1MzM3MDIwNDEwMTI5ODgzNTkwMTQwNTMzMTI0NTAxNjI4MjU0OTc3NDg3ODU1MDE3OTkzNDAxNzM2ODQzNzQ2MjYwMzQ3MzgxNTM3OTM5MjA3NDgxMDQ4NTAyMzk2NTk2NDQ3NjA4ODMyMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAw", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(java.math.BigInteger,java.math.BigInteger):java.math.BigInteger",
            new int[]{0,1000,664,-827,987,563,40,749,-359,-440,-222,-1000,-407,207,-512,196,747,-303,-1000,98,-596,27,-258,-797,-463,0,576,0,-314,-1000,499,-85,-170,-186,-973,-374,-573,-737,466,0,-1000,785,1000,-1000,-1000,0,922,0,-535,873,0,-298,-566,0,-1000,204,409,-1000,-596,1000,-1000,1000,-1000,-671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$4", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(java.math.BigInteger,java.math.BigInteger):java.math.BigInteger",
            new int[]{741,-443,23,860,-260,331,-35,44,-1000,-904,1000,-991,-1000,-350,-266,52,436,-1000,853,882,3,-185,-801,-155,45,-642,-349,-522,586,873,-632,-323,1000,64,410,728,542,-725,-371,704,-105,-1000,1000,-50,438,178,-1000,1000,871,-853,428,306,471,-931,-329,-107,-1000,605,298,944,1000,429,725,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "pow(java.math.BigInteger,java.math.BigInteger):java.math.BigInteger",
            new int[]{163,-30,-10,22,-383,227,492,-607,549,966,204,-445,458,-655,196,-965,-217,-370,-225,-393,-250,-809,626,846,-254,169,656,-637,859,965,-321,-180,-849,65,53,410,-319,142,-149,-745,353,855,258,-99,-758,312,456,-82,211,-198,503,543,-647,25,-579,-787,-908,-162,49,886,-403,296,91,110}));
    }
}
