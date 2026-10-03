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
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{-426,496,969,376,476,206,594,413,648,-741,781,-248,871,-539,590,93,-20,-308,3,961,868,-156,-951,-811,564,447,-531,-805,884,-887,-974,308,449,921,-657,-824,737,-25,-389,179,292,-523,460,655,202,-112,814,762,525,-990,-340,-156,-527,139,306,269,844,854,941,-115,466,-549,67,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{-555,-93,588,-156,376,-42,-383,1000,189,725,-344,327,-601,41,-553,473,14,245,19,182,403,-311,385,-208,-202,67,-164,-123,-433,289,-245,-266,-1000,-578,-43,-1000,366,212,508,-454,186,629,-207,-328,520,43,-208,-34,1000,-387,-31,313,-323,-884,349,104,-831,-918,-138,-48,-608,42,-326,-166}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{-494,1000,-367,307,-12,721,-82,-167,646,583,-371,401,849,816,462,-787,-951,-480,278,56,283,-1000,-525,-1000,189,-1000,-40,322,-568,339,783,1000,547,288,-75,525,463,-167,-519,-2,1000,-19,384,-1000,385,533,263,-1000,-133,-326,-283,814,-342,-616,-440,-266,664,59,-957,803,1000,-578,699,-80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{878,222,224,391,28,176,-189,194,-260,-223,-743,378,915,439,990,-897,809,-37,-129,-373,-201,-364,640,-724,-250,-485,583,748,-482,-145,88,340,515,-346,-407,490,83,387,-143,-127,892,120,-578,-769,577,421,708,-912,-630,-269,240,568,653,213,-799,617,-1,468,-313,145,560,-28,-136,-212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{856,289,418,492,-763,636,-703,-711,-32,207,422,-438,-942,-170,234,-251,-450,-600,-252,-905,90,618,497,676,-495,-602,404,-198,19,-510,312,929,-935,-145,-268,-950,530,-594,806,-642,689,-113,421,-70,114,863,-29,134,391,49,333,555,-405,-451,303,295,-797,-26,-44,717,-681,-164,154,920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{953,-828,-191,-465,-553,-112,-613,-209,63,-999,615,234,-691,622,402,-57,-440,316,-439,680,248,-702,88,966,-4,-193,473,806,910,979,797,466,471,526,222,565,427,-366,884,-458,-545,624,173,539,-575,-871,-95,684,-395,794,16,-163,-677,873,27,956,-733,-571,-217,417,845,-629,-267,-134}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "centuryOfEra():org.joda.time.LocalDate$Property",
            new int[]{-494,224,-166,730,-227,643,170,22,646,-592,527,726,-8,635,668,-802,-951,216,278,-811,283,992,600,-819,79,619,-601,-841,-938,770,117,-908,103,-662,-75,206,982,392,-250,-31,-912,-224,384,663,-434,-564,263,-620,-133,851,624,333,790,-745,416,508,791,-274,523,803,597,-299,699,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{414,331,507,-869,363,392,953,131,45,-238,-935,-679,956,345,687,452,-22,453,-441,-224,-607,-29,-187,-968,-251,449,-940,-527,-535,689,539,19,401,766,-203,936,-143,154,-735,54,438,667,740,-521,65,214,-743,980,-945,-924,145,-195,-385,339,149,-453,-633,400,-583,854,605,56,-109,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{29,-27,141,-490,-429,-806,-1000,416,265,106,-560,487,-244,-346,24,1,503,-950,304,-245,-436,446,338,-504,94,-257,53,-1000,564,-992,481,-223,-330,-773,76,569,43,609,428,637,-22,5,370,-196,-604,-843,68,-233,-6,-346,-794,-108,237,-297,-696,363,-407,-148,1000,-397,-455,-692,227,33}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{561,-531,-394,692,-865,-115,-451,743,545,158,-54,867,751,868,-880,-276,-330,-628,797,-622,-886,773,685,478,745,-818,-21,520,-749,199,-397,648,-597,-61,80,785,773,641,-265,461,977,-56,630,-316,-930,214,-904,397,-608,-7,10,666,589,-411,-903,582,812,-909,-580,-957,153,-305,964,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{-738,135,-224,341,813,-806,-557,872,-53,839,883,988,-801,309,399,-759,503,-297,723,-245,-151,3,-604,997,904,-694,-765,858,497,84,875,-72,-754,-282,-590,145,792,248,13,606,-856,-684,-925,276,-577,614,-110,565,960,798,-794,-694,-866,789,-959,-475,-407,676,-876,-405,-404,990,-997,-163}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{-143,-97,617,-816,-846,381,-185,657,-851,758,369,383,-9,356,40,985,584,-204,885,581,-381,-716,577,-344,161,-977,835,128,569,-520,832,-642,891,590,-517,-859,85,753,-641,370,-559,448,652,-39,-634,-341,85,-108,361,956,967,769,454,-979,668,175,614,708,739,-726,729,-722,610,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "compareTo(org.joda.time.ReadablePartial):int",
            new int[]{903,455,1000,-1000,-364,373,1000,-708,-372,158,-1000,-933,418,896,667,808,924,-218,421,-209,-1000,-542,-247,-67,-218,146,-3,-557,-235,700,667,-388,-597,176,236,785,-398,-160,-1000,225,1000,369,974,249,54,775,-1000,1000,-697,-820,20,666,-335,388,834,-625,812,417,-323,1000,685,414,298,151}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{445,-1000,-246,-385,-771,-164,-792,-41,-94,178,-1000,174,-830,-560,624,1000,-983,858,-729,-539,-473,-1000,-924,133,976,162,-107,-1000,19,138,-76,438,863,519,-1000,-212,43,-955,-1000,-118,-353,980,-542,642,-342,-961,-864,524,1000,-191,-831,-476,885,1000,134,-372,447,-911,-553,-1000,130,-208,-194,848}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{-372,-742,-39,-354,-302,-230,-988,-66,729,986,-899,-905,-102,-1,425,-709,-997,284,962,65,-650,-814,-516,312,869,983,-36,984,-758,35,-92,342,695,664,-847,131,883,-38,-418,-44,-198,488,-84,360,-976,-54,-768,623,746,375,183,-855,59,123,41,409,720,-77,623,-988,219,-227,911,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{-983,547,-917,-463,-834,-766,-530,192,613,-832,-934,415,-810,773,233,665,-303,927,-252,-692,782,-681,-991,-98,-260,-634,-9,-528,528,951,-548,839,-804,-563,994,-334,674,-81,-529,794,-598,-549,902,633,-385,-853,-403,918,-691,434,493,-29,835,-460,848,749,908,-707,-604,444,-601,-260,800,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{-327,-469,793,796,322,371,123,-545,303,-456,-518,-742,426,303,963,36,370,683,-465,580,-886,-295,752,219,998,981,807,-610,-979,-195,789,86,-100,-991,22,661,-871,-663,321,-640,847,-91,-389,-819,640,-543,-48,-841,-400,-535,-748,47,927,63,169,-517,150,604,-435,-785,658,-790,-288,718}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{-39,-383,-604,540,148,-860,-110,788,653,668,-435,514,-389,-30,-499,358,987,440,-564,707,-675,-233,-444,-447,976,-565,-878,-621,671,367,-999,-793,-803,-749,292,-449,-314,541,58,-118,552,973,-71,454,474,507,-192,857,984,94,-725,495,-804,356,-627,-659,-989,369,-550,789,-269,70,-543,195}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfMonth():org.joda.time.LocalDate$Property",
            new int[]{260,388,1000,-1000,-767,-686,-1000,-827,-138,213,-102,-662,-1000,773,53,665,-1000,363,384,-241,782,-742,476,-359,-911,-1000,850,-611,-924,48,-162,970,1000,1000,-1000,-334,780,-923,-1000,1000,-1000,-1000,-443,42,-228,-896,532,-1000,-463,1000,-97,-1000,737,-245,84,484,-502,-426,-422,-1000,-24,-878,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{158,485,420,-742,-965,520,827,606,233,-975,-651,-472,768,-357,-904,71,731,-345,-287,-318,658,-469,-919,-237,-432,-29,-233,535,-14,-143,236,-863,46,-10,566,-564,847,-330,-629,-574,-354,248,955,-715,178,-387,-660,172,421,273,743,649,-538,-887,308,292,769,-722,477,446,-860,-343,-753,386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{-649,-115,-735,-602,-282,-919,-744,207,-689,-1,-848,28,-900,-612,-917,-128,-824,-540,159,-383,-50,-315,-987,-379,-675,-135,-276,-477,342,-396,352,-156,-411,711,983,-436,722,-693,-801,223,-123,-572,-825,314,448,-191,230,731,-253,148,-400,857,-634,669,432,-477,759,-920,-446,-499,-514,123,464,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{874,-579,714,-812,-715,-245,-148,-482,615,490,227,-764,563,-316,-492,207,-778,-638,-959,-925,25,623,504,-163,461,86,730,-950,775,-725,-868,-773,-336,946,867,572,-958,-418,852,-416,-637,-650,-935,-751,704,-374,180,-439,832,-99,507,927,-198,331,466,648,-671,-502,-621,116,74,-945,-783,-372}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{-1000,-97,798,536,1000,-1000,83,-163,-1000,1000,161,-1000,533,565,277,126,1000,776,1000,1000,-1000,-539,-461,1000,-918,-670,56,-720,-618,-32,-117,879,-1000,889,-286,325,1000,514,-529,975,1000,-895,-115,472,108,-1000,-910,-214,-60,567,-452,157,212,1000,767,909,-871,-758,-724,288,-277,-285,-1000,900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{-1000,399,576,461,360,1000,-739,133,-1000,436,-664,421,-616,-1000,-450,-688,1000,654,1000,35,-166,-820,-551,1000,1000,-891,751,1000,287,-795,-131,1000,-16,-611,-394,-517,767,-210,-1000,123,774,-262,74,666,-1000,-933,487,-156,-480,1000,-428,-1000,-1000,-106,1000,1000,870,161,-705,604,-482,-1000,809,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfWeek():org.joda.time.LocalDate$Property",
            new int[]{-291,-575,1000,19,1000,176,1000,189,0,-1000,-978,791,461,-1000,253,-895,1000,-1000,-226,1000,-1000,-1000,-809,320,382,-622,306,1000,-1000,682,1000,-1000,263,-1000,362,428,33,-55,-1000,-1000,1000,44,712,1000,1000,-944,-858,-1000,134,956,545,-827,-1000,-1000,-152,720,-1000,1000,1000,1000,-1000,-1000,246,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{544,-131,855,-767,-200,1000,-167,829,-697,-126,792,-709,348,-35,1000,1000,-619,542,-292,1000,314,-1000,415,1000,-943,627,-1000,-991,-1000,211,1000,1000,867,1000,30,711,-520,1000,-136,-270,-622,369,133,-282,-746,-618,-159,198,-1000,-380,-220,76,-198,-633,808,247,683,-40,-965,-1000,551,1000,-376,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{263,611,960,748,-107,-224,464,399,-894,385,826,668,-53,245,-254,40,677,-10,122,-186,870,-526,-948,376,662,342,167,-172,441,-448,232,-610,832,-771,286,550,303,-777,-345,779,995,10,133,-150,424,-847,-685,-743,-403,461,621,-934,786,-822,921,-384,719,-940,808,-32,-755,67,-443,692}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{132,1000,-403,-11,-889,601,60,265,-511,641,10,446,-189,-384,-446,619,-360,63,-232,-245,818,-784,-253,376,639,556,1000,-536,-293,17,461,533,399,-317,377,519,456,-934,-47,-223,-259,168,319,-440,523,-456,210,-511,-157,255,-319,-486,685,-1000,222,357,1000,-188,405,-1000,-87,344,-642,2}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{-379,-971,316,646,-71,132,93,-375,152,195,-740,-153,879,151,754,976,93,-541,-978,903,160,281,-356,328,-221,448,-346,297,89,-298,-8,324,781,-677,-196,282,532,-785,-587,121,283,823,709,-350,24,813,412,106,-433,-629,-766,57,-458,167,293,297,320,575,-693,-197,-93,574,-914,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{997,883,-392,-505,286,-605,586,-340,-40,-313,-145,494,-222,530,104,-808,628,679,556,-423,558,967,-960,-60,359,-270,-452,-296,-450,-56,875,941,-714,-907,-789,-668,118,-238,-325,-952,-151,212,675,-836,-265,-209,728,-218,232,-68,-99,-233,124,-297,-603,-810,-559,-605,963,-41,832,478,796,-129}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{-701,112,639,647,-757,-642,-539,990,315,-825,547,-912,-193,150,-349,818,862,-587,412,-826,867,447,467,-647,150,847,444,-734,829,121,-114,706,287,-741,448,3,190,-203,-186,-177,234,345,-287,3,-134,-845,106,745,-876,192,-804,360,-575,-595,-755,-179,650,-615,-654,-147,190,476,291,-984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "dayOfYear():org.joda.time.LocalDate$Property",
            new int[]{374,-139,948,-376,481,848,96,432,-633,755,862,-412,-813,-900,786,542,-175,430,-300,843,-70,-971,-412,999,-387,713,-493,-804,-708,-566,222,771,-227,50,-444,871,-887,614,503,-401,71,-239,118,-865,29,-586,-122,-183,-861,-115,-204,966,160,-311,881,359,953,-99,-253,-305,-76,726,-904,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{502,276,-78,-510,167,153,183,-951,286,237,-376,603,-995,-954,-794,491,368,212,-114,-290,-679,-423,-313,-402,744,22,-93,-952,666,-418,912,197,-114,-277,210,-799,-942,-738,-560,781,-24,-664,804,-476,272,102,-946,-401,27,360,609,-515,898,-791,656,-380,-716,-16,119,430,779,-751,-683,126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{666,-104,-291,485,858,1000,209,0,522,-698,-1000,-63,0,-79,209,-1000,-433,-632,-627,1000,1000,-102,197,786,-218,-619,1000,-94,-570,782,998,-1000,431,-534,-904,447,-232,-123,158,-828,-1000,-353,1000,-59,-930,866,304,148,1000,-449,1000,860,-1000,-980,-529,-822,1000,543,-11,-1000,815,-661,-449,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{-655,679,-994,-455,777,648,290,-404,-26,731,-239,493,55,572,602,494,681,-333,702,-843,-914,-833,347,790,-386,-746,459,87,507,6,-87,715,-231,-57,-279,332,-244,103,817,534,822,389,-941,-408,-179,-793,-343,-553,-435,944,-304,448,440,721,-303,-462,-719,36,-796,228,-630,394,-705,-435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{34,-493,577,320,-968,-904,-45,600,-894,-927,-858,549,764,504,146,-315,-162,-640,48,-715,-569,213,-504,132,374,-276,101,383,-591,-941,-746,-926,399,-361,-888,437,665,-705,453,262,271,-213,-2,-136,513,-73,-640,-130,-817,-935,-728,997,274,-391,0,277,714,398,-105,756,430,-752,958,626}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{-286,-590,-223,-155,910,634,221,262,975,198,-961,624,-579,-870,-283,297,-314,-945,-392,406,737,552,228,905,-632,-27,807,498,-494,704,213,-745,-100,-866,-424,447,-580,-775,936,-812,-158,500,64,773,-496,524,251,-721,-525,496,289,934,-999,-557,139,-928,919,700,-143,26,426,-171,-783,271}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "equals(java.lang.Object):boolean",
            new int[]{943,25,0,485,-706,916,851,0,-974,-1000,0,-263,0,650,-135,0,594,-830,1000,-438,0,-102,44,655,287,-1000,917,-3,59,273,0,-680,-274,1000,-904,1000,-339,982,-72,-1000,-1000,-353,612,-1000,-846,-224,-882,455,1000,-585,0,-1000,-1000,54,-529,-1000,313,451,337,-1000,-17,655,-449,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{58,947,-750,-258,-112,518,-91,-187,-664,-947,-95,418,537,-856,216,-635,-610,859,165,-438,979,-709,846,-986,-12,-927,-180,-595,-99,811,-865,239,946,728,375,652,380,-806,665,-843,0,269,335,843,573,926,316,-169,-771,-827,-787,153,-145,949,510,654,-39,381,-649,-997,612,739,-9,419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{286,160,-870,-19,629,-637,1000,-1000,276,-813,-283,582,-1000,-456,827,-535,1000,573,1000,-378,1000,-863,623,-394,666,758,1000,1000,774,-748,-311,-56,-141,-271,-25,-122,-1000,-267,-674,1000,-399,-1000,-1000,669,-1000,842,1000,112,-1000,331,-42,244,-878,-729,540,-198,705,-1000,-147,-1000,1000,135,-671,-589}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{686,1000,88,-314,-1000,-983,1000,-755,-1000,1000,1000,79,-330,-1000,119,286,919,609,-77,238,-72,37,-1000,120,-619,576,1000,1000,-473,-182,-578,295,-75,1000,1000,640,-759,287,1000,1000,41,-464,-1000,208,295,-995,1000,-175,-1000,-1000,1000,-561,443,-1000,-1000,-785,1000,-732,1000,-873,483,-956,-169,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{436,-300,-582,983,35,693,235,819,-172,-781,413,323,550,-200,21,843,-575,825,-792,998,-640,-833,24,478,-447,-899,-819,-630,-430,-554,-461,462,13,188,715,93,-364,-769,485,296,1000,-628,385,-795,561,-867,-687,12,496,640,-206,-897,473,-605,-936,-558,93,487,-826,-242,804,-436,882,-386}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{190,-931,-752,519,-266,447,275,-908,95,-657,-881,709,954,891,-854,175,-875,-188,-586,594,-977,-434,929,795,645,-174,125,-360,760,135,-475,-918,-905,-122,-875,949,-968,-859,339,600,-449,-535,-933,-166,-6,9,-178,-250,-423,-757,769,-419,-457,913,-35,-10,-679,-271,155,778,-837,-994,-929,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{375,827,553,-436,-626,-986,-861,-540,-462,137,-267,-951,-934,790,-731,217,441,-881,-899,-94,178,171,-637,498,288,515,618,-383,338,666,897,912,-888,155,-70,476,533,482,516,-273,-192,-343,-162,850,521,543,830,-862,-168,-217,-382,-9,814,759,-457,721,526,727,-715,795,-558,-977,231,721}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "era():org.joda.time.LocalDate$Property",
            new int[]{315,223,733,-307,-164,314,717,-424,932,-691,116,242,-660,647,-44,-714,702,-417,-653,1000,417,-121,-187,1000,1000,-160,-814,-475,176,119,-131,-539,416,-7,545,528,8,-521,883,273,-133,417,883,-879,-127,-134,-138,-531,-1000,-740,809,-127,195,176,690,-784,522,-962,98,-586,1000,598,161,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "fromCalendarFields(java.util.Calendar):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "fromDateFields(java.util.Date):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{872,826,-588,-215,477,-879,858,387,952,385,988,-57,-227,775,301,-863,-92,-125,-183,-6,488,74,89,48,-919,-785,951,670,-100,991,-817,-854,-56,894,40,883,-582,217,-845,-806,76,621,-263,-467,-894,-949,143,753,-919,-562,955,-32,141,-464,471,-10,-954,482,-792,890,-946,-745,-605,261}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{-1000,-126,-834,294,1000,-4,134,-209,-450,-692,1000,-1000,131,-36,-510,-689,452,49,653,170,-55,-1000,720,10,-268,467,311,430,140,276,-68,-345,601,169,167,711,-180,-480,-658,445,-255,-369,422,343,-1000,388,1000,-368,95,-908,-935,-442,-1000,856,-600,-1000,1000,-1000,-317,321,311,1000,-211,18}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{641,659,-389,890,-86,-599,240,961,-450,506,900,216,131,-237,755,602,759,-330,-400,-202,-45,-379,720,932,-268,-252,400,-745,-860,993,-305,42,125,423,-203,-265,10,417,-78,445,-898,-606,46,343,985,38,-24,-845,910,594,-725,-977,351,-252,-600,327,-702,-147,-8,-295,112,794,-222,-516}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{-878,344,-216,551,240,787,-546,-539,-77,-871,444,-79,460,55,-404,-682,489,758,291,-518,127,-677,-25,506,-519,-336,721,121,197,4,-195,-715,286,251,-973,368,-120,-703,-943,424,-178,274,517,460,19,98,659,291,333,-747,-459,-389,-531,710,417,-739,796,-910,-326,296,304,783,135,449}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{-962,552,-235,-170,238,381,-91,328,158,-219,-906,34,-922,-399,71,-733,-640,-502,-647,272,589,653,-239,517,-199,64,-326,-344,-402,815,-399,701,551,62,-551,702,710,-812,460,-662,269,562,144,-776,-529,638,758,-569,696,-257,-143,-232,-660,875,-679,-313,567,-986,826,357,315,-945,-762,-633}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{-444,873,-307,469,825,962,901,-125,120,-896,467,644,-506,-268,-159,619,-635,-489,280,609,61,-450,952,-637,50,-640,950,-999,913,200,-879,678,706,803,312,-930,-220,-807,-740,-714,356,255,-293,-697,-805,69,533,637,-452,605,697,80,252,319,619,-409,300,-549,-759,146,520,-659,-515,257}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "get(org.joda.time.DateTimeFieldType):int",
            new int[]{78,-993,59,-802,473,-656,646,521,-619,-144,405,-633,-729,608,384,-698,-937,-799,984,886,-741,-972,-83,-915,-31,792,235,-266,-976,335,-25,749,3,186,695,-450,-626,-67,-228,256,227,-498,702,596,-926,-257,853,-782,77,338,-876,-933,-294,-17,778,-108,327,19,-627,530,-500,293,136,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjA=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getCenturyOfEra():int",
            new int[]{-345,-549,-108,1000,-755,599,1000,1000,1000,-413,-770,230,-602,-868,970,-455,1000,-875,-1000,944,1000,-723,-466,-508,384,792,435,-1000,1000,41,-1000,-1000,-785,315,746,564,-792,-395,1000,-674,776,96,683,369,-244,925,387,334,-272,94,-628,311,-396,-740,1000,-304,-1000,-1000,1000,1000,-860,550,-1000,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjA=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getCenturyOfEra():int",
            new int[]{971,-357,-612,273,-1000,918,-94,565,915,992,736,-1000,-529,-1000,-123,425,153,-188,60,1000,-590,-1000,807,269,-1000,1000,-1000,1000,78,36,757,-455,-237,1000,209,1000,-249,-107,495,245,944,145,-655,-1000,-1000,161,154,604,-901,1000,-1000,-786,-158,-1000,-84,-907,-595,-862,-1000,1000,-530,-80,-1000,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjA=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getCenturyOfEra():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjA=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getCenturyOfEra():int",
            new int[]{268,-172,-997,233,-983,533,537,728,-422,314,148,-915,-731,-627,969,489,3,796,-185,874,246,-924,100,329,-831,703,-540,676,756,-599,-728,88,46,881,80,583,-567,144,86,58,-2,286,-662,-903,-748,-445,31,213,288,933,-610,-573,403,-50,45,-906,-506,-401,-508,790,210,244,-718,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjA=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getCenturyOfEra():int",
            new int[]{547,-415,241,393,-35,-837,-409,-13,-810,-795,16,966,780,-687,-45,461,-106,-740,-649,532,584,576,-28,-491,-694,636,-660,439,-591,-920,-19,-715,-157,-249,-454,-483,14,437,-511,515,825,-320,476,809,-730,641,-558,-394,649,626,340,-735,475,-783,-17,-549,-586,187,134,504,-727,359,995,-703}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getCenturyOfEra():int",
            new int[]{-162,239,813,1000,92,-223,1000,654,85,-387,704,346,-66,1000,860,339,965,1000,-1000,1000,-1000,398,607,470,52,-1000,672,594,1000,-742,-49,1000,-1000,-589,1000,293,-1000,314,-1000,276,-82,-453,-231,-472,15,236,848,-422,456,661,369,-275,-222,910,226,-178,-346,393,1000,-1000,274,251,1000,-955}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{-9,837,246,-995,50,1000,-506,692,382,379,-159,171,-762,312,-315,635,-352,411,-820,-1000,1000,-403,262,-1000,678,-179,-331,-167,-1000,953,-21,680,-1000,-522,538,-1000,-761,933,-78,-29,1000,422,-836,190,-617,-608,-717,1000,-135,454,-70,-653,458,369,-528,-1000,1000,462,1000,-449,-668,-365,-224,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{873,490,648,-983,980,-685,-275,-374,595,-23,-804,-396,-469,105,626,-49,338,874,-442,391,-803,-222,-211,868,-40,-673,-115,50,202,148,-475,-165,-53,409,983,403,-182,-299,-5,-253,-275,570,613,817,-21,716,259,417,-308,-419,81,820,682,490,28,-301,763,-180,-443,417,235,-225,-222,989}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{386,580,323,892,-376,-24,-399,627,628,40,782,-933,321,563,168,-170,935,-620,-250,-388,633,-234,587,575,-335,-611,481,940,-958,-838,-830,113,-535,740,896,-767,-641,664,231,423,235,-846,331,220,-669,452,-570,666,-486,-756,-724,-632,741,191,516,-523,53,887,526,-43,27,650,-888,-181}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{246,-304,-9,-596,-411,-316,427,-556,648,-230,-492,488,-993,570,552,-283,-878,184,-305,913,27,-150,345,-512,-28,-424,649,-49,599,717,806,773,19,883,-290,162,-743,-222,442,654,-70,639,-613,623,713,749,424,814,218,222,-476,-168,197,696,367,165,-322,869,123,-264,-434,-72,426,-719}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{592,-811,-983,772,162,-372,-560,755,-315,-145,-662,967,1000,606,-94,210,367,248,-226,397,414,-520,-569,-309,959,-152,437,-1000,386,340,-53,-536,92,269,-1000,-662,-571,743,-525,1000,-1000,-4,166,457,-352,183,-160,-403,-533,1000,854,-916,492,62,954,252,494,51,481,89,-510,-474,-1000,115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getChronology():org.joda.time.Chronology",
            new int[]{-452,519,-9,-1000,347,835,-746,692,479,288,-1000,862,-762,531,34,779,-805,547,-55,-564,961,-216,345,-800,1000,165,-653,-397,-71,392,806,773,-1000,-1000,-288,-1000,441,57,-339,-76,724,1000,-1000,623,-369,-1000,-308,814,476,487,526,-329,275,119,-909,-722,692,110,1000,-993,-1000,-1000,205,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjc=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{630,34,-564,129,-646,493,-337,370,-603,389,-461,-322,-404,58,364,-393,498,1000,-220,195,712,628,819,-233,679,1000,-240,-269,-534,-449,520,-262,-618,948,763,80,1000,88,-710,513,450,1000,1000,-190,-514,-710,-1000,826,1000,225,407,968,-651,-593,-152,1000,-1000,-995,480,-208,-391,636,1000,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjc=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{1000,-38,-948,-327,-172,-443,230,-265,-1000,399,699,439,-791,1000,-100,-504,1000,1000,-203,1000,1000,1000,1000,-630,236,1000,-262,-516,-673,-553,1000,-881,-230,475,513,1000,557,-242,-494,1000,1000,1000,1000,-1000,-308,-412,-1000,984,850,1000,1000,632,-445,-454,-1000,485,-1000,-418,977,-227,-255,718,976,-654}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjc=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjc=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{201,554,671,-171,-194,164,-389,400,-106,-57,412,307,-45,540,80,678,-891,-312,599,948,-296,-584,-587,300,-757,-982,135,725,551,-141,514,468,743,-958,-516,-51,-929,-836,-225,-109,-23,-999,-988,-611,809,777,472,-133,-436,-99,756,-479,746,501,-480,-297,989,950,89,-65,411,-365,-698,-243}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTc=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{-759,145,75,152,-16,-728,220,989,534,-432,-12,721,-385,-778,-166,-848,-261,579,751,585,580,276,622,-451,618,786,110,537,-574,689,-931,-761,659,870,284,-931,258,-851,987,-537,-316,342,27,-645,100,677,-143,557,344,626,-452,-701,-764,741,-54,149,-654,871,769,692,659,253,-45,-92}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTY=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{-81,745,-161,489,33,-106,984,-396,838,-518,-741,221,911,33,454,-337,-400,640,-126,661,-903,-243,-976,394,-288,616,585,-941,-648,193,-742,-927,-143,562,-54,347,-632,765,300,-276,-857,-184,183,-391,-618,-607,-206,153,-502,-92,-679,867,50,156,-159,-497,38,555,-97,225,660,-908,723,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjc=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfMonth():int",
            new int[]{0,-118,180,261,1000,-1000,103,-559,603,-997,719,743,152,-413,-1000,-440,-378,-1000,335,-671,132,0,-46,-680,-544,-1000,93,42,489,590,-403,-277,1000,-275,-1000,-676,-472,-120,725,-805,-1000,-1000,-564,41,0,1000,1000,-555,-1000,228,-900,-620,220,-438,216,-607,608,153,-467,425,633,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfWeek():int",
            new int[]{-169,-1000,285,578,-89,37,-1000,-196,623,-94,-894,504,-1000,469,-275,401,954,-1000,1000,244,-90,499,364,444,464,226,1000,532,-371,-360,-171,-408,294,-500,4,-282,-6,790,161,239,-230,1000,-69,1000,1000,515,-731,396,1000,881,1000,-307,-393,-313,65,258,1000,252,288,351,580,41,-176,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfWeek():int",
            new int[]{-49,-434,-381,520,537,235,-54,-679,-350,330,-138,916,-734,214,751,353,907,-503,445,-521,-188,951,460,651,480,-253,837,710,-345,-555,380,-235,890,-890,597,961,216,686,399,-234,-913,336,-304,606,796,-162,-553,658,-157,326,-577,-480,378,666,970,-912,-254,601,393,491,431,409,-760,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfWeek():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfWeek():int",
            new int[]{920,85,-306,-106,-290,291,-92,-431,-815,-389,-986,262,-700,-797,-118,-815,-521,814,158,720,-554,-218,-247,-154,-9,-166,-605,-147,-222,424,568,635,-619,-779,688,531,77,202,-921,37,611,-686,-559,-21,166,-914,897,-228,44,752,384,-782,206,-525,244,4,-264,-935,134,499,566,-768,86,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfWeek():int",
            new int[]{-695,850,-527,-187,941,984,933,962,326,-509,874,80,863,889,-895,769,635,908,-676,-861,289,606,-252,-152,487,305,979,-890,-584,691,384,156,949,-600,687,250,-604,156,-706,739,-530,110,-80,905,104,-599,-488,361,24,-832,-840,-354,-357,256,-270,303,-248,649,-732,204,723,-639,-103,-793}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("java.lang.Integer:Nw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfWeek():int",
            new int[]{397,-689,-116,-198,821,207,-183,186,295,867,-341,-465,-35,-219,-294,718,519,237,-290,894,75,617,603,-127,-800,-4,-78,449,-410,-330,725,-32,41,-779,-729,-990,-332,-542,462,457,41,177,186,481,896,29,77,-10,359,-986,907,959,291,-884,-119,708,236,-582,414,691,-252,669,654,636}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjcw", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfYear():int",
            new int[]{-742,-1000,-318,123,520,-466,466,-467,-173,-579,-877,821,640,-30,1000,-112,-118,107,-1000,153,-1000,301,-381,-609,-557,-68,1000,-517,168,-1000,483,-591,1000,-722,-274,722,-108,45,-490,793,380,-680,649,231,887,277,341,-497,-557,158,400,728,-606,546,-8,88,-325,1000,39,-858,-666,400,-206,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjcw", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfYear():int",
            new int[]{95,655,135,1000,-902,-1000,306,-1000,-241,-590,-18,242,-894,748,688,45,1000,251,-1000,515,-256,-776,846,-151,-519,-1000,495,105,865,-1000,-484,796,-456,191,937,572,-1000,-660,-149,729,-962,379,297,-665,-154,-12,-821,-92,617,543,-978,-1000,-242,-96,-909,-518,235,-896,-35,-1000,-639,-274,339,250}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjcw", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfYear():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjcw", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfYear():int",
            new int[]{461,631,335,427,276,691,474,693,643,891,898,905,661,926,529,-170,-90,-804,-962,957,808,-254,604,-839,-211,614,402,-430,-283,-451,-80,-56,977,629,907,423,-363,-774,312,238,-297,961,-88,169,-951,831,518,-92,904,-474,-410,-735,903,-905,917,-803,-305,-411,-395,-991,812,91,-963,-230}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("java.lang.Integer:MzQx", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfYear():int",
            new int[]{-893,-377,685,952,451,-185,724,-344,563,-53,-790,-655,-135,-3,294,-4,84,652,-608,-214,934,914,-473,3,-309,-128,178,-265,704,-499,-135,-930,-24,-67,450,-946,-175,-456,650,-585,333,-584,642,-756,-191,-745,766,-807,-196,-315,-634,537,-652,1,-470,487,45,898,160,506,405,497,-818,-918}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mjcw", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getDayOfYear():int",
            new int[]{-255,278,35,-49,-337,-1000,766,-131,-191,-342,251,1000,-189,-587,-289,559,153,311,684,62,-1000,72,-21,-811,943,-492,-558,-732,489,-770,599,114,-556,915,190,1000,-29,-204,62,995,593,-151,-236,-522,-687,672,370,-541,799,-851,1000,1000,259,290,-561,-852,672,513,-960,676,245,-429,-441,124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{-1000,-1000,705,-1000,689,-990,-1000,608,62,468,195,-302,214,-472,-44,-1000,121,1000,1000,-6,1000,-518,1000,-1000,-1000,-1000,-700,-549,-460,123,693,-948,-889,1000,344,-1000,-392,-334,708,443,-1000,-856,-203,-1000,-909,-371,-213,142,95,-426,1000,-652,-705,-27,-92,-262,-1000,1000,813,666,1000,386,1000,-223}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{-34,-137,-240,190,986,-1000,-1000,576,-310,-420,312,105,-50,-410,346,-385,-5,840,1000,542,451,-305,201,208,-876,-167,-847,-380,-908,-995,-463,-451,1,2,575,145,521,133,398,-178,62,-396,-627,85,-287,-261,223,-756,287,-899,482,269,-1000,-394,-907,-696,-159,802,301,138,46,239,296,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{-929,-850,566,-457,-353,-145,-783,344,894,-526,366,83,304,-205,-797,-137,-728,-754,980,555,958,-600,-534,698,-908,-445,-952,458,-549,776,279,-950,796,293,-243,-216,-351,-287,-144,-292,-569,82,-7,-249,-766,-479,901,186,759,924,653,-719,-211,-55,105,58,-979,-570,-184,-745,-9,-688,548,-249}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{741,519,-664,850,218,273,426,-193,-72,278,181,606,284,-548,178,698,163,-890,-460,575,-910,-764,404,-169,-893,357,248,45,495,374,320,841,592,-828,491,191,490,386,-515,911,617,-675,-380,155,870,707,951,-309,289,-399,-987,-257,-911,-202,-748,281,283,-354,-335,763,-907,346,-843,556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{280,-425,80,433,-32,-914,-257,-950,-712,1000,-571,696,-432,-213,1000,-562,-419,574,-270,-44,-1000,470,1000,-1000,-1000,547,182,-655,-391,1000,1000,747,456,-986,936,476,298,-1000,-1000,897,969,-404,466,-1000,429,1000,632,476,-171,332,-640,1000,-1000,131,99,1000,572,617,227,1000,-1000,825,-924,415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getEra():int",
            new int[]{798,-657,-51,1000,-541,-197,440,-1000,184,623,-510,320,-654,161,676,-427,-745,-282,-844,77,-1000,971,196,-297,-255,720,744,-626,745,58,795,340,78,-1000,830,883,129,-847,-698,423,784,318,597,-265,338,635,-50,538,-87,732,-447,14,-25,-48,768,716,1000,-220,-89,163,-558,839,-816,90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{448,100,-990,-422,564,644,-516,-757,-524,-648,-544,-72,497,-440,373,-259,638,-192,434,-695,345,788,-467,151,916,748,16,-692,-65,726,276,-144,138,586,-68,-301,-3,744,-115,632,-979,670,-104,891,-728,-231,494,794,912,-891,-994,856,-251,-405,-714,777,687,-355,-673,111,732,-782,356,-927}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{146,534,-393,-1000,671,-663,742,-1000,-547,269,-928,210,1000,-152,-587,-567,643,-1000,-559,696,1000,1000,-380,-952,272,92,-40,680,-789,70,-324,-192,-179,-798,901,-961,-883,1000,-142,-44,-1000,173,-1000,1000,377,-375,-311,256,875,-1000,-643,734,-279,131,580,1000,-813,-233,-1000,-377,827,-1000,658,-652}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTI=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{-532,-64,238,-250,977,-197,647,-481,-815,367,-158,514,948,-656,-210,352,914,-253,-780,559,939,-625,671,70,169,-535,978,174,180,-280,-161,9,-290,-465,-229,-766,-646,152,379,-737,309,-332,145,624,772,350,155,-628,896,-435,150,289,361,-235,60,631,-437,-562,-188,-460,172,-298,607,951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{-607,333,66,-196,650,379,-261,659,-402,262,265,472,389,274,-817,-822,942,710,851,-994,-411,-453,-786,-15,926,533,299,980,-40,-191,178,773,704,808,-67,207,275,-953,-373,209,217,654,713,-377,-675,989,408,-766,518,577,598,91,486,685,-422,-706,760,374,-412,792,621,612,470,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{-170,-872,38,-645,563,81,781,579,-898,-652,357,407,884,-766,35,221,-715,-224,686,-305,-807,-571,249,388,845,-81,-341,-549,-325,294,188,-634,-22,-180,-154,-847,112,238,29,-761,606,-863,-14,366,107,-282,-197,291,-623,42,605,806,723,-959,-532,-344,-855,996,121,-700,298,993,951,-865}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getMonthOfYear():int",
            new int[]{97,411,444,374,329,977,-813,-19,-941,88,-437,-287,-684,125,-375,-2,-725,846,-772,-531,-865,-471,-380,725,-863,317,581,203,374,-781,974,526,159,-137,-688,-830,-406,-46,472,131,657,-374,-963,-645,-329,401,925,164,-690,-710,947,-385,445,741,698,818,80,-410,824,-404,392,959,102,549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{1000,-1000,414,-488,27,-269,1000,-429,-1000,336,341,60,-840,-259,-214,-433,-186,-1000,-1000,240,697,-1000,-55,-164,1000,-354,449,892,-1000,396,225,1000,682,839,62,1000,801,-364,-1000,-1000,-799,-1000,-476,-1000,1000,63,-788,-25,-1000,1000,1000,-1000,193,123,-947,-919,872,-860,-928,-698,579,-380,-1000,-361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("THROW:java.lang.IndexOutOfBoundsException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{-113,-786,429,-502,726,13,15,-591,661,-345,-1000,-766,336,811,-56,-37,0,-697,-924,-235,-507,-1000,439,-1000,1000,-854,-32,1000,-630,506,-236,942,0,253,-49,106,352,-380,-947,-1000,-20,-1000,320,0,1000,-694,195,538,-955,11,536,-315,-699,0,-711,-765,420,-23,0,1000,-40,-1000,0,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{304,-965,-954,-286,25,597,-522,352,-709,-588,-493,-9,830,885,997,-533,-174,695,-458,-113,166,-42,351,-591,79,305,-170,-667,620,-361,36,-918,117,-229,-541,-142,944,-443,320,-10,-175,-311,-46,-742,-784,-121,-259,897,-717,-205,-489,337,-520,-154,407,-701,-211,650,756,338,330,866,-324,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk3MA==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{-492,800,-820,-407,-425,538,-881,900,-530,358,492,453,-572,841,-756,923,273,-662,-969,-568,105,904,157,-216,638,-424,-851,248,-720,-303,-669,-65,-269,360,-38,-79,138,-479,-666,-455,-368,-169,-489,-407,-507,-167,-641,448,77,-204,-63,704,-801,-818,379,224,-339,481,1000,-807,-682,22,-520,-707}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("java.lang.Integer:OA==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{195,211,-246,836,-28,-355,-539,405,-574,-460,707,-645,-657,-995,256,405,-203,-167,-724,-562,-249,-316,-742,665,-546,803,-131,-700,263,-245,110,-943,196,24,407,-87,778,-182,36,849,-445,-90,-393,546,-969,933,283,417,711,190,-157,739,462,749,740,151,426,360,921,-645,-605,547,307,-577}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("java.lang.Integer:OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getValue(int):int",
            new int[]{304,774,52,1000,1000,-359,170,360,394,-777,44,-1000,215,885,-170,44,-818,1000,-179,-353,-764,-738,-1000,-51,-309,671,-1000,5,1000,2,438,-1000,654,-229,516,-1000,1000,-443,476,6,419,-350,-46,1000,-879,735,1000,611,219,-644,-1000,583,-919,442,1000,-845,-211,650,-13,899,-508,312,731,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzk=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{-333,-1000,930,-291,113,-539,320,-300,-1000,-128,-401,870,-859,294,-1000,-226,-605,-479,-1000,195,-1000,-274,-490,1000,336,791,-202,715,144,-1000,-781,-347,161,806,658,-449,-678,-1000,-421,541,-373,998,-588,1000,-1000,1000,657,-1000,-230,646,-551,56,1000,1000,-644,727,607,-873,140,-768,921,1000,-898,-567}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzk=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{-992,600,675,515,1,208,239,-415,-88,-603,120,238,-1000,-456,400,-162,560,-549,-624,383,-50,160,833,-18,374,813,-843,-378,-558,-1000,400,-912,1000,-557,20,-812,-781,351,-1000,-1000,-94,168,-877,78,-1000,-400,779,127,-90,632,-62,28,103,-696,-644,-775,-258,-791,757,507,840,997,375,397}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzk=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzk=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{599,-876,296,-164,635,405,-363,496,-980,167,144,-903,502,936,710,-732,746,377,-775,235,-145,419,-280,-659,-895,672,-748,289,517,-308,277,583,644,-798,285,-130,893,-239,920,-70,-958,-423,-502,899,207,-826,864,-991,466,-567,868,567,571,-834,393,-781,762,836,829,631,532,-705,163,638}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mzk=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{-129,993,775,-487,435,888,262,-791,-476,-990,798,564,452,561,216,-88,943,475,746,-613,-819,165,605,-27,843,-998,-149,-183,-28,27,943,749,-712,-6,-473,-824,-538,190,377,-434,244,710,-883,-415,917,849,398,-371,-504,117,-484,-60,-667,-9,-869,-159,347,2,-308,-341,-37,972,-530,884}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{642,-465,903,-353,-136,468,-458,-350,-398,-607,-925,405,-757,243,506,-874,-6,459,-646,-603,706,511,-117,171,298,191,-878,603,-299,488,-353,-583,-72,-623,180,-207,-163,-699,-551,-262,-492,-731,-505,-199,731,-475,-404,656,-931,-424,-639,521,169,984,-840,719,593,769,34,748,262,-438,-482,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekOfWeekyear():int",
            new int[]{-606,332,-469,464,720,-1000,454,795,7,247,742,-562,-208,-744,-908,1000,-525,-1000,-349,514,-970,-1000,1000,399,326,126,1000,-940,1000,-637,-699,413,292,882,-473,242,-17,203,-74,902,512,1000,-457,-31,-255,689,-223,-371,57,844,700,-183,323,424,-869,576,801,390,847,-574,-78,610,-530,24}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekyear():int",
            new int[]{-199,430,-168,-309,-648,-840,482,-394,763,122,-499,-341,-172,407,907,-511,316,140,-448,365,-572,-743,-665,802,348,-376,151,183,-879,253,-492,-181,731,18,96,-595,718,-776,635,704,-79,677,293,-516,988,468,662,-780,225,618,-159,-128,998,-102,512,188,-546,-292,80,392,-364,713,-98,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekyear():int",
            new int[]{-691,-203,-204,-1000,1000,1000,829,1000,-1000,136,-5,876,-1000,1000,-1000,409,-929,1000,-810,325,463,1000,375,636,826,518,-757,-844,-1000,-963,-404,1000,-734,-1000,446,-1000,1000,-458,-1000,1000,80,-295,1000,557,-1000,1000,-1000,680,-1000,-16,-1000,-1000,-432,-400,-742,-674,1000,1000,322,-354,-1000,-183,-1000,-141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekyear():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekyear():int",
            new int[]{-206,323,-852,-262,905,238,153,943,294,723,-144,-914,790,158,797,-609,523,396,-806,-782,-953,831,160,-336,509,84,395,-520,-838,-363,-650,308,155,370,-802,747,19,-72,8,656,460,114,-517,269,212,-898,295,-792,538,-842,643,253,693,767,980,-103,151,597,34,93,742,-440,225,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk3MA==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekyear():int",
            new int[]{46,558,-413,-48,276,-654,471,705,-428,837,40,-486,-7,361,-295,-67,768,-210,89,626,55,-142,-557,656,423,686,605,331,672,627,-161,-601,245,219,189,929,-60,969,69,-898,-122,-2,746,282,761,-872,77,-721,-659,933,431,482,-282,453,49,677,590,-185,-701,800,897,-162,211,-392}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getWeekyear():int",
            new int[]{-433,322,-705,188,-974,-822,-979,-312,-882,208,137,-165,672,629,-287,155,-80,-923,887,-475,758,-693,-313,110,240,997,145,361,112,740,730,-655,-111,-19,-518,199,-622,587,257,-593,835,-955,482,748,-996,857,-510,273,-822,-410,82,-521,-877,287,297,-40,193,416,-446,237,321,-565,-712,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{-286,-1000,-153,-428,-775,-246,-595,1000,-157,565,-554,857,-10,-844,683,-902,296,1000,-537,-219,-29,-236,400,-473,-342,6,411,256,-99,-8,-729,-364,355,-562,-850,78,395,890,34,71,-201,-1000,754,-1000,-1000,-830,583,-127,105,328,930,490,1000,539,-846,-1000,280,-216,1000,-282,-348,77,-784,-62}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{-774,501,834,702,512,601,1000,-452,-599,-100,431,-126,38,-77,-1000,290,-467,-489,1000,-59,704,467,-652,-473,580,380,-1000,385,814,658,821,434,636,95,589,1000,-755,-1000,444,-479,-751,-864,-689,528,786,1000,583,1000,-494,-327,-182,112,-917,-240,-156,1000,-700,-411,99,-193,-111,-127,118,-253}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk2OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{-182,-141,-540,407,583,-279,308,727,622,685,-585,954,-422,-187,-92,-998,-315,221,-137,-153,-263,376,731,-739,876,452,-112,691,177,724,110,529,-523,-469,-855,853,655,-192,213,218,672,-631,-710,516,81,-801,-684,-913,-587,-151,-917,287,887,689,-479,-453,229,276,307,-428,-980,359,-698,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{-18,-843,183,451,-143,-526,-179,729,-858,-86,439,282,838,-959,417,-828,-411,634,-306,747,88,-598,-820,-687,-490,459,324,-409,835,693,-974,899,412,-288,-520,-251,-97,536,189,-872,-210,-640,406,-879,-923,-192,545,-280,-499,-72,742,690,610,-160,-327,-846,373,449,957,-761,693,898,-918,-469}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{-196,-745,589,691,263,670,-389,-946,-486,-726,366,-835,572,687,234,603,-64,-360,-229,140,322,-710,-746,652,748,-784,-481,387,-790,285,-858,-874,637,-792,371,-487,590,998,-83,-566,-739,-205,-325,680,722,-455,786,340,-901,-118,63,380,359,-776,697,-316,-267,587,-521,-531,474,-125,246,-574}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjkyMjc4OTk0", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYear():int",
            new int[]{170,998,-475,-223,347,42,-956,-922,322,-146,864,-369,-901,-185,-980,-802,724,232,-877,-35,982,-302,-782,-225,338,-978,-526,-472,173,250,-424,592,796,-106,-974,-185,-469,910,-718,-670,-764,184,-369,-298,22,-659,990,169,-350,532,-508,760,-883,-480,592,-139,-729,-118,708,-539,-57,414,963,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjY=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{903,529,-360,390,99,21,662,-55,317,-213,-883,137,-442,382,475,-625,520,-682,-110,-836,511,-249,-963,858,118,-961,-275,529,740,-478,-592,730,-994,-741,-339,608,589,889,201,497,937,246,75,-97,50,930,273,620,592,624,234,-388,-668,-346,421,-761,-780,-23,505,-553,-784,-363,-466,-410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjY=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{-11,-500,282,-852,-755,-792,-478,522,-357,-45,160,847,-582,-949,353,-3,892,800,477,861,-471,383,93,690,799,-21,-113,-934,-773,-254,-41,-471,-93,-353,-233,564,-775,523,561,314,567,311,180,204,402,-155,721,-552,261,293,-203,-668,875,959,-196,-900,7,-93,999,310,-634,681,-998,-423}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjY=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{-176,1000,-382,194,82,250,-535,-647,465,411,-939,-1000,22,521,310,-555,-1000,-904,-263,-88,190,-1000,-525,1000,131,-681,-762,36,46,95,-147,226,-58,1000,32,357,774,-89,282,-99,-451,62,-30,-155,-106,276,-625,-400,157,-185,1000,172,-503,-249,-128,-115,-682,68,749,-302,521,-323,-431,-508}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjY=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{-580,-458,927,720,26,369,-866,382,129,-622,342,-800,539,-37,772,-105,-599,891,774,755,637,-659,-809,-967,-975,96,264,-727,46,546,747,-875,-421,-493,-166,891,-41,-698,982,699,964,-975,806,-984,884,456,-160,10,6,719,-863,-830,519,-563,-558,569,-805,874,-4,-862,-15,-631,-123,-571}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjY=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{391,223,-173,800,-324,67,-489,-859,320,804,998,313,-395,938,52,50,52,-700,665,44,114,-606,678,62,578,287,137,-503,-935,-63,315,-823,-70,-780,224,-101,362,357,-19,-142,-528,-104,439,923,-203,-9,125,-494,-233,301,-283,-277,-560,509,-962,429,63,233,-126,-213,876,837,-223,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:NzA=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{-744,-900,-985,939,872,511,975,22,-259,-264,743,-222,-862,-113,-767,-864,-69,-474,319,-577,247,-499,175,311,109,412,408,339,-497,-825,-47,373,-760,-485,-359,41,-531,623,-139,886,702,738,-225,-266,823,-85,212,-396,717,888,-483,-600,864,594,407,-445,-473,-322,199,824,14,-944,637,-485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Integer:OTQ=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfCentury():int",
            new int[]{-294,107,-396,306,122,383,995,323,-135,-954,-600,-278,256,1000,1000,-841,-327,-1000,-1000,-187,-53,383,-1000,1000,-567,-1000,-810,1000,1000,-864,-468,1000,-929,661,-233,420,248,1000,606,314,737,-1000,-247,-495,402,526,-135,1000,23,1000,-122,-183,54,-458,-196,-1000,-1000,-1000,737,-725,-1000,-1000,520,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{-246,-1000,981,-988,-179,671,1000,428,823,-1000,802,-777,-435,398,-739,614,-710,1000,-215,-319,-11,-382,1000,-297,1000,1000,609,442,-232,404,862,-84,-872,374,-1000,353,384,835,-165,674,873,1000,677,569,-707,-913,-902,482,217,244,-255,572,-1000,-1000,-1000,488,-893,288,-1000,-499,-374,-901,-247,-191}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{252,-610,873,-48,83,-81,147,-454,17,222,-891,-375,89,259,593,-949,-573,-845,7,610,-236,371,415,641,298,-1000,-819,-174,509,-31,-71,973,491,543,-815,529,1000,-1000,705,1000,86,567,-634,206,348,-95,-584,-185,903,-73,-1000,-951,-299,-431,757,297,289,-1000,662,22,-405,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjkyMjc4OTk0", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{-390,52,628,-450,-950,459,-847,-802,17,10,-123,-790,-995,-734,616,-950,992,158,523,699,682,531,-27,690,489,89,570,897,-175,109,0,330,310,-150,-799,-341,-574,-474,862,-993,817,871,622,-337,965,-88,-522,-188,-732,345,-69,-891,-287,95,-848,984,-742,106,87,943,807,-855,-202,-202}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{-383,180,-433,-57,-845,267,-575,-2,192,304,-676,368,-123,490,-857,189,-928,-735,975,-592,947,-194,-81,-430,-439,-55,-968,700,-394,-505,-197,145,562,-452,247,726,414,-784,264,362,-254,511,226,-542,576,-455,-32,-244,-408,127,-688,-885,575,-67,37,730,-208,-90,834,680,829,367,-820,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjAyNg==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{325,190,117,-534,602,-195,-344,425,54,191,307,-564,473,-508,732,-839,-901,254,246,40,-771,992,664,-216,611,451,641,375,81,-912,-382,-523,-31,-147,267,-373,714,292,859,419,770,177,294,-25,-530,-600,765,464,757,-290,-55,150,-106,-969,404,-491,-836,530,581,-660,368,428,-983,464}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Integer:MTk2OQ==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "getYearOfEra():int",
            new int[]{-892,90,-1000,1000,-706,404,-977,944,354,1000,-1000,834,462,465,584,-1000,273,-1000,-1000,609,-904,-194,-76,-492,-250,-1000,-165,-1000,39,-329,-197,-209,-22,-1000,858,274,-340,30,1000,546,-524,35,592,-1000,-419,322,176,-481,-1000,-1000,-384,-885,-124,168,1000,-1000,-205,-445,834,680,-509,963,1000,-318}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{437,-1000,753,-1000,-521,1000,-696,-1000,-671,-324,-154,-1000,1000,736,-1000,-1000,-863,-722,-198,1000,-171,-267,665,-808,-820,-1000,776,1000,1000,285,-902,-1000,380,-1000,-602,852,-220,-632,136,-186,-480,577,372,690,338,556,415,550,-802,-374,-954,-135,-994,855,-333,1000,-1000,942,668,179,9,891,-780,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{437,886,-252,-573,-605,-940,-696,749,979,632,-26,-695,-886,-374,-387,935,-428,-657,242,-936,230,-942,51,-808,-806,247,65,-846,-371,-653,678,939,760,199,-39,-692,-220,468,-101,-148,-861,577,526,690,52,-989,-699,-438,-20,172,20,-281,-704,430,39,-271,388,463,668,70,-970,-442,-780,-776}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{207,-700,-870,-966,988,-442,549,-440,-231,748,586,278,486,-471,480,-224,-3,405,81,999,441,112,20,690,716,-636,-104,-94,-145,-322,-857,-355,771,148,965,-6,-957,-827,953,-288,203,-590,830,-386,327,512,127,-226,658,-440,560,-858,-572,-454,-651,916,-892,-276,813,-228,900,220,179,-136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{925,828,-809,92,422,-611,516,83,837,711,-485,559,-993,69,409,44,344,658,-786,-25,554,460,-159,-412,244,473,-361,-563,-695,-107,685,929,625,-55,-242,645,-104,-779,238,-325,80,-365,947,-280,-577,-599,343,-732,836,-215,-576,833,-92,-168,-820,-86,880,-266,518,123,474,-933,192,753}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00159() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{135,-267,96,-647,882,769,-834,-286,696,-883,98,85,455,-699,-962,-607,-159,-883,593,295,87,724,581,-485,621,943,-975,-308,577,924,750,-313,766,-575,-403,-320,958,-868,536,834,-916,-301,-218,866,-683,457,368,371,913,131,-397,473,-862,304,-682,-3,-321,-125,-124,196,479,-709,557,852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00160() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DateTimeFieldType):boolean",
            new int[]{-94,-328,-700,-287,-586,-484,189,-837,-894,101,-851,644,93,760,-127,-231,21,159,216,180,407,-336,153,404,888,-212,790,-905,-588,390,833,-827,42,-528,-404,748,-732,546,-100,199,-493,813,20,251,816,-324,-797,-464,755,-527,241,-311,-205,-248,-930,-179,-286,873,-564,-107,-979,302,291,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00161() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{-707,-1000,-663,-766,-223,-684,-566,532,345,-769,418,-964,16,-882,-254,-631,-983,1000,-557,581,-802,-409,-1000,-826,-62,827,101,932,-665,-779,-991,-898,-1000,-1000,-590,-534,-261,1000,-1000,-719,35,645,-854,-174,-563,-9,828,-861,882,-1000,712,-850,-842,-502,880,-731,290,1000,-65,-168,365,1000,-750,-115}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00162() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{-707,512,741,-934,-582,837,772,-425,-382,-456,566,-905,601,-709,429,285,-836,495,906,165,35,-704,-613,798,-62,-818,-105,932,905,-615,58,-364,-14,356,-985,45,438,66,636,-309,-748,669,-159,-424,-563,-873,26,-861,882,-164,671,584,788,-40,17,-362,-303,-747,405,747,-47,-568,-676,984}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00163() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00164() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{-788,-410,-995,925,830,-291,503,936,542,-646,-881,210,882,-148,333,995,-680,155,-541,975,187,665,548,132,-333,-372,888,816,-460,-576,788,-722,-499,-753,80,-36,-147,85,-585,218,102,-252,-186,754,565,-274,183,235,-500,96,-463,601,-565,-622,526,-588,-588,-693,-463,414,674,-227,90,-247}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00165() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{-794,-823,-318,968,777,17,-696,-550,721,702,807,487,274,-169,-305,546,-622,-136,996,-639,-911,-585,-166,-8,-245,410,-35,-969,-61,678,-811,420,-865,-282,333,-557,-661,808,-870,-197,242,452,-294,366,426,190,630,-808,290,253,-471,717,-331,-794,100,-377,82,788,52,-167,409,470,-49,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00166() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{-170,48,-1000,695,-383,-449,-391,1000,-779,-857,-781,936,823,555,-1000,903,-51,1000,-970,1000,1000,-168,503,1000,924,-321,1000,1000,0,0,37,-630,314,-1000,-1000,-1000,-1000,671,-81,-1000,1000,1000,-1000,0,486,-284,258,264,393,1000,713,1000,-516,-1000,562,514,-807,-1000,-19,-268,-447,1000,0,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00167() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "isSupported(org.joda.time.DurationFieldType):boolean",
            new int[]{-878,-547,-114,398,476,-95,587,175,636,99,1000,1000,-100,311,-317,1000,-1000,75,-202,-569,-1000,-1000,544,-518,-826,986,-872,-1000,350,371,-632,892,-81,20,777,-638,915,257,-1000,-685,161,839,17,231,-473,784,-449,-851,154,797,244,1000,354,-1000,359,-1000,580,1000,183,-599,915,-967,-196,-104}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00168() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{269,-472,36,1000,-157,156,-1000,183,560,-1000,-1000,-1000,-941,514,295,-411,35,-1000,-172,154,-1000,-32,-463,-701,43,-220,-129,618,-1000,667,-617,-1000,-63,1000,-821,1000,1000,416,-71,100,-412,-797,1000,1000,-1000,-524,-1000,470,1000,-82,-381,308,-439,-603,-546,444,-140,910,-1000,730,-1000,624,-970,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00169() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{300,-38,-753,249,486,-768,-979,-1000,1000,1000,909,32,-566,76,124,922,-829,964,729,-462,400,665,-151,1000,375,1000,-1000,-249,1000,491,343,705,-1000,-598,207,946,-328,-948,-364,756,-308,1000,-815,-592,993,-344,635,958,117,-349,-1000,-56,-466,1000,-140,236,-92,-478,332,153,617,-514,967,-716}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00170() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00171() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{192,426,-445,561,973,-170,385,490,-223,522,-510,-595,-997,-128,870,-675,355,-904,-889,544,-681,896,-867,-559,705,745,808,-633,219,700,88,-942,-60,-550,-406,510,-170,958,-702,525,977,482,-123,-742,-888,-954,-415,-986,-863,-529,108,-756,907,987,-276,-735,-708,-862,-654,567,-327,-799,517,-279}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00172() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{-526,57,-59,67,874,-774,119,312,839,-246,817,-685,-238,-659,-837,-170,72,-619,38,197,-269,829,471,-391,-172,-436,-288,-668,-739,673,-336,-114,487,-45,-288,-309,499,-455,-377,951,808,-979,168,-334,-976,-398,-619,577,847,-857,962,570,763,-507,232,-381,539,-519,263,680,733,332,328,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00173() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{528,487,-360,1000,-845,-978,28,-1000,-496,-1000,-340,1000,449,471,809,459,1000,712,754,-310,1000,1000,684,759,803,496,-599,-445,-875,-1000,641,284,-457,-195,589,-1000,645,-679,1000,560,-1000,-1000,1000,224,378,217,-608,-599,-421,1000,-971,-56,201,83,-133,558,-840,-332,-66,-1000,-409,-1000,98,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00174() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{-295,147,540,1000,40,136,558,490,-1000,-20,-1000,-330,289,1000,870,-729,254,-904,993,78,615,896,15,-676,705,12,-196,69,-889,-1000,-584,1000,-60,-616,342,-689,749,365,334,234,-1000,-591,919,-742,822,-1000,-415,-642,791,-27,280,330,346,1000,-276,1000,-1000,-657,-1000,249,-327,-693,-485,324}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00175() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{1000,595,-261,1000,357,349,-167,471,-308,446,-893,369,461,-1000,-631,-182,182,1000,-145,529,442,-764,-1000,-10,-336,-93,214,-147,384,682,-1000,485,-316,131,-249,806,-891,79,1000,-521,568,-257,690,-170,395,571,-413,-866,-1000,383,-907,-281,-1000,26,604,-268,-515,-110,-1000,328,-43,-386,-584,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00176() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{-758,-764,-552,76,161,958,-137,-552,694,411,-334,-968,-671,-970,531,-437,-401,-376,-137,-631,-148,-861,553,766,-976,411,-490,-832,-445,121,898,-118,954,690,-775,-765,175,-909,14,94,-109,-296,-368,-866,-918,-779,-948,244,-108,-648,808,-619,644,-365,206,851,-908,-21,989,-475,536,757,71,-187}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00177() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{-126,97,64,-384,-470,937,-444,-83,764,263,-1000,503,-423,-630,286,-1000,-392,-668,-1000,-787,-577,601,77,740,-613,166,139,163,-436,-631,-497,331,-2,949,-934,394,874,-735,-145,1000,-147,-176,-723,547,-837,-58,204,1000,164,-444,1000,-768,1000,-252,-327,482,-1000,402,212,498,106,645,-97,924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00178() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{-411,-888,-868,904,-177,-405,-154,-445,-316,849,147,813,-22,-50,-575,-661,196,203,-374,477,677,-362,271,-503,-294,-605,596,813,-506,-27,-532,926,-750,-206,-993,457,276,-139,363,108,938,328,-8,635,635,-209,-51,-7,-477,-230,209,-310,589,-562,840,-897,939,992,949,999,-355,219,-207,-173}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00179() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{168,103,-389,-831,-52,-787,-193,-660,507,-756,444,-968,24,759,260,595,199,-295,-618,-158,-538,936,-276,-760,782,-705,280,-463,518,35,-963,-163,91,-180,-580,796,-81,-177,798,34,987,-415,-609,-860,-250,156,150,-289,596,-173,-370,-257,-949,-743,735,-595,83,-820,271,713,-591,-973,-261,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00180() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{-411,162,963,-935,1000,181,563,-297,-1000,-56,240,-745,-7,-50,-745,693,1000,896,1000,477,918,-1000,-680,-503,-343,834,-932,-61,280,411,1000,447,517,654,-993,-1000,-1000,1000,1000,-1000,-1000,-1000,-8,-1000,178,-358,553,-1000,-519,370,-1000,-630,-1000,1000,779,-897,158,-500,620,-873,206,-845,554,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00181() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{551,-31,-813,-800,-1000,540,5,-336,-1000,-85,-1000,-317,-431,-477,-621,-1000,-28,-1000,859,-483,-51,-794,-876,-1000,-346,-73,-308,452,-55,328,-955,-130,-719,1000,1000,-216,115,-1000,-843,-785,-277,-356,-1000,-515,-19,1000,55,-634,-1000,-556,-127,281,-1000,-882,321,-506,-48,1000,-228,-659,-701,-1000,1000,667}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00182() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusDays(int):org.joda.time.LocalDate",
            new int[]{611,319,164,-988,-728,-709,-359,668,-77,-270,-154,-317,809,372,233,-244,-140,1000,137,506,-365,-51,-1000,-1000,497,-20,97,39,419,402,-653,-309,-1000,156,1000,402,353,-1000,827,-268,-274,-284,-388,761,-94,1000,821,-395,-840,342,-836,15,-1000,-300,503,341,55,1000,-1000,197,-883,-1000,710,-521}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00183() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{-298,562,513,650,881,-49,-739,205,-48,-489,-477,161,534,-474,-101,-121,-64,-656,652,-839,-886,198,716,881,747,-161,888,165,-867,-966,132,700,-663,-33,426,854,781,-787,-875,-106,285,-895,424,-228,-503,-986,-830,731,-448,383,-961,-740,-137,160,837,836,8,726,-381,-691,-181,338,-834,-199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00184() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{-1000,-764,-339,-818,1000,1000,540,1000,-1000,1000,-792,-1000,-1000,-189,1000,-713,363,741,-1000,1000,230,1000,345,639,167,-873,1000,964,-1000,1000,683,612,544,639,-1000,904,2,1000,1000,-303,1000,1000,431,-1000,1000,1000,-688,47,1000,165,-1000,-184,1000,-1000,306,-1000,1000,1000,941,-452,1000,-428,1000,-613}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00185() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{977,682,-580,539,-278,-789,459,-801,550,-420,-244,982,-760,-245,-381,946,-59,113,936,-115,-395,-777,-328,-538,-368,898,-99,214,-790,572,-399,-432,-433,100,915,58,-906,-330,-875,570,157,-330,216,-373,-722,90,-441,-526,-656,286,-5,-952,-497,-165,389,782,-501,-443,-407,642,340,795,-318,798}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00186() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{386,563,295,-119,-784,259,988,917,983,307,607,-273,326,317,-160,808,-615,129,-603,-457,-24,-553,-498,972,568,687,787,267,-666,-864,95,591,267,537,-837,53,-884,830,-270,-872,-282,-85,-786,-930,-792,-948,781,626,536,-234,355,654,733,993,936,136,-528,606,-568,508,567,-588,-847,238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00187() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{-952,-107,-355,-301,899,472,-989,298,773,-656,65,367,-770,521,-469,-354,1,959,-267,-289,775,958,-689,793,-364,-207,-423,340,-542,950,-445,-236,-716,369,200,896,465,700,892,264,181,735,859,-466,-161,-119,887,188,823,858,416,-182,-928,-384,-112,328,-964,-103,119,724,726,824,-657,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00188() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{1000,96,-759,367,-671,-438,581,-1000,1000,-1000,17,827,-856,750,-33,-177,-46,456,1000,-814,-833,-1000,-926,-656,-92,719,-1000,-630,-1000,335,-55,-491,-437,-366,1000,250,100,121,-201,1000,267,291,-913,-1000,-1000,-224,1000,-641,130,854,-916,-1000,-1000,234,-372,750,-1000,-279,-1000,336,-986,-1000,-432,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00189() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusMonths(int):org.joda.time.LocalDate",
            new int[]{-47,444,70,-151,771,904,558,-616,351,116,607,-461,-51,328,143,-851,1000,129,-940,751,-791,190,1000,1000,1000,770,361,513,1000,-1000,95,-564,540,537,-1000,-960,372,-391,-398,-111,-1000,548,-663,1000,585,-844,428,-243,860,-1000,469,55,1000,-335,715,-1000,1000,21,-622,-468,-30,-760,-138,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00190() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{-393,-197,-522,-610,-950,395,-208,-660,-941,-995,261,-787,-538,-240,763,-158,415,552,609,730,618,347,482,-270,190,-391,223,800,185,-835,-172,504,969,686,523,287,-996,-699,360,468,-87,-172,437,-666,-858,595,-465,794,23,885,733,609,582,-317,-588,401,-957,570,-85,340,-319,373,121,-281}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00191() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{518,-302,-336,-90,1000,-227,-414,989,573,-147,187,602,553,-624,-748,398,-349,141,1000,598,1000,362,-127,1000,713,-69,-95,1000,133,1000,210,-1000,239,11,260,375,-121,-784,102,1000,-537,-592,-1000,280,-234,-207,-1000,995,622,765,797,310,409,1000,483,-349,694,-409,737,928,-996,-734,-714,-451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00192() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{-516,-486,-930,-478,-1000,433,314,-794,903,1000,866,-249,1000,907,-1000,671,-1000,-1000,1000,500,267,135,-1000,403,-981,459,-1000,-195,-1000,-953,590,-776,1000,1000,-320,-725,91,-246,857,1000,-1000,464,-531,1000,-545,239,-1000,1000,1000,-241,759,-483,1000,351,-281,676,-794,87,-1000,-1000,869,-338,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00193() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{507,-129,518,-91,-862,-209,779,33,464,-891,-476,454,-324,392,482,-687,655,585,-967,526,-186,-270,632,409,-48,-695,-174,899,-873,-184,750,846,812,69,509,172,-101,947,-579,-388,466,443,747,-769,-355,201,-758,-72,-562,890,615,-262,835,367,831,912,255,741,-925,465,943,263,-853,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00194() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{-627,-498,-808,-554,-975,-233,-172,-97,903,911,519,-399,818,106,-519,571,-885,-783,228,-455,434,761,-923,-362,-632,-818,-752,-548,-731,-671,713,-687,727,805,-573,-301,525,-729,182,829,-768,-310,-38,831,52,40,-635,982,977,-952,451,58,897,149,186,40,-102,62,-443,98,665,114,-625,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00195() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{-59,-702,-138,422,138,-582,610,-331,-769,-417,417,-434,-212,-327,275,345,727,651,-1000,391,-204,-992,-268,-801,-380,615,315,-576,-204,-261,501,577,92,-101,-791,-424,14,-58,-487,620,-149,-398,973,536,332,-286,4,-1000,-680,-437,563,666,-75,-1000,222,-13,70,326,330,-116,170,525,593,185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00196() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{-922,213,-822,-512,-1000,268,-302,-537,566,-159,-1000,940,201,-298,-571,516,-879,-233,1000,986,-639,1000,141,-188,666,-323,-1000,652,1000,-442,-151,852,811,-305,361,1000,-840,-576,933,1000,-388,-251,-166,-256,639,806,753,1000,1000,1000,645,-872,134,1000,-314,132,215,-436,251,618,-1000,799,113,46}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00197() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusWeeks(int):org.joda.time.LocalDate",
            new int[]{-1000,-154,-224,-1000,-1000,-57,-481,-1000,-1000,-939,49,-299,-249,228,1000,232,839,389,213,206,1000,586,819,-336,258,-437,-126,498,-490,-540,414,1000,513,672,180,-339,-624,-1000,369,432,-679,-243,435,-320,-789,634,-566,1000,313,148,1000,623,97,-228,-808,248,-1000,-582,537,491,344,933,701,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00198() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{-733,-402,315,-428,399,-371,229,945,230,1000,32,-871,738,-1000,-1000,-1000,27,1000,-73,-673,1000,-566,545,853,326,1000,-1000,-441,-1000,128,1000,-1000,1000,-259,1000,-1000,-424,-656,-809,72,693,648,1000,-361,-1000,825,-460,-1000,-599,-547,745,-1000,-321,1000,-1000,-160,-610,355,-973,-320,-648,-1000,-903,-117}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00199() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{280,-643,831,752,-590,-482,-505,1000,1000,447,-560,-787,1000,-766,1000,-1000,690,-181,-1000,-704,-960,-798,605,266,797,-932,1000,-1000,-1000,1000,224,967,-504,504,15,-56,-1000,-125,-168,-1000,1000,881,349,190,-199,438,-754,-981,-176,-1000,1000,-216,135,-160,1000,435,1000,354,-846,-1000,-662,-1000,-1000,-284}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00200() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{5,111,-96,-391,53,54,-781,787,-1000,-1000,-730,1000,629,401,1000,1000,-1000,-343,-633,-407,746,-864,776,-437,337,-1000,742,-801,-637,1000,-414,1000,97,1000,-536,885,-1000,-125,134,-1000,-606,468,197,694,57,200,-233,-886,-313,412,-410,967,1000,-153,225,528,1000,472,-555,114,-1000,252,-934,-295}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00201() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{880,508,363,-350,87,-764,-733,-691,111,715,-667,26,-844,-991,-255,987,-629,371,-8,-245,-550,-981,181,-685,-279,509,-838,150,29,-851,190,886,-75,-921,83,469,468,860,-328,378,-705,736,680,946,-488,67,-852,-491,431,-111,3,-197,229,11,-577,-200,-830,-374,-697,-991,-782,-975,-705,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00202() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{-426,-341,661,44,-47,565,480,704,274,919,640,-579,831,-598,-527,-875,991,932,899,231,995,342,518,-407,546,612,198,457,-505,372,402,-268,198,-403,769,-669,-131,-400,248,853,608,-223,472,-523,706,891,23,-444,-732,-881,691,-891,-734,557,-57,-793,-300,453,421,-972,-280,-988,-528,286}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00203() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{869,4,-121,-740,289,-371,470,1000,631,625,-312,-103,459,1000,-905,-447,-128,773,139,-191,770,-139,1000,853,178,-1000,-1000,-1000,-907,-123,-662,-922,801,1000,-743,-56,-877,-386,-1000,-1000,-560,-940,882,23,-1000,958,-460,-785,-599,582,-360,0,985,1000,-13,115,155,232,-973,1000,45,-784,-743,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00204() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{745,811,-1000,-857,-358,481,-794,-691,-837,-473,875,981,424,525,-643,541,-390,374,-294,543,-705,-525,-610,153,-649,158,1000,-408,-1000,550,254,-244,-452,-419,-968,-1000,-856,497,1000,145,-1000,-91,-757,-795,914,-322,-458,-491,-455,-1000,-1000,-119,655,-201,620,-544,750,-1000,-25,-991,330,-975,-640,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00205() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "minusYears(int):org.joda.time.LocalDate",
            new int[]{347,-107,-957,-1000,505,75,163,290,894,1000,131,583,867,290,-1000,-978,-444,748,-697,-292,525,-732,1000,54,-330,243,207,-1000,-1000,-117,444,-1000,1000,208,-193,-765,-923,443,-510,-1000,-270,245,-84,-770,56,277,-118,-527,-541,-522,-111,-970,505,1000,-659,-97,557,-604,-801,-69,474,-1000,-396,-733}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00206() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{-116,-406,585,854,-233,440,-798,-742,912,347,-652,546,-397,893,-223,-849,-590,-693,-559,-99,650,-476,-125,-406,360,-290,-224,302,203,-920,620,-69,-714,-475,-736,-957,-137,328,995,-476,582,-911,711,267,94,855,607,-284,-405,435,26,869,199,-138,992,-872,-89,69,-667,-774,-601,987,290,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00207() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{-219,677,63,218,-576,73,-682,-785,-770,370,423,-779,404,-630,-198,893,-129,-835,-109,866,-305,-306,174,-943,-125,-911,12,-516,929,54,79,-452,133,244,233,239,351,-652,-718,276,-944,452,229,794,-724,101,-691,-148,-3,476,437,-182,847,98,497,-573,757,556,-663,207,769,-101,-710,-109}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00208() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00209() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{-225,526,354,257,-1000,25,-605,-35,-467,448,327,-681,484,-430,226,548,515,-835,189,-1000,-262,-537,-285,-502,-453,-303,547,-387,694,-4,1000,-786,-14,-119,170,390,-174,-215,-455,296,-567,255,163,1000,-617,597,-662,-518,465,279,280,-403,437,295,503,-464,486,297,115,-577,72,-523,67,705}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00210() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{-894,536,584,-601,237,756,-909,-964,775,317,-462,-326,-277,-72,495,-168,861,-80,-722,-176,858,920,-785,-546,89,896,652,59,62,634,537,-825,-130,-652,-543,549,146,-46,-393,-223,619,-673,299,483,576,766,-615,-407,259,-144,-792,-984,-420,919,571,836,363,-665,-406,-5,-186,-853,342,326}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00211() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{17,-184,-983,341,-205,588,766,475,-980,131,790,-836,-871,317,-893,485,616,-451,-235,639,12,670,644,714,722,419,629,-171,318,-69,-799,-109,440,489,545,645,497,-497,-137,302,983,501,-263,583,425,-146,-882,-261,-265,-147,775,804,-970,835,925,-829,887,83,-876,-581,423,398,-487,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00212() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{-260,-416,503,1000,738,351,136,-928,-198,255,-1000,1000,-1000,1000,-1000,-74,19,-1000,-863,1000,-245,191,1000,-581,327,-1000,631,-782,952,-299,1000,-836,-145,-393,-115,-1000,534,-811,-794,-223,696,1000,436,-418,-848,132,1000,-1000,-1000,967,58,1000,968,-457,210,-1000,1000,873,-574,-715,-670,1000,-548,899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00213() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate$Property", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "monthOfYear():org.joda.time.LocalDate$Property",
            new int[]{-783,268,584,326,1000,1000,-1000,-1000,483,161,-864,65,-719,715,-417,-507,-475,-1000,-950,-8,938,1000,15,-1000,509,-440,-66,-287,-21,-553,-733,-387,-791,-447,-911,-982,-554,-514,625,274,444,-719,539,593,272,292,127,-227,441,369,1000,284,833,-439,991,-984,843,101,-1000,121,-631,591,-177,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00214() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "now():org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00215() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "now(org.joda.time.Chronology):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00216() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "now(org.joda.time.DateTimeZone):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00217() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "parse(java.lang.String):org.joda.time.LocalDate",
            new int[]{4,-1000,-123,-357,-981,389,-665,798,-361,252,1,76,-865,1000,1000,-720,168,-230,721,906,-1000,306,330,-493,251,-987,-47,-203,1000,-1000,-572,692,139,-125,117,-184,-297,-1000,880,93,115,1000,1000,744,-475,-290,448,-776,-541,1000,429,-1000,-62,-1000,603,490,-758,1000,-1000,-1000,-451,-436,-756,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00218() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "parse(java.lang.String,org.joda.time.format.DateTimeFormatter):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00219() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{-348,-351,-933,618,-582,-518,453,-184,-815,-464,842,-504,-795,-846,715,482,-146,984,-27,164,-499,168,73,929,772,-704,645,565,667,-968,-708,-48,-118,-900,150,383,-970,209,-763,55,496,378,600,441,132,-847,857,517,-858,222,847,192,-867,-932,-217,-387,544,-545,-427,550,445,795,767,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00220() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{1000,-533,-414,-273,356,3,-1000,-1000,490,-792,104,-299,-18,1000,1000,458,-1000,281,330,99,801,-128,1000,-1000,1000,384,-1000,1000,-245,-1000,606,1000,1000,-252,-592,-524,8,468,983,85,-848,-695,897,-725,-861,-974,704,-489,-251,818,-1000,-646,-164,913,1000,-703,529,1000,621,-1000,867,-1000,-408,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00221() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00222() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{-1000,592,-212,-210,-120,-767,52,658,415,-130,1000,-56,-449,-1000,339,634,966,239,458,913,-525,399,-240,647,751,1000,-1000,900,-394,-716,-525,-101,-112,-643,-336,-293,698,823,641,-796,327,170,701,-380,1000,-311,340,-707,-802,128,116,-655,-170,437,-372,-1000,681,-947,-814,-1000,-724,-1000,11,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00223() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{478,481,-805,989,774,-874,-732,287,706,551,131,-97,-450,-238,415,825,842,-30,185,197,461,-414,89,-674,-204,180,-497,821,371,476,-217,-189,-836,-786,616,284,-737,620,895,-300,444,106,537,-735,835,838,253,-497,-114,-157,380,-887,550,-556,-422,-541,777,-14,-532,-477,-764,-960,78,669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00224() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{957,-84,778,-411,-142,-589,958,945,-819,830,-937,476,989,492,-433,-469,927,-107,-854,-323,769,-527,-487,59,-243,-600,-263,-53,-828,960,-785,-993,-578,-6,364,-140,354,647,-506,-317,-540,-355,-620,-221,-827,276,-431,326,-450,-683,615,-155,317,-731,113,-694,421,409,-359,-737,87,416,575,923}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00225() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{-628,465,931,-1000,-189,-518,-727,414,764,-341,-738,-407,-1000,-846,170,-833,921,-820,309,68,621,693,-1000,187,-1000,1000,-364,14,940,352,-558,1000,137,240,1000,-846,-494,-500,-763,617,496,-513,-1000,-1000,241,-1000,718,-1000,-718,-150,-450,72,303,-625,-292,725,384,244,298,-732,-411,634,-1000,-523}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00226() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plus(org.joda.time.ReadablePeriod):org.joda.time.LocalDate",
            new int[]{-505,-779,680,-1000,-1000,-628,-326,-681,202,-1000,-1000,-1000,117,730,1000,1000,-421,627,1000,-1000,1000,1000,-199,-1000,122,1000,-682,1000,-256,-1000,1000,1000,685,-450,1000,327,-1000,684,-439,-439,1000,-553,352,-781,1000,-963,1000,-682,-1000,-1000,-1000,-609,162,-632,497,1000,915,39,-391,-177,-555,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00227() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{622,-1000,-204,-1000,766,-1000,1000,-297,-450,-748,-893,715,-522,54,629,579,-350,954,1000,-1000,816,316,1000,-998,978,-1000,1000,-926,744,-346,690,848,1000,438,822,484,788,377,647,1000,40,919,502,-1000,868,526,845,380,618,660,-373,1000,1000,294,1000,-611,-807,-452,208,-674,-79,156,672,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00228() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{607,-863,381,1000,321,-1000,919,-504,2,-386,-1000,-858,-1000,668,-29,-173,30,759,-1000,-644,-347,-1000,807,-320,1000,-62,322,-1000,-121,390,601,85,227,605,-449,1000,71,-11,261,-828,-152,621,-936,-1000,85,-55,-177,435,704,1000,572,-618,1000,1000,-567,-1000,-1000,75,340,662,1000,431,1000,-147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00229() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{359,-821,-549,-804,-100,-994,679,-370,-795,-639,865,592,23,-101,875,392,-96,895,779,-645,910,615,808,-742,131,-697,636,-811,946,-677,530,783,868,79,373,-358,252,-522,-508,677,114,907,-205,-912,925,-41,772,-244,-679,473,-380,-70,399,861,938,-746,-511,-885,237,-566,-865,452,-145,-868}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00230() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{865,-526,960,487,-479,590,-793,-597,884,137,-451,-598,-389,635,-635,580,761,-131,-462,-75,297,-549,-823,911,861,-929,-567,-891,853,-482,13,-670,-290,677,460,-324,706,-179,-803,798,617,726,885,634,-853,-584,741,-31,141,-337,-887,257,-706,768,830,325,-657,-400,-358,803,-506,384,384,445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00231() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{2,998,202,-131,314,962,-239,626,-104,90,495,436,690,174,345,635,29,520,-472,984,-961,53,-989,-955,288,-408,-512,-83,-597,836,730,805,-728,741,571,-228,882,69,743,-67,-153,-881,-655,-657,240,-99,802,890,-522,353,-178,-409,-304,599,105,883,284,-310,-675,738,-894,-216,-763,713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00232() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{697,792,41,34,940,548,283,372,569,-301,-452,-354,239,-25,-297,34,-228,50,-834,64,-1000,-889,109,-1000,782,-1000,285,-765,382,902,1000,-339,-365,1000,282,821,926,955,823,551,-725,87,-333,-1000,19,366,859,751,149,950,-17,-1000,590,1000,-339,-50,-534,123,-38,947,149,54,-106,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00233() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusDays(int):org.joda.time.LocalDate",
            new int[]{-477,168,811,289,176,330,662,-327,-355,156,-42,1000,-269,-63,675,585,-513,1000,-736,-142,283,-1000,65,3,292,306,-272,21,-1000,809,1000,480,739,596,-105,171,106,-1000,405,-707,-380,-916,-838,93,665,-442,280,402,-214,362,-290,-450,488,1000,-698,1000,-84,7,-645,823,-316,521,-844,629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00234() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{10,-736,-444,463,-1000,325,-174,271,-80,630,-449,722,357,729,1000,756,-442,586,-346,239,892,609,-160,394,-312,-56,1000,117,834,-811,978,689,222,-1000,224,862,-255,205,283,-882,260,-287,1000,-762,-538,1000,-310,227,227,304,790,-379,135,126,62,514,-323,385,-10,318,947,-371,915,371}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00235() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{669,787,-318,-146,-1000,-1000,992,-393,439,-1000,-952,-358,-762,108,683,383,1000,1000,-801,1000,991,47,-1000,1000,561,-1000,-897,393,-400,-1000,968,-838,1000,-106,1000,-1000,-402,1000,-249,1000,-1000,197,290,-687,1000,-1000,400,-1000,638,-1000,-352,-396,1000,481,-184,-569,-66,-566,847,927,-1000,-1000,200,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00236() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00237() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{-653,145,-878,-857,833,739,8,336,642,632,263,-124,-707,-676,926,857,499,792,362,-623,778,281,-14,-257,-542,-545,231,546,284,147,-39,-488,830,895,-259,526,-140,55,637,370,-154,381,-452,-668,-37,663,-532,-903,54,274,540,-918,292,273,-742,762,569,101,774,397,-2,-152,904,82}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00238() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{339,558,134,-952,-346,-884,-322,-255,33,-114,-500,412,-146,452,78,-481,704,-433,-343,-188,-162,-254,749,465,843,781,-275,-687,21,-58,-521,-923,-24,-423,-647,34,805,-555,781,673,-581,-809,-563,-436,562,95,-935,-159,-671,471,628,534,169,-103,920,284,-304,-157,-619,927,-22,210,489,864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00239() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{19,-415,42,-644,3,641,-559,-240,-894,185,828,-897,946,-432,-25,-234,-820,934,626,-70,-783,-670,787,-558,768,181,872,-671,-989,145,-83,-889,68,322,325,646,-502,-624,-167,273,524,257,-265,741,-130,-113,806,569,-799,441,790,-673,-548,-342,-125,625,-409,-10,-887,533,737,358,-569,-388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00240() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusMonths(int):org.joda.time.LocalDate",
            new int[]{340,-227,-253,340,32,-57,379,202,293,-100,24,-1000,1000,-420,-34,1000,-251,1000,544,820,727,-77,-870,-520,-542,-1000,-117,1000,-142,-580,1000,967,1000,77,1000,975,-1000,729,-675,-1000,423,321,1000,1000,-1000,-1000,1000,279,352,-457,298,-918,699,580,-381,977,-1000,114,75,389,507,-152,-1000,-77}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00241() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{-26,-1000,-966,716,1000,9,61,-608,-780,-595,-408,-64,993,-413,-1000,1000,881,-939,1000,-152,239,-312,-176,-1000,675,-62,-22,1000,-551,-854,890,1000,-384,1000,725,-1000,1000,-230,1000,150,1000,-65,-1000,1000,1000,-235,624,-341,304,1000,23,1000,1000,152,20,202,-619,572,821,-210,-238,239,1000,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00242() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{411,677,192,-781,215,696,-505,1000,-499,-465,584,-475,-1000,-350,-547,735,365,-138,611,-611,325,-291,751,-403,-566,971,1000,791,610,-623,-843,-107,70,131,572,-14,1000,-367,328,606,285,518,-420,17,1000,307,-190,-314,426,301,66,412,-1000,-57,-1000,-172,-433,923,188,-515,1000,648,292,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00243() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00244() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{-154,563,-298,-840,648,289,713,-352,-194,313,-270,-454,814,929,-689,52,102,-980,193,-442,920,-878,120,-942,-695,140,-45,728,-297,-890,-366,201,-811,-326,911,-349,-885,332,-235,-571,-493,-82,857,711,540,-718,-523,370,-955,655,-945,133,136,-173,510,966,775,931,-576,583,-620,-657,404,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00245() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{777,459,-1000,757,573,1000,214,945,552,-136,-911,-795,-1000,1000,-611,133,-1000,-361,-787,-152,-671,-312,630,-128,230,-91,645,-395,1000,121,883,-466,61,342,-799,504,-708,484,388,-592,446,192,-620,192,1000,-464,531,390,21,494,-880,1000,-911,-108,20,202,-619,-75,60,1000,-238,-72,1000,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00246() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{894,796,-1000,169,622,1000,-742,134,723,-1000,-514,-454,-1000,929,-220,-429,-1000,-393,-1000,834,-995,120,496,-465,254,-194,-45,-403,859,-560,725,-533,572,948,-510,710,-404,218,429,-913,-174,-251,857,-118,1000,-718,-417,956,598,671,-61,1000,-1000,-528,-185,1000,-544,931,413,1000,-620,-69,1000,260}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00247() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusWeeks(int):org.joda.time.LocalDate",
            new int[]{1000,608,596,934,-1000,919,-314,1000,1000,903,-660,-496,-1000,-350,-1000,-1000,711,301,-664,-568,-864,-490,1000,1000,315,1000,1000,217,-101,1000,234,-1000,464,-714,-1000,976,-790,1000,-700,1000,282,518,-420,366,-270,494,873,-157,-57,1000,-583,207,-1000,651,-618,-1000,634,-507,-552,-384,1000,637,-1000,-106}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00248() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{-585,529,-51,568,-604,118,391,1000,-602,-307,1000,-666,1000,750,-204,67,1000,-1000,-930,-211,-396,1000,-1000,-77,515,-988,-386,-326,901,-501,-121,-1000,-195,-1000,967,-637,-1000,-278,275,1000,-678,22,1000,-830,-692,-900,-679,486,565,1000,-969,-1000,-405,-155,-1000,-152,1000,-185,1000,1000,856,-65,408,-555}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00249() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{-736,-511,-378,-882,559,-78,696,-190,-569,-853,738,-835,-452,-536,-941,100,-772,575,674,-224,258,-987,236,-152,226,-675,835,152,-109,938,376,-235,-289,559,-438,-663,263,406,-44,56,12,-381,85,-304,664,-212,73,833,-867,-293,934,479,87,-853,-691,148,-43,777,170,691,339,-843,814,-898}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00250() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{213,-53,-754,-724,-647,764,-730,631,330,-384,-566,-180,946,716,-45,785,424,3,-843,-297,-930,784,-312,696,57,-719,-983,804,-807,108,740,-244,115,612,-435,-992,16,491,315,-970,688,-890,-277,-120,-538,-442,-612,-283,428,-365,837,-862,228,-315,-675,-82,669,-543,-242,-909,408,-952,-112,-827}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00251() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{621,-569,449,170,-451,168,934,-975,78,700,-649,75,501,338,-204,-276,809,883,514,986,-396,-499,-943,-77,223,144,-386,-326,279,-345,875,-807,-568,576,177,-231,796,-848,68,-99,161,80,958,146,-97,-900,18,221,-653,748,155,786,205,-758,-473,137,670,-185,-184,-705,792,-377,263,-90}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00252() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{-669,59,-241,-457,1000,-482,-583,-542,-748,-1000,1000,-1000,-935,-1000,-1000,1000,-283,1000,821,583,68,-892,17,106,1000,-1000,973,-785,451,999,739,-74,-317,245,-1000,-1000,551,643,173,450,-327,-136,-251,-1000,222,-598,-421,1000,-712,485,1000,-80,6,-37,-411,852,1000,168,-186,699,-108,-318,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00253() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{1000,352,696,240,797,1000,-810,-268,74,80,-539,-614,-235,-1000,419,765,-416,817,-105,1000,-161,134,-477,271,796,72,623,42,30,273,963,818,-1000,1000,-1000,-844,239,1000,-435,1000,-405,1000,-93,-1000,206,317,779,1000,120,722,918,592,720,655,-819,1000,109,-633,-1000,740,-209,942,-479,-920}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00254() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDate", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "plusYears(int):org.joda.time.LocalDate",
            new int[]{105,261,-13,318,556,593,-422,478,70,47,441,-511,414,-250,486,-5,-98,-189,829,9,78,-640,300,328,428,-678,209,703,-126,-1000,162,430,54,-304,671,563,-1000,-360,-200,539,-116,-850,430,1000,14,130,-135,93,200,990,220,86,839,896,472,104,-497,233,-587,1000,-343,-542,-66,-132}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00255() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{-419,-868,372,421,720,943,-241,317,104,-781,264,-42,65,330,411,263,45,-212,-211,955,-275,273,445,-688,540,786,-425,347,-351,696,888,594,-169,-387,-779,-112,209,-148,-824,-639,239,565,-132,-746,981,-474,253,-917,-697,61,893,-817,378,599,529,756,-397,949,-631,-878,906,560,-386,120}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00256() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{836,-313,984,-490,-258,-859,-1,-148,957,-576,-489,-649,-526,-999,-883,-483,-321,-310,258,722,825,-294,190,-225,648,4,68,673,311,-218,-340,786,-715,240,358,-982,182,-546,415,278,-894,-424,-393,-3,674,483,914,-667,459,-530,207,240,-631,605,-566,-367,196,-28,335,-524,424,173,341,874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00257() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00258() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{263,-152,201,395,-19,-668,462,-166,762,543,151,-150,-913,-308,-288,173,-728,-196,-754,-775,-956,-75,-496,835,876,220,943,803,465,543,561,293,-881,-790,-636,-737,284,565,-421,964,-41,218,5,-74,316,-230,270,764,-492,303,192,-329,314,-50,896,501,89,-556,-72,984,266,723,-802,-188}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00259() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{-723,-800,-37,934,928,-953,-252,-479,-177,295,636,891,149,313,455,624,-385,671,-828,-705,-79,-91,552,866,560,900,-116,-77,931,-295,561,-435,119,-978,489,-988,172,140,507,797,-787,632,-681,-193,-697,951,803,355,617,-464,-34,982,197,-903,-677,-259,334,321,6,-637,407,-580,-453,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00260() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{-1000,257,-237,744,-1000,243,-392,462,-248,599,-260,-1000,-426,1000,-346,-537,-742,830,264,683,-116,115,-529,-407,-742,168,-1000,-1000,808,-719,388,805,682,-436,-416,1000,844,1000,584,-157,585,42,-687,110,137,271,-899,268,-544,485,-335,-1000,-91,-203,1000,667,-79,-429,-717,-203,-1000,-1000,190,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00261() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "property(org.joda.time.DateTimeFieldType):org.joda.time.LocalDate$Property",
            new int[]{-1000,8,757,223,-1000,-131,-868,419,761,-368,-26,-1000,-238,316,-1000,-391,-1000,196,372,-1000,932,-554,877,-592,-37,-740,-1000,-814,723,-1000,602,1000,142,-555,-242,187,1000,1000,385,-233,-365,-741,-1000,-586,1000,-21,63,255,-625,465,-678,-1000,1000,193,1000,137,-635,-689,-144,-193,-395,-1000,361,-407}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00262() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{397,771,735,-380,-531,559,158,-145,-794,637,-965,-219,-27,472,531,-892,955,718,890,747,-108,-738,415,951,785,483,-612,769,-289,460,-386,-156,892,-433,-612,-237,-72,-12,621,615,739,-987,-347,-813,-859,-301,-182,330,-762,447,233,412,845,358,-520,-511,995,-143,836,874,281,3,-433,-922}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00263() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{-110,-544,-270,255,1000,287,181,1000,-1000,757,-778,-883,-1000,-303,502,1000,-243,-1000,1000,900,-767,-72,1000,-48,-253,188,-193,268,437,1000,743,-671,44,1000,-520,-131,-1000,-800,-449,67,-1000,632,483,-506,-1000,-1000,-1000,-1000,-75,-241,-35,-962,-91,-181,695,-1000,1000,-1000,257,344,76,-279,315,-691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00264() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00265() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{1000,-639,191,398,506,361,-580,-635,-12,-1000,-272,-844,-477,-984,-75,1000,-1000,-1000,332,-1000,-606,-337,-197,-356,-1000,-642,321,-1000,259,710,-958,-548,651,594,400,-1000,-400,-306,-302,860,-181,243,377,90,1000,-590,377,-513,856,-299,-738,-854,20,49,9,-275,1000,-193,-171,-1000,804,555,399,-185}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00266() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{431,-96,223,-635,164,37,-805,992,112,-440,201,-29,920,-989,-596,-376,834,868,472,958,593,-250,-247,520,-269,18,40,-603,-183,-148,964,807,142,-356,-805,-157,157,595,574,-600,553,766,-943,-896,-690,541,-69,-866,261,-586,-863,242,-248,-646,744,972,-151,608,618,-881,192,-276,158,817}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00267() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{-775,-867,404,-206,-807,82,774,528,441,-968,635,-240,842,290,815,119,508,-265,652,950,133,-485,425,-148,174,-331,577,-694,198,813,51,-647,676,-268,208,416,-701,815,708,-171,520,614,-450,-572,390,495,979,-381,-954,-639,405,-242,199,699,636,-613,859,-145,765,-106,-826,359,-143,-510}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00268() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{-488,488,-332,916,935,-131,-512,775,800,-324,611,-412,202,807,-149,500,178,-952,-124,-523,-42,-835,-499,-296,613,-239,-864,-776,39,645,329,384,192,195,512,830,-729,-730,-55,-775,172,575,-319,676,-76,81,888,897,-938,89,-702,598,941,-164,-296,230,827,-842,475,307,-719,-345,866,-179}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00269() {
        org.junit.Assert.assertEquals("java.lang.Integer:Mw==", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "size():int",
            new int[]{-866,525,590,-961,-1000,-617,-984,-387,-726,-94,-1000,-572,137,-761,-131,-731,107,774,564,-378,826,-1000,-640,-1000,796,-1000,-615,-117,-299,839,-817,372,-461,695,714,-364,-588,694,-155,361,-152,-603,295,975,1000,181,-876,-1000,1000,638,-696,255,-714,962,891,834,237,793,-198,1000,1000,118,490,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00270() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{1000,-1000,978,-1000,-373,-210,764,568,1000,193,683,-155,-887,-597,190,-490,-1000,-193,579,-329,-276,-173,579,291,556,1000,253,-109,-360,309,317,916,-1000,-249,-507,298,859,1000,-407,-196,-105,-366,-847,-285,-146,379,140,38,329,-152,418,86,-63,245,226,664,1000,-57,-107,-500,-32,-477,-639,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00271() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{-198,-951,228,686,-87,-368,-804,-784,-998,387,217,503,-125,-816,781,483,593,-79,-213,221,960,-951,728,671,-503,-398,658,305,439,-410,639,-912,-212,-838,485,-311,767,756,642,-423,163,644,806,-289,12,698,498,882,-196,5,-782,-534,979,-946,-945,337,-115,583,628,-15,-483,931,140,-479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00272() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00273() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{338,498,640,-236,687,-663,980,732,-487,-195,-829,515,-263,-739,578,634,-605,389,-828,-548,246,871,447,-240,-45,-29,816,-371,359,631,-359,-755,795,189,209,804,-165,-327,924,379,866,771,-175,-366,936,-959,85,-294,22,-282,-397,-783,-543,520,28,-631,-354,304,-446,-589,378,-889,-255,-635}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00274() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{-666,-42,-335,1000,250,958,473,-59,114,397,-61,-29,1000,-963,19,-4,1000,691,898,45,800,293,-86,-331,421,-641,-1000,472,90,403,-493,1000,883,481,-32,-157,295,-651,-101,152,315,587,757,-304,277,1000,83,201,203,-535,283,-841,712,-406,636,257,1000,434,-1000,-1000,-581,669,564,-573}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00275() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{269,-887,-1000,0,705,-430,1000,1000,-1000,119,325,1000,-358,-715,1000,1000,-506,0,37,-734,1000,-14,0,-588,935,-996,85,-530,1000,1000,1000,501,-51,-67,806,-385,-555,546,466,664,351,-504,136,-1000,298,0,0,849,1000,256,0,-470,1000,-1000,-1000,-1000,746,168,701,0,-1000,0,425,435}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00276() {
        org.junit.Assert.assertEquals("TYPE:java.util.Date", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDate():java.util.Date",
            new int[]{798,382,-1000,1000,-818,1000,-1000,400,266,-2,771,-187,1000,992,-1000,-729,-135,458,1000,-1000,-758,-709,447,730,692,-580,669,197,355,631,-1000,-755,-1000,261,-915,432,-418,-1000,-95,-199,-196,-1000,797,-456,422,-664,1000,328,144,-17,1000,-357,-402,-111,596,-886,-354,-1000,-1000,-304,-1000,528,1000,226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00277() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{-404,-637,297,13,736,172,369,983,-453,612,535,-652,452,563,-923,414,-587,-951,-48,-924,72,530,-593,316,-562,-265,-629,225,-621,993,-231,-135,-822,-915,600,-754,-741,-51,79,916,782,-768,-568,212,302,657,47,131,903,421,605,898,-676,455,514,-131,455,258,-445,159,294,246,-44,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00278() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{787,-984,0,-176,366,918,1000,936,-1000,1000,-531,1000,-92,1000,888,236,-597,-87,7,1000,366,861,1000,700,-163,0,223,1000,948,0,81,940,26,287,1000,1000,-1000,-1000,2,196,291,-1000,249,599,-142,-291,103,-177,1000,-1000,-90,496,0,-340,214,151,-727,-1000,-223,437,-310,-611,-564,-28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00279() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00280() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{484,554,307,-476,389,-522,811,-232,-437,-279,894,701,-213,442,-745,-43,508,-720,633,-179,189,560,910,458,-685,911,-928,904,-976,746,-578,112,-643,72,-924,289,936,-553,268,-12,-503,-463,286,990,191,-282,-52,876,-225,-603,454,365,-58,-634,180,995,-126,355,-485,529,833,202,-809,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00281() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{102,-437,-254,-585,-87,925,702,534,-880,829,-137,894,-646,707,-275,734,-873,-550,-591,882,926,987,757,282,-247,482,278,832,720,-469,473,241,359,-277,740,534,-907,-709,-459,89,990,-787,-198,637,-688,-892,623,-462,705,-482,273,-193,870,-216,209,-117,503,-394,-633,-74,-46,-415,339,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00282() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{-178,662,461,436,561,1000,-1000,-994,1000,532,-1000,-1000,99,-268,1000,215,-852,-868,-1000,-875,-1000,-62,-509,-565,-343,85,-688,178,1000,-728,1000,210,-205,207,1000,-629,-775,880,579,373,1000,-706,196,-419,-425,1000,126,-1000,26,590,361,378,-272,399,207,-593,1000,-624,234,-459,-1000,-647,1000,-196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00283() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight():org.joda.time.DateMidnight",
            new int[]{399,305,-13,-533,422,1000,-1000,-1000,351,18,-726,844,-510,-473,1000,140,-944,-614,-1000,-210,-848,811,260,503,-763,1000,671,1000,1000,-615,1000,607,754,-364,1000,-699,530,-615,803,46,87,-1000,173,-510,-1000,-285,185,-881,-424,-539,-701,-886,202,254,1000,430,1000,111,-503,238,-810,-618,1000,-170}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00284() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{430,-1000,-711,-137,852,991,-456,92,-339,38,317,860,330,-870,142,-393,-573,337,510,271,689,-703,339,-782,-216,760,192,-222,785,599,-555,183,-146,1000,-495,1000,327,547,-1000,629,1000,763,-185,1000,-848,1000,1000,1000,-663,-1000,280,-1000,-1000,1000,228,-825,-881,496,1000,-418,-1000,-1000,-392,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00285() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{602,490,-138,742,252,-179,-772,-246,-891,730,403,850,444,938,-329,-294,589,306,-682,-867,32,-620,-246,258,809,887,-387,597,-259,688,918,-265,565,129,923,-964,-1,225,284,-421,-585,-375,862,327,930,973,-366,458,449,-110,-60,-282,482,-399,-375,682,409,-21,-508,888,-751,676,926,959}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00286() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00287() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{-36,-667,-866,-797,470,200,-837,643,402,412,45,541,-130,-506,-877,-720,-639,-170,408,-192,988,-115,-542,-351,17,349,548,-128,423,-595,442,418,-294,745,-455,915,188,450,-720,-170,664,383,186,688,-605,383,385,-45,-829,-814,857,-870,-547,774,247,-364,-560,620,-180,-859,-663,-828,611,136}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00288() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{-962,-152,-168,-266,8,-212,143,-6,-801,-334,-461,344,-446,-27,-713,101,-193,-639,593,-453,-881,-132,-491,500,-588,135,-55,-52,188,-878,618,920,-938,-797,-755,-514,309,-954,365,-447,-707,-776,-190,-68,809,-906,-990,-923,98,185,97,-182,579,-947,61,-559,174,-637,-879,-428,512,822,865,-593}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00289() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{4,333,-954,58,-669,-721,187,247,790,-490,-582,855,-45,751,790,529,895,-180,-630,766,-666,-55,85,-220,748,324,-899,722,-714,-753,138,752,845,-469,641,-772,-323,-488,777,988,497,373,-131,584,-771,-802,54,-833,-774,838,-283,-267,102,388,-443,397,-842,-131,20,-498,785,-393,281,462}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00290() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateMidnight(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{-998,-190,-693,1000,1000,992,809,112,-65,745,322,411,217,-138,1000,661,-520,-744,1000,964,-1000,266,296,234,-379,-194,-610,-68,1000,1000,-1000,-370,237,-449,-1000,301,561,-1000,407,1000,-603,-1000,-134,-904,-154,-268,-890,1000,-42,95,-1000,-594,-1000,-98,-372,861,347,-659,868,-189,241,462,730,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00291() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{-720,-1000,249,-797,91,-1000,-891,-1000,-80,436,94,140,304,-103,-328,-608,-969,-967,478,-364,-855,-825,81,-589,346,253,-1000,549,445,1000,-388,49,-643,-778,1000,958,1000,-1000,811,-1000,-324,541,-426,753,598,-439,441,1000,448,92,1000,-1000,-1000,-1000,1000,1000,1000,1000,-282,316,661,717,514,278}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00292() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{845,-467,-747,473,0,1000,-642,-618,-1000,284,-816,57,506,-1000,-281,364,-1000,0,198,-544,-95,-1000,0,0,-501,728,1000,-615,58,116,421,72,753,-1000,167,-1000,-266,-218,695,182,-1000,580,0,2,-282,404,263,-941,-393,1000,-191,-1000,263,1000,229,-476,-506,-136,-467,0,-1000,-912,1000,-572}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00293() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00294() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{-1000,-937,-403,346,-502,-587,-614,-887,-1000,55,59,1000,389,-566,-704,-1000,-653,7,40,49,-804,214,-523,-719,709,797,-558,837,-143,1000,-107,-759,-1000,-845,1000,-501,319,-315,1000,-769,-172,-571,-517,431,325,-619,394,488,278,418,1000,-1000,-1000,-391,788,210,-47,707,113,5,1000,759,-108,791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00295() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{594,565,-621,6,-750,502,551,-227,413,880,979,-134,827,951,168,247,871,694,-969,553,285,406,-610,-249,643,882,-90,249,568,449,158,-122,-327,140,945,775,629,981,-108,610,-585,5,238,839,-187,-66,312,678,-482,-302,812,372,-166,975,-957,1000,889,-268,-379,512,-482,-594,-210,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00296() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{961,679,-72,-540,-973,-277,-243,-287,-265,472,-859,-420,-573,807,252,634,16,-628,726,226,-956,-989,-822,-907,697,-743,795,134,166,112,151,683,-193,306,831,276,920,-17,251,262,-356,331,-886,895,-520,-945,387,-463,224,801,350,485,-252,-365,327,590,979,515,39,-684,-330,313,160,-823}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00297() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{-482,146,29,-543,707,-512,108,-986,862,78,978,-330,568,739,771,489,-660,-927,-365,-684,426,-867,-125,-654,-714,-362,58,-271,962,297,34,914,-393,720,288,990,783,-807,-205,-275,-124,-859,-344,226,-94,-510,-805,175,-630,-650,896,-529,-659,-569,588,741,986,453,-362,-299,-67,404,546,-396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00298() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime):org.joda.time.DateTime",
            new int[]{-337,629,42,1000,-1000,997,-799,-531,1,472,523,253,-461,2,-348,-576,-54,-628,-960,757,-492,217,-335,-272,44,-640,736,692,-1,703,302,-159,-193,-180,112,-2,-107,588,465,1000,-1000,196,1000,895,-520,-946,745,-143,556,160,1000,-933,-706,97,327,679,303,54,-396,-684,511,708,-441,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00299() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{979,-1000,27,-292,-868,1000,1000,189,220,-887,-1000,576,1000,666,904,-1000,-1000,-78,436,1000,301,-1000,-1000,1000,409,1,-1000,-1000,654,587,-1000,-1000,1000,-1000,-951,193,72,1000,-1000,1000,-889,-543,1000,-1000,-1000,-6,1000,-325,582,789,1000,-1000,470,36,-65,-881,745,778,1000,564,-1000,-378,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00300() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{380,446,-354,1000,-693,838,-644,-676,-72,183,1000,-377,1000,1000,-186,802,381,324,271,653,1000,-958,-932,-1000,-82,-39,1000,1000,540,1000,-1000,391,-286,-631,660,764,-771,-1000,-143,476,-347,-543,-1000,-30,-1000,-71,1000,1000,-284,-1000,1000,525,-891,-294,-1000,-404,-239,-156,-321,-887,590,70,381,486}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00301() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00302() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{712,-997,409,-607,-797,423,488,-662,-33,-957,-297,-692,200,803,997,520,237,-18,616,323,518,87,-600,861,-182,-835,-812,-899,-531,938,506,-828,619,800,163,-115,648,513,-403,633,818,-336,931,-598,-90,-572,-447,-436,192,980,-251,-680,387,-971,-586,-811,-530,378,-791,775,-857,149,630,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00303() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{327,-635,433,-43,248,549,-576,255,-433,-839,-655,475,-993,-540,170,-391,451,191,-238,-52,308,-506,-975,-719,462,459,-611,207,-436,36,-678,-709,744,583,17,-283,-97,-788,-357,406,-330,-752,637,134,-161,636,-610,-920,-363,-84,391,468,534,-463,-630,-530,455,666,350,-610,-731,-564,-382,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00304() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-459,585,213,-53,220,-647,506,-600,-1000,188,967,613,200,159,917,660,-193,1000,-519,-573,627,-1000,980,-1000,-734,64,583,-361,731,-502,506,66,267,-479,-808,-480,-341,-748,-493,-785,-404,-336,182,-598,-1000,-523,-447,-309,1000,197,-1000,554,-612,715,-1000,-400,481,-959,-153,689,-539,-2,535,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00305() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTime(org.joda.time.LocalTime,org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{1000,910,-11,253,-242,646,-477,1000,-91,382,-160,408,810,914,-1000,-831,300,-1000,0,-1000,658,1000,-1000,406,1000,-940,1000,821,-932,99,1000,119,-1000,-388,1000,707,781,587,1000,352,-210,337,-619,1000,1000,-411,369,1000,-409,-1000,983,154,130,-1000,1000,1000,-382,-312,-593,217,705,-277,-1000,-995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00306() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{1000,-1000,957,-315,-1000,446,898,-510,970,-634,1000,379,605,-215,21,8,886,-313,339,-1000,-50,-253,599,-1000,579,-1000,100,-504,1000,-465,-632,1000,-478,-486,-876,1000,566,84,1000,925,999,-21,412,705,658,-483,451,-779,-1000,455,1000,896,1000,1,647,-187,-422,1000,985,-259,252,569,-769,-232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00307() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{-342,-280,-333,517,-220,-899,664,-766,-728,170,283,971,-492,-893,-754,-416,-341,-921,-648,-448,831,704,-977,377,-717,-508,241,96,-618,373,851,482,-319,553,352,-715,-419,870,-203,486,-610,619,-846,-62,-713,-657,598,-987,683,-495,-412,887,787,136,-681,988,-213,-420,118,769,24,-755,-246,438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00308() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00309() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{935,-942,89,-315,-800,446,89,-950,324,-906,217,772,605,494,776,8,-501,-675,-641,-677,960,-304,622,592,-344,21,-858,-530,-694,989,-547,849,122,-798,485,-496,-596,-988,-916,727,-294,852,-895,-805,658,-81,597,504,-449,455,73,874,59,72,-499,367,144,-422,720,-362,-943,569,-676,-297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00310() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{-313,503,406,180,-917,-342,-655,426,-57,-186,156,344,796,955,423,-942,-40,232,-428,-531,536,309,-246,871,103,722,-45,-685,-9,-697,-934,751,744,894,730,-212,-558,501,313,-497,920,-204,69,-854,611,-531,688,816,-275,-716,-191,-764,-220,923,776,594,-868,-314,-27,-915,-915,-39,380,-720}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00311() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{-425,-316,-987,645,337,125,-905,446,716,195,105,-955,630,-666,-834,-391,588,519,713,713,496,-130,-117,620,-79,808,360,68,-891,-233,-182,-453,-181,617,-753,821,84,875,-80,-914,-641,575,-463,-474,226,200,-863,305,-181,-495,828,-967,401,227,-955,-367,197,703,-915,-831,479,-774,39,-294}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00312() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime():org.joda.time.DateTime",
            new int[]{-833,-372,831,528,742,995,102,-821,-833,-295,890,-308,671,37,-291,-918,750,-514,460,764,545,631,895,-145,-109,880,811,542,-264,783,-965,-372,-606,692,401,-808,145,533,362,878,68,191,-309,777,-334,589,777,-956,-153,-67,453,493,-881,-227,-20,-787,-499,-431,857,-50,538,243,-541,-532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00313() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-865,-1000,-327,-1000,-369,-171,240,-218,-733,1000,723,-788,-775,563,-1000,-1000,1000,352,470,877,694,868,-464,-1000,-554,-793,-502,-962,-1000,1000,1000,1000,396,181,1000,853,-1000,-692,820,1000,-1000,-1000,-903,284,-21,137,858,1000,-677,363,-839,815,466,-978,-1000,435,-824,716,1000,734,782,-967,985,-213}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00314() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{1000,-82,831,-856,-785,-845,-694,184,263,379,-1000,539,1000,400,567,1000,-332,121,-635,340,-799,501,1000,1000,-548,148,1000,-96,1000,412,-712,-891,589,-608,587,190,165,602,870,-790,-67,161,247,-1000,-981,-592,476,1000,-350,-94,-587,929,-562,1000,-164,-568,371,-674,-1000,-636,-33,-289,518,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00315() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00316() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{495,575,-337,-9,-804,698,893,130,280,389,-267,-492,42,-32,-560,712,510,-800,-868,993,-158,-645,-86,840,557,-999,280,-428,-336,-386,64,440,460,-667,-254,134,-330,315,781,-94,-50,-951,830,-467,291,-261,-761,-719,-195,365,928,691,-414,-562,978,529,830,574,-380,-416,-707,52,93,-902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00317() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{322,24,-898,-166,-61,-963,486,-701,-868,-490,152,738,-937,-28,440,-41,-6,-456,180,752,-870,-194,456,719,-37,-618,-13,375,729,80,-606,-746,273,711,205,240,545,830,314,574,243,-477,771,-925,-287,137,-692,-2,-910,354,-223,630,-91,388,-169,273,924,233,-421,540,-299,-123,-295,-941}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00318() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-644,635,991,-454,-265,253,-83,197,632,-217,-349,-104,114,-64,-693,984,-316,-773,354,-618,-420,647,-295,493,711,-771,876,352,493,-25,118,402,470,884,992,-264,594,-49,728,-132,301,-106,878,217,96,117,-724,-666,-701,-50,133,-388,-242,559,190,-263,-74,-922,-684,-551,480,-477,-356,-683}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00319() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtCurrentTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-699,-29,-484,-437,-283,-993,-1000,-173,204,192,-743,-552,375,1000,-13,736,-60,380,-563,1000,-713,634,205,110,-229,-243,366,230,1000,242,-588,300,-15,-825,-555,1000,-272,200,758,-657,-259,574,-1000,-17,-629,-377,360,871,-72,12,-60,590,-210,538,222,620,-745,999,-662,-663,381,16,1000,207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00320() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{526,-1000,558,728,-785,816,-764,-265,-20,847,-289,-1000,-508,-653,-222,-662,988,193,1000,-576,467,210,1000,-227,-955,-605,-392,-325,1000,-767,-1000,-721,-1000,1000,281,-14,-258,462,512,-823,638,419,196,1000,523,-1000,-249,564,749,391,629,400,210,-586,176,-654,31,77,-721,-341,-162,-660,-225,727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00321() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{954,-929,-879,-857,-402,989,937,1000,479,-258,-897,-810,239,-977,-215,-363,-926,-397,1000,732,912,-755,-295,-17,494,-570,-1000,-696,-123,-996,719,420,213,76,818,75,-1000,808,-350,-141,467,-1000,-827,-1000,1000,-858,-671,-150,-308,-1000,142,944,1000,1000,-1000,439,-739,946,-1000,920,-45,-534,-271,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00322() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00323() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{822,-177,-657,-360,127,-314,-480,-287,-399,334,221,-731,932,-589,-578,-218,-156,295,174,-281,-361,-669,486,-327,97,-836,-19,-171,700,284,-354,-454,-874,-839,-269,-820,-64,186,650,121,512,85,608,490,-611,-627,-409,895,379,26,338,-729,122,-424,988,-104,-973,-454,-827,-587,-2,329,-473,-95}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00324() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{799,421,-619,-603,369,-626,-133,378,-872,-886,785,693,623,-247,-554,-91,-569,45,-143,657,-639,-487,-950,343,175,-246,-223,927,-856,841,203,-639,-332,-993,-50,-385,-784,659,-320,-908,942,-833,235,-332,-549,847,-707,602,-602,-907,467,-890,-107,707,-142,550,-21,127,138,-165,879,-507,450,936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00325() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{-946,195,738,526,-818,-509,-252,572,660,743,212,-248,-472,-427,261,521,145,-521,-485,-950,100,105,537,-481,608,325,123,-115,219,133,-389,815,-331,-423,154,-255,-326,-143,674,-57,-412,-910,-890,-135,964,-776,-554,-875,267,-883,46,-76,-740,-104,840,956,-83,357,13,449,-915,406,-949,310}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00326() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{200,-326,-1000,0,0,571,-268,-830,-1000,-776,-967,574,-16,86,-345,-614,412,988,336,1000,-424,1000,0,-957,463,-1000,1000,153,-850,0,-331,-452,0,1000,-57,-322,817,-293,118,574,1000,-170,735,-624,-631,692,433,0,-331,-252,-109,1000,-504,388,93,0,846,-276,232,-88,6,-400,594,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00327() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight():org.joda.time.DateTime",
            new int[]{70,-889,-358,-2,-181,-473,-252,-131,508,743,-952,-938,923,-220,-613,521,145,473,295,-908,255,-769,1000,-820,142,-821,555,-532,1000,-419,-723,815,-972,423,-671,-900,860,-771,1000,1000,-65,1000,-81,1000,964,-633,650,454,199,1000,-30,-76,408,-543,809,-349,-1000,-465,-830,173,-818,1000,-651,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00328() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{140,573,-741,0,1000,0,-942,957,-803,46,970,-850,-283,617,-412,69,664,-209,752,-1000,1000,-44,-246,866,-361,-1000,0,-142,-1000,0,1000,988,-523,-233,926,110,418,919,-564,1000,-188,-1000,1000,711,1000,1000,1000,695,-79,535,-528,931,834,0,479,-247,-108,-1000,464,-630,962,-221,1000,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00329() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{972,534,246,-313,-895,496,-248,299,205,789,821,-294,449,984,496,-354,795,137,979,-79,-795,-421,942,614,140,579,562,-443,256,618,-949,615,-882,953,-983,-894,-517,-678,317,862,-852,-803,-748,791,-258,823,925,-373,943,-593,-637,-468,-443,327,-362,647,-344,-30,590,-669,537,-84,36,837}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00330() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00331() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-772,-206,427,-449,-453,606,-190,-669,329,505,-555,-321,-944,-406,950,-32,-695,-955,404,938,-90,-520,-397,420,-117,97,102,894,429,938,-264,-425,288,626,-165,-453,-659,88,733,-440,-91,-678,-696,546,-488,-899,-741,664,973,643,720,-277,-274,-242,187,215,-81,-245,-555,-139,480,-537,165,-419}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00332() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-270,-854,19,-909,270,-220,-51,-44,930,209,940,859,-642,13,83,98,-524,-416,30,-145,476,878,-957,-315,278,547,748,826,464,-194,-563,441,-434,229,306,-304,-700,-89,367,-282,-165,-31,930,887,-483,-840,552,-328,-405,573,117,-725,-537,801,-682,-282,647,779,255,-317,404,562,-952,420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00333() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{436,-344,-256,-154,1000,-1000,833,-215,445,-173,-311,-475,1000,-319,-601,-374,-1000,25,-448,-1000,-637,-336,1000,-639,460,400,98,-189,-51,-537,-224,-66,156,176,-482,-198,324,665,-743,-120,-295,142,508,241,-358,-142,1000,-71,-1000,-643,-872,894,891,869,-501,142,-1000,267,427,341,-1000,490,-789,-306}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00334() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtMidnight(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{650,321,-909,-74,1000,-300,-1000,633,-1000,-847,1000,219,-996,16,-868,1000,164,837,-160,-718,1000,1000,-1000,594,-1000,-1000,-1000,870,-471,-624,1000,1000,247,-617,620,1000,78,361,517,1000,449,200,651,-403,394,-201,379,1000,-355,506,1000,994,-1000,-1000,1000,-812,1000,-1000,174,-277,687,-1000,416,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00335() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{324,353,-696,425,-57,-277,-955,519,-159,282,319,-984,-718,73,-354,383,-565,30,640,-266,-834,225,-171,68,-300,-804,762,309,-52,-722,254,661,285,-65,-469,-10,806,-970,-801,65,705,684,435,-646,296,-75,-345,774,441,673,-27,-699,-362,781,253,661,879,-175,-748,-512,556,135,448,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00336() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{317,666,657,229,659,35,608,894,-856,-799,-666,-270,-823,-542,315,-12,-641,191,164,-707,-44,-989,-971,801,534,720,414,-843,10,247,-724,-553,-621,770,-903,616,619,-811,-174,-124,875,799,-915,-471,924,-393,821,326,-857,40,726,-30,702,-6,169,-271,582,127,122,238,122,113,-366,-487}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00337() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00338() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{-995,-195,-227,632,-371,399,330,348,957,32,169,-429,759,-719,557,-714,-532,-245,780,-544,-671,-543,-889,-631,303,-417,-817,-519,222,-435,-241,-366,-420,-711,-173,-194,702,306,80,610,540,-380,368,-349,320,133,496,543,-584,-782,877,-99,-611,-959,-42,-58,957,-770,-892,461,-195,285,696,-87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00339() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{406,981,-505,-369,895,-592,-218,-800,-516,-588,-666,-733,-144,24,821,846,-262,55,542,-498,-913,39,50,-250,-250,854,-998,-44,599,-362,315,915,106,678,-451,-458,572,898,624,-829,844,290,-507,-149,-770,-836,157,-268,-443,470,-414,116,818,576,-521,119,711,-92,-407,-32,-487,-400,-612,945}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00340() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{376,950,176,966,260,126,-493,-891,-278,440,571,-1000,610,464,1000,-579,1000,-801,276,672,1000,-123,-912,4,-884,-797,-79,134,496,-853,402,-528,-276,305,498,-43,-890,-1000,94,-287,-365,721,489,730,-14,-968,-1000,323,-272,241,-1000,-664,42,-534,-1000,-807,-687,-218,-1000,481,-214,-135,137,-727}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00342() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-1000,397,-207,627,1000,1000,1000,-641,-76,634,-363,849,-561,1000,250,137,-667,40,1000,-444,-484,823,-313,-389,-1000,769,63,528,-274,-996,249,1000,-1000,-1000,455,746,814,-549,233,-1000,-846,-134,1000,1000,-1000,769,-373,655,-1000,95,1000,729,1000,365,364,-541,795,-760,-1000,723,1000,-1000,-613,-157}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00343() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{739,-918,-840,925,-147,985,909,892,-526,187,328,-426,417,-386,40,325,-13,-126,-410,-719,384,378,-737,-410,247,216,-764,-815,-541,555,-582,868,-543,662,-1000,642,980,44,955,358,441,-359,-491,18,926,-445,-47,-666,195,-662,78,-453,3,-461,970,892,559,-102,-47,623,-990,348,56,-983}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00344() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00345() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-395,179,764,-39,832,-544,522,522,375,795,-814,-515,868,-920,94,-299,751,778,-798,-97,-305,726,-742,80,735,778,-802,-680,888,728,-244,-486,91,207,-72,-521,51,690,-856,209,-216,-822,825,-807,-496,-364,289,50,834,869,-81,-901,-703,140,972,868,536,547,427,98,747,949,874,272}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00346() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{474,-20,-972,-682,-41,-137,-843,-82,252,746,-751,866,648,-360,-488,-191,-740,808,862,421,520,-917,-599,485,866,-326,149,120,508,-5,-861,700,-765,-642,665,134,-15,774,651,324,-921,-451,-79,-102,891,-90,496,-424,-921,-568,-69,892,434,-284,-795,-543,925,453,683,-633,574,794,283,-9}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00347() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-991,162,504,37,570,-694,-937,-775,-220,735,896,-993,694,-864,885,-148,900,-578,-577,883,895,-97,379,-594,403,267,344,-567,544,-539,317,293,935,866,687,-415,451,593,535,838,-342,-861,-881,-455,-285,-814,701,-522,958,903,-185,598,272,751,540,267,-107,484,364,975,-653,-173,830,329}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00348() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toDateTimeAtStartOfDay(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{-21,367,471,-88,381,431,-420,-891,-229,-221,461,776,-506,-988,-882,-147,-738,487,-114,598,-43,786,216,749,-50,527,-470,543,-468,507,-524,106,-827,515,-426,845,-1,485,-545,-355,-837,-798,693,914,553,-804,-440,823,711,131,955,425,723,555,-782,-204,-615,-345,-416,-204,995,370,623,-375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00349() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{958,-879,522,361,-355,-450,477,830,210,633,323,-154,-822,-91,-46,833,618,508,-46,467,22,584,-611,-74,847,186,950,350,-362,-540,-266,-94,477,-741,113,625,-504,137,-406,790,-400,509,-630,-937,210,-714,315,-214,842,714,617,903,624,-676,911,827,499,925,850,242,774,-636,-660,-724}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00350() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{-589,28,357,327,369,676,-681,-689,733,-224,-665,445,142,909,377,-292,491,-214,-585,-607,-104,202,421,-178,-754,-143,62,-1000,718,675,1000,-222,65,-23,499,260,-496,137,682,-1000,825,-185,-91,561,771,991,-99,-180,141,378,185,-961,490,919,-439,-726,-558,-484,39,-552,-1000,1000,802,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00351() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00352() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{162,728,698,-538,561,-561,-474,-158,-796,-566,780,-421,425,-889,-555,-118,-699,-639,137,-138,773,-881,-59,714,-548,-543,101,-198,232,535,-501,946,-437,-119,27,984,314,399,-569,-701,-486,268,947,403,-69,473,-821,-86,-524,-917,33,-711,678,610,-744,540,139,-244,-850,811,792,-279,360,205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00353() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{2,-338,218,379,-223,-503,874,522,267,59,127,700,-301,-627,719,867,-412,955,-488,-567,-592,886,-893,771,857,605,-623,211,656,-933,-321,-855,549,25,-347,614,252,166,5,383,-565,-438,404,-75,538,-494,-243,-71,-822,149,478,41,-741,-419,-858,354,-545,991,862,860,737,-624,141,533}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00354() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{-414,37,-66,-970,690,-616,331,-71,-822,-580,944,-174,-60,-122,-355,735,-394,-235,180,27,-384,242,-653,-245,-536,-867,-552,-594,-876,-736,-157,257,-618,969,583,-715,-126,-966,570,-806,-997,127,444,528,-434,322,519,75,945,-289,463,807,1000,313,136,458,431,457,43,485,-684,-302,-842,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00355() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval():org.joda.time.Interval",
            new int[]{465,74,587,87,988,61,297,272,-259,-545,39,445,-269,1000,15,1000,116,610,-493,-453,342,80,28,-139,81,-205,538,-1000,-849,-915,888,157,-733,-554,293,-298,-496,-893,826,-1000,276,896,-91,561,-86,17,575,-1000,790,626,1000,62,1000,730,406,370,-607,189,-27,-136,-982,40,-107,344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00356() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{666,-879,-273,666,-349,658,-466,468,941,992,-681,580,-711,595,718,-734,-151,887,-283,893,-822,-893,-636,-674,624,979,37,-973,-350,-668,-510,-927,-853,-668,-955,-198,-860,-128,-721,-505,-613,-253,348,-413,-645,775,705,-743,-203,-251,-643,-76,-285,-779,-935,-811,-83,-497,908,44,-467,991,510,813}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00357() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{553,-500,756,-728,-1000,617,584,830,-860,1000,621,431,297,-926,401,859,942,688,-1000,264,-922,98,-651,-667,1000,-120,1000,-390,-385,-285,-338,-216,-709,-833,-442,-385,-811,-911,-640,905,-366,-400,-642,1000,-346,41,337,-898,389,-104,-1000,-1000,897,-46,109,1000,257,-615,-1000,-296,9,903,-258,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00358() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00359() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{56,-557,597,-977,938,536,125,-602,23,553,-39,360,-480,-677,-797,-19,612,-443,-701,964,133,506,-724,-870,-227,786,692,613,-363,40,-700,-980,-490,668,-427,-717,480,645,234,-342,740,649,-294,-617,308,-861,5,877,167,-174,33,46,-79,-939,434,261,419,62,218,861,23,611,240,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00360() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{126,487,440,-831,-434,893,397,679,336,678,-188,19,457,-866,-92,176,-51,180,-385,277,-644,-370,-257,883,878,42,581,-629,912,556,371,-82,-688,-354,274,-980,-981,-453,438,-787,-189,282,-440,736,-99,652,930,-765,871,-639,-626,-705,962,-496,-256,571,-811,60,618,-389,-114,759,763,63}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00361() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{284,-592,93,543,-231,1000,266,951,881,1000,-464,187,-435,355,811,-119,-531,581,-639,1000,-1000,-1000,-727,517,967,-394,357,-887,825,-406,-230,-618,-1000,-313,-715,-279,-1000,-74,-399,-372,-1000,-239,-200,655,-54,1000,742,-541,849,741,53,-495,756,-1000,-1000,-308,-390,28,659,-630,-476,1000,1000,229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00362() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.Interval", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toInterval(org.joda.time.DateTimeZone):org.joda.time.Interval",
            new int[]{-911,-333,834,170,1000,1000,121,-1000,427,1000,202,-303,-460,-695,401,-162,-483,688,-7,328,729,-473,24,-667,-1000,1000,881,1000,-972,488,-13,-216,-709,1000,-442,-357,582,-911,-640,-1000,764,914,-759,-367,1000,-402,-326,1000,627,-784,258,756,-318,-1000,-864,1000,26,248,171,1000,-271,-39,379,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00363() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{-1000,496,-69,-207,-128,1000,880,-1000,518,520,729,-983,778,728,-1000,-411,-658,-813,1000,1000,101,404,-997,40,-1000,-345,-553,-532,-194,1000,1000,-829,583,-1000,-225,740,254,1,-1000,25,-846,-920,-426,58,-189,-452,230,-92,621,-302,-934,-990,1000,488,-981,-1000,-994,-175,450,-1000,208,-630,-1000,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00364() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{664,501,-78,-408,-252,-496,-623,-107,-639,-453,402,-801,414,-357,373,-509,-924,478,-432,985,-132,-602,-204,237,584,-677,-462,-707,780,-283,-805,211,568,-959,-212,578,-576,-76,-768,568,-174,747,203,479,-321,254,503,-290,680,-614,837,-288,-957,575,-35,552,353,-543,-715,-556,-319,-884,101,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00365() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00366() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{-3,-740,180,-366,-702,503,900,-706,254,-885,-349,-838,-317,-915,765,624,-655,-649,-451,32,-302,289,289,748,-83,-367,-527,-738,963,-478,-116,275,-496,-362,973,940,-536,-722,-291,-635,-923,-430,-36,871,461,39,-573,-454,-69,-731,998,174,538,-525,705,751,-346,-522,260,495,-191,-207,-440,507}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00367() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{-952,773,441,-928,275,482,1000,182,868,-237,-1000,-336,930,1000,-217,1000,-1000,675,-325,1000,124,892,-1000,560,-43,926,318,-314,-599,-867,327,-621,1000,-17,-675,-92,-677,-272,-1000,-862,467,570,-957,1000,632,61,-393,601,-1000,600,-150,844,-1000,947,-590,1000,-688,1000,545,459,312,1000,700,-268}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00368() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{407,235,-1000,-494,325,-135,-817,471,261,78,416,-227,-227,1000,-147,-411,-501,-307,340,826,687,-1000,1000,-236,230,-1000,358,1000,-749,1000,-187,-1000,-124,1000,243,-76,388,567,1000,-1000,1000,-1000,-274,1000,787,-184,1000,-1000,-62,-1000,245,221,-1000,172,-1000,-547,-766,-275,649,-1000,-903,-854,1000,225}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00369() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.LocalDateTime", DEReplay.run(
            "org.joda.time.LocalDate", "org.joda.time.LocalDate", "toLocalDateTime(org.joda.time.LocalTime):org.joda.time.LocalDateTime",
            new int[]{-650,816,-536,1000,443,-1000,-585,1000,140,825,-363,59,864,1000,793,47,86,897,-581,924,1000,-1000,349,805,860,206,1000,774,-1000,638,708,106,1000,614,-1000,-1000,-455,1000,344,-314,1000,-474,442,268,-123,774,1000,1000,336,99,-468,-670,-1000,-57,-566,298,-897,461,-822,-541,885,-58,1000,-1000}));
    }
}
