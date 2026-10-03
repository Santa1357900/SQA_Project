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
            new int[]{-16,-392,347,500,-540,-566,-148,400,228,-873,465,-700,-293,183,-560,786,-617,688,561,-690,-157,320,925,444,-120,79,199,-389,-850,-712,-663,-855,-179,-886,862,-313,-552,-45,-216,394,921,-629,967,-795,-270,-47,-655,-495,45,487,-239,910,-878,-900,-597,-363,429,-319,-173,-74,-478,809,-626,315}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00001() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "append(org.joda.time.format.PeriodPrinter,org.joda.time.format.PeriodParser):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{632,423,-134,-372,799,874,-479,35,-686,-765,712,-734,-198,51,782,462,559,363,-508,-570,-929,-759,-615,-923,-73,-150,-144,-910,877,819,942,812,-38,-299,-505,-49,124,-748,-761,-670,534,905,-508,222,-34,-646,782,-3,-986,-524,-491,-240,650,-38,-121,483,713,955,-156,-290,-732,-225,554,-659}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00002() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendDays():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{128,-291,133,548,-468,-686,-297,-31,-120,475,829,-77,-88,478,237,-34,-375,228,-318,-38,993,974,-320,-156,357,-674,-113,684,-117,27,-860,969,347,-946,577,-177,-42,739,227,-148,-65,-167,991,270,-895,663,20,7,918,912,186,628,984,904,-472,43,-536,758,345,251,472,-736,-12,-374}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00003() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendHours():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{731,-77,359,788,571,-984,484,425,954,752,-97,-210,720,-208,-844,184,503,864,-276,35,-619,-333,511,-662,-121,856,225,-559,-946,-665,-683,789,-277,7,744,730,-647,-347,547,-497,579,666,995,-877,897,-11,878,-954,-320,-196,-567,213,328,-730,313,-641,120,912,75,304,113,-838,-310,375}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00004() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{899,636,507,543,51,-120,-794,580,994,-559,-894,-304,-6,762,-504,-519,-403,-60,-520,-938,839,-674,530,837,-505,-452,-811,-173,-57,-65,-895,-204,-137,-724,-625,-346,-511,56,-152,284,417,-109,-550,904,-936,-246,873,-645,816,-241,32,299,600,-242,-403,-819,177,-181,575,72,-198,794,451,-16}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00005() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendLiteral(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{96,901,39,371,997,-128,54,-528,685,38,598,628,916,-662,383,7,-879,126,318,-925,27,93,744,954,-834,21,299,-576,-892,-393,825,-998,870,534,587,826,-509,-820,-350,-241,7,-56,776,-930,180,31,398,324,-729,-306,-650,-276,-166,472,-113,8,83,-288,237,-740,-269,842,442,490}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00006() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMillis():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-446,-425,541,-872,-262,-364,522,-299,-761,-416,748,-753,836,-998,-447,-745,419,146,-693,-533,168,-219,933,507,999,615,-405,-140,-419,-999,-511,-352,-155,-877,-740,461,37,520,789,-583,116,-567,832,567,555,-772,-704,-223,465,951,-152,303,366,-107,-51,-573,905,18,442,701,-668,-948,-630,646}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00007() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMillis3Digit():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{170,790,-354,-352,632,-453,-532,491,744,-229,43,462,-760,-20,921,118,811,-477,-735,-438,486,413,-414,-233,962,111,836,48,697,-4,534,748,199,-643,-710,539,207,954,371,247,212,-616,-278,-420,-862,-171,275,-242,-121,94,295,-343,-103,-714,-982,-191,-919,-40,-99,-667,-304,-885,437,-245}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00008() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMinutes():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-275,736,414,542,-426,-40,935,-429,846,-661,-682,-780,603,588,-815,240,116,-535,-950,178,-838,-936,-298,-568,-98,-181,-812,-258,-631,-865,160,-442,717,904,-976,866,64,557,-484,-790,36,751,-847,882,905,313,-675,576,-396,641,717,-878,-138,-602,-799,372,-843,-937,-591,800,297,-363,-259,888}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00009() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendMonths():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-448,970,-678,-962,-897,980,546,985,482,-820,-582,25,-720,6,-99,-633,-547,816,934,77,-326,444,-114,-269,-79,-866,-633,380,504,488,32,579,285,-878,-763,240,-737,-837,-556,335,854,385,-865,151,-759,857,644,678,972,-730,-181,-8,-379,796,-310,-970,-223,-732,448,34,-108,-242,932,-882}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00010() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-90,586,-341,-540,-299,-991,-162,914,16,202,-583,-946,850,-792,404,497,-582,-844,-846,16,-225,-58,-144,152,672,-320,-182,-691,-482,810,533,329,-583,-30,544,-33,-368,787,-597,-143,-450,-418,631,-414,256,-332,-436,295,139,380,-845,900,-370,287,-605,414,-886,120,-109,-828,-656,-123,278,794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00011() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-1000,1000,-629,-382,400,233,-263,-1000,1000,-747,-792,153,460,506,1000,1000,-1000,1000,-95,-855,-33,-1000,1000,1000,-1000,837,474,-1000,-102,13,-1000,1,-130,1000,1000,741,114,-840,-1000,-725,617,1000,-285,-52,866,1000,-504,1000,1000,-1000,-961,-1000,968,-121,1000,918,800,76,1000,51,-856,875,1000,625}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00012() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{846,-245,766,841,116,266,-249,-775,140,-850,843,-23,481,457,-315,834,-443,598,151,532,73,78,-961,463,765,691,148,982,824,476,-122,-129,153,-504,936,-857,-856,580,-65,387,76,574,726,119,418,-625,158,-726,338,21,617,864,533,-531,181,-907,137,334,527,-223,-86,825,805,-240}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00013() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-306,316,-192,373,-599,1000,571,225,-445,296,208,-599,30,-252,-260,794,208,403,1000,-665,-636,-1000,1000,-359,-1000,-379,-440,-944,-499,-847,-978,5,-272,998,210,141,-1000,660,-1000,175,457,-935,-158,-1000,99,-1000,589,822,-1000,-1000,715,171,-153,-156,165,-251,-1000,-235,-406,393,-61,691,1000,-1000}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00014() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendPrefix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{545,611,-588,-796,-724,-576,-757,-726,442,-736,-22,-477,760,-602,487,867,784,-280,640,-281,-258,175,542,842,-490,-643,347,-792,-615,373,-865,-826,109,847,-276,-10,172,-972,-472,617,-217,-646,-388,182,121,553,950,964,-529,-617,544,795,-326,-570,573,944,-37,-920,994,167,705,-316,136,-578}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00015() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeconds():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-865,-716,779,801,-308,718,850,814,-492,-498,17,749,-58,552,-516,684,-560,-677,129,220,-408,-258,-973,-453,-721,-701,-93,923,-238,995,-490,188,-546,-76,-769,769,554,409,-587,573,714,-175,178,-855,-517,-512,-222,511,-593,-644,-889,246,-668,-369,-726,-514,727,856,-209,-182,653,587,516,-699}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00016() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSecondsWithMillis():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{499,901,-44,533,612,-293,698,-195,523,511,-980,-56,-333,988,-961,-646,-839,-136,725,-218,987,738,-594,-70,-859,844,269,-619,620,-115,729,689,440,-12,-290,-423,-570,579,390,-931,457,-531,-250,-915,-41,-137,404,512,-815,104,-395,130,-640,984,-921,-134,-822,241,-622,807,-542,-562,-167,-806}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00017() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSecondsWithOptionalMillis():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-959,-633,758,11,-264,-434,455,26,677,-897,38,561,736,-643,171,617,816,-64,484,929,756,-919,575,-259,491,205,15,-64,855,-431,-872,885,-991,-135,807,-658,-642,986,-567,367,-936,625,91,-233,-250,-472,-309,-109,-133,-836,-628,736,-693,-572,596,259,-656,520,-271,-432,735,633,-419,232}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00018() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-647,-65,171,-975,452,143,-392,-554,513,770,848,121,725,-611,572,910,348,-870,956,452,105,-77,152,875,706,-974,289,-359,217,699,568,978,719,690,-39,621,384,461,-926,-560,-70,-987,-210,-76,259,549,443,487,-981,271,-478,63,-198,138,107,-415,756,-453,637,-428,-511,598,947,297}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00019() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{947,636,-133,-66,668,-832,-18,891,-469,-477,819,70,518,-230,605,-340,700,-804,-749,33,-736,512,367,411,-716,-884,447,393,-191,-90,-853,872,516,893,532,-838,-54,-922,-844,10,92,162,967,-448,-991,807,-874,-729,-983,-595,-942,-479,-935,-248,-670,-689,-134,187,-758,653,-469,448,455,51}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00020() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-231,-359,-707,813,-652,-386,910,-454,-5,-848,-733,317,841,-649,-592,-25,-262,-921,180,-991,-162,-815,-869,-302,-765,-477,-233,180,-509,-410,-994,-5,658,-103,-827,-128,49,-161,161,-874,-544,823,-510,-155,791,645,907,294,-830,727,-92,391,-639,-286,-913,-27,222,-848,-404,-348,242,-530,-865,506}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00021() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-112,-445,712,480,577,133,935,-606,630,736,-2,834,443,527,-453,-716,-191,-148,-76,168,12,-212,885,821,-947,871,-965,919,-833,907,672,524,589,-934,216,106,162,-859,702,480,-159,791,-93,-523,399,-114,-401,320,-301,-363,130,186,813,-875,132,259,937,-994,-575,658,-427,-237,423,-911}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00022() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{445,-445,-109,298,-320,-176,741,-824,867,-842,403,85,-228,-609,531,431,-715,-411,983,-671,-42,5,-77,801,785,490,-31,697,-675,702,9,-88,-958,752,-194,697,426,608,810,460,852,516,-333,281,437,981,881,-421,-790,-554,-246,-158,781,-295,335,-858,-577,-722,-608,-713,961,-457,653,886}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00023() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String,java.lang.String[]):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-473,36,-686,-235,632,-412,-759,-650,223,-784,-422,-399,323,387,48,-495,35,-595,272,-404,210,-336,-398,831,-681,864,-892,-882,-265,-269,134,935,-220,-642,588,945,706,-770,-339,-656,-481,-856,27,772,62,-179,-271,80,72,803,-28,951,-926,404,-225,-522,944,931,104,121,-314,-827,469,180}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00024() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String,java.lang.String[]):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{891,-707,-791,805,-545,944,-108,-979,-397,628,-463,-966,-970,-841,-160,-146,-307,-5,731,-249,200,610,13,176,-580,-879,518,-900,476,862,-388,-164,-681,-799,-401,-79,-987,784,142,444,-478,-390,-843,-687,-690,865,785,400,-600,272,69,234,653,-114,-287,983,989,700,914,813,-974,221,-301,426}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00025() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparator(java.lang.String,java.lang.String,java.lang.String[]):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-600,-679,737,-719,331,752,741,-331,610,-657,858,660,-715,-305,-752,-391,285,578,-323,-559,40,916,919,-319,-5,785,456,-881,-841,853,786,450,-153,255,-640,646,-274,-179,725,-317,903,771,-200,337,363,533,374,-807,-652,-851,900,-234,417,-78,437,693,-117,395,-785,-702,57,525,705,-441}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00026() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsAfter(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-366,382,306,212,306,918,-605,-711,-172,-876,-915,361,745,-479,954,-510,-768,379,-38,-984,-393,836,847,425,521,-167,203,-232,-230,-327,528,205,-49,-721,255,-872,-152,158,-185,956,317,-17,88,-795,711,260,279,699,451,59,-798,901,179,-994,-762,264,162,-173,-186,-769,64,-392,-843,42}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00027() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsAfter(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{426,-608,87,1000,-1000,704,1000,-125,-595,197,-102,-395,357,-205,212,1000,-605,1000,-414,687,-1000,-374,973,890,282,-518,185,383,-19,-211,-1000,905,-731,897,1000,1000,-1000,865,1000,-1000,632,-290,-1000,-928,-200,-910,333,-580,-213,-147,432,187,1000,117,-483,1000,469,-811,-189,170,132,1000,-192,-496}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00028() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsBefore(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-681,765,-659,-373,-390,327,627,607,418,794,400,471,474,-854,-348,-353,-998,-848,673,-50,565,-527,-94,-316,495,754,-714,-831,-172,-817,968,-88,274,-242,-483,-736,962,435,-668,62,-559,279,-603,-496,-268,755,252,-690,107,-645,-481,48,709,956,276,482,131,432,-889,-261,626,384,-237,161}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00029() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSeparatorIfFieldsBefore(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-232,130,-39,-179,48,514,-775,-152,-835,-456,256,948,560,389,-195,-197,153,-171,900,-610,-855,-75,707,-300,276,344,-608,795,-20,-422,911,726,-487,98,827,410,315,-611,979,265,621,-233,92,-869,-352,903,-715,786,563,27,-172,-733,-116,-13,-525,-920,951,472,-771,412,683,-959,498,479}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00030() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{673,-183,-37,-83,311,169,-494,-221,641,870,21,450,-155,775,-893,-546,576,-814,598,-480,385,-673,376,-844,-665,-864,-461,97,537,989,-550,-964,-953,315,-586,-777,-188,-406,492,767,162,654,333,737,725,845,-206,-427,587,-734,598,693,11,-69,309,-303,-901,-461,505,105,-592,340,179,193}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00031() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-490,-114,-749,-390,720,491,-259,496,-132,553,-235,534,-72,387,1000,-962,473,681,-305,-285,-1000,972,-273,253,832,-277,-737,818,150,-1000,-139,36,564,832,-65,-225,-1000,4,548,265,-1000,-858,-645,628,-620,715,-202,-628,-860,-1000,-706,-1000,251,177,1000,-402,1000,-832,72,-555,1000,-1000,1000,430}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00032() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalStateException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{579,716,-369,-709,585,380,-515,-905,49,143,-372,-160,128,-343,109,-768,-368,725,201,-516,617,-662,534,397,470,-41,-891,-326,834,-642,-130,-778,439,411,663,991,994,-754,-148,-509,635,-133,-340,578,133,964,-611,-723,-966,-8,-951,-489,218,-186,-321,-605,11,-789,632,-518,857,363,447,-226}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00033() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{0,-273,951,-512,1000,1000,-348,27,0,920,-1000,-1000,0,362,-1000,-377,539,-282,310,-999,753,0,-941,-185,1000,-594,-84,1000,-984,-541,-1000,-313,960,1000,-939,1000,495,-332,599,-380,-29,-516,445,394,307,1000,-1000,694,376,-44,186,-201,-633,0,-595,-653,-84,-686,1000,77,1000,761,344,-512}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00034() {
        org.junit.Assert.assertEquals("THROW:java.lang.IllegalArgumentException", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendSuffix(java.lang.String,java.lang.String):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{593,-374,548,330,-86,-970,560,219,537,-205,-632,84,47,27,-639,194,181,-834,649,162,937,763,370,-993,543,73,-41,837,290,667,211,317,969,395,-892,-115,695,978,851,-732,297,-731,328,-728,109,-199,-700,49,-991,-389,-980,-901,848,-464,710,73,-956,-807,326,179,-981,-346,-608,-603}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00035() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendWeeks():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-89,-56,401,624,549,-590,-423,-775,770,389,-91,924,-321,-778,319,208,-62,-414,-648,-791,368,-810,-212,-926,-280,82,-976,-124,-117,-735,847,259,-441,-487,343,809,-536,-826,182,726,-479,552,-748,-178,-843,995,-246,-941,-170,-768,225,921,222,-333,-307,-263,-871,-617,191,-249,-958,401,-814,-786}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00036() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "appendYears():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{267,417,833,662,582,153,722,-873,243,46,-372,790,294,246,699,-805,-493,415,-585,-1,-6,646,910,984,471,-227,864,-118,-403,260,425,615,-274,227,-475,-805,-107,-439,481,-757,-135,583,821,-148,707,270,908,-666,-555,643,131,-696,165,616,-827,505,-870,332,-828,779,-629,-198,-487,691}));
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
            new int[]{-428,275,-973,985,206,-698,332,984,613,833,899,-141,-350,-70,647,318,-990,223,-936,-417,527,686,-318,290,990,-716,735,401,-691,900,-212,-661,310,285,-70,-457,175,-138,-696,-562,609,-763,192,-601,175,-46,641,718,-73,-346,735,806,459,165,981,-83,805,245,20,-495,226,-287,881,19}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00039() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "minimumPrintedDigits(int):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{129,-547,496,439,154,-508,-672,-76,-113,642,72,858,-12,223,-39,-233,63,204,-130,976,237,-304,378,-438,894,-364,-66,957,31,35,-756,810,209,432,-868,809,-781,532,766,-774,291,390,-651,-839,871,-33,-800,681,-114,391,-559,-656,-294,-150,-539,966,-966,-334,-228,920,385,835,914,-269}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00040() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroAlways():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{972,917,458,-173,267,-269,-795,221,-750,-959,318,-433,178,377,20,993,235,-609,-911,-997,58,123,-6,153,-35,867,-34,784,815,197,-219,-67,-891,-509,958,850,-345,-420,-894,364,-51,61,221,-219,256,208,-104,-62,328,456,596,600,-346,981,-582,453,-795,-980,107,621,744,-725,453,-135}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00041() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroIfSupported():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-747,556,-267,569,-985,398,-241,-886,928,210,-32,253,709,-884,-789,782,-78,-53,130,507,868,-676,27,-522,816,-779,943,-46,859,165,290,-840,366,-172,-528,-802,994,104,310,-544,538,-974,-179,758,502,816,930,-883,220,251,-7,493,586,101,-284,179,30,906,-28,-398,212,544,-687,-794}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00042() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroNever():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{740,50,617,598,-626,696,379,-405,609,370,95,-546,-252,-773,918,-688,500,-633,708,603,941,450,-924,804,-53,-95,177,-581,656,-70,974,381,775,-556,-948,397,-451,513,283,506,92,-667,626,141,-556,-177,190,-695,-818,-880,-683,-840,704,796,-125,234,-562,-460,237,789,-897,162,-528,-565}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00043() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroRarelyFirst():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-789,745,-567,-512,-683,513,-677,-143,993,-980,569,-560,154,-284,-672,-612,339,962,531,-44,948,-249,-604,861,116,145,887,898,525,-396,615,-721,-391,-671,-394,-854,802,303,-668,-708,-224,795,478,-773,890,-257,-993,-555,532,-321,257,976,-571,608,64,123,-433,630,-824,-314,902,-26,-396,879}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00044() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "printZeroRarelyLast():org.joda.time.format.PeriodFormatterBuilder",
            new int[]{-602,-45,467,-463,-661,-355,779,-637,585,-333,246,88,490,-350,461,-36,615,-212,968,675,867,667,904,-355,-457,-476,-678,-508,435,-139,-278,743,-129,-229,-783,-215,-895,203,-546,292,820,-556,895,851,692,589,436,373,-81,478,416,12,423,-358,-923,-217,-970,-828,234,-148,363,124,-459,542}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00045() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "rejectSignedValues(boolean):org.joda.time.format.PeriodFormatterBuilder",
            new int[]{867,519,-777,722,932,-39,-314,451,-500,877,284,429,16,495,132,-198,-820,630,-600,37,-738,207,-466,-810,-988,-446,-679,904,430,-400,-328,349,-350,-801,-183,554,-570,739,810,-324,371,-101,729,619,-152,-32,-235,-880,-120,515,518,929,-333,320,-764,-975,-616,-503,-512,-35,953,-495,828,376}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00046() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "toFormatter():org.joda.time.format.PeriodFormatter",
            new int[]{-803,202,-782,-20,-278,528,-907,846,421,460,926,605,-928,623,707,696,-389,975,677,344,352,275,669,746,-623,-317,379,955,-604,-992,-220,686,605,35,-863,-632,672,77,-491,646,-103,465,-514,-776,776,-998,-2,457,752,630,462,425,756,474,306,-653,294,273,-75,157,312,767,536,796}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00047() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder$Literal", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "toParser():org.joda.time.format.PeriodParser",
            new int[]{59,948,-203,-612,-852,-691,736,-51,771,-876,-541,238,-185,390,986,-213,918,-352,-711,-787,748,-513,882,646,768,-667,-225,-384,182,-515,-275,-798,-965,-563,-232,-613,-281,-801,138,224,-399,541,-160,700,-119,-129,396,-18,339,-194,561,102,208,398,192,-895,40,-553,-153,-861,147,375,220,-910}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00048() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatterBuilder$Literal", DEReplay.run(
            "org.joda.time.format.PeriodFormatterBuilder", "org.joda.time.format.PeriodFormatterBuilder", "toPrinter():org.joda.time.format.PeriodPrinter",
            new int[]{16,387,171,-736,-159,965,46,744,-941,-96,904,584,-364,300,-360,767,304,306,953,-619,-153,657,-295,716,996,-701,-924,99,215,861,-39,305,-117,406,388,-603,-707,-252,586,939,-775,451,-211,643,-109,546,870,752,-487,17,440,688,959,-875,-800,-136,465,-237,1,-82,-278,-776,-661,-892}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00049() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternate():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00050() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternateExtended():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00051() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternateExtendedWithWeeks():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00052() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "alternateWithWeeks():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00053() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.ISOPeriodFormat", "", "standard():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00054() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormat", "", "getDefault():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00055() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormat", "", "wordBased():org.joda.time.format.PeriodFormatter",
            new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0}));
    }
    @org.junit.Test(timeout=60000L)
    public void testDE00056() {
        org.junit.Assert.assertEquals("TYPE:org.joda.time.format.PeriodFormatter", DEReplay.run(
            "org.joda.time.format.PeriodFormat", "", "wordBased(java.util.Locale):org.joda.time.format.PeriodFormatter",
            new int[]{-83,-932,-147,136,103,870,-575,88,368,246,-815,623,832,-764,671,-477,-710,-607,879,-916,-544,723,743,241,-87,-753,555,424,48,219,636,-692,800,863,-690,-184,377,583,-328,-724,315,60,48,-527,-831,-622,103,467,-39,-629,-457,-467,-891,-229,-877,-655,941,412,201,608,-815,-278,-487,-246}));
    }
}
