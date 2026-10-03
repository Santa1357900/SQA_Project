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
            new int[]{658,567,163,685,-609,-68,430,13,-130,-371,-219,-955,781,925,646,44,-950,-873,935,-635,-154,-473,252,-647,66,418,-231,442,-10,402,99,382,-792,-656,-556,-593,-49,540,868,-835,-517,-78,596,-727,-614,469,934,473,-780,373,-385,-469,-695,-21,138,273,198,-996,-941,-567,797,163,45,-636}));
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
            new int[]{18,-730,238,-615,-644,342,205,-855,-91,63,-445,535,-473,397,-979,151,-348,186,-244,187,137,-113,-571,-922,-246,-126,-684,997,-699,-199,940,-652,-304,608,827,-659,400,-995,439,-290,111,499,-534,-314,-206,-612,305,457,-228,514,-220,505,-104,43,939,-547,599,-953,-894,-624,-974,239,283,-164}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-774,-334,719,1000,-283,751,-968,775,294,280,-1000,225,-329,-184,-1000,1000,86,-535,-125,320,-621,199,-1000,-159,193,848,1000,833,-777,544,1000,1000,-429,-943,974,-432,-66,246,1000,1000,1000,287,-1000,-342,-1000,-459,829,-1000,-511,-489,97,-53,-922,-353,-1000,-825,-54,-290,400,-265,-1000,-477,-934,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-920,1000,-877,870,-1000,-1000,-1000,916,1000,-743,-824,488,-278,113,-433,-938,-876,-187,-289,1000,732,-65,230,-1000,-264,-1000,-120,-1000,157,-1000,-76,1000,411,-218,-653,-1000,-483,303,687,-122,-799,74,-6,147,804,-194,820,-1000,-41,507,340,-550,108,330,-982,842,-523,-553,-1000,67,196,-999,-93,519}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{-968,709,-497,552,-651,-510,-561,237,826,609,296,-79,285,780,-255,100,-961,912,-500,954,706,130,-967,107,-382,-424,-857,-868,-911,-901,236,987,-195,85,22,-151,-192,563,520,-118,-782,458,756,358,664,-579,-645,-760,651,989,333,-139,-397,-982,-160,-352,-848,-690,-667,-41,409,-879,-265,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU3Njg=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{403,-122,-513,198,-343,-184,-913,-424,-48,-506,-275,-70,-269,177,-127,684,-674,946,-605,227,-826,-964,-423,-109,-423,-609,-197,59,177,-259,-889,277,-715,559,-258,-524,-508,-776,-3,499,568,404,-823,-991,-453,243,221,551,-866,276,-250,-965,-934,150,675,-721,918,246,-738,-695,982,34,599,-880}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "addAndCheck(long,long):long",
            new int[]{256,-1000,-1000,963,546,-516,-1000,522,-109,-184,-517,582,-1000,-60,-980,1000,489,-62,-178,-623,-1000,-167,-881,-1000,798,-281,740,40,309,-971,-9,783,-1000,1000,211,-570,-727,-1000,872,718,587,-656,-640,-1000,548,-726,524,739,-681,441,-177,-1000,-1000,-206,905,-1000,615,-575,-989,-543,1000,598,706,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{1000,-1000,217,-458,105,719,749,-653,-1000,295,1000,842,-610,-1000,-382,-1000,1000,1000,-1000,1000,-611,554,1000,309,193,-758,302,-517,1000,1000,-629,-1000,-1000,-164,226,-1000,1000,-75,354,479,1000,-1000,-1000,134,541,-843,2,260,285,-1000,-799,744,-507,981,-151,-670,806,215,-722,-1000,1000,531,650,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{47,275,719,893,569,-436,786,623,84,51,440,832,68,-632,12,27,-337,-902,-509,727,-23,307,-521,13,-110,-460,-283,569,710,-681,-521,844,602,-752,340,11,-888,254,887,-340,-727,-817,770,-169,-174,12,179,-136,891,-725,290,853,-948,933,543,-12,290,-93,-318,-20,-566,-253,-565,934}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-647,859,429,-718,338,-12,-609,311,783,839,-631,-415,444,535,304,206,-607,1000,-19,-239,584,212,-646,742,-1000,509,-521,-267,-396,857,-451,-422,-432,253,-494,438,-79,-87,-637,397,-767,1000,-777,-16,134,273,284,-143,360,-817,-495,1000,-663,-385,-207,73,-45,-986,209,-1000,-735,254,-200,-764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{893,515,425,-497,-705,-155,-563,-979,372,-636,-453,357,-53,188,-246,739,169,-972,743,481,-689,619,224,-366,384,613,850,-46,559,-116,172,230,-9,266,-702,-941,-986,-859,969,407,853,16,619,-45,-958,256,-350,149,533,-538,631,-898,-862,658,-567,-926,-642,-622,-614,121,-37,-933,-498,-340}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-876,950,-361,-277,632,728,-668,35,-846,-398,456,547,-299,-299,-868,-744,523,-186,999,494,-77,706,747,-764,-56,433,802,367,191,-888,387,41,666,721,416,-825,-134,-137,709,-477,-433,172,-117,302,773,47,926,352,92,756,946,-999,451,-423,-115,389,516,629,79,876,-580,311,465,-680}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-242,688,-855,578,-583,337,939,-29,-818,-676,-799,543,442,446,-675,-85,-796,-194,-956,-831,-2,-493,-622,-68,703,332,-294,-73,-377,-844,698,-122,707,914,114,641,-810,383,472,731,134,701,484,-946,340,-377,-210,-444,-39,799,-983,-216,-263,58,489,-133,-742,-387,982,331,891,386,882,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("java.lang.Long:MjMwNTg0MzAwNTk5MjQ2ODQ4MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{1000,-1000,23,-458,210,719,1000,-564,-757,295,-873,711,-1000,-1000,-702,-1000,1000,924,-836,1000,-976,900,-1000,1000,449,-1000,551,-942,980,935,-1000,69,-1000,-328,634,-1000,891,-21,683,743,1000,-1000,-1000,134,1000,-1000,-241,-681,-19,-1000,-1000,744,-535,1000,-451,-670,1000,556,-192,-911,1000,1000,1000,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficient(int,int):long",
            new int[]{-1000,368,-451,169,-1000,-755,215,-176,1000,-132,649,-199,135,300,1000,1000,-971,131,1000,-659,696,-1000,-153,-88,1000,1000,-71,1000,-1000,-1000,1000,1000,-602,-1000,-1000,1000,-1000,1000,-1000,-1000,392,-232,1000,-575,-168,850,-1000,523,924,-1000,-824,-1000,-857,-349,-222,601,-1000,-1000,-1000,1000,-482,-1000,178,1000}));
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
            new int[]{-829,-899,-517,688,-722,-481,948,231,685,596,-350,839,-704,130,-379,167,-498,868,-725,-205,-402,471,-882,-435,470,387,-117,-326,-340,-230,-169,-626,370,755,-31,-969,-61,-424,434,-688,943,304,415,-719,122,305,-372,335,108,901,-413,-952,107,-808,-317,-732,561,-513,457,61,-850,-590,329,-491}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-1000,915,1000,-506,-424,-1000,-287,-703,614,-1000,-1000,-95,100,927,997,-1000,973,-620,-1000,1000,-566,-605,-604,-475,556,-971,-1000,-615,-207,1000,573,-1000,1000,211,839,174,1000,-363,577,1000,18,929,-223,-1000,930,1000,-1000,-128,-130,-331,-610,-694,322,1000,-1000,736,577,-1000,309,1000,-742,1000,-832,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{254,-929,-267,350,1000,-310,425,-1000,-152,496,-445,-981,-830,-379,-328,883,149,1000,-3,-676,-189,574,727,278,-614,-854,381,1000,-163,-567,100,-189,542,-340,1000,350,-1000,275,-988,-490,643,-4,77,1000,-41,653,-101,1000,929,-559,684,-421,409,-743,359,-1000,1000,-501,-464,-911,-794,-187,255,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{908,20,-615,-36,484,-943,-312,821,605,-155,-82,-366,27,754,-912,-604,-998,337,-176,-361,-753,-316,-874,-475,171,-399,393,-126,923,-991,414,676,285,53,51,-420,433,665,826,832,158,549,-233,-832,244,-86,-533,-684,423,-330,-399,892,-443,-761,-137,736,511,-328,684,33,568,-657,953,-82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-957,-170,-481,-848,658,728,-585,469,-19,936,10,352,734,-726,-793,806,75,82,722,-111,-500,942,-24,-180,352,432,913,-614,893,-655,314,-229,-723,-983,158,912,-197,554,-897,437,374,-951,-742,-712,-776,-809,81,-724,651,-914,-541,-57,762,-464,801,-616,-958,547,316,-50,-68,-554,592,-863}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-700,753,-226,334,-390,211,-27,321,40,-553,-581,-789,-215,-449,-339,462,-97,-525,759,221,-376,732,577,294,-763,-949,833,-437,-10,672,-553,734,-203,101,-288,212,-384,-624,320,880,875,118,398,624,-507,-978,343,-465,684,798,488,-76,-955,29,-611,-526,180,973,-97,-782,-464,-155,96,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDdFOQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientDouble(int,int):double",
            new int[]{-128,-124,802,577,678,47,411,-220,-417,21,-463,-832,-676,71,-716,618,328,704,-197,-210,-611,154,494,994,71,-855,814,656,-121,-474,347,-336,684,-510,842,802,-94,341,-599,453,496,-12,-877,-188,-93,732,-845,-41,-79,-756,337,-675,464,-852,-608,-711,-667,-917,-30,-11,169,-256,766,-254}));
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
            new int[]{16,-497,529,861,-114,479,-761,988,476,624,613,-281,-726,-607,972,-737,404,768,725,-77,711,113,750,-847,983,154,795,-100,926,-890,56,-953,912,-984,395,-35,225,170,272,194,563,257,398,82,893,217,370,861,-609,-795,-900,-913,187,81,705,606,890,808,-264,-674,-544,-163,-906,-98}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{584,378,794,914,-90,326,-992,606,218,962,612,832,656,-6,176,746,297,-451,-516,-241,-340,310,-818,-231,-405,797,671,-964,-126,-732,-393,503,-777,-242,-299,-477,253,653,101,-15,738,-171,-404,-355,-910,877,108,-894,453,245,504,327,875,-802,-368,-173,231,-561,652,790,214,948,-762,-819}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{695,514,761,630,785,-503,568,332,323,-485,-471,453,643,-166,1000,526,-291,921,-85,856,353,392,577,69,-153,-382,637,550,588,-594,-943,489,-935,-797,-827,-547,-3,-840,595,386,555,28,-382,808,-99,341,-964,-422,-20,-347,842,-953,379,671,-855,-307,-563,58,-177,-601,-700,-587,-79,410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{-110,-870,787,-801,-568,794,-165,846,-527,494,-1000,580,220,759,836,-844,-692,129,-460,589,-956,-351,513,218,-319,663,177,-301,-727,-917,621,367,25,-671,931,646,767,34,-309,-828,-774,-784,-917,-873,89,-850,222,916,-498,434,-28,-245,-374,846,-694,19,136,-466,489,-750,-864,765,845,-379}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "binomialCoefficientLog(int,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "cosh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double):boolean",
            new int[]{999,-905,-708,-105,-382,416,551,175,-950,-865,338,-124,329,-424,-758,744,-631,-512,-903,-21,-94,-233,907,-976,-641,250,195,-743,-75,695,-31,-798,689,402,741,-778,157,867,-555,601,234,-713,505,439,375,799,768,-182,435,-65,-388,370,523,901,118,-489,104,45,863,-588,526,136,661,-954}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double):boolean",
            new int[]{25,-497,-598,499,212,-226,-489,-894,-842,996,-137,666,176,630,-349,-305,-318,-189,606,853,472,-601,923,-975,-77,-697,169,300,975,257,-123,-487,-719,-418,-121,-620,-558,-648,736,-798,963,879,112,-395,181,-549,-640,-130,272,-141,186,1,-791,394,-150,911,-543,739,-981,804,-829,-561,-828,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{-886,-536,443,776,-990,-914,-165,816,-286,-584,-481,395,881,-309,809,72,-971,-864,648,-264,590,-662,-949,354,-210,642,596,471,-104,782,696,756,336,-166,-288,365,-615,-844,616,190,719,-531,886,160,-28,477,571,670,636,-508,81,-33,-880,-570,-876,-172,602,-784,458,-749,432,178,621,22}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{1000,-907,869,-686,1000,389,-487,-39,1000,-653,-131,-1000,846,56,235,379,-851,880,-1000,1000,-979,-217,-535,-616,-394,-411,-7,-1000,362,-346,520,3,-1000,-439,361,-136,-633,718,-265,-88,-1000,-638,-297,-1000,-159,1000,-1000,-187,432,-259,439,-460,554,97,-773,-682,-360,-381,-432,614,810,-973,131,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{132,-839,879,26,-984,978,-554,-186,323,-321,-241,-494,285,-224,184,-216,-385,-935,80,-78,-283,-147,-949,-336,926,918,-230,-594,964,-906,725,603,-292,-853,-426,791,-382,263,449,916,-266,907,-498,774,-988,-907,283,-746,-45,137,90,-173,-11,903,385,272,-157,193,522,947,-700,-512,609,125}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{-913,294,992,280,-913,-799,-916,-354,-396,-92,211,308,-676,-478,65,-901,-837,-979,246,-36,-274,636,-43,867,-779,633,495,228,-849,-988,-885,489,-898,-486,-900,901,-858,927,-407,912,943,154,-344,-376,571,750,340,-271,-908,-296,100,34,-364,154,808,-737,-641,378,-156,-873,-141,-43,231,873}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double,double,double):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{-1000,1000,-1000,-1000,-365,-1000,27,-1000,619,-1000,1000,1000,-1000,-1000,1000,1000,-759,1000,1000,-1000,-371,705,1000,-1000,1000,-1000,791,-1000,-587,-635,-1000,1000,-784,-118,1000,-1000,-761,-962,-1000,1000,-56,-633,-1000,-1000,400,-661,1000,1000,-1000,802,1000,1000,-563,1000,-1000,1000,969,1000,1000,-785,20,1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{886,-1000,-1000,1000,893,675,-1000,-425,-416,1000,-469,-1000,-1000,-319,-942,-1000,1000,1000,-1000,1000,-477,-853,-208,990,-372,1000,-1000,917,1000,1000,-1000,580,281,-1000,225,-1000,12,548,1000,-554,515,-1000,956,-767,-443,1000,-343,814,1000,994,1000,-1000,-1000,-1000,-621,187,-1000,-1000,-2,-998,-1000,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "equals(double[],double[]):boolean",
            new int[]{524,651,347,811,931,910,539,827,541,634,-694,-171,-812,-137,-497,736,-58,293,-147,-778,-203,-291,766,684,-2,212,-355,726,123,883,189,-945,-903,-85,84,-868,-520,234,351,-103,537,158,-342,-529,917,722,-77,-132,-955,-5,-365,432,774,-126,536,-50,-744,702,-501,-753,-466,-683,757,-596}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorial(int):long",
            new int[]{71,527,-273,794,-377,13,330,745,-354,216,918,-973,254,-15,-311,-987,116,985,-804,724,-508,-507,830,119,-987,819,19,720,9,-407,-139,387,465,-704,214,477,-582,-313,40,-810,-8,-943,-793,-560,-844,-405,-141,257,757,596,-992,-571,-564,-288,-975,-155,601,994,-633,849,-183,106,-960,797}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorial(int):long",
            new int[]{-524,826,62,509,-18,977,617,282,-596,-979,-280,479,-263,-760,888,779,-252,963,-463,802,-821,682,913,-555,429,562,129,227,-623,902,883,776,-794,252,663,-987,-130,133,-932,-864,-789,-399,239,-935,-386,277,360,-337,579,855,644,749,616,-376,-652,-318,-533,57,915,-859,-194,151,605,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorial(int):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialDouble(int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialDouble(int):double",
            new int[]{288,-766,-957,494,-352,260,447,-76,928,-942,-106,-116,-885,-709,189,-682,724,-295,197,774,774,-377,238,42,-794,735,30,-796,-93,-16,-794,837,-618,978,919,-488,-347,72,42,251,-990,35,-888,113,891,-972,-712,725,-679,-463,334,232,-877,845,913,878,421,-357,331,-776,-188,474,-728,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Double:MjYyMy43NjU2NjQ2ODA5MzY0", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialLog(int):double",
            new int[]{502,935,-214,798,-747,588,390,-942,343,-278,-434,86,-135,542,881,393,140,-647,902,74,-845,-455,571,983,-246,-289,518,167,414,-860,863,-653,572,262,771,-700,157,982,192,-772,-775,-659,49,-619,-570,426,-544,529,413,-964,-551,246,-852,-723,220,-635,792,-72,832,479,-371,220,-985,-115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialLog(int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "factorialLog(int):double",
            new int[]{673,688,701,496,-120,787,-912,-321,863,84,577,690,-180,825,-428,-357,-300,35,-683,185,-256,-668,494,-415,512,-816,757,-71,-704,-572,-152,-234,-53,-837,-399,-369,-174,548,-659,85,853,488,-1000,-899,-572,-309,583,968,-609,178,-446,-784,-889,-29,-834,-496,316,-638,799,890,313,976,-40,-535}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzI=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{199,21,352,263,-257,505,23,846,-973,289,9,421,-698,478,-534,-896,-720,-400,-494,380,-882,-447,-685,-351,-946,874,-155,196,78,874,-901,-358,-424,-953,-585,820,558,-318,546,-415,745,-406,837,71,-405,-483,357,-430,-832,858,437,401,602,-801,-260,850,-45,269,-249,-366,-829,916,339,169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{-1000,681,751,21,132,-503,-289,-542,-855,456,318,-1000,-727,-542,-437,1000,-775,-70,-507,-339,-4,-520,-77,-1000,245,-931,-1000,1000,1000,431,1000,-1000,360,340,864,67,-873,950,422,568,-949,1000,1000,-1000,-13,462,-151,-91,1000,-633,915,408,868,-934,-999,1000,11,935,62,116,-549,-962,-359,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mg==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{65,-110,-307,1000,-121,137,1000,449,488,317,-354,-213,-981,725,-754,392,-946,-1000,-453,1000,1000,-1000,-275,488,75,321,-591,-1000,-478,1000,-535,222,599,-657,-376,30,-1000,276,-190,-145,1000,29,1000,639,-267,-563,-85,-611,-1000,274,-705,1000,-140,-80,1000,547,379,1000,206,1000,229,-227,-212,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{294,-411,-257,-996,158,-503,15,-183,770,131,795,375,591,-33,213,-575,372,230,-163,364,-554,993,811,-318,-181,-607,-216,822,847,-874,574,643,-157,329,853,-376,631,-607,-420,433,737,-691,375,559,684,735,607,-675,-279,135,-456,-188,-859,43,-393,-68,-630,-178,515,246,-21,-892,880,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "gcd(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "hash(double):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "hash(double[]):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(byte):byte",
            new int[]{-51,-895,-427,880,374,-561,-23,-401,-184,-908,-188,929,343,-851,975,903,611,-484,280,-908,-399,-204,863,-997,831,214,955,-264,183,558,-570,113,-192,566,-249,-860,227,-536,-505,-925,713,-499,-483,-314,311,199,993,297,435,808,60,158,890,-45,609,-662,384,-135,-133,-142,98,-321,873,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(double):double",
            new int[]{214,638,-198,908,102,250,-550,-990,-250,726,616,-559,-877,971,-346,-751,-452,-568,378,-581,-894,672,-198,-954,-944,-149,-183,-277,-138,-545,-966,-155,-901,279,-238,416,-188,-586,-557,10,751,-233,-307,605,-703,537,-421,132,-126,-43,746,-178,-821,634,567,668,654,-167,-371,-328,276,196,-449,925}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(double):double",
            new int[]{896,595,319,-978,90,137,-886,998,-593,-101,319,-43,-198,361,-956,-976,480,623,678,-532,-614,-148,906,-90,181,886,973,-834,494,549,149,-488,-259,765,762,-534,335,819,-1000,656,-1000,-53,592,648,-877,-144,871,-29,-626,598,153,-328,634,-1000,-1000,1000,836,493,-366,-91,575,218,-502,-265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(float):float",
            new int[]{-292,868,-666,235,-476,-73,-879,-233,-202,-818,-971,528,702,650,-752,971,-636,-390,-332,99,-932,-10,556,423,-665,-456,-68,341,-271,-700,-718,290,-760,44,415,-302,986,227,-700,820,606,630,-464,849,214,-659,732,-637,84,171,-464,127,-689,509,-687,551,636,776,517,672,-659,-836,-267,453}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(float):float",
            new int[]{-439,-77,272,-350,-114,-603,-795,703,-518,-847,-509,-455,887,347,-962,981,-223,65,-829,-205,318,197,828,-943,730,-628,-653,558,-244,12,800,776,721,-127,-956,-478,774,52,659,433,528,-613,767,789,-188,392,369,826,542,471,-742,-344,-188,-33,-126,158,-772,-164,482,851,231,205,980,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(int):int",
            new int[]{110,393,184,929,339,551,-120,-375,961,-319,279,924,-544,128,920,-760,-555,416,-673,-612,-837,-911,-49,-180,112,-1000,147,-740,881,-566,-624,-458,945,273,716,505,-315,260,210,-24,817,344,-446,-942,-25,-913,-764,196,-732,140,318,862,-696,-76,-222,-952,397,826,560,245,426,-558,-695,-915}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(long):long",
            new int[]{-319,-714,493,-966,150,238,-488,123,-687,938,-253,-927,-683,-167,-975,562,-335,-182,845,-535,-717,201,789,55,-952,-406,901,415,-856,411,72,207,22,-822,-785,935,-769,-96,761,-535,942,406,824,-289,588,-345,-675,107,-283,-24,245,-203,-90,-756,-721,280,-609,-916,-836,-301,-181,-455,162,-107}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("java.lang.Short:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "indicator(short):short",
            new int[]{271,-736,665,-512,395,-703,539,-635,970,-881,-946,-207,252,-600,-380,-192,826,460,244,-306,-171,489,226,433,-391,423,143,-871,453,414,756,-825,-40,572,614,-927,-515,-294,54,-709,619,175,-891,309,141,619,328,-907,62,-639,990,-363,460,-441,307,-57,630,806,274,794,757,825,-370,-942}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzUxNjg=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{224,-529,314,491,427,1000,642,-1000,-498,-1000,225,-827,-1000,-1000,-1000,876,1000,-382,-1000,1000,208,-905,-107,881,-781,-284,-1000,-612,429,43,-1000,900,-367,889,-1000,-254,1000,-587,1000,500,-1000,1000,-240,-24,842,489,271,977,39,-322,-520,-423,-843,-1000,906,-1000,1000,-414,-1000,-1000,-607,-882,468,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{-5,-409,657,537,131,481,350,-300,193,-578,526,415,865,-54,-715,-1000,841,-69,351,-135,-544,256,1000,-223,316,81,-287,-14,-616,701,170,1000,444,-640,878,-244,363,-838,-146,441,-1000,-347,499,-918,-903,1000,1000,-897,-601,-773,33,978,-27,536,311,-220,-61,-146,-113,258,-42,-20,40,52}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("THROW:org.apache.commons.math.MathRuntimeException$1", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{38,1000,-829,-548,426,-1000,-1000,-1000,764,291,-246,-968,-1000,-964,-667,1000,377,378,-900,546,-379,-329,-347,-1000,-48,433,-1000,-35,1000,-1000,-877,-201,486,763,369,514,490,-638,-477,-266,146,-375,313,211,-105,-1000,-1000,229,979,-290,-971,-82,836,-1000,450,-692,755,-305,-360,-376,-517,-1000,-505,-156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{945,-490,650,-654,765,-210,-221,33,159,555,676,499,-519,621,-155,-241,259,-243,-309,280,786,-910,488,-949,96,582,-384,511,948,-505,186,512,-913,581,-666,145,235,-374,-792,872,-2,947,913,212,-776,426,-434,-35,191,788,991,860,-533,928,-420,426,-734,344,353,862,714,950,374,740}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{1000,1000,1000,-674,-1000,1000,1000,1000,226,717,689,1000,1000,1000,743,-1000,-1000,-492,-296,-1000,1000,-968,-806,-1000,-1000,-718,1000,-389,-278,-557,1000,1000,-1000,-565,-1000,-329,-988,-821,-689,-43,802,-1000,-1000,171,-859,-60,7,-585,-927,-1000,1000,-541,372,680,-1000,406,-1000,-697,1000,1000,-150,1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "lcm(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "log(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(int,int):int",
            new int[]{-597,647,229,364,-27,348,72,-308,-70,92,171,-292,-711,932,486,-307,135,-152,-393,-634,-970,-162,-976,-771,-535,539,-500,-762,-674,-759,-996,-937,-512,302,435,-727,614,-561,974,128,-40,132,-679,-626,593,496,311,388,-209,335,411,-621,-386,-161,509,918,609,-603,4,-962,-518,334,757,378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(int,int):int",
            new int[]{906,982,-359,-87,725,470,-402,520,-932,15,-104,-529,282,-801,-128,-355,796,571,-69,412,201,-90,85,-484,738,-841,-215,751,783,-467,-896,302,-914,414,138,711,-560,140,-913,801,228,-440,-99,541,-135,320,-406,-145,-457,-775,-616,-315,876,-230,650,224,50,-77,394,-827,-75,-391,-662,118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Long:LTQ2MTE2ODYwMTYyNzk5MDQyNTY=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-129,-57,765,676,-97,-117,78,991,-419,-684,-802,-462,-299,-370,-86,-114,297,-352,-572,302,6,750,-599,-843,-557,-460,-301,467,936,653,-432,-188,-255,-732,41,-675,-220,-756,543,-636,-285,867,-165,54,-569,-285,731,-36,-267,-700,-653,-163,406,831,-984,530,866,-327,483,85,-131,-71,-497,-795}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{398,764,121,160,-502,-169,909,-105,-556,143,388,-790,-221,459,-641,934,-997,995,-378,-540,-440,898,-796,-555,945,-681,247,-65,738,926,-748,-105,-842,594,-539,524,-379,-539,-714,-278,-324,-482,931,589,-291,477,-418,951,835,787,-108,547,514,366,896,755,609,-161,893,490,-182,-65,422,-721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{921,931,-416,-627,360,774,309,847,16,724,-925,-721,-894,-131,486,-377,761,-193,418,-912,-447,252,209,478,-899,-260,-549,-811,-994,-770,-501,-223,157,280,-63,-569,310,904,336,-375,-231,-774,-108,-200,-12,-378,-250,-16,-51,148,-803,934,298,-256,-739,610,-613,984,-261,553,596,914,164,-883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Long:MTUwMzIzODU1MzY=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{-73,250,467,1000,943,371,-1000,606,561,-572,-1000,-2,772,-787,-637,630,757,1000,-530,1000,894,-255,74,-973,267,1000,365,1000,-200,-1000,680,1000,-1000,-1000,-85,-399,-71,425,161,-511,535,213,516,8,-831,1000,182,-496,-60,-1000,748,813,-379,66,1000,434,-479,717,-1000,-1000,-412,659,318,-122}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{126,125,-189,-119,-960,-169,1000,-1000,-693,618,1000,-935,91,922,-14,-141,-1000,-165,-469,-1000,-515,478,-664,381,725,-1000,45,-65,1000,1000,-777,74,14,881,-29,778,-595,-598,1000,926,-791,-194,525,-504,1000,-511,-187,628,835,226,-1000,-283,565,-68,198,52,-1000,445,1000,804,302,-631,-963,-580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{1000,-1000,-697,-741,0,-668,1000,-667,0,210,0,-98,-1000,188,-1000,566,-429,185,-143,-583,443,-1000,0,635,956,-598,-866,-906,-316,1000,-663,-670,-100,1000,0,-873,-352,-616,1000,847,-1000,1000,870,82,307,751,974,-844,-697,619,1000,-969,-314,-9,39,-586,1000,-954,-81,0,895,-1000,-725,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{107,1000,1000,837,1000,1000,520,895,-1000,88,-642,-1000,533,-513,-128,1000,-1000,157,226,16,-1000,1000,-1000,747,137,-1000,348,146,1000,-99,229,1000,-659,1000,-557,1000,-1000,-1000,-1000,-646,398,-400,909,-1000,-810,-338,783,-1000,-42,4,-1000,1000,714,957,-125,1000,-1000,1000,1000,-134,-1000,1000,770,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "mulAndCheck(long,long):long",
            new int[]{734,-787,-458,516,-804,-486,-911,-944,843,456,220,-157,896,868,-587,939,-629,-799,622,726,890,654,-654,402,316,916,-984,-2,-840,767,-958,-192,536,705,962,-484,275,-830,271,286,752,-60,709,616,-43,-131,-947,841,-605,-770,-188,-156,502,-375,567,732,452,691,218,47,450,623,-147,-594}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ3OTk5OTk5OEU5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{-82,-140,-391,74,-650,667,493,-62,199,346,884,-871,-545,898,506,397,-164,713,-662,812,-454,146,886,-202,945,-728,414,-517,803,123,784,607,991,907,377,272,372,-871,133,206,448,344,204,-112,868,-659,909,-128,283,963,-260,947,-735,481,19,424,977,460,-533,-280,119,410,423,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Double:NC45RS0zMjQ=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{288,344,-328,56,-721,-833,218,-375,-548,292,-690,732,586,-331,-712,202,445,603,-251,438,-564,491,-483,294,-643,793,-837,-11,229,378,-963,112,-633,-456,-414,-167,769,-545,-675,-210,699,-475,-748,815,867,-620,720,646,20,387,820,-907,-39,511,-806,-274,-997,-770,843,-895,693,617,987,800}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Double:LTIuMTQ3NDgzNjQ4MDAwMDAwNUU5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{296,328,334,-990,-62,190,187,-527,-392,400,775,-491,-741,-130,882,573,-851,215,426,-533,-737,52,-37,488,146,-176,279,249,831,-808,-805,-400,655,-799,-483,-817,706,-139,-350,499,-579,597,823,-946,-590,81,103,131,972,-629,394,-929,-33,-10,-496,537,-285,615,7,-819,415,365,104,-631}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Double:Mi4xNDc0ODM2NDY5OTk5OTk4RTk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{150,375,-974,-922,328,-469,537,-554,183,-288,970,-401,516,-997,339,823,-275,845,555,-199,-665,-552,352,-935,-860,-188,885,845,919,191,-554,-435,851,-972,-472,323,-315,822,-908,49,485,183,-728,717,353,400,-979,-13,892,267,-788,-56,399,612,574,-753,771,-2,-486,-102,-84,-583,342,-875}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Double:LTQuOUUtMzI0", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{-672,-864,-248,-260,618,-306,-420,-514,-252,-883,-98,163,-562,-334,842,189,-717,-727,-95,298,-138,32,-130,-288,91,-854,188,230,166,559,695,-103,-410,-341,-749,-55,720,-112,520,80,-980,908,-470,-132,-192,999,854,-39,-371,-176,336,229,621,-620,-906,449,221,977,-770,-822,410,436,-849,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "nextAfter(double,double):double",
            new int[]{-485,367,543,814,654,-623,883,-204,-1000,887,298,1000,1000,-20,-1000,928,-48,1000,-171,215,-447,484,-261,-111,-1000,507,-555,1000,243,281,-1000,-997,-1000,-254,-326,-110,-357,1,95,146,8,233,-1000,864,486,-622,973,1000,-47,484,965,-1000,-463,804,-255,-424,-441,-923,591,-410,1000,1000,813,1}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "normalizeAngle(double,double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(double,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(double,int):double",
            new int[]{255,-183,-814,303,-859,-349,-262,697,-514,908,613,-796,67,633,133,261,469,-502,301,751,-32,-548,-47,-945,-553,184,974,-104,585,501,927,932,419,744,859,-49,846,561,-989,-564,-391,528,-290,248,-114,388,-210,222,-275,-997,562,605,267,-475,-411,-599,932,-295,-735,-37,580,-162,587,-559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(double,int):double",
            new int[]{-656,-317,83,-266,89,-380,750,343,-148,-932,189,-918,-173,240,-739,-79,-216,456,364,-2,-231,-746,-120,-389,-684,406,-543,589,-518,452,-467,-420,873,-521,-67,-344,276,0,318,-425,-486,-6,-156,-998,702,450,415,55,216,54,-354,663,-636,100,-182,-637,-987,-889,-81,440,240,-794,-801,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(double,int,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Double:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(double,int,int):double",
            new int[]{-972,-339,968,-954,-59,-697,58,810,-447,154,936,357,386,98,177,319,-956,658,-409,-438,355,627,920,33,-690,-919,-545,69,936,-634,890,864,-475,-574,-934,-158,460,571,-892,-274,325,712,931,-726,-79,976,56,823,370,605,279,-921,570,99,-177,899,927,676,-670,-784,-489,-721,282,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(double,int,int):double",
            new int[]{-674,655,-702,313,-537,-612,-342,-986,811,-430,15,832,-270,-523,202,553,717,-645,117,-532,-840,-616,967,603,521,-495,-145,-102,168,-748,-914,-365,-679,-487,915,-630,436,898,-696,774,-250,-703,711,-124,-293,89,-62,-557,-528,490,617,-532,-115,552,-948,-896,-165,-552,822,731,257,841,-207,342}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Float:NzAuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int):float",
            new int[]{682,646,-794,677,1000,-671,450,-148,1000,252,319,438,-1000,-1000,1000,-1000,959,-1000,1000,-88,-1000,1000,1000,1000,427,-395,1000,-368,-385,-1000,1000,734,-1000,1000,1000,309,-411,792,-484,-228,932,255,1000,1000,303,-719,-311,1000,-1000,71,643,-992,-1000,-734,-1000,-260,-995,-513,-824,-102,-1000,-831,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int):float",
            new int[]{-7,-718,-881,814,354,-71,-608,-623,649,95,-15,-600,433,283,-818,685,598,-747,122,752,-766,-717,-617,907,940,-827,189,-610,-455,-880,-732,-977,-387,-409,-365,931,-217,-784,234,-30,-938,355,-704,-717,-497,-490,-493,-945,-655,988,-365,-24,-318,395,-843,-805,975,-612,-436,-397,-680,894,576,182}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int):float",
            new int[]{-131,871,-762,938,377,827,-754,-301,-713,-34,-99,491,-940,-951,-443,-555,-685,283,672,832,697,814,98,494,-421,132,696,712,893,623,99,518,806,-504,769,625,-347,188,-879,-802,561,6,416,-514,563,258,345,970,617,-611,-173,142,926,775,367,-719,246,726,-459,-330,404,961,-362,-436}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int):float",
            new int[]{371,-838,-696,896,163,506,-946,595,661,-294,351,705,-381,411,325,-310,825,199,-897,-331,-410,113,-815,-136,-543,-60,-770,788,370,868,44,567,953,207,852,59,234,719,-454,391,-780,-262,-439,-611,18,773,244,-7,-113,568,-840,207,-461,-323,-364,388,-688,561,-966,-250,-610,-87,727,-152}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Float:OS4yMjMzNzJFMTg=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{-281,-475,781,204,-688,-107,-587,-389,8,413,37,141,380,728,778,235,-18,1000,-1000,797,-61,-22,-92,-1000,-636,124,-677,-849,-77,-190,-129,-479,-1000,100,-449,1000,1000,187,1000,226,-413,-838,179,-245,-579,-1000,-665,-662,-24,798,-547,-199,629,-253,-1000,1000,656,1000,-1000,-185,-698,-433,-505,-176}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{-526,-608,-206,-351,-631,-737,-327,431,422,-591,-521,-376,864,-72,-570,821,794,-482,775,-59,482,916,956,351,-485,272,-693,242,927,743,-268,-25,-180,-160,-12,-638,-538,-178,-589,676,-642,793,252,528,-633,-941,128,732,-418,774,531,741,-604,935,814,326,949,389,626,787,-31,660,395,184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{161,343,-871,963,-945,546,-800,901,310,957,-725,545,537,724,1000,235,486,-869,138,331,662,-22,-239,319,564,-270,1000,-849,283,-857,530,672,1000,-339,999,437,-1000,305,1000,362,-122,1000,845,-776,46,668,-226,-902,666,798,-1000,-199,629,388,-958,1000,656,-242,-739,674,-234,-1000,840,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4wRTk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{728,-962,-94,-242,-377,510,379,-837,-705,710,-385,335,-562,-78,-783,-413,-241,778,-171,-530,684,-762,406,371,-403,-538,-643,-391,258,-342,15,-984,-534,214,-917,-752,-523,193,-42,-95,-813,-381,-901,886,-285,909,987,360,598,-756,-341,238,-408,726,254,-902,-234,776,581,-883,467,-660,332,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Float:LTUwLjA=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{-549,-926,1000,290,-758,-179,-1000,-352,133,245,650,784,872,538,799,-409,179,1000,-1000,1000,302,482,-358,-1000,-1000,807,-956,686,468,-662,-527,-1000,-1000,53,0,1000,1000,-75,1000,729,-1000,-724,-779,-246,-908,-1000,-1000,287,660,-743,-803,-1000,808,-216,-1000,97,995,1000,-1000,282,-880,-83,-826,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{132,924,-612,-48,-1,937,798,-864,191,-653,149,-304,6,256,-328,482,366,-478,-623,-579,-628,-887,892,938,647,-159,-398,40,-497,-81,948,424,364,-959,907,-754,-682,421,-82,-22,219,615,-205,-828,-701,832,576,888,-71,-77,620,40,-148,264,809,978,-15,-655,217,257,719,42,240,734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{-923,-339,-628,-273,249,439,-371,982,-376,776,-670,-661,268,905,891,-517,-49,-626,-267,-424,-346,-722,266,582,-199,-494,817,-301,-98,-653,516,692,702,60,301,147,-638,749,484,493,-255,288,571,21,-566,-121,-579,-955,771,366,-755,-121,887,414,-565,362,-502,276,-259,268,-965,-972,968,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Float:LUluZmluaXR5", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{-526,-279,630,-833,7,-649,-795,-51,208,-830,180,450,536,659,-1000,-600,-4,983,1000,-403,1000,-403,-717,726,-1000,-695,723,207,1000,-228,-120,-260,-307,1000,-140,-377,-538,-759,1000,676,-642,793,308,1000,308,1000,128,732,1000,563,123,-197,-1000,1000,190,-1000,1000,1000,626,-430,235,660,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "round(float,int,int):float",
            new int[]{-420,253,216,957,-500,-661,-946,915,218,-291,-358,411,33,-906,85,-429,-489,-935,198,967,-442,-529,579,-488,-300,-427,993,-969,21,-34,824,27,-401,643,-472,-572,-281,301,-750,909,-324,363,-935,-244,561,78,686,-814,47,548,864,-461,930,-451,244,-187,-96,-378,-64,641,927,-935,-282,-972}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Double:LTAuNQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "scalb(double,int):double",
            new int[]{-122,554,540,272,965,-265,605,753,-711,458,-482,166,-992,-743,753,-343,847,-373,949,-640,-910,242,115,953,488,807,-398,241,807,-739,-264,-678,-537,755,753,870,-27,563,-14,75,-227,-138,-869,862,-581,-799,-126,147,86,-614,135,-888,-127,-488,782,-270,-523,899,-237,100,493,-173,-674,-429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Double:SW5maW5pdHk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "scalb(double,int):double",
            new int[]{-942,-856,431,599,-72,112,319,-463,-418,588,273,268,454,-654,-279,-924,29,-20,8,-72,-335,-130,597,792,-77,677,398,-97,180,675,-819,-871,-972,-114,-218,509,-746,736,-896,-917,-964,-250,812,177,-616,-350,-942,495,865,155,569,-470,129,-232,-7,-891,897,-115,533,184,711,664,584,-583}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "scalb(double,int):double",
            new int[]{778,-893,209,46,-866,798,548,-576,-104,-351,566,669,910,-785,-883,-321,-113,34,580,809,115,-289,747,668,-871,333,-98,668,-903,-772,525,655,-604,763,-211,-66,125,-377,-665,25,174,-211,684,-469,845,311,92,-324,18,897,-356,-728,375,-687,-37,112,285,646,28,160,-370,-401,-725,265}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "scalb(double,int):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Byte:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(byte):byte",
            new int[]{-965,-457,622,-264,822,-965,79,181,478,-895,255,887,10,456,798,-611,-352,-885,812,-775,-66,549,-477,930,-440,258,-557,-207,-130,-17,735,-792,96,-233,-933,188,-96,963,-853,134,-363,-8,337,-676,-257,-818,-729,-249,-660,-710,-749,-112,-432,517,-670,806,484,701,-550,-396,592,-815,-180,-401}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Byte:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(byte):byte",
            new int[]{-213,401,731,889,-501,602,-505,-16,121,389,-173,-969,-588,652,73,-146,532,405,-13,-546,564,-389,-557,-21,-21,536,391,-796,336,-556,-318,-466,-290,508,950,237,824,-301,644,-827,-320,117,519,623,591,-272,428,-989,943,-202,-932,259,-209,-536,517,-655,408,792,847,194,300,-520,-143,487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Byte:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(byte):byte",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Double:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(double):double",
            new int[]{510,650,-604,-714,-916,-745,448,-195,531,-366,-856,-320,238,952,-733,-986,-834,148,-681,961,-36,967,126,408,-573,502,395,-899,174,-25,357,-36,21,-755,-361,-288,309,838,-514,-337,232,-833,-450,64,-941,69,-383,-760,87,761,813,-615,-865,905,-851,-80,440,-415,533,-999,-56,696,838,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Double:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(double):double",
            new int[]{95,483,-999,394,-424,-189,-291,440,-380,88,755,-261,-28,-264,828,-274,-33,-423,186,1,-340,588,-566,584,545,104,458,155,941,711,459,-878,-133,-131,990,-401,908,-428,911,247,410,262,-743,338,183,-358,-837,353,-178,-974,-815,-2,-251,-546,-366,562,-895,888,868,-746,768,543,-131,346}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Double:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(double):double",
            new int[]{25,595,739,-394,429,-537,-804,404,-800,435,-527,-983,-766,773,533,-369,-493,-883,-905,-15,637,-509,763,-434,266,990,725,99,466,254,-432,-525,-952,576,-305,562,-596,-166,-141,-793,-712,-725,-596,-899,-178,664,-51,-799,-67,269,445,-101,397,744,-179,-762,-249,95,-248,129,-602,599,474,-239}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Float:LTEuMA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(float):float",
            new int[]{169,117,-151,695,-176,81,649,908,671,977,787,-74,999,-61,-642,256,-877,475,-432,-755,-73,-614,209,771,610,876,-596,-61,523,-369,-436,174,-704,-346,394,918,334,730,-821,-7,-788,-468,545,-399,478,-404,-568,955,-35,-353,-892,-466,808,-908,712,-887,84,891,36,-134,-772,-522,-966,79}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Float:MS4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(float):float",
            new int[]{209,-271,882,165,936,-351,703,448,-195,-630,-56,-736,945,566,-424,172,-887,-120,540,298,24,787,-579,842,712,-345,-219,638,976,906,247,-680,190,-821,310,-600,728,-491,-812,450,-381,-348,70,-502,89,-97,-386,-264,210,-983,644,673,975,185,654,-995,847,384,-27,624,49,-90,-818,425}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Float:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(float):float",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Float:TmFO", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(float):float",
            new int[]{-239,-293,-767,-217,-43,-648,-694,-876,916,-21,309,-952,760,-64,-752,-620,110,-314,-676,-44,-762,-334,-275,685,844,846,388,-351,412,-686,736,-802,-601,-758,-697,811,-586,-318,-475,-669,-37,-256,-40,140,340,-32,748,786,-179,912,176,-332,-563,-647,-69,447,-716,-126,782,71,-409,-948,271,508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(int):int",
            new int[]{-485,976,889,955,630,12,560,-837,-493,250,-79,695,861,233,448,512,961,-549,-292,482,-99,-440,-670,301,-10,-373,-744,600,279,196,-788,815,-698,372,-837,-198,279,89,461,184,-786,275,379,-19,588,699,759,-777,-304,965,637,894,-957,334,854,854,695,818,-70,156,976,-625,739,-413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(int):int",
            new int[]{502,898,926,185,-833,202,395,981,421,208,701,297,-81,-844,-265,-456,493,-191,672,650,245,340,872,20,-460,-113,109,-776,-297,-409,350,-507,-725,342,-878,-456,856,-552,-526,266,750,-51,-514,-671,891,414,-640,-38,-110,-307,207,771,-617,361,-146,-222,-626,315,-805,-366,-267,378,-62,355}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(long):long",
            new int[]{-52,722,148,268,-442,534,-78,842,-794,824,-451,-192,839,-249,-252,-17,238,-328,-975,-576,534,515,329,355,-505,-501,-905,982,641,520,-169,-982,-983,230,-384,109,607,-665,419,-523,-210,973,-48,-880,-28,-840,958,-323,606,-768,-728,-335,-833,994,-217,-774,-668,-168,500,738,616,-561,343,695}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(long):long",
            new int[]{14,25,-30,108,820,-154,-1000,345,-272,-246,-686,-191,568,68,769,144,-424,551,631,766,964,-499,-704,-588,771,-948,469,171,-592,682,-142,948,-807,-409,664,-291,214,-860,672,-998,356,870,572,-773,-976,-261,314,927,-819,166,-264,964,674,245,-486,877,-498,-950,522,-73,690,-672,147,392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(long):long",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Short:LTE=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(short):short",
            new int[]{-848,329,767,-544,-775,734,336,739,-87,172,-403,-523,162,189,248,-823,305,-592,602,-303,963,-926,767,52,-234,33,-623,710,-179,-574,403,-549,476,973,209,689,53,-734,216,25,-30,249,-725,-197,-50,720,178,-596,850,-465,598,138,-934,51,211,700,426,-753,718,-604,33,-29,100,-418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Short:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(short):short",
            new int[]{-8,85,-339,-32,898,811,971,822,-202,-270,395,142,887,-837,-977,694,2,223,-988,242,-543,-689,996,623,-921,-627,-248,-228,-465,-75,-157,-575,696,-618,589,-888,897,-184,921,891,704,-4,-600,-733,-929,-763,-982,737,604,431,272,-499,80,-532,-573,-7,322,198,-100,-880,74,-950,-732,-645}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Short:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sign(short):short",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Double:MC4w", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "sinh(double):double",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(int,int):int",
            new int[]{579,-964,284,50,-402,-664,-649,-780,-982,778,-174,-254,445,971,-752,-574,460,-971,-9,351,-377,486,-433,-322,294,-103,-246,532,668,48,-21,-580,-338,-25,-731,-264,379,-412,857,-560,763,575,23,796,739,-496,-857,-850,-343,-467,68,203,169,9,917,933,821,357,202,426,-437,154,-237,-817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(int,int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(int,int):int",
            new int[]{-662,-385,-409,-64,-924,-391,14,-118,465,500,-149,-858,-280,765,-766,186,-649,448,-463,900,312,-544,-976,746,-924,-800,217,369,-652,579,248,639,428,-374,-632,-707,278,681,-470,857,-757,-127,484,320,79,921,-818,-855,-559,760,-528,-340,-684,354,-987,579,1,214,-842,495,-797,584,-866,-465}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODQ0MDk=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{-762,395,-282,-105,-561,223,589,186,963,-651,-96,84,-659,12,-172,529,668,135,272,77,956,169,736,-174,-4,-778,-955,360,-231,-324,-696,-43,-402,-163,-997,405,-95,531,-537,953,-297,53,585,159,-241,348,196,-312,-262,-78,221,-521,-413,-411,356,141,926,-90,968,-123,-763,-110,520,-146}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{-642,121,-828,55,-306,-568,-409,626,-1,-101,256,-335,-661,-423,-536,478,-467,-305,-253,335,687,-120,959,846,613,-753,3,697,645,-199,-522,682,-478,-105,160,-883,-341,888,-525,869,414,-381,-119,-516,742,803,-555,-813,-962,-944,-214,-108,879,-106,55,-210,-109,748,-384,559,-964,817,740,-200}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzQ3MDcyOTIxNjA=", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{561,231,-157,128,672,-995,-933,367,-107,959,181,670,-66,-790,113,429,12,96,-371,177,918,822,-609,746,-95,247,735,164,-812,-519,-328,483,-64,299,-687,871,359,759,-913,452,728,902,952,627,-7,872,-885,-101,13,-570,696,594,400,449,231,294,-580,678,975,-734,522,-146,504,758}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{-280,-212,-141,353,-787,-492,-647,-603,61,48,-715,252,112,546,-35,684,240,164,600,415,279,972,310,682,182,441,75,658,-454,293,-41,545,725,828,-365,248,481,203,467,720,353,619,140,290,740,-128,240,606,91,-677,-964,172,496,-38,228,-115,521,962,-570,459,-850,-112,-224,191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{672,-871,170,64,1000,1000,575,-129,-1000,-1000,-288,-440,-622,722,371,671,305,284,-837,916,68,479,296,-48,367,-147,575,1000,-324,-770,-1000,417,151,165,-1000,-661,1000,-113,415,-361,-334,1000,-319,699,218,-204,369,-1000,422,842,988,685,-459,133,-308,-34,711,1000,-668,-191,-164,1000,624,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNDcwNzI5MjE2MA==", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{-76,148,-747,693,-51,-697,435,651,169,-700,921,97,-86,-135,-372,219,552,10,432,262,-609,-31,326,-634,617,-205,459,752,-820,690,210,337,-301,-508,146,-974,127,-899,-98,73,-824,130,-172,142,-688,-464,577,-998,-481,593,734,159,-56,-786,-971,-867,79,322,749,-405,-847,727,-485,106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.apache.commons.math.util.MathUtils", "", "subAndCheck(long,long):long",
            new int[]{67,-157,-205,-159,387,402,197,315,-346,345,913,-91,-409,43,499,748,659,90,-396,-609,874,73,211,-333,749,-101,516,22,-136,556,-361,767,-389,482,-972,722,-569,-856,648,-49,36,860,-186,-500,991,-580,889,340,-642,-354,956,112,-930,-327,63,-340,-802,-566,671,-158,-664,600,-491,650}));
    }
}
