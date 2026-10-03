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
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "append(org.joda.time.format.PeriodFormatter):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-601,517,-391,627,314,477,861,528,-573,23,-474,-582,511,-480,492,384,-457,-207,-101,-554,-529,232,636,-385,-771,149,922,-961,843,781,233,213,1,312,926,-943,633,-449,831,-76,-814,-523,-191,-992,-874,-198,-174,499,912,-130,-576,915,-808,-236,-335,-531,-706,330,-438,106,-784,-43,865,-139}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "append(org.joda.time.format.PeriodPrinter,org.joda.time.format.PeriodParser):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{935,-961,-204,581,-266,-634,-283,-220,-869,797,-964,673,-606,797,-26,676,-550,-892,-959,-17,912,-631,532,689,816,-419,-296,-424,96,723,-186,-940,-769,-5,142,-279,557,314,-318,-179,236,-986,-746,274,53,-9,-519,-376,-125,942,-735,793,-134,803,-815,-446,-642,10,612,24,673,-227,207,814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendDays():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{964,-831,-679,462,690,189,460,-54,-988,-784,-829,380,-337,184,-828,-684,804,229,258,-997,-459,-85,485,536,440,36,-653,-217,-344,89,-523,578,610,465,7,165,596,874,460,-127,174,-364,-203,631,772,31,-915,62,500,-836,125,-347,-117,-516,349,734,-51,-537,318,881,889,-217,216,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendHours():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{419,-858,-186,-583,-73,650,742,253,-56,843,268,931,394,879,-154,-164,548,-526,-963,720,708,179,-569,-623,-837,590,3,867,252,-438,294,622,175,-181,-725,988,-150,-966,70,336,543,389,-229,731,257,-97,-444,328,965,423,984,175,615,-948,-55,-111,-430,11,33,798,628,460,340,580}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-22,-8,-827,-94,581,-224,841,147,-771,-548,690,-945,-275,-758,-678,335,810,-978,2,-916,655,471,760,317,-413,-55,305,619,219,-989,611,870,-496,704,-431,552,-601,15,-626,941,-204,824,513,-307,-811,601,-890,734,-877,516,-827,-810,-105,318,867,-844,-762,317,-711,501,690,-877,-537,-358}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{10,841,-549,653,-128,768,-42,196,-580,194,519,986,255,467,-513,-557,906,-998,429,967,758,791,-16,667,-489,-784,655,97,-24,730,1,-399,-659,-901,259,364,225,-677,702,-856,-200,-911,-807,-238,-929,698,-654,941,992,733,-549,-182,-485,457,910,562,-92,887,-710,-67,-299,837,335,317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMillis():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-229,-378,-177,128,-81,-581,-709,-649,-335,-433,339,-975,321,668,-51,200,-595,-31,-213,870,-748,133,-927,168,-702,720,915,139,-298,-717,-983,-569,-791,-316,-439,850,-879,246,-285,-336,111,-275,-319,-198,-779,716,-477,599,829,66,-913,-315,-340,162,-745,-282,-44,-466,-499,215,815,113,268,503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMillis3Digit():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{601,121,-376,517,-483,93,-936,-840,73,-612,268,417,-679,-948,-134,-246,-125,788,284,606,136,-583,-920,585,-854,-761,-696,-495,328,813,-704,-831,-661,813,896,-146,-159,-59,88,496,-868,161,-198,651,-752,956,800,538,418,720,-658,639,558,-51,-287,689,859,-535,-451,-775,-509,-470,420,-736}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMinutes():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{185,-354,-588,309,-977,-84,-43,159,-614,-67,275,940,-67,129,-180,972,-310,-597,-53,-720,-663,721,599,999,5,-331,-400,266,104,-460,-314,-553,900,-260,822,366,797,-840,-81,508,552,503,599,-877,-954,-483,336,575,-361,568,974,-628,615,-925,-576,857,-196,-75,136,-281,-960,-788,-132,-773}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMonths():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-563,592,-563,77,438,-665,-797,-671,114,-854,91,449,19,104,135,-321,-57,-704,-186,681,975,702,51,-126,917,845,-271,452,-488,283,461,452,-353,379,-427,-565,919,295,51,575,-191,615,519,235,32,-373,-735,-509,-383,-84,-229,-528,-244,620,-756,879,503,489,252,-395,85,700,709,950}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-707,516,857,-39,-620,-460,546,-413,-879,-121,496,-921,-960,-452,49,769,241,990,535,-150,670,338,-797,366,297,-96,-180,-83,829,-746,-8,-980,531,-380,-341,116,-948,-410,-71,-912,786,-976,457,782,198,989,878,-455,820,101,-509,37,-842,155,340,730,-375,144,93,383,815,-133,748,518}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{514,15,478,690,-419,642,112,44,-1000,799,129,67,-738,-154,-391,576,1000,701,808,-172,-130,-3,55,-431,-225,457,774,-706,170,-288,297,-151,-400,124,83,-172,-519,-138,716,-495,-516,-84,-409,728,-329,162,457,-394,-56,469,-1000,-790,-130,19,-590,44,-774,260,-677,-61,756,-329,-393,-859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{8,809,803,616,224,193,-582,-284,846,803,-319,-976,-756,750,579,208,-868,-466,-864,965,752,682,260,-86,358,281,-609,863,392,706,254,336,-42,989,-726,669,6,538,-954,-485,961,-810,-409,-817,446,-26,103,-131,-765,-653,-621,-458,573,-826,185,30,-369,-717,-546,-348,-1,-995,378,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-820,202,81,-672,728,690,-971,-387,272,-586,-307,771,615,-293,699,-504,-720,-777,-615,-995,792,-492,-537,-862,503,-306,72,155,-480,-704,-303,801,568,-747,-280,-376,-653,-398,-834,-907,-665,469,569,616,-135,420,852,-799,-222,643,603,-780,27,913,175,-499,126,-153,-292,790,-417,-434,315,-463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-170,-247,-117,-1000,878,-531,-240,581,554,-168,445,-17,196,-940,-509,-1000,-1000,1000,261,919,442,-411,1000,67,-551,-737,-484,48,-256,810,156,-941,34,1000,254,-235,1000,578,-582,-460,-287,-936,207,0,505,-787,-1000,-986,-1000,804,466,738,-97,-200,-173,979,431,1000,921,-161,510,-925,-228,-782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeconds():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{232,114,331,-465,-341,-365,456,-344,-926,-55,937,-334,-980,803,-336,319,-809,-973,-967,678,611,995,937,643,-929,479,667,659,675,514,830,247,-680,891,472,198,-529,894,78,543,-323,-102,317,-251,962,611,-953,-38,-605,866,-639,600,-16,151,-734,98,-667,320,976,-811,-546,329,716,763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSecondsWithMillis():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-653,50,-386,-590,340,938,659,228,-76,-807,-653,378,-214,710,481,881,851,-501,-823,-868,-727,-954,-723,-253,-438,-262,-265,560,995,184,4,-835,184,-1,382,648,375,952,285,-963,872,246,522,-631,-166,127,762,281,609,41,610,528,-219,-887,-38,965,110,-156,-364,143,-286,617,699,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSecondsWithOptionalMillis():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{951,431,-434,317,-410,703,113,748,62,-321,410,415,-811,-958,-953,196,-724,-773,-628,-649,489,995,-423,473,-382,-424,-150,-973,-791,-904,849,-368,262,-313,-407,833,702,743,-483,-912,647,-912,965,883,138,499,622,-508,-576,956,-462,-256,197,326,-701,348,189,-307,613,812,-522,526,150,820}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{838,369,-437,750,-373,-882,422,-584,-229,-625,812,-114,-592,-917,210,7,-149,140,-9,-839,-810,331,-718,72,-676,510,-338,-372,648,772,-669,-245,-274,-204,-746,-218,370,487,703,-227,-184,598,-406,-165,892,-911,95,-431,460,-341,48,489,-243,88,-20,511,-248,-176,-354,-584,128,584,-592,-432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{993,-880,881,406,-832,-598,561,847,141,523,873,926,411,-397,-519,-509,831,885,594,423,154,984,-455,-116,419,-946,-42,-298,320,-231,525,589,767,455,-686,285,-999,-338,-571,755,-173,-991,834,-882,795,-786,-790,-890,492,905,-181,-416,-506,-995,517,-204,-6,276,-690,29,625,963,131,509}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{567,429,367,447,418,-414,-886,102,-675,287,-601,-900,-770,185,-790,-7,-216,-402,372,311,410,-975,592,-506,381,155,-564,-5,724,915,-773,-441,728,-680,375,700,449,-865,-263,-580,689,696,307,318,-349,635,691,-551,679,711,-336,-516,366,447,419,59,632,878,-965,-469,348,-657,158,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{224,-465,-673,633,-378,-322,671,17,-649,481,-464,-784,749,-714,-428,-996,-248,340,-548,-588,-296,749,299,-143,300,-788,214,657,-702,695,-779,-969,-346,-626,-512,702,278,-536,-220,-776,-231,881,-236,-696,-861,69,983,-455,-184,-230,-866,275,-984,-926,601,-574,-42,604,-885,-208,-669,-613,-113,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-262,-670,-366,368,305,-311,-647,176,-158,-1000,115,-145,290,-992,-421,-1000,245,298,22,457,513,798,-1000,-949,383,-153,709,901,-1000,-687,994,-139,1000,-735,-244,56,-241,890,-370,-994,1000,-171,249,-49,-888,-959,-491,491,-768,-102,324,-581,-16,-680,279,-15,217,-514,-461,-623,102,-727,382,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String,java.lang.String[]):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{460,-320,223,718,-348,29,287,71,828,701,-309,473,-593,-235,-203,-103,321,447,-508,-123,-835,-592,752,423,-809,739,524,-53,230,80,574,-428,-61,-482,860,-624,-217,538,690,-396,102,-836,-495,710,272,678,-534,188,-507,808,-838,-396,-939,109,-211,-42,118,438,-111,-406,-199,855,377,214}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String,java.lang.String[]):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{1000,-705,742,-190,1000,-1000,441,1000,552,-800,-473,-449,1000,-252,1000,-636,213,-519,376,-1000,1000,249,893,951,351,1000,1000,-804,-1000,-1000,-14,-122,1000,-1000,1000,-1000,1000,614,-287,1000,-250,-1000,1000,814,1000,-1000,-163,120,146,-1000,-843,-1000,-400,-570,-251,-519,1000,494,983,-468,-461,-1000,-324,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String,java.lang.String[]):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{206,528,142,876,713,432,1000,-263,-535,-236,219,115,-534,-1000,1000,677,952,757,1000,1000,-765,397,-450,-306,-370,-298,-811,368,928,151,-356,-880,-22,-612,661,-1000,458,187,-1000,-222,653,-112,1000,-651,1000,607,-958,-596,1000,-703,-540,-1000,-1000,1000,-551,-1000,-1000,333,40,-1000,-882,254,640,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsAfter(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{172,191,933,-768,400,-866,130,-552,-764,-561,-432,124,571,-120,-531,616,135,773,-919,-721,-235,732,-404,-621,-873,-890,697,155,790,-787,-573,416,659,67,-534,-758,948,761,347,158,-900,-781,-310,-498,-618,619,-937,598,223,-385,-224,828,-207,-967,578,730,819,-236,-986,840,-836,200,29,-840}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsAfter(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-669,-839,-323,-803,-434,-528,70,-248,501,-749,-192,188,755,698,608,-104,614,-412,-707,-579,-118,587,885,673,104,-167,-538,-160,-998,-673,424,840,-936,340,-832,310,-599,-699,665,943,229,-542,-65,326,625,962,977,-667,579,971,-599,-395,-239,-113,-344,-326,-48,-126,-437,-103,-407,-543,669,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsBefore(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-814,-640,144,947,-791,350,182,-221,960,833,-393,439,-190,689,553,-667,544,-853,785,920,-602,-315,-432,-583,931,-266,180,968,809,-45,-867,-653,781,-609,108,-138,-593,875,-290,-787,-604,-247,229,-209,-142,188,-239,-876,-836,-893,320,-1000,-312,177,-184,187,-765,-864,174,-418,-361,-758,-709,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsBefore(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-1000,-14,-636,1000,-9,117,-60,400,460,-141,-100,1000,1000,1000,272,147,-101,-650,-98,931,-847,738,-1000,90,584,56,-511,-1000,-210,-1000,549,624,1000,591,-585,-1000,-895,802,228,-1000,937,552,-38,181,342,-1000,-31,107,1000,66,-797,-257,939,-541,597,-638,-178,-1000,-1000,1000,-40,-74,-52,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{685,397,-329,-221,152,-838,914,-242,-506,402,226,227,-5,679,246,-583,-833,449,864,-350,218,538,533,-890,251,-254,-131,-46,-432,219,586,-235,181,920,-449,-747,-85,245,67,-831,-676,646,392,-544,65,972,887,-193,-671,181,260,-999,-526,-972,-142,676,-903,437,-836,-512,-568,305,-721,54}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-660,390,-244,-901,848,-493,-285,-790,-190,-161,-371,-592,-863,-73,11,-787,-240,-100,-587,-538,285,-580,-73,224,-187,511,-309,-21,-343,981,316,-361,391,890,855,-69,-43,174,-717,-309,407,412,-699,-320,-736,-487,748,-305,431,-914,-107,-53,-833,190,-572,931,-327,256,-852,-628,552,38,952,-811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{807,-652,-867,-240,-829,-578,727,-821,530,-3,971,944,456,430,-179,-306,590,307,375,853,559,26,-974,-28,184,-716,121,730,191,-671,960,260,-418,293,-450,852,-425,694,980,730,-389,288,495,-349,283,-148,372,-685,-726,185,-410,248,-629,-351,-79,-272,458,907,-443,-443,31,-842,-306,73}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-498,-722,-207,-946,722,12,275,-498,781,-80,-919,-88,812,-41,-648,312,-186,-833,943,279,-228,-991,88,972,-973,-627,-130,-17,50,206,-263,381,639,-717,-114,604,399,-657,801,601,869,929,-606,-415,88,674,-697,82,578,472,-288,-437,-284,-978,738,896,-682,-465,-90,91,864,93,-440,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{588,-851,-991,-1000,-145,-261,-360,-192,299,-719,727,702,728,420,262,-573,-961,11,714,-227,498,123,620,1000,89,377,463,432,40,-550,-388,-176,939,-1000,426,-739,-289,-141,979,172,-581,1000,-983,-469,-132,-1000,-604,137,1000,-744,-695,-972,-447,-205,-145,471,-257,-461,730,-654,-164,-577,-541,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendWeeks():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-850,257,-278,-706,294,-354,-745,-257,-112,129,192,362,-299,-2,-256,-468,-949,-993,-712,967,343,-496,-286,295,-510,-671,133,-4,-208,-485,302,-660,-341,981,-288,-793,467,125,-5,379,798,-475,0,231,-684,770,706,628,623,-115,17,-748,582,-237,-623,-384,-213,554,-851,-443,148,69,-248,147}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendYears():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-650,-373,-196,355,-950,484,943,-669,862,864,-135,327,350,902,-369,428,-354,758,-264,-326,-626,-699,-219,-385,-613,864,-550,-513,380,488,275,-838,-63,810,-262,-492,-335,502,-186,-634,661,987,260,-71,763,-912,-359,8,796,174,396,-596,315,-579,57,385,253,-131,-811,-850,295,-900,-455,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "clear():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "maximumParsedDigits(int):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{77,703,672,-554,827,-308,-433,-13,373,-279,51,-954,-446,183,-250,938,774,-815,-566,-284,300,-392,-869,-791,-939,373,-489,894,-494,-424,656,408,368,198,-797,-689,-910,-826,250,-443,-930,730,373,78,-89,-874,648,-159,-572,186,-332,-182,259,-645,193,-601,-331,380,-701,964,324,-794,677,-452}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "minimumPrintedDigits(int):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{644,661,-447,712,890,-84,80,0,168,776,-926,-248,-245,262,920,636,-222,-45,559,890,727,988,358,519,-165,-600,565,490,938,-543,-710,-267,618,643,491,-518,929,-114,-600,21,-212,294,-10,-732,267,-812,637,-215,-564,-470,-37,781,134,-12,983,472,-858,-175,172,-230,568,-447,799,668}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroAlways():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{973,956,599,6,350,-185,-982,652,-234,-740,-119,372,893,-209,-395,303,-522,571,573,-650,170,-680,-234,458,-904,-552,-213,847,460,-299,339,-453,302,-851,985,-760,-201,682,179,960,7,-629,958,-514,-231,-4,435,432,673,870,312,965,-578,653,965,838,712,-690,365,-970,858,-445,-926,-387}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroIfSupported():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-834,274,-717,-935,-313,-201,-445,-190,80,380,-450,-863,255,353,-591,10,182,-950,-236,509,237,956,564,666,152,191,-441,661,903,533,412,73,120,-803,-169,271,639,893,277,719,-329,306,-176,-971,713,70,-415,-89,-162,-459,-268,-174,259,-732,-408,112,176,878,-436,-374,846,311,673,534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroNever():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-1000,-161,-793,256,891,-548,-605,-639,125,-390,-60,117,-235,764,-925,714,611,-385,365,-736,739,-116,-304,-243,694,-586,-145,-974,-935,-773,-85,-272,314,278,-599,432,-336,148,827,-755,619,649,612,109,-738,456,-388,902,-147,216,537,-252,460,-626,382,602,-647,-105,-322,-919,716,487,817,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroRarelyFirst():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{421,-471,132,534,-798,-490,-648,218,-387,172,607,-663,-440,886,517,-409,932,473,-169,325,-776,236,-495,-632,-635,-953,-434,-513,844,768,-307,-721,-51,15,-567,608,621,-413,955,534,260,-223,343,72,670,201,571,-862,-759,798,504,506,-523,208,-470,-243,-991,867,776,825,-657,856,-760,-37}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroRarelyLast():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-810,-92,56,-186,285,-820,-928,80,463,-117,-369,-940,976,-767,630,646,-935,-774,-778,-28,-122,176,267,597,126,258,9,314,-277,68,-186,510,101,-46,-942,833,423,-763,512,-710,-526,370,977,-156,-319,-501,316,-918,392,-10,-480,581,333,-970,-981,241,-25,-419,12,25,-556,716,325,-684}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "rejectSignedValues(boolean):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-51,867,-31,334,727,-580,681,109,316,628,-137,663,819,-598,-912,185,78,-736,-965,-177,-149,-779,-187,-763,626,-536,-432,99,-949,11,-979,-268,481,878,-261,-496,-527,-733,935,338,-820,332,-625,768,551,-371,-431,-682,3,-938,865,682,-197,266,240,173,775,-241,698,-796,160,893,962,199}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "toFormatter():org.joda.time.format.PeriodFormatter",
            new int[]{228,434,-608,841,214,883,750,-840,454,-920,668,725,572,-381,-477,-934,-135,270,94,-282,541,-984,-464,-302,-529,266,44,23,911,-897,249,-399,696,925,-551,657,-986,-595,402,-387,-18,274,-398,-693,43,135,702,341,-606,232,-922,-626,-408,909,-610,283,-192,-648,-559,221,252,-878,644,831}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder$Literal", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "toParser():org.joda.time.format.PeriodParser",
            new int[]{-99,-48,-394,-25,-287,319,-263,-42,-23,551,-59,662,-473,-384,271,-213,604,623,744,-938,398,-115,-440,-833,65,-829,-335,521,-276,230,-478,323,880,605,520,55,367,-62,776,746,455,515,-726,-256,50,620,-740,487,95,433,-420,-181,170,-343,-711,-236,-886,-151,242,352,5,492,629,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder$Literal", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "toPrinter():org.joda.time.format.PeriodPrinter",
            new int[]{653,562,911,-861,-776,-637,-522,-186,809,493,579,594,397,-159,-174,646,762,166,590,702,149,-762,-540,-154,933,885,422,380,661,-373,-865,-695,-206,-681,830,78,319,-708,-698,-238,-38,-164,215,488,160,570,-479,216,201,211,-810,175,946,592,-391,-893,-576,-629,-934,-569,250,823,-484,-207}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "", "parse(java.lang.String):org.joda.time.MutablePeriod",
            new int[]{729,-169,-538,364,418,-439,-398,172,478,-565,-137,-674,-233,-269,807,-270,-831,544,-495,-552,732,-425,-605,-947,-866,219,-25,-604,-348,-654,277,238,360,-166,-115,274,882,72,-841,868,-287,-576,291,50,853,949,825,191,298,548,-644,193,-216,-842,-737,974,-881,-394,549,-419,-575,298,110,357}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "", "parse(java.lang.String):org.joda.time.Period",
            new int[]{-674,1000,-404,-580,-898,1000,88,1000,64,-1000,735,-294,-353,314,1000,-29,-643,-1000,-676,-991,-398,211,1000,-408,-636,-337,400,1000,761,-350,1000,553,-1000,251,844,1000,1000,-444,1000,879,-21,419,242,1000,466,402,-604,99,-726,-821,130,405,1000,-431,977,-415,-1000,-1000,-149,650,-660,-106,945,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Period", "", "parse(java.lang.String):org.joda.time.Period",
            new int[]{938,-596,450,268,-395,-954,-183,420,-651,719,-324,87,-111,779,522,430,-403,-1,-730,915,221,-814,747,29,156,847,-720,-620,-776,949,507,466,-259,-128,-471,862,-759,54,-766,355,-362,-542,228,-349,-151,-958,-821,663,-68,673,-416,110,638,-439,-27,-815,980,243,-614,-610,-163,-647,600,264}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternate():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternateExtended():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternateExtendedWithWeeks():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternateWithWeeks():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "standard():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormat", "", "getDefault():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormat", "", "wordBased():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormat", "", "wordBased(java.util.Locale):org.joda.time.format.PeriodFormatter",
            new int[]{-986,-769,-845,-109,892,408,366,305,-627,-673,-715,665,-789,-11,754,247,780,-17,-940,906,528,821,702,-204,125,826,632,-648,870,-871,568,161,146,228,710,-983,404,-511,-563,174,527,-972,-541,629,282,460,-745,113,978,145,-362,-586,-486,-198,799,608,499,579,-709,-142,298,29,-946,-466}));
    }
}
