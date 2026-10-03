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
            new int[]{-1000,-994,-546,-863,-246,1000,-1000,-997,1000,793,-1000,1000,-168,-400,358,1000,-126,-1000,363,1000,-1000,-171,-70,250,152,-563,941,1000,-1000,-879,-729,-724,-334,-1000,-1000,273,1000,137,149,558,-437,620,-416,-1000,-685,-990,-105,1000,-923,389,-384,-16,70,-150,1000,908,1000,1000,-798,532,1000,-604,750,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(char):void",
            new int[]{-362,353,269,-627,-573,1000,176,906,1000,1000,281,463,-212,-863,418,-1000,-682,-530,1000,994,331,-1000,-309,-522,707,40,-219,-772,1000,-967,-1000,-914,139,-1000,-37,-1000,1000,-405,440,868,-261,-375,968,-1000,-171,1000,-320,-1000,-612,1000,253,248,-501,-1000,142,-211,158,559,-667,-227,-919,-75,1000,24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(char):void",
            new int[]{-763,-40,-817,-318,452,168,172,-227,236,837,-346,31,-855,-833,169,11,431,-992,713,520,-159,-109,222,181,748,-266,-778,112,765,-520,-81,-69,-807,-102,-626,-304,705,116,619,112,-485,53,571,-653,-38,841,-569,-863,-100,611,-120,-459,581,-476,-162,613,829,-170,-190,599,214,-86,922,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(int):void",
            new int[]{-449,341,784,712,-583,256,-1000,-613,146,-329,1000,304,1000,-364,243,1000,-20,265,563,500,236,-1000,453,-42,-510,929,810,-1000,-586,16,572,-262,-136,234,1000,-768,1000,-813,180,-1000,1000,152,-124,-380,-478,629,-141,1000,-1000,1000,-747,-1000,381,259,544,-752,1000,206,-1000,-1000,1000,-507,-414,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(int):void",
            new int[]{-70,-1000,-586,-563,586,-86,-534,-612,-292,725,-1000,24,1000,1000,292,-1000,595,-722,411,-903,-315,-825,246,59,965,-932,-417,780,356,-978,-408,-385,892,-945,-1000,930,-1000,220,-191,1000,-1000,1000,120,101,-829,-975,81,-1000,238,-1000,-441,337,423,54,-948,423,-1000,362,620,862,-1000,-37,1000,963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(int):void",
            new int[]{-678,771,-471,77,-820,108,-1000,1000,-169,-580,275,1000,-455,1000,-713,-875,1000,613,133,-310,805,-1000,-415,117,-573,1000,226,-777,-77,527,106,-130,1000,2,31,-809,-1000,-1000,456,-820,1000,-139,-1000,-1000,174,676,481,-574,-684,1000,1000,-889,704,989,-1000,-336,1000,-1000,139,-1000,908,-1000,93,206}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Comparable):void",
            new int[]{-1000,699,-166,-258,445,-596,423,-1000,1000,1000,376,56,856,-491,1000,-117,-390,1000,1000,-1000,-924,90,511,-1000,-51,1000,529,1000,-1000,-424,-1000,-1000,401,953,1000,1000,1000,1000,631,426,-575,261,-1000,-1000,-447,919,-945,-1000,-521,217,576,-689,1000,-544,-362,-1000,-858,-1000,-15,-261,1000,-903,1000,869}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Comparable):void",
            new int[]{-1000,1000,104,-528,-751,977,102,-625,733,-232,-392,1000,-101,1000,-897,499,400,1000,91,-937,1000,1000,-46,-222,718,211,-129,197,-493,1000,-305,-1000,-485,-646,-326,1000,493,-429,-452,-36,429,1000,56,-892,565,1000,-909,-540,529,-638,-625,893,507,792,-968,553,-324,-1000,1000,-984,1000,1000,327,-787}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Integer):void",
            new int[]{-1000,-1000,104,-71,-527,-1000,1000,-1000,-1000,446,312,1000,-184,-968,1000,698,-1000,-393,1,-982,-620,955,-1000,1000,-201,-1000,1000,-616,263,-300,526,165,-21,121,411,600,-43,-658,163,336,-1000,763,1000,277,-507,-1000,315,561,-359,-198,-728,-1000,-1000,261,-1000,-1000,554,-380,215,-566,1000,-755,70,-343}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Integer):void",
            new int[]{-988,-667,-761,-49,-100,720,-627,639,-1000,935,-437,-1000,-823,675,-978,-917,1000,115,360,-220,48,1000,978,219,-455,148,376,48,-418,261,-123,-1000,175,472,793,89,482,912,-950,-627,1000,27,-1000,313,435,620,218,1000,1000,1000,7,-1000,-1000,502,308,59,79,-1000,418,307,542,345,1000,-642}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Integer):void",
            new int[]{-622,-397,164,-52,-671,1000,-735,561,20,456,-245,-1000,85,710,-1000,-1000,1000,-586,70,841,354,145,1000,-497,-138,-217,947,-380,252,700,-955,816,680,-218,355,-145,736,1000,-343,217,945,-298,-324,339,1000,833,-526,-11,357,1000,757,-206,837,-430,1000,1000,-907,-393,865,930,-38,466,662,-588}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Object):void",
            new int[]{-1000,-122,-291,941,-356,-1000,-1000,-1000,-1000,1000,189,-831,-1000,1000,-1000,-397,1000,133,-116,711,1000,726,703,759,1000,565,-1000,989,8,-1000,-497,-171,-1000,-1000,1000,65,-291,318,-253,-492,1000,1000,-1000,-1000,-1000,-343,63,-287,-203,-1000,-1000,607,1000,1000,-1000,955,707,1000,1000,901,1000,1000,276,-931}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(java.lang.Object):void",
            new int[]{-1000,355,-836,900,-1000,-248,1000,400,1000,-44,173,354,303,371,-28,-258,-269,-1000,-668,1000,476,1000,1000,-417,515,5,1000,-1000,1000,-107,-315,135,-65,-222,1000,-284,164,1000,-1000,1000,-1000,7,-1000,887,92,-1000,-375,-777,914,96,491,-80,-1000,212,-1000,-1000,56,-534,1000,-1000,893,-234,-489,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(long):void",
            new int[]{-489,-31,374,1000,-1000,-203,-1000,-187,1000,556,-686,810,-1000,591,824,1000,278,-516,-1000,94,796,682,751,-382,-335,33,-1000,-581,651,396,1000,-376,295,-242,268,-886,1000,728,425,-99,-156,-613,285,-1000,42,1000,87,1000,-562,899,1000,156,1000,-338,-536,-34,-1000,621,-202,99,906,-244,-237,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "addValue(long):void",
            new int[]{-105,-164,-787,410,-1000,-860,-736,245,1000,673,-1000,-69,-1000,541,1000,-1000,-732,-664,-576,451,795,846,917,-780,394,-794,-1000,-854,1000,1000,-329,-642,492,802,-139,-1000,982,-497,-423,-1000,691,-415,833,-892,1000,1000,100,333,-915,207,829,1000,567,-39,344,-239,-999,1000,429,406,1000,-696,317,-561}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{106,-519,-931,-860,-420,-233,948,-956,564,-336,818,-683,535,-604,-617,512,-494,582,-511,-322,-461,-353,-484,-312,518,238,933,-244,-117,948,314,863,921,-285,546,-366,601,-588,955,-835,17,351,-458,-741,-15,683,705,-400,-459,-986,393,-511,-280,946,-70,951,-718,713,-513,253,848,586,546,171}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{-399,-1000,309,-591,500,-448,1000,-561,133,-956,849,39,1000,708,505,-1000,1000,-1000,333,-458,167,381,756,-1000,382,-365,572,-864,285,611,-1000,878,-1000,493,1000,-386,1000,-363,1000,-193,-595,731,831,271,988,-530,-511,-953,1000,252,779,-1000,-1000,1000,374,77,-1000,-708,565,-569,537,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "clear():void",
            new int[]{-211,458,719,372,-459,-664,332,-983,429,-781,-1000,344,579,827,252,-889,1000,-1000,-45,-226,-181,1000,1000,-1000,-189,99,952,-276,688,-229,-137,-1000,-108,402,-91,-1000,-18,-66,-218,1000,-509,552,809,713,-55,-238,-1000,-234,716,804,-280,-405,-998,911,905,-1000,-1000,-1000,-482,-31,-1000,477,-248,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{-1000,1000,654,-1000,-1000,-1000,-49,-177,-123,753,-1000,1000,794,-177,-1000,1000,1000,290,-506,-1000,428,602,-10,-850,-823,966,56,389,-20,-63,-1000,678,-681,176,-942,912,-1000,-763,-1000,-199,1000,-1000,-525,1000,-568,358,-19,229,1000,126,1000,12,1000,1000,-353,-1000,-799,762,362,-1000,400,955,-273,-961}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{417,247,-762,1000,-252,-1000,-738,1000,-987,-1000,1000,1000,-175,1000,1000,-1000,-1000,199,-12,1000,-1000,699,-1000,-1000,1000,879,1000,-1000,-1000,-130,125,-970,1000,-601,-1000,-402,591,1000,-1000,-1000,-1000,700,1000,891,-622,-1000,1000,-1000,-503,976,-991,1000,-1000,704,-75,1000,-1000,1000,-880,-1000,-1000,-869,817,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{-1000,1000,-296,279,-1000,-456,1000,-242,-470,-1000,180,825,1000,464,784,614,-446,-1000,262,1000,-1000,512,-828,-915,-784,1000,1000,-175,-1000,-362,-41,-1000,-584,-758,-1000,-1000,667,1000,-1000,-82,-1000,-683,1000,-1000,311,357,1000,-1000,924,-1000,-883,1000,-1000,37,-231,952,277,668,-1000,-1000,-230,1000,-1000,-592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(char):long",
            new int[]{667,116,-511,557,1000,-1000,1000,-553,-1000,1000,-1000,805,626,-1000,1000,-155,-1000,-1000,1000,-1000,1000,413,813,1000,919,-1000,1000,847,408,-131,590,537,-33,273,108,-1000,-372,-16,-1000,-730,77,1000,-1000,561,682,-590,806,581,-1000,-1000,-1000,-1000,1000,-667,-1000,38,177,-397,-1000,838,-836,837,-1000,759}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("java.lang.Long:Mg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(int):long",
            new int[]{1000,-1000,-121,1000,192,-1000,638,-121,-1000,314,-126,1000,1000,-463,326,-319,1000,26,-402,-570,-136,1000,-582,206,112,-101,104,418,-211,127,-903,729,39,-380,-954,-1000,-1000,116,-63,-1000,415,-70,-127,272,282,-574,1000,-1000,-1000,1000,1000,-386,718,865,-682,-809,-265,971,599,-1000,-13,-217,172,-826}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(int):long",
            new int[]{1000,-59,-811,-917,-200,1000,-1000,1000,591,-1000,1000,846,-1000,1000,1000,313,-1000,755,-1000,578,1000,1000,-990,-1000,-443,1000,613,190,1000,1000,125,-1000,1000,-322,1000,-168,-559,-1000,-1000,-1000,833,-1000,-363,-1000,-1000,-1000,232,1000,1000,-1000,1000,236,1000,-1000,-1000,1000,117,-978,-1000,426,822,-815,307,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(int):long",
            new int[]{-718,905,-306,-1000,590,585,-1000,-593,-699,846,-647,197,-153,-251,20,794,713,688,930,-870,317,-907,-177,-352,740,-73,600,-811,-807,-226,349,467,106,-1000,1000,754,1000,1000,-215,-1000,-792,-17,-1000,-262,652,198,-908,71,413,18,-615,-1000,-415,-773,1000,250,1000,399,-900,781,913,-444,718,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{178,-94,-156,175,844,103,-1000,719,1000,970,-655,431,1000,268,1000,-16,704,-699,-1000,-462,-1000,1000,-1000,1000,272,-195,1000,-161,1000,486,-122,1000,200,406,196,874,572,-66,639,-173,199,178,-1000,-803,957,1000,-901,-1000,1000,1000,690,510,-418,1000,-396,-231,-1000,-1000,-428,1000,936,910,-991,-541}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{-298,813,954,969,958,643,443,-334,561,1000,-521,339,-399,-1000,-877,173,-200,1000,-382,877,-839,-1000,-1000,-391,-199,-647,-420,179,260,-1000,592,554,349,-185,575,682,-473,-1000,-80,915,-1000,364,-109,528,812,-170,-320,458,-1000,23,-277,809,952,-586,498,765,521,-1000,-932,-561,21,-235,37,-500}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{-357,579,933,1000,286,-150,-134,1000,-1000,20,-65,440,-142,-1000,-161,312,-82,177,391,731,-55,-835,-769,-131,1000,-794,348,-740,54,-2,313,323,431,165,451,-132,-247,-249,-101,-527,-847,91,-632,551,325,538,103,56,-1000,-638,84,689,575,-1000,946,-583,683,-533,417,-749,680,594,-481,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(java.lang.Object):long",
            new int[]{1000,-187,-846,-450,1000,426,-187,-180,1000,970,106,883,1000,99,334,-3,-1000,-699,-1000,-462,-463,447,-1000,1000,511,-74,972,-890,909,-381,741,1000,405,-304,-1000,1000,341,-613,-347,-1000,105,413,-1000,-304,408,-368,-333,-94,-16,1000,387,823,-109,444,-317,478,-1000,-615,-226,553,996,402,384,-562}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Long:Mg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{-618,-1000,284,-471,166,1000,-9,-1000,1000,-384,23,380,801,416,-131,-439,1000,193,-774,-1000,-744,1000,-632,-180,712,-704,-403,-188,-711,282,289,252,997,-627,1000,458,-1000,484,125,885,378,655,780,1000,-1000,-238,776,-1000,148,1000,-819,892,-87,239,124,795,-377,-873,743,1000,311,1000,-486,369}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{-1000,-151,744,109,1000,1000,741,652,639,-225,-406,1000,500,-948,574,400,-615,400,-943,-801,-321,1000,-1000,-805,-268,161,162,297,-944,190,1000,302,697,-16,308,288,-354,749,581,613,-59,321,128,742,671,-222,-815,-825,-783,105,154,-139,-142,827,-514,1000,609,1000,114,656,-161,-1000,-581,-585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCount(long):long",
            new int[]{-1000,737,929,1000,931,273,99,-1000,-956,-1000,-361,570,407,1000,330,-336,1000,195,-24,-816,-357,-184,65,809,364,-384,696,-391,-554,-461,-304,381,-1000,191,426,-221,-2,-449,1000,159,827,554,676,961,969,-890,406,1000,-79,152,536,-1000,-385,-847,-1000,-267,89,-629,-1000,-541,401,1000,-739,-547}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{-28,22,-726,-475,-1000,-549,221,404,704,1000,175,-116,400,481,-206,888,-893,-164,-791,1000,-325,1000,-760,-482,182,-82,-925,980,857,1000,-483,-1000,270,863,-400,288,400,-231,-1000,451,131,-400,1000,-1000,-463,-1000,-177,1000,-27,307,-684,-1000,-1000,141,-602,1000,-1000,-413,208,391,-919,-1000,776,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{340,-609,-631,143,-598,-1000,1000,-996,-485,1000,-1000,-945,1000,-1000,219,340,-770,742,-556,-234,550,-21,469,240,518,-759,-1000,1000,1000,507,669,468,-665,-61,-815,-914,1000,1000,845,-1000,117,-714,1000,1000,-457,-765,-1000,-635,834,-813,-285,-1000,541,547,193,484,-1000,550,-1000,1000,368,-1000,929,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{2,-936,-217,29,-1000,-674,202,931,1000,1000,-236,-426,1000,-145,334,455,-1000,-1000,-346,1000,-485,610,-411,133,688,206,-1000,957,73,1000,832,-476,-748,1000,-1000,-472,1000,99,-376,-653,-342,-1000,1000,-383,-154,57,-1000,1000,490,596,-1000,-1000,-1000,699,-431,796,-1000,-478,-131,988,-522,-1000,436,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{-400,281,29,237,-178,938,980,-738,-1000,652,-43,-631,-929,-638,978,-118,-75,291,1000,355,388,-71,75,147,607,-400,-438,305,321,-137,685,880,508,647,-1000,-532,1000,1000,521,-971,-1000,-932,261,685,115,-631,-1000,251,494,-1000,924,-702,-67,-368,669,733,-13,632,-851,400,-219,-628,360,157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(char):long",
            new int[]{-1000,-1000,-376,1000,-175,-526,4,-1000,-78,943,491,-1000,657,-617,-120,-105,-362,-862,-580,-83,-43,810,144,-443,1000,-248,-1000,1000,-1000,732,-100,-607,-300,849,-894,-212,751,1000,-842,-806,-828,-210,1000,-61,-641,-342,-733,438,147,977,-1000,-932,955,448,-1000,-119,-1000,241,-1000,886,233,-581,380,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Long:Mw==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{1000,-572,514,-1000,-873,-1000,-41,55,-1000,-81,38,389,-254,-949,-76,1000,812,650,616,1000,868,-312,461,-592,-1000,-83,1000,-125,-851,-732,-275,783,706,149,-250,1000,-174,-72,1000,563,-688,907,212,138,220,462,444,935,-381,333,-766,467,-817,-728,908,-485,-1000,166,795,1000,-1000,-511,843,-96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{1000,-993,509,-1000,-289,-1000,-43,-116,-355,314,177,818,59,-679,-1000,1000,558,-47,763,425,1000,-561,1000,-404,-1000,285,582,-960,-578,-1000,-929,-67,783,63,216,1000,-135,249,1000,639,-1000,-472,378,-961,-13,418,511,569,-519,119,418,-88,-847,-267,50,-691,-421,465,1000,1000,-928,-684,411,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{1000,-1000,-136,559,291,-1000,-577,1000,-445,1000,-463,709,1000,-1000,-1000,-894,1000,-464,-279,1000,-1000,-997,985,-1000,-793,79,276,-718,202,510,803,1000,-77,-114,550,23,1000,1000,1000,-113,-946,917,-898,599,-744,852,1000,1000,-1000,475,1000,550,-1000,-585,787,696,-57,745,1000,644,664,-1000,-199,69}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{918,653,-607,-819,-1000,-418,696,-637,-1000,355,-1000,-248,-163,-881,-627,129,1000,1000,-853,902,-803,1000,-793,-705,471,-1000,936,164,-1000,503,9,765,1000,714,-41,-716,-823,-1000,-638,-48,654,1000,-1000,622,-540,720,928,1000,582,348,100,226,369,-470,-766,717,-273,966,258,-644,1000,-86,-656,522}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(int):long",
            new int[]{-389,-336,174,292,-369,276,-777,650,-195,-920,1000,1000,-400,673,-1000,-322,236,194,843,115,-118,-210,417,135,-981,814,182,-167,-454,-18,1000,1000,551,-1000,733,14,128,-1000,467,1000,328,110,-61,346,754,-816,-1000,-522,-354,189,-1000,473,-415,213,700,313,-665,-710,668,-315,400,-466,-105,-557}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{1000,1000,-36,494,-239,-486,-621,-1000,915,432,1000,903,-726,-1000,542,-101,-1000,-868,-204,87,-576,629,622,-35,658,1000,1000,-256,634,218,-1000,1000,-889,-1000,1000,176,-309,-218,-599,-145,-917,-272,-369,-574,572,-1000,-531,701,-863,852,422,1000,743,321,-910,435,-716,205,739,-1000,403,-86,-322,91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{-564,1,-258,1000,298,-613,50,-32,1000,468,584,-339,-459,747,346,-830,-484,-344,315,1000,-1000,-499,561,220,175,25,-400,892,1000,435,99,-104,1000,-238,-994,-392,135,181,-1000,-279,-189,448,-978,-1000,731,350,-724,749,178,-1000,-630,1000,-1000,-1000,-828,-1000,304,1000,1000,-1000,478,306,861,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{-48,-970,749,-359,-796,576,-594,-805,-594,384,834,-761,-806,518,636,-265,153,273,543,409,-452,-823,288,-186,-279,-167,187,207,151,740,-239,345,506,22,687,83,-847,185,141,-326,-500,-544,-764,-926,380,-941,643,221,168,-164,-273,244,-205,68,566,-112,943,-987,74,-965,770,-247,118,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{-456,-1000,504,413,1000,-1000,98,-1000,-141,808,-1000,-1000,-1000,-476,-440,-818,1000,-1000,-711,1000,-825,-590,1000,1000,-298,1000,606,1000,984,-1000,967,-1000,1000,1000,-806,-402,499,-1000,-552,1000,-437,1000,826,-213,1000,1000,154,-972,-819,511,-65,1000,-721,960,-551,-1000,1000,884,390,-312,1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{128,1000,-771,244,-535,82,-326,-486,870,997,1000,1000,424,820,878,-712,-1000,1000,-437,-981,281,969,210,-889,-161,652,-769,845,-1000,1000,-1000,286,-217,-448,1000,575,1000,-300,-849,-1000,-222,-1000,751,584,731,-636,-302,-400,411,-1000,398,-1000,1000,1000,-1000,1000,-1000,45,163,1000,-505,-496,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(java.lang.Object):long",
            new int[]{19,-266,-61,-216,830,552,-601,-1000,642,179,10,411,174,1000,963,-1000,-1000,1000,241,-632,450,76,-217,-1000,1000,-594,409,1000,-67,1000,-593,-333,-261,444,1000,635,1000,-785,-993,-1000,992,-584,458,889,1000,54,-291,892,759,-742,-442,-1000,1000,-27,-1000,1000,-1000,-264,1000,1000,-438,-914,-200,219}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Long:Mg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{890,-950,279,13,789,1000,835,-802,-1000,-1000,-1000,552,-761,1000,935,-340,-444,-852,-351,334,495,79,-697,717,1000,-184,-502,936,-1000,-1000,1000,354,-304,-1000,-946,-352,-899,48,1000,282,-435,390,-804,-20,-90,-757,436,-168,-1000,1000,-1000,-729,1000,676,817,23,496,-1000,-62,-719,-1000,-217,-1000,-672}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{551,-415,-842,-597,-476,-801,-210,-558,336,-422,-157,747,585,883,87,-666,-97,103,687,91,851,-136,322,600,-901,135,-989,990,-821,-243,-222,327,806,509,-749,-655,465,-266,289,-880,-222,396,829,-233,39,-74,277,149,-809,-825,-20,-856,-457,377,-434,-865,-443,152,301,991,-851,256,-29,-912}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{-38,-487,899,823,-751,1000,62,-1000,-440,-1000,-597,-499,356,-1000,633,1000,1000,85,-392,-1000,-27,81,504,-875,-668,-629,54,381,-1000,741,1000,266,-84,-1000,-661,1000,-1000,1000,588,12,-181,-630,316,1000,1000,-1000,-1000,-528,-881,1000,-1000,269,761,250,-273,1000,1000,-1000,-1000,750,-499,-498,-1000,-460}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{-664,-1000,179,-898,-1000,58,1,987,-482,1000,756,-193,902,-1000,677,-18,700,1000,-1000,-1000,-1000,-145,608,887,-549,-1000,-892,875,1000,1000,463,-554,-1000,-268,329,-73,1000,406,1000,1000,32,-1000,1000,116,-1000,-1000,-509,-654,437,1000,-341,-1000,-868,1000,921,-902,393,1000,566,-1000,-232,72,972,483}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumFreq(long):long",
            new int[]{729,-718,-876,-1000,-1000,1000,298,-622,638,-254,-1000,676,1000,242,-215,-67,-1000,1000,-1000,-1000,589,543,1000,-1000,672,-1000,-630,-1000,-280,1000,-824,-106,-1000,-611,-1000,-243,-1000,-674,1000,1000,-679,-896,95,-1000,-529,1000,295,1000,530,-1000,1000,-1000,1000,1000,1000,-328,-1000,501,468,-1000,-755,1000,418,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{372,-114,-512,-392,576,-1000,819,-303,63,260,658,1000,116,-647,854,680,1000,-1000,-1000,-1000,151,-1000,-682,-1000,-1000,400,-580,430,1000,1000,1000,-127,329,-102,683,1000,-483,-58,668,1000,394,1000,455,-1000,674,-1000,-1000,862,354,1000,-687,-1000,1000,-1000,1000,1000,680,-400,482,-1000,-1000,-530,1000,-552}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{25,971,243,-1000,-36,-1000,-165,-1000,-237,1000,-1000,-1000,-858,86,1000,1000,-49,-1000,-1000,-1000,-758,-1000,-1000,983,-353,473,-148,165,221,933,-717,-1000,-1000,266,1000,-559,738,-451,655,622,1000,1000,168,-176,152,-1000,262,-441,-215,748,1000,-1000,1000,303,1000,1000,427,150,892,-1000,-1000,259,955,-84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{-1000,670,967,1000,-21,-1000,-1000,19,1000,-8,1000,892,1000,42,-1000,-1000,-559,444,339,-19,-41,-485,-197,1000,24,316,1000,-1000,201,757,1000,-520,685,52,-771,-398,-280,-709,780,1000,-57,-418,634,132,-804,171,-1000,-1000,809,-1000,-1000,401,-444,-796,1000,815,771,255,366,150,-1000,-615,1000,164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{1000,957,464,984,796,-29,426,-149,-93,-390,-5,1000,1000,-31,-754,-869,704,761,400,-264,799,624,192,554,-1000,363,133,-270,-1000,-404,671,90,1000,607,-487,1000,616,199,694,795,-1000,196,181,-569,-650,167,-481,-622,417,189,-1000,992,-546,-317,-603,-578,-677,-1000,481,575,202,-941,151,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(char):double",
            new int[]{296,1000,794,122,273,-1000,-78,-540,882,333,-458,-183,-864,-213,85,864,73,410,-1000,-25,-831,221,-1000,-1000,365,1000,-35,-237,351,-400,1000,-203,106,107,1000,1000,537,313,393,-183,290,134,988,-1000,-1000,-561,425,74,288,754,-1000,-1000,-47,-1000,580,374,686,-932,588,-961,-856,451,111,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Double:MC42NjY2NjY2NjY2NjY2NjY2", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{702,1000,-21,-1000,669,845,-1000,-344,-767,-158,1000,531,-497,279,443,-779,1000,-1000,-568,3,307,904,1000,-1000,1000,870,1000,-359,181,-1000,1000,-763,6,705,20,-145,463,806,-1000,54,-491,777,-774,204,1000,-84,1000,-478,447,469,1000,-780,804,946,-131,-264,373,203,-97,830,877,-865,141,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-78,171,-346,-1000,1000,-1000,-141,374,-859,941,782,-1000,-922,-400,-448,393,-827,-921,1000,-1000,1000,287,-655,232,546,-296,28,268,1000,-949,951,-1000,-1000,704,245,1000,-710,-400,-428,-1000,-196,504,-409,1000,194,539,184,130,-482,-243,343,-344,386,280,-629,-347,-1000,-1000,787,-1000,-815,-1000,-1000,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-494,-654,994,-633,-161,-1000,-1000,794,453,209,56,-365,804,-493,-1000,-866,-1000,315,598,-911,401,-289,-146,1000,1000,-603,-21,393,47,707,-806,778,285,-575,-915,-295,315,-314,722,32,847,-1000,710,81,918,826,82,-170,833,1000,699,-1000,-485,216,1000,161,-1000,498,196,-1000,-251,768,-20,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-164,-885,203,-1000,188,228,712,799,-1000,528,634,715,-1000,124,1000,230,14,-1000,1000,-492,637,804,-471,21,387,1000,571,356,863,-1000,637,-98,-225,425,470,498,-47,756,460,-1000,105,264,1000,-149,-4,622,1000,-682,538,168,-724,-66,-968,614,-564,-21,-1000,-400,-303,-874,-666,102,443,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(int):double",
            new int[]{-60,701,-711,-641,45,1000,-42,-561,-853,573,1000,433,-724,1000,-129,-381,-374,-1000,844,-686,-529,867,87,-286,-245,-530,1000,-1000,756,-288,730,-1000,-825,-638,-581,1000,-1000,628,-279,-44,865,545,143,536,1000,1000,1000,-335,-591,-758,-135,-297,1000,199,-1000,-452,-388,-1000,-729,-1000,-421,-1000,-550,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{-374,-1000,-636,594,-3,-655,1000,793,1000,-961,-1000,664,-1000,-1000,422,1000,1000,-837,-459,425,742,-899,-24,777,1000,-928,1000,1000,-1000,1000,1000,-1000,-421,-615,-1000,-1000,-874,763,-145,906,265,705,-569,-1000,-1000,1000,-232,-25,-735,884,-553,-974,-77,-366,-1000,453,1000,1000,-924,1000,-1000,-800,-890,906}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{-1000,-391,-516,789,679,-1000,-95,926,681,-1000,88,1000,394,-1000,1000,-912,1000,-136,1000,1000,1000,262,558,521,-162,-135,1000,-192,-637,1000,428,-1000,1000,177,992,898,-1000,582,972,-380,-1000,-358,1000,-1000,-493,1000,673,-1000,1000,563,832,1000,600,-6,69,-1000,1000,-1000,1000,-504,850,313,-624,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{400,-1000,-251,616,148,400,759,1000,1000,-397,1000,362,1000,225,833,262,-51,599,264,1000,596,-867,-50,186,-1000,539,-320,-164,-823,-326,98,-910,143,-237,22,337,-312,-839,1000,421,-534,-1000,-400,-581,-42,878,375,72,-176,232,-110,1000,930,965,1000,24,-717,-1000,-233,-949,153,1000,-1000,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{-1000,-591,748,1000,158,-1000,661,731,897,-1000,-287,1000,-68,-1000,833,-1000,1000,-767,1000,1000,1000,533,-23,648,-372,-102,1000,-356,-990,1000,1000,-1000,1000,39,859,773,-838,-250,393,-979,-1000,-244,1000,-1000,-1000,878,779,-1000,1000,688,-420,652,1000,647,0,24,1000,-526,1000,339,892,943,-1000,-475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(java.lang.Object):double",
            new int[]{-1000,-674,249,-1000,14,-1000,-886,1000,351,-961,-641,895,1000,-636,682,-339,831,917,1000,-1000,-58,1000,71,-824,-328,1000,300,-378,-428,836,700,-743,1000,1000,1000,866,-383,181,557,-380,-1000,785,1000,163,-1000,-733,1000,-1000,580,985,1000,1000,788,-428,151,-1000,609,-501,1000,-1000,620,-359,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Double:MC42NjY2NjY2NjY2NjY2NjY2", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{1000,968,-371,-356,793,-194,269,400,-199,285,-671,1000,-322,-381,-400,709,-650,-508,-5,-596,-918,30,-684,786,306,63,130,-310,-342,-262,-687,639,-918,199,-468,684,363,-664,-307,-580,-752,-15,-150,-68,489,-46,254,640,136,594,392,849,176,-526,532,-112,799,-184,-114,700,-530,-264,-176,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{1000,-217,-97,1000,465,-1000,1000,333,1000,-1000,-469,1000,445,-1000,-970,805,-633,-217,-75,85,699,244,-457,-768,-71,16,-120,27,503,-327,343,0,-441,1000,-246,-131,1000,66,267,130,-832,-247,837,1000,442,-1000,-294,626,644,1000,-1000,-776,1000,-634,-1000,-647,538,1000,-128,1000,-754,1000,303,-52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{1000,711,-991,-190,-241,269,-935,-262,-163,330,758,-186,-792,-752,-1000,-617,-648,-335,-168,-888,-338,1000,-217,-455,88,919,485,706,-347,-742,991,513,-535,1000,881,178,458,630,1000,855,557,-1000,734,857,-1000,-312,861,367,-820,-537,-242,-1000,-437,-628,46,-144,-201,-103,-246,-741,-1000,1000,-108,834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{-400,-183,-391,165,-411,-861,613,1000,999,-1000,-921,-535,638,22,1000,1000,208,621,-1000,973,372,-563,-403,-1000,145,627,965,-422,237,-1000,-470,657,98,1000,585,-684,-76,1000,868,-1000,-133,-1000,868,1000,921,-1000,-313,1000,640,366,-920,-53,1000,198,-428,-1000,-685,612,-67,940,-708,1000,617,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getCumPct(long):double",
            new int[]{164,1000,-62,-1000,1000,372,-79,909,-941,1000,-1000,1000,-257,1000,1000,1000,-470,739,-280,-118,-1000,-1000,-1000,944,-1000,-1000,1000,-36,-78,1000,-1000,188,-1000,-1000,-1000,818,-1000,-1000,137,-1000,-1000,286,-1000,-903,321,950,1000,-692,283,292,568,991,234,-956,-1000,1000,-478,-674,-1000,-992,247,-1000,-1000,-698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{508,301,-176,-531,-112,-296,-446,97,1000,-672,758,92,-679,658,736,-81,1000,-1000,235,858,57,97,1000,-165,202,685,-529,1000,-3,319,153,-536,127,-681,-218,1000,256,-355,782,-1000,323,-1000,-457,252,320,1000,100,-939,115,-1000,-1000,4,-9,411,-6,971,312,-936,-174,-1000,-231,-776,-294,38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{-631,-942,-506,-194,200,937,892,1000,168,-961,181,67,-175,854,-379,69,-209,45,-289,842,624,1000,1000,401,98,-248,-70,62,147,564,-202,-32,1000,-83,-94,1000,263,-850,87,647,179,-672,195,286,-170,1000,25,-1000,-219,-842,-1000,-375,1000,1000,-1000,-410,40,363,-1000,-535,-52,-615,-195,96}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{-1000,-1000,-496,1000,-1000,-658,1000,-211,322,-1000,-1000,369,1000,501,-1000,-150,-750,96,-1000,-372,537,284,1000,-1000,-932,-581,1000,-1000,386,70,227,1000,214,-637,1000,1000,-149,-1000,-1000,496,-710,-396,656,1000,-751,-147,-1000,284,-1000,-440,-650,138,747,-131,-646,-1000,1000,-593,-935,-157,-225,918,208,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(char):double",
            new int[]{-922,-1000,518,1000,-255,-658,-695,-211,359,-4,-1000,369,-1000,-491,-564,1000,-1000,1000,-972,-372,742,1000,-680,-1000,-1000,-966,-512,-1000,-512,-209,-427,859,93,-819,1000,-1000,-1000,-710,-1000,970,141,-1000,-831,1000,-1000,-1000,-1000,353,-864,503,-268,-1000,-1000,-1000,581,-1000,1000,-1000,1000,1000,581,1000,477,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{-463,-855,214,-1000,1000,1000,-774,839,1000,-388,851,-472,-569,-1000,-1000,1000,671,32,671,-106,1000,-227,357,1000,-741,1000,261,-1000,654,1000,-201,-431,-617,1000,-32,960,-154,-801,-944,809,-516,-1000,-1000,-1000,-72,455,723,460,765,707,-775,239,618,1000,-117,1000,-1000,-219,1000,-1000,979,49,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{1000,1000,603,756,-1000,1000,234,-1000,-915,-1000,1000,246,952,376,624,-1000,311,-795,364,403,-1000,522,-286,-102,1000,-859,-969,149,-1000,-1000,-347,-1000,806,-363,-168,762,-341,690,945,-38,1000,137,656,1000,-526,-482,576,-1000,443,-192,-1000,853,435,1000,475,-1000,495,420,-760,456,-590,767,703,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{477,1000,-686,-510,130,441,-854,544,1000,-38,1000,378,187,-696,514,-1000,948,-980,13,-68,-571,760,-185,-506,1000,705,-310,-608,535,-473,1000,220,-242,493,681,854,842,-51,-531,814,-15,631,144,-498,-1000,994,-333,8,237,-709,172,-510,1000,819,-1000,-389,121,-629,514,-252,-289,-674,-409,-710}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(int):double",
            new int[]{-629,-31,-152,360,443,-1000,-300,-848,-1000,-729,778,468,1000,1000,-30,373,-104,1000,-136,-136,-614,1000,-55,701,1000,-999,-1000,-766,798,-676,-1000,392,1000,-1000,-1000,-1000,610,921,1000,288,-1000,300,-422,-1000,762,1000,35,-1000,-636,-1000,-400,-1000,-1000,-989,-1000,147,889,1000,412,1000,-644,1000,1000,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Double:MC41", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{-366,-1000,-116,1000,-388,14,1000,-1000,304,-1000,-803,373,-1000,-635,-882,-230,1000,-115,859,-661,-822,722,594,18,-380,284,-1000,246,16,-61,1000,1000,1000,-1000,271,-1000,1000,166,832,1000,-311,-985,351,-1000,-363,-92,-157,1000,580,-1000,912,-344,228,1000,196,-1000,1000,-715,-901,-711,130,772,-888,258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{51,123,314,-1000,-1000,-142,178,1000,-275,773,-800,-382,-201,75,1000,-11,708,-547,549,-169,765,-781,278,-1000,363,-1000,-1000,612,-617,399,574,309,-596,-536,663,-753,-1000,826,833,-375,272,-712,-265,-542,285,-212,-124,799,-685,-972,-151,509,99,1000,-334,1000,1000,713,832,-1000,934,-1000,-294,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{184,-1000,-176,880,-969,-1000,1000,16,1000,-1000,-270,-487,-715,-316,-1000,114,708,-386,1000,-581,-567,1000,1000,-1000,-1000,-1000,-180,1000,1000,-1000,895,870,-1000,-423,-769,347,179,1000,847,1000,-1000,-712,-162,-542,-657,-27,-1000,1000,1000,-332,-669,-1000,-258,1000,-334,-1000,1000,-384,-1000,347,945,-531,-294,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(java.lang.Object):double",
            new int[]{586,399,269,-937,-419,-425,1000,224,540,244,-91,165,1000,858,651,-1000,-1000,-594,103,1000,375,-169,778,-63,536,-1000,848,-77,25,-410,-1000,-1000,-1000,1000,-1000,568,-1000,557,147,465,-820,-113,563,1000,-203,275,595,-465,46,354,-1000,-520,-60,-406,58,-113,-103,564,-3,168,-1000,-544,-242,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4zMzMzMzMzMzMzMzMzMzMz", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{56,234,-541,-564,-727,308,-1000,-135,-1000,-716,575,-1000,807,-228,-401,-1000,804,-785,55,-1000,-214,194,-738,754,954,-1000,-1000,-99,-42,-320,964,-1000,640,-824,1000,-247,352,-523,1000,380,1000,888,679,1000,-634,-372,67,-1000,-295,657,-549,429,-499,-16,543,1000,480,-699,446,-980,-551,1000,-908,707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{-482,39,-71,616,398,195,186,548,303,-482,183,344,-43,323,-155,-1000,1000,-1000,369,-487,64,-400,926,-1000,717,1000,1000,-482,-1000,-684,727,59,-176,456,-1000,-1000,112,-1000,-127,-1000,1000,-1000,-1000,-750,-588,-102,-913,-1000,443,51,1000,-1000,-160,1000,1000,-569,1000,-806,809,-434,-110,515,-368,-12}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{1000,-1000,-1,-1000,-1000,-693,470,-1000,-155,486,-403,375,-761,947,168,474,1000,160,-428,377,1000,-904,-22,666,78,-755,-1000,15,-1000,-420,-506,141,402,-1000,-521,94,686,928,1000,379,-126,1000,553,809,-366,-847,-41,52,-477,-529,-364,448,305,-19,119,866,-618,-1000,356,-508,681,-243,701,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getPct(long):double",
            new int[]{-612,-119,-946,110,1000,288,-1000,-751,-516,-671,981,-1000,1000,-879,339,-1000,804,-1000,-362,-1000,1000,219,-1000,-54,1000,-1000,1000,-326,-1000,454,437,-687,903,958,974,-1000,371,-1000,790,563,1000,1000,-695,-508,1000,741,-32,-1000,-599,-329,-774,748,210,1000,508,-1000,1000,-794,687,881,236,1000,-1000,-459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Long:Mw==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getSumFreq():long",
            new int[]{363,66,-731,-275,-431,-315,-271,-1000,-1000,-1,1000,-1000,17,1000,188,1000,951,864,-724,1000,370,-1000,44,1000,-567,-715,-953,-230,398,21,327,-1000,680,56,-400,1000,558,220,433,-1000,-13,371,1000,1000,-63,-1000,-846,725,87,249,-993,1000,792,420,127,-1000,-1000,-829,367,1000,-853,1000,-995,709}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getSumFreq():long",
            new int[]{825,47,209,-584,-86,-11,-610,-546,-1000,743,807,168,665,519,-136,263,338,-740,148,1000,-173,-323,-1000,-190,-370,1000,-1000,-836,188,945,-1000,-1000,601,-349,-523,-138,550,-783,-1000,-1000,-1000,431,588,339,-1000,-475,142,-401,-1000,-976,-1000,365,-1000,-396,924,-551,-220,370,-614,542,38,-483,488,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Long:NA==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "getSumFreq():long",
            new int[]{365,-209,169,206,213,422,-538,-346,-943,836,1000,1000,229,859,-772,580,1000,-260,93,-785,-166,-383,449,-262,-94,-88,94,-304,-454,21,-1000,284,493,-425,-580,488,-308,-816,-545,-1000,-884,583,766,1000,-1000,-464,72,-751,-601,57,-1000,-1000,483,375,389,-521,-305,-119,-144,699,149,420,-555,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKLTEzOQkxCTI1JQkyNSUKMAkyCTUwJQk3NSUKOTIyMzM3MjAzNjg1NDc3NTgwNwkxCTI1JQkxMDAlCg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{682,468,644,-188,-139,935,143,28,-161,-1000,-8,486,-37,85,-1000,-616,113,-902,597,-105,-845,-1000,8,-766,854,-188,-747,-681,209,1000,-10,656,831,-121,-1000,-1000,-130,-960,-63,-737,-49,305,-1000,368,1000,-493,-1000,282,482,1000,-487,977,608,1000,-560,-1000,-634,-29,-1000,-621,401,-509,-388,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKNDI5CTEJMTAwJQkxMDAlCg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{-28,107,584,-784,-316,261,974,895,451,429,167,838,-668,795,762,734,473,499,-80,49,584,343,449,85,847,127,988,-489,727,286,157,-721,865,63,398,737,778,191,-78,965,463,-766,-275,-107,-871,-864,913,581,-684,-731,-261,-793,380,-643,-305,765,-1,-12,-233,258,-284,-878,-492,-766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.String:VmFsdWUgCSBGcmVxLiAJIFBjdC4gCSBDdW0gUGN0LiAKa2V5MQkxCTMzJQkzMyUKb2JqZWN0MAkxCTMzJQk2NyUKb2JqZWN0MQkxCTMzJQkxMDAlCg==", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "toString():java.lang.String",
            new int[]{533,170,109,-612,-1000,55,-556,293,881,-376,271,553,628,-500,434,425,952,-982,-122,419,489,-34,8,-261,-614,1000,466,-469,-758,667,502,-348,-1000,-152,331,462,-316,-1000,970,1000,28,-317,612,-243,-994,-641,-67,1000,-681,-1000,-84,1000,1000,1000,-879,513,-561,-365,-183,-1000,-1000,1000,-727,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{1000,-73,404,-671,-881,1000,-199,-424,1000,402,1000,1000,66,769,-910,-1000,-69,-1000,-1000,681,-1000,1000,-314,-985,648,-932,-1000,-1000,-958,878,913,-995,-619,-229,-214,1000,-582,-1000,73,-1000,148,-1000,1000,-233,773,1000,-664,-74,930,-673,-942,1000,308,-930,984,1000,-1000,-113,1000,-1000,-382,-613,1000,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{606,1000,-372,-185,1000,370,-288,42,73,-1000,124,758,159,39,1000,342,-1000,-1000,-1000,587,1000,-1000,341,-391,131,908,435,985,-1000,-606,185,-1000,1000,133,1000,-1000,-208,1000,932,-1000,-1000,-941,-1000,470,162,1000,1000,-899,-1000,626,-714,224,1000,-41,-348,-1000,-751,-959,132,-485,-25,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeMap$KeyIterator", DEReplay.run(
            "org.apache.commons.math.stat.Frequency", "org.apache.commons.math.stat.Frequency", "valuesIterator():java.util.Iterator",
            new int[]{-1000,-634,-506,675,-918,-745,-473,-57,165,-542,-302,-904,-842,717,-620,-540,-256,412,-738,-90,1000,-822,293,-616,1000,-313,-73,-674,1000,-65,424,141,-1000,-119,638,232,-523,486,-205,679,-1000,-107,420,1000,673,-640,1000,-1000,-257,907,743,227,409,-877,-1000,587,1000,1000,66,1000,-1000,1000,-119,-649}));
    }
}
