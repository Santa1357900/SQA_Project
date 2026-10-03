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
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Days", "", "parseDays(java.lang.String):org.joda.time.Days",
            new int[]{527,-978,422,-864,-912,324,-927,135,-395,496,-573,441,-915,682,-986,-883,-8,684,-565,-125,949,-391,596,-412,-46,996,-663,-332,994,-494,960,644,-7,144,993,-844,-926,-662,361,998,-715,236,95,-146,-870,51,-346,956,-483,-576,12,-13,-930,410,773,258,-870,931,-268,-551,-750,-703,-441,995}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Hours", "", "parseHours(java.lang.String):org.joda.time.Hours",
            new int[]{-927,9,-613,-566,-442,-52,970,-971,432,-640,-35,731,968,513,306,904,419,-696,-612,452,51,-673,672,413,-98,831,414,192,-328,-982,-979,439,-746,-285,-277,-552,-821,-828,-964,910,501,431,758,-113,-481,-468,157,920,-888,679,-568,-624,-531,421,-486,293,495,866,892,285,296,-980,-806,-621}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Minutes", "", "parseMinutes(java.lang.String):org.joda.time.Minutes",
            new int[]{699,723,-927,-502,-793,-642,-3,348,75,921,408,421,43,-463,-844,181,294,-73,-251,-227,189,913,-466,-829,222,-510,-116,437,327,94,-945,-686,191,-474,82,-343,-392,-917,584,652,-955,-106,-313,-321,-407,-801,-617,786,-744,369,-376,205,-871,-176,166,153,455,-289,-195,191,-255,155,970,-137}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.Months", "", "parseMonths(java.lang.String):org.joda.time.Months",
            new int[]{531,-398,785,858,565,384,925,646,-903,-268,859,964,-385,502,-81,796,-842,-938,70,-718,774,-14,-484,-67,798,-951,766,37,-591,-912,-681,-702,668,704,818,-675,353,-1,-487,954,284,608,-823,-388,566,-899,-757,-78,179,-456,-137,-433,-285,441,-179,-909,-327,-402,808,723,-283,-452,421,-380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{1000,-716,-171,-155,-921,-812,818,-481,-1000,-1000,-252,-1000,-1000,-1000,-56,268,921,-179,336,-610,-389,-272,918,-343,-1000,362,640,1000,693,1000,-1000,-279,567,1000,-635,-1000,-388,-453,-1000,-558,990,781,1000,863,1000,-644,788,529,1000,1000,-323,-411,238,-829,1000,1000,345,-426,-500,-16,-249,1000,1000,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{1000,159,-171,-292,15,1000,-1000,7,222,-114,-59,-1000,-1000,632,-646,268,-1000,-1000,944,-708,615,390,1000,-343,413,430,1000,726,693,1000,656,-510,-1000,-1000,71,-651,-1000,-1000,-1000,-359,517,990,689,-727,-788,481,-1000,503,-780,1000,-1000,-1000,1000,207,58,425,-800,-1000,-1000,1000,144,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{446,661,-313,-474,-420,-42,-617,496,-313,-707,747,-1000,-1000,-463,-164,710,167,-711,899,504,-440,-101,1000,732,-1000,801,645,830,313,1000,-512,-307,-347,115,592,203,-432,-1000,-775,-959,-99,161,-52,-1000,236,-285,684,353,1000,553,219,-420,452,-1000,1000,129,767,-321,393,589,-400,-530,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{-1000,492,-227,716,987,1000,-419,1000,-373,-741,-68,-1000,-965,291,-534,-512,-414,927,881,-1000,54,423,-634,404,1000,1000,1000,438,10,262,-259,-254,-654,-1000,1000,-471,-420,-1000,-1000,386,340,19,297,-1000,-525,881,20,707,-172,614,-20,-1000,545,72,-147,225,646,-872,-200,640,-389,-1000,-77,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{-20,482,354,-226,-183,388,-436,-471,-211,-773,798,-178,-305,-452,-603,341,-419,259,1000,506,496,-323,-20,1000,142,288,-28,549,-697,-461,174,-508,207,-774,694,807,13,-464,-157,-374,360,-558,-326,-1000,-616,24,-918,-950,-751,-248,-101,628,235,644,-416,904,112,240,1000,473,-353,-282,-360,-91}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{743,900,-42,-19,647,1000,-135,-683,77,104,-649,-1000,-1000,-116,-636,-767,-1000,-700,636,-1000,844,1000,436,-927,1000,340,-389,605,165,1000,1000,-896,-1000,-1000,-153,-1000,-1000,-1000,-1000,396,820,1000,1000,-239,-1000,1000,-1000,956,449,1000,-1000,667,1000,753,-423,259,-1000,-1000,-1000,1000,614,-1000,881,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{-1000,443,728,638,-612,1000,618,-489,638,-191,911,899,811,106,-1000,-403,-890,1000,821,-930,612,-1000,-1000,527,1000,-775,-959,0,-723,-931,-206,-527,1000,-62,-1000,260,-752,1000,131,28,-226,576,936,157,-753,-307,-282,185,278,499,-101,386,-402,267,-1000,499,-296,972,773,646,-66,102,58,-534}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{-604,38,-313,-3,35,622,-617,235,-313,-1000,1000,-63,-191,-839,385,849,620,907,558,576,-372,-915,-1000,505,-401,601,572,788,-1000,-1000,-928,-344,137,16,1000,203,899,-35,-229,-47,-969,-925,26,-1000,-466,500,787,-1000,-1000,-1000,1000,-420,-958,-1000,830,738,794,400,393,188,-554,929,-1000,81}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long):void",
            new int[]{-1000,-179,314,928,-1000,1000,195,-906,917,-1000,-19,834,-330,933,6,80,-1000,1000,1000,-1000,28,419,-1000,589,1000,1000,521,349,531,-1000,333,-502,-457,-500,429,-28,231,-656,11,580,-660,-638,-746,354,73,345,-975,1000,-1000,1000,525,131,-1000,264,63,755,18,80,1000,1000,-856,812,-368,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{1000,495,294,528,-396,55,-242,664,795,1000,538,-1000,598,38,-1000,1000,143,-1000,-922,1000,511,201,1000,934,-444,229,518,236,-1000,-587,-607,392,-1000,-390,1000,-519,-1000,-1000,-580,-1000,666,1000,328,-212,-429,412,-462,-403,1000,808,-1000,1000,-1000,1000,1000,496,-563,-1000,1000,1000,222,-38,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{-213,-842,-1000,1000,-1000,-294,359,600,-554,641,762,-1000,254,1000,-372,1000,-703,-980,-1000,1000,676,1000,1000,55,-1000,-365,-1000,-400,-468,-640,-501,-1000,-401,-1000,1000,-811,-171,-1000,-76,-833,-781,-10,1000,1000,938,-1000,322,-1000,1000,1000,-303,677,1000,1000,1000,1000,151,-127,1000,-840,510,607,888,385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{571,814,227,-665,-374,815,-624,696,-549,247,288,-581,587,320,-752,1000,256,-1000,118,773,-154,136,919,-60,-622,289,400,-210,-1000,99,-403,399,913,80,19,398,-880,-1000,-529,-1000,571,-935,259,845,419,-474,995,826,722,264,513,411,260,-789,-199,676,475,489,-100,-572,326,159,331,807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{590,584,669,-939,197,635,-573,699,751,626,1000,-291,110,-685,-726,1000,414,-680,-205,1000,573,194,562,-68,-641,-246,-94,-560,-454,792,-693,1000,936,991,289,-437,-226,-455,-227,-364,201,117,265,326,-117,-454,133,845,416,-512,1000,81,-39,-239,165,612,534,-456,111,-331,551,-177,-436,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{992,433,1000,-1000,-77,580,-486,750,751,222,388,-112,-431,-1000,-1000,698,909,-552,-395,275,626,-758,562,1000,759,-28,377,472,-694,1000,-275,1000,977,1000,-524,-437,-737,-455,542,22,-483,529,-1000,26,382,-568,222,845,-518,-1000,-60,-359,-1000,-554,-7,45,675,-1000,-303,-331,652,-156,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{702,648,513,-627,733,570,-699,151,324,160,257,514,786,-780,-978,340,1000,-895,1000,931,114,-334,335,359,-276,-607,663,248,-776,561,-383,1000,-1000,994,164,1000,-807,-900,192,-697,644,-394,212,-716,-1000,-1000,1000,1000,463,-659,1000,406,-113,-246,-420,4,-54,-666,-51,-333,592,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{162,-208,11,-698,509,1000,-1000,966,1000,-5,1000,-624,-361,400,131,898,82,400,702,823,1000,377,152,-715,-708,-1000,-255,-910,-393,341,-1000,377,552,1000,792,-1000,321,139,-70,-474,-134,681,702,-69,-20,-283,-1000,436,956,-8,1000,353,-465,169,-486,1000,923,-221,-469,-1000,967,-398,38,380}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(long,org.joda.time.Chronology):void",
            new int[]{-590,452,367,1000,-105,645,804,896,-585,550,494,-1000,964,1000,1000,1000,-1000,-695,989,748,-142,1000,648,-1000,-1000,410,-1000,-881,8,-367,-998,-1000,-1000,1000,1000,-1000,1000,-1000,-862,-789,13,-1000,1000,809,867,-1000,1000,-614,851,1000,450,960,1000,-946,286,376,-66,1000,1000,-385,-709,1000,68,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-907,-947,-345,1000,-433,-164,-1000,1000,-1000,-1000,-929,-204,158,-490,1000,-327,-1000,-762,274,-48,-160,1000,251,-412,426,-376,-1000,1000,642,362,344,1000,1000,-1000,-2,-833,1000,-1000,844,1000,-800,-1000,-298,1000,1000,-1000,-1000,-1000,142,1000,-406,1000,1000,205,-710,867,110,364,-780,-422,-833,1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-534,-541,-420,-256,-479,-776,-546,14,-673,-396,575,-1000,258,-336,-261,1000,-749,131,980,718,908,-760,451,14,-370,-564,343,332,-712,232,1000,586,658,1000,-866,-1000,1000,-638,1000,-90,-620,192,576,4,-9,178,-680,-358,-559,839,-798,-1000,-630,-1000,-855,-769,-651,-282,-564,-826,773,169,-1000,404}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{359,626,639,-610,874,942,973,587,516,-220,823,458,797,850,65,-468,454,-515,-420,-194,801,-629,-513,150,985,411,-652,-658,487,-415,423,-47,-529,768,-864,-579,935,103,997,-661,0,777,-589,236,-887,813,48,950,-515,-167,728,-940,651,-327,-847,-211,-328,-814,237,-707,-773,-388,-728,307}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-626,261,349,-373,310,-196,285,-658,297,-303,937,-580,-106,37,-904,551,-554,-207,765,264,363,-459,265,-526,-701,-559,114,982,325,409,610,-409,991,18,-201,-386,-382,214,988,-971,-369,313,740,-481,-171,-179,-1000,-777,350,766,560,576,-141,-385,-248,-632,463,-590,-92,512,463,197,472,-274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{839,1000,1000,455,-240,303,628,1000,-281,-801,179,156,1000,610,1000,-607,813,-1000,-926,1000,747,499,-68,180,1000,-87,544,-1000,1000,-77,-326,-443,-1000,-868,-277,-368,1000,-307,1000,-16,740,-569,-686,906,821,-321,-176,915,-110,-299,810,-768,1000,-1000,104,879,210,-350,-74,-1000,-1000,-103,-450,496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{343,-528,-753,73,-142,-866,1000,-308,-555,415,-360,-1000,-390,738,-860,1000,-480,285,821,535,-759,21,-381,-404,-788,-205,840,-407,-86,1000,-1000,-463,-662,360,-568,-509,523,296,345,-104,126,-456,993,-184,112,142,40,109,-925,-1000,289,1000,-1000,354,1000,-1000,-690,560,87,1000,978,-247,82,-558}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.DurationFieldType,int):void",
            new int[]{-1000,611,1000,395,734,364,-1000,1000,849,-983,-79,863,-591,235,613,226,694,-1000,-643,787,524,532,-429,-455,1000,-592,-532,171,1000,-439,1000,109,750,-527,-1000,-161,-1000,-1000,1000,-533,-359,-240,113,211,-1000,-478,-219,326,1000,890,-794,-1000,761,8,-1000,1000,1000,-1000,-748,-789,-26,631,556,-35}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{-400,-230,-432,134,408,410,-97,-314,182,-674,-114,88,888,365,564,-642,459,1000,-1000,-1000,-400,129,-45,1000,-307,75,-623,400,510,-1000,590,-1000,675,605,-819,-215,-669,-159,102,554,554,-12,741,-344,330,877,-1000,-1000,400,-259,-18,1000,31,-18,-69,-233,-111,-656,578,-609,511,444,601,314}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{-608,-737,1000,609,887,-70,-585,405,-114,-1000,-867,-165,1000,-29,-125,-1000,695,-1000,-1000,1000,-1000,-1000,-259,-329,-1000,-1000,534,1000,-1000,1000,-1000,-1000,1000,1000,-263,-298,38,-301,847,-671,-193,-193,-759,-619,1000,898,444,-1000,1000,-12,1000,186,214,761,1000,-786,1000,1000,-1000,88,395,592,-158,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{-1000,17,-897,-1000,1000,-1000,559,-668,-353,-469,565,572,1000,-572,422,-93,250,227,-930,1000,-1000,64,-623,1000,-1000,-483,-222,1000,59,35,-117,-854,1000,1000,-11,58,536,231,1000,-121,622,-579,1000,-733,1000,1000,-1000,-1000,1000,-280,808,826,1000,1000,794,73,-496,372,-1000,297,213,1000,786,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{495,415,668,-1000,290,-991,-1000,784,-443,-338,-694,51,451,484,548,-756,1000,-1000,718,888,-10,-539,228,962,-580,-770,595,200,134,852,-124,-878,1000,233,754,-801,-95,-627,842,-990,693,970,61,-734,1000,865,138,-1000,-889,1000,-563,361,683,571,-849,-1000,1000,-160,-1000,1000,-1000,1000,-771,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{-1000,389,-811,716,770,821,-937,-850,-392,-233,-1000,264,-349,-1000,1000,1000,1000,-669,1000,-457,-1000,-186,706,436,978,-1000,-1000,657,-338,530,318,-499,399,1000,-1000,-1000,-92,427,-1000,841,578,665,1000,-418,-155,14,-166,-1000,1000,54,669,337,47,96,896,-498,-414,439,-924,-976,517,116,336,693}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{18,1000,-502,272,-67,-403,893,-712,-1000,19,377,948,-172,36,-764,1000,-1000,23,391,-816,817,274,-789,-96,297,-259,249,-630,-505,367,400,-465,-1000,162,-19,804,1000,-344,-103,183,888,342,1000,237,-804,-400,509,1000,-307,618,88,-79,110,-414,71,453,-124,-297,-290,-208,771,873,625,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{-384,86,-107,-325,578,-1000,-629,-689,-1000,181,391,117,173,-456,224,234,-512,369,566,1000,-910,-771,-669,481,-894,-171,853,819,211,1000,582,-665,1000,832,503,-56,-695,-779,1000,-209,807,411,558,-1000,577,264,192,-600,295,463,487,-82,-497,1000,887,1000,788,604,-1000,702,252,1000,423,-168}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableDuration):void",
            new int[]{645,375,-982,615,269,-441,186,778,-1000,-334,552,-623,451,-422,-673,-251,1000,111,382,952,1000,1000,253,1000,-360,82,-270,-749,47,1000,1000,-650,635,724,-461,-749,-1000,1000,654,1000,14,1000,1000,-1000,-1000,592,-1000,-1000,-1000,787,-1000,-87,-287,126,1000,679,751,766,-515,1000,206,611,1000,-536}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableInterval):void",
            new int[]{-42,-359,469,744,-1000,144,-452,-537,53,-683,169,-273,159,337,1000,-272,1000,983,-45,262,-1000,948,844,-1000,579,699,-250,173,-200,-153,599,-931,1000,-352,-618,1000,702,-23,-509,-1000,-620,124,685,398,614,-1000,-211,541,81,-554,-43,-997,655,-226,191,157,245,268,1000,-1000,916,-40,-1000,478}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableInterval):void",
            new int[]{-584,760,-462,-422,1000,117,-456,152,861,-871,-644,-594,215,-85,-910,103,22,1000,-1000,-772,-982,-962,49,830,740,-253,-488,429,-165,853,126,-589,238,-152,35,631,-839,-458,-15,147,-1000,-178,48,48,749,170,1000,-325,90,-928,-683,-280,-332,45,-228,-473,-202,448,-778,-404,282,-998,287,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableInterval):void",
            new int[]{211,-221,57,-221,378,-161,-542,35,-293,-468,-181,-452,70,163,1000,-1000,315,532,-550,-141,-183,-230,379,-103,692,-235,-631,-260,-641,-170,-284,-774,505,-269,-269,296,289,-593,-198,-250,-386,-71,446,-71,393,11,-120,112,-81,-630,-328,-464,359,-15,426,-321,162,309,-353,415,472,-517,-99,291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableInterval):void",
            new int[]{758,-698,-247,-615,953,361,137,813,-135,199,919,-135,532,-590,156,-955,358,-349,-364,829,830,-440,-976,-923,-916,-938,-751,175,-619,-179,-994,615,301,-273,-581,-458,496,-729,-633,-609,248,-600,-247,-867,-278,37,-919,925,-168,-187,-895,-63,-75,778,45,-956,101,856,-154,691,946,-374,-643,-252}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableInterval):void",
            new int[]{-304,18,373,376,-925,829,-709,-163,818,-1000,41,-400,1000,-160,-35,-583,1000,1000,939,306,-1000,-21,-636,-655,848,-500,-57,356,-1000,-122,117,-785,692,-634,-824,457,39,803,-598,-1000,-818,129,-485,103,401,-915,954,659,357,270,-1000,-441,253,-304,-171,-767,-52,960,604,434,600,-439,1000,-36}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadableInterval):void",
            new int[]{319,-481,-836,-553,112,1000,-951,798,289,-175,652,-273,159,-1000,-626,-725,1000,963,-149,186,-855,-3,-918,-601,-132,-1000,-250,601,-1000,-132,-1000,-32,317,-352,-1000,-369,277,-989,-509,-1000,-620,409,42,196,442,-189,403,1000,23,-1000,-1000,-348,426,1000,-353,-1000,-596,1000,-136,-836,446,-721,-1000,-477}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{326,-23,-8,-900,632,-1000,-197,499,-636,8,219,533,-618,119,866,-1000,715,-905,1000,-456,-383,-199,226,911,-847,426,569,-1000,-598,1000,314,-391,-537,-225,145,-199,77,476,56,902,-41,398,236,801,-371,880,-202,531,-215,55,-935,-400,-29,996,-987,-219,-591,76,1000,825,-1000,-723,958,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{-344,383,-822,242,114,-204,603,-130,-224,30,97,554,-209,-132,835,-423,-670,730,834,-241,-106,-870,441,-211,-746,-236,-24,-186,351,-190,-110,-985,62,-121,-445,-902,-945,-867,243,-833,-854,-468,-746,-742,535,930,-802,0,94,768,-527,301,-508,-489,-334,-258,342,-366,-778,-531,796,-26,101,726}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,268,-596,-776,-590,-1000,1000,-338,266,-1000,681,-1000,210,-446,-5,-138,-1000,1000,455,1000,-1000,87,1000,-206,188,357,-306,-383,112,-996,935,647,-11,-333,-998,-205,1000,560,-577,1000,398,34,710,-233,-1000,-964,639,-1000,-240,314,1000,1000,249,620,-1000,440,543,485,246,972,901,490,564,-312}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{1000,-88,-401,389,84,915,900,940,-986,-41,1000,-1000,43,118,83,605,-619,853,1000,-498,-1000,-210,1000,566,-407,-310,-1000,347,429,-1000,1000,-301,-1000,546,1000,272,1000,836,-1000,1000,7,-154,912,536,28,515,-657,-1000,67,-474,-341,1000,-1000,690,-847,-49,-28,-864,879,1000,62,1000,-81,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{263,1000,650,286,539,-983,554,-330,-164,-1000,1000,49,60,-480,-313,534,-414,224,132,-1000,17,50,1000,-95,-1000,-257,-1000,468,1000,-1000,250,-1000,1000,1000,90,1000,-156,427,-1000,396,206,507,-170,-355,1000,191,-1000,8,-361,-601,-33,-64,-1000,-1000,-689,-1000,-252,-918,119,-373,606,188,-609,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{311,-3,-417,-433,-978,-953,204,-1000,-79,-916,473,-1000,-113,120,-3,462,-1000,1000,597,987,-751,424,403,-643,531,-515,-306,474,426,-1000,-20,241,339,-333,221,-205,318,-251,-113,737,179,-123,92,-715,-86,-770,198,-428,357,766,1000,1000,-1000,-347,-1000,-115,1000,281,-1000,414,1000,20,75,-774}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{-944,-985,1000,471,-641,4,-1000,203,729,715,-35,-1000,-848,1000,558,377,775,-11,22,1000,431,138,-468,577,648,-158,1000,-88,-1000,-902,1000,653,-1000,1000,941,-1000,395,761,1000,-596,-875,-1000,934,-172,-225,77,769,-860,800,591,-637,1000,-131,610,-541,-266,-265,353,-56,572,-131,-704,-563,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "add(org.joda.time.ReadablePeriod):void",
            new int[]{86,-443,1000,-372,596,-983,1000,-376,-893,-1000,184,-398,-205,-1000,-529,-279,-181,574,615,-1000,167,50,625,109,-834,-314,-1000,-502,1000,-902,-578,-548,219,1000,90,734,-651,-733,-1000,960,1000,787,-671,-355,1000,1000,-1000,629,-655,-479,-63,-120,-1000,-182,-1000,1000,-918,-976,-360,-73,606,572,-609,779}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{468,607,847,797,-995,-666,-297,-714,39,1000,339,1000,-918,-447,-128,-1000,-240,-514,-537,282,251,829,505,-659,-818,-263,-665,-347,580,841,1000,-48,1000,-96,282,538,1000,-1000,-280,280,740,-174,-1000,843,529,-645,354,-119,1000,-132,-1000,1000,-812,-630,-847,-1000,393,-484,107,377,1000,-1000,802,-72}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{565,593,597,539,251,-959,-919,784,477,-832,-1000,-88,-1000,-1000,-1000,-1000,776,948,-774,-551,-1000,806,314,-409,-951,938,-1000,1000,1000,1,1000,1000,51,-1000,559,778,-429,-716,-589,508,1000,378,-1000,819,-909,1000,564,-1000,709,-869,-1000,-543,-47,1000,6,253,-1000,170,-425,1000,813,-130,910,-974}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{-1000,-194,259,-1000,138,-1000,450,-856,106,-1000,1000,-1000,253,-960,271,-1000,1000,-1000,-755,1000,1000,-1000,-1000,-1000,1000,720,-12,1000,-1000,595,598,1000,-793,-922,34,-818,-1000,-664,633,1000,-1000,618,-563,-1000,-515,1000,1000,190,1000,1000,1000,98,733,500,-274,1000,-555,-1000,1000,-1000,1000,-291,-1000,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{1000,-326,-907,708,-325,599,266,-832,-780,-340,-1000,907,263,-673,11,965,-17,1000,309,-639,-250,-321,233,1000,-1000,768,-870,-363,996,-951,-340,-607,-607,1000,82,1000,176,242,-589,213,617,378,1000,-227,822,-976,-1000,-654,709,-477,413,-1000,-717,619,-823,-421,-1000,1000,-790,1000,-327,-219,-83,-175}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{309,-262,358,175,-5,100,-322,656,61,-1000,-567,679,27,243,264,139,-573,729,428,150,851,238,-102,562,-801,740,-242,-665,-281,-1000,287,617,550,-96,133,980,-1000,262,138,-707,620,-299,-440,324,-71,-149,666,-471,-1000,-550,-1000,706,-1000,-316,226,104,1000,707,519,-940,1000,-625,638,-839}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{501,1000,43,983,169,310,-909,104,2,-602,-283,-147,-705,-926,-484,177,9,635,-1000,-1000,411,293,1000,511,-1000,1000,-1000,197,1000,-1000,389,249,-1000,-246,1000,-484,44,-1000,-823,997,-902,376,-176,1000,-400,1000,243,260,667,-256,612,5,-587,-318,187,1000,-862,-39,-234,1000,-448,-465,1000,-42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{133,-187,863,61,-96,-848,-570,-238,797,-267,-285,332,-1000,-867,497,-1000,-50,-54,-449,352,-816,211,593,-445,-439,-446,-1000,605,472,1000,875,1000,826,-760,-42,808,334,1000,-196,-358,1000,-852,-336,592,-1000,337,737,-275,200,-273,-1000,1000,-247,87,273,-285,1000,621,104,65,1000,-99,-495,-694}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addDays(int):void",
            new int[]{875,-129,332,643,-893,-923,-1000,-802,-606,1000,855,-151,-792,983,1000,-1000,355,-1000,154,1000,843,-1000,-12,-832,414,257,327,-92,-856,1000,411,-1000,429,-131,-1000,-566,-290,1000,-176,805,-1000,-1000,-445,-529,8,-829,971,667,280,268,179,228,101,910,790,238,90,-733,614,-1000,1000,-285,471,584}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{-693,-542,645,894,-216,1000,530,-400,-387,-469,-556,1000,-221,-389,1000,442,374,-313,406,472,-1000,1000,119,385,-699,555,309,-33,-448,1000,-1000,309,-1000,-780,765,348,297,310,-556,142,-192,304,614,-736,-26,627,81,-469,455,1000,913,-503,1000,400,-656,22,400,-548,-108,-1000,-1000,-400,504,-205}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{-342,19,1000,1000,-318,1000,-831,248,332,364,837,648,487,-590,-198,26,504,-864,133,-28,-267,493,-1000,913,-301,98,-1000,-272,208,-1000,394,-1000,953,-981,1000,-439,140,-37,-49,135,-1000,-1000,-402,-263,825,-399,-1000,1000,-97,50,-521,115,-432,-708,-263,370,217,938,1000,476,-888,-581,-1000,515}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{1000,325,-649,899,168,348,-1000,379,363,131,863,136,556,931,-254,467,416,-1000,87,162,-701,-448,-361,610,1000,144,-455,-95,419,-460,-670,-595,162,-1000,1000,-477,102,-1000,1000,-520,376,-172,502,-557,-55,-144,-257,-1000,1000,361,-266,-558,30,57,1000,1000,-348,698,923,845,-496,-300,-941,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{-89,-243,209,755,-1000,49,790,-1000,-981,-270,1,762,-1000,1000,873,-149,-1000,378,658,-1000,573,1000,944,489,-1000,-665,980,382,-1000,546,528,1000,-1000,1000,-337,192,-548,14,1000,632,419,-985,-220,-1000,1000,1000,-386,-1000,-991,1000,-81,7,297,1000,-1000,461,1000,-889,-1000,285,621,-1000,515,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{-92,935,684,648,-395,-839,-165,-351,385,1000,450,-710,217,292,976,1000,666,-668,29,-1000,-615,672,-456,695,524,220,388,-62,1000,1000,-314,-173,-88,-617,428,-499,815,-1000,-519,784,-475,100,-263,822,733,-828,-121,370,577,-873,-1000,-314,948,566,-268,1000,-210,1000,-790,-68,483,-1000,-819,432}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{680,-70,-307,1000,-1000,-643,579,-1000,-1000,1000,517,-175,-707,321,1000,1000,-1000,-615,1000,-573,-450,671,681,-284,-1000,-561,1000,1000,-1000,-1000,-708,1000,-986,946,1000,989,-372,-1000,1000,1000,-163,832,-1000,-1000,1000,997,329,-1000,306,1000,-252,-912,1000,1000,-1000,1000,1000,214,-1000,1000,4,-1000,323,-687}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{-781,-1000,-625,687,-561,1000,170,-22,1000,-596,-800,1000,-513,-102,427,-335,1000,-817,-126,-1000,1000,-348,-581,620,-108,-377,766,-402,-105,-1000,-1000,-1000,1000,-1000,1000,-424,598,-760,-258,484,1000,-1000,896,-1000,-638,-1000,55,-615,-400,1000,370,-805,908,-367,1000,620,-9,196,1000,1000,-1000,115,-941,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addHours(int):void",
            new int[]{-475,758,-742,-332,284,-692,132,983,-1000,-752,612,-978,-737,1000,-850,400,-952,-422,-277,-333,1000,643,1000,870,1000,371,752,-501,643,859,86,1000,-695,517,-261,744,151,-680,962,-504,738,-465,945,579,379,624,239,743,1000,-517,-349,1000,433,-1000,168,-370,598,-1000,-570,148,745,337,13,-900}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{-1000,-135,-336,-606,-80,1000,-997,769,1000,-1000,839,1000,-844,-306,644,238,-464,-26,-1000,1000,432,-66,1000,-404,458,1000,701,224,282,893,665,-408,-115,1000,-888,-1000,-617,355,-1000,-1000,-516,-135,-1000,349,393,-1000,436,417,-173,-260,-260,-699,-1000,1000,98,-803,669,131,839,99,-1000,1000,1000,-662}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{1000,-170,-3,740,-648,-400,985,-461,-532,-295,-576,-694,748,1000,-202,-388,-1000,-363,-946,-982,-368,720,-109,-1000,466,-1000,-695,-777,628,-677,-1000,-89,-548,-159,1000,1000,788,159,870,721,335,114,1000,211,-544,1000,-301,-137,430,1000,-28,1000,1000,-1000,-650,-518,-446,-547,-836,-1000,1000,-48,-827,960}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{741,-74,573,83,580,-745,514,-78,-541,-1000,-237,-821,875,658,737,-475,-331,256,-524,-1000,827,347,-400,-1000,-869,195,-1000,-104,-237,-1000,-1000,520,-422,-185,695,219,66,937,1000,-867,129,-623,595,-372,-805,1000,-188,-146,-431,154,-298,1000,1000,-133,-476,439,-369,-137,-48,744,756,588,-198,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{-249,79,413,-304,853,-114,395,-847,-941,-1000,-363,-159,1000,-41,-413,193,489,-973,-562,281,606,224,418,-216,-659,-484,-1000,151,-97,-650,-626,956,-931,-786,1000,453,-1000,-981,1000,-1000,-565,72,489,-22,-8,1000,400,-1000,-186,-55,-1000,1000,513,511,-1000,-96,-1000,-348,213,552,722,400,-124,970}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{-1000,-990,-536,546,-1000,-1000,234,-728,1000,-116,18,-584,-607,895,-1000,92,-886,-1000,-239,-524,854,1000,590,-342,623,-938,-413,400,541,919,237,-187,-1000,489,563,-894,-307,324,120,-246,447,-702,1000,1000,-133,87,-387,-192,913,-1000,104,-981,1000,-1000,-18,520,211,-522,-736,-961,55,-342,-512,316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{51,314,-791,-1000,758,1000,-396,24,-1000,-1000,405,992,90,510,-815,858,-124,-677,-227,-457,-500,-404,709,-380,-1000,446,-1000,373,-1000,-1000,-1000,1000,-149,-552,959,751,-1000,581,1000,-1000,-208,-323,285,-329,-842,1000,480,516,-1000,1000,-1000,205,-1000,596,-746,-1000,192,55,1000,24,-51,147,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{558,-208,142,-311,-476,-36,120,-995,380,29,-147,-66,-122,155,-543,233,-417,-1000,-979,-754,126,150,833,-720,735,-588,-119,-240,-179,924,-264,406,-1000,208,995,33,-813,689,761,355,-895,119,515,277,43,-577,-511,-839,-197,-507,-607,611,622,57,1000,-459,-976,-335,239,671,596,1000,-31,957}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMillis(int):void",
            new int[]{-396,675,287,-794,-335,606,120,-1000,287,-904,-293,320,249,1000,-306,1000,-1000,-1000,-964,-1000,-147,-73,1000,-1000,-573,-282,-785,-1000,-260,2,-792,1000,-1000,1000,992,-514,-1000,1000,1000,-159,-703,298,1000,615,8,426,771,-1000,-1000,246,-1000,-692,358,-485,694,-1000,-1000,-1000,980,1000,641,1000,422,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{572,358,-504,109,-237,533,-497,467,418,400,-31,186,371,-217,-1000,277,-86,302,322,-592,268,1000,-273,1000,1000,438,1000,222,1000,-69,913,1000,1000,-1000,484,519,1000,-1000,776,585,634,-300,279,453,283,66,1000,28,-1000,-585,-353,-1000,-1000,-901,124,984,612,767,1000,-272,1000,1000,-4,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{602,82,-490,-1000,193,692,-1000,-47,1000,-1000,172,428,-1000,245,-882,381,-87,739,815,-1000,894,1000,771,-26,571,559,1000,330,-537,-140,-1000,777,640,-250,739,-1000,1000,-545,1000,511,699,858,-553,815,-55,-1000,287,-757,287,-841,-291,564,92,-305,-488,-1000,1000,248,780,354,908,-209,968,492}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{243,121,584,579,736,700,-681,1000,1000,-270,-90,-726,44,134,-390,430,-441,658,322,252,853,723,83,941,524,128,1000,572,415,-33,-671,758,685,-613,1000,952,208,-703,412,483,941,18,420,-473,1000,-1000,649,-646,-1000,-426,-176,-1000,-461,-660,-240,735,-399,-160,-469,578,1000,-127,531,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{104,-676,444,73,-1000,272,995,-1000,-1000,532,27,-488,516,-566,-234,-237,348,-940,-1000,479,-741,-112,-125,-1000,-1000,-561,385,-175,-922,802,-1000,-1000,-1000,1000,-1000,-1000,-620,309,592,1000,-400,-1000,-1000,31,-595,1000,-1000,8,497,923,719,186,-798,1000,618,-201,390,-579,-823,-665,-1000,-1000,-1000,-400}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00079() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{-628,-150,-168,650,491,78,151,1000,1000,20,-1000,512,110,-92,-774,1000,-772,776,1000,-601,545,1000,572,1000,1000,1000,1000,-202,1000,-1000,-1000,1000,1000,-1000,1000,340,981,-1000,-1000,1000,-39,471,326,340,845,13,-177,-1000,-1000,-108,-481,-1000,-491,-1000,-1000,1000,52,376,1000,-971,1000,-20,963,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00080() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{-483,119,-139,239,944,-28,-786,627,-910,-400,-881,-1000,-469,180,-615,1000,-772,642,1000,-928,866,1000,368,1000,-752,1000,1000,297,1000,-1000,-770,1000,1000,694,1000,1000,1000,-1000,-1000,616,1000,526,-630,521,162,-1000,632,-1000,-1000,-144,-399,-847,104,-1000,-188,307,-502,631,1000,-612,1000,868,-371,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00081() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{863,86,-299,241,-586,74,771,-920,-613,1000,255,379,872,-176,-957,-362,1000,-106,-956,-560,168,117,269,-702,-293,442,863,-196,-537,882,-255,-69,-128,-115,-626,-221,254,-540,638,-695,463,-791,192,618,-137,919,440,-663,44,80,374,-712,-647,-304,1000,790,270,-355,-1000,-970,322,115,-1000,-29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00082() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMinutes(int):void",
            new int[]{-240,1000,59,-367,1000,-576,-697,-763,190,-1000,-479,785,-701,470,903,356,578,-332,-109,-1000,1000,-1000,476,56,-905,129,-1000,261,-774,-667,447,-318,56,-453,-626,723,-478,-1000,-1000,131,-1000,-232,95,20,500,-441,123,1000,751,144,260,577,1000,-1000,-778,-115,-1000,-659,-868,78,-570,345,-126,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00083() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{185,-947,-88,-131,345,770,-654,-1000,665,-784,-277,842,675,102,871,-214,91,-204,268,571,150,-812,-1000,753,-786,-11,591,759,599,45,-816,-1000,-925,-599,181,-666,0,947,651,144,-983,-611,-280,689,329,460,-251,-354,644,517,245,-400,-1000,637,593,609,-249,-443,292,-594,807,-419,409,-291}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00084() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{-81,-219,909,-501,175,610,-512,-1000,609,-1000,-23,988,242,229,310,279,76,629,370,-474,240,-594,-62,911,-430,249,63,441,468,196,-713,-50,-997,-371,599,-716,645,1000,171,211,-87,-924,699,1000,465,-502,-278,92,576,308,9,-181,-437,374,-440,275,-116,-429,-268,-422,-993,-314,1000,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00085() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{-1000,373,-758,-746,-1000,-933,376,277,-102,754,-1000,554,632,792,751,451,514,212,-780,-880,-242,1000,1000,-1000,47,284,269,794,284,-326,-1000,-211,-454,803,1000,-934,1000,2,-361,-1000,-870,1000,1000,-666,-32,-554,-1000,-1000,-1000,978,-857,-302,-21,-972,-214,-1000,-865,152,-552,-1000,-885,186,-533,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00086() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{-336,850,-87,562,-820,1000,169,-222,691,-359,-1000,-1000,-1000,-196,-543,-292,-1000,-276,-1000,575,-376,668,1000,-134,641,397,-121,249,82,-1000,-673,-180,-1000,145,769,1000,-833,1000,7,360,900,-951,-686,1000,-469,-1000,-1000,-1000,43,1000,-1000,762,1000,365,-701,-1000,-1000,363,4,-536,-889,-464,1000,196}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00087() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{-720,-297,-321,-1000,-281,-88,-578,-388,530,-991,-677,-65,-244,-57,-718,-617,395,167,-632,101,425,493,881,60,-962,200,243,947,177,-14,-1000,1000,-1000,518,858,-270,-497,581,-478,-104,-1000,575,1000,-101,36,271,-594,-101,828,-499,-567,794,345,-575,-346,-382,-1000,-168,278,-796,881,-323,471,582}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00088() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{-354,434,-341,-571,400,-523,-491,-410,698,-595,-170,-477,-116,-111,-514,-661,-991,37,-677,324,862,809,-119,-157,688,-8,954,827,-316,-346,-519,238,-1000,545,-177,750,1000,363,-258,319,330,79,557,160,-1000,167,-767,-285,-271,-365,-105,934,729,341,56,149,1000,670,-1000,-255,-1000,-606,235,-288}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00089() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addMonths(int):void",
            new int[]{185,989,-225,-131,-326,795,-134,-824,1000,-246,-713,113,411,-1000,1000,274,-53,-506,9,136,73,-1000,-992,498,-1000,579,591,274,135,45,176,-1000,1000,-1000,-861,418,1000,304,1000,1000,424,-1000,-1000,29,329,460,-787,-967,973,964,47,-421,-332,1000,-117,-24,23,-306,511,-12,-363,854,-10,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00090() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{522,73,768,779,-728,792,-625,274,-107,-340,281,-218,549,120,-935,848,93,-648,-178,451,2,823,-379,-186,-133,-250,-161,-303,-145,-81,36,-484,-856,-682,-922,307,285,853,-480,-980,-312,-942,939,-68,-41,169,-132,61,741,-835,234,-610,819,-524,825,-578,-186,927,-826,-855,548,-806,502,-158}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00091() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{-914,376,681,-624,-326,-347,-1000,-668,364,1000,979,-1000,386,1000,-539,-702,-731,-711,863,-231,431,-208,940,-1000,991,697,953,-445,1000,1000,-673,-123,-1000,422,-148,581,-465,-528,310,-1000,-146,325,-406,442,254,449,-193,160,-423,-288,-669,-1000,135,-584,829,-557,-726,-924,855,-98,-325,-3,-997,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00092() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{-459,572,-42,-624,-916,686,-469,244,56,619,-282,-705,1000,150,-802,1000,-609,-858,-504,408,1000,-738,775,1000,74,-256,-774,278,-603,-711,499,-310,-116,402,-683,805,-164,893,-274,-529,-116,-967,534,-224,-149,297,-126,-349,555,-62,-166,67,494,-209,639,-316,-1000,197,167,84,762,-748,1000,-229}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00093() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{-1000,-852,-317,549,-79,-535,-1000,-641,-219,816,435,-1000,-457,1000,1000,-962,-377,-98,-309,-1000,170,-1000,-123,650,1000,1000,184,-1000,-271,23,-1000,763,-969,-785,-799,1000,-1000,529,755,-404,1,139,-585,-796,-82,-610,-905,-1000,-822,-475,-902,-342,765,-1000,-14,-130,-1000,-172,-461,3,-979,3,-554,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00094() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{-1000,879,259,-722,166,-1000,-1000,-761,474,1000,661,-1000,368,24,-1000,-931,-500,121,-288,-83,-295,-984,1000,494,32,189,1000,20,256,772,875,452,-451,-761,180,422,-457,353,637,9,254,1000,-857,32,716,-111,-333,-118,-855,990,-1000,712,-476,-187,1000,-50,-1000,-904,478,-474,-574,146,-835,321}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00095() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{-1000,412,1000,371,-1000,-614,-101,354,613,1000,870,-1000,328,-503,-767,431,-432,-520,-916,844,459,-606,-316,275,430,-1000,63,735,635,772,875,-800,-1000,-516,526,-852,824,271,-1000,269,572,1000,-1000,15,135,-390,1000,1000,-1000,237,-359,624,-522,-238,733,490,141,-463,240,-953,216,-118,-131,-490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00096() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addSeconds(int):void",
            new int[]{-113,506,-978,335,97,-789,584,492,658,-115,-556,-389,284,632,-246,997,-1000,-873,-1000,414,1000,55,-642,245,-229,-79,592,102,-1000,-1000,341,-401,-485,1000,39,-13,467,1000,-636,-246,-170,541,-1000,-543,-18,384,-318,-554,593,-271,-719,-287,522,282,-542,182,273,387,689,-279,420,605,636,629}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00097() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{-459,-821,1000,975,71,53,-460,766,-654,1000,769,54,1000,1000,26,174,-203,-548,1000,541,-693,-1000,-610,659,-1000,-851,284,408,736,-73,925,443,-392,708,495,-1000,1000,-83,-82,-443,466,-1000,-140,-138,-1000,-61,-113,-35,-875,685,-239,1000,-365,-706,-280,-468,-307,1000,461,1000,390,-650,-613,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00098() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{480,148,390,-281,-707,-72,533,-340,1000,161,899,-578,-1000,355,-798,289,0,-774,-1000,-901,116,-179,-826,-528,-917,-384,163,-493,195,617,82,199,-419,258,-254,319,-223,253,591,776,872,459,210,-1000,-7,-961,1000,1000,-740,-784,1000,988,196,-140,-444,110,-817,-547,386,325,-846,-470,1000,643}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00099() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{609,955,-73,-406,-263,576,748,-105,643,-770,326,642,-617,-457,-169,478,645,-339,-625,-967,-128,-196,-553,-734,-397,-796,107,-208,121,542,190,110,-773,-332,545,650,-756,138,625,550,-798,885,652,-842,543,-786,-22,407,-308,229,314,391,339,-216,15,-222,390,-886,782,-19,-794,-618,789,-566}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00100() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{153,-999,79,1000,5,-333,615,674,-492,162,775,424,965,-142,-576,-29,87,-1000,67,-560,-1000,-1000,-787,-275,-676,-804,394,924,1000,6,1000,558,-1000,113,1000,662,718,186,493,597,375,-920,243,1000,-21,-241,-27,146,-447,662,-176,1000,-151,-56,-101,-264,-51,543,418,920,716,-253,789,902}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00101() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{-48,-402,529,-241,-314,-449,-534,-869,136,734,963,-263,-950,586,-88,-158,-550,795,-54,718,170,905,-748,420,-353,9,-444,262,90,-73,82,-306,881,934,-952,-710,342,-146,-470,-487,-164,112,453,-930,-398,227,876,741,-333,-876,754,845,-261,-356,-89,709,-947,-312,-897,869,829,-481,731,-859}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00102() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{-165,-225,789,75,586,-206,893,870,411,1000,988,523,527,695,-1000,-182,212,-1000,-115,460,-709,-637,167,-69,57,-217,555,-621,117,111,651,-377,38,1000,-191,-1000,710,-567,65,387,24,-901,-1000,-169,-1000,463,-71,716,-1000,-595,-148,444,164,-257,-466,275,158,1000,601,-293,-336,-645,-14,216}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00103() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{988,-250,-990,-86,-256,-1000,719,-1000,952,-650,947,129,-1000,1000,-497,-799,-820,-1000,821,-810,192,198,-1000,-1000,246,307,642,648,363,233,1000,-26,433,38,-707,366,-250,110,541,796,-738,606,855,675,1000,-101,1000,833,502,-1000,952,839,-192,553,378,1000,-707,-861,-1000,1000,52,-39,1000,87}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00104() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addWeeks(int):void",
            new int[]{-1000,955,-266,763,-584,-545,1000,-107,-643,-770,1000,741,11,385,-1000,467,-482,-169,-625,-967,-370,-618,-553,-1000,1000,-1000,41,-40,121,-130,1000,-965,-601,1000,14,-794,-812,-906,575,613,-919,-523,-256,-965,-733,1000,624,1000,-1000,-491,-460,581,339,267,-467,932,-324,-886,527,1000,-580,-98,-894,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00105() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{-689,-104,951,-16,13,906,-147,285,-404,350,205,523,-129,606,-1000,394,-41,692,1000,-1000,302,-373,-157,445,-224,839,352,-400,-1000,1000,285,583,318,1000,-817,-1000,886,1000,-805,502,-1000,-184,577,188,114,743,86,-1000,1000,416,683,715,585,648,-437,538,468,-1000,567,-881,-735,605,471,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00106() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{77,-2,538,-239,-385,-299,-377,-616,948,-605,-427,-698,356,-26,-794,34,-596,860,-869,1000,-103,-936,-216,1000,1000,1000,240,-515,716,314,786,875,726,1000,-532,-934,726,85,-1000,-872,1000,242,511,-681,886,522,-400,-1000,224,589,32,669,820,-1000,-552,-715,-458,-1000,26,-629,-299,-85,1000,258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00107() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{66,-655,804,478,1000,1000,289,741,-404,579,48,1000,-611,869,-411,157,1000,692,1000,-117,-850,-118,-157,-1000,-433,96,969,-111,-1000,835,498,1000,437,-1000,690,719,1000,1000,278,552,-243,-647,577,196,-1000,-208,547,-577,631,-767,-78,-892,657,-554,-894,1000,591,372,354,-878,-868,618,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00108() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{853,142,-7,-1000,-262,1000,-1000,673,1000,-971,-606,-535,-189,447,79,-214,-511,152,-1000,453,274,-1000,513,1000,1000,866,193,-672,-20,835,401,716,1000,214,-560,18,887,49,78,-1000,-1000,-278,1000,252,1000,-634,670,-577,-445,-407,450,-128,679,115,-468,-1000,-1000,372,-116,376,-712,-1000,-553,-133}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00109() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{82,-745,438,-301,1000,337,-1000,-959,340,1000,400,1000,620,845,-587,-316,884,1000,370,-308,-1000,-923,78,-1000,375,510,1000,-529,-1000,-96,-815,988,-392,-1000,1000,153,772,1000,692,-235,-339,-824,150,-415,-1000,-270,-1000,-704,172,-1000,-1000,-1000,1000,-191,-975,697,1000,1000,179,-749,-856,1000,1000,671}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00110() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{-734,-414,-475,-1000,-285,-836,-651,274,1000,-162,165,635,-611,962,-411,1000,65,1000,-1000,-671,1000,-1000,-181,268,1000,-241,326,255,544,1000,498,-84,26,-579,-644,-649,227,-385,1000,-671,-529,-647,502,-496,-120,560,547,-984,-218,421,-280,439,960,952,-169,-565,-312,-819,615,47,620,-513,1000,-258}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00111() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{654,521,598,899,1000,-456,781,-601,-606,982,-570,1000,-718,102,1000,-222,218,65,-36,1000,-311,-279,-1000,-434,43,661,1000,1000,-400,-580,1000,745,956,-466,606,1000,854,1000,670,-844,983,-730,-683,-293,-788,-1000,-903,-295,-519,-722,429,102,-224,-263,122,-79,-1000,561,252,-700,-641,787,1000,778}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00112() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "addYears(int):void",
            new int[]{669,2,-532,-878,698,-944,-1000,-889,471,-446,-803,-232,617,317,943,-696,537,506,-262,1000,-1000,-496,-49,-52,840,-413,455,-637,-1000,20,-813,1000,356,-914,923,1000,-334,49,960,-1000,892,53,832,549,-1000,-1000,-776,-1000,-512,-1000,-1000,-1000,994,-1000,-763,-134,-756,1000,-251,66,-390,-390,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00113() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{401,285,27,-517,-202,-918,1000,574,845,1000,545,-273,-633,880,-723,1000,-1000,-1000,872,-1000,1000,-406,-1000,-61,1000,-1000,47,779,588,967,834,977,-1000,392,1000,1000,112,109,98,-924,-1000,1000,-475,315,446,455,107,516,402,246,1000,-1000,1000,1000,-134,-1000,563,554,-1000,122,-403,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00114() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{127,-506,-33,-193,675,-287,715,375,-1000,331,-917,-153,-1000,813,923,-769,-795,701,490,-755,683,-450,-474,-698,-111,-241,1000,1000,-666,924,454,245,-63,848,-339,-486,256,948,-178,-673,-617,594,600,516,1000,573,531,571,1000,277,361,-438,-99,1000,-871,-575,-1000,703,-652,-986,-579,-1000,1000,347}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00115() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{-401,164,863,352,293,-331,-903,729,234,355,-148,236,1000,-718,-703,643,-450,-1000,-1000,1000,675,640,-1000,-1000,-726,-774,511,-921,-649,-997,397,50,-359,287,805,-1000,-793,-125,942,-1000,-118,521,785,674,629,45,291,899,-1000,-114,826,-511,159,263,80,909,-43,-844,147,14,876,382,-850,-160}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00116() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{-599,-194,492,-480,-403,-612,987,-457,-578,1000,212,157,-540,1000,872,1000,-1000,-73,895,-156,16,-841,-1000,1000,712,-1000,742,961,887,1000,-18,1000,-1000,1000,833,-547,-470,1000,735,-1000,-1000,539,56,-593,1000,968,688,1000,973,735,-79,230,74,1000,124,-1000,-657,821,-342,-361,601,-648,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00117() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{734,-469,-669,272,19,408,93,512,-151,-78,-643,-174,-1000,1000,-613,979,-1000,581,-131,-407,1000,-680,-2,-722,1000,-289,906,335,-27,-101,1000,819,213,1000,-292,587,453,1000,776,331,-1000,602,1000,-542,315,144,822,978,289,-170,-136,-1000,462,87,-1000,-1000,-347,1000,275,23,38,43,-451,274}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00118() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{-348,-612,229,557,373,87,-101,-1000,34,-254,-285,-54,1000,269,1000,-838,251,533,289,-197,406,-912,0,1000,34,486,-470,-130,-131,259,359,636,416,-403,-1000,-687,-1000,555,911,225,57,-218,733,-87,4,-1000,868,-561,439,272,-1000,-445,-922,-665,-5,531,1000,357,60,195,235,1000,-1000,-450}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00119() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{297,380,1000,1000,-271,510,-1000,-718,-1000,-812,85,0,1000,-1000,-268,-1000,863,-1000,-1000,1000,-978,253,1000,1000,-1000,1000,969,-1000,-1000,-1000,-1000,-366,1000,-1000,589,-346,65,261,-905,-796,1000,820,-616,-24,937,-1000,-383,244,-1000,-1000,-135,1000,-1000,-1000,618,1000,-783,981,1000,-1000,-390,-1000,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00120() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "clear():void",
            new int[]{-61,587,-356,1000,653,-626,809,1000,920,1000,529,-615,-955,367,-1000,1000,-1000,-1000,562,-1000,1000,-695,1000,-172,222,-1000,445,807,257,967,451,1000,-1000,801,1000,494,705,109,-321,-924,-1000,1000,-238,1000,446,178,162,516,687,-260,1000,-1000,-492,1000,-488,1000,563,255,-756,55,-795,-1000,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00121() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getDays():int",
            new int[]{-844,-513,-246,-236,448,566,-522,-745,171,63,530,-1000,-243,-85,-1000,83,536,462,124,682,822,374,700,-534,-384,1000,1000,415,-644,-643,333,1000,-585,-1000,-767,-1000,544,210,454,-309,269,-35,-505,337,-993,766,-270,-54,-225,-201,886,-209,1000,1000,-994,1000,-1000,870,622,-728,-1000,618,-185,585}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00122() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getDays():int",
            new int[]{434,-23,137,905,-21,635,-786,-706,-905,115,274,15,-660,-26,559,-815,703,986,-941,-1000,971,342,970,-1000,-1000,-259,-314,-738,1000,-384,-442,743,-1000,499,-312,-1000,-903,872,60,-703,1000,-583,-214,570,-820,707,667,903,-687,279,-431,-768,1000,687,-1000,-1000,-381,-636,909,-774,-360,786,1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00123() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getDays():int",
            new int[]{-86,-172,-252,794,430,282,-32,243,933,855,-727,1000,1000,1000,517,1000,-382,-62,469,490,-782,119,-1000,-1000,1000,-314,-799,-521,931,-363,1000,-801,1000,1000,178,1000,-1000,-56,1000,55,893,-1000,63,-846,572,244,78,1000,145,-293,849,-1000,-543,-255,-395,186,855,101,-850,-740,-492,-186,-1000,167}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00124() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getDays():int",
            new int[]{438,-54,494,73,952,172,-409,-371,-1000,508,759,450,564,1000,-61,-253,1000,464,-75,-1000,587,-94,-821,-1000,-1000,576,993,-1000,9,-994,-110,-670,-1000,175,279,-969,-891,1000,47,200,777,-747,226,-377,-984,1000,1000,32,55,1000,-735,-1000,747,-55,-1000,-791,-1000,-629,925,-432,-91,-357,1000,874}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00126() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getDays():int",
            new int[]{-1000,-696,-452,-374,1000,-999,510,-792,-878,679,376,-1000,635,1000,-580,-518,960,-773,-160,-68,-530,-689,1000,-759,208,188,982,-399,-1000,-75,-375,-754,-864,890,151,-757,205,519,-1000,-225,-1000,-63,585,-648,-1000,1000,438,-380,-392,875,-1000,-667,526,-568,1000,300,-744,-198,804,-755,-869,-821,-1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00127() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getDays():int",
            new int[]{-1000,-418,-63,-744,838,1000,-666,201,-562,-438,592,-533,-1000,333,-446,-704,487,-1000,805,990,-13,-880,1000,-806,1000,-63,-23,-70,-371,1000,251,292,-1000,538,-1000,-1000,894,-1000,679,482,-1000,700,15,-588,-1000,-396,-1000,-125,400,-333,-544,-300,-837,-307,1000,266,-657,-1000,969,-349,-691,793,-1000,-896}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00128() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{-711,929,405,-166,418,1000,116,-782,-32,1000,1000,977,-636,-992,-784,1000,742,-646,-135,-1000,1000,551,-920,-1000,-485,242,752,-1000,448,1000,-120,-240,-515,741,-826,-314,340,437,188,552,1000,106,-218,-1000,676,642,-314,994,-69,-195,-1000,-1000,712,-617,-755,233,611,-346,-305,-1000,-922,441,561,97}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00129() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{136,922,471,-236,-749,-466,1000,513,-1000,1000,318,1000,-1000,1000,838,-294,343,-269,-1000,-295,127,-176,-73,-625,-1000,-15,1000,-1000,-203,-1000,1000,30,-140,1000,17,-997,500,1000,-59,-1000,-67,-1000,1000,1000,144,-1000,1000,629,-48,-1000,135,-188,-227,1000,21,1000,1000,345,541,-577,661,180,-1000,359}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00130() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{126,353,864,947,-701,-509,103,-665,-1000,-622,-41,69,82,-899,300,-1000,-282,-51,94,-643,573,-698,-1000,710,-917,-353,403,127,-635,-688,489,853,-1000,654,-1000,-415,-403,521,-833,-1000,609,-478,-50,1000,916,174,1000,1000,-661,-663,941,350,1000,-849,30,-208,460,-505,155,-422,12,733,-843,867}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00131() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{1000,-957,513,-570,893,-64,1000,593,-1000,-600,-1000,720,1000,1000,785,-677,-425,-409,-35,1000,-1000,1000,-921,-1000,-1000,-1000,-463,858,251,-1000,1000,-834,543,229,892,-1000,926,1000,-253,-171,1000,-1000,1000,329,788,-1000,1000,867,1000,-50,-268,1000,950,1000,-772,-95,167,755,481,1000,1000,348,499,575}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00132() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{-1000,620,384,-277,818,-57,-266,-273,1000,-163,749,-438,108,-741,-864,-504,-88,-991,1000,-1000,1000,532,-1000,314,58,-959,195,214,775,1000,1000,180,-173,-55,-416,1000,100,-693,895,1000,222,990,-1000,-666,575,1000,-1000,981,-821,709,-44,-353,1000,476,-1000,233,163,-1000,52,-1000,-1000,581,332,396}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00133() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{-315,1000,953,134,-433,-401,679,-1000,-904,1000,913,937,-1000,638,376,103,264,60,-990,-855,750,-22,-1000,-540,-943,-524,1000,-1000,132,-507,-619,167,-148,934,-745,-237,319,657,959,-993,139,-1000,531,1000,633,170,960,1000,-107,-1000,-183,-237,889,-1000,-402,1000,847,-174,-314,-1000,-611,612,-420,616}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00134() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{-1000,317,486,-443,571,-339,-125,-613,969,635,337,194,-677,-718,-362,314,460,61,265,-1000,926,372,-645,4,398,-546,672,-834,1000,1000,-971,-691,-1000,210,1000,891,583,-627,-121,393,-307,775,-821,-491,-250,1000,-727,207,-384,133,-88,-512,412,-439,-1000,1000,485,-146,458,-1000,-351,439,58,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00135() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getHours():int",
            new int[]{1000,-209,-156,-848,957,-242,1000,220,-1000,-355,-1000,698,1000,916,965,-303,-169,-203,314,907,-1000,1000,-883,-180,-957,-669,57,300,251,-874,1000,-676,-172,-110,416,-320,985,973,-15,277,809,-1000,969,292,1000,-1000,1000,692,724,-66,123,798,413,967,-772,66,633,550,153,1000,344,-132,431,655}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00137() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTI=", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{-500,-2,178,-725,-682,73,-365,-410,564,182,58,-475,105,-610,496,538,-310,-728,512,-247,-91,400,962,252,69,-128,-433,-134,-1000,-1000,401,125,275,-868,129,752,112,780,-237,-1000,-894,6,1000,796,843,-659,-599,389,-292,-1000,-374,423,-975,-33,158,-1000,432,1000,820,-1000,-545,401,-392,-57}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00138() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{-805,33,-885,502,-1000,-127,-894,-62,304,893,635,-1000,165,-332,1000,-59,122,-1000,-74,-186,502,-295,795,135,47,662,536,-245,-850,434,556,986,603,31,784,1000,-563,812,1000,-204,-976,-526,1000,538,319,393,-659,-541,474,-897,978,319,-764,930,938,-1000,-799,403,1000,-238,1000,801,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00139() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{-309,933,-931,-968,-729,184,-89,-562,-409,988,-162,-911,337,-383,595,-665,-526,-887,92,-558,-497,-284,3,252,-486,86,570,544,147,774,778,176,-176,426,-90,895,-466,281,907,-428,-126,-339,899,-485,68,361,-134,-454,207,-880,401,-482,-848,668,294,-881,-841,-745,796,-70,577,11,-729,986}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00140() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{1000,-508,648,560,1000,346,97,-23,126,-380,174,755,93,-27,-613,1000,217,1000,-750,604,-347,168,-1000,-855,586,7,-743,-1000,-268,80,459,-718,672,400,-425,-95,483,-734,-1000,1000,-1000,230,542,775,-431,898,-301,-717,27,707,-33,1000,1000,787,1000,-271,508,718,-11,1000,14,1000,1000,-657}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00141() {
        org.junit.Assert.assertEquals("java.lang.Integer:MQ==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{-533,1000,58,432,-381,-227,23,-479,316,1000,-421,-176,-427,744,701,107,473,-220,92,-950,-283,886,-51,435,-486,155,155,134,-75,871,778,-206,123,-119,-678,541,440,-797,543,-427,-126,781,899,-484,-133,427,-134,-1000,-271,-525,954,-337,-389,486,1000,-848,-576,-588,923,476,1000,577,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00142() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{-821,494,948,1000,-291,-844,-840,-349,898,417,-977,320,-328,1000,524,290,724,-444,1000,-807,-56,671,923,723,1000,-703,-934,-25,-909,-1000,1000,-251,-64,-824,159,494,760,-1000,-892,-998,-1000,629,1000,537,369,-414,-1000,-1000,-299,-493,1000,340,-345,-37,334,-954,133,216,1000,-547,213,1000,-546,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00143() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ1NQ==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{214,-323,867,632,577,544,-122,-23,-1000,-1000,-465,579,745,-168,-1000,372,-156,-577,1000,671,-815,-1000,-1000,-448,1000,723,-743,-1000,-887,-1000,1000,-823,-1000,400,424,205,1000,-1000,-671,729,-155,-1000,158,-1000,-431,-661,-1000,-717,1000,868,-409,1000,-1000,1000,-1000,1000,-1000,718,152,1000,-158,-671,-367,-356}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00144() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMillis():int",
            new int[]{680,107,-658,1000,-366,737,-840,-259,106,787,552,320,126,-1000,903,-542,-926,-444,-39,216,-1000,-200,-818,-616,-957,431,1000,91,306,1000,49,-164,482,-58,99,625,-508,-1000,1000,-998,-127,-61,4,-94,369,-414,640,642,-299,-950,-396,-365,-676,1000,1000,-754,-1000,-988,-590,265,-328,-43,217,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00145() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTMx", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{-210,-688,-3,269,309,-985,-423,566,816,1000,-848,-1000,-1000,-301,-720,1000,-1000,403,254,-4,-672,-199,-748,1000,907,-955,137,-677,-1000,840,-69,285,-519,-1000,-754,-696,831,-534,-785,-1000,101,-353,328,534,-277,-290,809,136,-1000,384,-244,629,-963,-1000,-440,-144,-208,-1000,-370,601,-861,1000,-21,190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00146() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{306,-23,1000,-146,154,1000,1000,24,-1000,-1000,873,698,264,483,997,-334,593,-469,645,1000,-885,-427,-121,-583,20,58,-211,130,1000,-534,-1000,687,-129,663,-151,-1000,-1000,-989,545,249,133,515,-953,376,204,-72,-425,-1000,1000,782,380,-664,559,1000,1000,1000,519,1000,1000,-1000,-272,-862,428,-83}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00147() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{-1000,-159,-801,-304,20,108,-271,1000,292,-394,-463,-319,1000,912,1000,1000,721,-305,1000,-224,669,-631,-394,-256,-926,1000,-822,1000,147,-347,400,1000,919,-278,1000,-133,-1000,8,-440,1000,-813,-255,-465,895,-208,-641,-1000,-1000,158,1000,1000,381,291,747,-540,1000,1000,307,977,-837,1000,-605,100,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00148() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{626,-466,637,-154,316,1000,83,-819,62,-1000,-76,115,465,651,-400,145,513,-292,518,-458,-585,-383,536,400,-1000,192,-271,-400,766,397,91,704,-43,22,69,-840,-815,-928,529,928,41,90,-293,-70,-529,439,-339,-1000,788,1000,-390,-551,-152,-161,677,1000,429,85,661,-569,1000,-315,618,-602}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00149() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{-93,158,-396,-79,51,765,-577,197,-359,-209,259,1000,108,796,287,651,-64,-565,478,761,-1000,139,456,-574,-1000,56,-111,18,1000,788,-53,258,408,301,-20,-220,87,186,804,842,-483,1000,-406,670,-62,-134,-335,85,998,278,-204,319,-475,586,292,-400,110,1000,842,-551,-48,979,502,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00150() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTQ0", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{302,1000,374,-988,999,1000,-187,-851,-395,-1000,-469,876,1000,308,-110,1000,983,1000,-206,1000,-1000,642,1000,-479,-1000,400,-276,245,697,-140,-904,-348,817,-753,-438,360,-324,-701,-1000,897,-284,580,-1000,-99,-583,995,-1000,-101,1000,620,-741,416,-65,1000,266,1000,-331,953,368,833,-713,932,854,-38}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00151() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{-543,822,94,296,-340,837,-577,1000,517,277,-1000,-264,236,975,-1000,1000,1000,1000,312,1000,-161,-63,-729,1000,400,1000,-1000,-1000,498,-81,-848,265,-902,-1000,-420,464,-736,19,-1000,897,-76,-300,-1000,816,-424,-81,-492,-901,697,685,1000,652,-1000,-63,-1000,379,431,-307,367,-349,-1000,1000,-66,-67}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00152() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{108,-376,-1000,-286,384,-1000,-1000,284,487,-108,-576,-1000,854,372,-209,1000,-1000,1000,-93,-543,164,-299,-371,954,61,403,-822,-124,147,-261,400,221,-1000,-278,144,747,-527,-726,-90,117,3,-590,688,785,-208,688,368,-1000,-111,486,-319,51,-1000,-1000,-445,-400,-105,-578,199,1000,-749,-19,234,-505}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00153() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTEwMDA=", DEReplay.run(
            "org.joda.time.MutablePeriod", "org.joda.time.MutablePeriod", "getMinutes():int",
            new int[]{122,-1000,374,-776,818,-389,-1000,-517,99,400,949,-136,-37,-1000,422,1000,-1000,-786,478,-1000,-650,-728,31,-20,-4,723,88,405,-400,288,140,883,523,217,195,178,282,-542,868,-912,-225,536,341,189,-583,503,266,-205,-114,356,207,33,187,-288,639,199,-331,-356,41,66,548,-68,854,-399}));
    }
}
