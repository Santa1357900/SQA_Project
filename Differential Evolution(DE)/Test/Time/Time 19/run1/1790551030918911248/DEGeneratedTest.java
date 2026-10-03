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
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "adjustOffset(long,boolean):long",
            new int[]{-563,25,-883,-906,-666,-448,-192,-314,485,-216,-63,-985,-521,470,-526,-802,445,-504,-540,787,-178,242,-48,-158,-160,815,411,76,-753,-790,573,-656,-879,-657,989,829,-820,660,947,145,-110,541,-720,294,-87,81,680,916,940,-4,577,589,802,383,-610,-929,894,-145,0,335,-580,157,675,992}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "adjustOffset(long,boolean):long",
            new int[]{-360,-115,-717,534,-903,740,-364,844,-573,554,-48,631,-135,20,153,509,291,285,480,-877,-242,430,-315,-865,-236,-846,701,-33,546,-836,-960,529,944,705,-673,-738,951,-616,909,684,998,982,244,-650,-790,311,-70,-389,-594,329,-358,929,457,-296,994,-408,-252,-179,407,642,-14,-180,-564,600}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "adjustOffset(long,boolean):long",
            new int[]{-728,993,13,727,-794,221,467,-509,708,860,337,-238,-892,336,259,-743,48,-343,-295,-725,818,-87,-762,-856,-365,-313,87,-961,959,201,-879,-274,-813,938,-560,-144,-316,-790,328,-679,984,679,-908,894,-773,192,-544,-148,-584,-534,587,648,-95,124,-399,779,704,-614,411,-992,625,-689,-791,-204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{881,830,-541,860,-622,-742,294,-374,-840,740,855,486,873,120,90,538,168,522,250,781,-289,-486,-817,297,-777,414,-734,-71,-513,528,112,552,-618,-906,-896,994,237,-790,-224,286,-936,160,-869,0,-473,-833,-907,-860,944,859,450,309,-971,502,-100,-313,-364,-613,96,716,318,-310,967,354}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("java.lang.Long:NDI0", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{12,248,188,-142,690,540,-324,307,143,869,-425,-181,-657,-119,911,-969,-742,63,994,564,70,-840,900,20,-325,393,-91,-294,521,-592,-836,293,988,819,-286,-394,712,795,106,-670,537,180,-275,-470,-907,-649,208,-421,-601,-823,419,329,344,292,715,183,-581,-820,-468,-887,644,518,-317,-420}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{-833,388,681,-188,-444,507,18,844,-259,352,-91,786,-699,388,-324,808,695,733,-816,-654,350,-833,685,417,397,-887,-525,-964,560,-549,-951,-104,834,977,-643,-552,794,-117,756,-903,653,694,-687,344,509,-202,-798,-578,-391,-128,-866,-821,939,-642,-19,-178,-79,-587,64,-511,-369,-265,921,554}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:NTU4", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean,long):long",
            new int[]{-491,325,279,55,-816,-185,514,-306,287,-942,673,-982,170,558,-241,52,-938,-321,-680,-203,-813,388,616,-959,40,-147,278,-448,779,-166,-265,-637,-151,494,82,71,107,-514,544,872,959,21,-840,-628,-682,-22,939,-846,547,53,-643,522,991,464,-232,-95,-829,771,80,971,-731,24,879,685}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Ng==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{967,-307,-399,894,275,-365,-624,806,-982,207,-152,-918,-528,-545,-214,637,950,650,385,-843,-832,377,323,791,209,575,465,-285,-249,-914,953,57,-103,801,-523,875,959,928,-70,-58,-160,-953,-632,-82,268,-317,213,-801,-900,-95,-12,-845,-882,-884,503,-182,325,648,400,-868,617,-978,-789,839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{546,-914,-95,585,-739,-321,352,60,-405,-90,1000,-651,-641,388,420,-1000,411,315,230,-968,846,229,153,760,-380,318,-845,-1000,394,867,-9,-973,715,-125,1000,-1000,-784,-1000,0,-76,-747,0,1000,-793,-635,98,-1000,-255,949,-1000,364,1000,509,588,731,0,657,-203,-1000,257,-538,965,1000,-277}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzQ3MDcyOTIxNjE=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{855,823,-301,764,911,1000,130,836,11,132,-640,507,864,-324,-889,426,465,-440,-312,-831,-777,-393,-478,657,113,-769,-634,-542,925,589,794,-383,-639,975,-317,-131,724,594,824,127,-749,71,-553,-983,779,66,-458,-141,315,-846,-858,-990,-608,-191,-585,-257,598,-249,-639,969,544,-829,-808,-999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "equals(java.lang.Object):boolean",
            new int[]{809,202,-328,508,-123,-204,331,-283,902,95,-366,-519,-957,-730,-661,828,-910,58,-939,718,-566,-505,202,611,331,-780,314,-905,572,-260,-968,682,-992,-52,867,990,-85,-65,-351,343,115,660,231,-395,977,412,-844,-498,-66,-853,-541,41,16,-219,-522,-223,9,283,-355,-401,361,54,557,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-492,-1000,-38,-1000,371,-1000,800,-533,1000,220,-516,1000,-1000,-291,906,1000,-142,-301,-1000,593,685,-1000,-261,-817,672,-1000,713,-601,-1000,1000,-666,1000,1000,-844,466,-324,-1000,381,-15,1000,1000,-1000,-1000,-246,609,-1000,1000,238,835,1000,-1000,564,-759,-400,1000,502,-400,1000,-1000,-1000,535,246,-678,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-284,-13,-591,398,-1000,1000,1000,-1000,1000,555,-1000,1000,642,-1000,-1000,789,337,-1000,-83,-1000,345,-689,69,-827,-247,-1000,152,911,-847,-1000,121,-350,721,344,193,-528,-944,1000,736,-1000,345,-1000,-461,257,-400,284,1000,337,-942,-1000,296,838,1000,-708,1000,414,600,1000,1000,-613,-986,-330,-279,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-453,682,84,269,-9,914,-727,272,-700,919,-968,-136,-646,767,888,-523,-165,-83,152,692,301,963,998,829,458,-828,887,-181,-154,141,-770,-516,294,-145,-875,334,721,-5,-23,-435,93,396,872,59,713,255,-329,-105,393,-115,976,-894,517,-410,-640,-751,-479,-384,529,-265,-107,989,475,792}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{351,461,839,-142,-93,-402,-658,953,912,-396,-663,-116,-318,-386,-143,-974,-372,-847,506,248,210,-345,712,151,-73,547,47,407,-319,635,-982,551,144,505,-416,-649,-346,755,-916,771,-402,16,197,-574,233,-90,-895,455,-520,-634,148,-237,332,72,-132,705,-770,179,606,440,-22,885,-423,-546}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{830,-26,-339,-734,-719,624,-852,-849,-737,-451,48,-441,31,-925,388,102,-328,-76,806,4,660,-331,-976,576,-869,773,-639,981,-424,5,-440,-807,904,-946,12,-95,-239,261,-92,-35,403,-500,781,-150,-585,454,473,705,152,834,-376,461,-101,-652,456,-269,428,696,-156,293,-110,272,-554,246}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{-911,-241,331,-410,-577,-226,102,-235,-856,-817,-56,1000,575,112,537,-1000,-500,-671,-1000,-130,901,336,258,132,1000,737,648,349,-1000,61,514,-186,-644,60,629,-1000,-1000,107,-789,52,-2,-450,-923,-718,543,-954,-133,-750,695,-1000,354,-763,436,-425,-972,-320,561,1000,441,-548,-1000,1000,-882,-809}));
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
            new int[]{741,79,-131,73,460,1000,-499,450,51,-1000,184,522,1000,-334,475,82,1000,-1000,-500,-682,439,125,-961,685,146,-1000,591,-984,-1000,1000,873,-1000,514,-673,688,-420,-559,1000,707,1000,-823,107,-848,648,-6,-637,354,-282,1000,206,-738,46,-102,969,374,1000,-813,-1000,818,-511,269,986,-584,-957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{-682,239,280,-50,1000,220,289,1000,-318,-17,484,-813,-285,561,-219,-1000,50,-106,927,-325,-108,-948,1000,-381,-126,-1000,-884,-136,540,166,602,427,872,897,-462,962,980,-915,-763,533,939,-398,-562,-848,-354,-96,-405,-230,473,1000,394,-890,148,-258,739,1000,452,326,202,54,-340,952,1000,671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{132,-12,461,-832,282,707,-257,-31,-541,-638,1000,-7,-669,-598,-699,-1000,-1000,585,628,784,428,-114,-838,-446,690,-520,497,647,-128,119,552,250,1000,-782,877,1000,-1000,1000,-423,359,380,59,409,-730,-521,663,365,841,-1000,-264,190,-1000,565,-1000,524,1000,595,-994,-1000,-707,-658,-24,-587,280}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{772,-67,424,-546,352,975,-232,502,-536,-371,796,-57,64,-863,428,-693,142,232,-385,-837,-50,-515,479,-547,-83,-710,369,-202,-872,970,410,65,-569,519,70,511,414,967,241,-634,779,491,-611,-155,-51,-951,437,289,161,507,210,348,-262,51,517,979,748,-11,246,372,855,984,-797,-126}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{902,282,811,-267,311,11,-799,-477,-515,188,-261,260,-634,562,289,904,-435,-535,-945,-66,-791,-780,-648,171,204,-487,958,352,688,121,531,-380,614,347,-524,119,-430,-946,628,-575,-764,284,-805,692,-795,-893,489,65,-714,563,-849,183,-159,-653,-175,-621,281,-769,-470,596,351,-884,-672,-45}));
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
            new int[]{456,-119,-752,938,480,508,-760,70,-464,-59,-672,127,-590,-516,-671,-64,-363,-4,717,773,115,933,226,-554,147,-284,-287,-108,-173,-659,262,-85,-482,467,984,-992,814,896,270,-301,-213,303,-644,784,630,-650,955,-61,843,-88,-523,-255,11,-956,-135,926,905,-472,-558,-245,174,357,-665,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{-1000,-493,-1000,-1000,-1000,-328,1000,1000,-1000,386,577,-1000,-98,292,752,1000,320,-391,-134,496,70,380,662,664,672,-790,371,1000,477,714,978,-1000,-137,-381,-1000,1000,1000,-1000,-339,655,44,-429,-1000,-1000,1000,1000,1000,-42,-409,511,276,1000,-829,1000,863,-300,118,472,-218,1000,421,1000,-38,459}));
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
        org.junit.Assert.assertEquals("java.lang.String:LTkxNi4zNjU=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getID():java.lang.String",
            new int[]{-372,-743,-11,916,-751,-635,225,-312,-629,-362,-591,839,-815,56,483,461,-757,-636,-544,-730,-815,-704,-967,735,-259,903,421,233,504,934,-661,358,453,-354,782,-729,207,-98,-868,702,-390,972,-160,62,-582,-947,117,-968,-468,207,783,912,564,149,-227,-930,317,-890,-348,889,204,874,955,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.Long:LTE=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{727,975,65,245,752,728,514,868,111,575,328,-398,-19,-572,-227,806,159,-8,-919,58,717,-643,77,-281,227,-994,-446,939,47,-617,297,-671,-624,-699,-90,961,-427,756,-587,866,564,969,963,-185,-892,-130,974,766,106,638,-564,105,237,-585,271,547,-269,-144,-778,412,-685,782,922,-127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{-1000,44,-503,998,-207,-1000,976,-167,74,705,-256,-507,113,599,-182,-68,-1000,465,355,-386,508,1000,-994,944,426,-500,611,-431,413,418,-853,-1000,28,-476,-1000,-475,-683,-404,82,-974,-366,1000,706,-430,982,-538,-1000,-805,-1000,-158,938,519,473,347,489,-770,275,-250,940,-682,-1000,-179,-533,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{-109,-468,-687,-412,-534,413,976,-948,-726,81,103,788,740,-785,502,-685,973,-779,-456,828,147,991,-299,-590,-706,273,115,816,-122,-364,-34,-85,-282,233,-336,-681,-354,765,-271,278,-405,361,552,463,-250,453,-771,914,-152,150,101,158,-247,89,-251,-770,-977,-697,789,-813,354,255,5,597}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAwMQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-128,915,136,-441,-753,-500,-972,-86,796,-919,841,820,620,874,-451,4,80,27,610,42,-549,288,952,-424,286,789,-684,-208,-764,-61,-128,615,-933,-391,558,61,-828,-935,700,226,21,439,104,529,-340,-91,66,966,-359,-769,-506,-615,566,769,709,-887,-185,-241,-618,571,-52,169,-446,997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{217,-277,969,1000,-1000,-1000,1000,-709,-1000,716,-1000,791,-1000,1000,-1000,1000,-121,166,-203,1000,-628,-1000,1000,1000,1000,475,-493,55,1000,982,1000,-1000,-1000,1000,16,-434,-1000,-887,539,-494,-701,-320,1000,224,1000,-417,-1000,-1000,-1000,242,1000,-30,64,1000,1000,1000,-456,86,1000,-1000,-796,-50,-46,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-185,-746,-315,-148,-585,634,-676,24,222,585,99,588,852,-897,-515,-468,-272,267,-933,582,-330,-541,874,-889,271,30,-237,-102,302,-787,804,514,437,228,155,26,125,825,-11,-200,-422,-164,736,-715,518,685,-918,830,-400,288,-31,-173,227,-743,-816,-234,-155,931,38,154,764,393,-901,687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:TmFO", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-70,-887,-901,176,312,58,-160,14,261,246,221,3,-515,-249,709,-845,-504,-252,970,-165,957,-99,405,-194,-813,-971,-165,-560,-971,-755,-760,-766,302,988,-36,829,593,-393,-103,-295,-687,-480,829,-111,-418,-673,348,162,814,746,484,-501,626,-267,-399,758,135,-271,998,23,-403,656,-910,398}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAwMQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-49,955,-409,1000,-407,-1000,811,-1000,-556,-966,-935,1000,-1000,-773,-1000,-177,697,1000,414,489,1000,464,-720,147,838,-517,-1000,-104,-754,-1000,652,-1000,-794,21,-554,-1000,-1000,1000,-1000,500,167,495,-432,1000,-235,136,514,-1000,-864,-473,1000,-1000,-952,-1000,250,-880,-497,-349,-552,679,290,1000,432,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-1000,847,607,-433,375,-1000,1000,301,-115,892,-1000,443,371,755,1000,1000,1000,1000,-410,102,1000,1000,537,-667,171,-51,-1000,-1000,569,-768,152,-1000,148,354,1000,-974,736,304,-398,-543,232,603,1000,-1000,1000,167,485,-113,208,-270,-1000,-1000,-1000,-57,431,-684,-496,-1000,-737,1000,1000,-353,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{597,469,692,1000,-316,-889,-1000,252,-1000,420,-378,14,-136,-329,-129,-1000,-102,-321,72,976,-101,524,-342,-650,232,-550,-415,-612,-102,204,758,-501,-88,1000,931,1000,1000,-99,267,-28,-126,160,369,-289,574,113,-861,361,395,559,400,-308,-519,-175,364,-1000,683,328,-40,352,525,400,-167,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.String:KzB4ODAwMDAwMA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-1000,1000,-1000,-1000,-958,386,592,-1000,1000,-1000,0,-644,-532,1000,-713,969,-1000,1000,-1000,-602,912,-1000,-1000,-410,-439,336,1000,-1000,-1000,85,-35,-1000,-1000,-1000,529,-123,-1000,-1000,-249,224,-1000,-1000,-1000,-1000,-962,1000,-1000,718,966,1000,-482,500,-1000,-439,-329,-863,-1000,262,1000,-1000,338,1000,1000,882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("java.lang.String:ODIy", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameKey(long):java.lang.String",
            new int[]{-973,-616,239,570,-329,412,201,865,392,-983,964,889,403,563,891,-42,186,775,-189,822,-496,505,-271,630,702,569,-48,502,-753,-122,803,83,122,-279,393,-983,-885,-956,27,141,294,-173,-263,-604,-925,244,157,922,111,217,497,638,994,-833,736,-294,217,-720,-694,711,-807,-513,-466,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.DefaultNameProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameProvider():org.joda.time.tz.NameProvider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(long):int",
            new int[]{-658,-722,-190,-880,717,-88,949,451,13,561,481,-572,21,-514,354,-25,-641,-611,-483,-984,85,282,-575,-956,818,383,270,-544,-926,-15,503,896,-489,215,935,-186,-392,-16,140,692,949,51,204,907,713,-912,955,135,234,927,726,318,-417,744,427,-258,988,826,-743,-410,-388,-195,724,-575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(org.joda.time.ReadableInstant):int",
            new int[]{562,-971,453,705,933,-365,-588,-464,-9,-435,-495,558,895,-91,518,368,446,-788,224,271,-480,-243,-696,-469,-274,-999,935,-877,466,720,171,403,-649,-393,-938,-429,290,-620,-180,-573,-544,-162,280,532,-174,682,-571,609,-5,-547,-190,-219,601,-545,252,71,-187,-838,569,477,88,-743,-393,-81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffsetFromLocal(long):int",
            new int[]{-618,486,-663,596,723,571,-468,-428,-250,214,-22,-201,140,-116,616,269,-930,-112,-514,-74,357,469,-741,682,738,2,194,-201,445,-957,522,-448,871,-640,13,855,502,-218,-937,622,-444,638,10,33,-231,-56,-409,598,669,-961,-525,927,-701,-644,-403,-546,861,-777,694,-982,-965,185,-149,156}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.ZoneInfoProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getProvider():org.joda.time.tz.Provider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAyOA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-158,280,-14,-997,714,-213,262,944,-83,288,-758,333,-966,636,507,-885,394,-173,524,134,976,-723,879,647,-282,-746,15,763,482,794,-953,722,166,825,978,142,278,810,2,663,666,-257,-523,793,31,-999,-359,171,-650,-734,-851,-59,-586,474,130,516,296,609,606,-433,761,20,853,-198}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{186,687,-292,-88,956,1000,375,348,1000,-1000,-253,-1000,-1000,-729,1000,-1000,-605,-1000,-420,-83,339,1000,-761,1000,-1000,577,867,307,-896,537,-192,768,509,939,-951,583,-939,490,496,279,-777,1000,-65,109,-1000,1000,290,0,1000,1000,-1000,1000,546,1000,-456,524,-506,870,-1000,-242,1000,959,444,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-810,788,597,-726,-268,-382,-662,813,-840,-611,648,-930,922,322,29,-932,661,-776,-989,-237,-561,-631,743,61,-462,-814,-51,-408,-548,419,180,-487,-48,908,84,-139,-100,-372,-266,246,893,232,-5,79,-273,-46,344,902,-810,-523,-363,920,768,79,-139,358,-557,-731,-882,-121,981,953,765,980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:ICs5MTUg", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-6,-495,474,-915,754,-128,539,772,-5,957,698,848,331,717,-575,658,410,-629,737,-919,-176,693,-274,-440,333,820,-269,-261,-708,-210,-178,26,-552,456,-872,-394,-922,980,808,-249,-204,655,-560,634,339,469,-977,-738,-898,-481,863,645,280,143,-837,-134,100,504,-656,-693,803,-811,-194,998}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.String:KzU5NjozMToyMy42NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-70,-612,465,-832,57,426,-370,-1000,-459,-1000,-814,-1000,222,-376,-676,-140,-992,-834,748,461,78,1000,-1000,-47,-675,911,-596,1000,-195,-297,981,278,-491,-1000,-586,1000,62,-897,377,-1000,-922,19,678,763,-859,457,-341,-890,1000,516,385,128,-847,269,-33,1000,1000,-320,-1000,-1000,54,-44,970,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-544,-1000,1000,332,-1000,-1000,196,-1000,-1000,-1000,-541,898,-1000,1000,51,-1000,-1000,-1000,529,1000,-1000,362,-1000,273,-961,1000,-1000,1000,-1000,-1000,-1000,-1000,-1000,-435,-138,-887,58,1000,-300,1000,-1000,960,1000,178,-129,-211,-541,-794,-548,-418,-92,1000,-243,-946,-1000,-21,-1000,1000,797,950,568,1000,903,-676}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{73,686,811,1000,-161,408,-459,-486,373,-724,694,72,-246,-917,-818,-385,403,-397,5,-154,-402,442,-80,-559,-932,-363,-396,1000,-528,-692,-653,55,-986,-387,-466,1000,-250,-886,239,125,864,-366,524,401,-189,-808,-561,-580,171,328,983,-65,927,502,0,923,1000,905,-592,-74,-114,155,89,-238}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-526,-987,321,-790,14,448,264,-922,-912,-156,-834,-363,-592,-66,-518,-749,-38,-872,986,878,-268,755,-458,-290,-245,846,-972,995,-494,-36,286,222,331,52,868,774,183,89,-969,-650,275,452,950,999,-565,-659,-551,-572,-87,-674,-331,405,-806,181,45,550,-38,-519,-261,569,795,-811,218,-924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getStandardOffset(long):int",
            new int[]{-189,-705,669,-587,642,-612,848,122,-730,-940,-15,-76,-804,468,50,-270,19,-375,722,124,-254,976,-235,580,-188,-452,-942,-684,-820,-653,997,723,-525,652,-443,511,-873,-64,-615,104,-761,198,-953,-840,-951,735,748,589,674,-448,693,-81,-838,958,-383,475,790,-774,-35,-540,-7,630,688,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isFixed():boolean",
            new int[]{-754,-957,444,491,843,-848,-626,-984,663,815,849,-153,-330,467,-190,572,-605,-580,449,-221,-776,806,546,760,82,-530,92,874,395,-515,658,630,-273,-868,-595,-180,-685,-834,395,-787,890,980,-112,-771,-358,876,-293,-203,-65,-919,-580,171,-506,-636,81,-164,-834,-850,-405,-112,-911,897,359,212}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isLocalDateTimeGap(org.joda.time.LocalDateTime):boolean",
            new int[]{-417,-218,708,-568,-33,-572,358,-853,-139,-766,6,777,681,821,-496,129,992,357,763,-231,-121,386,874,-266,-483,-730,-790,-190,41,-848,-137,-734,805,320,856,-874,-381,-199,-946,-635,-310,-900,892,698,610,65,-323,57,993,-919,508,302,108,-7,-355,210,242,-518,568,394,395,965,-144,418}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{779,703,669,541,-141,-188,868,-151,-863,348,-418,443,61,-380,502,-709,-139,-370,552,-190,318,505,863,-614,849,403,265,351,758,229,426,-764,12,87,541,-740,-445,634,400,-240,795,197,-380,-467,-303,-125,-196,912,-346,-298,-466,-197,-785,-613,-816,-105,-361,-843,-206,758,-735,-211,-523,-319}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{-1000,-751,-1000,-426,569,-784,-992,-631,-172,1000,-438,-1000,739,-357,443,616,2,-242,-1000,948,1000,-214,-1000,-683,-230,-417,-1000,-180,674,252,322,-346,-143,1000,-1000,730,252,233,-1000,-168,-791,-162,427,1000,-565,1000,505,-1000,1000,-1000,-812,-741,-508,941,665,63,1000,-310,-797,-1000,1000,1000,-710,-876}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "nextTransition(long):long",
            new int[]{73,-67,-223,-742,215,-470,683,-345,-843,306,770,137,-535,660,663,-513,-548,-561,-729,353,-516,46,149,-633,-647,434,814,606,-699,376,-552,-145,-451,857,-864,-591,966,-27,181,451,942,-733,-73,385,-592,119,984,-60,498,-531,814,-663,-614,-58,177,384,-537,-721,-833,-64,214,-146,-785,-128}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.Long:LTUw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "previousTransition(long):long",
            new int[]{-234,689,-610,-7,-372,869,-71,-163,473,-981,-178,-617,278,-663,824,-298,97,-591,756,-437,939,-48,-748,-737,630,716,348,360,-975,-191,-768,858,-202,-656,-503,406,-313,-776,32,-580,-422,-862,-653,-325,70,384,-439,-94,-707,19,545,182,649,8,795,-529,893,-469,-534,-355,483,-562,-262,443}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setDefault(org.joda.time.DateTimeZone):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setNameProvider(org.joda.time.tz.NameProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setProvider(org.joda.time.tz.Provider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("java.lang.String:LTB4MjA3", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toString():java.lang.String",
            new int[]{49,404,-9,519,961,597,-271,-696,-681,-872,125,681,110,-484,806,-836,697,754,-579,390,536,469,-708,392,-29,-265,-570,-62,28,-752,-677,-34,-211,-565,-870,-281,119,-80,567,-133,240,757,-893,645,995,858,-68,-164,-488,83,496,-730,389,668,-481,-531,579,805,-226,13,659,508,127,288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:java.util.SimpleTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toTimeZone():java.util.TimeZone",
            new int[]{86,785,309,-815,-27,234,986,-511,475,479,-818,-824,280,-5,538,-46,-432,446,-299,-511,27,96,-817,22,-449,-159,-661,770,-640,458,-312,129,397,-142,532,-240,820,-467,-507,-1,-843,219,-45,721,263,-152,-357,-221,-375,277,-365,443,-291,997,909,-106,120,33,-760,740,-896,620,-291,791}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "org.joda.time.DateMidnight", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.NullPointerException", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTimeISO():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withChronology(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withEarlierOffsetAtOverlap():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withLaterOffsetAtOverlap():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withTimeAtStartOfDay():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZone(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getChronology(org.joda.time.Chronology):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getInstantChronology(org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInstant,org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInterval):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getZone(org.joda.time.DateTimeZone):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
