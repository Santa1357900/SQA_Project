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
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "abs():org.apache.commons.math.fraction.Fraction",
            new int[]{1000,798,1000,803,-749,-205,3,126,-603,1000,703,369,-261,-182,-752,-470,-1000,-338,1000,279,-122,620,-301,356,-376,96,107,-409,217,18,437,1000,-423,1000,1000,-901,-394,919,1000,781,-1000,1000,1000,1000,-1000,-1000,-430,1000,982,-1000,-436,-1000,-1000,-427,322,-689,-1000,-1000,294,-292,-247,1000,15,916}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "abs():org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,1000,1000,1000,-836,-304,-889,-847,-535,612,852,694,1000,-14,888,-985,-747,-218,827,626,-296,1000,45,-151,130,1000,-203,-211,307,27,-702,640,-1000,701,1000,-922,-1000,-1000,1000,665,-1000,-951,410,1000,-512,-1000,-1000,1000,716,-1000,-219,-1000,-1000,-100,-97,-468,-415,-345,771,-600,24,1000,-442,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "abs():org.apache.commons.math.fraction.Fraction",
            new int[]{182,72,776,-246,-383,-558,940,701,115,1000,494,1000,207,-558,-164,-845,-1000,-1000,1000,63,-998,333,994,270,298,-233,1000,835,100,494,519,354,-445,368,701,-476,749,1000,1000,-251,373,1000,66,400,-739,-23,198,513,956,-676,-1000,-785,-767,1,-142,-183,-1000,-1000,-110,609,275,400,-402,870}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "abs():org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,400,-287,-326,-698,-721,769,751,382,894,-5,942,-1000,915,-506,-456,-1000,196,364,457,-720,-1000,726,519,-397,-1000,284,130,1000,1000,-83,391,-257,-525,-1000,268,1000,-787,-112,224,267,-540,-820,-976,-587,339,1000,416,329,-727,-1000,-754,-1000,41,-162,415,-579,-360,-167,716,158,-520,1000,948}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "abs():org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,-910,-1000,-220,172,956,-98,-239,-28,85,599,-618,381,426,-615,307,361,1000,412,439,540,1000,-448,-1000,-907,-951,-143,-313,-1000,-778,-757,676,1000,-672,-135,-187,-122,996,-1000,631,353,767,265,-1000,1000,709,593,-230,-757,1000,-134,95,99,873,-708,800,1000,-647,885,-189,369,-928,-697,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "abs():org.apache.commons.math.fraction.Fraction",
            new int[]{-457,-477,329,-757,-449,-1000,968,628,-32,1000,10,332,1000,1000,-752,-470,-1000,-511,1000,337,-959,-1000,298,515,-397,-1000,621,597,217,608,-781,1000,-25,-712,12,62,-535,100,1000,72,283,1000,-1000,-1000,72,222,719,-25,418,-999,1000,-1000,-1000,196,-446,324,-1000,-666,-243,544,1000,-673,-792,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "add(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-433,452,-63,334,-166,-100,-884,-192,-398,1000,140,862,-102,731,-161,246,-23,-1000,101,492,178,362,-840,-766,-741,-449,895,163,-167,126,237,471,880,318,-525,706,101,-156,-689,-874,-603,61,-59,-400,850,143,-461,-758,503,709,-48,-997,300,-364,17,15,389,-509,388,-746,837,-301,470,289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "add(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-808,-1000,-54,126,-291,-1000,-302,1000,-1000,69,-524,-655,1000,-228,126,-299,371,-1000,-1000,191,-1000,-479,442,-1000,-94,-1000,1000,-962,-1000,567,-652,-449,-1000,824,-293,814,-502,1000,945,-1000,951,-146,1000,137,162,1000,888,-141,970,843,64,568,-81,432,-126,632,842,1000,-74,-424,-181,35,546,373}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "add(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-193,390,443,-1000,997,884,508,-554,535,-276,255,465,161,180,-198,-156,229,726,1000,-756,969,362,473,764,-579,-1000,1000,-310,693,230,980,-176,1000,-849,483,7,104,-1000,-1000,1000,1000,-243,-704,-1000,301,-1000,1000,526,-1000,-170,970,-935,-195,-1000,-326,-245,20,741,356,-609,-550,866,-198,846}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "add(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-851,226,879,-981,836,-202,-446,420,44,745,93,-31,738,-1000,1000,-550,503,444,-186,-57,-839,194,-1000,17,265,209,864,-782,280,117,-99,50,67,-1000,500,1000,-701,450,29,329,359,-1000,67,-635,-340,-279,1000,838,-532,226,991,548,-1000,438,-237,908,1000,-1000,1000,324,-359,927,-137,85}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "compareTo(org.apache.commons.math.fraction.Fraction):int",
            new int[]{217,-296,-419,-21,714,863,-197,-899,-543,-584,-603,-220,-868,482,-421,679,530,877,382,798,-398,529,793,850,329,215,-565,-915,956,-196,-653,25,493,-785,-254,771,133,-848,227,-446,242,355,846,-962,-79,572,727,482,789,-246,425,361,-396,88,-892,261,-395,371,862,-19,50,-6,841,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "compareTo(org.apache.commons.math.fraction.Fraction):int",
            new int[]{507,-39,-469,286,1000,119,468,-1000,663,-564,-1000,1000,-430,1000,-1000,979,610,-176,956,1000,-433,933,1000,697,681,500,612,-1000,478,-518,-1000,14,-813,-388,89,683,-346,-648,-614,-1000,1000,512,862,-1000,402,980,421,-53,-386,-1000,1000,1000,-454,-1000,-400,252,-1000,-240,440,-410,289,-269,-318,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "compareTo(org.apache.commons.math.fraction.Fraction):int",
            new int[]{-592,697,830,-629,-726,-380,93,-197,1000,1000,-901,1000,770,-452,-418,-457,1000,-396,1000,534,-256,673,-691,-838,-10,-1000,1000,-566,125,-557,729,-1000,-1000,1000,184,-296,-208,66,-598,1000,355,-400,-479,634,552,1000,-673,606,-1000,-734,1000,-1000,-597,-85,-26,-1000,-358,-995,-2,458,812,396,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "compareTo(org.apache.commons.math.fraction.Fraction):int",
            new int[]{255,50,-1000,-1000,29,193,384,-1000,-638,-1000,-103,775,-1000,336,-134,265,1000,540,1000,356,-831,142,412,1000,-84,1000,-839,-928,1000,-981,-781,-438,153,-1000,-75,32,-298,-1000,-580,-844,767,-283,1000,-1000,677,794,1000,138,1000,-920,1000,390,292,353,-1000,558,-100,26,59,-577,804,-521,-100,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "compareTo(org.apache.commons.math.fraction.Fraction):int",
            new int[]{1000,-163,66,202,1000,606,-671,653,206,393,1000,214,-1000,698,-613,1000,521,-1000,1000,501,-668,137,-1000,-454,933,-1000,-1000,-366,1000,-458,-102,617,-1000,214,779,735,534,791,293,-1000,-853,1000,-329,42,658,875,639,197,327,1000,351,-1000,182,420,-1000,-1000,324,664,878,-113,-1000,89,1000,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "compareTo(org.apache.commons.math.fraction.Fraction):int",
            new int[]{-595,1000,1000,1000,839,863,-1000,1000,-1000,-187,161,-1000,-338,65,127,-675,-1000,611,-522,1000,-1000,923,-1000,-50,878,1000,-1000,673,956,1000,186,-289,1000,-1000,-502,771,1000,-1000,227,-489,-334,355,846,1000,-562,779,727,1000,-367,324,-811,235,915,1000,-506,205,1000,1000,-653,463,175,1000,1000,201}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-592,-202,719,-122,312,-688,288,172,-804,858,-224,-291,-595,-497,-557,-1000,-1000,-720,-656,822,522,1000,-99,386,359,-792,219,259,534,972,-1000,-120,387,-1000,-1000,-121,559,1000,-272,26,225,370,154,-400,-195,-372,-12,-255,-400,395,-1000,-355,-779,-781,1000,580,181,-89,-887,-412,165,1000,-400,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{1000,-1000,1000,-672,524,18,296,-400,561,256,478,1000,-363,-574,113,1000,-1000,-591,-150,503,-291,1000,-1000,970,957,-387,1000,1000,-713,-665,-1000,1000,854,209,601,-237,969,1000,-119,1000,-483,-470,362,-607,1000,1000,512,-125,2,-44,-848,-1000,433,400,-1000,-329,222,-336,-410,928,971,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-466,-270,-156,541,-439,-322,289,735,-420,1000,483,-875,-691,-619,-37,-955,848,-11,-539,-1000,865,-259,524,429,-398,-1000,-770,-289,328,1000,547,-497,-250,-620,825,944,1000,1000,1000,965,919,938,-13,-498,-385,13,1000,-1000,-418,570,850,890,5,-796,-755,-986,-249,-383,-1000,-898,442,-220,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,908,691,286,-372,-483,125,272,-706,402,134,-1000,-139,1000,951,-137,-220,-444,552,-426,202,-1000,-891,48,-1000,716,-856,692,153,-326,549,-642,1000,700,-846,709,-492,-230,-49,528,510,552,-107,721,378,-299,329,-63,478,-225,-830,-691,844,-982,963,218,-431,-1000,468,444,-132,690,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{815,-1000,261,-305,-23,18,362,-145,561,256,478,442,628,-887,-930,466,25,-679,-189,975,-291,1000,-798,-889,-66,-920,-711,169,-621,1000,-1000,889,78,1000,890,638,-425,1000,-879,610,-357,-688,362,-19,447,461,512,-966,109,-943,757,12,-313,-13,-974,4,665,1000,493,702,971,817,-1000,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,397,-216,-147,-2,-660,28,1000,528,1000,610,-1000,187,179,140,329,-250,76,349,-304,222,-1000,1000,-1000,-495,71,-1000,75,905,166,-135,345,683,-765,-1000,767,-135,1000,-719,1000,1000,589,-1000,84,-12,-259,-12,45,-431,-664,-555,20,-40,-1000,1000,-441,-900,543,158,-1000,111,-530,1000,287}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{35,-30,844,-22,2,-496,426,1000,-930,643,1000,786,-1000,-938,-45,1000,-445,1000,-279,-392,180,674,287,1000,668,-693,544,1000,-433,-325,-1000,1000,1000,-15,-388,1000,1000,-1000,-1000,1000,803,642,-1000,-727,731,937,1000,-55,-956,123,-771,-1000,-31,-1000,28,-1000,-1000,67,-1000,-435,-557,1000,-745,-576}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "divide(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{947,-1000,1000,691,-811,1000,422,-869,555,-42,294,90,60,-1000,4,-1000,710,-915,-1000,1000,-441,1000,-1000,221,-564,-1000,-495,237,-1000,1000,-536,-250,-527,1000,1000,664,834,1000,1000,48,-942,-835,1000,25,-157,306,1000,-1000,452,-203,1000,320,-453,487,-1000,-45,1000,722,215,1000,-539,1000,-1000,-333}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "doubleValue():double",
            new int[]{1000,-1000,704,499,44,-880,1000,358,-962,-522,-425,-632,-282,822,-735,275,646,130,-933,1000,-521,742,1000,972,50,27,384,1000,1000,595,1000,1000,-1000,-1000,-1000,-1000,1000,768,185,-1000,1000,718,988,614,-1000,-730,-985,-579,-998,-812,-558,740,-1000,1000,-244,-642,224,106,500,236,187,486,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "doubleValue():double",
            new int[]{55,-741,-260,864,-486,-136,1000,1000,233,962,-331,-1000,1000,1000,1000,568,1000,296,-400,246,741,-219,58,-915,431,948,-125,416,-50,395,1000,86,-294,-129,845,-549,1000,-61,-997,806,1000,-709,400,497,-81,255,1000,-562,-147,620,-67,509,-1000,-391,806,-1000,757,889,1000,-254,-472,-1000,-26,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "doubleValue():double",
            new int[]{821,-1000,80,890,-562,-158,974,551,643,1000,807,157,785,428,279,761,172,801,-970,919,-221,-1000,1000,-551,1000,-496,-257,-1000,200,-403,-312,-1000,-149,-1000,-228,-345,-365,135,287,185,621,-96,-1000,1000,217,-272,-940,-1000,1000,626,501,709,326,605,-680,-674,739,-101,1000,349,-840,-293,-1000,111}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:ODEuOQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "doubleValue():double",
            new int[]{734,-1000,819,-98,-267,-916,850,-373,643,-836,-181,-237,1000,1000,-400,-328,984,36,-902,1000,-1000,-1000,1000,-808,258,-390,-870,-507,1000,-307,-120,-459,-754,-1000,-617,-673,1000,982,196,-1000,581,-617,-1000,1000,-1000,-1000,-947,-1000,913,1000,1000,624,260,914,-670,-854,739,580,-106,650,-615,616,-54,-353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.Double:NDMuMQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "doubleValue():double",
            new int[]{574,-1000,431,322,779,903,1000,1000,-718,-1000,258,-1000,879,860,-93,194,810,-324,-1000,1000,430,-1000,1000,783,-1000,888,250,-1000,1000,-377,-28,-1000,-13,-1000,-723,746,1000,-1000,-529,1000,1000,-390,476,899,-690,38,1000,1000,-92,458,-414,1000,833,-625,533,-560,-1000,95,937,476,-1000,-1000,-994,67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "doubleValue():double",
            new int[]{1000,-1000,1000,890,358,160,1000,-138,643,440,807,577,1000,438,-156,-928,462,456,-902,1000,-80,-1000,1000,569,1000,-496,-297,-507,1000,-173,-249,-1000,441,-1000,-1000,-345,414,135,545,4,1000,69,-243,1000,217,-272,-1000,-1000,642,380,119,1000,260,946,-900,-546,739,-101,840,106,-615,-293,-653,340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{750,-780,483,307,-796,-372,742,-1000,-115,957,-250,687,195,-400,621,635,316,101,-1000,-887,586,831,-817,-701,-865,-748,-542,-271,-1000,-1000,-300,-1000,70,-637,20,-1000,709,586,-641,-713,881,167,81,-1000,1000,-227,664,470,-243,-544,-795,-1000,343,-1000,-80,-1000,1000,504,-1000,-1000,1000,-669,-219,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{1000,-918,-568,223,1000,625,554,400,-489,-202,-974,691,-150,352,859,-1000,881,1000,400,354,1000,-911,-195,569,-401,-628,-378,1000,268,1000,-1000,49,-1000,1000,1000,183,589,-483,-1000,613,527,-346,400,-1000,204,407,-1000,-1000,525,1000,812,1000,1000,-702,83,1000,81,-126,-810,794,-1000,280,-691,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{489,-780,483,75,-397,-285,930,926,-115,1000,-320,687,312,1000,621,635,358,-21,-1000,529,722,831,-756,-701,-1000,-488,-510,-271,-542,-943,-356,1000,70,-1000,-1000,-1000,781,806,-721,-722,1000,-819,81,1000,-27,-68,329,1000,-795,-441,-671,-1000,343,-880,-156,-1000,1000,344,-1000,-1000,1000,-669,-602,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-633,-780,1000,-530,-371,743,181,179,-735,993,921,-281,231,1000,194,1000,453,-1000,-560,250,994,1000,-295,299,-1000,-706,-187,773,-51,-983,25,1000,527,-196,-626,-351,944,1000,-1000,-518,1000,-1000,-54,734,894,577,-69,986,-1000,852,-49,-20,487,-964,372,-1000,1000,-150,-396,-1000,1000,-544,-779,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-225,-94,965,453,824,171,-587,-1000,-1000,977,497,-418,122,-342,-75,-528,-1000,520,-1000,216,1000,-524,-2,1000,807,398,697,509,-1000,1000,569,-723,-911,-154,-696,-849,-392,135,-1000,-922,560,-432,-1000,145,1000,1000,-1000,46,-1000,1000,1000,400,1000,1000,-278,1000,793,-1000,708,-191,-1000,-73,-855,364}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{175,-918,-1000,-1000,1000,-304,554,890,-1000,-38,-974,1000,162,1000,441,-1000,-940,646,-676,354,1000,1000,-413,155,-401,-628,-378,-1000,1000,-751,-1000,-867,1000,-100,1000,107,1000,52,885,988,-406,-1000,-774,-886,-529,-230,498,-243,-697,-853,261,1000,321,-702,-535,-47,514,772,368,-1000,494,-1000,453,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{295,-46,483,-794,-834,-669,884,693,761,1000,-338,561,1000,551,410,1000,1000,-122,-672,929,722,285,-731,-1000,-1000,-390,-359,-271,-542,-264,-50,907,-1000,-1000,-1000,-1000,779,87,-1000,-1000,676,-759,533,1000,182,-360,597,1000,-625,-544,-1000,-1000,-130,-1000,-40,-1000,607,1000,-804,-290,1000,-615,-360,-792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "equals(java.lang.Object):boolean",
            new int[]{-207,628,1000,1000,-577,573,-94,-923,532,347,1000,763,-267,-52,316,-253,1000,-1000,173,-705,-19,1000,555,1000,1000,398,1000,1000,-711,1000,1000,-688,-1000,888,-696,-224,-319,31,-1000,-922,1000,-593,242,65,1000,1000,-1000,46,608,1000,1000,1000,1000,-1000,-213,1000,239,-1000,290,1000,528,-184,-1000,335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Float:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{339,1000,63,451,-271,586,-861,406,453,376,582,80,63,1000,560,-417,495,1000,1000,-708,-206,976,71,803,968,50,386,-493,-3,-408,-400,-697,-1000,-296,-155,-359,306,-568,-396,-535,400,819,-236,-536,1000,-997,-985,220,591,-75,806,-1000,257,722,544,285,175,-181,404,294,-205,-286,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{-910,669,829,787,-255,-806,739,365,210,191,39,-509,-86,300,705,901,501,84,699,-568,-285,783,476,-316,-280,808,496,-152,358,488,-800,-677,-176,-21,301,-246,586,-574,-31,13,959,504,514,-722,681,254,-508,869,101,-206,-163,772,-869,-93,-447,904,-21,795,798,61,875,333,-217,-740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Float:MzguMQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{394,1000,381,454,203,-1000,494,-1000,-251,734,460,-400,-15,1000,-120,-560,1000,-299,251,-1000,462,1000,-489,-662,1000,842,-1000,-1000,-235,929,212,669,317,975,532,97,-735,836,-1000,93,-466,287,456,-711,-1000,398,-1000,89,-1000,216,626,-365,577,-220,-1000,-813,-252,719,1000,-525,223,457,232,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Float:LTIuMTQ3NDgzNjVFOQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{1000,1000,493,580,264,-949,-1000,522,-392,1000,773,1000,1000,-238,-592,-594,-23,957,-194,-652,1000,63,803,1000,1000,-241,-515,617,405,-138,826,-791,-370,-790,-1000,76,1000,-1000,166,-1000,-1000,-461,-597,89,492,15,-263,-1000,1000,1000,1000,-928,900,-800,-25,1000,458,-658,-512,920,-604,1000,-413,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{424,-358,-60,-116,275,-1000,153,980,-163,169,170,426,459,-783,-82,-889,277,693,77,-401,-458,-282,165,-457,471,-170,380,378,-224,419,127,-1000,272,-239,-496,-1000,624,-559,308,-235,-633,-53,-597,-289,1000,1000,-130,-418,645,1000,260,-58,18,-453,-1000,-370,116,95,-404,159,317,149,-596,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Float:LTQ2LjE=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{1000,724,-461,-806,174,-229,-1000,-1000,755,-188,385,-698,-206,401,-1000,-168,598,742,788,143,1000,1000,-1000,703,855,-892,-1000,-274,661,-734,1000,102,-410,-636,661,-1000,6,-995,403,-1000,-890,-656,-808,-192,-1000,-589,-177,-1000,-1000,686,-156,-130,1000,629,1000,-477,-269,-345,663,1000,-663,1000,641,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Float:LTQwLjc=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "floatValue():float",
            new int[]{278,1000,-407,-314,-276,375,528,-1000,738,-431,58,-733,-532,-294,-1000,1000,1000,602,1000,-1000,1000,1000,-416,-124,565,-716,-1000,-1000,1000,-342,-1000,-462,-738,611,661,295,-1000,33,-1000,-113,999,589,-827,1000,-1000,978,-1000,-1000,-1000,-1000,-156,1000,1000,366,21,-640,-1000,921,1000,-575,1000,1000,-1000,-752}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTA=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getDenominator():int",
            new int[]{615,-452,-399,-410,-267,-983,-1000,157,763,750,-1000,239,-1000,1000,1000,1000,-661,-230,871,1000,1000,-42,-193,-882,-1000,-972,1000,-503,-708,-1000,-916,-911,-667,1000,-532,1000,-1000,-1000,-1000,679,1000,824,719,788,957,-1000,731,-1000,503,-1000,-485,352,-1000,-285,259,558,-1000,875,906,-93,770,-1000,106,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getDenominator():int",
            new int[]{523,-1000,-37,282,534,-792,-538,545,640,-225,1000,-476,-807,-903,187,-123,-166,-999,-37,-444,-266,-677,-561,-914,-1000,1000,-751,1000,196,280,114,755,1000,-1000,1000,1000,1000,400,207,-222,-400,211,874,404,-806,451,510,400,-15,-597,1000,-552,1000,-916,1000,747,359,-993,920,422,-791,400,-923,15}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getDenominator():int",
            new int[]{1000,886,-721,242,-267,161,-271,157,763,750,-1000,470,883,1000,1000,35,-641,-679,-287,1000,1000,-219,-542,-309,583,-1000,855,-1000,-1000,-299,-916,-234,-1000,1000,-759,-852,-1000,-1000,-857,911,1000,1000,1000,-1000,-530,427,-1000,-1000,-1000,-102,-322,352,-1000,998,-1000,-316,-1000,796,-997,-1000,770,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getDenominator():int",
            new int[]{-67,-148,83,1000,769,523,-126,111,-534,-400,435,594,-339,-697,-1000,132,-603,-321,-66,-950,-568,-1000,-950,304,-1000,174,-509,-41,-1000,617,530,1000,-311,197,987,-68,-233,607,-617,-654,-1000,-1000,-1000,155,-418,229,790,1000,635,-847,-146,328,549,-371,784,770,766,-772,343,474,330,1000,-1000,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.Integer:NQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getDenominator():int",
            new int[]{1000,-452,-734,262,348,-1000,-671,1000,-807,20,1000,-330,-1000,420,1000,219,1000,90,-214,-1000,-44,779,907,-817,-1000,29,329,580,1000,-203,98,1000,-9,-581,-436,831,1000,-20,-724,708,-702,-306,1000,971,-199,660,775,-123,1000,-979,-28,-1000,-1000,952,619,349,-48,1000,452,1000,-842,-86,19,723}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getDenominator():int",
            new int[]{1000,-276,922,583,328,-857,-778,649,908,819,-704,-1000,-137,1000,1000,-156,1000,108,423,1000,1000,931,383,-799,-529,-77,899,-588,-820,540,-343,-1000,100,207,-968,103,-40,-1000,641,1000,949,1000,803,609,139,140,-610,-1000,-641,-545,828,1000,-497,416,318,-787,-922,1000,507,-540,-370,649,-791,92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getNumerator():int",
            new int[]{-387,775,1000,487,844,590,240,-229,-146,678,263,-220,-1000,-182,483,900,-218,-1000,-1000,-135,45,288,-551,-241,-1000,996,722,-1000,964,457,142,964,556,1000,-996,-198,400,165,-678,230,-1000,-412,-517,-344,-1000,-270,-331,-191,809,-1000,-1000,-239,77,87,-1000,1000,996,-212,-434,-889,823,844,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIwMg==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getNumerator():int",
            new int[]{-95,447,-202,-553,853,862,403,-1000,969,-571,-1000,253,-275,524,79,1000,387,-302,916,-1000,-1000,303,382,212,429,88,893,-1000,607,-433,-207,274,-900,1,-966,1000,1000,1000,-1000,-293,-372,-1000,683,-512,-1000,1000,70,-687,-1000,-414,356,965,-671,-1000,-288,-31,532,972,326,-78,-899,-3,1000,-241}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:NDk=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getNumerator():int",
            new int[]{498,-43,496,-74,333,-367,-423,-1000,466,80,-564,-458,-638,-390,-340,1000,735,-114,-266,-790,-1000,1000,546,913,1000,315,814,-502,234,20,-435,625,-385,455,-1000,751,1000,-41,-225,-742,-221,-960,546,886,-1000,526,-938,-673,-674,-621,-232,887,-1000,-90,338,135,565,464,-198,259,-653,-130,841,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getNumerator():int",
            new int[]{1000,-100,146,450,-586,-704,350,151,881,932,-336,-1000,704,-942,153,277,-882,-310,-654,-1000,-375,-363,284,-1000,899,997,289,-277,686,866,-406,563,-242,-19,-435,734,1000,590,46,1000,-573,-1000,-82,996,346,149,-1000,737,1000,-625,511,-834,1000,55,1000,1000,1000,-666,-675,-47,43,195,-964,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQx", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getNumerator():int",
            new int[]{-172,234,413,70,816,866,634,-132,729,282,-607,407,-778,-91,1000,1000,-710,-708,-1000,-589,-291,1000,-627,-1000,-914,544,326,-1000,215,315,606,754,245,-117,-1000,-787,412,948,-825,387,-393,-879,791,1000,-665,-166,1000,1000,533,-558,353,1000,671,925,-235,1000,1000,-27,-190,-867,866,1000,17,624}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math.fraction.Fraction",
            new int[]{-550,-755,699,-812,508,126,-314,918,512,-654,-804,-244,-91,797,686,-465,-223,-363,-388,-876,605,500,670,939,895,397,344,585,-50,87,988,-437,190,926,409,328,-637,96,-291,-180,65,-489,8,69,-841,645,-401,451,877,-904,-937,-265,645,771,935,-780,19,235,604,899,503,-984,319,893}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math.fraction.Fraction",
            new int[]{-524,693,-559,789,-837,-883,-526,-57,-847,-995,-610,101,-936,64,930,-662,396,-269,-31,-269,957,-874,621,-287,-941,201,-239,-192,405,-366,-792,-3,528,-728,-424,-759,-334,-202,-422,-386,-945,-307,-556,881,-279,-463,-973,406,-886,231,158,-186,-857,806,872,-41,-468,-844,224,-542,807,-754,596,-725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math.fraction.Fraction",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math.fraction.Fraction",
            new int[]{-7,268,351,122,994,487,455,-489,-966,-535,488,762,-216,-967,655,-390,837,-43,631,-466,111,-567,-92,863,356,58,-889,878,349,-264,-324,-437,302,-767,-952,950,10,-37,-654,891,-622,980,-683,526,82,-876,291,725,337,-190,880,329,9,42,174,166,638,-126,-668,-513,330,-194,600,-272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math.fraction.Fraction",
            new int[]{74,-73,618,-254,-167,-460,941,724,358,939,503,136,190,-536,84,-436,-553,890,500,-192,-421,465,855,-993,92,224,778,-126,442,-78,239,-895,-710,-781,-763,297,-678,-399,-528,-362,-96,-90,960,-974,-6,-643,492,307,-59,397,-188,870,-938,954,-574,964,220,-863,-898,811,-978,-172,251,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "getReducedFraction(int,int):org.apache.commons.math.fraction.Fraction",
            new int[]{-486,-809,601,265,44,194,461,65,751,-441,763,694,95,-350,31,911,522,-669,353,-489,542,-702,620,-597,-957,-377,-228,-163,-711,807,364,-689,990,961,440,962,-123,-450,573,-823,-739,552,737,544,-773,875,630,303,313,-630,-962,-994,-233,-85,886,-782,-154,662,736,801,976,798,652,-457}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "intValue():int",
            new int[]{-54,-971,1000,-173,-414,495,119,1000,-763,1000,1000,1000,300,549,1000,-212,285,1000,534,-1000,-575,890,947,1000,-421,59,206,-440,949,1000,-233,-538,-1000,1000,352,-1000,258,13,-844,319,-1000,1000,-71,698,395,-182,-1000,-161,145,1000,-1000,1000,-1000,-1000,-108,-57,1000,1000,-386,-1000,943,851,1000,853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "intValue():int",
            new int[]{370,-1000,-185,328,-6,659,-354,1000,67,614,-338,131,289,724,465,342,-353,1000,712,-583,-103,63,-606,0,9,-344,130,229,800,1000,-917,-631,-31,-95,-2,-371,-938,30,390,-992,-475,202,-850,394,-839,925,-1000,187,-178,618,-458,-89,-174,391,-1000,349,-139,323,229,-1000,338,594,1000,-302}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "intValue():int",
            new int[]{-1000,-867,-1000,703,-88,161,543,-869,-483,748,-222,-1000,-698,-372,-741,-198,-1000,-1000,-1000,-369,828,-1000,446,-1000,547,-1000,-973,-324,-351,1000,-165,143,1000,-1000,-816,1000,-471,759,-764,-84,1000,690,-1000,165,-959,906,948,1000,-408,1000,836,-755,1000,-730,-242,176,-1000,-1000,-187,-136,-965,-1000,975,-177}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTM=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "intValue():int",
            new int[]{-670,-867,132,274,-32,481,-151,-135,-262,798,-791,-1000,849,401,52,768,-1000,-438,-904,-1000,343,-36,-130,150,785,1000,-445,-570,-206,-763,-265,-497,740,195,-651,862,-611,808,-105,-251,518,-8,-358,1000,-393,968,497,-1000,1000,-429,141,-1000,351,-189,-350,589,-1000,-684,285,-632,516,-117,975,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:NTA=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "intValue():int",
            new int[]{700,-1000,501,-50,813,317,402,-216,662,-1000,-614,131,-577,-1000,385,-50,352,-66,166,-307,357,-301,-679,1000,613,-1000,789,-871,477,1000,-853,1000,-32,-1000,-711,-274,828,464,-1000,491,1000,-87,-975,-1000,210,180,86,1000,-400,1000,-24,-322,-939,-244,644,-518,168,-162,-1000,1000,-157,548,331,-430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Long:LTg3", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{-1000,-1000,-873,10,-326,1000,-1000,1000,1000,1000,281,761,-1000,-1000,1000,-1000,1000,-1000,-1000,1000,1000,619,-17,1000,353,1000,-99,1000,1000,-120,58,1000,1000,912,-824,240,-1000,-652,1000,1000,1000,-1000,1000,-502,1000,-543,793,1000,109,640,438,75,917,684,-770,-630,-405,1000,-1000,568,-1000,-1000,-44,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Long:LTI1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{684,601,-242,-722,-178,-660,164,267,354,-866,-181,545,-298,524,-387,-269,-842,377,926,-908,-533,256,175,-774,-514,-943,-48,-1000,-8,1000,53,-611,-477,-1000,205,263,1000,-802,-365,-1000,-463,1000,135,1000,-808,-345,-332,492,-554,132,-1000,224,-114,-193,-335,307,-37,421,993,-477,400,1000,-851,-75}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{810,31,-809,-732,1000,-717,-796,-37,-206,-819,-642,818,377,1000,1000,1000,197,-452,146,17,-452,-333,-985,-137,-599,-46,846,-834,-1000,1000,1000,179,-150,-247,-1000,-822,1000,683,-529,-1000,63,-47,52,740,-121,-37,-1000,637,-92,-1000,-673,732,-1000,-1000,268,22,658,1000,155,1000,185,431,1000,400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{-1000,-706,-247,-999,-574,-1000,-686,-26,-83,-859,-127,116,-313,1000,682,871,-1000,233,-258,-296,-754,525,-271,-1000,-1000,-481,939,553,-594,1000,53,-400,-995,-1000,-212,-199,635,291,-679,-932,103,1000,709,1000,-776,502,-91,1000,-1000,-697,-1000,377,-721,-201,-435,690,419,719,177,381,-555,1000,507,-660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Long:LTU5", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{5,-1000,-594,226,658,-1000,-17,-1000,63,-1000,-354,119,592,1000,-174,1000,-200,-463,-229,-704,-1000,274,257,-1000,-636,-530,1000,-384,-1000,156,1000,-539,65,-145,-47,523,1000,1000,-1000,-903,1000,-373,1000,1000,308,1000,-20,1000,-1000,297,205,49,-1000,-525,-356,-430,1000,446,330,424,-795,1000,1000,277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("java.lang.Long:NzM=", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{141,601,733,-722,113,-1000,164,227,1000,501,1000,-908,49,666,-846,551,-1000,1000,184,-251,-326,221,727,-1000,-648,-1000,-48,-64,-533,-365,-187,-1000,-605,284,125,166,-113,-1000,-1000,-1000,-694,1000,99,986,-808,790,1000,1000,-933,132,403,-324,334,1000,-249,613,-222,-723,-62,-820,34,835,-833,812}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "longValue():long",
            new int[]{-1000,-716,892,847,-326,390,832,725,-455,601,281,-741,-947,-528,458,-111,720,-907,-922,966,22,1000,-570,127,-649,960,-58,930,269,-333,-1000,-2,644,1000,19,459,-1000,-1000,466,305,1000,-1000,163,317,-14,949,796,1000,-685,640,-516,28,917,-104,-770,-722,713,904,-563,-932,-620,-518,-887,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "multiply(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-847,-400,179,-806,864,352,363,-694,-615,177,-755,-449,129,-577,-1000,237,1000,-761,117,-196,-737,-1000,-62,-149,1000,-395,-506,705,-259,-292,297,265,-938,810,1000,796,191,-411,-395,-1000,409,-72,1000,976,3,111,-123,604,612,-427,-1000,1000,-847,326,403,677,-573,-453,-127,25,371,279,372,876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "multiply(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{31,400,-2,1000,-67,413,743,-359,43,-104,593,-429,-9,-704,400,386,-879,-564,230,507,162,-781,664,-417,44,-323,441,275,-259,-264,1000,189,-1000,747,993,477,999,630,-69,-1000,-2,-671,695,-353,575,-652,-1000,658,-43,-827,461,967,820,264,-109,-866,-712,1000,453,-1000,1000,-207,512,312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "multiply(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-371,-1000,454,408,914,-479,1000,1000,655,608,385,-474,10,-625,-270,1000,-95,-347,1000,1000,-303,-927,1000,-599,223,-1000,-302,769,1000,-1000,-449,-594,-549,-643,1000,1000,-1000,-368,983,-30,-237,346,1000,534,197,-241,414,32,-16,-579,305,407,1000,894,693,-1000,-840,850,1000,168,-522,-748,-1000,338}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "multiply(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{225,155,-1000,98,532,827,-171,1000,-132,-1000,-1000,1000,472,136,-306,-844,-1000,-549,-22,-224,-1000,27,-1000,59,-1000,991,-106,1000,723,915,573,-1000,93,38,1000,1000,-863,-528,1000,1000,-200,-1000,1000,1000,-449,1000,1000,-1000,1000,-552,117,-1000,-1000,1000,-844,-1000,596,-104,-549,-624,-1000,-143,1000,-448}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "multiply(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{119,1000,-1000,-613,-216,-637,1000,37,1000,-856,-656,-771,-1000,-1000,710,-744,1000,-62,1000,-137,586,1000,1000,1000,-193,1000,-637,138,1000,187,-820,400,-765,-755,-1000,256,809,-1000,-407,-763,-384,-935,-291,583,-1000,-881,-66,356,-1000,1000,-146,-437,1000,-1000,-1000,-365,-362,425,-997,543,-1000,890,-796,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "multiply(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{412,1000,-137,-710,-92,730,518,-81,-1000,-320,770,-681,278,-224,1000,393,-300,-377,182,802,-259,-1000,274,-503,-326,179,650,156,854,-1000,579,411,194,1000,1000,699,980,842,248,-60,-200,-285,565,14,598,-300,371,1000,89,-647,483,-159,177,39,-667,-1000,-585,449,767,-1000,551,45,-380,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "negate():org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,-1000,-816,1000,-231,62,129,1000,-723,336,-815,1000,279,286,226,-278,-1000,-1000,-149,1000,-738,-1000,1000,1000,521,-1000,1000,682,106,-1000,-1000,1000,-694,767,-360,-79,1000,1000,139,-1000,-107,1000,-1000,1000,-1000,1000,569,129,-973,-92,1000,208,-975,-282,-503,-1000,1000,244,1000,-1000,1000,-1000,-1000,725}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "negate():org.apache.commons.math.fraction.Fraction",
            new int[]{1000,624,563,-518,-598,-513,853,-479,-304,124,850,-29,-1000,-165,-134,-84,1000,654,-62,295,744,971,-613,-1000,-641,1000,72,1000,483,958,436,-517,483,-300,-315,-1000,298,-1000,229,886,-147,-919,982,-400,122,-845,-65,98,-31,1000,451,751,-837,-669,-56,316,400,-173,298,1000,652,-261,1000,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "negate():org.apache.commons.math.fraction.Fraction",
            new int[]{635,-29,255,192,-674,-652,761,30,-691,40,-436,-517,532,-229,-175,-253,359,349,-269,994,-165,374,-494,-378,-528,294,245,-488,-43,974,11,-411,746,-258,349,-818,-186,20,-139,187,-934,55,847,913,-184,-975,90,606,-399,851,-114,133,-848,-604,105,-940,779,-557,698,475,668,-178,425,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "negate():org.apache.commons.math.fraction.Fraction",
            new int[]{905,414,-222,-388,-943,-343,489,-256,222,71,943,-678,-733,-143,110,-439,884,828,64,855,530,878,-864,-538,-315,744,41,566,-933,-138,1000,-833,1000,642,-798,-847,-301,-418,535,1000,-460,-435,1000,-52,73,-1000,-400,578,-155,957,879,-782,-746,-261,-187,172,553,-235,396,886,-894,983,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "negate():org.apache.commons.math.fraction.Fraction",
            new int[]{1000,420,1000,-341,274,703,1000,-945,-174,-856,846,-373,1000,1000,483,-429,695,1000,5,-461,1000,380,-734,-1000,-635,1000,-751,897,213,1000,-393,-1000,467,-655,68,-355,994,-594,-51,1000,-944,-785,1000,-103,-333,-1000,-20,947,-689,325,-80,323,-1000,-461,215,-20,232,-1000,1000,1000,219,804,787,-504}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "reciprocal():org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,-370,-539,509,-85,-1000,663,-281,776,1000,631,-1000,-1000,446,10,52,685,-512,-889,1000,1000,-930,65,288,483,307,438,252,-209,123,-1000,508,388,444,-427,-329,-609,-187,-439,-382,-533,968,-493,417,-923,-1000,789,592,287,488,319,420,-369,-490,-195,-1000,-616,302,363,106,0,-747,958,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "reciprocal():org.apache.commons.math.fraction.Fraction",
            new int[]{1000,1000,673,-791,-861,-318,-1000,187,-39,-367,643,-392,1000,1000,-419,-684,-490,981,954,-1000,-1000,-463,227,717,994,-666,-1000,-1000,-656,-495,78,722,-771,-1000,-530,892,845,919,1000,-107,774,-1000,-360,-17,789,-939,-1000,-154,595,-137,-340,-1000,-1000,1000,-947,1000,-463,429,837,213,-340,-737,-750,-976}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "reciprocal():org.apache.commons.math.fraction.Fraction",
            new int[]{1000,559,-105,562,644,-1000,-764,-894,-821,269,583,-294,1000,712,1000,-754,503,-275,1000,-111,-1000,1000,557,310,-485,-86,288,1000,897,-12,934,500,-571,1000,-1000,530,1000,236,129,-359,1000,-1000,905,557,346,255,1000,-714,-785,591,-1000,-327,-42,845,-164,840,-486,-1000,1000,682,-1000,-666,-486,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "reciprocal():org.apache.commons.math.fraction.Fraction",
            new int[]{1000,1000,731,-360,-502,8,-1000,-93,391,576,974,205,892,1000,-181,-1000,-115,362,553,-735,-1000,532,284,1000,534,-977,-885,-1000,-645,-454,-174,1000,-1000,-928,-743,1000,1000,1000,1000,377,1000,-650,507,174,1000,-1000,-1000,-1000,551,477,-462,-250,-1000,1000,-1000,947,-277,700,1000,758,587,-506,-980,-755}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "reciprocal():org.apache.commons.math.fraction.Fraction",
            new int[]{75,-692,217,-110,88,222,-55,-1000,-788,-1000,-652,382,-498,490,-328,-1000,-49,64,1000,-660,660,-120,-233,-371,-1000,563,321,1000,-567,863,1000,-680,-1000,-423,-338,-1000,-765,-1000,-442,-1000,-329,-1000,-427,-713,1000,1000,-293,-1000,-1000,123,-732,-1000,1000,641,1000,434,-820,416,-258,-1000,64,-772,1000,-656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "reciprocal():org.apache.commons.math.fraction.Fraction",
            new int[]{-122,-948,-752,784,-476,-916,436,-1000,946,161,1000,-1000,496,570,-750,4,429,-853,832,-572,232,317,-338,-43,-393,-923,236,586,-168,-688,747,-108,156,-57,-778,-488,-400,-980,123,-521,-67,371,-198,366,699,-587,1000,967,-266,-558,-1000,145,-196,555,115,739,-969,999,-348,405,-629,-472,108,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,-267,-1000,631,528,635,-363,30,172,784,586,-651,731,41,-24,-245,1000,-1000,1000,-580,-891,-829,479,-950,-1000,880,427,-921,-1000,1000,593,149,628,-1000,548,1000,250,811,-1000,795,60,1000,103,7,797,-406,-267,645,-336,-506,-472,-220,426,-15,219,448,-168,1000,957,-366,632,-739,-316,656}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-476,585,843,-423,506,-365,-762,-677,854,1000,-454,451,-1000,448,1000,136,-1000,-159,182,-806,1000,-841,-806,80,409,-461,457,-66,705,-854,-1000,169,1000,247,731,1000,149,273,522,-1000,467,-929,-1000,-600,-7,-400,-533,-95,53,161,881,-1000,379,931,1000,-667,-382,1000,-472,28,1000,375,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{621,459,1000,442,862,-467,-707,-487,-701,1000,1000,-1000,213,-1000,174,567,171,1000,698,-542,1000,385,331,1000,1000,330,1000,-1000,978,700,412,-1000,-1000,273,-131,1000,-1000,-663,-85,930,-400,-1000,1000,-1000,-1000,814,-615,-856,-1000,403,-1000,876,313,-1000,-912,-1000,1000,857,-244,377,935,-467,1000,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("TYPE:org.apache.commons.math.fraction.Fraction", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-219,-184,-782,-768,332,869,-208,-803,482,301,-206,1000,403,-355,-566,-42,-706,-1000,-261,-760,-233,144,143,-696,-938,-359,-1000,1000,-523,-1000,-1000,-548,1000,82,968,647,-23,1000,521,-1000,-389,52,329,790,470,-935,-669,1000,507,-366,-303,777,85,1000,983,595,-303,1000,-486,-468,943,610,-434,173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,81,-72,-447,688,1000,-1000,-1000,-226,1000,1000,-289,330,518,1000,31,446,-209,628,-1000,-269,-704,-423,34,458,1000,-414,-1000,407,1000,201,-1000,798,-688,-1000,669,-1000,658,567,-1000,818,-300,76,-1000,788,956,-812,-181,-81,-1000,-294,-452,169,535,1000,-91,-1000,-161,-314,174,1000,-190,399,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,-270,-277,1000,20,-1000,-225,-78,941,-755,26,-1000,169,-494,-387,-79,1000,-783,-908,-1000,52,-1000,787,-49,-358,1000,124,-918,-1000,-353,1000,378,-337,-1000,1000,-581,277,687,-285,-62,-428,968,1000,346,410,127,559,84,68,767,-1000,-518,-76,-424,629,1000,1000,1000,1000,-44,813,-997,988,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-331,457,719,-638,-154,-388,93,-764,401,1000,-389,948,330,577,-360,747,-1000,-486,77,-373,772,-1000,-1000,34,1000,-315,-1000,-96,523,-669,-990,93,1000,302,119,1000,355,-386,567,-1000,818,-680,-1000,-1000,-381,-763,50,654,621,-229,-1000,884,89,931,1000,-91,-883,1000,-314,23,1000,255,399,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.fraction.Fraction", "org.apache.commons.math.fraction.Fraction", "subtract(org.apache.commons.math.fraction.Fraction):org.apache.commons.math.fraction.Fraction",
            new int[]{-1000,-136,-397,-26,699,689,-474,-1000,483,158,-435,216,1000,-451,376,-164,419,-1000,723,-1000,-1000,-1000,-651,-591,-645,884,-347,-709,-1000,-60,-219,645,1000,-553,-122,496,396,228,-555,-170,199,502,-305,-246,873,-463,96,800,-45,65,144,-170,-56,796,1000,491,-659,1000,889,-176,149,-1000,-688,1000}));
    }
}
