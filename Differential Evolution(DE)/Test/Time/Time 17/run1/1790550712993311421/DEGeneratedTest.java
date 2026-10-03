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
        org.junit.Assert.assertEquals("java.lang.Long:MzI0", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "adjustOffset(long,boolean):long",
            new int[]{86,-683,-131,-256,352,718,-472,524,512,-114,163,53,-210,34,-812,602,579,-244,502,142,471,-203,-253,-159,400,547,686,-276,-572,541,769,-357,324,707,-113,-175,921,-656,629,-822,420,901,907,258,-688,-488,745,-661,399,-80,222,401,495,-37,-922,980,-145,94,368,-148,-933,541,651,-734}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "adjustOffset(long,boolean):long",
            new int[]{-354,-1000,946,1000,93,-1000,577,1000,944,955,496,-466,-1000,901,-474,-375,374,137,156,-364,-113,-396,-850,-521,94,819,-1000,882,-763,-402,337,965,94,-571,1000,-1000,57,-1000,-514,-447,850,-100,199,-1000,10,139,-643,1000,811,546,-769,-321,287,131,-1000,26,1000,958,162,390,-178,-1000,-1000,475}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "adjustOffset(long,boolean):long",
            new int[]{-19,-419,652,931,-675,558,-568,515,698,-237,-208,-223,-715,-247,-692,110,336,928,139,569,-815,-788,371,1000,97,-38,951,-1000,219,-606,990,1000,856,-699,524,-690,-181,-224,415,430,296,1000,-367,224,166,865,124,-347,-156,623,851,953,488,913,867,-721,-645,476,-1000,335,-322,-275,117,-421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{641,-284,-603,953,-896,-161,-741,-113,413,264,-912,-993,-242,373,926,-556,177,-617,-78,667,-636,877,-972,284,-301,75,104,22,717,137,-663,827,709,-84,823,8,-887,-231,689,269,-35,350,-731,581,753,-421,452,-82,752,-569,-418,-933,138,585,46,967,21,-444,62,318,-123,-551,-949,566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{472,905,-97,368,771,505,-745,-733,401,163,-532,-6,-354,736,669,-727,905,-319,654,-241,914,-22,-472,-780,-720,-953,415,206,687,-921,269,293,-435,308,744,919,-958,990,-335,-182,538,100,687,101,494,-902,426,139,419,-500,547,594,-294,-533,-917,168,-559,-902,601,-65,-138,750,745,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0OA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{112,841,484,510,-587,63,-367,-63,624,-704,960,-298,-111,-420,135,-925,740,762,-625,-858,46,422,512,15,-374,-82,639,-948,-293,274,182,491,-182,754,-831,828,-241,-807,37,-749,-183,-570,400,503,-472,-526,852,628,-4,483,-887,273,10,-942,605,-587,-984,-130,887,-308,400,236,-716,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:NjY=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean,long):long",
            new int[]{220,-768,447,773,271,617,-322,-582,504,-795,504,43,-18,868,-521,246,-857,-419,145,523,-554,-245,508,639,-633,265,-393,-118,-112,574,119,964,-607,-155,-668,-374,315,532,682,-637,-240,559,538,-885,-121,934,-26,-742,815,382,814,-234,-342,-576,-557,44,-950,-765,569,588,838,-462,410,374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzM2Ng==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-544,-48,120,850,-805,-78,-279,724,918,-812,680,99,-492,697,-610,-281,191,-760,-830,-641,-891,5,562,-461,-265,-116,925,833,200,928,448,-317,-733,588,-353,-609,309,68,-678,-764,-569,483,-15,-496,-359,362,-136,-370,-142,-504,697,617,-546,-902,770,-554,23,865,897,260,111,-411,-618,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-768,-375,737,954,429,-279,563,612,-256,740,440,491,-808,-856,-758,-571,517,-650,369,-702,189,509,26,-76,831,-979,-16,-710,599,872,863,-894,-379,42,907,572,-723,-989,118,430,-129,686,694,-742,983,-53,-247,352,723,-527,-514,533,967,481,-913,-274,-312,598,67,272,525,202,-100,-406}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:NjIz", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{905,-680,488,92,442,-117,-137,-198,-32,-725,113,-412,429,207,624,-205,-884,742,864,327,-796,-795,-318,445,416,-754,-860,981,810,332,901,-222,246,174,-917,904,-193,293,-553,450,171,301,814,575,-292,293,655,889,-21,239,995,201,201,231,912,213,-755,5,230,-162,-215,317,-678,480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "equals(java.lang.Object):boolean",
            new int[]{-781,570,472,862,-867,203,322,-937,-681,-519,629,-212,680,950,729,-859,-326,581,-454,824,-406,732,518,127,-134,418,-683,640,882,-228,530,-284,-262,37,637,-297,-823,419,-187,-512,16,694,363,-992,395,-384,909,5,-716,-189,857,-429,875,153,-876,768,702,385,614,985,-353,152,-477,-412}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-588,1000,221,-970,-1000,-809,-1000,-491,-666,99,-105,15,640,-580,1000,-316,890,-327,-108,-519,469,96,129,-1000,1000,196,-36,569,1000,1000,1000,107,-78,-678,-507,565,-6,1000,-929,-193,1000,-1000,-769,-1000,909,-312,670,769,428,553,-131,-835,286,697,-450,204,-675,972,-381,72,702,1000,-629,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-395,20,602,261,-408,-665,-466,-949,-600,119,368,866,549,98,-650,309,279,-848,716,-756,-525,944,-661,-927,639,972,-530,383,720,118,633,239,291,450,0,916,986,-26,-274,761,-612,-669,-424,41,-389,-4,-37,135,712,446,19,-765,778,1,-311,-172,-972,-477,3,684,587,34,-337,805}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-767,-562,484,-420,-819,769,-14,742,239,-778,270,-465,-507,-35,812,474,-652,868,-234,13,64,492,124,306,969,-647,-222,-108,550,-86,-53,-69,267,440,658,183,-591,-223,-874,-220,509,435,765,24,990,-103,-703,-42,516,-981,-28,221,-722,-436,264,870,-61,461,-432,244,-743,209,-600,700}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{267,-334,-60,930,-589,-876,203,177,-245,-787,-83,537,-380,-250,931,547,-542,-803,349,-942,538,-496,775,-215,-293,712,73,349,35,542,883,-810,-250,-679,-619,-287,227,-28,811,355,19,523,174,-138,-89,595,96,-985,-200,-291,428,-738,-152,-164,-632,-694,-203,-351,38,530,-405,-838,-657,-653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{-763,-827,-620,-298,832,-262,-644,-736,-924,584,575,258,-154,261,665,-15,-161,-388,592,790,658,-150,795,-311,855,876,-708,-682,426,-735,-287,777,847,451,432,914,117,-968,-3,-74,466,831,945,-238,407,373,-284,-273,616,-224,887,979,790,-288,-777,101,32,44,-846,-137,626,-968,195,421}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{1000,143,619,393,-544,267,1000,594,169,-1000,-84,-264,271,-1000,-148,-1000,-2,388,-398,-1000,-396,-871,302,407,945,950,130,291,147,141,-713,-605,-1000,-691,-627,-1000,51,-238,333,-509,-1000,253,-1000,867,213,1000,411,429,-280,-3,-609,-1000,-137,65,269,-437,-873,-59,-412,-131,200,-1000,139,-296}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{1000,684,590,757,-406,818,368,1000,1000,82,628,-501,935,-1000,-544,517,-295,-511,1000,564,56,568,-297,893,-834,-265,152,443,-751,-1000,374,229,-1000,584,-133,-274,-1000,-152,-400,-232,-396,-1000,934,-75,-500,-979,583,-278,-1000,374,-783,-879,767,991,-102,-44,-450,1000,-565,-680,-92,1000,187,-305}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{-1000,-805,-1000,-437,1000,-1000,-1000,433,-1000,-860,-201,93,587,-474,1000,-1000,326,336,-1000,-771,229,179,563,402,522,1000,584,-1000,1000,671,145,998,1000,-116,-817,-262,669,-253,1000,177,63,1000,-1000,353,1000,494,57,-403,1000,-562,550,491,-1000,169,605,1000,1000,-282,1000,396,170,-709,-421,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{554,-804,-625,440,800,-495,860,-808,-485,-633,252,597,-942,522,-871,-539,-447,360,312,-696,-389,208,-602,149,-465,-354,-648,-943,-782,-523,-548,945,659,-496,-47,-562,-13,683,226,578,206,411,859,40,-500,708,-767,-817,837,-108,-603,743,898,953,752,-738,-125,324,-4,449,579,948,-944,-404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{460,-598,-1000,0,310,-864,-132,-26,212,-164,1000,-1000,43,-899,1000,-1000,315,-13,-1000,-222,329,401,1000,1000,272,-229,704,-1000,701,-765,168,352,356,0,-530,-770,-1000,810,350,-63,-754,1000,-345,524,-313,-526,118,-1000,460,-536,-517,-1000,-1000,190,1000,0,815,-859,1000,377,1000,687,929,-568}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{737,258,-74,705,-603,-289,-58,409,164,132,803,-826,634,-227,218,-197,248,-456,-357,418,940,387,753,104,572,-734,-67,-851,897,-805,95,703,507,-778,220,-91,-803,735,417,578,-530,616,21,372,-428,-200,257,-924,312,-407,-653,-930,-762,-228,584,357,232,-93,871,639,509,625,464,-162}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{511,-803,-708,174,231,1,-342,-958,39,817,771,-575,241,117,-381,-181,27,-213,130,695,-94,-670,-250,290,611,-606,261,-693,768,-161,859,-430,-868,-614,385,-808,-27,-353,851,-810,354,-197,-116,-368,-570,-460,-415,-610,672,979,-566,365,-24,-706,-660,637,-44,451,-297,-571,-549,-903,454,-5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{-1000,671,1000,102,526,264,-635,-126,-952,481,-46,-435,-1000,923,-1000,-1000,92,684,-1000,139,-1000,-731,1000,105,-1000,-88,1000,223,-710,-467,-1000,590,-218,1000,180,393,1000,542,-766,-892,666,-388,-480,53,164,437,228,-1000,-880,-555,164,-57,-857,-142,-381,1000,-402,21,179,-1000,706,-1000,1000,601}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeSet", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getAvailableIDs():java.util.Set",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:KzQ2OUQ=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getID():java.lang.String",
            new int[]{901,955,-135,-469,-426,681,-27,963,507,89,-408,-872,-111,282,-584,-345,706,609,-112,38,535,-187,516,-729,-484,-984,307,-789,-396,315,-616,63,-223,863,29,-605,-736,691,469,391,41,-734,556,666,867,774,-812,-82,-51,-522,745,896,-46,153,161,-225,-452,538,-684,840,-119,-953,-623,611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{681,469,-620,-613,88,28,715,-213,423,134,844,813,-989,-866,161,-682,504,-727,-100,975,532,-826,942,602,514,-792,594,-246,-677,-829,868,282,-360,341,-866,-257,-24,272,-482,340,-411,477,904,-583,842,526,996,453,-132,-953,-470,940,-132,589,-569,-403,-164,-594,493,-52,-936,804,-40,451}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{168,-830,373,522,-46,704,933,-803,-652,-687,857,-526,608,-744,-149,-990,-775,138,-342,-475,-938,-475,892,-529,954,856,98,-177,-643,-70,-575,-449,-269,-60,677,515,535,-743,968,-621,-76,13,744,407,565,876,467,-32,324,374,-39,-973,20,-376,70,-69,-90,-641,74,395,141,-177,145,765}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{-300,406,-486,-240,949,519,-481,-135,-388,121,934,-133,877,-376,-348,753,-108,367,744,-930,637,-387,-427,198,573,-741,-615,584,513,-620,623,-634,-703,488,622,-971,-1,558,-213,519,660,761,-136,597,-191,-836,-486,112,-573,636,31,-651,-602,-326,-246,635,11,947,-523,990,609,-31,-984,764}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:KzU5NjozMToyMy42NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-69,-747,-570,-412,906,873,316,97,639,-91,-633,680,-479,-941,-627,803,858,-607,312,596,-228,669,616,774,-154,780,-420,-451,162,965,317,75,-392,-923,-474,605,-721,-711,-33,-533,-314,897,65,187,-930,-420,320,698,592,-598,323,371,-573,-863,-926,-143,-151,76,-946,-841,-696,395,-973,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-795,58,1000,1000,1000,1000,-259,38,-1000,458,-750,1000,-635,219,-1000,-334,-225,1000,-1000,731,-455,1000,-736,822,837,137,-266,-1000,-322,-1000,524,1000,566,-1000,-695,1000,-357,-996,-5,1000,-1000,-1000,-675,1000,-634,-1000,1000,-749,565,887,1000,1000,23,-474,676,-1000,1000,-1000,1000,-820,-140,-632,-142,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{90,-1000,-440,-1000,4,542,-381,-1000,3,708,-569,430,290,-794,-1000,-628,-564,17,-1000,65,-921,-622,-370,-1000,-186,369,-163,82,-615,61,888,-1000,-925,-807,-348,1000,-809,-616,686,817,406,897,181,783,1000,-1000,1000,698,-496,7,-357,-270,-573,-863,92,-617,-251,186,844,397,-1000,-815,1000,-108}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:MHgzZTg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-200,-1000,-25,-1000,692,784,479,-326,-208,198,-788,725,-426,-620,-94,-333,593,148,-419,-468,-60,1000,650,1000,137,1000,543,-246,142,797,562,857,-500,-1000,-478,-510,-1000,-733,4,-650,-748,1000,133,795,-1000,-1000,1000,1000,171,-174,73,291,-504,-1000,-892,-622,666,-1000,-1000,-558,-155,216,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAxNw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{997,-121,995,573,-137,507,600,107,733,173,862,-18,723,-65,-165,133,197,800,797,-53,474,736,423,127,-739,-743,-648,-754,418,366,114,554,-742,714,83,-2,-131,-944,-662,-122,685,-727,-827,56,897,561,340,255,-799,735,-932,571,-152,27,-618,-905,994,-768,342,-764,950,-379,423,-761}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{1000,821,-871,683,1000,147,741,1000,27,86,1000,-349,821,-1000,932,551,1000,-1000,927,625,-594,-858,-1000,-473,-233,-777,-656,-904,-1000,-1000,1000,269,-57,683,564,324,-286,-83,540,-806,-30,261,-663,-644,1000,326,-1000,-341,-192,-520,-30,635,1000,1000,-714,1000,594,-489,400,59,198,-630,-401,388}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{1000,-944,1000,1000,1000,677,1000,-118,265,1000,1000,390,72,-1000,-1000,603,152,614,872,-52,581,-576,-107,-522,-255,62,144,-1000,160,652,1000,1000,-91,333,941,245,-805,-1000,-766,-721,-1000,621,-726,1000,1000,-293,84,-417,366,239,303,717,417,682,-1000,-698,-150,-673,1000,-118,931,-666,270,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAwLjAxOA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-66,-568,409,949,-321,815,380,189,-417,992,-18,743,-753,261,-784,708,261,634,118,327,-719,214,176,-918,-233,-71,158,-903,619,564,-20,591,672,177,989,541,-847,-870,-781,-848,77,209,58,257,815,632,81,-661,41,-946,791,531,-291,159,-503,-368,-259,-131,590,-44,710,-227,173,-334}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:OF82XzY2XzY4X182eTY2", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{1000,731,590,-229,-642,-460,-489,-1000,1000,-1000,1000,1000,-1000,1000,434,-1000,-1000,1000,-392,1000,1000,-32,-1000,628,-1000,-514,1000,1000,209,1000,1000,-679,-1000,-1000,-391,-1000,480,135,-1000,487,908,1000,-1000,1000,965,-305,-531,464,1000,812,-1000,817,-1000,1000,-1000,-1000,1000,-855,-168,-874,1000,-172,1000,-648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:LTU1Mw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameKey(long):java.lang.String",
            new int[]{421,-44,567,-389,-364,739,-553,295,5,306,-799,469,-499,-682,-827,-143,641,-448,-686,919,83,561,-860,-506,-298,-98,765,-697,546,-589,-502,79,688,632,156,-42,113,44,-595,-369,539,-536,213,708,-673,691,-198,-205,486,295,-631,818,202,293,290,-675,-731,-618,-505,-356,341,-612,321,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.DefaultNameProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameProvider():org.joda.time.tz.NameProvider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(long):int",
            new int[]{-782,240,424,697,705,-572,369,-529,304,501,133,899,51,-647,875,738,821,786,748,-562,114,-812,817,883,697,663,332,-530,315,-24,-543,-861,995,900,226,-174,81,-475,-442,-510,73,940,-516,-690,592,329,-614,-22,-664,-55,-975,90,-226,-266,-606,-431,-496,-607,-962,-234,686,-414,359,-551}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(org.joda.time.ReadableInstant):int",
            new int[]{-859,-856,-306,503,144,346,878,723,969,-603,-70,-972,821,-258,-948,89,-581,682,-323,219,475,774,733,-201,17,-445,-584,-869,716,-969,772,664,207,81,-440,896,-522,423,-555,414,871,145,351,523,-45,-117,-997,-305,-403,-265,126,682,-69,-279,539,-455,607,-822,-507,-916,-331,873,-21,-801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffsetFromLocal(long):int",
            new int[]{-815,-600,-826,-64,286,-94,974,-749,-448,-869,524,469,-454,-137,632,968,-317,794,-422,60,-794,466,-447,-696,-886,-850,715,150,676,730,-146,-854,532,-723,571,318,316,-395,344,767,251,-150,882,974,-773,838,-227,-86,488,-142,-417,623,262,-644,-706,596,405,6,226,-909,-548,638,478,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.ZoneInfoProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getProvider():org.joda.time.tz.Provider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAwMQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-583,-83,-993,-351,-905,609,-73,-53,459,227,-911,-166,656,567,-43,173,-313,588,943,-984,179,194,-119,171,-811,-310,-612,-668,668,-414,-969,-83,-214,-26,-978,896,-87,942,-911,-396,-261,-761,-897,-154,-253,-321,-938,-593,36,700,-873,-470,-603,-90,-476,-140,399,-565,-640,-94,-691,487,631,168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{1000,-168,51,1000,646,779,26,-223,-1000,-1000,-793,-1000,419,371,-1000,351,-1000,-1000,-1000,-1000,809,-558,-151,608,531,132,1000,-1000,-414,-106,746,-34,-876,1000,581,289,1000,-1000,-589,481,1000,1000,916,1000,455,1000,817,-842,-383,131,-1000,386,1000,-182,-505,-1000,1000,-634,503,-557,698,-1000,912,86}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-598,-867,-643,-55,83,-792,-396,580,541,172,-302,-447,364,-487,649,-456,-228,-465,-966,-276,236,-65,-520,-459,770,366,-412,524,859,427,525,50,-496,-837,-666,-827,-465,325,-91,-672,943,-591,537,17,788,-191,910,-582,-15,276,627,276,-834,343,303,398,723,352,-355,621,132,300,102,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:KzMwMkY=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{320,-775,713,-302,-670,361,576,-425,-501,155,379,185,-676,954,309,143,-322,-974,703,731,7,-59,-165,792,114,124,214,229,-701,-673,440,368,-828,-587,477,-407,72,-978,-904,-614,-317,-548,-796,-820,195,391,-208,294,-856,-981,300,-396,920,435,369,605,887,-621,-882,-983,-9,758,-616,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:KzU5NjozMToyMy42NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-786,-498,-79,-776,132,443,925,-185,250,-743,-393,-484,-509,-388,302,-471,185,614,119,101,-893,557,350,-955,-602,-430,-581,-282,510,929,-617,28,-421,-415,497,-580,-126,861,969,657,-593,183,-362,-269,-859,230,38,222,-224,962,-935,72,592,581,301,842,-511,-305,942,293,-6,703,227,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{833,307,-488,145,-466,-234,1000,262,-848,-380,-1000,95,-650,374,-113,647,-387,3,1000,1000,-12,-713,680,-322,-415,1000,1000,945,-97,-183,-28,143,316,30,738,-809,-187,1000,476,-892,-1000,-1000,94,1000,322,985,233,-981,19,275,-234,-292,33,-63,259,352,932,1000,64,-801,802,-419,291,-275}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{1000,466,-557,-1000,192,-1000,-388,1000,-27,1000,108,1000,-155,-290,1000,465,-745,1000,-1000,-479,1000,-80,-654,-1000,-824,1000,1000,-351,1000,-1000,681,-685,-161,-1000,637,693,371,1000,-845,-481,-1000,-1000,1000,1000,1000,-345,-37,-872,265,-385,-916,-1000,463,-1000,-1000,-20,1000,388,-853,-1000,-509,654,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.String:MHg4MDAwMDAwMDAwMDAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{770,-626,1000,576,472,632,-512,-143,-112,126,-109,287,-585,-520,-78,618,647,-274,-491,536,-196,-43,-833,994,-123,1000,609,1000,-863,624,1000,-182,315,-659,-909,430,242,194,-331,-1000,-273,-890,558,-279,138,-101,723,-111,122,-1000,795,-816,-620,173,204,-377,1000,740,-611,-649,403,-1000,-50,-442}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getStandardOffset(long):int",
            new int[]{-987,772,-136,700,675,-516,-683,-345,144,-133,-660,228,-434,873,-546,-927,301,87,839,-839,200,-877,-823,-313,-517,-166,704,352,-238,-766,-526,351,-114,-785,629,455,-765,-402,92,-61,-160,-183,-512,806,-713,-358,338,-263,754,734,-367,-501,703,550,-303,187,792,120,985,578,362,-404,866,-862}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isFixed():boolean",
            new int[]{-1000,-395,-52,-986,2,48,190,-51,-441,-259,-887,141,0,-447,917,7,-570,767,87,444,-417,-273,-996,676,-933,-631,-499,-534,-743,-925,-905,-439,562,291,408,-153,360,-958,107,830,489,-373,-420,809,-117,540,-460,363,628,-30,754,-379,-420,134,-837,233,479,-900,-167,-143,-524,-206,346,-415}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isLocalDateTimeGap(org.joda.time.LocalDateTime):boolean",
            new int[]{-609,920,-275,-970,-468,-331,12,-462,-629,-166,-983,-741,-582,191,550,231,-906,749,182,-963,390,-247,244,974,400,234,-941,-757,-399,-410,-192,953,798,396,-402,-124,373,712,214,380,-859,-483,826,-295,495,-412,-524,878,762,132,565,-87,734,781,-255,707,189,-800,-355,-976,890,452,-319,-665}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{-554,-580,-303,239,522,222,-146,364,606,-332,-125,-697,-688,619,728,-125,310,-590,579,-572,576,-957,972,-269,-576,516,-505,19,-63,640,521,696,689,-939,-53,-186,-360,515,-179,-919,471,785,234,837,741,319,925,231,779,13,-336,-344,419,-728,691,-629,-607,-859,211,-783,486,-971,-728,-650}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{-368,-471,168,251,-521,794,-424,212,-34,-142,-584,-200,910,-392,-299,-646,-912,-369,-74,12,-860,442,838,-722,669,896,671,861,267,296,-451,-569,503,-985,112,-467,-143,790,382,521,13,289,356,102,9,-969,916,-163,273,-717,249,760,-200,-820,-852,209,388,978,360,-132,-827,96,417,88}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "nextTransition(long):long",
            new int[]{-193,3,247,987,706,252,400,-307,214,224,-995,-607,-513,-444,-160,-607,-627,610,-220,589,436,252,111,908,456,-265,225,140,881,-283,-266,925,954,-864,-251,-22,782,60,962,-301,526,-88,-490,-730,3,-441,-851,38,-441,601,171,539,825,-76,-272,-117,-892,81,729,353,-392,380,-669,-914}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("java.lang.Long:MTY=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "previousTransition(long):long",
            new int[]{-136,77,-340,803,-988,-22,-136,-592,252,-199,-977,-808,127,616,52,160,322,761,829,418,653,691,597,-363,351,843,427,458,3,538,-104,-554,987,-782,-290,-106,530,-142,-519,-313,607,263,-876,761,-154,-861,-590,246,-700,334,78,-704,-501,432,640,-178,-427,27,-499,-130,-646,-596,-672,-785}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setDefault(org.joda.time.DateTimeZone):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setNameProvider(org.joda.time.tz.NameProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setProvider(org.joda.time.tz.Provider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toString():java.lang.String",
            new int[]{654,694,108,591,-413,866,351,549,-979,-116,523,-126,-424,-738,-430,432,661,-228,751,-436,248,-554,-493,-254,-692,-862,-25,456,582,305,-445,-724,606,-216,-7,376,-750,985,-466,-773,412,149,-97,624,979,96,78,945,657,246,146,901,899,588,874,34,740,752,54,661,-881,-621,-823,-936}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:java.util.SimpleTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toTimeZone():java.util.TimeZone",
            new int[]{431,-24,-746,924,-66,593,760,595,267,420,528,679,586,258,-20,221,-55,330,-9,614,609,928,360,704,552,394,249,454,-39,-543,807,999,-760,-938,-125,300,-782,-20,940,-941,59,759,289,-915,-296,-153,-267,-143,-408,85,-842,715,888,-802,-281,-238,447,403,508,379,378,778,-813,41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "org.joda.time.DateMidnight", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTimeISO():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withChronology(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZone(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getChronology(org.joda.time.Chronology):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getInstantChronology(org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInstant,org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInterval):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getZone(org.joda.time.DateTimeZone):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
