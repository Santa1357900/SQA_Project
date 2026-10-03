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
            "org.joda.time.chrono.ZonedChronology", "", "getInstance(org.joda.time.Chronology,org.joda.time.DateTimeZone):org.joda.time.chrono.ZonedChronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{955,-505,-46,-949,-625,-918,539,-19,-356,-244,705,-493,134,-40,-867,-53,-352,-433,169,-729,-263,745,89,894,-869,111,248,-63,-751,-968,913,630,-513,-679,-246,-315,216,6,-243,-287,245,-592,710,-945,-135,-698,244,-235,557,616,973,-103,-79,793,-776,-268,-540,-267,514,156,-511,59,-964,966}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("java.lang.Long:MjE0NzQ4MzU0OA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{-208,174,-1000,-623,586,-922,79,361,587,1000,530,-627,329,-366,-1000,-1000,-410,-357,-1000,-410,-492,-1000,79,0,342,-433,162,148,0,-17,-348,1000,173,93,-1000,465,1000,975,274,-115,153,877,-1000,-557,-414,-727,0,323,-203,-1000,325,748,-551,128,1000,955,-707,145,519,1000,32,-958,98,-45}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("java.lang.Long:LTI2Mg==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertLocalToUTC(long,boolean):long",
            new int[]{727,-391,-200,620,385,-369,868,-942,366,-915,454,227,809,-842,-353,-721,-889,-106,-447,267,-604,983,-865,-601,895,349,-369,-868,-37,-880,-928,-481,184,-17,644,-601,233,362,-591,-759,8,387,-741,-220,-94,199,-121,-90,59,-228,-32,-244,-154,-846,-6,305,-926,-226,-636,-459,826,313,-955,648}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("THROW:java.lang.ArithmeticException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-84,-401,-781,-705,-374,964,902,7,925,-395,611,-51,-871,-298,-991,-786,-78,-510,-37,-346,805,-869,946,-507,413,150,798,205,-286,-162,35,755,-705,-590,12,850,-523,831,-311,-867,839,244,499,401,-127,-315,839,-104,-26,-582,122,431,-295,588,-28,-255,427,166,-838,798,-284,-835,-820,987}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("java.lang.Long:LTYz", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-468,145,-72,-834,979,104,128,673,182,-636,-770,967,426,145,-596,-449,-138,269,561,190,367,473,760,272,447,524,408,-802,-739,-866,-466,-496,-977,-15,495,881,-396,375,936,-385,-39,-33,-463,96,187,886,174,973,-567,-937,456,326,740,91,301,-235,-114,-882,-787,775,-235,-668,-798,-190}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("java.lang.Long:MQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "convertUTCToLocal(long):long",
            new int[]{-350,-28,662,606,238,-364,-859,-139,487,359,957,504,702,-841,432,138,469,963,-746,-563,-185,881,-384,-297,175,462,809,-840,259,198,-935,-574,-864,877,547,-848,350,-427,826,-893,762,35,933,141,-79,8,122,397,257,-350,850,910,-842,-379,164,-707,-931,612,-320,-393,-825,186,-348,697}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "equals(java.lang.Object):boolean",
            new int[]{525,-435,40,-378,-177,-766,-399,-492,-69,-155,516,-263,-389,-354,459,-952,257,-857,531,-500,585,-70,-593,570,181,-600,481,687,976,141,763,361,-567,555,993,-955,518,-85,778,-170,-635,929,429,233,-28,-304,596,-607,199,892,-155,275,925,-1000,824,-310,263,442,-144,-704,-572,596,428,-851}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-876,-1000,-622,-1000,-843,1000,-1000,-240,-109,59,738,110,597,455,335,-79,-807,425,705,-880,-400,-558,-447,-551,-975,708,1000,113,-1000,1000,-1000,-863,-489,-76,-804,1000,1000,1000,-553,-47,956,-1000,928,-513,235,-83,182,-786,-5,-500,-201,-377,-455,-1000,-1000,288,-832,-594,-1000,-1000,-614,139,834,-852}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{-221,-1000,-16,1000,-1000,1000,-903,1000,1000,1000,562,109,1000,-397,1000,202,-1000,1000,1000,-1000,-926,-1000,-518,-1000,-1000,1000,1000,-1000,-138,1000,879,611,233,-1000,-69,1000,828,206,-1000,1000,1000,-1000,1000,830,66,420,-669,-1000,-266,-1000,1000,-1000,-731,-1000,1000,1000,1000,153,-8,357,-1000,1000,-1000,144}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forID(java.lang.String):org.joda.time.DateTimeZone",
            new int[]{438,512,-212,113,-888,581,457,-59,183,-276,-401,74,-434,690,537,520,362,728,869,-266,19,-860,-157,-291,-332,-360,-528,21,741,308,-87,-885,295,-472,95,-877,-453,-853,847,732,40,281,-176,-743,607,684,-555,-60,-171,-593,746,-279,-972,592,-472,256,23,-332,563,784,-162,-11,697,-385}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{689,-658,-317,-579,-261,70,-278,393,523,636,-631,-538,771,-445,-162,462,205,-535,300,-322,370,-580,-268,911,43,517,696,-211,114,638,201,-279,234,-96,-776,-891,484,-586,469,572,873,-211,-469,242,-456,-902,-281,520,-748,-124,-479,-949,874,84,-81,150,511,-870,584,430,-921,-665,-625,-51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{-340,85,-141,-519,-840,594,-216,-163,-160,-302,-124,105,596,309,263,-817,818,51,268,873,-215,25,338,527,436,497,458,-456,-584,-138,-752,-279,-197,-959,397,598,-205,450,-921,138,253,610,559,356,28,557,330,841,704,309,-185,502,-424,814,-103,-663,-505,-960,389,-481,520,-807,908,698}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHours(int):org.joda.time.DateTimeZone",
            new int[]{1000,467,1000,461,126,-539,-278,-112,70,1000,-1000,-834,1000,-858,-478,-225,739,-1000,-158,-1000,-47,-1000,-1000,1000,191,-957,307,54,1000,323,1000,-129,203,623,-1000,-643,151,-497,461,-22,1000,1000,-695,-168,-1000,-149,-561,422,-1000,-1000,531,-1000,1000,636,-868,560,76,1000,1000,-1000,-1000,827,9,641}));
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
            new int[]{-146,259,-832,-815,196,110,451,167,103,-515,-1000,81,662,44,-826,-630,-59,-300,-1000,111,289,1000,416,-263,113,-828,-151,585,-233,-457,366,1000,1000,445,-612,159,-195,46,-318,-759,-911,478,-584,88,-1000,197,692,-191,834,765,-317,74,319,78,-20,-710,-505,-785,-762,-383,-322,-1000,-669,-235}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{-727,875,-1000,841,-264,-611,-784,-19,-494,785,1000,-668,642,-1000,387,135,-657,-1000,-1000,-673,-1000,-1000,-1000,-1000,-640,959,-115,1000,605,446,-568,-1000,-1000,-1000,1000,-188,-1000,-976,-254,-434,169,-1000,-432,-653,1000,1000,529,-1000,-1000,803,103,-125,1000,-1000,-1000,1000,1000,1000,1000,-328,-1000,-1000,-1000,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{972,-557,947,-625,941,172,476,-71,-470,453,174,-338,588,-849,-365,986,556,312,141,-236,905,-556,922,735,814,-765,-613,-720,100,9,198,-344,298,-837,-443,606,-554,993,-560,-632,-290,-108,-301,550,226,-309,-106,-908,740,116,-449,556,530,-255,-903,-609,-127,-329,191,488,-116,-869,-643,-669}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{-292,515,-681,-66,611,-916,-351,-510,53,-133,455,-813,741,-1000,-563,-755,708,-238,-872,-13,64,256,374,74,-514,-785,45,229,-65,235,1000,-268,-299,-450,-912,-273,509,223,-449,-203,-746,-90,-1000,-507,-678,434,401,-768,54,-125,453,494,-13,-908,-1000,-207,-247,-105,1000,-170,-816,-1000,-776,361}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetHoursMinutes(int,int):org.joda.time.DateTimeZone",
            new int[]{68,90,591,-776,313,755,93,694,490,-550,-602,-437,-257,793,-160,946,-182,1,216,-341,-574,-130,-315,565,595,462,4,-810,242,90,-805,-40,116,-944,284,234,-66,827,-912,394,70,867,218,-270,-546,-844,423,552,-520,610,-807,-935,-317,38,165,874,-319,-897,-746,-303,471,-224,702,-171}));
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
            new int[]{54,-815,91,869,-818,-340,-730,157,790,-684,552,-19,440,545,940,899,521,-506,-54,922,703,680,188,-909,-705,571,382,-401,673,-486,-9,-405,-738,138,-405,242,-731,947,142,-169,-648,177,-389,-219,723,-771,-637,-776,-222,948,453,-473,307,-104,541,582,-429,-657,663,11,-921,-786,112,485}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "forOffsetMillis(int):org.joda.time.DateTimeZone",
            new int[]{-1000,-733,165,496,-590,-437,-1000,-428,-305,-904,1000,-14,-466,1000,246,1000,425,335,-804,-1000,1000,297,1000,-674,-828,1000,1000,78,-19,29,184,-1000,-757,-796,-862,-252,-834,714,1000,536,779,-1000,359,-508,1000,-1000,-794,-265,-941,485,1000,844,1000,1000,-1000,-472,-753,-887,-605,360,156,-1000,1000,1000}));
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
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getID():java.lang.String",
            new int[]{-689,427,97,-503,470,90,939,-112,-44,412,644,-953,-529,31,479,-516,230,223,-207,96,642,-586,-654,751,839,593,483,92,-29,153,723,492,-870,-199,737,-28,-845,287,676,-172,588,925,-675,672,628,152,-334,673,-953,-322,-915,-133,-300,202,-310,-412,570,96,-35,332,-973,-1,-629,612}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("java.lang.Long:OTIyMzM3MjAzNjg1NDc3NTgwNg==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getMillisKeepLocal(org.joda.time.DateTimeZone,long):long",
            new int[]{-733,-238,777,-579,-830,-295,412,-451,-465,-471,533,410,-387,-994,-925,-879,-647,-496,-509,-599,124,-204,-273,598,-176,447,-392,710,-731,-553,-387,-805,318,559,-337,548,532,-15,98,-808,331,362,302,16,596,-119,-390,-914,-540,324,47,-509,611,-756,-776,75,-127,150,278,-100,57,567,145,653}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjA3MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{485,-474,-236,-908,687,605,-96,711,519,-251,601,211,-734,-487,875,-390,457,391,-742,178,754,-388,432,23,-961,750,-598,254,708,-134,412,170,-231,-817,-41,-986,442,598,-116,-77,683,738,-259,862,269,742,-557,-401,-71,-563,-469,-945,-648,178,-713,-266,873,-751,437,-655,110,-551,171,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{554,-624,-307,1000,-492,-1000,660,739,-576,-1000,215,-1000,1000,-784,-1000,665,71,-614,-676,684,-192,-1000,-594,1000,-53,-1000,-1000,462,25,-483,1000,-1000,1000,954,-354,420,1000,-1000,-248,-1000,-1000,804,386,-187,546,-1000,1000,-1000,691,-252,761,1000,639,-6,-306,687,881,489,550,-1000,-126,-912,-493,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{142,876,808,-777,728,-413,305,862,-316,21,283,-820,-566,-839,-385,-585,-872,879,-471,676,872,266,926,953,790,-722,-695,141,-160,288,418,397,815,-592,-150,248,-856,768,388,951,-248,352,267,768,521,-587,-844,-898,693,-966,-902,-321,-72,973,522,372,691,-424,925,655,-877,-775,-163,189}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("java.lang.String:LTk2OUY=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long):java.lang.String",
            new int[]{983,225,-983,-969,-191,703,192,360,-556,-550,-968,654,513,80,-786,-116,-972,642,841,-772,201,-873,657,-519,-208,-706,-300,600,144,-339,-251,-17,-385,-224,15,119,-758,-797,197,374,-836,66,-768,829,-409,-560,853,358,909,-611,-574,389,891,47,425,182,-75,773,-597,883,-325,110,-689,76}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("java.lang.String:KzU5NjozMToyMy42NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-24,896,-534,38,-553,893,74,-170,480,-866,220,-439,-482,-747,-470,-256,-173,126,-352,695,5,187,-296,-312,192,-877,-384,588,652,-518,797,-907,-449,905,-944,-854,-376,929,-656,612,687,611,323,889,922,572,724,-244,287,-496,914,-513,480,647,202,-989,-567,308,-998,-276,515,100,-811,-531}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{571,83,-1000,964,327,-1000,834,-49,1000,-1000,839,859,-598,-1000,40,237,-1000,1000,167,421,417,1000,505,-71,-133,494,1000,436,-102,-642,-91,-1000,-644,1000,-277,345,1000,-192,-1000,223,487,1000,700,1000,2,-253,-884,-1000,783,139,-1000,-646,-1000,-1000,-181,-1000,-778,-17,1000,-113,829,773,-186,-854}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{-145,-687,-68,466,847,-1000,57,-342,754,-161,-802,-269,-1000,-1000,1000,-145,389,477,736,-1000,-917,-897,117,-770,380,1000,-1000,-1000,339,1000,718,46,1000,-95,581,108,86,-1000,-724,5,276,-526,-762,986,522,-388,739,-36,-5,450,-519,428,475,-849,-203,-484,-301,-453,1000,154,-1000,-1000,923,251}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00037() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getName(long,java.util.Locale):java.lang.String",
            new int[]{80,335,-769,966,-255,-1000,0,1000,-425,-384,-1000,143,-413,-419,1000,627,-906,768,1000,-1000,172,222,1000,0,-295,501,-948,-404,958,-15,1000,-1000,-560,957,879,-540,-1000,-1000,-575,615,-494,1000,236,182,-1000,628,0,0,1000,-502,0,409,-209,-334,397,-541,-554,1000,1000,-291,293,342,772,366}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00038() {
        org.junit.Assert.assertEquals("java.lang.String:X0QwTGYweg==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameKey(long):java.lang.String",
            new int[]{704,175,518,69,-277,-166,-481,696,470,907,349,-884,994,-592,-766,-568,35,-905,396,697,-271,191,741,410,-464,-893,-109,-277,-444,-306,-147,-554,883,13,-194,-630,-21,515,-563,185,-820,-399,-932,-681,389,-922,419,-608,-200,717,251,658,14,279,383,817,350,725,-397,864,315,74,-144,276}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.DefaultNameProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getNameProvider():org.joda.time.tz.NameProvider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTIxNDc0ODM2NDg=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(long):int",
            new int[]{-231,-143,-596,-145,-519,-586,-649,450,120,-321,-39,-259,427,445,534,-906,-667,-972,-473,672,-666,-945,367,452,9,-121,-953,-60,656,-801,-905,837,281,-934,97,234,-293,919,971,-2,456,549,430,933,239,955,676,27,-629,-31,274,-354,247,177,335,863,-870,-782,709,-674,-71,844,-312,985}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE5", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffset(org.joda.time.ReadableInstant):int",
            new int[]{-466,514,801,-256,426,861,-933,421,82,197,-790,984,-859,225,-967,241,-197,586,-490,185,-831,7,-252,-722,-818,-864,108,63,-807,746,47,-59,-696,558,753,-837,91,-802,514,29,-969,979,867,99,15,134,90,-179,-281,-126,-678,-612,-735,229,-277,873,-397,-980,466,-262,365,494,-917,637}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("java.lang.Integer:LTE=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getOffsetFromLocal(long):int",
            new int[]{-615,-139,23,66,-451,105,-979,-371,-442,723,950,645,526,548,335,-233,640,169,-634,659,977,430,409,297,618,-961,-769,-832,-685,-947,-431,381,986,-600,-442,-841,522,587,-583,167,-452,246,-179,-310,56,-688,-596,830,188,230,-33,545,-210,-141,-868,-363,857,-507,418,582,-512,771,24,-501}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.ZoneInfoProvider", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getProvider():org.joda.time.tz.Provider",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAwOjAwLjAwMQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{459,735,-469,728,535,-702,-574,-168,-534,-507,-155,967,343,-920,287,-178,661,-871,568,-476,468,-871,-410,642,368,308,118,894,-242,839,-895,-563,149,-234,88,-474,387,-323,-306,-665,187,-578,399,-228,-504,-479,341,221,-87,261,210,336,615,-21,-982,248,-909,516,899,821,323,425,-10,-21}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{1000,660,-285,-272,592,377,-2,1000,-250,-1000,83,-77,285,-74,-1000,-410,-2,1000,-1000,872,-99,-694,-575,39,871,-609,672,319,687,1000,-1000,-366,-65,-1000,1000,-1000,-286,-1000,-743,1000,38,1000,-1000,496,16,1000,974,-17,1000,519,-581,434,-823,312,-541,-470,973,-141,1000,-182,260,1000,1000,-968}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-956,-34,-983,-547,135,-1000,-281,951,-981,-511,-36,90,883,-489,308,-949,-687,-91,869,-937,-437,674,517,-230,343,-517,-269,-372,234,533,-122,128,292,-27,-340,658,-615,444,-28,-283,-616,-523,-449,311,-551,867,-572,417,353,496,425,-373,-979,570,712,278,291,715,-195,-317,-733,996,-727,-527}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("java.lang.String:WQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long):java.lang.String",
            new int[]{-476,-12,991,-403,-491,-899,-366,288,440,-994,253,379,539,452,-449,-442,-124,-415,409,-110,796,477,773,329,-34,-946,-139,541,274,-295,589,26,259,-1,-947,425,-254,-878,485,-115,939,-54,138,404,328,937,-729,208,482,525,582,-643,28,-989,245,31,950,-404,-962,-752,709,697,972,-890}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("java.lang.String:KzU5NjozMToyMy42NDc=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-596,-1000,-797,359,-299,140,504,1000,492,1000,-1000,817,313,643,-415,997,-53,-255,-288,-1000,-288,414,-739,-74,573,918,242,327,-1000,-175,-383,-834,68,609,-289,-372,1000,254,-621,-284,-835,419,-151,260,-412,-619,226,-306,532,-222,525,367,819,-854,509,-345,278,286,265,-1000,-467,-504,707,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("java.lang.String:LTAwOjAwOjAx", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-1000,-488,-1000,-1000,-1000,-56,-293,1000,-1000,1000,-1000,287,679,-109,1000,404,1000,-1000,-781,-1000,-99,168,-883,-287,943,17,815,-155,806,498,-660,-1000,328,1000,-1000,827,8,916,1000,-407,1000,1000,20,282,1000,1000,-740,1000,434,-1000,1000,335,543,-149,1000,-222,-1000,-248,-355,-1000,-30,-159,-298,1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("java.lang.String:KzAwOjAw", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-32,-82,-692,247,-695,-370,194,-713,-753,24,276,334,939,147,-912,-902,898,-765,814,847,-304,21,426,285,732,10,-435,46,687,526,620,964,228,350,154,165,364,87,298,-675,487,-905,-989,946,597,715,-779,-597,427,-124,779,-312,307,-314,-120,253,459,543,140,-790,-886,528,-110,-814}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("java.lang.String:LS0weDgwMDAwMDAwMDAwMDAwMA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getShortName(long,java.util.Locale):java.lang.String",
            new int[]{-752,-313,-1000,237,635,-306,-896,79,1000,-272,-835,692,471,1000,-859,334,820,-912,432,479,222,-640,-431,-463,-835,-705,751,1000,1000,440,-459,369,115,-1000,704,123,62,559,577,106,480,284,-577,877,-1000,-1000,-274,235,1000,88,-1000,891,-261,-1000,1000,-666,-1000,-3,1000,-402,648,-329,512,17}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("java.lang.Integer:MjE0NzQ4MzY0Nw==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "getStandardOffset(long):int",
            new int[]{330,-122,801,429,391,-673,-198,-630,633,-682,957,-554,-540,769,778,298,152,-780,33,-990,447,-534,-517,-585,714,-126,-869,23,-379,343,463,136,808,-353,-133,-60,-620,-950,-95,854,-781,-507,-538,-702,-902,-588,-95,153,-643,-721,363,-323,-482,-643,951,-595,622,831,396,638,305,-553,-149,-445}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isFixed():boolean",
            new int[]{555,-946,-163,280,-720,116,13,-136,829,-935,643,442,83,516,-658,-554,507,-537,-413,-615,-574,714,111,-923,459,404,-823,-906,548,317,-393,-173,-137,689,-514,952,698,-446,616,64,406,727,15,-227,-594,73,-998,655,343,835,-249,113,604,-514,579,409,-462,-174,713,934,1,858,759,102}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isLocalDateTimeGap(org.joda.time.LocalDateTime):boolean",
            new int[]{209,-593,414,423,-983,-287,57,-975,924,-609,5,330,615,-518,191,199,578,-228,983,-842,-747,682,858,300,515,-579,18,-715,431,-285,483,-793,-596,-639,609,-560,902,207,-854,340,-199,758,-1000,677,47,541,-365,4,810,-394,-258,-147,606,112,197,-430,-41,-221,-372,902,-906,-780,965,40}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("java.lang.Boolean:ZmFsc2U=", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{995,-757,-50,524,152,-698,-842,821,-344,-184,501,800,-601,-742,191,-719,-250,-994,486,796,-198,83,855,-46,785,-332,828,85,381,-577,144,-453,517,127,-887,-614,-442,-253,37,-621,-196,693,994,-56,648,698,344,866,860,557,-129,342,53,770,681,-540,-389,42,666,-94,-924,-470,-371,417}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("java.lang.Boolean:dHJ1ZQ==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "isStandardOffset(long):boolean",
            new int[]{454,-566,-253,-144,398,-215,342,249,694,-326,-744,20,-101,514,943,-139,-796,-591,546,-713,-782,-117,-900,-179,473,-350,692,547,50,340,338,808,552,-374,-360,156,-988,-262,245,-12,277,55,551,755,-90,-902,-832,-895,-303,-913,-764,857,509,-9,840,344,419,-59,503,317,750,800,-979,-675}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00057() {
        org.junit.Assert.assertEquals("java.lang.Long:LTU1", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "nextTransition(long):long",
            new int[]{-601,596,55,-5,146,298,-99,-616,958,-845,147,-667,-635,-554,550,-266,584,-602,609,-674,770,-667,436,434,547,566,-262,305,302,-23,-199,-459,303,662,797,573,464,-895,-801,-752,301,-95,-329,168,-501,240,367,-342,-650,-12,296,953,-674,-306,109,542,-953,-571,-522,460,-28,990,-904,767}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00058() {
        org.junit.Assert.assertEquals("java.lang.Long:MA==", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "previousTransition(long):long",
            new int[]{-36,789,504,534,212,-174,-28,826,108,510,128,536,-69,-820,295,984,394,-688,88,881,712,-332,14,-380,-228,787,411,-77,-234,-911,-954,-926,-911,899,473,-454,-87,-648,-933,-1,-604,-698,575,339,223,-863,-71,-192,-158,-303,143,946,629,-623,147,627,-975,931,571,402,624,-487,-278,-154}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00059() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setDefault(org.joda.time.DateTimeZone):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00060() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setNameProvider(org.joda.time.tz.NameProvider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00061() {
        org.junit.Assert.assertEquals("VOID", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "setProvider(org.joda.time.tz.Provider):void",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00062() {
        org.junit.Assert.assertEquals("java.lang.String:", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toString():java.lang.String",
            new int[]{729,-682,897,-28,796,403,-421,-579,561,-586,25,489,29,225,-878,-860,735,825,-107,-416,-581,984,144,636,762,346,388,940,-161,869,-5,-472,861,109,336,893,973,-831,779,265,414,-687,-143,-889,-813,219,-343,980,-485,648,529,-181,843,885,-393,574,-81,-247,285,-462,568,-793,809,-769}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00063() {
        org.junit.Assert.assertEquals("TYPE:java.util.SimpleTimeZone", DEReplay.run(
            "org.joda.time.DateTimeZone", "org.joda.time.tz.FixedDateTimeZone", "toTimeZone():java.util.TimeZone",
            new int[]{-72,-199,-451,880,814,-40,-167,559,18,768,-535,699,-61,380,-565,704,-680,-787,-157,47,-394,859,-486,78,145,813,-510,548,-877,-497,-853,-798,377,943,235,106,521,689,670,308,163,745,600,892,-545,525,-63,-486,-332,95,-669,-889,-958,494,-615,-365,792,163,-188,368,-212,236,-832,-317}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00064() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "", "parse(java.lang.String):org.joda.time.DateMidnight",
            new int[]{-685,-834,-422,195,387,339,189,-29,868,576,284,851,-284,-644,-846,32,578,454,137,-574,548,-478,263,798,628,144,-938,181,459,-806,814,-989,228,-965,877,-588,-535,-61,547,-822,-122,921,405,116,-680,-852,-933,-379,540,-971,823,-366,-555,188,-199,-552,-727,579,-48,-754,-710,-771,-579,-335}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00065() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateMidnight", DEReplay.run(
            "org.joda.time.DateMidnight", "org.joda.time.DateMidnight", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateMidnight",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00066() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "", "parse(java.lang.String):org.joda.time.DateTime",
            new int[]{388,-805,48,1000,85,146,-276,1000,-291,897,-820,891,945,-1000,-520,-1000,-760,1000,-1000,-1000,498,-756,1000,-435,-539,280,1000,476,-766,769,-351,-469,1000,-1000,-281,373,-1000,64,-284,-474,723,-689,-401,-206,-1000,-110,260,-1000,130,-183,-411,32,-229,163,-79,-1000,-231,-581,-86,-1000,1000,-187,-387,-537}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00067() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00068() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTime(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00069() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "toDateTimeISO():org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00070() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withChronology(org.joda.time.Chronology):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00071() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZone(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00072() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.DateTime", DEReplay.run(
            "org.joda.time.DateTime", "org.joda.time.DateTime", "withZoneRetainFields(org.joda.time.DateTimeZone):org.joda.time.DateTime",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00073() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getChronology(org.joda.time.Chronology):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00074() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getInstantChronology(org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00075() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInstant,org.joda.time.ReadableInstant):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00076() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.chrono.ISOChronology", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getIntervalChronology(org.joda.time.ReadableInterval):org.joda.time.Chronology",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00077() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.tz.FixedDateTimeZone", DEReplay.run(
            "org.joda.time.DateTimeUtils", "", "getZone(org.joda.time.DateTimeZone):org.joda.time.DateTimeZone",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00078() {
        org.junit.Assert.assertEquals("NULL", DEReplay.run(
            "org.joda.time.field.LenientDateTimeField", "", "getInstance(org.joda.time.DateTimeField,org.joda.time.Chronology):org.joda.time.DateTimeField",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
}
