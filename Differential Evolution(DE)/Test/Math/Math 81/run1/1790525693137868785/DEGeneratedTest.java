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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getD():org.apache.commons.math.linear.RealMatrix",
            new int[]{-223,-497,-39,-120,-932,648,276,-542,-289,-526,583,-98,485,412,-418,823,-548,898,-866,-44,405,218,-334,-779,611,-698,-234,932,-756,-284,-201,149,-629,795,360,596,-543,654,29,174,940,-378,-990,-327,658,-468,-28,971,630,-707,705,754,-974,132,-976,574,-979,-974,779,-74,700,-55,812,-27}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getD():org.apache.commons.math.linear.RealMatrix",
            new int[]{455,49,1000,672,1000,1000,-568,-397,1000,-233,583,531,-1000,-394,-841,-1000,1000,1000,-573,169,770,393,390,1000,-466,-1000,-1000,-1000,1000,1000,1000,728,-1000,-1000,1000,398,1000,-1000,419,-1000,-1000,-1000,1000,1000,-396,43,-1000,428,-1000,1000,-386,1000,1000,-1000,1000,-603,1000,361,-899,20,559,1000,1000,-629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getD():org.apache.commons.math.linear.RealMatrix",
            new int[]{476,829,315,1000,-1000,451,-1000,1000,-379,-1000,-676,-1000,-147,-1000,-1000,-903,-401,-792,-952,706,207,1000,783,330,-784,-442,-746,31,-432,161,55,1000,907,278,115,72,-943,275,435,-312,70,435,464,-8,-258,1000,392,-607,334,476,676,-505,520,-529,291,-169,1000,455,-1000,1000,-554,1000,979,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Double:LTkuOTAzNTIwNDkzMjE2NDZFMjc=", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getDeterminant():double",
            new int[]{259,-665,303,654,579,-607,1000,398,394,734,-400,-806,-120,1000,-271,972,-810,417,1000,-1000,600,574,-308,-494,-1000,-342,673,-796,-638,-1000,-696,565,858,-210,-945,762,-1000,17,-1000,-1000,-293,-643,111,60,784,440,-1000,1000,804,1000,1000,856,-1000,-80,763,1000,-1000,1000,-756,262,-975,752,-550,-11}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getDeterminant():double",
            new int[]{-1000,-109,1000,-176,-1000,-538,1000,803,-913,1000,-1000,-261,-502,1000,1000,-353,-931,-180,1000,-1000,-121,-497,797,-1000,331,1000,-814,-305,974,1000,-701,-344,561,-1000,-1000,-1000,-1000,128,-1000,-935,1000,740,1000,-1000,442,-1000,1000,414,-848,-837,1000,1000,-375,-1000,-1000,-1000,634,-170,-1000,1000,1000,128,-910,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getDeterminant():double",
            new int[]{177,-85,303,239,-10,-607,1000,558,-939,734,-723,-1000,-392,1000,-301,878,-810,174,1000,-1000,1000,614,20,-494,-466,1000,201,-1000,-298,1000,-379,-471,858,-936,-1000,103,-1000,58,-1000,-342,-293,-296,802,282,10,490,-533,590,-143,845,1000,1000,-777,-80,19,535,-494,764,-1000,581,-433,1000,-945,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getEigenvector(int):org.apache.commons.math.linear.RealVector",
            new int[]{1000,-681,1000,-219,818,-1000,-590,1000,491,1000,-1000,-303,-1000,-708,422,-914,462,-529,-839,270,-1000,495,-1000,50,-1000,126,-318,269,678,759,794,-384,580,88,-1000,-546,-669,-580,914,366,767,-223,613,-636,1000,-222,718,1000,-698,-1000,756,230,-191,-1000,-699,-28,-286,-1000,1000,280,-345,995,-865,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.ArrayRealVector", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getEigenvector(int):org.apache.commons.math.linear.RealVector",
            new int[]{-498,-833,-735,-805,-1000,53,-630,911,483,338,307,-475,-533,-243,-1000,26,258,-196,-965,793,20,-20,318,-1000,193,349,-253,-318,-871,-1000,663,-598,24,-1000,173,20,-455,-819,-20,-181,474,556,-196,150,315,-92,459,1000,220,-837,20,-1000,-818,-85,610,972,-736,-20,381,861,-20,586,-1000,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getEigenvector(int):org.apache.commons.math.linear.RealVector",
            new int[]{965,785,1000,745,-794,-1000,-479,943,1000,1000,-413,117,580,-790,-244,-987,-514,-370,-929,728,-1000,1000,-1000,-862,-743,-1000,8,1000,375,-272,639,-180,-1000,-666,79,-1000,-1000,-152,1000,1000,105,143,521,-1000,1000,31,1000,698,1000,-1000,-1000,-255,-243,-1000,-1000,1000,-821,1000,-1000,-1000,1000,-990,-992,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalue(int):double",
            new int[]{461,97,1000,1000,1000,1000,-897,-120,985,258,-208,-819,-552,400,-932,912,112,772,1000,1000,-1000,821,556,93,1000,1000,813,-658,1000,1000,-1000,-750,-729,-272,97,1000,-1000,-1000,1000,1000,1000,-376,-841,-837,1000,942,-1000,-1000,-1000,-1000,-1000,825,-1000,-1000,-1000,883,809,1000,1000,-553,-334,-652,53,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalue(int):double",
            new int[]{-1000,-475,785,824,1000,118,-1000,-1000,1000,-470,-198,-317,215,1000,357,893,1000,535,1000,67,-468,1000,-174,-1000,1000,668,817,1000,1000,1000,558,1000,-517,-1000,-735,565,-526,-1000,1000,1000,1000,-1000,-1000,144,1000,305,-296,-1000,-832,-1000,-1000,734,-1000,-246,176,-528,760,-606,1000,59,-493,33,-45,-966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalue(int):double",
            new int[]{-1000,-177,-721,-260,157,498,963,-601,-11,279,-154,312,-1000,1000,419,756,969,657,194,-407,-359,558,-189,-731,-27,593,6,734,303,994,20,-88,269,-1000,-606,467,-196,-749,426,1000,1000,939,-410,457,11,-619,177,-820,-773,879,-545,-244,-1000,-13,415,-772,-733,-1000,-49,406,-182,884,314,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalue(int):double",
            new int[]{-309,431,-135,760,1000,804,-897,105,-23,878,-569,1000,-12,1000,-129,0,866,-383,199,720,-581,812,522,794,1000,666,-245,-741,-610,700,-1000,-165,1000,-427,801,1000,-412,-1000,422,374,674,-1000,-1000,-1000,604,681,-479,-274,-1000,61,201,88,-793,-917,-609,550,-350,252,-719,-492,-611,1000,-226,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalues():double[]",
            new int[]{1000,617,681,-86,-286,467,289,-29,580,-808,-1000,-273,949,766,-670,1000,982,-966,-1000,1000,480,660,560,1000,145,-893,-650,-400,1000,-317,-322,-116,-812,-629,-188,241,-286,-127,-321,-1000,-286,663,-443,521,749,-887,-624,1000,149,-448,-318,-483,-1000,-936,876,-281,-62,830,150,514,322,-1000,-286,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("ARRAY:[D:5:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalues():double[]",
            new int[]{902,905,-241,216,1000,613,-1000,-863,-1000,-194,-1000,-616,181,1000,-371,-1000,-589,-1000,-914,-359,1000,-41,-1000,-1000,105,-1000,1000,-1000,-1000,-1000,309,-923,-581,-930,-918,1000,856,-409,-656,1000,1000,575,-167,225,-557,-905,1000,225,942,1000,1000,-63,487,375,580,175,-183,824,1000,1000,-772,-1000,1000,-737}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w:21:java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getImagEigenvalues():double[]",
            new int[]{-375,905,327,-252,-1000,1000,1000,-473,1000,-808,-1000,-597,181,101,-426,1000,1000,-67,-390,1000,-920,-41,1000,713,38,1000,-846,1000,1000,1000,1000,384,232,-224,-281,-185,-1000,-865,105,-1000,-1000,105,-167,225,-263,-603,-471,-949,-432,-854,-1000,-63,-1000,-936,1000,291,-62,753,-1000,1000,1000,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getRealEigenvalue(int):double",
            new int[]{-294,-825,83,601,413,253,-45,-444,555,310,209,1000,329,400,99,-322,582,1000,-1000,910,617,-134,-548,0,923,1000,573,-31,279,-400,-510,600,423,600,555,-169,364,-28,-338,507,-442,-1000,400,106,-22,-227,83,-771,129,-559,-799,-142,1000,67,-1000,-42,-277,389,-503,-67,220,402,349,142}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArrayIndexOutOfBoundsException", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getRealEigenvalue(int):double",
            new int[]{-632,-337,-69,160,1000,430,-1000,-190,-264,170,-246,-180,-120,890,-173,-990,1000,-1000,923,64,664,1000,-439,44,911,761,-200,-14,-381,-925,1000,973,918,453,-106,344,-181,-163,-1000,1000,-1000,665,1000,517,-593,-498,-388,-1000,430,-1000,-1000,1000,-152,1000,-207,-345,-368,798,-1000,1000,598,-220,320,-55}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getRealEigenvalue(int):double",
            new int[]{820,751,1000,936,665,239,515,-1000,-1000,510,644,-681,20,-1000,1000,32,278,1000,-753,77,450,-400,979,-1000,-185,-883,489,559,1000,1000,251,1000,-1000,929,1000,-167,464,-1000,-738,-334,58,-1000,-1000,-534,288,1000,969,500,-994,-1000,-471,-1000,-741,-78,876,-138,1000,668,678,-1000,114,1000,-221,-887}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("ARRAY:[D:5:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:21:java.lang.Double:TmFO:37:java.lang.Double:LTIuMTQ3NDgzNjQ4RTk=:29:java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getRealEigenvalues():double[]",
            new int[]{1000,603,509,811,741,-1000,448,-1000,892,-554,1000,-510,228,-644,1000,1000,160,-876,-1000,-1000,1000,448,855,-417,1000,1000,-325,613,-497,894,-1000,-1000,1000,245,-692,-236,456,601,1000,1000,-22,533,-1000,-122,-255,-1000,-1000,-235,-324,265,1000,-1000,-144,-1000,312,-1000,20,-547,-631,1000,1000,-431,1000,309}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("ARRAY:[D:3:21:java.lang.Double:TmFO:29:java.lang.Double:SW5maW5pdHk=:45:java.lang.Double:OS4yMjMzNzIwMzY4NTQ3NzZFMTg=", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getRealEigenvalues():double[]",
            new int[]{-951,637,465,1000,-367,-1000,194,-609,1000,-28,23,-527,-1000,-808,1000,-515,594,-392,1000,1000,516,-623,-1000,-888,-1000,-72,1000,-180,1000,-494,712,-692,1000,249,-998,297,559,-1000,24,51,416,801,177,1000,-138,860,-1000,642,-725,652,679,-308,-1000,-133,915,601,247,-1000,921,487,440,-653,-327,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.EigenDecompositionImpl$Solver", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getSolver():org.apache.commons.math.linear.DecompositionSolver",
            new int[]{-1000,733,1000,-91,681,285,-1000,1,276,213,1000,-879,-907,637,922,-1000,-799,-430,-31,891,313,80,-1000,1000,-168,-365,434,1000,-8,1000,-39,-34,625,-29,1000,247,1000,-1000,1000,194,265,635,-1000,-209,-1000,-607,-316,-414,-93,-577,-939,-1000,71,1000,-530,-1000,-1000,1000,-582,-1000,-489,385,216,75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.EigenDecompositionImpl$Solver", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getSolver():org.apache.commons.math.linear.DecompositionSolver",
            new int[]{240,121,453,59,-308,-536,-902,196,-338,-292,-886,-204,-272,281,-256,-375,524,-310,-675,-977,827,616,-561,-82,474,1000,462,80,698,1000,92,-606,216,-346,-55,-119,20,-54,-113,-924,-76,-46,-468,-631,810,260,307,-400,17,164,-1000,-11,290,-694,-356,481,-126,20,-31,460,689,588,-115,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.EigenDecompositionImpl$Solver", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getSolver():org.apache.commons.math.linear.DecompositionSolver",
            new int[]{-88,861,-1000,-399,-1000,-983,1000,-173,-1000,-880,-1000,-80,-891,16,150,70,483,896,-450,-207,436,-637,797,-1000,205,810,963,-297,-702,388,672,-1000,126,-805,1000,-1000,-400,-123,960,-176,-1000,878,451,-753,1000,-528,-463,-175,1000,282,1000,134,618,170,-16,-233,826,37,-433,-285,-385,-705,550,-69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getV():org.apache.commons.math.linear.RealMatrix",
            new int[]{1000,925,749,-793,680,-1000,1000,1000,37,-574,-1000,1000,1000,-380,-1000,-993,398,-1000,-645,-653,303,-143,-1000,277,416,1000,179,1000,1000,1000,-1000,107,-70,640,-1000,755,-149,1000,-262,1000,876,1000,-380,729,-1000,540,-111,-60,269,772,-796,563,-1000,-195,-1000,-275,54,436,-1000,-289,1000,686,829,237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getV():org.apache.commons.math.linear.RealMatrix",
            new int[]{561,-251,-393,-631,1000,-766,-768,99,503,-1000,-178,694,1000,-937,-1000,400,-777,-1000,1000,335,-371,-801,398,3,1000,949,333,1000,479,-83,-962,810,-1000,308,-1000,252,-584,278,84,-816,-2,1000,-361,-878,-1000,-879,-377,-962,21,-221,42,919,-361,-888,-572,431,400,1000,-927,271,139,-1000,245,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getV():org.apache.commons.math.linear.RealMatrix",
            new int[]{-1000,-729,-1000,1000,-65,-1000,-1000,523,20,-1000,-338,-1000,-504,657,1000,436,473,701,1000,1000,-1000,-1000,1000,245,1000,316,-1000,-1000,887,1000,-1000,30,1000,-1000,877,-413,-51,776,-177,1000,122,1000,-393,-1000,-160,-1000,338,1000,1000,-376,-952,-1000,1000,195,44,871,-496,865,-609,1000,288,228,621,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getVT():org.apache.commons.math.linear.RealMatrix",
            new int[]{1000,75,1000,376,1000,290,-204,1000,-1000,492,1000,-711,-1000,620,181,667,-1000,-320,97,-1000,-1000,-507,1000,546,1000,1000,76,45,79,820,987,1000,145,1000,-1000,734,868,613,645,-619,-437,-1000,-376,-5,-1000,481,384,323,697,249,-1000,-927,-46,-1000,1000,-1000,-1000,-187,772,-1000,1000,-1000,-436,752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getVT():org.apache.commons.math.linear.RealMatrix",
            new int[]{-1000,-891,-1000,1000,867,-1000,1000,325,944,1000,-1000,1000,-1000,942,706,-417,1000,-1000,351,537,1000,-442,490,552,992,576,-59,527,400,-250,-1000,48,293,400,-286,-1000,-151,219,1000,925,675,-87,1000,-1000,-294,1000,-18,1000,1000,-941,-1000,-1000,-698,-1000,-938,-786,-433,-1000,400,-838,-1000,-800,31,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.linear.Array2DRowRealMatrix", DEReplay.run(
            "org.apache.commons.math.linear.EigenDecompositionImpl", "org.apache.commons.math.linear.EigenDecompositionImpl", "getVT():org.apache.commons.math.linear.RealMatrix",
            new int[]{1000,-409,1000,-1000,1000,-121,-204,1000,-1000,712,-515,-711,-1000,-1000,77,667,1000,-669,1000,-1000,-1000,-625,1000,582,1000,342,-1000,552,861,-174,987,1000,613,1000,-1000,529,1000,1000,275,-1000,-159,-1000,30,-55,-1000,1000,364,-1000,793,-117,-1000,285,-1000,-665,1000,-1000,-1000,-1000,583,-1000,1000,-1000,-375,1000}));
    }
}
