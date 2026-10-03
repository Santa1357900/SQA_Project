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
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "append(org.joda.time.format.DateTimeFormatter):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-934,344,-787,-753,-675,202,80,-990,812,-424,423,-758,754,-486,-751,-753,904,774,-520,717,377,135,-990,-411,654,-99,190,839,747,475,-495,-975,871,141,-581,637,-538,929,-419,-872,864,826,890,-109,-378,466,-734,352,-79,942,431,-103,-537,380,437,-633,-212,534,-713,535,-71,809,560,105}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "append(org.joda.time.format.DateTimeParser):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-382,-167,-197,-838,-341,877,448,863,539,448,50,296,-606,138,-172,-129,-817,-76,-120,-384,802,162,-418,371,72,167,-767,800,208,766,-12,481,-242,146,-239,-163,414,-198,-506,680,594,479,957,748,-770,613,59,-234,-180,-20,547,-711,-533,986,-44,-128,-634,-614,625,935,13,-941,-244,-864}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "append(org.joda.time.format.DateTimePrinter):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{642,227,28,923,-947,796,106,432,341,407,102,557,-588,-90,-809,436,-283,-192,395,-614,-317,-83,-682,473,407,710,799,-349,-862,-665,505,-732,-701,-196,-543,-687,84,-15,-449,-113,956,-356,-772,93,804,44,633,-217,-48,371,835,-606,-276,-402,-28,-477,875,316,571,-201,812,-459,676,114}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "append(org.joda.time.format.DateTimePrinter,org.joda.time.format.DateTimeParser):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-14,-900,-551,632,-319,-513,538,-382,959,-30,-182,-343,-255,-595,538,547,-829,-503,-116,-538,-510,-501,-303,544,195,-234,-126,89,180,-269,-938,-383,975,985,901,808,544,982,-83,863,985,-369,-577,167,559,-488,282,328,915,791,233,-377,141,47,645,681,-713,165,999,-155,423,201,-799,-951}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "append(org.joda.time.format.DateTimePrinter,org.joda.time.format.DateTimeParser[]):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{445,364,-48,-176,-874,-193,-825,212,242,131,594,911,-987,-465,-885,-644,519,-736,541,-436,-979,-178,247,643,-990,375,-709,290,862,357,500,658,867,-694,-579,-151,-142,160,40,-806,618,395,691,493,-130,704,-342,405,-271,202,670,-680,218,566,-343,-804,691,961,-33,-897,-194,-885,-436,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "append(org.joda.time.format.DateTimePrinter,org.joda.time.format.DateTimeParser[]):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{655,741,-254,-674,445,-702,115,260,-433,756,236,-993,-916,-942,931,-100,-596,-254,239,-104,-148,-938,284,-161,-82,524,653,-915,-704,189,-944,-865,761,919,433,-227,316,-898,651,127,-916,489,444,-929,970,-940,-606,410,-775,193,347,428,-217,761,-71,542,-567,284,588,32,750,730,-67,-872}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendCenturyOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-300,-257,638,-24,-54,628,-684,865,714,-540,-274,894,-828,-529,-258,-312,-657,-742,960,816,874,-189,415,457,-555,-639,815,156,295,739,967,-456,950,-359,382,463,-303,111,-880,799,629,-148,-137,509,112,571,-51,-172,-542,-562,614,433,251,977,-946,477,305,221,98,-159,-465,-205,237,660}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendCenturyOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-150,641,-688,36,1000,-745,944,741,728,626,1000,108,989,58,-1000,-265,28,-369,-863,-625,-534,-532,-1000,735,-405,172,-1000,1000,-48,-709,-819,923,-999,194,635,857,-808,869,324,425,-489,-623,-463,260,886,90,-1000,-978,65,-141,-268,-660,167,592,526,-92,-790,-414,-788,630,-149,-1000,1000,-556}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendCenturyOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-736,-162,-79,-505,-933,162,-726,23,-271,964,-507,753,195,-902,970,892,449,-327,-493,343,824,502,627,-282,639,-71,482,675,933,568,-636,-749,717,-870,98,468,591,490,707,-704,-143,397,639,-694,-692,-147,612,-226,-230,-938,-387,867,175,57,-980,600,437,670,893,742,-253,323,-452,-458}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendCenturyOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-320,325,-564,701,-170,941,880,-812,-742,716,293,192,701,-672,450,727,372,-147,446,-420,790,-22,619,965,288,-257,260,942,620,-39,720,941,694,-882,823,235,907,538,604,132,841,-170,-715,785,-878,-716,170,-938,150,712,621,685,87,-492,-302,-17,30,184,-835,45,-433,-506,-447,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendClockhourOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{425,603,461,542,790,-89,125,-740,127,-558,-343,274,279,2,717,-358,567,-73,-429,-292,843,-639,-930,-380,-62,-552,-17,-263,382,983,-538,-558,204,-292,894,243,137,150,407,-431,-388,650,923,-812,-447,-918,778,-183,162,-361,675,140,-337,26,2,198,-748,-596,-596,-90,414,544,896,-639}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendClockhourOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-823,736,-212,386,-107,-739,213,999,693,464,143,-763,-105,815,469,-882,-675,-286,-120,589,775,-705,-874,-457,-375,-51,549,-666,-962,-101,-310,-456,-921,810,-734,-825,-396,-923,-195,-27,-160,263,-133,-935,-808,-71,-89,783,-993,49,-414,-843,-217,96,533,-677,622,-37,-404,-91,-414,-83,775,-722}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendClockhourOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-541,61,-56,296,-655,477,-82,880,213,-955,-238,752,-64,57,482,-670,-79,557,437,-617,605,-713,787,-298,450,811,966,992,-586,468,64,-403,-136,479,508,85,-632,226,-561,-782,862,468,784,362,352,138,-810,-965,447,-801,-301,914,-174,41,318,-577,11,891,511,759,-278,-207,-988,127}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendClockhourOfHalfday(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-153,972,377,849,-660,-539,744,-218,-471,-210,203,-458,-646,489,-996,-90,-553,-220,-636,-733,-395,676,929,716,-268,885,851,-808,-92,-75,503,-513,-644,898,-16,443,734,506,255,-49,-600,41,-996,879,-930,333,133,-469,-346,925,164,-159,-46,126,-218,155,-641,-3,-763,-865,757,817,402,-889}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendClockhourOfHalfday(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{409,656,701,843,970,142,-93,302,-546,875,-826,-247,-927,774,-236,-78,424,-906,704,851,511,450,-741,-649,361,-213,803,-497,714,-969,940,-560,-621,-823,-176,978,478,-300,-653,53,-947,342,84,-612,262,569,729,306,762,-708,102,227,775,-218,781,-26,795,191,-119,776,941,498,590,578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendClockhourOfHalfday(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-443,327,-307,-877,794,635,-506,-841,675,-24,-688,-932,-623,766,41,249,343,791,-875,656,235,35,752,35,-289,852,-915,612,-462,489,870,-752,-349,997,-56,-580,434,706,-193,-247,-292,-413,-296,316,-309,92,469,381,-428,-683,746,-51,-807,-251,170,-21,-128,224,-433,958,-632,274,-691,217}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfMonth(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-673,-244,896,563,-663,451,882,-197,334,-95,396,-370,-968,-284,-455,222,-864,11,344,623,-447,-183,812,821,354,979,825,-650,648,-397,621,510,553,-14,-702,951,836,701,-566,-287,490,-876,-977,-883,475,-955,-641,-115,-816,224,-822,748,-187,742,-592,464,-273,837,-780,651,-824,536,712,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfMonth(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-650,-445,-452,455,796,811,-416,-628,369,721,-983,319,-714,-550,-108,643,983,-235,452,500,-187,-616,-388,-376,234,376,-921,526,-564,900,-187,523,424,-471,-3,648,-54,-818,443,-127,317,228,464,311,216,743,623,-872,801,66,514,-297,149,-155,-283,305,915,554,437,962,-611,-882,977,543}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfMonth(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-734,854,524,-606,955,-423,-32,-482,38,-265,828,-101,-442,-267,-99,-129,909,832,-453,880,2,159,54,926,-697,-236,468,-259,289,361,-340,-64,-57,351,893,18,-756,480,922,552,-670,54,735,974,-22,17,-542,-366,-644,-588,-354,-547,-362,723,-774,295,-24,454,678,586,602,-100,-302,-957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfWeek(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{360,-742,304,-568,-645,865,-325,-328,961,61,-150,77,868,995,798,-458,-456,-343,-925,975,285,-265,581,-626,729,-222,-596,116,-290,-436,-395,106,898,-634,-60,-938,-68,-272,-753,-117,430,-703,504,-221,640,286,411,-356,325,-764,753,12,942,844,-205,-134,-834,890,440,-448,377,508,-955,-908}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfWeek(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{855,411,398,314,-884,56,588,-613,77,-794,315,-422,547,-171,482,352,930,916,-135,303,-57,-641,-901,-181,679,824,266,871,-599,-759,958,-127,-940,435,-152,-837,-241,247,-467,-671,97,510,-322,347,670,-792,-648,293,-132,286,-160,246,546,-352,211,941,168,-837,-586,689,552,-299,821,-841}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfWeek(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{589,350,-498,-380,-839,-147,407,435,992,-503,-213,106,-309,-15,527,-716,-336,-266,-756,82,-24,-799,696,-503,-913,-439,748,-713,-292,-740,663,-922,-328,819,-386,-833,895,-867,-945,102,250,-508,-144,-125,-505,535,976,376,278,464,826,605,669,-38,-533,-949,-316,472,-678,-340,-170,-238,149,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfWeekShortText():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-428,49,-41,-131,49,375,-521,-508,-332,47,411,-369,25,-587,620,-671,561,251,-337,676,-869,-35,-888,-493,764,551,110,460,-635,222,694,-815,178,-72,670,-848,664,566,785,-396,119,-333,-207,459,-161,554,-363,-321,-69,530,-579,554,-39,-473,-903,634,557,832,-128,625,-182,988,226,477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfWeekText():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{339,13,572,-100,980,-783,-117,106,792,-13,-519,-903,396,-28,-144,437,-491,-988,-272,-507,816,-338,339,374,-299,344,-110,-636,-567,-68,113,715,465,-805,-171,-468,-219,50,652,-913,-64,-171,-501,363,-647,896,-520,-121,-255,-154,892,-67,-904,-740,-443,-735,-443,520,531,-148,812,552,-644,-937}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{660,922,-352,323,584,443,-997,486,-916,317,350,-35,642,-570,-438,-357,-904,933,-609,-138,993,608,862,323,706,626,-347,-298,792,547,-18,-231,752,985,626,-976,340,756,87,-83,-119,-497,631,-453,-311,820,-43,428,-427,-277,598,-633,-296,921,774,812,-353,830,436,103,-767,493,452,5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-855,667,-473,608,560,219,310,235,19,651,-611,-664,-198,-887,-637,541,-476,-433,-950,-24,-592,898,28,-204,-255,-277,158,-556,435,418,-464,-422,-842,-577,-613,-149,754,-693,-168,-257,-347,256,159,522,-138,637,-742,57,916,168,231,700,-552,780,-195,-263,606,-207,186,11,-606,-797,223,-757}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDayOfYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{256,32,-614,-43,764,568,741,22,223,-918,115,890,-645,505,-351,125,-342,640,803,-551,-741,872,-336,-604,-703,-298,758,216,-444,914,601,-716,705,785,-668,519,537,-902,173,-691,871,945,137,-657,413,307,-511,-609,817,202,-248,537,408,-134,728,-287,602,843,-43,-586,-85,-871,-686,461}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendDecimal(org.joda.time.DateTimeFieldType,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-50,-861,-783,206,194,150,-938,293,-944,-152,517,-842,8,-534,898,759,-649,-259,-706,-382,-51,-260,-972,-10,152,306,528,-843,989,934,302,-124,-267,700,556,-144,-962,291,-207,611,-47,-858,-274,21,365,-756,-239,491,-337,-763,-604,-255,-259,-883,976,-32,-108,-941,422,-746,540,-333,-220,-844}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendEraText():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-318,-696,322,-444,126,150,-900,187,820,-667,953,241,572,-766,344,601,-782,721,-895,723,943,995,334,-508,-753,190,689,568,-816,555,-120,733,-764,979,910,-928,594,-136,-676,495,-34,902,106,-949,-907,-813,-819,757,107,-715,503,275,549,351,-369,652,-541,212,374,554,260,454,591,947}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFixedDecimal(org.joda.time.DateTimeFieldType,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-662,-776,-972,460,890,-296,616,657,716,20,188,750,-177,43,867,841,-259,-875,-546,687,-299,298,-806,-862,-574,-948,176,-497,-807,-811,-554,-963,252,958,-91,279,-153,-555,726,473,-373,17,34,961,468,518,-425,-939,482,863,71,724,278,-306,57,-957,433,859,-743,-464,576,-418,600,80}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFixedSignedDecimal(org.joda.time.DateTimeFieldType,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{176,520,618,659,-120,-723,-793,-484,-874,-192,-618,-307,-456,-51,500,-579,945,878,45,-67,489,456,731,-38,391,-109,-528,-764,-414,-786,31,162,916,-114,-743,-189,-111,419,152,-904,-196,-653,-288,590,-853,-919,-522,831,559,234,46,-848,379,877,-631,-921,-189,807,677,176,-930,-574,-975,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFraction(org.joda.time.DateTimeFieldType,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{18,526,902,-7,209,814,189,-36,917,151,971,-64,305,562,-86,683,338,-442,-12,101,538,-577,810,-107,-508,1,-650,-443,-706,-639,278,134,-195,54,231,66,436,-457,-601,-143,-982,-188,905,-718,237,-799,-257,329,116,-974,220,-746,44,717,258,-309,8,-132,228,61,-195,-141,-485,248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfDay(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-634,-861,919,-171,-560,-902,-481,353,346,745,247,-212,423,-813,-334,-41,583,-175,-767,844,744,-299,311,-658,-702,-772,361,763,-527,-38,447,-344,-598,-949,213,-155,279,941,26,770,-968,-9,-851,573,650,-253,-559,-219,473,-188,396,854,588,-526,-914,427,772,553,0,618,-642,304,680,-564}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfDay(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{248,-365,284,-325,-374,352,-825,387,132,-959,-786,105,-55,-380,127,-444,567,-887,801,927,664,624,-998,-597,-227,844,-140,-405,-207,340,358,-919,-851,-216,-989,451,43,-703,-465,543,-681,-894,380,861,112,-168,740,596,676,-98,-140,-932,574,-173,324,-2,753,343,-669,-473,934,-164,953,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfDay(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-1000,-460,323,-979,1000,368,1000,-611,-85,437,-302,-1000,-1000,-233,1000,-214,-969,-55,-1000,-1000,216,984,1000,1000,89,730,1000,810,-991,-756,637,-45,568,-643,-56,-816,-635,-1,319,-866,999,-471,1000,-343,6,-1000,-1000,1000,-1000,-33,550,-1000,-1000,1000,-857,-642,-1000,390,-1000,789,-729,-1000,-781,-980}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfDay(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{775,887,-591,1000,-331,343,-1000,-296,-142,-232,-169,923,1000,-694,965,1000,1000,90,1000,817,524,-377,-1000,-1000,1000,1000,-928,-1000,1000,1000,-1000,16,196,69,-1000,1000,1000,-422,-1000,1000,-1000,-748,-324,1000,185,1000,1000,1000,-680,40,-1000,705,-1000,-171,1000,155,1000,-171,400,1000,1000,115,1000,689}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfHour(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-351,700,-543,506,-409,-204,-352,-540,-637,468,483,-379,777,-358,-280,-727,-178,-707,-49,617,280,-327,-260,722,-744,423,-4,-945,857,42,-855,899,760,-199,47,729,-850,627,294,201,633,664,-499,-243,-911,-691,520,-894,-826,340,131,-219,202,432,677,856,451,-160,-623,501,620,-700,591,-480}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfHour(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-581,-385,512,-146,-942,-394,-306,-947,-737,195,-71,-483,494,-63,-133,-534,-434,-738,767,214,-365,609,458,-501,-98,-272,512,732,366,-518,787,-165,461,96,-111,-938,157,-808,-232,810,207,-819,-338,-218,622,653,918,-933,-836,212,-171,735,674,-84,547,316,-611,920,410,-766,63,250,-461,-344}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfHour(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-653,571,-851,94,-27,-60,444,425,-335,-533,-435,-511,513,566,331,-29,-368,-561,66,-842,656,-665,748,-24,712,-200,451,-428,-155,844,865,-293,-655,605,-936,-992,-821,475,-631,692,843,-111,139,760,336,31,648,838,666,-459,801,905,344,-3,-597,-111,-368,410,899,833,-15,264,113,-289}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfHour(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-322,-892,591,1000,-1000,-146,-827,1000,-648,-1000,-1000,258,-65,824,20,615,1000,-1000,700,-304,70,-1000,360,972,668,-1000,1000,-389,484,309,1000,537,-350,1000,1000,160,-1000,750,1000,704,521,22,-827,827,1000,112,-822,-1000,-1000,-1000,345,192,-834,5,958,-160,-1000,-468,-1000,-871,-1000,-1000,691,-110}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfMinute(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-841,276,-66,514,898,-18,-812,863,-937,710,-140,-108,716,-479,998,-812,-293,844,429,-513,371,-320,899,291,517,-210,759,-691,722,634,-504,-674,-801,-74,368,247,-168,318,-417,-246,-627,953,-404,-744,249,-582,-648,642,-63,957,587,-297,457,-278,680,49,165,-684,956,838,744,900,836,691}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfMinute(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{760,-709,-564,-380,520,799,126,-360,153,-764,619,-847,-356,45,899,-733,889,-274,-261,864,858,-855,-809,647,563,-73,-909,-476,-306,-436,675,830,-927,-31,518,1,-601,-118,-943,-990,-846,-260,171,130,984,398,-489,391,398,459,-420,496,-795,856,915,-123,751,-185,-839,-914,713,346,-711,-208}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfMinute(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-518,485,76,875,-458,373,-253,242,-415,1000,-1000,225,-219,-806,1000,1000,-973,1000,543,-1000,1000,578,1000,-799,778,-960,1000,-1000,577,746,-1000,862,-1000,1000,-402,-1000,-889,-504,-1000,14,-583,1000,-631,-301,-1000,-865,-1000,932,970,356,654,220,1000,-832,-318,401,47,-1000,412,-545,-366,240,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfMinute(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-839,744,888,487,905,558,-891,362,-878,-446,934,-902,559,-955,774,996,-920,-215,389,755,131,-691,900,930,14,172,-55,660,84,970,-662,-555,283,-858,673,-196,709,181,-597,407,362,127,105,464,622,999,879,-984,-448,135,-874,-879,-306,369,321,881,675,403,-202,746,634,-223,-930,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfSecond(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-730,-289,54,476,380,930,-812,-782,-592,120,-605,-39,535,-949,11,-356,-507,-466,149,686,484,409,776,-47,-951,465,246,917,-600,-172,-103,-31,197,125,569,-326,266,63,196,-876,-667,-13,982,478,486,448,106,-688,800,-162,365,416,302,753,753,357,-693,-102,-471,-742,28,76,58,-940}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfSecond(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{344,-931,408,-211,-409,-905,-511,-744,362,-108,-594,-926,410,495,-411,826,975,-731,-728,840,110,882,929,118,-70,537,402,-336,-931,30,-397,717,120,955,942,188,327,-249,903,12,384,-831,-432,-978,847,-773,-691,-503,356,-260,-655,-441,644,122,-570,-737,128,295,-474,377,790,-712,-987,-378}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfSecond(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-713,667,771,636,77,-755,-167,-38,-716,180,-496,-365,258,832,832,-568,97,982,-179,-613,-568,707,-333,934,677,-618,-288,-462,503,299,448,-768,717,168,-855,323,276,125,973,-112,-287,771,-157,629,-77,243,-358,-843,-47,-656,-15,-163,-813,-641,-814,948,194,348,749,130,-662,408,355,-216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendFractionOfSecond(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{802,-795,348,761,814,-1000,-1000,-697,-334,1000,-204,1000,427,-208,1000,-221,-906,0,328,1000,1000,-210,470,-1000,-1000,-329,-140,600,91,13,-1000,-602,-331,-44,-661,-311,1000,-307,845,-1000,-1000,-524,529,943,-1000,-428,-103,-1000,701,151,1000,-181,-1000,303,1000,1000,-978,1000,635,-1000,-135,1000,615,5}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHalfdayOfDayText():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{988,-205,-949,562,-525,-417,229,-974,653,72,-750,-31,832,675,-264,936,-383,-567,344,-826,-104,-26,703,95,-417,450,938,-805,934,667,553,892,-906,-766,-574,812,187,-507,508,-658,781,-767,454,-35,796,744,855,-183,-931,653,994,207,686,-862,-857,-586,-66,-355,-725,988,235,997,-217,-19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHourOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-198,245,-523,-408,-879,-548,582,-127,199,-997,-65,511,-858,-171,36,-98,306,696,-750,-709,13,-48,-119,-91,353,667,401,469,-339,-13,462,524,727,-113,820,-117,838,54,-624,-937,-670,145,-864,581,517,279,132,-227,537,-686,-957,795,-7,179,-400,473,71,330,-250,-258,240,-729,-59,-424}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHourOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{749,-756,-276,-17,933,528,-109,868,-808,-867,-419,459,645,641,-271,-515,265,-868,-287,-872,83,745,-155,677,726,-119,-572,-181,961,-326,-396,-572,417,-187,729,380,657,-750,51,-282,380,895,-893,-680,-283,0,-535,94,249,-360,142,700,-271,-628,388,-182,-742,-978,-618,-967,158,367,-174,-408}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHourOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{618,9,952,-504,-393,-735,-776,14,-501,357,764,-285,-574,754,-576,-939,222,-527,-431,448,-414,-245,982,-419,231,-294,-772,808,665,10,-361,685,-322,-813,-416,-243,-329,-594,-262,324,536,-634,726,-249,976,-587,669,817,-137,-306,-68,525,503,919,-314,823,-439,-921,766,760,-251,339,759,-237}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHourOfHalfday(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{132,-1000,806,-485,86,-449,-1000,-246,236,-31,-1000,1000,408,98,-192,966,476,947,-277,-980,-118,129,878,1000,-851,246,-567,-564,-445,1000,-347,664,630,-133,24,-160,53,514,-121,188,-167,-616,-129,102,925,179,456,-469,-957,-329,1000,-61,-297,-122,99,-556,-160,-310,-1000,-563,438,-628,-410,463}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHourOfHalfday(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-740,141,607,90,250,988,-301,975,-194,-981,-807,-760,94,-246,-861,-525,68,-250,402,858,559,-539,-610,-193,758,994,603,305,-762,-427,-154,919,-108,-104,-918,-969,689,-280,-681,159,963,-999,687,-664,-860,-309,891,492,-763,915,625,541,-742,656,-809,-576,-296,301,-432,-216,-207,465,-505,103}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendHourOfHalfday(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-240,971,-682,-342,-746,613,-631,-368,27,-823,308,732,-306,288,-919,305,-543,469,342,-615,-42,332,-295,956,-49,571,428,-133,46,442,951,-253,-12,-249,-126,806,461,-486,-644,715,-758,-513,-328,896,690,480,730,507,-1000,57,715,163,-549,-446,-308,995,-64,655,-979,58,-505,743,-269,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendLiteral(char):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-810,54,106,327,-805,-306,-114,-981,599,-835,79,673,-515,-701,-441,784,-990,-345,-167,-890,-648,-480,907,101,-127,-656,-612,631,-693,-187,-560,507,557,569,-418,-145,-766,-334,-434,-469,-540,229,-383,-38,683,536,-846,484,-784,-266,-211,-865,67,-242,-112,146,254,653,-758,900,241,-504,521,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{210,-435,-838,700,697,665,-623,766,80,330,-787,-296,907,919,581,697,-572,-96,885,-383,904,-158,-508,256,-659,17,399,-132,-479,816,106,443,-471,286,312,-951,-692,248,-320,-931,-785,847,-370,-850,38,-520,-125,330,469,689,250,-80,-342,-230,292,-527,-874,608,953,-574,74,-417,-124,883}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-545,401,-867,491,925,-55,674,-101,-235,-146,384,-839,-37,667,333,432,249,-622,-730,456,82,710,153,21,737,-779,743,938,475,180,840,319,69,471,-605,835,-672,-233,324,-892,679,688,-430,438,357,735,-256,620,-696,420,469,822,479,-809,488,-839,968,-476,-490,940,9,-169,-186,328}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-943,515,-343,983,503,-992,265,301,-512,-247,960,-424,-84,42,745,-162,-17,-640,-910,246,-534,-633,203,-912,-23,543,64,-672,602,-379,792,238,-304,300,-472,478,931,-302,-366,527,123,470,740,-652,867,-386,899,-190,-460,-406,993,-493,-154,-401,376,364,-231,531,-277,-305,-496,597,63,-781}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{457,-611,-448,190,-18,-815,224,709,167,-743,-311,54,-375,651,-173,815,119,-83,-49,875,7,821,-132,583,-298,674,841,-958,735,-445,130,-888,266,-960,-569,672,300,-992,137,121,-748,598,-931,-834,-997,441,-922,-484,-855,-66,-228,259,-288,-497,481,-554,378,-438,560,648,-173,-828,-861,-858}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMillisOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{408,-247,-884,221,317,-677,-87,-752,-758,-823,894,-74,-12,-344,-875,-162,-356,-468,-197,262,870,336,-110,648,-973,355,925,-737,466,278,822,-545,-221,541,206,-445,-641,23,696,-374,-777,958,417,-53,736,-328,-25,448,603,-948,181,-872,-699,577,837,900,969,-887,82,295,-272,744,-12,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMillisOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{350,424,353,20,-721,391,987,992,-902,-35,-839,-810,-286,599,-639,318,778,879,991,331,137,521,264,500,501,398,-857,138,907,-932,932,-555,-932,-590,836,535,-762,-463,697,877,-181,385,-54,80,-360,123,-180,257,-444,826,870,40,805,-250,890,718,-553,-28,-469,-43,-891,-822,-937,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMillisOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-837,-329,-151,758,456,761,593,404,-459,544,176,566,-965,-623,-903,520,-220,946,426,600,-91,163,-946,-593,213,-919,631,98,220,945,-893,-673,-62,520,-415,989,-283,508,976,83,-686,-983,904,-259,936,-161,-796,722,-272,-452,404,-438,-972,-611,-331,936,519,-726,800,-221,-630,291,-893,-750}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMillisOfSecond(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{946,298,454,-893,437,-192,-466,207,-509,-821,-958,798,116,238,-854,-323,637,-230,841,-69,-139,-213,-733,-518,879,-362,237,-887,-874,369,-137,231,-408,798,-802,731,418,471,924,-977,472,846,-682,-472,-996,859,-82,-225,-54,369,-344,-401,35,881,-445,875,575,-582,520,-248,-657,454,246,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMillisOfSecond(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{829,-529,667,-362,186,627,-393,92,68,150,-85,733,559,-7,-421,-682,-675,340,-57,-513,8,732,133,473,751,-865,146,836,343,-791,-678,317,-221,-852,-973,90,-696,-905,-553,-686,-541,393,576,-809,-949,678,728,523,-470,185,195,-855,-190,552,-975,-444,214,-137,329,564,950,714,18,-180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMillisOfSecond(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-386,630,719,-496,204,-617,558,-364,64,-635,-754,92,383,355,-957,837,118,-741,476,190,-256,-979,188,667,-116,-564,-295,-423,-117,178,464,-444,-339,-517,435,339,499,-453,838,769,824,-34,-418,-705,-512,-718,-670,12,-429,79,360,-298,330,964,-233,-766,-485,-754,989,215,-970,-852,-908,843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMinuteOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-974,-372,-748,181,915,334,666,-474,355,871,-385,89,543,34,568,69,389,-647,277,-618,-345,-690,-243,634,511,-948,-830,908,376,912,524,29,-368,949,-626,-353,156,794,-439,-673,447,129,463,661,605,-715,142,371,-603,-350,369,325,202,-159,561,464,794,-71,-662,-260,-441,392,630,336}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMinuteOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{562,-600,-688,992,803,707,-136,-57,679,355,933,470,-873,-428,-138,507,501,194,49,-250,-364,-279,-996,-54,827,342,302,-394,836,-251,144,-174,-155,502,466,-413,-463,-249,457,-233,395,180,686,898,736,189,-944,671,5,535,-692,-856,415,-84,918,689,143,-679,-142,-207,-15,350,943,-965}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMinuteOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-957,749,251,-858,179,-727,-699,-651,-680,-90,-783,620,-572,629,337,893,622,-783,-798,-579,300,-591,-481,924,-115,855,305,-884,468,-114,27,-612,-646,959,864,188,837,-290,998,-593,-579,-388,-672,-40,117,-629,-97,-321,476,-586,957,608,566,-929,-524,-158,224,-600,936,172,-378,-5,-977,-41}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMinuteOfHour(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{358,-337,-852,880,393,-910,-783,49,603,-240,-313,-170,-89,510,-891,-412,-899,-366,-190,-593,-239,905,973,-547,375,-380,162,-645,0,-490,-838,-545,-113,607,-730,700,552,-788,753,-330,318,11,-879,-207,-868,-904,-973,-867,614,-878,-818,-689,-307,176,473,-892,945,83,641,66,-186,-217,342,32}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMinuteOfHour(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-575,-985,406,-603,246,875,97,-552,598,-770,745,969,908,563,-48,-366,921,-628,451,636,405,126,990,-762,545,-867,-475,378,-144,233,-569,-223,-724,495,-290,917,-683,175,772,986,-382,-925,250,421,64,-875,542,-967,764,549,-722,877,-419,273,-232,-7,691,373,617,-54,-323,-677,181,-313}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMinuteOfHour(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-507,649,516,-749,4,-910,-779,-843,-49,-290,462,-591,-465,-725,-749,-703,68,-986,692,-935,-275,-235,-28,-689,298,-757,213,-718,997,309,606,944,-819,397,529,692,-889,-689,929,241,298,-487,176,816,820,-315,-233,87,115,900,-178,-524,182,-977,-861,445,-139,-887,-291,139,-67,-444,-914,-599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMonthOfYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-700,-14,952,-703,919,517,373,-515,-604,-456,421,569,718,-813,672,702,130,-472,-86,252,-459,365,-940,879,966,-130,478,965,-423,66,556,-480,-722,467,562,-627,-663,-439,437,863,622,873,1000,-463,207,308,243,-590,-245,171,893,-760,-54,801,-800,976,448,-860,-937,-991,451,687,996,245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMonthOfYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{486,889,-556,-861,562,-472,-397,911,8,-630,814,-491,-904,651,-903,884,-643,720,917,343,-45,595,791,717,342,659,-110,-807,949,-920,-348,-373,-993,0,653,-155,243,848,544,-190,-847,-790,-254,877,842,-354,-241,362,-11,-480,431,-433,-581,501,-634,-893,-45,706,743,873,765,-353,259,-704}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMonthOfYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-705,599,618,-226,596,270,684,789,443,264,-967,-200,-461,-155,-520,-260,-733,39,-777,-474,-916,-798,-687,-589,-511,-943,370,33,-267,-293,-15,-53,821,-372,515,-956,-24,920,-498,-290,717,-804,-158,-141,954,-578,-991,12,136,-266,109,509,-677,802,391,623,-660,673,901,859,-374,177,466,-963}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMonthOfYearShortText():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{355,-910,-633,-419,-875,789,-812,-474,635,-778,-272,197,-971,-992,-929,152,-428,871,-742,-683,-730,408,813,-642,15,405,179,-372,-71,-85,-249,-6,604,-311,-486,-112,-789,-247,-925,-238,-887,915,867,809,493,-164,-362,-709,-190,911,23,-906,-595,-335,916,236,485,-373,660,-6,-142,-288,667,994}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendMonthOfYearText():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-410,749,-824,796,203,511,244,-300,-200,469,-248,-720,-547,422,-731,-394,570,-127,90,860,675,75,-717,598,38,-420,956,-990,-18,-989,57,991,458,368,617,484,-671,349,-101,716,661,-133,626,-263,319,-753,-805,-59,143,424,565,-825,-917,-128,395,-567,804,957,-108,-824,491,-37,869,849}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendOptional(org.joda.time.format.DateTimeParser):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-398,-820,77,-133,-421,92,-68,241,301,338,-271,-914,870,182,337,237,491,940,9,-364,657,632,979,783,-556,-23,-661,862,-479,436,282,329,931,-263,405,820,-107,-737,114,650,-52,32,399,-112,-415,-426,-759,2,-800,991,-263,426,-326,-315,674,43,-829,820,716,-472,-147,-548,-362,801}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-723,-766,-542,-932,689,823,39,-493,493,1000,297,-130,1000,-556,-635,529,118,278,-101,-17,451,-1000,-667,208,191,16,-1000,478,23,-1000,109,974,-824,-702,11,-257,-447,1000,127,-808,-88,812,-399,819,616,-278,-358,-728,129,218,796,277,644,743,-531,1000,-187,-523,195,-450,-895,265,1000,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-112,1000,-1000,111,-158,1000,408,28,-457,-63,918,-272,1000,-544,467,1000,-600,-532,1000,-123,-1000,-322,-451,528,-641,-448,-316,-392,-573,1000,-554,1000,1000,318,366,-499,47,-378,954,224,454,460,826,-659,-139,308,36,1000,-490,1000,-1000,1000,-65,-1000,-468,-45,915,-699,1000,110,-1000,1000,-94,-422}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-290,219,-915,111,-45,1000,667,-1000,-416,-68,869,-272,1000,-557,159,1000,-843,-918,1000,-495,-1000,-967,-620,-179,-578,-442,308,799,-1000,1000,-319,627,-1000,-128,1000,-456,47,127,-98,743,-21,-286,1000,134,-289,83,-658,-166,-490,1000,-374,1000,70,-12,-649,361,-1000,-699,1000,434,-559,1000,-1,978}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-109,377,-702,799,-665,399,957,1000,-1000,439,259,119,28,985,-32,1000,-124,397,218,-144,-324,-254,-667,-904,191,-745,40,511,-916,675,109,296,304,-702,426,1000,-447,815,548,-808,346,146,-598,-9,-862,-427,105,58,122,425,-618,169,319,-39,-531,-1000,664,-215,611,1000,4,-830,-238,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-68,-79,-1000,-265,989,-400,-164,-796,-722,-550,766,-1000,616,-944,-1000,-181,68,-1000,-192,-104,233,-1000,-966,870,-272,949,313,-508,580,350,-146,1000,1000,-700,321,109,-308,418,1000,172,1000,570,365,-576,213,903,554,299,-311,1000,-1000,478,-1000,881,444,718,200,702,-679,332,-353,1000,196,592}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-267,1000,-790,77,-354,1000,799,114,-1000,841,844,-1000,1000,477,-41,1000,-1000,-745,66,-336,-403,-1000,-455,-691,-746,-701,161,591,-862,-30,-574,-956,-35,-13,1000,619,585,-566,1000,899,313,532,770,228,-252,133,82,394,882,1000,-751,648,-19,-1000,-470,-497,233,700,1000,406,246,-141,1000,-693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{754,309,1000,109,846,-70,-619,-404,742,82,109,-260,799,1000,920,-76,-2,743,466,67,289,1000,-31,910,-515,1000,929,-540,160,894,-666,526,674,1000,-544,-204,-630,1000,-441,-794,-183,775,-626,214,383,660,414,-266,112,-228,-646,386,-446,985,-52,1000,-11,-203,-863,-544,-941,254,92,413}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-27,-75,-530,-642,81,1000,254,530,-810,641,1000,814,1000,-590,-334,1000,274,-89,-252,126,-1000,886,267,206,-1000,-497,2,-268,-72,251,-526,400,1000,871,907,-216,-998,-596,1000,378,1000,515,1000,824,-851,724,224,1000,-593,607,-31,461,-1000,-604,-520,-669,374,1000,1000,865,-46,-1000,-519,514}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{1000,1000,-853,1000,564,-226,1000,1000,-810,561,1000,-631,1000,1000,1000,1000,-930,-889,1000,-557,367,316,149,-584,-1000,-652,91,-329,-954,1000,-1000,-1000,702,1000,221,734,1000,195,1000,-1000,-287,413,887,-346,-1000,536,759,-157,917,-261,-1000,804,-360,-1000,-170,-1000,13,1000,206,1000,1000,-760,530,-611}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendPattern(java.lang.String):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{454,-1000,586,92,-275,-1000,480,631,961,208,-46,583,-509,-77,269,432,114,-244,-84,241,-340,698,332,-617,-137,858,-427,-383,789,987,-761,-646,-1000,270,-406,52,714,1000,-504,-1000,-970,935,-698,350,179,-1000,523,-624,-679,-429,-371,-1000,945,44,-614,1000,-286,4,-1000,-17,421,-842,-28,141}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSecondOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-252,540,-831,334,764,-311,-604,-376,432,676,-676,-757,-868,860,-16,326,294,-857,451,-198,431,-169,-446,-357,-88,-813,-98,-68,-899,-795,314,-543,-927,293,-59,-494,492,-126,-588,-156,442,-304,-786,507,-985,616,-865,929,-741,1,-864,-803,-238,-498,-741,784,552,-42,301,-42,301,-578,-82,267}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSecondOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{372,-444,-168,300,250,68,970,-757,763,112,152,-141,-409,504,-657,-71,793,165,-912,-693,931,444,481,-614,-29,-361,17,43,891,813,-804,807,-537,95,934,-49,-899,-979,-78,-460,829,-768,-113,60,-543,829,-593,-771,-141,-947,-612,-230,-102,-414,-381,554,975,752,907,328,-562,-854,-770,-525}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSecondOfDay(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-565,-250,-218,684,-17,653,-283,326,929,254,-79,-621,787,248,9,-635,-460,445,-456,-449,936,-260,46,599,-236,791,381,390,-649,566,215,-682,-863,66,-534,364,511,-140,-289,573,-643,-149,367,-832,-306,-341,127,-542,693,390,605,955,919,-824,457,680,-851,856,321,470,333,438,-152,327}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSecondOfMinute(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-923,-169,202,-695,-482,-859,-401,151,818,-766,-572,-966,-484,-704,172,660,877,820,520,732,777,477,782,581,713,684,-402,636,603,-912,267,-370,-519,322,979,388,-780,-406,-210,-6,950,-54,260,209,-17,-657,-674,-886,743,137,-773,-876,441,-68,59,-241,-925,-646,888,-139,-319,858,-731,-713}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSecondOfMinute(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-458,58,-201,67,-166,441,731,428,-421,-185,-804,-658,816,-617,104,-788,-406,637,-793,166,-776,328,643,-223,197,769,-392,788,506,232,527,294,-463,378,-899,-214,-277,-171,-900,419,692,-710,-97,-54,783,-63,421,839,495,-811,-728,-681,-337,434,-148,240,-994,-199,300,-927,-959,-355,255,-410}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSecondOfMinute(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{20,722,84,-696,839,980,323,-685,-331,559,168,736,-914,302,284,-963,-209,-169,-229,516,26,518,-888,908,91,-918,234,997,90,-731,999,-133,-504,165,368,-672,-886,943,55,-943,-405,-905,539,-968,-221,507,12,-40,822,-819,6,-443,342,-191,-722,783,592,-55,-166,-546,98,117,-87,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendShortText(org.joda.time.DateTimeFieldType):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-74,554,238,144,-260,-751,924,-500,560,-43,-536,337,-815,-861,-597,-567,703,358,-277,152,-340,598,-466,-322,537,-948,904,-385,-240,198,-32,689,-453,144,281,-664,-106,676,976,359,729,-763,-303,-517,-154,-868,506,664,-379,666,19,-719,702,630,862,-10,-126,583,204,939,129,-292,625,269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendSignedDecimal(org.joda.time.DateTimeFieldType,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-45,837,18,53,285,439,669,-272,198,134,-221,946,333,236,266,-286,-477,726,-704,951,829,-531,205,139,-105,376,839,955,-177,839,745,555,-733,702,-502,754,261,-343,720,681,-608,542,818,647,391,269,346,-36,-71,303,-970,-774,-516,518,952,179,507,34,178,-295,-215,227,-806,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendText(org.joda.time.DateTimeFieldType):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{205,-952,-421,448,342,996,627,174,-806,-914,38,-822,-285,-234,-418,78,240,-640,434,-14,-93,-521,616,35,697,38,110,76,436,953,-579,758,-979,546,-391,-227,-876,-701,38,116,679,-318,458,-118,-452,502,403,-657,979,-545,-648,-172,-345,379,-384,263,458,92,505,-829,-549,-378,943,349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneId():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-843,-783,-873,148,-570,652,585,-891,72,-886,-321,-578,502,-959,490,-830,285,-867,-112,776,-936,-931,914,-154,357,-553,-948,993,-710,-610,259,-947,665,519,-230,-713,264,730,-62,-976,782,600,-339,-443,378,-883,-670,-962,461,-57,-369,639,37,404,34,-704,736,-959,-651,982,-591,-322,339,647}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneName():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{79,-715,-446,835,220,415,-615,-767,-276,-825,711,-808,-702,-833,844,-399,-822,827,649,396,118,-771,-186,123,-591,-347,880,-357,922,554,595,700,474,-454,20,-86,-323,138,202,-409,901,-939,206,685,959,269,843,223,-314,-72,509,-443,-260,-936,-776,919,-398,917,-835,-73,-973,524,784,928}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneName(java.util.Map):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-885,262,414,-433,675,758,460,-735,446,538,-826,517,-978,311,369,152,868,790,-929,-925,791,-647,-736,-82,-834,575,785,780,289,45,387,-530,58,-403,164,28,297,557,-913,-546,-537,-18,237,-437,-775,955,-556,669,-534,-33,537,408,609,189,167,412,-117,980,-739,-182,-902,-610,546,-433}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-671,-939,-288,731,-176,1000,-614,687,22,62,-800,-316,-1000,992,823,-654,185,46,109,-627,-795,-628,1000,933,827,-1000,-1000,-170,438,1000,1000,-1000,273,1000,594,861,1000,-404,-976,1000,-568,970,-473,1000,52,-495,-649,-1000,40,511,1000,-1000,505,-1000,45,790,-1000,80,816,-389,-787,1000,-413,-169}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-460,656,-788,24,662,363,-311,-838,-185,874,-480,397,244,-372,-825,-956,-35,804,-659,-670,329,242,369,-581,-645,-20,598,233,-873,-233,-115,-424,-534,-376,-589,825,-987,790,523,774,-112,262,184,71,-811,525,482,163,-298,-937,537,631,-577,-263,-830,380,764,254,-284,-718,-378,583,494,-470}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-671,1000,-288,653,-418,1000,702,541,22,40,-713,-767,1000,23,-13,-552,-1000,451,-560,-627,-170,-806,318,1000,1000,-1000,869,238,840,691,412,-1000,-232,638,101,42,1000,379,749,-649,-475,682,-125,1000,595,-130,747,-376,30,613,1000,-83,-728,-1000,580,880,-1000,-1000,-1000,-389,848,1000,-447,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-104,400,-387,254,511,-571,-979,733,-521,894,30,918,-842,-795,876,944,479,458,320,-221,-380,389,-217,-765,399,991,34,-781,-552,172,-896,-771,-838,571,-288,-68,-667,-120,-710,897,445,-56,-553,499,-373,-771,593,-602,463,-691,-640,42,14,474,-546,-836,711,726,609,416,-920,31,-573,-71}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-332,-822,-899,-953,221,1000,64,-60,601,-538,103,652,-1000,-389,480,-395,-395,-1000,-549,141,-274,133,37,-554,1000,-106,-490,261,-67,-1000,1000,-1000,461,-1000,504,462,339,666,-293,71,256,549,179,-653,-480,221,198,346,191,-688,1000,-193,-950,-845,-964,730,-330,-505,-924,923,418,214,629,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-926,-549,-989,-57,-470,174,-907,-946,359,-484,834,581,-827,503,729,-695,1,-865,-793,273,89,659,550,-451,551,290,-755,-231,451,-556,623,42,443,-826,448,-61,-293,712,-959,0,603,743,452,491,-818,371,221,-24,516,-396,362,-300,-353,-371,-677,712,-108,-841,-102,230,233,666,-672,-184}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{701,-798,-802,-489,257,554,538,638,121,403,-368,535,-557,291,409,-195,-357,-414,99,133,180,518,-778,-917,591,-802,375,660,-730,-462,553,-938,-6,-334,546,-765,805,590,-142,531,532,626,546,-915,-7,416,408,349,-310,246,998,-356,-316,-189,-877,-450,265,562,-341,494,5,360,799,-997}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneOffset(java.lang.String,java.lang.String,boolean,int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{844,-726,184,709,-32,41,-263,465,-606,974,502,-324,451,916,293,-687,-402,-792,536,-31,422,576,341,-182,63,-640,-461,-669,791,-444,174,238,-960,774,-193,-652,777,-592,-169,-413,-214,-931,5,883,173,625,376,376,197,102,-104,837,-613,115,-871,159,851,-497,699,-695,-360,422,-649,-802}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneShortName():org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{232,389,44,-638,-636,-508,911,-807,179,-15,440,-811,364,954,867,390,-860,-239,771,472,317,373,368,179,-351,197,625,-140,806,857,881,-171,-784,211,202,40,-308,-864,-953,-280,286,-858,9,-12,-866,-299,-669,298,-815,-680,743,530,-352,-113,-279,981,783,-463,-289,142,179,-572,87,-809}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTimeZoneShortName(java.util.Map):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-42,602,-173,-81,222,698,456,55,-991,-518,572,681,201,452,801,-993,-207,-863,215,326,166,-59,-296,-425,-867,545,870,-87,-564,-671,974,161,-809,-237,-194,-802,176,335,243,823,754,-829,759,963,-227,-733,-846,646,807,972,386,658,767,-772,-458,-553,-934,-67,-303,503,-706,774,-298,-825}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTwoDigitWeekyear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-381,767,573,618,-682,-547,343,-624,-92,-58,-631,810,167,-761,-815,275,-754,139,765,515,-438,207,111,-261,499,469,332,-448,71,-367,888,588,-827,-709,221,-248,-767,570,-587,-574,-866,-986,-326,-333,-861,82,-504,-539,195,573,131,455,492,-431,660,204,-573,-1,410,-31,248,821,692,345}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTwoDigitWeekyear(int,boolean):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-875,-191,441,223,764,510,987,-330,-711,-498,517,-85,-309,685,-739,-328,775,514,-399,-605,-977,880,438,-733,967,9,-458,92,-392,-345,-227,579,122,-664,-16,380,643,204,92,492,-725,212,306,-887,637,709,861,-73,225,336,682,899,689,-880,-582,-828,686,79,981,-624,424,-704,347,811}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTwoDigitYear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-127,250,538,-901,-364,-263,-377,-593,-344,-586,207,944,442,-700,296,-15,842,505,524,903,-3,922,113,-943,51,-743,-834,-93,-29,-826,211,701,-500,-323,-35,-9,128,-284,319,-399,778,441,-251,-354,-141,-479,491,-234,300,549,-845,785,260,619,738,708,-714,911,398,-841,-121,328,-177,-834}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendTwoDigitYear(int,boolean):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{485,-996,571,129,409,-741,-699,309,-593,-36,-613,788,-941,-375,-42,-212,-707,553,509,38,-311,-337,-980,-345,-623,-573,-76,939,-179,-720,593,498,-792,314,940,-487,-949,775,-814,-642,-859,555,-99,-724,51,-340,-2,-637,-989,450,522,-262,-841,349,820,-577,-52,671,921,220,-232,232,-157,-349}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekOfWeekyear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-573,-900,-834,-373,728,961,119,172,226,785,316,229,-927,982,967,127,468,-247,337,-718,-821,498,188,-611,-911,-86,-490,950,-692,615,848,371,27,156,594,578,-787,128,912,518,505,644,-360,263,-800,987,-521,493,-886,436,-976,64,269,369,-176,-880,-141,-366,-488,-306,741,-159,-699,-832}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekOfWeekyear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{47,-516,619,384,-344,439,238,218,812,-718,-738,-671,488,855,808,-833,958,980,-396,-590,307,-111,472,173,-679,-955,-646,-930,-109,-423,320,216,-303,-496,999,945,876,-205,194,-531,611,-83,-855,-969,-854,-21,-202,-829,-423,844,-222,667,-5,-353,308,-566,3,40,233,192,459,197,527,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekOfWeekyear(int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-120,-644,-512,-905,-245,-92,426,365,-570,-820,653,-122,-129,41,807,-383,-958,-300,-437,314,257,-904,-748,-908,133,-923,644,-625,323,-139,-140,919,-188,242,387,-465,526,871,-510,498,208,-467,-985,989,-896,-172,-525,957,853,-109,-362,758,-849,881,-591,-385,287,-158,-326,-477,-592,243,-964,532}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekyear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{271,-800,-603,-924,132,-313,589,-458,751,407,824,-928,-743,-157,-951,-776,-169,930,-354,673,-911,787,464,-122,24,676,-570,183,719,304,978,-873,-863,-910,23,-87,-270,-649,62,-74,-637,484,-874,-970,-908,-819,777,930,713,456,-880,336,673,-138,872,884,946,628,-43,-202,753,670,779,-853}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekyear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{706,-908,108,-79,449,205,-513,-784,-980,291,929,496,-581,210,418,890,-427,-255,248,42,967,-54,-940,343,-278,-636,816,500,746,234,-642,-863,714,-827,-265,-688,52,-721,312,-208,-141,640,819,738,740,237,976,43,912,-38,96,-26,611,-958,160,23,827,562,-758,900,702,979,913,429}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekyear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{657,-568,-614,210,-525,-678,-971,940,693,-833,323,812,644,-464,133,435,-442,52,-374,-759,784,886,467,-728,977,505,-902,644,861,-558,197,613,266,-620,-24,-676,608,150,-208,-664,698,374,717,756,-164,455,150,-532,-494,-657,357,332,-325,-886,-111,452,-300,-253,241,-316,-868,709,-166,363}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendWeekyear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{352,-747,884,-711,-989,-973,-134,69,965,-483,760,622,416,902,-600,266,-947,-982,202,-162,662,530,332,-325,401,813,-503,-689,-447,975,663,492,632,20,537,304,-788,-576,479,-845,-462,559,673,-831,-934,330,835,144,443,-823,-482,640,115,124,-828,992,738,-18,-509,-912,-94,-8,-682,610}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{186,276,-204,-593,-996,-527,-198,-506,218,-805,559,-34,-195,-204,10,204,-605,-354,890,-951,-900,876,743,-499,-831,525,-137,210,964,-762,-310,-127,805,-836,-670,-530,-211,-761,437,67,884,-337,907,815,-706,47,-395,747,-615,-567,-382,955,-685,-784,438,855,897,-174,-88,-652,-783,972,781,924}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-1000,-291,719,-246,-978,-600,796,1000,323,584,-1000,278,-1000,21,1000,-572,-1000,-93,610,1000,386,669,1000,281,-677,94,283,560,-965,-55,1000,-928,-1000,-616,-189,-84,829,-191,-1000,565,-1000,1000,-930,76,608,1000,804,-1000,441,-1000,-36,97,-850,232,-766,1000,-288,-1000,176,70,-768,1000,-927,-881}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{738,387,-909,210,277,-342,-630,-985,-656,919,871,573,892,544,-833,-736,666,-960,-126,100,-219,-50,-632,5,176,-956,532,-375,-892,-841,805,-31,-75,-502,834,-987,-83,784,700,163,227,-756,830,-585,-496,-899,509,977,289,969,174,155,945,342,591,-641,358,254,411,262,-373,-918,-347,322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYear(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{735,457,-233,-464,513,709,533,-312,405,-753,-979,960,743,874,355,-921,-48,-591,-400,-599,753,863,820,-957,-650,-559,-469,675,21,663,-483,759,-469,420,-936,801,411,304,-737,17,-703,175,252,-312,229,594,-3,-534,-887,496,526,-124,391,-738,445,-637,352,-765,-68,366,-479,619,-508,-899}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00125() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfCentury(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{248,1000,-227,-1000,-453,-1000,-1000,373,-1000,-872,295,-110,91,119,302,856,1000,-638,-622,945,1000,-426,-1000,763,-1000,1000,-1000,377,628,148,533,1000,1000,382,-1000,1000,971,1000,796,-327,1000,377,1000,1000,1000,1000,-1000,242,416,-1000,-426,449,-1000,1000,-331,562,-335,-1000,1000,1000,-762,1000,-194,565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfCentury(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-818,-833,252,734,335,324,-16,-851,-165,-131,631,-200,-376,4,-736,48,385,586,595,-798,-896,415,629,818,389,12,738,650,27,-743,-561,-256,-607,206,-430,-232,620,131,-618,72,-812,469,-868,-769,538,-338,148,-176,638,148,-925,94,-943,-272,29,-217,-985,-651,-290,-95,142,-971,-801,-473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfCentury(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-675,523,-589,926,863,-156,-214,314,765,-255,-202,892,444,-669,-754,-252,-698,378,-768,205,349,152,635,75,-993,710,770,-637,-741,682,-853,-463,-660,452,-351,88,533,453,-723,497,-666,825,647,978,-775,-889,872,-604,-396,819,-861,604,65,-272,-667,139,97,611,397,649,-413,695,-235,766}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfCentury(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-24,-206,-308,-87,-745,223,-907,467,460,-827,-393,-86,-511,100,-347,621,887,612,855,990,-132,199,-742,503,-663,-25,-926,-486,-361,467,434,974,783,453,-961,859,277,613,397,507,391,325,420,-733,665,307,-441,-65,757,40,-648,296,-614,873,311,-261,-974,-426,379,427,-735,690,-121,-248}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{615,524,328,-437,282,-498,1000,-287,866,1000,-1000,1000,-318,293,-701,-181,195,-1000,-16,29,-127,235,-270,72,-1000,-395,-42,-631,-482,1000,313,-318,103,-338,-606,-524,-309,-742,392,-1000,34,-737,-589,1000,-785,911,402,-1000,423,-300,-935,923,-1000,1000,-87,601,348,449,88,1000,-402,870,144,-434}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.DateTimeFormatterBuilder", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{609,-401,-601,-149,333,444,-109,-403,896,-58,-549,252,-689,352,-164,-133,-711,513,-482,44,542,648,-509,-347,-47,-278,-230,-325,517,390,617,-559,-432,-906,-898,-607,-68,488,-149,368,404,44,532,377,-792,420,-8,835,-63,-8,-202,-666,-86,-221,398,-867,-163,-54,-894,790,-854,-956,691,599}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{637,-507,-756,-652,850,205,918,638,127,796,161,170,570,-528,-885,-411,271,665,703,-130,-84,383,322,531,-288,903,-511,788,609,407,491,487,-624,-339,-254,-509,-739,-497,-231,-901,671,-460,-636,16,-403,213,-988,412,-634,-836,-721,703,-316,739,518,805,-57,338,126,847,-751,-647,-790,-438}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "appendYearOfEra(int,int):org.joda.time.format.DateTimeFormatterBuilder",
            new int[]{-55,-470,574,404,-122,-687,380,-1000,-469,1000,-236,232,-41,-1000,626,994,0,0,0,95,-1000,397,-530,882,-89,-748,0,-124,-81,1000,0,-110,1000,0,-1000,-28,-50,-91,-1000,0,-1000,-66,911,320,659,-112,-448,469,-1000,-925,-368,468,-48,730,-1000,144,-1000,-373,-931,1000,44,350,341,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "canBuildFormatter():boolean",
            new int[]{-304,-335,58,331,843,-147,-505,-455,-867,-786,128,-971,884,71,935,350,647,-420,5,734,-747,-31,-535,114,150,973,-810,692,74,776,454,488,494,903,-328,-668,304,-276,-107,-317,-226,500,-181,-264,923,-783,778,397,140,859,-722,850,-840,229,603,-283,294,923,-807,62,-803,-321,517,904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "canBuildParser():boolean",
            new int[]{290,-738,831,115,-133,883,-503,-146,-263,527,347,411,-153,134,-72,525,521,-504,291,-264,24,948,347,488,683,491,-20,708,-580,-795,125,-428,337,306,324,507,400,819,189,-200,188,441,687,635,182,-98,-828,858,-864,888,708,310,703,-577,-635,198,-343,486,909,624,-174,-370,564,-843}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "canBuildPrinter():boolean",
            new int[]{-634,297,57,-364,-685,-640,512,150,-143,528,852,182,138,589,-602,-466,100,879,-887,-594,-259,-400,-29,-623,721,-758,764,868,-261,344,-846,-758,274,233,48,520,-740,145,-448,838,55,175,688,-739,670,877,-187,-694,640,-987,-738,-350,-858,955,124,-128,-988,540,-756,812,169,793,563,-856}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00136() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "clear():void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "toFormatter():org.joda.time.format.DateTimeFormatter",
            new int[]{-238,119,-548,542,-298,591,-309,-469,-853,845,-21,618,-691,417,-708,925,621,883,-36,989,-294,-847,354,290,-454,-842,121,-488,-153,-918,631,-842,-910,-633,704,728,-700,348,-170,-140,-817,-433,794,517,947,-456,-343,-397,866,-866,-957,248,-551,-554,749,-618,-84,753,-138,-782,-367,-177,749,782}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "toParser():org.joda.time.format.DateTimeParser",
            new int[]{182,-169,-226,648,-718,-282,-389,688,189,-852,-880,-533,-203,-700,-165,676,-629,990,299,547,-840,236,185,662,-537,825,71,-356,381,793,892,200,963,986,678,696,-251,416,-340,-637,813,994,95,510,332,-580,189,-246,430,475,445,-901,-418,-201,-594,995,229,-666,297,958,410,-945,395,-770}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("THROW:java.lang.UnsupportedOperationException", DEReplay.run(
            "org.joda.time.format.DateTimeFormatterBuilder", "org.joda.time.format.DateTimeFormatterBuilder", "toPrinter():org.joda.time.format.DateTimePrinter",
            new int[]{331,559,-781,100,345,-174,21,410,-902,-610,557,500,-87,347,122,-778,941,-866,-531,-73,610,186,833,-197,263,147,650,-859,192,841,842,701,-717,-590,112,-645,42,-702,750,-812,-98,-928,-873,-604,407,-503,-719,-537,108,705,768,351,833,829,303,-277,364,-945,-373,-403,-508,-315,-498,235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{870,-79,870,862,-1000,93,122,411,-876,355,-874,-45,-594,195,734,-378,-606,100,338,695,-774,-885,437,-638,-672,-89,-425,-332,821,-776,-464,-283,193,-1000,-450,775,-153,-177,-525,441,233,-528,-130,656,-81,-548,287,481,840,-424,-178,51,-625,-917,-333,-572,193,294,461,-454,611,693,-22,-503}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{318,1000,825,-328,694,1000,-129,650,-876,-1000,-1000,621,-764,469,444,24,-1000,-285,-530,-955,147,-216,215,-1000,591,-547,-617,522,-571,988,-696,1000,-105,-970,-682,1000,790,913,-67,271,-626,-166,-521,1000,-37,-1000,1000,157,1000,-195,-305,-1000,-1000,-103,-691,-1000,1000,58,-803,116,-1000,-1000,407,145}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{564,846,-11,663,874,383,110,118,-21,-629,238,937,947,247,526,286,-469,933,-173,296,-965,61,673,786,954,488,-79,-187,889,-40,-934,-415,968,982,598,-611,-530,-537,-87,630,-290,-419,308,-197,-650,415,-666,-706,271,393,438,-727,-122,371,180,604,-691,-782,603,715,365,-766,314,-322}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{309,-207,106,1000,-16,1000,607,604,-1000,4,364,-24,43,-258,213,-629,-124,138,-63,186,-308,-801,606,561,-324,-309,-316,-258,265,-251,-35,395,-165,-256,-478,296,167,-496,-458,-85,522,-70,-660,-540,141,264,295,37,-326,524,-67,874,-137,-959,-612,-406,-461,301,330,-644,727,224,-315,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{870,-275,-25,-780,-1000,-715,-1000,-611,-876,-704,-79,653,1000,-524,-150,1000,-560,-558,-1000,-1000,-774,876,563,-638,1000,-1000,1000,461,1000,87,-464,-519,-1000,-601,442,-469,-278,-177,-35,-945,32,381,-1000,238,355,490,-122,727,-416,-340,-1000,720,1000,555,-333,1000,-309,-202,461,-75,146,-770,-353,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{-357,-248,452,-425,955,616,-109,-911,-548,880,-437,8,-249,643,-325,-536,139,620,987,-900,-384,892,142,454,-675,908,187,-78,458,-33,387,708,-549,-457,780,240,-227,-586,-881,-7,-48,332,773,399,-389,-829,6,566,-788,-85,-392,917,-979,-348,-690,528,296,810,-396,-53,-814,-729,666,569}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{318,-844,-414,391,646,1000,-229,1000,-675,1000,1000,1000,1000,-1000,-1000,-1000,247,-1000,-178,-618,1000,1000,1000,1000,-425,492,-1000,723,854,-645,1000,-1000,-1000,-717,-415,1000,-83,-745,58,-923,1000,-1000,7,-476,1000,250,1000,-1000,1000,1000,-1000,-724,-225,-1000,1000,257,-1000,-677,-1000,-255,1000,-153,-109,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{99,-230,-454,-219,255,771,66,824,243,-41,764,-327,801,552,694,940,521,-88,-466,349,651,671,-268,660,326,-289,439,-111,41,-797,478,726,679,585,631,-448,486,-851,-873,-8,-996,-286,355,-439,-847,777,-444,618,419,-524,-445,612,-584,714,-617,-647,964,891,-886,964,-740,-194,156,-969}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{-75,-381,434,-423,999,-835,-1000,830,-508,1000,1000,1000,-206,-1000,-84,-1000,1000,-1000,-893,-338,554,546,-90,1000,831,863,204,1000,325,-16,230,1000,1000,-140,233,716,-83,-1000,-59,788,566,-1000,438,-424,1000,-114,740,-959,1000,1000,-1000,-1000,1000,-588,814,-1000,-792,-153,-854,-454,-658,-439,-1000,604}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{1000,-74,-933,214,-637,37,253,-448,886,-629,-545,845,-61,472,546,499,-884,-1000,-1000,-714,-1000,-387,-850,-439,-1000,727,443,-1000,-1000,988,1000,1000,-616,-395,-1000,-909,1000,1000,-255,103,396,650,202,-366,-512,425,979,996,-490,-586,-707,-789,1000,-975,331,-826,-2,983,-1000,440,-600,-793,128,558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{955,-783,-500,-400,-756,27,829,-523,-234,-156,809,-468,230,815,-455,580,932,245,-872,-527,-782,-307,-670,-669,-300,-986,255,728,-769,202,-600,431,-846,621,-410,-227,396,-26,-164,-712,-412,175,-636,-151,622,213,-262,-787,-234,-994,-693,646,-199,315,56,-945,-494,816,-668,-53,-741,-383,-103,-549}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{89,-1000,-426,1000,1000,1000,-1000,-1000,-1000,-1000,-1000,-1000,283,1000,-910,-1000,1000,170,-1000,1000,-1000,1000,825,1000,1000,-1000,1000,423,-1000,-811,-1000,-32,-87,208,444,1000,693,-494,-688,-1000,-1000,986,-240,1000,-1000,802,-1000,1000,543,-651,-478,-172,1000,1000,-1000,-1000,1000,-671,1000,-1000,1000,1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{805,1000,329,109,-1000,312,485,-261,1000,1000,1000,849,-1000,338,1000,-527,-1000,-1000,-579,519,-256,-1000,-750,-317,-616,-984,433,983,909,952,1000,701,840,581,-1000,-737,317,-124,-1000,-1000,-559,910,178,1000,917,584,656,1000,655,936,-950,-598,723,-1000,332,-155,377,-708,-236,-1000,-1000,-1000,675,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{516,1000,-282,201,884,-315,-396,932,330,-329,161,-35,-204,1000,-390,-110,-1000,-1000,353,-1000,-1000,-1000,94,-1000,770,1000,510,696,1000,1000,925,-520,1000,1000,-808,-435,744,1000,-788,-219,-750,-249,-631,740,671,1000,375,-456,-660,-527,-457,-502,558,-678,-623,489,215,-325,787,-662,266,858,213,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00154() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{805,140,-242,1000,826,1000,-1000,-364,-1000,-320,-807,-1000,300,1000,-958,-1000,-1000,-837,-1000,1000,-1000,-318,117,-453,1000,1000,-18,1000,-990,-530,96,993,288,1000,577,1000,205,-124,-1000,-1000,-1000,1000,-70,1000,-738,1000,-503,1000,-598,669,248,-70,1000,260,-627,-433,1000,-1000,1000,-1000,618,192,-769,929}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00155() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{198,-13,-171,904,1000,1000,-1000,-254,-863,-1000,1000,-1000,120,1000,-1000,-1000,-711,-727,-1000,745,-1000,420,246,129,1000,400,905,1000,-787,-600,1000,-823,652,1000,-178,1000,1000,-494,-1000,-853,-1000,1000,-1000,1000,-1000,1000,-38,537,-5,-610,-902,-230,1000,706,-1000,42,-184,-1000,1000,-1000,1000,1000,-371,-836}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00156() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-396,471,-362,668,393,-935,-478,774,84,839,650,-1,129,667,233,117,-856,-549,849,874,-637,-875,82,546,-721,567,929,53,-883,816,861,856,14,-921,-534,-968,146,663,-294,-434,-595,216,7,179,707,399,-384,815,516,-491,-17,-706,-251,-566,276,-431,882,-172,-452,531,-232,-597,-416,370}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00157() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-1000,621,-58,455,-928,933,-367,-183,-140,-999,-897,-1000,-1000,494,436,-985,410,-1000,-856,1000,-105,-138,28,369,-370,-596,988,330,91,1000,-236,-1000,-664,-362,205,223,-31,-187,-174,1000,-817,1000,-511,1000,631,-385,-107,157,624,366,-1000,857,155,-745,-1000,1000,24,-378,310,-775,1000,912,-1000,-550}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00158() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isLocalDateTimeGap(org.joda.time.LocalDateTime):boolean",
            new int[]{-615,206,761,272,-440,-361,-41,-873,939,-264,-379,669,-798,-41,-809,157,709,423,-217,792,19,20,-851,-539,-126,-831,639,-419,-295,-386,652,-904,-439,508,120,4,210,511,744,828,-929,-389,-123,949,-346,-77,-766,-194,-799,-106,-65,652,-652,979,-229,-244,-191,153,61,-275,-697,-605,951,-13}));
    }
}
