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
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{-321,306,-178,-206,-515,-787,77,399,921,380,-863,-342,337,-784,-213,362,-419,-153,-513,872,31,211,71,-714,-400,381,552,-192,-631,-423,432,627,122,-251,-875,748,376,39,-102,486,259,974,75,609,-5,-204,-856,501,-303,997,905,107,643,830,803,-55,-683,-680,-796,-7,77,147,-477,-118}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("java.lang.Long:LTIxNDc0ODM2NDY=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{340,111,-682,154,704,165,508,214,602,35,514,-676,-974,787,278,124,-755,748,-18,966,357,301,172,-803,678,-888,79,725,364,-728,-147,-283,-640,-735,577,-605,-978,-883,-250,-822,-442,576,269,689,833,-195,-158,-489,-994,-354,-783,-983,-31,285,-298,-937,645,860,966,-107,786,418,-657,218}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Long:LTQyNg==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{538,146,972,509,-362,-13,150,549,-221,-7,425,-157,571,-450,-726,467,-922,-245,-685,763,-64,-743,19,-436,-698,144,-757,685,740,-455,948,668,-189,54,673,-20,399,-496,155,65,-381,149,777,-26,-466,894,491,643,821,-137,298,324,79,264,399,-846,480,340,759,396,-871,-46,-60,498}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean,long):long",
            new int[]{394,-835,-238,343,910,-860,-549,-800,-533,522,210,-554,378,765,-858,-656,-123,-356,545,378,-724,95,181,157,587,969,-747,-296,-221,582,265,11,-304,885,680,898,-210,-64,-616,-872,-695,875,-717,510,315,-891,-918,292,-900,-439,266,-473,769,930,-380,292,706,-243,-757,-346,-496,-279,937,-904}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-323,907,-358,560,-459,-516,-420,-753,158,-774,748,581,-457,-692,-696,-750,98,898,734,-804,851,218,740,-48,976,-281,-448,730,-763,428,-202,-281,-841,646,861,101,688,674,251,-320,-161,-12,924,-330,410,395,734,221,237,89,220,-464,649,-758,-200,67,-215,-207,-898,-792,294,441,-354,-860}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:Mg==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-377,-927,471,577,-835,-120,-581,-415,-663,3,671,-736,26,520,-695,890,118,-886,-543,803,796,683,-142,-333,461,206,-29,928,576,552,-507,-427,857,40,764,309,-825,-964,-762,-320,322,-348,-410,-680,327,-309,242,-494,-574,-611,-840,-481,967,-788,605,-86,-445,994,197,-259,-459,-956,127,-991}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:NDk=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{17,891,554,3,677,201,697,-956,-584,-460,-578,779,727,628,956,670,765,-605,500,310,356,990,497,-379,-432,-526,-396,730,225,-280,-274,955,889,173,253,-624,775,615,-760,-155,822,818,705,977,-268,98,-582,632,146,142,-367,739,-648,232,22,-91,-94,980,-205,223,389,48,398,816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "equals(java.lang.Object):boolean",
            new int[]{798,-587,-894,331,-431,-292,746,-475,-195,991,-27,286,183,-137,130,607,718,-955,174,236,-536,-1,-855,-938,612,-433,488,-432,255,855,-450,907,889,540,-114,920,-5,798,877,206,-53,134,506,-815,-85,-473,-809,-915,-554,534,-969,686,-29,222,-24,784,592,177,41,-611,226,-821,959,878}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{227,-1000,-326,-70,-1000,-1000,635,-481,-110,907,1000,967,-1000,-1000,879,359,1000,727,1000,260,-1000,560,1000,399,1000,-1000,-75,91,-33,139,814,400,-285,654,-1000,1000,-308,-1000,433,-961,373,1000,-332,-92,-551,435,-653,-1000,1000,-438,-1000,-1000,696,1000,-892,-43,999,-1000,-48,-254,536,318,559,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-552,-199,-246,686,872,-466,110,-54,-949,18,26,-92,-358,579,401,688,-840,-887,236,-202,308,363,-316,193,383,252,-268,184,657,-788,708,-923,261,460,784,-957,-1000,6,-988,-791,-190,-200,-919,259,-909,-136,-73,927,47,-444,-369,-311,311,122,-410,-321,678,638,890,407,518,-903,-9,204}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{626,-326,-288,590,-348,288,-763,-91,-582,501,519,-912,-930,371,576,884,662,-875,544,-332,795,531,-456,847,-21,-841,-658,-224,-852,529,540,-943,429,-837,57,-193,169,-619,995,-48,467,324,-388,886,-970,-899,150,953,29,268,447,102,822,-127,-45,831,888,212,594,30,151,-871,143,730}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{282,110,222,707,-488,-80,-785,157,727,11,12,-474,-289,615,-933,364,-712,-177,-358,378,893,817,-926,583,-784,-671,-543,696,-197,186,-960,-944,473,-623,-871,742,-105,-156,-704,69,743,914,315,-814,478,-841,-627,-892,-33,336,-138,-807,946,31,307,-201,-390,647,-143,674,-493,437,-851,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{609,-758,-319,-165,914,-856,500,810,598,855,-872,-630,-16,125,95,-894,-902,472,-686,657,-949,303,-709,251,789,-270,492,236,724,-770,-735,702,701,979,-793,-259,-782,365,-148,-653,-71,-39,213,531,60,-909,815,824,-441,972,611,28,-448,414,439,-212,20,43,534,-607,-573,117,7,28}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{958,-277,-152,156,841,238,305,-227,689,-367,553,-403,551,20,-401,-840,909,226,-134,-538,-257,838,-875,913,-933,-808,-750,592,585,-408,314,-960,2,704,-973,-750,-914,753,-452,-8,-401,-54,297,170,-137,351,552,510,910,372,-668,-445,918,-103,366,683,-53,-766,763,-341,-130,10,-549,93}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{544,672,436,-551,-354,212,684,281,-579,710,314,540,587,363,881,102,598,-887,911,978,-431,-658,144,798,355,-901,-584,-880,879,255,-192,-58,-915,258,640,-52,952,747,559,-451,-107,-825,-426,558,-428,817,187,465,-350,60,981,733,-663,-295,-27,-771,722,-836,-145,-979,835,908,817,-909}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{-765,335,-1000,216,-320,-1000,174,168,270,840,-1000,1000,1000,-1000,-492,-1000,77,-277,-922,-35,-1000,-108,72,-19,-1000,-1000,812,-54,336,-168,947,-858,-9,-164,815,-1000,1000,-210,-1000,793,-270,175,173,1000,-1000,591,1000,766,1000,-251,-1000,-1000,-774,-826,1000,47,512,1000,-772,-1000,-1000,246,-1000,29}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{-127,-761,889,755,-583,1000,209,569,-714,-893,944,-243,476,361,623,913,486,859,539,-53,761,-520,-170,604,-184,-103,750,-919,710,565,358,156,-209,418,-287,467,-260,602,441,26,-229,-550,45,-113,-406,-858,-303,4,-775,12,-578,737,64,282,-442,209,-212,-809,149,-206,568,-96,-424,-651}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{109,-727,-886,-773,-455,851,701,-665,-794,-935,-632,732,905,-743,-398,-424,-63,632,119,560,548,-243,443,195,-545,936,241,-398,-830,-223,-810,581,171,-491,96,-89,6,-764,-303,-665,308,-833,131,482,-594,110,-602,-897,724,-956,-682,-568,-537,-623,452,-790,230,-279,-727,1000,191,403,-568,-254}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{811,-300,871,-476,-122,-127,-22,119,-906,1000,1000,303,650,1000,529,-98,415,-342,1000,1000,-708,-724,381,167,518,-574,-248,-894,924,-439,-562,261,-422,792,1000,1000,-242,-11,534,-174,402,-642,-1000,342,-300,1000,-1000,467,-389,-917,-118,1000,-702,-47,-433,-1000,1000,-498,752,312,1000,882,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{619,70,693,-678,661,353,852,-703,444,547,-864,913,344,-320,701,-238,391,-349,-319,496,229,181,191,-829,446,-358,512,-662,973,993,-224,-872,51,765,666,-104,397,-486,192,-814,-383,519,-567,-276,-314,-391,279,-17,-670,968,685,417,-449,-481,-297,912,201,61,-322,-779,506,718,-649,-816}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{-1000,803,-507,-296,-26,-1000,-1000,-32,557,-92,-491,804,988,-1000,988,-668,-107,161,-523,718,973,-242,508,474,25,-712,106,-1000,847,-548,1000,-1000,-135,63,56,245,1000,-1000,-364,-664,-862,-472,-1000,117,-479,100,-85,-840,-522,132,-358,752,-238,649,-417,812,-1000,112,556,-553,-168,-309,-350,-738}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:java.util.TreeSet", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getAvailableIDs():java.util.Set",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("java.lang.String:LTYzMmUz", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getID():java.lang.String",
            new int[]{-987,964,-202,-632,-231,3,455,397,-503,743,542,-430,-642,400,-609,439,921,-950,-670,645,-560,-629,186,653,-363,535,552,-457,843,189,-490,594,-452,-730,-975,804,-255,783,-184,358,800,-388,120,556,999,-70,-494,-287,415,-161,646,176,-707,-489,773,-270,-252,-740,-527,-590,-743,-229,384,988}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{-498,297,-468,151,175,709,-110,-591,-700,-395,-910,-64,-195,-455,-4,90,6,598,750,258,859,-738,889,594,460,831,-205,974,-306,108,-853,446,211,-494,78,567,-766,186,-142,-276,-907,-845,85,-217,843,980,631,220,833,-845,856,259,675,-944,-20,511,710,-122,-72,66,763,-998,457,-901}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjA4OA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-122,433,817,-27,-325,-185,538,380,881,-506,-219,806,-72,-724,397,610,754,504,928,-793,-587,-197,-891,-710,-875,573,-942,484,736,572,843,314,661,-709,793,-488,-898,207,131,-186,-432,448,-687,692,531,-69,-237,719,-544,-387,-359,-352,351,131,-276,699,507,-181,-459,-941,153,855,455,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{-347,1000,395,-226,-1000,469,-1000,-1000,-1000,-990,-1000,443,-371,869,1000,-1000,1000,52,-1000,1000,375,351,495,-920,-1000,-1000,264,682,-279,-1000,-1000,403,-633,-339,882,192,-1000,-403,-690,-63,901,306,731,-825,-786,1000,-1000,1000,1000,981,873,454,-805,489,-1000,-753,-1000,-90,1000,431,-943,-1000,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{362,890,1,851,-168,314,841,-757,-746,948,-268,551,689,-942,-212,418,-442,244,521,619,-780,-751,530,599,135,-700,82,409,-21,962,-793,112,-786,886,571,-198,290,-823,624,585,-89,572,-715,78,637,-835,-212,326,414,947,225,-668,638,-889,762,-108,789,-231,-413,847,789,471,184,473}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:NnZfK3NvalFBRTBPNi9UR3pNSV8=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{752,164,671,-474,738,-780,1000,-892,-1000,-719,809,308,-833,-90,-674,-315,-568,-660,1000,-288,-797,-668,532,687,-382,-1000,240,204,714,-681,-1000,-592,219,1000,764,-833,355,116,209,1000,748,-809,1000,-846,-637,753,227,-406,-137,-731,702,239,1000,-471,-554,201,-865,-1000,-1000,440,1000,352,785,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjA4NA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-647,-878,-655,-1000,-275,1000,264,-1000,1000,847,-758,878,-173,-734,90,-330,1000,-495,-38,-119,80,-98,-56,-2,-1000,275,-584,-493,-1000,50,-166,-709,440,260,284,-520,-994,437,610,495,410,-368,53,-996,-535,-408,255,-71,1000,-270,922,-164,-826,117,-186,632,-84,722,-1000,492,621,-859,-612,68}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-1000,-1000,791,352,1000,828,-368,1000,-1000,-1000,-601,-874,-276,1000,-520,-1000,-1000,-369,1000,1000,613,-1000,-811,449,1000,606,-92,477,648,253,132,485,854,-628,-1000,1000,80,1000,-1000,1000,400,-921,854,1000,1000,1000,-1000,582,-1000,-968,-903,-567,-207,-664,344,803,-799,1000,1000,-1000,-1000,1000,44,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{728,891,-618,-196,-686,-406,-524,-4,-389,-180,966,-976,-793,727,26,-284,-233,-784,-599,-940,-945,-165,984,123,-901,-826,-11,884,511,-200,-337,420,-636,-314,-867,337,426,-372,686,-599,-930,-824,552,-116,-323,-745,-922,-665,-977,826,78,-836,762,-310,-876,-246,-117,-121,506,637,-198,-98,732,-325}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-394,-180,95,258,1000,1000,400,-1000,220,-592,1000,-184,102,1000,840,-895,-1000,-336,1000,1000,1000,656,-1000,403,-32,-80,-1000,-355,305,-1000,-678,564,1000,-1000,-127,407,1000,1000,-175,852,884,-1000,-955,-142,561,-653,-872,186,-770,232,-1000,1000,914,-1000,738,-911,1000,-15,810,-1000,-387,663,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:cDBmY29IQkhSMXBDczN3bzZO", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameKey(long):java.lang.String",
            new int[]{993,88,859,-478,-705,-36,-785,-175,-579,-7,-117,-852,-198,-201,379,256,-389,-596,266,-141,-756,38,-398,-565,955,237,148,901,-52,860,924,716,-819,-437,-301,-810,671,-734,-438,-945,221,207,844,677,-922,-749,-626,675,-668,-655,267,187,-417,215,281,514,-212,701,-837,-518,-899,-498,-683,-526}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.DefaultNameProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameProvider():org.joda.time.tz.NameProvider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(long):int",
            new int[]{-115,498,-77,93,799,311,37,324,99,62,167,920,-215,421,-364,-983,955,905,270,177,-303,580,570,-496,-101,-160,424,-839,979,790,-441,340,903,-93,460,-740,10,51,-212,631,-828,-335,-510,141,565,-944,-302,5,967,-992,334,393,-106,-326,848,619,134,-328,-53,-592,242,636,-656,-763}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(org.joda.time.ReadableInstant):int",
            new int[]{177,560,-300,-617,348,-500,520,-706,-263,11,403,-299,741,947,-892,-2,218,444,260,559,-994,-433,-670,-754,-244,680,327,-923,914,-951,818,863,788,900,310,-181,829,-594,465,960,-533,-125,-853,439,-832,795,-128,876,-292,352,-113,208,122,500,-213,-370,234,764,-624,770,253,-446,282,-570}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE4", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffsetFromLocal(long):int",
            new int[]{-45,189,907,-502,-193,-224,169,510,144,-712,-184,-362,-933,-6,859,968,-165,-618,-414,180,999,475,-964,-840,-141,802,248,-365,-888,-471,-88,515,-71,79,466,-282,717,-493,-304,-515,-688,882,-545,457,-523,989,-758,105,218,739,86,-484,654,-101,-589,963,-22,944,382,-754,-640,-833,-382,331}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.ZoneInfoProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getProvider():org.joda.time.tz.Provider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:KzU5NjozMToyMy42NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-804,-239,-578,-19,-375,230,109,-175,635,-514,973,767,-772,16,-508,-988,18,-33,215,-258,-344,-233,-408,-848,410,-997,-577,-686,346,-678,-892,-378,968,219,-621,-865,-696,-974,526,956,410,150,269,930,67,-328,365,427,40,-221,792,724,-315,-816,-44,612,-82,705,709,-754,608,-567,-798,958}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-1000,-463,363,-1000,-70,-1000,-924,-288,804,-1000,-229,112,-416,-591,-580,1000,-1000,-911,-329,935,-1000,1000,-87,-1000,-128,447,-390,-829,252,99,-141,797,977,1000,-713,594,-571,-1000,723,-965,649,-522,-473,-582,846,198,737,128,639,676,186,-1000,159,1000,1000,737,-302,220,1000,1000,585,-866,-146,-459}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-937,-478,44,-836,-801,-69,-585,-720,586,-742,127,-102,-102,-915,-6,834,-64,-178,-570,100,-721,-2,18,-711,-566,-201,-165,-844,770,-373,0,674,836,590,419,429,-155,-737,-2,-998,83,-194,-228,-830,788,568,317,-785,-342,-308,-426,-616,546,911,-207,670,156,721,694,441,548,-592,125,-807}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{508,-831,235,215,-5,-895,-464,441,621,340,-255,-42,81,-287,-526,69,504,-994,419,901,502,260,-141,564,-218,466,-768,-851,649,-41,-664,-558,-936,-997,171,-266,-891,463,773,-10,669,-801,-451,727,956,899,523,477,73,408,-285,437,-734,313,396,31,-292,530,400,477,-280,941,812,-789}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAwMQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-791,-570,568,-111,962,-716,969,286,68,134,136,289,-400,161,87,-866,635,53,609,364,-627,-204,-913,-527,464,503,941,892,-162,159,-743,778,-380,583,-960,-689,482,-535,817,477,-789,-107,239,718,-841,418,819,209,789,-558,-54,47,562,454,-60,-847,-941,-525,46,329,-589,980,8,-439}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{436,24,-1000,693,-530,726,325,72,635,1000,1000,827,881,864,-643,-85,170,233,407,242,-568,844,285,-197,-20,-680,759,837,1000,-483,-778,-338,-785,-1000,108,-228,-9,-184,-694,6,12,-163,160,223,-669,1000,-1000,-917,-385,627,-895,1000,767,1000,315,1000,-990,-1000,-55,1000,398,-496,1000,-7}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{530,1000,30,759,-602,-551,819,-568,512,-248,-1000,983,881,160,1000,-256,-1000,240,-1000,47,-1000,7,-1000,243,539,1000,-636,-17,298,-951,-69,609,1000,-164,695,72,-992,-170,-15,-1000,248,-163,0,-271,1000,1000,-1000,365,611,-1000,-215,260,447,527,-1000,113,317,-975,-498,-338,-588,604,-920,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:LS01OTY6LTMxOi0yMy4tNjQ4", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-1000,729,179,647,1000,-428,-1000,-1000,-1000,1000,44,-1000,768,-1000,1000,-1000,-249,1000,-589,-848,1000,456,-1000,1000,1000,-262,724,-412,-1000,-521,1000,1000,-580,-1000,1000,-315,-1000,1000,-658,517,-414,1000,-567,162,-633,-1000,1000,-387,605,280,-1000,-812,431,-697,420,-1000,-1000,-702,18,508,701,-787,1000,121}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.String:bnVsbA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{596,920,827,510,-262,-868,-400,-941,-293,400,999,43,-355,142,-1000,-400,-1000,189,-318,446,-361,-640,180,-90,400,-162,1000,1000,-357,399,-283,427,503,1000,607,-38,224,1000,-528,95,209,-106,-615,520,-611,354,51,-792,900,849,-53,-858,-425,-14,326,-371,-1000,60,-165,-74,590,790,-108,-732}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getStandardOffset(long):int",
            new int[]{-839,-408,494,361,417,191,104,-655,-991,716,924,980,-152,-923,-536,-982,-758,-591,-637,546,-93,-485,-546,360,761,933,233,229,178,-422,-643,-514,-923,-213,-601,-650,267,244,-973,-759,796,740,18,950,699,28,-919,2,512,-870,-693,799,-814,-644,-710,-801,-367,45,406,-931,-975,-436,584,65}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isFixed():boolean",
            new int[]{-400,486,-579,614,760,893,-646,-525,-206,576,687,-767,448,-699,294,543,-680,-636,-846,744,-563,-420,71,-811,838,-915,321,115,-675,-897,816,-285,148,244,779,566,-890,966,-592,644,-643,-569,-416,-215,-81,118,-756,-373,-13,298,-235,-168,866,-241,-603,-298,-477,6,-392,174,-200,561,-941,609}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isLocalDateTimeGap(org.joda.time.LocalDateTime):boolean",
            new int[]{493,884,868,-689,-764,-660,926,-38,-174,-803,-258,-33,654,642,124,-601,-364,463,-704,916,731,645,-491,976,826,734,-276,285,802,-714,454,-708,-835,340,706,722,242,-936,817,-190,-413,454,-9,244,-986,300,299,-387,454,482,36,604,-320,-860,439,-854,-85,221,985,237,-739,490,-237,270}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{-61,-730,-358,-347,226,-239,482,-312,283,-993,-989,674,-378,-805,-285,416,364,-375,-715,512,-546,-161,208,552,-121,449,-660,-402,-135,-516,92,-434,-335,-295,436,974,681,878,159,-587,-596,725,-461,-971,915,963,-83,-644,-436,682,535,-110,-164,137,-557,971,376,-20,-730,-311,700,-71,-767,-124}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{695,527,-142,-197,81,-473,-150,-182,133,888,485,-252,981,-489,352,-243,746,695,-796,899,-590,-75,-720,412,-870,120,-133,107,61,762,758,28,-661,889,-105,-568,52,527,-309,976,717,820,-750,-5,110,-139,38,746,-916,-994,434,-402,469,-673,14,-744,99,-207,-557,357,526,-275,405,999}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Long:LTkyMjMzNzIwMzY4NTQ3NzU4MDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "nextTransition(long):long",
            new int[]{-313,121,-805,-726,298,738,-82,926,37,782,-923,362,-130,-162,-282,592,-111,898,-610,436,-11,890,-327,174,-920,264,483,834,621,246,863,-759,-198,727,-825,267,-754,654,66,720,-234,272,919,-230,409,576,-941,889,-196,-39,472,-88,622,-813,-554,-978,903,-485,210,-817,-95,-862,366,-99}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "previousTransition(long):long",
            new int[]{242,608,-612,-842,949,264,-606,-29,705,497,-415,-796,865,-847,-962,420,199,-144,-119,-705,-423,-691,-922,-339,-391,-598,-575,183,818,244,941,-239,-280,916,59,-877,-676,-738,542,222,965,350,893,52,163,-626,168,-741,235,721,-426,562,-242,671,-993,-624,-329,783,-231,-709,795,404,-897,84}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setDefault(org.joda.time.DateTimeZone):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setNameProvider(org.joda.time.tz.NameProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setProvider(org.joda.time.tz.Provider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("java.lang.String:YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toString():java.lang.String",
            new int[]{-557,715,252,9,613,873,786,262,604,568,-160,-661,-569,-976,-280,437,-965,-651,-219,-595,431,430,161,-836,-655,279,882,-894,-501,-716,-575,393,456,691,522,72,907,-711,-183,747,671,-233,327,196,-705,-513,-850,760,131,541,-231,-416,6,845,466,-132,233,-216,-143,187,575,447,366,-402}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:java.util.SimpleTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toTimeZone():java.util.TimeZone",
            new int[]{-784,448,514,755,-371,534,863,202,541,281,-168,86,-264,-275,502,36,823,991,422,-200,-20,-880,337,284,862,-151,594,-391,911,-20,-848,639,-189,-160,54,-7,-390,-949,413,910,174,321,-507,-517,-935,-694,-689,-212,-338,-283,335,189,-572,-208,296,731,-425,-983,944,-62,60,561,640,559}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{-76,-693,176,-36,-595,180,-672,-217,628,833,222,55,-135,-848,-163,993,-474,-878,474,-413,950,490,484,834,398,-165,-264,-369,-291,636,659,270,666,-356,180,-586,-7,-588,-309,-896,141,199,-703,851,-448,605,-976,206,898,-699,-593,-672,972,-5,960,-981,-646,372,359,-554,757,-824,879,353}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "org.joda.time.DateMidnight", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{275,999,166,185,880,411,-620,-709,-191,762,-539,-390,-248,-360,-211,140,-210,867,635,-825,694,-673,-875,-640,379,571,-281,94,423,-279,787,921,-909,735,-963,-59,638,-90,502,-928,768,680,626,995,107,189,556,-975,-720,-58,181,813,778,888,684,-227,163,-305,408,453,378,-281,-293,-316}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTimeISO():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withChronology(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZone(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getChronology(org.joda.time.Chronology):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getInstantChronology(org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInstant,org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInterval):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getZone(org.joda.time.DateTimeZone):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
